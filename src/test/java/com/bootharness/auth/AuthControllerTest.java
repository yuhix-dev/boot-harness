package com.bootharness.auth;

import static org.assertj.core.api.Assertions.assertThat;

import com.bootharness.auth.dto.AuthResponse;
import com.bootharness.auth.dto.LoginRequest;
import com.bootharness.auth.dto.PasswordResetConfirmRequest;
import com.bootharness.auth.dto.PasswordResetRequestRequest;
import com.bootharness.auth.dto.RefreshRequest;
import com.bootharness.auth.dto.RegisterRequest;
import com.bootharness.auth.dto.SetPasswordRequest;
import com.bootharness.email.EmailRepository;
import com.bootharness.user.User;
import com.bootharness.user.UserRepository;
import java.time.OffsetDateTime;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.ResponseEntity;
import org.springframework.test.util.ReflectionTestUtils;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
class AuthControllerTest {

  @Container @ServiceConnection
  static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

  @Autowired TestRestTemplate restTemplate;
  @Autowired UserRepository userRepository;
  @Autowired PasswordResetTokenRepository passwordResetTokenRepository;
  @Autowired RefreshTokenRepository refreshTokenRepository;
  @Autowired JwtService jwtService;
  @MockBean EmailRepository emailRepository;

  @BeforeEach
  void setUp() {
    passwordResetTokenRepository.deleteAll();
    refreshTokenRepository.deleteAll();
    userRepository.deleteAll();
  }

  // --- register ---

  @Test
  void register_returns201WithTokens() {
    var response = register("user@example.com", "password123", "Alice");

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    assertThat(response.getBody()).isNotNull();
    assertThat(response.getBody().accessToken()).isNotBlank();
    assertThat(response.getBody().refreshToken()).isNotBlank();
  }

  @Test
  void register_duplicateEmail_returns409() {
    register("user@example.com", "password123", "Alice");

    var response = register("user@example.com", "password123", "Alice");

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
  }

  // --- login ---

  @Test
  void login_success() {
    register("user@example.com", "password123", "Alice");

    var response = login("user@example.com", "password123");

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(response.getBody()).isNotNull();
    assertThat(response.getBody().accessToken()).isNotBlank();
  }

  @Test
  void login_wrongPassword_returns401() {
    register("user@example.com", "password123", "Alice");

    var response = login("user@example.com", "wrongpassword");

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
  }

  @Test
  void login_unknownEmail_returns401() {
    var response = login("nobody@example.com", "password123");

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
  }

  @Test
  void login_oauthUserWithNoPassword_returns401() {
    User oauthUser = userRepository.save(User.createOAuth("oauth@example.com", "Bob"));

    var response = login(oauthUser.getEmail(), "anypassword");

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
  }

  // --- refresh ---

  @Test
  void refresh_rotatesTokenPair() {
    var registerResponse = register("user@example.com", "password123", "Alice");
    String oldRefreshToken = registerResponse.getBody().refreshToken();

    var response =
        restTemplate.postForEntity(
            "/api/v1/auth/refresh", new RefreshRequest(oldRefreshToken), AuthResponse.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(response.getBody()).isNotNull();
    assertThat(response.getBody().refreshToken()).isNotBlank();
    assertThat(response.getBody().refreshToken()).isNotEqualTo(oldRefreshToken);
    assertThat(refreshTokenRepository.findByToken(oldRefreshToken)).isEmpty();
    assertThat(refreshTokenRepository.findByToken(response.getBody().refreshToken())).isPresent();
  }

  @Test
  void refresh_invalidToken_returns401() {
    var response =
        restTemplate.postForEntity(
            "/api/v1/auth/refresh", new RefreshRequest("invalid-token"), String.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
  }

  // --- logout ---

  @Test
  void logout_revokesRefreshToken() {
    var registerResponse = register("user@example.com", "password123", "Alice");
    String refreshToken = registerResponse.getBody().refreshToken();

    var response =
        restTemplate.postForEntity(
            "/api/v1/auth/logout", new RefreshRequest(refreshToken), Void.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
    assertThat(refreshTokenRepository.findByToken(refreshToken)).isEmpty();
  }

  // --- set password ---

  @Test
  void setPassword_allowsOAuth2UserToLoginWithPassword() {
    User oauthUser = userRepository.save(User.createOAuth("oauth@example.com", "Bob"));
    String accessToken = jwtService.generateAccessToken(oauthUser.getId(), oauthUser.getEmail());

    var setResponse =
        restTemplate.exchange(
            "/api/v1/auth/password",
            HttpMethod.PUT,
            new HttpEntity<>(new SetPasswordRequest("newpassword1"), bearerHeaders(accessToken)),
            Void.class);
    assertThat(setResponse.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);

    var loginResponse = login("oauth@example.com", "newpassword1");
    assertThat(loginResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
  }

  @Test
  void setPassword_withoutAuth_isRejected() {
    var response =
        restTemplate.exchange(
            "/api/v1/auth/password",
            HttpMethod.PUT,
            new HttpEntity<>(new SetPasswordRequest("newpassword1")),
            Void.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
  }

  // --- password reset ---

  @Test
  void requestPasswordReset_knownEmail_returns202() {
    register("user@example.com", "password123", "Alice");

    var response =
        restTemplate.postForEntity(
            "/api/v1/auth/password/reset/request",
            new PasswordResetRequestRequest("user@example.com"),
            Void.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
  }

  @Test
  void requestPasswordReset_unknownEmail_stillReturns202() {
    var response =
        restTemplate.postForEntity(
            "/api/v1/auth/password/reset/request",
            new PasswordResetRequestRequest("nobody@example.com"),
            Void.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
  }

  @Test
  void confirmPasswordReset_success() {
    register("user@example.com", "password123", "Alice");
    restTemplate.postForEntity(
        "/api/v1/auth/password/reset/request",
        new PasswordResetRequestRequest("user@example.com"),
        Void.class);
    String token = passwordResetTokenRepository.findAll().get(0).getToken();

    var confirmResponse =
        restTemplate.postForEntity(
            "/api/v1/auth/password/reset/confirm",
            new PasswordResetConfirmRequest(token, "resetpassword1"),
            Void.class);
    assertThat(confirmResponse.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);

    var loginResponse = login("user@example.com", "resetpassword1");
    assertThat(loginResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
  }

  @Test
  void confirmPasswordReset_invalidToken_returns401() {
    var response =
        restTemplate.postForEntity(
            "/api/v1/auth/password/reset/confirm",
            new PasswordResetConfirmRequest("invalid-token", "newpassword1"),
            Void.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
  }

  @Test
  void confirmPasswordReset_expiredToken_returns401() {
    register("user@example.com", "password123", "Alice");
    restTemplate.postForEntity(
        "/api/v1/auth/password/reset/request",
        new PasswordResetRequestRequest("user@example.com"),
        Void.class);

    PasswordResetToken token = passwordResetTokenRepository.findAll().get(0);
    ReflectionTestUtils.setField(token, "expiresAt", OffsetDateTime.now().minusHours(1));
    passwordResetTokenRepository.save(token);

    var response =
        restTemplate.postForEntity(
            "/api/v1/auth/password/reset/confirm",
            new PasswordResetConfirmRequest(token.getToken(), "newpassword1"),
            Void.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
  }

  // --- helpers ---

  private ResponseEntity<AuthResponse> register(String email, String password, String name) {
    return restTemplate.postForEntity(
        "/api/v1/auth/register", new RegisterRequest(email, password, name), AuthResponse.class);
  }

  private ResponseEntity<AuthResponse> login(String email, String password) {
    return restTemplate.postForEntity(
        "/api/v1/auth/login", new LoginRequest(email, password), AuthResponse.class);
  }

  private HttpHeaders bearerHeaders(String accessToken) {
    HttpHeaders headers = new HttpHeaders();
    headers.setBearerAuth(accessToken);
    return headers;
  }
}

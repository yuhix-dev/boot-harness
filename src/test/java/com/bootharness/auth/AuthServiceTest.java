package com.bootharness.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;

import com.bootharness.api.exception.EmailAlreadyInUseException;
import com.bootharness.api.exception.InvalidCredentialsException;
import com.bootharness.api.exception.TokenException;
import com.bootharness.auth.dto.AuthResponse;
import com.bootharness.auth.dto.LoginRequest;
import com.bootharness.auth.dto.RegisterRequest;
import com.bootharness.auth.event.UserRegisteredEvent;
import com.bootharness.config.AppProperties;
import com.bootharness.user.User;
import com.bootharness.user.UserRepository;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.crypto.password.PasswordEncoder;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

  @Mock private UserRepository userRepository;
  @Mock private RefreshTokenRepository refreshTokenRepository;
  @Mock private PasswordEncoder passwordEncoder;
  @Mock private JwtService jwtService;
  @Mock private ApplicationEventPublisher eventPublisher;
  @Mock private AppProperties appProperties;

  @InjectMocks private AuthService authService;

  private static final long REFRESH_TOKEN_EXPIRY_MS = 604800000L; // 7 days
  private static final AppProperties.Jwt JWT_PROPS =
      new AppProperties.Jwt("test-secret", 900000L, REFRESH_TOKEN_EXPIRY_MS);

  /** Stubs the three mocks needed by issueTokens() — shared by all happy-path tests. */
  private void stubTokenIssuance() {
    given(appProperties.jwt()).willReturn(JWT_PROPS);
    given(jwtService.generateAccessToken(any(), anyString())).willReturn("access-token");
    given(refreshTokenRepository.save(any())).willAnswer(inv -> inv.getArgument(0));
  }

  // --- register ---

  @Test
  void register_returnsTokenPair_whenEmailIsNew() {
    given(userRepository.existsByEmail("new@example.com")).willReturn(false);
    given(userRepository.save(any(User.class))).willAnswer(inv -> inv.getArgument(0));
    given(passwordEncoder.encode("password")).willReturn("encoded");
    stubTokenIssuance();

    AuthResponse response =
        authService.register(new RegisterRequest("new@example.com", "password", "Alice"));

    assertThat(response.accessToken()).isEqualTo("access-token");
    assertThat(response.refreshToken()).isNotNull();
    // UserRegisteredEvent is not an ApplicationEvent, so use any(UserRegisteredEvent.class)
    // to resolve the publishEvent(Object) overload correctly
    then(eventPublisher).should().publishEvent(any(UserRegisteredEvent.class));
  }

  @Test
  void register_throwsEmailAlreadyInUseException_whenEmailExists() {
    given(userRepository.existsByEmail("existing@example.com")).willReturn(true);

    assertThatThrownBy(
            () ->
                authService.register(
                    new RegisterRequest("existing@example.com", "password", "Bob")))
        .isInstanceOf(EmailAlreadyInUseException.class);

    then(userRepository).should(never()).save(any());
  }

  // --- login ---

  @Test
  void login_returnsTokenPair_whenCredentialsAreValid() {
    User user = User.createLocal("user@example.com", "encoded", "Carol");
    given(userRepository.findByEmail("user@example.com")).willReturn(Optional.of(user));
    given(passwordEncoder.matches("password", "encoded")).willReturn(true);
    stubTokenIssuance();

    AuthResponse response = authService.login(new LoginRequest("user@example.com", "password"));

    assertThat(response.accessToken()).isEqualTo("access-token");
    assertThat(response.refreshToken()).isNotNull();
  }

  @Test
  void login_throwsInvalidCredentialsException_whenEmailNotFound() {
    given(userRepository.findByEmail("unknown@example.com")).willReturn(Optional.empty());

    assertThatThrownBy(() -> authService.login(new LoginRequest("unknown@example.com", "password")))
        .isInstanceOf(InvalidCredentialsException.class);
  }

  @Test
  void login_throwsInvalidCredentialsException_whenPasswordDoesNotMatch() {
    User user = User.createLocal("user@example.com", "encoded", "Dave");
    given(userRepository.findByEmail("user@example.com")).willReturn(Optional.of(user));
    given(passwordEncoder.matches("wrong", "encoded")).willReturn(false);

    assertThatThrownBy(() -> authService.login(new LoginRequest("user@example.com", "wrong")))
        .isInstanceOf(InvalidCredentialsException.class);
  }

  @Test
  void login_throwsInvalidCredentialsException_whenUserIsOAuthOnly() {
    // OAuth2-only users have null password
    User user = User.createLocal("oauth@example.com", null, "Eve");
    given(userRepository.findByEmail("oauth@example.com")).willReturn(Optional.of(user));

    assertThatThrownBy(() -> authService.login(new LoginRequest("oauth@example.com", "password")))
        .isInstanceOf(InvalidCredentialsException.class);
  }

  // --- refresh ---

  @Test
  void refresh_returnsNewTokenPair_andDeletesOldToken() {
    User user = User.createLocal("user@example.com", "encoded", "Frank");
    RefreshToken token = RefreshToken.create(user, REFRESH_TOKEN_EXPIRY_MS);
    given(refreshTokenRepository.findByToken("valid-token")).willReturn(Optional.of(token));
    stubTokenIssuance();

    AuthResponse response = authService.refresh("valid-token");

    assertThat(response.accessToken()).isEqualTo("access-token");
    then(refreshTokenRepository).should().delete(token);
  }

  @Test
  void refresh_throwsTokenException_whenTokenNotFound() {
    given(refreshTokenRepository.findByToken("unknown")).willReturn(Optional.empty());

    assertThatThrownBy(() -> authService.refresh("unknown")).isInstanceOf(TokenException.class);
  }

  @Test
  void refresh_throwsTokenException_andDeletesToken_whenExpired() {
    User user = User.createLocal("user@example.com", "encoded", "Grace");
    RefreshToken expiredToken = RefreshToken.create(user, -1L);
    given(refreshTokenRepository.findByToken("expired-token"))
        .willReturn(Optional.of(expiredToken));

    assertThatThrownBy(() -> authService.refresh("expired-token"))
        .isInstanceOf(TokenException.class)
        .hasMessageContaining("expired");

    then(refreshTokenRepository).should().delete(expiredToken);
  }

  // --- logout ---

  @Test
  void logout_deletesToken_whenFound() {
    User user = User.createLocal("user@example.com", "encoded", "Hank");
    RefreshToken token = RefreshToken.create(user, REFRESH_TOKEN_EXPIRY_MS);
    given(refreshTokenRepository.findByToken("valid-token")).willReturn(Optional.of(token));

    authService.logout("valid-token");

    then(refreshTokenRepository).should().delete(token);
  }

  @Test
  void logout_doesNothing_whenTokenNotFound() {
    given(refreshTokenRepository.findByToken("unknown")).willReturn(Optional.empty());

    authService.logout("unknown");

    then(refreshTokenRepository).should(never()).delete(any());
  }
}

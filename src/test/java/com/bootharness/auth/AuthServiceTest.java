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
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.crypto.password.PasswordEncoder;

@ExtendWith(MockitoExtension.class)
@DisplayName("AuthService")
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

  @Nested
  @DisplayName("register()")
  class Register {

    @Test
    @DisplayName("returns token pair when email is new")
    void returnsTokenPair() {
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
    @DisplayName("throws EmailAlreadyInUseException when email already exists")
    void throwsWhenEmailExists() {
      given(userRepository.existsByEmail("existing@example.com")).willReturn(true);

      assertThatThrownBy(
              () ->
                  authService.register(
                      new RegisterRequest("existing@example.com", "password", "Bob")))
          .isInstanceOf(EmailAlreadyInUseException.class);

      then(userRepository).should(never()).save(any());
    }
  }

  @Nested
  @DisplayName("login()")
  class Login {

    @Test
    @DisplayName("returns token pair when credentials are valid")
    void returnsTokenPair() {
      User user = User.createLocal("user@example.com", "encoded", "Carol");
      given(userRepository.findByEmail("user@example.com")).willReturn(Optional.of(user));
      given(passwordEncoder.matches("password", "encoded")).willReturn(true);
      stubTokenIssuance();

      AuthResponse response = authService.login(new LoginRequest("user@example.com", "password"));

      assertThat(response.accessToken()).isEqualTo("access-token");
      assertThat(response.refreshToken()).isNotNull();
    }

    @ParameterizedTest(name = "{0}")
    @EnumSource(LoginFailure.class)
    @DisplayName("throws InvalidCredentialsException when")
    void throwsInvalidCredentials(LoginFailure scenario) {
      scenario.setup(userRepository, passwordEncoder);

      assertThatThrownBy(() -> authService.login(new LoginRequest("u@e.com", "pw")))
          .isInstanceOf(InvalidCredentialsException.class);
    }
  }

  @Nested
  @DisplayName("refresh()")
  class Refresh {

    @Test
    @DisplayName("returns new token pair and deletes the old token")
    void returnsNewTokenPairAndDeletesOld() {
      User user = User.createLocal("user@example.com", "encoded", "Frank");
      RefreshToken token = RefreshToken.create(user, REFRESH_TOKEN_EXPIRY_MS);
      given(refreshTokenRepository.findByToken("valid-token")).willReturn(Optional.of(token));
      stubTokenIssuance();

      AuthResponse response = authService.refresh("valid-token");

      assertThat(response.accessToken()).isEqualTo("access-token");
      then(refreshTokenRepository).should().delete(token);
    }

    @Test
    @DisplayName("throws TokenException when token is not found")
    void throwsWhenTokenNotFound() {
      given(refreshTokenRepository.findByToken("unknown")).willReturn(Optional.empty());

      assertThatThrownBy(() -> authService.refresh("unknown")).isInstanceOf(TokenException.class);
    }

    @Test
    @DisplayName("throws TokenException and deletes the token when expired")
    void throwsAndDeletesWhenExpired() {
      User user = User.createLocal("user@example.com", "encoded", "Grace");
      RefreshToken expiredToken = RefreshToken.create(user, -1L);
      given(refreshTokenRepository.findByToken("expired-token"))
          .willReturn(Optional.of(expiredToken));

      assertThatThrownBy(() -> authService.refresh("expired-token"))
          .isInstanceOf(TokenException.class)
          .hasMessageContaining("expired");

      then(refreshTokenRepository).should().delete(expiredToken);
    }
  }

  @Nested
  @DisplayName("logout()")
  class Logout {

    @Test
    @DisplayName("deletes the token when found")
    void deletesTokenWhenFound() {
      User user = User.createLocal("user@example.com", "encoded", "Hank");
      RefreshToken token = RefreshToken.create(user, REFRESH_TOKEN_EXPIRY_MS);
      given(refreshTokenRepository.findByToken("valid-token")).willReturn(Optional.of(token));

      authService.logout("valid-token");

      then(refreshTokenRepository).should().delete(token);
    }

    @Test
    @DisplayName("does nothing when token is not found")
    void doesNothingWhenTokenNotFound() {
      given(refreshTokenRepository.findByToken("unknown")).willReturn(Optional.empty());

      authService.logout("unknown");

      then(refreshTokenRepository).should(never()).delete(any());
    }
  }

  /**
   * Enum-based parameterization for login failure scenarios. Each constant encapsulates the mock
   * setup that causes login to fail, eliminating the need for separate test methods.
   */
  private enum LoginFailure {
    EMAIL_NOT_FOUND("email is not registered") {
      @Override
      void setup(UserRepository repo, PasswordEncoder encoder) {
        given(repo.findByEmail(any())).willReturn(Optional.empty());
      }
    },
    WRONG_PASSWORD("password does not match") {
      @Override
      void setup(UserRepository repo, PasswordEncoder encoder) {
        User user = User.createLocal("u@e.com", "encoded", "X");
        given(repo.findByEmail(any())).willReturn(Optional.of(user));
        given(encoder.matches(any(), any())).willReturn(false);
      }
    },
    OAUTH_ONLY("user has no password (OAuth-only)") {
      @Override
      void setup(UserRepository repo, PasswordEncoder encoder) {
        User user = User.createLocal("u@e.com", null, "X");
        given(repo.findByEmail(any())).willReturn(Optional.of(user));
      }
    };

    private final String description;

    LoginFailure(String description) {
      this.description = description;
    }

    abstract void setup(UserRepository repo, PasswordEncoder encoder);

    @Override
    public String toString() {
      return description;
    }
  }
}

package com.bootharness.auth;

import com.bootharness.api.exception.EmailAlreadyInUseException;
import com.bootharness.api.exception.InvalidCredentialsException;
import com.bootharness.api.exception.TokenException;
import com.bootharness.auth.dto.AuthResponse;
import com.bootharness.auth.dto.LoginRequest;
import com.bootharness.auth.dto.RegisterRequest;
import com.bootharness.auth.event.PasswordResetRequestedEvent;
import com.bootharness.auth.event.UserRegisteredEvent;
import com.bootharness.config.AppProperties;
import com.bootharness.user.User;
import com.bootharness.user.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Handles user registration, login, token refresh, and logout. */
@Service
@RequiredArgsConstructor
public class AuthService {

  private final UserRepository userRepository;
  private final RefreshTokenRepository refreshTokenRepository;
  private final PasswordResetTokenRepository passwordResetTokenRepository;
  private final PasswordEncoder passwordEncoder;
  private final JwtService jwtService;
  private final ApplicationEventPublisher eventPublisher;
  private final AppProperties appProperties;

  /**
   * Registers a new local (email/password) user and returns an initial token pair.
   *
   * @throws EmailAlreadyInUseException if the email is already registered
   */
  @Transactional
  public AuthResponse register(RegisterRequest request) {
    if (userRepository.existsByEmail(request.email())) {
      throw new EmailAlreadyInUseException(request.email());
    }

    User user =
        User.createLocal(
            request.email(), passwordEncoder.encode(request.password()), request.name());
    userRepository.save(user);

    eventPublisher.publishEvent(new UserRegisteredEvent(user));

    return issueTokens(user);
  }

  /**
   * Authenticates a user with email and password and returns a token pair.
   *
   * @throws InvalidCredentialsException if the email is not found or the password does not match
   */
  @Transactional
  public AuthResponse login(LoginRequest request) {
    User user =
        userRepository.findByEmail(request.email()).orElseThrow(InvalidCredentialsException::new);

    if (user.getPassword() == null
        || !passwordEncoder.matches(request.password(), user.getPassword())) {
      throw new InvalidCredentialsException();
    }

    return issueTokens(user);
  }

  /**
   * Validates the given refresh token and issues a new token pair (token rotation).
   *
   * @throws TokenException if the token is not found or has expired
   */
  @Transactional
  public AuthResponse refresh(String rawRefreshToken) {
    RefreshToken stored =
        refreshTokenRepository
            .findByToken(rawRefreshToken)
            .orElseThrow(() -> new TokenException("Invalid refresh token"));

    if (stored.isExpired()) {
      refreshTokenRepository.delete(stored);
      throw new TokenException("Refresh token expired");
    }

    refreshTokenRepository.delete(stored);
    return issueTokens(stored.getUser());
  }

  /** Invalidates the given refresh token. No-op if the token does not exist. */
  @Transactional
  public void logout(String rawRefreshToken) {
    refreshTokenRepository.findByToken(rawRefreshToken).ifPresent(refreshTokenRepository::delete);
  }

  /**
   * Sets or updates the password for an authenticated user.
   *
   * <p>OAuth2 users can call this to enable password-based login in addition to OAuth2.
   */
  @Transactional
  public void setPassword(User currentUser, String newPassword) {
    currentUser.setPassword(passwordEncoder.encode(newPassword));
    userRepository.save(currentUser);
  }

  /**
   * Initiates a password reset by sending a reset token to the given email.
   *
   * <p>Always returns successfully to prevent email enumeration — no error is thrown if the email
   * is not found.
   */
  @Transactional
  public void requestPasswordReset(String email) {
    userRepository
        .findByEmail(email)
        .ifPresent(
            user -> {
              passwordResetTokenRepository.deleteByUser(user);
              PasswordResetToken token = PasswordResetToken.create(user);
              passwordResetTokenRepository.save(token);
              eventPublisher.publishEvent(new PasswordResetRequestedEvent(user, token.getToken()));
            });
  }

  /**
   * Validates the reset token and sets the new password.
   *
   * @throws TokenException if the token is invalid or expired
   */
  @Transactional
  public void confirmPasswordReset(String rawToken, String newPassword) {
    PasswordResetToken stored =
        passwordResetTokenRepository
            .findByToken(rawToken)
            .orElseThrow(() -> new TokenException("Invalid or expired reset token"));

    if (stored.isExpired()) {
      passwordResetTokenRepository.delete(stored);
      throw new TokenException("Invalid or expired reset token");
    }

    User user = stored.getUser();
    user.setPassword(passwordEncoder.encode(newPassword));
    userRepository.save(user);
    passwordResetTokenRepository.delete(stored);
  }

  private AuthResponse issueTokens(User user) {
    String accessToken = jwtService.generateAccessToken(user.getId(), user.getEmail());
    RefreshToken refreshToken =
        RefreshToken.create(user, appProperties.jwt().refreshTokenExpirationMs());
    refreshTokenRepository.save(refreshToken);
    return new AuthResponse(accessToken, refreshToken.getToken());
  }
}

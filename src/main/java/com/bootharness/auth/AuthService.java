package com.bootharness.auth;

import com.bootharness.auth.dto.AuthResponse;
import com.bootharness.auth.dto.LoginRequest;
import com.bootharness.auth.dto.RegisterRequest;
import com.bootharness.auth.event.UserRegisteredEvent;
import com.bootharness.config.AppProperties;
import com.bootharness.user.User;
import com.bootharness.user.UserRepository;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class AuthService {

  private final UserRepository userRepository;
  private final RefreshTokenRepository refreshTokenRepository;
  private final PasswordEncoder passwordEncoder;
  private final JwtService jwtService;
  private final ApplicationEventPublisher eventPublisher;
  private final long refreshTokenExpirationMs;

  public AuthService(
      UserRepository userRepository,
      RefreshTokenRepository refreshTokenRepository,
      PasswordEncoder passwordEncoder,
      JwtService jwtService,
      ApplicationEventPublisher eventPublisher,
      AppProperties appProperties) {
    this.userRepository = userRepository;
    this.refreshTokenRepository = refreshTokenRepository;
    this.passwordEncoder = passwordEncoder;
    this.jwtService = jwtService;
    this.eventPublisher = eventPublisher;
    this.refreshTokenExpirationMs = appProperties.jwt().refreshTokenExpirationMs();
  }

  @Transactional
  public AuthResponse register(RegisterRequest request) {
    if (userRepository.existsByEmail(request.email())) {
      throw new ResponseStatusException(HttpStatus.CONFLICT, "Email already in use");
    }

    User user = new User();
    user.setEmail(request.email());
    user.setPassword(passwordEncoder.encode(request.password()));
    user.setName(request.name());
    userRepository.save(user);

    eventPublisher.publishEvent(new UserRegisteredEvent(user));

    return issueTokens(user);
  }

  @Transactional
  public AuthResponse login(LoginRequest request) {
    User user =
        userRepository
            .findByEmail(request.email())
            .orElseThrow(
                () -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid credentials"));

    if (user.getPassword() == null
        || !passwordEncoder.matches(request.password(), user.getPassword())) {
      throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid credentials");
    }

    return issueTokens(user);
  }

  @Transactional
  public AuthResponse refresh(String rawRefreshToken) {
    RefreshToken stored =
        refreshTokenRepository
            .findByToken(rawRefreshToken)
            .orElseThrow(
                () ->
                    new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid refresh token"));

    if (stored.isExpired()) {
      refreshTokenRepository.delete(stored);
      throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Refresh token expired");
    }

    // Rotate: delete old token, issue new pair
    refreshTokenRepository.delete(stored);
    return issueTokens(stored.getUser());
  }

  @Transactional
  public void logout(String rawRefreshToken) {
    refreshTokenRepository.findByToken(rawRefreshToken).ifPresent(refreshTokenRepository::delete);
  }

  private AuthResponse issueTokens(User user) {
    String accessToken = jwtService.generateAccessToken(user.getId(), user.getEmail());

    RefreshToken refreshToken = new RefreshToken();
    refreshToken.setUser(user);
    refreshToken.setToken(UUID.randomUUID().toString());
    refreshToken.setExpiresAt(
        OffsetDateTime.now().plusNanos(refreshTokenExpirationMs * 1_000_000L));
    refreshTokenRepository.save(refreshToken);

    return new AuthResponse(accessToken, refreshToken.getToken());
  }
}

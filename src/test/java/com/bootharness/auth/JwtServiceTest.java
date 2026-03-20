package com.bootharness.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.bootharness.config.AppProperties;
import io.jsonwebtoken.JwtException;
import java.util.UUID;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class JwtServiceTest {

  private static final String SECRET = "test-secret-key-must-be-at-least-32-characters-for-hmac";

  // JwtService is stateless after construction — initialize once to avoid repeated HMAC key
  // derivation
  private static JwtService jwtService;

  @BeforeAll
  static void setUp() {
    jwtService = createJwtService(900000L);
  }

  private static JwtService createJwtService(long accessTokenExpirationMs) {
    return new JwtService(
        new AppProperties(
            new AppProperties.Jwt(SECRET, accessTokenExpirationMs, 0L), null, null, null));
  }

  @Test
  void generateAndValidate_roundTrip() {
    UUID userId = UUID.randomUUID();
    String token = jwtService.generateAccessToken(userId, "test@example.com");

    assertThat(jwtService.isValid(token)).isTrue();
    assertThat(jwtService.validateAndExtractUserId(token)).isEqualTo(userId);
  }

  @Test
  void isValid_returnsFalse_whenTokenIsTampered() {
    String token = jwtService.generateAccessToken(UUID.randomUUID(), "test@example.com");
    String tampered = token.substring(0, token.length() - 5) + "XXXXX";

    assertThat(jwtService.isValid(tampered)).isFalse();
  }

  @Test
  void isValid_returnsFalse_whenTokenIsExpired() {
    JwtService shortExpiryService = createJwtService(-1L);
    String token = shortExpiryService.generateAccessToken(UUID.randomUUID(), "test@example.com");

    assertThat(jwtService.isValid(token)).isFalse();
  }

  @Test
  void validateAndExtractUserId_throwsJwtException_whenTokenIsInvalid() {
    assertThatThrownBy(() -> jwtService.validateAndExtractUserId("not.a.token"))
        .isInstanceOf(JwtException.class);
  }
}

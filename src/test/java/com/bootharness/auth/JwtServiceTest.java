package com.bootharness.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.bootharness.config.AppProperties;
import io.jsonwebtoken.JwtException;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

@DisplayName("JwtService")
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

  @Nested
  @DisplayName("with a valid token")
  class ValidToken {

    @Test
    @DisplayName("validateAndExtractUserId returns the correct user ID")
    void extractsUserId() {
      UUID userId = UUID.randomUUID();
      String token = jwtService.generateAccessToken(userId, "test@example.com");

      assertThat(jwtService.validateAndExtractUserId(token)).isEqualTo(userId);
    }

    @Test
    @DisplayName("isValid returns true")
    void isValidReturnsTrue() {
      String token = jwtService.generateAccessToken(UUID.randomUUID(), "test@example.com");

      assertThat(jwtService.isValid(token)).isTrue();
    }
  }

  @Nested
  @DisplayName("with an invalid token")
  class InvalidToken {

    @ParameterizedTest(name = "{0}")
    @MethodSource("com.bootharness.auth.JwtServiceTest#invalidTokenCases")
    @DisplayName("isValid returns false for")
    void isValidReturnsFalse(String description, String token) {
      assertThat(jwtService.isValid(token)).isFalse();
    }

    @Test
    @DisplayName("validateAndExtractUserId throws JwtException for a malformed token")
    void validateThrowsForMalformed() {
      assertThatThrownBy(() -> jwtService.validateAndExtractUserId("not.a.token"))
          .isInstanceOf(JwtException.class);
    }
  }

  static Stream<Arguments> invalidTokenCases() {
    JwtService tempService = createJwtService(900000L);
    JwtService expiredService = createJwtService(-1L);
    String token = tempService.generateAccessToken(UUID.randomUUID(), "test@example.com");
    return Stream.of(
        Arguments.of("a tampered token", token.substring(0, token.length() - 5) + "XXXXX"),
        Arguments.of(
            "an expired token",
            expiredService.generateAccessToken(UUID.randomUUID(), "test@example.com")));
  }
}

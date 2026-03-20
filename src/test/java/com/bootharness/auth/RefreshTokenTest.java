package com.bootharness.auth;

import static org.assertj.core.api.Assertions.assertThat;

import com.bootharness.user.User;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

@DisplayName("RefreshToken")
class RefreshTokenTest {

  private static final long SEVEN_DAYS_MS = 604800000L;
  private static final User USER = User.createLocal("test@example.com", "encoded", "Test");

  @Nested
  @DisplayName("create()")
  class Create {

    @Test
    @DisplayName("sets user, token, and expiresAt correctly")
    void setsFieldsCorrectly() {
      RefreshToken token = RefreshToken.create(USER, SEVEN_DAYS_MS);

      assertThat(token.getUser()).isEqualTo(USER);
      assertThat(token.getToken()).isNotBlank();
      assertThat(token.getExpiresAt()).isNotNull();
    }
  }

  @Nested
  @DisplayName("isExpired()")
  class IsExpired {

    @ParameterizedTest(name = "{0}")
    @MethodSource("com.bootharness.auth.RefreshTokenTest#expiryScenarios")
    void returns(String description, long expiryMs, boolean expectedResult) {
      RefreshToken token = RefreshToken.create(USER, expiryMs);

      assertThat(token.isExpired()).isEqualTo(expectedResult);
    }
  }

  static Stream<Arguments> expiryScenarios() {
    return Stream.of(
        Arguments.of("false when expiry is in the future", SEVEN_DAYS_MS, false),
        Arguments.of("true when expiry is already past", -1L, true));
  }
}

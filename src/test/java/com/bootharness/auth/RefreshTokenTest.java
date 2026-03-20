package com.bootharness.auth;

import static org.assertj.core.api.Assertions.assertThat;

import com.bootharness.user.User;
import org.junit.jupiter.api.Test;

class RefreshTokenTest {

  private static final long SEVEN_DAYS_MS = 604800000L;
  private static final User USER = User.createLocal("test@example.com", "encoded", "Test");

  @Test
  void create_setsFieldsCorrectly() {
    RefreshToken token = RefreshToken.create(USER, SEVEN_DAYS_MS);

    assertThat(token.getUser()).isEqualTo(USER);
    assertThat(token.getToken()).isNotBlank();
    assertThat(token.getExpiresAt()).isNotNull();
  }

  @Test
  void isExpired_returnsFalse_whenNotYetExpired() {
    RefreshToken token = RefreshToken.create(USER, SEVEN_DAYS_MS);

    assertThat(token.isExpired()).isFalse();
  }

  @Test
  void isExpired_returnsTrue_whenAlreadyExpired() {
    RefreshToken token = RefreshToken.create(USER, -1L);

    assertThat(token.isExpired()).isTrue();
  }
}

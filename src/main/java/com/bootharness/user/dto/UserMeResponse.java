package com.bootharness.user.dto;

import com.bootharness.user.User;
import java.time.OffsetDateTime;
import java.util.UUID;

public record UserMeResponse(
    UUID id,
    String email,
    String name,
    String role,
    OffsetDateTime createdAt,
    OffsetDateTime updatedAt) {

  public static UserMeResponse from(User user) {
    return new UserMeResponse(
        user.getId(),
        user.getEmail(),
        user.getName(),
        user.getRole().name(),
        user.getCreatedAt(),
        user.getUpdatedAt());
  }
}

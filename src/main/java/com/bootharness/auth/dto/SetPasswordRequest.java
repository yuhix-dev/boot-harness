package com.bootharness.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record SetPasswordRequest(@NotBlank @Size(min = 8) String password) {}

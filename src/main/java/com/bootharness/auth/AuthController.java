package com.bootharness.auth;

import com.bootharness.auth.dto.AuthResponse;
import com.bootharness.auth.dto.LoginRequest;
import com.bootharness.auth.dto.PasswordResetConfirmRequest;
import com.bootharness.auth.dto.PasswordResetRequestRequest;
import com.bootharness.auth.dto.RefreshRequest;
import com.bootharness.auth.dto.RegisterRequest;
import com.bootharness.auth.dto.SetPasswordRequest;
import com.bootharness.user.User;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

  private final AuthService authService;

  @PostMapping("/register")
  public ResponseEntity<AuthResponse> register(@Valid @RequestBody RegisterRequest request) {
    return ResponseEntity.status(HttpStatus.CREATED).body(authService.register(request));
  }

  @PostMapping("/login")
  public ResponseEntity<AuthResponse> login(@Valid @RequestBody LoginRequest request) {
    return ResponseEntity.ok(authService.login(request));
  }

  @PostMapping("/refresh")
  public ResponseEntity<AuthResponse> refresh(@Valid @RequestBody RefreshRequest request) {
    return ResponseEntity.ok(authService.refresh(request.refreshToken()));
  }

  @PostMapping("/logout")
  public ResponseEntity<Void> logout(@Valid @RequestBody RefreshRequest request) {
    authService.logout(request.refreshToken());
    return ResponseEntity.noContent().build();
  }

  /** Sets or updates the password for the currently authenticated user. */
  @PutMapping("/password")
  public ResponseEntity<Void> setPassword(
      @AuthenticationPrincipal User user, @Valid @RequestBody SetPasswordRequest request) {
    authService.setPassword(user, request.password());
    return ResponseEntity.noContent().build();
  }

  /** Sends a password reset email. Always returns 202 to prevent email enumeration. */
  @PostMapping("/password/reset/request")
  public ResponseEntity<Void> requestPasswordReset(
      @Valid @RequestBody PasswordResetRequestRequest request) {
    authService.requestPasswordReset(request.email());
    return ResponseEntity.accepted().build();
  }

  /** Validates the reset token and sets the new password. */
  @PostMapping("/password/reset/confirm")
  public ResponseEntity<Void> confirmPasswordReset(
      @Valid @RequestBody PasswordResetConfirmRequest request) {
    authService.confirmPasswordReset(request.token(), request.newPassword());
    return ResponseEntity.noContent().build();
  }
}

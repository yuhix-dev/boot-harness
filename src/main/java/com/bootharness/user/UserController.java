package com.bootharness.user;

import com.bootharness.user.dto.UserMeResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
public class UserController {

  @GetMapping("/me")
  public ResponseEntity<UserMeResponse> me(@AuthenticationPrincipal User user) {
    return ResponseEntity.ok(UserMeResponse.from(user));
  }
}

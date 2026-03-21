package com.bootharness.billing;

import com.bootharness.billing.dto.CheckoutRequest;
import com.bootharness.billing.dto.CheckoutResponse;
import com.bootharness.billing.dto.PortalResponse;
import com.bootharness.billing.dto.SubscriptionResponse;
import com.bootharness.user.User;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/billing")
@RequiredArgsConstructor
public class BillingController {

  private final BillingService billingService;

  @PostMapping("/checkout")
  public ResponseEntity<CheckoutResponse> checkout(
      @AuthenticationPrincipal User user, @Valid @RequestBody CheckoutRequest request) {
    return ResponseEntity.ok(billingService.createCheckoutSession(user, request));
  }

  @PostMapping("/portal")
  public ResponseEntity<PortalResponse> portal(@AuthenticationPrincipal User user) {
    return ResponseEntity.ok(billingService.createPortalSession(user));
  }

  @GetMapping("/subscription")
  public ResponseEntity<SubscriptionResponse> subscription(@AuthenticationPrincipal User user) {
    return ResponseEntity.ok(billingService.getSubscription(user));
  }

  @PostMapping("/webhook")
  public ResponseEntity<Void> webhook(
      @RequestBody String payload, @RequestHeader("Stripe-Signature") String signature) {
    billingService.handleWebhook(payload, signature);
    return ResponseEntity.ok().build();
  }
}

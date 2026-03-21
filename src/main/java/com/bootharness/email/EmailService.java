package com.bootharness.email;

import com.bootharness.auth.event.PasswordResetRequestedEvent;
import com.bootharness.auth.event.UserRegisteredEvent;
import com.bootharness.billing.event.PaymentFailedEvent;
import com.bootharness.config.AppProperties;
import com.bootharness.user.User;
import lombok.RequiredArgsConstructor;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class EmailService {

  private final EmailRepository emailRepository;
  private final AppProperties appProperties;

  @EventListener
  public void onUserRegistered(UserRegisteredEvent event) {
    User user = event.user();
    emailRepository.send(user.getEmail(), "Welcome to BootHarness", welcomeHtml(user.getName()));
  }

  @EventListener
  public void onPasswordResetRequested(PasswordResetRequestedEvent event) {
    User user = event.user();
    String resetLink =
        appProperties.oauth2().redirectBaseUrl() + "/reset-password?token=" + event.rawToken();
    emailRepository.send(
        user.getEmail(), "Reset your password", passwordResetHtml(user.getName(), resetLink));
  }

  @EventListener
  public void onPaymentFailed(PaymentFailedEvent event) {
    User user = event.user();
    emailRepository.send(
        user.getEmail(), "Payment failed", paymentFailedHtml(user.getName(), event.invoiceUrl()));
  }

  private String welcomeHtml(String name) {
    return """
        <h1>Welcome, %s!</h1>
        <p>Your account has been created successfully.</p>
        """
        .formatted(name != null ? name : "there");
  }

  private String passwordResetHtml(String name, String resetLink) {
    return """
        <h1>Reset your password</h1>
        <p>Hi %s,</p>
        <p>Click the link below to reset your password. This link expires in 1 hour.</p>
        <p><a href="%s">Reset password</a></p>
        <p>If you didn't request this, you can ignore this email.</p>
        """
        .formatted(name != null ? name : "there", resetLink);
  }

  private String paymentFailedHtml(String name, String invoiceUrl) {
    String invoiceLink =
        invoiceUrl != null ? "<p><a href=\"%s\">View invoice</a></p>".formatted(invoiceUrl) : "";
    return """
        <h1>Payment failed</h1>
        <p>Hi %s,</p>
        <p>We were unable to process your payment. Please update your payment method to keep your subscription active.</p>
        %s
        """
        .formatted(name != null ? name : "there", invoiceLink);
  }
}

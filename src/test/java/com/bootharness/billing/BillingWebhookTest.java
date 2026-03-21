package com.bootharness.billing;

import static org.assertj.core.api.Assertions.assertThat;

import com.bootharness.email.EmailRepository;
import com.bootharness.user.User;
import com.bootharness.user.UserRepository;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.HexFormat;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
class BillingWebhookTest {

  private static final String WEBHOOK_SECRET = "test";

  @Container @ServiceConnection
  static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

  @Autowired TestRestTemplate restTemplate;
  @Autowired UserRepository userRepository;
  @Autowired StripeEventRepository stripeEventRepository;
  @MockBean EmailRepository emailRepository;

  @BeforeEach
  void setUp() {
    stripeEventRepository.deleteAll();
    userRepository.deleteAll();
  }

  @Test
  void webhook_withValidSignature_recordsEvent() {
    User user = User.createLocal("user@example.com", "encoded", "Alice");
    user.setStripeCustomerId("cus_123");
    userRepository.save(user);

    String payload =
        "{\"id\":\"evt_test_123\",\"object\":\"event\",\"type\":\"invoice.payment_failed\","
            + "\"data\":{\"object\":{\"customer\":\"cus_123\","
            + "\"hosted_invoice_url\":\"https://example.com/invoice\"}}}";

    var response =
        restTemplate.exchange(
            "/api/v1/billing/webhook",
            HttpMethod.POST,
            new HttpEntity<>(payload, webhookHeaders(payload)),
            String.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(stripeEventRepository.existsById("evt_test_123")).isTrue();
  }

  @Test
  void webhook_duplicateEvent_isIgnored() {
    String payload =
        "{\"id\":\"evt_test_456\",\"object\":\"event\",\"type\":\"invoice.payment_failed\","
            + "\"data\":{\"object\":{\"customer\":\"cus_123\"}}}";

    restTemplate.exchange(
        "/api/v1/billing/webhook",
        HttpMethod.POST,
        new HttpEntity<>(payload, webhookHeaders(payload)),
        String.class);

    var response =
        restTemplate.exchange(
            "/api/v1/billing/webhook",
            HttpMethod.POST,
            new HttpEntity<>(payload, webhookHeaders(payload)),
            String.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(stripeEventRepository.count()).isEqualTo(1);
  }

  @Test
  void webhook_withInvalidSignature_returns400() {
    String payload =
        "{\"id\":\"evt_test_789\",\"object\":\"event\",\"type\":\"invoice.payment_failed\","
            + "\"data\":{\"object\":{\"customer\":\"cus_123\"}}}";
    HttpHeaders headers = webhookHeadersWithSecret(payload, "wrong-secret");

    var response =
        restTemplate.exchange(
            "/api/v1/billing/webhook",
            HttpMethod.POST,
            new HttpEntity<>(payload, headers),
            String.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
  }

  private HttpHeaders webhookHeaders(String payload) {
    return webhookHeadersWithSecret(payload, WEBHOOK_SECRET);
  }

  private HttpHeaders webhookHeadersWithSecret(String payload, String secret) {
    long timestamp = Instant.now().getEpochSecond();
    String signedPayload = timestamp + "." + payload;
    String signature = hmacSha256Hex(secret, signedPayload);

    HttpHeaders headers = new HttpHeaders();
    headers.add("Stripe-Signature", "t=" + timestamp + ",v1=" + signature);
    return headers;
  }

  private String hmacSha256Hex(String secret, String payload) {
    try {
      Mac mac = Mac.getInstance("HmacSHA256");
      mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
      return HexFormat.of().formatHex(mac.doFinal(payload.getBytes(StandardCharsets.UTF_8)));
    } catch (Exception e) {
      throw new IllegalStateException("Failed to compute signature", e);
    }
  }
}

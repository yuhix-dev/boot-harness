package com.bootharness.billing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;

import com.bootharness.billing.Subscription.Plan;
import com.bootharness.billing.Subscription.Status;
import com.bootharness.billing.dto.SubscriptionResponse;
import com.bootharness.config.AppProperties;
import com.bootharness.user.User;
import com.bootharness.user.UserRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.stripe.exception.SignatureVerificationException;
import com.stripe.model.Event;
import com.stripe.model.EventDataObjectDeserializer;
import com.stripe.net.Webhook;
import java.time.OffsetDateTime;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

@ExtendWith(MockitoExtension.class)
@DisplayName("BillingService")
class BillingServiceTest {

  @Mock private AppProperties appProperties;
  @Mock private UserRepository userRepository;
  @Mock private SubscriptionRepository subscriptionRepository;
  @Mock private StripeEventRepository stripeEventRepository;
  @Mock private ApplicationEventPublisher eventPublisher;
  @Spy private ObjectMapper objectMapper = new ObjectMapper();

  @InjectMocks private BillingService billingService;

  private static final AppProperties.Stripe STRIPE_PROPS =
      new AppProperties.Stripe("sk_test", "whsec_test", "price_starter", "price_pro");

  @Nested
  @DisplayName("getSubscription()")
  class GetSubscription {

    @Test
    @DisplayName("returns subscription details when user has an active subscription")
    void returnsActiveSubscription() {
      User user = User.createLocal("user@example.com", "encoded", "Alice");
      OffsetDateTime periodEnd = OffsetDateTime.now().plusDays(30);
      Subscription sub =
          Subscription.create(
              user, "sub_123", "price_starter", Plan.STARTER, Status.ACTIVE, periodEnd);
      given(subscriptionRepository.findByUser(user)).willReturn(Optional.of(sub));

      SubscriptionResponse response = billingService.getSubscription(user);

      assertThat(response.plan()).isEqualTo("STARTER");
      assertThat(response.status()).isEqualTo(Status.ACTIVE);
      assertThat(response.currentPeriodEnd()).isEqualTo(periodEnd);
    }

    @Test
    @DisplayName("returns free plan when user has no subscription")
    void returnsFreeWhenNone() {
      User user = User.createLocal("user@example.com", "encoded", "Bob");
      given(subscriptionRepository.findByUser(user)).willReturn(Optional.empty());

      SubscriptionResponse response = billingService.getSubscription(user);

      assertThat(response.plan()).isEqualTo("FREE");
      assertThat(response.status()).isNull();
      assertThat(response.currentPeriodEnd()).isNull();
    }
  }

  @Nested
  @DisplayName("handleWebhook()")
  class HandleWebhook {

    @Test
    @DisplayName("throws ResponseStatusException(UNAUTHORIZED) on invalid signature")
    void throwsOnInvalidSignature() {
      given(appProperties.stripe()).willReturn(STRIPE_PROPS);
      try (MockedStatic<Webhook> webhookMock = mockStatic(Webhook.class)) {
        webhookMock
            .when(() -> Webhook.constructEvent(any(), any(), any()))
            .thenThrow(new SignatureVerificationException("bad sig", "sig"));

        assertThatThrownBy(() -> billingService.handleWebhook("payload", "sig"))
            .isInstanceOf(ResponseStatusException.class)
            .satisfies(
                e ->
                    assertThat(((ResponseStatusException) e).getStatusCode())
                        .isEqualTo(HttpStatus.UNAUTHORIZED));
      }
    }

    @Test
    @DisplayName("skips processing for duplicate events")
    void skipsDuplicateEvent() {
      given(appProperties.stripe()).willReturn(STRIPE_PROPS);
      try (MockedStatic<Webhook> webhookMock = mockStatic(Webhook.class)) {
        Event event = mock(Event.class);
        given(event.getId()).willReturn("evt_dup");
        webhookMock.when(() -> Webhook.constructEvent(any(), any(), any())).thenReturn(event);
        given(stripeEventRepository.existsById("evt_dup")).willReturn(true);

        billingService.handleWebhook("payload", "sig");

        then(stripeEventRepository).should(never()).save(any());
      }
    }

    @Test
    @DisplayName("marks subscription as CANCELED on customer.subscription.deleted")
    void cancelsSubscriptionOnDeletion() {
      given(appProperties.stripe()).willReturn(STRIPE_PROPS);
      try (MockedStatic<Webhook> webhookMock = mockStatic(Webhook.class)) {
        Event event = mock(Event.class);
        given(event.getId()).willReturn("evt_del");
        given(event.getType()).willReturn("customer.subscription.deleted");
        EventDataObjectDeserializer deserializer = mock(EventDataObjectDeserializer.class);
        given(event.getDataObjectDeserializer()).willReturn(deserializer);
        given(deserializer.getRawJson()).willReturn("{\"id\":\"sub_abc\"}");

        webhookMock.when(() -> Webhook.constructEvent(any(), any(), any())).thenReturn(event);
        given(stripeEventRepository.existsById("evt_del")).willReturn(false);
        given(stripeEventRepository.save(any())).willAnswer(inv -> inv.getArgument(0));

        User user = User.createLocal("user@example.com", "encoded", "Carol");
        Subscription sub =
            Subscription.create(
                user,
                "sub_abc",
                "price_starter",
                Plan.STARTER,
                Status.ACTIVE,
                OffsetDateTime.now().plusDays(30));
        given(subscriptionRepository.findByStripeSubscriptionId("sub_abc"))
            .willReturn(Optional.of(sub));
        given(subscriptionRepository.save(any())).willAnswer(inv -> inv.getArgument(0));

        billingService.handleWebhook("payload", "sig");

        assertThat(sub.getStatus()).isEqualTo(Status.CANCELED);
        then(subscriptionRepository).should().save(sub);
      }
    }
  }
}

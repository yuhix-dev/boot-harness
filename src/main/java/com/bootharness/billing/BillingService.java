package com.bootharness.billing;

import com.bootharness.billing.Subscription.Plan;
import com.bootharness.billing.Subscription.Status;
import com.bootharness.billing.dto.CheckoutRequest;
import com.bootharness.billing.dto.CheckoutResponse;
import com.bootharness.billing.dto.PortalResponse;
import com.bootharness.billing.dto.SubscriptionResponse;
import com.bootharness.billing.event.PaymentFailedEvent;
import com.bootharness.config.AppProperties;
import com.bootharness.user.User;
import com.bootharness.user.UserRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.stripe.exception.SignatureVerificationException;
import com.stripe.exception.StripeException;
import com.stripe.model.Customer;
import com.stripe.model.checkout.Session;
import com.stripe.net.Webhook;
import com.stripe.param.CustomerCreateParams;
import com.stripe.param.checkout.SessionCreateParams;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@Slf4j
@RequiredArgsConstructor
public class BillingService {

  private final AppProperties appProperties;
  private final UserRepository userRepository;
  private final SubscriptionRepository subscriptionRepository;
  private final StripeEventRepository stripeEventRepository;
  private final ApplicationEventPublisher eventPublisher;
  private final ObjectMapper objectMapper;

  public CheckoutResponse createCheckoutSession(User user, CheckoutRequest request) {
    try {
      String customerId = ensureStripeCustomer(user);
      String priceId = resolvePriceId(request.plan());

      String successUrl =
          request.successUrl() != null
              ? request.successUrl()
              : appProperties.oauth2().redirectBaseUrl() + "/billing/success";
      String cancelUrl =
          request.cancelUrl() != null
              ? request.cancelUrl()
              : appProperties.oauth2().redirectBaseUrl() + "/billing/cancel";

      SessionCreateParams params =
          SessionCreateParams.builder()
              .setCustomer(customerId)
              .setMode(SessionCreateParams.Mode.SUBSCRIPTION)
              .addLineItem(
                  SessionCreateParams.LineItem.builder().setPrice(priceId).setQuantity(1L).build())
              .setSuccessUrl(successUrl)
              .setCancelUrl(cancelUrl)
              .build();

      Session session = Session.create(params);
      return new CheckoutResponse(session.getUrl());
    } catch (StripeException e) {
      log.error("Failed to create checkout session userId={}", user.getId(), e);
      throw new ResponseStatusException(
          HttpStatus.BAD_GATEWAY, "Failed to create checkout session");
    }
  }

  public PortalResponse createPortalSession(User user) {
    try {
      String customerId = ensureStripeCustomer(user);
      String returnUrl = appProperties.oauth2().redirectBaseUrl() + "/billing";

      com.stripe.param.billingportal.SessionCreateParams params =
          com.stripe.param.billingportal.SessionCreateParams.builder()
              .setCustomer(customerId)
              .setReturnUrl(returnUrl)
              .build();

      com.stripe.model.billingportal.Session session =
          com.stripe.model.billingportal.Session.create(params);
      return new PortalResponse(session.getUrl());
    } catch (StripeException e) {
      log.error("Failed to create portal session userId={}", user.getId(), e);
      throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Failed to create portal session");
    }
  }

  public SubscriptionResponse getSubscription(User user) {
    return subscriptionRepository
        .findByUser(user)
        .map(
            s ->
                new SubscriptionResponse(
                    s.getPlan().name(), s.getStatus(), s.getCurrentPeriodEnd()))
        .orElse(SubscriptionResponse.free());
  }

  @Transactional
  public void handleWebhook(String payload, String signature) {
    com.stripe.model.Event event;
    try {
      event = Webhook.constructEvent(payload, signature, appProperties.stripe().webhookSecret());
    } catch (SignatureVerificationException e) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid webhook signature");
    }

    if (stripeEventRepository.existsById(event.getId())) {
      log.info("Skipping duplicate webhook event={}", event.getId());
      return;
    }
    stripeEventRepository.save(new StripeEvent(event.getId()));

    switch (event.getType()) {
      case "checkout.session.completed" -> handleCheckoutCompleted(event);
      case "customer.subscription.updated" -> handleSubscriptionUpdated(event);
      case "customer.subscription.deleted" -> handleSubscriptionDeleted(event);
      case "invoice.payment_failed" -> handlePaymentFailed(event);
      default -> log.debug("Unhandled webhook event={}", event.getType());
    }
  }

  private void handleCheckoutCompleted(com.stripe.model.Event event) {
    try {
      JsonNode node = rawJson(event);
      String subscriptionId = node.path("subscription").asText(null);
      String customerId = node.path("customer").asText(null);
      if (subscriptionId == null || customerId == null) {
        log.warn("checkout.session.completed missing subscription or customer fields");
        return;
      }
      com.stripe.model.Subscription stripeSub =
          com.stripe.model.Subscription.retrieve(subscriptionId);
      User user =
          userRepository
              .findByStripeCustomerId(customerId)
              .orElseThrow(
                  () -> new IllegalStateException("No user for Stripe customer: " + customerId));
      upsertSubscription(user, stripeSub);
      log.info("Subscription activated userId={}", user.getId());
    } catch (StripeException e) {
      log.error("Failed to retrieve subscription from Stripe", e);
      throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Failed to process checkout");
    }
  }

  private void handleSubscriptionUpdated(com.stripe.model.Event event) {
    String subscriptionId = rawJson(event).path("id").asText(null);
    if (subscriptionId == null) return;

    subscriptionRepository
        .findByStripeSubscriptionId(subscriptionId)
        .ifPresent(
            sub -> {
              try {
                upsertSubscription(
                    sub.getUser(), com.stripe.model.Subscription.retrieve(subscriptionId));
                log.info("Subscription updated userId={}", sub.getUser().getId());
              } catch (StripeException e) {
                log.error("Failed to retrieve updated subscription from Stripe", e);
              }
            });
  }

  private void handleSubscriptionDeleted(com.stripe.model.Event event) {
    String subscriptionId = rawJson(event).path("id").asText(null);
    if (subscriptionId == null) return;

    subscriptionRepository
        .findByStripeSubscriptionId(subscriptionId)
        .ifPresentOrElse(
            sub -> {
              sub.setStatus(Status.CANCELED);
              subscriptionRepository.save(sub);
              log.info("Subscription canceled userId={}", sub.getUser().getId());
            },
            () -> log.warn("Deletion for unknown subscription id={}", subscriptionId));
  }

  private void handlePaymentFailed(com.stripe.model.Event event) {
    JsonNode node = rawJson(event);
    String customerId = node.path("customer").asText(null);
    String invoiceUrl = node.path("hosted_invoice_url").asText(null);
    if (customerId == null) return;

    userRepository
        .findByStripeCustomerId(customerId)
        .ifPresentOrElse(
            user -> {
              eventPublisher.publishEvent(new PaymentFailedEvent(user, invoiceUrl));
              log.info("Payment failed userId={}", user.getId());
            },
            () -> log.warn("Payment failed for unknown customer id={}", customerId));
  }

  private void upsertSubscription(User user, com.stripe.model.Subscription stripeSub) {
    String priceId = stripeSub.getItems().getData().getFirst().getPrice().getId();
    Subscription sub = subscriptionRepository.findByUser(user).orElse(new Subscription());
    sub.setUser(user);
    sub.setStripeSubscriptionId(stripeSub.getId());
    sub.setStripePriceId(priceId);
    sub.setPlan(resolvePlan(priceId));
    sub.setStatus(resolveStatus(stripeSub.getStatus()));
    sub.setCurrentPeriodEnd(
        OffsetDateTime.ofInstant(
            Instant.ofEpochSecond(stripeSub.getCurrentPeriodEnd()), ZoneOffset.UTC));
    subscriptionRepository.save(sub);
  }

  private JsonNode rawJson(com.stripe.model.Event event) {
    try {
      return objectMapper.readTree(event.getDataObjectDeserializer().getRawJson());
    } catch (Exception e) {
      throw new IllegalStateException("Failed to parse Stripe event JSON", e);
    }
  }

  private String ensureStripeCustomer(User user) throws StripeException {
    if (user.getStripeCustomerId() != null) return user.getStripeCustomerId();

    Customer customer =
        Customer.create(
            CustomerCreateParams.builder()
                .setEmail(user.getEmail())
                .setName(user.getName())
                .build());

    user.setStripeCustomerId(customer.getId());
    userRepository.save(user);
    return customer.getId();
  }

  private String resolvePriceId(Plan plan) {
    return switch (plan) {
      case STARTER -> appProperties.stripe().priceIdStarter();
      case PRO -> appProperties.stripe().priceIdPro();
    };
  }

  private Plan resolvePlan(String priceId) {
    if (priceId.equals(appProperties.stripe().priceIdStarter())) return Plan.STARTER;
    if (priceId.equals(appProperties.stripe().priceIdPro())) return Plan.PRO;
    throw new IllegalArgumentException("Unknown Stripe price ID: " + priceId);
  }

  private Status resolveStatus(String stripeStatus) {
    return switch (stripeStatus) {
      case "active" -> Status.ACTIVE;
      case "canceled" -> Status.CANCELED;
      case "past_due" -> Status.PAST_DUE;
      default -> Status.INCOMPLETE;
    };
  }
}

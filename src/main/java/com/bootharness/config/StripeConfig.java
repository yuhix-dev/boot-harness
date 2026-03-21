package com.bootharness.config;

import com.stripe.Stripe;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;

@Configuration
@RequiredArgsConstructor
public class StripeConfig {

  private final AppProperties appProperties;

  @PostConstruct
  public void init() {
    Stripe.apiKey = appProperties.stripe().secretKey();
  }
}

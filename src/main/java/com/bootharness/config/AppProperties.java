package com.bootharness.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app")
public record AppProperties(Jwt jwt, Cors cors, Email email, Stripe stripe) {

  public record Jwt(String secret, long accessTokenExpirationMs, long refreshTokenExpirationMs) {}

  public record Cors(String allowedOrigins) {}

  public record Email(String resendApiKey, String fromAddress) {}

  public record Stripe(
      String secretKey, String webhookSecret, String priceIdStarter, String priceIdPro) {}
}

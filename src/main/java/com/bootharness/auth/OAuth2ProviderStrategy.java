package com.bootharness.auth;

import com.bootharness.auth.OauthIdentity.OauthProvider;
import java.util.Map;

/**
 * Strategy for extracting normalized user attributes from a provider-specific OAuth2 attribute map.
 *
 * <p>Implement this interface and annotate with {@code @Component} to support a new OAuth2
 * provider. No other changes are required.
 */
interface OAuth2ProviderStrategy {

  /** Spring Security registration ID (e.g. "github", "google"). */
  String registrationId();

  OauthProvider provider();

  String extractProviderId(Map<String, Object> attributes);

  /** Returns null if the provider did not supply an email address. */
  String extractEmail(Map<String, Object> attributes);

  /**
   * Resolves the email address, performing additional API calls if needed (e.g. GitHub private
   * email). Defaults to {@link #extractEmail(Map)}.
   */
  default String resolveEmail(Map<String, Object> attributes, String accessToken) {
    return extractEmail(attributes);
  }

  String extractName(Map<String, Object> attributes);
}

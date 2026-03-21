package com.bootharness.auth;

import com.bootharness.auth.OauthIdentity.OauthProvider;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
class GoogleOAuth2Strategy implements OAuth2ProviderStrategy {

  @Override
  public String registrationId() {
    return "google";
  }

  @Override
  public OauthProvider provider() {
    return OauthProvider.GOOGLE;
  }

  @Override
  public String extractProviderId(Map<String, Object> attributes) {
    return (String) attributes.get("sub");
  }

  @Override
  public String extractEmail(Map<String, Object> attributes) {
    return (String) attributes.get("email");
  }

  @Override
  public String extractName(Map<String, Object> attributes) {
    return (String) attributes.get("name");
  }
}

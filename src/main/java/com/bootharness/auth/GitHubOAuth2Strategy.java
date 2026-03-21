package com.bootharness.auth;

import com.bootharness.auth.OauthIdentity.OauthProvider;
import java.util.Map;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.stereotype.Component;

@Component
class GitHubOAuth2Strategy implements OAuth2ProviderStrategy {

  @Override
  public String registrationId() {
    return "github";
  }

  @Override
  public OauthProvider provider() {
    return OauthProvider.GITHUB;
  }

  @Override
  public String extractProviderId(Map<String, Object> attributes) {
    Object id = attributes.get("id");
    if (id == null) {
      throw new OAuth2AuthenticationException(
          new OAuth2Error("id_not_found"), "GitHub user ID not available.");
    }
    return String.valueOf(id);
  }

  @Override
  public String extractEmail(Map<String, Object> attributes) {
    return (String) attributes.get("email");
  }

  @Override
  public String extractName(Map<String, Object> attributes) {
    String name = (String) attributes.get("name");
    return name != null ? name : (String) attributes.get("login");
  }
}

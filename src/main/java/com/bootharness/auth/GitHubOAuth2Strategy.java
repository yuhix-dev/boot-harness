package com.bootharness.auth;

import com.bootharness.auth.OauthIdentity.OauthProvider;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
@RequiredArgsConstructor
class GitHubOAuth2Strategy implements OAuth2ProviderStrategy {

  private static final String EMAILS_URL = "https://api.github.com/user/emails";

  private final RestClient restClient;

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

  /** Falls back to /user/emails API when the primary email is private. */
  @Override
  public String resolveEmail(Map<String, Object> attributes, String accessToken) {
    String email = extractEmail(attributes);
    if (email != null) return email;

    List<Map<String, Object>> emails =
        restClient
            .get()
            .uri(EMAILS_URL)
            .header("Authorization", "Bearer " + accessToken)
            .retrieve()
            .body(new ParameterizedTypeReference<>() {});

    if (emails == null) return null;

    return emails.stream()
        .filter(
            e -> Boolean.TRUE.equals(e.get("primary")) && Boolean.TRUE.equals(e.get("verified")))
        .map(e -> (String) e.get("email"))
        .findFirst()
        .orElse(null);
  }

  @Override
  public String extractName(Map<String, Object> attributes) {
    String name = (String) attributes.get("name");
    return name != null ? name : (String) attributes.get("login");
  }
}

package com.bootharness.auth;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.security.oauth2.client.userinfo.DefaultOAuth2UserService;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.user.DefaultOAuth2User;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

/** Loads and provisions an OAuth2 user (e.g. GitHub) after successful authorization. */
@Service
public class CustomOAuth2UserService extends DefaultOAuth2UserService {

  private static final String GITHUB_EMAILS_URL = "https://api.github.com/user/emails";

  private final Map<String, OAuth2ProviderStrategy> strategies;
  private final OAuth2AuthService oauth2AuthService;
  private final RestClient restClient;

  public CustomOAuth2UserService(
      List<OAuth2ProviderStrategy> strategies,
      OAuth2AuthService oauth2AuthService,
      RestClient restClient) {
    this.strategies =
        strategies.stream()
            .collect(Collectors.toMap(OAuth2ProviderStrategy::registrationId, s -> s));
    this.oauth2AuthService = oauth2AuthService;
    this.restClient = restClient;
  }

  @Override
  public OAuth2User loadUser(OAuth2UserRequest userRequest) throws OAuth2AuthenticationException {
    OAuth2User oAuth2User = super.loadUser(userRequest);

    String registrationId = userRequest.getClientRegistration().getRegistrationId();
    OAuth2ProviderStrategy strategy = strategies.get(registrationId);
    if (strategy == null) {
      throw new OAuth2AuthenticationException(
          new OAuth2Error("unsupported_provider"),
          "Unsupported OAuth2 provider: " + registrationId);
    }

    Map<String, Object> attributes = oAuth2User.getAttributes();
    String email = strategy.extractEmail(attributes);

    // GitHub users with private emails need a separate API call to /user/emails
    if (email == null && "github".equals(registrationId)) {
      email = fetchGitHubPrimaryEmail(userRequest.getAccessToken().getTokenValue());
    }

    if (email == null) {
      throw new OAuth2AuthenticationException(
          new OAuth2Error("email_not_found"),
          "Email not available from "
              + registrationId
              + ". Ensure your account has a verified email address.");
    }

    oauth2AuthService.findOrCreateUser(
        email,
        strategy.extractName(attributes),
        strategy.provider(),
        strategy.extractProviderId(attributes));

    // Return a new OAuth2User with the resolved email in attributes so downstream
    // handlers (e.g. OAuth2AuthenticationSuccessHandler) can read it via getAttribute("email").
    if (attributes.get("email") == null) {
      Map<String, Object> enriched = new HashMap<>(attributes);
      enriched.put("email", email);
      String nameAttributeKey =
          userRequest
              .getClientRegistration()
              .getProviderDetails()
              .getUserInfoEndpoint()
              .getUserNameAttributeName();
      return new DefaultOAuth2User(oAuth2User.getAuthorities(), enriched, nameAttributeKey);
    }

    return oAuth2User;
  }

  private String fetchGitHubPrimaryEmail(String accessToken) {
    List<Map<String, Object>> emails =
        restClient
            .get()
            .uri(GITHUB_EMAILS_URL)
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
}

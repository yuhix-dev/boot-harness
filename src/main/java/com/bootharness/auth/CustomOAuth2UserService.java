package com.bootharness.auth;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.security.oauth2.client.userinfo.DefaultOAuth2UserService;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Service;

/** Loads and provisions an OAuth2 user (e.g. GitHub) after successful authorization. */
@Service
public class CustomOAuth2UserService extends DefaultOAuth2UserService {

  private final Map<String, OAuth2ProviderStrategy> strategies;
  private final OAuth2AuthService oauth2AuthService;

  public CustomOAuth2UserService(
      List<OAuth2ProviderStrategy> strategies, OAuth2AuthService oauth2AuthService) {
    this.strategies =
        strategies.stream()
            .collect(Collectors.toMap(OAuth2ProviderStrategy::registrationId, s -> s));
    this.oauth2AuthService = oauth2AuthService;
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
    if (email == null) {
      throw new OAuth2AuthenticationException(
          new OAuth2Error("email_not_found"),
          "Email not available from "
              + registrationId
              + ". Make your email public in provider settings.");
    }

    oauth2AuthService.findOrCreateUser(
        email,
        strategy.extractName(attributes),
        strategy.provider(),
        strategy.extractProviderId(attributes));

    return oAuth2User;
  }
}

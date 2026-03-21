package com.bootharness.auth;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserRequest;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserService;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.stereotype.Service;

/** Loads and provisions an OIDC user (e.g. Google) after successful authorization. */
@Service
public class CustomOidcUserService extends OidcUserService {

  private final Map<String, OAuth2ProviderStrategy> strategies;
  private final OAuth2AuthService oauth2AuthService;

  public CustomOidcUserService(
      List<OAuth2ProviderStrategy> strategies, OAuth2AuthService oauth2AuthService) {
    this.strategies =
        strategies.stream()
            .collect(Collectors.toMap(OAuth2ProviderStrategy::registrationId, s -> s));
    this.oauth2AuthService = oauth2AuthService;
  }

  @Override
  public OidcUser loadUser(OidcUserRequest userRequest) throws OAuth2AuthenticationException {
    OidcUser oidcUser = super.loadUser(userRequest);

    String registrationId = userRequest.getClientRegistration().getRegistrationId();
    OAuth2ProviderStrategy strategy = strategies.get(registrationId);
    if (strategy == null) {
      throw new OAuth2AuthenticationException(
          new OAuth2Error("unsupported_provider"), "Unsupported OIDC provider: " + registrationId);
    }

    Map<String, Object> attributes = oidcUser.getAttributes();
    oauth2AuthService.findOrCreateUser(
        strategy.extractEmail(attributes),
        strategy.extractName(attributes),
        strategy.provider(),
        strategy.extractProviderId(attributes));

    return oidcUser;
  }
}

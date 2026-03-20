package com.bootharness.auth;

import com.bootharness.auth.OauthIdentity.OauthProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserRequest;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserService;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.stereotype.Service;

/** Loads and provisions a Google user after successful OIDC authorization. */
@Service
@RequiredArgsConstructor
public class CustomOidcUserService extends OidcUserService {

  private final OAuth2AuthService oauth2AuthService;

  @Override
  public OidcUser loadUser(OidcUserRequest userRequest) throws OAuth2AuthenticationException {
    OidcUser oidcUser = super.loadUser(userRequest);

    oauth2AuthService.findOrCreateUser(
        oidcUser.getEmail(), oidcUser.getFullName(), OauthProvider.GOOGLE, oidcUser.getSubject());

    return oidcUser;
  }
}

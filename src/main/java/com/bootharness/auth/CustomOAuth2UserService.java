package com.bootharness.auth;

import com.bootharness.auth.OauthIdentity.OauthProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.security.oauth2.client.userinfo.DefaultOAuth2UserService;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Service;

/** Loads and provisions a GitHub user after successful OAuth2 authorization. */
@Service
@RequiredArgsConstructor
public class CustomOAuth2UserService extends DefaultOAuth2UserService {

  private final OAuth2AuthService oauth2AuthService;

  @Override
  public OAuth2User loadUser(OAuth2UserRequest userRequest) throws OAuth2AuthenticationException {
    OAuth2User oAuth2User = super.loadUser(userRequest);

    Integer idAttr = oAuth2User.getAttribute("id");
    if (idAttr == null) {
      throw new OAuth2AuthenticationException(
          new OAuth2Error("id_not_found"), "GitHub user ID not available.");
    }
    String providerId = String.valueOf(idAttr);

    String email = oAuth2User.getAttribute("email");
    if (email == null) {
      throw new OAuth2AuthenticationException(
          new OAuth2Error("email_not_found"),
          "Email not available from GitHub. Make your email public in GitHub settings.");
    }

    String name = oAuth2User.getAttribute("name");
    if (name == null) {
      name = oAuth2User.getAttribute("login");
    }

    oauth2AuthService.findOrCreateUser(email, name, OauthProvider.GITHUB, providerId);
    return oAuth2User;
  }
}

package com.bootharness.auth;

import com.bootharness.config.AppProperties;
import com.bootharness.user.User;
import com.bootharness.user.UserRepository;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.web.authentication.SimpleUrlAuthenticationSuccessHandler;
import org.springframework.stereotype.Component;

/**
 * Issues a JWT pair and redirects the browser to the frontend callback URL after a successful
 * OAuth2 login.
 *
 * <p>Redirect target: {@code
 * {app.oauth2.redirect-base-url}/auth/callback?accessToken=...&refreshToken=...}
 */
@Component
@RequiredArgsConstructor
public class OAuth2AuthenticationSuccessHandler extends SimpleUrlAuthenticationSuccessHandler {

  private final JwtService jwtService;
  private final RefreshTokenRepository refreshTokenRepository;
  private final UserRepository userRepository;
  private final AppProperties appProperties;

  @Override
  public void onAuthenticationSuccess(
      HttpServletRequest request, HttpServletResponse response, Authentication authentication)
      throws IOException {

    String email = extractEmail(authentication.getPrincipal());
    User user =
        userRepository
            .findByEmail(email)
            .orElseThrow(
                () ->
                    new IllegalStateException(
                        "OAuth2 user not found after provisioning: " + email));

    String accessToken = jwtService.generateAccessToken(user.getId(), user.getEmail());
    RefreshToken refreshToken =
        RefreshToken.create(user, appProperties.jwt().refreshTokenExpirationMs());
    refreshTokenRepository.save(refreshToken);

    String redirectUrl =
        appProperties.oauth2().redirectBaseUrl()
            + "/auth/callback"
            + "?accessToken="
            + accessToken
            + "&refreshToken="
            + refreshToken.getToken();

    getRedirectStrategy().sendRedirect(request, response, redirectUrl);
  }

  private String extractEmail(Object principal) {
    if (principal instanceof OidcUser oidcUser) {
      return oidcUser.getEmail();
    }
    return ((OAuth2User) principal).getAttribute("email");
  }
}

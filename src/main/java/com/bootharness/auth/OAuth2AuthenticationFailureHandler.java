package com.bootharness.auth;

import com.bootharness.config.AppProperties;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.authentication.SimpleUrlAuthenticationFailureHandler;
import org.springframework.stereotype.Component;

/**
 * Redirects the browser to the frontend error page when OAuth2 login fails.
 *
 * <p>Redirect target: {@code {app.oauth2.redirect-base-url}/auth/error}
 */
@Component
@RequiredArgsConstructor
public class OAuth2AuthenticationFailureHandler extends SimpleUrlAuthenticationFailureHandler {

  private final AppProperties appProperties;

  @Override
  public void onAuthenticationFailure(
      HttpServletRequest request, HttpServletResponse response, AuthenticationException exception)
      throws IOException {
    getRedirectStrategy()
        .sendRedirect(request, response, appProperties.oauth2().redirectBaseUrl() + "/auth/error");
  }
}

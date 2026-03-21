package com.bootharness.config;

import com.bootharness.auth.CustomOAuth2UserService;
import com.bootharness.auth.CustomOidcUserService;
import com.bootharness.auth.JwtService;
import com.bootharness.auth.OAuth2AuthenticationFailureHandler;
import com.bootharness.auth.OAuth2AuthenticationSuccessHandler;
import com.bootharness.user.UserRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Spring Security configuration.
 *
 * <ul>
 *   <li>JWT authentication for API requests — tokens validated on every request
 *   <li>OAuth2 login for Google and GitHub — state stored in HTTP session (short-lived)
 *   <li>CSRF disabled (API uses JWT — not cookie-based auth)
 *   <li>CORS delegated to {@link CorsConfig}
 * </ul>
 */
@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {

  private final JwtService jwtService;
  private final UserRepository userRepository;
  private final CustomOAuth2UserService customOAuth2UserService;
  private final CustomOidcUserService customOidcUserService;
  private final OAuth2AuthenticationSuccessHandler oAuth2SuccessHandler;
  private final OAuth2AuthenticationFailureHandler oAuth2FailureHandler;

  @Bean
  public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
    http.csrf(AbstractHttpConfigurer::disable)
        .cors(Customizer.withDefaults())
        // Sessions are only created to store the OAuth2 state parameter during the authorization
        // flow. API requests are authenticated via JWT — the jwtAuthFilter below.
        .sessionManagement(
            session -> session.sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED))
        .authorizeHttpRequests(
            auth ->
                auth.requestMatchers(
                        HttpMethod.POST,
                        "/api/v1/auth/register",
                        "/api/v1/auth/login",
                        "/api/v1/auth/refresh",
                        "/api/v1/auth/password/reset/request",
                        "/api/v1/auth/password/reset/confirm")
                    .permitAll()
                    .requestMatchers(HttpMethod.POST, "/api/v1/billing/webhook")
                    .permitAll()
                    .requestMatchers("/oauth2/**", "/login/oauth2/**")
                    .permitAll()
                    .requestMatchers("/actuator/health", "/actuator/info")
                    .permitAll()
                    .anyRequest()
                    .authenticated())
        // Return 401 for unauthenticated API requests instead of redirecting to the login page.
        // The OAuth2 flow is initiated by the client navigating to
        // /oauth2/authorization/{provider}.
        .exceptionHandling(
            ex ->
                ex.authenticationEntryPoint(
                    (request, response, authException) ->
                        response.sendError(HttpServletResponse.SC_UNAUTHORIZED)))
        .oauth2Login(
            oauth2 ->
                oauth2
                    .userInfoEndpoint(
                        userInfo ->
                            userInfo
                                .userService(customOAuth2UserService)
                                .oidcUserService(customOidcUserService))
                    .successHandler(oAuth2SuccessHandler)
                    .failureHandler(oAuth2FailureHandler))
        .addFilterBefore(jwtAuthFilter(), UsernamePasswordAuthenticationFilter.class);

    return http.build();
  }

  @Bean
  public PasswordEncoder passwordEncoder() {
    return new BCryptPasswordEncoder();
  }

  private OncePerRequestFilter jwtAuthFilter() {
    return new OncePerRequestFilter() {
      @Override
      protected void doFilterInternal(
          HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
          throws ServletException, IOException {

        String header = request.getHeader("Authorization");
        if (header == null || !header.startsWith("Bearer ")) {
          filterChain.doFilter(request, response);
          return;
        }

        String token = header.substring(7);
        if (!jwtService.isValid(token)) {
          filterChain.doFilter(request, response);
          return;
        }

        UUID userId = jwtService.validateAndExtractUserId(token);
        userRepository
            .findById(userId)
            .ifPresent(
                user -> {
                  var auth =
                      new org.springframework.security.authentication
                          .UsernamePasswordAuthenticationToken(
                          user,
                          null,
                          List.of(new SimpleGrantedAuthority("ROLE_" + user.getRole().name())));
                  SecurityContextHolder.getContext().setAuthentication(auth);
                });

        filterChain.doFilter(request, response);
      }
    };
  }
}

package com.bootharness.config;

import com.bootharness.auth.JwtService;
import com.bootharness.user.UserRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import java.util.UUID;
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
 *   <li>Stateless JWT authentication — no HTTP session is created
 *   <li>CSRF disabled (stateless API — no cookie-based session to protect)
 *   <li>CORS delegated to {@link CorsConfig}
 * </ul>
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

  private final JwtService jwtService;
  private final UserRepository userRepository;

  public SecurityConfig(JwtService jwtService, UserRepository userRepository) {
    this.jwtService = jwtService;
    this.userRepository = userRepository;
  }

  @Bean
  public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
    http.csrf(AbstractHttpConfigurer::disable)
        .cors(Customizer.withDefaults())
        .sessionManagement(
            session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
        .authorizeHttpRequests(
            auth ->
                auth.requestMatchers(
                        HttpMethod.POST,
                        "/api/v1/auth/register",
                        "/api/v1/auth/login",
                        "/api/v1/auth/refresh")
                    .permitAll()
                    .requestMatchers("/actuator/health", "/actuator/info")
                    .permitAll()
                    .anyRequest()
                    .authenticated())
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

package com.bootharness.auth;

import com.bootharness.config.AppProperties;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.UUID;
import javax.crypto.SecretKey;
import org.springframework.stereotype.Service;

@Service
public class JwtService {

  private final SecretKey signingKey;
  private final long accessTokenExpirationMs;

  public JwtService(AppProperties appProperties) {
    this.signingKey =
        Keys.hmacShaKeyFor(appProperties.jwt().secret().getBytes(StandardCharsets.UTF_8));
    this.accessTokenExpirationMs = appProperties.jwt().accessTokenExpirationMs();
  }

  public String generateAccessToken(UUID userId, String email) {
    long now = System.currentTimeMillis();
    return Jwts.builder()
        .subject(userId.toString())
        .claim("email", email)
        .issuedAt(new Date(now))
        .expiration(new Date(now + accessTokenExpirationMs))
        .signWith(signingKey)
        .compact();
  }

  /** Returns the user ID claim if the token is valid, otherwise throws JwtException. */
  public UUID validateAndExtractUserId(String token) {
    Claims claims =
        Jwts.parser().verifyWith(signingKey).build().parseSignedClaims(token).getPayload();
    return UUID.fromString(claims.getSubject());
  }

  public boolean isValid(String token) {
    try {
      validateAndExtractUserId(token);
      return true;
    } catch (JwtException | IllegalArgumentException e) {
      return false;
    }
  }
}

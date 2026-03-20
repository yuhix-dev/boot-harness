package com.bootharness.auth;

import com.bootharness.user.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

@Entity
@Table(name = "refresh_tokens")
@Getter
@Setter
@NoArgsConstructor
public class RefreshToken {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "user_id", nullable = false)
  private User user;

  @Column(nullable = false, unique = true)
  private String token;

  @Column(name = "expires_at", nullable = false)
  private OffsetDateTime expiresAt;

  @CreationTimestamp
  @Column(name = "created_at", nullable = false, updatable = false)
  private OffsetDateTime createdAt;

  @UpdateTimestamp
  @Column(name = "updated_at", nullable = false)
  private OffsetDateTime updatedAt;

  /** Creates a new refresh token for the given user with the specified expiration window. */
  public static RefreshToken create(User user, long expirationMs) {
    RefreshToken token = new RefreshToken();
    token.user = user;
    token.token = UUID.randomUUID().toString();
    token.expiresAt = OffsetDateTime.now().plusNanos(expirationMs * 1_000_000L);
    return token;
  }

  public boolean isExpired() {
    return OffsetDateTime.now().isAfter(expiresAt);
  }
}

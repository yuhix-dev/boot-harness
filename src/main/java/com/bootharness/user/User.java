package com.bootharness.user;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

@Entity
@Table(name = "users")
@Getter
@Setter
@NoArgsConstructor
public class User {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @Column(nullable = false, unique = true)
  private String email;

  @Column private String password; // null for OAuth2-only users

  @Column private String name;

  @Column(nullable = false)
  @Enumerated(EnumType.STRING)
  private Role role = Role.USER;

  @CreationTimestamp
  @Column(name = "created_at", nullable = false, updatable = false)
  private OffsetDateTime createdAt;

  @UpdateTimestamp
  @Column(name = "updated_at", nullable = false)
  private OffsetDateTime updatedAt;

  /**
   * Creates a local (email/password) user. Password must already be encoded before calling this
   * method.
   */
  public static User createLocal(String email, String encodedPassword, String name) {
    User user = new User();
    user.email = email;
    user.password = encodedPassword;
    user.name = name;
    return user;
  }

  /** Creates an OAuth2 user (no password). */
  public static User createOAuth(String email, String name) {
    User user = new User();
    user.email = email;
    user.name = name;
    return user;
  }

  public enum Role {
    USER,
    ADMIN
  }
}

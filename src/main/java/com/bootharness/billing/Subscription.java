package com.bootharness.billing;

import com.bootharness.user.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

@Entity
@Table(name = "subscriptions")
@Getter
@Setter
@NoArgsConstructor
public class Subscription {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @OneToOne
  @JoinColumn(name = "user_id", nullable = false)
  private User user;

  @Column(name = "stripe_subscription_id", nullable = false, unique = true)
  private String stripeSubscriptionId;

  @Column(name = "stripe_price_id", nullable = false)
  private String stripePriceId;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  private Plan plan;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  private Status status;

  @Column(name = "current_period_end", nullable = false)
  private OffsetDateTime currentPeriodEnd;

  @CreationTimestamp
  @Column(name = "created_at", nullable = false, updatable = false)
  private OffsetDateTime createdAt;

  @UpdateTimestamp
  @Column(name = "updated_at", nullable = false)
  private OffsetDateTime updatedAt;

  public static Subscription create(
      User user,
      String stripeSubscriptionId,
      String stripePriceId,
      Plan plan,
      Status status,
      OffsetDateTime currentPeriodEnd) {
    Subscription s = new Subscription();
    s.user = user;
    s.stripeSubscriptionId = stripeSubscriptionId;
    s.stripePriceId = stripePriceId;
    s.plan = plan;
    s.status = status;
    s.currentPeriodEnd = currentPeriodEnd;
    return s;
  }

  public enum Plan {
    STARTER,
    PRO
  }

  public enum Status {
    ACTIVE,
    CANCELED,
    PAST_DUE,
    INCOMPLETE
  }
}

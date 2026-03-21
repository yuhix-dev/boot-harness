package com.bootharness.billing;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

@Entity
@Table(name = "stripe_events")
@Getter
@NoArgsConstructor
public class StripeEvent {

  @Id private String id; // Stripe event ID (evt_...)

  @CreationTimestamp
  @Column(name = "processed_at", nullable = false, updatable = false)
  private OffsetDateTime processedAt;

  public StripeEvent(String id) {
    this.id = id;
  }
}

package com.bootharness.billing.dto;

import com.bootharness.billing.Subscription.Status;
import java.time.OffsetDateTime;

public record SubscriptionResponse(String plan, Status status, OffsetDateTime currentPeriodEnd) {

  public static SubscriptionResponse free() {
    return new SubscriptionResponse("FREE", null, null);
  }
}

package com.bootharness.billing.dto;

import com.bootharness.billing.Subscription.Plan;
import jakarta.validation.constraints.NotNull;

public record CheckoutRequest(@NotNull Plan plan, String successUrl, String cancelUrl) {}

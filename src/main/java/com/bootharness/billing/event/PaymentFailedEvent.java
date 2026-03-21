package com.bootharness.billing.event;

import com.bootharness.user.User;

public record PaymentFailedEvent(User user, String invoiceUrl) {}

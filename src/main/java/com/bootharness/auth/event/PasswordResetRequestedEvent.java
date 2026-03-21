package com.bootharness.auth.event;

import com.bootharness.user.User;

public record PasswordResetRequestedEvent(User user, String rawToken) {}

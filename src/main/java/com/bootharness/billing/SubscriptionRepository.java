package com.bootharness.billing;

import com.bootharness.user.User;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface SubscriptionRepository extends JpaRepository<Subscription, UUID> {
  Optional<Subscription> findByUser(User user);

  Optional<Subscription> findByStripeSubscriptionId(String stripeSubscriptionId);
}

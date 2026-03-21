package com.bootharness.billing;

import org.springframework.data.jpa.repository.JpaRepository;

interface StripeEventRepository extends JpaRepository<StripeEvent, String> {}

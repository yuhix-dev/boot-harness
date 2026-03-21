ALTER TABLE users ADD COLUMN stripe_customer_id VARCHAR(255) UNIQUE;

CREATE TABLE subscriptions (
    id                     UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id                UUID         NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    stripe_subscription_id VARCHAR(255) NOT NULL UNIQUE,
    stripe_price_id        VARCHAR(255) NOT NULL,
    plan                   VARCHAR(50)  NOT NULL,
    status                 VARCHAR(50)  NOT NULL,
    current_period_end     TIMESTAMP WITH TIME ZONE NOT NULL,
    created_at             TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
    updated_at             TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now()
);

-- Stripe event IDs used for webhook idempotency
CREATE TABLE stripe_events (
    id           VARCHAR(255) PRIMARY KEY,
    processed_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now()
);

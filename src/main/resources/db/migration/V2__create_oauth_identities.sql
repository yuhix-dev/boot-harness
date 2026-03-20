CREATE TABLE oauth_identities (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id     UUID         NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    provider    VARCHAR(50)  NOT NULL,   -- GOOGLE | GITHUB
    provider_id VARCHAR(255) NOT NULL,
    created_at  TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
    updated_at  TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
    UNIQUE (provider, provider_id)
);

CREATE INDEX idx_oauth_identities_user_id ON oauth_identities (user_id);

CREATE EXTENSION IF NOT EXISTS "pgcrypto";

CREATE TABLE IF NOT EXISTS payment_gateway_config (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    gateway     VARCHAR(50)  NOT NULL DEFAULT 'zarinpal',
    merchant_id VARCHAR(255) NOT NULL DEFAULT '',
    sandbox     BOOLEAN      NOT NULL DEFAULT TRUE,
    is_active   BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at  TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

INSERT INTO payment_gateway_config (gateway, merchant_id, sandbox, is_active)
VALUES ('zarinpal', '', TRUE, FALSE)
ON CONFLICT DO NOTHING;

CREATE TABLE IF NOT EXISTS subscription_plans (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name        VARCHAR(100) NOT NULL,
    slug        VARCHAR(50)  NOT NULL UNIQUE,
    price_cents INT          NOT NULL DEFAULT 0,
    currency    VARCHAR(3)   NOT NULL DEFAULT 'IRR',
    interval    VARCHAR(20)  NOT NULL DEFAULT 'monthly',
    features    JSONB        NOT NULL DEFAULT '[]',
    is_active   BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at  TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS user_subscriptions (
    id           UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id      UUID        NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    plan_id      UUID        NOT NULL REFERENCES subscription_plans(id),
    status       VARCHAR(20) NOT NULL DEFAULT 'pending',
    authority    VARCHAR(36),
    ref_id       BIGINT,
    started_at   TIMESTAMPTZ,
    expires_at   TIMESTAMPTZ,
    cancelled_at TIMESTAMPTZ,
    created_at   TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS donation_wallets (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id     UUID NOT NULL REFERENCES users(id),
    currency    VARCHAR(10) NOT NULL,
    address     VARCHAR(200) NOT NULL,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE(user_id, currency)
);

CREATE TABLE IF NOT EXISTS donations (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    donor_id    UUID REFERENCES users(id),
    receiver_id UUID NOT NULL REFERENCES users(id),
    currency    VARCHAR(10) NOT NULL,
    amount      DECIMAL(18, 8) NOT NULL,
    tx_hash     VARCHAR(200),
    created_at  TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_donations_receiver ON donations(receiver_id, created_at DESC);

CREATE INDEX IF NOT EXISTS idx_user_subscriptions_user_id ON user_subscriptions(user_id);
CREATE INDEX IF NOT EXISTS idx_user_subscriptions_authority ON user_subscriptions(authority);

CREATE TABLE IF NOT EXISTS invoices (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id         UUID        NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    subscription_id UUID        REFERENCES user_subscriptions(id),
    amount_cents    INT         NOT NULL,
    currency        VARCHAR(3)  NOT NULL DEFAULT 'IRR',
    status          VARCHAR(20) NOT NULL DEFAULT 'pending',
    payment_method  VARCHAR(50) NOT NULL DEFAULT 'zarinpal',
    payment_gateway VARCHAR(50) NOT NULL DEFAULT 'zarinpal',
    authority       VARCHAR(36),
    ref_id          BIGINT,
    card_pan        VARCHAR(20),
    paid_at         TIMESTAMPTZ,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_invoices_user_id ON invoices(user_id);
CREATE INDEX IF NOT EXISTS idx_invoices_authority ON invoices(authority);

CREATE TABLE IF NOT EXISTS ads (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    title           VARCHAR(200) NOT NULL,
    image_url       TEXT         NOT NULL DEFAULT '',
    target_url      TEXT         NOT NULL DEFAULT '',
    slot            VARCHAR(50)  NOT NULL DEFAULT 'feed',
    category_filter VARCHAR(50)  NOT NULL DEFAULT '',
    impressions     INT          NOT NULL DEFAULT 0,
    clicks          INT          NOT NULL DEFAULT 0,
    is_active       BOOLEAN      NOT NULL DEFAULT TRUE,
    starts_at       TIMESTAMPTZ,
    ends_at         TIMESTAMPTZ,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

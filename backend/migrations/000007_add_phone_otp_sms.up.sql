ALTER TABLE users ADD COLUMN phone VARCHAR(20);
CREATE UNIQUE INDEX idx_users_phone ON users(phone) WHERE phone IS NOT NULL;

CREATE TABLE sms_otps (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    phone           VARCHAR(20) NOT NULL,
    code            VARCHAR(10) NOT NULL,
    purpose         VARCHAR(20) NOT NULL DEFAULT 'auth',
    expires_at      TIMESTAMPTZ NOT NULL,
    used            BOOLEAN DEFAULT FALSE,
    attempts        INTEGER DEFAULT 0,
    created_at      TIMESTAMPTZ DEFAULT NOW()
);

CREATE INDEX idx_sms_otps_phone ON sms_otps(phone, purpose, created_at DESC);

ALTER TABLE notification_preferences
    ADD COLUMN comment_reply_sms BOOLEAN DEFAULT FALSE,
    ADD COLUMN comment_mention_sms BOOLEAN DEFAULT FALSE,
    ADD COLUMN post_published_sms BOOLEAN DEFAULT FALSE,
    ADD COLUMN system_announcement_sms BOOLEAN DEFAULT FALSE;

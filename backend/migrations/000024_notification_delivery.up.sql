-- v1 notification delivery: message prefs, push tokens, idempotency for new_message

ALTER TABLE notification_preferences
    ADD COLUMN IF NOT EXISTS new_message_web BOOLEAN DEFAULT TRUE,
    ADD COLUMN IF NOT EXISTS new_message_push BOOLEAN DEFAULT TRUE,
    ADD COLUMN IF NOT EXISTS new_follower_push BOOLEAN DEFAULT TRUE,
    ADD COLUMN IF NOT EXISTS comment_reply_push BOOLEAN DEFAULT TRUE,
    ADD COLUMN IF NOT EXISTS post_published_push BOOLEAN DEFAULT TRUE;

CREATE TABLE IF NOT EXISTS push_tokens (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id         UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    token           TEXT NOT NULL,
    platform        VARCHAR(20) NOT NULL DEFAULT 'android',
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT push_tokens_token_unique UNIQUE (token)
);

CREATE INDEX IF NOT EXISTS idx_push_tokens_user ON push_tokens(user_id);

-- Prevent duplicate new_message notifications for the same message+recipient
CREATE UNIQUE INDEX IF NOT EXISTS idx_notifications_new_message_idempotent
    ON notifications (user_id, ((data->>'message_id')))
    WHERE type = 'new_message' AND data->>'message_id' IS NOT NULL;

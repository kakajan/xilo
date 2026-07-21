-- System messages, pins, invite links for Telegram-like groups

ALTER TABLE messages DROP CONSTRAINT IF EXISTS messages_type_check;
ALTER TABLE messages ADD CONSTRAINT messages_type_check
    CHECK (type IN ('text', 'image', 'video', 'file', 'system'));

ALTER TABLE messages DROP CONSTRAINT IF EXISTS messages_payload_check;
ALTER TABLE messages ADD CONSTRAINT messages_payload_check CHECK (
    is_deleted
    OR (
        type = 'text'
        AND NULLIF(btrim(content), '') IS NOT NULL
        AND media_id IS NULL
        AND media_url IS NULL
    )
    OR (
        type = 'system'
        AND NULLIF(btrim(content), '') IS NOT NULL
        AND media_id IS NULL
        AND media_url IS NULL
    )
    OR (
        type IN ('image', 'video', 'file')
        AND media_id IS NOT NULL
        AND NULLIF(btrim(media_url), '') IS NOT NULL
    )
);

CREATE TABLE IF NOT EXISTS chat_pins (
    chat_id     UUID NOT NULL REFERENCES chats(id) ON DELETE CASCADE,
    message_id  UUID NOT NULL,
    pinned_by   UUID NOT NULL REFERENCES users(id) ON DELETE RESTRICT,
    pinned_at   TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    PRIMARY KEY (chat_id, message_id),
    CONSTRAINT fk_chat_pins_message
        FOREIGN KEY (chat_id, message_id)
        REFERENCES messages(chat_id, id)
        ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_chat_pins_chat_pinned_at
    ON chat_pins (chat_id, pinned_at DESC);

CREATE TABLE IF NOT EXISTS chat_invite_links (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    chat_id     UUID NOT NULL REFERENCES chats(id) ON DELETE CASCADE,
    token       VARCHAR(64) NOT NULL,
    created_by  UUID NOT NULL REFERENCES users(id) ON DELETE RESTRICT,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    revoked_at  TIMESTAMPTZ,
    use_count   INT NOT NULL DEFAULT 0,
    CONSTRAINT uq_chat_invite_links_token UNIQUE (token)
);

CREATE INDEX IF NOT EXISTS idx_chat_invite_links_chat_active
    ON chat_invite_links (chat_id, created_at DESC)
    WHERE revoked_at IS NULL;

CREATE TABLE chats (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    type                VARCHAR(10) NOT NULL,
    name                VARCHAR(100),
    avatar_url          VARCHAR(500),
    direct_user_low     UUID REFERENCES users(id) ON DELETE RESTRICT,
    direct_user_high    UUID REFERENCES users(id) ON DELETE RESTRICT,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    last_message_at     TIMESTAMPTZ,
    CONSTRAINT chats_type_check CHECK (type IN ('direct', 'group')),
    CONSTRAINT chats_shape_check CHECK (
        (
            type = 'direct'
            AND name IS NULL
            AND direct_user_low IS NOT NULL
            AND direct_user_high IS NOT NULL
            AND direct_user_low::text < direct_user_high::text
        )
        OR
        (
            type = 'group'
            AND name IS NOT NULL
            AND char_length(btrim(name)) BETWEEN 1 AND 100
            AND direct_user_low IS NULL
            AND direct_user_high IS NULL
        )
    )
);

CREATE UNIQUE INDEX uq_chats_direct_pair
    ON chats (direct_user_low, direct_user_high)
    WHERE type = 'direct';
CREATE INDEX idx_chats_last_message
    ON chats (last_message_at DESC NULLS LAST, id DESC);

CREATE TABLE chat_members (
    chat_id             UUID NOT NULL REFERENCES chats(id) ON DELETE CASCADE,
    user_id             UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    role                VARCHAR(10) NOT NULL DEFAULT 'member',
    joined_at           TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    last_read_at        TIMESTAMPTZ,
    is_muted            BOOLEAN NOT NULL DEFAULT FALSE,
    is_archived         BOOLEAN NOT NULL DEFAULT FALSE,
    left_at             TIMESTAMPTZ,
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    PRIMARY KEY (chat_id, user_id),
    CONSTRAINT chat_members_role_check CHECK (role IN ('admin', 'member')),
    CONSTRAINT chat_members_read_after_join_check CHECK (
        last_read_at IS NULL OR last_read_at >= joined_at
    ),
    CONSTRAINT chat_members_left_after_join_check CHECK (
        left_at IS NULL OR left_at >= joined_at
    )
);

CREATE INDEX idx_chat_members_user_active
    ON chat_members (user_id, is_archived, chat_id)
    WHERE left_at IS NULL;
CREATE INDEX idx_chat_members_chat_active
    ON chat_members (chat_id, joined_at, user_id)
    WHERE left_at IS NULL;
CREATE INDEX idx_chat_members_admins
    ON chat_members (chat_id, user_id)
    WHERE left_at IS NULL AND role = 'admin';

CREATE TABLE messages (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    chat_id             UUID NOT NULL REFERENCES chats(id) ON DELETE CASCADE,
    sender_id           UUID NOT NULL,
    type                VARCHAR(10) NOT NULL DEFAULT 'text',
    content             TEXT,
    media_id            UUID REFERENCES media(id) ON DELETE RESTRICT,
    media_url           VARCHAR(500),
    reply_to_id         UUID,
    is_edited           BOOLEAN NOT NULL DEFAULT FALSE,
    is_deleted          BOOLEAN NOT NULL DEFAULT FALSE,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    edited_at           TIMESTAMPTZ,
    deleted_at          TIMESTAMPTZ,
    CONSTRAINT uq_messages_chat_id UNIQUE (chat_id, id),
    CONSTRAINT fk_messages_sender_membership
        FOREIGN KEY (chat_id, sender_id)
        REFERENCES chat_members(chat_id, user_id)
        ON DELETE RESTRICT,
    CONSTRAINT fk_messages_reply_same_chat
        FOREIGN KEY (chat_id, reply_to_id)
        REFERENCES messages(chat_id, id)
        ON DELETE RESTRICT,
    CONSTRAINT messages_type_check CHECK (type IN ('text', 'image', 'video', 'file')),
    CONSTRAINT messages_content_length_check CHECK (
        content IS NULL OR char_length(content) <= 10000
    ),
    CONSTRAINT messages_payload_check CHECK (
        is_deleted
        OR (
            type = 'text'
            AND NULLIF(btrim(content), '') IS NOT NULL
            AND media_id IS NULL
            AND media_url IS NULL
        )
        OR (
            type IN ('image', 'video', 'file')
            AND media_id IS NOT NULL
            AND NULLIF(btrim(media_url), '') IS NOT NULL
        )
    ),
    CONSTRAINT messages_deleted_state_check CHECK (
        (is_deleted AND deleted_at IS NOT NULL)
        OR (NOT is_deleted AND deleted_at IS NULL)
    ),
    CONSTRAINT messages_edited_state_check CHECK (
        (is_edited AND edited_at IS NOT NULL)
        OR (NOT is_edited AND edited_at IS NULL)
    )
);

CREATE INDEX idx_messages_chat_cursor
    ON messages (chat_id, created_at DESC, id DESC);
CREATE INDEX idx_messages_sender
    ON messages (sender_id, created_at DESC);
CREATE INDEX idx_messages_media
    ON messages (media_id)
    WHERE media_id IS NOT NULL;

CREATE TABLE message_reads (
    message_id          UUID NOT NULL,
    chat_id             UUID NOT NULL,
    user_id             UUID NOT NULL,
    read_at             TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    PRIMARY KEY (message_id, user_id),
    CONSTRAINT fk_message_reads_message
        FOREIGN KEY (chat_id, message_id)
        REFERENCES messages(chat_id, id)
        ON DELETE CASCADE,
    CONSTRAINT fk_message_reads_member
        FOREIGN KEY (chat_id, user_id)
        REFERENCES chat_members(chat_id, user_id)
        ON DELETE CASCADE
);

CREATE INDEX idx_message_reads_user
    ON message_reads (user_id, read_at DESC);
CREATE INDEX idx_message_reads_chat
    ON message_reads (chat_id, message_id);

CREATE TABLE message_reactions (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    message_id          UUID NOT NULL,
    chat_id             UUID NOT NULL,
    user_id             UUID NOT NULL,
    reaction            VARCHAR(10) NOT NULL,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT fk_message_reactions_message
        FOREIGN KEY (chat_id, message_id)
        REFERENCES messages(chat_id, id)
        ON DELETE CASCADE,
    CONSTRAINT fk_message_reactions_member
        FOREIGN KEY (chat_id, user_id)
        REFERENCES chat_members(chat_id, user_id)
        ON DELETE CASCADE,
    CONSTRAINT message_reactions_value_check CHECK (
        reaction IN ('👍', '❤️', '😄', '😮', '😢', '😡', '👏', '🎉', '💡', '🔥')
    ),
    CONSTRAINT uq_message_reactions UNIQUE (message_id, user_id, reaction)
);

CREATE INDEX idx_message_reactions_message
    ON message_reactions (message_id, reaction);
CREATE INDEX idx_message_reactions_user
    ON message_reactions (user_id, created_at DESC);

-- Saved Messages chat type
ALTER TABLE chats DROP CONSTRAINT IF EXISTS chats_type_check;
ALTER TABLE chats DROP CONSTRAINT IF EXISTS chats_shape_check;

ALTER TABLE chats
    ADD CONSTRAINT chats_type_check CHECK (type IN ('direct', 'group', 'saved'));

ALTER TABLE chats
    ADD CONSTRAINT chats_shape_check CHECK (
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
        OR
        (
            type = 'saved'
            AND direct_user_low IS NULL
            AND direct_user_high IS NULL
            AND (name IS NULL OR name = 'Saved Messages')
        )
    );

ALTER TABLE chat_members
    ADD COLUMN IF NOT EXISTS is_saved_chat BOOLEAN NOT NULL DEFAULT FALSE;

CREATE UNIQUE INDEX uq_chat_members_one_saved_per_user
    ON chat_members (user_id)
    WHERE is_saved_chat = TRUE;

-- Auth session / device metadata on refresh tokens
ALTER TABLE refresh_tokens
    ADD COLUMN IF NOT EXISTS device_name VARCHAR(255),
    ADD COLUMN IF NOT EXISTS platform VARCHAR(50),
    ADD COLUMN IF NOT EXISTS user_agent TEXT,
    ADD COLUMN IF NOT EXISTS ip VARCHAR(45),
    ADD COLUMN IF NOT EXISTS last_seen_at TIMESTAMPTZ NOT NULL DEFAULT NOW();

-- Chat folders
CREATE TABLE chat_folders (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id     UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    name        VARCHAR(100) NOT NULL,
    sort_order  INT NOT NULL DEFAULT 0,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT chat_folders_name_check CHECK (char_length(btrim(name)) BETWEEN 1 AND 100)
);

CREATE INDEX idx_chat_folders_user_sort
    ON chat_folders (user_id, sort_order, id);

CREATE TABLE chat_folder_items (
    folder_id   UUID NOT NULL REFERENCES chat_folders(id) ON DELETE CASCADE,
    chat_id     UUID NOT NULL REFERENCES chats(id) ON DELETE CASCADE,
    sort_order  INT NOT NULL DEFAULT 0,
    PRIMARY KEY (folder_id, chat_id)
);

CREATE INDEX idx_chat_folder_items_chat
    ON chat_folder_items (chat_id);

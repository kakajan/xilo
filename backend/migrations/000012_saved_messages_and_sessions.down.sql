DROP TABLE IF EXISTS chat_folder_items;
DROP TABLE IF EXISTS chat_folders;

ALTER TABLE refresh_tokens
    DROP COLUMN IF EXISTS last_seen_at,
    DROP COLUMN IF EXISTS ip,
    DROP COLUMN IF EXISTS user_agent,
    DROP COLUMN IF EXISTS platform,
    DROP COLUMN IF EXISTS device_name;

DROP INDEX IF EXISTS uq_chat_members_one_saved_per_user;

ALTER TABLE chat_members
    DROP COLUMN IF EXISTS is_saved_chat;

ALTER TABLE chats DROP CONSTRAINT IF EXISTS chats_shape_check;
ALTER TABLE chats DROP CONSTRAINT IF EXISTS chats_type_check;

ALTER TABLE chats
    ADD CONSTRAINT chats_type_check CHECK (type IN ('direct', 'group'));

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
    );

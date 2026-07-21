DROP TABLE IF EXISTS chat_invite_links;
DROP TABLE IF EXISTS chat_pins;

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
        type IN ('image', 'video', 'file')
        AND media_id IS NOT NULL
        AND NULLIF(btrim(media_url), '') IS NOT NULL
    )
);

ALTER TABLE messages DROP CONSTRAINT IF EXISTS messages_type_check;
ALTER TABLE messages ADD CONSTRAINT messages_type_check
    CHECK (type IN ('text', 'image', 'video', 'file'));

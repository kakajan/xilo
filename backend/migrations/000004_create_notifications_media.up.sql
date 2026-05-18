CREATE TABLE notifications (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id         UUID NOT NULL REFERENCES users(id),
    type            VARCHAR(50) NOT NULL,
    title           VARCHAR(200) NOT NULL,
    body            TEXT,
    data            JSONB,
    is_read         BOOLEAN DEFAULT FALSE,
    created_at      TIMESTAMPTZ DEFAULT NOW()
);

CREATE INDEX idx_notifications_user ON notifications(user_id, created_at DESC);
CREATE INDEX idx_notifications_unread ON notifications(user_id, created_at DESC) WHERE NOT is_read;

CREATE TABLE notification_preferences (
    user_id                     UUID NOT NULL REFERENCES users(id) PRIMARY KEY,
    comment_reply_web           BOOLEAN DEFAULT TRUE,
    comment_reply_email         BOOLEAN DEFAULT TRUE,
    comment_mention_web         BOOLEAN DEFAULT TRUE,
    comment_mention_email       BOOLEAN DEFAULT TRUE,
    post_reaction_web           BOOLEAN DEFAULT TRUE,
    new_follower_web            BOOLEAN DEFAULT TRUE,
    post_published_web          BOOLEAN DEFAULT TRUE,
    post_published_email        BOOLEAN DEFAULT TRUE,
    system_announcement_web     BOOLEAN DEFAULT TRUE,
    system_announcement_email   BOOLEAN DEFAULT TRUE
);

CREATE TABLE media (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id         UUID NOT NULL REFERENCES users(id),
    filename        VARCHAR(255) NOT NULL,
    original_name   VARCHAR(255) NOT NULL,
    mime_type       VARCHAR(100) NOT NULL,
    size_bytes      BIGINT NOT NULL,
    width           INTEGER,
    height          INTEGER,
    variants        JSONB,
    created_at      TIMESTAMPTZ DEFAULT NOW()
);

CREATE INDEX idx_media_user ON media(user_id, created_at DESC);

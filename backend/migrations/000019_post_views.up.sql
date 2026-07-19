ALTER TABLE posts ADD COLUMN IF NOT EXISTS view_count BIGINT NOT NULL DEFAULT 0;

CREATE TABLE IF NOT EXISTS post_view_dedup (
    post_id UUID NOT NULL REFERENCES posts(id) ON DELETE CASCADE,
    viewer_key VARCHAR(64) NOT NULL,
    user_id UUID NULL,
    session_id VARCHAR(100) NOT NULL DEFAULT '',
    last_viewed_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    PRIMARY KEY (post_id, viewer_key)
);

CREATE INDEX IF NOT EXISTS idx_post_view_dedup_last ON post_view_dedup(last_viewed_at);
CREATE INDEX IF NOT EXISTS idx_posts_view_count ON posts(view_count DESC);

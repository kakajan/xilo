-- Author+ amplify: plain comment reposts + quote-comment posts.

ALTER TABLE comments
    ADD COLUMN IF NOT EXISTS repost_count INT NOT NULL DEFAULT 0;

CREATE TABLE IF NOT EXISTS comment_reposts (
    user_id     UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    comment_id  UUID NOT NULL REFERENCES comments(id) ON DELETE CASCADE,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    PRIMARY KEY (user_id, comment_id)
);

CREATE INDEX IF NOT EXISTS idx_comment_reposts_comment_id ON comment_reposts (comment_id);
CREATE INDEX IF NOT EXISTS idx_comment_reposts_user_created
    ON comment_reposts (user_id, created_at DESC);

ALTER TABLE posts
    ADD COLUMN IF NOT EXISTS quoted_comment_id UUID REFERENCES comments(id) ON DELETE SET NULL;

CREATE INDEX IF NOT EXISTS idx_posts_quoted_comment_id ON posts(quoted_comment_id)
    WHERE quoted_comment_id IS NOT NULL;

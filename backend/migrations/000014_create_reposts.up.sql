CREATE TABLE IF NOT EXISTS reposts (
    user_id     UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    post_id     UUID NOT NULL REFERENCES posts(id) ON DELETE CASCADE,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    PRIMARY KEY (user_id, post_id)
);

CREATE INDEX IF NOT EXISTS idx_reposts_post_id ON reposts (post_id);
CREATE INDEX IF NOT EXISTS idx_reposts_user_created
    ON reposts (user_id, created_at DESC);

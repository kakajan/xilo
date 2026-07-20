ALTER TABLE posts
    ADD COLUMN IF NOT EXISTS quoted_post_id UUID REFERENCES posts(id) ON DELETE SET NULL;

CREATE INDEX IF NOT EXISTS idx_posts_quoted_post_id ON posts(quoted_post_id)
    WHERE quoted_post_id IS NOT NULL;

ALTER TABLE posts
    ADD COLUMN post_type VARCHAR(20) NOT NULL DEFAULT 'article',
    ADD COLUMN link_url TEXT,
    ADD COLUMN media_ids UUID[] NOT NULL DEFAULT '{}';

ALTER TABLE posts
    ADD CONSTRAINT posts_post_type_check
    CHECK (post_type IN ('article', 'micro', 'photo', 'video', 'link'));

CREATE INDEX idx_posts_type ON posts (post_type, published_at DESC)
    WHERE status = 'published' AND deleted_at IS NULL;

CREATE INDEX idx_posts_drafts_author ON posts (author_id, created_at DESC)
    WHERE status = 'draft' AND deleted_at IS NULL;

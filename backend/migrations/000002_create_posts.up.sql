CREATE TABLE posts (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    author_id       UUID NOT NULL REFERENCES users(id),
    title           VARCHAR(200) NOT NULL,
    slug            VARCHAR(250) UNIQUE NOT NULL,
    excerpt         VARCHAR(500),
    content         JSONB NOT NULL,
    content_md      TEXT,
    cover_image_url VARCHAR(500),
    category        VARCHAR(100),
    tags            TEXT[] DEFAULT '{}',
    status          VARCHAR(20) DEFAULT 'draft',
    is_premium      BOOLEAN DEFAULT FALSE,
    word_count      INTEGER DEFAULT 0,
    reading_time    INTEGER DEFAULT 1,
    scheduled_at    TIMESTAMPTZ,
    published_at    TIMESTAMPTZ,
    created_at      TIMESTAMPTZ DEFAULT NOW(),
    updated_at      TIMESTAMPTZ DEFAULT NOW(),
    deleted_at      TIMESTAMPTZ
);

CREATE INDEX idx_posts_slug ON posts(slug) WHERE status = 'published';
CREATE INDEX idx_posts_author ON posts(author_id, created_at);
CREATE INDEX idx_posts_status ON posts(status, published_at DESC);
CREATE INDEX idx_posts_category ON posts(category, published_at DESC) WHERE status = 'published';
CREATE INDEX idx_posts_tags ON posts USING GIN(tags);

CREATE TABLE post_versions (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    post_id         UUID NOT NULL REFERENCES posts(id) ON DELETE CASCADE,
    title           VARCHAR(200),
    content         JSONB,
    content_md      TEXT,
    version         INTEGER NOT NULL,
    created_at      TIMESTAMPTZ DEFAULT NOW()
);

CREATE INDEX idx_post_versions_post ON post_versions(post_id, version DESC);

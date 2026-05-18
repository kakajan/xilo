CREATE TABLE comments (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    post_id         UUID NOT NULL REFERENCES posts(id) ON DELETE CASCADE,
    author_id       UUID NOT NULL REFERENCES users(id),
    parent_id       UUID REFERENCES comments(id) ON DELETE CASCADE,
    root_id         UUID REFERENCES comments(id) ON DELETE CASCADE,
    depth           SMALLINT NOT NULL DEFAULT 0,
    content         TEXT NOT NULL,
    content_html    TEXT,
    media_url       VARCHAR(500),
    is_pinned       BOOLEAN DEFAULT FALSE,
    is_spam         BOOLEAN DEFAULT FALSE,
    created_at      TIMESTAMPTZ DEFAULT NOW(),
    updated_at      TIMESTAMPTZ DEFAULT NOW(),
    deleted_at      TIMESTAMPTZ
);

CREATE INDEX idx_comments_post ON comments(post_id, created_at DESC) WHERE deleted_at IS NULL;
CREATE INDEX idx_comments_root ON comments(root_id, created_at) WHERE deleted_at IS NULL;
CREATE INDEX idx_comments_author ON comments(author_id, created_at);

CREATE TABLE reactions (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id         UUID NOT NULL REFERENCES users(id),
    target_type     VARCHAR(10) NOT NULL,
    target_id       UUID NOT NULL,
    reaction        VARCHAR(10) NOT NULL,
    created_at      TIMESTAMPTZ DEFAULT NOW(),
    UNIQUE(user_id, target_type, target_id, reaction)
);

CREATE INDEX idx_reactions_target ON reactions(target_type, target_id);

CREATE TABLE bookmarks (
    user_id         UUID NOT NULL REFERENCES users(id),
    post_id         UUID NOT NULL REFERENCES posts(id) ON DELETE CASCADE,
    created_at      TIMESTAMPTZ DEFAULT NOW(),
    PRIMARY KEY(user_id, post_id)
);

CREATE TABLE follows (
    follower_id     UUID NOT NULL REFERENCES users(id),
    following_id    UUID NOT NULL REFERENCES users(id),
    created_at      TIMESTAMPTZ DEFAULT NOW(),
    PRIMARY KEY(follower_id, following_id),
    CHECK(follower_id != following_id)
);

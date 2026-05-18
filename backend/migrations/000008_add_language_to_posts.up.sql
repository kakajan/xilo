ALTER TABLE posts ADD COLUMN language VARCHAR(5) NOT NULL DEFAULT 'fa';

ALTER TABLE posts ADD CONSTRAINT chk_posts_language
    CHECK (language IN ('fa', 'en', 'ar', 'ru', 'tr'));

CREATE INDEX idx_posts_language ON posts(language, published_at DESC)
    WHERE status = 'published';

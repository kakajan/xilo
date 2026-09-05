DROP INDEX IF EXISTS idx_posts_drafts_author;
DROP INDEX IF EXISTS idx_posts_type;

ALTER TABLE posts
    DROP CONSTRAINT IF EXISTS posts_post_type_check;

ALTER TABLE posts
    DROP COLUMN IF EXISTS media_ids,
    DROP COLUMN IF EXISTS link_url,
    DROP COLUMN IF EXISTS post_type;

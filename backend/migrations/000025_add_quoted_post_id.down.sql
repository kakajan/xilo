DROP INDEX IF EXISTS idx_posts_quoted_post_id;
ALTER TABLE posts DROP COLUMN IF EXISTS quoted_post_id;

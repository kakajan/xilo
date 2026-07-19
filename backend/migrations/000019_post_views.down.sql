DROP INDEX IF EXISTS idx_posts_view_count;
DROP INDEX IF EXISTS idx_post_view_dedup_last;
DROP TABLE IF EXISTS post_view_dedup;
ALTER TABLE posts DROP COLUMN IF EXISTS view_count;

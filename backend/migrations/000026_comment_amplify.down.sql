DROP INDEX IF EXISTS idx_posts_quoted_comment_id;
ALTER TABLE posts DROP COLUMN IF EXISTS quoted_comment_id;

DROP INDEX IF EXISTS idx_comment_reposts_user_created;
DROP INDEX IF EXISTS idx_comment_reposts_comment_id;
DROP TABLE IF EXISTS comment_reposts;

ALTER TABLE comments DROP COLUMN IF EXISTS repost_count;

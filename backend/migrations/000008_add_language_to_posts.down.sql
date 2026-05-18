ALTER TABLE posts DROP CONSTRAINT IF EXISTS chk_posts_language;
DROP INDEX IF EXISTS idx_posts_language;
ALTER TABLE posts DROP COLUMN IF EXISTS language;

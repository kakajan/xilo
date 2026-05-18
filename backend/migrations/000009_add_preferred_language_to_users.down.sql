ALTER TABLE users DROP CONSTRAINT IF EXISTS chk_users_preferred_language;
ALTER TABLE users DROP COLUMN IF EXISTS preferred_language;

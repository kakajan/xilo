DROP INDEX IF EXISTS idx_users_email_hash;
DROP INDEX IF EXISTS idx_users_phone_hash;

ALTER TABLE users
    DROP COLUMN IF EXISTS email_hash,
    DROP COLUMN IF EXISTS phone_hash;

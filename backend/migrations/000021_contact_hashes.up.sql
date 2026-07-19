-- Contact match hashes (HMAC-SHA256 hex). Backfill of existing rows is performed
-- by the application on startup via UserRepo.BackfillHashes (pepper must stay in app).

ALTER TABLE users
    ADD COLUMN IF NOT EXISTS phone_hash CHAR(64) NULL,
    ADD COLUMN IF NOT EXISTS email_hash CHAR(64) NULL;

CREATE INDEX IF NOT EXISTS idx_users_phone_hash ON users (phone_hash) WHERE phone_hash IS NOT NULL;
CREATE INDEX IF NOT EXISTS idx_users_email_hash ON users (email_hash) WHERE email_hash IS NOT NULL;

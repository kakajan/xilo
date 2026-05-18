ALTER TABLE users ADD COLUMN preferred_language VARCHAR(5) NOT NULL DEFAULT 'fa';

ALTER TABLE users ADD CONSTRAINT chk_users_preferred_language
    CHECK (preferred_language IN ('fa', 'en', 'ar', 'ru', 'tr'));

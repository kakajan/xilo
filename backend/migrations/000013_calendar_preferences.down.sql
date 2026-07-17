DELETE FROM platform_settings WHERE key = 'calendar_defaults';

DROP TABLE IF EXISTS platform_settings;

ALTER TABLE users DROP CONSTRAINT IF EXISTS chk_users_preferred_calendar;
ALTER TABLE users DROP COLUMN IF EXISTS preferred_calendar;

ALTER TABLE notification_preferences
    DROP COLUMN IF EXISTS comment_reply_sms,
    DROP COLUMN IF EXISTS comment_mention_sms,
    DROP COLUMN IF EXISTS post_published_sms,
    DROP COLUMN IF EXISTS system_announcement_sms;

DROP TABLE IF EXISTS sms_otps;

DROP INDEX IF EXISTS idx_users_phone;
ALTER TABLE users DROP COLUMN IF EXISTS phone;

DROP INDEX IF EXISTS idx_notifications_new_message_idempotent;
DROP INDEX IF EXISTS idx_push_tokens_user;
DROP TABLE IF EXISTS push_tokens;

ALTER TABLE notification_preferences
    DROP COLUMN IF EXISTS new_message_web,
    DROP COLUMN IF EXISTS new_message_push,
    DROP COLUMN IF EXISTS new_follower_push,
    DROP COLUMN IF EXISTS comment_reply_push,
    DROP COLUMN IF EXISTS post_published_push;

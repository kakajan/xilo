ALTER TABLE users ADD COLUMN preferred_calendar VARCHAR(10) NOT NULL DEFAULT 'auto';

ALTER TABLE users ADD CONSTRAINT chk_users_preferred_calendar
    CHECK (preferred_calendar IN ('auto', 'jalali', 'gregorian'));

CREATE TABLE IF NOT EXISTS platform_settings (
    key TEXT PRIMARY KEY,
    value JSONB NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

INSERT INTO platform_settings (key, value)
VALUES (
    'calendar_defaults',
    '{"fa":"jalali","en":"gregorian","ar":"gregorian","ru":"gregorian","tr":"gregorian"}'::jsonb
)
ON CONFLICT (key) DO NOTHING;

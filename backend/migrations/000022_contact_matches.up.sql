CREATE TABLE IF NOT EXISTS user_contact_matches (
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    matched_user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    matched_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    PRIMARY KEY (user_id, matched_user_id),
    CONSTRAINT user_contact_matches_no_self CHECK (user_id <> matched_user_id)
);

CREATE INDEX IF NOT EXISTS idx_user_contact_matches_user_id
    ON user_contact_matches (user_id);

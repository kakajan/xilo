-- Default platform admin for the Aile deploy (idempotent).
-- Login: faslolkhitab@gmail.com / (bootstrap password hashed below)
INSERT INTO users (
    email,
    username,
    phone,
    password_hash,
    display_name,
    role,
    email_verified
)
VALUES (
    'faslolkhitab@gmail.com',
    'faslolkhitab',
    '09112746075',
    '$argon2id$v=19$m=65536,t=3,p=4$7F+j8VsmVHTLfLogvxquVw$6s3bwMrIMRq00dev1anHvKyl7FXs7zI3K0uR281G3hk',
    'آیله',
    'superadmin',
    TRUE
)
ON CONFLICT (email) DO UPDATE
SET
    phone = EXCLUDED.phone,
    password_hash = EXCLUDED.password_hash,
    role = 'superadmin',
    email_verified = TRUE,
    updated_at = NOW();

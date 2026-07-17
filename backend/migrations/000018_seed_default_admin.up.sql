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
    '$argon2id$v=19$m=65536,t=3,p=4$XnZUju0jBwOCLE5UrE+Rrw$mtTLMdEiLh/M568FeOrj9pcFgj34ZezAJSZ+7PXFFCY',
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

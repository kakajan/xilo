CREATE TABLE IF NOT EXISTS interests (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    slug        VARCHAR(64) NOT NULL UNIQUE,
    labels      JSONB NOT NULL,
    icon        VARCHAR(64),
    sort_order  INT NOT NULL DEFAULT 0,
    is_active   BOOLEAN NOT NULL DEFAULT TRUE,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_interests_active_sort
    ON interests (sort_order ASC, slug ASC)
    WHERE is_active = TRUE;

CREATE TABLE IF NOT EXISTS user_interests (
    user_id     UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    interest_id UUID NOT NULL REFERENCES interests(id) ON DELETE CASCADE,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    PRIMARY KEY (user_id, interest_id)
);

CREATE INDEX IF NOT EXISTS idx_user_interests_interest
    ON user_interests (interest_id);

INSERT INTO interests (slug, labels, icon, sort_order) VALUES
    ('technology',  '{"en":"Technology","fa":"فناوری"}'::jsonb,       'cpu',            0),
    ('science',     '{"en":"Science","fa":"علم"}'::jsonb,              'flask-conical',  1),
    ('business',    '{"en":"Business","fa":"کسب‌وکار"}'::jsonb,         'briefcase',      2),
    ('art',         '{"en":"Art","fa":"هنر"}'::jsonb,                  'palette',        3),
    ('literature',  '{"en":"Literature","fa":"ادبیات"}'::jsonb,        'book-open',      4),
    ('music',       '{"en":"Music","fa":"موسیقی"}'::jsonb,             'music',          5),
    ('cinema',      '{"en":"Cinema","fa":"سینما"}'::jsonb,             'film',           6),
    ('sports',      '{"en":"Sports","fa":"ورزش"}'::jsonb,              'trophy',         7),
    ('travel',      '{"en":"Travel","fa":"سفر"}'::jsonb,               'plane',          8),
    ('food',        '{"en":"Food","fa":"غذا"}'::jsonb,                 'utensils',       9),
    ('health',      '{"en":"Health","fa":"سلامت"}'::jsonb,             'heart-pulse',    10),
    ('education',   '{"en":"Education","fa":"آموزش"}'::jsonb,          'graduation-cap', 11),
    ('history',     '{"en":"History","fa":"تاریخ"}'::jsonb,            'landmark',       12),
    ('photography', '{"en":"Photography","fa":"عکاسی"}'::jsonb,        'camera',         13),
    ('nature',      '{"en":"Nature","fa":"طبیعت"}'::jsonb,             'leaf',           14),
    ('comedy',      '{"en":"Comedy","fa":"کمدی"}'::jsonb,              'smile',          15),
    ('fashion',     '{"en":"Fashion","fa":"مد"}'::jsonb,               'shirt',          16),
    ('psychology',  '{"en":"Psychology","fa":"روان‌شناسی"}'::jsonb,    'brain',          17)
ON CONFLICT (slug) DO NOTHING;

INSERT INTO platform_settings (key, value)
VALUES (
    'brand',
    '{
      "name_fa": "آیله",
      "name_en": "aile",
      "display": "آیله | aile"
    }'::jsonb
)
ON CONFLICT (key) DO NOTHING;

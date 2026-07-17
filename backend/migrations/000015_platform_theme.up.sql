INSERT INTO platform_settings (key, value)
VALUES (
    'theme',
    '{
      "light": {
        "primary": "#1D9BF0",
        "primary_hover": "#1A8CD8",
        "primary_pressed": "#1A7BC5",
        "primary_surface": "#E8F5FE",
        "background": "#FFFFFF",
        "background_secondary": "#F7F9FA",
        "background_tertiary": "#EFF3F4",
        "text_primary": "#0F1419",
        "text_secondary": "#536471",
        "text_tertiary": "#8295A3",
        "border": "#EFF3F4",
        "border_strong": "#CFD9DE",
        "error": "#F4212E",
        "success": "#00BA7C",
        "warning": "#FFAD1F",
        "bubble_own": "#E8F5FE",
        "bubble_others": "#F7F9FA"
      },
      "dark": {
        "primary": "#1D9BF0",
        "primary_hover": "#4DB8F5",
        "primary_pressed": "#6BC9F7",
        "primary_surface": "#1A2A3A",
        "background": "#15202B",
        "background_secondary": "#192734",
        "background_tertiary": "#22303C",
        "text_primary": "#E7E9EA",
        "text_secondary": "#71767B",
        "text_tertiary": "#536471",
        "border": "#38444D",
        "border_strong": "#4A5A66",
        "error": "#F4212E",
        "success": "#00BA7C",
        "warning": "#FFAD1F",
        "bubble_own": "#1E3A5F",
        "bubble_others": "#2C2C2E"
      }
    }'::jsonb
)
ON CONFLICT (key) DO NOTHING;

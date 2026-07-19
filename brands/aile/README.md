# Aile brand assets

Canonical source SVGs for the first Xilo deploy brand (**آیله | aile**).

| File | Role |
|------|------|
| `rect-app-icon.svg` | App icon (mark on rounded blue square) — launcher, favicon, PWA |
| `logo-raw-colored.svg` | Mark only, brand gradient — compact UI (navbar, feed, onboarding) |
| `logo-raw-mono.svg` | Mark only, single-color — tintable / monochrome adaptive layer |
| `aile-text-logo-persian.svg` | Persian wordmark — auth/hero when locale is fa/ar |
| `aile-text-logo-english.svg` | English wordmark — auth/hero when locale is en/ru/tr |
| `AileLogo.svg` | Full lockup (mark + bilingual wordmarks) — splash / wide marketing |

## Where they are wired

- **Web**: `web/public/brand/aile/` (+ `web/src/app/icon.png`)
- **Android**: density PNGs under `android/app/src/main/res/drawable-*dpi/`, launcher under `mipmap-*`

Regenerate Android/web rasters from these SVGs with Inkscape when the source art changes.

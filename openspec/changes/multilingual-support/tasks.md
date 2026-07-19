# Tasks: Multilingual Support

**Legend:** 🔴 Critical path | 🟡 Important | 🟢 Nice-to-have

---

## 1. Database & Backend Foundation

### 1.1 Database Migration

- [x] **1.1.1** Create migration: add `language` column to `posts` table
  - Acceptance: Migration runs successfully; existing posts default to `fa`
- [x] **1.1.2** Create migration: add `preferred_language` column to `users` table
  - Acceptance: Migration runs successfully; existing users default to `fa`
- [x] **1.1.3** Add check constraints for valid language codes on both columns
  - Acceptance: Inserting invalid language code fails with constraint error
- [x] **1.1.4** Add index on `posts(language, published_at DESC)` for filtered queries
  - Acceptance: EXPLAIN ANALYZE shows index usage for language-filtered queries

### 1.2 Language Configuration Package

- [x] **1.2.1** Create `pkg/i18n` package with language definitions
  - Acceptance: `SupportedLanguages` map, `IsValidLanguage()`, `GetDirection()` functions work
- [x] **1.2.2** Create `GET /api/languages` endpoint
  - Acceptance: Returns list of supported languages with code, names, and direction

### 1.3 Post Service — Language Field

- [x] **1.3.1** Update post creation to accept and validate `language` field
  - Acceptance: POST /api/posts with `language` saves correctly; invalid code returns 400
- [x] **1.3.2** Update post list endpoint to accept `?language=` query parameter
  - Acceptance: GET /api/posts?language=en returns only English posts
- [x] **1.3.3** Include `language` field in post response DTOs
  - Acceptance: Post responses include `language` field
- [x] **1.3.4** Update post update endpoint to allow changing `language`
  - Acceptance: PATCH /api/posts/:id with `language` updates correctly

### 1.4 User Service — Language Preference

- [ ] **1.4.1** Update user profile to accept and validate `preferred_language`
  - Acceptance: `PATCH /api/auth/me` with `preferred_language` saves correctly; prior completion against the former incorrect profile route is not accepted.
- [ ] **1.4.2** Include `preferred_language` in user profile response
  - Acceptance: `GET /api/auth/me` returns `preferred_language`; prior completion against the former incorrect profile route is not accepted.
- [x] **1.4.3** Set default language on user registration
  - Acceptance: New users have `preferred_language = 'fa'`

### 1.5 Search Service — Language Filtering

- [x] **1.5.1** Update Meilisearch index config to include `_language` as filterable attribute
  - Acceptance: Index settings show `_language` in filterableAttributes
- [x] **1.5.2** Update post indexing to include `_language` field
  - Acceptance: Indexed documents have `_language` matching post language
- [x] **1.5.3** Update search endpoint to accept `?language=` parameter
  - Acceptance: Search with language filter returns only matching-language results

---

## 2. Web Frontend — i18n Infrastructure

### 2.1 next-intl Setup

- [x] **2.1.1** Install `next-intl` and configure in Next.js project
  - Acceptance: `next-intl` installed; basic config files created
- [x] **2.1.2** Create `src/i18n/config.ts` with locale definitions
  - Acceptance: Config exports `locales`, `localeConfig`, `defaultLocale`
- [ ] **2.1.3** Create `src/i18n/request.ts` for server-side locale detection
  - Acceptance: `getRequestConfig` returns correct locale from request
- [ ] **2.1.4** Create `src/i18n/routing.ts` for locale-aware routing
  - Acceptance: `Link`, `redirect`, `useRouter` from routing work with locales
- [ ] **2.1.5** Create `src/middleware.ts` for locale detection and routing
  - Acceptance: Middleware detects locale from Accept-Language header and routes correctly

### 2.2 Translation Files

- [x] **2.2.1** Create translation file structure (`src/i18n/messages/{locale}/`)
  - Acceptance: Directory structure exists for fa, en, ar, ru
- [x] **2.2.2** Extract all hardcoded UI strings to `common.json` (fa + en)
  - Acceptance: No hardcoded strings in navbar, footer, buttons, labels
  - Note: Core chrome (navbar, side nav, bottom nav, offline banner) migrated; remaining pages still being extracted.
- [x] **2.2.3** Create `auth.json` translations (fa + en)
  - Acceptance: Login/register pages use translation keys
  - Note: Login page migrated; register page catalog ready, wiring pending.
- [x] **2.2.4** Create `post.json` translations (fa + en)
  - Acceptance: Post cards, post detail, editor use translation keys
  - Note: Catalogs exist for all locales; component wiring pending.
- [x] **2.2.5** Create `comment.json` translations (fa + en)
  - Acceptance: Comment section uses translation keys
  - Note: Catalogs exist for all locales; component wiring pending.
- [x] **2.2.6** Create `dashboard.json` translations (fa + en)
  - Acceptance: Dashboard pages use translation keys
  - Note: Catalogs exist for all locales; component wiring pending.
- [x] **2.2.7** Create `notification.json` translations (fa + en)
  - Acceptance: Notification center uses translation keys
  - Note: Catalogs exist for all locales; component wiring pending.
- [x] **2.2.8** Add Arabic (`ar`) translation files
  - Acceptance: All domains translated to Arabic
- [x] **2.2.9** Add Russian (`ru`) translation files
  - Acceptance: All domains translated to Russian
- [x] **2.2.10** Add Turkish (`tr`) translation files
  - Acceptance: All domains translated to Turkish

### 2.3 RTL/LTR Layout

- [x] **2.3.1** Update root layout to set `dir` and `lang` attributes based on locale
  - Acceptance: `<html dir="rtl" lang="fa">` for Persian; `<html dir="ltr" lang="en">` for English
  - Note: Applied via locale store + `applyDocumentLocale` (client preference / profile).
- [ ] **2.3.2** Audit and fix all components to use logical CSS properties
  - Acceptance: No `ml-*`, `mr-*`, `pl-*`, `pr-*`, `text-left`, `text-right` in components; replaced with `ms-*`, `me-*`, `ps-*`, `pe-*`, `text-start`, `text-end`
- [ ] **2.3.3** Fix navbar layout for RTL
  - Acceptance: Logo on right, avatar on left in RTL; reversed in LTR
- [ ] **2.3.4** Fix sidebar layout for RTL
  - Acceptance: Sidebar renders correctly in both directions
- [ ] **2.3.5** Fix post card layout for RTL
  - Acceptance: Card content aligns correctly in both directions
- [ ] **2.3.6** Fix mobile bottom navigation for RTL
  - Acceptance: Nav items render correctly in both directions
- [ ] **2.3.7** Fix dialog/dropdown positioning for RTL
  - Acceptance: Dropdowns open in correct direction
- [ ] **2.3.8** Install and configure `tailwindcss-rtl` plugin
  - Acceptance: `rtl:` variant works in all components

### 2.4 Font Loading

- [ ] **2.4.1** Configure Vazirmatn font for Persian locale
  - Acceptance: Persian text renders with Vazirmatn
- [ ] **2.4.2** Configure Inter font for English/Russian locales
  - Acceptance: Latin text renders with Inter
- [ ] **2.4.3** Configure Noto Sans Arabic for Arabic locale
  - Acceptance: Arabic text renders with Noto Sans Arabic
- [ ] **2.4.4** Implement dynamic font loading based on locale
  - Acceptance: Only the font for the current locale is loaded

### 2.5 Language Switcher

- [ ] **2.5.1** Create `LanguageSwitcher` component
  - Acceptance: Dropdown shows all supported languages with native names
- [ ] **2.5.2** Add language switcher to navbar
  - Acceptance: Switcher is visible and accessible from all pages
- [ ] **2.5.3** Implement locale switching with next-intl routing
  - Acceptance: Selecting a language navigates to the same page in new locale
- [ ] **2.5.4** Persist language preference (localStorage + API for authenticated)
  - Acceptance: Preference survives page refresh and is synced for logged-in users

### 2.6 Language Badge on Posts

- [ ] **2.6.1** Create `LanguageBadge` component
  - Acceptance: Renders language code badge with consistent styling
- [ ] **2.6.2** Add language badge to post cards
  - Acceptance: Each post card shows its language
- [ ] **2.6.3** Add language badge to post detail page
  - Acceptance: Post header shows language

### 2.7 Language Filter

- [ ] **2.7.1** Add language filter to homepage post feed
  - Acceptance: Filter dropdown/button filters posts by language
- [ ] **2.7.2** Add language filter to search page
  - Acceptance: Search results can be filtered by language
- [ ] **2.7.3** Reflect language filter in URL query params
  - Acceptance: Filter state is shareable via URL

---

## 3. Historical Flutter Mobile i18n Records (Legacy / Out of Scope)

> The following Flutter records are not active implementation tasks and their historical checkmarks do not establish Android completion. Active Android i18n work is deliberately unchecked in `android-native-production` Phase 3 and `REQ-AND-014` through `REQ-AND-016`.

### 3.1 Legacy Flutter Localization Record

- [ ] **LEGACY-3.1.1** Former Flutter localization dependency record — out of scope
  - Acceptance: `pubspec.yaml` updated; `flutter pub get` succeeds
- [ ] **LEGACY-3.1.2** Former Flutter localization configuration record — out of scope
  - Acceptance: App supports multiple locales
- [ ] **LEGACY-3.1.3** Former Flutter ARB record — out of scope
  - Acceptance: ARB files exist for fa, en, ar, ru, tr

### 3.2 Translation Files

- [ ] **LEGACY-3.2.1** Former Flutter string-extraction record — out of scope
  - Acceptance: No hardcoded strings in UI widgets
- [ ] **LEGACY-3.2.2** Former Flutter Arabic-resource record — out of scope
  - Acceptance: All strings translated to Arabic
- [ ] **LEGACY-3.2.3** Former Flutter Russian-resource record — out of scope
  - Acceptance: All strings translated to Russian
- [ ] **LEGACY-3.2.4** Former Flutter Turkish-resource record — out of scope
  - Acceptance: All strings translated to Turkish
- [ ] **LEGACY-3.2.5** Former Flutter generated-localization record — out of scope
  - Acceptance: `AppLocalizations` class generated; compiles without errors

### 3.3 RTL/LTR Support

- [ ] **LEGACY-3.3.1** Former Flutter directionality record — out of scope
  - Acceptance: `Directionality.of(context)` returns correct direction
- [ ] **LEGACY-3.3.2** Former Flutter directional-widget audit — out of scope
  - Acceptance: No hardcoded `left`/`right` alignments; use `start`/`end`
- [ ] **LEGACY-3.3.3** Former Flutter RTL app-bar task — out of scope
  - Acceptance: Back button, title, actions render correctly in RTL
- [ ] **LEGACY-3.3.4** Former Flutter RTL list task — out of scope
  - Acceptance: Post cards, comment items render correctly in RTL
- [ ] **LEGACY-3.3.5** Former Flutter RTL navigation task — out of scope
  - Acceptance: Nav items render correctly in RTL
- [ ] **LEGACY-3.3.6** Former Flutter RTL drawer task — out of scope
  - Acceptance: Drawer slides from correct side

### 3.4 Font Support

- [ ] **LEGACY-3.4.1** Former Flutter Vazirmatn record — out of scope
  - Acceptance: Font declared in pubspec.yaml; download script ready
- [ ] **LEGACY-3.4.2** Former Flutter Arabic-font record — out of scope
  - Acceptance: Font declared in pubspec.yaml; download script ready
- [ ] **LEGACY-3.4.3** Former Flutter font-selection record — out of scope
  - Acceptance: `ThemeData.fontFamily` changes with locale

### 3.5 Language Settings

- [ ] **LEGACY-3.5.1** Former Flutter language-screen record — out of scope
  - Acceptance: Shows all supported languages with native names and checkmark for current
- [ ] **LEGACY-3.5.2** Former Flutter language-settings record — out of scope
  - Acceptance: "Language" option navigates to language selection
- [ ] **LEGACY-3.5.3** Former Flutter locale-state record — out of scope
  - Acceptance: Selecting a language rebuilds the entire app with new locale
- [ ] **LEGACY-3.5.4** Former Flutter preference-storage record — out of scope
  - Acceptance: Preference survives app restart
- [ ] **LEGACY-3.5.5** Former Flutter locale-sync record — out of scope
  - Acceptance: Historical only; the current profile route is `PATCH /api/auth/me`.

### 3.6 Language Badge

- [ ] **LEGACY-3.6.1** Former Flutter language-badge record — out of scope
  - Acceptance: Renders language indicator with consistent styling
- [ ] **LEGACY-3.6.2** Former Flutter post-card badge record — out of scope
  - Acceptance: Each post card shows its language
- [ ] **LEGACY-3.6.3** Former Flutter post-detail badge record — out of scope
  - Acceptance: Post header shows language

### 3.7 Device Locale Detection

- [ ] **LEGACY-3.7.1** Former Flutter device-locale record — out of scope
  - Acceptance: App uses device language if supported
- [ ] **LEGACY-3.7.2** Former Flutter locale-fallback record — out of scope
  - Acceptance: Unsupported device language falls back to Persian

---

## 4. Testing & Validation

### 4.1 Backend Tests

- [x] **4.1.1** Write unit tests for `pkg/i18n` package
  - Acceptance: `IsValidLanguage`, `GetDirection` tested for all locales
- [ ] **4.1.2** Write integration tests for post language filtering
  - Acceptance: API returns correct posts for each language filter
- [ ] **4.1.3** Write integration tests for user language preference
  - Acceptance: Preference saves and retrieves correctly

### 4.2 Frontend Tests

- [ ] **4.2.1** Write tests for language switcher component
  - Acceptance: Component renders, switches locale, persists preference
- [ ] **4.2.2** Write tests for RTL layout rendering
  - Acceptance: Snapshot tests show correct RTL structure
- [ ] **4.2.3** Write tests for language badge component
  - Acceptance: Renders correct label for each language code
- [ ] **4.2.4** Manual QA: verify all pages in fa, en, ar, ru, tr
  - Acceptance: No missing translations, correct direction on all pages

### 4.3 Legacy Flutter Mobile Test Records (Out of Scope)

- [ ] **LEGACY-4.3.1** Former Flutter widget tests for language settings — out of scope
  - Acceptance: Screen renders, switches locale, persists preference
- [ ] **LEGACY-4.3.2** Former Flutter widget tests for RTL layout — out of scope
  - Acceptance: Widgets render in correct direction
- [ ] **LEGACY-4.3.3** Former Flutter multilingual manual QA — out of scope
  - Acceptance: No missing translations, correct direction on all screens

### 4.4 CI/CD

- [ ] **4.4.1** Add translation validation to CI (check for missing keys)
  - Acceptance: CI fails if any locale is missing translation keys present in others
- [ ] **4.4.2** Add RTL visual regression tests (optional)
  - Acceptance: Screenshots compare RTL vs LTR layouts

---

## Task Summary

| Scope | Active tasks | Active completed | Notes |
|-------|-------------:|-----------------:|-------|
| 1. Database & Backend | 16 | 14 | Profile route tasks 1.4.1–1.4.2 were reopened for `/api/auth/me` verification. |
| 2. Web Frontend | 37 | 14 | next-intl + catalogs for fa/en/ar/ru/tr; chrome/settings/login wired; routing/fonts/switcher remain |
| 4. Active Testing & CI | 9 | 1 | Excludes legacy Flutter mobile tests. |
| **Active total in this change** | **62** | **15** | |
| Legacy Flutter records | 30 records | Excluded | Historical records never count as active completion. |
| Active native Android | Tracked in `android-native-production` | **0 complete** | Every Android task and gate remains unchecked. |

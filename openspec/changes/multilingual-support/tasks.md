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

- [x] **1.4.1** Update user profile to accept and validate `preferred_language`
  - Acceptance: PATCH /api/users/me with `preferred_language` saves correctly
- [x] **1.4.2** Include `preferred_language` in user profile response
  - Acceptance: GET /api/users/me returns `preferred_language`
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

- [ ] **2.1.1** Install `next-intl` and configure in Next.js project
  - Acceptance: `next-intl` installed; basic config files created
- [ ] **2.1.2** Create `src/i18n/config.ts` with locale definitions
  - Acceptance: Config exports `locales`, `localeConfig`, `defaultLocale`
- [ ] **2.1.3** Create `src/i18n/request.ts` for server-side locale detection
  - Acceptance: `getRequestConfig` returns correct locale from request
- [ ] **2.1.4** Create `src/i18n/routing.ts` for locale-aware routing
  - Acceptance: `Link`, `redirect`, `useRouter` from routing work with locales
- [ ] **2.1.5** Create `src/middleware.ts` for locale detection and routing
  - Acceptance: Middleware detects locale from Accept-Language header and routes correctly

### 2.2 Translation Files

- [ ] **2.2.1** Create translation file structure (`src/i18n/messages/{locale}/`)
  - Acceptance: Directory structure exists for fa, en, ar, ru
- [ ] **2.2.2** Extract all hardcoded UI strings to `common.json` (fa + en)
  - Acceptance: No hardcoded strings in navbar, footer, buttons, labels
- [ ] **2.2.3** Create `auth.json` translations (fa + en)
  - Acceptance: Login/register pages use translation keys
- [ ] **2.2.4** Create `post.json` translations (fa + en)
  - Acceptance: Post cards, post detail, editor use translation keys
- [ ] **2.2.5** Create `comment.json` translations (fa + en)
  - Acceptance: Comment section uses translation keys
- [ ] **2.2.6** Create `dashboard.json` translations (fa + en)
  - Acceptance: Dashboard pages use translation keys
- [ ] **2.2.7** Create `notification.json` translations (fa + en)
  - Acceptance: Notification center uses translation keys
- [ ] **2.2.8** Add Arabic (`ar`) translation files
  - Acceptance: All domains translated to Arabic
- [ ] **2.2.9** Add Russian (`ru`) translation files
  - Acceptance: All domains translated to Russian
- [ ] **2.2.10** Add Turkish (`tr`) translation files
  - Acceptance: All domains translated to Turkish

### 2.3 RTL/LTR Layout

- [ ] **2.3.1** Update root layout to set `dir` and `lang` attributes based on locale
  - Acceptance: `<html dir="rtl" lang="fa">` for Persian; `<html dir="ltr" lang="en">` for English
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

## 3. Mobile App — i18n Infrastructure

### 3.1 Flutter Localization Setup

- [x] **3.1.1** Add `flutter_localizations` and `intl` dependencies
  - Acceptance: `pubspec.yaml` updated; `flutter pub get` succeeds
- [x] **3.1.2** Configure `MaterialApp` with localization delegates
  - Acceptance: App supports multiple locales
- [x] **3.1.3** Create ARB file structure (`lib/l10n/`)
  - Acceptance: ARB files exist for fa, en, ar, ru, tr

### 3.2 Translation Files

- [x] **3.2.1** Extract all hardcoded strings to ARB files (fa + en)
  - Acceptance: No hardcoded strings in UI widgets
- [x] **3.2.2** Create Arabic (`ar`) ARB file
  - Acceptance: All strings translated to Arabic
- [x] **3.2.3** Create Russian (`ru`) ARB file
  - Acceptance: All strings translated to Russian
- [x] **3.2.4** Create Turkish (`tr`) ARB file
  - Acceptance: All strings translated to Turkish
- [x] **3.2.5** Run `flutter gen-l10n` to generate localization code
  - Acceptance: `AppLocalizations` class generated; compiles without errors

### 3.3 RTL/LTR Support

- [x] **3.3.1** Verify `Directionality` is set correctly per locale
  - Acceptance: `Directionality.of(context)` returns correct direction
- [ ] **3.3.2** Audit and fix all widgets to use directional properties
  - Acceptance: No hardcoded `left`/`right` alignments; use `start`/`end`
- [ ] **3.3.3** Fix app bar layout for RTL
  - Acceptance: Back button, title, actions render correctly in RTL
- [ ] **3.3.4** Fix list items for RTL
  - Acceptance: Post cards, comment items render correctly in RTL
- [ ] **3.3.5** Fix bottom navigation for RTL
  - Acceptance: Nav items render correctly in RTL
- [ ] **3.3.6** Fix drawer/sidebar for RTL
  - Acceptance: Drawer slides from correct side

### 3.4 Font Support

- [x] **3.4.1** Add Vazirmatn font to Flutter assets
  - Acceptance: Font declared in pubspec.yaml; download script ready
- [x] **3.4.2** Add Noto Sans Arabic font to Flutter assets
  - Acceptance: Font declared in pubspec.yaml; download script ready
- [x] **3.4.3** Configure dynamic font family based on locale
  - Acceptance: `ThemeData.fontFamily` changes with locale

### 3.5 Language Settings

- [x] **3.5.1** Create language selection screen
  - Acceptance: Shows all supported languages with native names and checkmark for current
- [x] **3.5.2** Add language settings to user settings page
  - Acceptance: "Language" option navigates to language selection
- [x] **3.5.3** Implement locale switching with Riverpod provider
  - Acceptance: Selecting a language rebuilds the entire app with new locale
- [x] **3.5.4** Persist language preference in Hive
  - Acceptance: Preference survives app restart
- [x] **3.5.5** Sync language preference with backend (if authenticated)
  - Acceptance: Language change calls `PATCH /api/users/me`

### 3.6 Language Badge

- [x] **3.6.1** Create `LanguageBadge` widget
  - Acceptance: Renders language indicator with consistent styling
- [x] **3.6.2** Add language badge to post cards
  - Acceptance: Each post card shows its language
- [x] **3.6.3** Add language badge to post detail screen
  - Acceptance: Post header shows language

### 3.7 Device Locale Detection

- [x] **3.7.1** Implement device locale detection on first launch
  - Acceptance: App uses device language if supported
- [x] **3.7.2** Implement fallback to default locale (`fa`) for unsupported device languages
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

### 4.3 Mobile Tests

- [ ] **4.3.1** Write widget tests for language settings screen
  - Acceptance: Screen renders, switches locale, persists preference
- [ ] **4.3.2** Write widget tests for RTL layout
  - Acceptance: Widgets render in correct direction
- [ ] **4.3.3** Manual QA: verify all screens in fa, en, ar, ru, tr
  - Acceptance: No missing translations, correct direction on all screens

### 4.4 CI/CD

- [ ] **4.4.1** Add translation validation to CI (check for missing keys)
  - Acceptance: CI fails if any locale is missing translation keys present in others
- [ ] **4.4.2** Add RTL visual regression tests (optional)
  - Acceptance: Screenshots compare RTL vs LTR layouts

---

## Task Summary

| Phase | Tasks | Focus |
|-------|-------|-------|
| 1. Database & Backend | 14 | Migration, language config, API changes, search |
| 2. Web Frontend | 25 | next-intl, translations, RTL, fonts, switcher, badges |
| 3. Mobile App | 22/22 | flutter_localizations, ARB files, RTL, fonts, settings |
| 4. Testing & Validation | 10 | Unit/integration tests, manual QA, CI |
| **Total** | **71** | **Mobile: 22 complete** |

# Delta for Web Frontend (i18n)

## ADDED Requirements

### Requirement: i18n Framework Integration
The web application SHALL use `next-intl` for internationalization, integrated with Next.js 15 App Router.

#### Scenario: Translation loading
- GIVEN a user visits any page
- WHEN the page renders
- THEN all UI text is displayed in the user's selected language
- AND missing keys fall back to the default language (`fa`)

---

### Requirement: Language Configuration
The application SHALL support a configurable set of locales with direction metadata.

```typescript
// i18n/config.ts
export const locales = ['fa', 'en', 'ar', 'ru', 'tr'] as const
export type Locale = (typeof locales)[number]

export const localeConfig: Record<Locale, { direction: 'rtl' | 'ltr'; name: string }> = {
  fa: { direction: 'rtl', name: 'فارسی' },
  en: { direction: 'ltr', name: 'English' },
  ar: { direction: 'rtl', name: 'العربية' },
  ru: { direction: 'ltr', name: 'Русский' },
  tr: { direction: 'ltr', name: 'Türkçe' },
}

export const defaultLocale: Locale = 'fa'
```

---

### Requirement: RTL/LTR Layout Switching
The application SHALL automatically switch layout direction based on the selected locale.

#### Scenario: RTL layout
- GIVEN the user selects `fa` or `ar`
- WHEN the page renders
- THEN the `<html>` element has `dir="rtl"` and `lang="fa"` (or `ar`)
- AND all layout components (navbar, sidebar, cards) are mirrored
- AND text alignment defaults to right

#### Scenario: LTR layout
- GIVEN the user selects `en` or `ru`
- WHEN the page renders
- THEN the `<html>` element has `dir="ltr"` and `lang="en"` (or `ru`)
- AND all layout components render in standard left-to-right order

---

### Requirement: Language Switcher Component
A language switcher SHALL be accessible from the navbar for all users.

#### Scenario: Switch language
- GIVEN a user clicks the language switcher in the navbar
- WHEN they select a different language
- THEN the UI immediately re-renders in the new language
- AND the preference is persisted (localStorage for anonymous, API for authenticated)
- AND the URL updates to reflect the new locale (if using locale routing)

#### Scenario: Language switcher UI
- GIVEN the language switcher is rendered
- THEN it displays the current language name natively
- AND shows a dropdown with all supported languages
- AND the current language is highlighted/checked

---

### Requirement: Translation Files
All UI strings SHALL be externalized into translation files organized by locale and domain.

```
src/
├── i18n/
│   ├── config.ts
│   ├── request.ts              — next-intl request config
│   ├── routing.ts              — locale-aware routing
│   └── messages/
│       ├── fa/
│       │   ├── common.json     — shared strings (buttons, labels)
│       │   ├── auth.json       — login/register
│       │   ├── post.json       — post-related strings
│       │   ├── comment.json    — comment-related strings
│       │   ├── dashboard.json  — dashboard strings
│       │   └── notification.json
│       ├── en/
│       │   ├── common.json
│       │   ├── auth.json
│       │   ├── post.json
│       │   ├── comment.json
│       │   ├── dashboard.json
│       │   └── notification.json
│       ├── ar/
│       │   └── ...
│       └── ru/
│           └── ...
```

#### Scenario: Translation key structure
- GIVEN a translation file `common.json`
- THEN keys use dot notation: `common.actions.save`, `common.errors.notFound`
- AND values support interpolation: `"Hello, {name}!"`
- AND values support pluralization: `"{count, plural, one {comment} other {comments}}"`

---

### Requirement: Language Badge on Posts
Each post card and post detail page SHALL display a badge indicating the post's language.

#### Scenario: Post language badge
- GIVEN a post with `language: "en"`
- WHEN the post card renders
- THEN a small badge shows `EN` (or the language name in the current UI language)
- AND the badge is visually distinct but non-intrusive

---

### Requirement: RTL-Aware Components
All components SHALL be direction-aware using Tailwind's logical properties and the `rtl:` variant.

#### Rules:
- Use `start`/`end` instead of `left`/`right` for spacing and positioning
- Use `ms-*` (margin-start) and `me-*` (margin-end) instead of `ml-*`/`mr-*`
- Use `ps-*` (padding-start) and `pe-*` (padding-end) instead of `pl-*`/`pr-*`
- Use `text-start` and `text-end` instead of `text-left`/`text-right`
- Use `rtl:` prefix for RTL-specific overrides when needed

#### Scenario: Navbar in RTL
- GIVEN the navbar renders in `fa` locale
- THEN the logo is on the right, user avatar on the left
- AND search input text aligns right
- AND dropdown menus open towards the left

#### Scenario: Post card in RTL
- GIVEN a post card renders in `ar` locale
- THEN the cover image, title, and excerpt align right
- AND the author avatar is on the right side of the author name
- AND the "read more" link is on the left

---

### Requirement: Font Loading per Locale
The application SHALL load appropriate fonts for each locale.

#### Scenario: Persian font
- GIVEN the locale is `fa`
- THEN the Vazirmatn (or similar Persian) font is loaded and applied

#### Scenario: English font
- GIVEN the locale is `en`
- THEN the Inter (or similar Latin) font is loaded and applied

#### Scenario: Arabic font
- GIVEN the locale is `ar`
- THEN an appropriate Arabic font (e.g., Noto Sans Arabic) is loaded

---

### Requirement: Language Preference Persistence
The user's language preference SHALL be persisted and restored.

#### Scenario: Anonymous user
- GIVEN an unauthenticated user selects a language
- THEN the preference is stored in localStorage
- AND restored on subsequent visits

#### Scenario: Authenticated user
- GIVEN an authenticated user selects a language
- THEN the preference is sent to the backend via `PATCH /api/auth/me`
- AND restored from `GET /api/auth/me` on login
- AND synced across devices

---

### Requirement: Language Filter on Post List
The homepage and search pages SHALL allow filtering posts by language.

#### Scenario: Filter by language
- GIVEN a user on the homepage
- WHEN they select a language filter
- THEN the post feed updates to show only posts in that language
- AND the filter state is reflected in the URL query params

---

### Requirement: Server-Side Locale Detection
The server SHALL detect the user's preferred locale from:
1. User profile (if authenticated)
2. `Accept-Language` header
3. localStorage value (client-side after hydration)
4. Platform default (`fa`)

#### Scenario: First visit
- GIVEN a first-time visitor with browser language `en-US`
- WHEN they visit the site
- THEN the UI renders in English (closest match from supported locales)

#### Scenario: Returning authenticated user
- GIVEN a user with `preferred_language: "fa"` in their profile
- WHEN they visit from any device
- THEN the UI renders in Persian

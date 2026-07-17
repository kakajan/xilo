# Historical Delta for Flutter Mobile i18n (Legacy / Out of Scope)

> **Non-normative historical record.** The Flutter requirements below are retained for audit history only. They SHALL NOT direct active implementation or count as completed work. The active Android i18n requirements are `REQ-AND-014` through `REQ-AND-016` in `openspec/changes/android-native-production/specs/android-i18n/spec.md`.

## Historical Requirements

### Requirement: Flutter Localization Setup
The mobile app SHALL use `flutter_localizations` and `intl` packages for internationalization.

#### Scenario: App initializes with locale
- GIVEN the app starts
- WHEN the localization delegate initializes
- THEN the app uses the device locale if supported, otherwise the default locale (`fa`)

---

### Requirement: Supported Locales
The app SHALL support the same locales as the web frontend.

```dart
// lib/core/constants/locales.dart
const supportedLocales = [
  Locale('fa'),
  Locale('en'),
  Locale('ar'),
  Locale('ru'),
  Locale('tr'),
];

const defaultLocale = Locale('fa');

Map<String, String> localeDirection = {
  'fa': 'rtl',
  'en': 'ltr',
  'ar': 'rtl',
  'ru': 'ltr',
  'tr': 'ltr',
};
```

---

### Requirement: RTL/LTR Layout Direction
The app SHALL automatically set the text direction based on the selected locale.

#### Scenario: RTL mode
- GIVEN the user selects Persian or Arabic
- WHEN the app renders
- THEN `Directionality.of(context)` returns `TextDirection.rtl`
- AND all widgets that respect directionality are mirrored (app bar, lists, drawers)

#### Scenario: LTR mode
- GIVEN the user selects English or Russian
- WHEN the app renders
- THEN `Directionality.of(context)` returns `TextDirection.ltr`
- AND all widgets render in standard left-to-right order

---

### Requirement: Translation Files
All UI strings SHALL be externalized into ARB files organized by locale.

```
lib/
├── l10n/
│   ├── app_fa.arb
│   ├── app_en.arb
│   ├── app_ar.arb
│   ├── app_ru.arb
│   └── app_en.arb (template)
```

#### Scenario: Translation key structure
- GIVEN an ARB file
- THEN keys use snake_case: `app_title`, `login_button`, `post_read_more`
- AND values support placeholders: `"Hello, {name}!"`
- AND values support plurals via `@` syntax

---

### Requirement: Language Settings
The app SHALL provide a language selection screen in user settings.

#### Scenario: Change language
- GIVEN a user in settings
- WHEN they tap "Language" and select a new language
- THEN the app immediately rebuilds with the new locale
- AND the preference is saved to Hive
- AND synced with the backend if authenticated

#### Scenario: Language settings UI
- GIVEN the language settings screen
- THEN it shows all supported languages with their native names
- AND the current language is indicated with a checkmark
- AND the list is scrollable if more languages are added

---

### Requirement: Language Preference Storage
The user's language preference SHALL be stored locally and synced with the backend.

#### Scenario: Local storage
- GIVEN a user changes their language
- THEN the preference is saved to Hive (`user_preferences` box)
- AND restored on app launch before the API call completes

#### Scenario: Backend sync
- GIVEN an authenticated user changes their language
- THEN the preference is sent to `PATCH /api/auth/me`
- AND on login, the user's `preferred_language` from the API overrides the local value

---

### Requirement: Language Badge on Posts
The mobile app SHALL display a language indicator on post cards and post detail screens.

#### Scenario: Post card language badge
- GIVEN a post with `language: "en"`
- WHEN the post card renders
- THEN a small badge or icon shows the language code
- AND the badge uses the current UI language for the label

---

### Requirement: RTL-Aware Widgets
All custom widgets SHALL respect the current `Directionality`.

#### Rules:
- Use `Directionality.of(context)` to determine layout direction
- Use `start`/`end` alignments instead of `left`/`right`
- Use `EdgeInsetsDirectional` instead of `EdgeInsets` for padding/margin
- Wrap custom layouts in `Directionality` when direction-specific behavior is needed

#### Scenario: Post list in RTL
- GIVEN the post feed renders in Persian
- THEN the list items align right
- AND the author avatar appears on the right side
- AND the trailing icons (bookmark, share) appear on the left

#### Scenario: App bar in RTL
- GIVEN the app bar renders in Arabic
- THEN the back button appears on the right
- AND the title aligns right
- AND action icons appear on the left

---

### Requirement: Font Support per Locale
The app SHALL use appropriate fonts for each locale.

#### Scenario: Persian font
- GIVEN the locale is `fa`
- THEN the Vazirmatn font is used for all text

#### Scenario: English font
- GIVEN the locale is `en`
- THEN the default system font (or a configured Latin font) is used

#### Scenario: Arabic font
- GIVEN the locale is `ar`
- THEN an appropriate Arabic font (e.g., Noto Sans Arabic) is used

---

### Requirement: Device Locale Detection
The app SHALL detect and use the device's system locale on first launch.

#### Scenario: First launch with supported locale
- GIVEN the device language is English
- WHEN the app launches for the first time
- THEN the app uses English

#### Scenario: First launch with unsupported locale
- GIVEN the device language is Japanese (not supported)
- WHEN the app launches for the first time
- THEN the app falls back to the default locale (`fa`)

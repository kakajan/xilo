# Design Document: Multilingual Support

## 1. Architecture Overview

```
┌─────────────────────────────────────────────────────────────┐
│                        Client Layer                          │
├──────────────────────────┬──────────────────────────────────┤
│       Web (Next.js)      │       Mobile (Flutter)            │
│  ┌────────────────────┐  │  ┌────────────────────────────┐  │
│  │ next-intl          │  │  │ flutter_localizations      │  │
│  │ locale routing     │  │  │ intl / ARB files           │  │
│  │ RTL via dir attr   │  │  │ Directionality widget      │  │
│  │ localStorage/API   │  │  │ Hive + API sync            │  │
│  └─────────┬──────────┘  │  └────────────┬───────────────┘  │
└────────────┼─────────────┴───────────────┼───────────────────┘
             │                             │
             └──────────────┬──────────────┘
                            │
┌───────────────────────────▼──────────────────────────────────┐
│                      API Gateway                              │
│  ┌─────────────────────────────────────────────────────────┐ │
│  │ Accept-Language header parsing                          │ │
│  │ Language validation middleware                          │ │
│  └──────────────────────┬──────────────────────────────────┘ │
└─────────────────────────┼────────────────────────────────────┘
                          │
┌─────────────────────────▼────────────────────────────────────┐
│                      Backend Services                         │
│  ┌──────────────┐  ┌──────────────┐  ┌──────────────────┐   │
│  │ User Service │  │ Post Service │  │ Search Service   │   │
│  │ preferred_   │  │ language     │  │ language filter  │   │
│  │ language     │  │ field        │  │ Meilisearch      │   │
│  └──────┬───────┘  └──────┬───────┘  └────────┬─────────┘   │
└─────────┼─────────────────┼───────────────────┼──────────────┘
          │                 │                   │
┌─────────▼─────────────────▼───────────────────▼──────────────┐
│                      Data Layer                               │
│  ┌──────────────┐  ┌──────────────┐  ┌──────────────────┐   │
│  │ PostgreSQL   │  │    Redis     │  │   Meilisearch    │   │
│  │ users.       │  │ cache keys   │  │ filterable       │   │
│  │ preferred_   │  │ include      │  │ attribute:       │   │
│  │ language     │  │ language     │  │ _language        │   │
│  │ posts.       │  │              │  │                  │   │
│  │ language     │  │              │  │                  │   │
│  └──────────────┘  └──────────────┘  └──────────────────┘   │
└─────────────────────────────────────────────────────────────┘
```

## 2. Database Schema Changes

### Migration: Add language to posts

```sql
-- Add language column to posts
ALTER TABLE posts ADD COLUMN language VARCHAR(5) NOT NULL DEFAULT 'fa';

-- Add check constraint for valid language codes
ALTER TABLE posts ADD CONSTRAINT chk_posts_language
    CHECK (language IN ('fa', 'en', 'ar', 'ru', 'tr'));

-- Index for language filtering
CREATE INDEX idx_posts_language ON posts(language, published_at DESC)
    WHERE status = 'published';

-- Add preferred_language to users
ALTER TABLE users ADD COLUMN preferred_language VARCHAR(5) NOT NULL DEFAULT 'fa';

-- Add check constraint for valid language codes
ALTER TABLE users ADD CONSTRAINT chk_users_preferred_language
    CHECK (preferred_language IN ('fa', 'en', 'ar', 'ru', 'tr'));
```

### Updated Posts Table (relevant columns)

```sql
CREATE TABLE posts (
    -- ... existing columns ...
    language        VARCHAR(5) NOT NULL DEFAULT 'fa',
    -- ... existing columns ...
);
```

### Updated Users Table (relevant column)

```sql
CREATE TABLE users (
    -- ... existing columns ...
    preferred_language VARCHAR(5) NOT NULL DEFAULT 'fa',
    -- ... existing columns ...
);
```

## 3. Language Configuration

### Backend Configuration

```go
// pkg/i18n/config.go
package i18n

type Language struct {
    Code         string `json:"code"`
    NameNative   string `json:"name_native"`
    NameEnglish  string `json:"name_english"`
    Direction    string `json:"direction"` // "rtl" or "ltr"
}

var SupportedLanguages = map[string]Language{
    "fa": {Code: "fa", NameNative: "فارسی", NameEnglish: "Persian", Direction: "rtl"},
    "en": {Code: "en", NameNative: "English", NameEnglish: "English", Direction: "ltr"},
    "ar": {Code: "ar", NameNative: "العربية", NameEnglish: "Arabic", Direction: "rtl"},
    "ru": {Code: "ru", NameNative: "Русский", NameEnglish: "Russian", Direction: "ltr"},
    "tr": {Code: "tr", NameNative: "Türkçe", NameEnglish: "Turkish", Direction: "ltr"},
}

const DefaultLanguage = "fa"

func IsValidLanguage(code string) bool {
    _, ok := SupportedLanguages[code]
    return ok
}

func GetDirection(code string) string {
    if lang, ok := SupportedLanguages[code]; ok {
        return lang.Direction
    }
    return SupportedLanguages[DefaultLanguage].Direction
}
```

### Web Frontend Configuration

```typescript
// src/i18n/config.ts
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

## 4. Web Frontend Architecture

### next-intl Setup

```
src/
├── i18n/
│   ├── config.ts              — locale definitions
│   ├── request.ts             — getRequestConfig for next-intl
│   ├── routing.ts             — createSharedPathnames, Link, redirect
│   └── messages/
│       ├── fa/
│       │   ├── common.json
│       │   ├── auth.json
│       │   ├── post.json
│       │   ├── comment.json
│       │   ├── dashboard.json
│       │   └── notification.json
│       ├── en/
│       │   └── ...
│       ├── ar/
│       │   └── ...
│       └── ru/
│           └── ...
├── app/
│   ├── layout.tsx             — RootLayout with NextIntlClientProvider
│   └── [locale]/              — optional: locale prefix in URL
│       └── ...
├── components/
│   ├── layout/
│   │   ├── language-switcher.tsx
│   │   └── ...
│   └── post/
│       ├── language-badge.tsx
│       └── ...
└── middleware.ts               — locale detection middleware
```

### Middleware for Locale Detection

```typescript
// src/middleware.ts
import createMiddleware from 'next-intl/middleware'
import { defaultLocale, locales } from './i18n/config'

export default createMiddleware({
  defaultLocale,
  locales,
  localePrefix: 'as-needed', // no prefix for default locale
  localeDetection: true,      // use Accept-Language header
})

export const config = {
  matcher: ['/((?!api|_next|.*\\..*).*)'],
}
```

### Root Layout with Direction

```tsx
// src/app/[locale]/layout.tsx
import { NextIntlClientProvider } from 'next-intl'
import { getMessages, getLocale } from 'next-intl/server'
import { localeConfig } from '@/i18n/config'

export default async function RootLayout({ children, params }: Props) {
  const locale = await getLocale()
  const messages = await getMessages()
  const direction = localeConfig[locale as Locale]?.direction ?? 'rtl'

  return (
    <html lang={locale} dir={direction}>
      <body>
        <NextIntlClientProvider messages={messages}>
          {children}
        </NextIntlClientProvider>
      </body>
    </html>
  )
}
```

### Language Switcher Component

```tsx
// src/components/layout/language-switcher.tsx
'use client'

import { useLocale } from 'next-intl'
import { useRouter, usePathname } from '@/i18n/routing'
import { localeConfig, locales } from '@/i18n/config'

export function LanguageSwitcher() {
  const locale = useLocale()
  const router = useRouter()
  const pathname = usePathname()

  const handleChange = (newLocale: string) => {
    router.replace(pathname, { locale: newLocale })
    // Also persist to localStorage and/or API
  }

  return (
    <select value={locale} onChange={(e) => handleChange(e.target.value)}>
      {locales.map((l) => (
        <option key={l} value={l}>{localeConfig[l].name}</option>
      ))}
    </select>
  )
}
```

### Language Badge Component

```tsx
// src/components/post/language-badge.tsx
'use client'

import { useTranslations } from 'next-intl'

const languageLabels: Record<string, string> = {
  fa: 'FA',
  en: 'EN',
  ar: 'AR',
  ru: 'RU',
  tr: 'TR',
}

export function LanguageBadge({ language }: { language: string }) {
  const label = languageLabels[language] ?? language.toUpperCase()

  return (
    <span className="inline-flex items-center px-2 py-0.5 rounded text-xs font-medium bg-muted text-muted-foreground">
      {label}
    </span>
  )
}
```

### Tailwind RTL Configuration

```typescript
// tailwind.config.ts
import type { Config } from 'tailwindcss'
import tailwindcssRTL from 'tailwindcss-rtl'

export default {
  // ...
  plugins: [tailwindcssRTL],
} satisfies Config
```

Usage in components:
```tsx
// Use logical properties
<div className="ms-4 me-2 ps-3 pe-1 text-start">
  {/* margin-start, margin-end, padding-start, padding-end, text-align-start */}
</div>

// RTL-specific overrides
<div className="flex flex-row rtl:flex-row-reverse">
  <Icon />
  <span>Title</span>
</div>
```

## 5. Mobile App Architecture

### Localization Setup

```dart
// lib/main.dart
import 'package:flutter_localizations/flutter_localizations.dart';
import 'package:flutter_gen/gen_l10n/app_localizations.dart';

void main() async {
  final prefs = await Hive.openBox('user_preferences');
  final savedLocale = prefs.get('language') ?? 'fa';

  runApp(
    RiverpodApp(
      locale: Locale(savedLocale),
      child: const MyApp(),
    ),
  );
}

class MyApp extends ConsumerWidget {
  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final locale = ref.watch(localeProvider);

    return MaterialApp(
      locale: locale,
      supportedLocales: AppLocalizations.supportedLocales,
      localizationsDelegates: const [
        AppLocalizations.delegate,
        GlobalMaterialLocalizations.delegate,
        GlobalWidgetsLocalizations.delegate,
        GlobalCupertinoLocalizations.delegate,
      ],
      localeResolutionCallback: (locale, supportedLocales) {
        if (locale == null) return const Locale('fa');
        for (var supported in supportedLocales) {
          if (supported.languageCode == locale.languageCode) {
            return supported;
          }
        }
        return const Locale('fa');
      },
      // MaterialApp automatically handles RTL based on locale
      home: const HomeScreen(),
    );
  }
}
```

### ARB File Example

```json
// lib/l10n/app_en.arb
{
  "@@locale": "en",
  "app_title": "Xilo",
  "login_button": "Login",
  "register_button": "Register",
  "post_read_more": "Read more",
  "post_reading_time": "{minutes} min read",
  "@post_reading_time": {
    "placeholders": {
      "minutes": {
        "type": "int"
      }
    }
  },
  "comment_count": "{count, plural, one {1 comment} other {{count} comments}}",
  "language_persian": "Persian",
  "language_english": "English",
  "language_arabic": "Arabic",
  "language_russian": "Russian",
  "settings_language": "Language",
  "search_placeholder": "Search posts..."
}
```

```json
// lib/l10n/app_fa.arb
{
  "@@locale": "fa",
  "app_title": "زیلو",
  "login_button": "ورود",
  "register_button": "ثبت‌نام",
  "post_read_more": "ادامه مطلب",
  "post_reading_time": "{minutes} دقیقه مطالعه",
  "@post_reading_time": {
    "placeholders": {
      "minutes": {
        "type": "int"
      }
    }
  },
  "comment_count": "{count, plural, one {1 نظر} other {{count} نظر}}",
  "language_persian": "فارسی",
  "language_english": "انگلیسی",
  "language_arabic": "عربی",
  "language_russian": "روسی",
  "settings_language": "زبان",
  "search_placeholder": "جستجو در نوشته‌ها..."
}
```

### Language Provider (Riverpod)

```dart
// lib/features/settings/providers/locale_provider.dart
final localeProvider = StateNotifierProvider<LocaleNotifier, Locale>((ref) {
  return LocaleNotifier();
});

class LocaleNotifier extends StateNotifier<Locale> {
  LocaleNotifier() : super(_loadSavedLocale());

  static Locale _loadSavedLocale() {
    final box = Hive.box('user_preferences');
    final code = box.get('language', defaultValue: 'fa') as String;
    return Locale(code);
  }

  Future<void> setLocale(Locale locale) async {
    state = locale;
    final box = Hive.box('user_preferences');
    await box.put('language', locale.languageCode);
    // If authenticated, also sync with backend
  }

  String get direction {
    const directions = {'fa': 'rtl', 'en': 'ltr', 'ar': 'rtl', 'ru': 'ltr', 'tr': 'ltr'};
    return directions[state.languageCode] ?? 'rtl';
  }
}
```

### Language Badge Widget

```dart
// lib/features/posts/widgets/language_badge.dart
class LanguageBadge extends StatelessWidget {
  final String languageCode;

  const LanguageBadge({super.key, required this.languageCode});

  static const _labels = {
    'fa': 'فا',
    'en': 'EN',
    'ar': 'عر',
    'ru': 'RU',
    'tr': 'TR',
  };

  @override
  Widget build(BuildContext context) {
    final label = _labels[languageCode] ?? languageCode.toUpperCase();
    return Container(
      padding: const EdgeInsets.symmetric(horizontal: 6, vertical: 2),
      decoration: BoxDecoration(
        color: Theme.of(context).colorScheme.surfaceContainerHighest,
        borderRadius: BorderRadius.circular(4),
      ),
      child: Text(
        label,
        style: Theme.of(context).textTheme.labelSmall,
      ),
    );
  }
}
```

## 6. API Changes

### GET /api/posts — Language Filter

```
GET /api/posts?language=fa&limit=10
```

Response includes `language` field on each post:
```json
{
  "posts": [
    {
      "id": "...",
      "title": "...",
      "language": "fa",
      // ... other fields
    }
  ],
  "pagination": { ... }
}
```

### POST /api/posts — Language in Request Body

```json
{
  "title": "...",
  "content": "...",
  "language": "en",
  // ... other fields
}
```

### PATCH /api/users/me — Language Preference

```json
{
  "preferred_language": "en"
}
```

## 7. Meilisearch Configuration

```go
// search-service/internal/index/config.go
func ConfigureIndex(client *meilisearch.Client) error {
  index := client.Index("posts")

  _, err := index.UpdateSettings(&meilisearch.Settings{
    FilterableAttributes: []string{"_language", "category", "tags", "status"},
    Faceting: &meilisearch.Faceting{
      MaxValuesPerFacet: 100,
    },
    // Language-specific settings can be added per-index
    // Meilisearch auto-detects language for most analyzers
  })
  return err
}
```

Search with language filter:
```go
results, err := index.Search(query, &meilisearch.SearchRequest{
  Filter: "_language = fa AND status = published",
  Facets: []string{"_language"},
})
```

## 8. Font Configuration

### Web Frontend

```typescript
// src/app/[locale]/layout.tsx
import { Vazirmatn, Inter, Noto_Sans_Arabic } from 'next/font/google'

const vazirmatn = Vazirmatn({
  subsets: ['arabic'],
  variable: '--font-vazirmatn',
  display: 'swap',
})

const inter = Inter({
  subsets: ['latin'],
  variable: '--font-inter',
  display: 'swap',
})

const notoArabic = Noto_Sans_Arabic({
  subsets: ['arabic'],
  variable: '--font-noto-arabic',
  display: 'swap',
})

// In RootLayout:
const fontClass = locale === 'fa' ? vazirmatn.variable
  : locale === 'ar' ? notoArabic.variable
  : inter.variable

return <html className={fontClass} ...>
```

### Mobile App

```dart
// lib/core/theme/fonts.dart
class AppFonts {
  static const Map<String, String> fontFamilyByLocale = {
    'fa': 'Vazirmatn',
    'ar': 'NotoSansArabic',
    'en': 'Inter',
    'ru': 'Inter',
    'tr': 'Inter',
  };

  static String getFontFamily(Locale locale) {
    return fontFamilyByLocale[locale.languageCode] ?? 'Vazirmatn';
  }
}
```

## 9. Translation Key Conventions

### Structure
```
<domain>.<category>.<key>

Examples:
common.actions.save
common.actions.cancel
common.errors.notFound
auth.login.title
auth.login.email_label
auth.login.submit_button
post.card.read_more
post.card.reading_time
post.detail.share
post.detail.copy_link
comment.form.submit
comment.form.placeholder
notification.comment_reply
notification.new_follower
```

### Pluralization
```json
{
  "comment_count": "{count, plural, one {# نظر} other {# نظر}}",
  "post_count": "{count, plural, one {# post} other {# posts}}"
}
```

### Interpolation
```json
{
  "welcome_message": "{name}، خوش آمدید!",
  "reading_time": "{minutes} دقیقه مطالعه"
}
```

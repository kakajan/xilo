# Proposal: Multilingual Support (i18n)

**Status:** Proposed  
**Date:** 2026-05-18

---

## Summary

Add full internationalization (i18n) support to the Xilo platform, enabling users to select their preferred UI language, automatic RTL/LTR layout direction based on language, and language metadata for posts. This covers the web frontend, mobile app, and backend API.

---

## Motivation

- **Global reach**: Xilo targets Persian-speaking audiences primarily, but must support English and other languages for broader adoption.
- **RTL support**: Persian (Farsi), Arabic, Urdu, and other RTL languages require proper text direction, layout mirroring, and font handling.
- **Content discovery**: Readers should be able to filter and discover posts by language.
- **Author intent**: Authors should declare the language of their posts so readers know what to expect.

---

## Scope

| Domain | Deliverables |
|--------|-------------|
| **Backend** | `language` field on `posts` table, language preference on `users` table, i18n API for system messages, language filter on post list endpoint |
| **Web Frontend** | `next-intl` integration, RTL/LTR layout switching, language switcher component, translation files (fa, en), direction-aware components |
| **Mobile App** | `flutter_localizations` + `intl`, RTL/LTR support, language settings, translation files (fa, en) |
| **Database** | Migration: add `language` column to `posts`, `preferred_language` to `users` |
| **Search** | Meilisearch language-aware indexing and filtering |

### Supported Languages (Initial)

| Code | Name | Direction |
|------|------|-----------|
| `fa` | Persian (Farsi) | RTL |
| `en` | English | LTR |
| `ar` | Arabic | RTL |
| `ru` | Russian | LTR |
| `tr` | Turkish | LTR |

### Out of Scope (Future)

- Per-user post translation (auto-translate content)
- RTL-aware image mirroring (beyond layout)
- Locale-specific date/time formatting beyond standard i18n
- Right-to-left font fallback beyond initial set

---

## Design Principles

1. **Language is user choice** — UI language is always the user's preference, never inferred from content.
2. **RTL is automatic** — direction is derived from language config, not manually toggled.
3. **Post language is author-declared** — authors explicitly set the language when creating a post.
4. **Default is system default** — unauthenticated users see the platform default language (fa).
5. **Progressive enhancement** — missing translations fall back to default language gracefully.

---

## Success Criteria

- [ ] UI fully translated in fa and en with zero hardcoded strings
- [ ] RTL layout renders correctly in fa and ar (mirrored navigation, proper text alignment)
- [ ] LTR layout renders correctly in en and ru
- [ ] Language switcher accessible from navbar (web) and settings (mobile)
- [ ] Posts display language badge/indicator
- [ ] Post list endpoint supports `?language=` filter
- [ ] Meilisearch returns results filtered by language
- [ ] Mobile app supports RTL with proper direction-aware widgets

---

## Risks & Mitigations

| Risk | Impact | Mitigation |
|------|--------|------------|
| RTL CSS conflicts with existing Tailwind classes | Medium | Use Tailwind RTL plugin; audit all components |
| Translation management becomes unmaintainable | Medium | Use structured JSON/YAML keys; add translation validation in CI |
| Meilisearch language analyzer misbehavior | Low | Test analyzers per language; fallback to default analyzer |
| Performance impact from i18n runtime | Low | next-intl uses compile-time extraction; minimal runtime cost |

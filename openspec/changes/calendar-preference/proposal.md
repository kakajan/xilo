# Proposal: Calendar Preference (Jalali / Gregorian)

**Status:** Active  
**Date:** 2026-07-17

---

## Summary

Add platform-wide and per-user calendar preferences so Persian (and other) locales can display dates in the Jalali (Shamsi) or Gregorian calendar. Resolution order is user override → admin locale defaults → Gregorian fallback.

## Motivation

- Persian UI currently shows Gregorian dates with Persian month labels (e.g. «۱۸ مه»), which is incorrect for Shamsi audiences.
- Admins need to set locale defaults (e.g. `fa → jalali`).
- Individual users must be able to choose Gregorian even while using Persian UI.

## Scope

| Domain | Deliverables |
|--------|-------------|
| **Backend** | `preferred_calendar` on users; `platform_settings.calendar_defaults`; GET/PATCH platform settings; expose defaults on `/api/languages` |
| **Web** | Locale-aware `formatDate`; admin settings page; user settings calendar picker |
| **Android** | Shared `DateFormatter`; settings UI for calendar; feed/comment/chat display dates |

## Out of Scope

- Full next-intl / UI string i18n (covered by multilingual-support)
- Hijri (قمری) calendar
- Custom date format strings beyond short display formats

## Success Criteria

- [ ] Default for `fa` is Jalali when user preference is `auto`
- [ ] User can force `gregorian` or `jalali` via profile update
- [ ] Admin can change per-locale defaults
- [ ] Android feed no longer shows Gregorian months as Persian labels for Jalali default

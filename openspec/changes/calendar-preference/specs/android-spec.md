# Delta Spec: Android — Calendar Preference

## Requirements

### REQ-CAL-AND-001: Shared date formatter

The Android app SHALL resolve calendar via user preference → platform defaults → gregorian, and format absolute dates accordingly (ICU Persian calendar for Jalali).

#### Scenario: Feed absolute date

- GIVEN resolved calendar is Jalali
- WHEN a post older than one week is shown
- THEN the absolute date uses Jalali (not Gregorian month labels like «مه»)

### REQ-CAL-AND-002: Settings picker

The settings screen SHALL allow choosing خودکار / شمسی / میلادی and persist via `PATCH /api/auth/me`.

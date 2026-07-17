# Delta Spec: Backend — Calendar Preference

## Requirements

### REQ-CAL-001: User preferred calendar

The system SHALL store `preferred_calendar` on each user with values `auto`, `jalali`, or `gregorian` (default `auto`).

#### Scenario: Update calendar preference

- GIVEN an authenticated user
- WHEN they `PATCH /api/auth/me` with `"preferred_calendar": "gregorian"`
- THEN the profile response includes `preferred_calendar: "gregorian"`

#### Scenario: Reject invalid calendar

- GIVEN an authenticated user
- WHEN they `PATCH /api/auth/me` with an invalid calendar value
- THEN the API returns an error and does not change the preference

### REQ-CAL-002: Platform calendar defaults

The system SHALL persist per-locale calendar defaults under platform settings key `calendar_defaults`.

#### Scenario: Public read

- GIVEN seeded defaults
- WHEN a client calls `GET /api/platform/settings` or `GET /api/languages`
- THEN the response includes `calendar_defaults` with at least `fa` and `en`

#### Scenario: Admin update

- GIVEN an admin or superadmin
- WHEN they `PATCH /api/platform/settings` with updated `calendar_defaults`
- THEN subsequent GET responses reflect the new map

#### Scenario: Non-admin forbidden

- GIVEN a non-admin user
- WHEN they `PATCH /api/platform/settings`
- THEN the API returns 403

# Delta Spec: Web — Calendar Preference

## Requirements

### REQ-CAL-WEB-001: Locale-aware date formatting

The web app SHALL format display dates using the resolved calendar (user override → platform locale default → gregorian).

#### Scenario: Persian default Jalali

- GIVEN platform default `fa → jalali` and user preference `auto`
- WHEN a post publish date is rendered
- THEN the date uses Jalali month names (e.g. اردیبهشت), not Gregorian Persian labels (e.g. مه)

### REQ-CAL-WEB-002: Admin settings

Admins SHALL configure per-locale calendar defaults from the dashboard settings page.

### REQ-CAL-WEB-003: User settings

Authenticated users SHALL set preferred calendar to `auto`, `jalali`, or `gregorian` from account settings, including forcing Gregorian while using Persian UI.

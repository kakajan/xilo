# Design: Calendar Preference

## Resolution

```
effective = user.preferred_calendar if in {jalali, gregorian}
          else platform.calendar_defaults[locale]
          else gregorian
```

- User values: `auto` | `jalali` | `gregorian` (default `auto`)
- Platform `calendar_defaults`: JSON map language code → `jalali` | `gregorian`
- Seed: `fa=jalali`, others `gregorian`
- Guest / missing user: use platform default for active locale (Android UI locale `fa`)

## Data model

### users.preferred_calendar

```sql
VARCHAR(10) NOT NULL DEFAULT 'auto'
CHECK (preferred_calendar IN ('auto', 'jalali', 'gregorian'))
```

### platform_settings

```sql
key TEXT PRIMARY KEY
value JSONB NOT NULL
updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
```

Key `calendar_defaults` value example:

```json
{"fa":"jalali","en":"gregorian","ar":"gregorian","ru":"gregorian","tr":"gregorian"}
```

## API

| Method | Path | Auth | Notes |
|--------|------|------|-------|
| GET | `/api/platform/settings` | public | Returns `calendar_defaults` |
| PATCH | `/api/platform/settings` | admin/superadmin | Update `calendar_defaults` |
| GET | `/api/languages` | public | Adds `calendar_defaults` |
| PATCH | `/api/auth/me` | user | Accepts `preferred_calendar` |
| GET | `/api/auth/me` | user | Returns `preferred_calendar` |

## Clients

- **Web**: `date-fns-jalali` for Jalali formatting; shared `format-date.ts`
- **Android**: ICU `fa_IR@calendar=persian` for Jalali; shared `DateFormatter`

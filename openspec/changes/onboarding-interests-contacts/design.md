# Design: Onboarding Interests + Contact Sync

**Date:** 2026-07-19  
**Change:** `onboarding-interests-contacts`

---

## 1. Interests data model

### `interests`

| Column | Type | Notes |
|--------|------|-------|
| id | UUID PK | |
| slug | VARCHAR(64) UNIQUE | Stable key, e.g. `technology` |
| labels | JSONB NOT NULL | `{"en":"Technology","fa":"فناوری",...}` |
| icon | VARCHAR(64) NULL | Optional Lucide/Material key |
| sort_order | INT NOT NULL DEFAULT 0 | Admin reorder |
| is_active | BOOLEAN NOT NULL DEFAULT TRUE | Soft deactivate |
| created_at / updated_at | TIMESTAMPTZ | |

### `user_interests`

| Column | Type | Notes |
|--------|------|-------|
| user_id | UUID FK → users | ON DELETE CASCADE |
| interest_id | UUID FK → interests | ON DELETE CASCADE |
| created_at | TIMESTAMPTZ | |
| PRIMARY KEY (user_id, interest_id) | | |

### Seed (~18, ≤30% tech)

technology, science, business, art, literature, music, cinema, sports, travel, food, health, education, history, photography, nature, comedy, fashion, psychology — labels `fa` + `en` minimum.

Migration: `000020_interests.up.sql` / `.down.sql`.

---

## 2. Contact hash model

### Columns on `users`

| Column | Type | Notes |
|--------|------|-------|
| phone_hash | CHAR(64) NULL | HMAC-SHA256 hex of normalized phone |
| email_hash | CHAR(64) NULL | HMAC-SHA256 hex of normalized email |

Indexes: `idx_users_phone_hash`, `idx_users_email_hash` (partial WHERE NOT NULL).

Migration: `000021_contact_hashes.up.sql` / `.down.sql`.

### Normalization (client + server agree)

- **Phone:** strip non-digits; if starts with `00` → replace with country logic as E.164-ish digits only; Iranian local `09…` → `989…` when 11 digits starting with 09.
- **Email:** trim, lowercase.

### Hashing pipeline

1. Client: `SHA256(normalized_value)` → hex (never send raw).
2. Server: `HMAC-SHA256(CONTACT_MATCH_PEPPER, client_sha256_hex)` → hex stored/compared.
3. On register / profile phone or email change: recompute and store server hash from raw (server has raw at write time).
4. Backfill migration: compute from existing `users.phone` / `users.email` using same HMAC.

`CONTACT_MATCH_PEPPER` from env (required in prod; dev default in config with warning).

---

## 3. API contracts

### Public interests

`GET /api/interests` → `{ "interests": [ { id, slug, labels, icon, sort_order } ] }`  
Only `is_active=true`, ordered by `sort_order`, then slug. Optional auth.

### User interests

`GET /api/users/me/interests` (JWT) → `{ "interest_ids": [...], "interests": [...] }`  
`PUT /api/users/me/interests` (JWT) body `{ "interest_ids": ["uuid", ...] }` — replace set (max 20).

### Admin interests

All under `/api/admin/interests` (admin/superadmin):

- `GET /` — all including inactive
- `POST /` — `{ slug, labels, icon?, sort_order?, is_active? }`
- `PATCH /:id` — partial update
- `DELETE /:id` — hard delete if unused else deactivate (`is_active=false`) OR soft-delete only (prefer soft deactivate)
- `PUT /reorder` — `{ "ordered_ids": ["uuid", ...] }`

### Contact match

`POST /api/contacts/match` (JWT)

Request:
```json
{ "phone_hashes": ["hexsha256...", ...], "email_hashes": ["hexsha256...", ...] }
```

- Combined length ≤ 500; empty arrays OK.
- Rate limit: ~30/min per user.
- Server HMACs each client hash with pepper, looks up users (exclude self, deleted).
- Response:
```json
{
  "matches": [
    {
      "id": "uuid",
      "username": "...",
      "display_name": "...",
      "avatar_url": "...",
      "already_following": false
    }
  ]
}
```

Never echo submitted hashes back unnecessarily; never accept raw phones/emails.

Successful matches are upserted into `user_contact_matches (user_id, matched_user_id, matched_at)` so clients can show a sync badge later without re-uploading hashes.

### Contacts hub list

`GET /api/contacts` (JWT) → `{ "data": [ { id, username, display_name, avatar_url, is_verified, is_following, from_contacts } ] }`

Returns the caller's followings; `from_contacts` is true when a `user_contact_matches` row exists.

### Discover comments (interest-aware)

`GET /api/discover/comments?limit=50&interest=slug` (OptionalAuth)

- If authenticated and user has interests: boost comments whose parent post `category` or `tags` match interest slugs (case-insensitive); optionally filter when `interest=` query set.
- Unauthenticated / no interests: recent high-signal comments (recency + likes).
- Response shape aligned with comment list items + post title/slug for card context.

---

## 4. Web admin

- Page: `web/src/app/(dashboard)/dashboard/interests/page.tsx`
- Nav entry in `dashboard/layout.tsx` (mirror users page: search/list, create/edit dialog, reorder, activate toggle).
- Use existing `apiFetch` + admin JWT cookie patterns.

---

## 5. Android

### Onboarding

- New `OnboardingViewModel` (Hilt): load interests, selection state, complete → `PUT /users/me/interests`, then suggestions step.
- `ContactsReader`: READ_CONTACTS (runtime), normalize, SHA-256, call match API; skippable.
- Suggestions: real `POST/DELETE api/users/{username}/follow`.
- Replace mesh/sync jargon strings with plain “contacts” / “people you may know” wording (fa+en at minimum).

### Discover

- Topic chips from user interests / active interests catalog.
- Refresh via `GET /api/discover/comments` (fallback to current client path if offline).

### Manifest

- `READ_CONTACTS` permission (not required for install; runtime request).

---

## 6. Package layout (backend)

Prefer:

- `backend/internal/interest/` — model, repository, handler (public + admin + user)
- `backend/internal/contact/` — match handler + hash util
- Discover endpoint in `internal/discover/` or extend comment handler; wire in `api-gateway/main.go`.

Follow Clean Architecture patterns used by `internal/user/handler`.

---

## 7. Testing

- Unit: interest repo CRUD, hash normalize+HMAC, match lookup, discover boost ordering.
- Android: ViewModel tests where feasible; emulator smoke for onboarding flow.

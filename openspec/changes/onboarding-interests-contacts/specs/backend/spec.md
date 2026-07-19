# Delta for Backend — Interests, Contacts, Discover

## ADDED Requirements

### Requirement: Interest catalog

The system SHALL store admin-managed interests with multilingual JSONB labels, slug, sort order, and active flag. A seed of approximately 18 diverse interests (technology share ≤ ~30%) SHALL be applied on migration.

#### Scenario: Public list
- GIVEN active interests exist
- WHEN a client calls `GET /api/interests`
- THEN only active interests are returned ordered by `sort_order`

#### Scenario: User saves interests
- GIVEN an authenticated user
- WHEN they `PUT /api/users/me/interests` with up to 20 interest IDs
- THEN the user's interest set is replaced and returned on `GET /api/users/me/interests`

#### Scenario: Admin CRUD
- GIVEN an admin
- WHEN they create, update, reorder, or deactivate interests via `/api/admin/interests`
- THEN the catalog changes are persisted and reflected to clients

### Requirement: Contact hash matching

The system SHALL store `phone_hash` and `email_hash` on users (HMAC-SHA256 with `CONTACT_MATCH_PEPPER` of client-normalized SHA-256 hex, or of normalized raw values at write time). `POST /api/contacts/match` SHALL accept only hashes (max 500 combined), require JWT, apply rate limiting, and return public profiles plus `already_following`. Raw phones/emails MUST NOT be accepted.

#### Scenario: Match members
- GIVEN users with stored hashes
- WHEN an authenticated client submits matching phone/email client hashes
- THEN matching non-self users are returned with public profile fields and follow state

#### Scenario: Hash updates
- GIVEN a user registers or updates phone/email
- WHEN the write succeeds
- THEN `phone_hash` / `email_hash` are recomputed and stored

### Requirement: Interest-aware Discover comments

The system SHALL expose `GET /api/discover/comments` that, for authenticated users with interests, boosts (and optionally filters via `interest` query) comments whose parent post category or tags match interest slugs. Unauthenticated or interest-less requests SHALL return a recent/engagement fallback.

#### Scenario: Personalized boost
- GIVEN a user interested in `music`
- WHEN they fetch discover comments
- THEN comments on posts tagged/categorized `music` appear ahead of unrelated recent comments when scores are otherwise similar

### Requirement: Contacts hub listing

The system SHALL persist successful contact matches in `user_contact_matches` (user_id, matched_user_id, matched_at) when `POST /api/contacts/match` returns matches. `GET /api/contacts` (JWT) SHALL return the caller's followings enriched with `from_contacts` (true when a match row exists). Raw PII MUST NOT be stored in this table.

#### Scenario: Match persists sync badge
- GIVEN an authenticated user matches another member via contact hashes
- WHEN match succeeds
- THEN a `user_contact_matches` row is upserted for that pair

#### Scenario: List contacts
- GIVEN the user follows Alice (matched) and Bob (not matched)
- WHEN they call `GET /api/contacts`
- THEN both appear with `is_following=true`, Alice has `from_contacts=true`, Bob has `from_contacts=false`

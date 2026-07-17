# Delta: Android Internationalization Alignment

## ADDED Requirements

### Requirement: REQ-AND-014 — Android Locale Resources
The Android client SHALL use Android resource qualifiers and Compose resource APIs for all user-visible strings. It SHALL provide complete initial locale resources for `fa`, `en`, `ar`, `ru`, and `tr`, with Persian as the fallback locale.

#### Scenario: Unsupported device locale
- GIVEN a first launch on an unsupported device locale
- WHEN no user preference exists
- THEN the app falls back to Persian
- AND the selected fallback is persisted.

### Requirement: REQ-AND-015 — Directional Compose UI
All Android Compose layouts SHALL use layout direction, start/end alignment, semantic traversal, and mirrored directional icons where appropriate. Fixed left/right positioning MUST be justified for content that is inherently directional.

#### Scenario: RTL chat and navigation
- GIVEN Arabic or Persian is active
- WHEN chat, back navigation, and post cards render
- THEN control order and alignment follow RTL
- AND message ownership semantics remain clear without relying on color alone.

### Requirement: REQ-AND-016 — Locale Preference Synchronization
The Android client SHALL store non-secret locale preference in DataStore, apply it before normal screen composition, and synchronize it with `PATCH /api/auth/me` after authenticated changes. Server preference SHALL be restored from `GET /api/auth/me` and reconcile safely during login.

#### Scenario: Authenticated locale change
- GIVEN an authenticated user selects Turkish
- WHEN the update succeeds locally
- THEN the UI changes immediately
- AND a retryable outbox operation synchronizes `preferred_language` through `PATCH /api/auth/me`.

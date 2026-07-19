# Tasks: Android Native Production

**Rule:** Every Android task below is intentionally unchecked. Existing Flutter work and Android prototype code are not acceptance evidence.

## Phase 1 — Foundation and Contract Gate

- [ ] **ANP-1.1** Audit and refactor the existing `android/` project in place.
  - Acceptance: `applicationId` is configured as `ir.xilo.app`, `minSdk` is 24, Gradle build variants and existing repository history are retained, and no replacement app is bootstrapped.
- [ ] **ANP-1.2** Establish Compose, Material 3, Jetpack Navigation 3, Hilt, coroutine/Flow, Retrofit, OkHttp, Room, Paging 3, WorkManager, DataStore, and Keystore foundations.
  - Acceptance: debug build, lint, and a dependency-injection smoke test pass on API 24+.
- [ ] **ANP-1.3** Implement common API/error/auth/session infrastructure.
  - Acceptance: authenticated request, serialized refresh, logout, typed failure, and no-secret logging tests pass.
- [ ] **ANP-1.4** Establish Room schema, migrations, repositories, connectivity state, and durable outbox primitives.
  - Acceptance: migration and offline/online repository tests pass; an operation persists across process death.
- [ ] **ANP-1.5** Implement and contract-test the frozen retryable-mutation idempotency contract with the shared backend dependency.
  - Acceptance: every retryable mutation sends a UUIDv4 `Idempotency-Key`, retries reuse the same key, and shared contract tests prove deduplication plus return of the original semantic result for at least 30 days; backend implementation remains an explicit shared dependency.
- [ ] **Gate ANP-G1**
  - Acceptance: architecture review approves the in-place foundation, the backend owner accepts the frozen idempotency contract, and all Phase 1 checks pass.

## Phase 2 — Account, Content, and Discovery Parity

- [ ] **ANP-2.1** Deliver onboarding, registration, login, recovery, session restoration, and account security flows.
  - Acceptance: `REQ-AND-003` scenarios and shared `auth-spec.md` contracts pass in UI and integration tests.
- [ ] **ANP-2.2** Deliver home feed, discover, search, post detail, profiles, follows, and bookmarks using Paging.
  - Acceptance: `REQ-AND-004` plus shared `discover-spec.md`, `search-spec.md`, and post/profile contracts are exercised online and from cached Room data.
- [ ] **ANP-2.3** Deliver post drafting/editing/publishing, media selection/upload, and draft recovery.
  - Acceptance: `REQ-AND-004` plus media and post contract tests pass; interrupted draft and upload behavior has a user-visible recovery state.
  - Progress: local debounced draft recovery shipped via `ComposeDraftStore` (create/edit). Media upload + server draft list remain.
- [ ] **ANP-2.4** Deliver comments, reactions, mentions, report/moderation entry points, and realtime reconciliation.
  - Acceptance: `REQ-AND-005` and shared `comment-spec.md` scenarios pass, including optimistic update rollback and permission-denied states.
- [ ] **Gate ANP-G2**
  - Acceptance: authenticated content parity smoke suite passes on API 24 and a current supported Android version.

## Phase 3 — Realtime, Offline, and Preferences

- [ ] **ANP-3.1** Deliver notification inbox, push registration, notification preferences, deep links, and foreground/background handling.
  - Acceptance: `REQ-AND-006` and shared `notification-spec.md` scenarios pass with a test provider; Android 13+ permission denial, registration failure, and invalid/deep-link payloads are safely handled.
- [ ] **ANP-3.2** Deliver direct and group chat, typing, presence, receipts, reactions, attachments, and reconnect reconciliation.
  - Acceptance: shared `chat-spec.md` scenarios pass across reconnect, duplicate event, and offline-send cases.
- [ ] **ANP-3.3** Complete offline read models, outbox synchronization, conflict recovery, and user-visible sync state.
  - Acceptance: `REQ-AND-006` through `REQ-AND-008` scenarios and `ANP-1.5` contract tests pass after airplane-mode/process-death testing.
- [ ] **ANP-3.4** Deliver settings, theme, locale, Persian/Arabic RTL, English/Russian/Turkish LTR, accessibility preferences, and language persistence.
  - Acceptance: `REQ-AND-009`, `REQ-AND-014` through `REQ-AND-016`, and `openspec/changes/android-native-production/specs/android-i18n/spec.md` pass in Compose tests.
- [ ] **Gate ANP-G3**
  - Acceptance: offline/realtime/RTL test matrix passes with no data loss or duplicate mutation.

## Phase 4 — Revenue, Measurement, and Release

- [ ] **ANP-4.1** Deliver subscription/premium, donation/ad presentation, billing status, and writer/admin analytics surfaces where authorized by shared contracts.
  - Acceptance: `REQ-AND-010` plus shared `monetization-spec.md` and `analytics-spec.md` permission, error, and consent states pass.
- [ ] **ANP-4.2** Complete performance, accessibility, and security hardening.
  - Acceptance: `REQ-AND-011`, `REQ-AND-012`, `REQ-AND-014`, and `REQ-AND-015` pass; TalkBack, keyboard/switch access where applicable, contrast, API 24, and no-cleartext checks are verified.
- [ ] **ANP-4.3** Add unit, integration, Compose UI, and connected-device coverage plus Android CI/release automation.
  - Acceptance: `REQ-AND-013` passes; signed release candidate is reproducible and deployable to an internal test track.
- [ ] **Gate ANP-G4**
  - Acceptance: product, security, accessibility, performance, and release owners approve documented evidence.

# Delta: Native Android Production Client

## ADDED Requirements

### Requirement: REQ-AND-001 — Android Platform Authority
The sole active mobile client SHALL be the Kotlin application in `android/`. It SHALL be refactored in place, its `applicationId` SHALL be `ir.xilo.app`, and its `minSdk` SHALL be 24. Flutter artifacts in `mobile/` SHALL be treated as legacy and out of scope.

#### Scenario: Active client selection
- GIVEN an engineer starts mobile work
- WHEN they consult repository guidance or OpenSpec
- THEN they are directed to `android/` and this change
- AND they are not directed to implement or validate Flutter.

### Requirement: REQ-AND-002 — Foundation Architecture
The Android client SHALL use Kotlin, Jetpack Compose, Jetpack Navigation 3, Hilt, Room, Retrofit, OkHttp, Paging 3, WorkManager, DataStore, and Android Keystore. It SHALL use presentation/domain/data boundaries, ViewModels, and StateFlow.

#### Scenario: Recoverable screen state
- GIVEN Android recreates a process while a user is viewing a supported screen
- WHEN the app is reopened
- THEN persisted state and Room-backed content are restored safely
- AND secret credentials are not read from plain preferences.

### Requirement: REQ-AND-003 — Auth and Onboarding Parity
The client SHALL implement onboarding, registration, login, refresh/logout, recovery, and authenticated-session restoration according to `auth-spec.md`.

#### Scenario: Expired access credential
- GIVEN a valid refresh credential and an expired access credential
- WHEN concurrent API requests receive an authentication failure
- THEN the client performs one serialized refresh
- AND retries eligible requests without exposing credentials in logs.

### Requirement: REQ-AND-004 — Feed, Discover, Search, and Post Parity
The client SHALL provide feed, discover, search, post reading, editor, drafts, publishing, editing, and media workflows according to the corresponding shared specifications. Paginated collections SHALL use Paging 3 and preserve loading, empty, error, and retry states. Visual presentation SHALL follow `openspec/changes/xilo-platform/specs/ui-ux-spec.md`.

#### Scenario: Interrupted post composition
- GIVEN a user has an unsent draft
- WHEN the process is terminated or connectivity is lost
- THEN the draft is recoverable
- AND the user can distinguish local, pending, failed, and published state.

### Requirement: REQ-AND-005 — Engagement and Moderation Parity
The client SHALL support comments, threaded replies, reactions, mentions, reporting, and available moderation actions using `comment-spec.md` and `post-spec.md` as the behavior authority and `openspec/changes/xilo-platform/specs/ui-ux-spec.md` as the visual authority. Own bubbles SHALL use `#E8F5FE` in light mode and `#1E3A5F` in dark mode; other users' bubbles SHALL use `#F7F9FA` in light mode and `#2C2C2E` in dark mode.

#### Scenario: Optimistic reaction failure
- GIVEN a user toggles a reaction while online
- WHEN the server rejects the mutation
- THEN the UI reconciles to the authoritative state
- AND displays an actionable, accessible error.

### Requirement: REQ-AND-006 — Profile, Follow, Bookmark, Notifications, and Push Parity
The client SHALL support profiles, follows, bookmarks, notification inbox/preferences, push registration, and safe deep links using the shared specifications.

#### Scenario: Background notification
- GIVEN the app is backgrounded and a valid notification is received
- WHEN the user taps it
- THEN the app navigates only to an authorized target
- AND the notification state is reconciled with the server.

#### Scenario: Android 13+ notification permission denied
- GIVEN an Android 13+ user denies `POST_NOTIFICATIONS`
- WHEN push registration or a notification-producing flow runs
- THEN the app remains fully usable without notification permission
- AND it records the disabled state, avoids repeated coercive prompts, and offers an accessible route to system settings.

#### Scenario: Push registration fails
- GIVEN notification permission is available
- WHEN provider-token acquisition or backend registration fails
- THEN the failure is shown as a non-blocking retryable state
- AND WorkManager retries with bounded backoff without logging the provider token.

### Requirement: REQ-AND-007 — Chat and Realtime Parity
The client SHALL provide direct and group chat, message lifecycle, reactions, media, typing, presence, and read receipts according to `chat-spec.md`. OkHttp WebSockets SHALL reconnect with bounded exponential backoff and reconcile events into Room.

#### Scenario: Offline chat send
- GIVEN a user sends a message without connectivity
- WHEN the message is accepted locally
- THEN it is placed in the durable outbox with a stable UUIDv4 `Idempotency-Key`
- AND it is sent exactly once from the user's perspective after connectivity returns.

#### Scenario: Reconnect, resubscribe, and reconcile
- GIVEN an authenticated client has active user, chat, and post subscriptions and has applied events through a known server sequence/version
- WHEN its WebSocket disconnects and later reconnects
- THEN it reconnects with bounded exponential backoff and jitter
- AND reauthenticates and resubscribes to every still-active subscription before declaring the stream live
- AND requests or receives missed state since the last acknowledged sequence/version
- AND deduplicates repeated event IDs and applies out-of-order events by server sequence/version so Room converges to the authoritative state.

#### Scenario: Group administration is authorized
- GIVEN a non-admin group member
- WHEN they attempt to add or remove members or change group metadata
- THEN the client does not commit an optimistic administration change
- AND the server authorization defined by `REQ-CHAT-010` rejects the action and the client reconciles to the authoritative group state.

### Requirement: REQ-AND-008 — Offline Sync and Outbox
The client SHALL be offline-first for supported reads and mutations. Room SHALL be the local read model; WorkManager SHALL process network-constrained, retryable outbox work. Every retryable mutation MUST send an `Idempotency-Key` UUIDv4 generated for that semantic operation and MUST reuse the same key on every retry. The shared backend MUST deduplicate by principal, operation, and key for at least 30 days from first receipt and return the original semantic result for duplicates; reuse with a different semantic payload MUST be rejected as a conflict. Backend implementation is a shared dependency.

#### Scenario: Process death during synchronization
- GIVEN a worker is interrupted after a request may have reached the server
- WHEN it resumes
- THEN it retries using the same `Idempotency-Key`
- AND duplicate server-side effects are not created.

### Requirement: REQ-AND-009 — Settings, Internationalization, and RTL
The client SHALL implement settings, locale/theme persistence, fa/en/ar/ru/tr support, RTL/LTR-aware Compose layouts, appropriate fonts, and locale-aware text/content formatting. It SHALL follow `openspec/changes/android-native-production/specs/android-i18n/spec.md`.

#### Scenario: Direction switch
- GIVEN a user changes from English to Persian
- WHEN the settings update completes
- THEN semantic navigation, alignment, icons, and text direction render RTL
- AND the selected locale persists after restart and synchronizes when authenticated.

### Requirement: REQ-AND-010 — Monetization and Analytics
The client SHALL expose subscriptions, premium-content states, supported donation/advertising flows, and consent-aware analytics only as authorized by `monetization-spec.md` and `analytics-spec.md`.

#### Scenario: Premium content denial
- GIVEN a non-entitled user opens premium content
- WHEN the entitlement check denies access
- THEN the client shows the server-approved preview and upgrade state
- AND does not cache protected full content for offline access.

### Requirement: REQ-AND-011 — Performance and Accessibility
The client SHALL test startup, smooth-scrolling, memory, network recovery, binary size, contrast, touch targets, TalkBack, semantics, dynamic text, and reduced motion against a documented release baseline.

#### Scenario: Measured performance gate
- GIVEN a release-like profileable build with the Baseline Profile installed
- AND an approved baseline device profile documenting device model, SoC, RAM, Android API level, display refresh rate, build commit, dataset, network conditions, and measurement-tool versions
- WHEN startup is measured over at least 10 cold and 10 warm launches after one discarded setup run
- THEN median cold start is less than 1.5 seconds
- AND median warm start is less than 500 milliseconds
- AND a representative feed-scroll trace renders at 60fps on 60Hz hardware, and 120fps on supported 120Hz hardware, with at least 95% of frames within the corresponding 16.67ms or 8.33ms budget
- AND the release APK or equivalent directly installable Android artifact is less than 50MB
- AND raw Macrobenchmark/frame-timing results and artifact-size evidence are attached to the release gate.

#### Scenario: Screen-reader navigation
- GIVEN TalkBack is enabled
- WHEN a user navigates a feed, composer, or chat
- THEN actionable controls expose meaningful labels, state, and actions
- AND realtime updates are announced without excessive interruption.

### Requirement: REQ-AND-012 — Security
The client SHALL use HTTPS/WSS in production, Keystore-backed secrets, least-privilege permissions, non-secret diagnostics, secure release configuration, and validated deep links. It MUST not hardcode credentials or permit cleartext production traffic.

#### Scenario: Production transport
- GIVEN a production build
- WHEN a cleartext HTTP or WS endpoint is configured
- THEN the connection is rejected
- AND the failure does not reveal credentials.

### Requirement: REQ-AND-013 — Testing, CI, and Release
The client SHALL have unit, repository, Room migration, networking, outbox, Compose UI, accessibility, and connected-device tests. CI SHALL run formatting/static analysis, Android Lint, tests, and release build validation; release automation SHALL preserve signing and provenance controls.

#### Scenario: Release candidate gate
- GIVEN a release candidate is created
- WHEN CI executes the Android gate
- THEN all required tests and lint checks pass on API 24 and a current supported API level
- AND an installable signed artifact is produced without embedded secrets.

# Design: Android Native Production

## Authority and Boundaries

`android/` is the active mobile client and SHALL be refactored in place. Its `applicationId` SHALL be `ir.xilo.app`; `minSdk` SHALL be 24. `mobile/` is a preserved Flutter legacy tree and SHALL NOT be used as an implementation target or completion evidence.

This change owns Android-client architecture. Shared domain behavior remains authoritative in the corresponding `xilo-platform` specifications, including `auth-spec.md`, `post-spec.md`, `comment-spec.md`, `notification-spec.md`, `search-spec.md`, `media-spec.md`, `monetization-spec.md`, `analytics-spec.md`, `discover-spec.md`, and `chat-spec.md`.

## Architecture

The app SHALL use Clean Architecture with package boundaries by feature:

```text
android/
  app/                         application, navigation, Compose theme
  core/
    network/                   Retrofit, OkHttp, auth interceptors, WebSocket
    database/                  Room entities, DAOs, migrations
    datastore/                 non-secret preferences
    security/                  Keystore-backed secret storage
    sync/                      WorkManager constraints, outbox executor
    ui/                        reusable Compose UI and accessibility utilities
  feature/<domain>/
    data/                      DTOs, local/remote sources, repository implementations
    domain/                    models, repository interfaces, use cases
    presentation/              Compose screens, ViewModels, UiState
```

- Presentation SHALL use Jetpack Navigation 3 and expose immutable `StateFlow` UI state and one-off events.
- Domain SHALL remain Android-framework independent.
- Data SHALL use Retrofit over OkHttp for REST, OkHttp WebSockets for realtime, Room for durable client data, Paging 3 for paginated lists, and Hilt for dependency injection.
- DataStore SHALL store locale, theme, onboarding, and non-secret settings. Tokens and cryptographic material SHALL be Keystore-protected and never logged.
- WorkManager SHALL execute constrained, retryable background synchronization and a durable mutation outbox.

## Networking, Realtime, and Offline

REST clients SHALL follow the shared API contracts. OkHttp SHALL attach access credentials only to approved Xilo origins, refresh expired credentials through a serialized refresh flow, and clear local authenticated state on terminal refresh failure.

WebSocket endpoints and event shapes SHALL follow `xilo-platform/design.md` and `chat-spec.md`. The app SHALL authenticate securely, reconnect with bounded exponential backoff and jitter, resubscribe after reconnect, and reconcile incoming events with Room.

Room is the read model for offline-supported screens. Repository reads SHALL emit local data first and refresh it when connectivity permits. Mutation intent SHALL be written atomically to an outbox before background execution. Workers SHALL preserve ordering where required, use network constraints, expose retry/conflict state, and remove an operation only after an idempotent success response.

Every retryable mutation request SHALL carry an `Idempotency-Key` header containing a UUIDv4 generated when the outbox operation is created. The same key SHALL be reused for every retry of the same semantic mutation. The backend SHALL deduplicate by authenticated principal, operation, and key for at least 30 days from the first received attempt and return the original semantic result, including the original resource identity and outcome, for duplicates. Reusing a key with a different semantic payload SHALL be rejected as a conflict. Backend persistence and contract-test implementation are a shared dependency and block the idempotency acceptance gate.

## Visual Authority

`openspec/changes/xilo-platform/specs/ui-ux-spec.md` is the normative visual authority for Android and web. In particular, own comment bubbles SHALL use `#E8F5FE` in light mode and `#1E3A5F` in dark mode; other users' comment bubbles SHALL use `#F7F9FA` in light mode and `#2C2C2E` in dark mode. Icon-and-title headings SHALL render on one horizontal row and SHALL never stack the icon above the title.

## Security and Privacy

- Production traffic SHALL use HTTPS/WSS only; cleartext traffic SHALL be disabled outside explicitly isolated development configuration.
- Refresh/access tokens, upload credentials, and notification registration data SHALL not be stored in plain preferences, source code, logs, or analytics payloads.
- The app SHALL minimize Android permissions and request media/notification permissions only in context.
- Release builds SHALL disable debug logging, protect network security configuration, and use secret injection outside version control.

## Quality and Release

The release pipeline SHALL run Kotlin formatting/static analysis, Android Lint, unit tests, Compose UI tests, and relevant connected/device tests. A release candidate SHALL verify API 24 compatibility, application startup, authenticated and offline smoke flows, push deep links, RTL, TalkBack labels, and signing/provenance controls. Performance baselines SHALL measure cold start, scrolling/jank, network failure recovery, and binary size.

## Dependencies Requiring Later Resolution

The following require implementation-time confirmation with their owning teams: production push provider credentials and registration endpoint; final deep-link host/verification; backend persistence for the frozen `Idempotency-Key` contract; media upload resumability and limits; payment provider/store policy; analytics consent and retention policy; release signing/key custody.

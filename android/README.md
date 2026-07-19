# Xilo Android (native)

Kotlin + Jetpack Compose client. Application id / namespace: `ir.xilo.app`.

## Requirements

- JDK 17+
- Android SDK (set `ANDROID_HOME`)
- Gradle Wrapper in this directory (`gradlew` / `gradlew.bat`)

## Quick start (emulator debug)

```bash
# From android/
./gradlew :app:assembleDebug
./gradlew :app:installDebug
# or
./run-on-emulator.sh
```

Debug builds default to the Android emulator host bridge:

| Endpoint | Default |
|----------|---------|
| REST API | `http://10.0.2.2:8888/` |
| WebSocket | `ws://10.0.2.2:8888/ws` |

Cleartext to `10.0.2.2` / `localhost` / `127.0.0.1` is allowed **only** in the `debug` source set (network security config + manifest override). The main/`release` manifest sets `usesCleartextTraffic=false`.

## Overriding API endpoints

Endpoints are injected via `BuildConfig` (`API_BASE_URL`, `WS_BASE_URL`). Prefer Gradle properties or environment variables — never hardcode hosts in Kotlin network code.

| Purpose | Gradle property | Environment variable |
|---------|-----------------|----------------------|
| REST base URL (trailing `/` optional) | `xilo.apiBaseUrl` | `XILO_API_BASE_URL` |
| WebSocket URL | `xilo.wsBaseUrl` | `XILO_WS_BASE_URL` |

Examples:

```bash
# Debug against a LAN device
./gradlew :app:assembleDebug -Pxilo.apiBaseUrl=http://192.168.1.10:8888/ -Pxilo.wsBaseUrl=ws://192.168.1.10:8888/ws

# Debug / emulator testing against production API (do not hardcode secrets)
./gradlew :app:assembleDebug \
  -Pxilo.apiBaseUrl=https://brain.aile.ir/ \
  -Pxilo.wsBaseUrl=wss://brain.aile.ir/ws

# Or installDebug for a connected emulator/device:
./gradlew :app:installDebug \
  -Pxilo.apiBaseUrl=https://brain.aile.ir/ \
  -Pxilo.wsBaseUrl=wss://brain.aile.ir/ws

# Release against production (aile.ir) — signed with android/signing/
./gradlew :app:assembleRelease \
  -Pxilo.apiBaseUrl=https://brain.aile.ir/ \
  -Pxilo.wsBaseUrl=wss://brain.aile.ir/ws
# → app/build/outputs/apk/release/app-release.apk
```

Release signing uses `android/signing/aile-release.jks` and `android/signing/keystore.properties` (see `android/signing/README.md`). Keep the repo private; back up the keystore and passwords offline.

For visual emulator checks after install, capture a screenshot to `android/emulator_test.png` (see `run-on-emulator.sh`). When pointing at production, use the Gradle properties above — never commit API keys or tokens.

Or with env vars (Windows `cmd`):

```bat
set XILO_API_BASE_URL=https://brain.aile.ir/
set XILO_WS_BASE_URL=wss://brain.aile.ir/ws
gradlew.bat :app:assembleRelease
```

See also `gradle.properties.example` for the production property names.

Release packaging runs `checkReleaseEndpoints` and **fails** if either override is missing, contains embedded credentials, lacks a valid host, or does not use `https://` for REST and `wss://` for WebSocket.

## Logging

- OkHttp `HttpLoggingInterceptor` uses `BASIC` only when `BuildConfig.DEBUG` is true; request and response bodies are never logged because they can contain passwords, tokens, messages, or other personal data. Release uses `NONE`.
- Headers `Authorization`, `Cookie`, `Set-Cookie`, and `Proxy-Authorization` are redacted.

## Durable chat outbox

Chat creation and message sending use a Room-backed durable outbox. The app
writes a canonical request payload and one UUIDv4 operation key before network
I/O and immediately schedules network-constrained WorkManager recovery before
foreground delivery. Immediate work uses bounded unique `KEEP` semantics.
Future retries use one replaceable delayed successor at the earliest eligible
attempt time, avoiding both an unbounded append chain and busy retry. Operations
drain oldest-first, and pending messages preserve order within each chat. The
same persisted key is reused after process death or an ambiguous server commit,
allowing the backend idempotency replay response to reconcile the authoritative
chat or message into Room.

On a true cold process start, all ambiguous `in_flight` rows are reset
immediately; a worker in the same process resets only rows stale for 15 minutes.
No credentials are stored in the outbox or WorkManager input. `IOException`,
local Room/SQLite reconciliation failures after HTTP success, HTTP 429, and HTTP
5xx failures use a maximum of six delivery attempts with exponential backoff;
`Retry-After` is honored up to 24 hours. A replay after local reconciliation
failure uses the same key and repairs Room without duplicating the server
mutation. A terminal HTTP 401 is `auth_required` only after the separate OkHttp
authenticator has exhausted refresh. Other stable HTTP 4xx failures, including
idempotency payload conflicts, become user-visible `permanent_failure` rows.

Permanent failures can be explicitly retried or deleted through
`ChatRepository`. A permanent older message does not block later messages in
the same chat: the failed bubble remains in place with accessible retry/delete
actions while later pending messages may continue. After 30 days, the retained
outbox payload and its failed optimistic bubble are purged together.

Room schemas v1 through v5 are exported under `app/schemas/`. Git history shows
that v1→v2 added only `chats.isArchived`; the explicit migration preserves
existing rows with `false`. v2→v3 creates the outbox table and indices. v3→v4
adds a unique nullable client operation correlation, a canonical payload hash,
and `pending` / `delivered` / `permanent_failure` delivery metadata to messages;
pre-existing server messages migrate as delivered. Until recipient receipts are
implemented, the internal `delivered` value means only that the server accepted
the authoritative message. Instrumented tests cover
3→4 and the complete 1→4 and 2→4 paths. No destructive production fallback is
configured.

For `message.send`, Room now commits the outbox operation, a deterministic
optimistic message, and the chat preview in one transaction before network I/O.
Transient failures keep that bubble queued, permanent failures update both
rows atomically, and successful/replayed responses replace the local bubble
with the correlated server message while removing the outbox row in the same
transaction. Duplicate WebSocket deliveries upsert by server id without
discarding REST correlation. The conversation UI exposes queued, server-
accepted, and permanent-failure states. Server acceptance uses one neutral tick
and does not claim recipient delivery or reading; those indicators remain
reserved for future receipt reconciliation.

This first outbox slice covers only:

- `chat.create`
- `message.send`

Posts, comments, reactions, server-driven read-receipt reconciliation, media uploads, profile/follow mutations,
notification registration, and other write operations remain to be added in
later ANP-1.4/ANP-3.3 slices.

## Release minify

Release enables R8 minify + resource shrinking with `app/proguard-rules.pro` (Retrofit, Kotlin Serialization, Hilt, Room keep rules).

## Useful tasks

```bash
./gradlew :app:testDebugUnitTest
./gradlew :app:lintDebug
./gradlew :app:assembleDebug
./gradlew :app:launchDebug
```

## Note on AGP 9 opt-outs

`gradle.properties` still sets `android.builtInKotlin=false`, `android.newDsl=false`, and `ksp.useKSP2=false`. These suppress AGP 9 defaults that would require a separate Kotlin/KSP migration; do not remove them until that migration is planned.

# Historical Prototype Notes: Native Android App

## Overview
> **Superseded as an active specification.** These prototype notes may be consulted only for historical visual context. The production authority is `openspec/changes/android-native-production/`, especially `openspec/changes/android-native-production/specs/android-native-production/spec.md`, `openspec/changes/android-native-production/specs/android-i18n/spec.md`, and its phased task gates. Where this document differs from that change or a shared domain specification, the newer authority wins.

The active client is `android/`, refactored in place with `applicationId ir.xilo.app`, `minSdk 24`, Kotlin, Jetpack Compose, Hilt, Room, Retrofit, OkHttp, Paging 3, WorkManager, DataStore, and Android Keystore.

---

## 1. Architectural Integrity

### REQ-AND-HIST-001: Clean Architecture Layer Separation
- **Presentation Layer**: Composable screens, ViewModels (StateFlow), and UI-bound models.
- **Domain Layer**: Clean Kotlin business logic (use cases and entity abstractions).
- **Data Layer**: Room database (local storage), SharedPreferences (local config), Retrofit API service (remote REST), and WebSocketManager (real-time stream).

### REQ-AND-HIST-002: Offline-First Caching (Room)
- **GIVEN** a user opens the application without internet connectivity
- **WHEN** they navigate to the Feed, Discover Feed, or Chat screens
- **THEN** the application MUST load cached data from the Room local database:
  - Feed: Last 50 cached posts.
  - Discover: Last 50 cached comments.
  - Chats: List of conversations and up to 100 messages per conversation.
- **AND** the app MUST show a discrete indicator showing the app is running in offline mode.

---

## 2. Visual & Layout Specifications

### REQ-AND-HIST-003: Profile collapsing toolbar and verified ticks
- **GIVEN** a user opens a profile screen
- **WHEN** they scroll down the screen
- **THEN** the hero banner image MUST collapse smoothly using a collapsing toolbar animation with translucent dark circular buttons (`<` and `Edit/More`).
- **AND** verified accounts MUST display a solid blue verified badge next to the display name.

### REQ-AND-HIST-004: Telegram-style reply bubbles
- **GIVEN** a post thread containing comments
- **WHEN** the comments are rendered in a list
- **THEN** replies MUST be encased in chat bubbles with a corner radius of 16dp and follow the visual authority in `openspec/changes/xilo-platform/specs/ui-ux-spec.md`:
  - The author's own responses MUST use light blue `#E8F5FE` in light mode and `#1E3A5F` in dark mode.
  - Other users' responses MUST use gray `#F7F9FA` in light mode and `#2C2C2E` in dark mode.
- **AND** replies MUST render Telegram-style emoji reaction pills (`🔥 12`) on the bottom-left inside of the bubble.
- **AND** a vertical thread connection line MUST link comment authors to their parents.

### REQ-AND-HIST-005: Chat conversations with floating bottom bar
- **GIVEN** a user on the main screen
- **WHEN** swapping tabs
- **THEN** the bottom bar MUST float above the content in a pill-shaped elevated container, containing `Feed`, `Discover`, `Chats`, and `Profile`.

---

## 3. Real-time Connection & Direct Notifications

### REQ-AND-HIST-006: OkHttp WebSocket Connection
- **GIVEN** a user is logged in
- **WHEN** the app starts or gains network connection
- **THEN** the app MUST open a WebSocket connection to `ws://localhost:8000/ws?token=JWT_TOKEN`.
- **AND** if the connection drops, it MUST reconnect with exponential backoff (starting at 1s, doubling up to 30s).
- **AND** the client MUST subscribe to:
  - `subscribe:post` with post ID when entering a thread.
  - `subscribe:user` with user ID to receive direct real-time notifications (messages, replies, follows) direct to the UI.

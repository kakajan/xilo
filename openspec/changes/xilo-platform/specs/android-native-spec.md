# Spec: Native Android App (Kotlin & Jetpack Compose)

## Overview
This document specifies the requirements, visual layout details, offline caching behavior, and real-time synchronization requirements for the native Android application of the Xilo platform. The app is built using Kotlin, Jetpack Compose, Dagger Hilt, Room Database, Retrofit, and OkHttp WebSockets.

---

## 1. Architectural Integrity

### REQ-AND-001: Clean Architecture Layer Separation
- **Presentation Layer**: Composable screens, ViewModels (StateFlow), and UI-bound models.
- **Domain Layer**: Clean Kotlin business logic (use cases and entity abstractions).
- **Data Layer**: Room database (local storage), SharedPreferences (local config), Retrofit API service (remote REST), and WebSocketManager (real-time stream).

### REQ-AND-002: Offline-First Caching (Room)
- **GIVEN** a user opens the application without internet connectivity
- **WHEN** they navigate to the Feed, Discover Feed, or Chat screens
- **THEN** the application MUST load cached data from the Room local database:
  - Feed: Last 50 cached posts.
  - Discover: Last 50 cached comments.
  - Chats: List of conversations and up to 100 messages per conversation.
- **AND** the app MUST show a discrete indicator showing the app is running in offline mode.

---

## 2. Visual & Layout Specifications

### REQ-AND-003: Profile collapsing toolbar and verified ticks
- **GIVEN** a user opens a profile screen
- **WHEN** they scroll down the screen
- **THEN** the hero banner image MUST collapse smoothly using a collapsing toolbar animation with translucent dark circular buttons (`<` and `Edit/More`).
- **AND** verified accounts MUST display a solid blue verified badge next to the display name.

### REQ-AND-004: Telegram-style reply bubbles
- **GIVEN** a post thread containing comments
- **WHEN** the comments are rendered in a list
- **THEN** replies MUST be encased in chat bubbles with a corner radius of 16dp and:
  - The author's own responses MUST alternate to a gray background `#F7F9FA`.
  - External user responses MUST use a light blue background `#E8F5FE` (or `#1E3A5F` in dark mode).
- **AND** replies MUST render Telegram-style emoji reaction pills (`🔥 12`) on the bottom-left inside of the bubble.
- **AND** a vertical thread connection line MUST link comment authors to their parents.

### REQ-AND-005: Chat conversations with floating bottom bar
- **GIVEN** a user on the main screen
- **WHEN** swapping tabs
- **THEN** the bottom bar MUST float above the content in a pill-shaped elevated container, containing `Feed`, `Discover`, `Chats`, and `Profile`.

---

## 3. Real-time Connection & Direct Notifications

### REQ-AND-006: OkHttp WebSocket Connection
- **GIVEN** a user is logged in
- **WHEN** the app starts or gains network connection
- **THEN** the app MUST open a WebSocket connection to `ws://localhost:8000/ws?token=JWT_TOKEN`.
- **AND** if the connection drops, it MUST reconnect with exponential backoff (starting at 1s, doubling up to 30s).
- **AND** the client MUST subscribe to:
  - `subscribe:post` with post ID when entering a thread.
  - `subscribe:user` with user ID to receive direct real-time notifications (messages, replies, follows) direct to the UI.

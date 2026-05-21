# Spec: Mobile App (Flutter)

## Overview
Cross-platform native mobile app built with Flutter 3+ and Dart 3+. Clean Architecture (presentation/domain/data), Riverpod state management, Dio networking, Hive local storage, WebSocket real-time communication. UX inspired by Telegram with smooth animations.

---

## Requirements

### REQ-MOB-001: Feature Parity

The mobile app mirrors all web features:

| Feature | Web | Mobile |
|---------|-----|--------|
| Browse feed | Yes | Yes |
| Discover feed | Yes | Yes |
| Read posts | Yes | Yes |
| Search | Yes | Yes |
| Write/Edit posts | Yes (Tiptap) | Yes (custom editor) |
| Comments + reactions | Yes | Yes |
| Chat/Messaging | Yes | Yes |
| Notifications | Yes (WS) | Yes (WS + Push) |
| Bookmarks | Yes | Yes |
| User profiles | Yes | Yes |
| Settings | Yes | Yes |
| Dark mode | Yes | Yes (system) |
| Offline reading | Limited | Yes (full, via Hive) |

### REQ-MOB-002: Architecture

```
lib/
├── main.dart
├── app.dart
├── core/
│   ├── di/              — GetIt service locator
│   ├── router/          — GoRouter configuration
│   ├── theme/           — AppTheme (light + dark)
│   ├── network/         — Dio client, interceptors
│   ├── websocket/       — WebSocket manager
│   ├── storage/         — Hive + SharedPreferences
│   ├── utils/           — Extensions, helpers
│   └── errors/          — Failure classes
├── features/
│   ├── auth/
│   │   ├── data/        — Repositories, data sources
│   │   ├── domain/      — Entities, use cases
│   │   └── presentation/ — Pages, providers, widgets
│   ├── feed/
│   ├── discover/
│   ├── post/
│   ├── search/
│   ├── editor/
│   ├── comments/
│   ├── chat/
│   ├── notifications/
│   ├── profile/
│   └── settings/
└── shared/
    └── widgets/         — Reusable components
```

### REQ-MOB-003: Navigation

**GoRouter** with these routes:

| Route | Screen |
|-------|--------|
| `/` | Feed (Home) |
| `/discover` | Discover Feed |
| `/post/:slug` | Post Detail |
| `/search` | Search |
| `/editor` | Post Editor |
| `/editor/:id` | Edit Post |
| `/chat` | Chat List |
| `/chat/:id` | Chat Conversation |
| `/notifications` | Notifications |
| `/profile/:username` | User Profile |
| `/bookmarks` | Bookmarks |
| `/settings` | Settings |
| `/settings/profile` | Edit Profile |
| `/settings/billing` | Billing |
| `/login` | Login |
| `/register` | Register |

### REQ-MOB-004: Offline Support

**Given** a user without internet  
**When** they open the app  
**Then** they can:
- Read previously cached posts (last 50)
- View cached feed
- View cached discover feed
- Write drafts (synced when online)
- See cached profile info
- Read cached chat messages (last 100 per chat)
- Queue outgoing chat messages (synced when online)

**Hive boxes:**
- `posts` — cached post data
- `feed` — cached feed items
- `discover` — cached discover items
- `drafts` — local drafts
- `user` — cached profile
- `settings` — preferences
- `chats` — cached chat list
- `messages` — cached messages per chat

### REQ-MOB-005: Push Notifications

**Given** the app is in background  
**When** a notification event fires  
**Then** a push notification is delivered via Firebase Cloud Messaging (FCM) or a self-hosted alternative.

### REQ-MOB-006: Animations

- **Hero animations**: Post card → Post detail cover image
- **Page transitions**: Slide left/right (iOS-style) or fade
- **Shimmer loading**: Skeleton placeholders for all lists
- **Pull to refresh**: All scrollable lists
- **Infinite scroll**: Feed, comments, discover, search results, chat messages
- **Haptic feedback**: Reactions, button taps
- **Staggered list animations**: Feed items appear in sequence
- **Bubble entrance**: Comment/chat bubbles slide in with opacity + translateY animation

### REQ-MOB-008: Discover Screen

**Given** a user on the Discover tab  
**When** the screen loads  
**Then** they see:
- Infinite-scroll list of comment cards
- Each card: author avatar, name, comment text, engagement counts, parent post link
- Topic filter chips at top
- Pull-to-refresh support
- Empty state with illustration

### REQ-MOB-009: Chat Screen

**Given** a user on the Chat tab  
**When** the screen loads  
**Then** they see:
- **Chat list**: Conversations sorted by last message time
  - Avatar, name, last message preview, unread badge, timestamp
  - Swipe actions: archive, delete
- **Chat conversation**: Telegram-style message bubbles
  - Own messages: right-aligned, blue bubble
  - Others: left-aligned, gray bubble
  - Message composer with emoji picker, attachment, send
  - Typing indicator, online status
  - Long-press message for: reply, copy, forward, delete, react
  - Swipe right to reply, swipe left to like

### REQ-MOB-007: Performance Targets

- Cold start: < 1.5s
- Warm start: < 500ms
- Frame rate: 60fps (120fps on ProMotion)
- APK size: < 50MB
- IPA size: < 80MB

---

## Key Dependencies

```yaml
dependencies:
  flutter_riverpod          # State management
  riverpod_annotation       # Code generation for Riverpod
  go_router                 # Navigation
  dio                       # HTTP client
  retrofit                  # API code generation
  hive_flutter              # Local storage
  freezed_annotation        # Immutable models
  json_annotation           # JSON serialization
  cached_network_image      # Image caching
  web_socket_channel        # WebSocket
  flutter_animate           # Animation helpers
  shimmer                   # Loading effects
  firebase_messaging        # Push notifications (or self-hosted)
  image_picker              # Media selection
  share_plus                # Share sheet
  flutter_slidable          # Swipe actions (chat list)
  
dev_dependencies:
  build_runner              # Code generation
  riverpod_generator        # Riverpod code gen
  retrofit_generator         # API code gen
  freezed                   # Immutable classes
  json_serializable         # JSON code gen
  hive_generator            # Hive code gen
  flutter_lints             # Linting
  mocktail                  # Testing
```

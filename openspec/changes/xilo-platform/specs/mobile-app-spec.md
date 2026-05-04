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
| Read posts | Yes | Yes |
| Search | Yes | Yes |
| Write/Edit posts | Yes (Tiptap) | Yes (custom editor) |
| Comments + reactions | Yes | Yes |
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
│   ├── post/
│   ├── search/
│   ├── editor/
│   ├── comments/
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
| `/post/:slug` | Post Detail |
| `/search` | Search |
| `/editor` | Post Editor |
| `/editor/:id` | Edit Post |
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
- Write drafts (synced when online)
- See cached profile info

**Hive boxes:**
- `posts` — cached post data
- `feed` — cached feed items
- `drafts` — local drafts
- `user` — cached profile
- `settings` — preferences

### REQ-MOB-005: Push Notifications

**Given** the app is in background  
**When** a notification event fires  
**Then** a push notification is delivered via Firebase Cloud Messaging (FCM) or a self-hosted alternative.

### REQ-MOB-006: Animations

- **Hero animations**: Post card → Post detail cover image
- **Page transitions**: Slide left/right (iOS-style) or fade
- **Shimmer loading**: Skeleton placeholders for all lists
- **Pull to refresh**: All scrollable lists
- **Infinite scroll**: Feed, comments, search results
- **Haptic feedback**: Reactions, button taps
- **Staggered list animations**: Feed items appear in sequence

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

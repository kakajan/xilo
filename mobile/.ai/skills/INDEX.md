# Skills Index

> Quick reference for all available AI development skills

| Skill | File | Use When |
|-------|------|----------|
| Riverpod State Management | `riverpod.md` | Creating/modifying providers, state |
| Freezed Models | `freezed-models.md` | Creating data models, entities |
| Dio Networking | `dio-networking.md` | HTTP requests, interceptors, API |
| GoRouter Navigation | `gorouter-navigation.md` | Routing, deep links, navigation |
| Hive Storage | `hive-storage.md` | Local storage, caching, drafts |
| UI & Widgets | `ui-widgets.md` | Screens, widgets, animations |
| Testing | `testing.md` | Unit tests, widget tests |
| WebSocket Real-time | `websocket-realtime.md` | Live comments, notifications |
| Service Locator | `service-locator.md` | DI, GetIt registration |
| Theme & Styling | `theme-styling.md` | Colors, typography, responsive |

## Quick Commands

```bash
# Generate code (freezed, json, retrofit)
flutter pub run build_runner build --delete-conflicting-outputs

# Watch mode
flutter pub run build_runner watch --delete-conflicting-outputs

# Analyze
flutter analyze

# Format
dart format .

# Test
flutter test

# Test single file
flutter test test/features/auth/auth_test.dart

# Build
flutter build apk --release
flutter build ios --release
```

## Feature Checklist

When creating a new feature:

- [ ] Create domain entity (Freezed)
- [ ] Create repository interface
- [ ] Create remote data source
- [ ] Create local data source (if caching needed)
- [ ] Create repository implementation
- [ ] Create use case(s)
- [ ] Create Riverpod provider
- [ ] Create screen
- [ ] Create widgets
- [ ] Add route in app_router.dart
- [ ] Register in service_locator.dart
- [ ] Write tests
- [ ] Run `flutter analyze`

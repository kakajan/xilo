# Xilo Mobile — AI Agent Guide

> **هدف**: افزایش سرعت توسعه با هوش مصنوعی و کاهش مصرف توکن

## 📋 قوانین کلی (MUST)

1. **قبل از کدنویسی**: این فایل + `.ai/skills/` را بخوان
2. **زبان کامنت**: انگلیسی
3. **معماری**: Clean Architecture (presentation → domain → data)
4. **State Management**: Riverpod 3.x با `@riverpod` codegen
5. **DI**: GetIt (service_locator.dart) برای singletonها
6. **Models**: `@freezed` + `@jsonSerializable` برای همه مدل‌ها
7. **Network**: Dio با interceptors
8. **Offline**: Hive — cache last 50 posts
9. **Navigation**: GoRouter با deep link support
10. **Testing**: هر feature باید unit test داشته باشد

## 🏗 ساختار پروژه

```
lib/
├── main.dart                      # Entry point + ProviderScope
├── core/
│   ├── di/service_locator.dart    # GetIt registration
│   ├── network/dio_client.dart    # Dio + interceptors
│   ├── router/app_router.dart     # GoRouter config
│   ├── storage/offline_storage.dart # Hive service
│   ├── theme/app_theme.dart       # Light/Dark themes
│   └── websocket/                 # Real-time events
│       ├── ws_manager.dart
│       └── ws_provider.dart
├── features/                      # Feature modules
│   ├── auth/                      # Login/Register
│   ├── feed/                      # Home feed
│   ├── post/                      # Post detail
│   ├── editor/                    # Post editor
│   ├── search/                    # Search
│   ├── comments/                  # Live comments
│   ├── notifications/             # Notifications
│   ├── profile/                   # User profile
│   ├── bookmarks/                 # Saved posts
│   ├── billing/                   # Subscriptions
│   ├── settings/                  # App settings
│   └── api/                       # Shared API providers
└── shared/widgets/                # Reusable widgets
    ├── animations.dart
    ├── shimmer_list.dart
    └── states.dart                # Error/Empty/Loading
```

## 🎯 الگوی ساخت Feature جدید

هر feature جدید این ساختار را دنبال کند:

```
features/<name>/
├── data/
│   ├── datasources/
│   │   ├── <name>_remote.dart     # API calls
│   │   └── <name>_local.dart      # Hive cache
│   └── repositories/
│       └── <name>_repo_impl.dart  # Implementation
├── domain/
│   ├── entities/<name>.dart       # Freezed model
│   ├── repositories/
│   │   └── <name>_repository.dart # Interface
│   └── usecases/
│       └── <name>_usecase.dart    # Business logic
└── presentation/
    ├── providers/<name>_provider.dart  # Riverpod
    ├── screens/<name>_screen.dart      # UI
    └── widgets/                        # Feature widgets
```

## ⚡ الگوهای پرکاربرد

### Riverpod Provider

```dart
@riverpod
class FeedNotifier extends _$FeedNotifier {
  @override
  Future<List<Post>> build() => _fetch();

  final _repo = sl<FeedRepository>();

  Future<List<Post>> _fetch() async => _repo.getFeed();
  Future<void> refresh() async { state = const AsyncLoading(); state = AsyncData(await _fetch()); }
}
```

### Freezed Model

```dart
@freezed
class Post with _$Post {
  const factory Post({
    required String id,
    required String title,
    String? coverUrl,
    @Default([]) List<String> tags,
    required DateTime createdAt,
  }) = _Post;

  factory Post.fromJson(Map<String, dynamic> json) => _$PostFromJson(json);
}
```

### Offline-First Repository

```dart
class PostRepoImpl implements PostRepository {
  Future<List<Post>> getFeed({String? cursor}) async {
    try {
      final posts = await _remote.getFeed(cursor: cursor);
      if (cursor == null) await _local.cachePosts(posts);
      return posts;
    } on DioException {
      return _local.getCachedPosts();
    }
  }
}
```

## 🔧 دستورات پرکاربرد

```bash
# Generate freezed + json models
flutter pub run build_runner build --delete-conflicting-outputs

# Watch mode
flutter pub run build_runner watch --delete-conflicting-outputs

# Run tests
flutter test

# Analyze
flutter analyze

# Format
dart format .

# Build APK
flutter build apk --release

# Build iOS
flutter build ios --release
```

## 📦 Dependencies

| Package | Purpose |
|---------|---------|
| `flutter_riverpod` | State management |
| `go_router` | Navigation |
| `dio` | HTTP client |
| `freezed_annotation` | Data models |
| `hive_flutter` | Local storage |
| `web_socket_channel` | Real-time |
| `cached_network_image` | Image caching |
| `flutter_animate` | Animations |
| `shimmer` | Loading states |
| `flutter_secure_storage` | Secure tokens |

## 🚫 نبایدها

- ❌ از `setState` برای state management استفاده نکن (Riverpod)
- ❌ مدل‌ها را بدون `@freezed` نساز
- ❌ DI را بدون GetIt انجام نده
- ❌ API call مستقیم در UI ممنوع
- ❌ کامنت فارسی در کد ممنوع
- ❌ `print` برای production ممنوع (از logger استفاده کن)

## ✅ چک‌لیست قبل از commit

- [ ] `flutter analyze` بدون error
- [ ] `flutter test` پاس شده
- [ ] `build_runner` اجرا شده
- [ ] کامنت‌ها انگلیسی هستند
- [ ] از الگوهای موجود پیروی شده

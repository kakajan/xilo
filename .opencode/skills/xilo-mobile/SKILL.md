---
name: xilo-mobile
description: Use when writing Flutter/Dart mobile code for the Xilo platform. Covers Clean Architecture, Riverpod state management, Dio networking, Hive storage, GoRouter navigation, and offline patterns.
---

# Xilo Mobile Development

Flutter 3+, Dart 3+, Clean Architecture, Riverpod, Dio, Hive, GoRouter.

## Project Structure

```
mobile/
├── lib/
│   ├── main.dart
│   ├── app.dart                        # MaterialApp + GoRouter + Theme
│   ├── core/
│   │   ├── di/injection_container.dart # GetIt registration
│   │   ├── router/app_router.dart      # GoRouter config
│   │   ├── theme/app_theme.dart        # Light + Dark themes
│   │   ├── network/
│   │   │   ├── dio_client.dart
│   │   │   └── interceptors/auth_interceptor.dart
│   │   ├── websocket/ws_manager.dart
│   │   ├── storage/
│   │   │   ├── hive_service.dart
│   │   │   └── secure_storage.dart
│   │   └── utils/extensions.dart
│   ├── features/
│   │   ├── auth/
│   │   │   ├── data/
│   │   │   │   ├── datasources/auth_remote.dart
│   │   │   │   ├── datasources/auth_local.dart
│   │   │   │   └── repositories/auth_repo_impl.dart
│   │   │   ├── domain/
│   │   │   │   ├── entities/user.dart
│   │   │   │   ├── repositories/auth_repository.dart
│   │   │   │   └── usecases/login.dart
│   │   │   └── presentation/
│   │   │       ├── providers/auth_provider.dart
│   │   │       ├── pages/login_page.dart
│   │   │       └── widgets/login_form.dart
│   │   ├── feed/
│   │   ├── post/
│   │   ├── search/
│   │   ├── editor/
│   │   ├── comments/
│   │   ├── notifications/
│   │   ├── profile/
│   │   └── settings/
│   └── shared/widgets/
│       ├── post_card.dart
│       ├── skeleton_loader.dart
│       └── error_state.dart
├── pubspec.yaml
└── analysis_options.yaml
```

## Dependency Injection (GetIt)

```dart
// core/di/injection_container.dart
final sl = GetIt.instance;

Future<void> initDependencies() async {
  // External
  sl.registerLazySingleton(() => Dio(BaseOptions(baseUrl: Config.apiUrl)));
  sl.registerLazySingleton(() => WebSocketManager(Config.wsUrl));
  
  // Hive boxes
  final postBox = await Hive.openBox('posts');
  sl.registerLazySingleton(() => postBox);
  
  // Data Sources
  sl.registerLazySingleton(() => AuthRemoteDataSource(sl()));
  sl.registerLazySingleton(() => AuthLocalDataSource(sl()));
  
  // Repositories
  sl.registerLazySingleton<AuthRepository>(
    () => AuthRepositoryImpl(remote: sl(), local: sl()),
  );
  
  // Use Cases
  sl.registerLazySingleton(() => LoginUseCase(sl()));
  sl.registerLazySingleton(() => GetFeedUseCase(sl()));
  
  // Providers (Riverpod — auto-dispose when not listened)
}
```

## Riverpod Providers

```dart
// features/feed/presentation/providers/feed_provider.dart

@riverpod
class FeedNotifier extends _$FeedNotifier {
  @override
  Future<List<Post>> build() => _fetchPage(null);

  final _repository = sl<FeedRepository>();

  Future<void> loadMore() async {
    final current = state.valueOrNull ?? [];
    final cursor = current.isNotEmpty ? current.last.id : null;
    final newPosts = await _repository.getFeed(cursor: cursor);
    state = AsyncData([...current, ...newPosts]);
  }

  Future<void> refresh() async {
    state = const AsyncLoading();
    state = AsyncData(await _repository.getFeed());
  }

  Future<List<Post>> _fetchPage(String? cursor) =>
      _repository.getFeed(cursor: cursor);
}
```

## Dio Network Setup

```dart
// core/network/dio_client.dart
Dio createDioClient() {
  final dio = Dio(BaseOptions(
    baseUrl: Config.apiUrl,
    connectTimeout: const Duration(seconds: 10),
    receiveTimeout: const Duration(seconds: 10),
    headers: {'Content-Type': 'application/json'},
  ));

  dio.interceptors.addAll([
    AuthInterceptor(),           // Inject JWT token
    LogInterceptor(requestBody: true, responseBody: true),
    RetryInterceptor(dio: dio, retries: 3),
  ]);

  return dio;
}

// interceptors/auth_interceptor.dart
class AuthInterceptor extends Interceptor {
  @override
  void onRequest(RequestOptions options, RequestInterceptorHandler handler) {
    final token = sl<SecureStorage>().getAccessToken();
    if (token != null) {
      options.headers['Authorization'] = 'Bearer $token';
    }
    handler.next(options);
  }

  @override
  void onError(DioException err, ErrorInterceptorHandler handler) async {
    if (err.response?.statusCode == 401) {
      await _refreshToken();
      // Retry the request
      handler.resolve(await _retry(err.requestOptions));
    } else {
      handler.next(err);
    }
  }
}
```

## Freezed Models

```dart
// features/post/domain/entities/post.dart
import 'package:freezed_annotation/freezed_annotation.dart';

part 'post.freezed.dart';
part 'post.g.dart';

@freezed
class Post with _$Post {
  const factory Post({
    required String id,
    required String title,
    required String slug,
    required String excerpt,
    required String authorName,
    String? coverImageUrl,
    @Default([]) List<String> tags,
    required DateTime publishedAt,
    required int readingTime,
    @Default(0) int reactionsCount,
    @Default(0) int commentsCount,
  }) = _Post;

  factory Post.fromJson(Map<String, dynamic> json) => _$PostFromJson(json);
}
```

## Offline Pattern (Hive)

```dart
// features/feed/data/datasources/feed_local.dart
class FeedLocalDataSource {
  final Box<String> _box;

  Future<List<Post>> getCachedFeed() async {
    final json = _box.get('feed');
    if (json == null) return [];
    final list = jsonDecode(json) as List;
    return list.map((e) => Post.fromJson(e)).toList();
  }

  Future<void> cacheFeed(List<Post> posts) async {
    final json = jsonEncode(posts.map((p) => p.toJson()).toList());
    await _box.put('feed', json);
  }
}

// features/feed/data/repositories/feed_repo_impl.dart
class FeedRepositoryImpl implements FeedRepository {
  Future<List<Post>> getFeed({String? cursor}) async {
    try {
      final posts = await _remote.getFeed(cursor: cursor);
      if (cursor == null) await _local.cacheFeed(posts);
      return posts;
    } on DioException {
      // Offline: return cached posts
      return _local.getCachedFeed();
    }
  }
}
```

## WebSocket Manager

```dart
// core/websocket/ws_manager.dart
class WebSocketManager {
  final WebSocketChannel _channel;
  final _controller = StreamController<WsEvent>.broadcast();

  Stream<WsEvent> get events => _controller.stream;

  void connect() {
    _channel.stream.listen(
      (data) {
        final event = WsEvent.fromJson(jsonDecode(data));
        _controller.add(event);
      },
      onDone: _reconnect,
      onError: (_) => _reconnect(),
    );
  }

  void _reconnect() {
    Future.delayed(const Duration(seconds: 2), connect);
  }

  void subscribe(String postId) {
    _channel.sink.add(jsonEncode({'type': 'subscribe', 'postId': postId}));
  }
}
```

## Key Rules

- Use `@riverpod` code generation (not manual `StateNotifier`)
- Use `@freezed` for all data models
- Use `service locator` (GetIt) for DI, not Riverpod for singleton dependencies
- All network calls go through Dio with interceptors
- Offline-first: read from Hive, then try API, save to Hive
- Use `GoRouter` for navigation with deep link support
- Hero animations for image transitions between pages
- Shimmer loading for all list views
- `minSdkVersion: 23`, iOS `17.0+`

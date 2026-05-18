# Skill: Service Locator (GetIt)

> Use when creating or modifying dependency injection with GetIt

## Rules

1. ALL singleton dependencies MUST be registered in `service_locator.dart`
2. Use `registerLazySingleton` for services created on demand
3. Use `registerSingleton` for services needed immediately
4. Use `registerFactory` for objects created per use
5. NEVER use Riverpod for singleton DI (use GetIt)
6. Riverpod is ONLY for UI state management

## Service Locator Setup

```dart
// core/di/service_locator.dart
final sl = GetIt.instance;

Future<void> setupServiceLocator() async {
  // ===== External =====
  sl.registerLazySingleton(() => createDioClient());
  sl.registerLazySingleton(() => WebSocketManager(Config.wsUrl));
  sl.registerLazySingleton(() => SecureStorage());

  // ===== Hive Boxes =====
  sl.registerLazySingleton(() => Hive.box<String>('posts'));
  sl.registerLazySingleton(() => Hive.box<dynamic>('settings'));
  sl.registerLazySingleton(() => Hive.box<String>('drafts'));

  // ===== Core Services =====
  sl.registerLazySingleton<AuthService>(() => AuthServiceImpl(sl()));

  // ===== Data Sources =====
  // Auth
  sl.registerLazySingleton<AuthRemoteDataSource>(
    () => AuthRemoteDataSourceImpl(sl()),
  );
  sl.registerLazySingleton<AuthLocalDataSource>(
    () => AuthLocalDataSourceImpl(sl()),
  );

  // Posts
  sl.registerLazySingleton<PostRemoteDataSource>(
    () => PostRemoteDataSourceImpl(sl()),
  );
  sl.registerLazySingleton<PostLocalDataSource>(
    () => PostLocalDataSourceImpl(sl()),
  );

  // Comments
  sl.registerLazySingleton<CommentRemoteDataSource>(
    () => CommentRemoteDataSourceImpl(sl()),
  );

  // ===== Repositories =====
  sl.registerLazySingleton<AuthRepository>(
    () => AuthRepositoryImpl(remote: sl(), local: sl()),
  );
  sl.registerLazySingleton<PostRepository>(
    () => PostRepositoryImpl(remote: sl(), local: sl()),
  );
  sl.registerLazySingleton<CommentRepository>(
    () => CommentRepositoryImpl(remote: sl()),
  );

  // ===== Use Cases =====
  sl.registerLazySingleton(() => LoginUseCase(sl()));
  sl.registerLazySingleton(() => RegisterUseCase(sl()));
  sl.registerLazySingleton(() => GetFeedUseCase(sl()));
  sl.registerLazySingleton(() => GetPostUseCase(sl()));
  sl.registerLazySingleton(() => CreatePostUseCase(sl()));
  sl.registerLazySingleton(() => GetCommentsUseCase(sl()));
  sl.registerLazySingleton(() => AddCommentUseCase(sl()));

  // ===== Providers (Riverpod) =====
  // Providers are auto-discovered, no registration needed
}
```

## Usage Pattern

```dart
// In providers
@riverpod
class FeedNotifier extends _$FeedNotifier {
  @override
  Future<List<Post>> build() => _fetch();

  // Access repository via service locator
  final _repo = sl<PostRepository>();

  Future<List<Post>> _fetch() => _repo.getFeed();
}

// In use cases
class GetFeedUseCase {
  final PostRepository _repository;

  GetFeedUseCase(this._repository);

  Future<List<Post>> call({String? cursor}) {
    return _repository.getFeed(cursor: cursor);
  }
}

// In screens (avoid direct sl usage when possible)
// Prefer ref.watch(provider) instead
```

## Registration Types

| Type | When to Use | Example |
|------|-------------|---------|
| `registerLazySingleton` | Created on first use | Dio, Repositories |
| `registerSingleton` | Needed immediately | Config, Constants |
| `registerFactory` | New instance each time | UseCase (if stateful) |
| `registerLazySingletonAsync` | Async initialization | Hive boxes |

## Testing with GetIt

```dart
void main() {
  setUp(() {
    // Reset before each test
    sl.reset();
  });

  test('use case returns posts', () async {
    // Arrange
    final mockRepo = MockPostRepository();
    sl.registerLazySingleton<PostRepository>(() => mockRepo);

    when(() => mockRepo.getFeed()).thenAnswer((_) async => [tPost]);

    // Act
    final result = await sl<GetFeedUseCase>()();

    // Assert
    expect(result, [tPost]);
  });
}
```

## Reset for Tests

```dart
// Call before each test
Future<void> resetServiceLocator() async {
  await sl.reset();
}

// Unregister specific
sl.unregister<PostRepository>();

// Check if registered
sl.isRegistered<PostRepository>();
```

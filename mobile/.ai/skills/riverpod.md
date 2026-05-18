# Skill: Riverpod State Management

> Use when creating or modifying state management with Riverpod 3.x

## Rules

1. ALWAYS use `@riverpod` annotation (code generation), NOT manual `StateNotifier`
2. Providers MUST be in `presentation/providers/` folder
3. Use `AsyncValue` for async state (loading/error/data)
4. Use `AutoDispose` for providers that should clean up
5. Use `KeepAlive` only for critical data (user session, settings)

## Provider Templates

### Simple Async Provider

```dart
@riverpod
Future<Post> postDetail(PostDetailRef ref, String postId) async {
  return sl<PostRepository>().getPost(postId);
}
```

### Notifier with Actions

```dart
@riverpod
class FeedNotifier extends _$FeedNotifier {
  @override
  Future<List<Post>> build() => _fetch();

  final _repo = sl<FeedRepository>();

  Future<List<Post>> _fetch() async => _repo.getFeed();

  Future<void> refresh() async {
    state = const AsyncLoading();
    state = AsyncData(await _fetch());
  }

  Future<void> loadMore() async {
    final current = state.valueOrNull ?? [];
    if (current.isEmpty) return;
    final cursor = current.last.id;
    final more = await _repo.getFeed(cursor: cursor);
    state = AsyncData([...current, ...more]);
  }
}
```

### Family Provider (parameterized)

```dart
@riverpod
Future<List<Comment>> postComments(PostCommentsRef ref, String postId) async {
  return sl<CommentRepository>().getComments(postId);
}
```

### Stream Provider (WebSocket)

```dart
@riverpod
Stream<Notification> notificationStream(NotificationStreamRef ref) {
  return sl<WebSocketManager>().events.whereType<Notification>();
}
```

## UI Usage

```dart
// Watch provider
final feed = ref.watch(feedNotifierProvider);

feed.when(
  data: (posts) => ListView.builder(...),
  loading: () => const ShimmerList(),
  error: (e, _) => ErrorState(message: e.toString()),
);

// Call action
ref.read(feedNotifierProvider.notifier).refresh();
```

## Common Patterns

| Pattern | Implementation |
|---------|---------------|
| Pagination | `loadMore()` with cursor |
| Refresh | `refresh()` resets state |
| Optimistic update | Update state before API call, rollback on error |
| Cache | Use `ref.cache` for short-lived data |
| Invalidate | `ref.invalidate(provider)` to refresh |

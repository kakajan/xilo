# Skill: Hive Offline Storage

> Use when creating or modifying local data storage with Hive

## Rules

1. Hive MUST be initialized in `main()` before `runApp()`
2. Use type adapters for complex objects
3. Cache strategy: write on success, read on failure
4. Max cache: last 50 posts (LRU eviction)
5. Box names: lowercase with underscores

## Initialization

```dart
// main.dart
void main() async {
  WidgetsFlutterBinding.ensureInitialized();

  await Hive.initFlutter();
  await Hive.openBox('posts');
  await Hive.openBox('settings');
  await Hive.openBox('drafts');

  await setupServiceLocator();
  runApp(const ProviderScope(child: XiloApp()));
}
```

## Local Data Source Pattern

```dart
class PostLocalDataSource {
  final Box<String> _box;

  PostLocalDataSource(this._box);

  Future<List<Post>> getCachedPosts() async {
    final json = _box.get('feed');
    if (json == null) return [];

    final list = jsonDecode(json) as List;
    return list.map((e) => Post.fromJson(e)).toList();
  }

  Future<void> cachePosts(List<Post> posts) async {
    // Keep only last 50 posts
    final limited = posts.take(50).toList();
    final json = jsonEncode(limited.map((p) => p.toJson()).toList());
    await _box.put('feed', json);
  }

  Future<void> clearCache() async {
    await _box.delete('feed');
  }
}
```

## Draft Storage

```dart
class DraftLocalDataSource {
  final Box<String> _box;

  Future<List<Draft>> getDrafts() async {
    final keys = _box.keys.where((k) => k.toString().startsWith('draft_'));
    return keys
        .map((k) => Draft.fromJson(jsonDecode(_box.get(k)!)))
        .toList();
  }

  Future<void> saveDraft(Draft draft) async {
    final json = jsonEncode(draft.toJson());
    await _box.put('draft_${draft.id}', json);
  }

  Future<void> deleteDraft(String id) async {
    await _box.delete('draft_$id');
  }
}
```

## Settings Storage

```dart
class SettingsLocalDataSource {
  final Box<dynamic> _box;

  ThemeMode getThemeMode() {
    final value = _box.get('theme_mode', defaultValue: 'system');
    return switch (value) {
      'light' => ThemeMode.light,
      'dark' => ThemeMode.dark,
      _ => ThemeMode.system,
    };
  }

  Future<void> setThemeMode(ThemeMode mode) async {
    final value = switch (mode) {
      ThemeMode.light => 'light',
      ThemeMode.dark => 'dark',
      ThemeMode.system => 'system',
    };
    await _box.put('theme_mode', value);
  }

  bool getNotificationsEnabled() =>
      _box.get('notifications', defaultValue: true);

  Future<void> setNotificationsEnabled(bool enabled) async {
    await _box.put('notifications', enabled);
  }
}
```

## Type Adapters (for complex objects)

```dart
// Generate with: flutter pub run build_runner build
@HiveType(typeId: 0)
class PostAdapter extends TypeAdapter<Post> {
  @override
  final int typeId = 0;

  @override
  Post read(BinaryReader reader) {
    return Post(
      id: reader.readString(),
      title: reader.readString(),
      // ...
    );
  }

  @override
  void write(BinaryWriter writer, Post obj) {
    writer.writeString(obj.id);
    writer.writeString(obj.title);
    // ...
  }
}
```

## Cache-First Repository Pattern

```dart
class PostRepoImpl implements PostRepository {
  Future<List<Post>> getFeed({String? cursor}) async {
    // If loading more, don't use cache
    if (cursor != null) {
      return _remote.getFeed(cursor: cursor);
    }

    try {
      // Try network first
      final posts = await _remote.getFeed();
      await _local.cachePosts(posts);
      return posts;
    } on DioException {
      // Fallback to cache
      return _local.getCachedPosts();
    }
  }
}
```

## Box Management

```dart
// Clear all cache
Future<void> clearAllCache() async {
  await Hive.box('posts').clear();
  await Hive.box('settings').clear();
}

// Get box size
int getCacheSize() {
  return Hive.box('posts').length;
}

// Close boxes (on logout)
Future<void> closeBoxes() async {
  await Hive.close();
}
```

import 'package:hive_flutter/hive_flutter.dart';

class OfflineStorage {
  static const _maxCachedPosts = 50;
  static const _feedBox = 'feed';
  static const _draftsBox = 'drafts';
  static const _settingsBox = 'settings';

  Future<void> init() async {
    await Hive.initFlutter();
    await Hive.openBox(_feedBox);
    await Hive.openBox(_draftsBox);
    await Hive.openBox(_settingsBox);
  }

  Future<void> cachePosts(List<Map<String, dynamic>> posts) async {
    final box = Hive.box(_feedBox);
    final allCached = List<Map<String, dynamic>>.from(
      box.get('posts', defaultValue: []) as List
    );

    final existingIds = allCached.map((p) => p['id']).toSet();
    for (final post in posts) {
      if (!existingIds.contains(post['id'])) {
        allCached.insert(0, post);
      } else {
        final idx = allCached.indexWhere((p) => p['id'] == post['id']);
        if (idx >= 0) allCached[idx] = post;
      }
    }

    if (allCached.length > _maxCachedPosts) {
      allCached.removeRange(_maxCachedPosts, allCached.length);
    }

    await box.put('posts', allCached);
  }

  List<Map<String, dynamic>> getCachedPosts() {
    final box = Hive.box(_feedBox);
    final raw = box.get('posts', defaultValue: []) as List;
    return raw.cast<Map<String, dynamic>>();
  }

  Future<void> saveDraft(String id, String title, String content) async {
    final box = Hive.box(_draftsBox);
    final drafts = Map<String, dynamic>.from(
      box.get('items', defaultValue: {}) as Map
    );
    drafts[id] = {'title': title, 'content': content, 'updatedAt': DateTime.now().toIso8601String()};
    await box.put('items', drafts);
  }

  Map<String, dynamic> getDrafts() {
    final box = Hive.box(_draftsBox);
    return Map<String, dynamic>.from(
      box.get('items', defaultValue: {}) as Map
    );
  }

  Future<void> deleteDraft(String id) async {
    final box = Hive.box(_draftsBox);
    final drafts = Map<String, dynamic>.from(
      box.get('items', defaultValue: {}) as Map
    );
    drafts.remove(id);
    await box.put('items', drafts);
  }

  Future<void> setSetting(String key, dynamic value) async {
    final box = Hive.box(_settingsBox);
    await box.put(key, value);
  }

  dynamic getSetting(String key) {
    final box = Hive.box(_settingsBox);
    return box.get(key);
  }
}

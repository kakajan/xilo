import 'package:hive_flutter/hive_flutter.dart';
import '../../domain/entities/user_profile.dart';

class ProfileLocalDataSource {
  static const _boxName = 'user';

  Future<void> init() async {
    if (!Hive.isBoxOpen(_boxName)) {
      await Hive.openBox(_boxName);
    }
  }

  Future<void> cacheProfile(String username, UserProfile profile) async {
    await init();
    final box = Hive.box(_boxName);
    await box.put('profile:$username', profile.toJson());
  }

  UserProfile? getCachedProfile(String username) {
    if (!Hive.isBoxOpen(_boxName)) return null;
    final box = Hive.box(_boxName);
    final raw = box.get('profile:$username');
    if (raw is! Map) return null;
    return UserProfile.fromJson(Map<String, dynamic>.from(raw));
  }
}

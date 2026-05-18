import 'package:flutter_riverpod/flutter_riverpod.dart';
import '../../../api/api_providers.dart';
import '../../../post/domain/entities/post.dart';

final feedNotifierProvider =
    AsyncNotifierProvider<FeedNotifier, List<Post>>(FeedNotifier.new);

class FeedNotifier extends AsyncNotifier<List<Post>> {
  @override
  Future<List<Post>> build() async {
    final api = ref.read(feedApiProvider);
    final res = await api.getFeed(limit: 20);
    final data = res['data'] as List;
    return data.map((e) => Post.fromJson(e as Map<String, dynamic>)).toList();
  }

  Future<void> refresh() async {
    state = const AsyncLoading();
    state = await AsyncValue.guard(() async {
      final api = ref.read(feedApiProvider);
      final res = await api.getFeed(limit: 20);
      final data = res['data'] as List;
      return data.map((e) => Post.fromJson(e as Map<String, dynamic>)).toList();
    });
  }
}

import 'package:dio/dio.dart';
import '../../../api/api_providers.dart';
import '../../../post/domain/entities/post.dart';
import '../../../../shared/widgets/comment_bubble_card.dart';
import '../../domain/entities/user_profile.dart';

class ProfileRemoteDataSource {
  ProfileRemoteDataSource(this._api);

  final ProfileApi _api;

  Future<UserProfile> fetchProfile(String username) async {
    final data = await _api.getProfile(username);
    _ensureUserPayload(data, username);
    return UserProfile.fromJson(data);
  }

  void _ensureUserPayload(Map<String, dynamic> data, String username) {
    if (data.containsKey('error')) {
      throw DioException(
        requestOptions: RequestOptions(path: '/api/users/$username'),
        message: data['error']?.toString() ?? 'profile unavailable',
      );
    }
    if (!data.containsKey('id') || !data.containsKey('username')) {
      throw DioException(
        requestOptions: RequestOptions(path: '/api/users/$username'),
        message: 'invalid profile response',
      );
    }
  }

  Future<List<Post>> fetchPosts(String username, {String tab = 'posts', String? cursor}) async {
    final res = await _api.getUserPosts(username, tab: tab, cursor: cursor);
    return _parsePosts(res);
  }

  Future<List<ProfileCommentItem>> fetchReplies(String username, {String? cursor}) async {
    final res = await _api.getUserReplies(username, cursor: cursor);
    final list = res['data'] as List<dynamic>? ?? [];
    return list
        .map((e) => ProfileCommentItem.fromJson(e as Map<String, dynamic>))
        .toList();
  }

  Future<List<Post>> fetchLikes(String username, {String? cursor}) async {
    final res = await _api.getUserLikes(username, cursor: cursor);
    return _parsePosts(res);
  }

  Future<bool> follow(String username) async {
    final res = await _api.follow(username);
    return res['following'] as bool? ?? true;
  }

  Future<bool> unfollow(String username) async {
    final res = await _api.unfollow(username);
    return res['following'] as bool? ?? false;
  }

  List<Post> _parsePosts(Map<String, dynamic> res) {
    final list = res['data'] as List<dynamic>? ?? [];
    return list.map((e) => Post.fromJson(e as Map<String, dynamic>)).toList();
  }
}

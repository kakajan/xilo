import 'package:dio/dio.dart';
import '../../../post/domain/entities/post.dart';
import '../../../../shared/widgets/comment_bubble_card.dart';
import '../../domain/entities/user_profile.dart';
import '../../domain/repositories/profile_repository.dart';
import '../datasources/profile_local.dart';
import '../datasources/profile_remote.dart';

class ProfileRepositoryImpl implements ProfileRepository {
  ProfileRepositoryImpl(this._remote, this._local);

  final ProfileRemoteDataSource _remote;
  final ProfileLocalDataSource _local;

  @override
  Future<UserProfile> getProfile(String username) async {
    try {
      final profile = await _remote.fetchProfile(username);
      await _local.cacheProfile(username, profile);
      return profile;
    } on DioException {
      final cached = _local.getCachedProfile(username);
      if (cached != null) return cached;
      rethrow;
    }
  }

  @override
  Future<List<Post>> getPosts(String username, {String tab = 'posts', String? cursor}) {
    return _remote.fetchPosts(username, tab: tab, cursor: cursor);
  }

  @override
  Future<List<ProfileCommentItem>> getReplies(String username, {String? cursor}) {
    return _remote.fetchReplies(username, cursor: cursor);
  }

  @override
  Future<List<Post>> getLikes(String username, {String? cursor}) {
    return _remote.fetchLikes(username, cursor: cursor);
  }

  @override
  Future<bool> toggleFollow(String username, {required bool currentlyFollowing}) {
    if (currentlyFollowing) {
      return _remote.unfollow(username);
    }
    return _remote.follow(username);
  }
}

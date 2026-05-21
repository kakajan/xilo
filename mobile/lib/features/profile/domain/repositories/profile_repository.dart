import '../../../post/domain/entities/post.dart';
import '../../../../shared/widgets/comment_bubble_card.dart';
import '../entities/user_profile.dart';

abstract class ProfileRepository {
  Future<UserProfile> getProfile(String username);
  Future<List<Post>> getPosts(String username, {String tab = 'posts', String? cursor});
  Future<List<ProfileCommentItem>> getReplies(String username, {String? cursor});
  Future<List<Post>> getLikes(String username, {String? cursor});
  Future<bool> toggleFollow(String username, {required bool currentlyFollowing});
}

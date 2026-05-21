import 'package:flutter_riverpod/flutter_riverpod.dart';
import '../../../api/api_providers.dart';
import '../../../post/domain/entities/post.dart';
import '../../../../shared/widgets/comment_bubble_card.dart';
import '../../data/datasources/profile_local.dart';
import '../../data/datasources/profile_remote.dart';
import '../../data/repositories/profile_repo_impl.dart';
import '../../domain/entities/user_profile.dart';
import '../../domain/repositories/profile_repository.dart';

final profileRepositoryProvider = Provider<ProfileRepository>((ref) {
  return ProfileRepositoryImpl(
    ProfileRemoteDataSource(ref.read(profileApiProvider)),
    ProfileLocalDataSource(),
  );
});

final profileProvider = FutureProvider.family<UserProfile, String>((ref, username) async {
  return ref.read(profileRepositoryProvider).getProfile(username);
});

enum ProfileTab { posts, replies, media, likes }

final profileTabProvider =
    FutureProvider.family<List<dynamic>, ({String username, ProfileTab tab})>((ref, args) async {
  final repo = ref.read(profileRepositoryProvider);
  switch (args.tab) {
    case ProfileTab.posts:
      return repo.getPosts(args.username, tab: 'posts');
    case ProfileTab.media:
      return repo.getPosts(args.username, tab: 'media');
    case ProfileTab.replies:
      return repo.getReplies(args.username);
    case ProfileTab.likes:
      return repo.getLikes(args.username);
  }
});

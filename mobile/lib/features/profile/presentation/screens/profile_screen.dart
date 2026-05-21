import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';
import '../../../../core/theme/app_spacing.dart';
import '../../../../core/theme/xilo_theme_extension.dart';
import '../../../../l10n/app_localizations.dart';
import '../../../../shared/widgets/comment_bubble_card.dart';
import '../../../../shared/widgets/shimmer_list.dart';
import '../../../../shared/widgets/states.dart';
import '../../../../shared/widgets/xilo_post_card.dart';
import '../../../post/domain/entities/post.dart';
import '../../domain/entities/user_profile.dart';
import '../providers/profile_provider.dart';
import '../widgets/profile_action_bar.dart';
import '../widgets/profile_header.dart';
import '../widgets/profile_stats_row.dart';
import '../widgets/profile_tab_bar.dart';

class ProfileScreen extends ConsumerStatefulWidget {
  const ProfileScreen({super.key, required this.username});

  final String username;

  @override
  ConsumerState<ProfileScreen> createState() => _ProfileScreenState();
}

class _ProfileScreenState extends ConsumerState<ProfileScreen>
    with SingleTickerProviderStateMixin {
  late TabController _tabController;
  bool _followLoading = false;

  static const _tabs = ProfileTab.values;

  @override
  void initState() {
    super.initState();
    _tabController = TabController(length: _tabs.length, vsync: this)
      ..addListener(() {
        if (!_tabController.indexIsChanging) setState(() {});
      });
  }

  @override
  void dispose() {
    _tabController.dispose();
    super.dispose();
  }

  ProfileTab get _currentTab => _tabs[_tabController.index];

  @override
  Widget build(BuildContext context) {
    final l10n = AppLocalizations.of(context)!;
    final colors = context.xiloColors;
    final profileAsync = ref.watch(profileProvider(widget.username));
    final tabAsync = ref.watch(
      profileTabProvider((username: widget.username, tab: _currentTab)),
    );

    return Scaffold(
      backgroundColor: colors.background,
      body: profileAsync.when(
        loading: () => const Center(child: CircularProgressIndicator()),
        error: (e, _) => ErrorStateWidget(
          message: l10n.failed_to_load('profile'),
          retryLabel: l10n.retry,
          onRetry: () => ref.invalidate(profileProvider(widget.username)),
        ),
        data: (profile) => NestedScrollView(
          headerSliverBuilder: (context, innerBoxIsScrolled) => [
            SliverAppBar(
              pinned: true,
              backgroundColor: colors.background,
              surfaceTintColor: Colors.transparent,
              leading: IconButton(
                icon: const Icon(Icons.arrow_back),
                onPressed: () => context.pop(),
              ),
              title: Text(
                l10n.xilo,
                style: TextStyle(
                  color: colors.primary,
                  fontWeight: FontWeight.w700,
                ),
              ),
              centerTitle: true,
              actions: [
                IconButton(
                  icon: const Icon(Icons.more_horiz),
                  onPressed: () {},
                ),
              ],
            ),
            SliverToBoxAdapter(
              child: Column(
                children: [
                  ProfileHeader(profile: profile),
                  ProfileStatsRow(
                    stats: profile.stats,
                    postsLabel: l10n.tab_posts,
                    followersLabel: l10n.tab_followers,
                    followingLabel: l10n.tab_following,
                  ),
                  ProfileActionBar(
                    isFollowing: profile.isFollowing,
                    isOwnProfile: _isOwnProfile(profile),
                    onFollowToggle: () => _toggleFollow(profile),
                    followLabel: l10n.follow,
                    unfollowLabel: l10n.unfollow,
                    messageLabel: l10n.message,
                    shareLabel: l10n.share_profile,
                    shareUrl: 'https://xilo.app/@${profile.username}',
                    messageComingSoon: l10n.message_coming_soon,
                  ),
                  const SizedBox(height: AppSpacing.s4),
                ],
              ),
            ),
            SliverToBoxAdapter(
              child: ProfileTabBar(
                controller: _tabController,
                onTap: (_) => setState(() {}),
                tabs: [
                  Tab(text: l10n.tab_posts),
                  Tab(text: l10n.tab_replies),
                  Tab(text: l10n.tab_media),
                  Tab(text: l10n.tab_likes),
                ],
              ),
            ),
          ],
          body: tabAsync.when(
            loading: () => const ShimmerList(itemCount: 4),
            error: (e, _) => ErrorStateWidget(
              message: l10n.failed_to_load('content'),
              retryLabel: l10n.retry,
              onRetry: () => ref.invalidate(
                profileTabProvider((username: widget.username, tab: _currentTab)),
              ),
            ),
            data: (items) {
              if (items.isEmpty) {
                return EmptyStateWidget(
                  title: l10n.no_posts_yet,
                  subtitle: l10n.check_back_later,
                  icon: Icons.article_outlined,
                );
              }
              return RefreshIndicator(
                onRefresh: () async {
                  ref.invalidate(
                    profileTabProvider((username: widget.username, tab: _currentTab)),
                  );
                },
                child: ListView.builder(
                  padding: EdgeInsets.zero,
                  itemCount: items.length,
                  itemBuilder: (context, index) {
                    final item = items[index];
                    if (item is Post) {
                      return XiloPostCard(post: item, compact: true);
                    }
                    if (item is ProfileCommentItem) {
                      return CommentBubbleCard(comment: item);
                    }
                    return const SizedBox.shrink();
                  },
                ),
              );
            },
          ),
        ),
      ),
    );
  }

  bool _isOwnProfile(UserProfile profile) {
    // Compare with cached auth user if available
    return false;
  }

  Future<void> _toggleFollow(UserProfile profile) async {
    if (_followLoading) return;
    setState(() => _followLoading = true);
    try {
      final repo = ref.read(profileRepositoryProvider);
      await repo.toggleFollow(
        widget.username,
        currentlyFollowing: profile.isFollowing,
      );
      ref.invalidate(profileProvider(widget.username));
    } catch (_) {
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(
            content: Text(AppLocalizations.of(context)!.failed_to_load('follow')),
          ),
        );
      }
    } finally {
      if (mounted) setState(() => _followLoading = false);
    }
  }
}


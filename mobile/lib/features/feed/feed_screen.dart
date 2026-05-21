import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import '../../../core/theme/app_spacing.dart';
import '../../../core/theme/xilo_theme_extension.dart';
import '../../../shared/widgets/shimmer_list.dart';
import '../../../shared/widgets/states.dart';
import '../../../shared/widgets/xilo_post_card.dart';
import '../../../l10n/app_localizations.dart';
import 'presentation/providers/feed_provider.dart';

class FeedScreen extends ConsumerWidget {
  const FeedScreen({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final feedState = ref.watch(feedNotifierProvider);
    final l10n = AppLocalizations.of(context)!;
    final colors = context.xiloColors;

    return Scaffold(
      backgroundColor: colors.background,
      appBar: AppBar(
        title: Text(l10n.xilo),
        centerTitle: false,
      ),
      body: feedState.when(
        loading: () => const ShimmerList(),
        error: (error, stack) => ErrorStateWidget(
          message: l10n.failed_to_load('posts'),
          retryLabel: l10n.retry,
          onRetry: () => ref.refresh(feedNotifierProvider),
        ),
        data: (posts) {
          if (posts.isEmpty) {
            return EmptyStateWidget(
              title: l10n.no_posts_yet,
              subtitle: l10n.check_back_later,
              icon: Icons.article_outlined,
            );
          }

          return PullToRefresh(
            onRefresh: () => ref.read(feedNotifierProvider.notifier).refresh(),
            child: ListView.builder(
              padding: EdgeInsets.zero,
              itemCount: posts.length,
              itemBuilder: (context, index) => XiloPostCard(
                post: posts[index],
                compact: true,
              ),
            ),
          );
        },
      ),
    );
  }
}

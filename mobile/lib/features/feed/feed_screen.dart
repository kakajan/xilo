import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';
import 'package:cached_network_image/cached_network_image.dart';
import '../../../shared/widgets/shimmer_list.dart';
import '../../../shared/widgets/states.dart';
import '../../../shared/widgets/language_badge.dart';
import '../../../l10n/app_localizations.dart';
import 'presentation/providers/feed_provider.dart';

class FeedScreen extends ConsumerWidget {
  const FeedScreen({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final feedState = ref.watch(feedNotifierProvider);
    final l10n = AppLocalizations.of(context)!;

    return Scaffold(
      appBar: AppBar(title: Text(l10n.xilo), centerTitle: false),
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
              padding: const EdgeInsets.all(16),
              itemCount: posts.length,
              itemBuilder: (context, index) {
                final post = posts[index];
                final author = post.author;

                return Card(
                  margin: const EdgeInsets.only(bottom: 16),
                  clipBehavior: Clip.antiAlias,
                  shape: RoundedRectangleBorder(
                    borderRadius: BorderRadius.circular(12),
                  ),
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      Padding(
                        padding: const EdgeInsets.fromLTRB(16, 16, 16, 0),
                        child: Row(
                          children: [
                            if (author != null)
                              InkWell(
                                borderRadius: BorderRadius.circular(14),
                                onTap: () => context.push('/profile/${author.username}'),
                                child: author.avatarUrl != null
                                    ? CachedNetworkImage(
                                        imageUrl: author.avatarUrl!,
                                        imageBuilder: (context, imageProvider) => CircleAvatar(
                                          radius: 14,
                                          backgroundImage: imageProvider,
                                        ),
                                        placeholder: (context, url) => const CircleAvatar(radius: 14),
                                        errorWidget: (context, url, error) => const CircleAvatar(radius: 14),
                                      )
                                    : const CircleAvatar(radius: 14),
                              )
                            else
                              const CircleAvatar(radius: 14),
                            const SizedBox(width: 8),
                            InkWell(
                              onTap: author != null
                                  ? () => context.push('/profile/${author.username}')
                                  : null,
                              child: Column(
                                crossAxisAlignment: CrossAxisAlignment.start,
                                children: [
                                  Text(
                                    author?.displayName ?? l10n.unknown,
                                    style: Theme.of(context).textTheme.labelMedium,
                                  ),
                                  Text(
                                    _formatDate(post.publishedAt ?? post.createdAt, l10n),
                                    style: Theme.of(context).textTheme.bodySmall,
                                  ),
                                ],
                              ),
                            ),
                            const Spacer(),
                            if (post.language != null) ...[
                              LanguageBadge(languageCode: post.language!),
                              const SizedBox(width: 8),
                            ],
                            if (post.isPremium)
                              Container(
                                padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 4),
                                decoration: BoxDecoration(
                                  color: Theme.of(context).colorScheme.primary,
                                  borderRadius: BorderRadius.circular(12),
                                ),
                                child: Text(
                                  l10n.premium,
                                  style: TextStyle(
                                    color: Theme.of(context).colorScheme.onPrimary,
                                    fontSize: 12,
                                    fontWeight: FontWeight.bold,
                                  ),
                                ),
                              ),
                          ],
                        ),
                      ),
                      InkWell(
                        onTap: () => context.push('/post/${post.slug}'),
                        child: Padding(
                          padding: const EdgeInsets.all(16),
                          child: Column(
                            crossAxisAlignment: CrossAxisAlignment.start,
                            children: [
                              Text(
                                post.title,
                                style: Theme.of(context).textTheme.titleMedium?.copyWith(
                                      fontWeight: FontWeight.bold,
                                    ),
                              ),
                              const SizedBox(height: 8),
                              Text(
                                post.excerpt,
                                style: Theme.of(context).textTheme.bodyMedium,
                                maxLines: 2,
                                overflow: TextOverflow.ellipsis,
                              ),
                              if (post.tags.isNotEmpty) ...[
                                const SizedBox(height: 8),
                                Wrap(
                                  spacing: 4,
                                  runSpacing: 4,
                                  children: post.tags
                                      .map((tag) => InkWell(
                                            onTap: () {},
                                            child: Text(
                                              '#$tag',
                                              style: TextStyle(
                                                fontSize: 13,
                                                fontWeight: FontWeight.w500,
                                                color: Theme.of(context).colorScheme.primary,
                                              ),
                                            ),
                                          ))
                                      .toList(),
                                ),
                              ],
                              if (post.readingTime != null) ...[
                                const SizedBox(height: 8),
                                Text(
                                  l10n.min_read(post.readingTime!),
                                  style: Theme.of(context).textTheme.bodySmall?.copyWith(
                                        color: Theme.of(context).colorScheme.outline,
                                      ),
                                ),
                              ],
                            ],
                          ),
                        ),
                      ),
                    ],
                  ),
                );
              },
            ),
          );
        },
      ),
    );
  }

  String _formatDate(DateTime date, AppLocalizations l10n) {
    final now = DateTime.now();
    final difference = now.difference(date);

    if (difference.inMinutes < 1) return l10n.just_now;
    if (difference.inMinutes < 60) return l10n.minutes_ago(difference.inMinutes);
    if (difference.inHours < 24) return l10n.hours_ago(difference.inHours);
    if (difference.inDays < 7) return l10n.days_ago(difference.inDays);
    return '${date.day}/${date.month}/${date.year}';
  }
}

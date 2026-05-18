import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';
import 'package:cached_network_image/cached_network_image.dart';
import '../../../shared/widgets/language_badge.dart';
import '../../../l10n/app_localizations.dart';
import '../api/api_providers.dart';
import 'domain/entities/post.dart';

class PostDetailScreen extends ConsumerWidget {
  final String slug;
  const PostDetailScreen({super.key, required this.slug});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final postState = ref.watch(postProvider(slug));
    final l10n = AppLocalizations.of(context)!;

    return Scaffold(
      appBar: AppBar(),
      body: postState.when(
        loading: () => const Center(child: CircularProgressIndicator()),
        error: (error, stack) => Center(
          child: Column(
            mainAxisSize: MainAxisSize.min,
            children: [
              const Icon(Icons.error_outline, size: 48),
              const SizedBox(height: 16),
              Text(l10n.failed_to_load_post),
              const SizedBox(height: 16),
              FilledButton(
                onPressed: () => ref.refresh(postProvider(slug)),
                child: Text(l10n.retry),
              ),
            ],
          ),
        ),
        data: (post) => _PostContent(post: post),
      ),
    );
  }
}

class _PostContent extends StatelessWidget {
  final Post post;
  const _PostContent({required this.post});

  @override
  Widget build(BuildContext context) {
    final author = post.author;
    final l10n = AppLocalizations.of(context)!;

    return SingleChildScrollView(
      padding: const EdgeInsets.all(16),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Text(
            post.title,
            style: Theme.of(context).textTheme.headlineMedium?.copyWith(
                  fontWeight: FontWeight.bold,
                ),
          ),
          if (post.tags.isNotEmpty) ...[
            const SizedBox(height: 12),
            Wrap(
              spacing: 8,
              runSpacing: 4,
              children: post.tags
                  .map((tag) => Text(
                        '#$tag',
                        style: TextStyle(
                          fontSize: 13,
                          fontWeight: FontWeight.w500,
                          color: Theme.of(context).colorScheme.primary,
                        ),
                      ))
                  .toList(),
            ),
          ],
          const SizedBox(height: 16),
          Row(
            children: [
              if (post.language != null) ...[
                LanguageBadge(languageCode: post.language!),
                const SizedBox(width: 8),
              ],
              if (author != null) ...[
                InkWell(
                  borderRadius: BorderRadius.circular(16),
                  onTap: () => context.push('/profile/${author.username}'),
                  child: author.avatarUrl != null && author.avatarUrl!.isNotEmpty
                      ? CachedNetworkImage(
                          imageUrl: author.avatarUrl!,
                          imageBuilder: (context, imageProvider) => CircleAvatar(
                            radius: 16,
                            backgroundImage: imageProvider,
                          ),
                          placeholder: (context, url) => const CircleAvatar(radius: 16),
                          errorWidget: (context, url, error) => const CircleAvatar(radius: 16),
                        )
                      : const CircleAvatar(radius: 16),
                ),
                const SizedBox(width: 8),
                InkWell(
                  onTap: () => context.push('/profile/${author.username}'),
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      Text(
                        author.displayName,
                        style: Theme.of(context).textTheme.labelLarge,
                      ),
                      Text(
                        _formatDate(post.publishedAt ?? post.createdAt, l10n) +
                            (post.readingTime != null ? ' · ${l10n.min_read(post.readingTime!)}' : ''),
                        style: Theme.of(context).textTheme.bodySmall,
                      ),
                    ],
                  ),
                ),
              ] else ...[
                const CircleAvatar(radius: 16),
                const SizedBox(width: 8),
                Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Text(l10n.unknown, style: Theme.of(context).textTheme.labelLarge),
                    Text(
                      _formatDate(post.publishedAt ?? post.createdAt, l10n),
                      style: Theme.of(context).textTheme.bodySmall,
                    ),
                  ],
                ),
              ],
            ],
          ),
          const SizedBox(height: 24),
          if (post.coverImageUrl != null && post.coverImageUrl!.isNotEmpty) ...[
            ClipRRect(
              borderRadius: BorderRadius.circular(12),
              child: CachedNetworkImage(
                imageUrl: post.coverImageUrl!,
                width: double.infinity,
                fit: BoxFit.cover,
                placeholder: (context, url) => Container(
                  height: 200,
                  color: Theme.of(context).colorScheme.surfaceContainerHighest,
                ),
                errorWidget: (context, url, error) => Container(
                  height: 200,
                  color: Theme.of(context).colorScheme.surfaceContainerHighest,
                  child: const Icon(Icons.image_not_supported),
                ),
              ),
            ),
            const SizedBox(height: 24),
          ],
          Text(
            _formatContent(post.contentMd.isNotEmpty ? post.contentMd : post.excerpt),
            style: Theme.of(context).textTheme.bodyLarge?.copyWith(height: 1.7),
          ),
          const SizedBox(height: 32),
          const Divider(),
          const SizedBox(height: 16),
          Text(
            l10n.comments,
            style: Theme.of(context).textTheme.titleMedium?.copyWith(
                  fontWeight: FontWeight.bold,
                ),
          ),
          const SizedBox(height: 12),
          Center(
            child: Text(l10n.no_comments_yet),
          ),
        ],
      ),
    );
  }

  String _formatContent(String content) {
    return content
        .replaceAll(r'\n', '\n')
        .replaceAll('# ', '')
        .replaceAll('## ', '')
        .replaceAll('### ', '');
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

final postProvider =
    FutureProvider.family<Post, String>((ref, slug) async {
  final api = ref.read(feedApiProvider);
  final res = await api.getPost(slug);
  return Post.fromJson(res);
});

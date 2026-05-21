import 'package:cached_network_image/cached_network_image.dart';
import 'package:flutter/material.dart';
import 'package:go_router/go_router.dart';
import '../../core/theme/app_radius.dart';
import '../../core/theme/app_spacing.dart';
import '../../core/theme/xilo_theme_extension.dart';
import '../../features/post/domain/entities/post.dart';
import 'xilo_avatar.dart';

class XiloPostCard extends StatelessWidget {
  const XiloPostCard({
    super.key,
    required this.post,
    this.onLike,
    this.onShare,
    this.compact = false,
  });

  final Post post;
  final VoidCallback? onLike;
  final VoidCallback? onShare;
  final bool compact;

  @override
  Widget build(BuildContext context) {
    final colors = context.xiloColors;
    final author = post.author;
    final timeLabel =
        post.publishedAt != null ? _relativeTime(post.publishedAt!) : '';

    return InkWell(
      onTap: () => context.push('/post/${post.slug}'),
      child: Container(
        padding: const EdgeInsets.all(AppSpacing.s4),
        decoration: BoxDecoration(
          color: colors.background,
          border: Border(bottom: BorderSide(color: colors.border)),
        ),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Row(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                if (author != null)
                  XiloAvatar(
                    imageUrl: author.avatarUrl,
                    radius: 24,
                    isVerified: author.isVerified,
                    onTap: () => context.push('/profile/${author.username}'),
                    initials: author.displayName.isNotEmpty
                        ? author.displayName[0].toUpperCase()
                        : author.username[0].toUpperCase(),
                  )
                else
                  const XiloAvatar(radius: 24),
                const SizedBox(width: AppSpacing.s3),
                Expanded(
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      Row(
                        children: [
                          Expanded(
                            child: InkWell(
                              onTap: author != null
                                  ? () => context.push('/profile/${author.username}')
                                  : null,
                              child: RichText(
                                text: TextSpan(
                                  style: Theme.of(context).textTheme.bodyMedium,
                                  children: [
                                    TextSpan(
                                      text: author?.displayName ?? 'Unknown',
                                      style: TextStyle(
                                        fontWeight: FontWeight.w700,
                                        color: colors.textPrimary,
                                      ),
                                    ),
                                    if (author != null)
                                      TextSpan(
                                        text: ' @${author.username}',
                                        style: TextStyle(color: colors.textSecondary),
                                      ),
                                    if (timeLabel.isNotEmpty)
                                      TextSpan(
                                        text: ' · $timeLabel',
                                        style: TextStyle(color: colors.textSecondary),
                                      ),
                                  ],
                                ),
                              ),
                            ),
                          ),
                          Icon(Icons.more_horiz, size: 20, color: colors.textSecondary),
                        ],
                      ),
                      const SizedBox(height: AppSpacing.s2),
                      Text(
                        compact && post.excerpt.isNotEmpty ? post.excerpt : post.title,
                        style: Theme.of(context).textTheme.bodyMedium?.copyWith(
                              color: colors.textPrimary,
                            ),
                      ),
                    ],
                  ),
                ),
              ],
            ),
            if (post.coverImageUrl != null && post.coverImageUrl!.isNotEmpty) ...[
              const SizedBox(height: AppSpacing.s3),
              ClipRRect(
                borderRadius: AppRadius.lgBorder,
                child: CachedNetworkImage(
                  imageUrl: post.coverImageUrl!,
                  width: double.infinity,
                  height: 200,
                  fit: BoxFit.cover,
                ),
              ),
            ],
            const SizedBox(height: AppSpacing.s3),
            _ActionRow(
              commentCount: post.commentCount,
              likeCount: post.likeCount,
              liked: post.viewerLiked,
              onLike: onLike,
              onShare: onShare,
            ),
          ],
        ),
      ),
    );
  }

  static String _relativeTime(DateTime dt) {
    final diff = DateTime.now().difference(dt);
    if (diff.inDays > 0) return '${diff.inDays}d';
    if (diff.inHours > 0) return '${diff.inHours}h';
    if (diff.inMinutes > 0) return '${diff.inMinutes}m';
    return 'now';
  }
}

class _ActionRow extends StatelessWidget {
  const _ActionRow({
    required this.commentCount,
    required this.likeCount,
    required this.liked,
    this.onLike,
    this.onShare,
  });

  final int commentCount;
  final int likeCount;
  final bool liked;
  final VoidCallback? onLike;
  final VoidCallback? onShare;

  @override
  Widget build(BuildContext context) {
    final colors = context.xiloColors;

    return Row(
      mainAxisAlignment: MainAxisAlignment.spaceBetween,
      children: [
        _ActionIcon(
          icon: Icons.chat_bubble_outline,
          count: commentCount,
          color: colors.textSecondary,
        ),
        _ActionIcon(
          icon: Icons.favorite_border,
          count: likeCount,
          color: liked ? colors.likeActive : colors.textSecondary,
          activeColor: colors.likeActive,
          onTap: onLike,
        ),
        _ActionIcon(
          icon: Icons.ios_share_outlined,
          color: colors.textSecondary,
          onTap: onShare,
        ),
        Icon(Icons.bar_chart_outlined, size: 20, color: colors.textSecondary),
      ],
    );
  }
}

class _ActionIcon extends StatelessWidget {
  const _ActionIcon({
    required this.icon,
    this.count,
    this.color,
    this.activeColor,
    this.onTap,
  });

  final IconData icon;
  final int? count;
  final Color? color;
  final Color? activeColor;
  final VoidCallback? onTap;

  @override
  Widget build(BuildContext context) {
    return InkWell(
      onTap: onTap,
      borderRadius: AppRadius.fullBorder,
      child: Padding(
        padding: const EdgeInsets.symmetric(horizontal: AppSpacing.s2, vertical: AppSpacing.s1),
        child: Row(
          children: [
            Icon(icon, size: 20, color: activeColor ?? color),
            if (count != null && count! > 0) ...[
              const SizedBox(width: AppSpacing.s1),
              Text(
                _formatCount(count!),
                style: Theme.of(context).textTheme.bodySmall?.copyWith(color: color),
              ),
            ],
          ],
        ),
      ),
    );
  }

  String _formatCount(int n) {
    if (n >= 1000000) return '${(n / 1000000).toStringAsFixed(1)}M';
    if (n >= 1000) return '${(n / 1000).toStringAsFixed(1)}K';
    return n.toString();
  }
}

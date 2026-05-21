import 'package:flutter/material.dart';
import 'package:go_router/go_router.dart';
import '../../core/theme/app_radius.dart';
import '../../core/theme/app_spacing.dart';
import '../../core/theme/xilo_theme_extension.dart';
import 'xilo_avatar.dart';

class ProfileCommentItem {
  const ProfileCommentItem({
    required this.id,
    required this.text,
    required this.createdAt,
    required this.authorUsername,
    required this.authorDisplayName,
    this.authorAvatarUrl,
    this.isVerified = false,
    this.reactions = const {},
    this.postId,
    this.postTitle,
    this.postSlug,
  });

  final String id;
  final String text;
  final DateTime createdAt;
  final String authorUsername;
  final String authorDisplayName;
  final String? authorAvatarUrl;
  final bool isVerified;
  final Map<String, int> reactions;
  final String? postId;
  final String? postTitle;
  final String? postSlug;

  factory ProfileCommentItem.fromJson(Map<String, dynamic> json) {
    final author = json['author'] as Map<String, dynamic>?;
    final post = json['post'] as Map<String, dynamic>?;
    final reactionsRaw = json['reactions'] as Map<String, dynamic>?;

    return ProfileCommentItem(
      id: json['id'] as String,
      text: json['text'] as String? ?? json['content'] as String? ?? '',
      createdAt: DateTime.parse(json['created_at'] as String),
      authorUsername: author?['username'] as String? ?? json['username'] as String? ?? '',
      authorDisplayName:
          author?['display_name'] as String? ?? json['display_name'] as String? ?? '',
      authorAvatarUrl: author?['avatar_url'] as String?,
      isVerified: author?['is_verified'] as bool? ?? false,
      reactions: reactionsRaw?.map((k, v) => MapEntry(k.toString(), (v as num).toInt())) ??
          const {},
      postId: post?['id'] as String? ?? json['post_id'] as String?,
      postTitle: post?['title'] as String?,
      postSlug: post?['slug'] as String?,
    );
  }
}

class CommentBubbleCard extends StatelessWidget {
  const CommentBubbleCard({
    super.key,
    required this.comment,
    this.showThreadLine = false,
    this.onTap,
  });

  final ProfileCommentItem comment;
  final bool showThreadLine;
  final VoidCallback? onTap;

  @override
  Widget build(BuildContext context) {
    final colors = context.xiloColors;
    final time = TimeOfDay.fromDateTime(comment.createdAt);
    final timeLabel =
        '${time.hourOfPeriod}:${time.minute.toString().padLeft(2, '0')} ${time.period == DayPeriod.am ? 'AM' : 'PM'}';

    return InkWell(
      onTap: onTap ??
          (comment.postSlug != null
              ? () => context.push('/post/${comment.postSlug}')
              : null),
      child: Container(
        padding: const EdgeInsets.symmetric(
          horizontal: AppSpacing.s4,
          vertical: AppSpacing.s3,
        ),
        decoration: BoxDecoration(
          border: Border(bottom: BorderSide(color: colors.border)),
        ),
        child: Row(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            SizedBox(
              width: 40,
              child: Column(
                children: [
                  XiloAvatar(
                    imageUrl: comment.authorAvatarUrl,
                    radius: 16,
                    isVerified: comment.isVerified,
                  ),
                  if (showThreadLine)
                    Expanded(
                      child: Container(
                        width: 2,
                        margin: const EdgeInsets.only(top: AppSpacing.s2),
                        color: colors.border,
                      ),
                    ),
                ],
              ),
            ),
            const SizedBox(width: AppSpacing.s3),
            Expanded(
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Row(
                    children: [
                      Expanded(
                        child: Text(
                          comment.authorDisplayName,
                          style: Theme.of(context).textTheme.titleSmall?.copyWith(
                                fontWeight: FontWeight.w700,
                                color: colors.textPrimary,
                              ),
                        ),
                      ),
                      Text(
                        '@${comment.authorUsername}',
                        style: Theme.of(context).textTheme.bodySmall,
                      ),
                    ],
                  ),
                  const SizedBox(height: AppSpacing.s2),
                  Container(
                    width: double.infinity,
                    padding: const EdgeInsets.all(AppSpacing.s3),
                    decoration: BoxDecoration(
                      color: colors.bubbleOthers,
                      borderRadius: AppRadius.xlBorder,
                      border: Border.all(color: colors.bubbleBorder),
                    ),
                    child: Text(
                      comment.text,
                      style: Theme.of(context).textTheme.bodyMedium,
                    ),
                  ),
                  if (comment.postTitle != null) ...[
                    const SizedBox(height: AppSpacing.s2),
                    Text(
                      '↳ ${comment.postTitle}',
                      style: Theme.of(context).textTheme.bodySmall?.copyWith(
                            color: colors.textLink,
                          ),
                      maxLines: 1,
                      overflow: TextOverflow.ellipsis,
                    ),
                  ],
                  const SizedBox(height: AppSpacing.s2),
                  Row(
                    children: [
                      ...comment.reactions.entries.map(
                        (e) => Padding(
                          padding: const EdgeInsets.only(right: AppSpacing.s2),
                          child: Container(
                            padding: const EdgeInsets.symmetric(
                              horizontal: AppSpacing.s2,
                              vertical: AppSpacing.s1,
                            ),
                            decoration: BoxDecoration(
                              color: colors.backgroundSecondary,
                              borderRadius: AppRadius.fullBorder,
                            ),
                            child: Text('${e.key} ${e.value}', style: const TextStyle(fontSize: 13)),
                          ),
                        ),
                      ),
                      const Spacer(),
                      Text(timeLabel, style: Theme.of(context).textTheme.labelSmall),
                    ],
                  ),
                ],
              ),
            ),
          ],
        ),
      ),
    );
  }
}

import 'package:flutter/material.dart';
import '../../../../core/theme/app_spacing.dart';
import '../../../../core/theme/xilo_theme_extension.dart';
import '../../domain/entities/user_profile.dart';

class ProfileStatsRow extends StatelessWidget {
  const ProfileStatsRow({
    super.key,
    required this.stats,
    required this.postsLabel,
    required this.followersLabel,
    required this.followingLabel,
  });

  final ProfileStats stats;
  final String postsLabel;
  final String followersLabel;
  final String followingLabel;

  @override
  Widget build(BuildContext context) {
    final dividerColor = context.xiloColors.borderStrong;

    return Padding(
      padding: const EdgeInsets.symmetric(vertical: AppSpacing.s4),
      child: IntrinsicHeight(
        child: Row(
          children: [
            Expanded(
              child: _StatItem(count: stats.posts, label: postsLabel),
            ),
            VerticalDivider(
              width: 1,
              thickness: 1,
              color: dividerColor,
              indent: AppSpacing.s2,
              endIndent: AppSpacing.s2,
            ),
            Expanded(
              child: _StatItem(count: stats.followers, label: followersLabel),
            ),
            VerticalDivider(
              width: 1,
              thickness: 1,
              color: dividerColor,
              indent: AppSpacing.s2,
              endIndent: AppSpacing.s2,
            ),
            Expanded(
              child: _StatItem(count: stats.following, label: followingLabel),
            ),
          ],
        ),
      ),
    );
  }
}

class _StatItem extends StatelessWidget {
  const _StatItem({required this.count, required this.label});

  final int count;
  final String label;

  @override
  Widget build(BuildContext context) {
    final colors = context.xiloColors;
    return Column(
      mainAxisSize: MainAxisSize.min,
      children: [
        Text(
          _formatCount(count),
          style: Theme.of(context).textTheme.titleMedium?.copyWith(
                fontWeight: FontWeight.w700,
                color: colors.textPrimary,
              ),
        ),
        const SizedBox(height: AppSpacing.s1),
        Text(
          label,
          style: Theme.of(context).textTheme.bodySmall?.copyWith(
                color: colors.textSecondary,
              ),
        ),
      ],
    );
  }

  String _formatCount(int n) {
    if (n >= 1000000) {
      final m = n / 1000000;
      return m == m.roundToDouble() ? '${m.round()}M' : '${m.toStringAsFixed(1)}M';
    }
    if (n >= 10000) {
      final k = n / 1000;
      return k == k.roundToDouble() ? '${k.round()}K' : '${k.toStringAsFixed(1)}K';
    }
    return _withCommas(n);
  }

  String _withCommas(int n) {
    final s = n.toString();
    final buf = StringBuffer();
    for (var i = 0; i < s.length; i++) {
      if (i > 0 && (s.length - i) % 3 == 0) {
        buf.write(',');
      }
      buf.write(s[i]);
    }
    return buf.toString();
  }
}

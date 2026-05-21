import 'package:flutter/material.dart';
import 'package:share_plus/share_plus.dart';
import '../../../../core/theme/app_radius.dart';
import '../../../../core/theme/app_spacing.dart';
import '../../../../core/theme/xilo_theme_extension.dart';

/// Profile header actions — matches ui mockup: equal-width pill buttons.
class ProfileActionBar extends StatelessWidget {
  const ProfileActionBar({
    super.key,
    required this.isFollowing,
    required this.isOwnProfile,
    required this.onFollowToggle,
    required this.followLabel,
    required this.unfollowLabel,
    required this.messageLabel,
    required this.shareLabel,
    required this.shareUrl,
    required this.messageComingSoon,
  });

  final bool isFollowing;
  final bool isOwnProfile;
  final VoidCallback onFollowToggle;
  final String followLabel;
  final String unfollowLabel;
  final String messageLabel;
  final String shareLabel;
  final String shareUrl;
  final String messageComingSoon;

  static const _buttonHeight = 40.0;

  @override
  Widget build(BuildContext context) {
    if (isOwnProfile) {
      return const SizedBox.shrink();
    }

    return Padding(
      padding: const EdgeInsets.symmetric(horizontal: AppSpacing.s4),
      child: Row(
        children: [
          Expanded(
            child: _ProfileActionButton(
              label: isFollowing ? unfollowLabel : followLabel,
              icon: isFollowing ? Icons.person_remove_outlined : Icons.person_add_outlined,
              primary: !isFollowing,
              onPressed: onFollowToggle,
            ),
          ),
          const SizedBox(width: AppSpacing.s2),
          Expanded(
            child: _ProfileActionButton(
              label: messageLabel,
              icon: Icons.chat_bubble_outline,
              onPressed: () {
                ScaffoldMessenger.of(context).showSnackBar(
                  SnackBar(content: Text(messageComingSoon)),
                );
              },
            ),
          ),
          const SizedBox(width: AppSpacing.s2),
          Expanded(
            child: _ProfileActionButton(
              label: shareLabel,
              icon: Icons.ios_share_outlined,
              onPressed: () => Share.share(shareUrl, subject: shareLabel),
            ),
          ),
        ],
      ),
    );
  }
}

class _ProfileActionButton extends StatelessWidget {
  const _ProfileActionButton({
    required this.label,
    required this.icon,
    required this.onPressed,
    this.primary = false,
  });

  final String label;
  final IconData icon;
  final VoidCallback onPressed;
  final bool primary;

  @override
  Widget build(BuildContext context) {
    final colors = context.xiloColors;
    final fg = primary ? Colors.white : colors.textPrimary;
    final bg = primary ? colors.primary : colors.background;
    final border = primary ? colors.primary : colors.borderStrong;

    return Material(
      color: bg,
      elevation: 0,
      shape: RoundedRectangleBorder(
        borderRadius: AppRadius.lgBorder,
        side: BorderSide(color: border, width: 1),
      ),
      child: InkWell(
        onTap: onPressed,
        borderRadius: AppRadius.lgBorder,
        child: SizedBox(
          height: ProfileActionBar._buttonHeight,
          child: Center(
            child: Padding(
              padding: const EdgeInsets.symmetric(horizontal: AppSpacing.s2),
              child: FittedBox(
                fit: BoxFit.scaleDown,
                child: Row(
                  mainAxisSize: MainAxisSize.min,
                  children: [
                    Icon(icon, size: 18, color: fg),
                    const SizedBox(width: AppSpacing.s1),
                    Text(
                      label,
                      maxLines: 1,
                      style: TextStyle(
                        color: fg,
                        fontSize: 14,
                        fontWeight: FontWeight.w600,
                      ),
                    ),
                  ],
                ),
              ),
            ),
          ),
        ),
      ),
    );
  }
}

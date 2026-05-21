import 'package:flutter/material.dart';
import 'package:share_plus/share_plus.dart';
import '../../../../core/theme/app_spacing.dart';
import '../../../../shared/widgets/xilo_button.dart';

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
            child: XiloButton(
              label: isFollowing ? unfollowLabel : followLabel,
              icon: isFollowing ? Icons.person_remove_outlined : Icons.person_add_outlined,
              onPressed: onFollowToggle,
            ),
          ),
          const SizedBox(width: AppSpacing.s2),
          Expanded(
            child: XiloButton(
              label: messageLabel,
              icon: Icons.chat_bubble_outline,
              variant: XiloButtonVariant.secondary,
              onPressed: () {
                ScaffoldMessenger.of(context).showSnackBar(
                  SnackBar(content: Text(messageComingSoon)),
                );
              },
            ),
          ),
          const SizedBox(width: AppSpacing.s2),
          Expanded(
            child: XiloButton(
              label: shareLabel,
              icon: Icons.ios_share_outlined,
              variant: XiloButtonVariant.secondary,
              onPressed: () => Share.share(shareUrl, subject: shareLabel),
            ),
          ),
        ],
      ),
    );
  }
}

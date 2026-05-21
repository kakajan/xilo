import 'package:flutter/material.dart';
import '../../../../core/theme/app_spacing.dart';
import '../../../../core/theme/xilo_theme_extension.dart';
import '../../../../shared/widgets/xilo_avatar.dart';
import '../../domain/entities/user_profile.dart';

class ProfileHeader extends StatelessWidget {
  const ProfileHeader({super.key, required this.profile});

  final UserProfile profile;

  @override
  Widget build(BuildContext context) {
    final colors = context.xiloColors;

    return Padding(
      padding: const EdgeInsets.symmetric(horizontal: AppSpacing.s4),
      child: Column(
        children: [
          XiloAvatar(
            imageUrl: profile.avatarUrl.isNotEmpty ? profile.avatarUrl : null,
            radius: 60,
            isVerified: profile.isVerified,
            initials: profile.displayName.isNotEmpty
                ? profile.displayName[0].toUpperCase()
                : profile.username[0].toUpperCase(),
          ),
          const SizedBox(height: AppSpacing.s4),
          Row(
            mainAxisAlignment: MainAxisAlignment.center,
            children: [
              Flexible(
                child: Text(
                  profile.displayName,
                  style: Theme.of(context).textTheme.headlineLarge,
                  textAlign: TextAlign.center,
                ),
              ),
              if (profile.isVerified) ...[
                const SizedBox(width: AppSpacing.s1),
                Icon(Icons.verified, color: colors.primary, size: 20),
              ],
            ],
          ),
          const SizedBox(height: AppSpacing.s1),
          Text(
            '@${profile.username}',
            style: Theme.of(context).textTheme.bodyMedium?.copyWith(
                  color: colors.textSecondary,
                ),
          ),
          if (profile.bio.isNotEmpty) ...[
            const SizedBox(height: AppSpacing.s3),
            Text(
              profile.bio,
              textAlign: TextAlign.center,
              style: Theme.of(context).textTheme.bodyMedium,
            ),
          ],
        ],
      ),
    );
  }
}

import 'package:cached_network_image/cached_network_image.dart';
import 'package:flutter/material.dart';
import '../../core/theme/app_colors.dart';
import '../../core/theme/app_shadows.dart';
import '../../core/theme/xilo_theme_extension.dart';

class XiloAvatar extends StatelessWidget {
  const XiloAvatar({
    super.key,
    this.imageUrl,
    this.radius = 24,
    this.isVerified = false,
    this.onTap,
    this.initials,
  });

  final String? imageUrl;
  final double radius;
  final bool isVerified;
  final VoidCallback? onTap;
  final String? initials;

  @override
  Widget build(BuildContext context) {
    final colors = context.xiloColors;

    Widget avatar;
    if (imageUrl != null && imageUrl!.isNotEmpty) {
      avatar = CachedNetworkImage(
        imageUrl: imageUrl!,
        imageBuilder: (context, provider) => CircleAvatar(
          radius: radius,
          backgroundImage: provider,
        ),
        placeholder: (_, __) => _placeholder(colors, radius, initials),
        errorWidget: (_, __, ___) => _placeholder(colors, radius, initials),
      );
    } else {
      avatar = _placeholder(colors, radius, initials);
    }

    if (onTap != null) {
      avatar = InkWell(
        onTap: onTap,
        borderRadius: BorderRadius.circular(radius),
        child: avatar,
      );
    }

    return Stack(
      clipBehavior: Clip.none,
      children: [
        DecoratedBox(
          decoration: BoxDecoration(
            shape: BoxShape.circle,
            border: Border.all(color: colors.background, width: radius > 40 ? 4 : 2),
            boxShadow: AppShadows.md(Colors.black),
          ),
          child: avatar,
        ),
        if (isVerified)
          Positioned(
            right: 0,
            bottom: 0,
            child: Container(
              width: radius > 40 ? 22 : 16,
              height: radius > 40 ? 22 : 16,
              decoration: BoxDecoration(
                color: AppColors.primary,
                shape: BoxShape.circle,
                border: Border.all(color: colors.background, width: 2),
              ),
              child: Icon(
                Icons.verified,
                size: radius > 40 ? 14 : 10,
                color: Colors.white,
              ),
            ),
          ),
      ],
    );
  }

  Widget _placeholder(AppPalette colors, double r, String? initials) {
    return CircleAvatar(
      radius: r,
      backgroundColor: colors.backgroundTertiary,
      child: initials != null && initials!.isNotEmpty
          ? Text(
              initials!,
              style: TextStyle(
                color: colors.textSecondary,
                fontWeight: FontWeight.w600,
                fontSize: r * 0.5,
              ),
            )
          : Icon(Icons.person, color: colors.textTertiary, size: r),
    );
  }
}

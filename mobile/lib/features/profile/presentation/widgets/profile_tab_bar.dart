import 'package:flutter/material.dart';
import '../../../../core/theme/app_radius.dart';
import '../../../../core/theme/app_spacing.dart';
import '../../../../core/theme/xilo_theme_extension.dart';

/// Pill-shaped tab strip for profile screens (Posts / Replies / Media / Likes).
class ProfileTabBar extends StatelessWidget {
  const ProfileTabBar({
    super.key,
    required this.controller,
    required this.tabs,
    this.onTap,
  });

  final TabController controller;
  final List<Tab> tabs;
  final ValueChanged<int>? onTap;

  static const _barHeight = 44.0;

  @override
  Widget build(BuildContext context) {
    final colors = context.xiloColors;

    return Padding(
      padding: const EdgeInsets.fromLTRB(
        AppSpacing.s4,
        AppSpacing.s2,
        AppSpacing.s4,
        AppSpacing.s3,
      ),
      child: DecoratedBox(
        decoration: BoxDecoration(
          color: colors.backgroundSecondary,
          borderRadius: AppRadius.fullBorder,
          border: Border.all(color: colors.border, width: 1),
        ),
        child: SizedBox(
          height: _barHeight,
          child: TabBar(
            controller: controller,
            onTap: onTap,
            isScrollable: false,
            tabAlignment: TabAlignment.fill,
            dividerHeight: 0,
            indicatorSize: TabBarIndicatorSize.label,
            indicator: UnderlineTabIndicator(
              borderSide: BorderSide(color: colors.primary, width: 2.5),
              borderRadius: const BorderRadius.vertical(top: Radius.circular(2)),
            ),
            labelColor: colors.primary,
            unselectedLabelColor: colors.textSecondary,
            labelStyle: const TextStyle(
              fontSize: 14,
              fontWeight: FontWeight.w600,
            ),
            unselectedLabelStyle: const TextStyle(
              fontSize: 14,
              fontWeight: FontWeight.w500,
            ),
            splashFactory: NoSplash.splashFactory,
            overlayColor: WidgetStateProperty.all(Colors.transparent),
            tabs: tabs,
          ),
        ),
      ),
    );
  }
}

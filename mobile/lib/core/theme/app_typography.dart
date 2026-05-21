import 'package:flutter/material.dart';
import 'app_colors.dart';

/// Type scale from ui-ux-spec §3.
abstract final class AppTypography {
  static TextTheme textTheme(AppPalette palette, String? fontFamily) {
    TextStyle base({
      required double size,
      required FontWeight weight,
      required double height,
      Color? color,
    }) {
      return TextStyle(
        fontFamily: fontFamily,
        fontSize: size,
        fontWeight: weight,
        height: height,
        color: color ?? palette.textPrimary,
      );
    }

    return TextTheme(
      displayLarge: base(size: 32, weight: FontWeight.w700, height: 1.2),
      headlineLarge: base(size: 24, weight: FontWeight.w700, height: 1.3),
      headlineMedium: base(size: 20, weight: FontWeight.w700, height: 1.3),
      headlineSmall: base(size: 18, weight: FontWeight.w600, height: 1.4),
      titleLarge: base(size: 17, weight: FontWeight.w400, height: 1.5),
      titleMedium: base(size: 15, weight: FontWeight.w400, height: 1.5),
      titleSmall: base(size: 13, weight: FontWeight.w400, height: 1.4),
      bodyLarge: base(size: 17, weight: FontWeight.w400, height: 1.5),
      bodyMedium: base(size: 15, weight: FontWeight.w400, height: 1.5),
      bodySmall: base(
        size: 13,
        weight: FontWeight.w400,
        height: 1.4,
        color: palette.textSecondary,
      ),
      labelLarge: base(size: 15, weight: FontWeight.w600, height: 1.2),
      labelMedium: base(size: 13, weight: FontWeight.w600, height: 1.2),
      labelSmall: base(
        size: 11,
        weight: FontWeight.w400,
        height: 1.3,
        color: palette.textTertiary,
      ),
    );
  }
}

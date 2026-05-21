import 'package:flutter/material.dart';

/// Design tokens from ui-ux-spec §2.
abstract final class AppColors {
  static const Color primary = Color(0xFF1D9BF0);
  static const Color primaryHover = Color(0xFF1A8CD8);
  static const Color primaryPressed = Color(0xFF1A7BC5);
  static const Color primarySurface = Color(0xFFE8F5FE);

  static const Color error = Color(0xFFF4212E);
  static const Color errorSurface = Color(0xFFFDE8E8);
  static const Color success = Color(0xFF00BA7C);
  static const Color warning = Color(0xFFFFAD1F);

  static const AppPalette light = AppPalette(
    primary: primary,
    primaryHover: primaryHover,
    primaryPressed: primaryPressed,
    primarySurface: primarySurface,
    background: Color(0xFFFFFFFF),
    backgroundSecondary: Color(0xFFF7F9FA),
    backgroundTertiary: Color(0xFFEFF3F4),
    textPrimary: Color(0xFF0F1419),
    textSecondary: Color(0xFF536471),
    textTertiary: Color(0xFF8295A3),
    textLink: primary,
    border: Color(0xFFEFF3F4),
    borderStrong: Color(0xFFCFD9DE),
    error: error,
    errorSurface: errorSurface,
    success: success,
    successSurface: Color(0xFFE0F5EC),
    warning: warning,
    warningSurface: Color(0xFFFFF4E0),
    bubbleOwn: Color(0xFFE8F5FE),
    bubbleOthers: Color(0xFFF7F9FA),
    bubbleHighlighted: Color(0xFFFFF9C4),
    bubbleBorder: Color(0xFFEFF3F4),
    likeActive: Color(0xFFF91880),
  );

  static const AppPalette dark = AppPalette(
    primary: primary,
    primaryHover: Color(0xFF4DB8F5),
    primaryPressed: Color(0xFF6BC9F7),
    primarySurface: Color(0xFF1A2A3A),
    background: Color(0xFF15202B),
    backgroundSecondary: Color(0xFF192734),
    backgroundTertiary: Color(0xFF22303C),
    textPrimary: Color(0xFFE7E9EA),
    textSecondary: Color(0xFF71767B),
    textTertiary: Color(0xFF536471),
    textLink: Color(0xFF6BC9F7),
    border: Color(0xFF38444D),
    borderStrong: Color(0xFF4A5A66),
    error: error,
    errorSurface: Color(0xFF3D1A1E),
    success: success,
    successSurface: Color(0xFF1A3D2E),
    warning: warning,
    warningSurface: Color(0xFF3D2E1A),
    bubbleOwn: Color(0xFF1E3A5F),
    bubbleOthers: Color(0xFF2C2C2E),
    bubbleHighlighted: Color(0xFF3E3A2F),
    bubbleBorder: Color(0xFF38444D),
    likeActive: Color(0xFFF91880),
  );
}

@immutable
class AppPalette {
  const AppPalette({
    required this.primary,
    required this.primaryHover,
    required this.primaryPressed,
    required this.primarySurface,
    required this.background,
    required this.backgroundSecondary,
    required this.backgroundTertiary,
    required this.textPrimary,
    required this.textSecondary,
    required this.textTertiary,
    required this.textLink,
    required this.border,
    required this.borderStrong,
    required this.error,
    required this.errorSurface,
    required this.success,
    required this.successSurface,
    required this.warning,
    required this.warningSurface,
    required this.bubbleOwn,
    required this.bubbleOthers,
    required this.bubbleHighlighted,
    required this.bubbleBorder,
    required this.likeActive,
  });

  final Color primary;
  final Color primaryHover;
  final Color primaryPressed;
  final Color primarySurface;
  final Color background;
  final Color backgroundSecondary;
  final Color backgroundTertiary;
  final Color textPrimary;
  final Color textSecondary;
  final Color textTertiary;
  final Color textLink;
  final Color border;
  final Color borderStrong;
  final Color error;
  final Color errorSurface;
  final Color success;
  final Color successSurface;
  final Color warning;
  final Color warningSurface;
  final Color bubbleOwn;
  final Color bubbleOthers;
  final Color bubbleHighlighted;
  final Color bubbleBorder;
  final Color likeActive;
}

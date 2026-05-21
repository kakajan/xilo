import 'package:flutter/material.dart';
import 'app_colors.dart';

@immutable
class XiloThemeExtension extends ThemeExtension<XiloThemeExtension> {
  const XiloThemeExtension({required this.palette});

  final AppPalette palette;

  @override
  XiloThemeExtension copyWith({AppPalette? palette}) {
    return XiloThemeExtension(palette: palette ?? this.palette);
  }

  @override
  XiloThemeExtension lerp(ThemeExtension<XiloThemeExtension>? other, double t) {
    if (other is! XiloThemeExtension) return this;
    return t < 0.5 ? this : other;
  }
}

extension XiloThemeContext on BuildContext {
  XiloThemeExtension get xiloTheme {
    return Theme.of(this).extension<XiloThemeExtension>()!;
  }

  AppPalette get xiloColors => xiloTheme.palette;
}

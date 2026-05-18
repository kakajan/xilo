import 'package:flutter/material.dart';

class AppTheme {
  static String _fontFamilyForLocale(Locale locale) {
    final code = locale.languageCode;
    if (code == 'fa') return 'Vazirmatn';
    if (code == 'ar') return 'NotoSansArabic';
    return 'Inter';
  }

  static ThemeData _base(Brightness brightness, Locale locale) {
    final fontFamily = _fontFamilyForLocale(locale);
    return ThemeData(
      useMaterial3: true,
      colorSchemeSeed: const Color(0xFF4F46E5),
      brightness: brightness,
      fontFamily: fontFamily,
      inputDecorationTheme: InputDecorationTheme(
        border: OutlineInputBorder(borderRadius: BorderRadius.circular(12)),
        contentPadding: const EdgeInsets.symmetric(horizontal: 16, vertical: 12),
      ),
      cardTheme: CardThemeData(
        elevation: 0,
        shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(12)),
      ),
      filledButtonTheme: FilledButtonThemeData(
        style: FilledButton.styleFrom(
          minimumSize: const Size(double.infinity, 48),
          shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(12)),
        ),
      ),
    );
  }

  static ThemeData light(Locale locale) => _base(Brightness.light, locale);
  static ThemeData dark(Locale locale) => _base(Brightness.dark, locale);
}

# Skill: Theme & Styling

> Use when creating or modifying app themes, colors, typography, or responsive design

## Rules

1. ALL colors MUST be defined in `AppTheme`
2. Use `Theme.of(context)` for accessing theme values
3. Support light AND dark themes
4. Use Material 3 design
5. Typography MUST use `TextTheme` extensions

## Theme Setup

```dart
// core/theme/app_theme.dart
abstract class AppTheme {
  static const _primaryColor = Color(0xFF6366F1);
  static const _errorColor = Color(0xFFEF4444);

  static ThemeData get light => ThemeData(
    useMaterial3: true,
    brightness: Brightness.light,
    colorScheme: ColorScheme.light(
      primary: _primaryColor,
      error: _errorColor,
      surface: Colors.white,
      onSurface: Colors.black87,
    ),
    appBarTheme: const AppBarTheme(
      centerTitle: true,
      elevation: 0,
    ),
    cardTheme: CardTheme(
      elevation: 0,
      shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(12)),
    ),
    inputDecorationTheme: InputDecorationTheme(
      border: OutlineInputBorder(borderRadius: BorderRadius.circular(8)),
      filled: true,
      fillColor: Colors.grey[100],
    ),
    textTheme: _textTheme,
  );

  static ThemeData get dark => ThemeData(
    useMaterial3: true,
    brightness: Brightness.dark,
    colorScheme: ColorScheme.dark(
      primary: _primaryColor,
      error: _errorColor,
      surface: const Color(0xFF1F2937),
      onSurface: Colors.white,
    ),
    appBarTheme: const AppBarTheme(
      centerTitle: true,
      elevation: 0,
    ),
    cardTheme: CardTheme(
      elevation: 0,
      shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(12)),
    ),
    inputDecorationTheme: InputDecorationTheme(
      border: OutlineInputBorder(borderRadius: BorderRadius.circular(8)),
      filled: true,
      fillColor: Colors.grey[900],
    ),
    textTheme: _textTheme,
  );

  static const _textTheme = TextTheme(
    displayLarge: TextStyle(fontSize: 32, fontWeight: FontWeight.bold),
    titleLarge: TextStyle(fontSize: 22, fontWeight: FontWeight.w600),
    titleMedium: TextStyle(fontSize: 18, fontWeight: FontWeight.w500),
    bodyLarge: TextStyle(fontSize: 16),
    bodyMedium: TextStyle(fontSize: 14),
    bodySmall: TextStyle(fontSize: 12, color: Colors.grey),
  );
}
```

## Usage in Widgets

```dart
// Access colors
Color primary = Theme.of(context).colorScheme.primary;

// Access typography
TextStyle title = Theme.of(context).textTheme.titleLarge!;

// Access spacing (use constants)
const EdgeInsets.all(16)

// Access shapes
ShapeBorder cardShape = Theme.of(context).cardTheme.shape!;
```

## Spacing Constants

```dart
abstract class Spacing {
  static const double xxs = 4;
  static const double xs = 8;
  static const double sm = 12;
  static const double md = 16;
  static const double lg = 24;
  static const double xl = 32;
  static const double xxl = 48;
}
```

## Border Radius Constants

```dart
abstract class AppRadius {
  static const double sm = 8;
  static const double md = 12;
  static const double lg = 16;
  static const double xl = 24;
  static const double full = 9999;
}
```

## Responsive Breakpoints

```dart
abstract class Breakpoints {
  static const double mobile = 600;
  static const double tablet = 900;
  static const double desktop = 1200;
}

// Usage
bool isMobile = MediaQuery.sizeOf(context).width < Breakpoints.mobile;
bool isTablet = MediaQuery.sizeOf(context).width >= Breakpoints.mobile &&
    MediaQuery.sizeOf(context).width < Breakpoints.tablet;
```

## Custom Extensions

```dart
extension ThemeExtension on BuildContext {
  bool get isDark => Theme.of(this).brightness == Brightness.dark;
  Color get surface => Theme.of(this).colorScheme.surface;
  Color get primary => Theme.of(this).colorScheme.primary;
  TextTheme get text => Theme.of(this).textTheme;
}

// Usage
context.isDark
context.primary
context.text.titleLarge
```

import 'package:flutter/material.dart';
import 'package:flutter_localizations/flutter_localizations.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'core/router/app_router.dart';
import 'core/theme/app_theme.dart';
import 'core/di/service_locator.dart';
import 'core/storage/offline_storage.dart';
import 'core/i18n/locales.dart';
import 'features/settings/presentation/providers/locale_provider.dart';
import 'l10n/app_localizations.dart';

void main() async {
  WidgetsFlutterBinding.ensureInitialized();
  await OfflineStorage().init();
  await setupServiceLocator();
  runApp(const ProviderScope(child: XiloApp()));
}

class XiloApp extends ConsumerWidget {
  const XiloApp({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final router = ref.watch(routerProvider);
    final locale = ref.watch(localeProvider);

    return MaterialApp.router(
      title: 'Xilo',
      locale: locale,
      supportedLocales: supportedLocales,
      localizationsDelegates: const [
        AppLocalizations.delegate,
        GlobalMaterialLocalizations.delegate,
        GlobalWidgetsLocalizations.delegate,
        GlobalCupertinoLocalizations.delegate,
      ],
      localeListResolutionCallback: (locales, supported) {
        if (locales == null || locales.isEmpty) return defaultLocale;
        for (final locale in locales) {
          for (final supportedLocale in supported) {
            if (supportedLocale.languageCode == locale.languageCode) {
              return supportedLocale;
            }
          }
        }
        return defaultLocale;
      },
      theme: AppTheme.light(locale),
      darkTheme: AppTheme.dark(locale),
      themeMode: ThemeMode.system,
      routerConfig: router,
      debugShowCheckedModeBanner: false,
      builder: (context, child) {
        return Directionality(
          textDirection: getDirectionForLocale(locale),
          child: child!,
        );
      },
    );
  }
}

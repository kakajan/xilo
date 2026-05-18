import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';
import '../../../../core/i18n/locales.dart';
import '../../../../l10n/app_localizations.dart';
import '../providers/locale_provider.dart';

class LanguageSelectionScreen extends ConsumerWidget {
  const LanguageSelectionScreen({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final currentLocale = ref.watch(localeProvider);
    final l10n = AppLocalizations.of(context)!;

    return Scaffold(
      appBar: AppBar(
        title: Text(l10n.select_language),
      ),
      body: ListView(
        children: supportedLocales.map((locale) {
          final code = locale.languageCode;
          final nativeName = localeNativeNames[code] ?? code;
          final isSelected = code == currentLocale.languageCode;

          return ListTile(
            title: Text(nativeName),
            trailing: isSelected
                ? Icon(Icons.check, color: Theme.of(context).colorScheme.primary)
                : null,
            onTap: () async {
              await ref.read(localeProvider.notifier).setLocale(locale);
              if (context.mounted) context.pop();
            },
          );
        }).toList(),
      ),
    );
  }
}

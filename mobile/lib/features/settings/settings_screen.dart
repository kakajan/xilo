import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';
import '../../../core/i18n/locales.dart';
import '../../../l10n/app_localizations.dart';
import '../api/api_providers.dart';
import 'presentation/providers/locale_provider.dart';

class SettingsScreen extends ConsumerWidget {
  const SettingsScreen({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final l10n = AppLocalizations.of(context)!;
    final currentLocale = ref.watch(localeProvider);
    final currentLangName = localeNativeNames[currentLocale.languageCode] ?? currentLocale.languageCode;

    return Scaffold(
      appBar: AppBar(title: Text(l10n.settings)),
      body: ListView(
        children: [
          ListTile(
            leading: const Icon(Icons.person_outline),
            title: Text(l10n.profile),
            trailing: const Icon(Icons.chevron_right),
            onTap: () async {
              try {
                final me = await ref.read(authApiProvider).me();
                final username = me['username'] as String?;
                if (username != null && context.mounted) {
                  context.push('/profile/$username');
                }
              } catch (_) {
                if (context.mounted) {
                  context.push('/login');
                }
              }
            },
          ),
          ListTile(
            leading: const Icon(Icons.payment_outlined),
            title: Text(l10n.billing_subscription),
            trailing: const Icon(Icons.chevron_right),
            onTap: () => context.push('/billing'),
          ),
          ListTile(
            leading: const Icon(Icons.language),
            title: Text(l10n.language),
            subtitle: Text(currentLangName),
            trailing: const Icon(Icons.chevron_right),
            onTap: () => context.push('/settings/language'),
          ),
          ListTile(
            leading: const Icon(Icons.palette_outlined),
            title: Text(l10n.appearance),
            trailing: const Icon(Icons.chevron_right),
            onTap: () {},
          ),
          ListTile(
            leading: const Icon(Icons.notifications_outlined),
            title: Text(l10n.notifications),
            trailing: const Icon(Icons.chevron_right),
            onTap: () {},
          ),
          ListTile(
            leading: const Icon(Icons.info_outline),
            title: Text(l10n.about),
            trailing: const Icon(Icons.chevron_right),
            onTap: () {},
          ),
        ],
      ),
    );
  }
}

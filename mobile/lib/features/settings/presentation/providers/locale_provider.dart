import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:riverpod_annotation/riverpod_annotation.dart';
import '../../../../core/i18n/locales.dart';
import '../../../../core/storage/offline_storage.dart';

part 'locale_provider.g.dart';

const _languageKey = 'language';

@riverpod
class LocaleNotifier extends _$LocaleNotifier {
  final _storage = OfflineStorage();

  @override
  Locale build() {
    return _loadSavedLocale();
  }

  Locale _loadSavedLocale() {
    final saved = _storage.getSetting(_languageKey) as String?;
    if (saved != null) {
      final locale = Locale(saved);
      if (supportedLocales.contains(locale)) return locale;
    }

    final deviceLocale = WidgetsBinding.instance.platformDispatcher.locale;
    if (supportedLocales.contains(deviceLocale)) return deviceLocale;

    for (final supported in supportedLocales) {
      if (supported.languageCode == deviceLocale.languageCode) {
        return supported;
      }
    }

    return defaultLocale;
  }

  Future<void> setLocale(Locale locale) async {
    if (!supportedLocales.contains(locale)) return;
    state = locale;
    await _storage.setSetting(_languageKey, locale.languageCode);
  }

  TextDirection get direction {
    return getDirectionForLocale(state);
  }
}

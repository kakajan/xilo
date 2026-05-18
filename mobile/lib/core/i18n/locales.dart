import 'package:flutter/material.dart';

const supportedLocales = [
  Locale('fa'),
  Locale('en'),
  Locale('ar'),
  Locale('ru'),
  Locale('tr'),
];

const defaultLocale = Locale('fa');

const Map<String, String> localeDirection = {
  'fa': 'rtl',
  'en': 'ltr',
  'ar': 'rtl',
  'ru': 'ltr',
  'tr': 'ltr',
};

const Map<String, String> localeNativeNames = {
  'fa': 'فارسی',
  'en': 'English',
  'ar': 'العربية',
  'ru': 'Русский',
  'tr': 'Türkçe',
};

const Map<String, String> languageBadgeLabels = {
  'fa': 'فا',
  'en': 'EN',
  'ar': 'عر',
  'ru': 'RU',
  'tr': 'TR',
};

TextDirection getDirectionForLocale(Locale locale) {
  final dir = localeDirection[locale.languageCode];
  return dir == 'rtl' ? TextDirection.rtl : TextDirection.ltr;
}

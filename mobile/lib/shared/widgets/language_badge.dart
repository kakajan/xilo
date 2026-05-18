import 'package:flutter/material.dart';
import '../../../core/i18n/locales.dart';

class LanguageBadge extends StatelessWidget {
  final String languageCode;

  const LanguageBadge({super.key, required this.languageCode});

  @override
  Widget build(BuildContext context) {
    final label = languageBadgeLabels[languageCode] ?? languageCode.toUpperCase();
    return Container(
      padding: const EdgeInsets.symmetric(horizontal: 6, vertical: 2),
      decoration: BoxDecoration(
        color: Theme.of(context).colorScheme.surfaceContainerHighest,
        borderRadius: BorderRadius.circular(4),
      ),
      child: Text(
        label,
        style: Theme.of(context).textTheme.labelSmall?.copyWith(
              fontSize: 10,
              fontWeight: FontWeight.w600,
            ),
      ),
    );
  }
}

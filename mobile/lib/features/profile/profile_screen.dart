import 'package:flutter/material.dart';
import '../../../l10n/app_localizations.dart';

class ProfileScreen extends StatelessWidget {
  final String username;
  const ProfileScreen({super.key, required this.username});

  @override
  Widget build(BuildContext context) {
    final l10n = AppLocalizations.of(context)!;
    return Scaffold(
      appBar: AppBar(title: Text('@$username')),
      body: Padding(
        padding: const EdgeInsets.all(16),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.center,
          children: [
            const SizedBox(width: double.infinity),
            const CircleAvatar(radius: 48),
            const SizedBox(height: 16),
            Text(l10n.display_name, style: Theme.of(context).textTheme.headlineSmall),
            Text('@$username', style: Theme.of(context).textTheme.bodyMedium?.copyWith(color: Colors.grey)),
            const SizedBox(height: 8),
            Text(l10n.bio_placeholder),
            const SizedBox(height: 24),
            FilledButton(onPressed: () {}, child: Text(l10n.follow)),
          ],
        ),
      ),
    );
  }
}

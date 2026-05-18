import 'package:flutter/material.dart';
import '../../../l10n/app_localizations.dart';

class EditorScreen extends StatelessWidget {
  const EditorScreen({super.key});

  @override
  Widget build(BuildContext context) {
    final l10n = AppLocalizations.of(context)!;
    return Scaffold(
      appBar: AppBar(
        title: Text(l10n.write),
        actions: [
          FilledButton.icon(
            onPressed: () {},
            icon: const Icon(Icons.publish, size: 18),
            label: Text(l10n.publish),
            style: FilledButton.styleFrom(minimumSize: const Size(0, 36)),
          ),
          const SizedBox(width: 12),
        ],
      ),
      body: Padding(
        padding: const EdgeInsets.all(16),
        child: Column(
          children: [
            TextField(
              decoration: InputDecoration(
                hintText: l10n.post_title_hint,
                border: InputBorder.none,
              ),
              style: Theme.of(context).textTheme.headlineSmall,
            ),
            const Divider(),
            const SizedBox(height: 16),
            const Expanded(
              child: TextField(
                decoration: InputDecoration(
                  hintText: '',
                  border: InputBorder.none,
                ),
                maxLines: null,
                expands: true,
                textAlignVertical: TextAlignVertical.top,
              ),
            ),
          ],
        ),
      ),
    );
  }
}

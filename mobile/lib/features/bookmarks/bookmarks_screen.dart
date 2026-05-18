import 'package:flutter/material.dart';
import '../../../l10n/app_localizations.dart';

class BookmarksScreen extends StatelessWidget {
  const BookmarksScreen({super.key});

  @override
  Widget build(BuildContext context) {
    final l10n = AppLocalizations.of(context)!;
    return Scaffold(
      appBar: AppBar(title: Text(l10n.bookmarks)),
      body: Center(child: Text(l10n.no_bookmarks_yet)),
    );
  }
}

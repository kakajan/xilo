import 'dart:io';

import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:hive/hive.dart';
import 'package:xilo_mobile/features/feed/presentation/providers/feed_provider.dart';
import 'package:xilo_mobile/features/post/domain/entities/post.dart';
import 'package:xilo_mobile/main.dart';

/// Avoids HTTP calls during widget tests (Dio timers keep the test binding alive).
class _EmptyFeedNotifier extends FeedNotifier {
  @override
  Future<List<Post>> build() async => [];
}

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();

  setUpAll(() async {
    final temp = await Directory.systemTemp.createTemp('xilo_test_hive');
    Hive.init(temp.path);
    for (final name in ['feed', 'drafts', 'settings', 'user']) {
      await Hive.openBox(name);
    }
  });

  tearDownAll(() async {
    await Hive.close();
  });

  testWidgets('App loads successfully', (WidgetTester tester) async {
    await tester.pumpWidget(
      ProviderScope(
        overrides: [
          feedNotifierProvider.overrideWith(_EmptyFeedNotifier.new),
        ],
        child: const XiloApp(),
      ),
    );
    await tester.pump();
    await tester.pump(const Duration(milliseconds: 100));

    expect(find.byType(MaterialApp), findsOneWidget);
  });
}

# Skill: Testing

> Use when writing unit, widget, or integration tests

## Rules

1. EVERY feature MUST have unit tests
2. Test files MUST be in `test/` mirroring `lib/` structure
3. Use `mocktail` for mocking (not `mockito`)
4. Test naming: `<feature>_test.dart`
5. Test groups: `group('methodName', () {})`

## Test Structure

```
test/
├── core/
│   └── network/
│       └── dio_client_test.dart
├── features/
│   ├── auth/
│   │   ├── data/
│   │   │   └── repositories/
│   │   │       └── auth_repo_impl_test.dart
│   │   └── presentation/
│   │       └── providers/
│   │           └── auth_provider_test.dart
│   └── feed/
│       └── ...
└── shared/
    └── widgets/
        └── post_card_test.dart
```

## Unit Test Template

```dart
import 'package:flutter_test/flutter_test.dart';
import 'package:mocktail/mocktail.dart';

// Mocks
class MockPostRepository extends Mock implements PostRepository {}

void main() {
  late MockPostRepository repository;
  late GetFeedUseCase usecase;

  setUp(() {
    repository = MockPostRepository();
    usecase = GetFeedUseCase(repository);
  });

  group('call', () {
    final tPosts = [Post(id: '1', title: 'Test')];

    test('should return posts from repository', () async {
      // Arrange
      when(() => repository.getFeed()).thenAnswer((_) async => tPosts);

      // Act
      final result = await usecase();

      // Assert
      expect(result, equals(tPosts));
      verify(() => repository.getFeed()).called(1);
      verifyNoMoreInteractions(repository);
    });

    test('should throw when repository fails', () async {
      // Arrange
      when(() => repository.getFeed()).thenThrow(const NetworkException('Error'));

      // Act & Assert
      expect(() => usecase(), throwsA(isA<NetworkException>()));
    });
  });
}
```

## Provider Test Template

```dart
void main() {
  test('feedNotifier loads posts', () async {
    // Arrange
    final container = ProviderContainer();
    addTearDown(container.dispose);

    // Act
    final feed = await container.read(feedNotifierProvider.future);

    // Assert
    expect(feed, isA<List<Post>>());
    expect(feed, isNotEmpty);
  });

  test('feedNotifier handles error', () async {
    // Arrange
    final container = ProviderContainer(
      overrides: [
        feedRepositoryProvider.overrideWithValue(MockErrorRepository()),
      ],
    );
    addTearDown(container.dispose);

    // Act
    final state = await container.read(feedNotifierProvider.future);

    // Assert
    expect(state, isEmpty);
  });
}
```

## Widget Test Template

```dart
void main() {
  testWidgets('PostCard displays title and author', (tester) async {
    // Arrange
    final post = Post(
      id: '1',
      title: 'Test Post',
      authorName: 'John Doe',
      excerpt: 'Test excerpt',
    );

    // Act
    await tester.pumpWidget(
      MaterialApp(
        home: PostCard(post: post),
      ),
    );

    // Assert
    expect(find.text('Test Post'), findsOneWidget);
    expect(find.text('John Doe'), findsOneWidget);
  });

  testWidgets('PostCard navigates on tap', (tester) async {
    // Arrange
    final post = Post(id: '1', title: 'Test');
    var navigated = false;

    await tester.pumpWidget(
      MaterialApp(
        home: PostCard(
          post: post,
          onTap: () => navigated = true,
        ),
      ),
    );

    // Act
    await tester.tap(find.byType(PostCard));
    await tester.pump();

    // Assert
    expect(navigated, isTrue);
  });
}
```

## Integration Test Template

```dart
void main() {
  IntegrationTestWidgetsFlutterBinding.ensureInitialized();

  testWidgets('Login flow', (tester) async {
    // Arrange
    await tester.pumpWidget(const ProviderScope(child: XiloApp()));
    await tester.pumpAndSettle();

    // Act
    await tester.enterText(find.byType(TextField).at(0), 'test@test.com');
    await tester.enterText(find.byType(TextField).at(1), 'password');
    await tester.tap(find.byType(ElevatedButton));
    await tester.pumpAndSettle();

    // Assert
    expect(find.byType(FeedScreen), findsOneWidget);
  });
}
```

## Commands

```bash
# Run all tests
flutter test

# Run single test
flutter test test/features/auth/auth_repo_impl_test.dart

# Run with coverage
flutter test --coverage

# Run widget tests only
flutter test test/features/**/presentation/

# Integration test
flutter test integration_test/app_test.dart
```

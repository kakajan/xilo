# Skill: GoRouter Navigation

> Use when creating or modifying app navigation, routing, or deep links

## Rules

1. ALL navigation MUST go through GoRouter (no `Navigator.push`)
2. Routes defined in `core/router/app_router.dart`
3. Use path parameters for dynamic routes (`/post/:id`)
4. Use query parameters for optional filters (`?tab=trending`)
5. Deep links MUST be supported for all public routes

## Router Setup

```dart
// core/router/app_router.dart
final routerProvider = Provider<GoRouter>((ref) {
  final authState = ref.watch(authStateProvider);

  return GoRouter(
    initialLocation: '/feed',
    debugLogDiagnostics: true,
    redirect: (context, state) {
      final isAuth = authState.value?.isAuthenticated ?? false;
      final isLogin = state.matchedLocation == '/login';

      if (!isAuth && !isLogin) return '/login';
      if (isAuth && isLogin) return '/feed';
      return null;
    },
    routes: [
      GoRoute(
        path: '/feed',
        builder: (context, state) => const FeedScreen(),
      ),
      GoRoute(
        path: '/post/:id',
        builder: (context, state) => PostDetailScreen(
          postId: state.pathParameters['id']!,
        ),
      ),
      GoRoute(
        path: '/editor',
        builder: (context, state) => const EditorScreen(),
        routes: [
          GoRoute(
            path: 'draft/:draftId',
            builder: (context, state) => EditorScreen(
              draftId: state.pathParameters['draftId'],
            ),
          ),
        ],
      ),
      ShellRoute(
        builder: (context, state, child) => MainShell(child: child),
        routes: [
          GoRoute(path: '/feed', builder: ...),
          GoRoute(path: '/search', builder: ...),
          GoRoute(path: '/profile', builder: ...),
        ],
      ),
    ],
  );
});
```

## Navigation Methods

```dart
// Push to route
context.go('/post/$postId');

// Push with replacement
context.go('/feed');

// Pop
context.pop();

// Pop with result
context.pop(result);

// Navigate with query params
context.go('/search?q=$query');

// Navigate with extra data
context.push('/editor', extra: {'draftId': draftId});
```

## Deep Link Configuration

### Android (android/app/src/main/AndroidManifest.xml)

```xml
<intent-filter android:autoVerify="true">
  <action android:name="android.intent.action.VIEW" />
  <category android:name="android.intent.category.DEFAULT" />
  <category android:name="android.intent.category.BROWSABLE" />
  <data android:scheme="https" android:host="xilo.app" />
</intent-filter>
```

### iOS (ios/Runner/Info.plist)

```xml
<key>FlutterDeepLinkingEnabled</key>
<true/>
```

## Auth Guard Pattern

```dart
redirect: (context, state) {
  final auth = ref.read(authServiceProvider);
  final isAuthenticated = auth.currentUser != null;
  final isAuthRoute = state.matchedLocation.startsWith('/auth');

  if (!isAuthenticated && !isAuthRoute) {
    return '/auth/login?redirect=${Uri.encodeComponent(state.matchedLocation)}';
  }

  if (isAuthenticated && isAuthRoute) {
    return '/feed';
  }

  return null;
}
```

## Bottom Navigation Shell

```dart
class MainShell extends StatelessWidget {
  final Widget child;
  const MainShell({super.key, required this.child});

  @override
  Widget build(BuildContext context) {
    final location = GoRouterState.of(context).matchedLocation;

    return Scaffold(
      body: child,
      bottomNavigationBar: NavigationBar(
        selectedIndex: _getIndex(location),
        onDestinationSelected: (i) => _navigate(context, i),
        destinations: const [
          NavigationDestination(icon: Icon(Icons.home), label: 'Feed'),
          NavigationDestination(icon: Icon(Icons.search), label: 'Search'),
          NavigationDestination(icon: Icon(Icons.person), label: 'Profile'),
        ],
      ),
    );
  }
}
```

## Route Constants

```dart
abstract class AppRoutes {
  static const feed = '/feed';
  static const post = '/post/:id';
  static const editor = '/editor';
  static const editorDraft = '/editor/draft/:draftId';
  static const search = '/search';
  static const profile = '/profile';
  static const settings = '/settings';
  static const login = '/auth/login';
  static const register = '/auth/register';
  static const notifications = '/notifications';
  static const bookmarks = '/bookmarks';
}
```

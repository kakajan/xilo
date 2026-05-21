import 'package:go_router/go_router.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import '../../features/feed/feed_screen.dart';
import '../../features/search/search_screen.dart';
import '../../features/auth/login_screen.dart';
import '../../features/auth/register_screen.dart';
import '../../features/editor/editor_screen.dart';
import '../../features/notifications/notifications_screen.dart';
import '../../features/bookmarks/bookmarks_screen.dart';
import '../../features/settings/settings_screen.dart';
import '../../features/settings/presentation/screens/language_selection_screen.dart';
import '../../features/profile/presentation/screens/profile_screen.dart';
import '../../features/post/post_detail_screen.dart';
import '../../features/billing/plans_screen.dart';
import '../../features/billing/subscription_screen.dart';

final routerProvider = Provider<GoRouter>((ref) {
  return GoRouter(
    initialLocation: '/',
    routes: [
      GoRoute(path: '/', builder: (_, _) => const FeedScreen()),
      GoRoute(path: '/search', builder: (_, _) => const SearchScreen()),
      GoRoute(path: '/login', builder: (_, _) => const LoginScreen()),
      GoRoute(path: '/register', builder: (_, _) => const RegisterScreen()),
      GoRoute(path: '/editor', builder: (_, _) => const EditorScreen()),
      GoRoute(path: '/notifications', builder: (_, _) => const NotificationsScreen()),
      GoRoute(path: '/bookmarks', builder: (_, _) => const BookmarksScreen()),
      GoRoute(path: '/settings', builder: (_, _) => const SettingsScreen()),
      GoRoute(path: '/settings/language', builder: (_, _) => const LanguageSelectionScreen()),
      GoRoute(path: '/billing', builder: (_, _) => const PlansScreen()),
      GoRoute(path: '/billing/manage', builder: (_, _) => const SubscriptionScreen()),
      GoRoute(
        path: '/profile/:username',
        builder: (_, state) => ProfileScreen(username: state.pathParameters['username']!),
      ),
      GoRoute(
        path: '/post/:slug',
        builder: (_, state) => PostDetailScreen(slug: state.pathParameters['slug']!),
      ),
    ],
  );
});

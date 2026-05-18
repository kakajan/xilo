# Skill: UI & Widgets

> Use when creating or modifying screens, widgets, or UI components

## Rules

1. Screens MUST be in `presentation/screens/`
2. Feature widgets MUST be in `presentation/widgets/`
3. Shared widgets MUST be in `shared/widgets/`
4. Use `const` constructors everywhere possible
5. Use `ConsumerWidget` or `ConsumerStatefulWidget` for Riverpod
6. ALL lists MUST have shimmer loading states
7. ALL screens MUST handle error/empty states

## Screen Template

```dart
class FeedScreen extends ConsumerWidget {
  const FeedScreen({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final feed = ref.watch(feedNotifierProvider);

    return Scaffold(
      appBar: AppBar(title: const Text('Feed')),
      body: feed.when(
        data: (posts) => _FeedList(posts: posts),
        loading: () => const ShimmerList(),
        error: (e, _) => ErrorState(
          message: e.toString(),
          onRetry: () => ref.invalidate(feedNotifierProvider),
        ),
      ),
    );
  }
}
```

## Shimmer Loading

```dart
class ShimmerList extends StatelessWidget {
  const ShimmerList({super.key});

  @override
  Widget build(BuildContext context) {
    return ListView.builder(
      itemCount: 5,
      itemBuilder: (context, index) => Shimmer.fromColors(
        baseColor: Colors.grey[300]!,
        highlightColor: Colors.grey[100]!,
        child: const PostCardSkeleton(),
      ),
    );
  }
}
```

## Error State Widget

```dart
class ErrorState extends StatelessWidget {
  final String message;
  final VoidCallback? onRetry;

  const ErrorState({super.key, required this.message, this.onRetry});

  @override
  Widget build(BuildContext context) {
    return Center(
      child: Column(
        mainAxisSize: MainAxisSize.min,
        children: [
          const Icon(Icons.error_outline, size: 48, color: Colors.red),
          const SizedBox(height: 16),
          Text(message, textAlign: TextAlign.center),
          if (onRetry != null) ...[
            const SizedBox(height: 16),
            ElevatedButton(onPressed: onRetry, child: const Text('Retry')),
          ],
        ],
      ),
    );
  }
}
```

## Empty State Widget

```dart
class EmptyState extends StatelessWidget {
  final String title;
  final String subtitle;
  final IconData icon;
  final Widget? action;

  const EmptyState({
    super.key,
    required this.title,
    required this.subtitle,
    this.icon = Icons.inbox,
    this.action,
  });

  @override
  Widget build(BuildContext context) {
    return Center(
      child: Column(
        mainAxisSize: MainAxisSize.min,
        children: [
          Icon(icon, size: 64, color: Colors.grey),
          const SizedBox(height: 16),
          Text(title, style: Theme.of(context).textTheme.titleLarge),
          const SizedBox(height: 8),
          Text(subtitle, style: Theme.of(context).textTheme.bodyMedium),
          if (action != null) ...[
            const SizedBox(height: 16),
            action!,
          ],
        ],
      ),
    );
  }
}
```

## Post Card Widget

```dart
class PostCard extends StatelessWidget {
  final Post post;
  final VoidCallback? onTap;

  const PostCard({super.key, required this.post, this.onTap});

  @override
  Widget build(BuildContext context) {
    return Card(
      child: InkWell(
        onTap: onTap ?? () => context.go('/post/${post.id}'),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            if (post.coverUrl != null)
              Hero(
                tag: 'post-${post.id}',
                child: CachedNetworkImage(
                  imageUrl: post.coverUrl!,
                  height: 200,
                  width: double.infinity,
                  fit: BoxFit.cover,
                ),
              ),
            Padding(
              padding: const EdgeInsets.all(16),
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Text(post.title, style: Theme.of(context).textTheme.titleLarge),
                  const SizedBox(height: 8),
                  Text(post.excerpt, maxLines: 2, overflow: TextOverflow.ellipsis),
                  const SizedBox(height: 8),
                  Row(
                    children: [
                      Text(post.authorName),
                      const SizedBox(width: 8),
                      Text('${post.readingTime} min read'),
                    ],
                  ),
                ],
              ),
            ),
          ],
        ),
      ),
    );
  }
}
```

## Animation Patterns

```dart
// Fade in animation
AnimatedSwitcher(
  duration: const Duration(milliseconds: 300),
  child: isLoading ? const ShimmerList() : _FeedList(posts: posts),
);

// Slide transition
AnimatedSlide(
  offset: isVisible ? Offset.zero : const Offset(0, 0.2),
  child: FadeInOpacity(child: widget),
);

// Hero animation (between screens)
Hero(
  tag: 'post-${post.id}',
  child: Image.network(post.coverUrl),
)
```

## Responsive Design

```dart
LayoutBuilder(
  builder: (context, constraints) {
    if (constraints.maxWidth > 600) {
      return _DesktopLayout();
    }
    return _MobileLayout();
  },
);
```

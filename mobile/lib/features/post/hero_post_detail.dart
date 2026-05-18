import 'package:flutter/material.dart';
import 'package:flutter_animate/flutter_animate.dart';

class HeroPostDetail extends StatelessWidget {
  final String coverImageUrl;
  final String title;
  final String authorName;
  final String content;
  final String date;
  final int readingTime;

  const HeroPostDetail({
    super.key,
    required this.coverImageUrl,
    required this.title,
    required this.authorName,
    required this.content,
    required this.date,
    required this.readingTime,
  });

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      body: CustomScrollView(
        slivers: [
          SliverAppBar(
            expandedHeight: 250,
            pinned: true,
            flexibleSpace: FlexibleSpaceBar(
              background: coverImageUrl.isNotEmpty
                  ? Hero(
                      tag: 'post-cover-$coverImageUrl',
                      child: Image.network(
                        coverImageUrl,
                        fit: BoxFit.cover,
                        width: double.infinity,
                      ),
                    )
                  : Container(color: Theme.of(context).colorScheme.primaryContainer),
            ),
          ),
          SliverToBoxAdapter(
            child: Padding(
              padding: const EdgeInsets.all(20),
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Text(
                    title,
                    style: Theme.of(context).textTheme.headlineMedium?.copyWith(fontWeight: FontWeight.bold),
                  ).animate().fadeIn(delay: 200.ms).slideY(begin: 0.1, end: 0),
                  const SizedBox(height: 12),
                  Row(
                    children: [
                      const CircleAvatar(radius: 18),
                      const SizedBox(width: 10),
                      Column(
                        crossAxisAlignment: CrossAxisAlignment.start,
                        children: [
                          Text(authorName, style: Theme.of(context).textTheme.titleSmall),
                          Text('$date · $readingTime min read', style: Theme.of(context).textTheme.bodySmall),
                        ],
                      ),
                    ],
                  ).animate().fadeIn(delay: 400.ms),
                  const SizedBox(height: 24),
                  Text(
                    content,
                    style: Theme.of(context).textTheme.bodyLarge?.copyWith(height: 1.8),
                  ).animate().fadeIn(delay: 600.ms),
                  const SizedBox(height: 80),
                ],
              ),
            ),
          ),
        ],
      ),
    );
  }
}

class ShimmerPostDetail extends StatelessWidget {
  const ShimmerPostDetail({super.key});

  @override
  Widget build(BuildContext context) {
    return const Center(child: CircularProgressIndicator());
  }
}

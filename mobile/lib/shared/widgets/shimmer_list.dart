import 'package:flutter/material.dart';
import 'package:shimmer/shimmer.dart';

class ShimmerList extends StatelessWidget {
  final int itemCount;
  const ShimmerList({super.key, this.itemCount = 5});

  @override
  Widget build(BuildContext context) {
    return ListView.builder(
      itemCount: itemCount,
      padding: const EdgeInsets.all(16),
      itemBuilder: (_, i) => Shimmer.fromColors(
        baseColor: Theme.of(context).colorScheme.surfaceContainerHighest,
        highlightColor: Theme.of(context).colorScheme.surfaceContainer,
        child: Padding(
          padding: const EdgeInsets.only(bottom: 24),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Row(
                children: [
                  const CircleAvatar(radius: 14, backgroundColor: Colors.white),
                  const SizedBox(width: 8),
                  Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      Container(height: 12, width: 100, color: Colors.white),
                      const SizedBox(height: 4),
                      Container(height: 10, width: 60, color: Colors.white),
                    ],
                  ),
                ],
              ),
              const SizedBox(height: 12),
              Container(height: 16, width: double.infinity, color: Colors.white),
              const SizedBox(height: 8),
              Container(height: 14, width: double.infinity, color: Colors.white),
              const SizedBox(height: 4),
              Container(height: 14, width: 200, color: Colors.white),
            ],
          ),
        ),
      ),
    );
  }
}

class PullToRefresh extends StatelessWidget {
  final Future<void> Function() onRefresh;
  final Widget child;

  const PullToRefresh({super.key, required this.onRefresh, required this.child});

  @override
  Widget build(BuildContext context) {
    return RefreshIndicator(
      onRefresh: onRefresh,
      child: child,
    );
  }
}

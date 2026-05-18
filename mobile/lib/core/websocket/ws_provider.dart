import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_animate/flutter_animate.dart';
import 'ws_manager.dart';

final wsManagerProvider = Provider<WebSocketManager>((ref) {
  final manager = WebSocketManager();
  ref.onDispose(() => manager.dispose());
  manager.connect();
  return manager;
});

class PerformanceOptimizedList extends ConsumerStatefulWidget {
  final String title;
  final String? retryLabel;
  final String? errorLabel;
  final Future<List<dynamic>> Function() loadData;
  final Widget Function(dynamic item, int index) itemBuilder;

  const PerformanceOptimizedList({
    super.key,
    required this.title,
    this.retryLabel,
    this.errorLabel,
    required this.loadData,
    required this.itemBuilder,
  });

  @override
  ConsumerState<PerformanceOptimizedList> createState() => _PerformanceOptimizedListState();
}

class _PerformanceOptimizedListState extends ConsumerState<PerformanceOptimizedList> {
  final _scrollController = ScrollController();
  final _items = <dynamic>[];
  bool _isLoading = true;
  String? _error;

  @override
  void initState() {
    super.initState();
    _load();
    _scrollController.addListener(_onScroll);
  }

  Future<void> _load() async {
    try {
      final data = await widget.loadData();
      setState(() {
        _items.addAll(data);
        _isLoading = false;
      });
    } catch (e) {
      setState(() {
        _error = e.toString();
        _isLoading = false;
      });
    }
  }

  void _onScroll() {
    if (_scrollController.position.pixels >= _scrollController.position.maxScrollExtent - 200) {
      // Load more
    }
  }

  @override
  void dispose() {
    _scrollController.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(
        title: Text(widget.title),
        actions: [
          IconButton(
            icon: const Icon(Icons.refresh),
            onPressed: () {
              setState(() {
                _items.clear();
                _isLoading = true;
              });
              _load();
            },
          ),
        ],
      ),
      body: _isLoading
          ? const Center(child: CircularProgressIndicator())
          : _error != null
              ? Center(
                  child: Column(
                    mainAxisSize: MainAxisSize.min,
                    children: [
                      Text(widget.errorLabel ?? 'Error: $_error'),
                      FilledButton(onPressed: _load, child: Text(widget.retryLabel ?? 'Retry')),
                    ],
                  ),
                )
              : RefreshIndicator(
                  onRefresh: () async {
                    setState(() {
                      _items.clear();
                      _isLoading = true;
                    });
                    await _load();
                  },
                  child: ListView.builder(
                    controller: _scrollController,
                    itemCount: _items.length,
                    addAutomaticKeepAlives: true,
                    addRepaintBoundaries: true,
                    cacheExtent: 500,
                    itemBuilder: (_, i) => widget.itemBuilder(_items[i], i)
                        .animate()
                        .fadeIn(duration: 300.ms, delay: (i * 30).ms)
                        .slideY(begin: 0.05, end: 0),
                  ),
                ),
    );
  }
}

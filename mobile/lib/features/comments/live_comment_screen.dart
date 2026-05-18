import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_animate/flutter_animate.dart';
import '../../../l10n/app_localizations.dart';
import '../../core/websocket/ws_provider.dart';

class LiveCommentScreen extends ConsumerStatefulWidget {
  final String postId;
  final String postSlug;

  const LiveCommentScreen({super.key, required this.postId, required this.postSlug});

  @override
  ConsumerState<LiveCommentScreen> createState() => _LiveCommentScreenState();
}

class _LiveCommentScreenState extends ConsumerState<LiveCommentScreen> {
  final _controller = TextEditingController();
  final _comments = <Map<String, dynamic>>[];
  bool _loading = true;

  @override
  void initState() {
    super.initState();
    _loadComments();
    _subscribeToWebSocket();
  }

  void _loadComments() {
    setState(() => _loading = true);
    // API call would go here
    setState(() => _loading = false);
  }

  void _subscribeToWebSocket() {
    final ws = ref.read(wsManagerProvider);
    ws.subscribeToPost(widget.postId);

    ws.messages.listen((msg) {
      final event = msg['event'] as String?;
      if (event == 'comment.created' || event == 'comment.updated') {
        setState(() {
          final comment = msg['data'] as Map<String, dynamic>;
          final idx = _comments.indexWhere((c) => c['id'] == comment['id']);
          if (idx >= 0) {
            _comments[idx] = comment;
          } else {
            _comments.insert(0, comment);
          }
        });
      } else if (event == 'comment.deleted') {
        final id = msg['data']['id'] as String;
        setState(() => _comments.removeWhere((c) => c['id'] == id));
      } else if (event == 'notification.created') {
        _showNotificationBanner(msg['data'] as Map<String, dynamic>);
      }
    });
  }

  void _showNotificationBanner(Map<String, dynamic> notification) {
    if (!mounted) return;
    ScaffoldMessenger.of(context).showSnackBar(
      SnackBar(
        content: Text(notification['title'] as String? ?? 'New notification'),
        duration: const Duration(seconds: 3),
        behavior: SnackBarBehavior.floating,
      ),
    );
  }

  @override
  void dispose() {
    _controller.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    final l10n = AppLocalizations.of(context)!;
    return Scaffold(
      appBar: AppBar(title: Text(l10n.comments_title)),
      body: Column(
        children: [
          Expanded(
            child: _loading
                ? const Center(child: CircularProgressIndicator())
                : _comments.isEmpty
                    ? Center(child: Text(l10n.no_comments_yet))
                    : ListView.builder(
                        itemCount: _comments.length,
                        padding: const EdgeInsets.all(16),
                        itemBuilder: (_, i) {
                          final comment = _comments[i];
                          return ListTile(
                            leading: const CircleAvatar(radius: 16),
                            title: Text(comment['author']?['display_name'] ?? 'User'),
                            subtitle: Text(comment['content'] ?? ''),
                            isThreeLine: false,
                          ).animate().fadeIn(delay: (i * 50).ms, duration: 300.ms);
                        },
                      ),
          ),
          SafeArea(
            child: Padding(
              padding: const EdgeInsets.all(16),
              child: Row(
                children: [
                  Expanded(
                    child: TextField(
                      controller: _controller,
                      decoration: InputDecoration(
                        hintText: l10n.write_comment_hint,
                        border: const OutlineInputBorder(),
                      ),
                    ),
                  ),
                  const SizedBox(width: 8),
                  FilledButton(
                    onPressed: () {
                      if (_controller.text.trim().isNotEmpty) {
                        final ws = ref.read(wsManagerProvider);
                        ws.send('comment.create', {
                          'postId': widget.postId,
                          'content': _controller.text.trim(),
                        });
                        _controller.clear();
                      }
                    },
                    child: Text(l10n.send),
                  ),
                ],
              ),
            ),
          ),
        ],
      ),
    );
  }
}

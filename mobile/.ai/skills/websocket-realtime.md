# Skill: WebSocket & Real-time

> Use when creating or modifying real-time features (comments, notifications, live updates)

## Rules

1. WebSocket connection MUST be managed by `WebSocketManager`
2. Auto-reconnect on disconnect with exponential backoff
3. Use Riverpod `StreamProvider` for consuming events
4. Subscribe/unsubscribe based on screen lifecycle
5. Handle connection state in UI (connecting/connected/disconnected)

## WebSocket Manager

```dart
class WebSocketManager {
  final String _url;
  WebSocketChannel? _channel;
  final _controller = StreamController<WsEvent>.broadcast();

  Stream<WsEvent> get events => _controller.stream;
  bool get isConnected => _channel != null;

  WebSocketManager(this._url);

  void connect({String? token}) {
    final uri = Uri.parse(_url).replace(
      queryParameters: {'token': token},
    );

    _channel = WebSocketChannel.connect(uri);
    _channel!.stream.listen(
      (data) {
        try {
          final event = WsEvent.fromJson(jsonDecode(data));
          _controller.add(event);
        } catch (e) {
          debugPrint('WS parse error: $e');
        }
      },
      onDone: () => _reconnect(),
      onError: (e) => _reconnect(),
    );
  }

  void _reconnect() {
    Future.delayed(const Duration(seconds: 2), () {
      if (!isConnected) connect();
    });
  }

  void subscribe(String channel) {
    _channel?.sink.add(jsonEncode({'type': 'subscribe', 'channel': channel}));
  }

  void unsubscribe(String channel) {
    _channel?.sink.add(jsonEncode({'type': 'unsubscribe', 'channel': channel}));
  }

  void send(Map<String, dynamic> data) {
    _channel?.sink.add(jsonEncode(data));
  }

  void dispose() {
    _channel?.sink.close();
    _controller.close();
  }
}
```

## Event Model

```dart
@freezed
class WsEvent with _$WsEvent {
  const factory WsEvent({
    required String type,
    required String channel,
    Map<String, dynamic>? data,
  }) = _WsEvent;

  factory WsEvent.fromJson(Map<String, dynamic> json) =>
      _$WsEventFromJson(json);
}

// Event types
abstract class WsTypes {
  static const newComment = 'new_comment';
  static const newNotification = 'new_notification';
  static const postUpdate = 'post_update';
  static const liveReaction = 'live_reaction';
}
```

## Riverpod Stream Provider

```dart
@riverpod
Stream<WsEvent> wsEvents(WsEventsRef ref) {
  return sl<WebSocketManager>().events;
}

@riverpod
Stream<List<Comment>> liveComments(LiveCommentsRef ref, String postId) async* {
  // Initial fetch
  final comments = await sl<CommentRepository>().getComments(postId);
  yield comments;

  // Listen for new comments
  await for (final event in sl<WebSocketManager>().events) {
    if (event.type == WsTypes.newComment &&
        event.data?['postId'] == postId) {
      final newComment = Comment.fromJson(event.data!['comment']);
      yield [...comments, newComment];
    }
  }
}
```

## Notification Provider

```dart
@riverpod
class NotificationNotifier extends _$NotificationNotifier {
  StreamSubscription? _sub;

  @override
  Future<List<Notification>> build() async {
    // Load initial notifications
    final list = await sl<NotificationRepository>().getNotifications();

    // Subscribe to real-time
    _sub = sl<WebSocketManager>().events.listen((event) {
      if (event.type == WsTypes.newNotification) {
        final newNotif = Notification.fromJson(event.data!['notification']);
        state = AsyncData([newNotif, ...state.valueOrNull ?? []]);
      }
    });

    return list;
  }

  @override
  void dispose() {
    _sub?.cancel();
    super.dispose();
  }
}
```

## Screen Lifecycle Management

```dart
class PostDetailScreen extends ConsumerStatefulWidget {
  final String postId;
  const PostDetailScreen({super.key, required this.postId});

  @override
  ConsumerState<PostDetailScreen> createState() => _PostDetailScreenState();
}

class _PostDetailScreenState extends ConsumerState<PostDetailScreen> {
  @override
  void initState() {
    super.initState();
    // Subscribe to post channel
    sl<WebSocketManager>().subscribe('post:${widget.postId}');
  }

  @override
  void dispose() {
    // Unsubscribe when leaving
    sl<WebSocketManager>().unsubscribe('post:${widget.postId}');
    super.dispose();
  }

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    return Scaffold(...);
  }
}
```

## Connection Status Widget

```dart
class ConnectionStatus extends ConsumerWidget {
  const ConnectionStatus({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final ws = sl<WebSocketManager>();

    if (!ws.isConnected) {
      return Container(
        color: Colors.orange,
        padding: const EdgeInsets.all(8),
        child: const Text('Reconnecting...', textAlign: TextAlign.center),
      );
    }

    return const SizedBox.shrink();
  }
}
```

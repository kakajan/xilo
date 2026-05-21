import 'dart:async';
import 'dart:convert';
import 'package:web_socket_channel/web_socket_channel.dart';
import 'package:flutter_secure_storage/flutter_secure_storage.dart';
import 'dart:math';

class WebSocketManager {
  WebSocketChannel? _channel;
  final _storage = const FlutterSecureStorage();
  final _messageController = StreamController<Map<String, dynamic>>.broadcast();
  Timer? _reconnectTimer;
  int _backoff = 1000;
  bool _disposed = false;

  Stream<Map<String, dynamic>> get messages => _messageController.stream;

  Future<void> connect() async {
    final token = await _storage.read(key: 'access_token');
    if (token == null) return;

    try {
      _channel = WebSocketChannel.connect(
        Uri.parse('ws://127.0.0.1:8000/ws?token=$token'),
      );

      _channel!.stream.listen(
        (data) {
          try {
            final msg = jsonDecode(data as String) as Map<String, dynamic>;
            _messageController.add(msg);
          } catch (_) {}
        },
        onDone: _onDisconnected,
        onError: (_) => _onDisconnected(),
      );

      _backoff = 1000;
    } catch (_) {
      _onDisconnected();
    }
  }

  void _onDisconnected() {
    _channel = null;
    if (_disposed) return;

    _reconnectTimer?.cancel();
    _reconnectTimer = Timer(Duration(milliseconds: _backoff), () {
      _backoff = min(_backoff * 2, 30000);
      connect();
    });
  }

  Future<void> subscribeToPost(String postId) async {
    _channel?.sink.add(jsonEncode({
      'event': 'subscribe:post',
      'postId': postId,
    }));
  }

  void send(String event, Map<String, dynamic> data) {
    _channel?.sink.add(jsonEncode({
      'event': event,
      ...data,
    }));
  }

  void dispose() {
    _disposed = true;
    _reconnectTimer?.cancel();
    _channel?.sink.close();
    _messageController.close();
  }
}

import 'package:flutter/foundation.dart';

/// Loopback on the Android emulator maps to the host machine via 10.0.2.2.
String get apiHost {
  if (!kIsWeb && defaultTargetPlatform == TargetPlatform.android) {
    return '10.0.2.2';
  }
  return '127.0.0.1';
}

String get apiBaseUrl => 'http://$apiHost:8888';
String get wsBaseUrl => 'ws://$apiHost:8000';

import 'package:flutter_secure_storage/flutter_secure_storage.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import '../../core/network/dio_client.dart';

class AuthState {
  final bool isAuthenticated;
  final String? userId;
  final String? username;

  const AuthState({this.isAuthenticated = false, this.userId, this.username});
}

final authProvider = NotifierProvider<AuthNotifier, AuthState>(AuthNotifier.new);

class AuthNotifier extends Notifier<AuthState> {
  late final FlutterSecureStorage _storage;

  @override
  AuthState build() {
    _storage = ref.read(secureStorageProvider);
    return const AuthState();
  }

  Future<void> login(String email, String password) async {
    // Will call API via dio
  }

  Future<void> register(String username, String email, String password) async {
    // Will call API via dio
  }

  Future<void> logout() async {
    await _storage.deleteAll();
    state = const AuthState();
  }

  Future<void> restoreSession() async {
    final token = await _storage.read(key: 'access_token');
    if (token != null) {
      state = const AuthState(isAuthenticated: true);
    }
  }
}

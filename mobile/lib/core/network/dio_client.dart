import 'package:dio/dio.dart';
import 'package:flutter_secure_storage/flutter_secure_storage.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

final dioProvider = Provider<Dio>((ref) {
  final dio = Dio(BaseOptions(
    baseUrl: 'http://10.0.2.2:8888',
    connectTimeout: const Duration(seconds: 10),
    receiveTimeout: const Duration(seconds: 10),
  ));
  dio.interceptors.add(ref.read(authInterceptorProvider));
  return dio;
});

final secureStorageProvider = Provider<FlutterSecureStorage>((ref) {
  return const FlutterSecureStorage();
});

final authInterceptorProvider = Provider<Interceptor>((ref) {
  return InterceptorsWrapper(
    onRequest: (options, handler) async {
      final storage = ref.read(secureStorageProvider);
      final token = await storage.read(key: 'access_token');
      if (token != null) {
        options.headers['Authorization'] = 'Bearer $token';
      }
      handler.next(options);
    },
    onError: (error, handler) async {
      if (error.response?.statusCode == 401) {
        final storage = ref.read(secureStorageProvider);
        final refresh = await storage.read(key: 'refresh_token');
        if (refresh != null) {
          try {
            final dio = Dio(BaseOptions(baseUrl: 'http://10.0.2.2:8888'));
            final res = await dio.post('/api/auth/refresh', data: {
              'refresh_token': refresh,
            });
            final access = res.data['access_token'];
            final newRefresh = res.data['refresh_token'];
            await storage.write(key: 'access_token', value: access);
            await storage.write(key: 'refresh_token', value: newRefresh);

            error.requestOptions.headers['Authorization'] = 'Bearer $access';
            final retryResponse = await Dio().fetch(error.requestOptions);
            handler.resolve(retryResponse);
            return;
          } catch (_) {
            await storage.deleteAll();
          }
        }
      }
      handler.next(error);
    },
  );
});

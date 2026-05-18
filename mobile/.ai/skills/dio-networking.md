# Skill: Dio Networking

> Use when creating or modifying HTTP requests, interceptors, or API clients

## Rules

1. ALL HTTP calls MUST go through Dio (no `http` package)
2. Use interceptors for auth, logging, retry
3. Error handling: catch `DioException` and map to domain errors
4. Base URL from environment/config, NOT hardcoded
5. Timeouts: 10s connect, 10s receive

## Dio Client Setup

```dart
// core/network/dio_client.dart
Dio createDioClient() {
  final dio = Dio(BaseOptions(
    baseUrl: Config.apiUrl,
    connectTimeout: const Duration(seconds: 10),
    receiveTimeout: const Duration(seconds: 10),
    headers: {'Content-Type': 'application/json'},
  ));

  dio.interceptors.addAll([
    AuthInterceptor(),
    LogInterceptor(requestBody: true, responseBody: true),
    RetryInterceptor(dio: dio, retries: 3),
  ]);

  return dio;
}
```

## Auth Interceptor

```dart
class AuthInterceptor extends Interceptor {
  @override
  void onRequest(RequestOptions options, RequestInterceptorHandler handler) {
    final token = sl<SecureStorage>().getAccessToken();
    if (token != null) {
      options.headers['Authorization'] = 'Bearer $token';
    }
    handler.next(options);
  }

  @override
  void onError(DioException err, ErrorInterceptorHandler handler) async {
    if (err.response?.statusCode == 401) {
      final refreshed = await _refreshToken();
      if (refreshed) {
        handler.resolve(await _retry(err.requestOptions));
        return;
      }
      // Token refresh failed → logout
      sl<AuthService>().logout();
    }
    handler.next(err);
  }

  Future<Response> _retry(RequestOptions options) async {
    final newToken = sl<SecureStorage>().getAccessToken();
    options.headers['Authorization'] = 'Bearer $newToken';
    return sl<Dio>().fetch(options);
  }
}
```

## Remote Data Source Pattern

```dart
class PostRemoteDataSource {
  final Dio _dio;

  PostRemoteDataSource(this._dio);

  Future<List<Post>> getFeed({String? cursor}) async {
    final response = await _dio.get('/api/posts', queryParameters: {
      if (cursor != null) 'cursor': cursor,
      'limit': 20,
    });

    return (response.data['posts'] as List)
        .map((json) => Post.fromJson(json))
        .toList();
  }

  Future<Post> getPost(String id) async {
    final response = await _dio.get('/api/posts/$id');
    return Post.fromJson(response.data['post']);
  }

  Future<Post> createPost(CreatePostRequest request) async {
    final response = await _dio.post(
      '/api/posts',
      data: request.toJson(),
    );
    return Post.fromJson(response.data['post']);
  }
}
```

## Error Mapping

```dart
sealed class AppException implements Exception {
  const AppException(this.message);
  final String message;
}

class NetworkException extends AppException {
  const NetworkException(super.message);
}

class UnauthorizedException extends AppException {
  const UnauthorizedException(super.message);
}

class ServerException extends AppException {
  const ServerException(super.message);
}

AppException mapDioException(DioException e) {
  return switch (e.type) {
    DioExceptionType.connectionTimeout ||
    DioExceptionType.sendTimeout ||
    DioExceptionType.receiveTimeout =>
      const NetworkException('Connection timeout'),
    DioExceptionType.badResponse => switch (e.response?.statusCode) {
      401 => const UnauthorizedException('Unauthorized'),
      404 => const NetworkException('Not found'),
      >= 500 => const ServerException('Server error'),
      _ => const NetworkException('Request failed'),
    },
    _ => const NetworkException('Network error'),
  };
}
```

## Repository with Offline Fallback

```dart
class PostRepoImpl implements PostRepository {
  Future<List<Post>> getFeed({String? cursor}) async {
    try {
      final posts = await _remote.getFeed(cursor: cursor);
      if (cursor == null) await _local.cachePosts(posts);
      return posts;
    } on DioException {
      // Offline: return cached data
      return _local.getCachedPosts();
    }
  }
}
```

## File Upload

```dart
Future<void> uploadImage(File image) async {
  final formData = FormData.fromMap({
    'image': await MultipartFile.fromFile(image.path),
  });

  final response = await _dio.post('/api/upload', data: formData);
  return response.data['url'];
}
```

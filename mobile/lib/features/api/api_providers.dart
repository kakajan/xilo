import 'package:dio/dio.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import '../../core/network/dio_client.dart';

final authApiProvider = Provider<AuthApi>((ref) {
  return AuthApi(ref.read(dioProvider));
});

class AuthApi {
  final Dio _dio;
  AuthApi(this._dio);

  Future<Map<String, dynamic>> login(String email, String password) async {
    final res = await _dio.post('/api/auth/login', data: {
      'email': email,
      'password': password,
    });
    return res.data;
  }

  Future<Map<String, dynamic>> register(String username, String email, String password) async {
    final res = await _dio.post('/api/auth/register', data: {
      'username': username,
      'email': email,
      'password': password,
    });
    return res.data;
  }

  Future<Map<String, dynamic>> me() async {
    final res = await _dio.get('/api/auth/me');
    return res.data;
  }
}

final feedApiProvider = Provider<FeedApi>((ref) {
  return FeedApi(ref.read(dioProvider));
});

class FeedApi {
  final Dio _dio;
  FeedApi(this._dio);

  Future<Map<String, dynamic>> getFeed({int limit = 10}) async {
    final res = await _dio.get('/api/posts?limit=$limit');
    return res.data;
  }

  Future<Map<String, dynamic>> getPost(String slug) async {
    final res = await _dio.get('/api/posts/$slug');
    return res.data;
  }
}

final searchApiProvider = Provider<SearchApi>((ref) {
  return SearchApi(ref.read(dioProvider));
});

class SearchApi {
  final Dio _dio;
  SearchApi(this._dio);

  Future<Map<String, dynamic>> search(String query, {int limit = 20}) async {
    final res = await _dio.get('/api/search/posts?q=$query&limit=$limit');
    return res.data;
  }

  Future<Map<String, dynamic>> suggest(String query) async {
    final res = await _dio.get('/api/search/suggest?q=$query');
    return res.data;
  }
}

final billingApiProvider = Provider<BillingApi>((ref) {
  return BillingApi(ref.read(dioProvider));
});

class BillingApi {
  final Dio _dio;
  BillingApi(this._dio);

  Future<Map<String, dynamic>> getPlans() async {
    final res = await _dio.get('/api/billing/plans');
    return res.data;
  }

  Future<Map<String, dynamic>> getMySubscription() async {
    final res = await _dio.get('/api/billing/my-subscription');
    return res.data;
  }

  Future<Map<String, dynamic>> subscribe(String planSlug, {String? mobile, String? email}) async {
    final res = await _dio.post('/api/billing/subscribe', data: {
      'plan_slug': planSlug,
      if (mobile != null) 'mobile': mobile,
      if (email != null) 'email': email,
    });
    return res.data;
  }

  Future<Map<String, dynamic>> cancelSubscription() async {
    final res = await _dio.delete('/api/billing/subscription');
    return res.data;
  }

  Future<Map<String, dynamic>> getInvoices() async {
    final res = await _dio.get('/api/billing/invoices');
    return res.data;
  }
}

import 'package:flutter_riverpod/flutter_riverpod.dart';
import '../api/api_providers.dart';

class BillingState {
  final List<Map<String, dynamic>> plans;
  final Map<String, dynamic>? subscription;
  final bool isActive;
  final bool isLoading;
  final String? error;

  const BillingState({
    this.plans = const [],
    this.subscription,
    this.isActive = false,
    this.isLoading = false,
    this.error,
  });

  BillingState copyWith({
    List<Map<String, dynamic>>? plans,
    Map<String, dynamic>? subscription,
    bool? isActive,
    bool? isLoading,
    String? error,
  }) {
    return BillingState(
      plans: plans ?? this.plans,
      subscription: subscription ?? this.subscription,
      isActive: isActive ?? this.isActive,
      isLoading: isLoading ?? this.isLoading,
      error: error,
    );
  }
}

final billingProvider =
    NotifierProvider<BillingNotifier, BillingState>(BillingNotifier.new);

class BillingNotifier extends Notifier<BillingState> {
  late final BillingApi _api;

  @override
  BillingState build() {
    _api = ref.read(billingApiProvider);
    return const BillingState();
  }

  Future<void> loadPlans() async {
    state = state.copyWith(isLoading: true, error: null);
    try {
      final data = await _api.getPlans();
      final plans = (data['data'] as List?)?.cast<Map<String, dynamic>>() ?? [];
      state = state.copyWith(plans: plans, isLoading: false);
    } catch (e) {
      state = state.copyWith(isLoading: false, error: e.toString());
    }
  }

  Future<void> loadSubscription() async {
    try {
      final data = await _api.getMySubscription();
      state = BillingState(
        plans: state.plans,
        subscription: data['subscription'],
        isActive: data['active'] == true,
      );
    } catch (_) {}
  }

  Future<Map<String, dynamic>?> subscribe(String planSlug) async {
    try {
      final data = await _api.subscribe(planSlug);
      return data;
    } catch (e) {
      state = state.copyWith(error: e.toString());
      return null;
    }
  }

  Future<bool> cancelSubscription() async {
    try {
      await _api.cancelSubscription();
      state = state.copyWith(subscription: null, isActive: false);
      return true;
    } catch (e) {
      state = state.copyWith(error: e.toString());
      return false;
    }
  }

  Future<List<Map<String, dynamic>>> loadInvoices() async {
    try {
      final data = await _api.getInvoices();
      return (data['data'] as List?)?.cast<Map<String, dynamic>>() ?? [];
    } catch (_) {
      return [];
    }
  }

  void clearError() {
    state = state.copyWith(error: null);
  }
}

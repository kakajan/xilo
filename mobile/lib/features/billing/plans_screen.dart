import 'dart:convert';
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import '../../../l10n/app_localizations.dart';
import 'billing_provider.dart';
import 'payment_webview.dart';
import 'subscription_screen.dart';

class PlansScreen extends ConsumerStatefulWidget {
  const PlansScreen({super.key});

  @override
  ConsumerState<PlansScreen> createState() => _PlansScreenState();
}

class _PlansScreenState extends ConsumerState<PlansScreen> {
  bool _subscribing = false;

  @override
  void initState() {
    super.initState();
    Future.microtask(() {
      ref.read(billingProvider.notifier).loadPlans();
      ref.read(billingProvider.notifier).loadSubscription();
    });
  }

  Future<void> _handleSubscribe(String planSlug) async {
    final l10n = AppLocalizations.of(context)!;
    setState(() => _subscribing = true);
    try {
      final result = await ref.read(billingProvider.notifier).subscribe(planSlug);
      if (result == null || !mounted) return;

      final gatewayUrl = result['gateway_url'] as String?;
      final invoiceId = result['invoice_id'] as String?;
      final authority = result['authority'] as String?;

      if (gatewayUrl == null) return;

      if (!mounted) return;
      final success = await Navigator.of(context).push<bool>(
        MaterialPageRoute(
          builder: (_) => PaymentWebview(
            gatewayUrl: gatewayUrl,
            invoiceId: invoiceId ?? '',
            authority: authority ?? '',
          ),
        ),
      );

      if (success == true && mounted) {
        ref.read(billingProvider.notifier).loadSubscription();
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(
            content: Text(l10n.payment_success),
            backgroundColor: Colors.green,
          ),
        );
      } else if (success == false && mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(
            content: Text(l10n.payment_failed),
            backgroundColor: Colors.orange,
          ),
        );
      }
    } catch (e) {
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(
            content: Text('${l10n.error_prefix} ${e.toString()}'),
            backgroundColor: Colors.red,
          ),
        );
      }
    } finally {
      if (mounted) setState(() => _subscribing = false);
    }
  }

  String _formatPrice(int priceCents, String currency) {
    final amount = priceCents / 100;
    if (currency == 'IRR') {
      return '${priceCents.toString().replaceAllMapped(RegExp(r'(\d{1,3})(?=(\d{3})+(?!\d))'), (m) => '${m[1]},')} IRR';
    }
    return '$amount $currency';
  }

  String _intervalLabel(String interval, AppLocalizations l10n) {
    switch (interval) {
      case 'monthly':
        return l10n.per_month;
      case 'yearly':
        return l10n.per_year;
      default:
        return '';
    }
  }

  @override
  Widget build(BuildContext context) {
    final state = ref.watch(billingProvider);
    final theme = Theme.of(context);
    final l10n = AppLocalizations.of(context)!;

    return Scaffold(
      appBar: AppBar(
        title: Text(l10n.subscription_plans),
        actions: [
          if (state.isActive)
            TextButton.icon(
              onPressed: () => Navigator.of(context).push(
                MaterialPageRoute(builder: (_) => const SubscriptionScreen()),
              ),
              icon: const Icon(Icons.manage_accounts, size: 20),
              label: Text(l10n.manage),
            ),
        ],
      ),
      body: _subscribing
          ? const Center(child: CircularProgressIndicator())
          : state.isLoading
              ? const Center(child: CircularProgressIndicator())
              : state.error != null
                  ? Center(
                      child: Column(
                        mainAxisSize: MainAxisSize.min,
                        children: [
                          Text(state.error!, style: TextStyle(color: theme.colorScheme.error)),
                          const SizedBox(height: 16),
                          FilledButton(
                            onPressed: () => ref.read(billingProvider.notifier).loadPlans(),
                            child: Text(l10n.retry),
                          ),
                        ],
                      ),
                    )
                  : ListView(
                      padding: const EdgeInsets.all(16),
                      children: [
                        if (state.isActive)
                          Card(
                            color: theme.colorScheme.primaryContainer,
                            child: Padding(
                              padding: const EdgeInsets.all(16),
                              child: Row(
                                children: [
                                  Icon(Icons.check_circle, color: theme.colorScheme.primary),
                                  const SizedBox(width: 12),
                                  Expanded(
                                    child: Column(
                                      crossAxisAlignment: CrossAxisAlignment.start,
                                      children: [
                                        Text(
                                          l10n.active_subscription,
                                          style: theme.textTheme.titleMedium?.copyWith(
                                            color: theme.colorScheme.onPrimaryContainer,
                                          ),
                                        ),
                                        if (state.subscription?['plan_name'] != null)
                                          Text(
                                            state.subscription!['plan_name'] as String,
                                            style: theme.textTheme.bodyMedium?.copyWith(
                                              color: theme.colorScheme.onPrimaryContainer,
                                            ),
                                          ),
                                      ],
                                    ),
                                  ),
                                ],
                              ),
                            ),
                          ),
                        const SizedBox(height: 24),
                        Text(
                          l10n.choose_plan,
                          style: theme.textTheme.headlineSmall,
                        ),
                        const SizedBox(height: 16),
                        ...state.plans.map((plan) {
                          final slug = plan['slug'] as String? ?? '';
                          final name = plan['name'] as String? ?? '';
                          final priceCents = plan['price_cents'] as int? ?? 0;
                          final currency = plan['currency'] as String? ?? 'IRR';
                          final interval = plan['interval'] as String? ?? 'monthly';
                          final features = plan['features'] as String? ?? '[]';
                          final isCurrentPlan = state.isActive &&
                              state.subscription?['plan_id'] == plan['id'];

                          List<String> featureList;
                          try {
                            featureList = (jsonDecode(features) as List)
                                .map((f) => f.toString())
                                .toList();
                          } catch (_) {
                            featureList = ['Ad-free reading', 'Premium content', 'Priority support'];
                          }

                          return Card(
                            margin: const EdgeInsets.only(bottom: 12),
                            child: Padding(
                              padding: const EdgeInsets.all(16),
                              child: Column(
                                crossAxisAlignment: CrossAxisAlignment.start,
                                children: [
                                  Row(
                                    mainAxisAlignment: MainAxisAlignment.spaceBetween,
                                    children: [
                                      Expanded(
                                        child: Text(
                                          name,
                                          style: theme.textTheme.titleMedium,
                                        ),
                                      ),
                                      Column(
                                        crossAxisAlignment: CrossAxisAlignment.end,
                                        children: [
                                          Text(
                                            priceCents == 0
                                                ? l10n.free
                                                : _formatPrice(priceCents, currency),
                                            style: theme.textTheme.titleMedium?.copyWith(
                                              color: theme.colorScheme.primary,
                                            ),
                                          ),
                                          Text(
                                            _intervalLabel(interval, l10n),
                                            style: theme.textTheme.bodySmall?.copyWith(
                                              color: theme.colorScheme.onSurfaceVariant,
                                            ),
                                          ),
                                        ],
                                      ),
                                    ],
                                  ),
                                  if (featureList.isNotEmpty) ...[
                                    const SizedBox(height: 12),
                                    ...featureList.map((f) => Padding(
                                          padding: const EdgeInsets.only(bottom: 4),
                                          child: Row(
                                            children: [
                                              Icon(Icons.check, size: 16, color: theme.colorScheme.primary),
                                              const SizedBox(width: 8),
                                              Text(f, style: theme.textTheme.bodyMedium),
                                            ],
                                          ),
                                        )),
                                  ],
                                  const SizedBox(height: 16),
                                  SizedBox(
                                    width: double.infinity,
                                    child: isCurrentPlan
                                        ? FilledButton.tonal(
                                            onPressed: null,
                                            child: Text(l10n.current_plan),
                                          )
                                        : priceCents == 0
                                            ? FilledButton(
                                                onPressed: () => _handleSubscribe(slug),
                                                child: Text(l10n.get_started_free),
                                              )
                                            : FilledButton(
                                                onPressed: () => _handleSubscribe(slug),
                                                child: Text('${l10n.subscribe}${_intervalLabel(interval, l10n)}'),
                                              ),
                                  ),
                                ],
                              ),
                            ),
                          );
                        }),
                      ],
                    ),
    );
  }
}

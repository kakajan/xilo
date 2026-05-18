import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import '../../../l10n/app_localizations.dart';
import 'billing_provider.dart';

class SubscriptionScreen extends ConsumerStatefulWidget {
  const SubscriptionScreen({super.key});

  @override
  ConsumerState<SubscriptionScreen> createState() => _SubscriptionScreenState();
}

class _SubscriptionScreenState extends ConsumerState<SubscriptionScreen> {
  List<Map<String, dynamic>> _invoices = [];
  bool _loadingInvoices = false;

  @override
  void initState() {
    super.initState();
    _loadInvoices();
  }

  Future<void> _loadInvoices() async {
    setState(() => _loadingInvoices = true);
    final invoices = await ref.read(billingProvider.notifier).loadInvoices();
    if (mounted) {
      setState(() {
        _invoices = invoices;
        _loadingInvoices = false;
      });
    }
  }

  Future<void> _handleCancel() async {
    final l10n = AppLocalizations.of(context)!;
    final confirmed = await showDialog<bool>(
      context: context,
      builder: (ctx) => AlertDialog(
        title: Text(l10n.cancel_subscription_title),
        content: Text(l10n.cancel_subscription_msg),
        actions: [
          TextButton(onPressed: () => Navigator.pop(ctx, false), child: Text(l10n.no)),
          FilledButton(
            onPressed: () => Navigator.pop(ctx, true),
            style: FilledButton.styleFrom(backgroundColor: Theme.of(ctx).colorScheme.error),
            child: Text(l10n.cancel_subscription_btn),
          ),
        ],
      ),
    );

    if (confirmed == true && mounted) {
      final success = await ref.read(billingProvider.notifier).cancelSubscription();
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(
            content: Text(success ? l10n.subscription_cancelled : l10n.cancel_failed),
            backgroundColor: success ? Colors.green : Colors.red,
          ),
        );
        if (success) Navigator.of(context).pop();
      }
    }
  }

  String _formatDate(String? dateStr) {
    if (dateStr == null) return '-';
    try {
      final dt = DateTime.parse(dateStr);
      return '${dt.year}-${dt.month.toString().padLeft(2, '0')}-${dt.day.toString().padLeft(2, '0')}';
    } catch (_) {
      return dateStr;
    }
  }

  String _formatPrice(int cents) {
    return '${cents.toString().replaceAllMapped(RegExp(r'(\d{1,3})(?=(\d{3})+(?!\d))'), (m) => '${m[1]},')} IRR';
  }

  @override
  Widget build(BuildContext context) {
    final state = ref.watch(billingProvider);
    final theme = Theme.of(context);
    final l10n = AppLocalizations.of(context)!;

    return Scaffold(
      appBar: AppBar(title: Text(l10n.my_subscription)),
      body: ListView(
        padding: const EdgeInsets.all(16),
        children: [
          if (state.isActive && state.subscription != null) ...[
            Card(
              color: theme.colorScheme.primaryContainer,
              child: Padding(
                padding: const EdgeInsets.all(16),
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Row(
                      children: [
                        Icon(Icons.check_circle, color: theme.colorScheme.primary),
                        const SizedBox(width: 8),
                        Text(
                          l10n.active_plan,
                          style: theme.textTheme.titleMedium?.copyWith(
                            color: theme.colorScheme.onPrimaryContainer,
                          ),
                        ),
                      ],
                    ),
                    const SizedBox(height: 8),
                    Text(
                      state.subscription!['plan_name'] as String? ?? 'Premium',
                      style: theme.textTheme.headlineSmall?.copyWith(
                        color: theme.colorScheme.onPrimaryContainer,
                      ),
                    ),
                    if (state.subscription!['expires_at'] != null) ...[
                      const SizedBox(height: 4),
                      Text(
                        '${l10n.expires}: ${_formatDate(state.subscription!['expires_at'] as String?)}',
                        style: theme.textTheme.bodyMedium?.copyWith(
                          color: theme.colorScheme.onPrimaryContainer,
                        ),
                      ),
                    ],
                  ],
                ),
              ),
            ),
            const SizedBox(height: 16),
            SizedBox(
              width: double.infinity,
              child: FilledButton(
                onPressed: _handleCancel,
                style: FilledButton.styleFrom(
                  backgroundColor: theme.colorScheme.error,
                ),
                child: Text(l10n.cancel_subscription_btn),
              ),
            ),
          ] else ...[
            Card(
              child: Padding(
                padding: const EdgeInsets.all(16),
                child: Column(
                  children: [
                    Icon(Icons.info_outline, size: 48, color: theme.colorScheme.onSurfaceVariant),
                    const SizedBox(height: 12),
                    Text(
                      l10n.no_active_subscription,
                      style: theme.textTheme.titleMedium,
                    ),
                    const SizedBox(height: 4),
                    Text(
                      l10n.choose_plan_get_started,
                      style: theme.textTheme.bodyMedium?.copyWith(
                        color: theme.colorScheme.onSurfaceVariant,
                      ),
                    ),
                    const SizedBox(height: 16),
                    FilledButton(
                      onPressed: () => Navigator.of(context).pushReplacementNamed('/billing'),
                      child: Text(l10n.view_plans),
                    ),
                  ],
                ),
              ),
            ),
          ],
          const SizedBox(height: 32),
          Text(l10n.billing_history, style: theme.textTheme.titleMedium),
          const SizedBox(height: 12),
          if (_loadingInvoices)
            const Center(child: CircularProgressIndicator())
          else if (_invoices.isEmpty)
            Card(
              child: Padding(
                padding: const EdgeInsets.all(24),
                child: Center(
                  child: Text(
                    l10n.no_invoices_yet,
                    style: theme.textTheme.bodyMedium?.copyWith(
                      color: theme.colorScheme.onSurfaceVariant,
                    ),
                  ),
                ),
              ),
            )
          else
            ..._invoices.map((inv) {
              final status = inv['status'] as String? ?? '';
              final method = inv['payment_gateway'] as String? ?? inv['payment_method'] as String? ?? '';
              return Card(
                margin: const EdgeInsets.only(bottom: 8),
                child: ListTile(
                  leading: Icon(
                    status == 'paid' ? Icons.check_circle : Icons.hourglass_empty,
                    color: status == 'paid' ? Colors.green : Colors.orange,
                  ),
                  title: Text(_formatPrice(inv['amount_cents'] as int? ?? 0)),
                  subtitle: Text('$method · ${_formatDate(inv['created_at'] as String?)}'),
                  trailing: status == 'paid'
                      ? Container(
                          padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 4),
                          decoration: BoxDecoration(
                            color: Colors.green.withOpacity(0.1),
                            borderRadius: BorderRadius.circular(8),
                          ),
                          child: Text(l10n.paid, style: const TextStyle(color: Colors.green, fontSize: 12)),
                        )
                      : Container(
                          padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 4),
                          decoration: BoxDecoration(
                            color: Colors.orange.withOpacity(0.1),
                            borderRadius: BorderRadius.circular(8),
                          ),
                          child: Text(l10n.pending, style: const TextStyle(color: Colors.orange, fontSize: 12)),
                        ),
                ),
              );
            }),
        ],
      ),
    );
  }
}

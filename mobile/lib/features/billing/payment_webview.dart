import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:webview_flutter/webview_flutter.dart';
import '../../../l10n/app_localizations.dart';
import 'billing_provider.dart';

class PaymentWebview extends ConsumerStatefulWidget {
  final String gatewayUrl;
  final String invoiceId;
  final String authority;

  const PaymentWebview({
    super.key,
    required this.gatewayUrl,
    required this.invoiceId,
    required this.authority,
  });

  @override
  ConsumerState<PaymentWebview> createState() => _PaymentWebviewState();
}

class _PaymentWebviewState extends ConsumerState<PaymentWebview> {
  late final WebViewController _controller;
  bool _callbackIntercepted = false;

  @override
  void initState() {
    super.initState();
    _controller = WebViewController()
      ..setJavaScriptMode(JavaScriptMode.unrestricted)
      ..setNavigationDelegate(
        NavigationDelegate(
          onNavigationRequest: (request) {
            final uri = Uri.tryParse(request.url);
            if (uri != null && uri.host == Uri.parse(widget.gatewayUrl).host) {
              return NavigationDecision.navigate;
            }

            if (_callbackIntercepted) {
              return NavigationDecision.prevent;
            }

            final queryStatus = uri?.queryParameters['Status'];
            final queryAuthority = uri?.queryParameters['Authority'];

            if (queryStatus == 'OK' && queryAuthority != null) {
              _callbackIntercepted = true;
              Future.microtask(() async {
                ref.read(billingProvider.notifier).loadSubscription();
                if (mounted) {
                  Navigator.of(context).pop(true);
                }
              });
              return NavigationDecision.prevent;
            }

            if (queryStatus == 'NOK') {
              _callbackIntercepted = true;
              Future.microtask(() {
                if (mounted) {
                  Navigator.of(context).pop(false);
                }
              });
              return NavigationDecision.prevent;
            }

            return NavigationDecision.navigate;
          },
        ),
      )
      ..loadRequest(Uri.parse(widget.gatewayUrl));
  }

  @override
  Widget build(BuildContext context) {
    final l10n = AppLocalizations.of(context)!;
    return Scaffold(
      appBar: AppBar(
        title: Text(l10n.zarinpal_payment),
        leading: IconButton(
          icon: const Icon(Icons.close),
          onPressed: () => Navigator.of(context).pop(false),
        ),
      ),
      body: WebViewWidget(controller: _controller),
    );
  }
}

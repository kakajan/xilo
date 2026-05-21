import 'package:flutter/material.dart';
import '../../core/theme/app_spacing.dart';

enum XiloButtonVariant { primary, secondary, ghost }

class XiloButton extends StatelessWidget {
  const XiloButton({
    super.key,
    required this.label,
    required this.onPressed,
    this.variant = XiloButtonVariant.primary,
    this.icon,
    this.expanded = false,
    this.enabled = true,
  });

  final String label;
  final VoidCallback? onPressed;
  final XiloButtonVariant variant;
  final IconData? icon;
  final bool expanded;
  final bool enabled;

  @override
  Widget build(BuildContext context) {
    final child = icon != null
        ? Row(
            mainAxisSize: MainAxisSize.min,
            mainAxisAlignment: MainAxisAlignment.center,
            children: [
              Icon(icon, size: 18),
              const SizedBox(width: AppSpacing.s2),
              Text(label),
            ],
          )
        : Text(label);

    final effectiveOnPressed = enabled ? onPressed : null;

    Widget button;
    switch (variant) {
      case XiloButtonVariant.primary:
        button = FilledButton(onPressed: effectiveOnPressed, child: child);
      case XiloButtonVariant.secondary:
        button = OutlinedButton(onPressed: effectiveOnPressed, child: child);
      case XiloButtonVariant.ghost:
        button = TextButton(onPressed: effectiveOnPressed, child: child);
    }

    if (expanded) {
      return SizedBox(width: double.infinity, child: button);
    }
    return button;
  }
}

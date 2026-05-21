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
    this.compact = false,
    this.enabled = true,
  });

  final String label;
  final VoidCallback? onPressed;
  final XiloButtonVariant variant;
  final IconData? icon;
  final bool expanded;
  final bool compact;
  final bool enabled;

  static final _compactStyle = ButtonStyle(
    padding: WidgetStatePropertyAll(
      EdgeInsets.symmetric(horizontal: AppSpacing.s2, vertical: AppSpacing.s2),
    ),
    minimumSize: const WidgetStatePropertyAll(Size(0, 36)),
    tapTargetSize: MaterialTapTargetSize.shrinkWrap,
  );

  @override
  Widget build(BuildContext context) {
    final iconSize = compact ? 16.0 : 18.0;
    final labelStyle = compact
        ? Theme.of(context).textTheme.labelMedium
        : Theme.of(context).textTheme.labelLarge;

    Widget child = icon != null
        ? Row(
            mainAxisSize: MainAxisSize.min,
            mainAxisAlignment: MainAxisAlignment.center,
            children: [
              Icon(icon, size: iconSize),
              const SizedBox(width: AppSpacing.s1),
              Text(label, maxLines: 1, style: labelStyle),
            ],
          )
        : Text(label, maxLines: 1, style: labelStyle);

    if (expanded) {
      child = SizedBox(
        width: double.infinity,
        child: FittedBox(
          fit: BoxFit.scaleDown,
          child: child,
        ),
      );
    }

    final effectiveOnPressed = enabled ? onPressed : null;
    final style = compact ? _compactStyle : null;

    Widget button;
    switch (variant) {
      case XiloButtonVariant.primary:
        button = FilledButton(
          style: style,
          onPressed: effectiveOnPressed,
          child: child,
        );
      case XiloButtonVariant.secondary:
        button = OutlinedButton(
          style: style,
          onPressed: effectiveOnPressed,
          child: child,
        );
      case XiloButtonVariant.ghost:
        button = TextButton(
          style: style,
          onPressed: effectiveOnPressed,
          child: child,
        );
    }

    return SizedBox(width: expanded ? double.infinity : null, child: button);
  }
}

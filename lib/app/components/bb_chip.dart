import 'package:flutter/material.dart';
import 'package:get/get.dart';

/// A custom BlueBubbles chip widget that provides a consistent design pattern
/// for filter chips throughout the app. Supports both deletable and selectable modes.
class BBChip extends StatelessWidget {
  final Widget label;
  final Widget? avatar;
  final bool tapEnabled;
  final VoidCallback? onPressed;
  final VoidCallback? onDeleted;
  final bool showCheckmark;
  final bool selected;
  final ValueChanged<bool>? onSelected;
  final Color? checkmarkColor;

  const BBChip({
    super.key,
    required this.label,
    this.avatar,
    this.tapEnabled = true,
    this.onPressed,
    this.onDeleted,
    this.showCheckmark = false,
    this.selected = false,
    this.onSelected,
    this.checkmarkColor,
  });

  @override
  Widget build(BuildContext context) {
    return RawChip(
      tapEnabled: tapEnabled,
      deleteIcon: onDeleted != null ? const Icon(Icons.close, size: 16) : null,
      side: BorderSide(color: context.theme.colorScheme.outline.withValues(alpha: 0.1)),
      shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(20)),
      avatar: avatar,
      label: label,
      onDeleted: onDeleted,
      onPressed: onPressed,
      showCheckmark: showCheckmark,
      selected: selected,
      onSelected: onSelected,
      checkmarkColor: checkmarkColor,
    );
  }
}

import 'package:bluebubbles/helpers/helpers.dart';
import 'package:bluebubbles/services/services.dart';
import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:get/get.dart';

class SettingsSlider extends StatefulWidget {
  const SettingsSlider(
      {required this.startingVal,
      this.update,
      this.onChangeEnd,
      this.formatValue,
      required this.min,
      required this.max,
      this.leadingMinWidth,
      required this.divisions,
      this.leading,
      this.backgroundColor,
      super.key});

  final double startingVal;
  final Function(double val)? update;
  final Function(double val)? onChangeEnd;
  final Function(double value)? formatValue;
  final double min;
  final double max;
  final double? leadingMinWidth;
  final int divisions;
  final Widget? leading;
  final Color? backgroundColor;

  @override
  State<SettingsSlider> createState() => _SettingsSliderState();
}

class _SettingsSliderState extends State<SettingsSlider> {
  // Non-focusable node so the Slider never takes D-pad focus and can't consume the arrow
  // keys for its own value adjustment — the wrapping Focus handles them instead.
  final FocusNode _sliderNode = FocusNode(canRequestFocus: false, skipTraversal: true, debugLabel: 'slider');

  @override
  void dispose() {
    _sliderNode.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    String value = widget.startingVal.toString();
    if (widget.formatValue != null) {
      value = widget.formatValue!(widget.startingVal);
    }

    final Widget realSlider = Slider(
      focusNode: _sliderNode,
      activeColor: context.theme.colorScheme.primary.oppositeLightenOrDarken(20),
      secondaryActiveColor: context.theme.colorScheme.primary.withValues(alpha: 0.6),
      thumbColor: context.theme.colorScheme.primary,
      inactiveColor: context.theme.colorScheme.primary.withValues(alpha: 0.2),
      value: widget.startingVal,
      onChanged: widget.update,
      onChangeEnd: widget.onChangeEnd,
      label: value,
      divisions: widget.divisions,
      min: widget.min,
      max: widget.max,
      mouseCursor: SystemMouseCursors.click,
    );

    Widget slider = realSlider;
    // In D-pad mode, left/right adjust the value while up/down navigate between settings.
    if (SettingsSvc.settings.isDumb.value) {
      slider = Focus(
        onKeyEvent: (node, event) {
          if (event is! KeyDownEvent && event is! KeyRepeatEvent) return KeyEventResult.ignored;
          final step = widget.divisions > 0 ? (widget.max - widget.min) / widget.divisions : (widget.max - widget.min) / 10;
          if (event.logicalKey == LogicalKeyboardKey.arrowLeft) {
            final v = (widget.startingVal - step).clamp(widget.min, widget.max).toDouble();
            widget.update?.call(v);
            widget.onChangeEnd?.call(v);
            return KeyEventResult.handled;
          }
          if (event.logicalKey == LogicalKeyboardKey.arrowRight) {
            final v = (widget.startingVal + step).clamp(widget.min, widget.max).toDouble();
            widget.update?.call(v);
            widget.onChangeEnd?.call(v);
            return KeyEventResult.handled;
          }
          // Up/Down move focus to the next/previous setting instead of changing the value.
          if (event.logicalKey == LogicalKeyboardKey.arrowUp) {
            node.focusInDirection(TraversalDirection.up);
            return KeyEventResult.handled;
          }
          if (event.logicalKey == LogicalKeyboardKey.arrowDown) {
            node.focusInDirection(TraversalDirection.down);
            return KeyEventResult.handled;
          }
          return KeyEventResult.ignored;
        },
        child: Builder(
          builder: (context) => DecoratedBox(
            decoration: BoxDecoration(
              color: Focus.of(context).hasFocus
                  ? context.theme.colorScheme.primary.withValues(alpha: 0.12)
                  : Colors.transparent,
              borderRadius: BorderRadius.circular(8),
            ),
            child: realSlider,
          ),
        ),
      );
    }

    return ListTile(
      leading: widget.leading,
      trailing: Text(value, style: context.theme.textTheme.bodyLarge),
      minLeadingWidth: widget.leadingMinWidth,
      title: slider,
    );
  }
}

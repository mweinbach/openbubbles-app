import 'package:bluebubbles/app/layouts/settings/pages/theming/theme_studio/widgets/color_editor_tile.dart';
import 'package:bluebubbles/app/layouts/settings/pages/theming/theme_studio/theme_studio_panel.dart';
import 'package:bluebubbles/helpers/helpers.dart';
import 'package:flutter/material.dart';
import 'package:get/get.dart';

// ─── Data types ────────────────────────────────────────────────────────────────

/// Describes one color slot.  [on] is the paired "on" color key, if any.
@immutable
class ColorPair {
  const ColorPair(this.main, [this.on]);
  final String main;
  final String? on;
}

/// Describes a named group of [ColorPair]s shown in one collapsible section.
@immutable
class ColorGroup {
  const ColorGroup({required this.title, required this.icon, required this.pairs});
  final String title;
  final IconData icon;
  final List<ColorPair> pairs;
}

// ─── Section widget ────────────────────────────────────────────────────────────

class ColorEditorSection extends StatefulWidget {
  const ColorEditorSection({
    super.key,
    required this.group,
    required this.colors,
    required this.editable,
    required this.controller,
  });

  final ColorGroup group;
  final Map<String, Color> colors;
  final bool editable;
  final ThemeStudioController controller;

  @override
  State<ColorEditorSection> createState() => _ColorEditorSectionState();
}

class _ColorEditorSectionState extends State<ColorEditorSection> {
  bool _expanded = false;

  Color? _colorFor(String key) => widget.colors[key];

  @override
  Widget build(BuildContext context) {
    final cs = context.theme.colorScheme;
    // Collect preview swatches for collapsed header
    final swatchColors = widget.group.pairs.map((p) => _colorFor(p.main)).whereType<Color>().take(4).toList();

    return ClipRRect(
      borderRadius: BorderRadius.circular(12),
      child: Material(
        color: context.tileColor,
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.stretch,
          children: [
            // Header row
            InkWell(
              onTap: () => setState(() => _expanded = !_expanded),
              child: Padding(
                padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 12),
                child: Row(
                  children: [
                    Icon(widget.group.icon, size: 20, color: cs.primary),
                    const SizedBox(width: 12),
                    Expanded(
                      child: Text(
                        widget.group.title,
                        style: context.theme.textTheme.bodyMedium?.copyWith(fontWeight: FontWeight.w600),
                      ),
                    ),
                    // Mini swatch preview
                    if (!_expanded)
                      Row(
                        mainAxisSize: MainAxisSize.min,
                        children: swatchColors
                            .map((c) => Padding(
                                  padding: const EdgeInsets.only(left: 4),
                                  child: _MiniSwatch(color: c),
                                ))
                            .toList(),
                      ),
                    const SizedBox(width: 8),
                    AnimatedRotation(
                      turns: _expanded ? 0.5 : 0,
                      duration: const Duration(milliseconds: 200),
                      child: Icon(Icons.expand_more, color: cs.onSurfaceVariant),
                    ),
                  ],
                ),
              ),
            ),
            // Expanded color tiles — only built when the section is open,
            // avoiding the cost of constructing all ColorEditorTile widgets
            // on initial page load when every section is collapsed.
            AnimatedSize(
              duration: const Duration(milliseconds: 200),
              curve: Curves.easeInOut,
              child: _expanded
                  ? Column(
                      crossAxisAlignment: CrossAxisAlignment.stretch,
                      children: [
                        Divider(height: 1, color: cs.outline.withValues(alpha: 0.15), indent: 16, endIndent: 16),
                        ...widget.group.pairs.asMap().entries.map((entry) {
                          final pair = entry.value;
                          final mainColor = _colorFor(pair.main);
                          final onColor = pair.on != null ? _colorFor(pair.on!) : null;
                          if (mainColor == null) return const SizedBox.shrink();
                          return Column(
                            children: [
                              if (entry.key > 0)
                                Divider(height: 1, color: cs.outline.withValues(alpha: 0.1), indent: 16, endIndent: 16),
                              ColorEditorTile(
                                mainKey: pair.main,
                                onKey: pair.on,
                                mainColor: mainColor,
                                onColor: onColor,
                                editable: widget.editable,
                                controller: widget.controller,
                              ),
                            ],
                          );
                        }),
                      ],
                    )
                  : const SizedBox.shrink(),
            ),
          ],
        ),
      ),
    );
  }
}

class _MiniSwatch extends StatelessWidget {
  const _MiniSwatch({required this.color});
  final Color color;

  @override
  Widget build(BuildContext context) {
    return Container(
      width: 14,
      height: 14,
      decoration: BoxDecoration(
        color: color,
        shape: BoxShape.circle,
        border: Border.all(
          color: context.theme.colorScheme.outline.withValues(alpha: 0.25),
          width: 0.5,
        ),
      ),
    );
  }
}

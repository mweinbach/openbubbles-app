import 'package:bluebubbles/app/layouts/settings/pages/theming/theme_studio/theme_studio_panel.dart';
import 'package:bluebubbles/app/layouts/settings/widgets/settings_widgets.dart';
import 'package:bluebubbles/database/models.dart';
import 'package:bluebubbles/helpers/helpers.dart';
import 'package:flutter/material.dart';
import 'package:get/get.dart';
import 'package:google_fonts/google_fonts.dart';

/// Font-family selector + master + per-style size sliders.
class TypographyEditor extends StatefulWidget {
  const TypographyEditor({super.key, required this.controller});

  final ThemeStudioController controller;

  @override
  State<TypographyEditor> createState() => _TypographyEditorState();
}

class _TypographyEditorState extends State<TypographyEditor> {
  bool _sizesExpanded = false;

  ThemeStudioController get ctrl => widget.controller;

  void _resetAllSizes(BuildContext context) {
    ctrl.updateTextSize(context, 'master', 1.0);
  }

  double _multiplierFor(String key) {
    final sizes = ctrl.activeTheme.textSizes;
    final def = ThemeStruct.defaultTextSizes[key]!;
    return (sizes[key] ?? def) / def;
  }

  @override
  Widget build(BuildContext context) {
    final theme = ctrl.activeTheme;
    final editable = ctrl.isEditable;
    final tileColor = context.tileColor;

    return Padding(
      padding: const EdgeInsets.symmetric(horizontal: 16),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.stretch,
        children: [
          // ── Font selector ──────────────────────────────────────────────────
          ClipRRect(
            borderRadius: BorderRadius.circular(12),
            child: Material(
              color: tileColor,
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.stretch,
                children: [
                  _FontPickerTile(
                    currentFont: theme.googleFont,
                    editable: editable,
                    onChanged: (value) => ctrl.updateFont(context, value),
                  ),
                  Padding(
                    padding: const EdgeInsets.only(bottom: 8, left: 16, right: 16),
                    child: _FontPreviewBanner(activeTheme: theme),
                  ),
                  SettingsSubtitle(
                    subtitle: editable
                        ? "Fonts are downloaded on selection. Visit fonts.google.com to preview them."
                        : "Select a custom theme above to change the font.",
                    unlimitedSpace: true,
                  ),
                ],
              ),
            ),
          ),

          const SizedBox(height: 8),

          // ── Master + individual size presets ───────────────────────────────
          if (editable) ...[
            ClipRRect(
              borderRadius: BorderRadius.circular(12),
              child: Material(
                color: tileColor,
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.stretch,
                  children: [
                    _SizePresetRow(
                      label: 'master',
                      currentMultiplier: _multiplierFor('bodyMedium'),
                      onChanged: (v) => ctrl.updateTextSize(context, 'master', v),
                    ),
                    Align(
                      alignment: Alignment.centerRight,
                      child: Padding(
                        padding: const EdgeInsets.only(right: 8, bottom: 4),
                        child: TextButton.icon(
                          onPressed: () => _resetAllSizes(context),
                          style: TextButton.styleFrom(
                            visualDensity: VisualDensity.compact,
                            foregroundColor: context.theme.colorScheme.onSurfaceVariant,
                          ),
                          icon: const Icon(Icons.refresh, size: 14),
                          label: const Text("Reset All", style: TextStyle(fontSize: 12)),
                        ),
                      ),
                    ),
                  ],
                ),
              ),
            ),

            const SizedBox(height: 8),

            // ── Per-style sliders (collapsible) ──────────────────────────────
            ClipRRect(
              borderRadius: BorderRadius.circular(12),
              child: Material(
                color: tileColor,
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.stretch,
                  children: [
                    InkWell(
                      onTap: () => setState(() => _sizesExpanded = !_sizesExpanded),
                      child: Padding(
                        padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 12),
                        child: Row(
                          children: [
                            Icon(Icons.format_size, size: 20, color: context.theme.colorScheme.primary),
                            const SizedBox(width: 12),
                            Expanded(
                              child: Text("Individual Sizes",
                                  style: context.theme.textTheme.bodyMedium?.copyWith(fontWeight: FontWeight.w600)),
                            ),
                            AnimatedRotation(
                              turns: _sizesExpanded ? 0.5 : 0,
                              duration: const Duration(milliseconds: 200),
                              child: Icon(Icons.expand_more, color: context.theme.colorScheme.onSurfaceVariant),
                            ),
                          ],
                        ),
                      ),
                    ),
                    AnimatedSize(
                      duration: const Duration(milliseconds: 200),
                      curve: Curves.easeInOut,
                      child: _sizesExpanded
                          ? Column(
                              children: theme.textSizes.keys.map((key) {
                                return _SizePresetRow(
                                  label: key,
                                  currentMultiplier: _multiplierFor(key),
                                  onChanged: (v) => ctrl.updateTextSize(context, key, v),
                                );
                              }).toList(),
                            )
                          : const SizedBox.shrink(),
                    ),
                  ],
                ),
              ),
            ),
          ],
        ],
      ),
    );
  }
}

// ─── Font preview banner ───────────────────────────────────────────────────────

class _FontPreviewBanner extends StatelessWidget {
  const _FontPreviewBanner({required this.activeTheme});
  final ThemeStruct activeTheme;

  @override
  Widget build(BuildContext context) {
    return Container(
      padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 10),
      decoration: BoxDecoration(
        color: context.theme.colorScheme.surfaceContainerHighest.withValues(alpha: 0.4),
        borderRadius: BorderRadius.circular(8),
      ),
      child: Text(
        "The quick brown fox jumps over the lazy dog. 0123456789",
        style: activeTheme.data.textTheme.bodyMedium?.copyWith(
          color: context.theme.colorScheme.onSurface,
        ),
      ),
    );
  }
}

// ─── Size preset row ──────────────────────────────────────────────────────────

class _SizePresetRow extends StatelessWidget {
  const _SizePresetRow({
    required this.label,
    required this.currentMultiplier,
    required this.onChanged,
  });

  final String label;
  final double currentMultiplier;
  final ValueChanged<double> onChanged;

  static const Map<String, double> _presets = {
    'Small': 0.85,
    'Normal': 1.0,
    'Large': 1.15,
    'XL': 1.30,
  };

  String get _selected {
    for (final entry in _presets.entries) {
      if ((currentMultiplier - entry.value).abs() < 0.02) return entry.key;
    }
    return 'Normal';
  }

  @override
  Widget build(BuildContext context) {
    final selected = _selected;
    return Padding(
      padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 8),
      child: Row(
        children: [
          SizedBox(
            width: 90,
            child: Text(
              label,
              style: context.theme.textTheme.bodyMedium,
              overflow: TextOverflow.ellipsis,
            ),
          ),
          Expanded(
            child: SegmentedButton<String>(
              segments: _presets.keys.map((k) => ButtonSegment<String>(value: k, label: Text(k))).toList(),
              selected: {selected},
              onSelectionChanged: (s) => onChanged(_presets[s.first]!),
              style: SegmentedButton.styleFrom(
                visualDensity: VisualDensity.compact,
                textStyle: const TextStyle(fontSize: 12),
              ),
              showSelectedIcon: false,
            ),
          ),
        ],
      ),
    );
  }
}

// (removed _StyleSlider — replaced by _SizePresetRow above)

// ─── Font picker (lazy — avoids building 1000+ DropdownMenuItems at render) ───

/// File-level cache: GoogleFonts.asMap() is called at most once per session.
List<String>? _cachedFontNames;
List<String> _getFontNames() => _cachedFontNames ??= ['Default', ...GoogleFonts.asMap().keys];

class _FontPickerTile extends StatelessWidget {
  const _FontPickerTile({
    required this.currentFont,
    required this.editable,
    required this.onChanged,
  });

  final String currentFont;
  final bool editable;
  final void Function(String) onChanged;

  void _openSheet(BuildContext context) {
    showModalBottomSheet<void>(
      context: context,
      backgroundColor: Theme.of(context).colorScheme.surfaceContainerHighest,
      isScrollControlled: true,
      builder: (_) => _FontPickerSheet(
        currentFont: currentFont,
        onSelected: onChanged,
      ),
    );
  }

  @override
  Widget build(BuildContext context) {
    return Padding(
      padding: const EdgeInsets.all(16),
      child: Row(
        children: [
          Text('Font Family', style: context.theme.textTheme.bodyLarge),
          const SizedBox(width: 15),
          Expanded(
            child: GestureDetector(
              onTap: editable ? () => _openSheet(context) : null,
              child: Opacity(
                opacity: editable ? 1.0 : 0.5,
                child: Container(
                  padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 8),
                  decoration: BoxDecoration(
                    borderRadius: BorderRadius.circular(8),
                    color: context.headerColor,
                  ),
                  child: Row(
                    children: [
                      Expanded(
                        child: Text(
                          currentFont,
                          style: context.theme.textTheme.bodyLarge,
                          overflow: TextOverflow.ellipsis,
                        ),
                      ),
                      Icon(Icons.arrow_drop_down, color: context.theme.textTheme.bodyLarge?.color),
                    ],
                  ),
                ),
              ),
            ),
          ),
        ],
      ),
    );
  }
}

class _FontPickerSheet extends StatefulWidget {
  const _FontPickerSheet({required this.currentFont, required this.onSelected});

  final String currentFont;
  final void Function(String) onSelected;

  @override
  State<_FontPickerSheet> createState() => _FontPickerSheetState();
}

class _FontPickerSheetState extends State<_FontPickerSheet> {
  late List<String> _filtered;
  final _searchCtrl = TextEditingController();

  @override
  void initState() {
    super.initState();
    _filtered = _getFontNames();
    _searchCtrl.addListener(_onSearch);
  }

  void _onSearch() {
    final q = _searchCtrl.text.toLowerCase();
    setState(() {
      _filtered = q.isEmpty ? _getFontNames() : _getFontNames().where((f) => f.toLowerCase().contains(q)).toList();
    });
  }

  @override
  void dispose() {
    _searchCtrl.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    return DraggableScrollableSheet(
      initialChildSize: 0.6,
      minChildSize: 0.4,
      maxChildSize: 0.9,
      expand: false,
      builder: (_, scrollCtrl) => Column(
        children: [
          Padding(
            padding: const EdgeInsets.fromLTRB(16, 16, 16, 8),
            child: TextField(
              controller: _searchCtrl,
              autofocus: false,
              decoration: InputDecoration(
                hintText: 'Search fonts…',
                prefixIcon: const Icon(Icons.search),
                border: OutlineInputBorder(borderRadius: BorderRadius.circular(8)),
                contentPadding: const EdgeInsets.symmetric(vertical: 10),
              ),
            ),
          ),
          Expanded(
            child: ListView.builder(
              controller: scrollCtrl,
              itemCount: _filtered.length,
              itemBuilder: (_, i) {
                final font = _filtered[i];
                final selected = font == widget.currentFont;
                return ListTile(
                  dense: true,
                  title: Text(font, style: context.theme.textTheme.bodyMedium),
                  selected: selected,
                  selectedTileColor: context.theme.colorScheme.primaryContainer.withValues(alpha: 0.35),
                  trailing: selected ? Icon(Icons.check, color: context.theme.colorScheme.primary) : null,
                  onTap: () {
                    Navigator.of(context).pop();
                    widget.onSelected(font);
                  },
                );
              },
            ),
          ),
        ],
      ),
    );
  }
}

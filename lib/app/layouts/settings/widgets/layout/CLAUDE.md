# settings/widgets/layout/ — Settings Page Layout Helpers

Structural widgets used as building blocks inside every settings panel to maintain consistent spacing and visual hierarchy.

## Files

| File | Purpose |
|------|---------|
| `settings_scaffold.dart` | Wraps a settings page with the correct app bar, background color, and scroll container |
| `settings_header.dart` | Section header label with uppercase tracking (e.g., "NOTIFICATIONS") |
| `settings_divider.dart` | Horizontal rule between sections |
| `settings_section.dart` | Rounded card container that groups a set of setting tiles |

## Usage
Always use these instead of raw `Scaffold`, `ListTile`, or `Container` in settings pages. `settings_section.dart` + `settings_header.dart` is the canonical section pattern.

## Related
- Settings tile variants: `../tiles/CLAUDE.md`
- Content widgets: `../content/CLAUDE.md`

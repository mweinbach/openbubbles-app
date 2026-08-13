# settings/pages/theming/ — Theme & Visual Customization

Settings panels for all visual customization: color schemes, gradients, fonts, avatars, and the full Theme Studio editor.

## Files & Subdirectories

| Path | Purpose |
|------|---------|
| `theming_panel.dart` | Entry point — top-level theme settings (skin selector, preset themes, dark mode) |
| `advanced/` | Font picker, gradient editor, custom color tokens → `CLAUDE.md` below |
| `avatar/` | Custom avatar color + image settings → `CLAUDE.md` below |
| `background/` | Chat background image crop and selection |
| `theme_studio/` | Full WYSIWYG theme editor → `CLAUDE.md` below |

### `advanced/`

| File | Purpose |
|------|---------|
| `advanced_theming_panel.dart` | Entry panel for font, color token, and gradient overrides |
| `advanced_theming_content.dart` | Content widget shared between panel variants |

### `avatar/`

| File | Purpose |
|------|---------|
| `custom_avatar_panel.dart` | Lets user set a custom avatar image for a contact |
| `custom_avatar_color_panel.dart` | Color picker for custom avatar background color |
| `avatar_crop.dart` | Image crop UI for avatar photo selection |

### `background/`

| File | Purpose |
|------|---------|
| `background_crop.dart` | Image crop UI for chat background photo selection |

### `theme_studio/`

| File | Purpose |
|------|---------|
| `theme_studio_panel.dart` | Main Theme Studio editor panel |
| `widgets/preset_theme_actions.dart` | Shared preset-theme constants + name validation helpers |
| `widgets/preset_theme_dialogs.dart` | Preset-theme dialog/menu orchestration (clone/rename/delete/create) |
| `widgets/color_editor_section.dart` | Section that groups color token editors |
| `widgets/color_editor_tile.dart` | Single editable color token row |
| `widgets/preset_theme_strip.dart` | Horizontal scrollable strip of preset themes |
| `widgets/theme_management_section.dart` | Save/load/delete/share theme actions |
| `widgets/theme_preview_card.dart` | Live preview card showing theme applied to a mock message |
| `widgets/typography_editor.dart` | Font family + size editor section |

Theme studio note: keep card/swatch visual rendering in widget files, and keep reusable preset-theme action validation or dialog orchestration in `preset_theme_actions.dart` / `preset_theme_dialogs.dart`.

## Related
- Theme service: `lib/services/ui/theme/themes_service.dart`
- Theme entity: `lib/database/io/theme.dart`
- Settings router: `../CLAUDE.md`

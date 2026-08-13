# conversation_list/widgets/header/ — Chat List Headers (Tri-Platform)

Platform-specific header widgets for the conversation list screen. Each implements the same logical header (search button, title, action icons) adapted to its design language.

## Files

| File | Purpose |
|------|---------|
| `cupertino_header.dart` | iOS-style large title + inline search (collapses on scroll) |
| `material_header.dart` | Material Design app bar with search + compose icons |
| `samsung_header.dart` | Samsung One UI header with bottom-aligned navigation |
| `header_widgets.dart` | Shared sub-widgets used by all three headers (search icon, compose button, unread badge, etc.) |

## Platform Selection
`ThemeSwitcher` in the parent list page routes to the correct header. Do not add platform checks inside these files.

## Related
- Parent list page: `../CLAUDE.md` (conversation_list)
- Footer (Samsung only): `../footer/CLAUDE.md`

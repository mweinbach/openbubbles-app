# widgets/search/ — Settings Search UI

Implements the settings search feature — finding settings panels by keyword across all settings pages.

## Files

| File | Purpose |
|------|---------|
| `settings_search_bar.dart` | Search input with 500ms debounce; skin-aware (dispatches to iOS variant) |
| `settings_search_bar_ios.dart` | iOS Cupertino-style search bar |
| `searchable_setting_item.dart` | Individual search result row |
| `settings_items_list.dart` | List container for search results and settings sections |
| `settings_items_actions.dart` | Side-effect handlers extracted from list-building UI (export contacts, review, external links) |
| `settings_search_breadcrumb_tile.dart` | Navigation breadcrumb showing the settings hierarchy path |
| `settings_search_empty_result.dart` | Empty state widget when no results match |

## How It Works

1. `SettingsSearchBar` fires `onChanged(String)` after a 500ms debounce when the query is ≥ 3 characters.
2. The parent settings page filters its registered `SearchableSettingItem` list against the query.
3. `SettingsItemsList` renders the filtered results with breadcrumb navigation.
4. Tapping a result navigates to the relevant settings panel page.

## Refactor Notes
- Keep large section declarations in `settings_items_list.dart`, but move side-effectful handlers (`onTap` network/file/external-link flows) into `settings_items_actions.dart`.
- Promote repeated labels/search tags/breadcrumb strings into shared constants when adding new sections.

## Making a Setting Searchable

When adding a new setting, register it as a `SearchableSettingItem` with:
- `title` — display label matching the tile title
- `description` — optional subtitle for better matching
- `breadcrumb` — the settings path (e.g., `"Settings > Advanced > Private API"`)
- `destination` — the widget to navigate to on tap

# lib/helpers/ — Cross-Cutting Utilities

Import via barrel: `package:bluebubbles/helpers/helpers.dart` (re-exports everything below).

## UI Helpers (`ui/`) → `ui/CLAUDE.md`
- `ui_helpers.dart` — general UI utilities (back button, safe area, etc.)
- `theme_helpers.dart` — `ThemeHelpers` mixin; mixed into `CustomState`; provides `iOS`, `material`, `samsung` skin booleans
- `message_widget_helpers.dart` — message-specific UI utilities
- `reaction_helpers.dart` — tapback emoji display helpers
- `attributed_body_helpers.dart` — renders `AttributedBody` rich text (bold, italic, mention, link)
- `facetime_helpers.dart` — FaceTime link detection and launch
- `oauth_helpers.dart` — OAuth flow UI (open browser, capture callback)
- `async_task.dart` — `AsyncTask` wrapper for cancellable async work

## Type Helpers (`types/`)
- `constants.dart` — `effectMap` (iMessage effect name → Apple code), `stringToMessageEffect`
- `extensions/extensions.dart` — extension methods on `String`, `DateTime`, `Color`, `List`, `int`, etc.
- `helpers/` → `helpers/types/helpers/CLAUDE.md` — date, string, message, contact, misc utilities

## Network Helpers (`network/`) → `network/CLAUDE.md`
- `network_helpers.dart` — HTTP utility functions
- `network_tasks.dart` — async network operations
- `network_error_handler.dart` — classifies and surfaces network errors to UI
- `metadata_helper.dart` — URL metadata / Open Graph extraction for link previews

## Backend Helpers (`backend/`) → `backend/CLAUDE.md`
- `settings_helpers.dart` — settings read/write shortcuts
- `foreground_service_helpers.dart` — Android foreground service start/stop control
- `startup_tasks.dart` — ordered app initialization task runner
- `sync/sync_helpers.dart` — sync coordination utilities

## Key Routings
- Message effect names → Apple codes: `helpers/types/constants.dart`
- Date/time formatting: `helpers/types/helpers/date_helpers.dart`
- Rich text rendering: `helpers/ui/attributed_body_helpers.dart`
- Notification text: `helpers/types/helpers/message_helper.dart` → `getNotificationText()`

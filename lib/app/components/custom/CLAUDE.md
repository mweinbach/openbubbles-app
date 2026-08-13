# app/components/custom/ — Custom Flutter Widget Overrides

Override or extend built-in Flutter widgets to fix platform-specific issues or add BlueBubbles-specific behavior.

## Files

| File | Purpose |
|------|---------|
| `custom_bouncing_scroll_physics.dart` | Tweaked `BouncingScrollPhysics` with reduced overscroll; prevents excessive bounce on desktop |
| `custom_cupertino_alert_dialog.dart` | `CupertinoAlertDialog` wrapper that fixes text-scaling issues and adds dark/light mode theming |
| `custom_cupertino_page_transition.dart` | Custom page route transition that matches iOS slide behavior while allowing back-swipe on Android |
| `custom_error_box.dart` | Standardized error display widget (red border, icon, message) used across the app for non-fatal display errors |

## Rules
Use these instead of the stock Flutter equivalents where they exist. Do not create additional custom widget overrides here unless the stock widget has a genuine bug or missing behavior.

# macos/ — macOS Native (Minimal Custom Code)

Custom Swift:
- `Runner/AppDelegate.swift` — sets `applicationShouldTerminateAfterLastWindowClosed = true` (app quits when main window closes)
- `Runner/MainFlutterWindow.swift` — window initialization

All macOS behavior is plugin-driven. macOS-specific Flutter code lives in:
- `lib/utils/window_effects.dart` — Mica/acrylic transparency (`flutter_acrylic`)
- `lib/app/wrappers/titlebar_wrapper.dart` — custom window chrome (`bitsdojo_window`)
- `lib/app/wrappers/trackpad_bug_wrapper.dart` — trackpad two-finger scroll fix

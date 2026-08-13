# lib/app/layouts/startup/ — App Boot Screens

Two screens shown before the main app UI is ready.

## Files

| File | Purpose |
|------|---------|
| `splash_screen.dart` | Initial loading screen shown while services initialize; transitions to either setup or conversation list |
| `failure_to_start.dart` | Error screen shown if a fatal startup failure occurs (DB init failed, etc.) |

## Flow
`SplashScreen` → checks server connection + DB init → routes to `SetupView` (first launch) or `ConversationList` (returning user).
`FailureToStart` → terminal state; shows error message and a "retry" or "reset" action.

## Related
- Server connection setup: `lib/app/layouts/setup/` → `CLAUDE.md` inside
- Backend init sequence: `lib/helpers/backend/startup_tasks.dart`

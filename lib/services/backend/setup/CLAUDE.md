# services/backend/setup/ — App Setup Service

## File
`setup_service.dart` — `SetupService` (GetX singleton via `SetupSvc` shorthand)

## Responsibilities
- Orchestrates the first-run server connection flow
- Validates server URL, tests connectivity, and stores credentials
- Triggers the initial full data sync after a successful connection
- Tracks setup progress state so `SetupView` can show step-by-step progress UI

## Usage
Called exclusively from `lib/app/layouts/setup/` during onboarding.
Do not call `SetupService` after initial setup is complete — use `ServerInterface` for server operations at runtime.

## Related
- UI: `lib/app/layouts/setup/CLAUDE.md`
- Full sync: `lib/services/backend/sync/full_sync_manager.dart`
- Server HTTP calls: `lib/services/network/http_service.dart`

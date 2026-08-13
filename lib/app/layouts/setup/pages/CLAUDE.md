# setup/pages/ — Setup Step Pages

Individual step-pages shown in sequence during the first-run server connection setup flow.

## Subdirectories

| Directory | File(s) | Step |
|-----------|---------|------|
| `welcome/` | `welcome_page.dart` | Introduction screen with "Get Started" |
| `permissions/` | `request_permissions.dart` | Request notification + battery optimization permissions |
| `setup_checks/` | `battery_optimization.dart`, `mac_setup_check.dart` | Platform-specific requirement checks |
| `sync/` | `qr_code_scanner.dart`, `server_credentials.dart`, `sync_progress.dart`, `sync_settings.dart` | Server URL entry, QR scan, sync options, progress display |

## Template
`page_template.dart` (at `pages/` level) — base layout (title, subtitle, back/next buttons) mixed into each step page.

## Flow
`welcome` → `permissions` → `setup_checks` → `sync` (credentials → progress)

`SetupService` drives transitions between steps.

## Related
- Setup service: `lib/services/backend/setup/CLAUDE.md`
- Setup view: `../CLAUDE.md` (setup)

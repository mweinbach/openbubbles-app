# settings/ — App Settings

## Page Categories (`pages/`) → `pages/CLAUDE.md`
| Directory | Content |
|-----------|---------|
| `theming/` | Theme picker; `advanced/` for color customization; `avatar/` for avatar colors |
| `message_view/` | Message display preferences |
| `conversation_list/` | Chat list display preferences |
| `server/` | Server connection, backup/restore, OAuth |
| `system/` | Notifications, permissions |
| `profile/` | User profile |
| `scheduling/` | Scheduled messages and reminders |
| `advanced/` | Private API, Firebase, Tasker, Redacted mode, UnifiedPush |
| `misc/` | Logging, troubleshoot, about |
| `desktop/` | Desktop-specific options |

## Reusable Widgets (`widgets/`)
- `tiles/` — preference tile components (primary building block)
- `layout/` — page layout containers
- `content/` — **core building blocks** (`SettingsTile`, `SettingsSwitch`, `SettingsOptions`, `SettingsSlider`) → CLAUDE.md inside
- `search/` — settings search UI → CLAUDE.md inside

## Adding a New Setting
1. Add field to `lib/database/global/settings.dart`
2. Add tile in the appropriate `pages/*/` file using widgets from `widgets/tiles/`

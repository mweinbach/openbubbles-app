# settings/pages/scheduling/ — Scheduled Messages & Reminders

UI for creating, viewing, and managing scheduled messages and message reminders.

## Files

| File | Purpose |
|------|---------|
| `scheduled_messages_panel.dart` | Entry point — routes to platform variant |
| `cupertino_scheduled_messages_panel.dart` | iOS-skin scheduled messages list |
| `material_scheduled_messages_panel.dart` | Material-skin scheduled messages list |
| `samsung_scheduled_messages_panel.dart` | Samsung-skin scheduled messages list |
| `scheduled_messages_mixin.dart` | Shared logic mixin for all three list variants |
| `create_scheduled_panel.dart` | Entry point for creating a new scheduled message — routes to platform variant |
| `cupertino_create_scheduled_panel.dart` | iOS-skin scheduled message creator |
| `material_create_scheduled_panel.dart` | Material-skin scheduled message creator |
| `samsung_create_scheduled_panel.dart` | Samsung-skin scheduled message creator |
| `create_scheduled_mixin.dart` | Shared creation logic (date picker, recurrence, send) |
| `message_reminders_panel.dart` | Separate panel for message reminders (nudge at a time, not a full schedule) |

## Related
- Backend scheduling model: `lib/database/global/scheduled_message.dart`
- Settings router: `../CLAUDE.md`

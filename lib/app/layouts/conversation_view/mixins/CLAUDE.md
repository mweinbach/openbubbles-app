# conversation_view/mixins/ — Conversation View Mixins

## Files
| File | Purpose |
|------|---------|
| `messages_service_mixin.dart` | Mixed into `MessagesView`; wires up `MessagesService` init/dispose and provides `newFunc`, `updateFunc`, `removeFunc`, and `jumpToMessage` callbacks that `MessagesService` calls when the message list changes |

## Rules
This mixin is the **only** place where message list callbacks are registered. Do not subscribe to message events directly in widget `initState` — use this mixin instead.

## Related
- `MessagesService`: `lib/services/ui/message/messages_service.dart`
- `MessagesView`: `../pages/messages_view.dart`

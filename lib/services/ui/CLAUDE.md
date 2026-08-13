# services/ui/ — UI State Services

All are GetX singletons. Shorthand getters live in `lib/services/services.dart`.

## Chat → `chat/CLAUDE.md`
- `chat/chats_service.dart` (`ChatsSvc`) — sorted chat list, unread count, `ChatState` map, active chat tracking; loads in batches of 100
- `chat/conversation_view_controller.dart` — state for the currently open conversation (text, attachments, reply, scroll position)

## Messages → `message/CLAUDE.md`
- `message/messages_service.dart` (`MessagesSvc`) — per-chat service tagged by GUID; owns `MessageState` map for granular widget reactivity
- `message/message_widget_controller.dart` — per-message reactive state cached by message GUID

## Contacts
- `contact_service.dart` — legacy V1; avoid for new code
- `contact_service_v2.dart` (`ContactsSvcV2`) — desktop sync (requires server v42+)

## Other
- `theme/themes_service.dart` (`ThemeSvc`) — theme switching, custom theme management, preset themes
- `navigator/navigator_service.dart` (`NavigationSvc`) — GetX-based app routing; always use this over `Navigator.of(context)` directly
- `attachments_service.dart` — tracks file attachments in the composer + send progress state
- `unifiedpush.dart` — push notification provider abstraction (UnifiedPush protocol)

## Key Separation Rule
`ChatState` / `MessageState` (in `lib/app/state/`) are what widgets **read**.
`ChatsService` / `MessagesService` call `updateXxxInternal()` on state — **widgets never write state directly**.

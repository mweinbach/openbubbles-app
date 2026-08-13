# conversation_view/widgets/header/ — Conversation Header Bar

Three files. The header follows the standard skin pattern — each skin has its own implementation sharing the same controller.

## Files
| File | Contents |
|------|---------|
| `cupertino_header.dart` | iOS-skin header (title, back button, avatar, call button) |
| `material_header.dart` | Material-skin header |
| `header_widgets.dart` | Shared sub-widgets used across skins — `ManualMark` (private API read receipt button), connection status indicator, and other reusable pieces |

## Skin Pattern

The parent `ConversationView` page uses `ThemeSwitcher` to route to the correct header:
```dart
ThemeSwitcher(
  iOSSkin:      CupertinoHeader(controller: controller),
  materialSkin: MaterialHeader(controller: controller),
  samsungSkin:  MaterialHeader(controller: controller),  // Samsung reuses Material header
)
```

All three variants receive the same `ConversationViewController`. Header state (chat title, avatar, typing indicator) is read from the controller or directly from `ChatState`.

## Shared Widgets (`header_widgets.dart`)

**`ManualMark`** — appears when Private API is enabled and the conversation is open. Lets the user manually mark the chat as read on the server. Visibility is gated on `SettingsSvc.settings.enablePrivateAPI.value`.

When adding new header features, add shared logic or sub-widgets to `header_widgets.dart` and reference them from the skin-specific files rather than duplicating.

## Related
- Controller: `lib/services/ui/chat/conversation_view_controller.dart`
- Chat reactive state: `lib/app/state/chat_state.dart`
- Parent page: `lib/app/layouts/conversation_view/pages/conversation_view.dart`

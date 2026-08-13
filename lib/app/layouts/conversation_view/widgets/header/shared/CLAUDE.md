# conversation_view/widgets/header/shared/ — Shared Header Logic

Utilities shared across all three platform-specific conversation header variants (cupertino, material, samsung).

## Files

| File | Purpose |
|------|---------|
| `chat_title_mixin.dart` | Provides `chatTitle`, `chatSubtitle`, and contact-name resolution logic; mixed into all three header widgets so they compute the same display name without duplication |
| `header_components.dart` | Shared sub-widgets: back button, video/audio call buttons, details-page navigation icon, connection status badge |

## Related
- Platform header variants: `../cupertino_header.dart`, `../material_header.dart`, `../samsung_header.dart`
- Contact name resolution: `lib/services/ui/contact_service_v2.dart`
- `ChatState.title`: `lib/app/state/chat_state.dart`

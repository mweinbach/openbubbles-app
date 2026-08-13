# widgets/message/ — Message Rendering (54+ files)

A message renders as a composition of specialized sub-widgets.

## Component Routing
| Directory | Renders | Details |
|-----------|---------|---------|
| `message_holder/` | Outer bubble, alignment, tail, sender name | → CLAUDE.md inside |
| `text/` | Text content inside bubble | |
| `attachment/` | Images, video, audio, stickers, contact cards | → CLAUDE.md inside |
| `reaction/` | Tapback emoji display and picker | → CLAUDE.md inside |
| `reply/` | Quoted reply bubble and reply line | → CLAUDE.md inside |
| `timestamp/` | Delivery status, read receipts, date separators | → CLAUDE.md inside |
| `typing/` | Typing indicator | |
| `popup/` | Long-press context menu / action sheet | → CLAUDE.md inside |
| `interactive/` | Apple Pay, Game Pigeon, URL previews, maps, embedded media | → CLAUDE.md inside |
| `chat_event/` | System messages (member added, subject changed) | |
| `effects/` | Send effect overlays (balloon, confetti, etc.) | → CLAUDE.md inside |
| `misc/` | Message editing, selection, swipe-to-reply dispatcher | → CLAUDE.md inside |
| `parts/` | Per-part-type renderers (a message can have multiple parts) | |
| `shared/` | Shared utilities across message widgets | |

## Related
- Reactive state: `lib/app/state/message_state.dart`
- DB model: `lib/database/io/message.dart`
- Animations: `lib/app/animations/`
- Popup action handlers: `popup/actions/` (menu behavior extracted from `popup/message_popup.dart`)

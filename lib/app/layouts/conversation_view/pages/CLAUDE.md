# conversation_view/pages/ — Top-Level Page Widgets

Two entry-point widgets for the conversation screen.

## Files

| File | Purpose |
|------|---------|
| `conversation_view.dart` | Outer container; sets up `ConversationViewController`, handles routing, manages keyboard/layout |
| `messages_view.dart` | Scrollable message list; owns `CustomScrollView`, loads messages in pages, triggers `MessagesService` |

## Relationship
`ConversationView` renders `MessagesView` as its body. `MessagesView` does **not** create the controller — it receives it from the parent via `ConversationViewController`.

## Key Controllers
- `ConversationViewController` (from `lib/services/ui/chat/conversation_view_controller.dart`) — owns text field state, reply context, attachment drafts, scroll position
- `MessagesService` (tagged by chat GUID) — owns the message data and `MessageState` map

## Related
- Full widget tree overview: `../CLAUDE.md` (conversation_view)
- Message rendering: `../widgets/message/CLAUDE.md`
- Text field: `../widgets/text_field/CLAUDE.md`

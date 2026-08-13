# widgets/message/text/ — Text Bubble Renderer

## Files
| File | Purpose |
|------|---------|
| `text_bubble.dart` | Renders the text content for a single message part; handles plain text, rich text (attributed body), mentions, and edit-unsent styling |

## How Text is Rendered
1. If `part.attributedBody` is non-null → `AttributedBodyHelpers.buildAttributedText()` for rich text (bold/italic/mentions/links)
2. Otherwise → plain `SelectableText` with `TextStyle` from `context.theme`

## Edit / Unsent States
- Edited messages show strikethrough on the original text part
- Unsent parts show "Unsent" label in muted style

## Related
- Attributed body helper: `lib/helpers/ui/attributed_body_helpers.dart`
- Message part model: `lib/database/global/message_part.dart`
- Parent dispatcher: `../message_holder.dart` → `MessagePartContent`

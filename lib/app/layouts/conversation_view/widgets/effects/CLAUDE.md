# conversation_view/widgets/effects/ — Send Effect UI

UI for selecting and displaying iMessage send effects (balloon, confetti, echo, etc.).

## Files

| File | Purpose |
|------|---------|
| `screen_effects_widget.dart` | Full-screen animated overlay that plays a send effect (rendered on top of the conversation) |
| `send_effect_picker.dart` | Picker sheet that lists available effects; lets user choose before sending |

## Effect Definitions
The animation classes and renderers live in `lib/app/animations/` → `CLAUDE.md` inside.
Effect name ↔ Apple code mapping: `lib/helpers/types/constants.dart` (`effectMap`).

## Trigger
Effect playback is triggered from `MessagesView` when an incoming or outgoing message has a non-null `expressiveSendStyleId`.

## Related
- Animation renderers: `lib/app/animations/CLAUDE.md`
- Send flow: `docs/MESSAGE_SEND_FLOW.md`

# widgets/message/typing/ — Typing Indicator

## Files
| File | Purpose |
|------|---------|
| `typing_indicator.dart` | Animated three-dot typing bubble shown when a participant is composing; driven by socket events |
| `typing_clipper.dart` | `CustomClipper` that shapes the typing bubble's rounded tail |

## Lifecycle
`TypingIndicator` appears at the bottom of the message list when `ChatState.showTypingIndicator` is true.
The `ChatsService` toggles this flag when the server sends a `typing` socket event.

## Animation
Uses a staggered `AnimationController` to bounce each of the three dots at offset intervals. Auto-disposes when hidden.

## Related
- `ChatState.showTypingIndicator`: `lib/app/state/chat_state.dart`
- Socket events: `lib/services/network/socket_service.dart`

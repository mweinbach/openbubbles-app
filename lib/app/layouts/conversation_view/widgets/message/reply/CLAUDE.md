# reply/ — Quoted Reply Bubbles & Thread UI

Displays reply threading: the quoted preview above a message and the thread connector lines (iOS only).

## Files

| File | Purpose |
|------|---------|
| `reply_bubble.dart` | Quoted reply preview widget (shows target message's text/attachment snippet) |
| `reply_line_painter.dart` | `CustomPainter` that draws the vertical thread connector line (iOS only) |
| `reply_thread_popup.dart` | Full-screen modal showing all messages in a reply thread |

## How `ReplyBubble` Works

- Reads `controller.messageState?.text` reactively so the quoted text updates if the original was edited.
- Shows: sender name, content snippet (text or attachment icon), and a mini attachment thumbnail if applicable.
- Tap → scrolls the conversation list to the original message (via `cvController.scrollToMessage(replyTo)`).

## Thread Lines (iOS)

`ReplyLineDecoration` wraps `CustomPaint(_ReplyLinePainter)` to draw a vertical line connecting a series of replies. Only rendered when `iOS == true` and `message.threadOriginatorGuid != null`.

## Thread Popup

`ReplyThreadPopup` is a bottom sheet / modal that shows all messages with the same `threadOriginatorGuid`. Opened from `MessageProperties` when the user taps the reply-count badge.

## Placement in MessageHolder

`ReplyBubbleSection` (in `message_holder/`) positions the reply bubble:
- **iOS**: above the message bubble
- **Material**: inline as part of the column (slightly indented)

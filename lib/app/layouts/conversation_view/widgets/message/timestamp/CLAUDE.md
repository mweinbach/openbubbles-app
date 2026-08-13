# timestamp/ — Delivery Status & Date Separators

Shows when a message was sent and its delivery/read status.

## Files

| File | Purpose |
|------|---------|
| `message_timestamp.dart` | Timestamp label (iOS: slides in from right on tap; Samsung: always visible) |
| `delivered_indicator.dart` | Delivery status checkmarks (sent / delivered / read) |
| `timestamp_separator.dart` | Full-width date header between messages from different days ("Today", "Yesterday", etc.) |

## Skin Behavior

| Skin | Timestamp visibility | Delivered indicator |
|------|---------------------|---------------------|
| iOS | Slides in from right on tap | Shown below the last sent message |
| Material | Shown inline in message row | Shown below the message |
| Samsung | Always visible (left of bubble) via `SamsungTimestampObserver` | Shown below the message |

## Reactivity

```dart
Obx(() {
  final dateCreated = controller.messageState?.dateCreated.value ?? message.dateCreated;
  // ... render timestamp
})
```

Reactive so edit timestamps update in real time.

## Date Separator

`TimestampSeparator` is placed at the top of the `MessageHolder` column. It compares `message.dateCreated` to `oldMessage.dateCreated`. If different calendar day → renders the full-width separator. The text is formatted via `buildDate()` from `lib/helpers/types/helpers/`.

## Delivered Indicator

`DeliveredIndicator` only renders for the last sent message in a run of consecutive sent messages. Uses `MessageState.isDelivered`, `isRead`, and `isSending` to choose the icon/label.

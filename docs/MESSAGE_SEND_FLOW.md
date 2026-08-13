# Message Send Flow

End-to-end flow for an outbound message: from the user tapping Send through the API call, the race between the socket response and the HTTP response, and the tempGuid → realGuid swap that merges them.

For the inbound half of this flow (after the server echoes the message back), see `docs/MESSAGE_RECEIVE_FLOW.md`.

---

## High-Level Overview

```
UI (send button tap)
  → build Message with tempGuid
  → OutgoingMessageHandler.queue() (serial, one at a time)
      → prepMessage: save to DB with tempGuid (appears in UI as "sending")
      → sendMessage: fire HTTP POST; register send progress tracker
          ↓ (concurrent)
    ┌─────────────────────┬──────────────────────────┐
    │ Path A              │ Path B                   │
    │ Socket/Firebase     │ HTTP response             │
    │ fires first         │ arrives first             │
    └─────────────────────┴──────────────────────────┘
          ↓ (both paths converge here)
      _matchMessageWithExisting(tempGuid, realMessage)
        → delete temp record, upsert real record
        → MessagesService.updateMessage(real, oldGuid: temp)
          → MessageState: isSending → false, isSent → true
          → UI rebuilds (bubble stops animating)
```

---

## Step-by-Step Detail

### Step 1 — User Taps Send (`send_button.dart`)

The Send button calls the `sendMessage` callback passed down from `ConversationTextField`. Validation runs (non-empty content, setup complete, etc.), then delegates to `controller.send()` (`conversation_view_controller.dart`), which calls the `sendFunc` registered by `SendAnimation`.

**Key file:** `lib/app/layouts/conversation_view/widgets/text_field/send_button.dart`

---

### Step 2 — Build Message Objects with tempGuid (`send_animation.dart`)

`SendAnimation.send()` constructs one `Message` per attachment and one for the text body (if non-empty).

**For each message:**
```dart
message.generateTempGuid();  // sets guid = "temp-XXXXXXXX" (8 random chars)
```

The tempGuid format is always `temp-` followed by 8 random alphanumeric characters (e.g. `temp-a7d3k9m2`). This prefix is how the app detects "sending" state: `message.isSending == guid.startsWith("temp")`.

Each message is then wrapped in a typed outgoing queue item and pushed to `OutgoingMessageHandler`:
```dart
OutgoingMsgHandler.queue(OutgoingMessage(
  chat: controller.chat,
  message: message,              // has tempGuid at this point
));
```

Queue item types are now explicit and compile-time safe:

- `OutgoingMessage` for plain text send
- `OutgoingReaction` for tapbacks/reactions (with required `selected` + `reaction`)
- `OutgoingAttachment` for file/audio sends (with required `attachment` + `isAudioMessage`)
- `OutgoingMultipartMessage` for attributed/multipart sends

This replaced the previous `OutgoingItem` + `customArgs` map pattern.

**Key file:** `lib/app/layouts/conversation_view/widgets/message/send_animation.dart`

---

### Step 3 — OutgoingMessageHandler: Prepare (`outgoing_message_handler.dart`)

`OutgoingMessageHandler.queue()` calls `_prepItem()` before the item enters the serial queue. For text messages this calls `prepMessage()`; for files it calls `prepAttachment()`.

**`prepMessage()`** saves the message to ObjectBox **with its tempGuid** via `chat.addMessage(message)`. This is intentional — the message appears in the UI immediately in a "sending" state before the server has confirmed anything. After this call, the message exists in the DB and in `MessagesService` state with `isSending == true`.

For attachments, `prepAttachment()` additionally copies the file from its source path to the app's attachment directory and loads image metadata (dimensions, etc.) before the upload.

---

### Step 4 — OutgoingMessageHandler: Send (`outgoing_message_handler.dart`)

`OutgoingMessageHandler._processNext()` dequeues items one at a time and calls `_dispatchItem()`, which routes to `sendMessage()`, `sendMultipart()`, or `sendAttachment()` based on the concrete queue item type (`OutgoingMessage`, `OutgoingReaction`, `OutgoingMultipartMessage`, `OutgoingAttachment`).

Each send method uses `_sendWithRace()`, which does two things:

1. **Registers a send progress tracker** keyed by tempGuid:
   ```dart
   registerSendProgressTracker(tempGuid, chat, race);
   ```
   This is the mechanism that handles the race condition (see Step 6).

2. **Fires the HTTP call** and races the response against the socket echo. Both paths call `completeSendProgressIfExists(tempGuid)` when they resolve — whichever arrives first wins. The outgoing queue's `_handleSend()` wrapper unblocks as soon as either path completes.

The tempGuid is passed to the server as the `tempGuid` field in the request body. The server echoes it back in the "new-message" socket event so the client can correlate the two.

**Key file:** `lib/services/backend/outgoing_message_handler.dart`

---

### Step 5 — HTTP POST to Server (`http_service.dart`)

`HttpSvc.sendMessage()` makes the actual network request via `dio`:
```dart
await dio.post("$apiRoot/message/text", data: {
  "chatGuid": chatGuid,
  "tempGuid": tempGuid,   // so server can echo it in the socket event
  "message": text,
  "method": "private-api" | "apple-script",
  // + effectId, subject, selectedMessageGuid if applicable
});
```

For attachments, `sendAttachment()` uses `FormData` and `MultipartFile` with upload progress callbacks. For multipart (rich text with mentions), `sendMultipart()` sends a `parts` array.

All three use `runApiGuarded()` for consistent error handling.

The request is fire-and-forget from the queue's perspective — the `.then()` and `.catchError()` callbacks handle the result asynchronously.

**Key file:** `lib/services/network/http_service.dart`

---

### Step 6 — The Race: Two Paths to Confirmation

After the POST fires, two things can happen concurrently. Whichever arrives first wins and hands off to the other.

---

#### Path A — Socket/Firebase/Method Channel fires BEFORE the HTTP response

The server sends a "new-message" socket event (or Firebase push on Android) with the real GUID and the original `tempGuid` in the payload.

`SocketService` routes this to `MessageHandlerSvc.handleEvent("new-message", data)` (`action_handler.dart`). The handler checks the payload for `tempGuid`:

- **If `tempGuid` is present:** The real GUID and the tempGuid are known. `IncomingMsgHandler.handle()` is called with the `tempGuid` set, which eventually calls `OutgoingMsgHandler.completeSendProgressIfExists(tempGuid)`. This:
  - Removes the progress tracker from `OutgoingMessageHandler._sendProgressTrackers`
  - Completes the send progress animation (`chat.sendProgress.value = 1`)
  - Completes the `Completer`, unblocking `_sendWithRace()`
  - `IncomingMessageHandler` processes the payload (GUID swap) in its own queue

- **If `tempGuid` is null** (out-of-order event — the server sent the real GUID before the client registered the tracker): The real GUID is added to `MessageHandlerSvc.outOfOrderTempGuids` in `action_handler.dart`. The handler waits 500ms, then checks again. If the tracker arrived in the meantime, the swap proceeds normally; if not, the event is treated as a regular new message.

When `IncomingMessageHandler` processes the item, `_processNewMessage()` routes to `_processUpdatedMessage()` for the GUID swap (see `docs/MESSAGE_RECEIVE_FLOW.md`).

---

#### Path B — HTTP response arrives BEFORE the socket event

The `.then()` callback in `_sendWithRace()` fires with the server's response body, which contains the real `Message`:
```dart
completeSendProgressIfExists(tempGuid);   // removes tracker, finishes progress
await onSuccess(Message.fromMap(response.data['data']));
```

`onSuccess` calls `_matchMessageWithExisting()` directly. If the socket event has not yet arrived with the real GUID, the temp message is swapped for the real one here. If the socket event then arrives, `_matchMessageWithExisting()` sees that a message with the real GUID already exists and skips the swap.

---

### Step 7 — `_matchMessageWithExisting()`: The tempGuid → realGuid Swap

This private method on `OutgoingMessageHandler` handles both paths. It is safe to call from either.

**Logic:**
1. Look up a message with the **real GUID** in the DB.
   - **If found:** The socket event already completed the swap. If the HTTP response's message is newer (e.g. has a `dateDelivered`), replace it. Then if the tempGuid record still exists, delete it and call `MessagesSvc.updateMessage(real, oldGuid: tempGuid)` to re-key the state map.
   - **If not found:** The temp message is still the only record. Call `Message.replaceMessage(tempGuid, realMessage)` → `MessageInterface.replaceMessage()` → `MessageActions.replaceMessage()` in the GlobalIsolate → ObjectBox atomically renames the record's GUID. Then calls `MessagesSvc.updateMessage(real, oldGuid: tempGuid)`.

**Key file:** `lib/services/backend/outgoing_message_handler.dart`, `_matchMessageWithExisting()` method

---

### Step 8 — State Update and UI Rebuild

`MessagesService.updateMessage(realMessage, oldGuid: tempGuid)`:
1. Finds the `MessageState` keyed by `tempGuid`
2. Calls `messageState.updateFromMessage(realMessage)`:
   - `updateGuidInternal(realGuid)` — sets the GUID, auto-updates `isSending = false`, `isSent = true`
   - All other changed fields (`dateDelivered`, `error`, etc.) updated via their `*Internal()` methods
3. Re-keys the `messageStates` map: `messageStates.remove(tempGuid)` → `messageStates[realGuid] = state`
4. Increments `messageUpdateTrigger[realGuid]` — widgets watching this rebuild

The `Obx()` wrapper around `isSending` in the message bubble widget rebuilds and removes the "sending" animation. The `Obx()` wrapper around `dateDelivered` rebuilds to show the delivery timestamp when it arrives.

**Key files:**
- `lib/services/ui/message/messages_service.dart` — `updateMessage()`
- `lib/app/state/message_state.dart` — `updateGuidInternal()`, `updateFromMessage()`

---

### Step 9 — Error Path

If the HTTP call fails (`.catchError()` inside `_sendWithRace()`):
1. `completeSendProgressIfExists(tempGuid)` runs (clears the tracker)
2. `onError` is called, which invokes `handleSendError(error, message)` — sets `message.guid = guid.replace("temp", "error-...")` and `message.error = BAD_REQUEST`
3. `Message.replaceMessage(tempGuid, errorMessage)` persists the error state
4. If the app is backgrounded or the conversation is not active, a "Failed to send" local notification is created
5. The `Completer` completes with an error, which `OutgoingMessageHandler._processNext()` catches — if `cancelQueuedMessages` is enabled, all subsequent outgoing items for the same chat are cancelled

---

## Key Files at a Glance

| Step | File | Key Method |
|------|------|-----------|
| Send button | `lib/app/layouts/conversation_view/widgets/text_field/send_button.dart` | `onPressed` callback |
| Build message + tempGuid | `lib/app/layouts/conversation_view/widgets/message/send_animation.dart` | `send()`, `message.generateTempGuid()` |
| tempGuid generation | `lib/database/io/message.dart` | `generateTempGuid()` → `"temp-XXXXXXXX"` |
| Queue + prep + send | `lib/services/backend/outgoing_message_handler.dart` | `queue()`, `_prepItem()`, `_processNext()`, `_dispatchItem()` |
| Save to DB (temp) | `lib/services/backend/outgoing_message_handler.dart` | `prepMessage()` → `chat.addMessage()` |
| HTTP POST | `lib/services/network/http_service.dart` | `sendMessage()`, `sendMultipart()`, `sendAttachment()` |
| HTTP + socket race | `lib/services/backend/outgoing_message_handler.dart` | `_sendWithRace()` |
| Progress tracker | `lib/services/backend/outgoing_message_handler.dart` | `registerSendProgressTracker()`, `completeSendProgressIfExists()` |
| Out-of-order handling | `lib/services/backend/action_handler.dart` | `outOfOrderTempGuids`, 500ms grace period |
| tempGuid → realGuid swap | `lib/services/backend/outgoing_message_handler.dart` | `_matchMessageWithExisting()` |
| DB swap | `lib/database/io/message.dart` + interfaces | `replaceMessage()` → `MessageInterface.replaceMessage()` |
| State update | `lib/services/ui/message/messages_service.dart` | `updateMessage(real, oldGuid: temp)` |
| UI rebuild | `lib/app/state/message_state.dart` | `updateGuidInternal()` → `isSending = false` |

---

## Deduplication Guarantees

- **`IncomingMessageHandler._processedGuids`** — a rolling ring-buffer of the last 100 GUIDs processed by `IncomingMessageHandler`. Prevents the same socket/FCM event from being processed twice (e.g. if Firebase and socket both deliver it).
- **`_matchMessageWithExisting()` real-GUID-first check** — before swapping, always checks whether a message with the real GUID already exists. If it does, the swap is a no-op (or a metadata update only). This makes both Path A and Path B safe to call.
- **`_sendProgressTrackers` removal** — `completeSendProgressIfExists()` removes the tracker on first call. The second arrival (socket after HTTP, or HTTP after socket) finds no tracker and does nothing extra.
- **`outOfOrderTempGuids` with 500ms delay** — lives in `action_handler.dart`. Handles the edge case where the server emits "new-message" with a null `tempGuid` before the client has registered its tracker.

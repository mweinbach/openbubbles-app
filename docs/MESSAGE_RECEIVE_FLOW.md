# Message Receive Flow

End-to-end flow for an inbound message: from the server socket through the database to the reactive UI state.

For the outgoing half (user sends a message), see `docs/MESSAGE_SEND_FLOW.md`.

---

## High-Level Overview

```
Server WebSocket event: "new-message" / "updated-message"
  → SocketService
  → MessageHandlerSvc.handleEvent()  (action_handler.dart)
  → IncomingMessageHandler  (FIFO queue, configurable concurrency)
  → IncomingMessageHandler._processNewMessage() or _processUpdatedMessage()
  → chat.addMessage() → ChatInterface → GlobalIsolate → ChatActions (ObjectBox write)
  → hydrate ID → Message object
  → ChatsSvc.updateChat()       → ChatState.*Internal()     → Obx() rebuild
  → MessagesSvc.updateMessage() → MessageState.*Internal()  → Obx() rebuild
```

---

## Step-by-Step Detail

### Step 1 — Socket Receives Event (`socket_service.dart`)

The WebSocket connection maintained by `SocketService` registers listeners at startup:
```dart
socket?.on("new-message",     (data) => MessageHandlerSvc.handleEvent("new-message",     data, 'DartSocket'));
socket?.on("updated-message", (data) => MessageHandlerSvc.handleEvent("updated-message", data, 'DartSocket'));
```

No parsing or routing logic lives here — raw JSON is passed directly to `MessageHandlerSvc`. Firebase push and the Android method channel use the same `handleEvent()` entry point with a different `source` string.

**Key file:** `lib/services/network/socket_service.dart`

---

### Step 2 — Event Dispatch (`action_handler.dart`)

`MessageHandlerSvc` is a GetX singleton alias for `ActionHandler`. All incoming socket event routing lives here.

**`handleEvent(eventName, data, source)`** parses the raw payload into a typed `ServerPayload`, extracts the `Chat` and `Message`, then hands off to `IncomingMsgHandler.handle()` with an `IncomingPayload` that carries the parsed data and the `MessageSource` (socket or method channel).

For `"new-message"` events on messages sent by this device, the handler checks whether a `tempGuid` field is present in the payload. If it is, the server is echoing back a message we sent — see `docs/MESSAGE_SEND_FLOW.md` for how the tempGuid → realGuid swap is resolved.

**Key file:** `lib/services/backend/action_handler.dart`

---

### Step 3 — IncomingMessageHandler (`incoming_message_handler.dart`)

`IncomingMessageHandler` (accessed via the `IncomingMsgHandler` GetIt getter) owns all inbound message queuing and dispatch. It is a GetIt singleton registered at startup.

Internally it maintains a `Queue<_QueueEntry>` and processes entries up to `maxConcurrency` at a time. Same-GUID payloads are additionally serialized via an `_inflightByGuid` map so that two transports racing each other (socket + FCM) can never interleave DB writes for the same message.

Calling `handle(payload, {front})` enqueues the payload. Passing `front: true` jumps ahead of waiting items — used for user-initiated actions (e.g. the outgoing-message echo) where an immediate response is expected.

Routing by `MessageEventType`:
- `MessageEventType.newMessage` → `_processNewMessage(payload)`
- `MessageEventType.updatedMessage` → `_processUpdatedMessage(payload)`

**Key file:** `lib/services/backend/incoming_message_handler.dart`

---

### Step 4 — Handle New Message (`incoming_message_handler.dart`)

**`_processNewMessage(IncomingPayload)`:**

1. **Deduplication** — checks `_processedGuids` (a rolling set). If the GUID was already processed (e.g. delivered by both socket and Firebase), returns early.

2. **Existing record check** — if the message already exists in the DB (HTTP response saved it before the socket event, or duplicate delivery), redirects to `_processUpdatedMessage()` for a clean field refresh or GUID swap.

3. **Chat hydration** — calls `_hydrateChat()` to ensure the chat has valid participants and a DB ID before insertion.

4. **Save message to DB** — calls `chat.addMessage(message)`. This is the DB write entry point (see Step 5).

5. **Mark as processed** — adds the GUID to `_processedGuids` before any async I/O so a duplicate delivery that races in while playing sound or sending a notification is skipped.

6. **Complete send-progress tracker** — if `tempGuid` is set, calls `OutgoingMsgHandler.completeSendProgressIfExists(tempGuid)`.

7. **Audible receive feedback** — plays a receive sound for messages not from this device.

8. **Push / in-app notification** — calls `NotificationsSvc.tryCreateNewMessageNotification()`.

9. **Drive UI reactivity** — calls `_dispatchNewMessage()` which fires `EventDispatcherSvc.emit('new-message', ...)` and, if this is a tempGuid echo, calls `MessagesSvc.updateMessage()` to swap the temp bubble in-place.

10. **Refresh chat-list ordering** — calls `ChatsSvc.updateChat(chat, override: true)` (see Step 7).

11. **Flush out-of-order updates** — calls `_flushPendingUpdate()` to replay any `updated-message` that arrived before this `new-message`.

**`_processUpdatedMessage(IncomingPayload)`:**

1. **Complete send-progress tracker** — if `tempGuid` is set.

2. **Locate existing DB record** — tries `tempGuid` first (outgoing echo), then the real GUID.

3. **Out-of-order buffering** — if no DB record exists yet, parks the payload via `_parkPendingUpdate()` and waits. `_flushPendingUpdate()` replays it once the `new-message` is processed.

4. **Chat hydration** — calls `_hydrateChat()`.

5. **Persist GUID swap / field update** — calls `_replaceMessage()` which goes through `MessageInterface.replaceMessage()` (see `docs/MESSAGE_SEND_FLOW.md`).

6. **Persist attachment GUID swaps** — calls `_replaceAttachments()`.

7. **Drive UI reactivity** — calls `_dispatchUpdatedMessage()` → `MessagesSvc.updateMessage()` + `EventDispatcherSvc.emit('updated-message', ...)`.

8. **Refresh chat-list ordering** — calls `ChatsSvc.updateChat(chat, override: true)`.

---

### Step 5 — Database Write (via Interface + GlobalIsolate)

**Entry point:** `chat.addMessage(message)` in `lib/database/io/chat.dart`

**`ChatInterface.addMessageToChat()`** (`lib/services/backend/interfaces/chat_interface.dart`):

- Packs arguments into a `Map<String, dynamic>` (all values must be primitive — no ObjectBox entities cross the isolate boundary)
- **If already inside an isolate** (`isIsolate == true`): calls `ChatActions.addMessageToChat(data)` directly
- **If on the main thread:** dispatches to `GlobalIsolate.send(IsolateRequestType.addMessageToChat, data)` and awaits the response
- After the isolate returns `{ messageId: int, isNewer: bool }`, hydrates the result: `Database.messages.get(messageId)` → full `Message` object (O(1) lookup)

**`ChatActions.addMessageToChat()`** (`lib/services/backend/actions/chat_actions.dart`) runs inside the isolate:
- Upserts the `Message` record into ObjectBox
- Updates `Chat.latestMessage` pointer if the new message is newer
- Returns a plain map — never ObjectBox entities

For a temp → real GUID swap, `MessageInterface.replaceMessage()` → `MessageActions.replaceMessage()` follows the same isolate routing pattern and atomically updates the GUID on the existing record.

**Key files:**
- `lib/database/io/chat.dart` — `addMessage()`
- `lib/services/backend/interfaces/chat_interface.dart` — `addMessageToChat()`
- `lib/services/backend/actions/chat_actions.dart` — `addMessageToChat()` (runs in isolate)

---

### Step 6 — Unread / Archive State Update

Inside `chat.addMessage()`, after the DB write, the chat's unread and archive state is updated:
- If the message is **from someone else** and is newer: `chat.toggleHasUnreadAsync(true)` — marks the chat as having an unread message
- If the message is **from this device**: `chat.toggleHasUnreadAsync(false)` — clears the unread badge
- If the chat was **archived** and receives a message from someone else: `chat.toggleArchivedAsync(false)` — unarchives it automatically

These also go through `ChatInterface` → ObjectBox in the isolate.

---

### Step 7 — Chat Service State Update (`chats_service.dart`)

**`ChatsSvc.updateChat(Chat updated)`:**

1. Finds the `ChatState` for this chat (keyed by GUID in `chatStates` map)
2. Calls `state.updateFromChat(updated)` — updates every changed `Rx*` field via `*Internal()` methods. Each `Obx()` widget watching a field rebuilds independently — a `latestMessage` change does not force the chat title to rebuild.
3. If `latestMessage` or `pinIndex` changed (sort-order-relevant), calls `_repositionChat()` to re-sort the chat list

**Key file:** `lib/services/ui/chat/chats_service.dart`

---

### Step 8 — Message Service State Update (`messages_service.dart`)

**`MessagesSvc(chatGuid).updateMessage(Message updated, {String? oldGuid})`:**

1. Finds the existing `Message` in the in-memory `MessageStruct` by `oldGuid` (if swapping) or by `updated.guid`
2. Merges the updated message with the existing one (`updated.mergeWith(existing)`)
3. Finds or creates the `MessageState` for this message
4. Calls `messageState.updateFromMessage(updated)` — pushes new values into all `Rx*` fields
5. **If `oldGuid != null`** (tempGuid → realGuid swap): removes `messageStates[oldGuid]` and inserts `messageStates[realGuid]` pointing to the same state object
6. Increments `messageUpdateTrigger[realGuid]` — widgets that observe the trigger rather than individual fields are notified to rebuild

**Key file:** `lib/services/ui/message/messages_service.dart`

---

### Step 9 — UI Reactivity (`lib/app/state/`)

`ChatState` and `MessageState` are never written to directly by UI code. The only write path is `*Internal()` methods called by the service layer.

**ChatState observables** that drive UI rebuilds: `isPinned`, `hasUnreadMessage`, `isArchived`, `muteType`, `title`, `displayName`, `subtitle`, `latestMessage`, `textFieldText`, `isActive`, `isAlive`, and others. Each is a separate `Rx*` field.

**MessageState observables**: `guid`, `text`, `dateDelivered`, `dateRead`, `dateEdited`, `error`, `hasReactions`, `associatedMessages`, `isSending`, `isSent`, `hasError`, `isReaction`, and others.

Because each field is its own observable, `Obx()` rebuilds only the widget that reads that specific field. An unread badge update does not re-render the chat title. A delivery timestamp update does not re-render the message bubble text.

---

## Key Files at a Glance

| Step | File | Key Method |
|------|------|-----------|
| Socket | `lib/services/network/socket_service.dart` | `socket.on("new-message", ...)` |
| Event dispatch | `lib/services/backend/action_handler.dart` | `handleEvent()` |
| Queue & dispatch | `lib/services/backend/incoming_message_handler.dart` | `handle()`, `_processNewMessage()`, `_processUpdatedMessage()` |
| Chat DB entry | `lib/database/io/chat.dart` | `addMessage()` |
| Interface (chat) | `lib/services/backend/interfaces/chat_interface.dart` | `addMessageToChat()` |
| Interface (message) | `lib/services/backend/interfaces/message_interface.dart` | `replaceMessage()` |
| Isolate actions | `lib/services/backend/actions/chat_actions.dart` | `addMessageToChat()` (runs in isolate) |
| Chat state update | `lib/services/ui/chat/chats_service.dart` | `updateChat()` |
| Message state update | `lib/services/ui/message/messages_service.dart` | `updateMessage()` |
| Reactive state | `lib/app/state/chat_state.dart` | `updateFromChat()`, `update*Internal()` |
| Reactive state | `lib/app/state/message_state.dart` | `updateFromMessage()`, `update*Internal()` |

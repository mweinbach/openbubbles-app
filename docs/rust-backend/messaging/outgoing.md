# Outgoing: the send surface

Part of the [Rust backend reference](../README.md). Prerequisites: the
[sync-vs-async rule](../foundations/runtime.md#sync-vs-async-exports--the-rule-that-prevents-deadlocks).
Related: [message model](message-model.md), [incoming](incoming.md) (SendConfirm).

All live on `NativePushState`. Network sends are `suspend` functions (async exports)
and return the staged `UMessageInst` — its `id` is the staging guid Kotlin persists for
the ObjectBox row before the network transfer finishes.

## Conversations and the group-version invariant

`UConversation { participants, cv_name, sender_guid, after_guid }`. Participants are
`mailto:`/`tel:`-prefixed URIs **including the sender**; `cv_name` is the group name;
`after_guid` anchors reply threads. Group membership changes (`ChangeParticipants`,
`IconChange`) carry a `group_version` that **Kotlin owns**: start from the version of
the last incoming change message and bump by exactly one per outgoing mutation
(`chat.groupVersion = (chat.groupVersion ?? -1) + 1` semantics from the Dart client).
Rust does not track it.

## Sends

| Method | Notes |
|---|---|
| `send_text(conversation, sender, text, reply_guid?, reply_part?, effect?, subject?)` | Single text part |
| `send_parts(...)` | Full part list: text (with `TextFormat` JSON), mentions, attachment XMLs |
| `send_typing(conversation, sender, typing)` | Typing indicator |
| `send_read(conversation, sender, message_guid)` | Read receipt; the instance id is set to the newest acked message guid — pass that, not a fresh id |
| `send_reaction(...)` | Tapbacks: `reaction_idx` 0 heart, 1 like, 2 dislike, 3 laugh, 4 emphasize, 5 question, 6 + `emoji` for custom; `enable=false` removes; `to_uuid`/`to_part` identify the target part |
| `send_sticker(...)` | Uploads the file (progress callback), then sends a sticker-reaction with normalized 0..1 coordinates, radians rotation, 0.1..4 scale, `effect_type` |
| `send_attachment` / `send_attachments` | Upload then send one/many files as parts of a single message, optional leading caption text; `send_attachments` groups its parallel `mimes`/`utis`/`names` arrays and send metadata in `USendAttachmentsRequest` so the arm64 JNA boundary stays compact |
| `edit_message`, `unsend_message` | `to_uuid` = original guid, `edit_part` = part index; edits carry the full replacement part list |
| `rename_chat`, `change_participants`, `leave_chat` | Group ops; `change_participants` takes the *full* new list (adds/removes inferred by diff); `leave_chat` filters the sender out (prefix-insensitive) |
| `set_group_icon` / `remove_group_icon` | 570×570 PNG upload → `IconChange` message |
| `send_profile` | Sends the JSON produced by `set_profile` ([profiles](../icloud/services.md#profiles-and-contact-posters)) |
| `send_sms(conversation, sender, text, using_number?, from_handle?, ...)` | SMS relay (`MessageType::SMS`); `using_number` defaults to the first registered `tel:` handle; `from_handle` marks a forwarded message. Historically a **sync** export (`block_on`) — call it from `Dispatchers.IO`, not from a delegate/Tokio callback |

`sms_targets_for(handle, refresh)` lists SMS-relay-capable devices (`PrivateDeviceInfo`)
for the forwarding UI. `report_spam(handle, messages)` files spam reports via IDS.

Read receipts identify the newest message they acknowledge — that is why `send_read`
overrides the fresh envelope id with the target message guid.

## Attachments

Attachments are opaque handles, not bytes:

- Incoming `UPart.Attachment.xml` is the plist-XML serialization of the rustpush
  `Attachment`. **Persist it** with the message row (the Dart app kept it as
  `attachment.metadata["rustpush"]`), then `restoreAttachment(xml)` → `UAttachment`
  and `state.downloadAttachment(attachment, destPath, progress?)`. Parent directories
  are created for you. `isInline()`/`totalSize()` tell you whether a transfer is needed.
- Outgoing: `uploadAttachment(file, mime, uti, name?, progress?)` → `UAttachment`;
  persist via `saveAttachment()` if the send may be retried after a restart, then send
  it as a part (`send_parts` with the attachment XML) or use the one-call
  `send_attachment`.
- `downloadMmcs(mmcsXml, dest, progress?)` fetches bare MMCS descriptors (group icons
  from `UMessage.IconChange.iconXml`, transcript wallpapers from
  `SetTranscriptBackground.mmcsXml`).
- `UProgressCallback.onProgress(done, total)` fires synchronously inside the transfer;
  treat the thread as unspecified and never re-enter Rust from it.

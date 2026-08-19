# CloudKit message-history sync

Part of the [Rust backend reference](../README.md). Consumers: `CloudSyncManager` in
`:core`. Related: [keychain/Octagon](../internals/rustpush.md#keychain--octagon-icloudkeychainrs),
[state files](../foundations/state.md).

## Availability and keychain trust

`cloudSyncState()` reports `Available` (iCloud account + keychain + cloud-messages
client live), `NeedsLogin`, or `NotEnabled` (account without iCloud Keychain).
Messages-in-iCloud records are end-to-end encrypted through the Octagon circle, so
before the first sync the device must join:

- `isInClique()` — membership check; the Dart sync loop disabled syncing when out.
- `getViableBottles()` — trusted-device escrow bottles (device name, model class,
  numeric code length). **Empty list must not silently reset encrypted iCloud data.**
- `joinCliqueWithBottle(escrowData, password, devicePassword)` — non-destructive
  recovery with a trusted device's passcode + a new recovery code.
- `startCliquePairing()` → BLE service UUID to advertise; `completeCliquePairing(code,
  devicePassword)` submits the 6-digit code (180 s timeout); `cancelCliquePairing()`.

## Pulling history (chats → messages)

Two styles; both pull chats first, then messages:

- **Kotlin-driven paging**: `syncChatsPage(cursor?)` / `syncMessagesPage(cursor?)` /
  `syncAttachmentsPage(cursor?)` each return one page: records, `nextCursor`, `more`
  (zone status 3 = caught up), and `status`. Records are `(recordId, value?)` —
  `null` value = tombstone (delete the local row matched by ckRecordId). **The caller
  owns the cursor**: persist it only after the page was applied; an empty cursor means
  "zone never pulled a page" — keep the previous one.
- **Rust-driven bulk**: `syncHistory(chatCursor, messageCursor, mode, USyncPageCallback)`
  runs both zones to completion (or `keepGoing() == false`), streaming pages with
  running totals through `onPage`, and returns a summary + the cursors reached.
  Cooperatively cancellable between pages; per-record failures are the callback's
  concern.

Every chat/message record also carries a re-uploadable `blob` (binary plist of the
rustpush record; message blobs keep the protobuf halves as exact gzipped wire bytes).
Persist blobs next to local rows — they feed the upload half.

**Deletion ordering**: push local deletions *before* pulling, or the pull resurrects
rows (`deleteChatsRemote` / `deleteMessagesRemote` / `deleteAttachmentsRemote` with
the ckRecordIds).

## Record shapes (what Kotlin maps to ObjectBox)

- `UCloudChat`: guid (`iMessage;+/-;chatIdentifier`), style (43 group / 45 normal),
  participants (URIs incl. mine), `group_version` (`CloudProp.pv` — apply only when
  newer), `last_seen_message_guid`, `last_read_message_timestamp` (ns since the 2001
  Apple epoch), `has_group_photo` (download via `downloadGroupPhoto(recordId, path)`).
- `UCloudMessage`: guid, chat_id, sender, time (Apple epoch ns), flags bits (bit 2 =
  from-me), flattened text (plain field, else decoded attributed body),
  `attachment_guids` (already converted `at_<part>_<msgGuid>` → `<msgGuid>_<part>`),
  balloon/link json, `summary_info_json` (edits/retractions plist as JSON),
  `thread_originator_guid/part` (from `tg`-style `r:<part>:<guid>`), receipt times,
  `associated_message_type` (2 sticker, 2000+ tapback, 3000+ removed), and a parsed
  `transcript_background` for type-138 records.
- `UCloudAttachment`: guid, message_guid?, uti, mime, is_outgoing, transfer_name,
  total_bytes. Payload bytes stay remote until `downloadCloudAttachment(recordId,
  path)`.

Wallpapers: incremental tokens never re-emit walked-past type-138 records, so
`queryTranscriptBackgrounds()` re-queries them by type on demand.

## The upload half (re-sync after local edits)

- `uploadChats(records: UCkBlob[])` / `uploadMessages(records)` — restore the blobs
  pulled during sync and push them back (per-record `ok`/`error` results; restore
  failures don't abort the batch). Constructing brand-new cloud records from purely
  local rows is not exposed yet.
- `uploadAttachments(uploads)` — bytes up (progress per file), folded into
  `CloudAttachment` records with caller-supplied `meta_json` (`AttachmentMeta` keys:
  `mimet`, `sdt`, `tb`, `st`, `is`, `aguid`, `ha`, `ui`, `fn`, `ig`, `tn`, `vers`, `t`,
  `cdt`, `pathc`, `mdh`, `aui`).
- `uploadGroupPhoto(file, chatRecordId, chatBlob)` — uploads the image, grafts the
  asset onto the restored chat record, saves it.

CloudKit zone/container facts for orientation: private DB, container
`com.apple.messages.cloud`, PCS service "Messages3"/zone "Engram"; zones
`chatManateeZone`, `messageManateeZone`, `attachmentManateeZone` (records
`chatEncryptedv2`, `MessageEncryptedV3`, `attachment`). Kotlin never names these —
they are `rustpush/src/imessage/cloud_messages.rs` internals.

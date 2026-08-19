# Message model reference (`UMessage`/`UPart`/`UMessageInst`)

Part of the [Rust backend reference](../README.md). Consumers: `MessageIngestor` in
`:core` (the reference implementation of the "Handling" column). Related:
[incoming](incoming.md), [outgoing](outgoing.md).

`UMessageInst { id, sender?, conversation?, message, sent_timestamp, send_delivered,
verification_failed }` wraps every event. The `UMessage` variants and what Kotlin must
do with each:

| Variant | Carries | Handling |
|---|---|---|
| `Normal` | parts, effect?, reply guid/part?, subject?, voice, is_sms, app_json?, link_json? | Insert message row; parts may be Text/Mention/Attachment/Object |
| `React` | to_uuid, to_part?, reaction_json, to_text, parts (sticker/extension bodies) | Tapback on target part; sticker reactions carry attachment parts to auto-download |
| `Rename`, `ChangeParticipants` | new name / new list + group_version | Update chat row and group version |
| `IconChange` | json + optional `icon_xml` | Download via `downloadMmcs` to `filesDir/group_icons`, set chat avatar; null xml clears it; bump group_version from the json |
| `Delivered`, `Read`, `MessageReadOnDevice`, `MarkUnread`, `NotifyAnyways` | — | Receipt state updates |
| `Typing` | bool | 60 s expiring typing state |
| `Unsend`, `Edit` | tuuid, edit_part (+ new parts for Edit) | Retract/replace part |
| `SmsConfirmSent` | bool | SMS relay send result |
| `EnableSmsActivation` | bool | SMS-relay activation handshake (auto-confirmed inside Rust) |
| `Error` | for_uuid, status, status_str | Mark that outgoing message failed |
| `MoveToRecycleBin`, `RecoverChat`, `PermanentDelete` | json | Cloud-side deletions |
| `UpdateProfile`, `UpdateProfileSharing`, `ShareProfile` | json | Feed `fetch_profile` ([profiles](../icloud/services.md#profiles-and-contact-posters)) |
| `SetTranscriptBackground` | json, version, chat_id?, remove, mmcs_xml? | Apply chat wallpaper via `TranscriptBackgroundStore`; never let it fail the journal entry |
| `UpdateExtension`, `PeerCacheInvalidate`, `Unschedule` | json / — | App-balloon state / identity-cache invalidation / scheduled-send cancel |

`is_sms` on `Normal` and the `SmsConfirmSent`/`EnableSmsActivation` pair cover the SMS
relay path; `isFromMe` in intake is decided by matching `sender` against
`get_handles()`.

For the rustpush-side wire details (plist keys `t`, `x`, `bid`, `amt` tapback codes,
`ia-0`/`ia-1` inline attachments, gzip rules) see `rustpush/src/imessage/rawmessages.rs`
and [rustpush internals](../internals/rustpush.md) — Kotlin never touches wire plists;
it consumes the `U*` projection.

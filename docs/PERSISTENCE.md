# Persistence

ObjectBox store shared with the retired Flutter client. The contract is the model file, not the
[retired Dart model notes](../legacy/flutter/docs/models.md).

## Store

[`Db.build(dir)`](../db/src/main/kotlin/app/openbubbles/db/Db.kt) opens `<dir>/objectbox` (5 GB cap).

- Android: `{dataDir}/app_flutter/objectbox` — see
  [`CoreGraph.kt`](../app-native/src/main/kotlin/app/openbubbles/nativeapp/data/CoreGraph.kt).
  Do not move this path. Application id stays `com.openbubbles.messaging`.
- Desktop: `~/.openbubbles-natives/objectbox`.
- Attachments: `<root>/attachments/<guid>/…` (`AttachmentStore`).

`:db` is `kotlin-jvm`. Callers pass the directory. No Android types here.

## Model contract

- Live: `db/objectbox-model.json`
- Seed (Flutter-era bytes): `db/seed-objectbox-model.json`
- Guard: `:db:checkModelParity` — the two files must be trim-equal.

UIDs are sacred. Regenerating them makes existing device stores unloadable. In-place upgrade of a
real Flutter-era backup is still a [CUTOVER](../tools/CUTOVER.md) device gate.

Entities are **generated Java** (`db/src/main/java/app/openbubbles/db/`). Kotlin entities break
kapt on this `kotlin-jvm` module. After a reviewed seed change:

```bash
python3 tools/gen_db_entities.py
cd native && ./gradlew :db:checkModelParity
```

Do not hand-edit the Java files or invent UIDs.

Entities: `Attachment`, `Chat`, `Message`, `Handle`, `ContactV2`, plus `FCMData` / `Theme*` kept
for upgrade compatibility.

Relations to preserve: `Chat.handles`, `Chat.dbLatestMessage`; `Message.chat` / `handleRelation` /
`dbAttachments`; `Attachment.message`; `ContactV2.handles` ↔ `Handle.contactsV2`.

## Who writes

| Writer | Role |
|---|---|
| `MessageIngestor` | Incoming UniFFI events, local send echoes |
| `ChatRepo` / `MessageRepo` | Observe, pin/mute/archive, stage outgoing, fail outgoing |
| `ContactSync` | Device + CardDAV contacts |
| `CloudSyncManager` | History backfill — **must not** mark chats unread |
| `BackupManager` | Zip of `objectbox/` + `attachments/`; restore requires process restart |

Compose and ViewModels never open `BoxStore`. They go through `AppGraph` contracts.

Core DTOs (`core/.../model/ChatListItem`, `MessageItem`) are not the UI types. `CoreGraph` maps
into `app-native/.../data/Repository.kt`.

## Agent rules

1. Shared behavior in `:core`. Schema in `:db` via the generator. Android extras (group icons,
   wallpapers) may touch the store in `app-native` only when no core API exists yet.
2. Do not notify on journal replay or the first history poll (`isNewIncomingMessage`,
   `hasCompletedHistorySync`).
3. Optimistic send stages a `temp-` guid, then swaps to the Rust staging guid and ingests the echo.
   There is no durable outbound queue — missing push state fails the staged row.
4. Keep `senderGuid` / `afterGuid` on send. Dropping them splits groups.

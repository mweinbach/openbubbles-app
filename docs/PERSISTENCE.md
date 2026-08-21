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
./gradlew :db:checkModelParity
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

## Transactions, threads, and observers

ObjectBox cursors, transactions, lazy relation reads, query subscriptions, and the `BoxStore` that
created them have one ownership lifetime.

- Keep a logical ingest/apply event inside one explicit transaction on the dispatcher/thread that
  performs its lazy relation access. Do not return lazy ObjectBox-backed state across dispatchers.
- Close thread resources on the thread that created them when an API requires thread ownership.
  Do not wait until process shutdown and then close a store underneath live reader threads.
- Give shared invalidation/query subscriptions one explicit coordinator owner per store. Release
  that owner before restore, account/store replacement, or final store close.
- After release begins, late callbacks must observe the closed/generation state and return without
  querying, invalidating projections, or publishing UI state.
- Coalesce invalidations at the transaction/history-page boundary. A replay or no-op write must not
  trigger repeated transcript/contact warming or full projection rebuilds.
- Tests that close temporary stores must also release repositories/subscriptions and drain their
  test dispatchers first. Treat ObjectBox reader/cursor warnings as lifecycle failures, not harmless
  test noise.

Follow [DATA_LIFECYCLE.md](DATA_LIFECYCLE.md) for account cancellation, durable file publication,
cleanup, and stale callback rules around the store.

## Query and migration discipline

Before adding an index or replacing the engine, prove the exact hot query and narrow when it runs.
Fallback lookups such as staged-send correlation belong only on events that can legitimately match;
peer messages must not pay for a full scan intended for self-originated echoes.

If another engine is evaluated:

1. preserve the existing ObjectBox store and model unchanged as the upgrade source/cold importer;
2. design a resumable, idempotent importer against a copied production-scale store;
3. preserve entity identity, relations, attachment paths, backup/restore format, desktop behavior,
   and `{dataDir}/app_flutter/objectbox` until the migration is proven and reviewed;
4. benchmark the actual hot paths before and after on equivalent data/device state;
5. keep rollback possible until an in-place Flutter-era upgrade and backup restore pass on hardware.

Changing database libraries is not a substitute for fixing redundant scans, invalidation storms,
reader ownership, or projection amplification in the current code.

## Agent rules

1. Shared behavior in `:core`. Schema in `:db` via the generator. Android extras (group icons,
   wallpapers) may touch the store in `app-native` only when no core API exists yet.
2. Do not notify on journal replay or the first history poll (`isNewIncomingMessage`,
   `hasCompletedHistorySync`).
3. Optimistic send stages a `temp-` guid, then swaps to the Rust staging guid and ingests the echo.
   There is no durable outbound queue — missing push state fails the staged row.
4. Keep `senderGuid` / `afterGuid` on send. Dropping them splits groups.

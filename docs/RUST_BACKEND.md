# The Rust backend: how it works, how Kotlin uses it, how to change it

This is the deep reference for everything below `:core`'s UniFFI Kotlin bindings: the
`rust/` application facade, the `rustpush/` Apple-protocol submodule, and the
lifecycle/contract the Kotlin app must uphold. Read [RUST_KOTLIN.md](RUST_KOTLIN.md)
first for the one-page orientation; read this when you need the actual API surface,
state machines, file formats, or a change recipe. For module-level app architecture see
[ARCHITECTURE.md](ARCHITECTURE.md).

Everything here is the shipping Kotlin+Rust stack. Flutter Rust Bridge (FRB) leftovers
compile but are dead surface (§1.4).

---

## 1. Layer map

```text
app-native / desktopApp        Compose UI, services, Android Keystore impl
        │
      :core                     intake, repos, sync  (imports committed UniFFI Kotlin)
        │  JNA → librust_lib_bluebubbles.so
      rust/                     UniFFI facade: U* mirrors, boot, queue, keystore, engine
        │
      rustpush/                 Apple protocols (git submodule)
        ├── keystore/           abstract keystore traits + software/backup impls
        ├── open-absinthe/      on-device Apple validation circuit (source-built)
        └── apple-private-apis/ icloud-auth (GSA), omnisette (anisette), cloudkit-proto…
```

### 1.1 The `rust/` crate (`rust_lib_bluebubbles`)

| File | Owns | Change rules |
|---|---|---|
| `src/lib.rs` | UniFFI scaffolding, the Tokio `RUNTIME` (§2), logger init, `bbhwinfo` protobuf (OABS payload), debug NAC smoke-test `extern "C"` fns | Rarely touched; runtime sizing and logging policy live here |
| `src/native.rs` | Process boot (`start`, `init_native`), `NativePushState` object, the receive loop (`start_loop`), the durable queue (journal + pointer map, §5), carrier lookup, legacy passwords/keychain callbacks, `MsgReceiver`/`KotlinFilePackager`/`HandleWifiNetworksCallback` foreign traits | Queue/receive-loop behavior and boot semantics only |
| `src/uniffi_ext.rs` | The bulk of the Kotlin-visible API: all `U*` mirrored types, sends, attachments, login (`ULoginSession`), CloudKit sync, vault/passwords, shared albums, Find My, FaceTime, posters/profiles, provisioning, SMS helpers | Any new Kotlin-visible type or method defaults to here |
| `src/api/api.rs` | The engine behind the facade: `SharedPushState`/`SharedICloudServices`, state restore, all `make_*` service constructors, `recv_wait` topic dispatch, `send`/SendConfirm, migration, login helpers, reset/teardown, CloudKit wrappers, FindMy/FaceTime/StatusKit wrappers, `GSAConfig` persistence | Restore/APS-watcher/journal internals and cross-service glue |
| `src/api/mirrors.rs` | Legacy FRB Dart mirrors of rustpush types | Do not extend; needed only so `frb_generated.rs` still compiles |
| `src/frb_generated*.rs` | Legacy FRB glue from the retired Dart client | Never hand-edit, never extend |
| `src/keystore.rs` | `NativeKeystore` foreign trait, `setup_keystore`, hardware/software selection, import key-wrapping (ASN.1 `KeyWrapper`), lock/unlock/recover | Only for keystore contract changes |
| `uniffi-bindgen.rs` + `build.rs` | UniFFI bindgen binary + scaffolding | — |

### 1.2 The `rustpush/` submodule

| Module | Protocol |
|---|---|
| `src/auth.rs` | GSA delegate login, IDS cert provisioning (`authenticate_apple`/`_phone`/`_smsless`), `TokenProvider`, IDMS circle sessions (client + server), `IdmsAuthListener` |
| `src/aps.rs` | APNs socket (port 5223, rustls, packed/raw wire), `APSConnection` as a self-healing `ResourceManager`, topic `Filter`s, `send_message` + APNs-tunneled `SignedRequest`s |
| `src/ids/` | `IDSUser` registration (`register`), NGM identities + payload crypto (`pair-ec`), `IdentityManager`/`KeyCache` (per-handle delivery keys + send counters), the send pipeline (`SendJob`/`SendResult`/`MessageTarget`) |
| `src/imessage/` | `IMClient` (madrid orchestrator), message model (`messages.rs`), wire plists (`rawmessages.rs`, `include!`d), CloudKit message sync (`cloud_messages.rs`), name/photo sharing, contact posters (`posterkit.rs`) |
| `src/icloud/` | `CloudKitClient` (web CK), `mmcs.rs` attachment transport, `keychain.rs` (Octagon circles/escrow), `pcs.rs` (protected-cloud-service keys) |
| `src/findmy.rs`, `facetime.rs`, `statuskit.rs`, `passwords.rs`, `sharedstreams.rs` | Find My (devices/friends/items), FaceTime signaling, presence/status, iCloud Passwords, Shared Albums |
| `src/util.rs` | `ResourceManager`/`Resource` (the retry/backoff wrapper under APS + IDS), NSKeyedArchive coder helpers, `CompactECKey`, misc |
| `src/activation.rs`, `src/macos.rs`, `src/relay.rs` | Albert activation (push cert CSR + FairPlay), `MacOSConfig`/`HardwareConfig` + on-device validation data, hosted `RelayConfig` |
| `keystore/` | `Keystore` trait, `BackupKeystore` (hardware-backed + state file), `SoftwareKeystore` (desktop) |
| `open-absinthe/` | Source-built Apple validation ("absinthe") circuit — constructor, key establishment, signing; loaded via `open_absinthe::nac` |
| `apple-private-apis/` | Vendored `icloud-auth` (GSA/SRP), `omnisette` (anisette providers), `cloudkit-proto`, `cloudkit-derive` |

`rustpush` types never cross the FFI boundary directly (they cannot derive UniFFI traits).
Everything Kotlin sees is a `U*` record/enum from `uniffi_ext.rs`, or an opaque XML/plist
blob (attachments, MMCS files) that round-trips through persistence.

### 1.3 The `U*` mirroring pattern

`uniffi_ext.rs` mirrors rustpush types field-by-field (`conv_*` functions in, `back_*`
functions out). Where a variant is rare or pre-MVP, the raw value is carried as
`serde_json` under a `*_json` field (or plist-XML under `*_xml` for transfer
descriptors) so nothing is lost while Kotlin stays typed. When you extend the API,
follow this pattern; do not invent a second convention.

### 1.4 FRB legacy

`#[frb]` attributes, `frb_generated.rs`, and `api/mirrors.rs` exist only so the crate
still compiles during/after the Dart→Kotlin cutover. The Kotlin API is exclusively the
committed UniFFI bindings in `core/src/main/kotlin/uniffi/rust_lib_bluebubbles/`. Do
not add FRB exports; do not hand-edit the generated Kotlin.

---

## 2. Runtime and concurrency model

### 2.1 The Tokio runtime

`rust/src/lib.rs` builds one process-wide multi-thread runtime named `tokio-rustpush`
with 2–4 workers (`available_parallelism().clamp(2, 4)`). It is deliberately small —
this is a phone — but deliberately never one thread: a single worker head-of-line blocks
the APNs socket behind journal writes and CloudKit pages.

### 2.2 Sync vs async exports — the rule that prevents deadlocks

- **Synchronous exports** (`start`, `init_native`, `get_handles`, `get_regstate`,
  `stop_loop`, `teardown`, `send_sms`, `list_passwords`, `upload_chats`, …) internally
  do `RUNTIME.block_on(...)`. They park the *calling* thread. Never call them from a
  Tokio worker — i.e. never from inside `MsgReceiver.native_ready`, `receieved_msg`,
  `finish`, or any `U*Delegate` callback: those run on runtime threads, and
  `block_on`-inside-the-runtime panics or deadlocks regardless of worker count.
  Kotlin's `NativePushService` hops to `Dispatchers.IO` before touching any sync
  export.
- **Async exports** (`#[uniffi::export(async_runtime = "tokio")]` + `pub async fn` →
  `suspend fun` in Kotlin) are driven through `drive_ffi` (`uniffi_ext.rs`): the future
  runs on the runtime's own *blocking pool*, so the Kotlin caller suspends its coroutine
  instead of parking a thread. All network transfers — sends (incl. stickers, edits,
  group ops, profiles), attachment up/downloads, CloudKit sync pages and uploads — are
  async. They are safe from any coroutine context.

Choose sync vs async from the operation, not caller convenience: anything that can
wait on the network must be an async export.

### 2.3 Foreign callbacks (delegates)

`#[uniffi::export(with_foreign)]` traits (`MsgReceiver`, `KotlinFilePackager`,
`ULoginDelegate`, `UEapAkaHandler`, `UProgressCallback`, `USyncPageCallback`,
`NativeKeystore`, `CarrierHandler`, wifi/2FA/vault callbacks) cross JNA into Kotlin.
Invariants:

1. Delegate methods fire **synchronously on the thread that called the Rust method**
   (e.g. `ULoginDelegate.on_stage` fires before `login()` returns). For runtime-spawned
   events (`receieved_msg`), Rust uses `spawn_blocking` so the receive loop never
   blocks on Kotlin.
2. **Never re-enter Rust from inside a delegate.** A delegate that calls a sync export
   while Rust holds the login-session lock deadlocks.
3. Keep delegate bodies light: hand the event to a coroutine and return.

### 2.4 Logging

`init_logger` (called by `start`) fans every record to logcat (`android_logger`) and a
rotating file `<dir>/logs/rs_r*.log` (10 MB, 1 day, keep 1). Debug builds log at
`debug` (rustpush dumps full payload hex and per-page sync traces); release caps at
`info`, so `debug!` arguments are never evaluated. Panics are routed through `log` so
they reach logcat (`RUST PANIC: …` with backtrace). Debug-only `extern "C"` smoke
tests `openbubbles_debug_nac_round_trip[_saved]` run the account-free validation
handshake from ADB receivers.

---

## 3. Boot and lifecycle — what Kotlin must do

Order matters; every step is required before the next.

1. **`RustBoot.ensureStarted(context, dir)`** — once per process (idempotent, blocks
   concurrent callers). Inside: `uniffiEnsureInitialized()` → `start(dir, packager,
   wifiCallback)` (registers the `KotlinFilePackager` + wifi callback globals, creates
   the log dir) → `setupKeystore(dir, AndroidNativeKeystore)` (§9). Provisioning and
   login both touch the keystore, so this must run even before the push service starts
   (onboarding does it from the activity).
2. **Provisioning** (fresh installs, §8.1) writes `hw_info.plist`.
3. **Login** (§8.2) writes `gsa.plist` / `id.plist` and friends.
4. **`initNative(dir, handle: String?, MsgReceiver)`** — `NativePushService` calls this
   with `handle = null` on Android. Rust spawns `SharedPushState::restore_with_error`
   on the runtime; the result comes back as `nativeReady(NativePushState?)` or
   `nativeError(reason)` on the `MsgReceiver`. A `null` state means "nothing
   registered" (no `hw_info.plist`/`id.plist`) — the service stops itself. The desktop
   app may instead pass a daemon handle from `get_state()`/`send_daemon` to re-attach
   an already-live state (`take_daemon`).
5. **On `nativeReady`** — hop off the Tokio thread immediately, then: read
   `get_handles()` (drives `isFromMe` in intake) and `get_regstate()`, install the
   state in `PushStateHolder`, drain the journal once (silent — no notifications), and
   `startLoop(receiver)`.
6. **`startLoop`** runs the APS receive loop (§5) until stopped; `finish()` fires on
   the receiver when it ends.
7. **Modes.** Live: foreground service, `START_STICKY`, keep it running. Battery-saver
   poll: WorkManager starts the service with `ACTION_POLL_ONCE`, Kotlin sets the poll
   mode *before* `initNative`, runs one incremental CloudKit sync, notifies, and
   `stopSelf()`. After login, `NativePushService.reloadAfterLogin` sends
   `ACTION_RELOAD`, which stops the old state and re-runs `initNative`; the service
   tags each boot with a generation counter so stale `nativeReady`/`finish` callbacks
   from the superseded state are ignored.
8. **Tear down.** `stop_loop()` cancels the receive loop and closes live resources
   without deleting state. `teardown(logout: Boolean)` is terminal: with `logout` it
   deregisters from iMessage (re-`register` with zero users), logs the Apple account
   out, deletes `gsa.plist` + iCloud service files (+ `id.plist`); hardware validation
   is kept (`reset_hw = false`) so re-login does not need re-provisioning. Kotlin then
   re-`initNative` only after a fresh login.

Reconnects are Kotlin's job: `NativePushService` backs off (2 s → 120 s cap) and calls
`bootRust()` again (i.e. a fresh `initNative`). Rust-side, the APS connection itself is
a `ResourceManager` that re-dials internally; the Kotlin reconnect ladder is for the
whole restore path failing.

---

## 4. State: what lives where

### 4.1 In-memory

`NativePushState` (UniFFI object) wraps `Arc<SharedPushState>` + the `APSWatcher`.
`SharedPushState` (`api.rs`) holds:

| Field | Meaning |
|---|---|
| `os_config` | `JoinedOSConfig` — `MacOS` or `Relay`; the spoofed device identity |
| `conn` | `APSConnection` — the APNs `ResourceManager` |
| `anisette` | Anisette client (remote-v3 by default) |
| `client` | `Arc<IMClient>` — madrid messaging (IDS identity inside) |
| `ft_client`, `idms_client` | FaceTime, IDMS/2FA listeners |
| `icloud_services: Option<SharedICloudServices>` | Present only when an Apple account is signed in: `account` (AppleAccount), `token_provider`, and per-service clients — `cloudkit_client`, `keychain`, `passwords`, `profiles_client`, `fmfd` (Find My), `sharedstreams`, `cloud_messages_client`, `statuskit_client`. Several are `None` until iCloud Keychain (Octagon) is joined — that gates history sync, passwords, and Find My items |
| `local_broadcast` | mpsc sender that feeds local events (SendConfirm etc.) into the receive loop |
| `cancel_poll` | stops the loop (`stop_loop`) |
| `active_circle_sessions`, `client_session` | IDMS 2FA/circle approval state |
| `conf_dir` | the config directory below |

`NativePushState.get_state()` registers the state in a process registry and returns a
pointer id (used by the desktop daemon handoff; Android does not need it).

### 4.2 On disk (the config dir passed to `start`/`initNative`)

Treat every file here as app-private. **Never commit any of them.**

| File | Contents | Writer | Secret |
|---|---|---|---|
| `hw_info.plist` | `SavedHardwareState`: APS push state, encrypted NGM identity, OS config | provisioning / `setup_push` (rewritten atomically whenever the push token rotates) | yes (identity) |
| `id.plist` | Registered `IDSUser`s (certs; private keys are keystore handles) | `register_ids` / IMClient `keys_updated` callback | yes |
| `id_cache.plist` | `KeyCache`: recipient delivery keys + NGM send counters | IdentityManager | yes |
| `gsa.plist` | `GSAConfig`: username, password encrypted under keystore key `gsa:password`, postdata flag | login | yes |
| `anisette_test/` | Anisette provisioning state (`state.plist`) | anisette provider | yes |
| `cloudkit.plist` | `CloudKitState` tokens | login/CloudKit | yes |
| `keychain.plist` | `KeychainClientState` (Octagon circle, cloud keys) | keychain sync | yes |
| `keychain_identity.plist` | Sidecar copy of the keychain peer identity so repairs re-adopt the *same* circle peer instead of registering ghosts | `make_keychain` | yes |
| `passwords.plist` | Passwords local cache | PasswordManager | yes |
| `findmy.plist` | `FindMyState` (encoded; includes keys) | FindMy client | yes |
| `facetime.plist`, `sharedstreams.plist`, `sync.plist`, `statuskit.plist` | FaceTime sessions/links, shared-stream tokens, sync-controller state, StatusKit key | respective clients | tokens, yes |
| `keystore.plist` / `keystore_s.plist` | Keystore state (hardware-backed vs software) | `setup_keystore` | **critical** |
| `messages.journal` | Durable incoming-message queue (§5.3) | receive loop | contents are messages |
| `logs/rs_r*.log` | Rotating Rust log | logger | may contain payloads |
| `incident`, `incident_affected` | One-shot markers that the IDS key cache predates an incident and was rebuilt | `make_imclient` | — |

Durability rules in `api.rs` that you must preserve when touching writers: state files
are written via `atomic_write_plist` (temp file + fsync + rename + parent fsync); a
state file that exists but fails to parse is *quarantined* to `<name>.corrupt-<ms>`
(`quarantine_corrupt_state`) — never silently regenerated, because regenerating
keystore/keychain secrets permanently orphans everything sealed by the old ones; the
login-time `migrate()` upgrades old formats (key material into keystore handles,
identity encoding, gsa password encryption) exactly once.

---

## 5. Incoming pipeline

### 5.1 The receive loop (`start_loop` → `recv_wait`)

`recv_wait` selects over four sources: the APNs broadcast queue, the IDS registration
watch channel, the local message channel, and the cancel channel. APNs messages are
dispatched by SHA-1 topic (`HANDLER_TOPICS` in `api.rs`, mirroring rustpush's own
gates):

| Topics | Handler | Emits |
|---|---|---|
| `com.apple.private.alloy.fmf` / `fmd` / `findmy.itemsharing-crossaccount` | `fmfd.handle` | `BeaconShared` (item share offers) |
| `com.apple.sharedstreams` | sharedstreams | `NewPhotostream` (pending album invites) via local broadcast |
| `com.apple.icloud.presence.mode.status` / `com.apple.private.alloy.status.keysharing` / `com.apple.private.alloy.status.personal` (+ any channel notification) | statuskit | `StatusUpdate` |
| kcsharing containers / securityd / kcsharing.invite | passwords | internal sync |
| `com.apple.idmsauth` | `IdmsAuthListener` → `handle_2fa`/`handle_circle` | `Idms` debug events; drives in-app 2FA approval (`ActiveCircleSession`), `TwoFaAuthEvent` |
| facetime.multi / facetime.video | `ft_client.handle` | `FaceTime(UFtMessage)` |
| everything else | `IMClient::handle` (madrid + sms-relay topics) | `IMessage(MessageInst)` |

Registration-state changes emit `RegistrationState` and drive the auto-renew ladder
(§5.4). Panics inside the loop are caught and logged; the loop continues.

### 5.2 Pointer queue and callbacks

Every produced `PushMessage` is stored in `QUEUED_MESSAGES` (an in-memory map behind a
monotonic pointer id) and delivered to Kotlin as `receievedMsg(ptr, retry)` via
`spawn_blocking`. If Kotlin has not completed the pointer within 30 s, Rust re-emits it,
up to 5 retries, then drops it. `TwoFaAuthEvent` is delivered straight to
`twofaEvent` without queueing.

Kotlin (`NativePushService.receievedMsg`) must:

1. `ptrToMessage(ptr)` → `UPushMessage`.
2. Dispatch: `ProcessQueue` → drain the journal (below); `RegistrationState` → refresh
   registration UI state; anything else → `MessageIngestor.ingestWithResult` (ObjectBox
   write, notifications, attachment auto-download, group-icon/transcript-background
   side effects).
3. `completeMessage(ptr)` — **only after ingest succeeded**. On exception, leave it
   queued; Rust re-emits with backoff.

### 5.3 The durable journal (`messages.journal`)

`IMessage` events are journaled *before* the pointer is emitted (journal writes fsync on
a blocking-pool thread, never a runtime worker). The journal is a length-prefixed
append-only log of `Message`/`Attempt`/`Finish` records (binary plist), compacted with
an amortized policy (rewrite only once the file doubles, or when the live set empties).
On boot, `readQueuedJournal()`/`markJournalAttempt(id, ok)` drain it:

- `readQueuedJournal` returns the oldest entry `(id, attempts, UPushMessage)`.
- `markJournalAttempt(id, true)` finishes the entry; `false` records a retry. Kotlin
  sleeps 2 s / 10 s / 30 s→240 s between attempts; **after the third failure Rust drops
  the entry as poison** (an undecodable payload must not wedge every later message).
- `ProcessQueue` push events trigger a journal drain; the service also drains once at
  boot, silently (entries may overlap history sync or already-rendered rows).

### 5.4 Send confirmation and registration renewals

- **`SendConfirm`**: every send export returns a staged `UMessageInst` immediately; the
  actual APNs fan-out runs in the background. `watch_send_progress` clears the
  "sending" state on the **first recipient-device ack** (a sender-own-device ack does
  not confirm anything), and only emits a terminal failure if nothing was ever
  delivered. Kotlin's `MessageIngestor` keeps a pending-confirm window keyed by the
  staging guid.
- **IDS re-auth**: when registration reports a `6005` auth failure, the loop tries once
  to silently renew the Apple session (`refresh_apple_ids_auth`: password re-login +
  delegate re-fetch + `authenticate_apple`). If that fails, `get_regstate()` reports
  `Failed` with a stable message prefix — `"Apple ID session expired. Sign in again…"`
  or `"Apple ID verification required…"`. Kotlin maps those to
  `ACCOUNT_REAUTH_REQUIRED`/`ACCOUNT_TWO_FACTOR_REQUIRED` states while **keeping the
  APNs session alive** so in-flight messages and the journal still drain during
  re-login.

---

## 6. Outgoing: the send surface

All live on `NativePushState`. Network sends are `suspend` functions (§2.2) and return
the staged `UMessageInst` — its `id` is the staging guid Kotlin persists for the
ObjectBox row before the network transfer finishes.

### 6.1 Conversations and the group-version invariant

`UConversation { participants, cv_name, sender_guid, after_guid }`. Participants are
`mailto:`/`tel:`-prefixed URIs **including the sender**; `cv_name` is the group name;
`after_guid` anchors reply threads. Group membership changes (`ChangeParticipants`,
`IconChange`) carry a `group_version` that **Kotlin owns**: start from the version of
the last incoming change message and bump by exactly one per outgoing mutation
(`chat.groupVersion = (chat.groupVersion ?? -1) + 1` semantics from the Dart client).
Rust does not track it.

### 6.2 Sends

| Method | Notes |
|---|---|
| `send_text(conversation, sender, text, reply_guid?, reply_part?, effect?, subject?)` | Single text part |
| `send_parts(...)` | Full part list: text (with `TextFormat` JSON), mentions, attachment XMLs |
| `send_typing(conversation, sender, typing)` | Typing indicator |
| `send_read(conversation, sender, message_guid)` | Read receipt; the instance id is set to the newest acked message guid — pass that, not a fresh id |
| `send_reaction(...)` | Tapbacks: `reaction_idx` 0 heart, 1 like, 2 dislike, 3 laugh, 4 emphasize, 5 question, 6 + `emoji` for custom; `enable=false` removes; `to_uuid`/`to_part` identify the target part |
| `send_sticker(...)` | Uploads the file (progress callback), then sends a sticker-reaction with normalized 0..1 coordinates, radians rotation, 0.1..4 scale, `effect_type` |
| `send_attachment` / `send_attachments` | Upload then send one/many files as parts of a single message, optional leading caption text; parallel `mimes`/`utis`/`names` arrays |
| `edit_message`, `unsend_message` | `to_uuid` = original guid, `edit_part` = part index; edits carry the full replacement part list |
| `rename_chat`, `change_participants`, `leave_chat` | Group ops; `change_participants` takes the *full* new list (adds/removes inferred by diff); `leave_chat` filters the sender out (prefix-insensitive) |
| `set_group_icon` / `remove_group_icon` | 570×570 PNG upload → `IconChange` message |
| `send_profile` | Sends the JSON produced by `set_profile` (§11.6) |
| `send_sms(conversation, sender, text, using_number?, from_handle?, ...)` | SMS relay (`MessageType::SMS`); `using_number` defaults to the first registered `tel:` handle; `from_handle` marks a forwarded message. Historically a **sync** export (`block_on`) — call it from `Dispatchers.IO`, not from a delegate/Tokio callback |

`sms_targets_for(handle, refresh)` lists SMS-relay-capable devices (`PrivateDeviceInfo`)
for the forwarding UI. `report_spam(handle, messages)` files spam reports via IDS.

Read receipts identify the newest message they acknowledge — that is why `send_read`
overrides the fresh envelope id with the target message guid.

### 6.3 Attachments

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

---

## 7. Message model reference (`UMessage`/`UPart`/`UMessageInst`)

`UMessageInst { id, sender?, conversation?, message, sent_timestamp, send_delivered,
verification_failed }` wraps every event. The `UMessage` variants and what Kotlin must
do with each (the `MessageIngestor` is the reference implementation):

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
| `UpdateProfile`, `UpdateProfileSharing`, `ShareProfile` | json | Feed `fetch_profile` (§11.6) |
| `SetTranscriptBackground` | json, version, chat_id?, remove, mmcs_xml? | Apply chat wallpaper via `TranscriptBackgroundStore`; never let it fail the journal entry |
| `UpdateExtension`, `PeerCacheInvalidate`, `Unschedule` | json / — | App-balloon state / identity-cache invalidation / scheduled-send cancel |

`is_sms` on `Normal` and the `SmsConfirmSent`/`EnableSmsActivation` pair cover the SMS
relay path; `isFromMe` in intake is decided by matching `sender` against
`get_handles()`.

For the rustpush-side wire details (plist keys `t`, `x`, `bid`, `amt` tapback codes,
`ia-0`/`ia-1` inline attachments, gzip rules) see `rustpush/src/imessage/rawmessages.rs`
— Kotlin never touches wire plists; it consumes the `U*` projection.

---

## 8. Login, provisioning, and account lifecycle

### 8.1 Provisioning (writes `hw_info.plist`)

- `provisionFromValidationData(dir, data, extra)` — 517-byte `0x02`-prefixed validation
  envelope extracted from a real Mac, plus `UHwExtra` (macOS version, protocol version
  1660, device id, iCloud/AOSKit UA strings). One-time per install.
- `provisionFromEncoded(dir, encoded)` — the `OABS` QR payload after the magic +
  sharing flag; carries the full config so no extras needed.
- `provisionFromRelay(dir, code, host, token?)` — hosted hardware-relay bridge
  (`hw.openbubbles.app`-style). Supported but **not the default path**; self-hosted
  OABS + on-device validation is.
- `hasHardwareConfig(dir)` gates the login UI's step. Provisioning = fresh NGM identity
  + `setup_push` (Albert activation → push cert/token) + persisted hardware state.
- `repairICloudServices(dir)` — deletes *only* iCloud service files (keychain,
  CloudKit, passwords, Find My, FaceTime, shared streams, StatusKit key) keeping the
  Apple session, IDS registration, and hardware identity. Recovery for service state
  corrupted before writes were atomic: stop the push service, call this, sign in again,
  re-join iCloud Keychain.

### 8.2 `ULoginSession` — the login state machine

Create with `createLoginSession(dir, delegate)` after provisioning; it fails `NotReady`
if `hw_info.plist` is missing or the stored identity won't decode. All methods are
**synchronous** (`RUNTIME.block_on`) — call from a background thread
(`LoginViewModel` serializes them; `RustLoginHandle` drops the session after a failure
so a poisoned lock cannot brick retries). The delegate fires on the calling thread
before the method returns.

States (`ULoginState`): `LoggedIn`, `NeedsDevice2Fa`, `Needs2FaVerification`,
`NeedsSms2Fa`, `NeedsSms2FaVerification { phone_id, mode }`, `NeedsExtraStep { detail }`,
`NeedsLogin`. Stages (`ULoginStage`): Connecting → Authenticating → AwaitingDevice2Fa /
FetchingSmsOptions / SendingSmsCode → VerifyingCode → RegisteringIds → Finished.

The internal `pump` drives the machine until a state needing user input:

```
session.connect()                     // optional; login() auto-connects (APS + anisette)
session.login(user?, pass?)           // creds lowercase; nulls reuse saved gsa.plist
  → Needs2FaVerification              // trusted-device code path (default)
      session.submit2faCode("123456") // code shown on a trusted Apple device
  → NeedsSms2Fa                       // via requestSmsFallback() or automatic
      getSmsPhoneOptions() → chooseSmsPhone(id)   // single option auto-sends
      session.submit2faCode("987654") // SMS code
  → NeedsExtraStep                    // Apple terms / account update
      getUpdateAccountPage() → show HTML → completeUpdateAccount()
  → LoggedIn
session.register()                    // → Registered (id.plist written) | AppleBlocked{...}
initNative(dir, null, handler)        // rebuild the live state; reloadAfterLogin
```

Details that matter:

- `login` with no credentials resumes the saved session from `gsa.plist`
  (`savedLoginUsername`, `hasSavedUsers`).
- Device 2FA uses a proximity circle session — `on_circle_session(Some(sid))` tells
  Kotlin to advertise the BLE GATT service with that UUID (modern Apple devices refuse
  the join without it); `None` clears the surface.
- `register()` collects the Apple user + any phone users; `AppleBlocked` mirrors
  Apple's support-alert dialog (registration stops until acknowledged). On
  `Registered`, `id.plist` is written — the state must be rebuilt with `initNative`.
- `setNewIdentity()` rotates the NGM identity, resets anisette, and resets the session
  to `NeedsLogin`. `resetConnection()` re-dials APS with a fresh push token (required
  before SMS-gateway phone registration) while keeping account/login state.

### 8.3 Phone (carrier SMS) registration

Two paths, both storing per-subscription `IDSUser`s in the session:

- **SMS-less (EAP-AKA)**: `getCarrier(handler, mccmnc)` resolves the carrier gateway,
  then `smsLessAuth(subscription, mccmnc, subscriber, imei, UEapAkaHandler)`. The
  handler answers carrier challenges from the Android telephony stack; returning an
  empty string aborts.
- **SMS gateway**: `authPhone(subscription, number, sig)` with the gateway response
  parts (`number|sig`, sig hex-decoded).

Cache phone users with `exportPhoneUsers()`/`importPhoneUser()` (validated against the
live connection; a stale cert returns `false` and the cached entry must be discarded).

### 8.4 Keypad-style 2FA approvals (runtime)

When another device requests sign-in, the IDMS path in the receive loop (§5.1) builds an
`ActiveCircleSession`; `getAuthCode(txnid)` returns the OTP the user must enter on the
requesting device (or falls back to the anisette 2FA code), `teardown2fa(action,
txnid)` aborts, and `TwoFaAuthEvent` reports the outcome.

---

## 9. Keystore

Rust keys never live in plaintext on disk except inside the Android Keystore / desktop
software keystore state.

- `setupKeystore(dir, NativeKeystore)` picks the backend: if `keystore.plist` exists →
  hardware-backed `BackupKeystore`; else if `keystore_s.plist` exists → software; else
  it *probes* the hardware implementation (`supports_import`: RSA import + P-384
  ECDH derive) and initializes accordingly. An unparsable state file is quarantined,
  never regenerated (§4.2).
- The foreign trait `NativeKeystore` (implemented by `AndroidNativeKeystore` in
  `app-native/`, package `com.bluebubbles.messaging.services.rustpush`) exposes
  create/destroy/list/import/sign/verify/public-key/derive/encrypt/decrypt. Rust-side
  `keystore.rs` wraps imports in an ASN.1 `KeyWrapper` (RSA-OAEP-wrapped AES-GCM
  transport key + keymaster `AuthorizationList`) so private keys transit as ciphertext.
- Locking: `isLocked()`, `finishUnlock()`, `doLock()`, `recoverKeychain()`; the Android
  side gates unlock behind biometrics and `RustBoot.unlockKeystore` bridges the prompt.
- Key alias registry (what lives where):

| Alias | Type | Purpose |
|---|---|---|
| `activation:{serial}` | RSA-1024 (SHA-1/PKCS1) | Albert push certificate |
| `ids:{user_id}` | RSA-2048 | IDS auth CSR / registration keypair |
| `keychain:signing:{mid}`, `keychain:encryption:{mid}` | EC P-384 | Octagon peer identity |
| `keychain:cloudkey-access-key:{dsid}` | secret (64 B) | unwraps synced CloudKeys |
| `gsa:password` | AES-256-GCM | `gsa.plist` password at rest |
| `ids:identity-storage-key:{tag}` | AES | NGM identity serialization |

`rustpush/keystore/` defines the traits; `backup.rs` is the hybrid
hardware-keystore + state-file implementation; `software.rs` the desktop one (whose
`SoftwareEncryptor` key is a fixed literal — desktop is best-effort, Android is the
hardened path).

---

## 10. CloudKit message-history sync

### 10.1 Availability and keychain trust

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

### 10.2 Pulling history (chats → messages)

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

### 10.3 Record shapes (what Kotlin maps to ObjectBox)

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

### 10.4 The upload half (re-sync after local edits)

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

---

## 11. iCloud services beyond messages

All live on `NativePushState` and require `icloud_services` (sign-in); several also
require the keychain clique. `NotReady` errors mean exactly that precondition.

### 11.1 iCloud Passwords / vault

`syncPasswords()` (pull Passwords/Wi-Fi/credit-card zones + refresh groups, then push
Wi-Fi networks to the boot callback), `listPasswords(kind: Password|Code|Passkey|Wifi)`,
`revealPassword(id, kind)` (TOTP codes generate + expiry), `createPassword`,
`deletePassword` (removes credential + paired metadata; code-delete keeps the
password), `addPasswordTotp(site, username, setupString)`. Groups:
`listPasswordGroups`, `createPasswordGroup`, `renamePasswordGroup`,
`deletePasswordGroup` (delete-if-owner / leave-if-shared), invites:
`listPasswordGroupInvites`, `acceptPasswordGroupInvite`, `decline…`,
`invitePasswordGroupMember` (validates the handle can receive invitations; owner-only),
`removePasswordGroupMember`. Legacy insert hooks (`keychainPasswordInsert`,
`keychainPasskeyInsert`, `getSiteConfig`) remain on the state object for the
autofill/credential service surfaces.

### 11.2 Shared Albums

`listSharedAlbums(refresh)`, `acceptSharedAlbum(id)` / `acceptSharedAlbumToken(token)`,
`setSharedAlbumSync(id, folder?)` (enable/stop local sync), `syncSharedAlbums()`,
`listSharedAlbumAssets(id)`.

### 11.3 Find My

- Devices: `getDevices()` (cached; client created on first call), `refreshDevices()`.
- Friends: `getFollowing()` / `refreshFollowing()` via the fmfd daemon.
- Items: `getBeaconItems()` (syncs positions), `getCachedBeaconItems()`,
  `acceptBeaconShare(shareId)` (from `BeaconShared` pushes), `deleteBeaconShare`,
  `updateBeaconName`.
- There is no "play sound" — not implemented upstream.

### 11.4 FaceTime

`ftSessions()` (active + known sessions for caller resolution), `getFtLink(usage)`,
`rotateIncomingLinks()`, `startFacetimeCall(uuid, handle, participants)` (validates
targets, reserves + rotates the link atomically, returns it), `createFacetime`,
`cancelFacetime(guid)`, `declineFacetime(guid)`, `approveLetMeIn(...)` (knock-to-join).
Incoming `UFtMessage` variants (Ring/Decline/JoinEvent/AddMembers/RemoveMembers/
LeaveEvent/LinkChanged/RespondedElsewhere/LetMeInRequest) arrive through the push loop
and are routed by `FaceTimeDispatch` before message ingest.

### 11.5 StatusKit

`publishStatus(guid?)` publishes presence (null = active). `StatusUpdate` pushes carry
`user/mode/allowed`.

### 11.6 Profiles and contact posters

`fetchProfile(profileJson)` resolves a `ShareProfile`/`UpdateProfile` message JSON to
the sender's shared name/avatar/poster (`UNicknameRecord`). `setProfile(name, first,
last, image?, poster?, existingJson?)` publishes this account's profile and returns the
new `ShareProfileMessage` JSON — persist it and `sendProfile(...)` it into
conversations. Posters parse/render through opaque objects:
`parsePoster(zipBytes)` → `UTranscriptPoster` (chat wallpaper: `watch()` background
bytes, `kind()`, `titleLuminance()`, `photoFiles(i)`), `parseCallPoster(UPosterRecord)`
→ `UCallPoster` (`textMetadata()`, `lowResImage()`, …); both save/restore as binary
plists (`save`/`restore*Save`).

### 11.7 Contacts and misc

`getContactsHeaders()` mints short-lived CardDAV headers (family auth token + mme
token; never the password) for the contacts sync. `getQuotaInfo`-equivalent lives
behind `TokenProvider::get_storage_info` in Rust (not yet a UniFFI export).

---

## 12. rustpush internals you will most likely touch

Orientation for protocol changes; everything below is internal to the submodule and
**not** Kotlin-visible until you mirror it (§13.1).

- **`OSConfig`** (`src/lib.rs`) abstracts the spoofed device: `MacOSConfig`
  (`macos.rs`, `HardwareConfig` + version strings) and `RelayConfig` (`relay.rs`).
  Notable methods: `generate_validation_data()` (absinthe circuit vs relay call),
  `build_activation_info(csr)`, `get_gsa_config(push, require_mac)`, `get_private_data()`.
- **`ResourceManager<T>`/`Resource`** (`util.rs`): generate-with-backoff wrapper.
  `APSConnection` and the IDS `IdentityManager` are both ResourceManagers — call
  `ensure_ready`/`refresh_now`; subscribe `generated_signal` for reconnect events.
  Errors: `ResourceTimeout`/`ResourceGenTimeout`/`ResourceStalled`/`ResourceFailure`.
- **APS** (`aps.rs`): connect = optional Albert `activate()` → signed `Connect` →
  `ConnectResponse`; 60 s ping/pong keepalive; auto-ack notifications; topic
  `Filter`s are refcounted via `APSInterestToken` (drop = unsubscribe);
  `send_message(topic, data, id)` rides a Notification + Ack; `SignedRequest::send_apns`
  tunnels IDS HTTP over APNs. Wire is the packed format when ALPN negotiates it.
- **IDS** (`ids/`): `register()` posts per-user registrations (auth cert per user,
  NGM prekeys, capabilities via `IDSService.client_data` — `MADRID_SERVICE` is the
  iMessage capability set); `IdentityManager` re-registers on heartbeat/expiry and
  keeps `KeyCache` (delivery keys per service/handle, invalidated by
  `sha1(id_cert‖push_token)` change; per-peer NGM send counters; single-flight batched
  lookups, 18 handles/query). Sending (`send_message`) encrypts per-target
  (`pair-ec` NGM or legacy `pair`), bundles ≤5000 B, retries stragglers up to 5 rounds
  (`SendResult::Sent | APSError(code) | TimedOut`).
- **IMClient** (`imessage/aps_client.rs`): `handle(APSMessage)` → decrypt →
  `process_msg` dispatch (receipts, typing, errors incl. 802 key invalidation, SMS
  activation auto-reply, full `from_raw` decode). `send(MessageInst)` picks the
  sms-relay or madrid topic, `prepare_send`s the envelope, pre-fetches keys, and
  hands to `IdentityManager`; queued/scheduled messages first sync a copy to our own
  devices only.
- **Wire format** (`rawmessages.rs`): binary plists, gzipped unless the body contains
  XML (`x`/`ix`); key map in §7 and the file itself. New IDS command values must be
  reflected in the inbound raw parser list.
- **MMCS** (`icloud/mmcs.rs`): uploads/downloads are negotiated over madrid commands
  c=150/c=151 via APNs; files are chunked at 5 MB with per-chunk signatures; iMessage
  transfers wrap content in a streaming AES-256-CTR `IMessageContainer` (zero nonce,
  first key byte discarded); CloudKit assets use the v2 "FORD" path (HKDF-derived
  per-chunk keys + AES-256-SIV-protected key metadata that becomes the record's
  protection info). `MMCSFile::prepare_put` mints the key/size/signature.
  `MMCSMatcher` streams chunks source→target with progress callbacks.
- **CloudKit web API** (`icloud/cloudkit.rs`): `CloudKitClient` (dsid + token provider)
  → `CloudKitContainer::init` → `CloudKitOpenContainer` (session, zone-key cache).
  Operations are protobuf `RequestOperation`s (ULEB128-delimited, gzipped) against
  `gateway.icloud.com`: record save/fetch/delete/query, `FetchRecordChangesOperation`
  (the `/record/sync` engine under all `sync_*_page` exports — it loops until every
  zone's status is 3, threading the continuation token), assets, zones, shares,
  functions (Cuttlefish). Encrypted zones get a `PCSZoneConfig`; record crypto goes
  through `PCSEncryptor` (AES-128-GCM under HKDF-derived `PCSKey`s). 401 → mme token
  refresh; 429 → `TooManyRequests`.
- **Keychain / Octagon** (`icloud/keychain.rs`): `KeychainClient` over the cuttlefish
  container (`FunctionInvokeOperation`: fetchChanges / updateTrust / joinWithVoucher /
  establish / reset). `sync_keychain(zones)` pulls CKKS items — the zones include
  `Passwords`, `WiFi`, `CreditCards`, `Engram` (messages), `ProtectedCloudStorage`,
  `Photos`, and more; each item is AES-SIV-wrapped under keystore-backed access keys,
  so the state file never holds plaintext secrets. IDS keys do **not** live here; the
  P-384 peer identity is the circle identity, and `ProtectedCloudStorage` items are
  the trust root for every other dataclass's PCS service keys — which is why history
  sync, passwords, and Find My items all gate on the clique being joined.
- **PCS** (`icloud/pcs.rs`): service-key hierarchy (master key → per-service keys →
  per-record `PCSKey`s) and share protection; modules touch it only through
  `get_zone_encryption_config` / `prepare_put_v2` boundary keys.
- **The service-client shape**: every long-lived rustpush client follows one pattern —
  `state: DebugRwLock<State>` + an `update_state` persistence callback (the host
  writes it atomically), a held `APSInterestToken` for its topics, and a
  `handle(APSMessage)` dispatcher fed by the shared APNs connection. Copy this shape
  for any new push-driven service.
- **Errors** (`error.rs`): `PushError` groups as GSA/login, IDS, sending, APS,
  activation/anisette/relay, resource, crypto/encoding, circle/keychain/escrow,
  domain. Callers mostly string-render them (`UError.reason`).

Features (`rustpush/Cargo.toml`): `macos-validation-data` (default; source-builds
open-absinthe for on-device validation), `remote-anisette-v3` (**active** in `rust/`'s
dependency line — remote anisette provisioning), `remote-clearadi` (self-contained
emulated ADI, alternate provider). The two remote features are provider selections;
on real macOS `AOSKitAnisetteProvider` wins. `rust/` pins
`features = ["macos-validation-data", "remote-anisette-v3"]`.

### 12.1 open-absinthe (on-device validation)

`rustpush/open-absinthe/` is a source-recovered reimplementation of Apple's
FairPlay/"absinthe" validation engine — the thing that proves the spoofed Mac is real
to IDS without an official native library. `nac.rs` exposes `HardwareConfig`
(`from_validation_data` parses the 517-byte `0x02` envelope), `ValidationCtx::new`
(certificate chain + session-info request), `key_establishment`, and `sign()`. The
signing proof runs on a recovered architecture-neutral p-code circuit
(`proof_vm.rs`); Android debug builds differentially compare against a pinned
official `.so` oracle. To touch validation logic, read `RECOVERY.md` in that crate
first and keep the differential comparison green.

---

## 13. How to make changes

### 13.1 Add or change a Kotlin-visible API

1. Decide the owner file: `uniffi_ext.rs` for new methods/types; `native.rs` for
   boot/queue/receive-loop; `keystore.rs` for the keystore trait; `api.rs` for engine
   internals you must surface.
2. Protocol behavior goes in `rustpush/` first; expose only the application-level
   facade from `rust/`.
3. Mirror types as `U*` records/enums (`conv_*`/`back_*`), reuse existing mirrors,
   carry exotic payloads as `*_json`/`*_xml`.
4. Pick sync vs async by §2.2 — network waits are async exports driven by `drive_ffi`;
   everything else may `block_on`.
5. Regenerate and commit bindings:
   ```bash
   (cd rust && ./build-uniffi.sh)
   (cd native && ./gradlew :app-native:checkUniffiBindings --console=plain)
   ```
   Inspect the diff in `core/src/main/kotlin/uniffi/` (the script also copies it into
   the test source set). Never hand-edit generated Kotlin.
6. Add Kotlin behavior tests where the contract is testable on the JVM, and state which
   gates ran. There are **no `#[test]`s under `rust/src/`** — do not invent a
   `cargo test --manifest-path rust/Cargo.toml` gate; protocol unit tests are
   `cargo test --manifest-path rustpush/Cargo.toml --lib --locked`.
7. For the full contract-change procedure load the
   `openbubbles-uniffi-contract-change` skill.

### 13.2 Extend the message model

New `Message` variant → add encode (`to_raw` + a `Raw*` struct in `rawmessages.rs`),
decode (insert in the `from_raw` try-order), a `UMessage` mirror + `conv`/`back`,
`MessageIngestor` handling, and persistence mapping. Keep the variant list in
`messages.rs`'s `get_c()` comment in sync with new command values.

### 13.3 Change protocol behavior (rustpush)

Work in the submodule: commit + push inside `rustpush/` **first**, then update and
push the parent pointer separately. Run `cargo test --manifest-path
rustpush/Cargo.toml --lib --locked` from the repo root (APNs proxy/replay tests stay
`#[ignore]`). Bare cloud images need the FairPlay placeholders and
`native/local.properties` fixture first (see [DEVELOPMENT.md](DEVELOPMENT.md#cloudci-fixture-setup)).

### 13.4 Change persisted state

Add a field to a plist-backed struct → handle missing fields with `#[serde(default)]`
or a `migrate()` step in `api.rs`. Write via `atomic_write_plist`; on parse failure of
an existing file, quarantine (never regenerate secrets). New secret material belongs
in the keystore under a namespaced alias, not in the plist.

### 13.5 Change the receive loop / add a topic handler

Register the topic SHA-1 in `HANDLER_TOPICS` **and** mirror the gate inside the
rustpush handler's `handle()`; the api.rs table must stay behavior-preserving with the
rustpush topic sets. Emit a `PushMessage` variant + `UPushMessage` mirror; if it is an
`IMessage`-class event, decide journal-first (durable) vs direct callback.

### 13.6 Change the keystore contract

Trait change in `rustpush/keystore/` → update `NativeKeystore` in `rust/src/keystore.rs`
→ update `AndroidNativeKeystore.kt` → regenerate bindings. Keep import wrapping
(`wrap_import_key`) consistent with the ASN.1 `KeyWrapper` the Android side parses.

### 13.7 Evidence tiers

State which of these passed: JVM/Gradle gates (`:db:test :core:test
:app-native:testDebugUnitTest :db:checkModelParity :app-native:assembleDebug`),
cargo gate, screenshot gate, and device evidence (login/2FA/battery/upgrade per
[tools/CUTOVER.md](../tools/CUTOVER.md)). A green build is not a hardware protocol
oracle.

---

## 14. Invariant checklist

- One `RustBoot.ensureStarted` per process before any keystore-touching Rust call.
- Never call a sync UniFFI export from a Tokio callback thread (`nativeReady`,
  `receievedMsg`, `finish`, delegates) — hop to a Kotlin dispatcher first.
- Never re-enter Rust from inside a delegate callback; keep delegate bodies light.
- `completeMessage` only after `:core` ingest succeeded; journal entries are poison-
  dropped after three failed Kotlin attempts.
- Kotlin owns `group_version` (bump by one from the last seen value).
- Persist attachment/MMCS XML with the row it belongs to; transfers must survive
  restarts.
- Push CloudKit deletions before pulling; persist cursors only after applying; empty
  cursor = keep the previous one.
- `AppleBlocked` and empty escrow-bottle lists stop flows for a human — never paper
  over them.
- Never commit credentials or any config-dir file (§4.2); never regenerate ObjectBox
  UIDs; keep `:db:checkModelParity` green.
- Do not add FRB exports; do not hand-edit `core/src/main/kotlin/uniffi/`.
- Commit rustpush submodule changes first, then the parent pointer.

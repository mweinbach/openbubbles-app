# Incoming pipeline

Part of the [Rust backend reference](../README.md). Consumers:
[message model](message-model.md), `MessageIngestor` in `:core`. Related:
[lifecycle](../foundations/lifecycle.md), [outgoing](outgoing.md) (SendConfirm).

## The receive loop (`start_loop` → `recv_wait`)

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
(below). Panics inside the loop are caught and logged; the loop continues.

## Pointer queue and callbacks

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

## The durable journal (`messages.journal`)

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

## Send confirmation and registration renewals

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

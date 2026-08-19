# Boot and lifecycle — what Kotlin must do

Part of the [Rust backend reference](../README.md). Prerequisites:
[runtime model](runtime.md). Related: [incoming pipeline](../messaging/incoming.md),
[login](../account/login.md), [keystore](../account/keystore.md).

Order matters; every step is required before the next.

1. **`RustBoot.ensureStarted(context, dir)`** — once per process (idempotent, blocks
   concurrent callers). Inside: `uniffiEnsureInitialized()` → `start(dir, packager,
   wifiCallback)` (registers the `KotlinFilePackager` + wifi callback globals, creates
   the log dir) → `setupKeystore(dir, AndroidNativeKeystore)` (see
   [keystore](../account/keystore.md)). Provisioning and login both touch the keystore,
   so this must run even before the push service starts (onboarding does it from the
   activity).
2. **Provisioning** (fresh installs — see [login](../account/login.md)) writes
   `hw_info.plist`.
3. **Login** (same doc) writes `gsa.plist` / `id.plist` and friends.
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
6. **`startLoop`** runs the APS receive loop ([incoming](../messaging/incoming.md))
   until stopped; `finish()` fires on the receiver when it ends.
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

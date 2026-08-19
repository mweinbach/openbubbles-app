# Runtime and concurrency model

Part of the [Rust backend reference](../README.md). Related:
[lifecycle](lifecycle.md) (boot order), [outgoing](../messaging/outgoing.md)
(sync-vs-async per send call).

## The Tokio runtime

`rust/src/lib.rs` builds one process-wide multi-thread runtime named `tokio-rustpush`
with 2–4 workers (`available_parallelism().clamp(2, 4)`). It is deliberately small —
this is a phone — but deliberately never one thread: a single worker head-of-line blocks
the APNs socket behind journal writes and CloudKit pages.

## Sync vs async exports — the rule that prevents deadlocks

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

## Foreign callbacks (delegates)

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

## Logging

`init_logger` (called by `start`) fans every record to logcat (`android_logger`) and a
rotating file `<dir>/logs/rs_r*.log` (10 MB, 1 day, keep 1). Debug builds log at
`debug` (rustpush dumps full payload hex and per-page sync traces); release caps at
`info`, so `debug!` arguments are never evaluated. Panics are routed through `log` so
they reach logcat (`RUST PANIC: …` with backtrace). Debug-only `extern "C"` smoke
tests `openbubbles_debug_nac_round_trip[_saved]` run the account-free validation
handshake from ADB receivers.

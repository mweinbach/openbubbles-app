# Rust and Kotlin

```
app-native / desktopApp
        │
      :core   intake, repos  (committed UniFFI Kotlin)
        │  JNA → librust_lib_bluebubbles
      rust/   UniFFI facade, tokio, journal, U* mirrors
        │
      rustpush/   Apple protocols (git submodule)
```

Flutter Rust Bridge leftovers (`rust/src/frb_generated*.rs`, `#[frb]` in `api.rs`) still compile.
They are **not** the Kotlin API. Do not add new FRB exports.

## Where to change what

| Task | Edit | Do not |
|---|---|---|
| APS, IDS, iMessage, MMCS, CloudKit, GSA/2FA, FaceTime, Find My | `rustpush/src/` | Reimplement in Kotlin or `rust/` |
| New Kotlin-visible type, send/login/provision API, `U*` mapping | `rust/src/uniffi_ext.rs` (boot/queue: `native.rs`; crypto: `keystore.rs`) then `cd rust && ./build-uniffi.sh` | Hand-edit `core/src/main/kotlin/uniffi/` |
| Restore, APS watcher, journal internals | `rust/src/api/api.rs` | New `#[frb]` |
| Android Keystore / biometric unlock | `AndroidNativeKeystore.kt` + `rust/src/keystore.rs` if the trait changes | Android Keystore in `:core` |
| Foreground service, poll vs live, reconnect | `NativePushService.kt`, `BatterySaver.kt`, `RustBoot.kt` | A second APS loop from Compose |
| Persist / optimistic send / disk attachments | `:core` | ObjectBox writes from Rust |
| OABS QR / provision copy | `ProvisionScreen.kt` | Hosted-relay as the default path |
| Official-lib load / unavailable | `rustpush/open-absinthe/src/nac.rs` | |

`rustpush` types stay internal. Cross FFI as `U*` records/enums or plist/JSON.

## Boot and queue

[`RustBoot.ensureStarted`](../app-native/src/main/kotlin/app/openbubbles/nativeapp/data/RustBoot.kt)
is process-wide and must run before provision or login: `uniffiEnsureInitialized` → `start` →
`setupKeystore`.

[`NativePushService`](../app-native/src/main/kotlin/app/openbubbles/nativeapp/service/NativePushService.kt)
owns the live `NativePushState`:

1. Apply poll vs live from the start intent **before** `initNative`.
2. `initNative` restores on the one-worker Tokio runtime (`tokio-rustpush`) and calls `nativeReady`.
3. Hop off that thread immediately. Sync UniFFI methods `RUNTIME.block_on` — calling them from
   `nativeReady` / `receievedMsg` on Tokio **deadlocks**.
4. Incoming events are pointers into `QUEUED_MESSAGES` (`ptrToMessage` → ingest → `completeMessage`).
5. Complete a pointer **only after** `:core` ingest succeeds. Rust re-emits failures (30s, max 5).

Live mode: `startLoop`, `START_STICKY`. Battery-saver: WorkManager `POLL_ONCE`, one CloudKit
increment, `START_NOT_STICKY`. After login: `ACTION_RELOAD` bumps a generation so stale callbacks
are ignored.

## Login and provisioning

Provisioning writes `hw_info.plist` from an `OABS` Mac payload (or a 517-byte `0x02` envelope).
`ULoginSession` then drives credentials → trusted-device 2FA or SMS phone+code → terms / blocked /
`register()` (`id.plist`, `gsa.plist`).
[`LoginViewModel`](../app-native/src/main/kotlin/app/openbubbles/nativeapp/ui/login/LoginViewModel.kt)
serializes actions. [`RustLoginHandle`](../app-native/src/main/kotlin/app/openbubbles/nativeapp/ui/login/RustLoginHandle.kt)
drops the session after a failure so a poisoned lock cannot brick retries.

`ULoginDelegate` fires on the calling thread before the UniFFI method returns. Do not re-enter Rust
from a delegate.

Default anisette is remote-v3. Hosted hardware relay exists in the API and is not the shipping UI.

## Keystore and validation

Android: `AndroidNativeKeystore` behind `NativeKeystore`. Desktop restore uses the software
keystore. Treat `hw_info.plist`, `gsa.plist`, `id.plist`, and `keystore*.plist` as secrets.

On-device validation (OpenAbsinthe) loads the packaged `libopenbubbles_official.so` on arm64,
checks layout + UniFFI contract-version anchor, then runs Apple's validation handshake. Mismatch
is unavailable — no silent relay fallback. Debug account-free smoke: 517-byte envelope
(`openbubbles_debug_nac_round_trip`).

## Build

- Android `.so`: direct Cargo/NDK build (`app-native/cargo-android.gradle`). The native build does
  not require Dart or Flutter tooling.
- Bindings: `rust/build-uniffi.sh` → `core/src/main/kotlin/uniffi/`. Commit the result.
- Gate: `:app-native:checkUniffiBindings` (not in the default CI command).
- There are no `#[test]`s under `rust/src/`. Protocol unit tests are
  `cargo test --manifest-path rustpush/Cargo.toml --lib --locked`.
  APNs proxy/replay stay `#[ignore]`.

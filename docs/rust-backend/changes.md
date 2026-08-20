# How to make changes

Part of the [Rust backend reference](../README.md). Read the
[invariant checklist](#invariant-checklist) at the bottom before editing.

## Add or change a Kotlin-visible API

1. Decide the owner file: `uniffi_ext.rs` for new methods/types; `native.rs` for
   boot/queue/receive-loop; `keystore.rs` for the keystore trait; `api.rs` for engine
   internals you must surface.
2. Protocol behavior goes in `rustpush/` first; expose only the application-level
   facade from `rust/`.
3. Mirror types as `U*` records/enums (`conv_*`/`back_*`), reuse existing mirrors,
   carry exotic payloads as `*_json`/`*_xml`.
4. Pick sync vs async by the
   [runtime rule](foundations/runtime.md#sync-vs-async-exports--the-rule-that-prevents-deadlocks) —
   network waits are async exports driven by `drive_ffi`; everything else may
   `block_on`.
5. Regenerate and commit bindings:
   ```bash
   (cd rust && ./build-uniffi.sh)
   ./gradlew :app-native:checkUniffiBindings --console=plain
   ```
   Inspect the diff in `core/src/main/kotlin/uniffi/` (the script also copies it into
   the test source set). Never hand-edit generated Kotlin.
6. Add Kotlin behavior tests where the contract is testable on the JVM, and state which
   gates ran. There are **no `#[test]`s under `rust/src/`** — do not invent a
   `cargo test --manifest-path rust/Cargo.toml` gate; protocol unit tests are
   `cargo test --manifest-path rustpush/Cargo.toml --lib --locked`.
7. For the full contract-change procedure load the
   `openbubbles-uniffi-contract-change` skill.

## Extend the message model

New `Message` variant → add encode (`to_raw` + a `Raw*` struct in `rawmessages.rs`),
decode (insert in the `from_raw` try-order), a `UMessage` mirror + `conv`/`back`,
`MessageIngestor` handling, and persistence mapping. Keep the variant list in
`messages.rs`'s `get_c()` comment in sync with new command values.

## Change protocol behavior (rustpush)

Work in the submodule: commit + push inside `rustpush/` **first**, then update and
push the parent pointer separately. Run `cargo test --manifest-path
rustpush/Cargo.toml --lib --locked` from the repo root (APNs proxy/replay tests stay
`#[ignore]`). Bare cloud images need the FairPlay placeholders and
`local.properties` fixture first (see
[DEVELOPMENT.md](../DEVELOPMENT.md#cloudci-fixture-setup)).

## Change persisted state

Add a field to a plist-backed struct → handle missing fields with `#[serde(default)]`
or a `migrate()` step in `api.rs`. Write via `atomic_write_plist`; on parse failure of
an existing file, quarantine (never regenerate secrets) — see
[durability rules](foundations/state.md#durability-rules). New secret material belongs
in the keystore under a namespaced alias, not in the plist.

## Change the receive loop / add a topic handler

Register the topic SHA-1 in `HANDLER_TOPICS` **and** mirror the gate inside the
rustpush handler's `handle()`; the api.rs table must stay behavior-preserving with the
rustpush topic sets. Emit a `PushMessage` variant + `UPushMessage` mirror; if it is an
`IMessage`-class event, decide journal-first (durable) vs direct callback.

## Change the keystore contract

Trait change in `rustpush/keystore/` → update `NativeKeystore` in `rust/src/keystore.rs`
→ update `AndroidNativeKeystore.kt` → regenerate bindings. Keep import wrapping
(`wrap_import_key`) consistent with the ASN.1 `KeyWrapper` the Android side parses.

## Evidence tiers

State which of these passed: JVM/Gradle gates (`:db:test :core:test
:app-native:testDebugUnitTest :db:checkModelParity :app-native:assembleDebug`),
cargo gate, screenshot gate, and device evidence (login/2FA/battery/upgrade per
[tools/CUTOVER.md](../../tools/CUTOVER.md)). A green build is not a hardware protocol
oracle.

## Invariant checklist

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
- Never commit credentials or any config-dir file (see
  [state files](foundations/state.md#on-disk-the-config-dir-passed-to-startinit_native));
  never regenerate ObjectBox UIDs; keep `:db:checkModelParity` green.
- Do not add FRB exports; do not hand-edit `core/src/main/kotlin/uniffi/`.
- Commit rustpush submodule changes first, then the parent pointer.

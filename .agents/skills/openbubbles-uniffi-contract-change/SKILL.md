---
name: openbubbles-uniffi-contract-change
description: Safely evolve a Kotlin-visible Rust UniFFI API, enum, callback, event, or result mapping and regenerate committed bindings. Use only when the cross-language contract changes; not for Rust-internal or Kotlin-only edits.
---

# OpenBubbles UniFFI Contract Change

Read [../../../docs/RUST_KOTLIN.md](../../../docs/RUST_KOTLIN.md) and [../../../docs/DEVELOPMENT.md](../../../docs/DEVELOPMENT.md) before editing. The runtime/deadlock rules are in [../../../docs/rust-backend/foundations/runtime.md](../../../docs/rust-backend/foundations/runtime.md), the send/attachment surface in [../../../docs/rust-backend/messaging/outgoing.md](../../../docs/rust-backend/messaging/outgoing.md), and the mirroring conventions and binding-regeneration recipe in [../../../docs/rust-backend/changes.md](../../../docs/rust-backend/changes.md).

## Change the owned contract

- Put Apple protocol behavior in `rustpush/`; expose only the required application facade from `rust/`.
- Define Kotlin-visible records/enums/functions and mappings in `rust/src/uniffi_ext.rs` unless the documented boot, queue, or keystore owner applies.
- Keep Android framework types in `app-native/` and shared values Android-free.
- Do not add new Flutter Rust Bridge exports or hand-edit generated Kotlin.

Choose sync versus async from the operation, not caller convenience. Network transfers, sends, attachments, and CloudKit operations must be async/suspending. Do not call a synchronous `RUNTIME.block_on` export from `nativeReady`, `receievedMsg`, or another Tokio callback thread. Delegates fire on the calling thread; do not re-enter Rust from a delegate.

## Regenerate and inspect

From the repository root:

```bash
(cd rust && ./build-uniffi.sh)
(cd native && ./gradlew :app-native:checkUniffiBindings --console=plain)
```

Inspect the generated diff in `core/src/main/kotlin/uniffi/` and its test copy. Confirm the API shape, nullability, enum/result mapping, and `suspend` behavior match the intended contract. Commit generated bindings with the source change.

Run the focused Rust/Kotlin behavior tests plus the union of affected gates in [../../../docs/VERIFY.md](../../../docs/VERIFY.md). There are no unit tests under `rust/src/`; do not invent a `cargo test --manifest-path rust/Cargo.toml` gate.

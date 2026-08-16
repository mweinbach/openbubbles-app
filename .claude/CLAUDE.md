# OpenBubbles

The shipping client is Kotlin + Rust. Follow [../AGENTS.md](../AGENTS.md).

The Android `.so` is built directly by Cargo + the pinned NDK through
`app-native/cargo-android.gradle`. Never make Dart, Flutter, or Cargokit a native build dependency.

Do not implement from Flutter / GetX / Dart ObjectBox recipes. Historical files:

- `docs/DECISIONS.md`, `docs/COMMON_TASKS.md`, `docs/MESSAGE_*_FLOW.md`, `docs/models.md`

Current:

- `docs/ARCHITECTURE.md` — modules and runtime
- `docs/UI.md` — Compose design and navigation
- `docs/RUST_KOTLIN.md` — UniFFI boundary
- `docs/PERSISTENCE.md` — ObjectBox contract
- `docs/VERIFY.md` — what tests prove

# OpenBubbles

Kotlin + Rust client. Follow [AGENTS.md](AGENTS.md).

Android builds `rust/` directly with Cargo + the pinned NDK via
`app-native/cargo-android.gradle`. Do not add Dart, Flutter, or Cargokit to Gradle or CI.

Flutter documents under `docs/DECISIONS.md`, `docs/COMMON_TASKS.md`, `docs/MESSAGE_*_FLOW.md`,
`docs/models.md`, and historical `.claude/rules` files are not the implementation architecture.

# GitHub Copilot Instructions — OpenBubbles

The shipping client is Kotlin + Rust. Start from [AGENTS.md](../AGENTS.md).

Android compiles `rust/` directly with Cargo and the pinned NDK via
`app-native/cargo-android.gradle`. Do not introduce Dart, Flutter, or Cargokit into the Gradle or
CI build.

## Current docs

- [AGENTS.md](../AGENTS.md) — orientation, commands, constraints
- [docs/ARCHITECTURE.md](../docs/ARCHITECTURE.md) — modules and runtime
- [docs/UI.md](../docs/UI.md) — Compose design and navigation
- [docs/RUST_KOTLIN.md](../docs/RUST_KOTLIN.md) — UniFFI boundary
- [docs/PERSISTENCE.md](../docs/PERSISTENCE.md) — ObjectBox contract
- [docs/VERIFY.md](../docs/VERIFY.md) — what tests prove
- [CONTRIBUTING.md](../CONTRIBUTING.md) — submodules and review
- [tools/CUTOVER.md](../tools/CUTOVER.md) — device/release gates

## Historical (do not implement from)

`legacy/flutter/docs/` describes the retired Flutter client. There is no `lib/` application tree.
Retained Flutter Rust Bridge sources are legacy Rust compatibility surface, not the Kotlin API or
Android build system.

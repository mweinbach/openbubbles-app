# Docs

Current (Kotlin + Rust):

- [DEVELOPMENT.md](DEVELOPMENT.md) — evidence-first change loop, device proof, handoff
- [ARCHITECTURE.md](ARCHITECTURE.md) — modules, login, send/receive, background modes
- [UI.md](UI.md) — Compose design, Navigation3, screens, recipes
- [RUST_KOTLIN.md](RUST_KOTLIN.md) — UniFFI, rust vs rustpush, queue, keystore
- [DATA_LIFECYCLE.md](DATA_LIFECYCLE.md) — account work, caches, staging, retries, cleanup
- [PERSISTENCE.md](PERSISTENCE.md) — ObjectBox path, parity, who writes
- [VERIFY.md](VERIFY.md) — Gradle/cargo vs device evidence
- [RELEASES.md](RELEASES.md) — versioning, signing, Update Ledger, release evidence
- [agents/issue-tracker.md](agents/issue-tracker.md) — issue intake, deduplication, durable specs

Release checklist: [../tools/CUTOVER.md](../tools/CUTOVER.md).
Agent entry: [../AGENTS.md](../AGENTS.md).

Native Android build rule: Cargo + pinned Android NDK through
`../app-native/cargo-android.gradle`; no Dart, Flutter, or Cargokit tooling.

Historical Flutter (do not implement from): `DECISIONS.md`, `COMMON_TASKS.md`,
`MESSAGE_RECEIVE_FLOW.md`, `MESSAGE_SEND_FLOW.md`, `models.md`.

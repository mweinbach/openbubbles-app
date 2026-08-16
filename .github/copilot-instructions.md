# GitHub Copilot Instructions — OpenBubbles

The shipping client is Kotlin + Rust. Start from [AGENTS.md](../AGENTS.md).

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

`docs/DECISIONS.md`, `docs/COMMON_TASKS.md`, `docs/MESSAGE_*_FLOW.md`, `docs/models.md`
describe the retired Flutter client. There is no `lib/` application tree.

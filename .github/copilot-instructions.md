# GitHub Copilot Instructions — BlueBubbles App

This project uses CLAUDE.md files as the authoritative source for all coding conventions, architecture guidance, and workflow rules. Follow them exactly.

## Required Reading (load before making changes)

- **Project overview & key conventions** → [CLAUDE.md](../CLAUDE.md)
- **Workflow, code generation, lint** → [.claude/CLAUDE.md](../.claude/CLAUDE.md)

## Coding Standards (`.claude/rules/`)

Load the relevant rule file(s) before writing any code:

- **Frontend / Flutter UI** → [.claude/rules/frontend.md](../.claude/rules/frontend.md)
- **HTTP & backend operations** → [.claude/rules/api.md](../.claude/rules/api.md)
- **ObjectBox database** → [.claude/rules/database.md](../.claude/rules/database.md)
- **Services, events, method channels** → [.claude/rules/services.md](../.claude/rules/services.md)
- **Git commit format** → [.claude/rules/git.md](../.claude/rules/git.md)

## Architecture Docs (`docs/`)

For non-trivial tasks, consult the relevant doc:

- [docs/ARCHITECTURE.md](../docs/ARCHITECTURE.md) — how major subsystems work and interact
- [docs/DECISIONS.md](../docs/DECISIONS.md) — why key design choices were made
- [docs/COMMON_TASKS.md](../docs/COMMON_TASKS.md) — step-by-step recipes for frequent tasks
- [docs/MESSAGE_RECEIVE_FLOW.md](../docs/MESSAGE_RECEIVE_FLOW.md) — socket → queue → DB → state → UI
- [docs/MESSAGE_SEND_FLOW.md](../docs/MESSAGE_SEND_FLOW.md) — send button → tempGuid → HTTP + socket race → real GUID swap

## Directory-Scoped CLAUDE.md Files

Many directories contain a `CLAUDE.md` with context specific to that layer. Always check for one before editing files in a directory. Key examples:

- `lib/CLAUDE.md` — Dart/Flutter code conventions
- `android/CLAUDE.md` — Android native layer
- `lib/database/CLAUDE.md`, `lib/database/io/CLAUDE.md` — ObjectBox entities
- `lib/services/CLAUDE.md` — service layer guidance
- `lib/helpers/CLAUDE.md` — helper utilities
- `macos/CLAUDE.md`, `linux/CLAUDE.md` — desktop platform layers

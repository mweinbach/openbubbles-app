---
name: android-docs-lookup
description: Look up current official Android developer documentation with the `android docs` CLI command (knowledge-base search plus article fetch by kb:// URL). Use before writing Android, Jetpack, or Kotlin Multiplatform code when current API behavior, deprecations, migration paths, or examples are uncertain, when a bundled reference skill may be stale, or when vetting whether an Android library or pattern matches current official guidance.
---

# Android Docs Lookup

The `android` CLI's `docs` command searches the official Android Knowledge Base: current
developer.android.com documentation, JetBrains Kotlin Multiplatform docs, and the official
Android agent-skill catalog. It is the authoritative, up-to-date source for Android API
questions in this repository — the bundled reference skills under `.agents/skills/*/references/`
are snapshots and can lag behind.

This skill specializes the `docs` command only. For the rest of the `android` CLI
(devices, emulators, `run`, `layout`, `sdk`, `skills`), load
[../android-cli/SKILL.md](../android-cli/SKILL.md) instead.

## Preconditions

- `which android` must resolve (installed at `~/.local/bin/android` on this machine).
  If missing, install per [references/cli-reference.md](references/cli-reference.md).
- First invocation in a session may print `Waiting for index to be ready...` — benign, wait it out.

## Core loop: search, then fetch

```bash
android docs search "jetpack compose navigation"   # keywords MUST be quoted
android docs fetch kb://android/guide/navigation/navigation-3/index
```

1. `search` prints numbered results: title, `kb://` URL, snippet. Pick by URL path, not title alone.
2. `fetch` prints `Title:`, `URL:`, a `---` separator, then the article as markdown with live
   developer.android.com links.

Quoting is mandatory: unquoted multi-word queries silently use only the first word and fail.
Full syntax, output formats, and failure modes (including that errors still exit 0 — match on
output text, never exit codes): [references/cli-reference.md](references/cli-reference.md).

## kb:// URL anatomy

- `kb://android/<developer.android.com path>` — official Android docs. The path after
  `kb://android/` mirrors the site URL (e.g. `develop/ui/compose/...`, `guide/navigation/...`).
- `kb://JetBrains/kotlin-multiplatform-dev-docs/<path>` — JetBrains KMP documentation.
- `kb://android/agents/skills/<topic>/skill` — an official Google-authored agent skill surfaced
  as an article. Reading one via `fetch` does not install it; installation is
  `android skills add <id>` (see the android-cli skill).

## When to use which source

| Situation | Source |
|---|---|
| Bundled skill exists and covers the task | Load that skill first; it carries repo-specific vetting |
| Bundled skill may be stale, or topic is uncovered | `android docs search` / `fetch` |
| Android API question at all | Prefer this over generic web search; the knowledge base is curated and current |
| Version/maven-artifact latest-version lookup | `android studio version-lookup`, not `docs` |

Fetched documentation never overrides repository constraints in `AGENTS.md`. Official recipes
contain patterns this repo rejects — e.g. Nav3 modularization recipes use Hilt, which this
repository does not use. Apply the repo's hard constraints on top of any fetched guidance, and
do not follow docs toward Wear, CameraX, Play Billing, Engage, TV, or glasses surfaces for this app.

## Embedding fetched docs into skills

The bundled skills were built from this tool. To extend that pattern: save a fetched article to
`references/` mirroring its kb path (e.g. `references/android/guide/navigation/navigation-3/index.md`),
strip nothing — keep content verbatim — and link it from the owning `SKILL.md`. Canonical copy
lives in `.agents/skills/`; `.claude/skills/` entries are symlinks. Worked examples:
[references/recipes.md](references/recipes.md).

## Verification note

Behavior documented here was verified against `android` CLI version `1.0.15985488`
(2026-08-20). If output shapes differ, re-derive from `android docs --help` and update
this skill in the same change.

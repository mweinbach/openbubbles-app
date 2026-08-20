# `android docs` CLI reference

Verified against `android` CLI version `1.0.15985488` (2026-08-20). Re-derive from
`android docs --help` if shapes drift, and update this file in the same change.

## Commands

### `android docs search <query>`

```
Usage: android docs search [-h] <query>
Search Android documentation. Enclose keywords in quotes.
      <query>   The query to search the documentation for. Enclose keywords in
                  quotes.
```

- Single positional argument. Multi-word queries MUST be one quoted string.
- First run in a session prints `Waiting for index to be ready...` while the local
  knowledge-base index spins up; this is benign.

Output format (numbered list; title, URL, snippet per hit):

```
Searching docs for: jetpack compose navigation
1. Migrate from Fragment-based Navigation to Navigation Compose
   URL: kb://android/develop/ui/compose/migrate/migration-scenarios/navigation
   This document describes how to migrate an Android app from using Fragment-based Jetpack Navigation t...

2. Navigation in Compose
   URL: kb://JetBrains/kotlin-multiplatform-dev-docs/topics/compose/compose-navigation
   ...
```

Pick results by URL path, not title: result sets mix android docs, JetBrains KMP docs,
and official agent-skill articles with similar titles.

### `android docs fetch <url>`

```
Usage: android docs fetch [-h] <url>
Fetch an Android documentation article from a URL (kb://...)
      <url>    The specific URL of the documentation article to fetch. You can
                 find available URLs by running 'android docs search <query>'
                 first.
```

Output format — header block, `---` separator, then the article as markdown
(body links are live `https://developer.android.com/...` URLs):

```
Fetching docs from: kb://android/guide/navigation/navigation-3/index
Title: Navigation 3
URL: kb://android/guide/navigation/navigation-3/index
----------------------------------------
Navigation 3 is a new navigation library designed to work with Compose. ...
```

## Failure modes

| Symptom | Cause / handling |
|---|---|
| `search 'navigation' is not a valid command` | Unquoted multi-word query; the CLI parsed only the first token as the query and treated the rest as subcommand words. Re-run with the whole query in quotes. |
| `No document found for URL: kb://...` | Unknown `kb://` URL. Re-run `search` and copy the URL exactly. |
| `Waiting for index to be ready...` | Benign first-run index spin-up; wait for results. |

**Important:** failures above still exit with code 0. Never gate on exit codes; match on
output text (`No document found`, `is not a valid command`) instead.

## `kb://` URL scheme

| Prefix | Source |
|---|---|
| `kb://android/<path>` | Official developer.android.com docs; `<path>` mirrors the site URL (`develop/ui/compose/...`, `guide/navigation/...`, `training/...`, `reference/...`). |
| `kb://JetBrains/kotlin-multiplatform-dev-docs/<path>` | JetBrains Kotlin Multiplatform documentation. |
| `kb://android/agents/skills/<topic>/skill` | An official Google-authored agent skill rendered as an article. Reading it via `fetch` does NOT install it; install with `android skills add <id>` (see the android-cli skill). |

## Related commands (not this skill)

- `android skills list` / `find` / `add` / `remove` — manage installed Android CLI skills.
- `android studio version-lookup` — latest published versions of maven artifacts and Android
  versions; use this instead of `docs` for "what's the newest version of X" questions.
- Full CLI surface (devices, emulators, `run`, `layout`, `sdk`, `screen`): load the
  `android-cli` skill.

## Installation (if `android` is not on PATH)

```bash
# Linux
curl -fsSL https://dl.google.com/android/cli/latest/linux_x86_64/install.sh | bash
# Mac Arm
curl -fsSL https://dl.google.com/android/cli/latest/darwin_arm64/install.sh | bash
# Mac Intel
curl -fsSL https://dl.google.com/android/cli/latest/darwin_x86_64/install.sh | bash
```

`android update` updates the CLI in place once installed.

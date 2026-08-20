# Recipes: using `android docs` on this repository

Worked patterns verified on this machine (CLI `1.0.15985488`).

## 1. Check whether a bundled reference is stale

The Navigation 3 skill bundles `references/android/guide/navigation/navigation-3/index.md`.
Before relying on a bundled snapshot for an API that moves fast, compare against the live copy:

```bash
android docs fetch kb://android/guide/navigation/navigation-3/index > /tmp/nav3-live.md
diff /tmp/nav3-live.md .agents/skills/navigation-3/references/android/guide/navigation/navigation-3/index.md
```

The fetched output includes a small header (`Fetching docs from:`/`Title:`/`URL:`/`---`)
before the article body, so diff against the body or strip the first four lines. If the live
copy changed materially, update the bundled snapshot in the same commit that relies on the
new behavior.

## 2. Resolve an API question with no bundled skill

Task: is the old `registerForActivityResult` contract pattern still recommended, and what is
the current Compose equivalent?

```bash
android docs search "rememberlauncherforactivityresult activity result compose"
android docs fetch kb://android/develop/ui/compose/components/activity
```

Read the fetched article, then apply repo constraints (Compose-only in `app-native/`, no new
dependencies without need, Material 3 Expressive idioms per the m3-expressive skills).

## 3. Refresh / add a bundled snapshot

Pattern used by the existing skills (`navigation-3`, `adaptive`, `engage-sdk-integration`, ...):

1. `android docs search "<topic>"` to find candidate URLs.
2. `android docs fetch kb://android/<path>` and verify the article is the right one.
3. Save the article body under the owning skill's `references/` directory mirroring the kb
   path after the source prefix:
   - `kb://android/guide/navigation/navigation-3/index` →
     `references/android/guide/navigation/navigation-3/index.md`
   - `kb://JetBrains/kotlin-multiplatform-dev-docs/topics/<rest>` →
     `references/JetBrains/kotlin-multiplatform-dev-docs/topics/<rest>.md`
4. Keep the article verbatim — no paraphrasing; snapshots must stay diffable against live output.
5. Link the new file from the owning `SKILL.md` with a one-line annotation.
6. Keep the canonical copy in `.agents/skills/`; `.claude/skills/` mirrors via symlinks only.

## 4. Find the official skill for a topic without installing it

```bash
android docs search "navigation 3 skill"
android docs fetch kb://android/agents/skills/navigation/navigation-3/skill
```

This reads the official Google skill as an article. Fetching does not install anything; if the
team wants it installed as a managed Android CLI skill, use `android skills add <id>` and note
that repo policy still forbids loading Wear, CameraX, Play Billing, Engage, TV, or glasses
skills for this app.

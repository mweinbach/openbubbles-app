# Releases and self-updates

Devices update themselves from GitHub Releases — no Play Store. Each release
carries two assets:

- `openbubbles-<version>-<code>.apk` — the production-signed universal APK
  (arm64-v8a + x86_64)
- `update.json` — the feed the in-app updater reads
  (`app-native/.../update/GitHubUpdateSource.kt`)
## Publishing a release

**Automatic (version-bump only):** a push to `main` triggers the
`Self-update release` workflow only when it changed `versionName` or
`versionCode` in `app-native/build.gradle` — releases are a deliberate
version bump, not every green main. The workflow's `version` job compares
the push's before/after versions and skips the build when they match.
It builds the signed APK, verifies the signing certificate, computes the
SHA-256, writes `update.json`, and publishes the release.

**Manual (always publishes):** Actions → Self-update release →
Run workflow → optionally fill in `notes`. Use this to re-publish the
current version or to release without bumping gradle.

**Local:** `scripts/publish-update.sh --set --version-name X.Y.Z
--version-code N [--notes "..."]` does the same from a machine holding the
production keystore. Use it when CI is down or for pre-main test publishes.

## Version numbers

- `versionCode` must strictly increase; the updater (and PackageInstaller)
  refuse anything else. CI auto-increments past the latest published release
  when gradle's `versionCode` hasn't been bumped, stamping the runner-local
  build before compiling — gradle stays the floor for human-cut releases.
- `versionName` (the user-visible name) comes from `app-native/build.gradle`.
  When CI auto-increments the code, the release displays
  `"<versionName> (build <code>)"`.

## Release notes

Resolved in this order (same logic in the workflow and the local script):

1. the workflow-dispatch `notes` input, when run manually
2. the `## v<versionName>` section of `assets/changelog/changelog.md` —
   **this is the documented path**: add/refresh the section for the version
   being cut, using the existing `### Enhancements` / `### Fixes` subsections
3. the commit log since the previous release tag

Notes are truncated to 4000 characters in `update.json`.

## Signing

- Keystore: `android/release.jks`, alias `openbubbles` (JKS, 4096-bit RSA,
  10000-day validity). Gitignored; CI receives it via the `KEYSTORE_BASE64`
  secret with `KEYSTORE_PASSWORD`, `KEY_ALIAS`, `KEY_PASSWORD`.
- **Back this keystore up somewhere safe.** Losing it means every installed
  device must uninstall/reinstall (data loss) — self-updates and Play-style
  in-place upgrades only work with the same key.
- The workflow runs `apksigner verify --print-certs` before publishing; a
  wrongly-signed or unsigned build fails the job.

## Private submodule access in CI

The parent repo is public; its `GITHUB_TOKEN` cannot clone the five private
submodule repositories. Each has a read-only deploy key stored as a repo
secret (`SUBMODULE_KEY_RUSTPUSH`, `SUBMODULE_KEY_TELEPHONY`,
`SUBMODULE_KEY_APIS`, `SUBMODULE_KEY_ABSINTHE`, `SUBMODULE_KEY_SMSMMS`),
wired through SSH host aliases + `insteadOf` rewrites in both workflows. To
rotate: generate a new key, `gh api -X POST repos/<owner>/<repo>/keys ...`,
replace the secret. Deploy keys are per-repository (GitHub constraint).

## On-device acceptance

See the "Self-update acceptance" section of [tools/CUTOVER.md](../tools/CUTOVER.md).
Use the release completion handoff in [DEVELOPMENT.md](DEVELOPMENT.md#7-commit-push-and-release) to
keep the version, changelog, workflow, signed asset, feed hash, and remaining device checks in one
evidence unit.

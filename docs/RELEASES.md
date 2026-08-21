# Releases and self-updates

Devices update themselves without the Play Store. Update Ledger stores the
production-signed APK and exposes the Sparkle-compatible appcast consumed by
the Android client. Downloads always verify the exact byte count, SHA-256, and
Android signing identity.

The migration boundary is explicit:

- **3.4.7** is the final release published to GitHub Releases. It is the bridge
  build that installs the Ledger-only appcast client on existing devices.
- **3.5.0 and newer** are published only to Update Ledger. The app has no
  runtime GitHub update source or fallback.

## Publishing a release

**Automatic (version-bump only):** a push to `main` triggers the
`Self-update release` workflow only when it changed `versionName` or
`versionCode` in `app-native/build.gradle` — releases are a deliberate
version bump, not every green main. The workflow's `version` job compares
the push's before/after versions and skips the build when they match.
It builds the signed APK, verifies the signing certificate, computes the
SHA-256, writes `update.json`, uploads the APK to Update Ledger in bounded
multipart chunks, and publishes Ledger's JSON and appcast feeds. For versions
through 3.4.7 only, it also publishes the GitHub bridge release.
The Ledger release record is accepted only after its R2 object matches the
declared project, channel, build, filename, size, and checksum metadata.

**Manual (always publishes):** Actions → Self-update release →
Run workflow → optionally fill in `notes`. Use this to re-publish the
current version or to release without bumping gradle.

**Local:** `scripts/publish-update.sh --set --version-name X.Y.Z
--version-code N [--notes "..."]` does the same from a machine holding the
production keystore for a GitHub release. It is a legacy/emergency path after
the 3.4.7 cutoff; the automated Action is the canonical Ledger publisher.

## Update Ledger credentials

The repository secret `UPDATE_LEDGER_API_KEY` is the write-only project key.
It is exposed only to the final Ledger publication step. Public clients need
no secret to read `/api/v1/update/openbubbles`, the appcast, or artifacts.
Rotating the project key requires replacing this Actions secret before the
next release.

## Production diagnostics and instant update notices

Release builds use the Firebase project configured by
`app-native/google-services.json` for three narrowly scoped jobs:

- Google Analytics reports aggregate installs, active users, sessions, and
  coarse update-funnel events. Debug builds have collection disabled so local
  development does not inflate production usage.
- Crashlytics captures JVM crashes, ANRs, sanitized non-fatals, and native
  crashes. Release CI uploads the unstripped Rust symbols so native stacks can
  be symbolicated.
- Firebase Cloud Messaging subscribes clients to
  `update-ledger-openbubbles-stable`. A wake-up contains only the project,
  channel, version, and numeric build. It never carries an APK, download URL,
  account data, or message content.

Advertising-ID collection, ad personalization signals, and Firebase user IDs
are disabled. Application telemetry must go through `AppTelemetry`; do not add
message text, handles, contacts, credentials, push tokens, URLs, or raw native
errors to Analytics parameters, Crashlytics keys, or logs.

Publishing through CI or the Update Ledger dashboard automatically sends an
update wake-up after the durable release is accepted. If Firebase is
temporarily unavailable the release remains valid; use **Update Ledger →
OpenBubbles → Manage → Notify devices** to retry manually. Receiving a push
shows availability immediately and starts an expedited appcast check, but the
existing Update Ledger hash, byte-count, signing-identity, and version checks
remain authoritative before download or installation.

The first FCM-capable bridge is 3.4.7. Older devices continue using the legacy
GitHub check until they install 3.4.7; 3.5.0 and later are Ledger-only.

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

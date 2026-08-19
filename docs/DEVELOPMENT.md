# Evidence-driven development

Use this loop for native Kotlin/Rust work. Architecture documents define ownership; this guide
defines the order of work and the evidence required to finish it.

All commands below are self-contained and start from the repository root. Keep Gradle in a
subshell so later Rust paths do not accidentally resolve under `native/`.

## 1. Anchor the task

Before editing, capture the smallest stable task anchor:

- exact symptom or requested outcome;
- branch, root worktree, and recursive submodule state;
- device serial, Android version, installed version name/code, and time window when hardware is
  involved;
- exact screenshot text, error, log marker, failing check, or release tag;
- what is observed, inferred, and still unknown.

Preserve unrelated user changes. Do not fold generated files, lockfiles, or submodule movement into
the task unless the requested change owns them.

## 2. Find the owner and contract

| Boundary | Owner | Read / load |
|---|---|---|
| Compose and Android lifecycle | `app-native/` | [UI.md](UI.md), Material 3 skills |
| Shared ingest, paging, sync, backup | `core/` | [ARCHITECTURE.md](ARCHITECTURE.md) |
| ObjectBox entities and upgrade contract | `db/` | [PERSISTENCE.md](PERSISTENCE.md) |
| Kotlin-visible native API | `rust/` + committed UniFFI Kotlin | [RUST_KOTLIN.md](RUST_KOTLIN.md), `openbubbles-uniffi-contract-change` |
| Apple protocols | `rustpush/` | [RUST_KOTLIN.md](RUST_KOTLIN.md) |
| APK/ELF native packaging | Cargo + `app-native/cargo-android.gradle` | `openbubbles-native-library-compat` |

For a cross-layer bug, name the contract between owners before editing. Add the closest
deterministic test on every side whose behavior changes; do not move ownership merely to avoid a
boundary.

Before changing UI from a screenshot, confirm the currently mounted Compose screen and navigation
root. Do not implement against the retired Flutter shell or an older navigation path.

## 3. Diagnose the first missing transition

Do not start at the most visible symptom and guess backward. Prove the path in order.

For live messaging, keep these states separate:

```text
account / 2FA
  -> APNs transport + Android service
  -> IDS registration and handles
  -> Rust decrypt + durable journal pointer
  -> Kotlin callback + MessageIngestor
  -> ObjectBox row
  -> Compose projection / notification
```

“Connected to Apple push” proves neither IDS registration nor message ingestion. A decrypted Rust
event does not prove the service scope stayed alive long enough to persist it. Complete a Rust
pointer only after successful ingest so retry remains possible.

For sends, trace one row from local staging through transport-guid promotion, outgoing echo,
`SendConfirm`, persistent status, and UI. Test terminal events in more than one order; a
confirmation can beat row promotion.

For APK reports, distinguish source-built project code, vendored artifacts, and third-party AAR
libraries. ZIP alignment and ELF `PT_LOAD`/RELRO alignment are separate facts. Record provenance
and a behavioral oracle before replacing recovered or opaque native code.

For CloudSync, separate Messages in iCloud/CKKS history from live iMessage. Healthy APNs/IDS can
coexist with a CloudSync signature or canonicalization failure.

## 4. Implement the narrow slice

- Fix the owning layer and preserve the neighboring contracts.
- Keep service/account states explicit; do not present APNs transport as full registration.
- Keep compatibility fallbacks bounded to known representations and retain cryptographic
  verification.
- Generate UniFFI bindings from source; never edit generated Kotlin.
- Keep tests deterministic. Device evidence complements unit/oracle tests; it does not replace
  them.

## 5. Build an evidence ladder

Run focused tests while iterating, then the union of affected rows in [VERIFY.md](VERIFY.md).
Before completing an ordinary native change:

```bash
(cd native && ./gradlew :db:test :core:test :app-native:testDebugUnitTest \
  :db:checkModelParity :app-native:assembleDebug --console=plain)
```

Additional boundaries:

```bash
# Apple protocol changes, from the repository root
cargo test --manifest-path rustpush/Cargo.toml --lib --locked

# Kotlin-visible Rust contract changes
(cd rust && ./build-uniffi.sh)
(cd native && ./gradlew :app-native:checkUniffiBindings --console=plain)

# Compose chrome
(cd native && ./gradlew :app-native:validateDebugScreenshotTest --console=plain)
```

Within `.github/workflows/native.yml`, PR/push CI is test-only: it runs Rust tests plus JVM tests,
model parity, lint, UniFFI binding validation, and screenshots. That workflow packages APK/AAB
artifacts only on manual dispatch with the `package` input. The separate self-update release
workflow builds a signed APK for a version-bump push or manual release. A local `assembleDebug`
remains the pre-handoff packaging proof when the change affects the Android artifact.

Classify failures as introduced, pre-existing, or environment-only. Preserve exact output and do
not update unrelated goldens, lint baselines, or product behavior just to make a different task
green.

## 6. Capture hardware evidence

Host tests do not prove login, 2FA, Apple validation, live/background receive, SMS/MMS, upgrade,
or installation. When device validation is requested, record:

- device serial/model and OS;
- exact APK path, version name/code, and whether install preserved data;
- user scenario and start/end time;
- relevant UI state plus focused Android and Rust log markers;
- observed outcome and any unrun [CUTOVER](../tools/CUTOVER.md) items.

Inspect package/signature compatibility before an in-place install. Do not uninstall, clear data,
sign in, send external messages, or alter account/device state beyond the user's scope.
Keep captures filtered and time-bounded; stop immediately if device permission narrows. Do not dump
message bodies, credentials, key material, or full sync records. Redact excerpts included in the
handoff.

## 7. Commit, push, and release

Inspect the focused diff and secret-sensitive paths before staging. For nested changes, commit and
push the leaf repository first, then its parent submodule, then the root pointer. Verify every
referenced commit is reachable from its remote before pushing the parent.

An implementation is not a release. When a release is explicitly requested, follow
[RELEASES.md](RELEASES.md) and keep these in one evidence unit:

- version name/code and changelog;
- tag resolved to the intended pushed source commit;
- APK version name/code and signing certificate matched that source/release;
- `update.json` version/name, asset size, and SHA-256 matched the published APK;
- workflow status and any device update acceptance still pending.

## Completion handoff

Report:

1. changed behavior and owned contracts;
2. root and nested commits/push state;
3. exact automated commands and results;
4. artifact/package evidence;
5. device evidence, or an explicit not-run list;
6. CI/release status when applicable;
7. preserved unrelated changes or pre-existing failures.

## Cloud/CI fixture setup

Cursor Cloud and bare CI images need the gitignored FairPlay placeholders used by
`.github/workflows/native.yml` before compiling `rustpush/`:

```bash
mkdir -p rustpush/certs/fairplay
for name in \
  4056631661436364584235346952193 \
  4056631661436364584235346952194 \
  4056631661436364584235346952195 \
  4056631661436364584235346952196 \
  4056631661436364584235346952197 \
  4056631661436364584235346952198 \
  4056631661436364584235346952199 \
  4056631661436364584235346952200 \
  4056631661436364584235346952201 \
  4056631661436364584235346952208
do
  cp rustpush/certs/legacy-fairplay/fairplay.pem "rustpush/certs/fairplay/$name.pem"
  cp rustpush/certs/legacy-fairplay/fairplay.crt "rustpush/certs/fairplay/$name.crt"
done
```

Create gitignored `native/local.properties` with the actual SDK path. On the current Cursor image:

```bash
echo "sdk.dir=${ANDROID_HOME:-/home/ubuntu/android-sdk}" > native/local.properties
```

Do not commit either generated fixture set.

# OpenBubbles

OpenBubbles is a native Kotlin and Rust messaging client for Android and
desktop. It connects directly to Apple's messaging services; it is not the
legacy BlueBubbles client and does not require a BlueBubbles Mac server.

> The native rewrite is pre-release. Automated JVM, Rust, database-compatibility,
> and APK gates pass, but real-device Apple ID login, 2FA, background delivery,
> upgrade migration, and battery acceptance must pass before release.

## Repository layout

- `app-native/` — Android app, Compose UI, services, notifications, login, SMS/MMS.
- `core/` — shared message intake, repositories, contacts, attachments, backup,
  and CloudKit orchestration.
- `db/` — ObjectBox entities and the compatibility model.
- `desktopApp/` — Compose Desktop application, currently targeting Windows.
- `rust/` — UniFFI-facing native API used by the Kotlin applications.
- `rustpush/` — Apple protocol implementation, included as a Git submodule
  (with nested `apple-private-apis` and `open-absinthe` submodules of its own).
- `telephony_plus/` — Android telephony support, included as a Git submodule
  (provides the `:android-smsmms` Java MMS stack).
- `settings.gradle`, `build.gradle`, `gradle/` — root Gradle project for the Kotlin modules (JDK 21+).
- `legacy/flutter/` — archived documentation for the retired Flutter/GetX client.

Clone with submodules included:

```bash
git clone --recurse-submodules https://github.com/mweinbach/openbubbles-app.git
```

If you cloned without the flag, recover with `git submodule update --init --recursive`.
Most submodule remotes are private GitHub repositories — your SSH key or token must
be authorized on them. Full submodule map and workflow:
[docs/rust-backend/foundations/submodules.md](docs/rust-backend/foundations/submodules.md).

See [AGENTS.md](AGENTS.md) for agent/contributor orientation,
[docs/ARCHITECTURE.md](docs/ARCHITECTURE.md) for runtime flows, and
[tools/CUTOVER.md](tools/CUTOVER.md) for remaining release gates.

## Build and test

Required tooling:

- JDK 21 or newer (Android Studio's bundled JBR is recommended)
- Android SDK 36 and NDK `28.2.13676358`
- stable Rust with `aarch64-linux-android` and `x86_64-linux-android`
- `protoc`

### Android Studio

Open the repository root as the project directory. Android Studio's bundled JBR is supported.
After Gradle sync, select the `app-native` configuration and run it on an API 26+
device or emulator. The launch activity is `app.openbubbles.nativeapp.NativeMainActivity`.

The Gradle project imports `app-native/`, `core/`, `db/`, `desktopApp/`, and the required
`telephony_plus/android-smsmms/library` module from their repository-root locations.

Clone with `--recurse-submodules` (see above), provide `local.properties` with
`sdk.dir`, then run:

```bash
./gradlew :db:test :core:test :app-native:testDebugUnitTest \
  :db:checkModelParity :app-native:lintDebug \
  :app-native:assembleDebug :app-native:bundleRelease --console=plain
```

The APK is written to
`build-native/app-native/outputs/apk/debug/app-native-debug.apk`.
The release-variant AAB is written to
`build-native/app-native/outputs/bundle/release/app-native-release.aab`. Without
a usable `android/key.properties` and referenced keystore, it is debug-signed
for compilation/testing only and must not be submitted to a store.

Run the clean-checkout Rust gate separately:

```bash
cargo test --manifest-path rustpush/Cargo.toml --lib --locked
```

The APNs proxy and replay tests are manual integration tools. They remain
ignored unless their private/local fixtures and network environment are supplied.

## Current functionality

The Android client includes native provisioning, Apple ID login and 2FA state
handling, iMessage send/receive, attachment transfer, SMS/MMS, notifications,
CloudKit history sync, contacts, backup/restore, Find My, and FaceTime surfaces.

The native UI also includes tapbacks, replies, edit/unsend, group membership,
group names and photos, pin/mute/archive/delete controls, and Android open/share
handling for downloaded media and generic files. Important remaining release
work is direct real-device acceptance of every login/2FA and carrier path,
timed unmute, broader journey instrumentation, desktop parity, signing, and
store-ready packaging.

The native rewrite targets the self-hosted flow: scan or paste an `OABS` Mac
hardware payload once, then generate Apple validation data locally on the
Android device. It does not depend on the OpenBubbles hosted hardware relay.
The APK contains no project-owned precompiled compatibility library. Gradle
builds `librust_lib_bluebubbles.so` directly from `rust/` with Cargo and the
pinned Android NDK for arm64 and x86_64. OpenAbsinthe's constructor, key
establishment, and signing path execute recovered source on every platform;
the signing circuit is a generated architecture-neutral program interpreted
by Rust and never loads the historical engine at runtime. See
[`rustpush/open-absinthe/RECOVERY.md`](rustpush/open-absinthe/RECOVERY.md) for
the recovery evidence and the boundary between offline differential checks and
device acceptance.

## Releases and self-updates

The app updates itself from GitHub Releases — no Play Store. Every push to
`main` with code changes makes the `Self-update release` GitHub Action build
the production-signed APK and publish it with an `update.json` feed; installed
devices check daily and on app open, then download, verify (SHA-256 +
signature), and install with one tap.

To cut a named version:

1. Bump `versionName`/`versionCode` in `app-native/build.gradle` (CI
   auto-increments the code past the last release if you don't).
2. Add a `## v<version>` section to `assets/changelog/changelog.md` — it
   becomes the release notes shown on GitHub and in the app.
3. Push to `main`. The next CI release carries your version.

Full mechanics — signing keys and backup, the versionCode rules, notes
resolution, manual dispatch, and private-submodule CI access — are in
[docs/RELEASES.md](docs/RELEASES.md).

## Data compatibility

Android retains the shipping application ID, `com.openbubbles.messaging`, and
uses the legacy ObjectBox data location for in-place upgrades. The
`:db:checkModelParity` task guards the ObjectBox model contract. Any model change
must keep that gate green or provide an explicit migration.

## Security

Never commit Apple credentials, device provisioning state, signing keys, APNs
proxy certificates, or captured message traffic. Treat `hw_info.plist`,
`gsa.plist`, `id.plist`, keystores, and replay fixtures as secrets.

## Contributing

Read [CONTRIBUTING.md](CONTRIBUTING.md). Every change should include focused
tests, the relevant full gate, and a descriptive commit.

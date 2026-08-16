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
- `rustpush/` — Apple protocol implementation, included as a Git submodule.
- `native/` — Gradle root for the Kotlin modules (JDK 21).

See [AGENTS.md](AGENTS.md) for agent/contributor orientation,
[docs/ARCHITECTURE.md](docs/ARCHITECTURE.md) for runtime flows, and
[tools/CUTOVER.md](tools/CUTOVER.md) for remaining release gates.

## Build and test

Required tooling:

- JDK 21 (`.java-version` is checked in; other JDKs are rejected explicitly)
- Android SDK 36 and NDK `28.2.13676358`
- stable Rust with `aarch64-linux-android` and `x86_64-linux-android`
- `protoc`
- Dart, used only by cargokit's Rust build helper

Initialize submodules, provide `native/local.properties` with `sdk.dir`, then run:

```bash
cd native
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
The arm64 APK packages a compatibility library pinned to a recorded SHA-256.
The Rust OpenAbsinthe layer checks its expected context layout and exported
anchor before calling the version-specific native routines. The debug device
gate completes Apple's account-free validation exchange and requires a
517-byte envelope.

For local ABI investigation, a debug build can override the pinned library:

```bash
cd native
OPENBUBBLES_OFFICIAL_RUST_LIB=/path/to/librust_lib_bluebubbles.so \
  ./gradlew :app-native:assembleDebug
```

The override is accepted only when it matches the compatibility contract used
by the Rust validation backend. A debug-only broadcast performs the complete
Apple certificate, key-establishment, and signing round trip without account
credentials.

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

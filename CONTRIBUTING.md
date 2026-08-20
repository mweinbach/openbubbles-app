# Contributing to OpenBubbles

OpenBubbles is in a native Kotlin/Rust cutover. New work must target the native
modules; do not reintroduce Flutter or Dart application code. Agents should
start from [AGENTS.md](AGENTS.md). The executable change loop and evidence handoff are in
[docs/DEVELOPMENT.md](docs/DEVELOPMENT.md).

## Setup

1. Clone with submodules (`git clone --recurse-submodules`) or run
   `git submodule update --init --recursive`. Most submodule remotes are private
   repositories; see
   [docs/rust-backend/foundations/submodules.md](docs/rust-backend/foundations/submodules.md)
   for the full tree, access requirements, and the pointer-bump workflow.
2. Use Android Studio's bundled JBR or another JDK 21+ installation, plus the
   Android SDK/NDK versions listed in `README.md`, stable Rust, the Android Rust
   targets, and `protoc`.
3. Create `native/local.properties` containing your Android SDK path.
4. Keep secrets and local device state out of Git.

The Gradle settings reject runtimes older than JDK 21 with an actionable error.
The Android build compiles `rust/` directly with Cargo and the pinned NDK through
`app-native/cargo-android.gradle`; it must not require Dart, Flutter, or Cargokit.
Retained Flutter/FRB files are compatibility or reference material, not the native build path.

## Code ownership by module

- Android UI and platform behavior: `app-native/`
- Shared messaging and sync behavior: `core/`
- ObjectBox persistence: `db/`
- Desktop UI and lifecycle: `desktopApp/`
- Kotlin-facing native exports: `rust/`
- Apple protocol implementation: `rustpush/`

Prefer shared behavior in `core/`; keep Android framework types in
`app-native/` and desktop framework types in `desktopApp/`.

## Required verification

Run focused tests while iterating. Before completing a native change, run:

```bash
(cd native && ./gradlew :db:test :core:test :app-native:testDebugUnitTest \
  :db:checkModelParity :app-native:assembleDebug --console=plain)
```

For changes touching `rustpush/`, also run:

```bash
# From the repository root
cargo test --manifest-path rustpush/Cargo.toml --lib --locked
```

Manual APNs proxy/replay tools are ignored by default because they require
private fixtures. Do not weaken normal unit gates to accommodate them.

Changes to Android lifecycle, notifications, permissions, receivers, workers,
or services should add JVM tests for pure policy and an instrumentation or
journey test when behavior depends on the framework. Device-dependent work must
remain explicitly unverified until it is exercised on hardware.

## Persistence rules

`db/objectbox-model.json` is a compatibility boundary. Entity changes must keep
`:db:checkModelParity` green or include a reviewed migration. Never regenerate
UIDs casually, and test upgrades against a real backup before release.

## Generated and local files

Do not commit Gradle caches, build reports, SDK paths, Flutter ephemeral plugin
links, native binaries, signing material, provisioning files, or replay data.
Update `.gitignore` when a tool creates a new recurring artifact.

UniFFI bindings and ObjectBox model files are exceptions only where the build
explicitly treats them as source or compatibility contracts.

## Submodules

Commit submodule changes inside the submodule first, on a named branch. Ensure
the commit is available from a remote before committing the parent pointer.
Then commit the parent repository update separately so the dependency change is
easy to audit.

## Commits and reviews

- Keep commits focused and descriptive.
- Include tests with the behavior they protect.
- Preserve unrelated user changes in a dirty worktree.
- State which gates passed and which device/release checks remain.
- Never claim a push, deployment, device flow, or migration succeeded without
  the corresponding evidence.

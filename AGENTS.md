# OpenBubbles

Direct Apple messaging client for Android and desktop. Not BlueBubbles-over-a-Mac-server.
The shipping code is Kotlin + Rust. Do not add Flutter or Dart application code.

## Why

Same Android application id (`com.openbubbles.messaging`) and ObjectBox path as the retired
Flutter client so in-place upgrades open the existing store. `core/` is shared product logic.
Platform UI and lifecycle stay in `app-native/` and `desktopApp/`. Apple protocols live in
`rustpush/`; Kotlin talks to them through the UniFFI facade in `rust/`.

## How

JDK 21 only. Gradle root is `native/` (not the repo root). Submodules required.

### Native build boundary

- Android compiles `rust/` directly with Cargo and NDK `28.2.13676358` through
  `app-native/cargo-android.gradle`. Dart, Flutter, and Cargokit are not build prerequisites.
- Do not route Gradle or CI through `rust_builder/cargokit/`, `run_build_tool.sh`, or any Dart tool.
  That directory and the retired Flutter client are reference/migration material only.
- `rust/src/frb_generated*.rs` and existing Flutter Rust Bridge exports still compile as legacy Rust
  surface. They are not the Kotlin API; Kotlin uses committed UniFFI bindings from `:core`.

All paths and commands in this file start at the repository root. Keep directory changes in
subshells so Gradle does not leave later Rust paths resolving under `native/`.

```bash
(cd native && ./gradlew :db:test :core:test :app-native:testDebugUnitTest \
  :db:checkModelParity :app-native:assembleDebug --console=plain)
```

UI chrome: also `(cd native && ./gradlew :app-native:validateDebugScreenshotTest --console=plain)`.
`rustpush/` changes: `cargo test --manifest-path rustpush/Cargo.toml --lib --locked` from the root.
UniFFI surface changes: `(cd rust && ./build-uniffi.sh)`, then commit the Kotlin in `core/src/main/kotlin/uniffi/`.
Device login, 2FA, battery, and upgrade: [tools/CUTOVER.md](tools/CUTOVER.md). Do not claim those passed without hardware evidence.

## Development loop

Follow [docs/DEVELOPMENT.md](docs/DEVELOPMENT.md): anchor the exact symptom and worktree, identify
the owning contract, prove the first failing boundary, make the narrow slice, run the union of
affected gates, capture device evidence separately, then commit and push leaf submodules before
parent pointers. A visible UI status, green host test, successful package, and hardware protocol
oracle are different evidence tiers; state exactly which one passed.

## Hard constraints

- Android types stay in `app-native/`. Desktop types stay in `desktopApp/`. Do not put `android.*` in `core/` or `db/`.
- Do not resurrect a `:shared` KMP module (removed for AGP 9).
- `db/objectbox-model.json` is a compatibility boundary. Keep `:db:checkModelParity` green or land a reviewed migration. Never regenerate ObjectBox UIDs.
- Do not move the Android store off `{dataDir}/app_flutter/objectbox`.
- Kotlin ↔ Rust is UniFFI. Do not add Flutter Rust Bridge APIs. Do not hand-edit generated UniFFI Kotlin.
- Keep the Android Rust build Dart-free: direct Cargo + pinned NDK only.
- SIM (`isRpSms`) attachments go through Android MMS, never MMCS.
- Default path is self-hosted OABS + on-device validation. Do not require a hosted hardware relay.
- After completing and verifying requested changes, commit them and push the current branch automatically unless the user explicitly asks not to. Only include safely separable requested work; never sweep unrelated generated files or unrelated/unverified changes into the commit. Report the blocker if clean separation is impossible.
- Commit rustpush changes inside the submodule first, then the parent pointer separately.
- Never commit credentials, `hw_info.plist` / `gsa.plist` / `id.plist`, keystores, `android/key.properties`, APNs proxy certs, or replay traffic.

## Cursor Cloud specific instructions

Before compiling `rustpush/` on a bare cloud image, create the gitignored FairPlay placeholders
and `native/local.properties` exactly as documented in
[docs/DEVELOPMENT.md](docs/DEVELOPMENT.md#cloudci-fixture-setup). Do not commit either fixture set.

## Read when relevant

| Task | Doc |
|---|---|
| Evidence-first change loop, device evidence, handoff | [docs/DEVELOPMENT.md](docs/DEVELOPMENT.md) |
| Modules, login, receive/send, live vs poll | [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md) |
| Compose design, nav, screens, how to add UI | [docs/UI.md](docs/UI.md) |
| UniFFI, rust vs rustpush, keystore, queue | [docs/RUST_KOTLIN.md](docs/RUST_KOTLIN.md) |
| Rust backend deep dive: API surface, lifecycle, state files, rustpush internals, change recipes | [docs/RUST_BACKEND.md](docs/RUST_BACKEND.md) |
| Personal iCloud Photos investigation and implementation | [docs/PHOTOS_SYNC.md](docs/PHOTOS_SYNC.md) |
| ObjectBox entities, parity, store path | [docs/PERSISTENCE.md](docs/PERSISTENCE.md) |
| Which tests prove what | [docs/VERIFY.md](docs/VERIFY.md) |
| Human contrib, submodules | [CONTRIBUTING.md](CONTRIBUTING.md) |
| Setup, SDK/NDK pins | [README.md](README.md) |
| Release / device checklist | [tools/CUTOVER.md](tools/CUTOVER.md) |
| Cutting versions, signing, self-update feed | [docs/RELEASES.md](docs/RELEASES.md) |

## Skills (load only for that task)

The OpenBubbles skills in `.agents/skills` are canonical and mirrored into `.claude/skills` for
Claude project discovery. Update the canonical skill only; keep both discovery surfaces pointing
to the same files.

UI already uses Material 3 Expressive + Navigation3. Do not invent a bottom nav or extra top-bar
destinations for Find My / Settings (those live in the chat-list profile menu).

| When | Load |
|---|---|
| Device says connected but login/receive state disagrees | [openbubbles-live-flow-triage](.agents/skills/openbubbles-live-flow-triage/SKILL.md) |
| Outgoing message is stuck, duplicated, or has wrong status | [openbubbles-send-lifecycle](.agents/skills/openbubbles-send-lifecycle/SKILL.md) |
| Kotlin-visible Rust API/event/type changes | [openbubbles-uniffi-contract-change](.agents/skills/openbubbles-uniffi-contract-change/SKILL.md) |
| Native `.so`, 16 KiB, ELF/RELRO, or provenance issue | [openbubbles-native-library-compat](.agents/skills/openbubbles-native-library-compat/SKILL.md) |
| Any Compose / visual change | [.agents/skills/m3-expressive/SKILL.md](.agents/skills/m3-expressive/SKILL.md) then the specialist it routes to |
| Theme, color, shapes, type | [m3-expressive-theming](.agents/skills/m3-expressive-theming/SKILL.md) |
| Springs, shared elements, reduce-motion | [m3-expressive-motion](.agents/skills/m3-expressive-motion/SKILL.md) |
| Buttons, lists, toolbars, indicators | [m3-expressive-components](.agents/skills/m3-expressive-components/SKILL.md) |
| List-detail, panes, rails, Nav3 | [m3-expressive-navigation](.agents/skills/m3-expressive-navigation/SKILL.md) and [navigation-3](.agents/skills/navigation-3/SKILL.md) |
| Foldables / window size classes | [adaptive](.agents/skills/adaptive/SKILL.md) |
| Critique a screen | [m3-expressive-review](.agents/skills/m3-expressive-review/SKILL.md) |
| Receivers, exported components, SMS role | [android-intent-security](.agents/skills/android-intent-security/SKILL.md) |
| Adding test kinds | [testing-setup](.agents/skills/testing-setup/SKILL.md) — keep our existing stack; do not install Hilt |

Do not load Wear, CameraX, Play Billing, Engage, TV, or glasses skills for this app.

## Historical (do not implement from)

`docs/DECISIONS.md`, `docs/COMMON_TASKS.md`, `docs/MESSAGE_*_FLOW.md`, and `docs/models.md`
describe the retired Dart client.

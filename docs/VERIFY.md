# Verify

Automated gates prove JVM policy, ingest, and compile. They do not prove Apple login, 2FA,
background delivery, upgrade, or battery.

## Iterate

Run each command independently from the repository root:

| Change | Command |
|---|---|
| Persistence / entities | `(cd native && ./gradlew :db:test :db:checkModelParity)` |
| Ingest, repos, CloudKit, backup, contacts | `(cd native && ./gradlew :core:test)` |
| ViewModels, send routing, SMS builders, service policy | `(cd native && ./gradlew :app-native:testDebugUnitTest)` |
| List / chat / onboarding chrome | `(cd native && ./gradlew :app-native:validateDebugScreenshotTest)` |
| UniFFI surface | `(cd rust && ./build-uniffi.sh)` then `(cd native && ./gradlew :app-native:checkUniffiBindings)` |
| `rustpush/` | `cargo test --manifest-path rustpush/Cargo.toml --lib --locked` |

Re-record goldens with
`(cd native && ./gradlew :app-native:updateDebugScreenshotTest --console=plain)`. Preview clocks
stay **fixed**.

Do not invent `cargo test --manifest-path rust/Cargo.toml` as a gate — `rust/` has no unit tests.

## Before done

```bash
(cd native && ./gradlew :db:test :core:test :app-native:testDebugUnitTest \
  :db:checkModelParity :app-native:assembleDebug --console=plain)
```

PR/push CI also runs `:app-native:lintDebug`, `:app-native:checkUniffiBindings`, and
`:app-native:validateDebugScreenshotTest`; it does not package an APK or AAB. A manual native
workflow dispatch with `package` enabled adds `:app-native:assembleDebug` and
`:app-native:bundleRelease`. The local default command above still assembles debug as the ordinary
artifact proof.

`rustpush` APNs proxy/replay tests stay `#[ignore]`. Do not weaken the lib suite to need private
fixtures.

## What the tests actually cover

- `:db` — entity relations and unique guids. **Not** opening a production Flutter `data.mdb`.
- `:core` — temp ObjectBox + fabricated UniFFI values / fake ports (ingest, paging, attachments,
  contacts, CloudKit, backup).
- `:app-native` — ViewModels with fakes, send-routing helpers, SMS push shape, notification
  preview text, poll-vs-sticky **pure functions**. Not live APNs or a running service.
- Screenshots — list, chat, onboarding chrome. Not settings, login, Find My, or effects.

## Device and release

Unchecked items in [CUTOVER.md](../tools/CUTOVER.md) need hardware evidence: OABS provision,
Apple ID + 2FA, SMS/MMS, attachments, notifications, reboot, long-background live, battery-saver,
24-hour drain, Flutter-era upgrade, sign-out/in, signed artifacts.

State in the PR which Gradle/cargo gates ran and which device checks remain. Never claim a
push, login, upgrade, or store publish succeeded without that evidence.

Use the hardware-evidence record and completion handoff in [DEVELOPMENT.md](DEVELOPMENT.md) so the
artifact, device, scenario, time window, and exact log/UI proof stay together.

Adding new test *kinds*: load
[.agents/skills/testing-setup/SKILL.md](../.agents/skills/testing-setup/SKILL.md) but keep the
current stack (JUnit, Compose screenshot plugin, fakes in `FakeRepository`). Do not install Hilt
or Jacoco as a drive-by.

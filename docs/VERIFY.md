# Verify

Automated gates prove JVM policy, ingest, and compile. They do not prove Apple login, 2FA,
background delivery, upgrade, or battery.

## Iterate

From `native/`:

| Change | Command |
|---|---|
| Persistence / entities | `./gradlew :db:test :db:checkModelParity` |
| Ingest, repos, CloudKit, backup, contacts | `./gradlew :core:test` |
| ViewModels, send routing, SMS builders, service policy | `./gradlew :app-native:testDebugUnitTest` |
| List / chat / onboarding chrome | `./gradlew :app-native:validateDebugScreenshotTest` |
| UniFFI surface | `cd rust && ./build-uniffi.sh` then `:app-native:checkUniffiBindings` |
| `rustpush/` | `cargo test --manifest-path rustpush/Cargo.toml --lib --locked` |

Re-record goldens with `:app-native:updateDebugScreenshotTest`. Preview clocks stay **fixed**.

Do not invent `cargo test --manifest-path rust/Cargo.toml` as a gate — `rust/` has no unit tests.

## Before done

```bash
cd native
./gradlew :db:test :core:test :app-native:testDebugUnitTest \
  :db:checkModelParity :app-native:assembleDebug --console=plain
```

CI also runs `:app-native:lintDebug` and `:app-native:bundleRelease`. Screenshot validation is
not in that default command — run it when you change those surfaces.

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

Adding new test *kinds*: load
[.agents/skills/testing-setup/SKILL.md](../.agents/skills/testing-setup/SKILL.md) but keep the
current stack (JUnit, Compose screenshot plugin, fakes in `FakeRepository`). Do not install Hilt
or Jacoco as a drive-by.

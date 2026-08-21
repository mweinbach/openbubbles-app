# Verify

Automated gates prove JVM policy, ingest, and compile. They do not prove Apple login, 2FA,
background delivery, upgrade, or battery.

## Iterate

Run each command independently from the repository root:

| Change | Command |
|---|---|
| Persistence / entities | `./gradlew :db:test :db:checkModelParity` |
| Ingest, repos, CloudKit, backup, contacts | `./gradlew :core:test` |
| ViewModels, send routing, SMS builders, service policy | `./gradlew :app-native:testDebugUnitTest` |
| Deliberate visual review (optional) | `./gradlew :app-native:validateDebugScreenshotTest` |
| UniFFI surface | `(cd rust && ./build-uniffi.sh)` then `./gradlew :app-native:checkUniffiBindings` |
| `rustpush/` | `cargo test --manifest-path rustpush/Cargo.toml --lib --locked` |
| Baseline Profile / Macrobenchmark sources | `./gradlew :benchmark:compileBenchmarkReleaseKotlin :benchmark:compileNonMinifiedReleaseKotlin` |

Re-record goldens with
`./gradlew :app-native:updateDebugScreenshotTest --console=plain`. Preview clocks
stay **fixed**.

For a visual change, first run the pure tests that select layout/direction/semantics, then update only
the owned fixtures, inspect the rendered images, and run screenshot validation. Use the applicable
matrix in [UI.md](UI.md#visual-change-evidence): both message directions, same-side cases, direct vs
group behavior, text vs media, light/dark, LTR/RTL, adaptive panes, large text, accessibility, and
reduced motion are separate branches even when one screenshot looks correct.

Do not invent `cargo test --manifest-path rust/Cargo.toml` as a gate — `rust/` has no unit tests.

## Before done

```bash
./gradlew :db:test :core:test :app-native:testDebugUnitTest \
  :db:checkModelParity :app-native:assembleDebug --console=plain
```

PR/push CI also runs `:app-native:lintDebug` and `:app-native:checkUniffiBindings`; it does not
run the screenshot renderer or package an APK/AAB. A manual native
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
- Vault catalog — `:core` covers the catalog contract, site-key canonicalization, the credential
  provider lookup reducer, and the field crypto envelope; `:app-native` pins the SQLite schema,
  its migration guard, and the cleanup table list. None of it proves system provider selection,
  Chrome delegation, biometric prompts, or a real AndroidKeyStore key, which are device evidence.
- Optional screenshots — visual review of list, chat, and onboarding chrome. They are not a
  routine correctness gate and do not cover settings, login, Find My, or effects. They also do not
  prove touch/gesture arbitration, IME behavior, TalkBack order, platform authentication, predictive
  back, fold posture, or physical-device rendering.

## Device and release

Baseline Profiles and Macrobenchmarks are physical-device evidence, not host tests. AGP's connected
test task installs and then uninstalls the target application; that removes its private data. Run
these Gradle tasks only on a disposable benchmark device/user whose app state can be recreated, never
on a primary signed-in installation. Verify that the test APK's certificate matches before starting.

Run a dry instrumentation pass first, then generate the release profile:

```bash
ANDROID_SERIAL=<serial> ./gradlew :benchmark:connectedNonMinifiedReleaseAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.androidx.benchmark.enabledRules=BaselineProfile \
  -Pandroid.testInstrumentationRunnerArguments.androidx.benchmark.dryRunMode.enable=true \
  -Pandroid.testInstrumentationRunnerArguments.class=app.openbubbles.benchmark.BaselineProfileGenerator \
  --console=plain --no-configuration-cache

ANDROID_SERIAL=<serial> ./gradlew :app-native:generateReleaseBaselineProfile \
  -Pandroid.testInstrumentationRunnerArguments.androidx.benchmark.enabledRules=BaselineProfile \
  -Pandroid.testInstrumentationRunnerArguments.class=app.openbubbles.benchmark.BaselineProfileGenerator \
  --console=plain --no-configuration-cache
```

The committed profile belongs under `app-native/src/release/generated/baselineProfiles`. Rebuild
`:app-native:assembleRelease` and confirm the APK contains `assets/dexopt/baseline.prof` and
`assets/dexopt/baseline.profm`. The connected Macrobenchmark task intentionally fails before
installation when the app-owned `baseline-prof.txt` is absent; dependency profiles are not accepted
as proof of OpenBubbles startup/chat/Photos coverage.

Measure startup, chat-list scrolling, and Photos scrolling only after the profile is packaged:

```bash
ANDROID_SERIAL=<serial> ./gradlew :benchmark:connectedBenchmarkReleaseAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.androidx.benchmark.enabledRules=Macrobenchmark \
  -Pandroid.testInstrumentationRunnerArguments.class=app.openbubbles.benchmark.OpenBubblesMacrobenchmark \
  --console=plain --no-configuration-cache
```

Record model/OS, fold posture and orientation, refresh rate, battery/thermal state, installed
version/signature, and the generated JSON/Perfetto report paths. A compiled benchmark module or dry
run proves wiring only; it is not performance evidence.

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

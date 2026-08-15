# Testing and Verifying M3 Expressive UI

What hangs, what to screenshot, what only a human can check. For the case where the review found
nothing in the code but the screen still regresses on the next alpha bump.

Source tags: `[OFFICIAL jetpacker]` = `android/ai-samples`, path `jetpacker/`.
`[OFFICIAL androidify]` = `android/androidify`. `[CANONICAL]` = correct form assembled here, **not**
copied from a Google sample — recommendation, not evidence.

**Read this first.** *No Expressive-specific screenshot testing exists in any official Google
sample.* androidify — the most Expressive-heavy Google app — has **no** screenshot-testing
dependency at all. nowinandroid, JetNews, JetLagged and Jetcaster carry Roborazzi but never point it
at Expressive components. jetpacker has a complete modern screenshot setup and **zero** Expressive
components (plain `MaterialTheme`, no `MotionScheme`, no `ButtonGroup`/`FloatingToolbar`/
`LoadingIndicator`/wavy progress). So §4 is generic Compose screenshot testing, verbatim from
Google, applied to an app that happens not to use Expressive. Applying it *to* Expressive is this
document's recommendation, not Google's demonstrated practice. If asked for the official way to
golden-test a wavy indicator: there isn't one.

---

## 1. The core problem — expressive UI hangs naive Compose tests

`waitForIdle()` — which every `onNodeWithText`, `performClick` and `assert*` calls implicitly —
blocks until the composition **and the animation clock** are idle. Expressive UI is springs and
continuous animation, which from the clock's view are never done:

| Thing | Why it never idles |
| --- | --- |
| `LoadingIndicator` / `ContainedLoadingIndicator` | infinite shape-morph cycle |
| `LinearWavyProgressIndicator` / `CircularWavyProgressIndicator` | wave animates forever, even at determinate progress |
| `Morph` loaders (`rememberInfiniteTransition` + `Morph`) | infinite by construction |
| `MotionScheme` spatial springs | low damping settles asymptotically; can exceed the idling window |
| `shapeByInteraction` / pressed-state morph | spring per interaction |
| `FloatingActionButtonMenu`, `FloatingToolbar` expand/collapse | spring, often chained |
| Any `rememberInfiniteTransition` glow/shimmer | infinite by construction |

Symptom: the test does not fail, it **hangs**, then dies on the harness timeout with no useful
message. On CI this looks like flake. It is not flake — it is deterministic and will recur on every
run once the screen renders one of the above. This is a testing problem, not a correctness problem;
do not "fix" it by deleting the indicator.

---

## 2. The fix — `LocalInspectionMode` and the v2 test rule

`[OFFICIAL androidify]` `feature/home/src/androidTest/java/com/android/developers/androidify/home/HomeScreenTest.kt:18-70`

Two techniques, both load-bearing:

1. `androidx.compose.ui.test.junit4.v2.createAndroidComposeRule` — the **v2** test rule.
2. `CompositionLocalProvider(LocalInspectionMode provides true)` wrapped around the content under
   test, to suppress infinite/expressive animations (which otherwise never idle and hang
   `waitForIdle`).

```kotlin
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule   // <- the v2 path
import com.android.developers.androidify.theme.SharedElementContextPreview
// + ComponentActivity, assertIsDisplayed, onNodeWithText, performClick, IntOffset,
//   AndroidJUnit4, assertTrue, Rule, Test, RunWith

@RunWith(AndroidJUnit4::class)
class HomeScreenTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun clickingLetsGo_invokesCallback() {
        var wasClicked = false
        val letsGoButtonLabel = composeTestRule.activity.getString(R.string.home_button_label)

        composeTestRule.setContent {
            // Directly render HomeScreenContents, passing a lambda to track the click

            SharedElementContextPreview {
                CompositionLocalProvider(LocalInspectionMode provides true) {
                    HomeScreenContents(
                        layoutType = HomeScreenLayoutType.Compact, // Provide a default or mock value
                        onClickLetsGo = { offset: IntOffset -> // Match the lambda signature
                            wasClicked = true
                        },
                        onAboutClicked = {}, // Provide a default or mock value,
                        videoLink = "",
                        dancingBotLink = "",
                    )
                }
            }
        }

        // Find the "Lets Go" button and click it
        composeTestRule.onNodeWithText(letsGoButtonLabel).performClick()

        // Assert that the lambda was invoked
        assertTrue("The onClickLetsGo lambda should have been called", wasClicked)
    }
```

Third technique in that file: `SharedElementContextPreview { }` — a test/preview harness composable
supplying the `SharedTransitionScope`/`AnimatedVisibilityScope` locals so screens using
`sharedBoundsReveal` can be tested in isolation. Without an equivalent, `setContent` throws on a
missing local. Sibling files use the same shape (bodies unverified): `ResultsScreenTest.kt`,
`CreationScreenTest.kt`, `CameraScreenTest.kt`.

**`LocalInspectionMode provides true` — needed whenever the subtree contains anything from the §1
table.** This is what stops the hang: components check it and skip infinite animation, the same
mechanism the IDE preview renderer uses.

- Use for assertion tests: does the node exist, is the callback invoked, is the label right.
- Do **not** use when the animation *is* the thing under test — inspection mode renders the static
  frame, so a mid-morph assertion is meaningless.
- Blunt instrument: it also flips preview-guarded branches in *your* code
  (`if (LocalInspectionMode.current) placeholder else realThing`), so the test then exercises the
  placeholder path. Check before applying.

**The v2 rule — needed for new tests, and wherever `LaunchedEffect` ordering is fragile.** v2 became
the default in Compose 1.11 (April 2026), replacing v1, and runs coroutines on
`StandardTestDispatcher` instead of `UnconfinedTestDispatcher`, so launched coroutines **queue**
rather than running immediately. Migration consequence: a v1 test that passed because an effect ran
eagerly may now need an explicit `waitUntil { }` — that is v2 behaviour, not a regression. Use
`createAndroidComposeRule<ComponentActivity>()` rather than `createComposeRule()` to get
`composeTestRule.activity.getString(...)`, so assertions use real string resources.

Both together is the default for instrumented tests of Expressive screens.

---

## 3. `mainClock.autoAdvance = false` — for mid-animation assertions

When you must assert *during* an animation, inspection mode is wrong because it removes the
animation. Take the clock instead.

`[OFFICIAL jetpacker]` `android/feature/create_trip/src/test/kotlin/.../CreateTripScreenTest.kt`

```kotlin
@RunWith(AndroidJUnit4::class)
@Config(application = android.app.Application::class, qualifiers = "+w411dp-h891dp-mdpi", sdk = [33])
class CreateTripScreenTest {

  @get:Rule val composeTestRule = createComposeRule()

  @Test
  fun testCreateTripScreen_showsImage() {
    composeTestRule.mainClock.autoAdvance = false
    ...
    composeTestRule.setContent {
      val uiState by fakeViewModel.uiState.collectAsState()
      JetPackerTheme {
        CreateTripPanelContent(uiState = uiState, viewModel = fakeViewModel, onCollapse = {})
      }
    }
  }
}
```

Every part of that annotation line matters for expressive UI: `qualifiers = "+w411dp-h891dp-mdpi"`
pins window size so breakpoint-driven layouts are deterministic; `sdk = [33]` pins the SDK (dynamic
color behaves differently below 31); `application = android.app.Application::class` bypasses Hilt
with no test component; `autoAdvance = false` stops infinite animation from hanging the test.
Requires `testOptions { unitTests { isIncludeAndroidResources = true } }` in that module.

Honest note: **that test asserts nothing** — it is a smoke test. jetpacker has no assertion-based
Compose tests at all: no `createAndroidComposeRule`, no `onNodeWithText`/`assertIsDisplayed`, no
mocking library, no Hilt test harness, and no `androidTest` sources despite declaring the deps.

`[CANONICAL]` the manual-advance shape jetpacker sets up but never completes:

```kotlin
composeTestRule.mainClock.autoAdvance = false
composeTestRule.setContent { MaterialExpressiveTheme { MyMorphingCard(expanded = expanded) } }

composeTestRule.mainClock.advanceTimeByFrame()   // compose + lay out the first frame
expanded = true
composeTestRule.mainClock.advanceTimeBy(150L)    // land mid-spring; assert here

composeTestRule.mainClock.autoAdvance = true     // only if nothing infinite is on screen
```

- `advanceTimeByFrame()` immediately after `setContent`, or nothing has been laid out.
- Never re-enable `autoAdvance` while an infinite animation is on screen — that is §1 again.
- Springs have no duration; "halfway" is undefined. Assert a *qualitative* property (larger than
  start, smaller than target), not an exact value, or the test breaks when `MotionScheme` retunes
  its springs between alphas.
- Prefer §4 to this. A numeric bound asserted mid-spring is the most brittle test you can write
  against Expressive.

---

## 4. Screenshot testing — the Compose Screenshot Testing gradle plugin

jetpacker's setup is production-shaped: 9 modules opted in, 17 committed reference PNGs, a CI job.

### 4.1 Why this matters specifically for Expressive

Everything Expressive gets wrong is invisible to assertions and glaring in a pixel diff:

- **Shape morphs and `MaterialShapes` silhouettes.** jetpacker ships a bug of exactly this class —
  `.clip(CircleShape)` applied *before* `.background(color, MaterialShapes.Burst.toShape())`, which
  nullifies the burst. No assertion catches it; the reference PNG shows a circle.
- **Motion end-states.** The renderer evaluates animations at a fixed clock, so the captured frame
  is deterministic — meaning infinite animations *are* screenshot-testable.
  `LoadingStateScreenshotPreview` captures jetpacker's `Morph` loader; a morph or rotation
  regression surfaces as a diff.
- **Alpha-version churn.** Pinning `material3 1.5.0-alpha16` (or alpha26) means shape, spacing,
  elevation and color defaults move *between alphas* with no change on your side. Screenshot
  validation is the only cheap guard against a bump silently restyling the app.
- **Dark mode and dynamic color** — whole-app visual states no unit test exercises. (Dynamic color
  has a caveat, §9.4.)
- **Loading/streaming states** — shimmer, `"Thinking..."`, the indeterminate wavy bar. Hardest to
  reach by hand, easiest to break.

### 4.2 Complete gradle configuration

`[OFFICIAL jetpacker]`, verbatim. All four pieces are required.

**1. Version catalog** (`gradle/libs.versions.toml`):

```toml
[versions]
composeScreenshot = "0.0.1-alpha15"

[libraries]
screenshot-validation-api = { group = "com.android.tools.screenshot", name = "screenshot-validation-api", version.ref = "composeScreenshot" }

[plugins]
android-compose-screenshot = { id = "com.android.compose.screenshot", version.ref = "composeScreenshot" }
```

**2. Root `build.gradle.kts`**: `alias(libs.plugins.android.compose.screenshot) apply false`

**3. `gradle.properties`** (project-wide): `android.experimental.enableScreenshotTest=true`

**4. Per module** — all four of these, in every module with screenshot tests:

```kotlin
plugins {
  // ...
  alias(libs.plugins.android.compose.screenshot)
}

android {
  // ...
  experimentalProperties["android.experimental.enableScreenshotTest"] = true
  buildFeatures { compose = true }
}

dependencies {
  screenshotTestImplementation(libs.androidx.compose.ui.tooling)      // REQUIRED: renders @Preview
  screenshotTestImplementation(libs.screenshot.validation.api)        // REQUIRED: provides @PreviewTest
  // plus any module needed only by the fakes, e.g.:
  screenshotTestImplementation(project(":data:trips"))
}

screenshotTests {
  imageDifferenceThreshold = 0.05f
}
```

`screenshotTests { }` is a **top-level** block, sibling to `android { }`, not nested; jetpacker uses
`0.05f` — 5% — **identically in all 9 modules**. The flag is redundantly set **both** in
`gradle.properties` **and** per module via `experimentalProperties[...]`, and both appear required at
AGP 9.2.1 / plugin alpha15. Neither `screenshotTestImplementation` dep is optional: `ui-tooling`
renders the `@Preview`, `screenshot-validation-api` supplies `@PreviewTest`. `0.0.1-alpha15` is what
jetpacker pins — the plugin is pre-1.0 and its DSL can move, so check the current version before
pinning rather than assuming alpha15 is latest.

### 4.3 The test function form

`[OFFICIAL jetpacker]`
`android/feature/trip/itinerary/enrichment/src/screenshotTest/kotlin/com/example/jetpacker/feature/itinerary_enrichment/EnrichmentScreenshotTest.kt`
(VERBATIM, entire file)

```kotlin
package com.example.jetpacker.feature.itinerary_enrichment

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.android.tools.screenshot.PreviewTest

class EnrichmentScreenshotTest {
  @PreviewTest
  @Preview(showBackground = true)
  @Composable
  fun TripSummaryAndTipsScreenshotPreview() {
    TripSummaryAndTipsCardPreview()
  }

  @PreviewTest
  @Preview(showBackground = true)
  @Composable
  fun TripSummaryAndTipsLoadingScreenshotPreview() {
    TripSummaryAndTipsCardLoadingPreview()
  }
}
```

- A **plain class** — no `@RunWith`, no `@get:Rule`, no JUnit at all.
- **All three annotations** on each method — `@PreviewTest`, `@Preview(showBackground = true)`,
  `@Composable`. `@PreviewTest` comes from `com.android.tools.screenshot` and is the marker that
  promotes an IDE preview into a validated screenshot.
- The body just **calls the existing `@Preview` composable from `main`** — zero duplication; the
  preview you already wrote becomes the screenshot fixture. Same-package placement reaches internal
  previews without widening visibility.

### 4.4 Where reference PNGs live

```
<module>/src/screenshotTest/kotlin/<package>/XxxScreenshotTest.kt
<module>/src/screenshotTestDebug/reference/<package-as-dirs>/<TestClassName>/<TestMethodName>_<hash>_<index>.png

# concrete, from jetpacker:
feature/home/src/screenshotTest/kotlin/com/example/jetpacker/feature/home/HomeScreenshotTest.kt
feature/home/src/screenshotTestDebug/reference/com/example/jetpacker/feature/home/HomeScreenshotTest/HomeScreenScreenshotPreview_748aa731_0.png
feature/home/src/screenshotTestDebug/reference/com/example/jetpacker/feature/home/HomeScreenshotTest/LoadingStateScreenshotPreview_748aa731_0.png
```

Anatomy: `<PreviewFunctionName>_<8-hex-hash>_<previewIndex>.png`. The hash digests the `@Preview`
**configuration** — all 17 jetpacker references share `748aa731` because every screenshot preview
uses the identical `@Preview(showBackground = true)` with no device/`uiMode`/`fontScale` override.
**Change any `@Preview` parameter and the hash changes, orphaning the old PNG** — the most confusing
part of this workflow. Trailing `_0` is the index within a multi-`@Preview` function, so five stacked
`@Preview`s produce `_0`…`_4`. The source set is `screenshotTest<Variant>` = `screenshotTestDebug`,
so references are per build variant. Full-screen previews render at a fixed 1080×2400 (default
preview device) while component previews render at intrinsic height (jetpacker's are
215/368/395/508px), so `@Preview(showBackground = true)` on a component gives a tight, diff-friendly
crop for free — no `widthDp`/`heightDp` needed.

### 4.5 Gradle tasks

```bash
cd android
./gradlew validateDebugScreenshotTest                     # verify all modules (what CI runs)
./gradlew :feature:home:validateDebugScreenshotTest       # verify one module
./gradlew updateDebugScreenshotTest                       # regenerate/accept references
./gradlew :feature:home:updateDebugScreenshotTest
```

`validateDebugScreenshotTest` is confirmed verbatim from CI; `updateDebugScreenshotTest` is the
counterpart task the plugin registers (jetpacker's own docs never mention it). Reports land in
`**/build/reports/screenshotTest/` (reference / actual / diff triplets — what you send a designer),
`**/build/outputs/screenshotTest-results/`, and `**/build/test-results/validateDebugScreenshotTest/`
— paths taken from the CI upload globs, so authoritative.

### 4.6 The CI job

`[OFFICIAL jetpacker]` `.github/workflows/ci.yml`, the screenshot job, verbatim:

```yaml
  screenshot-tests:
    name: Run Screenshot Tests
    runs-on: ubuntu-24-04-c3-standard-4
    defaults:
      run:
        working-directory: android

    steps:
      - name: Checkout code
        uses: actions/checkout@9c091bb21b7c1c1d1991bb908d89e4e9dddfe3e0

      - name: Set up JDK 17
        uses: actions/setup-java@0f481fcb613427c0f801b606911222b5b6f3083a
        with:
          distribution: 'zulu'
          java-version: '17'

      - name: Cache Android SDK
        uses: actions/cache@caa296126883cff596d87d8935842f9db880ef25
        with:
          path: /usr/local/lib/android/sdk
          key: ${{ runner.os }}-android-sdk
          restore-keys: |
            ${{ runner.os }}-android-sdk

      - name: Setup Android SDK
        uses: android-actions/setup-android@40fd30fb8d7440372e1316f5d1809ec01dcd3699

      - name: Setup Gradle
        uses: gradle/actions/setup-gradle@3f131e8634966bd73d06cc69884922b02e6faf92
        with:
          cache-overwrite-existing: true

      - name: Make gradlew executable
        run: chmod +x gradlew

      - name: Run Screenshot Tests
        run: ./gradlew validateDebugScreenshotTest --build-cache --stacktrace

      - name: Upload Screenshot Test Reports
        if: always()
        uses: actions/upload-artifact@ff15f0306b3f739f7b6fd43fb5d26cd321bd4de5
        with:
          name: screenshot-test-reports
          path: |
            **/build/reports/screenshotTest/
            **/build/outputs/screenshotTest-results/
            **/build/test-results/validateDebugScreenshotTest/
```

Three jobs in that file total: `unit-tests` (`./gradlew test`), `screenshot-tests`, `build-apk`
(`./gradlew assembleDebug`). Worth copying: **no emulator** — Layoutlib renders on the JVM, which is
why this is cheap enough for every PR; all actions **pinned by commit SHA**; `if: always()` on the
upload so a *failing* run still publishes the diffs, which is the only run that needs them.

### 4.7 What to screenshot in an Expressive app

`[CANONICAL]` — jetpacker does none of this; it follows from §4.1. *What* to capture is the preview
matrix in §6; two rules govern *where*:

1. **The design-system module first.** jetpacker's clearest gap: `:core:ui`, which owns the shared
   components, has **no** screenshot tests while feature modules do. Shape/color/typography
   regressions originate in the design system.
2. **Wrap in the app theme, not bare `MaterialTheme`.** jetpacker wraps screen tests in
   `JetPackerTheme { }` but component tests in bare `MaterialTheme { }`, so those references do not
   validate the real typography or shape scale. For Expressive this is worse than a wart — bare
   `MaterialTheme` is not `MaterialExpressiveTheme`, so you capture standard motion/shape defaults.

---

## 5. Testable architecture — the pattern that makes all of this cheap

jetpacker uses **no mocking library** (no Mockito, no MockK) and **no Hilt test harness** (no
`@HiltAndroidTest`, no `HiltAndroidRule`, no test components). Every fake is an
`object : SomeInterface { }` or an anonymous subclass of an `open` ViewModel. This is a genuinely
good pattern and the reason its screenshot tests are readable.

**(a) ViewModels are `open class` with `open val uiState`.**
`[OFFICIAL jetpacker]` `android/feature/home/.../HomeViewModel.kt:47-101`:

```kotlin
@HiltViewModel
@SuppressLint("GlobalCoroutineDispatchers")
open class HomeViewModel
@Inject
constructor(
  private val tripDao: TripDao,
  private val eventDao: EventDao,
  // ... tourDetailDao, expenseDao, voiceInputManager
) : ViewModel() {
  private val _uiState = MutableStateFlow(HomeUiState())
  open val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()
```

Repeated across all 12 ViewModels: `MutableStateFlow` + `asStateFlow()` +
`_uiState.update { it.copy(...) }`, with `open class` / `open val` / `open fun`. Tests write
`object : XViewModel(fakes...) { override val uiState = MutableStateFlow(fixture) }`. **`open` plus
anonymous-object override is how they avoid a mocking library entirely.** Trade-off: production
classes are non-final. They accepted it.

**(b) Screens take `viewModel: X = hiltViewModel()` as a defaulted parameter**, so a test passes a
fake with no DI harness.

**(c) Better — screens ship as stateful/stateless overload pairs.**
`[OFFICIAL jetpacker]` `android/feature/detail/.../FlightDetailScreen.kt:84-96`:

```kotlin
@Composable
fun FlightDetailScreen(
  eventId: String? = null,
  onBack: () -> Unit,
  viewModel: FlightDetailViewModel = hiltViewModel(),
) {
  LaunchedEffect(eventId) { eventId?.let { viewModel.loadDetail(it) } }
  val uiState by viewModel.uiState.collectAsStateWithLifecycle()
  FlightDetailScreen(onBack = onBack, uiState = uiState)
}

@Composable
fun FlightDetailScreen(onBack: () -> Unit, uiState: FlightDetailUiState) { ... }
```

The test calls the **stateless** overload with a literal `FlightDetailUiState(...)` — no fakes at
all. Same split in `ChatbotScreen`/`ChatbotContent`, `HotelSupportChat`/`HotelSupportChatContent`,
`ReviewScreen`/`ReviewScreenContent`, `CreateTripScreen`/`CreateTripPanelContent`. **This is the
cleanest of the three and the one to recommend** — it is also what makes a screen previewable at
all, so it pays for itself twice.

### 5.1 Complete example test file — stateless, no DI

`[OFFICIAL jetpacker]`
`android/feature/detail/museum_assistant/src/screenshotTest/kotlin/com/example/jetpacker/feature/detail/museum_assistant/MuseumAssistantScreenshotTest.kt`
(VERBATIM, entire file)

```kotlin
package com.example.jetpacker.feature.detail.museum_assistant

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.android.tools.screenshot.PreviewTest
import com.example.jetpacker.core.ui.JetPackerTheme

class MuseumAssistantScreenshotTest {

  @PreviewTest
  @Preview(showBackground = true)
  @Composable
  fun ChatbotScreenScreenshotPreview() {
    val messages = listOf(
      ChatMessage(id = 1, text = "Hello! Can I ask about Louvre opening hours?", isUser = true, sender = "You"),
      ChatMessage(id = 2, text = "Yes, of course! The Louvre is open from 9 AM to 6 PM every day except Tuesday.", isUser = false, sender = "Museum Assistant"),
      ChatMessage(id = 3, text = "Are there any cafes inside?", isUser = true, sender = "You"),
      ChatMessage(id = 4, text = "Thinking...", isUser = false, sender = "Museum Assistant")
    )
    JetPackerTheme {
      ChatbotContent(
        messages = messages,
        onSendMessage = {},
        onBack = {}
      )
    }
  }
}
```

Twelve lines of fixture, no DI, no mocks, and it captures the in-flight AI placeholder as a
first-class visual state. This is the bar.

### 5.2 The fake-ViewModel form, when a screen is not split

`[OFFICIAL jetpacker]`
`android/feature/trip/itinerary/src/screenshotTest/kotlin/.../ItineraryScreenshotTest.kt:17-232`,
structure verbatim, the mechanical DAO stub bodies and the fixture list-building elided:

```kotlin
class ItineraryScreenshotTest {
  @PreviewTest
  @Preview(showBackground = true)
  @Composable
  fun ItineraryScreenScreenshotPreview() {
    val fakeEventDao =
      object : EventDao {
        override fun getEventsForTrip(tripId: String): Flow<List<TimelineEvent>> = emptyFlow()
        override fun getAllSessionIds() = flowOf(emptyList<TripSessionIdentifier>())
        override suspend fun insertEvent(event: TimelineEvent) {}
        // ... 17 more mechanical overrides returning flowOf(null) / Unit
      }
    val savedStateHandle = SavedStateHandle(mapOf("tripId" to "trip1"))
    val fakeTripDao = object : TripDao { /* flowOf(emptyList()) + no-op writes */ }
    // ... fakeDayThemeDao, fakeTourDetailDao, fakeTripSummaryAndTipsProvider, fakeDailyThemeProvider

    val fakeViewModel =
      object :
        ItineraryViewModel(
          savedStateHandle = savedStateHandle,
          eventDao = fakeEventDao,
          tripDao = fakeTripDao,
          // ... remaining constructor fakes
        ) {
        private val state =
          MutableStateFlow(
            ItineraryUiState(
              items = run { /* built from DummyData.events.filter { it.tripId == "2026-3" } */ },
              tripSummaryAndTips = "Relaxing, romantic, and cultural.",
              isGenerating = false,
            )
          )
        override val uiState: StateFlow<ItineraryUiState> = state
      }

    JetPackerTheme {
      ItineraryScreen(
        contentPadding = PaddingValues(0.dp),
        onBack = {},
        viewModel = fakeViewModel,
        onEventClick = { _, _ -> },
      )
    }
  }
}
```

Details worth stealing:

- **`SavedStateHandle(mapOf("tripId" to "trip1"))`** — nav args faked with a real `SavedStateHandle`.
  No Robolectric required.
- **`DummyData` is a real `main`-source object** (1290 lines in `:data:trips`), reachable via
  `screenshotTestImplementation(project(":data:trips"))` and shared with the app's first-run DB
  seeding, so screenshots depict realistic content instead of lorem ipsum.
- **Every string is a fixed literal** — `"10:00 AM"`, `"May 15, 2026"` — never
  `System.currentTimeMillis()` or `LocalDate.now()`. Where a screen genuinely needs the clock,
  inject it: jetpacker's `HomeScreenContent` takes `currentTimeMillis: Long` and the preview passes
  `Instant.parse("2026-05-19T12:00:00Z").toEpochMilli()`.

---

## 6. Previews for expressive UI

Under §4 previews *are* the screenshot fixtures, so preview coverage and screenshot coverage are the
same work.

`[OFFICIAL jetpacker]` `android/feature/home/.../HomeScreen.kt:458-507` — five stacked `@Preview`
annotations on one composable (two shown verbatim; the other three are "Phone Light - Large Font"
at `fontScale = 1.5f`, "Tablet Light" and "Tablet Dark", each identical but for `name`, `fontScale`,
`device` and `uiMode`):

```kotlin
@OptIn(ExperimentalComposeUiApi::class)
@Preview(
  name = "Phone Light - Standard Font",
  fontScale = 1.0f,
  showBackground = true,
  device = "spec:width=411dp,height=891dp,dpi=440",
  uiMode = Configuration.UI_MODE_NIGHT_NO,
)
@Preview(
  name = "Tablet Dark - Standard Font",
  fontScale = 1.0f,
  showBackground = true,
  device = "spec:width=1280dp,height=800dp,dpi=240",
  uiMode = Configuration.UI_MODE_NIGHT_YES,
)
@Composable
fun HomeScreenMultipreview() {
  ComposeUiFlags.isMediaQueryIntegrationEnabled = true
  JetPackerTheme {
    HomeScreenContent(trips = DummyData.trips, tripToDelete = null, onTripClick = {}, /* … */)
  }
}
```

The matrix is deliberate: phone light / phone dark / phone at `fontScale 1.5` / tablet light /
tablet dark. Note these are **repeated `@Preview` annotations, not a custom `@PreviewScreenSizes`-style
multipreview annotation class** — jetpacker defines no custom multipreview annotations. Note also
that `HomeScreenMultipreview` is **not** `@PreviewTest`-marked, so the matrix renders in the IDE
only; the screenshot-tested variant is a separate single-configuration function. **That is the gap
to close in your app: put `@PreviewTest` on the multipreview and get five validated references.**

### What to preview when the UI is expressive

| Axis | Why it matters for Expressive specifically |
| --- | --- |
| Light / dark (`uiMode`) | Expressive leans on `surfaceContainer*` roles and shadow; both invert |
| `fontScale = 1.5f` / `2.0f` | expressive components run *shorter* than predecessors (nav bar 80→64dp), so headroom for large text is thinner |
| Phone / foldable / tablet `device` spec | breakpoint layout swaps, FAB relocation, rail↔bar switches |
| Dynamic color on/off | only meaningful with a real wallpaper source — §9.4 |
| Shape-morph endpoints | one preview per state, driven by a boolean parameter |
| Loading / empty / error / streaming | the states nobody reaches by hand; jetpacker's real strength here — a `"Thinking..."` chat fixture plus dedicated shimmer and loading previews |
| RTL (`locale = "ar"`) | asymmetric expressive shapes and connected-group corner logic are direction-sensitive |
| Every adaptive branch | pin the window first, §9.5 |

Hygiene from jetpacker: `@Preview(showBackground = true)` as default,
`@Preview(showBackground = true, showSystemUi = true)` for full screens; naming `XxxPreview` /
`XxxLoadingPreview`; import always `androidx.compose.ui.tooling.preview.Preview` with
`implementation(libs.androidx.compose.ui.tooling.preview)` +
`debugImplementation(libs.androidx.compose.ui.tooling)`.

Anti-pattern present in jetpacker: previews calling the **stateful** screen with a default
`hiltViewModel()` (`JetPackerTheme { ItineraryScreen() }`) render empty in the IDE and are useless as
fixtures. If a preview renders blank, that is why — preview the stateless overload.

---

## 7. Manual verification checklist — expressive-specific

Automation covers none of these. Do them on a device before calling an expressive redesign done.

**Shape**

- [ ] Every morph at **both endpoints** — resting and target shapes are the intended `MaterialShapes`
      member, not a clipped approximation.
- [ ] Every morph at **mid-progress** — scrub it slowly (§3, or drag if gesture-driven).
      Feature-mapping between polygons with different vertex counts can produce a visibly wrong
      intermediate even when both endpoints are correct.
- [ ] No `.clip()` upstream of a `MaterialShapes.X.toShape()` background in the same chain — this
      silently nullifies the shape and is a real shipped bug in jetpacker.
- [ ] Connected-group corner logic at the ends of the group, with 2, 3 and 5 members.

**Motion**

- [ ] With **reduced motion / animator duration scale off** in Developer options: does the UI still
      communicate state? A motion-only state signal disappears entirely here. Repeat with the
      accessibility "Remove animations" setting — a different switch.
- [ ] Interrupt every transition halfway (press-release fast; navigate back mid-transition). Springs
      retarget, `tween`s snap. Anything that snaps is not on the motion scheme.
- [ ] Predictive back at 10%, 50%, full commit, and a cancelled swipe.

**Color and theme**

- [ ] Dark mode on the real device, on every screen — not just the previewed ones.
- [ ] Dynamic color with three genuinely different wallpapers, including one very saturated and one
      near-monochrome. Hand-picked `on*` colors break here first.
- [ ] Dynamic color **off** (below API 31, or the fallback scheme) — a different code path.

**Layout**

- [ ] Smallest and largest supported widths; then resize *live* in a freeform/desktop window and
      watch the breakpoint cross. Breakpoint bugs only show during the transition.
- [ ] **200% text scale** on every screen with a fixed `.height()`.
- [ ] Landscape, and unfolded on a foldable, including a posture change while the app is running.

**Screen reader**

- [ ] TalkBack over each **new container component** — `FloatingActionButtonMenu`, `FloatingToolbar`,
      `ButtonGroup`, segmented list items, `SplitButton`, expressive `SearchBar`. These have the
      least-settled semantics.
- [ ] The expanded FAB menu can be **dismissed without touch** (switch access / keyboard), and its
      items do not interleave with the content behind it.
- [ ] A single-select connected group announces "2 of 5", not five independent buttons.
- [ ] The floating toolbar stays expanded while a screen reader is active — the platform does this
      for you; verify your own state management is not fighting it.

---

## 8. Accessibility testing — what tooling catches and what it does not

**Catches automatically** (Accessibility Scanner, Espresso `AccessibilityChecks`, Compose UI-test
accessibility checks): touch targets under 48×48dp — including expressive XSmall (32dp) and Small
(40dp) buttons whose visual bounds are smaller than the required target; missing accessible names on
icon-only controls; text contrast below 4.5:1 for **static, known** color pairs; duplicate content
descriptions; overlapping clickable regions.

`[CANONICAL]` wiring — verify the API name against your Compose version, this surface is still
experimental:

```kotlin
composeTestRule.enableAccessibilityChecks()
composeTestRule.onRoot().tryPerformAccessibilityChecks()
```

**Does not catch — and these are the expressive-specific ones:**

- **Contrast under dynamic color.** The scanner sees the colors on *this* device with *this*
  wallpaper. Review the *pattern* (a hand-picked `on*` color instead of the scheme role), not a
  computed ratio.
- **Semantics on the new container components.** Missing `isTraversalGroup`, `traversalIndex`,
  `stateDescription`, `customActions` on a FAB menu, or `Role.RadioButton` on a single-select
  connected group are all *silently valid* to every checker. Nothing flags "these five buttons are a
  radio group." Largest blind spot for Expressive.
- **Traversal order.** A floating toolbar declared before the content it floats over reads in the
  wrong order; no tool objects.
- **Motion as the only state signal.** A morph that is the sole selection indicator is invisible at
  reduced motion and to a screen reader. Human only.
- **Live regions.** Streamed AI text with no `liveRegion` is announced never — jetpacker's streaming
  chat has exactly this defect.
- **Whether `contentDescription = null` is correct** — right on a decorative icon inside a labelled
  button, a failure when the icon *is* the control. Tools cannot tell.

Source-level patterns for each are in `review-checklist.md` §B.10; that section is *what to look for
in code*, this is *what a test run will and will not tell you*.

**Do not cite jetpacker as an accessibility reference.** `Modifier.semantics { }` appears **zero**
times across 103 Kotlin files: no `stateDescription`, no `role`, no `heading()`, no `liveRegion`, no
`mergeDescendants`, no `testTag`. Custom clickables are bare `Modifier.clickable { }` on `Row`/`Box`
with no `role` and no `onClickLabel`.

---

## 9. Gotchas

### 9.1 Flaky screenshots from animation

Layoutlib evaluates animations at a **fixed clock**, which is what makes infinite animations
screenshot-testable at all — but the frame it lands on is a function of the renderer. A
renderer/plugin upgrade can therefore shift *which* frame is captured, producing a wall of diffs
with no source change; regenerate deliberately rather than assuming the UI broke. Anything driven by
a **spring** rather than a clock (an `animate*AsState` reacting to an initial state change) may
capture mid-flight — fix by making the preview's initial state *be* the target state, not by raising
the threshold. Anything reading a real clock (`System.currentTimeMillis()`, `LocalDate.now()`, "2
hours ago") diffs every run: inject the clock (§5.2). Same for randomness, shuffled lists, and
`hashCode`-derived colors.

### 9.2 Threshold tuning

`imageDifferenceThreshold = 0.05f` means 5% of pixels may differ — enough to absorb antialiasing
noise while still catching a moved element or a changed corner radius. Raising it to hide flake
hides real regressions: a changed corner radius on a small component can itself be under 5% of a
full-screen image, which is the argument for testing components at intrinsic size (§4.4) rather than
only whole screens. A test that needs a looser threshold is signalling a nondeterministic fixture —
fix the fixture. The threshold is per-module, not per-test, so isolate a genuinely noisy surface into
its own module rather than loosening everything.

### 9.3 Font rendering differences

References are machine-dependent in practice even though rendering is JVM-side. **A different JDK
vendor or major version means different text rasterization** — jetpacker's CI pins **JDK 17 Zulu**
explicitly (its Gradle daemon runs JVM 21 while compiling to JVM 17). Pin vendor and version in CI
**and** tell contributors which to use, or expect reference churn. A custom font unreachable from the
`screenshotTest` source set silently falls back and every reference regenerates; variable fonts (the
`FontVariation.Settings(FontVariation.weight(...))` pattern used for expressive type scales) are the
most sensitive case, since a fallback there changes every glyph. Practical rule: **regenerate
references only on CI, or from one designated machine.** Never accept a local
`updateDebugScreenshotTest` that touched files you did not change.

### 9.4 Dynamic color makes screenshots machine-dependent

`dynamicLightColorScheme(LocalContext.current)` reads the **device wallpaper**. In a Layoutlib render
there is no wallpaper, so you get a fallback — and which fallback depends on the renderer and the
emulated API level. So: **do not screenshot-test the dynamic-color path.** Screenshot the
fixed-scheme path and pass `dynamicColor = false` (or equivalent) from every `@PreviewTest` fixture.
That requires the theme composable to *have* the parameter — a theme hardcoding
`Build.VERSION.SDK_INT >= S -> dynamicLightColorScheme(...)` with no override is untestable by
construction, so make the branch a parameter with a sensible default. jetpacker sidesteps this by
not supporting dynamic color at all (only `JetPackerTheme(darkTheme: Boolean = isSystemInDarkTheme())`,
with all 35 color roles enumerated by hand) — which is why its screenshots are stable and also why
it is no evidence about dynamic color. Verify dynamic color manually (§7) or on a device with a
pinned wallpaper. Not in screenshots.

### 9.5 Environment-dependent adaptive output

If the UI uses `derivedMediaQuery`, the screenshot reads the *real* window and output becomes
environment-dependent. Pin it with a fake `UiMediaScope`.

`[OFFICIAL jetpacker]` `android/feature/home/src/screenshotTest/kotlin/.../HomeScreenshotTest.kt:17-83`
(VERBATIM, abridged to the mechanism):

```kotlin
@file:OptIn(
  androidx.compose.ui.ExperimentalMediaQueryApi::class,
  androidx.compose.ui.ExperimentalComposeUiApi::class
)

@OptIn(ExperimentalComposeUiApi::class)
private val initMediaQuery = run {
  ComposeUiFlags.isMediaQueryIntegrationEnabled = true
  true
}

@OptIn(ExperimentalComposeUiApi::class)
class MockUiMediaScope(
  override val windowWidth: Dp = 400.dp,
  override val windowHeight: Dp = 800.dp,
  override val windowPosture: UiMediaScope.Posture = UiMediaScope.Posture.Flat,
  override val pointerPrecision: UiMediaScope.PointerPrecision = UiMediaScope.PointerPrecision.Coarse,
  override val keyboardKind: UiMediaScope.KeyboardKind = UiMediaScope.KeyboardKind.Virtual,
  override val hasCamera: Boolean = true,
  override val hasMicrophone: Boolean = true,
  override val viewingDistance: UiMediaScope.ViewingDistance = UiMediaScope.ViewingDistance.Near
) : UiMediaScope

// inside the test class, on a @PreviewTest @Preview @Composable function:
CompositionLocalProvider(LocalUiMediaScope provides MockUiMediaScope()) { HomeScreenPreview() }
```

Pass `MockUiMediaScope(windowWidth = 1280.dp)` to pin the other side of a breakpoint. The top-level
`private val initMediaQuery = run { ... }` is required because tests and previews never run
`Application.onCreate`, where the flag is normally set. `derivedMediaQuery` is experimental and
flag-gated — full treatment in the navigation skill's `nav-containers.md` §9a and in the base
skill's `modern-compose-idioms.md`.

### 9.6 Orphaned and missing references

All three hazards are live in jetpacker and all three will happen to you:

- **Orphaned references** — `feature/trip/itinerary/src/screenshotTestDebug/reference/.../CloudHybridScreenshotTest/`
  holds three PNGs for a test class that no longer exists there. `validate` does not fail on
  unreferenced images, so they rot silently.
- **Reference-less tests** — three jetpacker screenshot tests (museum_assistant, hotel_chat, review)
  have no committed references at all. Check what `validate` actually did rather than trusting green.
- **Hash churn** — changing any `@Preview` parameter changes the `_<hash>_` segment and orphans the
  old file (§4.4). Delete the old one in the same commit.

### 9.7 Do not test what the theme already guarantees

Asserting that a button's corner radius is 20.dp duplicates the shape scale and breaks on the next
alpha. Test the *binding* (this component reads `MaterialTheme.shapes.largeIncreased`) or the
*pixels* (screenshot). Not the number.

---

## 10. Quick index

| Problem | Section |
| --- | --- |
| Test hangs on loading indicator / wavy progress / morph | §1, §2 |
| Assert mid-animation | §3 |
| Set up screenshot testing from scratch | §4.2 |
| Write the first screenshot test | §4.3, §5.1 |
| Reference PNG in the wrong place / hash changed | §4.4, §9.6 |
| Wire screenshot tests into CI | §4.6 |
| Make a screen testable at all | §5 |
| Decide what to preview | §6 |
| Ship checklist before an expressive redesign lands | §7 |
| "Is it accessible?" | §8, plus `review-checklist.md` §B.10 |
| Screenshots diff with no source change | §9.1, §9.3, §9.4 |
| Adaptive screenshot differs per machine | §9.5 |

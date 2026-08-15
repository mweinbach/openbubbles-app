# Adjacent Modern Compose Idioms (not Material)

APIs you will meet in current expressive-era Google code that are **not part of `material3`**. Every
one comes from `androidx.compose.ui` or `androidx.compose.foundation`, and every one is here for a
single reason: **Google's own current sample, `android/ai-samples/jetpacker`, uses it.** A reader
working from that sample will hit these and needs to know what they are.

## Read this before using any of it

- **Most of these are experimental.** They require `@OptIn`, some require a process-wide feature
  flag, and any of them can change signature or disappear between Compose releases. The per-API
  status is in the table below and repeated at each section.
- **None of them is Material.** They do not read `MaterialTheme`, they are not part of Expressive,
  and adopting them does not make a UI more expressive. They are orthogonal plumbing that happens to
  co-occur with expressive-era code.
- **jetpacker is not an Expressive showcase.** It uses plain `MaterialTheme` (not
  `MaterialExpressiveTheme`), has zero `MotionScheme` usage, zero dynamic color, and zero expressive
  components. Its genuinely novel contributions are the APIs on this page plus navigation3 and the
  screenshot-test setup. Do not cite it as evidence for anything Expressive.
- **Versions:** jetpacker pins AGP `9.2.1`, Kotlin `2.3.10`, `compose-bom 2026.03.00`, and
  `material3 1.5.0-alpha16`. Availability and opt-in names below are as observed at those versions.
  Verify against the project in front of you before writing an import.

| API | Package | Opt-in / flag | Verdict |
| --- | --- | --- | --- |
| `Modifier.dropShadow` | `androidx.compose.ui.draw` | **none observed** in jetpacker | Adopt where `Modifier.shadow` cannot express it |
| `derivedMediaQuery` / `UiMediaScope` | `androidx.compose.ui` | `ExperimentalMediaQueryApi` + `ExperimentalComposeUiApi` **and** a runtime flag | Experimental; `WindowSizeClass` is still the conservative choice |
| `FlexBox` / `Modifier.flex` | `androidx.compose.foundation.layout` | `ExperimentalFlexBoxApi` | Experimental; use for genuine row↔column flips only |
| `TextAutoSize.StepBased` | `androidx.compose.foundation.text` | none observed | Adopt — replaces hand-rolled shrink loops |
| `Modifier.animateItem` | `androidx.compose.foundation.lazy` scopes | none — stable, renamed from `animateItemPlacement` | Adopt, with stable `key`s |
| `CompositingStrategy.Offscreen` + `BlendMode.DstIn` | `androidx.compose.ui.graphics` | none — stable | Adopt; extract it into one modifier |
| `Modifier.animateBounds` + `LookaheadScope` | `androidx.compose.animation` / `androidx.compose.ui.layout` | none at bom 2026.03.00 | See the motion skill, §7 here is a pointer only |

---

## 1. `Modifier.dropShadow` — how current Google samples do elevation and glow

**Status:** `import androidx.compose.ui.draw.dropShadow`. **No `@OptIn` annotation appears on any of
jetpacker's 12 call sites**, and the files that use it carry no opt-in at all. Treat as available and
un-gated at `compose-bom 2026.03.00`; confirm it resolves at your Compose version before relying on
it. It is the most-used new API in the sample — **12 sites**, a genuine idiom, not a one-off.

Shape: `Modifier.dropShadow(shape) { ... }` with a trailing `ShadowScope` lambda whose properties are
`radius`, `spread`, `offset`, `color`, and `brush` — all set in **pixels**, hence `x.dp.toPx()`
inside the scope.

### 1.1 Stacked, for layered elevation

`[OFFICIAL jetpacker]`
`android/core/ui/src/main/kotlin/com/example/jetpacker/core/ui/components/JetPackerToolbar.kt:94-104`

```kotlin
        .dropShadow(shape = shape) {
          radius = 3.dp.toPx()
          offset = Offset(0f, 1.dp.toPx())
          color = Color.Black.copy(alpha = 0.3f)
        }
        .dropShadow(shape = shape) {
          radius = 8.dp.toPx()
          offset = Offset(0f, 4.dp.toPx())
          spread = 3.dp.toPx()
          color = Color.Black.copy(alpha = 0.15f)
        }
        .background(MaterialTheme.colorScheme.onSurface, shape)
```

Two stacked `dropShadow`s = a two-layer M3-style elevation shadow: a tight, darker key shadow plus a
wide, soft ambient shadow. **Not expressible with `Modifier.shadow(elevation)`**, which gives one
platform-derived shadow with no control over its parts.

### 1.2 `radius = 0f` / `spread = 0f` — the sticker shadow

`[OFFICIAL jetpacker]` `android/core/ui/.../components/JetPackerExtendedFloatingActionButton.kt:66-87`:

```kotlin
  val shape = CircleShape
  val inverseSurface = MaterialTheme.colorScheme.inverseSurface

  Row(
    modifier =
      modifier
        .dropShadow(shape = shape) {
          radius = 0f
          spread = 0f
          offset = Offset(x = 2.dp.toPx(), y = 3.dp.toPx())
          color = inverseSurface
        }
        .background(MaterialTheme.colorScheme.primary, shape)
        .border(1.dp, MaterialTheme.colorScheme.onSurfaceVariant, shape)
        .clip(shape)
```

Zero blur plus an offset = a crisp neo-brutalist "sticker" shadow, and this is the app's signature
look. Impossible with `Modifier.shadow(elevation)`. Note the color is hoisted into a local
(`val inverseSurface = MaterialTheme.colorScheme.inverseSurface`) **before** the modifier chain —
`MaterialTheme` cannot be read inside the `ShadowScope` lambda, which runs in a draw scope.

The same `radius = 0f, spread = 0f` idiom appears at `JetPackerFab.kt:63-68`,
`VoiceNotesScreen.kt:462-469` and `:580-585`, but there the color is a hardcoded
`Color(0xFF20290A)` rather than a theme role — so those three shadows do not follow dark mode.
Copy the `inverseSurface` version, not the literal.

### 1.3 `brush = <animated gradient>` — the AI/glow treatment

`[OFFICIAL jetpacker]`
`android/feature/trip/itinerary/enrichment/.../TripSummaryAndTipsCard.kt:139-145`:

```kotlin
        .dropShadow(RoundedCornerShape(24.dp)) {
          if (isLoaded) {
            brush = glowBrush
            radius = 24.dp.toPx()
            offset = Offset(x = 0f, y = 16.dp.toPx())
          }
        }
```

`ShadowScope.brush` accepts a `Brush`, so the shadow itself becomes a moving three-color gradient.
The `if` **inside the scope** means "no shadow while loading" without branching the modifier chain —
a neat trick worth remembering generally. The brush, same file:

```kotlin
  val infiniteTransition = rememberInfiniteTransition(label = "glow_transition")
  val rotation by
    infiniteTransition.animateFloat(
      initialValue = 0f,
      targetValue = 360f,
      animationSpec =
        infiniteRepeatable(
          animation = tween(durationMillis = 4000, easing = LinearEasing),
          repeatMode = RepeatMode.Reverse,
        ),
      label = "glow_rotation",
    )

  val glowBrush =
    remember(rotation, containerSize) {
      val width = containerSize.width.toFloat()
      if (width <= 0f)
        return@remember Brush.linearGradient(listOf(Color.Transparent, Color.Transparent))

      val xOffset = width * (rotation / 360f)
      Brush.linearGradient(
        colors =
          listOf(
            Color(0xFF9CEFFF), // Cyan
            Color(0xFFD2E4FF), // Blue
            Color(0xFFFFB59F), // Pink
          ),
        start = Offset(xOffset - width, 0f),
        end = Offset(xOffset, 0f),
        tileMode = TileMode.Mirror,
      )
    }
```

`TileMode.Mirror` plus start/end offsets sliding by `width * (rotation / 360f)` gives a seamlessly
looping shimmer. **Two flaws in the shipped code — do not copy them:** `remember(rotation, ...)`
rebuilds the `Brush` every frame (hoist the gradient and animate a `graphicsLayer` or a shader
uniform instead), and the colors are hardcoded literals rather than theme roles, so the glow does
not respond to dark mode.

### When to use / when not

**Use `dropShadow` when:** you need two or more shadow layers; you need a hard offset shadow with no
blur; you need a colored or gradient shadow; you need the shadow to animate independently of
elevation semantics.

**Do not use it when:** a plain `Modifier.shadow(elevation, shape)` or a Material component's own
`elevation`/`shadowElevation` parameter does the job — those carry platform elevation semantics
(including the correct ambient/key split per API level) that a hand-rolled `dropShadow` throws away.
Do not use it to fake Material elevation tokens; read `MaterialTheme` elevation instead. And note
that hardcoded `Color.Black.copy(alpha = ...)` shadows, as in §1.1, are not theme-aware — in a dark
scheme a black shadow on a dark surface is invisible.

Related: jetpacker also uses `Modifier.blur(80.dp, edgeTreatment = BlurredEdgeTreatment.Unbounded)`
for its voice-mode aurora (`VoiceNotesScreen.kt:348`). Unbounded edge treatment is what lets the blur
bleed past the composable's bounds.

---

## 2. `derivedMediaQuery` / `ExperimentalMediaQueryApi`

**Status: experimental and runtime-flag-gated.** Requires `@OptIn` on **two** annotations —
`ExperimentalMediaQueryApi` and `ExperimentalComposeUiApi` — *and*
`ComposeUiFlags.isMediaQueryIntegrationEnabled = true` set before first composition. Without the
flag the API silently does not integrate.

**Cross-reference:** the navigation skill covers this in full — `nav-containers.md` §9a (API surface,
flag idiom, all six call sites, testing) and `adaptive-and-nav3.md` (when it is and is not the right
choice against `WindowSizeClass` and pane scaffolds). This section is the short form.

```kotlin
import androidx.compose.ui.ComposeUiFlags
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.ExperimentalMediaQueryApi
import androidx.compose.ui.derivedMediaQuery
import androidx.compose.ui.LocalUiMediaScope   // tests/previews only
import androidx.compose.ui.UiMediaScope        // tests/previews only
```

`derivedMediaQuery { ... }` returns a `State<T>`, consumed with `by`. The lambda receiver is
`UiMediaScope`, exposing `windowWidth: Dp`, `windowHeight: Dp`, `windowPosture`, `pointerPrecision`,
`keyboardKind`, `hasCamera`, `hasMicrophone`, `viewingDistance`.

### The flag, set at process start

`[OFFICIAL jetpacker]` `android/app/src/main/kotlin/com/example/jetpacker/JetPackerApplication.kt:17-52`:

```kotlin
@HiltAndroidApp
class JetPackerApplication : Application() {

  @OptIn(ExperimentalComposeUiApi::class)
  override fun onCreate() {
    ComposeUiFlags.isMediaQueryIntegrationEnabled = true
    super.onCreate()
    // ... FeatureFlags.initialize, Firebase init
  }
}
```

Note it is set **before** `super.onCreate()`. Because previews and tests never run
`Application.onCreate`, jetpacker re-sets it in three more places — a defensive belt-and-braces idiom
worth copying. Top-level in the screen file (`HomeScreen.kt:125-129`), so it runs at class init:

```kotlin
@OptIn(ExperimentalComposeUiApi::class)
private val initMediaQuery = run {
  ComposeUiFlags.isMediaQueryIntegrationEnabled = true
  true
}
```

and again as the first statement inside each preview body (`HomeScreen.kt:496`, `:513`), and again as
a top-level `run { }` in the screenshot-test file.

### Breakpoints and use

`[OFFICIAL jetpacker]`, used consistently across six call sites:

```kotlin
val foldableBreakpoint by derivedMediaQuery { windowWidth >= 600.dp }
val tabletBreakpoint  by derivedMediaQuery { windowWidth >= 1200.dp }
```

600dp = "unfolded / small tablet"; 1200dp = "large tablet / desktop". They drive a whole-layout swap
(`TabletLayout` vs `PhoneLayout`), FAB placement and suppression, column count, screen gutters, and —
the ergonomic argument for the whole API — **component-level** decisions:
`TripCard.kt:214-226` has a leaf component query the window itself and pick `displaySmall` vs
`titleLarge`, with no size-class prop-drilling.

### Testing it

```kotlin
CompositionLocalProvider(LocalUiMediaScope provides MockUiMediaScope(windowWidth = 1280.dp)) {
  ScreenUnderTest()
}
```

`MockUiMediaScope` is a plain class implementing `UiMediaScope` with defaults for all eight members.
Full source in the review skill's `testing-expressive-ui.md` §9.5.

### When to use / when not

**Use it when:** a leaf component needs its own window knowledge and prop-drilling a size class is
the only alternative; phone and tablet layouts differ in *kind* rather than in measurement.

**Do not use it when:** you have (or will add) a pane scaffold or `NavigationSuiteScaffold` — those
consume `WindowAdaptiveInfo`, and `derivedMediaQuery` then becomes a second source of truth that
disagrees at the boundary. Do not mix them. Also note jetpacker's 1200dp threshold is **not** an M3
breakpoint (Expanded starts at 840dp), so its layouts are not size-class-conformant by construction.
`WindowSizeClass` remains the conservative choice, and `material3.adaptive` went **stable at 1.3.0**
while this is still experimental.

---

## 3. `FlexBox` / `Modifier.flex { basis() / grow() }`

**Status: experimental.** `androidx.compose.foundation.layout.FlexBox`, opt-in `ExperimentalFlexBoxApi`.
Two call sites in jetpacker, both in one file. CSS-flexbox semantics in Compose: one composable that
becomes a `Row` or a `Column` by configuration, with `basis`/`grow` instead of `weight`.

`[OFFICIAL jetpacker]` `android/feature/home/.../components/TripCard.kt:85-96, 156-220`:

```kotlin
@OptIn(ExperimentalMediaQueryApi::class, ExperimentalFlexBoxApi::class)
@Composable
fun TripCard(
  trip: Trip,
  isSwipedOff: Boolean,
  modifier: Modifier = Modifier,
  useHorizontalLayout: Boolean = true,
  canSwipeToDelete: Boolean = true,
  showTitle: Boolean = true,
  onClick: () -> Unit = {},
  onSwipeToDelete: () -> Unit = {},
) {
```

```kotlin
    FlexBox(
      modifier =
        Modifier.fillMaxWidth()
          .clip(RoundedCornerShape(40.dp))
          .then(modifier)
          .background(MaterialTheme.colorScheme.surface)
          .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(40.dp))
          .clickable(onClick = onClick)
          .padding(16.dp),
      config = {
        if (useHorizontalLayout) {
          direction(FlexDirection.Row)
        } else {
          direction(FlexDirection.Column)
        }

        if (useHorizontalLayout) {
          gap(32.dp)
        } else {
          gap(24.dp)
        }

        alignItems(FlexAlignItems.Stretch)
      },
    ) {
      val imageModifier =
        if (useHorizontalLayout) {
          Modifier.flex { basis(0.5f) }.aspectRatio(1.31f)
        } else {
          Modifier.fillMaxWidth().requiredHeight(200.dp)
        }
      // ... Image / AsyncImage with imageModifier

      val tabletBreakpoint by derivedMediaQuery { windowWidth >= 1200.dp }
      val spacers = if (tabletBreakpoint) 16.dp else 8.dp

      Column(
        modifier = Modifier.flex { grow(1f) },
        verticalArrangement = Arrangement.spacedBy(spacers),
      ) { /* title, dates, location, participants */ }
    }
```

API shape: `FlexBox(modifier, config = { direction(...); gap(...); alignItems(...) }) { children }`,
with per-child `Modifier.flex { basis(0.5f) }` / `Modifier.flex { grow(1f) }`.

**Use it when:** the same subtree must render as a row on wide windows and a column on narrow ones.
It replaces the "duplicate the whole subtree in a `Row` branch and a `Column` branch" antipattern,
which is where drift between the two forms comes from.

**Do not use it when:** the layout is only ever a `Row` or only ever a `Column` — `Row`/`Column` with
`weight` are stable, cheaper to read, and do not need an opt-in. Do not reach for it just because it
looks like CSS.

**Bug in the shipped code, do not copy:** `Modifier.fillMaxWidth()...then(modifier)` re-applies the
caller's `modifier` mid-chain while the enclosing `SwipeToDismissBox` already received the same
`modifier` — a double-apply.

**Related stale opt-in:** `ExperimentalGridApi` is imported and opted into at `HomeScreen.kt:42,403`
but no `Grid`/`GridCells` from `foundation.layout` is used there; the grid is an ordinary
`androidx.compose.foundation.lazy.grid.LazyVerticalGrid`. Do not infer `Grid` adoption from that
opt-in.

---

## 4. `TextAutoSize.StepBased`

**Status:** `androidx.compose.foundation.text.TextAutoSize`, used via the `autoSize` parameter on
`Text`. No `@OptIn` observed at jetpacker's version. Two call sites, both "a big number or a long
title must fit on one line" — which is exactly the expressive hero-typography problem.

`[OFFICIAL jetpacker]` `android/feature/trip/itinerary/.../ItineraryScreen.kt:242-252`:

```kotlin
            Text(
              uiState.trip?.title?.uppercase() ?: "ITINERARY",
              style =
                MaterialTheme.typography.titleLarge.copy(
                  fontSize = 22.sp,
                  fontFamily = SekuyaFontFamily,
                ),
              color = MaterialTheme.colorScheme.secondary,
              maxLines = 1,
              autoSize = TextAutoSize.StepBased(minFontSize = 12.sp, maxFontSize = 22.sp),
            )
```

`[OFFICIAL jetpacker]` `android/feature/trip/expenses/.../ManageExpensesScreen.kt:302-315`:

```kotlin
          Surface(shape = RoundedCornerShape(30.dp), color = MaterialTheme.colorScheme.surface) {
            Text(
              text = totalText,
              style =
                MaterialTheme.typography.displayLarge.copy(
                  fontSize = 84.sp,
                  fontWeight = FontWeight.Black,
                ),
              color = MaterialTheme.colorScheme.onSurface,
              maxLines = 1,
              autoSize = TextAutoSize.StepBased(minFontSize = 24.sp, maxFontSize = 84.sp),
            )
          }
```

`autoSize = TextAutoSize.StepBased(min, max)` plus `maxLines = 1` is the whole recipe. It is the
modern replacement for hand-rolled `onTextLayout` shrink loops.

**Use it when:** an expressive display numeral or hero title must not clip in a container whose width
you do not control — a currency total, a trip title, a stat tile.

**Do not use it when:** the text can simply wrap, or when shrinking would take it below a legible
size. Note the interaction with accessibility: auto-sizing *fights* the user's font-scale setting, so
set `minFontSize` at a size that is still readable at the user's scale rather than letting it shrink
to fit at any cost. Do not apply it to body copy.

---

## 5. `Modifier.animateItem`

**Status: stable.** The current name — it was `Modifier.animateItemPlacement`, which is what older
code and older model memory will produce. It is scoped to lazy item scopes
(`LazyItemScope` / `LazyGridItemScope`), so it is only callable inside an `item { }` / `items { }`
body.

`[OFFICIAL jetpacker]` — applied to **every** itinerary list item
(`ItineraryScreen.kt:360, 367, 378, 386, 398`), paired with the sample's absolute discipline about
keys: `items(events, key = { it.id })`, `item(key = "header_image")`, `item(key = header.date)`,
`items(3, key = { "shimmer_$it" })`, `itemsIndexed(upcomingTrips, key = { _, trip -> trip.id })` —
**zero un-keyed lazy items in the whole repo.**

`[CANONICAL]` the shape (jetpacker's exact call sites were not captured verbatim):

```kotlin
LazyColumn {
    items(events, key = { it.id }) { event ->
        TimelineItemCard(event, modifier = Modifier.animateItem())
    }
}
```

**Use it when:** list contents can be added, removed or reordered. With stable `key`s this gives
free add/remove/move animation and is the cheapest expressive win available in a list.

**Do not use it when:** items have no stable identity — without `key`, `animateItem` animates the
wrong things, because Compose cannot tell "item moved" from "item at this index changed". Also skip
it for lists that only ever append at the end and scroll, where the animation is pure cost.

---

## 6. The `CompositingStrategy.Offscreen` + `BlendMode.DstIn` scroll-fade recipe

**Status: stable** — `androidx.compose.ui.graphics.CompositingStrategy`,
`androidx.compose.ui.graphics.BlendMode`. This is the current canonical way to fade the edge of a
scrolling container, and jetpacker repeats it **five times, identically**: `PhoneLayout.kt:84-96`,
`TabletLayout.kt:78-90`, `ItineraryScreen.kt:338-352`, `ManageExpensesScreen.kt:360-372`,
`VoiceNotesScreen.kt:247-259`.

`[OFFICIAL jetpacker]`:

```kotlin
    Modifier.fillMaxSize()
        .graphicsLayer(compositingStrategy = CompositingStrategy.Offscreen)
        .drawWithContent {
          drawContent()
          drawRect(
            brush =
              Brush.verticalGradient(
                0f to Color.Black,
                0.8f to Color.Black,
                1f to Color.Transparent,
              ),
            blendMode = BlendMode.DstIn,
          )
        }
```

How it works: `CompositingStrategy.Offscreen` forces the subtree into its own layer, so blend modes
apply to *that layer* rather than to whatever is behind it. `BlendMode.DstIn` keeps the destination
(the content) only where the source (the gradient) is opaque — so the black-to-transparent gradient
becomes an alpha mask. The colors are irrelevant; only alpha matters.

**Use it when:** you want content to fade out at a container edge rather than being hard-clipped —
the standard treatment under a floating toolbar, a bottom bar, or a scrim-free app bar.

**Do not use it when:** a simple gradient `Box` overlay in the container's own background color would
do. The offscreen layer is a real cost — an extra buffer allocation and composite per frame — and on
a full-screen list it is measurable. Do not apply it to a subtree that is already inside another
offscreen layer.

**Extract it.** jetpacker's failure to do so is a named defect: the same 12 lines appear five times.
Write it once in the design-system module as `fun Modifier.fadingBottomEdge(fadeStart: Float = 0.8f)`
and use that.

---

## 7. `Modifier.animateBounds` + `LookaheadScope` — pointer only

**Status:** stable at `compose-bom 2026.03.00` / `material3 1.5.0-alpha16` — jetpacker's call site
carries no `@OptIn` at all. Import paths, which are easy to get wrong:

```kotlin
import androidx.compose.animation.animateBounds        // animation artifact, NOT animation-core
import androidx.compose.ui.layout.LookaheadScope       // ui artifact, NOT foundation.layout
```

One real call site in jetpacker (`TripScreen.kt:161` opens the scope, `:173` applies the modifier),
in `JetPackerBottomBar`. A third grep hit at `ItineraryScreen.kt:29` is an **unused import** — not a
call site.

**The motion skill covers this fully — do not duplicate it here.** `m3-expressive-motion`,
`references/motion-recipes.md` §9 (`§9a` is the jetpacker bystander pattern verbatim with the
ordering rules; `§9b` is androidify's motion-scheme-driven variant with a nullable scope). Go there
for the recipe, the modifier-ordering trap, and the `boundsTransform` discussion.

---

## 8. Build-side changes: AGP 9 / Kotlin 2.3

Four things in jetpacker's build files that will not match older muscle memory. All observed at
**AGP 9.2.1 / Kotlin 2.3.10 / KSP 2.3.5 / Gradle 9.5.0-milestone-5**.

### 8.1 The `compileSdk { }` block DSL

`[OFFICIAL jetpacker]` `android/app/build.gradle.kts`:

```kotlin
android {
  namespace = "com.example.jetpacker"
  compileSdk {
    version = release(libs.versions.compileSdk.get().toInt()) {
      minorApiLevel = libs.versions.compileSdkMinor.get().toInt()
    }
  }
```

This is the AGP 9 SDK-with-minor-version DSL (`compileSdk = "37"`, `compileSdkMinor = "0"` in the
catalog). Library modules in the same project still use the older scalar form —
`compileSdk = libs.versions.compileSdk.get().toInt()` — so **both forms coexist**; the block form is
only needed when you must pin a minor API level.

### 8.2 The `"ksp"(...)` string-quoted configuration

```kotlin
dependencies {
  implementation(libs.hilt.android)
  "ksp"(libs.hilt.compiler)
}
```

Used consistently in **every** module, instead of `ksp(...)`. It is a workaround for the KSP
configuration not being statically available in the Kotlin DSL accessor set under AGP 9 / KSP 2.3.5.
If `ksp(...)` resolves in your project, use it; if it does not compile, this is the escape hatch, not
a style choice.

### 8.3 `-Xannotation-default-target=param-property`

`[OFFICIAL jetpacker]` root `android/build.gradle.kts`:

```kotlin
subprojects {
  tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>().configureEach {
    compilerOptions {
      jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
      freeCompilerArgs.add("-Xannotation-default-target=param-property")
    }
  }
  tasks.withType<Test>().configureEach {
    failOnNoDiscoveredTests = false
  }
}
```

The Kotlin 2.2+ migration flag for the annotation-use-site-default change; required with K2.3 + AGP
9, otherwise annotations on constructor properties land on the wrong target and DI/serialization
break in confusing ways.

### 8.4 No Kotlin Android plugin

**`org.jetbrains.kotlin.android` appears nowhere in the project** — AGP 9.2.1 brings built-in Kotlin
support. Modules apply only `com.android.library` (or `com.android.application`), plus
`org.jetbrains.kotlin.plugin.compose` where Compose is used. If you are copying a module template
from an older sample, dropping that plugin is part of the AGP 9 migration.

Two more conventions from the same files, worth knowing but not AGP-9-specific: there is **no
`buildSrc`, no `build-logic`, no convention plugins** — every module repeats its own `android { }`
block and cross-cutting config is done in the root `subprojects { }`; and there is **no
Gradle-level `-opt-in=`** anywhere, so every experimental API on this page is opted into per-file
(`@file:OptIn(...)`) or per-call-site. For an Expressive project, prefer the global Gradle opt-in
over that — see `setup-and-versions.md`.

---

## 9. Adoption verdict

| API | Adopt? |
| --- | --- |
| `Modifier.dropShadow` | **Yes**, where `Modifier.shadow` cannot express the effect. Hoist theme colors out of the scope; do not hardcode black. |
| `TextAutoSize.StepBased` | **Yes** for hero numerals and titles. Set a legible `minFontSize`. |
| `Modifier.animateItem` | **Yes**, always with stable `key`s. |
| `CompositingStrategy.Offscreen` + `DstIn` | **Yes**, extracted into one named modifier. Watch the layer cost. |
| `Modifier.animateBounds` / `LookaheadScope` | **Yes** — see the motion skill. |
| `FlexBox` / `Modifier.flex` | **Only** for real row↔column flips. Experimental. |
| `derivedMediaQuery` | **Not by default.** Experimental, flag-gated, and it conflicts with pane scaffolds. `WindowSizeClass` unless a leaf component genuinely needs to self-query. |
| AGP 9 build DSL | Match whatever the project already uses; do not migrate a build opportunistically. |

Anything on this page can change. If an import does not resolve, check the release notes for the
`androidx.compose.ui` / `androidx.compose.foundation` line rather than assuming the code here is
wrong — it was correct at `compose-bom 2026.03.00`.

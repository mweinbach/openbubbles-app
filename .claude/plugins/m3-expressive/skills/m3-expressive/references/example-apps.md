# Reference Apps — Where These Patterns Came From

Two tiers of source were mined for this plugin.

**Tier 1 — official Google sources** (§7-§10, added 2026-08-14): the androidx `material3/samples`
module, `android/ai-samples/jetpacker`, `android/androidify`, and Jetcaster. Highest authority; use
these to settle any signature question.

**Tier 2 — four community apps** (§1-§4): real, shipping, open-source, and the origin of most
patterns in the component/motion/shape/theming skills. They show what people actually build, which is
often not what the samples show.

This file says which source to read for which problem, and — equally important — which parts of each
**not** to copy.

Community counts/versions verified 2026-08-01; official sources 2026-08-14. Repos may have moved on.

**Sourcing rule that overrides everything below:** for any Expressive component signature, prefer the
androidx `material3/samples` source (§9) over developer.android.com's component **guide** pages.
Those pages are stale — verified 2026-08-14, `components/app-bars` has no `AppBarRow`/`AppBarColumn`/
flexible bars, `components/search-bar` has no `SearchBarState`/`TopSearchBar`/
`ExpandedFullScreenSearchBar`, `components/navigation-rail` has no `WideNavigationRail`/
`ModalWideNavigationRail`/`ShortNavigationBar`, `components/carousel` has no
`HorizontalCenteredHeroCarousel` and never mentions `maskClip`/`maskBorder`, and
`designsystems/material3` mentions Expressive in prose only with no `MaterialExpressiveTheme` or
`MotionScheme` code at all.

---

## Summary

### Official Google sources

| Source | material3 | Size / scope | Best for |
| --- | --- | --- | --- |
| [androidx/androidx `material3/samples`](https://github.com/androidx/androidx/tree/androidx-main/compose/material3/material3/samples) | androidx-main tip (post-`1.5.0-alpha26`) | 48 sample files, `@Sampled` | **Every signature question.** The canonical minimal example for every API, including the ones with zero app usage anywhere |
| [jetpacker](https://github.com/android/ai-samples/tree/main/jetpacker) | `1.5.0-alpha16` pinned over BOM `2026.03.00` | 103 `.kt`, 19 modules | `animateBounds`/`LookaheadScope`, `derivedMediaQuery`, screenshot testing, nav3, `dropShadow`, AI/chat UI, testable ViewModels |
| [androidify](https://github.com/android/androidify) | `1.5.0-alpha20`, BOM `2026.05.01` | 34 files opt into Expressive | **The heaviest real Expressive app.** `MotionScheme`-driven `BoundsTransform`, `VerticalFloatingToolbar`, broad component usage |
| [Jetcaster](https://github.com/android/compose-samples/tree/main/Jetcaster) (in compose-samples) | `1.5.0-alpha22` pin over BOM `2026.08.00` | narrow Expressive surface | The **only** compose-sample that adopted Expressive |

### Community apps

| App | material3 | Compose BOM | Size | Opt-in strategy | Best for |
| --- | --- | --- | --- | --- | --- |
| [vivi-music](https://github.com/vivizzz007/vivi-music) | `1.5.0-alpha23` | none (pins `compose = 1.11.4` directly) | 566 `.kt` | global `freeCompilerArgs` **+** 72 explicit `@OptIn` in 32 files | Connected `ToggleButton` groups, `SplitButtonLayout`, wavy indicators, custom expressive sliders, `MaterialShapes` in production |
| [Tomato](https://github.com/nsh07/Tomato) | BOM-managed, **no pin** | `compose-bom-alpha:2026.03.00` | 110 `.kt` | 85 per-callsite `@OptIn`, no global | `MotionScheme`, floating-toolbar-as-nav, flexible top app bars, Navigation3, segmented list items |
| [LastChat](https://github.com/Cocolalilal/LastChat) | `1.5.0-alpha08` (inline pin) | `2025.11.00` | 637 `.kt` | global `compilerOptions.optIn.add`, **zero** annotations | `Morph` animation, reduced-motion policy, densest wavy-indicator corpus, variable-font depth |
| [Med](https://github.com/FeDeveloper95/Med) | `1.5.0-alpha21` | `2026.05.01` | 61 `.kt` | 38 × `@file:OptIn` | FAB menu (complete), `ShortNavigationBar` ↔ `WideNavigationRail`, incremental Expressive adoption, Wear module |
| [PixelMusicApp](https://github.com/ianshulyadav/PixelMusicApp) | — | — | **0 `.kt`** | — | **Nothing. No source code.** See §5. |

---

## 1. vivi-music — music player

**What it is.** A YouTube-Music-style Android music player. GPL-3.0. Package `com.music.vivi`.
The single largest and most Expressive-committed app in the corpus: 566 Kotlin files, 72
`ExperimentalMaterial3ExpressiveApi` occurrences across 32 files, plus a module-wide opt-in flag.

**Version pinned.** `material3 = "1.5.0-alpha23"`, `adaptive = "1.3.0-alpha09"`, AGP 9.1.1,
Kotlin 2.3.10, Compose 1.11.4 (pinned directly, no BOM), materialKolor 4.1.1.

### What it demonstrates best

- **Connected `ToggleButton` groups — the deepest corpus anywhere.** Twelve files use
  `ToggleButton` + `ButtonGroupDefaults`, covering ten distinct shapes of the pattern: an artist
  action bar (Subscribe/Radio/Shuffle), a `FlowRow` connected group on the queue screen, a group over
  a translucent player background, index-driven segmented radio over a dynamic list, an
  audio-quality selector inside a bottom sheet, an audio-device picker, a generic reusable
  "expressive action grid", a two-button equalizer mode switcher, **a connected group used as
  three-way destructive dialog buttons**, and horizontally scrollable version chips. If you need a
  connected group in any layout, the precedent is here.
- **The only real `SplitButtonLayout` usage in the corpus** — a complete sort-header component at
  `ui/component/SortHeader.kt`. (LastChat only has a dead import.)
- **Custom expressive slider family.** Four complete, working components:
  `ui/component/WavySlider.kt`, `SquigglySlider.kt`, `VolumeSlider.kt`, `PlayerSlider.kt`. The wavy
  seekbar idiom — `LinearWavyProgressIndicator` with `amplitude = { … }` driven by play state,
  `gapSize = thumbRadius + 4.dp`, and a hand-drawn `Canvas` thumb — is the reference implementation.
- **`MaterialShapes` in production, not in a demo.** Cookie-shaped album thumbnails in list rows
  (`ui/component/Items.kt`), player artwork switching between `Clover8Leaf` and a rounded rect
  (`ui/player/Thumbnail.kt`), `Clover4Leaf` / `Cookie7Sided` as reusable icon shapes
  (`ui/screens/settings/AboutScreen.kt`).
- **Wavy progress indicators at scale.** `CircularWavyProgressIndicator` across 8 files, including a
  determinate battery ring with a custom `Stroke`, an instrumental-break countdown ring inside synced
  lyrics, and a determinate ring with a percentage/check overlay for playlist downloads.
  `LinearWavyProgressIndicator` drives the Discord rich-presence song progress bar.
- **Loading indicators.** `LoadingIndicator` in list-pagination footers, media-info sheets, and
  cross-faded over a custom dot grid; `ContainedLoadingIndicator` for content-level loading and as
  the lyrics-pending state on the player screen.
- **Theme depth.** `MaterialExpressiveTheme` + `MotionScheme.expressive()` + materialkolor
  `rememberDynamicColorScheme` with `ColorSpec.SpecVersion.SPEC_2025`, seeded per-track from **album
  art** (`Palette` + `Score` in `MainActivity.kt`), plus a `pureBlack` AMOLED `ColorScheme` extension
  threaded through 93 call sites, plus four runtime-selectable variable-font families (48
  `FontVariation` sites).
- **Shape-morph-on-press, three ways.** `ButtonDefaults.shapes(shape, pressedShape)` and
  `IconButtonDefaults.shapes()` where the API supports it; `animateIntAsState` on
  `RoundedCornerShape(percent)` for pill→squircle where it doesn't; and a custom `Shape` that lerps
  each corner independently for non-button surfaces.

### What to copy

The connected-group shape assignment (`connectedLeadingButtonShapes()` /
`connectedMiddleButtonShapes()` / `connectedTrailingButtonShapes()` keyed on index, with
`Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween)` and
`semantics { role = Role.RadioButton }`). The `Theme.kt` structure. The wavy-seekbar recipe. The
`pureBlack` extension (`ColorScheme.copy(surface = Black, background = Black)` applied *after*
dynamic-color generation, gated on `darkTheme`).

### What not to copy

- **Coverage gaps.** No floating toolbars, no FAB menu, no `ShortNavigationBar`, no
  `WideNavigationRail`, no `Morph`, no flexible top app bars, no `VerticalDragHandle`, no
  `SharedTransitionLayout`. It hand-rolls a custom `FloatingNav` bottom bar instead of using
  `ShortNavigationBar` — do not treat that as a recommendation.
- **Opt-in inconsistency.** It has a global `-opt-in` flag *and* 72 redundant annotations. Pick one
  strategy; the redundancy here is historical, not a pattern.
- **`SplitButtonLayout` is the current name, not an old one.** There is no `SplitButton` composable
  on any artifact — this app's usage is correct as written (see `setup-and-versions.md` §4.10).
- **Heavy custom re-implementations.** `SquigglySlider`, the custom expressive-shape loading art, and
  the bespoke `FloatingNav` exist because stock components didn't fit that app. Reach for stock
  first; copy these only when you need that exact effect.
- **Not an architecture model.** 566 files with substantial duplication. Read it for component code,
  not for structure.

---

## 2. Tomato — Compose Multiplatform pomodoro timer

**What it is.** A KMP pomodoro timer (`shared/` + `androidApp/`). Package `org.nsh07.pomodoro`.
Small, current, and the most *idiomatic* of the four — it uses new APIs the way the docs intend.

**Version pinned.** No explicit material3 pin — it resolves from
`androidx.compose:compose-bom-alpha:2026.03.00`. That alpha BOM is what unlocks `SegmentedListItem`,
`ListItemDefaults.segmentedShapes`, `veilOut`/`unveilIn`, and `shapes.extraLargeIncreased`.
`adaptive = 1.2.0`, `navigation3 = 1.0.1`, AGP 9.1.0, Kotlin 2.3.20, CMP 1.11.0-alpha04.

### What it demonstrates best

- **`MotionScheme` — this is the reference, full stop.** 53 `motionScheme` references across 10
  files, of which 39 are spec calls: `defaultSpatialSpec()` ×19, `defaultEffectsSpec()` ×11,
  `slowEffectsSpec()` ×7, `slowSpatialSpec()` ×2. No other app in the corpus consumes it broadly.
  The idiom is consistent: `import androidx.compose.material3.MaterialTheme.motionScheme`, then hoist
  `val motionScheme = motionScheme` at the top of the composable so it can be used inside non-`@Composable`
  lambdas. It also shows the spatial/effects discipline correctly — `slowEffectsSpec()` for
  `animateColorAsState` focus↔break crossfades, `defaultSpatialSpec()` for an animated clock font
  size, and both together in `AnimatedVisibility` (`fadeIn(defaultEffectsSpec()) +
  scaleIn(defaultSpatialSpec(), 4f)`). Plus a genuinely clever "snap while dragging, spring on
  release" trick in `SliderListItem.kt`.
- **`HorizontalFloatingToolbar` as the app's bottom navigation** (`androidApp/.../ui/AppScreen.kt`) —
  with `FloatingToolbarDefaults.exitAlwaysScrollBehavior`, `vibrantFloatingToolbarColors`, and
  `ToggleButton`s as the nav items. The standout pattern in the whole corpus and a direct answer to
  Material's "toolbars on subsequent pages" guidance.
- **`LargeFlexibleTopAppBar` with `subtitle`** across five settings screens, plus
  `TopAppBar(subtitle = …, titleHorizontalAlignment = …)` on the timer.
- **Navigation3, for real.** The only repo that actually uses it: `NavDisplay` + `entryProvider` +
  `rememberNavBackStack` + `ListDetailSceneStrategy`, a root `NavDisplay` inside
  `SharedTransitionLayout` with a `predictivePopTransitionSpec`, and a nested `NavDisplay` for
  list/detail on one back stack.
- **Segmented list items** — heavy use of `SegmentedListItem`, `ListItemDefaults.segmentedShapes`,
  and `ListItemDefaults.segmentedColors` with AMOLED-aware token objects. This is the modern
  "grouped settings list" idiom.
- **The only `ButtonGroup` composable calls with `customItem` + overflow + `animateWidth`**
  (`TimerScreen.kt:448`, `AlarmSettings.kt:350`).
- **`SupportingPaneScaffold` + `VerticalDragHandle` + `paneExpansionDraggable`.**
- **`SharedTransitionLayout` / `sharedBounds`**, including a reusable `Modifier.sharedBoundsReveal`
  helper, plus `veilOut` / `unveilIn` (Compose Animation 1.10+ expressive transitions).
- **`MaterialShapes.toShape()`** ×3 (`Cookie12Sided`, `Square` as icon backgrounds and detail
  placeholders), and a variable-font `Type.kt` using `fontFeatureSettings = "ss02, dlig"` for Google
  Sans Flex's expressive alternates.
- **CMP `MaterialExpressiveTheme`** via `expect`/`actual`, with MaterialKolor `SPEC_2025` and an
  AMOLED fallback to `SPEC_2021`.

### What to copy

The `motionScheme` hoisting idiom and every spec choice it makes. The floating-toolbar-as-nav
composition. `LargeFlexibleTopAppBar(subtitle = …)`. The segmented list-item token setup
(`Shape.kt` + `Color.kt`). `sharedBoundsReveal`. The nav3 wiring if you are on nav3.

### What not to copy

- **No global opt-in.** 85 hand-written `@OptIn(ExperimentalMaterial3ExpressiveApi::class)`
  annotations. Defensible for a library; noise for an app.
- **No explicit material3 pin.** The Expressive version is implicit in the BOM, which makes "what am
  I actually on" hard to audit. Prefer a stable BOM plus an explicit pin unless you need alpha
  compose-ui too.
- **It never uses `expressiveLightColorScheme()`** — it uses `lightColorScheme()` + MaterialKolor.
  That works, but it means Tomato is not a reference for the stock Expressive color scheme.
- **The forked `calculatePaneScaffoldDirective`** (0dp gutter) in `ui/UiUtils.kt` is a workaround
  against the adaptive library, not an endorsed pattern.
- **`expect`/`actual` theme indirection** is KMP overhead. An Android-only app should not adopt it.

---

## 3. LastChat — AI chat app

**What it is.** An Android-only AI chat client (package `me.rerere.rikkahub`). 637 Kotlin files —
the largest file count in the corpus, though less Expressive-dense than vivi-music.

**Version pinned.** `androidx-material3 = { group = "androidx.compose.material3", name = "material3",
version = "1.5.0-alpha08" }` — an **inline** pin with no `[versions]` entry, overriding
`compose-bom:2025.11.00`. `material3.adaptive = 1.2.0`.

**Read the version carefully: alpha08 is by far the oldest pin in the corpus** and pre-dates almost
every graduation in the timeline. Its opt-in posture and API shapes reflect that.

### What it demonstrates best

- **`androidx.graphics.shapes.Morph` + `MaterialShapes` → animated `GenericShape`** —
  `ui/hooks/AvatarShape.kt`, a complete file. The **only** real `Morph` usage in the corpus. This is
  the reference for animating between two `MaterialShapes` polygons: `Morph(a, b)` +
  `morph.asCubics(progress)` inside a `GenericShape`. (Med declares `graphics-shapes` but never uses
  `Morph`.)
- **The cleanest global-opt-in proof.** Three lines in `app/build.gradle.kts`
  (`compilerOptions.optIn.add(...)` × `ExperimentalMaterial3Api`,
  `ExperimentalMaterial3ExpressiveApi`, `adaptive.ExperimentalMaterial3AdaptiveApi`) produce **zero**
  annotations in 637 files, despite using `LoadingIndicator`, `LinearWavyProgressIndicator`,
  `CircularWavyProgressIndicator`, `MaterialShapes`, and `FloatingToolbarDefaults`.
- **The densest wavy-indicator corpus.** `LinearWavyProgressIndicator` ×6,
  `CircularWavyProgressIndicator` ×7 (indeterminate, 24dp, mostly in the backup page).
- **`floatingToolbarVerticalNestedScroll` + `FloatingToolbarDefaults.ScreenOffset`** across four
  settings detail pages — the auto-collapse-on-scroll toolbar behaviour.
- **`MotionPolicy` — the best reduced-motion implementation in the corpus.** `ui/motion/MotionPolicy.kt`
  observes Android's three global animation-scale settings, exposes a `reduceMotion` flag through a
  CompositionLocal, and branches every nav transition on it. This is the accessibility-first
  counterpoint to `MotionScheme` and is worth copying **regardless** of which app you otherwise
  follow.
- **The most complete `FontVariation` treatment** — Google Sans Flex with `wght` / `wdth` / `ROND` /
  `GRAD` axes.
- **Hand-authored shape token set with optical-roundness math** (`ui/theme/Shape.kt`) — useful if you
  need a custom shape scale rather than the stock one.
- **App-wide `SharedTransitionLayout` + CompositionLocals**, preset themes with an AMOLED override and
  runtime typography, and a `PresetThemeButtonGroup` of horizontally scrolling theme swatches with
  edge fades.

### What to copy

`AvatarShape.kt` (the `Morph` recipe). `MotionPolicy.kt` (reduced motion). The global opt-in block.
The `FontVariation` axis setup.

### What not to copy

- **It uses plain `MaterialTheme`, not `MaterialExpressiveTheme`, and never touches
  `MotionScheme`.** It is Expressive *components* on a non-Expressive *theme*, which means every one
  of those components silently falls back to standard springs. Do not copy its `Theme.kt` as a
  target state.
- **alpha08 is stale.** Anything it does that depends on a signature may not match a current
  artifact. Cross-check against the graduation table before porting.
- **Dead code.** `SplitButtonLayout` is imported at
  `ui/pages/assistant/detail/AssistantImporter.kt:13` and **never called**. There is also a dead
  `LoadingIndicator` import in `ChatList.kt`. Do not mistake imports for usage.
- **Its `*ButtonGroup*` names are app-defined** (`PresetThemeButtonGroup`,
  `MinimalFileButtonGrouped…`), **not** the material3 API. Searching this repo for "ButtonGroup" is
  misleading.
- **nav3 is declared but disabled** — the version catalog lists Navigation3 while
  `app/build.gradle.kts` has the dependencies commented out. It ships Navigation2 (`NavHost`,
  `composable<T>`) plus a hand-rolled adaptive settings scaffold.

---

## 4. Med — medication tracker (with a Wear module)

**What it is.** A medication tracker, package `com.fedeveloper95.med`, with a **separate Wear OS
module** on the `androidx.wear.compose:compose-material3:1.6.2` train. The smallest app in the
corpus at 61 Kotlin files, and the one that adopted Expressive most *incrementally*.

**Version pinned.** `material3 = "1.5.0-alpha21"` (also for
`material3-adaptive-navigation-suite` and `material3-window-size-class`), `graphicsShapes = "1.1.0"`
declared explicitly, `compose-bom:2026.05.01`, AGP 9.2.1, Kotlin 2.4.0.

### What it demonstrates best

- **The complete FAB menu, in one file.** `elements/MainActivity/MainFAB.kt` contains
  `FloatingActionButtonMenu`, `FloatingActionButtonMenuItem`, `ToggleFloatingActionButton`,
  `Modifier.animateFloatingActionButton`, and `animateIcon` working together. The **only** real FAB
  menu in the corpus. If you need a FAB menu, read this file.
- **Incremental Expressive adoption — the single most transferable idea here.** Med's app-wide theme
  is plain `MaterialTheme`, but `MainFAB.kt` wraps just the FAB subtree in a nested
  `MaterialExpressiveTheme(colorScheme = MaterialTheme.colorScheme, typography = MaterialTheme.typography,
  shapes = MaterialTheme.shapes, motionScheme = MotionScheme.expressive())`. That is how you get
  expressive spring physics on one hero component without converting a whole app.
- **`ShortNavigationBar` ↔ `WideNavigationRail` adaptive switch** with
  `rememberWideNavigationRailState` (`services/MedApp.kt`). The **only** real usage of either.
- **Reusable connected `OutlinedToggleButton` groups**, both single- and multi-select, with
  `ToggleButtonDefaults.outlinedToggleButtonColors` (`elements/MainActivity/MedicineBottomSheet.kt`)
  — as shipped; on alpha26 that name is `@Deprecated @BytecodeOnly` and uncallable from Kotlin
  source, so port it to `OutlinedToggleButtonDefaults.colors` (`current.txt` at androidx HEAD
  `360e8cba`, 2026-08-14) —
  plus a two-button `ToggleButton` confirm affordance and a **custom
  `ToggleButtonDefaults.shapes()` with asymmetric corners** (`SettingsActivity.kt`).
- **`PullToRefreshDefaults.LoadingIndicator`** ×3 — expressive pull-to-refresh done with the stock
  API rather than a custom indicator.
- **`HorizontalFloatingToolbar` with `leadingContent` / `trailingContent` slots**
  (`EditModeActivity.kt`) — the edit-mode toolbar shape, different from Tomato's nav-bar use.
- **A Wear module** applying the same `GoogleSansFlex` variable font against
  `androidx.wear.compose.material3.Typography`. The only Wear reference in the corpus.
- **"Poor man's shape morph"** — an animated corner-percent on press (`btnMorph`) across five files,
  for surfaces where `ButtonDefaults.shapes(...)` isn't available.
- **A custom tooltip `PopupPositionProvider`** (Above / Start placement).
- **Explicit `graphics-shapes` declaration** — the only app in the corpus that declares it rather
  than relying on material3's transitive dependency.

### What to copy

`MainFAB.kt` wholesale. The nested-`MaterialExpressiveTheme` incremental-adoption pattern. The
`ShortNavigationBar`/`WideNavigationRail` switch. The reusable connected-toggle-group helpers. The
Wear typography file if you have a Wear target.

### What not to copy

- **The app-wide theme is plain `MaterialTheme`.** Med is a model for *how to start*, not for where
  to end up. A new Expressive app should use `MaterialExpressiveTheme` at the root.
- **The architecture: Activity-per-screen, no navigation library at all** (a `selectedTab` state
  variable). Do not copy this.
- **It depends on `material3-adaptive-navigation-suite` but hand-rolls the nav switch** — it never
  uses `NavigationSuiteScaffold`. If you want the suite, look elsewhere (nobody in this corpus uses
  it).
- **It declares `graphics-shapes` but never uses `Morph`** and never uses `MaterialShapes` at all.
- **It has one `MaterialTheme.motionScheme` import and no usage.** Med is not a motion reference.

---

## 5. PixelMusicApp — no source code

**https://github.com/ianshulyadav/PixelMusicApp**

**This repository contains no Kotlin source. It is documentation and assets only.** Verified:
`find … -name "*.kt"` returns **zero files**. Repository contents in full: `README.md`,
`CHANGELOG.md`, `DISCLAIMER.md`, `LICENSE`, `PROVENANCE.md`, `SECURITY.md`,
`THIRD_PARTY_NOTICES.md`, and `assets/`. Single commit (`ec0432e "Add files via upload"`), one
branch. The README declares a **proprietary license** — the app is closed-source and only
screenshots and marketing material are published.

**Do not cite it as a code reference. Do not attempt to derive patterns from it.** If a user points
at PixelMusicApp screenshots and asks to replicate the look, say plainly that the repo has no code,
and build from the patterns in the other four apps instead.

---

## 6. jetpacker — Google's AI travel sample (`android/ai-samples`)

**https://github.com/android/ai-samples/tree/main/jetpacker**

**What it is.** A Google-authored (Android DevRel) AI trip-planning app. Copyright headers read
"Copyright 2026 The Android Open Source Project". 103 Kotlin files, ~19,500 LOC, **19 Gradle modules**
in a nested feature graph (`:feature:trip:itinerary:enrichment` is depth 4). Hilt + Room + Firebase AI.

**Version pinned.** `material3 = "1.5.0-alpha16"` declared as an **inline literal in the version
catalog**, overriding `compose-bom 2026.03.00`. AGP 9.2.1, Kotlin 2.3.10, `nav3Core = 1.1.3`,
compileSdk 37, minSdk 26. `androidx.graphics:graphics-shapes` is **never declared** — it arrives
transitively through material3.

### Read this before you cite it

**jetpacker is a modern-Compose / bleeding-edge-AndroidX sample first, and an M3 Expressive sample
only in narrow spots.** Specifically:

- **It does NOT use `MaterialExpressiveTheme`.** Plain `MaterialTheme(colorScheme, typography, shapes)`.
- **It does NOT use `MotionScheme`** — zero occurrences of `MotionScheme`, `MaterialTheme.motionScheme`,
  `MotionScheme.expressive()`, `LocalMotionScheme`. No motion-scheme-derived spec anywhere.
- **Zero `expressiveLightColorScheme`/`expressiveDarkColorScheme`, zero dynamic color.** Hand-authored
  light/dark schemes only.
- **Zero Expressive components.** No `ButtonGroup`, `FloatingToolbar` (either axis), `LoadingIndicator`,
  `ContainedLoadingIndicator`, `Linear`/`CircularWavyProgressIndicator`, `SplitButtonLayout`, `ToggleButton`,
  `ShortNavigationBar`, `WideNavigationRail`, `NavigationSuiteScaffold`, Carousel, `VerticalDragHandle`,
  `PullToRefresh`, `SearchBar`, `Slider`, `TimePicker`, `Snackbar`/`SnackbarHost`.
- It reads as Expressive because of its **shape scale** — `Shapes(small = 8.dp, medium = 24.dp,
  large = 32.dp, extraLarge = 48.dp)` vs M3's 4/8/12/16/28 — plus a variable-font family. The look is
  achieved without a single Expressive API.

So: **jetpacker provides no evidence for how Google wires `MaterialExpressiveTheme` or `MotionScheme`.**
Anything this plugin says about those remains canonical-form after reading it. For that, go to
androidify (§7).

### What it demonstrates best

- **`Modifier.animateBounds` + `LookaheadScope` — the only real call site in any sampled repo except
  androidify.** `feature/trip/.../TripScreen.kt`: `LookaheadScope` at :161, `animateBounds` at :173,
  animating a toolbar that gets passively repositioned as a conditional FAB enters/exits. Note
  `widthIn(max = 272.dp)` comes **before** `animateBounds`, and no `boundsTransform` is supplied.
  (The third grep hit, `ItineraryScreen.kt:29`, is an **unused import** — not a call site.)
- **`derivedMediaQuery` instead of `WindowSizeClass`, entirely.** Two breakpoints (600dp/1200dp),
  `ComposeUiFlags.isMediaQueryIntegrationEnabled = true` in `Application.onCreate` and re-set in every
  preview and screenshot test, tested via
  `CompositionLocalProvider(LocalUiMediaScope provides MockUiMediaScope(...))`. Experimental, and the
  only worked example of it anywhere.
- **Screenshot testing — fills a real gap.** `com.android.compose.screenshot` in 9 modules,
  `@PreviewTest @Preview @Composable`, 17 committed reference PNGs under
  `src/screenshotTestDebug/reference/...`, `screenshotTests { imageDifferenceThreshold = 0.05f }`, CI
  wired. No Paparazzi, no Roborazzi.
- **Navigation3, the simple end.** `sealed interface Screen : NavKey` with 15 `@Serializable` keys, a
  `Navigator` wrapper class, `typealias NavigationState = NavBackStack<NavKey>`,
  `remember(navigator) { entryProvider<NavKey> { … } }`, and a **three-argument** `NavDisplay`.
- **`dropShadow` with `ShadowScope`** — 12 sites: stacked for elevation, `radius=0/spread=0` for
  neo-brutalist sticker shadows, `brush = <animated gradient>` for an AI glow.
- **AI / chat UI, four archetypes** — classic bubbles, token streaming into a card, structured
  generative input, and a full-screen voice overlay with an 8-bar equalizer.
- **Testable ViewModel structure with no mocking library.** `open class` ViewModels overridden in
  tests, paired stateful/stateless screen composables so previews and screenshot tests take plain
  data. Fakes are `object : Interface { }`. No Mockito, no MockK, no Hilt test components.
- `MaterialShapes` two ways (`.toShape()` for background/clip; `toPath()` + `Matrix.scale(size)` +
  `drawWithCache` when you need the path), and one `Morph` shape-morphing loader.
- `TextAutoSize.StepBased(min, max)` + `maxLines = 1`; `FlexBox` + `Modifier.flex { grow(1f) }`.

### What to copy

The `animateBounds` recipe wholesale. The BOM-plus-inline-pin idiom. The entire `screenshotTest`
setup. The `Navigator` wrapper + `remember(navigator) { entryProvider }`. The `open class` ViewModel /
stateless-screen-overload testability strategy. The `dropShadow` patterns. The multipreview matrix
(phone light / phone dark / **phone at fontScale 1.5** / tablet light / tablet dark).

### What NOT to copy

- **Not a theming or motion reference.** See above. Do not present its `Theme.kt` as a target state.
- **It hand-rolls four components that M3 already provides** — `JetPackerToolbar` (~130 lines
  replacing `HorizontalFloatingToolbar`/`ShortNavigationBar`), `JetPackerFab` (which *imports*
  `FloatingActionButton`, `FloatingActionButtonDefaults` and `ripple` and uses none of them),
  `JetPackerExtendedFloatingActionButton`, and a `SectionHeader`. That is defensible for *this* app —
  it has a strong non-Material brand — and the implied rule is worth keeping (*stock M3 for structure
  and input; custom only for the 3-4 components carrying brand identity*), but the hand-rolls
  themselves are not recommendations.
- **Zero `Modifier.semantics`, zero `testTag`, zero `contentDescription` discipline** in the custom
  components: `JetPackerFab` and `JetPackerToolbarAction` use a bare `Modifier.clickable { }` — no
  ripple, no `indication`, no `role`, no minimum-touch-target enforcement.
- **Zero `stringResource`.** 12 strings exist in `strings.xml`; **none are referenced.** Everything is
  hardcoded in Kotlin, no `values-<locale>` directories. Do not model i18n on this.
- **Zero `derivedStateOf`, `rememberSaveable`, `snapshotFlow`, `produceState`, `rememberUpdatedState`,
  `@Stable`/`@Immutable`, `stateIn`.** No repository layer — DAOs are injected straight into ViewModels.
- **Zero `SharedTransitionLayout` / `sharedBounds` / `sharedElement`.** It contributes `animateBounds`
  and nothing on shared elements.
- **14 defects were catalogued in this sample; it should not be copied uncritically.** The
  load-bearing one for this plugin: `ItineraryScreen.kt:670` puts **`.clip(CircleShape)` before
  `.background(color, MaterialShapes.Burst.toShape())`, which nullifies the shape** — the clip wins
  and you get a circle, not a Burst. That is the exact modifier-order bug the shapes skill warns
  about, shipped in an official sample. Others: two malformed hex literals in `Color.kt`
  (`0xFFF1F2F3F`, `0xFFF47549FDC2B1`); the caller's `modifier` applied twice in `TripCard.kt:161-168`;
  a dead `Screen.ManageExpenses` route; `:data:trips → :core:ui` layering violation; shimmer
  implemented three times and never extracted; duplicate top-level colour vals and `InputBar`
  composables across two chat screens, both hardcoding slate colours instead of theme roles;
  `:core:ui` — the design system — has **no** screenshot tests; unused imports in five files.

---

## 7. androidify — the heaviest real Expressive app (`android/androidify`)

**https://github.com/android/androidify**

**What it is.** Google's Androidify showcase app — the one demoed for M3 Expressive. Multi-module,
`com.android.developers.androidify`. **34 files opt into `ExperimentalMaterial3ExpressiveApi`**, the
highest count of any Google-authored source and higher than any community app except vivi-music.

**Version pinned.** `material3 = 1.5.0-alpha20`, `composeBom = 2026.05.01`. Git HEAD `931cfdd68227`
(2026-06-02).

### What it demonstrates best

- **`MotionScheme`-driven shared elements — nobody else does this.** 29 `motionScheme` hits.
  `core/theme/.../Motion.kt` defines an **extension property on `MotionScheme` that yields a
  `BoundsTransform`**:
  `val MotionScheme.sharedElementTransitionSpec: BoundsTransform @Composable get() = BoundsTransform { _, _ -> slowSpatialSpec() }`.
  That single idiom is how every shared-element and bounds animation in the app inherits the theme's
  motion instead of a hardcoded tween. This is the highest-value thing in the app.
- **`animateBounds` guarded by a nullable `LookaheadScope` CompositionLocal** —
  `compositionLocalOf<LookaheadScope?> { null }` plus a `Modifier.safeAnimateBounds()` that degrades
  to `this` when the scope is absent, so a component works inside *or* outside a lookahead scope.
  2 `animateBounds` sites.
- **`VerticalFloatingToolbar` in a real app** (12 in androidx samples, **2 here**, zero everywhere
  else). `feature/results/.../ToolSelector.kt` hoists one `val buttons = @Composable { … }` and reuses
  it across `HorizontalFloatingToolbar` and `VerticalFloatingToolbar`, making orientation a pure
  layout switch with no duplicated item code.
- **Shape-morphing shared elements** — a `MorphOverlayClip : SharedTransitionScope.OverlayClip` driving
  the overlay clip path from a `Morph`, plus `sharedBoundsRevealWithShapeMorph`. Genuinely distinct
  from the `MorphPolygonShape` + `clip()` pattern the community corpus uses.
- **Broad Expressive component usage**: `ToggleButton` ×18, `ButtonGroup` ×6, `MaterialShapes` ×14,
  `MaterialExpressiveTheme` ×2, `HorizontalFloatingToolbar` ×4, `LoadingIndicator` ×2.
- **Testing Expressive UI**: `androidx.compose.ui.test.junit4.v2.createAndroidComposeRule` (the **v2**
  rule) plus `CompositionLocalProvider(LocalInspectionMode provides true)` wrapped around the content
  under test — which suppresses infinite/expressive animations that would otherwise never idle and
  would hang `waitForIdle`. That trick is not in the community corpus and it is the answer to "my
  Expressive test times out".

### What to copy

`Motion.kt` wholesale. The `LocalAnimateBoundsScope` + `safeAnimateBounds()` pattern. The
one-`buttons`-lambda-two-orientations toolbar structure. The `LocalInspectionMode provides true` test
wrapper.

### What NOT to copy

- **The `SharedElementsConfig.kt` twin of `sharedElementTransitionSpec` — it is a bug.** The app
  contains two same-named things with different behaviour: the `Motion.kt` **property** honours
  `motionScheme.slowSpatialSpec()`, while `SharedElementsConfig.kt:92`'s **generic function**
  `fun <T> MotionScheme.sharedElementTransitionSpec(): FiniteAnimationSpec<T> = tween(600)` ignores the
  receiver entirely and hardcodes a tween. Because `sharedElementTransitionBounds` delegates to the
  *function*, everything defaulting to it silently gets 600ms of tween and no motion-scheme physics.
  **Copy the property; treat the function as the "don't do this" example** — it is a
  motion-scheme-shaped API that does not read the motion scheme, in Google's own showcase.
- Its `safeAnimateBounds()` picks `slowEffectsSpec<Rect>()`. Bounds are *spatial*;
  `slowSpatialSpec<Rect>()` is token-correct and is what `Motion.kt` uses.
- It still writes `@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)` for `FloatingToolbar`,
  which graduated at alpha22 — consistent with its alpha20 pin, redundant on a current one.

---

## 8. androidx/androidx `material3/samples` — the canonical source

**`compose/material3/material3/samples/src/main/java/androidx/compose/material3/samples/`**

**What it is.** The `@Sampled` functions that androidx embeds into the API reference documentation.
48 sample files, written and maintained by the material3 team alongside the components themselves.
**This is the single highest-authority source available and the right answer to any signature
question.** It contains a canonical minimal example for every API on this plugin's wishlist —
including every one that returns zero across every app, official or community.

Read at androidx-main `360e8cba7ae6` (2026-08-14), post-`1.5.0-alpha26`.

### Best for

`AppBarRow` / `AppBarColumn` (5/2) · `SearchBarState` / `ExpandedFullScreenSearchBar` (8/2) ·
`ModalWideNavigationRail` (5) · `WideNavigationRail` (46) · `ShortNavigationBar` (10) ·
`VerticalFloatingToolbar` (12) · `HorizontalCenteredHeroCarousel` (3) · the official
`TimePickerDialog` (17) · `FloatingActionButtonMenu` (5) · `SplitButtonLayout` (13) ·
`MaterialShapes` (38) · `ButtonGroup` (32) · wavy progress indicators (6+).

### What it settles that nothing else could

- **Opt-in reality.** Measured per-file across the samples module: 37 × `ExperimentalMaterial3ExpressiveApi`
  vs 109 × `ExperimentalMaterial3Api`. `ProgressIndicatorSamples.kt` (wavy) and `ThemeSamples.kt`
  (`MaterialExpressiveTheme`) carry **zero** of either. Floating toolbars, nav rails, search bars,
  carousels, split buttons, FAB menus and flexible app bars have all graduated. **Still Expressive-gated:**
  `ButtonGroup` (**contested** — the alpha22 release note says "Promote `ButtonGroup` APIs to stable",
  but the census still shows 5 Expressive opt-ins in `ButtonGroupSamples.kt`; keep the opt-in and let
  the compiler decide at your pin), `LoadingIndicator`/`ContainedLoadingIndicator`, `MaterialShapes`, the expressive
  Menu/`ExposedDropdownMenu` APIs, `PullToRefresh`, and the `ToggleButton` **size** variants only
  (`XSmall`/`Medium`/`Large` — the base `ToggleButton`, `ElevatedToggleButton`,
  `FilledTonalToggleButton`, `OutlinedToggleButton` samples carry no opt-in). **Stop presenting the
  Expressive opt-in as blanket-required.**
- **`TimePickerDialog` is a real material3 API now** — plus `TimePickerDialogDefaults` (`.Title`,
  `.DisplayModeToggle`, `.MinHeightForTimePicker`), `TimePickerDisplayMode`, and `RichTimePickerDialog`.
  Anyone hand-rolling one from `AlertDialog` (as android/snippets still does, and as
  developer.android.com therefore still renders) is working from outdated guidance and also loses
  `state.isInputValid` gating on the confirm button.
- **a11y rules stated inline by Google** that no app in the corpus captures: declare a floating
  toolbar **before** the scrolling content and give it `.zIndex(1f)` (focus order vs paint order); a
  nav-rail header toggle **must** have a tooltip and **must** set `stateDescription`; drive rail
  visual state from `state.targetValue` and settled state from `state.currentValue`.

### What NOT to copy

These are **minimal demonstrations**, not architecture. They use local `var selectedItem by
rememberSaveable`, hardcoded `listOf("Home", "Search", "Settings")`, and `"Localized description"`
placeholder strings. Take the API shape and the inline comments; take nothing about state ownership.

---

## 9. Jetcaster — the only compose-sample that adopted Expressive

**https://github.com/android/compose-samples/tree/main/Jetcaster**

**What it is.** The podcast sample in `android/compose-samples`, and **the only one of the six that
adopted M3 Expressive**. It additionally pins `androidx-material3 = 1.5.0-alpha22` over the repo-wide
BOM `2026.08.00`.

**Best for.** A minimal, honest `MaterialExpressiveTheme` adoption in a Google sample:

```kotlin
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
fun JetcasterTheme(dynamicColor: Boolean = false, content: @Composable () -> Unit) {
    MaterialExpressiveTheme(
        motionScheme = MotionScheme.expressive(),
```

Other Expressive surfaces (bodies `[UNVERIFIED]` beyond greps): `util/Buttons.kt:43` `IconToggleButton`
+ `IconToggleButtonColors` + `IconToggleButtonShapes`; `ui/player/PlayerScreen.kt:722` `ToggleButton` +
`ToggleButtonColors` + `ToggleButtonShapes`, `:754` `ButtonGroup`; `ui/home/Home.kt:446`
`HorizontalFloatingToolbar` + `FloatingToolbarColors`. Also the **only** source using `maskClip` /
`maskBorder` — ordered `.maskClip(...)` **then** `.clickable {}` so the ripple is clipped to the
carousel mask.

**What NOT to copy.** Its Expressive surface is **narrow** — theme + a handful of components. It is
not a motion reference and not a shapes reference. Do not extrapolate "compose-samples uses
Expressive" from it (see §10).

---

## 10. Blunt negatives — repos that look promising and are not

Checked 2026-08-14, all with zero `ExperimentalMaterial3ExpressiveApi` files:

- **`android/compose-samples` apart from Jetcaster.** Jetsnack, Reply, JetNews, Jetchat and JetLagged
  are all on BOM `2026.08.00` and all use **classic M3**. Do not treat compose-samples as an
  Expressive corpus. Two of them are still worth reading for **predictive back**: JetLagged's
  `PredictiveBackHandler` + `VelocityTracker` drawer (`JetLaggedDrawer.kt:98-118`) and JetNews's
  `NavigationBackHandler` + `rememberNavigationEventState` + edge-aware `predictivePopTransitionSpec`
  (`JetnewsNavDisplay.kt`). Both are in `motion-recipes.md` §10c/§10d.
- **`android/nowinandroid`** — **zero** M3 Expressive. Last touched 2026-04-30. Its `ToggleButton`
  hits are classic `IconToggleButton`/`FilterChip`-adjacent M3, and its `LoadingIndicator` is a
  *local custom composable*, not `androidx.compose.material3.LoadingIndicator`. Do not mine it.
- **`android/platform-samples`** — **zero**. Pinned to stable `material3 1.4.0`, classic M3 only.
- **`android/snippets` — zero, and this is the important negative** because it is the repo that
  renders into developer.android.com. **Its apparent `ButtonGroup` / `ToggleButton` hits are
  `androidx.xr.glimmer`, not material3 — an easy and costly false positive.** Its only `MotionScheme`
  is `androidx.wear.compose.material3.MotionScheme` (Wear). Its Carousel and TimePicker snippets are
  `ExperimentalMaterial3Api`, not Expressive, and its TimePicker snippet is outdated (§8). If a search
  surfaces "ButtonGroup in android/snippets", check the import before believing it.

---

## 11. Which source to look at for X

**First rule: for a signature, go to the androidx samples (§8). For everything else, this table.**

| Looking for | Go to | Where |
| --- | --- | --- |
| **Any API signature, settled** | **androidx `material3/samples`** | `compose/material3/material3/samples/.../<Component>Samples.kt` |
| `Modifier.animateBounds` + `LookaheadScope` | **jetpacker**, then **androidify** | `feature/trip/.../TripScreen.kt:161,173`; `feature/results/.../ImageRenderer.kt:192` |
| `MotionScheme` → `BoundsTransform` for shared elements | **androidify** | `core/theme/.../Motion.kt` (full file) |
| `animateBounds` usable inside *or* outside a lookahead scope | **androidify** | `core/theme/.../SharedElementsConfig.kt:80`, `ImageRenderer.kt:192` |
| Shape-morphing shared element (`MorphOverlayClip`) | **androidify** | `core/theme/.../SharedElementsConfig.kt:145` |
| `VerticalFloatingToolbar` | **androidx samples**, then **androidify** | `FloatingToolbarSamples.kt:348`; `feature/results/.../ToolSelector.kt` |
| `ModalWideNavigationRail` | **androidx samples** | `NavigationRailSamples.kt:183` (+ dismissible variant at 297) |
| `AppBarRow` / `AppBarColumn` | **androidx samples** | `AppBarSamples.kt:190` |
| `SearchBarState` / `ExpandedFullScreenSearchBar` / `TopSearchBar` | **androidx samples** | `SearchBarSamples.kt` |
| `HorizontalCenteredHeroCarousel`, `maskClip` / `maskBorder` | **androidx samples**, then **Jetcaster** | `CarouselSamples.kt`; Jetcaster carousel |
| Official `TimePickerDialog` (stop hand-rolling it) | **androidx samples** | `TimePickerSamples.kt` |
| Which APIs still need the Expressive opt-in | **androidx samples** | per-file census in §8 |
| `derivedMediaQuery` instead of `WindowSizeClass` | **jetpacker** | `feature/home/.../HomeScreen.kt`, `JetPackerApplication.kt`, `HomeScreenshotTest.kt` |
| Screenshot testing an Expressive UI | **jetpacker** | `src/screenshotTest/...` in 9 modules |
| Suppressing infinite animations so tests can idle | **androidify** | `feature/home/src/androidTest/.../HomeScreenTest.kt` (`LocalInspectionMode provides true`) |
| Navigation3, minimal — `Navigator` wrapper, 3-arg `NavDisplay` | **jetpacker** | `ui/navigation/{NavigationState,Navigator,NavGraph}.kt` |
| `PredictiveBackHandler` with velocity + cancel | **JetLagged** (compose-samples) | `JetLaggedDrawer.kt:98` |
| `NavigationBackHandler` / edge-aware predictive pop on nav3 | **JetNews** (compose-samples) | `ui/JetnewsNavDisplay.kt` |
| `MaterialExpressiveTheme` in a Google sample | **Jetcaster** | `ui/theme/Theme.kt:469` |
| `dropShadow` / `ShadowScope` | **jetpacker** | `:core:ui`, 12 sites |
| Streaming-AI / chat surfaces | **jetpacker** | `§10` archetypes A–D |
| `MotionScheme` / spring specs / spatial vs effects in practice | **Tomato** | `timerScreen/TimerScreen.kt`, `settingsScreen/components/SliderListItem.kt` |
| Reduced motion / respecting system animation settings | **LastChat** | `ui/motion/MotionPolicy.kt` |
| `MaterialExpressiveTheme` + dynamic color + AMOLED, full app | **vivi-music** | `ui/theme/Theme.kt`, `MainActivity.kt` |
| `MaterialExpressiveTheme` in Compose Multiplatform (`expect`/`actual`) | **Tomato** | `shared/src/androidMain/.../ui/theme/Theme.android.kt` |
| Adopting Expressive incrementally on one subtree | **Med** | `elements/MainActivity/MainFAB.kt` (lines 52-64) |
| Connected button groups (any layout) | **vivi-music** | 12 files; start with `ui/screens/equalizer/axion/AxionEqScreen.kt`, `ui/player/Queue.kt`, `ui/screens/artist/ArtistScreen.kt` |
| `ButtonGroup` composable with overflow + `animateWidth` | **Tomato**, then **vivi-music** | `TimerScreen.kt:448`, `AlarmSettings.kt:350`; `ui/screens/AlbumScreen.kt:492` |
| `ToggleButtonDefaults.shapes()` with custom/asymmetric corners | **Med** | `SettingsActivity.kt` |
| `OutlinedToggleButton` / `outlinedToggleButtonColors` (alpha26: `OutlinedToggleButtonDefaults.colors`) | **Med** | `elements/MainActivity/MedicineBottomSheet.kt` |
| `SplitButtonLayout` | **vivi-music** | `ui/component/SortHeader.kt` (only real usage anywhere) |
| FAB menu (`FloatingActionButtonMenu`, `ToggleFloatingActionButton`, `animateFloatingActionButton`) | **Med** | `elements/MainActivity/MainFAB.kt` (only real usage anywhere) |
| `HorizontalFloatingToolbar` as bottom navigation | **Tomato** | `androidApp/.../ui/AppScreen.kt` |
| `HorizontalFloatingToolbar` with leading/trailing slots | **Med** | `EditModeActivity.kt` |
| `floatingToolbarVerticalNestedScroll` / `ScreenOffset` | **LastChat** | `ui/pages/setting/SettingProviderDetailPage.kt`, `SettingTTSProviderDetailPage.kt` |
| `ShortNavigationBar` / `WideNavigationRail` adaptive switch | **Med** | `services/MedApp.kt` (only real usage anywhere) |
| `LargeFlexibleTopAppBar` + `subtitle` | **Tomato** | `settingsScreen/screens/{AlarmSettings,AboutScreen,TimerSettings,AppearanceSettings}.kt` |
| `TopAppBar(subtitle =, titleHorizontalAlignment =)` | **Tomato** | `timerScreen/TimerScreen.kt` |
| `MaterialShapes` + `.toShape()` in production UI | **vivi-music** | `ui/component/Items.kt`, `ui/player/Thumbnail.kt`, `ui/screens/settings/AboutScreen.kt` |
| `MaterialShapes` as icon backgrounds | **Tomato** | `settingsScreen/DetailPlaceholder.kt`, `settingsScreen/screens/AboutScreen.kt` |
| `androidx.graphics.shapes.Morph` → animated `GenericShape` | **LastChat** | `ui/hooks/AvatarShape.kt` (only real usage anywhere) |
| Shape morph on press via `ButtonDefaults.shapes` / `IconButtonDefaults.shapes` | **vivi-music** | `WelcomeActivity.kt` |
| Hand-rolled press morph (animated corner percent) | **Med**, **vivi-music** | `elements/EditModeActivity/SavePopup.kt`; `utils/ShapesCurve.kt`, `ui/utils/ShapeUtils.kt` |
| `SegmentedListItem` / `ListItemDefaults.segmentedShapes` / `segmentedColors` | **Tomato** | `shared/src/commonMain/.../ui/theme/{Shape,Color}.kt` and settings screens |
| Grouped-list corner shapes, hand-rolled | **vivi-music** | see the grouped-list idiom in the components skill |
| `LinearWavyProgressIndicator` | **LastChat** (6 sites), **vivi-music** (seekbar) | `ui/components/ai/McpPicker.kt`; `ui/component/WavySlider.kt` |
| `CircularWavyProgressIndicator`, determinate + custom stroke | **vivi-music**, **Tomato** | `ui/component/Lyrics.kt`, `ui/player/Player.kt`; `timerScreen/TimerScreen.kt` |
| `LoadingIndicator` / `ContainedLoadingIndicator` | **vivi-music** (8 files), **Tomato** | `ui/player/Player.kt`, `ui/screens/HomeScreen.kt` |
| `PullToRefreshDefaults.LoadingIndicator` | **Med** | `services/MedApp.kt` |
| Custom expressive sliders (wavy / squiggly / volume / player track) | **vivi-music** | `ui/component/{WavySlider,SquigglySlider,VolumeSlider,PlayerSlider}.kt` |
| `SharedTransitionLayout` / `sharedBounds` / reusable reveal modifier | **Tomato** | `statsScreen/components/sharedBoundsReveal.kt`, `statsScreen/StatsScreen.kt` |
| `veilOut` / `unveilIn` | **Tomato** | `statsScreen/StatsScreen.kt` |
| Navigation3 (`NavDisplay`, `entryProvider`, `ListDetailSceneStrategy`) | **Tomato** | `ui/Screen.kt`, `settingsScreen/SettingsScreen.kt`, `androidApp/.../ui/AppScreen.kt` |
| `SupportingPaneScaffold` + `VerticalDragHandle` + pane expansion | **Tomato** | `timerScreen/TimerScreen.kt` |
| Hand-rolled adaptive list/detail (no pane scaffold) | **LastChat** | `ui/pages/setting/SettingsAdaptiveScaffold.kt` |
| Variable fonts / `FontVariation` (most complete) | **LastChat**, then **vivi-music** | `ui/theme/Type.kt`; `ui/theme/Type.kt` (48 sites) |
| `fontFeatureSettings` for Google Sans Flex alternates | **Tomato** | `shared/src/commonMain/.../ui/theme/Type.kt` |
| Wear OS Expressive typography | **Med** | `wear/src/main/kotlin/.../ui/theme/Type.kt` |
| materialkolor seed-color / palette pickers | **vivi-music**, **Tomato** | `ui/screens/settings/ThemeScreen.kt`; `settingsScreen/components/ColorSchemePickerListItem.kt` |
| AMOLED / pure-black plumbing | **vivi-music** (manual copy), **Tomato** (`isAmoled = true`) | `ui/theme/Theme.kt` in both |
| Global gradle opt-in, done cleanly | **LastChat** | `app/build.gradle.kts` |
| File-level `@file:OptIn` strategy | **Med** | `services/MedApp.kt:1` and 37 others |

---

## 12. APIs with zero real-world examples

Grepped across the four community repos. **No usable precedent exists in the community corpus** for:

`MediumFlexibleTopAppBar` · `FlexibleBottomAppBar` · `AppBarRow` / `AppBarColumn` ·
`ExpandedFullScreenSearchBar` · `ModalWideNavigationRail` · `VerticalFloatingToolbar` ·
`NavigationSuiteScaffold` · `ListDetailPaneScaffold` · `ShapeDefaults` (referenced only in
vivi-music's `GridMenu.kt`) · `Modifier.animateBounds` · explicit `LookaheadScope` · direct
`RoundedPolygon` construction · `expressiveLightColorScheme` (nobody uses it —
all four generate schemes some other way).

**Most of that list is now resolved by the official sources.** Updated status:

| API | Community corpus | Now covered by |
| --- | --- | --- |
| `Modifier.animateBounds` / `LookaheadScope` | zero | **jetpacker** (1 call site), **androidify** (2) — §6, §7 |
| `ModalWideNavigationRail` | zero | **androidx samples** `NavigationRailSamples.kt:183` — §8 |
| `VerticalFloatingToolbar` | zero | **androidx samples** (12) + **androidify** (2) — §7, §8 |
| `AppBarRow` / `AppBarColumn` | zero | **androidx samples** `AppBarSamples.kt:190` — §8 |
| `ExpandedFullScreenSearchBar` / `SearchBarState` | zero | **androidx samples** `SearchBarSamples.kt` — §8 |
| `MediumFlexibleTopAppBar` / `FlexibleBottomAppBar` | zero | **androidx samples** `AppBarSamples.kt` — §8 |
| `HorizontalCenteredHeroCarousel` | zero | **androidx samples** `CarouselSamples.kt` — §8 |
| Official `TimePickerDialog` | zero (hand-rolled) | **androidx samples** (17) — §8 |
| `expressiveLightColorScheme` | zero | **androidx samples** `ThemeSamples.kt` — §8 |
| `RoundedPolygon` constructed directly | zero | **androidify** `SharedElementsConfig.kt` (`RoundedPolygon.rectangle()`, `.circle()`, `.normalized()`) |

**Still zero everywhere, official sources included:** `NavigationSuiteScaffold` (Med declares the
artifact and never calls it; no Google sample uses it either) and `ListDetailPaneScaffold` outside
Tomato/vivi-music. For those two, work from the API signatures in the components/navigation skills and
say plainly that the pattern is not corroborated by a shipping app.

**Two things that are *not* in that list, because "no examples yet" is the wrong diagnosis for them:**

- **`shapeByInteraction` — does not exist as public API. Do not write it.** It is internal to
  `Button` / `ToggleButton`, which apply the interaction-driven morph themselves from the
  `ButtonShapes` you hand them. There is no signature to work from and nothing to call. **Use the
  `shapes =` parameter instead** — `shapes = ButtonDefaults.shapes()` (note: `shape =` singular is a
  different overload and gives you a static container). When a component has no `shapes =`, hand-roll
  with `interactionSource.collectIsPressedAsState()`. See `buttons.md` §"Pitfalls" and
  `morph-recipes.md` §3.
- **The `SplitButtonLayout` → `SplitButton` rename: RETRACTED, it does not exist.** An earlier
  version of this file marked this question UNVERIFIED and then wrongly flipped it to CONFIRMED on
  the strength of the alpha25 release note (Ic9840, *"Deprecated `SplitButtonLayout` Api"*). Reading
  the API surface reverses that. Verified in `compose/material3/material3/api/current.txt` at
  androidx HEAD `360e8cba`, 2026-08-14 (post-alpha26): the only top-level split-button composable is
  `SplitButtonLayout(leadingButton, trailingButton, modifier, spacing)`, it carries **no
  `@Deprecated` annotation**, and **no `SplitButton` composable exists** — zero matches for a
  top-level `SplitButton(` in any api txt file. All 13 androidx samples/source call sites use
  `SplitButtonLayout`, so read them as current, not legacy. What *is* deprecated is
  `SplitButtonDefaults.leadingButtonShapes(CornerSize)` / `trailingButtonShapes(CornerSize)`, in
  favor of `*ShapesFor(buttonHeight: Dp)` — most plausibly what the release note meant
  (**inference, not fact**). Guidance: **write `SplitButtonLayout` on every pin.** Community code
  (vivi-music, on alpha23) already has it right. No experimental opt-in is needed (the string
  "Experimental" does not occur in `SplitButton.kt`).

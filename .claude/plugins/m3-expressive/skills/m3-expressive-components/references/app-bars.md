# M3 Expressive App Bars & Search Bars

Top app bars, the flexible variants, subtitles, bottom app bars, overflow-aware action containers,
and the search-bar family.

Confidence markers used below:
- **[verified]** — signature or value read from material3 source / API listing.
- **[from-corpus]** — verbatim from a shipping app in the reference set (Tomato, Med, LastChat,
  vivi-music). Paths given above each excerpt.
- **[canonical-form]** — the API shape is right and this is how it is written, but the exact
  signature was not readable from source. Compile-check before trusting.
- **[judgment]** — practical guidance, not an API fact.

---

## 0. Sourcing note — read before trusting any web page on this topic

**developer.android.com's app-bars and search-bar guide pages are STALE and contain none of the
APIs in this file.** Verified by direct fetch on 2026-08-14:

- <https://developer.android.com/develop/ui/compose/components/app-bars> contains **no**
  `AppBarRow`, `AppBarColumn`, `MediumFlexibleTopAppBar`, `LargeFlexibleTopAppBar` or
  `TwoRowsTopAppBar`. Only classic `TopAppBar` / `CenterAligned` / `Medium` / `Large` /
  `BottomAppBar`.
- <https://developer.android.com/develop/ui/compose/components/search-bar> contains **no**
  `SearchBarState`, `rememberSearchBarState`, `TopSearchBar`, `ExpandedFullScreenSearchBar` or
  `ExpandedDockedSearchBar`. Both of its examples use the old
  `var expanded by rememberSaveable` + `SearchBarDefaults.InputField(query = ...)` form.

The reason is structural: those pages render from `android/snippets`, which has **zero** M3
Expressive material3 usage at HEAD `2a8ec97edf8e`. Its `ButtonGroup` / `ToggleButton` hits are
`androidx.xr.glimmer`, not Material 3 — an easy false positive.

**Prefer the androidx `material3/samples` module** —
`compose/material3/material3/samples/src/main/java/androidx/compose/material3/samples/` — which
holds the `@Sampled` functions embedded into the API reference. It is the single highest-authority
source for this family, and the only place `AppBarRow`, `AppBarColumn`, `SearchBarState` and
`ExpandedFullScreenSearchBar` appear at all: all five Google sample repos (androidify,
compose-samples, snippets, nowinandroid, platform-samples) have **zero** occurrences of each.

---

## 1. Opt-in and version gates

| Family | Gate on 1.4.0 | Gate on 1.5.0-alpha26 |
| --- | --- | --- |
| `TopAppBar`, `MediumFlexibleTopAppBar`, `LargeFlexibleTopAppBar`, `TwoRowsTopAppBar`, `LargeTopAppBar` | `ExperimentalMaterial3Api` | none (graduated alpha23) |
| `FlexibleBottomAppBar`, `FlexibleContentPadding`, `FlexibleBottomAppBarHeight`, `FlexibleHorizontalArrangement`, `FlexibleFixedHorizontalArrangement` | `ExperimentalMaterial3Api` | none (graduated alpha23) |
| **`BottomAppBar`** and its associated methods | `ExperimentalMaterial3Api` | **none — promoted to stable in alpha26 (I42c61)** |
| `AppBarRow` / `AppBarColumn` / `AppBarMenuState` | `ExperimentalMaterial3Api` | none (graduated alpha23) |
| `TopAppBarScrollBehavior` + `TopAppBarDefaults` behaviors | `ExperimentalMaterial3Api` | none (stable alpha17, re-promoted alpha22) |
| `SearchBar`, `SearchBarState`, `ExpandedFullScreenSearchBar`, `ExpandedDockedSearchBar` | `ExperimentalMaterial3Api` | none (alpha23/24) |
| `AppBarWithSearch` | `ExperimentalMaterial3Api` | `ExperimentalMaterial3Api` — **re-gated in alpha24, still gated at alpha26** |

**[verified]** — from the material3 release-note graduation timeline, cross-checked against the
opt-in census of the androidx samples module at androidx-main `360e8cba7ae6`.

> **Opt-in correction (changed alpha22–alpha26).** An earlier revision of this file treated the
> Expressive opt-in as broadly required across this family. **It is not.** Measured directly from
> the canonical samples: `AppBarSamples.kt` carries **0** `ExperimentalMaterial3ExpressiveApi` and
> 2 `ExperimentalMaterial3Api`; `SearchBarSamples.kt` likewise **0** and 2. The whole
> app-bar / flexible-bar / search family has graduated off the Expressive annotation. What remains
> is the ordinary `@OptIn(ExperimentalMaterial3Api::class)` — and only for `AppBarWithSearch` and
> the still-experimental corners noted per-section below.
>
> On alpha26 you can also drop `@OptIn(ExperimentalMaterial3Api::class)` kept solely for
> **`BottomAppBar`**. A stale opt-in is a warning, not an error — unless you build with
> warnings-as-errors.

App bars use `ExperimentalMaterial3Api`, **not** `ExperimentalMaterial3ExpressiveApi`. This trips
people up: the flexible bars are Expressive features gated by the *non*-Expressive annotation.
Both Tomato and Med opt into both annotations at file level and stop worrying about it — harmless,
but on alpha26 the Expressive half of that line is dead weight for app bars:

```kotlin
@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
```

---

## 2. The family, and which are Expressive additions

| Composable | Status | Use for |
| --- | --- | --- |
| `TopAppBar` | baseline, **gained `subtitle` + `titleHorizontalAlignment`** | dense layouts, scrolled pages, any secondary screen |
| `CenterAlignedTopAppBar` | baseline | brand-centred screens; superseded by `TopAppBar(titleHorizontalAlignment = CenterHorizontally)` |
| `MediumTopAppBar` | baseline, **being replaced** | → `MediumFlexibleTopAppBar` |
| `LargeTopAppBar` | baseline, **being replaced** | → `LargeFlexibleTopAppBar` |
| `MediumFlexibleTopAppBar` | **Expressive addition** | larger headline that collapses into a small bar on scroll |
| `LargeFlexibleTopAppBar` | **Expressive addition** | emphasize the headline of the page — the hero bar |
| `TwoRowsTopAppBar` | **Expressive addition** (graduated alpha23) | the primitive the flexible bars are built on; use directly only when you need a custom two-row layout |
| `FlexibleBottomAppBar` | **Expressive addition** | bottom action bar with arrangement control |
| `AppBarRow` / `AppBarColumn` | **Expressive addition** | actions that overflow into a menu when they don't fit |

Google's guidance on the flexible variants **[verified — material-components-android TopAppBar.md]**:
they *"replace the deprecated medium and large variants"* and bring *"reduced overall height, larger
title text, subtitle, left- and center-aligned text options, text wrapping, more flexible elements
for imagery and filled buttons."*

Per-variant use **[verified — same doc]**:
- Small — *"Use in dense layouts or when a page is scrolled."*
- Medium flexible — *"Use to display a larger headline. It can collapse into a small app bar on scroll."*
- Large flexible — *"Use to emphasize the headline of the page."*

**[judgment]** On a new Expressive app: `LargeFlexibleTopAppBar` on top-level destinations you want
to give weight to, `TopAppBar` everywhere else. Skip `MediumFlexibleTopAppBar` unless you have a
specific reason — the medium/large distinction is subtle and two heights is one fewer thing to
maintain.

---

## 3. Signatures

### 3.1 `TopAppBar` — three overloads **[verified]**

```kotlin
// Deprecated (no contentPadding)
@Composable
fun TopAppBar(
    title: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    navigationIcon: @Composable () -> Unit = {},
    actions: @Composable RowScope.() -> Unit = {},
    expandedHeight: Dp = TopAppBarDefaults.TopAppBarExpandedHeight,
    windowInsets: WindowInsets = TopAppBarDefaults.windowInsets,
    colors: TopAppBarColors = TopAppBarDefaults.topAppBarColors(),
    scrollBehavior: TopAppBarScrollBehavior? = null,
)

// Current
@Composable
fun TopAppBar(
    title: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    navigationIcon: @Composable () -> Unit = {},
    actions: @Composable RowScope.() -> Unit = {},
    expandedHeight: Dp = TopAppBarDefaults.TopAppBarExpandedHeight,
    windowInsets: WindowInsets = TopAppBarDefaults.windowInsets,
    colors: TopAppBarColors = TopAppBarDefaults.topAppBarColors(),
    scrollBehavior: TopAppBarScrollBehavior? = null,
    contentPadding: PaddingValues = TopAppBarDefaults.ContentPadding,
)

// WITH SUBTITLE — the Expressive addition
@Composable
fun TopAppBar(
    title: @Composable () -> Unit,
    subtitle: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    navigationIcon: @Composable () -> Unit = {},
    actions: @Composable RowScope.() -> Unit = {},
    titleHorizontalAlignment: Alignment.Horizontal = Alignment.Start,
    expandedHeight: Dp = TopAppBarDefaults.TopAppBarExpandedHeight,
    windowInsets: WindowInsets = TopAppBarDefaults.windowInsets,
    colors: TopAppBarColors = TopAppBarDefaults.topAppBarColors(),
    scrollBehavior: TopAppBarScrollBehavior? = null,
    contentPadding: PaddingValues = TopAppBarDefaults.ContentPadding,
)
```

**Pitfall:** in the subtitle overload `subtitle` is **non-null and positional #2** — it is a
distinct overload, not an optional parameter. `titleHorizontalAlignment` only exists on that
overload (and on the flexible bars). So to centre a title on a plain `TopAppBar` you must pass a
subtitle slot — pass `subtitle = {}` if you don't want one. That is exactly what Tomato does
(§4.2).

### 3.2 `MediumFlexibleTopAppBar` / `LargeFlexibleTopAppBar` **[verified]**

```kotlin
@Composable
fun MediumFlexibleTopAppBar(
    title: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    subtitle: (@Composable () -> Unit)? = null,
    navigationIcon: @Composable () -> Unit = {},
    actions: @Composable RowScope.() -> Unit = {},
    titleHorizontalAlignment: Alignment.Horizontal = Alignment.Start,
    collapsedHeight: Dp = TopAppBarDefaults.MediumAppBarCollapsedHeight,
    expandedHeight: Dp = /* MediumFlexibleAppBarWithSubtitleExpandedHeight if subtitle != null
                            else MediumFlexibleAppBarWithoutSubtitleExpandedHeight */,
    windowInsets: WindowInsets = TopAppBarDefaults.windowInsets,
    colors: TopAppBarColors = TopAppBarDefaults.topAppBarColors(),
    scrollBehavior: TopAppBarScrollBehavior? = null,
)

@Composable
fun LargeFlexibleTopAppBar(
    title: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    subtitle: (@Composable () -> Unit)? = null,
    navigationIcon: @Composable () -> Unit = {},
    actions: @Composable RowScope.() -> Unit = {},
    titleHorizontalAlignment: Alignment.Horizontal = Alignment.Start,
    collapsedHeight: Dp = TopAppBarDefaults.LargeAppBarCollapsedHeight,
    expandedHeight: Dp = /* LargeFlexibleAppBarWith(out)SubtitleExpandedHeight */,
    windowInsets: WindowInsets = TopAppBarDefaults.windowInsets,
    colors: TopAppBarColors = TopAppBarDefaults.topAppBarColors(),
    scrollBehavior: TopAppBarScrollBehavior? = null,
)
```

Here `subtitle` **is** an optional named parameter (`(@Composable () -> Unit)? = null`) — opposite
of the `TopAppBar` situation.

`expandedHeight` auto-adjusts based on whether a subtitle is present; invalid heights are replaced
with Material defaults **[verified — KDoc]**. Token constants `TopAppBarDefaults.MediumAppBarCollapsedHeight`,
`TopAppBarDefaults.LargeAppBarCollapsedHeight`,
`MediumFlexibleAppBarWith(out)SubtitleExpandedHeight`,
`LargeFlexibleAppBarWith(out)SubtitleExpandedHeight` exist; their dp values are **UNVERIFIED**.
Do not hardcode heights — use the defaults.

### 3.3 `CenterAlignedTopAppBar`, `MediumTopAppBar`, `LargeTopAppBar`, `TwoRowsTopAppBar`

**[canonical-form]** — exact signatures not readable from source in this pass. `CenterAlignedTopAppBar`
mirrors the current `TopAppBar` overload (title / navigationIcon / actions / expandedHeight /
windowInsets / colors / scrollBehavior). `MediumTopAppBar` and `LargeTopAppBar` add
`collapsedHeight` + `expandedHeight` and take `TopAppBarDefaults.mediumTopAppBarColors()` /
`largeTopAppBarColors()`. `TwoRowsTopAppBar` is the two-row primitive underneath the flexible
bars and takes separate collapsed/expanded title slots. **Compile-check `TwoRowsTopAppBar` before
writing against it — write a flexible bar instead unless you specifically need the primitive.**

Real `LargeTopAppBar` call site — **[from-corpus]**
`/root/work/repos/Med/app/src/main/kotlin/com/fedeveloper95/med/SettingsActivity.kt` (lines 165-220):

```kotlin
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

    val appBarTypography = MaterialTheme.typography.copy(
        headlineMedium = MaterialTheme.typography.displaySmall.copy(
            fontFamily = GoogleSansFlex,
            fontWeight = FontWeight.Normal
        ),
        titleLarge = MaterialTheme.typography.titleLarge.copy(
            fontFamily = GoogleSansFlex,
            fontWeight = FontWeight.Normal
        )
    )

    Scaffold(
        topBar = {
            MaterialTheme(typography = appBarTypography) {
                LargeTopAppBar(
                    title = {
                        Text(
                            text = stringResource(R.string.settings_title),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    },
                    navigationIcon = { /* … */ },
                    scrollBehavior = scrollBehavior,
                    colors = TopAppBarDefaults.largeTopAppBarColors(
                        containerColor = MaterialTheme.colorScheme.background,
                        scrolledContainerColor = MaterialTheme.colorScheme.background,
                        titleContentColor = MaterialTheme.colorScheme.onBackground
                    )
                )
            }
        },
        containerColor = Color.Transparent,
        modifier = Modifier
            .widthIn(max = 700.dp)
            .fillMaxSize()
            .nestedScroll(scrollBehavior.nestedScrollConnection)
    ) { padding -> /* … */ }
```

Two techniques worth stealing here: **scoping a `MaterialTheme(typography = …)` around just the app
bar** to swap in a bigger display face without touching the global scale, and pinning
`containerColor`/`scrolledContainerColor` to the same value so the bar never flashes a tonal change
on scroll.

---

## 4. `subtitle` and `titleHorizontalAlignment` — the cheapest hero move

Subtitles are new across variants in Expressive and default to `colorOnSurfaceVariant`
**[verified — TopAppBar.md]**. A large flexible bar with a subtitle and an emphasized display face
is the least-effort legitimate hero moment on a screen: it costs two slots and no custom drawing.

### 4.1 `LargeFlexibleTopAppBar` with subtitle + hero variable font

**[from-corpus]**
`/root/work/repos/Tomato/shared/src/commonMain/kotlin/org/nsh07/pomodoro/ui/settingsScreen/screens/AppearanceSettings.kt`
(lines 90-140). Identical shape appears five times in Tomato: `AlarmSettings.kt:217`,
`AboutScreen.kt:137`, `TimerSettings.kt:224`, `AppearanceSettings.kt:108`,
`BackupRestoreScreen.kt:137`.

```kotlin
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfo
import androidx.window.core.layout.WindowSizeClass.Companion.WIDTH_DP_EXPANDED_LOWER_BOUND
import org.nsh07.pomodoro.ui.theme.CustomColors.detailPaneTopBarColors
import org.nsh07.pomodoro.ui.theme.CustomColors.topBarColors
import org.nsh07.pomodoro.ui.theme.LocalAppFonts
import org.nsh07.pomodoro.ui.theme.TomatoShapeDefaults.PANE_MAX_WIDTH
```

```kotlin
        .isWidthAtLeastBreakpoint(WIDTH_DP_EXPANDED_LOWER_BOUND)

    val barColors = if (widthExpanded) detailPaneTopBarColors
    else topBarColors

    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .fillMaxSize()
            .background(barColors.containerColor)
    ) {
        Scaffold(
            topBar = {
                LargeFlexibleTopAppBar(
                    title = {
                        Text(
                            stringResource(Res.string.appearance),
                            fontFamily = LocalAppFonts.current.topBarTitle
                        )
                    },
                    subtitle = {
                        Text(stringResource(Res.string.settings))
                    },
                    navigationIcon = {
                        if (!widthExpanded)
                            FilledTonalIconButton(
                                onClick = onBack,
                                shapes = IconButtonDefaults.shapes(),
                                colors = IconButtonDefaults.filledTonalIconButtonColors(
                                    containerColor = listItemColors.containerColor
                                )
                            ) {
                                Icon(
                                    painterResource(Res.drawable.arrow_back),
                                    stringResource(Res.string.back)
                                )
                            }
                    },
                    colors = barColors,
                    scrollBehavior = scrollBehavior
                )
            },
            containerColor = barColors.containerColor,
            modifier = modifier
                .widthIn(max = PANE_MAX_WIDTH)
                .nestedScroll(scrollBehavior.nestedScrollConnection)
        ) { innerPadding ->
```

What is doing the work:
- `fontFamily = LocalAppFonts.current.topBarTitle` is a wght 900 / wdth 112.5 / ROND 35 instance of
  a variable font, applied **only** to the bar title. Hero typography stays contained to the hero
  element. See `m3-expressive-theming/references/typography-and-variable-fonts.md`.
- `subtitle` is the breadcrumb ("Settings" above "Appearance") — semantic, not decorative.
- The navigation icon is a `FilledTonalIconButton` with `IconButtonDefaults.shapes()` (press-morph),
  not a bare `IconButton`. That is the "flexible elements for imagery and filled buttons" the spec
  mentions.
- `navigationIcon` is **hidden when the pane is expanded** in a list/detail layout — the list pane
  already provides the way back.
- `containerColor` on the `Scaffold` matches the bar's container so the collapse has no seam.

### 4.2 `TopAppBar(subtitle = {}, titleHorizontalAlignment = CenterHorizontally)`

**[from-corpus]**
`/root/work/repos/Tomato/shared/src/androidMain/kotlin/org/nsh07/pomodoro/ui/timerScreen/TimerScreen.kt`
(lines 225-303):

```kotlin
                Scaffold(
                    topBar = {
                        TopAppBar(
                            title = {
                                AnimatedContent(
                                    if (!timerState.showBrandTitle) timerState.timerMode else TimerMode.BRAND,
                                    transitionSpec = {
                                        slideInVertically(
                                            animationSpec = motionScheme.defaultSpatialSpec(),
                                            initialOffsetY = { (-it * 1.25).toInt() }
                                        ).togetherWith(
                                            slideOutVertically(
                                                animationSpec = motionScheme.defaultSpatialSpec(),
                                                targetOffsetY = { (it * 1.25).toInt() }
                                            )
                                        )
                                    }
                                ) {
                                    ...
                                }
                            },
                            subtitle = {},
                            titleHorizontalAlignment = CenterHorizontally,
                            colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
                            scrollBehavior = scrollBehavior
                        )
                    },
                    bottomBar = { Spacer(Modifier.height(contentPadding.calculateBottomPadding())) },
                    snackbarHost = { SnackbarHost(snackbarHostState) },
                    modifier = modifier.nestedScroll(scrollBehavior.nestedScrollConnection)
                ) { innerPadding ->
```

`subtitle = {}` is the idiom for "I want the Expressive overload for its
`titleHorizontalAlignment`, but no subtitle text." It is used at six sites in Tomato. Prefer this to
`CenterAlignedTopAppBar` on Expressive projects — same result, one component instead of two, and
you can add a subtitle later without changing composables.

The title itself is an `AnimatedContent` driven by `motionScheme.defaultSpatialSpec()`. **[judgment]**
Animating an app-bar title is cheap and reads as alive; keep it to spatial specs (slide/scale), never
effects springs with overshoot on colour.

---

## 5. Scroll behaviors

```kotlin
TopAppBarDefaults.enterAlwaysScrollBehavior(...)        // bar leaves on scroll down, returns immediately on scroll up
TopAppBarDefaults.exitUntilCollapsedScrollBehavior(...) // bar collapses to its collapsedHeight and stays
TopAppBarDefaults.pinnedScrollBehavior(...)             // bar stays; only the container colour lifts
```

**[verified]** Stable since alpha17; `TopAppBarScrollBehavior` and related re-promoted in alpha22.

Version-sensitive facts **[verified]**:
- **alpha15** added `isAtTopState` to `enterAlwaysScrollBehavior` / `pinnedScrollBehavior`.
- **alpha16** renamed that param `isAtTop` → **`isAtStart`**. If you see `isAtTop` in a snippet, it
  is pre-alpha16 code.
- **alpha22**: `pinnedScrollBehavior` and `enterAlwaysScrollBehavior` accept a **`ScrollableState`**
  overload — pass your `LazyListState` directly instead of hand-wiring an `isAtStart` lambda. Prefer
  this form on alpha22+.
- **alpha22**: `TopAppBarDefaults.snapAnimationSpec` is a public getter.
- **alpha23**: `TopAppBarDefaults.flingAnimationSpec` is a public getter. Use these to read the
  platform specs rather than inventing your own when you build a custom `TopAppBarScrollBehavior`.

Choosing **[judgment]**:

| Want | Use |
| --- | --- |
| Content-first screen, long list | `enterAlwaysScrollBehavior` |
| Large/flexible bar that should shrink to a small bar and stay | `exitUntilCollapsedScrollBehavior` |
| Bar must stay visible (has essential actions, or it hosts a search field) | `pinnedScrollBehavior` |

LastChat uses `TopAppBarDefaults.exitUntilCollapsedScrollBehavior()` on ~20 settings pages; Med uses
it on all six of its settings activities. **[from-corpus]** It is the default answer for a
large-headline screen.

`scrollBehavior` is **disabled automatically when an accessibility service is active** for
`FlexibleBottomAppBar` and floating toolbars **[verified]**. Don't fight it, and don't build a layout
whose usability depends on the bar hiding.

### Custom scroll behavior — when the built-ins aren't enough

**[from-corpus]** `/root/work/repos/vivi-music/app/src/main/kotlin/com/music/vivi/ui/utils/AppBar.kt`
(complete file) — a pinned behavior that also tracks height offset, so a bar can collapse while
staying `isPinned`:

```kotlin
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun appBarScrollBehavior(
    state: TopAppBarState = rememberTopAppBarState(),
    canScroll: () -> Boolean = { true },
    snapAnimationSpec: AnimationSpec<Float>? = spring(stiffness = Spring.StiffnessMediumLow),
    flingAnimationSpec: DecayAnimationSpec<Float>? = rememberSplineBasedDecay(),
): TopAppBarScrollBehavior =
    AppBarScrollBehavior(
        state = state,
        snapAnimationSpec = snapAnimationSpec,
        flingAnimationSpec = flingAnimationSpec,
        canScroll = canScroll,
    )

@ExperimentalMaterial3Api
class AppBarScrollBehavior(
    override val state: TopAppBarState,
    override val snapAnimationSpec: AnimationSpec<Float>?,
    override val flingAnimationSpec: DecayAnimationSpec<Float>?,
    val canScroll: () -> Boolean = { true },
) : TopAppBarScrollBehavior {
    override val isPinned: Boolean = true
    override var nestedScrollConnection =
        object : NestedScrollConnection {
            override fun onPostScroll(
                consumed: Offset,
                available: Offset,
                source: NestedScrollSource,
            ): Offset {
                if (!canScroll()) return Offset.Zero
                state.contentOffset += consumed.y
                if (state.heightOffset == 0f || state.heightOffset == state.heightOffsetLimit) {
                    if (consumed.y == 0f && available.y > 0f) {
                        // Reset the total content offset to zero when scrolling all the way down.
                        // This will eliminate some float precision inaccuracies.
                        state.contentOffset = 0f
                    }
                }
                state.heightOffset += consumed.y
                return Offset.Zero
            }
        }
}

@OptIn(ExperimentalMaterial3Api::class)
suspend fun TopAppBarState.resetHeightOffset() {
    if (heightOffset != 0f) {
        animate(
            initialValue = heightOffset,
            targetValue = 0f,
        ) { value, _ ->
            heightOffset = value
        }
    }
}
```

`resetHeightOffset()` is the piece most people miss: when you navigate between tabs, the bar's
`heightOffset` persists and the new screen opens with a half-collapsed bar. Call it on destination
change.

---

## 6. Scaffold integration, insets, edge-to-edge

The complete wiring:

```kotlin
val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

Scaffold(
    topBar = {
        LargeFlexibleTopAppBar(
            title = { Text("Library") },
            subtitle = { Text("42 albums") },
            scrollBehavior = scrollBehavior,
        )
    },
    modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),  // REQUIRED
) { innerPadding ->
    LazyColumn(contentPadding = innerPadding) { /* … */ }
}
```

Rules:

1. **`Modifier.nestedScroll(scrollBehavior.nestedScrollConnection)` goes on the `Scaffold`**, not on
   the app bar and not on the list. Forgetting it is the #1 cause of "my scroll behavior does
   nothing."
2. **Consume `innerPadding` via `contentPadding`, not `Modifier.padding`.** `contentPadding = innerPadding`
   on a `LazyColumn` lets content scroll under the bar while keeping the first and last items clear.
   `Modifier.padding(innerPadding)` clips the scroll container and kills the collapse effect.
3. **Edge-to-edge:** call `enableEdgeToEdge()` in the Activity and leave
   `windowInsets = TopAppBarDefaults.windowInsets` alone. The bar handles the status bar itself.
4. **Nested scaffolds / panes:** when an app bar is *not* at the window top — inside a detail pane,
   or as a `LazyColumn` item — pass **`windowInsets = WindowInsets()`** (empty) so it doesn't add a
   second status-bar pad.

   **[from-corpus]** `/root/work/repos/Tomato/.../statsScreen/screens/StatsMainScreen.kt:162` and
   `/root/work/repos/Tomato/.../timerScreen/TimerScreen.kt:721`:

   ```kotlin
                       TopAppBar(
                           title = {
                               Text(
                                   text = stringResource(Res.string.up_next),
                                   fontFamily = LocalAppFonts.current.topBarTitle,
                                   maxLines = 1
                               )
                           },
                           subtitle = {},
                           windowInsets = WindowInsets(),
                           colors = detailPaneTopBarColors
                       )
   ```

   That call site is literally an `item { }` inside a `LazyColumn` in a `SupportingPaneScaffold`
   supporting pane. An app bar as a list item is a legitimate pattern for pane headers.
5. **Merging insets across nested scaffolds:** Tomato carries a `contentPadding` down from the outer
   scaffold and merges it with the inner one (`mergePaddingValues(innerPadding, contentPadding)`)
   rather than double-applying. **[from-corpus]** If you nest scaffolds, do the same; do not let
   both add system bars.
6. **Colours:** set `Scaffold(containerColor = barColors.containerColor)` to match the bar, or the
   collapse shows a visible edge. Tomato pins `containerColor == scrolledContainerColor` on its
   `TopAppBarColors` for exactly this reason:

   **[from-corpus]** `/root/work/repos/Tomato/shared/src/commonMain/kotlin/org/nsh07/pomodoro/ui/theme/Color.kt`:

   ```kotlin
       val topBarColors: TopAppBarColors
           @Composable get() =
               TopAppBarDefaults.topAppBarColors(
                   containerColor = if (!black) colorScheme.surfaceContainer else colorScheme.surface,
                   scrolledContainerColor = if (!black) colorScheme.surfaceContainer else colorScheme.surface
               )

       val detailPaneTopBarColors: TopAppBarColors
           @Composable get() =
               TopAppBarDefaults.topAppBarColors(
                   containerColor = if (!black) colorScheme.surfaceContainerLow else colorScheme.surface,
                   scrolledContainerColor = if (!black) colorScheme.surfaceContainerLow else colorScheme.surface
               )
   ```

   Note the **different surface tier for the detail pane** — `surfaceContainerLow` vs
   `surfaceContainer` — so two panes side by side read as distinct planes.

---

## 7. `FlexibleBottomAppBar`

**[verified]**

```kotlin
@Composable
fun FlexibleBottomAppBar(
    modifier: Modifier = Modifier,
    containerColor: Color = BottomAppBarDefaults.containerColor,
    contentColor: Color = contentColorFor(containerColor),
    contentPadding: PaddingValues = BottomAppBarDefaults.FlexibleContentPadding,
    horizontalArrangement: Arrangement.Horizontal =
        BottomAppBarDefaults.FlexibleHorizontalArrangement,
    expandedHeight: Dp = BottomAppBarDefaults.FlexibleBottomAppBarHeight,
    windowInsets: WindowInsets = BottomAppBarDefaults.windowInsets,
    scrollBehavior: BottomAppBarScrollBehavior? = null,
    content: @Composable RowScope.() -> Unit,
)
```

`expandedHeight` KDoc, verbatim: *"the maximum height this bottom bar can reach when fully
expanded."*

Supporting defaults, all graduated alpha23 **[verified]**:
`BottomAppBarDefaults.FlexibleContentPadding`, `FlexibleBottomAppBarHeight`,
`FlexibleHorizontalArrangement`, `FlexibleFixedHorizontalArrangement`. Supported arrangements
include Fixed, SpaceAround, SpaceBetween, SpaceEvenly.

Wire it with `Modifier.nestedScroll(scrollBehavior.nestedScrollConnection)` on the `Scaffold`, same
as top bars. `scrollBehavior` is disabled when accessibility services are active **[verified]**.

**Not used by any app in the reference set** — Tomato, Med, LastChat and vivi-music have zero
`FlexibleBottomAppBar` occurrences, and so do all five Google application-sample repos. The
signature above is from the API listing, not from shipping code — but there **is** now canonical
verbatim usage: `BottomAppBarWithOverflow` in the androidx samples (§8.2) shows
`FlexibleBottomAppBar(contentPadding = PaddingValues(horizontal = 96.dp), horizontalArrangement = BottomAppBarDefaults.FlexibleFixedHorizontalArrangement)`
wrapping an `AppBarRow`. That is the reference form.

**Changed in alpha26:** plain **`BottomAppBar` and its associated methods were promoted to stable**
(I42c61) and no longer require `@ExperimentalMaterial3Api`. `FlexibleBottomAppBar` and the
`Flexible*` defaults graduated earlier, at alpha23. There is now no opt-in anywhere in the
bottom-app-bar family.

**[judgment]** Before reaching for it, check whether you want a **floating toolbar** instead.
Material's own guidance deprecates the bottom app bar in favour of the docked toolbar
**[verified — design guidance: "Keep using the bottom app bar" → "Deprecated — replace with the
docked toolbar"]**, and three of the four reference apps use `HorizontalFloatingToolbar` where a
bottom bar would once have gone. `FlexibleBottomAppBar` is for when you genuinely want an edge-to-edge
bar with arrangement control, not a floating pill.

---

## 8. `AppBarRow` / `AppBarColumn` — overflow-aware action containers

**Not used by any app in the reference set**, and absent from every Google *application* sample
too. But it is **fully covered by the androidx `@Sampled` functions**, and §8.1/§8.2 below are
verbatim from them — this section is no longer speculative.

Opt-in: **`ExperimentalMaterial3Api`, not Expressive.** `AppBarRow` has graduated off the
Expressive annotation (`AppBarSamples.kt`: 0 Expressive occurrences, 2 `ExperimentalMaterial3Api`).

**[verified]** signature:

```kotlin
@Composable
fun AppBarRow(
    modifier: Modifier = Modifier,
    overflowIndicator: @Composable (AppBarMenuState) -> Unit = { menuState ->
        AppBarOverflowIndicator(menuState)
    },
    maxItemCount: Int = Int.MAX_VALUE,
    content: AppBarRowScope.() -> Unit,
)
```

KDoc, verbatim: *"arranges its children in a horizontal sequence, and if any children overflow the
constraints, an overflow indicator is displayed."*
`maxItemCount` doc, verbatim: *"the actual maximum is reduced by one to accommodate the overflow
composable itself."*

`AppBarColumn` is the vertical analogue with `AppBarColumnScope`; its exact signature is
**UNVERIFIED** but is structurally identical (`modifier`, `overflowIndicator`, `maxItemCount`,
`content: AppBarColumnScope.() -> Unit`). It is present in `AppBarSamples.kt` (2 references) but
that sample body was **not read** — presence confirmed, code **UNVERIFIED**.

Supporting API: `AppBarMenuState`, `AppBarRowScope` / `AppBarColumnScope`,
`AppBarOverflowIndicator`. The scope DSL is **confirmed** by §8.1/§8.2 below:
`clickableItem(onClick, icon, label)`. The menu state's surface is **confirmed** as
`isShowing` / `show()` / `dismiss()`.

### 8.1 `AppBarRow` in a `TopAppBar`, size-class-driven — verbatim

Source: **androidx material3 samples**,
`compose/material3/material3/samples/src/main/java/androidx/compose/material3/samples/AppBarSamples.kt`
(lines 190–311), `SimpleTopAppBarWithAdaptiveActions`. Note that the content lambda uses a **scope
DSL** (`clickableItem`), not plain composables, and that in the `overflowIndicator` lambda the
implicit `it` **is the menu state** — `it.show()`.

```kotlin
@Preview
@Sampled
@Composable
@Suppress("DEPRECATION") // Move to currentWindowAdaptiveInfoV2 when dependency is updated
fun SimpleTopAppBarWithAdaptiveActions() {
    val sizeClass = currentWindowAdaptiveInfo().windowSizeClass
    // Material guidelines state 3 items max in compact, and 5 items max elsewhere.
    // To test this, try a resizable emulator, or a phone in landscape and portrait orientation.
    val maxItemCount =
        if (sizeClass.minWidthDp >= WindowSizeClass.WIDTH_DP_MEDIUM_LOWER_BOUND) {
            5
        } else {
            3
        }
    val icons =
        listOf(
            Icons.Filled.Attachment,
            Icons.Filled.Edit,
            Icons.Outlined.Star,
            Icons.Filled.Snooze,
            Icons.Outlined.MarkEmailUnread,
        )
    val items = listOf("Attachment", "Edit", "Star", "Snooze", "Mark unread")

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text("Simple TopAppBar", maxLines = 1, overflow = TextOverflow.Ellipsis)
                },
                navigationIcon = {
                    TooltipBox(
                        positionProvider =
                            TooltipDefaults.rememberTooltipPositionProvider(
                                TooltipAnchorPosition.Above
                            ),
                        tooltip = {
                            PlainTooltip(
                                modifier =
                                    Modifier.semantics {
                                        // TODO(b/496338253): Remove this modifier once bug where
                                        //   tooltip text is not announced by a11y screen readers
                                        //   is resolved.
                                        liveRegion = LiveRegionMode.Assertive
                                        paneTitle = "Menu"
                                    }
                            ) {
                                Text("Menu")
                            }
                        },
                        state = rememberTooltipState(),
                    ) {
                        IconButton(onClick = { /* doSomething() */ }) {
                            Icon(imageVector = Icons.Filled.Menu, contentDescription = "Menu")
                        }
                    }
                },
                actions = {
                    AppBarRow(
                        maxItemCount = maxItemCount,
                        overflowIndicator = {
                            TooltipBox(
                                positionProvider =
                                    TooltipDefaults.rememberTooltipPositionProvider(
                                        TooltipAnchorPosition.Above
                                    ),
                                tooltip = {
                                    PlainTooltip(
                                        modifier =
                                            Modifier.semantics {
                                                liveRegion = LiveRegionMode.Assertive
                                                paneTitle = "Overflow"
                                            }
                                    ) {
                                        Text("Overflow")
                                    }
                                },
                                state = rememberTooltipState(),
                            ) {
                                IconButton(onClick = { it.show() }) {
                                    Icon(
                                        imageVector = Icons.Filled.MoreVert,
                                        contentDescription = "Overflow",
                                    )
                                }
                            }
                        },
                    ) {
                        items.forEachIndexed { index, item ->
                            clickableItem(
                                onClick = {},
                                icon = {
                                    Icon(imageVector = icons[index], contentDescription = item)
                                },
                                label = item,
                            )
                        }
                    }
                },
            )
        },
        content = { innerPadding ->
            LazyColumn(
                modifier = Modifier.padding(innerPadding),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                val list = (0..75).map { it.toString() }
                items(count = list.size) {
                    Text(
                        text = list[it],
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                    )
                }
            }
        },
    )
}
```

Three things this settles that the old speculative block only guessed at:

1. `maxItemCount` really is driven off `currentWindowAdaptiveInfo().windowSizeClass` compared
   against `WindowSizeClass.WIDTH_DP_MEDIUM_LOWER_BOUND`, per Google's stated guideline of
   **3 items max in compact, 5 elsewhere**.
2. Every icon-only action — including the overflow indicator — is wrapped in a `TooltipBox` with
   `TooltipAnchorPosition.Above`, and the tooltip carries
   `Modifier.semantics { liveRegion = LiveRegionMode.Assertive; paneTitle = … }` to work around
   b/496338253 (tooltip text not announced by screen readers). Copy that workaround; it is Google's
   own.
3. `overflowIndicator` is a **named parameter passed after `maxItemCount`**, not a trailing lambda —
   the trailing lambda position belongs to `content`.

### 8.2 `AppBarRow` inside `FlexibleBottomAppBar` — verbatim, with explicit menu state

Source: **androidx material3 samples**, same file, `BottomAppBarWithOverflow` (lines ~2216–2282).
This variant names the menu state and toggles it, which is what you want if the overflow button
should also *close* the menu:

```kotlin
/** A sample for a [FlexibleBottomAppBar] with an overflow behavior when the content doesn't fit. */
@Preview
@Sampled
@Composable
fun BottomAppBarWithOverflow() {
    val icons =
        listOf(
            Icons.AutoMirrored.Filled.ArrowBack,
            Icons.AutoMirrored.Filled.ArrowForward,
            Icons.Filled.Add,
            Icons.Filled.Check,
            Icons.Filled.Edit,
            Icons.Filled.Favorite,
        )
    val items = listOf("Back", "Forward", "Add", "Check", "Edit", "Favorite")
    FlexibleBottomAppBar(
        contentPadding = PaddingValues(horizontal = 96.dp),
        horizontalArrangement = BottomAppBarDefaults.FlexibleFixedHorizontalArrangement,
    ) {
        AppBarRow(
            overflowIndicator = { menuState ->
                TooltipBox(
                    positionProvider =
                        TooltipDefaults.rememberTooltipPositionProvider(
                            TooltipAnchorPosition.Above
                        ),
                    tooltip = {
                        PlainTooltip(
                            modifier =
                                Modifier.semantics {
                                    liveRegion = LiveRegionMode.Assertive
                                    paneTitle = "Overflow"
                                }
                        ) {
                            Text("Overflow")
                        }
                    },
                    state = rememberTooltipState(),
                ) {
                    IconButton(
                        onClick = {
                            if (menuState.isShowing) {
                                menuState.dismiss()
                            } else {
                                menuState.show()
                            }
                        }
                    ) {
                        Icon(imageVector = Icons.Filled.MoreVert, contentDescription = "Overflow")
                    }
                }
            }
        ) {
            items.forEachIndexed { index, item ->
                clickableItem(
                    onClick = { /* doSomething() */ },
                    icon = { Icon(icons[index], contentDescription = item) },
                    label = item,
                )
            }
        }
    }
}
```

`AppBarRow` with **no `maxItemCount`** falls back to fit-based overflow — it measures and collapses
whatever doesn't fit. That is the mode to use inside a `FlexibleBottomAppBar`, where the available
width is whatever the arrangement leaves you.

**[judgment]** If `AppBarRow` doesn't resolve on your pin, fall back to a plain `actions = { … }`
row with an explicit `IconButton` + `DropdownMenu` overflow. That is what all four reference apps
do, and it is not wrong — it just doesn't measure.

---

## 9. Search bars — the churniest family in material3

This family changed more between 1.4.0 and 1.5.0-alpha24 than anything else in material3. Read the
timeline before writing any of it.

**[verified] timeline:**

| Version | Change |
| --- | --- |
| 1.4.0 | `SearchBarState`, `ExpandedFullScreenSearchBar`, `ExpandedDockedSearchBar` introduced |
| alpha16 | `SearchBarDefaults.InputField` **parameter order changed** |
| alpha18 | `rememberWithGapSearchBarState` **renamed** → `rememberSearchBarWithGapState` |
| alpha23 | `ExpandedDockedSearchBarWithGap`, `ExpandedFullScreenContainedSearchBar` graduated |
| alpha24 | `SearchBarState` + **slot-based `SearchBar` promoted to stable**; older `expanded` / `onExpandedChange` `SearchBar` overloads **deprecated**; `@ExperimentalMaterial3Api` **re-added** to `AppBarWithSearch` |
| **alpha26** | Scroll offset variables **removed from `SearchBarScrollBehavior`**; they now live on a new **`SearchBarScrollState`** class (Ib24e4). See §9.4. |

Practical consequences:
- Any snippet using `active` / `onActiveChange` is from the **pre-1.4 API** and is doubly stale.
- Any snippet using `expanded` / `onExpandedChange` is the **alpha24-deprecated** form.
- The current form is **state-based**: hold a `SearchBarState`, pass an `inputField` slot.
- Any read of `searchBarScrollBehavior.scrollOffset` / `.scrollOffsetLimit` / `.contentOffset`
  **stops compiling on alpha26** — insert `.scrollState` (§9.4).

### 9.1 `ExpandedFullScreenSearchBar` **[verified]**

```kotlin
@Composable
fun ExpandedFullScreenSearchBar(
    state: SearchBarState,
    inputField: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    collapsedShape: Shape = SearchBarDefaults.inputFieldShape,
    colors: SearchBarColors = SearchBarDefaults.colors(),
    tonalElevation: Dp = SearchBarDefaults.TonalElevation,
    shadowElevation: Dp = SearchBarDefaults.ShadowElevation,
    windowInsets: @Composable () -> WindowInsets = { SearchBarDefaults.fullScreenWindowInsets },
    properties: DialogProperties = DialogProperties(),
    content: @Composable ColumnScope.() -> Unit,
)
```

KDoc, verbatim: *"a search bar that is currently expanding or in the expanded state, showing search
results."*

**Pitfall:** `windowInsets` here is a **`@Composable () -> WindowInsets` lambda**, unlike every other
component in material3 where it is a plain `WindowInsets`. Passing `WindowInsets(...)` directly is a
type error.

**Pitfall:** it renders in a **`Dialog`** (`properties: DialogProperties`). That affects back
handling, predictive-back, and TalkBack traversal. Test both.

### 9.2 The rest of the family **[verified names, signatures not fully captured]**

| API | Notes |
| --- | --- |
| `SearchBarState` + `rememberSearchBarState()` | stable alpha24; the current state holder |
| slot-based `SearchBar(state, inputField, …)` | stable alpha24; **use this** |
| `SearchBarDefaults.InputField(…)` | param order changed in alpha16 — recheck against your pinned version |
| `ExpandedDockedSearchBar` | expanded state anchored to the bar rather than full-screen |
| `ExpandedDockedSearchBarWithGap` | graduated alpha23; results panel detached from the input by a gap |
| `rememberSearchBarWithGapState()` | renamed from `rememberWithGapSearchBarState` in alpha18 |
| `ExpandedFullScreenContainedSearchBar` | graduated alpha23; full-screen but visually contained |
| `rememberContainedSearchBarState()` | state holder for the *contained* variants; used by the canonical `AppBarWithSearch` sample (§9.3b) |
| `SearchBarDefaults.containedColors(state)` / `appBarWithSearchColors(searchBarColors)` | the contained/app-bar colour factories (§9.3b) |
| `SearchBarDefaults.enterAlwaysSearchBarScrollBehavior()` | the search-bar scroll behavior factory; see §9.4 for the alpha26 state split |
| `AppBarWithSearch` | **re-gated experimental in alpha24, still gated at alpha26** — a top app bar with a built-in search affordance. Needs `@OptIn(ExperimentalMaterial3Api::class)`. androidx's own canonical sample uses it (§9.3b), so it is usable — just pin your version and expect churn. |

### 9.3a `SearchBarState` + `ExpandedFullScreenSearchBar` — the canonical minimal form

Source: **androidx material3 samples**,
`compose/material3/material3/samples/src/main/java/androidx/compose/material3/samples/SearchBarSamples.kt`
(lines 83–113), `SimpleSearchBarSample`. Verbatim.

The load-bearing idiom: **`inputField` is hoisted into a `@Composable` val and passed to *both*
`SearchBar` and `ExpandedFullScreenSearchBar`.** That is what replaces the manual
`var expanded by rememberSaveable` bookkeeping in the old (and still-on-the-docs-site) form.

```kotlin
@Preview
@Sampled
@Composable
fun SimpleSearchBarSample() {
    val searchBarState = rememberSearchBarState()
    val textFieldState = rememberTextFieldState()
    val scope = rememberCoroutineScope()
    val inputField =
        @Composable {
            SearchBarDefaults.InputField(
                textFieldState = textFieldState,
                searchBarState = searchBarState,
                onSearch = { scope.launch { searchBarState.animateToCollapsed() } },
                placeholder = {
                    Text(modifier = Modifier.clearAndSetSemantics {}, text = "Search")
                },
                leadingIcon = { SampleLeadingIcon(searchBarState, scope) },
                trailingIcon = { SampleTrailingIcon() },
            )
        }

    SearchBar(state = searchBarState, inputField = inputField)
    ExpandedFullScreenSearchBar(state = searchBarState, inputField = inputField) {
        SampleSearchResults(
            onResultClick = { result ->
                textFieldState.setTextAndPlaceCursorAtEnd(result)
                scope.launch { searchBarState.animateToCollapsed() }
            }
        )
    }
}
```

Imports present in that file (verbatim, lines 46–64 subset):

```kotlin
import androidx.compose.material3.ExpandedFullScreenSearchBar
import androidx.compose.material3.SearchBarState
import androidx.compose.material3.rememberContainedSearchBarState
import androidx.compose.material3.rememberSearchBarState
```

Points to internalise:

- `SearchBarDefaults.InputField` takes **`textFieldState` + `searchBarState`** — not
  `query`/`onQueryChange`. If you see `query = `, it is the pre-1.4 API.
- Collapsing is `searchBarState.animateToCollapsed()` in a coroutine scope, not a boolean flip.
- `ExpandedFullScreenSearchBar` is a **sibling** of `SearchBar` in the composition, not a child.
- The placeholder carries `Modifier.clearAndSetSemantics {}` so the placeholder text isn't
  double-announced over the input field's own label.

### 9.3b `AppBarWithSearch` + `ExpandedFullScreenContainedSearchBar` — scaffold integration

Source: **androidx material3 samples**, same file, `FullScreenSearchBarScaffoldSample`
(lines 115–177). Verbatim. This is the app-bar-integrated *contained* variant; there is no
equivalent anywhere else in the corpus or on the docs site.

```kotlin
@Preview
@Sampled
@Composable
fun FullScreenSearchBarScaffoldSample() {
    val textFieldState = rememberTextFieldState()
    val searchBarState = rememberContainedSearchBarState()
    val scope = rememberCoroutineScope()
    val scrollBehavior = SearchBarDefaults.enterAlwaysSearchBarScrollBehavior()
    val appBarWithSearchColors =
        SearchBarDefaults.appBarWithSearchColors(
            searchBarColors = SearchBarDefaults.containedColors(state = searchBarState)
        )
    val inputField =
        @Composable {
            SearchBarDefaults.InputField(
                textFieldState = textFieldState,
                searchBarState = searchBarState,
                colors = appBarWithSearchColors.searchBarColors.inputFieldColors,
                onSearch = { scope.launch { searchBarState.animateToCollapsed() } },
                placeholder = {
                    Text(modifier = Modifier.clearAndSetSemantics {}, text = "Search")
                },
                leadingIcon = { SampleLeadingIcon(searchBarState, scope) },
                trailingIcon = { SampleTrailingIcon() },
            )
        }

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            AppBarWithSearch(
                scrollBehavior = scrollBehavior,
                state = searchBarState,
                colors = appBarWithSearchColors,
                inputField = inputField,
                navigationIcon = { SampleNavigationIcon(searchBarState, isAnimated = true) },
                actions = { SampleActions(searchBarState, isAnimated = true) },
            )
            ExpandedFullScreenContainedSearchBar(
                state = searchBarState,
                inputField = inputField,
                colors = appBarWithSearchColors.searchBarColors,
            ) {
                SampleSearchResults(
                    onResultClick = { result ->
                        textFieldState.setTextAndPlaceCursorAtEnd(result)
                        scope.launch { searchBarState.animateToCollapsed() }
                    }
                )
            }
        },
    ) { padding ->
        LazyColumn(contentPadding = padding, verticalArrangement = Arrangement.spacedBy(8.dp)) {
            val list = List(100) { "Text $it" }
            items(count = list.size) {
                Text(
                    text = list[it],
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                )
            }
        }
    }
}
```

**The non-obvious structural rule:** the expanded search bar is placed **inside the `topBar` slot**,
as a sibling of `AppBarWithSearch` — *not* as an overlay elsewhere in the tree. The `topBar` lambda
emits two composables.

Also note the colour plumbing: build **one** `appBarWithSearchColors` from
`SearchBarDefaults.containedColors(state = searchBarState)`, then hand its
`.searchBarColors.inputFieldColors` to the input field and its `.searchBarColors` to the expanded
bar. Three components, one source of colour truth.

### 9.4 alpha26: `SearchBarScrollState` — scroll offsets moved off the behavior

**[verified]** at the alpha26 SHA `4d087bd6f764b8425a70fd94102f855aa382d94b`. This is a **hard
compile break**, not a deprecation.

```kotlin
@Stable
public interface SearchBarScrollBehavior {
    public val scrollState: SearchBarScrollState
    public val nestedScrollConnection: NestedScrollConnection
    public val searchBarScrollBehaviorModifier: Modifier
}

@Stable
public class SearchBarScrollState(
    initialScrollOffsetLimit: Float,
    initialScrollOffset: Float,
    initialContentOffset: Float,
) {
    public var scrollOffset: Float
    public var scrollOffsetLimit: Float
    public var contentOffset: Float

    public companion object {
        public val Saver: Saver<SearchBarScrollState, *>
    }
}

@Composable
public fun rememberSearchBarScrollState(
    initialScrollOffsetLimit: Float = -Float.MAX_VALUE,
    initialScrollOffset: Float = 0f,
    initialContentOffset: Float = 0f,
): SearchBarScrollState
```

The three properties that **moved off** `SearchBarScrollBehavior`: `scrollOffset`,
`scrollOffsetLimit`, `contentOffset`. Migration is one extra hop:

```kotlin
// OLD — ≤ alpha25
val behavior = SearchBarDefaults.enterAlwaysSearchBarScrollBehavior()
val offset = behavior.scrollOffset

// NEW — alpha26+
val behavior = SearchBarDefaults.enterAlwaysSearchBarScrollBehavior()
val offset = behavior.scrollState.scrollOffset
```

`SearchBarScrollState` has **no usage anywhere** — not in the androidx samples, not in any Google
app repo. The interface/class shapes above are read from source; the *idiomatic* way to use
`rememberSearchBarScrollState` (as opposed to reading the one the behavior already owns) is
**UNVERIFIED**.

The factory name `SearchBarDefaults.enterAlwaysSearchBarScrollBehavior()` was previously
**UNVERIFIED**; it is now corroborated independently — it appears verbatim in
`FullScreenSearchBarScaffoldSample` (§9.3b). Note that sample is from androidx-main *after*
alpha26, and it uses `scrollBehavior` only for `.nestedScrollConnection` and to hand to
`AppBarWithSearch` — it never reads a scroll offset, so it is unaffected by this break.

### 9.5 What the corpus actually does — and why not to copy it

**[from-corpus]** `/root/work/repos/vivi-music/app/src/main/kotlin/com/music/vivi/ui/screens/search/SearchScreen.kt`
(lines 244-340), abridged:

```kotlin
                SearchBar(
                    query = query.text,
                    onQueryChange = { query = TextFieldValue(it) },
                    onSearch = {
                        onSearch(it)
                        searchActive = false
                    },
                    active = searchActive,
                    onActiveChange = { searchActive = it },
                    placeholder = { /* … */ },
                    leadingIcon = { /* … */ },
                    trailingIcon = { /* … */ },
                    colors = SearchBarDefaults.colors(
                        containerColor = if (pureBlack) MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                                         else MaterialTheme.colorScheme.surfaceVariant
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = searchBarHorizontalPadding)
                        .padding(top = searchBarTopPadding)
                ) {
                    /* expanded content */
                }
```

vivi-music is pinned to `material3 = "1.5.0-alpha23"` and is still calling the **`active` /
`onActiveChange`** overload — two API generations behind. This is a real, current, shipping app
using the wrong API. **Do not copy this.** It is here so you recognise it in the wild and migrate it:
`active`/`onActiveChange` → `expanded`/`onExpandedChange` (deprecated alpha24) → `state: SearchBarState`
with an `inputField` slot (current — §9.3a is the target shape).

Worth stating plainly: **developer.android.com's search-bar page is also stuck on the pre-state
form** (§0), so "it's on the docs site" is not evidence the API is current. `SimpleSearchBar` and
`CustomizableSearchBar` on that page both inline `SearchBarDefaults.InputField(query = …)` into a
single `SearchBar` with a manual `expanded` flag.

The one thing worth keeping from it: the animated horizontal/top padding
(`searchBarHorizontalPadding`, `searchBarTopPadding` — `animateDpAsState` driven by the expanded
flag) so the bar grows to full-bleed as it expands. That effect is now built into
`ExpandedFullScreenSearchBar`; you don't need to hand-roll it.

**[judgment] Decision rule:**
- Search is the screen's purpose → `ExpandedFullScreenSearchBar`.
- Search filters a list that stays visible → `ExpandedDockedSearchBar` (or `…WithGap` for a
  detached results panel).
- Search is a persistent affordance in the top app bar → `AppBarWithSearch` +
  `ExpandedFullScreenContainedSearchBar` (§9.3b). It is still `@ExperimentalMaterial3Api` at
  alpha26, so opt in and pin your version — but androidx ships it as a canonical `@Sampled`
  function, so it is not a dead end.
- Search is one action among many in an app bar → an `IconButton` that navigates to a search
  destination.

---

## 10. Design guidance and anti-patterns

### Which height

**[verified — TopAppBar.md]** Small for dense layouts and scrolled pages. Medium flexible for a
larger headline that collapses. Large flexible to emphasize the page headline.

**[judgment]** Height is a hierarchy signal, so it must vary. If every screen has a
`LargeFlexibleTopAppBar`, none of them reads as important — the same relational logic as shape and
colour ("Break from the surrounding shape style… smaller shapes can result in essential actions
looking less important" **[CANON]**). Tomato gets this right: large flexible on settings/stats
detail screens, plain `TopAppBar` on the timer (the screen where the *content* is the hero).

### Collapse behavior

**[verified]** Available effects: lift-on-scroll (elevation/container colour increases),
scroll/enterAlways/snap (disappear/appear), and a compress effect.

**[judgment]** Pick one per app and hold it. Mixing `enterAlways` on one screen and
`exitUntilCollapsed` on the next makes the app feel unpredictable — motion principle: don't break
spatial coherence.

### Hero typography in app bars

The Expressive move is: `LargeFlexibleTopAppBar` + `subtitle` + a heavy variable-font instance on the
title only. **[from-corpus — Tomato]**

Do **not**:
- put the hero face into the global `Typography` and get it everywhere;
- animate the title with an effects spring that overshoots colour;
- use display type so large it wraps to three lines on a small phone — flexible bars support text
  wrapping, but a wrapped hero headline reads as a bug.

Use `MaterialTheme(typography = appBarTypography) { LargeTopAppBar(…) }` (Med's technique, §3.3) if
you only want the swap inside the bar.

### Anti-patterns

| Don't | Instead |
| --- | --- |
| Forget `Modifier.nestedScroll(scrollBehavior.nestedScrollConnection)` on the `Scaffold` | Always wire it; without it `scrollBehavior` is inert |
| `Modifier.padding(innerPadding)` on the scrolling container | `contentPadding = innerPadding` |
| Hardcode `expandedHeight` / `collapsedHeight` dp values | Use `TopAppBarDefaults.*` tokens; the flexible bars adjust for the subtitle automatically |
| Add a second status-bar inset to a bar inside a pane or list | `windowInsets = WindowInsets()` |
| Use `CenterAlignedTopAppBar` on an Expressive project | `TopAppBar(subtitle = {}, titleHorizontalAlignment = CenterHorizontally)` |
| Use `MediumTopAppBar` / `LargeTopAppBar` on new code | The flexible variants replace them |
| Ship `active`/`onActiveChange` or `expanded`/`onExpandedChange` search bars | slot-based `SearchBar` + `SearchBarState` (§9.3a) |
| Build a second `inputField` for the expanded search bar | Hoist one `@Composable` val and pass it to both (§9.3a) |
| Use `AppBarWithSearch` without an opt-in and a pinned version | It is `@ExperimentalMaterial3Api` from alpha24 through alpha26 |
| Read `searchBarScrollBehavior.scrollOffset` | `behavior.scrollState.scrollOffset` on alpha26+ (§9.4) |
| Carry `@OptIn(ExperimentalMaterial3Api::class)` for `BottomAppBar` | It is stable as of alpha26 |
| Trust developer.android.com's app-bars / search-bar pages | Use androidx `material3/samples` (§0) |
| Show a navigation bar and a toolbar on the same page | Navigation bar on primary pages, toolbars on subsequent pages with actions **[verified]** |
| Let a bar's actions overflow the pane edge | `AppBarRow` with `maxItemCount`, or an explicit overflow menu |
| Leave `heightOffset` set when navigating between destinations | `resetHeightOffset()` on destination change |
| Large flexible bar on every screen | Budget it; one or two hero moments per product **[CANON]** |

### Accessibility

- Navigation icon and every action need a `contentDescription`. Tomato passes
  `stringResource(Res.string.back)` positionally as the icon's content description. **[from-corpus]**
- Touch targets stay ≥48×48dp even when the icon button is visually smaller **[verified — Material
  accessibility guidance]**.
- With an accessibility service running, `scrollBehavior` is disabled on flexible bottom bars and
  floating toolbars — the layout must be usable with the bar permanently visible **[verified]**.
- `ExpandedFullScreenSearchBar` is a `Dialog`: verify back gesture, predictive back, and that focus
  lands in the input field when it expands.
- Subtitle text defaults to `onSurfaceVariant` **[verified]** — check it against your container
  colour if you override `TopAppBarColors`, since that pairing is only guaranteed 3:1 for the
  default surface.

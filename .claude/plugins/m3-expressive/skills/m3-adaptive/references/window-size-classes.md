# Window Size Classes — Artifacts, Breakpoints, and the `>=` Trap

Everything needed to make a correct size-class decision at **`material3-adaptive` 1.3.0** (stable,
2026-08-12) and **`material3` 1.5.0-alpha26**. Read §1 before writing a Gradle line and §5 before
writing a `when`.

## Provenance markers used in this file

| Marker | Meaning |
|---|---|
| **[API-1.3.0]** | Verbatim from the frozen metalava signature file `api/1.3.0-rc01.txt` (byte-identical to shipped 1.3.0). |
| **[SRC@HEAD]** | Verbatim Kotlin source at androidx-main HEAD `360e8cba7ae6` (2026-08-14). HEAD is *post*-1.3.0. |
| **[DOC]** | developer.android.com / m3.material.io, fetched 2026-08-14. |
| **[REPO]** | Verbatim from a cloned sample app. |
| **UNVERIFIED** | Stated but not confirmed against a primary source. |

## The four things that go wrong most often

1. **Two different version trains.** The adaptive artifacts are `1.3.0`; the nav-suite and
   window-size-class artifacts are in the **`androidx.compose.material3` group** and version with
   material3. Pinning `material3-adaptive-navigation-suite:1.3.0` is a classic mistake. → §1
2. **`currentWindowAdaptiveInfo()` is deprecated.** It defaults `supportLargeAndXLargeWidth = false`
   and silently clamps everything ≥840dp to Expanded. Use `currentWindowAdaptiveInfoV2()`. → §7
3. **The predicates are `>=`.** A `when` chain that runs smallest→largest collapses to the smallest
   bucket for every window. → §5
4. **Width has five buckets, not three.** Large (1200) and Extra-large (1600) exist. Height still has
   three. → §4

---

# 1. Artifacts, coordinates, versions

## 1.1 The adaptive group — `androidx.compose.material3.adaptive`

All four artifacts version **in lockstep**. Current stable: **1.3.0** (2026-08-12). [DOC]

```kotlin
dependencies {
    implementation("androidx.compose.material3.adaptive:adaptive:1.3.0")
    implementation("androidx.compose.material3.adaptive:adaptive-layout:1.3.0")
    implementation("androidx.compose.material3.adaptive:adaptive-navigation:1.3.0")
    implementation("androidx.compose.material3.adaptive:adaptive-navigation3:1.3.0")
}
```

| Artifact | Package | What it contains | Depends on |
|---|---|---|---|
| `adaptive` | `androidx.compose.material3.adaptive` | `WindowAdaptiveInfo`, `currentWindowAdaptiveInfoV2()`, `Posture`, `HingeInfo`, `collectFoldingFeaturesAsState()`, `ExperimentalMaterial3AdaptiveApi` | `androidx.window:window`, `androidx.window:window-core`, compose-runtime/ui |
| `adaptive-layout` | `…adaptive.layout` | Pane scaffolds, `PaneScaffoldDirective`, adapt strategies, pane expansion, motion, `AnimatedPane` | `adaptive` |
| `adaptive-navigation` | `…adaptive.navigation` | `ThreePaneScaffoldNavigator`, `remember*Navigator`, `BackNavigationBehavior`, `Navigable*PaneScaffold` | `adaptive-layout`, `androidx.activity:activity-compose` |
| `adaptive-navigation3` | `…adaptive.navigation3` | `ListDetailSceneStrategy`, `SupportingPaneSceneStrategy`, scene scopes | `adaptive-navigation`, `androidx.navigation3:navigation3-runtime` / `-ui` |

**`adaptive-navigation3` first shipped in 1.3.0.** Its `api/` directory contains only
`1.3.0-beta01/beta02/rc01/current` — there is no 1.2.x signature file. Prereleases were
`1.3.0-alphaNN`.

**Structural note:** these live at `compose/material3/adaptive/` in androidx, **not**
`compose/material3/material3-adaptive/`.

### Release history [DOC]

| Version | Date | Notes |
|---|---|---|
| 1.3.0 | 2026-08-12 | Stable. Adds `adaptive-navigation3`. Scaffold margins + edge-to-edge. RTL pane-expansion anchors. |
| 1.3.0-rc01 | 2026-06-17 | API frozen |
| 1.2.0 | 2025-10-22 | `preferredHeight`; **Reflow** and **Levitate** adapt strategies; `currentWindowAdaptiveInfo()` gains L/XL width support |
| 1.1.0 | — | `PaneExpansionState`, `NavigableListDetailPaneScaffold`, predictive back |
| 1.0.0 | — | `ListDetailPaneScaffold`, `SupportingPaneScaffold`, `ThreePaneScaffold` |

## 1.2 The material3 group — nav suite + window size class

**These are NOT in the adaptive group.** They version with `androidx.compose.material3`. This is the
single most common dependency mistake in adaptive code.

```kotlin
dependencies {
    implementation("androidx.compose.material3:material3:1.5.0-alpha26")
    implementation("androidx.compose.material3:material3-adaptive-navigation-suite:1.5.0-alpha26")
    // LEGACY — do NOT add this to a new project (§9). The type you want,
    // androidx.window.core.layout.WindowSizeClass, already arrives via the adaptive artifacts.
    // Only add it to keep an existing calculateWindowSizeClass(Activity) call site compiling, and
    // then let the Compose BOM (or your material3 version ref) resolve it — do not pin it by hand.
    // implementation("androidx.compose.material3:material3-window-size-class")
}
```

| Artifact | Group | Version on the release page (2026-08-14) | Package |
|---|---|---|---|
| `material3` | `androidx.compose.material3` | 1.4.0 stable; **1.5.0-alpha26** (Expressive line) | `androidx.compose.material3` |
| `material3-adaptive-navigation-suite` | `androidx.compose.material3` | **1.5.0-alpha26** per the release-page snippet | `androidx.compose.material3.adaptive.navigationsuite` |
| `material3-window-size-class` | `androidx.compose.material3` | **legacy — do not add to new code** (§9). Versions with whichever `material3` line you are already on; let the BOM/version-ref resolve it rather than pinning it. | `androidx.compose.material3.windowsizeclass` |

**Rule: pin the nav-suite artifact to the same version as `material3`.** If the project is on the
Expressive line, that is `1.5.0-alpha26`. If it uses the Compose BOM, leave all three un-versioned
and let the BOM resolve them — that is what `compose-samples/Reply` and `JetNews` do.

> **UNVERIFIED:** which nav-suite version is "the" stable one. The checkout's `api/` directory has
> `1.3.0-beta01..03` and `1.4.0-beta01..03`, yet the material3 release page's dependency snippet
> reads `1.5.0-alpha26`. What **is** verified is the group (`androidx.compose.material3`) and that
> the snippet advertises `1.5.0-alpha26`.

`material3-window-size-class` is a **legacy artifact you generally should not add.** The current API
is `androidx.window.core.layout.WindowSizeClass`, reached via `currentWindowAdaptiveInfoV2()`, and it
already arrives with the adaptive artifacts. Add the legacy artifact only to keep existing
`calculateWindowSizeClass(Activity)` code compiling — see §9. (This is the same call
`${CLAUDE_PLUGIN_ROOT}/skills/m3-expressive-best-practices/references/starter-project.md` makes when
it omits the artifact deliberately.)

## 1.3 Companion artifacts you will need

| Artifact | Why |
|---|---|
| `androidx.window:window` | `WindowInfoTracker`, `FoldingFeature`, `WindowMetricsCalculator`, `WindowSdkExtensions`, `SupportedPosture`, `WindowArea*` |
| `androidx.window:window-core` | **`androidx.window.core.layout.WindowSizeClass`** — the type actually returned by `WindowAdaptiveInfo.windowSizeClass` |
| `androidx.navigation3:navigation3-runtime` / `-ui` | required by `adaptive-navigation3` |
| `androidx.activity:activity-compose` | `PredictiveBackHandler`, used by `ThreePaneScaffoldPredictiveBackHandler` |

Observed pins in real repos:

| Repo | Pins |
|---|---|
| `snippets` (dev.android.com) | `material3-adaptive = "1.3.0"`, `material3-adaptive-navigation3 = "1.3.0"`, `material3-adaptive-navigation-suite = "1.4.0"`, `androidx-navigation3 = "1.1.5"`, `androidx-window = "1.6.0-alpha05"`, `androidx-window-core = "1.5.1"` |
| `nowinandroid` | `androidxComposeMaterial3Adaptive = "1.1.0-rc01"`, `…AdaptiveNavigation3 = "1.3.0-alpha04"`, `androidxWindowManager = "1.3.0"` |
| `androidify` | `adaptive = "1.2.0"`, `window = "1.5.1"` |
| `compose-samples/Reply`, `JetNews` | `androidx-window = "1.5.1"`, adaptive artifacts un-versioned (BOM) |

Note that nowinandroid mixes `adaptive = 1.1.0-rc01` with `adaptive-navigation3 = 1.3.0-alpha04` —
that works only because navigation3 was a separate prerelease line. Do not copy that split into new
code; use 1.3.0 across all four.

## 1.4 Opt-in annotations

[API-1.3.0] `adaptive/api/1.3.0-rc01.txt`:

```
@SuppressCompatibility @kotlin.RequiresOptIn(message="This material3 adaptive API is experimental and is likely to change or to beremoved in the future.") @kotlin.annotation.Retention(kotlin.annotation.AnnotationRetention.BINARY) public @interface ExperimentalMaterial3AdaptiveApi {
}

@SuppressCompatibility @kotlin.RequiresOptIn(message="This material3 adaptive API is experimental and is likely to change or to beremoved in the future.") @kotlin.annotation.Retention(kotlin.annotation.AnnotationRetention.BINARY) public @interface ExperimentalMaterial3AdaptiveComponentOverrideApi {
}
```

(The "to beremoved" typo is in the shipped source.)
`ExperimentalMaterial3AdaptiveComponentOverrideApi` exists in **1.3.0** and is **removed at HEAD** —
do not build on the `*Override` composition locals.

**What is and is not gated:**

| Symbol | Opt-in needed? |
|---|---|
| `currentWindowAdaptiveInfoV2()` | **No** |
| `WindowAdaptiveInfo`, `Posture`, `HingeInfo` and the six hinge-bounds properties | **No** |
| `androidx.window.core.layout.WindowSizeClass` and all its predicates | **No** |
| `collectFoldingFeaturesAsState()` | **No** |
| `calculatePaneScaffoldDirective`, `calculatePaneScaffoldDirectiveWithTwoPanesOnMediumWidth` | **Yes** — `@ExperimentalMaterial3AdaptiveApi` |
| `currentWindowDpSize()` | **Yes** — `@ExperimentalMaterial3AdaptiveApi` (and deprecated) |
| `NavigationSuiteScaffold`, `NavigationSuiteType`, `NavigationSuiteScaffoldDefaults` | **No** as of 1.4.0/1.5.0-alpha — the core nav-suite surface carries no opt-in in `current.txt` |
| `calculateWindowSizeClass(Activity)` (legacy artifact) | **Yes** — `@ExperimentalMaterial3WindowSizeClassApi` |

Nav-suite still ships its own annotation for whatever remains gated:
```
@SuppressCompatibility @kotlin.RequiresOptIn(message="This material3-adaptive-navigation-suite API is experimental and is likely tochange or to be removed in the future.") @kotlin.annotation.Retention(kotlin.annotation.AnnotationRetention.BINARY) public @interface ExperimentalMaterial3AdaptiveNavigationSuiteApi {
}
```

---

# 2. `WindowSizeClass` — the current API

`androidx.window.core.layout.WindowSizeClass`, from `androidx.window:window-core`. This is the type
returned by `WindowAdaptiveInfo.windowSizeClass`.

**It is not an enum.** It is a pair of *lower bounds* in dp, queried with `isAtLeast*` predicates.

### Full signature [API `window/window-core/api/current.txt`]

```
public final class WindowSizeClass {
    ctor public WindowSizeClass(float widthDp, float heightDp);
    ctor public WindowSizeClass(int minWidthDp, int minHeightDp);
    method @Deprecated public static androidx.window.core.layout.WindowSizeClass compute(float dpWidth, float dpHeight);
    method @InaccessibleFromKotlin public int getMinHeightDp();
    method @InaccessibleFromKotlin public int getMinWidthDp();
    method @InaccessibleFromKotlin @Deprecated public androidx.window.core.layout.WindowHeightSizeClass getWindowHeightSizeClass();
    method @InaccessibleFromKotlin @Deprecated public androidx.window.core.layout.WindowWidthSizeClass getWindowWidthSizeClass();
    method public boolean isAtLeastBreakpoint(int widthDpBreakpoint, int heightDpBreakpoint);
    method public boolean isHeightAtLeastBreakpoint(int heightDpBreakpoint);
    method public boolean isWidthAtLeastBreakpoint(int widthDpBreakpoint);
    property public int minHeightDp;
    property public int minWidthDp;
    property @Deprecated public androidx.window.core.layout.WindowHeightSizeClass windowHeightSizeClass;
    property @Deprecated public androidx.window.core.layout.WindowWidthSizeClass windowWidthSizeClass;
    field public static final java.util.Set<androidx.window.core.layout.WindowSizeClass> BREAKPOINTS_V1;
    field public static final java.util.Set<androidx.window.core.layout.WindowSizeClass> BREAKPOINTS_V2;
    field public static final androidx.window.core.layout.WindowSizeClass.Companion Companion;
    field public static final int HEIGHT_DP_EXPANDED_LOWER_BOUND = 900; // 0x384
    field public static final int HEIGHT_DP_MEDIUM_LOWER_BOUND = 480; // 0x1e0
    field public static final int WIDTH_DP_EXPANDED_LOWER_BOUND = 840; // 0x348
    field public static final int WIDTH_DP_EXTRA_LARGE_LOWER_BOUND = 1600; // 0x640
    field public static final int WIDTH_DP_LARGE_LOWER_BOUND = 1200; // 0x4b0
    field public static final int WIDTH_DP_MEDIUM_LOWER_BOUND = 600; // 0x258
}

public static final class WindowSizeClass.Companion {
    method @Deprecated public androidx.window.core.layout.WindowSizeClass compute(float dpWidth, float dpHeight);
    property public java.util.Set<androidx.window.core.layout.WindowSizeClass> BREAKPOINTS_V1;
    property public java.util.Set<androidx.window.core.layout.WindowSizeClass> BREAKPOINTS_V2;
    property public static int HEIGHT_DP_EXPANDED_LOWER_BOUND;
    property public static int HEIGHT_DP_MEDIUM_LOWER_BOUND;
    property public static int WIDTH_DP_EXPANDED_LOWER_BOUND;
    property public static int WIDTH_DP_EXTRA_LARGE_LOWER_BOUND;
    property public static int WIDTH_DP_LARGE_LOWER_BOUND;
    property public static int WIDTH_DP_MEDIUM_LOWER_BOUND;
}

public final class WindowSizeClassGridCreator {
    method public static java.util.Set<androidx.window.core.layout.WindowSizeClass> addHeightDpBreakpoints(java.util.Set<androidx.window.core.layout.WindowSizeClass>, java.util.Set<java.lang.Integer> minHeightDpBreakPoints);
    method public static java.util.Set<androidx.window.core.layout.WindowSizeClass> createGridWindowSizeClassSet(java.util.Set<java.lang.Integer> minWidthDpBreakPoints, java.util.Set<java.lang.Integer> minHeightDpBreakPoints);
}

public final class WindowSizeClassSelectors {
    method public static androidx.window.core.layout.WindowSizeClass computeWindowSizeClass(java.util.Set<androidx.window.core.layout.WindowSizeClass>, float widthDp, float heightDp);
    method public static androidx.window.core.layout.WindowSizeClass computeWindowSizeClass(java.util.Set<androidx.window.core.layout.WindowSizeClass>, int widthDp, int heightDp);
    method public static androidx.window.core.layout.WindowSizeClass computeWindowSizeClassPreferHeight(java.util.Set<androidx.window.core.layout.WindowSizeClass>, int widthDp, int heightDp);
}

@Deprecated public final class WindowHeightSizeClass {
    field @Deprecated public static final androidx.window.core.layout.WindowHeightSizeClass COMPACT;
    field @Deprecated public static final androidx.window.core.layout.WindowHeightSizeClass.Companion Companion;
    field @Deprecated public static final androidx.window.core.layout.WindowHeightSizeClass EXPANDED;
    field @Deprecated public static final androidx.window.core.layout.WindowHeightSizeClass MEDIUM;
}

@Deprecated public final class WindowWidthSizeClass {
    field @Deprecated public static final androidx.window.core.layout.WindowWidthSizeClass COMPACT;
    field @Deprecated public static final androidx.window.core.layout.WindowWidthSizeClass.Companion Companion;
    field @Deprecated public static final androidx.window.core.layout.WindowWidthSizeClass EXPANDED;
    field @Deprecated public static final androidx.window.core.layout.WindowWidthSizeClass MEDIUM;
}
```

### There is no `containsWidthDp` / `containsHeightDp`

**Do not write them.** They do not exist at any version. The whole query surface is three methods
and two properties:

| You want | Write |
|---|---|
| "is the window at least Expanded wide?" | `wsc.isWidthAtLeastBreakpoint(WindowSizeClass.WIDTH_DP_EXPANDED_LOWER_BOUND)` |
| "is the window at least Medium tall?" | `wsc.isHeightAtLeastBreakpoint(WindowSizeClass.HEIGHT_DP_MEDIUM_LOWER_BOUND)` |
| "at least Expanded × Medium?" | `wsc.isAtLeastBreakpoint(WindowSizeClass.WIDTH_DP_EXPANDED_LOWER_BOUND, WindowSizeClass.HEIGHT_DP_MEDIUM_LOWER_BOUND)` |
| "which exact bucket am I in?" | compare `wsc.minWidthDp` / `wsc.minHeightDp` (`Int`) against the constants |

Constructor validation [SRC@HEAD]:
```kotlin
init {
    require(minWidthDp >= 0) {
        "Expected minWidthDp to be at least 0, minWidthDp: $minWidthDp."
    }
    require(minHeightDp >= 0) {
        "Expected minHeightDp to be at least 0, minHeightDp: $minHeightDp."
    }
}
```

---

# 3. The complete breakpoint table

**Width has 5 buckets. Height has 3.**

| Axis | Bucket | Constant | dp range |
|---|---|---|---|
| Width | Compact | *(implicit 0)* | `0 ≤ w < 600` |
| Width | Medium | `WIDTH_DP_MEDIUM_LOWER_BOUND` = **600** | `600 ≤ w < 840` |
| Width | Expanded | `WIDTH_DP_EXPANDED_LOWER_BOUND` = **840** | `840 ≤ w < 1200` |
| Width | **Large** | `WIDTH_DP_LARGE_LOWER_BOUND` = **1200** | `1200 ≤ w < 1600` |
| Width | **Extra-large** | `WIDTH_DP_EXTRA_LARGE_LOWER_BOUND` = **1600** | `w ≥ 1600` |
| Height | Compact | *(implicit 0)* | `0 ≤ h < 480` |
| Height | Medium | `HEIGHT_DP_MEDIUM_LOWER_BOUND` = **480** | `480 ≤ h < 900` |
| Height | Expanded | `HEIGHT_DP_EXPANDED_LOWER_BOUND` = **900** | `h ≥ 900` |

### Device coverage [DOC `use-window-size-classes`]

| Size class | Breakpoint | Device representation |
|---|---|---|
| Compact width | width < 600dp | 99.96% of phones in portrait |
| Medium width | 600 ≤ width < 840 | 93.73% of tablets in portrait, most large unfolded inner displays in portrait |
| Expanded width | 840 ≤ width < 1200 | 97.22% of tablets in landscape, most large unfolded inner displays in landscape |
| Large width | 1200 ≤ width < 1600 | Large tablet displays |
| Extra-large width | width ≥ 1600 | Desktop displays |
| Compact height | height < 480 | 99.78% of phones in landscape |
| Medium height | 480 ≤ height < 900 | 96.56% of tablets in landscape, 97.59% of phones in portrait |
| Expanded height | height ≥ 900 | 94.25% of tablets in portrait |

### What each width bucket implies for layout

This is the actual behaviour of `calculatePaneScaffoldDirective`, not a guideline — derived from
[SRC@HEAD `PaneScaffoldDirective.kt`]:

| Width bucket | `maxHorizontalPartitions` | gutter | `defaultPanePreferredWidth` | Layout it produces |
|---|---|---|---|---|
| Compact (0) | **1** | 0.dp | 360.dp | single pane |
| Medium (600) | **1** | 0.dp | 360.dp | **single pane** — deliberate Material guidance |
| Expanded (840) | **2** | 24.dp | 360.dp | list + detail |
| Large (1200) | **3** | 24.dp | **412.dp** | list + detail + extra |
| Extra-large (1600) | **3** | 24.dp | **412.dp** | list + detail + extra |

| Condition | `maxVerticalPartitions` | gutter |
|---|---|---|
| `posture.isTabletop == true` | **2** | 24.dp |
| `maxHorizontalPartitions == 1 && minHeight == 900` | **2** | 24.dp |
| otherwise | 1 | 0.dp |

**Medium width is single-pane by default.** A 700dp window shows ONE pane. If you genuinely need two
there, use `calculatePaneScaffoldDirectiveWithTwoPanesOnMediumWidth` — but the androidx KDoc
explicitly recommends against it: *"We recommend to use `[calculatePaneScaffoldDirective]`, unless
you have a strong use case to show two panes on a medium-width window, which can make your layout
look too packed."*

### Navigation component per width bucket

Classic guidance [DOC]: Compact/Medium → bottom navigation bar (3–4 destinations) or drawer (5+);
Expanded → side rail, plus multiple panes. The library's own default
(`NavigationSuiteScaffoldDefaults.navigationSuiteType`) is stricter and Expressive-flavoured, and
also keys off `isTabletop` — full logic and all `NavigationSuiteType` values in this skill's
`navigation-suite.md`.

---

# 4. `BREAKPOINTS_V1` vs `BREAKPOINTS_V2`

[SRC@HEAD `window/window-core/…/WindowSizeClass.kt`]

```kotlin
private val WIDTH_DP_BREAKPOINTS_V1 =
    listOf(0, WIDTH_DP_MEDIUM_LOWER_BOUND, WIDTH_DP_EXPANDED_LOWER_BOUND)

private val WIDTH_DP_BREAKPOINTS_V2 =
    WIDTH_DP_BREAKPOINTS_V1 +
        listOf(WIDTH_DP_LARGE_LOWER_BOUND, WIDTH_DP_EXTRA_LARGE_LOWER_BOUND)

private val HEIGHT_DP_BREAKPOINTS_V1 =
    listOf(0, HEIGHT_DP_MEDIUM_LOWER_BOUND, HEIGHT_DP_EXPANDED_LOWER_BOUND)

private val HEIGHT_DP_BREAKPOINTS_V2 = HEIGHT_DP_BREAKPOINTS_V1

private fun createBreakpointSet(
    widthBreakpoints: List<Int>,
    heightBreakpoints: List<Int>,
): Set<WindowSizeClass> {
    return widthBreakpoints
        .flatMap { widthBp ->
            heightBreakpoints.map { heightBp ->
                WindowSizeClass(minWidthDp = widthBp, minHeightDp = heightBp)
            }
        }
        .toSet()
}

@JvmField
public val BREAKPOINTS_V1: Set<WindowSizeClass> =
    createBreakpointSet(WIDTH_DP_BREAKPOINTS_V1, HEIGHT_DP_BREAKPOINTS_V1)

@JvmField
public val BREAKPOINTS_V2: Set<WindowSizeClass> =
    createBreakpointSet(WIDTH_DP_BREAKPOINTS_V2, HEIGHT_DP_BREAKPOINTS_V2)
```

| Set | Widths | Heights | Total size classes |
|---|---|---|---|
| `BREAKPOINTS_V1` | 0 / 600 / 840 | 0 / 480 / 900 | **9** |
| `BREAKPOINTS_V2` | 0 / 600 / 840 / **1200** / **1600** | 0 / 480 / 900 | **15** |

**V2 adds width breakpoints only.** `HEIGHT_DP_BREAKPOINTS_V2 = HEIGHT_DP_BREAKPOINTS_V1` — literally
the same list. There is no "Large height" and no "XL height"; do not look for
`HEIGHT_DP_LARGE_LOWER_BOUND`, it does not exist.

Consequence: if you compute a size class yourself against `BREAKPOINTS_V1`, a 1600dp-wide window
reports `minWidthDp == 840` and you can never distinguish desktop from tablet-landscape. androidify
does exactly this [REPO `androidify/core/util/.../LayoutUtils.kt`] and consequently has no
desktop-specific layout. New code should use `BREAKPOINTS_V2`, or just call
`currentWindowAdaptiveInfoV2()` which already does.

---

# 5. The `>=` trap — READ THIS BEFORE WRITING A `when`

All three predicates are `>=`. [SRC@HEAD]

```kotlin
/**
 * Returns `true` when [minWidthDp] is greater than or equal to [widthDpBreakpoint], `false`
 * otherwise. When processing a [WindowSizeClass] note that this method is order dependent.
 * Selection should go from largest to smallest breakpoints.
 */
public fun isWidthAtLeastBreakpoint(widthDpBreakpoint: Int): Boolean {
    return minWidthDp >= widthDpBreakpoint
}

public fun isHeightAtLeastBreakpoint(heightDpBreakpoint: Int): Boolean {
    return minHeightDp >= heightDpBreakpoint
}

public fun isAtLeastBreakpoint(widthDpBreakpoint: Int, heightDpBreakpoint: Int): Boolean {
    return isWidthAtLeastBreakpoint(widthDpBreakpoint) &&
        isHeightAtLeastBreakpoint(heightDpBreakpoint)
}
```

Class KDoc [SRC@HEAD], emphasis added:
> To process a `[WindowSizeClass]` use the methods `[isAtLeastBreakpoint]`,
> `[isWidthAtLeastBreakpoint]`, `[isHeightAtLeastBreakpoint]` methods. **Note these methods are order
> dependent as the smaller `[minWidthDp]` and `[minHeightDp]` would match all the breakpoints that
> are larger. Therefore when processing the selection should normally be ordered from larger to
> smaller breakpoints.**

## BROKEN — smallest first

```kotlin
// ❌ WRONG. Every window ≥600dp takes the FIRST branch.
// A 1920dp desktop window gets the "medium / single pane" layout.
val layout = when {
    wsc.isWidthAtLeastBreakpoint(WindowSizeClass.WIDTH_DP_MEDIUM_LOWER_BOUND)      -> Layout.Medium
    wsc.isWidthAtLeastBreakpoint(WindowSizeClass.WIDTH_DP_EXPANDED_LOWER_BOUND)    -> Layout.Expanded
    wsc.isWidthAtLeastBreakpoint(WindowSizeClass.WIDTH_DP_LARGE_LOWER_BOUND)       -> Layout.Large
    wsc.isWidthAtLeastBreakpoint(WindowSizeClass.WIDTH_DP_EXTRA_LARGE_LOWER_BOUND) -> Layout.XLarge
    else -> Layout.Compact
}
```

There is **no compile error and no crash**. The Large and XL branches are simply unreachable, and
Expanded is unreachable too. On a 1920dp window the app quietly renders the 600dp layout. This is the
single most likely bug in size-class code.

## CORRECT — largest first

```kotlin
// ✅ largest → smallest
val layout = when {
    wsc.isWidthAtLeastBreakpoint(WindowSizeClass.WIDTH_DP_EXTRA_LARGE_LOWER_BOUND) -> Layout.XLarge   // >= 1600
    wsc.isWidthAtLeastBreakpoint(WindowSizeClass.WIDTH_DP_LARGE_LOWER_BOUND)       -> Layout.Large    // >= 1200
    wsc.isWidthAtLeastBreakpoint(WindowSizeClass.WIDTH_DP_EXPANDED_LOWER_BOUND)    -> Layout.Expanded // >= 840
    wsc.isWidthAtLeastBreakpoint(WindowSizeClass.WIDTH_DP_MEDIUM_LOWER_BOUND)      -> Layout.Medium   // >= 600
    else -> Layout.Compact                                                                            // < 600
}
```

Same rule for height (three branches):

```kotlin
val density = when {
    wsc.isHeightAtLeastBreakpoint(WindowSizeClass.HEIGHT_DP_EXPANDED_LOWER_BOUND) -> Density.Tall   // >= 900
    wsc.isHeightAtLeastBreakpoint(WindowSizeClass.HEIGHT_DP_MEDIUM_LOWER_BOUND)   -> Density.Medium // >= 480
    else -> Density.Short                                                                            // < 480
}
```

## The escape hatches from ordering

If a `when` chain feels fragile, use exact-bucket comparison instead — `minWidthDp` is the bucket's
lower bound, so equality is a genuine bucket test:

```kotlin
// exact bucket, order-independent
when (wsc.minWidthDp) {
    WindowSizeClass.WIDTH_DP_EXTRA_LARGE_LOWER_BOUND -> { /* 1600 */ }
    WindowSizeClass.WIDTH_DP_LARGE_LOWER_BOUND       -> { /* 1200 */ }
    WindowSizeClass.WIDTH_DP_EXPANDED_LOWER_BOUND    -> { /* 840 */ }
    WindowSizeClass.WIDTH_DP_MEDIUM_LOWER_BOUND      -> { /* 600 */ }
    else                                             -> { /* 0 = compact */ }
}
```

This is exactly how androidx itself does it internally — `calculatePaneScaffoldDirective` switches on
`windowSizeClass.minWidth` (a `Dp` extension over `minWidthDp`), not on the predicates.

**Caveat:** exact comparison depends on which breakpoint set produced the size class. If the value
came from `BREAKPOINTS_V1`, `minWidthDp` will never be 1200 or 1600, and the `else` branch will pick
up compact *and* nothing else — but 840 will absorb everything wide. Use exact comparison only when
you know the class came from V2 (i.e. from `currentWindowAdaptiveInfoV2()`).

The single-boolean pattern is also legitimate when the app really only has two layouts
[REPO `compose-samples/JetNews/.../JetnewsApp.kt`]:

```kotlin
import androidx.window.core.layout.WindowSizeClass
// ...
val windowSizeClass = currentWindowAdaptiveInfo().windowSizeClass
val isExpandedScreen = remember(windowSizeClass) {
    windowSizeClass.isWidthAtLeastBreakpoint(WindowSizeClass.WIDTH_DP_EXPANDED_LOWER_BOUND)
}
```

(JetNews shown verbatim; it uses the now-deprecated `currentWindowAdaptiveInfo()` — write
`currentWindowAdaptiveInfoV2()` in new code.) One boolean threaded down is fine for a two-column
reader. It is not fine once you have three or more layouts.

---

# 6. `WindowAdaptiveInfo`

[API-1.3.0]
```
@androidx.compose.runtime.Immutable public final class WindowAdaptiveInfo {
    ctor public WindowAdaptiveInfo(androidx.window.core.layout.WindowSizeClass windowSizeClass, androidx.compose.material3.adaptive.Posture windowPosture);
    method @InaccessibleFromKotlin public androidx.compose.material3.adaptive.Posture getWindowPosture();
    method @InaccessibleFromKotlin public androidx.window.core.layout.WindowSizeClass getWindowSizeClass();
    property public androidx.compose.material3.adaptive.Posture windowPosture;
    property public androidx.window.core.layout.WindowSizeClass windowSizeClass;
}
```

[SRC@HEAD `adaptive/…/WindowAdaptiveInfo.kt`]
```kotlin
@Immutable
public class WindowAdaptiveInfo(
    public val windowSizeClass: WindowSizeClass,
    public val windowPosture: Posture,
)
```

**Two fields, nothing else.** `equals`/`hashCode`/`toString` are implemented, so it is safe as a
`remember` key and as a `@Composable` parameter default.

> There is **no** type named `WindowPosture`. The property is `windowPosture: Posture`.

It carries **no** window size in dp. If you need raw dp, read `LocalWindowInfo.current.containerDpSize`
(see §7).

---

# 7. `currentWindowAdaptiveInfoV2()` — the entry point

[API-1.3.0] `WindowAdaptiveInfoKt`
```
method @KotlinOnly @androidx.compose.runtime.Composable public static androidx.compose.material3.adaptive.WindowAdaptiveInfo currentWindowAdaptiveInfoV2();
method @KotlinOnly @Deprecated @androidx.compose.runtime.Composable public static androidx.compose.material3.adaptive.WindowAdaptiveInfo currentWindowAdaptiveInfo(optional boolean supportLargeAndXLargeWidth);
method @KotlinOnly @Deprecated @androidx.compose.runtime.Composable public static androidx.compose.ui.unit.DpSize currentWindowDpSize();
method @KotlinOnly @Deprecated @androidx.compose.runtime.Composable public static androidx.compose.ui.unit.IntSize currentWindowSize();
```

[SRC@HEAD]
```kotlin
@Composable
@Suppress("DEPRECATION")
public fun currentWindowAdaptiveInfoV2(): WindowAdaptiveInfo =
    currentWindowAdaptiveInfo(supportLargeAndXLargeWidth = true)

@Deprecated(
    message = "Please use V2 version of this function to support L and XL width size classes.",
    replaceWith = ReplaceWith("currentWindowAdaptiveInfoV2"),
    DeprecationLevel.WARNING,
)
@Composable
public fun currentWindowAdaptiveInfo(
    supportLargeAndXLargeWidth: Boolean = false
): WindowAdaptiveInfo {
    // Workaround (b/358626778): Directly using WindowInfo.containerDpSize breaks tests based on
    //   DeviceConfigurationOverride.ForcedSize. Those clients need to migrate to
    //   DeviceConfigurationOverride.WindowSize when its available.
    val windowSize =
        with(LocalDensity.current) { LocalWindowInfo.current.containerSize.toSize().toDpSize() }
    return WindowAdaptiveInfo(
        windowSizeClass =
            if (supportLargeAndXLargeWidth) {
                WindowSizeClass.computeFromDpSizeV2(windowSize)
            } else {
                WindowSizeClass.computeFromDpSize(windowSize)
            },
        windowPosture = calculatePosture(),
    )
}
```

**Facts that matter:**

- `currentWindowAdaptiveInfo()` is **`@Deprecated` at WARNING level in 1.3.0**. The Android zero-arg
  overload is deprecated at **HIDDEN** level.
- Its default is `supportLargeAndXLargeWidth = false`, so it **clamps everything ≥840dp to Expanded**.
  You silently lose Large/XL behaviour: no 3-partition directive, no 412dp preferred width.
- `currentWindowAdaptiveInfoV2()` is exactly `currentWindowAdaptiveInfo(true)`.
- Window size comes from `LocalWindowInfo.current.containerSize` converted with `LocalDensity`, **not**
  `containerDpSize` — a deliberate workaround for b/358626778.
- It is a plain `@Composable` function, **not** a state holder: it recomposes automatically on window
  resize, fold, rotation, and multi-window drag. Do not cache it across configuration changes.
- No opt-in required.

Canonical use:
```kotlin
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfoV2
import androidx.window.core.layout.WindowSizeClass

val info = currentWindowAdaptiveInfoV2()
val wsc: WindowSizeClass = info.windowSizeClass
val posture = info.windowPosture
```

### Deprecated size accessors

```kotlin
@Deprecated(
    message = "Going to be removed in the next version. Prefer LocalWindowInfo instead",
    replaceWith = ReplaceWith("LocalWindowInfo.current.containerDpSize"),
    DeprecationLevel.WARNING,
)
@ExperimentalMaterial3AdaptiveApi
@Composable
public fun currentWindowDpSize(): DpSize = LocalWindowInfo.current.containerDpSize

@Deprecated(
    message = "Prefer LocalWindowInfo instead",
    replaceWith = ReplaceWith("LocalWindowInfo.current.containerSize"),
    DeprecationLevel.WARNING,
)
@Composable
public fun currentWindowSize(): IntSize = LocalWindowInfo.current.containerSize
```

| Old | New |
|---|---|
| `currentWindowSize()` | `LocalWindowInfo.current.containerSize` (`IntSize`, px) |
| `currentWindowDpSize()` | `LocalWindowInfo.current.containerDpSize` (`DpSize`) |

### Hoist it as a parameter

The pattern that makes screenshot tests and previews possible — inject the info instead of reading
it [REPO `nowinandroid/core/designsystem/.../Navigation.kt`]:

```kotlin
@Composable
fun MyAppScaffold(
    modifier: Modifier = Modifier,
    windowAdaptiveInfo: WindowAdaptiveInfo = currentWindowAdaptiveInfoV2(),
    // ...
) { /* ... */ }
```

nowinandroid's `NiaAppScreenSizesScreenshotTests.kt` works precisely because of this. Adopt it on
every composable that branches on size class.

---

# 8. Deprecation table — everything deprecated in 1.3.0

Full list from the version-delta pass. Applies to `material3-adaptive` 1.3.0 and the `window-core`
version it resolves against.

| Symbol | Deprecation level | Replacement |
|---|---|---|
| `currentWindowAdaptiveInfo(supportLargeAndXLargeWidth)` | WARNING | **`currentWindowAdaptiveInfoV2()`** |
| `currentWindowAdaptiveInfo()` (Android 0-arg) | HIDDEN | **`currentWindowAdaptiveInfoV2()`** |
| `currentWindowSize()` | WARNING | `LocalWindowInfo.current.containerSize` |
| `currentWindowDpSize()` | WARNING | `LocalWindowInfo.current.containerDpSize` |
| `WindowSizeClass.compute(Float, Float)` (window-core) | plain | `BREAKPOINTS_V1.computeWindowSizeClass(widthDp = …, heightDp = …)` |
| `WindowSizeClass.windowWidthSizeClass` / `.windowHeightSizeClass` | plain | `isWidthAtLeastBreakpoint` / `isHeightAtLeastBreakpoint` |
| `androidx.window.core.layout.WindowWidthSizeClass` / `WindowHeightSizeClass` | class-level | breakpoint constants + predicates |
| `AdaptStrategy.adapt()` | plain | inspect the strategy type directly |
| `defaultDragHandleSemantics(PaneExpansionState)` | plain | default semantics of `Modifier.paneExpansionDraggable(...)` |
| `rememberPaneExpansionState(...)` without `consumeDragDelta` | HIDDEN | overload with `consumeDragDelta` |
| `PaneScaffoldDirective.copy(...)` without `defaultPanePreferredHeight` | HIDDEN | the new `copy` |
| `AdaptStrategy.Levitate(alignment, scrim)` 2-arg ctor | HIDDEN | 3-arg with `dragToResizeState` |
| `PaneAdaptedValue.Levitated(alignment, scrim)` 2-arg ctor | HIDDEN | 3-arg |
| `NavigableListDetailPaneScaffold-WahKDqQ` | plain (bytecode) | current arity |
| `NavigationSuiteDefaults.colors-5tl4gsc` | bytecode | current `colors(...)` |
| `NavigationSuiteScaffold-Fin9vsw` / `-QXLVvyo` / `-Ynpp4HM` / `-thDv9LQ` | bytecode | current arities |

The deprecated `compute` still exists for binary compat and is **V1-only** — note it has no Large/XL
branches at all [SRC@HEAD]:

```kotlin
@JvmStatic
@Deprecated(
    "Use computeWindowSizeClass instead.",
    ReplaceWith(
        "BREAKPOINTS_V1.computeWindowSizeClass(widthDp = dpWidth, heightDp = dpHeight)",
        "androidx.window.core.layout.computeWindowSizeClass",
    ),
)
public fun compute(dpWidth: Float, dpHeight: Float): WindowSizeClass {
    val widthDp =
        when {
            dpWidth >= WIDTH_DP_EXPANDED_LOWER_BOUND -> WIDTH_DP_EXPANDED_LOWER_BOUND
            dpWidth >= WIDTH_DP_MEDIUM_LOWER_BOUND -> WIDTH_DP_MEDIUM_LOWER_BOUND
            else -> 0
        }
    val heightDp =
        when {
            dpHeight >= HEIGHT_DP_EXPANDED_LOWER_BOUND -> HEIGHT_DP_EXPANDED_LOWER_BOUND
            dpHeight >= HEIGHT_DP_MEDIUM_LOWER_BOUND -> HEIGHT_DP_MEDIUM_LOWER_BOUND
            else -> 0
        }
    return WindowSizeClass(widthDp, heightDp)
}
```

Note it is itself written largest→smallest. Follow the same shape in your own code.

**Also gone at HEAD (present in 1.3.0, do not build on them):**
`ExperimentalMaterial3AdaptiveComponentOverrideApi`, `AnimatedPaneOverride`,
`AnimatedPaneOverrideScope`, `LocalAnimatedPaneOverride`, `ThreePaneScaffoldOverride`,
`ThreePaneScaffoldOverrideScope`, `LocalThreePaneScaffoldOverride`.

---

# 9. The legacy API — `androidx.compose.material3.windowsizeclass`

Artifact `material3-window-size-class`, package `androidx.compose.material3.windowsizeclass`.
Superseded by `androidx.window.core.layout.WindowSizeClass`. Still
`@ExperimentalMaterial3WindowSizeClassApi` after all these years. **Compact/Medium/Expanded only** —
no Large, no XL.

You will meet it in `compose-samples/Reply`, in `JetNews`, and in any app written before ~2024.

### API [API `material3-window-size-class/api/current.txt`], verbatim

```
package androidx.compose.material3.windowsizeclass {

  @SuppressCompatibility public final class AndroidWindowSizeClass_androidKt {
    method @KotlinOnly @SuppressCompatibility @androidx.compose.material3.windowsizeclass.ExperimentalMaterial3WindowSizeClassApi @androidx.compose.runtime.Composable public static androidx.compose.material3.windowsizeclass.WindowSizeClass calculateWindowSizeClass(android.app.Activity activity);
  }

  @androidx.compose.runtime.Immutable public final class WindowSizeClass {
    property public androidx.compose.material3.windowsizeclass.WindowHeightSizeClass heightSizeClass;
    property public androidx.compose.material3.windowsizeclass.WindowWidthSizeClass widthSizeClass;
    field public static final androidx.compose.material3.windowsizeclass.WindowSizeClass.Companion Companion;
  }

  public static final class WindowSizeClass.Companion {
    method @KotlinOnly @SuppressCompatibility @androidx.compose.material3.windowsizeclass.ExperimentalMaterial3WindowSizeClassApi public androidx.compose.material3.windowsizeclass.WindowSizeClass calculateFromSize(androidx.compose.ui.unit.DpSize size, optional java.util.Set<androidx.compose.material3.windowsizeclass.WindowWidthSizeClass> supportedWidthSizeClasses, optional java.util.Set<androidx.compose.material3.windowsizeclass.WindowHeightSizeClass> supportedHeightSizeClasses);
  }

  @androidx.compose.runtime.Immutable @kotlin.jvm.JvmInline public final value class WindowWidthSizeClass implements java.lang.Comparable<androidx.compose.material3.windowsizeclass.WindowWidthSizeClass> {
    method @KotlinOnly public operator int compareTo(androidx.compose.material3.windowsizeclass.WindowWidthSizeClass other);
    field public static final androidx.compose.material3.windowsizeclass.WindowWidthSizeClass.Companion Companion;
  }

  public static final class WindowWidthSizeClass.Companion {
    property public java.util.Set<androidx.compose.material3.windowsizeclass.WindowWidthSizeClass> AllSizeClasses;
    property public androidx.compose.material3.windowsizeclass.WindowWidthSizeClass Compact;
    property public java.util.Set<androidx.compose.material3.windowsizeclass.WindowWidthSizeClass> DefaultSizeClasses;
    property public androidx.compose.material3.windowsizeclass.WindowWidthSizeClass Expanded;
    property public androidx.compose.material3.windowsizeclass.WindowWidthSizeClass Medium;
  }

  // WindowHeightSizeClass + WindowHeightSizeClass.Companion are structurally identical
  // to the WindowWidthSizeClass pair above: value class over Comparable, with
  // AllSizeClasses / DefaultSizeClasses / Compact / Medium / Expanded on the Companion.

}
```

### THREE different `WindowSizeClass` types exist. Do not confuse them.

| # | Fully-qualified name | Status | Shape |
|---|---|---|---|
| 1 | `androidx.window.core.layout.WindowSizeClass` | **current** — use this | breakpoint-based, `isWidthAtLeastBreakpoint(Int)` |
| 2 | `androidx.compose.material3.windowsizeclass.WindowSizeClass` | legacy Compose wrapper | `widthSizeClass` / `heightSizeClass` value classes, `Comparable` |
| 3 | `androidx.window.core.layout.WindowWidthSizeClass` / `WindowHeightSizeClass` | deprecated enums | reachable only via the deprecated `wsc.windowWidthSizeClass` getter |

Type 2 and type 3 have **identically-named** members (`Compact`/`Medium`/`Expanded`) in different
packages. If the code compiles but the behaviour is wrong, check the import first.

### Migrating off it

| Legacy | Current |
|---|---|
| `calculateWindowSizeClass(activity)` | `currentWindowAdaptiveInfoV2().windowSizeClass` |
| `wsc.widthSizeClass == WindowWidthSizeClass.Expanded` | `wsc.isWidthAtLeastBreakpoint(WindowSizeClass.WIDTH_DP_EXPANDED_LOWER_BOUND)` — note this is now `>=`, not `==` |
| `wsc.widthSizeClass >= WindowWidthSizeClass.Medium` | `wsc.isWidthAtLeastBreakpoint(WindowSizeClass.WIDTH_DP_MEDIUM_LOWER_BOUND)` |
| `wsc.heightSizeClass == WindowHeightSizeClass.Compact` | `!wsc.isHeightAtLeastBreakpoint(WindowSizeClass.HEIGHT_DP_MEDIUM_LOWER_BOUND)` |
| `WindowSizeClass.calculateFromSize(dpSize)` | `WindowSizeClass.BREAKPOINTS_V2.computeWindowSizeClass(widthDp = …, heightDp = …)` |
| passing the `Activity` down to get a size class | drop it; the current API needs no `Activity` |

**Semantic trap in the migration:** the legacy `widthSizeClass == Expanded` is an *equality* test on
a three-valued enum; `isWidthAtLeastBreakpoint(840)` is `>=`. On the legacy API, Expanded meant
"≥840dp" because Expanded was the top bucket. On the current API, "≥840" is true for Large and XL
too — which is usually what you wanted, but check any code that had special "expanded but not
larger" handling.

Reply's real-world hybrid, worth studying because it shows both worlds at once
[REPO `compose-samples/Reply/.../ReplyNavigationComponents.kt`]:

```kotlin
private fun WindowSizeClass.isCompact() = !isWidthAtLeastBreakpoint(WindowSizeClass.WIDTH_DP_MEDIUM_LOWER_BOUND) ||
    !isHeightAtLeastBreakpoint(WindowSizeClass.HEIGHT_DP_MEDIUM_LOWER_BOUND)
```

Reply's `isCompact()` = "compact in *either* dimension", which is stricter than the library's
per-axis checks — a legitimate app-level policy. But note Reply also gates its drawer on
`isWidthAtLeastBreakpoint(WIDTH_DP_EXPANDED_LOWER_BOUND) && windowSize.width >= 1200.dp`, a manual
1200dp check that predates `WIDTH_DP_LARGE_LOWER_BOUND` being usable there. In new code write
`isWidthAtLeastBreakpoint(WindowSizeClass.WIDTH_DP_LARGE_LOWER_BOUND)` instead, and drop the
`currentWindowSize()` call entirely.

---

# 10. How the adaptive library maps dp → size class

When behaviour surprises you, this is the code that ran.
[SRC@HEAD `adaptive/…/WindowSizeClassHelper.kt`]

```kotlin
@Suppress("PrimitiveInCollection")
private object DpWidthSizeClasses {
    val Compact = 0.dp
    val Medium = WIDTH_DP_MEDIUM_LOWER_BOUND.dp
    val Expanded = WIDTH_DP_EXPANDED_LOWER_BOUND.dp
    val Large = WIDTH_DP_LARGE_LOWER_BOUND.dp
    val ExtraLarge = WIDTH_DP_EXTRA_LARGE_LOWER_BOUND.dp

    val Default = setOf(Compact, Medium, Expanded)
    val DefaultV2 = setOf(Compact, Medium, Expanded, Large, ExtraLarge)
}

@Suppress("PrimitiveInCollection")
private object DpHeightSizeClasses {
    val Compact = 0.dp
    val Medium = HEIGHT_DP_MEDIUM_LOWER_BOUND.dp
    val Expanded = HEIGHT_DP_EXPANDED_LOWER_BOUND.dp
    val Default = setOf(Compact, Medium, Expanded)
}

@Suppress("PrimitiveInCollection")
internal fun WindowSizeClass.Companion.computeFromDpSize(
    windowSize: DpSize,
    supportedWidthSizeClasses: Set<Dp> = DpWidthSizeClasses.Default,
    supportedHeightSizeClasses: Set<Dp> = DpHeightSizeClasses.Default,
) =
    WindowSizeClass(
        supportedWidthSizeClasses.filter { windowSize.width >= it }.maxOf { it.value },
        supportedHeightSizeClasses.filter { windowSize.height >= it }.maxOf { it.value },
    )

@Suppress("PrimitiveInCollection")
internal fun WindowSizeClass.Companion.computeFromDpSizeV2(
    windowSize: DpSize,
    supportedWidthSizeClasses: Set<Dp> = DpWidthSizeClasses.DefaultV2,
    supportedHeightSizeClasses: Set<Dp> = DpHeightSizeClasses.Default,
) = computeFromDpSize(windowSize, supportedWidthSizeClasses, supportedHeightSizeClasses)
```

Read the algorithm: *filter the supported lower bounds to those the window meets, take the max.*
That is why `currentWindowAdaptiveInfo()` (V1 set) reports `minWidthDp == 840` for a 1920dp window —
1200 and 1600 simply are not in the candidate set.

`adaptive-layout` keeps its **own internal mirror**, used by `calculatePaneScaffoldDirective`
[SRC@HEAD `adaptive-layout/…/WindowSizeClassHelper.kt`]:

```kotlin
internal val WindowSizeClass.Companion.WidthSizeClasses
    get() = DpWidthSizeClasses

internal val WindowSizeClass.Companion.HeightSizeClasses
    get() = DpHeightSizeClasses

internal object DpWidthSizeClasses {
    val Compact = 0.dp
    val Medium = WIDTH_DP_MEDIUM_LOWER_BOUND.dp
    val Expanded = WIDTH_DP_EXPANDED_LOWER_BOUND.dp
    // TODO(conradchen): Move to window-core definition when it goes to 1.5 stable
    val Large = 1200.dp
    // TODO(conradchen): Move to window-core definition when it goes to 1.5 stable
    val ExtraLarge = 1600.dp
}

internal val WindowSizeClass.minWidth
    get() = minWidthDp.dp

internal val WindowSizeClass.minHeight
    get() = minHeightDp.dp
```

**`adaptive-layout` hard-codes 1200/1600** rather than importing the `window-core` constants (see the
`TODO(conradchen)`), because it targets a `window-core` version where those constants may be absent.
Practical effect: Large/XL directive behaviour is stable even on an older `window-core`, but the
`window-core` constants may not resolve in *your* code if your `window-core` pin is old. If
`WIDTH_DP_LARGE_LOWER_BOUND` is unresolved, bump `androidx.window:window-core` (snippets pins
`1.5.1`).

`WindowSizeClass.minWidth` / `.minHeight` (the `Dp` extensions) are **internal** — you cannot call
them. Use `minWidthDp` / `minHeightDp` (`Int`) and `.dp` them yourself if you need `Dp`.

---

# 11. Testing and previewing size classes

## 11.1 Construct one directly

`WindowSizeClass` has two public constructors, so faking one in a test or preview is trivial and
needs no `Activity`, no emulator, and no opt-in:

```kotlin
import androidx.window.core.layout.WindowSizeClass

// exact bucket — the Int ctor takes LOWER BOUNDS
val expandedTall = WindowSizeClass(minWidthDp = 840, minHeightDp = 900)
val compactShort = WindowSizeClass(minWidthDp = 0,   minHeightDp = 0)
val desktop      = WindowSizeClass(
    minWidthDp = WindowSizeClass.WIDTH_DP_EXTRA_LARGE_LOWER_BOUND,
    minHeightDp = WindowSizeClass.HEIGHT_DP_EXPANDED_LOWER_BOUND,
)
```

Pass real dp through the selector when you want the bucketing logic exercised too:

```kotlin
import androidx.window.core.layout.WindowSizeClass
import androidx.window.core.layout.computeWindowSizeClass

val wsc = WindowSizeClass.BREAKPOINTS_V2.computeWindowSizeClass(widthDp = 1280, heightDp = 800)
// -> minWidthDp == 1200, minHeightDp == 480
```

`computeWindowSizeClassPreferHeight(set, widthDp, heightDp)` is the third selector, for when height
should win a tie. `WindowSizeClassGridCreator.createGridWindowSizeClassSet(widths, heights)` builds a
custom breakpoint grid if you have app-specific breakpoints.

## 11.2 Inject a `WindowAdaptiveInfo`

`WindowAdaptiveInfo`'s constructor is public, so the whole thing is fakeable:

```kotlin
import androidx.compose.material3.adaptive.Posture
import androidx.compose.material3.adaptive.WindowAdaptiveInfo

val fakeInfo = WindowAdaptiveInfo(
    windowSizeClass = WindowSizeClass(minWidthDp = 840, minHeightDp = 900),
    windowPosture = Posture(),          // no hinges, not tabletop
)
```

This only reaches your code if your composables **take it as a parameter**. Hoist it (§7) — that is
exactly the mechanism behind nowinandroid's `NiaAppScreenSizesScreenshotTests.kt`.

```kotlin
@Preview(widthDp = 1280, heightDp = 800)
@Composable
private fun MyScreenExpandedPreview() {
    MaterialExpressiveTheme {
        MyScreen(windowAdaptiveInfo = fakeInfo)
    }
}
```

## 11.3 Preview / test tooling [DOC]

| Tool | Use |
|---|---|
| `@PreviewScreenSizes` | multi-size preview matrix in one annotation |
| `@PreviewFontScale`, `@PreviewLightDark` | the other two axes worth covering |
| `DeviceConfigurationOverride` | test multiple configurations in one instrumented test |
| Host-side screenshot tests | verify appearance across display sizes |
| Emulators | Resizable, Pixel Fold, Pixel Tablet, Desktop emulators |
| Remote Device Streaming | real Pixel and Samsung hardware |

**`DeviceConfigurationOverride.ForcedSize` caveat.** The androidx source itself carries this comment
in `currentWindowAdaptiveInfo` [SRC@HEAD]:

```
// Workaround (b/358626778): Directly using WindowInfo.containerDpSize breaks tests based on
//   DeviceConfigurationOverride.ForcedSize. Those clients need to migrate to
//   DeviceConfigurationOverride.WindowSize when its available.
```

So `ForcedSize` **does** currently work with `currentWindowAdaptiveInfoV2()` — because of that
workaround, not despite it. If a future release removes the workaround, migrate to
`DeviceConfigurationOverride.WindowSize`. Prefer parameter injection (§11.2) over
`DeviceConfigurationOverride` where you can; it is faster and does not depend on this workaround.

## 11.4 Which sizes to cover

Cover at minimum one window per bucket, and one per *combination* that changes layout:

| Preview `widthDp` × `heightDp` | Bucket | Why |
|---|---|---|
| 411 × 891 | compact × medium | baseline phone portrait |
| 891 × 411 | expanded × compact | phone landscape — the "impractical for two panes" case [DOC] |
| 700 × 1000 | medium × expanded | portrait tablet: single pane **and** `maxVerticalPartitions = 2` |
| 1024 × 800 | expanded × medium | tablet landscape: two panes |
| 1280 × 800 | large × medium | 3 partitions, 412dp panes |
| 1920 × 1200 | XL × expanded | desktop |

> [DOC] "Account for height in scenarios like phones/open flippables in landscape (medium width +
> compact height = impractical for two-pane layouts)."

> [DOC] Optimization order: "Start with compact layout, optimize for expanded width (most space),
> then design medium width layout if needed."

---

# 12. Alternatives: `derivedMediaQuery`

`androidx.compose.ui.derivedMediaQuery` / `UiMediaScope` is a newer, component-level way to ask about
the window. Full treatment lives in the **m3-expressive** skill's
`references/modern-compose-idioms.md` §2.

Summary of the trade-off:

| | `WindowSizeClass` | `derivedMediaQuery` |
|---|---|---|
| Status | **stable**; `material3-adaptive` went stable at 1.3.0 | **experimental** — needs `@OptIn(ExperimentalMediaQueryApi::class, ExperimentalComposeUiApi::class)` **and** `ComposeUiFlags.isMediaQueryIntegrationEnabled = true` before first composition |
| Granularity | app/screen level; usually prop-drilled | any leaf composable can query the window itself |
| Feeds the scaffolds | **yes** — `WindowAdaptiveInfo` is what `calculatePaneScaffoldDirective` and `NavigationSuiteScaffold` consume | no |
| Failure mode | none silent, if you order branches correctly | forgetting the runtime flag makes the API silently not integrate |

**Use `WindowSizeClass` (via `currentWindowAdaptiveInfoV2()`) as the default.** It is the conservative
choice: stable, it is the only thing the pane scaffolds and the nav suite actually read, and it
cannot silently no-op.

**Reach for `derivedMediaQuery` only when** a leaf component genuinely needs its own window knowledge
and prop-drilling a size class is the only alternative — e.g. a card that picks `displaySmall` vs
`titleLarge` by window width without a size-class prop.

**Do not mix them** in one screen. If you have (or will add) a pane scaffold or
`NavigationSuiteScaffold`, those consume `WindowAdaptiveInfo`; adding `derivedMediaQuery` gives you a
second source of truth that disagrees at the boundary. Also note that jetpacker's `derivedMediaQuery`
thresholds (600dp / 1200dp) are **not** M3 breakpoints — Expanded starts at 840dp — so layouts built
on them are not size-class-conformant by construction.

---

# 13. Anti-patterns

Everything in this section produces code that compiles, runs, and is wrong.

## 13.1 `Configuration.screenWidthDp` branching

```kotlin
// ❌ never
val config = LocalConfiguration.current
if (config.screenWidthDp >= 600) { TabletLayout() } else { PhoneLayout() }
```

Why it is wrong:
- It is the **screen**, not the app **window**. In split-screen, freeform, or desktop windowing your
  window is a fraction of the screen and this reads the wrong number.
- It does not recompose reliably on window drag-resize the way `currentWindowAdaptiveInfoV2()` does.
- It hard-codes a breakpoint that will drift from the M3 table.

Use `currentWindowAdaptiveInfoV2().windowSizeClass`. If you truly need raw dp, use
`LocalWindowInfo.current.containerDpSize` — window, not screen.

## 13.2 Orientation checks

```kotlin
// ❌ never
if (LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE) { … }
```

[DOC] lists "Hardcoding orientation assumptions" and "Locking screen rotation" under **Avoid**.
Orientation tells you nothing useful: a portrait tablet is wider than a landscape phone. Branch on
width and height buckets, which is what you actually meant.

## 13.3 `isTablet` / `isPhone` booleans

[DOC], verbatim:
> "**Not Device-Based**: Window size classes are determined by available window space, not physical
> device type — window size class is NOT for 'isTablet'-type logic"

> "**Dynamic Nature**: Window size classes can change during app lifetime due to device orientation
> changes, multitasking, folding/unfolding, or window resizing"

Any `val isTablet = …` computed once and cached is wrong twice over: wrong on a resized window, and
wrong on a foldable that changes class mid-session.

A single derived boolean is fine when it is *derived from the size class* and *recomputed on
recomposition* — `JetNews`'s `isExpandedScreen` (§5) is the acceptable form. `isTablet` computed from
`Build`, screen size, or a `sw600dp` resource flag is not.

## 13.4 Resource qualifiers for layout decisions

`res/values-sw600dp/`, `res/layout-land/`, `bool/is_tablet.xml` — do not drive Compose layout
decisions from these.

- They resolve against the **configuration**, which in multi-window and desktop windowing does not
  match the window your Compose tree occupies.
- They fragment the layout decision across the resource system and the composition, so the two can
  disagree.
- They cannot express posture at all.

Resource qualifiers remain correct for what they are for — density-specific drawables, locale
strings. They are not a layout-decision mechanism in Compose.

## 13.5 Smallest-first `when` chains

Covered in §5. Restated here because it is the one that ships.

## 13.6 Assuming three width buckets

```kotlin
// ❌ silently wrong on desktop and large tablets
if (wsc.isWidthAtLeastBreakpoint(WindowSizeClass.WIDTH_DP_EXPANDED_LOWER_BOUND)) {
    TwoPaneLayout()   // a 1920dp window deserves three panes
}
```

If you deliberately want "expanded or wider → two panes", say so in a comment. If you did not think
about 1200 and 1600, you have a bug on desktop.

## 13.7 Using `currentWindowAdaptiveInfo()` because a tutorial did

Every pre-1.3.0 tutorial, and several current samples (`Reply`, `JetNews`, `nowinandroid`), call the
deprecated function. Copying them costs you Large/XL. Write `currentWindowAdaptiveInfoV2()`.

---

# 14. Cheat sheet

```kotlin
// ---- Dependencies -----------------------------------------------------------
implementation("androidx.compose.material3.adaptive:adaptive:1.3.0")
implementation("androidx.compose.material3.adaptive:adaptive-layout:1.3.0")
implementation("androidx.compose.material3.adaptive:adaptive-navigation:1.3.0")
implementation("androidx.compose.material3.adaptive:adaptive-navigation3:1.3.0")
implementation("androidx.compose.material3:material3-adaptive-navigation-suite:1.5.0-alpha26")
// ^ different group, different version train

// ---- Imports ----------------------------------------------------------------
import androidx.compose.material3.adaptive.WindowAdaptiveInfo
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfoV2
import androidx.window.core.layout.WindowSizeClass

// ---- Read it ----------------------------------------------------------------
val info: WindowAdaptiveInfo = currentWindowAdaptiveInfoV2()
val wsc: WindowSizeClass = info.windowSizeClass
val posture = info.windowPosture

// ---- Branch on it: LARGEST → SMALLEST ---------------------------------------
when {
    wsc.isWidthAtLeastBreakpoint(WindowSizeClass.WIDTH_DP_EXTRA_LARGE_LOWER_BOUND) -> {} // >= 1600
    wsc.isWidthAtLeastBreakpoint(WindowSizeClass.WIDTH_DP_LARGE_LOWER_BOUND)       -> {} // >= 1200
    wsc.isWidthAtLeastBreakpoint(WindowSizeClass.WIDTH_DP_EXPANDED_LOWER_BOUND)    -> {} // >= 840
    wsc.isWidthAtLeastBreakpoint(WindowSizeClass.WIDTH_DP_MEDIUM_LOWER_BOUND)      -> {} // >= 600
    else -> {}                                                                          // compact
}

when {
    wsc.isHeightAtLeastBreakpoint(WindowSizeClass.HEIGHT_DP_EXPANDED_LOWER_BOUND) -> {} // >= 900
    wsc.isHeightAtLeastBreakpoint(WindowSizeClass.HEIGHT_DP_MEDIUM_LOWER_BOUND)   -> {} // >= 480
    else -> {}                                                                          // compact
}

// both axes at once
if (wsc.isAtLeastBreakpoint(
        WindowSizeClass.WIDTH_DP_EXPANDED_LOWER_BOUND,
        WindowSizeClass.HEIGHT_DP_MEDIUM_LOWER_BOUND,
)) { /* wide AND not-short */ }

// ---- Raw window size (deprecated helpers are gone) --------------------------
val dpSize = LocalWindowInfo.current.containerDpSize   // NOT currentWindowDpSize()
val pxSize = LocalWindowInfo.current.containerSize     // NOT currentWindowSize()

// ---- Fake one for a test/preview --------------------------------------------
val fake = WindowAdaptiveInfo(
    windowSizeClass = WindowSizeClass(minWidthDp = 1200, minHeightDp = 900),
    windowPosture = Posture(),
)

// ---- Compute from dp yourself ------------------------------------------------
import androidx.window.core.layout.computeWindowSizeClass
val computed = WindowSizeClass.BREAKPOINTS_V2.computeWindowSizeClass(widthDp = 1280, heightDp = 800)
```

### Constants at a glance
```
WIDTH_DP_MEDIUM_LOWER_BOUND       = 600
WIDTH_DP_EXPANDED_LOWER_BOUND     = 840
WIDTH_DP_LARGE_LOWER_BOUND        = 1200
WIDTH_DP_EXTRA_LARGE_LOWER_BOUND  = 1600
HEIGHT_DP_MEDIUM_LOWER_BOUND      = 480
HEIGHT_DP_EXPANDED_LOWER_BOUND    = 900

BREAKPOINTS_V1 = 3 widths × 3 heights = 9 classes
BREAKPOINTS_V2 = 5 widths × 3 heights = 15 classes   (heights identical to V1)

defaultPanePreferredWidth   = 360.dp  (Compact/Medium/Expanded), 412.dp (Large/XL)   [internal]
defaultPanePreferredHeight  = 420.dp  (all buckets)                                  [internal]
partition spacer, when >1 partition = 24.dp
```

### Naming reference — things that do NOT exist

| Do not write | Because |
|---|---|
| `containsWidthDp(...)` / `containsHeightDp(...)` | never existed on any `WindowSizeClass` |
| `HEIGHT_DP_LARGE_LOWER_BOUND` / `HEIGHT_DP_EXTRA_LARGE_LOWER_BOUND` | height has 3 buckets only |
| `WindowPosture` | the type is `Posture`; the *property* is `windowPosture` |
| `wsc.minWidth` / `wsc.minHeight` | those `Dp` extensions are `internal`; use `minWidthDp` / `minHeightDp` (`Int`) |
| `PaneScaffoldDirective.DefaultPreferredWidth` | `internal`; the value is 360.dp |
| `currentWindowAdaptiveInfo()` | deprecated → `currentWindowAdaptiveInfoV2()` |

## Cross-references

- Posture, hinges, foldables, Android 16 resizability → `foldables-and-posture.md` (this skill)
- `derivedMediaQuery` in full → m3-expressive skill, `references/modern-compose-idioms.md` §2
- Nav-suite type selection and Expressive nav containers → m3-expressive-navigation skill

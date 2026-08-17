# Pane scaffolds — roles, directives, adaptation, `AnimatedPane`

`ListDetailPaneScaffold`, `SupportingPaneScaffold`, `PaneScaffoldDirective`, `AdaptStrategy`,
`ThreePaneScaffoldValue`, `AnimatedPane`, scopes, motion, margins, levitation.

Navigators, back behaviour, predictive back, pane expansion / drag-to-resize →
`pane-navigation-and-expansion.md`.

**Target:** `androidx.compose.material3.adaptive:*` **1.3.0 stable** (2026-08-12);
`androidx.compose.material3:material3` **1.5.0-alpha26**.

| Tag | Meaning |
| --- | --- |
| `[API-1.3.0]` | Verbatim from the frozen metalava file `api/1.3.0-rc01.txt` — byte-identical to shipped 1.3.0 |
| `[SRC@HEAD]` | Verbatim Kotlin from androidx-main HEAD (commit `360e8cba7ae6fa4fe8059d993f75faefb32f51b8`, 2026-08-14). HEAD is **post**-1.3.0 — deltas flagged inline |
| `[DOC]` | developer.android.com / m3.material.io, fetched 2026-08-14 |
| `[REPO]` | Verbatim from a cloned repo, path given |
| `UNVERIFIED` | Stated but not confirmed against a primary source |

KDoc inside code blocks is elided with `...` where prose below covers it. Signatures and
implementation bodies are verbatim and unelided.
Checkout: `/root/work/repos/androidx-m3/compose/material3/adaptive/adaptive-layout/`.

---

## 1. THE ROLE TRAP — read this first

Every pane API keys on `ThreePaneScaffoldRole.Primary` / `.Secondary` / `.Tertiary`. The semantic
names (`List`, `Detail`, `Main`, `Supporting`) are **aliases** over those three and map
**differently per scaffold**. Getting it backwards silently reverses your adapt strategies, your
pane order and your `scaffoldValue[role]` reads — it compiles and looks almost right.

### Role mapping `[SRC@HEAD ListDetailPaneScaffold.kt / SupportingPaneScaffold.kt]`

| Generic role | List-detail alias | Supporting-pane alias |
| --- | --- | --- |
| `ThreePaneScaffoldRole.Primary` | **`ListDetailPaneScaffoldRole.Detail`** | **`SupportingPaneScaffoldRole.Main`** |
| `ThreePaneScaffoldRole.Secondary` | **`ListDetailPaneScaffoldRole.List`** | **`SupportingPaneScaffoldRole.Supporting`** |
| `ThreePaneScaffoldRole.Tertiary` | `ListDetailPaneScaffoldRole.Extra` | `SupportingPaneScaffoldRole.Extra` |

**In list-detail the List pane is `Secondary`, not `Primary`.** The leftmost pane is not the primary
one. In supporting-pane it is, because `Main` is `Primary` and `Main` comes first.

### Horizontal pane order — differs per scaffold, both `internal`

| Scaffold | start → end |
| --- | --- |
| `ListDetailPaneScaffold` | Secondary (**List**), Primary (**Detail**), Tertiary (Extra) |
| `SupportingPaneScaffold` | Primary (**Main**), Secondary (**Supporting**), Tertiary (Extra) |

`[SRC@HEAD]` — both declarations, verbatim including the KDoc that states the order:
```kotlin
// ListDetailPaneScaffoldDefaults
/**
 * Denotes [ThreePaneScaffold] to use the list-detail pane-order to arrange its panes
 * horizontally, which allocates panes in the order of secondary, primary, and tertiary from
 * start to end.
 */
internal val PaneOrder =
    ThreePaneScaffoldHorizontalOrder(
        ThreePaneScaffoldRole.Secondary,
        ThreePaneScaffoldRole.Primary,
        ThreePaneScaffoldRole.Tertiary,
    )

// SupportingPaneScaffoldDefaults
/**
 * Denotes [ThreePaneScaffold] to use the supporting-pane pane-order to arrange its panes
 * horizontally, which allocates panes in the order of primary, secondary, and tertiary from
 * start to end.
 */
internal val PaneOrder =
    ThreePaneScaffoldHorizontalOrder(
        ThreePaneScaffoldRole.Primary,
        ThreePaneScaffoldRole.Secondary,
        ThreePaneScaffoldRole.Tertiary,
    )
```

**Both are `internal` — pane order is not configurable.** If you need Detail-then-List you have no
API for it; pick the other scaffold or write your own layout.

### Consequences

- `ListDetailPaneScaffoldDefaults.adaptStrategies(detailPaneAdaptStrategy = …)` sets **`Primary`**;
  `listPaneAdaptStrategy` sets **`Secondary`**. The androidx KDoc says so literally: *"`@param
  detailPaneAdaptStrategy` the adapt strategy of the primary pane"*.
- `navigator.scaffoldValue[ListDetailPaneScaffoldRole.List]` reads the **secondary** slot.
- `AdaptStrategy.Reflow(reflowUnder = ListDetailPaneScaffoldRole.Detail)` = "reflow under the
  *primary* pane", which in list-detail sits to the *right* of the list.
- `ThreePaneScaffoldAdaptStrategies`'s constructor is positional **by generic role**
  (`primary, secondary, tertiary`) — never call it positionally with semantic intent.
- The alias objects are `val`s, not enum constants, so
  `ListDetailPaneScaffoldRole.Detail == SupportingPaneScaffoldRole.Main` is **`true`**. Mixing alias
  namespaces compiles and works; it is a readability bug, not a runtime one.

---

## 2. Coordinates and opt-in

```kotlin
// [DOC] developer.android.com/jetpack/androidx/releases/compose-material3-adaptive
implementation("androidx.compose.material3.adaptive:adaptive:1.3.0")        // WindowAdaptiveInfo, Posture
implementation("androidx.compose.material3.adaptive:adaptive-layout:1.3.0") // everything in this file
```

Most of this surface needs `@OptIn(ExperimentalMaterial3AdaptiveApi::class)`. Exceptions worth
knowing:

| NOT experimental | Experimental |
| --- | --- |
| `PaneScaffoldDirective` (the class) | `calculatePaneScaffoldDirective` (the function) |
| `ListDetailPaneScaffoldRole` | `SupportingPaneScaffoldRole` |
| `PaneExpansionState` (the class) | `rememberPaneExpansionState` |
| `Modifier.paneExpansionDraggable` | `Modifier.paneMargins` |

`ExperimentalMaterial3AdaptiveComponentOverrideApi` also ships in 1.3.0 and is **removed at HEAD**
(§15).

---

## 3. `PaneScaffoldDirective`

The "how many partitions, how big are the gutters, what areas must I avoid" object. Scaffold-value
calculation, pane measurement and reflow eligibility all read from it.

`[API-1.3.0]`: `@Immutable public final class PaneScaffoldDirective` — 3 constructors, `copy`, 8
read-only properties, `Companion.Default`.

`[SRC@HEAD PaneScaffoldDirective.kt]`
```kotlin
@Immutable
public class PaneScaffoldDirective(
    public val maxHorizontalPartitions: Int,
    public val horizontalPartitionSpacerSize: Dp,
    public val maxVerticalPartitions: Int,
    public val verticalPartitionSpacerSize: Dp,
    public val defaultPanePreferredWidth: Dp,
    public val defaultPanePreferredHeight: Dp,
    public val excludedBounds: List<Rect>,
    @get:JvmName("shouldAutoFocusCurrentDestination")
    public val shouldAutoFocusCurrentDestination: Boolean,
)
```

### Every field

| Field | Type | Meaning / effect |
| --- | --- | --- |
| `maxHorizontalPartitions` | `Int` | Max simultaneously-**expanded** panes side by side. `1` ⇒ single-pane. `2` ⇒ list+detail. `3` ⇒ list+detail+extra. Fed to `calculateThreePaneScaffoldValue`. KDoc: *"the max number of partitions along the horizontal axis the layout can be split into"*. |
| `horizontalPartitionSpacerSize` | `Dp` | Gutter **between** side-by-side panes; KDoc: *"equivalent to the left/right margins the horizontal partitions"*. `0.dp` makes panes read as one connected surface. |
| `maxVerticalPartitions` | `Int` | Max stacked partitions vertically. `1` ⇒ **reflow impossible**. `>= 2` ⇒ a `Reflowed` pane may sit under its anchor. |
| `verticalPartitionSpacerSize` | `Dp` | Gutter between vertical partitions (top/bottom margins). |
| `defaultPanePreferredWidth` | `Dp` | Fallback width for a *fixed* (non-stretching) pane when no `Modifier.preferredWidth` is set. **360.dp** normally, **412.dp** at Large/XL. |
| `defaultPanePreferredHeight` | `Dp` | Fallback height for **reflowed / levitated** panes. Always **420.dp** from the calculator. Added in 1.2.0. |
| `excludedBounds` | `List<Rect>` | Window-coordinate rects the layout must not paint on — hinge areas. Populated from `Posture` by `HingePolicy`. |
| `shouldAutoFocusCurrentDestination` | `Boolean` | If `true`, focus moves to the current-destination pane when it changes. `true` via the 7-arg ctor. |

### Constants `[SRC@HEAD]`

```kotlin
public companion object {
    internal val DefaultPreferredWidth = 360.dp
    internal val DefaultPreferredWidthXL = 412.dp
    internal val DefaultPreferredHeight = 420.dp

    /**
     * A default instance of [PaneScaffoldDirective] that suggests a single-pane layout that
     * occupies the full window. To create a customized [PaneScaffoldDirective], you can use
     * [PaneScaffoldDirective.copy] on the default instance to create a copy with custom values.
     */
    public val Default: PaneScaffoldDirective =
        PaneScaffoldDirective(
            maxHorizontalPartitions = 1,
            horizontalPartitionSpacerSize = 0.dp,
            maxVerticalPartitions = 1,
            verticalPartitionSpacerSize = 0.dp,
            defaultPanePreferredWidth = DefaultPreferredWidth,
            defaultPanePreferredHeight = DefaultPreferredHeight,
            excludedBounds = emptyList(),
        )
}
```

The three `Default*` values are **`internal`** — you cannot write
`PaneScaffoldDirective.DefaultPreferredWidth` from app code. Hard-code 360/412/420 dp.
`PaneScaffoldDirective.Default` **is** public and is the right base for a hand-rolled directive.

### Constructors and `copy`

Three public constructors: (1) 7-arg without `shouldAutoFocusCurrentDestination`, delegating with
`= true`; (2) 8-arg with it; (3) 6-arg legacy without `defaultPanePreferredHeight`, delegating with
`= DefaultPreferredHeight`.

`[SRC@HEAD]`
```kotlin
public fun copy(
    maxHorizontalPartitions: Int = this.maxHorizontalPartitions,
    horizontalPartitionSpacerSize: Dp = this.horizontalPartitionSpacerSize,
    maxVerticalPartitions: Int = this.maxVerticalPartitions,
    verticalPartitionSpacerSize: Dp = this.verticalPartitionSpacerSize,
    defaultPanePreferredWidth: Dp = this.defaultPanePreferredWidth,
    excludedBounds: List<Rect> = this.excludedBounds,
    defaultPanePreferredHeight: Dp = this.defaultPanePreferredHeight,
): PaneScaffoldDirective
```

Pitfalls:
- **`copy` does not expose `shouldAutoFocusCurrentDestination`** — it routes through the 7-arg ctor,
  so copying always resets that flag to `true`. To keep it `false`, call the 8-arg ctor.
- Parameter order is `…, excludedBounds, defaultPanePreferredHeight` — **use named arguments**.
- A `@Deprecated(level = HIDDEN)` 6-param `copy` exists for binary compat.
- **`equals`/`hashCode` ignore `shouldAutoFocusCurrentDestination`** — verified in source. Two
  directives differing only in that flag compare equal, so `remember(directive)` will not re-fire.

### Internal helper you cannot call

```kotlin
internal fun PaneScaffoldDirective.isSinglePaneLayout(): Boolean = maxHorizontalPartitions == 1
```
The public route to the same predicate is `AdaptStrategy.Levitate(...).onlyIfSinglePane(directive)`.
For the raw boolean, write `directive.maxHorizontalPartitions == 1`.

---

## 4. `calculatePaneScaffoldDirective` — decoded exhaustively

The single most important function in the library. Everything about "why does my tablet show one
pane" lives here. `@ExperimentalMaterial3AdaptiveApi`.

`[SRC@HEAD PaneScaffoldDirective.kt]` — full implementation, verbatim:
```kotlin
@ExperimentalMaterial3AdaptiveApi
public fun calculatePaneScaffoldDirective(
    windowAdaptiveInfo: WindowAdaptiveInfo,
    verticalHingePolicy: HingePolicy = HingePolicy.AvoidSeparating,
): PaneScaffoldDirective {
    val maxHorizontalPartitions: Int
    val horizontalPartitionSpacerSize: Dp
    val defaultPanePreferredWidth: Dp
    when (windowAdaptiveInfo.windowSizeClass.minWidth) {
        WindowSizeClass.WidthSizeClasses.Compact -> {
            maxHorizontalPartitions = 1
            horizontalPartitionSpacerSize = 0.dp
            defaultPanePreferredWidth = PaneScaffoldDirective.DefaultPreferredWidth
        }
        WindowSizeClass.WidthSizeClasses.Medium -> {
            maxHorizontalPartitions = 1
            horizontalPartitionSpacerSize = 0.dp
            defaultPanePreferredWidth = PaneScaffoldDirective.DefaultPreferredWidth
        }
        WindowSizeClass.WidthSizeClasses.Expanded -> {
            maxHorizontalPartitions = 2
            horizontalPartitionSpacerSize = 24.dp
            defaultPanePreferredWidth = PaneScaffoldDirective.DefaultPreferredWidth
        }
        else -> {
            maxHorizontalPartitions = 3
            horizontalPartitionSpacerSize = 24.dp
            defaultPanePreferredWidth = PaneScaffoldDirective.DefaultPreferredWidthXL
        }
    }
    val maxVerticalPartitions: Int
    val verticalPartitionSpacerSize: Dp

    // TODO(conradchen): Confirm the table top mode settings
    if (
        windowAdaptiveInfo.windowPosture.isTabletop ||
            (maxHorizontalPartitions == 1 &&
                windowAdaptiveInfo.windowSizeClass.minHeight ==
                    WindowSizeClass.HeightSizeClasses.Expanded)
    ) {
        maxVerticalPartitions = 2
        verticalPartitionSpacerSize = 24.dp
    } else {
        maxVerticalPartitions = 1
        verticalPartitionSpacerSize = 0.dp
    }

    val defaultPanePreferredHeight = PaneScaffoldDirective.DefaultPreferredHeight

    return PaneScaffoldDirective(
        maxHorizontalPartitions = maxHorizontalPartitions,
        horizontalPartitionSpacerSize = horizontalPartitionSpacerSize,
        maxVerticalPartitions = maxVerticalPartitions,
        verticalPartitionSpacerSize = verticalPartitionSpacerSize,
        defaultPanePreferredWidth = defaultPanePreferredWidth,
        defaultPanePreferredHeight = defaultPanePreferredHeight,
        excludedBounds =
            getExcludedVerticalBounds(windowAdaptiveInfo.windowPosture, verticalHingePolicy),
    )
}
```

⚠️ `WindowSizeClass.minWidth` / `.minHeight` and `WindowSizeClass.Companion.WidthSizeClasses` /
`.HeightSizeClasses` used above are **`internal` extensions inside `adaptive-layout`**
(`WindowSizeClassHelper.kt`). You cannot call them. From app code use the public `minWidthDp` /
`minHeightDp` `Int` properties, or `isWidthAtLeastBreakpoint(...)` — see §4.1 and
`window-size-classes.md`.

### Horizontal truth table

| Width bucket (`minWidth`) | `maxHorizontalPartitions` | `horizontalPartitionSpacerSize` | `defaultPanePreferredWidth` |
| --- | --- | --- | --- |
| Compact (**0**dp) | **1** | 0.dp | 360.dp |
| Medium (**600**dp) | **1** | 0.dp | 360.dp |
| Expanded (**840**dp) | **2** | **24.dp** | 360.dp |
| Large (**1200**dp) *(`else`)* | **3** | **24.dp** | **412.dp** |
| Extra-large (**1600**dp) *(`else`)* | **3** | **24.dp** | **412.dp** |

Large and XL fall through the same `else` branch — indistinguishable to this function.

### Vertical truth table

| Condition | `maxVerticalPartitions` | `verticalPartitionSpacerSize` |
| --- | --- | --- |
| `posture.isTabletop == true` | **2** | 24.dp |
| `maxHorizontalPartitions == 1 && minHeight == Expanded (900dp)` | **2** | 24.dp |
| otherwise | 1 | 0.dp |

`maxVerticalPartitions = 2` **iff** tabletop posture, **or** (single-pane width **and** expanded
height ≥900dp). Nothing else. `defaultPanePreferredHeight` is **always 420.dp**, every bucket.

### Consequences to internalise

- **Medium width (600–839dp) is single-pane by default.** A 700dp window — portrait tablet, unfolded
  inner display in portrait, half-screen split — shows **one** pane. Deliberate Material guidance,
  not a bug. Escape hatch in §5.
- **Large/XL get three partitions**, and fixed panes widen 360 → 412dp.
- **Tabletop posture always enables reflow.**
- **On a phone in portrait, reflow does not engage** — compact width gives
  `maxHorizontalPartitions == 1` ✓, but typical phone height is Medium, not Expanded (≥900dp), so
  `maxVerticalPartitions` stays 1. Reflow needs both (§12).

### 4.1 Feed it `currentWindowAdaptiveInfoV2()`, always

`[SRC@HEAD WindowAdaptiveInfo.kt]`
```kotlin
@Composable
@Suppress("DEPRECATION")
public fun currentWindowAdaptiveInfoV2(): WindowAdaptiveInfo =
    currentWindowAdaptiveInfo(supportLargeAndXLargeWidth = true)
```

`currentWindowAdaptiveInfo()` is **`@Deprecated` in 1.3.0** — WARNING on the
`supportLargeAndXLargeWidth` overload, HIDDEN on the zero-arg Android overload. Its default is
`false`, which clamps every width ≥840dp to **Expanded**: you silently lose the 3-partition split and
the 412dp defaults, and never reach the `else` branch above.

Also deprecated in 1.3.0: `currentWindowSize()` → `LocalWindowInfo.current.containerSize`;
`currentWindowDpSize()` → `LocalWindowInfo.current.containerDpSize`.

Branching on size class yourself: `isWidthAtLeastBreakpoint` is **`>=`**, so order the `when`
**largest → smallest** or every branch matches.
```kotlin
val wsc = currentWindowAdaptiveInfoV2().windowSizeClass
when {
    wsc.isWidthAtLeastBreakpoint(WindowSizeClass.WIDTH_DP_EXTRA_LARGE_LOWER_BOUND) -> { } // >= 1600
    wsc.isWidthAtLeastBreakpoint(WindowSizeClass.WIDTH_DP_LARGE_LOWER_BOUND)       -> { } // >= 1200
    wsc.isWidthAtLeastBreakpoint(WindowSizeClass.WIDTH_DP_EXPANDED_LOWER_BOUND)    -> { } // >= 840
    wsc.isWidthAtLeastBreakpoint(WindowSizeClass.WIDTH_DP_MEDIUM_LOWER_BOUND)      -> { } // >= 600
    else -> { }                                                                          // compact
}
```
Width breakpoints `0 / 600 / 840 / 1200 / 1600`; height `0 / 480 / 900`. **Height has no Large/XL** —
the V2 breakpoint set added width classes only.

---

## 5. `calculatePaneScaffoldDirectiveWithTwoPanesOnMediumWidth`

`[SRC@HEAD PaneScaffoldDirective.kt]` — implementation verbatim, KDoc elided except the
recommendation:
```kotlin
/**
 * ... Note that this function results in a dual-pane layout when the window width falls in the
 * Medium size bucket, while [calculatePaneScaffoldDirective] results in a single-pane layout
 * instead. We recommend to use [calculatePaneScaffoldDirective], unless you have a strong use case
 * to show two panes on a medium-width window, which can make your layout look too packed.
 */
@ExperimentalMaterial3AdaptiveApi
public fun calculatePaneScaffoldDirectiveWithTwoPanesOnMediumWidth(
    windowAdaptiveInfo: WindowAdaptiveInfo,
    verticalHingePolicy: HingePolicy = HingePolicy.AvoidSeparating,
): PaneScaffoldDirective {
    val isMediumWidth =
        windowAdaptiveInfo.windowSizeClass.minWidth == WindowSizeClass.WidthSizeClasses.Medium
    val isTableTop = windowAdaptiveInfo.windowPosture.isTabletop
    return with(calculatePaneScaffoldDirective(windowAdaptiveInfo, verticalHingePolicy)) {
        copy(
            maxHorizontalPartitions = if (isMediumWidth) 2 else maxHorizontalPartitions,
            horizontalPartitionSpacerSize =
                if (isMediumWidth) {
                    24.dp
                } else {
                    horizontalPartitionSpacerSize
                },
            maxVerticalPartitions = if (isMediumWidth && !isTableTop) 1 else maxVerticalPartitions,
            verticalPartitionSpacerSize =
                if (isMediumWidth && !isTableTop) 0.dp else verticalPartitionSpacerSize,
        )
    }
}
```

Delta, applied **only** when width is exactly Medium:

| | plain | …WithTwoPanesOnMediumWidth |
| --- | --- | --- |
| `maxHorizontalPartitions` | 1 | **2** |
| `horizontalPartitionSpacerSize` | 0.dp | **24.dp** |
| `maxVerticalPartitions` (non-tabletop) | 1 or 2 | forced to **1** |
| `verticalPartitionSpacerSize` (non-tabletop) | 0/24.dp | forced to **0.dp** |

At medium width it deliberately prefers a **horizontal** split over a vertical/reflow one; tabletop
posture still wins the vertical fields.

**When to use:** only with a strong product reason for two panes at 600–839dp and genuinely narrow
content (one-line list rows plus a sparse detail). Otherwise follow Google's own recommendation
above.

**Pitfall:** it goes through `copy`, so **`shouldAutoFocusCurrentDestination` resets to `true`** even
if your base directive had it `false`.

---

## 6. `HingePolicy`

`@Immutable @JvmInline value class`, 4 values. `[SRC@HEAD]` KDoc verbatim:
```kotlin
public companion object {
    /** When rendering content in a layout, always avoid where hinges are. */
    public val AlwaysAvoid: HingePolicy = HingePolicy(0)
    /**
     * When rendering content in a layout, avoid hinges that are separating. Note that an
     * occluding hinge is supposed to be separating as well but not vice versa.
     */
    public val AvoidSeparating: HingePolicy = HingePolicy(1)
    /**
     * When rendering content in a layout, avoid hinges that are occluding. Note that an
     * occluding hinge is supposed to be separating as well but not vice versa.
     */
    public val AvoidOccluding: HingePolicy = HingePolicy(2)
    /** When rendering content in a layout, never avoid any hinges, separating or not. */
    public val NeverAvoid: HingePolicy = HingePolicy(3)
}
```

Which `Posture` bounds become `excludedBounds` `[SRC@HEAD]`:
```kotlin
private fun getExcludedVerticalBounds(posture: Posture, hingePolicy: HingePolicy): List<Rect> {
    return when (hingePolicy) {
        HingePolicy.AvoidSeparating -> posture.separatingVerticalHingeBounds
        HingePolicy.AvoidOccluding -> posture.occludingVerticalHingeBounds
        HingePolicy.AlwaysAvoid -> posture.allVerticalHingeBounds
        else -> emptyList()
    }
}
```

| `HingePolicy` | `excludedBounds` source |
| --- | --- |
| `AlwaysAvoid` (0) | `posture.allVerticalHingeBounds` |
| `AvoidSeparating` (1) — **default** | `posture.separatingVerticalHingeBounds` |
| `AvoidOccluding` (2) | `posture.occludingVerticalHingeBounds` |
| `NeverAvoid` (3) | `emptyList()` |

Pitfalls:
- **Only *vertical* hinges become `excludedBounds`** — `getExcludedVerticalBounds` is the sole call
  site. Horizontal (tabletop) hinges influence layout only via `isTabletop → maxVerticalPartitions = 2`.
- **`toString()` does not match the property names**: `AvoidSeparating` prints
  `"HingePolicy.AvoidOccludingAndSeparating"`, `AvoidOccluding` prints
  `"HingePolicy.AvoidOccludingOnly"`. Never parse it.
- Use `NeverAvoid` when deliberately painting across a fold (full-bleed media).

---

## 7. Customising the directive — worked 0dp-gutter example

### 7a. `copy()` the computed directive — do this first

```kotlin
val directive = calculatePaneScaffoldDirective(currentWindowAdaptiveInfoV2())
    .copy(horizontalPartitionSpacerSize = 0.dp)
```
Panes now touch and read as one connected surface; partition counts, preferred widths and hinge
exclusions stay Material-correct and stay correct as the window resizes.

Feed it to the navigator so the whole system agrees:
```kotlin
val navigator = rememberListDetailPaneScaffoldNavigator<MyKey>(
    scaffoldDirective = calculatePaneScaffoldDirective(currentWindowAdaptiveInfoV2())
        .copy(horizontalPartitionSpacerSize = 0.dp)
)
```

Other common tweaks:
```kotlin
directive.copy(maxHorizontalPartitions = 2)                   // force dual-pane
directive.copy(defaultPanePreferredWidth = 320.dp)            // narrower fixed panes
directive.copy(maxVerticalPartitions = 2)                     // force reflow eligibility
calculatePaneScaffoldDirective(info, HingePolicy.NeverAvoid)  // paint across the fold
```

### 7b. Fork the calculator — what Tomato ships

Tomato forked the AndroidX calculator solely to zero the gutter; the docstring says so.
`[REPO /root/work/repos/Tomato/shared/src/commonMain/kotlin/org/nsh07/pomodoro/ui/UiUtils.kt`
lines 69–150`]` — verbatim, with the unchanged tail elided:
```kotlin
/**
 * (Copied from [androidx.compose.material3.adaptive.layout.calculatePaneScaffoldDirective] with
 * minor modifications, namely the reduction of horizontalPartitionSpacerSize to 0.dp)
 * ...
 */
fun calculatePaneScaffoldDirective(
    windowAdaptiveInfo: WindowAdaptiveInfo,
    verticalHingePolicy: HingePolicy = HingePolicy.AvoidSeparating,
): PaneScaffoldDirective {
    val maxHorizontalPartitions: Int
    val horizontalPartitionSpacerSize: Dp
    val defaultPanePreferredWidth: Dp
    when (windowAdaptiveInfo.windowSizeClass.minWidthDp) {
        0 -> {
            maxHorizontalPartitions = 1
            horizontalPartitionSpacerSize = 0.dp
            defaultPanePreferredWidth = 360.dp
        }

        WindowSizeClass.WIDTH_DP_MEDIUM_LOWER_BOUND -> {
            maxHorizontalPartitions = 1
            horizontalPartitionSpacerSize = 0.dp
            defaultPanePreferredWidth = 360.dp
        }

        WindowSizeClass.WIDTH_DP_EXPANDED_LOWER_BOUND -> {
            maxHorizontalPartitions = 2
            horizontalPartitionSpacerSize = 0.dp
            defaultPanePreferredWidth = 360.dp
        }

        else -> {
            maxHorizontalPartitions = 3
            horizontalPartitionSpacerSize = 0.dp
            defaultPanePreferredWidth = 412.dp
        }
    }
    // ... vertical partition block identical to androidx, except:
    if (
        windowAdaptiveInfo.windowPosture.isTabletop ||
        (maxHorizontalPartitions == 1 &&
                windowAdaptiveInfo.windowSizeClass.minHeightDp ==
                WindowSizeClass.WIDTH_DP_EXPANDED_LOWER_BOUND)   // <-- see defect 1
    ) { /* 2 / 24.dp */ } else { /* 1 / 0.dp */ }

    return PaneScaffoldDirective(
        // ... same seven arguments ...
        excludedBounds = when (verticalHingePolicy) {
            HingePolicy.AvoidSeparating -> windowAdaptiveInfo.windowPosture.separatingVerticalHingeBounds
            HingePolicy.AvoidOccluding -> windowAdaptiveInfo.windowPosture.occludingVerticalHingeBounds
            HingePolicy.AlwaysAvoid -> windowAdaptiveInfo.windowPosture.allVerticalHingeBounds
            else -> emptyList()
        }
    )
}
```

Two defects in the fork — **do not copy**:
1. `minHeightDp == WindowSizeClass.WIDTH_DP_EXPANDED_LOWER_BOUND` compares a *height* against a
   *width* constant (840 vs the real height bound 900), so the branch never fires and tall-window
   reflow is silently dead. androidx uses `HeightSizeClasses.Expanded`.
2. It reimplements the hinge-bounds `when` inline because `getExcludedVerticalBounds` is `private` —
   unavoidable in a fork, but more code to keep in sync.

**Guidance:** fork only when `copy()` cannot express the change (different partition counts or
preferred widths *per bucket*). For a uniform gutter change, §7a is strictly better — it inherits
future androidx fixes.

---

## 8. Role types

`[API-1.3.0]`
```
public interface PaneScaffoldRole {
}

public enum ThreePaneScaffoldRole implements androidx.compose.material3.adaptive.layout.PaneScaffoldRole {
    enum_constant public static final androidx.compose.material3.adaptive.layout.ThreePaneScaffoldRole Primary;
    enum_constant public static final androidx.compose.material3.adaptive.layout.ThreePaneScaffoldRole Secondary;
    enum_constant public static final androidx.compose.material3.adaptive.layout.ThreePaneScaffoldRole Tertiary;
}
```
`PaneScaffoldRole` is a bare marker interface. `ThreePaneScaffoldRole` has **only three values, and
they are generic, not semantic.**

`[SRC@HEAD]` — the alias objects, all values:
```kotlin
public object ListDetailPaneScaffoldRole {
    /** ... It maps to [ThreePaneScaffoldRole.Secondary]. */
    public val List: ThreePaneScaffoldRole = ThreePaneScaffoldRole.Secondary
    /** ... It maps to [ThreePaneScaffoldRole.Primary]. */
    public val Detail: ThreePaneScaffoldRole = ThreePaneScaffoldRole.Primary
    /** ... It maps to [ThreePaneScaffoldRole.Tertiary]. */
    public val Extra: ThreePaneScaffoldRole = ThreePaneScaffoldRole.Tertiary
}

@ExperimentalMaterial3AdaptiveApi
public object SupportingPaneScaffoldRole {
    /** ... It maps to [ThreePaneScaffoldRole.Primary]. */
    public val Main: ThreePaneScaffoldRole = ThreePaneScaffoldRole.Primary
    /** ... It maps to [ThreePaneScaffoldRole.Secondary]. */
    public val Supporting: ThreePaneScaffoldRole = ThreePaneScaffoldRole.Secondary
    /** ... It maps to [ThreePaneScaffoldRole.Tertiary]. */
    public val Extra: ThreePaneScaffoldRole = ThreePaneScaffoldRole.Tertiary
}
```
`ListDetailPaneScaffoldRole` is **not** experimental; `SupportingPaneScaffoldRole` **is**.

### `ThreePaneScaffoldHorizontalOrder`

`[API-1.3.0]` — `@ExperimentalMaterial3AdaptiveApi @Immutable public final class
ThreePaneScaffoldHorizontalOrder implements PaneScaffoldHorizontalOrder<ThreePaneScaffoldRole>` with
`forEach`, `forEachIndexed`, `forEachIndexedReversed`, `get(index)`, `indexOf(role)`, `size` — and
**no public constructor listed**. You can read an order you were handed, not build one. The
1.3.0-beta01 note *"Move scaffold order APIs back to experimental"* refers to this. Do not plan a
feature around custom pane order.

---

## 9. `PaneAdaptedValue` — the *result* of adaptation for one pane

A sealed interface: 2 singletons + 2 data-carrying classes. `[SRC@HEAD PaneAdaptedValue.kt]`
```kotlin
@ExperimentalMaterial3AdaptiveApi
@Stable
public sealed interface PaneAdaptedValue {
    private class Simple(private val description: String) : PaneAdaptedValue {
        override fun toString() = "PaneAdaptedValue[$description]"
    }

    /**
     * Indicates that the associated pane should be reflowed to its [reflowUnder], i.e., it will be
     * displayed under the target pane.
     */
    @Immutable
    public class Reflowed(internal val reflowUnder: PaneScaffoldRole) : PaneAdaptedValue

    /**
     * Indicates that the associated pane should be levitated with the specified [alignment].
     * ... @param scrim the scrim to show when the levitated pane is shown to block user interaction
     *   with the underlying layout ...; by default it will be `null` and no scrim will show; to
     *   display a scrim, we recommend to use [LevitatedPaneScrim] as a default implementation.
     */
    @Immutable
    public class Levitated(
        internal val alignment: Alignment,
        internal val scrim: (@Composable () -> Unit)? = null,
        internal val dragToResizeState: DragToResizeState? = null,
    ) : PaneAdaptedValue

    public companion object {
        /** Indicates that the associated pane should be displayed in its full width and height. */
        public val Expanded: PaneAdaptedValue = Simple("Expanded")
        /** Indicates that the associated pane should be hidden. */
        public val Hidden: PaneAdaptedValue = Simple("Hidden")
    }
}
```

| Value | Meaning |
| --- | --- |
| `Expanded` | pane occupies a full horizontal partition |
| `Hidden` | pane not visible — `AnimatedPane` treats `!= Hidden` as visible |
| `Reflowed(reflowUnder)` | pane stacked **under** `reflowUnder` in the same horizontal partition (needs `maxVerticalPartitions >= 2`) |
| `Levitated(alignment, scrim, dragToResizeState)` | pane floats above the others, dialog/sheet style |

Pitfalls:
- `Reflowed.reflowUnder`, `Levitated.alignment`/`.scrim`/`.dragToResizeState` are all **`internal`** —
  constructible, not readable back.
- **`Levitated.equals` uses identity (`!==`) for `scrim` and `dragToResizeState`** and structural
  equality for `alignment`. An inline lambda scrim yields a fresh unequal value every recomposition →
  the scaffold value churns. **Hoist the scrim lambda.**
- Read current values with `scaffoldValue[role]`. androidx's own helper `[SRC@HEAD samples]`:
  ```kotlin
  @OptIn(ExperimentalMaterial3AdaptiveApi::class)
  private fun ThreePaneScaffoldNavigator<*>.isExpanded(role: ThreePaneScaffoldRole) =
      scaffoldValue[role] == PaneAdaptedValue.Expanded
  ```

---

## 10. `AdaptStrategy` — the *policy* when a pane cannot be `Expanded`

`[SRC@HEAD AdaptStrategy.kt]`
```kotlin
@ExperimentalMaterial3AdaptiveApi
@Stable
public sealed interface AdaptStrategy {
    /** Override this function to provide the resulted adapted state. */
    @Deprecated(
        "This function is deprecated in favor of directly using the info carried by the " +
            "strategy instances to make adaptation decisions."
    )
    public fun adapt(): PaneAdaptedValue = PaneAdaptedValue.Hidden

    /**
     * Indicate the associated pane should be reflowed when certain conditions are met. With the
     * default calculation functions [calculateThreePaneScaffoldValue] we provide, when it's a
     * single pane layout, a pane with a reflow strategy will be adapted to either:
     * 1. [PaneAdaptedValue.Reflowed], when either the reflowed pane or the target pane it's
     *    supposed to be reflowed to is the current destination; or
     * 2. [PaneAdaptedValue.Hidden] otherwise.
     *
     * Note that if the current layout can have more than one horizontal partition, the pane will
     * never be reflowed.
     */
    @Immutable
    public class Reflow(internal val reflowUnder: PaneScaffoldRole) : AdaptStrategy

    /**
     * Indicate the associated pane should be levitated when it's the current destination.
     * ... With the default [calculateThreePaneScaffoldValue] we provide, a pane with a levitate
     * strategy will be adapted to either:
     * 1. [PaneAdaptedValue.Levitated] with specified [alignment], when the levitated pane is the
     *    current destination; or
     * 2. [PaneAdaptedValue.Hidden] otherwise.
     */
    @Immutable
    public class Levitate(
        internal val alignment: Alignment = Alignment.Center,
        internal val scrim: (@Composable () -> Unit)? = null,
        internal val dragToResizeState: DragToResizeState? = null,
    ) : AdaptStrategy {

        /**
         * This is a convenient function to only levitate the associated pane when the provided
         * condition is met. If the condition is not met, the pane will be expanded instead, if
         * there's enough room; otherwise it will be hidden.
         */
        @Composable
        public fun onlyIf(condition: Boolean): AdaptStrategy = if (condition) this else Hide

        /**
         * This is a convenient function to only levitate the associated pane when it's a
         * single-pane layout. On multi-pane layouts, the pane will be expanded instead, if it's one
         * of the recent destinations.
         */
        @Composable
        public fun onlyIfSinglePane(scaffoldDirective: PaneScaffoldDirective): AdaptStrategy =
            onlyIf(scaffoldDirective.isSinglePaneLayout())
    }

    public companion object {
        /**
         * The default [AdaptStrategy] that suggests the layout to hide the associated pane when it
         * has to be adapted, i.e., cannot be displayed in its [PaneAdaptedValue.Expanded] state.
         */
        public val Hide: AdaptStrategy = Simple("Hide")
    }
}
```
`[API-1.3.0]` additionally shows a zero-arg `ctor public AdaptStrategy.Levitate()` alongside the
3-optional-arg one.

| Strategy | Result when adapted |
| --- | --- |
| `AdaptStrategy.Hide` | `PaneAdaptedValue.Hidden` |
| `AdaptStrategy.Reflow(reflowUnder)` | `Reflowed(reflowUnder)` when single-pane **and** `maxVerticalPartitions >= 2` **and** the reflowed pane or its anchor is the current destination; else `Hidden` |
| `AdaptStrategy.Levitate(alignment = Center, scrim = null, dragToResizeState = null)` | `Levitated(...)` **only when it is the current destination**; else `Hidden` |

Pitfalls:
- `onlyIf` / `onlyIfSinglePane` return **`Hide`** on failure — the pane falls back to
  expand-if-room-else-hide. Both are `@Composable`; call them inside composition.
- `adapt()` is deprecated and **ignored** — strategies are matched **by type** in
  `calculateThreePaneScaffoldValue`. The interface is `sealed` anyway, so you cannot add strategies.
- Levitation applies **only to the current destination**.

---

## 11. `ThreePaneScaffoldAdaptStrategies` and the two `*Defaults`

`[SRC@HEAD ThreePaneScaffoldAdaptStrategies.kt]`
```kotlin
@ExperimentalMaterial3AdaptiveApi
public class ThreePaneScaffoldAdaptStrategies(
    private val primaryPaneAdaptStrategy: AdaptStrategy,
    private val secondaryPaneAdaptStrategy: AdaptStrategy,
    private val tertiaryPaneAdaptStrategy: AdaptStrategy,
) {
    public operator fun get(role: ThreePaneScaffoldRole): AdaptStrategy {
        return when (role) {
            ThreePaneScaffoldRole.Primary -> primaryPaneAdaptStrategy
            ThreePaneScaffoldRole.Secondary -> secondaryPaneAdaptStrategy
            ThreePaneScaffoldRole.Tertiary -> tertiaryPaneAdaptStrategy
        }
    }
    // equals / hashCode implemented over the three strategies
}
```
Constructor is positional **by generic role**; only `get(role)` is public for reading. Prefer the
`*Defaults.adaptStrategies(...)` factories, which name parameters semantically.

`[SRC@HEAD]` — both factories, verbatim, with the role-mapping KDoc kept:
```kotlin
/** Provides default values of [ListDetailPaneScaffold]. */
@ExperimentalMaterial3AdaptiveApi
public object ListDetailPaneScaffoldDefaults {
    /**
     * @param detailPaneAdaptStrategy the adapt strategy of the primary pane
     * @param listPaneAdaptStrategy the adapt strategy of the secondary pane
     * @param extraPaneAdaptStrategy the adapt strategy of the tertiary pane
     */
    public fun adaptStrategies(
        detailPaneAdaptStrategy: AdaptStrategy = AdaptStrategy.Hide,
        listPaneAdaptStrategy: AdaptStrategy = AdaptStrategy.Hide,
        extraPaneAdaptStrategy: AdaptStrategy = AdaptStrategy.Hide,
    ): ThreePaneScaffoldAdaptStrategies =
        ThreePaneScaffoldAdaptStrategies(
            detailPaneAdaptStrategy,
            listPaneAdaptStrategy,
            extraPaneAdaptStrategy,
        )
    // internal val PaneOrder = ...
}

public object SupportingPaneScaffoldDefaults {
    /**
     * @param mainPaneAdaptStrategy the adapt strategy of the main pane
     * @param supportingPaneAdaptStrategy the adapt strategy of the supporting pane
     * @param extraPaneAdaptStrategy the adapt strategy of the extra pane
     */
    public fun adaptStrategies(
        mainPaneAdaptStrategy: AdaptStrategy = AdaptStrategy.Hide,
        supportingPaneAdaptStrategy: AdaptStrategy =
            AdaptStrategy.Reflow(SupportingPaneScaffoldRole.Main),
        extraPaneAdaptStrategy: AdaptStrategy = AdaptStrategy.Hide,
    ): ThreePaneScaffoldAdaptStrategies =
        ThreePaneScaffoldAdaptStrategies(
            mainPaneAdaptStrategy,
            supportingPaneAdaptStrategy,
            extraPaneAdaptStrategy,
        )
    // internal val PaneOrder = ...
}
```

> **Default divergence — the second most common surprise after the role trap.** List-detail defaults
> **everything** to `Hide`. Supporting-pane defaults the supporting pane to
> **`AdaptStrategy.Reflow(SupportingPaneScaffoldRole.Main)`**, so on a single-pane layout with
> `maxVerticalPartitions >= 2` (tabletop, or single-pane width + expanded height) the supporting
> content stacks *below* the main content instead of vanishing. That matches the guidance *"For
> compact-width displays, place the supporting content below the main content."*
>
> If you pass custom strategies, **re-specify `supportingPaneAdaptStrategy` or you lose reflow.**
> Passing `Hide` explicitly is a legitimate choice —
> `[REPO Tomato .../ui/timerScreen/TimerScreen.kt:216]` does exactly
> `SupportingPaneScaffoldDefaults.adaptStrategies(supportingPaneAdaptStrategy = AdaptStrategy.Hide)`
> — just make it deliberate.

---

## 12. `ThreePaneScaffoldValue` and `calculateThreePaneScaffoldValue` — how adaptation resolves

`[API-1.3.0]`
```
@SuppressCompatibility @androidx.compose.material3.adaptive.ExperimentalMaterial3AdaptiveApi @androidx.compose.runtime.Immutable public final class ThreePaneScaffoldValue implements androidx.compose.material3.adaptive.layout.PaneExpansionStateKeyProvider androidx.compose.material3.adaptive.layout.PaneScaffoldValue<androidx.compose.material3.adaptive.layout.ThreePaneScaffoldRole> {
    ctor public ThreePaneScaffoldValue(androidx.compose.material3.adaptive.layout.PaneAdaptedValue primary, androidx.compose.material3.adaptive.layout.PaneAdaptedValue secondary, androidx.compose.material3.adaptive.layout.PaneAdaptedValue tertiary);
    method public operator androidx.compose.material3.adaptive.layout.PaneAdaptedValue get(androidx.compose.material3.adaptive.layout.ThreePaneScaffoldRole role);
    property public androidx.compose.material3.adaptive.layout.PaneExpansionStateKey paneExpansionStateKey;
    property public androidx.compose.material3.adaptive.layout.PaneAdaptedValue primary;
    property public androidx.compose.material3.adaptive.layout.PaneAdaptedValue secondary;
    property public androidx.compose.material3.adaptive.layout.PaneAdaptedValue tertiary;
}
```

Two facts to carry:
- **`ThreePaneScaffoldValue` is itself a `PaneExpansionStateKeyProvider`** — that is why samples pass
  `keyProvider = scaffoldNavigator.scaffoldValue` to `rememberPaneExpansionState`
  (`pane-navigation-and-expansion.md` §12).
- The public ctor takes 3 args; internally there is a 4th `currentDestination:
  ThreePaneScaffoldRole?` you cannot set. Build values via `calculateThreePaneScaffoldValue` or a
  navigator, never by hand.

### The two overloads

`[SRC@HEAD ThreePaneScaffoldValue.kt]` — single-destination convenience, with the KDoc that states
the algorithm:
```kotlin
/**
 * ... The function will treat the current destination as the highest priority and then adapt the
 * rest panes according to the order of [ThreePaneScaffoldRole.Primary],
 * [ThreePaneScaffoldRole.Secondary] and [ThreePaneScaffoldRole.Tertiary]. If there are still
 * remaining partitions to put the pane, the pane will be set as [PaneAdaptedValue.Expanded],
 * otherwise it will be adapted according to its associated [AdaptStrategy].
 * ...
 * @param maxVerticalPartitions The maximum allowed partitions along the vertical axis, by default
 *   it will be 1 and in this case no reflowed panes will be allowed; if the value equals to or
 *   larger than 2, reflowed panes are allowed, besides the expanded pane in the same horizontal
 *   partition.
 */
@ExperimentalMaterial3AdaptiveApi
public fun calculateThreePaneScaffoldValue(
    maxHorizontalPartitions: Int,
    adaptStrategies: ThreePaneScaffoldAdaptStrategies,
    currentDestination: ThreePaneScaffoldDestinationItem<*>?,
    maxVerticalPartitions: Int = 1,
): ThreePaneScaffoldValue =
    calculateThreePaneScaffoldValue(
        maxHorizontalPartitions,
        adaptStrategies,
        listOfNotNull(currentDestination),
        maxVerticalPartitions,
    )
```

History-aware overload — the real algorithm, verbatim:
```kotlin
/**
 * @param destinationHistory The history of past destination items. The last destination will have
 *   the highest priority, and the second last destination will have the second highest priority,
 *   and so forth until all panes have a priority assigned. ...
 */
@ExperimentalMaterial3AdaptiveApi
public fun calculateThreePaneScaffoldValue(
    maxHorizontalPartitions: Int,
    adaptStrategies: ThreePaneScaffoldAdaptStrategies,
    destinationHistory: List<ThreePaneScaffoldDestinationItem<*>>,
    maxVerticalPartitions: Int = 1,
): ThreePaneScaffoldValue {
    var expandedCount = 0
    var primaryPaneAdaptedValue: PaneAdaptedValue? = null
    var secondaryPaneAdaptedValue: PaneAdaptedValue? = null
    var tertiaryPaneAdaptedValue: PaneAdaptedValue? = null

    fun getAdaptedValue(role: ThreePaneScaffoldRole) = /* when(role) -> the local var */
    fun setAdaptedValue(role: ThreePaneScaffoldRole, value: PaneAdaptedValue) { /* ... */ }

    var checkReflowedPane =
        maxHorizontalPartitions == 1 &&
            maxVerticalPartitions > 1 &&
            (adaptStrategies[ThreePaneScaffoldRole.Primary] is AdaptStrategy.Reflow ||
                adaptStrategies[ThreePaneScaffoldRole.Secondary] is AdaptStrategy.Reflow ||
                adaptStrategies[ThreePaneScaffoldRole.Tertiary] is AdaptStrategy.Reflow)

    val currentDestination = destinationHistory.lastOrNull()

    // Only levitate a pane when it is the current destination
    currentDestination?.apply {
        (adaptStrategies[pane] as? AdaptStrategy.Levitate)?.apply {
            setAdaptedValue(pane, PaneAdaptedValue.Levitated(alignment, scrim, dragToResizeState))
        }
    }

    run {
        forEachPaneByPriority(destinationHistory) { pane ->
            val hasAvailablePartition = expandedCount < maxHorizontalPartitions
            if (!hasAvailablePartition && !checkReflowedPane) {
                return@run // No need to check more panes, break;
            }
            if (getAdaptedValue(pane) != null) {
                return@forEachPaneByPriority // Pane already adapted, continue;
            }
            var reflowedPane: ThreePaneScaffoldRole? = null
            var anchorPane: ThreePaneScaffoldRole = pane
            var anchorPaneValue: PaneAdaptedValue? = null
            if (checkReflowedPane) {
                (adaptStrategies[pane] as? AdaptStrategy.Reflow)?.apply {
                    (this.reflowUnder as? ThreePaneScaffoldRole)?.apply {
                        reflowedPane = pane
                        anchorPane = this
                        anchorPaneValue = getAdaptedValue(anchorPane)
                    }
                }
            }
            when (anchorPaneValue) {
                null ->
                    if (adaptStrategies[anchorPane] is AdaptStrategy.Levitate) {
                        // The anchor pane can only be levitated, continue;
                        return@forEachPaneByPriority
                    } else if (hasAvailablePartition) {
                        // Expand the anchor pane to reflow the pane
                        setAdaptedValue(anchorPane, PaneAdaptedValue.Expanded)
                        expandedCount++
                    } else {
                        // Cannot expand the anchor pane, continue;
                        return@forEachPaneByPriority
                    }
                PaneAdaptedValue.Expanded -> {
                    // Anchor pane is expanded, do nothing
                }
                else -> return@forEachPaneByPriority // Anchor pane is not expanded, continue;
            }
            reflowedPane?.apply {
                setAdaptedValue(this, PaneAdaptedValue.Reflowed(anchorPane))
                checkReflowedPane = false
            }
        }
    }
    return ThreePaneScaffoldValue(
        primary = primaryPaneAdaptedValue ?: PaneAdaptedValue.Hidden,
        secondary = secondaryPaneAdaptedValue ?: PaneAdaptedValue.Hidden,
        tertiary = tertiaryPaneAdaptedValue ?: PaneAdaptedValue.Hidden,
        currentDestination = currentDestination?.pane,
    )
}

@ExperimentalMaterial3AdaptiveApi
private inline fun forEachPaneByPriority(
    destinationHistory: List<ThreePaneScaffoldDestinationItem<*>>,
    action: (ThreePaneScaffoldRole) -> Unit,
) {
    destinationHistory.fastForEachReversed { action(it.pane) }
    action(ThreePaneScaffoldRole.Primary)
    action(ThreePaneScaffoldRole.Secondary)
    action(ThreePaneScaffoldRole.Tertiary)
}
```

### Resolution rules, distilled

1. **Levitation resolves first, outside the loop, and only for the current destination.**
2. **Priority = destination history reversed** (most recent first), then `Primary`, `Secondary`,
   `Tertiary` as tie-breakers. A role may be visited twice; the `getAdaptedValue(pane) != null` guard
   makes the second visit a no-op.
3. Each pane in priority order takes an expanded partition while `expandedCount <
   maxHorizontalPartitions`.
4. **Reflow engages only when `maxHorizontalPartitions == 1 && maxVerticalPartitions > 1`** and some
   role has a `Reflow` strategy. **At most one pane reflows** (`checkReflowedPane = false` after the
   first).
5. A pane whose reflow anchor cannot be expanded is skipped → stays `Hidden`.
6. Anything unresolved is **`Hidden`**.

Worked examples — list-detail, default (`Hide`) strategies:

| Directive | History (oldest→newest) | Result |
| --- | --- | --- |
| `maxH = 1` | `[List]` | List `Expanded`; Detail, Extra `Hidden` |
| `maxH = 1` | `[List, Detail(k)]` | Detail `Expanded`, List `Hidden` |
| `maxH = 2` | `[List, Detail(k)]` | Detail `Expanded` (top priority), List `Expanded` |
| `maxH = 2` | `[List, Detail, Extra]` | Extra + Detail `Expanded`, List `Hidden` |
| `maxH = 3` | `[List, Detail, Extra]` | all three `Expanded` |
| `maxH = 2`, history-unaware | current = `Extra` | Extra `Expanded`, then `Primary` (Detail) `Expanded`, List `Hidden` |

`isDestinationHistoryAware` on the navigator selects which overload the default navigator calls —
`pane-navigation-and-expansion.md` §5.

---

## 13. `ListDetailPaneScaffold` — both overloads

Two overloads: one takes a `ThreePaneScaffoldValue` (static), one a `ThreePaneScaffoldState`
(animated / seekable). **Both exist in 1.3.0.**

`[SRC@HEAD ListDetailPaneScaffold.kt:81]` — the `value` overload, signature and body verbatim:
```kotlin
@ExperimentalMaterial3AdaptiveApi
@Composable
public fun ListDetailPaneScaffold(
    directive: PaneScaffoldDirective,
    value: ThreePaneScaffoldValue,
    listPane: @Composable ThreePaneScaffoldPaneScope.() -> Unit,
    detailPane: @Composable ThreePaneScaffoldPaneScope.() -> Unit,
    modifier: Modifier = Modifier,
    extraPane: (@Composable ThreePaneScaffoldPaneScope.() -> Unit)? = null,
    paneExpansionDragHandle: (@Composable ThreePaneScaffoldScope.(PaneExpansionState) -> Unit)? =
        null,
    paneExpansionState: PaneExpansionState? = null,
) {
    val expansionState =
        paneExpansionState
            ?: rememberDefaultPaneExpansionState(
                keyProvider = { value },
                mutable = paneExpansionDragHandle != null,
            )
    ThreePaneScaffold(
        modifier = modifier.fillMaxSize(),
        scaffoldDirective = directive,
        scaffoldValue = value,
        paneOrder = ListDetailPaneScaffoldDefaults.PaneOrder,
        secondaryPane = listPane,
        tertiaryPane = extraPane,
        paneExpansionDragHandle = paneExpansionDragHandle,
        paneExpansionState = expansionState,
        primaryPane = detailPane,
    )
}
```

`[SRC@HEAD ListDetailPaneScaffold.kt:168]` — the `scaffoldState` overload (identical except the
second parameter and `keyProvider = { scaffoldState.targetState }`):
```kotlin
@ExperimentalMaterial3AdaptiveApi
@Composable
public fun ListDetailPaneScaffold(
    directive: PaneScaffoldDirective,
    scaffoldState: ThreePaneScaffoldState,
    listPane: @Composable ThreePaneScaffoldPaneScope.() -> Unit,
    detailPane: @Composable ThreePaneScaffoldPaneScope.() -> Unit,
    modifier: Modifier = Modifier,
    extraPane: (@Composable ThreePaneScaffoldPaneScope.() -> Unit)? = null,
    paneExpansionDragHandle: (@Composable ThreePaneScaffoldScope.(PaneExpansionState) -> Unit)? =
        null,
    paneExpansionState: PaneExpansionState? = null,
)
```

### Every parameter

| Parameter | KDoc / behaviour |
| --- | --- |
| `directive` | *"The top-level directives about how the scaffold should arrange its panes."* Normally `navigator.scaffoldDirective`. |
| `value` | *"The current adapted value of the scaffold, which indicates how each pane of the scaffold is adapted."* Static — no animation. |
| `scaffoldState` | *"The current state of the scaffold, containing information about the adapted value of each pane of the scaffold and the transitions/animations in progress."* |
| `listPane` | *"supposed to hold a list of item summaries that can be selected from, for example, the inbox mail list of a mail app. See `[ListDetailPaneScaffoldRole.List]`."* → **secondary** slot. |
| `detailPane` | *"supposed to hold the detailed info of a selected item, for example, the mail content currently being viewed."* → **primary** slot. |
| `modifier` | *"`[Modifier]` of the scaffold layout."* `fillMaxSize()` is applied on top of it internally. |
| `extraPane` | *"any supplementary info besides the list and the detail panes, for example, a task list or a mini-calendar view of a mail app."* Nullable; → tertiary. |
| `paneExpansionDragHandle` | *"the pane expansion drag handle to allow users to drag to change pane expansion state, `null` by default."* Receiver is `ThreePaneScaffoldScope` (**not** a pane scope). |
| `paneExpansionState` | *"when no value is provided but `[paneExpansionDragHandle]` is not `null`, a default implementation will be created for the drag handle to use."* |

**Which overload:** `scaffoldState = navigator.scaffoldState` in almost every case — it is what makes
pane transitions animate and what predictive back seeks. Use `value = …` only for a static,
externally-computed layout (previews, tests, non-navigator-driven). Every androidx `@Sampled`
list-detail sample uses `scaffoldState`.

Other notes:
- `listPane` / `detailPane` are **required positional** and sit **before `modifier`**.
- Pane lambdas are `ThreePaneScaffoldPaneScope.() -> Unit` — that scope provides `AnimatedPane`,
  `Modifier.preferredWidth/Height`, `paneRole`, `paneMotion`, `isInteractable`.
- **Both overloads apply `Modifier.fillMaxSize()` internally.** Do not fight it with size modifiers;
  wrap the scaffold instead.
- Every pane KDoc repeats: *"we suggest you to use `[AnimatedPane]` as the root layout of panes"*.

Canonical usage `[SRC@HEAD samples/ThreePaneScaffoldSample.kt:125–159]`:
```kotlin
@OptIn(ExperimentalMaterial3AdaptiveApi::class)
@Preview
@Sampled
@Composable
fun ListDetailPaneScaffoldSample() {
    val coroutineScope = rememberCoroutineScope()
    val scaffoldNavigator = rememberListDetailPaneScaffoldNavigator<NavItemData>()
    val items = listOf("Item 1", "Item 2", "Item 3")
    val selectedItem = scaffoldNavigator.currentDestination?.contentKey

    ListDetailPaneScaffold(
        directive = scaffoldNavigator.scaffoldDirective,
        scaffoldState = scaffoldNavigator.scaffoldState,
        listPane = {
            AnimatedPane(modifier = Modifier.preferredWidth(200.dp)) {
                ListPaneContent(
                    items = items,
                    selectedItem = selectedItem,
                    scaffoldNavigator = scaffoldNavigator,
                    coroutineScope = coroutineScope,
                )
            }
        },
        detailPane = {
            AnimatedPane {
                DetailPaneContent(
                    items = items,
                    selectedItem = selectedItem,
                    scaffoldNavigator = scaffoldNavigator,
                    coroutineScope = coroutineScope,
                )
            }
        },
    )
}
```
Idioms: typed navigator; `directive` + `scaffoldState` both from the navigator;
`selectedItem = navigator.currentDestination?.contentKey`; `preferredWidth` on the **list** pane only
so the detail pane stretches.

Using `ListDetailPaneScaffold` directly means **you** must add
`ThreePaneScaffoldPredictiveBackHandler` — see `pane-navigation-and-expansion.md` §8.

---

## 14. `SupportingPaneScaffold` — both overloads

Same shape; `mainPane` → `primaryPane`, `supportingPane` → `secondaryPane`, and
`paneOrder = SupportingPaneScaffoldDefaults.PaneOrder`.

`[SRC@HEAD SupportingPaneScaffold.kt:72 / :150]`
```kotlin
@ExperimentalMaterial3AdaptiveApi
@Composable
public fun SupportingPaneScaffold(
    directive: PaneScaffoldDirective,
    value: ThreePaneScaffoldValue,
    mainPane: @Composable ThreePaneScaffoldPaneScope.() -> Unit,
    supportingPane: @Composable ThreePaneScaffoldPaneScope.() -> Unit,
    modifier: Modifier = Modifier,
    extraPane: (@Composable ThreePaneScaffoldPaneScope.() -> Unit)? = null,
    paneExpansionDragHandle: (@Composable ThreePaneScaffoldScope.(PaneExpansionState) -> Unit)? =
        null,
    paneExpansionState: PaneExpansionState? = null,
)

@ExperimentalMaterial3AdaptiveApi
@Composable
public fun SupportingPaneScaffold(
    directive: PaneScaffoldDirective,
    scaffoldState: ThreePaneScaffoldState,
    mainPane: @Composable ThreePaneScaffoldPaneScope.() -> Unit,
    supportingPane: @Composable ThreePaneScaffoldPaneScope.() -> Unit,
    modifier: Modifier = Modifier,
    extraPane: (@Composable ThreePaneScaffoldPaneScope.() -> Unit)? = null,
    paneExpansionDragHandle: (@Composable ThreePaneScaffoldScope.(PaneExpansionState) -> Unit)? =
        null,
    paneExpansionState: PaneExpansionState? = null,
)
```
Parameter semantics are identical to §13 with `mainPane` → primary, `supportingPane` → secondary.

Canonical usage `[SRC@HEAD samples/ThreePaneScaffoldSample.kt:271–313]`:
```kotlin
SupportingPaneScaffold(
    directive = scaffoldNavigator.scaffoldDirective,
    scaffoldState = scaffoldNavigator.scaffoldState,
    mainPane = { AnimatedPane { MainPaneContent(/* ... */) } },
    supportingPane = {
        AnimatedPane(modifier = Modifier.preferredWidth(200.dp)) { SupportingPaneContent() }
    },
    extraPane = { AnimatedPane { ExtraPaneContent(/* ... */) } },
    paneExpansionState =
        rememberPaneExpansionState(
            keyProvider = scaffoldNavigator.scaffoldValue,
            anchors = PaneExpansionAnchors,
        ),
    paneExpansionDragHandle = { state -> PaneExpansionDragHandleSample(state) },
)
```
`preferredWidth` goes on the **supporting** pane (the fixed one); main stretches.

### Choosing between the two scaffolds `[DOC]`

- **List-detail**: peer items where selecting one shows its full content — mail, contacts, files,
  settings with sub-screens. The list stands on its own.
- **Supporting-pane**: one primary task plus content that *supports* it — a video with a playlist, a
  document with comments, a timer with statistics. The supporting content is not a peer and rarely
  stands alone; on narrow layouts it belongs **below** the main content, which is exactly why its
  default strategy is `Reflow`.

---

## 15. `ThreePaneScaffold` — is it public?

**No, not as a composable.** `[SRC@HEAD ThreePaneScaffold.kt:80, :109]` — both declarations are
`internal fun ThreePaneScaffold(...)`.

What *is* public in 1.3.0 is only the **override hook** `[API-1.3.0]`:
```
@SuppressCompatibility public final class ThreePaneScaffoldKt {
    property @SuppressCompatibility @androidx.compose.material3.adaptive.ExperimentalMaterial3AdaptiveComponentOverrideApi public static androidx.compose.runtime.ProvidableCompositionLocal<androidx.compose.material3.adaptive.layout.ThreePaneScaffoldOverride> LocalThreePaneScaffoldOverride;
}

@SuppressCompatibility @androidx.compose.material3.adaptive.ExperimentalMaterial3AdaptiveComponentOverrideApi public interface ThreePaneScaffoldOverride {
    method @KotlinOnly @androidx.compose.runtime.Composable public void ThreePaneScaffold(androidx.compose.material3.adaptive.layout.ThreePaneScaffoldOverrideScope);
}
```
`ThreePaneScaffoldOverrideScope` exposes `modifier`, `paneExpansionDragHandle`, `paneExpansionState`,
`paneOrder`, `primaryPane`, `scaffoldDirective`, `scaffoldState`, `secondaryPane`, `tertiaryPane`.

### ☠️ DOOMED — do not build on these

`diff adaptive-layout/api/1.3.0-rc01.txt adaptive-layout/api/current.txt` shows these **removed at
androidx HEAD** — they will not exist in 1.4.0:

- `ThreePaneScaffoldOverride` (interface)
- `ThreePaneScaffoldOverrideScope` (class, 9 properties)
- `ThreePaneScaffoldKt` — the whole class, i.e. **`LocalThreePaneScaffoldOverride`**
- `AnimatedPaneOverride` (interface)
- `AnimatedPaneOverrideScope<Role, ScaffoldValue>` (class, 7 properties)
- `PaneKt.LocalAnimatedPaneOverride`
- `ExperimentalMaterial3AdaptiveComponentOverrideApi` (from the `adaptive` artifact)

They shipped in 1.3.0 and are already gone from main. Anything written against them is a rewrite at
the next release. **For custom three-pane arrangement, build on `ListDetailPaneScaffold` /
`SupportingPaneScaffold`, or write your own `Layout`.** There is no supported route to the internal
`ThreePaneScaffold`.

The marker scopes *are* public and stable `[API-1.3.0]`: `ThreePaneScaffoldScope extends
ExtendedPaneScaffoldScope<ThreePaneScaffoldRole, ThreePaneScaffoldValue>`, and
`ThreePaneScaffoldPaneScope extends ThreePaneScaffoldScope,
ExtendedPaneScaffoldPaneScope<ThreePaneScaffoldRole, ThreePaneScaffoldValue>` — both
`sealed nonexhaustive interface`, both `@ExperimentalMaterial3AdaptiveApi`.

---

## 16. The scope hierarchy — what is available where

```
PaneScaffoldScope                                  (modifiers + saveableStateHolder)
   ▲
   └── ExtendedPaneScaffoldScope<Role, ScaffoldValue>
          extends PaneScaffoldScope, LookaheadScope, PaneScaffoldTransitionScope<Role, ScaffoldValue>
          + focusRequesters: Map<Role, FocusRequester>
          ▲
          └── ThreePaneScaffoldScope   (Role = ThreePaneScaffoldRole, ScaffoldValue = ThreePaneScaffoldValue)

PaneScaffoldPaneScope<Role>                        (paneRole, paneMotion, isInteractable)
   ▲
   └── ExtendedPaneScaffoldPaneScope<Role, ScaffoldValue>
          extends ExtendedPaneScaffoldScope<Role,ScaffoldValue>, PaneScaffoldPaneScope<Role>
          ▲
          └── ThreePaneScaffoldPaneScope  (also extends ThreePaneScaffoldScope)
```

| You are in… | You get |
| --- | --- |
| `listPane` / `detailPane` / `mainPane` / `supportingPane` / `extraPane` | `ThreePaneScaffoldPaneScope` — **everything**: `AnimatedPane`, all modifiers, `paneRole`, `paneMotion`, `isInteractable`, `scaffoldStateTransition`, `motionProgress`, `motionDataProvider`, `focusRequesters`, `saveableStateHolder`, plus `LookaheadScope` |
| `paneExpansionDragHandle` | `ThreePaneScaffoldScope` — modifiers (incl. `paneExpansionDraggable`), transition info, `focusRequesters`, `saveableStateHolder`. **No `paneRole`, no `AnimatedPane`** |

`AnimatedPane` is an extension on `ExtendedPaneScaffoldPaneScope`, so it is callable **only** inside a
pane lambda — never inside the drag-handle lambda.

### `PaneScaffoldScope` — the modifier surface `[SRC@HEAD PaneScaffold.kt]`

```kotlin
public sealed interface PaneScaffoldScope {
    public fun Modifier.preferredWidth(width: Dp): Modifier
    public fun Modifier.preferredWidth(@FloatRange(0.0, 1.0) proportion: Float): Modifier
    public fun Modifier.preferredHeight(height: Dp): Modifier
    public fun Modifier.preferredHeight(@FloatRange(0.0, 1.0) proportion: Float): Modifier

    public fun Modifier.paneExpansionDraggable(
        state: PaneExpansionState,
        minTouchTargetSize: Dp,
        interactionSource: MutableInteractionSource,
        semanticsProperties: (SemanticsPropertyReceiver.() -> Unit)? = null,
    ): Modifier

    @ExperimentalMaterial3AdaptiveApi
    @Composable
    public fun Modifier.paneMargins(vararg insets: RectRulers): Modifier

    @ExperimentalMaterial3AdaptiveApi
    @Composable
    public fun Modifier.paneMargins(
        fixedMargins: PaddingValues,
        vararg insets: RectRulers,
    ): Modifier

    /**
     * The saveable state holder to save pane states across their visibility life-cycles. The
     * default pane implementations like [AnimatedPane] are supposed to use it to store states.
     */
    public val saveableStateHolder: SaveableStateHolder
}
```

| Member | Behaviour (from KDoc + impl) |
| --- | --- |
| `preferredWidth(Dp)` | Respected when the pane renders as a **fixed** pane, *"i.e., a pane that are not stretching to fill the remaining spaces"*. Unset or `Dp.Unspecified` ⇒ `directive.defaultPanePreferredWidth`. **Not** applied when the pane has higher priority and stretches, or when hinge avoidance reshapes it. |
| `preferredWidth(Float)` | Same, as a proportion `0.0..1.0` of scaffold width. |
| `preferredHeight(Dp)` | Respected *"when the associated pane is rendered as a reflowed or a levitated pane"*. Unset ⇒ `directive.defaultPanePreferredHeight` (420.dp). |
| `preferredHeight(Float)` | Proportion of scaffold height; applies in `Reflowed` / `Levitated` states. |
| `paneExpansionDraggable` | *"Dragging the handle horizontally within the pane scaffold"* + a11y actions; also *"excluding system gestures and ensuring minimum touch target size"*. Not `@Composable`, not experimental. |
| `paneMargins(vararg insets)` | Per-pane insets from `RectRulers`; multiple rulers ⇒ **union** (largest margin wins). |
| `paneMargins(fixedMargins, vararg insets)` | Union of fixed margins and insets. KDoc warns margins apply *"against the pane scaffold's bounds"* — the gap between two panes is `directive.horizontalPartitionSpacerSize`, **not** the adjacent panes' margins. |
| `saveableStateHolder` | Used by `AnimatedPane` to preserve pane state across visibility changes. |

Pitfalls:
- Impl validation: `require(width == Dp.Unspecified || width > 0.dp) { "invalid width" }` —
  **`0.dp` throws.** Pass `Dp.Unspecified` for "use the directive default".
- **Multiple `preferredWidth` modifiers: the last applied one wins.**
- Apply `preferredWidth` to the pane you want **fixed**; the higher-priority pane stretches.
- `paneMargins` is the **1.3.0 edge-to-edge story** — pass `WindowInsetsRulers`-derived `RectRulers`
  per pane instead of padding the whole scaffold, so a pane not touching a system bar gets no
  spurious inset.

Sample idiom `[SRC@HEAD samples/ThreePaneScaffoldSample.kt:371–405]` — note `.then(modifier)` last:
```kotlin
AnimatedPane(
    modifier =
        Modifier.preferredWidth(preferredWidth).preferredHeight(preferredHeight).then(modifier)
) {
    content()
}
```

### `PaneScaffoldPaneScope` and `PaneScaffoldTransitionScope` `[SRC@HEAD]`

```kotlin
@ExperimentalMaterial3AdaptiveApi
public sealed interface PaneScaffoldPaneScope<Role : PaneScaffoldRole> {
    /** The role of the current pane in the scope. */
    public val paneRole: Role

    /** The specified pane motion of the current pane in the scope. */
    public val paneMotion: PaneMotion

    /**
     * Indicates if the pane should be interactable, i.e. focusable, clickable, etc. A pane can be
     * non-interactable if it's [PaneAdaptedValue.Hidden] or being covered by a scrim casted by a
     * [PaneAdaptedValue.Levitated] pane.
     */
    public val isInteractable: Boolean
}

@ExperimentalMaterial3AdaptiveApi
public sealed interface PaneScaffoldTransitionScope<
    Role : PaneScaffoldRole,
    ScaffoldValue : PaneScaffoldValue<Role>,
> {
    /** The current scaffold state transition between [PaneScaffoldValue]s. */
    public val scaffoldStateTransition: Transition<ScaffoldValue>

    /** The current motion progress. */
    @get:FloatRange(from = 0.0, to = 1.0) public val motionProgress: Float

    /**
     * Provides measurement and other data required in motion calculation like the size and offset
     * of each pane before and after the motion.
     *
     * Note that the data provided are supposed to be only read proactively by the motion logic
     * "on-the-fly" when the scaffold motion is happening. Using them elsewhere may cause unexpected
     * behavior.
     */
    public val motionDataProvider: PaneScaffoldMotionDataProvider<Role>
}
```
Use `isInteractable` — not a hand-rolled `scaffoldValue` check — to decide whether a pane's controls
should respond; it accounts for levitated scrims.

### `PaneScaffoldParentData` `[API-1.3.0]`
```
public sealed nonexhaustive interface PaneScaffoldParentData {
    property public abstract boolean isAnimatedPane;
    property public abstract androidx.compose.ui.unit.Dp minTouchTargetSize;
    property public abstract androidx.compose.material3.adaptive.layout.PaneMargins paneMargins;
    property public abstract androidx.compose.ui.unit.Dp preferredHeight;
    property public abstract float preferredHeightInProportion;
    property public abstract androidx.compose.ui.unit.Dp preferredWidth;
    property public abstract float preferredWidthInProportion;
}
```
Read-only view of what the modifiers wrote into layout parent data — relevant only if you write a
custom `Layout` measuring pane children.

---

## 17. `AnimatedPane`

**Use `AnimatedPane` as the root of every pane.** Without it: no enter/exit transitions, no bounds
animation, no per-pane saveable state, no focus wiring, and predictive back has nothing to animate.
Every scaffold KDoc says so: *"we suggest you to use `[AnimatedPane]` as the root layout of panes,
which supports default pane behaviors like enter/exit transitions."*

`[SRC@HEAD Pane.kt]` — **HEAD** signature (`shape` is post-1.3.0):
```kotlin
@ExperimentalMaterial3AdaptiveApi
@Composable
public fun <
    RoleT : PaneScaffoldRole,
    ScaffoldValueT : PaneScaffoldValue<RoleT>,
> ExtendedPaneScaffoldPaneScope<RoleT, ScaffoldValueT>.AnimatedPane(
    modifier: Modifier = Modifier,
    enterTransition: EnterTransition = motionDataProvider.calculateDefaultEnterTransition(paneRole),
    exitTransition: ExitTransition = motionDataProvider.calculateDefaultExitTransition(paneRole),
    boundsAnimationSpec: FiniteAnimationSpec<IntRect> = PaneMotionDefaults.AnimationSpec,
    dragToResizeHandle: (@Composable (DragToResizeState) -> Unit)? = null,
    shape: Shape = RectangleShape,
    content: (@Composable AnimatedPaneScope.() -> Unit),
)
```

| Parameter | KDoc |
| --- | --- |
| `modifier` | *"The modifier applied to the `[AnimatedPane]`."* — but it is **split**, see below. |
| `enterTransition` | *"The `[EnterTransition]` used to animate the pane in."* |
| `exitTransition` | *"The `[ExitTransition]` used to animate the pane out."* |
| `boundsAnimationSpec` | *"used to animate the bounds of the pane when the pane is keeping showing but changing its size and/or position."* |
| `dragToResizeHandle` | *"The optional handle which will be shown when the pane is levitated and drag-to-resizable; the handle will be draggable and clickable to resize the pane freely or among collapsed, partially expanded, and expanded states."* |
| `shape` **(post-1.3.0)** | *"The shape of the pane, which will also be applied to the shadow when the pane is levitated."* |
| `content` | *"The content of the `[AnimatedPane]`. Also see `[AnimatedPaneScope]`."* |

### ⚠️ `shape` does NOT exist in 1.3.0

`[API-1.3.0]` parameters are exactly: `modifier`, `enterTransition`, `exitTransition`,
`boundsAnimationSpec`, `dragToResizeHandle`, `content`. `shape` lands **after** 1.3.0, inserted
between `dragToResizeHandle` and `content`; the old arity survives as `@Deprecated @BytecodeOnly`.

`AnimatedPane(shape = RoundedCornerShape(16.dp))` **will not compile against 1.3.0.** For rounded
pane corners at 1.3.0 use `Modifier.clip(RoundedCornerShape(16.dp))` inside the pane — accepting it
will not clip the levitated shadow, which is what `shape` was added to fix.

### What the body actually does `[SRC@HEAD]` — key excerpts

```kotlin
val animatingBounds = paneMotion == PaneMotion.AnimateBounds
val paneValue = scaffoldStateTransition.targetState[paneRole]
val (paneModifier, contentModifier) = modifier.splitPaneAndContentModifiers()
scaffoldStateTransition.AnimatedVisibility(
    visible = { value: ScaffoldValueT -> value[paneRole] != PaneAdaptedValue.Hidden },
    modifier =
        Modifier.animatedPane()
            .animateBounds(/* animateFraction = motionProgress, ... */ enabled = animatingBounds)
            .focusRequester(focusRequesters[paneRole]!!)
            .focusableInWholeTree(isInteractable, paneRole)
            // This is a workaround to b/375496210 - shadows cannot be faded so we have
            // to apply shadows on AnimatedVisibility instead of the content.
            .levitatedProperties(paneValue, shape, dragToResizeHandle != null)
            .then(if (animatingBounds) Modifier else Modifier.clipToBounds())
            // The pane modifiers contains:
            // 1. Size modifiers that have to be applied at this level so the scaffold
            //    can read them from the parent data.
            // 2. The graphics layer modifiers, which have to be applied last so they
            //    can take effect on modifiers (like, shadows) applied before them.
            .then(paneModifier)
            .clip(shape),
    enter = enterTransition,
    exit = exitTransition,
) {
    saveableStateHolder.SaveableStateProvider(paneRole.toString()) {
        Column(modifier = contentModifier) {
            if (
                paneValue is PaneAdaptedValue.Levitated &&
                    paneValue.dragToResizeState != null &&
                    dragToResizeHandle != null
            ) {
                Box(/* ... */.dragToResize(state = paneValue.dragToResizeState, showIndication = true)) {
                    dragToResizeHandle(paneValue.dragToResizeState)
                }
            }
            AnimatedPaneScope.create(this@AnimatedVisibility).content()
        }
    }
}
```

Consequences you must know:
- **Visibility predicate is `value[paneRole] != PaneAdaptedValue.Hidden`** — `Reflowed` and
  `Levitated` panes are still **composed**. Never assume "not expanded ⇒ not composed".
- **`modifier` is split**: size and graphics-layer modifiers go on the `AnimatedVisibility`,
  everything else on the inner `Column`. That is precisely why
  `AnimatedPane(Modifier.preferredWidth(200.dp))` works — the scaffold reads preferred size from
  parent data one level up.
- **Content root is a `Column`, not a `Box`.** `Box` habits (`contentAlignment`, `Modifier.align`)
  do not transfer; children stack vertically.
- Each pane gets `SaveableStateProvider(paneRole.toString())` — **pane state survives being hidden**
  (scroll positions, text fields, expanded rows).
- `dragToResizeHandle` renders only when the pane is `Levitated` **and** its `Levitated` value
  carries a non-null `dragToResizeState`.

`AnimatedPaneScope` `[API-1.3.0]` is a `sealed nonexhaustive interface extends AnimatedVisibilityScope`
with `Companion.create(animatedVisibilityScope)`. Because it extends `AnimatedVisibilityScope`, inside
`AnimatedPane { … }` you can use `this@AnimatedPane` as the scope for shared-element transitions
between list and detail — what the developer.android.com list-detail guide does.

**`AnimatedPaneOverride` / `AnimatedPaneOverrideScope` / `LocalAnimatedPaneOverride` ship in 1.3.0
and are REMOVED at HEAD** — §15.

---

## 18. Motion APIs

`PaneMotion` — **12 singletons** `[API-1.3.0 PaneMotion.Companion]`: `NoMotion`, `AnimateBounds`,
`EnterFromLeft`, `EnterFromRight`, `EnterFromLeftDelayed`, `EnterFromRightDelayed`, `EnterWithExpand`,
`EnterAsModal`, `ExitToLeft`, `ExitToRight`, `ExitWithShrink`, `ExitAsModal`.

`PaneMotion.Type` (`JvmInline` value class) — **6 values**: `Entering`, `EnteringModal`, `Exiting`,
`ExitingModal`, `Hidden`, `Shown`.

`PaneMotionDefaults` — **7 specs** `[API-1.3.0]`:
```
public final class PaneMotionDefaults {
    property public androidx.compose.animation.core.FiniteAnimationSpec<androidx.compose.ui.unit.IntRect> AnimationSpec;
    property public androidx.compose.animation.core.FiniteAnimationSpec<androidx.compose.ui.unit.IntRect> DelayedAnimationSpec;
    property public androidx.compose.animation.core.FiniteAnimationSpec<androidx.compose.ui.unit.IntOffset> DelayedOffsetAnimationSpec;
    property public androidx.compose.animation.core.FiniteAnimationSpec<androidx.compose.ui.unit.IntSize> DelayedSizeAnimationSpec;
    property public androidx.compose.animation.core.FiniteAnimationSpec<androidx.compose.ui.unit.IntOffset> OffsetAnimationSpec;
    property public androidx.compose.animation.core.FiniteAnimationSpec<androidx.compose.ui.unit.IntSize> SizeAnimationSpec;
    property public androidx.compose.animation.core.FiniteAnimationSpec<java.lang.Float> VisibilityAnimationSpec;
    field public static final androidx.compose.material3.adaptive.layout.PaneMotionDefaults INSTANCE;
}
```
`PaneMotionDefaults.AnimationSpec` is `AnimatedPane`'s default `boundsAnimationSpec`.

`PaneMotionData` — per-pane measurement snapshot with `motion`, `originPosition`, `originSize`,
`targetPosition`, `targetSize`.

`PaneScaffoldMotionDataProvider<Role>` `[API-1.3.0]`:
```
public sealed nonexhaustive interface PaneScaffoldMotionDataProvider<Role extends ...PaneScaffoldRole> {
    method public operator androidx.compose.material3.adaptive.layout.PaneMotionData get(int index);
    method public operator androidx.compose.material3.adaptive.layout.PaneMotionData get(Role role);
    method public Role getRoleAt(int index);
    property public abstract int count;
    property public abstract androidx.compose.ui.unit.IntSize scaffoldSize;
}
```

Free functions in `PaneMotionKt`, all extensions on `PaneScaffoldMotionDataProvider<Role>`:
`calculateDefaultEnterTransition(role)`, `calculateDefaultExitTransition(role)`,
`forEach { role, data -> }`, `forEachReversed { role, data -> }`.

`ThreePaneMotion` — `@Immutable`, `operator get(ThreePaneScaffoldRole): PaneMotion`, plus
`ThreePaneMotion.Companion.NoMotion`.

How to use:
- **Do nothing** in the normal case. `AnimatedPane` already defaults `enterTransition` /
  `exitTransition` to `motionDataProvider.calculateDefault*Transition(paneRole)`, which are the
  Material-correct direction-aware motions derived from origin/target geometry.
- Override per pane only for a deliberate effect — you lose that direction-awareness.
- Read `paneMotion` from `PaneScaffoldPaneScope` to branch content on what is happening;
  `paneMotion == PaneMotion.AnimateBounds` means the pane stays visible and resizes, which is when
  `AnimatedPane` skips `clipToBounds()`.
- `motionProgress` / `motionDataProvider` are **on-the-fly reads during motion only** — the KDoc
  warns that using them elsewhere *"may cause unexpected behavior"*.

---

## 19. `PaneMargins`

`[SRC@HEAD PaneMargins.kt]`
```kotlin
public sealed interface PaneMargins {
    public fun Placeable.PlacementScope.getPaneLeft(measuredLeft: Int): Int = measuredLeft
    public fun Placeable.PlacementScope.getPaneTop(measuredTop: Int): Int = measuredTop
    public fun Placeable.PlacementScope.getPaneRight(measuredRight: Int, parentRight: Int): Int = /* ... */
    public fun Placeable.PlacementScope.getPaneBottom(measuredBottom: Int, parentBottom: Int): Int = /* ... */
    public companion object { /* Unspecified */ }
}
```
`[API-1.3.0]`: `@ExperimentalMaterial3AdaptiveApi @Immutable public sealed nonexhaustive interface
PaneMargins`, with `PaneMargins.Companion.Unspecified`.

**`sealed` — you cannot implement it.** It is *produced* by `Modifier.paneMargins(...)` (§16) and read
back via `PaneScaffoldParentData.paneMargins`. `Unspecified` is the no-margins default.

This is the mechanism behind the 1.3.0 release note *"`ListDetailPaneScaffold`/`SupportingPaneScaffold`
support margins + edge-to-edge"*. Prefer per-pane `Modifier.paneMargins(insets)` over padding the
whole scaffold.

---

## 20. Levitation support — `DragToResizeState`, `DockedEdge`, `LevitatedPaneScrim`

`[API-1.3.0]`
```
@SuppressCompatibility @androidx.compose.material3.adaptive.ExperimentalMaterial3AdaptiveApi public enum DockedEdge {
    enum_constant public static final androidx.compose.material3.adaptive.layout.DockedEdge Bottom;
    enum_constant public static final androidx.compose.material3.adaptive.layout.DockedEdge End;
    enum_constant public static final androidx.compose.material3.adaptive.layout.DockedEdge Start;
    enum_constant public static final androidx.compose.material3.adaptive.layout.DockedEdge Top;
}

@androidx.compose.runtime.Stable public abstract class DragToResizeState implements androidx.compose.foundation.gestures.DraggableState {
    method public void dispatchRawDelta(float delta);
    method public suspend Object? drag(optional androidx.compose.foundation.MutatePriority dragPriority, kotlin.jvm.functions.Function2<? super androidx.compose.foundation.gestures.DragScope,? super kotlin.coroutines.Continuation<? super kotlin.Unit>,? extends java.lang.Object?> block, kotlin.coroutines.Continuation<? super kotlin.Unit>);
    field public static final androidx.compose.material3.adaptive.layout.DragToResizeState.Companion Companion;
}

method @KotlinOnly ... @androidx.compose.runtime.Composable public static androidx.compose.material3.adaptive.layout.DragToResizeState rememberDragToResizeState(androidx.compose.material3.adaptive.layout.DockedEdge dockedEdge, optional androidx.compose.ui.unit.Dp minSize, optional androidx.compose.ui.unit.Dp maxSize);

public final class LevitatedPaneScrimDefaults {
    property public androidx.compose.ui.graphics.Color Color;
    field public static final androidx.compose.material3.adaptive.layout.LevitatedPaneScrimDefaults INSTANCE;
}

method @KotlinOnly ... @androidx.compose.runtime.Composable public static void LevitatedPaneScrim(optional androidx.compose.ui.Modifier modifier, optional kotlin.jvm.functions.Function0<kotlin.Unit> onClick, optional androidx.compose.ui.graphics.Color color);
```

`DragToResizeState` implements `DraggableState` and is an **abstract class with an internal
constructor path** — obtain instances only from `rememberDragToResizeState`. `DockedEdge`: `Bottom`
⇒ bottom sheet, `Start`/`End` ⇒ side sheet, `Top` ⇒ top sheet.

**Experimental status, accurately:** `AdaptStrategy.Levitate` and `PaneAdaptedValue.Levitated`
shipped in **1.2.0**. `dragToResizeState` was added later as a third ctor parameter, with the 2-arg
`(alignment, scrim)` ctors retained as `@Deprecated(level = HIDDEN)` for binary compat — so at 1.3.0
the 3-arg form is current. `DragToResizeState`, `DockedEdge`, `rememberDragToResizeState`,
`LevitatedPaneScrim` and `LevitatedPaneScrimDefaults` are all **present in the 1.3.0 signature file
and unchanged at HEAD**. They are experimental in the sense that the whole adaptive surface is
(`@ExperimentalMaterial3AdaptiveApi`), but they are **not doomed** like the `*Override` APIs. Safe to
build on with an opt-in.

### Extra pane as a modal dialog `[SRC@HEAD samples/ThreePaneScaffoldSample.kt:439–478]`
```kotlin
@OptIn(ExperimentalMaterial3AdaptiveApi::class)
@Preview
@Sampled
@Composable
fun <T> levitateAsDialogSample(): ThreePaneScaffoldNavigator<T> {
    val coroutineScope = rememberCoroutineScope()
    val scaffoldDirective = calculatePaneScaffoldDirective(currentWindowAdaptiveInfoV2())
    var navigator: ThreePaneScaffoldNavigator<T>? = null
    val onClick: () -> Unit = { coroutineScope.launch { navigator?.navigateBack() } }
    navigator =
        rememberListDetailPaneScaffoldNavigator<T>(
            scaffoldDirective = scaffoldDirective,
            adaptStrategies =
                SupportingPaneScaffoldDefaults.adaptStrategies(
                    extraPaneAdaptStrategy =
                        AdaptStrategy.Levitate(
                                alignment = Alignment.Center,
                                scrim = {
                                    LevitatedPaneScrim(
                                        Modifier.semantics {
                                            contentDescription = "Scrim"
                                            this.onClick("Dismiss the extra pane") {
                                                onClick()
                                                true
                                            }
                                        },
                                        onClick = onClick,
                                    )
                                },
                            )
                            .onlyIfSinglePane(scaffoldDirective)
                ),
        )
    return navigator
}
```
Idioms: the forward-reference trick (`var navigator: … ? = null`, then assign) so the scrim's
`onClick` can call `navigator?.navigateBack()`; `.onlyIfSinglePane(scaffoldDirective)` so the pane
expands normally when there is room; explicit a11y semantics on the scrim.
⚠️ The sample pairs `SupportingPaneScaffoldDefaults.adaptStrategies` with a **list-detail** navigator.
It compiles (both produce a plain `ThreePaneScaffoldAdaptStrategies`) but the **secondary** pane —
the List — silently inherits the supporting-pane `Reflow` default. Use
`ListDetailPaneScaffoldDefaults.adaptStrategies` with a list-detail navigator.

### Extra pane as a bottom sheet `[SRC@HEAD samples/ThreePaneScaffoldSample.kt:480–514]`
```kotlin
@OptIn(ExperimentalMaterial3AdaptiveApi::class)
@Preview
@Sampled
@Composable
fun <T> levitateAsBottomSheetSample(): ThreePaneScaffoldNavigator<T> {
    val scaffoldDirective = calculatePaneScaffoldDirective(currentWindowAdaptiveInfoV2())
    val dragToResizeState = rememberDragToResizeState(dockedEdge = DockedEdge.Bottom)
    var navigator: ThreePaneScaffoldNavigator<T>? = null
    navigator =
        rememberSupportingPaneScaffoldNavigator<T>(
            scaffoldDirective = scaffoldDirective,
            adaptStrategies =
                SupportingPaneScaffoldDefaults.adaptStrategies(
                    extraPaneAdaptStrategy =
                        AdaptStrategy.Levitate(
                                alignment = Alignment.BottomCenter,
                                dragToResizeState = dragToResizeState,
                            )
                            .onlyIfSinglePane(scaffoldDirective)
                ),
        )
    return navigator
}
```
Paired pane content `[SRC@HEAD samples/ThreePaneScaffoldSample.kt:315–369]`:
```kotlin
extraPane = {
    AnimatedPane(
        modifier =
            Modifier.preferredWidth(1f)
                .preferredHeight(0.5f)
                .background(MaterialTheme.colorScheme.surface),
        dragToResizeHandle = { BottomSheetDefaults.DragHandle() },
    ) {
        ExtraPaneContent(/* ... */)
    }
}
```
Bottom-sheet recipe: `preferredWidth(1f)` + `preferredHeight(0.5f)` (the **proportional** overloads),
an explicit background, and `dragToResizeHandle = { BottomSheetDefaults.DragHandle() }`.

### Reflow `[SRC@HEAD samples/ThreePaneScaffoldSample.kt:426–437]`
```kotlin
@OptIn(ExperimentalMaterial3AdaptiveApi::class)
@Preview
@Sampled
@Composable
fun <T> reflowAdaptStrategySample(): ThreePaneScaffoldNavigator<T> =
    rememberListDetailPaneScaffoldNavigator<T>(
        adaptStrategies =
            ListDetailPaneScaffoldDefaults.adaptStrategies(
                extraPaneAdaptStrategy =
                    AdaptStrategy.Reflow(reflowUnder = ListDetailPaneScaffoldRole.Detail)
            )
    )
```
Only produces a visible reflow when the directive yields
`maxHorizontalPartitions == 1 && maxVerticalPartitions > 1`.

---

## 21. Gotchas

**Directive / size class**

1. **`currentWindowAdaptiveInfo()` is deprecated in 1.3.0** → `currentWindowAdaptiveInfoV2()`. The old
   one defaults `supportLargeAndXLargeWidth = false`, clamping everything ≥840dp to Expanded — you
   silently lose Large/XL, the 3-partition split and the 412dp defaults.
2. **Medium width is single-pane by default.** A 700dp window shows ONE pane. Use
   `calculatePaneScaffoldDirectiveWithTwoPanesOnMediumWidth` only with a strong reason — Google
   recommends against it.
3. **`…WithTwoPanesOnMediumWidth` resets `shouldAutoFocusCurrentDestination` to `true`** because it
   goes through `copy()`, which does not expose that field. Same for any `copy()` you write.
4. **`PaneScaffoldDirective.equals` ignores `shouldAutoFocusCurrentDestination`.**
5. **`copy()` parameter order is `…, excludedBounds, defaultPanePreferredHeight`** — use named args.
6. **`isWidthAtLeastBreakpoint` is `>=`.** Order `when` branches largest→smallest. Width
   `0/600/840/1200/1600`, height `0/480/900`. **Height has no Large/XL.**
7. **Three different `WindowSizeClass` types exist** — `androidx.window.core.layout.WindowSizeClass`
   (current), `androidx.compose.material3.windowsizeclass.WindowSizeClass` (legacy), and the
   deprecated `WindowWidthSizeClass`/`WindowHeightSizeClass` enums. Import carefully;
   `material3-window-size-class` is effectively legacy.
8. **`adaptive-layout` hard-codes 1200/1600** for Large/XL rather than importing `window-core`
   constants (there is a `TODO(conradchen)`), so behaviour is stable even on older `window-core`.
9. **Only *vertical* hinges become `excludedBounds`.** Horizontal (tabletop) hinges act only through
   `isTabletop → maxVerticalPartitions = 2`.
10. **`HingePolicy.toString()` does not match the property names** — never parse it.
11. `DefaultPreferredWidth` / `…XL` / `…Height` are `internal`; hard-code 360/412/420 dp.

**Roles / adaptation**

12. **Role names are scaffold-specific aliases over `Primary`/`Secondary`/`Tertiary`.** In
    list-detail, **List = Secondary** and **Detail = Primary** — the "first" pane is not `Primary`.
    Getting this backwards silently reverses adapt strategies and pane order.
13. **Horizontal pane order differs between the two scaffolds**, and both `PaneOrder` values are
    `internal` — pane order is not configurable.
14. **`SupportingPaneScaffoldDefaults.adaptStrategies()` defaults the supporting pane to `Reflow`,
    not `Hide`.** Re-specify it if you pass custom strategies and still want that behaviour.
15. **Reflow requires BOTH `maxHorizontalPartitions == 1` AND `maxVerticalPartitions > 1`.** On a
    phone in portrait (compact width, medium height) reflow does **not** engage. At most one pane
    reflows.
16. **Levitation only applies to the current destination** — `Levitate` on a non-current pane yields
    `Hidden`.
17. **`AdaptStrategy.Levitate.equals` / `PaneAdaptedValue.Levitated.equals` use identity for
    `scrim` / `dragToResizeState`.** Hoist the scrim lambda or the scaffold value churns every
    recomposition.
18. **`AdaptStrategy.adapt()` is deprecated and ignored** — strategies are matched by type, and the
    interface is `sealed`.
19. `ThreePaneScaffoldValue`'s public ctor takes 3 args; the internal 4th (`currentDestination`) is
    unreachable — always build values via `calculateThreePaneScaffoldValue` or a navigator.

**Scaffolds / panes**

20. **`ListDetailPaneScaffold` / `SupportingPaneScaffold` apply `fillMaxSize()` internally.**
21. **Pane lambdas are required positional params sitting before `modifier`.**
22. **`ThreePaneScaffold` is `internal`** — not callable, and there is no supported custom-order
    scaffold.
23. **`ThreePaneScaffoldOverride`, `ThreePaneScaffoldOverrideScope`, `LocalThreePaneScaffoldOverride`,
    `AnimatedPaneOverride`, `AnimatedPaneOverrideScope`, `LocalAnimatedPaneOverride` and
    `ExperimentalMaterial3AdaptiveComponentOverrideApi` exist in 1.3.0 but are REMOVED at HEAD.**
    Do not build on them.
24. **`AnimatedPane(shape = …)` does not exist in 1.3.0** — post-1.3.0 addition. Use
    `Modifier.clip(...)` inside the pane at 1.3.0.
25. **`AnimatedPane`'s content root is a `Column`**, not a `Box`.
26. **`AnimatedPane` splits your `modifier`** — size/graphics-layer modifiers to the
    `AnimatedVisibility`, everything else to the inner `Column`. That is why
    `Modifier.preferredWidth(...)` works at that call site.
27. **`AnimatedPane` composes `Reflowed` and `Levitated` panes** — the predicate is `!= Hidden`, not
    `== Expanded`.
28. **`Modifier.preferredWidth(0.dp)` throws** (`require(width == Dp.Unspecified || width > 0.dp)`).
    Use `Dp.Unspecified` for "use the default".
29. **Multiple `preferredWidth` modifiers: the last applied one wins.**
30. Use `isInteractable` from `PaneScaffoldPaneScope` — not a hand-rolled check — to decide whether a
    pane's controls should respond; it accounts for levitated scrims.
31. **`defaultDragHandleSemantics(PaneExpansionState)` is deprecated**;
    `Modifier.paneExpansionDraggable` installs default semantics when `semanticsProperties == null`.

---

## 22. Quick reference

```kotlin
val info      = currentWindowAdaptiveInfoV2()
val directive = calculatePaneScaffoldDirective(info)                          // 1 / 1 / 2 / 3
val dense     = calculatePaneScaffoldDirectiveWithTwoPanesOnMediumWidth(info)  // 2 at medium
val flush     = directive.copy(horizontalPartitionSpacerSize = 0.dp)           // Tomato-style
val hingeless = calculatePaneScaffoldDirective(info, HingePolicy.NeverAvoid)

// roles — memorise
ListDetailPaneScaffoldRole.List       == ThreePaneScaffoldRole.Secondary
ListDetailPaneScaffoldRole.Detail     == ThreePaneScaffoldRole.Primary
SupportingPaneScaffoldRole.Main       == ThreePaneScaffoldRole.Primary
SupportingPaneScaffoldRole.Supporting == ThreePaneScaffoldRole.Secondary

ListDetailPaneScaffold(
    directive = navigator.scaffoldDirective,
    scaffoldState = navigator.scaffoldState,   // animated; `value =` is the static overload
    listPane   = { AnimatedPane(Modifier.preferredWidth(360.dp)) { /* fixed */ } },
    detailPane = { AnimatedPane { /* stretches */ } },
    extraPane  = { AnimatedPane { } },
)

ListDetailPaneScaffoldDefaults.adaptStrategies(
    detailPaneAdaptStrategy = AdaptStrategy.Hide,   // Primary
    listPaneAdaptStrategy   = AdaptStrategy.Hide,   // Secondary
    extraPaneAdaptStrategy  = AdaptStrategy.Hide,   // Tertiary
)
SupportingPaneScaffoldDefaults.adaptStrategies(
    supportingPaneAdaptStrategy = AdaptStrategy.Reflow(SupportingPaneScaffoldRole.Main), // the default
)
```

| Constant | Value |
| --- | --- |
| `WIDTH_DP_MEDIUM_LOWER_BOUND` | 600 |
| `WIDTH_DP_EXPANDED_LOWER_BOUND` | 840 |
| `WIDTH_DP_LARGE_LOWER_BOUND` | 1200 |
| `WIDTH_DP_EXTRA_LARGE_LOWER_BOUND` | 1600 |
| `HEIGHT_DP_MEDIUM_LOWER_BOUND` | 480 |
| `HEIGHT_DP_EXPANDED_LOWER_BOUND` | 900 |
| `DefaultPreferredWidth` (internal) | 360.dp |
| `DefaultPreferredWidthXL` (internal) | 412.dp |
| `DefaultPreferredHeight` (internal) | 420.dp |
| partition spacer when >1 partition | 24.dp |

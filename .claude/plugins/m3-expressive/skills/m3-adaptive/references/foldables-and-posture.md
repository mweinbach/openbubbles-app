# Foldables, Posture, and Forced Resizability

Hinges, tabletop/book postures, and the Android 16 resizability rules that removed your ability to
opt out. Target: **`material3-adaptive` 1.3.0**, `androidx.window` 1.5.1+.

Read §8 (Android 16 / API 36) even if you never ship on a foldable. It changes behaviour for every
app on every large screen.

## Provenance markers

| Marker | Meaning |
|---|---|
| **[API-1.3.0]** | Verbatim from the frozen `api/1.3.0-rc01.txt` (byte-identical to shipped 1.3.0). |
| **[SRC@HEAD]** | Verbatim Kotlin at androidx-main HEAD `360e8cba7ae6` (2026-08-14), post-1.3.0. |
| **[DOC]** | developer.android.com, fetched 2026-08-14. |
| **[REPO]** | Verbatim from a cloned sample app. |
| **UNVERIFIED** | Stated but not confirmed against a primary source. |

## The five facts

1. **`Posture` has exactly two members**: `isTabletop: Boolean` and `hingeList: List<HingeInfo>`.
   There is **no `isBookPosture`** and **no `WindowPosture` type**. → §1
2. **Only *vertical* hinges become `excludedBounds`.** Horizontal (tabletop) hinges affect layout
   solely through `isTabletop → maxVerticalPartitions = 2`. → §5, §6
3. **`collectFoldingFeaturesAsState()` emits `emptyList()` first.** The first composed frame has no
   posture info. Never make an irreversible decision on it. → §7
4. **Android 16 (API 36) ignores your orientation, aspect-ratio, and resizability restrictions** on
   any display ≥ sw600dp. 19 manifest overrides are dead. The opt-out property disappears at API 37.
   → §8
5. **`isSeparating` and `isOccluding` are not the same thing.** An occluding hinge is separating;
   a separating hinge need not be occluding. → §1, §3

---

# 1. `Posture` and `HingeInfo`

## Verbatim signatures [API-1.3.0]

```
@androidx.compose.runtime.Immutable public final class Posture {
    ctor public Posture();
    ctor public Posture(optional boolean isTabletop, optional java.util.List<androidx.compose.material3.adaptive.HingeInfo> hingeList);
    method @InaccessibleFromKotlin public java.util.List<androidx.compose.material3.adaptive.HingeInfo> getHingeList();
    method @InaccessibleFromKotlin public boolean isTabletop();
    property public java.util.List<androidx.compose.material3.adaptive.HingeInfo> hingeList;
    property public boolean isTabletop;
}

@androidx.compose.runtime.Immutable public final class HingeInfo {
    ctor public HingeInfo(androidx.compose.ui.geometry.Rect bounds, boolean isFlat, boolean isVertical, boolean isSeparating, boolean isOccluding);
    property public androidx.compose.ui.geometry.Rect bounds;
    property public boolean isFlat;
    property public boolean isOccluding;
    property public boolean isSeparating;
    property public boolean isVertical;
}
```

[SRC@HEAD `adaptive/src/commonMain/kotlin/androidx/compose/material3/adaptive/Posture.kt`]

```kotlin
/**
 * Posture info that can help make layout adaptation decisions. For example when
 * [Posture.separatingVerticalHingeBounds] is not empty, the layout may want to avoid putting any
 * content over those hinge area. We suggest to use [calculatePosture] to retrieve instances of this
 * class in applications, unless you have a strong need of customization that cannot be fulfilled by
 * the default implementation.
 *
 * Note that the hinge bounds will be represent as [Rect] with window coordinates, instead of layout
 * coordinate.
 *
 * @property isTabletop `true` if the current window is considered as in the table top mode, i.e.
 *   there is one half-opened horizontal hinge in the middle of the current window. When this is
 *   `true` it usually means it's hard for users to interact with the window area around the hinge
 *   and developers may consider separating the layout along the hinge and show software keyboard or
 *   other controls in the bottom half of the window.
 * @property hingeList a list of all hinges that are relevant to the posture.
 * @constructor create an instance of [Posture]
 */
@Immutable
public class Posture(
    public val isTabletop: Boolean = false,
    public val hingeList: List<HingeInfo> = emptyList(),
)

/**
 * A class that contains the info of a hinge relevant to a [Posture].
 *
 * @param bounds the bounds of the hinge in the relevant viewport.
 * @param isFlat `true` if the hinge is fully open and the relevant window space presented to the
 *   user is flat.
 * @param isVertical `true` if the hinge is a vertical one, i.e., it separates the viewport into
 *   left and right; `false` if the hinge is horizontal, i.e., it separates the viewport into top
 *   and bottom.
 * @param isSeparating `true` if the hinge creates two logical display areas.
 * @param isOccluding `true` if the hinge conceals part of the display.
 */
@Immutable
public class HingeInfo(
    public val bounds: Rect,
    public val isFlat: Boolean,
    public val isVertical: Boolean,
    public val isSeparating: Boolean,
    public val isOccluding: Boolean,
)
```

## Field meanings

| Field | Type | Meaning |
|---|---|---|
| `Posture.isTabletop` | `Boolean` | Any horizontal, half-opened hinge exists. The device is laid open like a laptop. Interaction near the hinge is awkward; split the layout along it. |
| `Posture.hingeList` | `List<HingeInfo>` | Every hinge relevant to the current window. Usually 0 or 1; dual-hinge devices can report more. |
| `HingeInfo.bounds` | `Rect` | **Window coordinates**, not layout coordinates. Do not pass straight into a `Modifier.offset` on a nested composable. |
| `HingeInfo.isFlat` | `Boolean` | The fold is fully open (`State.FLAT`) and the presented space is planar. |
| `HingeInfo.isVertical` | `Boolean` | `true` = splits left/right (book). `false` = splits top/bottom (tabletop). |
| `HingeInfo.isSeparating` | `Boolean` | The hinge creates **two logical display areas**. Content must not straddle it. |
| `HingeInfo.isOccluding` | `Boolean` | The hinge **physically conceals** part of the display. Pixels behind it are invisible. |

**`isSeparating` vs `isOccluding`.** The androidx KDoc states the relation directly:
> *"an occluding hinge is supposed to be separating as well but not vice versa."*

So: `isOccluding ⇒ isSeparating`. A half-opened foldable with a seamless inner display is
**separating but not occluding** — the pixels exist, but placing a button across the crease is bad.
A device with a physical gap between two panels is **both**.

Pick the predicate by the question you are asking:

| Question | Predicate |
|---|---|
| "Would content here be invisible?" | `isOccluding` |
| "Would content here be split across two logical areas / awkward to touch?" | `isSeparating` |
| "Is there a hinge at all, regardless of state?" | neither — check `hingeList.isNotEmpty()` |

## What is NOT there

> **`Posture` has NO `isBookPosture`.** Only `isTabletop`. Book posture must be derived yourself from
> a vertical, half-opened `FoldingFeature` (§4). The library models book mode indirectly via
> `separatingVerticalHingeBounds → PaneScaffoldDirective.excludedBounds`.

> There is **no type named `WindowPosture`**. The property on `WindowAdaptiveInfo` is
> `windowPosture: Posture`.

> `Posture` exposes **no hinge angle**. [DOC]: *"`FoldingFeature` does NOT expose angle; sensor
> accuracy varies by device."*

---

# 2. `calculatePosture` — the framework → Material bridge

`calculatePosture()` is `internal expect` in common, `internal actual` on Android. **It is not public
API.** You get a `Posture` through `WindowAdaptiveInfo`:

```kotlin
val posture = currentWindowAdaptiveInfoV2().windowPosture
```

[SRC@HEAD `adaptive/src/commonMain/.../WindowAdaptiveInfo.kt`]
```kotlin
@Composable internal expect fun calculatePosture(): Posture
```

[SRC@HEAD `adaptive/src/androidMain/.../AndroidPosture.android.kt`] — the whole implementation:

```kotlin
/**
 * Calculates the [Posture] for a given list of [FoldingFeature]s. This methods converts framework
 * folding info into the Material-opinionated posture info.
 */
@VisibleForTesting
internal fun calculatePosture(foldingFeatures: List<FoldingFeature>): Posture {
    var isTableTop = false
    val hingeList = mutableListOf<HingeInfo>()
    @Suppress("ListIterator")
    foldingFeatures.forEach {
        if (
            it.orientation == FoldingFeature.Orientation.HORIZONTAL &&
                it.state == FoldingFeature.State.HALF_OPENED
        ) {
            isTableTop = true
        }
        hingeList.add(
            HingeInfo(
                bounds = it.bounds.toComposeRect(),
                isFlat = it.state == FoldingFeature.State.FLAT,
                isVertical = it.orientation == FoldingFeature.Orientation.VERTICAL,
                isSeparating = it.isSeparating,
                isOccluding = it.occlusionType == FoldingFeature.OcclusionType.FULL,
            )
        )
    }
    return Posture(isTableTop, hingeList)
}

@OptIn(ExperimentalMaterial3AdaptiveApi::class)
@Composable
internal actual fun calculatePosture(): Posture =
    calculatePosture(collectFoldingFeaturesAsState().value)
```

## Exact mapping table

| `Posture` / `HingeInfo` field | Derived from `androidx.window.layout.FoldingFeature` |
|---|---|
| `Posture.isTabletop` | **any** feature with `orientation == HORIZONTAL && state == HALF_OPENED` |
| `HingeInfo.bounds` | `feature.bounds.toComposeRect()` |
| `HingeInfo.isFlat` | `feature.state == State.FLAT` |
| `HingeInfo.isVertical` | `feature.orientation == Orientation.VERTICAL` |
| `HingeInfo.isSeparating` | `feature.isSeparating` (passed through unchanged) |
| `HingeInfo.isOccluding` | `feature.occlusionType == OcclusionType.FULL` |

Note `isTabletop` is an **OR across all features** — with two hinges, one horizontal half-opened is
enough. Note also that `isFlat` and `isVertical` are per-hinge, while `isTabletop` is per-window.

## `FoldingFeature` reference [DOC]

| Property | Values | Purpose |
|---|---|---|
| `state` | `FLAT`, `HALF_OPENED` | Fold state of device |
| `orientation` | `HORIZONTAL`, `VERTICAL` | Fold/hinge direction |
| `occlusionType` | `NONE`, `FULL` | Whether fold conceals display |
| `isSeparating` | `true`, `false` | Creates two logical display areas |
| `bounds` | Rectangle | Bounding box of fold/hinge |

There is no `CLOSED` state in this enum — a closed foldable reports the cover display's window with
no folding feature at all.

---

# 3. The six hinge-bounds extension properties

[SRC@HEAD `Posture.kt`] — public extensions on `Posture`, no opt-in:

```kotlin
/** Returns the list of vertical hinge bounds that are separating. */
public val Posture.separatingVerticalHingeBounds: List<Rect>
    get() = hingeList.getBounds { isVertical && isSeparating }

/** Returns the list of vertical hinge bounds that are occluding. */
public val Posture.occludingVerticalHingeBounds: List<Rect>
    get() = hingeList.getBounds { isVertical && isOccluding }

/** Returns the list of all vertical hinge bounds. */
public val Posture.allVerticalHingeBounds: List<Rect>
    get() = hingeList.getBounds { isVertical }

/** Returns the list of horizontal hinge bounds that are separating. */
public val Posture.separatingHorizontalHingeBounds: List<Rect>
    get() = hingeList.getBounds { !isVertical && isSeparating }

/** Returns the list of horizontal hinge bounds that are occluding. */
public val Posture.occludingHorizontalHingeBounds: List<Rect>
    get() = hingeList.getBounds { !isVertical && isOccluding }

/** Returns the list of all horizontal hinge bounds. */
public val Posture.allHorizontalHingeBounds: List<Rect>
    get() = hingeList.getBounds { !isVertical }

private inline fun List<HingeInfo>.getBounds(predicate: HingeInfo.() -> Boolean): List<Rect> =
    @Suppress("ListIterator") mapNotNull { if (it.predicate()) it.bounds else null }
```

## Which one, when

| Property | Filter | Use it when |
|---|---|---|
| `allVerticalHingeBounds` | `isVertical` | You want to avoid the crease **always**, even when the device is flat and the display is seamless. Most conservative; produces gaps on flat unfolded devices where none is needed. Maps to `HingePolicy.AlwaysAvoid`. |
| `separatingVerticalHingeBounds` | `isVertical && isSeparating` | **The default and the right answer in almost every case.** Avoid the crease exactly when the system says the display is logically split. Maps to `HingePolicy.AvoidSeparating`. |
| `occludingVerticalHingeBounds` | `isVertical && isOccluding` | You only care about pixels the user physically cannot see — e.g. you tolerate a control straddling a seamless crease but not one hidden by a bezel. Maps to `HingePolicy.AvoidOccluding`. |
| `allHorizontalHingeBounds` | `!isVertical` | Custom tabletop layouts. **Not consulted by any library directive** — you must use these yourself. |
| `separatingHorizontalHingeBounds` | `!isVertical && isSeparating` | Positioning a tabletop split yourself: place video above `bounds.top`, controls below `bounds.bottom`. |
| `occludingHorizontalHingeBounds` | `!isVertical && isOccluding` | Rare; a horizontal hinge that hides pixels. |

**The asymmetry that catches people:** `getExcludedVerticalBounds` is the **only** call site inside
the library. Horizontal hinges never populate `excludedBounds` in the default directive calculation.
If you want a tabletop layout that respects the physical hinge rect (rather than just splitting into
two equal vertical partitions), you must read `separatingHorizontalHingeBounds` yourself.

Bounds are in **window coordinates**. To use them inside a nested composable, convert via
`LayoutCoordinates` / `onGloballyPositioned` — do not assume layout-local origin.

---

# 4. Tabletop and book postures

## Tabletop

> [DOC] **Definition**: Phone sits on surface with horizontal hinge, screen half-opened.
> **Use Cases**: Media playback, video calls, hands-free viewing.
> **Layout Strategy**: Place video/primary content above fold; controls and supplementary content
> below fold.

Detection — the library way:
```kotlin
val posture = currentWindowAdaptiveInfoV2().windowPosture
if (posture.isTabletop) { /* ... */ }
```

Detection — raw, when you have a `FoldingFeature` in hand [DOC, and byte-identical to androidify's
`LayoutUtils.kt`]:
```kotlin
fun isTableTopPosture(foldFeature: FoldingFeature?): Boolean {
    contract { returns(true) implies (foldFeature != null) }
    return foldFeature?.state == FoldingFeature.State.HALF_OPENED &&
        foldFeature.orientation == FoldingFeature.Orientation.HORIZONTAL
}
```
This is exactly what `calculatePosture` computes for `Posture.isTabletop`.

**What tabletop should produce:** a layout split along the fold — watched/read content above,
touched content below. Content breakdown in §14. The Material rationale is in the `Posture` KDoc:
*"it's hard for users to interact with the window area around the hinge and developers may consider
separating the layout along the hinge and show software keyboard or other controls in the bottom
half of the window."*

The library gives you this via `maxVerticalPartitions = 2` (§6) plus `AdaptStrategy.Reflow` — the
supporting pane reflows **below** the main pane. `SupportingPaneScaffoldDefaults.adaptStrategies()`
already defaults the supporting pane to `Reflow`, not `Hide`.

The nav suite also reacts: `NavigationSuiteScaffoldDefaults.navigationSuiteType` returns
`ShortNavigationBarMedium` (bottom bar) when `isTabletop`, so navigation stays in the reachable lower
half rather than as a side rail spanning the crease [SRC@HEAD].

## Book

> [DOC] **Definition**: Device half-opened with vertical hinge.
> **Use Cases**: E-book reading, dual-page layout, hands-free photography.
> **Layout Strategy**: Create two-page layout mimicking open book; hinge acts as natural separator.

Detection — you must write this yourself [DOC]:
```kotlin
fun isBookPosture(foldFeature: FoldingFeature?): Boolean {
    contract { returns(true) implies (foldFeature != null) }
    return foldFeature?.state == FoldingFeature.State.HALF_OPENED &&
        foldFeature.orientation == FoldingFeature.Orientation.VERTICAL
}
```

> **There is no `Posture.isBookPosture`.** The adaptive library handles book posture implicitly: a
> vertical separating hinge lands in `separatingVerticalHingeBounds` → `excludedBounds` → the
> scaffold splits panes around the hinge.

**What book posture should produce:** a genuine two-column reading layout where the hinge is the
gutter. Left page and right page are peers — this is *not* list-detail. Text columns must terminate
before `bounds.left` and resume after `bounds.right`; a line of prose that runs through the crease is
the failure mode.

If you are using a pane scaffold you get this for free: `calculatePaneScaffoldDirective` puts the
vertical hinge rect in `excludedBounds` and the scaffold lays partitions out around it. If you are
hand-rolling, read `separatingVerticalHingeBounds.firstOrNull()` and use `bounds.left` / `bounds.right`
as your column boundaries.

Reply computes a richer posture model by hand, if you need one
[REPO `compose-samples/Reply/.../ReplyApp.kt`]:
```kotlin
val foldingFeature = displayFeatures.filterIsInstance<FoldingFeature>().firstOrNull()

val foldingDevicePosture = when {
    isBookPosture(foldingFeature) ->
        DevicePosture.BookPosture(foldingFeature.bounds)
    isSeparating(foldingFeature) ->
        DevicePosture.Separating(foldingFeature.bounds, foldingFeature.orientation)
    else -> DevicePosture.NormalPosture
}
```

## Flat but separating

> [DOC] **Dual-screen devices**: Always use layouts for tabletop/book **even when `FLAT`** if
> `isSeparating == true`.

A dual-screen device (two physical panels) reports `FLAT` with `isSeparating = true` and usually
`isOccluding = true`. `Posture.isTabletop` will be **false** (it requires `HALF_OPENED`), but the
bounds still land in `separatingVerticalHingeBounds`. This is precisely why `HingePolicy.AvoidSeparating`
is the default rather than something keyed on `isTabletop`.

---

# 5. `HingePolicy`

[API-1.3.0]
```
@androidx.compose.runtime.Immutable @kotlin.jvm.JvmInline public final value class HingePolicy {
    field public static final androidx.compose.material3.adaptive.layout.HingePolicy.Companion Companion;
}

public static final class HingePolicy.Companion {
    property public androidx.compose.material3.adaptive.layout.HingePolicy AlwaysAvoid;
    property public androidx.compose.material3.adaptive.layout.HingePolicy AvoidOccluding;
    property public androidx.compose.material3.adaptive.layout.HingePolicy AvoidSeparating;
    property public androidx.compose.material3.adaptive.layout.HingePolicy NeverAvoid;
}
```

[SRC@HEAD]
```kotlin
/** Policies that indicate how hinges are supposed to be addressed in an adaptive layout. */
@Immutable
@JvmInline
public value class HingePolicy private constructor(private val value: Int) {
    public override fun toString(): String {
        return "HingePolicy." +
            when (this) {
                AlwaysAvoid -> "AlwaysAvoid"
                AvoidSeparating -> "AvoidOccludingAndSeparating"
                AvoidOccluding -> "AvoidOccludingOnly"
                NeverAvoid -> "NeverAvoid"
                else -> ""
            }
    }

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
}
```

## What each value does to pane splitting

The policy selects which `Posture` bounds list becomes `PaneScaffoldDirective.excludedBounds`
[SRC@HEAD]:

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

| `HingePolicy` | Int | `excludedBounds` source | Effect on pane splitting |
|---|---|---|---|
| `AlwaysAvoid` | 0 | `posture.allVerticalHingeBounds` | Panes always split at the crease, even on a fully-flat seamless display. Produces a visible gap where the hardware needs none. |
| `AvoidSeparating` | 1 | `posture.separatingVerticalHingeBounds` | **DEFAULT.** Panes split at the crease exactly when the system reports two logical display areas. |
| `AvoidOccluding` | 2 | `posture.occludingVerticalHingeBounds` | Panes split only where pixels are physically hidden. On a seamless half-opened foldable this yields **no** split — content spans the crease. |
| `NeverAvoid` | 3 | `emptyList()` | Hinges are ignored entirely. Panes are laid out as if the display were unbroken. Use only for full-bleed media that intentionally spans the fold. |

`AvoidSeparating` is the default parameter value of both `calculatePaneScaffoldDirective` and
`calculatePaneScaffoldDirectiveWithTwoPanesOnMediumWidth`.

```kotlin
val directive  = calculatePaneScaffoldDirective(info)                              // AvoidSeparating
val fullBleed  = calculatePaneScaffoldDirective(info, HingePolicy.NeverAvoid)
val paranoid   = calculatePaneScaffoldDirective(info, HingePolicy.AlwaysAvoid)
```

**Only vertical hinges are consulted.** `getExcludedVerticalBounds` is the only call site;
`HingePolicy` has no effect whatsoever on horizontal (tabletop) hinges.

> **Do not parse `HingePolicy.toString()`.** It returns
> `"HingePolicy.AvoidOccludingAndSeparating"` for `AvoidSeparating` and `"HingePolicy.AvoidOccludingOnly"`
> for `AvoidOccluding` — the strings do not match the property names.

---

# 6. How posture feeds `calculatePaneScaffoldDirective`

Posture enters the directive at exactly two points. [SRC@HEAD], the relevant excerpt:

```kotlin
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

    // ...
    return PaneScaffoldDirective(
        // ...
        excludedBounds =
            getExcludedVerticalBounds(windowAdaptiveInfo.windowPosture, verticalHingePolicy),
    )
```

## The two entry points

**1. `isTabletop` → `maxVerticalPartitions`.**

| Condition | `maxVerticalPartitions` | `verticalPartitionSpacerSize` |
|---|---|---|
| `posture.isTabletop == true` | **2** | 24.dp |
| `maxHorizontalPartitions == 1 && minHeight == 900` (Expanded height) | **2** | 24.dp |
| otherwise | 1 | 0.dp |

Read the condition precisely: `maxVerticalPartitions = 2` **iff** tabletop **or**
(single-pane-horizontally **and** expanded height). The second clause fires on a tall portrait tablet
even with no foldable hardware at all.

**2. `HingePolicy` + vertical hinges → `excludedBounds`** (§5). `excludedBounds` is a `List<Rect>` in
window coordinates that the scaffold's measurement pass refuses to paint on.

## Why `maxVerticalPartitions` matters

**Reflow requires BOTH `maxHorizontalPartitions == 1` AND `maxVerticalPartitions > 1`.**

`AdaptStrategy.Reflow` places a pane *underneath* its anchor pane rather than hiding it. With the
default directive, `maxVerticalPartitions` is 2 only in tabletop posture or
compact/medium-width + expanded-height. **On a normal phone in portrait (compact width, medium
height) reflow does not engage** — the supporting pane is hidden, not reflowed. If your supporting
pane "isn't reflowing", this is why.

Tabletop is therefore the posture that makes reflow work as intended: main content above the fold,
reflowed supporting pane below it.

## The full width truth table, for context

| Width bucket (`minWidth`) | `maxHorizontalPartitions` | gutter | `defaultPanePreferredWidth` |
|---|---|---|---|
| Compact (0dp) | 1 | 0.dp | 360.dp |
| Medium (600dp) | 1 | 0.dp | 360.dp |
| Expanded (840dp) | 2 | 24.dp | 360.dp |
| Large (1200dp) | 3 | 24.dp | 412.dp |
| Extra-large (1600dp) | 3 | 24.dp | 412.dp |

`defaultPanePreferredHeight` is **always 420.dp** regardless of size class or posture.

Full width/breakpoint detail lives in `window-size-classes.md`.

## `excludedBounds` in the directive

[SRC@HEAD `PaneScaffoldDirective.kt`], the relevant KDoc:
> `@property excludedBounds` the bounds of all areas in the window that the layout needs to avoid
> displaying anything upon it. Usually these bounds represent where physical hinges are.

If you build a `PaneScaffoldDirective` by hand (or via `copy`), you own `excludedBounds` — the
hinge-avoidance behaviour is not re-derived. Copying a directive computed by
`calculatePaneScaffoldDirective` preserves it:

```kotlin
val custom = calculatePaneScaffoldDirective(info)
    .copy(maxHorizontalPartitions = 2, horizontalPartitionSpacerSize = 16.dp)
// excludedBounds carried over — hinge avoidance intact
```

Note `copy` does **not** expose `shouldAutoFocusCurrentDestination` and always resets it to `true`.

---

# 7. Getting fold info yourself

## `collectFoldingFeaturesAsState()`

[API-1.3.0] `AndroidWindowAdaptiveInfo_androidKt`
```
method @KotlinOnly @androidx.compose.runtime.Composable public static androidx.compose.runtime.State<java.util.List<androidx.window.layout.FoldingFeature>> collectFoldingFeaturesAsState();
```

[SRC@HEAD `adaptive/src/androidMain/.../AndroidWindowAdaptiveInfo.android.kt`]
```kotlin
/**
 * Collects the current window folding features from [WindowInfoTracker] in to a [State].
 *
 * @return a [State] of a [FoldingFeature] list.
 */
@Composable
public fun collectFoldingFeaturesAsState(): State<List<FoldingFeature>> {
    val context = LocalContext.current
    return remember(context) {
            WindowInfoTracker.getOrCreate(context).windowLayoutInfo(context).map {
                @Suppress("ListIterator") it.displayFeatures.filterIsInstance<FoldingFeature>()
            }
        }
        .collectAsState(emptyList())
}
```

This is **android-only** (`androidMain`) and is the bridge from Jetpack WindowManager to Compose. No
opt-in required.

> **It starts with `emptyList()`.** The first composition has no folding features, so the first frame
> always reports `isTabletop = false` and an empty `hingeList`. Do not trigger navigation, start a
> camera session, or persist a layout choice off the first emission. Let recomposition settle.

Prefer this over hand-rolling `WindowInfoTracker` in Compose — it already does the right thing.

## Raw WindowManager (Views, or outside composition) [DOC]

```kotlin
class DisplayFeaturesActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        lifecycleScope.launch(Dispatchers.Main) {
            lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
                WindowInfoTracker.getOrCreate(this@DisplayFeaturesActivity)
                    .windowLayoutInfo(this@DisplayFeaturesActivity)
                    .collect { newLayoutInfo ->
                        // Use newLayoutInfo to update the layout
                    }
            }
        }
    }
}
```

## Detecting *supported* postures (Android 15+ / WindowSdkExtensions 6+)

Ask whether the hardware can ever enter a posture, rather than whether it is in one now — useful for
deciding whether to build the affordance at all [DOC]:

```kotlin
if (WindowSdkExtensions.getInstance().extensionVersion >= 6) {
    val postures = WindowInfoTracker.getOrCreate(context).supportedPostures
    if (postures.contains(SupportedPosture.TABLETOP)) {
        // Device supports tabletop posture
    }
}
```

androidify wraps it [REPO `androidify/core/util/.../LayoutUtils.kt`]:
```kotlin
@SuppressLint("RequiresWindowSdk")
@Composable
fun supportsTabletop(): Boolean {
    return if (WindowSdkExtensions.getInstance().extensionVersion >= 6) {
        val postures = WindowInfoTracker.getOrCreate(LocalContext.current).supportedPostures
        postures.contains(SupportedPosture.TABLETOP)
    } else {
        false
    }
}
```

Note the `@SuppressLint("RequiresWindowSdk")` and the explicit version guard — `supportedPostures`
throws or is unavailable below extension version 6.

---

# 8. Rear display and dual screen (`WindowAreaController`)

Moving your UI to the outer/cover display, or presenting different content on a second panel.
[REPO `snippets:.../adaptivelayouts/SupportFoldableDisplayModes.kt`] — verbatim:

```kotlin
// [START android_adaptive_foldable_vars]
private lateinit var windowAreaController: WindowAreaController
private lateinit var displayExecutor: Executor
private var windowAreaSession: WindowAreaSessionPresenter? = null
private var windowAreaInfo: WindowArea? = null
private var capabilityStatus: WindowAreaCapability.Status =
    WindowAreaCapability.Status.WINDOW_AREA_STATUS_UNSUPPORTED

private val dualScreenOperation = WindowAreaCapability.Operation.OPERATION_PRESENT_ON_AREA
private val rearDisplayOperation = WindowAreaCapability.Operation.OPERATION_TRANSFER_TO_AREA
// [END android_adaptive_foldable_vars]

// [START android_adaptive_foldable_init]
displayExecutor = ContextCompat.getMainExecutor(this)
windowAreaController = WindowAreaController.getOrCreate()

lifecycleScope.launch(Dispatchers.Main) {
    lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
        windowAreaController.windowAreaInfos()
            .map { info -> info.firstOrNull { it.type == WindowArea.Type.TYPE_REAR_FACING } }
            .onEach { info -> windowAreaInfo = info }
            .map { it?.getCapability(operation)?.status ?: WindowAreaCapability.Status.WINDOW_AREA_STATUS_UNSUPPORTED }
            .distinctUntilChanged()
            .collect {
                capabilityStatus = it
            }
    }
}
// [END android_adaptive_foldable_init]

// [START android_adaptive_toggle_dual_screen]
fun toggleDualScreenMode() {
    if (windowAreaSession != null) {
        windowAreaSession?.close()
    }
    else {
        windowAreaInfo?.token?.let { token ->
            windowAreaController.presentContentOnWindowArea(
                windowAreaToken = token,
                activity = this,
                executor = displayExecutor,
                windowAreaPresentationSessionCallback = this
            )
        }
    }
}
// [END android_adaptive_toggle_dual_screen]

// [START android_adaptive_toggle_rear_display]
fun toggleRearDisplayMode() {
    if(capabilityStatus == WindowAreaCapability.Status.WINDOW_AREA_STATUS_ACTIVE) {
        windowAreaController.transferToWindowArea(null, this)
    } else {
        windowAreaInfo?.token?.let { token ->
            windowAreaController.transferToWindowArea(token, this)
        }
    }
}
// [END android_adaptive_toggle_rear_display]
```

| Operation | Constant | Meaning |
|---|---|---|
| Dual screen | `OPERATION_PRESENT_ON_AREA` | Show *additional* content on the rear panel while the main UI stays put. Returns a `WindowAreaSessionPresenter`. |
| Rear display | `OPERATION_TRANSFER_TO_AREA` | *Move* the activity to the rear display — the canonical rear-camera-selfie flow. |

Capability statuses: `WINDOW_AREA_STATUS_UNSUPPORTED`, `WINDOW_AREA_STATUS_UNAVAILABLE`,
`WINDOW_AREA_STATUS_AVAILABLE`, `WINDOW_AREA_STATUS_ACTIVE`.

Both are `androidx.window:window` APIs (`WindowAreaController`, `WindowArea`, `WindowAreaCapability`,
`WindowAreaSessionPresenter`), not `material3-adaptive`. They are Activity-scoped and callback-based,
not Compose-native. `transferToWindowArea(null, this)` transfers *back*.

Nothing in `material3-adaptive` models these modes; after a transfer, the window changes and
`currentWindowAdaptiveInfoV2()` recomposes with the new size class — which is all the adaptive layer
needs to know.

---

# 9. Android 16 / API 36: large screens can no longer opt out of resizing

**This is the section most developers do not know about.** It applies to every app, foldable or not.

[DOC `developer.android.com/guide/topics/large-screens/large-screen-compatibility-mode`]

> "Android 16 (API level 36) ignores screen orientation, aspect ratio, and app resizability
> restrictions to improve the layout of apps on form factors with smallest width >= 600dp."

If your app targets API 36 and runs on any display with smallest-width ≥ 600dp — tablets, unfolded
foldables, ChromeOS, desktop windowing, connected displays — the system **ignores** your requests to
be portrait-only, fixed-aspect-ratio, or non-resizable. Your app will be rotated and resized. If the
layout was never tested wide, users see the broken version.

## All 19 dead per-app overrides

These become non-functional for apps targeting API 36, verbatim from [DOC]:

**Resizability (2):** `FORCE_RESIZE_APP`, `FORCE_NON_RESIZE_APP`

**Aspect ratio (6):** `OVERRIDE_MIN_ASPECT_RATIO`, `OVERRIDE_MIN_ASPECT_RATIO_PORTRAIT_ONLY`,
`OVERRIDE_MIN_ASPECT_RATIO_MEDIUM`, `OVERRIDE_MIN_ASPECT_RATIO_LARGE`,
`OVERRIDE_MIN_ASPECT_RATIO_TO_ALIGN_WITH_SPLIT_SCREEN`,
`OVERRIDE_MIN_ASPECT_RATIO_EXCLUDE_PORTRAIT_FULLSCREEN`

**Orientation (10):** `OVERRIDE_ANY_ORIENTATION`, `OVERRIDE_ANY_ORIENTATION_TO_USER`,
`OVERRIDE_UNDEFINED_ORIENTATION_TO_PORTRAIT`, `OVERRIDE_UNDEFINED_ORIENTATION_TO_NOSENSOR`,
`OVERRIDE_LANDSCAPE_ORIENTATION_TO_REVERSE_LANDSCAPE`, `OVERRIDE_ORIENTATION_ONLY_FOR_CAMERA`,
`OVERRIDE_USE_DISPLAY_LANDSCAPE_NATURAL_ORIENTATION`,
`OVERRIDE_ENABLE_COMPAT_IGNORE_REQUESTED_ORIENTATION`,
`OVERRIDE_ENABLE_COMPAT_IGNORE_ORIENTATION_REQUEST_WHEN_LOOP_DETECTED`,
`OVERRIDE_RESPECT_REQUESTED_ORIENTATION`

**Insets (1):** `OVERRIDE_EXCLUDE_CAPTION_INSETS_FROM_APP_BOUNDS`

(The grouping is added for readability; the list itself is the complete verbatim set.)

## The temporary opt-out

Application-wide:
```xml
<application ...>
    <property
        android:name="android.window.PROPERTY_COMPAT_ALLOW_RESTRICTED_RESIZABILITY"
        android:value="true" />
</application>
```

Per-activity:
```xml
<activity ...>
    <property
        android:name="android.window.PROPERTY_COMPAT_ALLOW_RESTRICTED_RESIZABILITY"
        android:value="true" />
    ...
</activity>
```

## The opt-out is going away at API 37

> "The Android framework will eliminate the opt-out capability in API level 37. For apps that target
> API level 37 or higher, orientation, aspect ratio, and resizability restrictions will always be
> ignored on displays that are at least sw600dp."

> "If your app targets Android 16 (API level 36) or higher, this property doesn't lock the display
> orientation or prevent screen rotation on large displays."

Treat `PROPERTY_COMPAT_ALLOW_RESTRICTED_RESIZABILITY` as a **migration deadline marker**, not a
solution. Adding it buys one API level.

## Constraints that survive the opt-out

> - All overrides other than `OVERRIDE_ANY_ORIENTATION_TO_USER` remain applicable **even if your app
>   opts out**.
> - Users can override app aspect ratio in **Settings** even if your app opts out.
> - **Desktop windowing:** orientation restrictions are overridden **despite** the opt-out;
>   resizability restrictions are respected if opted out (though scaled resizing can be applied).

So even with the property set, you do not get the old behaviour back. Desktop windowing in particular
ignores your orientation lock unconditionally.

## Required work [DOC]

1. Remove orientation restrictions from your manifest.
2. Remove aspect ratio restrictions (`minAspectRatio`, `maxAspectRatio`).
3. Set `resizeableActivity="true"` to explicitly support resizing.
4. Use responsive/adaptive layouts that work on all display sizes.
5. Test on large screen devices to ensure compatibility.

> "App should support all device and multi-window mode display orientations and sizes. Remove all
> orientation and fixed aspect ratio restrictions from your app layouts and app manifest file."

**Concretely, delete these from `AndroidManifest.xml`:**
```xml
<!-- ❌ all of these are now liabilities -->
android:screenOrientation="portrait"
android:resizeableActivity="false"
android:minAspectRatio="..."
android:maxAspectRatio="..."
```

---

# 10. Desktop windowing and freeform windows

Desktop windowing is precisely why the **Large (1200dp)** and **Extra-large (1600dp)** width classes
exist, and why `calculatePaneScaffoldDirective` returns `maxHorizontalPartitions = 3` and
`defaultPanePreferredWidth = 412.dp` there.

## What changes

| Aspect | Change |
|---|---|
| Window size | Freely draggable by the user, at any moment. Size class can change every frame during a drag. `currentWindowAdaptiveInfoV2()` recomposes; anything cached does not. |
| Orientation | Your `screenOrientation` is **overridden regardless of the opt-out property** (§8). |
| Resizability | Respected if you opted out — but "scaled resizing can be applied", i.e. the system may scale rather than relayout, which looks worse than adapting. |
| Insets | A **caption bar** appears at the top of the window. It is a real inset you must respect. |
| Input | Mouse/trackpad becomes the primary pointer. Hover, right-click, and precise-pointer affordances matter. |

## Precise-pointer detection

[REPO `snippets:.../adaptivelayouts/DesktopWindowing.kt`]
```kotlin
// [START android_compose_desktop_engagement_mode]
lifecycleScope.launch(Dispatchers.Main) {
    lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
        windowInfoTracker.windowEngagementInfo(this@DesktopWindowingActivity)
            .collect { windowEngagementInfo ->
                if(windowEngagementInfo.hasEngagementMode(WindowEngagementInfo.EngagementMode.PRECISE_POINTER)){
                    showDesktopOptimizedUI()
                }else {
                    showTouchOptimizedUI()
                }
        }
    }
}
// [END android_compose_desktop_engagement_mode]
```

`androidx.window.layout.WindowEngagementInfo` + `WindowInfoTracker.windowEngagementInfo(activity)`.
**UNVERIFIED** which `androidx.window` version first exposes this; the snippets repo pins
`androidx-window = "1.6.0-alpha05"`.

## Caption bar inset

[REPO `snippets:.../adaptivelayouts/DesktopWindowing.kt`]
```kotlin
/**
 * A custom Title Bar that respects the system caption bar insets.
 */
// [START android_compose_desktop_window_insets_title]
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CaptionBar() {
    if (WindowInsets.isCaptionBarVisible) {
        Row(
            modifier = Modifier
                .windowInsetsTopHeight(WindowInsets.captionBar)
                .fillMaxWidth()
                .background(if (isSystemInDarkTheme()) Color.White else Color.Black),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Caption Bar Title",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(4.dp)
            )
        }
    }
}
// [END android_compose_desktop_window_insets_title]
```

`WindowInsets.captionBar` + `WindowInsets.isCaptionBarVisible` (`@ExperimentalLayoutApi`). Note that
`OVERRIDE_EXCLUDE_CAPTION_INSETS_FROM_APP_BOUNDS` is one of the 19 dead overrides (§8) — you cannot
opt out of the caption inset on API 36.

## Input support [DOC]

Adaptive apps must support:
- **Touch** (default)
- **Keyboard & Tab navigation** (Compose 1.7+)
- **Mouse/Trackpad** — click, select, scroll
- **Stylus** — `TextField` support in Material 3
- **Keyboard shortcuts** — discoverable via Keyboard Shortcuts Helper; override
  `onProvideKeyboardShortcuts()` to publish app shortcuts

---

# 11. Multi-window and split-screen

## What breaks

| Symptom | Cause |
|---|---|
| Layout uses the wrong breakpoint | You read `Configuration.screenWidthDp` (the **screen**) rather than the **window**. In split-screen your window is a fraction of the screen. |
| State lost when the user drags the divider | A resize is a configuration change. Without `ViewModel`/`rememberSaveable` state, everything resets. |
| Layout thrashes during the drag | Size class changes many times during a continuous drag. Anything doing IO or navigation on size-class change fires repeatedly. |
| Content clipped or letterboxed | Fixed aspect ratio or `resizeableActivity="false"` — dead on API 36 (§8), and the system will letterbox or scale instead. |
| Two-pane layout on a 500dp-wide half-screen | You branched on device type, not window size. |

`currentWindowAdaptiveInfoV2()` handles all of this correctly: it reads
`LocalWindowInfo.current.containerSize`, which is the **window**, and it is a plain `@Composable`
that recomposes on every resize including mid-drag.

[DOC] Adaptive apps change layouts based on: *"App window size (primary consideration); Device
posture (foldables: tabletop, book mode); Screen density and font size; Multi-window mode (users can
resize windows at any time)."*

> [DOC] "Rather than stretching/shrinking UI elements, adaptive apps: Replace layout components;
> Show/hide content strategically; Display multiple panes on large screens, single pane on small
> screens."

## How to test it

1. Launch on a tablet or foldable emulator, enter split-screen from Recents, and **drag the divider
   through every breakpoint** (600 / 840 / 1200 / 1600). Watch for layout thrash and for state loss.
2. Repeat with your app on both sides of the split — the narrow side is the one that breaks.
3. Rotate while split.
4. On a desktop-windowing emulator, drag the window corner continuously from small to full-screen.

The snippets repo has `SupportMultiWindowMode.kt` (57 lines) and `SupportConnectedDisplays.kt` (66
lines) under `snippets/compose/snippets/src/main/java/com/example/compose/snippets/adaptivelayouts/`
if you want the exact dev.android.com code.

---

# 12. Configuration-change rules

Three rules, from [DOC]:

**1. Retain state across configuration changes.**
> "Retain state during configuration changes (window resize, posture change, density/font changes)
> via `Activity.onSaveInstanceState()` or `ViewModel`."

In Compose: `rememberSaveable` for UI state, `ViewModel` for anything larger. A fold, a resize, and a
rotation are all the same class of event.

**2. Support all orientations and sizes.**
> "App should support all device and multi-window mode display orientations and sizes. Remove all
> orientation and fixed aspect ratio restrictions from your app layouts and app manifest file."

**3. Do not fight the system.** [DOC] **Avoid:**
> - Hardcoding orientation assumptions
> - Physical device type checks (`isPhone()`, `isTablet()`)
> - Locking screen rotation
> - Single-pane-only layouts
>
> **Do:**
> - Use window size classes for layout decisions
> - Consider the app window (not device screen)
> - Build with Compose Material 3 Adaptive library
> - Support multi-window mode and dynamic resizing
> - Save state during configuration changes (via `onSaveInstanceState()` or `ViewModel`)
> - Support external input devices (keyboards, mice, trackpads, styluses)

**Specific Compose consequence:** the content-key type `T` in `rememberListDetailPaneScaffoldNavigator<T>()`
must be **Bundle-storable** — the navigator is `rememberSaveable`. Use `@Parcelize`. A fold or a
resize will otherwise crash or silently drop the selection.

---

# 13. Testing folds and resizes

## Emulators [DOC]

| Emulator | Tests |
|---|---|
| **Resizable** | Arbitrary window sizes; the fastest way to sweep all five width buckets |
| **Pixel Fold** | Tabletop and book postures, inner/outer display transitions |
| **Pixel Tablet** | Medium/Expanded width, expanded height |
| **Desktop** | Freeform windows, caption bar, Large/XL widths, precise pointer |
| **Remote Device Streaming** | Real Pixel and Samsung hardware |

The Resizable emulator's preset dropdown (Phone / Foldable / Tablet / Desktop) plus free window
resizing covers most of the surface without device-specific hardware.

To reach a posture on a foldable emulator, use the emulator's **virtual sensors / fold controls** to
move the device between closed, half-opened, and flat. `Posture.isTabletop` requires `HALF_OPENED` —
a fully flat emulator will never report it.

> **The source corpus contains no `adb` commands for setting postures.** UNVERIFIED whether a
> supported `adb` path exists; use the emulator's fold control and virtual-sensor panel.

## Unit and screenshot testing [DOC]

| Tool | Use |
|---|---|
| `@PreviewScreenSizes` | multi-size preview matrix |
| `@PreviewFontScale`, `@PreviewLightDark` | the other two axes |
| `DeviceConfigurationOverride` | test multiple configurations in one instrumented test |
| Host-side screenshot tests | verify appearance across display sizes |

## Fake a posture directly — the fastest test

`Posture` and `HingeInfo` both have public constructors and no opt-in, so no emulator is needed:

```kotlin
import androidx.compose.material3.adaptive.HingeInfo
import androidx.compose.material3.adaptive.Posture
import androidx.compose.material3.adaptive.WindowAdaptiveInfo
import androidx.compose.ui.geometry.Rect
import androidx.window.core.layout.WindowSizeClass

// flat, no hinge
val flat = Posture()

// tabletop: horizontal, half-opened, separating
val tabletop = Posture(
    isTabletop = true,
    hingeList = listOf(
        HingeInfo(
            bounds = Rect(left = 0f, top = 700f, right = 1080f, bottom = 740f),
            isFlat = false,
            isVertical = false,
            isSeparating = true,
            isOccluding = false,
        )
    ),
)

// book: vertical, half-opened, separating
val book = Posture(
    isTabletop = false,
    hingeList = listOf(
        HingeInfo(
            bounds = Rect(left = 1050f, top = 0f, right = 1090f, bottom = 1800f),
            isFlat = false,
            isVertical = true,
            isSeparating = true,
            isOccluding = true,
        )
    ),
)

val fakeInfo = WindowAdaptiveInfo(
    windowSizeClass = WindowSizeClass(minWidthDp = 840, minHeightDp = 900),
    windowPosture = tabletop,
)
```

This only reaches your code if your composables **take `WindowAdaptiveInfo` as a parameter**. Hoist
it — that is the mechanism behind nowinandroid's `NiaAppScreenSizesScreenshotTests.kt`:

```kotlin
@Composable
fun MyScreen(
    modifier: Modifier = Modifier,
    windowAdaptiveInfo: WindowAdaptiveInfo = currentWindowAdaptiveInfoV2(),
) { /* ... */ }
```

`calculatePosture(foldingFeatures: List<FoldingFeature>)` is `@VisibleForTesting internal` — you
cannot call it from app code. Construct `Posture` directly instead.

## The cases to cover

| Case | `isTabletop` | hinge `isVertical` | `isSeparating` | Expect |
|---|---|---|---|---|
| Flat phone | false | — (no hinge) | — | single pane, no gaps |
| Tabletop half-opened | **true** | false | true | `maxVerticalPartitions = 2`; content above fold, controls below; bottom nav |
| Book half-opened | false | **true** | true | panes split around `excludedBounds`; two-column reading |
| Flat unfolded, seamless | false | true | **false** | no split — `AvoidSeparating` yields empty `excludedBounds` |
| Dual-screen, flat | false | true | true (+ occluding) | split despite `FLAT` and `isTabletop == false` |
| First frame | false | — | — | `collectFoldingFeaturesAsState()` emitted `emptyList()`; must not be sticky |

---

# 14. Design guidance for foldables

## Hinge avoidance [DOC]

> - **Avoid placing UI controls too close to folds/hinges** when `isSeparating == true` (difficult to
>   reach).
> - **Use `occlusionType`** to determine if content should be placed within fold bounds.
>   - `FULL`: Don't place content in fold area (fully occluded)
>   - `NONE`: Content may be visible across fold
> - **Dual-screen devices**: Always use layouts for tabletop/book even when `FLAT` if
>   `isSeparating == true`.
> - **Hinge angle**: `FoldingFeature` does NOT expose angle; sensor accuracy varies by device.

Never place these across a separating hinge: a primary CTA, a text input, a media scrubber, a small
tappable icon, a single line of body text, a face in a video call.

Fine to span a **non-separating** fold: a full-bleed background image, a decorative gradient, a large
hero photo where the crease reads as a natural break.

## Tabletop: what goes above and below

| Above the fold | Below the fold |
|---|---|
| Video, viewfinder, map, call participants, the artefact being discussed | Playback controls, IME, chat/comments, seek bar, notes, capture button, navigation |

The rule of thumb: **the upper half is a screen, the lower half is a keyboard.** Anything the user
touches repeatedly belongs below. This is also why the nav suite switches to
`ShortNavigationBarMedium` (bottom) on `isTabletop` rather than a side rail.

## Book: reading layouts

- Treat left and right as **peer pages**, not list-and-detail.
- Terminate each text column before `bounds.left` and resume after `bounds.right`.
- Page-turn affordances at the outer edges, not near the crease.
- Do not centre a single column over the hinge. If you have only one column's worth of content,
  centre it in **one** half or widen the margins, but keep the crease clear.

## Reachability [DOC + SRC]

- The library ships bottom navigation for **compact height** windows too, not just compact width —
  that is a reachability decision: a short-and-wide window (phone landscape, flip phone unfolded
  landscape) is easier to reach at the bottom than at the side.
- `NavigationSuiteScaffold(navigationItemVerticalArrangement = Arrangement.Bottom)` bottom-aligns
  rail/drawer items into the thumb zone on tall windows. KDoc: *"It's recommended to use
  `[Arrangement.Top]`, `[Arrangement.Center]`, or `[Arrangement.Bottom]`."*
- Reply encodes this explicitly [REPO `compose-samples/Reply/.../ReplyNavigationComponents.kt`]:
  ```kotlin
  val navContentPosition = if (adaptiveInfo.windowSizeClass.isHeightAtLeastBreakpoint(WindowSizeClass.HEIGHT_DP_MEDIUM_LOWER_BOUND)) {
      ReplyNavigationContentPosition.CENTER
  } else {
      ReplyNavigationContentPosition.TOP
  }
  ```
- `LocalMinimumInteractiveComponentSize.current` is the recommended `minTouchTargetSize` for the
  pane-expansion drag handle — it guarantees a ≥48dp target even though the visual handle is thin.
- **UNVERIFIED:** no explicit m3.material.io "thumb zone" numbers were retrieved in this pass.

---

# 15. Anti-patterns

## 15.1 Locking orientation

```xml
<!-- ❌ -->
<activity android:screenOrientation="portrait" ... />
```

[DOC] lists "Locking screen rotation" under **Avoid**. On API 36 it is **ignored** on any display
≥ sw600dp (§8), and it is ignored under desktop windowing **even with the opt-out property set**. All
this achieves is guaranteeing your landscape layout is untested when the system forces it anyway.

## 15.2 `android:resizeableActivity="false"`

```xml
<!-- ❌ -->
<activity android:resizeableActivity="false" ... />
```

Dead on large screens at API 36; the opt-out property that preserves it disappears at API 37. The
required migration is literally the opposite [DOC]: *"Set `resizeableActivity="true"` to explicitly
support resizing."*

## 15.3 Assuming a fixed aspect ratio

```xml
<!-- ❌ -->
android:minAspectRatio="1.33"
android:maxAspectRatio="1.86"
```

Ignored at API 36 on large screens, **and** users can override aspect ratio in Settings even if you
opt out. Any layout that only works at one aspect ratio — a full-bleed background sized to 9:16, a
hero image with a hard-coded `height`, a viewfinder assuming portrait — will break. Use
`AspectRatio`-aware modifiers and let content reflow.

## 15.4 Hardcoding a hinge width

```kotlin
// ❌ every one of these is wrong on some device
val HINGE_WIDTH = 32.dp
Spacer(Modifier.width(24.dp))               // "the fold is about this wide"
if (posture.isTabletop) Spacer(Modifier.height(40.dp))
```

Hinge geometry varies by device and by state; there is no constant. Read
`hingeInfo.bounds` and use `bounds.width` / `bounds.height`, or — better — let
`PaneScaffoldDirective.excludedBounds` do it and never touch the number:

```kotlin
// ✅ the library measures the actual hinge
val directive = calculatePaneScaffoldDirective(currentWindowAdaptiveInfoV2())
// excludedBounds == the real hinge rect, in window coordinates
```

If you must position content manually, read the rect:
```kotlin
val hinge = posture.separatingHorizontalHingeBounds.firstOrNull()
// content above: constrain to hinge?.top
// content below: start at hinge?.bottom
```

## 15.5 Treating `isTabletop` as "is a foldable"

`isTabletop` is `false` on a **flat unfolded** foldable, on a **dual-screen** device (which is
`FLAT`), and on the **first composed frame** of every device. It answers exactly one question: "is
there a horizontal half-opened hinge right now?" For "does this window have a hinge at all", check
`posture.hingeList.isNotEmpty()`. For "can this hardware ever fold", use
`WindowInfoTracker.supportedPostures` (§7).

## 15.6 Deciding on the first frame

```kotlin
// ❌ fires with isTabletop == false on every launch
LaunchedEffect(Unit) {
    if (!posture.isTabletop) navigateToPhoneLayout()
}
```

`collectFoldingFeaturesAsState()` emits `emptyList()` first. Branch declaratively in composition and
let recomposition correct the layout; do not perform one-shot navigation or persist a choice from the
first emission.

## 15.7 Using horizontal hinge bounds and expecting the library to help

`HingePolicy` and `excludedBounds` are **vertical only**. If you set `HingePolicy.AlwaysAvoid` and
expect the scaffold to route around a tabletop crease, nothing happens. Tabletop is handled by
`maxVerticalPartitions = 2` plus `AdaptStrategy.Reflow`, or by your own use of
`separatingHorizontalHingeBounds`.

---

# 16. Quick reference

```kotlin
import androidx.compose.material3.adaptive.HingeInfo
import androidx.compose.material3.adaptive.Posture
import androidx.compose.material3.adaptive.allHorizontalHingeBounds
import androidx.compose.material3.adaptive.allVerticalHingeBounds
import androidx.compose.material3.adaptive.collectFoldingFeaturesAsState
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfoV2
import androidx.compose.material3.adaptive.occludingHorizontalHingeBounds
import androidx.compose.material3.adaptive.occludingVerticalHingeBounds
import androidx.compose.material3.adaptive.separatingHorizontalHingeBounds
import androidx.compose.material3.adaptive.separatingVerticalHingeBounds
import androidx.compose.material3.adaptive.layout.HingePolicy
import androidx.compose.material3.adaptive.layout.calculatePaneScaffoldDirective

// ---- Read posture -----------------------------------------------------------
val info = currentWindowAdaptiveInfoV2()
val posture = info.windowPosture

posture.isTabletop                       // Boolean
posture.hingeList                        // List<HingeInfo>
posture.separatingVerticalHingeBounds    // List<Rect>  <- what the directive uses
posture.occludingVerticalHingeBounds
posture.allVerticalHingeBounds
posture.separatingHorizontalHingeBounds  // tabletop; NOT used by any directive
posture.occludingHorizontalHingeBounds
posture.allHorizontalHingeBounds

// ---- Book posture: derive it yourself ---------------------------------------
val isBookLike = posture.separatingVerticalHingeBounds.isNotEmpty() && !posture.isTabletop

// ---- Directive with a hinge policy ------------------------------------------
val directive = calculatePaneScaffoldDirective(info)                          // AvoidSeparating
val fullBleed = calculatePaneScaffoldDirective(info, HingePolicy.NeverAvoid)
val paranoid  = calculatePaneScaffoldDirective(info, HingePolicy.AlwaysAvoid)
val visualOnly = calculatePaneScaffoldDirective(info, HingePolicy.AvoidOccluding)
directive.excludedBounds                 // List<Rect>, window coordinates

// ---- Raw folding features ---------------------------------------------------
val features = collectFoldingFeaturesAsState().value   // emptyList() on first frame
```

### Constants and defaults
```
HingePolicy.AlwaysAvoid      = 0   -> allVerticalHingeBounds
HingePolicy.AvoidSeparating  = 1   -> separatingVerticalHingeBounds   [DEFAULT]
HingePolicy.AvoidOccluding   = 2   -> occludingVerticalHingeBounds
HingePolicy.NeverAvoid       = 3   -> emptyList()

isTabletop  <=  any FoldingFeature with orientation == HORIZONTAL && state == HALF_OPENED
isOccluding =>  isSeparating          (but not the reverse)

maxVerticalPartitions = 2  iff  isTabletop || (maxHorizontalPartitions == 1 && minHeight == 900)
verticalPartitionSpacerSize = 24.dp when maxVerticalPartitions == 2, else 0.dp

Reflow engages only when maxHorizontalPartitions == 1 AND maxVerticalPartitions > 1
```

### Things that do NOT exist

| Do not write | Reality |
|---|---|
| `Posture.isBookPosture` | derive it from a vertical half-opened `FoldingFeature`, or use `separatingVerticalHingeBounds` |
| `WindowPosture` | the type is `Posture`; the property is `windowPosture` |
| `calculatePosture()` from app code | `internal expect`/`actual`; get it from `WindowAdaptiveInfo` |
| `FoldingFeature.angle` | not exposed at any version |
| `HingePolicy` affecting horizontal hinges | vertical only |
| `FoldingFeature.State.CLOSED` | only `FLAT` and `HALF_OPENED` |

## Cross-references

- Breakpoints, `WindowSizeClass`, the `>=` trap, deprecations → `window-size-classes.md` (this skill)
- Reflow / Levitate adapt strategies and pane scaffolds → the pane-scaffold reference in this skill
- Nav-suite behaviour under tabletop → m3-expressive-navigation skill

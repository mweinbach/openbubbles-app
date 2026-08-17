# NavigationSuiteScaffold and adaptive-navigation3

The adaptive navigation surface: `NavigationSuiteScaffold` (one item list, correct container per
window size), and `adaptive-navigation3` (`ListDetailSceneStrategy` / `SupportingPaneSceneStrategy`,
which turn a Navigation 3 back stack into panes).

**Scope split.** This file owns the *suite scaffold* and the *nav3 scene strategies*. The individual
nav containers — `ShortNavigationBar`, `WideNavigationRail`, `ModalWideNavigationRail`, the manual
`ShortNavigationBar` ⇄ `WideNavigationRail` switch, toolbar-as-navigation, item/indicator APIs, and
Navigation3 routing itself — live in the `m3-expressive-navigation` skill at
`${CLAUDE_PLUGIN_ROOT}/skills/m3-expressive-navigation/`. Do not duplicate that material; link to it.

Provenance markers:

- `[API-1.3.0]` — verbatim from the frozen metalava signature `api/1.3.0-rc01.txt` (byte-identical
  to shipped 1.3.0).
- `[SRC@HEAD]` — verbatim Kotlin from androidx-main HEAD (commit `360e8cb`, 2026-08-14). HEAD is
  *post*-1.3.0.
- `[REPO <path>]` — verbatim from a cloned repo.
- `[DOC]` — developer.android.com / m3.material.io, fetched 2026-08-14.
- `[UNVERIFIED]` — stated, not confirmed against a primary source.

Target versions: `material3-adaptive` **1.3.0 stable**, `material3` **1.5.0-alpha26**.

---

# 1. Artifact and coordinates

`material3-adaptive-navigation-suite` is in group **`androidx.compose.material3`**, not
`androidx.compose.material3.adaptive`. It rides the material3 train.

```kotlin
// [DOC] developer.android.com/jetpack/androidx/releases/compose-material3 (2026-08-14)
implementation("androidx.compose.material3:material3-adaptive-navigation-suite:1.5.0-alpha26")

// adaptive-navigation3 is on the adaptive train, first shipped in 1.3.0
implementation("androidx.compose.material3.adaptive:adaptive-navigation3:1.3.0")
implementation("androidx.navigation3:navigation3-runtime:1.1.6")
implementation("androidx.navigation3:navigation3-ui:1.1.6")
```

Package: `androidx.compose.material3.adaptive.navigationsuite`.

The nav-suite `api/` directory contains `1.3.0-beta01..03`, `1.4.0-beta01..03`, `current.txt` and
`public_plus_experimental_current.txt`. A 1.4.0 line was cut; the material3 release page's snippet
nonetheless advertises `1.5.0-alpha26`. **Which nav-suite version is "the" stable is `[UNVERIFIED]`**;
what is verified is the group and the snippet text.

**As of 1.4.0/1.5.0-alpha the core nav-suite APIs are NOT experimental.** `NavigationSuiteScaffold`,
`NavigationSuiteType`, `NavigationSuiteScaffoldDefaults` carry no opt-in in `current.txt`.
`ExperimentalMaterial3AdaptiveNavigationSuiteApi` still exists for anything still gated.

---

# 2. `NavigationSuiteType` — eight values, not three

`[SRC@HEAD NavigationSuiteScaffold.kt lines 1063–1155]` verbatim:

```kotlin
/**
 * Class that describes the different navigation suite types of the [NavigationSuiteScaffold].
 *
 * The [NavigationSuiteType] informs the [NavigationSuite] of what navigation component to expect.
 */
@JvmInline
public value class NavigationSuiteType private constructor(private val description: String) {
    public override fun toString(): String {
        return description
    }

    public companion object {
        /**
         * A navigation suite type that instructs the [NavigationSuite] to expect a
         * [ShortNavigationBar] with vertical [ShortNavigationBarItem]s that will be displayed at
         * the bottom of the screen.
         *
         * @see [ShortNavigationBar]
         */
        public val ShortNavigationBarCompact: NavigationSuiteType =
            NavigationSuiteType(description = "ShortNavigationBarCompact")

        /**
         * A navigation suite type that instructs the [NavigationSuite] to expect a
         * [ShortNavigationBar] with horizontal [ShortNavigationBarItem]s that will be displayed at
         * the bottom of the screen.
         *
         * @see [ShortNavigationBar]
         */
        public val ShortNavigationBarMedium: NavigationSuiteType =
            NavigationSuiteType(description = "ShortNavigationBarMedium")

        /**
         * A navigation suite type that instructs the [NavigationSuite] to expect a collapsed
         * [WideNavigationRail] that will be displayed at the start of the screen.
         *
         * @see [WideNavigationRail]
         */
        public val WideNavigationRailCollapsed: NavigationSuiteType =
            NavigationSuiteType(description = "WideNavigationRailCollapsed")

        /**
         * A navigation suite type that instructs the [NavigationSuite] to expect an expanded
         * [WideNavigationRail] that will be displayed at the start of the screen.
         *
         * @see [WideNavigationRail]
         */
        public val WideNavigationRailExpanded: NavigationSuiteType =
            NavigationSuiteType(description = "WideNavigationRailExpanded")

        /**
         * A navigation suite type that instructs the [NavigationSuite] to expect a [NavigationBar]
         * that will be displayed at the bottom of the screen.
         *
         * Note: It's recommended to use [ShortNavigationBarCompact] instead of this layout type.
         *
         * @see [NavigationBar]
         */
        public val NavigationBar: NavigationSuiteType =
            NavigationSuiteType(description = "NavigationBar")

        /**
         * A navigation suite type that instructs the [NavigationSuite] to expect a [NavigationRail]
         * that will be displayed at the start of the screen.
         *
         * Note: It's recommended to use [WideNavigationRailCollapsed] instead of this layout type.
         *
         * @see [NavigationRail]
         */
        public val NavigationRail: NavigationSuiteType =
            NavigationSuiteType(description = "NavigationRail")

        /**
         * A navigation suite type that instructs the [NavigationSuite] to expect a
         * [PermanentDrawerSheet] that will be displayed at the start of the screen.
         *
         * Note: It's recommended to use [WideNavigationRailExpanded] instead of this layout type.
         *
         * @see [PermanentDrawerSheet]
         */
        public val NavigationDrawer: NavigationSuiteType =
            NavigationSuiteType(description = "NavigationDrawer")

        /**
         * A navigation suite type that instructs the [NavigationSuite] to not display any
         * navigation components on the screen.
         *
         * Note: It's recommended to use [NavigationSuiteScaffoldState] instead of this layout type
         * and set the visibility of the navigation component to hidden.
         */
        public val None: NavigationSuiteType = NavigationSuiteType(description = "None")
    }
}
```

**It is a `@JvmInline value class` over `String`, not an enum.** No `values()`, no exhaustive `when`,
no `when` *expression* without an `else`.

| Value | Renders | Position | Status |
| --- | --- | --- | --- |
| `ShortNavigationBarCompact` | `ShortNavigationBar` with **vertical** `ShortNavigationBarItem`s | bottom | **Expressive-preferred** |
| `ShortNavigationBarMedium` | `ShortNavigationBar` with **horizontal** `ShortNavigationBarItem`s | bottom | **Expressive-preferred** |
| `WideNavigationRailCollapsed` | collapsed `WideNavigationRail` | start | **Expressive-preferred** |
| `WideNavigationRailExpanded` | expanded `WideNavigationRail` | start | **Expressive-preferred** |
| `NavigationBar` | `NavigationBar` | bottom | legacy — KDoc: use `ShortNavigationBarCompact` instead |
| `NavigationRail` | `NavigationRail` | start | legacy — KDoc: use `WideNavigationRailCollapsed` instead |
| `NavigationDrawer` | `PermanentDrawerSheet` | start | legacy — KDoc: use `WideNavigationRailExpanded` instead |
| `None` | nothing | — | discouraged — KDoc: use `NavigationSuiteScaffoldState` and hide instead |

The four preferred types map onto the Expressive nav components (`ShortNavigationBar`,
`ShortNavigationBarItem`, `WideNavigationRail`) that live in `androidx.compose.material3`. The four
legacy types map onto classic M3 components. This is the Adaptive × Expressive intersection.

---

# 3. `NavigationSuiteScaffoldDefaults` — the type-selection logic and its quirk

`[SRC@HEAD lines 1157–1218]` verbatim:

```kotlin
/** Contains the default values used by the [NavigationSuiteScaffold]. */
public object NavigationSuiteScaffoldDefaults {
    /**
     * Returns the recommended [NavigationSuiteType] according to the provided [WindowAdaptiveInfo],
     * following the Material specifications. Usually used with the [NavigationSuiteScaffold] and
     * related APIs.
     *
     * @param adaptiveInfo the provided [WindowAdaptiveInfo]
     * @see NavigationSuiteScaffold
     */
    public fun navigationSuiteType(adaptiveInfo: WindowAdaptiveInfo): NavigationSuiteType {
        return with(adaptiveInfo) {
            if (windowSizeClass.minWidth == WindowSizeClass.WidthSizeClasses.Compact) {
                NavigationSuiteType.ShortNavigationBarCompact
            } else if (
                windowPosture.isTabletop ||
                    windowSizeClass.minHeight == WindowSizeClass.HeightSizeClasses.Compact
            ) {
                NavigationSuiteType.ShortNavigationBarMedium
            } else {
                NavigationSuiteType.WideNavigationRailCollapsed
            }
        }
    }

    /**
     * Returns the standard [NavigationSuiteType] according to the provided [WindowAdaptiveInfo].
     * Usually used with the [NavigationSuiteScaffold] and related APIs.
     *
     * Note: It's recommended to use [navigationSuiteType] instead of this function, as that one
     * offers extended and preferred types.
     *
     * @param adaptiveInfo the provided [WindowAdaptiveInfo]
     * @see NavigationSuiteScaffold
     * @see navigationSuiteType
     */
    @Suppress("DEPRECATION") // WindowWidthSizeClass deprecated
    public fun calculateFromAdaptiveInfo(adaptiveInfo: WindowAdaptiveInfo): NavigationSuiteType {
        return with(adaptiveInfo) {
            if (
                windowPosture.isTabletop ||
                    windowSizeClass.minHeight == WindowSizeClass.HeightSizeClasses.Compact ||
                    windowSizeClass.minWidth == WindowSizeClass.WidthSizeClasses.Compact
            ) {
                NavigationSuiteType.NavigationBar
            } else {
                NavigationSuiteType.NavigationRail
            }
        }
    }

    /** Default container color for a navigation suite scaffold. */
    public val containerColor: Color
        @Composable get() = MaterialTheme.colorScheme.background

    /** Default content color for a navigation suite scaffold. */
    public val contentColor: Color
        @Composable get() = MaterialTheme.colorScheme.onBackground

    /** Default primary action content alignment for a navigation suite scaffold. */
    public val primaryActionContentAlignment: Alignment.Horizontal = Alignment.End
}
```

`navigationSuiteType` (modern — use this), checked in order:

| Condition | Result |
| --- | --- |
| width == Compact (`minWidth == 0dp`) | `ShortNavigationBarCompact` |
| `isTabletop` **OR** height == Compact (`minHeight == 0dp`) | `ShortNavigationBarMedium` |
| otherwise | `WideNavigationRailCollapsed` |

`calculateFromAdaptiveInfo` (legacy):

| Condition | Result |
| --- | --- |
| `isTabletop` **OR** height Compact **OR** width Compact | `NavigationBar` |
| otherwise | `NavigationRail` |

Matching `[DOC]`: *"The default behavior shows: Navigation bar if the width or height is compact or
if the device is in tabletop posture; Navigation rail for everything else."*

## The quirk you must know

**`navigationSuiteType()` never returns `WideNavigationRailExpanded`, `NavigationDrawer` or `None`.**
Its `else` branch terminates at `WideNavigationRailCollapsed`. It has no Large/XL branch at all —
a 1600dp desktop window gets the same collapsed rail as an 850dp tablet.

Likewise `calculateFromAdaptiveInfo()` only ever returns `NavigationBar` or `NavigationRail`.

If you want an expanded rail or a permanent drawer on wide windows, **you must compute it yourself**
(§5). Three of the eight values are unreachable from the defaults; a fourth (`None`) is explicitly
discouraged in favour of `NavigationSuiteScaffoldState.hide()`.

Defaults: `containerColor = colorScheme.background`, `contentColor = colorScheme.onBackground`,
`primaryActionContentAlignment = Alignment.End`.

---

# 4. `NavigationSuiteScaffold` — both overloads, every parameter

## 4.1 Modern overload (`navigationItems` + `NavigationSuiteItem`)

`[SRC@HEAD lines 150–232]` verbatim, KDoc included — this documents every parameter:

```kotlin
/**
 * The Navigation Suite Scaffold wraps the provided content and places the adequate provided
 * navigation component on the screen according to the current [NavigationSuiteType].
 *
 * The navigation component can be animated to be hidden or shown via a
 * [NavigationSuiteScaffoldState].
 *
 * The scaffold also supports an optional primary action composable, such as a floating action
 * button, which will be displayed according to the current [NavigationSuiteType].
 *
 * A simple usage example looks like this:
 *
 * @sample androidx.compose.material3.adaptive.navigationsuite.samples.NavigationSuiteScaffoldSample
 *
 * An usage with custom layout choices looks like this:
 *
 * @sample androidx.compose.material3.adaptive.navigationsuite.samples.NavigationSuiteScaffoldCustomConfigSample
 * @param navigationItems the navigation items to be displayed, typically [NavigationSuiteItem]s
 * @param modifier the [Modifier] to be applied to the navigation suite scaffold
 * @param navigationSuiteType the current [NavigationSuiteType]. Defaults to
 *   [NavigationSuiteScaffoldDefaults.navigationSuiteType]
 * @param navigationSuiteColors [NavigationSuiteColors] that will be used to determine the container
 *   (background) color of the navigation component and the preferred color for content inside the
 *   navigation component
 * @param containerColor the color used for the background of the navigation suite scaffold,
 *   including the passed [content] composable. Use [Color.Transparent] to have no color
 * @param contentColor the preferred color to be used for typography and iconography within the
 *   passed in [content] lambda inside the navigation suite scaffold.
 * @param state the [NavigationSuiteScaffoldState] of this navigation suite scaffold
 * @param navigationItemVerticalArrangement the vertical arrangement of the items inside vertical
 *   navigation components (such as the types [NavigationSuiteType.WideNavigationRailCollapsed] and
 *   [NavigationSuiteType.WideNavigationRailExpanded]). It's recommended to use [Arrangement.Top],
 *   [Arrangement.Center], or [Arrangement.Bottom]. Defaults to [Arrangement.Top]
 * @param primaryActionContent The optional primary action content of the navigation suite scaffold,
 *   if any. Typically a [androidx.compose.material3.FloatingActionButton]. It'll be displayed
 *   inside vertical navigation components as part of their header , and above horizontal navigation
 *   components.
 * @param primaryActionContentHorizontalAlignment The horizontal alignment of the primary action
 *   content, if present, when it's displayed along with a horizontal navigation component.
 * @param content the content of your screen
 */
@Composable
public fun NavigationSuiteScaffold(
    navigationItems: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    navigationSuiteType: NavigationSuiteType =
        NavigationSuiteScaffoldDefaults.navigationSuiteType(WindowAdaptiveInfoDefault),
    navigationSuiteColors: NavigationSuiteColors = NavigationSuiteDefaults.colors(),
    containerColor: Color = NavigationSuiteScaffoldDefaults.containerColor,
    contentColor: Color = NavigationSuiteScaffoldDefaults.contentColor,
    state: NavigationSuiteScaffoldState = rememberNavigationSuiteScaffoldState(),
    navigationItemVerticalArrangement: Arrangement.Vertical =
        NavigationSuiteDefaults.verticalArrangement,
    primaryActionContent: @Composable (() -> Unit) = {},
    primaryActionContentHorizontalAlignment: Alignment.Horizontal =
        NavigationSuiteScaffoldDefaults.primaryActionContentAlignment,
    content: @Composable () -> Unit,
) {
    Surface(modifier = modifier, color = containerColor, contentColor = contentColor) {
        NavigationSuiteScaffoldLayout(
            navigationSuite = {
                NavigationSuite(
                    navigationSuiteType = navigationSuiteType,
                    colors = navigationSuiteColors,
                    primaryActionContent = primaryActionContent,
                    verticalArrangement = navigationItemVerticalArrangement,
                    content = navigationItems,
                )
            },
            navigationSuiteType = navigationSuiteType,
            state = state,
            primaryActionContent = primaryActionContent,
            primaryActionContentHorizontalAlignment = primaryActionContentHorizontalAlignment,
            content = {
                Box(
                    Modifier.navigationSuiteScaffoldConsumeWindowInsets(navigationSuiteType, state)
                ) {
                    content()
                }
            },
        )
    }
}
```

`WindowAdaptiveInfoDefault` is an internal `@Composable` shorthand for the current adaptive info.
`[UNVERIFIED]` exactly which of `currentWindowAdaptiveInfo`/`V2` it wraps; either way it yields a
`window-core` `WindowSizeClass`. **If you care about Large/XL behaviour, pass
`navigationSuiteType = NavigationSuiteScaffoldDefaults.navigationSuiteType(currentWindowAdaptiveInfoV2())`
explicitly rather than relying on the default.**

## 4.2 Legacy overload (`navigationSuiteItems` + `NavigationSuiteScope.item`)

`[SRC@HEAD lines 234–270]`:

```kotlin
/**
 * ...
 * Note: It is recommended to use the [NavigationSuiteScaffold] function with the navigationItems
 * param that accepts [NavigationSuiteItem]s instead of this one.
 * ...
 * @param layoutType the current [NavigationSuiteType]. Defaults to
 *   [NavigationSuiteScaffoldDefaults.calculateFromAdaptiveInfo]
 * ...
 */
@Composable
public fun NavigationSuiteScaffold(
    navigationSuiteItems: NavigationSuiteScope.() -> Unit,
    modifier: Modifier = Modifier,
    layoutType: NavigationSuiteType =
        NavigationSuiteScaffoldDefaults.calculateFromAdaptiveInfo(WindowAdaptiveInfoDefault),
    navigationSuiteColors: NavigationSuiteColors = NavigationSuiteDefaults.colors(),
    containerColor: Color = NavigationSuiteScaffoldDefaults.containerColor,
    contentColor: Color = NavigationSuiteScaffoldDefaults.contentColor,
    state: NavigationSuiteScaffoldState = rememberNavigationSuiteScaffoldState(),
    content: @Composable () -> Unit = {},
)
```

Differences that bite:

- `navigationSuiteItems: NavigationSuiteScope.() -> Unit` is a **non-composable** builder lambda;
  `navigationItems: @Composable () -> Unit` is composable.
- The parameter is named **`layoutType`**, not `navigationSuiteType`.
- Default type comes from **`calculateFromAdaptiveInfo`** → legacy `NavigationBar`/`NavigationRail`
  containers. Switching overloads silently changes which components render.
- No `navigationItemVerticalArrangement`, no `primaryActionContent`, no alignment.
- `content` has a default `= {}`.

**Choose the `navigationItems` overload** for Expressive containers, FAB integration and vertical
arrangement. The `navigationSuiteItems` overload is what most existing apps (nowinandroid, the
developer.android.com snippets) still use.

---

# 5. Overriding the computed type

You must override to reach `WideNavigationRailExpanded` or `NavigationDrawer`.

## 5.1 Modern idiom — androidx `@Sampled`, verbatim

`[REPO androidx-m3:compose/material3/material3-adaptive-navigation-suite/samples/src/main/java/androidx/compose/material3/adaptive/navigationsuite/samples/NavigationSuiteScaffoldSamples.kt lines 102–169]`:

```kotlin
@Preview
@Sampled
@Composable
@Suppress("DEPRECATION") // WindowWidthSizeClass is deprecated
fun NavigationSuiteScaffoldCustomConfigSample() {
    var selectedItem by remember { mutableIntStateOf(0) }
    val navItems = listOf("Songs", "Artists", "Playlists")
    // Custom configuration that shows a wide navigation rail in small/medium width screens, an
    // expanded wide navigation rail in expanded width screens, and a short navigation bar in small
    // height screens.
    val navSuiteType =
        with(currentWindowAdaptiveInfoV2()) {
            if (
                windowSizeClass.windowWidthSizeClass == WindowWidthSizeClass.COMPACT ||
                    windowSizeClass.windowWidthSizeClass == WindowWidthSizeClass.MEDIUM
            ) {
                NavigationSuiteType.WideNavigationRailCollapsed
            } else if (windowSizeClass.windowHeightSizeClass == WindowHeightSizeClass.COMPACT) {
                NavigationSuiteType.ShortNavigationBarMedium
            } else if (windowSizeClass.windowWidthSizeClass == WindowWidthSizeClass.EXPANDED) {
                NavigationSuiteType.WideNavigationRailExpanded
            } else {
                NavigationSuiteScaffoldDefaults.navigationSuiteType(currentWindowAdaptiveInfoV2())
            }
        }
    val state = rememberNavigationSuiteScaffoldState()
    val scope = rememberCoroutineScope()

    NavigationSuiteScaffold(
        navigationSuiteType = navSuiteType,
        state = state,
        navigationItemVerticalArrangement = Arrangement.Center,
        navigationItems = {
            navItems.forEachIndexed { index, navItem ->
                NavigationSuiteItem(
                    navigationSuiteType = navSuiteType,
                    icon = {
                        Icon(
                            if (selectedItem == index) Icons.Filled.Favorite
                            else Icons.Outlined.FavoriteBorder,
                            contentDescription = null,
                        )
                    },
                    label = { Text(navItem) },
                    selected = selectedItem == index,
                    onClick = { selectedItem = index },
                )
            }
        },
    ) {
        // Screen content.
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                modifier = Modifier.padding(16.dp),
                text =
                    "Current NavigationSuiteType: $navSuiteType\n" +
                        "Visibility: ${state.currentValue}",
                textAlign = TextAlign.Center,
            )
            Button(onClick = { scope.launch { state.toggle() } }) {
                Text("Hide/show navigation component")
            }
        }
    }
}
```

Two things to copy and one thing not to:

- **Copy:** `navigationSuiteType` is passed to **both** the scaffold **and** every
  `NavigationSuiteItem`. Miss the item and it renders with the default type's item shape.
- **Copy:** the `else` branch delegates back to `NavigationSuiteScaffoldDefaults.navigationSuiteType`
  rather than hardcoding a fallback.
- **Do not copy:** the file header carries `@file:Suppress("DEPRECATION") // Suppress for
  WindowWidthSizeClass` — androidx's own sample uses the deprecated `windowWidthSizeClass` enum
  accessor. Write `isWidthAtLeastBreakpoint` instead.

## 5.2 The same override, written with current predicates

Authored, not cited. `isWidthAtLeastBreakpoint` is `>=`, so the `when` runs **largest → smallest**:

```kotlin
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfoV2
import androidx.window.core.layout.WindowSizeClass

val info = currentWindowAdaptiveInfoV2()
val navSuiteType = with(info) {
    when {
        // Large / XL desktop-class windows: give the rail its labels.
        windowSizeClass.isWidthAtLeastBreakpoint(WindowSizeClass.WIDTH_DP_LARGE_LOWER_BOUND) ->
            NavigationSuiteType.WideNavigationRailExpanded

        // Short windows (phone landscape, unfolded flip in landscape) and tabletop: bottom bar.
        windowPosture.isTabletop ||
            !windowSizeClass.isHeightAtLeastBreakpoint(WindowSizeClass.HEIGHT_DP_MEDIUM_LOWER_BOUND) ->
            NavigationSuiteType.ShortNavigationBarMedium

        // Everything else follows Material defaults.
        else -> NavigationSuiteScaffoldDefaults.navigationSuiteType(info)
    }
}
```

Constants: width `WIDTH_DP_MEDIUM_LOWER_BOUND` 600, `WIDTH_DP_EXPANDED_LOWER_BOUND` 840,
`WIDTH_DP_LARGE_LOWER_BOUND` 1200, `WIDTH_DP_EXTRA_LARGE_LOWER_BOUND` 1600; height
`HEIGHT_DP_MEDIUM_LOWER_BOUND` 480, `HEIGHT_DP_EXPANDED_LOWER_BOUND` 900. Height has no Large/XL.

## 5.3 Legacy idiom

`[REPO snippets:compose/snippets/src/main/java/com/example/compose/snippets/adaptivelayouts/SampleNavigationSuiteScaffold.kt lines 187–206]`:

```kotlin
@Composable
fun SampleNavigationSuiteScaffoldCustomType() {
    // [START android_compose_adaptivelayouts_sample_navigation_suite_scaffold_layout_type]
    val adaptiveInfo = currentWindowAdaptiveInfo()
    val customNavSuiteType = with(adaptiveInfo) {
        if (windowSizeClass.isWidthAtLeastBreakpoint(WIDTH_DP_EXPANDED_LOWER_BOUND)) {
            NavigationSuiteType.NavigationDrawer
        } else {
            NavigationSuiteScaffoldDefaults.calculateFromAdaptiveInfo(adaptiveInfo)
        }
    }

    NavigationSuiteScaffold(
        navigationSuiteItems = { /* ... */ },
        layoutType = customNavSuiteType,
    ) {
        // Content...
    }
    // [END android_compose_adaptivelayouts_sample_navigation_suite_scaffold_layout_type]
}
```

> Cited verbatim. **It calls the deprecated `currentWindowAdaptiveInfo()`** — replace with
> `currentWindowAdaptiveInfoV2()` when you adapt it, or you silently clamp everything ≥840dp to
> Expanded and never see Large/XL.

---

# 6. `NavigationSuiteScaffoldState` — the right way to hide nav

`[SRC@HEAD lines 94–148]` verbatim:

```kotlin
/** Possible values of [NavigationSuiteScaffoldState]. */
public enum class NavigationSuiteScaffoldValue {
    /** The state of the navigation component of the scaffold when it's visible. */
    Visible,

    /** The state of the navigation component of the scaffold when it's hidden. */
    Hidden,
}

/**
 * A state object that can be hoisted to observe the navigation suite scaffold state. It allows for
 * setting its navigation component to be hidden or displayed.
 *
 * @see rememberNavigationSuiteScaffoldState to construct the default implementation.
 */
@Stable
public interface NavigationSuiteScaffoldState {
    /** Whether the state is currently animating. */
    public val isAnimating: Boolean

    /** Whether the navigation component is going to be shown or hidden. */
    public val targetValue: NavigationSuiteScaffoldValue

    /** Whether the navigation component is currently shown or hidden. */
    public val currentValue: NavigationSuiteScaffoldValue

    /** Hide the navigation component with animation and suspend until it fully expands. */
    public suspend fun hide()

    /** Show the navigation component with animation and suspend until it fully expands. */
    public suspend fun show()

    /**
     * Hide the navigation component with animation if it's shown, or collapse it otherwise, and
     * suspend until it fully expands.
     */
    public suspend fun toggle()

    /**
     * Set the state without any animation and suspend until it's set.
     *
     * @param targetValue the value to set to
     */
    public suspend fun snapTo(targetValue: NavigationSuiteScaffoldValue)
}

/** Create and [remember] a [NavigationSuiteScaffoldState] */
@Composable
public fun rememberNavigationSuiteScaffoldState(
    initialValue: NavigationSuiteScaffoldValue = NavigationSuiteScaffoldValue.Visible
): NavigationSuiteScaffoldState {
    return rememberSaveable(saver = NavigationSuiteScaffoldStateImpl.Saver()) {
        NavigationSuiteScaffoldStateImpl(initialValue = initialValue)
    }
}
```

`NavigationSuiteScaffoldValue` **is** a real enum (unlike `NavigationSuiteType`). All mutators are
`suspend` — call from `rememberCoroutineScope().launch { }`.

Hide chrome for an immersive route with `state.hide()`, **not** `NavigationSuiteType.None`. The
KDoc on `None` says so explicitly, and `None` re-lays-out the scaffold instead of animating.

---

# 7. Items, scope and colors

## 7.1 `NavigationSuiteScope` (legacy item DSL)

`[SRC@HEAD]`:

```kotlin
public sealed interface NavigationSuiteScope {
    public fun item(
        selected: Boolean,
        onClick: () -> Unit,
        icon: @Composable () -> Unit,
        modifier: Modifier = Modifier,
        enabled: Boolean = true,
        label: @Composable (() -> Unit)? = null,
        alwaysShowLabel: Boolean = true,
        badge: (@Composable () -> Unit)? = null,
        colors: NavigationSuiteItemColors? = null,
        interactionSource: MutableInteractionSource? = null,
    )
}
```

## 7.2 `NavigationSuiteItem` (modern, composable)

`[API]`:

```
method @KotlinOnly @androidx.compose.runtime.Composable public static void NavigationSuiteItem(boolean selected, kotlin.jvm.functions.Function0<kotlin.Unit> onClick, kotlin.jvm.functions.Function0<kotlin.Unit> icon, kotlin.jvm.functions.Function0<kotlin.Unit>? label, optional androidx.compose.ui.Modifier modifier, optional androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteType navigationSuiteType, optional boolean enabled, optional kotlin.jvm.functions.Function0<kotlin.Unit>? badge, optional androidx.compose.material3.NavigationItemColors? colors, optional androidx.compose.foundation.interaction.MutableInteractionSource? interactionSource);
```

Differences vs `item`:

- `label` is a **required** (nullable) positional parameter, not optional. Pass `label = null`
  explicitly for an unlabelled item.
- Takes an explicit `navigationSuiteType`.
- `colors` is `androidx.compose.material3.NavigationItemColors` (single unified type), **not**
  `NavigationSuiteItemColors`.
- There is **no `alwaysShowLabel`**.

## 7.3 `NavigationSuiteItemColors` (legacy, three-way)

```
public final class NavigationSuiteItemColors {
    ctor public NavigationSuiteItemColors(androidx.compose.material3.NavigationBarItemColors navigationBarItemColors, androidx.compose.material3.NavigationRailItemColors navigationRailItemColors, androidx.compose.material3.NavigationDrawerItemColors navigationDrawerItemColors);
    property public androidx.compose.material3.NavigationBarItemColors navigationBarItemColors;
    property public androidx.compose.material3.NavigationDrawerItemColors navigationDrawerItemColors;
    property public androidx.compose.material3.NavigationRailItemColors navigationRailItemColors;
}
```

Built with `NavigationSuiteDefaults.itemColors(navigationBarItemColors, navigationRailItemColors,
navigationDrawerItemColors)` — all three optional. It covers only the **legacy** trio; it has no
slot for `ShortNavigationBar` or `WideNavigationRail`. That is a reason to move to the modern
overload if you theme items.

## 7.4 `NavigationSuiteColors` — container/content per component

```
public final class NavigationSuiteColors {
    property public androidx.compose.ui.graphics.Color navigationBarContainerColor;
    property public androidx.compose.ui.graphics.Color navigationBarContentColor;
    property public androidx.compose.ui.graphics.Color navigationDrawerContainerColor;
    property public androidx.compose.ui.graphics.Color navigationDrawerContentColor;
    property public androidx.compose.ui.graphics.Color navigationRailContainerColor;
    property public androidx.compose.ui.graphics.Color navigationRailContentColor;
    property public androidx.compose.ui.graphics.Color shortNavigationBarContainerColor;
    property public androidx.compose.ui.graphics.Color shortNavigationBarContentColor;
    property public androidx.compose.material3.WideNavigationRailColors wideNavigationRailColors;
}
```

The Expressive components get `shortNavigationBar*Color` plus a whole `WideNavigationRailColors`
object, alongside the legacy trio.

## 7.5 `NavigationSuiteDefaults`

`[SRC@HEAD lines 1220–1268]`:

```kotlin
/** Contains the default values used by the [NavigationSuite]. */
public object NavigationSuiteDefaults {
    /** Default items vertical arrangement for a navigation suite. */
    public val verticalArrangement: Arrangement.Vertical = Arrangement.Top

    /**
     * Creates a [NavigationSuiteColors] with the provided colors for the container color, according
     * to the Material specification.
     *
     * Use [Color.Transparent] for the navigation*ContainerColor to have no color. The
     * navigation*ContentColor will default to either the matching content color for
     * navigation*ContainerColor, or to the current [LocalContentColor] if navigation*ContainerColor
     * is not a color from the theme.
     * ...
     */
    @Composable
    public fun colors(
        shortNavigationBarContentColor: Color = ShortNavigationBarDefaults.contentColor,
        shortNavigationBarContainerColor: Color = ShortNavigationBarDefaults.containerColor,
        wideNavigationRailColors: WideNavigationRailColors = WideNavigationRailDefaults.colors(),
        navigationBarContainerColor: Color = NavigationBarDefaults.containerColor,
        navigationBarContentColor: Color = contentColorFor(navigationBarContainerColor),
        navigationRailContainerColor: Color = NavigationRailDefaults.ContainerColor,
        navigationRailContentColor: Color = contentColorFor(navigationRailContainerColor),
        navigationDrawerContainerColor: Color =
            @Suppress("DEPRECATION") DrawerDefaults.containerColor,
        navigationDrawerContentColor: Color = contentColorFor(navigationDrawerContainerColor),
    ): NavigationSuiteColors
    // ... plus a legacy 6-color overload and a @Deprecated colors-5tl4gsc
}
```

`verticalArrangement` defaults to `Arrangement.Top`; the scaffold KDoc recommends `Top`, `Center`
or `Bottom`. `Bottom` puts rail items in the thumb zone on tall windows — a real reachability win.

---

# 8. `NavigationSuiteScaffoldLayout` — layout only

Use this when you want the correct *placement* per type but supply the nav component yourself.
Two overloads `[API]`:

```
method @KotlinOnly @androidx.compose.runtime.Composable public static void NavigationSuiteScaffoldLayout(kotlin.jvm.functions.Function0<kotlin.Unit> navigationSuite, optional androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteType layoutType, optional androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffoldState state, optional kotlin.jvm.functions.Function0<kotlin.Unit> content);

method @KotlinOnly @androidx.compose.runtime.Composable public static void NavigationSuiteScaffoldLayout(kotlin.jvm.functions.Function0<kotlin.Unit> navigationSuite, androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteType navigationSuiteType, optional androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffoldState state, optional kotlin.jvm.functions.Function0<kotlin.Unit> primaryActionContent, optional androidx.compose.ui.Alignment.Horizontal primaryActionContentHorizontalAlignment, kotlin.jvm.functions.Function0<kotlin.Unit> content);
```

Overload 1 takes `layoutType` (optional). Overload 2 takes `navigationSuiteType` — **required**, not
optional — and adds the primary-action slot. Note you get no `Surface`, no container color, and no
inset consumption; `NavigationSuiteScaffold` adds those on top.

---

# 9. The two androidx `@Sampled` functions, verbatim

## 9.1 `NavigationSuiteScaffoldSample`

`[REPO androidx-m3:.../navigationsuite/samples/NavigationSuiteScaffoldSamples.kt lines 53–100]`:

```kotlin
@Preview
@Sampled
@Composable
fun NavigationSuiteScaffoldSample() {
    var selectedItem by remember { mutableIntStateOf(0) }
    val navItems = listOf("Songs", "Artists", "Playlists")
    val navSuiteType =
        NavigationSuiteScaffoldDefaults.navigationSuiteType(currentWindowAdaptiveInfoV2())
    val state = rememberNavigationSuiteScaffoldState()
    val scope = rememberCoroutineScope()

    NavigationSuiteScaffold(
        state = state,
        navigationItems = {
            navItems.forEachIndexed { index, navItem ->
                NavigationSuiteItem(
                    icon = {
                        Icon(
                            if (selectedItem == index) Icons.Filled.Favorite
                            else Icons.Outlined.FavoriteBorder,
                            contentDescription = null,
                        )
                    },
                    label = { Text(navItem) },
                    selected = selectedItem == index,
                    onClick = { selectedItem = index },
                )
            }
        },
    ) {
        // Screen content.
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                modifier = Modifier.padding(16.dp),
                text =
                    "Current NavigationSuiteType: $navSuiteType\n" +
                        "Visibility: ${state.currentValue}",
                textAlign = TextAlign.Center,
            )
            Button(onClick = { scope.launch { state.toggle() } }) {
                Text("Hide/show navigation component")
            }
        }
    }
}
```

Note: this sample computes `navSuiteType` only to *display* it — it does not pass it to the
scaffold, so the scaffold uses its own internal default. `currentWindowAdaptiveInfoV2()` is the
current entry point, and this sample uses it.

## 9.2 `NavigationSuiteScaffoldCustomConfigSample`

Verbatim in §5.1 above.

---

# 10. Reply's production `NavigationSuiteScaffoldLayout`

The reference implementation for "I want the placement logic but all my own content".

`[REPO /root/work/repos/compose-samples/Reply/app/src/main/java/com/example/reply/ui/navigation/ReplyNavigationComponents.kt lines 76–168]` verbatim:

```kotlin
private fun WindowSizeClass.isCompact() = !isWidthAtLeastBreakpoint(WindowSizeClass.WIDTH_DP_MEDIUM_LOWER_BOUND) ||
    !isHeightAtLeastBreakpoint(WindowSizeClass.HEIGHT_DP_MEDIUM_LOWER_BOUND)

class ReplyNavSuiteScope(val navSuiteType: NavigationSuiteType)

@Composable
fun ReplyNavigationWrapper(
    currentDestination: NavDestination?,
    navigateToTopLevelDestination: (ReplyTopLevelDestination) -> Unit,
    content: @Composable ReplyNavSuiteScope.() -> Unit,
) {
    val adaptiveInfo = currentWindowAdaptiveInfo()
    val windowSize = with(LocalDensity.current) {
        currentWindowSize().toSize().toDpSize()
    }

    val navLayoutType = when {
        adaptiveInfo.windowPosture.isTabletop -> NavigationSuiteType.NavigationBar

        adaptiveInfo.windowSizeClass.isCompact() -> NavigationSuiteType.NavigationBar

        adaptiveInfo.windowSizeClass.isWidthAtLeastBreakpoint(WindowSizeClass.WIDTH_DP_EXPANDED_LOWER_BOUND) &&
            windowSize.width >= 1200.dp -> NavigationSuiteType.NavigationDrawer

        else -> NavigationSuiteType.NavigationRail
    }
    val navContentPosition = if (adaptiveInfo.windowSizeClass.isHeightAtLeastBreakpoint(WindowSizeClass.HEIGHT_DP_MEDIUM_LOWER_BOUND)) {
        ReplyNavigationContentPosition.CENTER
    } else {
        ReplyNavigationContentPosition.TOP
    }

    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val coroutineScope = rememberCoroutineScope()
    // Avoid opening the modal drawer when there is a permanent drawer or a bottom nav bar,
    // but always allow closing an open drawer.
    val gesturesEnabled =
        drawerState.isOpen || navLayoutType == NavigationSuiteType.NavigationRail

    BackHandler(enabled = drawerState.isOpen) {
        coroutineScope.launch {
            drawerState.close()
        }
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        gesturesEnabled = gesturesEnabled,
        drawerContent = {
            ModalNavigationDrawerContent(
                currentDestination = currentDestination,
                navigationContentPosition = navContentPosition,
                navigateToTopLevelDestination = navigateToTopLevelDestination,
                onDrawerClicked = {
                    coroutineScope.launch {
                        drawerState.close()
                    }
                },
            )
        },
    ) {
        NavigationSuiteScaffoldLayout(
            layoutType = navLayoutType,
            navigationSuite = {
                when (navLayoutType) {
                    NavigationSuiteType.NavigationBar -> ReplyBottomNavigationBar(
                        currentDestination = currentDestination,
                        navigateToTopLevelDestination = navigateToTopLevelDestination,
                    )

                    NavigationSuiteType.NavigationRail -> ReplyNavigationRail(
                        currentDestination = currentDestination,
                        navigationContentPosition = navContentPosition,
                        navigateToTopLevelDestination = navigateToTopLevelDestination,
                        onDrawerClicked = {
                            coroutineScope.launch {
                                drawerState.open()
                            }
                        },
                    )

                    NavigationSuiteType.NavigationDrawer -> PermanentNavigationDrawerContent(
                        currentDestination = currentDestination,
                        navigationContentPosition = navContentPosition,
                        navigateToTopLevelDestination = navigateToTopLevelDestination,
                    )
                }
            },
        ) {
            ReplyNavSuiteScope(navLayoutType).content()
        }
    }
}
```

What to take from it:

- `NavigationSuiteScaffoldLayout` + a hand-written `when` over the type is the escape hatch that
  keeps placement/insets correct while you own every pixel of the container.
- Custom `isCompact()` = "compact in *either* dimension" — stricter than the library's per-axis
  checks, and the right call when a two-pane layout would be unusable.
- The drawer is gated on `isWidthAtLeastBreakpoint(WIDTH_DP_EXPANDED_LOWER_BOUND) && width >= 1200.dp`.
  Today write `isWidthAtLeastBreakpoint(WindowSizeClass.WIDTH_DP_LARGE_LOWER_BOUND)` — same 1200dp,
  one predicate.
- `ModalNavigationDrawer` wraps everything, with gestures disabled except in rail mode. Good
  reachability pattern: the rail's menu button opens the modal drawer; the permanent drawer never
  competes with a swipe.
- The type is threaded to content via a custom `ReplyNavSuiteScope` — cleaner than a composition
  local when only one subtree needs it.
- `when (navLayoutType) { ... }` compiles **only because it is a statement, not an expression** —
  `NavigationSuiteType` is a value class, so there is no exhaustiveness. Add an `else` if you ever
  assign the result.

> Cited verbatim. Reply calls the deprecated **`currentWindowAdaptiveInfo()`** and
> **`currentWindowSize()`**. Modern equivalents: `currentWindowAdaptiveInfoV2()` and
> `LocalWindowInfo.current.containerSize` / `.containerDpSize`.

Reply does **not** use `ListDetailPaneScaffold`; it pairs this wrapper with
`accompanist-adaptive`'s legacy `TwoPane`.

---

# 11. When `NavigationSuiteScaffold` is right, and when to hand-roll

Use `NavigationSuiteScaffold` when **all** of these hold:

- One flat set of top-level destinations, same on every window size.
- You are happy with a cross-fade between containers.
- You do not need the container's own state (rail expand/collapse) hoisted to a ViewModel.
- Your nav container is one of the eight built-in types.

Hand-roll (see `${CLAUDE_PLUGIN_ROOT}/skills/m3-expressive-navigation/references/nav-containers.md`)
when any of these hold:

- You want a **custom transition** between bar and rail — the scaffold cross-fades and gives you no
  hook.
- Your nav container is a **`HorizontalFloatingToolbar` used as navigation**, or any non-built-in
  container. That skill covers the toolbar-as-nav pattern including
  `FloatingToolbarDefaults.exitAlwaysScrollBehavior()`.
- You need the rail's `rememberWideNavigationRailState` hoisted, or a modal rail.
- You need a **different item set** per size class (e.g. overflow into the expanded rail).

Middle ground: `NavigationSuiteScaffoldLayout` (§8, §10) — library placement, your content.

Whichever you pick: **never show two nav containers at once.** A bar plus a rail, or a bar plus a
toolbar-as-nav, is the most common adaptive bug. See the "two nav containers" entry in
`adaptive-recipes.md` §Troubleshooting.

---

# 12. Integrating with Navigation 2 and Navigation 3

The scaffold is a *container*; it does not own routing. Put the nav host **inside** `content`, and
hoist the scaffold **above** it so it survives destination changes.

## 12.1 With Navigation 2 (`NavHost`)

```kotlin
val navController = rememberNavController()
val backStackEntry by navController.currentBackStackEntryAsState()
val current = backStackEntry?.destination

val navSuiteType =
    NavigationSuiteScaffoldDefaults.navigationSuiteType(currentWindowAdaptiveInfoV2())

NavigationSuiteScaffold(
    navigationSuiteType = navSuiteType,
    navigationItems = {
        TopLevel.entries.forEach { dest ->
            NavigationSuiteItem(
                navigationSuiteType = navSuiteType,
                selected = current?.hierarchy?.any { it.hasRoute(dest.route::class) } == true,
                onClick = {
                    navController.navigate(dest.route) {
                        popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                        launchSingleTop = true
                        restoreState = true
                    }
                },
                icon = { Icon(dest.icon, contentDescription = null) },
                label = { Text(stringResource(dest.label)) },
            )
        }
    },
) {
    NavHost(navController, startDestination = TopLevel.Home.route) { /* composable(...) */ }
}
```

The `popUpTo(startDestination) { saveState = true }` + `restoreState = true` triple is what keeps
per-tab state across container swaps. Without it, resizing from bar to rail re-creates screens.

## 12.2 With Navigation 3 (`NavDisplay`)

```kotlin
val backStack = rememberNavBackStack(HomeKey)
val navSuiteType =
    NavigationSuiteScaffoldDefaults.navigationSuiteType(currentWindowAdaptiveInfoV2())
val listDetailStrategy = rememberListDetailSceneStrategy<NavKey>()

NavigationSuiteScaffold(
    navigationSuiteType = navSuiteType,
    navigationItems = {
        TopLevel.entries.forEach { dest ->
            NavigationSuiteItem(
                navigationSuiteType = navSuiteType,
                selected = backStack.lastOrNull() == dest.key,
                // App-defined: replace the root entry rather than pushing, so tabs don't stack.
                onClick = { backStack.apply { clear(); add(dest.key) } },
                icon = { Icon(dest.icon, contentDescription = null) },
                label = { Text(stringResource(dest.label)) },
            )
        }
    },
) {
    NavDisplay(
        backStack = backStack,
        onBack = { backStack.removeLastOrNull() },
        sceneStrategies = listOf(listDetailStrategy),
        entryProvider = entryProvider { /* … */ },
    )
}
```

Two rules:

1. **Hoist the scaffold above `NavDisplay`, not per-destination.** Rebuilding the container per
   screen kills cross-destination shared transitions and re-animates the bar on every navigation.
2. The nav suite and the scene strategy read the *same* window info but are otherwise independent.
   The strategy decides pane count; the suite decides the nav container. They must not both try to
   render navigation.

Navigation3 routing details — `NavKey`, `rememberNavBackStack`, `entryProvider`, transition specs,
`SharedTransitionLayout` — are in
`${CLAUDE_PLUGIN_ROOT}/skills/m3-expressive-navigation/references/adaptive-and-nav3.md`.

---

# 13. `adaptive-navigation3` — back stack becomes panes

New in **1.3.0**. Bridges the pane scaffolds into Navigation 3's `SceneStrategy` model, so
`NavDisplay` renders multiple back-stack entries simultaneously as panes.

## 13.1 `ListDetailSceneStrategy<T>`

`[SRC@HEAD ListDetailSceneStrategy.kt]`:

```kotlin
/**
 * A [ListDetailSceneStrategy] supports arranging [NavEntry]s into an adaptive
 * [androidx.compose.material3.adaptive.layout.ListDetailPaneScaffold]. By using [listPane],
 * [detailPane], or [extraPane] in a NavEntry's metadata, entries can be assigned as belonging to a
 * list pane, detail pane, or extra pane. These panes will be displayed together if the window size
 * is sufficiently large, and will automatically adapt if the window size changes, for example, on a
 * foldable device.
 *
 * @param shouldHandleSinglePaneLayout whether [ListDetailSceneStrategy] should apply when only a
 *   single pane is displayed. By default, this is false and instead yields to the next
 *   [SceneStrategy] in the chain. If true, single pane layouts will instead be handled internally
 *   by the Material adaptive scaffold instead of the Navigation 3 system.
 * @param backNavigationBehavior the behavior describing which backstack entries may be skipped
 *   during the back navigation. See [BackNavigationBehavior].
 * @param directive The top-level directives about how the list-detail scaffold should arrange its
 *   panes.
 * @param adaptStrategies adaptation strategies of each pane, which denotes how each pane should be
 *   adapted if they can't fit on screen in the [PaneAdaptedValue.Expanded] state. It is recommended
 *   to use [ListDetailPaneScaffoldDefaults.adaptStrategies] as a default, but custom
 *   [ThreePaneScaffoldAdaptStrategies] are supported as well.
 * @param paneExpansionDragHandle when two panes are displayed side-by-side, a non-null drag handle
 *   allows users to resize the panes and change the pane expansion state.
 * @param paneExpansionState the state object of pane expansion. If this is null but a
 *   [paneExpansionDragHandle] is provided, a default implementation will be created.
 * @sample androidx.compose.material3.adaptive.samples.ListDetailWithNavigation3Sample
 */
@ExperimentalMaterial3AdaptiveApi
public class ListDetailSceneStrategy<T : Any>(
    @get:JvmName("shouldHandleSinglePaneLayout") public val shouldHandleSinglePaneLayout: Boolean,
    public val backNavigationBehavior: BackNavigationBehavior,
    public val directive: PaneScaffoldDirective,
    public val adaptStrategies: ThreePaneScaffoldAdaptStrategies,
    public val paneExpansionDragHandle:
        (@Composable
        ThreePaneScaffoldScope.(PaneExpansionState) -> Unit)?,
    public val paneExpansionState: PaneExpansionState?,
) : SceneStrategy<T> {
```

**The constructor has no default values.** Use `rememberListDetailSceneStrategy` for defaults. Type
bound is `T : Any`.

### How `calculateScene` groups entries

Semantics you must know (from `[SRC@HEAD]`):

1. If the **top** entry has no pane metadata → returns `null`; the strategy declines and `NavDisplay`
   falls through to the next strategy or normal single-pane rendering.
2. It walks the back stack **downward from the top**, stopping at the first entry without pane
   metadata. Only a **contiguous suffix** of the back stack becomes the scaffold.
3. Entries whose `sceneKey` differs from the top entry's are skipped (the walk continues). `sceneKey`
   lets you run multiple independent list-detail scaffolds in one `NavDisplay`.
4. Each `NavEntry.contentKey` becomes the `ThreePaneScaffoldDestinationItem.contentKey`.
5. **With the default `shouldHandleSinglePaneLayout = false` the strategy returns `null` whenever
   only one pane would show** — Nav3 owns the single-pane case and its transitions. Set `true` to let
   the Material scaffold own single-pane too.

### Metadata helpers — verbatim

```kotlin
/**
 * Constructs metadata to mark a [NavEntry] as belonging to a
 * [list pane][ListDetailPaneScaffoldRole.List] within a
 * [androidx.compose.material3.adaptive.layout.ListDetailPaneScaffold].
 *
 * @param sceneKey the key to distinguish the scene of the list-detail scaffold, in case
 *   multiple list-detail scaffolds are supported within the same NavDisplay.
 * @param detailPlaceholder composable content to display in the detail pane in case there
 *   is no other [NavEntry] representing a detail pane in the backstack. Note that this
 *   content does not receive the same scoping mechanisms as a full-fledged [NavEntry].
 */
public fun listPane(
    sceneKey: Any = Unit,
    detailPlaceholder: @Composable ThreePaneScaffoldScope.() -> Unit = {},
): Map<String, Any> = mapOf(ListDetailRoleKey to ListMetadata(sceneKey, detailPlaceholder))

/**
 * Constructs metadata to mark a [NavEntry] as belonging to a
 * [detail pane][ListDetailPaneScaffoldRole.Detail] within a
 * [androidx.compose.material3.adaptive.layout.ListDetailPaneScaffold].
 *
 * @param sceneKey the key to distinguish the scene of the list-detail scaffold, in case
 *   multiple list-detail scaffolds are supported within the same NavDisplay.
 */
public fun detailPane(sceneKey: Any = Unit): Map<String, Any> =
    mapOf(ListDetailRoleKey to DetailMetadata(sceneKey))

/**
 * Constructs metadata to mark a [NavEntry] as belonging to an
 * [extra pane][ListDetailPaneScaffoldRole.Extra] within a
 * [androidx.compose.material3.adaptive.layout.ListDetailPaneScaffold].
 *
 * @param sceneKey the key to distinguish the scene of the list-detail scaffold, in case
 *   multiple list-detail scaffolds are supported within the same NavDisplay.
 */
public fun extraPane(sceneKey: Any = Unit): Map<String, Any> =
    mapOf(ListDetailRoleKey to ExtraMetadata(sceneKey))
```

Only `listPane` takes `detailPlaceholder`. `sceneKey` defaults to `Unit` for all three. Because
`detailPlaceholder` is last, the trailing-lambda form works:
`ListDetailSceneStrategy.listPane { Placeholder() }`.

Two more helpers, both `@Composable`-free map builders:

```kotlin
public fun preferredPaneSize(
    width: Dp = Dp.Unspecified,
    height: Dp = Dp.Unspecified,
): Map<String, Any> = buildMap {
    if (width != Dp.Unspecified) { put(MetadataPreferredWidthKey, width) }
    if (height != Dp.Unspecified) { put(MetadataPreferredHeightKey, height) }
}

public fun preferredPaneSize(
    width: Float = Float.NaN,
    height: Float = Float.NaN,
): Map<String, Any> = buildMap {
    if (!width.isNaN()) { put(MetadataPreferredWidthKey, width) }
    if (!height.isNaN()) { put(MetadataPreferredHeightKey, height) }
}

/**
 * Constructs metadata to customize the animation of panes within a list-detail scaffold.
 *
 * If the value is null or unset, the default motions defined in [PaneMotionDefaults] will
 * be used instead.
 *
 * @param enterTransition The [EnterTransition] used to animate the pane in.
 * @param exitTransition The [ExitTransition] used to animate the pane out.
 * @param boundsAnimationSpec The [FiniteAnimationSpec] used to animate the bounds of the
 *   pane when it remains showing but changes its size and/or position.
 */
public fun paneAnimation(
    enterTransition: EnterTransition? = null,
    exitTransition: ExitTransition? = null,
    boundsAnimationSpec: FiniteAnimationSpec<IntRect>? = null,
): Map<String, Any> = buildMap { /* ... */ }
```

Combine metadata maps with `+` — all helpers return `Map<String, Any>` with disjoint keys:

```kotlin
entry<DetailKey>(
    metadata = ListDetailSceneStrategy.detailPane() +
        ListDetailSceneStrategy.preferredPaneSize(width = 400.dp)
) { ... }
```

`[UNVERIFIED]` that androidx documents `+` explicitly; map concatenation is the intended composition.

## 13.2 `rememberListDetailSceneStrategy`

```kotlin
@ExperimentalMaterial3AdaptiveApi
@Composable
public fun <T : Any> rememberListDetailSceneStrategy(
    shouldHandleSinglePaneLayout: Boolean = false,
    backNavigationBehavior: BackNavigationBehavior =
        BackNavigationBehavior.PopUntilScaffoldValueChange,
    directive: PaneScaffoldDirective =
        calculatePaneScaffoldDirective(currentWindowAdaptiveInfoV2()),
    adaptStrategies: ThreePaneScaffoldAdaptStrategies =
        ListDetailPaneScaffoldDefaults.adaptStrategies(),
    paneExpansionDragHandle: (@Composable ThreePaneScaffoldScope.(PaneExpansionState) -> Unit)? =
        null,
    paneExpansionState: PaneExpansionState? = null,
): ListDetailSceneStrategy<T> {
    return remember(
        shouldHandleSinglePaneLayout,
        backNavigationBehavior,
        directive,
        adaptStrategies,
        paneExpansionDragHandle,
        paneExpansionState,
    ) {
        ListDetailSceneStrategy(
            shouldHandleSinglePaneLayout = shouldHandleSinglePaneLayout,
            backNavigationBehavior = backNavigationBehavior,
            directive = directive,
            adaptStrategies = adaptStrategies,
            paneExpansionDragHandle = paneExpansionDragHandle,
            paneExpansionState = paneExpansionState,
        )
    }
}
```

The default `directive` already uses `currentWindowAdaptiveInfoV2()` — Large/XL-aware, single-pane at
medium width.

`paneExpansionDragHandle` is a **lambda inside the `remember` keys**. Pass a hoisted/stable lambda
or you re-create the strategy on every recomposition.

## 13.3 `SupportingPaneSceneStrategy<T>`

Mirror image, same parameter list, different defaults and metadata names.

`[API-1.3.0]`:

```
public static final class SupportingPaneSceneStrategy.Companion {
    method public java.util.Map<java.lang.String,java.lang.Object> extraPane(optional Object sceneKey);
    method public java.util.Map<java.lang.String,java.lang.Object> mainPane(optional Object sceneKey);
    method public java.util.Map<java.lang.String,java.lang.Object> paneAnimation(optional androidx.compose.animation.EnterTransition? enterTransition, optional androidx.compose.animation.ExitTransition? exitTransition, optional androidx.compose.animation.core.FiniteAnimationSpec<androidx.compose.ui.unit.IntRect>? boundsAnimationSpec);
    method @KotlinOnly public java.util.Map<java.lang.String,java.lang.Object> preferredPaneSize(optional androidx.compose.ui.unit.Dp width, optional androidx.compose.ui.unit.Dp height);
    method public java.util.Map<java.lang.String,java.lang.Object> preferredPaneSize(optional float width, optional float height);
    method public java.util.Map<java.lang.String,java.lang.Object> supportingPane(optional Object sceneKey);
}
```

Metadata helpers are **`mainPane`**, **`supportingPane`**, **`extraPane`**. **There is no
`detailPlaceholder` equivalent** — `mainPane(sceneKey)` takes only a scene key, because the main
pane is always present.

`adaptStrategies` default is `SupportingPaneScaffoldDefaults.adaptStrategies()` (which *reflows* the
supporting pane rather than hiding it). `[UNVERIFIED]` whether the `remember*` default literally
reads `SupportingPaneScaffoldDefaults` — the API file shows the parameter without its value —
strongly implied by symmetry.

```
method @KotlinOnly @SuppressCompatibility @androidx.compose.material3.adaptive.ExperimentalMaterial3AdaptiveApi @androidx.compose.runtime.Composable public static <T> androidx.compose.material3.adaptive.navigation3.SupportingPaneSceneStrategy<T> rememberSupportingPaneSceneStrategy(optional boolean shouldHandleSinglePaneLayout, optional androidx.compose.material3.adaptive.navigation.BackNavigationBehavior backNavigationBehavior, optional androidx.compose.material3.adaptive.layout.PaneScaffoldDirective directive, optional androidx.compose.material3.adaptive.layout.ThreePaneScaffoldAdaptStrategies adaptStrategies, optional kotlin.jvm.functions.Function2<androidx.compose.material3.adaptive.layout.ThreePaneScaffoldScope,androidx.compose.material3.adaptive.layout.PaneExpansionState,kotlin.Unit>? paneExpansionDragHandle, optional androidx.compose.material3.adaptive.layout.PaneExpansionState? paneExpansionState);
```

## 13.4 Scene scopes — how an entry knows it is in a pane

`[API-1.3.0]`:

```
@SuppressCompatibility @androidx.compose.material3.adaptive.ExperimentalMaterial3AdaptiveApi public sealed nonexhaustive interface ListDetailSceneScope {
    property public abstract androidx.compose.material3.adaptive.layout.PaneScaffoldTransitionScope<androidx.compose.material3.adaptive.layout.ThreePaneScaffoldRole,androidx.compose.material3.adaptive.layout.ThreePaneScaffoldValue> scaffoldTransitionScope;
}

public final class ListDetailSceneScopeKt {
    property @SuppressCompatibility @androidx.compose.material3.adaptive.ExperimentalMaterial3AdaptiveApi public static androidx.compose.runtime.ProvidableCompositionLocal<androidx.compose.material3.adaptive.navigation3.ListDetailSceneScope?> LocalListDetailSceneScope;
}
```

Same shape for `SupportingPaneSceneScope` / `LocalSupportingPaneSceneScope`.

**The critical idiom: `LocalListDetailSceneScope.current == null` means "I am NOT inside a
list-detail scaffold right now"** — i.e. this entry is rendering as a normal single-pane Nav3
destination. Use it to decide whether to show a back button:

```kotlin
entry<DetailKey>(metadata = ListDetailSceneStrategy.detailPane()) {
    val scaffoldSceneScope = LocalListDetailSceneScope.current
    DetailPaneContent(
        selectedItem = selectedIndex?.let { items[it] },
        onShowExtra = { ... },
        backButton =
            if (scaffoldSceneScope == null) {
                // Only show back button in a single-pane context
                { BackButton(onClick = { backStack.removeLastOrNull() }) }
            } else null,
    )
}
```

The scope also exposes `scaffoldTransitionScope: PaneScaffoldTransitionScope<ThreePaneScaffoldRole,
ThreePaneScaffoldValue>` for driving your own motion off the scaffold transition.

`ThreePaneScaffoldScene` (the internal `Scene<T>` implementation, 559 lines, all `internal`) is not
public API. Do not depend on it.

## 13.5 The androidx `@Sampled` composition with `NavDisplay`

`[REPO androidx-m3:.../samples/ThreePaneScaffoldSample.kt lines 647–730]` verbatim:

```kotlin
@OptIn(ExperimentalMaterial3AdaptiveApi::class)
@Preview
@Sampled
@Composable
fun ListDetailWithNavigation3Sample() {
    val backStack = rememberNavBackStack(HomeKey)
    val sceneStrategy = rememberListDetailSceneStrategy<Any>()

    val items = listOf("Item 1", "Item 2", "Item 3")
    val extraItems = listOf("Extra 1", "Extra 2", "Extra 3")

    val selectedIndex =
        backStack.lastOrNull()?.let {
            when (it) {
                is DetailKey -> it.index
                is ExtraKey -> it.index
                else -> null
            }
        }

    NavDisplay(
        backStack = backStack,
        modifier = Modifier.fillMaxSize(),
        sceneStrategies = listOf(sceneStrategy),
        entryProvider =
            entryProvider {
                entry<HomeKey> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Button(
                            onClick = { if (backStack.last() != ListKey) backStack.add(ListKey) }
                        ) {
                            Text("Go to list")
                        }
                    }
                }

                entry<ListKey>(
                    metadata =
                        ListDetailSceneStrategy.listPane(
                            detailPlaceholder = {
                                DetailPaneContent(selectedItem = null, onShowExtra = {})
                            }
                        )
                ) {
                    ListPaneContent(
                        items = items,
                        selectedIndex = selectedIndex,
                        onItemClick = { index ->
                            val dest = DetailKey(index)
                            if (backStack.last() != dest) backStack.add(dest)
                        },
                    )
                }

                entry<DetailKey>(metadata = ListDetailSceneStrategy.detailPane()) {
                    val scaffoldSceneScope = LocalListDetailSceneScope.current
                    DetailPaneContent(
                        selectedItem = selectedIndex?.let { items[it] },
                        onShowExtra = {
                            val dest = ExtraKey(selectedIndex!!)
                            if (backStack.last() != dest) backStack.add(dest)
                        },
                        backButton =
                            if (scaffoldSceneScope == null) {
                                // Only show back button in a single-pane context
                                { BackButton(onClick = { backStack.removeLastOrNull() }) }
                            } else null,
                    )
                }

                entry<ExtraKey>(metadata = ListDetailSceneStrategy.extraPane()) {
                    val scaffoldSceneScope = LocalListDetailSceneScope.current
                    ExtraPaneContent(
                        item = extraItems[selectedIndex!!],
                        backButton =
                            if (scaffoldSceneScope == null) {
                                // Only show back button in a single-pane context
                                { BackButton(onClick = { backStack.removeLastOrNull() }) }
                            } else null,
                    )
                }
            },
    )
}
```

Nav keys `[same file, lines 753–759]`:

```kotlin
@Serializable private data object HomeKey : NavKey

@Serializable private data object ListKey : NavKey

@Serializable private data class DetailKey(val index: Int) : NavKey

@Serializable private data class ExtraKey(val index: Int) : NavKey
```

Points that matter:

- `HomeKey` has **no** pane metadata → when it is on top the strategy returns `null` and Nav3 renders
  it full-screen. The scaffold only engages from `ListKey` upward.
- `sceneStrategies = listOf(sceneStrategy)` — a **list**; strategies are tried in order.
- Every `backStack.add` is guarded with `if (backStack.last() != dest)` to avoid duplicate entries.
- **No `AnimatedPane` anywhere** — the scene strategy wraps entries itself. Adding `AnimatedPane`
  inside a nav3 entry is wrong.
- Selection state is **derived from the back stack**, not held separately. That is what makes
  resize-time restoration correct for free.

## 13.6 nowinandroid — production `ListDetailSceneStrategy` entry providers

The best real example. Deps `[REPO nowinandroid/gradle/libs.versions.toml]`:

```toml
androidxComposeMaterial3Adaptive = "1.1.0-rc01"
androidxComposeMaterial3AdaptiveNavigation3 = "1.3.0-alpha04"
androidxWindowManager = "1.3.0"
# ...
androidx-compose-material3-navigationSuite = { group = "androidx.compose.material3", name = "material3-adaptive-navigation-suite" }
androidx-compose-material3-adaptive = { group = "androidx.compose.material3.adaptive", name = "adaptive", version.ref = "androidxComposeMaterial3Adaptive" }
androidx-compose-material3-adaptive-layout = { group = "androidx.compose.material3.adaptive", name = "adaptive-layout", version.ref = "androidxComposeMaterial3Adaptive" }
androidx-compose-material3-adaptive-navigation = { group = "androidx.compose.material3.adaptive", name = "adaptive-navigation", version.ref = "androidxComposeMaterial3Adaptive" }
androidx-compose-material3-adaptive-navigation3 = { group = "androidx.compose.material3.adaptive", name = "adaptive-navigation3",version.ref="androidxComposeMaterial3AdaptiveNavigation3" }
androidx-compose-material3-windowSizeClass = { group = "androidx.compose.material3", name = "material3-window-size-class" }
androidx-window-core = { group = "androidx.window", name = "window-core", version.ref = "androidxWindowManager" }
```

**List pane entry** — `feature/interests/impl/src/main/kotlin/.../navigation/InterestsEntryProvider.kt`,
verbatim:

```kotlin
@OptIn(ExperimentalMaterial3AdaptiveApi::class)
fun EntryProviderScope<NavKey>.interestsEntry(navigator: Navigator) {
    entry<InterestsNavKey>(
        metadata = ListDetailSceneStrategy.listPane {
            InterestsDetailPlaceholder()
        },
    ) { key ->
        val viewModel = hiltViewModel<InterestsViewModel, InterestsViewModel.Factory> {
            it.create(key)
        }
        InterestsScreen(
            // TODO: This event should either be provided by the ViewModel or by the navigator, not both
            onTopicClick = navigator::navigateToTopic,

            // TODO: This should be dynamically calculated based on the rendering scene
            //  See https://github.com/android/nav3-recipes/commit/488f4811791ca3ed7192f4fe3c86e7371b32ebdc#diff-374e02026cdd2f68057dd940f203dc4ba7319930b33e9555c61af7e072211cabR89
            shouldHighlightSelectedTopic = false,
            viewModel = viewModel,
        )
    }
}
```

**Detail pane entry** — `feature/topic/impl/src/main/kotlin/.../navigation/TopicEntryProvider.kt`:

```kotlin
@OptIn(ExperimentalMaterial3AdaptiveApi::class)
fun EntryProviderScope<NavKey>.topicEntry(navigator: Navigator) {
    entry<TopicNavKey>(
        metadata = ListDetailSceneStrategy.detailPane(),
    ) { key ->
        val id = key.id
        TopicScreen(
            showBackButton = true,
            onBackClick = { navigator.goBack() },
            onTopicClick = navigator::navigateToTopic,
            viewModel = hiltViewModel<TopicViewModel, Factory>(
                key = id,
            ) { factory ->
                factory.create(id)
            },
        )
    }
}
```

**Wiring** — `app/src/main/kotlin/.../ui/NiaApp.kt` around line 257:

```kotlin
val listDetailStrategy = rememberListDetailSceneStrategy<NavKey>()

val entryProvider = entryProvider {
    forYouEntry(navigator)
    bookmarksEntry(navigator)
    interestsEntry(navigator)
    topicEntry(navigator)
    searchEntry(navigator)
}

NavDisplay(
    entries = appState.navigationState.toEntries(entryProvider),
    sceneStrategy = listDetailStrategy,
    onBack = { navigator.goBack() },
)
```

What to copy, and what NIA gets wrong:

- **Copy the file layout.** One `EntryProviderScope<NavKey>.xEntry(navigator)` extension per feature
  module, metadata declared next to the screen it belongs to. This is the only structure that scales
  past a handful of destinations, and it keeps the pane role with the feature.
- **Copy** the trailing-lambda `listPane { Placeholder() }` form.
- **Do not copy `showBackButton = true`.** NIA hardcodes it; the androidx sample derives it from
  `LocalListDetailSceneScope.current == null`. Hardcoding shows a back button in the detail pane
  even when the list is visible beside it.
- **Do not copy `shouldHighlightSelectedTopic = false`.** NIA's own `TODO` admits it: the list should
  highlight the selected item when both panes show. Derive it the same way — highlight when
  `LocalListDetailSceneScope.current != null`.
- `sceneStrategy = ` (singular) here vs `sceneStrategies = listOf(...)` in the androidx sample —
  different nav3 versions expose different parameter names. `[UNVERIFIED]` which version introduced
  which. Check autocomplete against your pin.

**Nav suite** — `core/designsystem/src/main/kotlin/.../component/Navigation.kt`:

```kotlin
/**
 * Wraps Material 3 [NavigationSuiteScaffold].
 * ...
 * @param navigationSuiteItems A slot to display multiple items via [NiaNavigationSuiteScope].
 */
@Composable
fun NiaNavigationSuiteScaffold(
    navigationSuiteItems: NiaNavigationSuiteScope.() -> Unit,
    modifier: Modifier = Modifier,
    windowAdaptiveInfo: WindowAdaptiveInfo = currentWindowAdaptiveInfo(),
    // ...
) {
    val layoutType = NavigationSuiteScaffoldDefaults
        .calculateFromAdaptiveInfo(windowAdaptiveInfo)
    val navigationSuiteItemColors = NavigationSuiteItemColors(
        // ...
    )
    // ...
    NavigationSuiteScaffold(
        // ...
        navigationSuiteColors = NavigationSuiteDefaults.colors(
            // ...
        ),
    )
}
```

> Cited verbatim; uses deprecated `currentWindowAdaptiveInfo()` and the legacy
> `calculateFromAdaptiveInfo` (so: classic `NavigationBar`/`NavigationRail`, not Expressive
> containers). Modernise both when you copy it.

**The one pattern worth stealing wholesale:** hoisting `windowAdaptiveInfo` as a parameter with a
default. That is what makes NIA's `NiaAppScreenSizesScreenshotTests.kt` able to inject fake adaptive
info and screenshot every size class. Do this on any composable that branches on window size.

---

# 14. Gotchas

1. **`NavigationSuiteType` is a `value class` over `String`, not an enum.** No `values()`, no
   exhaustive `when`. A `when` *expression* over it needs an `else`.
2. **`navigationSuiteType()` never returns `WideNavigationRailExpanded`, `NavigationDrawer` or
   `None`.** Compute those yourself (§5).
3. **`navigationSuiteType()` has no Large/XL branch.** 1600dp gets the same collapsed rail as 850dp.
4. **Two overloads, different defaults.** `navigationItems` → `navigationSuiteType()` (Expressive
   containers); `navigationSuiteItems` → `calculateFromAdaptiveInfo()` (legacy containers). The
   parameter is also named differently: `navigationSuiteType` vs `layoutType`.
5. **With the modern overload, pass `navigationSuiteType` to the scaffold AND every
   `NavigationSuiteItem`** when overriding, or items render for the wrong container.
6. **`NavigationSuiteItem.label` is a required positional parameter** sitting before `modifier`.
   `label = null` for unlabelled. `NavigationSuiteScope.item`'s `label` is optional. Migrating
   between overloads is a guaranteed compile error until you fix this.
7. **`NavigationSuiteItemColors` covers only the legacy trio.** No short-bar or wide-rail slot; the
   modern item takes `NavigationItemColors` instead.
8. **Prefer `NavigationSuiteScaffoldState.hide()` over `NavigationSuiteType.None`.** KDoc says so.
   All state mutators are `suspend`.
9. **`material3-adaptive-navigation-suite` is in group `androidx.compose.material3`.** Its version
   tracks material3 (1.5.0-alpha26), **not** the adaptive 1.3.0 line. Do not try to align them.
10. **`ListDetailSceneStrategy` returns `null` for single-pane by default.** If single-pane
    transitions look wrong, that is why — Nav3 owns them. `shouldHandleSinglePaneLayout = true` hands
    them to the scaffold.
11. **`calculateScene` stops at the first entry lacking pane metadata.** Only a contiguous suffix of
    the back stack is grouped. An un-annotated entry in the middle silently truncates the scaffold.
12. **`rememberListDetailSceneStrategy` puts `paneExpansionDragHandle` in the `remember` keys.** Pass
    a stable lambda or the strategy is rebuilt every recomposition.
13. **`LocalListDetailSceneScope.current == null` means "rendering single-pane."** Use it for back
    buttons and selection highlighting; do not hardcode either.
14. **Do not put `AnimatedPane` inside a nav3 entry.** The scene strategy already wraps entries. Use
    `AnimatedPane` only with the raw `ListDetailPaneScaffold`/`SupportingPaneScaffold` APIs.
15. **`SupportingPaneSceneStrategy.mainPane()` has no `detailPlaceholder`.** Only the list-detail
    strategy has one, because only it can have an empty primary pane.
16. **Never render two nav containers.** The suite scaffold plus a `Scaffold(bottomBar = …)` is the
    classic double-nav bug; so is a toolbar-as-nav inside a suite scaffold.

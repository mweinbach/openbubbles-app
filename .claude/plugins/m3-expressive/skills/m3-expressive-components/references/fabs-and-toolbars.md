# M3 Expressive FABs, FAB menus and floating toolbars

FAB sizes and variants, `FloatingActionButtonMenu`, `ToggleFloatingActionButton`, floating and
docked toolbars, and how all of it interacts with `Scaffold`, insets and edge-to-edge.

Every section: **signature → when to use → working code → pitfalls.**

Provenance markers:

- `[CORPUS <repo>]` — from a shipping open-source app; path given. Safe to copy.
- `[ANDROIDX]` — signature from androidx source or the rendered API reference. Verify it resolves
  against your pinned artifact.
- `[UNVERIFIED]` — name real, exact signature/value unconfirmed. Do not paste blind.

Corpus repos: `/root/work/repos/{vivi-music,Tomato,LastChat,Med}`.
Med has the best FAB menu in the corpus. Tomato has the best toolbar pattern.

---

# 1. FAB sizes and variants

| Composable | Notes |
| --- | --- |
| `SmallFloatingActionButton` | Baseline. |
| `FloatingActionButton` | Baseline, the default size. |
| **`MediumFloatingActionButton`** | **The Expressive addition.** Baseline M3 had Small/Regular/Large/Extended only. |
| `LargeFloatingActionButton` | Baseline. |
| `ExtendedFloatingActionButton` | Icon + text. **Cannot be used with a FAB menu** (§3). |
| `ToggleFloatingActionButton` | Expressive. Animates container size, corner radius and color from a 0..1 checked progress. The FAB menu's opener. |

## `MediumFloatingActionButton` / `LargeFloatingActionButton`

`[ANDROIDX]`, verbatim:

```kotlin
@Composable
fun MediumFloatingActionButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    shape: Shape = FloatingActionButtonDefaults.mediumShape,
    containerColor: Color = FloatingActionButtonDefaults.containerColor,
    contentColor: Color = contentColorFor(containerColor),
    elevation: FloatingActionButtonElevation = FloatingActionButtonDefaults.elevation(),
    interactionSource: MutableInteractionSource? = null,
    content: @Composable () -> Unit,
)

@Composable
fun LargeFloatingActionButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    shape: Shape = FloatingActionButtonDefaults.largeShape,
    containerColor: Color = FloatingActionButtonDefaults.containerColor,
    contentColor: Color = contentColorFor(containerColor),
    elevation: FloatingActionButtonElevation = FloatingActionButtonDefaults.elevation(),
    interactionSource: MutableInteractionSource? = null,
    content: @Composable () -> Unit,
)
```

`FloatingActionButtonDefaults` constants `[ANDROIDX]`, verbatim:

```kotlin
val MediumIconSize = FabMediumTokens.IconSize
val LargeIconSize = 36.dp

val mediumShape: Shape @Composable get() = ShapeDefaults.LargeIncreased        // 20dp
val largeShape: Shape  @Composable get() = FabLargeTokens.ContainerShape.value

val ShowHideTargetScale: Float          // value [UNVERIFIED]
```

> The `developer.android.com/develop/ui/compose/components/fab` guide page has **not** been updated
> for Expressive — it still documents only FAB / SmallFAB / LargeFAB / ExtendedFAB and has no FAB
> menu content at all. Do not use it as a source.

## `Modifier.animateFloatingActionButton`

`[ANDROIDX]` `FloatingActionButton.kt`, verbatim:

```kotlin
fun Modifier.animateFloatingActionButton(
    visible: Boolean,
    alignment: Alignment,
    targetScale: Float = FloatingActionButtonDefaults.ShowHideTargetScale,
    scaleAnimationSpec: AnimationSpec<Float>? = null,
    alphaAnimationSpec: AnimationSpec<Float>? = null,
): Modifier
```

**When to use**: any time a FAB appears or disappears. It applies the Material scale+alpha show/hide
transition anchored at `alignment`, instead of the FAB popping in.

Passing `null` specs uses the theme's `MotionScheme`. Pass explicit specs only when you need to
deviate.

**Pitfalls**

- `alignment` must match where the FAB actually sits (`Alignment.BottomEnd` for a standard Scaffold
  FAB) or it scales from the wrong origin.
- A crash in this modifier was fixed in **1.5.0-alpha19**. If you're on an earlier alpha and seeing
  crashes on FAB hide, that's it.
- Do not wrap the FAB in `AnimatedVisibility` *and* use this modifier — pick one.

---

# 2. `FloatingActionButtonMenu`

## Signatures

`[ANDROIDX]` `FloatingActionButtonMenu.kt`. Opt-in in 1.4.0: `ExperimentalMaterial3ExpressiveApi`;
**graduated in 1.5.0-alpha19.**

```kotlin
@Composable
fun FloatingActionButtonMenu(
    expanded: Boolean,
    button: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    horizontalAlignment: Alignment.Horizontal = Alignment.End,
    content: @Composable FloatingActionButtonMenuScope.() -> Unit,
)

@Composable
fun FloatingActionButtonMenuScope.FloatingActionButtonMenuItem(
    onClick: () -> Unit,
    text: @Composable () -> Unit,
    icon: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    containerColor: Color = MaterialTheme.colorScheme.primaryContainer,
    contentColor: Color = contentColorFor(containerColor),
)

@Composable
fun ToggleFloatingActionButton(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    containerColor: (Float) -> Color = ToggleFloatingActionButtonDefaults.containerColor(),
    contentAlignment: Alignment = Alignment.TopEnd,
    containerSize: (Float) -> Dp = ToggleFloatingActionButtonDefaults.containerSize(),
    containerCornerRadius: (Float) -> Dp =
        ToggleFloatingActionButtonDefaults.containerCornerRadius(),
    content: @Composable ToggleFloatingActionButtonScope.() -> Unit,
)
```

KDoc, verbatim: *"FAB Menus should be used in conjunction with a `ToggleFloatingActionButton` to
provide additional choices to the user after clicking a FAB."*

`ToggleFloatingActionButton` *"supports animating its container size, corner radius, and color when
it is toggled."* Every `(Float) -> …` parameter is driven by the **0..1 checked progress**, not by a
boolean. That is the whole design: you get continuous interpolation for free.

`ToggleFloatingActionButtonScope` exposes **`checkedProgress`** — read it inside `content`.

## `ToggleFloatingActionButtonDefaults`

`[ANDROIDX]`, `animateIcon` verbatim:

```kotlin
@Composable
fun Modifier.animateIcon(
    checkedProgress: () -> Float,
    color: (Float) -> Color = iconColor(),
    size: (Float) -> Dp = iconSize(),
)
```

Plus `containerColor()`, `containerSize()`, `containerCornerRadius()`, `iconColor()`, `iconSize()` —
all returning `(Float) -> T` progress-driven lambdas. Exact signatures of those five
`[UNVERIFIED]`.

## Complete verbatim implementation — Med's `MainFAB`

The most complete, accessible expressive-FAB implementation in the corpus: tooltip that repositions
when expanded, full semantics (`traversalIndex`, `stateDescription`, `isTraversalGroup`, a
`CustomAccessibilityAction` to close the menu from the last item), and `animateIcon` driven by the
scope's `checkedProgress`.

`[CORPUS Med]` `app/src/main/kotlin/com/fedeveloper95/med/elements/MainActivity/MainFAB.kt`
(all 146 lines):

```kotlin
@file:OptIn(
    ExperimentalMaterial3ExpressiveApi::class, ExperimentalMaterial3Api::class,
    ExperimentalTextApi::class
)

package com.fedeveloper95.med.elements.MainActivity

import android.graphics.Color.parseColor
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FloatingActionButtonMenu
import androidx.compose.material3.FloatingActionButtonMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.Text
import androidx.compose.material3.ToggleFloatingActionButton
import androidx.compose.material3.ToggleFloatingActionButtonDefaults.animateIcon
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.animateFloatingActionButton
import androidx.compose.material3.rememberTooltipState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.isTraversalGroup
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.traversalIndex
import androidx.compose.ui.text.ExperimentalTextApi
import com.fedeveloper95.med.ItemType
import com.fedeveloper95.med.R
import com.fedeveloper95.med.TooltipPosition
import com.fedeveloper95.med.rememberCustomTooltipPositionProvider
import com.fedeveloper95.med.ui.theme.GoogleSansFlex

@Composable
fun MainFAB(
    fabMenuExpanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    menuItems: List<Triple<ItemType, androidx.compose.ui.graphics.vector.ImageVector, Triple<String, String?, String?>>>,
    onMenuItemClick: (ItemType, String, String?, String?) -> Unit
) {
    MaterialExpressiveTheme(
        colorScheme = MaterialTheme.colorScheme,
        typography = MaterialTheme.typography,
        shapes = MaterialTheme.shapes,
        motionScheme = MotionScheme.expressive()
    ) {
        FloatingActionButtonMenu(
            expanded = fabMenuExpanded,
            button = {
                val tooltipPos =
                    if (fabMenuExpanded) TooltipPosition.Start else TooltipPosition.Above
                val expandedString = stringResource(R.string.expanded_state)
                val collapsedString = stringResource(R.string.collapsed_state)
                val menuActionDesc = stringResource(R.string.menu_action_desc)
                TooltipBox(
                    positionProvider = rememberCustomTooltipPositionProvider(tooltipPos),
                    tooltip = {
                        PlainTooltip {
                            Text(
                                stringResource(R.string.menu_tooltip),
                                fontFamily = GoogleSansFlex
                            )
                        }
                    },
                    state = rememberTooltipState()
                ) {
                    ToggleFloatingActionButton(
                        modifier = Modifier
                            .semantics {
                                traversalIndex = -1f
                                stateDescription =
                                    if (fabMenuExpanded) expandedString else collapsedString
                                contentDescription = menuActionDesc
                            }
                            .animateFloatingActionButton(
                                visible = true,
                                alignment = Alignment.BottomEnd
                            )
                            .focusRequester(remember { FocusRequester() }),
                        checked = fabMenuExpanded,
                        onCheckedChange = { onExpandedChange(!fabMenuExpanded) }
                    ) {
                        val imageVector by remember { derivedStateOf { if (checkedProgress > 0.5f) Icons.Filled.Close else Icons.Filled.Add } }
                        Icon(
                            painter = rememberVectorPainter(imageVector),
                            contentDescription = null,
                            modifier = Modifier.animateIcon({ checkedProgress })
                        )
                    }
                }
            }
        ) {
            val closeMenuString = stringResource(R.string.close_menu_action)
            menuItems.forEachIndexed { i, item ->
                val (name, iconName, colorCode) = item.third

                FloatingActionButtonMenuItem(
                    modifier = Modifier.semantics {
                        isTraversalGroup = true
                        if (i == menuItems.size - 1) customActions =
                            listOf(
                                CustomAccessibilityAction(
                                    label = closeMenuString,
                                    action = { onExpandedChange(false); true })
                            )
                    },
                    onClick = {
                        onExpandedChange(false)
                        onMenuItemClick(item.first, name, iconName, colorCode)
                    },
                    icon = {
                        if (colorCode != null && colorCode != "dynamic") {
                            val color = try {
                                Color(parseColor(colorCode))
                            } catch (e: Exception) {
                                MaterialTheme.colorScheme.primary
                            }
                            Icon(item.second, contentDescription = null, tint = color)
                        } else {
                            Icon(item.second, contentDescription = null)
                        }
                    },
                    text = { Text(text = name, fontFamily = GoogleSansFlex) }
                )
            }
        }
    }
}
```

### What to take from it

1. **`checkedProgress` is in scope inside `ToggleFloatingActionButton`'s content.** Use it twice —
   once in a `derivedStateOf` to swap the vector at 0.5, once in `Modifier.animateIcon({ checkedProgress })`
   to cross-fade and resize. Passing it as a lambda (`{ checkedProgress }`) not a value is
   deliberate: it defers the read so only the modifier's draw phase invalidates.
2. **Nested `MaterialExpressiveTheme`.** Med's root theme is a plain `MaterialTheme`, so the FAB
   wraps itself in an expressive theme purely to guarantee `MotionScheme.expressive()` is in scope
   for the menu animation. Copy this only if your app's root theme is not already expressive.
3. **`traversalIndex = -1f`** puts the FAB before the menu items in TalkBack order even though it
   comes after them visually when expanded.
4. **`stateDescription` on the opener** — expanded/collapsed. Without it TalkBack says nothing about
   the menu's state.
5. **`isTraversalGroup = true` on every item** so each item's icon + text read as one node.
6. **`CustomAccessibilityAction` on the last item** — a screen-reader user who traverses to the end
   of the menu can close it without hunting back for the FAB. This is the detail almost every
   implementation misses.

## Mounting it in a `Scaffold`

`[CORPUS Med]` `app/src/main/kotlin/com/fedeveloper95/med/services/MedApp.kt:204-292`:

```kotlin
BackHandler(enabled = fabMenuExpanded || selectedTab != 0) {
    if (fabMenuExpanded) {
        fabMenuExpanded = false
    } else if (selectedTab != 0) {
        selectedTab = 0
    }
}
// ...
Scaffold(
    modifier = Modifier.weight(1f),
    containerColor = MaterialTheme.colorScheme.background,
    floatingActionButton = {
        if (selectedTab == 0) {
            Box(modifier = Modifier.wrapContentSize(unbounded = true)) {
                MainFAB(
                    fabMenuExpanded = fabMenuExpanded,
                    onExpandedChange = { fabMenuExpanded = it },
                    menuItems = menuItems,
                    onMenuItemClick = { type, name, iconName, colorCode ->
                        fabMenuExpanded = false
                        // ...
                    }
                )
            }
        }
    },
)
```

**Two non-obvious requirements:**

- **`Box(Modifier.wrapContentSize(unbounded = true))`.** The `Scaffold` FAB slot is measured to the
  collapsed FAB. Without `unbounded = true` the expanded menu is clipped to the FAB's box. This is
  the single most common FAB-menu bug.
- **A `BackHandler` that collapses before it navigates.** Back must close the menu first. The
  component does not do this for you.

## Med's custom tooltip `PopupPositionProvider`

`TooltipDefaults` has no "start" anchor, so Med wrote one. `MainFAB` swaps `Above` → `Start` when
the menu opens so the tooltip doesn't collide with the menu items stacked above the FAB.

`[CORPUS Med]` `app/src/main/kotlin/com/fedeveloper95/med/MainActivity.kt:338-373`:

```kotlin
enum class TooltipPosition { Above, Start }

@Composable
fun rememberCustomTooltipPositionProvider(
    position: TooltipPosition,
    spacing: Int = 8
): PopupPositionProvider {
    val density = LocalDensity.current
    val spacingPx = with(density) { spacing.dp.roundToPx() }

    return remember(position, density) {
        object : PopupPositionProvider {
            override fun calculatePosition(
                anchorBounds: IntRect,
                windowSize: IntSize,
                layoutDirection: LayoutDirection,
                popupContentSize: IntSize
            ): IntOffset {
                return when (position) {
                    TooltipPosition.Above -> {
                        val x =
                            anchorBounds.left + (anchorBounds.width - popupContentSize.width) / 2
                        val y = anchorBounds.top - popupContentSize.height - spacingPx
                        IntOffset(x, y)
                    }

                    TooltipPosition.Start -> {
                        val x = anchorBounds.left - popupContentSize.width - spacingPx
                        val y =
                            anchorBounds.top + (anchorBounds.height - popupContentSize.height) / 2
                        IntOffset(x, y)
                    }
                }
            }
        }
    }
}
```

`remember(position, density)` is load-bearing — the provider must be recreated when `position`
flips, or the tooltip stays where it was.

If you only need `Above`, use the built-in:
`TooltipDefaults.rememberTooltipPositionProvider(TooltipAnchorPosition.Above)` — that is what Tomato
does (§5).

## FAB menu design constraints

`[GOOGLE — FloatingActionButtonMenu.md]`

- **"Use the FAB menu to show multiple related actions in a prominent, expressive style."**
- **Item count: 2–6 related actions.** One item is a plain FAB. More than six is a bottom sheet or a
  full screen.
- **What it replaces**: "It should replace the speed dial and any usage of stacked small FABs."
- **Placement**: it "opens from a FAB to show multiple related actions" and "should always appear in
  the same place as the FAB that opened it." Never open it centred, or from a different corner.
- **Sizing**: one consistent menu size regardless of the size of the FAB that opens it.
- **Hard exclusion**: **"Fab menu is not used with extended FABs."** There is no supported way to
  combine them.
- Visual strategy: it "uses contrasting colors and large items to focus attention."

### More than six actions

Do not extend the menu. Pick one:

- Promote the top 2–6 into the menu, put the rest behind an "More…" item that opens a bottom sheet.
- Replace the FAB entirely with a floating toolbar + overflow menu (§4) if the actions are
  contextual to the content rather than creative/additive.
- Move them into a full-screen picker if the choice is the whole task.

### Anti-patterns

| Don't | Instead |
| --- | --- |
| Build a speed dial or stack small FABs | Use the FAB menu |
| Use a FAB menu with an **extended FAB** | Not supported |
| Exceed 6 FAB-menu actions | Keep to 2–6 |
| Open the menu somewhere other than its FAB | It "should always appear in the same place as the FAB that opened it" |
| Forget `wrapContentSize(unbounded = true)` | The expanded menu clips |
| Forget the `BackHandler` | Back navigates away with the menu still open |

---

# 3. Floating toolbars

## Signatures

`[ANDROIDX]`. Opt-in in 1.4.0: `ExperimentalMaterial3ExpressiveApi`; **graduated 1.5.0-alpha22.**

Four composables: horizontal/vertical × (three-slot | with-FAB).

```kotlin
@Composable
fun HorizontalFloatingToolbar(
    expanded: Boolean,
    modifier: Modifier = Modifier,
    colors: FloatingToolbarColors = FloatingToolbarDefaults.standardFloatingToolbarColors(),
    contentPadding: PaddingValues = FloatingToolbarDefaults.ContentPadding,
    scrollBehavior: FloatingToolbarScrollBehavior? = null,
    shape: Shape = FloatingToolbarDefaults.ContainerShape,
    leadingContent: @Composable (RowScope.() -> Unit)? = null,
    trailingContent: @Composable (RowScope.() -> Unit)? = null,
    expandedShadowElevation: Dp = FloatingToolbarDefaults.ContainerExpandedElevation,
    collapsedShadowElevation: Dp = FloatingToolbarDefaults.ContainerCollapsedElevation,
    content: @Composable RowScope.() -> Unit,
)

@Composable
fun HorizontalFloatingToolbar(
    expanded: Boolean,
    floatingActionButton: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    colors: FloatingToolbarColors = FloatingToolbarDefaults.standardFloatingToolbarColors(),
    contentPadding: PaddingValues = FloatingToolbarDefaults.ContentPadding,
    scrollBehavior: FloatingToolbarScrollBehavior? = null,
    shape: Shape = FloatingToolbarDefaults.ContainerShape,
    floatingActionButtonPosition: FloatingToolbarHorizontalFabPosition = FloatingToolbarHorizontalFabPosition.End,
    animationSpec: FiniteAnimationSpec<Float> = FloatingToolbarDefaults.animationSpec(),
    expandedShadowElevation: Dp = FloatingToolbarDefaults.ContainerExpandedElevationWithFab,
    collapsedShadowElevation: Dp = FloatingToolbarDefaults.ContainerCollapsedElevationWithFab,
    content: @Composable RowScope.() -> Unit,
)

@Composable
fun VerticalFloatingToolbar(
    expanded: Boolean,
    modifier: Modifier = Modifier,
    colors: FloatingToolbarColors = FloatingToolbarDefaults.standardFloatingToolbarColors(),
    contentPadding: PaddingValues = FloatingToolbarDefaults.ContentPadding,
    scrollBehavior: FloatingToolbarScrollBehavior? = null,
    shape: Shape = FloatingToolbarDefaults.ContainerShape,
    leadingContent: @Composable (ColumnScope.() -> Unit)? = null,
    trailingContent: @Composable (ColumnScope.() -> Unit)? = null,
    expandedShadowElevation: Dp = FloatingToolbarDefaults.ContainerExpandedElevation,
    collapsedShadowElevation: Dp = FloatingToolbarDefaults.ContainerCollapsedElevation,
    content: @Composable ColumnScope.() -> Unit,
)

@Composable
fun VerticalFloatingToolbar(
    expanded: Boolean,
    floatingActionButton: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    colors: FloatingToolbarColors = FloatingToolbarDefaults.standardFloatingToolbarColors(),
    contentPadding: PaddingValues = FloatingToolbarDefaults.ContentPadding,
    scrollBehavior: FloatingToolbarScrollBehavior? = null,
    shape: Shape = FloatingToolbarDefaults.ContainerShape,
    floatingActionButtonPosition: FloatingToolbarVerticalFabPosition =
        FloatingToolbarVerticalFabPosition.Bottom,
    animationSpec: FiniteAnimationSpec<Float> = FloatingToolbarDefaults.animationSpec(),
    expandedShadowElevation: Dp = FloatingToolbarDefaults.ContainerExpandedElevationWithFab,
    collapsedShadowElevation: Dp = FloatingToolbarDefaults.ContainerCollapsedElevationWithFab,
    content: @Composable ColumnScope.() -> Unit,
)
```

KDoc: horizontal = *"displays navigation and key actions in a `Row`"*; vertical = *"…in a
`Column`"*.

### The three-slot form

`leadingContent` / `content` / `trailingContent`. **`leadingContent` and `trailingContent` are shown
only when `expanded == true`** — that is the collapse behaviour. `content` is always visible. So:
put the one indispensable action in `content` and the secondary ones in the leading/trailing slots.

### Accessibility behaviour baked in

The toolbar **stays expanded and `scrollBehavior` is disabled when accessibility services are
active.** Do not fight it, and do not build your own collapse on top that ignores the setting.

## `FloatingToolbarDefaults`

`[ANDROIDX]` — names verified, literal values `[UNVERIFIED]`.

Properties:

```kotlin
FloatingToolbarDefaults.ContainerSize
FloatingToolbarDefaults.ContainerShape
FloatingToolbarDefaults.ContentPadding          // "when content are default size (24dp) icons"
FloatingToolbarDefaults.ScreenOffset            // the standard gap from the screen edge
FloatingToolbarDefaults.ScrollDistanceThreshold
FloatingToolbarDefaults.ContainerExpandedElevation
FloatingToolbarDefaults.ContainerCollapsedElevation
FloatingToolbarDefaults.ContainerExpandedElevationWithFab
FloatingToolbarDefaults.ContainerCollapsedElevationWithFab
```

Functions:

```kotlin
FloatingToolbarDefaults.animationSpec()
FloatingToolbarDefaults.standardFloatingToolbarColors()
FloatingToolbarDefaults.vibrantFloatingToolbarColors()
FloatingToolbarDefaults.exitAlwaysScrollBehavior(exitDirection: FloatingToolbarExitDirection, ...)
FloatingToolbarDefaults.floatingToolbarVerticalNestedScroll(expanded, onExpand, onCollapse)
FloatingToolbarDefaults.StandardFloatingActionButton(...)
FloatingToolbarDefaults.VibrantFloatingActionButton(...)
FloatingToolbarDefaults.horizontalEnterTransition / horizontalExitTransition
FloatingToolbarDefaults.verticalEnterTransition / verticalExitTransition
```

Enums: `FloatingToolbarHorizontalFabPosition` (`Start`, `End`),
`FloatingToolbarVerticalFabPosition` (`Top`, `Bottom`), `FloatingToolbarExitDirection`
(`Bottom`, `Top`, `Start`, `End` — corpus-verified: `Bottom`).

**standard vs vibrant**: both color schemes are available on both toolbar types. `[GOOGLE]`
`vibrant` is the higher-chroma treatment — use it when the toolbar *is* the focal navigation
surface (Tomato, §5), `standard` when it floats over content it must not compete with (Med, §6).

## When to use which

`[GOOGLE — DockedFloatingToolbars.md]`

**Docked toolbar**
- "Spans the full width of the window. Best used for **global actions that remain the same across
  multiple pages**."
- **Replaces the deprecated bottom app bar.** Google: the bottom app bar "should be replaced with
  the docked toolbar, which is very similar and more flexible."
- Shorter height than the bottom app bar; more layout and element options.

**Floating toolbar**
- "Floats above the body content. Best used for **contextual actions relevant to the body content or
  the specific page**."
- Horizontal or vertical; vertical suits larger screens.
- Holds "greater amounts of actions" than a docked toolbar.
- Can be paired with a FAB (the `floatingActionButton` overload).
- **Must not exceed the edge of the window or pane.** Apps with many actions should use **overflow
  menus**.

Material's own cited examples: Google Photos album actions (share/add/edit) as a floating toolbar;
Fitbit using one as tab navigation (Day/Week/Month/Year).

---

# 4. Toolbar vs bottom app bar vs navigation bar

Three rules, and they are not negotiable:

1. **The docked toolbar replaces the deprecated bottom app bar.** `[GOOGLE]` If you are writing new
   code, do not reach for `BottomAppBar`. `FlexibleBottomAppBar` exists for migration, but the
   guidance is to move to a toolbar.
2. **Never show a bottom app bar and a toolbar at the same time.** They occupy the same slot and the
   same conceptual role.
3. **Never pair a toolbar with a navigation bar on the same page.** `[GOOGLE, via 9to5google]`
   > "Show the navigation bar on primary pages, and toolbars on subsequent pages with actions."

   A page is either a primary destination (nav bar) or an action page (toolbar). If you find
   yourself needing both, the page is doing two jobs.

**Legitimate exception**: using `HorizontalFloatingToolbar` **as** the app's bottom navigation
instead of a nav bar. That is one surface, not two — Tomato ships exactly this (§5). It is a
deliberate choice, and once you make it you do not additionally add a `ShortNavigationBar`.

Anti-patterns:

| Don't | Instead |
| --- | --- |
| Keep using `BottomAppBar` | Replace with a docked toolbar |
| Show a nav bar and a toolbar on the same page | Nav bar on primary pages, toolbars on action pages |
| Let a floating toolbar run past the pane edge | Use an overflow menu |
| Put 8 icon buttons in a floating toolbar | Overflow; the toolbar "shouldn't exceed the edge of the window or pane" |
| Build your own collapse-on-scroll that ignores a11y services | Use `scrollBehavior` / `floatingToolbarVerticalNestedScroll` — they honour the setting |

---

# 5. `HorizontalFloatingToolbar` as bottom navigation — Tomato

The standout pattern in the corpus. Tomato replaces the bottom navigation bar entirely with a
vibrant `HorizontalFloatingToolbar` full of `ToggleButton`s. Colors animate between primary and
tertiary as the timer flips focus↔break, labels expand/collapse per selection (or always, at
medium+ widths), and the toolbar hides on scroll via `exitAlwaysScrollBehavior`.

`[CORPUS Tomato]` `androidApp/src/main/java/org/nsh07/pomodoro/ui/AppScreen.kt` (imports 45-64,
body 130-290):

```kotlin
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FloatingToolbarDefaults
import androidx.compose.material3.FloatingToolbarDefaults.ScreenOffset
import androidx.compose.material3.FloatingToolbarExitDirection
import androidx.compose.material3.HorizontalFloatingToolbar
import androidx.compose.material3.MaterialTheme.motionScheme
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.ToggleButton
import androidx.compose.material3.ToggleButtonDefaults
import androidx.compose.material3.ToggleButtonShapes
import androidx.compose.material3.TooltipAnchorPosition
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.rememberTooltipState
```

```kotlin
val backStack = rememberNavBackStack(Screen.Timer)
val toolbarScrollBehavior = FloatingToolbarDefaults.exitAlwaysScrollBehavior(
    FloatingToolbarExitDirection.Bottom
)
```

```kotlin
Scaffold(
    bottomBar = {
        AnimatedVisibility(
            backStack.last() !is Screen.AOD,
            enter = slideInVertically(motionScheme.slowSpatialSpec()) { it },
            exit = slideOutVertically(motionScheme.slowSpatialSpec()) { it }
        ) {
            val wide = remember {
                windowSizeClass.isWidthAtLeastBreakpoint(
                    WindowSizeClass.WIDTH_DP_MEDIUM_LOWER_BOUND
                )
            }

            val primary by animateColorAsState(
                if (uiState.timerMode == TimerMode.FOCUS) colorScheme.primary else colorScheme.tertiary
            )
            val onPrimary by animateColorAsState(
                if (uiState.timerMode == TimerMode.FOCUS) colorScheme.onPrimary else colorScheme.onTertiary
            )
            val primaryContainer by animateColorAsState(
                if (uiState.timerMode == TimerMode.FOCUS) colorScheme.primaryContainer else colorScheme.tertiaryContainer
            )
            val onPrimaryContainer by animateColorAsState(
                if (uiState.timerMode == TimerMode.FOCUS) colorScheme.onPrimaryContainer else colorScheme.onTertiaryContainer
            )

            Box(
                Modifier
                    .fillMaxWidth()
                    .padding(
                        start = cutoutInsets.calculateStartPadding(layoutDirection),
                        end = cutoutInsets.calculateEndPadding(layoutDirection)
                    ),
                Alignment.Center
            ) {
                HorizontalFloatingToolbar(
                    expanded = true,
                    scrollBehavior = toolbarScrollBehavior,
                    colors = FloatingToolbarDefaults.vibrantFloatingToolbarColors(
                        toolbarContainerColor = primaryContainer,
                        toolbarContentColor = onPrimaryContainer
                    ),
                    modifier = Modifier
                        .padding(
                            top = ScreenOffset,
                            bottom = systemBarsInsets.calculateBottomPadding()
                                    + ScreenOffset
                        )
                        .zIndex(1f)
                ) {
                    mainScreens.fastForEach { item ->
                        val selected by remember { derivedStateOf { backStack.lastOrNull() == item.route } }
                        TooltipBox(
                            positionProvider =
                                TooltipDefaults.rememberTooltipPositionProvider(
                                    TooltipAnchorPosition.Above
                                ),
                            tooltip = { PlainTooltip { Text(stringResource(item.label)) } },
                            state = rememberTooltipState()
                        ) {
                            ToggleButton(
                                checked = selected,
                                onCheckedChange = if (!selected) {
                                    {
                                        if (item.route != Screen.Timer) { // Ensure the backstack does not accumulate screens
                                            if (backStack.size < 2) backStack.add(item.route)
                                            else backStack[1] = item.route
                                        } else {
                                            if (backStack.size > 1) backStack.removeAt(1)
                                        }
                                    }
                                } else {
                                    { item.onNavigateHome() }
                                },
                                colors = ToggleButtonDefaults.colors(
                                    containerColor = primaryContainer,
                                    contentColor = onPrimaryContainer,
                                    checkedContainerColor = primary,
                                    checkedContentColor = onPrimary
                                ),
                                shapes = ToggleButtonShapes(   // alpha25+; corpus wrote ToggleButtonDefaults.shapes(...), now HIDDEN
                                    CircleShape,
                                    CircleShape,
                                    CircleShape
                                ),
                                modifier = Modifier.height(56.dp)
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Crossfade(selected) {
                                        if (it) Icon(
                                            painterResource(item.selectedIcon),
                                            stringResource(item.label)
                                        )
                                        else Icon(
                                            painterResource(item.unselectedIcon),
                                            stringResource(item.label)
                                        )
                                    }
                                    AnimatedVisibility(
                                        visible = selected || wide,
                                        enter = expandHorizontally(motionScheme.defaultSpatialSpec()),
                                        exit = shrinkHorizontally(motionScheme.defaultSpatialSpec())
                                    ) {
                                        Text(
                                            text = stringResource(item.label),
                                            fontSize = 16.sp,
                                            lineHeight = 24.sp,
                                            maxLines = 1,
                                            softWrap = false,
                                            overflow = TextOverflow.Clip,
                                            modifier = Modifier.padding(start = ButtonDefaults.IconSpacing)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    },
    modifier = modifier
) { contentPadding ->
```

### What to take from it

- **`expanded = true` fixed, collapse driven by `scrollBehavior`.** The toolbar's own expand/collapse
  slots aren't used at all — `exitAlwaysScrollBehavior(FloatingToolbarExitDirection.Bottom)` slides
  the whole surface off the bottom on scroll.
- **`Box(Modifier.fillMaxWidth(), Alignment.Center)` around it.** The toolbar sizes to its content;
  the Box centres it horizontally. Without this it pins to the start.
- **Cutout insets on the Box, system-bar insets on the toolbar's padding.** Two different inset
  sources for two different purposes — see §8.
- **`.zIndex(1f)`** so it draws above scrolled content.
- **`ToggleButtonShapes(CircleShape, CircleShape, CircleShape)`** — force a pill in all
  three states so items don't morph inside the pill container. (The corpus source writes
  `ToggleButtonDefaults.shapes(...)`; that overload is `DeprecationLevel.HIDDEN` on 1.5.0-alpha25+, so
  use the `ToggleButtonShapes` constructor.)
- **Selected label expands, unselected collapses** — `AnimatedVisibility(visible = selected || wide)`
  with expand/shrink horizontally on a spatial spec. At medium+ widths everything is labelled.
- **Each item is a `TooltipBox`** with `TooltipAnchorPosition.Above` — icon-only items at compact
  width still get a name on long-press.

---

# 6. Leading/trailing edit-mode toolbar — Med

The three-slot API and the negative-offset idiom.

`[CORPUS Med]` `app/src/main/kotlin/com/fedeveloper95/med/EditModeActivity.kt` (imports 57-60,
usage 486-515):

```kotlin
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FloatingToolbarDefaults
import androidx.compose.material3.FloatingToolbarDefaults.floatingToolbarVerticalNestedScroll
import androidx.compose.material3.HorizontalFloatingToolbar
```

```kotlin
if (editItems.isNotEmpty()) {
    HorizontalFloatingToolbar(
        modifier = Modifier
            .align(Alignment.BottomCenter)
            .padding(bottom = 32.dp)
            .offset(y = -FloatingToolbarDefaults.ScreenOffset)
            .zIndex(1f),
        expanded = toolbarExpanded,
        leadingContent = {
            IconButton(onClick = { saveOrder() }) {
                Icon(
                    Icons.Rounded.Check,
                    contentDescription = stringResource(R.string.save_action)
                )
            }
        },
        trailingContent = {
            IconButton(onClick = { isDeleteMode = !isDeleteMode }) {
                val icon = if (isDeleteMode) Icons.Rounded.Close else Icons.Rounded.Delete
                Icon(icon, contentDescription = stringResource(R.string.delete_mode_action))
            }
        },
        content = {
            FilledIconButton(
                modifier = Modifier.width(64.dp),
                onClick = { showGroupNameSheet = true }
            ) {
                Icon(Icons.Rounded.Add, contentDescription = null)
            }
        }
    )
}
```

### What to take from it

- **The primary action lives in `content`** (the `FilledIconButton` "add"), because `content` is the
  only slot that survives collapse. Save and delete-mode go in leading/trailing and disappear when
  `toolbarExpanded` is false.
- **`.offset(y = -FloatingToolbarDefaults.ScreenOffset)`** — the toolbar is aligned
  `BottomCenter` inside a `Box` and then lifted by the standard screen offset. Negative offset,
  because `align` puts it flush against the edge.
- **`if (editItems.isNotEmpty())`** — the toolbar only exists in edit mode. Contextual toolbars
  should be conditionally composed, not permanently present and disabled.
- The `content` slot's `FilledIconButton` gets `Modifier.width(64.dp)` — wider than tall, which is
  the Expressive "Wide" icon-button proportion done by hand.

---

# 7. `floatingToolbarVerticalNestedScroll` and `ScreenOffset` standalone — LastChat

LastChat never renders a `HorizontalFloatingToolbar`, but it uses two `FloatingToolbarDefaults`
helpers on their own. This is a legitimate lightweight way to get the expressive scroll behaviour
without the toolbar container.

`[CORPUS LastChat]`
`app/src/main/java/me/rerere/rikkahub/ui/pages/setting/SettingTTSProviderDetailPage.kt`
(imports 48-49, usage 322-332):

```kotlin
import androidx.compose.material3.FloatingToolbarDefaults.ScreenOffset
import androidx.compose.material3.FloatingToolbarDefaults.floatingToolbarVerticalNestedScroll
```

```kotlin
LazyColumn(
    state = lazyListState,
    modifier = Modifier
        .fillMaxSize()
        .floatingToolbarVerticalNestedScroll(
            expanded = expanded,
            onExpand = { expanded = true },
            onCollapse = { expanded = false },
        ),
    contentPadding = contentPadding + PaddingValues(horizontal = 16.dp, vertical = 8.dp) + PaddingValues(bottom = 60.dp),
    horizontalAlignment = Alignment.CenterHorizontally,
    verticalArrangement = Arrangement.spacedBy(4.dp),
) {
```

`ScreenOffset` used as a bare constant to lift stacked FABs off the screen edge —
`[CORPUS LastChat]` `.../SettingProviderDetailPage.kt` (state 769, modifier 876-880, offset
1024-1032):

```kotlin
var expanded by rememberSaveable { mutableStateOf(true) }
// ...
    .floatingToolbarVerticalNestedScroll(
        expanded = expanded,
        onExpand = { expanded = true },
        onCollapse = { expanded = false },
    )
// ...
// Stacked FABs for adding models
Column(
    modifier = Modifier
        .align(Alignment.BottomEnd)
        .padding(16.dp)
        .offset(y = -ScreenOffset),
    verticalArrangement = Arrangement.spacedBy(12.dp),
    horizontalAlignment = Alignment.CenterHorizontally
) {
```

`ScreenOffset` is also imported in `SettingLorebooksPage.kt:41` and `SettingSkillsPage.kt:42`.

**Pitfalls**

- `floatingToolbarVerticalNestedScroll` is a **modifier on the scrollable**, not on the toolbar. It
  reports expand/collapse through callbacks; you own the state.
- It is a **different mechanism** from `scrollBehavior`. `scrollBehavior` +
  `exitAlwaysScrollBehavior` *translates the toolbar off screen*; `floatingToolbarVerticalNestedScroll`
  *flips the expanded flag* so leading/trailing content collapses in place. Pick one per toolbar.
- Use `rememberSaveable` for the `expanded` flag so it survives rotation.
- Those stacked FABs in `SettingProviderDetailPage` are exactly what a FAB menu is supposed to
  replace (§2). Copy the `ScreenOffset` idiom, not the stacked-FAB layout.

---

# 8. Positioning, insets and Scaffold interaction

Floating toolbars are not laid out by `Scaffold`. You place them, so you own the insets.

## Three placement strategies from the corpus

| Strategy | Code | When |
| --- | --- | --- |
| In `Scaffold`'s `bottomBar` | Tomato §5 | The toolbar **is** the app's bottom navigation. `Scaffold` then reserves space for it in `contentPadding`. |
| Aligned in a `Box` over content | Med §6 | Contextual toolbar. `Scaffold` reserves nothing; you must pad the content yourself. |
| Nothing rendered, helpers used standalone | LastChat §7 | Just borrowing the scroll behaviour. |

## The offset rules

- **`FloatingToolbarDefaults.ScreenOffset`** is the standard gap between the toolbar and the screen
  edge. Use it — do not invent a literal.
- When the toolbar is inside a `Scaffold` slot, **add** `ScreenOffset` as padding:
  ```kotlin
  .padding(
      top = ScreenOffset,
      bottom = systemBarsInsets.calculateBottomPadding() + ScreenOffset
  )
  ```
- When the toolbar is `align`ed against a `Box` edge, **subtract** it as an offset:
  ```kotlin
  .align(Alignment.BottomCenter)
      .padding(bottom = 32.dp)
      .offset(y = -FloatingToolbarDefaults.ScreenOffset)
  ```

## Insets, edge-to-edge

Two different inset sources do two different jobs — Tomato uses both in the same block:

```kotlin
// cutout insets → horizontal padding on the wrapper Box, so the toolbar
// never slides under a display cutout in landscape
Box(
    Modifier
        .fillMaxWidth()
        .padding(
            start = cutoutInsets.calculateStartPadding(layoutDirection),
            end = cutoutInsets.calculateEndPadding(layoutDirection)
        ),
    Alignment.Center
) {
    HorizontalFloatingToolbar(
        // system-bar insets → bottom padding on the toolbar itself,
        // so it clears the gesture nav bar
        modifier = Modifier.padding(
            top = ScreenOffset,
            bottom = systemBarsInsets.calculateBottomPadding() + ScreenOffset
        ).zIndex(1f)
    ) { /* ... */ }
}
```

## Content padding

- If the toolbar is in `Scaffold.bottomBar`, the `contentPadding` lambda parameter already accounts
  for it. Apply it to your list.
- If the toolbar floats over the content in a `Box`, `Scaffold` knows nothing about it. Add manual
  bottom padding to the scrollable so the last item isn't permanently hidden. LastChat's
  `+ PaddingValues(bottom = 60.dp)` is exactly this.
- **`.zIndex(1f)`** on the toolbar in both cases. Without it, elevated content (cards, sticky
  headers) can draw over it.

## Vertical toolbars

`VerticalFloatingToolbar` is the same API with `ColumnScope` slots and
`FloatingToolbarVerticalFabPosition` (`Top` / `Bottom`). Guidance: vertical placement suits **larger
screens**. On a compact phone a vertical toolbar eats horizontal content width; use the horizontal
form. No corpus app ships a vertical toolbar — treat any vertical-specific layout advice here as
`[UNVERIFIED]` and check it visually.

## Final checklist

- Toolbar and navigation bar are never both on screen.
- Toolbar never exceeds the pane edge; overflow beyond ~5 actions.
- `scrollBehavior` **or** `floatingToolbarVerticalNestedScroll`, not both.
- Insets: cutout horizontally, system bars vertically.
- `ScreenOffset` used, sign correct for your placement strategy.
- With TalkBack on, the toolbar stays expanded — verify the layout still works at full width.

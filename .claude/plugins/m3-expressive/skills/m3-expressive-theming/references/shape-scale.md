# Shape Scale — M3 Expressive rounded-rectangle system

**Scope boundary — read this first.** This file covers the **rounded-rectangle corner scale**: the
eight `Shapes` steps, `ShapeDefaults`, `ShapeTokens`, per-component shape defaults, and building a
custom `Shapes` for `MaterialExpressiveTheme`.

Polygon shapes — `MaterialShapes` (the ~35 named cookies/clovers/bursts), `RoundedPolygon`,
`Morph`, `toShape()`, `androidx.graphics:graphics-shapes` — are a **different system** and live in
the **`m3-expressive-shapes`** skill. If the task is "make this avatar a 7-sided cookie" or "morph
the shape on press", go there. If the task is "what corner radius should this card have", stay here.

---

## 1. The eight-step scale

Source: `androidx-main` `compose/material3/.../tokens/ShapeTokens.kt`, exact dp.

| Token | Value | Expressive addition? |
| --- | --- | --- |
| `CornerNone` | `RectangleShape` (0dp) | no |
| `CornerExtraSmall` | `RoundedCornerShape(4.0.dp)` | no |
| `CornerSmall` | `RoundedCornerShape(8.0.dp)` | no |
| `CornerMedium` | `RoundedCornerShape(12.0.dp)` | no |
| `CornerLarge` | `RoundedCornerShape(16.0.dp)` | no |
| **`CornerLargeIncreased`** | **`RoundedCornerShape(20.0.dp)`** | **YES** |
| `CornerExtraLarge` | `RoundedCornerShape(28.0.dp)` | no |
| **`CornerExtraLargeIncreased`** | **`RoundedCornerShape(32.0.dp)`** | **YES** |
| **`CornerExtraExtraLarge`** | **`RoundedCornerShape(48.0.dp)`** | **YES** |
| `CornerFull` | `CircleShape` (50%) | no |

The eight *steps* referenced by the `Shapes` class are **4 / 8 / 12 / 16 / 20 / 28 / 32 / 48 dp**.
`None` and `Full` are token constants but not `Shapes` properties.

**The three Expressive additions are `LargeIncreased` (20dp), `ExtraLargeIncreased` (32dp) and
`ExtraExtraLarge` (48dp).** Baseline M3 stopped at `ExtraLarge` (28dp). Design intent: "each step
provides meaningful visual difference" — the new steps both extend the scale upward and fill the
16→28 gap.

Corner families remain **rounded** and **cut**, defaulting to rounded.

### 1.1 Directional variants

```kotlin
CornerExtraSmallTop  = RoundedCornerShape(topStart = 4.0.dp,  topEnd = 4.0.dp,  bottomEnd = 0.0.dp, bottomStart = 0.0.dp)
CornerLargeTop       = RoundedCornerShape(topStart = 16.0.dp, topEnd = 16.0.dp, bottomEnd = 0.0.dp, bottomStart = 0.0.dp)
CornerExtraLargeTop  = RoundedCornerShape(topStart = 28.0.dp, topEnd = 28.0.dp, bottomEnd = 0.0.dp, bottomStart = 0.0.dp)
CornerLargeStart     = RoundedCornerShape(topStart = 16.0.dp, topEnd = 0.0.dp,  bottomEnd = 0.0.dp, bottomStart = 16.0.dp)
CornerLargeEnd       = RoundedCornerShape(topStart = 0.0.dp,  topEnd = 16.0.dp, bottomEnd = 16.0.dp, bottomStart = 0.0.dp)
```

`*Top` is what bottom sheets and docked surfaces use. `*Start` / `*End` are for panes and segmented
horizontal groups.

### 1.2 `CornerSize` values

For composing partial shapes (one corner from token A, another from token B):

```kotlin
CornerValueNone                = CornerSize(0.0.dp)
CornerValueExtraSmall          = CornerSize(4.0.dp)
CornerValueSmall               = CornerSize(8.0.dp)
CornerValueMedium              = CornerSize(12.0.dp)
CornerValueLarge               = CornerSize(16.0.dp)
CornerValueLargeIncreased      = CornerSize(20.0.dp)
CornerValueExtraLarge          = CornerSize(28.0.dp)
CornerValueExtraLargeIncreased = CornerSize(32.0.dp)
CornerValueExtraExtraLarge     = CornerSize(48.0.dp)
```

`ShapeDefaults.CornerFull` is also a `CornerSize` (used widely in component code, e.g.
`ButtonGroupDefaults`, `SplitButtonDefaults.OuterCornerSize`).

---

## 2. The `Shapes` class

Source: `androidx-main` `compose/material3/.../Shapes.kt`.

```kotlin
class Shapes(
    val extraSmall: CornerBasedShape = ShapeDefaults.ExtraSmall,
    val small: CornerBasedShape = ShapeDefaults.Small,
    val medium: CornerBasedShape = ShapeDefaults.Medium,
    val large: CornerBasedShape = ShapeDefaults.Large,
    val extraLarge: CornerBasedShape = ShapeDefaults.ExtraLarge,
    largeIncreased: CornerBasedShape = ShapeDefaults.LargeIncreased,
    extraLargeIncreased: CornerBasedShape = ShapeDefaults.ExtraLargeIncreased,
    extraExtraLarge: CornerBasedShape = ShapeDefaults.ExtraExtraLarge,
)
```

| Property | Default | dp | Read as |
| --- | --- | --- | --- |
| `extraSmall` | `ShapeDefaults.ExtraSmall` | 4 | `MaterialTheme.shapes.extraSmall` |
| `small` | `ShapeDefaults.Small` | 8 | `MaterialTheme.shapes.small` |
| `medium` | `ShapeDefaults.Medium` | 12 | `MaterialTheme.shapes.medium` |
| `large` | `ShapeDefaults.Large` | 16 | `MaterialTheme.shapes.large` |
| `largeIncreased` | `ShapeDefaults.LargeIncreased` | 20 | `MaterialTheme.shapes.largeIncreased` |
| `extraLarge` | `ShapeDefaults.ExtraLarge` | 28 | `MaterialTheme.shapes.extraLarge` |
| `extraLargeIncreased` | `ShapeDefaults.ExtraLargeIncreased` | 32 | `MaterialTheme.shapes.extraLargeIncreased` |
| `extraExtraLarge` | `ShapeDefaults.ExtraExtraLarge` | 48 | `MaterialTheme.shapes.extraExtraLarge` |

Notes:
- **The last three are not `val` in the primary constructor signature** — they are exposed as
  properties separately. You can still pass them by name to the constructor and read them off
  `MaterialTheme.shapes`.
- Declaration order is not scale order: `largeIncreased` (20dp) is declared *after* `extraLarge`
  (28dp) in the constructor. Always pass named arguments.
- A **secondary constructor** takes only the first five params and defaults the rest — the
  binary-compat shim for the pre-Expressive five-step scale. `Shapes(extraSmall, small, medium,
  large, extraLarge)` still compiles and silently keeps Expressive defaults for the other three.
- `Shapes.kt` also defines a `Shapes.fromToken()` extension and internal `top()` / `bottom()` /
  `start()` / `end()` helpers.
- **There is no `toShape()` in `Shapes.kt`** — that lives in `MaterialShapes.kt` and belongs to the
  polygon system (`m3-expressive-shapes` skill).

Accessing shapes on 1.5.0-alpha15+: theme values come through one composition local
(`MaterialTheme.LocalMaterialTheme.current`). `MaterialTheme.shapes` still works and is what you
should write.

---

## 3. `ShapeDefaults`

Source: `androidx-main` `compose/material3/.../Shapes.kt`.

```kotlin
val ExtraSmall: CornerBasedShape          = ShapeTokens.CornerExtraSmall            // 4dp
val Small: CornerBasedShape               = ShapeTokens.CornerSmall                 // 8dp
val Medium: CornerBasedShape              = ShapeTokens.CornerMedium                // 12dp
val Large: CornerBasedShape               = ShapeTokens.CornerLarge                 // 16dp
val LargeIncreased: CornerBasedShape      = ShapeTokens.CornerLargeIncreased        // 20dp
val ExtraLarge: CornerBasedShape          = ShapeTokens.CornerExtraLarge            // 28dp
val ExtraLargeIncreased: CornerBasedShape = ShapeTokens.CornerExtraLargeIncreased   // 32dp
val ExtraExtraLarge: CornerBasedShape     = ShapeTokens.CornerExtraExtraLarge       // 48dp
```

Plus `ShapeDefaults.CornerFull` — a **`CornerSize`**, not a `Shape`. Used to build pill ends:

```kotlin
RoundedCornerShape(
    topStart = ShapeDefaults.CornerFull,
    bottomStart = ShapeDefaults.CornerFull,
    topEnd = someInnerCornerSize,
    bottomEnd = someInnerCornerSize,
)
```

**`ShapeDefaults` vs `MaterialTheme.shapes`:**

| Use | When |
| --- | --- |
| `MaterialTheme.shapes.large` | Almost always. Respects the app's `Shapes` override; themeable. |
| `ShapeDefaults.Large` | Non-composable context, or you deliberately want the Material default regardless of the app theme. Not `@Composable`, so usable in plain functions and object initializers. |

vivi-music uses `ShapeDefaults.Large` directly in a `Modifier.clip()` inside a grid-menu item —
legitimate, but it means a themed `Shapes` override would not reach that component.

---

## 4. Which components use which token

Verified from source/API signatures:

| Component / default | Shape source |
| --- | --- |
| `ButtonDefaults.shapes()` | `MaterialTheme.shapes.defaultButtonShapes` |
| `ButtonGroupDefaults.connectedMiddleButtonShapes(shape = …)` | `ShapeDefaults.Small` (8dp) |
| `ButtonGroupDefaults.connectedLeadingButtonShape` / `connectedTrailingButtonShape` | `ShapeDefaults.CornerFull` on the outer corners + `ConnectedButtonGroupSmallTokens.InnerCornerCornerSize` on the inner corners |
| `ButtonGroupDefaults.connectedButtonCheckedShape` | `ShapeTokens.CornerFull` |
| `ButtonGroupDefaults.OverflowIndicator(shape = …)` | `IconButtonDefaults.filledShape` |
| `SplitButtonDefaults.OuterCornerSize` | `ShapeDefaults.CornerFull` |
| `MediumFloatingActionButton(shape = …)` | `FloatingActionButtonDefaults.mediumShape` |
| `LargeFloatingActionButton(shape = …)` | `FloatingActionButtonDefaults.largeShape` |
| `HorizontalFloatingToolbar` / `VerticalFloatingToolbar` (all 4 overloads) | `FloatingToolbarDefaults.ContainerShape` |
| `WideNavigationRail(shape = …)` | `WideNavigationRailDefaults.shape` |
| `ModalWideNavigationRail(collapsedShape/expandedShape = …)` | `WideNavigationRailDefaults.modalCollapsedShape` / `.modalExpandedShape` |
| `ContainedLoadingIndicator(containerShape = …)` | `LoadingIndicatorDefaults.containerShape` (→ `LoadingIndicatorTokens.ContainerShape`) |
| `SearchBar(collapsedShape = …)` | `SearchBarDefaults.inputFieldShape` |
| `ListItem(shapes = …)` | `ListItemDefaults.shapes()` → `ListItemShapes` |
| Buttons: `ButtonDefaults.squareShape`, `.pressedShape`, `.extraSmallPressedShape`, `.mediumPressedShape`, `.largePressedShape`, `.extraLargePressedShape`, `.shape`, `.elevatedShape`, `.filledTonalShape`, `.outlinedShape`, `.textShape` | per-size shape constants |

The literal dp behind `*Tokens.*Shape` entries (`LoadingIndicatorTokens.ContainerShape`,
`FloatingToolbarDefaults.ContainerShape`, `WideNavigationRailDefaults.shape`,
`FloatingActionButtonDefaults.mediumShape` / `largeShape`) is **UNVERIFIED**. The classic M3
component→token mapping (Card → `medium`, Dialog → `extraLarge`, TextField → `extraSmall` top,
Chip → `small`, small FAB → `large`) is **not re-verified for Expressive** in these sources —
do not quote specific dp for a component you have not checked.

**The reliable rule:** components read their shape from `MaterialTheme.shapes` through their
`*Defaults`. Override the `Shapes` on the theme and every component follows. Override a `shape =`
argument at a call site and only that one changes.

### 4.1 Shape-by-interaction-state (Expressive's signature move)

Expressive components take a **shapes bundle**, not one shape, so they can morph on press/check:

```kotlin
// ButtonDefaults
@Composable fun shapes(shape: Shape? = null, pressedShape: Shape? = null): ButtonShapes

// ToggleButtonShapes carries shape / pressedShape / checkedShape
ButtonGroupDefaults.connectedLeadingButtonShapes(
    shape = connectedLeadingButtonShape,
    pressedShape = connectedLeadingButtonPressShape,
    checkedShape = connectedButtonCheckedShape,
)
```

Real example — Tomato's segmented list items, using `extraLargeIncreased` (32dp) as the pressed and
selected shape against an 8dp/16dp resting shape:

Source: `/root/work/repos/Tomato/shared/src/commonMain/kotlin/org/nsh07/pomodoro/ui/theme/Shape.kt`

```kotlin
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun segmentedListItemShapes(
    index: Int,
    count: Int,
    singleElement: Boolean = count == 1
): ListItemShapes =
    ListItemDefaults.segmentedShapes(
        index,
        count,
        ListItemDefaults.shapes(
            shape = if (singleElement) shapes.large else shapes.extraSmall,
            selectedShape = shapes.extraLargeIncreased,
            pressedShape = shapes.extraLargeIncreased,
            focusedShape = shapes.large,
            hoveredShape = shapes.extraLarge,
            draggedShape = shapes.extraLargeIncreased
        )
    )
```

That is the correct use of the increased steps: as the **destination** of a state morph, not as a
resting value. Resting 4-8dp → pressed 32dp is a large, legible jump.

---

## 5. Building a custom `Shapes`

```kotlin
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

val AppShapes = Shapes(
    extraSmall         = RoundedCornerShape(4.dp),
    small              = RoundedCornerShape(8.dp),
    medium             = RoundedCornerShape(12.dp),
    large              = RoundedCornerShape(16.dp),
    largeIncreased     = RoundedCornerShape(20.dp),
    extraLarge         = RoundedCornerShape(28.dp),
    extraLargeIncreased = RoundedCornerShape(32.dp),
    extraExtraLarge    = RoundedCornerShape(48.dp),
)

MaterialExpressiveTheme(
    colorScheme = colorScheme,
    typography = AppTypography,
    shapes = AppShapes,
    motionScheme = MotionScheme.expressive(),
    content = content,
)
```

Rules:
1. **Pass named arguments.** Constructor order is not scale order.
2. **Do not skip the theme.** If you leave `shapes = null` on `MaterialExpressiveTheme`, you get the
   Expressive defaults — which is fine, and better than hardcoding radii at call sites.
3. **Keep the scale monotonic.** If `small` > `medium`, components that assume ordering (segmented
   groups, connected buttons) produce visually broken corners.
4. **Only override what you mean.** A brand that wants sharper corners should shift the *whole* scale,
   not one step. Changing only `large` desynchronizes every component that pairs `large` with a
   neighbouring token.
5. `Shapes(extraSmall, small, medium, large, extraLarge)` (five args, secondary constructor) is
   **valid but incomplete** — the three Expressive steps stay at defaults and will not match a
   scaled-down brand scale. That is exactly what LastChat does (§6); note it as a limitation.

### 5.1 Segmented / grouped-list corners

The single most-used Expressive shape idiom: first item gets large top corners, middle items get tiny
corners all round, last item gets large bottom corners.

Source: `/root/work/repos/vivi-music/app/src/main/kotlin/com/music/vivi/utils/ShapesCurve.kt`
(complete file)

```kotlin
package com.music.vivi.utils

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ListItemColors
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp

private const val ConnectedCornerRadius = 4
private const val EndCornerRadius = 16

@Composable
fun listItemColors(): ListItemColors =
    ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)

fun leadingItemShape(): RoundedCornerShape = RoundedCornerShape(
    topStart = EndCornerRadius.dp,
    topEnd = EndCornerRadius.dp,
    bottomStart = ConnectedCornerRadius.dp,
    bottomEnd = ConnectedCornerRadius.dp
)

fun middleItemShape(): RoundedCornerShape = RoundedCornerShape(ConnectedCornerRadius.dp)

fun endItemShape(): RoundedCornerShape = RoundedCornerShape(
    topStart = ConnectedCornerRadius.dp,
    topEnd = ConnectedCornerRadius.dp,
    bottomStart = EndCornerRadius.dp,
    bottomEnd = EndCornerRadius.dp
)

fun getGroupedShape(index: Int, count: Int): Shape = when {
    index == 0 -> leadingItemShape()
    index == count - 1 -> endItemShape()
    else -> middleItemShape()
}
```

The theme-token version of the same idea, which is what you should actually write because it survives
a `Shapes` override:

Source: `/root/work/repos/Tomato/shared/src/commonMain/kotlin/org/nsh07/pomodoro/ui/theme/Shape.kt`
(complete file after the license header)

```kotlin
package org.nsh07.pomodoro.ui.theme

import androidx.compose.foundation.shape.CornerBasedShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.ListItemShapes
import androidx.compose.material3.MaterialTheme.shapes
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp

object TomatoShapeDefaults {
    @OptIn(ExperimentalMaterial3ExpressiveApi::class)
    val topListItemShape: RoundedCornerShape
        @Composable get() =
            RoundedCornerShape(
                topStart = shapes.large.topStart,
                topEnd = shapes.large.topEnd,
                bottomStart = shapes.extraSmall.bottomStart,
                bottomEnd = shapes.extraSmall.bottomStart
            )

    val middleListItemShape: RoundedCornerShape
        @Composable get() = RoundedCornerShape(shapes.extraSmall.topStart)

    @OptIn(ExperimentalMaterial3ExpressiveApi::class)
    val bottomListItemShape: RoundedCornerShape
        @Composable get() =
            RoundedCornerShape(
                topStart = shapes.extraSmall.topStart,
                topEnd = shapes.extraSmall.topEnd,
                bottomStart = shapes.large.bottomStart,
                bottomEnd = shapes.large.bottomEnd
            )

    @OptIn(ExperimentalMaterial3ExpressiveApi::class)
    val cardShape: CornerBasedShape
        @Composable get() = shapes.large

    @OptIn(ExperimentalMaterial3ExpressiveApi::class)
    @Composable
    fun segmentedListItemShapes(
        index: Int,
        count: Int,
        singleElement: Boolean = count == 1
    ): ListItemShapes =
        ListItemDefaults.segmentedShapes(
            index,
            count,
            ListItemDefaults.shapes(
                shape = if (singleElement) shapes.large else shapes.extraSmall,
                selectedShape = shapes.extraLargeIncreased,
                pressedShape = shapes.extraLargeIncreased,
                focusedShape = shapes.large,
                hoveredShape = shapes.extraLarge,
                draggedShape = shapes.extraLargeIncreased
            )
        )

    val PANE_MAX_WIDTH = 600.dp
}
```

Note it reads `shapes.large.topStart` (a `CornerSize`) rather than a literal dp — so a theme-level
`Shapes` override propagates. Prefer this form.

`ListItemDefaults.segmentedShapes(index, count, shapes)` is the first-party version of this idiom;
use it when your material3 version has it (Tomato is on Compose BOM alpha `2026.03.00`). Exact member
names of `ListItemShapes` beyond those shown are **UNVERIFIED**.

A small helper for "round only the top" of any `CornerBasedShape`:

Source: `/root/work/repos/vivi-music/app/src/main/kotlin/com/music/vivi/ui/utils/ShapeUtils.kt`

```kotlin
fun CornerBasedShape.top(): CornerBasedShape =
    copy(bottomStart = CornerSize(0.dp), bottomEnd = CornerSize(0.dp))
```

---

## 6. LastChat's hand-authored token set (verbatim)

An example of going well beyond the eight-step scale: a documented shape vocabulary with explicit
optical-roundness math. Copy the *approach* (name shapes by role; compute nested radii), not
necessarily the values.

Source: `/root/work/repos/LastChat/app/src/main/java/me/rerere/rikkahub/ui/theme/Shape.kt`
(complete file, 82 lines)

```kotlin
package me.rerere.rikkahub.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

/**
 * M3 Expressive Shape System
 *
 * Consistent shape tokens for visual rhythm and hierarchy.
 * Shapes guide users' attention and create visual groupings.
 */
object AppShapes {
    // Large containers - cards, sheets, dialogs
    val CardLarge = RoundedCornerShape(28.dp)
    val CardMedium = RoundedCornerShape(24.dp)
    val CardSmall = RoundedCornerShape(16.dp)

    // Buttons and interactive elements
    val ButtonPill = RoundedCornerShape(50)           // Fully rounded pills
    val ButtonRounded = RoundedCornerShape(20.dp)     // Softer buttons
    val ButtonSquared = RoundedCornerShape(12.dp)     // Compact buttons

    // Input fields
    val InputField = RoundedCornerShape(20.dp)        // Match chat message bubble outer radius
    val SearchField = ButtonPill                      // Search and filter bars are fully rounded pills

    // Chips and tags
    val Chip = RoundedCornerShape(12.dp)
    val Tag = RoundedCornerShape(50)                  // Tags are pill-shaped

    // Dialogs and sheets
    val Dialog = RoundedCornerShape(28.dp)
    val BottomSheet = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)

    // Small elements
    val Avatar = RoundedCornerShape(50)               // Circular avatars
    val IconButton = RoundedCornerShape(50)           // Circular icon buttons
    val Indicator = RoundedCornerShape(8.dp)

    // List items
    val ListItem = RoundedCornerShape(16.dp)
    val ListItemFirst = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp)
    val ListItemLast = RoundedCornerShape(bottomStart = 16.dp, bottomEnd = 16.dp)

    // Optical roundness for nested elements inside cards
    // Formula: outer radius - padding = inner radius
    // CardLarge (28dp) with 12dp padding -> 16dp inner
    // CardLarge (28dp) with 8dp padding -> 20dp inner
    val CardLargeInner12 = RoundedCornerShape(16.dp)  // For 12dp padding inside CardLarge
    val CardLargeInner8 = RoundedCornerShape(20.dp)   // For 8dp padding inside CardLarge
    val CardMediumInner12 = RoundedCornerShape(12.dp) // For 12dp padding inside CardMedium
    val CardSmallInner8 = RoundedCornerShape(8.dp)    // For 8dp padding inside CardSmall

    // Optical roundness for elements INSIDE message bubbles
    // Message bubbles use 20dp outer radius with 12dp padding
    // Formula: 20dp - 12dp = 8dp inner radius
    val MessageBubbleInner = RoundedCornerShape(8.dp)  // For code blocks, reasoning cards inside bubbles

    // Message bubbles
    val MessageOutgoing = RoundedCornerShape(
        topStart = 20.dp,
        topEnd = 20.dp,
        bottomStart = 20.dp,
        bottomEnd = 6.dp
    )
    val MessageIncoming = RoundedCornerShape(
        topStart = 20.dp,
        topEnd = 20.dp,
        bottomStart = 6.dp,
        bottomEnd = 20.dp
    )
}

// Material 3 default shapes override
val Shapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(28.dp)
)
```

### 6.1 What is worth stealing

- **The optical-roundness formula: `inner radius = outer radius − padding`.** Concentric rounded
  rectangles look wrong when their radii are equal — the inner corner appears too round. Subtracting
  the padding keeps the two curves parallel. This is the single most useful idea in the file.
- **Shapes named by role, not by size.** `MessageBubbleInner`, `SearchField`, `Avatar` — a call site
  reading `AppShapes.MessageBubbleInner` is self-documenting; `RoundedCornerShape(8.dp)` is not.
- **`RoundedCornerShape(50)`** (percent overload, no `.dp`) for true pills that stay pill-shaped at
  any height. Do not write `RoundedCornerShape(50.dp)` for a pill — that is a fixed 50dp radius and
  breaks on short elements.
- **Asymmetric bubbles**: 20/20/20/6 vs 20/20/6/20 — one small corner as a directional "tail". Cheap,
  effective expressive detail.

### 6.2 What not to copy

- The `Shapes(...)` override uses the **five-arg secondary constructor**, so `largeIncreased` (20),
  `extraLargeIncreased` (32) and `extraExtraLarge` (48) stay at Material defaults while the rest of
  the scale is shifted (8/12/16/24/28). The three Expressive steps are therefore out of proportion
  with the custom scale. If you shift the scale, shift all eight.
- `AppShapes` is a parallel vocabulary that **bypasses `MaterialTheme.shapes`**. That is defensible
  for app-specific shapes (message bubbles), but everything that maps onto a standard token
  (`CardLarge`, `Chip`, `ListItem`) should read the theme instead, or a theme-level shape change will
  not reach it.
- Hardcoded dp inside the object means these values cannot vary by theme. If you need multiple shape
  themes, make them functions of `MaterialTheme.shapes`.

---

## 7. Design guidance

### 7.1 Bigger radii read as more expressive

The Expressive scale extends upward for a reason: larger corner radii read as softer, friendlier and
more "designed". A 48dp radius on a 96dp-tall container is nearly a stadium; that is the look.

But **expression is relational**. Material's tactic 1: *"Break from the surrounding shape style to
draw attention to a particular element."* If everything is 32dp, nothing is emphasized — you have
just built a rounder version of a flat app.

### 7.2 Where to use the increased steps

| Step | Good uses |
| --- | --- |
| `largeIncreased` (20dp) | Buttons that want a softer-than-16 feel; input fields; the "one step up" from a 16dp card in a grouped list. |
| `extraLargeIncreased` (32dp) | The hero card on a screen; the pressed/selected destination of a shape morph (Tomato); an expanded FAB or toolbar container. |
| `extraExtraLarge` (48dp) | Large media containers, full-bleed hero surfaces, an expanded player, a large decorative avatar. Needs real size — 48dp radius on a 64dp element is a blob. |

Also good: the increased steps as **morph destinations**. A 16dp resting card that becomes 32dp on
press is legible motion; 16 → 20 is invisible.

### 7.3 When NOT to

- **Not on small components.** "Smaller shapes can result in essential actions looking less
  important." A 32dp radius on a 40dp-tall chip eats the whole element and shrinks the tap-legible
  area. Rule of thumb: radius ≤ ~⅓ of the shorter dimension unless you want a pill, in which case use
  `CornerFull`.
- **Not uniformly.** Applying `extraLargeIncreased` app-wide annihilates the contrast it exists to
  create.
- **Not on dense lists.** Grouped/segmented lists want *small* interior corners (4-8dp) with large
  corners only at the group's ends. That is the whole point of the segmented idiom.
- **Not on text fields you want to read as inputs.** Very round inputs read as buttons.
- **Not where it costs clarity.** *"No amount of emotion can compensate for a lack of clarity."*
- **Not as iconography.** *"Avoid assigning a fixed, literal meaning to any single shape."* A rounder
  card does not mean "favorite".

### 7.4 Building shape hierarchy

1. Pick a **base** step for ordinary containers — 12dp or 16dp is typical.
2. Go **down** (4-8dp) for interior/nested/segmented elements.
3. Go **up** one or two steps (20/28dp) for surfaces that own a section.
4. Reserve **32/48dp** for the one hero container per screen.
5. Use `CornerFull` for pills and circles (buttons, chips, avatars, FABs) — that is a category, not a
   step on the scale.
6. Apply the optical-roundness subtraction (§6.1) whenever one rounded rect nests in another.

### 7.5 Shape as state signal

Verified bindings, all belonging to the components rather than the scale:
- **Button press** — shape morph on press/release is the standard Expressive button feedback.
- **Split button trailing toggle** — spins and changes shape when activated.
- **Selection in connected button groups** — member shapes are overridden to unify the group; the
  checked member goes `CornerFull`.
- **Loading** — the loading indicator is a looping morph through seven `MaterialShapes` polygons
  (polygon system, not this scale).

Shape morphs are driven by the **spatial** spring, so they inherit overshoot. See the
`m3-expressive-motion` skill.

---

## 8. Checklist

- [ ] Theme passes a `Shapes` (or deliberately relies on Expressive defaults) — no per-screen
      `RoundedCornerShape(…)` literals for anything that maps to a token.
- [ ] Custom `Shapes` uses the **eight-arg** constructor with named arguments.
- [ ] Scale is monotonic: 4 ≤ 8 ≤ 12 ≤ 16 ≤ 20 ≤ 28 ≤ 32 ≤ 48 (or your shifted equivalent).
- [ ] Grouped lists read corner sizes off `MaterialTheme.shapes`, not literals.
- [ ] Pills use `RoundedCornerShape(50)` or `CornerFull`, never `RoundedCornerShape(50.dp)`.
- [ ] Nested rounded rects subtract their padding from the outer radius.
- [ ] At most one container per screen uses `extraLargeIncreased` / `extraExtraLarge`.
- [ ] Polygon/morph work is going through the `m3-expressive-shapes` skill, not this scale.

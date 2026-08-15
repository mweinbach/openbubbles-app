# M3 Expressive Lists, Cards & Containers

`ListItem` and the segmented-list idiom, cards, carousels, `VerticalDragHandle`, sheets, dialogs,
`Scrim`, and menus.

Confidence markers used below:
- **[verified]** — signature or value read from material3 source / API listing.
- **[from-corpus]** — verbatim from a shipping app in the reference set (Tomato, Med, LastChat,
  vivi-music). Paths given above each excerpt.
- **[canonical-form]** — the API shape is right, exact signature not readable from source.
  Compile-check.
- **[judgment]** — practical guidance, not an API fact.

---

## 1. The headline: containment is a first-class Expressive tactic

Material's Tactic 4, verbatim **[CANON]**:

> "Contain content for emphasis — Organize content into logical groupings or containers."
> **Do:** "Group similar content into informative groupings."
> **Caution:** "Ungrouped information can blend together."

In practice, on Android, this cashes out as the **segmented list**: consecutive `ListItem`s that
share a container colour, sit 2dp apart, and have their outer corners rounded while their inner
corners stay nearly square, so the group reads as one object. This is the single most-used
Expressive layout idiom in the reference corpus — 63 `SegmentedListItem` call sites across Tomato
and Med. Learn this before anything else in this file.

---

## 2. `ListItem` — the expressive overloads

**[verified]** Expressive list item APIs became **non-experimental in 1.5.0-alpha23**. Non-interactive
variants of the standard *and* segmented list item were added in the same release. On 1.4.0 →
alpha22 they need `@OptIn(ExperimentalMaterial3ExpressiveApi::class)`.

```kotlin
// Deprecated (baseline M3)
@Composable
fun ListItem(
    headlineContent: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    overlineContent: @Composable (() -> Unit)? = null,
    supportingContent: @Composable (() -> Unit)? = null,
    leadingContent: @Composable (() -> Unit)? = null,
    trailingContent: @Composable (() -> Unit)? = null,
    colors: ListItemColors = ListItemDefaults.colors(),
    tonalElevation: Dp = ListItemDefaults.Elevation,
    shadowElevation: Dp = ListItemDefaults.Elevation,
)

// Non-interactive (Expressive) — note: content is the TRAILING lambda, not headlineContent
@Composable
fun ListItem(
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    leadingContent: @Composable (() -> Unit)? = null,
    trailingContent: @Composable (() -> Unit)? = null,
    overlineContent: @Composable (() -> Unit)? = null,
    supportingContent: @Composable (() -> Unit)? = null,
    verticalAlignment: Alignment.Vertical = ListItemDefaults.verticalAlignment(),
    shapes: ListItemShapes = ListItemDefaults.shapes(),
    colors: ListItemColors = ListItemDefaults.colors(),
    elevation: ListItemElevation = ListItemDefaults.elevation(),
    contentPadding: PaddingValues = ListItemDefaults.ContentPadding,
    content: @Composable () -> Unit,
)

// Clickable
@Composable
fun ListItem(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    leadingContent: @Composable (() -> Unit)? = null,
    trailingContent: @Composable (() -> Unit)? = null,
    overlineContent: @Composable (() -> Unit)? = null,
    supportingContent: @Composable (() -> Unit)? = null,
    verticalAlignment: Alignment.Vertical = ListItemDefaults.verticalAlignment(),
    onLongClick: (() -> Unit)? = null,
    onLongClickLabel: String? = null,
    shapes: ListItemShapes = ListItemDefaults.shapes(),
    colors: ListItemColors = ListItemDefaults.colors(),
    elevation: ListItemElevation = ListItemDefaults.elevation(),
    contentPadding: PaddingValues = ListItemDefaults.ContentPadding,
    interactionSource: MutableInteractionSource? = null,
    content: @Composable () -> Unit,
)

// Selectable — same as clickable plus a leading `selected: Boolean`
@Composable
fun ListItem(selected: Boolean, onClick: () -> Unit, /* …identical remainder… */)

// Toggleable — same plus leading `checked: Boolean, onCheckedChange: (Boolean) -> Unit`
@Composable
fun ListItem(checked: Boolean, onCheckedChange: (Boolean) -> Unit, /* …identical remainder… */)
```

**[verified]** all of the above.

**The biggest gotcha:** the deprecated overload takes `headlineContent` as its **first** parameter.
The expressive overloads take the headline as the **trailing `content` lambda**. Mechanically:

```kotlin
// old
ListItem(headlineContent = { Text("Language") }, supportingContent = { Text("English") })

// expressive
ListItem(onClick = { … }, supportingContent = { Text("English") }) { Text("Language") }
```

Mixing these up produces confusing "no applicable overload" errors because both overload sets are
still on the classpath.

**Behavioural note [verified]:** alpha23 added the feature flag
`isExpressiveListItemHeightBasedOnTextLinesFixEnabled` — expressive list items change minimum height
based on the number of content lines. If your list items are taller/shorter than expected after an
upgrade, this flag is why.

---

## 3. `SegmentedListItem` + `ListItemDefaults.segmentedShapes` / `segmentedColors` / `SegmentedGap`

The API surface actually used in shipping code, counted across the corpus:

| Member | Call sites |
| --- | --- |
| `ListItemDefaults.colors` | 34 |
| `ListItemDefaults.segmentedShapes` | 20 |
| `ListItemDefaults.SegmentedGap` | 18 |
| `ListItemDefaults.shapes` | 2 |
| `ListItemDefaults.segmentedColors` | 1 |

`ListItemDefaults.segmentedShapes(index, count)` **[verified by usage]** returns a `ListItemShapes`
whose resting/pressed/selected shapes are computed for that item's position in a group of `count`.
`ListItemDefaults.SegmentedGap` is the canonical inter-item spacing. Together they are the whole
idiom.

### 3.1 The minimum viable segmented list

**[from-corpus]** `/root/work/repos/Med/app/src/main/kotlin/com/fedeveloper95/med/elements/MainActivity/Tabs/You/BloodType.kt`
(lines 109-140):

```kotlin
            val itemColors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainer)

            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f, fill = false),
                verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap)
            ) {
                itemsIndexed(bloodTypes) { index, type ->
                    val isSelected = selectedType == type
                    SegmentedListItem(
                        selected = isSelected,
                        onClick = { selectedType = type },
                        colors = itemColors,
                        shapes = ListItemDefaults.segmentedShapes(index = index, count = bloodTypes.size),
                        trailingContent = {
                            if (isSelected) {
                                Icon(
                                    imageVector = Icons.Rounded.Check,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                        },
                        content = {
                            Text(
                                text = type,
                                fontFamily = GoogleSansFlex,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                            )
                        }
                    )
                }
            }
```

That is the whole pattern: `Arrangement.spacedBy(ListItemDefaults.SegmentedGap)` +
`shapes = ListItemDefaults.segmentedShapes(index, count)` + one shared `colors`. Nothing else.

### 3.2 Reusable segmented row — Med's shape

**[from-corpus]** `/root/work/repos/Med/app/src/main/kotlin/com/fedeveloper95/med/SettingsActivity.kt`
(lines 658-700) and the near-identical
`/root/work/repos/Med/app/src/main/kotlin/com/fedeveloper95/med/NotificationsSettingsActivity.kt`
(lines 490-540):

```kotlin
@Composable
fun SettingsSegmentedItem(
    icon: ImageVector,
    title: String,
    subtitle: String,
    containerColor: Color,
    iconColor: Color,
    index: Int,
    count: Int,
    onClick: () -> Unit,
    trailingContent: @Composable (() -> Unit)? = null
) {
    SegmentedListItem(
        selected = false,
        onClick = onClick,
        colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
        shapes = ListItemDefaults.segmentedShapes(index = index, count = count),
        content = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(containerColor),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = iconColor,
                        modifier = Modifier.size(24.dp)
                    )
                }
                Spacer(modifier = Modifier.width(16.dp))
                /* title / subtitle Column */
            }
        }
    )
}
```

The notifications variant adds one line worth copying:

```kotlin
        modifier = if (count == 1) Modifier.clip(RoundedCornerShape(20.dp)) else Modifier,
```

**[judgment]** That is a workaround for a group of one. Prefer to solve it in the shapes (§3.3,
Tomato's `singleElement` parameter) rather than clipping the modifier — clipping fights the
press-state shape morph.

> **Fixed library-side in 1.5.0-alpha25 (I2ea1c, b/479721696): "Fixed shape of segmented list item
> when the list only has a single item."** `ListItemDefaults.segmentedShapes(index = 0, count = 1)`
> now returns a fully-rounded shape on its own. **On alpha25+ delete both workarounds** — Med's
> `count == 1` modifier clip above, and Tomato's `singleElement` branch in §3.3. They are no longer
> needed and the `Modifier.clip` one actively suppresses the press morph.
>
> On **alpha24 and earlier** you still need one of them. Both remain documented below because the
> reference apps are pinned there, and because the identical defect appears in the hand-rolled
> fallback in §4 (`getGroupedShape(0, 1)`), which the library fix does **not** touch — that is your
> own code.

Call sites use it in a plain `Column`:

```kotlin
Column(verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap)) {
    SettingsSegmentedItem(index = 0, count = 3, …)
    SettingsSegmentedItem(index = 1, count = 3, …)
    SettingsSegmentedItem(index = 2, count = 3, …)
}
```

### 3.3 Tomato's complete segmented system — the best example in the corpus

Two files. Shapes first.

**[from-corpus]** `/root/work/repos/Tomato/shared/src/commonMain/kotlin/org/nsh07/pomodoro/ui/theme/Shape.kt`
(lines 18-78, complete after the licence header):

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

Read the three-argument `segmentedShapes(index, count, baseShapes)` overload carefully: you supply a
`ListItemShapes` describing the **per-interaction-state** shapes, and `segmentedShapes` applies the
positional corner logic on top. The per-state entries are what produce the shape morph:

- resting `extraSmall` (or `large` when the group is a single element — **the `singleElement`
  parameter is redundant on alpha25+**, where `segmentedShapes` handles `count == 1` itself; keep
  it only if you are pinned to alpha24 or below),
- **pressed / selected / dragged → `extraLargeIncreased`** (an Expressive-only token),
- focused → `large`, hovered → `extraLarge`.

That is the whole "the row swells into a rounder shape when you touch it" effect, with no
animation code.

Colours second. **[from-corpus]**
`/root/work/repos/Tomato/shared/src/commonMain/kotlin/org/nsh07/pomodoro/ui/theme/Color.kt`
(imports lines 20-28, `CustomColors` lines 103-131):

```kotlin
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.ListItemColors
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme.colorScheme
import androidx.compose.material3.SwitchColors
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.TopAppBarColors
import androidx.compose.material3.TopAppBarDefaults

object CustomColors {
    var black = false

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

    @OptIn(ExperimentalMaterial3ExpressiveApi::class)
    val listItemColors: ListItemColors
        @Composable get() =
            ListItemDefaults.segmentedColors(
                containerColor = if (!black) colorScheme.surfaceBright else colorScheme.surfaceContainerHigh,
                disabledContainerColor = if (!black) colorScheme.surfaceBright else colorScheme.surfaceContainerHigh,
            )

    val switchColors: SwitchColors
        @Composable get() = SwitchDefaults.colors(
            checkedIconColor = colorScheme.primary,
        )
```

`ListItemDefaults.segmentedColors(...)` is the colour counterpart to `segmentedShapes` — it supplies
the selected/pressed container and content colours the segmented item expects. **Use it, not
`ListItemDefaults.colors()`, when the items are selectable**; otherwise the selected state has no
container treatment. (Med uses plain `colors()` and paints selection itself via content colour —
also valid, more work.)

The `black` flag threading an AMOLED override through both objects is worth copying: one boolean,
every surface token switched at the source, no `if (amoled)` at call sites.

Usage — **[from-corpus]**
`/root/work/repos/Tomato/shared/src/commonMain/kotlin/org/nsh07/pomodoro/ui/settingsScreen/screens/SettingsMainScreen.kt`
(lines 166-190):

```kotlin
            itemsIndexed(settingsScreens) { index, item ->
                SegmentedListItem(
                    leadingContent = {
                        Icon(painterResource(item.icon), null)
                    },
                    supportingContent = {
                        val innerStrings = item.innerSettings.map { stringResource(it) }
                        val joinedText = remember(innerStrings) { innerStrings.joinToString(", ") }
                        Text(
                            joinedText,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    },
                    trailingContent = if (!widthExpanded) {
                        { Icon(painterResource(Res.drawable.arrow_forward_big), null) }
                    } else null,
                    shapes = segmentedListItemShapes(index, settingsScreens.size),
                    colors = listItemColors,
                    selected = currentScreen == item.route,
                    onClick = { onNavigate(item.route) }
                ) { Text(stringResource(item.label)) }
            }
```

Note `selected = currentScreen == item.route` — in a list/detail layout the segmented item doubles
as the navigation selection indicator, and `trailingContent` (the chevron) disappears when the
detail pane is already visible. That is Tactic 6 (component flexibility) done correctly.

Sub-groups within one screen are just separate `index`/`count` runs separated by a `Spacer`:
`segmentedListItemShapes(0, 2)` then `segmentedListItemShapes(1, 2)`, then a
`Spacer(Modifier.height(12.dp))`, then `segmentedListItemShapes(0, 1)` for a standalone row.

### 3.4 Known API gap: `SegmentedListItem` has no non-clickable overload

**[from-corpus]** verbatim comment in Tomato's `ColorSchemePickerListItem.kt`:

```kotlin
        Box( // TODO: Workaround to disable clickable behavior of SegmentedListItem. Remove once an overload is implemented
            Modifier
                .matchParentSize()
                .clickable(false) {}
        )
```

alpha23 added *"non-interactive variants of standard + segmented list item"* **[verified — release
note]**, so on alpha23+ check whether a no-`onClick` overload resolves before applying this hack. On
alpha21 (Tomato's and Med's pin) it does not.

---

## 4. The manual corner-computation fallback (projects below alpha21)

If `ListItemDefaults.segmentedShapes` isn't available at your pinned version, compute the corners
yourself and pass them as `shape` to a `Surface` / `Card` / clipped `Row`.

**[from-corpus]** `/root/work/repos/vivi-music/app/src/main/kotlin/com/music/vivi/utils/ShapesCurve.kt`
(complete file):

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
fun listItemColors(): ListItemColors {
    return ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)
}

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

fun getGroupedShape(index: Int, count: Int): Shape {
    return when {
        index == 0 -> leadingItemShape()
        index == count - 1 -> endItemShape()
        else -> middleItemShape()
    }
}
```

### Bug in that version — fix it when you copy it

`getGroupedShape(0, 1)` — a group of exactly one item — hits `index == 0` first and returns
`leadingItemShape()`: rounded top, 4dp bottom. A single-item group must be fully rounded on all four
corners. Order the `when` so the singleton case wins:

```kotlin
fun onlyItemShape(): RoundedCornerShape = RoundedCornerShape(EndCornerRadius.dp)

fun getGroupedShape(index: Int, count: Int): Shape = when {
    count <= 1 -> onlyItemShape()          // <- must come first
    index == 0 -> leadingItemShape()
    index == count - 1 -> endItemShape()
    else -> middleItemShape()
}
```

Same defect exists in the constant pair duplicated at the bottom of vivi-music's
`ui/component/UpdaterComponents.kt`. Med's `count == 1 -> Modifier.clip(RoundedCornerShape(20.dp))`
(§3.2) is the same bug patched at the call site rather than in the helper.

**Note the scope of the alpha25 fix.** I2ea1c fixed the *library's* `segmentedShapes` for
`count == 1`. It does nothing for hand-rolled helpers like `getGroupedShape` above — that is your
code, and the ordering bug stays until you fix it. If you are on alpha25+, the better move is to
delete the hand-rolled helper entirely and call `ListItemDefaults.segmentedShapes(index, count)`.

Related one-liner worth having — **[from-corpus]**
`/root/work/repos/vivi-music/app/src/main/kotlin/com/music/vivi/ui/utils/ShapeUtils.kt` (complete file):

```kotlin
fun CornerBasedShape.top(): CornerBasedShape =
    copy(bottomStart = CornerSize(0.dp), bottomEnd = CornerSize(0.dp))
```

Use it to square off the bottom of a shape token when you attach something below it (see §5).

Same trio in vivi-music's onboarding, with 20dp ends instead of 16dp **[from-corpus,
`WelcomeActivity.kt`]**:

```kotlin
    val topCardShape =
        RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp, bottomStart = 4.dp, bottomEnd = 4.dp)
    val middleCardShape = RoundedCornerShape(4.dp)
    val bottomCardShape =
        RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp, bottomStart = 20.dp, bottomEnd = 20.dp)
```

**[judgment]** The 4dp inner / 16-20dp outer ratio is the tell of the idiom. Both independent
implementations landed on it. If you hand-roll, use those numbers.

---

## 5. Custom expressive list item — Tomato's `ColorSchemePickerListItem`

Shows three techniques at once: composing `segmentedShapes` by hand to attach a non-list-item
element underneath, a `Switch` with `thumbContent` in `trailingContent`, and
`IconButtonDefaults.shapes()` swatches.

**[from-corpus]**
`/root/work/repos/Tomato/shared/src/commonMain/kotlin/org/nsh07/pomodoro/ui/settingsScreen/components/ColorSchemePickerListItem.kt`
(lines 64-195):

```kotlin
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ColorSchemePickerListItem(
    color: Color,
    items: Int,
    index: Int,
    isPlus: Boolean,
    onColorChange: (Color) -> Unit,
    modifier: Modifier = Modifier
) {
    val colorSchemes = listOf(
        Color(0xfffeb4a7), Color(0xffffb3c0), Color(0xfffcaaff), Color(0xffb9c3ff),
        Color(0xff62d3ff), Color(0xff44d9f1), Color(0xff52dbc9), Color(0xff78dd77),
        Color(0xff9fd75c), Color(0xffc1d02d), Color(0xfffabd00), Color(0xffffb86e),
        Color.White
    )

    if (androidSdkVersionAtLeast(31)) {
        val checked = color == colorSchemes.last()
        SegmentedListItem(
            onClick = {
                if (!checked) onColorChange(colorSchemes.last())
                else onColorChange(colorSchemes.first())
            },
            leadingContent = { Icon(painterResource(Res.drawable.colors), null) },
            content = { Text(stringResource(Res.string.dynamic_color)) },
            supportingContent = { Text(stringResource(Res.string.dynamic_color_desc)) },
            trailingContent = {
                Switch(
                    checked = checked,
                    onCheckedChange = {
                        if (it) onColorChange(colorSchemes.last())
                        else onColorChange(colorSchemes.first())
                    },
                    enabled = isPlus,
                    thumbContent = {
                        if (checked) {
                            Icon(
                                painter = painterResource(Res.drawable.check),
                                contentDescription = null,
                                modifier = Modifier.size(SwitchDefaults.IconSize),
                            )
                        } else {
                            Icon(
                                painter = painterResource(Res.drawable.clear),
                                contentDescription = null,
                                modifier = Modifier.size(SwitchDefaults.IconSize),
                            )
                        }
                    },
                    colors = switchColors
                )
            },
            colors = listItemColors,
            enabled = isPlus,
            shapes = segmentedListItemShapes(index, items),
            modifier = modifier
        )
        Spacer(Modifier.height(2.dp))
    }

    Box {
        SegmentedListItem(
            onClick = {},
            leadingContent = {
                Icon(
                    painter = painterResource(Res.drawable.palette),
                    contentDescription = null
                )
            },
            content = { Text(stringResource(Res.string.color_scheme)) },
            supportingContent = {
                Text(
                    if (color == Color.White) stringResource(Res.string.dynamic)
                    else stringResource(Res.string.color)
                )
            },
            colors = listItemColors,
            enabled = isPlus,
            shapes = ListItemDefaults.segmentedShapes(
                1,
                3,
                ListItemDefaults.shapes(
                    shape = shapes.extraSmall.copy(
                        bottomStart = CornerSize(0),
                        bottomEnd = CornerSize(0)
                    )
                )
            ),
            modifier = modifier
        )

        Box( // TODO: Workaround to disable clickable behavior of SegmentedListItem. Remove once an overload is implemented
            Modifier
                .matchParentSize()
                .clickable(false) {}
        )
    }

    LazyRow(
        contentPadding = PaddingValues(horizontal = 48.dp),
        userScrollEnabled = isPlus,
        modifier = modifier
            .background(
                animateColorAsState(listItemColors.containerColor).value,
                shape = shapes.extraSmall.copy(topStart = CornerSize(0), topEnd = CornerSize(0))
            )
            .padding(bottom = 8.dp)
    ) {
        items(colorSchemes.dropLast(1)) {
            ColorPickerButton(
                color = it,
                isSelected = it == color,
                enabled = isPlus,
                modifier = Modifier.padding(4.dp)
            ) {
                onColorChange(it)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ColorPickerButton(
    color: Color,
    isSelected: Boolean,
    enabled: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    IconButton(
        shapes = IconButtonDefaults.shapes(),
        colors = IconButtonDefaults.iconButtonColors(
            containerColor = color,
            disabledContainerColor = color.copy(0.3f)
        ),
        /* … */
    )
}
```

The key trick: `segmentedShapes(1, 3, ListItemDefaults.shapes(shape = shapes.extraSmall.copy(bottomStart = CornerSize(0), bottomEnd = CornerSize(0))))`
— "pretend this is item 1 of 3 (so it gets middle-item corners), then zero the bottom corners
entirely" — and the `LazyRow` below wears the mirror-image shape
(`topStart = CornerSize(0), topEnd = CornerSize(0)`). The list item and the swatch row read as one
container. The container colour is animated (`animateColorAsState`) so the swatch row's background
follows the list item's enabled/disabled transition.

---

## 6. Cards

`Card`, `ElevatedCard`, `OutlinedCard` are unchanged by Expressive at the API level. What changes is
which one you reach for.

**[from-corpus]** Every card in Med is flat:

```kotlin
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) { /* … */ }
```

**[judgment]** This is the Expressive house style and it is deliberate: separation comes from
**surface tier + shape + spacing**, not from shadows. `surfaceContainerLow` → `surfaceContainer` →
`surfaceContainerHigh` → `surfaceContainerHighest` is a four-step depth ladder that survives dark
mode and AMOLED, which shadows do not.

Shape: Tomato exposes `cardShape = shapes.large` as a single token and uses it everywhere
**[from-corpus, `Shape.kt`]**. Expressive adds `largeIncreased`, `extraLarge`,
`extraLargeIncreased` to the scale — cards are where `largeIncreased` and above earn their keep.

**Card vs segmented list — the decision [judgment]:**

| Situation | Use |
| --- | --- |
| Rows are variations on one thing (settings, options, a picker) | **Segmented list.** One container = one concept. |
| Each item is independently meaningful and has its own actions | **Separate cards** with real spacing between them. |
| A list of >3 items | Segmented list. Three-plus cards in a column is visual noise — "Ungrouped information can blend together" cuts both ways; over-containment is also blending. |
| An item that needs a media header, a body, and its own button row | Card. |

Do not put cards inside a segmented list, or segmented items inside a card. Pick one containment
level per region.

### 6.1 `Modifier.dropShadow` — when you *do* want a shadow

`androidx.compose.ui.draw.dropShadow` is the new Compose shadow API: a modifier that takes a
`shape` and a trailing **`ShadowScope`** lambda (`radius`, `spread`, `offset`, `color`, `brush`).
It replaces `Modifier.shadow(elevation)` and can express three things the old API cannot.

**[GOOGLE — android/ai-samples, jetpacker]** — 12 call sites, three distinct uses.

**1. Layered soft elevation** (`JetPackerToolbar.kt:94-104`) — two stacked `dropShadow`s produce a
proper two-layer M3 elevation shadow, which `Modifier.shadow` cannot do at all:

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
```

**2. Hard "sticker" offset shadow** (`JetPackerFab.kt:63-68`, identical in
`JetPackerExtendedFloatingActionButton.kt:82-87` and `VoiceNotesScreen.kt:462`/`:580`) —
`radius = 0f, spread = 0f` plus an offset gives a crisp neo-brutalist edge:

```kotlin
            .dropShadow(shape = shape) {
                radius = 0f
                spread = 0f
                offset = Offset(x = 2.dp.toPx(), y = 3.dp.toPx())
                color = Color(0xFF20290A)
            }
```

**3. Animated gradient glow** (`TripSummaryAndTipsCard.kt:139-145`) — `ShadowScope.brush` accepts a
`Brush`, so the shadow itself can be a rotating multi-colour gradient. jetpacker uses this as its
AI-affordance treatment:

```kotlin
        .dropShadow(RoundedCornerShape(24.dp)) {
          if (isLoaded) {
            brush = glowBrush
            radius = 24.dp.toPx()
            offset = Offset(x = 0f, y = 16.dp.toPx())
          }
        }
```

Note the `if` **inside** the scope lambda: "no shadow while loading" without adding or removing a
modifier from the chain, so no relayout.

**[judgment]** None of this contradicts the flat-card house style above — it *sharpens* it. Surface
tiers remain the default separator for ordinary containers. `dropShadow` is for the cases where a
shadow is the point: a floating element that must read as detached from arbitrary content beneath
it (#1), a deliberate stylistic signature (#2), or a state/affordance signal that a colour change
alone would not carry (#3). Reaching for #2 or #3 on every card is the same over-application error
as putting a hero shape on every row.

---

## 7. Carousels

**[verified]** Opt-in on 1.4.0: `ExperimentalMaterial3Api`. Package: `androidx.compose.material3.carousel`.

```kotlin
@Composable
fun HorizontalMultiBrowseCarousel(
    state: CarouselState,
    preferredItemWidth: Dp,
    modifier: Modifier = Modifier,
    itemSpacing: Dp = 0.dp,
    flingBehavior: TargetedFlingBehavior =
        CarouselDefaults.singleAdvanceFlingBehavior(state = state),
    userScrollEnabled: Boolean = true,
    minSmallItemWidth: Dp = CarouselDefaults.MinSmallItemSize,
    maxSmallItemWidth: Dp = CarouselDefaults.MaxSmallItemSize,
    contentPadding: PaddingValues = PaddingValues(0.dp),
    content: @Composable CarouselItemScope.(itemIndex: Int) -> Unit,
)

@Composable
fun HorizontalUncontainedCarousel(
    state: CarouselState,
    itemWidth: Dp,
    modifier: Modifier = Modifier,
    itemSpacing: Dp = 0.dp,
    flingBehavior: TargetedFlingBehavior = CarouselDefaults.noSnapFlingBehavior(),
    userScrollEnabled: Boolean = true,
    contentPadding: PaddingValues = PaddingValues(0.dp),
    content: @Composable CarouselItemScope.(itemIndex: Int) -> Unit,
)
```

Supporting **[verified]**: `CarouselState` + `rememberCarouselState`, `CarouselItemScope`,
`CarouselDefaults` (`singleAdvanceFlingBehavior`, `multiBrowseFlingBehavior`, `noSnapFlingBehavior`,
`MinSmallItemSize`, `MaxSmallItemSize`).

`HorizontalCenteredHeroCarousel` — see §7.2 for canonical verbatim code. Note it takes
`itemSpacing` + `contentPadding` but **no** `preferredItemWidth` / `itemWidth`: the hero strategy
sizes the focal item itself.

**Opt-in correction:** the whole carousel family is gated by `ExperimentalMaterial3Api`, **not**
`ExperimentalMaterial3ExpressiveApi`. Verified from the census of the androidx samples module —
`CarouselSamples.kt` carries **0** Expressive occurrences and 2 `ExperimentalMaterial3Api`.

Which strategy **[verified — material-components-android Carousel.md]**:

| Strategy | Guidance | Use when |
| --- | --- | --- |
| **Multi-browse** (default) | *"allows quick browsing of many small items, like a photo thumbnail gallery"* — large, medium, small items in sequence | scanning many items fast |
| **Hero** | *"highlights large content, like movies and other media, for more considered browsing and selection"* — start-aligned or center-aligned | considered selection |
| **Uncontained** | *"fits as many items as possible into the carousel without altering the item size"* — cuts off edge items | preserving original aspect ratios matters |
| **Full-screen** | *"shows one item at a time that takes up the entire space of the carousel"* | recommended for **vertical orientation in portrait** |

**Sizing rule [verified]:** item width must be a concrete dp value, never wrap-content. The layout
manager uses the first item's width as reference and adjusts the rest to fit. Small-item size is
bounded by `minSmallItemWidth`/`maxSmallItemWidth`.

**`contentPadding` vs `itemSpacing` [verified]:** `contentPadding` is applied **after** clipping —
use `itemSpacing` for gaps between items, `contentPadding` only for leading/trailing space.

### 7.1 Real usage — `HorizontalMultiBrowseCarousel`

Carousels are **not** thin in the corpus: vivi-music uses `HorizontalMultiBrowseCarousel` at two
sites (below), Jetcaster uses it at one plus `HorizontalUncontainedCarousel` at another, the
docs-site snippet is byte-verified, and androidx ships six `@Sampled` carousel functions.

**[from-corpus]** `/root/work/repos/vivi-music/app/src/main/kotlin/com/music/vivi/ui/screens/HomeScreen.kt`
(lines 1434-1460):

```kotlin
                                        val carouselState = rememberCarouselState { discoverList.size }
                                        HorizontalMultiBrowseCarousel(
                                            state = carouselState,
                                            preferredItemWidth = 320.dp,
                                            itemSpacing = 16.dp,
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .height(320.dp)
                                        ) { i ->
                                            val item = discoverList[i]
                                            DailyDiscoverCard(
                                                dailyDiscover = item,
                                                onClick = { /* … */ },
                                                navController = navController,
                                                modifier = Modifier.maskClip(MaterialTheme.shapes.extraLarge)
                                            )
                                        }
```

**[from-corpus]** `/root/work/repos/vivi-music/app/src/main/kotlin/com/music/vivi/ui/screens/search/suggestions/TabNewsSuggestion.kt`
(lines 476-512):

```kotlin
    val carouselState = rememberCarouselState(itemCount = { videos.size })
    …
        HorizontalMultiBrowseCarousel(
            state = carouselState,
            preferredItemWidth = 320.dp,
            itemSpacing = 12.dp,
            modifier = Modifier
                .fillMaxWidth()
                .height(200.dp),
            contentPadding = PaddingValues(horizontal = 16.dp)
        ) { i -> /* … */ }
```

**The critical detail is `Modifier.maskClip(...)`.** It is a `CarouselItemScope` modifier: it clips
the item to the carousel's *current mask* for that item, so the item's corners animate as it scrolls
between the large and small slots. Using `Modifier.clip(RoundedCornerShape(16.dp))` instead (as
`TabNewsSuggestion.kt` does) gives you a static corner and the item visibly clips wrong at the
edges. Always `maskClip` inside a carousel; `CarouselItemScope` also exposes `maskBorder` for a
stroke that follows the same mask.

**Pitfalls:**
- `rememberCarouselState { list.size }` takes the count as a **lambda**, re-read on recomposition.
  Both forms above are the same function; `itemCount = { … }` is the named version.
- Fix the carousel's height explicitly (`Modifier.height(320.dp)`); items are measured against it.
- The default fling for multi-browse is `singleAdvanceFlingBehavior` (one item per fling). For long
  galleries where users want to travel, pass `CarouselDefaults.multiBrowseFlingBehavior(...)`.

### 7.2 `HorizontalCenteredHeroCarousel` — canonical, verbatim

Source: **androidx material3 samples**,
`compose/material3/material3/samples/src/main/java/androidx/compose/material3/samples/CarouselSamples.kt`
(lines 174–214), `HorizontalCenteredHeroCarouselSample`. This is the **only** source for this
component anywhere — developer.android.com's carousel page does not document it, and no Google
application sample uses it.

```kotlin
@Preview
@Sampled
@Composable
fun HorizontalCenteredHeroCarouselSample() {

    data class CarouselItem(
        val id: Int,
        @DrawableRes val imageResId: Int,
        @StringRes val contentDescriptionResId: Int,
    )

    val items =
        listOf(
            CarouselItem(0, R.drawable.carousel_image_1, R.string.carousel_image_1_description),
            CarouselItem(1, R.drawable.carousel_image_2, R.string.carousel_image_2_description),
            CarouselItem(2, R.drawable.carousel_image_3, R.string.carousel_image_3_description),
            CarouselItem(3, R.drawable.carousel_image_4, R.string.carousel_image_4_description),
            CarouselItem(4, R.drawable.carousel_image_5, R.string.carousel_image_5_description),
        )
    val state = rememberCarouselState { items.count() }
    val animationScope = rememberCoroutineScope()
    HorizontalCenteredHeroCarousel(
        state = state,
        modifier = Modifier.fillMaxWidth().height(221.dp).padding(horizontal = 24.dp),
        itemSpacing = 8.dp,
        contentPadding = PaddingValues(horizontal = 16.dp),
    ) { i ->
        val item = items[i]
        Image(
            modifier =
                Modifier.fillMaxWidth()
                    .height(205.dp)
                    .maskClip(MaterialTheme.shapes.extraLarge)
                    .clickable(true, "Tap to focus", Role.Image) {
                        animationScope.launch { state.animateScrollToItem(i) }
                    },
            painter = painterResource(id = item.imageResId),
            contentDescription = stringResource(item.contentDescriptionResId),
            contentScale = ContentScale.Crop,
        )
    }
}
```

Two behaviours this establishes:

1. **Hero items are tap-to-focus.** `clickable(true, "Tap to focus", Role.Image)` calling
   `state.animateScrollToItem(i)` — tapping an off-centre item scrolls it to the hero slot rather
   than opening it. If your hero items open a detail screen instead, you owe the user a separate
   affordance for focusing.
2. **No `preferredItemWidth` / `itemWidth`.** Only `itemSpacing` and `contentPadding`. Height is
   set on the carousel (`221.dp`) and again on the item (`205.dp`) — the difference is the mask's
   breathing room.

Other carousel samples in the same file (names confirmed, **bodies UNVERIFIED**):
`FadingHorizontalMultiBrowseCarouselSample` (line 220), `CarouselWithShowAllButtonSample` (339),
`MultiAspectCarouselLazyRowSample` (428, `@OptIn(ExperimentalMaterial3Api::class)`).

### 7.3 `maskClip` before `clickable` — the ordering rule

**[GOOGLE — android/compose-samples, Jetcaster]**
`Jetcaster/mobile/src/main/java/com/example/jetcaster/ui/home/Home.kt` (lines ~642–679):

```kotlin
        HorizontalMultiBrowseCarousel(
            state = rememberCarouselState { items.count() },
            preferredItemWidth = 205.dp,
            itemSpacing = 12.dp,
            contentPadding = PaddingValues(8.dp),
        ) { page ->
            val podcast = items[page]
            FollowedPodcastCarouselItem(
                podcastImageUrl = podcast.imageUrl,
                podcastTitle = podcast.title,
                onUnfollowedClick = { onPodcastUnfollowed(podcast) },
                lastEpisodeDateText = podcast.lastEpisodeDate?.let { lastUpdated(it) },
                modifier = Modifier
                    .fillMaxSize()
                    .maskClip(MaterialTheme.shapes.large)
                    .clickable {
                        navigateToPodcastDetails(podcast)
                    },
            )
        }
```

**`.maskClip(...)` goes before `.clickable {}`** so the ripple is clipped to the carousel mask. Get
it backwards and the ripple paints square over rounded artwork as the item scrolls between slots.

Caveat on this file: the real Jetcaster source wraps this in a `BoxWithConstraints` that computes an
unused `horizontalPadding` val, with a stale TODO about `snapPosition`. That is leftover
scaffolding — **do not copy it**; it costs a second measure pass for nothing.

Jetcaster's `HorizontalUncontainedCarousel` site, for reference:
`Jetcaster/mobile/src/main/java/com/example/jetcaster/ui/home/category/PodcastCategory.kt:127`.

`maskClip` / `maskBorder` need **no import** — they are `CarouselItemScope` receiver extensions.
They are also entirely absent from developer.android.com's carousel page, which is otherwise
current: that page's multi-browse and uncontained snippets are byte-identical to
`snippets/compose/snippets/.../components/Carousel.kt` (lines 41–127) but never explain the mask.

---

## 8. `VerticalDragHandle`

**[verified]** Opt-in on 1.4.0: `ExperimentalMaterial3ExpressiveApi` (status at alpha24 UNVERIFIED).

```kotlin
@Composable
fun VerticalDragHandle(
    modifier: Modifier = Modifier,
    sizes: DragHandleSizes = VerticalDragHandleDefaults.sizes(),
    colors: DragHandleColors = VerticalDragHandleDefaults.colors(),
    shapes: DragHandleShapes = VerticalDragHandleDefaults.shapes(),
    interactionSource: MutableInteractionSource? = null,
)
```

KDoc, verbatim: *"a capsule-like shape that can be used by users to change component size and/or
position by dragging."* `interactionSource` doc: if not supplied, *"interactions will still happen
internally."*

The Expressive part is that it **grows and changes shape on press and drag** — that is why it takes
`sizes` / `shapes` / `colors` triples rather than a single set. It is a pure affordance: it does not
implement dragging. You must combine it with a drag modifier and
`Modifier.systemGestureExclusion()` so it doesn't fight the system back gesture **[verified — sample
note]**.

### Real use: pane expansion in `SupportingPaneScaffold`

**[from-corpus]**
`/root/work/repos/Tomato/shared/src/androidMain/kotlin/org/nsh07/pomodoro/ui/timerScreen/TimerScreen.kt`
(setup lines 216-224; handle lines 823-837):

```kotlin
    val navigator = rememberSupportingPaneScaffoldNavigator(
        adaptStrategies = SupportingPaneScaffoldDefaults.adaptStrategies(supportingPaneAdaptStrategy = AdaptStrategy.Hide)
    )
    val expansionState = rememberPaneExpansionState()

    SupportingPaneScaffold(
        directive = navigator.scaffoldDirective,
        scaffoldState = navigator.scaffoldState,
        mainPane = {
            AnimatedPane {
                ...
            }
        },
```

```kotlin
        paneExpansionDragHandle = {
            val interactionSource = remember { MutableInteractionSource() }
            VerticalDragHandle(
                modifier = Modifier
                    .paneExpansionDraggable(
                        expansionState,
                        LocalMinimumInteractiveComponentSize.current,
                        interactionSource
                    )
                    .systemGestureExclusion()
            )
        },
        paneExpansionState = expansionState
    )
}
```

Everything essential is here:
- `rememberPaneExpansionState()` is created once and passed to **both**
  `Modifier.paneExpansionDraggable(...)` and the scaffold's `paneExpansionState`.
- The `MutableInteractionSource` is shared between the modifier and the handle so the handle's
  press/drag shape morph is driven by the actual drag.
- `LocalMinimumInteractiveComponentSize.current` is the minimum touch target — pass it, don't
  hardcode 48.dp.
- `.systemGestureExclusion()` last.

Outside pane scaffolds, `VerticalDragHandle` is the right affordance for any user-resizable vertical
split (a resizable sidebar, a split editor). Pair it with `Modifier.draggable()` **[verified — sample
note]**.

**Pitfall:** there is no `HorizontalDragHandle` counterpart in this family for pane expansion — for
bottom sheets the drag handle is `BottomSheetDefaults.DragHandle`, a different component.

---

## 9. Sheets

### 9.1 State: the alpha20 unification

**[verified]** `rememberBottomSheetState` (alpha20/21) is the unified replacement for
**`rememberModalBottomSheetState`** and **`rememberStandardBottomSheetState`**, both deprecated.

Related changes **[verified]**:
- **alpha15** — a standalone **static sheet component** was introduced (name/signature UNVERIFIED),
  plus a **back-handler-disable parameter** on `BottomSheet`.
- **alpha16** — `BottomSheet` now respects `MaterialTheme.motionScheme`. This is the change that
  makes sheets feel Expressive with no code: the expand/settle springs come from the theme.
- **alpha18** — `BottomSheet` moved to `BottomSheet.kt`.
- **alpha21** — the `PartiallyExpanded` anchor is no longer auto-removed; it is explicitly
  controlled. If a sheet that used to snap straight to expanded now stops halfway after an upgrade,
  this is why.
- Feature flags in the area: `isAnchoredDraggableComponentsAnchorRecoveryEnabled` (alpha16),
  `isBottomSheetPartiallyExpandedDeterministicEnabled`.

**Every sheet in the corpus still uses `rememberModalBottomSheetState`** — 288 `ModalBottomSheet`
occurrences, all on the deprecated state factory. Migrate to `rememberBottomSheetState` on
alpha20+; the corpus is behind here.

vivi-music's `rememberBottomSheetState` is **its own** custom function in
`com.music.vivi.ui.component`, not material3's — do not confuse the two if you read that repo.

### 9.2 `ModalBottomSheet` in practice

**[from-corpus]**
`/root/work/repos/Tomato/shared/src/androidMain/kotlin/org/nsh07/pomodoro/ui/settingsScreen/components/LocaleBottomSheet.android.kt`
(lines 95-135):

```kotlin
    val bottomSheetState = rememberModalBottomSheetState()
    val listState = rememberLazyListState()

    ModalBottomSheet(
        onDismissRequest = { setShowSheet(false) },
        sheetState = bottomSheetState,
        modifier = modifier
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = stringResource(Res.string.choose_language),
                style = MaterialTheme.typography.labelLarge,
                modifier = Modifier.padding(bottom = 16.dp)
            )
            if (supportedLocalesList != null) {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                    state = listState,
                    modifier = Modifier
                        .padding(horizontal = 16.dp)
                        .clip(shapes.large)
                ) {
                    item {
                        SegmentedListItem(
                            onClick = {
                                scope
                                    .launch { bottomSheetState.hide() }
                                    .invokeOnCompletion {
                                        /* …apply, then… */
                                        setShowSheet(false)
                                    }
                            },
                            content = { Text(stringResource(Res.string.system_default)) },
                            trailingContent = { /* check icon when selected */ },
                            colors = listItemColors,
                            selected = currentLocales.isEmpty,
                            shapes = segmentedListItemShapes(0, 1)
                        )
                    }
                    …
                }
            }
        }
    }
```

The dismissal idiom is load-bearing: **`scope.launch { sheetState.hide() }.invokeOnCompletion { … }`**.
Calling `setShowSheet(false)` directly removes the sheet from composition instantly and you lose the
exit animation. `hide()` is a suspend animation; commit the state change in `invokeOnCompletion`.

Note also `contentWindowInsets = { WindowInsets(0, 0, 0, 0) }` in Med's sheets
**[from-corpus, `MedicineBottomSheet.kt:310`, `EventBottomSheet.kt:170`, `IllnessesBottomSheet.kt:185`]**
— when the sheet content is a scrolling list that manages its own bottom inset, zero out the sheet's.

### 9.3 `Scrim`

**[verified]** New standalone component in **1.5.0-alpha15**, *"for use alongside modal components."*
**Signature UNVERIFIED** and used by nothing in the corpus. It exists so you can build a custom modal
surface (a bespoke sheet, a custom overlay) with the same scrim colour/alpha/animation the built-in
modals use, instead of hand-rolling a `Box(Modifier.background(Color.Black.copy(alpha = 0.32f)))`.
Look up its actual signature before use.

---

## 10. Dialogs

`AlertDialog` is unchanged structurally. What Expressive gives it:

- **`AlertDialogDefaults.IconSize`** — new in **alpha16** **[verified]**. Use it instead of a literal
  24.dp on the dialog's icon slot.
- Shape comes from the theme (`shapes.extraLarge` by default), so an Expressive shape scale changes
  dialog corners for free.
- Motion comes from `MaterialTheme.motionScheme` — dialogs enter/exit on theme springs.
- **`BasicAlertDialog` graduated from Experimental in 1.5.0-alpha25** (If157c) **[verified]**. It is
  the unstyled dialog container you use when you want `AlertDialog`'s window/scrim/predictive-back
  behaviour but your own content layout. On alpha25+ drop any
  `@OptIn(ExperimentalMaterial3Api::class)` that existed solely for it — a stale opt-in is a
  warning, not an error, unless you build with warnings-as-errors.

`AlertDialogDefaults` members used in shipping code **[from-corpus,
`/root/work/repos/vivi-music/app/src/main/kotlin/com/music/vivi/ui/component/Dialog.kt`]**:

```kotlin
        Surface(
            shape = AlertDialogDefaults.shape,
            color = AlertDialogDefaults.containerColor,
            tonalElevation = AlertDialogDefaults.TonalElevation
        ) {
            …
                    CompositionLocalProvider(LocalContentColor provides AlertDialogDefaults.iconContentColor) { … }
            …
                    CompositionLocalProvider(LocalContentColor provides AlertDialogDefaults.titleContentColor) { … }
```

That is the correct way to build a **custom** dialog: reuse `AlertDialogDefaults` tokens on a
`Surface` rather than inventing colours, so your custom dialog matches the built-in one under every
theme.

**[judgment]** The Expressive move available in a dialog is the **button row**: a connected
`ButtonGroup` of confirm/dismiss instead of two `TextButton`s. vivi-music does exactly this for a
three-way destructive confirm (see `m3-expressive-components/references/buttons.md`). Do not
over-animate the dialog itself — no overshoot on a scrim's alpha.

### 10.1 `TimePickerDialog` is a real material3 API now — stop hand-rolling it

**This contradicts developer.android.com.** The docs site's time-picker page renders from
`android/snippets` (`TimePickers.kt:317-337`), which defines its **own** `TimePickerDialog` out of
`AlertDialog`, plus an `AdvancedTimePickerDialog` (line 391) built from a raw `Dialog` + `Surface`.
That snippet is **outdated**. androidx ships the real thing.

Confirmed public surface, none of which existed in this file before:
`TimePickerDialog` (slot-based: `title` / `confirmButton` / `dismissButton` / `modeToggleButton` /
content), `TimePickerDialogDefaults.Title`, `TimePickerDialogDefaults.DisplayModeToggle`,
`TimePickerDialogDefaults.MinHeightForTimePicker`, `TimePickerDisplayMode.Picker | Input`,
`state.isInputValid`, and a separate **`RichTimePickerDialog`**.

Source: **androidx material3 samples**,
`compose/material3/material3/samples/src/main/java/androidx/compose/material3/samples/TimePickerSamples.kt`
(lines 152–219), `TimePickerSwitchableSample`. Verbatim.

```kotlin
@OptIn(ExperimentalMaterial3Api::class)
@Sampled
@Composable
@Preview
fun TimePickerSwitchableSample() {
    var showTimePicker by rememberSaveable { mutableStateOf(false) }
    val state = rememberTimePickerState()
    val formatter = remember { SimpleDateFormat("hh:mm a", Locale.getDefault()) }
    val snackState = remember { SnackbarHostState() }
    var displayMode by rememberSaveable { mutableStateOf(TimePickerDisplayMode.Picker) }
    val snackScope = rememberCoroutineScope()
    val configuration = LocalConfiguration.current

    Box(propagateMinConstraints = false) {
        Button(modifier = Modifier.align(Alignment.Center), onClick = { showTimePicker = true }) {
            Text("Set Time")
        }
        SnackbarHost(hostState = snackState)
    }

    if (showTimePicker) {
        TimePickerDialog(
            title = { TimePickerDialogDefaults.Title(displayMode = displayMode) },
            onDismissRequest = { showTimePicker = false },
            confirmButton = {
                TextButton(
                    enabled = state.isInputValid,
                    onClick = {
                        val cal = Calendar.getInstance()
                        cal.set(Calendar.HOUR_OF_DAY, state.hour)
                        cal.set(Calendar.MINUTE, state.minute)
                        cal.isLenient = false
                        snackScope.launch {
                            snackState.showSnackbar("Entered time: ${formatter.format(cal.time)}")
                        }
                        showTimePicker = false
                    },
                ) {
                    Text("Ok")
                }
            },
            dismissButton = { TextButton(onClick = { showTimePicker = false }) { Text("Cancel") } },
            modeToggleButton = {
                if (configuration.screenHeightDp.dp > MinHeightForTimePicker) {
                    TimePickerDialogDefaults.DisplayModeToggle(
                        onDisplayModeChange = {
                            displayMode =
                                if (displayMode == TimePickerDisplayMode.Picker) {
                                    TimePickerDisplayMode.Input
                                } else {
                                    TimePickerDisplayMode.Picker
                                }
                        },
                        displayMode = displayMode,
                    )
                }
            },
        ) {
            if (
                displayMode == TimePickerDisplayMode.Picker &&
                    configuration.screenHeightDp.dp > MinHeightForTimePicker
            ) {
                TimePicker(state = state)
            } else {
                TimeInput(state = state)
            }
        }
    }
}
```

Imports (verbatim, lines 23–39 subset):

```kotlin
import androidx.compose.material3.RichTimePickerDialog
import androidx.compose.material3.TimePickerDialog
import androidx.compose.material3.TimePickerDialogDefaults
import androidx.compose.material3.TimePickerDialogDefaults.MinHeightForTimePicker
import androidx.compose.material3.rememberTimePickerState
```

What the hand-rolled docs-site version loses:

- **`state.isInputValid` gating on the confirm button.** In `TimeInput` mode a user can type an
  invalid time; the official dialog disables "Ok" for it. The `AlertDialog` version does not.
- The `MinHeightForTimePicker` height check that switches to `TimeInput` on short screens —
  a dial simply does not fit in landscape on most phones.
- `TimePickerDialogDefaults.Title` / `.DisplayModeToggle`, which carry the correct strings,
  a11y labels and icon for the picker↔input toggle.

Also present in the same file (names + line numbers confirmed, **bodies UNVERIFIED**):
`TimePickerSample` (61), `TimeInputSample` (109), `RichTimePickerSample` (226 — uses
`TimePicker(state = state, shapes = TimePickerDefaults.shapes())`), `RichTimeInputSample` (269),
`RichTimePickerSwitchableSample` (312).

**Cross-reference:** `TimePickerState` itself — including alpha26's new `initialSelection`
parameter and the selection-mode-survives-restoration change — is documented in
`m3-expressive-components/references/sliders-and-inputs.md` §7 ("Time pickers"). This section owns the
**dialog**; that one owns the **state**. Do not duplicate state guidance here.

---

## 11. Menus

Expressive menu APIs were **promoted in alpha19** **[verified]**. The surface, with versions:

| API | Version | Note |
| --- | --- | --- |
| `DropdownMenu` / `DropdownMenuItem` | baseline | `supportingText` moved **after** `trailingIcon` in alpha20 — a positional-argument break |
| `DropdownMenuItemTrailingLabel` | alpha16 | the right way to show a shortcut/meta label at the end of an item |
| `MenuDefaults.DropdownMenuGroupLabel` | alpha23 | **renamed from `MenuDefaults.Label`** — section header inside a menu |
| `MenuDefaults.selectableItemColors` / `selectableItemVibrantColors` | alpha19 | `disabledContainerColor` added alpha23 |
| `DropdownMenuPopupPositionProviders` + **submenu support** | alpha18 | nested menus without hand-rolled popups |
| `MenuItems(horizontalArrangement = …)` | alpha21 | |
| `MenuAnchorPosition` collapsed to a single class; `MenuAnchorPositionScope` | alpha22 | `MenuAnchorPosition.PrimaryEditable`-style constants changed shape |
| `DropdownMenuItem(… trailingIcon = )` → **`trailingContent = `** | **alpha25** | **shape / checked / selected overloads only** — see §11.1 |
| New selection + container properties on `MenuItemColors` | **alpha25** | see §11.2 |
| `ExposedDropdownMenu` becomes an **extension function** | **alpha26** | import change — see §11.3 |
| `MenuDefaults.itemColors` partial-customization fix | **alpha26** | unprovided colours are no longer clobbered to `Color.Unspecified` (Iea847). A theme that looked "fine" because of the bug may now render differently. No opt-out. |
| `ExposedDropdownMenu` crash fix when screen height < menu height | **alpha26** | I6adf1 |

**Nothing in the corpus uses any of these.** The only menu usage across all four repos is
`ExposedDropdownMenuDefaults.TrailingIcon(expanded = …)` inside `ExposedDropdownMenuBox`, which is
baseline M3. Treat the table above as the API listing, not as validated code.

### 11.1 `trailingIcon` → `trailingContent` (alpha25, I2ecbd)

**[verified]** at the alpha26 SHA `4d087bd6f764b8425a70fd94102f855aa382d94b`.

This is a **per-overload** change, not a global rename, and getting that wrong is the trap:

- The **shape**, **checked** and **selected** `DropdownMenuItem` overloads renamed
  `trailingIcon` → **`trailingContent`**.
- The **plain** overload (`text, onClick, modifier, leadingIcon, trailingIcon, enabled, colors,
  contentPadding, interactionSource`), declared `public expect fun`, **still uses `trailingIcon`**.

So a find-and-replace across your codebase will break the plain call sites. Binary compatibility is
maintained (the old forms survive as `DropdownMenuItemLegacy` at `DeprecationLevel.HIDDEN` with
`@JvmName("DropdownMenuItem")`), but **source breaks if you passed `trailingIcon = ` by name** to
one of those three overloads.

```kotlin
// [ANDROIDX] verified — the new shape overload
@JvmName("DropdownMenuItemNew")
@Composable
public fun DropdownMenuItem(
    onClick: () -> Unit,
    text: @Composable () -> Unit,
    shape: Shape,
    modifier: Modifier = Modifier,
    leadingIcon: @Composable (() -> Unit)? = null,
    trailingContent: @Composable (() -> Unit)? = null,
    supportingText: @Composable (() -> Unit)? = null,
    enabled: Boolean = true,
    colors: MenuItemColors = MenuDefaults.itemColors(),
    horizontalArrangement: Arrangement.Horizontal = MenuDefaults.DropdownMenuItemHorizontalArrangement,
    contentPadding: PaddingValues = MenuDefaults.DropdownMenuSelectableItemContentPadding,
    interactionSource: MutableInteractionSource? = null,
)
```

The checked and selected overloads mirror this: `checked`/`onCheckedChange` + `checkedLeadingIcon`,
and `selected`/`onClick` + `selectedLeadingIcon`, both taking `shapes: MenuItemShapes` and
defaulting `colors` to `MenuDefaults.selectableItemColors()`.

### 11.2 `MenuItemColors` gained selection and container colours (alpha25, I20b92)

Renamed, old names deprecated with `ReplaceWith` (warns, does not break):

| Old | New |
| --- | --- |
| `MenuItemColors.trailingIconColor` | `.trailingContentColor` |
| `MenuItemColors.disabledTrailingIconColor` | `.disabledTrailingContentColor` |

Added: `containerColor`, `disabledContainerColor`, `selectedContainerColor`, `selectedTextColor`,
`selectedLeadingIconColor`, `selectedTrailingContentColor`.

**PARTIAL verification** — the property *names* were read from source, but the declaration
formatting was normalized during extraction. Treat the member list as authoritative and the exact
constructor layout as **UNVERIFIED**.

Pair this with the alpha26 `MenuDefaults.itemColors` bug fix (Iea847): before alpha26, partially
customizing `itemColors` overwrote every colour you *didn't* pass with `Color.Unspecified`. Menus
that looked correct only because of that behaviour will change appearance on alpha26. Re-check any
menu where you passed a subset of colours.

### 11.3 `ExposedDropdownMenu` is now an extension function (alpha26, Ie8a65, b/356452026)

**[verified]** The receiver is `ExposedDropdownMenuBoxScope` — which is a `public sealed class`, not
an interface:

```kotlin
public sealed class ExposedDropdownMenuBoxScope { ... }

public fun ExposedDropdownMenuBoxScope.ExposedDropdownMenu(
    expanded: Boolean,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    scrollState: ScrollState = rememberScrollState(),
    matchAnchorWidth: Boolean = true,
    shape: Shape = MenuDefaults.shape,
    containerColor: Color = MenuDefaults.containerColor,
    tonalElevation: Dp = MenuDefaults.TonalElevation,
    shadowElevation: Dp = MenuDefaults.ShadowElevation,
    border: BorderStroke? = null,
    content: @Composable ColumnScope.() -> Unit,
)
```

**Call sites inside `ExposedDropdownMenuBox { … }` look identical.** The only change is that you may
need an explicit import — this is exactly what the release note means by "You may need to update
your code with a new import":

```kotlin
import androidx.compose.material3.ExposedDropdownMenu
```

**Wildcard imports (`androidx.compose.material3.*`) are unaffected.** The old member version
survives at `DeprecationLevel.HIDDEN`, so binary compatibility holds but source does not.

Since `ExposedDropdownMenuBox` is the *only* menu API any corpus app touches, this is the one menu
change most likely to bite an upgrade in practice.

**[canonical-form — compile-check]** shape of an expressive grouped menu:

```kotlin
DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
    MenuDefaults.DropdownMenuGroupLabel { Text("Sort by") }
    DropdownMenuItem(
        text = { Text("Date added") },
        onClick = { … },
        leadingIcon = { Icon(Icons.Filled.Schedule, null) },
        trailingIcon = { DropdownMenuItemTrailingLabel { Text("⌘1") } },
    )
}
```

**Pitfalls:**
- If you are on alpha22 or earlier, the name is `MenuDefaults.Label`, not `DropdownMenuGroupLabel`.
- `DropdownMenuItem`'s `supportingText`/`trailingIcon` order changed in alpha20 — always pass those
  by name. On **alpha25+** the parameter on the shape/checked/selected overloads is
  **`trailingContent`**, so the snippet above needs `trailingContent = { … }` if you add a `shape`
  argument. The plain overload shown keeps `trailingIcon` (§11.1).
- Submenus need `DropdownMenuPopupPositionProviders` (alpha18+); on older versions there is no
  supported submenu and you should flatten the menu instead of nesting popups.
- On **alpha26**, add `import androidx.compose.material3.ExposedDropdownMenu` if you use explicit
  (non-wildcard) imports (§11.3).

---

## 12. Design guidance and anti-patterns

### Containment and grouping

**[CANON]** "Group similar content into informative groupings." "Ungrouped information can blend
together."

Practical rules **[judgment]**:
1. One container per concept. A settings screen is 3-5 segmented groups separated by
   `Spacer(Modifier.height(12.dp))`, each with its own `index`/`count` run — not one 20-row list and
   not 20 cards.
2. Group boundaries should mean something. If you cannot name the group, it should not be a group.
3. Inside a group, keep row heights uniform. The Expressive list item's height varies with text
   lines (alpha23 flag); either give every row the same number of supporting lines or accept the
   ragged look deliberately.
4. `Arrangement.spacedBy(ListItemDefaults.SegmentedGap)` — never a literal dp. That token is what
   makes the 4dp inner corners read as connected rather than merely close.
5. Surface tiers do the separating: group container one step above the screen background
   (`surface` → `surfaceContainer`, or Tomato's `surfaceBright` on a `surfaceContainer` screen).

### When a segmented list beats separate cards

| Signal | Verdict |
| --- | --- |
| Items are parallel options in one decision | Segmented list |
| Items each carry an image, a body, and their own actions | Cards |
| The list is selectable and one item is "current" | Segmented list — the selected shape morph is free hierarchy |
| Items need to be reordered or swiped away | Cards or a plain list; segmented corners fight reordering |
| More than ~3 items | Segmented list |

### Anti-patterns

| Don't | Instead |
| --- | --- |
| `getGroupedShape(0, 1)` returning a top-rounded shape | Handle `count <= 1` first, return a fully rounded shape |
| Literal 2.dp between segmented items | `ListItemDefaults.SegmentedGap` |
| `ListItemDefaults.colors()` on a **selectable** segmented list | `ListItemDefaults.segmentedColors(...)` |
| `Modifier.clip(...)` on a carousel item | `Modifier.maskClip(...)` from `CarouselItemScope` |
| wrap-content carousel item width | A concrete `preferredItemWidth` / `itemWidth` in dp **[verified rule]** |
| `setShowSheet(false)` to dismiss a sheet | `scope.launch { state.hide() }.invokeOnCompletion { setShowSheet(false) }` |
| `rememberModalBottomSheetState` on alpha20+ | `rememberBottomSheetState` |
| `MenuDefaults.Label` on alpha23+ | `MenuDefaults.DropdownMenuGroupLabel` |
| Global find-and-replace of `trailingIcon` → `trailingContent` in menus | Only the shape/checked/selected `DropdownMenuItem` overloads renamed; the plain one did not (§11.1) |
| Hand-roll a `TimePickerDialog` from `AlertDialog` (as the docs site does) | `androidx.compose.material3.TimePickerDialog` + `TimePickerDialogDefaults` (§10.1) |
| A `count == 1` clip workaround on a segmented list, on alpha25+ | `ListItemDefaults.segmentedShapes` handles it; the clip suppresses the press morph |
| `Modifier.shadow(elevation)` for a two-layer or coloured shadow | Stacked / brush-driven `Modifier.dropShadow` (§6.1) |
| Elevated cards everywhere for separation | Surface tiers + shape + spacing; `elevation = CardDefaults.cardElevation(0.dp)` |
| Abstract `MaterialShapes` polygons as list-item containers | Keep unconventional shapes to decorative moments (avatars, media); "used sparingly in core components" |
| A hero shape treatment on every row | Expression is relational — if every row morphs, none of them reads as emphasized |

### Accessibility

- **Touch targets ≥48×48dp, separated by ≥8dp** **[verified — Material accessibility guidance]**.
  A 2dp `SegmentedGap` is a *visual* gap between rows whose targets are already ≥48dp tall — that is
  fine. Do not shrink row height to make a group more compact.
- Segmented items that carry selection must expose it. The `selected` overload does this for you;
  if you paint selection yourself (Med's approach: bold text + primary colour + check icon), add
  `Modifier.semantics { selected = isSelected }` or use the selectable overload.
- Icon-only `trailingContent` (a chevron) should have `contentDescription = null` — the row's label
  already names the action. A check mark that *is* the state needs a description
  (Tomato passes `stringResource(Res.string.selected)`) or a `stateDescription` on the row.
- Group a segmented list with `Modifier.semantics { isTraversalGroup = true }` so TalkBack treats it
  as a unit.
- `ExpandedFullScreenSearchBar`, `ModalBottomSheet` and dialogs are all focus traps: verify back
  gesture, predictive back, and that focus enters and returns correctly.
- Carousels auto-scroll nothing by default — if you add auto-advance, it must be pausable/stoppable
  after five seconds **[verified — Material accessibility guidance]**.

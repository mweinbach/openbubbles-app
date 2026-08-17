# M3 Expressive — Decision Trees

**Purpose: stop dithering.** Every tree in this file terminates in a concrete API call you can paste,
not a category name. When two options are close, the tree names a default and says why. Take the
default unless you can state the reason not to.

**Provenance `[API-alpha26]`.** Every `material3` / `material3-adaptive` composable name and
`*Defaults` member name in this file was machine-checked against
`compose/material3/material3/api/current.txt` and `compose/material3/adaptive/*/api/current.txt`
at the alpha26 / 1.3.0 SHA. Non-material3 APIs (foundation, ui, animation, navigation3,
third-party) were **not** checked; anything that could not be confirmed is flagged `[UNVERIFIED]`
inline at its use site.

All code is valid at **material3 `1.5.0-alpha26`** / **material3-adaptive `1.3.0`**.

**Reading rules**

- A tree's first matching branch wins. Read top to bottom.
- "→" means "you are done, write this."
- `@OptIn(ExperimentalMaterial3ExpressiveApi::class)` is called out **only where it is still
  required**. Most of the surface graduated between alpha19 and alpha26. Do not add it elsewhere.
- Deep API detail lives in the sibling skills; this file cross-references rather than duplicates.

---

## 0. The three questions that come before every tree

Answer these once per screen. They short-circuit most of what follows.

| # | Question | If the answer is… |
| --- | --- | --- |
| 1 | **What is this screen's single job?** | Browse/media → size + shape levers. Dense data/settings → containment + color, motion-quiet. Single-purpose action (timer, player, camera) → motion contrast. |
| 2 | **Does this screen own a hero moment?** | Yes → §10 picks it, and everything else on the screen holds baseline. No → every tree below takes its *calm* branch. |
| 3 | **What is the window width bucket?** | Compact/Medium → single pane, bottom nav, one action container. Expanded+ → panes, rail, room for a vertical toolbar. §5 and §6. |

---

## 1. Which button?

### 1.1 Emphasis ladder — pick the treatment first

Exactly **one** rank-1 button per view. Everything else steps down.

| Rank | Composable | Use for |
| --- | --- | --- |
| 1 | `Button` (filled) | The single most important action on the view. |
| 2 | `FilledTonalButton` | Important, not *the* action. The safe second button in a two-button row. |
| 3 | `ElevatedButton` | A tonal button that needs separation from a busy or image background. Elevation is for separation, never for emphasis. |
| 4 | `OutlinedButton` | Medium emphasis needing a visible boundary. Pairs with a filled button. |
| 5 | `TextButton` | Dismiss, cancel, tertiary link. |

### 1.2 Size — pick the height second

The five Expressive heights, and what each is actually for:

| Size | Height | `ButtonDefaults` constant | When |
| --- | --- | --- | --- |
| XSmall | **32dp** | `ExtraSmallContainerHeight` | Inline action inside a list row, card, or dense header. **Must still carry a 48dp touch target** (§ accessibility). |
| Small | **40dp** | `MinHeight` (there is no `SmallContainerHeight`) | The default. Everything that used to be a plain `Button`. |
| Medium | **56dp** | `MediumContainerHeight` | Section-primary action; connected-group members carrying icon + label; bottom-sheet confirm. |
| Large | **96dp** | `LargeContainerHeight` | **Hero control. One per screen.** Play, start, the one CTA on an onboarding page. |
| XLarge | **136dp** | `ExtraLargeContainerHeight` | **Hero control, essentially alone.** Media transport on a player; the one action a purpose-built screen exists for. |

**Decision:**

```
Is this the screen's hero action AND the screen has no other hero?
  YES → Large (96dp), or XLarge (136dp) if it stands alone on the screen
  NO  ↓
Does it sit inside a row / card / list item, competing for horizontal space?
  YES → XSmall (32dp) with an expanded touch target
  NO  ↓
Is it the primary action of a section or a sheet?
  YES → Medium (56dp)
  NO  → Small (40dp) — the default
```

**The sizing mechanism — feed the same `Dp` to every helper.** Do not mix a Medium height with Small
padding.

```kotlin
Button(
    onClick = onStart,
    modifier = Modifier.heightIn(ButtonDefaults.MediumContainerHeight),
    shapes = ButtonDefaults.shapesFor(ButtonDefaults.MediumContainerHeight),
    contentPadding = ButtonDefaults.contentPaddingFor(
        ButtonDefaults.MediumContainerHeight,
        hasStartIcon = true,
    ),
) {
    Icon(Icons.Filled.PlayArrow, contentDescription = null,
        modifier = Modifier.size(ButtonDefaults.MediumIconSize))
    Spacer(Modifier.width(ButtonDefaults.MediumIconSpacing))
    Text("Start", style = ButtonDefaults.textStyleFor(ButtonDefaults.MediumContainerHeight))
}
```

Use the **3-arg** `contentPaddingFor(height, hasStartIcon, hasEndIcon)` — the 1-arg overload was
re-marked experimental in alpha21. Never write `Modifier.size(ButtonDefaults.IconSize)`: that
constant is the 18dp legacy baseline and is smaller than every Expressive size constant.

### 1.3 One action, several actions, or a stateful action?

```
How many actions, and what is their relationship?

ONE action
  ├─ It is a normal action                       → Button(onClick, shapes = ButtonDefaults.shapes())
  ├─ It is the screen's primary create/compose action, floating
  │    ├─ needs a label                          → ExtendedFloatingActionButton(...)
  │    └─ icon only                              → FloatingActionButton / MediumFloatingActionButton
  │                                                / LargeFloatingActionButton
  └─ It has ONE obvious default plus variants    → SplitButtonLayout (§1.5)

ONE action with binary state (on/off, saved/unsaved, shuffle)
  └─ Should it read as a BUTTON (not a switch/checkbox)?
       YES → ToggleButton / FilledTonalToggleButton / OutlinedToggleButton / ElevatedToggleButton
       NO  → Switch (a setting) or Checkbox (a selection in a form)

2–5 actions that are ALTERNATIVES in one set (segmented control)
  → connected group: Row + ButtonGroupDefaults.ConnectedSpaceBetween (§2.2)

2–5 INDEPENDENT actions
  → standard group: Row + ButtonGroupDefaults.HorizontalArrangement (12dp), or ButtonGroup (§2.2)

2–6 related actions launched from the FAB
  → FloatingActionButtonMenu + ToggleFloatingActionButton (§2.4)

7+ actions
  → not a button problem. Bottom sheet, menu, or a redesign. (§2)
```

### 1.4 Toggle button — the shape parameter (the alpha25 break)

```kotlin
// Default shape set, derived from the button's height:
shapes = ToggleButtonDefaults.shapesFor(ButtonDefaults.MinHeight)

// Customising: use the CONSTRUCTOR. ToggleButtonDefaults.shapes(...) is DeprecationLevel.HIDDEN
// on alpha25+ — it does not warn, it fails to resolve.
shapes = ToggleButtonShapes(
    shape = MaterialTheme.shapes.large,
    pressedShape = MaterialTheme.shapes.medium,
    checkedShape = CircleShape,
)
```

`ToggleButtonShapes` has **three** slots — `shape`, `pressedShape`, `checkedShape`. That third slot
is what makes checked-ness read as a shape change and not only a color change. There is no zero-arg
`shapesFor()`.

**A `ToggleButton` used as a plain action** (to get connected-group shapes and the press morph on
something with no toggle state) is a real, corpus-wide idiom — but it changes the accessibility role:

```kotlin
ToggleButton(
    checked = false,
    onCheckedChange = { onPlayRadio() },
    modifier = Modifier.semantics { role = Role.Button },   // NOT optional
    shapes = ButtonGroupDefaults.connectedMiddleButtonShapes(),
) { Text("Radio") }
```

Base `ToggleButton` variants need **no** opt-in on alpha19+. The **size variants** (XSmall / Medium /
Large toggle buttons) still carry `@ExperimentalMaterial3ExpressiveApi` at alpha26.

### 1.5 Split button

Use it when there is **one obvious default action plus related variants** — the leading button must
be worth pressing on its own. If there is no clear default, use a menu button or a button group.

```kotlin
var menuOpen by remember { mutableStateOf(false) }
val rotation by animateFloatAsState(if (menuOpen) 180f else 0f,
    animationSpec = MaterialTheme.motionScheme.fastSpatialSpec(), label = "chevron")

SplitButtonLayout(                             // current name at alpha26; no SplitButton exists
    leadingButton = {
        SplitButtonDefaults.LeadingButton(onClick = onExport) {
            Icon(Icons.Filled.Download, contentDescription = null,
                modifier = Modifier.size(SplitButtonDefaults.LeadingIconSize))
            Spacer(Modifier.width(ButtonDefaults.IconSpacing))
            Text("Export")
        }
    },
    trailingButton = {
        SplitButtonDefaults.TrailingButton(
            checked = menuOpen,
            onCheckedChange = { menuOpen = it },
            modifier = Modifier.semantics {
                stateDescription = if (menuOpen) "Expanded" else "Collapsed"
                contentDescription = "More export options"
            },
        ) {
            Icon(Icons.Filled.KeyboardArrowDown, contentDescription = null,
                modifier = Modifier.size(SplitButtonDefaults.TrailingIconSize)
                    .graphicsLayer { rotationZ = rotation })
        }
    },
)
```

No opt-in on alpha20+. Tonal variants: `SplitButtonDefaults.TonalLeadingButton` / `TonalTrailingButton`.

The composable is `SplitButtonLayout` on every pin, including alpha26, and it is not deprecated —
verified in `compose/material3/material3/api/current.txt` at androidx HEAD `360e8cba`, 2026-08-14.
**Do not write `SplitButton(`; no such composable exists.** If you pass a `CornerSize` to
`SplitButtonDefaults.leadingButtonShapes(...)` / `trailingButtonShapes(...)`, note those helpers are
`@Deprecated` — use `leadingButtonShapesFor(buttonHeight: Dp)` / `trailingButtonShapesFor(...)`, or
build a `SplitButtonShapes(shape, pressedShape, checkedShape)` directly.

### 1.6 FAB vs extended FAB vs FAB menu

| Situation | Answer |
| --- | --- |
| One primary create action, icon speaks for itself | `FloatingActionButton` (or `MediumFloatingActionButton` / `LargeFloatingActionButton` for weight) |
| One primary create action, the icon is ambiguous | `ExtendedFloatingActionButton` |
| 2–6 related creation actions | `FloatingActionButtonMenu` + `ToggleFloatingActionButton` |
| You were about to build a speed dial or stack small FABs | **Don't.** That is exactly what the FAB menu replaces. |
| You want a FAB menu opened by an extended FAB | **Not supported.** "Fab menu is not used with extended FABs." |
| 7+ actions | Bottom sheet. |

Deep API: `${CLAUDE_PLUGIN_ROOT}/skills/m3-expressive-components/references/buttons.md` and
`.../fabs-and-toolbars.md`.

---

## 2. Which container for a set of actions?

This is the tree people get wrong most often. **Group before you decorate** — a loose row of buttons
cannot be made expressive, only loud.

### 2.1 The tree

```
Where do these actions live, and how many are there?

They are the app's TOP-LEVEL DESTINATIONS
  → not actions. Go to §5 (nav container).

They belong to the TOP APP BAR (page-scoped: search, filter, overflow)
  ├─ ≤3 and they always fit          → actions = { IconButton(...) } on the app bar
  └─ variable count, may not fit     → AppBarRow(maxItemCount = 3) { clickableItem(...) }  (§2.5)

They are ALTERNATIVES in one set (view mode, sort key, filter tab)
  ├─ 2–5 members                     → connected group (§2.2), Role.RadioButton
  └─ 6+                              → this is a filter surface, not a group: FilterChip row,
                                        a menu, or a bottom sheet

They are INDEPENDENT actions on the page's content
  ├─ 2–3, tied to one element        → standard group: Row(Arrangement.spacedBy(
  │                                      ButtonGroupDefaults.HorizontalArrangement))
  ├─ 3–6, page-level, contextual     → HorizontalFloatingToolbar (§2.3)
  ├─ 3–6, GLOBAL across pages        → FlexibleBottomAppBar (docked toolbar) (§2.3)
  └─ 7+                              → floating toolbar + overflow menu, or a bottom sheet

They are CREATION actions launched from the FAB
  ├─ 2–6                             → FloatingActionButtonMenu (§2.4)
  └─ 7+                              → ModalBottomSheet

They only appear in a transient mode (multi-select, edit)
  → HorizontalFloatingToolbar shown with AnimatedVisibility, or a contextual top app bar
```

### 2.2 Loose row vs `ButtonGroup` vs hand-built connected group

Three shapes, and they are not interchangeable:

| | Loose `Row` | Hand-built connected group | `ButtonGroup` composable |
| --- | --- | --- | --- |
| Spacing | whatever you typed | `ButtonGroupDefaults.ConnectedSpaceBetween` (**2dp**) | `ButtonGroupDefaults.HorizontalArrangement` (**~12dp**) by default |
| Reads as | N separate things | **one object** | N things that squeeze each other |
| Press interaction | per-button morph | per-button morph | press expands the child, compresses neighbours |
| Overflow | none | none (you handle wrap) | menu or wrap, built in |
| Signature stability 1.4.0 → alpha26 | n/a | **stable** | broke at alpha22 and alpha25 |
| Use for | almost never — go connected or standard | **segmented / single-select controls** | variable action count, or you want the squeeze |

**Default: the hand-built connected group.** It is what shipping apps overwhelmingly use, it survived
every alpha break, and it is the right shape for a segmented control.

```kotlin
val options = listOf("List", "Grid", "Compact")
var selected by remember { mutableIntStateOf(0) }

Row(
    modifier = Modifier.fillMaxWidth(),
    horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween),
) {
    options.forEachIndexed { index, label ->
        ToggleButton(
            checked = selected == index,
            onCheckedChange = { selected = index },
            modifier = Modifier
                .weight(1f)
                .semantics { role = Role.RadioButton },
            shapes = when {
                options.size == 1 -> ButtonGroupDefaults.connectedLeadingButtonShapes()
                index == 0 -> ButtonGroupDefaults.connectedLeadingButtonShapes()
                index == options.lastIndex -> ButtonGroupDefaults.connectedTrailingButtonShapes()
                else -> ButtonGroupDefaults.connectedMiddleButtonShapes()
            },
        ) { Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis) }
    }
}
```

Handle `options.size == 1` explicitly or the single button gets trailing shapes and looks wrong.

**Reach for the `ButtonGroup` composable only when you need overflow or the compression
interaction:**

```kotlin
val sources = remember { List(3) { MutableInteractionSource() } }

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
ButtonGroup(overflowIndicator = { ButtonGroupDefaults.OverflowIndicator(it) }) {
    customItem(
        buttonGroupContent = {
            FilledIconButton(
                onClick = onShare,
                interactionSource = sources[0],
                shapes = IconButtonDefaults.shapes(),
                modifier = Modifier.size(56.dp)
                    .animateWidth(sources[0]),        // 1-arg form: portable across all pins
            ) { Icon(Icons.Filled.Share, contentDescription = "Share") }
        },
        menuContent = { state ->
            DropdownMenuItem(text = { Text("Share") }, onClick = { onShare(); state.dismiss() })
        },
    )
    // …two more customItems
}
```

The `InteractionSource` given to `animateWidth` must be the **same instance** passed to the child's
`interactionSource =` — different instances compile fine and animate nothing. `compressionLimit` is a
**`Dp`** on alpha25+, not `PaddingValues`. Keep the `@OptIn`: the release notes and the source disagree
about whether `ButtonGroup` graduated, and a redundant opt-in costs a warning while a missing one costs
the build.

### 2.3 Floating toolbar vs docked toolbar

| | `HorizontalFloatingToolbar` / `VerticalFloatingToolbar` | `FlexibleBottomAppBar` (docked) |
| --- | --- | --- |
| Spans | floats above content, inset from the edge | full window width |
| For | **contextual** actions for this page's content | **global** actions that are the same across pages |
| Capacity | more actions; can pair with a FAB | fewer; shorter than the old bottom app bar |
| Orientation | horizontal or vertical (vertical suits Expanded widths) | horizontal only |
| Color | `standardFloatingToolbarColors()` or `vibrantFloatingToolbarColors()` | app-bar colors |

```kotlin
var expanded by rememberSaveable { mutableStateOf(true) }
val scrollBehavior = FloatingToolbarDefaults.exitAlwaysScrollBehavior(
    exitDirection = FloatingToolbarExitDirection.Bottom,
)

HorizontalFloatingToolbar(
    expanded = expanded,
    colors = FloatingToolbarDefaults.vibrantFloatingToolbarColors(),
    scrollBehavior = scrollBehavior,
    leadingContent = { IconButton(onClick = onUndo) { Icon(Icons.Filled.Undo, "Undo") } },
    trailingContent = { IconButton(onClick = onDelete) { Icon(Icons.Filled.Delete, "Delete") } },
    modifier = Modifier.align(Alignment.BottomCenter)
        .offset(y = -FloatingToolbarDefaults.ScreenOffset),
) {
    // `content` is always visible; leading/trailing show only when expanded == true
    IconButton(onClick = onEdit) { Icon(Icons.Filled.Edit, "Edit") }
}
```

Put the one indispensable action in `content`, the collapsible ones in the leading/trailing slots. The
toolbar force-expands and disables `scrollBehavior` under an accessibility service — intentional; do
not fight it. **`BottomAppBar` is deprecated at the design level**; new code uses a docked or floating
toolbar.

### 2.4 FAB menu

```kotlin
var fabExpanded by rememberSaveable { mutableStateOf(false) }
BackHandler(enabled = fabExpanded) { fabExpanded = false }   // required — the component won't

Scaffold(floatingActionButton = {
    Box(Modifier.wrapContentSize(unbounded = true)) {        // required — or the menu is clipped
        FloatingActionButtonMenu(
            expanded = fabExpanded,
            button = {
                ToggleFloatingActionButton(
                    checked = fabExpanded,
                    onCheckedChange = { fabExpanded = it },
                    modifier = Modifier
                        .semantics {
                            traversalIndex = -1f
                            stateDescription = if (fabExpanded) "Expanded" else "Collapsed"
                            contentDescription = "Create"
                        }
                        .animateFloatingActionButton(visible = true,
                            alignment = Alignment.BottomEnd),
                ) {
                    val icon by remember {
                        derivedStateOf {
                            if (checkedProgress > 0.5f) Icons.Filled.Close else Icons.Filled.Add
                        }
                    }
                    Icon(icon, contentDescription = null,
                        modifier = Modifier.animateIcon({ checkedProgress }))
                }
            },
        ) {
            actions.forEach { a ->                            // 2–6 of these
                FloatingActionButtonMenuItem(
                    onClick = { fabExpanded = false; a.run() },
                    icon = { Icon(a.icon, contentDescription = null) },
                    text = { Text(a.label) },
                )
            }
        }
    }
}) { padding -> /* … */ }
```

Hard constraints: **2–6 items**; opens in the same place as its FAB; never with an
`ExtendedFloatingActionButton`.

### 2.5 App-bar overflow

```kotlin
TopAppBar(
    title = { Text("Library") },
    actions = {
        AppBarRow(maxItemCount = 3) {      // the real max is 3 − 1: one slot goes to the indicator
            clickableItem(onClick = onSearch,
                icon = { Icon(Icons.Filled.Search, contentDescription = null) }, label = "Search")
            clickableItem(onClick = onFilter,
                icon = { Icon(Icons.Filled.FilterList, contentDescription = null) }, label = "Filter")
            clickableItem(onClick = onSettings,
                icon = { Icon(Icons.Filled.Settings, contentDescription = null) }, label = "Settings")
        }
    },
)
```

`AppBarRow` is gated by `ExperimentalMaterial3Api`, **not** the Expressive annotation, and graduated at
alpha23 — no opt-in at alpha26. `AppBarColumn` is the vertical analogue.

### 2.6 Item-count thresholds, consolidated

| Container | Min | Max | Above the max |
| --- | --- | --- | --- |
| Connected button group | 2 | 5 | Filter chips, menu, or a sheet |
| Standard button group | 2 | 4 | Floating toolbar |
| FAB menu | 2 | **6** (spec) | `ModalBottomSheet` |
| Floating toolbar | 2 | ~6 visible | Overflow menu — never let it exceed the window/pane edge |
| Docked toolbar | 2 | ~5 | Overflow menu |
| App bar actions | 1 | 3 | `AppBarRow` overflow |
| `ShortNavigationBar` | 3 | **5** | Rail, drawer, or a "More" destination |

---

## 3. Which progress indicator?

### 3.1 The tree

```
Is the wait under ~5 seconds AND indeterminate for its whole life?
  YES ↓
      Does the indicator sit on top of content that needs a backdrop, or must it
      match sibling 48dp states?
        YES → ContainedLoadingIndicator()
        NO  → LoadingIndicator()
  NO ↓
Does the process ever become determinate (a download that starts as "connecting…")?
  YES → use a determinate progress indicator FROM THE START. Never swap a LoadingIndicator
        mid-flight for a progress bar.
  NO ↓
Do you know the fraction complete?
  YES → determinate:   LinearWavyProgressIndicator(progress = { f })
                     / CircularWavyProgressIndicator(progress = { f })
                     / LinearProgressIndicator(progress = { f })
                     / CircularProgressIndicator(progress = { f })
  NO  → indeterminate: the same four, minus the `progress` argument
  ↓
Is the container a list/grid whose shape you already know, and the wait is a first load?
  → skeleton placeholders sized to the final content (reserve the height; never let layout jump)
```

### 3.2 Wavy or not

| Indicator size | Answer |
| --- | --- |
| **≥ 80dp** (hero ring, full-width bar on a media screen) | Wavy. This is what the component is for. Tune `wavelength ≈ ring / 5–6` and raise `gapSize` to 8dp at thick strokes. |
| **40–80dp** | Wavy still reads. Keep `stroke ≥ 4dp`; you may need `gapSize = 3.dp` to keep the ring from closing up. |
| **< 40dp** | **Not wavy.** "At very small sizes, the wavy shape may not be as visible." Use `CircularProgressIndicator` / `LinearProgressIndicator`. |

Wavy also has to *mean* something. The best use is a **mode signal** — a smooth ring for one state
and a wavy ring for another. Wave applied "for consistency" costs complexity and buys no signal.

```kotlin
val density = LocalDensity.current
val stroke = remember(density) {
    Stroke(width = with(density) { 16.dp.toPx() }, cap = StrokeCap.Round)
}

CircularWavyProgressIndicator(
    progress = { fraction },                 // lambda, not a value — keeps recomposition at the leaf
    modifier = Modifier.size(280.dp),
    stroke = stroke,
    trackStroke = stroke,
    wavelength = 48.dp,                      // Dp
    gapSize = 8.dp,                          // Dp
)
```

**`stroke` and `trackStroke` are `Stroke`, whose `width` is a `Float` in PIXELS.** `wavelength`,
`gapSize`, `stopSize` are `Dp`. `Stroke(width = 8f)` written meaning "8dp" is a hairline at 3×
density. Convert with `LocalDensity`, and `remember` the result.

The default `indicatorAmplitude` flattens the wave to zero below 10% and above 95% progress. If your
bar looks flat at both ends, that is by design — pass `amplitude = { 1f }` to override. Note the
determinate overload takes `amplitude: (Float) -> Float` and the indeterminate one takes a plain
`Float`; passing `amplitude = 1f` to the determinate overload will not compile.

### 3.3 Consistency and opt-in

- **"Only one type should represent each kind of activity in an app."** Do not use a linear bar for
  network fetches on one screen and a circular spinner for the same class of fetch on another.
- `LinearWavyProgressIndicator` / `CircularWavyProgressIndicator` need **no opt-in** (graduated
  alpha18).
- `LoadingIndicator` / `ContainedLoadingIndicator` / `LoadingIndicatorDefaults` **still require
  `@OptIn(ExperimentalMaterial3ExpressiveApi::class)` at alpha26.** Their alpha18 promotion was
  reverted in alpha19 and never restored.

Deep API: `${CLAUDE_PLUGIN_ROOT}/skills/m3-expressive-components/references/progress-and-loading.md`.

---

## 4. Which app bar?

### 4.1 The tree

```
Is this a top-level destination whose headline should carry weight?
  YES → LargeFlexibleTopAppBar(title, subtitle, scrollBehavior = exitUntilCollapsedScrollBehavior())
  NO  ↓
Is it a secondary/detail page, or a page that is usually scrolled?
  YES → TopAppBar(title = …, subtitle = …, scrollBehavior = enterAlwaysScrollBehavior())
  NO  ↓
Do you want a centred title (brand screen, single-purpose screen)?
  YES → TopAppBar(title = …, subtitle = {},
                  titleHorizontalAlignment = Alignment.CenterHorizontally)
        (the subtitle OVERLOAD is what carries titleHorizontalAlignment — pass subtitle = {})
  NO  ↓
Do you need a genuinely custom two-row bar (an image row, a filter row under the title)?
  YES → TwoRowsTopAppBar — the primitive under the flexible bars. Compile-check it first.
  NO  → TopAppBar(title = …)
```

**Do not write `MediumTopAppBar` or `LargeTopAppBar` in new code** — the flexible variants replace
them. `MediumFlexibleTopAppBar` sits between; skip it unless you have a specific reason. The
medium/large distinction is subtle and two heights is one more thing to maintain.
`CenterAlignedTopAppBar` is superseded by the `titleHorizontalAlignment` overload.

### 4.2 When does a subtitle earn its place?

Add a subtitle when it is **semantic**, not decorative:

| Earns it | Does not |
| --- | --- |
| A breadcrumb — "Settings" above "Appearance" | Restating the title in other words |
| Live state — "12 items · 3 selected" | A tagline or marketing line |
| Scope — "Shared with 4 people" | Anything the body content says immediately below |

Subtitles default to `onSurfaceVariant`, so they cost no color decision.

### 4.3 Scroll behavior

| Behavior | Effect | Use with |
| --- | --- | --- |
| `TopAppBarDefaults.pinnedScrollBehavior()` | Bar stays, container color lifts on scroll | Small bars on dense pages |
| `TopAppBarDefaults.enterAlwaysScrollBehavior()` | Bar leaves on scroll-down, returns immediately on scroll-up | Small bars on long content |
| `TopAppBarDefaults.exitUntilCollapsedScrollBehavior()` | Large/medium bar collapses to the small height and stays | **The flexible bars** — this is the collapsing headline |

Wire it in both places or nothing happens:

```kotlin
val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

Scaffold(
    topBar = {
        LargeFlexibleTopAppBar(
            title = { Text("Appearance",
                style = MaterialTheme.typography.headlineLargeEmphasized) },
            subtitle = { Text("Settings") },
            scrollBehavior = scrollBehavior,
        )
    },
    modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),   // ← the other half
) { padding -> /* … */ }
```

Match the `Scaffold`'s `containerColor` to the bar's container color so the collapse has no seam.

App bars are gated by `ExperimentalMaterial3Api`, not the Expressive annotation, and all of the
above graduated at alpha23 — at alpha26 no opt-in is needed. `AppBarWithSearch` is the exception; it
was re-gated at alpha24 and is still gated.

---

## 5. Which nav container?

### 5.1 By width bucket

| Width | Container | Notes |
| --- | --- | --- |
| Compact (< 600dp) | `ShortNavigationBar` at the bottom | 3–5 destinations, hard limit |
| Medium (600–839dp) | Collapsed `WideNavigationRail`, **or** `ShortNavigationBar` with `arrangement = Centered` + `iconPosition = Start` | Both are canonical; the rail wins if content is wide |
| Expanded (≥ 840dp) | Expanded `WideNavigationRail` | It **replaces the navigation drawer** and is non-modal |
| Any, transient | `ModalWideNavigationRail` | Overlay form, for a menu button |

Compact **height** also matters: a short-and-wide window (phone landscape) wants bottom navigation
for reachability, not a rail.

### 5.2 The tree

```
Do you want the library to make the switch for you?
  YES → NavigationSuiteScaffold(navigationItems = { … }) { screenContent() }
        Default type comes from NavigationSuiteScaffoldDefaults.navigationSuiteType(...):
          compact width                    → ShortNavigationBarCompact
          tabletop posture OR compact height → ShortNavigationBarMedium
          everything else                  → WideNavigationRailCollapsed
        It never returns WideNavigationRailExpanded, NavigationDrawer, or None —
        compute those yourself and pass navigationSuiteType explicitly.
  NO ↓
Do you need custom breakpoints, custom item chrome, or per-destination behaviour?
  YES → hand-roll the bar ⇄ rail switch (§5.3)
  NO ↓
Is the app a single-purpose surface where navigation IS the expressive moment?
  YES → HorizontalFloatingToolbar used AS the nav container (§5.4) — and then there is no nav bar
```

### 5.3 The manual switch

```kotlin
val widthClass = currentWindowAdaptiveInfoV2().windowSizeClass   // NOT currentWindowAdaptiveInfo()

when {
    widthClass.isWidthAtLeastBreakpoint(WindowSizeClass.WIDTH_DP_EXPANDED_LOWER_BOUND) ->
        WideNavigationRail(state = railState) { destinations.forEach { RailItem(it) } }

    widthClass.isWidthAtLeastBreakpoint(WindowSizeClass.WIDTH_DP_MEDIUM_LOWER_BOUND) ->
        ShortNavigationBar(arrangement = ShortNavigationBarArrangement.Centered) {
            destinations.forEach { d ->
                ShortNavigationBarItem(
                    selected = d == current,
                    onClick = { navigate(d) },
                    icon = { Icon(d.icon, contentDescription = null) },
                    label = { Text(stringResource(d.label)) },       // required positional
                    iconPosition = NavigationItemIconPosition.Start,
                )
            }
        }

    else ->
        ShortNavigationBar {
            destinations.forEach { d ->
                ShortNavigationBarItem(
                    selected = d == current,
                    onClick = { navigate(d) },
                    icon = { Icon(d.icon, contentDescription = null) },
                    label = { Text(stringResource(d.label)) },
                )
            }
        }
}
```

Three things this encodes: `currentWindowAdaptiveInfoV2()`, the **largest-first** `when` chain
(§6.4), and `label` as a required positional parameter before `modifier` (pass `label = null` for an
unlabelled item — there is no `alwaysShowLabel`).

### 5.4 Toolbar-as-nav

A `HorizontalFloatingToolbar` full of `ToggleButton`s, used *instead of* a nav bar, is a legitimate
and striking choice — it is one surface, not two. The moment you make it, **you do not additionally
show a `ShortNavigationBar`.** Two "where am I" affordances on one page is the single most common
Expressive regression.

The other half of that rule: **never show a navigation bar and a toolbar on the same page.** Nav bar
on primary destinations; toolbars on subsequent pages with actions. A page needing both is doing two
jobs.

### 5.5 Non-negotiables

- The nav container is **hoisted above** the `NavDisplay` / `NavHost`, never rebuilt inside it.
  Inside, it re-enters on every navigation and kills cross-destination shared elements.
  ```kotlin
  SharedTransitionLayout {
      Scaffold(bottomBar = { ShortNavigationBar { … } }) { padding ->
          NavDisplay(backStack = backStack, modifier = Modifier.padding(padding)) { … }
      }
  }
  ```
- Selected destination and scroll state go in `rememberSaveable`, or a fold/rotate resets them.

Deep API: `${CLAUDE_PLUGIN_ROOT}/skills/m3-expressive-navigation/references/nav-containers.md` and
`${CLAUDE_PLUGIN_ROOT}/skills/m3-adaptive/references/navigation-suite.md`.

---

## 6. Which layout?

### 6.1 The tree — content shape first, size class second

```
What is the relationship between the two bodies of content?

There is only one body of content
  → single pane. A Scaffold + LazyColumn. Do not reach for a scaffold you do not need.

A homogeneous collection with no obvious "detail"
  → FEED: LazyVerticalGrid(columns = GridCells.Adaptive(minSize = 180.dp))
    Use maxLineSpan for headers/dividers. There is no feed scaffold; this is it.

Selecting item A determines WHICH content B shows, and B is meaningful on its own
  → LIST-DETAIL: NavigableListDetailPaneScaffold
    (messaging, contacts, mail, media browsers)

B is a tool/palette/comments panel — meaningless without A
  → SUPPORTING PANE: SupportingPaneScaffold / NavigableSupportingPaneScaffold
    (editor + properties, video + related, doc + comments)

List-detail, plus a third body that is meaningful alongside the detail
  → THREE-PANE: the extraPane slot of ListDetailPaneScaffold at Large/XL widths
```

The list-detail / supporting-pane distinction is **not about layout, it is about meaning**: detail
content stands alone; supporting content does not.

### 6.2 What the size class does to each

| Width | Panes rendered | Directive |
| --- | --- | --- |
| Compact (0) | 1 | `maxHorizontalPartitions = 1` |
| **Medium (600)** | **1** | still 1 — deliberate Material guidance |
| Expanded (840) | 2 | `maxHorizontalPartitions = 2`, 24dp gutter |
| Large (1200) | 3 | 3 partitions, `defaultPanePreferredWidth = 412dp` |
| XL (1600) | 3 | same as Large |

**Medium width is single-pane.** A 700dp window shows ONE pane. If you truly need two,
`calculatePaneScaffoldDirectiveWithTwoPanesOnMediumWidth` exists — and androidx's own KDoc
recommends against it ("can make your layout look too packed").

### 6.3 The list-detail skeleton

```kotlin
val scope = rememberCoroutineScope()
val navigator = rememberListDetailPaneScaffoldNavigator<ItemId>()   // ItemId must be @Parcelize

NavigableListDetailPaneScaffold(                // "Navigable" = predictive back for free
    navigator = navigator,
    defaultBackBehavior = BackNavigationBehavior.PopUntilScaffoldValueChange,
    listPane = {
        AnimatedPane(Modifier.preferredWidth(360.dp)) {
            ItemList(
                selected = navigator.currentDestination?.contentKey,
                onClick = { id ->
                    scope.launch { navigator.navigateTo(ListDetailPaneScaffoldRole.Detail, id) }
                },
            )
        }
    },
    detailPane = {
        AnimatedPane {
            ItemDetail(navigator.currentDestination?.contentKey) {
                scope.launch { navigator.navigateBack() }
            }
        }
    },
)
```

`navigateTo` / `navigateBack` / `seekBack` are **`suspend`**. And note the role trap: in list-detail,
**List = Secondary and Detail = Primary** — see `common-mistakes.md`.

### 6.4 The `when`-chain rule (applies to every size-class branch in this file)

All three predicates are `>=`, so a chain must run **largest → smallest** or every window falls into
the first branch:

```kotlin
val wsc = currentWindowAdaptiveInfoV2().windowSizeClass
when {
    wsc.isWidthAtLeastBreakpoint(WindowSizeClass.WIDTH_DP_EXTRA_LARGE_LOWER_BOUND) -> { /* ≥1600 */ }
    wsc.isWidthAtLeastBreakpoint(WindowSizeClass.WIDTH_DP_LARGE_LOWER_BOUND)       -> { /* ≥1200 */ }
    wsc.isWidthAtLeastBreakpoint(WindowSizeClass.WIDTH_DP_EXPANDED_LOWER_BOUND)    -> { /* ≥840  */ }
    wsc.isWidthAtLeastBreakpoint(WindowSizeClass.WIDTH_DP_MEDIUM_LOWER_BOUND)      -> { /* ≥600  */ }
    else -> { /* compact */ }
}
```

Width has **five** buckets (0 / 600 / 840 / 1200 / 1600); height has **three** (0 / 480 / 900). There
is no `HEIGHT_DP_LARGE_LOWER_BOUND`. There is no `containsWidthDp`.

Deep API: `${CLAUDE_PLUGIN_ROOT}/skills/m3-adaptive/references/pane-scaffolds.md` and
`.../window-size-classes.md`.

---

## 7. Which motion spec?

### 7.1 The decision table — property → family → tier

Two axes. **Family is determined by the property and is not a preference.** Tier is determined by the
size of the thing moving.

| What you are animating | Family | Default tier | Call |
| --- | --- | --- | --- |
| `Dp` size / width / height | Spatial | Default | `motionScheme.defaultSpatialSpec()` |
| Offset / translation / slide | Spatial | Default (Slow if full-screen) | `defaultSpatialSpec()` / `slowSpatialSpec()` |
| Scale (`scaleIn` / `scaleOut` / `graphicsLayer` scale) | Spatial | Default | `defaultSpatialSpec()` |
| Rotation | Spatial | Fast | `fastSpatialSpec()` |
| Corner radius / shape-morph progress | Spatial | **Fast** | `fastSpatialSpec()` |
| Layout bounds (`animateContentSize`, `animateBounds`, `BoundsTransform`) | Spatial | Default | `defaultSpatialSpec()` |
| `expandVertically` / `shrinkVertically` | Spatial | Default | `defaultSpatialSpec()` |
| Lazy-list item placement | Spatial | Default | `defaultSpatialSpec()` |
| **Alpha, `fadeIn` / `fadeOut`** | **Effects** | Default | `defaultEffectsSpec()` |
| **`Color`** (container, content, tint) | **Effects** | Default (Slow for a whole-screen recolor) | `defaultEffectsSpec()` / `slowEffectsSpec()` |
| **Elevation** (a `Dp`, but reads as lighting) | **Effects** | Fast | `fastEffectsSpec()` |
| Blur radius / scrim opacity | Effects | Default | `defaultEffectsSpec()` |
| Cross-fade between contents | Effects | Default | `defaultEffectsSpec()` |

### 7.2 Tier by element scale

| Tier | Canonical scope | Examples |
| --- | --- | --- |
| **Fast** | "Small components like switches and buttons" | press feedback, toggle thumb, chip selection, icon swap, corner morph, elevation change |
| **Default** | "Medium-scale animations like bottom sheets and navigation rails" | FAB-menu open, sheet settle, card expand, list reorder, app-bar title swap |
| **Slow** | "Full-screen animations and content refreshes" | bottom bar slide, full-screen reveal, a mode change that recolors the whole screen |

**A slow bouncy spring on a switch reads as broken.** Tier mismatch is the most common motion defect
after family mismatch.

### 7.3 Combined transitions get one spec per half

The single most-copied line in the corpus:

```kotlin
AnimatedVisibility(
    visible = visible,
    enter = fadeIn(MaterialTheme.motionScheme.defaultEffectsSpec()) +
            scaleIn(MaterialTheme.motionScheme.defaultSpatialSpec(), initialScale = 0.92f),
    exit  = fadeOut(MaterialTheme.motionScheme.defaultEffectsSpec()) +
            scaleOut(MaterialTheme.motionScheme.defaultSpatialSpec(), targetScale = 0.92f),
) { /* … */ }
```

### 7.4 When NOT to use a motion-scheme spec

| Case | Use instead | Why |
| --- | --- | --- |
| Determinate progress | keep `tween` | Progress must be linear; `WavyProgressIndicatorDefaults.ProgressAnimationSpec` is deliberately a `tween` |
| Looping / ambient animation | `infiniteRepeatable` | Motion-scheme specs are `FiniteAnimationSpec` — springs cannot repeat |
| Multi-stop choreography | `keyframes { }` | A spring has one target |
| Timing synced to an external clock (audio, countdown, scrubber) | `tween` / direct value | Springs have no duration you can match |
| "No animation" and gesture tracking | `snap()` | Correct as-is |

### 7.5 Reading the spec

`MaterialTheme.motionScheme` is `@Composable`, so it cannot be read inside a non-composable lambda
(`onDragEnd`, a coroutine body). Hoist it once per screen:

```kotlin
val motionScheme = MaterialTheme.motionScheme
val scope = rememberCoroutineScope()
// motionScheme is now usable inside scope.launch { … } and gesture callbacks
```

Deep API and the exact spring constants:
`${CLAUDE_PLUGIN_ROOT}/skills/m3-expressive-motion/references/motion-scheme.md`.

---

## 8. Which shape treatment?

### 8.1 The tree

```
Does this container hold TEXT or a PHOTO the user must read?
  YES → theme shape scale only. MaterialTheme.shapes.{small…extraExtraLarge}. Stop here.
  NO  ↓
Is this a BUTTON / TOGGLE / ICON BUTTON that should morph on press?
  YES → the `shapes =` parameter. One line, themeable, no custom drawing:
          Button(onClick = …, shapes = ButtonDefaults.shapes())
          IconButton(onClick = …, shapes = IconButtonDefaults.shapes())
          ToggleButton(…, shapes = ToggleButtonDefaults.shapesFor(ButtonDefaults.MinHeight))
  NO  ↓
Is this DECORATIVE — an avatar, an icon backdrop, a badge, empty-state art, media art?
  YES → MaterialShapes polygon, clipped onto the BACKDROP not the content:
          Box(Modifier.size(72.dp).clip(MaterialShapes.Cookie7Sided.toShape())
                  .background(MaterialTheme.colorScheme.secondaryContainer),
              contentAlignment = Alignment.Center) { Icon(…) }
  NO  ↓
Does a REAL STATE CHANGE need to be signalled by a shape change, and no built-in
`shapes =` parameter covers it (a custom Surface, a drag handle, a chart marker)?
  YES → hand-rolled Morph. Remember it; animate only the progress. (§8.3)
  NO  → you do not need a shape treatment. Use the theme scale.
```

### 8.2 The theme scale, and the three Expressive additions

| Token | dp | Reach for it when |
| --- | --- | --- |
| `extraSmall` | 4 | Chips, tight badges |
| `small` | 8 | Inner corners of connected/segmented items |
| `medium` | 12 | Default small container |
| `large` | 16 | Cards, list items — the calm baseline |
| **`largeIncreased`** | **20** | The "one step up" from a 16dp card; input fields |
| `extraLarge` | 28 | Sheets, dialogs, large cards |
| **`extraLargeIncreased`** | **32** | Pressed/selected states of segmented items; a card that must read softer |
| **`extraExtraLarge`** | **48** | Large hero containers |

If you are on Expressive and never touch `largeIncreased` / `extraLargeIncreased` /
`extraExtraLarge`, you are shipping a baseline-M3 shape feel. Note that the `Shapes(...)` five-arg
secondary constructor silently defaults those three — pass them explicitly if you customise.

Nesting rule: **outer radius − padding = inner radius.** A 28dp card with 12dp padding wants a 16dp
inner surface.

### 8.3 Hand-rolled morph — the only correct shape

```kotlin
// @file:OptIn(ExperimentalMaterial3ExpressiveApi::class) — MaterialShapes is still gated at alpha26
val morph = remember { Morph(MaterialShapes.Circle, MaterialShapes.Cookie9Sided) }
val progress by animateFloatAsState(
    targetValue = if (active) 1f else 0f,
    animationSpec = MaterialTheme.motionScheme.fastSpatialSpec(),
    label = "morphProgress",
)
// pass `morph` and `{ progress }` into a custom Shape; call morph.toPath(progress, path)
// inside createOutline. Morph.toPath is NOT @Composable, and there is no Morph.toShape().
```

`Morph` construction runs a feature-mapping algorithm between two polygons. Constructing it inside a
`Shape.createOutline` body or an animated composable runs it **every frame**.

### 8.4 Guard rails

- **Only cookies 4, 6, 7, 9, 12 exist.** `Cookie5Sided` / `Cookie8Sided` / `Cookie10Sided` /
  `Cookie11Sided` are compile errors. Clovers are `Clover4Leaf` and `Clover8Leaf` only.
- Below roughly 40dp, `Burst`, `VerySunny`, `Clover8Leaf` and the high-count cookies read as a blob.
  Fall back to `Circle` or `Pill`.
- **If a morph does not correspond to a state change, delete it.** A decorative morph burns the
  signal a real state morph needs and trips the five-second auto-motion accessibility rule.
- `MaterialShapes`, `RoundedPolygon.toShape()` / `.toPath()` and `Morph.toPath()` **still require
  `@OptIn(ExperimentalMaterial3ExpressiveApi::class)` at alpha26.**

Deep API: `${CLAUDE_PLUGIN_ROOT}/skills/m3-expressive-shapes/references/shapes-catalog.md` and
`.../morph-recipes.md`.

---

## 9. Which list treatment?

### 9.1 The tree

```
Are the rows a SETTINGS / PREFERENCES / PICKER group — a small, bounded set that
belongs together?
  YES → SEGMENTED list items (§9.2). This is the Expressive default for grouped rows.
  NO  ↓
Are the rows an UNBOUNDED feed of equivalent records (mail, tracks, contacts, files)?
  YES → plain ListItem inside a LazyColumn. No cards, no segments — the repetition IS the
        structure, and per-row containers add 200 borders that carry no information.
  NO  ↓
Does each row carry substantial independent content — an image, a summary, its own actions?
  YES → Card (one per record) inside a LazyColumn/LazyVerticalGrid.
        Reach for elevation only to separate from a busy background.
  NO  ↓
Is this a horizontally browsable set the user should SCAN rather than read?
  YES → carousel (§9.3)
  NO  → plain ListItem
```

### 9.2 Segmented

```kotlin
LazyColumn(verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap)) {
    itemsIndexed(options, key = { _, o -> o.id }) { index, option ->
        SegmentedListItem(
            selected = option.id == selectedId,
            onClick = { onSelect(option.id) },
            colors = ListItemDefaults.colors(
                containerColor = MaterialTheme.colorScheme.surfaceContainer),
            shapes = ListItemDefaults.segmentedShapes(index = index, count = options.size),
            trailingContent = {
                if (option.id == selectedId) Icon(Icons.Rounded.Check, contentDescription = null)
            },
        ) { Text(option.label) }
    }
}
```

That is the whole idiom: `Arrangement.spacedBy(ListItemDefaults.SegmentedGap)` +
`shapes = ListItemDefaults.segmentedShapes(index, count)` + one shared `colors`.

**The `ListItem` overload trap:** the deprecated overload takes `headlineContent` as its first
parameter; every Expressive overload takes the headline as the **trailing `content` lambda**. Both
sets are on the classpath, so mixing them produces confusing "no applicable overload" errors.

Expressive list-item APIs graduated at alpha23 (no opt-in at alpha26); on 1.4.0 → alpha22 they need
`@OptIn(ExperimentalMaterial3ExpressiveApi::class)`.

### 9.3 Which carousel

| Strategy | Composable | Use when |
| --- | --- | --- |
| Multi-browse (default) | `HorizontalMultiBrowseCarousel(state, preferredItemWidth = 320.dp)` | Scanning many small items fast — a thumbnail gallery |
| Hero | `HorizontalCenteredHeroCarousel(state)` | Considered selection of large media. Sizes the focal item itself — takes no `preferredItemWidth` |
| Uncontained | `HorizontalUncontainedCarousel(state, itemWidth = 200.dp)` | Original aspect ratios matter; edge items may be cut |
| Full-screen | one item at a time | Recommended for vertical orientation in portrait |

```kotlin
val carouselState = rememberCarouselState { items.size }

HorizontalMultiBrowseCarousel(
    state = carouselState,
    preferredItemWidth = 320.dp,
    itemSpacing = 16.dp,
    modifier = Modifier.fillMaxWidth().height(320.dp),   // fix the height explicitly
) { i ->
    ItemCard(
        item = items[i],
        modifier = Modifier.maskClip(MaterialTheme.shapes.extraLarge),   // NOT Modifier.clip
    )
}
```

`Modifier.maskClip` is a `CarouselItemScope` modifier that clips to the carousel's *current* mask, so
corners animate as the item moves between the large and small slots. `Modifier.clip` gives a static
corner and visibly clips wrong at the edges. Carousel item width must be a concrete `dp`, never
wrap-content. Carousels are gated by `ExperimentalMaterial3Api`, not the Expressive annotation.

### 9.4 The cards-vs-segments call, restated

A settings screen rendered as eight separate `Card`s is the most common Expressive misfire. Eight
cards assert eight independent things; a segmented group asserts one group of eight options —
which is what a settings section actually is. Group first, then decorate.

---

## 10. Where does the hero go?

**Budget: one or two hero moments per PRODUCT.** Not per screen, not per component. If the product
already has two, this screen gets none and that is the correct answer.

### 10.1 The procedure

1. **List every visually distinct element on the screen** — app bar, hero card, list, FAB, toolbar,
   each button group.

2. **Score each on four axes, against this screen's own baseline** (not an abstract ideal):

   | Axis | Diverges when… |
   | --- | --- |
   | **Size** | ≥1.5× the median element, or a Large/XLarge button (96/136dp) among Small/Medium (40/56dp) |
   | **Shape** | different corner token from its neighbours, or a `MaterialShapes` polygon among `RoundedCornerShape`s |
   | **Color** | uses `primary` / `tertiary` / a `*Container` where neighbours use `surface*` |
   | **Motion** | has its own animation, morph, or shared-element key while neighbours are static |

3. **Count elements with ≥2 divergent axes.** Those are your hero candidates.
   - **0** → no hero. Correct for a settings sub-page; a failure for a home, library, player or
     landing screen.
   - **1–2** → plausible. Go to step 4.
   - **≥3** → too many. Contrast is relational; four breaks means no break reads as one. Demote.

4. **Both qualifying questions must be YES** for anything you keep:
   - *Is this interaction emotionally impactful?*
   - *Is this a key interaction in your product?*

   A visually interesting element that fails the second question is decoration competing with the
   real hero.

5. **Verify the baseline is actually calm.** If everything is rounded, animated and colorful, the
   hero has nothing to break from — and the fix is **subtractive**, on the neighbours, not additive
   on the hero.

### 10.2 Choosing the hero, in ascending cost

```
Does the screen have ONE naturally dominant object — album art, a chart, a countdown, a photo?
  YES → that object is the hero. Size + shape. It costs nothing; the content already earned it.
  NO  ↓
Is there ONE action the whole screen exists for — play, start, send, capture?
  YES → that action is the hero. Large (96dp) or XLarge (136dp) button, plus a press morph.
  NO  ↓
Is the screen editorial or navigational (home, library, settings root, profile)?
  YES → the HEADLINE is the hero. LargeFlexibleTopAppBar + subtitle + an emphasized type role.
        This is the cheapest legitimate hero in the system: it collapses on scroll, so the
        editorial moment costs no sustained screen real estate.
  NO  ↓
Is there a single delightful interaction that IS the product's identity?
  YES → motion contrast. Shared-element flight, a shape morph on a state change, a container
        transform. Spend it last: it costs frame budget, fights reduced motion, and a wrong
        spring reads as a bug.
  NO  → this screen has no hero. Ship it calm. That is a legitimate outcome.
```

### 10.3 The default hero, spelled out

When nothing else obviously qualifies:

```kotlin
val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

Scaffold(
    topBar = {
        LargeFlexibleTopAppBar(
            title = { Text("Library",
                style = MaterialTheme.typography.headlineLargeEmphasized) },
            subtitle = { Text("248 items") },
            scrollBehavior = scrollBehavior,
        )
    },
    modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
) { padding -> LibraryContent(Modifier.padding(padding)) }
```

Three axes diverge on one element (size, type weight, motion-on-scroll), everything below holds
baseline, and it costs two slots and no custom drawing.

### 10.4 Lever budget

| Levers on one element | Verdict |
| --- | --- |
| 1 | Normal emphasis. Fine anywhere. |
| 2 | A hero moment. Budget applies. |
| 3–4 | Noise, unless this is *the* one hero on the product's most important screen. |

**Never trade clarity for it.** "No amount of emotion can compensate for a lack of clarity." If an
expressive choice made the screen harder to read, remove it.

---

## 11. Master quick-reference

| Question | Default answer |
| --- | --- |
| Which button treatment? | `Button` for the one primary action; `FilledTonalButton` for everything else that matters |
| Which button size? | Small (40dp, `ButtonDefaults.MinHeight`) |
| Segmented control? | `Row` + `ButtonGroupDefaults.ConnectedSpaceBetween` + `connected*ButtonShapes()` + `Role.RadioButton` |
| 3–6 page actions? | `HorizontalFloatingToolbar` |
| 2–6 create actions? | `FloatingActionButtonMenu` + `ToggleFloatingActionButton` |
| Short indeterminate wait? | `LoadingIndicator()` (needs the Expressive opt-in) |
| Known progress? | `LinearWavyProgressIndicator(progress = { f })` if ≥40dp, else `LinearProgressIndicator` |
| App bar on a top-level page? | `LargeFlexibleTopAppBar` + `subtitle` + `exitUntilCollapsedScrollBehavior()` |
| App bar elsewhere? | `TopAppBar` |
| Nav container? | `NavigationSuiteScaffold`, unless you need custom breakpoints |
| Layout? | Single pane, until content genuinely has a list/detail or primary/supporting relationship |
| Motion spec? | Spatial for anything that moves or resizes; effects for anything that fades or recolors; Default tier |
| Shape? | `MaterialTheme.shapes.large`, plus `shapes = …Defaults.shapes()` on buttons |
| Grouped rows? | `SegmentedListItem` + `ListItemDefaults.segmentedShapes(index, count)` |
| Hero? | The collapsing `LargeFlexibleTopAppBar` headline, if nothing else obviously qualifies |

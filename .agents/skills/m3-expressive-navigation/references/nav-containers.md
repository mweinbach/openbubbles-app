# Navigation containers — ShortNavigationBar, WideNavigationRail, NavigationSuiteScaffold, toolbar-as-nav

Containers only. Back stacks, panes, Navigation3 → `adaptive-and-nav3.md`.

| Tag | Meaning |
| --- | --- |
| `[SRC]` | Verbatim from androidx source / rendered reference docs |
| `[CANON]` | Material design docs, verbatim or near-verbatim |
| `[CORPUS]` | Shipping open-source app — path given above the block |
| `[OFFICIAL]` | Google-authored sample — androidx `@Sampled`, `android/androidify`, `android/ai-samples`. Highest authority for signatures. |
| `[CANONICAL-FORM]` | Established public signature **not** captured in this corpus. Verify against the artifact before relying on it. |
| `[UNVERIFIED]` | Not confirmed against a primary source |

**Sourcing note.** For any container signature, prefer the androidx `material3/samples` module
(`compose/material3/material3/samples/src/main/java/androidx/compose/material3/samples/`) — those are
the `@Sampled` functions embedded into the API reference. The developer.android.com **guide** pages
are stale: `components/navigation-rail` documents only classic `NavigationRail`/`NavigationRailItem`
and contains no `WideNavigationRail`, `ModalWideNavigationRail`, `rememberWideNavigationRailState`,
`WideNavigationRailItem`, `NavigationRailValue` or `ShortNavigationBar` (verified 2026-08-14). Do not
send users there.

---

## 1. Pick the container

| Width class | Container |
| --- | --- |
| Compact (<600dp) | `ShortNavigationBar`, bottom. 3–5 destinations `[CANON]` |
| Medium (600–839dp) | Collapsed `WideNavigationRail`, or `ShortNavigationBar` with `arrangement = Centered` + `iconPosition = Start` `[CANON]` |
| Expanded (≥840dp) | Expanded `WideNavigationRail` — replaces the nav drawer `[CANON]` |
| Overlay | `ModalWideNavigationRail` (§3.1) |

Decide by **window size class**, never device type (§9). `derivedMediaQuery` is a real but
experimental alternative (§9a) — Google's own jetpacker sample uses it instead, at the cost of being
invisible to every adaptive scaffold.

Three shipping strategies, ascending work: **(1) `NavigationSuiteScaffold`** — the recommended
default, do the switch for you; **(2) manual bar ⇄ rail** (Med, §5) for full control;
**(3) floating toolbar / custom container** (Tomato §6, vivi-music §7) for maximum expression and
maximum ownership of behaviour you now have to get right yourself.

---

## 2. `ShortNavigationBar`

`[SRC]`. Opt-in on 1.4.0: `ExperimentalMaterial3ExpressiveApi`.

**Opt-in status — corrected as of 1.5.0-alpha26 (2026-08-12).** Earlier guidance here said "assume
still gated". That was too conservative. Read directly from the alpha26 source tree: the string
`"Experimental"` does **not occur anywhere** in `ShortNavigationBar.kt` or `WideNavigationRail.kt`.
`ShortNavigationBar`, `ShortNavigationBarItem`, `ShortNavigationBarItemDefaults`,
`WideNavigationRail`, `ModalWideNavigationRail` and `WideNavigationRailItem` all declare as plain
`@Composable public fun` with **zero** experimental annotations. The androidx `NavigationRailSamples.kt`
corroborates: 0 × `ExperimentalMaterial3ExpressiveApi`, 4 × `ExperimentalMaterial3Api` (for the
`TooltipBox` inside, not the rail).

**And yet no graduation release note exists anywhere.** Every 1.5.0-alpha section was searched; only
two bullets mention these components at all — alpha26's a11y contrast fix, and alpha20's *"Remove
deprecated experimental `WideNavigationRail` APIs"* (Iaadd6, b/497891040). That alpha20 bullet is the
likely mechanism: stable overloads were added and the experimental ones deleted, so the promotion was
never written up as a promotion.

**Guidance: shipped source shows no annotation; no graduation note was ever published; trust the
compiler at your pin.** If it builds without `@OptIn`, drop it. If your pin predates alpha20 it will
still demand the opt-in — that is expected, not a contradiction. This is a documentation gap in the
release notes, not a research failure, and it is worth re-checking at each new alpha.

⚠️ Do **not** generalise this to `MaterialShapes` or `LoadingIndicator` — those were reverted to
experimental in alpha19 (I30e69) and have **not** been re-promoted through alpha26. Removing their
opt-ins breaks the build.

```kotlin
@Composable
fun ShortNavigationBar(
    modifier: Modifier = Modifier,
    containerColor: Color = ShortNavigationBarDefaults.containerColor,
    contentColor: Color = ShortNavigationBarDefaults.contentColor,
    windowInsets: WindowInsets = ShortNavigationBarDefaults.windowInsets,
    arrangement: ShortNavigationBarArrangement = ShortNavigationBarDefaults.arrangement,
    content: @Composable () -> Unit,
)

@Composable
fun ShortNavigationBarItem(
    selected: Boolean,
    onClick: () -> Unit,
    icon: @Composable () -> Unit,
    label: @Composable (() -> Unit)?,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    iconPosition: NavigationItemIconPosition = NavigationItemIconPosition.Top,
    colors: NavigationItemColors = ShortNavigationBarItemDefaults.colors(),
    interactionSource: MutableInteractionSource? = null,
)
```

**The trap:** `label` is a **required positional** parameter sitting *before* `modifier`. Pass
`label = null` explicitly for an unlabelled item. This is the #1 migration compile error.

`ShortNavigationBarArrangement` `[SRC]`: values include `EqualWeight` (default) and `Centered`.
`[UNVERIFIED whether further members exist.]` `EqualWeight` divides width evenly; `Centered` sizes
items to content and centres the group — pair it with `NavigationItemIconPosition.Start` to get the
icon-beside-label layout `[CANON]` prescribes at ≥600dp.

`NavigationItemIconPosition` `[SRC]`: `Top`, `Start`.

```kotlin
val medium = currentWindowAdaptiveInfo().windowSizeClass
    .isWidthAtLeastBreakpoint(WindowSizeClass.WIDTH_DP_MEDIUM_LOWER_BOUND)

ShortNavigationBar(
    arrangement = if (medium) ShortNavigationBarArrangement.Centered
                  else ShortNavigationBarArrangement.EqualWeight
) {
    destinations.forEach { d ->
        ShortNavigationBarItem(
            selected = d == current,
            onClick = { navigate(d) },
            icon = { Icon(d.icon, contentDescription = null) },
            label = { Text(stringResource(d.label)) },
            iconPosition = if (medium) NavigationItemIconPosition.Start
                           else NavigationItemIconPosition.Top,
        )
    }
}
```

Defaults `[SRC]`, names verified / values not captured: `ShortNavigationBarDefaults.{containerColor,
contentColor, windowInsets, arrangement}`; `ShortNavigationBarItemDefaults.colors()` →
`NavigationItemColors`. That colour type is **shared** with `WideNavigationRailItem` — write one
colours object for both sides of an adaptive switch.

### vs classic `NavigationBar`

`[CANON]` sizing: container **80→64dp**, top padding **12→6dp**, bottom padding **16→6dp**, active
indicator **64→56dp**.

| | `NavigationBar(Item)` | `ShortNavigationBar(Item)` |
| --- | --- | --- |
| Item layout | icon-over-label only | `iconPosition = Top` or `Start` |
| Bar arrangement | always equal-weight | `EqualWeight` / `Centered` |
| `label` | optional | **required positional**, nullable |
| Label visibility | `alwaysShowLabel: Boolean` | no equivalent — pass `label = null`, or use arrangement + icon position |
| Colours | `NavigationBarItemColors` | `NavigationItemColors` (shared with rail) |
| Indicator | static 64dp pill | 56dp pill, animates on the theme motion scheme |
| Opt-in | none | `ExperimentalMaterial3ExpressiveApi` on 1.4.0; **none** in alpha26 source (see above) |

There is no `alwaysShowLabel` escape hatch. `[CANON]` documents four label-visibility modes (auto /
selected-only / always / unlabeled) for the navigation bar; the Compose Expressive API does not
expose them as a parameter `[UNVERIFIED whether a later alpha adds one]`. For selected-only labels
either pass `label = null` on unselected items (the item resizes) or drop to §6/§7.

---

## 3. `WideNavigationRail` / `ModalWideNavigationRail`

`[SRC]`. Opt-in on 1.4.0: `ExperimentalMaterial3ExpressiveApi`; **none in alpha26 source** — same
correction as §2, read it there.

**1.5.0-alpha20 removed the deprecated experimental `WideNavigationRail` APIs** — the overloads
*without* `contentPadding` (rail, modal rail) and *without* `indicatorPadding` (item) are gone. On
1.4.0 you are calling the old ones; upgrading past alpha20 breaks those call sites and you add the
padding params or take the defaults.

**1.5.0-alpha26 changed the rail's content padding: bottom padding is now `0`** (was 44.dp) —
release note I572bb, *"Fixed wrong content padding of wide navigation rail."* Silent visual change,
no compile error. To revert:

```kotlin
WideNavigationRail(contentPadding = PaddingValues(0.dp, 44.dp, 0.dp, 44.dp)) { … }
```

**alpha26 also changed horizontal `NavigationItem` label colour for a11y contrast** (I85855,
b/490910896). The selected label colour is now specified via two properties —
`selectedTextColorTopIconPosition` (vertical item, unchanged) and `selectedTextColorStartIconPosition`
(horizontal item, **now set to match `selectedIconColor`** instead of `secondary`). This affects both
`ShortNavigationBarItem` with `iconPosition = Start` and every expanded `WideNavigationRailItem`. To
revert:

```kotlin
colors = WideNavigationRailItemDefaults.colors()
    .copy(selectedTextColorStartIconPosition = MaterialTheme.colorScheme.secondary)
// same shape on ShortNavigationBarItemDefaults.colors()
```

Both are P2 (visual only) and both have documented one-line opt-outs. If a user reports "my rail
labels changed colour" or "the rail lost its bottom padding" after an upgrade, these are the two
causes.

```kotlin
@Composable
fun WideNavigationRail(
    modifier: Modifier = Modifier,
    state: WideNavigationRailState = rememberWideNavigationRailState(),
    shape: Shape = WideNavigationRailDefaults.shape,
    colors: WideNavigationRailColors = WideNavigationRailDefaults.colors(),
    header: @Composable (() -> Unit)? = null,
    windowInsets: WindowInsets = WideNavigationRailDefaults.windowInsets,
    arrangement: Arrangement.Vertical = WideNavigationRailDefaults.arrangement,
    contentPadding: PaddingValues = WideNavigationRailDefaults.ContentPadding,
    content: @Composable () -> Unit,
)

@Composable
fun ModalWideNavigationRail(
    modifier: Modifier = Modifier,
    state: WideNavigationRailState = rememberWideNavigationRailState(),
    hideOnCollapse: Boolean = false,
    collapsedShape: Shape = WideNavigationRailDefaults.modalCollapsedShape,
    expandedShape: Shape = WideNavigationRailDefaults.modalExpandedShape,
    colors: WideNavigationRailColors = WideNavigationRailDefaults.colors(),
    header: @Composable (() -> Unit)? = null,
    expandedHeaderTopPadding: Dp = 0.dp,
    windowInsets: WindowInsets = WideNavigationRailDefaults.windowInsets,
    arrangement: Arrangement.Vertical = WideNavigationRailDefaults.arrangement,
    expandedProperties: ModalWideNavigationRailProperties =
        WideNavigationRailDefaults.ModalExpandedProperties,
    contentPadding: PaddingValues = WideNavigationRailDefaults.ContentPadding,
    content: @Composable () -> Unit,
)

@Composable
fun WideNavigationRailItem(
    selected: Boolean,
    onClick: () -> Unit,
    icon: @Composable () -> Unit,
    label: @Composable (() -> Unit)?,
    railExpanded: Boolean,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    iconPosition: NavigationItemIconPosition =
        WideNavigationRailItemDefaults.iconPositionFor(railExpanded),
    colors: NavigationItemColors = WideNavigationRailItemDefaults.colors(),
    interactionSource: MutableInteractionSource? = null,
    indicatorPadding: PaddingValues =
        WideNavigationRailItemDefaults.indicatorPadding(railExpanded = railExpanded),
)
```

`railExpanded` is **required positional**, right after `label`, and is *not* derived from the rail —
you read the state and pass it down (§5). Second most common migration error.

**State** `[SRC]`:

```kotlin
class WideNavigationRailState        // currentValue confirmed by corpus; mutators [UNVERIFIED]
enum class WideNavigationRailValue { Collapsed, Expanded }

@Composable
fun rememberWideNavigationRailState(
    initialValue: WideNavigationRailValue = WideNavigationRailValue.Collapsed,
): WideNavigationRailState
```

`currentValue` is confirmed in use (§5). `expand()` / `collapse()` / `toggle()` are `[UNVERIFIED]` —
check via IDE before calling them.

**Expanded is non-modal** `[CANON]`: the expanded `WideNavigationRail` replaces the navigation drawer
and shares layout with content rather than overlaying it. `ModalWideNavigationRail` is the overlay
variant; `hideOnCollapse = true` makes it vanish when collapsed rather than shrink to icon width.

**`header`** `[SRC]` KDoc: *"optional header that may hold a `FloatingActionButton` or a logo"* —
this is where the menu/expand toggle goes, and where the app's primary action lives on wide windows
(the rail's answer to "where did the FAB go on a tablet").

Defaults `[SRC]`: `WideNavigationRailDefaults.{shape, modalCollapsedShape, modalExpandedShape,
colors(), windowInsets, arrangement, ContentPadding, ModalExpandedProperties}`;
`WideNavigationRailItemDefaults.{colors(), iconPositionFor(railExpanded), indicatorPadding(railExpanded)}`.
Also `WideNavigationRailColors`, `ModalWideNavigationRailProperties`.

`[CANON]` Expressive rail vs classic: width **80→96dp**, item min height **60→64dp**, elevation
**0→3dp**, active label colour → **secondary**, selected label **no longer bold**.

`[SRC]` The `develop/ui/compose/components/navigation-rail` guide page still documents only legacy
`NavigationRail`/`NavigationRailItem` and has not been updated for `WideNavigationRail` — do not send
users there.

### 3.1 `ModalWideNavigationRail` — the canonical androidx sample

Still **zero occurrences** across all four community corpus apps `[CORPUS]` — but the androidx
`@Sampled` function is now available and is the authority. 5 hits in the samples module.

`[OFFICIAL androidx]`
`compose/material3/material3/samples/src/main/java/androidx/compose/material3/samples/NavigationRailSamples.kt:183-290`
— verbatim:

```kotlin
@Preview
@Sampled
@Composable
fun ModalWideNavigationRailSample() {
    var selectedItem by rememberSaveable { mutableIntStateOf(0) }
    val items = listOf("Home", "Search", "Settings")
    val selectedIcons = listOf(Icons.Filled.Home, Icons.Filled.Favorite, Icons.Filled.Star)
    val unselectedIcons =
        listOf(Icons.Outlined.Home, Icons.Outlined.FavoriteBorder, Icons.Outlined.StarBorder)
    val state = rememberWideNavigationRailState()
    val scope = rememberCoroutineScope()
    val headerDescription =
        if (state.targetValue == WideNavigationRailValue.Expanded) {
            "Collapse rail"
        } else {
            "Expand rail"
        }

    Row(Modifier.fillMaxWidth()) {
        ModalWideNavigationRail(
            state = state,
            // Note: the value of expandedHeaderTopPadding depends on the layout of your screen in
            // order to achieve the best alignment.
            expandedHeaderTopPadding = 64.dp,
            header = {
                // Header icon button should have a tooltip.
                @OptIn(ExperimentalMaterial3Api::class)
                TooltipBox(
                    positionProvider =
                        TooltipDefaults.rememberTooltipPositionProvider(
                            TooltipAnchorPosition.Above
                        ),
                    tooltip = {
                        PlainTooltip(
                            Modifier.semantics {
                                // TODO(b/496338253): Remove this modifier once bug where tooltip
                                //  text is not announced by a11y screen readers is resolved.
                                liveRegion = LiveRegionMode.Assertive
                                paneTitle = headerDescription
                            }
                        ) {
                            Text(headerDescription)
                        }
                    },
                    state = rememberTooltipState(),
                ) {
                    IconButton(
                        modifier =
                            Modifier.padding(start = 24.dp).semantics {
                                // The button must announce the expanded or collapsed state of the
                                // rail for accessibility.
                                stateDescription =
                                    if (state.currentValue == WideNavigationRailValue.Expanded) {
                                        "Expanded"
                                    } else {
                                        "Collapsed"
                                    }
                            },
                        onClick = {
                            scope.launch {
                                if (state.targetValue == WideNavigationRailValue.Expanded)
                                    state.collapse()
                                else state.expand()
                            }
                        },
                    ) {
                        if (state.targetValue == WideNavigationRailValue.Expanded) {
                            Icon(Icons.AutoMirrored.Filled.MenuOpen, headerDescription)
                        } else {
                            Icon(Icons.Filled.Menu, headerDescription)
                        }
                    }
                }
            },
        ) {
            items.forEachIndexed { index, item ->
                WideNavigationRailItem(
                    railExpanded = state.targetValue == WideNavigationRailValue.Expanded,
                    icon = {
                        Icon(
                            if (selectedItem == index) selectedIcons[index]
                            else unselectedIcons[index],
                            contentDescription = item,
                        )
                    },
                    label = { Text(item) },
                    selected = selectedItem == index,
                    onClick = { selectedItem = index },
                )
            }
        }

        val textString =
            if (state.currentValue == WideNavigationRailValue.Expanded) {
                "Expanded"
            } else {
                "Collapsed"
            }
        Column {
            Text(modifier = Modifier.padding(16.dp), text = "The rail is $textString.")
            Text(
                modifier = Modifier.padding(16.dp),
                text =
                    "Note: This demo is best shown in portrait mode, as landscape mode" +
                        " may result in a compact height in certain devices. For any" +
                        " compact screen dimensions, use a Navigation Bar instead.",
            )
        }
    }
}
```

**This sample resolves three things the signature alone could not:**

1. **`state.expand()` / `state.collapse()` exist and are `suspend`** — they are called inside
   `scope.launch { }`. That upgrades §3's `[UNVERIFIED]` note on the mutators. `toggle()` is still
   `[UNVERIFIED]`; the sample branches on `targetValue` instead of calling one.
2. **`targetValue` vs `currentValue` is a real distinction with a rule.** Drive **visual** state
   (`railExpanded`, which icon) from `state.targetValue` so the rail flips at gesture start rather
   than at settle; describe **settled** state (`stateDescription`) from `state.currentValue`. Getting
   these backwards produces a rail whose label lags its own animation.
3. **`expandedHeaderTopPadding = 64.dp` is layout-dependent** and Google says so inline — it is not a
   universal constant, it aligns the header against whatever else is at the top of your screen.

**Two a11y rules Google states as mandatory in this sample, both of which a hand-written rail will
miss:** the header toggle **must** carry a `TooltipBox`, and it **must** set `stateDescription` to
"Expanded"/"Collapsed". A fourth, stated in the sample's own body text: **on compact screens use a
navigation bar, not a rail.**

Dismissible variant, same file, `DismissibleModalWideNavigationRailSample` (line 297) — one
parameter's difference:

```kotlin
        ModalWideNavigationRail(state = state, hideOnCollapse = true) {
```

`hideOnCollapse = true` makes the rail vanish entirely when collapsed instead of shrinking to icon
width. Use it when the rail is an overlay you summon; leave it `false` when collapsed-icon-rail is a
legitimate resting state.

Other samples in the same file, presence confirmed, **bodies UNVERIFIED**:
`WideNavigationRailResponsiveSample` (75), `WideNavigationRailCollapsedSample` (340),
`WideNavigationRailExpandedSample` (367), `WideNavigationRailArrangementsSample` (395).

`[OFFICIAL androidx]` The `ShortNavigationBar` sample (`NavigationBarSamples.kt:51+`) is the minimal
counterpart, and confirms the `label`-before-`modifier` positional trap from §2 by always passing it:

```kotlin
fun ShortNavigationBarSample() {
    var selectedItem by rememberSaveable { mutableIntStateOf(0) }
    val items = listOf("Songs", "Artists", "Playlists")
    val selectedIcons = listOf(Icons.Filled.Home, Icons.Filled.Favorite, Icons.Filled.Star)
    val unselectedIcons =
        listOf(Icons.Outlined.Home, Icons.Outlined.FavoriteBorder, Icons.Outlined.StarBorder)

    ShortNavigationBar {
        items.forEachIndexed { index, item ->
            ShortNavigationBarItem(
                icon = {
                    Icon(
                        if (selectedItem == index) selectedIcons[index] else unselectedIcons[index],
                        contentDescription = null,
                    )
                },
                label = { Text(item) },
                selected = selectedItem == index,
                onClick = { selectedItem = index },
            )
        }
    }
}
```

Horizontal-item arrangement variant (line 93): `ShortNavigationBar(arrangement = ShortNavigationBarArrangement.Centered) {`
— which confirms `Centered` as a real member (§2).

---

## 4. `NavigationSuiteScaffold` — the recommended default

Artifact `androidx.compose.material3:material3-adaptive-navigation-suite`. **Note the group:** it
rides the **material3** train (`1.5.0-alpha26`, latest as of 2026-08-12), *not* `material3.adaptive`
(now **stable at 1.3.0**, 2026-08-12) `[SRC]`.

```kotlin
implementation("androidx.compose.material3:material3-adaptive-navigation-suite:1.5.0-alpha26")
```

**Use this unless you have a concrete reason not to.** It selects the container from
`WindowAdaptiveInfo`, handles insets, and gives you one item list instead of two. (Med declares the
artifact and never calls it — that is a choice, not a recommendation.)

`[CANONICAL-FORM]` — **not captured in this corpus.** Long-standing shape of the 1.x API. Confirm
names against your artifact; the Expressive line keeps adding parameters.

```kotlin
@Composable
fun NavigationSuiteScaffold(
    navigationSuiteItems: NavigationSuiteScope.() -> Unit,
    modifier: Modifier = Modifier,
    layoutType: NavigationSuiteType =
        NavigationSuiteScaffoldDefaults.calculateFromAdaptiveInfo(currentWindowAdaptiveInfo()),
    navigationSuiteColors: NavigationSuiteColors = NavigationSuiteDefaults.colors(),
    containerColor: Color = NavigationSuiteScaffoldDefaults.containerColor,
    contentColor: Color = NavigationSuiteScaffoldDefaults.contentColor,
    content: @Composable () -> Unit = {},
)

// NavigationSuiteScope
fun item(
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: @Composable () -> Unit,
    label: @Composable (() -> Unit)? = null,
    alwaysShowLabel: Boolean = true,
    badge: (@Composable () -> Unit)? = null,
    colors: NavigationSuiteItemColors? = null,
    interactionSource: MutableInteractionSource? = null,
)
```

**`NavigationSuiteType`** `[CANONICAL-FORM]` long-standing values: `NavigationBar`, `NavigationRail`,
`NavigationDrawer`, `None`. `[UNVERIFIED]` the Expressive line has been adding Expressive-container
variants (names of the form `ShortNavigationBarCompact` / `WideNavigationRailExpanded`) plus a
`NavigationSuiteScaffoldDefaults.navigationSuiteType(...)` calculator alongside
`calculateFromAdaptiveInfo(...)`. **Confirm the enum members via IDE autocomplete before writing a
`when` over them** — never hand a user a branch on a member name you have not verified.

**Overriding the type calculation** — the reason `layoutType` is a parameter:

```kotlin
val adaptiveInfo = currentWindowAdaptiveInfo()
val layoutType = when {
    // Cap at a rail: never use a drawer, even on very wide windows.
    adaptiveInfo.windowSizeClass
        .isWidthAtLeastBreakpoint(WindowSizeClass.WIDTH_DP_MEDIUM_LOWER_BOUND) ->
            NavigationSuiteType.NavigationRail
    else -> NavigationSuiteType.NavigationBar
}

NavigationSuiteScaffold(
    layoutType = layoutType,
    navigationSuiteItems = {
        destinations.forEach { d ->
            item(
                selected = d == current,
                onClick = { navigate(d) },
                icon = { Icon(d.icon, contentDescription = null) },
                label = { Text(stringResource(d.label)) },
            )
        }
    },
) {
    AppNavDisplay(...)   // content BELOW the container, never around it
}
```

Other legitimate overrides: force the bar in tabletop posture regardless of width; pass
`NavigationSuiteType.None` on an immersive route to hide nav without unmounting the scaffold (which
would lose state).

**Skip it when** you need a custom transition between bar and rail (the scaffold cross-fades), a
container that isn't a built-in type, rail state hoisted to a ViewModel, or you can't confirm the
enum member you need. Then go to §5.

---

## 5. Med's manual `WideNavigationRail` ⇄ `ShortNavigationBar` switch — full control

`[CORPUS]` `/root/work/repos/Med/app/src/main/kotlin/com/fedeveloper95/med/services/MedApp.kt`
(imports 60–73; body 213–330). One `isExpandedScreen: Boolean` picks an expanded rail in a `Row`
against a `ShortNavigationBar` in `Scaffold(bottomBar =)`. `rememberWideNavigationRailState` is read
back to feed each item's `railExpanded`.

```kotlin
import androidx.compose.material3.ShortNavigationBar
import androidx.compose.material3.ShortNavigationBarItem
import androidx.compose.material3.WideNavigationRail
import androidx.compose.material3.WideNavigationRailItem
import androidx.compose.material3.WideNavigationRailValue
import androidx.compose.material3.rememberWideNavigationRailState
```

```kotlin
    Box(modifier = Modifier.fillMaxSize()) {
        Row(modifier = Modifier.fillMaxSize()) {
            if (isExpandedScreen) {
                val wideNavRailState =
                    rememberWideNavigationRailState(initialValue = WideNavigationRailValue.Expanded)
                WideNavigationRail(
                    state = wideNavRailState,
                    modifier = Modifier.width(IntrinsicSize.Max)
                ) {
                    val isRailExpanded =
                        wideNavRailState.currentValue == WideNavigationRailValue.Expanded
                    WideNavigationRailItem(
                        railExpanded = isRailExpanded,
                        selected = selectedTab == 0,
                        onClick = { selectedTab = 0 },
                        icon = { Icon(Icons.Rounded.Home, contentDescription = null) },
                        label = {
                            Text(
                                stringResource(R.string.home_tab_title),
                                fontFamily = GoogleSansFlex
                            )
                        }
                    )
                    WideNavigationRailItem(
                        railExpanded = isRailExpanded,
                        selected = selectedTab == 1,
                        onClick = { selectedTab = 1 },
                        icon = { Icon(Icons.Rounded.BarChart, contentDescription = null) },
                        label = {
                            Text(
                                stringResource(R.string.stats_tab_title),
                                fontFamily = GoogleSansFlex
                            )
                        }
                    )
                    WideNavigationRailItem(
                        railExpanded = isRailExpanded,
                        selected = selectedTab == 2,
                        onClick = { selectedTab = 2 },
                        icon = { Icon(Icons.Rounded.Person, contentDescription = null) },
                        label = {
                            Text(
                                stringResource(R.string.you_tab_title),
                                fontFamily = GoogleSansFlex
                            )
                        }
                    )
                }
            }

            Scaffold(
                modifier = Modifier.weight(1f),
                ...
                bottomBar = {
                    if (!isExpandedScreen) {
                        ShortNavigationBar {
                            ShortNavigationBarItem(
                                selected = selectedTab == 0,
                                onClick = { selectedTab = 0 },
                                icon = { Icon(Icons.Rounded.Home, contentDescription = null) },
                                label = {
                                    Text(
                                        stringResource(R.string.home_tab_title),
                                        fontFamily = GoogleSansFlex
                                    )
                                }
                            )
                            ShortNavigationBarItem(
                                selected = selectedTab == 1,
                                onClick = { selectedTab = 1 },
                                icon = { Icon(Icons.Rounded.BarChart, contentDescription = null) },
                                label = {
                                    Text(
                                        stringResource(R.string.stats_tab_title),
                                        fontFamily = GoogleSansFlex
                                    )
                                }
                            )
                            ShortNavigationBarItem(
                                selected = selectedTab == 2,
                                onClick = { selectedTab = 2 },
                                icon = { Icon(Icons.Rounded.Person, contentDescription = null) },
                                label = {
                                    Text(
                                        stringResource(R.string.you_tab_title),
                                        fontFamily = GoogleSansFlex
                                    )
                                }
                            )
                        }
                    }
                }
            ) { padding ->
```

`isExpandedScreen` is computed once at the Activity `[CORPUS]` `.../med/MainActivity.kt:191,323`:

```kotlin
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.material3.windowsizeclass.calculateWindowSizeClass
val windowSizeClass = calculateWindowSizeClass(this)
isExpandedScreen = windowSizeClass.widthSizeClass == WindowWidthSizeClass.Expanded,
```

**Copy** the structure: one boolean, `Row` + rail left, `Scaffold` with `weight(1f)` right,
`Modifier.width(IntrinsicSize.Max)` on the rail so labels aren't clipped.
**Fix three things** when you adapt it:
1. The item list is written out **three times**. Hoist to `List<NavItem>` and `forEach` in both branches.
2. `calculateWindowSizeClass` is the older `material3-window-size-class` API. Prefer
   `currentWindowAdaptiveInfo().windowSizeClass` — the adaptive scaffolds already consume it, so you
   compute one value, not two (§9).
3. Two-state only: medium widths get the bottom bar. Add a medium branch (`Centered` +
   `iconPosition = Start`, or a collapsed rail) for canonical three-tier behaviour.

---

## 6. `HorizontalFloatingToolbar` used AS navigation — Tomato

`[SRC]` Graduated to non-experimental in **1.5.0-alpha22**; on 1.4.0 needs
`ExperimentalMaterial3ExpressiveApi`. KDoc: horizontal = *"displays navigation and key actions in a
`Row`"* — navigation is a sanctioned use.

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
```

`FloatingToolbarDefaults` members used here `[SRC]` (names verified, values not captured):
`ScreenOffset`, `ContainerShape`, `ContentPadding`, `standardFloatingToolbarColors()`,
`vibrantFloatingToolbarColors()`, `exitAlwaysScrollBehavior(...)`, `ScrollDistanceThreshold`. Enum
`FloatingToolbarExitDirection` (`Bottom` confirmed in use).

`[SRC]` Built-in a11y override: **the toolbar stays expanded and `scrollBehavior` is disabled when an
accessibility service is active.** `leadingContent`/`trailingContent` render **only when expanded**.

`[CORPUS]` `/root/work/repos/Tomato/androidApp/src/main/java/org/nsh07/pomodoro/ui/AppScreen.kt`
(imports 45–64; body 130–290):

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
                                    colors = ToggleButtonDefaults.toggleButtonColors(
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

The destination model `[CORPUS]` (same file, 140–161) — the trailing lambda is "tap the selected tab
to pop that tab's stack to root":

```kotlin
    val mainScreens = remember {
        listOf(
            NavItem(Screen.Timer, Res.drawable.timer_outlined, Res.drawable.timer_filled, Res.string.timer) {},
            NavItem(Screen.Stats.Main, Res.drawable.monitoring, Res.drawable.monitoring_filled, Res.string.stats) {
                statsViewModel.backStack.removeRange(1, statsViewModel.backStack.size)
            },
            NavItem(Screen.Settings.Main, Res.drawable.settings, Res.drawable.settings_filled, Res.string.settings) {
                settingsViewModel.backStack.removeRange(1, settingsViewModel.backStack.size)
            }
        )
    }
```

### When toolbar-as-nav is right — and when it isn't

**Right:** ≤3 top-level destinations (Tomato has exactly three); edge-to-edge scrolling content that
`exitAlwaysScrollBehavior(FloatingToolbarExitDirection.Bottom)` can push the bar out of; a hero
colour identity the nav should carry (`vibrantFloatingToolbarColors` with animated
`primaryContainer`/`tertiaryContainer` is what makes Tomato's bar read as part of the timer instead
of chrome); no second toolbar of actions on the same page.

**Wrong:** 4–5 destinations — the pill gets long, labels clip, and it collides with cutout insets you
now hand-manage. Pages that also need an action toolbar — `[CANON]` *"Show the navigation bar on
primary pages, and toolbars on subsequent pages with actions."* Apps that need a rail on tablets —
there is no vertical story here, and building one separately is the two-containers trap; use §4/§5.
`[CANON]` also: *"a floating toolbar shouldn't exceed the edge of the window or pane"* — the
component will not shrink for you.

**Copy these details if you copy the pattern:** `TooltipBox` on every item (with labels collapsed,
the tooltip is the only visible name); `contentDescription = stringResource(item.label)` on **both**
crossfade icon branches; `zIndex(1f)`; manual insets
(`systemBarsInsets.calculateBottomPadding() + ScreenOffset` — a floating toolbar in `bottomBar` gets
no free inset handling, unlike `ShortNavigationBar`'s `windowInsets`);
`Modifier.height(56.dp)` per item for touch targets; `AnimatedVisibility` on the whole bar with
`slowSpatialSpec()` so immersive routes hide nav with app-consistent motion, not a jump cut.

---

## 6a. `VerticalFloatingToolbar` — the rail-side counterpart

Previously **zero examples anywhere**. Now covered from both directions: 12 hits in the androidx
samples module and 2 real usages in `android/androidify`.

`[SRC]` Graduated to non-experimental in **1.5.0-alpha22** along with the rest of the FloatingToolbar
family; on 1.4.0 it needs `ExperimentalMaterial3ExpressiveApi`.

### Canonical form (androidx `@Sampled`)

`[OFFICIAL androidx]`
`compose/material3/material3/samples/src/main/java/androidx/compose/material3/samples/FloatingToolbarSamples.kt:348-421`
— verbatim:

```kotlin
@OptIn(ExperimentalMaterial3Api::class)
@Preview
@Sampled
@Composable
fun ExpandableVerticalFloatingToolbarSample() {
    var expanded by rememberSaveable { mutableStateOf(true) }
    Scaffold(
        content = { innerPadding ->
            Box(Modifier.padding(innerPadding)) {
                // The toolbar should receive focus before the screen content for a11y, so place it
                // first. Make sure to set its zIndex so it's above the screen content visually.
                VerticalFloatingToolbar(
                    modifier =
                        Modifier.align(Alignment.CenterEnd).offset(x = -ScreenOffset).zIndex(1f),
                    expanded = expanded,
                    leadingContent = { LeadingContent() },
                    trailingContent = { TrailingContent() },
                    content = {
                        TooltipBox(
                            positionProvider =
                                TooltipDefaults.rememberTooltipPositionProvider(
                                    TooltipAnchorPosition.Above
                                ),
                            tooltip = {
                                PlainTooltip(
                                    modifier =
                                        Modifier.semantics {
                                            // TODO(b/496338253): Remove this modifier once bug
                                            //  where tooltip text is not announced by a11y screen
                                            //  readers is resolved.
                                            liveRegion = LiveRegionMode.Assertive
                                            paneTitle = "Localized description"
                                        }
                                ) {
                                    Text("Localized description")
                                }
                            },
                            state = rememberTooltipState(),
                        ) {
                            FilledIconButton(
                                modifier = Modifier.height(64.dp),
                                onClick = { /* doSomething() */ },
                            ) {
                                Icon(Icons.Filled.Add, contentDescription = "Localized description")
                            }
                        }
                    },
                )
                LazyColumn(
                    // Apply a floatingToolbarVerticalNestedScroll Modifier toggle the expanded
                    // state of the HorizontalFloatingToolbar.
                    modifier =
                        Modifier.floatingToolbarVerticalNestedScroll(
                            expanded = expanded,
                            onExpand = { expanded = true },
                            onCollapse = { expanded = false },
                        ),
                    state = rememberLazyListState(),
                    contentPadding = innerPadding,
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
            }
        }
    )
}
```

**Two rules Google states inline that the community corpus never captured** — both apply equally to
`HorizontalFloatingToolbar` (§6), where Tomato and Med both get the first one wrong:

1. *"The toolbar should receive focus before the screen content for a11y, so place it first. Make
   sure to set its zIndex so it's above the screen content visually."* → declare the toolbar
   **before** the content inside the `Box`, and give it `.zIndex(1f)`. Declaration order is
   traversal order; `zIndex` restores paint order. Our community examples place toolbars last, which
   reads correctly and traverses wrong.
2. **`Modifier.floatingToolbarVerticalNestedScroll(expanded, onExpand, onCollapse)` goes on the
   scrolling content, not on the toolbar.** Note the *vertical* variant is used with a
   *vertical* toolbar here and also with a horizontal one in LastChat — the name describes the
   scroll axis, not the toolbar axis.

Also note `Modifier.align(Alignment.CenterEnd).offset(x = -ScreenOffset)` — the negative offset is
how a right-anchored floating toolbar gets its standard inset from the window edge.
`FloatingToolbarDefaults.ScreenOffset` is the token; do not hardcode a dp.

Other vertical-toolbar samples in the same file, presence confirmed, **bodies UNVERIFIED**:
`OverflowingVerticalFloatingToolbarSample` (427), `ScrollableVerticalFloatingToolbarSample` (546),
`VerticalFloatingToolbarWithFabSample` (977), `CenteredVerticalFloatingToolbarWithFabSample` (1169).

### Real usage — androidify, one content lambda for both orientations

`[OFFICIAL androidify]`
`feature/results/src/main/java/com/android/developers/androidify/customize/ToolSelector.kt`
(full file, 16-121). The valuable idiom: **hoist the buttons into a `@Composable` val and reuse it
across both orientations**, so horizontal ⇄ vertical is a pure layout switch with zero duplicated
item code — the exact fix §11 prescribes for Med's triplicated destination list.

```kotlin
@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

@Composable
fun ToolSelector(
    tools: List<CustomizeTool>,
    selectedOption: CustomizeTool,
    onToolSelected: (CustomizeTool) -> Unit,
    horizontal: Boolean,
    modifier: Modifier = Modifier,
) {
    val buttons = @Composable {
        tools.forEachIndexed { index, tool ->
            ToolSelectorToggleButton(
                modifier = Modifier,
                tool = tool,
                checked = selectedOption == tool,
                onCheckedChange = { onToolSelected(tool) },
            )
            if (index != tools.size - 1) {
                Spacer(Modifier.size(8.dp))
            }
        }
    }
    val toolbarColors = FloatingToolbarColors(
        toolbarContainerColor = MaterialTheme.colorScheme.surface,
        toolbarContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
        fabContainerColor = MaterialTheme.colorScheme.tertiary,
        fabContentColor = MaterialTheme.colorScheme.onTertiary,
    )

    if (horizontal) {
        HorizontalFloatingToolbar(
            modifier = modifier.toolbarBorder(),
            shape = MaterialTheme.shapes.large,
            colors = toolbarColors,
            expanded = true,
        ) {
            buttons()
        }
    } else {
        VerticalFloatingToolbar(
            modifier = modifier.toolbarBorder(),
            shape = MaterialTheme.shapes.large,
            colors = toolbarColors,
            expanded = true,
        ) {
            buttons()
        }
    }
}
```

**Notes**

- `val buttons = @Composable { … }` is a plain local holding a composable lambda — not `remember`ed,
  not a function. It is invoked as `buttons()` inside whichever container wins. Both branches get
  byte-identical children.
- `FloatingToolbarColors(...)` is constructed **directly** here rather than via
  `FloatingToolbarDefaults.standardFloatingToolbarColors()` / `vibrantFloatingToolbarColors()`. That
  is a valid alternative when you want all four roles explicit; note it names `fabContainerColor` /
  `fabContentColor` even in a toolbar with no FAB.
- `expanded = true` hardcoded — this toolbar is a persistent tool palette, not a scroll-reactive one,
  so there is no `scrollBehavior` and no `floatingToolbarVerticalNestedScroll`.
- `shape = MaterialTheme.shapes.large` overrides `FloatingToolbarDefaults.ContainerShape`, and
  `.toolbarBorder()` is an app-local modifier — both are brand decisions, not API requirements.

**Vertical toolbar vs `WideNavigationRail` — do not confuse them.** A `VerticalFloatingToolbar` holds
**actions** on a tall window (androidify: a tool palette). A `WideNavigationRail` holds
**destinations**. `[CANON]` *"Show the navigation bar on primary pages, and toolbars on subsequent
pages with actions."* The vertical toolbar is not the tablet answer to toolbar-as-nav (§6) — that
answer is still §4/§5.

---

## 7. vivi-music's fully custom floating navigation bar

`[CORPUS]` `/root/work/repos/vivi-music/app/src/main/kotlin/com/music/vivi/ui/component/FloatingNav.kt`

No `ShortNavigationBar`, no floating toolbar — a hand-rolled two-pill nav (destinations in a left
capsule, Search in its own circle) with AMOLED support and a long-press affordance. The "we built our
own" reference; read it for the full cost of owning the container.

```kotlin
@Composable
fun FloatingNavigationBar(
    navigationItems: List<Screens>,
    currentRoute: String?,
    onItemClick: (Screens, Boolean) -> Unit,
    modifier: Modifier = Modifier,
    pureBlack: Boolean = false,
    slimNav: Boolean = false,
    onSearchLongClick: (() -> Unit)? = null,
    bottomInset: Dp = 0.dp
) {
    val containerColor = if (pureBlack) Color.Black else MaterialTheme.colorScheme.surfaceContainer
    val outlineColor = if (pureBlack) Color(0xFF222222) else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)

    // Left items are all main screens EXCEPT Search
    val leftItems = remember(navigationItems) {
        navigationItems.filter { it != Screens.Search }
    }
    // Right item is Search
    val rightItem = Screens.Search

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(bottom = bottomInset + 12.dp), // Suspended above screen edges
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Left Pill: Home, Library, (Listen Together if enabled)
        Row(
            modifier = Modifier
                .height(64.dp)
                .shadow(elevation = 6.dp, shape = CircleShape)
                .background(containerColor, shape = CircleShape)
                .border(width = 1.dp, color = outlineColor, shape = CircleShape)
                .padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            leftItems.forEach { screen ->
                val isSelected = remember(currentRoute, screen.route) {
                    isRouteSelected(currentRoute, screen.route, navigationItems)
                }
                FloatingNavItem(
                    screen = screen,
                    isSelected = isSelected,
                    onClick = { onItemClick(screen, isSelected) },
                    slimNav = slimNav
                )
            }
        }

        Spacer(modifier = Modifier.width(16.dp))

        // Right Circle Pill: Search (standalone)
        val isSearchSelected = remember(currentRoute, rightItem.route) {
            isRouteSelected(currentRoute, rightItem.route, navigationItems)
        }

        Box(
            modifier = Modifier
                .size(64.dp)
                .shadow(elevation = 6.dp, shape = CircleShape)
                .background(containerColor, shape = CircleShape)
                .border(width = 1.dp, color = outlineColor, shape = CircleShape),
            contentAlignment = Alignment.Center
        ) {
            FloatingNavItem(
                screen = rightItem,
                isSelected = isSearchSelected,
                onClick = { onItemClick(rightItem, isSearchSelected) },
                slimNav = slimNav,
                modifier = Modifier.fillMaxSize(),
                onSearchLongClick = onSearchLongClick
            )
        }
    }
}

private fun isRouteSelected(currentRoute: String?, screenRoute: String, navigationItems: List<Screens>): Boolean {
    if (currentRoute == null) return false
    if (currentRoute == screenRoute) return true
    return navigationItems.any { it.route == screenRoute } &&
           currentRoute.startsWith("$screenRoute/")
}
```

The item — every behaviour `ShortNavigationBarItem` gives free is re-implemented here (selection
colour, indicator pill, indicator animation, label expand/collapse, ripple, long-press):

```kotlin
@Composable
private fun FloatingNavItem(
    screen: Screens,
    isSelected: Boolean,
    onClick: () -> Unit,
    slimNav: Boolean,
    modifier: Modifier = Modifier,
    onSearchLongClick: (() -> Unit)? = null
) {
    val haptics = LocalHapticFeedback.current
    val viewConfiguration = LocalViewConfiguration.current
    val interactionSource = remember { MutableInteractionSource() }

    val isSearchItem = screen == Screens.Search && onSearchLongClick != null
    if (isSearchItem) {
        LaunchedEffect(interactionSource) {
            // [ELIDED — 22 lines of manual long-press plumbing: collectLatest over
            // interactionSource.interactions; PressInteraction.Press → delay(
            // viewConfiguration.longPressTimeoutMillis) → performHapticFeedback(LongPress) +
            // onSearchLongClick(); Release → onClick() if not already long-pressed; Cancel → reset.]
        }
    }

    val iconRes = if (isSelected) screen.iconIdActive else screen.iconIdInactive
    val contentColor = if (isSelected) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }

    // Material 3 Expressive smooth animated pill background for selected items
    val backgroundColor by animateColorAsState(
        targetValue = if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f) else Color.Transparent,
        animationSpec = tween(durationMillis = 200)
    )

    // Bouncy scale feedback on selection
    val scale by animateFloatAsState(
        targetValue = if (isSelected) 1.05f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMediumLow
        )
    )

    Box(
        modifier = modifier
            .graphicsLayer(scaleX = scale, scaleY = scale)
            .clip(CircleShape)
            .background(backgroundColor)
            .clickable(
                interactionSource = interactionSource,
                indication = ripple(bounded = true),
                onClick = {
                    if (!isSearchItem) {
                        onClick()
                    }
                }
            )
            .let {
                if (screen == Screens.Search) it else it.padding(horizontal = 14.dp, vertical = 10.dp)
            },
        contentAlignment = Alignment.Center
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.animateContentSize(
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioNoBouncy,
                    stiffness = Spring.StiffnessMediumLow
                )
            )
        ) {
            Icon(
                painter = painterResource(id = iconRes),
                contentDescription = stringResource(screen.titleId),
                tint = contentColor,
                modifier = Modifier.size(24.dp)
            )

            AnimatedVisibility(
                visible = !slimNav && isSelected && screen != Screens.Search,
                enter = fadeIn() + expandHorizontally(),
                exit = fadeOut() + shrinkHorizontally()
            ) {
                Text(
                    text = stringResource(screen.titleId),
                    color = contentColor,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1
                )
            }
        }
    }
}
```

**Do not copy uncritically.** Three defects: (1) hardcoded `tween(200)` and
`spring(MediumBouncy, MediumLow)` instead of `MaterialTheme.motionScheme.*` — the nav animates on a
different curve from the rest of the app; use `defaultEffectsSpec()` for colour, `fastSpatialSpec()`
for scale. (2) **No selected semantics** — `Modifier.clickable` yields `Role.Button`, so TalkBack
never says "selected" (§11). (3) In slim mode the item is ~44dp tall (24dp icon + 10dp vertical
padding) — below the 48dp floor `[CANON]`.

**Worth stealing:** Search split into its own circle so it reads as a distinct affordance;
`bottomInset` as a parameter instead of reading insets internally; route-prefix matching in
`isRouteSelected` so a nested route still highlights its top-level tab.

---

## 8. `NavigationBarItem` inside a `BottomAppBar` — LastChat

`[CORPUS]` `/root/work/repos/LastChat/app/src/main/java/me/rerere/rikkahub/ui/pages/developer/DeveloperPage.kt:47-60`
— the only classic `NavigationBarItem` in the corpus, on a hidden developer page:

```kotlin
        bottomBar = {
            BottomAppBar {
                NavigationBarItem(
                    selected = pager.currentPage == 0,
                    onClick = { scope.launch { pager.animateScrollToPage(0) } },
                    label = {
                        Text(text = stringResource(R.string.developer))
                    },
                    icon = {
                        Icon(Icons.Rounded.Description, null)
                    }
                )
            }
        }
```

It compiles, but do not recommend it: `NavigationBarItem` expects a `NavigationBar` parent for its
colours and row measurement, so the indicator may not contrast against the app-bar container;
`[CANON]` the **bottom app bar is deprecated in Expressive** — *"Keep using the bottom app bar →
Deprecated, replace with the docked toolbar"*; and one item in a nav container is a button, not
navigation. Cite this when a user asks whether it's possible — yes, and here's why not to.

---

## 9. Window size classes

**Use these** `[CORPUS]` (Tomato, throughout):

```kotlin
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfo
import androidx.window.core.layout.WindowSizeClass
import androidx.window.core.layout.WindowSizeClass.Companion.WIDTH_DP_EXPANDED_LOWER_BOUND

val windowSizeClass = currentWindowAdaptiveInfo().windowSizeClass

val widthExpanded = currentWindowAdaptiveInfo()
    .windowSizeClass
    .isWidthAtLeastBreakpoint(WIDTH_DP_EXPANDED_LOWER_BOUND)
```

Those three lines appear verbatim in `AlarmSettings.kt:139`, `AboutScreen.kt:111`,
`SettingsMainScreen.kt:107`, `TimerSettings.kt:157`, `AppearanceSettings.kt:93`,
`BackupRestoreScreen.kt:93`, `LastWeekScreen.kt:138`, `StatsMainScreen.kt:107` under
`/root/work/repos/Tomato/shared/src/commonMain/kotlin/org/nsh07/pomodoro/ui/`.

Confirmed members `[CORPUS]`: `WindowSizeClass.WIDTH_DP_MEDIUM_LOWER_BOUND` (600),
`WindowSizeClass.WIDTH_DP_EXPANDED_LOWER_BOUND` (840), `minWidthDp`, `minHeightDp`,
`isWidthAtLeastBreakpoint(Int)`. `currentWindowAdaptiveInfo()` → `WindowAdaptiveInfo` with
`windowSizeClass` and `windowPosture` (`isTabletop`, `separatingVerticalHingeBounds`,
`occludingVerticalHingeBounds`, `allVerticalHingeBounds`).

**Never:**

```kotlin
val isTablet = LocalConfiguration.current.screenWidthDp >= 600   // WRONG
val isTablet = LocalContext.current.resources.getBoolean(R.bool.is_tablet)  // WRONG
```

`Configuration.screenWidthDp` is the **screen**, not the window — in split-screen, freeform or
desktop windowing you own a fraction of it and will render a rail in a 300dp window. It carries no
posture, so folded and unfolded states reporting the same width class get identical treatment.
Resource qualifiers (`values-sw600dp`) bake the decision at inflation and never react to resize.
And the adaptive scaffolds consume `WindowAdaptiveInfo` anyway — a second computation is a second
source of truth that disagrees at the boundary.

The older `material3-window-size-class` API (`calculateWindowSizeClass(activity)` +
`WindowWidthSizeClass.Expanded`, used by Med `[CORPUS]`) works and is not deprecated, but it is
Activity-scoped and carries no posture. Prefer `currentWindowAdaptiveInfo()` in new code.

---

## 9a. `derivedMediaQuery` — the experimental alternative to window size classes

**Read this before recommending it.** `androidx.compose.ui.derivedMediaQuery` is **experimental**
(`ExperimentalMediaQueryApi` + `ExperimentalComposeUiApi`), requires a **process-wide feature flag**,
and is not what the adaptive scaffolds consume. `WindowSizeClass` /
`currentWindowAdaptiveInfo()` (§9) remains the conservative choice and the only one that
`NavigationSuiteScaffold`, `ListDetailPaneScaffold` and the scene strategies read. But this is not a
fringe API — **Google's own `android/ai-samples/jetpacker` uses it instead of `WindowSizeClass`
entirely**, so it belongs here as a documented alternative rather than an unknown.

`[OFFICIAL jetpacker]` Confirmed absent from that repo (grep = 0 across all `.kt`): `WindowSizeClass`,
`calculateWindowSizeClass`, `currentWindowAdaptiveInfo`, `ListDetailPaneScaffold`,
`SupportingPaneScaffold`, `AnimatedPane`, `NavigationSuiteScaffold`, `NavigationRail`,
`WideNavigationRail`, `NavigationBar`, `ShortNavigationBar`. The
`androidx.compose.material3.adaptive:*` artifacts are **not declared at all**. Responsive behaviour
is `derivedMediaQuery` plus two dp thresholds, full stop.

### The API surface

```kotlin
import androidx.compose.ui.ComposeUiFlags
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.ExperimentalMediaQueryApi
import androidx.compose.ui.derivedMediaQuery
import androidx.compose.ui.LocalUiMediaScope   // tests only
import androidx.compose.ui.UiMediaScope        // tests only
```

`derivedMediaQuery { … }` returns a `State<T>`, consumed with `by`. The lambda receiver is
`UiMediaScope`, exposing `windowWidth: Dp`, `windowHeight: Dp`, `windowPosture`, `pointerPrecision`,
`keyboardKind`, `hasCamera`, `hasMicrophone`, `viewingDistance` (member list confirmed from the
sample's `MockUiMediaScope` override list, below).

### It must be feature-flagged on at process start

`[OFFICIAL jetpacker]` `android/app/src/main/kotlin/com/example/jetpacker/JetPackerApplication.kt:17-52`:

```kotlin
@HiltAndroidApp
class JetPackerApplication : Application() {

  @OptIn(ExperimentalComposeUiApi::class)
  override fun onCreate() {
    ComposeUiFlags.isMediaQueryIntegrationEnabled = true
    super.onCreate()
    // … Firebase / feature-flag init …
  }
}
```

The flag is set **before `super.onCreate()`**. Miss it and every query silently returns defaults.

**Previews and screenshot tests do not run `Application.onCreate`**, so jetpacker re-sets the flag in
three more places — a belt-and-braces idiom worth copying wholesale:

```kotlin
// top-level in the screen file, runs at class init — HomeScreen.kt:125-129
@OptIn(ExperimentalComposeUiApi::class)
private val initMediaQuery = run {
  ComposeUiFlags.isMediaQueryIntegrationEnabled = true
  true
}
```

```kotlin
// and again inside each preview body — HomeScreen.kt:496, 513
@OptIn(ExperimentalComposeUiApi::class)
@Preview(showBackground = true)
@Composable
fun HomeScreenPreview() {
  ComposeUiFlags.isMediaQueryIntegrationEnabled = true
  JetPackerTheme { HomeScreenContent(/* … */) }
}
```

…plus the same top-level `run { }` in `HomeScreenshotTest.kt:37-40`. **This is the single most
likely thing to get wrong** — the API compiles and runs and just gives you phone layout everywhere.

### Two breakpoints, used consistently

```kotlin
val foldableBreakpoint by derivedMediaQuery { windowWidth >= 600.dp }
val tabletBreakpoint  by derivedMediaQuery { windowWidth >= 1200.dp }
```

600dp ≈ M3's Medium lower bound; 1200dp is **not** an M3 boundary (Expanded starts at 840dp) — it is
jetpacker's own "large tablet / desktop" line. Six call sites across five files, always one of those
two expressions. What each changes:

| Breakpoint | Effect | Where |
| --- | --- | --- |
| ≥600dp | Bottom FAB moves from `Alignment.Center` to `Alignment.CenterEnd` | `HomeScreen.kt:184-202` |
| ≥600dp | List section goes 1 → 2 columns (`chunked(columns)` + `Row` + trailing `Spacer(weight(1f))`) | `PhoneLayout.kt:152-170` |
| ≥1200dp | Whole layout family swaps: `PhoneLayout` ⇄ `TabletLayout`, two separate composables in separate files | `HomeScreen.kt:427-455` |
| ≥1200dp | Bottom FAB suppressed entirely; it moves inline into a sticky header | `HomeScreen.kt:184`, `TabletLayout.kt:123-127` |
| ≥1200dp | Card typography `titleLarge` → `displaySmall`, spacers 8dp → 16dp | `TripCard.kt:214-226` |
| ≥1200dp | Screen gutters 0dp → 128dp | `CreateTripScreen.kt:75-89` |

**The ergonomic argument, and it is a real one:** a leaf component queries the window *itself* rather
than taking a `windowSizeClass` parameter. `TripCard` reads `tabletBreakpoint` inside its own body —
no prop-drilling of size classes through four layers of layout. That is what you are buying.

**The cost:** it is a second source of truth. If any part of the app also uses
`currentWindowAdaptiveInfo()` — and it will, the moment you add a pane scaffold or
`NavigationSuiteScaffold` — the two can disagree at a boundary, which is exactly the failure §9 warns
about for `Configuration.screenWidthDp`. jetpacker avoids this by using **zero** adaptive artifacts.
Mixing is the trap.

### Testing it — `LocalUiMediaScope` + a fake `UiMediaScope`

`[OFFICIAL jetpacker]`
`android/feature/home/src/screenshotTest/kotlin/com/example/jetpacker/feature/home/HomeScreenshotTest.kt:17-83`:

```kotlin
@file:OptIn(
  androidx.compose.ui.ExperimentalMediaQueryApi::class,
  androidx.compose.ui.ExperimentalComposeUiApi::class
)

@OptIn(ExperimentalComposeUiApi::class)
private val initMediaQuery = run {
  ComposeUiFlags.isMediaQueryIntegrationEnabled = true
  true
}

@OptIn(ExperimentalComposeUiApi::class)
class MockUiMediaScope(
  override val windowWidth: Dp = 400.dp,
  override val windowHeight: Dp = 800.dp,
  override val windowPosture: UiMediaScope.Posture = UiMediaScope.Posture.Flat,
  override val pointerPrecision: UiMediaScope.PointerPrecision = UiMediaScope.PointerPrecision.Coarse,
  override val keyboardKind: UiMediaScope.KeyboardKind = UiMediaScope.KeyboardKind.Virtual,
  override val hasCamera: Boolean = true,
  override val hasMicrophone: Boolean = true,
  override val viewingDistance: UiMediaScope.ViewingDistance = UiMediaScope.ViewingDistance.Near
) : UiMediaScope

class HomeScreenshotTest {
  @OptIn(ExperimentalComposeUiApi::class)
  @PreviewTest
  @Preview(showBackground = true)
  @Composable
  fun HomeScreenScreenshotPreview() {
    CompositionLocalProvider(LocalUiMediaScope provides MockUiMediaScope()) {
      HomeScreenPreview()
    }
  }
}
```

**This is the whole testing story for the API, and it is genuinely better than what
`WindowSizeClass` offers**: `UiMediaScope` is an interface, so a data-holder implementation with
sensible defaults plus
`CompositionLocalProvider(LocalUiMediaScope provides MockUiMediaScope(windowWidth = 1280.dp))` pins a
breakpoint deterministically. Without it, `derivedMediaQuery` reads the real window and screenshot
output becomes environment-dependent.

`UiMediaScope` members confirmed by this override list: `windowWidth`, `windowHeight`,
`windowPosture` (`UiMediaScope.Posture.Flat`), `pointerPrecision`
(`UiMediaScope.PointerPrecision.Coarse`), `keyboardKind` (`UiMediaScope.KeyboardKind.Virtual`),
`hasCamera`, `hasMicrophone`, `viewingDistance` (`UiMediaScope.ViewingDistance.Near`). Other enum
members are `[UNVERIFIED]` — do not write a `when` over `Posture` or `ViewingDistance` without
checking autocomplete.

### Verdict

**Use `WindowSizeClass` / `currentWindowAdaptiveInfo()` unless you have jetpacker's exact shape:** no
adaptive artifacts, no pane scaffolds, no navigation suite, and a strong preference for leaf
components resolving their own layout. `derivedMediaQuery` gives you posture, pointer precision,
keyboard kind and viewing distance in one receiver — genuinely more expressive than a width class —
but it is experimental, flag-gated, unknown to every adaptive scaffold, and it will not survive a
later decision to adopt `ListDetailPaneScaffold`. Say that plainly rather than presenting it as a
drop-in upgrade.

See also `adaptive-and-nav3.md` §14 — jetpacker pairs this with a `FlexBox` and zero pane scaffolds.

---

## 10. Selection, indicator animation, and motion wiring

`ShortNavigationBarItem` / `WideNavigationRailItem` animate the indicator and item colours off
`MaterialTheme.motionScheme` — set `motionScheme = MotionScheme.expressive()` in the theme and the
indicator picks up expressive springs with no spec passed.

`[SRC]` Expressive spatial springs are underdamped (`defaultSpatial` 0.8/380, `fastSpatial` 0.6/800);
effects springs are critically damped (1.0) in both schemes. Rule for anything you animate yourself
in a nav container: **bounds and position on `*SpatialSpec`, colour and alpha on `*EffectsSpec`.**

`[CANON]` tier by scale: Fast for small controls, **Default for nav rails** (the canonical Default
example), Slow for full-screen surfaces.

`[CORPUS]` Tomato — container slide (large surface → slow):

```kotlin
AnimatedVisibility(
    backStack.last() !is Screen.AOD,
    enter = slideInVertically(motionScheme.slowSpatialSpec()) { it },
    exit = slideOutVertically(motionScheme.slowSpatialSpec()) { it }
)
```

`[CORPUS]` Tomato — per-item label (small control → default):

```kotlin
AnimatedVisibility(
    visible = selected || wide,
    enter = expandHorizontally(motionScheme.defaultSpatialSpec()),
    exit = shrinkHorizontally(motionScheme.defaultSpatialSpec())
)
```

`NavigationSuiteScaffold` cross-fades bar ⇄ rail for you. Hand-rolled (§5), `if (isExpandedScreen)`
is a hard cut — to soften it keep both in the tree behind `AnimatedVisibility` with
`expandHorizontally`/`expandVertically` on `defaultSpatialSpec()`, and hoist `selectedTab` above both
branches so the swap does not reset selection.

`[CANON]` Material's first motion principle is respecting platform settings — honour Android's
Remove/Reduce animations by dropping overshoot and substituting cross-fades. The floating toolbar
already does part of this `[SRC]` (stays expanded, disables `scrollBehavior` under an accessibility
service); a hand-rolled container must do the equivalent. See the `m3-expressive-motion` skill.

---

## 11. Anti-patterns

**Two nav containers at once.** A `ShortNavigationBar` plus a floating toolbar on one screen, or a
rail plus a bar mid-resize. `[CANON]`: *"Show the navigation bar on primary pages, and toolbars on
subsequent pages with actions."* If the toolbar is your nav (§6), it is your only nav surface **and**
your only toolbar. Review symptom: `bottomBar = { ShortNavigationBar { ... } }` in a `Scaffold` whose
content also places a floating toolbar.

**>5 destinations in a short nav bar.** `[CANON]` the nav bar *"supports three to five
destinations"*; the explicit do-not is *"Exceed 3–5 navigation bar destinations → use a rail/drawer
pattern."* A sixth tab belongs in the rail's expanded state or an overflow.

**Nav container inside the `NavDisplay`.** Per-destination bars remount on every navigation (losing
the indicator animation), animate along with the content enter/exit (so the bar visibly slides), and
break cross-destination shared elements because the container sits inside the animating subtree.
Hoist it: `[CORPUS]` Tomato has `Scaffold(bottomBar = { HorizontalFloatingToolbar ... })` wrapping
`SharedTransitionLayout { NavDisplay(...) }` — the toolbar is a sibling of the display, not a child
of an entry.

**Hardcoded device branches.** `Configuration.screenWidthDp`, `sw600dp` qualifiers, `isTablet`
booleans (§9).

**Duplicating the destination list per branch** (Med triplicates three items). One `List<NavItem>`,
iterated in both.

**Selection state remembered inside a branch** — it resets on every rotation and resize. Hoist above
the `if (expanded)`.

**Re-implementing what the item already does.** vivi-music's `FloatingNavItem` is ~120 lines
rebuilding indicator, label animation and ripple. Check `ShortNavigationBarItem` +
`NavigationItemColors` + `iconPosition` first.

**Hardcoded specs in nav** (`tween(200)`) — navigation then animates on a different curve than every
other surface. Use `MaterialTheme.motionScheme`.

**Guessing an enum member.** Do not write `NavigationSuiteType.<X>` or
`ShortNavigationBarArrangement.<X>` without confirming it on the user's artifact version.

---

## 12. Accessibility

**Selected state.** `ShortNavigationBarItem` / `WideNavigationRailItem` /
`NavigationSuiteScope.item` carry it; any custom container loses it (`Modifier.clickable` yields
`Role.Button` and nothing else). Add it back:

```kotlin
Modifier
    .semantics { role = Role.Tab; selected = isSelected }
    .clickable(onClickLabel = stringResource(screen.titleId)) { onClick() }
```

**Accessible names.** If the item shows a visible `label`, the `Icon` takes
`contentDescription = null` — two names double-announce ("Home Home"). If the label is hidden or
absent, the `Icon` **must** carry a real `contentDescription`. `[CORPUS]` Tomato does the second
correctly (`Icon(painterResource(item.selectedIcon), stringResource(item.label))` in both crossfade
branches, because labels collapse at compact width) and adds a `TooltipBox` for sighted users; Med
does the first correctly (`contentDescription = null` everywhere, since every item always shows
`Text`). `[CANON]` unlabeled mode removes visible text so icons must carry accessible names
`[UNVERIFIED — stated as inference in the source, but it is the correct reading]`.

**Touch targets** `[CANON]`: **48×48dp** minimum, 44×44dp for pointer, **8dp** minimum separation.
The Expressive bar is 64dp with 6dp padding each side, so the item is 52dp — fine. Danger: custom
items sized from icon + padding (vivi-music slim mode ≈44dp); a `WideNavigationRailItem` in a rail
you constrained below the 96dp expressive width; floating-toolbar items (Tomato pins
`Modifier.height(56.dp)` for exactly this). Verify in Layout Inspector, not by eye.

**Traversal order.** A bottom bar should follow content, a rail should precede it — keeping the rail
first in the `Row` and the bar in `Scaffold(bottomBar = ...)` already gets this. A custom floating
container in a `Box` **over** content takes order from composition, which may not match visual order;
fix with `Modifier.semantics { isTraversalGroup = true; traversalIndex = 1f }`. Group the container
with `isTraversalGroup = true` so swipe-through does not interleave nav items with content.

**Focus.** `[CANON]` the expanded `WideNavigationRail` is **non-modal** — focus is not trapped and
content behind stays reachable. Do not add a scrim or focus trap; `ModalWideNavigationRail` is the
modal one.

**Auto-hide.** A container that hides on scroll must return without scrolling up. Built-in
`exitAlwaysScrollBehavior` restores on any upward scroll and `[SRC]` disables itself entirely under
an accessibility service. A hand-rolled hide must do both.

**Long-press affordances** (vivi-music's Search) need
`Modifier.semantics { onLongClick(label = "...") { ...; true } }` or they are invisible to assistive
tech.

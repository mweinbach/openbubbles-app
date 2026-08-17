# Adaptive layout recipes

Complete, copy-pasteable adaptive layouts. Every recipe: **use this when → full code → pitfalls.**

All code is valid at **`material3-adaptive` 1.3.0** and **`material3` 1.5.0-alpha26**. Rules
applied throughout:

- `currentWindowAdaptiveInfoV2()`, never the deprecated `currentWindowAdaptiveInfo()`.
- `LocalWindowInfo.current.containerSize` / `.containerDpSize`, never `currentWindowSize()` /
  `currentWindowDpSize()`.
- `isWidthAtLeastBreakpoint` / `isHeightAtLeastBreakpoint` / `isAtLeastBreakpoint`. There is **no**
  `containsWidthDp`. All three are `>=`, so `when` chains run **largest → smallest**.
- Width buckets: 0 / 600 / 840 / 1200 / 1600. Height buckets: 0 / 480 / 900. Height has no Large/XL.

Verbatim excerpts are cited with `[REPO <path>]` / `[SRC@HEAD]` / `[DOC]`. Everything uncited is
authored from those sources. `[UNVERIFIED]` markings are preserved.

`NavigationSuiteScaffold`, `NavigationSuiteType` and the nav3 scene strategies are documented in
`navigation-suite.md` in this same directory. Nav *containers* (`ShortNavigationBar`,
`WideNavigationRail`, toolbar-as-nav) are in
`${CLAUDE_PLUGIN_ROOT}/skills/m3-expressive-navigation/`.

## Dependencies

```kotlin
// [DOC] developer.android.com/jetpack/androidx/releases/compose-material3-adaptive
implementation("androidx.compose.material3.adaptive:adaptive:1.3.0")
implementation("androidx.compose.material3.adaptive:adaptive-layout:1.3.0")
implementation("androidx.compose.material3.adaptive:adaptive-navigation:1.3.0")
implementation("androidx.compose.material3.adaptive:adaptive-navigation3:1.3.0")
implementation("androidx.compose.material3:material3-adaptive-navigation-suite:1.5.0-alpha26")
```

All four adaptive artifacts version in lockstep. `material3-adaptive-navigation-suite` is in group
`androidx.compose.material3` and does **not** align with them.

---

# Recipe: the 10-line adaptive app

**Use this when** you are starting a new app, or someone asks "how do I make this adaptive" and the
answer is "you don't have one yet". This is the minimum correct adaptive shell: right nav container
at every size, state preserved across container swaps.

```kotlin
enum class TopLevel(val label: Int, val icon: ImageVector, val route: Route) {
    Home(R.string.home, Icons.Default.Home, Route.Home),
    Search(R.string.search, Icons.Default.Search, Route.Search),
    Library(R.string.library, Icons.Default.LibraryMusic, Route.Library),
}

@Composable
fun App() {
    val navController = rememberNavController()
    val current by navController.currentBackStackEntryAsState()
    val navSuiteType =
        NavigationSuiteScaffoldDefaults.navigationSuiteType(currentWindowAdaptiveInfoV2())

    NavigationSuiteScaffold(
        navigationSuiteType = navSuiteType,
        navigationItems = {
            TopLevel.entries.forEach { dest ->
                NavigationSuiteItem(
                    navigationSuiteType = navSuiteType,
                    selected = current?.destination?.hierarchy
                        ?.any { it.hasRoute(dest.route::class) } == true,
                    onClick = {
                        navController.navigate(dest.route) {
                            popUpTo(navController.graph.findStartDestination().id) {
                                saveState = true
                            }
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
        NavHost(navController, startDestination = Route.Home) {
            composable<Route.Home> { HomeScreen() }
            composable<Route.Search> { SearchScreen() }
            composable<Route.Library> { LibraryScreen() }
        }
    }
}
```

That is the whole adaptive shell. At compact width you get a `ShortNavigationBar` at the bottom; at
medium/expanded a collapsed `WideNavigationRail` at the start; in tabletop posture or a short window
a horizontal-item `ShortNavigationBar`.

**Pitfalls**

- **Omitting the `popUpTo`/`saveState`/`restoreState` triple.** Without it, resizing from bar to
  rail re-creates every screen and loses scroll position. This is the #1 "state lost on resize" bug
  and it has nothing to do with the adaptive library.
- **Not passing `navigationSuiteType` to `NavigationSuiteItem`.** The scaffold and the items compute
  it independently; if you override on one and not the other the item renders for the wrong
  container.
- **Relying on the scaffold's default `navigationSuiteType`.** It reads an internal
  `WindowAdaptiveInfoDefault` whose Large/XL support is `[UNVERIFIED]`. Pass it explicitly from
  `currentWindowAdaptiveInfoV2()`.
- **Putting the nav host outside `content`.** The scaffold must wrap the host, not sit beside it.
- **Adding a `Scaffold(bottomBar = ...)` inside.** That is two nav containers. See Troubleshooting.
- No `WideNavigationRailExpanded` at any size — `navigationSuiteType()` never returns it. If you
  want labels on a 1200dp+ window, override (see `navigation-suite.md` §5).

---

# Recipe: list-detail done right

**Use this when** content is a list of items each with meaningful detail: mail, contacts, notes,
podcasts, settings. `[DOC]`: *"List-detail is ideal for messaging apps, contact managers,
interactive media browsers or any app where the content can be organized as a list of items that
reveal additional information."*

This is the single most requested adaptive layout. Everything below matters.

## The complete implementation

```kotlin
import androidx.compose.material3.adaptive.ExperimentalMaterial3AdaptiveApi
import androidx.compose.material3.adaptive.layout.AnimatedPane
import androidx.compose.material3.adaptive.layout.ListDetailPaneScaffoldRole
import androidx.compose.material3.adaptive.layout.PaneExpansionAnchor
import androidx.compose.material3.adaptive.layout.rememberPaneExpansionState
import androidx.compose.material3.adaptive.navigation.BackNavigationBehavior
import androidx.compose.material3.adaptive.navigation.NavigableListDetailPaneScaffold
import androidx.compose.material3.adaptive.navigation.rememberListDetailPaneScaffoldNavigator
import kotlinx.parcelize.Parcelize

// 1. The content key. MUST be Bundle-storable — the navigator is rememberSaveable.
@Parcelize
data class MailKey(val id: String) : Parcelable

private val ListDetailAnchors = listOf(
    PaneExpansionAnchor.Proportion(0f),
    PaneExpansionAnchor.Offset.fromStart(240.dp),
    PaneExpansionAnchor.Proportion(0.5f),
    PaneExpansionAnchor.Offset.fromEnd(240.dp),
    PaneExpansionAnchor.Proportion(1f),
)

@OptIn(ExperimentalMaterial3AdaptiveApi::class)
@Composable
fun MailListDetail(items: List<Mail>, modifier: Modifier = Modifier) {
    val scope = rememberCoroutineScope()

    // 2. The navigator. Survives process death; drives directive + scaffold state.
    val navigator = rememberListDetailPaneScaffoldNavigator<MailKey>()

    // 3. Selection is DERIVED from the navigator, never held separately.
    val selected = navigator.currentDestination?.contentKey

    // 4. Hoisted drag-handle lambda + expansion state (see pitfalls).
    val expansionState = rememberPaneExpansionState(
        keyProvider = navigator.scaffoldValue,
        anchors = ListDetailAnchors,
        initialAnchoredIndex = 1,
    )
    val dragHandle: @Composable ThreePaneScaffoldScope.(PaneExpansionState) -> Unit =
        remember {
            { state ->
                val interactionSource = remember { MutableInteractionSource() }
                VerticalDragHandle(
                    modifier = Modifier
                        .paneExpansionDraggable(
                            state,
                            LocalMinimumInteractiveComponentSize.current,
                            interactionSource,
                        )
                        .systemGestureExclusion(),
                    interactionSource = interactionSource,
                )
            }
        }

    // 5. Navigable* wires predictive back for you.
    NavigableListDetailPaneScaffold(
        navigator = navigator,
        modifier = modifier,
        defaultBackBehavior = BackNavigationBehavior.PopUntilScaffoldValueChange,
        listPane = {
            AnimatedPane(Modifier.preferredWidth(360.dp)) {
                MailList(
                    items = items,
                    // 6. Highlight the selection only when the detail is visible beside it.
                    selectedId = selected?.id.takeIf { !navigator.scaffoldDirective.isSinglePane() },
                    onItemClick = { mail ->
                        scope.launch {
                            navigator.navigateTo(ListDetailPaneScaffoldRole.Detail, MailKey(mail.id))
                        }
                    },
                )
            }
        },
        detailPane = {
            AnimatedPane {
                if (selected == null) {
                    // 7. Placeholder, not an empty pane.
                    DetailPlaceholder(text = stringResource(R.string.pick_a_message))
                } else {
                    MailDetail(
                        key = selected,
                        // 8. Back button only when the list is NOT visible.
                        onBack = if (navigator.scaffoldDirective.isSinglePane()) {
                            { scope.launch { navigator.navigateBack() } }
                        } else null,
                        onShowAttachments = {
                            scope.launch {
                                navigator.navigateTo(ListDetailPaneScaffoldRole.Extra, selected)
                            }
                        },
                    )
                }
            }
        },
        extraPane = {
            AnimatedPane { AttachmentsPane(key = selected) }
        },
        paneExpansionState = expansionState,
        paneExpansionDragHandle = dragHandle,
    )
}

// Helper — `isSinglePaneLayout()` is internal in the library, so write your own.
@OptIn(ExperimentalMaterial3AdaptiveApi::class)
private fun PaneScaffoldDirective.isSinglePane() = maxHorizontalPartitions == 1
```

## Why each piece is there

**Roles are aliases and they are not in the order you expect** `[SRC@HEAD]`:

```kotlin
public object ListDetailPaneScaffoldRole {
    /** ... It maps to [ThreePaneScaffoldRole.Secondary]. */
    public val List: ThreePaneScaffoldRole = ThreePaneScaffoldRole.Secondary
    /** ... It maps to [ThreePaneScaffoldRole.Primary]. */
    public val Detail: ThreePaneScaffoldRole = ThreePaneScaffoldRole.Primary
    /** ... It maps to [ThreePaneScaffoldRole.Tertiary]. */
    public val Extra: ThreePaneScaffoldRole = ThreePaneScaffoldRole.Tertiary
}
```

| Generic | List-detail | Supporting-pane |
| --- | --- | --- |
| `Primary` | **Detail** | **Main** |
| `Secondary` | **List** | **Supporting** |
| `Tertiary` | **Extra** | **Extra** |

**List = Secondary, Detail = Primary.** The leftmost pane is not `Primary`. Getting this backwards
silently reverses adapt strategies and pane order — the layout still renders, just wrong.
Always write `ListDetailPaneScaffoldRole.Detail`, never `ThreePaneScaffoldRole.Primary`.

**`NavigableListDetailPaneScaffold` is a thin wrapper** `[SRC@HEAD AndroidThreePaneScaffold.android.kt]`:

```kotlin
@ExperimentalMaterial3AdaptiveApi
@Composable
public fun <T> NavigableListDetailPaneScaffold(
    navigator: ThreePaneScaffoldNavigator<T>,
    listPane: @Composable ThreePaneScaffoldPaneScope.() -> Unit,
    detailPane: @Composable ThreePaneScaffoldPaneScope.() -> Unit,
    modifier: Modifier = Modifier,
    extraPane: (@Composable ThreePaneScaffoldPaneScope.() -> Unit)? = null,
    defaultBackBehavior: BackNavigationBehavior =
        BackNavigationBehavior.PopUntilScaffoldValueChange,
    paneExpansionDragHandle: (@Composable ThreePaneScaffoldScope.(PaneExpansionState) -> Unit)? =
        null,
    paneExpansionState: PaneExpansionState? = null,
) {
    ThreePaneScaffoldPredictiveBackHandler(
        navigator = navigator,
        backBehavior = defaultBackBehavior,
    )

    ListDetailPaneScaffold(
        modifier = modifier,
        directive = navigator.scaffoldDirective,
        scaffoldState = navigator.scaffoldState,
        detailPane = detailPane,
        listPane = listPane,
        extraPane = extraPane,
        paneExpansionDragHandle = paneExpansionDragHandle,
        paneExpansionState = paneExpansionState,
    )
}
```

That is its entire value-add: predictive back + `scaffoldState` wiring. **If you use
`ListDetailPaneScaffold` directly you must add `ThreePaneScaffoldPredictiveBackHandler` yourself**,
and you must pass `scaffoldState = navigator.scaffoldState` (not `value =`) or you get no animation.

Predictive back on Android 15 and lower also needs `[DOC]`:

```xml
<application android:enableOnBackInvokedCallback="true">
```

Android 16+ enables it by default.

**Back behaviour** — four values `[SRC@HEAD BackNavigationBehavior.kt]`, default and recommended is
`PopUntilScaffoldValueChange`:

| Behavior | Multi-pane | Single-pane | Use when `[DOC]` |
| --- | --- | --- | --- |
| **`PopUntilScaffoldValueChange`** (default) | Click Item 2 while viewing Item 1 → back might exit app (no layout change) | Item 1 → Item 2 → back returns directly to list | "You want distinct layout transitions with each back action" |
| `PopUntilContentChange` | Click Item 2 → back restores Item 1 in detail pane | same content reversion | "Users expect to return to previously viewed content" |
| `PopUntilCurrentDestinationChange` | pops until the current destination pane changes | same | "Maintaining clear visual indication of current navigation is crucial" |
| `PopLatest` | removes only the most recent destination | same | "Back navigation without skipping intermediate states is required" |

`PopLatest` and `PopUntilContentChange` both carry the same library warning: *"a multi-pane layout
may create navigation backstacks that are not possible in a single-pane layout (e.g., navigating
directly from one detail item to another). If the device size changes in the middle of the
navigation, this `BackNavigationBehavior` may result in unintuitive behavior."*

**State preservation** happens on three levels and you need all three:

1. `rememberListDetailPaneScaffoldNavigator` is **`rememberSaveable`** — the destination history
   survives process death. This is why `T` must be Bundle-storable.
2. `AnimatedPane` wraps its content in `SaveableStateProvider(paneRole.toString())` — each pane's
   `rememberSaveable` state survives the pane being hidden and re-shown.
3. Your own screen state still needs a `ViewModel` or `rememberSaveable`. The scaffold does not
   retain arbitrary composable state across a full recomposition of the pane content.

**The androidx reference sample** `[REPO androidx-m3:.../samples/ThreePaneScaffoldSample.kt lines 161–214]`,
which is where the anchor list and `initialAnchoredIndex = 1` come from:

```kotlin
@OptIn(ExperimentalMaterial3AdaptiveApi::class)
@Preview
@Sampled
@Composable
fun ListDetailPaneScaffoldSampleWithExtraPane() {
    val coroutineScope = rememberCoroutineScope()
    val scaffoldNavigator = rememberListDetailPaneScaffoldNavigator<NavItemData>()
    val items = listOf("Item 1", "Item 2", "Item 3")
    val extraItems = listOf("Extra 1", "Extra 2", "Extra 3")
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
                    hasExtraPane = true,
                    coroutineScope = coroutineScope,
                )
            }
        },
        extraPane = {
            AnimatedPane {
                ExtraPaneContent(
                    extraItems = extraItems,
                    selectedItem = selectedItem,
                    scaffoldNavigator = scaffoldNavigator,
                    coroutineScope = coroutineScope,
                )
            }
        },
        paneExpansionState =
            rememberPaneExpansionState(
                keyProvider = scaffoldNavigator.scaffoldValue,
                anchors = PaneExpansionAnchors,
                initialAnchoredIndex = 1,
            ),
        paneExpansionDragHandle = { state -> PaneExpansionDragHandleSample(state) },
    )
}
```

with `[same file, lines 1099–1106]`:

```kotlin
private val PaneExpansionAnchors =
    listOf(
        PaneExpansionAnchor.Proportion(0f),
        PaneExpansionAnchor.Offset.fromStart(240.dp),
        PaneExpansionAnchor.Proportion(0.5f),
        PaneExpansionAnchor.Offset.fromEnd(240.dp),
        PaneExpansionAnchor.Proportion(1f),
    )
```

**Pitfalls**

- **`navigateTo` / `navigateBack` / `seekBack` are `suspend`.** Wrap in
  `rememberCoroutineScope().launch { }`. Tutorials predating 1.1 call them directly and will not
  compile.
- **Content key type `T` must be Bundle-storable.** `@Parcelize` on a data class. A plain data class
  crashes on process death restore, not at compile time.
- **`Medium width is single-pane by default.`** A 700dp window (portrait tablet, unfolded inner
  display in portrait) shows ONE pane. That is deliberate Material guidance, not a bug. To change it
  see the custom-directive recipe — but Google recommends against it in the KDoc.
- **`AnimatedPane`'s content root is a `Column`, not a `Box`.** Alignment behaves accordingly.
- **`AnimatedPane` splits your modifier**: size and graphics-layer modifiers go to the internal
  `AnimatedVisibility`, everything else to the inner `Column`. That is why
  `Modifier.preferredWidth(360.dp)` works at that call site and nowhere else.
- **`AnimatedPane(shape = …)` does not exist in 1.3.0.** It is a post-1.3.0 addition. Do not write it.
- **`ListDetailPaneScaffold` applies `fillMaxSize()` internally.** Sizing it from outside mostly
  does nothing.
- **Do not hardcode the back button or the selection highlight.** Derive both from
  `maxHorizontalPartitions == 1` (raw scaffold) or `LocalListDetailSceneScope.current == null` (nav3).
- **`navigateBack` returns `Boolean`** and clears the entire history, returning `false`, when there
  is no previous destination.
- **`ThreePaneScaffoldOverride` / `AnimatedPaneOverride` exist in 1.3.0 but are removed at HEAD.**
  Do not build on them.

---

# Recipe: list-detail on Navigation 3

**Use this when** the app is already on Navigation 3, or when you want one back stack to drive both
the panes and the single-pane stack. This is the modern answer: no separate scaffold navigator, no
duplicated selection state.

```kotlin
@Serializable data object ProductListKey : NavKey
@Serializable data class ProductDetailKey(val id: String) : NavKey
@Serializable data class ProductSpecsKey(val id: String) : NavKey

@OptIn(ExperimentalMaterial3AdaptiveApi::class)
@Composable
fun ProductsNav3() {
    val backStack = rememberNavBackStack(ProductListKey)
    val listDetailStrategy = rememberListDetailSceneStrategy<NavKey>()

    // Selection derived from the back stack — the only source of truth.
    val selectedId = backStack.lastOrNull()?.let {
        when (it) {
            is ProductDetailKey -> it.id
            is ProductSpecsKey -> it.id
            else -> null
        }
    }

    NavDisplay(
        backStack = backStack,
        modifier = Modifier.fillMaxSize(),
        onBack = { backStack.removeLastOrNull() },
        sceneStrategies = listOf(listDetailStrategy),
        entryProvider = entryProvider {
            entry<ProductListKey>(
                metadata = ListDetailSceneStrategy.listPane(
                    detailPlaceholder = { DetailPlaceholder("Choose a product") }
                )
            ) {
                ProductList(
                    selectedId = selectedId,
                    onClick = { id ->
                        val dest = ProductDetailKey(id)
                        if (backStack.last() != dest) backStack.add(dest)
                    },
                )
            }

            entry<ProductDetailKey>(metadata = ListDetailSceneStrategy.detailPane()) { key ->
                val inScaffold = LocalListDetailSceneScope.current != null
                ProductDetail(
                    id = key.id,
                    // Only show back when rendering as a full-screen destination.
                    onBack = if (!inScaffold) ({ backStack.removeLastOrNull() }) else null,
                    onShowSpecs = {
                        val dest = ProductSpecsKey(key.id)
                        if (backStack.last() != dest) backStack.add(dest)
                    },
                )
            }

            entry<ProductSpecsKey>(metadata = ListDetailSceneStrategy.extraPane()) { key ->
                val inScaffold = LocalListDetailSceneScope.current != null
                ProductSpecs(
                    id = key.id,
                    onBack = if (!inScaffold) ({ backStack.removeLastOrNull() }) else null,
                )
            }
        },
    )
}
```

The canonical developer.android.com version, verbatim
`[REPO snippets:.../navigation3/scenes/material/MaterialScenesSnippets.kt lines 44–110]`:

```kotlin
// [START android_compose_navigation3_scenes_material_1]
@Serializable
object ProductList : NavKey

@Serializable
data class ProductDetail(val id: String) : NavKey

@Serializable
data object Profile : NavKey

class MaterialListDetailActivity : ComponentActivity() {

    @OptIn(ExperimentalMaterial3AdaptiveApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            Scaffold { paddingValues ->
                val backStack = rememberNavBackStack(ProductList)
                val listDetailStrategy = rememberListDetailSceneStrategy<NavKey>()

                NavDisplay(
                    backStack = backStack,
                    modifier = Modifier.padding(paddingValues),
                    onBack = { backStack.removeLastOrNull() },
                    sceneStrategies = listOf(listDetailStrategy),
                    entryProvider = entryProvider {
                        entry<ProductList>(
                            metadata = ListDetailSceneStrategy.listPane(
                                detailPlaceholder = {
                                    ContentYellow("Choose a product from the list")
                                }
                            )
                        ) {
                            ContentRed("Welcome to Nav3") {
                                Button(onClick = {
                                    backStack.add(ProductDetail("ABC"))
                                }) {
                                    Text("View product")
                                }
                            }
                        }
                        entry<ProductDetail>(
                            metadata = ListDetailSceneStrategy.detailPane()
                        ) { product ->
                            ContentBlue("Product ${product.id} ", Modifier.background(PastelBlue)) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Button(onClick = {
                                        backStack.add(Profile)
                                    }) {
                                        Text("View profile")
                                    }
                                }
                            }
                        }
                        entry<Profile>(
                            metadata = ListDetailSceneStrategy.extraPane()
                        ) {
                            ContentGreen("Profile")
                        }
                    }
                )
            }
        }
    }
}
// [END android_compose_navigation3_scenes_material_1]
```

**The nested variant** — a second `NavDisplay` inside one destination, so a settings section becomes
list-detail on wide windows and a normal stack on phones, with no branching.
`[REPO /root/work/repos/Tomato/shared/src/commonMain/kotlin/org/nsh07/pomodoro/ui/settingsScreen/SettingsScreen.kt lines 98–200]`:

```kotlin
    NavDisplay(
        backStack = backStack,
        onBack = backStack::onBack,
        transitionSpec = {
            (slideInHorizontally(initialOffsetX = { directionMultiplier * it }))
                .togetherWith(slideOutHorizontally(targetOffsetX = { directionMultiplier * -it / 4 }) + fadeOut())
        },
        popTransitionSpec = {
            (slideInHorizontally(initialOffsetX = { directionMultiplier * -it / 4 }) + fadeIn())
                .togetherWith(slideOutHorizontally(targetOffsetX = { directionMultiplier * it }))
        },
        predictivePopTransitionSpec = {
            (slideInHorizontally(initialOffsetX = { directionMultiplier * -it / 4 }) + fadeIn())
                .togetherWith(slideOutHorizontally(targetOffsetX = { directionMultiplier * it }))
        },
        sceneStrategy = rememberListDetailSceneStrategy(
            directive = calculatePaneScaffoldDirective(currentWindowAdaptiveInfo())
        ),
        entryProvider = entryProvider {
            entry<Screen.Settings.Main>(
                metadata = listPane(detailPlaceholder = { DetailPlaceholder(Res.drawable.settings_filled) })
            ) {
                SettingsMainScreen(...)
            }

            entry<Screen.Settings.About>(
                metadata = detailPane()
            ) {
                AboutScreen(...)
            }
            // ... one detailPane() entry per settings sub-screen
        },
        modifier = Modifier.background(topBarColors.containerColor)
    )
```

> Cited verbatim. Tomato calls the deprecated `currentWindowAdaptiveInfo()` and its own forked
> `calculatePaneScaffoldDirective` (see the custom-directive recipe). Use
> `currentWindowAdaptiveInfoV2()`.

**Pitfalls**

- **Never wrap nav3 entry content in `AnimatedPane`.** The scene strategy wraps entries itself. This
  is the most common nav3-adaptive error.
- **`ListDetailSceneStrategy` declines single-pane by default.** `shouldHandleSinglePaneLayout =
  false` means it returns `null` when only one pane would show, and Nav3 renders the entry normally
  with your `transitionSpec`. If single-pane transitions look wrong, that is why; set it to `true` to
  hand them to the Material scaffold.
- **`calculateScene` stops at the first entry without pane metadata.** Only a contiguous suffix of
  the back stack is grouped. An un-annotated entry pushed in the middle silently truncates the
  scaffold.
- **An entry with no metadata renders full-screen**, which is exactly how you get a "home" screen
  above a list-detail section (the androidx sample's `HomeKey`).
- **Guard every `backStack.add`** with `if (backStack.last() != dest)`, as both the androidx sample
  and the snippets repo do, or a double tap pushes duplicate entries.
- **`rememberListDetailSceneStrategy` keys on `paneExpansionDragHandle`.** Hoist the lambda or the
  strategy is rebuilt each recomposition.
- **`sceneStrategies = listOf(...)` vs `sceneStrategy = ...`** — parameter name differs by nav3
  version. `[UNVERIFIED]` which version introduced which; check autocomplete.
- **Do not hardcode `showBackButton = true`** the way nowinandroid does. Use
  `LocalListDetailSceneScope.current == null`.

Full API detail — metadata helpers, `SupportingPaneSceneStrategy`, scene scopes, nowinandroid's
production entry providers — is in `navigation-suite.md` §13.

---

# Recipe: supporting pane with the 70/30 split and a resize handle

**Use this when** the secondary content is meaningless on its own: comments on a document, a
now-playing queue, a tool palette, related videos. `[DOC]`: *"Secondary pane content is meaningful
only in relation to the primary content; for example, a supporting pane tool window is irrelevant by
itself. The supplementary content in the detail pane of a list-detail layout, however, is meaningful
even without the primary content."*

Sizing is specified `[DOC]`: *"For medium width, split the display space equally between the main
and supporting content. For expanded width, give 70% of the space to the main content, 30% to the
supporting content."* **The library does not do this for you** — you must set it.

```kotlin
@OptIn(ExperimentalMaterial3AdaptiveApi::class)
@Composable
fun DocumentWithComments(modifier: Modifier = Modifier) {
    val scope = rememberCoroutineScope()
    val navigator = rememberSupportingPaneScaffoldNavigator<Any>()

    // Anchor list gives the user snap points; 0.7 is the Material-specified default.
    val anchors = remember {
        listOf(
            PaneExpansionAnchor.Proportion(0.5f),
            PaneExpansionAnchor.Proportion(0.7f),
            PaneExpansionAnchor.Proportion(1f),   // supporting pane collapsed away
        )
    }
    val expansionState = rememberPaneExpansionState(
        keyProvider = navigator.scaffoldValue,
        anchors = anchors,
        initialAnchoredIndex = 1,                 // 70/30
    )

    val dragHandle: @Composable ThreePaneScaffoldScope.(PaneExpansionState) -> Unit =
        remember {
            { state ->
                val interactionSource = remember { MutableInteractionSource() }
                VerticalDragHandle(
                    modifier = Modifier
                        .paneExpansionDraggable(
                            state,
                            LocalMinimumInteractiveComponentSize.current,
                            interactionSource,
                        )
                        .systemGestureExclusion(),
                    interactionSource = interactionSource,
                )
            }
        }

    NavigableSupportingPaneScaffold(
        navigator = navigator,
        modifier = modifier,
        mainPane = {
            AnimatedPane { DocumentBody() }
        },
        supportingPane = {
            AnimatedPane {
                CommentsPane(
                    onClose = { scope.launch { navigator.navigateBack() } },
                )
            }
        },
        paneExpansionState = expansionState,
        paneExpansionDragHandle = dragHandle,
    )
}
```

To set the split imperatively instead of via anchors:

```kotlin
LaunchedEffect(Unit) { expansionState.setFirstPaneProportion(0.7f) }
```

`setFirstPaneProportion` throws outside `[0f, 1f]`. Precedence when rendering is **user drag >
`setFirstPaneWidth`/`setFirstPaneProportion` > directive defaults**, and setting either clears the
other and resets `currentAnchor` to `null`.

## The supporting pane's default is Reflow, not Hide

`[SRC@HEAD SupportingPaneScaffoldDefaults]`:

```kotlin
public fun adaptStrategies(
    mainPaneAdaptStrategy: AdaptStrategy = AdaptStrategy.Hide,
    supportingPaneAdaptStrategy: AdaptStrategy =
        AdaptStrategy.Reflow(SupportingPaneScaffoldRole.Main),
    extraPaneAdaptStrategy: AdaptStrategy = AdaptStrategy.Hide,
): ThreePaneScaffoldAdaptStrategies
```

Compare `ListDetailPaneScaffoldDefaults.adaptStrategies()`, where all three default to
`AdaptStrategy.Hide`. The supporting pane *reflows below the main content* on a single-pane layout —
that is the library implementing *"For compact-width displays, place the supporting content below
the main content."*

**Reflow requires `maxHorizontalPartitions == 1` AND `maxVerticalPartitions > 1`.** With the default
directive, `maxVerticalPartitions` is only 2 in tabletop posture or on a compact/medium-width window
with **expanded height (≥900dp)**. On a normal phone in portrait (compact width, medium height)
reflow does **not** engage and the pane hides instead. Do not promise users the stacked layout on
phones without checking.

**If you pass custom strategies you must re-specify the supporting pane** or you silently lose
reflow:

```kotlin
// WRONG — loses reflow
SupportingPaneScaffoldDefaults.adaptStrategies(
    extraPaneAdaptStrategy = AdaptStrategy.Hide,
)   // supportingPane still defaults to Reflow — this one is actually fine

// WRONG — explicitly kills it
SupportingPaneScaffoldDefaults.adaptStrategies(
    supportingPaneAdaptStrategy = AdaptStrategy.Hide,
)
```

Tomato does exactly the second, deliberately, for its timer screen
`[REPO /root/work/repos/Tomato/shared/src/androidMain/.../timerScreen/TimerScreen.kt lines 216–224]`:

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

and its drag handle `[same file, lines 823–837]`:

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
```

`Modifier.systemGestureExclusion()` is Tomato's addition and worth copying — without it a drag
handle near the screen edge fights the system back gesture.

The canonical androidx handle `[REPO androidx-m3:.../samples/ThreePaneScaffoldSample.kt lines 407–424]`:

```kotlin
@OptIn(ExperimentalMaterial3AdaptiveApi::class)
@Preview
@Sampled
@Composable
fun ThreePaneScaffoldScope.PaneExpansionDragHandleSample(
    state: PaneExpansionState = rememberPaneExpansionState()
) {
    val interactionSource = remember { MutableInteractionSource() }
    VerticalDragHandle(
        modifier =
            Modifier.paneExpansionDraggable(
                state,
                LocalMinimumInteractiveComponentSize.current,
                interactionSource,
            ),
        interactionSource = interactionSource,
    )
}
```

**Pitfalls**

- **`VerticalDragHandle` lives in `androidx.compose.material3`**, not the adaptive artifact.
- **Share one `MutableInteractionSource`** between `Modifier.paneExpansionDraggable` and the handle,
  or press/drag visuals never fire. The handle's `DragHandleSizes`/`DragHandleColors`/
  `DragHandleShapes` triples (default/pressed/dragged) are the Expressive shape-morph-on-interaction
  pattern and depend on that shared source.
- **`minTouchTargetSize` should be `LocalMinimumInteractiveComponentSize.current`** — the visual
  handle is thin; this guarantees a ≥48dp target.
- **A drag handle without `paneExpansionState` still works** — the scaffold creates a mutable default
  when `paneExpansionDragHandle != null`. But you cannot then set anchors or an initial split.
- **`animateTo(anchor)` throws if the anchor is not in the list** passed to
  `rememberPaneExpansionState`. `initialAnchoredIndex` out of range throws too.
- **`firstPaneWidth` / `firstPaneProportion` are write-only** from app code — internal getters. Only
  `currentAnchor` and `isUnspecified()` are readable.
- **`keyProvider = navigator.scaffoldValue` gives a split per layout configuration** (list/detail and
  detail/extra remember separate splits). `PaneExpansionStateKey.Default` gives one shared split for
  everything. Pick deliberately.
- **`defaultDragHandleSemantics` is deprecated.** Do not call it; `paneExpansionDraggable` installs
  default semantics when `semanticsProperties == null`.

---

# Recipe: feed / canonical feed layout

**Use this when** content is a homogeneous collection with no obvious detail hierarchy: news, social,
photos, a store front. `[DOC]`: *"A feed layout arranges equivalent content elements in a
configurable grid… Feeds are especially well suited for news and social media apps."*

**Feed has no dedicated scaffold.** It is `LazyVerticalGrid` + `GridCells.Adaptive`.

`[REPO snippets:.../adaptivelayouts/CanonicalLayoutSamples.kt lines 32–44]` verbatim:

```kotlin
// [START android_compose_canonical_layouts_sample_my_feed]
@Composable
fun MyFeed(names: List<String>) {
    LazyVerticalGrid(
        // GridCells.Adaptive automatically adapts column count based on available width
        columns = GridCells.Adaptive(minSize = 180.dp),
    ) {
        items(names) { name ->
            Text(name)
        }
    }
}
// [END android_compose_canonical_layouts_sample_my_feed]
```

`[DOC]`: *"The key to an adaptive feed is the `columns` configuration. `GridCells.Adaptive(minSize =
180.dp)` creates a grid where each column is at least `180.dp` wide. The grid then displays as many
columns as can fit in the available space."* And: *"On compact-width displays that don't have enough
space to show more than one column, `LazyVerticalGrid` behaves just like a `LazyColumn`."*

`GridCells.Adaptive` alone handles most feeds. Branch on the size class only when the *content* of a
cell should differ, not just the count — and then run the `when` **largest → smallest**:

```kotlin
@Composable
fun AdaptiveFeed(articles: List<Article>, modifier: Modifier = Modifier) {
    val windowSizeClass = currentWindowAdaptiveInfoV2().windowSizeClass

    // >= tests: the chain MUST go largest -> smallest.
    val (minCellSize, contentPadding) = when {
        windowSizeClass.isWidthAtLeastBreakpoint(WindowSizeClass.WIDTH_DP_EXTRA_LARGE_LOWER_BOUND) ->
            280.dp to 32.dp
        windowSizeClass.isWidthAtLeastBreakpoint(WindowSizeClass.WIDTH_DP_LARGE_LOWER_BOUND) ->
            260.dp to 24.dp
        windowSizeClass.isWidthAtLeastBreakpoint(WindowSizeClass.WIDTH_DP_EXPANDED_LOWER_BOUND) ->
            240.dp to 24.dp
        windowSizeClass.isWidthAtLeastBreakpoint(WindowSizeClass.WIDTH_DP_MEDIUM_LOWER_BOUND) ->
            200.dp to 16.dp
        else -> 160.dp to 16.dp
    }

    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = minCellSize),
        contentPadding = PaddingValues(contentPadding),
        horizontalArrangement = Arrangement.spacedBy(contentPadding),
        verticalArrangement = Arrangement.spacedBy(contentPadding),
        modifier = modifier,
    ) {
        // Full-width section header — maxLineSpan is the documented idiom.
        item(span = { GridItemSpan(maxLineSpan) }) { FeedHeader() }

        // Hero item: emphasize by spanning, not by a separate layout.
        item(span = { GridItemSpan(minOf(2, maxLineSpan)) }) { HeroCard(articles.first()) }

        items(articles.drop(1), key = { it.id }) { ArticleCard(it) }
    }
}
```

`[DOC]`: *"When defining grid items, adjust column spans to emphasize some items over others. For
section headers, dividers, or other items designed to occupy the full width of the feed, use
`maxLineSpan` to take up the full width of the layout."*

**Pitfalls**

- **A `when` chain in the wrong order collapses to the smallest branch.** `isWidthAtLeastBreakpoint`
  is `>=`, so a 1600dp window matches *every* breakpoint. Largest first, always.
- **Do not cap the grid at three columns with `GridCells.Fixed`** because "tablets have three
  columns". A 1600dp desktop window then renders 500dp-wide cards.
- **`maxLineSpan` is only available inside the `span` lambda** — it is a property of
  `LazyGridItemSpanScope`, not something you can read outside.
- **Give `items` a stable `key`.** Without it, a resize that changes column count loses item state
  and re-runs enter animations.
- On very wide windows, constrain the grid's own width (`Modifier.widthIn(max = 1600.dp)`) rather
  than letting line length grow forever.

---

# Recipe: three panes on Large / XL

**Use this when** the window is ≥1200dp — a large tablet, a desktop window, a connected display.
`calculatePaneScaffoldDirective` gives you three horizontal partitions there automatically:

| Width bucket (`minWidth`) | `maxHorizontalPartitions` | `horizontalPartitionSpacerSize` | `defaultPanePreferredWidth` |
| --- | --- | --- | --- |
| Compact (0dp) | **1** | 0.dp | 360.dp |
| Medium (600dp) | **1** | 0.dp | 360.dp |
| Expanded (840dp) | **2** | 24.dp | 360.dp |
| Large (1200dp) | **3** | 24.dp | **412.dp** |
| Extra-large (1600dp) | **3** | 24.dp | **412.dp** |

You get the third pane for free by passing `extraPane = { … }`. The hard part is what goes in it.

## What to put in the third pane

The `Extra` pane must be **content the user chose to open**, not filler. `AdaptStrategy` enforces
this: `Levitate` only applies *when the pane is the current destination*, and the default
`AdaptStrategy.Hide` means an extra pane the user never opened simply is not there.

Good third-pane content:

- Attachments / related items for the currently-open detail (mail).
- A comment thread on the open document.
- Track details or lyrics for the playing item.
- A properties/inspector panel the user toggled on.

Bad third-pane content, and what to do instead:

| Filler | Do this instead |
| --- | --- |
| A second copy of the nav destinations | Widen the rail: override to `WideNavigationRailExpanded` at ≥1200dp |
| Ads / promos | Nothing. Let the two panes breathe. |
| A permanently-empty "select something" panel | Only compose `extraPane` when there is something to show |
| Duplicated detail metadata | Fold it into the detail pane; a wider `defaultPanePreferredWidth` (412dp at Large/XL) already gives it room |

## Only offering three panes when there is a third thing

```kotlin
@OptIn(ExperimentalMaterial3AdaptiveApi::class)
@Composable
fun MailThreePane(items: List<Mail>) {
    val scope = rememberCoroutineScope()
    val navigator = rememberListDetailPaneScaffoldNavigator<MailKey>()
    val selected = navigator.currentDestination?.contentKey
    val hasAttachments = selected?.let { attachmentsFor(it).isNotEmpty() } == true

    NavigableListDetailPaneScaffold(
        navigator = navigator,
        listPane = { AnimatedPane(Modifier.preferredWidth(360.dp)) { MailList(...) } },
        detailPane = {
            AnimatedPane {
                MailDetail(
                    key = selected,
                    // Do not offer the third pane when there is nothing for it.
                    onShowAttachments = if (hasAttachments) {
                        { scope.launch { navigator.navigateTo(ListDetailPaneScaffoldRole.Extra, selected) } }
                    } else null,
                )
            }
        },
        // Pass null, not an empty composable, when there is no third pane.
        extraPane = if (hasAttachments) {
            { AnimatedPane { AttachmentsPane(selected) } }
        } else null,
    )
}
```

`extraPane` is `(@Composable ThreePaneScaffoldPaneScope.() -> Unit)? = null` — passing `null` is the
supported way to say "this scaffold has two panes".

## The alternative: levitate the third pane instead of docking it

When the third pane is transient (a picker, a detail-of-detail), levitate it as a dialog or sheet
rather than giving it a permanent partition. `[REPO androidx-m3:.../samples/ThreePaneScaffoldSample.kt lines 439–478]`:

```kotlin
/**
 * This sample shows how to create a [ThreePaneScaffoldNavigator] that will show the extra pane as a
 * modal dialog when the extra pane is the current destination. The dialog will be centered in the
 * scaffold, with a scrim that clicking on it will dismiss the dialog.
 */
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

Note the forward-reference trick (`var navigator: … ? = null`, then assign) so the scrim can call
`navigator?.navigateBack()`, and `.onlyIfSinglePane(scaffoldDirective)` — levitate on phones, dock
as a real pane on wide windows.

Bottom-sheet variant `[same file, lines 480–514]`:

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

paired with `[same file, lines 315–369]`:

```kotlin
        extraPane = {
            AnimatedPane(
                modifier =
                    Modifier.preferredWidth(1f)
                        .preferredHeight(0.5f)
                        .background(MaterialTheme.colorScheme.surface),
                dragToResizeHandle = { BottomSheetDefaults.DragHandle() },
            ) {
                ExtraPaneContent(...)
            }
        },
```

**Pitfalls**

- **`AdaptStrategy.Levitate.equals` uses identity for `scrim` and `dragToResizeState`.** An inline
  scrim lambda produces a new, unequal strategy every recomposition and the scaffold value churns.
  Hoist it.
- **Levitation only applies to the current destination.** `Levitate` on a non-current pane yields
  `Hidden`, not a floating pane.
- **`AnimatedPane`'s proportional overloads** (`preferredWidth(1f)`, `preferredHeight(0.5f)`) are
  distinct from the `Dp` overloads. `Float` = proportion of scaffold, `Dp` = absolute.
- **A levitated pane needs an explicit background.** It is not a `Surface`.
- `Reflowed`/`Levitated` panes are still **composed** — `AnimatedPane`'s visibility predicate is
  `value[paneRole] != PaneAdaptedValue.Hidden`. Do not assume "not expanded" means "not running".

---

# Recipe: tabletop-aware layout

**Use this when** the app has primary media or a viewport plus controls: video, camera, maps, a
timer, a call. `[DOC]`: *"Place video/primary content above fold; controls and supplementary content
below fold."*

Tabletop = phone on a surface, **horizontal** hinge, half-opened. The library exposes it as
`Posture.isTabletop`, and it is the one posture the directive calculation reacts to.

```kotlin
@OptIn(ExperimentalMaterial3AdaptiveApi::class)
@Composable
fun PlayerScreen(modifier: Modifier = Modifier) {
    val info = currentWindowAdaptiveInfoV2()

    if (info.windowPosture.isTabletop) {
        // Two vertical partitions: content above the fold, controls below.
        Column(modifier.fillMaxSize()) {
            VideoSurface(
                Modifier
                    .fillMaxWidth()
                    .weight(1f),          // upper half — above the hinge
            )
            PlayerControls(
                Modifier
                    .fillMaxWidth()
                    .weight(1f),          // lower half — below the hinge, thumb zone
            )
        }
    } else {
        Box(modifier.fillMaxSize()) {
            VideoSurface(Modifier.fillMaxSize())
            PlayerControls(Modifier.align(Alignment.BottomCenter))
        }
    }
}
```

The pane scaffolds react automatically: `[SRC@HEAD calculatePaneScaffoldDirective]`

```kotlin
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
```

so in tabletop a `SupportingPaneScaffold` reflows its supporting pane under the main pane with a
24dp gutter, without any code from you. `NavigationSuiteScaffoldDefaults.navigationSuiteType()` also
switches to `ShortNavigationBarMedium` (bottom bar) in tabletop.

## Detecting postures the library does not expose

There is **no `Posture.isBookPosture`** and no `WindowPosture` type. Book posture (vertical hinge) is
handled implicitly: a separating vertical hinge lands in `separatingVerticalHingeBounds` →
`excludedBounds` → the scaffold splits panes around the hinge. If you need it explicitly, compute it
from raw folding features `[DOC]`:

```kotlin
fun isTableTopPosture(foldFeature: FoldingFeature?): Boolean {
    contract { returns(true) implies (foldFeature != null) }
    return foldFeature?.state == FoldingFeature.State.HALF_OPENED &&
        foldFeature.orientation == FoldingFeature.Orientation.HORIZONTAL
}

fun isBookPosture(foldFeature: FoldingFeature?): Boolean {
    contract { returns(true) implies (foldFeature != null) }
    return foldFeature?.state == FoldingFeature.State.HALF_OPENED &&
        foldFeature.orientation == FoldingFeature.Orientation.VERTICAL
}
```

The tabletop helper is byte-identical to what `calculatePosture` does for `Posture.isTabletop`.

In Compose, collect folding features with `collectFoldingFeaturesAsState()` from the `adaptive`
artifact `[SRC@HEAD]`:

```kotlin
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

**Pitfalls**

- **`collectFoldingFeaturesAsState()` emits `emptyList()` first.** The first frame has no posture
  info. Never make an irreversible decision (starting playback, committing a transaction) on it.
- **Only *vertical* hinges become `excludedBounds`.** Horizontal (tabletop) hinges affect layout only
  through `isTabletop → maxVerticalPartitions = 2`.
- **`FoldingFeature` does not expose a hinge angle** `[DOC]`, and sensor accuracy varies by device.
- **Dual-screen devices**: `[DOC]` *"Always use layouts for tabletop/book even when `FLAT` if
  `isSeparating == true`."*
- **`occlusionType`**: `FULL` means do not place content in the fold area at all; `NONE` means
  content may span it.
- Do not place interactive controls right against a separating hinge — `[DOC]` calls them difficult
  to reach.
- `HingePolicy` selects which bounds are excluded: `AvoidSeparating` (**default**),
  `AvoidOccluding`, `AlwaysAvoid`, `NeverAvoid`. Its `toString()` does not match the property names
  (`AvoidSeparating` prints `"HingePolicy.AvoidOccludingAndSeparating"`) — never parse it.

---

# Recipe: adaptive + Expressive together

**Use this when** you have both an Expressive design (short nav bars, wide rails, floating toolbars,
FAB menus) and an adaptive layout, and you need them to coexist without fighting.

## Rule 1 — one nav container per window, chosen by size

| Window | Container | Where it comes from |
| --- | --- | --- |
| Compact width (<600dp) | `ShortNavigationBar`, vertical items | `NavigationSuiteType.ShortNavigationBarCompact` |
| Compact height (<480dp) or tabletop | `ShortNavigationBar`, horizontal items | `NavigationSuiteType.ShortNavigationBarMedium` |
| Medium / Expanded width (600–1199dp) | collapsed `WideNavigationRail` | `NavigationSuiteType.WideNavigationRailCollapsed` |
| Large / XL width (≥1200dp) | expanded `WideNavigationRail` | `NavigationSuiteType.WideNavigationRailExpanded` — **you must select this yourself** |

`NavigationSuiteScaffoldDefaults.navigationSuiteType()` covers the first three rows and stops. It
never returns `WideNavigationRailExpanded`, `NavigationDrawer` or `None`. The ≥1200dp row requires an
override — see `navigation-suite.md` §5.

## Rule 2 — never two nav containers

The three ways this happens, all of which look fine on a phone:

1. `NavigationSuiteScaffold` wrapping a `Scaffold(bottomBar = { ShortNavigationBar(...) })`. At
   compact width you now have two bars stacked.
2. A `HorizontalFloatingToolbar` used as navigation *plus* the suite scaffold. The toolbar floats
   over the bar.
3. A rail rendered per-screen inside the nav host while the suite scaffold also renders one at
   medium width.

If you use a toolbar as your nav container, do not use `NavigationSuiteScaffold` at all — hand-roll
the size switch. That pattern (`FloatingToolbarDefaults.exitAlwaysScrollBehavior()`, the exit
directions, and the item APIs) is in
`${CLAUDE_PLUGIN_ROOT}/skills/m3-expressive-navigation/references/nav-containers.md`.

## Rule 3 — the FAB belongs to the suite scaffold, not to your Scaffold

`primaryActionContent` places a FAB correctly per container: *"It'll be displayed inside vertical
navigation components as part of their header, and above horizontal navigation components."*
Alignment defaults to `Alignment.End`.

```kotlin
val navSuiteType =
    NavigationSuiteScaffoldDefaults.navigationSuiteType(currentWindowAdaptiveInfoV2())

NavigationSuiteScaffold(
    navigationSuiteType = navSuiteType,
    navigationItemVerticalArrangement = Arrangement.Center,
    primaryActionContent = {
        FloatingActionButton(onClick = ::compose) {
            Icon(Icons.Default.Edit, contentDescription = stringResource(R.string.compose))
        }
    },
    navigationItems = { /* NavigationSuiteItem per destination */ },
) {
    NavDisplay(...)
}
```

That is one API instead of a per-size-class FAB placement branch. If you instead put the FAB in an
inner `Scaffold`, it will overlap the bar at compact width and sit in dead space beside the rail at
expanded width.

## Rule 4 — a floating toolbar inside a pane is fine; a floating toolbar as nav inside a suite scaffold is not

Contextual floating toolbars *within* a detail pane (formatting, playback, selection actions) coexist
with the nav container by design — they are content, not navigation. Anchor them inside the pane, not
to the window.

`[UNVERIFIED]` — no source-level coupling between the adaptive artifacts and
`FloatingToolbar`/`TopAppBar` was found; `material3/samples/AppBarSamples.kt` is the only material3
sample importing anything adaptive. Treat toolbar-in-pane placement as ordinary layout work.

## Rule 5 — the Expressive touches that adaptive layouts specifically unlock

1. **`VerticalDragHandle`** with its `DragHandleSizes` / `DragHandleColors` / `DragHandleShapes`
   triples (default / pressed / dragged) — shape-morph on interaction, the Expressive signature —
   is the recommended handle for `Modifier.paneExpansionDraggable`. Under-used.
2. **`NavigationSuiteColors`** carries `shortNavigationBarContainerColor`,
   `shortNavigationBarContentColor` and a whole `WideNavigationRailColors`, defaulted from
   `ShortNavigationBarDefaults` and `WideNavigationRailDefaults.colors()`. Theme the Expressive
   containers there, not with `NavigationSuiteItemColors` (which only covers the legacy trio).
3. **`navigationItemVerticalArrangement = Arrangement.Bottom`** bottom-aligns rail items into the
   thumb zone on tall windows.
4. **Pane motion** — `AnimatedPane`'s `enterTransition` / `exitTransition` / `boundsAnimationSpec`
   default to `PaneMotionDefaults`. Override per-pane for expressive spatial springs; see the
   `m3-expressive-motion` skill.
5. **`AnimatedPane(shape = …)`** for Expressive corner shapes on panes is **post-1.3.0**. Not
   available on 1.3.0 — do not write it yet.

**Pitfalls**

- **`NavigationSuiteType` is a value class over `String`.** No exhaustive `when`; a `when`
  *expression* needs an `else`.
- **Do not write `TonalToggleButton`** — the component is `FilledTonalToggleButton`.
- **Do not write `ToggleButtonDefaults.shapes(...)`** — it is `shapesFor(Dp)` or the
  `ToggleButtonShapes(...)` constructor.
- **Do not write `SplitButton`** — the composable is `SplitButtonLayout`, and it is not deprecated
  (verified in `compose/material3/material3/api/current.txt` at androidx HEAD `360e8cba`, 2026-08-14).
- Legacy `NavigationSuiteType` values (`NavigationBar`, `NavigationRail`, `NavigationDrawer`) render
  classic M3 components, not Expressive ones. Their KDoc says to prefer the newer four. If your app
  looks non-Expressive despite Expressive theming, check which overload of
  `NavigationSuiteScaffold` you called — `navigationSuiteItems` defaults to the legacy calculator.

---

# Recipe: custom directive (0dp gutter, connected-surface look)

**Use this when** the default 24dp gutter between panes reads as two disconnected cards and you want
one continuous surface — a common Expressive choice.

## The cheap way — copy the default and change one field

```kotlin
val directive = calculatePaneScaffoldDirective(currentWindowAdaptiveInfoV2())
    .copy(horizontalPartitionSpacerSize = 0.dp)

val navigator = rememberListDetailPaneScaffoldNavigator<MailKey>(
    scaffoldDirective = directive,
)
```

Two things to know about `copy`:

- Its parameter order is odd — `excludedBounds` comes **before** `defaultPanePreferredHeight`. Use
  named arguments.
- **`copy` does not expose `shouldAutoFocusCurrentDestination` and always resets it to `true`**,
  because it routes through the 7-arg constructor. `equals`/`hashCode` ignore that field anyway.

## The Tomato way — fork the calculator

Tomato forked the androidx directive calculator solely to zero the gutter. The docstring says so.
`[REPO /root/work/repos/Tomato/shared/src/commonMain/kotlin/org/nsh07/pomodoro/ui/UiUtils.kt lines 69–150]`
verbatim:

```kotlin
/**
 * (Copied from [androidx.compose.material3.adaptive.layout.calculatePaneScaffoldDirective] with
 * minor modifications, namely the reduction of horizontalPartitionSpacerSize to 0.dp)
 *
 * Calculates the recommended [PaneScaffoldDirective] from a given [WindowAdaptiveInfo]. ...
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
    val maxVerticalPartitions: Int
    val verticalPartitionSpacerSize: Dp

    if (
        windowAdaptiveInfo.windowPosture.isTabletop ||
        (maxHorizontalPartitions == 1 &&
                windowAdaptiveInfo.windowSizeClass.minHeightDp ==
                WindowSizeClass.WIDTH_DP_EXPANDED_LOWER_BOUND)
    ) {
        maxVerticalPartitions = 2
        verticalPartitionSpacerSize = 24.dp
    } else {
        maxVerticalPartitions = 1
        verticalPartitionSpacerSize = 0.dp
    }

    val defaultPanePreferredHeight = 420.dp

    return PaneScaffoldDirective(
        maxHorizontalPartitions = maxHorizontalPartitions,
        horizontalPartitionSpacerSize = horizontalPartitionSpacerSize,
        maxVerticalPartitions = maxVerticalPartitions,
        verticalPartitionSpacerSize = verticalPartitionSpacerSize,
        defaultPanePreferredWidth = defaultPanePreferredWidth,
        defaultPanePreferredHeight = defaultPanePreferredHeight,
        excludedBounds = when (verticalHingePolicy) {
            HingePolicy.AvoidSeparating -> windowAdaptiveInfo.windowPosture.separatingVerticalHingeBounds
            HingePolicy.AvoidOccluding -> windowAdaptiveInfo.windowPosture.occludingVerticalHingeBounds
            HingePolicy.AlwaysAvoid -> windowAdaptiveInfo.windowPosture.allVerticalHingeBounds
            else -> emptyList()
        }
    )
}
```

**Read this fork as a warning, not a template.** Two bugs are visible in it:

1. `windowSizeClass.minHeightDp == WindowSizeClass.WIDTH_DP_EXPANDED_LOWER_BOUND` compares a
   **height** against the **width** constant (840 vs the correct `HEIGHT_DP_EXPANDED_LOWER_BOUND` =
   900). The tall-window branch therefore never fires as intended.
2. The `when` switches on `minWidthDp` with equality against the library's own bucket constants — it
   works only because `minWidthDp` is snapped to a bucket lower bound, and it silently loses the
   Large/XL distinction that `defaultPanePreferredWidth = 412.dp` was supposed to encode (both fall
   into the same `else`).

Forking also forfeits future androidx breakpoint fixes. **Prefer `.copy()`.** Fork only when you need
to change the `when` shape itself — genuinely different breakpoints — and then copy the *current*
androidx source, not this one.

## Other legitimate directive overrides

```kotlin
val info = currentWindowAdaptiveInfoV2()
val base = calculatePaneScaffoldDirective(info)

// Cap at two panes: the third pane has nothing meaningful to show even at 1600dp.
val twoPaneMax = base.copy(maxHorizontalPartitions = minOf(base.maxHorizontalPartitions, 2))

// Wider fixed pane for a text-heavy detail.
val wideList = base.copy(defaultPanePreferredWidth = 420.dp)

// Ignore hinges entirely (rare — usually wrong on separating hinges).
val hingeless = calculatePaneScaffoldDirective(info, HingePolicy.NeverAvoid)

// Two panes at medium width. Google recommends against this; see below.
val dense = calculatePaneScaffoldDirectiveWithTwoPanesOnMediumWidth(info)
```

`calculatePaneScaffoldDirectiveWithTwoPanesOnMediumWidth` KDoc `[SRC@HEAD]`: *"We recommend to use
`[calculatePaneScaffoldDirective]`, unless you have a strong use case to show two panes on a
medium-width window, which can make your layout look too packed."* It also resets
`shouldAutoFocusCurrentDestination` to `true`, because it goes through `copy()`.

**Pitfalls**

- **Pass the custom directive everywhere.** `rememberListDetailPaneScaffoldNavigator(scaffoldDirective
  = …)`, `rememberListDetailSceneStrategy(directive = …)`, and `ListDetailPaneScaffold(directive = …)`
  if you use the raw scaffold. Miss one and it silently uses androidx defaults, giving you two
  different layouts in the same app.
- **`PaneScaffoldDirective.DefaultPreferredWidth` / `DefaultPreferredWidthXL` /
  `DefaultPreferredHeight` are `internal`.** You cannot reference them; the values are 360.dp,
  412.dp, 420.dp. Write the literals.
- **`adaptive-layout` hard-codes 1200/1600 for Large/XL** rather than using the `window-core`
  constants (there is a `TODO(conradchen)` about it), so directive behaviour is stable even on older
  `window-core`.
- A 0dp gutter without a drag handle reads as one surface; **with** a drag handle it reads as a
  seam. If you zero the gutter, either drop the handle or give the panes distinct container colors.

---

# Recipe: making a phone-only screen adaptive

**Use this when** you have a working single-pane screen and need it to earn its keep on a tablet.
Do it in this order; each step ships independently.

**Step 0 — remove the blockers.** `[DOC]`, for API 36+: remove `screenOrientation` from the manifest,
remove `minAspectRatio`/`maxAspectRatio`, set `resizeableActivity="true"`. Android 16 ignores those
restrictions on displays ≥sw600dp anyway, and the temporary opt-out property
(`android.window.PROPERTY_COMPAT_ALLOW_RESTRICTED_RESIZABILITY`) is removed for apps targeting API 37.

**Step 1 — add the adaptive shell.** Wrap the app in `NavigationSuiteScaffold` (first recipe). The
container now switches by size. Nothing else changes. Ship it.

**Step 2 — audit for device-type checks.** Delete every one of these:

```kotlin
val isTablet = LocalConfiguration.current.screenWidthDp >= 600      // WRONG
val isTablet = resources.getBoolean(R.bool.is_tablet)               // WRONG
```

`Configuration.screenWidthDp` is the **screen**, not your window — in split-screen, freeform or
desktop windowing you own a fraction of it and will render a rail in a 300dp window. Resource
qualifiers (`values-sw600dp`) bake the decision at inflation and never react to resize. Replace with
`currentWindowAdaptiveInfoV2().windowSizeClass` + `isWidthAtLeastBreakpoint`.

`[DOC]`: *"Window size classes are determined by available window space, not physical device
type — window size class is NOT for 'isTablet'-type logic."*

**Step 3 — make the feed/grid adaptive.** `GridCells.Fixed(2)` → `GridCells.Adaptive(minSize = …)`.
One line, immediate payoff, no state changes. Ship it.

**Step 4 — hoist selection state out of navigation.** Before you can show two panes, "which item is
selected" must live somewhere both panes can read. Move it from a nav argument into a `ViewModel`, or
plan to derive it from the scaffold navigator / back stack. This is the actual work of the migration;
the scaffold is the easy part.

**Step 5 — introduce the pane scaffold.** Replace the list screen + detail screen pair with
`NavigableListDetailPaneScaffold`. Your existing composables become `listPane` and `detailPane`
contents with no internal changes. Two adjustments:

- Selection comes from `navigator.currentDestination?.contentKey`.
- The detail's back button becomes conditional on `maxHorizontalPartitions == 1`.

**Step 6 — audit fixed sizes.** `Modifier.width(360.dp)` inside a pane fights the scaffold. Use
`PaneScaffoldScope.preferredWidth` on the `AnimatedPane` instead, and let content fill.

**Step 7 — verify.** `[DOC]` testing tools: `@PreviewScreenSizes`, `@PreviewFontScale`,
`@PreviewLightDark`, `DeviceConfigurationOverride`, host-side screenshots, the resizable / Pixel Fold
/ Pixel Tablet / desktop emulators.

Steal nowinandroid's testability pattern while you are here: hoist adaptive info as a parameter with
a default, so screenshot tests can inject it.

```kotlin
@Composable
fun MyScreen(
    windowAdaptiveInfo: WindowAdaptiveInfo = currentWindowAdaptiveInfoV2(),
) { /* ... */ }
```

## When NOT to add pane scaffolds

Not every screen needs one. Legitimate lighter patterns from the corpus:

- **JetNews** reduces the whole size-class system to **one boolean** and threads it down
  `[REPO /root/work/repos/compose-samples/JetNews/app/src/main/java/com/example/jetnews/ui/JetnewsApp.kt]`:

  ```kotlin
  import androidx.window.core.layout.WindowSizeClass
  // ...
  val windowSizeClass = currentWindowAdaptiveInfo().windowSizeClass
  val isExpandedScreen = remember(windowSizeClass) {
      windowSizeClass.isWidthAtLeastBreakpoint(WindowSizeClass.WIDTH_DP_EXPANDED_LOWER_BOUND)
  }
  ```

  > Cited verbatim; uses the deprecated `currentWindowAdaptiveInfo()`. It drives drawer permanence,
  > gesture enablement and a two-column reader — no pane scaffolds at all. A legitimate pattern for a
  > reader.

- **Reply** uses `NavigationSuiteScaffoldLayout` + `accompanist-adaptive`'s `TwoPane` and never
  touches `ListDetailPaneScaffold`.
- **vivi-music** puts a `ListDetailPaneScaffold` *inside a sheet* (`CommentSheet.kt`) rather than at
  the app root — unusual but legal.
- **Med** hand-rolls `if (isExpanded) NavigationRail else NavigationBar` with no adaptive library at
  all.

If the screen is a single scrolling surface with no natural secondary content, a max-width constraint
and an adaptive grid is the whole job.

---

# Troubleshooting

## Panes not appearing

**Symptom:** two panes on a 900dp emulator, one pane on a 700dp tablet, and you expected two.

- **Medium width is single-pane by design.** `calculatePaneScaffoldDirective` returns
  `maxHorizontalPartitions = 1` for both Compact (0dp) and Medium (600dp) buckets. Two panes start at
  Expanded (840dp). Verify with `navigator.scaffoldDirective.maxHorizontalPartitions`.
- Use `calculatePaneScaffoldDirectiveWithTwoPanesOnMediumWidth` only if you accept the KDoc's
  "too packed" warning.
- **You passed a custom directive to the navigator but not to the scaffold** (or vice versa). They
  must be the same object.
- **You are on `currentWindowAdaptiveInfo()`**, whose `supportLargeAndXLargeWidth` defaults to
  `false`, clamping everything ≥840dp to Expanded. You never reach the 3-partition Large/XL branch.
  Switch to `currentWindowAdaptiveInfoV2()`.
- **Nav3 only:** the top entry has no pane metadata, so `calculateScene` returned `null` and Nav3
  rendered it full-screen. Or an un-annotated entry sits between your list and detail entries and
  truncated the contiguous suffix.
- **Nav3 only:** `shouldHandleSinglePaneLayout = false` (default) means the strategy declines when
  one pane would show — expected, not a bug.
- **Reflow specifically:** it needs `maxHorizontalPartitions == 1` **and** `maxVerticalPartitions > 1`.
  On a phone in portrait (compact width, medium height) `maxVerticalPartitions` is 1, so the
  supporting pane hides instead of stacking. Only tabletop posture or an expanded-height (≥900dp)
  window enables it.
- **Levitation:** `AdaptStrategy.Levitate` only applies when the pane is the *current destination*.
  Otherwise the pane is `Hidden`.

## Wrong pane on back

- **Default is `PopUntilScaffoldValueChange`**, which pops until the *layout* changes. In a two-pane
  layout, going Item 1 → Item 2 → back may exit the app, because no pane changed state. If users
  expect to return to Item 1's content, use `PopUntilContentChange`.
- **Using the raw `ListDetailPaneScaffold` without `ThreePaneScaffoldPredictiveBackHandler`.** No
  handler means system back bypasses the scaffold entirely. `Navigable*PaneScaffold` adds it for you.
- **Two back stacks fighting.** With Navigation 2 you have `navController` *and* `scaffoldNavigator`.
  The androidx sample's comment is the rule: *"`navController` handles navigation outside the
  ListDetailPaneScaffold, and `scaffoldNavigator` handles navigation within it."* Do not let both
  handle the same gesture.
- **`isDestinationHistoryAware`** (default `true`) makes scaffold-value calculation consider the full
  destination history. Setting it `false` considers only the current destination and changes what
  "back" resolves to.
- **Predictive back shows nothing on Android 15 or lower** without
  `android:enableOnBackInvokedCallback="true"` in the manifest `<application>`.
- **`navigateBack` returns `false` and clears the whole history** when there is no previous
  destination — check the return value if you chain behaviour off it.

## State lost on resize

- **Navigation 2: missing `saveState`/`restoreState`.** The `popUpTo(startDestination) { saveState =
  true }` + `launchSingleTop` + `restoreState = true` triple is mandatory. Without it, a container
  swap re-creates every screen.
- **Content key not Bundle-storable.** `rememberListDetailPaneScaffoldNavigator` is
  `rememberSaveable`; a non-`Parcelable` `T` fails on process-death restore. Use `@Parcelize`.
- **State held in a plain `remember` inside a pane that gets hidden.** `AnimatedPane` provides
  `SaveableStateProvider(paneRole.toString())`, so `rememberSaveable` survives — plain `remember`
  does not.
- **Recomputing window info per screen.** Calling `currentWindowAdaptiveInfoV2()` in five places
  gives five recompositions and, if any of them uses the deprecated variant, disagreeing answers at
  the boundary. Compute once and hoist.
- **Directive/strategy rebuilt every recomposition.** `rememberListDetailSceneStrategy` keys on
  `paneExpansionDragHandle`; an inline lambda rebuilds the strategy and resets pane expansion. Same
  for `AdaptStrategy.Levitate`, whose `equals` compares `scrim` and `dragToResizeState` by
  **identity** — an inline scrim churns the scaffold value on every frame.
- `[DOC]`: retain state across configuration changes via `ViewModel` or
  `Activity.onSaveInstanceState()`. Window resize, posture change, density and font-scale changes are
  all configuration changes.

## Drag handle not draggable

- **`Modifier.paneExpansionDraggable` not applied**, or applied to the wrong element. It must be on
  the handle composable itself.
- **`minTouchTargetSize` too small.** Pass `LocalMinimumInteractiveComponentSize.current`; the visual
  handle is only a few dp wide.
- **`interactionSource` not shared** between the modifier and `VerticalDragHandle`. Press/drag
  visuals then never fire, which reads as "not draggable" even when it is.
- **The system back gesture is eating the drag** near a screen edge. Add
  `Modifier.systemGestureExclusion()`, as Tomato does.
- **Only one pane is expanded.** There is nothing to resize in a single-pane layout; the handle is
  not rendered.
- **`paneExpansionState` provided but immutable.** When you pass your own state, the scaffold does not
  wrap it; the auto-created default is only made mutable when `paneExpansionDragHandle != null`.
- **`animateTo(anchor)` throws** if the anchor is not in the list you passed to
  `rememberPaneExpansionState`. So does an out-of-range `initialAnchoredIndex`.
- **You are reading `firstPaneWidth`/`firstPaneProportion` to debug.** You cannot — the getters are
  internal. Only `currentAnchor` and `isUnspecified()` are readable.

## `when` chain collapsing to compact

**Symptom:** every window size takes the same branch, usually the smallest.

`isWidthAtLeastBreakpoint` is `>=` — a 1600dp window satisfies *every* breakpoint. The class KDoc is
explicit: *"these methods are order dependent as the smaller `minWidthDp` and `minHeightDp` would
match all the breakpoints that are larger. Therefore when processing the selection should normally be
ordered from larger to smaller breakpoints."*

```kotlin
// WRONG — every window ≥600dp takes the first branch
when {
    wsc.isWidthAtLeastBreakpoint(WindowSizeClass.WIDTH_DP_MEDIUM_LOWER_BOUND) -> medium()
    wsc.isWidthAtLeastBreakpoint(WindowSizeClass.WIDTH_DP_LARGE_LOWER_BOUND) -> large()   // dead
    else -> compact()
}

// RIGHT — largest to smallest
when {
    wsc.isWidthAtLeastBreakpoint(WindowSizeClass.WIDTH_DP_EXTRA_LARGE_LOWER_BOUND) -> xl()      // >= 1600
    wsc.isWidthAtLeastBreakpoint(WindowSizeClass.WIDTH_DP_LARGE_LOWER_BOUND)       -> large()   // >= 1200
    wsc.isWidthAtLeastBreakpoint(WindowSizeClass.WIDTH_DP_EXPANDED_LOWER_BOUND)    -> expanded()// >= 840
    wsc.isWidthAtLeastBreakpoint(WindowSizeClass.WIDTH_DP_MEDIUM_LOWER_BOUND)      -> medium()  // >= 600
    else -> compact()
}
```

Related failures in the same family:

- **`containsWidthDp` does not exist.** If you have it in your notes, it was never in this API. The
  predicates are `isWidthAtLeastBreakpoint(Int)`, `isHeightAtLeastBreakpoint(Int)`,
  `isAtLeastBreakpoint(Int, Int)`. Raw values are `minWidthDp` / `minHeightDp` (Ints).
- **Only three width buckets in your chain.** There are five: 0 / 600 / 840 / 1200 / 1600. A chain
  that stops at Expanded gives a 2000dp desktop window the tablet layout.
- **Looking for Large/XL heights.** There are none — `HEIGHT_DP_BREAKPOINTS_V2 =
  HEIGHT_DP_BREAKPOINTS_V1`. Height is 0 / 480 / 900, full stop.
- **Three different `WindowSizeClass` types on the classpath**:
  `androidx.window.core.layout.WindowSizeClass` (current, what the scaffolds consume),
  `androidx.compose.material3.windowsizeclass.WindowSizeClass` (legacy, Activity-scoped, no posture),
  and the deprecated `WindowWidthSizeClass`/`WindowHeightSizeClass` enums. Import carefully; the
  compiler will happily accept the wrong one.
- **You are on `BREAKPOINTS_V1`.** V1 is 3 widths × 3 heights = 9 classes; V2 is 5 × 3 = 15.
  `currentWindowAdaptiveInfoV2()` uses V2; the deprecated `currentWindowAdaptiveInfo()` defaults to
  V1 behaviour.

## Two nav containers appearing

**Symptom:** a bar and a rail at once, two bars stacked, or a floating toolbar over a nav bar.

- **A `Scaffold(bottomBar = …)` inside `NavigationSuiteScaffold`.** Delete the `bottomBar`. If you
  need the inner `Scaffold` for insets/snackbars, keep it and drop only the bar.
- **A per-screen nav container inside the nav host.** Hoist it above `NavDisplay`/`NavHost`. Doing so
  also fixes cross-destination shared transitions.
- **A toolbar-as-nav plus the suite scaffold.** Pick one. Toolbar-as-nav means hand-rolling the size
  switch; see `${CLAUDE_PLUGIN_ROOT}/skills/m3-expressive-navigation/references/nav-containers.md`.
- **A permanent drawer plus a modal drawer.** Reply's guard is the pattern: gestures enabled only
  when `drawerState.isOpen || navLayoutType == NavigationSuiteType.NavigationRail`, so the modal
  drawer can never open while a permanent one is on screen.
- **Hiding nav with `NavigationSuiteType.None`** on immersive routes re-lays-out the scaffold and can
  briefly show both states. Use `NavigationSuiteScaffoldState.hide()` — the KDoc says so, and it
  animates.
- **Verification:** resize slowly through 599/600, 839/840, 1199/1200 and 1599/1600 dp. Double
  containers usually appear only in a narrow band around a breakpoint.

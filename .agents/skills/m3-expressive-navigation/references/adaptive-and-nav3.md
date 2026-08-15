# Adaptive layouts and Navigation3 — NavDisplay, scene strategies, pane scaffolds, predictive back

Containers (nav bar, rail, toolbar-as-nav) → `nav-containers.md`. This file is the back stack, the
panes, and the transitions between destinations.

| Tag | Meaning |
| --- | --- |
| `[SRC]` | Verbatim from androidx source / rendered reference docs / release notes |
| `[CORPUS]` | Shipping open-source app — path given above the block |
| `[CANONICAL-FORM]` | Established public signature **not** captured in this corpus. Verify against the artifact before relying on it. |
| `[UNVERIFIED]` | Not confirmed against a primary source |

---

## 1. Artifacts and versions

`[SRC]` as of **2026-08-14**. (Was 2026-08-01 — `material3.adaptive` and `navigation3` both shipped
on 2026-08-12 and the rows below moved.)

| Artifact | Stable | Alpha | Notes |
| --- | --- | --- | --- |
| `androidx.compose.material3.adaptive:adaptive` | **1.3.0** (2026-08-12) | — | `currentWindowAdaptiveInfo`, `WindowAdaptiveInfo`, `currentWindowDpSize` |
| `androidx.compose.material3.adaptive:adaptive-layout` | **1.3.0** | — | `ListDetailPaneScaffold`, `SupportingPaneScaffold`, `AnimatedPane`, `PaneScaffoldDirective`, `PaneExpansionState` |
| `androidx.compose.material3.adaptive:adaptive-navigation` | **1.3.0** | — | `rememberListDetailPaneScaffoldNavigator`, `rememberSupportingPaneScaffoldNavigator` |
| `androidx.compose.material3.adaptive:adaptive-navigation3` | **ships within 1.3.0** | — | `ListDetailSceneStrategy`, `rememberListDetailSceneStrategy` — the nav3 bridge. **Stabilized into the 1.3.0 adaptive train** (it circulated earlier as a `1.0.0-SNAPSHOT`/pre-release artifact — see LastChat below) |
| `androidx.navigation3:navigation3-runtime` | **1.1.6** (2026-08-12) | 1.2.0-alpha07 (2026-07-29) | `NavKey`, `NavEntry`, `entryProvider`, `rememberNavBackStack` |
| `androidx.navigation3:navigation3-ui` | **1.1.6** | 1.2.0-alpha07 | `NavDisplay`, `SceneStrategy` |
| `androidx.compose.material3:material3-adaptive-navigation-suite` | 1.4.0 | **1.5.0-alpha26** (2026-08-12) | Rides the **material3** version train, not `material3.adaptive` |

**`material3.adaptive` 1.3.0 is stable as of 2026-08-12** — the `1.3.0-rc01` line this file previously
recommended is superseded, and `adaptive-navigation3` is no longer an RC-only artifact: it ships
*within* 1.3.0. There is currently **no** adaptive alpha in flight.

**navigation3 reached 1.1.6 stable**, with `1.2.0-alpha07` on the alpha train. Both corpus pins below
(`1.0.1`, `1.1.3`) are behind; neither is broken, but neither is current.

```kotlin
dependencies {
    implementation("androidx.compose.material3.adaptive:adaptive:1.3.0")
    implementation("androidx.compose.material3.adaptive:adaptive-layout:1.3.0")
    implementation("androidx.compose.material3.adaptive:adaptive-navigation:1.3.0")
    implementation("androidx.compose.material3.adaptive:adaptive-navigation3:1.3.0")
}
```

Keep all four adaptive artifacts on the **same** version; they are one release train and mixing them
produces `NoSuchMethodError` at runtime, not compile time.

`androidx.navigation3` is a **separate** group on a **separate** train — it is not covered by the
Compose BOM and not aligned with `material3.adaptive`. Three sampled apps pin three different
versions, none of them current:

`[CORPUS]` `/root/work/repos/Tomato/gradle/libs.versions.toml` — a shipping, working combination:

```toml
adaptive = "1.2.0"
navigation3 = "1.0.1"

androidx-adaptive = { group = "androidx.compose.material3.adaptive", name = "adaptive", version.ref = "adaptive" }
androidx-compose-adaptive-navigation3 = { group = "androidx.compose.material3.adaptive", name = "adaptive-navigation3" }
androidx-navigation3-runtime = { module = "androidx.navigation3:navigation3-runtime", version.ref = "navigation3" }
androidx-navigation3-ui = { module = "androidx.navigation3:navigation3-ui", version.ref = "navigation3" }
```

`[OFFICIAL jetpacker]` `/root/work/repos/ai-samples/jetpacker/android/gradle/libs.versions.toml` —
Google's own pin, and note there are **no** `material3.adaptive` entries at all:

```toml
nav3Core = "1.1.3"

androidx-navigation3-runtime = { module = "androidx.navigation3:navigation3-runtime", version.ref = "nav3Core" }
androidx-navigation3-ui = { module = "androidx.navigation3:navigation3-ui", version.ref = "nav3Core" }
```

`[CORPUS]` `/root/work/repos/LastChat/gradle/libs.versions.toml:20-23` — a **non**-working
combination, and the reason it never shipped (§10):

```toml
nav3Core = "1.0.0-rc01"
material3AdaptiveNav3 = "1.2.0"
lifecycleViewmodelNav3 = "1.0.0-alpha04"
nav3Material = "1.0.0-SNAPSHOT"     # <-- a SNAPSHOT, in a version catalog
```

`androidx.lifecycle:lifecycle-viewmodel-navigation3` is the third piece: it supplies the
`NavEntryDecorator` that scopes a `ViewModel` to a nav3 entry. Tomato does not use it (Koin +
`koinViewModel()` instead). If you want per-destination `ViewModel` lifecycle, you need it.

Material3 side `[SRC]`, verbatim from the material3 release notes:

```kotlin
dependencies {
    implementation("androidx.compose.material3:material3:1.4.0")
    implementation("androidx.compose.material3:material3-window-size-class:1.4.0")
    implementation("androidx.compose.material3:material3-adaptive-navigation-suite:1.5.0-alpha26")
}
```

The BOM never ships alphas, so pin material3 explicitly *after* the `platform(...)` line if you want
`1.5.0-alphaNN`. That override is the documented pattern, and `[OFFICIAL jetpacker]` is the cleanest
demonstration of it: every Compose module declares `implementation(platform(libs.androidx.compose.bom))`
*and* `implementation(libs.androidx.compose.material3)`, where the catalog entry carries an inline
literal `version = "1.5.0-alpha16"`. An explicit version on the dependency beats a BOM's
`dependencyConstraints`, so BOM governs `ui`/`foundation`/`icons` while material3 stays pinned ahead.

**Opt-in.** `adaptive-layout` / `adaptive-navigation` / `adaptive-navigation3` are gated by
`@ExperimentalMaterial3AdaptiveApi`. Set it globally rather than per call site `[CORPUS]`
`/root/work/repos/LastChat/app/build.gradle.kts:205`:

```kotlin
compilerOptions.optIn.add("androidx.compose.material3.adaptive.ExperimentalMaterial3AdaptiveApi")
```

`VerticalDragHandle` additionally needs `ExperimentalMaterial3ExpressiveApi` on 1.4.0 `[SRC]`
(`[UNVERIFIED for alpha26]`).

---

## 2. Navigation3 core model

Four concepts, and the whole thing is smaller than it sounds.

1. **`NavKey`** — marker interface. Your routes implement it. A route is a value, not a string.
2. **The back stack is a plain `MutableList<NavKey>`** that you own. `add` to push, `removeLastOrNull`
   to pop. There is no `NavController` and no navigation graph.
3. **`NavDisplay`** — renders the top of the stack, animates between entries, applies a
   `SceneStrategy` that may render more than one entry at a time (that is how multi-pane works).
4. **`entryProvider` / `NavEntry`** — maps a key to content, plus per-entry `metadata` that scene
   strategies read.

### Route definitions

`[CORPUS]` `/root/work/repos/Tomato/shared/src/commonMain/kotlin/org/nsh07/pomodoro/ui/Screen.kt`
(complete after the licence header, lines 18–80):

```kotlin
package org.nsh07.pomodoro.ui

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.StringResource

sealed class Screen : NavKey {
    @Serializable
    object Timer : Screen()

    @Serializable
    object AOD : Screen()

    @Serializable
    sealed class Settings : Screen() {
        @Serializable
        object Main : Settings()

        @Serializable
        object About : Settings()

        @Serializable
        object Alarm : Settings()

        @Serializable
        object Appearance : Settings()

        @Serializable
        object Backup : Settings()

        @Serializable
        object Timer : Settings()
    }

    @Serializable
    sealed class Stats : Screen() {
        @Serializable
        object Main : Stats()

        @Serializable
        object LastWeek : Stats()

        @Serializable
        object LastMonth : Stats()

        @Serializable
        object LastYear : Stats()
    }
}

data class NavItem(
    val route: Screen,
    val unselectedIcon: DrawableResource,
    val selectedIcon: DrawableResource,
    val label: StringResource,
    val onNavigateHome: () -> Unit
)

data class SettingsNavItem(
    val route: Screen.Settings,
    val icon: DrawableResource,
    val label: StringResource,
    val innerSettings: List<StringResource>
)
```

**Nested sealed classes give you typed subgraphs.** `Screen.Settings` is both a `NavKey` and a type
you can constrain a nested back stack to (`mutableStateListOf<Screen.Settings>(...)`) — the nav3
equivalent of a nested navigation graph, in the type system instead of a builder DSL. `@Serializable`
on every route is what makes the stack saveable. Routes with arguments are data classes, not objects
(`@Serializable data class Article(val id: String) : Screen()` — `[CANONICAL-FORM]`, extrapolated;
no arg-carrying route exists in this corpus).

### The "navigation controller" is two extension functions

`[CORPUS]` `/root/work/repos/Tomato/shared/src/commonMain/kotlin/org/nsh07/pomodoro/utils/Utils.kt:82-89`:

```kotlin
fun <T> MutableList<T>.onBack() {
    if (size > 1) removeLastOrNull()
}

fun <T> MutableList<T>.onTopLevelNavigate(screen: T) {
    if (size < 2) add(screen)
    else set(1, screen)
}
```

That is the entire navigation API of a shipping app. `onTopLevelNavigate` implements "switching tabs
replaces the second entry rather than growing the stack" — the nav2 equivalent needs
`popUpTo(startDestination) { saveState = true }` + `launchSingleTop` + `restoreState`.

### Creating the stack

`[CORPUS]` root — `rememberNavBackStack` (saveable across process death; requires `@Serializable`
routes). `[CORPUS]` nested — plain `mutableStateListOf` **hoisted into the ViewModel**
(`.../settingsScreen/viewModel/SettingsViewModel.kt:61`, `.../statsScreen/viewModel/StatsViewModel.kt:57`):

```kotlin
val backStack = rememberNavBackStack(Screen.Timer)                        // root
val backStack = mutableStateListOf<Screen.Settings>(Screen.Settings.Main) // nested, in a ViewModel
val backStack = mutableStateListOf<Screen.Stats>(Screen.Stats.Main)
```

The single most useful nav3 idea: **the back stack is ordinary state**, so it goes wherever state
goes. Hoisting it into a ViewModel is how Tomato pops a tab to root from the nav bar
(`statsViewModel.backStack.removeRange(1, statsViewModel.backStack.size)`) — a cross-cutting
operation that in nav2 needs the `NavController`.

### `NavDisplay` signature

`[CANONICAL-FORM]` — **not captured verbatim in this corpus.** Parameter *names* below are confirmed
by Tomato's named-argument call sites; types and defaults are not.

```kotlin
@Composable
fun <T : Any> NavDisplay(
    backStack: List<T>,
    modifier: Modifier = Modifier,
    onBack: () -> Unit,                                  // Tomato passes `backStack::onBack`, a
                                                         // zero-arg function, on navigation3 1.0.1.
                                                         // Later versions changed this to (Int) -> Unit
                                                         // for multi-pop. [UNVERIFIED which version.]
    entryDecorators: List<NavEntryDecorator<*>> = /* saveable state + saved state + viewmodel */,
    sceneStrategy: SceneStrategy<T> = SinglePaneSceneStrategy(),
    transitionSpec: AnimatedContentTransitionScope<*>.() -> ContentTransform = { ... },
    popTransitionSpec: AnimatedContentTransitionScope<*>.() -> ContentTransform = transitionSpec,
    predictivePopTransitionSpec: AnimatedContentTransitionScope<*>.() -> ContentTransform = popTransitionSpec,
    entryProvider: (key: T) -> NavEntry<T>,
)
```

**Confirmed present and named exactly this** `[CORPUS]`: `backStack`, `onBack`, `transitionSpec`,
`popTransitionSpec`, `predictivePopTransitionSpec`, `sceneStrategy`, `entryProvider`, `modifier`.
If you write anything else, verify it first.

`entryProvider { }` builds the lookup; `entry<T>(metadata = ...) { }` registers one route
`[CORPUS]`. `metadata` is an opaque `Map` that scene strategies consume — `listPane(...)` and
`detailPane()` are metadata factories, not layout calls.

---

## 2a. jetpacker's nav3 — the simple end, from Google

`[OFFICIAL jetpacker]` `android/ai-samples/jetpacker` is a **pure nav3 app** (zero
`androidx.navigation.compose` — no `NavHost`, no `composable`, no `NavController`) written by Android
DevRel. Its entire navigation layer is **three files, 293 lines**, all in `:app`. It is the useful
counterweight to Tomato: same core shape, four extra idioms, and **deliberately none of the advanced
machinery**.

**Be honest about what it does not do.** No scene strategies of any kind (`SceneStrategy`,
`SinglePaneSceneStrategy`, `ListDetailSceneStrategy`, `rememberListDetailSceneStrategy`, custom
`Scene`). No `entryDecorators`, no `rememberSavedStateNavEntryDecorator`, no
`rememberViewModelStoreNavEntryDecorator`. No `transitionSpec`, no `popTransitionSpec`, no
`predictivePopTransitionSpec`, no `NavEntry` metadata. No `SharedTransitionLayout`. Exactly one
`BackHandler` in the whole app (`CreateTripScreen.kt:73`, which just calls `onBack()`). **`NavDisplay`
is called with three arguments.** If you are looking for evidence that scene strategies are the norm,
jetpacker neither confirms nor contradicts — it simply never reaches for them.

### The three files

`[OFFICIAL jetpacker]` `.../ui/navigation/NavigationState.kt` — complete:

```kotlin
package com.example.jetpacker.ui.navigation

import androidx.compose.runtime.Composable
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.rememberNavBackStack

typealias NavigationState = NavBackStack<NavKey>

@Composable
fun rememberNavigationState(startRoute: Screen): NavigationState {
  return rememberNavBackStack(startRoute)
}
```

`[OFFICIAL jetpacker]` `.../ui/navigation/Navigator.kt` — complete:

```kotlin
package com.example.jetpacker.ui.navigation

import androidx.navigation3.runtime.NavKey

class Navigator(val state: NavigationState) {
  fun navigate(route: NavKey) {
    state.add(route)
  }

  fun goBack() {
    if (state.size > 1) {
      state.removeLastOrNull()
    }
  }
}
```

`[OFFICIAL jetpacker]` `.../ui/navigation/NavGraph.kt:17-182` — keys, provider, display:

```kotlin
sealed interface Screen : NavKey {
  @Serializable data object CreateTrip : Screen
  @Serializable data object Debug : Screen
  @Serializable data class EditTrip(val tripId: String) : Screen
  @Serializable data class FlightDetail(val eventId: String) : Screen
  @Serializable data class HotelDetail(val eventId: String) : Screen
  @Serializable data object ManageExpenses : Screen
  @Serializable data class MuseumDetail(val eventId: String) : Screen
  @Serializable data object MyTrips : Screen
  @Serializable data class RestaurantDetail(val eventId: String) : Screen
  @Serializable data class Timeline(val tripId: String) : Screen
  @Serializable data class TourDetail(val eventId: String) : Screen
  @Serializable data class VoiceNotes(val tripId: String) : Screen
  @Serializable data class Assistant(val eventId: String) : Screen
  @Serializable data class ReviewScreen(val placeId: String, val placeName: String) : Screen
  @Serializable data class HotelChat(val hotelName: String, val language: String) : Screen
}

@Composable
fun JetPackerNavGraph(
  navigationState: NavigationState,
  navigator: Navigator,
) {
  val entryProvider = remember(navigator) {
    entryProvider<NavKey> {
      // … 15 entries …
      entry<Screen.VoiceNotes> { key ->
        VoiceNotesScreen(
          tripId = key.tripId,
          contentPadding = PaddingValues(0.dp),
          onBack = { navigator.goBack() },
        )
      }
    }
  }

  NavDisplay(
    backStack = navigationState,
    onBack = { navigator.goBack() },
    entryProvider = entryProvider,
  )
}
```

Host wiring `[OFFICIAL jetpacker]` `MainActivity.kt:17-93` — **state first, navigator second, both
`remember`ed**:

```kotlin
    setContent {
      JetPackerTheme {
        val navigationState = rememberNavigationState(startRoute = Screen.MyTrips)
        val navigator = remember(navigationState) { Navigator(navigationState) }

        SetupShakeDetection(navigator)

        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
          JetPackerNavGraph(navigationState = navigationState, navigator = navigator)
        }
      }
    }
```

### Four idioms worth adopting, vs Tomato

| | Tomato `[CORPUS]` | jetpacker `[OFFICIAL]` |
| --- | --- | --- |
| Route declaration | `sealed class Screen : NavKey` with **nested sealed subgraphs** (`Screen.Settings`, `Screen.Stats`) | `sealed interface Screen : NavKey`, **flat**, 15 members one per line |
| Args | none — every route is an `object` | 9 of 15 are `data class`es carrying primitive IDs |
| Mutating the stack | `backStack.add(...)` / `removeLastOrNull()` at the call site, plus two `MutableList` extension functions | a **`Navigator` wrapper class** |
| Provider type | `entryProvider { }` | **`entryProvider<NavKey> { }`** — the *supertype* |
| Provider lifetime | rebuilt inline | **`remember(navigator) { entryProvider { … } }`** |
| Type alias | none | **`typealias NavigationState = NavBackStack<NavKey>`** |
| `NavDisplay` args | 7 (three transition specs, scene strategy, …) | **3** |
| Nested stacks | yes — one per tab, hoisted into ViewModels | none |

1. **`sealed interface Screen : NavKey` with `@Serializable` on every member**, `data object` for
   argument-less routes and `data class` for the rest. Keys carry **only primitive IDs** (`tripId`,
   `eventId`, `placeId`, `placeName`, `hotelName`, `language`) — never objects. This requires
   `alias(libs.plugins.kotlin.serialization)` on the module; serialization is what lets
   `rememberNavBackStack` survive process death.
2. **The `Navigator` wrapper class — adopt this.** It is a plain, non-`@Composable`, non-injected
   class. `navigate` = `List.add`; `goBack` = `removeLastOrNull()` guarded by `size > 1` so the root
   can never be popped. Two concrete wins over mutating the list at each call site: the root-pop guard
   lives in **one** place, and because it is a plain class it can be held **outside composition** —
   jetpacker passes it to `SetupShakeDetection`, where a `SensorEventListener` calls
   `navigator.navigate(Screen.Debug)` from a non-Compose callback. That is the practical argument.
3. **`remember(navigator) { entryProvider<NavKey> { … } }` — adopt this too.** `entryProvider` builds
   a map; rebuilding it every recomposition is waste. The key is stable because `Navigator` itself is
   created with `remember(navigationState) { Navigator(navigationState) }`. Typing it `<NavKey>`
   rather than `<Screen>` means third-party keys can be mixed into the same provider.
4. **`typealias NavigationState = NavBackStack<NavKey>`** so feature-facing signatures never mention
   nav3 types directly. Cheap, and it makes a later navigation swap a one-line change.

### Two more things it establishes

- **Navigation is callbacks all the way down.** Every screen takes `onBack: () -> Unit` plus
  domain-specific `onXClick` lambdas. Screens have **zero** navigation imports — which is precisely
  what makes each of the 19 feature modules independently previewable and screenshot-testable. No
  `LocalNavController`, no controller handed to screens.
- **Type-based dispatch lives in the entry, not the screen.** `Screen.Timeline`'s `onEventClick`
  receives `(eventId, EventType)` and the `when` mapping event type → destination lives in
  `NavGraph.kt`. The itinerary feature module does not know the detail routes exist.
- **"Replace" is done manually**: `Screen.CreateTrip`'s `onTripCreated` does
  `navigator.goBack(); navigator.navigate(Screen.Timeline(tripId))` — pop-then-push, because nav3 has
  no `popUpTo`. Compare Tomato's `onTopLevelNavigate` (§2), which solves the tab case instead.
- ViewModels come from `hiltViewModel()` inside each screen, with args read from `SavedStateHandle`
  (`savedStateHandle["tripId"]`) — **not** from `lifecycle-viewmodel-navigation3`. That is a third
  option alongside Tomato's Koin `koinViewModel()` and the nav3 decorator.

**Defects in this sample, do not propagate:** `NavGraph.kt` carries five unused imports
(`hiltViewModel`, `collectAsStateWithLifecycle`, `ItineraryViewModel`, `ItineraryScreen`, `getValue`),
and `Screen.ManageExpenses` is declared with a working `entry` that **nothing ever navigates to** —
expenses are reached as a tab inside `TripScreen`. A dead route that still costs you a serializable
key.

---

## 3. Root `NavDisplay` inside `SharedTransitionLayout` with a predictive-pop spec

**The highest-value pattern in this file.** Hoisting `SharedTransitionLayout` above the `NavDisplay`
is what lets an element on screen A morph into an element on screen B; giving the display a
`predictivePopTransitionSpec` is what makes the back gesture preview that morph in reverse.

`[CORPUS]` `/root/work/repos/Tomato/androidApp/src/main/java/org/nsh07/pomodoro/ui/AppScreen.kt`
(imports 81–83; body 295–345):

```kotlin
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.ui.NavDisplay
```

```kotlin
        SharedTransitionLayout {
            NavDisplay(
                backStack = backStack,
                onBack = backStack::onBack,
                transitionSpec = {
                    fadeIn(motionScheme.defaultEffectsSpec())
                        .togetherWith(fadeOut(motionScheme.defaultEffectsSpec()))
                },
                popTransitionSpec = {
                    fadeIn(motionScheme.defaultEffectsSpec())
                        .togetherWith(fadeOut(motionScheme.defaultEffectsSpec()))
                },
                predictivePopTransitionSpec = {
                    fadeIn(motionScheme.defaultEffectsSpec())
                        .togetherWith(fadeOut(motionScheme.defaultEffectsSpec()))
                },
                entryProvider = entryProvider {
                    entry<Screen.Timer> {
                        TimerScreen(
                            timerState = uiState,
                            settingsState = settingsState,
                            isPlus = isPlus,
                            contentPadding = contentPadding,
                            progress = { progress },
                            onAction = timerViewModel::onAction,
                            modifier = if (isAODEnabled) Modifier
                                .clickable {
                                    if (!uiState.timerRunning)
                                        timerViewModel.onAction(TimerAction.ToggleTimer)
                                    if (backStack.size < 2)
                                        backStack.add(Screen.AOD)
                                } else Modifier
                        )
                    }

                    entry<Screen.AOD> {
                        AlwaysOnDisplay(
                            timerState = uiState,
                            secureAod = settingsState.secureAod,
                            progress = { progress },
                            setTimerFrequency = setTimerFrequency,
                            modifier = if (isAODEnabled) Modifier.clickable {
                                if (backStack.size > 1) backStack.removeLastOrNull()
                            } else Modifier
                        )
                    }

                    entry<Screen.Settings.Main> {
                        SettingsScreenRoot(
                            setShowPaywall = { showPaywall = it },
                            contentPadding = contentPadding
                        )
                    }

                    entry<Screen.Stats.Main> {
                        StatsScreenRoot(
                            contentPadding = contentPadding,
                            focusGoal = settingsState.focusGoal
                        )
                    }
                }
            )
        }
```

Notes that matter:

- **All three specs are supplied.** `popTransitionSpec` and `predictivePopTransitionSpec` fall back on
  the previous one, so omitting them silently plays the *forward* animation backwards during a back
  gesture. Set all three deliberately.
- **Fade uses `defaultEffectsSpec()`, not spatial.** Alpha is non-spatial; effects springs are
  critically damped so opacity never overshoots `[SRC]`. Rule, not preference.
- **`contentPadding` from the outer `Scaffold` is threaded into every entry** — the nav container
  lives *above* the display (`nav-containers.md` §6), so entries must be told how much it occupies.
- The whole thing sits inside `Scaffold(bottomBar = { HorizontalFloatingToolbar ... })`: display
  inside scaffold, container as a sibling slot.

Non-fade variant, `[CORPUS]` `.../ui/statsScreen/StatsScreen.kt:85-100`:

```kotlin
    SharedTransitionLayout {
        NavDisplay(
            backStack = backStack,
            onBack = backStack::onBack,
            transitionSpec = {
                fadeIn().togetherWith(veilOut(targetColor = colorScheme.surfaceDim))
            },
            popTransitionSpec = {
                unveilIn(initialColor = colorScheme.surfaceDim).togetherWith(fadeOut())
            },
            predictivePopTransitionSpec = {
                unveilIn(initialColor = colorScheme.surfaceDim).togetherWith(fadeOut())
            },
            sceneStrategy = rememberListDetailSceneStrategy(
                directive = calculatePaneScaffoldDirective(currentWindowAdaptiveInfo())
            ),
            entryProvider = entryProvider { ... }
        )
    }
```

`veilOut` / `unveilIn` are Compose Animation 1.10+ expressive transitions — see the
`m3-expressive-motion` skill.

---

## 4. Nested `NavDisplay` + `ListDetailSceneStrategy`

The most reusable nav3 + adaptive pattern: a **second** `NavDisplay` inside a destination, with
`rememberListDetailSceneStrategy` and per-entry `metadata = listPane(...)` / `metadata = detailPane()`.
On expanded widths this renders as a two-pane list-detail; on compact it is a normal stack. **Same
code, no branching, one back stack.** That is the thing nav2 cannot do cleanly.

`[CORPUS]` `/root/work/repos/Tomato/shared/src/commonMain/kotlin/org/nsh07/pomodoro/ui/settingsScreen/SettingsScreen.kt`
(imports 28–36, 43–45; body 98–200):

```kotlin
import androidx.compose.material3.adaptive.ExperimentalMaterial3AdaptiveApi
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfo
import androidx.compose.material3.adaptive.navigation3.ListDetailSceneStrategy.Companion.detailPane
import androidx.compose.material3.adaptive.navigation3.ListDetailSceneStrategy.Companion.listPane
import androidx.compose.material3.adaptive.navigation3.rememberListDetailSceneStrategy
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.ui.NavDisplay
```

```kotlin
    val directionMultiplier = if (LocalLayoutDirection.current == LayoutDirection.Ltr) 1 else -1

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
                SettingsMainScreen(
                    settingsState = settingsState,
                    contentPadding = contentPadding,
                    currentScreen = backStack.last(),
                    isPlus = isPlus,
                    onAction = viewModel::onAction,
                    onNavigate = backStack::onTopLevelNavigate,
                    setShowPaywall = setShowPaywall,
                    modifier = modifier,
                )
            }

            entry<Screen.Settings.About>(
                metadata = detailPane()
            ) {
                AboutScreen(
                    contentPadding = contentPadding,
                    isPlus = isPlus,
                    onBack = backStack::onBack
                )
            }

            entry<Screen.Settings.Alarm>(
                metadata = detailPane()
            ) {
                AlarmSettings(
                    settingsState = settingsState,
                    isPlus = isPlus,
                    contentPadding = contentPadding,
                    onAction = viewModel::onAction,
                    setShowPaywall = setShowPaywall,
                    onBack = backStack::onBack,
                    modifier = modifier
                )
            }

            entry<Screen.Settings.Appearance>(
                metadata = detailPane()
            ) { ... }

            entry<Screen.Settings.Backup>(metadata = detailPane()) { ... }

            entry<Screen.Settings.Timer>(metadata = detailPane()) { ... }
        },
        modifier = Modifier.background(topBarColors.containerColor)
    )
```

Key points:

- **`listPane(detailPlaceholder = ...)`** fills the empty right pane on a tablet before anything is
  selected. Omit it and you get a blank half-screen. Tomato's `DetailPlaceholder` draws a
  `MaterialShapes.Cookie12Sided.toShape()` behind an icon — an expressive empty state, not a grey box.
- **The list pane reads the stack directly** (`currentScreen = backStack.last()`) to highlight the
  selected row in two-pane mode. **No `isExpanded` flag is threaded anywhere** — that is the payoff.
- **`onNavigate = backStack::onTopLevelNavigate`** replaces entry 1 instead of pushing, so tapping
  through rows on a tablet swaps the detail pane rather than growing the stack.
- **`onBack = backStack::onBack` is passed into detail screens** so their up-affordance uses the same
  stack; the framework hides that button in two-pane mode.
- **RTL is handled with `directionMultiplier`.** Slide offsets in a `transitionSpec` are raw pixels
  and do not mirror automatically. Copy this.
- The scene strategy takes an explicit `directive` — §5.

---

## 5. `calculatePaneScaffoldDirective` — what the directive controls, and overriding it

A `PaneScaffoldDirective` is the policy object every pane scaffold and every scene strategy consumes.
It answers: how many panes fit, how much space between them, how wide each prefers, and which screen
regions to avoid (hinges).

| Field | Controls |
| --- | --- |
| `maxHorizontalPartitions` | How many side-by-side panes are permitted. 1 = single pane (compact/medium), 2 = list+detail, 3 = list+detail+extra |
| `horizontalPartitionSpacerSize` | The **gutter** between horizontal panes. Set 0.dp to make panes read as one connected surface |
| `maxVerticalPartitions` | Stacked panes. 2 in tabletop posture so content sits above the fold and controls below |
| `verticalPartitionSpacerSize` | Gutter between vertical partitions |
| `defaultPanePreferredWidth` | Width a pane asks for before expansion state overrides it |
| `defaultPanePreferredHeight` | Same, vertically |
| `excludedBounds` | Rects the layout must not place content in — hinge bounds, chosen by `HingePolicy` |

Tomato forked the AndroidX calculator solely to zero the gutter. The docstring says so.

`[CORPUS]` `/root/work/repos/Tomato/shared/src/commonMain/kotlin/org/nsh07/pomodoro/ui/UiUtils.kt:69-150`:

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

**Read the AndroidX defaults out of it** — this is a faithful copy: stock gutters 24dp horizontal
(Tomato zeroed them) and 24dp vertical, 2 panes at 840dp, 3 at the next breakpoint, preferred pane
width 360dp (412dp at 3 panes).

**Do not fork the whole function unless you must** — forking forfeits AndroidX's future breakpoint
fixes. Cheaper (`[CANONICAL-FORM]`; `PaneScaffoldDirective` being a data class is `[UNVERIFIED]`):

```kotlin
val directive = calculatePaneScaffoldDirective(currentWindowAdaptiveInfo())
    .copy(horizontalPartitionSpacerSize = 0.dp)
```

Fork only to change the `when` shape itself (different breakpoints). Legitimate overrides: a 0dp
gutter so panes read as one connected surface (expressive); capping `maxHorizontalPartitions = 2`
when a third pane has nothing to show; a wider `defaultPanePreferredWidth` for text-heavy details.

**Pass the result explicitly everywhere** — `rememberListDetailSceneStrategy(directive = ...)`,
`ListDetailPaneScaffold(directive = ...)`. Miss one scaffold and it silently uses AndroidX defaults,
so its gutter will not match the others.

---

## 6. `ListDetailPaneScaffold` and `SupportingPaneScaffold`

Use these when you are **not** on nav3, or when the panes are not back-stack destinations (a bottom
sheet, a dialog, a supporting panel that has no route). On nav3, prefer `ListDetailSceneStrategy`
(§4) — it puts the panes on the back stack, which is the entire point.

### Signatures

`[CANONICAL-FORM]` — parameter names below are all confirmed by corpus call sites; defaults are not.

```kotlin
@Composable
fun ListDetailPaneScaffold(
    directive: PaneScaffoldDirective,
    scaffoldState: ThreePaneScaffoldState,
    listPane: @Composable ThreePaneScaffoldPaneScope.() -> Unit,
    detailPane: @Composable ThreePaneScaffoldPaneScope.() -> Unit,
    modifier: Modifier = Modifier,
    extraPane: (@Composable ThreePaneScaffoldPaneScope.() -> Unit)? = null,
    paneExpansionDragHandle: (@Composable ThreePaneScaffoldScope.(PaneExpansionState) -> Unit)? = null,
    paneExpansionState: PaneExpansionState? = null,
)

@Composable
fun SupportingPaneScaffold(
    directive: PaneScaffoldDirective,
    scaffoldState: ThreePaneScaffoldState,
    mainPane: @Composable ThreePaneScaffoldPaneScope.() -> Unit,
    supportingPane: @Composable ThreePaneScaffoldPaneScope.() -> Unit,
    modifier: Modifier = Modifier,
    extraPane: (@Composable ThreePaneScaffoldPaneScope.() -> Unit)? = null,
    paneExpansionDragHandle: (@Composable ThreePaneScaffoldScope.(PaneExpansionState) -> Unit)? = null,
    paneExpansionState: PaneExpansionState? = null,
)
```

Confirmed named arguments `[CORPUS]`: `directive`, `scaffoldState`, `listPane`, `detailPane`,
`mainPane`, `supportingPane`, `paneExpansionDragHandle`, `paneExpansionState`, `modifier`.
`[UNVERIFIED]`: `extraPane`, the exact receiver scope types, and whether `paneExpansionDragHandle`
takes a `PaneExpansionState` argument — Tomato's handle lambda ignores its parameters and closes over
`expansionState` instead.

### Navigator

`[CORPUS]` — both navigators expose `scaffoldDirective`, `scaffoldState`, `currentDestination`,
and `navigateTo(role, contentKey)`:

```kotlin
val navigator = rememberListDetailPaneScaffoldNavigator<CommentThreadRenderer>()
val navigator = rememberSupportingPaneScaffoldNavigator(
    adaptStrategies = SupportingPaneScaffoldDefaults.adaptStrategies(
        supportingPaneAdaptStrategy = AdaptStrategy.Hide
    )
)
```

The type parameter is the **content key** — the value the detail pane needs to render, carried by the
navigator and restored across configuration change. `AdaptStrategy.Hide` means "when this pane
doesn't fit, remove it entirely" (as opposed to reflowing or levitating it).

`ThreePaneScaffoldNavigator<T>` members confirmed in use `[CORPUS]`: `scaffoldDirective`,
`scaffoldState`, `currentDestination?.contentKey`, `navigateTo(ListDetailPaneScaffoldRole.Detail, value)`.
`[UNVERIFIED]`: `navigateBack()`, `canNavigateBack()` — both exist in the public API but no corpus
call site confirms the exact spelling.

`ListDetailPaneScaffoldRole`: `List`, `Detail`, `Extra`. `SupportingPaneScaffoldRole`: `Main`,
`Supporting`, `Extra`. `[CANONICAL-FORM]` — only `ListDetailPaneScaffoldRole.Detail` is confirmed.

### `AnimatedPane`

**Every pane body must be wrapped in `AnimatedPane`.** It is what animates the pane in and out when
the directive changes (resize, fold, rotate). Skip it and panes appear and disappear as jump cuts.

`[SRC]` material3 **1.5.0-alpha22** added *"Shapes in `AnimatedPane`"* — panes can now carry their own
shape, which is how you get rounded, visually separated panes without wrapping each in a `Surface`.
`[UNVERIFIED]` exact parameter name — check `AnimatedPane`'s signature on your artifact before using
it; do not guess `shape =`.

### `ListDetailPaneScaffold` in the wild

`[CORPUS]` `/root/work/repos/vivi-music/app/src/main/kotlin/com/music/vivi/ui/screens/CommentSheet.kt`
(navigator line 78; scaffold lines 166–215) — list-detail **inside a `ModalBottomSheet`**, which is a
good demonstration that pane scaffolds are layout, not navigation:

```kotlin
    val navigator = rememberListDetailPaneScaffoldNavigator<CommentThreadRenderer>()
```

```kotlin
                    ListDetailPaneScaffold(
                        directive = navigator.scaffoldDirective,
                        scaffoldState = navigator.scaffoldState,
                        modifier = Modifier.fillMaxHeight(),
                        listPane = {
                            AnimatedPane {
                                LazyColumn(...) {
                                    items(
                                        comments,
                                        key = { item -> item.comment?.commentRenderer?.commentId ?: "comment-${item.hashCode()}" }
                                    ) { thread ->
                                        val renderer = thread.comment?.commentRenderer ?: return@items
                                        CommentItem(
                                            renderer = renderer,
                                            onShowReplies = {
                                                scope.launch {
                                                    navigator.navigateTo(ListDetailPaneScaffoldRole.Detail, thread)
                                                }
                                            }
                                        )
                                    }
                                    // … pagination loader item …
                                }
                            }
                        },
                        detailPane = {
                            AnimatedPane {
                                val selectedThread = navigator.currentDestination?.contentKey
                                if (selectedThread != null) {
                                    CommentDetailPane(
```

`navigateTo` is a **suspend** call — note the `scope.launch`.

---

## 7. Pane expansion — `VerticalDragHandle` + `paneExpansionDraggable`

Letting the user drag the divider to resize panes. `[SRC]` `VerticalDragHandle` KDoc: *"a capsule-like
shape that can be used by users to change component size and/or position by dragging."* It grows and
changes shape on press and drag — that is the Expressive part. Under-used and cheap.

`[SRC]` — opt-in `ExperimentalMaterial3ExpressiveApi` on 1.4.0 (`[UNVERIFIED for alpha26]`):

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

`[SRC]` sample guidance: combine with `Modifier.draggable()` and **`Modifier.systemGestureExclusion()`**
so the drag does not fight the system back gesture. In a pane scaffold,
`Modifier.paneExpansionDraggable(...)` replaces the raw `draggable`.

`[CORPUS]` `/root/work/repos/Tomato/shared/src/androidMain/kotlin/org/nsh07/pomodoro/ui/timerScreen/TimerScreen.kt`
(imports 87–94; setup 216–224; handle 823–837):

```kotlin
import androidx.compose.material3.adaptive.ExperimentalMaterial3AdaptiveApi
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfo
import androidx.compose.material3.adaptive.layout.AdaptStrategy
import androidx.compose.material3.adaptive.layout.AnimatedPane
import androidx.compose.material3.adaptive.layout.SupportingPaneScaffold
import androidx.compose.material3.adaptive.layout.SupportingPaneScaffoldDefaults
import androidx.compose.material3.adaptive.layout.rememberPaneExpansionState
import androidx.compose.material3.adaptive.navigation.rememberSupportingPaneScaffoldNavigator
```

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
                Scaffold(
                    topBar = { TopAppBar( ... ) },
                    ...
                )
            }
        },
        supportingPane = {
            val isFocus = timerState.timerMode == TimerMode.FOCUS
            AnimatedPane {
                LazyColumn(
                    contentPadding = contentPadding,
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier
                        .background(detailPaneTopBarColors.containerColor)
                        .fillMaxSize()
                        .padding(horizontal = 16.dp)
                ) {
                    item { TopAppBar(title = { Text(stringResource(Res.string.up_next)) }, subtitle = {}, ...) }
                    ...   // upcoming-interval ListItems
                }
            }
        },
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

`Modifier.paneExpansionDraggable(state, minTouchTargetSize, interactionSource)` — positional
arguments as Tomato calls them `[CORPUS]`. `LocalMinimumInteractiveComponentSize.current` as the
minimum touch target is the correct value; do not hardcode 48.dp.

`rememberPaneExpansionState()` is called with **no arguments** here `[CORPUS]`. `[UNVERIFIED]`
overloads taking anchors / initial fraction / a key exist in the public API but no corpus call site
confirms their spelling.

Three things to copy: the same `expansionState` instance passed to **both**
`Modifier.paneExpansionDraggable` and the scaffold's `paneExpansionState` (they must be the same
object or the drag does nothing); `systemGestureExclusion()`; a fresh `MutableInteractionSource` so
the handle renders its own press/drag states.

---

## 8. Predictive back

`[SRC]` Android's predictive back gives the user a live preview of where a back gesture lands. In
Compose there are two levels.

**Level 1 — `NavDisplay` does it for you.** Supply `predictivePopTransitionSpec` and the display
drives that `ContentTransform` from the gesture's progress. This is the only predictive-back wiring
that exists in the corpus `[CORPUS]` (Tomato, §3 and §4) and it is almost always enough.

```kotlin
predictivePopTransitionSpec = {
    fadeIn(motionScheme.defaultEffectsSpec())
        .togetherWith(fadeOut(motionScheme.defaultEffectsSpec()))
}
```

Rules:
- Make it the **inverse of the forward transition**, not a copy. Tomato's settings display slides the
  incoming pane in from the leading edge at `-it/4` with a fade — a deliberate reverse-parallax that
  reads as "going back", not "going forward slowly".
- Never leave it at the default. It falls back to `popTransitionSpec` → `transitionSpec`, which means
  a gesture-driven forward animation.
- Effects springs for fade, spatial for movement.
- The transition must be **fully driven by progress** — no `LaunchedEffect`, no timed animation. That
  is automatic when you express it as a `ContentTransform`.

**Level 1½ — `NavigationBackHandler` + `rememberNavigationEventState`.** `[OFFICIAL JetNews]` A newer
integration (`androidx.navigationevent.compose`) that pairs with a `NavDisplay` taking
`sceneState` + `navigationEventState`, and whose `predictivePopTransitionSpec` receives the **swipe
edge** so the transform origin can pivot away from it. Full code in the `m3-expressive-motion` skill,
`motion-recipes.md` §10d. On nav3 this supersedes level 2; verify the `NavDisplay` overload exists at
your navigation3 pin before rewriting a working call.

**Level 2 — `PredictiveBackHandler` for custom surfaces.** From `androidx.activity.compose`. **No
community-corpus usage** — all four community apps use plain
`androidx.activity.compose.BackHandler` `[CORPUS]` — but `[OFFICIAL JetLagged]`
`JetLaggedDrawer.kt:98-118` is a complete real implementation (progress → `snapTo`, a `VelocityTracker`
fed from `backEvent.touchX/touchY`, velocity handed to the settle animation on **both** the commit and
cancel paths). Read that before shipping the sketch below; it is in `motion-recipes.md` §10c.

```kotlin
PredictiveBackHandler(enabled = sheetOpen) { progress: Flow<BackEventCompat> ->
    try {
        progress.collect { event -> dragFraction = event.progress }   // drive your own animation
        onDismiss()                                                    // gesture completed
    } catch (e: CancellationException) {
        dragFraction = 0f                                              // gesture cancelled — reverse
    }
}
```

The `try/collect/catch(CancellationException)` shape is the whole contract: collect drives the
preview, normal completion means commit, cancellation means revert. Use it for anything that owns its
own back semantics — a full-screen sheet, an expanded player, a selection mode. `[CORPUS]` Med's
FAB-menu `BackHandler` (collapse the expanded surface before letting back propagate) is the
non-predictive version of the same idea and is a good candidate for upgrading.

**With shared elements.** Predictive back and `SharedTransitionLayout` compose because both are
progress-driven: the gesture drives the `ContentTransform`, the transform drives the
`AnimatedVisibilityScope`, and `sharedBounds`/`sharedElement` interpolate off that scope. Nothing
extra is needed — but it only works if the `SharedTransitionLayout` is **outside** the `NavDisplay`
(§9). Inside, the scope is recreated per entry and the shared element has no partner to match.

---

## 9. Cross-destination shared element transitions

Two requirements: one `SharedTransitionLayout` above the destination switcher, and matching keys on
both sides.

`[CORPUS]` Tomato hoists it directly (§3): `SharedTransitionLayout { NavDisplay(...) }`.

`[CORPUS]` LastChat is on Navigation2 and hoists it at the app root, then publishes both scopes
through CompositionLocals — the pattern to copy when destinations are far from the layout:
`/root/work/repos/LastChat/app/src/main/java/me/rerere/rikkahub/RouteActivity.kt`
(imports 12, 50, 53; body 660–678):

```kotlin
import androidx.compose.animation.SharedTransitionLayout
import me.rerere.rikkahub.ui.context.LocalAnimatedVisibilityScope
import me.rerere.rikkahub.ui.context.LocalSharedTransitionScope
```

```kotlin
    @Composable
    fun AppRoutes(navBackStack: NavHostController, startDestination: Screen.Chat) {
        val toastState = rememberAppToasterState()
        val settings by settingsStore.settingsFlow.collectAsStateWithLifecycle()
        val tts = rememberCustomTtsState()
        val stt = rememberCustomSttState()
        val motionPolicy = rememberSystemMotionPolicy()
        SharedTransitionLayout {
            CompositionLocalProvider(
                LocalNavController provides navBackStack,
                LocalSharedTransitionScope provides this,
                LocalSettings provides settings,
                LocalHighlighter provides highlighter,
                LocalMotionPolicy provides motionPolicy,
                LocalToaster provides toastState,
                LocalTTSState provides tts,
                LocalSTTState provides stt,
            ) {
```

Each destination then re-provides its own `AnimatedVisibilityScope` `[CORPUS]` (same file):

```kotlin
                        CompositionLocalProvider(LocalAnimatedVisibilityScope provides this@composable) {
```

`LocalSharedTransitionScope provides this` — `this` inside `SharedTransitionLayout`'s content is the
`SharedTransitionScope`. That is the trick. Two locals are required because `sharedBounds` needs
**both**: the shared scope (which pairs elements by key) and the per-destination
`AnimatedVisibilityScope` (which supplies the animation progress).

**Matching keys.** The key must be equal across both destinations and unique per element. Use a
stable domain identifier, never an index:

```kotlin
// list item
Modifier.sharedBounds(
    rememberSharedContentState(key = "article-${article.id}"),
    animatedVisibilityScope = LocalAnimatedVisibilityScope.current,
)
// detail header — same key string
Modifier.sharedBounds(
    rememberSharedContentState(key = "article-${article.id}"),
    animatedVisibilityScope = LocalAnimatedVisibilityScope.current,
)
```

`[CORPUS]` Tomato packages this into a reusable `Modifier.sharedBoundsReveal` with M3 Expressive
defaults (`SharedTransitionDefaults.BoundsTransform`, `scaleToBounds(ContentScale.Crop)`) at
`/root/work/repos/Tomato/shared/src/commonMain/kotlin/org/nsh07/pomodoro/ui/statsScreen/components/sharedBoundsReveal.kt`
— read that file when writing more than two shared-element call sites, and see the
`m3-expressive-motion` skill.

Failure modes: keys that differ by whitespace or interpolation (silently no transition, no error);
the element existing in only one destination (`sharedBounds` needs both sides; use `sharedElement`
semantics deliberately if only one exists); `SharedTransitionLayout` inside the `NavDisplay` or
inside a `composable {}` (scope recreated per destination, so nothing ever matches).

---

## 10. LastChat's hand-rolled adaptive scaffold — the "no adaptive library" alternative

**Honest framing first.** `[CORPUS]` LastChat declares the full Navigation3 stack in its version
catalog and then **comments the dependencies out** in `app/build.gradle.kts:267-270`:

```kotlin
    // Navigation 2
    implementation(libs.androidx.navigation2)

    // Navigation 3
//    implementation(libs.androidx.navigation3.runtime)
//    implementation(libs.androidx.navigation3.ui)
//    implementation(libs.androidx.lifecycle.viewmodel.navigation3)
//    implementation(libs.androidx.material3.adaptive.navigation3)
```

It keeps `adaptive` and `adaptive-layout` (line 260–261) but uses neither `ListDetailPaneScaffold`
nor `NavigationSuiteScaffold` anywhere. This is a real team that evaluated nav3, wired the catalog,
and backed out. Treat it as evidence for §11, not as a bug.

What it does instead: reads `currentWindowDpSize()` from `material3-adaptive` and, above 840×600,
renders a fixed 336dp `Surface` nav pane plus a detail area, using `CompositionLocalProvider` to
suppress the detail pane's back button. Scroll position and last selection live in **file-level
`var`s** so they survive route changes.

`[CORPUS]` `/root/work/repos/LastChat/app/src/main/java/me/rerere/rikkahub/ui/pages/setting/SettingsAdaptiveScaffold.kt`
(lines 60–62, 86–170):

```kotlin
import androidx.compose.material3.adaptive.currentWindowDpSize
...
val LocalSettingsWideLayout = staticCompositionLocalOf { false }

private var settingsPaneScrollIndex = 0
private var settingsPaneScrollOffset = 0
private var lastSettingsPaneSelected: SettingsDestination? = null

private const val SettingsPanePressMillis = 80
private const val SettingsPaneFadeMillis = 90
private const val SettingsPaneShapeMillis = 120
private const val SettingsPaneExpandMillis = 140

private val SettingsPaneItemOuterRadius = 24.dp
private val SettingsPaneItemInnerRadius = 8.dp
private val SettingsPaneChildOuterRadius = 20.dp
private val SettingsPaneChildInnerRadius = 8.dp

enum class SettingsDestination {
    Display, Assistants, PromptInjections, Models, Providers, ProviderModels, Search, Tts, Mcp,
    Web, AndroidIntegration, Backup, BackupWebDav, BackupLocal, ChatStorage, Lorebooks, Skills,
    About, Fonts, UiCustomization, RpOptimizations, Workspaces,
}

@Composable
fun AdaptiveSettingsScaffold(
    selected: SettingsDestination,
    modifier: Modifier = Modifier,
    compactContent: (@Composable () -> Unit)? = null,
    detailContent: @Composable () -> Unit,
) {
    val windowSize = currentWindowDpSize()
    val useWideLayout = windowSize.width >= 840.dp && windowSize.height >= 600.dp
    val navController = LocalNavController.current

    if (!useWideLayout) {
        compactContent?.invoke() ?: detailContent()
        return
    }

    BackHandler {
        handleSettingsPaneBack(navController)
    }

    Row(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        CompositionLocalProvider(LocalSettingsWideLayout provides true) {
            SettingsNavigationPane(selected = selected)
        }
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
        ) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxHeight()
                    .widthIn(max = 900.dp)
                    .fillMaxWidth()
            ) {
                CompositionLocalProvider(
                    LocalBackButtonVisible provides false,
                    LocalSettingsWideLayout provides true,
                ) {
                    detailContent()
                }
            }
        }
    }
}
```

The nav pane (same file, 172–215) — a 336dp `Surface`, 32dp corners, scroll persisted through the
file-level `var`s because a `rememberSaveable` here would die with the route:

```kotlin
@Composable
private fun SettingsNavigationPane(
    selected: SettingsDestination,
    navController: NavHostController = LocalNavController.current,
) {
    var displayedSelected by remember { mutableStateOf(lastSettingsPaneSelected ?: selected) }
    val listState = rememberLazyListState(
        initialFirstVisibleItemIndex = settingsPaneScrollIndex,
        initialFirstVisibleItemScrollOffset = settingsPaneScrollOffset,
    )

    LaunchedEffect(selected) {
        displayedSelected = selected
        lastSettingsPaneSelected = selected
    }

    LaunchedEffect(listState) {
        snapshotFlow {
            listState.firstVisibleItemIndex to listState.firstVisibleItemScrollOffset
        }.collect { (index, offset) ->
            settingsPaneScrollIndex = index
            settingsPaneScrollOffset = offset
        }
    }

    Surface(
        modifier = Modifier
            .fillMaxHeight()
            .width(336.dp)
            .statusBarsPadding()
            .padding(start = 8.dp, top = 8.dp, bottom = 8.dp),
        shape = RoundedCornerShape(32.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        tonalElevation = 1.dp,
    ) {
```

Wired into Navigation2 destinations `[CORPUS]` `.../RouteActivity.kt:820-850`:

```kotlin
                    composable<Screen.Assistant> {
                        CompositionLocalProvider(LocalAnimatedVisibilityScope provides this@composable) {
                            AdaptiveSettingsScaffold(selected = SettingsDestination.Assistants) {
```

…and the `NavHost` **suppresses transitions entirely** between two settings routes in wide layout
`[CORPUS]` (same file, 741–790) — without this the whole two-pane layout slides on every row tap:

```kotlin
                val windowSize = currentWindowDpSize()
                val useWideSettingsLayout = windowSize.width >= 840.dp && windowSize.height >= 600.dp
                NavHost(
                    startDestination = actualStartDestination,
                    navController = navBackStack,
                    enterTransition = {
                        if (
                            useWideSettingsLayout &&
                            isSettingsPaneRoute(initialState.destination.route) &&
                            isSettingsPaneRoute(targetState.destination.route)
                        ) {
                            EnterTransition.None
                        } else {
                            rootEnterTransition(motionPolicy)
                        }
                    },
                    exitTransition = { ... rootExitTransition(motionPolicy) },
                    popEnterTransition = { ... rootPopEnterTransition(motionPolicy) },
                    popExitTransition = { ... rootPopExitTransition(motionPolicy) },
                    modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background),
                ) {
```

**Why it exists.** On nav2 every detail route is still a full-screen destination, so the pane is faked
by rendering the nav list beside it in *every* settings route. That forces all three hacks: the
`EnterTransition.None` suppression, `LocalBackButtonVisible provides false`, and file-level `var`s for
scroll (a `rememberSaveable` inside the pane dies with the route it lives in).

**Costs, worst first:** file-level mutable state is process-global (leaks across sign-out);
`currentWindowDpSize()` recomputes per route instead of sharing one `WindowAdaptiveInfo`; the 840×600
threshold is hardcoded, so posture is ignored entirely; the nav pane remounts on every route change
and only *appears* stable because scroll is externally cached; no pane expansion, no hinge avoidance,
no `AnimatedPane`.

**Recommend only** when the app is committed to nav2 *and* adaptive-layout is genuinely unavailable.
Otherwise `ListDetailPaneScaffold` (§6) does all of this with a directive and no globals.

---

## 11. Navigation2 vs Navigation3 — be honest

**Nav3 pays for itself when:**
- The app has real list-detail or supporting-pane behaviour that should be **driven by the back
  stack**, so one back press works identically at every width. `ListDetailSceneStrategy` (§4) is the
  only clean way to get this.
- You need the back stack as ordinary state — hoisted to a ViewModel, mutated from a nav bar,
  inspected by other UI. Tomato's `backStack.removeRange(1, size)` from a nav item is trivial in
  nav3 and awkward in nav2.
- You want the display, not a controller, to own transitions — including the predictive-pop spec.
- Multiple independent nested stacks (a stack per tab) without a nested-graph DSL.

**Nav3 does not pay when:**
- The app is a flat set of tabs with occasional pushes. `NavHost` + typed routes already does that
  and the migration buys nothing. This is the common case.
- You depend on the nav2 ecosystem: deep links, `navigation-compose` Hilt/`hiltViewModel()`
  integration, `SavedStateHandle` route argument injection, `navigation-testing`. Nav3 equivalents
  are thinner or absent, and `lifecycle-viewmodel-navigation3` is on its own alpha train.
- Your team ships on `1.0.0-SNAPSHOT` pins because a piece isn't released yet. That is exactly what
  LastChat's catalog shows (§1, §10) and exactly why it backed out.
- The app is mostly one screen. Tomato is three top-level destinations and still uses nav3 — because
  its **settings and stats subgraphs** are list-detail, not because the root is.

**Say this to the user, not something more diplomatic:** nav3 is worth adopting for back-stack-driven
multi-pane, and is not worth the churn for a flat tab app. If they have neither list-detail nor a
supporting pane, recommend staying on nav2 and spending the effort on
`ListDetailPaneScaffold`/`SupportingPaneScaffold` inside existing destinations instead.

---

## 12. Migrating nav2 → nav3

| Nav2 | Nav3 |
| --- | --- |
| `NavHost(navController, startDestination)` | `NavDisplay(backStack, onBack, entryProvider)` |
| `NavController` | The `MutableList` itself, plus your own extension functions |
| `composable<Route> { }` | `entry<Route> { }` inside `entryProvider { }` |
| `navController.navigate(Route)` | `backStack.add(Route)` |
| `navController.popBackStack()` | `backStack.removeLastOrNull()` (guard `size > 1`) |
| `popUpTo(start) { saveState = true } + launchSingleTop` | `onTopLevelNavigate` (§2) — 3 lines |
| `enterTransition` / `exitTransition` / `popEnter` / `popExit` on the host or per destination | `transitionSpec` / `popTransitionSpec` / `predictivePopTransitionSpec` on the display |
| `navBackStackEntry.toRoute<T>()` | The key **is** the typed route — read its properties directly |
| `SavedStateHandle` route args in a `ViewModel` | Pass values from the entry lambda, or use `lifecycle-viewmodel-navigation3` |
| `currentBackStackEntryAsState()` | `backStack.last()` / `backStack.lastOrNull()` — plain snapshot state |
| Nested `navigation<Graph>` builders | A nested `NavDisplay` with its own typed stack (§4) |
| `hiltViewModel()` scoped to the entry | `lifecycle-viewmodel-navigation3` decorator, or a DI framework that does not assume nav2 (Tomato uses Koin's `koinViewModel()`) |

**Typed routes and arguments.** Nav2 serializes typed routes into a URI and parses them back, so
arguments are limited to what fits in a route string. Nav3 keys *are* the objects — no serialization
on the navigate path, arguments can be any type the data class holds. But `rememberNavBackStack`
**does** serialize for save/restore, so: every route still needs `@Serializable`; never put a
`Bitmap`, lambda or repository in a route (put an ID in and resolve it in the entry, same as nav2);
very large arguments still make save/restore expensive.

**What actually breaks.** No `navDeepLink` equivalent — parse the `Intent` yourself and push keys.
`NavigationUI`/`AppBarConfiguration` are gone (irrelevant in Compose). Anything reaching for
`LocalNavController` must be rewritten to take callbacks or a hoisted stack. Nav-graph-scoped
ViewModels have no direct analogue — hoist the state, which is nav3's answer to most "where does this
live" questions.

**Migration order:** (1) define `NavKey` routes alongside the nav2 ones; (2) convert the **root** host
to `NavDisplay` with one `entryProvider`, screen composables unchanged; (3) replace `NavController`
calls with back-stack mutations plus `onBack`/`onTopLevelNavigate`; (4) only then convert a subgraph
to a nested `NavDisplay` + `ListDetailSceneStrategy`. Step 4 is the payoff, 1–3 are cost. **If the app
has no step 4, do not start.**

---

## 13. Testing and verification

**Resize** — compact (≤599dp), medium (600–839dp), expanded (≥840dp), via the resizable emulator or
`adb shell wm size`. Verify the nav container swaps (`nav-containers.md` §5), panes animate via
`AnimatedPane` rather than jump-cutting, and the selected destination survives every crossing. Test
**at** the boundary: 599↔600 and 839↔840 are where the directive's `when` changes branch.

**Fold/unfold** — confirm `windowPosture.isTabletop` produces the vertical split
(`maxVerticalPartitions = 2`, §5) and that hinge `excludedBounds` keeps content out of the crease. A
forked directive that drops `excludedBounds` will place a button under the hinge.

**Rotate** — selected destination, scroll position, pane expansion fraction and the detail pane's
content key must all survive. If any resets, it is remembered below the thing that recreates.

**State restoration across pane changes** — the hard case: select a detail item on a tablet, rotate to
portrait so it collapses to single-pane, rotate back; the same item must still be selected.
`ThreePaneScaffoldNavigator<T>`'s content key handles this if `T` is saveable; `rememberNavBackStack`
handles it if every route is `@Serializable`. **Most common adaptive bug — test it explicitly.**

**Process death** — "Don't keep activities", or `adb shell am kill <pkg>` while backgrounded.
`rememberNavBackStack` survives; a ViewModel's `mutableStateListOf` does not unless backed by
`SavedStateHandle`. Tomato's nested stacks reset here — fine for settings, not for a half-filled form.

**Predictive back at every depth** — from each depth of each stack, start the gesture, hold at ~50%,
cancel; the preview must match the pop animation and cancelling must fully revert. Then complete it
and verify the destination matches the preview.

**Shared elements across the swap** — trigger the transition in single-pane *and* two-pane. Animating
in only one mode means the `SharedTransitionLayout` is inside the scene, not above the display (§9).

**TalkBack** — both panes reachable, drag handle focusable and operable (it is a control, not
decoration), and the detail pane's suppressed back button must not strand the user.

---

## 14. Adaptive **without** the adaptive library — jetpacker's counter-example

Everything above assumes the `material3.adaptive` stack. Google's own `android/ai-samples/jetpacker`
does adaptivity with **none of it**, and the honest reading is that this is a legitimate choice for
one specific shape of app.

`[OFFICIAL jetpacker]` Verified absent, grep = 0 across all 103 `.kt` files: `WindowSizeClass`,
`calculateWindowSizeClass`, `currentWindowAdaptiveInfo`, `WindowAdaptiveInfo`,
`ListDetailPaneScaffold`, `NavigableListDetailPaneScaffold`, `SupportingPaneScaffold`,
`TwoPaneScaffold`, `AnimatedPane`, `PaneExpansion`, `rememberListDetailPaneScaffoldNavigator`,
`NavigationSuiteScaffold`. The `androidx.compose.material3.adaptive:*` artifacts are **not declared
in `libs.versions.toml` at all** — so there is no `PaneScaffoldDirective` (§5), no scene strategy
(§4), and no `@ExperimentalMaterial3AdaptiveApi` opt-in anywhere.

What it uses instead, two pieces:

1. **`androidx.compose.ui.derivedMediaQuery`** with two dp thresholds (600dp / 1200dp), gated on
   `ComposeUiFlags.isMediaQueryIntegrationEnabled = true` set in `Application.onCreate` **and**
   re-set in every preview and screenshot test. Full treatment — API surface, the flag idiom, all six
   call sites, and the `CompositionLocalProvider(LocalUiMediaScope provides MockUiMediaScope(…))`
   testing story — is in **`nav-containers.md` §9a**. It is experimental; `WindowSizeClass` remains
   the conservative choice.
2. **`androidx.compose.foundation.layout.FlexBox`** (opt-in `ExperimentalFlexBoxApi`), used once in
   `feature/home/.../components/TripCard.kt`, which is the mechanism by which one card flips between
   row and column form without duplicating its children — `Modifier.flex { grow(1f) }` on the
   children. The component-level equivalent of what a pane scaffold does at screen level.

At the screen level the whole adaptive story is a `when` over one boolean:

```kotlin
  Box(modifier = modifier.fillMaxSize()) {
    val tabletBreakpoint by derivedMediaQuery { windowWidth >= 1200.dp }

    when {
      tabletBreakpoint -> { TabletLayout(/* … */) }
      else -> { PhoneLayout(/* … */) }
    }
  }
```

`PhoneLayout` and `TabletLayout` are **two entirely separate composables in separate files** —
`LazyVerticalGrid` with sticky headers vs a `LazyColumn` of `LazyRow` shelves with 128dp gutters and
504dp cards. Not one parameterized layout.

**When this is the right call.** No list-detail and no supporting pane — so nothing needs to be
driven by the back stack across widths, which is the entire reason `ListDetailSceneStrategy` exists
(§11). Phone and tablet layouts that genuinely differ in *kind* rather than in measurement, so a
directive would not help. A leaf component that wants to resolve its own type scale and gutters
without size-class prop-drilling. That is jetpacker.

**When it is not.** The moment you add a pane scaffold or `NavigationSuiteScaffold`, those consume
`WindowAdaptiveInfo` and `derivedMediaQuery` becomes a second source of truth that disagrees at the
boundary — exactly the failure mode `nav-containers.md` §9 warns about for
`Configuration.screenWidthDp`. Do not mix them. And note jetpacker's 1200dp threshold is **not** an
M3 breakpoint (Expanded starts at 840dp); it is that app's own line, so its layouts are not
size-class-conformant by construction.

**Do not read this as Google deprecating the adaptive library.** jetpacker is a bleeding-edge-AndroidX
sample that happens to have no multi-pane requirement. Tomato (§3-§7) remains the reference for
back-stack-driven adaptivity, and `material3.adaptive` went **stable at 1.3.0** on 2026-08-12 (§1)
while `derivedMediaQuery` is still experimental and flag-gated.

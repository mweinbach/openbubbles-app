# M3 Expressive motion recipes

Working code. Every recipe: **when to use it**, the code, then pitfalls.

Provenance is marked per recipe:

- `[CORPUS <repo>]` — lifted from a shipping open-source app; path given. Safe to copy.
- `[OFFICIAL <repo>]` — verbatim from a Google-authored sample (`android/ai-samples`, `android/androidify`,
  `android/compose-samples`, `androidx/androidx` `@Sampled`). Highest authority.
- `[CANONICAL — NOT FROM CORPUS]` — the documented androidx form, reconstructed here. No app in the
  reference corpus uses it. Verify the signature against your artifact version before shipping.
- `[DERIVED]` — assembled here from verified pieces.

Community corpus: `/root/work/repos/{Tomato,vivi-music,LastChat,Med}`.
Official sources: `/root/work/repos/{ai-samples/jetpacker,androidify,compose-samples,androidx-m3}`.
Tomato is the richest motion source (53 `MaterialTheme.motionScheme` call sites); **androidify** is the
richest *official* one (29 `motionScheme` hits, and the only source that wires `MotionScheme` into
`BoundsTransform`).

Assumed imports throughout: `androidx.compose.material3.MaterialTheme.motionScheme` (imported as a
property so it can be referenced unqualified), plus the usual `androidx.compose.animation.*`.

---

## 1. Read a spec off the theme

**Use this when**: any animation in a Material 3 Expressive app. This is the base move; everything
else composes on top of it.

`[CORPUS Tomato]` `.../ui/timerScreen/TimerScreen.kt:173-202`

```kotlin
import androidx.compose.material3.MaterialTheme.motionScheme

@Composable
fun TimerScreen(/* ... */) {
    val motionScheme = motionScheme          // hoist once, use everywhere below
    val scope = rememberCoroutineScope()
    val haptic = LocalHapticFeedback.current

    val color by animateColorAsState(
        if (timerState.timerMode == TimerMode.FOCUS) colorScheme.primary
        else colorScheme.tertiary,
        animationSpec = motionScheme.slowEffectsSpec()
    )
    val colorContainer by animateColorAsState(
        if (timerState.timerMode == TimerMode.FOCUS) colorScheme.secondaryContainer
        else colorScheme.tertiaryContainer,
        animationSpec = motionScheme.slowEffectsSpec()
    )
    // (onColor follows the same shape — three color animations share one slow effects spec)

    val clockFontSize by animateFloatAsState(
        targetValue = if (!timerState.infiniteFocus) {
            if (timerState.timeStr.length < 6) 72f else 64f
        } else {
            if (timerState.timeStr.length < 6) 100f else 88f
        },
        animationSpec = motionScheme.defaultSpatialSpec()
    )
```

**Notes**

- `val motionScheme = motionScheme` is not a typo. `MaterialTheme.motionScheme` is `@Composable`; the
  local copy is readable from non-composable lambdas (gesture callbacks, coroutines).
- The color animations share one `slowEffectsSpec()` so the whole-screen recolor reads as one event,
  not three twitches.
- Font size is a `Float` driving `.sp` — the glyphs change size, so it is *spatial*.
- Hoist the spec out of `LazyColumn` item bodies and hot loops where you can; each call allocates a
  new spring object.

### `animateDpAsState` + `animateColorAsState` in one component

`[CORPUS Tomato]` `.../settingsScreen/components/MinuteInputField.kt:75-95` — the two-family rule in
six lines: width gets spatial, background color gets effects, in the same modifier chain.

```kotlin
decorator = { innerTextField ->
    val text = state.text
    val width by animateDpAsState(
        if (text.length < 3) 112.dp else 140.dp,
        motionScheme.defaultSpatialSpec()          // size → spatial
    )
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .size(width, 100.dp)
            .background(
                animateColorAsState(
                    if (text.isNotEmpty()) listItemColors.containerColor
                    else colorScheme.errorContainer,
                    motionScheme.defaultEffectsSpec()   // color → effects
                ).value,
                shape
            )
    ) { innerTextField() }
}
```

### `updateTransition` with motion scheme specs

`[CANONICAL — NOT FROM CORPUS]` No reference app uses `updateTransition`/`rememberTransition` with
motion-scheme specs; they all use individual `animate*AsState`. Canonical form:

```kotlin
val transition = updateTransition(targetState = expanded, label = "card")

val elevation by transition.animateDp(
    transitionSpec = { motionScheme.fastEffectsSpec() },      // elevation reads as lighting
    label = "elevation"
) { if (it) 8.dp else 1.dp }

val cornerRadius by transition.animateDp(
    transitionSpec = { motionScheme.fastSpatialSpec() },      // shape → spatial
    label = "corner"
) { if (it) 8.dp else 28.dp }

val contentAlpha by transition.animateFloat(
    transitionSpec = { motionScheme.defaultEffectsSpec() },
    label = "alpha"
) { if (it) 1f else 0f }
```

Use `updateTransition` when several properties must start on the same frame off one state change.
Prefer separate `animate*AsState` when they are independent — that is what the corpus does and it
recomposes less. **Pitfall**: `transitionSpec` is a per-segment lambda, so `motionScheme` must be
the hoisted local or you get "@Composable invocations can only happen from…".

---

## 2. Press / scale feedback via a custom `IndicationNodeFactory`

**Use this when**: you want press feedback applied uniformly across custom clickable surfaces
instead of pasting an `interactionSource` + `animateFloatAsState` into every component — and you
want it to not re-measure layout.

`[CANONICAL — NOT FROM CORPUS]` `IndicationNodeFactory` appears nowhere in the reference corpus. The
structure below is the documented androidx form (`Indication` + `Modifier.Node`/`DrawModifierNode`),
parameterized by a motion-scheme spec. Verify against your Compose Foundation version.

```kotlin
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.foundation.IndicationNodeFactory
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.node.DelegatableNode
import androidx.compose.ui.node.DrawModifierNode
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/** Press indication that scales content toward the press point. Springs come from the caller. */
data class ScaleIndication(
    private val pressedScale: Float = 0.92f,
    private val pressSpec: FiniteAnimationSpec<Float>,
    private val releaseSpec: FiniteAnimationSpec<Float>,
) : IndicationNodeFactory {

    override fun create(interactionSource: InteractionSource): DelegatableNode =
        ScaleIndicationNode(interactionSource, pressedScale, pressSpec, releaseSpec)
}

private class ScaleIndicationNode(
    private val interactionSource: InteractionSource,
    private val pressedScale: Float,
    private val pressSpec: FiniteAnimationSpec<Float>,
    private val releaseSpec: FiniteAnimationSpec<Float>,
) : Modifier.Node(), DrawModifierNode {

    private var currentPressPosition: Offset = Offset.Zero
    private val animatedScale = Animatable(1f)

    private suspend fun animateToPressed(pressPosition: Offset) {
        currentPressPosition = pressPosition
        animatedScale.animateTo(pressedScale, pressSpec)
    }

    private suspend fun animateToResting() {
        animatedScale.animateTo(1f, releaseSpec)
    }

    override fun onAttach() {
        coroutineScope.launch {
            interactionSource.interactions.collectLatest { interaction ->
                when (interaction) {
                    is PressInteraction.Press   -> animateToPressed(interaction.pressPosition)
                    is PressInteraction.Release -> animateToResting()
                    is PressInteraction.Cancel  -> animateToResting()
                }
            }
        }
    }

    override fun ContentDrawScope.draw() {
        scale(scale = animatedScale.value, pivot = currentPressPosition) {
            this@draw.drawContent()
        }
    }
}
```

The factory cannot be `@Composable`, so wrap it to pull specs off the theme, then apply it:

```kotlin
@Composable
fun rememberScaleIndication(pressedScale: Float = 0.92f): ScaleIndication {
    val press = motionScheme.fastSpatialSpec<Float>()
    val release = motionScheme.defaultSpatialSpec<Float>()
    return remember(pressedScale, press, release) {
        ScaleIndication(pressedScale, press, release)
    }
}

// per component
Modifier.clickable(
    interactionSource = remember { MutableInteractionSource() },
    indication = rememberScaleIndication(),
    onClick = onClick
)

// or app-wide
CompositionLocalProvider(LocalIndication provides rememberScaleIndication()) { App() }
```

**Pitfalls**

- `create()` is **not** `@Composable` — it cannot read `MaterialTheme.motionScheme`. Capture the
  spec at construction; hence `rememberScaleIndication`.
- `Indication` needs stable `equals`/`hashCode` or every recomposition rebuilds the node and restarts
  the animation. `data class` gives you this; a plain `class` does not.
- `collectLatest`, not `collect` — a fast tap must cancel the press animation mid-flight and start
  the release. That is exactly what a spring is for.
- Press → `fastSpatialSpec()`, release → `defaultSpatialSpec()` so the overshoot back is visible.
  Never an effects spec; nothing about scale is non-spatial.
- Scaling in `draw()` does not change layout bounds, so the 48 dp touch target survives. An
  equivalent that animated `Modifier.size` would shrink the hit area under the finger — a real
  accessibility regression.

### Corpus alternatives

Neither uses a custom `Indication`; both ship in real apps.

**Med — animated corner percent on press** `[CORPUS Med]` `.../med/MainActivity.kt:442-467`

```kotlin
val interactionSource = remember { MutableInteractionSource() }
val isPressed by interactionSource.collectIsPressedAsState()
val cornerPercent by animateIntAsState(
    targetValue = if (isPressed) 15 else 50,
    animationSpec = spring(
        dampingRatio = Spring.DampingRatioMediumBouncy,
        stiffness = Spring.StiffnessMedium
    ),
    label = "corner"
)

TextButton(
    onClick = onClick,
    shape = RoundedCornerShape(cornerPercent),
    interactionSource = interactionSource
) { Text(text) }
```

**Note**: Med hand-rolls the spring — replace with `motionScheme.fastSpatialSpec()` (0.6/800 is the
intended press feel). Better still, on material3 1.4+ use `ButtonDefaults.shapes()` /
`IconButtonDefaults.shapes()` / `ToggleButtonDefaults.shapesFor(buttonHeight)`, which do the press
morph for you. (`ToggleButtonDefaults.shapes()` became `DeprecationLevel.HIDDEN` in 1.5.0-alpha25 —
use `shapesFor(ButtonDefaults.MinHeight)`, or `ToggleButtonShapes(...)` to customise.)
The manual version is only for components lacking a `shapes` param.

**LastChat — `Animatable` "pop" on selection** `[CORPUS LastChat]` `.../ui/TabAnimation.kt:50-101`

```kotlin
val scale = remember { Animatable(1f) }
val animationSpec = spring<Float>(dampingRatio = 0.5f, stiffness = 400f)

LaunchedEffect(isSelected) {
    if (isSelected) {
        launch {
            scale.animateTo(1.15f, animationSpec = animationSpec)
            scale.animateTo(1f, animationSpec = animationSpec)
        }
    } else scale.snapTo(1f)
}

Tab(modifier = modifier.graphicsLayer { scaleX = scale.value; translationX = offsetX.value })
```

**Note**: `graphicsLayer` is the important part — draw phase only, no re-layout. The two-step
`animateTo(1.15f)` then `animateTo(1f)` is a hand-rolled overshoot; an expressive spatial spec gives
it to you with one `animateTo`.

## 3. Expand / collapse with the correct spec family

**Use this when**: a section, card, or text block grows or shrinks in place.

`[CORPUS Tomato]` — `AnimatedVisibility` form, `.../timerScreen/TimerScreen.kt:426-432`

```kotlin
AnimatedVisibility(
    expanded,
    enter = fadeIn(motionScheme.defaultEffectsSpec()) +
            expandVertically(motionScheme.defaultSpatialSpec()),
    exit  = fadeOut(motionScheme.defaultEffectsSpec()) +
            shrinkVertically(motionScheme.defaultSpatialSpec())
) {
    Text(/* ... */)
}
```

`[CORPUS Tomato]` — `animateContentSize` form, for content that changes size without appearing or
disappearing. `.../settingsScreen/screens/AlarmSettings.kt:291-300`

```kotlin
var expanded by remember { mutableStateOf(false) }
Text(
    stringResource(item.description),
    maxLines = if (expanded) Int.MAX_VALUE else 2,
    overflow = TextOverflow.Ellipsis,
    modifier = Modifier
        .clickable { expanded = !expanded }
        .animateContentSize(motionScheme.defaultSpatialSpec())
)
```

The paired affordance is a `TonalToggleButton` (renamed `FilledTonalToggleButton` in alpha25) whose chevron rotates on
`animateFloatAsState(if (expanded) 180f else 0f)` (`.../statsScreen/screens/LastWeekScreen.kt:290`)
— note Tomato passes **no spec** there; add `motionScheme.defaultSpatialSpec()`, rotation is spatial.

**Pitfalls**

- `expandVertically`/`shrinkVertically` are spatial; the paired `fadeIn`/`fadeOut` are effects. Give
  them different specs. One spec for the whole `enter` is the most common motion bug in Expressive
  code.
- `animateContentSize` invalidates layout every frame. Correct on a `Text` or small card; never on a
  screen-level `Column` — the whole subtree re-measures.
- `animateContentSize` and `AnimatedVisibility` on the same node fight each other. Pick one.

## 4. `SharedTransitionLayout` — app-wide setup with CompositionLocals

**Use this when**: you want shared elements available from any screen without threading
`SharedTransitionScope` through every composable signature. Set this up once at the app root.

`[CORPUS LastChat]` — verbatim, `.../rikkahub/RouteActivity.kt`
(imports lines 12, 50, 53; body lines 660-678)

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

Each destination then re-provides its own `AnimatedVisibilityScope`:

```kotlin
CompositionLocalProvider(LocalAnimatedVisibilityScope provides this@composable) {
```

The locals themselves `[DERIVED — reconstructed from usage; declarations live in `ui/context/`]`:

```kotlin
val LocalSharedTransitionScope = compositionLocalOf<SharedTransitionScope> {
    error("No SharedTransitionScope provided")
}
val LocalAnimatedVisibilityScope = compositionLocalOf<AnimatedVisibilityScope> {
    error("No AnimatedVisibilityScope provided")
}
```

**Pitfalls**

- `SharedTransitionLayout` must wrap the **navigation host**, not live inside a destination.
  Elements can only be shared between siblings under the same layout.
- `LocalSharedTransitionScope` is provided once at the layout; `LocalAnimatedVisibilityScope` must be
  re-provided **per destination** — each `composable<T>` block is its own `AnimatedContentScope`.
- Throw from the local's default (`error(...)`) rather than returning null: you get a clear crash at
  the wrong call site instead of a silently-missing animation.
- On Navigation 3 you do not need `LocalAnimatedVisibilityScope` — Nav3 supplies
  `LocalNavAnimatedContentScope` (recipe 7).

---

## 5. `Modifier.sharedBoundsReveal` — reusable shared-bounds helper

**Use this when**: you want one call site for a container transform instead of eight named
arguments. This packages M3-Expressive defaults: `SharedTransitionDefaults.BoundsTransform`,
`scaleToBounds(ContentScale.Crop)`, and an `OverlayClip` using `MaterialTheme.shapes.largeIncreased`.

`[CORPUS Tomato]` — **complete file**, verbatim after the license header (lines 16-62),
`/root/work/repos/Tomato/shared/src/commonMain/kotlin/org/nsh07/pomodoro/ui/statsScreen/components/sharedBoundsReveal.kt`

```kotlin
package org.nsh07.pomodoro.ui.statsScreen.components

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.BoundsTransform
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.SharedTransitionDefaults
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.SharedTransitionScope.ResizeMode.Companion.scaleToBounds
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.layout.ContentScale
import androidx.navigation3.ui.LocalNavAnimatedContentScope

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun Modifier.sharedBoundsReveal(
    sharedContentState: SharedTransitionScope.SharedContentState,
    sharedTransitionScope: SharedTransitionScope,
    animatedVisibilityScope: AnimatedVisibilityScope = LocalNavAnimatedContentScope.current,
    boundsTransform: BoundsTransform = SharedTransitionDefaults.BoundsTransform,
    enter: EnterTransition = fadeIn(),
    exit: ExitTransition = fadeOut(),
    resizeMode: SharedTransitionScope.ResizeMode = scaleToBounds(
        contentScale = ContentScale.Crop
    ),
    clipShape: Shape = MaterialTheme.shapes.largeIncreased,
    renderInOverlayDuringTransition: Boolean = true,
): Modifier =
    with(sharedTransitionScope) {
        this@sharedBoundsReveal
            .sharedBounds(
                sharedContentState = sharedContentState,
                animatedVisibilityScope = animatedVisibilityScope,
                boundsTransform = boundsTransform,
                enter = enter,
                exit = exit,
                resizeMode = resizeMode,
                clipInOverlayDuringTransition = OverlayClip(clipShape),
                renderInOverlayDuringTransition = renderInOverlayDuringTransition,
            )
    }
```

**Notes / pitfalls**

- `OverlayClip(clipShape)` resolves inside `with(sharedTransitionScope)` — it is a member of
  `SharedTransitionScope`. Outside that block it will not compile.
- The default `animatedVisibilityScope = LocalNavAnimatedContentScope.current` ties this helper to
  Navigation 3. On Navigation 2 / Compose Navigation, change the default to
  `LocalAnimatedVisibilityScope.current` (recipe 4) or make it a required parameter.
- `enter = fadeIn(), exit = fadeOut()` use **default** Compose springs, not motion-scheme specs.
  Improve on the original by passing `fadeIn(motionScheme.defaultEffectsSpec())` /
  `fadeOut(motionScheme.defaultEffectsSpec())` at the call site.
- `MaterialTheme.shapes.largeIncreased` is the Expressive shape token. Using it as the overlay clip
  is what keeps the travelling element looking like a Material container the whole way.
- `scaleToBounds(ContentScale.Crop)` scales rather than re-lays-out the content mid-flight. Use
  `ResizeMode.RemeasureToBounds` instead only when the content must genuinely reflow (long text
  changing line count); it is much more expensive.

---

## 6. Container transform: card → detail screen

**Use this when**: tapping a card opens a screen showing the same content. The highest-value
expressive motion and the most commonly skipped.

`[CORPUS Tomato]` — the real pair. Source card, `.../statsScreen/screens/StatsMainScreen.kt:252-270`

```kotlin
item {
    Column(
        verticalArrangement = Arrangement.spacedBy(16.dp),
        modifier = Modifier
            .then(
                if (!widthExpanded) {
                    Modifier.sharedBoundsReveal(
                        sharedTransitionScope = this@StatsMainScreen,
                        sharedContentState = this@StatsMainScreen.rememberSharedContentState(
                            "last week card"
                        ),
                        animatedVisibilityScope = LocalNavAnimatedContentScope.current,
                        clipShape = topListItemShape
                    )
                } else Modifier
            )
            .clip(topListItemShape)
            .background(listItemColors.containerColor)
            .clickable { onNavigate(Screen.Stats.LastWeek) }
    ) { /* summary content */ }
}
```

Destination — the whole `Scaffold` is the shared container, with the identical key.
`.../statsScreen/screens/LastWeekScreen.kt:176-186`

```kotlin
Scaffold(
    topBar = { /* ... */ },
    modifier = modifier
        .nestedScroll(scrollBehavior.nestedScrollConnection)
        .sharedBoundsReveal(
            sharedTransitionScope = this@LastWeekScreen,
            sharedContentState = this@LastWeekScreen.rememberSharedContentState("last week card"),
            animatedVisibilityScope = LocalNavAnimatedContentScope.current,
            clipShape = topListItemShape
        )
) { innerPadding -> /* ... */ }
```

Both screens are `SharedTransitionScope.` extension composables, so `this@ScreenName` is the scope.
Nested elements travel inside the container with their own keys — the heading uses `sharedBounds`
with `"last week heading"` on both sides; the chart uses `sharedElement` with `"last week chart"`,
guarded by `if (!widthExpanded) { ... } else Modifier`.

**Pitfalls**

- **Keys must match exactly** between source and destination and be unique per transition.
  Descriptive strings (`"last week card"`) are far easier to debug than hashes. In a list, key by
  item id: `"card-${item.id}"`.
- **Disable shared elements when both panes are visible.** Tomato guards every one with
  `if (!widthExpanded)`. On expanded widths the list and detail render simultaneously, so a shared
  element would have two live copies of one key and produce a nonsensical flight. Mandatory on
  adaptive layouts, not polish.
- `sharedBounds` vs `sharedElement`: `sharedBounds` when the two contents *differ* (summary card vs
  full screen) — it animates bounds and cross-fades content. `sharedElement` when the content is
  *identical* (same chart, same image) — one element between two positions, no cross-fade.
- Put the shared-bounds modifier **before** `.clip()`/`.background()`. The overlay clip comes from
  `clipShape`; the later `.clip()` shapes the resting-state card.
- The destination's shared node must exist on the **first frame**. Behind an `if (loaded)` guard or a
  `LaunchedEffect`, the transition silently degrades to a fade.

## 7. Shared bounds across a Navigation 3 `NavDisplay`, with predictive pop

**Use this when**: the app uses `androidx.navigation3` and you want transitions and shared elements
across the back stack, including the predictive-back preview.

`[CORPUS Tomato]`
`/root/work/repos/Tomato/androidApp/src/main/java/org/nsh07/pomodoro/ui/AppScreen.kt`
(imports lines 81-83; body lines 295-345)

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
            entry<Screen.Timer> { TimerScreen(/* ... */) }
            entry<Screen.AOD> { AlwaysOnDisplay(/* ... */) }
            entry<Screen.Settings.Main> { SettingsScreenRoot(/* ... */) }
            entry<Screen.Stats.Main> { StatsScreenRoot(/* ... */) }
        }
    )
}
```

**Notes**

- Three separate specs: forward (`transitionSpec`), programmatic back (`popTransitionSpec`), and
  gesture-driven back (`predictivePopTransitionSpec`). **All three must be set.** Omitting
  `predictivePopTransitionSpec` leaves a system default that will not match, and the shared elements
  visibly re-target when the gesture commits.
- `predictivePopTransitionSpec` is the Navigation 3 predictive-back hook, and the only
  predictive-back wiring found in the **community** corpus. JetNews does the same job with
  `NavigationBackHandler` + an edge-aware spec (recipe 10d) — read that before copying this one
  verbatim, because Tomato's three identical fades ignore the swipe edge.
- Tomato uses effects specs (pure cross-fade) at the nav level and lets the *shared elements* carry
  all spatial motion. Right division of labour: if the screen also slid, the shared element would
  appear to move twice.
- Inside each `entry<T>`, Nav3 provides `LocalNavAnimatedContentScope.current` — the
  `AnimatedVisibilityScope` the shared-bounds modifiers need. No manual `CompositionLocalProvider`
  (unlike Navigation 2, recipe 4).
- Nested `NavDisplay`s each get their own `SharedTransitionLayout` in Tomato. Shared elements do not
  cross a `SharedTransitionLayout` boundary.

---

## 8. `veilOut` / `unveilIn` — the curtain reveal

**Use this when**: you want the Compose Animation 1.10+ expressive "curtain" transition instead of a
cross-fade for a list→detail navigation. Not a material3 API — `androidx.compose.animation` — but
part of the same expressive wave.

`[CORPUS Tomato]` `.../ui/statsScreen/StatsScreen.kt` (imports lines 25-26; body lines 62-112)

```kotlin
import androidx.compose.animation.unveilIn
import androidx.compose.animation.veilOut
```

```kotlin
@OptIn(ExperimentalAnimationApi::class, ExperimentalMaterial3AdaptiveApi::class)
@Composable
fun StatsScreenRoot(/* ... */) {
    val colorScheme = colorScheme

    // Defer expensive charts until after the nav transition has settled.
    var chartsVisible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        delay(300)
        chartsVisible = true
    }

    SharedTransitionLayout {
        NavDisplay(
            backStack = viewModel.backStack,
            onBack = viewModel.backStack::onBack,
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
            entryProvider = entryProvider { /* ... */ }
        )
    }
}
```

**Notes / pitfalls**

- `veilOut(targetColor)` is an **exit** transition — the outgoing screen is covered by the colour.
  `unveilIn(initialColor)` is an **enter** transition — the incoming screen is revealed from under
  it. Pairing them the wrong way round produces a flash of the veil colour.
- Seed the colour from the theme (`colorScheme.surfaceDim`), never a literal. A hard-coded veil is
  the most visible dynamic-color failure you can ship.
- Requires `@OptIn(ExperimentalAnimationApi::class)` and Compose Animation 1.10+.
- The `chartsVisible` + `delay(300)` trick is unrelated to the veil: it defers building expensive
  charts until the transition has settled so it does not jank. Copy that for any heavy destination —
  cheaper than optimizing the chart.

## 9. `Modifier.animateBounds` / `LookaheadScope`

**Use this when**: a composable changes position or size because of a **layout change** (moving
between a `Row` and a `Column`, changing `Arrangement`, appearing in a different slot) rather than
because of a state value you control. `animateBounds` interpolates the layout result itself.

**Corroborated by two Google samples.** The four community apps (Tomato, vivi-music, LastChat, Med)
still return **zero** hits — but `android/ai-samples/jetpacker` has one real call site and
`android/androidify` has two. Both are reproduced below. The imports are easy to get wrong:

```kotlin
import androidx.compose.animation.animateBounds        // animation artifact, NOT animation-core
import androidx.compose.ui.layout.LookaheadScope       // ui artifact, NOT foundation.layout
```

### 9a. The bystander pattern — jetpacker (Google, `android/ai-samples`)

`[OFFICIAL jetpacker]`
`android/feature/trip/src/main/kotlin/com/example/jetpacker/feature/trip/TripScreen.kt`
(import 20 + 29; `LookaheadScope` opens at **161**; `animateBounds` at **173**)

This is the **only** `animateBounds` call site in the whole 103-file repo, inside its only
`LookaheadScope`. (A third grep hit at `ItineraryScreen.kt:29` is an **unused import** — not a call
site, do not count it.) Verbatim:

```kotlin
@Composable
fun JetPackerBottomBar(
  selectedTab: TripTab,
  onTabSelected: (TripTab) -> Unit,
  fabConfig: JetPackerFabConfig?,
  modifier: Modifier = Modifier,
) {
  val showExpenses = FeatureFlags.ENABLE_EXPENSE_MANAGEMENT
  val showVoiceNotes = FeatureFlags.ENABLE_VOICE_NOTES

  LookaheadScope {
    Row(
      modifier =
        modifier
          .fillMaxWidth()
          .windowInsetsPadding(WindowInsets.navigationBars)
          .padding(bottom = 16.dp)
          .padding(horizontal = 32.dp),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.Center,
    ) {
      JetPackerToolbar(
        modifier = Modifier.widthIn(max = 272.dp).animateBounds(this@LookaheadScope)
      ) {
        JetPackerToolbarAction(
          icon = Icons.Rounded.Event,
          onClick = { onTabSelected(TripTab.ITINERARY) },
          selected = selectedTab == TripTab.ITINERARY,
          contentDescription = "Itinerary",
        )
        // … two more conditional actions …
      }

      Spacer(Modifier.width(4.dp))

      AnimatedVisibility(
        visible = fabConfig != null,
        enter = slideInHorizontally { it / 2 } + fadeIn(),
        exit = slideOutHorizontally { it / 2 } + fadeOut(),
      ) {
        JetPackerFab(
          modifier = Modifier.padding(4.dp),
          onClick = fabConfig?.onClick ?: {},
          icon = fabConfig?.icon ?: Icons.Rounded.Add,
          contentDescription = fabConfig?.contentDescription,
          containerColor = fabConfig?.containerColor ?: MaterialTheme.colorScheme.tertiary,
          contentColor = fabConfig?.contentColor ?: MaterialTheme.colorScheme.onTertiary,
        )
      }
    }
  }
}
```

**What it buys.** The `Row` is `Arrangement.Center`ed and the toolbar is `widthIn(max = 272.dp)`, so
every time the conditional FAB enters or exits the toolbar's measured **position** (and possibly its
width) changes. `AnimatedVisibility` animates *its own* size; the sibling that gets repositioned as a
consequence would otherwise **teleport** on the same frame. `animateBounds` interpolates that
sibling's bounds from previous layout to new layout, in lockstep.

**The teachable rule, in one line: the actor animates itself, the bystander uses `animateBounds`.**

**Notes / pitfalls — all confirmed against this call site**

- **Modifier order: `widthIn` comes BEFORE `animateBounds`.** Constraint modifiers go first so the
  lookahead pass measures the *constrained* target; `animateBounds` then animates toward that.
  Putting `animateBounds` first animates toward an unconstrained target. jetpacker gets this right.
- **`LookaheadScope` is mandatory and should be tight.** `animateBounds` needs the *target* layout
  before it is committed; `LookaheadScope` runs the speculative measure/layout pass that produces it.
  Hence the explicit scope parameter: `Modifier.animateBounds(lookaheadScope: LookaheadScope, …)`.
  jetpacker wraps only the `Row` — the smallest subtree containing both the element that changes and
  the element that must react — **not** the `Scaffold` or the screen. A `LookaheadScope` forces an
  extra measure pass over everything it contains.
- **`this@LookaheadScope`** is the labelled receiver of the enclosing lambda and is the canonical
  form when the modifier is written inline. If the scope is obtained further away, hoist it:
  `LookaheadScope { val scope = this; … }`.
- **No `boundsTransform` is supplied here** — the default spring is used, and jetpacker threads no
  `MotionScheme` spec anywhere in the repo. See §9b for the version that does.
- **The receiving composable must apply its `modifier` parameter first in its own chain.**
  `JetPackerToolbar` starts with `modifier.dropShadow(…)`, which puts the `animateBounds` node at the
  very outside of the toolbar's chain — exactly where it must be to animate the whole component. A
  component that ignores or mis-orders its `modifier` silently breaks `animateBounds`.
- **Coordinates read *inside* the animated subtree are the approach (animated) coordinates.**
  `JetPackerToolbar` runs `.onGloballyPositioned { state.toolbarCoords = it }` inside, so its own
  selection pill (driven by `animateOffsetAsState`/`animateSizeAsState`) tracks correctly *during*
  the bounds animation instead of jumping to the final layout. Two animation systems composed,
  neither aware of the other.
- No `@OptIn` is required at material3 `1.5.0-alpha16` / compose-bom `2026.03.00` — `TripScreen.kt`
  carries no opt-in annotations at all.

### 9b. Motion-scheme-driven bounds + a nullable scope — androidify (Google)

androidify is the only source that feeds `MotionScheme` into bounds animation. Two pieces.

**(1) `MotionScheme` extension properties yielding `BoundsTransform`.**
`[OFFICIAL androidify]` `core/theme/src/main/java/com/android/developers/androidify/theme/Motion.kt`
(full file):

```kotlin
package com.android.developers.androidify.theme

import androidx.compose.animation.BoundsTransform
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MotionScheme
import androidx.compose.runtime.Composable

@OptIn(
    ExperimentalMaterial3ExpressiveApi::class,
    ExperimentalSharedTransitionApi::class,
)
val MotionScheme.sharedElementTransitionSpec: BoundsTransform
    @Composable
    get() = BoundsTransform { _, _ ->
        this@sharedElementTransitionSpec.slowSpatialSpec()
    }
```

That is the idiom worth stealing: **an extension property on `MotionScheme` that returns a
`BoundsTransform`**, so every shared-element and bounds animation in the app inherits the theme's
motion instead of a hardcoded tween. It reads as `MaterialTheme.motionScheme.sharedElementTransitionSpec`
at the call site and composes into `sharedBounds(boundsTransform = …)` or `animateBounds(…)` alike.

`[OFFICIAL androidify]` `core/theme/.../SharedElementsConfig.kt:80-96` adds the nullable scope local
and a second, differently-named property:

```kotlin
val LocalAnimateBoundsScope = compositionLocalOf<LookaheadScope?> {
    null
}

@OptIn(
    ExperimentalMaterial3ExpressiveApi::class,
    ExperimentalSharedTransitionApi::class,
)
val MotionScheme.sharedElementTransitionBounds: BoundsTransform
    @Composable
    get() = BoundsTransform { _, _ -> sharedElementTransitionSpec() }

fun <T> MotionScheme.sharedElementTransitionSpec(): FiniteAnimationSpec<T> {
    return tween(600)
}
```

⚠️ **Do not copy that last function.** androidify contains two same-named things with different
behaviour: the `Motion.kt` **property** honours `motionScheme.slowSpatialSpec()`, while the
`SharedElementsConfig.kt` **generic function** ignores the receiver entirely and hardcodes
`tween(600)`. Because `sharedElementTransitionBounds` delegates to the *function*, anything defaulting
to it silently gets a 600ms tween and no motion-scheme physics. Copy the property; treat the function
as the bug it is. (This is the single best "don't do this" example in the official corpus: a
motion-scheme-shaped API that does not read the motion scheme.)

**(2) `animateBounds` guarded by the nullable `LookaheadScope`.**
`[OFFICIAL androidify]`
`feature/results/src/main/java/com/android/developers/androidify/customize/ImageRenderer.kt:192-205`:

```kotlin
@Composable
private fun Modifier.safeAnimateBounds(): Modifier {
    val spec = MaterialTheme.motionScheme.slowEffectsSpec<Rect>()
    return if (LocalAnimateBoundsScope.current != null) {
        this.animateBounds(
            LocalAnimateBoundsScope.current!!,
            boundsTransform = { _, _ ->
                spec
            },
        )
    } else {
        this
    }
}
```

The point: `animateBounds` **requires** a `LookaheadScope`, so a reusable component that might be
rendered outside one has no safe call. Publishing the scope through a
`compositionLocalOf<LookaheadScope?> { null }` and degrading to `this` when it is absent makes the
component usable in both places without crashing — the opposite of the `error("No scope provided")`
default recommended for `LocalSharedTransitionScope` (recipe 4), and correct here because a missing
lookahead scope should mean "no animation", not "wrong call site".

Call sites (lines 135, 170) hoist it out of the modifier chain:
`val safeAnimateBounds = Modifier.safeAnimateBounds()` then `.then(safeAnimateBounds)` — computed
once, applied twice, rather than re-derived inside a `layout {}` chain.

Note androidify picks `slowEffectsSpec<Rect>()` here. **Bounds are spatial**; `slowSpatialSpec<Rect>()`
is the token-correct choice and is what `Motion.kt` uses. Treat the effects spec as a deliberate
"do not overshoot this particular image frame" judgement call, not a pattern.

**General pitfalls**

- `BoundsTransform` is a `fun interface` from `initialBounds`/`targetBounds` to a
  `FiniteAnimationSpec<Rect>`. Feeding it `motionScheme.defaultSpatialSpec<Rect>()` (or
  `slowSpatialSpec`) is how you get motion-scheme physics into bounds animation.
- `remember` the `BoundsTransform`, or hoist the spec as androidify does. A new lambda every
  recomposition restarts the animation.
- Participating children must be **keyed identically** across both layouts (same composable
  identity), or Compose treats them as different nodes and there is nothing to animate.
- If the change comes from state *you* control and the element does not move between parents,
  `animateContentSize` (recipe 3) or plain `animate*AsState` is simpler and cheaper.
- What the community apps do instead: `sharedBounds` (recipes 5-7) for cross-screen,
  `animateContentSize` for in-place. Reach for `animateBounds` only when the **parent layout** changes
  and the element you care about is the passive one.
- Debugging: Compose UI 1.11+ ships `LookaheadAnimationVisualDebugging`, which visualises target
  bounds and animation trajectories. `[UNVERIFIED]` exact API shape — check it before recommending a
  call.

## 10. Predictive back

**Use this when**: a back gesture should preview its destination, or should first collapse an
expanded surface rather than leaving the screen.

### 10a. Collapse-before-pop — `BackHandler`

`[CORPUS Med]`
`/root/work/repos/Med/app/src/main/kotlin/com/fedeveloper95/med/services/MedApp.kt:204-210`

```kotlin
BackHandler(enabled = fabMenuExpanded || selectedTab != 0) {
    if (fabMenuExpanded) {
        fabMenuExpanded = false
    } else if (selectedTab != 0) {
        selectedTab = 0
    }
}
```

**Notes**: this is the pattern for every expanded Expressive surface — FAB menu, expanded search
bar, selection mode, expanded navigation rail. The `enabled` guard is what lets back fall through to
the navigator once everything is collapsed. Every reference app uses plain
`androidx.activity.compose.BackHandler` for this; none uses a material3 predictive-back API.

Other corpus instances of the same shape:

```
vivi-music  ui/component/BottomSheet.kt:115   BackHandler(onBack = state::collapseSoft)
vivi-music  ui/component/SearchBar.kt:145     BackHandler(enabled = active) { ... }
vivi-music  ui/player/Queue.kt:278            BackHandler(onBack = onExitSelectionMode)
Med         EditModeActivity.kt:205           BackHandler(enabled = hasChanges) { ... }
```

### 10b. Navigation 3 predictive pop

Already covered — see recipe 7. `predictivePopTransitionSpec` is the correct hook and the only
predictive-back wiring in the **community** corpus. For the edge-aware official version, see 10d.

### 10c. Progress-driven predictive back — `PredictiveBackHandler`

**Use this when**: the back *gesture progress* (0f→1f) should drive an animation directly on a
**custom, non-navigation surface** — a drawer, an expanded player, a full-screen sheet.

`[OFFICIAL JetLagged]`
`/root/work/repos/compose-samples/JetLagged/app/src/main/java/com/example/jetlagged/JetLaggedDrawer.kt:98-118`
— the complete shape, and the one to copy. No community app in the corpus uses `PredictiveBackHandler`;
this is the reference implementation.

```kotlin
        val velocityTracker = remember {
            VelocityTracker()
        }
        PredictiveBackHandler(drawerState == DrawerState.Open) { progress ->
            try {
                progress.collect { backEvent ->
                    val targetSize = (drawerWidth - (drawerWidth * backEvent.progress))
                    translationX.snapTo(targetSize)
                    velocityTracker.addPosition(
                        SystemClock.uptimeMillis(),
                        Offset(backEvent.touchX, backEvent.touchY),
                    )
                }
                closeDrawer(velocityTracker.calculateVelocity().x)
            } catch (_: CancellationException) {
                openDrawer(velocityTracker.calculateVelocity().x)
            }
            velocityTracker.resetTracking()
        }
```

**Read the three things this gets right that a naive version misses:**

1. **`snapTo`, not `animateTo`, while the gesture drives progress.** Same rule as recipe 12 — a spring
   between finger and pixel adds latency. The settle animation happens *after* the flow terminates.
2. **A `VelocityTracker` fed from `backEvent.touchX`/`touchY`**, whose velocity is handed to the settle
   animation on **both** paths (`closeDrawer(v)` on commit, `openDrawer(v)` on cancel). This is what
   makes a flung-away drawer feel different from a slowly-dragged one.
3. **The cancel path reverts explicitly** in `catch (_: CancellationException)` and the tracker is
   reset **outside** the try/catch, so it runs on both paths.

Note JetLagged is *not* an Expressive app — it uses no `MotionScheme`. On an Expressive theme, the
settle animations inside `closeDrawer`/`openDrawer` should take `motionScheme.defaultSpatialSpec()`
with the tracked velocity as `initialVelocity`.

`[DERIVED]` The same contract applied to the standard Android 14+ "scale down and reveal what's
beneath" screen treatment — assembled from the JetLagged shape above plus the documented
`androidx.activity.compose` signature; verify against your activity-compose version:

```kotlin
import androidx.activity.compose.PredictiveBackHandler
import androidx.activity.BackEventCompat
import kotlin.coroutines.cancellation.CancellationException

var backProgress by remember { mutableFloatStateOf(0f) }
var backSwipeEdge by remember { mutableIntStateOf(BackEventCompat.EDGE_LEFT) }

// Only matters when progress jumps back to 0f on cancel/commit — spring, not tween.
val settled by animateFloatAsState(
    targetValue = backProgress,
    animationSpec = motionScheme.defaultSpatialSpec(),
    label = "backProgress"
)

PredictiveBackHandler(enabled = canGoBack) { progressFlow ->
    try {
        progressFlow.collect { event ->
            backProgress = event.progress          // 0f..1f, driven by the finger
            backSwipeEdge = event.swipeEdge
        }
        onBack()                                   // flow completed => gesture committed
        backProgress = 0f
    } catch (e: CancellationException) {
        backProgress = 0f                          // released before threshold => spring back
        throw e
    }
}

Box(
    modifier = Modifier.graphicsLayer {
        val s = lerp(1f, 0.9f, settled)
        scaleX = s; scaleY = s
        translationX = lerp(
            0f,
            if (backSwipeEdge == BackEventCompat.EDGE_LEFT) 48.dp.toPx() else -48.dp.toPx(),
            settled
        )
        transformOrigin = TransformOrigin(
            pivotFractionX = if (backSwipeEdge == BackEventCompat.EDGE_LEFT) 1f else 0f,
            pivotFractionY = 0.5f
        )
    }
) { ScreenContent() }
```

**Pitfalls**

- `onBack` is `suspend (Flow<BackEventCompat>) -> Unit`. Flow **completing** = committed; a
  `CancellationException` = cancelled. The catch block is where you revert.
- **Rethrow vs swallow:** the Google sample (JetLagged, above) *swallows* the `CancellationException`
  after reverting — `catch (_: CancellationException) { openDrawer(...) }` with no rethrow — because
  the cancellation is the gesture's own signal, not the enclosing scope being torn down, and the
  handler's coroutine ends immediately after. Follow the sample. If you do additional suspending work
  after the catch, rethrow instead so structured cancellation still propagates.
- Do not put a spring between the finger and the transform while the gesture is live; it adds
  latency. If it still feels laggy, drive the layer from `backProgress` directly during the drag and
  spring only the release, as in recipe 12.
- Apply the transform in `graphicsLayer`, never by animating size/padding. Draw phase only.

### 10d. Nav3 predictive back with `NavigationBackHandler` — the newest form

**Use this when**: the app is on Navigation 3 and you want the back gesture integrated with
`NavDisplay` rather than hand-driven. This supersedes `PredictiveBackHandler` **for navigation
surfaces**; `PredictiveBackHandler` (10c) is still correct for bespoke gesture surfaces that own
their own back semantics.

`[OFFICIAL JetNews]`
`/root/work/repos/compose-samples/JetNews/app/src/main/java/com/example/jetnews/ui/JetnewsNavDisplay.kt:~95-130`
— `androidx.navigationevent.compose.NavigationBackHandler` + `rememberNavigationEventState`, wired
into `NavDisplay`, with an **edge-aware** `predictivePopTransitionSpec`:

```kotlin
            val scene = sceneState.currentScene

            val currentInfo = SceneInfo(scene)
            val previousSceneInfos = sceneState.previousScenes.map { SceneInfo(it) }
            val navigationEventState = rememberNavigationEventState(
                currentInfo = currentInfo,
                backInfo = previousSceneInfos,
            )

            NavigationBackHandler(
                state = navigationEventState,
                isBackEnabled = isBackEnabled && scene.previousEntries.isNotEmpty(),
                onBackCompleted = {
                    repeat(navEntries.size - scene.previousEntries.size) { onBack() }
                },
            )

            NavDisplay(
                sceneState = sceneState,
                navigationEventState = navigationEventState,
                predictivePopTransitionSpec = {
                    unveilIn() togetherWith scaleOut(
                        targetScale = .8f,
                        transformOrigin = when (it) {
                            NavigationEvent.EDGE_LEFT -> TransformOrigin(1 - PIVOT_FRACTION_OFFSET, .5f)
                            NavigationEvent.EDGE_RIGHT -> TransformOrigin(PIVOT_FRACTION_OFFSET, .5f)
                            else -> TransformOrigin.Center
                        },
                    )
                },
            )
```

**Notes**

- **The `predictivePopTransitionSpec` lambda receives the swipe edge.** `it` is a
  `NavigationEvent.EDGE_LEFT` / `EDGE_RIGHT` value, and the `transformOrigin` pivots *away* from the
  swiped edge — the correct Material predictive-back feel, and something Tomato's fade-only spec
  (recipe 7) does not do. This is the upgrade path from recipe 7: keep the three specs, make the
  predictive one edge-aware.
- `unveilIn()` here is the recipe-8 curtain transition used as the *enter* half of a pop —
  consistent with the rule in §8 (`unveilIn` enters, `veilOut` exits).
- **This `NavDisplay` overload takes `sceneState` + `navigationEventState`, not a raw back stack.**
  That is a different call shape from recipe 7 and from Tomato's. `[UNVERIFIED]` which navigation3
  version introduced it — confirm against your pin before rewriting a working `NavDisplay(backStack =
  …)` call.
- `onBackCompleted` pops *n* entries at once (`repeat(navEntries.size - scene.previousEntries.size)`)
  because a multi-pane scene can hold several entries; a single `onBack()` would leave the display
  mid-scene.
- Related JetNews files: `.../navigation/NavigationState.kt:69`
  (`rememberNavBackStack(*backStack.toTypedArray())`) and `.../navigation/ListDetailScene.kt:75`
  (`NavDisplay.TransitionKey`, `NavDisplay.PopTransitionKey`).

## 11. `AnimatedContent` / `AnimatedVisibility` with motion scheme specs

**Use this when**: swapping one piece of content for another, or showing/hiding.

### Directional value swap (odometer-style)

`[CORPUS Tomato]`
`/root/work/repos/Tomato/shared/src/androidMain/kotlin/org/nsh07/pomodoro/ui/timerScreen/TimerScreen.kt:229-245` (and again at 641-679)

```kotlin
AnimatedContent(
    if (!timerState.showBrandTitle) timerState.timerMode else TimerMode.BRAND,
    transitionSpec = {
        slideInVertically(
            animationSpec = motionScheme.defaultSpatialSpec(),
            initialOffsetY = { (-it * 1.25).toInt() }
        ).togetherWith(
            slideOutVertically(
                animationSpec = motionScheme.defaultSpatialSpec(),
                targetOffsetY = { (it * 1.25).toInt() }
            )
        )
    },
    contentAlignment = Alignment.Center,
    modifier = Modifier.fillMaxWidth(.9f)
) { mode ->
    when (mode) { /* ... */ }
}
```

**Note**: the `1.25` multiplier overshoots the slide distance so the outgoing content clears the
bounds before the incoming content arrives — that is what stops the two texts overlapping mid-swap
(motion principle 7, "clean fading").

### Fade + scale reveal

`[CORPUS Tomato]` `.../timerScreen/TimerScreen.kt:322-328`

```kotlin
this@Column.AnimatedVisibility(
    !timerState.infiniteFocus,
    enter = fadeIn(motionScheme.defaultEffectsSpec()) +
            scaleIn(motionScheme.defaultSpatialSpec(), 4f),
    exit  = fadeOut(motionScheme.defaultEffectsSpec()) +
            scaleOut(motionScheme.defaultSpatialSpec(), 4f)
) { /* the progress ring */ }
```

**Note**: `4f` is the *initial scale* — the ring scales down **from** 4× on enter. A dramatic hero
reveal. Use fractional values (`0.92f`) for ordinary content; `scaleIn(initialScale = 4f)` on a list
row would be absurd.

### Slide a whole surface off-screen

`[CORPUS Tomato]` `/root/work/repos/Tomato/androidApp/src/main/java/org/nsh07/pomodoro/ui/AppScreen.kt:175-180`

```kotlin
Scaffold(
    bottomBar = {
        AnimatedVisibility(
            backStack.last() !is Screen.AOD,
            enter = slideInVertically(motionScheme.slowSpatialSpec()) { it },
            exit  = slideOutVertically(motionScheme.slowSpatialSpec()) { it }
        ) { /* bottom bar */ }
    }
)
```

**Note**: full-width surface → `slowSpatialSpec()`. Tier matched to scale.

### Cross-fade between screens, and asymmetric fade tiers

`[CORPUS Tomato]` `.../ui/AppScreen.kt:299-311`

```kotlin
fadeIn(motionScheme.defaultEffectsSpec())
    .togetherWith(fadeOut(motionScheme.defaultEffectsSpec()))
```

`[CORPUS vivi-music]` `app/src/main/kotlin/com/music/vivi/WelcomeActivity.kt`

```kotlin
AnimatedVisibility(
    visible = !showFinishingTransition,
    enter = fadeIn(MaterialTheme.motionScheme.defaultEffectsSpec()),
    exit  = fadeOut(MaterialTheme.motionScheme.fastEffectsSpec()),
    modifier = Modifier.fillMaxSize()
) { /* ... */ }
```

**Note**: asymmetric tiers — enter at `default`, exit at `fast`. Departures should not linger; the
effects-family equivalent of "overshoot for arrivals, not departures." The same file drives one
state across both families at once (`animateDpAsState` + `defaultSpatialSpec()` for a button width
and a spacing, `animateFloatAsState` + `defaultEffectsSpec()` for its alpha), and feeds
`pageTransitionSpatialSpec = motionScheme.defaultSpatialSpec<Float>()` into
`pagerState.animateScrollToPage(page, animationSpec = ...)`.

## 12. Gesture-driven motion — spring only on release

**Use this when**: a finger is dragging something. The rule: **`snapTo` while the finger is down,
`animateTo` when it lifts.** A spring between finger and pixel adds latency and makes the UI feel
detached.

`[CORPUS LastChat]` `.../ui/components/ui/PhysicsSwipeToDelete.kt:277-370`

```kotlin
.pointerInput(Unit) {
    detectHorizontalDragGestures(
        onDragStart = { isDragging = true },
        onDragEnd = {
            isDragging = false
            onDragEnd?.invoke()
            scope.launch {
                if (!deleteEnabled) {                    // spring back, delete disabled
                    if (offsetX.value.absoluteValue > 10f) haptics.perform(HapticPattern.Thud)
                    offsetX.animateTo(
                        targetValue = 0f,
                        animationSpec = spring(
                            dampingRatio = 0.55f,
                            stiffness = Spring.StiffnessMediumLow
                        )
                    )
                } else if (offsetX.value.absoluteValue > unlockThresholdPx) {
                    if (!isUnlocked) {                   // past threshold: snap open
                        haptics.perform(HapticPattern.Pop)
                        isUnlocked = true
                    }
                    offsetX.animateTo(
                        targetValue = -revealDistancePx,
                        animationSpec = spring(dampingRatio = 0.6f, stiffness = 300f)
                    )
                } else {                                 // under threshold: snap closed
                    if (isUnlocked) {
                        haptics.perform(HapticPattern.Thud)
                        isUnlocked = false
                    }
                    offsetX.animateTo(
                        targetValue = 0f,
                        animationSpec = spring(
                            dampingRatio = 0.55f,
                            stiffness = Spring.StiffnessLow
                        )
                    )
                }
            }
        },
        onDragCancel = {
            isDragging = false
            scope.launch {
                offsetX.animateTo(
                    targetValue = if (isUnlocked) -revealDistancePx else 0f,
                    animationSpec = spring(dampingRatio = 0.6f)
                )
            }
            onDragEnd?.invoke()
        },
        onHorizontalDrag = { change, dragAmount ->
            change.consume()
            val currentOffset = offsetX.value
            if (dragAmount < 0 || currentOffset < 0) {
                // extra resistance before the detent — "magnetic pull"
                val friction = if (currentOffset.absoluteValue < unlockThresholdPx && !isUnlocked) {
                    dragFriction * (1f - magneticPullStrength * (currentOffset.absoluteValue / unlockThresholdPx))
                } else dragFriction

                val newOffset = (currentOffset + dragAmount * friction)
                    .coerceIn(-revealDistancePx * 1.2f, 0f)

                scope.launch { offsetX.snapTo(newOffset) }   // <-- snapTo during drag

                // haptics fire on threshold CROSSINGS, not continuously
                val wasUnder = currentOffset.absoluteValue < unlockThresholdPx
                val isOver = newOffset.absoluteValue >= unlockThresholdPx
                if (wasUnder && isOver && !isUnlocked) haptics.perform(HapticPattern.Pop)
                else if (!wasUnder && !isOver && currentOffset.absoluteValue > 0)
                    haptics.perform(HapticPattern.Tick)
            }
        }
    )
}
```

**Notes / pitfalls**

- `snapTo` in `onHorizontalDrag`, `animateTo` in `onDragEnd`/`onDragCancel`. That split is the whole
  recipe.
- Three release targets, three springs. On an Expressive theme replace the hand-rolled ones with
  `motionScheme.defaultSpatialSpec()` (return) and `motionScheme.fastSpatialSpec()` (commit).
- **Handle `onDragCancel`.** Without it an interrupted gesture leaves the element stranded
  mid-flight. Most commonly forgotten callback.
- Friction makes the element move at 60% of finger speed, less near the detent — that is what makes
  a threshold feel physical rather than binary.
- `change.consume()` prevents the parent scroller from stealing the gesture.
- The same file computes `unlockProgress` and the interpolated corner shape in `derivedStateOf`
  (lines 153-204) so downstream recomposition fires only when the derived value changes.

### Sliders: snap while dragging, spring on release

`[CORPUS Tomato]` `.../settingsScreen/components/SliderListItem.kt:49-115` — the cleanest expression
of the rule: one `animateFloatAsState`, spec swapped between the motion scheme and `snap()` by a
boolean. Use this when you cannot restructure into `Animatable` + `snapTo`.

```kotlin
var animateSliderValue by remember { mutableStateOf(true) }
var value by remember(value) { mutableFloatStateOf(value) }

val valueAnimated by animateFloatAsState(
    value,
    animationSpec = if (animateSliderValue) motionScheme.defaultSpatialSpec()
    else snap()
)

Slider(
    value = valueAnimated,
    onValueChange = {
        animateSliderValue = false     // finger down: no spring
        value = it
    },
    onValueChangeFinished = {
        animateSliderValue = true      // finger up: spring again
        onValueChangeFinished(value)
    },
    valueRange = valueRange,
)
```

vivi-music does the same thing on a `rememberSliderState` volume row
(`.../vivimusic/AudioDeviceBottomSheet.kt`): raw `sliderState.value = newValue` while
`sliderState.isDragging`, then in `onValueChangeFinished` an `animate(initialValue, targetValue,
animationSpec = MaterialTheme.motionScheme.fastEffectsSpec())` in a job that the next drag cancels.
Note it picks an **effects** spec for a thumb position — defensible (a slider must not overshoot its
range) but `fastSpatialSpec()` is the token-correct choice. Judgement call, not a pattern to copy
blindly.

### Pull-to-refresh offset

`[CORPUS vivi-music]` `app/src/main/kotlin/com/music/vivi/ui/screens/HomeScreen.kt`

```kotlin
val expressiveSpring = MaterialTheme.motionScheme.defaultSpatialSpec<Float>()
val animatedFraction by animateFloatAsState(
    targetValue = if (isRefreshing) 1f else pullRefreshState.distanceFraction.coerceIn(0f, 1f),
    animationSpec = expressiveSpring,
    label = "pull_fraction"
)

PullToRefreshBox(state = pullRefreshState, isRefreshing = isRefreshing, onRefresh = ::refresh,
    indicator = {
        if (animatedFraction > 0.001f) {
            val yOffset = lerp(-indicatorHeightPx, topInsetPx, animatedFraction)
            val m = Modifier.align(Alignment.TopCenter).offset { IntOffset(0, yOffset.toInt()) }
            if (isRefreshing) ContainedLoadingIndicator(modifier = m)          // indeterminate
            else ContainedLoadingIndicator(                                    // determinate
                progress = { pullRefreshState.distanceFraction.coerceIn(0f, 1f) },
                modifier = m
            )
        }
    }
) { /* content */ }
```

**Notes**: the determinate indicator tracks the **raw** drag fraction — no spring, it must follow the
finger — while the indicator's *position* is spring-animated. Two values, two treatments.
`.offset { IntOffset(...) }` is the lambda overload: layout phase only.

## 13. List item enter / exit / reorder

**Use this when**: items are inserted, removed, or reordered in a `LazyColumn`/`LazyRow`/`LazyGrid`.

`[CORPUS LastChat]` `.../ui/pages/chat/ConversationList.kt:286-334` (the same three-arg call is
repeated for headers and items; `.../ui/components/ui/AppToast.kt:184` uses the same shape):

```kotlin
modifier = Modifier.animateItem(
    fadeInSpec  = spring(dampingRatio = 0.6f, stiffness = 300f),
    fadeOutSpec = spring(dampingRatio = 0.6f, stiffness = 300f),
    placementSpec = spring(
        stiffness = Spring.StiffnessMediumLow,
        visibilityThreshold = IntOffset.VisibilityThreshold
    )
)
```

Motion-scheme version `[DERIVED]` — prefer this:

```kotlin
modifier = Modifier.animateItem(
    fadeInSpec    = motionScheme.defaultEffectsSpec(),   // appearance → effects
    fadeOutSpec   = motionScheme.fastEffectsSpec(),      // departures leave faster
    placementSpec = motionScheme.defaultSpatialSpec()    // reorder → spatial
)
```

**Pitfalls**

- **`items(key = { it.id })` is mandatory.** Without a stable key Compose cannot tell a reorder from
  a content change, and `animateItem` does nothing.
- `fadeInSpec`/`fadeOutSpec` are effects, `placementSpec` is spatial. LastChat's fade springs are
  underdamped at 0.6, so the alpha rings instead of settling cleanly — Material's `MotionScheme` KDoc
  says effects motion must have no overshoot. `graphicsLayer.alpha` is clamped to `[0f, 1f]` so you
  will not see a spike past full opacity; the damage is that these fades land late and out of step
  with every other fade in the app. That is why the motion-scheme version above is the one to copy.
- A hand-rolled `placementSpec` needs `visibilityThreshold = IntOffset.VisibilityThreshold` or the
  spring burns frames chasing sub-pixel offsets. Motion-scheme specs handle this.
- `animateItem` only animates items that stay in composition. Items scrolled off-screen and back
  jump into place — expected.
- Med's `animateItem(placementSpec = tween(durationMillis = 200))` (`EditModeActivity.kt:321`) is a
  migration candidate, not a model: a reorder is spatial and should be a spring.

## 14. Worked example — motion in a Tomato-style timer

**Use this when**: a full-screen focal element (a progress ring, an album cover, a map) must survive
a navigation change and land at a different size and position on the destination.

Tomato's timer → always-on-display transition, from both sides. It combines theme setup, a hoisted
motion scheme, slow effects for a whole-screen recolor, shared bounds on the ring and the clock,
spatial enter/exit for the ring, and a nav-level cross-fade.

**Step 1 — theme supplies the scheme.** `.../ui/theme/Theme.android.kt:80-87`

```kotlin
MaterialExpressiveTheme(
    colorScheme = scheme,
    typography = typography(),
    motionScheme = MotionScheme.expressive(),
    content = content
)
```

**Step 2 — root `NavDisplay` inside `SharedTransitionLayout`, pure cross-fade.**
`.../ui/AppScreen.kt:295-320` — full block in recipe 7. The screen transition is
`fadeIn(motionScheme.defaultEffectsSpec()).togetherWith(fadeOut(...))` on all three specs
(`transitionSpec`, `popTransitionSpec`, `predictivePopTransitionSpec`). All spatial motion comes from
shared elements. Progress is threaded as `progress = { progress }` — a lambda, not a value.

**Step 3 — the ring and clock on the timer screen.** `.../timerScreen/TimerScreen.kt:322-419`

```kotlin
this@Column.AnimatedVisibility(
    !timerState.infiniteFocus,
    enter = fadeIn(motionScheme.defaultEffectsSpec()) +
            scaleIn(motionScheme.defaultSpatialSpec(), 4f),
    exit  = fadeOut(motionScheme.defaultEffectsSpec()) +
            scaleOut(motionScheme.defaultSpatialSpec(), 4f)
) {
    if (timerState.timerMode == TimerMode.FOCUS) {
        CircularProgressIndicator(
            progress = progress,
            modifier = Modifier
                .sharedBounds(
                    sharedContentState = this@TimerScreen.rememberSharedContentState(
                        "focus progress"
                    ),
                    animatedVisibilityScope = LocalNavAnimatedContentScope.current
                )
                .fillMaxWidth(0.9f)
                .aspectRatio(1f),
            color = color,
            trackColor = colorContainer,
            strokeWidth = 16.dp,
            gapSize = 8.dp
        )
    } else {
        // CircularWavyProgressIndicator, key "break progress" — different component,
        // therefore a different shared-content key. Same bounds and colors otherwise.
    }
}

Text(
    text = timerState.timeStr,
    style = TextStyle(
        fontFamily = typography.bodyLarge.fontFamily,
        fontSize = clockFontSize.sp,          // spring-animated Float from recipe 1
        letterSpacing = (-2.6).sp,
        fontFeatureSettings = "tnum"
    ),
    maxLines = 1,
    autoSize = TextAutoSize.StepBased(maxFontSize = clockFontSize.sp),
    modifier = Modifier
        .fillMaxWidth()
        .sharedBounds(
            sharedContentState = this@TimerScreen.rememberSharedContentState("clock"),
            animatedVisibilityScope = LocalNavAnimatedContentScope.current
        )
)
```

**Step 4 — the same three keys on the destination.** `.../ui/AlwaysOnDisplay.kt:85-292`

```kotlin
@Composable
fun SharedTransitionScope.AlwaysOnDisplay(
    timerState: TimerState,
    progress: () -> Float,
    modifier: Modifier = Modifier
) {
    var sharedElementTransitionComplete by remember { mutableStateOf(false) }

    // Hold the app palette until the shared-element flight has landed, then fade to AOD greys.
    LaunchedEffect(Unit) {
        delay(300)
        sharedElementTransitionComplete = true
    }

    val primary by animateColorAsState(
        if (sharedElementTransitionComplete) Color(0xFFA2A2A2)
        else {
            if (timerState.timerMode == TimerMode.FOCUS) colorScheme.primary
            else colorScheme.tertiary
        },
        animationSpec = motionScheme.slowEffectsSpec()
    )
    val surface by animateColorAsState(
        if (sharedElementTransitionComplete) Color.Black else colorScheme.surface,
        animationSpec = motionScheme.slowEffectsSpec()
    )
    // secondaryContainer / onSurface follow the same shape

    Box(modifier = modifier.fillMaxSize().background(surface)) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(250.dp)
                .offset { IntOffset(x, y) }        // burn-in avoidance; layout-phase lambda
        ) {
            CircularProgressIndicator(
                progress = progress,
                modifier = Modifier
                    .sharedBounds(
                        sharedContentState = this@AlwaysOnDisplay.rememberSharedContentState(
                            "focus progress"           // <-- same key as TimerScreen
                        ),
                        animatedVisibilityScope = LocalNavAnimatedContentScope.current
                    )
                    .size(250.dp),                     // <-- different size: the ring flies
                color = primary,
                trackColor = secondaryContainer,
                strokeWidth = 12.dp,                   // <-- and thins out
                gapSize = 8.dp,
            )
            // ... "break progress" wavy branch, and the "clock" Text with the third key
        }
    }
}
```

### What to take from it

1. **The screen cross-fades; the focal elements fly.** Never both — the eye cannot track an element
   moving inside a container that is also moving.
2. **Three keys, matched exactly**: `"focus progress"`, `"break progress"`, `"clock"`. The focus and
   break rings are *different components* (`CircularProgressIndicator` vs
   `CircularWavyProgressIndicator`) so they get different keys. One key across two different
   composables produces a garbled flight.
3. **The destination geometry differs on purpose** — `fillMaxWidth(0.9f).aspectRatio(1f)` becomes
   `size(250.dp)`, stroke 16.dp becomes 12.dp. That difference *is* the animation. `sharedBounds`
   interpolates bounds; the stroke width snaps, invisible under motion.
4. **`sharedElementTransitionComplete` + `delay(300)`** holds the app colours until the flight lands,
   then fades to AOD greys on `slowEffectsSpec()`. Never recolor *during* a shared-element flight —
   the element renders in the overlay and a simultaneous colour change reads as a flicker. Sequence
   them.
5. **`progress: () -> Float`**, not `progress: Float`, from the ViewModel down. The recomposition
   scope stays at the indicator; nothing above recomposes at tick rate.
6. **`.offset { IntOffset(x, y) }`** — lambda overload, layout phase only. The AOD nudges the clock
   every minute for burn-in without invalidating composition.
7. **Both screens are `SharedTransitionScope.` extension functions**, which is how `this@TimerScreen`
   and `this@AlwaysOnDisplay` resolve. Tomato's alternative to LastChat's CompositionLocal approach
   (recipe 4). Pick one per app and be consistent.

## 15. Quick index

| Need | Recipe | Primary source |
| --- | --- | --- |
| Read a spec off the theme | 1 | Tomato `TimerScreen.kt:173` |
| `animateDpAsState` + `animateColorAsState` together | 1 | Tomato `MinuteInputField.kt:75` |
| `updateTransition` | 1 | canonical, not in corpus |
| Press/scale indication | 2 | canonical, not in corpus |
| Press corner morph | 2 | Med `MainActivity.kt:442` |
| Selection "pop" | 2 | LastChat `TabAnimation.kt:50` |
| Expand/collapse | 3 | Tomato `TimerScreen.kt:426`, `AlarmSettings.kt:291` |
| App-wide shared transition scope | 4 | LastChat `RouteActivity.kt:660` |
| Reusable `sharedBoundsReveal` | 5 | Tomato `sharedBoundsReveal.kt` (complete file) |
| Container transform card→detail | 6 | Tomato `StatsMainScreen.kt:252` / `LastWeekScreen.kt:176` |
| Nav3 transitions + predictive pop | 7 | Tomato `AppScreen.kt:295` |
| `veilOut` / `unveilIn` | 8 | Tomato `StatsScreen.kt:62`, JetNews (as pop-enter) |
| `animateBounds` / `LookaheadScope` (bystander pattern) | 9a | **jetpacker** `TripScreen.kt:161/173` (only call site) |
| `MotionScheme` → `BoundsTransform` extension property | 9b | **androidify** `theme/Motion.kt` (full file) |
| `animateBounds` with a nullable `LookaheadScope` local | 9b | **androidify** `ImageRenderer.kt:192` |
| Collapse-before-pop back handling | 10a | Med `MedApp.kt:204` |
| `PredictiveBackHandler` + `VelocityTracker` | 10c | **JetLagged** `JetLaggedDrawer.kt:98` |
| `NavigationBackHandler` / edge-aware predictive pop | 10d | **JetNews** `JetnewsNavDisplay.kt:~95` |
| `AnimatedContent` / `AnimatedVisibility` | 11 | Tomato `TimerScreen.kt:229`, `AppScreen.kt:175` |
| Gesture: snap on drag, spring on release | 12 | LastChat `PhysicsSwipeToDelete.kt:277` |
| Slider settle | 12 | vivi-music `AudioDeviceBottomSheet.kt`, Tomato `SliderListItem.kt:49` |
| Pull-to-refresh | 12 | vivi-music `HomeScreen.kt` |
| List item enter/reorder | 13 | LastChat `ConversationList.kt:286` |
| Full worked example | 14 | Tomato `TimerScreen.kt` + `AlwaysOnDisplay.kt` |

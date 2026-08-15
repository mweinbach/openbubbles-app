# MotionScheme — API, spring values, and motion theory

Reference layer for `androidx.compose.material3.MotionScheme`. Code recipes live in
`motion-recipes.md`.

**Provenance tags used below**

| Tag | Meaning |
| --- | --- |
| `[SRC]` | Verbatim from androidx source / rendered reference docs |
| `[CANON]` | m3.material.io design guidance, verbatim or near-verbatim |
| `[CORPUS]` | Lifted from a shipping open-source app (path given) |
| `[DERIVED]` | Computed or inferred here — not a published Material value |
| `[UNVERIFIED]` | Could not be confirmed against a primary source; verify before relying on it |

---

## 1. The interface

`[SRC]` — verbatim:

```kotlin
interface MotionScheme
```

> "A motion scheme provides all the `FiniteAnimationSpec`s for a `MaterialTheme`.
> Motion schemes are designed to create a harmonious motion for components in the app.
> There are two built-in schemes, a `standard` and an `expressive`, that can be used as-is or
> customized. You can customize the motion scheme for all components in the `MaterialTheme`."

All six members `[SRC]`:

```kotlin
fun <T> defaultSpatialSpec(): FiniteAnimationSpec<T>
fun <T> fastSpatialSpec():    FiniteAnimationSpec<T>
fun <T> slowSpatialSpec():    FiniteAnimationSpec<T>
fun <T> defaultEffectsSpec(): FiniteAnimationSpec<T>
fun <T> fastEffectsSpec():    FiniteAnimationSpec<T>
fun <T> slowEffectsSpec():    FiniteAnimationSpec<T>
```

KDoc, verbatim `[SRC]`:

- Spatial: *"This motion spec is designed to be applied to animations that may change the shape or
  bounds of the component. For color or alpha animations use the `effects` equivalent which ensures
  a 'non-spatial' motion."*
- Effects: *"This motion spec is designed to be applied to animations that do not change the shape
  or bounds of the component. For example, color animation."*
- On `T`: *"…the generic data type that will be animated by the system, as long as the appropriate
  `TwoWayConverter` for converting the data to and from an `AnimationVector` is supplied."*

`T` is inferred at the call site unless assigned to a bare `val`:

```kotlin
val spec = MaterialTheme.motionScheme.defaultSpatialSpec<Dp>()      // explicit
val alphaSpec = MaterialTheme.motionScheme.fastEffectsSpec<Float>() // explicit
val w by animateDpAsState(target, MaterialTheme.motionScheme.defaultSpatialSpec()) // inferred
```

Every returned spec is a `FiniteAnimationSpec<T>` — what `AnimatedVisibility`, `AnimatedContent`,
`Modifier.animateContentSize`, `Modifier.animateItem` and `BoundsTransform` all require.

---

## 2. Companion object

`[SRC]` `MotionScheme.kt`, verbatim:

```kotlin
companion object {

    /**
     * Returns a standard Material motion scheme.
     *
     * The standard scheme is Material's basic motion scheme for utilitarian UI elements and
     * recurring interactions. It provides a linear motion feel.
     */
    @Suppress("UNCHECKED_CAST") fun standard(): MotionScheme = StandardMotionSchemeImpl

    /**
     * Returns an expressive Material motion scheme.
     *
     * The expressive scheme is Material's recommended motion scheme for prominent UI elements
     * and hero interactions. It provides a visually engaging motion feel.
     */
    @Suppress("UNCHECKED_CAST") fun expressive(): MotionScheme = ExpressiveMotionSchemeImpl
}
```

Also in that file (names verified, bodies not captured) `[SRC]`: `MotionScheme.fromToken(...)`
mapping a `MotionSchemeKeyTokens` to a spec, and a `@Composable` `MotionSchemeKeyTokens.value()`
accessor reading `MaterialTheme.motionScheme`.

Things that **do not exist** — do not write them:

- No top-level `expressiveMotionScheme()` or `standardMotionScheme()` function.
- No `LocalMotionScheme` composition local. Motion lives on the single `LocalMaterialTheme`.

---

## 3. Accessor

`[SRC]` `MaterialTheme.kt`, verbatim (alongside identical `colorScheme` / `typography` / `shapes`
accessors):

```kotlin
val motionScheme: MotionScheme
    @Composable @ReadOnlyComposable get() = LocalMaterialTheme.current.motionScheme
```

As of material3 `1.5.0-alpha15` the whole theme is **one** composition local
(`MaterialTheme.LocalMaterialTheme.current`). Code that read `LocalColorScheme` / `LocalTypography`
directly needs updating.

### The hoisting idiom `[CORPUS Tomato]`

`MaterialTheme.motionScheme` is `@Composable`, so it cannot be read from inside a non-composable
lambda (`transitionSpec = { ... }` on `AnimatedContent` is composable; `onDragEnd = { ... }` on a
gesture detector is not). Tomato imports the property directly and hoists it once per screen:

```kotlin
import androidx.compose.material3.MaterialTheme.motionScheme
// ...
val motionScheme = motionScheme      // hoist at the top of the composable
val scope = rememberCoroutineScope()
```

`/root/work/repos/Tomato/shared/src/androidMain/kotlin/org/nsh07/pomodoro/ui/timerScreen/TimerScreen.kt:174`

That local `motionScheme` is then usable inside coroutines, gesture callbacks, and `remember`
blocks. Do this whenever a screen uses more than two specs.

---

## 4. Wiring it into the theme

Two entry points `[SRC]`:

```kotlin
@Composable
fun MaterialExpressiveTheme(
    colorScheme: ColorScheme? = null,
    motionScheme: MotionScheme? = null,
    shapes: Shapes? = null,
    typography: Typography? = null,
    content: @Composable () -> Unit,
)

// MaterialTheme's second overload — the only one that takes motion
@Composable
fun MaterialTheme(
    colorScheme: ColorScheme = MaterialTheme.colorScheme,
    motionScheme: MotionScheme = MaterialTheme.motionScheme,
    shapes: Shapes = MaterialTheme.shapes,
    typography: Typography = MaterialTheme.typography,
    content: @Composable () -> Unit,
)
```

`MaterialExpressiveTheme` params are **nullable**, and `null` means "use the Expressive default",
not "inherit from ambient". Its documented default motion scheme is `MotionScheme.expressive()`.
`MaterialTheme`'s default is `MotionScheme.standard()`.

Real setup `[CORPUS Tomato]` `.../ui/theme/Theme.android.kt:80-87`:

```kotlin
CompositionLocalProvider(LocalAppFonts provides getAppFonts()) {
    MaterialExpressiveTheme(
        colorScheme = scheme,
        typography = typography(),
        motionScheme = MotionScheme.expressive(),
        content = content
    )
}
```

Pass `MotionScheme.expressive()` explicitly even though it is the documented default. One line, it
survives a default change, and it makes the intent greppable.

### The silent-fallback trap

A bare `MaterialTheme(colorScheme = ..., typography = ...) { }` — the 3-param overload every
pre-Expressive template emits — gives you **`MotionScheme.standard()`**. Nothing warns you. Every
`MaterialTheme.motionScheme` call still compiles and returns valid springs; they just never
overshoot. Symptom: "I used the expressive specs everywhere and it looks the same as before."

When expressive motion looks flat, check in order:

1. Root theme is `MaterialExpressiveTheme`, or `MaterialTheme` with an explicit
   `motionScheme = MotionScheme.expressive()`. A bare `MaterialTheme` is standard.
2. No nested 3-param `MaterialTheme(...)` further down (previews, bottom sheets, per-screen theme
   overrides) resetting the subtree.
3. You are animating something **spatial**. Effects specs are identical in both schemes — alpha and
   color will never show a difference. See §5.

---

## 5. Exact spring constants

### Expressive — `[SRC] tokens/ExpressiveMotionTokens.kt`

| Token | Value |
| --- | --- |
| `SpringDefaultSpatialDamping` | `0.8f` |
| `SpringDefaultSpatialStiffness` | `380.0f` |
| `SpringDefaultEffectsDamping` | `1.0f` |
| `SpringDefaultEffectsStiffness` | `1600.0f` |
| `SpringFastSpatialDamping` | `0.6f` |
| `SpringFastSpatialStiffness` | `800.0f` |
| `SpringFastEffectsDamping` | `1.0f` |
| `SpringFastEffectsStiffness` | `3800.0f` |
| `SpringSlowSpatialDamping` | `0.8f` |
| `SpringSlowSpatialStiffness` | `200.0f` |
| `SpringSlowEffectsDamping` | `1.0f` |
| `SpringSlowEffectsStiffness` | `800.0f` |

### Standard — `[SRC] tokens/StandardMotionTokens.kt`

| Spec | Damping | Stiffness |
| --- | --- | --- |
| Default Spatial | `0.9f` | `700.0f` |
| Default Effects | `1.0f` | `1600.0f` |
| Fast Spatial | `0.9f` | `1400.0f` |
| Fast Effects | `1.0f` | `3800.0f` |
| Slow Spatial | `0.9f` | `300.0f` |
| Slow Effects | `1.0f` | `800.0f` |

Both impls construct `spring(dampingRatio = …, stiffness = …)` and return it as
`FiniteAnimationSpec<T>` from `StandardMotionSchemeImpl` / `ExpressiveMotionSchemeImpl`.

### Side by side — the only thing that actually differs

| Spec | Expressive | Standard | Difference |
| --- | --- | --- | --- |
| Default spatial | `0.8 / 380` | `0.9 / 700` | underdamped **and** ~half the stiffness |
| Fast spatial | `0.6 / 800` | `0.9 / 1400` | much more underdamped, softer |
| Slow spatial | `0.8 / 200` | `0.9 / 300` | underdamped, softer |
| Default effects | `1.0 / 1600` | `1.0 / 1600` | **identical** |
| Fast effects | `1.0 / 3800` | `1.0 / 3800` | **identical** |
| Slow effects | `1.0 / 800` | `1.0 / 800` | **identical** |

**Read this table before you argue about motion feel.** All six effects specs are critically damped
(`dampingRatio = 1.0f`) and share identical stiffness between the two schemes. Effects motion is
intentionally scheme-invariant: fades and color changes must never bounce, in either scheme.

So the entire mechanical difference between `MotionScheme.expressive()` and `MotionScheme.standard()`
is: **spatial springs become underdamped (visible overshoot) and softer.** Nothing else changes.

Consequences you can act on:

- A screen that animates only alpha and color will not change at all when you switch schemes. Never
  ship a "switch to expressive" change and claim a motion improvement without a spatial animation in
  the diff.
- A reduced-motion mode that swaps `expressive()` → `standard()` removes overshoot only. It does not
  shorten or remove fades. To kill motion you need `snap()`, not a scheme swap. See §10.
- `0.6` damping on fast-spatial is the bounciest value in the system — the spec behind press morphs
  and button-group compression, where overshoot is felt through the finger and reads as
  responsiveness. Do not reuse it for anything large.

### Approximate settle times `[DERIVED]`

Compose springs use unit mass: ω = √stiffness rad/s, settling ≈ 4/(ζω). Order-of-magnitude only —
**not** published Material values; real termination depends on `visibilityThreshold`.

Note the ζ=1 special case: 4/(ζω) is the underdamped envelope and under-estimates at critical damping,
where the response decays as `(1 + ωt)e^(−ωt)`; solving that to a ~1% threshold gives **≈5.8/ω**. So
the critically-damped rows (all six effects specs, ζ=1) below use 5.8/ω and the underdamped spatial
rows use 4/(ζω) — that is what makes the table internally consistent across the two families.

| Spec | Expressive | Standard |
| --- | --- | --- |
| Default spatial | ~260 ms | ~180 ms |
| Fast spatial | ~235 ms (visible bounce) | ~120 ms |
| Slow spatial | ~350 ms | ~260 ms |
| Default / Fast / Slow effects | ~145 / ~95 / ~205 ms | same |

Expressive fast-spatial does not *settle* faster than default-spatial — it *departs* faster and
then rings. The ringing is the point.

---

## 6. Spatial vs effects

`[CANON]` The central taxonomy:

- **Spatial** — position, rotation, size, rounded corners. This spring "overshoots the final value
  and bounces into place."
- **Effects** — color and opacity. "there shouldn't be any overshoot."

The rule: **bounce is for things that move; bounce is never for things that fade or recolor.**

### Why the wrong family is a bug, not a preference

An underdamped spring overshoots its target. For a `Dp` or an offset that is the point. For an effects
property it is a defect — Material's `MotionScheme` KDoc states outright that effects motion
"shouldn't have any overshoot." The concrete failures:

- **Color** — an overshooting interpolation passes *through* a value outside both endpoints, so the
  channels interpolate out of gamut and the animation lands wrong: a primary→tertiary crossfade
  visibly detours through a hue that belongs to neither scheme role.
- **Elevation / shadow** — the overshoot is directly observable, the shadow pumping past its resting
  depth and back, which reads as a rendering error.
- **In general** — the bounce reads as a *flicker* rather than as movement. Bounce communicates
  "arrived" only when something actually moved.

**Alpha is the case to state precisely.** `graphicsLayer.alpha` is clamped to `[0f, 1f]` by the render
node, so an overshooting alpha does **not** render past full opacity and there is no visible spike to
point at. The damage is that the value rings: the fade settles later than the spec advertises and is
out of step with every other fade in the app. Do not claim a visible flash on alpha — claim the timing
and consistency argument, which is true and checkable.

That is why the split exists. Getting it wrong produces artifacts users notice and cannot name.

### Property → family

| Animating | Family | Typical spec |
| --- | --- | --- |
| `Dp` size / width / height | Spatial | `defaultSpatialSpec()` |
| `IntOffset` / `Offset` / translation | Spatial | `defaultSpatialSpec()` |
| Scale (`scaleX`/`scaleY`, `scaleIn`/`scaleOut`) | Spatial | `defaultSpatialSpec()` |
| Rotation | Spatial | `defaultSpatialSpec()` |
| Corner radius / shape morph progress | Spatial | `fastSpatialSpec()` |
| Layout bounds (`animateContentSize`, `animateBounds`, `BoundsTransform`) | Spatial | `defaultSpatialSpec()` |
| `expandVertically` / `shrinkVertically` / `expand*`/`shrink*` | Spatial | `defaultSpatialSpec()` |
| `slideIn*` / `slideOut*` | Spatial | `defaultSpatialSpec()` or `slowSpatialSpec()` |
| Item placement in a lazy list | Spatial | `defaultSpatialSpec()` |
| Scroll offset / fling target | Spatial | `defaultSpatialSpec()` |
| Alpha, `fadeIn` / `fadeOut` | **Effects** | `defaultEffectsSpec()` / `fastEffectsSpec()` |
| `Color` (background, content, tint, container) | **Effects** | `defaultEffectsSpec()` / `slowEffectsSpec()` |
| Elevation (`Dp`, but reads as a lighting change) | **Effects** | `fastEffectsSpec()` |
| Blur radius / scrim opacity | **Effects** | `defaultEffectsSpec()` |
| Cross-fade between two contents | **Effects** | `defaultEffectsSpec()` |

Elevation is the one judgement call `[DERIVED]`: it is a `Dp`, but users read it as a lighting
change, and an overshooting shadow looks like a rendering error. Use effects.

### The combined-transition rule

`AnimatedVisibility` and `AnimatedContent` almost always combine both families in one call. Give
each part its own spec — the single most copied line in the corpus:

```kotlin
enter = fadeIn(motionScheme.defaultEffectsSpec()) +
        scaleIn(motionScheme.defaultSpatialSpec(), 4f),
exit  = fadeOut(motionScheme.defaultEffectsSpec()) +
        scaleOut(motionScheme.defaultSpatialSpec(), 4f)
```

`[CORPUS Tomato]` `.../ui/timerScreen/TimerScreen.kt:322-328`

---

## 7. Fast / Default / Slow

Canonical assignment `[CANON]`:

| Tier | Canonical guidance | Concrete examples |
| --- | --- | --- |
| **Fast** | "Small components like switches and buttons" | press/scale feedback, toggle thumb travel, chip selection, icon swap, corner-radius morph on press, elevation change |
| **Default** | "Medium-scale animations like bottom sheets and navigation rails" | FAB menu open/close, sheet settle, list item reorder, card expand, top-app-bar title swap, slider settle after release |
| **Slow** | "Full-screen animations and content refreshes" | bottom bar slide in/out, full-screen scrim + surface reveal, mode change that recolors the whole screen, onboarding page transitions |

Governing heuristic: **faster for smaller elements, slower for larger ones.** `[CANON]`

Corpus anchors for the Slow tier: Tomato slides the whole bottom bar with `slowSpatialSpec()`
(`.../ui/AppScreen.kt:177`), and crossfades the entire screen palette on a focus↔break switch with
four simultaneous `slowEffectsSpec()` color animations so the recolor reads as one deliberate event
rather than four twitches (`.../timerScreen/TimerScreen.kt:180-193`).

Device scaling `[CANON]`: "Token values adjust per device type," so a spec feels fast "in the context
of the device." Do not hard-code durations to compensate. Coverage `[CANON]`: 21 Compose Material
components use the physics system by default.

---

## 8. Migrating `tween()` to a motion scheme spec

Legacy Compose animation code is duration + easing. Expressive is springs. Springs handle
"gestures, interruptions, and retargeting animations seamlessly" `[CANON]` — a spring can be
redirected mid-flight from its current position *and velocity*, whereas a duration/easing curve
must restart or cross-fade. That is the reason to migrate, not aesthetics.

Migration table `[DERIVED]` — map by *what is animating* first, then sanity-check against the old
duration:

| Legacy | Replace with | Notes |
| --- | --- | --- |
| `tween(100)` on a color/alpha | `fastEffectsSpec()` | |
| `tween(150–200)` on a color/alpha | `defaultEffectsSpec()` | |
| `tween(300–400)` on a color/alpha | `slowEffectsSpec()` | |
| `tween(100–150)` on size/offset/scale | `fastSpatialSpec()` | |
| `tween(200–300)` on size/offset/scale | `defaultSpatialSpec()` | the most common substitution |
| `tween(350+)` on size/offset/scale | `slowSpatialSpec()` | |
| `tween(n, easing = FastOutSlowInEasing)` | matching spatial spec | the standard "moves" curve — always spatial |
| `tween(n, easing = LinearOutSlowInEasing)` (enter) | matching spatial spec | overshoot replaces the decelerate tail |
| `tween(n, easing = FastOutLinearInEasing)` (exit) | matching **effects** spec, or `fastSpatialSpec()` | exits should not bounce — see §9 |
| `spring(dampingRatio = Spring.DampingRatioNoBouncy)` | `defaultEffectsSpec()` if non-spatial, else `defaultSpatialSpec()` | |
| `spring(dampingRatio = Spring.DampingRatioMediumBouncy)` | `fastSpatialSpec()` | 0.6 is the nearest damping match; `defaultSpatialSpec()` (0.8) is calmer |
| `snap()` | keep | still correct for "no animation" and gesture tracking; see §10 |
| `keyframes { }` / `infiniteRepeatable { }` | keep | no motion-scheme equivalent; springs cannot express a multi-stop path or repeat (specs are `FiniteAnimationSpec`) |
| spec on a determinate progress indicator | keep `tween` | `WavyProgressIndicatorDefaults.ProgressAnimationSpec` is `tween(DurationLong2, EasingLinearCubicBezier)` `[SRC]` — progress must be linear |

**Do not migrate**: infinite/looping animations, keyframed choreography, and anything whose timing
is externally synchronized (audio position, a countdown, a video scrubber). Springs have no duration
you can match to an external clock.

Mechanical steps per file: hoist `val motionScheme = motionScheme`; replace each
`animationSpec = tween(...)` per the table; split combined enter/exit transitions so the fade half
gets an effects spec; then grep for leftover `FastOutSlowInEasing` imports and for `label = "..."`
(those are `animate*AsState` calls and are usually the ones missed).

---

## 9. Design-level motion guidance

### Overshoot rules `[CANON]`

1. Overshoot **only** on spatial properties (position, rotation, size, corner radius).
2. **No overshoot** on color or opacity.
3. Reserve the Expressive scheme for "hero moments and key interactions"; use Standard for
   utilitarian surfaces.
4. Match tier to element scale: Fast for small controls, Slow for full-screen.

### Overshoot for arrivals, not departures `[DERIVED, consistent with CANON]`

An element that bounces as it *enters* reads as arriving with momentum. One that bounces as it
*leaves* reads as a glitch — the eye tracks it back toward the screen after it has committed to
leaving. So: enter transitions get a spatial spec with overshoot welcome; exit transitions should be
carried by a fade (effects spec), with the spatial part omitted or given `fastSpatialSpec()` so it
is gone before the ring is visible. Symmetric enter/exit springs are what the corpus actually ships
(Tomato uses the same `defaultSpatialSpec()` on both halves of `AnimatedVisibility`) and that is
acceptable — but if something reads as glitchy on dismissal, this is the first thing to change.

### The eight principles of good motion `[CANON, via secondary summary]`

1. **Accessibility** — respect user platform settings.
2. **Consistency** — "Having certain rules for movement creates a sense of unity."
3. **Stable layouts** — prevent layout shifts during loading.
4. **Avoid jump cuts** — no instant screen switches with no motion.
5. **Spatial coherence** — maintain clear structural relationships.
6. **Unified directionality** — "group the elements and move them along an axis."
7. **Clean fading** — prevent content overlap during transitions.
8. **Functional simplicity** — motion assists user tasks rather than distracting.

### When motion carries meaning

Motion is doing work when it answers one of these:

- *Where did this come from / where did it go?* → shared bounds, container transform, directional
  slide. Highest-value motion in an app, and the most commonly skipped.
- *What changed?* → the changed element moves or resizes; nothing else does.
- *Is the system responding to me?* → press feedback, button-group compression. Must be Fast.
- *Is this the same object I was just looking at?* → shared element continuity across a nav change.

Motion is decoration — and a liability — when it answers none of them: staggered entrance animations
on a list the user has already seen, an icon that always bounces, a hero animation on a settings row.

### Over-animation anti-patterns `[CANON / GOOGLE]`

- The overriding rule: *"Don't compromise your product's core functionality for visual flourishes.
  **No amount of emotion can compensate for a lack of clarity.**"*
- **Budget hero moments**: "Stick to one or two hero moments in your product; too many moments can
  be overwhelming or distracting." Expressive springs on every element is the motion equivalent of
  expressive-everywhere — expression is *relational*, and uniform application annihilates emphasis.
- Don't allow layout shift during loading; don't use jump cuts; don't break spatial coherence; don't
  let content overlap during cross-fades; move grouped elements along a shared axis.
- **A slow bouncy spring on a switch feels broken.** Tier mismatch is the most common failure.

### Time-based limits `[GOOGLE — W3C-derived]`

Content that "moves, scrolls, or blinks automatically" must be "paused, stopped, or hidden if it
lasts more than five seconds"; flashing limited to "three times in a one-second period"; "avoid
flashing large central regions of the screen." These apply to looping/ambient motion, which the
motion scheme does not cover (its specs are finite). If you hand-roll an `infiniteRepeatable`, these
limits are yours to enforce.

---

## 10. Reduced motion and accessibility

Material's first motion principle is **accessibility: respect user platform settings.** `[CANON]`

### Detecting it on Android

There is **no** `LocalReducedMotion` and no material3 API for this. `[UNVERIFIED — no Material
substitution recipe for Android reduced-motion was retrievable; the approach below is what shipping
apps do]` Read the three global animation scales — `Settings.Global.ANIMATOR_DURATION_SCALE`,
`TRANSITION_ANIMATION_SCALE`, `WINDOW_ANIMATION_SCALE` — plus
`ValueAnimator.areAnimatorsEnabled()`. Any scale at `0f`, or `areAnimatorsEnabled()` false, means
the user asked for animations off (Settings → Accessibility → Remove animations, or Developer
options). These change at runtime, so observe with a `ContentObserver` rather than reading once.

### The recommended pattern — LastChat's `MotionPolicy`

`[CORPUS LastChat]`
`/root/work/repos/LastChat/app/src/main/java/me/rerere/rikkahub/ui/motion/MotionPolicy.kt:30-132`
— verbatim:

```kotlin
private const val TOP_LEVEL_FADE_IN_DURATION_MS = 120
private const val TOP_LEVEL_FADE_OUT_DURATION_MS = 90
private const val FORWARD_BACK_SLIDE_DURATION_MS = 200
private const val FORWARD_BACK_FADE_IN_DURATION_MS = 150
private const val FORWARD_BACK_FADE_OUT_DURATION_MS = 100

internal val CHAT_ROUTE_BASE: String = Screen.Chat.serializer().descriptor.serialName
internal val MENU_ROUTE: String = Screen.Menu.serializer().descriptor.serialName
internal val SETTING_ROUTE: String = Screen.Setting.serializer().descriptor.serialName
internal val SETTING_DISPLAY_ROUTE: String = Screen.SettingDisplay.serializer().descriptor.serialName

@Stable
data class MotionPolicy(
    val reduceMotion: Boolean
)

val LocalMotionPolicy = compositionLocalOf { MotionPolicy(reduceMotion = false) }

@Composable
fun rememberSystemMotionPolicy(): MotionPolicy {
    val context = LocalContext.current
    val resolver = context.contentResolver
    var scales by remember(resolver) { mutableStateOf(readSystemAnimationScales(resolver)) }

    DisposableEffect(resolver) {
        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) {
                scales = readSystemAnimationScales(resolver)
            }
        }

        listOf(
            Settings.Global.getUriFor(Settings.Global.ANIMATOR_DURATION_SCALE),
            Settings.Global.getUriFor(Settings.Global.TRANSITION_ANIMATION_SCALE),
            Settings.Global.getUriFor(Settings.Global.WINDOW_ANIMATION_SCALE)
        ).forEach { uri ->
            resolver.registerContentObserver(uri, false, observer)
        }

        onDispose {
            resolver.unregisterContentObserver(observer)
        }
    }

    val reduceMotion = remember(scales) {
        !ValueAnimator.areAnimatorsEnabled() ||
            scales.animatorScale <= 0f ||
            scales.transitionScale <= 0f ||
            scales.windowScale <= 0f
    }

    return remember(reduceMotion) { MotionPolicy(reduceMotion = reduceMotion) }
}

internal fun shouldUseTopLevelFade(initialRoute: String?, targetRoute: String?): Boolean {
    return isTopLevelRootRoute(initialRoute) && isTopLevelRootRoute(targetRoute)
}

internal fun AnimatedContentTransitionScope<NavBackStackEntry>.rootEnterTransition(
    motionPolicy: MotionPolicy
): EnterTransition {
    return if (
        motionPolicy.reduceMotion ||
        shouldUseTopLevelFade(initialState.destination.route, targetState.destination.route)
    ) {
        fadeIn(animationSpec = tween(TOP_LEVEL_FADE_IN_DURATION_MS))
    } else {
        slideInHorizontally(
            animationSpec = tween(
                durationMillis = FORWARD_BACK_SLIDE_DURATION_MS,
                easing = FastOutSlowInEasing
            )
        ) { it / 2 } + fadeIn(animationSpec = tween(FORWARD_BACK_FADE_IN_DURATION_MS))
    }
}

```

(`rootExitTransition` mirrors the above with `fadeOut` / `slideOutHorizontally { -it / 4 }`;
`rootPopEnterTransition` / `rootPopExitTransition` follow at lines 134-170 with mirrored offsets.
`readSystemAnimationScales` is a private helper in the same file returning the three floats.)

Provided at the app root alongside the shared transition scope
(`.../rikkahub/RouteActivity.kt:660-678`):

```kotlin
val motionPolicy = rememberSystemMotionPolicy()
SharedTransitionLayout {
    CompositionLocalProvider(
        LocalSharedTransitionScope provides this,
        LocalMotionPolicy provides motionPolicy,
        // ...
    ) {
```

**Note honestly**: LastChat never touches `MaterialTheme.motionScheme` at all — it uses hand-rolled
`tween`s branched on `reduceMotion`. The *policy plumbing* is what to copy; the `tween`s are not.
Combine them: keep `MotionPolicy` + the CompositionLocal, branch between motion-scheme specs and
reduced substitutes.

### Recommended degradation ladder `[DERIVED]`

```kotlin
@Composable
fun spatial(): FiniteAnimationSpec<Float> =
    if (LocalMotionPolicy.current.reduceMotion) snap()
    else MaterialTheme.motionScheme.defaultSpatialSpec()

@Composable
fun effects(): FiniteAnimationSpec<Float> =
    if (LocalMotionPolicy.current.reduceMotion) MaterialTheme.motionScheme.fastEffectsSpec()
    else MaterialTheme.motionScheme.defaultEffectsSpec()
```

The ladder, in order of aggressiveness:

1. **Swap the scheme** (`standard()` instead of `expressive()`). Removes overshoot only, does
   nothing for fades — insufficient alone for a user who zeroed animation scale.
2. **Substitute spatial with a cross-fade.** Slide/scale/expand → `fadeIn`/`fadeOut` on an effects
   spec. Preserves the sense of change without translation. This is what LastChat does
   (`shouldUseTopLevelFade`).
3. **`snap()` for spatial, keep short effects.** Position/size changes instant, ~100 ms fade remains
   so state changes stay legible. Good default for `reduceMotion == true`.
4. **`snap()` for everything.** Only if step 3 still causes discomfort.

Do **not** disable shared-element transitions and leave the destination appearing with no transition
at all — that is a jump cut, its own accessibility problem (principle 4). Degrade to a fade.

### Other accessibility constraints that interact with motion

- **Touch targets ≥ 48×48 dp** (~9 mm), pointer targets ≥ 44×44 dp, separated by ≥ 8 dp `[GOOGLE]`.
  A press-scale animation must not shrink the *touch target* — animate a `graphicsLayer` scale,
  which does not change layout bounds, rather than the size.
- **Contrast**: small text ≥ 4.5:1, large text ≥ 3:1; all Material role pairs guarantee ≥ 3:1
  `[GOOGLE]`. A color animation must be legible at *every* intermediate frame — another reason
  effects springs must not overshoot.
- **Accessibility overrides are baked in**: floating toolbars stay expanded and disable
  `scrollBehavior`, and `FlexibleBottomAppBar` disables `scrollBehavior`, whenever an accessibility
  service is active `[SRC]`. Don't fight it.

---

## 11. Which built-in components read the motion scheme

`[CANON]` states 21 Compose Material components use the physics system by default. Confirmed from
release notes and API signatures — a floor, not the full 21.

| Component / API | How it uses motion | Confidence |
| --- | --- | --- |
| `BottomSheet` | Respects `MaterialTheme.motionScheme` **as of 1.5.0-alpha16** (2026-03-25); fixed motion before that | `[SRC]` release note |
| `Modifier.animateFloatingActionButton` | `scaleAnimationSpec` / `alphaAnimationSpec` nullable; **`null` uses the theme's motion scheme** | `[SRC]` signature + doc |
| `FloatingActionButtonMenu` / `ToggleFloatingActionButton` | Open/close choreography, `ToggleFloatingActionButtonDefaults.animateIcon` | `[SRC]` |
| `Horizontal`/`VerticalFloatingToolbar` | `animationSpec: FiniteAnimationSpec<Float> = FloatingToolbarDefaults.animationSpec()` | `[SRC]` signature |
| `ButtonGroup` (`Modifier.animateWidth`) | Press-compression of neighbours | `[SRC]` |
| `ToggleButton` / `IconButton` with `shapes = ...Defaults.shapes()` | Shape morph on press/check | `[SRC]` |
| Flexible top app bars (`snapAnimationSpec` / `flingAnimationSpec`) | Collapse/expand settle — these are `tween`-based `MotionTokens`, **not necessarily scheme-derived** | `[UNVERIFIED]` |
| `LoadingIndicator`, `Slider`, `ShortNavigationBar`, `WideNavigationRail` | Shape morph / settle / indicator travel | `[UNVERIFIED]` |
| Wavy progress indicators | `WavyProgressIndicatorDefaults.ProgressAnimationSpec` is an explicit `tween(DurationLong2, EasingLinearCubicBezier)` — **not** scheme-driven | `[SRC]` — confirmed exception |

Takeaways: never pass an explicit spec to `Modifier.animateFloatingActionButton` unless you mean to
deviate — `null` inherits the theme. Progress indicators are deliberately linear; do not "fix" them
with a spring. If a built-in's motion looks wrong, check the theme (§4) before overriding it.

---

## 12. Performance

Cost is dominated by **which phase the animation invalidates**, not by the spec.

| Phase | Cost | Examples |
| --- | --- | --- |
| **Composition** | Highest — re-runs composable code | reading an animated `State<T>` in a composable body; `animateContentSize`; `AnimatedContent` |
| **Layout** | High — re-measures the subtree | `Modifier.size`, `padding`, `offset(Dp)`, `weight`, `fillMaxWidth(fraction)`, `Arrangement.spacedBy(animatedDp)` |
| **Draw** | Low — one layer re-render | `graphicsLayer { }`, `offset { IntOffset }` (lambda overload), `drawBehind`, `alpha`, `rotate`, `scale` |

Rules:

1. **Use `Modifier.graphicsLayer { }` for alpha, scale, rotation and translation.** Draw-phase only.
   LastChat's `TabAnimation` applies an `Animatable` scale/offset exactly this way
   (`.../ui/components/ui/TabAnimation.kt:97-101`).
2. **Prefer the lambda overloads.** `Modifier.offset { IntOffset(x, y) }` defers the read to layout;
   `Modifier.offset(x.dp, y.dp)` reads it in composition and invalidates everything above. Same for
   `graphicsLayer { alpha = ... }` vs `Modifier.alpha(animatedAlpha)`. Tomato's AOD positions the
   clock with `.offset { IntOffset(x, y) }` (`.../ui/AlwaysOnDisplay.kt:210`).
3. **Never animate a size when a scale will do.** Scaling in `graphicsLayer` does not re-measure and
   does not change the touch target (§10). Animating `Modifier.size` re-measures the subtree every
   frame and moves siblings.
4. **`animateContentSize` is a layout animation.** Correct for text expanding to more lines (Tomato:
   `.../settingsScreen/screens/AlarmSettings.kt:299`), expensive on a deep subtree. Never put it on
   a screen-level container.
5. **Pass lambdas, not values.** `progress: () -> Float` keeps the recomposition scope at the leaf.
   Tomato threads timer progress this way from the ViewModel to the `CircularProgressIndicator`.
6. **`Animatable` + `snapTo` during a gesture, `animateTo` on release.** A spring between finger and
   pixel adds latency. See the gesture recipe in `motion-recipes.md`.
7. **`derivedStateOf` for computed animation inputs.** LastChat computes swipe `unlockProgress` and
   the interpolated corner shape inside `derivedStateOf` so downstream recomposition fires only when
   the derived value changes (`.../ui/components/ui/PhysicsSwipeToDelete.kt:153-204`).
8. **Shared element transitions render in an overlay.** `renderInOverlayDuringTransition = true`
   (default) lifts the element out of the layout during the flight — correct and cheap. Disabling it
   forces in-place drawing and clipping against ancestors.

---

## 13. Version and opt-in notes `[SRC]`

- `MotionScheme` + `MaterialTheme.motionScheme` needed
  `@OptIn(ExperimentalMaterial3ExpressiveApi::class)` on material3 1.4.x. **Graduated from
  experimental in 1.5.0-alpha15** (2026-02-25). On 1.4.0 stable, expect a file-level `@OptIn` for
  anything touching motion.
- `MaterialExpressiveTheme` / `expressiveLightColorScheme` promoted in 1.5.0-alpha18.
- `MaterialTheme` collapsed to a single composition local in 1.5.0-alpha15 — use
  `MaterialTheme.LocalMaterialTheme.current`, not `LocalColorScheme` / `LocalTypography`.
- `BottomSheet` began respecting the motion scheme in 1.5.0-alpha16.

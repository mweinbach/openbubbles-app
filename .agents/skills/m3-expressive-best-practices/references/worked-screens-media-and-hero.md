# Worked Screens — Media & Hero Surfaces

Four complete screens where M3 Expressive earns its keep: a **media player**, a **timer/progress
hero**, an **onboarding/empty state**, and the **loading/skeleton** family. Each is a screen you can
copy: state shape, complete composables, annotated expressive decisions, an accessibility block, and
an explicit list of what was left deliberately calm.

**Provenance `[API-alpha26]`.** Every `material3` / `material3-adaptive` composable name and
`*Defaults` member name in this file was machine-checked against
`compose/material3/material3/api/current.txt` and `compose/material3/adaptive/*/api/current.txt`
at the alpha26 / 1.3.0 SHA. Non-material3 APIs (foundation, ui, animation, navigation3,
third-party) were **not** checked; anything that could not be confirmed is flagged `[UNVERIFIED]`
inline at its use site.

Target artifacts: `material3 1.5.0-alpha26`, `material3-adaptive 1.3.0`, `graphics-shapes 1.1.0`,
`compose-ui 1.12.0`.

## Provenance markers

- `[CORPUS <repo>: path]` — verbatim (or lightly condensed) from a shipping open-source app.
- `[ANDROIDX]` — signature or sample from the androidx tree / `material3/samples`.
- `[COMPOSED]` — assembled here from verified pieces. Every API call in a `[COMPOSED]` block appears
  in a `[CORPUS]` or `[ANDROIDX]` block elsewhere in this plugin; the *composition* is new. Compile
  it, don't trust it.
- `[UNVERIFIED]` — the name is real, the exact signature was not read from source. Do not paste blind.

## Opt-in policy used throughout this file

Most of the Expressive surface graduated between alpha19 and alpha26. Reflexively adding
`@OptIn(ExperimentalMaterial3ExpressiveApi::class)` is now the *more* common error. In this file the
Expressive opt-in appears only for:

| Needs `ExperimentalMaterial3ExpressiveApi` at alpha26 | Where |
| --- | --- |
| `LoadingIndicator`, `ContainedLoadingIndicator` | §4 |
| `MaterialShapes`, `RoundedPolygon.toShape()` / `.toPath()`, `Morph.toPath()` | §1.3, §3.1, §4.3 |
| `ToggleButton` **size** variants (XSmall/Medium/Large toggle buttons) | not used here |
| Menu APIs, `PullToRefreshDefaults.loadingIndicatorColor(...)` | not used here |

`ExperimentalMaterial3Api` is a *different* annotation and is still required for `ModalBottomSheet`,
`TimePickerDialog`, `AppBarWithSearch`, and pull-to-refresh. It is used where marked.

**`ButtonGroup` is the one contested case.** The alpha22 release note says its APIs were promoted;
`ButtonGroupSamples.kt` at androidx-main still carries the Expressive opt-in. The `ButtonGroup` call
sites below are written *without* it. If your pin rejects them, add
`@OptIn(ExperimentalMaterial3ExpressiveApi::class)` — a redundant opt-in costs a warning, a missing
one costs the build. See `${CLAUDE_PLUGIN_ROOT}/skills/m3-expressive-components/references/buttons.md` §5.

## Deep material lives elsewhere — do not duplicate it

| Topic | Read |
| --- | --- |
| Slider / wavy-slider API, four custom sliders | `${CLAUDE_PLUGIN_ROOT}/skills/m3-expressive-components/references/sliders-and-inputs.md` |
| `LoadingIndicator`, wavy progress, pull-to-refresh, shimmer | `${CLAUDE_PLUGIN_ROOT}/skills/m3-expressive-components/references/progress-and-loading.md` |
| Button size scale, `ButtonGroup`, `SplitButtonLayout`, toggle migrations | `${CLAUDE_PLUGIN_ROOT}/skills/m3-expressive-components/references/buttons.md` |
| `MaterialShapes` catalog, `Morph`, per-frame morph shapes | `${CLAUDE_PLUGIN_ROOT}/skills/m3-expressive-shapes/references/{shapes-catalog,morph-recipes}.md` |
| Spring tiers, shared bounds, predictive back | `${CLAUDE_PLUGIN_ROOT}/skills/m3-expressive-motion/references/{motion-scheme,motion-recipes}.md` |
| Seeded color, materialkolor, AMOLED | `${CLAUDE_PLUGIN_ROOT}/skills/m3-expressive-theming/references/color.md` |
| Size classes, `currentWindowAdaptiveInfoV2()` | `${CLAUDE_PLUGIN_ROOT}/skills/m3-adaptive/references/window-size-classes.md` |

---

# 1. Media player screen — the flagship expressive surface

## 1.0 What this demonstrates

| Expressive tactic | Where it lands here | Why it is correct here |
| --- | --- | --- |
| **Shape as state** | Album art morphs `Circle` ⇄ `Cookie9Sided` with playback | The art is the largest element on screen; the morph *is* the play/pause feedback |
| **Wave as state** | Seek bar wave amplitude tracks `isPlaying` and flattens while scrubbing | A wave that keeps waving while paused is a lie |
| **Hero button size** | One Large (96dp) play/pause; everything else is Small | Material's budget is one or two hero moments — this screen spends one |
| **Connected group** | Shuffle / Repeat as a 2dp-gap connected `ToggleButton` pair | They are alternatives in one set: playback modifiers |
| **Seeded color** | Whole scheme re-tints from album art via materialkolor | The screen's job is to present *this* album |
| **Container transform** | Mini-player expands to full player via `sharedBounds` | Same content, two sizes — the canonical shared-element case |

**What was deliberately left calm:** the track title/artist block (plain `titleLarge` /
`bodyMedium`, no emphasized styles, no animation); the top app bar (plain `TopAppBar`, not
`LargeFlexibleTopAppBar` — the *content* is the hero, so the bar must not compete); the skip
buttons (default `IconButton`, no shape morph, no size bump); the queue sheet rows (plain
`ListItem`, rounded-rect, no polygons). Expression is relational: the morphing art and the wavy
track read as emphatic only because eleven other things on this screen do not move.

## 1.1 State shape

`[COMPOSED]`

```kotlin
package com.example.player

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color

@Immutable
data class TrackUi(
    val id: String,
    val title: String,
    val artist: String,
    val artworkUrl: String?,
    val durationMs: Long,
)

enum class RepeatMode { Off, All, One }

@Immutable
data class PlayerUiState(
    val track: TrackUi? = null,
    val positionMs: Long = 0L,
    val isPlaying: Boolean = false,
    val isBuffering: Boolean = false,
    val shuffle: Boolean = false,
    val repeat: RepeatMode = RepeatMode.Off,
    val queue: List<TrackUi> = emptyList(),
    val queueIndex: Int = 0,
    /** Seed extracted from artwork; null until the bitmap has been decoded. */
    val seedColor: Color? = null,
) {
    val durationMs: Long get() = track?.durationMs ?: 0L
    val progress: Float
        get() = if (durationMs > 0L) (positionMs.toFloat() / durationMs).coerceIn(0f, 1f) else 0f
}
```

Two rules the rest of the screen depends on:

1. **`positionMs` ticks; nothing else does.** Expose it so the *indicator* reads it through a lambda
   (`progress = { … }`), never as a value captured in a wide composable — otherwise the whole player
   recomposes at 10 Hz. `[CORPUS Tomato]` does this and it is the single biggest perf decision here.
2. **Scrubbing is a nullable local override, not state in the ViewModel.** While the finger is down
   the slider shows `scrubMs`; on release it seeks and drops back to `positionMs`. This is
   vivi-music's `sliderPosition: Long?` idiom
   (`[CORPUS vivi-music: .../ui/player/Player.kt]`) and it is the correct shape for *any* control
   over a value that also changes on its own.

## 1.2 The seek bar — a wavy slider that TalkBack can actually operate

### The problem with the corpus versions

vivi-music ships two wavy seek bars. Both are good-looking and **both are unusable with a screen
reader**:

- `WavySlider` `[CORPUS vivi-music: .../ui/component/WavySlider.kt]` — drives a real
  `LinearWavyProgressIndicator` but rebuilds gestures with `pointerInput` +
  `detectHorizontalDragGestures` on a plain `Box`.
- `SquigglySlider` `[CORPUS vivi-music: .../ui/component/SquigglySlider.kt]` — same, on a raw
  `Canvas`.

A `Box` with a `pointerInput` has **no `progressBarRangeInfo`, no `setProgress` action, no focus
target, and no keyboard handling**. TalkBack announces nothing and offers no way to change the
value; a hardware keyboard cannot move it; RTL is whatever your maths happens to do. Neither file
contains the string `semantics`.

### The fix: keep the real `Slider`, replace the `track` slot

`Slider`'s `track` parameter is `@Composable (SliderState) -> Unit`. Putting the wavy indicator
*there* keeps material3's gesture detector, semantics, focus, keyboard arrows, RTL mirroring and
minimum touch target, and costs nothing visually. This is the same move
`[CORPUS vivi-music: .../ui/component/PlayerSlider.kt]` makes for its slim track — applied to the
wavy indicator instead.

`[COMPOSED]`

```kotlin
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderColors
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.WavyProgressIndicatorDefaults
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.DpSize

/**
 * Expressive seek bar. Wavy while playing, flat while paused or scrubbing.
 * Gestures, semantics, keyboard and RTL come from the real Slider.
 */
@Composable
fun WavySeekBar(
    positionMs: Long,
    durationMs: Long,
    isPlaying: Boolean,
    onSeek: (Long) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    colors: SliderColors = SliderDefaults.colors(),
) {
    // Nullable drag override — see §1.1 rule 2.
    var scrubMs: Long? by remember { mutableStateOf(null) }

    val interactionSource = remember { MutableInteractionSource() }
    val isDragging by interactionSource.collectIsDraggedAsState()

    // Wave amplitude carries playback state. Effects spec: amplitude is not a spatial property,
    // so it must not overshoot.
    val amplitude by animateFloatAsState(
        targetValue = if (isPlaying && !isDragging) 1f else 0f,
        animationSpec = MaterialTheme.motionScheme.defaultEffectsSpec(),
        label = "waveAmplitude",
    )

    // Stroke is in PIXELS. wavelength / gapSize / stopSize are Dp. Do not mix them up.
    val density = LocalDensity.current
    val strokePx = with(density) { 4.dp.toPx() }
    val wavyStroke = remember(strokePx) { Stroke(width = strokePx, cap = StrokeCap.Round) }

    val safeDuration = durationMs.coerceAtLeast(1L)
    val shownMs = (scrubMs ?: positionMs).coerceIn(0L, safeDuration)

    Slider(
        value = shownMs.toFloat(),
        onValueChange = { scrubMs = it.toLong() },
        onValueChangeFinished = {
            scrubMs?.let(onSeek)
            scrubMs = null
        },
        valueRange = 0f..safeDuration.toFloat(),
        enabled = enabled,
        interactionSource = interactionSource,
        colors = colors,
        modifier = modifier
            .fillMaxWidth()
            // Replaces "37 percent" with "1:12 of 3:45" in TalkBack. See §1.9.
            .semantics {
                stateDescription =
                    "${formatDuration(shownMs)} of ${formatDuration(safeDuration)}"
            },
        thumb = {
            SliderDefaults.Thumb(
                interactionSource = interactionSource,
                colors = colors,
                enabled = enabled,
                // The Expressive thumb is a narrow capsule, not a circle.
                thumbSize = DpSize(width = 4.dp, height = 32.dp),
            )
        },
        track = { sliderState ->
            val range = sliderState.valueRange
            val span = (range.endInclusive - range.start).takeIf { it > 0f } ?: 1f
            val fraction = ((sliderState.value - range.start) / span).coerceIn(0f, 1f)

            LinearWavyProgressIndicator(
                progress = { fraction },
                modifier = Modifier.fillMaxWidth(),
                color = colors.activeTrackColor,
                trackColor = colors.inactiveTrackColor,
                stroke = wavyStroke,
                trackStroke = wavyStroke,
                // Carve a hole around the thumb so it sits IN the wave, not ON it.
                gapSize = 10.dp,
                stopSize = WavyProgressIndicatorDefaults.LinearTrackStopIndicatorSize,
                // Default indicatorAmplitude flattens below 10% and above 95%; a media bar must
                // be wavy end to end, so override it and gate only on playback state.
                amplitude = { p -> if (p > 0f) amplitude else 0f },
                wavelength = 20.dp,
            )
        },
    )
}

internal fun formatDuration(ms: Long): String {
    val totalSeconds = ms / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "%d:%02d".format(minutes, seconds)
}
```

**Why each choice:**

- `gapSize = 10.dp` ≈ half the thumb height plus breathing room. vivi-music computes it as
  `thumbRadius + 4.dp`; the principle is the same — the gap must be wide enough that the capsule
  thumb never overlaps a wave crest.
- `amplitude = { p -> if (p > 0f) amplitude else 0f }` is a **lambda** on the determinate overload.
  The indeterminate overload takes a plain `Float`. Passing `amplitude = 1f` to the determinate
  overload does not compile.
- `wavelength = 20.dp` on a ~4dp stroke. Rule of thumb: wavelength ≈ 5× stroke for a bar, ≈ ring/5
  for a ring.
- The wave **flattens while dragging** as well as while paused. Motion gets out of the way of direct
  manipulation — vivi-music's `SquigglySlider` encodes the same rule as
  `shouldFlatten = !isPlaying || isDragging`.

### If you genuinely must keep a `pointerInput` implementation

Sometimes you need a wave the platform indicator cannot express (per-pixel amplitude ramp, custom
caps). Then you owe it the semantics the real `Slider` would have given you.

`[COMPOSED, semantics API shape canonical — compile-check against your Compose UI version]`

```kotlin
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.setProgress

private fun Modifier.seekBarSemantics(
    valueMs: Long,
    durationMs: Long,
    enabled: Boolean,
    onSeek: (Long) -> Unit,
): Modifier = this
    .focusable(enabled)                    // the real Slider is focusable; a Box is not
    .semantics {
        if (!enabled) disabled()
        contentDescription = "Seek"
        stateDescription = "${formatDuration(valueMs)} of ${formatDuration(durationMs)}"
        progressBarRangeInfo = ProgressBarRangeInfo(
            current = valueMs.toFloat(),
            range = 0f..durationMs.coerceAtLeast(1L).toFloat(),
        )
        setProgress { target ->
            onSeek(target.toLong())
            true
        }
    }
```

That restores announcement and the TalkBack "increase/decrease" actions. It does **not** restore
keyboard arrow handling or RTL mirroring — you must add those yourself. Which is the argument for
the `track`-slot version.

## 1.3 Album art — a `MaterialShapes` treatment that carries state

The art morphs between a circle (paused) and a nine-lobed cookie (playing). `Morph` has no
`toShape()`, so you write the `Shape`. `Morph.toPath` is deliberately **not** `@Composable` so it can
be called inside `createOutline`.

`[COMPOSED — structure from `${CLAUDE_PLUGIN_ROOT}/skills/m3-expressive-shapes/references/morph-recipes.md` §2.2]`

```kotlin
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialShapes
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Matrix          // androidx.compose.ui.graphics — NOT android.graphics
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.graphics.shapes.Morph
import androidx.graphics.shapes.RoundedPolygon

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
private class MorphShape(
    private val morph: Morph,
    private val progress: Float,
    private val startAngle: Int = 0,
) : Shape {
    override fun createOutline(
        size: Size,
        layoutDirection: LayoutDirection,
        density: Density,
    ): Outline {
        // toPath is NOT @Composable — that is why this works here.
        val path = morph.toPath(progress = progress, startAngle = startAngle)
        path.transform(Matrix().apply { scale(size.width, size.height) })
        return Outline.Generic(path)
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun rememberPlaybackArtShape(isPlaying: Boolean): Shape {
    val calm: RoundedPolygon = MaterialShapes.Circle
    val lively: RoundedPolygon = MaterialShapes.Cookie9Sided

    // Key the Morph on the POLYGONS, never on progress. Rebuilding it per frame is the
    // classic morph-stutter bug: Morph() computes the feature mapping at construction.
    val morph = remember(calm, lively) { Morph(calm, lively) }

    val progress by animateFloatAsState(
        targetValue = if (isPlaying) 1f else 0f,
        // Effects spec on purpose: a bouncy spatial spring overshoots past 1f and Morph
        // extrapolates, which can self-intersect at the peak.
        animationSpec = MaterialTheme.motionScheme.defaultEffectsSpec(),
        label = "artMorph",
    )

    return remember(morph, progress) { MorphShape(morph, progress.coerceIn(0f, 1f)) }
}
```

Call site:

```kotlin
@Composable
fun AlbumArt(
    artworkUrl: String?,
    isPlaying: Boolean,
    modifier: Modifier = Modifier,
) {
    val shape = rememberPlaybackArtShape(isPlaying)
    Box(
        modifier = modifier
            .aspectRatio(1f)                 // Morph output is a unit box — keep the slot square
            .clip(shape),
    ) {
        AsyncImage(
            model = artworkUrl,
            contentDescription = null,       // the track title beside it carries the meaning
            contentScale = ContentScale.Crop, // mandatory for any polygon-clipped image
            modifier = Modifier.fillMaxSize(),
        )
    }
}
```

**Pitfalls, all load-bearing:**

- `Modifier.aspectRatio(1f)` is not optional. `MaterialShapes` values are normalized to a unit box;
  in a non-square slot `toShape()`/`MorphShape` stretch, and lobes read as ovals.
- `Cookie9Sided` "reads down to" ~48dp. This treatment is for the full player's 280–320dp art. The
  mini-player art (§1.7) uses `RoundedCornerShape` — do not reuse a nine-lobed cookie at 48dp.
- `Circle` maps cleanly to almost every `MaterialShapes` polygon, which is why it is the calm end of
  the morph. `Cookie12Sided ↔ Triangle` would kink mid-flight.
- **Cheaper alternative if this is too much:** skip the morph, clip statically to
  `MaterialShapes.Cookie9Sided.toShape()` and animate `Modifier.scale()` instead. You lose the
  silhouette change and keep 90% of the feel for 5% of the cost.
- If the art must also *rotate* (record-player look), rotate the mask on the parent with
  `graphicsLayer { rotationZ = r }` and counter-rotate the child with `-r` so the photo stays
  upright — `[CORPUS vivi-music: .../ui/player/Thumbnail.kt]`.

## 1.4 The transport row — one hero button, two calm ones

`[COMPOSED]`

```kotlin
import androidx.compose.material3.FilledIconToggleButton
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.IconButtonWidthOption

@Composable
fun TransportRow(
    isPlaying: Boolean,
    isBuffering: Boolean,
    onPlayPause: (Boolean) -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // CALM: default IconButton. No shape morph, no size bump.
        IconButton(onClick = onPrevious) {
            Icon(Icons.Rounded.SkipPrevious, contentDescription = "Previous track")
        }

        // HERO: the one Large control on this screen.
        FilledIconToggleButton(
            checked = isPlaying,
            onCheckedChange = onPlayPause,
            // toggleableShapes() has three slots (shape / pressedShape / checkedShape).
            // shapes() has two and would lose the checked shape change.
            shapes = IconButtonDefaults.toggleableShapes(),
            modifier = Modifier
                .size(IconButtonDefaults.largeContainerSize(IconButtonWidthOption.Wide))
                .semantics {
                    stateDescription = if (isPlaying) "Playing" else "Paused"
                },
        ) {
            AnimatedContent(
                targetState = isPlaying,
                transitionSpec = {
                    (fadeIn(MaterialTheme.motionScheme.fastEffectsSpec()) +
                        scaleIn(MaterialTheme.motionScheme.defaultSpatialSpec(), initialScale = 0.85f))
                        .togetherWith(fadeOut(MaterialTheme.motionScheme.fastEffectsSpec()))
                },
                label = "playPauseIcon",
            ) { playing ->
                Icon(
                    imageVector = if (playing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                    // Null: the button already carries contentDescription + stateDescription.
                    contentDescription = null,
                    modifier = Modifier.size(IconButtonDefaults.largeIconSize),
                )
            }
        }

        IconButton(onClick = onNext) {
            Icon(Icons.Rounded.SkipNext, contentDescription = "Next track")
        }
    }
}
```

**Notes**

- `IconButtonDefaults.largeContainerSize(IconButtonWidthOption.Wide)` returns a `DpSize` — the
  Expressive width axis (`Narrow` / `Uniform` / `Wide`) gives a pill-shaped hero rather than a
  circle. If those helpers do not resolve at your pin, the Large token values are a **96dp**
  container and a **32dp** icon.
- The icon swap is **fade (effects) + scale (spatial)** — the combined-transition rule: each
  property gets its own family. Never overshoot alpha.
- `scaleIn(initialScale = 0.85f)`, not `4f`. Tomato uses `4f` for a whole progress ring appearing;
  on a 32dp icon that would be absurd.
- `isBuffering` is deliberately **not** wired to a spinner inside the button here. See §4.5 for why
  a buffering state on a media transport is a different problem from a loading state.

## 1.5 Shuffle / repeat — a connected `ToggleButton` group

Hand-built connected group (`Row` + `ConnectedSpaceBetween` + per-position shapes). This is the
dominant corpus pattern by an order of magnitude and is the right trade here: two members, fixed
count, no overflow, per-item colors and semantics.

`[COMPOSED — structure verbatim from `[CORPUS vivi-music: .../ui/player/Queue.kt]`]`

```kotlin
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.ToggleButton
import androidx.compose.material3.ToggleButtonDefaults
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role

@Composable
fun PlaybackModeGroup(
    shuffle: Boolean,
    repeat: RepeatMode,
    onShuffleChange: (Boolean) -> Unit,
    onRepeatCycle: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val toggleColors = ToggleButtonDefaults.colors(
        containerColor = MaterialTheme.colorScheme.secondaryContainer,
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
        checkedContainerColor = MaterialTheme.colorScheme.primaryContainer,
        checkedContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
    )

    Row(
        modifier = modifier.fillMaxWidth(),
        // 2dp. ButtonGroupDefaults.HorizontalArrangement is the ~12dp NON-connected spacing —
        // using the wrong one is the difference between a segmented control and loose buttons.
        horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween),
    ) {
        ToggleButton(
            checked = shuffle,
            onCheckedChange = onShuffleChange,
            shapes = ButtonGroupDefaults.connectedLeadingButtonShapes(),
            colors = toggleColors,
            modifier = Modifier
                .weight(1f)
                .height(56.dp)
                .semantics { stateDescription = if (shuffle) "Shuffle on" else "Shuffle off" },
        ) {
            Icon(
                Icons.Rounded.Shuffle,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
            )
            Spacer(Modifier.size(ToggleButtonDefaults.IconSpacing))
            Text("Shuffle", style = MaterialTheme.typography.labelLarge, maxLines = 1)
        }

        // Repeat is TRI-state. Modelled as a toggle for the shape treatment, with the real
        // state carried by stateDescription and the icon — not by "checked/unchecked".
        ToggleButton(
            checked = repeat != RepeatMode.Off,
            onCheckedChange = { onRepeatCycle() },
            shapes = ButtonGroupDefaults.connectedTrailingButtonShapes(),
            colors = toggleColors,
            modifier = Modifier
                .weight(1f)
                .height(56.dp)
                .semantics {
                    role = Role.Button          // NOT a checkbox: three states, cyclic action
                    stateDescription = when (repeat) {
                        RepeatMode.Off -> "Repeat off"
                        RepeatMode.All -> "Repeat all"
                        RepeatMode.One -> "Repeat one"
                    }
                },
        ) {
            Icon(
                imageVector = if (repeat == RepeatMode.One) Icons.Rounded.RepeatOne
                              else Icons.Rounded.Repeat,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
            )
            Spacer(Modifier.size(ToggleButtonDefaults.IconSpacing))
            Text("Repeat", style = MaterialTheme.typography.labelLarge, maxLines = 1)
        }
    }
}
```

**Why not `ButtonGroup` (the composable)?** `ButtonGroup` buys you press-expands-and-squeezes-
neighbours and menu overflow. Neither applies to a fixed two-item mode selector. The timer screen
(§2.4) uses the real `ButtonGroup` because *there* the squeeze is the point.

**Shape API landmines:** never write `ToggleButtonDefaults.shapes(...)` — both overloads are
`DeprecationLevel.HIDDEN` at alpha25+, i.e. invisible to Kotlin source, so alpha24-era code fails to
compile rather than warn. Use `ToggleButtonDefaults.shapesFor(height)` for the default set or the
`ToggleButtonShapes(shape, pressedShape, checkedShape)` **constructor** for a custom silhouette. The
`ButtonGroupDefaults.connected*ButtonShapes()` helpers used above were **not** deprecated.

## 1.6 Queue sheet

`[COMPOSED — dismissal idiom from `[CORPUS Tomato: .../LocaleBottomSheet.android.kt]`,
`animateItem` from `[CORPUS LastChat: .../ui/pages/chat/ConversationList.kt]`]`

```kotlin
// ModalBottomSheet / rememberModalBottomSheetState are ExperimentalMaterial3Api at alpha26.
// (rememberBottomSheetState is the alpha20+ unified replacement for the state factory —
//  migrate when you can; the sheet composable is unchanged.)
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QueueSheet(
    queue: List<TrackUi>,
    currentIndex: Int,
    onSelect: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState()
    val scope = rememberCoroutineScope()

    fun dismissAnimated(then: () -> Unit = {}) {
        // hide() is a suspend animation. Flipping the boolean directly yanks the sheet out of
        // composition and you lose the exit animation.
        scope.launch { sheetState.hide() }.invokeOnCompletion {
            then()
            onDismiss()
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
    ) {
        Text(
            text = "Up next",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(start = 24.dp, bottom = 8.dp),
        )
        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(2.dp),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
        ) {
            itemsIndexed(
                items = queue,
                // MANDATORY for animateItem. Without a stable key Compose cannot tell a reorder
                // from a content change and the animation silently does nothing.
                key = { _, track -> track.id },
            ) { index, track ->
                val isCurrent = index == currentIndex
                ListItem(
                    onClick = { dismissAnimated { onSelect(index) } },
                    supportingContent = { Text(track.artist) },
                    trailingContent = {
                        if (isCurrent) {
                            Icon(
                                Icons.Rounded.GraphicEq,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                            )
                        }
                    },
                    colors = ListItemDefaults.colors(
                        containerColor = if (isCurrent)
                            MaterialTheme.colorScheme.secondaryContainer
                        else MaterialTheme.colorScheme.surfaceContainerHigh,
                    ),
                    shapes = ListItemDefaults.segmentedShapes(index = index, count = queue.size),
                    modifier = Modifier
                        .animateItem(
                            fadeInSpec = MaterialTheme.motionScheme.defaultEffectsSpec(),
                            fadeOutSpec = MaterialTheme.motionScheme.fastEffectsSpec(),
                            placementSpec = MaterialTheme.motionScheme.defaultSpatialSpec(),
                        )
                        .semantics {
                            if (isCurrent) stateDescription = "Now playing"
                        },
                ) {
                    Text(track.title, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}
```

**Note the expressive `ListItem` overload takes the headline as the trailing `content` lambda**, not
as a first `headlineContent` parameter. Both overload sets are on the classpath, so mixing them up
produces a confusing "no applicable overload".

`ListItemDefaults.segmentedShapes(index, count)` handles `count == 1` correctly on alpha25+
(I2ea1c). On alpha24 and earlier a one-item queue gets rounded-top/square-bottom — see §1.8 of the
settings file for the fallback.

## 1.7 Seeded color from album art

**Do not reimplement this — read `${CLAUDE_PLUGIN_ROOT}/skills/m3-expressive-theming/references/color.md` §6.3**, which carries
vivi-music's complete `extractThemeColor()` / `LaunchedEffect` / `ColorSaver` plumbing. The player
screen's only job is to hoist the seed and hand it to the theme:

`[CORPUS vivi-music: .../ui/theme/Theme.kt, condensed]`

```kotlin
val scheme = rememberDynamicColorScheme(
    seedColor = seed,                                   // from Palette + Score on the artwork bitmap
    isDark = darkTheme,
    // THE most important argument. Without SPEC_2025 you get 2021 palette math and the
    // scheme will not match an Expressive design.
    specVersion = ColorSpec.SpecVersion.SPEC_2025,
    style = if (seed.toArgb() == 0xFF000000.toInt()) PaletteStyle.Monochrome
            else PaletteStyle.TonalSpot,
)

MaterialExpressiveTheme(
    colorScheme = scheme,
    motionScheme = MotionScheme.expressive(),
    typography = appTypography,
) { /* player */ }
```

Four rules, all of which the corpus violates somewhere: `.allowHardware(false)` on the image request
(`Palette` cannot read hardware bitmaps — the #1 silent failure); decode on `Dispatchers.IO`, never
in composition; always have a fallback seed (a bitmap can be one flat colour); and **put the whole
feature behind a preference** — re-tinting the entire app per track is a strong effect and reads as
flicker if it is not opt-in.

## 1.8 Mini-player → full player via shared bounds

`[COMPOSED — helper verbatim from `[CORPUS Tomato: .../statsScreen/components/sharedBoundsReveal.kt]`,
adapted from Nav3 to a local `AnimatedContent`]`

```kotlin
@Composable
fun PlayerHost(
    state: PlayerUiState,
    actions: PlayerActions,
    modifier: Modifier = Modifier,
) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    BackHandler(enabled = expanded) { expanded = false }

    SharedTransitionLayout(modifier = modifier) {
        AnimatedContent(
            targetState = expanded,
            // Effects specs at the container level; the shared elements carry ALL the spatial
            // motion. If the container also slid, the art would appear to move twice.
            transitionSpec = {
                fadeIn(MaterialTheme.motionScheme.defaultEffectsSpec())
                    .togetherWith(fadeOut(MaterialTheme.motionScheme.fastEffectsSpec()))
            },
            label = "playerExpansion",
        ) { isExpanded ->
            val sharedScope = this@SharedTransitionLayout
            val visibilityScope = this@AnimatedContent

            if (isExpanded) {
                FullPlayerScreen(
                    state = state,
                    actions = actions,
                    onCollapse = { expanded = false },
                    sharedScope = sharedScope,
                    visibilityScope = visibilityScope,
                )
            } else {
                MiniPlayer(
                    state = state,
                    actions = actions,
                    onExpand = { expanded = true },
                    sharedScope = sharedScope,
                    visibilityScope = visibilityScope,
                )
            }
        }
    }
}
```

Both sides use the **same keys**. Artwork is `sharedElement` (identical content, two sizes); the
container is `sharedBounds` (different content, cross-fade); the title is `sharedBounds` (same
string, different type scale).

```kotlin
@Composable
fun MiniPlayer(
    state: PlayerUiState,
    actions: PlayerActions,
    onExpand: () -> Unit,
    sharedScope: SharedTransitionScope,
    visibilityScope: AnimatedVisibilityScope,
) = with(sharedScope) {
    val track = state.track ?: return@with

    Surface(
        onClick = onExpand,
        shape = MaterialTheme.shapes.largeIncreased,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp)
            .sharedBounds(
                sharedContentState = rememberSharedContentState("player-container"),
                animatedVisibilityScope = visibilityScope,
                clipInOverlayDuringTransition = OverlayClip(MaterialTheme.shapes.largeIncreased),
            )
            // One tap target for the whole bar; the play button inside is a separate node.
            .semantics(mergeDescendants = true) {
                contentDescription = "Now playing: ${track.title} by ${track.artist}"
                onClick(label = "Open player") { onExpand(); true }
            },
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(8.dp)) {
            AsyncImage(
                model = track.artworkUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(48.dp)
                    // CALM at 48dp: rounded rect, not a nine-lobed cookie. §1.3.
                    .clip(MaterialTheme.shapes.medium)
                    .sharedElement(
                        sharedContentState = rememberSharedContentState("player-artwork"),
                        animatedVisibilityScope = visibilityScope,
                    ),
            )
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    track.title,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.sharedBounds(
                        sharedContentState = rememberSharedContentState("player-title"),
                        animatedVisibilityScope = visibilityScope,
                        resizeMode = SharedTransitionScope.ResizeMode.ScaleToBounds(),
                    ),
                )
                Text(
                    track.artist,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
            }
            IconButton(onClick = { actions.onPlayPause(!state.isPlaying) }) {
                Icon(
                    if (state.isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                    contentDescription = if (state.isPlaying) "Pause" else "Play",
                )
            }
        }
    }
}
```

> `SharedTransitionScope.ResizeMode.ScaleToBounds()` is the API shape used by
> `[CORPUS Tomato]` via the `scaleToBounds(contentScale = …)` companion helper. Both forms appear in
> the wild; **check which resolves at your `compose-animation` pin** — `[UNVERIFIED]` as written.
> `RemeasureToBounds` is the alternative and is much more expensive; use it only when text must
> genuinely reflow to a different line count mid-flight.

**Shared-bounds pitfalls that actually bite:**

- Keys must match exactly and be unique per transition. Use descriptive strings, and in a list key
  by item id (`"artwork-${track.id}"`).
- The destination's shared node must exist on the **first frame**. Behind an `if (loaded)` guard or
  a `LaunchedEffect`, the transition silently degrades to a fade.
- Put the shared modifier **before** `.clip()`/`.background()`. The overlay clip comes from
  `clipInOverlayDuringTransition`; a later `.clip()` shapes the resting state.
- **On expanded widths where both mini and full player are visible simultaneously, disable the
  shared elements.** Two live copies of one key produce a nonsensical flight. `[CORPUS Tomato]`
  guards every shared element with `if (!widthExpanded)`. Mandatory on adaptive layouts, not polish.

## 1.9 The assembled screen

`[COMPOSED]`

```kotlin
@Composable
fun FullPlayerScreen(
    state: PlayerUiState,
    actions: PlayerActions,
    onCollapse: () -> Unit,
    sharedScope: SharedTransitionScope,
    visibilityScope: AnimatedVisibilityScope,
) = with(sharedScope) {
    val track = state.track ?: return@with
    var showQueue by rememberSaveable { mutableStateOf(false) }

    // Largest -> smallest. isWidthAtLeastBreakpoint is a >= predicate, so smallest-first
    // silently makes every wider branch unreachable.
    val widthClass = currentWindowAdaptiveInfoV2().windowSizeClass
    val twoPane = when {
        widthClass.isWidthAtLeastBreakpoint(WindowSizeClass.WIDTH_DP_EXPANDED_LOWER_BOUND) -> true
        widthClass.isWidthAtLeastBreakpoint(WindowSizeClass.WIDTH_DP_MEDIUM_LOWER_BOUND) -> false
        else -> false
    }

    Scaffold(
        modifier = Modifier.sharedBounds(
            sharedContentState = rememberSharedContentState("player-container"),
            animatedVisibilityScope = visibilityScope,
        ),
        topBar = {
            // CALM: plain TopAppBar. The content is the hero; a LargeFlexibleTopAppBar here
            // would compete with the album art for the same job.
            TopAppBar(
                title = { },
                navigationIcon = {
                    IconButton(onClick = onCollapse) {
                        Icon(Icons.Rounded.ExpandMore, contentDescription = "Collapse player")
                    }
                },
                actions = {
                    IconButton(onClick = { showQueue = true }) {
                        Icon(Icons.Rounded.QueueMusic, contentDescription = "Show queue")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent,
                    scrolledContainerColor = Color.Transparent,
                ),
            )
        },
        containerColor = MaterialTheme.colorScheme.surface,
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.weight(1f))

            AlbumArt(
                artworkUrl = track.artworkUrl,
                isPlaying = state.isPlaying,
                modifier = Modifier
                    .fillMaxWidth(if (twoPane) 0.5f else 0.85f)
                    .sharedElement(
                        sharedContentState = rememberSharedContentState("player-artwork"),
                        animatedVisibilityScope = visibilityScope,
                    ),
            )

            Spacer(Modifier.height(32.dp))

            // CALM: no emphasized styles, no marquee, no animation.
            Text(
                text = track.title,
                style = MaterialTheme.typography.titleLarge,
                maxLines = 2,
                textAlign = TextAlign.Center,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.sharedBounds(
                    sharedContentState = rememberSharedContentState("player-title"),
                    animatedVisibilityScope = visibilityScope,
                ),
            )
            Text(
                text = track.artist,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
            )

            Spacer(Modifier.height(24.dp))

            WavySeekBar(
                positionMs = state.positionMs,
                durationMs = state.durationMs,
                isPlaying = state.isPlaying,
                onSeek = actions.onSeek,
            )
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                // The seek bar already announces both values via stateDescription; these two
                // labels are visual duplicates, so hide them from the reader.
                Text(
                    formatDuration(state.positionMs),
                    style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier.clearAndSetSemantics {},
                )
                Text(
                    formatDuration(state.durationMs),
                    style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier.clearAndSetSemantics {},
                )
            }

            Spacer(Modifier.height(16.dp))

            TransportRow(
                isPlaying = state.isPlaying,
                isBuffering = state.isBuffering,
                onPlayPause = actions.onPlayPause,
                onPrevious = actions.onPrevious,
                onNext = actions.onNext,
            )

            Spacer(Modifier.height(16.dp))

            PlaybackModeGroup(
                shuffle = state.shuffle,
                repeat = state.repeat,
                onShuffleChange = actions.onShuffleChange,
                onRepeatCycle = actions.onRepeatCycle,
            )

            Spacer(Modifier.weight(1f))
        }
    }

    if (showQueue) {
        QueueSheet(
            queue = state.queue,
            currentIndex = state.queueIndex,
            onSelect = actions.onSelectQueueIndex,
            onDismiss = { showQueue = false },
        )
    }
}
```

## 1.10 Accessibility block — media player

**Content descriptions**

| Node | Description | Note |
| --- | --- | --- |
| Album art | `null` | Decorative. The title/artist text beside it carries the identity. Describing artwork as "album art" adds a stop and no information. |
| Play/pause | `null` on the `Icon`; the button carries `stateDescription` | The visible state is the *button's*, not the icon's. Setting both double-announces. |
| Skip prev/next | `"Previous track"` / `"Next track"` | Icon-only, so mandatory. |
| Collapse / queue | `"Collapse player"` / `"Show queue"` | |
| Shuffle / repeat | `null` on icons; text label + `stateDescription` on the button | |
| Elapsed / total labels | `clearAndSetSemantics {}` | Duplicates of the seek bar's own announcement. |

**State descriptions** — the highest-value semantics on this screen.

```kotlin
// Seek bar: replaces the default "37 percent"
stateDescription = "1:12 of 3:45"
// Play/pause
stateDescription = if (isPlaying) "Playing" else "Paused"
// Shuffle
stateDescription = if (shuffle) "Shuffle on" else "Shuffle off"
// Repeat — tri-state, so role = Role.Button and the state carried in words
stateDescription = "Repeat all"
// Queue row
stateDescription = "Now playing"
```

**Traversal order.** Default reading order on this screen is art → title → artist → seek → times →
transport → modes, which matches visual order and needs no override. Two things to check:

- The mini-player merges its children (`semantics(mergeDescendants = true)`) so it reads as one
  target, **except** the play button, which stays its own node. Verify the play button is still
  reachable — a merged parent can swallow children if you also set `clearAndSetSemantics`.
- If you add a lyrics pane that overlays the art, mark the pane
  `Modifier.semantics { isTraversalGroup = true }` and give it a `traversalIndex` so it is read as a
  block rather than interleaved with the transport controls.

**Touch targets.** The seek bar's *visual* track is 4dp; the real `Slider` supplies a ≥48dp
interactive height. That is the concrete reason to use the `track` slot and not a
`Box(Modifier.height(48.dp))` with `pointerInput` — you get it for free. The `IconButton`s are 48dp
by default via `minimumInteractiveComponentSize()`. The connected group's **2dp internal spacing is
below the 8dp separation guidance** — intentional in the spec (the members read as one control), but
it means you must not additionally shrink members below 40dp.

**What TalkBack should announce, in order, when the screen opens playing:**

1. "Collapse player, button"
2. "Show queue, button"
3. "Midnight City" (title)
4. "M83" (artist)
5. "Seek, 1:12 of 3:45, slider. Swipe up or down with one finger to adjust."
6. "Previous track, button"
7. "Playing, button" (the hero play/pause)
8. "Next track, button"
9. "Shuffle, Shuffle off, button"
10. "Repeat, Repeat off, button"

**Reduced motion.** The art morph, the wave animation and the shared-bounds flight all keep the
frame pump alive. Honour the system "Remove animations" setting: gate the morph target to a constant
and pass `amplitude = { 0f }`. See `${CLAUDE_PLUGIN_ROOT}/skills/m3-expressive-motion/references/motion-scheme.md` §10 for
LastChat's `MotionPolicy` pattern — the corpus's only real implementation.

**Contrast under seeded colour.** The seed changes per track, so the contrast you verified against
one album is not the contrast you ship. Always pair accent-with-on-accent and
container-with-on-container roles (material color roles guarantee ≥3:1 for those pairs); never
improvise `TextBackgroundColor.copy(alpha = 0.2f)` containers over artwork, which is exactly where
vivi-music's translucent player group falls below the floor.

---

# 2. Timer / progress hero screen

## 2.0 What this demonstrates

A single large circular ring that **swaps component by mode** — `CircularProgressIndicator` in focus,
`CircularWavyProgressIndicator` on a break — wrapped in `sharedBounds` with keys that also exist on a
second destination, so the ring morphs across a navigation change rather than cross-fading.

The wave is **a mode signal, not decoration**. That is the correct reason to use it, and it is the
single most transferable idea in the corpus.

**Deliberately calm:** the background (flat `surface`, no gradient); the mode label (plain
`labelLarge`); the settings entry point (default `IconButton` in a plain `TopAppBar`). One hero: the
ring plus its numerals.

## 2.1 State shape

`[COMPOSED]`

```kotlin
enum class TimerMode { Focus, Break }

@Immutable
data class TimerUiState(
    val mode: TimerMode = TimerMode.Focus,
    val remainingMs: Long = 25 * 60 * 1000L,
    val totalMs: Long = 25 * 60 * 1000L,
    val running: Boolean = false,
) {
    val progress: Float
        get() = if (totalMs > 0L) (remainingMs.toFloat() / totalMs).coerceIn(0f, 1f) else 0f
}
```

## 2.2 The ring — smooth ⇄ wavy, shared across destinations

`[CORPUS Tomato: shared/src/androidMain/.../timerScreen/TimerScreen.kt:325-380 and
.../ui/AlwaysOnDisplay.kt:216-262 — structure verbatim, sizes parameterised]`

```kotlin
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.CircularWavyProgressIndicator

@Composable
fun SharedTransitionScope.TimerRing(
    mode: TimerMode,
    progress: () -> Float,          // LAMBDA. Never pass the ticking Float by value.
    visibilityScope: AnimatedVisibilityScope,
    modifier: Modifier = Modifier,
    strokeWidth: Dp = 16.dp,
    wavelength: Dp = 60.dp,
) {
    val color = MaterialTheme.colorScheme.primary
    val trackColor = MaterialTheme.colorScheme.secondaryContainer

    val density = LocalDensity.current
    val strokePx = with(density) { strokeWidth.toPx() }
    val ringStroke = remember(strokePx) { Stroke(width = strokePx, cap = StrokeCap.Round) }

    if (mode == TimerMode.Focus) {
        CircularProgressIndicator(
            progress = progress,
            modifier = modifier
                .sharedBounds(
                    sharedContentState = rememberSharedContentState("focus progress"),
                    animatedVisibilityScope = visibilityScope,
                )
                .aspectRatio(1f),
            color = color,
            trackColor = trackColor,
            strokeWidth = strokeWidth,      // Dp here...
            gapSize = 8.dp,                 // 2x the 4dp default: at a 16dp stroke the default vanishes
        )
    } else {
        CircularWavyProgressIndicator(
            progress = progress,
            modifier = modifier
                .sharedBounds(
                    sharedContentState = rememberSharedContentState("break progress"),
                    animatedVisibilityScope = visibilityScope,
                )
                .aspectRatio(1f),
            color = color,
            trackColor = trackColor,
            stroke = ringStroke,            // ...Stroke in PIXELS here. The classic bug.
            trackStroke = ringStroke,
            wavelength = wavelength,        // ~ring/5. 60dp on ~315dp, 42dp on 250dp.
            gapSize = 8.dp,
        )
    }
}
```

**The cross-destination trick.** Tomato repeats this block *verbatim* in its always-on-display
screen at 250dp with a 12dp stroke and 42dp wavelength, using **the same two content keys**. Both
screens sit under one app-level `SharedTransitionLayout`, so navigating between them morphs the ring
instead of cross-fading it. Two rules make it work:

1. **Both branches carry keys, and the keys differ by mode** (`"focus progress"` /
   `"break progress"`). A single key across two different component types would try to morph a
   `CircularProgressIndicator` into a `CircularWavyProgressIndicator` — the bounds would animate but
   the content would pop.
2. **Reserve the space in the empty branch.** Tomato writes `Box(Modifier.size(250.dp))` in its
   `else`. "Do not allow layout shift during loading" is one of Material's eight motion principles,
   and a ring that vanishes is the loudest possible violation.

## 2.3 Big display type, without a per-second announcement

`[COMPOSED — AnimatedContent shape from `[CORPUS Tomato: .../timerScreen/TimerScreen.kt:229-245]`]`

```kotlin
@Composable
fun TimerNumerals(
    remainingMs: Long,
    mode: TimerMode,
    modifier: Modifier = Modifier,
) {
    val text = remember(remainingMs) { formatDuration(remainingMs) }

    // Announce once a minute, not 60 times a minute. TalkBack re-reads a Polite live region
    // whenever the semantics change, so the description must be keyed on a COARSE value.
    // Bucket to whole minutes, then remember the string on that bucket: it is a new object
    // (and therefore a new announcement) only when the minute rolls over.
    val minutesRemaining = remainingMs / 60_000L + 1
    val announcement = remember(minutesRemaining) {
        "$minutesRemaining ${if (minutesRemaining == 1L) "minute" else "minutes"} remaining"
    }

    Column(
        modifier = modifier.semantics(mergeDescendants = true) {
            liveRegion = LiveRegionMode.Polite
            contentDescription = "${if (mode == TimerMode.Focus) "Focus" else "Break"}, $announcement"
        },
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        AnimatedContent(
            targetState = text,
            transitionSpec = {
                slideInVertically(
                    animationSpec = MaterialTheme.motionScheme.defaultSpatialSpec(),
                    initialOffsetY = { (-it * 1.25).toInt() },   // 1.25 clears the bounds so the
                ).togetherWith(                                   // two values never overlap
                    slideOutVertically(
                        animationSpec = MaterialTheme.motionScheme.defaultSpatialSpec(),
                        targetOffsetY = { (it * 1.25).toInt() },
                    )
                )
            },
            label = "timerNumerals",
        ) { value ->
            Text(
                text = value,
                // Emphasized = the system-sanctioned weight bump. Prefer this over
                // displayLarge.copy(fontWeight = Bold) — it stays tokenized and themeable.
                style = MaterialTheme.typography.displayLargeEmphasized,
                // Tabular figures stop the numerals jittering as digits change width.
                fontFeatureSettings = "tnum",
            )
        }
        Text(
            text = if (mode == TimerMode.Focus) "Focus" else "Break",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
```

Two details worth transferring anywhere you show live numerals:

- **`fontFeatureSettings = "tnum"`** (tabular figures). Without it a proportional face shifts the
  whole string every time a `1` becomes a `0`. Note this is an OpenType feature string, separate
  from `FontVariation` axes — see
  `${CLAUDE_PLUGIN_ROOT}/skills/m3-expressive-theming/references/typography-and-variable-fonts.md` §3.5.
- **`displayLargeEmphasized` is a weight shift, not a size shift** — same 57sp/64sp as
  `displayLarge`, `WeightMedium` instead of `Normal`, and tracking changes from -0.25 to 0. It looks
  like synthetic fake-bold unless your face has a real `wght` axis.

## 2.4 Transport controls — the real `ButtonGroup`

Here the press-expands-and-squeezes interaction *is* the point: three chunky controls in a row where
pressing one visibly claims space from its neighbours.

`[CORPUS Tomato: shared/src/androidMain/.../timerScreen/TimerScreen.kt:446-530 — structure
verbatim, condensed to two items]`

```kotlin
import androidx.compose.material3.ButtonGroup
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.FilledIconToggleButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.IconButtonDefaults

@Composable
fun TimerTransport(
    running: Boolean,
    onToggle: () -> Unit,
    onReset: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // One InteractionSource per child, and the SAME instance must go to both the button's
    // `interactionSource =` and its `animateWidth(...)`. Two different sources = no animation
    // and no compile error. This is the #1 ButtonGroup failure.
    val sources = remember { List(2) { MutableInteractionSource() } }
    val haptic = LocalHapticFeedback.current

    ButtonGroup(
        overflowIndicator = { menuState ->
            ButtonGroupDefaults.OverflowIndicator(
                menuState = menuState,
                colors = IconButtonDefaults.filledTonalIconButtonColors(),
            )
        },
        modifier = modifier.padding(16.dp),
    ) {
        customItem(
            buttonGroupContent = {
                FilledIconToggleButton(
                    checked = running,
                    onCheckedChange = { checked ->
                        onToggle()
                        haptic.performHapticFeedback(
                            if (checked) HapticFeedbackType.ToggleOn else HapticFeedbackType.ToggleOff
                        )
                    },
                    shapes = IconButtonDefaults.toggleableShapes(),
                    interactionSource = sources[0],
                    modifier = Modifier
                        .size(width = 128.dp, height = 96.dp)
                        .animateWidth(sources[0])
                        .semantics { stateDescription = if (running) "Running" else "Paused" },
                ) {
                    Icon(
                        imageVector = if (running) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                        contentDescription = if (running) "Pause timer" else "Start timer",
                        modifier = Modifier.size(32.dp),
                    )
                }
            },
            // The overflow fallback MUST carry the same label as the inline button, or the two
            // presentations announce differently.
            menuContent = { menuState ->
                DropdownMenuItem(
                    leadingIcon = {
                        Icon(
                            if (running) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                            contentDescription = null,
                        )
                    },
                    text = { Text(if (running) "Pause timer" else "Start timer") },
                    onClick = { onToggle(); menuState.dismiss() },
                )
            },
        )

        customItem(
            buttonGroupContent = {
                FilledTonalIconButton(
                    onClick = onReset,
                    shapes = IconButtonDefaults.shapes(),
                    interactionSource = sources[1],
                    modifier = Modifier
                        .size(width = 96.dp, height = 96.dp)
                        .animateWidth(sources[1]),
                ) {
                    Icon(
                        Icons.Rounded.Refresh,
                        contentDescription = "Reset timer",
                        modifier = Modifier.size(28.dp),
                    )
                }
            },
            menuContent = { menuState ->
                DropdownMenuItem(
                    leadingIcon = { Icon(Icons.Rounded.Refresh, contentDescription = null) },
                    text = { Text("Reset timer") },
                    onClick = { onReset(); menuState.dismiss() },
                )
            },
        )
    }
}
```

**API notes**

- `overflowIndicator` is the **first, required, positional** parameter on alpha22+. The 1.4.0
  signature `ButtonGroup(modifier, horizontalArrangement, content)` was removed. If you get "no
  overload matches", this is why. Pass `{}` if overflow genuinely cannot happen.
- Use the **bare 1-arg `animateWidth(source)`**. It is correct on every artifact. The 2-arg form's
  `compressionLimit` changed type from `PaddingValues` to `Dp` in alpha25 — the 1-arg form also
  resolves layout direction dynamically, which the old `PaddingValues` default could not.
- `ButtonGroupScope` became a **sealed interface** in alpha25. If you wrapped or faked it in tests,
  that code no longer compiles and there is no replacement.

## 2.5 Accessibility block — timer

**The one thing that matters most here: do not put a live region on a per-second value.** A `Polite`
live region fires on every change. On a ticking timer that is 60 interruptions per minute and TalkBack
becomes unusable. §2.3 solves it by deriving a coarse minute bucket and announcing from that.

- **Content descriptions:** the ring itself gets `contentDescription = null` — the numerals below it
  carry the value, and a progress indicator that also announces "34 percent" competes with
  "12 minutes remaining". If the ring is the *only* representation (an always-on display with no
  numerals), then it needs `progressBarRangeInfo` and the numerals do not exist to conflict with.
- **State descriptions:** `"Running"` / `"Paused"` on the play/pause toggle; the mode
  (`"Focus"` / `"Break"`) folded into the numerals' merged description so it is announced with the
  time rather than as a separate stop.
- **Traversal order:** ring (skipped) → numerals+mode (one merged node, live region) → transport
  group. Wrap the transport in `Modifier.semantics { isTraversalGroup = true }`; the members only
  make sense together.
- **Touch targets:** the transport buttons are 96dp — far above the minimum, correct for a control
  you press without looking. `ButtonGroup`'s default spacing is ~12dp, above the 8dp separation
  guidance.
- **Announcements on transition:** when the timer completes and flips Focus → Break, announce it
  once. The live region on the numerals will do this automatically because the merged description
  changes; verify it does not fire twice (once for mode, once for minutes) by keeping both in a
  single `contentDescription` string.
- **`scrollBehavior` is disabled when an accessibility service is running** on flexible bottom bars
  and floating toolbars — the layout must be usable with those bars permanently visible.

---

# 3. Hero / onboarding / empty state

This is the one screen type where a large `MaterialShapes` polygon, an **XLarge** button and
display-emphasized type are all simultaneously correct. Material's budget is **one or two hero
moments per product**; an onboarding page is where you spend one, because there is nothing else on
the screen to compete with.

## 3.1 Onboarding page

`[COMPOSED — polygon backdrop verbatim in structure from
`[CORPUS Tomato: .../settingsScreen/DetailPlaceholder.kt]`; button sizing from `[ANDROIDX]`]`

```kotlin
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.toShape

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun OnboardingPage(
    title: String,
    body: String,
    primaryLabel: String,
    onPrimary: () -> Unit,
    onSkip: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // toShape() allocates a Path — hoist it once per screen, never inside items { }.
    val heroShape = remember { MaterialShapes.Cookie12Sided }

    Column(
        modifier = modifier
            .fillMaxSize()
            .safeDrawingPadding()
            .padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.weight(1f))

        // HERO 1 — the polygon. 200dp: well above Cookie12Sided's ~56dp legibility floor.
        Box(
            contentAlignment = Alignment.Center,
            // Decorative. Hide the whole cluster from the reader; the headline says the same thing.
            modifier = Modifier.clearAndSetSemantics {},
        ) {
            Spacer(
                Modifier
                    .size(200.dp)
                    .background(
                        color = MaterialTheme.colorScheme.secondaryContainer,
                        shape = heroShape.toShape(),
                    )
            )
            Icon(
                imageVector = Icons.Rounded.AutoAwesome,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSecondaryContainer,
                // ~56% of the container is the right ratio for the cookie family: the icon must
                // clear the lobes' inner radius, not the outer.
                modifier = Modifier.size(112.dp),
            )
        }

        Spacer(Modifier.height(48.dp))

        // HERO 2 — display-emphasized type. One per screen.
        Text(
            text = title,
            style = MaterialTheme.typography.displaySmallEmphasized,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(16.dp))
        // CALM: body copy is never emphasized. Medium-weight paragraphs read as heavy.
        Text(
            text = body,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )

        Spacer(Modifier.weight(1f))

        // HERO 3 — the XLarge button (136dp). Legitimate ONLY because it is alone.
        Button(
            onClick = onPrimary,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = ButtonDefaults.ExtraLargeContainerHeight),
            // Feed the SAME Dp to every *For helper. Mixing an XLarge height with Small
            // padding is the most common way this looks wrong.
            shapes = ButtonDefaults.shapesFor(ButtonDefaults.ExtraLargeContainerHeight),
            contentPadding = ButtonDefaults.contentPaddingFor(
                buttonHeight = ButtonDefaults.ExtraLargeContainerHeight,
                hasStartIcon = false,
                hasEndIcon = true,
            ),
        ) {
            Text(
                text = primaryLabel,
                style = ButtonDefaults.textStyleFor(ButtonDefaults.ExtraLargeContainerHeight),
            )
            Spacer(Modifier.width(ButtonDefaults.ExtraLargeIconSpacing))
            Icon(
                Icons.AutoMirrored.Rounded.ArrowForward,
                contentDescription = null,
                modifier = Modifier.size(ButtonDefaults.ExtraLargeIconSize),
            )
        }

        Spacer(Modifier.height(8.dp))

        // CALM: lowest-emphasis dismissal. Never two filled buttons side by side.
        TextButton(onClick = onSkip, modifier = Modifier.fillMaxWidth()) {
            Text("Not now")
        }

        Spacer(Modifier.height(24.dp))
    }
}
```

**The hero budget, spent explicitly.** This screen spends three hero moves — polygon, display type,
XLarge button — and they are *the same* moment: one call to action, stated three ways. Everything
else is baseline. The failure mode is spending them on three *different* things: a polygon hero next
to an XLarge button next to an emphasized headline that all point at different actions. Then nothing
is emphatic, because emphasis is relational — a shape reads as emphatic only by breaking from the
surrounding shape style.

**Three-arg `contentPaddingFor` on purpose.** The 1-arg `contentPaddingFor(buttonHeight)` overload
was re-marked `@ExperimentalMaterial3ExpressiveApi` in alpha21; the 3-arg form is not gated.

**Do not use `ButtonDefaults.IconSize`** on an Expressive button — it is the legacy 18dp baseline and
is smaller than every size-specific constant, including `SmallIconSize = 20.dp`. Use
`ExtraLargeIconSize` (40dp) / `LargeIconSize` (32dp) / `MediumIconSize` (24dp).

## 3.2 Empty state — the same anatomy, one emphasis level down

An empty state is an onboarding page with a smaller budget: it is not the product's first
impression, and the user got here by accident or by deleting things. Drop the XLarge button to
Medium, drop display type to headline, keep the polygon but shrink it.

`[COMPOSED]`

```kotlin
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun EmptyState(
    headline: String,
    supporting: String,
    actionLabel: String?,
    onAction: (() -> Unit)?,
    modifier: Modifier = Modifier,
    // Ghostish / Bun / ClamShell read as friendly. Avoid Heart (unavoidable literal meaning)
    // and Arrow (reads as iconography, not decoration).
    shape: RoundedPolygon = MaterialShapes.Ghostish,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(32.dp)
            .semantics { isTraversalGroup = true },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Spacer(
            Modifier
                .size(120.dp)                       // above Ghostish's ~48dp floor, well below hero size
                .background(MaterialTheme.colorScheme.surfaceContainerHighest, shape.toShape())
                .clearAndSetSemantics {}
        )
        Spacer(Modifier.height(24.dp))
        Text(headline, style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center)
        Spacer(Modifier.height(8.dp))
        Text(
            supporting,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        if (actionLabel != null && onAction != null) {
            Spacer(Modifier.height(24.dp))
            Button(
                onClick = onAction,
                modifier = Modifier.heightIn(min = ButtonDefaults.MediumContainerHeight),
                shapes = ButtonDefaults.shapesFor(ButtonDefaults.MediumContainerHeight),
                contentPadding = ButtonDefaults.contentPaddingFor(
                    ButtonDefaults.MediumContainerHeight, hasStartIcon = false, hasEndIcon = false,
                ),
            ) { Text(actionLabel) }
        }
    }
}
```

**Shape-choice rules for empty-state art**, from the catalog's "reads down to" column:

| Want | Use | Avoid |
| --- | --- | --- |
| Friendly / playful | `Ghostish`, `Bun`, `SoftBoom`, `Puffy` | `Burst`, `Boom` (celebration only) |
| Neutral container | `Square`, `Pill`, `Puffy` | `Diamond` (wastes ~50% of its box) |
| Error / attention | `Triangle` | `Arrow` (reads as a "next" affordance) |
| Anything under 40dp | `Circle`, `Square`, `Cookie4Sided` | every 9- and 12-lobed shape |

## 3.3 Accessibility block — hero / empty state

- **The polygon is decoration and must not be announced.** `clearAndSetSemantics {}` on the art
  container (not `contentDescription = null` alone, which still leaves an empty node in some
  traversal paths). If the illustration genuinely carries information the text does not, then it is
  not decoration — write the description and reconsider the layout.
- **Traversal order:** art (skipped) → headline → body → primary → dismissal. That is the default
  order and matches visual order, so no `traversalIndex` is needed. Wrap the whole state in
  `Modifier.semantics { isTraversalGroup = true }` so it does not interleave with a surrounding list.
- **Touch targets:** an XLarge button is 136dp tall and `fillMaxWidth` — no risk. The `TextButton`
  below it is 40dp with `minimumInteractiveComponentSize()` applied by material3; verify the two are
  ≥8dp apart (the `Spacer(8.dp)` above is exactly at the floor — 12dp is safer).
- **What TalkBack should announce:** "Track your focus, heading" → "Start a session and we'll keep
  the timer honest." → "Get started, button" → "Not now, button". Nothing about a cookie.
- **Contrast:** `secondaryContainer` / `onSecondaryContainer` is a guaranteed ≥3:1 pair. A hero icon
  tinted `primary` on `secondaryContainer` is *not* a guaranteed pair — that combination is the most
  common contrast defect on hero screens.
- **Reduced motion:** if the hero art animates (§4.3's rotating cluster is a common onboarding
  choice), auto-moving content lasting more than five seconds must be pausable, stoppable or
  hideable. An `infiniteRepeatable` on a splash is exactly this case.

---

# 4. Loading, skeletons, and shape-morph art

## 4.1 Which one — decide before you write anything

```
Is the wait under ~5 seconds AND indeterminate throughout?
  YES -> LoadingIndicator / ContainedLoadingIndicator
  NO  v
Do you know the progress fraction?
  YES -> determinate progress indicator (linear or circular)
  NO  -> indeterminate progress indicator
```

Then, orthogonally:

| Situation | Use | Not |
| --- | --- | --- |
| First paint of a list/grid whose layout you know | **Skeleton / shimmer** | a centred spinner |
| Pagination footer of that same list | `LoadingIndicator` | another skeleton |
| An action the user just triggered (in a button, a dialog) | `ContainedLoadingIndicator` | a full-screen scrim |
| A wait that starts indeterminate and becomes determinate | a **progress indicator from the start** | `LoadingIndicator` that swaps mid-flight |
| Branded splash / onboarding art | shape-morph art (§4.3) | `LoadingIndicator` scaled to 180dp |

Two hard rules: **never put a shimmer skeleton and a spinner on the same screen at the same time**,
and **"only one type should represent each kind of activity in an app"** — don't use a linear bar for
network fetches on one screen and a circular spinner for the same class of fetch on another.

## 4.2 `ContainedLoadingIndicator` as a real content state

`LoadingIndicator` and `ContainedLoadingIndicator` are **still `@ExperimentalMaterial3ExpressiveApi`
at alpha26** — the promotion was reverted in alpha19 and never returned. This is the one place where
"helpfully" removing an Expressive opt-in breaks the build.

Use the *contained* form when the indicator sits **on top of content** rather than in cleared space,
or when it must match the visual weight of its sibling states.

`[CORPUS Tomato: .../settingsScreen/screens/backupRestore/BottomSheetTemplate.kt:131-166 —
the clearest justification for contained: the sibling branches are 48dp icon blobs]`

```kotlin
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun BackupStateBadge(state: BackupRestoreState) {
    AnimatedContent(
        targetState = state,
        transitionSpec = {
            fadeIn(MaterialTheme.motionScheme.defaultEffectsSpec())
                .togetherWith(fadeOut(MaterialTheme.motionScheme.fastEffectsSpec()))
        },
        label = "backupState",
    ) { s ->
        when (s) {
            BackupRestoreState.CHOOSE_FILE ->
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .background(MaterialTheme.colorScheme.onSurfaceVariant, MaterialTheme.shapes.extraLarge)
                        .size(48.dp),
                ) {
                    Icon(
                        Icons.Rounded.Folder,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.surfaceVariant,
                        modifier = Modifier.size(28.dp),
                    )
                }

            // Its built-in container matches the 48dp blobs exactly. That match is the reason
            // to choose contained over uncontained here.
            BackupRestoreState.LOADING -> ContainedLoadingIndicator()

            BackupRestoreState.DONE ->
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .background(MaterialTheme.colorScheme.onPrimaryContainer, MaterialTheme.shapes.extraLarge)
                        .size(48.dp),
                ) {
                    Icon(
                        Icons.Rounded.Check,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.surfaceVariant,
                        modifier = Modifier.size(28.dp),
                    )
                }
        }
    }
}
```

**In-button loading** — swap the icon for a 24dp `ContainedLoadingIndicator` *in place*, keep the
label, and put `.animateContentSize()` on the `Button` so the width change is animated rather than a
jump. Derive the indicator's colours from the button's own `ButtonColors`
(`containerColor = colors.containerColor`, `indicatorColor = colors.contentColor`) so a caller
override is inherited. Complete code:
`${CLAUDE_PLUGIN_ROOT}/skills/m3-expressive-components/references/progress-and-loading.md` §2, "In-button loading".

24dp is *below* the indicator's default and does cost morph legibility. It gets away with it because
the shape is small, brief and beside a text label that carries the meaning. Do not generalise it to
a standalone page-level spinner — the seven-shape morph is illegible at 16dp, and below ~36dp use a
plain `CircularProgressIndicator`.

## 4.3 Shape-morph loading art

For branded splash/onboarding art rather than a spinner. Two production approaches, both already
written out in full elsewhere — the value here is knowing which to reach for.

**A. Morph one shape and rotate it.** `[CORPUS LastChat: .../ui/hooks/AvatarShape.kt]`, reproduced
complete in `${CLAUDE_PLUGIN_ROOT}/skills/m3-expressive-shapes/references/morph-recipes.md` §2.1. Copy it from there rather than
adapting §1.3's compact `MorphShape`: LastChat's version recomputes bounds per frame, which handles
the fact that **an intermediate morph is not guaranteed normalized even when both endpoints are**.
Use `Cookie6Sided ↔ Circle` (LastChat's pair) — well-behaved feature mapping. `Cookie12Sided ↔
Triangle` self-intersects mid-morph.

**B. Counter-rotating tinted cluster.** `[CORPUS vivi-music: .../WelcomeActivity.kt]`, reproduced in
`morph-recipes.md` §7.2. Three copies of one polygon on `rememberInfiniteTransition`, at **25s / 30s
/ 20s with one reversed** — non-harmonic periods on purpose, because equal periods produce a visibly
repeating pose. Tint with container roles (`primaryContainer` / `secondaryContainer` /
`tertiaryContainer`) so it stays on-theme under dynamic colour. vivi-music uses a hand-drawn
10-sided-cookie drawable because `Cookie10Sided` **does not exist** — substitute
`MaterialShapes.Cookie9Sided.toShape()`, hoisted once.

Whichever you pick: an infinite transition **keeps the frame pump alive even off-screen**, so never
leave it composed behind a navigated-away destination, and gate it on the reduce-animations setting —
auto-moving content lasting more than five seconds must be pausable, stoppable or hideable. Give the
container one `contentDescription = "Loading"` and a single `liveRegion = LiveRegionMode.Polite`;
the individual blobs get nothing.

## 4.4 Skeletons that do not shift the layout

Material ships no skeleton component. The corpus answer is
`com.valentinilk.shimmer:compose-shimmer:1.5.0` `[CORPUS vivi-music: gradle/libs.versions.toml]`,
wrapped in layout-matched placeholder composables under `ui/component/shimmer/`.

`[COMPOSED — placeholder structure from vivi-music's `ListItemPlaceholder.kt` idiom]`

```kotlin
@Composable
fun TrackRowSkeleton(shape: Shape, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Match the REAL content's shape. If the loaded thumbnail is a Cookie4Sided,
        // the placeholder must be too — that is where the shape system meets loading.
        Spacer(
            Modifier
                .size(48.dp)
                .clip(shape)
                .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f))
        )
        Spacer(Modifier.width(12.dp))
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            // Exact dimensions of the real text block, so nothing moves on swap.
            Spacer(Modifier.height(16.dp).width(180.dp)
                .clip(MaterialTheme.shapes.small)
                .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f)))
            Spacer(Modifier.height(12.dp).width(120.dp)
                .clip(MaterialTheme.shapes.small)
                .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f)))
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun TrackList(state: ListUiState) {
    // Hoist the polygon shape ONCE. toShape() allocates a Path; calling it inside items { }
    // is the classic lazy-list jank source.
    val thumbShape = MaterialShapes.Cookie4Sided.toShape()

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .semantics { if (state.isFirstLoad) liveRegion = LiveRegionMode.Polite }
    ) {
        if (state.isFirstLoad) {
            // Skeleton for FIRST paint of a known layout.
            items(count = 8, key = { "skeleton-$it" }) {
                TrackRowSkeleton(shape = thumbShape, modifier = Modifier.shimmer())
            }
        } else {
            items(state.tracks, key = { it.id }) { track ->
                TrackRow(track = track, thumbShape = thumbShape)
            }
            // LoadingIndicator for the PAGINATION footer of the same list.
            if (state.hasMore) {
                item(key = "paging") {
                    Box(
                        Modifier.fillMaxWidth().padding(32.dp),
                        contentAlignment = Alignment.Center,
                    ) { LoadingIndicator() }
                }
            }
        }
    }
}
```

That split — skeleton for first paint, `LoadingIndicator` for the footer — is exactly what
vivi-music ships, and it is the right division: a skeleton says "content *of this shape* is coming
here"; a spinner says "the app is working".

## 4.5 Buffering is not loading

A media transport that shows a spinner in place of the play button while buffering is a common and
wrong pattern: it removes the control the user is trying to press, and it re-announces on every
buffer event. Buffering is a **transient property of the seek bar**, not a state of the screen.

`[COMPOSED]`

```kotlin
// In the seek bar's track slot, when buffering: keep the wave, drop the amplitude to zero and
// overlay a subtle indeterminate bar. The play button never changes.
if (isBuffering) {
    LinearProgressIndicator(
        modifier = Modifier.fillMaxWidth().height(2.dp).align(Alignment.BottomCenter),
        color = colors.activeTrackColor.copy(alpha = 0.6f),
        trackColor = Color.Transparent,
    )
}
```

Announce it once, politely, on the container — not on the button:

```kotlin
Modifier.semantics {
    if (isBuffering) {
        liveRegion = LiveRegionMode.Polite
        stateDescription = "Buffering"
    }
}
```

## 4.6 Accessibility block — loading states

- **Determinate vs indeterminate is reported to assistive tech.** Choosing wrong misreports state. A
  determinate bar animating 0→90% on a fixed timer and then hanging is worse than an indeterminate
  one: it makes a false promise.
- **Give long determinate work a live-region text label** ("Downloading, 42 percent") rather than
  relying on the visual fill. Throttle it the same way §2.3 throttles the timer — announce at 10%
  steps, not per frame.
- **A spinner must not be a live region that fires repeatedly.** Set `contentDescription = "Loading"`
  and `liveRegion = Polite` **once, on the container that appears/disappears**, so it announces on
  entry and on completion and never in between.
- **Skeleton rows must not be announced individually.** Eight "loading" nodes is eight stops. Put
  `clearAndSetSemantics {}` on each placeholder row and one polite live region on the list container.
- **Track gap (4dp) and stop indicator (4dp) exist for contrast**, not decoration — the same reason
  the slider carries a 6dp thumb–track gap. On alpha25+ you can name
  `WavyProgressIndicatorDefaults.LinearIndicatorTrackGapSize` / `CircularIndicatorTrackGapSize` /
  `LinearTrackStopIndicatorSize` with no opt-in, so there is no reason left to hardcode or drop them.
- **If the user can navigate away during the wait, do not block the UI behind a modal scrim.** The
  loading indicator's documented job includes "indicating whether users can navigate away".
- **Content that moves for more than five seconds must be pausable, stoppable or hideable.** A long
  indeterminate animation should at minimum ship with a cancel affordance.

---

# 5. Cross-references

| You are building | Start at | Then read |
| --- | --- | --- |
| Any wavy indicator | §1.2 here | `${CLAUDE_PLUGIN_ROOT}/skills/m3-expressive-components/references/progress-and-loading.md` §3 |
| A custom slider look | §1.2 here | `sliders-and-inputs.md` §5.3/§5.4 — customise the **slot**, not the component |
| A shape that changes with state | §1.3 here | `${CLAUDE_PLUGIN_ROOT}/skills/m3-expressive-shapes/references/morph-recipes.md` §2 (two full implementations) |
| Anything with springs | the spec choices annotated throughout | `${CLAUDE_PLUGIN_ROOT}/skills/m3-expressive-motion/references/motion-scheme.md` §6 (spatial vs effects) |
| A list/detail or two-pane player | §1.9's `when` chain | `${CLAUDE_PLUGIN_ROOT}/skills/m3-adaptive/references/pane-scaffolds.md` |
| A list, detail, feed, or adaptive pair | — | `references/worked-screens-list-detail-feed.md` (sibling file) |
| Settings, forms, chat, search | — | `references/worked-screens-forms-settings-chat.md` (sibling file) |

**The five mistakes this file exists to prevent**

1. A wavy seek bar that TalkBack cannot operate, because gestures were rebuilt on `pointerInput`
   instead of replacing the `Slider`'s `track` slot (§1.2).
2. `Stroke(width = 16f)` — pixels, not dp — on a wavy indicator (§1.2, §2.2).
3. `ToggleButtonDefaults.shapes(...)`, which is `DeprecationLevel.HIDDEN` and fails to compile on
   alpha25+ (§1.5).
4. A `Polite` live region on a per-second value, which makes a timer unusable with a screen reader
   (§2.3).
5. Spending the hero budget on three unrelated elements instead of one moment stated three ways
   (§3.1).

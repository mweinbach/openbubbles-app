# M3 Expressive Sliders & Input Components

Sliders (including four complete custom wavy/squiggly/slim implementations), text fields, time
pickers, and the selection controls.

Confidence markers used below:
- **[verified]** — signature or value read from material3 source / API listing.
- **[from-corpus]** — verbatim from a shipping app in the reference set (vivi-music, Tomato, Med,
  LastChat). Paths given above each excerpt.
- **[canonical-form]** — API shape is right, exact signature not readable from source. Compile-check.
- **[judgment]** — practical guidance, not an API fact.

---

## 1. Slider — status and signatures

**[verified]** Slider experimental APIs were **promoted to stable in 1.5.0-alpha16**. Baseline
`Slider` / `RangeSlider` overloads are unchanged. What remains gated:

| API | Gate at alpha26 |
| --- | --- |
| `Slider`, `RangeSlider`, `SliderState`, `RangeSliderState`, `SliderDefaults.colors/Thumb/Track` | none |
| `VerticalSliderLegacy` (the deprecated `reverseDirection` form) | `ExperimentalMaterial3ExpressiveApi` |
| `SliderDefaults.Thumb(interactionSource, sliderState, …)` (deprecated overload) | `ExperimentalMaterial3ExpressiveApi` |

alpha19 fixed slider padding being applied from inset focus rings when not focused **[verified]** —
if a slider on an older alpha looks inset by a few dp for no reason, that is it.

### 1.0 Breaking change in alpha25 — `SliderState` is no longer a `DraggableState`

**[verified — release note I7c91b]** *"Deprecated 2-parameter `SliderState.Saver` and
`RangeSliderState.Saver` in favor of new overloads taking steps. **`SliderState` no longer publicly
implements `DraggableState`.**"*

Two separate items in one change:

| Change | Level | Source-breaking? |
| --- | --- | --- |
| `SliderState : DraggableState` removed from the public API | removed outright | **Yes, if you relied on it** |
| 2-param `SliderState.Saver(a, b)` / `RangeSliderState.Saver(a, b)` | deprecated | No — warns |

**Who this breaks.** Anything that treated a `SliderState` as a drag source: passing it to
`Modifier.draggable(state = sliderState, …)`, calling `sliderState.drag { … }` or
`dispatchRawDelta(…)`, or holding one in a `DraggableState`-typed variable. There is **no
documented replacement** — drive the slider through its own `value` / `onValueChange` /
`onValueChangeFinished` surface (§3.2 shows the correct shape) rather than through a drag state.

This matters more than it looks for the custom sliders in §5: those are all hand-rolled on
`pointerInput` + `detectDragGestures`, **not** on `SliderState`-as-`DraggableState`, so they are
unaffected. Verify before assuming the same of your own code.

**The `Saver` deprecation.** The replacements take a `steps` argument. The **exact new signatures
are UNVERIFIED** — the deprecation is confirmed from the release note, the replacement parameter
lists were not read from source. Do not write them from memory; let the IDE's `ReplaceWith` fill
them in, or read `Slider.kt` at your pin.

alpha26 also landed an external contribution (I97a6d, b/533483487): *"Fixed Material3 `Slider`
`Label` not showing while dragging the slider by its thumb with a mouse."* Behavioural only, no API
change — but if you suppressed a slider label on desktop/ChromeOS because it was broken, re-test.

### 1.1 Vertical sliders **[verified]**

```kotlin
@Deprecated(
    "Maintained for binary compatibility. Use the version with topToBottom instead.",
    replaceWith = ReplaceWith(
        "VerticalSlider(state, modifier, enabled, !reverseDirection, colors, " +
            "interactionSource, thumb, track)"
    ),
    level = DeprecationLevel.WARNING,
)
@ExperimentalMaterial3ExpressiveApi
@JvmName("VerticalSlider")
@Composable
fun VerticalSliderLegacy(
    state: SliderState,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    reverseDirection: Boolean = false,
    colors: SliderColors = SliderDefaults.colors(),
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
    thumb: @Composable (SliderState) -> Unit = { _ ->
        SliderDefaults.Thumb(
            interactionSource = interactionSource,
            isVertical = true,
            colors = colors,
            enabled = enabled,
            thumbSize = VerticalThumbSize,
        )
    },
    track: @Composable (SliderState) -> Unit = { sliderState ->
        SliderDefaults.Track(
            colors = colors,
            enabled = enabled,
            sliderState = sliderState,
            trackCornerSize = Dp.Unspecified,
        )
    },
)

@JvmName("VerticalSliderNew")
@Composable
fun VerticalSlider(
    state: SliderState,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    topToBottom: Boolean = true,
    colors: SliderColors = SliderDefaults.colors(),
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
    thumb: @Composable (SliderState) -> Unit = { /* as above */ },
    track: @Composable (SliderState) -> Unit = { /* as above */ },
)
```

**Pitfall:** `reverseDirection` became **`topToBottom` with inverted meaning**
(`topToBottom = !reverseDirection`). Migrating without flipping the boolean silently reverses the
slider.

`VerticalSlider` takes a **`SliderState`**, not a `value`/`onValueChange` pair. Vertical sliders are
used by nothing in the corpus — the only vertical-slider-shaped thing anyone ships is a custom
volume control.

### 1.2 `SliderDefaults.Thumb` **[verified]**

```kotlin
@Composable
fun Thumb(
    interactionSource: MutableInteractionSource,
    modifier: Modifier = Modifier,
    colors: SliderColors = colors(),
    enabled: Boolean = true,
    thumbSize: DpSize = ThumbSize,
)

@Composable
fun Thumb(
    interactionSource: MutableInteractionSource,
    isVertical: Boolean,
    modifier: Modifier = Modifier,
    colors: SliderColors = colors(),
    enabled: Boolean = true,
    thumbSize: DpSize = if (isVertical) VerticalThumbSize else ThumbSize,
)
```

The Expressive thumb is a **narrow vertical capsule**, not a circle, and `thumbSize` is a `DpSize`
so width and height are independent. A deprecated `Thumb(interactionSource, sliderState, …)`
overload remains, gated `@ExperimentalMaterial3ExpressiveApi`.

### 1.3 `SliderDefaults.Track` and `CenteredTrack` **[verified]**

```kotlin
@Composable
fun Track(
    sliderState: SliderState,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    colors: SliderColors = colors(),
    drawStopIndicator: (DrawScope.(Offset) -> Unit)? = {
        drawStopIndicator(
            offset = it,
            color = colors.trackColor(enabled, active = true),
            size = TrackStopIndicatorSize,
        )
    },
    drawTick: DrawScope.(Offset, Color) -> Unit = { offset, color ->
        drawStopIndicator(offset = offset, color = color, size = TickSize)
    },
    thumbTrackGapSize: Dp = ThumbTrackGapSize,
    trackInsideCornerSize: Dp = TrackInsideCornerSize,
)

@Composable
fun Track(
    sliderState: SliderState,
    trackCornerSize: Dp,          // <- outer corner radius, added for expressive
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    colors: SliderColors = colors(),
    drawStopIndicator: (DrawScope.(Offset) -> Unit)? = { /* … */ },
    drawTick: DrawScope.(Offset, Color) -> Unit = { /* … */ },
    thumbTrackGapSize: Dp = ThumbTrackGapSize,
    trackInsideCornerSize: Dp = TrackInsideCornerSize,
)

@Composable
fun CenteredTrack(                // <- fills outward from the centre
    sliderState: SliderState,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    colors: SliderColors = colors(),
    drawStopIndicator: (DrawScope.(Offset) -> Unit)? = { /* … */ },
    drawTick: DrawScope.(Offset, Color) -> Unit = { /* … */ },
    thumbTrackGapSize: Dp = ThumbTrackGapSize,
    trackInsideCornerSize: Dp = TrackInsideCornerSize,
    trackCornerSize: Dp = Dp.Unspecified,
)
```

Matching `RangeSliderState` overloads exist with the same parameter sets.

Constants referenced: `ThumbSize`, `VerticalThumbSize`, `TrackStopIndicatorSize`, `TickSize`,
`ThumbTrackGapSize`, `TrackInsideCornerSize` — **dp values UNVERIFIED in source**, but the design
spec gives them (§2).

Related, and relevant because §5.1's `WavySlider` reads them: the **wavy** counterparts
`WavyProgressIndicatorDefaults.LinearTrackStopIndicatorSize`, `LinearIndicatorTrackGapSize` and
`CircularIndicatorTrackGapSize` were **promoted to stable in 1.5.0-alpha25** (I794d0)
**[verified]** — they no longer require any opt-in. Full treatment in
`m3-expressive-components/references/progress-and-loading.md`.

### 1.4 Colors **[verified]**

```kotlin
@Composable fun colors() = MaterialTheme.colorScheme.defaultSliderColors
@Composable fun colors(
    thumbColor: Color = Color.Unspecified,
    activeTrackColor: Color = Color.Unspecified,
    activeTickColor: Color = Color.Unspecified,
    inactiveTrackColor: Color = Color.Unspecified,
    inactiveTickColor: Color = Color.Unspecified,
    disabledThumbColor: Color = Color.Unspecified,
    disabledActiveTrackColor: Color = Color.Unspecified,
    disabledActiveTickColor: Color = Color.Unspecified,
    disabledInactiveTrackColor: Color = Color.Unspecified,
    disabledInactiveTickColor: Color = Color.Unspecified,
): SliderColors
```

Unset values fall through to the theme — pass only what you override.

---

## 2. The expressive slider look

**[verified — material-components-android Slider.md]**

- **Renaming:** "Changed continuous slider to **standard slider**"; "The discrete slider is now the
  **stops configuration**." So "discrete slider" is dead vocabulary; it is `steps` on a standard
  slider.
- **Five sizes** — XS (default), S, M, L, XL — differing in **track thickness and corner size**.
  Stop indicators and handles scale proportionally.
- **Orientation:** horizontal *and* vertical.
- **Optional inset icons** — standard sliders only.
- **Centered** variant when the default/zero value sits at the midpoint (positive and negative
  ranges) → `SliderDefaults.CenteredTrack`.
- **Accessibility-driven spec numbers**, added explicitly "to meet modern contrast requirements":
  **thumb–track gap 6dp, track inside corner 2dp, stop indicator 4dp.**

Those three numbers are the recipe. A slider with a fat track, a 6dp gap punched out around a narrow
capsule thumb, 2dp inner corners and a 4dp stop dot at the far end *is* the Expressive slider. §5.3
implements exactly that spec.

**When to use which [verified]:** standard = pick a single value from a range; range = a min/max
pair; centered = the neutral value is the midpoint.

---

## 3. `SliderState` — the two recipes worth knowing

`Slider` has a `value`/`onValueChange` overload and a `state: SliderState` overload. Use `SliderState`
when you need to know about dragging (`isDragging`) or to animate the value independently of user
input.

### 3.1 Animate the value, but not while dragging

**[from-corpus]**
`/root/work/repos/Tomato/shared/src/commonMain/kotlin/org/nsh07/pomodoro/ui/settingsScreen/components/SliderListItem.kt`
(complete component, abridged to the mechanism):

```kotlin
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun SliderListItem(
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    enabled: Boolean,
    label: String,
    trailingLabel: (Float) -> String,
    shape: CornerBasedShape,
    modifier: Modifier = Modifier,
    icon: (@Composable () -> Unit)? = null,
    trailingIcon: (@Composable () -> Unit)? = null,
    containerColor: Color = listItemColors.containerColor,
    onValueChangeFinished: (Float) -> Unit,
) {
    var animateSliderValue by remember { mutableStateOf(true) }

    var value by remember(value) { mutableFloatStateOf(value) }
    val valueAnimated by animateFloatAsState(
        value,
        animationSpec = if (animateSliderValue) motionScheme.defaultSpatialSpec()
        else snap()
    )

    Column(modifier.background(containerColor, shape)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(top = 16.dp, start = 16.dp, end = 16.dp)
        ) {
            if (icon != null) {
                CompositionLocalProvider(LocalContentColor provides colorScheme.onSurfaceVariant) {
                    icon()
                }
                Spacer(Modifier.width(16.dp))
            }
            Text(label, style = typography.bodyLarge)
            Spacer(Modifier.weight(1f))
            Text(
                trailingLabel(value),
                style = typography.labelMedium,
                color = colorScheme.onSurfaceVariant
            )
        }
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(start = 20.dp, end = 20.dp, bottom = 8.dp)
        ) {
            if (icon != null) {
                Spacer(Modifier.width(36.dp))
            }
            Slider(
                value = valueAnimated,
                onValueChange = {
                    animateSliderValue = false
                    value = it
                },
                onValueChangeFinished = {
                    animateSliderValue = true
                    onValueChangeFinished(value)
                },
                valueRange = valueRange,
                enabled = enabled,
                modifier = Modifier.weight(1f)
            )
            if (trailingIcon != null) {
                Spacer(Modifier.width(20.dp))
                trailingIcon()
            }
        }
    }
}
```

The technique: an `animateFloatAsState` whose spec **flips between `motionScheme.defaultSpatialSpec()`
and `snap()`**. While the user drags, `snap()` means the thumb tracks the finger with zero lag; when
an external source changes the value (a reset button, a preset), the spring animates it. One boolean.

Note also: this is a slider **inside** a segmented list container — it takes the group's `shape` and
`containerColor` so it slots into a settings list next to `SegmentedListItem`s. That is the standard
way to put a slider in an Expressive settings screen.

### 3.2 Settle a slider with `fastEffectsSpec` after the drag ends

**[from-corpus]**
`/root/work/repos/vivi-music/app/src/main/kotlin/com/music/vivi/vivimusic/AudioDeviceBottomSheet.kt`:

```kotlin
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun VolumeControlRow(
    label: String,
    icon: ImageVector,
    volume: Float,
    maxVolume: Int,
    onVolumeChange: (Float) -> Unit,
    onDragStart: () -> Unit = {},
    onDragEnd: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val coroutineScope = rememberCoroutineScope()
    val sliderState = rememberSliderState(
        valueRange = 0f..maxVolume.toFloat(),
    )

    val snapAnimationSpec = MaterialTheme.motionScheme.fastEffectsSpec<Float>()
    var currentValue by rememberSaveable { mutableFloatStateOf(volume) }
    var animateJob: Job? by remember { mutableStateOf(null) }

    LaunchedEffect(volume) {
        if (!sliderState.isDragging) {
            currentValue = volume
            sliderState.value = volume
        }
    }

    sliderState.onValueChange = { newValue ->
        currentValue = newValue
        if (sliderState.isDragging) {
            onDragStart()
            animateJob?.cancel()
            sliderState.value = newValue
            onVolumeChange(newValue)
        }
    }

    sliderState.onValueChangeFinished = {
        animateJob = coroutineScope.launch {
            animate(
                initialValue = sliderState.value,
                targetValue = currentValue,
                animationSpec = snapAnimationSpec
            ) { value, _ ->
                sliderState.value = value
            }
        }
        onDragEnd()
    }
```

`SliderState` exposes `isDragging`, and `onValueChange` / `onValueChangeFinished` are **assignable
properties**, not constructor params. The `LaunchedEffect(volume) { if (!isDragging) … }` guard is
what stops an external volume update from yanking the thumb out from under the user's finger.

`isDragging`, `value`, `onValueChange` and `onValueChangeFinished` are **all still public at
alpha26** — the alpha25 change (§1.0) removed only the `DraggableState` *implementation*, not any of
these members. This recipe is unaffected and remains the correct way to drive a `SliderState`
programmatically.

**[judgment]** `fastEffectsSpec` (no overshoot) is correct here — the settle is a non-spatial
correction. Use a spatial spec only when the thumb is travelling a visible distance.

---

## 4. The three-way `SliderStyle` switch

vivi-music makes the seek-bar style a user preference across three implementations. This is the
frame the four custom components below plug into.

**[from-corpus]** `/root/work/repos/vivi-music/app/src/main/kotlin/com/music/vivi/constants/PreferenceKeys.kt`:

```kotlin
enum class SliderStyle {
    DEFAULT,
    WAVY,
    SLIM
}
```

**[from-corpus]** `/root/work/repos/vivi-music/app/src/main/kotlin/com/music/vivi/ui/player/Player.kt`
(imports, prefs, and the seek-bar `when`, abridged):

```kotlin
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import com.music.vivi.constants.SliderStyle
import com.music.vivi.constants.SliderStyleKey
import com.music.vivi.constants.SquigglySliderKey
import com.music.vivi.ui.component.PlayerSliderTrack
import com.music.vivi.ui.component.SquigglySlider
import com.music.vivi.ui.component.WavySlider
import com.music.vivi.ui.component.VolumeSlider
import com.music.vivi.ui.theme.PlayerSliderColors
```

```kotlin
    val sliderStyle by rememberEnumPreference(SliderStyleKey, SliderStyle.SLIM)
    val squigglySlider by rememberPreference(SquigglySliderKey, defaultValue = false)
```

```kotlin
            when (sliderStyle) {
                SliderStyle.DEFAULT -> {
                    Slider(
                        value = (sliderPosition ?: effectivePosition).toFloat(),
                        valueRange = 0f..(if (duration == C.TIME_UNSET) 0f else duration.toFloat()),
                        onValueChange = { sliderPosition = it.toLong() },
                        onValueChangeFinished = {
                            sliderPosition?.let { playerConnection.player.seekTo(it); position = it }
                            sliderPosition = null
                        },
                        colors = PlayerSliderColors.getSliderColors(…),
                        modifier = Modifier.padding(horizontal = PlayerHorizontalPadding),
                    )
                }

                SliderStyle.WAVY -> {
                    if (squigglySlider) {
                        SquigglySlider(
                            value = (sliderPosition ?: effectivePosition).toFloat(),
                            valueRange = 0f..(if (duration == C.TIME_UNSET) 0f else duration.toFloat()),
                            onValueChange = { sliderPosition = it.toLong() },
                            onValueChangeFinished = { /* seek */ },
                            modifier = Modifier.padding(horizontal = PlayerHorizontalPadding),
                            colors = PlayerSliderColors.getSliderColors(…),
                            isPlaying = effectiveIsPlaying,
                        )
                    } else {
                        WavySlider(
                            value = (sliderPosition ?: effectivePosition).toFloat(),
                            valueRange = 0f..(if (duration == C.TIME_UNSET) 0f else duration.toFloat()),
                            onValueChange = { sliderPosition = it.toLong() },
                            onValueChangeFinished = { /* seek */ },
                            colors = PlayerSliderColors.getSliderColors(…),
                            modifier = Modifier.padding(horizontal = PlayerHorizontalPadding),
                            isPlaying = effectiveIsPlaying
                        )
                    }
                }

                SliderStyle.SLIM -> {
                    val trackInteractionSource = remember { MutableInteractionSource() }
                    val isTrackDragged by trackInteractionSource.collectIsDraggedAsState()
                    val isTrackPressed by trackInteractionSource.collectIsPressedAsState()
                    val isTrackActive = (isTrackDragged || isTrackPressed) && !useNewPlayerDesign

                    val trackHeight by animateDpAsState(
                        targetValue = if (isTrackActive) 16.dp else 10.dp,
                        animationSpec = spring(
                            dampingRatio = Spring.DampingRatioMediumBouncy,
                            stiffness = Spring.StiffnessMedium
                        ),
                        label = "trackHeight"
                    )

                    Slider(
                        value = (sliderPosition ?: effectivePosition).toFloat(),
                        valueRange = 0f..(if (duration == C.TIME_UNSET) 0f else duration.toFloat()),
                        onValueChange = { sliderPosition = it.toLong() },
                        onValueChangeFinished = { /* seek */ },
                        interactionSource = trackInteractionSource,
                        thumb = { Spacer(modifier = Modifier.size(0.dp)) },
                        track = { sliderState ->
                            PlayerSliderTrack(
                                sliderState = sliderState,
                                trackHeight = trackHeight,
                                colors = PlayerSliderColors.getSliderColors(…)
                            )
                        },
                        modifier = Modifier.padding(horizontal = PlayerHorizontalPadding)
                    )
                }
            }
```

Three patterns worth extracting:

1. **`sliderPosition: Long?` as a nullable drag override.** While dragging it holds the pending
   value; on finish it seeks and resets to `null`, and the displayed value falls back to the real
   playback position. This is the correct way to build any slider over a value that also changes on
   its own.
2. **`thumb = { Spacer(Modifier.size(0.dp)) }`** — the supported way to delete the thumb and let a
   custom track own the whole visual. Do not try to hide it with alpha.
3. **A shared `interactionSource`** passed to `Slider` and read with `collectIsDraggedAsState()` /
   `collectIsPressedAsState()` drives the track's grow-on-touch spring. This is the Expressive
   "component responds physically to touch" idea implemented on a component that doesn't ship it.

---

## 5. Four complete custom sliders (vivi-music, verbatim)

### 5.1 `WavySlider` — built on `LinearWavyProgressIndicator`

**The single most valuable pattern here**: rather than hand-drawing a wave, drive the real
`LinearWavyProgressIndicator` and paint a thumb over it. You get the platform's wave geometry,
amplitude animation and stop indicator for free.

**[from-corpus]** `/root/work/repos/vivi-music/app/src/main/kotlin/com/music/vivi/ui/component/WavySlider.kt`
(complete file, 153 lines):

```kotlin
package com.music.vivi.ui.component

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.ProgressIndicatorDefaults
import androidx.compose.material3.SliderColors
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.WavyProgressIndicatorDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun WavySlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    valueRange: ClosedFloatingPointRange<Float> = 0f..1f,
    onValueChangeFinished: (() -> Unit)? = null,
    colors: SliderColors = SliderDefaults.colors(),
    isPlaying: Boolean = true,
    enabled: Boolean = true,
    strokeWidth: Dp = 4.dp,
    thumbRadius: Dp = 8.dp,
    wavelength: Dp = WavyProgressIndicatorDefaults.LinearDeterminateWavelength,
    waveSpeed: Dp = wavelength
) {
    val density = LocalDensity.current
    val strokeWidthPx = with(density) { strokeWidth.toPx() }
    val thumbRadiusPx = with(density) { thumbRadius.toPx() }
    val stroke = remember(strokeWidthPx) { 
        Stroke(width = strokeWidthPx, cap = StrokeCap.Round) 
    }
    
    val normalizedValue = ((value - valueRange.start) / (valueRange.endInclusive - valueRange.start))
        .coerceIn(0f, 1f)
    
    var isDragging by remember { mutableStateOf(false) }
    var dragValue by remember { mutableFloatStateOf(normalizedValue) }
    
    val displayValue = if (isDragging) dragValue else normalizedValue
    
    val animatedAmplitude by animateFloatAsState(
        targetValue = if (isPlaying) 1f else 0f,
        animationSpec = ProgressIndicatorDefaults.ProgressAnimationSpec,
        label = "amplitude"
    )
    
    val activeColor = colors.activeTrackColor
    val inactiveColor = colors.inactiveTrackColor
    val thumbColor = colors.thumbColor
    
    // Calculate container height to accommodate thumb
    val containerHeight = maxOf(WavyProgressIndicatorDefaults.LinearContainerHeight, thumbRadius * 2)
    
    val baseModifier = modifier
        .fillMaxWidth()
        .height(containerHeight)

    val interactiveModifier = if (enabled) {
        baseModifier
            .pointerInput(valueRange) {
                detectTapGestures { offset ->
                    val newValue = (offset.x / size.width).coerceIn(0f, 1f)
                    val mappedValue = valueRange.start + newValue * (valueRange.endInclusive - valueRange.start)
                    onValueChange(mappedValue)
                    onValueChangeFinished?.invoke()
                }
            }
            .pointerInput(valueRange) {
                detectHorizontalDragGestures(
                    onDragStart = { offset ->
                        isDragging = true
                        dragValue = (offset.x / size.width).coerceIn(0f, 1f)
                        val mappedValue = valueRange.start + dragValue * (valueRange.endInclusive - valueRange.start)
                        onValueChange(mappedValue)
                    },
                    onDragEnd = {
                        isDragging = false
                        onValueChangeFinished?.invoke()
                    },
                    onDragCancel = {
                        isDragging = false
                    },
                    onHorizontalDrag = { _, dragAmount ->
                        dragValue = (dragValue + dragAmount / size.width).coerceIn(0f, 1f)
                        val mappedValue = valueRange.start + dragValue * (valueRange.endInclusive - valueRange.start)
                        onValueChange(mappedValue)
                    }
                )
            }
    } else {
        baseModifier
    }

    Box(
        modifier = interactiveModifier,
        contentAlignment = Alignment.Center
    ) {
        LinearWavyProgressIndicator(
            progress = { displayValue },
            modifier = Modifier.fillMaxWidth(),
            color = activeColor,
            trackColor = inactiveColor,
            stroke = stroke,
            trackStroke = stroke,
            gapSize = thumbRadius + 4.dp,
            stopSize = WavyProgressIndicatorDefaults.LinearTrackStopIndicatorSize,
            amplitude = { progress -> if (progress > 0f) animatedAmplitude else 0f },
            wavelength = wavelength,
            waveSpeed = waveSpeed
        )
        
        // Draw circular thumb - synced with progress indicator position
        Canvas(modifier = Modifier.fillMaxSize()) {
            val thumbX = size.width * displayValue
            val thumbY = size.height / 2
            
            drawCircle(
                color = thumbColor,
                radius = thumbRadiusPx,
                center = Offset(thumbX, thumbY)
            )
        }
    }
}
```

**Technique notes:**
- `gapSize = thumbRadius + 4.dp` carves a hole in the track exactly where the custom thumb sits, so
  the thumb doesn't sit *on* the wave — it sits in a gap in it. This is the whole trick.
- `amplitude = { progress -> if (progress > 0f) animatedAmplitude else 0f }` flattens the wave at
  position 0 **and** whenever playback pauses (via `animatedAmplitude`). Wave amplitude carrying
  play/pause state is the expressive payload; a wave that keeps waving while paused is a bug.
- `ProgressIndicatorDefaults.ProgressAnimationSpec` for the amplitude transition — reuse the
  platform spec rather than inventing a duration.
- `strokeWidth` is `Dp` here and converted with `toPx()`. `LinearWavyProgressIndicator` takes
  `Stroke` in **pixels** while `wavelength` / `gapSize` are `Dp`. Mixing those up is the classic
  wavy-indicator bug.
- The container height is `maxOf(LinearContainerHeight, thumbRadius * 2)` — never let the thumb be
  clipped by the indicator's natural height.
- `remember(strokeWidthPx) { Stroke(...) }` — `Stroke` allocates; don't rebuild it per frame.

**Pitfall:** this component reimplements gestures with `pointerInput`, so it has **no slider
semantics**. See §9 — add `Modifier.semantics { progressBarRangeInfo = … ; setProgress { … } }` or
wrap it, or TalkBack users cannot operate it.

### 5.2 `SquigglySlider` — pure Canvas, no material3 API

A hand-rolled squiggly seek bar (ported from mpvEx). It uses **no** material3 drawing API — pure
`Canvas` + `Path` cubics with a frame-driven phase offset, an amplitude transition region, and a
vertical-bar thumb. Included because it is what people built *before*
`LinearWavyProgressIndicator`, and because the amplitude-transition maths is genuinely useful.

**[judgment] Prefer §5.1.** Reach for this only if you need a wave shape the platform indicator
cannot express (per-pixel amplitude ramp, custom cap behaviour).

**[from-corpus]** `/root/work/repos/vivi-music/app/src/main/kotlin/com/music/vivi/ui/component/SquigglySlider.kt`
(complete file, 307 lines):

```kotlin
package com.music.vivi.ui.component

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.SliderColors
import androidx.compose.material3.SliderDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

@Composable
fun SquigglySlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    valueRange: ClosedFloatingPointRange<Float> = 0f..1f,
    onValueChangeFinished: (() -> Unit)? = null,
    colors: SliderColors = SliderDefaults.colors(),
    isPlaying: Boolean = true,
) {
    val primaryColor = colors.activeTrackColor
    val inactiveColor = colors.inactiveTrackColor

    var isDragging by remember { mutableStateOf(false) }
    var dragPosition by remember { mutableFloatStateOf(value) }
    
    val currentValue = if (isDragging) dragPosition else value
    val duration = valueRange.endInclusive - valueRange.start
    val position = currentValue - valueRange.start

    // Animation state
    var phaseOffset by remember { mutableFloatStateOf(0f) }
    var heightFraction by remember { mutableFloatStateOf(if (isPlaying) 1f else 0f) }

    val scope = rememberCoroutineScope()

    // Wave parameters
    val waveLength = 80f
    val lineAmplitude = 6f
    val phaseSpeed = 24f // Faster wave movement to match old squiggly
    val transitionPeriods = 1.5f
    val minWaveEndpoint = 0f
    val matchedWaveEndpoint = 1f
    val transitionEnabled = true

    // Animate height fraction based on playing state and dragging state
    LaunchedEffect(isPlaying, isDragging) {
        scope.launch {
            val shouldFlatten = !isPlaying || isDragging
            val targetHeight = if (shouldFlatten) 0f else 1f
            val animDuration = if (shouldFlatten) 150 else 200 // Faster appear/disappear
            val startDelay = if (shouldFlatten) 0L else 30L

            delay(startDelay)

            val animator = Animatable(heightFraction)
            animator.animateTo(
                targetValue = targetHeight,
                animationSpec = tween(
                    durationMillis = animDuration,
                    easing = LinearEasing,
                ),
            ) {
                heightFraction = this.value
            }
        }
    }

    // Animate wave movement only when playing
    LaunchedEffect(isPlaying) {
        if (!isPlaying) return@LaunchedEffect

        var lastFrameTime = withFrameMillis { it }
        while (isActive) {
            withFrameMillis { frameTimeMillis ->
                val deltaTime = (frameTimeMillis - lastFrameTime) / 1000f
                phaseOffset += deltaTime * phaseSpeed
                phaseOffset %= waveLength
                lastFrameTime = frameTimeMillis
            }
        }
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(48.dp)
            .then(
                if (enabled) {
                    Modifier
                        .pointerInput(valueRange) {
                            detectTapGestures { offset ->
                                val newPosition = (offset.x / size.width) * duration
                                val mappedValue = valueRange.start + newPosition.coerceIn(0f, duration)
                                onValueChange(mappedValue)
                                onValueChangeFinished?.invoke()
                            }
                        }
                        .pointerInput(valueRange) {
                            detectDragGestures(
                                onDragStart = { offset ->
                                    isDragging = true
                                    val newPosition = (offset.x / size.width) * duration
                                    dragPosition = valueRange.start + newPosition.coerceIn(0f, duration)
                                    onValueChange(dragPosition)
                                },
                                onDragEnd = {
                                    isDragging = false
                                    onValueChangeFinished?.invoke()
                                },
                                onDragCancel = {
                                    isDragging = false
                                },
                                onDrag = { change, _ ->
                                    change.consume()
                                    val newPosition = (change.position.x / size.width) * duration
                                    dragPosition = valueRange.start + newPosition.coerceIn(0f, duration)
                                    onValueChange(dragPosition)
                                }
                            )
                        }
                } else {
                    Modifier
                }
            ),
        contentAlignment = Alignment.Center
    ) {
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
        ) {
            val strokeWidth = 5.dp.toPx()
            val progress = if (duration > 0f) (position / duration).coerceIn(0f, 1f) else 0f
            val totalWidth = size.width
            val totalProgressPx = totalWidth * progress
            val centerY = size.height / 2f

            // Calculate wave progress
            val waveProgressPx = if (!transitionEnabled || progress > matchedWaveEndpoint) {
                totalWidth * progress
            } else {
                val t = (progress / matchedWaveEndpoint).coerceIn(0f, 1f)
                totalWidth * (minWaveEndpoint + (matchedWaveEndpoint - minWaveEndpoint) * t)
            }

            // Helper function to compute amplitude
            fun computeAmplitude(x: Float, sign: Float): Float {
                return if (transitionEnabled) {
                    val length = transitionPeriods * waveLength
                    val coeff = ((waveProgressPx + length / 2f - x) / length).coerceIn(0f, 1f)
                    sign * heightFraction * lineAmplitude * coeff
                } else {
                    sign * heightFraction * lineAmplitude
                }
            }

            // Build wavy path for played portion
            val path = Path()
            val waveStart = -phaseOffset - waveLength / 2f
            val waveEnd = if (transitionEnabled) totalWidth else waveProgressPx

            path.moveTo(waveStart, centerY)

            var currentX = waveStart
            var waveSign = 1f
            var currentAmp = computeAmplitude(currentX, waveSign)
            val dist = waveLength / 2f

            while (currentX < waveEnd) {
                waveSign = -waveSign
                val nextX = currentX + dist
                val midX = currentX + dist / 2f
                val nextAmp = computeAmplitude(nextX, waveSign)

                path.cubicTo(
                    midX,
                    centerY + currentAmp,
                    midX,
                    centerY + nextAmp,
                    nextX,
                    centerY + nextAmp,
                )

                currentAmp = nextAmp
                currentX = nextX
            }

            // Draw path up to progress position using clipping
            val clipTop = lineAmplitude + strokeWidth

            val disabledAlpha = 77f / 255f
            val inactiveTrackColor = primaryColor.copy(alpha = disabledAlpha)
            val capRadius = strokeWidth / 2f

            fun drawPathSegment(startX: Float, endX: Float, color: Color) {
                if (endX <= startX) return
                clipRect(
                    left = startX,
                    top = centerY - clipTop,
                    right = endX,
                    bottom = centerY + clipTop,
                ) {
                    drawPath(
                        path = path,
                        color = color,
                        style = Stroke(width = strokeWidth, cap = StrokeCap.Round),
                    )
                }
            }

            // Played segment
            drawPathSegment(0f, totalProgressPx, primaryColor)

            // Unplayed segment
            drawPathSegment(totalProgressPx, totalWidth, inactiveTrackColor)

            // Helper function to get wave Y position at any X
            fun getWaveY(x: Float): Float {
                val phase = (x - waveStart) / waveLength
                val waveCycle = phase - kotlin.math.floor(phase)
                val waveValue = kotlin.math.cos(waveCycle * 2f * kotlin.math.PI.toFloat())
                
                // Calculate amplitude coefficient at this x position
                val ampCoeff = if (transitionEnabled) {
                    val length = transitionPeriods * waveLength
                    ((waveProgressPx + length / 2f - x) / length).coerceIn(0f, 1f)
                } else {
                    1f
                }
                
                return centerY + waveValue * lineAmplitude * heightFraction * ampCoeff
            }

            // Draw round cap at start (synced with wave)
            drawCircle(
                color = primaryColor,
                radius = capRadius,
                center = Offset(0f, getWaveY(0f)),
            )

            // Draw round cap at end (only right half, synced with wave movement)
            val endWaveY = getWaveY(totalWidth)
            clipRect(
                left = totalWidth,
                top = centerY - clipTop,
                right = totalWidth + capRadius,
                bottom = centerY + clipTop,
            ) {
                drawCircle(
                    color = inactiveTrackColor,
                    radius = capRadius,
                    center = Offset(totalWidth, endWaveY),
                )
            }

            // Vertical Bar Thumb
            val barHalfHeight = (lineAmplitude + strokeWidth)
            val barWidth = 5.dp.toPx()

            if (barHalfHeight > 0.5f) {
                drawLine(
                    color = primaryColor,
                    start = Offset(totalProgressPx, centerY - barHalfHeight),
                    end = Offset(totalProgressPx, centerY + barHalfHeight),
                    strokeWidth = barWidth,
                    cap = StrokeCap.Round,
                )
            }
        }
    }
}
```

**Technique notes:**
- **One path, two clipped draws.** The wave is built once across the full width, then drawn twice
  through `clipRect` — active colour left of the thumb, inactive right. Far cheaper and more robust
  than building two paths.
- **Frame-driven phase** via `withFrameMillis` with a delta-time integration, and the loop only runs
  while `isPlaying`. Do not use an infinite `rememberInfiniteTransition` for this; you want the
  animation to actually stop, not run invisibly.
- **The wave flattens while dragging too** (`shouldFlatten = !isPlaying || isDragging`) — motion
  gets out of the way of direct manipulation.
- `computeAmplitude` ramps the amplitude in across `transitionPeriods * waveLength` around the
  playhead, so the wave doesn't start abruptly at x=0.
- **Anti-pattern in this file:** `tween(150, LinearEasing)` hardcoded durations. On an Expressive
  project replace those with `MaterialTheme.motionScheme.fastEffectsSpec<Float>()`; the amplitude is
  a non-spatial property and should not overshoot.

### 5.3 `VolumeSlider` — the M3 Expressive Slider "Size M" spec, literally

Implements the published Expressive spec (track 40dp, handle 52×4dp, 12dp track corners, 24dp inset
icon) **on top of the real `SliderDefaults.Track`**, using `thumbTrackGapSize` / `trackCornerSize` /
`drawStopIndicator`, and draws a volume icon *inside* the track that hops from the active side to
the inactive side as the value shrinks.

**[from-corpus]** `/root/work/repos/vivi-music/app/src/main/kotlin/com/music/vivi/ui/component/VolumeSlider.kt`
(complete file, 172 lines):

```kotlin
/**
 * Material 3 Expressive Volume Slider
 * Based on M3 Expressive Slider specifications (Size M):
 * - Track height: 40dp
 * - Handle height: 52dp
 * - Handle width: 4dp
 * - Track corner radius: 12dp
 * - Inset icon size: 24dp
 */

package com.music.vivi.ui.component

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.height
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import com.music.vivi.R

/**
 * Material 3 Expressive Volume Slider dimensions (Size M)
 */
private object VolumeSliderDefaults {
    val TrackHeight: Dp = 40.dp
    val HandleHeight: Dp = 52.dp
    val HandleWidth: Dp = 4.dp
    val TrackCornerRadius: Dp = 12.dp
    val InsetIconSize: Dp = 24.dp
    val IconPadding: Dp = 10.dp
    val ThumbTrackGapSize: Dp = 6.dp
    val StopIndicatorRadius: Dp = 4.dp
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun VolumeSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    onValueChangeFinished: (() -> Unit)? = null,
    accentColor: Color = MaterialTheme.colorScheme.primary
) {
    val interactionSource = remember { MutableInteractionSource() }

    val volumeOffIcon = painterResource(R.drawable.volume_off)
    val volumeMuteIcon = painterResource(R.drawable.volume_mute)
    val volumeDownIcon = painterResource(R.drawable.volume_down)
    val volumeUpIcon = painterResource(R.drawable.volume_up)

    val currentIcon = when {
        value <= 0f -> volumeOffIcon
        value < 0.33f -> volumeMuteIcon
        value < 0.66f -> volumeDownIcon
        else -> volumeUpIcon
    }

    val colors = SliderDefaults.colors(
        thumbColor = accentColor,
        activeTrackColor = accentColor,
        activeTickColor = MaterialTheme.colorScheme.onPrimary,
        inactiveTrackColor = MaterialTheme.colorScheme.surfaceVariant,
        inactiveTickColor = MaterialTheme.colorScheme.onSurfaceVariant
    )
    
    val stopIndicatorColor = MaterialTheme.colorScheme.onSurfaceVariant

    Slider(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier,
        enabled = enabled,
        valueRange = 0f..1f,
        onValueChangeFinished = onValueChangeFinished,
        colors = colors,
        interactionSource = interactionSource,
        track = { sliderState ->
            val iconSize = DpSize(VolumeSliderDefaults.InsetIconSize, VolumeSliderDefaults.InsetIconSize)
            val activeIconColor = colors.activeTickColor
            val inactiveIconColor = colors.inactiveTickColor

            SliderDefaults.Track(
                sliderState = sliderState,
                modifier = Modifier
                    .height(VolumeSliderDefaults.TrackHeight)
                    .drawWithContent {
                        drawContent()
                        val yOffset = size.height / 2 - iconSize.toSize().height / 2
                        val fraction = value.coerceIn(0f, 1f)
                        val thumbGapPx = VolumeSliderDefaults.ThumbTrackGapSize.toPx()
                        val activeTrackEnd = size.width * fraction - thumbGapPx
                        val inactiveTrackStart = activeTrackEnd + thumbGapPx * 2
                        val activeTrackWidth = activeTrackEnd
                        val inactiveTrackWidth = size.width - inactiveTrackStart

                        drawVolumeIcon(
                            icon = currentIcon,
                            iconSize = iconSize,
                            iconPadding = VolumeSliderDefaults.IconPadding,
                            yOffset = yOffset,
                            activeTrackWidth = activeTrackWidth,
                            inactiveTrackStart = inactiveTrackStart,
                            inactiveTrackWidth = inactiveTrackWidth,
                            activeIconColor = activeIconColor,
                            inactiveIconColor = inactiveIconColor,
                            volumeOffIcon = volumeOffIcon
                        )
                    },
                colors = colors,
                enabled = enabled,
                thumbTrackGapSize = VolumeSliderDefaults.ThumbTrackGapSize,
                trackCornerSize = VolumeSliderDefaults.TrackCornerRadius,
                drawStopIndicator = if (value < 0.90f) { offset ->
                    drawCircle(
                        color = stopIndicatorColor,
                        radius = VolumeSliderDefaults.StopIndicatorRadius.toPx(),
                        center = offset
                    )
                } else null
            )
        }
    )
}

private fun DrawScope.drawVolumeIcon(
    icon: Painter,
    iconSize: DpSize,
    iconPadding: Dp,
    yOffset: Float,
    activeTrackWidth: Float,
    inactiveTrackStart: Float,
    inactiveTrackWidth: Float,
    activeIconColor: Color,
    inactiveIconColor: Color,
    volumeOffIcon: Painter
) {
    val iconSizePx = iconSize.toSize()
    val iconPaddingPx = iconPadding.toPx()
    val minSpaceForIcon = iconSizePx.width + iconPaddingPx * 2

    if (activeTrackWidth >= minSpaceForIcon) {
        translate(iconPaddingPx, yOffset) {
            with(icon) {
                draw(iconSizePx, colorFilter = ColorFilter.tint(activeIconColor))
            }
        }
    } else if (inactiveTrackWidth >= minSpaceForIcon) {
        translate(inactiveTrackStart + iconPaddingPx, yOffset) {
            with(volumeOffIcon) {
                draw(iconSizePx, colorFilter = ColorFilter.tint(inactiveIconColor))
            }
        }
    }
}
```

**This is the reference implementation to copy for any "fat expressive slider".** Technique notes:

- It **keeps the real `Slider`** and only replaces the `track` slot. Gestures, semantics, keyboard
  and RTL all still come from material3. Compare §5.1/§5.2, which throw those away.
- `SliderDefaults.Track(thumbTrackGapSize = 6.dp, trackCornerSize = 12.dp)` — the 6dp gap is the
  published accessibility spec number (§2), not an arbitrary choice.
- `drawStopIndicator = if (value < 0.90f) { offset -> … } else null` — the stop dot is **suppressed
  when the thumb is nearly on top of it**. Passing `null` removes it; that conditional is the
  polish most implementations miss.
- The inset icon is drawn in `Modifier.drawWithContent { drawContent(); … }` so it paints *over* the
  track, and it recomputes `activeTrackEnd` / `inactiveTrackStart` using the same 6dp gap the track
  uses — keeping icon and track geometry in sync.
- The icon **hops sides**: if the active portion is too narrow to hold a 24dp icon plus padding, it
  draws the "off" icon in the inactive portion instead. Never let an inset icon overflow its
  segment.
- `HandleHeight` / `HandleWidth` are declared but the default thumb is used — if you want the full
  spec, also pass `thumb = { SliderDefaults.Thumb(interactionSource, thumbSize = DpSize(4.dp, 52.dp), colors = colors) }`.

### 5.4 `PlayerSliderTrack` — the "slim" hand-drawn track

A minimal custom track that grows from 10dp to 16dp while dragged (the animation lives at the call
site, §4).

**[from-corpus]** `/root/work/repos/vivi-music/app/src/main/kotlin/com/music/vivi/ui/component/PlayerSlider.kt`
(complete file, 118 lines):

```kotlin
package com.music.vivi.ui.component

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.SliderColors
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.SliderState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.lerp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlayerSliderTrack(
    sliderState: SliderState,
    modifier: Modifier = Modifier,
    colors: SliderColors = SliderDefaults.colors(),
    trackHeight: Dp = 10.dp
) {
    val inactiveTrackColor = colors.inactiveTrackColor
    val activeTrackColor = colors.activeTrackColor
    val inactiveTickColor = colors.inactiveTickColor
    val activeTickColor = colors.activeTickColor
    val valueRange = sliderState.valueRange
    Canvas(
        modifier
            .fillMaxWidth()
            .height(trackHeight)
    ) {
        drawTrack(
            stepsToTickFractions(sliderState.steps),
            0f,
            calcFraction(
                valueRange.start,
                valueRange.endInclusive,
                sliderState.value.coerceIn(valueRange.start, valueRange.endInclusive)
            ),
            inactiveTrackColor,
            activeTrackColor,
            inactiveTickColor,
            activeTickColor,
            trackHeight
        )
    }
}

private fun DrawScope.drawTrack(
    tickFractions: FloatArray,
    activeRangeStart: Float,
    activeRangeEnd: Float,
    inactiveTrackColor: Color,
    activeTrackColor: Color,
    inactiveTickColor: Color,
    activeTickColor: Color,
    trackHeight: Dp = 2.dp
) {
    val isRtl = layoutDirection == LayoutDirection.Rtl
    val sliderLeft = Offset(0f, center.y)
    val sliderRight = Offset(size.width, center.y)
    val sliderStart = if (isRtl) sliderRight else sliderLeft
    val sliderEnd = if (isRtl) sliderLeft else sliderRight
    val tickSize = 2.0.dp.toPx()
    val trackStrokeWidth = trackHeight.toPx()
    drawLine(
        inactiveTrackColor,
        sliderStart,
        sliderEnd,
        trackStrokeWidth,
        StrokeCap.Round
    )
    val sliderValueEnd = Offset(
        sliderStart.x +
                (sliderEnd.x - sliderStart.x) * activeRangeEnd,
        center.y
    )
    val sliderValueStart = Offset(
        sliderStart.x +
                (sliderEnd.x - sliderStart.x) * activeRangeStart,
        center.y
    )
    drawLine(
        activeTrackColor,
        sliderValueStart,
        sliderValueEnd,
        trackStrokeWidth,
        StrokeCap.Round
    )
    for (tick in tickFractions) {
        val outsideFraction = tick > activeRangeEnd || tick < activeRangeStart
        drawCircle(
            color = if (outsideFraction) inactiveTickColor else activeTickColor,
            center = Offset(lerp(sliderStart, sliderEnd, tick).x, center.y),
            radius = tickSize / 2f
        )
    }
}

private fun stepsToTickFractions(steps: Int): FloatArray {
    return if (steps == 0) floatArrayOf() else FloatArray(steps + 2) { it.toFloat() / (steps + 1) }
}

private fun calcFraction(a: Float, b: Float, pos: Float) =
    (if (b - a == 0f) 0f else (pos - a) / (b - a)).coerceIn(0f, 1f)
```

**Technique notes:**
- It is a **`track` slot implementation**, taking `SliderState` — so it plugs into the real `Slider`
  and inherits gestures and semantics. This is the right way to customise a slider's look.
- **RTL is handled explicitly** by swapping `sliderStart`/`sliderEnd`. Any hand-drawn track must do
  this; `DrawScope` does not mirror for you.
- `trackHeight` is a parameter, so the grow-on-touch animation lives at the call site
  (`animateDpAsState` + `Spring.DampingRatioMediumBouncy` — §4) rather than inside the component.
  Keep animation at the call site; keep components stateless.
- `stepsToTickFractions` / `calcFraction` are re-implementations of material3 internals. If you copy
  this, keep them private and keep them.

**Notably missing vs `SliderDefaults.Track`:** no thumb-track gap, no inside corners, no stop
indicator. It is a deliberately pre-Expressive look. Use it when the *content* is the hero (album
art) and the slider should recede — but recognise it is not the Expressive slider.

---

## 6. Text fields

**[verified — alpha21]** the Expressive text-field additions:

| API | Replaces | Note |
| --- | --- | --- |
| `TextFieldDefaults.roundedShape` | — | the Expressive rounded (pill-ish) field shape |
| `TextFieldDefaults.tonalColors()` | — | tonal container treatment |
| `OutlinedTextFieldDefaults.roundedShape` / `.tonalColors()` | — | outlined equivalents |
| `TextFieldLabelPosition.Inside` / `TextFieldLabelPosition.Cutout` | `TextFieldLabelPosition.Attached` (**deprecated**) | `Cutout` is the notch-in-the-outline look; `Inside` keeps the label within the container |
| `OutlinedTextFieldDefaults.contentPaddingWithLabel()` / `contentPaddingWithoutLabel()` | `contentPadding()` (**deprecated**) | |

Also in the family: **`SecureTextField` / `OutlinedSecureTextField`** (new in 1.4.0) — the
`BasicSecureTextField`-backed password fields that work with `TextFieldState` instead of a
`String`/`onValueChange` pair — and **text auto-sizing** (also 1.4.0).

**None of these are used anywhere in the corpus.** Zero occurrences of `roundedShape`,
`tonalColors`, `TextFieldLabelPosition`, or `SecureTextField` across all four repos. All four still
use `TextFieldDefaults.colors()` / `OutlinedTextFieldDefaults.colors()` and nothing else. Treat this
section as the API listing; **compile-check every one of these before shipping**.

**[canonical-form — NOT from corpus]**:

```kotlin
OutlinedTextField(
    state = textFieldState,
    label = { Text("Email") },
    labelPosition = TextFieldLabelPosition.Cutout(),
    shape = OutlinedTextFieldDefaults.roundedShape,
    colors = OutlinedTextFieldDefaults.tonalColors(),
    contentPadding = OutlinedTextFieldDefaults.contentPaddingWithLabel(),
)
```

**Pitfalls:**
- `TextFieldLabelPosition.Attached` is deprecated but still compiles — a deprecation warning is your
  only signal. Migrate to `Inside` or `Cutout` deliberately; they look different.
- `contentPadding()` → `contentPaddingWithLabel()` / `contentPaddingWithoutLabel()`: you must pick
  based on whether the field actually has a label. The single old function guessed.
- `roundedShape` on a **multi-line** text field looks wrong. It is for single-line inputs.
- `SecureTextField` is `TextFieldState`-based; you cannot drop it in where a
  `value`/`onValueChange` `TextField` was without migrating state.
- Feature flag in the area: `ComposeFoundationFlags#isBasicTextFieldMinSizeOptimizationEnabled`.

---

## 7. Time pickers

**[verified]**
- `TimePickerDialog` — new in **1.4.0**.
- **Expressive `TimePicker` component** — **1.5.0-alpha23**.
- **Scroll variant of the expressive `TimePicker`** — **1.5.0-alpha24**.
- `TimePicker` **shapes** parameter — alpha21/22.

The expressive `TimePicker`'s exact signature is **UNVERIFIED**; the alpha23 release note names the
component but the parameter list was not readable. Do not write it from memory.

> **Where the dialog lives.** This section owns `TimePickerState` — the state holder. The
> **dialog** surface (`TimePickerDialog`, `TimePickerDialogDefaults.Title` / `.DisplayModeToggle` /
> `.MinHeightForTimePicker`, `TimePickerDisplayMode`, `RichTimePickerDialog`) is documented with
> canonical verbatim androidx sample code in
> `m3-expressive-components/references/lists-cards-containers.md` §10.1. Read that before writing a
> time-picker dialog; the two sections are written to agree, and §10.1 supersedes the hand-rolled
> structure shown below.

### 7.1 `TimePickerState` changes in alpha25 / alpha26

**[verified — release notes]**

| Change | Version | Note |
| --- | --- | --- |
| **`initialSelection` parameter** added to the `TimePickerState` factory function | **alpha26** (Iad905) | Lets you open the picker on the minute field instead of the hour field. Exact parameter type **UNVERIFIED**. |
| `TimePickerState` now **saves the active selection mode across state restorations** | **alpha26** (Iad905) | Behavioural. Rotating the device or backgrounding no longer resets the picker to the hour field mid-edit. No opt-out; you can delete any code that hand-persisted this. |
| **TalkBack announces formatted time values** in the scrollable `TimePicker` | **alpha26** (Ice981) | Bug fix. Previously the scroll-variant read raw numbers. |
| **`TimePickerTextField` focus switching on error** | **alpha25** (Ieceec) | Focus no longer jumps away from an invalid field before the user can correct it. |
| `TimePickerDialog.kt` KDoc updated | alpha25 (Ia5fb8) | Docs only. |

The `initialSelection` addition and the restoration fix are the same change (Iad905): selection
mode became real, restorable state rather than an internal default. If you previously worked around
the reset — e.g. by keying a `remember` on a rotation counter, or re-creating the state — remove
that on alpha26; it now fights the built-in `Saver`.

### 7.2 What the corpus has: the classic picker + a hand-rolled dialog

Med (pinned alpha21, so pre-expressive-TimePicker) builds its own `TimePickerDialog`. The structure
below is sound and worth reading for the three rules underneath it, but **on any current artifact
prefer the official slot-based `TimePickerDialog`** shown verbatim in `lists-cards-containers.md`
§10.1 — note in particular that Med's `toggle = ` slot is named `modeToggleButton = ` in the
official API, and that the official version gates its confirm button on `state.isInputValid`, which
this one cannot.

**[from-corpus]** `/root/work/repos/Med/app/src/main/kotlin/com/fedeveloper95/med/elements/MainActivity/TimePicker.kt`
(complete file, abridged):

```kotlin
@Composable
fun TimePicker(
    onDismiss: () -> Unit,
    onConfirm: (LocalTime) -> Unit,
    initialTime: LocalTime = LocalTime.now()
) {
    val context = LocalContext.current
    val isSystem24Hour = remember { DateFormat.is24HourFormat(context) }

    val state = rememberTimePickerState(
        initialHour = initialTime.hour,
        initialMinute = initialTime.minute,
        is24Hour = isSystem24Hour
    )

    var showPicker by remember { mutableStateOf(true) }
    val configuration = LocalConfiguration.current

    TimePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = { onConfirm(LocalTime.of(state.hour, state.minute)) }) {
                Text(stringResource(R.string.ok_action), fontFamily = GoogleSansFlex)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel_action), fontFamily = GoogleSansFlex)
            }
        },
        toggle = {
            if (configuration.screenHeightDp > 400) {
                IconButton(onClick = { showPicker = !showPicker }) {
                    val icon = if (showPicker) Icons.Rounded.Keyboard else Icons.Rounded.Schedule
                    Icon(
                        imageVector = icon,
                        contentDescription = if (showPicker) stringResource(R.string.switch_to_text_input)
                                             else stringResource(R.string.switch_to_touch_input)
                    )
                }
            }
        }
    ) {
        if (showPicker && configuration.screenHeightDp > 400) {
            TimePicker(
                state = state,
                layoutType = TimePickerLayoutType.Vertical
            )
        } else {
            TimeInput(
                state = state,
                modifier = Modifier.padding(top = 16.dp)
            )
        }
    }
}
```

Three things every time picker must do, all present here:
- **Read the system 24-hour setting** (`DateFormat.is24HourFormat(context)`), never assume.
- **Offer the keyboard/dial toggle** and give both icons real content descriptions.
- **Fall back to `TimeInput` when the screen is short** (`screenHeightDp > 400`) — the dial does not
  fit in landscape on a phone.

### 7.3 `ScrollField`

**[verified]** `ScrollField` gained **expressive number transitions** plus accessibility/TalkBack
fixes across alpha23/alpha24; `ScrollIndicatorState`'s parameter was made **non-nullable in alpha24**.
Signature UNVERIFIED, zero corpus usage. It is the scrolling-number picker primitive underneath the
alpha24 scroll-variant `TimePicker` — the "expressive number transitions" are digits that animate
between values rather than snapping. Check the package summary before using it.

**alpha25 (If7477): "Improved `ScrollField` accessibility and keyboard navigation support."**
**[verified]** Un-enumerated beyond that sentence — no API delta is documented and the signature
remains **UNVERIFIED**. Practical read: `ScrollField` was not keyboard-operable before alpha25, so
if you evaluated it for a desktop/ChromeOS target and rejected it on that basis, re-evaluate. Paired
with the alpha26 TalkBack fix for the scrollable `TimePicker` (§7.1), the scroll-variant picker's
a11y story is materially better at alpha26 than it was at alpha24.

---

## 8. Switch, Checkbox, RadioButton

No API break in Expressive. What changed:

- **Motion.** Their state transitions come from `MaterialTheme.motionScheme` once you use
  `MaterialExpressiveTheme`. The Switch thumb's travel gets the spatial spring; the tick's fade gets
  the effects spring (no overshoot on opacity).
- **`Switch(thumbContent = …)`** is the expressive move that costs nothing: put a check/clear icon in
  the thumb so state is legible without relying on colour alone.

**[from-corpus]** `/root/work/repos/Tomato/.../ColorSchemePickerListItem.kt` and the same pattern at
six sites in Med:

```kotlin
                Switch(
                    checked = checked,
                    onCheckedChange = { … },
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
```

Always `Modifier.size(SwitchDefaults.IconSize)` — a 24dp icon overflows the thumb.

Tomato's shared `switchColors` is one override **[from-corpus, `Color.kt`]**:

```kotlin
    val switchColors: SwitchColors
        @Composable get() = SwitchDefaults.colors(
            checkedIconColor = colorScheme.primary,
        )
```

**[judgment]** Placement: a `Switch` belongs in `trailingContent` of a `SegmentedListItem` whose
`onClick` toggles the same state (Tomato and Med both wire `onClick` and `onCheckedChange` to the
same action, so tapping the row works). A `Checkbox` belongs in `leadingContent` of a multi-select
list; a `RadioButton` should usually be replaced by the **selected `SegmentedListItem`** with a
trailing check icon (Med's `BloodType.kt`, §3.1 of `lists-cards-containers.md`) — that is the
Expressive form of a radio group.

---

## 9. Design guidance, anti-patterns, accessibility

### Choosing a slider

| Need | Use |
| --- | --- |
| One value from a range | Standard `Slider` |
| A min/max pair | `RangeSlider` |
| Neutral value is the midpoint (±) | `Slider` with `SliderDefaults.CenteredTrack` |
| Discrete positions | `Slider(steps = n)` — "stops configuration", not a separate component |
| Media seek bar | `Slider` with a custom `track` slot (§5.3/§5.4), or `WavySlider` (§5.1) |
| Volume with an icon | §5.3 |

### Anti-patterns

| Don't | Instead |
| --- | --- |
| Rebuild gestures with `pointerInput` to customise a slider's look | Replace the `track` (and `thumb`) slots and keep the real `Slider` |
| Hide the thumb with `alpha = 0f` | `thumb = { Spacer(Modifier.size(0.dp)) }` |
| Hardcode `tween(150)` for slider/wave animations | `MaterialTheme.motionScheme.fastEffectsSpec<Float>()` for settle/opacity, `defaultSpatialSpec()` for travel |
| Let a wave keep animating while paused or while dragging | Drive amplitude from playback state; flatten during drag |
| Bind a slider directly to an externally-changing value | Nullable drag override (`sliderPosition: Long?`) or `LaunchedEffect(value) { if (!state.isDragging) … }` |
| Draw a track without an RTL check | Swap `sliderStart`/`sliderEnd` on `LayoutDirection.Rtl` |
| Improvise thumb-gap/corner/stop sizes | 6dp gap, 2dp inside corner, 4dp stop indicator — they exist for contrast **[verified]** |
| Leave a stop indicator under the thumb at max value | `drawStopIndicator = if (value < 0.90f) { … } else null` |
| Overshoot on colour or opacity | Effects springs; overshoot is for spatial properties only |
| Ship `TextFieldLabelPosition.Attached` or `OutlinedTextFieldDefaults.contentPadding()` | `Inside`/`Cutout`, `contentPaddingWithLabel()`/`contentPaddingWithoutLabel()` |
| Assume 12/24-hour format | `DateFormat.is24HourFormat(context)` |
| Show the dial `TimePicker` on a short screen | Fall back to `TimeInput` (the official dialog gives you `TimePickerDialogDefaults.MinHeightForTimePicker` for the threshold) |
| Hand-roll a `TimePickerDialog` out of `AlertDialog` | `androidx.compose.material3.TimePickerDialog` — see `lists-cards-containers.md` §10.1 |
| Hand-persist the picker's selection mode across rotation | alpha26 saves it in `TimePickerState`; your workaround now fights the `Saver` (§7.1) |
| Pass a `SliderState` to `Modifier.draggable(state = …)` | It stopped implementing `DraggableState` in alpha25 (§1.0) — use `value`/`onValueChange` |
| 2-param `SliderState.Saver(a, b)` | The alpha25 overload taking `steps` (exact signature UNVERIFIED — use the IDE's `ReplaceWith`) |
| Wavy anything at very small sizes | "at very small sizes, the wavy shape may not be as visible" **[verified]** — use the plain form |

### Accessibility

- **Touch targets ≥48×48dp; pointer targets ≥44×44dp; ≥8dp separation** **[verified — Material
  accessibility guidance]**. A 10dp visual track still needs a 48dp tall touch area. `Slider` gives
  you this; a hand-rolled `Box(Modifier.height(48.dp))` (as `SquigglySlider` does) is the reason its
  container is 48dp.
- **Custom sliders built on `pointerInput` have no semantics.** `WavySlider` and `SquigglySlider`
  are both unusable with TalkBack as written. If you ship either, add:

  ```kotlin
  Modifier.semantics {
      contentDescription = "Seek"
      progressBarRangeInfo = ProgressBarRangeInfo(current = value, range = valueRange)
      setProgress { target -> onValueChange(target); onValueChangeFinished?.invoke(); true }
  }
  ```

  **[canonical-form]** — verify the exact semantics property names against your Compose version.
  Better: don't rebuild gestures at all (§5.3/§5.4).
- **Keyboard:** the real `Slider` handles arrow keys and focus rings. Custom `pointerInput`
  implementations do not — another reason to customise the slot, not the component.
- **Labels:** a slider needs a visible label *and* a value readout. Tomato's `SliderListItem` pairs
  `label` with `trailingLabel(value)` — copy that structure.
- **Contrast:** the 6dp thumb–track gap and 4dp stop indicator exist "to meet modern contrast
  requirements" **[verified]**. Removing them to make a slider look sleeker is an accessibility
  regression, not a style choice.
- **Colour alone is never state.** `Switch(thumbContent = check/clear icons)` and a trailing check
  icon on a selected list row are the cheap fixes.
- **Reduced motion:** honour the system Remove-animations setting for wave animation and thumb
  springs — a frame-driven `withFrameMillis` loop keeps running regardless of the setting, so gate it
  yourself.

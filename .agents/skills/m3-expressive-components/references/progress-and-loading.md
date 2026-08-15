# M3 Expressive progress and loading

`LoadingIndicator`, `ContainedLoadingIndicator`, wavy linear/circular progress, pull-to-refresh,
skeletons.

Every section: **signature → when to use → working code → pitfalls.**

Provenance markers:

- `[CORPUS <repo>]` — from a shipping open-source app; path given. Safe to copy.
- `[ANDROIDX]` — signature from androidx source or the rendered API reference. Verify it resolves
  against your pinned artifact.
- `[UNVERIFIED]` — name real, exact signature/value unconfirmed. Do not paste blind.

Corpus repos: `/root/work/repos/{vivi-music,Tomato,LastChat,Med}`.
LastChat is the heaviest wavy-progress user (12 linear + 8 circular sites). Tomato has the best
circular pattern. vivi-music has the best pull-to-refresh and the only `ContainedLoadingIndicator`
used as a real content state.

---

# 1. Decision procedure — read this first

```
Is the wait under ~5 seconds AND indeterminate throughout?
  YES → LoadingIndicator / ContainedLoadingIndicator
  NO  ↓
Do you know the progress fraction?
  YES → determinate progress indicator (linear or circular)
  NO  → indeterminate progress indicator (linear or circular)

Wavy or not?
  Indicator ≥ ~40dp AND an expressive moment is appropriate → wavy
  Small inline spinner, dense list, or a utilitarian screen  → non-wavy
```

`[GOOGLE — ProgressIndicator.md]`

- **Determinate** — "fill from 0% to 100%. **Use it when progress and wait time is known.**"
- **Indeterminate** — "move along a fixed track, growing and shrinking in size. **Use it when
  progress and wait time is unknown.**"
- **Consistency rule**: **"Only one type should represent each kind of activity in an app."** Don't
  use a linear bar for network fetches on one screen and a circular spinner for the same class of
  fetch on another.
- Determinate vs indeterminate is a **semantic** choice, not a visual one. Choosing wrong
  misreports state to every user including assistive tech.

Linear = "display progress by animating an indicator along the length of a fixed, visible track."
Circular = "display progress by animating an indicator along an invisible circular track in a
clockwise direction. They can be applied directly to a surface, such as a button or card."

Expressive spec numbers `[GOOGLE]`:

| Spec | Value |
| --- | --- |
| Default track thickness | 4dp |
| Recommended thick (expressive) variant | 8dp |
| Track gap (indicator ↔ track) | 4dp — present for **accessibility contrast** |
| Stop indicator (linear determinate) | 4dp — also an accessibility affordance |

---

# 2. `LoadingIndicator` / `ContainedLoadingIndicator`

## Status — still experimental, re-confirmed at alpha26

**`ExperimentalMaterial3ExpressiveApi` is STILL REQUIRED at 1.5.0-alpha26.** The promotion to stable
was **reverted in alpha19** (I30e69, b/497876695, b/497877850) and there has been **no
re-promotion** — not in alpha25, not in alpha26. Do not assume it graduated with the rest of the
Expressive surface.

```kotlin
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
```

**Verified three ways at alpha26:**

| API | Evidence |
| --- | --- |
| `LoadingIndicator` (both overloads) | Declaration-level `@ExperimentalMaterial3ExpressiveApi` on every overload in `LoadingIndicator.kt`, read at the alpha26 terminal commit `4d087bd6f764b8425a70fd94102f855aa382d94b`. |
| `ContainedLoadingIndicator` (both overloads) | Same file, same annotation. |
| **`MaterialShapes`** | `public sealed class MaterialShapes` preceded by `@ExperimentalMaterial3ExpressiveApi` in `MaterialShapes.kt`. |
| all of the above | No graduation bullet appears in the alpha25 or alpha26 release notes. |
| all of the above | The canonical androidx samples still opt in: `LoadingIndicatorSamples.kt` = 6 `ExperimentalMaterial3ExpressiveApi` occurrences, `MaterialShapesSamples.kt` = 3. Compare `ProgressIndicatorSamples.kt` (wavy) = **0**, and `ThemeSamples.kt` = **0** — those really did graduate. |

> **This is the one place where "helpfully" removing an Expressive opt-in breaks the build.** A lot
> of the Expressive surface graduated between alpha19 and alpha26 — buttons, toggle buttons, split
> buttons, flexible app bars, search bars, floating toolbars, nav rails, `MaterialExpressiveTheme`,
> wavy progress. `MaterialShapes` and the loading indicators did **not**. If you are doing an
> opt-in cleanup pass, exclude these three.

`MaterialShapes` is tied to this section because `LoadingIndicatorDefaults`' polygon lists are built
from it (see the defaults block below) — so the loading indicators cannot graduate ahead of it.

## Signatures

`[ANDROIDX]` `LoadingIndicator.kt`, verbatim — four overloads, determinate and indeterminate for
each of contained/uncontained:

```kotlin
@ExperimentalMaterial3ExpressiveApi
@Composable
fun LoadingIndicator(
    progress: () -> Float,
    modifier: Modifier = Modifier,
    color: Color = LoadingIndicatorDefaults.indicatorColor,
    polygons: List<RoundedPolygon> = LoadingIndicatorDefaults.DeterminateIndicatorPolygons,
)

@ExperimentalMaterial3ExpressiveApi
@Composable
fun LoadingIndicator(
    modifier: Modifier = Modifier,
    color: Color = LoadingIndicatorDefaults.indicatorColor,
    polygons: List<RoundedPolygon> = LoadingIndicatorDefaults.IndeterminateIndicatorPolygons,
)

@ExperimentalMaterial3ExpressiveApi
@Composable
fun ContainedLoadingIndicator(
    progress: () -> Float,
    modifier: Modifier = Modifier,
    containerColor: Color = LoadingIndicatorDefaults.containedContainerColor,
    indicatorColor: Color = LoadingIndicatorDefaults.containedIndicatorColor,
    containerShape: Shape = LoadingIndicatorDefaults.containerShape,
    polygons: List<RoundedPolygon> = LoadingIndicatorDefaults.DeterminateIndicatorPolygons,
)

@ExperimentalMaterial3ExpressiveApi
@Composable
fun ContainedLoadingIndicator(
    modifier: Modifier = Modifier,
    containerColor: Color = LoadingIndicatorDefaults.containedContainerColor,
    indicatorColor: Color = LoadingIndicatorDefaults.containedIndicatorColor,
    containerShape: Shape = LoadingIndicatorDefaults.containerShape,
    polygons: List<RoundedPolygon> = LoadingIndicatorDefaults.IndeterminateIndicatorPolygons,
)
```

`progress` tracks 0.0 → 1.0; values outside the range are coerced. `polygons` requires a **minimum
of two** `RoundedPolygon`s defining the morph sequence. The un-contained overloads delegate to the
same impl with `containerColor = Color.Unspecified`.

## What it actually draws

`[ANDROIDX]` `LoadingIndicatorDefaults`, verbatim:

```kotlin
@ExperimentalMaterial3ExpressiveApi
object LoadingIndicatorDefaults {
    val ContainerWidth: Dp = LoadingIndicatorTokens.ContainerWidth
    val ContainerHeight: Dp = LoadingIndicatorTokens.ContainerHeight
    val IndicatorSize = LoadingIndicatorTokens.ActiveSize

    val containerShape: Shape
        @Composable get() = LoadingIndicatorTokens.ContainerShape.value

    val indicatorColor: Color
        @Composable get() = LoadingIndicatorTokens.ActiveIndicatorColor.value

    val containedIndicatorColor: Color
        @Composable get() = LoadingIndicatorTokens.ContainedActiveColor.value

    val containedContainerColor: Color
        @Composable get() = LoadingIndicatorTokens.ContainedContainerColor.value

    val IndeterminateIndicatorPolygons = listOf(
        MaterialShapes.SoftBurst,
        MaterialShapes.Cookie9Sided,
        MaterialShapes.Pentagon,
        MaterialShapes.Pill,
        MaterialShapes.Sunny,
        MaterialShapes.Cookie4Sided,
        MaterialShapes.Oval,
    )

    val DeterminateIndicatorPolygons = listOf(
        MaterialShapes.Circle.transformed(Matrix().apply { rotateZ(360f / 20) }),
        MaterialShapes.SoftBurst,
    )

    internal val ActiveIndicatorScale =
        IndicatorSize.value / min(ContainerWidth.value, ContainerHeight.value)
}
```

Literal dp behind `LoadingIndicatorTokens.*` is `[UNVERIFIED]`; Material's published design spec says
**38dp indicator inside a 48dp × 48dp container**, and it "can scale in size."

The indeterminate form is **a looping shape-morph sequence through seven Material 3 shapes** —
SoftBurst → Cookie9Sided → Pentagon → Pill → Sunny → Cookie4Sided → Oval. The determinate form
morphs between a rotated Circle and SoftBurst as progress advances. That is the signature Expressive
loading animation and the reason it exists: it is *not* a rotating arc.

Two configurations `[GOOGLE]`: **contained** (morphing shape on a rounded container backdrop) and
**uncontained** (shape only).

## When to use

`[GOOGLE — LoadingIndicator.md]`

- **"Designed to show progress that loads in under five seconds."** It communicates app state and
  whether the user can navigate away.
- **It "should replace most indeterminate circular progress indicators."** This is the default
  Expressive spinner.
- Documented placements: pull-to-refresh (in a circular container with a lighter background), page
  centre, inside other components such as buttons, even browser favicons.

**Do not** use it:

- for waits longer than ~5 seconds — use a progress indicator;
- **"if processes transition from indeterminate to determinate states"** — use a progress indicator.
  This one is easy to violate: a download that starts as "connecting…" and then reports bytes should
  be a progress indicator from the start, not a `LoadingIndicator` that swaps mid-flight.

## Working code

Page-centre and list-footer indeterminate — the two most common shapes.

`[CORPUS vivi-music]` `.../ui/screens/search/OnlineSearchResult.kt`:

```kotlin
import androidx.compose.material3.LoadingIndicator

// pagination footer
if (itemsPage?.continuation != null) {
    item(key = "loading") {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(32.dp),
            contentAlignment = Alignment.Center
        ) {
            LoadingIndicator()
        }
    }
}

// first-load, whole-page
if (searchFilter == null && searchSummary == null || searchFilter != null && itemsPage == null) {
    item {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(64.dp),
            contentAlignment = Alignment.Center
        ) {
            LoadingIndicator()
        }
    }
}
```

Sized and recoloured, cross-faded over custom art —
`[CORPUS vivi-music]` `.../ui/component/RandomizeGridItem.kt`:

```kotlin
Box(modifier = Modifier.alpha(loadingAlpha)) {
    LoadingIndicator(
        modifier = Modifier.size(48.dp),
        color = MaterialTheme.colorScheme.onSecondaryContainer,
    )
}
```

Three more shapes of the same idea:

```kotlin
// [CORPUS Tomato] .../ui/statsScreen/screens/StatsMainScreen.kt (3 sites) — chart skeleton.
// Note the fixed height: the chart's final size is reserved, so nothing shifts.
else Box(
    contentAlignment = Alignment.Center,
    modifier = Modifier.fillMaxWidth().height(226.dp)
) { LoadingIndicator() }

// [CORPUS LastChat] .../ui/components/ai/ContextRefreshDialog.kt:242-257 — indicator + caption.
Column(
    modifier = Modifier.fillMaxWidth().padding(vertical = 32.dp),
    horizontalAlignment = Alignment.CenterHorizontally,
    verticalArrangement = Arrangement.spacedBy(16.dp)
) {
    LoadingIndicator()
    Text(
        text = stringResource(R.string.context_refresh_loading),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}

// [CORPUS Med] app/.../UpdaterActivity.kt:483-488 — oversized as the hero of a whole screen.
LoadingIndicator(modifier = Modifier.size(180.dp))
```

Other LastChat sites: `ui/components/textselection/TextSelectionSheet.kt:431` and `:485`,
`ui/pages/setting/SettingWebPage.kt:694`.

## `ContainedLoadingIndicator`

Use the contained form when the indicator sits **on top of content** rather than in cleared space, or
when it needs to match the visual weight of sibling states.

`[CORPUS vivi-music]` `.../ui/screens/AlbumScreen.kt` and
`.../ui/screens/playlist/OnlinePlaylistScreen.kt` — content-level loading:

```kotlin
Box(
    modifier = Modifier
        .fillMaxWidth()
        .padding(32.dp),
    contentAlignment = Alignment.Center
) {
    ContainedLoadingIndicator()
}
```

As the lyrics-pending state on the player screen — `[CORPUS vivi-music]` `.../ui/player/Player.kt`:

```kotlin
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun InlineLyricsView(
    mediaMetadata: MediaMetadata?,
    showLyrics: Boolean,
    positionProvider: () -> Long
) {
    // ...fetch lyrics in a LaunchedEffect...

    Box(
        modifier = Modifier
            .fillMaxSize()
            .clip(RoundedCornerShape(12.dp)),
        contentAlignment = Alignment.Center
    ) {
        when {
            lyrics == null -> {
                ContainedLoadingIndicator()
            }
            lyrics == LyricsEntity.LYRICS_NOT_FOUND -> {
                Text(
                    text = stringResource(R.string.lyrics_not_found),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                    textAlign = TextAlign.Center
                )
            }
            else -> { /* Lyrics(...) */ }
        }
    }
}
```

The container is what makes this work: the loading state, the empty state and the loaded state all
occupy the same visual box over album art, and the contained indicator has enough backdrop to stay
legible.

The clearest justification for choosing contained is Tomato's — the indicator is one branch of an
`AnimatedContent` whose **other branches are 48dp icon blobs**, so the built-in container matches
them exactly. `[CORPUS Tomato]` `.../settingsScreen/screens/backupRestore/BottomSheetTemplate.kt:131-166`:

```kotlin
AnimatedContent(backupState) {
    when (it) {
        BackupRestoreState.CHOOSE_FILE ->
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .background(colorScheme.onSurfaceVariant, shapes.extraLarge)
                    .size(48.dp)
            ) {
                Icon(painterResource(Res.drawable.folder), null,
                    tint = colorScheme.surfaceVariant, modifier = Modifier.size(28.dp))
            }

        BackupRestoreState.LOADING ->
            ContainedLoadingIndicator()

        BackupRestoreState.DONE ->
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .background(colorScheme.onPrimaryContainer, shapes.extraLarge)
                    .size(48.dp)
            ) {
                Icon(painterResource(Res.drawable.check), null,
                    tint = colorScheme.surfaceVariant, modifier = Modifier.size(28.dp))
            }
    }
}
```

### Canonical minimal forms `[ANDROIDX — material3 samples]`

Source: `compose/material3/material3/samples/src/main/java/androidx/compose/material3/samples/LoadingIndicatorSamples.kt`
(lines 66–127). Verbatim, and note the opt-in is present in Google's own samples — corroborating the
status table above:

```kotlin
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
fun LoadingIndicatorSample() {
    Column(horizontalAlignment = Alignment.CenterHorizontally) { LoadingIndicator() }
```

```kotlin
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
fun ContainedLoadingIndicatorSample() {
    Column(horizontalAlignment = Alignment.CenterHorizontally) { ContainedLoadingIndicator() }
```

Determinate variants in the same file: `LoadingIndicator(progress = { animatedProgress })` (line 97)
and `ContainedLoadingIndicator(progress = { animatedProgress })` (line 127). And
`LoadingIndicatorPullToRefreshSample` (line 143) uses
`PullToRefreshDefaults.LoadingIndicator(state = state, isRefreshing = isRefreshing)` under
`@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)` — **both**
annotations, because pull-to-refresh is `ExperimentalMaterial3Api` and the indicator is Expressive.
See §6.

### In-button loading — swap the icon, animate the width

`[GOOGLE — android/androidify]`
`feature/results/src/main/java/com/android/developers/androidify/customize/watchface/WatchFacePanelButton.kt`
(lines 45–86). The best real-app `ContainedLoadingIndicator` idiom found in any Google repo:

```kotlin
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun WatchFacePanelButton(
    modifier: Modifier = Modifier,
    onClick: () -> Unit = {},
    buttonText: String,
    isInProgress: Boolean = false,
    iconResId: Int? = null,
    colors: ButtonColors = ButtonDefaults.buttonColors(
        contentColor = MaterialTheme.colorScheme.surface,
        containerColor = MaterialTheme.colorScheme.onSurface,
    ),
) {
    Button(
        colors = colors,
        modifier = modifier
            .heightIn(min = 64.dp)
            .fillMaxWidth()
            .animateContentSize(),
        border = BorderStroke(2.dp, color = colors.contentColor),
        onClick = onClick,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (isInProgress) {
                ContainedLoadingIndicator(
                    modifier = Modifier.size(24.dp),
                    containerColor = colors.containerColor,
                    indicatorColor = colors.contentColor,
                )
            } else if (iconResId != null) {
                Icon(
                    ImageVector.vectorResource(iconResId),
                    contentDescription = null,
                )
            }
            Spacer(modifier.width(8.dp))
            Text(buttonText, fontSize = 18.sp)
        }
    }
}
```

Three transferable decisions:

1. **Swap icon ↔ indicator in place**, keeping the label. The button never disappears, so the tap
   target and the layout are stable.
2. **`.animateContentSize()` on the `Button`** so the width change when the icon becomes an
   indicator is animated rather than a jump.
3. **Derive the indicator's colours from the button's own `ButtonColors`** —
   `containerColor = colors.containerColor`, `indicatorColor = colors.contentColor`. The indicator
   inherits any caller override for free instead of falling back to scheme defaults that may clash
   with a custom button colour.

Note this contradicts the "do not scale it below its default" pitfall below only in appearance:
androidify constrains it to `Modifier.size(24.dp)`, which *is* below the default and *does* cost
morph legibility. It gets away with it because the shape is small, brief and beside a text label
that carries the meaning. Do not generalise it to a standalone page-level spinner.

## Pitfalls

- **The `@OptIn` is still required, including at alpha26.** If it compiled last month and doesn't
  now, check whether you moved from alpha18 to alpha19+.
- `polygons` needs **≥ 2** entries. One polygon = nothing to morph between.
- `LoadingIndicator` requires `androidx.graphics:graphics-shapes` on the classpath (it renders
  `RoundedPolygon`s). It is a transitive dependency of material3 but declare it if you also touch
  `MaterialShapes` directly.
- Do not scale it below its default. The seven-shape morph is illegible at 16dp — use a plain
  `CircularProgressIndicator` for in-button spinners at that size, or accept the wavy circular
  (§3) at 24dp.
- Dead-import check: LastChat imports `LoadingIndicator` in `ui/pages/chat/ChatList.kt:48` and never
  calls it. Grep before assuming an app uses what it imports.

---

# 3. Wavy progress indicators

## Signatures

`[ANDROIDX]`. Opt-in in 1.4.0: `ExperimentalMaterial3ExpressiveApi`. **Promoted 1.5.0-alpha18** —
these are stable on current alphas, unlike `LoadingIndicator`.

Independently corroborated at androidx-main `360e8cba7ae6` (post-alpha26): the canonical
`ProgressIndicatorSamples.kt` — which holds the wavy `@Sampled` functions — carries **zero**
`ExperimentalMaterial3ExpressiveApi` occurrences **and zero** `ExperimentalMaterial3Api`
occurrences. No opt-in of any kind. That is the strongest available signal that the wavy family is
genuinely stable and not merely un-annotated by oversight.

**Also promoted to stable in 1.5.0-alpha25 (I794d0)** **[verified — release note]**:
`LinearTrackStopIndicatorSize`, `LinearIndicatorTrackGapSize`, and `CircularIndicatorTrackGapSize`.
These are the three `WavyProgressIndicatorDefaults` constants used as defaults for the `stopSize`
and `gapSize` parameters below. Practical effect: you can now name them explicitly — e.g.
`stopSize = WavyProgressIndicatorDefaults.LinearTrackStopIndicatorSize` as vivi-music's `WavySlider`
does (see `sliders-and-inputs.md` §5.1) — **without any opt-in**, on alpha25+. Before alpha25 those
three constants were experimental even though the composables around them were not, which produced
the confusing situation of an opt-in warning on a default value.

Everything else in `WavyProgressIndicatorDefaults` was already stable from alpha18.

```kotlin
@Composable
fun LinearWavyProgressIndicator(
    progress: () -> Float,
    modifier: Modifier = Modifier,
    color: Color = WavyProgressIndicatorDefaults.indicatorColor,
    trackColor: Color = WavyProgressIndicatorDefaults.trackColor,
    stroke: Stroke = WavyProgressIndicatorDefaults.linearIndicatorStroke,
    trackStroke: Stroke = WavyProgressIndicatorDefaults.linearTrackStroke,
    gapSize: Dp = WavyProgressIndicatorDefaults.LinearIndicatorTrackGapSize,
    stopSize: Dp = WavyProgressIndicatorDefaults.LinearTrackStopIndicatorSize,
    amplitude: (progress: Float) -> Float = WavyProgressIndicatorDefaults.indicatorAmplitude,
    wavelength: Dp = WavyProgressIndicatorDefaults.LinearDeterminateWavelength,
    waveSpeed: Dp = wavelength
)

@Composable
fun LinearWavyProgressIndicator(
    modifier: Modifier = Modifier,
    color: Color = WavyProgressIndicatorDefaults.indicatorColor,
    trackColor: Color = WavyProgressIndicatorDefaults.trackColor,
    stroke: Stroke = WavyProgressIndicatorDefaults.linearIndicatorStroke,
    trackStroke: Stroke = WavyProgressIndicatorDefaults.linearTrackStroke,
    gapSize: Dp = WavyProgressIndicatorDefaults.LinearIndicatorTrackGapSize,
    @FloatRange(from = 0.0, to = 1.0) amplitude: Float = 1f,
    wavelength: Dp = WavyProgressIndicatorDefaults.LinearIndeterminateWavelength,
    waveSpeed: Dp = wavelength
)

@Composable
fun CircularWavyProgressIndicator(
    progress: () -> Float,
    modifier: Modifier = Modifier,
    color: Color = WavyProgressIndicatorDefaults.indicatorColor,
    trackColor: Color = WavyProgressIndicatorDefaults.trackColor,
    stroke: Stroke = WavyProgressIndicatorDefaults.circularIndicatorStroke,
    trackStroke: Stroke = WavyProgressIndicatorDefaults.circularTrackStroke,
    gapSize: Dp = WavyProgressIndicatorDefaults.CircularIndicatorTrackGapSize,
    amplitude: (progress: Float) -> Float = WavyProgressIndicatorDefaults.indicatorAmplitude,
    wavelength: Dp = WavyProgressIndicatorDefaults.CircularWavelength,
    waveSpeed: Dp = wavelength
)

@Composable
fun CircularWavyProgressIndicator(
    modifier: Modifier = Modifier,
    color: Color = WavyProgressIndicatorDefaults.indicatorColor,
    trackColor: Color = WavyProgressIndicatorDefaults.trackColor,
    stroke: Stroke = WavyProgressIndicatorDefaults.circularIndicatorStroke,
    trackStroke: Stroke = WavyProgressIndicatorDefaults.circularTrackStroke,
    gapSize: Dp = WavyProgressIndicatorDefaults.CircularIndicatorTrackGapSize,
    @FloatRange(from = 0.0, to = 1.0) amplitude: Float = 1f,
    wavelength: Dp = WavyProgressIndicatorDefaults.CircularWavelength,
    waveSpeed: Dp = wavelength
)
```

Note the **determinate `amplitude` is a lambda `(Float) -> Float`; the indeterminate `amplitude` is
a plain `Float`.** Passing `amplitude = 1f` to the determinate overload will not compile.

`waveSpeed` defaults to `wavelength`, i.e. **one wavelength per second.**

## ⚠️ `stroke` is in PIXELS. `wavelength` and `gapSize` are `Dp`.

This is the single biggest footgun in the whole progress API.

`CircularProgressIndicator` takes `strokeWidth: Dp`. `CircularWavyProgressIndicator` takes
`stroke: androidx.compose.ui.graphics.drawscope.Stroke`, whose `width` is a **`Float` in pixels**.
Same for `trackStroke`. Meanwhile `wavelength`, `gapSize`, `stopSize` are all `Dp`.

If you write `Stroke(width = 16f)` you get a hairline on a 3× device. If you pass a `Dp.value` you
get a stroke that changes physical size per device.

**Always convert:**

```kotlin
stroke = Stroke(
    width = with(LocalDensity.current) { 16.dp.toPx() },
    cap = StrokeCap.Round,
)
```

Or hoist it once and `remember` it:

```kotlin
// [CORPUS vivi-music] .../vivimusic/AudioDeviceBottomSheet.kt
val density = LocalDensity.current
val strokeWidthPx = with(density) { 4.dp.toPx() }
val wavyStroke = remember(strokeWidthPx) {
    Stroke(width = strokeWidthPx, cap = StrokeCap.Round)
}
```

The androidx defaults do exactly this — `[ANDROIDX]` `WavyProgressIndicatorDefaults`, verbatim:

```kotlin
public val linearIndicatorStroke: Stroke
    @Composable
    get() =
        Stroke(
            width = with(LocalDensity.current) {
                LinearProgressIndicatorTokens.ActiveThickness.toPx()
            },
            cap = StrokeCap.Round,
        )
```

`cap = StrokeCap.Round` is not optional decoration — square caps make the wave crests look chopped.

## `WavyProgressIndicatorDefaults`

`[ANDROIDX]`, verbatim:

```kotlin
public object WavyProgressIndicatorDefaults {

    public val ProgressAnimationSpec: AnimationSpec<Float> =
        tween(
            durationMillis = MotionTokens.DurationLong2.toInt(),
            easing = MotionTokens.EasingLinearCubicBezier,
        )

    public val indicatorColor: Color @Composable get() = ProgressIndicatorTokens.ActiveIndicatorColor.value
    public val trackColor: Color     @Composable get() = ProgressIndicatorTokens.TrackColor.value

    public val linearIndicatorStroke: Stroke   // Stroke(LinearProgressIndicatorTokens.ActiveThickness.toPx(), Round)
    public val circularIndicatorStroke: Stroke // Stroke(CircularProgressIndicatorTokens.ActiveThickness.toPx(), Round)
    public val linearTrackStroke: Stroke       // Stroke(LinearProgressIndicatorTokens.TrackThickness.toPx(), Round)
    public val circularTrackStroke: Stroke     // Stroke(CircularProgressIndicatorTokens.TrackThickness.toPx(), Round)

    public val LinearDeterminateWavelength: Dp = LinearProgressIndicatorTokens.ActiveWaveWavelength
    public val LinearIndeterminateWavelength: Dp =
        LinearProgressIndicatorTokens.IndeterminateActiveWaveWavelength
    public val LinearContainerHeight: Dp = LinearProgressIndicatorTokens.WaveHeight
    public val LinearContainerWidth: Dp = 240.dp
    public val LinearTrackStopIndicatorSize: Dp = LinearProgressIndicatorTokens.StopSize
    public val CircularContainerSize: Dp = CircularProgressIndicatorTokens.WaveSize
    public val CircularWavelength: Dp = CircularProgressIndicatorTokens.ActiveWaveWavelength
    public val LinearIndicatorTrackGapSize: Dp = LinearProgressIndicatorTokens.TrackActiveSpace
    public val CircularIndicatorTrackGapSize: Dp = CircularProgressIndicatorTokens.TrackActiveSpace
    // ^ these two and LinearTrackStopIndicatorSize above: promoted to STABLE in 1.5.0-alpha25 (I794d0)

    public val indicatorAmplitude: (progress: Float) -> Float = { progress ->
        if (progress <= 0.1f || progress >= 0.95f) {
            0f
        } else {
            1f
        }
    }
}
```

**The only literal is `LinearContainerWidth = 240.dp`**; everything else is token-indirected and the
dp are `[UNVERIFIED]`.

**Behavioural detail worth knowing**: the default `indicatorAmplitude` **flattens the wave to zero
below 10% and above 95% progress.** The wave only animates in the middle of the range. If your bar
looks flat at the start and end, that is by design — override `amplitude = { 1f }` if you want it
wavy throughout (vivi-music does, §3.3).

## Parameter cheat sheet

| Param | Type | Meaning | Tuning |
| --- | --- | --- | --- |
| `stroke` / `trackStroke` | `Stroke` (**px**) | Thickness + cap of the active/inactive line | 4dp default, 8dp for the expressive thick variant, 12–16dp for hero rings |
| `wavelength` | `Dp` | Distance between wave crests | Bigger indicator → bigger wavelength. Tomato: 60dp on a ~315dp ring, 42dp on a 250dp ring. vivi-music: 16dp on a 6dp-tall bar |
| `gapSize` | `Dp` | Gap between the indicator and the track | 4dp default (accessibility contrast). Tomato uses 8dp on hero rings, vivi-music 3dp on a 56dp ring |
| `stopSize` | `Dp` | Endpoint marker on linear determinate | 4dp default, accessibility affordance |
| `amplitude` | `(Float)->Float` (determinate) / `Float` (indeterminate) | Wave height, 0 = flat | Default zeroes below 10% and above 95% |
| `waveSpeed` | `Dp` | Distance travelled per second | Defaults to `wavelength` = one wavelength/sec |

## Minimum size — wavy fails small

`[GOOGLE]`: **"at very small sizes, the wavy shape may not be as visible."**

Practical threshold from the corpus: **below roughly 40dp the wave stops reading.** All the
corpus circular-wavy sites are either ≥ 36dp (where it's a real ring) or exactly 24dp used as an
inline in-button spinner where the wave is essentially decoration and the motion is what carries
the message.

Rules:

- Hero ring, 80–320dp → wavy, thick stroke, tuned wavelength. This is the point of the component.
- 36–56dp → wavy still reads if you shrink `gapSize` and keep the stroke ≥ 4dp.
- < 36dp → fall back to `CircularProgressIndicator` / `LinearProgressIndicator`, or accept that
  `CircularWavyProgressIndicator(Modifier.size(24.dp))` is just a spinner with texture.
- **Don't** apply wave to a tiny indicator "for consistency" — you pay the complexity and get no
  signal.

---

# 4. The best circular pattern in the corpus — Tomato's timer

Tomato's timer swaps between a **smooth** ring in focus mode and a **wavy** ring in break mode, and
ties the two together with `sharedBounds` using **matching keys across two screens**, so the ring
morphs across a Nav3 destination change into the always-on display.

`[CORPUS Tomato]`
`shared/src/androidMain/kotlin/org/nsh07/pomodoro/ui/timerScreen/TimerScreen.kt:325-380`:

```kotlin
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
    CircularWavyProgressIndicator(
        progress = progress,
        modifier = Modifier
            .sharedBounds(
                sharedContentState = this@TimerScreen.rememberSharedContentState(
                    "break progress"
                ),
                animatedVisibilityScope = LocalNavAnimatedContentScope.current
            )
            .fillMaxWidth(0.9f)
            .aspectRatio(1f),
        color = color,
        trackColor = colorContainer,
        stroke = Stroke(
            width = with(LocalDensity.current) {
                16.dp.toPx()
            },
            cap = StrokeCap.Round,
        ),
        trackStroke = Stroke(
            width = with(LocalDensity.current) {
                16.dp.toPx()
            },
            cap = StrokeCap.Round,
        ),
        wavelength = 60.dp,
        gapSize = 8.dp
    )
}
```

Imports from the same file (60-119):

```kotlin
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.CircularWavyProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
```

The AOD screen repeats the **exact same structure and the exact same shared-content keys** at 250dp
with a 12dp stroke and 42dp wavelength — `[CORPUS Tomato]`
`shared/src/androidMain/kotlin/org/nsh07/pomodoro/ui/AlwaysOnDisplay.kt:216-262`:

```kotlin
if (!timerState.infiniteFocus) {
    if (timerState.timerMode == TimerMode.FOCUS) {
        CircularProgressIndicator(
            progress = progress,
            modifier = Modifier
                .sharedBounds(
                    sharedContentState = this@AlwaysOnDisplay.rememberSharedContentState(
                        "focus progress"
                    ),
                    animatedVisibilityScope = LocalNavAnimatedContentScope.current
                )
                .size(250.dp),
            color = primary,
            trackColor = secondaryContainer,
            strokeWidth = 12.dp,
            gapSize = 8.dp,
        )
    } else {
        CircularWavyProgressIndicator(
            progress = progress,
            modifier = Modifier
                .sharedBounds(
                    sharedContentState = this@AlwaysOnDisplay.rememberSharedContentState(
                        "break progress"
                    ),
                    animatedVisibilityScope = LocalNavAnimatedContentScope.current
                )
                .size(250.dp),
            color = primary,
            trackColor = secondaryContainer,
            stroke = Stroke(
                width = with(LocalDensity.current) {
                    12.dp.toPx()
                },
                cap = StrokeCap.Round,
            ),
            trackStroke = Stroke(
                width = with(LocalDensity.current) {
                    12.dp.toPx()
                },
                cap = StrokeCap.Round,
            ),
            wavelength = 42.dp,
            gapSize = 8.dp
        )
    }
} else {
    Box(modifier = Modifier.size(250.dp))
}
```

### What to take from it

1. **Wavy = a mode signal, not decoration.** Smooth ring = focus (work, serious); wavy ring = break
   (relaxed). The wave carries meaning. That is the correct reason to use it.
2. **`progress` is passed as a lambda, hoisted outside**, so the ticking value doesn't recompose the
   whole subtree — only the indicator's draw invalidates. Never write `progress = { millisLeft /
   total }` inline if you can compute it outside.
3. **Both branches share content keys with the other screen.** `"focus progress"` and
   `"break progress"` exist on both `TimerScreen` and `AlwaysOnDisplay`; both sit under the app-level
   `SharedTransitionLayout`, so tapping into AOD morphs the ring across the Nav3 destination change
   instead of cross-fading.
4. **Wavelength scales with the ring**: 60dp on ~315dp, 42dp on 250dp. Roughly wavelength ≈ ring/5–6.
5. **`gapSize = 8.dp`** — double the 4dp default, because at a 16dp stroke the default gap
   disappears.
6. **`Box(Modifier.size(250.dp))` in the else branch.** When there's no progress to show, reserve the
   space. Never let the layout jump — "don't allow layout shift during loading" is one of Material's
   eight motion principles.

---

# 5. More wavy recipes

## 5.1 Determinate ring with a custom stroke — battery level

The clearest single example of the whole API surface: `stroke`, `trackStroke`, `gapSize`,
`progress` lambda, plus the hoisted-and-remembered `Stroke`.

`[CORPUS vivi-music]` `.../vivimusic/AudioDeviceBottomSheet.kt`:

```kotlin
if (activeDevice?.type == AudioDeviceType.BLUETOOTH && activeDevice.batteryLevel != null) {
    val density = LocalDensity.current
    val strokeWidthPx = with(density) { 4.dp.toPx() }
    val wavyStroke = remember(strokeWidthPx) {
        Stroke(width = strokeWidthPx, cap = StrokeCap.Round)
    }

    Box(
        modifier = Modifier
            .padding(start = 8.dp)
            .size(56.dp),
        contentAlignment = Alignment.Center
    ) {
        CircularWavyProgressIndicator(
            progress = { activeDevice.batteryLevel.toFloat() / 100f },
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.primary,
            trackColor = MaterialTheme.colorScheme.primaryContainer,
            stroke = wavyStroke,
            trackStroke = wavyStroke,
            gapSize = 3.dp
        )
        Text(
            text = "${activeDevice.batteryLevel}%",
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.ExtraBold,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center
        )
    }
} else {
    Spacer(modifier = Modifier.width(1.dp))
}
```

Note `gapSize = 3.dp` — below the 4dp default because the ring is only 56dp. And the same `Stroke`
instance reused for both indicator and track.

## 5.2 Ring + overlay content — playlist download

`[CORPUS vivi-music]` `.../ui/menu/PlaylistMenu.kt` — the same 80dp ring, two states:

```kotlin
// complete
CircularWavyProgressIndicator(
    progress = { 1f },
    modifier = Modifier.size(80.dp)
)
Icon(
    painter = painterResource(R.drawable.check),
    contentDescription = null,
    modifier = Modifier.size(36.dp),
    tint = MaterialTheme.colorScheme.primary
)

// in progress
CircularWavyProgressIndicator(
    progress = { progressValue },
    modifier = Modifier.size(80.dp)
)
Text(
    text = "${(progressValue * 100).toInt()}%",
    style = MaterialTheme.typography.titleMedium
)
```

At `progress = 1f` the default `indicatorAmplitude` returns 0, so the completed ring is a clean
circle behind the check. That is the default doing exactly the right thing.

## 5.3 Determinate linear bar with forced amplitude

`[CORPUS vivi-music]` `.../ui/screens/settings/integrations/DiscordSettings.kt` — song progress.
Shows `amplitude` and `wavelength` on the determinate overload:

```kotlin
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun SongProgressBar(currentTimeMillis: Long, durationMillis: Long) {
    val progress = if (durationMillis > 0) currentTimeMillis.toFloat() / durationMillis else 0f

    Column(modifier = Modifier.fillMaxWidth()) {
        Spacer(modifier = Modifier.height(16.dp))

        LinearWavyProgressIndicator(
            progress = { progress },
            amplitude = { 1f },
            wavelength = 16.dp,
            modifier = Modifier
                .fillMaxWidth()
                .height(6.dp),
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = makeTimeString(currentTimeMillis),
                modifier = Modifier.weight(1f),
                textAlign = TextAlign.Start,
                fontSize = 12.sp
            )
            Text(
                text = makeTimeString(durationMillis),
                modifier = Modifier.weight(1f),
                textAlign = TextAlign.End,
                fontSize = 12.sp
            )
        }
    }
}
```

`amplitude = { 1f }` overrides the default flatten-at-the-ends behaviour so the bar is wavy from 0%
to 100% — correct for a media seek bar, where a flat start reads as "broken", not "just started".

## 5.4 The three best LastChat sites

LastChat has 12 `LinearWavyProgressIndicator` sites, all **indeterminate, all "background job
running"**. Three patterns cover all of them.

**Full-width bar + caption** — the idiom for "a thing is refreshing in this section".
`[CORPUS LastChat]` `.../ui/pages/setting/SettingDisplayPage.kt:278-289`:

```kotlin
if (modelCatalogStatus.isRefreshing) {
    Column(
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        LinearWavyProgressIndicator(modifier = Modifier.fillMaxWidth())
        Text(
            text = stringResource(R.string.setting_display_model_catalog_refreshing),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
```

**Bar inside an `AnimatedVisibility`** so it slides in rather than popping.
`[CORPUS LastChat]` `.../ui/components/ai/McpPicker.kt:134-146`:

```kotlin
AnimatedVisibility(loading) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
        modifier = Modifier.padding(vertical = 4.dp)
    ) {
        LinearWavyProgressIndicator()
        Text(
            text = stringResource(id = R.string.mcp_picker_syncing),
            style = MaterialTheme.typography.bodyLarge
        )
    }
}
```

**`weight(1f)` inside a `Row`** — the bar fills the remaining width beside a label or a button.
`[CORPUS LastChat]` `.../ui/pages/setting/SettingTTSProviderDetailPage.kt:824` and
`.../SettingProviderDetailPage.kt:1536`:

```kotlin
LinearWavyProgressIndicator(modifier = Modifier.weight(1f))
```

**In-button circular spinner** — LastChat's 8 `CircularWavyProgressIndicator` sites are all this,
at 24dp, replacing the icon while work is in flight. `[CORPUS LastChat]`
`.../ui/pages/backup/BackupPage.kt:531-543` (also `:654`, `:797`, `:1013`, `:1035`, `:1061`,
`:1078`):

```kotlin
IconButton(
    onClick = { /* ... */ },
    shape = CircleShape,
) {
    if (isBackingUp) {
        CircularWavyProgressIndicator(
            modifier = Modifier.size(24.dp)
        )
    } else {
        Icon(
            Icons.Rounded.CloudUpload,
            contentDescription = stringResource(R.string.backup_page_backup_now),
        )
    }
}
```

At 24dp the wave barely reads (§3, minimum size). Swapping in place of the icon means the button
never changes size — that part is right. If you want the wave to actually be visible, use ≥36dp or
drop to the non-wavy indicator.

## 5.5 vivi-music's small circular sites

All the same shape — an inline spinner sized to the slot it replaces.
`[CORPUS vivi-music]`:

```kotlin
CircularWavyProgressIndicator(modifier = Modifier.size(24.dp))  // .../ui/player/Player.kt download button
CircularWavyProgressIndicator(modifier = Modifier.size(32.dp))  // .../ui/screens/CommentSheet.kt loading
CircularWavyProgressIndicator(modifier = Modifier.size(24.dp))  // .../ui/screens/CommentSheet.kt pagination
CircularWavyProgressIndicator()                                 // .../ui/menu/LyricsMenu.kt
```

Also at `.../ui/screens/search/SearchScreen.kt` (2×) and
`.../ui/screens/search/suggestions/TabNewsSuggestion.kt`.

## 5.6 A creative determinate use — countdown between lyric lines

`[CORPUS vivi-music]` `.../ui/component/Lyrics.kt` — `IntervalIndicator` fills a wavy ring during
the instrumental gap between sung lines. Two techniques worth stealing:

```kotlin
// 1. progress derived from a time range, then smoothed so it doesn't step per frame
val progress = if (gapEndMs > gapStartMs) {
    ((currentPositionMs - gapStartMs).toFloat() / (gapEndMs - gapStartMs).toFloat()).coerceIn(0f, 1f)
} else 0f
val animatedProgress by animateFloatAsState(
    targetValue = progress,
    animationSpec = tween(durationMillis = 100, easing = LinearEasing),
    label = "intervalProgress"
)

// 2. the whole row's height animates to/from zero via an Animatable, so the indicator
//    expands the layout in rather than popping into reserved space
Box(
    modifier = modifier
        .height(targetHeightDp * rowHeightPx.value)
        .padding(top = 16.dp * rowHeightPx.value)
        .graphicsLayer { this.alpha = alpha.value; this.clip = true },
    contentAlignment = Alignment.Center
) {
    CircularWavyProgressIndicator(
        progress = { animatedProgress },
        modifier = Modifier.size(36.dp).alpha(alpha.value),
        color = color,
        trackColor = color.copy(alpha = 0.2f),
    )
}
```

36dp is right at the wavy legibility floor — it works because the ring is alone on a dark backdrop
with a high-contrast track.

---

# 6. Pull to refresh

## `PullToRefreshBox`

`[ANDROIDX]`:

```kotlin
@Composable
fun PullToRefreshBox(
    isRefreshing: Boolean,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier,
    state: PullToRefreshState = rememberPullToRefreshState(),
    contentAlignment: Alignment = Alignment.TopStart,
    indicator: @Composable BoxScope.() -> Unit = {
        Indicator(
            modifier = Modifier.align(Alignment.TopCenter),
            isRefreshing = isRefreshing,
            state = state,
        )
    },
    enabled: Boolean = true,
    threshold: Dp = PullToRefreshDefaults.PositionalThreshold,
    content: @Composable BoxScope.() -> Unit,
)
```

KDoc: *"PullToRefreshBox is a container that expects a scrollable layout as content and adds gesture
support for manually refreshing when the user swipes downward at the beginning of the content."*

`enabled` + `threshold` were added in **1.5.0-alpha15**; a deprecated overload without them remains.

**The default `indicator` is the baseline M3 arrow indicator.** You get the Expressive one only by
overriding the `indicator` slot.

## Med's expressive pull-to-refresh

Three identical sites in Med (`Tabs/Stats.kt:213-229`, `Tabs/You.kt:397`, `services/MedApp.kt:578`).

`[CORPUS Med]` `app/src/main/kotlin/com/fedeveloper95/med/elements/MainActivity/Tabs/Stats.kt:198-230`:

```kotlin
        isRefreshing = true
        scope.launch {
            delay(1000)
            viewModel.reloadData()
            isRefreshing = false
        }
    }

    PullToRefreshBox(
        state = pullRefreshState,
        isRefreshing = isRefreshing,
        onRefresh = onRefresh,
        modifier = Modifier.fillMaxSize(),
        indicator = {
            Box(
                modifier = Modifier.fillMaxWidth(),
                contentAlignment = Alignment.TopCenter
            ) {
                PullToRefreshDefaults.LoadingIndicator(
                    state = pullRefreshState,
                    isRefreshing = isRefreshing
                )
            }
        }
    ) {
```

Imports (`MedApp.kt:69-71`):

```kotlin
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
```

`PullToRefreshDefaults.LoadingIndicator(state, isRefreshing)` is the drop-in expressive indicator —
it renders the morphing `ContainedLoadingIndicator` and handles the pull offset itself. **This
two-line override is the whole upgrade.**

> ⚠️ **alpha21 re-gating.** `PullToRefreshDefaults.loadingIndicatorColor` and
> `PullToRefreshDefaults.loadingIndicatorContainerColor` were **re-marked experimental in
> 1.5.0-alpha21.** If you customise the indicator's colours you need
> `@OptIn(ExperimentalMaterial3ExpressiveApi::class)` again even though the surrounding APIs are
> stable. The `LoadingIndicator(state, isRefreshing)` call itself is corpus-verified working on
> alpha21.

## vivi-music's hand-built version — determinate on drag

More work, more control: a **determinate** `ContainedLoadingIndicator` whose `progress` tracks the
drag fraction, swapping to the **indeterminate** overload once the refresh starts, with the whole
indicator translated by a spring-animated offset.

`[CORPUS vivi-music]` `.../ui/screens/HomeScreen.kt`:

```kotlin
val pullDensity = LocalDensity.current
val maxOffsetPx = with(pullDensity) { PullToRefreshDefaults.PositionalThreshold.toPx() + 16.dp.toPx() }
val topInsetPx = with(pullDensity) {
    LocalPlayerAwareWindowInsets.current.asPaddingValues().calculateTopPadding().toPx() + 16.dp.toPx()
}
val indicatorHeightPx = with(pullDensity) { 56.dp.toPx() }
val expressiveSpring = MaterialTheme.motionScheme.defaultSpatialSpec<Float>()
val targetFraction = if (isRefreshing) 1f
                     else pullRefreshState.distanceFraction.coerceIn(0f, 1f)
val animatedFraction by animateFloatAsState(
    targetValue = targetFraction,
    animationSpec = expressiveSpring,
    label = "pull_fraction"
)

PullToRefreshBox(
    state = pullRefreshState,
    isRefreshing = isRefreshing,
    onRefresh = viewModel::refresh,
    indicator = {
        if (animatedFraction > 0.001f) {
            val yOffset = lerp(-indicatorHeightPx, topInsetPx, animatedFraction)
            if (isRefreshing) {
                ContainedLoadingIndicator(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .offset { IntOffset(0, yOffset.toInt()) }
                )
            } else {
                ContainedLoadingIndicator(
                    progress = { pullRefreshState.distanceFraction.coerceIn(0f, 1f) },
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .offset { IntOffset(0, yOffset.toInt()) }
                )
            }
        }
    }
) {
```

Key moves: determinate while dragging (the shape morphs as you pull — direct manipulation),
indeterminate while refreshing, spring-animated offset off `MaterialTheme.motionScheme`, and
`if (animatedFraction > 0.001f)` so nothing is composed at rest.

**Use Med's version unless you specifically want the drag-driven morph.** Two lines vs thirty.

---

# 7. Skeletons and shimmer

Material does not ship a skeleton/shimmer component. The corpus answer is a third-party library.

`[CORPUS vivi-music]` — `com.valentinilk.shimmer:compose-shimmer:1.5.0`, declared in
`gradle/libs.versions.toml`:

```toml
shimmer = "1.5.0"
shimmer = { module = "com.valentinilk.shimmer:compose-shimmer", version.ref = "shimmer" }
```

vivi-music wraps it in a small local component set under
`app/src/main/kotlin/com/music/vivi/ui/component/shimmer/`:

- `ShimmerHost.kt` — a `Column` that applies `Modifier.shimmer()` plus a `drawWithContent` +
  `BlendMode` fade so the shimmer tapers at the bottom edge.
- `ListItemPlaceholder.kt`, `GridItemPlaceholder.kt`, `TextPlaceholder.kt`, `ButtonPlaceholder.kt` —
  layout-matched grey blocks. Each placeholder is a `Spacer` with
  `.clip(thumbnailShape).background(MaterialTheme.colorScheme.onSurface)` at the exact dimensions of
  the real content.

Used across `HomeScreen`, `BrowseScreen`, `ExploreScreen`, `ArtistScreen`, `ChartsScreen`,
`MoodAndGenresScreen`, `NewReleaseScreen`, `AccountScreen`, `YouTubeBrowseScreen`, `Lyrics`.

## How this relates to expressive loading

They solve different problems and should not be mixed on the same surface:

| | `LoadingIndicator` | Skeleton / shimmer |
| --- | --- | --- |
| Communicates | "the app is working" | "content of *this shape* is coming here" |
| Best for | An action you triggered; a dialog; a footer | First paint of a content-heavy screen with known layout |
| Layout | Occupies a fixed box | Occupies the exact final layout |
| Expressive fit | The morphing polygon **is** the expressive moment | Neutral; the expressiveness is in the shapes you use for the placeholders |

Rules:

- Use skeletons for the **initial load of a list/grid whose layout you know**. Use
  `LoadingIndicator` for the pagination footer of that same list. vivi-music does exactly this.
- **Never** put a shimmer skeleton and a spinner on the same screen at the same time.
- Match the placeholder's shape to the real content's shape — if your album art is a
  `MaterialShapes.Cookie9Sided`, the placeholder should be too. That is where the expressive shape
  system meets loading.
- Skeletons make the "no layout shift during loading" principle nearly free. Take it.

---

# 8. Anti-patterns

| Don't | Why / Instead |
| --- | --- |
| **Wavy everywhere** | Wavy "is best used when a more expressive style is appropriate." `[GOOGLE]` If every progress bar waves, the wave carries no information. Tomato's rule is the right one: **wavy means something** (break mode vs focus mode). |
| **Wavy below ~40dp** | "At very small sizes, the wavy shape may not be as visible." You pay complexity, get no signal. Fall back to the non-wavy indicator. |
| **Indeterminate spinner for a long wait** | `LoadingIndicator` is for **under five seconds**. Past that a user cannot tell a slow operation from a hung one. Show determinate progress, or a step-by-step status, or both. |
| **`LoadingIndicator` on a process that becomes determinate** | Explicit `[GOOGLE]` do-not: "do not use it if processes transition from indeterminate to determinate states." Use a progress indicator throughout. |
| **Progress that doesn't reflect real progress** | A determinate bar animating 0→90% on a fixed timer and then hanging at 90% is worse than an indeterminate one — it makes a false promise. If you don't know the fraction, say so with an indeterminate form. |
| **Animating a spinner off-screen** | An indicator in a `LazyColumn` item that has scrolled out, or behind a dismissed dialog, burns frames forever. Gate composition on visibility (`if (loading)`, `AnimatedVisibility`), and don't leave an `infiniteRepeatable` running in a `remember` that outlives the UI. |
| **Mixing linear and circular for the same activity** | "Only one type should represent each kind of activity in an app." `[GOOGLE]` Pick one per activity class and be consistent. |
| **Layout shift when loading finishes** | Material's motion principles: don't allow layout shift during loading. Reserve the final size (Tomato's `Box(Modifier.size(250.dp))` else-branch) or use a skeleton. |
| **`Stroke(width = 16f)`** | Pixels, not dp. Convert with `with(LocalDensity.current) { 16.dp.toPx() }`. |
| **Removing the track gap or stop indicator** | The 4dp track gap and 4dp stop indicator exist "to meet modern contrast requirements." `[GOOGLE]` They are accessibility affordances, not decoration. On alpha25+ you can reference `WavyProgressIndicatorDefaults.LinearIndicatorTrackGapSize` / `CircularIndicatorTrackGapSize` / `LinearTrackStopIndicatorSize` with no opt-in — no reason left to hardcode or drop them. |
| **Stripping `@OptIn(ExperimentalMaterial3ExpressiveApi::class)` during an upgrade cleanup** | `MaterialShapes`, `LoadingIndicator` and `ContainedLoadingIndicator` are **still experimental at alpha26** (§2). Most of the rest of the Expressive surface graduated; these three did not. Removing their opt-in breaks the build. |
| **Auto-animating content for >5s with no escape** | Content that "moves, scrolls, or blinks automatically" must be pausable, stoppable or hideable past five seconds. `[GOOGLE — accessibility guidance]` A long indeterminate animation should at minimum be accompanied by a cancel affordance. |

## Accessibility notes

- Determinate vs indeterminate is reported to assistive tech. Choosing wrong misreports state.
- The `LoadingIndicator` "communicate[s] app state and available actions, indicating whether users
  can navigate away." `[GOOGLE]` If the user *can* navigate away during the wait, don't block the UI
  behind a modal scrim.
- Give long-running determinate indicators a live-region text label ("Downloading, 42%") rather than
  relying on the visual fill alone.
- Track gap (4dp), stop indicator (4dp) and — on sliders — the 6dp thumb–track gap all exist for
  contrast. Do not zero them out for a cleaner look.

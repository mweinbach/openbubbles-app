# M3 Expressive Shape Recipes — Working Code

Each recipe: **Use when**, code, **Pitfalls**. *(verbatim: path)* = unmodified from a shipping app in
`/root/work/repos/`. **[canonical-form]** = standard way to write an API whose exact signature was
not readable from source; compile-check it.

Every `MaterialShapes` / `toShape()` / `toPath()` call site needs
`@OptIn(ExperimentalMaterial3ExpressiveApi::class)` — the stable promotion was reverted in
1.5.0-alpha19 and has not returned.

1. Static shapes · 2. Animated morph `Shape` · 3. Shape-by-interaction · 4. Manual press-morph ·
5. Segmented list items · 6. Connected button groups · 7. Loading art · 8. Polygon grids ·
9. Drawing vs clipping · 10. Troubleshooting

---

## 1. Static polygon shapes

### 1.1 Clip an image to a MaterialShape

**Use when** an avatar / album thumb / media tile needs personality and the content is an image you
are happy to crop.

*(verbatim, condensed: `/root/work/repos/vivi-music/app/src/main/kotlin/com/music/vivi/ui/component/Items.kt`)*

```kotlin
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.toShape

Box(
    contentAlignment = Alignment.Center,
    modifier = Modifier.padding(end = 12.dp).size(40.dp)
        .clip(MaterialShapes.Cookie4Sided.toShape())
) {
    AsyncImage(
        model = ImageRequest.Builder(LocalContext.current).data(song.thumbnailUrl?.resize(120, 120)).build(),
        contentDescription = null,
        contentScale = ContentScale.Crop,
        modifier = Modifier.fillMaxSize()
    )
}
```

vivi-music gates this behind a user preference (`ExpressiveSongAlbumImageKey`); the default row shape
is a rounded rect — a good instinct for a dense list.

**Pitfalls** — `Cookie4Sided` is the right cookie at 40dp (`Cookie12Sided` there is a circle,
`Clover8Leaf` is mush). `ContentScale.Crop` is mandatory. Hoist `.toShape()` out of `items { }` (§1.3).

### 1.2 Fill without clipping — `background(color, shape)`

**Use when** the shape is a *backdrop* for an icon, not a mask. Safest polygon pattern: the polygon is
a **sibling**, so nothing gets clipped and nothing gets eaten.

*(verbatim: `/root/work/repos/Tomato/shared/src/commonMain/kotlin/org/nsh07/pomodoro/ui/settingsScreen/DetailPlaceholder.kt`)*

```kotlin
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun DetailPlaceholder(icon: DrawableResource, background: Color = colorScheme.surfaceContainerLow) {
    Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize().background(background)) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(contentAlignment = Alignment.Center) {
                Spacer(
                    Modifier
                        .background(colorScheme.secondaryContainer, MaterialShapes.Cookie12Sided.toShape())
                        .size(128.dp)
                )
                Icon(painterResource(icon), null, tint = colorScheme.onSecondaryContainer,
                     modifier = Modifier.size(72.dp))
            }
        }
    }
}
```

128dp → 72dp (56%) is a good default ratio for cookie-family shapes. Works straight on an `Icon`
modifier too *(Tomato `.../screens/AboutScreen.kt`)*:
`Modifier.size(64.dp).background(colorScheme.secondaryContainer, MaterialShapes.Square.toShape()).padding(8.dp)`

**Pitfalls** — modifier order: `.background(…, shape).padding(8.dp)` insets the *icon*;
`.padding().background()` shrinks the *shape*. `Cookie12Sided` at 64dp reads as a slightly wobbly
circle — fine as a subtle backdrop, wrong if you wanted an obvious cookie.

### 1.3 Hoist shapes once per screen

`toShape()` allocates a `Path`. *(verbatim: `/root/work/repos/vivi-music/.../ui/screens/settings/AboutScreen.kt`)*

```kotlin
val cloverShape = MaterialShapes.Clover4Leaf.toShape()
val cookieShape = MaterialShapes.Cookie7Sided.toShape()
// … then passed down as `iconShape = cookieShape`
```

### 1.4 Rotating mask with upright content (+ polygon shadow)

**Use when** you want spinning-record player artwork: the mask rotates, the photo does not.

*(verbatim, condensed: `/root/work/repos/vivi-music/app/src/main/kotlin/com/music/vivi/ui/player/Thumbnail.kt`)*

```kotlin
val shape = if (rotatingThumbnail) MaterialShapes.Clover8Leaf.toShape()
            else RoundedCornerShape(dimensions.cornerRadius)

Box(
    modifier = Modifier.size(dimensions.thumbnailSize)
        .graphicsLayer { rotationZ = rotation }          // outer: rotates the mask
        .shadow(                                          // polygon shadows work
            elevation = playerThumbnailShadowElevation.dp, shape = shape, clip = false,
            ambientColor = Color.Black.copy(alpha = 0.4f), spotColor = Color.Black.copy(alpha = 0.4f)
        ),
    contentAlignment = Alignment.Center
) {
    Box(
        modifier = Modifier.size(dimensions.thumbnailSize)
            .clip(shape)
            .graphicsLayer { rotationZ = -rotation }      // inner: cancels it for content
    ) { ThumbnailImage(artworkUri = artworkUri?.resize(1200, 1200), cropArtwork = cropAlbumArt) }
}
```

**Pitfalls** — the counter-rotation must be on a **child** of the rotated node, reading the same
`rotation` state, or they drift a frame. Pass `clip = false` to `shadow` or you clip twice.
`Clover8Leaf` is great at 200dp+; do not reuse it for the mini player.

---

## 2. The animated morph `Shape`

There is **no `Morph.toShape()`**. `Morph.toPath(progress, path, startAngle)` is non-`@Composable`
precisely so you can call it inside `Shape.createOutline` or a `DrawScope`. You write the `Shape`.

§2.1 is verbatim shipping code handling bounds/aspect/rotation explicitly — use it for non-square
boxes, spinning shapes, or custom polygon endpoints. §2.2 is the compact form — use it for a square
box and two normalized `MaterialShapes`. If only a corner radius changes, use §4 instead; if the
component has a `shapes =` parameter, use §3 and write no morph at all.

### 2.1 PRIMARY — LastChat's `rememberAvatarShape` (complete file, verbatim)

**Use when** a shape must morph between two `MaterialShapes` in response to state, optionally
spinning while it does.

*(verbatim, two unused imports elided: `/root/work/repos/LastChat/app/src/main/java/me/rerere/rikkahub/ui/hooks/AvatarShape.kt`)*

```kotlin
package me.rerere.rikkahub.ui.hooks

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.shape.GenericShape
import androidx.compose.material3.MaterialShapes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.graphics.Shape
import androidx.graphics.shapes.Morph
import kotlinx.coroutines.isActive

/**
 * Creates a morphing shape that transitions smoothly between a 6-sided cookie and a circle.
 * Uses the official Material You 3 Expressive Cookie6Sided shape with proper Morph animation.
 *
 * @param loading When true, shows the rotating 6-sided cookie. When false, morphs to a circle.
 */
@Composable
fun rememberAvatarShape(loading: Boolean): Shape {
    // Create the morph between cookie and circle
    val cookiePolygon = MaterialShapes.Cookie6Sided
    val circlePolygon = MaterialShapes.Circle
    val morph = remember(cookiePolygon, circlePolygon) { Morph(cookiePolygon, circlePolygon) }

    // Rotation animation for when loading
    val rotation = remember { Animatable(0f) }
    LaunchedEffect(loading) {
        if (loading) {
            // Smooth continuous rotation at a steady pace (one full rotation every 3s)
            val degreesPerSecond = 120f
            var lastFrameTimeNanos = 0L
            while (isActive) {
                val frameTime = withFrameNanos { it }
                if (lastFrameTimeNanos == 0L) {
                    lastFrameTimeNanos = frameTime
                    continue
                }
                val deltaSeconds = (frameTime - lastFrameTimeNanos) / 1_000_000_000f
                lastFrameTimeNanos = frameTime
                val next = (rotation.value + degreesPerSecond * deltaSeconds) % 360f
                rotation.snapTo(next)
            }
        } else {
            // Reset to 0 when not loading
            rotation.animateTo(
                targetValue = 0f,
                animationSpec = spring(dampingRatio = 0.5f, stiffness = 400f)
            )
        }
    }

    // Morph factor: 0f = full cookie, 1f = full circle
    val morphProgress by animateFloatAsState(
        targetValue = if (loading) 0f else 1f,
        animationSpec = spring(dampingRatio = 0.5f, stiffness = 400f),
        label = "cookie_morph"
    )

    val rotationDegrees = rotation.value

    return remember(morph, morphProgress, rotationDegrees) {
        GenericShape { size, _ ->
            // Get the cubics for the current morph progress
            val cubics = morph.asCubics(morphProgress)

            if (cubics.isNotEmpty()) {
                // Calculate bounds
                var minX = Float.MAX_VALUE
                var minY = Float.MAX_VALUE
                var maxX = Float.MIN_VALUE
                var maxY = Float.MIN_VALUE

                cubics.forEach { cubic ->
                    val points = listOf(
                        cubic.anchor0X, cubic.anchor0Y,
                        cubic.control0X, cubic.control0Y,
                        cubic.control1X, cubic.control1Y,
                        cubic.anchor1X, cubic.anchor1Y
                    )
                    for (i in points.indices step 2) {
                        minX = minOf(minX, points[i])
                        minY = minOf(minY, points[i + 1])
                        maxX = maxOf(maxX, points[i])
                        maxY = maxOf(maxY, points[i + 1])
                    }
                }

                val boundsWidth = maxX - minX
                val boundsHeight = maxY - minY

                if (boundsWidth > 0 && boundsHeight > 0) {
                    val scaleX = size.width / boundsWidth
                    val scaleY = size.height / boundsHeight
                    val scale = minOf(scaleX, scaleY)

                    // Calculate center offset
                    val offsetX = (size.width - boundsWidth * scale) / 2f - minX * scale
                    val offsetY = (size.height - boundsHeight * scale) / 2f - minY * scale

                    // Apply rotation if needed
                    val centerX = size.width / 2f
                    val centerY = size.height / 2f
                    val rotationRad = Math.toRadians(rotationDegrees.toDouble()).toFloat()
                    val cos = kotlin.math.cos(rotationRad)
                    val sin = kotlin.math.sin(rotationRad)

                    fun transformPoint(x: Float, y: Float): Pair<Float, Float> {
                        // Scale and translate
                        var tx = x * scale + offsetX
                        var ty = y * scale + offsetY

                        // Rotate around center
                        if (rotationDegrees != 0f) {
                            val dx = tx - centerX
                            val dy = ty - centerY
                            tx = centerX + (dx * cos - dy * sin)
                            ty = centerY + (dx * sin + dy * cos)
                        }

                        return Pair(tx, ty)
                    }

                    // Build the path from cubics
                    cubics.forEachIndexed { index, cubic ->
                        val (ax0, ay0) = transformPoint(cubic.anchor0X, cubic.anchor0Y)
                        val (cx0, cy0) = transformPoint(cubic.control0X, cubic.control0Y)
                        val (cx1, cy1) = transformPoint(cubic.control1X, cubic.control1Y)
                        val (ax1, ay1) = transformPoint(cubic.anchor1X, cubic.anchor1Y)

                        if (index == 0) {
                            moveTo(ax0, ay0)
                        }
                        cubicTo(cx0, cy0, cx1, cy1, ax1, ay1)
                    }

                    close()
                }
            }
        }
    }
}
```

Call site: `Box(Modifier.size(48.dp).clip(rememberAvatarShape(loading = isThinking))) { AsyncImage(...) }`

**Why each piece is there** — `Morph` is remembered against the two **polygons**, never against
progress (rebuilding per frame is the classic stutter bug, §10.4). `asCubics(progress)` returns cubics
in polygon space, and the **intermediate** morph is not guaranteed normalized even though both
endpoints are — hence per-frame bounds. `minOf(scaleX, scaleY)` keeps the shape uniform in a
non-square box. Rotation is applied to already-scaled points around the box center so the shape spins
in place; `graphicsLayer { rotationZ }` on the clipped node would rotate the *content* too.
`withFrameNanos` + `snapTo` gives frame-accurate constant angular velocity with no hitch at the 360°
wrap (unlike `infiniteRepeatable`). `spring(dampingRatio = 0.5f, stiffness = 400f)` is bouncy, i.e.
spatial-flavoured, correct for shape. `remember(morph, morphProgress, rotationDegrees)` means a new
`Shape` is allocated per frame **only while animating**, stable at rest.

**Pitfalls** — `GenericShape`'s lambda is a `Path` builder scope; `moveTo`/`cubicTo`/`close` apply to
the implicit path, do not make your own. Keep the empty-`cubics` guard (an unclosed empty path throws
on some Skia paths). `Float.MIN_VALUE` in Kotlin is the smallest **positive** float — this works only
because these polygons always have positive-x points; on arbitrary geometry use `-Float.MAX_VALUE`.

### 2.2 Compact alternative — `Morph.toPath` + `Matrix` [canonical-form]

```kotlin
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
private class MorphShape(
    private val morph: Morph,
    private val progress: Float,
    private val startAngle: Int = 0,
) : Shape {
    private val matrix = Matrix()   // androidx.compose.ui.graphics.Matrix

    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val path = morph.toPath(progress = progress, startAngle = startAngle)  // NOT @Composable
        matrix.reset()
        matrix.scale(size.width, size.height)   // unit box -> pixels
        path.transform(matrix)
        return Outline.Generic(path)
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun rememberMorphShape(start: RoundedPolygon, end: RoundedPolygon, progress: Float): Shape {
    val morph = remember(start, end) { Morph(start, end) }
    return remember(morph, progress) { MorphShape(morph, progress) }
}
```

Drive `progress` from `animateFloatAsState(if (pressed) 1f else 0f, MaterialTheme.motionScheme.fastSpatialSpec())`
with a `MutableInteractionSource` you also pass to the clickable.

**Pitfalls** — `matrix.scale(w, h)` assumes 0..1 output; an off-center or clipped result means that
assumption failed, fall back to §2.1. A **bouncy** spring overshoots past 1f / below 0f and `Morph`
extrapolates, which can self-intersect at the peak — `coerceIn(0f, 1f)`, or use `fastEffectsSpec()`
for the morph and put the bounce on scale. Use `androidx.compose.ui.graphics.Matrix`, **not**
`android.graphics.Matrix`. The `matrix` field is mutated in `createOutline` — do not share one
`MorphShape` across two nodes.

---

## 3. Shape-by-interaction the easy way

**Always check for a `shapes =` parameter before hand-rolling a morph.** These are themeable, respect
`MaterialTheme.motionScheme`, handle press/release/cancel/checked correctly, cost one argument.

`shapeByInteraction` is **not a public API** — the interaction-driven morph is applied internally from
the `ButtonShapes` / `ToggleButtonShapes` you hand the component. Nothing to call yourself.

### 3.1 `ButtonDefaults.shapes()` [verified]

```kotlin
@Composable fun shapes(): ButtonShapes                      // = MaterialTheme.shapes.defaultButtonShapes
@Composable fun shapes(shape: Shape? = null, pressedShape: Shape? = null): ButtonShapes
@Composable fun shapesFor(buttonHeight: Dp): ButtonShapes   // ButtonShapes = { shape, pressedShape }
```

```kotlin
Button(onClick = onPlay, shapes = ButtonDefaults.shapes()) { Text("Play") }
```

Custom circle → squircle *(verbatim, condensed: `/root/work/repos/vivi-music/app/src/main/kotlin/com/music/vivi/WelcomeActivity.kt`)*:

```kotlin
val shapes = ButtonDefaults.shapes(shape = CircleShape, pressedShape = RoundedCornerShape(20.dp))
Button(
    onClick = onClick,
    modifier = modifier,
    colors = ButtonDefaults.buttonColors(containerColor = containerColor, contentColor = contentColor),
    shapes = shapes
) { /* AnimatedContent between an arrow Icon and a Text label */ }
```

**Sizing**: feed one `Dp` to the whole `*For` family — `shapesFor(h)`,
`contentPaddingFor(h, hasStartIcon, hasEndIcon)`, `iconSizeFor(h)`, `iconSpacingFor(h)`,
`textStyleFor(h)`. Prefer the 3-arg `contentPaddingFor` (the 1-arg overload was re-marked experimental
in alpha21).

### 3.2 `IconButtonDefaults.shapes()` [verified]

```kotlin
fun shapes(): IconButtonShapes
fun shapes(shape: Shape? = null, pressedShape: Shape? = null): IconButtonShapes
fun toggleableShapes(): IconToggleButtonShapes
fun toggleableShapes(shape: Shape? = null, pressedShape: Shape? = null, checkedShape: Shape? = null): IconToggleButtonShapes
```

```kotlin
// zero-arg, verbatim: /root/work/repos/Tomato/.../settingsScreen/screens/AboutScreen.kt
FilledTonalIconButton(onClick = { uriHandler.openUri(url) }, shapes = IconButtonDefaults.shapes()) { … }

// custom, verbatim: /root/work/repos/vivi-music/.../WelcomeActivity.kt
val languageButtonShapes = IconButtonDefaults.shapes(shape = CircleShape, pressedShape = RoundedCornerShape(12.dp))
FilledTonalIconButton(
    onClick = { … },
    modifier = Modifier.size(languageButtonWidth),
    shapes = languageButtonShapes,
    colors = IconButtonDefaults.filledTonalIconButtonColors(
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        contentColor = MaterialTheme.colorScheme.onSurfaceVariant
    )
) { Icon(painterResource(R.drawable.language), "Language", Modifier.size(28.dp)) }
```

**Pitfall** — `IconButton` has two overloads, `shape: Shape` and `shapes: IconButtonShapes`. Pass
`shapes = …` or you get a static shape and no morph. Same for `Filled*`/`Outlined*` and the
`IconToggleButton` family.

### 3.3 `ToggleButtonDefaults.shapesFor()` / `ToggleButtonShapes(...)` [verified names]

```kotlin
fun shapesFor(buttonHeight: Dp): ToggleButtonShapes   // ToggleButtonShapes = { shape, pressedShape, checkedShape }
ToggleButtonShapes(shape: Shape, pressedShape: Shape, checkedShape: Shape)   // constructor — for a custom silhouette
```

`ToggleButtonDefaults.shapes(...)` — both the zero-arg and the by-shape overloads — is
`DeprecationLevel.HIDDEN` on **1.5.0-alpha25+**: invisible to Kotlin source, so alpha24-era call
sites fail to compile rather than warn.

`ToggleButton` defaults to `shapesFor(ButtonDefaults.MinHeight)` and already morphs round ↔ square on
press and check. Override only for a specific silhouette.

Med's asymmetric-corner theme selector *(verbatim:
`/root/work/repos/Med/app/src/main/kotlin/com/fedeveloper95/med/SettingsActivity.kt` lines 273-299)*:

```kotlin
ToggleButton(
    checked = currentTheme == THEME_SYSTEM,
    onCheckedChange = {
        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
        onThemeChanged(THEME_SYSTEM)
    },
    modifier = Modifier.fillMaxWidth().height(40.dp),
    shapes = ToggleButtonDefaults.shapes(
        shape = RoundedCornerShape(topStartPercent = 50, topEndPercent = 50, bottomStartPercent = 15, bottomEndPercent = 15),
        checkedShape = RoundedCornerShape(50)
    ),
    colors = ToggleButtonDefaults.colors(
        containerColor = Color.Transparent,
        checkedContainerColor = MaterialTheme.colorScheme.primary,
        contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
        checkedContentColor = MaterialTheme.colorScheme.onPrimary
    ),
    border = if (currentTheme == THEME_SYSTEM) null else BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
) { Row(Arrangement.Center, Alignment.CenterVertically) { Icon(...); Spacer(Modifier.width(8.dp)); Text(...) } }
```

That quote is Med's alpha20-era source and **does not compile on alpha25+** — swap the factory for the
constructor: `shapes = ToggleButtonShapes(shape = …, checkedShape = …)`.

Worth stealing: unchecked is pill-top / squared-bottom (a "tab"), checked is a full pill, and the
border exists only when unchecked — so checking removes the outline *and* rounds the bottom in one
motion. `pressedShape` is omitted and falls back to the default.

**Pitfall** — `RoundedCornerShape(topStartPercent = 50, …)` and `RoundedCornerShape(50)` are **Int
percents**; `RoundedCornerShape(50.dp)` is dp. `RoundedCornerShape(20)` is 20 percent, not 20dp.

### 3.4 Chip shape morphing

`FilterChip`, `ElevatedFilterChip`, `InputChip` gained **shape-morphing overloads in 1.5.0-alpha18**;
the morphing overload takes a shapes object rather than a single `shape`.

```kotlin
// [canonical-form] — the overload exists (alpha18 release note); exact param name/type UNVERIFIED.
FilterChip(selected = selected, onClick = { … }, label = { Text("Rock") },
           shapes = FilterChipDefaults.shapes())   // name UNVERIFIED — check autocomplete
```

Do not guess further. If it does not resolve, fall back to §4 with the chip's `shape` parameter, or
upgrade past alpha18. (Adjacent: chip `horizontalSpacing` became `horizontalArrangement` in alpha15;
the old overload was **removed** in alpha16.)

---

## 4. Manual press-morph when there is no `shapes =` param

### 4.1 Cheapest — `animateIntAsState` on corner **percent**

**Use when** you want the press morph and nothing else. `RoundedCornerShape(percent: Int)` is
percent-of-min-dimension, so 50 = pill and 15 = squircle regardless of size.

*(verbatim: `/root/work/repos/Med/app/src/main/kotlin/com/fedeveloper95/med/MainActivity.kt` lines 442-467)*

```kotlin
@Composable
fun ExpressiveTextButton(onClick: () -> Unit, text: String, contentColor: Color = MaterialTheme.colorScheme.primary) {
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
        colors = ButtonDefaults.textButtonColors(contentColor = contentColor),
        interactionSource = interactionSource
    ) { Text(text, fontFamily = GoogleSansFlex, style = MaterialTheme.typography.titleMedium) }
}
```

The same helper exists in Med for a 44dp `Surface`-based icon button (lines 375-400), and vivi-music
ships the identical idiom with `tween(200)` instead of a spring under the label `"btnMorph"`
(`.../ui/component/UpdaterComponents.kt`, plus five files in Med). Prefer the spring — a shape change
is a spatial property and should inherit overshoot. Better still:
`MaterialTheme.motionScheme.fastSpatialSpec<Int>()`.

**Pitfalls** — **you must pass your own `interactionSource` to the component**, otherwise it makes its
own, `collectIsPressedAsState()` never fires, and nothing animates (the #1 failure of this pattern).
`animateIntAsState` quantizes to whole percent: invisible at 44dp, visibly stepped above ~300dp — use
`animateFloatAsState` + `CornerSize` for large surfaces. 50% of the min dimension is a pill on a wide
button but a **circle** on a square; check both.

### 4.2 Per-corner lerp with a custom `Shape`

**Use when** the resting shape has **different corners per position** (a middle card at 4dp, an end
card at 20/4) and all four should round out toward a common pressed radius. `animateIntAsState` cannot
express that.

*(verbatim, condensed: `/root/work/repos/vivi-music/app/src/main/kotlin/com/music/vivi/WelcomeActivity.kt`)*

```kotlin
val interactionSource = remember { MutableInteractionSource() }
val isPressed by interactionSource.collectIsPressedAsState()
val pressProgress by animateFloatAsState(
    targetValue = if (isPressed) 1f else 0f,
    animationSpec = tween(durationMillis = 200),
    label = "anim_shape"
)

val animatedShape = remember(shape, pressProgress) {
    if (shape is RoundedCornerShape) {
        object : Shape {
            override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
                val targetPx = with(density) { 20.dp.toPx() }
                fun lerp(start: Float, stop: Float, fraction: Float) = (1 - fraction) * start + fraction * stop

                val ts = lerp(shape.topStart.toPx(size, density), targetPx, pressProgress)
                val te = lerp(shape.topEnd.toPx(size, density), targetPx, pressProgress)
                val bs = lerp(shape.bottomStart.toPx(size, density), targetPx, pressProgress)
                val be = lerp(shape.bottomEnd.toPx(size, density), targetPx, pressProgress)

                return Outline.Rounded(
                    RoundRect(
                        rect = Rect(0f, 0f, size.width, size.height),
                        topLeft = CornerRadius(ts), topRight = CornerRadius(te),
                        bottomRight = CornerRadius(be), bottomLeft = CornerRadius(bs)
                    )
                )
            }
        }
    } else shape
}

Card(
    modifier = Modifier.fillMaxWidth().clip(animatedShape)
        .clickable(interactionSource = interactionSource, indication = null, onClick = onClick),
    …
)
```

**Pitfalls** — `Outline.Rounded` uses physical `topLeft`/`topRight`; `RoundedCornerShape` uses logical
`topStart`/`topEnd`. **This code ignores `layoutDirection`**, so corners mirror wrong in RTL — swap
start/end when `layoutDirection == LayoutDirection.Rtl`. `indication = null` removes the ripple
(deliberate here, but it costs the standard press affordance). The `is RoundedCornerShape` guard
silently no-ops for `CutCornerShape` or a polygon shape.

If the press must change the silhouette (circle → cookie, not just radius), that is §2 — hero
elements only, dramatically more expensive than a corner lerp.

---

## 5. Segmented / connected list items

### 5.1 First-class API

**Use when** on 1.5.0-alpha21+. (The expressive list-item APIs stopped being experimental in
**alpha23**, but `SegmentedListItem` / `segmentedShapes` / `segmentedColors` / `SegmentedGap` are
already present and usable on **alpha21** — Med ships them there under a file-level opt-in.)

Verified surface:
- `ListItemDefaults.segmentedShapes(index: Int, count: Int): ListItemShapes`
- `ListItemDefaults.segmentedShapes(index: Int, count: Int, shapes: ListItemShapes): ListItemShapes`
- `ListItemDefaults.segmentedColors(containerColor, disabledContainerColor, …): ListItemColors`
- `ListItemDefaults.SegmentedGap` — `Dp` spacing between segments
- `ListItemDefaults.shapes(shape, selectedShape, pressedShape, focusedShape, hoveredShape, draggedShape): ListItemShapes`

*(verbatim: `/root/work/repos/Med/app/src/main/kotlin/com/fedeveloper95/med/SettingsActivity.kt`)*

```kotlin
Column(verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap)) {
    SegmentedListItem(
        selected = false,
        onClick = {},
        colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
        shapes = ListItemDefaults.segmentedShapes(index = 0, count = 3),
        content = { … }
    )
    // … index = 1, index = 2
}
```

### 5.2 Tomato's complete `Shape.kt` — segmented shapes with per-state overrides

**Use when** the group's press/select states should morph too — this is where the "shape morph on
press" feel in a settings list comes from.

*(verbatim, complete file after the license header:
`/root/work/repos/Tomato/shared/src/commonMain/kotlin/org/nsh07/pomodoro/ui/theme/Shape.kt`)*

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

`pressedShape`/`selectedShape` = `shapes.extraLargeIncreased` (32dp, Expressive-only token) is what
makes a row visibly round out under the finger. Building from `MaterialTheme.shapes.*` rather than
literal dp means theme overrides propagate. Matching colors *(`.../ui/theme/Color.kt`)* come from
`ListItemDefaults.segmentedColors(containerColor = …, disabledContainerColor = …)`; the pair is
consumed as `shapes = segmentedListItemShapes(2, 3), colors = listItemColors` on a
`SegmentedListItem` (`.../settingsScreen/screens/AppearanceSettings.kt`).

**Pitfalls** — typo preserved from source: `bottomEnd = shapes.extraSmall.bottomStart` in
`topListItemShape` (should be `.bottomEnd`; harmless because both are 4dp — do not propagate it).
`count` is the count of items **in the group**, not the whole list; deriving it from a paginated
`list.size` gives two "last" items. Space segments with `ListItemDefaults.SegmentedGap`, not an
arbitrary dp — the gap is tuned against the inner corner radius.

### 5.3 Manual fallback for older pins

**Use when** on 1.4.0 / an early alpha without `SegmentedListItem`, or grouping `Card`s.

*(verbatim, complete file minus imports: `/root/work/repos/vivi-music/app/src/main/kotlin/com/music/vivi/utils/ShapesCurve.kt`)*

```kotlin
private const val ConnectedCornerRadius = 4
private const val EndCornerRadius = 16

fun leadingItemShape(): RoundedCornerShape = RoundedCornerShape(
    topStart = EndCornerRadius.dp, topEnd = EndCornerRadius.dp,
    bottomStart = ConnectedCornerRadius.dp, bottomEnd = ConnectedCornerRadius.dp
)

fun middleItemShape(): RoundedCornerShape = RoundedCornerShape(ConnectedCornerRadius.dp)

fun endItemShape(): RoundedCornerShape = RoundedCornerShape(
    topStart = ConnectedCornerRadius.dp, topEnd = ConnectedCornerRadius.dp,
    bottomStart = EndCornerRadius.dp, bottomEnd = EndCornerRadius.dp
)

fun getGroupedShape(index: Int, count: Int): Shape {
    return when {
        index == 0 -> leadingItemShape()
        index == count - 1 -> endItemShape()
        else -> middleItemShape()
    }
}
```

**`getGroupedShape` has a bug — fix it when you copy.** For a single-item group (`count == 1`),
`index == 0` matches first and the item gets a rounded top / square bottom instead of fully rounded.
Add `count == 1 -> RoundedCornerShape(EndCornerRadius.dp)` as the first branch.

vivi-music's onboarding uses 20dp (the Expressive `largeIncreased` step) instead of 16dp for the outer
radius *(`.../WelcomeActivity.kt`)*, and ships a one-liner for "round only the top of an existing
shape" *(`.../ui/utils/ShapeUtils.kt`)*:

```kotlin
fun CornerBasedShape.top(): CornerBasedShape =
    copy(bottomStart = CornerSize(0.dp), bottomEnd = CornerSize(0.dp))
```

**Pitfalls** — space manual groups by ~2dp; a 12dp gap makes the group read as separate cards and the
4dp inner corners look like a mistake. Prefer deriving from `MaterialTheme.shapes` (Tomato's approach)
over hardcoded 4/16 constants.

---

## 6. Connected button groups

**Use when** you need a segmented control / exclusive selection row. Do not compute the corners.

```kotlin
// Verified ButtonGroupDefaults surface, abridged
val ConnectedSpaceBetween: Dp                        // gap the inner radii are designed against
val HorizontalArrangement: Arrangement.Horizontal    // wider — for NON-connected groups
val connectedButtonCheckedShape = ShapeTokens.CornerFull

@Composable fun connectedLeadingButtonShapes(
    shape: Shape = connectedLeadingButtonShape,            // pill start, small end
    pressedShape: Shape = connectedLeadingButtonPressShape,
    checkedShape: Shape = connectedButtonCheckedShape,
): ToggleButtonShapes
@Composable fun connectedMiddleButtonShapes(
    shape: Shape = ShapeDefaults.Small,
    pressedShape: Shape = connectedMiddleButtonPressShape,
    checkedShape: Shape = connectedButtonCheckedShape,
): ToggleButtonShapes
@Composable fun connectedTrailingButtonShapes(
    shape: Shape = connectedTrailingButtonShape,           // small start, pill end
    pressedShape: Shape = connectedTrailingButtonPressShape,
    checkedShape: Shape = connectedButtonCheckedShape,
): ToggleButtonShapes
```

Outer edges are pills, inner edges are small radii, **checked becomes a full pill** — so selecting an
item visibly detaches it from the group. That is the whole interaction.

*(verbatim, core: `/root/work/repos/Tomato/shared/src/commonMain/kotlin/org/nsh07/pomodoro/ui/settingsScreen/components/ThemePickerListItem.kt`)*

```kotlin
Row(
    horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween),
    modifier = Modifier.padding(top = 8.dp)
) {
    options.fastForEachIndexed { index, theme ->
        val isSelected = selectedIndex == index
        ToggleButton(
            checked = isSelected,
            onCheckedChange = { onThemeChange(theme.first) },
            modifier = Modifier
                .weight(1f)
                .semantics { role = Role.RadioButton },
            shapes =
                when (index) {
                    0 -> ButtonGroupDefaults.connectedLeadingButtonShapes()
                    options.lastIndex -> ButtonGroupDefaults.connectedTrailingButtonShapes()
                    else -> ButtonGroupDefaults.connectedMiddleButtonShapes()
                },
        ) {
            Text(stringResource(theme.second.second), maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}
```

Tomato nests this whole `Row` in a `SegmentedListItem`'s `supportingContent`, with the list item
itself shaped by `segmentedListItemShapes(index, items)` from §5.2 — a connected group inside a
segmented group.

**Pitfalls** — **`semantics { role = Role.RadioButton }` is not optional**; without it a row of
`ToggleButton`s is announced as N independent checkboxes (`Role.Checkbox` for multi-select). Use
`ConnectedSpaceBetween`, not `HorizontalArrangement` (that is the wider non-connected spacing). A
2-item group has no middle — the `when` above is safe because `options.lastIndex` is tested before
`else`; testing `count - 1` first breaks for `count == 1`. `weight(1f)` gives equal widths; for a
scrollable chip group drop it and use `horizontalScroll`. `ButtonGroup`'s own signature **changed
incompatibly** between 1.4.0 and 1.5.0-alpha22 — these shape helpers are stable across that change,
the composable is not.

---

## 7. Loading art from shapes

### 7.1 Prefer the component

`LoadingIndicator` / `ContainedLoadingIndicator` *are* "a looping shape morph sequence composed of
seven unique Material 3 shapes." Use them for the M3E loading look; they still need
`@OptIn(ExperimentalMaterial3ExpressiveApi::class)` (promotion reverted in alpha19). Material's rules:
not for waits > ~5s, and not once the process becomes determinate — switch to a progress indicator.

### 7.2 Hand-rolled: counter-rotating tinted shape cluster

**Use when** you want branded splash/onboarding art, not a spinner. vivi-music builds this from a
10-sided cookie **vector drawable** — because `Cookie10Sided` does not exist in `MaterialShapes`.

*(verbatim, condensed: `/root/work/repos/vivi-music/app/src/main/kotlin/com/music/vivi/WelcomeActivity.kt`)*

```kotlin
fun BlobClusterLoading(modifier: Modifier = Modifier) {
    val infiniteTransition = rememberInfiniteTransition(label = "blobClusterRotation")
    // three non-harmonic periods, one reversed, so the cluster never repeats a pose
    val rotation1 by infiniteTransition.animateFloat(0f, 360f,
        infiniteRepeatable(tween(25000, easing = LinearEasing), RepeatMode.Restart), label = "rotation1")
    val rotation2 by infiniteTransition.animateFloat(360f, 0f,
        infiniteRepeatable(tween(30000, easing = LinearEasing), RepeatMode.Restart), label = "rotation2")
    val rotation3 by infiniteTransition.animateFloat(180f, 540f,
        infiniteRepeatable(tween(20000, easing = LinearEasing), RepeatMode.Restart), label = "rotation3")

    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Icon(painterResource(R.drawable.ic_ten_sided_cookie), null,
             tint = MaterialTheme.colorScheme.primaryContainer,
             modifier = Modifier.size(140.dp).rotate(rotation1))
        Icon(painterResource(R.drawable.ic_ten_sided_cookie), null,
             tint = MaterialTheme.colorScheme.secondaryContainer,
             modifier = Modifier.size(100.dp).rotate(rotation2).align(Alignment.TopStart).padding(top = 16.dp, start = 16.dp))
        Icon(painterResource(R.drawable.ic_ten_sided_cookie), null,
             tint = MaterialTheme.colorScheme.tertiaryContainer,
             modifier = Modifier.size(80.dp).rotate(rotation3).align(Alignment.BottomEnd).padding(bottom = 8.dp, end = 8.dp))
    }
}
```

The container-role tints keep it on-theme under dynamic color. Same effect with no drawable
**[canonical-form]** — swap each `Icon` for
`Box(Modifier.size(140.dp).rotate(rotation1).background(color, MaterialShapes.Cookie9Sided.toShape()))`.

### 7.3 Punch-out splash

*(verbatim, condensed, same file)* — the monochrome launcher icon is tinted with the **background**
color and drawn over a spinning primary-colored cookie, so the logo appears knocked out of the shape:

```kotlin
Box(modifier = modifier, contentAlignment = Alignment.Center) {
    Icon(painterResource(R.drawable.ic_ten_sided_cookie), null, tint = MaterialTheme.colorScheme.primary,
         modifier = Modifier.fillMaxSize().rotate(rotation))       // rotation: 20s linear infinite
    Icon(painterResource(R.mipmap.ic_launcher_monochrome), null, tint = MaterialTheme.colorScheme.background,
         modifier = Modifier.size(220.dp))
}
```

**Pitfalls** — the punch-out only reads over a surface that really is `colorScheme.background`; over
`surfaceContainer` it looks like a mistinted logo. Auto-moving content lasting > 5s must be
pausable/stoppable/hideable for accessibility — gate on the reduce-animations setting. Infinite
animations keep the frame pump running, so do not leave this composed off-screen.

---

## 8. Polygon grids without the stamped look

**Use when** the same shape repeats — category tiles, avatar rows, thumbnail walls. Identical polygons
look printed. Vary `startAngle` (`toShape(startAngle: Int = 0)`, degrees).

```kotlin
// [canonical-form] — composed from verified pieces
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun CategoryGrid(categories: List<Category>) {
    // toShape() is @Composable — build the variants up front, not inside items { }
    val shapes = List(6) { i -> MaterialShapes.Cookie7Sided.toShape(startAngle = i * 17) }

    LazyVerticalGrid(columns = GridCells.Fixed(3)) {
        itemsIndexed(categories) { index, category ->
            Box(
                Modifier.aspectRatio(1f).padding(6.dp)
                    .background(category.color, shapes[index % shapes.size]),
                contentAlignment = Alignment.Center,
            ) { Icon(category.icon, null, Modifier.size(28.dp)) }
        }
    }
}
```

An N-lobed shape repeats every `360 / N` degrees, so a step at or above that is a no-op.
`Cookie7Sided` has a ~51° period, so 17° gives three distinct orientations before wrapping.

**Pitfalls** — `toShape()` is `@Composable`, so build the `List<Shape>` outside the lazy scope. Do not
randomize per item with `Random`; it re-randomizes on recomposition and the grid shimmers — derive
from a stable index or id hash. Rotating an asymmetric shape (`Arrow`, `Fan`, `SemiCircle`) changes
its meaning; restrict this to radially symmetric shapes.

---

## 9. Drawing polygons instead of clipping

**Use when** the shape is decoration, not a mask. `clip` costs a save-layer plus a clip path on every
draw and clips children; `background(color, shape)` just fills; `drawWithCache` is cheapest and is the
only option if you need a stroke, a gradient, or multiple paths. Use `clip` only to mask real content
(a photo, an image avatar).

`RoundedPolygon.toPath(startAngle)` is `@Composable` and returns a `Path` in normalized space, so you
scale it in the draw scope. **[canonical-form]**

```kotlin
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun PolygonBackdrop(
    polygon: RoundedPolygon = MaterialShapes.Cookie9Sided,
    color: Color = MaterialTheme.colorScheme.secondaryContainer,
    modifier: Modifier = Modifier,
) {
    val basePath = polygon.toPath()          // @Composable — must be called here
    Box(
        modifier.drawWithCache {
            // recomputed only when size changes, not every frame
            val scaled = Path().apply {
                addPath(basePath)
                transform(Matrix().apply { scale(size.width, size.height) })
            }
            onDrawBehind { drawPath(scaled, color) }
        }
    )
}
```

For a **morph** you skip the `@Composable` hop entirely, because `Morph.toPath` is a plain function:

```kotlin
@Composable
fun MorphingBlob(progress: Float, color: Color, modifier: Modifier = Modifier) {
    val morph = remember { Morph(MaterialShapes.Cookie6Sided, MaterialShapes.Burst) }
    Canvas(modifier) {
        val path = morph.toPath(progress = progress)   // not @Composable
        path.transform(Matrix().apply { scale(size.width, size.height) })
        drawPath(path, color)
    }
}
```

**Pitfalls** — allocating `Path`/`Matrix` inside `onDrawBehind` allocates every frame; construction
goes in the `drawWithCache` block, only `drawPath` in `onDraw*`. `Path.transform` **mutates** — copy
the `@Composable`-produced `basePath` (`Path().apply { addPath(basePath) }`) or the scale compounds on
every size change. Use `androidx.compose.ui.graphics.Matrix`. `drawWithCache` invalidates on size
change only, so animated state must be read inside `onDrawBehind`.

---

## 10. Troubleshooting

### 10.1 Shape self-intersects or kinks mid-morph

Perfect at `0f` and `1f`, folds through itself around 0.4–0.6. **Cause:** vertex/feature count
mismatch — when one polygon has 6 lobes and the other 12, several features collapse onto one and
control points cross.

1. Morph between shapes with the **same or a multiple** vertex count. `Cookie6Sided` ↔ `Circle` (the
   LastChat pair) is well behaved; `Cookie12Sided` ↔ `Triangle` is not.
2. Upgrade to graphics-shapes **1.1.0** — it switched outline progress from angle measurement to
   **curve-length measurement** and improved feature mapping specifically for complex morphs. On
   1.0.x, upgrade before debugging anything else.
3. Chain through an intermediate: `A → Circle → B`. `Circle` maps cleanly to almost everything.
4. Align the mapping start with `startAngle` so the two shapes' first features correspond.
5. For custom polygons, match `numVertices` and differentiate with `perVertexRounding` instead.

### 10.2 Shape does not fill its box / is off-center

**Cause:** normalization. `MaterialShapes` values are normalized (unit box); polygons from
`RoundedPolygon(...)`, `.star(...)`, `.transformed(...)` are **not** — they sit at
`(centerX, centerY)` with extent `radius`.

1. `.normalized()` on any polygon you construct, before `toShape()`.
2. If it *is* normalized and still wrong, the box is non-square and `toShape()` stretches to fill —
   constrain with `Modifier.aspectRatio(1f)`, or use §2.1's `minOf(scaleX, scaleY)` + centering.
3. An off-center **morph**: the intermediate is not guaranteed normalized even when both endpoints
   are. Compute bounds per frame (§2.1) instead of assuming a unit box (§2.2).
4. In a custom `Shape`, confirm you scaled by `size.width`/`size.height` and not by density.

### 10.3 Shape looks like a blob at small size

Lobe amplitude scales with the shape; below a pixel or two, antialiasing smears it. Check "Reads down
to" in `shapes-catalog.md` §4.1 — **more lobes needs more size** (`Cookie4Sided` works at 32dp,
`Cookie12Sided` needs 56dp+). Swap shape by size rather than shrinking one:
`if (size < 40.dp) MaterialShapes.Cookie4Sided else MaterialShapes.Cookie12Sided`. Below ~32dp fall
back to `Circle` or the rounded-rect scale — a crisp circle beats a mushy cookie. `PixelCircle` /
`PixelTriangle` alias badly when rotated at any size. Always render at the smallest shipping size on
a real device before committing.

### 10.4 Morph stutters or drops frames

**Cause, almost always:** the `Morph` is reconstructed every frame. `Morph(start, end)` computes the
feature mapping at construction; running that 60×/s is the bug.

1. `val morph = remember(start, end) { Morph(start, end) }`. Key on the **polygons**. If `progress` is
   in the `Morph`'s remember key, that is the bug.
2. Never call `Morph(...)` inside `Shape.createOutline` or a `DrawScope`.
3. Remember the derived `Shape` too, keyed on `(morph, progress, …)`.
4. Prefer drawing (§9) over clipping (§2) when you do not need to mask children.
5. For continuous rotation use `withFrameNanos` + `snapTo`, not `infiniteRepeatable` (which restarts
   at the wrap and can hitch).
6. Confirm you are not animating behind a navigated-away composable — an `infiniteTransition` keeps
   the frame pump alive.

### 10.5 Press morph never fires, or `toShape()` in a lazy list is slow

`collectIsPressedAsState()` stays false when you create a `MutableInteractionSource` but do not pass
it to the component — set `interactionSource = interactionSource` on the `Button` / `Surface` /
`clickable`. For lazy-list jank, hoist `.toShape()` above the list (§1.3); one `Shape` instance is
safely shared by all rows.

### 10.6 Unresolved `MaterialShapes` / `toShape`

1. `@OptIn(ExperimentalMaterial3ExpressiveApi::class)` present? **Still required** on 1.5.0-alpha26 —
   promotion reverted in alpha19. On **1.4.0** everything Expressive needs `@OptIn`, not just shapes.
2. `androidx.graphics:graphics-shapes` on the classpath? `RoundedPolygon` / `Morph` resolve from
   `androidx.graphics.shapes`, not material3.
3. Import `androidx.compose.material3.toShape` explicitly — it is an extension function and IDE
   auto-import misses it.
4. Did you write `Cookie5Sided` / `Cookie8Sided` / `Cookie10Sided` / `Cookie11Sided`? Only 4, 6, 7,
   9, 12 exist.

### 10.7 The shape is right but the design is wrong

If a reviewer says "this looks busy," the fix is usually not a different shape — it is **fewer**
shapes. Shape contrast is relational: an expressive silhouette reads as emphasis only by breaking
from its neighbours. One or two per screen; everything else holds the rounded-rect baseline.

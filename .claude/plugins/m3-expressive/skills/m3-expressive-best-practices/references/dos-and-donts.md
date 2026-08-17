# M3 Expressive — Do / Don't, in code

54 paired examples: the wrong version a model actually writes, the right version, and the one thing
that makes the difference. Valid at **material3 `1.5.0-alpha26`** / **material3-adaptive `1.3.0`**.

**Provenance `[API-alpha26]`.** Every `material3` / `material3-adaptive` composable name and
`*Defaults` member name in this file was machine-checked against
`compose/material3/material3/api/current.txt` and `compose/material3/adaptive/*/api/current.txt`
at the alpha26 / 1.3.0 SHA. Non-material3 APIs (foundation, ui, animation, navigation3,
third-party) were **not** checked; anything that could not be confirmed is flagged `[UNVERIFIED]`
inline at its use site.

`@OptIn(ExperimentalMaterial3ExpressiveApi::class)` appears **only where still required at alpha26** —
`LoadingIndicator`/`ContainedLoadingIndicator`, `MaterialShapes` + `toShape()`/`toPath()`, Expressive
menu APIs, `PullToRefreshDefaults` indicator colors, and `ToggleButton` size variants. Everything else
graduated; do not sprinkle it.

**`ButtonGroup` is the one contested case.** The alpha22 release note claims the `ButtonGroup` APIs
were promoted to stable; some census data disagreed; and the shipped alpha26 `current.txt` carries
**no** experimental annotation on `ButtonGroup` or `ButtonGroupDefaults.OverflowIndicator`. The
`@OptIn` kept on the `ButtonGroup` example below is therefore **defensive, not required** — redundant
at this pin (a warning at worst), and load-bearing only if your pin predates the promotion. Same
framing as `${CLAUDE_PLUGIN_ROOT}/skills/m3-expressive/references/setup-and-versions.md` §5.

[Theming](#theming) · [Motion](#motion) · [Shape](#shape) · [Color](#color) ·
[Typography](#typography) · [Components](#components) · [Layout & adaptive](#layout--adaptive) ·
[Accessibility](#accessibility) · [Performance](#performance)

---

## Theming

### The root theme

❌
```kotlin
MaterialTheme(
    colorScheme = if (darkTheme) darkColorScheme() else lightColorScheme(),
    typography = AppTypography,
    content = content,
)
```
✅
```kotlin
MaterialExpressiveTheme(
    colorScheme = if (darkTheme) darkColorScheme() else expressiveLightColorScheme(),
    typography = AppTypography,
    shapes = AppShapes,
    motionScheme = MotionScheme.expressive(),
    content = content,
)
```
The 3-param `MaterialTheme` overload every pre-Expressive template emits resolves `motionScheme` to
`MotionScheme.standard()`. Nothing warns; ~21 components silently run standard springs.

### `expressiveDarkColorScheme()`

❌
```kotlin
val scheme = if (darkTheme) expressiveDarkColorScheme() else expressiveLightColorScheme()
```
✅
```kotlin
val scheme = if (darkTheme) darkColorScheme() else expressiveLightColorScheme()
```
**It does not exist.** `expressiveLightColorScheme()` differs from `lightColorScheme()` in exactly four
`on*Container` roles; the dark counterpart is plain `darkColorScheme()`.

### Leaving `motionScheme` implicit

❌
```kotlin
MaterialExpressiveTheme(colorScheme = scheme, typography = AppTypography) { content() }
```
✅
```kotlin
MaterialExpressiveTheme(
    colorScheme = scheme,
    typography = AppTypography,
    motionScheme = MotionScheme.expressive(),
) { content() }
```
`null` defaults to expressive so this is usually harmless — but it breaks the moment anything nests a
plain `MaterialTheme`, and it makes the app's motion intent ungreppable.

### Scoped theming that resets the subtree

❌
```kotlin
MaterialExpressiveTheme(motionScheme = MotionScheme.expressive()) {
    FloatingActionButtonMenu(expanded = expanded, button = { … }) { … }
}
```
✅
```kotlin
MaterialExpressiveTheme(
    colorScheme = MaterialTheme.colorScheme,
    typography = MaterialTheme.typography,
    shapes = MaterialTheme.shapes,
    motionScheme = MotionScheme.expressive(),
) { FloatingActionButtonMenu(expanded = expanded, button = { … }) { … } }
```
`MaterialExpressiveTheme`'s nullable params mean **"use the Expressive default"**, not "inherit from
ambient" — the opposite of `MaterialTheme`. The ❌ version silently replaces colors, type and shapes.

### The old composition locals

❌
```kotlin
val scheme = LocalColorScheme.current
val type = LocalTypography.current
```
✅
```kotlin
val scheme = MaterialTheme.colorScheme
val type = MaterialTheme.typography
// or, if you need the whole theme: MaterialTheme.LocalMaterialTheme.current
```
`MaterialTheme` collapsed to a **single** composition local in alpha15. There is no `LocalMotionScheme`
either.

### Hardcoding what the theme owns

❌
```kotlin
Surface(shape = RoundedCornerShape(24.dp), color = Color(0xFFF3EDF7)) {
    Text("Now playing", fontSize = 18.sp, fontWeight = FontWeight.Bold)
}
```
✅
```kotlin
Surface(
    shape = MaterialTheme.shapes.extraLarge,
    color = MaterialTheme.colorScheme.surfaceContainerHigh,
) { Text("Now playing", style = MaterialTheme.typography.titleMediumEmphasized) }
```
Each hardcoded value is a place the theme cannot reach: dark mode, dynamic color, the shape scale and
a typography swap all stop there. `24.dp` is not even on the scale (4/8/12/16/**20**/28/**32**/**48**).

---

## Motion

### `tween` where a spec belongs

❌
```kotlin
val offset by animateDpAsState(
    if (expanded) 0.dp else 120.dp,
    animationSpec = tween(300, easing = FastOutSlowInEasing),
    label = "sheet",
)
```
✅
```kotlin
val offset by animateDpAsState(
    if (expanded) 0.dp else 120.dp,
    animationSpec = MaterialTheme.motionScheme.defaultSpatialSpec(),
    label = "sheet",
)
```
A curve cannot be retargeted mid-flight — it restarts or cross-fades. That is why a gesture reversed
mid-animation looks broken with `tween` and correct with a spring.

### Spatial spring on a color

❌
```kotlin
val bg by animateColorAsState(
    target, animationSpec = MaterialTheme.motionScheme.defaultSpatialSpec(), label = "bg")
```
✅
```kotlin
val bg by animateColorAsState(
    target, animationSpec = MaterialTheme.motionScheme.defaultEffectsSpec(), label = "bg")
```
Spatial springs are underdamped (0.8 default, 0.6 fast) and **overshoot** — on a color, out of gamut and
through a hue in neither endpoint. Effects springs are critically damped (1.0) so they cannot.

### Effects spring on a size

❌
```kotlin
val size by animateDpAsState(
    target, animationSpec = MaterialTheme.motionScheme.defaultEffectsSpec(), label = "card")
```
✅
```kotlin
val size by animateDpAsState(
    target, animationSpec = MaterialTheme.motionScheme.defaultSpatialSpec(), label = "card")
```
Effects specs are **identical** between `expressive()` and `standard()`. Using one on a spatial property
means switching schemes changes nothing — symptom: "expressive motion looks the same."

### One spec for a combined transition

❌
```kotlin
enter = fadeIn(motionScheme.defaultSpatialSpec()) + scaleIn(motionScheme.defaultSpatialSpec()),
exit  = fadeOut(motionScheme.defaultSpatialSpec()) + scaleOut(motionScheme.defaultSpatialSpec()),
```
✅
```kotlin
enter = fadeIn(motionScheme.defaultEffectsSpec()) +
        scaleIn(motionScheme.defaultSpatialSpec(), initialScale = 0.92f),
exit  = fadeOut(motionScheme.defaultEffectsSpec()) +
        scaleOut(motionScheme.defaultSpatialSpec(), targetScale = 0.92f),
```
`AnimatedVisibility` / `AnimatedContent` almost always combine both families. Fade goes effects, the
scale/slide goes spatial.

### Wrong tier for the element's scale

❌
```kotlin
// a 24dp toggle thumb
val x by animateDpAsState(t, MaterialTheme.motionScheme.slowSpatialSpec(), label = "thumb")
```
✅
```kotlin
val x by animateDpAsState(t, MaterialTheme.motionScheme.fastSpatialSpec(), label = "thumb")
```
Fast for small components (switches, buttons, chips), Default for medium (sheets, rails, cards), Slow
for full-screen. A slow bouncy spring on a switch reads as broken hardware.

### Over-animation

❌
```kotlin
// on every list row
val scale by animateFloatAsState(if (pressed) 0.96f else 1f, label = "s")
val elev by animateDpAsState(if (pressed) 8.dp else 2.dp, label = "e")
val bg by animateColorAsState(if (pressed) primaryContainer else surface, label = "b")
val corner by animateDpAsState(if (pressed) 4.dp else 16.dp, label = "c")
```
✅
```kotlin
ListItem(onClick = onOpen, shapes = ListItemDefaults.shapes()) { Text(item.title) }
```
Four concurrent animations on a repeated element is noise, not expression — and animating every row
means no row reads as emphasised. Let the component do it: one parameter, themeable, correct springs.

### No reduced-motion handling

❌
```kotlin
val p by animateFloatAsState(t, MaterialTheme.motionScheme.defaultSpatialSpec(), label = "p")
```
✅
```kotlin
@Stable data class MotionPolicy(val reduceMotion: Boolean)
val LocalMotionPolicy = compositionLocalOf { MotionPolicy(reduceMotion = false) }

@Composable
fun spatialSpec(): FiniteAnimationSpec<Float> =
    if (LocalMotionPolicy.current.reduceMotion) snap()
    else MaterialTheme.motionScheme.defaultSpatialSpec()

val p by animateFloatAsState(t, animationSpec = spatialSpec(), label = "p")
```
Material's **first** motion principle is accessibility. Provide the policy at the root from
`ValueAnimator.areAnimatorsEnabled()` plus the three `Settings.Global` animation scales, observed with a
`ContentObserver`. Swapping `expressive()` → `standard()` removes overshoot only and is not enough.

### Infinite decorative motion

❌
```kotlin
val p by rememberInfiniteTransition(label = "shimmer")
    .animateFloat(0f, 1f, infiniteRepeatable(tween(2000), RepeatMode.Reverse), label = "morph")
```
✅
```kotlin
val p by animateFloatAsState(
    targetValue = if (isSyncing) 1f else 0f,
    animationSpec = MaterialTheme.motionScheme.fastSpatialSpec(),
    label = "morph",
)
```
Content that moves automatically must be pausable/stoppable/hidden past five seconds. A permanent
animation also burns the signal a real state change needs, and costs frame budget the whole time.

---

## Shape

### Uniform radii everywhere

❌
```kotlin
Card(shape = RoundedCornerShape(24.dp)) { … }
Surface(shape = RoundedCornerShape(24.dp)) { … }
Button(onClick = …, shape = RoundedCornerShape(24.dp)) { … }
```
✅
```kotlin
Card(shape = MaterialTheme.shapes.large) { … }                        // 16dp — the calm baseline
Surface(shape = MaterialTheme.shapes.large) { … }
Card(shape = MaterialTheme.shapes.extraExtraLarge) { HeroContent() }  // 48dp — the one break
```
"Break from the surrounding shape style to draw attention to a particular element." One radius across a
screen means shape carries no information at all.

### `shape =` where `shapes =` belongs

❌
```kotlin
Button(onClick = onPlay, shape = CircleShape) { Text("Play") }
```
✅
```kotlin
Button(onClick = onPlay, shapes = ButtonDefaults.shapes()) { Text("Play") }
// specific morph:
Button(onClick = onPlay,
    shapes = ButtonDefaults.shapes(CircleShape, MaterialTheme.shapes.large)) { Text("Play") }
```
`shape` (singular) and `shapes` (plural) are different overloads. `shape` gives a static container with
no press morph — the defining Expressive button interaction simply does not happen.

### `ToggleButtonDefaults.shapes(...)` on alpha25+

❌
```kotlin
shapes = ToggleButtonDefaults.shapes(checkedShape = MaterialTheme.shapes.large)
```
✅
```kotlin
shapes = ToggleButtonShapes(checkedShape = MaterialTheme.shapes.large)
// defaulting case instead:
shapes = ToggleButtonDefaults.shapesFor(ButtonDefaults.MinHeight)
```
Both old overloads are `DeprecationLevel.HIDDEN` — invisible to the compiler, so you get **unresolved
reference**, not a warning. `shapesFor` takes a **height `Dp`**, not shapes; customising moved to the
`ToggleButtonShapes(...)` constructor. `ButtonGroupDefaults.connected*ButtonShapes()` are unaffected.

### A polygon clipping real content

❌
```kotlin
// @file:OptIn(ExperimentalMaterial3ExpressiveApi::class) — MaterialShapes + toShape() still gated
Box(Modifier.size(160.dp).clip(MaterialShapes.Clover8Leaf.toShape())) {
    Column(Modifier.padding(16.dp)) {
        Text("Weekly summary", style = MaterialTheme.typography.titleMedium)
        Text("4h 12m", style = MaterialTheme.typography.bodyMedium)
    }
}
```
✅
```kotlin
// @file:OptIn(ExperimentalMaterial3ExpressiveApi::class) — MaterialShapes + toShape() still gated
Row(verticalAlignment = Alignment.CenterVertically) {
    Box(
        Modifier.size(56.dp)
            .clip(MaterialShapes.Clover4Leaf.toShape())
            .background(MaterialTheme.colorScheme.secondaryContainer),
        contentAlignment = Alignment.Center,
    ) { Icon(Icons.Filled.Headphones, contentDescription = null) }
    Spacer(Modifier.width(16.dp))
    Column { Text("Weekly summary"); Text("4h 12m") }
}
```
Deep concave notches eat text and photos. Clip the **backdrop**, not the content. Both `.toShape()`
sites still need `@OptIn(ExperimentalMaterial3ExpressiveApi::class)` at alpha26.

### A polygon at a size where it turns to mush

❌
```kotlin
// @file:OptIn(ExperimentalMaterial3ExpressiveApi::class) — MaterialShapes + toShape() still gated
Icon(painter, null, Modifier.size(24.dp).clip(MaterialShapes.VerySunny.toShape()))
```
✅
```kotlin
Icon(painter, null, Modifier.size(24.dp).clip(CircleShape))
```
Below ~40dp, `Burst`, `VerySunny`, `Clover8Leaf` and the high-count cookies read as a blob. And
"smaller shapes can result in essential actions looking less important."

### Decorative morphing

❌
```kotlin
// a settings-row icon backdrop that morphs continuously "for delight"
val p by rememberInfiniteTransition(label = "m")
    .animateFloat(0f, 1f, infiniteRepeatable(tween(3000), RepeatMode.Reverse), label = "p")
```
✅
```kotlin
// @file:OptIn(ExperimentalMaterial3ExpressiveApi::class) — MaterialShapes is still gated
val morph = remember { Morph(MaterialShapes.Circle, MaterialShapes.Cookie7Sided) }
val p by animateFloatAsState(
    targetValue = if (downloading) 1f else 0f,
    animationSpec = MaterialTheme.motionScheme.fastSpatialSpec(),
    label = "downloadMorph",
)
```
Shape change is a **state signal** — press, selection, expand, playback, loading. If a morph does not
correspond to a state change, delete it.

---

## Color

### Wrong `on-*` pairing

❌
```kotlin
Surface(
    color = MaterialTheme.colorScheme.primaryContainer,
    contentColor = MaterialTheme.colorScheme.onPrimary,
) { Text("Saved") }
```
✅
```kotlin
Surface(color = MaterialTheme.colorScheme.primaryContainer) { Text("Saved") }
// contentColorFor picks onPrimaryContainer; name it explicitly only if you must
```
The pairing rule is structural. Material's ≥3:1 guarantee applies to **matched** pairs only —
`onPrimary` on `primaryContainer` fails contrast in at least one of light/dark.

### Hardcoded colors

❌
```kotlin
Card(colors = CardDefaults.cardColors(containerColor = Color(0xFFFFFFFF))) {
    Text("Title", color = Color.Black)
    Text("Subtitle", color = Color.Gray)
}
```
✅
```kotlin
Card(colors = CardDefaults.cardColors(
    containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
    Text("Title", color = MaterialTheme.colorScheme.onSurface)
    Text("Subtitle", color = MaterialTheme.colorScheme.onSurfaceVariant)
}
```
One edit breaks dark mode, dynamic color, AMOLED and contrast at once. `Color.Black` text on a card that
goes dark is the canonical disappearing-text bug.

### AMOLED by overriding two roles

❌
```kotlin
val amoled = darkColorScheme().copy(surface = Color.Black, background = Color.Black)
```
✅
```kotlin
val amoled = darkColorScheme().copy(
    surface = Color.Black, background = Color.Black,
    surfaceContainerLowest = Color.Black,
    surfaceContainerLow = Color(0xFF0A0A0A),
    surfaceContainer = Color(0xFF101010),
    surfaceContainerHigh = Color(0xFF161616),
    surfaceContainerHighest = Color(0xFF1C1C1C),
)
```
Every card, sheet, nav bar and app bar reads `surfaceContainer*`. Override two roles and you ship grey
cards on a black background. Gate it on `darkTheme && preference`, never the preference alone.

### `surfaceVariant` / alpha overlays as the container model

❌
```kotlin
Surface(color = MaterialTheme.colorScheme.surface.copy(alpha = 0.08f)) { … }
Surface(color = MaterialTheme.colorScheme.surfaceVariant) { … }
```
✅
```kotlin
Surface(color = MaterialTheme.colorScheme.surfaceContainerLow) { … }    // non-interactive card
Surface(color = MaterialTheme.colorScheme.surfaceContainer) { … }       // default container
Surface(color = MaterialTheme.colorScheme.surfaceContainerHigh) { … }   // sits ON surfaceContainer
```
The alpha-overlay model is what M3 replaced, and it reads flatter than moving up the tonal ramp. Pick a
rung, not a translucency.

---

## Typography

### Ad-hoc bold instead of the emphasized scale

❌
```kotlin
Text("Your library",
    style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold)
```
✅
```kotlin
Text("Your library", style = MaterialTheme.typography.headlineLargeEmphasized)
```
The Expressive scale is 30 styles — 15 baseline plus 15 `*Emphasized` (same size/line-height/tracking,
weight steps to Medium, or Bold for `labelLargeEmphasized`). Hand-set weight also risks **synthetic
bolding** if the family lacks that instance.

### Hardcoded `sp`

❌
```kotlin
Text("12:34", fontSize = 64.sp, fontWeight = FontWeight.Bold)
```
✅
```kotlin
// Type.kt
val TimerDisplay = TextStyle(
    fontFamily = GoogleSansFlex, fontSize = 64.sp, lineHeight = 68.sp,
    fontWeight = FontWeight.Medium,
)
// call site
Text("12:34", style = TimerDisplay, modifier = Modifier.wrapContentHeight())
```
A custom size is legitimate — it belongs in `Type.kt` as a named token, not inline. Inline `fontSize`
also clips at 200% font scale inside a fixed-`dp` container.

### A variable font declared with one instance

❌
```kotlin
val GoogleSansFlex = FontFamily(Font(R.font.google_sans_flex))
```
✅
```kotlin
val GoogleSansFlex = FontFamily(
    Font(R.font.google_sans_flex, weight = FontWeight.Normal,
        variationSettings = FontVariation.Settings(FontVariation.weight(400))),
    Font(R.font.google_sans_flex, weight = FontWeight.Medium,
        variationSettings = FontVariation.Settings(FontVariation.weight(500))),
    Font(R.font.google_sans_flex, weight = FontWeight.Bold,
        variationSettings = FontVariation.Settings(FontVariation.weight(700))),
)
```
One entry means every emphasized style gets synthetically bolded — the exact opposite of the point,
since the emphasized scale *is* a real weight-axis shift.

### No hero type on a screen that needs one

❌
```kotlin
Scaffold(topBar = { TopAppBar(title = { Text("Library") }) }) { padding -> … }
```
✅
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
) { padding -> … }
```
On an editorial or navigational screen this is the cheapest legitimate hero moment: it collapses on
scroll, so the editorial weight costs no sustained screen real estate.

---

## Components

### A loose row of buttons

❌
```kotlin
Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
    OutlinedButton(onClick = { view = LIST }) { Text("List") }
    OutlinedButton(onClick = { view = GRID }) { Text("Grid") }
    OutlinedButton(onClick = { view = COMPACT }) { Text("Compact") }
}
```
✅
```kotlin
Row(horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween)) {
    ViewMode.entries.forEachIndexed { index, mode ->
        ToggleButton(
            checked = view == mode,
            onCheckedChange = { view = mode },
            modifier = Modifier.weight(1f).semantics { role = Role.RadioButton },
            shapes = when (index) {
                0 -> ButtonGroupDefaults.connectedLeadingButtonShapes()
                ViewMode.entries.lastIndex -> ButtonGroupDefaults.connectedTrailingButtonShapes()
                else -> ButtonGroupDefaults.connectedMiddleButtonShapes()
            },
        ) { Text(mode.label) }
    }
}
```
The ❌ version asserts three unrelated actions and shows no selected state. `ConnectedSpaceBetween` is
2dp; the group reads as **one object** with one member selected. (`ButtonGroupDefaults.HorizontalArrangement`
is ~12dp — the *standard*, non-connected spacing.)

### Connecting unrelated actions

❌
```kotlin
Row(horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween)) {
    ToggleButton(false, { save() }, shapes = ButtonGroupDefaults.connectedLeadingButtonShapes()) { Text("Save") }
    ToggleButton(false, { delete() }, shapes = ButtonGroupDefaults.connectedMiddleButtonShapes()) { Text("Delete") }
    ToggleButton(false, { share() }, shapes = ButtonGroupDefaults.connectedTrailingButtonShapes()) { Text("Share") }
}
```
✅
```kotlin
Row(horizontalArrangement = ButtonGroupDefaults.HorizontalArrangement) {
    FilledTonalButton(onClick = ::save) { Text("Save") }
    FilledTonalButton(onClick = ::share) { Text("Share") }
    TextButton(onClick = ::delete, colors = ButtonDefaults.textButtonColors(
        contentColor = MaterialTheme.colorScheme.error)) { Text("Delete") }
}
```
Connected implies **"one set"** — the members are alternatives. Connecting Save/Delete/Share asserts a
relationship that does not exist and invites mis-taps between destructive and safe actions.

### `TonalToggleButton`, and the `SplitButtonDefaults` shape helpers

❌
```kotlin
TonalToggleButton(checked = expanded, onCheckedChange = { expanded = it }) { Text("More") }
SplitButtonDefaults.LeadingButton(
    onClick = ::export,
    shapes = SplitButtonDefaults.leadingButtonShapes(CornerSize(50)),   // @Deprecated
) { Text("Export") }
```
✅
```kotlin
FilledTonalToggleButton(checked = expanded, onCheckedChange = { expanded = it }) { Text("More") }
SplitButtonDefaults.LeadingButton(
    onClick = ::export,
    shapes = SplitButtonDefaults.leadingButtonShapesFor(SplitButtonDefaults.SmallContainerHeight),
) { Text("Export") }
```
`TonalToggleButton` → `FilledTonalToggleButton` is a real alpha25 rename with an identical parameter
list, at warning level; the old name carried a declaration-level Expressive annotation and the new
one does not need it.

**`SplitButtonLayout` is NOT part of this.** It is the current, undeprecated composable name and
there is no `SplitButton` composable — verified in `compose/material3/material3/api/current.txt` at
androidx HEAD `360e8cba`, 2026-08-14. What alpha25 deprecated in this family are the
`SplitButtonDefaults.leadingButtonShapes(CornerSize)` / `trailingButtonShapes(CornerSize)` helpers,
superseded by `*ShapesFor(buttonHeight: Dp)` — that is most plausibly what the release note
"Deprecated `SplitButtonLayout` API" meant (**inference, not fact**).

### `ButtonGroup` with the 1.4.0 signature

❌
```kotlin
ButtonGroup(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
    clickableItem(onClick = ::onSort, label = "Sort")
}
```
✅
```kotlin
// Defensive only — neither ButtonGroup nor OverflowIndicator is annotated in the alpha26
// current.txt, so this opt-in is redundant (warning, not error) at this pin. See the header note.
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
ButtonGroup(
    overflowIndicator = { menuState -> ButtonGroupDefaults.OverflowIndicator(menuState) },
) {
    clickableItem(onClick = ::onSort, label = "Sort")
    clickableItem(onClick = ::onFilter, label = "Filter")
}
```
`overflowIndicator` became the **first, required, positional** parameter in alpha22 and the old
3-param overload was removed. Pass `{}` if you genuinely want no overflow affordance.

### `animateWidth` with a mismatched interaction source

❌
```kotlin
FilledIconButton(
    onClick = onPlay,
    interactionSource = remember { MutableInteractionSource() },
    modifier = Modifier.size(56.dp).animateWidth(remember { MutableInteractionSource() }),
) { Icon(Icons.Filled.PlayArrow, "Play") }
```
✅
```kotlin
val sources = remember { List(3) { MutableInteractionSource() } }
FilledIconButton(
    onClick = onPlay,
    interactionSource = sources[0],
    modifier = Modifier.size(56.dp).animateWidth(sources[0]),
) { Icon(Icons.Filled.PlayArrow, "Play") }
```
Two different sources compile fine and animate nothing. If you tune `compressionLimit`, it is a **`Dp`**
on alpha25+ (`compressionLimit = 8.dp`), not `PaddingValues`; the 1-arg form is portable across pins.

### Wavy stroke in dp

❌
```kotlin
CircularWavyProgressIndicator(
    progress = { f }, modifier = Modifier.size(240.dp),
    stroke = Stroke(width = 12f),      // meant 12dp — this is 12 PIXELS
    wavelength = 40.dp,
)
```
✅
```kotlin
val density = LocalDensity.current
val stroke = remember(density) {
    Stroke(width = with(density) { 12.dp.toPx() }, cap = StrokeCap.Round)
}
CircularWavyProgressIndicator(
    progress = { f }, modifier = Modifier.size(240.dp),
    stroke = stroke, trackStroke = stroke,
    wavelength = 40.dp,   // Dp — do NOT convert
    gapSize = 8.dp,       // Dp
)
```
`stroke`/`trackStroke` take a `Stroke` whose `width` is a **`Float` in pixels**; `wavelength`, `gapSize`
and `stopSize` on the same call are `Dp`. `StrokeCap.Round` is not decoration — square caps chop the
crests.

### Wavy too small

❌
```kotlin
CircularWavyProgressIndicator(modifier = Modifier.size(20.dp))
```
✅
```kotlin
CircularProgressIndicator(modifier = Modifier.size(20.dp))
```
"At very small sizes, the wavy shape may not be as visible." Below ~40dp you pay the complexity and get
no signal.

### `LoadingIndicator` for a long or determinate wait

❌
```kotlin
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
if (uploading) LoadingIndicator()          // a 40 MB upload that reports bytes
```
✅
```kotlin
LinearWavyProgressIndicator(
    progress = { bytesSent.toFloat() / totalBytes },
    modifier = Modifier.fillMaxWidth(),
)
```
The loading indicator is for waits "under five seconds" and must not be used "if processes transition
from indeterminate to determinate states." Determinate-vs-indeterminate is a **semantic** claim —
choosing wrong misreports state to assistive tech.

### Two bottom containers

❌
```kotlin
Scaffold(bottomBar = { ShortNavigationBar { … } }) { padding ->
    Box(Modifier.padding(padding)) {
        Content()
        HorizontalFloatingToolbar(expanded = true,
            modifier = Modifier.align(Alignment.BottomCenter)) { … }
    }
}
```
✅
```kotlin
// primary destination — nav bar only
Scaffold(bottomBar = { ShortNavigationBar { … } }) { p -> Content(Modifier.padding(p)) }

// action page one level down — toolbar only
Scaffold { p ->
    Box(Modifier.padding(p)) {
        Content()
        HorizontalFloatingToolbar(
            expanded = true,
            modifier = Modifier.align(Alignment.BottomCenter)
                .offset(y = -FloatingToolbarDefaults.ScreenOffset),
        ) { … }
    }
}
```
"Show the navigation bar on primary pages, and toolbars on subsequent pages with actions." Two stacked
bottom containers gives the user two competing "where am I" affordances.

### Speed dial / stacked small FABs

❌
```kotlin
Column(horizontalAlignment = Alignment.End) {
    if (expanded) {
        SmallFloatingActionButton(onClick = ::addNote) { Icon(Icons.Filled.Note, "Note") }
        SmallFloatingActionButton(onClick = ::addPhoto) { Icon(Icons.Filled.Photo, "Photo") }
    }
    FloatingActionButton(onClick = { expanded = !expanded }) { Icon(Icons.Filled.Add, "Add") }
}
```
✅
```kotlin
FloatingActionButtonMenu(
    expanded = expanded,
    button = {
        ToggleFloatingActionButton(
            checked = expanded,
            onCheckedChange = { expanded = it },
            modifier = Modifier.animateFloatingActionButton(
                visible = true, alignment = Alignment.BottomEnd),
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
    FloatingActionButtonMenuItem(onClick = ::addNote,
        icon = { Icon(Icons.Filled.Note, null) }, text = { Text("Note") })
    FloatingActionButtonMenuItem(onClick = ::addPhoto,
        icon = { Icon(Icons.Filled.Photo, null) }, text = { Text("Photo") })
}
```
The FAB menu exists specifically to replace the speed dial and stacked small FABs. Hard constraints:
**2–6 items**, opens in the same place as its FAB, and **never with an `ExtendedFloatingActionButton`.**

### FAB menu clipped by the Scaffold slot

❌
```kotlin
Scaffold(floatingActionButton = {
    FloatingActionButtonMenu(expanded = expanded, button = { … }) { … }
}) { padding -> … }
```
✅
```kotlin
BackHandler(enabled = expanded) { expanded = false }

Scaffold(floatingActionButton = {
    Box(Modifier.wrapContentSize(unbounded = true)) {
        FloatingActionButtonMenu(expanded = expanded, button = { … }) { … }
    }
}) { padding -> … }
```
The FAB slot is measured to the **collapsed** FAB, so the expanded menu is clipped to that box — the
single most common FAB-menu bug. And back must close the menu; the component does not do that for you.

### Deprecated bars

❌
```kotlin
Scaffold(bottomBar = { BottomAppBar(actions = { … }) }) { p -> … }
LargeTopAppBar(title = { Text("Settings") }, scrollBehavior = scrollBehavior)
```
✅
```kotlin
Scaffold(bottomBar = { FlexibleBottomAppBar { … } }) { p -> … }
LargeFlexibleTopAppBar(
    title = { Text("Settings") },
    subtitle = { Text("Account") },
    scrollBehavior = scrollBehavior,
)
```
The bottom app bar "should be replaced with the docked toolbar"; the flexible top-bar variants "replace
the deprecated medium and large variants" and add subtitle, wrapping and alignment control.
`FlexibleBottomAppBar` also takes `scrollBehavior: BottomAppBarScrollBehavior?` — check the factory
name on `BottomAppBarDefaults` at your pin before naming it.

### Scroll behavior wired only halfway

❌
```kotlin
val sb = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
Scaffold(topBar = { LargeFlexibleTopAppBar(title = { … }, scrollBehavior = sb) }) { p ->
    LazyColumn(Modifier.padding(p)) { … }
}
```
✅
```kotlin
val sb = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
Scaffold(
    topBar = { LargeFlexibleTopAppBar(title = { … }, scrollBehavior = sb) },
    modifier = Modifier.nestedScroll(sb.nestedScrollConnection),
) { p -> LazyColumn(Modifier.padding(p)) { … } }
```
The behavior has two halves: the component consumes it, the `Scaffold` feeds it nested-scroll deltas.
Miss the modifier and the bar never collapses, with no error.

### Eight cards where a segmented group belongs

❌
```kotlin
Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
    settings.forEach { s ->
        Card(onClick = { s.open() }, modifier = Modifier.fillMaxWidth()) {
            ListItem(headlineContent = { Text(s.title) })
        }
    }
}
```
✅
```kotlin
Column(verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap)) {
    settings.forEachIndexed { index, s ->
        SegmentedListItem(
            onClick = { s.open() },
            shapes = ListItemDefaults.segmentedShapes(index = index, count = settings.size),
            colors = ListItemDefaults.colors(
                containerColor = MaterialTheme.colorScheme.surfaceContainer),
            supportingContent = { Text(s.summary) },
        ) { Text(s.title) }
    }
}
```
Eight cards assert eight independent things; a segmented group asserts one group of eight options.
Note the Expressive overloads take the headline as the **trailing `content` lambda**, not
`headlineContent` — mixing the two produces confusing "no applicable overload" errors.

### `Modifier.clip` inside a carousel

❌
```kotlin
HorizontalMultiBrowseCarousel(state = carouselState, preferredItemWidth = 320.dp) { i ->
    Image(painter, items[i].title, Modifier.clip(RoundedCornerShape(16.dp)))
}
```
✅
```kotlin
HorizontalMultiBrowseCarousel(
    state = carouselState,
    preferredItemWidth = 320.dp,
    itemSpacing = 16.dp,
    modifier = Modifier.fillMaxWidth().height(320.dp),
) { i ->
    Image(painter, items[i].title, Modifier.maskClip(MaterialTheme.shapes.extraLarge))
}
```
`maskClip` is a `CarouselItemScope` modifier that clips to the carousel's *current* mask, so corners
animate as items move between slots. `clip` gives a static corner that visibly clips wrong at the edges.

---

## Layout & adaptive

### `Configuration.screenWidthDp`

❌
```kotlin
val isTablet = LocalConfiguration.current.screenWidthDp >= 600
if (isTablet) TwoPaneLayout() else SinglePaneLayout()
```
✅
```kotlin
val wsc = currentWindowAdaptiveInfoV2().windowSizeClass
if (wsc.isWidthAtLeastBreakpoint(WindowSizeClass.WIDTH_DP_EXPANDED_LOWER_BOUND)) TwoPaneLayout()
else SinglePaneLayout()
```
`screenWidthDp` is the *device* screen, not the app's window — wrong in split-screen, freeform windows
and on foldables. Note the threshold is **840dp**: Medium width is single-pane by Material's directive.

### `currentWindowAdaptiveInfo()`

❌
```kotlin
val info = currentWindowAdaptiveInfo()
```
✅
```kotlin
val info = currentWindowAdaptiveInfoV2()
```
The old one is deprecated in adaptive 1.3.0 and defaults `supportLargeAndXLargeWidth = false`, so it
**silently clamps everything ≥840dp to Expanded** — a desktop window gets a tablet layout.

### `when` chain smallest-first

❌
```kotlin
when {
    wsc.isWidthAtLeastBreakpoint(WindowSizeClass.WIDTH_DP_MEDIUM_LOWER_BOUND) -> Layout.Medium
    wsc.isWidthAtLeastBreakpoint(WindowSizeClass.WIDTH_DP_EXPANDED_LOWER_BOUND) -> Layout.Expanded
    wsc.isWidthAtLeastBreakpoint(WindowSizeClass.WIDTH_DP_LARGE_LOWER_BOUND) -> Layout.Large
    else -> Layout.Compact
}
```
✅
```kotlin
when {
    wsc.isWidthAtLeastBreakpoint(WindowSizeClass.WIDTH_DP_EXTRA_LARGE_LOWER_BOUND) -> Layout.XLarge
    wsc.isWidthAtLeastBreakpoint(WindowSizeClass.WIDTH_DP_LARGE_LOWER_BOUND)       -> Layout.Large
    wsc.isWidthAtLeastBreakpoint(WindowSizeClass.WIDTH_DP_EXPANDED_LOWER_BOUND)    -> Layout.Expanded
    wsc.isWidthAtLeastBreakpoint(WindowSizeClass.WIDTH_DP_MEDIUM_LOWER_BOUND)      -> Layout.Medium
    else -> Layout.Compact
}
```
All predicates are `>=`, so smallest-first matches its first branch for **every** window ≥600dp — a
1920dp desktop gets the Medium layout. Width has 5 buckets, height 3; `containsWidthDp` does not exist.

### Nav container inside the `NavDisplay`

❌
```kotlin
NavDisplay(backStack = backStack) { key ->
    when (key) {
        Home -> NavEntry(key) {
            Scaffold(bottomBar = { ShortNavigationBar { … } }) { p -> HomeScreen(p) }
        }
        Search -> NavEntry(key) {
            Scaffold(bottomBar = { ShortNavigationBar { … } }) { p -> SearchScreen(p) }
        }
    }
}
```
✅
```kotlin
SharedTransitionLayout {
    Scaffold(bottomBar = { ShortNavigationBar { … } }) { padding ->
        NavDisplay(backStack = backStack, modifier = Modifier.padding(padding)) { key ->
            when (key) {
                Home -> NavEntry(key) { HomeScreen() }
                Search -> NavEntry(key) { SearchScreen() }
            }
        }
    }
}
```
A container recreated per destination re-enters on every navigation (visible flicker) and kills
cross-destination shared elements — the shared bounds have no common parent to fly through.

### Locked orientation and lost state

❌
```xml
<activity android:name=".MainActivity"
    android:screenOrientation="portrait"
    android:resizeableActivity="false" />
```
```kotlin
var selectedTab by remember { mutableIntStateOf(0) }
```
✅
```xml
<activity android:name=".MainActivity"
    android:configChanges="orientation|screenSize|screenLayout|keyboardHidden|smallestScreenSize|uiMode"
    android:enableOnBackInvokedCallback="true" />
```
```kotlin
var selectedTab by rememberSaveable { mutableIntStateOf(0) }
```
On Android 16 / API 36 large screens can no longer opt out of resizing — the lock is ignored and
untested paths run. And an expressive layout that swaps container at a breakpoint must not reset the
selected destination, scroll position or expanded pane while doing it.

---

## Accessibility

### XSmall button with a 32dp touch target

❌
```kotlin
IconButton(onClick = onClear, modifier = Modifier.size(32.dp)) {
    Icon(Icons.Filled.Close, contentDescription = "Clear")
}
```
✅
```kotlin
IconButton(
    onClick = onClear,
    modifier = Modifier.minimumInteractiveComponentSize().size(32.dp),
) { Icon(Icons.Filled.Close, contentDescription = "Clear") }
```
Minimum target is **48 × 48dp**, separated by ≥8dp. The Expressive scale makes this easy to violate —
XSmall is 32dp by spec. (A connected group's 2dp internal spacing is spec: the members are one object.)

### Icon-only control with no name

❌
```kotlin
IconButton(onClick = onShuffle) { Icon(Icons.Rounded.Shuffle, contentDescription = null) }
```
✅
```kotlin
IconButton(
    onClick = onShuffle,
    modifier = Modifier.semantics { contentDescription = shuffleLabel },
) { Icon(Icons.Rounded.Shuffle, contentDescription = null) }
```
`contentDescription = null` is **correct** for a decorative icon inside a labelled control and a
**failure** when the icon is the control's only content. Name the control, not the icon.

### Toggle with no state description

❌
```kotlin
ToggleFloatingActionButton(checked = expanded, onCheckedChange = { expanded = it }) { … }
```
✅
```kotlin
ToggleFloatingActionButton(
    checked = expanded,
    onCheckedChange = { expanded = it },
    modifier = Modifier.semantics {
        traversalIndex = -1f
        stateDescription = if (expanded) expandedLabel else collapsedLabel
        contentDescription = menuLabel
    },
) { … }
// on the LAST menu item — the detail almost everyone misses:
Modifier.semantics {
    isTraversalGroup = true
    customActions = listOf(
        CustomAccessibilityAction(closeMenuLabel) { onExpandedChange(false); true })
}
```
TalkBack otherwise announces a generic "checked/unchecked" for something that is expanded/collapsed,
and there is no switch-access way to dismiss the menu. `traversalIndex = -1f` puts the opener first.

### Connected group with no role

❌
```kotlin
ToggleButton(checked = selected == i, onCheckedChange = { onSelect(i) },
    shapes = ButtonGroupDefaults.connectedMiddleButtonShapes()) { Text(label) }
```
✅
```kotlin
ToggleButton(
    checked = selected == i,
    onCheckedChange = { onSelect(i) },
    modifier = Modifier.semantics { role = Role.RadioButton },
    shapes = ButtonGroupDefaults.connectedMiddleButtonShapes(),
) { Text(label) }
```
A single-select connected group **is** a radio group; without the role TalkBack announces N independent
checkboxes and never says "2 of 5". Use `Role.Button` for independent actions and for the
`checked = false` action trick.

### Overlay traversal order

❌
```kotlin
Box {
    HorizontalFloatingToolbar(expanded = true,
        modifier = Modifier.align(Alignment.BottomCenter)) { … }
    LazyColumn { items(rows) { RowItem(it) } }
}
```
✅
```kotlin
Box {
    LazyColumn(Modifier.semantics { isTraversalGroup = true; traversalIndex = 0f }) {
        items(rows) { RowItem(it) }
    }
    HorizontalFloatingToolbar(
        expanded = true,
        modifier = Modifier.align(Alignment.BottomCenter)
            .semantics { isTraversalGroup = true; traversalIndex = 1f },
    ) { … }
}
```
Declaration order drives default traversal, so the ❌ version reads the toolbar before all the content
it floats over. Do **not** additionally force the toolbar collapsed under a screen reader — it
force-expands and disables `scrollBehavior` deliberately.

---

## Performance

### `Morph` / `toShape()` in a hot path

❌
```kotlin
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun MorphingBadge(active: Boolean) {
    val progress by animateFloatAsState(if (active) 1f else 0f, label = "p")
    val morph = Morph(MaterialShapes.Circle, MaterialShapes.Cookie9Sided)   // every recomposition
    Box(Modifier.size(64.dp).clip(MorphShape(morph, progress)))   // MorphShape = your own Shape
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun TrackList(tracks: List<Track>) {
    LazyColumn {
        items(tracks) { Box(Modifier.clip(MaterialShapes.Cookie4Sided.toShape())) { … } }
    }
}
```
✅
```kotlin
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun MorphingBadge(active: Boolean) {
    val morph = remember { Morph(MaterialShapes.Circle, MaterialShapes.Cookie9Sided) }
    val progress by animateFloatAsState(
        targetValue = if (active) 1f else 0f,
        animationSpec = MaterialTheme.motionScheme.fastSpatialSpec(),
        label = "badgeMorph",
    )
    Box(Modifier.size(64.dp).clip(MorphShape(morph) { progress }))
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun TrackList(tracks: List<Track>) {
    // toShape() is @Composable — call it once here, above the list, never inside items { }.
    val cookieShape = MaterialShapes.Cookie4Sided.toShape()
    LazyColumn { items(tracks, key = { it.id }) { Box(Modifier.clip(cookieShape)) { … } } }
}
```
`Morph` construction runs a feature-mapping algorithm between two polygons; `toShape()` is `@Composable`
and allocates per call. Remember the morph, hoist the shape, and pass progress as a lambda so the shape
reads it in the draw phase. `Morph.toPath` is not `@Composable`, and there is no `Morph.toShape()`.

### Animating layout where draw would do

❌
```kotlin
val scale by animateFloatAsState(if (pressed) 0.95f else 1f, label = "press")
Box(Modifier.size(120.dp * scale)) { … }
```
✅
```kotlin
val scale by animateFloatAsState(
    if (pressed) 0.95f else 1f,
    animationSpec = MaterialTheme.motionScheme.fastSpatialSpec(),
    label = "press",
)
Box(Modifier.size(120.dp).graphicsLayer { scaleX = scale; scaleY = scale }) { … }
```
Animating `Modifier.size` re-measures the subtree every frame and moves siblings; `graphicsLayer`
invalidates draw only — and does **not** shrink the touch target. Same rule for
`Modifier.offset { IntOffset(...) }` over `Modifier.offset(x.dp, y.dp)`.

### Progress passed as a value

❌
```kotlin
LinearWavyProgressIndicator(progress = fraction, modifier = Modifier.fillMaxWidth())
```
✅
```kotlin
LinearWavyProgressIndicator(progress = { fraction }, modifier = Modifier.fillMaxWidth())
```
`progress: () -> Float` exists to keep the recomposition scope at the leaf. Reading the value in the
enclosing composable recomposes it on every tick — for a ticking timer, every frame.

### Unstable lambdas in hot list items

❌
```kotlin
LazyColumn {
    items(tracks) { track ->
        ListItem(onClick = { viewModel.select(track.id) }) { Text(track.title) }
    }
}
```
✅
```kotlin
val onSelect = remember(viewModel) { { id: String -> viewModel.select(id) } }
LazyColumn {
    items(tracks, key = { it.id }) { track ->
        ListItem(onClick = { onSelect(track.id) }) { Text(track.title) }
    }
}
```
An unstable lambda makes the item composable unskippable — in a scrolling list that is every item, every
frame. Add `key = { … }` while you are there, or insertions recompose everything below the change.

---

## Cross-references

| Topic | File |
| --- | --- |
| Which component / spec / layout to choose | `${CLAUDE_PLUGIN_ROOT}/skills/m3-expressive-best-practices/references/decision-trees.md` |
| Ranked failure modes with symptoms and greps | `${CLAUDE_PLUGIN_ROOT}/skills/m3-expressive-best-practices/references/common-mistakes.md` |
| Button / group / split-button API detail | `${CLAUDE_PLUGIN_ROOT}/skills/m3-expressive-components/references/buttons.md` |
| FAB menu, floating & docked toolbars | `${CLAUDE_PLUGIN_ROOT}/skills/m3-expressive-components/references/fabs-and-toolbars.md` |
| Progress, loading, pull-to-refresh | `${CLAUDE_PLUGIN_ROOT}/skills/m3-expressive-components/references/progress-and-loading.md` |
| App bars, flexible bars, search | `${CLAUDE_PLUGIN_ROOT}/skills/m3-expressive-components/references/app-bars.md` |
| Lists, cards, carousels, sheets | `${CLAUDE_PLUGIN_ROOT}/skills/m3-expressive-components/references/lists-cards-containers.md` |
| Motion scheme, spring constants, reduced motion | `${CLAUDE_PLUGIN_ROOT}/skills/m3-expressive-motion/references/motion-scheme.md` |
| Shape catalog, morph recipes | `${CLAUDE_PLUGIN_ROOT}/skills/m3-expressive-shapes/references/shapes-catalog.md` |
| Color, typography, shape scale, theme recipes | `${CLAUDE_PLUGIN_ROOT}/skills/m3-expressive-theming/references/` |
| Nav containers, nav3 | `${CLAUDE_PLUGIN_ROOT}/skills/m3-expressive-navigation/references/nav-containers.md` |
| Window size classes, pane scaffolds, foldables | `${CLAUDE_PLUGIN_ROOT}/skills/m3-adaptive/references/` |
| Versions, opt-ins, breaking changes | `${CLAUDE_PLUGIN_ROOT}/skills/m3-expressive/references/setup-and-versions.md` |
| Audit checklist and severity labels | `${CLAUDE_PLUGIN_ROOT}/skills/m3-expressive-review/references/review-checklist.md` |
| Design tactics, hero budget, expression levers | `${CLAUDE_PLUGIN_ROOT}/skills/m3-expressive/references/design-principles.md` |

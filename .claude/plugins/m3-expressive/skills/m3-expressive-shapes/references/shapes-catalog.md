# M3 Expressive Shape System — API & Catalog Reference

Everything the shape system exposes: the rounded-rect scale, the 35-shape `MaterialShapes` polygon
catalog, the conversion functions, and the `androidx.graphics.shapes` primitives underneath.

Confidence markers used below:
- **[verified]** — signature or value read from material3 source / API listing.
- **[canonical-form]** — the shape of the API is right and this is how everyone writes it, but the
  exact signature was not readable from source. Compile-check before trusting.
- **[judgment]** — practical guidance, not an API fact.

---

## 1. Dependency and opt-in

```kotlin
dependencies {
    implementation("androidx.compose.material3:material3:1.5.0-alpha26")  // or 1.4.0
    implementation("androidx.graphics:graphics-shapes:1.1.0")
}
```

material3 pulls `graphics-shapes` in transitively, but **declare it explicitly**. The moment you
touch `MaterialShapes.X` as a `RoundedPolygon`, build a `Morph`, or construct your own polygon, you
are writing against `androidx.graphics.shapes` types directly and want a pinned version. (Med's
`libs.versions.toml` pins `graphicsShapes = "1.1.0"` as
`androidx-graphics-shapes = { group = "androidx.graphics", name = "graphics-shapes", version.ref = "graphicsShapes" }`.)

### The opt-in situation — read this before assuming it's stable

`MaterialShapes`, `RoundedPolygon.toShape()`, `RoundedPolygon.toPath()` and `Morph.toPath()` were
promoted to stable, and then **the promotion was reverted in 1.5.0-alpha19**:

> 1.5.0-alpha19 release note: *"Revert `MaterialShapes` and `LoadingIndicator` promotions to stable."*

So **as of 1.5.0-alpha26 they still require**:

```kotlin
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
```

This is the single most common wrong assumption about the shape system. Most of the Expressive
surface (`ToggleButton` alpha19, `ButtonGroup` alpha22, expressive `ListItem` alpha23, wavy progress
alpha18, `MaterialExpressiveTheme` alpha18) *did* graduate. `MaterialShapes` did not. Neither did
`LoadingIndicator`.

Opt in per-file (what Med and Tomato do):

```kotlin
@file:OptIn(
    ExperimentalMaterial3Api::class,
    ExperimentalMaterial3ExpressiveApi::class,
)
```

Or globally in `build.gradle.kts`:

```kotlin
kotlin {
    compilerOptions {
        optIn.addAll(
            "androidx.compose.material3.ExperimentalMaterial3Api",
            "androidx.compose.material3.ExperimentalMaterial3ExpressiveApi",
        )
    }
}
```

`ExperimentalMaterial3ExpressiveApi` has no explicit level → default `RequiresOptIn.Level.ERROR`.
Missing the opt-in is a compile error, not a warning.

Note also `Material3ExpressiveApi` (added alpha18): a **marker annotation, not a gate**. It labels
an API as Expressive without forcing opt-in. Seeing it does not mean you need `@OptIn`.

---

## 2. Two shape systems — pick the right one

| | Rounded-rect scale | Polygon catalog |
| --- | --- | --- |
| Types | `Shapes`, `CornerBasedShape`, `RoundedCornerShape` | `MaterialShapes`, `RoundedPolygon`, `Morph` |
| Package | `androidx.compose.material3` / `.foundation.shape` | `androidx.compose.material3` + `androidx.graphics.shapes` |
| Themeable | yes — `MaterialTheme.shapes` | no — values are constants |
| Used by components | yes, by default | no, you apply them manually |
| Animatable | corner radii lerp cheaply | `Morph` between polygons |
| Safe for text containers | yes | **no** — see §9 |
| Opt-in needed | no | yes (`ExperimentalMaterial3ExpressiveApi`) |

**Decision rule [judgment]:** default to the rounded-rect scale for anything that contains content
(cards, list items, sheets, buttons, text fields). Reach for the polygon catalog only for avatars,
icon backdrops, badges, empty-state art, loading art, and decorative accents. A polygon in a core
component costs legibility and buys very little.

---

## 3. The rounded-rect scale

### 3.1 Shape tokens — `tokens/ShapeTokens.kt` [verified, exact dp]

| Token | Value |
| --- | --- |
| `CornerNone` | `RectangleShape` |
| `CornerExtraSmall` | `RoundedCornerShape(4.dp)` |
| `CornerSmall` | `RoundedCornerShape(8.dp)` |
| `CornerMedium` | `RoundedCornerShape(12.dp)` |
| `CornerLarge` | `RoundedCornerShape(16.dp)` |
| **`CornerLargeIncreased`** | **`RoundedCornerShape(20.dp)`** — Expressive addition |
| `CornerExtraLarge` | `RoundedCornerShape(28.dp)` |
| **`CornerExtraLargeIncreased`** | **`RoundedCornerShape(32.dp)`** — Expressive addition |
| **`CornerExtraExtraLarge`** | **`RoundedCornerShape(48.dp)`** — Expressive addition |
| `CornerFull` | `CircleShape` |

Directional variants [verified] — `CornerExtraSmallTop` (4dp top only), `CornerLargeTop` (16dp top),
`CornerExtraLargeTop` (28dp top), `CornerLargeStart` (16dp start side), `CornerLargeEnd` (16dp end
side); all zero the opposite two corners. `CornerSize` values exist for every step too:
`CornerValueNone` … `CornerValueExtraExtraLarge`.

**The three Expressive additions are 20dp / 32dp / 48dp.** Baseline M3 stopped at 28dp. Design intent:
"each step provides meaningful visual difference." If you are on Expressive and never using
`largeIncreased` / `extraLargeIncreased` / `extraExtraLarge`, you are shipping a baseline-M3 shape
feel.

### 3.2 `Shapes` class — `Shapes.kt` [verified]

```kotlin
class Shapes(
    val extraSmall: CornerBasedShape = ShapeDefaults.ExtraSmall,
    val small: CornerBasedShape = ShapeDefaults.Small,
    val medium: CornerBasedShape = ShapeDefaults.Medium,
    val large: CornerBasedShape = ShapeDefaults.Large,
    val extraLarge: CornerBasedShape = ShapeDefaults.ExtraLarge,
    largeIncreased: CornerBasedShape = ShapeDefaults.LargeIncreased,
    extraLargeIncreased: CornerBasedShape = ShapeDefaults.ExtraLargeIncreased,
    extraExtraLarge: CornerBasedShape = ShapeDefaults.ExtraExtraLarge,
)
```

The last three are **not** `val` in the primary constructor — they are exposed as separate
properties. A secondary constructor takes only the first five and defaults the rest; that is the
binary-compat shim for the pre-Expressive 5-step scale. Consequence: if you write
`Shapes(extraSmall = …, small = …, medium = …, large = …, extraLarge = …)` you silently get the
**default** 20/32/48 tokens for the three Expressive steps. Pass them explicitly if you are
customizing the scale.

`Shapes.kt` also has `Shapes.fromToken()` plus internal `top()` / `bottom()` / `start()` / `end()`
helpers. **There is no `toShape()` in `Shapes.kt`** — that lives in `MaterialShapes.kt`.

### 3.3 `ShapeDefaults` — [verified]

```kotlin
object ShapeDefaults {
    val ExtraSmall: CornerBasedShape          = ShapeTokens.CornerExtraSmall            // 4dp
    val Small: CornerBasedShape               = ShapeTokens.CornerSmall                 // 8dp
    val Medium: CornerBasedShape              = ShapeTokens.CornerMedium                // 12dp
    val Large: CornerBasedShape               = ShapeTokens.CornerLarge                 // 16dp
    val LargeIncreased: CornerBasedShape      = ShapeTokens.CornerLargeIncreased        // 20dp
    val ExtraLarge: CornerBasedShape          = ShapeTokens.CornerExtraLarge            // 28dp
    val ExtraLargeIncreased: CornerBasedShape = ShapeTokens.CornerExtraLargeIncreased   // 32dp
    val ExtraExtraLarge: CornerBasedShape     = ShapeTokens.CornerExtraExtraLarge       // 48dp
}
```

Plus `ShapeDefaults.CornerFull` — a **`CornerSize`**, not a `Shape`. It is what
`ButtonGroupDefaults.connectedLeadingButtonShape` and `SplitButtonDefaults.OuterCornerSize` are built
from. Use it when you need "pill on this side, N dp on that side":

```kotlin
RoundedCornerShape(
    topStart = ShapeDefaults.CornerFull,
    bottomStart = ShapeDefaults.CornerFull,
    topEnd = CornerSize(8.dp),
    bottomEnd = CornerSize(8.dp),
)
```

`ShapeDefaults.*` values are **not** theme-aware — they are the raw tokens. `MaterialTheme.shapes.*`
is. Prefer `MaterialTheme.shapes.large` over `ShapeDefaults.Large` in app code unless you
specifically want to bypass a theme override. Real usage: `Modifier.clip(ShapeDefaults.Large)` in
`/root/work/repos/vivi-music/app/src/main/kotlin/com/music/vivi/ui/component/GridMenu.kt`.

### 3.4 Optical roundness for nested shapes [judgment, but well-established]

LastChat documents the rule in `/root/work/repos/LastChat/app/src/main/java/me/rerere/rikkahub/ui/theme/Shape.kt`:

> Formula: **outer radius − padding = inner radius**
> CardLarge (28dp) with 12dp padding → 16dp inner
> CardLarge (28dp) with 8dp padding → 20dp inner
> Message bubble 20dp outer with 12dp padding → 8dp inner

Apply this whenever you nest a shaped surface inside another shaped surface. Ignoring it makes the
inner corner look either pinched or too square relative to the outer curve.

---

## 4. `MaterialShapes` — the polygon catalog

```kotlin
@ExperimentalMaterial3ExpressiveApi
sealed class MaterialShapes
```

> "Holds predefined Material Design shapes as `RoundedPolygon`s that can be used at various
> components as they are, or as part of a `Morph`. Note that each `RoundedPolygon` in this class is
> **normalized**." [verified doc string]

**Normalized** means each polygon's bounds fit a unit box — that is what makes `toShape()` able to
fill any size, and what makes a `Morph` between any two of them behave.

Reference image: `https://developer.android.com/images/reference/androidx/compose/material3/shapes.png`

### 4.1 The complete catalog

35 properties on the companion object, in declaration order. **Name and Doc columns are
[verified]**; "Good for" and "Reads down to" are **[judgment]** — practical guidance derived from
vertex count and concavity, not from Google.

"Reads down to" = the smallest square size at which the silhouette is still identifiable at typical
display density. Below it the shape degrades into a blob or a circle.

| # | Property | Doc string | Good for | Reads down to |
| --- | --- | --- | --- | --- |
| 1 | `Circle` | A circle shape. | Avatars, icon buttons, anything. The safe fallback at small size. | any |
| 2 | `Square` | A rounded square shape. | Icon backdrops, app-icon slots, thumbnails. Content-safe (convex, near-rect). | any |
| 3 | `Slanted` | A slanted square shape. | Decorative tiles, category chips, "this row is different" accents. | 32dp |
| 4 | `Arch` | An arch shape. | Media/hero containers with a directional top; onboarding illustration frames. | 40dp |
| 5 | `Fan` | A fan shape. | Decorative accents, single hero element. Strongly directional — do not mirror-tile it. | 40dp |
| 6 | `Arrow` | An arrow shape. | Directional badges, "next" affordances, progress markers. Reads as iconography — that is a risk, see §7. | 32dp |
| 7 | `SemiCircle` | A semi-circle shape. | Half-height headers, gauge caps, bottom-anchored decorative fills. | 32dp |
| 8 | `Oval` | An oval shape. | Wide avatars, media thumbs in wide slots. Non-square aspect by design. | 32dp |
| 9 | `Pill` | A pill shape. | Tags, status badges, count chips, marquee containers. Content-safe. | 24dp tall |
| 10 | `Triangle` | A rounded triangle shape. | Play affordances, warning badges, sharp accent against round neighbours. | 32dp |
| 11 | `Diamond` | A diamond shape. | Badges, rating markers, decorative accent. Wastes ~50% of its box — content-hostile. | 32dp |
| 12 | `ClamShell` | A clam-shell shape. | Decorative container, empty-state art. | 48dp |
| 13 | `Pentagon` | A pentagon shape. | Achievement/badge slots, icon backdrops. | 32dp |
| 14 | `Gem` | A gem shape. | Premium/pro badges, "special" markers. | 40dp |
| 15 | `Sunny` | A sunny shape. | Icon backdrops, avatar frames — the workhorse scallop. | 40dp |
| 16 | `VerySunny` | A very-sunny shape. | Same as `Sunny` but louder. Hero-only. Deep scallops eat corners of any content. | 56dp |
| 17 | `Cookie4Sided` | A 4-sided cookie shape. | **Album/track thumbnails, list-row avatars.** Best small-size cookie — only 4 lobes. | 32dp |
| 18 | `Cookie6Sided` | A 6-sided cookie shape. | Avatars, loading/thinking states (LastChat morphs this → `Circle`). | 40dp |
| 19 | `Cookie7Sided` | A 7-sided cookie shape. | Icon backdrops in settings/about rows. Odd count reads deliberately asymmetric. | 40dp |
| 20 | `Cookie9Sided` | A 9-sided cookie shape. | Larger icon backdrops, empty-state art. | 48dp |
| 21 | `Cookie12Sided` | A 12-sided cookie shape. | **Large decorative backdrops** (Tomato uses 64–128dp). Near-circular below ~48dp. | 56dp |
| 22 | `Ghostish` | A ghost-ish shape. | Playful empty states, mascot slots, error art. | 48dp |
| 23 | `Clover4Leaf` | A 4-leaf clover shape. | Icon backdrops, settings-row icons, avatar frames. | 40dp |
| 24 | `Clover8Leaf` | An 8-leaf clover shape. | **Album art in a player** (vivi-music), hero media. Very concave — never over text. | 64dp |
| 25 | `Burst` | A burst shape. | Notification/"new" badges, hero accents. Spiky; turns to mush small. | 56dp |
| 26 | `SoftBurst` | A soft-burst shape. | Same role as `Burst`, safer at mid sizes and next to text. | 40dp |
| 27 | `Boom` | A boom shape. | Celebration/reward moments, achievement art. Hero-only. | 56dp |
| 28 | `SoftBoom` | A soft-boom shape. | Softer celebration accent; usable as an avatar frame. | 48dp |
| 29 | `Flower` | A flower shape. | Avatars, profile frames, decorative icon backdrops. | 48dp |
| 30 | `Puffy` | A puffy shape. | **Content-friendly blob** — mostly convex, good for image thumbs and cards you want soft. | 32dp |
| 31 | `PuffyDiamond` | A puffy-diamond shape. | Badges, accent tiles, category markers. | 40dp |
| 32 | `PixelCircle` | A pixel-circle shape. | Retro/gaming skins, "8-bit" branding. Stepped edges alias badly when scaled or rotated. | 48dp |
| 33 | `PixelTriangle` | A pixel-triangle shape. | Same as `PixelCircle` — a stylistic commitment, not a neutral choice. | 48dp |
| 34 | `Bun` | A bun shape. | Soft container for an icon; friendly empty states. | 40dp |
| 35 | `Heart` | A heart shape. | Favorite/like states **only when the app genuinely means "like"** — the one shape with unavoidable literal meaning. | 32dp |

### 4.2 Cookies: only 4, 6, 7, 9, 12

**`Cookie5Sided`, `Cookie8Sided`, `Cookie10Sided`, `Cookie11Sided` do not exist.** Writing one is an
unresolved-reference compile error, and it is the single most common mistake with this API. Only:

```
MaterialShapes.Cookie4Sided
MaterialShapes.Cookie6Sided
MaterialShapes.Cookie7Sided
MaterialShapes.Cookie9Sided
MaterialShapes.Cookie12Sided
```

(vivi-music wanted a 10-sided cookie and shipped it as a hand-drawn vector drawable
`R.drawable.ic_ten_sided_cookie`, precisely because the API has no `Cookie10Sided`.)

Clovers are similarly limited: **`Clover4Leaf` and `Clover8Leaf` only** — no 5/6/7-leaf.

### 4.3 Count caveat

The property list enumerates 35 names; a source-side comment refers to "37 predefined shape
properties." The 35 names above are individually verified. If a 36th/37th exists it was not
observable. Do not guess additional names — check autocomplete against your pinned artifact.

---

## 5. Conversion functions — `MaterialShapes.kt` [verified, verbatim]

```kotlin
@ExperimentalMaterial3ExpressiveApi
fun Morph.toPath(progress: Float, path: Path = Path(), startAngle: Int = 0): Path

@ExperimentalMaterial3ExpressiveApi
@Composable
fun RoundedPolygon.toPath(startAngle: Int = 0): Path

@ExperimentalMaterial3ExpressiveApi
@Composable
fun RoundedPolygon.toShape(startAngle: Int = 0): Shape
```

Four facts that determine how you write morph code:

1. **`RoundedPolygon.toShape()` and `RoundedPolygon.toPath()` are `@Composable`.** They can only be
   called in composition — not inside `DrawScope`, not inside `Shape.createOutline`, not inside a
   `remember { }` lambda body. Hoist the result.
2. **`Morph.toPath()` is NOT `@Composable`.** It is a plain function, so it *can* be called inside
   `createOutline` or a draw scope. That asymmetry is deliberate: it is the escape hatch for
   per-frame morph rendering.
3. **There is no `Morph.toShape()`.** To use a morph as a clip/background `Shape` you must write the
   `Shape` yourself. An internal `MorphShape`-style helper appears to exist in material3 but is
   **not public** [unverified]. See `morph-recipes.md` §2 for the two working implementations.
4. `startAngle` is an **`Int`** (degrees), not a `Float`. It rotates the polygon's feature mapping —
   use it to de-stamp repeated shapes and to control which vertex faces up.

Minimal static use:

```kotlin
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
Box(
    Modifier
        .size(96.dp)
        .clip(MaterialShapes.Cookie9Sided.toShape())
        .background(MaterialTheme.colorScheme.primaryContainer)
)
```

`Modifier.background(color, shape)` accepts the polygon shape directly — you do not need `clip` if
you are only filling. `Modifier.shadow(elevation, shape = <polygon shape>)` also works and casts a
correct polygon shadow (verified in vivi-music's player).

---

## 6. `androidx.graphics.shapes` primitives

material3's `MaterialShapes.*` values **are** `androidx.graphics.shapes.RoundedPolygon` instances.
Anything you can do to a `RoundedPolygon` you can do to a `MaterialShapes` value.

### 6.1 Constructors and factories

> The graphics-shapes reference pages are JS-rendered and were not machine-readable. The signatures
> below are **[canonical-form]** — the names and parameter roles are right and this is how the
> library is used in practice, but verify parameter names/defaults against your artifact before
> relying on named arguments.

```kotlin
// Regular N-gon centered at (centerX, centerY)
RoundedPolygon(numVertices: Int, radius: Float = 1f, centerX: Float = 0f, centerY: Float = 0f,
               rounding: CornerRounding = CornerRounding.Unrounded,
               perVertexRounding: List<CornerRounding>? = null)

// From explicit vertices, as a flat [x0, y0, x1, y1, …] array
RoundedPolygon(vertices: FloatArray, rounding: CornerRounding = CornerRounding.Unrounded,
               perVertexRounding: List<CornerRounding>? = null,
               centerX: Float = Float.MIN_VALUE, centerY: Float = Float.MIN_VALUE)

RoundedPolygon.circle(numVertices: Int = 8, radius: Float = 1f, centerX: Float = 0f, centerY: Float = 0f)

RoundedPolygon.rectangle(width: Float = 2f, height: Float = 2f,
                         rounding: CornerRounding = CornerRounding.Unrounded,
                         perVertexRounding: List<CornerRounding>? = null,
                         centerX: Float = 0f, centerY: Float = 0f)

RoundedPolygon.star(numVerticesPerRadius: Int, radius: Float = 1f, innerRadius: Float = 0.5f,
                    rounding: CornerRounding = CornerRounding.Unrounded,
                    innerRounding: CornerRounding? = null,
                    perVertexRounding: List<CornerRounding>? = null,
                    centerX: Float = 0f, centerY: Float = 0f)

RoundedPolygon.pill(width: Float = 2f, height: Float = 1f, smoothing: Float = 0f,
                    centerX: Float = 0f, centerY: Float = 0f)
```

`.star()` is what "cookie" and "burst" shapes are: N vertices per radius, an inner radius controlling
lobe depth, and rounding on both radii. A cookie ≈ a star with a large `innerRadius` (shallow lobes)
and heavy rounding; a burst ≈ a small `innerRadius` (deep spikes) with light rounding.

### 6.2 `CornerRounding`

```kotlin
class CornerRounding(radius: Float = 0f, smoothing: Float = 0f)
CornerRounding.Unrounded  // radius = 0, smoothing = 0
```

- `radius` — corner arc radius in the polygon's own coordinate space (so it scales with `radius`
  passed to the polygon factory, typically 1f → radii are 0..~0.5).
- `smoothing` — 0f = pure circular arc; 1f = fully smoothed "squircle" continuous-curvature corner.
  This is the knob that makes M3 corners look like squircles rather than rounded rects.

### 6.3 Transforms

```kotlin
fun RoundedPolygon.normalized(): RoundedPolygon
fun RoundedPolygon.transformed(transform: PointTransformer): RoundedPolygon
fun RoundedPolygon.calculateBounds(bounds: FloatArray = FloatArray(4), approximate: Boolean = true): FloatArray
fun RoundedPolygon.calculateMaxBounds(bounds: FloatArray = FloatArray(4)): FloatArray
val RoundedPolygon.cubics: List<Cubic>
```
[canonical-form]

- **`.normalized()`** moves and scales the polygon so its bounds fit `[0,1] × [0,1]` centered.
  **Every `MaterialShapes` value is already normalized.** Your own polygons are not — call
  `.normalized()` or your shape will be off-center and the wrong size.
- **`.transformed(…)`** returns a new polygon with a point transform applied (rotate, scale, skew).
  Useful for baking a rotation into a polygon once rather than rotating the layer every frame.
- `calculateBounds` returns `[left, top, right, bottom]`.

### 6.4 `Morph`

```kotlin
class Morph(start: RoundedPolygon, end: RoundedPolygon)

fun Morph.asCubics(progress: Float): List<Cubic>
fun Morph.forEachCubic(progress: Float, callback: (Cubic) -> Unit)
fun Morph.calculateBounds(bounds: FloatArray = FloatArray(4), approximate: Boolean = true): FloatArray
```
[canonical-form except `asCubics`, which is verified by LastChat's working code]

`Morph` precomputes a mapping between the two polygons' features at construction time. **This is the
expensive part.** Construct once, `remember` it, then call `asCubics(progress)` / `toPath(progress)`
per frame.

`progress` is `0f` = fully `start`, `1f` = fully `end`. Values outside 0..1 are what a bouncy spring
will hand you during overshoot — clamp if your morph misbehaves past the endpoints.

`Cubic` exposes `anchor0X/Y`, `control0X/Y`, `control1X/Y`, `anchor1X/Y` as `Float`s — exactly what
`Path.cubicTo` needs after you transform them.

### 6.5 What 1.1.0 added

- Shape outline progress switched from **angle measurement to curve-length measurement**, "enabling
  morphing of more complex shapes"; improved feature-mapping for "more natural morphs". *If your
  morphs looked wrong on an older version, upgrade before debugging.*
- SVG path import `SvgPathParser.parseFeatures()`; feature serialization
  `FeatureSerializer.serialize()` / `.parse()`; polygon features exposed, and generic `RoundedPolygon`
  creation from a `List<Feature>`.
- The library demo is now a **Shape Editor**: import an SVG path, edit features, export production
  Kotlin. This is the sanctioned way to author a custom brand shape rather than hand-typing vertices.
- 1.1.0-beta01 added `mingwX64`, JS and Wasm KMP targets; requires KGP ≥ 2.0.0.

---

## 7. Design guidance

### 7.1 Shape contrast is relational

Canonical Material guidance (Tactic 1, "Use a variety of shapes"):

> "Shape can be a powerful communication tool in your interface."
> **Do:** "Break from the surrounding shape style to draw attention to a particular element."
> **Caution:** "Smaller shapes can result in essential actions looking less important."

The operative consequence: **a shape is emphatic only because its neighbours are not.** Applying
`Cookie12Sided` to every thumbnail in a list annihilates the emphasis it was meant to create and
just reads as a theme. If you want one item to matter, give the other items `Square` or a rounded
rect.

Related canon: *"Embrace tension"* — combine "sharp, angular forms alongside soft, rounded ones"
rather than applying one roundedness everywhere.

### 7.2 Shape as a state signal

Verified bindings where shape change carries meaning:

| State change | Shape behaviour |
| --- | --- |
| Button press | Round resting shape morphs squarer (or vice versa). The standard Expressive button feedback — `ButtonDefaults.shapes()`. |
| Toggle check/select | Morphs to `checkedShape`; connected groups use `ShapeTokens.CornerFull` when checked. |
| Split-button menu open | Trailing button "spins and changes shape when activated." |
| Loading | `LoadingIndicator` is "a looping shape morph sequence composed of seven unique Material 3 shapes." |
| Selection in a button group | Connected groups override member shapes to unify the group. |
| List item press/select | `ListItemShapes` carries per-state shapes; pressed/selected round out. |

Morphs are driven by the **spatial** spring, so they inherit overshoot. Do not drive a shape morph
with an effects spring (those exist specifically because "there shouldn't be any overshoot" on
non-spatial properties).

### 7.3 Avoid fixed meaning, apply intentionally

> "Avoid assigning a fixed, literal meaning to any single shape."

Shapes are not iconography. A clover does not mean "favorite," a gem does not mean "premium," a
burst does not mean "new." The one unavoidable exception is `Heart`, which readers *will* interpret
literally — only use it where the app genuinely means like/favorite.

> "Abstract and unconventional shapes should be used thoughtfully" — concentrate them in decorative
> moments such as avatars and media containers, and use them **sparingly in core components** where
> predictability matters.

Combined with the hero-moment budget (*"Stick to one or two hero moments in your product"*): **one
or two expressive polygon shapes per screen, maximum.** Everything else holds the rounded-rect
baseline so the break reads as a break. And the overriding rule: *"Don't compromise your product's
core functionality for visual flourishes. No amount of emotion can compensate for a lack of clarity."*

### 7.4 The expanded scale is the cheap win

Before reaching for polygons, exploit the three new corner steps. Moving a card from `large` (16dp)
to `extraLargeIncreased` (32dp), or a sheet to `extraExtraLarge` (48dp), is themeable, animatable,
content-safe, needs no opt-in, and delivers most of the Expressive shape feel at zero risk.

---

## 8. Performance

### 8.1 `toShape()` allocates — hoist it

`RoundedPolygon.toShape()` builds a `Path` and wraps it in a `Shape` object. Called inline inside a
`LazyColumn` item, that is one allocation per item per recomposition.

```kotlin
// BAD  — allocates inside every row, every recomposition
LazyColumn { items(songs) { Box(Modifier.size(40.dp).clip(MaterialShapes.Cookie4Sided.toShape())) { … } } }

// GOOD — hoist above the list
val cookieShape = MaterialShapes.Cookie4Sided.toShape()
LazyColumn { items(songs) { Box(Modifier.size(40.dp).clip(cookieShape)) { … } } }
```

vivi-music does this at screen scope in `.../ui/screens/settings/AboutScreen.kt` — two
`val …Shape = MaterialShapes.X.toShape()` hoists, then `iconShape = cookieShape` down to every row.

### 8.2 `remember` your polygons

`MaterialShapes.*` values are constants — no need to remember those. But polygons **you** construct
(`RoundedPolygon.star(...)`, `.transformed(...)`, `.normalized()`) run real geometry and must be
remembered:

```kotlin
val poly = remember { RoundedPolygon.star(8, innerRadius = 0.6f, rounding = CornerRounding(0.2f)).normalized() }
```

### 8.3 `Morph` construction is the expensive part — never build it per frame

`Morph(start, end)` computes the feature mapping between the two polygons. Building it inside a
composable body without `remember`, or worse inside `Shape.createOutline`, rebuilds that mapping on
every frame. That is the #1 cause of "my morph stutters."

```kotlin
// CORRECT — keyed on the polygons, not on progress (LastChat does exactly this)
val morph = remember(startPolygon, endPolygon) { Morph(startPolygon, endPolygon) }
```

Then remember the per-frame `Shape` against `(morph, progress, rotation)`, so only the cheap `Shape`
wrapper is reallocated while animating and the mapping is reused.

### 8.4 Prefer drawing over clipping; animate progress, not shape identity

`Modifier.clip(shape)` on a non-rect shape forces a save-layer / clip-path on every draw of that
subtree and clips **children** too. For a purely decorative shape, use `Modifier.background(color,
shape)` (fills, does not clip children, cheaper than `clip` + `background`) or
`Modifier.drawWithCache { onDrawBehind { drawPath(path, color) } }` (cheapest; path computed once per
size change). Reserve `clip` for masking real content — a photo, an image avatar.

Animating a `Float` progress that feeds one remembered `Morph` is cheap. Swapping the whole `Shape`
object via `AnimatedContent`, or crossfading two clipped `Box`es, is not — you pay two clip layers
during the transition.

---

## 9. Clipping caveats — where polygons bite

### 9.1 Concave shapes eat content

`Clover8Leaf`, `Burst`, `Boom`, `VerySunny`, `Flower`, `PixelTriangle` and the deep cookies have
large concave regions. Clipping a photo or a text block to one of these removes real content:

- **Photos** lose their corners and often their subject if the subject is off-center. Acceptable for
  album art and avatars (users forgive it); unacceptable for anything with information at the edge —
  charts, screenshots, maps, document previews.
- **Text** is simply cut. Never put a text container inside a polygon clip.
- **Icons** are fine if you size them ≤ ~55% of the container and center them (see §9.2).

### 9.2 Safe content insets for polygon containers [judgment]

There is no API for "the inscribed rect of a polygon." Use these as starting points for the largest
centered square content that stays clear of the clip:

| Shape family | Safe content ≈ % of container | Example |
| --- | --- | --- |
| `Circle`, `Square`, `Pill`, `Oval`, `Puffy` | 70–75% | 128dp box → 90dp icon |
| Cookies (4/6/7/9/12), `Sunny`, `SoftBurst`, `SoftBoom`, `Bun` | 55–60% | 128dp box → 72dp icon (Tomato's `DetailPlaceholder` uses exactly 128 → 72) |
| `Clover4Leaf`, `Clover8Leaf`, `Flower`, `PuffyDiamond` | 50% | 128dp box → 64dp icon |
| `Burst`, `Boom`, `VerySunny`, `Diamond`, `Triangle`, `Gem`, `Heart` | 40–45% | 128dp box → 52dp icon |

Verify visually. Tomato's pattern is the safe one: the polygon is a **sibling** `Spacer` with
`.background(color, shape)` and the `Icon` is drawn on top at a smaller size — the icon is never
clipped at all. See `morph-recipes.md` §1.2.

### 9.3 Four more traps

- **Ripple and touch target.** `Modifier.clip(polygon).clickable { }` clips the ripple to the polygon,
  but the **touch target** is still the layout bounds — a user can tap a concave notch that looks
  empty. Keep small polygon buttons in a ≥48dp layout box regardless of how much of it the shape fills.
- **Rotation + clip.** Rotating a clipped subtree rotates the content too. To spin the *mask* while
  content stays upright, counter-rotate: outer `graphicsLayer { rotationZ = r }`, inner `.clip(shape)`
  + `graphicsLayer { rotationZ = -r }` (`morph-recipes.md` §1.4).
- **Elevation.** `Modifier.shadow(elevation, shape = polygonShape)` renders a correct polygon shadow,
  but `Surface`/`Card` `tonalElevation` uses the shape for its outline too and a heavily concave shape
  reads as a mud smear. Use `shadow` on polygons, not tonal elevation.
- **Non-square boxes.** `toShape()` fills the given size, so a normalized polygon in a 200×80 box comes
  out **stretched**. Put the polygon in a square child, or take `minOf(scaleX, scaleY)` yourself
  (`morph-recipes.md` §2.1).

---

## 10. Quick decision table

| Need | Use |
| --- | --- |
| Card / sheet / dialog corners | `MaterialTheme.shapes.*` (`largeIncreased`, `extraLargeIncreased`, `extraExtraLarge` are the Expressive steps) |
| Button that reacts to press | `shapes = ButtonDefaults.shapes()` — not a manual morph |
| Toggle that reacts to check | `shapes = ToggleButtonShapes(shape, pressedShape, checkedShape)` — the old `ToggleButtonDefaults.shapes(...)` is HIDDEN on alpha25+ |
| Connected segmented buttons | `ButtonGroupDefaults.connected{Leading,Middle,Trailing}ButtonShapes()` + `ConnectedSpaceBetween` |
| Grouped settings list | `SegmentedListItem` + `ListItemDefaults.segmentedShapes(index, count)` + `ListItemDefaults.SegmentedGap` |
| Avatar / album thumb with personality | `MaterialShapes.Cookie4Sided` (small) or `Cookie6Sided` / `Clover4Leaf` (medium) via `.toShape()` |
| Big decorative icon backdrop | `MaterialShapes.Cookie12Sided.toShape()` as a sibling `Spacer` background |
| Shape that animates between two silhouettes | `Morph` + a custom `Shape` — `morph-recipes.md` §2 |
| Indeterminate loading | `LoadingIndicator` (still experimental) before hand-rolling morph art |
| Custom brand shape | graphics-shapes 1.1.0 Shape Editor demo → `SvgPathParser.parseFeatures()` → `RoundedPolygon` from features |
| Repeated shapes in a grid | one polygon, varying `startAngle` per cell |

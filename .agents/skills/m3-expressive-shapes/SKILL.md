---
name: m3-expressive-shapes
description: >
  Applies the Material 3 Expressive polygon shape system in Jetpack Compose — MaterialShapes
  (Cookie, Clover, Burst, Puffy, Pill, Sunny, Ghostish, Heart and the rest of the 35-shape
  catalog), RoundedPolygon, Morph, toShape(), toPath(), androidx.graphics:graphics-shapes,
  shape morphing on press or selection via ButtonDefaults.shapes, and segmented/connected list
  item corners. Use when the user asks for blob shapes, squircles, morphing buttons, animated
  shapes, cookie shapes, custom polygon avatars, or grouped list items with rounded outer
  corners.
---

# M3 Expressive Shapes & Morphing

## Two separate systems — do not conflate them

1. **The shape scale** (`MaterialTheme.shapes`, `RoundedCornerShape`) — rounded rectangles at
   eight sizes. This is what components use by default. Covered in **m3-expressive-theming**.
2. **`MaterialShapes` + `RoundedPolygon` + `Morph`** — arbitrary polygon geometry from
   `androidx.graphics:graphics-shapes`. This is the expressive blob/cookie/clover system.

This skill is about #2.

## Dependency and opt-in

```kotlin
implementation("androidx.graphics:graphics-shapes:1.1.0")
```

`MaterialShapes`, `toShape()` and `toPath()` **still require**
`@OptIn(ExperimentalMaterial3ExpressiveApi::class)` even on 1.5.0-alpha26 — their promotion to
stable was reverted in alpha19. Do not assume they graduated.

## The catalog

35 named shapes on `MaterialShapes`, all normalized `RoundedPolygon`s:

`Circle`, `Square`, `Slanted`, `Arch`, `Fan`, `Arrow`, `SemiCircle`, `Oval`, `Pill`, `Triangle`,
`Diamond`, `ClamShell`, `Pentagon`, `Gem`, `Sunny`, `VerySunny`, `Cookie4Sided`, `Cookie6Sided`,
`Cookie7Sided`, `Cookie9Sided`, `Cookie12Sided`, `Ghostish`, `Clover4Leaf`, `Clover8Leaf`,
`Burst`, `SoftBurst`, `Boom`, `SoftBoom`, `Flower`, `Puffy`, `PuffyDiamond`, `PixelCircle`,
`PixelTriangle`, `Bun`, `Heart`.

Only cookies **4, 6, 7, 9, 12** exist. There is no `Cookie5Sided` / `Cookie8Sided` /
`Cookie10Sided` / `Cookie11Sided` — writing one is a compile error people hit constantly.

## Static use — clip to a shape

```kotlin
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
Box(
    Modifier
        .size(96.dp)
        .clip(MaterialShapes.Cookie9Sided.toShape())
        .background(MaterialTheme.colorScheme.primaryContainer)
)
```

`RoundedPolygon.toShape(startAngle: Int = 0)` is `@Composable`. `startAngle` rotates the polygon
— useful for making a grid of identical shapes not look stamped.

## Animated use — morphing

`Morph(start, end)` interpolates between two `RoundedPolygon`s. Note the asymmetry:
`Morph.toPath(progress, path, startAngle)` is **not** `@Composable` and there is **no**
`Morph.toShape()`. To animate a morph you implement a custom `Shape` that calls `toPath` with
the current progress. The complete, working implementation is in
`references/morph-recipes.md` — copy it rather than reinventing, because the
`Matrix`/`scale`/`translate` bookkeeping is easy to get subtly wrong.

## Shape-by-interaction — the built-in path

Before hand-rolling a morph, check whether the component already supports it. Buttons and icon
buttons take a `shapes` parameter that animates between resting/pressed/checked shapes for free:

```kotlin
Button(
    onClick = { … },
    shapes = ButtonDefaults.shapes(),   // press morphs the corner radius
) { Text("Play") }

ToggleButton(
    checked = checked,
    onCheckedChange = { checked = it },
    shapes = ToggleButtonDefaults.shapesFor(ButtonDefaults.MinHeight),
) { Text("Shuffle") }
```

`IconButtonDefaults.shapes()`, `ToggleButtonShapes(shape, pressedShape, checkedShape)`
and friends work the same way. This is the correct first choice — it is themeable, respects
motion scheme, and costs one parameter. Note the toggle-button asymmetry: `ButtonDefaults.shapes()`
and `IconButtonDefaults.shapes()` are still current, but `ToggleButtonDefaults.shapes(...)` became
`DeprecationLevel.HIDDEN` in 1.5.0-alpha25 — use `shapesFor(height)` or the `ToggleButtonShapes`
constructor.

Note: `shapeByInteraction` is **internal** to those components, not public API. Do not write it.
Use the `shapes =` parameter.

## Segmented / connected list items

The "grouped settings list where the group has rounded outer corners and square inner corners"
idiom is first-class via `ListItemDefaults.segmentedShapes` / `segmentedColors` /
`SegmentedListItem`. These have existed since roughly 1.5.0-alpha15 and stopped being
experimental in **alpha23** — do not assume alpha23 is the introduction. A shipping app uses
them on alpha21. Check whether they resolve at the project's pin before falling back; the manual
corner-computation version is in the recipes file for genuinely older pins.

## Reference files

| Task | Read |
| --- | --- |
| Full catalog with usage notes, `RoundedPolygon`/`Morph` API surface, `ShapeDefaults`, graphics-shapes primitives | `references/shapes-catalog.md` |
| Working code: animated morph `Shape`, press-morph without `shapes =`, avatar morphing, segmented list corners, shape-based loading art, custom polygon drawing | `references/morph-recipes.md` |

## Design discipline

- **A morph should mean something.** Press, selection, expand, playback state. Decorative
  morphing on a timer is noise and it costs battery.
- **Do not put a cookie shape on everything.** One or two expressive shapes per screen, on the
  elements that deserve attention. See
  `${CLAUDE_PLUGIN_ROOT}/skills/m3-expressive/references/design-principles.md` on hero budgets.
- **Polygon shapes clip content badly.** Text and photos inside a `Clover8Leaf` get eaten at the
  concave points. Use polygons for avatars, icon backdrops, and decorative surfaces — not for
  containers holding dense content.
- **Check the shape at small sizes.** `Burst`, `VerySunny` and `Clover8Leaf` turn to mush below
  ~40dp. Fall back to `Circle` or `Pill` at small sizes.

## Verification

- Confirm `graphics-shapes` is on the classpath — the `RoundedPolygon` import resolves from
  `androidx.graphics.shapes`, not from material3.
- Confirm `@OptIn(ExperimentalMaterial3ExpressiveApi::class)` covers every `MaterialShapes` and
  `toShape()` call site.
- Render each custom shape at the smallest size it will actually appear at.
- For morphs, check both endpoints and mid-progress — a morph that looks right at 0f and 1f can
  self-intersect at 0.5f when vertex counts differ badly.

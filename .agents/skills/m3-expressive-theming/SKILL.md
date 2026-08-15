---
name: m3-expressive-theming
description: >
  Sets up or fixes Material 3 Expressive theming in Jetpack Compose — MaterialExpressiveTheme,
  color schemes, dynamic color, AMOLED/pure-black modes, album-art or image-seeded palettes
  via materialkolor, the expressive type scale, emphasized type styles, variable fonts
  (Roboto Flex / Google Sans Flex / FontVariation), and the expressive shape scale. Use when
  the user asks about Theme.kt, Type.kt, Color.kt, Shape.kt, colorScheme, typography, fonts,
  dark mode, dynamic color, Material You colors, contrast, or "my theme doesn't look
  expressive".
---

# M3 Expressive Theming

## Always start here: the theme wrapper

`MaterialTheme` does **not** provide a motion scheme. Expressive components that read
`MaterialTheme.motionScheme` silently fall back to standard motion. If the app wants expressive
feel, the root must be `MaterialExpressiveTheme`.

```kotlin
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun AppTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit,
) {
    val colorScheme = rememberAppColorScheme(darkTheme, dynamicColor)
    MaterialExpressiveTheme(
        colorScheme = colorScheme,
        typography = AppTypography,
        shapes = AppShapes,
        motionScheme = MotionScheme.expressive(),
        content = content,
    )
}
```

`motionScheme` is the parameter people forget. Pass it explicitly even when it looks redundant.

**Scoped expressive theming is legitimate.** A common real-world pattern is wrapping only the
subtree that needs expressive motion, inheriting everything else:

```kotlin
MaterialExpressiveTheme(
    colorScheme = MaterialTheme.colorScheme,
    typography = MaterialTheme.typography,
    shapes = MaterialTheme.shapes,
    motionScheme = MotionScheme.expressive(),
) { FloatingActionButtonMenu(/* … */) }
```

Use that when retrofitting an existing app incrementally — but prefer theming at the root once
the migration is done.

## Choosing the work

| Task | Read |
| --- | --- |
| Color roles, dynamic color, seeded palettes, AMOLED, contrast | `references/color.md` |
| Type scale, emphasized styles, variable fonts, `FontVariation` | `references/typography-and-variable-fonts.md` |
| Shape scale tokens, `Shapes`, `ShapeDefaults`, per-component shape defaults | `references/shape-scale.md` |
| Complete working `Theme.kt` / `Type.kt` / `Color.kt` / `Shape.kt` files from shipping apps | `references/theme-recipes.md` |

## Color: the facts that trip people up

- `expressiveLightColorScheme()` **exists**. `expressiveDarkColorScheme()` **does not** — use
  `darkColorScheme()`. This asymmetry is real, not a bug in your memory.
- Expressive adds **no new color roles**. `ColorScheme` is the same shape; the expressive light
  scheme differs from baseline in exactly four `on*Container` roles, moved from tone 10 to tone
  30. That is *more saturated but lower contrast* against the container — check small text on
  container surfaces still clears 4.5:1. See `references/color.md`.
- There is no `contrastLevel` parameter on the Compose scheme builders. System contrast comes
  from `dynamicLightColorScheme(context)` / `dynamicDarkColorScheme(context)` on Android 12+,
  which already reflect the user's contrast setting.
- For image-seeded color (album art, hero photo, user-picked accent), the community standard is
  `com.materialkolor:material-kolor` → `rememberDynamicColorScheme(seedColor, isDark, style,
  specVersion = ColorSpec.SpecVersion.SPEC_2025)`. `SPEC_2025` is the Expressive-era spec — pass
  it or you get the older palette math.

## Typography: the expressive scale

The M3 type scale is 15 baseline styles. Expressive adds **15 emphasized variants**
(`displayLargeEmphasized`, `titleMediumEmphasized`, `labelSmallEmphasized`, …) for a total of 30.
Emphasized styles keep the same size and line height and step the **weight** up (Medium, or Bold
for `labelLargeEmphasized`); tracking is per-token and does not always match the baseline. Use
them for the hero text on a screen, not for body copy.

Variable fonts are the single highest-leverage typography move in Expressive. One font file,
many instances, via `FontVariation.Settings`:

```kotlin
val GoogleSansFlex = FontFamily(
    Font(
        R.font.google_sans_flex,
        weight = FontWeight.Normal,
        variationSettings = FontVariation.Settings(
            FontVariation.weight(400),
            FontVariation.width(100f),
        ),
    ),
    // …one Font() entry per weight you actually use
)
```

Then either pass `Typography(fontFamily = GoogleSansFlex)` (constructor added in 1.5.0-alpha16 —
the parameter is `fontFamily`; there is no `defaultFontFamily` on material3 `Typography`) or map
all 15/30 slots explicitly. `references/typography-and-variable-fonts.md` has complete files from
three shipping apps, including a graded/slanted display face.

## Shape: the scale grew

The Expressive shape scale adds three steps to the classic five:

| Token | dp |
| --- | --- |
| ExtraSmall | 4 |
| Small | 8 |
| Medium | 12 |
| Large | 16 |
| **LargeIncreased** | **20** |
| ExtraLarge | 28 |
| **ExtraLargeIncreased** | **32** |
| **ExtraExtraLarge** | **48** |

The bolded three are the Expressive additions. Bigger radii read as more expressive; use the
increased steps on hero containers and sheets, not on every card.

## Verification

- Confirm `MaterialExpressiveTheme` (not `MaterialTheme`) wraps the app, with `motionScheme` set.
- Toggle light/dark and dynamic-on/dynamic-off; check `on*` pairings still meet contrast.
- If a custom font is used, confirm it renders at every weight the type scale asks for — missing
  variable-font instances silently fall back to synthetic bolding, which looks wrong.
- Check `MaterialTheme.shapes` is actually consumed by components rather than hardcoded
  `RoundedCornerShape(...)` sprinkled through screens.

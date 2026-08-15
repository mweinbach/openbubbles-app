# Typography & Variable Fonts — M3 Expressive

Scope: the 30-style scale, the `Typography` constructors, `FontVariation` / variable fonts, and four
complete real `Type.kt` files. Color is in `color.md`; shape in `shape-scale.md`; assembled themes in
`theme-recipes.md`.

**The two facts that matter:** Expressive adds **15 emphasized styles** on top of the 15 baseline
styles (30 total), and it expects a **variable font** driven by `FontVariation.Settings` rather than
a bundle of static weight files.

---

## 1. The 30-style scale

Baseline M3: 5 families (Display / Headline / Title / Body / Label) × 3 sizes (Large / Medium / Small)
= 15. Expressive adds a parallel `*Emphasized` set = 30.

### 1.1 Baseline styles

Values below are the M3 type-scale tokens as reproduced verbatim in
`/root/work/repos/vivi-music/app/src/main/kotlin/com/music/vivi/ui/theme/Type.kt` (whose comment cites
`m3.material.io/styles/typography/type-scale-tokens`).

| Style | Size | Line height | Tracking | Weight | Typeface token |
| --- | --- | --- | --- | --- | --- |
| `displayLarge` | 57.sp | 64.sp | -0.25.sp | Normal (400) | Brand |
| `displayMedium` | 45.sp | 52.sp | 0.sp | Normal | Brand |
| `displaySmall` | 36.sp | 44.sp | 0.sp | Normal | Brand |
| `headlineLarge` | 32.sp | 40.sp | 0.sp | Normal | Brand |
| `headlineMedium` | 28.sp | 36.sp | 0.sp | Normal | Brand |
| `headlineSmall` | 24.sp | 32.sp | 0.sp | Normal | Brand |
| `titleLarge` | 22.sp | 28.sp | 0.sp | Normal (M2 used Medium) | Brand |
| `titleMedium` | 16.sp | 24.sp | 0.15.sp | Medium (500) | Brand |
| `titleSmall` | 14.sp | 20.sp | 0.1.sp | Medium | Brand |
| `bodyLarge` | 16.sp | 24.sp | 0.5.sp | Normal | Plain |
| `bodyMedium` | 14.sp | 20.sp | 0.25.sp | Normal | Plain |
| `bodySmall` | 12.sp | 16.sp | 0.4.sp | Normal | Plain |
| `labelLarge` | 14.sp | 20.sp | 0.1.sp | Medium | Plain |
| `labelMedium` | 12.sp | 16.sp | 0.5.sp | Medium | Plain |
| `labelSmall` | 11.sp | 16.sp | 0.5.sp | Medium | Plain |

`TypefaceTokens.Brand` = the brand/display face (display, headline, title).
`TypefaceTokens.Plain` = the text face (body, label). Both default to the platform typeface until you
override them.

### 1.2 Emphasized styles

Accessors: `MaterialTheme.typography.displayLargeEmphasized`, `.titleLargeEmphasized`, … — all 15
`*Emphasized` properties exist on `Typography`.

Verbatim token values from `androidx-main` `compose/material3/.../tokens/TypeScaleTokens.kt` — only the
five **Large** variants were read from source:

```kotlin
val DisplayLargeEmphasizedFont       = TypefaceTokens.Brand
val DisplayLargeEmphasizedLineHeight = 64.0.sp
val DisplayLargeEmphasizedSize       = 57.sp
val DisplayLargeEmphasizedTracking   = 0.sp
val DisplayLargeEmphasizedWeight     = TypefaceTokens.WeightMedium

val HeadlineLargeEmphasizedFont       = TypefaceTokens.Brand
val HeadlineLargeEmphasizedLineHeight = 40.0.sp
val HeadlineLargeEmphasizedSize       = 32.sp
val HeadlineLargeEmphasizedTracking   = 0.sp
val HeadlineLargeEmphasizedWeight     = TypefaceTokens.WeightMedium

val TitleLargeEmphasizedFont       = TypefaceTokens.Brand
val TitleLargeEmphasizedLineHeight = 28.0.sp
val TitleLargeEmphasizedSize       = 22.sp
val TitleLargeEmphasizedTracking   = 0.sp
val TitleLargeEmphasizedWeight     = TypefaceTokens.WeightMedium

val BodyLargeEmphasizedFont       = TypefaceTokens.Plain
val BodyLargeEmphasizedLineHeight = 24.0.sp
val BodyLargeEmphasizedSize       = 16.sp
val BodyLargeEmphasizedTracking   = 0.15.sp
val BodyLargeEmphasizedWeight     = TypefaceTokens.WeightMedium

val LabelLargeEmphasizedFont       = TypefaceTokens.Plain
val LabelLargeEmphasizedLineHeight = 20.0.sp
val LabelLargeEmphasizedSize       = 14.sp
val LabelLargeEmphasizedTracking   = 0.1.sp
val LabelLargeEmphasizedWeight     = TypefaceTokens.WeightBold
```

### 1.3 The full 30-style table

| # | Style | Size | Line height | Tracking | Weight |
| --- | --- | --- | --- | --- | --- |
| 1 | `displayLarge` | 57 | 64 | -0.25 | Normal |
| 2 | `displayMedium` | 45 | 52 | 0 | Normal |
| 3 | `displaySmall` | 36 | 44 | 0 | Normal |
| 4 | `headlineLarge` | 32 | 40 | 0 | Normal |
| 5 | `headlineMedium` | 28 | 36 | 0 | Normal |
| 6 | `headlineSmall` | 24 | 32 | 0 | Normal |
| 7 | `titleLarge` | 22 | 28 | 0 | Normal |
| 8 | `titleMedium` | 16 | 24 | 0.15 | Medium |
| 9 | `titleSmall` | 14 | 20 | 0.1 | Medium |
| 10 | `bodyLarge` | 16 | 24 | 0.5 | Normal |
| 11 | `bodyMedium` | 14 | 20 | 0.25 | Normal |
| 12 | `bodySmall` | 12 | 16 | 0.4 | Normal |
| 13 | `labelLarge` | 14 | 20 | 0.1 | Medium |
| 14 | `labelMedium` | 12 | 16 | 0.5 | Medium |
| 15 | `labelSmall` | 11 | 16 | 0.5 | Medium |
| 16 | `displayLargeEmphasized` | **57** | **64** | **0** | **Medium** |
| 17 | `displayMediumEmphasized` | 45 *(assumed)* | 52 *(assumed)* | ? | Medium *(assumed)* |
| 18 | `displaySmallEmphasized` | 36 *(assumed)* | 44 *(assumed)* | ? | Medium *(assumed)* |
| 19 | `headlineLargeEmphasized` | **32** | **40** | **0** | **Medium** |
| 20 | `headlineMediumEmphasized` | 28 *(assumed)* | 36 *(assumed)* | ? | Medium *(assumed)* |
| 21 | `headlineSmallEmphasized` | 24 *(assumed)* | 32 *(assumed)* | ? | Medium *(assumed)* |
| 22 | `titleLargeEmphasized` | **22** | **28** | **0** | **Medium** |
| 23 | `titleMediumEmphasized` | 16 *(assumed)* | 24 *(assumed)* | ? | Medium *(assumed)* |
| 24 | `titleSmallEmphasized` | 14 *(assumed)* | 20 *(assumed)* | ? | Medium *(assumed)* |
| 25 | `bodyLargeEmphasized` | **16** | **24** | **0.15** | **Medium** |
| 26 | `bodyMediumEmphasized` | 14 *(assumed)* | 20 *(assumed)* | ? | Medium *(assumed)* |
| 27 | `bodySmallEmphasized` | 12 *(assumed)* | 16 *(assumed)* | ? | Medium *(assumed)* |
| 28 | `labelLargeEmphasized` | **14** | **20** | **0.1** | **Bold** |
| 29 | `labelMediumEmphasized` | 12 *(assumed)* | 16 *(assumed)* | ? | Bold? *(assumed)* |
| 30 | `labelSmallEmphasized` | 11 *(assumed)* | 16 *(assumed)* | ? | Bold? *(assumed)* |

Bold rows 16/19/22/25/28 are **verbatim from `TypeScaleTokens.kt`**. Rows marked *(assumed)* are
**UNVERIFIED** — `TypeScaleTokens.kt` holds 82 constants and only the Large group was read. Do not
quote the assumed values as API; read them from the token file if they matter.

### 1.4 What "emphasized" actually changes

- **Size and line height are identical** to the non-emphasized counterpart in all five verified rows.
- **Weight steps up**: `WeightMedium` for display/headline/title/body, `WeightBold` for
  `labelLargeEmphasized`.
- **Tracking is not always identical**, despite a common claim to the contrary. Two of five verified
  rows differ from baseline: `displayLarge` -0.25 → `displayLargeEmphasized` 0; `bodyLarge` 0.5 →
  `bodyLargeEmphasized` 0.15. Treat tracking as per-token, not inherited.

So: **"emphasized" is primarily a weight-axis shift, not a second size ramp.** That is exactly why a
variable font matters — the emphasized set is free if the face has a `wght` axis, and looks like
synthetic fake-bold if it does not.

### 1.5 When to use emphasized vs baseline

| Use emphasized for | Use baseline for |
| --- | --- |
| The one headline that owns the screen (usually inside `LargeFlexibleTopAppBar` / `MediumFlexibleTopAppBar`) | All other headings |
| A selected item that must read as selected without a color change | Unselected items |
| The primary action's label when the button is the hero | Every other button label |
| A single key statistic or numeric callout | Supporting text, captions, list content |
| Section headers in an editorial layout with only 2-3 sections | Section headers in a long settings list |

Rules:
1. **Emphasized is the system-sanctioned way to add weight.** Prefer
   `MaterialTheme.typography.titleLargeEmphasized` over
   `MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)`. Emphasis stays tokenized
   and themeable.
2. **Emphasis is relational.** If every heading is emphasized, nothing is. Budget it like a hero
   moment — one or two per screen.
3. **Never emphasize body copy.** Medium-weight paragraphs read as heavy and hurt long-form legibility.
4. Structural home for hero type: the **flexible app bars**. `MediumFlexibleTopAppBar` "displays a
   larger headline, collapsing into a small app bar on scroll"; `LargeFlexibleTopAppBar` "emphasizes
   the headline of the page". Both support larger title text, subtitles, left/center alignment and
   text wrapping. That gives you an editorial moment that costs no sustained screen real estate.

---

## 2. `Typography` constructors

Source: `androidx-main` `compose/material3/.../Typography.kt`.

### 2.1 Primary constructor — 30 params

```kotlin
constructor(
    val displayLarge: TextStyle = typographyTokens.DisplayLarge,
    val displayMedium: TextStyle = typographyTokens.DisplayMedium,
    val displaySmall: TextStyle = typographyTokens.DisplaySmall,
    val headlineLarge: TextStyle = typographyTokens.HeadlineLarge,
    val headlineMedium: TextStyle = typographyTokens.HeadlineMedium,
    val headlineSmall: TextStyle = typographyTokens.HeadlineSmall,
    val titleLarge: TextStyle = typographyTokens.TitleLarge,
    val titleMedium: TextStyle = typographyTokens.TitleMedium,
    val titleSmall: TextStyle = typographyTokens.TitleSmall,
    val bodyLarge: TextStyle = typographyTokens.BodyLarge,
    val bodyMedium: TextStyle = typographyTokens.BodyMedium,
    val bodySmall: TextStyle = typographyTokens.BodySmall,
    val labelLarge: TextStyle = typographyTokens.LabelLarge,
    val labelMedium: TextStyle = typographyTokens.LabelMedium,
    val labelSmall: TextStyle = typographyTokens.LabelSmall,
    displayLargeEmphasized: TextStyle = typographyTokens.DisplayLargeEmphasized,
    displayMediumEmphasized: TextStyle = typographyTokens.DisplayMediumEmphasized,
    displaySmallEmphasized: TextStyle = typographyTokens.DisplaySmallEmphasized,
    headlineLargeEmphasized: TextStyle = typographyTokens.HeadlineLargeEmphasized,
    headlineMediumEmphasized: TextStyle = typographyTokens.HeadlineMediumEmphasized,
    headlineSmallEmphasized: TextStyle = typographyTokens.HeadlineSmallEmphasized,
    titleLargeEmphasized: TextStyle = typographyTokens.TitleLargeEmphasized,
    titleMediumEmphasized: TextStyle = typographyTokens.TitleMediumEmphasized,
    titleSmallEmphasized: TextStyle = typographyTokens.TitleSmallEmphasized,
    bodyLargeEmphasized: TextStyle = typographyTokens.BodyLargeEmphasized,
    bodyMediumEmphasized: TextStyle = typographyTokens.BodyMediumEmphasized,
    bodySmallEmphasized: TextStyle = typographyTokens.BodySmallEmphasized,
    labelLargeEmphasized: TextStyle = typographyTokens.LabelLargeEmphasized,
    labelMediumEmphasized: TextStyle = typographyTokens.LabelMediumEmphasized,
    labelSmallEmphasized: TextStyle = typographyTokens.LabelSmallEmphasized,
)
```

Only the 15 baseline params are declared `val` in the primary constructor; the emphasized ones are
exposed as properties separately. All 30 are settable by name.

### 2.2 Font-family constructor — added 1.5.0-alpha16, extended in alpha19

```kotlin
constructor(
    fontFamily: FontFamily,
    displayLarge: TextStyle? = null,
    displayMedium: TextStyle? = null,
    displaySmall: TextStyle? = null,
    headlineLarge: TextStyle? = null,
    headlineMedium: TextStyle? = null,
    headlineSmall: TextStyle? = null,
    titleLarge: TextStyle? = null,
    titleMedium: TextStyle? = null,
    titleSmall: TextStyle? = null,
    bodyLarge: TextStyle? = null,
    bodyMedium: TextStyle? = null,
    bodySmall: TextStyle? = null,
    labelLarge: TextStyle? = null,
    labelMedium: TextStyle? = null,
    labelSmall: TextStyle? = null,
    displayLargeEmphasized: TextStyle? = null,
    displayMediumEmphasized: TextStyle? = null,
    displaySmallEmphasized: TextStyle? = null,
    headlineLargeEmphasized: TextStyle? = null,
    headlineMediumEmphasized: TextStyle? = null,
    headlineSmallEmphasized: TextStyle? = null,
    titleLargeEmphasized: TextStyle? = null,
    titleMediumEmphasized: TextStyle? = null,
    titleSmallEmphasized: TextStyle? = null,
    bodyLargeEmphasized: TextStyle? = null,
    bodyMediumEmphasized: TextStyle? = null,
    bodySmallEmphasized: TextStyle? = null,
    labelLargeEmphasized: TextStyle? = null,
    labelMediumEmphasized: TextStyle? = null,
    labelSmallEmphasized: TextStyle? = null,
)
```

Release-note wording (alpha19): *"Typography now supports default font family merged with provided
text styles."* Pass `fontFamily` plus only the styles you want to override; the family is applied to
all 30 slots.

```kotlin
MaterialExpressiveTheme(
    typography = Typography(fontFamily = GoogleSansFlex)
) { /* … */ }
```

> **Parameter name warning.** The parameter is **`fontFamily`**, per the source signature above. It is
> sometimes referred to colloquially as "the `defaultFontFamily` overload" (that was the *Material 2*
> `Typography(defaultFontFamily = …)` parameter name). **`defaultFontFamily` does not exist on
> material3 `Typography`** — writing it will not compile. Use `fontFamily`.

### 2.3 Legacy constructor

A 15-param constructor (baseline styles only, no emphasized) still exists for binary compatibility.
If you write `Typography(displayLarge = …, …, labelSmall = …)` with all 15 named, you get the primary
constructor with emphasized styles defaulted — which is what you want.

### 2.4 Which constructor to use

| Situation | Constructor |
| --- | --- |
| One font family, stock M3 metrics | `Typography(fontFamily = family)` — one line, all 30 slots |
| One family, a few tweaks | `Typography(fontFamily = family, displayLarge = …)` |
| Brand face for display/headline/title, text face for body/label | Full 15-param form (see vivi-music `getTypography` in §5) |
| Non-default sizes/weights across the board | Full form with explicit `TextStyle`s |
| On material3 < 1.5.0-alpha16 | Full form only; the `fontFamily` constructor does not exist yet |

The "copy every slot from `MaterialTheme.typography`" idiom (Med, §6) predates the `fontFamily`
constructor and is what you must write on older material3. On alpha16+, replace it with
`Typography(fontFamily = …)`.

---

## 3. Variable fonts

### 3.1 Why they matter for Expressive

Expressive wants weight, width and roundness variation across the type scale, plus emphasized
variants of all 15 styles. With static fonts that means shipping 6+ font files and still having gaps.
With one variable font file you instantiate as many named `Font` entries as you need, each pinned to
different axis values.

Google's framing: variable fonts allow "dynamic, customizable typography" with "adjustable axes,
including weight and width", and weight/width can be animated as *feedback* — a label thickening on
press, the typographic analogue of a shape morph. (Because weight/width change glyph bounds, treat
animating them as **spatial** motion, i.e. a spring that may overshoot. That classification is
inference, **UNVERIFIED** as published guidance.)

### 3.2 The API

```kotlin
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight

@OptIn(ExperimentalTextApi::class)
val Family = FontFamily(
    Font(
        resId = R.font.google_sans_flex,
        weight = FontWeight.Normal,          // what Compose matches against
        style = FontStyle.Normal,
        variationSettings = FontVariation.Settings(
            FontVariation.weight(400),       // what the renderer actually applies
            FontVariation.width(100f),
            FontVariation.Setting("ROND", 100f),
        ),
    ),
    // one Font(...) per weight the type scale asks for
)
```

Key point: `weight = FontWeight.Normal` is the **lookup key** Compose uses to pick an entry from the
family. `FontVariation.weight(400)` is the **axis value** applied to the variable font. They are
independent — you must set both, and they should agree, or `FontWeight.Bold` text will select an
entry whose `wght` axis is 400.

`variationSettings` on `Font(resId, …)` requires `@OptIn(ExperimentalTextApi::class)` in the versions
these repos use. Variable-font `variationSettings` require **API 26 (O)+**; LastChat guards it
explicitly (§7).

### 3.3 `FontVariation` helpers vs raw settings

| Helper | Axis | Notes |
| --- | --- | --- |
| `FontVariation.weight(Int)` | `wght` | Registered axis. Integer, typically 100-1000. |
| `FontVariation.width(Float)` | `wdth` | Registered axis. Percentage; 100f = normal. |
| `FontVariation.slant(Float)` | `slnt` | Registered axis. Degrees, typically 0 to -10 (negative = forward lean). |
| `FontVariation.italic(Float)` | `ital` | Registered axis (0/1). |
| `FontVariation.opticalSizing(TextUnit)` | `opsz` | Registered axis. |
| `FontVariation.grade(Int)` | `GRAD` | Also expressible as `Setting("GRAD", …)`. |
| `FontVariation.Setting(tag: String, value: Float)` | any | The escape hatch for custom axes such as `ROND`, `XTRA`, `YOPQ`. |

Any four-character axis tag the font exposes works through `FontVariation.Setting`. Axis values
outside the font's declared range are clamped or ignored — nothing errors, the text just looks wrong.

### 3.4 Google Sans Flex axes

Best in-repo documentation, from
`/root/work/repos/LastChat/app/src/main/java/me/rerere/rikkahub/ui/theme/Type.kt`:

```kotlin
/**
 * Material 3 Expressive Typography using Google Sans Flex variable font.
 *
 * Google Sans Flex axes:
 * - wght (weight): 100-1000, standard weight control
 * - wdth (width): 75-125, condensed to expanded
 * - ROND (roundness): 0-100, sharp to rounded letterforms
 * - GRAD (grade): -50 to 150, adjusts visual weight without changing size
 * - slnt (slant): -10 to 0, italic angle
 * - opsz (optical size): auto-adjusts based on font size
 *
 * M3 Expressive uses:
 * - High roundness (ROND=100) for friendly, approachable feel
 * - Wider width for display text hierarchy
 * - Varied weights for emphasis
 */
```

| Axis | Range | What it does | Expressive usage |
| --- | --- | --- | --- |
| `wght` | 100-1000 | Stroke weight | 400 body, 500-600 emphasized, 900 hero |
| `wdth` | 75-125 | Condensed → expanded | 100 default; 110-112.5 for display/hero |
| `ROND` | 0-100 | Sharp → rounded letterforms | **100 is the M3 Expressive signature**; 0 = "normal" mode |
| `GRAD` | -50 to 150 | Visual weight without changing glyph bounds | Small optical corrections; safe to animate (no reflow) |
| `slnt` | -10 to 0 | Italic angle | One-off editorial faces |
| `opsz` | auto | Optical size | Usually leave to the renderer |

`ROND` is the axis that makes an app read as "M3 Expressive" rather than "M3". LastChat ships two
whole families that differ only by `ROND` (100 vs 0) as its expressive/normal toggle.

Roboto Flex exposes a comparable set (`wght`, `wdth`, `slnt`, `opsz`, `GRAD`, `XTRA`, `YOPQ`, …).
Axis lists beyond the LastChat comment above are **UNVERIFIED** — check the font's own axis table.

### 3.5 OpenType features are separate

`fontFeatureSettings` is a `TextStyle` property, not a `FontVariation`:

```kotlin
TYPOGRAPHY.displayLarge.copy(
    fontFamily = googleFlex600,
    fontFeatureSettings = "ss02, dlig"   // stylistic set 2 + discretionary ligatures
)
```

Tomato turns on `ss02, dlig` across its whole scale for Google Sans Flex's alternate glyph forms.

---

## 4. Complete `Type.kt` — Tomato (one variable font, two instances, Compose Multiplatform)

Source: `/root/work/repos/Tomato/shared/src/commonMain/kotlin/org/nsh07/pomodoro/ui/theme/Type.kt`
(complete file, 160 lines)

Approach: instantiate the same font file twice (400 and 600-with-ROND), map every slot with `.copy()`
off a stock `Typography()`, and expose a separate hero face through a `staticCompositionLocalOf`.
Uses `org.jetbrains.compose.resources.Font` (multiplatform resources), not `androidx…Font(resId)`.

```kotlin
package org.nsh07.pomodoro.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import org.jetbrains.compose.resources.Font
import tomato.shared.generated.resources.Res
import tomato.shared.generated.resources.google_sans_flex

val TYPOGRAPHY = Typography()

data class AppFonts(
    val topBarTitle: FontFamily,
    val annotatedString: FontFamily
)

@Composable
fun typography(): Typography {
    val googleFlex400 = FontFamily(
        Font(
            Res.font.google_sans_flex,
            FontWeight.Normal,
            variationSettings = FontVariation.Settings(FontVariation.weight(400))
        )
    )

    val googleFlex600 = FontFamily(
        Font(
            Res.font.google_sans_flex,
            FontWeight.Bold,
            variationSettings = FontVariation.Settings(
                FontVariation.weight(600),
                FontVariation.Setting("ROND", 100f)
            )
        )
    )

    return remember {
        Typography(
            displayLarge = TYPOGRAPHY.displayLarge.copy(
                fontFamily = googleFlex600,
                fontFeatureSettings = "ss02, dlig"
            ),
            displayMedium = TYPOGRAPHY.displayMedium.copy(
                fontFamily = googleFlex600,
                fontFeatureSettings = "ss02, dlig"
            ),
            displaySmall = TYPOGRAPHY.displaySmall.copy(
                fontFamily = googleFlex600,
                fontFeatureSettings = "ss02, dlig"
            ),
            headlineLarge = TYPOGRAPHY.headlineLarge.copy(
                fontFamily = googleFlex600,
                fontFeatureSettings = "ss02, dlig"
            ),
            headlineMedium = TYPOGRAPHY.headlineMedium.copy(
                fontFamily = googleFlex600,
                fontFeatureSettings = "ss02, dlig"
            ),
            headlineSmall = TYPOGRAPHY.headlineSmall.copy(
                fontFamily = googleFlex600,
                fontFeatureSettings = "ss02, dlig"
            ),
            titleLarge = TYPOGRAPHY.titleLarge.copy(
                fontFamily = googleFlex400,
                fontFeatureSettings = "ss02, dlig"
            ),
            titleMedium = TYPOGRAPHY.titleMedium.copy(
                fontFamily = googleFlex600,
                fontFeatureSettings = "ss02, dlig"
            ),
            titleSmall = TYPOGRAPHY.titleSmall.copy(
                fontFamily = googleFlex600,
                fontFeatureSettings = "ss02, dlig"
            ),
            bodyLarge = TYPOGRAPHY.bodyLarge.copy(
                fontFamily = googleFlex600,
                fontFeatureSettings = "ss02, dlig"
            ),
            bodyMedium = TYPOGRAPHY.bodyMedium.copy(
                fontFamily = googleFlex400,
                fontFeatureSettings = "ss02, dlig"
            ),
            bodySmall = TYPOGRAPHY.bodySmall.copy(
                fontFamily = googleFlex400,
                fontFeatureSettings = "ss02, dlig"
            ),
            labelLarge = TYPOGRAPHY.labelLarge.copy(
                fontFamily = googleFlex600,
                fontFeatureSettings = "ss02, dlig"
            ),
            labelMedium = TYPOGRAPHY.labelMedium.copy(
                fontFamily = googleFlex600,
                fontFeatureSettings = "ss02, dlig"
            ),
            labelSmall = TYPOGRAPHY.labelSmall.copy(
                fontFamily = googleFlex600,
                fontFeatureSettings = "ss02, dlig"
            )
        )
    }
}

@Composable
fun getAppFonts(): AppFonts {
    val robotoFlexTopBar = FontFamily(
        Font(
            Res.font.google_sans_flex,
            variationSettings = FontVariation.Settings(
                FontVariation.weight(900),
                FontVariation.width(112.5f),
                FontVariation.Setting("ROND", 35f)
            )
        )
    )

    val annotatedStringFontFamily = FontFamily(
        Font(
            Res.font.google_sans_flex,
            FontWeight.Normal,
            variationSettings = FontVariation.Settings(FontVariation.weight(400))
        ),
        Font(
            Res.font.google_sans_flex,
            FontWeight.Bold,
            variationSettings = FontVariation.Settings(
                FontVariation.weight(600),
                FontVariation.Setting("ROND", 100f)
            )
        ) // Used for <b> tags
    )

    return AppFonts(
        topBarTitle = robotoFlexTopBar,
        annotatedString = annotatedStringFontFamily
    )
}

val LocalAppFonts = staticCompositionLocalOf<AppFonts> { error("AppFonts not provided") }
```

What to steal:
- **A dedicated hero instance.** `wght 900 / wdth 112.5 / ROND 35` — heavier, wider and *sharper*
  than the body face — used only for `LargeFlexibleTopAppBar` titles, consumed as
  `fontFamily = LocalAppFonts.current.topBarTitle`. The `ROND` drop is deliberate: the hero face is
  crisper, not rounder.
- **A two-entry family for `AnnotatedString`.** Normal + Bold entries so `<b>` tags in rich text
  resolve to a real 600-weight instance instead of synthetic bold.
- Providing `LocalAppFonts` **outside** the theme, so raw `FontFamily` values are reachable by callers
  that need them directly.
- `fontFeatureSettings = "ss02, dlig"` applied uniformly.

Caveat: `remember { }` in `typography()` has **no keys** — correct only because both families are
constructed from constants. If your axis values come from state, key the `remember`.

---

## 5. `Type.kt` — vivi-music (runtime user-selectable font family)

Source: `/root/work/repos/vivi-music/app/src/main/kotlin/com/music/vivi/ui/theme/Type.kt`
(complete file)

Four selectable families, each a 3-instance `FontFamily` (Normal/Medium/Bold), plus a `getTypography`
factory that splits **brand** vs **plain** font per role.

```kotlin
package com.music.vivi.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

@OptIn(ExperimentalTextApi::class)
val GoogleSansFontFamily = FontFamily(
    Font(
        resId = com.music.vivi.R.font.google_sans_flex,
        weight = FontWeight.Normal,
        variationSettings = FontVariation.Settings(
            FontVariation.weight(400),
            FontVariation.width(100f),
            FontVariation.Setting("ROND", 100f)
        )
    ),
    Font(
        resId = com.music.vivi.R.font.google_sans_flex,
        weight = FontWeight.Medium,
        variationSettings = FontVariation.Settings(
            FontVariation.weight(500),
            FontVariation.width(100f),
            FontVariation.Setting("ROND", 100f)
        )
    ),
    Font(
        resId = com.music.vivi.R.font.google_sans_flex,
        weight = FontWeight.Bold,
        variationSettings = FontVariation.Settings(
            FontVariation.weight(700),
            FontVariation.width(100f),
            FontVariation.Setting("ROND", 100f)
        )
    )
)

@OptIn(ExperimentalTextApi::class)
val SansFlexFontFamily = FontFamily(
    Font(
        resId = com.music.vivi.R.font.sans_flex,
        weight = FontWeight.Normal,
        variationSettings = FontVariation.Settings(
            FontVariation.weight(400),
            FontVariation.width(100f),
            FontVariation.Setting("ROND", 100f)
        )
    ),
    Font(
        resId = com.music.vivi.R.font.sans_flex,
        weight = FontWeight.Medium,
        variationSettings = FontVariation.Settings(
            FontVariation.weight(500),
            FontVariation.width(100f),
            FontVariation.Setting("ROND", 100f)
        )
    ),
    Font(
        resId = com.music.vivi.R.font.sans_flex,
        weight = FontWeight.Bold,
        variationSettings = FontVariation.Settings(
            FontVariation.weight(700),
            FontVariation.width(100f),
            FontVariation.Setting("ROND", 100f)
        )
    )
)

@OptIn(ExperimentalTextApi::class)
val OutfitFontFamily = FontFamily(
    Font(
        resId = com.music.vivi.R.font.outfit,
        weight = FontWeight.Normal,
        variationSettings = FontVariation.Settings(FontVariation.weight(400))
    ),
    Font(
        resId = com.music.vivi.R.font.outfit,
        weight = FontWeight.Medium,
        variationSettings = FontVariation.Settings(FontVariation.weight(500))
    ),
    Font(
        resId = com.music.vivi.R.font.outfit,
        weight = FontWeight.Bold,
        variationSettings = FontVariation.Settings(FontVariation.weight(700))
    )
)

@OptIn(ExperimentalTextApi::class)
val PlusJakartaSansFontFamily = FontFamily(
    Font(
        resId = com.music.vivi.R.font.plus_jakarta_sans,
        weight = FontWeight.Normal,
        variationSettings = FontVariation.Settings(FontVariation.weight(400))
    ),
    Font(
        resId = com.music.vivi.R.font.plus_jakarta_sans,
        weight = FontWeight.Medium,
        variationSettings = FontVariation.Settings(FontVariation.weight(500))
    ),
    Font(
        resId = com.music.vivi.R.font.plus_jakarta_sans,
        weight = FontWeight.Bold,
        variationSettings = FontVariation.Settings(FontVariation.weight(700))
    )
)

// Define M3 Expressive Typography based on Material Design guidelines
// https://m3.material.io/styles/typography/type-scale-tokens
@OptIn(ExperimentalTextApi::class)
fun getTypography(brandFont: FontFamily, plainFont: FontFamily = FontFamily.Default): Typography = Typography(
    displayLarge = TextStyle(
        fontFamily = brandFont, fontWeight = FontWeight.Normal,
        fontSize = 57.sp, lineHeight = 64.sp, letterSpacing = (-0.25).sp
    ),
    displayMedium = TextStyle(
        fontFamily = brandFont, fontWeight = FontWeight.Normal,
        fontSize = 45.sp, lineHeight = 52.sp, letterSpacing = 0.sp
    ),
    displaySmall = TextStyle(
        fontFamily = brandFont, fontWeight = FontWeight.Normal,
        fontSize = 36.sp, lineHeight = 44.sp, letterSpacing = 0.sp
    ),
    headlineLarge = TextStyle(
        fontFamily = brandFont, fontWeight = FontWeight.Normal,
        fontSize = 32.sp, lineHeight = 40.sp, letterSpacing = 0.sp
    ),
    headlineMedium = TextStyle(
        fontFamily = brandFont, fontWeight = FontWeight.Normal,
        fontSize = 28.sp, lineHeight = 36.sp, letterSpacing = 0.sp
    ),
    headlineSmall = TextStyle(
        fontFamily = brandFont, fontWeight = FontWeight.Normal,
        fontSize = 24.sp, lineHeight = 32.sp, letterSpacing = 0.sp
    ),
    titleLarge = TextStyle(
        fontFamily = brandFont,
        fontWeight = FontWeight.Normal, // M3 uses Normal, M2 used Medium
        fontSize = 22.sp, lineHeight = 28.sp, letterSpacing = 0.sp
    ),
    titleMedium = TextStyle(
        fontFamily = brandFont, fontWeight = FontWeight.Medium,
        fontSize = 16.sp, lineHeight = 24.sp, letterSpacing = 0.15.sp
    ),
    titleSmall = TextStyle(
        fontFamily = brandFont, fontWeight = FontWeight.Medium,
        fontSize = 14.sp, lineHeight = 20.sp, letterSpacing = 0.1.sp
    ),
    bodyLarge = TextStyle(
        fontFamily = plainFont, fontWeight = FontWeight.Normal,
        fontSize = 16.sp, lineHeight = 24.sp,
        letterSpacing = 0.5.sp // M3 uses 0.5, M2 used 0.15
    ),
    bodyMedium = TextStyle(
        fontFamily = plainFont, fontWeight = FontWeight.Normal,
        fontSize = 14.sp, lineHeight = 20.sp, letterSpacing = 0.25.sp
    ),
    bodySmall = TextStyle(
        fontFamily = plainFont, fontWeight = FontWeight.Normal,
        fontSize = 12.sp, lineHeight = 16.sp, letterSpacing = 0.4.sp
    ),
    labelLarge = TextStyle(
        fontFamily = plainFont, fontWeight = FontWeight.Medium,
        fontSize = 14.sp, lineHeight = 20.sp, letterSpacing = 0.1.sp
    ),
    labelMedium = TextStyle(
        fontFamily = plainFont, fontWeight = FontWeight.Medium,
        fontSize = 12.sp, lineHeight = 16.sp, letterSpacing = 0.5.sp
    ),
    labelSmall = TextStyle(
        fontFamily = plainFont, fontWeight = FontWeight.Medium,
        fontSize = 11.sp, lineHeight = 16.sp, letterSpacing = 0.5.sp
    )
)

val AppTypography = getTypography(FontFamily.Default)
```

The preference enum
(`/root/work/repos/vivi-music/app/src/main/kotlin/com/music/vivi/constants/PreferenceKeys.kt`):

```kotlin
enum class AppFont(val value: String) {
    SYSTEM("system"),
    GOOGLE_SANS("google_sans"),
    SANS_FLEX("sans_flex"),
    OUTFIT("outfit"),
    PLUS_JAKARTA_SANS("plus_jakarta_sans");

    companion object {
        fun fromValue(value: String): AppFont = entries.find { it.value == value } ?: SYSTEM
    }
}
```

Runtime switching, inside the theme composable
(`/root/work/repos/vivi-music/app/src/main/kotlin/com/music/vivi/ui/theme/Theme.kt`):

```kotlin
val selectedFontValue by rememberPreference(SelectedFontKey, AppFont.SYSTEM.value)

val brandFont = remember(selectedFontValue) {
    when (AppFont.fromValue(selectedFontValue)) {
        AppFont.SYSTEM -> FontFamily.Default
        AppFont.GOOGLE_SANS -> GoogleSansFontFamily
        AppFont.SANS_FLEX -> SansFlexFontFamily
        AppFont.OUTFIT -> OutfitFontFamily
        AppFont.PLUS_JAKARTA_SANS -> PlusJakartaSansFontFamily
    }
}

val typography = remember(brandFont) {
    getTypography(brandFont = brandFont, plainFont = brandFont)
}

MaterialExpressiveTheme(
    colorScheme = colorScheme,
    typography = typography,
    motionScheme = MotionScheme.expressive(),
    content = content
)
```

The pattern for runtime font switching:
1. Declare each `FontFamily` as a **top-level `val`** — construction is not free, and top-level vals
   are created once per process.
2. Map preference → family inside `remember(preferenceValue)`.
3. Build the `Typography` inside `remember(family)`.
4. Pass it to the theme. Every `Text` in the app re-lays out on change; that is expected.

Note `getTypography(brandFont = brandFont, plainFont = brandFont)` — vivi-music exposes a brand/plain
split but then passes the same family for both. Keep the split in the signature; it is the correct
shape for a design system even when a single face is used.

---

## 6. `Type.kt` / theme typography — Med (`GoogleSansFlex`, copy-every-slot)

Source: `/root/work/repos/Med/app/src/main/kotlin/com/fedeveloper95/med/ui/theme/Theme.kt`

One instance, one axis set, applied by copying all 15 stock styles. This is the minimal correct form
on material3 versions without the `Typography(fontFamily = …)` constructor.

```kotlin
@androidx.compose.ui.text.ExperimentalTextApi
val GoogleSansFlex = FontFamily(
    Font(
        resId = R.font.sans_flex,
        weight = FontWeight.Normal,
        style = FontStyle.Normal,
        variationSettings = FontVariation.Settings(
            FontVariation.weight(400),
            FontVariation.width(100f),
            FontVariation.Setting("ROND", 100f)
        )
    )
)

MaterialTheme(
    colorScheme = colorScheme,
    typography = Typography(
        displayLarge = MaterialTheme.typography.displayLarge.copy(fontFamily = GoogleSansFlex),
        displayMedium = MaterialTheme.typography.displayMedium.copy(fontFamily = GoogleSansFlex),
        displaySmall = MaterialTheme.typography.displaySmall.copy(fontFamily = GoogleSansFlex),
        headlineLarge = MaterialTheme.typography.headlineLarge.copy(fontFamily = GoogleSansFlex),
        headlineMedium = MaterialTheme.typography.headlineMedium.copy(fontFamily = GoogleSansFlex),
        headlineSmall = MaterialTheme.typography.headlineSmall.copy(fontFamily = GoogleSansFlex),
        titleLarge = MaterialTheme.typography.titleLarge.copy(fontFamily = GoogleSansFlex),
        titleMedium = MaterialTheme.typography.titleMedium.copy(fontFamily = GoogleSansFlex),
        titleSmall = MaterialTheme.typography.titleSmall.copy(fontFamily = GoogleSansFlex),
        bodyLarge = MaterialTheme.typography.bodyLarge.copy(fontFamily = GoogleSansFlex),
        bodyMedium = MaterialTheme.typography.bodyMedium.copy(fontFamily = GoogleSansFlex),
        bodySmall = MaterialTheme.typography.bodySmall.copy(fontFamily = GoogleSansFlex),
        labelLarge = MaterialTheme.typography.labelLarge.copy(fontFamily = GoogleSansFlex),
        labelMedium = MaterialTheme.typography.labelMedium.copy(fontFamily = GoogleSansFlex),
        labelSmall = MaterialTheme.typography.labelSmall.copy(fontFamily = GoogleSansFlex)
    ),
    content = content
)
```

**Two problems with this exact code, worth knowing before copying it:**
1. **Only one `Font` entry exists.** Every style resolving to `FontWeight.Medium` (titleMedium,
   titleSmall, all three labels) has no matching instance, so the platform synthesises weight. See §8.1.
2. It only sets the 15 baseline slots. The 15 emphasized slots keep the **default** font family. If
   you use any `*Emphasized` style, set those too — or use `Typography(fontFamily = …)`, which covers
   all 30 in one argument.

### Wear variant — Med

Source: `/root/work/repos/Med/wear/src/main/kotlin/com/fedeveloper95/med/ui/theme/Type.kt`
(complete file, 41 lines). Same font, against `androidx.wear.compose.material3.Typography`.

```kotlin
package com.fedeveloper95.med.ui.theme

import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.wear.compose.material3.Typography
import com.fedeveloper95.med.R

@OptIn(ExperimentalTextApi::class)
val GoogleSansFlex = FontFamily(
    Font(
        resId = R.font.sans_flex,
        weight = FontWeight.Normal,
        style = FontStyle.Normal,
        variationSettings = FontVariation.Settings(
            FontVariation.weight(400),
            FontVariation.width(100f),
            FontVariation.Setting("ROND", 100f)
        )
    )
)

val Typography = Typography().let {
    it.copy(
        displayLarge = it.displayLarge.copy(fontFamily = GoogleSansFlex),
        displayMedium = it.displayMedium.copy(fontFamily = GoogleSansFlex),
        displaySmall = it.displaySmall.copy(fontFamily = GoogleSansFlex),
        titleLarge = it.titleLarge.copy(fontFamily = GoogleSansFlex),
        titleMedium = it.titleMedium.copy(fontFamily = GoogleSansFlex),
        titleSmall = it.titleSmall.copy(fontFamily = GoogleSansFlex),
        bodyLarge = it.bodyLarge.copy(fontFamily = GoogleSansFlex),
        bodyMedium = it.bodyMedium.copy(fontFamily = GoogleSansFlex),
        bodySmall = it.bodySmall.copy(fontFamily = GoogleSansFlex),
        labelLarge = it.labelLarge.copy(fontFamily = GoogleSansFlex),
        labelMedium = it.labelMedium.copy(fontFamily = GoogleSansFlex),
        labelSmall = it.labelSmall.copy(fontFamily = GoogleSansFlex)
    )
}
```

**Wear `Typography` has no `headline*` slots** — that is why they are absent, not an oversight. Wear
also adds surface-specific roles (Arc Text for surface titles, a "numerals" role with larger styles
for non-localized strings). Do not assume Wear roles exist on mobile or vice versa.

---

## 7. `Type.kt` — LastChat (the most complete `FontVariation` treatment)

Source: `/root/work/repos/LastChat/app/src/main/java/me/rerere/rikkahub/ui/theme/Type.kt`

Two parameterised composable builders (expressive vs normal), a non-composable 6-weight factory, two
shipped families, and a full type-scale factory with heavier weights than stock M3.

### 7.1 Parameterised composable builders

```kotlin
@file:OptIn(androidx.compose.ui.text.ExperimentalTextApi::class)

package me.rerere.rikkahub.ui.theme

import android.os.Build
import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import me.rerere.rikkahub.R

// Create font with specific variation settings for M3 Expressive mode
@Composable
fun rememberGoogleSansFlexExpressive(
    weight: FontWeight = FontWeight.Normal,
    width: Float = 100f,      // 75-125, 100 = normal
    roundness: Float = 100f,  // 0-100, 100 = fully rounded (M3E style)
    grade: Float = 0f,        // -50 to 150
): FontFamily {
    return remember(weight, width, roundness, grade) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            FontFamily(
                Font(
                    R.font.google_sans_flex,
                    weight = weight,
                    variationSettings = FontVariation.Settings(
                        FontVariation.weight(weight.weight),
                        FontVariation.width(width),
                        FontVariation.Setting("ROND", roundness),
                        FontVariation.Setting("GRAD", grade)
                    )
                )
            )
        } else {
            // Fallback for older Android versions
            FontFamily(Font(R.font.google_sans_flex, weight = weight))
        }
    }
}

// Create font for Normal mode (no roundness, standard settings)
@Composable
fun rememberGoogleSansFlexNormal(
    weight: FontWeight = FontWeight.Normal,
    width: Float = 100f,
    grade: Float = 0f,
): FontFamily {
    return remember(weight, width, grade) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            FontFamily(
                Font(
                    R.font.google_sans_flex,
                    weight = weight,
                    variationSettings = FontVariation.Settings(
                        FontVariation.weight(weight.weight),
                        FontVariation.width(width),
                        FontVariation.Setting("ROND", 0f), // No roundness
                        FontVariation.Setting("GRAD", grade)
                    )
                )
            )
        } else {
            FontFamily(Font(R.font.google_sans_flex, weight = weight))
        }
    }
}
```

Note `FontVariation.weight(weight.weight)` — deriving the axis value from the `FontWeight` lookup key
so the two can never disagree. Copy that idiom.

Note also the **API 26 guard**. `variationSettings` needs O+; below that the code falls back to a
plain `Font(resId, weight)`, accepting synthetic weights on ancient devices.

### 7.2 Non-composable 6-weight factory + the expressive/normal toggle

```kotlin
// Static font families for non-composable contexts
private fun createGoogleSansFlex(roundness: Float, wideForExpressive: Boolean = false): FontFamily {
    // Use wider width (110) for expressive display/headline text
    val displayWidth = if (wideForExpressive) 110f else 100f
    val normalWidth = 100f

    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        FontFamily(
            // Light
            Font(
                R.font.google_sans_flex,
                weight = FontWeight.Light,
                variationSettings = FontVariation.Settings(
                    FontVariation.weight(300),
                    FontVariation.width(normalWidth),
                    FontVariation.Setting("ROND", roundness)
                )
            ),
            // Normal
            Font(
                R.font.google_sans_flex,
                weight = FontWeight.Normal,
                variationSettings = FontVariation.Settings(
                    FontVariation.weight(400),
                    FontVariation.width(normalWidth),
                    FontVariation.Setting("ROND", roundness)
                )
            ),
            // Medium (500), SemiBold (600), Bold (700), ExtraBold (800)
            // follow the same shape
        )
    } else {
        // Fallback for older Android
        FontFamily(
            Font(R.font.google_sans_flex, FontWeight.Light),
            Font(R.font.google_sans_flex, FontWeight.Normal),
            Font(R.font.google_sans_flex, FontWeight.Medium),
            Font(R.font.google_sans_flex, FontWeight.SemiBold),
            Font(R.font.google_sans_flex, FontWeight.Bold),
            Font(R.font.google_sans_flex, FontWeight.ExtraBold)
        )
    }
}

// M3 Expressive font family - rounded, friendly, wider display text
val GoogleSansFlexExpressive = createGoogleSansFlex(roundness = 100f, wideForExpressive = true)

// Normal font family - standard, no roundness, normal width
val GoogleSansFlexNormal = createGoogleSansFlex(roundness = 0f, wideForExpressive = false)
```

**Six instances is the right number** — Light/Normal/Medium/SemiBold/Bold/ExtraBold covers every
weight the baseline and emphasized scales can ask for. This is the file to copy if you want zero
synthetic bolding.

(`displayWidth` is computed but unused in the excerpt available — the wider-for-display intent did not
make it into the per-instance settings. If you copy this, wire `displayWidth` into the heavier
instances or drop the parameter.)

### 7.3 LastChat's type scale — heavier than stock M3

```kotlin
/**
 * Creates M3 Expressive Typography with the specified font family.
 *
 * M3E Guidelines:
 * - Display: Bold, wider for hero text
 * - Headlines: SemiBold for section headers
 * - Titles: Medium weight for cards and items
 * - Body: Normal weight for content
 * - Labels: Medium weight for buttons and chips
 */
fun createTypography(fontFamily: FontFamily): Typography = Typography(
    displayLarge = TextStyle(
        fontFamily = fontFamily, fontWeight = FontWeight.Bold,
        fontSize = 57.sp, lineHeight = 64.sp, letterSpacing = (-0.25).sp
    ),
    displayMedium = TextStyle(
        fontFamily = fontFamily, fontWeight = FontWeight.Bold,
        fontSize = 45.sp, lineHeight = 52.sp, letterSpacing = 0.sp
    ),
    displaySmall = TextStyle(
        fontFamily = fontFamily, fontWeight = FontWeight.SemiBold,
        fontSize = 36.sp, lineHeight = 44.sp, letterSpacing = 0.sp
    ),
    headlineLarge = TextStyle(
        fontFamily = fontFamily, fontWeight = FontWeight.SemiBold,
        fontSize = 32.sp, lineHeight = 40.sp, letterSpacing = 0.sp
    ),
    headlineMedium = TextStyle(
        fontFamily = fontFamily, fontWeight = FontWeight.SemiBold,
        fontSize = 28.sp, lineHeight = 36.sp, letterSpacing = 0.sp
    ),
    headlineSmall = TextStyle(
        fontFamily = fontFamily, fontWeight = FontWeight.SemiBold,
        fontSize = 24.sp, lineHeight = 32.sp, letterSpacing = 0.sp
    ),
    titleLarge = TextStyle(
        fontFamily = fontFamily, fontWeight = FontWeight.SemiBold,
        fontSize = 22.sp, lineHeight = 28.sp, letterSpacing = 0.sp
    ),
    titleMedium = TextStyle(
        fontFamily = fontFamily, fontWeight = FontWeight.Medium,
        fontSize = 16.sp, lineHeight = 24.sp, letterSpacing = 0.15.sp
    ),
    titleSmall = TextStyle(
        fontFamily = fontFamily, fontWeight = FontWeight.Medium,
        fontSize = 14.sp, lineHeight = 20.sp, letterSpacing = 0.1.sp
    ),
    bodyLarge = TextStyle(
        fontFamily = fontFamily, fontWeight = FontWeight.Medium,
        fontSize = 16.sp, lineHeight = 24.sp, letterSpacing = 0.5.sp
    ),
    bodyMedium = TextStyle(
        fontFamily = fontFamily, fontWeight = FontWeight.Medium,
        fontSize = 14.sp, lineHeight = 20.sp, letterSpacing = 0.25.sp
    ),
    bodySmall = TextStyle(
        fontFamily = fontFamily, fontWeight = FontWeight.Medium,
        fontSize = 12.sp, lineHeight = 16.sp, letterSpacing = 0.4.sp
    ),
    labelLarge = TextStyle(
        fontFamily = fontFamily, fontWeight = FontWeight.Medium,
        fontSize = 14.sp, lineHeight = 20.sp, letterSpacing = 0.1.sp
    ),
    labelMedium = TextStyle(
        fontFamily = fontFamily, fontWeight = FontWeight.Medium,
        fontSize = 12.sp, lineHeight = 16.sp, letterSpacing = 0.5.sp
    ),
    labelSmall = TextStyle(
        fontFamily = fontFamily, fontWeight = FontWeight.Medium,
        fontSize = 11.sp, lineHeight = 16.sp, letterSpacing = 0.5.sp
    )
)

// Default Typography uses M3 Expressive (rounded)
val Typography = createTypography(GoogleSansFlexExpressive)

// Normal Typography (non-expressive)
val TypographyNormal = createTypography(GoogleSansFlexNormal)
```

Sizes and tracking are stock M3; **weights are pushed up one step** (display Bold instead of Normal,
headline/titleLarge SemiBold, body Medium). This is an *opinion*, not the spec — it approximates the
emphasized scale everywhere instead of reserving it. Do this only if you want the whole app to read
heavy; it costs you the emphasized/baseline contrast.

### 7.4 User-scalable type — LastChat's runtime typography

Source: same file, `rememberTypographyFromFontSettings`.

```kotlin
/**
 * Create Typography from FontSettings.
 * All non-code styles share the same app font configuration.
 */
@Composable
fun rememberTypographyFromFontSettings(fontSettings: FontSettings): Typography {
    val normalizedFontSettings = fontSettings.normalize()
    val appFontConfig = appFontConfigFromSettings(normalizedFontSettings)
    val appFontFamily = rememberAppFontFamily(normalizedFontSettings)

    return remember(appFontConfig, appFontFamily, normalizedFontSettings) {
        Typography(
            displayLarge = TextStyle(
                fontFamily = appFontFamily,
                fontWeight = FontWeight.Bold,
                fontSize = (57 * appFontConfig.fontSize).sp,
                lineHeight = (64 * appFontConfig.lineHeight).sp,
                letterSpacing = (-0.25 + appFontConfig.letterSpacing).sp
            ),
            displayMedium = TextStyle(
                fontFamily = appFontFamily,
                fontWeight = FontWeight.Bold,
                fontSize = (45 * appFontConfig.fontSize).sp,
                lineHeight = (52 * appFontConfig.lineHeight).sp,
                letterSpacing = (0 + appFontConfig.letterSpacing).sp
            ),
            // …all 15 slots follow the same
            //   size * scale / lineHeight * scale / tracking + delta shape
        )
    }
}
```

The scheme: **font size and line height are multiplied** by a user scale factor; **letter spacing is
added** to. That is correct — tracking is an absolute offset, scaling it distorts the face at large
sizes. Note the `remember` is keyed on all three inputs.

---

## 8. The advanced case — a slanted, graded display face

Source: `/root/work/repos/vivi-music/app/src/main/kotlin/com/music/vivi/WelcomeActivity.kt`

vivi-music's onboarding builds a one-off `FontFamily` exercising **five axes at once**
(`slnt`, `wdth`, `wght`, `GRAD`, `ROND`) for a 48sp thin header.

```kotlin
@OptIn(ExperimentalTextApi::class)
val GoogleSansFlex = FontFamily(
    Font(
        resId = com.music.vivi.R.font.google_sans_flex,
        weight = FontWeight.Normal,
        style = FontStyle.Normal,
        variationSettings = FontVariation.Settings(
            FontVariation.weight(400),
            FontVariation.width(100f),
            FontVariation.Setting("ROND", 100f)
        )
    )
)
```

```kotlin
val customWelcomeFontFamily = FontFamily(
    Font(
        resId = com.music.vivi.R.font.sans_flex,
        variationSettings = FontVariation.Settings(
            FontVariation.slant(-9f),
            FontVariation.width(111f),
            FontVariation.weight(333),
            FontVariation.Setting("GRAD", 100f),
            FontVariation.Setting("ROND", 100f)
        )
    )
)

val thinHeaderStyle = TextStyle(
    fontFamily = customWelcomeFontFamily,
    fontSize = 48.sp
)
```

Used as a two-line editorial pair — the thin slanted face against a bold upright one:

```kotlin
Column(modifier = Modifier.padding(bottom = 16.dp)) {
    Text(
        text = "Setting up",
        style = thinHeaderStyle,
        color = MaterialTheme.colorScheme.onBackground
    )
    Text(
        text = "ViviMusic…",
        fontFamily = GoogleSansFlex,
        fontWeight = FontWeight.Bold,
        fontSize = 48.sp,
        color = MaterialTheme.colorScheme.primary,
        lineHeight = 56.sp
    )
}
```

What this demonstrates:
- **Non-round axis values** (`slnt = -9f`, `wdth = 111f`, `wght = 333`) are legitimate. Variable fonts
  are continuous; there is no reason to snap to 100s.
- `GRAD = 100` thickens strokes *without* changing glyph advance widths — so the line does not reflow.
  That makes `GRAD` the safe axis to animate.
- The one-off family is declared **local to the screen**, not in the theme. Hero faces that appear in
  one place do not belong in `Typography`.
- The second `Text` sets `lineHeight = 56.sp` explicitly because it is not going through a type-scale
  style. At 48sp, default line height would collapse the two lines.

---

## 9. Gotchas

### 9.1 Missing variable-font instances → synthetic bolding

The failure: your family declares one `Font(weight = FontWeight.Normal, …)`. The type scale asks for
`FontWeight.Medium` (titleMedium, titleSmall, labelLarge/Medium/Small) and `FontWeight.Bold`
(anything emphasized). Compose finds no matching entry, falls back to the nearest, and the platform
**synthesises** the weight by smearing outlines. Result: mushy, slightly-wrong-shaped bold text that
does not match the design and looks worse at large sizes.

Diagnosis: render `Text("Ag", fontWeight = FontWeight.Medium)` next to `FontWeight.Normal`. If they
look identical, or the "bold" looks blurry/outlined rather than genuinely heavier, you have synthetic
weight.

Fix: declare one `Font` entry per weight the scale uses, with matching `weight =` and
`FontVariation.weight(…)`:

| Slots that need it | Weight |
| --- | --- |
| Body, display, headline, titleLarge (baseline) | `FontWeight.Normal` / 400 |
| titleMedium, titleSmall, all labels (baseline); all emphasized display/headline/title/body | `FontWeight.Medium` / 500 |
| `labelLargeEmphasized`; anything you push heavier | `FontWeight.Bold` / 700 |
| Hero display faces | 600-900 |

Minimum viable family = 3 entries (400/500/700), as vivi-music does. Zero-gap family = 6 entries
(300/400/500/600/700/800), as LastChat does.

### 9.2 Setting only the 15 baseline slots

`Typography(displayLarge = …, …, labelSmall = …)` leaves the 15 `*Emphasized` slots at their token
defaults — **default font family**. Any `MaterialTheme.typography.titleLargeEmphasized` then renders
in the platform face while everything around it renders in yours.

Fix: use `Typography(fontFamily = family, …)` (alpha16+), which covers all 30, or set the emphasized
slots explicitly.

### 9.3 Letter spacing at display sizes

- Tracking is an **absolute** `sp` offset, not a ratio. `letterSpacing = 0.5.sp` is generous at 12sp
  and invisible at 57sp.
- The M3 scale already accounts for this: tracking is **negative** at `displayLarge` (-0.25.sp),
  **zero** through display/headline/titleLarge, and **positive and growing** as sizes shrink
  (0.1 → 0.5.sp at label sizes).
- If you scale type for accessibility, **multiply size and line height, add to tracking** (LastChat,
  §7.4). Multiplying tracking blows apart display text.
- Never add positive tracking to display styles to "make it breathe". At 45-57sp, optical spacing
  wants to be tight or negative.
- Rounded/wide axis values (`ROND 100`, `wdth 110`) already add apparent spacing. If display text
  looks loose after switching to Google Sans Flex, reduce tracking further rather than reducing width.

### 9.4 Line height and hero type

- Every type-scale style carries `lineHeight`. When you write a raw `Text(fontSize = 48.sp)` **outside**
  the scale, you get the font's default leading, which is usually too tight for display sizes. Set
  `lineHeight` explicitly — vivi-music uses `48.sp / 56.sp` (≈1.17).
- The M3 ratio narrows as size grows: 57/64 ≈ 1.12 at displayLarge, 16/24 = 1.50 at bodyLarge. Follow
  that curve; do not apply a single multiplier across the scale.
- Multi-line hero headlines in `LargeFlexibleTopAppBar` wrap. Check the collapse animation with the
  longest real string, not "Title".
- If a hero line clips its ascenders/descenders, the cause is usually `lineHeight` < the font's
  natural line box, not padding. Consider `LineHeightStyle` trimming rather than adding padding.
- Emphasized styles keep the baseline line height (verified for all five Large tokens), so switching
  a headline from `headlineLarge` to `headlineLargeEmphasized` will not reflow the layout — only the
  weight changes. That is the whole point.

### 9.5 Other traps

| Trap | Fix |
| --- | --- |
| `Typography(defaultFontFamily = …)` | Does not exist in material3. The param is `fontFamily`. |
| `remember { }` with no keys around a family built from state | Key the `remember` on every axis value. |
| Constructing `FontFamily` inside a composable that recomposes often | Hoist to a top-level `val` or `remember`. |
| `variationSettings` without `@OptIn(ExperimentalTextApi::class)` | Add the opt-in, or set it module-wide in `build.gradle.kts`. |
| `variationSettings` on API < 26 | Silently ignored. Guard with `Build.VERSION.SDK_INT >= Build.VERSION_CODES.O` and fall back (LastChat, §7.1). |
| `weight =` and `FontVariation.weight()` disagreeing | Derive one from the other: `FontVariation.weight(weight.weight)`. |
| Hardcoding `fontSize`/`fontWeight` on `Text` throughout the app | Use `style = MaterialTheme.typography.…`; ad-hoc values are unthemeable and break user font scaling. |
| Using `.copy(fontWeight = FontWeight.Bold)` for emphasis | Use the `*Emphasized` style so emphasis stays tokenized. |

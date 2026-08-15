# Theme Recipes — complete, copy-pasteable M3 Expressive setups

Every recipe below is either verbatim from a shipping repo (cited) or assembled from verified pieces.
Companion files: `color.md`, `typography-and-variable-fonts.md`, `shape-scale.md`.

---

## 0. The API you are wiring up

```kotlin
@Composable
fun MaterialExpressiveTheme(
    colorScheme: ColorScheme? = null,
    motionScheme: MotionScheme? = null,
    shapes: Shapes? = null,
    typography: Typography? = null,
    content: @Composable () -> Unit,
)
```

KDoc first line, verbatim: *"Material Expressive Theming refers to the customization of your Material
Design app to better reflect your product's brand."*

**The trap:** the parameters are **nullable**, and `null` means *"use the Expressive default"* — it does
**not** mean *"inherit from the ambient theme"*, which is what `MaterialTheme`'s non-null defaults do.

| | `MaterialTheme` | `MaterialExpressiveTheme` |
| --- | --- | --- |
| Param nullability | non-null; defaults **inherit from the ambient theme** | **nullable**; `null` = "use the Expressive default" |
| Default color scheme | ambient / `lightColorScheme()` semantics | `expressiveLightColorScheme()` |
| Default motion scheme | `MotionScheme.standard()` | `MotionScheme.expressive()` |
| Default shapes | `Shapes()` baseline scale | Expressive shape defaults |
| Default typography | `Typography()` | Expressive typography incl. `*Emphasized` |
| Opt-in | none | `@ExperimentalMaterial3ExpressiveApi` on ≤ 1.4.x; promoted in 1.5.0-alpha18 |

Consequence: a **nested** `MaterialExpressiveTheme` must explicitly pass
`colorScheme = MaterialTheme.colorScheme` etc. to inherit — see Recipe 5.

> The precise default *values* substituted for each `null` argument are **UNVERIFIED** beyond
> `expressiveLightColorScheme()` (documented as "the default color configuration for
> `MaterialExpressiveTheme`") and `MotionScheme.expressive()` (documented intent). Pass arguments
> explicitly and this stops mattering.

### Gradle

```toml
# gradle/libs.versions.toml
[versions]
material3 = "1.5.0-alpha23"      # vivi-music; Med pins alpha21, LastChat alpha08
materialKolor = "4.1.1"

[libraries]
material3 = { module = "androidx.compose.material3:material3", version.ref = "material3" }
material3-adaptive-navigation-suite = { module = "androidx.compose.material3:material3-adaptive-navigation-suite", version.ref = "material3" }
materialKolor = { module = "com.materialkolor:material-kolor", version.ref = "materialKolor" }
```

Module-wide opt-in (vivi-music, `app/build.gradle.kts`) — preferable to annotating hundreds of call
sites:

```kotlin
kotlin {
    compilerOptions {
        freeCompilerArgs.add("-opt-in=androidx.compose.material3.ExperimentalMaterial3ExpressiveApi")
    }
}
```

LastChat's equivalent, same file:

```kotlin
compilerOptions.optIn.add("androidx.compose.material3.ExperimentalMaterial3Api")
compilerOptions.optIn.add("androidx.compose.material3.ExperimentalMaterial3ExpressiveApi")
compilerOptions.optIn.add("androidx.compose.material3.adaptive.ExperimentalMaterial3AdaptiveApi")
```

Alternative: BOM. Tomato uses `androidx.compose:compose-bom-alpha:2026.03.00` and declares
`androidx.compose.material3:material3` with **no version**. That alpha BOM is what unlocks
`SegmentedListItem`, `ListItemDefaults.segmentedShapes`, `veilOut`/`unveilIn` and
`shapes.extraLargeIncreased`.

---

## Recipe 1 — "Start here" AppTheme

**Use this when:** starting a new app, or replacing a theme wholesale. Covers system dynamic color,
dark mode, AMOLED, a custom seed accent, expressive motion, and a variable font. Assembled from
verified pieces across all four repos — this is the recommended default, not a verbatim excerpt.

```kotlin
package com.example.app.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.expressiveLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import com.materialkolor.PaletteStyle
import com.materialkolor.dynamiccolor.ColorSpec
import com.materialkolor.rememberDynamicColorScheme

/** Fallback seed when the user has not picked an accent and dynamic color is unavailable. */
val DefaultSeedColor = Color(0xFF6750A4)

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun AppTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    pureBlack: Boolean = false,
    seedColor: Color? = null,          // non-null => seeded palette wins over system dynamic color
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current

    val baseColorScheme: ColorScheme = when {
        // 1. User-chosen or image-derived seed
        seedColor != null -> rememberDynamicColorScheme(
            seedColor = seedColor,
            isDark = darkTheme,
            specVersion = ColorSpec.SpecVersion.SPEC_2025,
            style = if (seedColor.toArgb() == 0xFF000000.toInt())
                PaletteStyle.Monochrome else PaletteStyle.TonalSpot,
        )
        // 2. System wallpaper colors (Android 12+)
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        // 3. Static fallback. NOTE: there is no expressiveDarkColorScheme().
        darkTheme -> darkColorScheme()
        else -> expressiveLightColorScheme()
    }

    val colorScheme = remember(baseColorScheme, pureBlack, darkTheme) {
        if (darkTheme && pureBlack) baseColorScheme.pureBlack(true) else baseColorScheme
    }

    MaterialExpressiveTheme(
        colorScheme = colorScheme,
        typography = AppTypography,          // see typography-and-variable-fonts.md
        shapes = AppShapes,                  // see shape-scale.md; omit to take Expressive defaults
        motionScheme = MotionScheme.expressive(),
        content = content,
    )
}

/** AMOLED override. Surfaces only — see color.md §7 for why containers need call-site handling. */
fun ColorScheme.pureBlack(apply: Boolean) =
    if (apply) copy(surface = Color.Black, background = Color.Black) else this
```

Why each line is there:

| Line | Reason |
| --- | --- |
| `motionScheme = MotionScheme.expressive()` | **The parameter people forget.** Without it, expressive components fall back to standard springs and the app feels like baseline M3. |
| `specVersion = ColorSpec.SpecVersion.SPEC_2025` | The Expressive-era palette spec in material-kolor. Omit it and you get 2021 math. |
| `Build.VERSION.SDK_INT >= S` guard | `dynamic*ColorScheme` are API 31+; calling them lower crashes. |
| `expressiveLightColorScheme()` / `darkColorScheme()` | The asymmetry is real. `expressiveDarkColorScheme()` does not exist. |
| `remember(baseColorScheme, pureBlack, darkTheme)` | All three inputs must be keys or a stale scheme survives recomposition. |
| `PaletteStyle.Monochrome` for a black seed | A near-black seed has no usable hue; `TonalSpot` produces mud. |
| seed **before** system dynamic | An explicit user choice should beat the wallpaper. |

Priority order to keep in mind: **explicit seed > system dynamic > static fallback**, then AMOLED
applied on top of whichever won.

---

## Recipe 2 — vivi-music `Theme.kt` (verbatim, complete file)

**Use this when:** you need album-art / image-seeded color that re-tints the whole app, a user-selectable
font, and a hand-rolled AMOLED mode. This is the most complete single-file Expressive theme in the
corpus.

Source: `/root/work/repos/vivi-music/app/src/main/kotlin/com/music/vivi/ui/theme/Theme.kt`
(material3 `1.5.0-alpha23`, materialKolor `4.1.1`)

```kotlin
/**
 * vivimusic Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.music.vivi.ui.theme

import android.graphics.Bitmap
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.SaverScope
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.palette.graphics.Palette
import com.materialkolor.PaletteStyle
import com.materialkolor.dynamiccolor.ColorSpec
import com.materialkolor.rememberDynamicColorScheme
import com.materialkolor.score.Score

import androidx.compose.runtime.getValue
import com.music.vivi.constants.SelectedFontKey
import com.music.vivi.constants.AppFont
import com.music.vivi.utils.rememberPreference
import androidx.compose.ui.text.font.FontFamily

val DefaultThemeColor = Color(0xFFED5564)

@Composable
fun vivimusicTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    pureBlack: Boolean = false,
    themeColor: Color = DefaultThemeColor,
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
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


    // Determine if system dynamic colors should be used (Android S+ and default theme color)
    val useSystemDynamicColor = (themeColor == DefaultThemeColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S)

    // Select the appropriate color scheme generation method
    val baseColorScheme = if (useSystemDynamicColor) {
        // Use standard Material 3 dynamic color functions for system wallpaper colors
        if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
    } else {
        // Use materialKolor only when a specific seed color is provided
        rememberDynamicColorScheme(
            seedColor = themeColor, // themeColor is guaranteed non-default here
            isDark = darkTheme,
            specVersion = ColorSpec.SpecVersion.SPEC_2025,
            style = if (themeColor.toArgb() == 0xFF000000.toInt()) PaletteStyle.Monochrome else PaletteStyle.TonalSpot
        )
    }

    // Apply pureBlack modification if needed, similar to original logic
    val colorScheme = remember(baseColorScheme, pureBlack, darkTheme) {
        if (darkTheme && pureBlack) {
            baseColorScheme.pureBlack(true)
        } else {
            baseColorScheme
        }
    }

    MaterialExpressiveTheme(
        colorScheme = colorScheme,
        typography = typography, // Use the dynamically configured typography
        motionScheme = MotionScheme.expressive(),
        content = content
    )
}

fun Bitmap.extractThemeColor(): Color {
    val colorsToPopulation = Palette.from(this)
        .maximumColorCount(8)
        .generate()
        .swatches
        .associate { it.rgb to it.population }
    val rankedColors = Score.score(colorsToPopulation)
    return Color(rankedColors.first())
}

fun Bitmap.extractGradientColors(): List<Color> {
    val extractedColors = Palette.from(this)
        .maximumColorCount(64)
        .generate()
        .swatches
        .associate { it.rgb to it.population }

    val orderedColors = Score.score(extractedColors, 2, 0xff4285f4.toInt(), true)
        .sortedByDescending { Color(it).luminance() }

    return if (orderedColors.size >= 2)
        listOf(Color(orderedColors[0]), Color(orderedColors[1]))
    else
        listOf(Color(0xFF595959), Color(0xFF0D0D0D))
}

fun ColorScheme.pureBlack(apply: Boolean) =
    if (apply) copy(
        surface = Color.Black,
        background = Color.Black
    ) else this

val ColorSaver = object : Saver<Color, Int> {
    override fun restore(value: Int): Color = Color(value)
    override fun SaverScope.save(value: Color): Int = value.toArgb()
}
```

### Commentary

1. **`themeColor == DefaultThemeColor` is the sentinel for "user hasn't chosen".** When the accent is
   still default *and* the device is API 31+, system dynamic color wins. Any other value routes through
   materialkolor. One boolean, no extra state.
2. **No `shapes` argument.** vivi-music takes the Expressive shape defaults. That is a legitimate
   choice — pass `shapes` only if your brand needs a different scale.
3. **`MotionScheme.expressive()` is the one line that makes it Expressive.** It swaps every component's
   default animation spec to the springy curves. Everything downstream reads
   `MaterialTheme.motionScheme` (`defaultSpatialSpec()`, `slowSpatialSpec()`, `defaultEffectsSpec()`,
   `fastEffectsSpec()`).
4. **`pureBlack` is applied after generation, gated on `darkTheme`.** `copy(surface = Black,
   background = Black)` only — see color.md §7 for why call sites still need to branch.
5. **Typography is rebuilt inside `remember(brandFont)`** so the font preference is live without an
   activity restart.
6. **`Palette` + `Score` live in the theme file, not the activity.** `extractThemeColor()` is a pure
   `Bitmap.() -> Color`; the activity just calls it. Keep the extraction here.
7. **`ColorSaver`** lets `rememberSaveable` persist the current seed across configuration changes.
8. `Score.score(extractedColors, 2, 0xff4285f4.toInt(), true)` — the 4-arg overload requests 2 colors
   with a Google-blue fallback; used for gradient backdrops, not the scheme seed.

### The activity side — album-art seeding

Source: `/root/work/repos/vivi-music/app/src/main/kotlin/com/music/vivi/MainActivity.kt`

```kotlin
        val pureBlack = remember(pureBlackEnabled, useDarkTheme) {
            pureBlackEnabled && useDarkTheme
        }

        val (selectedThemeColorInt) = rememberPreference(SelectedThemeColorKey, defaultValue = DefaultThemeColor.toArgb())
        val selectedThemeColor = Color(selectedThemeColorInt)

        var themeColor by rememberSaveable(stateSaver = ColorSaver) {
            mutableStateOf(selectedThemeColor)
        }

        LaunchedEffect(selectedThemeColor) {
            if (!enableDynamicTheme) {
                themeColor = selectedThemeColor
            }
        }

        LaunchedEffect(playerConnection, enableDynamicTheme, selectedThemeColor) {
            val playerConnection = playerConnection
            if (!enableDynamicTheme || playerConnection == null) {
                themeColor = selectedThemeColor
                return@LaunchedEffect
            }

            playerConnection.service.currentMediaMetadata.collectLatest { song ->
                if (song?.thumbnailUrl != null) {
                    withContext(Dispatchers.IO) {
                        try {
                            val result = imageLoader.execute(
                                ImageRequest.Builder(this@MainActivity)
                                    .data(song.thumbnailUrl)
                                    .allowHardware(false)
                                    .memoryCachePolicy(CachePolicy.ENABLED)
                                    .diskCachePolicy(CachePolicy.ENABLED)
                                    .networkCachePolicy(CachePolicy.ENABLED)
                                    .crossfade(false)
                                    .build()
                            )
                            themeColor = result.image?.toBitmap()?.extractThemeColor() ?: selectedThemeColor
                        } catch (e: Exception) {
                            // Fallback to default on error
                            themeColor = selectedThemeColor
                        }
                    }
                } else {
                    themeColor = selectedThemeColor
                }
            }
        }

        vivimusicTheme(
            darkTheme = useDarkTheme,
            pureBlack = pureBlack,
            themeColor = themeColor,
        ) {
            BoxWithConstraints(
                modifier = Modifier
                    .fillMaxSize()
                    .background(if (pureBlack) Color.Black else MaterialTheme.colorScheme.surface)
            ) {
```

Non-negotiables in that block: `.allowHardware(false)` (Palette cannot read hardware bitmaps),
extraction on `Dispatchers.IO`, a try/catch with a fallback seed, and `pureBlack` derived as
`preference && isDark` rather than from the preference alone.

---

## Recipe 3 — Tomato `Theme.android.kt` (verbatim, Compose Multiplatform)

**Use this when:** you are on Compose Multiplatform and need an Android `actual` that combines system
dynamic color, a materialkolor seed, an AMOLED mode, and a `CompositionLocal` for raw font families.

Source: `/root/work/repos/Tomato/shared/src/androidMain/kotlin/org/nsh07/pomodoro/ui/theme/Theme.android.kt`
(complete file after the license header; Compose BOM alpha `2026.03.00`, materialKolor `4.1.1`)

```kotlin
package org.nsh07.pomodoro.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import com.materialkolor.dynamiccolor.ColorSpec
import com.materialkolor.rememberDynamicColorScheme

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
actual fun TomatoTheme(
    darkTheme: Boolean,
    seedColor: Color,
    dynamicColor: Boolean,
    blackTheme: Boolean,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }

        darkTheme -> darkScheme
        else -> lightScheme
    }

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkTheme
        }
    }
    CustomColors.black = blackTheme && darkTheme

    val dynamicColorScheme = rememberDynamicColorScheme(
        seedColor = when (seedColor) {
            Color.White -> colorScheme.primary
            else -> seedColor
        },
        isDark = darkTheme,
        specVersion = if (blackTheme && darkTheme) ColorSpec.SpecVersion.SPEC_2021 else ColorSpec.SpecVersion.SPEC_2025,
        isAmoled = blackTheme && darkTheme
    )

    val scheme =
        if (seedColor == Color.White && !(blackTheme && darkTheme)) colorScheme
        else dynamicColorScheme

    CompositionLocalProvider(LocalAppFonts provides getAppFonts()) {
        MaterialExpressiveTheme(
            colorScheme = scheme,
            typography = typography(),
            motionScheme = MotionScheme.expressive(),
            content = content
        )
    }
}
```

The common `expect` declaration and static schemes:
`/root/work/repos/Tomato/shared/src/commonMain/kotlin/org/nsh07/pomodoro/ui/theme/Theme.kt`

```kotlin
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val lightScheme = lightColorScheme(
    primary = primaryLight,
    onPrimary = onPrimaryLight,
    // ... all 30 M3 roles including surfaceDim / surfaceBright /
    //     surfaceContainerLowest..Highest
)

@Composable
expect fun TomatoTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    seedColor: Color = Color.White,
    dynamicColor: Boolean = true,
    blackTheme: Boolean = false,
    content: @Composable () -> Unit
)
```

### Commentary

1. **`Color.White` is the "no seed chosen" sentinel.** When `seedColor == Color.White`, the static or
   system scheme is used and its `primary` is fed to materialkolor as the seed — so the AMOLED path
   still has something to work from. Slightly obscure; `Color?` would be clearer in new code.
2. **`isAmoled = blackTheme && darkTheme`** — materialkolor darkens the whole surface ramp coherently,
   unlike a two-role `copy()`. Cleanest AMOLED option if you already depend on the library.
3. **`SPEC_2021` in AMOLED mode, `SPEC_2025` otherwise.** A deliberate choice: the 2025 spec's surface
   tones apparently did not suit the black theme. Note it, do not cargo-cult it.
4. **`CustomColors.black` is a mutable global** set from composition. It works, and is used by
   `TopAppBarDefaults.topAppBarColors(...)` accessors elsewhere in Tomato, but it is not observable
   state — recomposition is not triggered by writing it. In new code, use a `CompositionLocal`.
5. **`LocalAppFonts` is provided outside the theme** so callers can reach raw `FontFamily` values
   (the hero top-bar face) that do not fit the 15-slot type scale.
6. **Status-bar appearance is set in the theme, not the activity.** Reasonable in a CMP app where the
   theme is the only Android-aware layer. `!view.isInEditMode` guards Compose previews.
7. `expect fun` carries the default arguments; the `actual` must not repeat them.

### Companion — Tomato's AMOLED-aware component color tokens

Source: `/root/work/repos/Tomato/shared/src/commonMain/kotlin/org/nsh07/pomodoro/ui/theme/Color.kt`

```kotlin
object CustomColors {
    var black = false

    val topBarColors: TopAppBarColors
        @Composable get() =
            TopAppBarDefaults.topAppBarColors(
                containerColor = if (!black) colorScheme.surfaceContainer else colorScheme.surface,
                scrolledContainerColor = if (!black) colorScheme.surfaceContainer else colorScheme.surface
            )

    val detailPaneTopBarColors: TopAppBarColors
        @Composable get() =
            TopAppBarDefaults.topAppBarColors(
                containerColor = if (!black) colorScheme.surfaceContainerLow else colorScheme.surface,
                scrolledContainerColor = if (!black) colorScheme.surfaceContainerLow else colorScheme.surface
            )

    @OptIn(ExperimentalMaterial3ExpressiveApi::class)
    val listItemColors: ListItemColors
        @Composable get() =
            ListItemDefaults.segmentedColors(
                containerColor = if (!black) colorScheme.surfaceBright else colorScheme.surfaceContainerHigh,
                disabledContainerColor = if (!black) colorScheme.surfaceBright else colorScheme.surfaceContainerHigh,
            )

    val switchColors: SwitchColors
        @Composable get() = SwitchDefaults.colors(
            checkedIconColor = colorScheme.primary,
        )
```

This is the answer to "AMOLED mode still shows grey cards": centralize the branch in one
`@Composable get()` object rather than repeating `if (pureBlack)` at every call site.

---

## Recipe 4 — Med `Theme.kt` (verbatim, variable font + dynamic color)

**Use this when:** you want the simplest possible working theme — dynamic color with a static fallback,
one variable font, edge-to-edge window setup, a theme override for previews/settings.

Note Med's root theme is **plain `MaterialTheme`**, with Expressive opted into locally (Recipe 5).
That is a valid incremental-adoption stance; see the caveats below.

Source: `/root/work/repos/Med/app/src/main/kotlin/com/fedeveloper95/med/ui/theme/Theme.kt`
(all 103 lines; material3 `1.5.0-alpha21`)

```kotlin
package com.fedeveloper95.med.ui.theme

import android.app.Activity
import android.content.Context
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.core.view.WindowCompat
import com.fedeveloper95.med.PREF_THEME
import com.fedeveloper95.med.R
import com.fedeveloper95.med.THEME_DARK
import com.fedeveloper95.med.THEME_LIGHT
import com.fedeveloper95.med.THEME_SYSTEM

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

@OptIn(androidx.compose.ui.text.ExperimentalTextApi::class)
@Composable
fun MedTheme(
    themeOverride: Int? = null,
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    val view = LocalView.current

    val prefs = remember { context.getSharedPreferences("med_settings", Context.MODE_PRIVATE) }
    val themePref = themeOverride ?: prefs.getInt(PREF_THEME, THEME_SYSTEM)

    val darkTheme = when (themePref) {
        THEME_LIGHT -> false
        THEME_DARK -> true
        else -> isSystemInDarkTheme()
    }

    val colorScheme = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
    } else {
        if (darkTheme) darkColorScheme() else lightColorScheme()
    }

    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = Color.Transparent.toArgb()
            window.navigationBarColor = Color.Transparent.toArgb()
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = !darkTheme
                isAppearanceLightNavigationBars = !darkTheme
            }
        }
    }

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
}
```

### Commentary

Good:
- **`themeOverride: Int?`** — an escape hatch for previews, a settings preview pane, and secondary
  activities that need a forced light/dark. Cheap, worth copying.
- **Correct API-31 gate** with a real `lightColorScheme()`/`darkColorScheme()` fallback.
- **Window setup in one `SideEffect`**, guarded by `!view.isInEditMode` so previews do not crash
  casting `view.context as Activity`.

Fix before copying:
1. **Root is `MaterialTheme`, not `MaterialExpressiveTheme`.** No motion scheme is provided, so every
   expressive component silently uses standard springs. To adopt Expressive, swap the wrapper and add
   `motionScheme = MotionScheme.expressive()`.
2. **One `Font` entry only.** Every `FontWeight.Medium` slot (titleMedium, titleSmall, all labels) has
   no matching instance → synthetic bolding. Add 500 and 700 instances.
3. **Only the 15 baseline styles are set.** The 15 `*Emphasized` slots keep the default family. On
   alpha16+ replace the whole block with `Typography(fontFamily = GoogleSansFlex)`.
4. **`window.statusBarColor` / `navigationBarColor` are deprecated** on recent SDKs. Prefer
   `enableEdgeToEdge()` in the activity (Recipe 7) and drop these two lines.
5. **`prefs.getInt(...)` is read directly during composition** — it is not observable state, so a
   preference change does not recompose. Use DataStore + `collectAsState`, or a `remember`ed
   `MutableState` updated by a listener.

---

## Recipe 5 — Scoped `MaterialExpressiveTheme` (incremental retrofit)

**Use this when:** you have an existing `MaterialTheme` app and want expressive motion for one subtree
(a FAB menu, a toolbar, one screen) without a global migration.

Source: `/root/work/repos/Med/app/src/main/kotlin/com/fedeveloper95/med/elements/MainActivity/MainFAB.kt`

```kotlin
@Composable
fun MainFAB(
    fabMenuExpanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    menuItems: List<Triple<ItemType, androidx.compose.ui.graphics.vector.ImageVector, Triple<String, String?, String?>>>,
    onMenuItemClick: (ItemType, String, String?, String?) -> Unit
) {
    MaterialExpressiveTheme(
        colorScheme = MaterialTheme.colorScheme,
        typography = MaterialTheme.typography,
        shapes = MaterialTheme.shapes,
        motionScheme = MotionScheme.expressive()
    ) {
```

**Why all four arguments are passed explicitly:** `MaterialExpressiveTheme`'s `null` defaults mean
"use the Expressive default", **not** "inherit". Omitting `colorScheme` here would replace the app's
dynamic scheme with `expressiveLightColorScheme()` inside the FAB subtree — a visible color break.
Passing `MaterialTheme.colorScheme` / `.typography` / `.shapes` re-injects the ambient values and
changes **only** motion.

Generic form:

```kotlin
@Composable
fun ExpressiveMotionScope(content: @Composable () -> Unit) {
    MaterialExpressiveTheme(
        colorScheme = MaterialTheme.colorScheme,
        typography = MaterialTheme.typography,
        shapes = MaterialTheme.shapes,
        motionScheme = MotionScheme.expressive(),
        content = content,
    )
}
```

Migration path: wrap subtrees like this while retrofitting → once most screens are wrapped, promote
`MaterialExpressiveTheme` to the root and delete the scopes. Do not leave scoped themes scattered
permanently; they are easy to forget and produce inconsistent motion.

---

## Recipe 6 — LastChat `Theme.kt` (verbatim, preset themes + AMOLED + runtime typography)

**Use this when:** you ship user-selectable preset palettes alongside dynamic color, need extended
(non-Material) color roles, and want user-scalable typography.

Source: `/root/work/repos/LastChat/app/src/main/java/me/rerere/rikkahub/ui/theme/Theme.kt`
(all 117 lines; material3 `1.5.0-alpha08`)

```kotlin
package me.rerere.rikkahub.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.LayoutDirection
import androidx.core.view.WindowCompat
import kotlinx.serialization.Serializable

import me.rerere.rikkahub.ui.hooks.rememberColorMode
import me.rerere.rikkahub.ui.hooks.rememberUserSettingsState

private val ExtendLightColors = lightExtendColors()
private val ExtendDarkColors = darkExtendColors()
val LocalExtendColors = compositionLocalOf { ExtendLightColors }

val LocalDarkMode = compositionLocalOf { false }

private val AMOLED_DARK_BACKGROUND = Color(0xFF000000)

@Serializable
enum class ColorMode {
    SYSTEM,
    LIGHT,
    DARK
}

@Composable
fun RikkahubTheme(
    content: @Composable () -> Unit
) {
    val settings by rememberUserSettingsState()

    val colorMode by rememberColorMode()
    val darkTheme = when (colorMode) {
        ColorMode.SYSTEM -> isSystemInDarkTheme()
        ColorMode.LIGHT -> false
        ColorMode.DARK -> true
    }


    val colorScheme = when {
        settings.dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> findPresetTheme(settings.themeId).getColorScheme(dark = true)
        else -> findPresetTheme(settings.themeId).getColorScheme(dark = false)
    }
    val colorSchemeConverted = remember(darkTheme, colorScheme) {
        if (darkTheme) {
            colorScheme.copy(
                background = AMOLED_DARK_BACKGROUND,
                surface = AMOLED_DARK_BACKGROUND,
            )
        } else {
            colorScheme
        }
    }
    val extendColors = if (darkTheme) ExtendDarkColors else ExtendLightColors
    val statusBarColor = colorSchemeConverted.background

    // 更新状态栏图标颜色
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = statusBarColor.luminance() > 0.5f
                isAppearanceLightNavigationBars = !darkTheme
            }
            view.layoutDirection = android.view.View.LAYOUT_DIRECTION_LTR
            @Suppress("DEPRECATION")
            window.navigationBarColor = android.graphics.Color.TRANSPARENT
            @Suppress("DEPRECATION")
            window.statusBarColor = statusBarColor.toArgb()
        }
    }

    CompositionLocalProvider(
        LocalLayoutDirection provides LayoutDirection.Ltr,
        LocalDarkMode provides darkTheme,
        LocalExtendColors provides extendColors
    ) {
        // Create typography from font settings
        val fontSettings = me.rerere.rikkahub.ui.hooks.rememberFontSettings()
        val typography = rememberTypographyFromFontSettings(fontSettings)

        MaterialTheme(
            colorScheme = colorSchemeConverted,
            typography = typography,
            shapes = Shapes,
            content = content
        )
    }
}

val MaterialTheme.extendColors
    @Composable
    @ReadOnlyComposable
    get() = LocalExtendColors.current
```

The preset-theme model:
Source: `/root/work/repos/LastChat/app/src/main/java/me/rerere/rikkahub/ui/theme/PresetTheme.kt`

```kotlin
data class PresetTheme(
    val id: String,
    val name: @Composable () -> Unit,
    val standardLight: ColorScheme,
    val standardDark: ColorScheme,
) {
    fun getColorScheme(dark: Boolean): ColorScheme {
        return if (dark) standardDark else standardLight
    }
}

val PresetThemes by lazy {
    listOf(
        SeafoamMintThemePreset,
        OceanThemePreset,
        SakuraThemePreset,
        SpringThemePreset,
        AutumnThemePreset,
        BlackThemePreset,
    )
}

fun normalizePresetThemeId(id: String): String {
    return when (id) {
        LegacyNightskyBlueThemeId -> SeafoamMintThemeId
        else -> id
    }
}

fun findPresetTheme(id: String): PresetTheme {
    val normalizedId = normalizePresetThemeId(id)
    return PresetThemes.find { it.id == normalizedId } ?: SakuraThemePreset
}
```

### Commentary

Worth copying:
- **`PresetTheme` as a data class holding both schemes**, looked up by id, with a `normalize` step for
  renamed/legacy ids and a **non-null fallback** on lookup miss. That is the right shape for a
  user-selectable palette list — never let an unknown persisted id crash or blank the theme.
- **`LocalExtendColors` + the `MaterialTheme.extendColors` extension property.** The correct way to add
  colors Material does not define (success/warning/brand accents) without polluting `ColorScheme`.
  The `@ReadOnlyComposable` extension gives call sites `MaterialTheme.extendColors.success`.
- **`LocalDarkMode`** — cheaper than re-deriving dark state deep in the tree.
- **`isAppearanceLightStatusBars = statusBarColor.luminance() > 0.5f`** — deriving icon appearance from
  the actual bar color rather than assuming it tracks `darkTheme`. More correct than `!darkTheme` when
  the bar is a custom color.
- **`rememberTypographyFromFontSettings(fontSettings)`** — the whole type scale is a function of user
  settings (size / line height / letter-spacing multipliers). See
  `typography-and-variable-fonts.md` §7.4.

Fix before copying:
1. **Root is `MaterialTheme`.** No `motionScheme`. LastChat's "expressive" is entirely typography and
   component choice. Swap to `MaterialExpressiveTheme` + `MotionScheme.expressive()` for real
   Expressive motion.
2. **AMOLED is unconditional in dark mode** — no user toggle. Gate it.
3. **`LocalLayoutDirection provides LayoutDirection.Ltr` and
   `view.layoutDirection = LAYOUT_DIRECTION_LTR` force LTR app-wide**, breaking RTL locales. Do not
   copy this.
4. **Deprecated `window.statusBarColor` / `navigationBarColor`** (already `@Suppress`ed). Use
   `enableEdgeToEdge()`.
5. `shapes = Shapes` uses the five-arg `Shapes` constructor, leaving the three Expressive steps at
   defaults while the rest of the scale is shifted. See `shape-scale.md` §6.2.

---

## Recipe 7 — Activity-level wiring

**Use this when:** setting up `MainActivity` — edge-to-edge, splash, insets, status-bar appearance.

### 7.1 The modern form

```kotlin
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()          // BEFORE super.onCreate()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()             // AFTER super.onCreate()

        setContent {
            AppTheme {
                Scaffold { innerPadding ->
                    // consume innerPadding — do NOT ignore it under edge-to-edge
                }
            }
        }
    }
}
```

```kotlin
// build.gradle.kts
implementation("androidx.activity:activity-compose:…")   // enableEdgeToEdge
implementation("androidx.core:core-splashscreen:…")      // installSplashScreen
```

Order matters: `installSplashScreen()` must run before `super.onCreate()`; `enableEdgeToEdge()` after.

Real call sites:
- `/root/work/repos/Med/app/src/main/kotlin/com/fedeveloper95/med/MainActivity.kt`
- `/root/work/repos/LastChat/app/src/main/java/me/rerere/rikkahub/RouteActivity.kt`
- `/root/work/repos/vivi-music/app/src/main/kotlin/com/music/vivi/MainActivity.kt`

### 7.2 Med — verbatim

Source: `/root/work/repos/Med/app/src/main/kotlin/com/fedeveloper95/med/MainActivity.kt`

```kotlin
    @OptIn(ExperimentalMaterial3WindowSizeClassApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        AppLockManager.init(application)
        HandoffHelper.init(application)
        enableEdgeToEdge()

        setContent {
            val windowSizeClass = calculateWindowSizeClass(this)
            // …
```

### 7.3 vivi-music — verbatim (pre-`enableEdgeToEdge` form)

Source: `/root/work/repos/vivi-music/app/src/main/kotlin/com/music/vivi/MainActivity.kt`

```kotlin
    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        window.decorView.layoutDirection = View.LAYOUT_DIRECTION_LTR
        WindowCompat.setDecorFitsSystemWindows(window, false)
```

`WindowCompat.setDecorFitsSystemWindows(window, false)` is the manual equivalent of
`enableEdgeToEdge()`. Prefer `enableEdgeToEdge()` — it also configures the system-bar scrims for you.
(The forced `LAYOUT_DIRECTION_LTR` breaks RTL; do not copy it.)

### 7.4 LastChat — verbatim

Source: `/root/work/repos/LastChat/app/src/main/java/me/rerere/rikkahub/RouteActivity.kt`

```kotlin
    override fun onCreate(savedInstanceState: Bundle?) {
        try {
            enableEdgeToEdge()
            androidx.core.view.WindowCompat.setDecorFitsSystemWindows(window, false)
            disableNavigationBarContrast()
            super.onCreate(savedInstanceState)
```

Note this calls `enableEdgeToEdge()` **before** `super.onCreate()` and then also calls
`setDecorFitsSystemWindows` — belt and braces. `enableEdgeToEdge()` alone, after `super.onCreate()`,
is the documented form.

### 7.5 Status-bar / navigation-bar appearance

Set from inside the theme, so it tracks the same `darkTheme` boolean as the color scheme:

```kotlin
val view = LocalView.current
if (!view.isInEditMode) {
    SideEffect {
        val window = (view.context as Activity).window
        WindowCompat.getInsetsController(window, view).apply {
            isAppearanceLightStatusBars = !darkTheme
            isAppearanceLightNavigationBars = !darkTheme
        }
    }
}
```

Rules:
- **`!view.isInEditMode` is mandatory.** `view.context as Activity` throws in Compose previews.
- **`SideEffect`, not a bare call** — this is a side effect on a non-Compose system, and must run after
  a successful composition.
- If your bars sit on a custom color, derive appearance from luminance rather than `darkTheme`
  (LastChat): `isAppearanceLightStatusBars = barColor.luminance() > 0.5f`.
- **`window.statusBarColor` / `window.navigationBarColor` are deprecated.** Under `enableEdgeToEdge()`
  you do not need them. Med and LastChat still set them; that is legacy.

### 7.6 Edge-to-edge checklist

- [ ] `enableEdgeToEdge()` in every activity that hosts Compose content, not just `MainActivity`.
      (LastChat calls it in `RouteActivity`, `TextSelectionActivity`, `AskLastChatShareActivity`,
      `WidgetConfigActivity`; Med in `MainActivity`, `SettingsActivity`, `EditModeActivity`,
      `UpdaterActivity`, `NotificationsSettingsActivity`, `QuickActionsSettingsActivity`.)
- [ ] `Scaffold`'s `innerPadding` is consumed, or `WindowInsets` are handled manually.
- [ ] Scrolling content extends under the bars; only interactive elements respect insets.
- [ ] Bottom sheets / floating toolbars / FABs use `WindowInsets.safeDrawing` or `navigationBars`.
- [ ] `installSplashScreen()` before `super.onCreate()`.
- [ ] Status-bar icon appearance tracks the theme.
- [ ] Tested on a gesture-nav device **and** a 3-button-nav device.

---

## Recipe 8 — Compose Multiplatform

**Use this when:** the theme must compile for Android plus desktop/iOS/wasm targets.

### 8.1 What differs

| Concern | Android | Other CMP targets |
| --- | --- | --- |
| `dynamicLightColorScheme` / `dynamicDarkColorScheme` | Available, API 31+ | **Do not exist.** They are Android-only, in `androidMain`. |
| Wallpaper-derived color | Yes | No source of a system seed. Use a static scheme or a materialkolor seed. |
| `WindowCompat` / `WindowInsetsController` | Yes | No. Window chrome is per-platform. |
| `Activity`, `LocalView.context as Activity` | Yes | No. Must live in `androidMain`. |
| Font loading | `Font(resId = R.font.x, …)` | `org.jetbrains.compose.resources.Font(Res.font.x, …)` |
| `FontVariation.Settings` | API 26+ | Support varies by target; verify on each. **UNVERIFIED** for non-Android targets. |
| `MaterialExpressiveTheme`, `MotionScheme`, `Shapes`, `Typography` | Yes | Yes — all in `commonMain` of material3. |
| materialkolor `rememberDynamicColorScheme` | Yes | Yes — material-kolor is multiplatform. |

### 8.2 The `expect` / `actual` split (Tomato's structure)

```
shared/src/commonMain/…/ui/theme/Theme.kt          // expect fun + lightScheme/darkScheme + Color.kt
shared/src/commonMain/…/ui/theme/Type.kt           // typography(), getAppFonts(), LocalAppFonts
shared/src/commonMain/…/ui/theme/Shape.kt          // TomatoShapeDefaults
shared/src/androidMain/…/ui/theme/Theme.android.kt // actual fun — dynamic color, window insets
```

`commonMain`:

```kotlin
@Composable
expect fun TomatoTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    seedColor: Color = Color.White,
    dynamicColor: Boolean = true,
    blackTheme: Boolean = false,
    content: @Composable () -> Unit
)
```

`androidMain` — the full `actual` is Recipe 3.

A non-Android `actual` (illustrative; **not** verbatim from a repo — Tomato's non-Android actuals were
not read):

```kotlin
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
actual fun TomatoTheme(
    darkTheme: Boolean,
    seedColor: Color,
    dynamicColor: Boolean,      // ignored: no system dynamic color off Android
    blackTheme: Boolean,
    content: @Composable () -> Unit
) {
    val scheme = if (seedColor == Color.White) {
        if (darkTheme) darkScheme else lightScheme
    } else {
        rememberDynamicColorScheme(
            seedColor = seedColor,
            isDark = darkTheme,
            specVersion = ColorSpec.SpecVersion.SPEC_2025,
            isAmoled = blackTheme && darkTheme,
        )
    }

    CompositionLocalProvider(LocalAppFonts provides getAppFonts()) {
        MaterialExpressiveTheme(
            colorScheme = scheme,
            typography = typography(),
            motionScheme = MotionScheme.expressive(),
            content = content
        )
    }
}
```

### 8.3 CMP rules

1. **Keep the `expect` signature platform-agnostic.** `dynamicColor: Boolean` is fine as a *request*;
   platforms that cannot honour it ignore it. Do not put `Context` in the common signature.
2. **Default arguments go on the `expect`, never on the `actual`.**
3. **Static schemes belong in `commonMain`.** Generate them with Material Theme Builder; they are the
   fallback on every platform and the only scheme on non-Android.
4. **Fonts:** `commonMain` uses `org.jetbrains.compose.resources.Font(Res.font.name, weight,
   variationSettings = …)` with generated `Res` accessors. Same `FontVariation` API, different `Font`
   function.
5. **All window/insets/status-bar code goes in `androidMain`.** Anything touching `Activity`,
   `WindowCompat` or `LocalView.context as Activity` cannot be common.
6. **material-kolor is the portable dynamic-color story.** It is the only way to get a
   generated-from-a-seed scheme on non-Android targets.
7. Provide `CompositionLocal`s (fonts, extended colors) **inside** the `actual` so every platform gets
   them, or in a shared wrapper composable called by each `actual`.

---

## 9. Cross-recipe comparison

| | vivi-music | Tomato | Med | LastChat |
| --- | --- | --- | --- | --- |
| Root theme | `MaterialExpressiveTheme` | `MaterialExpressiveTheme` | `MaterialTheme` (+ scoped Expressive) | `MaterialTheme` |
| `MotionScheme.expressive()` | yes | yes | scoped only | no |
| material3 version | 1.5.0-alpha23 | BOM alpha 2026.03.00 | 1.5.0-alpha21 | 1.5.0-alpha08 |
| System dynamic color | yes (default accent only) | yes | yes | yes (setting) |
| Seeded palette | materialkolor, SPEC_2025 | materialkolor, SPEC_2025/2021 | no | no (preset schemes) |
| Image-derived seed | Palette + Score, per track | no | no | no |
| AMOLED | `copy(surface, background)` | materialkolor `isAmoled` | no | unconditional `copy()` in dark |
| Custom `Shapes` | no (Expressive defaults) | no theme-level; `TomatoShapeDefaults` object | no | yes (5-arg constructor) |
| Variable font | 4 selectable families, 3 weights each | Google Sans Flex ×2 + hero instance | Google Sans Flex ×1 | Google Sans Flex ×6 weights |
| Runtime typography | font-family preference | no | no | full size/spacing settings |
| Extended colors | no | `CustomColors` object | no | `LocalExtendColors` |
| Opt-in strategy | module-wide compiler flag | per-call-site `@OptIn` (85 sites) | per-call-site | module-wide compiler flags |

**Where to start:** Recipe 1. Then borrow the seeded-color plumbing from Recipe 2, the AMOLED handling
from Recipe 3, the `PresetTheme` + `LocalExtendColors` structure from Recipe 6, and the activity wiring
from Recipe 7.

---

## 10. Verification checklist

- [ ] Root is `MaterialExpressiveTheme`, not `MaterialTheme`.
- [ ] `motionScheme = MotionScheme.expressive()` is passed explicitly.
- [ ] `expressiveLightColorScheme()` for light; `darkColorScheme()` for dark. No
      `expressiveDarkColorScheme()` anywhere.
- [ ] Every `dynamic*ColorScheme(context)` call is inside a `Build.VERSION.SDK_INT >= S` branch.
- [ ] Any materialkolor call passes `specVersion = ColorSpec.SpecVersion.SPEC_2025`.
- [ ] Scheme construction is `remember`ed and keyed on all inputs (base scheme, dark, AMOLED, seed).
- [ ] Nested/scoped `MaterialExpressiveTheme` passes `colorScheme`/`typography`/`shapes` explicitly.
- [ ] The font family declares one `Font` entry per weight the type scale uses.
- [ ] `Typography` covers the emphasized slots (use `Typography(fontFamily = …)` on alpha16+).
- [ ] Window insets: `enableEdgeToEdge()`, `installSplashScreen()` before `super.onCreate()`,
      status-bar appearance tracks `darkTheme`, `!view.isInEditMode` guard present.
- [ ] Toggled light ↔ dark, dynamic on ↔ off, AMOLED on ↔ off, and each preset — no unreadable text.
- [ ] No `RoundedCornerShape(…)` or hex `Color(0xFF…)` literals in screen composables.

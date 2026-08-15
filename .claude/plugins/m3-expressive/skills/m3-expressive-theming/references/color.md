# Color — M3 Expressive `ColorScheme`

Scope: color roles, pairing rules, the Expressive scheme builders, dynamic color, seeded palettes,
AMOLED, contrast. Shape scale is in `shape-scale.md`; complete `Theme.kt` files are in
`theme-recipes.md`.

**The one-sentence summary:** M3 Expressive added **zero new color roles**. It changed four
`on*Container` values in the light scheme and changed *how* you are expected to spend the existing
roles (heavier use of containers, tertiary as the "break" accent).

---

## 1. Decision table — pick the scheme builder

| Situation | Use |
| --- | --- |
| Light, Expressive defaults | `expressiveLightColorScheme()` |
| Dark, any Expressive app | `darkColorScheme()` — **there is no expressive dark builder** |
| Android 12+ (API 31+), wallpaper-derived | `dynamicLightColorScheme(context)` / `dynamicDarkColorScheme(context)` |
| Seeded from an image, album art, or a user-picked accent | materialkolor `rememberDynamicColorScheme(seedColor, isDark, specVersion = SPEC_2025)` |
| Hand-authored brand palette | `lightColorScheme(primary = …, …)` with values from Material Theme Builder |
| True-black / AMOLED on top of any of the above | `ColorScheme.copy(surface = Black, background = Black)` or materialkolor `isAmoled = true` |

---

## 2. `ColorScheme` role inventory — 48 roles

Source: `androidx-main` `compose/material3/.../ColorScheme.kt`, verbatim constructor params.

### 2.1 Accent groups (primary / secondary / tertiary)

| Role | Pairs with | Use for |
| --- | --- | --- |
| `primary` | `onPrimary` | The single most important action per screen. Filled buttons, FAB, active selection. |
| `onPrimary` | — | Content drawn **on** `primary`. |
| `primaryContainer` | `onPrimaryContainer` | Lower-emphasis primary surfaces: highlighted cards, selected states, ongoing-activity chips. |
| `onPrimaryContainer` | — | Content on `primaryContainer`. **Tone 30 in the Expressive light scheme** (tone 10 in baseline). |
| `inversePrimary` | — | Primary-flavoured accent drawn on `inverseSurface` (snackbar actions). |
| `secondary` | `onSecondary` | Supporting actions in dense UI. Visible without competing with primary. |
| `onSecondary` | — | Content on `secondary`. |
| `secondaryContainer` | `onSecondaryContainer` | Structuring secondary elements in dense layouts. Common for `ToggleButton` checked state. |
| `onSecondaryContainer` | — | **Tone 30 in Expressive light.** |
| `tertiary` | `onTertiary` | The "stand out" accent: badges, stickers, one-off special actions. The color break in a hero moment. |
| `onTertiary` | — | Content on `tertiary`. |
| `tertiaryContainer` | `onTertiaryContainer` | Backgrounds grouping tertiary content (a row of badges). |
| `onTertiaryContainer` | — | **Tone 30 in Expressive light.** |

### 2.2 Error group

| Role | Pairs with | Use for |
| --- | --- | --- |
| `error` | `onError` | Remove / delete / close / dismiss actions; validation failure. |
| `onError` | — | Content on `error`. |
| `errorContainer` | `onErrorContainer` | Less prominent error state — "an active error state that feels less interactive than a filled state". |
| `onErrorContainer` | — | **Tone 30 in Expressive light.** |

### 2.3 Surfaces and the tonal container ramp

M3 uses **tonal surface containers** instead of elevation overlays. Do not fake elevation with alpha.

| Role | Pairs with | Use for |
| --- | --- | --- |
| `background` | `onBackground` | Root window fill. In practice most apps set this equal to `surface`. |
| `surface` | `onSurface` | Default page surface. |
| `surfaceDim` | `onSurface` | Dimmest surface in the ramp. |
| `surfaceBright` | `onSurface` | Brightest surface in the ramp. Tomato uses it for list-item containers. |
| `surfaceContainerLowest` | `onSurface` | Lowest container tone. |
| `surfaceContainerLow` | `onSurface` | Expanded containers that must sit *below* `surfaceContainer`; non-interactive cards. |
| `surfaceContainer` | `onSurface` | **The default container color for most elements.** |
| `surfaceContainerHigh` | `onSurface` | High-emphasis components sitting on top of `surfaceContainer`; brings focus/hierarchy. |
| `surfaceContainerHighest` | `onSurface` | Top of the ramp. |
| `surfaceVariant` | `onSurfaceVariant` | Variant surface. Prefer the container ramp for new work. |
| `onSurfaceVariant` | — | Secondary text, inactive icons, supporting content. |
| `surfaceTint` | — | Defaults to `primary`. Tint applied at elevation. |
| `inverseSurface` | `inverseOnSurface` | Snackbars, tooltips — inverted against the page. |
| `inverseOnSurface` | — | Content on `inverseSurface`. |
| `outline` | — | Borders that must be visible: `OutlinedButton`, `OutlinedTextField`. |
| `outlineVariant` | — | Decorative dividers, low-emphasis separators. |
| `scrim` | — | Modal dimming behind sheets/dialogs. |

### 2.4 Fixed roles (predate Expressive, unchanged by it)

`primaryFixed`, `primaryFixedDim`, `onPrimaryFixed`, `onPrimaryFixedVariant`, and the identical
`secondary*` and `tertiary*` sets — 12 roles total. These stay the same value in light **and** dark,
so a component keeps its color across theme switches. Useful for persistent branded elements
(a media-player accent that must not invert). Rarely needed; if you are unsure, do not use them.

### 2.5 Pairing rules — the structural law

1. **Never mix families.** `onPrimary` goes only on `primary`. `onPrimaryContainer` goes only on
   `primaryContainer`. `onSurface`/`onSurfaceVariant` go on any `surface*` role.
2. Every Material-generated pair guarantees **≥ 3:1** contrast. That is the *role-pair* guarantee for
   UI elements — it is **not** sufficient for small text (see §8).
3. `onBackground` pairs with `background`; in practice it equals `onSurface` in every generated
   scheme.
4. `inverseOnSurface` pairs only with `inverseSurface`.
5. If you find yourself writing `MaterialTheme.colorScheme.onPrimary` on a `surfaceContainer`
   background, you have a bug, not a style choice.

---

## 3. `expressiveLightColorScheme()`

Source: `ColorScheme.kt`, verbatim.

```kotlin
fun expressiveLightColorScheme() =
    lightColorScheme(
        onPrimaryContainer = PaletteTokens.Primary30,
        onSecondaryContainer = PaletteTokens.Secondary30,
        onTertiaryContainer = PaletteTokens.Tertiary30,
        onErrorContainer = PaletteTokens.Error30,
    )
```

KDoc: *"Returns a light Material color scheme."* — *"It serves as the default color configuration
for `MaterialExpressiveTheme`, with a corresponding dark variant available through
`darkColorScheme`."*

**That is the entire delta.** Four `on*Container` roles moved from tone 10 to tone 30. Lighter, more
saturated on-container text — the visible "expressive" color signal. Everything else is identical to
`lightColorScheme()`.

Practical consequence: `on*Container` text is *lower contrast* against its container in the
Expressive scheme than in baseline M3. It still clears the 3:1 role-pair floor, but if you put 12sp
supporting text in `onPrimaryContainer` you may fail 4.5:1. Use it for labels and short emphasis,
not for paragraph text.

### `expressiveDarkColorScheme()` — DOES NOT EXIST

Verified absent from `ColorScheme.kt` as of `androidx-main` 2026-08-14 and `1.5.0-alpha26`.
There is no expressive dark builder. Use `darkColorScheme()`.

```kotlin
// Official sample, verbatim
MaterialExpressiveTheme(
    colorScheme =
        if (isSystemInDarkTheme()) darkColorScheme() else expressiveLightColorScheme()
) {
    content()
}
```

If you see `expressiveDarkColorScheme()` in generated code, a blog post, or your own memory, it is
wrong and will not compile.

---

## 4. `lightColorScheme()` / `darkColorScheme()` — full signature

Source: `ColorScheme.kt`, verbatim (light shown; `darkColorScheme` is identical with
`ColorDarkTokens`).

```kotlin
fun lightColorScheme(
    primary: Color = ColorLightTokens.Primary,
    onPrimary: Color = ColorLightTokens.OnPrimary,
    primaryContainer: Color = ColorLightTokens.PrimaryContainer,
    onPrimaryContainer: Color = ColorLightTokens.OnPrimaryContainer,
    inversePrimary: Color = ColorLightTokens.InversePrimary,
    secondary: Color = ColorLightTokens.Secondary,
    onSecondary: Color = ColorLightTokens.OnSecondary,
    secondaryContainer: Color = ColorLightTokens.SecondaryContainer,
    onSecondaryContainer: Color = ColorLightTokens.OnSecondaryContainer,
    tertiary: Color = ColorLightTokens.Tertiary,
    onTertiary: Color = ColorLightTokens.OnTertiary,
    tertiaryContainer: Color = ColorLightTokens.TertiaryContainer,
    onTertiaryContainer: Color = ColorLightTokens.OnTertiaryContainer,
    background: Color = ColorLightTokens.Background,
    onBackground: Color = ColorLightTokens.OnBackground,
    surface: Color = ColorLightTokens.Surface,
    onSurface: Color = ColorLightTokens.OnSurface,
    surfaceVariant: Color = ColorLightTokens.SurfaceVariant,
    onSurfaceVariant: Color = ColorLightTokens.OnSurfaceVariant,
    surfaceTint: Color = primary,
    inverseSurface: Color = ColorLightTokens.InverseSurface,
    inverseOnSurface: Color = ColorLightTokens.InverseOnSurface,
    error: Color = ColorLightTokens.Error,
    onError: Color = ColorLightTokens.OnError,
    errorContainer: Color = ColorLightTokens.ErrorContainer,
    onErrorContainer: Color = ColorLightTokens.OnErrorContainer,
    outline: Color = ColorLightTokens.Outline,
    outlineVariant: Color = ColorLightTokens.OutlineVariant,
    scrim: Color = ColorLightTokens.Scrim,
    surfaceBright: Color = ColorLightTokens.SurfaceBright,
    surfaceContainer: Color = ColorLightTokens.SurfaceContainer,
    surfaceContainerHigh: Color = ColorLightTokens.SurfaceContainerHigh,
    surfaceContainerHighest: Color = ColorLightTokens.SurfaceContainerHighest,
    surfaceContainerLow: Color = ColorLightTokens.SurfaceContainerLow,
    surfaceContainerLowest: Color = ColorLightTokens.SurfaceContainerLowest,
    surfaceDim: Color = ColorLightTokens.SurfaceDim,
    primaryFixed: Color = ColorLightTokens.PrimaryFixed,
    primaryFixedDim: Color = ColorLightTokens.PrimaryFixedDim,
    onPrimaryFixed: Color = ColorLightTokens.OnPrimaryFixed,
    onPrimaryFixedVariant: Color = ColorLightTokens.OnPrimaryFixedVariant,
    secondaryFixed: Color = ColorLightTokens.SecondaryFixed,
    secondaryFixedDim: Color = ColorLightTokens.SecondaryFixedDim,
    onSecondaryFixed: Color = ColorLightTokens.OnSecondaryFixed,
    onSecondaryFixedVariant: Color = ColorLightTokens.OnSecondaryFixedVariant,
    tertiaryFixed: Color = ColorLightTokens.TertiaryFixed,
    tertiaryFixedDim: Color = ColorLightTokens.TertiaryFixedDim,
    onTertiaryFixed: Color = ColorLightTokens.OnTertiaryFixed,
    onTertiaryFixedVariant: Color = ColorLightTokens.OnTertiaryFixedVariant,
): ColorScheme
```

`ColorScheme` also has a `copy(...)` with the same 48 parameters — that is the mechanism behind every
AMOLED override in §7.

---

## 5. Dynamic color (Material You)

Unchanged by Expressive.

```kotlin
val dynamicColor = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
val colorScheme = when {
    dynamicColor && darkTheme  -> dynamicDarkColorScheme(LocalContext.current)
    dynamicColor && !darkTheme -> dynamicLightColorScheme(LocalContext.current)
    darkTheme -> darkColorScheme()
    else -> expressiveLightColorScheme()
}
MaterialExpressiveTheme(colorScheme = colorScheme) { /* … */ }
```

| Fact | Detail |
| --- | --- |
| API level | `dynamicLightColorScheme(Context)` / `dynamicDarkColorScheme(Context)` require **API 31+ (Android 12)**. Guard with `Build.VERSION.SDK_INT >= Build.VERSION_CODES.S` or you get a runtime crash on older devices. |
| Source of the seed | The system wallpaper (or the user's chosen accent on Pixel/Android 13+). You do not control it. |
| What it guarantees | All 48 roles populated, all pairings meet the ≥3:1 role floor, and the palette reflects the user's **contrast setting** (Android 14+) and dark-mode state. |
| What it does **not** guarantee | Anything about hue. Your brand color is gone. A red destructive-action design can end up with a green `error` neighbourhood. It also does not guarantee that *your* hardcoded colors still look right next to it. |
| Harmonization | If you must keep a reserved/semantic brand color alongside dynamic color, **harmonize** it toward the dynamic primary rather than pasting the raw hex. |
| Gate it | Always expose a user toggle. Dynamic color is a preference, not a default everyone wants. |

**Do not** use `dynamicLightColorScheme` on a `darkTheme == true` branch. The light/dark choice must
match the `darkTheme` boolean, or every `on*` role inverts against its surface.

---

## 6. Seeded / generated palettes — `com.materialkolor:material-kolor`

The community standard for generating a full `ColorScheme` from an arbitrary seed `Color` (album art,
hero image, user-picked accent). Both vivi-music and Tomato pin **`materialKolor = "4.1.1"`**.

```toml
# gradle/libs.versions.toml
[versions]
materialKolor = "4.1.1"

[libraries]
materialKolor = { module = "com.materialkolor:material-kolor", version.ref = "materialKolor" }
```

```kotlin
import com.materialkolor.PaletteStyle
import com.materialkolor.dynamiccolor.ColorSpec
import com.materialkolor.rememberDynamicColorScheme
import com.materialkolor.score.Score   // only if you use Score for seed ranking
```

### 6.1 `rememberDynamicColorScheme` — parameters observed in shipping code

Named arguments verified in use across vivi-music and Tomato. Full signature and parameter order are
**unverified** — always pass named arguments.

| Parameter | Type | Notes |
| --- | --- | --- |
| `seedColor` | `Color` | The source color. Required. |
| `isDark` | `Boolean` | Must track your `darkTheme` state. |
| `specVersion` | `ColorSpec.SpecVersion` | **Pass `SPEC_2025`** for M3 Expressive palette math. Defaults to the older spec. |
| `style` | `PaletteStyle` | Generation strategy. `TonalSpot` and `Monochrome` verified in use. |
| `isAmoled` | `Boolean` | Forces true-black surfaces. Tomato's AMOLED path. |

```kotlin
val scheme = rememberDynamicColorScheme(
    seedColor = themeColor,
    isDark = darkTheme,
    specVersion = ColorSpec.SpecVersion.SPEC_2025,
    style = PaletteStyle.TonalSpot,
)
```

> **`specVersion = ColorSpec.SpecVersion.SPEC_2025` is the single most important argument here.**
> Without it you get 2021-era palette math and the scheme will not match an Expressive design. The
> other verified value is `ColorSpec.SpecVersion.SPEC_2021`.

### 6.2 `PaletteStyle`

Verified in use in these repos: **`PaletteStyle.TonalSpot`** (the default Material You feel) and
**`PaletteStyle.Monochrome`** (used when the seed is pure black).

MaterialKolor ships additional styles — commonly `Neutral`, `Vibrant`, `Expressive`, `Rainbow`,
`FruitSalad`, `Content`, `Fidelity`. **UNVERIFIED from these sources**: confirm against the version
of material-kolor you actually depend on before using any name beyond `TonalSpot` / `Monochrome`.

Rule of thumb:
- `TonalSpot` — general purpose, safest.
- `Monochrome` — greyscale; the correct choice when the seed carries no usable hue (pure black/white).
- `Content` / `Fidelity` (if present) — keep the seed hue as literally as possible. Use for album art
  where users expect the app to *look like* the cover.

### 6.3 Album-art-seeded color — real implementation (vivi-music)

Source: `/root/work/repos/vivi-music/app/src/main/kotlin/com/music/vivi/ui/theme/Theme.kt`

The extraction half: `androidx.palette.graphics.Palette` produces swatch→population pairs,
materialkolor's `Score` ranks them into a single seed color.

```kotlin
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
```

The scheme-selection half — system dynamic color when the user has not picked an accent, materialkolor
otherwise:

```kotlin
val useSystemDynamicColor =
    (themeColor == DefaultThemeColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S)

val baseColorScheme = if (useSystemDynamicColor) {
    if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
} else {
    rememberDynamicColorScheme(
        seedColor = themeColor,
        isDark = darkTheme,
        specVersion = ColorSpec.SpecVersion.SPEC_2025,
        style = if (themeColor.toArgb() == 0xFF000000.toInt())
            PaletteStyle.Monochrome else PaletteStyle.TonalSpot
    )
}
```

The plumbing half — `MainActivity` collects the current track's artwork and pushes a new seed into the
theme, so the whole palette re-tints per song.
Source: `/root/work/repos/vivi-music/app/src/main/kotlin/com/music/vivi/MainActivity.kt`

```kotlin
var themeColor by rememberSaveable(stateSaver = ColorSaver) {
    mutableStateOf(selectedThemeColor)
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
                            .allowHardware(false)          // required: Palette cannot read HW bitmaps
                            .memoryCachePolicy(CachePolicy.ENABLED)
                            .diskCachePolicy(CachePolicy.ENABLED)
                            .networkCachePolicy(CachePolicy.ENABLED)
                            .crossfade(false)
                            .build()
                    )
                    themeColor = result.image?.toBitmap()?.extractThemeColor() ?: selectedThemeColor
                } catch (e: Exception) {
                    themeColor = selectedThemeColor
                }
            }
        } else {
            themeColor = selectedThemeColor
        }
    }
}
```

Persisting a `Color` across config changes:

```kotlin
val ColorSaver = object : Saver<Color, Int> {
    override fun restore(value: Int): Color = Color(value)
    override fun SaverScope.save(value: Color): Int = value.toArgb()
}
```

Rules for image-seeded color, all visible in the code above:
- `.allowHardware(false)` — `Palette` cannot read hardware bitmaps. Omitting this is the #1 reason
  extraction silently returns nothing.
- Do the decode + extraction on `Dispatchers.IO`, never on the composition.
- Always have a fallback seed. A bitmap can fail to load, or be entirely one flat color.
- Re-tinting the whole app per track is a strong effect. Wrap it in a user preference
  (`enableDynamicTheme` above) and animate the transition or it reads as flicker.

### 6.4 Live palette previews

`rememberDynamicColorScheme` is cheap enough to call once per swatch in a settings screen, which is how
vivi-music renders its theme picker:

```kotlin
val colorScheme = rememberDynamicColorScheme(
    seedColor = palette.seedColor,
    isDark = isSystemDark,
    style = if (palette.seedColor.toArgb() == 0xFF000000.toInt())
        PaletteStyle.Monochrome else PaletteStyle.TonalSpot
)
```

---

## 7. AMOLED / pure-black

There is no first-party AMOLED mode. Two verified approaches.

### 7.1 `ColorScheme.pureBlack(Boolean)` — vivi-music

Source: `/root/work/repos/vivi-music/app/src/main/kotlin/com/music/vivi/ui/theme/Theme.kt`

```kotlin
fun ColorScheme.pureBlack(apply: Boolean) =
    if (apply) copy(
        surface = Color.Black,
        background = Color.Black
    ) else this
```

Applied **after** the scheme is generated, and only when dark:

```kotlin
val colorScheme = remember(baseColorScheme, pureBlack, darkTheme) {
    if (darkTheme && pureBlack) {
        baseColorScheme.pureBlack(true)
    } else {
        baseColorScheme
    }
}
```

Derived from preference AND dark state — never from the preference alone:

```kotlin
val pureBlackEnabled by rememberPreference(PureBlackKey, defaultValue = false)
val pureBlack = remember(pureBlackEnabled, useDarkTheme) {
    pureBlackEnabled && useDarkTheme
}
```

The `pureBlack` boolean is then threaded through the UI (93 references in vivi-music) because
overriding two roles is not enough — containers still need to be forced:

```kotlin
// ui/component/AppNavigation.kt
val containerColor = if (pureBlack) Color.Black else MaterialTheme.colorScheme.surfaceContainer
val contentColor   = if (pureBlack) Color.White else MaterialTheme.colorScheme.onSurfaceVariant

// ui/player/MiniPlayer.kt
val backgroundColor =
    if (pureBlack && useDarkTheme) Color.Black else MaterialTheme.colorScheme.surfaceContainer
```

vivi-music keeps **two** independent preference keys: `PureBlackKey` (global) and
`PureBlackMiniPlayerKey` (mini player only).

### 7.2 LastChat's unconditional dark override

Source: `/root/work/repos/LastChat/app/src/main/java/me/rerere/rikkahub/ui/theme/Theme.kt`

```kotlin
private val AMOLED_DARK_BACKGROUND = Color(0xFF000000)

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
```

Note: LastChat applies this to **every** dark theme, not behind a toggle. That is a product decision,
not a recommendation — most apps should gate it.

### 7.3 materialkolor's built-in `isAmoled` — Tomato

```kotlin
val dynamicColorScheme = rememberDynamicColorScheme(
    seedColor = …,
    isDark = darkTheme,
    specVersion = if (blackTheme && darkTheme)
        ColorSpec.SpecVersion.SPEC_2021 else ColorSpec.SpecVersion.SPEC_2025,
    isAmoled = blackTheme && darkTheme
)
```

This is the cleanest option when you already depend on material-kolor: the library darkens the whole
surface ramp coherently instead of you patching two roles. Note Tomato deliberately falls back to
`SPEC_2021` in AMOLED mode.

### 7.4 Choosing

| Approach | When |
| --- | --- |
| `copy(surface, background)` | No materialkolor dependency; you accept patching container colors at call sites. |
| materialkolor `isAmoled = true` | You already use materialkolor. Best result, least call-site work. |
| Full custom dark scheme with black surfaces | You hand-author your palette anyway. |

Whatever you pick: **surfaceContainer* roles will still be grey** unless you override them too. A
"pure black" app with grey cards is the usual bug.

---

## 8. Contrast

### 8.1 There is no `contrastLevel` parameter

Verified from the constructor signatures in §4: `lightColorScheme`, `darkColorScheme`, and
`expressiveLightColorScheme` have **no** contrast parameter. Do not write it.

Android 14+ exposes a system contrast setting through `UiModeManager.getContrast()`
(0.0 / 0.5 / 1.0 = standard / medium / high). The only Compose path that reflects it automatically is
**dynamic color** — `dynamicLightColorScheme(context)` / `dynamicDarkColorScheme(context)` return a
scheme that already honours the user's contrast setting.

For non-dynamic apps: generate medium- and high-contrast schemes in Material Theme Builder, ship them
as separate `ColorScheme` values, read the system contrast yourself, and select. Whether any newer
material3 alpha adds first-class contrast support is **UNVERIFIED** — no release-note entry through
`1.5.0-alpha26`.

### 8.2 Ratio requirements

| Content | Minimum |
| --- | --- |
| Small text | **4.5:1** against its background |
| Large text (≥18pt regular, or ≥14pt bold) | **3:1** |
| UI elements / non-text (icons, borders, focus indicators) | **3:1** |
| Any Material role pair (`x` / `on-x`) | Guaranteed **≥3:1** by the token generator |

The 3:1 role-pair guarantee is a **floor for UI elements**, not a pass for small text. `onSurfaceVariant`
supporting text at 12sp on `surfaceContainer` is the pairing most likely to fail in a custom palette.

Contrast is also baked into component geometry — the progress indicator's 4dp track gap and 4dp stop
indicator, and the slider's 6dp thumb-track gap / 2dp inside corner / 4dp stop indicator, exist
"to meet modern contrast requirements". Do not remove them.

---

## 9. Design guidance — spending the roles

### 9.1 primary vs secondary vs tertiary

| Ask | Answer |
| --- | --- |
| "Is this the one action the screen exists for?" | `primary` |
| "Is this a supporting action in a dense area that must stay visible but not compete?" | `secondary` |
| "Does this need to break from the primary/secondary rhythm — a badge, a sticker, a one-off special element?" | `tertiary` |
| "Is this destructive?" | `error` |

**Tertiary is the hero-moment accent.** It is the only accent not already spoken for by the primary
action or its supporting cast, which makes it the natural carrier of a deliberate color break.

### 9.2 Accent vs container

Use the **filled accent** (`primary`) when the element must be unmissable and there is exactly one of
it in view. Use the **container** (`primaryContainer`) when there are several, when it is a surface
rather than a control, or when the element indicates state rather than invites action.

M3 Expressive leans hard on container roles — selected `ToggleButton`s, connected button groups,
segmented list items, highlighted cards. If your Expressive app uses only `primary` and `surface`,
it will look like a 2021 M3 app.

### 9.3 Picking a surface container tone

- `surfaceContainerLow` — expanded containers that must sit *below* the default; non-interactive cards.
- `surfaceContainer` — the default for most elements. Start here.
- `surfaceContainerHigh` — components that sit on top of `surfaceContainer`, or need focus.
- `surfaceBright` / `surfaceDim` — the ends of the ramp; use for large regions you want visibly
  separated from the page, not for individual small components.

Never simulate elevation with `surface.copy(alpha = …)` or a black overlay. The ramp *is* the
elevation system.

### 9.4 Expressive color anti-patterns

| Anti-pattern | Why it fails | Fix |
| --- | --- | --- |
| Applying a vivid accent to everything | "Without contrast, elements can blend together." Expression is relational — uniform expressiveness has no emphasis. | Budget one or two hero moments; hold baseline elsewhere. |
| Hardcoded hex anywhere in a screen composable | Breaks dark mode, dynamic color, AMOLED and contrast simultaneously. | Read `MaterialTheme.colorScheme.*`. |
| Using `Color.White` / `Color.Black` for text | Same, plus fails the pairing law. | `onSurface`, `onPrimary`, etc. |
| `surfaceVariant` used as the default container | Legacy M3 habit; the container ramp is the current system. | `surfaceContainer`. |
| Alpha-blended "elevation overlays" | M3 uses tonal containers, not overlays. | Move up the `surfaceContainer*` ramp. |
| Brand hex pasted next to a dynamic scheme | Clashes with a wallpaper-derived palette. | Harmonize the brand color toward the dynamic primary, or disable dynamic color. |
| `tertiary` used as a third generic accent everywhere | Destroys its function as the break. | Reserve it for badges/special elements. |
| Toolbar/nav given a vibrant scheme *and* the hero card given one | Two competing breaks read as noise. | One break per screen. |

---

## 10. Troubleshooting

### "Dark mode looks broken / text disappears in dark mode"
Cause: hardcoded colors. Search the codebase for `Color(0xFF`, `Color.White`, `Color.Black`,
`colorResource(`, and any `Color` constant declared outside `ui/theme/`.

```kotlin
// wrong
Text("Title", color = Color.Black)
Card(colors = CardDefaults.cardColors(containerColor = Color(0xFFF5F5F5)))

// right
Text("Title", color = MaterialTheme.colorScheme.onSurface)
Card(colors = CardDefaults.cardColors(
    containerColor = MaterialTheme.colorScheme.surfaceContainer))
```

Legitimate exceptions: `Color.Black` in an AMOLED override, and `Color.Transparent`. Everything else
should be a role.

### "Text on my colored card is unreadable"
Cause: wrong on-color. The pairing law is mechanical — match the family.

```kotlin
// wrong — onPrimary is designed for the *filled* primary, not the container
Surface(color = MaterialTheme.colorScheme.primaryContainer) {
    Text("…", color = MaterialTheme.colorScheme.onPrimary)
}

// right
Surface(color = MaterialTheme.colorScheme.primaryContainer) {
    Text("…", color = MaterialTheme.colorScheme.onPrimaryContainer)
}
```

Better: use `Surface(color = …, contentColor = …)` or a component that sets `contentColor` for you
(`Card`, `Button`, `ListItem` with `*Defaults.colors()`), so `LocalContentColor` is correct for the
whole subtree and children need no explicit `color` at all.

### "My seeded scheme has unreadable text"
Symptoms: correct role pairings, still low contrast. Causes, in order of likelihood:
1. Missing `specVersion = ColorSpec.SpecVersion.SPEC_2025` — you are getting old palette math.
2. `isDark` does not match the actual theme state. Check you are not passing a constant.
3. The seed is near-black or near-white, so the generator has no hue to work with. Detect this and
   switch to `PaletteStyle.Monochrome` (vivi-music does exactly this via
   `themeColor.toArgb() == 0xFF000000.toInt()`).
4. You are drawing `on*Container` small text on an Expressive light scheme — those roles are tone 30,
   which is lower contrast than baseline. Move the text to `onSurface` or raise its size/weight.
5. You applied an AMOLED override to `surface`/`background` but the text color came from a role
   generated against the *non*-black surface.

### "Dynamic color crashes on older devices"
`dynamicLightColorScheme` / `dynamicDarkColorScheme` are API 31+. The `Build.VERSION.SDK_INT >=
Build.VERSION_CODES.S` guard is mandatory, and must be inside the branch that *calls* the function,
not just around the `LocalContext.current` read.

### "Light and dark disagree about which is which"
The `darkTheme` boolean must drive **both** the light/dark builder choice *and* `isDark` on any
seeded scheme *and* `isAppearanceLightStatusBars` on the window insets controller. Three places, one
source of truth.

### "The theme doesn't update when the user changes the accent"
Wrap scheme construction in `remember(...)` keyed on **every** input that can change:

```kotlin
val colorScheme = remember(baseColorScheme, pureBlack, darkTheme) { … }
```

Missing a key means a stale scheme survives recomposition.

### "Pure black mode still shows grey cards"
Expected. `copy(surface = Black, background = Black)` does not touch `surfaceContainer*`. Either
override those roles too, use materialkolor's `isAmoled = true`, or thread a `pureBlack: Boolean`
down and branch at the component (vivi-music's approach).

### "Components ignore my ColorScheme"
Check you are not nesting a second `MaterialTheme` / `MaterialExpressiveTheme` inside the first with
default arguments. `MaterialExpressiveTheme`'s parameters are **nullable and mean "use the Expressive
default"** — they do *not* inherit from the ambient theme the way `MaterialTheme`'s do. A scoped
`MaterialExpressiveTheme` must pass `colorScheme = MaterialTheme.colorScheme` explicitly to inherit.

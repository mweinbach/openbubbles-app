# M3 Expressive buttons

Buttons, toggle buttons, button groups, split buttons, icon buttons, chips.

Every section: **signature → when to use → working code → pitfalls.**

Provenance is marked per block:

- `[CORPUS <repo>]` — lifted from a shipping open-source app; path given. Safe to copy.
- `[ANDROIDX]` — signature copied from androidx source or the rendered API reference. Not used by
  any app in the corpus; verify it resolves against your pinned artifact.
- `[UNVERIFIED]` — name is real, exact signature/value could not be confirmed. Do not paste blind.

Corpus repos: `/root/work/repos/{vivi-music,Tomato,LastChat,Med}`.
vivi-music is the heaviest button user: `ButtonGroupDefaults` appears **72 times across 12 files**,
`ToggleButton` **55 times**.

---

# 0. Breaking changes at 1.5.0-alpha25 / alpha26 — read first

Signatures verified against the androidx tree pinned at alpha26's terminal commit
`4d087bd6f764b8425a70fd94102f855aa382d94b`. Corpus code below this section was written against
1.4.0/alpha20-era artifacts; where a snippet is now source-incompatible it carries an inline
"changed in alpha25/26" note.

| Old (≤ alpha24) | New (alpha25/26) | Deprecation level | Source-breaking? |
| --- | --- | --- | --- |
| `TonalToggleButton(...)` | **`FilledTonalToggleButton(...)`** | WARNING | No — warns. Identical param list. |
| `ToggleButtonDefaults.shapes()` | **`ToggleButtonDefaults.shapesFor(buttonHeight: Dp)`** | **HIDDEN** | **Yes — hard fail** |
| `ToggleButtonDefaults.shapes(shape, pressedShape, checkedShape)` | **`ToggleButtonShapes(shape, pressedShape, checkedShape)`** | **HIDDEN** | **Yes — hard fail** |
| `SplitButtonLayout(...)` | **`SplitButton(...)`** | WARNING | No — warns. Identical param list. |
| `Modifier.animateWidth(is, compressionLimit: PaddingValues)` | **`animateWidth(is, compressionLimit: Dp)`** or **`animateWidth(is)`** | type change | **Yes** |
| `interface ButtonGroupScope` | **`sealed interface ButtonGroupScope`** | sealed | **Yes** if you implemented it |

New in alpha26: `ElevatedToggleButtonDefaults`, `FilledTonalToggleButtonDefaults`,
`OutlinedToggleButtonDefaults` — the per-variant color/shape defaults were split out of
`ToggleButtonDefaults`, which had its non-semantic shape properties cleaned up (Ia0a85).

New in alpha25: `OutlinedToggleButton` gained **smooth border stroke animations** (Icb433) — the
border now animates between checked/unchecked instead of snapping. No API change, no opt-out.

**Opt-in status.** Most of this family has graduated and needs **no** caller opt-in:
`Button` and all five variants, `ToggleButton`, `ElevatedToggleButton`, `FilledTonalToggleButton`,
`OutlinedToggleButton`, `ToggleButtonDefaults.shapesFor`, the three new `*ToggleButtonDefaults`
objects, and `SplitButton` (the string "Experimental" does not occur anywhere in `SplitButton.kt`
at the alpha26 SHA). Still gated at alpha26:

- **`ToggleButton` size variants** — `@ExperimentalMaterial3ExpressiveApi`. Verified from the
  androidx samples module: the base samples `ToggleButtonSample`, `ElevatedToggleButtonSample`,
  `FilledTonalToggleButtonSample`, `OutlinedToggleButtonSample`, `ToggleButtonWithIconSample`
  carry **no** opt-in, while `XSmallToggleButtonWithIconSample`,
  `MediumToggleButtonWithIconSample` and `LargeToggleButtonWithIconSample` each carry
  `@OptIn(ExperimentalMaterial3ExpressiveApi::class)`.
- **`ButtonGroup`** — contested, and genuinely unresolved (§5). The alpha22 release note says
  "Promote `ButtonGroup` APIs to stable (removes deprecated experimental ButtonGroup overloads)",
  yet `ButtonGroupSamples.kt` still carries 5 occurrences of `ExperimentalMaterial3ExpressiveApi`
  at androidx-main `360e8cba7ae6`. Keep the opt-in on `ButtonGroup` call sites; it is harmless if
  redundant.
- The 1-arg `ButtonDefaults.contentPaddingFor(buttonHeight)` (§1).

A stale `@OptIn` is a warning, not an error — unless you build with warnings-as-errors, in which
case removing the now-redundant ones is required.

---

# 1. The five-size button scale

M3 Expressive replaced "one button height" with five. `[ANDROIDX]` — literal dp from the
`Button*Tokens` files; these are exact.

| Size | ContainerHeight | IconSize | IconLabelSpace | Leading/TrailingSpace | Round shape | Square shape | Pressed shape | Outline width |
| --- | --- | --- | --- | --- | --- | --- | --- | --- |
| **XSmall** | **32dp** | 20dp | 8dp | 16dp | `CornerFull` | `CornerMedium` (12dp) | `CornerSmall` (8dp) | 1dp |
| **Small** | **40dp** | 20dp | 8dp | 16dp | `CornerFull` | `CornerMedium` (12dp) | `CornerSmall` (8dp) | 1dp |
| **Medium** | **56dp** | 24dp | 8dp | 24dp | `CornerFull` | `CornerLarge` (16dp) | `CornerMedium` (12dp) | 1dp |
| **Large** | **96dp** | 32dp | 12dp | 48dp | `CornerFull` | `CornerExtraLarge` (28dp) | `CornerLarge` (16dp) | 2dp |
| **XLarge** | **136dp** | 40dp | 16dp | 64dp | `CornerFull` | `CornerExtraLarge` (28dp) | `CornerLarge` (16dp) | 3dp |

Each token object also defines `SelectedContainerShapeRound` (= `CornerFull`) and
`SelectedContainerShapeSquare` (= same as `ContainerShapeSquare`).

Notice the outline width scales too — a 1dp border on a 136dp button looks like a hairline defect,
which is why XLarge outlined buttons carry 3dp.

## When to use each

| Size | Use for |
| --- | --- |
| **XSmall** 32dp | Dense inline actions inside list rows, cards, chips-adjacent contexts. Needs an expanded touch target (§10). |
| **Small** 40dp | The default. Everything that used to be a plain `Button`. `ButtonDefaults.MinHeight` = 40dp. |
| **Medium** 56dp | Primary action in a section, connected group members that carry an icon + label, bottom-sheet confirms. |
| **Large** 96dp | **Hero control.** One per screen. A play button, a start-timer button, a single call to action on an onboarding page. |
| **XLarge** 136dp | **Hero control, full-bleed.** A media transport button on a player screen, an emergency/primary action on a purpose-built screen. Essentially always alone. |

Large and XLarge are the expressive "hero moment" lever for buttons. Material's budget is **one or
two hero moments per product** — a row of three XLarge buttons is not expressive, it is broken
hierarchy. `[design-guidance §8]`

## The `*For(buttonHeight)` sizing mechanism

`[ANDROIDX]` — the Expressive way to size a button is to set the height and then feed the *same*
`Dp` into every accessory helper. Do not mix a Medium height with Small padding.

```kotlin
@Composable fun shapesFor(buttonHeight: Dp): ButtonShapes

fun contentPaddingFor(
    buttonHeight: Dp,
    hasStartIcon: Boolean = false,
    hasEndIcon: Boolean = false,
): PaddingValues

@ExperimentalMaterial3ExpressiveApi
fun contentPaddingFor(buttonHeight: Dp): PaddingValues   // 1-arg overload — re-gated in alpha21

fun iconSizeFor(buttonHeight: Dp): Dp
fun iconSpacingFor(buttonHeight: Dp): Dp

@Composable fun textStyleFor(buttonHeight: Dp): TextStyle
```

```kotlin
// [ANDROIDX] canonical Expressive medium button. No corpus app calls the *For helpers —
// verify these resolve on your artifact before shipping.
Button(
    onClick = { },
    modifier = Modifier.heightIn(ButtonDefaults.MediumContainerHeight),
    shapes = ButtonDefaults.shapesFor(ButtonDefaults.MediumContainerHeight),
    contentPadding = ButtonDefaults.contentPaddingFor(
        ButtonDefaults.MediumContainerHeight, hasStartIcon = true
    ),
) {
    Icon(Icons.Filled.Add, null, Modifier.size(ButtonDefaults.MediumIconSize))
    Spacer(Modifier.width(ButtonDefaults.MediumIconSpacing))
    Text("Medium", style = ButtonDefaults.textStyleFor(ButtonDefaults.MediumContainerHeight))
}
```

**Pitfalls**

- The 1-arg `contentPaddingFor(buttonHeight)` was **re-marked experimental in 1.5.0-alpha21**.
  Prefer the 3-arg overload, which is not gated.
- `ButtonDefaults.IconSize = 18.dp` is the *legacy baseline* value and is **smaller than every
  size-specific constant**, including `SmallIconSize = 20.dp`. If you write
  `Modifier.size(ButtonDefaults.IconSize)` on an Expressive button you get an undersized icon.
  Use `ButtonDefaults.SmallIconSize` / `MediumIconSize` / etc.
- `ExtraSmallIconSpacing = 4.dp` is the only literal in the spacing family; every other size
  resolves to its token's `IconLabelSpace` (8/8/8/12/16 dp).
- There is **no `SmallContainerHeight`** property. 40dp is `ButtonDefaults.MinHeight`.
- Corpus apps mostly ignore this mechanism and set `Modifier.height(52.dp)` / `.height(56.dp)`
  directly. That works, but you lose the matched padding, shape and type scale. Prefer the helpers
  on new code.

---

# 2. Button variants and emphasis order

Highest emphasis first. Exactly one highest-emphasis button per view.

| Composable | Emphasis | Use for |
| --- | --- | --- |
| `Button` (filled) | 1 — highest | The single most important action. Filled with `primary`. |
| `FilledTonalButton` | 2 | Important but not *the* action; also the safe "secondary primary" in a two-button row. |
| `ElevatedButton` | 3 | A filled-tonal that needs separation from a busy/patterned background. Use elevation for separation, not for emphasis. |
| `OutlinedButton` | 4 | Medium emphasis, needs a visible boundary. Pairs with a filled button. |
| `TextButton` | 5 — lowest | Dismissals, tertiary links, dialog "Cancel". |

`[ANDROIDX]` all five keep their baseline signatures. The `developer.android.com` button guide page
has **not** been updated for Expressive and documents only these five — that page is not a source
for anything in this file.

Color treatments available across the Expressive button family: **elevated, filled, tonal
(filled-tonal), outlined.** `[GOOGLE — SplitButton.md]`

---

# 3. `ButtonDefaults`

## Shape morphing — `shapes()`

`[ANDROIDX]`

```kotlin
@Composable fun shapes() = MaterialTheme.shapes.defaultButtonShapes

@Composable
fun shapes(shape: Shape? = null, pressedShape: Shape? = null): ButtonShapes
```

`ButtonShapes` = `{ shape, pressedShape }`. Passing a `shapes` (plural) argument to `Button` opts it
into the defining Expressive interaction: **the container morphs shape while pressed.**

`[CORPUS vivi-music]` `app/src/main/kotlin/com/music/vivi/WelcomeActivity.kt` — circle at rest,
20dp rounded rect on press:

```kotlin
@Composable
fun WelcomeExpressiveButton(
    text: String,
    onClick: () -> Unit,
    containerColor: Color,
    contentColor: Color,
    modifier: Modifier = Modifier,
    isOutlined: Boolean = false,
    showArrowOnly: Boolean = false
) {
    val shapes = ButtonDefaults.shapes(
        shape = CircleShape,
        pressedShape = RoundedCornerShape(20.dp)
    )
    Button(
        onClick = onClick,
        modifier = modifier,
        colors = ButtonDefaults.buttonColors(
            containerColor = containerColor,
            contentColor = contentColor
        ),
        shapes = shapes
    ) {
        AnimatedContent(
            targetState = showArrowOnly,
            transitionSpec = {
                (fadeIn(tween(220, delayMillis = 90)) +
                 scaleIn(initialScale = 0.92f, animationSpec = tween(220, delayMillis = 90)))
                    .togetherWith(fadeOut(tween(90)))
            },
            label = "buttonContentTransition"
        ) { arrowOnly ->
            if (arrowOnly) Icon(Icons.Rounded.ArrowForward, text, Modifier.size(32.dp))
            else Text(text, fontWeight = FontWeight.Bold, fontFamily = GoogleSansFlex, fontSize = 18.sp)
        }
    }
}
```

The zero-arg form is the cheapest possible expressive upgrade — it just opts into the theme's
default pressed shapes. `[CORPUS Tomato]`
`shared/src/commonMain/kotlin/org/nsh07/pomodoro/ui/settingsScreen/screens/AboutScreen.kt`:

```kotlin
FilledTonalIconButton(
    onClick = { uriHandler.openUri("https://discord.gg/MHhBQcxHu6") },
    shapes = IconButtonDefaults.shapes()
) {
    Icon(painterResource(Res.drawable.discord), contentDescription = "Discord",
        modifier = Modifier.size(24.dp))
}
```

**Pitfalls**

- `shape =` (singular) and `shapes =` (plural) are **different overloads**. Passing `shape` gets you
  a static container with no morph. If nothing animates on press, check which one you passed.
- `shapeByInteraction` is **not public** `[UNVERIFIED / likely internal]`. The morph is applied
  inside `Button`/`ToggleButton` from the `ButtonShapes` you hand it. You cannot drive it manually
  through that name.
- When you can't use `shapes =` (custom `Surface`, non-button), hand-roll it with
  `interactionSource.collectIsPressedAsState()` + `animateIntAsState` on
  `RoundedCornerShape(percent)`. See the shapes skill.

## Colors and elevation

`[ANDROIDX]`

```kotlin
@Composable fun buttonColors() = MaterialTheme.colorScheme.defaultButtonColors

@Composable
fun buttonColors(
    containerColor: Color = Color.Unspecified,
    contentColor: Color = Color.Unspecified,
    disabledContainerColor: Color = Color.Unspecified,
    disabledContentColor: Color = Color.Unspecified,
): ButtonColors

@Composable
fun buttonElevation(
    defaultElevation: Dp = FilledButtonTokens.ContainerElevation,
    pressedElevation: Dp = FilledButtonTokens.PressedContainerElevation,
    focusedElevation: Dp = FilledButtonTokens.FocusedContainerElevation,
    hoveredElevation: Dp = FilledButtonTokens.HoveredContainerElevation,
    disabledElevation: Dp = FilledButtonTokens.DisabledContainerElevation,
): ButtonElevation
```

Sibling color factories: `elevatedButtonColors()`, `filledTonalButtonColors()`,
`outlinedButtonColors()`, `textButtonColors()`, and `outlinedButtonBorder(enabled)`.

## Size constants worth memorising

```kotlin
val MinWidth = 58.dp
val MinHeight                                                     // = 40dp
val ExtraSmallContainerHeight = ButtonXSmallTokens.ContainerHeight   // 32dp
val MediumContainerHeight     = ButtonMediumTokens.ContainerHeight   // 56dp
val LargeContainerHeight      = ButtonLargeTokens.ContainerHeight    // 96dp
val ExtraLargeContainerHeight = ButtonXLargeTokens.ContainerHeight   // 136dp

val ExtraSmallIconSize = 20.dp; val SmallIconSize = 20.dp
val MediumIconSize = 24.dp; val LargeIconSize = 32.dp; val ExtraLargeIconSize = 40.dp

val IconSpacing = 8.dp            // ButtonSmallTokens.IconLabelSpace
val ExtraSmallIconSpacing = 4.dp
val MediumIconSpacing = 8.dp; val LargeIconSpacing = 12.dp; val ExtraLargeIconSpacing = 16.dp
```

`ButtonDefaults.IconSpacing` is the idiomatic gap between an icon and its label — corpus apps use it
constantly rather than a literal `8.dp`. `[CORPUS Tomato]`
`.../statsScreen/screens/LastWeekScreen.kt`:

```kotlin
Spacer(Modifier.width(ButtonDefaults.IconSpacing))
```

---

# 4. `ToggleButton` and friends

## Signature

`[ANDROIDX]` — opt-in in 1.4.0: `ExperimentalMaterial3ExpressiveApi`. **Stable since
1.5.0-alpha19 — no caller opt-in needed on alpha19+ for the base variants.** Only the *size*
variants (XSmall/Medium/Large toggle buttons) still require
`@OptIn(ExperimentalMaterial3ExpressiveApi::class)` at alpha26 (§0).

```kotlin
@Composable
fun ToggleButton(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    shapes: ToggleButtonShapes = ToggleButtonDefaults.shapesFor(ButtonDefaults.MinHeight),
    colors: ToggleButtonColors = ToggleButtonDefaults.toggleButtonColors(),
    elevation: ButtonElevation? = ButtonDefaults.buttonElevation(),
    border: BorderStroke? = null,
    contentPadding: PaddingValues = ButtonDefaults.contentPaddingFor(ButtonDefaults.MinHeight),
    interactionSource: MutableInteractionSource? = null,
    content: @Composable RowScope.() -> Unit,
)
```

Variants — names verified, individual signatures parallel with different `colors`/`elevation`/
`border` defaults `[UNVERIFIED for exact defaults]`:

| Composable | Colors factory (≤ alpha25) | Colors factory (alpha26+) |
| --- | --- | --- |
| `ToggleButton` | `ToggleButtonDefaults.toggleButtonColors()` | unchanged |
| **`FilledTonalToggleButton`** (was `TonalToggleButton`) | `ToggleButtonDefaults.filledTonalToggleButtonColors()` | `FilledTonalToggleButtonDefaults.filledTonalToggleButtonColors()` |
| `OutlinedToggleButton` | `ToggleButtonDefaults.outlinedToggleButtonColors()` + `outlinedToggleButtonBorder()` | `OutlinedToggleButtonDefaults.*` |
| `ElevatedToggleButton` | `ToggleButtonDefaults.elevatedToggleButtonColors()` | `ElevatedToggleButtonDefaults.*` |

> **Renamed in alpha25 (Icb433):** `TonalToggleButton` → **`FilledTonalToggleButton`**. Pure rename,
> the parameter list is byte-identical — verified by reading both declarations. The old name survives
> at `DeprecationLevel.WARNING` with a `ReplaceWith`, so old source still compiles with a warning.
> Two non-obvious deltas: the old `TonalToggleButton` carried a **declaration-level**
> `@ExperimentalMaterial3ExpressiveApi`, so callers needed an opt-in; `FilledTonalToggleButton` does
> not — the annotation is `@OptIn(...)` used internally only. And its defaults moved:

```kotlin
// [ANDROIDX] verified full signature at the alpha26 SHA
@Composable
public fun FilledTonalToggleButton(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    shapes: ToggleButtonShapes = ToggleButtonDefaults.shapesFor(ButtonDefaults.MinHeight),
    colors: ToggleButtonColors = FilledTonalToggleButtonDefaults.filledTonalToggleButtonColors(),
    elevation: ButtonElevation? = ButtonDefaults.filledTonalButtonElevation(),
    border: BorderStroke? = null,
    contentPadding: PaddingValues = ButtonDefaults.contentPaddingFor(ButtonDefaults.MinHeight),
    interactionSource: MutableInteractionSource? = null,
    content: @Composable RowScope.() -> Unit,
)
```

Icon-only siblings: `FilledIconToggleButton`, `FilledTonalIconToggleButton`,
`OutlinedIconToggleButton` (§8).

## `ToggleButtonDefaults`

`[ANDROIDX]` structure verified; some literal values `[UNVERIFIED]`.

```kotlin
ToggleButtonDefaults.MinHeight        // = ButtonSmallTokens.ContainerHeight = 40.dp
ToggleButtonDefaults.IconSpacing      // = 8.dp  — corpus-verified in use
ToggleButtonDefaults.IconSize         // = 20.dp
ToggleButtonDefaults.ContentPadding

// shapes
ToggleButtonDefaults.roundShape
ToggleButtonDefaults.squareShape

@Composable fun shapesFor(buttonHeight: Dp): ToggleButtonShapes   // alpha25+ — the ONLY factory
// plus per-size (extraSmall / medium / large / extraLarge) round/square/pressed/selected variants

// colors
ToggleButtonDefaults.toggleButtonColors()
ToggleButtonDefaults.elevatedToggleButtonColors()        // → ElevatedToggleButtonDefaults on alpha26
ToggleButtonDefaults.filledTonalToggleButtonColors()     // → FilledTonalToggleButtonDefaults on alpha26
ToggleButtonDefaults.outlinedToggleButtonColors()        // → OutlinedToggleButtonDefaults on alpha26
ToggleButtonDefaults.outlinedToggleButtonBorder()   // conditional BorderStroke by enabled/checked
```

## `shapes()` → `shapesFor()` / `ToggleButtonShapes(...)` — migration (alpha25, Icb433)

**This is the single hardest break in the button family.** It is *not* a rename.
`shapesFor` takes **one argument: the button height as a `Dp`** — not a shape triple. M3 Expressive
derives the shape set from the button's height, so the customizing path moved to the
**`ToggleButtonShapes(...)` constructor** instead.

```kotlin
// OLD — ≤ alpha24. Both overloads are now DeprecationLevel.HIDDEN
// ("maintained for binary compatibility"), i.e. invisible to Kotlin source.
// This does not warn on alpha25+ — it fails to compile.
val shapes = ToggleButtonDefaults.shapes()
val custom = ToggleButtonDefaults.shapes(shape = a, pressedShape = b, checkedShape = c)

// NEW — alpha25+
val shapes = ToggleButtonDefaults.shapesFor(ButtonDefaults.MinHeight)
val custom = ToggleButtonShapes(shape = a, pressedShape = b, checkedShape = c)
```

Verified `ReplaceWith` targets on the two hidden overloads: `ToggleButtonShapes()` and
`ToggleButtonShapes(shape, pressedShape, checkedShape)`. Note that the zero-arg replacement the
compiler suggests is the **constructor**, while the idiomatic default used by every
`ToggleButton` signature is `shapesFor(ButtonDefaults.MinHeight)`. There is **no zero-arg
`shapesFor()`** overload — you must pass a height.

Rules of thumb:

- Replacing a bare `ToggleButtonDefaults.shapes()` → `ToggleButtonDefaults.shapesFor(height)`,
  feeding the *same* `Dp` you gave the button (§1's `*For(buttonHeight)` mechanism).
- Replacing any call that passed shapes by name → `ToggleButtonShapes(...)`. Partial argument lists
  still work; the constructor's parameters are the same three names.
- `ButtonGroupDefaults.connected*ButtonShapes()` are **unaffected** — they return
  `ToggleButtonShapes` directly and were not deprecated. The connected-group recipes in §6B and §7
  need no change.

`ToggleButtonShapes` = `{ shape, pressedShape, checkedShape }` — three states, not two. That third
slot is what makes checked-ness read as a shape change and not just a color change.

Corpus-verified named parameters on `toggleButtonColors()`: `containerColor`, `contentColor`,
`checkedContainerColor`, `checkedContentColor`, `disabledContainerColor`, `disabledContentColor`.
On `outlinedToggleButtonColors()`: `checkedContainerColor`, `checkedContentColor`.

## When to use

- Any binary state that should read as a **button**, not a switch or checkbox: shuffle on/off,
  repeat, subscribe, bookmark, expand/collapse.
- As a **member of a connected group** — this is the dominant use in the corpus (§6).
- As a **plain action button** with `checked = false`. See the trick below.

## The `checked = false` trick

Widely repeated across vivi-music and Med: pass `checked = false`, do the work in
`onCheckedChange`, and add `semantics { role = Role.Button }`. You get the connected-group shapes
and the press morph on a control that has no toggle state.

`[CORPUS vivi-music]` `.../ui/screens/artist/ArtistScreen.kt`:

```kotlin
ToggleButton(
    checked = false,
    onCheckedChange = { playerConnection.playQueue(YouTubeQueue(radioEndpoint)) },
    modifier = Modifier
        .weight(1f)
        .height(52.dp)
        .semantics { role = Role.Button },
    shapes = ButtonGroupDefaults.connectedMiddleButtonShapes()
) {
    Icon(painterResource(R.drawable.radio), contentDescription = null,
        modifier = Modifier.size(20.dp))
    Spacer(Modifier.size(ToggleButtonDefaults.IconSpacing))
    Text(stringResource(R.string.radio), style = MaterialTheme.typography.labelMedium)
}
```

**Pitfall**: without `role = Role.Button` TalkBack announces it as a toggle with a checked state
that never changes. Always override the role when you use this trick.

## Custom asymmetric shapes

`shapes` and `checkedShape` accept any `Shape` — they are not restricted to the connected presets.

`[CORPUS Med]` `app/src/main/kotlin/com/fedeveloper95/med/SettingsActivity.kt:273-299` — pill top,
squared bottom when unchecked; full pill when checked (two more at `:308` and `:335`):

```kotlin
ToggleButton(
    checked = currentTheme == THEME_SYSTEM,
    onCheckedChange = {
        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
        onThemeChanged(THEME_SYSTEM)
        prefs.edit().putInt(PREF_THEME, THEME_SYSTEM).apply()
    },
    modifier = Modifier.fillMaxWidth().height(40.dp),
    // changed in alpha25 — see the migration note above. On alpha25+ this line becomes:
    //   shapes = ToggleButtonShapes(
    //       shape = RoundedCornerShape(topStartPercent = 50, topEndPercent = 50, bottomStartPercent = 15, bottomEndPercent = 15),
    //       checkedShape = RoundedCornerShape(50)
    //   ),
    shapes = ToggleButtonDefaults.shapes(
        shape = RoundedCornerShape(topStartPercent = 50, topEndPercent = 50, bottomStartPercent = 15, bottomEndPercent = 15),
        checkedShape = RoundedCornerShape(50)
    ),
    colors = ToggleButtonDefaults.toggleButtonColors(
        containerColor = Color.Transparent,
        checkedContainerColor = MaterialTheme.colorScheme.primary,
        contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
        checkedContentColor = MaterialTheme.colorScheme.onPrimary
    ),
    border = if (currentTheme == THEME_SYSTEM) null
             else BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
) {
    Row(horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Rounded.Smartphone, contentDescription = null, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(8.dp))
        Text(stringResource(R.string.settings_theme_system), fontFamily = GoogleSansFlex)
    }
}
```

Or force a pill in **all three** states — useful when the toggle lives inside another shaped
container and a morph would fight it. `[CORPUS Tomato]`
`androidApp/src/main/java/org/nsh07/pomodoro/ui/AppScreen.kt`:

```kotlin
shapes = ToggleButtonDefaults.shapes(CircleShape, CircleShape, CircleShape),

// changed in alpha25 — the positional 3-arg factory is DeprecationLevel.HIDDEN. Write:
shapes = ToggleButtonShapes(CircleShape, CircleShape, CircleShape),
```

## `FilledTonalToggleButton` (was `TonalToggleButton`)

`[CORPUS Tomato]` `.../statsScreen/screens/LastWeekScreen.kt:290-310` (duplicated in
`LastMonthScreen.kt`, `LastYearScreen.kt`) — expand/collapse with a rotating chevron.
Tomato is pinned pre-alpha25, so it still writes the old name; **on alpha25+ rename the call to
`FilledTonalToggleButton` — nothing else changes**, and you can drop any
`@OptIn(ExperimentalMaterial3ExpressiveApi::class)` that existed only for this composable:

```kotlin
val iconRotation by animateFloatAsState(if (breakdownChartExpanded) 180f else 0f)
TonalToggleButton(          // alpha25+: FilledTonalToggleButton(
    checked = breakdownChartExpanded,
    onCheckedChange = { breakdownChartExpanded = it },
    modifier = Modifier.align(Alignment.End)
) {
    Icon(
        painterResource(Res.drawable.arrow_down),
        stringResource(Res.string.more_info),
        modifier = Modifier.rotate(iconRotation)
    )
    Spacer(Modifier.width(ButtonDefaults.IconSpacing))
    Text(stringResource(Res.string.show_chart))
}
```

## Google-official toggle-button sources

`[ANDROIDX — material3 samples]`
`compose/material3/material3/samples/src/main/java/androidx/compose/material3/samples/ToggleButtonSamples.kt`
(androidx-main `360e8cba7ae6`, post-alpha26). These are the `@Sampled` functions embedded in the
API reference. Sample **names and opt-in state are verified by line**; the bodies were not read, so
do not paste a reconstruction — use them as the authoritative list of what exists and what is gated:

| Sample | Line | Caller opt-in required |
| --- | --- | --- |
| `ToggleButtonSample` | 47 | none |
| `ElevatedToggleButtonSample` | 55 | none |
| `FilledTonalToggleButtonSample` | 65 | none |
| `OutlinedToggleButtonSample` | 75 | none |
| `ToggleButtonWithIconSample` | 85 | none |
| `XSmallToggleButtonWithIconSample` | 102 | `@OptIn(ExperimentalMaterial3ExpressiveApi::class)` |
| `MediumToggleButtonWithIconSample` | 126 | `@OptIn(ExperimentalMaterial3ExpressiveApi::class)` |
| `LargeToggleButtonWithIconSample` | 150 | `@OptIn(ExperimentalMaterial3ExpressiveApi::class)` |

That split is the evidence behind §0's opt-in rule: base variants graduated, **size variants did
not**.

### Real Google-app idiom — one `buttons` lambda reused across orientations

`[GOOGLE — android/androidify]`
`feature/results/src/main/java/com/android/developers/androidify/customize/ToolSelector.kt`
(pinned `material3 = 1.5.0-alpha20`, HEAD `931cfdd68227`). The transferable bit is that the toggle
row is hoisted into a single `@Composable` val so horizontal↔vertical is a pure layout switch:

```kotlin
@Composable
fun ToolSelector(
    tools: List<CustomizeTool>,
    selectedOption: CustomizeTool,
    onToolSelected: (CustomizeTool) -> Unit,
    horizontal: Boolean,
    modifier: Modifier = Modifier,
) {
    val buttons = @Composable {
        tools.forEachIndexed { index, tool ->
            ToolSelectorToggleButton(
                modifier = Modifier,
                tool = tool,
                checked = selectedOption == tool,
                onCheckedChange = { onToolSelected(tool) },
            )
            if (index != tools.size - 1) {
                Spacer(Modifier.size(8.dp))
            }
        }
    }
    // ...HorizontalFloatingToolbar { buttons() } or VerticalFloatingToolbar { buttons() }
}

@Composable
private fun ToolSelectorToggleButton(
    tool: CustomizeTool,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    ToggleButton(
        modifier = modifier,
        checked = checked,
        onCheckedChange = onCheckedChange,
        shapes = ToggleButtonDefaults.shapes(          // ← alpha20 code; see below
            checkedShape = MaterialTheme.shapes.large,
        ),
        colors = ToggleButtonDefaults.toggleButtonColors(
            checkedContainerColor = MaterialTheme.colorScheme.onSurface,
            containerColor = MaterialTheme.colorScheme.surface,
        ),
    ) {
        Icon(
            painterResource(tool.icon),
            contentDescription = tool.displayName,
        )
    }
}
```

**This file does not compile on alpha25+ as written.** It is a clean worked example of the §4
migration — the single-named-argument form is exactly the case people miss:

```kotlin
// alpha25+
shapes = ToggleButtonShapes(checkedShape = MaterialTheme.shapes.large),
```

androidify's `@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)` at the top of that file is
also now redundant for the `ToggleButton` itself (it is still needed there for the toolbar APIs at
androidify's alpha20 pin, which graduated later at alpha22).

---

# 5. `ButtonGroup`

## Signature — and the incompatible change

`[ANDROIDX]` `ButtonGroup.kt`. Opt-in in 1.4.0: `ExperimentalMaterial3ExpressiveApi`.

> **Opt-in status is contested — keep the `@OptIn`.** Two sources disagree and we did not resolve
> which one describes the whole surface:
>
> - **The release notes say promoted.** alpha22 carries its own verbatim bullet, separate from the
>   floating-toolbar one: "Promote `ButtonGroup` APIs to stable (removes deprecated experimental
>   ButtonGroup overloads)." (An earlier revision of this file wrongly read the alpha22 "Graduate
>   Expressive `FloatingToolbar` APIs" bullet as the only one and concluded no promotion existed.)
> - **The source census says still gated.** `ButtonGroupSamples.kt` at androidx-main `360e8cba7ae6`
>   still carries **5** occurrences of `ExperimentalMaterial3ExpressiveApi` and **0** of
>   `ExperimentalMaterial3Api`.
>
> Most likely some members (the `ButtonGroupDefaults` shape helpers, or the overflow/menu-state
> types) stayed gated while the core composable graduated. Practical rule: **keep the opt-in on
> `ButtonGroup` code and trust the compiler at your pin** — a redundant opt-in costs a warning, a
> missing one costs a build. `ButtonGroupDefaults.connected*ButtonShapes()`, used by the hand-built
> groups in §6B/§7, is a different matter — those resolve without the Expressive opt-in in every
> corpus app.

```kotlin
@Composable
fun ButtonGroup(
    overflowIndicator: @Composable (ButtonGroupMenuState) -> Unit,
    modifier: Modifier = Modifier,
    @FloatRange(0.0) expandedRatio: Float = ButtonGroupDefaults.ExpandedRatio,
    horizontalArrangement: Arrangement.Horizontal = ButtonGroupDefaults.HorizontalArrangement,
    verticalAlignment: Alignment.Vertical = Alignment.Top,
    content: ButtonGroupScope.() -> Unit,
)
```

> **The single biggest 1.4.0-vs-alpha divergence in the whole Expressive surface.**
> The pre-alpha22 signature `ButtonGroup(modifier, horizontalArrangement, content)` — no
> `overflowIndicator` — existed in 1.4.0 and was **removed** in 1.5.0-alpha22 ("remove deprecated
> experimental APIs from 1.5.0-alpha"). `overflowIndicator` is now the **first, required,
> positional** parameter.
>
> If you get a "no overload matches" or "too many arguments" error on `ButtonGroup`, this is why.
> On 1.4.0, drop `overflowIndicator`. On alpha22+, it is mandatory — pass `{}` if you genuinely
> don't want an overflow affordance.

Parameter docs:

- `overflowIndicator` — composable shown at the end when the group overflows; receives
  `ButtonGroupMenuState`.
- `expandedRatio` — "the percentage, of the original width, that a child should expand when
  interacted with". Default `ButtonGroupDefaults.ExpandedRatio = 0.15f`.
- `content` — a `ButtonGroupScope` lambda; children should be tagged `Modifier.animateWidth`.

## `ButtonGroupScope`

`[ANDROIDX]`, verbatim:

```kotlin
// alpha25+ (I8ef39): now a SEALED interface — you can no longer implement it outside material3.
sealed interface ButtonGroupScope {
    fun Modifier.weight(
        @FloatRange(from = 0.0, fromInclusive = false) weight: Float
    ): Modifier

    // alpha25+: two real overloads. The 1-arg form is no longer deprecated —
    // it exists so compressionLimit can be omitted, and it resolves layout direction dynamically.
    fun Modifier.animateWidth(interactionSource: InteractionSource): Modifier

    fun Modifier.animateWidth(
        interactionSource: InteractionSource,
        compressionLimit: Dp,          // was PaddingValues through alpha24
    ): Modifier

    @Stable
    fun Modifier.align(alignment: Alignment.Vertical): Modifier

    fun clickableItem(
        onClick: () -> Unit,
        label: String,
        icon: (@Composable () -> Unit)? = null,
        weight: Float = Float.NaN,
        enabled: Boolean = true,
    )

    fun toggleableItem(
        checked: Boolean,
        label: String,
        onCheckedChange: (Boolean) -> Unit,
        icon: (@Composable () -> Unit)? = null,
        weight: Float = Float.NaN,
        enabled: Boolean = true,
    )

    fun customItem(
        buttonGroupContent: @Composable () -> Unit,
        menuContent: @Composable (ButtonGroupMenuState) -> Unit,
    )
}
```

`compressionLimit` bounds how far a neighbour is allowed to squeeze when a sibling expands. It was
**added in 1.5.0-alpha21** as a `PaddingValues` defaulting to `ButtonDefaults.ContentPadding`, with
the 1-arg `animateWidth` demoted to `DeprecationLevel.HIDDEN`.

> **Changed in alpha25 (I8ef39) — two breaks in one.** `Modifier.animateWidth` was **split into two
> real overloads**, and `compressionLimit` was **retyped `PaddingValues` → `Dp`**. It is no longer
> defaultable in a single signature; the 1-arg overload exists precisely so you can omit it, and it
> resolves the layout direction dynamically (which the old `PaddingValues` default could not).
>
> ```kotlin
> // OLD — ≤ alpha24
> Modifier.animateWidth(interactionSource, compressionLimit = PaddingValues(horizontal = 8.dp))
>
> // NEW — alpha25+
> Modifier.animateWidth(interactionSource, compressionLimit = 8.dp)
> // or omit it entirely and get dynamic layout-direction resolution:
> Modifier.animateWidth(interactionSource)
> ```
>
> The bare 1-arg `animateWidth(interactionSource)` — which every corpus example in §6A uses — is
> **correct on both old and new artifacts**. Only call sites that passed `compressionLimit` need
> touching.

`ButtonGroupScope` also became a **`sealed interface`** in the same change. If you implemented it
(to wrap or fake a button group in tests, say), that code no longer compiles and there is no
replacement — use the real `ButtonGroup`.

Three item kinds:

- `clickableItem(onClick, label, icon)` — the shorthand. Renders a standard button; you don't
  control its composable.
- `toggleableItem(checked, label, onCheckedChange, icon)` — the toggle shorthand.
- `customItem(buttonGroupContent, menuContent)` — you supply **both** the inline composable **and**
  a `DropdownMenuItem` fallback for when the group overflows. This is what every real app uses.

## `ButtonGroupDefaults`

`[ANDROIDX]`, verbatim:

```kotlin
object ButtonGroupDefaults {
    val ExpandedRatio = 0.15f

    val HorizontalArrangement: Arrangement.Horizontal =
        Arrangement.spacedBy(ButtonGroupSmallTokens.BetweenSpace)          // "standard" — ~12dp

    val ConnectedSpaceBetween: Dp = ConnectedButtonGroupSmallTokens.BetweenSpace   // "connected" — 2dp

    val connectedLeadingButtonShape: Shape
        @Composable get() =
            RoundedCornerShape(
                topStart = ShapeDefaults.CornerFull,
                bottomStart = ShapeDefaults.CornerFull,
                topEnd = ConnectedButtonGroupSmallTokens.InnerCornerCornerSize,
                bottomEnd = ConnectedButtonGroupSmallTokens.InnerCornerCornerSize,
            )

    val connectedLeadingButtonPressShape: Shape
    val connectedTrailingButtonShape: Shape
    val connectedTrailingButtonPressShape: Shape
    val connectedButtonCheckedShape = ShapeTokens.CornerFull
    val connectedMiddleButtonPressShape: Shape

    @Composable fun connectedLeadingButtonShapes(
        shape: Shape = connectedLeadingButtonShape,
        pressedShape: Shape = connectedLeadingButtonPressShape,
        checkedShape: Shape = connectedButtonCheckedShape,
    ): ToggleButtonShapes

    @Composable fun connectedMiddleButtonShapes(
        shape: Shape = ShapeDefaults.Small,
        pressedShape: Shape = connectedMiddleButtonPressShape,
        checkedShape: Shape = connectedButtonCheckedShape,
    ): ToggleButtonShapes

    @Composable fun connectedTrailingButtonShapes(
        shape: Shape = connectedTrailingButtonShape,
        pressedShape: Shape = connectedTrailingButtonPressShape,
        checkedShape: Shape = connectedButtonCheckedShape,
    ): ToggleButtonShapes

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable fun OverflowIndicator(
        menuState: ButtonGroupMenuState,
        modifier: Modifier = Modifier,
        enabled: Boolean = true,
        shape: Shape = IconButtonDefaults.filledShape,
        colors: IconButtonColors = IconButtonDefaults.filledIconButtonColors(),
        interactionSource: MutableInteractionSource? = null,
    )
}
```

Two variants, per `[GOOGLE — ButtonGroup.md]`:

- **Standard** — preserves each button's own shape. Default spacing **12dp**.
- **Connected** — overrides member shapes for cohesion: **2dp spacing, 8dp inner corners, fully
  rounded outer corners.**

Overflow has two modes: **menu** (collapse into a popup) and **wrap** (flow to a new line). Use one
of them rather than letting the group exceed its container.

---

# 6. Two ways to build a group — both are correct

There are two distinct approaches in the wild. Know which one you're writing.

## 6A. Calling the `ButtonGroup` composable

Use this when you want the **press-expands-and-squeezes-neighbours** interaction and/or **overflow
into a menu**. It costs you an `InteractionSource` per child and a `menuContent` per item.

`[CORPUS Tomato]`
`shared/src/androidMain/kotlin/org/nsh07/pomodoro/ui/timerScreen/TimerScreen.kt:446-530` — the most
complete `ButtonGroup` in the corpus. Three `customItem`s, each with an inline composable **and** a
`DropdownMenuItem` fallback, plus `animateWidth` per child:

```kotlin
val interactionSources = remember { List(3) { MutableInteractionSource() } }
ButtonGroup(
    overflowIndicator = { state ->
        ButtonGroupDefaults.OverflowIndicator(
            state,
            colors = IconButtonDefaults.filledTonalIconButtonColors(),
            modifier = Modifier.size(64.dp, 96.dp)
        )
    },
    modifier = Modifier.padding(16.dp)
) {
    customItem(
        {
            FilledIconToggleButton(
                onCheckedChange = { checked ->
                    onAction(TimerAction.ToggleTimer)
                    if (checked) haptic.performHapticFeedback(HapticFeedbackType.ToggleOn)
                    else haptic.performHapticFeedback(HapticFeedbackType.ToggleOff)
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && checked) {
                        permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                    }
                },
                checked = timerState.timerRunning,
                colors = IconButtonDefaults.filledIconToggleButtonColors(
                    checkedContainerColor = color,
                    checkedContentColor = onColor
                ),
                shapes = IconButtonDefaults.toggleableShapes(),
                interactionSource = interactionSources[0],
                modifier = Modifier
                    .size(width = 128.dp, height = 96.dp)
                    .animateWidth(interactionSources[0])
            ) {
                if (timerState.timerRunning) {
                    Icon(painterResource(Res.drawable.pause_large),
                        contentDescription = stringResource(Res.string.pause),
                        modifier = Modifier.size(32.dp))
                } else {
                    Icon(painterResource(Res.drawable.play_large),
                        contentDescription = stringResource(Res.string.play),
                        modifier = Modifier.size(32.dp))
                }
            }
        },
        { state ->                                   // the overflow-menu fallback for this item
            DropdownMenuItem(
                leadingIcon = {
                    Icon(painterResource(if (timerState.timerRunning) Res.drawable.pause else Res.drawable.play),
                        contentDescription = stringResource(if (timerState.timerRunning) Res.string.pause else Res.string.play))
                },
                text = {
                    Text(if (timerState.timerRunning) stringResource(Res.string.pause)
                         else stringResource(Res.string.play))
                },
                onClick = {
                    onAction(TimerAction.ToggleTimer)
                    state.dismiss()                  // ButtonGroupMenuState.dismiss()
                }
            )
        }
    )
    // ...second customItem: reset, with IconButtonDefaults.shapes() and a snackbar-with-undo
}
```

Same structure, smaller, in a `ListItem` trailing slot with **`overflowIndicator = {}`** and empty
`menuContent`s. `[CORPUS Tomato]`
`shared/src/commonMain/kotlin/org/nsh07/pomodoro/ui/settingsScreen/screens/AlarmSettings.kt:350`:

```kotlin
val interactionSources = remember { List(2) { MutableInteractionSource() } }

ButtonGroup(
    overflowIndicator = {},
    horizontalArrangement = Arrangement.spacedBy(8.dp)
) {
    customItem(
        buttonGroupContent = {
            FilledIconToggleButton(
                checked = vibrationPlaying,
                onCheckedChange = {
                    vibrationPlaying = it
                    if (it) vibrator.playWaveform(
                        settingsState.vibrationOnDuration,
                        settingsState.vibrationOffDuration,
                        settingsState.vibrationAmplitude
                    ) else vibrator.cancel()
                },
                interactionSource = interactionSources[0],
                modifier = Modifier
                    .size(52.dp, 40.dp)
                    .animateWidth(interactionSources[0])
            ) {
                if (vibrationPlaying) Icon(painterResource(Res.drawable.stop), null)
                else Icon(painterResource(Res.drawable.play), null)
            }
        },
        menuContent = {}
    )
    customItem(
        buttonGroupContent = {
            FilledTonalIconButton(
                onClick = { /* restore defaults */ },
                enabled = isPlus,
                shapes = IconButtonDefaults.shapes(),
                interactionSource = interactionSources[1],
                modifier = Modifier
                    .size(40.dp)
                    .animateWidth(interactionSources[1])
            ) {
                Icon(painterResource(Res.drawable.restore_default), null)
            }
        },
        menuContent = {}
    )
}
```

Mixed sizes and shapes inside one group — a circular icon, a capsule, a third action.
`[CORPUS vivi-music]` `app/src/main/kotlin/com/music/vivi/ui/screens/AlbumScreen.kt:492`:

```kotlin
val favoriteInteractionSource = remember { MutableInteractionSource() }
val playInteractionSource = remember { MutableInteractionSource() }
val shuffleInteractionSource = remember { MutableInteractionSource() }

ButtonGroup(
    overflowIndicator = { menuState ->
        ButtonGroupDefaults.OverflowIndicator(menuState = menuState)
    },
    modifier = Modifier
        .fillMaxWidth()
        .padding(horizontal = 32.dp),
    horizontalArrangement = Arrangement.spacedBy(16.dp),
    verticalAlignment = Alignment.CenterVertically
) {
    customItem(
        buttonGroupContent = {
            Surface(
                onClick = { database.query { update(albumWithSongs.album.toggleLike()) } },
                shape = CircleShape,
                color = MaterialTheme.colorScheme.surfaceVariant,
                interactionSource = favoriteInteractionSource,
                modifier = Modifier
                    .animateWidth(favoriteInteractionSource)
                    .size(48.dp)
            ) {
                Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                    Icon(
                        painter = painterResource(
                            if (albumWithSongs.album.bookmarkedAt != null) R.drawable.favorite
                            else R.drawable.favorite_border
                        ),
                        contentDescription = if (albumWithSongs.album.bookmarkedAt != null)
                            stringResource(R.string.saved) else stringResource(R.string.save),
                        modifier = Modifier.size(22.dp),
                        tint = if (albumWithSongs.album.bookmarkedAt != null)
                            MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        },
        menuContent = {}
    )
    // ...play (capsule Button), shuffle
}
```

**Pitfalls for 6A**

- Every `animateWidth(interactionSource)` needs the **same** `InteractionSource` you passed to the
  child button's `interactionSource =` parameter. Two different sources = no animation and no
  compile error. `remember { List(n) { MutableInteractionSource() } }` is the idiom.
- `animateWidth` only exists inside `ButtonGroupScope`. It will not resolve in a plain `Row`.
- `menuContent = {}` is legal and common when overflow can't happen (fixed 2-item group in a
  constrained slot). `overflowIndicator = {}` likewise.
- Tune `compressionLimit` only if neighbours squash too far. **On alpha25+ it is a `Dp`, not
  `PaddingValues`** — `animateWidth(source, compressionLimit = 8.dp)`. On alpha21–alpha24 it was
  `PaddingValues`. The 1-arg `animateWidth(source)` is the portable form and works on both.

## 6B. The hand-built connected group — what most apps actually ship

`Row`/`FlowRow` with `Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween)`, each child a
`ToggleButton` whose `shapes` come from `connectedLeadingButtonShapes()` /
`connectedMiddleButtonShapes()` / `connectedTrailingButtonShapes()`.

**This is the dominant pattern in the corpus by an order of magnitude.** You give up press-expansion
and overflow; you get full control over layout, weights, wrapping, colors and semantics. For a
segmented / radio-style control that is the right trade.

Use 6B when: the members are alternatives in one set (a segmented control), you need `weight(1f)`
distribution, you need `FlowRow` wrapping, or you need per-item colors.

Use 6A when: you want the press-squeeze interaction, or the action count is variable and might
overflow.

### The index-driven form — memorise this

`[CORPUS vivi-music]` `.../ui/screens/library/LibraryAlbumsScreen.kt` (identical code in
`LibraryArtistsScreen.kt`, `LibraryMixScreen.kt`, `LibraryPlaylistsScreen.kt`):

```kotlin
FlowRow(
    modifier = Modifier.padding(start = 12.dp, end = 12.dp),
    horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween),
    verticalArrangement = Arrangement.spacedBy(2.dp),
) {
    LibraryViewType.entries.forEachIndexed { index, type ->
        ToggleButton(
            checked = viewType == type,
            onCheckedChange = { viewType = type },
            shapes = when (index) {
                0 -> ButtonGroupDefaults.connectedLeadingButtonShapes()
                LibraryViewType.entries.lastIndex -> ButtonGroupDefaults.connectedTrailingButtonShapes()
                else -> ButtonGroupDefaults.connectedMiddleButtonShapes()
            },
            modifier = Modifier.semantics { role = Role.RadioButton },
        ) {
            Icon(
                painter = painterResource(
                    when (type) {
                        LibraryViewType.LIST -> R.drawable.list
                        LibraryViewType.GRID -> R.drawable.grid_view
                    }
                ),
                contentDescription = null,
            )
        }
    }
}
```

Handle the one-item case explicitly or a single button gets *trailing* shapes and looks wrong.
`[CORPUS vivi-music]` `.../ui/component/NewMenuComponents.kt`:

```kotlin
shapes = when {
    actions.size == 1 -> ButtonGroupDefaults.connectedLeadingButtonShapes()
    index == 0 -> ButtonGroupDefaults.connectedLeadingButtonShapes()
    index == actions.lastIndex -> ButtonGroupDefaults.connectedTrailingButtonShapes()
    else -> ButtonGroupDefaults.connectedMiddleButtonShapes()
}
```

### Full four-colour toggle palette on a connected group

`[CORPUS vivi-music]` `app/src/main/kotlin/com/music/vivi/ui/player/Queue.kt` — Shuffle / Repeat /
Radio, `FlowRow`, weight-distributed, 56dp tall:

```kotlin
FlowRow(
    modifier = Modifier
        .padding(horizontal = 12.dp, vertical = 8.dp)
        .fillMaxWidth(),
    horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween),
) {
    ToggleButton(
        checked = shuffleModeEnabled,
        onCheckedChange = { checked ->
            coroutineScope.launch {
                lazyListState.animateScrollToItem(if (shuffleModeEnabled) currentWindowIndex else 0)
            }.invokeOnCompletion {
                playerConnection.player.shuffleModeEnabled = checked
            }
        },
        enabled = !isListenTogetherGuest,
        shapes = ButtonGroupDefaults.connectedLeadingButtonShapes(),
        colors = ToggleButtonDefaults.toggleButtonColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer,
            contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
            checkedContainerColor = MaterialTheme.colorScheme.primaryContainer,
            checkedContentColor = MaterialTheme.colorScheme.onPrimaryContainer
        ),
        modifier = Modifier
            .weight(1f)
            .height(56.dp),
    ) {
        Icon(painterResource(R.drawable.shuffle), contentDescription = null,
            modifier = Modifier.size(22.dp))
        Spacer(Modifier.size(ToggleButtonDefaults.IconSpacing))
        Text(stringResource(R.string.shuffle), style = MaterialTheme.typography.labelMedium,
            maxLines = 1, overflow = TextOverflow.Ellipsis)
    }

    // repeat: identical, connectedMiddleButtonShapes(), same four colors
    // radio:  identical, connectedTrailingButtonShapes(), checked = false (action, not toggle)
}
```

### Three more shapes of the same recipe (vivi-music, all in-repo)

- **Over album art** — `.../ui/player/Queue.kt`: same `Row` + `ConnectedSpaceBetween`, but colors
  derived from an extracted background color at fixed alphas instead of scheme roles:
  `containerColor = TextBackgroundColor.copy(alpha = 0.2f)`,
  `checkedContainerColor = TextBackgroundColor.copy(alpha = 0.4f)`. Check contrast — see §12.
- **As dialog buttons** — `.../ui/screens/settings/AccountSettingsScreen.kt`: Cancel / Clear Data /
  Keep Data as one connected group, destructive middle tinted with
  `toggleButtonColors(contentColor = MaterialTheme.colorScheme.error)`, and the recommended option
  expressed as `checked = true` rather than a separate style.
- **Horizontally scrollable** — `.../vivimusic/changelog/changelogscreen.kt`: same index-driven
  shapes inside `Row(Modifier.horizontalScroll(rememberScrollState()))` instead of weights. Med's
  `OutlinedSingleSelectButtonGroup` (§7.2) is the extractable version of this.

**Pitfalls for 6B**

- `ConnectedSpaceBetween` is **2dp**. `ButtonGroupDefaults.HorizontalArrangement` is the
  *standard* (~12dp) spacing. Using the wrong one is the difference between a segmented control and
  three loose buttons.
- Always set `semantics { role = Role.RadioButton }` (single-select) or `Role.Button` (actions).
  Without it, a segmented control announces as three independent checkboxes.
- A connected group asserts the members are alternatives in **one set**. Don't connect unrelated
  actions just because you like the look.
- With `FlowRow`, a wrapped row's first item still carries "leading" shapes from its index — visually
  the second line starts with a rounded-left button, which is usually what you want, but check it.

---

# 7. Connected toggle-group recipes, verbatim

## 7.1 Single-select (radio) inside a `SegmentedListItem`

`[CORPUS Tomato]`
`shared/src/commonMain/kotlin/org/nsh07/pomodoro/ui/settingsScreen/components/ThemePickerListItem.kt`
(complete file after license):

```kotlin
package org.nsh07.pomodoro.ui.settingsScreen.components

// material3 imports: ButtonGroupDefaults, ExperimentalMaterial3ExpressiveApi, Icon,
// SegmentedListItem, Text, ToggleButton. Plus androidx.compose.ui.semantics.{Role, role, semantics}
// and androidx.compose.ui.util.fastForEachIndexed.

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ThemePickerListItem(
    theme: String,
    items: Int,
    index: Int,
    onThemeChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val themeMap: Map<String, Pair<DrawableResource, StringResource>> = remember {
        mapOf(
            "auto" to Pair(Res.drawable.brightness_auto, Res.string.system_default),
            "light" to Pair(Res.drawable.light_mode, Res.string.light),
            "dark" to Pair(Res.drawable.dark_mode, Res.string.dark)
        )
    }

    SegmentedListItem(
        onClick = {},
        leadingContent = {
            AnimatedContent(themeMap[theme]!!.first) {
                Icon(painter = painterResource(it), contentDescription = null)
            }
        },
        content = { Text(stringResource(Res.string.theme)) },
        supportingContent = {
            val options = themeMap.toList()
            val selectedIndex = options.indexOf(Pair(theme, themeMap[theme]))

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
                        Text(
                            stringResource(theme.second.second),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        },
        colors = listItemColors,
        shapes = segmentedListItemShapes(index, items),
        modifier = modifier
    )
}
```

## 7.2 Reusable single-select and multi-select helpers

`[CORPUS Med]`
`app/src/main/kotlin/com/fedeveloper95/med/elements/MainActivity/MedicineBottomSheet.kt:876-945`.
Single-select scrolls horizontally; multi-select distributes with `weight(1f)` and takes a
`Set<Int>`. A near-identical copy lives at `NotificationsSettingsActivity.kt:713-735`.

```kotlin
@Composable
fun OutlinedSingleSelectButtonGroup(
    options: List<String>,
    selectedIndex: Int,
    onOptionSelected: (Int) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween)
    ) {
        options.forEachIndexed { index, option ->
            OutlinedToggleButton(
                checked = selectedIndex == index,
                onCheckedChange = { onOptionSelected(index) },
                shapes = when (index) {
                    0 -> ButtonGroupDefaults.connectedLeadingButtonShapes()
                    options.lastIndex -> ButtonGroupDefaults.connectedTrailingButtonShapes()
                    else -> ButtonGroupDefaults.connectedMiddleButtonShapes()
                },
                colors = ToggleButtonDefaults.outlinedToggleButtonColors(
                    checkedContainerColor = MaterialTheme.colorScheme.primary,
                    checkedContentColor = MaterialTheme.colorScheme.onPrimary
                )
            ) {
                Text(
                    text = option,
                    fontFamily = GoogleSansFlex,
                    maxLines = 1,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

@Composable
fun MultiSelectConnectedButtonGroupWithFlowLayout(
    options: List<String>,
    selectedIndices: Set<Int>,
    onOptionSelected: (Int) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically
    ) {
        options.forEachIndexed { index, option ->
            OutlinedToggleButton(
                checked = selectedIndices.contains(index),
                onCheckedChange = { onOptionSelected(index) },
                modifier = Modifier.weight(1f),
                shapes = when (index) {
                    0 -> ButtonGroupDefaults.connectedLeadingButtonShapes()
                    options.lastIndex -> ButtonGroupDefaults.connectedTrailingButtonShapes()
                    else -> ButtonGroupDefaults.connectedMiddleButtonShapes()
                },
                colors = ToggleButtonDefaults.outlinedToggleButtonColors(
                    checkedContainerColor = MaterialTheme.colorScheme.primary,
                    checkedContentColor = MaterialTheme.colorScheme.onPrimary
                )
            ) {
                Text(
                    text = option,
                    fontFamily = GoogleSansFlex,
                    maxLines = 1,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}
```

**Note the missing semantics.** Med's helpers omit `role = Role.RadioButton` / `Role.Checkbox`.
Add it — the Tomato and vivi-music versions do.

## 7.3 Two-button confirm with transparent containers

`[CORPUS Med]` `app/src/main/kotlin/com/fedeveloper95/med/MainActivity.kt:594-627` — `ToggleButton`
used purely for the connected shape and press morph, with a transparent container and a manual
outline. The second button is identical with `connectedTrailingButtonShapes()` and
`contentColor = MaterialTheme.colorScheme.primary`:

```kotlin
Row(
    modifier = Modifier.fillMaxWidth(),
    horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween)
) {
    ToggleButton(
        checked = false,
        onCheckedChange = { onNo() },
        shapes = ButtonGroupDefaults.connectedLeadingButtonShapes(),
        colors = ToggleButtonDefaults.toggleButtonColors(
            containerColor = Color.Transparent,
            contentColor = MaterialTheme.colorScheme.onSurfaceVariant
        ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier.weight(1f)
    ) {
        Text(stringResource(R.string.no_action), fontFamily = GoogleSansFlex)
    }
    // ...yes button: connectedTrailingButtonShapes(), contentColor = primary
}
```

---

# 8. `SplitButton` (was `SplitButtonLayout`)

## The rename — RESOLVED

**`SplitButtonLayout` → `SplitButton`, landed in 1.5.0-alpha25 (Ic9840).** This was previously
marked `[UNVERIFIED]` in this file; it is now verified from the androidx tree pinned at alpha26's
terminal commit `4d087bd6f764b8425a70fd94102f855aa382d94b`, and it appears in the alpha25 release
notes as "Deprecated `SplitButtonLayout` Api to use `SplitButton` instead".

It is a **pure rename with an identical parameter list**. The old name survives at the default
`DeprecationLevel.WARNING` with a `ReplaceWith`, so old source still compiles — noisily.

- On **1.4.0 and 1.5.0-alpha ≤ alpha24**: only `SplitButtonLayout` resolves.
- On **alpha25+**: write `SplitButton`. `SplitButtonLayout` still resolves and still works.

```kotlin
// [ANDROIDX] verified at the alpha26 SHA
@Deprecated(
    message = "Renamed to SplitButton",
    replaceWith = ReplaceWith("SplitButton(leadingButton, trailingButton, modifier, spacing)"),
)
@Composable
public fun SplitButtonLayout(
    leadingButton: @Composable () -> Unit,
    trailingButton: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    spacing: Dp = SplitButtonDefaults.Spacing,
)

@Composable
fun SplitButton(
    leadingButton: @Composable () -> Unit,
    trailingButton: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    spacing: Dp = SplitButtonDefaults.Spacing,
)
```

> **Opt-in: none needed on alpha20+.** In 1.4.0 this was `ExperimentalMaterial3ExpressiveApi`.
> At the alpha26 SHA the string "Experimental" does **not occur anywhere** in `SplitButton.kt`, so
> neither `SplitButton` nor `SplitButtonDefaults.LeadingButton`/`TrailingButton` requires a caller
> opt-in. Any `@OptIn(ExperimentalMaterial3ExpressiveApi::class)` kept solely for a split button is
> now redundant.

## When to use

"Split buttons open a menu to give people more options related to an action." `[GOOGLE]` It is "a
specialized type of the connected button group."

Use it when there is **one obvious default action plus related variants**. Anatomy: leading button
(icon and/or label, does the default thing) + trailing button (always a menu icon, checkable, with
an animated icon).

If there is no clear default action, use a menu button or a button group instead — the leading
button must be worth pressing on its own.

## `SplitButtonDefaults` button constructors

`[ANDROIDX]`, verbatim:

```kotlin
@Composable
public fun LeadingButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    shapes: SplitButtonShapes = leadingButtonShapesFor(SmallContainerHeight),
    colors: ButtonColors = ButtonDefaults.buttonColors(),
    elevation: ButtonElevation? = ButtonDefaults.buttonElevation(),
    border: BorderStroke? = null,
    contentPadding: PaddingValues = leadingButtonContentPaddingFor(SmallContainerHeight),
    interactionSource: MutableInteractionSource? = null,
    content: @Composable RowScope.() -> Unit,
)

// TrailingButton has two overloads. The onClick one mirrors LeadingButton exactly
// (onClick, modifier, enabled, shapes = trailingButtonShapesFor(...), colors, elevation, border,
//  contentPadding = trailingButtonContentPaddingFor(...), interactionSource, content).
// The checkable one — the dropdown toggle, and the one you almost always want:

@Composable
public fun TrailingButton(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    shapes: SplitButtonShapes = trailingButtonShapesFor(SmallContainerHeight),
    colors: ButtonColors = ButtonDefaults.buttonColors(),
    elevation: ButtonElevation? = ButtonDefaults.buttonElevation(),
    border: BorderStroke? = null,
    contentPadding: PaddingValues = trailingButtonContentPaddingFor(SmallContainerHeight),
    interactionSource: MutableInteractionSource? = null,
    content: @Composable RowScope.() -> Unit,
)
```

Style variants, all `[ANDROIDX]`, differing only in `colors`/`elevation`/`border`:

| Function | `colors` | `elevation` | `border` |
| --- | --- | --- | --- |
| `TonalLeadingButton(onClick, …)` | `filledTonalButtonColors()` | `filledTonalButtonElevation()` | `null` |
| `TonalTrailingButton(checked, onCheckedChange, …)` | `filledTonalButtonColors()` | `filledTonalButtonElevation()` | `null` |
| `OutlinedLeadingButton(onClick, …)` | `outlinedButtonColors()` | `null` | `outlinedButtonBorder(enabled)` |
| `OutlinedTrailingButton(checked, onCheckedChange, …)` | `outlinedButtonColors()` | `null` | `outlinedButtonBorder(enabled)` |
| `ElevatedLeadingButton(onClick, …)` | `elevatedButtonColors()` | `elevatedButtonElevation()` | `null` |
| `ElevatedTrailingButton(checked, onCheckedChange, …)` | `elevatedButtonColors()` | `elevatedButtonElevation()` | `null` |

Note the asymmetry: **leading** takes `onClick`; **trailing** style-variants take
`checked`/`onCheckedChange` — the trailing button is the dropdown *toggle*, and its checked state is
real state, not decoration.

`AnimatedTrailingButton` appears in some doc summaries but could **not** be verified as public
`[UNVERIFIED / probably not public]`. The rotation animation in every real sample is done by
animating the icon inside `TrailingButton(checked = …)`.

## `SplitButtonDefaults` constants

`[ANDROIDX]`, verbatim:

```kotlin
public val LeadingIconSize: Dp = ButtonSmallTokens.IconSize
public val TrailingIconSize: Dp = SplitButtonSmallTokens.TrailingIconSize
public val Spacing: Dp = SplitButtonSmallTokens.BetweenSpace     // 2dp per design spec
public val OuterCornerSize: CornerSize = ShapeDefaults.CornerFull

// per-size inner corners at the junction, resting + pressed
ExtraSmallInnerCornerSize / SmallInnerCornerSize / MediumInnerCornerSize /
LargeInnerCornerSize / ExtraLargeInnerCornerSize
ExtraSmallInnerCornerSizePressed / SmallInnerCornerSizePressed / … / ExtraLargeInnerCornerSizePressed

// per-size heights — track the button scale 32/40/56/96/136
ExtraSmallContainerHeight / SmallContainerHeight / MediumContainerHeight /
LargeContainerHeight / ExtraLargeContainerHeight

// per-size trailing icon sizes
ExtraSmallTrailingButtonIconSize / … / ExtraLargeTrailingButtonIconSize
```

Helpers: `leadingButtonShapesFor(height)`, `trailingButtonShapesFor(height)`,
`leadingButtonContentPaddingFor(height)`, `trailingButtonContentPaddingFor(height)`,
`leadingButtonIconSizeFor(height)`, `trailingButtonIconSizeFor(height)`.

The literal dp behind each `SplitButton*Tokens` value is `[UNVERIFIED]` but almost certainly equals
the corresponding `Button*Tokens.ContainerHeight`.

## Complete working component — sort header

The only working split button in the whole corpus, and a good one: a generic
`inline fun <reified T : Enum<T>> SortHeader` where the leading button opens a dropdown and the
trailing button is a *checked* toggle that rotates a chevron 180°. Shows
`SplitButtonDefaults.TrailingIconSize`, `TooltipBox` + `TooltipAnchorPosition.Above`, and
`stateDescription` semantics on both branches.

`[CORPUS vivi-music]` `app/src/main/kotlin/com/music/vivi/ui/component/SortHeader.kt` (complete
file, 178 lines):

```kotlin
package com.music.vivi.ui.component

// material3 imports that matter here:
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.SplitButtonDefaults
import androidx.compose.material3.SplitButtonLayout   // alpha25+: androidx.compose.material3.SplitButton
import androidx.compose.material3.TooltipAnchorPosition
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.rememberTooltipState
// plus androidx.compose.animation.core.animateFloatAsState,
// androidx.compose.ui.graphics.graphicsLayer, and
// androidx.compose.ui.semantics.{contentDescription, semantics, stateDescription}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
inline fun <reified T : Enum<T>> SortHeader(
    sortType: T,
    sortDescending: Boolean,
    crossinline onSortTypeChange: (T) -> Unit,
    crossinline onSortDescendingChange: (Boolean) -> Unit,
    crossinline sortTypeText: (T) -> Int,
    modifier: Modifier = Modifier,
    showDescending: Boolean? = true,
) {
    var menuExpanded by remember { mutableStateOf(false) }

    val displayDescending = showDescending == true && sortType != PlaylistSongSortType.CUSTOM

    SplitButtonLayout(          // alpha25+: SplitButton( — same params, pure rename
        leadingButton = {
            SplitButtonDefaults.LeadingButton(
                onClick = { menuExpanded = !menuExpanded },
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                ),
                modifier = Modifier.widthIn(min = 120.dp)
            ) {
                Text(
                    text = stringResource(sortTypeText(sortType)),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.labelLarge,
                )
            }
        },
        trailingButton = {
            if (displayDescending) {
                val description = "Toggle sort order"
                TooltipBox(
                    positionProvider = TooltipDefaults.rememberTooltipPositionProvider(TooltipAnchorPosition.Above),
                    tooltip = { PlainTooltip { Text(description) } },
                    state = rememberTooltipState(),
                ) {
                    SplitButtonDefaults.TrailingButton(
                        checked = sortDescending,
                        onCheckedChange = { onSortDescendingChange(it) },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary
                        ),
                        modifier = Modifier.semantics {
                            stateDescription = if (sortDescending) "Descending" else "Ascending"
                            contentDescription = description
                        },
                    ) {
                        val rotation: Float by animateFloatAsState(
                            targetValue = if (sortDescending) 180f else 0f,
                            label = "Trailing Icon Rotation",
                        )
                        Icon(
                            imageVector = Icons.Filled.KeyboardArrowDown,
                            modifier = Modifier
                                .size(SplitButtonDefaults.TrailingIconSize)
                                .graphicsLayer {
                                    this.rotationZ = rotation
                                },
                            contentDescription = null,
                        )
                    }
                }
            } else {
                // Structurally identical, minus the TooltipBox, and driven by menuExpanded
                // instead of sortDescending:
                //   checked = menuExpanded, onCheckedChange = { menuExpanded = it }
                //   stateDescription = if (menuExpanded) "Expanded" else "Collapsed"
                //   contentDescription = "Show sort options"
                //   rotation = if (menuExpanded) 180f else 0f
                SplitButtonDefaults.TrailingButton(
                    checked = menuExpanded,
                    onCheckedChange = { menuExpanded = it },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    ),
                    modifier = Modifier.semantics {
                        stateDescription = if (menuExpanded) "Expanded" else "Collapsed"
                        contentDescription = "Show sort options"
                    },
                ) { /* same rotating KeyboardArrowDown as above */ }
            }
        },
        modifier = modifier.padding(vertical = 8.dp)
    )

    DropdownMenu(
        expanded = menuExpanded,
        onDismissRequest = { menuExpanded = false },
        modifier = Modifier.widthIn(min = 172.dp),
    ) {
        enumValues<T>().forEach { type ->
            DropdownMenuItem(
                text = {
                    Text(
                        text = stringResource(sortTypeText(type)),
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Normal,
                    )
                },
                trailingIcon = {
                    Icon(
                        painter = painterResource(
                            if (sortType == type) R.drawable.radio_button_checked
                            else R.drawable.radio_button_unchecked
                        ),
                        contentDescription = null,
                    )
                },
                onClick = {
                    onSortTypeChange(type)
                    menuExpanded = false
                },
            )
        }
    }
}
```

**Pitfalls**

- The `DropdownMenu` is a **sibling** of `SplitButton`, not a child. It anchors to the
  position in the composition, so keep it immediately after.
- `LeadingButton` and `TrailingButton` are `SplitButtonDefaults` members, not top-level composables.
- The trailing button's `checked` state is what you animate the chevron off. Do not add a separate
  `rememberSaveable` for rotation.
- Negative result worth knowing: LastChat has a **dead import** of `SplitButtonLayout` at
  `.../ui/pages/assistant/detail/AssistantImporter.kt:13` and actually renders two stacked
  `OutlinedButton`s. Split button adoption is genuinely low; don't assume it is available.
- Canonical Google source: `androidx material3 samples`,
  `compose/material3/material3/samples/src/main/java/androidx/compose/material3/samples/SplitButtonSamples.kt`
  — **13 references**, and the only split-button code anywhere in Google's sample repos
  (androidify, compose-samples, snippets, nowinandroid, platform-samples all have **zero**).
  The file's per-sample bodies were **not read**, so they are `[UNVERIFIED]` — go to source rather
  than copying a reconstruction. Its opt-in census is verified: 0 `ExperimentalMaterial3ExpressiveApi`,
  2 `ExperimentalMaterial3Api`, consistent with the graduation above.

---

# 9. Icon buttons

## Sizing and width options

`[ANDROIDX]` `IconButtonDefaults.kt` — declarations verbatim; the dp are token-indirected so
literals are `[UNVERIFIED]`.

```kotlin
val extraSmallIconSize: Dp = XSmallIconButtonTokens.IconSize
val smallIconSize: Dp      = SmallIconButtonTokens.IconSize
val mediumIconSize: Dp     = MediumIconButtonTokens.IconSize
val largeIconSize: Dp      = LargeIconButtonTokens.IconSize
val extraLargeIconSize: Dp = XLargeIconButtonTokens.IconSize

fun extraSmallContainerSize(widthOption: IconButtonWidthOption = IconButtonWidthOption.Uniform): DpSize
fun smallContainerSize(widthOption: IconButtonWidthOption = IconButtonWidthOption.Uniform): DpSize
fun mediumContainerSize(widthOption: IconButtonWidthOption = IconButtonWidthOption.Uniform): DpSize
fun largeContainerSize(widthOption: IconButtonWidthOption = IconButtonWidthOption.Uniform): DpSize
fun extraLargeContainerSize(widthOption: IconButtonWidthOption = IconButtonWidthOption.Uniform): DpSize
```

`IconButtonWidthOption` is the Expressive **width axis** — a second dimension on top of size:

```kotlin
val Narrow  = IconButtonWidthOption(0)
val Uniform = IconButtonWidthOption(1)   // square-ish, the default
val Wide    = IconButtonWidthOption(2)
```

`Wide` gives a pill-shaped icon button — useful as the emphasized member of a toolbar row.

## Shapes

28 shape properties, `[ANDROIDX]`. Three base shapes plus a 5 × 5 matrix:

```kotlin
val standardShape: Shape; val filledShape: Shape; val outlinedShape: Shape

// {extraSmall|small|medium|large|extraLarge} ×
// {Round|Square|Pressed|SelectedRound|SelectedSquare} + "Shape"
val mediumRoundShape: Shape           // ...one of 25, all following that pattern
```

```kotlin
fun shapes(shape: Shape? = null, pressedShape: Shape? = null): IconButtonShapes
fun shapes(): IconButtonShapes
fun toggleableShapes(
    shape: Shape? = null,
    pressedShape: Shape? = null,
    checkedShape: Shape? = null,
): IconToggleButtonShapes
fun toggleableShapes(): IconToggleButtonShapes
```

## Overloads

`[ANDROIDX]` parameter names verified; types/defaults on the `shapes` overloads `[UNVERIFIED]`:

1. `IconButton(onClick, modifier, enabled, colors, interactionSource, shape, content)`
2. `IconButton(onClick, shapes, modifier, enabled, colors, interactionSource, content)` ← **Expressive morphing overload**
3. `IconToggleButton(checked, onCheckedChange, modifier, enabled, colors, interactionSource, shape, content)`
4. `IconToggleButton(checked, onCheckedChange, shapes, modifier, enabled, colors, interactionSource, content)`

`FilledIconButton`, `FilledTonalIconButton`, `OutlinedIconButton`, `FilledIconToggleButton`,
`FilledTonalIconToggleButton`, `OutlinedIconToggleButton` follow the same 2×(`shape` | `shapes`)
pattern — individual signatures `[UNVERIFIED]`.

## Working code

`[ANDROIDX]` canonical sizing pattern:

```kotlin
FilledIconButton(
    onClick = { },
    shapes = IconButtonDefaults.shapes(),
    modifier = Modifier.size(IconButtonDefaults.largeContainerSize(IconButtonWidthOption.Wide)),
) {
    Icon(Icons.Filled.Edit, null, Modifier.size(IconButtonDefaults.largeIconSize))
}
```

`[CORPUS vivi-music]` `app/src/main/kotlin/com/music/vivi/WelcomeActivity.kt` — custom press morph on
a tonal icon button:

```kotlin
val languageButtonShapes = IconButtonDefaults.shapes(
    shape = CircleShape,
    pressedShape = RoundedCornerShape(12.dp)
)
FilledTonalIconButton(
    onClick = { /* open locale settings */ },
    modifier = Modifier
        .size(languageButtonWidth)
        .alpha(languageButtonAlpha),
    shapes = languageButtonShapes,
    colors = IconButtonDefaults.filledTonalIconButtonColors(
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        contentColor = MaterialTheme.colorScheme.onSurfaceVariant
    )
) {
    Icon(
        painter = painterResource(id = R.drawable.language),
        contentDescription = "Language",
        modifier = Modifier.size(28.dp)
    )
}
```

For `FilledIconToggleButton` with `IconButtonDefaults.toggleableShapes()` and
`filledIconToggleButtonColors(checkedContainerColor, checkedContentColor)`, see the Tomato
`ButtonGroup` example in §6A.

**Pitfalls**

- Icon-only buttons **must** carry a `contentDescription` on the `Icon`, or a `contentDescription`
  in `semantics` on the button. There is no visible label to fall back on.
- `IconButtonDefaults.shapes()` (zero-arg) is a one-line expressive upgrade for any existing icon
  button. Do it everywhere before you start hand-rolling morphs.
- `toggleableShapes()` has three slots (`shape`, `pressedShape`, `checkedShape`); `shapes()` has two.
  Using `shapes()` on a toggle loses the checked shape change.

---

# 10. Chips — what actually changed in Expressive

Chips were **updated**, not new. `[GOOGLE — Chip.md]` gives the four-type decision procedure:

> "Does the chip represent an action (assist chip) or filter results (filter chip)? Is its content
> generated by the product (suggestion chip), or by the person using the product (input chip)?"

Elevated styles exist for each and are appropriate "when placed against a background that needs
protection, such as an image."

## The two Expressive-era API changes

1. **Shape morphing overloads — added 1.5.0-alpha18.** `FilterChip`, `ElevatedFilterChip` and
   `InputChip` gained overloads that take shape-morphing parameters, matching the button press-morph
   behaviour. Exact parameter names on those overloads are `[UNVERIFIED]` — no corpus app uses them.
2. **`horizontalSpacing` → `horizontalArrangement`.** Renamed in **1.5.0-alpha15**; the old
   `horizontalSpacing` overload was **removed in alpha16**. New members:
   `FilterChipDefaults.horizontalArrangement`, `AssistChipDefaults.horizontalArrangement`.
   If you have `horizontalSpacing = ...` in a chip call it will not compile on alpha16+.

No corpus app uses either change — chip usage across all four repos is plain baseline:

`[CORPUS LastChat]` `.../ui/pages/setting/SettingProviderPage.kt:1745`:

```kotlin
FilterChip(
    selected = tag.id in selectedTagIds,
    onClick = {
        val newSelection = if (tag.id in selectedTagIds) selectedTagIds - tag.id
                           else selectedTagIds + tag.id
        onUpdateSelectedTagIds(newSelection)
    },
    label = { Text(tag.name) }
)
```

**Chips vs. connected toggle groups.** A `FlowRow` of `FilterChip`s and a connected `ToggleButton`
group look superficially similar and mean different things:

- **Chips** = an open, growable set of independent filters/tags. Order and count are data-driven.
- **Connected group** = a fixed, closed set of mutually-related alternatives (segmented control).

Do not build a segmented control out of chips, and do not build a tag cloud out of a connected
group.

All chips "include accessibility features like configurable touch targets and RTL-friendly layouts."
`[GOOGLE — Chip.md]`

---

# 11. Design guidance and anti-patterns per family

## Buttons

- Five sizes, four color treatments (elevated, filled, tonal, outlined). `[GOOGLE]`
- Round and square resting shapes, with **shape morph on press**. Buttons "transform shape and size
  to achieve eye-catching springy animation effects." `[GOOGLE — Wear OS blog]`
- Motion tier: buttons are small components → use the **Fast** spring tier. `[CANON]`
- **Don't**: "Smaller shapes can result in essential actions looking less important." Do not shrink
  a primary action into visual insignificance in pursuit of shape variety. `[CANON]`
- **Don't**: put two filled `Button`s side by side. Pair filled + tonal, or filled + outlined.

## Button groups

- "Button groups organize buttons and add interactions between them." `[GOOGLE]`
- Standard = individual shapes preserved, 12dp spacing. Connected = shapes overridden, 2dp spacing,
  8dp inner corners, fully rounded outer corners.
- Supports fixed, flexible and mixed button sizes for responsiveness.
- **Don't** let a group exceed its container — use **menu** or **wrap** overflow. `[GOOGLE]`
- **Don't** connect unrelated actions. A connected group asserts the members are alternatives in one
  set.

## Split button

- One obvious default action + related variants. "A specialized type of the connected button group."
- The trailing button "spins and changes shape when activated" and is checkable with an animated
  icon. Its checked state is **real state**, expose it via `stateDescription`.
- 2dp default spacing; configurable inner corner size at the junction.
- **Don't** use it when there's no clear default action — use a menu button or a button group.

## Toggle buttons

- The defining Expressive interaction: the container **morphs shape** on press and on check
  (round ↔ square), and container/content colors switch between primary and tonal roles.
- **Don't** use `ToggleButton` where a `Switch` belongs (settings rows with a persistent boolean and
  no other affordance) or where a `Checkbox` belongs (a form field in a list of independent options).

## Icon buttons

- Five sizes × three width options (Narrow / Uniform / Wide) — a large matrix. Pick one size for a
  toolbar row and stick to it.
- **Don't** mix filled and standard icon buttons in the same row without a hierarchy reason.

## Global

> "Don't compromise your product's core functionality for visual flourishes. **No amount of emotion
> can compensate for a lack of clarity.**" `[GOOGLE — design.google]`

- **Budget hero moments**: one or two per product. A screen of Large/XLarge buttons is not a hero
  moment, it is noise.
- **Expression is relational**: a shape reads as emphatic only by breaking from the surrounding
  shape style. Applying the expressive treatment uniformly annihilates the emphasis.
- **No overshoot on color or opacity** — that is what the effects springs are for.
- **Reserve the bouncy scheme** for hero moments and key interactions.

---

# 12. Accessibility

## Touch targets

`[GOOGLE — Material accessibility guidance]`

- **Touch targets: at least 48 × 48 dp** ("a physical size of about 9mm, regardless of screen size").
- **Pointer targets: at least 44 × 44 dp** for mouse/stylus.
- **Spacing: touch targets separated by 8dp or more.**

This constrains the size scale directly. An **XSmall button is 32dp tall — 16dp short of the
minimum.** It must carry an expanded touch target:

```kotlin
// [DERIVED] expand the touch target without changing the visual size
Button(
    onClick = { },
    modifier = Modifier
        .heightIn(ButtonDefaults.ExtraSmallContainerHeight)   // 32dp visual
        .minimumInteractiveComponentSize(),                    // ≥48dp target
)
```

`Modifier.minimumInteractiveComponentSize()` is applied automatically by most Material clickables;
verify it survives if you replace the container with a custom `Surface`.

Connected groups use **2dp internal spacing**, which is below the 8dp separation guidance. That is
intentional in the spec (the members read as one control), but it means adjacent targets are
genuinely close — do not additionally shrink members below the default height.

## Semantics on toggles

Always set the role, and set a `stateDescription` when "checked/unchecked" doesn't describe the
state in the user's language:

```kotlin
// role only — connected single-select
modifier = Modifier.semantics { role = Role.RadioButton }

// role only — ToggleButton used as a plain action (checked = false trick)
modifier = Modifier.semantics { role = Role.Button }

// [CORPUS vivi-music] stateDescription where checked/unchecked is meaningless
modifier = Modifier.semantics {
    stateDescription = if (sortDescending) "Descending" else "Ascending"
    contentDescription = "Toggle sort order"
}

// [CORPUS vivi-music] expanded/collapsed on a menu toggle
modifier = Modifier.semantics {
    stateDescription = if (menuExpanded) "Expanded" else "Collapsed"
    contentDescription = "Show sort options"
}
```

Multi-select groups should use `Role.Checkbox`, not `Role.RadioButton`. Med's
`MultiSelectConnectedButtonGroupWithFlowLayout` (§7.2) omits the role entirely — fix that when you
copy it.

## Semantics on button groups

- The group itself does not automatically announce as a group. If the members only make sense
  together, wrap them and set `Modifier.semantics { isTraversalGroup = true }` plus a
  `contentDescription` on the container.
- With `ButtonGroup` + overflow, the overflow menu items are separate nodes; make sure the
  `menuContent` `DropdownMenuItem`s carry the same labels as the inline buttons so the two
  presentations announce identically.
- Icon-only group members need `contentDescription` on each `Icon`. In vivi-music's
  `LibraryAlbumsScreen` list/grid toggle, the icons pass `contentDescription = null` and rely on the
  `Role.RadioButton` + selection state — that is the weakest link in an otherwise good pattern. Add
  descriptions.

## Contrast

- Small text **4.5:1**; large text (14pt bold / 18pt regular+) **3:1**.
- Material color roles guarantee "a minimum of 3:1 color contrast" for **all** pairs. Use
  accent-with-On-accent and container-with-On-container pairings rather than improvising —
  especially in the translucent-over-album-art pattern (§6B), where `TextBackgroundColor.copy(alpha
  = 0.2f)` containers can fall below the floor. Check that case explicitly.

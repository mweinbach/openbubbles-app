# M3 Expressive Review Checklist

The full audit. **A** is the mechanical process, **B** the checklist, **C** the 10-item fast version,
**D** the report shape. Severity labels are the ones in this skill's `SKILL.md`:

- **[Broken]** — won't compile, crashes, missing opt-in, wrong artifact version, contrast < 4.5:1,
  touch target < 48dp, missing accessible name.
- **[Wrong]** — contradicts Material guidance in a way users feel.
- **[Weak]** — works, but leaves expression or clarity on the table.
- **[Note]** — preference, or a valid choice worth flagging so it's deliberate.

Do not inflate severity. A review that files eleven [Broken]s to look thorough is a review nobody reads
twice.

---

# A. How to run the audit

## A.1 Scope first, in one question

| User said | Scope |
| --- | --- |
| "review this screen" | that file + the theme it inherits + any custom component it calls |
| "review this PR / diff" | changed hunks + the theme + every file the diff imports from |
| "audit the app" / "why doesn't it feel expressive" | full sweep, §A.3 grep pass first |
| "is this ready to ship" | quick pass (§C), then deep-dive only where the quick pass fails |

If it's ambiguous, assume **screen + theme** and say so in the report's first line. Do not silently
audit 400 files when the user pasted one composable.

## A.2 Reading order — theme, then structure, then leaves

This order is not arbitrary. Most "it doesn't feel like Material 3 Expressive" complaints resolve at
step 1 or 2, and a finding filed against a screen that is actually a theme bug wastes the fix.

1. **Build files.** `libs.versions.toml`, `app/build.gradle.kts`. Establish the pinned material3
   version and whether opt-ins are global. Everything in §B.1 depends on knowing this number.
2. **`Theme.kt` / `Color.kt` / `Type.kt` / `Shape.kt`.** Is the root `MaterialExpressiveTheme`? Is
   `motionScheme` passed? Are `shapes` and `typography` real or defaulted?
3. **The app entry point** (`MainActivity`, `App()`, root `NavDisplay`/`NavHost`). Where the theme is
   applied, where the nav container lives, whether `SharedTransitionLayout` wraps the graph.
4. **Screen scaffolds.** One level down: what's in the top bar, bottom bar, FAB slot. Nav-container
   duplication and toolbar/nav-bar stacking are visible here and nowhere else.
5. **Leaf composables.** Only now read individual buttons, list items, indicators.
6. **Custom components** (`ui/component/`, `ui/components/`). This is where hardcoded `tween`,
   hardcoded colors, and reinvented Material components accumulate.

## A.3 The grep sweep

Run these from the module root before reading anything but the theme. Each returns a candidate set,
not a finding — every hit needs a look. `--type kotlin` covers `.kt` and `.kts`.

```bash
# --- versions, opt-ins, artifacts (§B.1) ---
rg -n 'material3|graphics-shapes|compose-bom|adaptive' --glob '*.toml' --glob '*.gradle*'
rg -n 'optIn|freeCompilerArgs|-opt-in=' --glob '*.gradle*'
rg -n --type kotlin 'ExperimentalMaterial3ExpressiveApi|ExperimentalMaterial3Api'
rg -n --type kotlin '^import androidx\.compose\.material\.'      # M2 leaking in; material3 won't match
rg -n --type kotlin 'ExperimentalMaterial3ComponentOverrideApi|LocalButtonOverride|ComponentOverride'

# --- theme (§B.2) ---
rg -n --type kotlin 'MaterialExpressiveTheme|MaterialTheme\('
rg -n --type kotlin 'motionScheme\s*='
rg -n --type kotlin 'expressiveDarkColorScheme'                  # does not exist — always a finding
rg -n --type kotlin 'LocalColorScheme|LocalTypography|LocalShapes'   # pre-alpha15 composition locals

# --- color (§B.3) ---
rg -n --type kotlin 'Color\(0x|Color\.White|Color\.Black|Color\.Gray|Color\.Red' --glob '!**/theme/**'
rg -n --type kotlin 'contentColor\s*=|containerColor\s*=|tint\s*='    # check on-* pairing at each site
rg -n --type kotlin 'surfaceVariant|copy\(alpha\s*='             # legacy container / M2 elevation overlay
rg -n --type kotlin 'pureBlack|amoled|rememberDynamicColorScheme|seedColor'

# --- typography (§B.4) ---
rg -n --type kotlin 'fontSize\s*=\s*[0-9]|lineHeight\s*=|letterSpacing\s*='
rg -n --type kotlin 'FontWeight\.(Bold|SemiBold|Medium|ExtraBold)' --glob '!**/theme/**'
rg -n --type kotlin 'Emphasized'                                 # inverse check: is the emphasized scale used at all?
rg -n --type kotlin 'FontVariation|variationSettings'

# --- shape (§B.5) ---
rg -n --type kotlin 'RoundedCornerShape\(|CircleShape'
rg -c --type kotlin 'MaterialTheme\.shapes'                      # inverse check: near-zero is the smell
rg -n --type kotlin 'MaterialShapes\.|\.toShape\(|\.toPath\(|Morph\('

# --- motion (§B.6) ---
rg -n --type kotlin 'tween\(|LinearEasing|FastOutSlowInEasing|LinearOutSlowInEasing|CubicBezierEasing|durationMillis'
rg -n --type kotlin 'spring\(|Spring\.DampingRatio|Spring\.Stiffness'
rg -n --type kotlin 'motionScheme\.(fast|default|slow)(Spatial|Effects)Spec'
rg -n --type kotlin -B2 'animateColorAsState|fadeIn\(|fadeOut\('     # which spec family? (§B6.2)
rg -n --type kotlin 'SharedTransitionLayout|sharedBounds|sharedElement'
rg -n --type kotlin 'infiniteRepeatable|rememberInfiniteTransition'
rg -n --type kotlin 'ANIMATOR_DURATION_SCALE|areAnimatorsEnabled|reduceMotion|MotionPolicy'

# --- components (§B.7) ---
rg -n --type kotlin 'ButtonGroup|ToggleButton|SplitButtonLayout'
rg -n --type kotlin 'FloatingActionButtonMenu|ToggleFloatingActionButton|ExtendedFloatingActionButton|SmallFloatingActionButton'
rg -n --type kotlin 'FloatingToolbar|BottomAppBar\(|FlexibleBottomAppBar'
rg -n --type kotlin 'LoadingIndicator|CircularProgressIndicator|LinearProgressIndicator|WavyProgressIndicator'
rg -n --type kotlin -A3 'stroke\s*=\s*Stroke\(|trackStroke\s*='  # px vs dp (§B7.9)
rg -n --type kotlin 'ListItem\(|segmentedShapes|segmentedColors'
rg -n --type kotlin 'TopAppBar\(|MediumTopAppBar|LargeTopAppBar|LargeFlexibleTopAppBar|MediumFlexibleTopAppBar'

# --- navigation & adaptive (§B.8) ---
rg -n --type kotlin 'NavigationBar\(|ShortNavigationBar|NavigationRail\(|WideNavigationRail|NavigationSuiteScaffold'
rg -n --type kotlin 'screenWidthDp|smallestScreenWidth|isTablet|LocalConfiguration'
rg -n --type kotlin 'WindowSizeClass|currentWindowAdaptiveInfo|windowWidthSizeClass'
rg -n --type kotlin 'BackHandler|predictivePopTransitionSpec|PredictiveBack'
rg -c --type kotlin 'rememberSaveable'                           # inverse check for state-on-resize

# --- accessibility (§B.10) ---
rg -n --type kotlin 'contentDescription\s*=\s*null'
rg -n --type kotlin 'IconButton|IconToggleButton'
rg -n --type kotlin 'semantics\s*\{|stateDescription|isTraversalGroup|traversalIndex|CustomAccessibilityAction'
rg -n --type kotlin 'minimumInteractiveComponentSize|\.size\([0-9]+\.dp\)'
rg -n --type kotlin 'Role\.(Button|RadioButton|Checkbox|Switch|Tab)'
rg -n --type kotlin 'gapSize\s*=\s*0|stopSize\s*=\s*0|thumbTrackGapSize'

# --- performance (§B.11) ---
rg -n --type kotlin 'RoundedPolygon\(|Morph\(|\.toShape\(' | rg -v 'remember'
rg -n --type kotlin 'Modifier\.(alpha|offset|scale|rotate)\('    # value overloads; prefer graphicsLayer/lambda
rg -n --type kotlin -A6 'items\(|itemsIndexed\('                 # lambdas captured per item
rg -n --type kotlin 'animateContentSize'
```

## A.4 Diff review vs full audit

**Diff review.** Changed set from `git diff --name-only <base>...HEAD -- '*.kt' '*.kts'`, context from
`git diff -U8 <base>...HEAD`. Then run the §A.3 sweep restricted to those files. Always read the theme
even if unchanged — a diff adding `LinearWavyProgressIndicator` to an app themed with bare
`MaterialTheme` is a finding *against the diff*, because the diff assumed motion that isn't there. Do not
file on unchanged code unless the diff makes it newly load-bearing; when you do, label it **[Note]
pre-existing**. Judge the diff against its own stated intent first, guidance second.

**Full audit.** Whole sweep, then read in §A.2 order. Cap the report: anything repeating across ≥3 files
becomes **one** finding with a representative `file:line` plus a count ("hardcoded `tween(300)` at 14
sites; representative: `ui/player/Player.kt:212`"). A 60-line list with 40 duplicates is unreadable.

## A.5 What you cannot conclude from code alone

Say so rather than guessing. **Rendered contrast under dynamic color** — you can check hardcoded pairs,
not what a user's wallpaper produces; flag the *pattern* (hand-picked `on` color), not a computed ratio.
**Whether a morph feels right** — but whether it's bound to a state change *is* checkable. **TalkBack
ordering** beyond obvious `traversalIndex` gaps; recommend a manual pass. **Frame timing** — recommend
recomposition-count tooling or a Macrobenchmark; never assert a jank number.

---

# B. The checklist

## B.1 Setup & versions

Read `m3-expressive → references/setup-and-versions.md` §5 (graduation timeline) before filing
anything here. The version number determines whether an API exists and whether it needs opt-in.

**B1.1 [Broken] Expressive API used at a version where it doesn't exist or isn't opted in.**
Find: `rg -n 'material3' --glob '*.toml'` for the pin, then `rg -n --type kotlin 'ExperimentalMaterial3ExpressiveApi'`.
Why: on **1.4.0** the whole Expressive surface exists but is gated by
`@OptIn(ExperimentalMaterial3ExpressiveApi::class)` (app bars/search use `ExperimentalMaterial3Api`). On
**1.5.0-alpha19+** most graduated — but `MaterialShapes` and `LoadingIndicator` had their promotions
**reverted** and are still gated at alpha26. `ShortNavigationBar`/`WideNavigationRail`/
`ModalWideNavigationRail` are the opposite case: they carry **zero experimental annotations** in the
shipped alpha26 source, yet no graduation note was ever published for them. That is a documentation
gap, not a gate — treat them as stable and **do not** file a missing-opt-in finding against them.
Trust the compiler at the project's pin.
Fix: at alpha26 the more common defect is a *stale* opt-in, not a missing one. Where opt-ins are
genuinely needed, a global opt-in beats scattered annotations.
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

**B1.2 [Broken] `androidx.graphics:graphics-shapes` missing while `MaterialShapes`/`Morph`/`RoundedPolygon` are used.**
Find: `rg -n 'graphics-shapes' --glob '*.toml' --glob '*.gradle*'` vs `rg -n --type kotlin 'MaterialShapes\.|Morph\(|RoundedPolygon'`.
Why: `MaterialShapes.*` values *are* `RoundedPolygon`s from that artifact, and `Morph`/`RoundedPolygon`
import from `androidx.graphics.shapes`. material3 pulls it transitively, so it compiles today and breaks
on a dependency bump; depending on it directly is the documented practice.
Fix: `implementation("androidx.graphics:graphics-shapes:1.1.0")`.

**B1.3 [Broken] BOM silently downgrading an intended alpha pin.**
Find: `rg -n 'compose-bom|platform\(' --glob '*.gradle*'`.
Why: `compose-bom:2026.08.00` maps material3 → **1.4.0** and never ships alphas. If the project wants
`1.5.0-alphaNN`, the explicit pin must come **after** the platform line, otherwise the BOM wins and
every alpha-only API fails to resolve.
Fix:
```kotlin
implementation(platform("androidx.compose:compose-bom:2026.08.00"))
implementation("androidx.compose.material3:material3:1.5.0-alpha26")   // explicit override, after the BOM
```

**B1.4 [Broken] `androidx.compose.material` (M2) mixed with material3.**
Find: `rg -n --type kotlin '^import androidx\.compose\.material\.'`
Why: M2 `MaterialTheme`, `Text`, `Surface`, `Icon` do not read the M3 color/type/shape/motion system.
An M2 `Text` inside an M3 screen renders with M2 defaults and ignores `MaterialTheme.typography`
entirely. Common leaks: `androidx.compose.material.pullrefresh`, `material.icons` (that one is fine —
`androidx.compose.material.icons` is the icon artifact, not M2 components).
Fix: swap the import to `androidx.compose.material3.*`. Keep `material.icons.*` and
`material.ripple` only where there's no M3 equivalent.

**B1.5 [Wrong] `ButtonGroup` called with the pre-alpha22 signature.**
Find: `rg -n --type kotlin -A2 'ButtonGroup\('`
Why: 1.4.0 had `ButtonGroup(modifier, horizontalArrangement, content)`; alpha22 **removed** it in
favour of `ButtonGroup(overflowIndicator, modifier, expandedRatio, horizontalArrangement,
verticalAlignment, content)`. This is the single biggest 1.4.0-vs-alpha divergence and the most likely
"wrong overload" compile error.
Fix at alpha22+:
```kotlin
ButtonGroup(
    overflowIndicator = { menuState -> ButtonGroupDefaults.OverflowIndicator(menuState) },
) {
    clickableItem(onClick = ::onSort, label = "Sort")
    clickableItem(onClick = ::onFilter, label = "Filter")
}
```

**B1.6 [Wrong] Removed / renamed APIs still referenced.**
Find: `rg -n --type kotlin 'ComponentOverride|LocalColorScheme|LocalTypography|isAtTop|rememberModalBottomSheetState|rememberStandardBottomSheetState|animateWidth\('`
Why, per the release notes: `ComponentOverride` APIs **removed** in alpha25 (I3784b — the removal
spanned alpha23 → alpha25) with no migration path;
`MaterialTheme` collapsed to one composition local in alpha15 (`MaterialTheme.LocalMaterialTheme.current`);
`isAtTop` → `isAtStart` in alpha16; `rememberModalBottomSheetState`/`rememberStandardBottomSheetState`
deprecated for `rememberBottomSheetState` in alpha20/21; `Modifier.animateWidth` **inverted at
alpha25** — the 1-arg overload `animateWidth(interactionSource)` was re-added (it resolves layout
direction dynamically), and `compressionLimit` was retyped from `PaddingValues` to **`Dp`**. Passing
`compressionLimit = PaddingValues(...)` is now the compile error.
Fix: apply the rename; for `ComponentOverride`, the mechanism is gone — the code must be restructured.

**B1.7 [Note] `SplitButtonLayout` is correct; `SplitButton` does not exist.** **Correction —
this reverses what earlier versions of this checklist said.** There is no `SplitButtonLayout` →
`SplitButton` rename. Verified in `compose/material3/material3/api/current.txt` at androidx HEAD
`360e8cba`, 2026-08-14 (post-alpha26): `SplitButtonLayout(leadingButton, trailingButton, modifier,
spacing)` is the only top-level split-button composable, it carries **no `@Deprecated` annotation**,
and a top-level `SplitButton(` has zero matches in any api txt file. Do **not** flag
`SplitButtonLayout` as an outdated name, and do not "migrate" it. What alpha25 did deprecate here
are the `SplitButtonDefaults.leadingButtonShapes(CornerSize)` / `trailingButtonShapes(CornerSize)`
helpers, superseded by `*ShapesFor(buttonHeight: Dp)` — flag those instead. (That the release note
"Deprecated `SplitButtonLayout` API" meant those helpers is **inference, not fact**.)

**B1.8 [Note] Adaptive artifacts on two version trains.** `material3-adaptive-navigation-suite` is
versioned with the **material3** group (1.5.0-alpha26), not with `material3.adaptive`
(**1.3.0 stable**, 2026-08-12 — if you are still pinning `1.3.0-rc01`, drop the suffix). One shared
`adaptiveVersion` in `libs.versions.toml` is a latent resolution failure.

---

## B.2 Theme

**B2.1 [Wrong] Root is bare `MaterialTheme`, not `MaterialExpressiveTheme`.**
Find: `rg -n --type kotlin 'MaterialTheme\(|MaterialExpressiveTheme\('`
Why: **the highest-yield check in this document.** `MaterialTheme` defaults to `MotionScheme.standard()`;
`MaterialExpressiveTheme` defaults to `MotionScheme.expressive()`. Every component reading
`MaterialTheme.motionScheme` — 21 by default — silently runs standard springs. Nothing crashes, nothing
warns, and the app just feels like baseline M3. No amount of screen work fixes it.
Fix:
```kotlin
MaterialExpressiveTheme(
    colorScheme = colorScheme,
    typography = AppTypography,
    shapes = AppShapes,
    motionScheme = MotionScheme.expressive(),
    content = content,
)
```

**B2.2 [Wrong] `MaterialExpressiveTheme` used but `motionScheme` omitted.**
Find: `rg -n --type kotlin -A6 'MaterialExpressiveTheme\(' | rg -v 'motionScheme'`
Why: all four params are **nullable**, and `null` means "use the Expressive default" — for motion that
*is* `MotionScheme.expressive()`, so this is usually harmless. File as [Wrong] only when the tree also
contains a plain `MaterialTheme` (where `null`-vs-inherit semantics differ); otherwise **[Note]: pass it
explicitly so the intent is legible.**

**B2.3 [Wrong] Theme not applied at the root, or applied twice.**
Find: `rg -n --type kotlin 'MaterialExpressiveTheme\(|MaterialTheme\(|AppTheme\('` and check every hit's
enclosing function.
Why: two competing themes in one tree means half the screen reads one `colorScheme` and half another.
Classic causes: a theme call inside a screen composable; a theme inside a `Dialog`/`Popup` lambda that
re-derives dark mode independently; a `@Preview` theme leaking into production code.
Fix: one theme at the activity/root content. **Scoped expressive theming is legitimate** when
retrofitting — but it must inherit explicitly rather than resetting:
```kotlin
MaterialExpressiveTheme(
    colorScheme = MaterialTheme.colorScheme,
    typography = MaterialTheme.typography,
    shapes = MaterialTheme.shapes,
    motionScheme = MotionScheme.expressive(),
) { FloatingActionButtonMenu(/* … */) }
```

**B2.4 [Weak] `shapes` / `typography` never passed — the theme is colors only.**
Find: `rg -n --type kotlin -A6 'MaterialExpressiveTheme\('` and look for `shapes =` / `typography =`.
Why: defaults are fine, but an `AppShapes`/`AppTypography` object defined and never wired in means every
component uses baseline values while the design tokens sit unused. Check both directions — defined but
unpassed, and passed but never actually differing from the default.

**B2.5 [Weak] Hardcoded `RoundedCornerShape` / `Color` / `TextStyle` in screens instead of tokens.**
Find: `rg -n --type kotlin 'RoundedCornerShape\(|TextStyle\(' --glob '!**/theme/**'`
Why: each is a place the theme cannot reach — the screen stops responding to a shape-scale change, a
typography swap, or dark mode. This is how an app ends up with 40 slightly different corner radii.
Fix: `MaterialTheme.shapes.large`, `MaterialTheme.colorScheme.surfaceContainer`,
`MaterialTheme.typography.titleLargeEmphasized`. A genuine one-off belongs in `Shape.kt`/`Type.kt` as a
named token, not inline.

**B2.6 [Note] Theme derived from unremembered state.** A `colorScheme` recomputed on every recomposition
flashes on rotation. Check the derivation sits inside `remember(darkTheme, dynamicColor, seed)`.

---

## B.3 Color

**B3.1 [Broken] `expressiveDarkColorScheme()` referenced.**
Find: `rg -n --type kotlin 'expressiveDarkColorScheme'`
Why: **it does not exist** — verified absent from `ColorScheme.kt` on `androidx-main` as of alpha26.
`expressiveLightColorScheme()` exists; the dark counterpart is plain `darkColorScheme()`.
Fix — the official sample:
```kotlin
MaterialExpressiveTheme(
    colorScheme = if (isSystemInDarkTheme()) darkColorScheme() else expressiveLightColorScheme()
) { content() }
```

**B3.2 [Broken] Hardcoded colors in screen code.**
Find: `rg -n --type kotlin 'Color\(0x|Color\.White|Color\.Black|Color\.Gray|Color\.Red' --glob '!**/theme/**'`
Why: breaks dark mode, dynamic color, AMOLED and contrast simultaneously in one edit. `Color.White` text
on a card that goes light in light mode is the canonical disappearing-text bug.
Fix: `MaterialTheme.colorScheme.onSurface` / `.onPrimary` / `.onPrimaryContainer` per the pairing law.
Brand colors that must survive dynamic color belong in the theme, harmonized, not inline.

**B3.3 [Broken] Text contrast below 4.5:1 (3:1 for large text and UI elements).**
Find: every site where `containerColor` / `background` / `Surface(color=)` is set alongside a
`contentColor` or `Text(color=)` that isn't the matching `on*` role.
How: compute the WCAG ratio rather than eyeballing —
```python
def lum(c):  # c = (r,g,b) 0..255
    f = lambda v: (v/255/12.92) if v/255 <= 0.03928 else ((v/255+0.055)/1.055)**2.4
    r,g,b = map(f,c); return 0.2126*r+0.7152*g+0.0722*b
ratio = lambda a,b: (max(lum(a),lum(b))+0.05)/(min(lum(a),lum(b))+0.05)
```
Thresholds: small text **4.5:1**; large text (≥18pt regular / ≥14pt bold) **3:1**; non-text UI **3:1**.
Material role pairs guarantee **≥3:1** — a floor for UI elements, **not** a pass for small text. The
pairing most likely to fail in a custom palette is `onSurfaceVariant` at 12sp on `surfaceContainer`.
Fix: use the matching `on*` role; if the palette is hand-authored, regenerate it rather than nudging one
hex.

**B3.4 [Wrong] Wrong `on-*` pairing.**
Find: `rg -n --type kotlin 'containerColor\s*=|contentColor\s*=|tint\s*='` and check each pair.
Why: the pairing rule is structural — accent with its `on` role, container with its `onContainer` role.
`onPrimary` on a `primaryContainer` background is a guaranteed contrast failure in at least one of
light/dark. Also flag `contentColorFor()` being bypassed where it would have been correct.
Fix: `Surface(color = colorScheme.primaryContainer, contentColor = colorScheme.onPrimaryContainer)`, or
let `contentColorFor(containerColor)` do it.

**B3.5 [Wrong] Seeded / image-derived scheme with no readability guard.**
Find: `rg -n --type kotlin 'rememberDynamicColorScheme|seedColor|PaletteStyle|Palette\.from'`
Why: an album-art or user-picked seed can produce a scheme whose `onSurfaceVariant` fails against
`surfaceContainer`. Also check `specVersion` — materialkolor defaults to older palette math; the
Expressive-era spec is `ColorSpec.SpecVersion.SPEC_2025`, and omitting it gives visibly duller results.
Fix: pass `specVersion = ColorSpec.SpecVersion.SPEC_2025`, and verify the two or three text roles the
app actually uses against a sample of extreme seeds (near-black art, neon art, desaturated art).

**B3.6 [Wrong] AMOLED mode implemented by overriding `surface` and `background` only.**
Find: `rg -n --type kotlin 'pureBlack|amoled|AMOLED|Color\.Black'`
Why: the `surfaceContainer*` ramp stays grey. A "pure black" app with grey cards is the standard bug —
overriding two roles is not enough, because every card, sheet, nav bar and app bar reads
`surfaceContainer`/`surfaceContainerHigh`.
Fix: use a generator that darkens the whole ramp (materialkolor `isAmoled = true`), or `copy()` all seven
roles — `surface`, `background`, and `surfaceContainerLowest`/`Low`/(default)/`High`/`Highest`. Verify it
is gated on `darkTheme && preference`, never the preference alone.

**B3.7 [Weak] `surfaceVariant` as the default container, or alpha-blended elevation overlays.**
Why: both are M2/legacy-M3 habits. The current system is the tonal ramp — `surfaceContainerLow` for
non-interactive cards, `surfaceContainer` as the default, `surfaceContainerHigh` for high-emphasis
components sitting on top of `surfaceContainer`. `surface.copy(alpha = 0.08f)` over `surface` is the
overlay model M3 replaced, and reads flatter than moving up the ramp.

**B3.8 [Weak] `tertiary` spent as a third generic accent, or never used at all.**
Why: tertiary is the "stand out" role — badges, stickers, special one-off actions — and the natural
carrier of a hero moment's color break, since primary and secondary are already spoken for. Used
everywhere it stops standing out; used nowhere the screen has one fewer contrast lever.

---

## B.4 Typography

**B4.1 [Broken] Hardcoded `sp` sizes.**
Find: `rg -n --type kotlin 'fontSize\s*=\s*[0-9]'`
Why: bypasses the type scale, and fixed `sp` inside a fixed-`dp` container clips or overlaps at 200% font
scale. Every `fontSize =` is also a place the emphasized scale can't reach.
Fix: `style = MaterialTheme.typography.titleLarge`. A genuinely custom size (hero countdown, big stat)
belongs in `Type.kt` as a named `TextStyle`, in a `wrapContentHeight` container.

**B4.2 [Wrong] Baseline styles where emphasized belongs.**
Find: `rg -n --type kotlin 'Emphasized'` — near-zero hits in an app claiming to be Expressive *is* the
finding.
Why: the Expressive scale is 30 styles — 15 baseline + 15 emphasized. Emphasized is the
system-sanctioned way to add weight (identical size/line-height/tracking; weight steps to Medium, or
Bold for `labelLargeEmphasized`) and is for "headlines, selected items, or other areas that require a
clear focal point" — not body copy, where a heavier paragraph has no focal point at all.
Fix: `MaterialTheme.typography.headlineLargeEmphasized` on the screen title; `titleMediumEmphasized` on
a selected item.

**B4.3 [Wrong] Ad-hoc `FontWeight.Bold` instead of an emphasized style.**
Find: `rg -n --type kotlin 'FontWeight\.(Bold|SemiBold|Medium|ExtraBold)' --glob '!**/theme/**'`
Why: emphasis stops being tokenized and themeable; a typography swap no longer moves it; and if the font
family lacks that weight instance you get **synthetic bolding** (the renderer smears the glyphs), which
looks visibly wrong next to real weights.
Fix: `style = MaterialTheme.typography.titleLargeEmphasized` instead of
`style = titleLarge, fontWeight = FontWeight.Bold`.

**B4.4 [Wrong] Variable font declared with fewer instances than the type scale asks for.**
Find: `rg -n --type kotlin -A10 'FontVariation|variationSettings'` and count `Font(` entries vs the
weights used across `Type.kt`.
Why: one `Font()` entry at `weight(400)` plus a type scale that asks for Medium and Bold gives synthetic
bolding at every emphasized style. The whole point of the emphasized scale is a *real* weight axis shift.
Fix: one `Font()` entry per weight actually used, each with matching
`weight = FontWeight.X` and `FontVariation.weight(n)`:
```kotlin
val GoogleSansFlex = FontFamily(
    Font(R.font.google_sans_flex, weight = FontWeight.Normal,
         variationSettings = FontVariation.Settings(FontVariation.weight(400), FontVariation.width(100f))),
    Font(R.font.google_sans_flex, weight = FontWeight.Medium,
         variationSettings = FontVariation.Settings(FontVariation.weight(500), FontVariation.width(100f))),
    Font(R.font.google_sans_flex, weight = FontWeight.Bold,
         variationSettings = FontVariation.Settings(FontVariation.weight(700), FontVariation.width(100f))),
)
```

**B4.5 [Weak] No hero type on a screen that needs one.**
Why: on an editorial or navigational screen (library, settings root, profile) the cheapest legitimate
hero moment is a large collapsing headline — it costs nothing in sustained space.
Fix: `LargeFlexibleTopAppBar` ("emphasize the headline of the page") or `MediumFlexibleTopAppBar`
("larger headline… collapses into a small app bar on scroll"), with a `subtitle`. These replace the
deprecated `MediumTopAppBar`/`LargeTopAppBar` — flag those too.

**B4.6 [Weak] Line height and tracking overridden at display sizes.**
Find: `rg -n --type kotlin 'lineHeight\s*=|letterSpacing\s*='`
Why: the tokens encode tuned tracking (0.sp at display/headline, positive at body/label). Hand-set
tracking on a 57sp display style is almost always a regression, and a `lineHeight` below the token clips
descenders at large font scales.

---

## B.5 Shape

**B5.1 [Broken] `MaterialShapes` / `toShape()` / `toPath()` without opt-in.**
Find: `rg -n --type kotlin 'MaterialShapes\.|\.toShape\(|\.toPath\('` and check for a file-level
`@file:OptIn` or a global gradle opt-in.
Why: their promotion to stable was **reverted in 1.5.0-alpha19**. They still require
`@OptIn(ExperimentalMaterial3ExpressiveApi::class)` at alpha26. Same for `LoadingIndicator`.
Fix: `@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)`, or the global
gradle opt-in from B1.1.

**B5.2 [Broken] A `MaterialShapes` member that doesn't exist.**
Find: `rg -n --type kotlin 'MaterialShapes\.\w+'` and check against the catalog.
Why: only cookies **4, 6, 7, 9, 12** exist. `Cookie5Sided`, `Cookie8Sided`, `Cookie10Sided`,
`Cookie11Sided` are compile errors people write constantly. The verified catalog is:
`Circle, Square, Slanted, Arch, Fan, Arrow, SemiCircle, Oval, Pill, Triangle, Diamond, ClamShell,
Pentagon, Gem, Sunny, VerySunny, Cookie4Sided, Cookie6Sided, Cookie7Sided, Cookie9Sided, Cookie12Sided,
Ghostish, Clover4Leaf, Clover8Leaf, Burst, SoftBurst, Boom, SoftBoom, Flower, Puffy, PuffyDiamond,
PixelCircle, PixelTriangle, Bun, Heart`.

**B5.3 [Wrong] Polygon shape clipping real content.**
Find: `rg -n --type kotlin -B3 -A6 '\.clip\(MaterialShapes'`
Why: `Clover8Leaf`, `Burst`, `Flower` and friends have deep concave regions that eat text and photos at
the notches. Guidance: keep abstract shapes in decorative moments (avatars, media, icon backdrops), use
them **sparingly in core components**.
Fix: clip the *backdrop*, not the content — polygon on a `Box` behind a centered icon, or an avatar where
cropping is expected. Dense content containers stay on `MaterialTheme.shapes`.

**B5.4 [Wrong] Expressive shape at a size where it turns to mush.**
Find: the `.size(...)` on the same modifier chain as a `.clip(MaterialShapes...)`.
Why: below roughly 40dp, `Burst`, `VerySunny`, `Clover8Leaf` and the higher-count cookies read as a blob.
Material's caution is broader: "Smaller shapes can result in essential actions looking less important."
Fix: fall back to `Circle` or `Pill` at small sizes, or size up. Never shrink an essential action for
shape variety.

**B5.5 [Wrong] Decorative morphing that signals nothing.**
Find: `rg -n --type kotlin -B5 'Morph\('` and `rg -n --type kotlin 'infiniteRepeatable|rememberInfiniteTransition'`
Why: shape change is a **state signal** — press, selection, expand, playback, loading. A morph on a timer
burns the signal a real state morph needs, costs battery the whole time it's on screen, and trips the
five-second auto-motion rule (B6.8).
Fix: bind the morph to state, or delete it. For press feedback the built-in path is one parameter and is
themeable:
```kotlin
Button(onClick = onPlay, shapes = ButtonDefaults.shapes()) { Text("Play") }
ToggleButton(
    checked = shuffled,
    onCheckedChange = ::setShuffle,
    shapes = ToggleButtonDefaults.shapesFor(ButtonDefaults.MinHeight),   // alpha25+: shapes() is HIDDEN
)
```

**B5.6 [Weak] `MaterialTheme.shapes` unused.**
Find: `rg -c --type kotlin 'MaterialTheme\.shapes'` vs `rg -c --type kotlin 'RoundedCornerShape\('`
Why: if the second dwarfs the first, the shape scale is decorative — defined and ignored. The Expressive
additions (`largeIncreased` 20dp, `extraLargeIncreased` 32dp, `extraExtraLarge` 48dp) exist to give
bigger containers a token; hardcoding 24.dp everywhere skips the system.
Fix: map component shapes onto the scale; add outliers to `Shape.kt` as named tokens.

**B5.7 [Weak] Uniform corner radius everywhere — no shape contrast.**
How: `rg -o --type kotlin 'RoundedCornerShape\([^)]*\)'` plus the `MaterialTheme.shapes.*` reads, then
count distinct values. One value across a whole screen means shape carries no information.
Why: "Break from the surrounding shape style to draw attention to a particular element." Contrast is
relational — a shape is emphatic only because its neighbours are not.
Fix: hold the baseline (`medium`/`large`) for the calm majority; push exactly one element up the scale or
onto a `MaterialShapes` polygon.

**B5.8 [Weak] Grouped list rendered as separate cards instead of segmented items.**
Find: `rg -n --type kotlin -B3 'ListItem\(|Card\('` in settings/preferences screens.
Why: "Group similar content into informative groupings"; "ungrouped information can blend together." The
segmented idiom — rounded outer corners, near-square inner corners, one connected surface — reads as a
single object.
Fix: `SegmentedListItem` + `ListItemDefaults.segmentedShapes` / `segmentedColors` — present since
~alpha15; graduated from experimental in alpha23 — check whether they resolve at your pin before using
the manual fallback (per-index corner shapes: first / middle / last). alpha23 is *not* the
introduction: Med ships them on alpha21, Tomato reaches them via compose-bom-alpha 2026.03.00; below
alpha23 they need `@OptIn(ExperimentalMaterial3ExpressiveApi::class)`.

---

## B.6 Motion

**B6.1 [Wrong] Hardcoded `tween` / durations / easing curves.**
Find: `rg -n --type kotlin 'tween\(|durationMillis|LinearEasing|FastOutSlowInEasing|LinearOutSlowInEasing|CubicBezierEasing'`
Why: Expressive motion is physics, not curves. A duration+easing curve cannot be retargeted mid-flight —
it restarts or cross-fades — which is why springs replaced it. Hardcoded durations also skip the
per-device scaling the tokens apply ("token values adjust per device type").
Fix — six specs, read off the theme:
```kotlin
val offset by animateFloatAsState(target, MaterialTheme.motionScheme.defaultSpatialSpec())
// fastSpatialSpec / defaultSpatialSpec / slowSpatialSpec · fastEffectsSpec / defaultEffectsSpec / slowEffectsSpec
```
**Legitimate exceptions ([Note], not [Wrong]):** an explicit `snap()`, and a deliberate reduced-motion
cross-fade (§B6.7).

**B6.2 [Wrong] Spatial spec on an effects property, or effects spec on a spatial one.**
Find: `rg -n --type kotlin -B2 -A2 'animateColorAsState|fadeIn\(|fadeOut\(|alpha ='` and check which spec
family is passed; then the inverse for `animateDpAsState` / `slideIn` / `scaleIn`.
Why: a **bug, not a preference**. Expressive spatial springs are underdamped (damping 0.8 default, 0.6
fast) and overshoot by design, and Material's `MotionScheme` KDoc is explicit that effects motion
"shouldn't have any overshoot." Concretely: an overshooting **color** interpolates out of gamut and
lands on a value in neither endpoint; an overshooting **elevation/shadow** is directly visible as the
shadow pumping past its resting depth; and in general the bounce reads as a flicker rather than as
movement, because bounce only communicates "arrived" when something moved. **Alpha is the case to
state precisely:** `graphicsLayer.alpha` is clamped to `[0f, 1f]` by the render node, so an
overshooting alpha does *not* render a visible spike past full opacity — the damage is that the value
rings, so the fade lands late and out of step with every other fade in the app. Do not write findings
claiming a visible flash on alpha. Effects springs are critically damped (1.0) in both schemes
precisely so none of this happens.
Fix — match family to property:
```kotlin
// color / opacity / elevation  →  effects
val color by animateColorAsState(target, animationSpec = motionScheme.slowEffectsSpec())
// position / size / rotation / corner radius  →  spatial
val size  by animateDpAsState(target, animationSpec = motionScheme.defaultSpatialSpec())
// combined transition: each half gets its own family
enter = fadeIn(motionScheme.defaultEffectsSpec()) + scaleIn(motionScheme.defaultSpatialSpec())
```

**B6.3 [Wrong] Spring tier mismatched to element scale.**
Find: `rg -n --type kotlin 'slowSpatialSpec|fastSpatialSpec'` and look at what's being animated.
Why: Fast is for "small components like switches and buttons"; Default for "medium-scale animations like
bottom sheets and navigation rails"; Slow for "full-screen animations and content refreshes." A slow
bouncy spring on a switch reads as broken; a fast spring on a full-screen transition reads as a jump cut.

**B6.4 [Wrong] Overshoot on a departure.**
How: check exit transitions for spatial specs with visible overshoot.
Why: an element bouncing as it leaves reads as a glitch — the eye interprets overshoot as arrival energy.
Fix: spatial spring in, effects fade or a critically-damped spatial spec out.

**B6.5 [Weak] No shared element transition where content is obviously continuous.**
Find: `rg -c --type kotlin 'SharedTransitionLayout|sharedBounds|sharedElement'` — zero hits in an app with
list→detail navigation is the finding.
Why: the highest-value expressive motion and the most commonly skipped. A card that opens a detail screen
showing the same image and title, cross-faded, is a jump cut with extra steps — Material's motion
principles name both "avoid jump cuts" and "spatial coherence."
Fix: wrap the root `NavDisplay`/`NavHost` in `SharedTransitionLayout` and key the shared image and title
with `Modifier.sharedBounds(rememberSharedContentState(key), animatedVisibilityScope)`. The nav container
must be hoisted **above** the display or the shared element is clipped out of existence (B8.3).

**B6.6 [Weak] Over-animation — everything moves.**
How: count animated properties visible in one screen state. More than ~3 concurrent independent
animations outside a designated hero moment is noise.
Why: "Reserve the Expressive scheme for hero moments and key interactions"; "ensure motion assists user
tasks rather than distracting." Expressive springs on every element is the motion equivalent of
expressive-everywhere — nothing is left to contrast against.

**B6.7 [Broken] No reduced-motion handling.**
Find: `rg -n --type kotlin 'ANIMATOR_DURATION_SCALE|areAnimatorsEnabled|reduceMotion|MotionPolicy'`
Why: Material's **first** motion principle is accessibility — respect user platform settings. Android's
is Remove/Reduce animations. [Broken] when the app has significant spatial motion; [Weak] when static.
Fix — read the system scales and expose a policy:
```kotlin
val reduceMotion = !ValueAnimator.areAnimatorsEnabled() ||
    Settings.Global.getFloat(resolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f ||
    Settings.Global.getFloat(resolver, Settings.Global.TRANSITION_ANIMATION_SCALE, 1f) == 0f
```
Then swap `MotionScheme.expressive()` → `MotionScheme.standard()` at the theme, degrade slides to fades,
and `snap()` anything decorative. Register a `ContentObserver` on those URIs so it reacts live.

**B6.8 [Wrong] Auto-running motion longer than five seconds with no stop affordance.**
Find: `rg -n --type kotlin 'rememberInfiniteTransition|infiniteRepeatable'`
Why: content that "moves, scrolls, or blinks automatically" must be "paused, stopped, or hidden if it
lasts more than five seconds" — a looping morph background, an always-on wavy indicator, an auto-advancing
carousel all qualify. Also: flashing ≤3×/second, and avoid flashing large central regions.
Fix: gate on reduced motion, stop the loop when the underlying state settles, or add a pause control.

**B6.9 [Wrong] Animating layout when a draw-phase property would do.**
Find: `rg -n --type kotlin -B3 'Modifier\.(size|padding|offset)\('` near `animate*AsState`, plus
`rg -n --type kotlin 'animateContentSize'`.
Why: animating `Modifier.size`/`padding`/`offset(Dp)` re-measures the subtree **every frame** and moves
siblings; `graphicsLayer`, the `offset { IntOffset }` lambda overload and `drawBehind` invalidate draw
only. Reading an animated `State` in a composable body invalidates composition — the costliest phase.
Fix:
```kotlin
Modifier.graphicsLayer { alpha = animatedAlpha; scaleX = s; scaleY = s }   // draw phase
Modifier.offset { IntOffset(x.roundToInt(), y.roundToInt()) }              // layout phase, deferred read
```
A `graphicsLayer` scale also does **not** change the touch target — usually what you want (B10.1).

**B6.10 [Note] `MotionScheme.standard()` chosen deliberately.** Valid — "Standard feels more functional
with minimal bounce" — but confirm it's intentional and consistent; never mix schemes in one product.

---

## B.7 Components

**B7.1 [Wrong] Loose row of buttons that should be a connected group.**
Find: `rg -n --type kotlin -A8 'Row\(' | rg -n 'Button\(|OutlinedButton\(|FilledTonalButton\('`
Why: "Group similar content into informative groupings"; "ungrouped information can blend together." A
connected group reads as a single object that can then carry one expressive treatment. A loose row cannot
be made expressive, only loud.
Fix — the de-facto real-world pattern (most shipping apps never call the `ButtonGroup` composable):
```kotlin
Row(horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween)) {
    options.forEachIndexed { i, option ->
        ToggleButton(
            checked = selected == i,
            onCheckedChange = { onSelect(i) },
            shapes = when (i) {
                0 -> ButtonGroupDefaults.connectedLeadingButtonShapes()
                options.lastIndex -> ButtonGroupDefaults.connectedTrailingButtonShapes()
                else -> ButtonGroupDefaults.connectedMiddleButtonShapes()
            },
            modifier = Modifier.weight(1f).semantics { role = Role.RadioButton },
        ) { Text(option) }
    }
}
```
Connected spec: **2dp spacing, 8dp inner corners, fully rounded outer corners.** Standard (non-connected)
group spacing is **12dp** and preserves each button's own shape.

**B7.2 [Wrong] Unrelated actions inside one connected group.**
Why: connected implies "one set" — its members are alternatives. Connecting Save + Delete + Share asserts
a relationship that doesn't exist and invites mis-taps.
Fix: standard button group (12dp, individual shapes), or separate rows.

**B7.3 [Wrong] Button group allowed to overflow its container.**
Find: `horizontalScroll` or clipped rows where a group would have overflowed.
Why: the component has two overflow modes — **menu** (collapse into a popup) and **wrap** (new line).
Running off-screen loses actions silently.
Fix: `ButtonGroup(overflowIndicator = { ButtonGroupDefaults.OverflowIndicator(it) }) { … }`.

**B7.4 [Wrong] FAB menu with the wrong item count, or paired with an extended FAB.**
Find: `rg -n --type kotlin -A25 'FloatingActionButtonMenu\('` and count `FloatingActionButtonMenuItem`.
Why: the spec is **2–6 related actions** — fewer than two is a plain FAB, more than six is a bottom
sheet. Hard exclusion: **"Fab menu is not used with extended FABs."** It also "should always appear in
the same place as the FAB that opened it."
Fix: trim to ≤6, promote overflow to a sheet, use `ToggleFloatingActionButton` as the `button` slot.

**B7.5 [Wrong] Speed dial or stacked small FABs.**
Find: `rg -n --type kotlin -B5 -A5 'SmallFloatingActionButton'`
Why: the FAB menu "should replace the speed dial and any usage of stacked small FABs."
Fix: `FloatingActionButtonMenu` + `ToggleFloatingActionButton` + `Modifier.animateFloatingActionButton`.

**B7.6 [Wrong] Deprecated `BottomAppBar` alongside, or instead of, a toolbar.**
Find: `rg -n --type kotlin 'BottomAppBar\('`
Why: the bottom app bar is deprecated and "should be replaced with the docked toolbar, which is very
similar and more flexible." Worse is showing it *and* a floating toolbar or nav bar.
Fix: `FlexibleBottomAppBar` for a docked bar ("global actions that remain the same across multiple
pages"); `HorizontalFloatingToolbar` for "contextual actions relevant to the body content or the
specific page."

**B7.7 [Wrong] Navigation bar and toolbar on the same page.**
Find: `rg -n --type kotlin -A20 'Scaffold\(' | rg 'NavigationBar|ShortNavigationBar|FloatingToolbar|BottomAppBar'`
Why: "Show the navigation bar on primary pages, and toolbars on subsequent pages with actions." Two
stacked bottom containers is the most common Expressive regression in practice.
Fix: one per page. A `HorizontalFloatingToolbar` used *as* the nav container is a legitimate deliberate
choice — but then there is no nav bar.

**B7.8 [Wrong] Wavy indicator too small to read as wavy.**
Find: `rg -n --type kotlin -B3 -A3 'WavyProgressIndicator'` and check the `.size(...)`.
Why: "at very small sizes, the wavy shape may not be as visible" — below roughly 40dp it reads as a fuzzy
circle. The default `indicatorAmplitude` also **flattens the wave to zero below 10% and above 95%
progress**, so an indicator that lives near either end is effectively non-wavy anyway.
Fix: size up, or use plain `CircularProgressIndicator`/`LinearProgressIndicator` at small sizes.

**B7.9 [Broken] Wavy stroke width passed as a raw float meant as dp.**
Find: `rg -n --type kotlin -A3 'stroke\s*=\s*Stroke\(|trackStroke\s*='`
Why: `stroke`/`trackStroke` are `Stroke`, whose `width` is in **pixels**, while `gapSize`, `wavelength`
and `waveSpeed` are `Dp`. `Stroke(width = 8f)` written meaning "8dp" is a hairline at 3x. Mixing units in
one call is the common form of this bug.
Fix:
```kotlin
val density = LocalDensity.current
CircularWavyProgressIndicator(
    progress = { fraction },
    stroke = with(density) { Stroke(width = 8.dp.toPx(), cap = StrokeCap.Round) },
    wavelength = 20.dp,          // Dp — do not convert
    gapSize = 4.dp,
)
```

**B7.10 [Wrong] Indeterminate `LoadingIndicator` for a long or determinate wait.**
Find: `rg -n --type kotlin 'LoadingIndicator|ContainedLoadingIndicator'` and trace what it's waiting on.
Why: the loading indicator "is designed to show progress that loads in **under five seconds**", and must
**not** be used "if processes transition from indeterminate to determinate states." A network upload, a
backup, a model download all fail both tests. Determinate-vs-indeterminate is a **semantic** claim —
choosing wrong misreports state to assistive tech, not just to the eye.
Fix: `LinearProgressIndicator` / `LinearWavyProgressIndicator(progress = { … })` when progress is known;
indeterminate only when it genuinely isn't.

**B7.11 [Wrong] Linear and circular progress mixed for the same class of activity.**
Why: "Only one type should represent each kind of activity in an app." Pick one per activity class and
apply it everywhere that activity appears.

**B7.12 [Weak] Flexible app bar variants unused.**
Find: `rg -n --type kotlin 'MediumTopAppBar|LargeTopAppBar'`
Why: the flexible variants **replace** the deprecated medium/large ones and add subtitle, text wrapping,
center alignment and reduced height. Subtitles default to `onSurfaceVariant` — free hierarchy.

**B7.13 [Note] Custom reimplementation of a shipped component.**
Find: custom sliders, nav bars, loading art in `ui/component/`.
Why: often legitimate (a squiggly seekbar with a custom thumb has no first-party equivalent), sometimes a
component the author didn't know existed. Either way, check it reads `MaterialTheme.motionScheme` and the
color roles — custom components with hardcoded springs are where motion inconsistency originates.

---

## B.8 Navigation & adaptive

**B8.1 [Wrong] Hardcoded device branches instead of window size classes.**
Find: `rg -n --type kotlin 'screenWidthDp|smallestScreenWidth|isTablet|LocalConfiguration'`
Why: breaks on foldables, in split-screen, in freeform windows, and on any tablet the threshold didn't
anticipate. "UI should adapt to the user context" — the context is the *window*, not the device.
Fix: `val widthClass = currentWindowAdaptiveInfo().windowSizeClass.windowWidthSizeClass`. Compact →
`ShortNavigationBar`; Medium → `WideNavigationRail` collapsed; Expanded → `WideNavigationRail` expanded
or a permanent drawer. `NavigationSuiteScaffold` does the switch for you and is the right default unless
custom breakpoints are needed.

**B8.2 [Wrong] Two navigation containers visible at once.**
Find: `rg -n --type kotlin 'NavigationBar\(|ShortNavigationBar|NavigationRail\(|WideNavigationRail|HorizontalFloatingToolbar'`
Why: rail + nav bar at Medium width, or a floating toolbar used as nav *plus* a nav bar, gives two
competing "where am I" affordances. See also B7.7.

**B8.3 [Wrong] Nav container rebuilt inside the `NavDisplay`/`NavHost` instead of hoisted above it.**
Find: `rg -n --type kotlin -B10 'NavDisplay\(|NavHost\('` and check where the `Scaffold` lives.
Why: a container recreated per destination re-enters on every navigation (visible flicker) and kills
cross-destination shared element transitions — the shared bounds have no common parent.
Fix: `SharedTransitionLayout { Scaffold(bottomBar = { … }) { NavDisplay(…) } }`.

**B8.4 [Wrong] No predictive back.**
Find: `rg -n --type kotlin 'predictivePopTransitionSpec|PredictiveBack|BackHandler'`
Why: on Android 16 a back gesture with no preview reads as broken. A bare `BackHandler` that just pops is
not predictive back — there is no preview frame.
Fix: give the root `NavDisplay` a predictive-pop spec and make the pop the inverse of the push. Verify
from every depth, including nested graphs.

**B8.5 [Broken] State lost on resize / rotate / fold.**
Find: `rg -c --type kotlin 'rememberSaveable'` vs `rg -c --type kotlin 'remember \{ mutableStateOf'`
Why: expressive layouts swap container at breakpoints; if the selected destination, scroll position or
expanded pane resets on the swap, the adaptivity is worse than not adapting. Folding triggers it every
time.
Fix: `rememberSaveable` for UI state, ViewModel/`SavedStateHandle` for data-derived state. Test by
folding, rotating, and entering split-screen.

**B8.6 [Wrong] More than five destinations in a short navigation bar.**
Find: count `ShortNavigationBarItem` / `NavigationBarItem` per container.
Why: the spec is **3–5 destinations**; six on a compact screen gives sub-48dp targets and truncated labels.
Fix: overflow into the rail's expanded state, a "More" destination, or a menu.

**B8.7 [Weak] Expressive nav sizing left on the legacy components.**
Find: `rg -n --type kotlin 'NavigationBar\(|NavigationRail\('`
Why: `ShortNavigationBar` is materially shorter (height 80→**64dp**, padding 12/16→**6/6dp**, active
indicator 64→**56dp**) and at **≥600dp** switches to icon-beside-label items. `WideNavigationRail` is
wider (80→**96dp**), elevated (0→**3dp**), and **expands** like a drawer — it replaces the navigation
drawer, non-modally.
Fix: migrate. `ShortNavigationBarItem`'s `label` is a **required positional** (nullable) parameter before
`modifier` — a common migration compile error.

**B8.8 [Note] Navigation3 adopted for a flat tab app.** nav3's advantage is composing with adaptive panes
off one back stack; for flat tabs it only buys churn. Say so rather than migrating reflexively.

---

## B.9 Hierarchy & expression — the design layer

The area a linter can't reach, and the one users mean when they say "it doesn't feel expressive." Do the
procedure; don't freehand it.

### B9.0 The procedure for judging hierarchy from code

Walk the top-level composable and list every visually distinct element (app bar, hero card, list, FAB,
toolbar, each button group). For each, score divergence on four axes **against the screen's own
baseline**, not an abstract ideal:

| Axis | Diverges when… | Read it from |
| --- | --- | --- |
| **Size** | ≥1.5× the median element's footprint, or a Large/XLarge button (96/136dp) among Small/Medium (40/56dp) | `.size()`, `.height()`, `ButtonDefaults` size params, `.weight()` |
| **Shape** | different corner token from its neighbours, or a `MaterialShapes` polygon among `RoundedCornerShape`s | `.clip()`, `shape =`, `MaterialTheme.shapes.*` |
| **Color** | uses an accent (`primary`/`tertiary`/`*Container`) where neighbours use `surface*` | `containerColor =`, `.background()`, `colors =` |
| **Motion** | has its own animation, morph, or shared-element key while neighbours are static | `animate*AsState`, `shapes =` with a pressedShape, `sharedBounds` |

Then count. **Elements with ≥2 divergent axes = candidate heroes.**

**Per-screen count — an explicitly-labelled proxy, not the canon.** Material's budget is stated at
*product* scope: "stick to one or two hero moments in your product." A single-screen review can only
see one screen, so use the per-screen count as a stand-in for that budget and say in the finding that
it is a proxy.

- **0 → [Weak] no hero.** Uniformly calm. Fine for a settings sub-page; a failure for a home, library,
  player or landing screen.
- **1–2 → plausible on this screen.** Verify each passes both qualifying questions: "Is this
  interaction emotionally impactful?" and "Is this a key interaction in your product?" Both must be
  yes. Then check it against the product-level count below — a screen can be locally correct and still
  be one of twenty screens spending the same budget.
- **≥3 on one screen → [Wrong] too many heroes.** Name which one survives and why, and say what to
  pull back.

**The product-level check — run it whenever you can see more than one screen.** Count heroes *across*
screens, not within them: "Stick to one or two hero moments in your product; too many moments can be
overwhelming or distracting." How: repeat B9.0 per screen composable and tally the screens that
produce ≥1 hero. Flag when many screens each claim one.

Be honest about severity here. A 20-screen app where every screen has a single well-behaved hero
passes the per-screen check twenty times and still violates the canon by **20×** — that is the larger
violation, and a one-screen review cannot see it. Rank it accordingly: three heroes on one screen is a
local [Wrong]; twenty heroes across twenty screens is the same [Wrong] at twenty times the scale, and
the fix is deciding which one or two screens keep theirs while the rest go calm.

Record both counts in the report. "Four elements diverge on ≥2 axes (lines 88, 140, 203, 261); the
per-screen proxy budget is two" is actionable; "nine of eleven screens each declare a hero, and the
product budget is one or two total" is more actionable still. "Feels busy" is not.

**B9.1 [Weak] No hero on a screen whose job needs one.**
Fix, in ascending cost: promote the app bar to `LargeFlexibleTopAppBar` with a subtitle and an emphasized
style (cheapest — collapses on scroll, costs no sustained space); or size up the single most important
element; or give the primary action a Large/XLarge button.

**B9.2 [Wrong] Three or more heroes competing.**
Fix: demote to baseline everywhere except the element tied to the screen's job. Demotion means dropping
to one divergent axis, not zero.

**B9.3 [Wrong] All four levers pulled on the same element outside a hero moment.**
Why: size + shape + color + motion on a secondary control makes it read as the hero and steals attention
from the real one. One lever is usually enough outside a hero; two is a hero; four is noise.

**B9.4 [Wrong] Expression applied uniformly.**
How: if every card is `extraLargeIncreased`, every button is filled `primary`, and every state change
animates, the B9.0 scores are identical — which means zero relative to each other.
Why: "Break from the surrounding shape style"; "without contrast, elements can blend together." Uniform
expressiveness has no contrast and is worse than baseline M3, not better.
Fix: pull the baseline back down. This is a subtractive edit — say so explicitly, since the user asked to
add expression and the answer is to remove some.

**B9.5 [Weak] Grouping / containment missing.**
Find: long flat `Column`s of `ListItem`s or controls with no container boundaries.
Why: "Ungrouped information can blend together." Containment is the substrate — it's how the rest of the
screen gets calm enough for the levers to register. Group **before** you decorate.
Fix: segmented list items, `*Container` roles, `surfaceContainer` tones, connected button groups,
floating toolbars.

**B9.6 [Note] Motion contrast spent on a non-hero.** Motion is the most expensive lever — frame budget,
reduced-motion conflict, and a wrong spring reads as a bug. Spend it last, and on the hero.

---

## B.10 Accessibility

**B10.1 [Broken] Touch target under 48dp.**
Find: `rg -n --type kotlin -B4 '\.size\([0-9]+\.dp\)'` on clickable elements; every `IconButton` with an
explicit small size; every XSmall (32dp) / Small (40dp) button.
Why: **48 × 48dp minimum** — "a physical size of about 9mm, regardless of screen size" — separated by
"8dp of space or more." The expressive size scale makes this easy to violate: an XSmall button is 32dp
tall by spec. Its *visual* bounds may be 32dp; its touch target may not.
Fix:
```kotlin
IconButton(onClick = onPlay, modifier = Modifier.minimumInteractiveComponentSize()) { … }
Modifier.sizeIn(minWidth = 48.dp, minHeight = 48.dp).clickable(onClick = onPlay)  // custom clickables
```
A connected group's **2dp** internal spacing is spec — the members are one object, so the 8dp separation
rule applies to the group, not between members. Don't file that.

**B10.2 [Broken] Icon-only control with no accessible name.**
Find: `rg -n --type kotlin -B6 'contentDescription\s*=\s*null'`
Why: `contentDescription = null` is **correct** for a decorative icon inside a labelled control (an
`Icon` inside a `Button` that already has `Text`), and a **failure** when the icon *is* the control's
only content. Check the enclosing composable before filing.
Fix: put the description on the control, not the icon:
```kotlin
IconButton(onClick = onShuffle, modifier = Modifier.semantics { contentDescription = shuffleLabel }) {
    Icon(Icons.Rounded.Shuffle, contentDescription = null)
}
```

**B10.3 [Broken] Toggle with no `stateDescription`.**
Find: `rg -n --type kotlin -A6 'ToggleButton|IconToggleButton|ToggleFloatingActionButton|Switch\('` and
check for `stateDescription`.
Why: TalkBack announces "checked/unchecked" generically. A `ToggleFloatingActionButton` that opens a menu
needs "expanded"/"collapsed". A split button's trailing toggle carries **real state**, not decoration.
Fix: see B10.4 — same `semantics` block.

**B10.4 [Wrong] FAB menu / toolbar / button group with no group semantics or escape action.**
Find: `rg -n --type kotlin -A20 'FloatingActionButtonMenu|FloatingToolbar' | rg 'semantics|traversal|customActions'`
Why: an expanded FAB menu is a modal-ish overlay. Without `isTraversalGroup` its items interleave with the
content behind it; without a custom action there is no keyboard/switch-access way to dismiss it; without
`traversalIndex` the toggle itself may be read last.
Fix — the shipping pattern:
```kotlin
ToggleFloatingActionButton(
    modifier = Modifier.semantics {
        traversalIndex = -1f
        stateDescription = if (expanded) expandedString else collapsedString
        contentDescription = menuActionDesc
    },
    checked = expanded, onCheckedChange = { onExpandedChange(!expanded) },
) { … }
// on the last menu item:
Modifier.semantics {
    isTraversalGroup = true
    customActions = listOf(CustomAccessibilityAction(closeMenuString) { onExpandedChange(false); true })
}
```

**B10.5 [Wrong] Connected group members with no role.**
Find: `rg -n --type kotlin 'connectedLeadingButtonShapes|ConnectedSpaceBetween' -A10 | rg 'Role\.'`
Why: a single-select connected group **is** a radio group. Without the role, TalkBack announces five
independent buttons and never says "2 of 5".
Fix: `Modifier.semantics { role = Role.RadioButton }` for single-select; `Role.Button` for a group of
independent actions.

**B10.6 [Wrong] TalkBack traversal order broken by overlays.**
How: look for `Box` stacking where a floating toolbar, FAB or bottom sheet is declared before the content
it floats over — declaration order drives default traversal.
Fix: `Modifier.semantics { isTraversalGroup = true }` on both overlay and content, with `traversalIndex`
to order them. The platform already handles part of this: floating toolbars stay expanded and disable
`scrollBehavior`, and `FlexibleBottomAppBar` disables `scrollBehavior`, whenever an accessibility service
is active. **Don't fight that with manual state** — forcing a toolbar collapsed while a screen reader is
on is itself a finding.

**B10.7 [Broken] Reduced motion not honored.** See B6.7; file it once, under whichever area leads.

**B10.8 [Broken] Layout breaks at 200% text scale.**
Find: fixed `.height()` around `Text`, every `fontSize =` from B4.1, `maxLines = 1` without `overflow`.
Why: a 56dp row with `bodyLarge` clips at 200% scale, and expressive components are already shorter
(nav bar 80→64dp) so the margin is thinner than it used to be.
Fix: `Modifier.heightIn(min = …)` instead of `height()`; let text wrap; verify at Settings → Display →
Font size = largest. A feature flag exists for this on expressive list items
(`isExpressiveListItemHeightBasedOnTextLinesFixEnabled`) — check it if list rows clip.

**B10.9 [Wrong] Component contrast geometry zeroed out for a cleaner look.**
Find: `rg -n --type kotlin 'gapSize\s*=\s*0|trackGap|stopSize\s*=\s*0|thumbTrackGapSize'`
Why: the progress indicator's **4dp track gap** and **4dp stop indicator**, and the slider's **6dp
thumb-track gap / 2dp inside corner / 4dp stop indicator**, exist "to meet modern contrast requirements."
Accessibility affordances, not styling.

**B10.10 [Note] Unlabeled navigation items.** Legal — the label-visibility modes include unlabeled — but
the icons must then carry accessible names, since nothing visible does.

---

## B.11 Performance

**B11.1 [Wrong] `Morph` / `RoundedPolygon` / `toShape()` recreated per frame.**
Find: `rg -n --type kotlin 'Morph\(|RoundedPolygon\(|\.toShape\(' | rg -v 'remember'`
Why: `Morph` construction runs the feature-mapping algorithm between two polygons — not cheap, and in a
`Shape.createOutline` body or an animated composable it runs every frame. `toShape()` is `@Composable`
and allocates a new `Shape` per call.
Fix: hoist and remember; animate only the progress.
```kotlin
val morph = remember { Morph(MaterialShapes.Circle, MaterialShapes.Cookie9Sided) }
val progress by animateFloatAsState(if (pressed) 1f else 0f,
    animationSpec = MaterialTheme.motionScheme.fastSpatialSpec())
// pass `morph` + `{ progress }` into a custom Shape; call morph.toPath(progress, path) inside it
```
`Morph.toPath` is **not** `@Composable` (correct — it's called from draw/outline code) and there is **no**
`Morph.toShape()`.

**B11.2 [Wrong] `toShape()` called inside a scroll or draw body.**
Find: `rg -n --type kotlin -B6 '\.toShape\('` — is the enclosing lambda a `LazyColumn` item, a `Canvas`,
or a `drawBehind`?
Why: per-item is survivable; per-frame is not. A `toShape()` inside `graphicsLayer` or `drawWithContent`
allocates every frame of every scroll.
Fix: hoist above the `LazyColumn` — `val shape = MaterialShapes.Cookie9Sided.toShape()` once, reused by
every item. For `startAngle` variation, remember a small list of shapes rather than recomputing.

**B11.3 [Wrong] Animation invalidating composition instead of draw.** See B6.9 — file under Motion if
the complaint is feel, under Performance if it's jank; not both.

**B11.4 [Wrong] Unstable lambdas in hot list items.**
Find: `rg -n --type kotlin -A8 'items\(|itemsIndexed\('`
Why: `onClick = { vm.select(item.id) }` allocates a new lambda per recomposition, making the item
composable unskippable. In a scrolling list that's every item, every frame.
Fix: hoist to a stable reference, or key it:
```kotlin
val onSelect = remember(vm) { { id: String -> vm.select(id) } }
items(list, key = { it.id }) { item -> Row(Modifier.clickable { onSelect(item.id) }) { … } }
```
Also check for unstable parameter types (raw `List<T>`, non-`@Immutable` data holders) reaching item
composables.

**B11.5 [Weak] Animated values read as `State` in a composable body instead of a lambda.**
Find: `rg -n --type kotlin 'progress\s*=\s*[a-z]' ` on progress indicators.
Why: `progress: () -> Float` exists on the indicator APIs specifically to keep the recomposition scope at
the leaf. Passing `progress = fraction` (a read) recomposes the whole enclosing composable each frame.
Fix: `progress = { fraction }`.

**B11.6 [Weak] `animateContentSize` on a large subtree.** It is a **layout** animation — correct for text
expanding to more lines, expensive on a deep subtree, never right on a screen-level container.

**B11.7 [Note] Shared element `renderInOverlayDuringTransition` disabled.** The default (`true`) lifts the
element out of the layout during the flight — correct and cheap. Disabling it forces in-place drawing and
clipping against ancestors, usually to work around a clipping bug that has a better fix.

---

# C. The quick pass

Ten checks, roughly fifteen minutes, for "is this sane" rather than "audit this." Run in order; stop
early only if the user asked for a go/no-go and item 1 fails.

1. **Root theme.** `MaterialExpressiveTheme` with an explicit `motionScheme`?
   `rg -n --type kotlin 'MaterialExpressiveTheme\(|MaterialTheme\('` → §B2.1
2. **Version and opt-in coherence.** material3 pin, `graphics-shapes` present if `MaterialShapes` is used,
   opt-ins global. `rg -n 'material3|graphics-shapes' --glob '*.toml'` → §B1
3. **Hardcoded motion.** `rg -n --type kotlin 'tween\(|LinearEasing|FastOutSlowInEasing'` → §B6.1
4. **Spatial/effects mismatch.** `rg -n --type kotlin -B2 'animateColorAsState|fadeIn\(|fadeOut\('` — is
   any of them on a spatial spec? → §B6.2
5. **Hardcoded colors outside the theme.**
   `rg -n --type kotlin 'Color\(0x|Color\.White|Color\.Black' --glob '!**/theme/**'` → §B3.2
6. **Hero count.** Run the B9.0 divergence count on the main screen. 0 or ≥3 is a finding. → §B9
7. **Two nav containers / nav bar + toolbar.**
   `rg -n --type kotlin 'NavigationBar|NavigationRail|FloatingToolbar|BottomAppBar'` → §B7.7, §B8.2
8. **Touch targets and accessible names.**
   `rg -n --type kotlin 'contentDescription\s*=\s*null'` and every `IconButton` with an explicit size
   → §B10.1, §B10.2
9. **Reduced motion.** `rg -n --type kotlin 'reduceMotion|areAnimatorsEnabled|ANIMATOR_DURATION_SCALE'` —
   zero hits in an animated app is a finding. → §B6.7
10. **Shape scale actually consumed.**
    `rg -c --type kotlin 'MaterialTheme\.shapes'` vs `rg -c --type kotlin 'RoundedCornerShape\('` → §B5.6

If all ten pass, say so plainly and name the two or three things that would most improve the screen next.
Do not manufacture a finding to fill the report.

---

# D. Report template

```markdown
## Verdict

<One paragraph. What this is, what the dominant problem is, and whether it ships.
Lead with the single highest-leverage fact — usually the theme. Name the hero count.
No hedging, no "overall this is a solid foundation" preamble.>

## Broken

- **`path/to/File.kt:212` — <one-line claim>.**
  <Why it fails, one or two sentences, with the guidance or API fact behind it.>
  Fix: <concrete change; code block if the change isn't obvious from the sentence.>

## Wrong

- **`path/to/File.kt:88` — <claim>.** …

## Weak

- **`path/to/File.kt:140` — <claim>.** …

## Note

- **`path/to/File.kt:31` — <claim>.** …

## Already good

- <2–5 bullets naming what works and why, so the user doesn't undo it while fixing the above.>

## If you fix three things

1. …  2. …  3. …
```

Rules for the body:

- **Every finding carries `file:line`.** A finding without a location is an opinion.
- **Collapse repeats.** `hardcoded tween(300) at 14 sites; representative: ui/player/Player.kt:212`.
- **Order within a severity by blast radius**, not by file order. The theme finding comes before the
  fourteen screen findings it causes.
- **Omit empty severity sections.** A report with an empty "Broken" heading reads as padding.
- **"Already good" is not optional.** It is how the user knows which parts of the diff to leave alone.

## A well-written finding vs a badly-written one

**Bad:**

> - Consider using more expressive motion in the player screen. The animations currently feel a bit
>   static and could benefit from Material 3 Expressive's spring-based motion system. Also, some colors
>   might not be accessible.

Why it's bad: no location, no claim that can be true or false, no fix, two unrelated issues in one
bullet, and "might not be accessible" pushes the actual work back onto the reader.

**Good:**

> - **`ui/player/Player.kt:212` — album-art crossfade animates alpha with a spatial spring; effects
>   motion must not overshoot.**
>   `animateFloatAsState(alpha, animationSpec = motionScheme.defaultSpatialSpec())` — the expressive
>   spatial spring is underdamped (damping 0.8) by design and rings past its target, and Material's
>   `MotionScheme` KDoc says effects motion "shouldn't have any overshoot"; effects springs are
>   critically damped (1.0) precisely so it can't. Mechanism, stated exactly: `graphicsLayer.alpha` is
>   clamped to `[0f, 1f]` by the render node, so this does **not** produce a visible spike past full
>   opacity — the damage is the ringing tail, which makes this fade settle later and with a different
>   shape than every other fade in the app.
>   Fix: `animationSpec = MaterialTheme.motionScheme.defaultEffectsSpec()`.

Why it's good: exact location, a falsifiable claim, the *correct* mechanism (0.8 damping rings; alpha
is clamped, so the symptom is timing and inconsistency rather than a flash), the rule it violates
(Material's own no-overshoot-on-effects mandate), and a one-line fix. Someone can verify it in thirty
seconds and fix it in ten. Note what it does not do: it does not assert a visible flicker it cannot
demonstrate. On a **color** the same mistake is worse and *is* visible — channels interpolate out of
gamut and land on a hue in neither endpoint — and on **elevation** the shadow overshoot is plainly
visible; write the symptom that the property actually produces.

**Also good — a design-layer finding, which is harder to write well:**

> - **`ui/home/HomeScreen.kt` — four competing hero moments; the budget is two.**
>   Elements diverging on ≥2 expression axes: the `LargeFlexibleTopAppBar` (size + emphasized type,
>   line 61), the featured card (size + `Cookie12Sided` shape + `tertiaryContainer`, line 88), the
>   XLarge play button (size + shape morph, line 140), and the animated stats row (motion + color,
>   line 203). Material's constraint: "Stick to one or two hero moments in your product; too many
>   moments can be overwhelming or distracting" — and because contrast is relational, four breaks means
>   no break reads as one.
>   Fix: keep the featured card (it's what the screen is for) and the app bar headline. Demote the play
>   button to `MaterialTheme.shapes.large` with default size, and drop the stats row's color animation —
>   its motion alone is enough to signal freshness.

Why it's good: it names the specific elements and lines, shows the counting method so the judgement is
auditable, cites the constraint, and — critically — says which hero survives *and why*, rather than
telling the user to "reduce visual noise."

# M3 Expressive — The 25 Most Common Mistakes

Ranked by **frequency × damage**. #1 is the one to check before anything else — it invalidates work on
every screen beneath it, and nothing warns you.

**Provenance `[API-alpha26]`.** Every `material3` / `material3-adaptive` composable name and
`*Defaults` member name in this file was machine-checked against
`compose/material3/material3/api/current.txt` and `compose/material3/adaptive/*/api/current.txt`
at the alpha26 / 1.3.0 SHA. Non-material3 APIs (foundation, ui, animation, navigation3,
third-party) were **not** checked; anything that could not be confirmed is flagged `[UNVERIFIED]`
inline at its use site.

Each entry: the **symptom as a user would describe it**, the **actual cause**, **how to confirm it**,
and the **fix**. All code valid at material3 `1.5.0-alpha26` / material3-adaptive `1.3.0`.

## Triage by symptom

| The user says… | Start at |
| --- | --- |
| "it doesn't feel expressive" / "it looks the same as before" | #1, #2, #8, #10 |
| "it won't compile" / "unresolved reference" | #3, #4, #12, #15 |
| "the animation stutters" / "scrolling janks" | #22, #24 |
| "the animation looks wrong / flickers / lands weird" | #9, #16 |
| "it breaks on my tablet / foldable / in split screen" | #6, #7, #13, #19, #21 |
| "TalkBack reads it wrong" / "I can't tap it" | #23, #25 |
| "the wavy thing is a hairline" / "it's just a fuzzy circle" | #5, #18 |
| "the app bar doesn't collapse" | #17 |
| "the FAB menu is cut off" | #16 |
| "dark mode is broken" / "text disappears" | #11, #20 |
| "there are two bottom bars" | #14 |

---

## 1. Bare `MaterialTheme` instead of `MaterialExpressiveTheme`

**Symptom.** "I used Expressive components everywhere and it still feels like normal Material 3."
"I switched to the expressive motion specs and nothing changed."

**Cause.** The 3-param `MaterialTheme(colorScheme, typography, shapes)` overload — the one every
pre-Expressive project template emits — resolves `motionScheme` to **`MotionScheme.standard()`**.
`MaterialTheme.motionScheme` still compiles everywhere and still returns valid springs; they just
never overshoot. The ~21 physics-driven Material components silently run standard motion. There is no
warning, no crash, and no amount of screen-level work fixes it.

**Confirm.**

```bash
rg -n --type kotlin 'MaterialExpressiveTheme\(|MaterialTheme\('
```

Zero `MaterialExpressiveTheme` hits in an app claiming to be Expressive **is** the finding. Then check
for a *second* offence: a nested plain `MaterialTheme(...)` inside a screen, dialog, bottom sheet or
`@Preview` that resets the subtree.

**Fix.**

```kotlin
@Composable
fun AppTheme(darkTheme: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    MaterialExpressiveTheme(
        colorScheme = if (darkTheme) darkColorScheme() else expressiveLightColorScheme(),
        typography = AppTypography,
        shapes = AppShapes,
        motionScheme = MotionScheme.expressive(),
        content = content,
    )
}
```

Retrofitting one subtree is legitimate, but it must inherit explicitly — `MaterialExpressiveTheme`'s
params are nullable and `null` means "use the **Expressive default**", not "inherit from ambient":

```kotlin
MaterialExpressiveTheme(
    colorScheme = MaterialTheme.colorScheme,
    typography = MaterialTheme.typography,
    shapes = MaterialTheme.shapes,
    motionScheme = MotionScheme.expressive(),
) { FloatingActionButtonMenu(expanded = expanded, button = { … }) { … } }
```

---

## 2. `motionScheme` omitted

**Symptom.** "Motion is inconsistent — some screens bounce, some don't."

**Cause.** `MaterialExpressiveTheme` defaults `motionScheme` to `MotionScheme.expressive()`, so
omitting it is *usually* harmless. It stops being harmless the moment anything in the tree is a plain
`MaterialTheme`, because `null` and inherit are different semantics — and it makes the app's motion
intent ungreppable.

**Confirm.**

```bash
rg -n --type kotlin -A6 'MaterialExpressiveTheme\(' | rg -v 'motionScheme'
```

**Fix.** Pass it explicitly. One line, survives a default change, and greps.

```kotlin
motionScheme = MotionScheme.expressive(),
```

Deliberately choosing `MotionScheme.standard()` for a utilitarian product is valid — but be consistent,
and never mix schemes within one product.

---

## 3. `ToggleButtonDefaults.shapes()` on alpha25+

**Symptom.** "It compiled last month and now it says unresolved reference." "The IDE offers no
deprecation quick-fix."

**Cause.** Both `ToggleButtonDefaults.shapes()` overloads were deprecated at
**`DeprecationLevel.HIDDEN`** in alpha25 (Icb433). HIDDEN means they exist in the binary for
compatibility but are **invisible to the Kotlin compiler** — you get an unresolved-reference error,
not a warning. And it is **not a rename**: `shapesFor` takes the button's **height as a `Dp`** and
derives the shape set from it, because Expressive sizes shapes by height. There is no zero-arg
`shapesFor()`.

**Confirm.**

```bash
rg -n --type kotlin 'ToggleButtonDefaults\.shapes\('
```

**Fix.** Two different targets depending on which case you had:

```kotlin
// defaulting case — feed the SAME Dp you gave the button
shapes = ToggleButtonDefaults.shapesFor(ButtonDefaults.MinHeight)

// customising case — the CONSTRUCTOR, not shapesFor
shapes = ToggleButtonShapes(
    shape = a,
    pressedShape = b,
    checkedShape = c,
)
// partial argument lists work:
shapes = ToggleButtonShapes(checkedShape = MaterialTheme.shapes.large)
```

`ButtonGroupDefaults.connected{Leading,Middle,Trailing}ButtonShapes()` are **unaffected** — they return
`ToggleButtonShapes` directly and were never deprecated. So is `ButtonDefaults.shapes()`, which is a
different object and is fine.

---

## 4. Opt-in sprinkling — and opt-in stripping

**Symptom.** Sprinkling: "the build fails with warnings-as-errors on redundant opt-ins." Stripping:
"I cleaned up the opt-ins and now `LoadingIndicator` won't resolve."

**Cause.** The blanket rule "assume every Expressive API needs
`@OptIn(ExperimentalMaterial3ExpressiveApi::class)`" was correct for **1.4.0** and is **wrong for the
1.5.0 alpha line.** Most of the surface graduated between alpha19 and alpha26. But a specific short
list did *not*, and two of those had their promotions **reverted in alpha19** and never restored.

**Still gated at alpha26 — never remove these:**

| API | Note |
| --- | --- |
| `LoadingIndicator`, `ContainedLoadingIndicator`, `LoadingIndicatorDefaults` | Declaration-level annotation on every overload |
| `MaterialShapes` + `RoundedPolygon.toShape()` / `.toPath()` / `Morph.toPath()` | `public sealed class MaterialShapes` is annotated |
| Expressive menu / `ExposedDropdownMenu` APIs | |
| `PullToRefreshDefaults.loadingIndicatorColor` / `loadingIndicatorContainerColor` | re-marked experimental in alpha21 |
| `ToggleButton` **size variants** (XSmall / Medium / Large) | base variants are fine |
| `ButtonDefaults.contentPaddingFor(buttonHeight)` — the **1-arg** overload | prefer the 3-arg one |
| `ButtonGroup` / `ButtonGroupScope` / `ButtonGroupDefaults` | contested; keep the opt-in defensively |

**Graduated — no Expressive opt-in needed:** `MaterialExpressiveTheme`, `expressiveLightColorScheme`,
`MotionScheme`, wavy progress indicators, `SplitButtonLayout`, `ToggleButton` base variants, FAB menu,
floating toolbars, app bars + flexible bars, search bars, carousels, `ShortNavigationBar` /
`WideNavigationRail`, expressive `ListItem` / `SegmentedListItem`. Several of those moved onto the
older, broader `ExperimentalMaterial3Api` instead — app bars, search bars, carousels, floating
toolbars, nav rails' tooltips — so you still need *an* opt-in there, just not the Expressive one.

**Confirm.**

```bash
rg -n --type kotlin 'ExperimentalMaterial3ExpressiveApi|ExperimentalMaterial3Api'
rg -n 'material3\s*=' --glob '*.toml'      # what pin are we actually on?
```

**Fix.** For an app module committed to Expressive, the global opt-in is the pragmatic default — it is
now **insurance** against the remaining gated set and against alpha26's unenumerated API-review sweep,
not a prerequisite:

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

On **1.4.0 stable** the blanket rule *is* still correct — opt in everywhere.

---

## 5. Wavy indicator stroke passed in dp

**Symptom.** "The wavy progress ring is a hairline on my phone but looks fine in the preview."
"The stroke is a different thickness on different devices."

**Cause.** `stroke` and `trackStroke` take an
`androidx.compose.ui.graphics.drawscope.Stroke`, whose `width` is a **`Float` in pixels**. Meanwhile
`wavelength`, `gapSize` and `stopSize` on the same call are `Dp`. `Stroke(width = 8f)` written meaning
"8dp" renders 8 physical pixels — a hairline at 3× density. Passing `8.dp.value` is equally wrong: it
produces a stroke whose physical size changes per device. The non-wavy `CircularProgressIndicator`
takes `strokeWidth: Dp`, which is exactly why people get it wrong.

**Confirm.**

```bash
rg -n --type kotlin -A3 'stroke\s*=\s*Stroke\(|trackStroke\s*='
```

Any `Stroke(width = <number>f)` or `Stroke(width = <n>.dp.value)` is a hit.

**Fix.** Convert with `LocalDensity`, and `remember` the result so it is not reallocated per frame:

```kotlin
val density = LocalDensity.current
val stroke = remember(density) {
    Stroke(width = with(density) { 12.dp.toPx() }, cap = StrokeCap.Round)
}

CircularWavyProgressIndicator(
    progress = { fraction },
    modifier = Modifier.size(240.dp),
    stroke = stroke,
    trackStroke = stroke,
    wavelength = 40.dp,      // Dp — do NOT convert
    gapSize = 8.dp,          // Dp
)
```

`cap = StrokeCap.Round` is not decoration — square caps chop the wave crests. androidx's own defaults
do exactly this conversion.

---

## 6. `when` chain on size class running smallest-first

**Symptom.** "It works on a phone but my tablet and desktop both get the phone-ish layout."
"The Large/XL branches never run."

**Cause.** All three predicates are `>=`. `isWidthAtLeastBreakpoint(600)` is true for a 1920dp window,
so a chain that tests Medium first matches Medium for **every** window ≥600dp. androidx's own KDoc:
"these methods are order dependent … selection should normally be ordered from larger to smaller
breakpoints."

**Confirm.**

```bash
rg -n --type kotlin -A6 'isWidthAtLeastBreakpoint|isHeightAtLeastBreakpoint'
```

Read the branch order. Also grep for `containsWidthDp` / `containsHeightDp` — **they do not exist at
any version**; if you see them, the code was written from a hallucinated API.

**Fix.**

```kotlin
val wsc = currentWindowAdaptiveInfoV2().windowSizeClass
when {
    wsc.isWidthAtLeastBreakpoint(WindowSizeClass.WIDTH_DP_EXTRA_LARGE_LOWER_BOUND) -> { /* ≥1600 */ }
    wsc.isWidthAtLeastBreakpoint(WindowSizeClass.WIDTH_DP_LARGE_LOWER_BOUND)       -> { /* ≥1200 */ }
    wsc.isWidthAtLeastBreakpoint(WindowSizeClass.WIDTH_DP_EXPANDED_LOWER_BOUND)    -> { /* ≥840  */ }
    wsc.isWidthAtLeastBreakpoint(WindowSizeClass.WIDTH_DP_MEDIUM_LOWER_BOUND)      -> { /* ≥600  */ }
    else -> { /* compact */ }
}
```

Width has **five** buckets (0 / 600 / 840 / 1200 / 1600); height has **three** (0 / 480 / 900). There
is no `HEIGHT_DP_LARGE_LOWER_BOUND` — `BREAKPOINTS_V2` adds width breakpoints only.

---

## 7. `currentWindowAdaptiveInfo()` instead of `V2`

**Symptom.** "My desktop window gets the tablet layout." "The Large and XL branches never fire even
though the `when` order is right."

**Cause.** `currentWindowAdaptiveInfo()` is **deprecated in adaptive 1.3.0** and defaults
`supportLargeAndXLargeWidth = false`, so it computes against `BREAKPOINTS_V1` and **silently clamps
everything ≥840dp to Expanded**. A 1920dp window reports `minWidthDp == 840`. You also lose the
3-partition directive and the 412dp `defaultPanePreferredWidth`.

**Confirm.**

```bash
rg -n --type kotlin 'currentWindowAdaptiveInfo\(\)'
```

**Fix.**

```kotlin
val info = currentWindowAdaptiveInfoV2()
val wsc = info.windowSizeClass        // androidx.window.core.layout.WindowSizeClass
val posture = info.windowPosture
```

Neither `currentWindowAdaptiveInfoV2()` nor `WindowSizeClass` and its predicates need an opt-in.
Watch the imports: there are **three** `WindowSizeClass` types —
`androidx.window.core.layout.WindowSizeClass` (current),
`androidx.compose.material3.windowsizeclass.WindowSizeClass` (legacy artifact), and the deprecated
`WindowWidthSizeClass` / `WindowHeightSizeClass` enums.

---

## 8. Expression applied uniformly

**Symptom.** "I made everything expressive and it still doesn't feel expressive." "It just looks
busy." "The designer says it's loud but flat."

**Cause.** **Expression is relational.** A shape is emphatic only by "break[ing] from the surrounding
shape style." If every card is `extraLargeIncreased`, every button is filled `primary`, and every
state change animates, then every element scores identically — which means zero relative to each
other. Uniform expressiveness has no contrast and is *worse* than baseline M3, not better.

**Confirm.** Run the divergence count. Walk the top-level composable, list every visually distinct
element, and score each on four axes against **this screen's own baseline**:

| Axis | Diverges when… | Read from |
| --- | --- | --- |
| Size | ≥1.5× the median, or a 96/136dp button among 40/56dp ones | `.size()`, `.height()`, `ButtonDefaults` size params |
| Shape | different corner token from neighbours, or a polygon among rounded rects | `.clip()`, `shape =`, `MaterialTheme.shapes.*` |
| Color | an accent where neighbours use `surface*` | `containerColor =`, `.background()` |
| Motion | its own animation / morph / shared-element key while neighbours are static | `animate*AsState`, `shapes =` with a pressedShape, `sharedBounds` |

Elements with **≥2 divergent axes** are hero candidates. 0 → no hero (fine for a settings sub-page, a
failure for home/library/player). 1–2 → plausible. **≥3 → too many.**

Also check the product-level count: Material's budget is "one or two hero moments **in your product**."
Twenty screens each with one well-behaved hero passes the per-screen check twenty times and violates
the canon by 20×.

**Fix.** The fix is **subtractive**, and say so out loud — the user asked to add expression and the
answer is to remove some.

1. Name the one element tied to the screen's job. It keeps its heroism.
2. Demote everything else to **one** divergent axis (not zero — demotion is not deletion).
3. Verify both qualifying questions on what survives: *is this emotionally impactful?* and *is this a
   key interaction in the product?* Both must be yes.
4. Re-check that the baseline is now genuinely calm, or the hero has nothing to break from.

---

## 9. Spatial spec on an effects property (and vice versa)

**Symptom.** "The color transition passes through a weird shade." "The shadow pumps." "Switching to
the expressive scheme changed nothing."

**Cause.** A **bug, not a preference.** Expressive spatial springs are underdamped by design (damping
0.8 default, 0.6 fast) and overshoot; Material's `MotionScheme` KDoc says effects motion "shouldn't
have any overshoot," and all six effects specs are critically damped (1.0) so it cannot. Concretely:

- **Color** — the interpolation overshoots out of gamut and visibly detours through a hue in neither
  endpoint.
- **Elevation** — the shadow pumps past its resting depth and back; reads as a rendering error.
- **Alpha** — state this one precisely. `graphicsLayer.alpha` is **clamped to `[0f, 1f]`** by the
  render node, so there is *no* visible spike past full opacity. The damage is that the value rings:
  the fade settles later than the spec advertises and out of step with every other fade in the app.
  Do not claim a visible flash on alpha.

The inverse — an effects spec on a size or offset — is silent: effects specs are **identical** between
`MotionScheme.expressive()` and `MotionScheme.standard()`, so switching schemes does nothing and the
symptom is "expressive motion looks the same as standard."

**Confirm.**

```bash
rg -n --type kotlin -B2 -A2 'animateColorAsState|fadeIn\(|fadeOut\(|alpha ='
rg -n --type kotlin -B2 -A2 'animateDpAsState|slideIn|slideOut|scaleIn|scaleOut'
```

**Fix.**

```kotlin
// color / opacity / elevation → EFFECTS
val color by animateColorAsState(target, MaterialTheme.motionScheme.defaultEffectsSpec(), label = "c")

// position / size / rotation / corner radius → SPATIAL
val size by animateDpAsState(target, MaterialTheme.motionScheme.defaultSpatialSpec(), label = "s")

// combined transitions: each half gets its own family
val motionScheme = MaterialTheme.motionScheme
enter = fadeIn(motionScheme.defaultEffectsSpec()) + scaleIn(motionScheme.defaultSpatialSpec())
exit  = fadeOut(motionScheme.defaultEffectsSpec()) + scaleOut(motionScheme.defaultSpatialSpec())
```

---

## 10. Hardcoded `tween` everywhere

**Symptom.** "Animations feel mechanical." "Interrupting a gesture makes the animation snap back and
restart."

**Cause.** Duration + easing is the pre-Expressive model. A curve **cannot be retargeted mid-flight** —
it restarts or cross-fades — whereas a spring can be redirected from its current position *and
velocity*. That is the actual reason to migrate, not aesthetics. Hardcoded durations also bypass the
per-device token scaling ("token values adjust per device type").

**Confirm.**

```bash
rg -n --type kotlin 'tween\(|durationMillis|LinearEasing|FastOutSlowInEasing|LinearOutSlowInEasing|CubicBezierEasing'
rg -c --type kotlin 'motionScheme\.(fast|default|slow)(Spatial|Effects)Spec'
```

A high first count with a near-zero second count is the finding.

**Fix.** Map by **what is animating** first, then sanity-check against the old duration:

| Legacy | Replace with |
| --- | --- |
| `tween(100)` on color/alpha | `fastEffectsSpec()` |
| `tween(150–200)` on color/alpha | `defaultEffectsSpec()` |
| `tween(300–400)` on color/alpha | `slowEffectsSpec()` |
| `tween(100–150)` on size/offset/scale | `fastSpatialSpec()` |
| `tween(200–300)` on size/offset/scale | `defaultSpatialSpec()` — the most common substitution |
| `tween(350+)` on size/offset/scale | `slowSpatialSpec()` |
| `spring(dampingRatio = MediumBouncy)` | `fastSpatialSpec()` |

**Do not migrate:** determinate progress (must stay linear), `infiniteRepeatable`, `keyframes`,
`snap()`, and anything synced to an external clock. Springs have no duration you can match to audio
position or a countdown.

`MaterialTheme.motionScheme` is `@Composable`, so hoist it once per screen
(`val motionScheme = MaterialTheme.motionScheme`) to use it inside coroutines and gesture callbacks.

---

## 11. `expressiveDarkColorScheme()`

**Symptom.** "Unresolved reference: expressiveDarkColorScheme."

**Cause.** **It does not exist**, at any version. `expressiveLightColorScheme()` exists and differs
from `lightColorScheme()` in exactly four roles (`onPrimaryContainer`, `onSecondaryContainer`,
`onTertiaryContainer`, `onErrorContainer`). There is no dark counterpart because the dark scheme
already has the contrast those four overrides buy in light. This is a hallucination that appears in
generated code, blog posts and model memory alike.

**Confirm.**

```bash
rg -n --type kotlin 'expressiveDarkColorScheme'
```

Any hit is a finding.

**Fix.** What androidx's own sample does:

```kotlin
MaterialExpressiveTheme(
    colorScheme = if (isSystemInDarkTheme()) darkColorScheme() else expressiveLightColorScheme(),
    motionScheme = MotionScheme.expressive(),
) { content() }
```

---

## 12. `MaterialShapes` members that don't exist

**Symptom.** "Unresolved reference: Cookie5Sided."

**Cause.** The cookie family is **4, 6, 7, 9, 12 only**. `Cookie5Sided`, `Cookie8Sided`,
`Cookie10Sided`, `Cookie11Sided` are compile errors that models write constantly because the existing
names imply a full series. Clovers are similarly limited: **`Clover4Leaf` and `Clover8Leaf` only** — no
5/6/7-leaf.

**Confirm.**

```bash
rg -n --type kotlin 'MaterialShapes\.\w+'
```

Check each hit against the 35-name catalog:

`Circle, Square, Slanted, Arch, Fan, Arrow, SemiCircle, Oval, Pill, Triangle, Diamond, ClamShell,
Pentagon, Gem, Sunny, VerySunny, Cookie4Sided, Cookie6Sided, Cookie7Sided, Cookie9Sided,
Cookie12Sided, Ghostish, Clover4Leaf, Clover8Leaf, Burst, SoftBurst, Boom, SoftBoom, Flower, Puffy,
PuffyDiamond, PixelCircle, PixelTriangle, Bun, Heart`

**Fix.** Pick the nearest existing member. If you genuinely need a shape the catalog lacks, ship a
vector drawable — that is what real apps do rather than inventing a `MaterialShapes` name.

While you are here, two adjacent errors in the same family:

- `MaterialShapes`, `RoundedPolygon.toShape()` / `.toPath()` and `Morph.toPath()` **still need
  `@OptIn(ExperimentalMaterial3ExpressiveApi::class)` at alpha26** (see #4).
- There is **no `Morph.toShape()`**, and `Morph.toPath` is **not** `@Composable` — correctly, since it
  is called from draw/outline code.

---

## 13. The list-detail role trap

**Symptom.** "The panes are in the wrong order." "The wrong pane hides when the window narrows."
"My custom adapt strategies apply to the wrong pane."

**Cause.** The scaffold role names are **aliases over `Primary` / `Secondary` / `Tertiary`**, and in
list-detail the mapping is counter-intuitive:

| List-detail role | Underlying `ThreePaneScaffoldRole` |
| --- | --- |
| `ListDetailPaneScaffoldRole.List` | **Secondary** |
| `ListDetailPaneScaffoldRole.Detail` | **Primary** |
| `ListDetailPaneScaffoldRole.Extra` | Tertiary |

The "first" pane is **not** `Primary`. Getting it backwards silently reverses adapt strategies and
pane order — no compile error, just wrong behaviour on resize. Horizontal pane order also **differs
between the two scaffolds**: list-detail is Secondary / Primary / Tertiary, supporting-pane is Primary
/ Secondary / Tertiary, and both `PaneOrder` values are `internal` so you cannot inspect them.

**Confirm.**

```bash
rg -n --type kotlin 'ThreePaneScaffoldRole\.|PaneScaffoldRole\.|adaptStrategies\('
```

**Fix.** Use the scaffold-specific role constants and never translate by hand:

```kotlin
scope.launch { navigator.navigateTo(ListDetailPaneScaffoldRole.Detail, itemId) }
```

Two related traps in the same area: `SupportingPaneScaffoldDefaults.adaptStrategies()` defaults the
supporting pane to **`Reflow`**, not `Hide` — pass custom strategies and you lose the
below-the-main-content behaviour unless you re-specify it. And `navigateTo` / `navigateBack` /
`seekBack` are **`suspend`**; wrap them in `rememberCoroutineScope().launch { }`.

---

## 14. Two navigation containers on one page

**Symptom.** "There are two bars at the bottom." "The content area is tiny." "Where am I in the app?"

**Cause.** Either a `ShortNavigationBar` plus a floating/docked toolbar on the same page, or a rail
plus a nav bar at Medium width. Material's rule: **"Show the navigation bar on primary pages, and
toolbars on subsequent pages with actions."** A page needing both is doing two jobs.

**Confirm.**

```bash
rg -n --type kotlin -A20 'Scaffold\(' | rg 'NavigationBar|ShortNavigationBar|NavigationRail|WideNavigationRail|FloatingToolbar|BottomAppBar|FlexibleBottomAppBar'
```

**Fix.** One per page.

- Primary destinations → nav container only.
- Action pages one level down → toolbar only.
- Using a `HorizontalFloatingToolbar` **as** the nav container is a legitimate, deliberate choice — and
  once you make it, there is no additional `ShortNavigationBar`.

Related: `BottomAppBar` is deprecated at the design level ("should be replaced with the docked
toolbar"). Use `FlexibleBottomAppBar` for a docked bar or `HorizontalFloatingToolbar` for contextual
actions.

---

## 15. The rest of the alpha25 / alpha26 hard breaks

**Symptom.** "It compiled on alpha24 and I get five different unresolved references on alpha26."

**Cause.** alpha25 and alpha26 landed several source-incompatible changes at once. If code (or a
model's memory of the API) was written against alpha24 or earlier, fix these before debugging anything
else.

| Was | Is | Level |
| --- | --- | --- |
| `ToggleButtonDefaults.shapes(...)` | `shapesFor(height)` / `ToggleButtonShapes(...)` | **HIDDEN** — see #3 |
| `animateWidth(is, compressionLimit: PaddingValues)` | `animateWidth(is, compressionLimit: Dp)` or `animateWidth(is)` | type change |
| `behavior.scrollOffset` / `.scrollOffsetLimit` / `.contentOffset` on `SearchBarScrollBehavior` | `behavior.scrollState.scrollOffset` etc. on the new **`SearchBarScrollState`** | removed |
| `ButtonGroup(modifier, horizontalArrangement, content)` | `ButtonGroup(overflowIndicator, modifier, expandedRatio, horizontalArrangement, verticalAlignment, content)` | overload removed in alpha22 |
| `interface ButtonGroupScope` | `sealed interface ButtonGroupScope` | breaks implementers |
| `DropdownMenuItem(trailingIcon = …)` on the **shape / checked / selected** overloads | `trailingContent = …` | HIDDEN per-overload — the plain overload still uses `trailingIcon` |
| `ComponentOverride` APIs, `LocalXOverride`, `@ExperimentalMaterial3ComponentOverrideApi` | **removed, no replacement** | rewrite required |
| `SliderState` implementing `DraggableState` | no longer public | removed |
| `TonalToggleButton` | `FilledTonalToggleButton` | WARNING only |
| `SplitButtonDefaults.leadingButtonShapes(CornerSize)` / `trailingButtonShapes(CornerSize)` | `leadingButtonShapesFor(Dp)` / `trailingButtonShapesFor(Dp)` | WARNING. **`SplitButtonLayout` is NOT in this table** — it is current and undeprecated; an earlier reading of the alpha25 note as a `SplitButtonLayout` → `SplitButton` composable rename was **wrong**, no `SplitButton` composable exists. |
| `isAtTop` on scroll behaviors | `isAtStart` | alpha16 |

**Confirm.** Run this before building:

```bash
rg -n --type kotlin 'ToggleButtonDefaults\.shapes\(|animateWidth\(|\.scrollOffset|ComponentOverride|trailingIcon\s*=|TonalToggleButton|SplitButtonDefaults\.(leading|trailing)ButtonShapes\(|isAtTop'
```

**Fix.** Apply the table. Two notes:

- `compressionLimit` is a `Dp` now — `animateWidth(source, compressionLimit = 8.dp)`. The **1-arg**
  `animateWidth(source)` is the portable form and compiles on every pin.
- The `DropdownMenuItem` rename is **per-overload**, not a global find-and-replace.

alpha26 also contains an **unenumerated** API sweep ("Updated the public API surface to align with
recent API review feedback", I71aff). If something fails to resolve and is not in this table, that is
the likely cause. **Trust the compiler over any document, including this one.**

---

## 16. FAB menu clipped, and back doesn't close it

**Symptom.** "The FAB menu items are cut off." "Only the top item is visible." "Back exits the screen
instead of closing the menu."

**Cause.** The `Scaffold` FAB slot is measured to the **collapsed** FAB, so the expanded menu is
clipped to that box. Separately, `FloatingActionButtonMenu` does not install a back handler.

**Confirm.**

```bash
rg -n --type kotlin -B4 -A4 'FloatingActionButtonMenu\('
```

Look for `wrapContentSize(unbounded = true)` above it and a `BackHandler` nearby.

**Fix.**

```kotlin
var expanded by rememberSaveable { mutableStateOf(false) }
BackHandler(enabled = expanded) { expanded = false }

Scaffold(floatingActionButton = {
    Box(Modifier.wrapContentSize(unbounded = true)) {
        FloatingActionButtonMenu(
            expanded = expanded,
            button = { ToggleFloatingActionButton(expanded, { expanded = it }) { … } },
        ) { /* 2–6 FloatingActionButtonMenuItem */ }
    }
}) { padding -> … }
```

Full opener with semantics and the icon cross-fade: see `dos-and-donts.md` → "Speed dial / stacked
small FABs". While you are in here, check the count and the pairing: the spec is **2–6 items**, the
menu must open in the same place as its FAB, and **"Fab menu is not used with extended FABs."**

---

## 17. Scroll behavior wired only halfway

**Symptom.** "The large app bar never collapses." "The floating toolbar never hides on scroll."

**Cause.** A scroll behavior has two halves: the component consumes it, and an ancestor feeds it
nested-scroll deltas. Passing `scrollBehavior =` without
`Modifier.nestedScroll(scrollBehavior.nestedScrollConnection)` produces a bar that simply never moves,
with no error anywhere.

**Confirm.**

```bash
rg -n --type kotlin -A10 'scrollBehavior\s*=' | rg -c 'nestedScroll'
```

**Fix.**

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
    modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),   // the other half
) { padding -> LazyColumn(Modifier.padding(padding)) { … } }
```

Match `Scaffold(containerColor = …)` to the bar's container color so the collapse has no seam. Pick the
behavior deliberately: `exitUntilCollapsedScrollBehavior()` for flexible bars (collapse to the small
height and stay), `enterAlwaysScrollBehavior()` for small bars on long content,
`pinnedScrollBehavior()` for a bar that only lifts its container color.

---

## 18. `LoadingIndicator` for the wrong kind of wait, and wavy at the wrong size

**Symptom.** "The spinner runs for thirty seconds." "The progress bar is just a fuzzy circle." "It
switches from a blob to a bar halfway through."

**Cause.** Three distinct misuses:

1. `LoadingIndicator` is "designed to show progress that loads in **under five seconds**." A network
   upload, backup or model download fails that test.
2. It must **not** be used "if processes transition from indeterminate to determinate states" — a
   download that starts as "connecting…" and then reports bytes should be a progress indicator *from
   the start*, not a `LoadingIndicator` that swaps mid-flight.
3. Wavy indicators below roughly **40dp** stop reading as wavy: "at very small sizes, the wavy shape
   may not be as visible." You pay the complexity and get no signal.

Determinate-vs-indeterminate is a **semantic** claim, not a visual one. Choosing wrong misreports
state to assistive tech.

**Confirm.**

```bash
rg -n --type kotlin 'LoadingIndicator|ContainedLoadingIndicator'   # then trace what each awaits
rg -n --type kotlin -B3 -A3 'WavyProgressIndicator'                # then read the .size()
```

**Fix.**

```kotlin
// known progress → determinate, from the very first frame
LinearWavyProgressIndicator(
    progress = { bytesSent.toFloat() / totalBytes },
    modifier = Modifier.fillMaxWidth(),
)

// genuinely unknown, and short → the Expressive spinner (opt-in still required)
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
LoadingIndicator()

// small inline spinner → not wavy
CircularProgressIndicator(modifier = Modifier.size(20.dp))
```

Also: **"Only one type should represent each kind of activity in an app."** Do not use a linear bar for
network fetches on one screen and a circular spinner for the same class of fetch on another. And note
the default `indicatorAmplitude` flattens the wave to zero below 10% and above 95% progress — a bar
that looks flat at both ends is working as designed; pass `amplitude = { 1f }` to override.

---

## 19. `Configuration.screenWidthDp` / `isTablet` branching

**Symptom.** "It breaks in split-screen." "On a foldable it uses the tablet layout while folded."
"Freeform windows get the wrong layout."

**Cause.** `LocalConfiguration.current.screenWidthDp` is the **device screen**, not the app's window.
Window size classes are explicitly not for `isTablet`-style logic: "window size classes are determined
by available window space, not physical device type."

**Confirm.**

```bash
rg -n --type kotlin 'screenWidthDp|smallestScreenWidth|isTablet|LocalConfiguration'
```

**Fix.**

```kotlin
val wsc = currentWindowAdaptiveInfoV2().windowSizeClass
if (wsc.isWidthAtLeastBreakpoint(WindowSizeClass.WIDTH_DP_EXPANDED_LOWER_BOUND)) {
    TwoPaneLayout()
} else {
    SinglePaneLayout()
}
```

Note the threshold is **840dp** (Expanded), not 600: **Medium width is single-pane** by Material's own
directive, and `calculatePaneScaffoldDirective` gives a 700dp window `maxHorizontalPartitions = 1`.
`calculatePaneScaffoldDirectiveWithTwoPanesOnMediumWidth` exists, and androidx's KDoc recommends
against it ("can make your layout look too packed").

Two related resize failures worth checking at the same time:

- **Locked orientation / `resizeableActivity="false"`.** On Android 16 / API 36 large screens can no
  longer opt out of resizing; the lock is ignored and untested paths run.
- **State lost on resize.** `remember { mutableStateOf(...) }` for selected tab, scroll position or
  expanded pane resets on every fold and rotate. Use `rememberSaveable`, and make any
  `ThreePaneScaffoldNavigator` content key `@Parcelize` — the navigator is itself `rememberSaveable`.

---

## 20. Hardcoded colors and mispaired `on-*` roles

**Symptom.** "Text disappears in dark mode." "The card is white in dark mode." "Contrast fails
accessibility scanning."

**Cause.** Two related habits. Hardcoded `Color(0x…)` / `Color.White` / `Color.Black` breaks dark mode,
dynamic color, AMOLED and contrast in a single edit. Mispairing — `onPrimary` on a `primaryContainer`
background — breaks the one guarantee Material actually makes: **"all color pairs provide a minimum of
3:1 color contrast"** applies to *matched* pairs only, and 3:1 is the floor for UI elements, not a pass
for small text (which needs 4.5:1).

**Confirm.**

```bash
rg -n --type kotlin 'Color\(0x|Color\.White|Color\.Black|Color\.Gray' --glob '!**/theme/**'
rg -n --type kotlin 'containerColor\s*=|contentColor\s*=|tint\s*='
```

Then check each pair by eye against the role table; compute the actual WCAG ratio rather than guessing.

**Fix.**

```kotlin
Surface(
    color = MaterialTheme.colorScheme.primaryContainer,
    contentColor = MaterialTheme.colorScheme.onPrimaryContainer,   // or omit — contentColorFor does it
) { Text("Saved") }
```

Adjacent failures in the same area:

- **AMOLED by overriding two roles.** `surface` and `background` alone leaves the whole
  `surfaceContainer*` ramp grey, so a "pure black" app ships grey cards. Override all seven, or use a
  generator that darkens the ramp — and gate it on `darkTheme && preference`.
- **`surfaceVariant` and `surface.copy(alpha = 0.08f)` as the container model.** That is the M2 overlay
  system M3 replaced. Use the tonal ramp: `surfaceContainerLow` / `surfaceContainer` /
  `surfaceContainerHigh`.
- **Seeded / image-derived schemes with no readability guard.** An album-art seed can produce an
  `onSurfaceVariant` that fails against `surfaceContainer`. Test with extreme seeds (near-black art,
  neon art, desaturated art).

---

## 21. Nav container rebuilt inside the `NavDisplay` / `NavHost`

**Symptom.** "The bottom bar flickers on every navigation." "Shared element transitions don't work."
"The card doesn't fly into the detail screen, it just cross-fades."

**Cause.** A nav container declared inside each destination is recreated per destination: it re-enters
on every navigation (visible flicker) and it destroys cross-destination shared elements, because the
shared bounds have no common parent to fly through.

**Confirm.**

```bash
rg -n --type kotlin -B10 'NavDisplay\(|NavHost\('     # where does the Scaffold live?
rg -c --type kotlin 'SharedTransitionLayout|sharedBounds|sharedElement'
```

Zero shared-transition hits in an app with list→detail navigation is itself a finding — that is the
highest-value expressive motion available and the most commonly skipped.

**Fix.**

```kotlin
SharedTransitionLayout {
    Scaffold(bottomBar = { ShortNavigationBar { … } }) { padding ->
        NavDisplay(backStack = backStack, modifier = Modifier.padding(padding)) { key -> … }
    }
}
```

While you are here, check predictive back: a bare `BackHandler` that just pops is **not** predictive
back — there is no preview frame. Give the root display a predictive-pop spec, make the pop the inverse
of the push, and set `android:enableOnBackInvokedCallback="true"` for Android 15 and lower.

---

## 22. Animating layout where a draw-phase property would do

**Symptom.** "Scrolling janks while the card expands." "Everything below the animation jitters."
"Frame drops on a mid-range device."

**Cause.** Cost is dominated by **which phase the animation invalidates**, not by the spec:

| Phase | Cost | Triggered by |
| --- | --- | --- |
| Composition | highest — re-runs composable code | reading an animated `State` in a composable body; `animateContentSize`; `AnimatedContent` |
| Layout | high — re-measures the subtree | `Modifier.size`, `padding`, `offset(Dp)`, `weight`, `fillMaxWidth(fraction)` |
| Draw | low — one layer re-render | `graphicsLayer { }`, `offset { IntOffset }`, `drawBehind`, `alpha`, `rotate`, `scale` |

**Confirm.**

```bash
rg -n --type kotlin -B3 'Modifier\.(size|padding|offset)\(' | rg 'animate'
rg -n --type kotlin 'animateContentSize'
rg -n --type kotlin 'Modifier\.(alpha|offset|scale|rotate)\('   # value overloads
```

**Fix.**

```kotlin
// draw phase — does not re-measure, and does NOT change the touch target
Modifier.graphicsLayer { alpha = animatedAlpha; scaleX = s; scaleY = s }

// layout phase with a deferred read — better than Modifier.offset(x.dp, y.dp)
Modifier.offset { IntOffset(x.roundToInt(), y.roundToInt()) }
```

`animateContentSize` is a **layout** animation: correct for text expanding to more lines, expensive on
a deep subtree, never right on a screen-level container. And never animate a size when a scale will
do — scaling in `graphicsLayer` preserves the touch target, which is usually what you want for press
feedback.

---

## 23. Touch targets under 48dp

**Symptom.** "I keep missing the button." "Accessibility scanner flags every icon button."

**Cause.** The minimum is **48 × 48dp** — "a physical size of about 9mm, regardless of screen size" —
separated by ≥8dp. The Expressive size scale makes this newly easy to violate: an XSmall button is
**32dp** tall by spec and a Small button is 40dp. Their *visual* bounds may be small; their targets may
not. Expressive components are also generally shorter than before (nav bar 80→64dp), so the margin is
thinner than it used to be.

**Confirm.**

```bash
rg -n --type kotlin -B4 '\.size\([0-9]+\.dp\)'          # on clickable elements
rg -n --type kotlin 'IconButton|IconToggleButton'
rg -c --type kotlin 'minimumInteractiveComponentSize'
```

**Fix.**

```kotlin
IconButton(
    onClick = onClear,
    modifier = Modifier.minimumInteractiveComponentSize().size(32.dp),
) { Icon(Icons.Filled.Close, contentDescription = "Clear") }

// custom clickables
Modifier.sizeIn(minWidth = 48.dp, minHeight = 48.dp).clickable(onClick = onPlay)
```

A connected button group's **2dp** internal spacing is spec, not a violation — the members are one
object, so the 8dp separation rule applies to the group, not between members. Do not file that.

Also check 200% font scale: fixed `.height()` around `Text` clips. Use `Modifier.heightIn(min = …)` and
let text wrap.

---

## 24. `Morph` / `toShape()` recreated per frame, and progress passed as a value

**Symptom.** "The morph animation stutters." "The list drops frames while scrolling."

**Cause.** Three related allocations in hot paths:

1. **`Morph(a, b)` runs a feature-mapping algorithm** between two polygons. Constructed inside an
   animated composable or a `Shape.createOutline` body, that runs **every frame**.
2. **`RoundedPolygon.toShape()` is `@Composable` and allocates a new `Shape` per call.** Per lazy item
   is survivable; inside `drawBehind` or `graphicsLayer` it is per-frame and is not.
3. **`progress = fraction` instead of `progress = { fraction }`** on progress indicators recomposes the
   whole enclosing composable every tick. The lambda parameter exists specifically to keep the
   recomposition scope at the leaf.

**Confirm.**

```bash
rg -n --type kotlin 'Morph\(|RoundedPolygon\(|\.toShape\(' | rg -v 'remember'
rg -n --type kotlin -B6 '\.toShape\('           # is the enclosing lambda a LazyColumn item or a draw scope?
rg -n --type kotlin 'progress\s*=\s*[a-z]'      # value, not lambda
rg -n --type kotlin -A8 'items\(|itemsIndexed\('  # lambdas allocated per item
```

**Fix.**

```kotlin
// MaterialShapes / toShape() / toPath() are still @ExperimentalMaterial3ExpressiveApi at alpha26.
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun TrackList(tracks: List<Track>, pressed: Boolean, fraction: Float) {
    val morph = remember { Morph(MaterialShapes.Circle, MaterialShapes.Cookie9Sided) }
    val progress by animateFloatAsState(
        targetValue = if (pressed) 1f else 0f,
        animationSpec = MaterialTheme.motionScheme.fastSpatialSpec(),
        label = "morph",
    )
    // pass `morph` plus `{ progress }` into a custom Shape; call morph.toPath(progress, path) inside it

    // toShape() is @Composable — hoisted ABOVE the LazyColumn, never inside items { }
    val cookieShape = MaterialShapes.Cookie4Sided.toShape()
    LazyColumn {
        items(tracks, key = { it.id }) { track ->
            Box(Modifier.size(56.dp).clip(cookieShape)) { … }
        }
    }

    LinearWavyProgressIndicator(progress = { fraction })       // lambda
}
```

Also hoist item lambdas — `onClick = { viewModel.select(item.id) }` allocates per recomposition and
makes the item composable unskippable, which in a scrolling list is every item, every frame. And add
`key = { … }` to `items`.

---

## 25. Missing semantics on Expressive controls

**Symptom.** "TalkBack says 'checked' for something that's expanded." "A segmented control reads as
five separate buttons and never says 'two of five'." "There's no way to close the FAB menu with switch
access."

**Cause.** The new components carry real state that the default semantics do not describe:

- A **connected single-select group is a radio group**. Without `Role.RadioButton` TalkBack announces N
  independent checkboxes.
- The **`checked = false` action trick** (a `ToggleButton` used as a plain action to get the group
  shapes) announces a toggle whose checked state never changes, unless you override `Role.Button`.
- A **`ToggleFloatingActionButton`** is expanded/collapsed, not checked/unchecked.
- An **expanded FAB menu** is a modal-ish overlay: without `isTraversalGroup` its items interleave with
  the content behind it, and without a custom action there is no keyboard/switch-access dismissal.
- **Icon-only controls** need the description on the *control*, not the icon.

**Confirm.**

```bash
rg -n --type kotlin 'connectedLeadingButtonShapes|ConnectedSpaceBetween' -A10 | rg -c 'Role\.'
rg -n --type kotlin -A6 'ToggleButton|IconToggleButton|ToggleFloatingActionButton' | rg -c 'stateDescription'
rg -n --type kotlin -A20 'FloatingActionButtonMenu' | rg -c 'isTraversalGroup|customActions|traversalIndex'
rg -n --type kotlin -B6 'contentDescription\s*=\s*null'
```

**Fix.**

```kotlin
// connected single-select member (Role.Button for independent actions and the checked=false trick)
modifier = Modifier.semantics { role = Role.RadioButton }

// FAB-menu opener
modifier = Modifier.semantics {
    traversalIndex = -1f
    stateDescription = if (expanded) expandedLabel else collapsedLabel
    contentDescription = menuLabel
}

// last FAB-menu item — the detail almost every implementation misses
modifier = Modifier.semantics {
    isTraversalGroup = true
    customActions = listOf(
        CustomAccessibilityAction(closeMenuLabel) { onExpandedChange(false); true })
}

// icon-only control: name the control, not the icon
IconButton(onClick = onShuffle,
    modifier = Modifier.semantics { contentDescription = shuffleLabel },
) { Icon(Icons.Rounded.Shuffle, contentDescription = null) }
```

`contentDescription = null` is **correct** on a decorative icon inside an already-labelled control —
check the enclosing composable before filing it.

Two things **not** to "fix": floating toolbars and `FlexibleBottomAppBar` force-expand and disable
`scrollBehavior` under an active accessibility service — intentional, so forcing a toolbar collapsed
there is itself a defect. And do not zero out contrast geometry: the progress indicator's 4dp track gap
and 4dp stop indicator, and the slider's 6dp thumb–track gap, exist "to meet modern contrast
requirements."

---

## Honourable mentions

Real, common, but below the top 25:

| Mistake | Fix |
| --- | --- |
| `ShortNavigationBarItem(icon = …, modifier = …)` — missing `label` | `label` is a **required positional** parameter *before* `modifier`. Pass `label = null` for an unlabelled item; there is no `alwaysShowLabel`. |
| Expressive `ListItem` called with `headlineContent` | The Expressive overloads take the headline as the **trailing `content` lambda**. Both overload sets are on the classpath, so the error reads as "no applicable overload". |
| `Modifier.clip(...)` on a carousel item | Use `Modifier.maskClip(...)` — a `CarouselItemScope` modifier that clips to the animating mask. `maskBorder` for a matching stroke. |
| More than 5 destinations in a nav bar | Spec is 3–5. Overflow into the rail's expanded state or a "More" destination. |
| `MediumTopAppBar` / `LargeTopAppBar` in new code | The flexible variants replace them and add subtitle, wrapping and alignment. |
| Centring a `TopAppBar` title without a subtitle slot | `titleHorizontalAlignment` lives on the **subtitle overload**. Pass `subtitle = {}`. |
| `androidx.compose.material.*` (M2) imports | M2 `Text`/`Surface`/`MaterialTheme` ignore the M3 color/type/shape/motion system entirely. `androidx.compose.material.icons.*` is fine — that is the icon artifact, not M2 components. |
| `material3-adaptive-navigation-suite` pinned to `1.3.0` | It is in the **`androidx.compose.material3`** group and versions with material3 (`1.5.0-alpha26`), not with `material3.adaptive` (`1.3.0`). Two trains. |
| BOM silently downgrading an alpha pin | The explicit `material3` version must come **after** the `platform(...)` line. `compose-bom` never ships alphas. |
| `graphics-shapes` not declared while using `MaterialShapes` / `Morph` | It arrives transitively via material3 so it compiles today and breaks on a dependency bump. `implementation("androidx.graphics:graphics-shapes:1.1.0")`. |
| `ExpandedFullScreenSearchBar(windowInsets = someInsets)` | Its `windowInsets` is a **`@Composable () -> WindowInsets` lambda**, unlike every other material3 component. |
| `Shapes(extraSmall, small, medium, large, extraLarge)` | That is the five-arg secondary constructor; `largeIncreased` (20), `extraLargeIncreased` (32) and `extraExtraLarge` (48) silently stay at Material defaults. |
| `MaterialShapes` count guessing | 35 verified names. A source comment says "37"; do not invent the extras — check autocomplete at your pin. |

---

## The 90-second sanity check

Before shipping anything Expressive, in this order:

1. `rg -n --type kotlin 'MaterialExpressiveTheme\('` → non-zero, and `motionScheme` is passed. (#1, #2)
2. `./gradlew :app:compileDebugKotlin` actually runs. Do not assume. (#3, #15)
3. `rg -n --type kotlin 'tween\(|FastOutSlowInEasing'` vs `rg -c --type kotlin 'motionScheme\.'` (#10)
4. `rg -n --type kotlin -B2 'animateColorAsState|fadeIn\('` → effects specs? (#9)
5. `rg -n --type kotlin 'Color\(0x|Color\.White' --glob '!**/theme/**'` (#20)
6. Run the hero divergence count on the main screen: 0 or ≥3 is a finding. (#8)
7. `rg -n --type kotlin 'NavigationBar|FloatingToolbar|BottomAppBar'` → one per page. (#14)
8. `rg -n --type kotlin 'screenWidthDp|currentWindowAdaptiveInfo\(\)'` (#7, #19)
9. `rg -n --type kotlin 'contentDescription\s*=\s*null'` + every explicitly-sized `IconButton` (#23, #25)
10. `rg -n --type kotlin 'reduceMotion|areAnimatorsEnabled|ANIMATOR_DURATION_SCALE'` → zero hits in an
    animated app is a finding.

If all ten pass, say so plainly and name the two or three things that would most improve the screen
next. Do not manufacture a finding to fill a report.

---

## Cross-references

| For | Read |
| --- | --- |
| Which component / spec / layout to choose | `${CLAUDE_PLUGIN_ROOT}/skills/m3-expressive-best-practices/references/decision-trees.md` |
| Paired right/wrong code for each of these | `${CLAUDE_PLUGIN_ROOT}/skills/m3-expressive-best-practices/references/dos-and-donts.md` |
| Versions, opt-in census, every breaking change | `${CLAUDE_PLUGIN_ROOT}/skills/m3-expressive/references/setup-and-versions.md` |
| Spring constants, reduced motion, migration table | `${CLAUDE_PLUGIN_ROOT}/skills/m3-expressive-motion/references/motion-scheme.md` |
| Full shape catalog and morph recipes | `${CLAUDE_PLUGIN_ROOT}/skills/m3-expressive-shapes/references/` |
| Component signatures and corpus code | `${CLAUDE_PLUGIN_ROOT}/skills/m3-expressive-components/references/` |
| Window size classes, pane scaffolds, foldables | `${CLAUDE_PLUGIN_ROOT}/skills/m3-adaptive/references/` |
| Running a full audit with severity labels | `${CLAUDE_PLUGIN_ROOT}/skills/m3-expressive-review/references/review-checklist.md` |
| The design layer — tactics, hero budget, levers | `${CLAUDE_PLUGIN_ROOT}/skills/m3-expressive/references/design-principles.md` |

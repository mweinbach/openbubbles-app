---
name: m3-expressive
description: >
  Entry point for Material 3 Expressive work in Android Jetpack Compose. Establishes the
  correct material3 version and opt-in strategy, applies the Expressive design principles, and
  routes to the specialist theming, motion, shapes, components, navigation and review skills.
  Use when the user mentions Material 3 Expressive or M3 Expressive by name, asks to start or
  set up an Expressive app, hits opt-in or unresolved-reference errors on
  ExperimentalMaterial3ExpressiveApi, or asks to make a Compose app "feel like Android 16",
  "more expressive", or "match the new Material design" without naming a specific component.
---

# Material 3 Expressive for Jetpack Compose

Route the work, then load only the reference files needed. Do not read every reference — pick
by task.

## Step 1 — establish the version floor before writing any code

Expressive APIs are version-gated and the gating **changed repeatedly**. Getting this wrong is
the #1 cause of "it doesn't compile" in Expressive work.

Read `references/setup-and-versions.md` first whenever:

- starting a new project or adding Expressive to an existing one
- the user reports unresolved references, opt-in errors, or `ExperimentalMaterial3ExpressiveApi`
  compile failures
- you are about to write a `build.gradle.kts` / `libs.versions.toml` edit

The short version, current as of **2026-08-14** (verify against the reference — and against the
release notes if much time has passed):

| Artifact | Use |
| --- | --- |
| `androidx.compose.material3:material3` | `1.4.0` stable, or `1.5.0-alpha26` for the full Expressive surface |
| `androidx.graphics:graphics-shapes` | `1.1.0` — required for `RoundedPolygon` / `Morph` |
| `androidx.compose.material3.adaptive:adaptive*` | `1.3.0` (now stable; includes `adaptive-navigation3`) |
| `androidx.navigation3:*` | `1.1.6` stable |
| `androidx.compose:compose-bom` | `2026.08.00` (never ships alphas — pin material3 explicitly after it) |

Google's own samples pin material3 explicitly *over* the BOM. That is the sanctioned pattern, not
a hack.

**Do not assume every Expressive API needs an opt-in — that rule is out of date.** Most of the
surface graduated across alpha18–alpha23. A measured census of the shipped source shows:

- **Graduated, no opt-in**: `MaterialExpressiveTheme`, `MotionScheme`, floating toolbars,
  flexible app bars, `ShortNavigationBar` / `WideNavigationRail` / `ModalWideNavigationRail`,
  search bars, carousels, `SplitButton`, the FAB menu family, wavy progress indicators.
- **Still gated**: `LoadingIndicator` / `ContainedLoadingIndicator`, `MaterialShapes` +
  `toShape()` / `toPath()` (both promotions were *reverted* in alpha19), menu APIs,
  pull-to-refresh color APIs, and ToggleButton **size variants**. `ButtonGroup` is contested —
  the alpha22 note claims promotion, the source census disagrees.

Trust the compiler at the project's actual pin over any table, including this one. A global
gradle opt-in remains a reasonable safety net, not a requirement:

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

**Breaking changes landed in alpha25/alpha26.** If the project is on alpha24 or earlier, or if
code was written against it, check `references/setup-and-versions.md` §4 before touching buttons:
`ToggleButtonDefaults.shapes` → `shapesFor(Dp)` is a *semantic* change and both old overloads are
`DeprecationLevel.HIDDEN`, so old source hard-fails. `TonalToggleButton` → `FilledTonalToggleButton`
and `SplitButtonLayout` → `SplitButton` are pure renames.

## Step 2 — route to the right skill

| The user wants… | Go to |
| --- | --- |
| Theme setup, color schemes, dynamic color, typography, variable fonts, shape scale | **m3-expressive-theming** |
| Animation, springs, `MotionScheme`, transitions, shared elements, gesture feel | **m3-expressive-motion** |
| `MaterialShapes`, `Morph`, shape-by-interaction, segmented list corners | **m3-expressive-shapes** |
| A specific component: buttons, FABs, toolbars, progress, app bars, lists, sliders | **m3-expressive-components** |
| Nav bars, rails, adaptive layouts, Navigation3, list-detail panes | **m3-expressive-navigation** |
| Critique / audit an existing screen or diff against Expressive guidance | **m3-expressive-review** |
| An XML/Views app, MDC-Android, whether to migrate, theme bridging, interop | **m3-expressive-migration** |

Skills live at `${CLAUDE_PLUGIN_ROOT}/skills/<name>/`. Read their `SKILL.md` and referenced
files directly when the routing table points there.

## Step 3 — apply the design discipline, not just the API

Expressive is a *contrast* system. Its failure mode is applying expression uniformly until
nothing stands out. Read `references/design-principles.md` before making visual decisions on a
new screen.

The rules that matter most in practice:

1. **Budget one or two hero moments — across the product, not per screen.** Material's own
   guidance: "Stick to one or two hero moments in your product. While hero moments are eye
   catching, too many can be overwhelming or distracting." In practice that means most screens
   get zero heroes and one or two screens carry the expression. When reviewing a single screen
   in isolation, treat "at most one or two" as a proxy ceiling and flag three or more.
2. **Express through contrast, not volume.** Size contrast, shape contrast, color contrast, and
   motion contrast are the four levers. Pick the one that serves the screen's job; do not pull
   all four.
3. **Shape change is a state signal.** Morphing a container on press/select communicates
   something. Morphing decoratively burns the signal.
4. **Motion is physics now, not curves.** Use `MaterialTheme.motionScheme` specs; do not
   hand-roll `tween()` durations. Spatial springs may overshoot; effects springs must not.
5. **Group and contain.** Connected button groups, segmented list items, and floating toolbars
   read as single objects. Use them instead of loose rows of controls.

## Step 4 — verify before declaring done

- Every Expressive composable used is available at the pinned version, with the right opt-in.
- Imports are `androidx.compose.material3.*`, not `androidx.compose.material.*`.
- The theme is `MaterialExpressiveTheme` with an explicit `motionScheme`, not bare
  `MaterialTheme` — otherwise components silently fall back to standard motion.
- Dark theme and dynamic color both render correctly.
- Touch targets ≥ 48dp; content descriptions on icon-only controls; the new container
  components (FAB menu, toolbars, button groups) have sane semantics.
- If the project builds, run `./gradlew :app:compileDebugKotlin` (or the module equivalent)
  rather than assuming.

## Reference files in this skill

- `references/setup-and-versions.md` — dependencies, version catalog, opt-in strategy, the
  graduation timeline, and the known version traps.
- `references/design-principles.md` — the research behind Expressive, the seven design tactics
  with Material's own do/caution pairs, hero-moment budgeting, and anti-patterns.
- `references/example-apps.md` — index of the real shipping open-source apps this plugin's
  patterns were mined from, what each one demonstrates well, and what to copy from where.
- `references/modern-compose-idioms.md` — adjacent non-Material APIs that show up in current
  Google sample code: `Modifier.dropShadow`, `derivedMediaQuery`, `FlexBox`, `TextAutoSize`,
  `Modifier.animateItem`, the offscreen scroll-fade recipe, and the AGP 9 / Kotlin 2.3 build
  changes. Read when unfamiliar experimental APIs appear in the user's code or in a sample they
  are copying from.

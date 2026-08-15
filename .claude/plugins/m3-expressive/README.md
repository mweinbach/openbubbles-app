# M3 Expressive

A plugin for building **Material 3 Expressive** Android apps with **Jetpack Compose**.

Grounded in the material.io Expressive guidance, the `androidx.compose.material3` source and
release notes (verified through **1.5.0-alpha26**, 14 August 2026), Google's official sample apps,
and real shipping open-source apps. Every code pattern is taken from a real source, not invented.

## What's in it

Eight skills, ~28,000 lines of reference material.

| Skill | Covers |
| --- | --- |
| **m3-expressive** | Entry point. Version/dependency setup, the opt-in census, design principles, adjacent modern Compose idioms, and routing to the rest. |
| **m3-expressive-theming** | `MaterialExpressiveTheme`, color schemes, dynamic + seeded color, AMOLED, the expressive type scale, variable fonts, the eight-step shape scale. |
| **m3-expressive-motion** | `MotionScheme`, spatial vs effects springs, the exact spring constants, shared elements, `animateBounds`, predictive back, reduced motion. |
| **m3-expressive-shapes** | `MaterialShapes` (all 35), `RoundedPolygon`, `Morph`, shape-by-interaction, segmented list corners. |
| **m3-expressive-components** | The full component catalog: buttons & groups, split buttons, FAB menus, floating toolbars, loading + wavy progress, flexible app bars, lists, carousels, sliders, time pickers. |
| **m3-expressive-navigation** | `ShortNavigationBar`, `WideNavigationRail`, `ModalWideNavigationRail`, toolbar-as-nav, `derivedMediaQuery`, Navigation3, adaptive pane scaffolds. |
| **m3-expressive-migration** | The Compose-first shift, MDC-Android status, which Expressive components shipped for Views, interop, theme bridging, incremental migration. |
| **m3-expressive-review** | Severity-ranked audit checklist with ripgrep patterns, plus testing: why expressive UI hangs naive Compose tests, and the screenshot-testing setup. |

## How it behaves

Ask for anything Material 3 Expressive — "set up an expressive theme", "add a FAB menu", "why
doesn't this feel like Android 16", "should we migrate off XML", "review this screen" — and the
right skill loads. The entry skill establishes the version floor first, because the Expressive
API surface has churned hard across the alphas and several renames are compile-breaking.

Reference files load on demand, so a question about buttons doesn't pull in the navigation
material.

## Source apps

See `skills/m3-expressive/references/example-apps.md` for what each is good for.

**Official (Google):**

- [androidx material3 samples](https://github.com/androidx/androidx/tree/androidx-main/compose/material3/material3/samples) — the canonical `@Sampled` functions straight from the library source. Highest authority for any signature question.
- [jetpacker](https://github.com/android/ai-samples/tree/main/jetpacker) — material3 `1.5.0-alpha16`. Best for `animateBounds`/`LookaheadScope`, `derivedMediaQuery`, screenshot testing, Navigation3, `dropShadow`. Note: it does **not** use `MaterialExpressiveTheme` or any Expressive component — documented honestly, including 14 defects not to copy.
- [androidify](https://github.com/android/androidify) — material3 `1.5.0-alpha20`. The heaviest real Expressive app available.
- [Jetcaster](https://github.com/android/compose-samples) — the only compose-sample that adopted Expressive.
- `nowinandroid`, `platform-samples` and `android/snippets` have **zero** Expressive usage.

**Community:**

- [vivi-music](https://github.com/vivizzz007/vivi-music) `1.5.0-alpha23` — custom expressive sliders, `ButtonGroup`/`ToggleButton` at scale, album-art seeded color.
- [Tomato](https://github.com/nsh07/Tomato) — `MotionScheme` usage, Navigation3, segmented lists, toolbar-as-navigation.
- [LastChat](https://github.com/Cocolalilal/LastChat) `1.5.0-alpha08` — shape morphing, shared transitions, wavy progress.
- [Med](https://github.com/FeDeveloper95/Med) `1.5.0-alpha21` — the FAB menu with full accessibility semantics, adaptive nav switching.
- [PixelMusicApp](https://github.com/ianshulyadav/PixelMusicApp) — contains no Kotlin source; nothing was extractable.

## Caveats

- Version facts are current as of **14 August 2026**: material3 `1.4.0` stable (shipped 24 Sept
  2025 — the release-notes header misleadingly shows the page's latest-update date) and
  `1.5.0-alpha26`. `material3` moves fast; the plugin tells Claude to verify the pin before
  writing code.
- The opt-in situation is not what most sources say. Most of the Expressive surface has
  graduated; only a handful of APIs still need `@OptIn`. The census is in
  `setup-and-versions.md`.
- Anything unconfirmed against a primary source is marked `UNVERIFIED` or `CANONICAL-FORM`
  rather than asserted. Where a release note and the shipped source disagree (`ButtonGroup`), the
  conflict is documented instead of resolved by guessing.
- `developer.android.com`'s component guide pages are stale and omit most of these APIs — the
  plugin points at the androidx samples instead.
- Third-party sample code is cited as evidence of practice, not endorsement; known bugs in it are
  called out inline with fixes.

## Changelog

**0.2.0** — Added the migration skill (Compose-first, Views→Compose) and testing reference.
Corrected the opt-in guidance from "assume gated" to the measured census. Added alpha25/alpha26
breaking changes (`shapesFor`, `FilledTonalToggleButton`, `SplitButton`, `animateWidth` split,
`SearchBarScrollState`). Replaced canonical-form placeholders with real code for `AppBarRow`,
`SearchBarState`, `ModalWideNavigationRail`, `VerticalFloatingToolbar`, carousels, `TimePickerDialog`,
`animateBounds` and predictive back. Added `modern-compose-idioms.md`.

**0.1.0** — Initial release: seven skills covering setup, theming, motion, shapes, components,
navigation and review.

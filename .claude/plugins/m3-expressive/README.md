# M3 Expressive

A plugin for building **Material 3 Expressive** and **Material 3 Adaptive** Android apps with
**Jetpack Compose**.

Grounded in the material.io Expressive guidance, the `androidx` source and release notes
(verified against `compose/material3/material3/api/current.txt` at androidx HEAD `360e8cba`,
**14 August 2026** — material3 `1.5.0-alpha26`, material3-adaptive `1.3.0`), Google's official
sample apps, and real shipping open-source apps. Every code pattern comes from a real source.

## What's in it

Ten skills, ~48,000 lines of reference material.

| Skill | Covers |
| --- | --- |
| **m3-expressive-best-practices** | **Start here to build.** Complete worked screens (list, detail, feed, media player, timer, settings, form, chat, search, onboarding), 11 decision trees, 54 do/don't code pairs, the top 25 mistakes, and a full copy-paste starter project. |
| **m3-adaptive** | Window size classes and the five breakpoints, `currentWindowAdaptiveInfoV2`, pane scaffolds, `PaneScaffoldDirective`, pane navigation and expansion, `NavigationSuiteScaffold`, foldables/posture, Android 16 resizability, adaptive recipes. |
| **m3-expressive** | Setup and routing hub. Versions, the opt-in census, design principles, adjacent modern Compose idioms. |
| **m3-expressive-theming** | `MaterialExpressiveTheme`, color schemes, dynamic + seeded color, AMOLED, the expressive type scale, variable fonts, the eight-step shape scale. |
| **m3-expressive-motion** | `MotionScheme`, spatial vs effects springs, the exact spring constants, shared elements, `animateBounds`, predictive back, reduced motion. |
| **m3-expressive-shapes** | `MaterialShapes` (all 35), `RoundedPolygon`, `Morph`, shape-by-interaction, segmented list corners. |
| **m3-expressive-components** | The full component catalog: buttons & groups, split buttons, FAB menus, floating toolbars, loading + wavy progress, flexible app bars, lists, carousels, sliders, time pickers. |
| **m3-expressive-navigation** | Nav containers (`ShortNavigationBar`, `WideNavigationRail`, toolbar-as-nav), Navigation3 routing, transitions. |
| **m3-expressive-migration** | The Compose-first shift, MDC-Android status, which Expressive components shipped for Views, interop, theme bridging. |
| **m3-expressive-review** | Severity-ranked audit checklist with ripgrep patterns, plus testing: why expressive UI hangs naive Compose tests, and the screenshot-testing setup. |

## How it behaves

Ask for anything Material 3 Expressive or Adaptive — "build me a settings screen", "why does my
layout break on a tablet", "add a FAB menu", "should we migrate off XML", "review this screen" —
and the right skill loads. Construction work routes to **best-practices**; large-screen and
multi-pane work routes to **m3-adaptive**; the entry skill handles versions, opt-ins and
principles.

Reference files load on demand, so a question about buttons doesn't pull in the adaptive
material.

## Source apps

See `skills/m3-expressive/references/example-apps.md` for what each is good for.

**Official (Google):**

- [androidx material3 + adaptive source and samples](https://github.com/androidx/androidx/tree/androidx-main/compose/material3) — the canonical `@Sampled` functions and the `api/current.txt` signature files. Highest authority; every contested API question in this plugin was settled here.
- [jetpacker](https://github.com/android/ai-samples/tree/main/jetpacker) — material3 `1.5.0-alpha16`. Best for `animateBounds`/`LookaheadScope`, `derivedMediaQuery`, screenshot testing, Navigation3, `dropShadow`. It does **not** use `MaterialExpressiveTheme` or any Expressive component — documented honestly, including 14 defects not to copy.
- [androidify](https://github.com/android/androidify) — material3 `1.5.0-alpha20`. The heaviest real Expressive app available.
- [Reply / JetNews / Jetcaster / JetLagged](https://github.com/android/compose-samples) — Reply for `NavigationSuiteScaffoldLayout`, JetNews and JetLagged for predictive back, Jetcaster for Expressive adoption.
- [nowinandroid](https://github.com/android/nowinandroid) — production `ListDetailSceneStrategy`. No Expressive usage.

**Community:**

- [vivi-music](https://github.com/vivizzz007/vivi-music) `1.5.0-alpha23` — custom expressive sliders, `ButtonGroup`/`ToggleButton` at scale, album-art seeded color.
- [Tomato](https://github.com/nsh07/Tomato) — `MotionScheme` usage, Navigation3, segmented lists, toolbar-as-navigation.
- [LastChat](https://github.com/Cocolalilal/LastChat) `1.5.0-alpha08` — shape morphing, shared transitions, wavy progress.
- [Med](https://github.com/FeDeveloper95/Med) `1.5.0-alpha21` — the FAB menu with full accessibility semantics, adaptive nav switching.
- [PixelMusicApp](https://github.com/ianshulyadav/PixelMusicApp) — contains no Kotlin source; nothing was extractable.

## Caveats

- Version facts are current as of **14 August 2026**: material3 `1.4.0` stable (shipped
  24 Sept 2025 — the release-notes header misleadingly shows the page's latest-update date) and
  `1.5.0-alpha26`; material3-adaptive `1.3.0` stable. These move fast; the plugin tells Claude to
  verify the pin before writing code.
- **Release notes are not always right.** Two claims in the notes did not survive checking the
  shipped signature files: the alpha25 "Deprecated `SplitButtonLayout` API" entry does *not* mean
  a `SplitButton` composable exists (it doesn't — `SplitButtonLayout` is current and
  undeprecated), and `ButtonGroup`'s promotion status is contested. Both are documented as
  conflicts rather than resolved by guessing.
- The opt-in situation is not what most sources say. Most of the Expressive surface has
  graduated; only a handful of APIs still need `@OptIn`. The census is in `setup-and-versions.md`.
- Anything unconfirmed against a primary source is marked `UNVERIFIED` or `CANONICAL-FORM`.
- `developer.android.com`'s component guide pages are stale and omit most of these APIs — the
  plugin points at the androidx samples instead.
- Third-party sample code is cited as evidence of practice, not endorsement; known bugs in it are
  called out inline with fixes.

## Changelog

**0.3.0** — Added **m3-expressive-best-practices** (worked screens, decision trees, do/don't
pairs, common mistakes, starter project) and **m3-adaptive** as a first-class skill (five
breakpoints, pane scaffolds, posture, navigation suite, adaptive recipes). Corrected the
`SplitButtonLayout` → `SplitButton` "rename" — verified against the signature files, the rename
never happened. Corrected `ToggleButtonDefaults`: the per-variant color factories are
`@Deprecated @BytecodeOnly` and uncallable from Kotlin at alpha26; variant colors moved to
`ElevatedToggleButtonDefaults` / `FilledTonalToggleButtonDefaults` /
`OutlinedToggleButtonDefaults`. Fixed `currentWindowAdaptiveInfo()` → `V2` and the three-vs-five
window size class error throughout. Narrowed skill descriptions to stop adaptive queries firing
two skills.

**0.2.0** — Added the migration skill (Compose-first, Views→Compose) and testing reference.
Corrected the opt-in guidance from "assume gated" to the measured census. Added alpha25/alpha26
breaking changes. Replaced canonical-form placeholders with real code for `AppBarRow`,
`SearchBarState`, `ModalWideNavigationRail`, `VerticalFloatingToolbar`, carousels,
`TimePickerDialog`, `animateBounds` and predictive back. Added `modern-compose-idioms.md`.

**0.1.0** — Initial release: seven skills covering setup, theming, motion, shapes, components,
navigation and review.

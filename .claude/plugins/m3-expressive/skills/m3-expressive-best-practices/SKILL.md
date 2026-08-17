---
name: m3-expressive-best-practices
description: >
  The opinionated end-to-end guide to building Material 3 Expressive screens in Jetpack Compose
  correctly — complete worked screens (list, detail, feed, media player, timer, settings, form,
  chat, search, onboarding), decision trees for picking the right component, do/don't code pairs,
  the most common mistakes with fixes, and a full copy-paste starter project. Use when the user
  asks how to build a screen, what the best practice is, which component to use, how to structure
  an Expressive app, why their UI doesn't look right, or wants a complete working example rather
  than API reference. Also use as the default starting point for building any new Expressive
  screen from scratch.
---

# M3 Expressive Best Practices

This is the *build it correctly* skill. The sibling skills are organised by subsystem and are
right for "what is the signature of X". This one is organised by **what you are trying to make**.

When building a screen, start here. Drop into a sibling skill only for a detail this skill
doesn't settle.

## The seven rules

Everything below follows from these. If a decision is unclear, resolve it against this list.

1. **Theme at the root with `MaterialExpressiveTheme` and an explicit `motionScheme`.** Bare
   `MaterialTheme` silently falls back to standard motion and no amount of screen work recovers
   it. This one line is the difference between an app that feels Expressive and one that doesn't.
2. **Spend the hero budget deliberately.** Material's rule is one or two hero moments *in the
   product*, not per screen. Most screens should be calm. Decide what the hero is before writing
   the screen, and leave everything else alone.
3. **Read motion off the theme, never hardcode it.** `MaterialTheme.motionScheme.defaultSpatialSpec()`,
   not `tween(300)`. Spatial for things that move or resize; effects for alpha, color, elevation.
4. **Contain and group.** A connected `ButtonGroup`, a segmented list, a floating toolbar — these
   read as one object. Loose rows of controls are the most common way an Expressive redesign
   looks unfinished.
5. **Shape and color must carry information.** A morph should mark state. A `primaryContainer`
   fill should mark importance. Applied uniformly they say nothing and cost attention.
6. **Adapt by window size class, never by device type.** Five width buckets, not three. Never
   branch on `Configuration.screenWidthDp`, orientation, or an `isTablet` boolean.
7. **Semantics are part of the component.** The reference apps this plugin was built from are
   weak here — several ship zero `contentDescription`. Do not copy that. Every icon-only control
   gets a description, every toggle a state description, every new container a sane traversal
   order.

## Working method for a new screen

1. **Name the job of the screen** and pick its archetype (list / detail / feed / media / form /
   settings / chat / search / hero). Open the matching worked screen and start from it.
2. **Pick the hero** — or decide the screen has none. Use the "where does the hero go" tree in
   `references/decision-trees.md`.
3. **Choose components with the decision trees**, not from memory. Several of these APIs have
   near-identical siblings with different correct uses.
4. **Wire motion from the theme**, matching the spec family to the property.
5. **Make it adaptive** — at minimum, confirm the screen survives a resize. Use the `m3-adaptive`
   skill for anything multi-pane.
6. **Add semantics and check touch targets.**
7. **Verify.** Compile it. Then check dark mode, dynamic color, a resize, 200% text scale, and
   reduced motion.

## Reference files

| You want… | Read |
| --- | --- |
| Which component/API for this job — 11 decision trees ending in concrete calls | `references/decision-trees.md` |
| ❌/✅ code pairs across theming, motion, shape, color, type, components, adaptive, a11y, perf | `references/dos-and-donts.md` |
| The top 25 mistakes: symptom → cause → grep to confirm → fix | `references/common-mistakes.md` |
| Complete list, detail, feed, and list-detail-as-one-adaptive-screen | `references/worked-screens-list-detail-feed.md` |
| Complete media player, timer/progress hero, onboarding hero, loading patterns | `references/worked-screens-media-and-hero.md` |
| Complete settings, form, chat, search screens | `references/worked-screens-forms-settings-chat.md` |
| A full copy-paste project: gradle, manifest, theme, nav, adaptive shell, one screen, tests | `references/starter-project.md` |

Sibling skills, for depth this one deliberately doesn't duplicate:
`${CLAUDE_PLUGIN_ROOT}/skills/m3-adaptive/` (window size classes, pane scaffolds, posture),
`m3-expressive-theming`, `m3-expressive-motion`, `m3-expressive-shapes`,
`m3-expressive-components`, `m3-expressive-navigation`, `m3-expressive-review`.

## API facts that break builds

Current as of **material3 1.5.0-alpha26 / material3-adaptive 1.3.0, 14 Aug 2026**. Verify against
`${CLAUDE_PLUGIN_ROOT}/skills/m3-expressive/references/setup-and-versions.md` before trusting.

- `FilledTonalToggleButton` — `TonalToggleButton` was renamed in alpha25.
- `ToggleButtonDefaults.shapesFor(height: Dp)` or the `ToggleButtonShapes(...)` constructor.
  `ToggleButtonDefaults.shapes(...)` is `DeprecationLevel.HIDDEN` — code written against alpha24
  **will not compile**.
- `SplitButtonLayout` is the correct name and is not deprecated. There is no `SplitButton`
  composable.
- `currentWindowAdaptiveInfoV2()` — the V1 form is deprecated.
- `isWidthAtLeastBreakpoint(...)`, not `containsWidthDp`. All predicates are `>=`, so a `when`
  chain **must run largest → smallest** or it silently collapses to the smallest bucket.
- Width buckets: 0 / 600 / 840 / 1200 / 1600. Height: 0 / 480 / 900.
- Wavy indicators take `Stroke` in **pixels** but `wavelength` / `gapSize` in **`Dp`**.
- List-detail roles invert what you'd guess: **List = Secondary, Detail = Primary**.
- Only `@OptIn(ExperimentalMaterial3ExpressiveApi::class)` where still needed —
  `LoadingIndicator`, `MaterialShapes` + `toShape()`/`toPath()`, menu APIs, pull-to-refresh
  colors, ToggleButton size variants. Most of the surface graduated; do not sprinkle opt-ins.

## Verification before declaring done

- It compiles. Run `./gradlew :app:compileDebugKotlin` rather than assuming.
- Light, dark, and dynamic-color-off all render correctly.
- Resize through every width bucket the app supports; state survives.
- 200% font scale doesn't clip.
- Reduced motion is respected.
- TalkBack traverses the screen sensibly and announces states.
- The hero is still the hero — nothing added late is competing with it.

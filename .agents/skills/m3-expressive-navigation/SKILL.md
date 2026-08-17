---
name: m3-expressive-navigation
description: >
  Builds navigation containers and Navigation3 routing with Material 3 Expressive in Jetpack
  Compose — ShortNavigationBar, ShortNavigationBarItem, WideNavigationRail,
  ModalWideNavigationRail, floating toolbars used as navigation, navigation item and indicator
  APIs, Navigation3 (NavDisplay, NavKey, back stacks, entryProvider), transition specs, shared
  element transitions across destinations, and predictive back. Use when the user asks about
  bottom navigation, navigation rails, nav3, routing between screens, or screen transition
  animations. For window size classes, pane scaffolds, list-detail/multi-pane layouts, foldables
  and NavigationSuiteScaffold, use the m3-adaptive skill instead.
---

# M3 Expressive Navigation & Adaptive Layout

> **Deep adaptive material now lives in the `m3-adaptive` skill**, at
> `${CLAUDE_PLUGIN_ROOT}/skills/m3-adaptive/`. Go there for window size classes and breakpoints,
> `PaneScaffoldDirective`, `ListDetailPaneScaffold` / `SupportingPaneScaffold` recipes, pane
> expansion, posture/foldables, `NavigationSuiteScaffold` and the full `NavigationSuiteType` surface,
> and the `adaptive-navigation3` scene strategies.
>
> **This skill covers nav *containers* and Navigation3 routing** — `ShortNavigationBar`,
> `WideNavigationRail`, `ModalWideNavigationRail`, toolbar-as-navigation, item/indicator APIs,
> `NavDisplay`/`NavKey`/back stacks, transition specs and shared transitions. The pane-scaffold
> material below is kept for continuity; where the two overlap, `m3-adaptive` is authoritative.

## Pick the container by window size, not by device type

| Width size class | Container |
| --- | --- |
| Compact (<600dp) | `ShortNavigationBar` (or a `HorizontalFloatingToolbar` used as nav) |
| Medium (600–839dp) | `WideNavigationRail` collapsed |
| Expanded (840–1199dp) | `WideNavigationRail` collapsed or expanded |
| Large (1200–1599dp) | `WideNavigationRail` expanded, or a permanent drawer |
| Extra-large (≥1600dp) | `WideNavigationRail` expanded, or a permanent drawer |

**There are five width buckets, not three** — 0 / 600 / 840 / 1200 / 1600 dp. Large and Extra-large
were added for desktop windowing and connected displays. Height still has three: 0 / 480 / 900 dp.
`NavigationSuiteScaffoldDefaults.navigationSuiteType()` stops at a *collapsed* rail and has no
Large/XL branch, so the last two rows require an explicit override — see the `m3-adaptive` skill.

`NavigationSuiteScaffold` from `material3-adaptive-navigation-suite` does this switch for you
and is the right default. Hand-rolling the switch off `WindowSizeClass` is only worth it when you
need non-standard breakpoints or a custom container.

Opt-in status for `ShortNavigationBar` / `WideNavigationRail` / `ModalWideNavigationRail` is a
documentation gap: the shipped androidx source carries **zero** experimental annotations on them,
yet no graduation release note was ever published (alpha20 quietly deleted the experimental
overloads instead of announcing a promotion). Write them without opt-in and let the compiler
correct you.

## The three real-world approaches

Shipping apps do one of these. Know which you are building.

1. **`NavigationSuiteScaffold`** — least code, correct behaviour, least control.
2. **Manual `ShortNavigationBar` ⇄ `WideNavigationRail` switch** on a computed size class. More
   code, full control over animation between the two.
3. **`HorizontalFloatingToolbar` as the nav container** — the most expressive option. A pill of
   destinations floating over content, with
   `FloatingToolbarDefaults.exitAlwaysScrollBehavior()` hiding it on scroll. Do **not** also show
   a navigation bar when you do this.

`references/nav-containers.md` has complete working code for all three, plus the item/indicator
API differences that bite when migrating from `NavigationBar`.

## Navigation3 vs Navigation2

Navigation3 (`androidx.navigation3` + `androidx.compose.material3.adaptive:adaptive-navigation3`)
is the model that composes cleanly with adaptive panes: a `NavDisplay` over a mutable back stack
of `NavKey`s, with `SceneStrategy` deciding whether entries render as one pane or two.

Use nav3 when the app needs list-detail or supporting-pane behaviour driven by the back stack.
Stay on nav2 when the app is a flat set of tabs — nav3's advantage does not pay for the churn
there. Be honest with the user about this rather than migrating reflexively; at least one of the
reference apps declared nav3 in its version catalog and then commented the dependencies back out.

Expressive detail that matters: wrap the root `NavDisplay` in `SharedTransitionLayout` and give
it a predictive-pop spec so back gestures animate as a real container transform instead of a
slide. Code in `references/adaptive-and-nav3.md`.

## Pane layouts

**Full treatment is in the `m3-adaptive` skill** —
`${CLAUDE_PLUGIN_ROOT}/skills/m3-adaptive/references/adaptive-recipes.md` has complete, corrected
recipes for each of these. Summary only:

- `ListDetailPaneScaffold` — list ⇄ detail, the common case. Prefer
  `NavigableListDetailPaneScaffold`, which adds predictive back for you.
- `SupportingPaneScaffold` — a main surface with a secondary panel (stats, queue, inspector).
- Both take a `PaneScaffoldDirective`; prefer
  `calculatePaneScaffoldDirective(currentWindowAdaptiveInfoV2()).copy(...)` over forking the
  calculator, to change gutters and margins (a 0dp gutter reads as a single connected surface, which
  is often what an expressive layout wants).
- `VerticalDragHandle` + `paneExpansionState` + `Modifier.paneExpansionDraggable` lets the user
  resize panes. This is a genuinely expressive touch and is under-used.

## Reference files

| Task | Read |
| --- | --- |
| `ShortNavigationBar`, `WideNavigationRail`, `ModalWideNavigationRail`, toolbar-as-nav, item APIs | `references/nav-containers.md` |
| Navigation3 setup, `NavDisplay`, `NavKey`, transition specs, shared transitions across destinations | `references/adaptive-and-nav3.md` |
| `NavigationSuiteScaffold`, all 8 `NavigationSuiteType` values, `adaptive-navigation3` scene strategies | `${CLAUDE_PLUGIN_ROOT}/skills/m3-adaptive/references/navigation-suite.md` |
| Window size classes, pane scaffolds, posture, adaptive recipes and troubleshooting | `${CLAUDE_PLUGIN_ROOT}/skills/m3-adaptive/references/adaptive-recipes.md` |

## Anti-patterns

- Two navigation containers visible at once (nav bar + floating toolbar; rail + nav bar).
- More than five destinations in a `ShortNavigationBar`. Move the overflow into the rail's
  expanded state or a menu.
- Rebuilding the nav container per-screen instead of hoisting it above the `NavDisplay`, which
  kills the cross-destination shared transition.
- Ignoring predictive back. On Android 16 a back gesture that doesn't preview reads as broken.
- Hardcoding phone/tablet branches off `Configuration.screenWidthDp` instead of window size
  classes — breaks on foldables and in split-screen.

## Verification

- Resize: run the layout at compact, medium and expanded widths; check the container swaps and
  that state survives the swap.
- Rotate and fold. Confirm the selected destination and scroll position persist.
- Trigger predictive back from every depth and confirm the preview animation matches the forward
  transition.
- TalkBack: navigate the container and confirm each destination announces its selected state.

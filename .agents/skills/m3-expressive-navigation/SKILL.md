---
name: m3-expressive-navigation
description: >
  Builds navigation and adaptive layouts with Material 3 Expressive in Jetpack Compose —
  ShortNavigationBar, WideNavigationRail, ModalWideNavigationRail, NavigationSuiteScaffold,
  floating toolbars used as navigation, Navigation3 (NavDisplay, NavKey, ListDetailSceneStrategy),
  ListDetailPaneScaffold, SupportingPaneScaffold, pane expansion with VerticalDragHandle,
  window size classes, and predictive back. Use when the user asks about bottom navigation,
  navigation rails, tablet/foldable layouts, list-detail, multi-pane, adaptive UI, nav3, or
  screen transitions between destinations.
---

# M3 Expressive Navigation & Adaptive Layout

## Pick the container by window size, not by device type

| Width size class | Container |
| --- | --- |
| Compact | `ShortNavigationBar` (or a `HorizontalFloatingToolbar` used as nav) |
| Medium | `WideNavigationRail` collapsed |
| Expanded | `WideNavigationRail` expanded, or a permanent drawer |

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

- `ListDetailPaneScaffold` — list ⇄ detail, the common case.
- `SupportingPaneScaffold` — a main surface with a secondary panel (stats, queue, inspector).
- Both take a `PaneScaffoldDirective`; override
  `calculatePaneScaffoldDirective` to change gutters and margins (a 0dp gutter reads as a single
  connected surface, which is often what an expressive layout wants).
- `VerticalDragHandle` + `paneExpansionState` + `Modifier.paneExpansionDraggable` lets the user
  resize panes. This is a genuinely expressive touch and is under-used.

## Reference files

| Task | Read |
| --- | --- |
| `ShortNavigationBar`, `WideNavigationRail`, `ModalWideNavigationRail`, `NavigationSuiteScaffold`, toolbar-as-nav, item APIs | `references/nav-containers.md` |
| Navigation3 setup, `NavDisplay`, `NavKey`, scene strategies, pane scaffolds, drag handles, predictive back, shared transitions across destinations | `references/adaptive-and-nav3.md` |

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

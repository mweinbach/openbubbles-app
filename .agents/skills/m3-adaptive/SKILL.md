---
name: m3-adaptive
description: >
  Builds adaptive Android layouts with Material 3 Adaptive in Jetpack Compose — window size
  classes and breakpoints, currentWindowAdaptiveInfoV2, ListDetailPaneScaffold,
  SupportingPaneScaffold, PaneScaffoldDirective, ThreePaneScaffoldNavigator, pane expansion with
  VerticalDragHandle, NavigationSuiteScaffold and NavigationSuiteType, adaptive-navigation3 scene
  strategies, foldables, posture, hinges, and Android 16 resizability. Use when the user mentions
  tablets, foldables, large screens, split-screen, desktop windowing, responsive or adaptive
  layout, list-detail, multi-pane, window size class, breakpoints, or asks why their layout
  breaks when resized or rotated.
---

# Material 3 Adaptive

`androidx.compose.material3.adaptive` is a **separate library** from `material3`, with its own
version train. Treating it as a footnote to navigation is why adaptive code so often goes wrong.

## Two version trains — get this right first

| Artifact | Group | Version |
| --- | --- | --- |
| `adaptive`, `adaptive-layout`, `adaptive-navigation`, `adaptive-navigation3` | `androidx.compose.material3.adaptive` | **1.3.0** (stable, 2026-08-12) |
| `material3-adaptive-navigation-suite` | `androidx.compose.material3` | **1.5.0-alpha26** |
| `material3-window-size-class` | `androidx.compose.material3` | legacy — generally don't add it |

The navigation suite versions with **material3**, not with the adaptive group. Pinning it to
`1.3.0` is a common and confusing failure.

`material3-window-size-class` is the **old** size-class API. Don't add it to new projects: the
current type is `androidx.window.core.layout.WindowSizeClass`, reached through
`currentWindowAdaptiveInfoV2()`, which the `adaptive` artifact already brings in.

`adaptive-navigation3` first shipped in the 1.3.0 adaptive release.

## Five facts that cause most adaptive bugs

1. **There are five width buckets, not three.** `0 / 600 / 840 / 1200 (Large) / 1600 (XL)` dp.
   Height has three: `0 / 480 / 900`. Large and XL exist for desktop windowing and connected
   displays.
2. **All breakpoint predicates are `>=`.** `isWidthAtLeastBreakpoint(...)` — there is no
   `containsWidthDp`. A `when` chain **must run largest → smallest**. Written smallest-first it
   compiles fine and silently collapses every window to the smallest bucket. This is the single
   most common adaptive bug.
3. **`currentWindowAdaptiveInfo()` is deprecated** in 1.3.0. Use `currentWindowAdaptiveInfoV2()`.
   Same for `currentWindowSize()` and `currentWindowDpSize()`.
4. **The pane roles are counter-intuitive.** In list-detail, **List = `Secondary`, Detail =
   `Primary`**. In supporting-pane, **Main = `Primary`, Supporting = `Secondary`**. Get this
   backwards and panes appear in the wrong place or not at all.
5. **`NavigationSuiteType` has eight values, not three** — and
   `NavigationSuiteScaffoldDefaults.navigationSuiteType()` never returns
   `WideNavigationRailExpanded`, `NavigationDrawer` or `None`, and has no Large/XL branch. Above
   1200dp you must override it yourself or your desktop-sized window keeps a collapsed rail.

```kotlin
// ✅ largest → smallest
val info = currentWindowAdaptiveInfoV2()
val width = info.windowSizeClass
when {
    width.isWidthAtLeastBreakpoint(1600) -> ExtraLargeLayout()
    width.isWidthAtLeastBreakpoint(1200) -> LargeLayout()
    width.isWidthAtLeastBreakpoint(840)  -> ExpandedLayout()
    width.isWidthAtLeastBreakpoint(600)  -> MediumLayout()
    else                                  -> CompactLayout()
}
```

## Reference files

| You want… | Read |
| --- | --- |
| Breakpoints, `WindowSizeClass`, `currentWindowAdaptiveInfoV2`, the `>=` trap, the 1.3.0 deprecation table, migrating off the legacy size-class API | `references/window-size-classes.md` |
| `ListDetailPaneScaffold`, `SupportingPaneScaffold`, roles, `PaneScaffoldDirective` decoded, `AdaptStrategy`, `AnimatedPane` | `references/pane-scaffolds.md` |
| `ThreePaneScaffoldNavigator`, `BackNavigationBehavior`, `Navigable*` wrappers, predictive back, pane expansion and drag handles | `references/pane-navigation-and-expansion.md` |
| `NavigationSuiteScaffold`, all 8 `NavigationSuiteType` values, overriding the computed type, `adaptive-navigation3` scene strategies | `references/navigation-suite.md` |
| Posture, hinges, tabletop/book mode, `HingePolicy`, Android 16 / API 36 resizability, desktop windowing, multi-window | `references/foldables-and-posture.md` |
| Complete working layouts: the 10-line adaptive app, list-detail done right, supporting pane, feed, three-pane, tabletop, custom directive, retrofitting a phone-only screen, troubleshooting | `references/adaptive-recipes.md` |

For nav *containers* themselves (`ShortNavigationBar`, `WideNavigationRail`, toolbar-as-nav) and
Navigation3 routing, see `${CLAUDE_PLUGIN_ROOT}/skills/m3-expressive-navigation/`.

## Working method

1. **Start from `references/adaptive-recipes.md`.** Most requests are one of the canonical
   layouts; copying a correct recipe beats assembling the API from memory.
2. **Prefer the batteries-included wrappers.** `NavigableListDetailPaneScaffold` and
   `NavigableSupportingPaneScaffold` wire predictive back for you. Reach for a raw
   `ThreePaneScaffoldNavigator` only when you need custom back behaviour.
3. **Prefer `.copy()` over forking the directive.**
   `calculatePaneScaffoldDirective(currentWindowAdaptiveInfoV2()).copy(horizontalPartitionSpacerSize = 0.dp)`
   rather than reimplementing the calculator — a reference app forked it and introduced two bugs
   by comparing a height against a width bound.
4. **Decide what Large/XL actually do.** The defaults give you three partitions above 1200dp. An
   empty third pane is worse than two panes; either fill it meaningfully or cap the partitions.
5. **Test by resizing, not by device.** Foldables, split-screen, and desktop windows all move
   through buckets at runtime.

## Design rules

- Adapt layout, never content meaning. The same screen at every size, not a different app.
- Reachability: on large screens, put primary actions within thumb reach rather than centred.
- Never lock orientation, never set `android:resizeableActivity="false"`. On **Android 16 / API
  36** large-screen apps can no longer opt out of resizing — 19 manifest overrides are dead, and
  the `PROPERTY_COMPAT_ALLOW_RESTRICTED_RESIZABILITY` escape hatch is removed at API 37.
- Avoid the hinge: in tabletop posture put content above the fold and controls below.
- One navigation container at a time, whatever the size class.

## Verification

- Resize continuously through every bucket — 400 → 700 → 900 → 1300 → 1700dp — and confirm the
  layout changes at the right points and state survives each change.
- Fold and unfold; check tabletop and book postures.
- Rotate; enter split-screen; if targeting desktop, resize the freeform window.
- Confirm back behaviour in each configuration: back from a detail pane should do the right thing
  at both single-pane and two-pane widths.
- Confirm no `when` chain runs smallest-first and no deprecated `currentWindowAdaptiveInfo()`
  remains.

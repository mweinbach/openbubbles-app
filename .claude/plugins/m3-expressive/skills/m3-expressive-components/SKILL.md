---
name: m3-expressive-components
description: >
  Builds with Material 3 Expressive components in Jetpack Compose — ButtonGroup, ToggleButton,
  SplitButtonLayout, the XSmall–XLarge button size scale, FloatingActionButtonMenu,
  ToggleFloatingActionButton, animateFloatingActionButton, HorizontalFloatingToolbar and
  VerticalFloatingToolbar, LoadingIndicator, ContainedLoadingIndicator, LinearWavyProgressIndicator,
  CircularWavyProgressIndicator, wavy/squiggly sliders, MediumFlexibleTopAppBar,
  LargeFlexibleTopAppBar, FlexibleBottomAppBar, app bar subtitles, AppBarRow/AppBarColumn,
  VerticalDragHandle, expressive ListItem and segmented list items, carousels, search bars, and
  expressive pull-to-refresh. Use when the user names any of those, or asks for a specific
  piece of expressive UI like a play button, sort header, filter row, or loading spinner.
---

# M3 Expressive Component Catalog

Load only the reference file for the component family in play. Each reference gives the API
signature, the required opt-in, sizing/spacing specs, real working code from shipping apps, and
the anti-patterns.

| Family | Reference |
| --- | --- |
| `ButtonGroup`, `ToggleButton`, `SplitButtonLayout`, button size scale, icon buttons, chips | `references/buttons.md` |
| FAB, `FloatingActionButtonMenu`, `ToggleFloatingActionButton`, floating toolbars | `references/fabs-and-toolbars.md` |
| `LoadingIndicator`, wavy progress, pull-to-refresh, skeletons | `references/progress-and-loading.md` |
| Flexible top app bars, subtitles, `FlexibleBottomAppBar`, `AppBarRow`/`AppBarColumn`, search bars | `references/app-bars.md` |
| `ListItem`, segmented lists, cards, carousels, `VerticalDragHandle`, sheets, dialogs | `references/lists-cards-containers.md` |
| Sliders (including wavy/squiggly), text fields, time picker, switches | `references/sliders-and-inputs.md` |

## Fast facts worth knowing before you open a reference

**Button size scale.** Expressive buttons come in five sizes with fixed heights:

| Size | Height |
| --- | --- |
| XSmall | 32dp |
| Small | 40dp |
| Medium | 56dp |
| Large | 96dp |
| XLarge | 136dp |

Large and XLarge are hero controls — a single primary action, usually one per screen. Do not
build a row of XLarge buttons.

**`ButtonGroup` is not a segmented control.** It is a container that connects adjacent buttons,
compresses neighbours when one is pressed, and can overflow into a menu. Its signature changed
incompatibly in 1.5.0-alpha22 (`overflowIndicator` moved first) — the most likely compile break
when moving between 1.4.0 and current alphas. There is also a widely used hand-built
alternative: `Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween)` with per-item
shapes, which gives the connected look with full layout control. Both are valid and both appear
in the reference.

**The split-button composable is `SplitButtonLayout` on every version, including alpha26 — there is
no `SplitButton` composable.** It carries no `@Deprecated` annotation (verified in
`compose/material3/material3/api/current.txt` at androidx HEAD `360e8cba`, 2026-08-14). An earlier
version of this skill claimed a `SplitButtonLayout` → `SplitButton` rename in alpha25; that was
wrong. The alpha25 note "Deprecated `SplitButtonLayout` API" most plausibly refers to the deprecated
`SplitButtonDefaults.leadingButtonShapes(CornerSize)` / `trailingButtonShapes(CornerSize)` helpers,
superseded by `*ShapesFor(buttonHeight: Dp)` — that reading is **inference, not fact**.

**Button APIs broke in alpha25/alpha26.** Before writing any toggle button: `TonalToggleButton`
is now `FilledTonalToggleButton` (pure rename), and `ToggleButtonDefaults.shapes` is replaced by
`shapesFor(buttonHeight: Dp)` — *not* a rename, it takes a Dp height. For custom shapes use the
`ToggleButtonShapes(...)` constructor. Both old `shapes` overloads are `DeprecationLevel.HIDDEN`,
so alpha24-era source will not compile. `ButtonGroupScope` is now a sealed interface and
`Modifier.animateWidth`'s `compressionLimit` changed from `PaddingValues` to `Dp`. Full
old-to-new migration in `references/buttons.md`.

**FAB menu takes 2–6 items.** Fewer than two is a plain FAB; more than six is a bottom sheet.
Never combine a FAB menu with an extended FAB.

**A floating toolbar replaces the bottom app bar** — never show both, and never pair a floating
toolbar with a navigation bar. Some apps legitimately use `HorizontalFloatingToolbar` *as* their
bottom navigation; that is a deliberate choice, not an accident.

**`LoadingIndicator` is for waits under ~5 seconds.** Beyond that use determinate progress.
`LoadingIndicator` and `ContainedLoadingIndicator` are among the few components that still
require `@OptIn(ExperimentalMaterial3ExpressiveApi::class)` — their promotion was reverted in
alpha19 and has not returned as of alpha26. Most other Expressive components have graduated; do
not add opt-ins reflexively.

**Wavy progress indicators take px, not dp, for strokes.** `LinearWavyProgressIndicator` and
`CircularWavyProgressIndicator` accept `Stroke` / `trackStroke` in pixels while `wavelength` and
`gapSize` are `Dp`. Mixing those up produces either a hairline or a blob. Wavy also fails
visually below roughly 40dp — fall back to the non-wavy indicator at small sizes.

**Flexible app bars are where hero typography lives.** `LargeFlexibleTopAppBar` with a `subtitle`
and an emphasized display style is the cheapest legitimate hero moment on a screen.

## Working method

1. Confirm the component exists at the project's pinned version and note its opt-in requirement
   (see `${CLAUDE_PLUGIN_ROOT}/skills/m3-expressive/references/setup-and-versions.md`).
2. Read the family reference and copy the closest working example rather than composing from
   memory — several of these APIs have non-obvious slot ordering and defaults objects.
3. Wire motion through `MaterialTheme.motionScheme`, not literal durations.
4. Add semantics: `contentDescription` on icon-only controls, `stateDescription` for toggles,
   `CustomAccessibilityAction` on FAB menus, `isTraversalGroup` on grouped containers.

## Verification

- Compile. These APIs churn between alphas and signature drift is the norm.
- Check the component at both light/dark and at the smallest supported width.
- Check touch targets: an XSmall 32dp button still needs a 48dp touch target.
- For anything with an expanded/collapsed state (FAB menu, toolbar, search bar), verify back
  handling and TalkBack traversal in both states.

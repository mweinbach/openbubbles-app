---
name: m3-expressive-migration
description: >
  Handles the Material "Compose-first" shift and migration of Android Views/XML UI to Jetpack
  Compose for Material 3 Expressive. Covers MDC-Android (com.google.android.material)
  maintenance status, which Expressive components shipped for Views versus Compose-only,
  ComposeView/AndroidView interop, ViewCompositionStrategy, theme bridging between XML
  Theme.Material3 and MaterialExpressiveTheme, and incremental migration strategy. Use when the
  user has an XML/Views app, asks whether to migrate to Compose, mentions MDC-Android,
  material-components-android, Theme.Material3, styles.xml, ComposeView, AndroidView, theme
  adapters, or asks what they lose by staying on Views.
---

# Material Compose-First & Views → Compose Migration

## The situation, stated accurately

Material Android is **Compose-first**. As of the May 2026 announcement, all new Material
development targets Compose. Nick Butcher's companion platform post is explicit and worth
quoting to a skeptical team: *"all Android UI should be built with Compose"* and *"We have no
plans to deprecate or remove View components... but they will receive no new features."*

Do not overstate this. The accurate framing:

- **MDC-Android is in maintenance mode, not deprecated.** The repo banner reads
  `[MAINTENANCE MODE]`. Latest version is **1.14.0 (13 May 2026)** — six days before the
  announcement, making it the terminal feature release. No removal timeline exists.
- **Views apps keep working.** Nothing breaks. The cost is opportunity cost.
- **`developer.android.com/develop/ui/compose/first`** lists Material Design Components (Views)
  among 25 frozen View-based Jetpack libraries.

## The nuance almost everyone gets wrong

**Material 3 Expressive did ship for Views.** People assume Expressive is Compose-only. It isn't:

- MDC **1.13.0** (Sep 2025) added `FloatingToolbarLayout`, `LoadingIndicator`,
  `MaterialSplitButton`, `MaterialButtonGroup`.
- MDC **1.14.0** gates `Theme.Material3Expressive.*`.
- Wavy progress exists in XML: `Widget.Material3Expressive.LinearProgressIndicator.Wavy` with
  `app:waveAmplitude`.

Views got Expressive once, then stopped. So the honest answer to "do I have to migrate to get
Expressive?" is *no, you already have a lot of it* — followed by the list of what you genuinely
cannot have.

**Genuinely Compose-only:**

| Compose-only | Why it matters |
| --- | --- |
| `MotionScheme` | The entire spring-physics motion system. No XML equivalent at all. |
| `MaterialShapes` shape morphing | Polygon shapes and `Morph`. XML has no analogue. |
| `FloatingActionButtonMenu` | Views has no FAB menu. |
| Interactive `ButtonGroup` | Views' `MaterialButtonGroup` is static; the compression/overflow behaviour is Compose-only. |
| `MaterialExpressiveTheme` as a contract | XML themes can carry color/type/shape but cannot carry motion. |

Read that table as: **color, type and shape bridge across the boundary. Motion does not.** That
single fact drives most migration decisions.

## Reference files

| Task | Read |
| --- | --- |
| The announcement, MDC status, full Views-vs-Compose Expressive inventory, urgency and decision guidance | `references/compose-first-status.md` |
| Interop mechanics, `ViewCompositionStrategy`, theme bridging tables, incremental strategy, pitfalls | `references/views-to-compose.md` |

## How to advise

1. **Establish where they actually are.** MDC version, whether they're on `Theme.Material3.*`
   already, how much XML exists, and whether any Compose is present.
2. **Do not reflexively recommend full migration.** It is often wrong. Give the three options
   honestly — full migration, incremental, stay on Views — with the real cost of each. A team
   shipping a stable Views app that doesn't need spring motion has a defensible position.
3. **If they're staying on Views**, tell them to at least get to MDC 1.14.0 and
   `Theme.Material3Expressive.*` — that's free Expressive they may not know they have.
4. **If they're migrating incrementally**, the leaf-first order (small reusable components →
   screens → navigation) minimises interop pain. Warn about the motion split: a half-migrated app
   has two motion feels, and there is no bridge. Mitigation is to migrate whole screens, not
   fragments of screens.
5. **Theme bridging has no adapter.** Accompanist's `themeadapter-material3` is dead — its own
   README says *"This library is deprecated, and the API is no longer maintained."* The original
   MDC adapter repo 404s. The current approach is a single source of truth (Material Theme
   Builder) generating both XML and Compose theme files. The mapping tables are in
   `references/views-to-compose.md`.
6. **Point them at the tool.** Google ships
   `android skills add --skill migrate-xml-views-to-jetpack-compose`.

## The pitfalls that actually bite

Full list with code in `references/views-to-compose.md`. The ones that cause real bugs:

- **Wrong `ViewCompositionStrategy` in Fragments** — the default leaks or disposes at the wrong
  time. This is the number one interop bug.
- **Duplicate `ComposeView` IDs** in reused layouts — state goes to the wrong composable.
- **Insets consumed twice** across the boundary — content ends up double-padded edge-to-edge.
- **RecyclerView + ComposeView** requires a minimum RecyclerView version; below it, scrolling
  recycles compositions incorrectly.
- **ViewModel scoping** across the boundary — easy to accidentally get two instances.

## Verification

- Build and run both paths: a screen still in XML and a migrated screen, side by side.
- Compare color, type and shape across the boundary — they should be indistinguishable. If they
  aren't, the theme source of truth isn't single.
- Check motion honestly: the migrated screen will feel different. Confirm that's acceptable or
  that the screen boundary hides it.
- Rotate, resize, and background/foreground each interop screen — lifecycle bugs surface there.
- Verify insets and edge-to-edge on both sides.

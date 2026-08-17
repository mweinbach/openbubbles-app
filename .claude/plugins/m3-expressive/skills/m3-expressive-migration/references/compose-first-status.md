# Compose-first — status, facts, and the migrate/don't-migrate decision

What Google actually announced in May 2026, what it does and does not mean for a Views/XML app,
and exactly which parts of M3 Expressive you can and cannot get without Compose.

Mechanics of migrating live in `views-to-compose.md`. This file is the *situation and the
decision*.

**Provenance tags used below**

| Tag | Meaning |
| --- | --- |
| `[SRC]` | Verbatim from a machine-readable primary Google source (repo markdown, blog post, developer.android.com, release notes) |
| `[DERIVED]` | Reasoned here from `[SRC]` facts — not itself a published statement |
| `[UNVERIFIED]` | Could not be confirmed against a primary source; verify before relying on it |

**Facts as of 2026-08-14.** Version numbers go stale; the policy statements do not.

---

## 0. Source-access caveat — read before quoting anything

The canonical Material post is <https://m3.material.io/blog/material-is-compose-first>.
**Its body text is not recoverable.** `m3.material.io` is a JavaScript-rendered SPA and returns an
empty document body to every non-browser fetch. The only text recoverable from that URL is metadata:

- `<title>` / `og:title`: **"Material Android is Compose-first"** `[SRC]`
- `description` / `og:description`: **"Start migrating to Compose to get the latest from Material"** `[SRC]`

The sibling pages `m3.material.io/develop/android/mdc-android` and
`m3.material.io/develop/android/jetpack-compose` are empty-body for the same reason.

**Rule: never quote a sentence and attribute it to the m3.material.io blog post.** If a user asks
"what did the Material blog say," answer with the title and description above, then quote the
MDC-Android README — which carries Material's own wording of the same announcement, verbatim and
machine-readable, and links back to that blog post. Everything in §1 below is from sources that
*are* readable.

---

## 1. The announcement

### 1.1 Material's wording — MDC-Android `README.md`, verbatim `[SRC]`

Top of `master`:

> # [MAINTENANCE MODE] Material Components for Android
>
> At Google I/O 2026, Material Android announced that we're "all-in" on Compose,
> alongside the official
> [Android Compose-first announcement](https://android-developers.googleblog.com/2026/05/android-ui-development-is-compose-first.html),
> marking a new chapter in Android development.
>
> **This means that the Views-based Material Components for Android library
> (MDC-Android) is now in maintenance mode.**
>
> There are no more planned feature releases for Views, so all projects using the
> Views library should begin or continue
> [migrating to Compose](https://developer.android.com/develop/ui/compose/migrate/migrate-xml-views-to-jetpack-compose)
> to get the latest Material Design and Android platform updates.

Source: `raw.githubusercontent.com/material-components/material-components-android/master/README.md`.
The identical text opens `docs/getting-started.md`.

The repo's GitHub one-line description is now `[SRC]`:

> **[MAINTENANCE MODE] Modular and customizable Material Design UI components for Android**

The README also redirects Compose traffic away `[SRC]`:

> Note: If your issue or feature request is for Material Jetpack Compose, please
> file it at the Compose Issue Tracker instead.

### 1.2 The platform post — fully recoverable `[SRC]`

**"Android UI Development is Compose First"** — **Nick Butcher, Product Manager** — **19 May 2026**.
<https://android-developers.googleblog.com/2026/05/android-ui-development-is-compose-first.html>
(mirror: <https://developer.android.com/blog/posts/android-ui-development-is-compose-first>)

The load-bearing paragraph, verbatim:

> Compose has matured into the standard for Android UI development—we believe
> that **all Android UI should be built with Compose**; we call this going Compose
> First. From today, we'll provide all APIs, libraries, tools and guidance in
> Compose. We now consider the View components that Compose replaces (components
> in the android.widget package) to be in **maintenance mode**. We have no plans to
> deprecate or remove View components and will continue to support them with
> critical bug fixes, but they will receive no new features.

The other four sections, verbatim:

> **View-based Jetpack Libraries** — The same goes for View based libraries like
> Fragments, RecyclerView or Viewpager — we consider them complete and will only
> publish critical bugfixes. For a complete list of libraries now in maintenance
> mode, see here.

> **Tools** — Any new Android Studio UI tools will be built for Jetpack Compose
> only. Existing view-based tools (such as the Navigation Editor and Layout Editor)
> are now in maintenance mode and will not receive new features.

> **Guidance** — Documentation, codelabs, and samples will focus on building UI
> with Jetpack Compose. You can still find Views-specific documentation linked from
> pages that contain generic and Compose information, where relevant.

> **Happy Composing** — We recommend that you build all new features with Compose
> and convert existing features when you touch them to gain the many Compose
> benefits. Check out our XML to Compose migration skill to help you convert
> existing layouts to Compose.

Four stated reasons for Compose `[SRC]`: rich feature set; highly performant ("native performance
out of the box"); adaptive ("the easiest way to build adaptive apps that work across the range of
Android form factors"); productive (Previews, Live Edit, Kotlin).

### 1.3 The doc version — the maintenance list

<https://developer.android.com/develop/ui/compose/first> names the 25 View-based Jetpack libraries
now in maintenance mode `[SRC]`:

> CardView, ConstraintLayout, CoordinatorLayout, Cursoradapter, CustomView,
> Databinding, DragAndDrop, DrawerLayout, DynamicAnimation, Emoji, Fragment,
> GridLayout, Interpolator, Loader, Navigation, PercentLayout, Preference,
> RecyclerView, SlidingPaneLayout, SwipeRefreshLayout, Transition, VectorDrawable,
> ViewPager, ViewPager2, and **Material Design Components (Views)**

Same page draws a distinction people miss `[SRC]`: `android.widget` (TextView, ListView, …) is in
maintenance mode; **`android.view` remains supported** as "foundational plumbing for Compose and
other UI toolkits." Views get "only highly critical fixes."

---

## 2. MDC-Android status

### 2.1 The four-way classification

| Question | Answer | Evidence |
| --- | --- | --- |
| In maintenance mode? | **Yes, officially and by name** | Repo banner `[MAINTENANCE MODE]`; named in the developer.android.com list `[SRC]` |
| Deprecated? | **No** | "We have no plans to deprecate or remove View components" `[SRC]` |
| Removed / removal date? | **No, and none announced** | Same sentence `[SRC]` |
| Feature-frozen? | **Yes** | "There are no more planned feature releases for Views" `[SRC]` |
| Still gets bug fixes? | **Yes — critical only** | "will continue to support them with critical bug fixes" `[SRC]` (platform-level statement) |
| Repo accepting issues? | **Yes**, observationally | README still directs bug reports to GitHub Issues; repo shows open issues/PRs |
| Repo formally GitHub-archived? | `[UNVERIFIED]` | GitHub REST API blocked (HTTP 403) from the research environment. Observationally *not* archived, since it accepts issues |

Note the asymmetry: the **MDC README itself never restates a bug-fix commitment** — it only says no
feature releases. The bug-fix promise is inherited from the platform-level post. Do not tell a team
"Material promised to keep fixing MDC bugs"; tell them "Google's platform-level commitment covers
Views with critical bug fixes."

### 2.2 Versions and dates

| Artifact | Version | Date | Note |
| --- | --- | --- | --- |
| `com.google.android.material:material` | **1.14.0** | **13 May 2026** | Terminal feature release. `minSdk` 23. Built with AGP 8.11.1 / Gradle 8.13 |
| | 1.13.0 | 3 Sep 2025 | First Expressive *components* for Views. `minSdk` 21 |
| | 1.12.0 | 2 May 2024 | Pre-Expressive |

**1.14.0 shipped six days before the 19 May 2026 announcement.** `[DERIVED]` That sequencing is the
whole story: Material finished the Expressive port to Views, shipped it, and then capped the
library. There is nothing "coming soon" for Views — 1.14.0 *is* the last one.

> **Date trap — do not repeat the common error.** Automated summarizers repeatedly mis-transcribe
> 1.14.0 as 2024 or 2025, because the GitHub tag page renders "13 May 15:02" with no year. **13 May
> 2026** is the corroborated date (three independent reads agree) and is the only one consistent
> with the timeline.

Compose side, for the contrast:

| Artifact | Version | Date |
| --- | --- | --- |
| `androidx.compose.material3:material3` | **1.4.0** stable | **24 Sept 2025** |
| | **1.5.0-alpha26** | 12 Aug 2026 (2-week alpha train, still moving) |

> **Second date trap.** The compose-material3 release-notes page shows "Latest Update: August 12,
> 2026" in a header cell. That is the newest release across *all* channels (alpha26), **not**
> 1.4.0's ship date. 1.4.0 shipped 24 Sept 2025. Never cite 12 Aug 2026 as 1.4.0's release date.

### 2.3 What "no new features" means concretely

Not abstract. It means all of the following, permanently:

1. **No new MDC components.** Every component Material designs after May 2026 ships to
   `androidx.compose.material3` only.
2. **No new XML styles or theme attributes** on existing components. The `Widget.Material3*` and
   `Theme.Material3*` surfaces are final at 1.14.0.
3. **No new `app:` attributes**, no new setters on MDC widget classes.
4. **No new Android Studio UI tooling** for XML. Layout Editor and Navigation Editor are frozen at
   their May 2026 capability `[SRC]`.
5. **No new features in the 25 View-based Jetpack libraries** — Fragment, RecyclerView, ViewPager2,
   Navigation, Preference, Transition, ConstraintLayout, DataBinding, and the rest.
6. **No new first-party docs, codelabs, or samples** targeting XML `[SRC]`.
7. **No integration path for new platform APIs** as they arrive — they will land with Compose APIs.

What "maintenance mode" does **not** mean: your build does not break, your `compileSdk` bump does
not fail, `Theme.Material3Expressive.*` does not stop working, and no deprecation warning appears in
your IDE. Nothing in the announcement changes the behavior of code you already shipped.

---

## 3. The Expressive-in-Views inventory — the most important table here

**Correct framing: M3 Expressive is NOT Compose-only.** Expressive landed on Views substantially,
in two releases (1.13.0 components, 1.14.0 themes and styles), and *that is where Views stops*.
Every Expressive question should start by checking this table, not by assuming Compose exclusivity.

Version gates, verbatim from `docs/getting-started.md` `[SRC]`:

> For Material3 themes: "you should depend on version `1.5.0` or later."
> For Material3Expressive themes: **"you should depend on version `1.14.0` or later."**

### 3.1 Full inventory — Views vs Compose

`Views` column names are XML style/class names where verified. `[SRC]` on a Views cell means the
resource or class name itself is verbatim from MDC docs/release notes; a category name in quotes is
the verbatim release-note bullet with the individual style names not audited.

| Expressive capability | Views / XML — MDC | Compose — `androidx.compose.material3` |
| --- | --- | --- |
| **Expressive theme** | `Theme.Material3Expressive.*` `[SRC]` — requires **1.14.0+** | `MaterialExpressiveTheme(colorScheme, motionScheme, shapes, typography)` |
| **Expressive widget styles** | `Widget.Material3Expressive.*` namespace `[SRC]` | per-component `*Defaults` objects |
| **Emphasized type scale** | "Emphasized Typescale" (1.14.0) `[SRC]` | `Typography.displayLargeEmphasized` … `labelSmallEmphasized` (15 props) |
| **Wavy linear progress** | `Widget.Material3Expressive.LinearProgressIndicator.Wavy` `[SRC]`; attrs `app:waveAmplitude`, `app:wavelength`, `app:waveSpeed`; setters `setWaveAmplitude`/`setWavelength`/`setWaveSpeed` `[SRC]` | `LinearWavyProgressIndicator` |
| **Wavy circular progress** | `Widget.Material3Expressive.CircularProgressIndicator.Wavy` `[SRC]` (`.Flat` variants exist and **Flat is the default**) | `CircularWavyProgressIndicator`, `WavyProgressIndicatorDefaults` |
| **Loading indicator** | `LoadingIndicator` (1.13.0) `[SRC]` | `LoadingIndicator`, `LoadingIndicatorDefaults` (still `@ExperimentalMaterial3ExpressiveApi`) |
| **Split button** | `MaterialSplitButton` (1.13.0) `[SRC]` | `SplitButtonLayout` (current, undeprecated at alpha26 — there is **no** `SplitButton` composable) |
| **Button group — static** | `MaterialButtonGroup` (1.13.0) `[SRC]` | `ButtonGroup`, `ButtonGroupScope`, `ButtonGroupDefaults` |
| **Button group — interactive resize / pressure response** | **none** | `ButtonGroupScope` + `Modifier.animateWidth(interactionSource, compressionLimit)` |
| **Floating toolbar** | `FloatingToolbarLayout` (1.13.0) `[SRC]` | `FloatingToolbarScrollBehavior`, `FloatingToolbarState` `[SRC]` (composable entry-point names not audited — `[UNVERIFIED]`) |
| **Docked toolbar** | `DockedToolbarLayout` (1.13.0) `[SRC]` | no counterpart name verified — `[UNVERIFIED]` |
| **FAB menu (expanding)** | **none** | `FloatingActionButtonMenu`, `FloatingActionButtonMenuScope` |
| **Shape morphing** | **none** | `MaterialShapes` + `androidx.graphics:graphics-shapes` `Morph` |
| **Motion scheme (theme-level)** | **none** — expressive motion is baked into individual widget styles | `MotionScheme`, `MaterialTheme.motionScheme`, spatial/effects × fast/default/slow |
| **Expressive buttons** | "Expressive Button Styles" (1.14.0) `[SRC]` | `Button` + `ButtonDefaults` shape/size families |
| **Expressive icon buttons** | "Expressive Icon Button Styles" (1.14.0) `[SRC]` | `IconButton` variants + `IconButtonDefaults` |
| **Toggle buttons** | not named in 1.13.0/1.14.0 notes — `[UNVERIFIED]` | `ToggleButton`, `ElevatedToggleButton`, `FilledTonalToggleButton`, `OutlinedToggleButton` |
| **Expressive FAB** | "Expressive FAB Styles" (1.14.0) `[SRC]` | `FloatingActionButton` variants |
| **Expressive top app bar** | "Expressive Top App Bar Styles" (1.14.0) `[SRC]` | `MediumFlexibleTopAppBar`, `LargeFlexibleTopAppBar`, `TwoRowsTopAppBar` |
| **Flexible bottom app bar** | not named in the notes — `[UNVERIFIED]` | `FlexibleBottomAppBar` |
| **Expressive navigation bar** | "Expressive Navigation Bar (BottomNavigationView) Styles" (1.14.0) `[SRC]` | `ShortNavigationBar`, `ShortNavigationBarItem` |
| **Expressive navigation rail** | "Expressive Navigation Rail Styles" (1.14.0) `[SRC]` | `WideNavigationRail`, `ModalWideNavigationRail`, `WideNavigationRailItem` |
| **Expressive search** | "Expressive Search Styles" (1.14.0) `[SRC]` | `SearchBar` + `SearchBarScrollBehavior` / `SearchBarScrollState` |
| **Expressive slider** | "Expressive Slider Styles" (1.14.0) `[SRC]` | `Slider`, `RangeSlider` |
| **Expressive lists** | "Expressive Lists" (1.14.0) `[SRC]` | list items incl. segmented list item shapes |

Full verbatim "New in 1.14.0" bullet list `[SRC]`: Expressive Themes; Expressive Lists; Expressive
Button Styles; Expressive Icon Button Styles; Expressive Button Group Styles; Expressive FAB Styles;
Expressive Top App Bar Styles; Expressive Navigation Bar (BottomNavigationView) Styles; Expressive
Navigation Rail Styles; Expressive Search Styles; Expressive Progress Indicator Styles; Expressive
Slider Styles; Emphasized Typescale.

### 3.2 The short answer — genuinely Compose-only

Five things, and only these five, are confirmed to have **no Views counterpart**:

| Compose-only | Why Views can't have it |
| --- | --- |
| `MotionScheme` | It is a *theming primitive* — a set of six spring specs injected through the theme and read by every component. XML themes have no slot for animation specs; MDC bakes motion per-widget. |
| `MaterialShapes` + shape morphing | Depends on `androidx.graphics:graphics-shapes` `RoundedPolygon`/`Morph` driven per-frame from a Compose animation. No XML drawable equivalent. |
| `FloatingActionButtonMenu` | The expanding FAB-to-menu component simply was never ported. |
| `ButtonGroup` interactive resize / pressure response | `MaterialButtonGroup` exists in Views as a *static* grouping container; the press-driven width animation does not. |
| `MaterialExpressiveTheme` as a theme-level contract | On Views, "expressive" is a set of opt-in styles you apply per widget. In Compose it is one wrapper that sets color + motion + shape + type at once, and components read it automatically. |

> `[UNVERIFIED]` (partial): the "no Views counterpart" claim rests on the MDC 1.13.0/1.14.0 release
> notes and the component docs that were readable. Not every MDC XML attribute was audited. Treat
> as strong-but-not-exhaustive; if a user says "but there's an XML attribute for that," check the
> component's `docs/components/*.md` before contradicting them.

### 3.3 How to answer "is Expressive Compose-only?"

Say: **No.** Expressive shipped for Views in MDC 1.13.0 and 1.14.0 — themes, emphasized type, wavy
progress, floating/docked toolbars, split buttons, button groups, and expressive styles for buttons,
FABs, nav bar, nav rail, search, and slider. What Views never got is `MotionScheme`, shape morphing,
`FloatingActionButtonMenu`, interactive `ButtonGroup`, and expressive-as-a-theme-contract. And
1.14.0 is the last release, so Views' Expressive coverage is frozen at that snapshot forever.

---

## 4. What a Views team actually loses — ranked

Ranked by how much it costs a real team, most to least.

**1. Compounding design drift.** `[DERIVED]` This is the real cost and it is not a cliff. Material
keeps shipping components and refinements to Compose; your app's visual vocabulary stops advancing
at May 2026. After two or three Android releases the gap between "your app" and "a current Android
app" is visible to users without them being able to name why.

**2. The migration bill grows while you wait.** `[DERIVED]` Every new XML screen you write is
future migration work you are choosing to buy. This is why "stop adding new XML" matters far more
than "convert the old XML" — the first stops the bleeding at zero cost, the second is expensive.

**3. Motion.** No `MotionScheme`, ever. Expressive motion is the single most distinctive part of the
design language and it is the part Views cannot express at all. Static Expressive screenshots look
identical; the app *feels* a generation old in motion.

**4. Tooling.** `[SRC]` "Any new Android Studio UI tools will be built for Jetpack Compose only."
Layout Editor and Navigation Editor are frozen. You also permanently forgo Previews, Live Edit, and
whatever comes next.

**5. New components.** Any component Material designs after May 2026. Today that costs nothing
(the catalog is complete as of the freeze); in two years it will be the visible gap.

**6. Jetpack library features.** Fragment, RecyclerView, Navigation, Preference, Transition, etc.
are complete. In practice these are mature libraries and this hurts least in the near term.

**7. Docs and samples.** `[SRC]` Guidance "will focus on building UI with Jetpack Compose." Your
team will increasingly read Compose docs and mentally translate. Onboarding new hires gets harder as
the industry's mental model shifts.

**8. New platform API integration.** `[DERIVED]` New capability arrives with Compose APIs first;
Views access will be secondhand or absent. A secondary source (DEV Community, 24 May 2026 — *not
Google*) points at the Grid API for 2D layouts, Media3 AI effects, Gemini integration surfaces,
trackpad events, and shared-element debug tools as Compose-only arrivals. Treat as directional, not
authoritative.

### What you keep — say this first, it defuses panic

- Every MDC 1.14.0 component and `Theme.Material3Expressive.*` theme keeps working, indefinitely.
- API stability. No breaking changes coming to `RecyclerView`, `ConstraintLayout`, `Fragment`, etc.
- Critical bug fixes at the platform level `[SRC]`.
- `android.view` — the base plumbing — is **fully supported**, not in maintenance mode `[SRC]`.
- Dynamic color, Material You, M3 theming: already in Views, unaffected.

---

## 5. Urgency assessment — honest, not alarmist

**Low urgency. High direction-certainty.** Those are different axes and both matter.

| Claim | Verdict |
| --- | --- |
| "Views is deprecated" | **False.** Verbatim: "We have no plans to deprecate or remove View components." |
| "Views will be removed" | **False.** No removal timeline exists, anywhere. |
| "There's a deadline" | **False.** None was announced. |
| "My app will stop building" | **False.** Nothing changes about compilation or runtime. |
| "Views is frozen" | **True.** MDC 1.14.0 is the terminal feature release. |
| "New Material work is Compose-only from here" | **True**, by stated policy `[SRC]`. |
| "M3 Expressive is Compose-only" | **False.** See §3. It shipped for Views once. |
| "There's no reason to start anything new in XML" | **True.** `[DERIVED]` This is the actionable conclusion. |

The asymmetry to watch, and the cleanest way to communicate the situation: `androidx.compose.material3`
is on a two-week alpha train at `1.5.0-alpha26` and moving; `com.google.android.material` is on
1.14.0 and stopped. **That gap only widens from here, monotonically, forever.**

If someone asks "how long do I have?" the honest answer is: *indefinitely for correctness, and
you're already out of time for parity.* Nothing forces the migration. Nothing will make the gap
smaller either.

---

## 6. Decision guidance

### 6.1 Do this first, regardless of what you decide about migrating

**Upgrade to MDC 1.14.0.** Highest value-per-effort action available to a Views team, and it is not
a migration. 1.14.0 is simultaneously the terminal release *and* the Expressive release. Requires
`minSdk 23`. You get:

- `Theme.Material3Expressive.*` and the `Widget.Material3Expressive.*` styles
- Emphasized typescale
- Wavy linear and circular progress indicators
- Expressive button / icon-button / button-group / FAB / top-app-bar / nav-bar / nav-rail / search /
  slider / list styles
- Plus the 1.13.0 components: `FloatingToolbarLayout`, `DockedToolbarLayout`, `LoadingIndicator`,
  `MaterialSplitButton`, `MaterialButtonGroup`

A team that does only this gets most of the Expressive visual refresh **without writing one line of
Compose.** Recommend it before recommending anything else.

### 6.2 Full migration is right when

- The app is small (single-digit screens) or early enough that rewriting is cheaper than maintaining
  two toolkits.
- A design refresh is already funded — the migration hides inside work you were doing anyway.
- Motion is a product differentiator. If the design calls for expressive motion or shape morphing,
  Views cannot deliver it and no amount of Views work will.
- The team is already fluent in Compose and the XML surface is the minority of the codebase.
- You are building for form factors where adaptive layout matters (foldables, tablets, XR, Auto) —
  Google's stated adaptive story is Compose-only going forward.

### 6.3 Incremental migration is right when — and this is the default

This is Google's own recommendation, verbatim `[SRC]`:

> We recommend that you build all new features with Compose and convert existing
> features when you touch them to gain the many Compose benefits.

The official three-step strategy `[SRC]` (<https://developer.android.com/develop/ui/compose/migrate/strategy>):

1. **Build new screens with Compose** — "Using Compose to build new features that encompass an
   entire screen is the best way to drive your adoption of Compose."
2. **Create a library of common UI components** — "Creating a library of common UI components allows
   you to have a single source of truth for these components in your app and promote reusability."
3. **Replace existing features one screen at a time.**

Pragmatic policy for a constrained team `[DERIVED]`:

1. **Stop writing new XML screens.** New screens go in Compose behind a `ComposeView`. Make this a
   lint rule or a code-review rule; it is the highest-leverage decision in the whole exercise.
2. **Leave stable, well-tested, rarely-touched legacy screens alone.** They are not costing you
   anything.
3. **Convert opportunistically.** When a screen is already being reworked for product reasons,
   convert it then.
4. **Keep Fragment-based Navigation until the very end.** Do not move to Navigation Compose or
   Navigation3 until every destination is a composable — see `views-to-compose.md` §11.
5. **Build the shared component library early.** It is what stops the two halves of the app from
   visually diverging.
6. **Budget for the dual-theme maintenance tax.** Two theme definitions must be kept numerically
   identical until XML is gone. Treat the XML theme as source of truth while both exist.

### 6.4 Staying on Views is defensible when

State this without hedging when it applies — recommending a migration a team cannot afford is worse
advice than recommending no migration.

- The app is in maintenance itself: bug fixes and compliance updates only, no new feature work.
- The remaining product lifetime is shorter than the migration payback period.
- It is an internal/enterprise app where visual currency has no business value.
- The team has no Compose expertise and no budget to acquire it, and the app ships to a captive
  audience.
- Heavy dependence on a Views-only third-party SDK with no Compose story and no interop path.
- Extreme `minSdk` constraints below 23 — note this blocks MDC 1.14.0 too, so such a team cannot get
  Expressive on Views *either* and should be told so plainly.

In all these cases: still upgrade to MDC 1.14.0 if `minSdk` allows, still stop writing new XML if
any new work is happening at all, and set the expectation that the design gap grows.

### 6.5 The one-paragraph answer for a stakeholder

Google announced on 19 May 2026 that all Android UI should be built with Compose. View components
are in maintenance mode: no new features, but explicitly **not deprecated, not removed, and no
removal date**. The Views Material library (MDC-Android) shipped its final feature release, 1.14.0,
on 13 May 2026 — which is also the release that brought M3 Expressive to XML, so Views apps *can*
get most of the Expressive look today by upgrading that one dependency. What Views can never get is
expressive motion, shape morphing, and anything Material designs from here on. Nothing breaks and
there is no deadline; the cost of staying is compounding drift, not failure. The right default is
to stop writing new XML immediately and convert screens opportunistically.

---

## 7. Timeline

| Date | Event |
| --- | --- |
| 2 May 2024 | MDC-Android **1.12.0** (pre-Expressive) |
| 13 May 2025 | M3 Expressive announced publicly as a design language (*secondary sources only*) |
| 24 Sept 2025 | `androidx.compose.material3` **1.4.0** stable |
| 3 Sep 2025 | MDC-Android **1.13.0** — first Expressive components for Views (`DockedToolbarLayout`, `FloatingToolbarLayout`, `LoadingIndicator`, `MaterialSplitButton`, `MaterialButtonGroup`); `minSdk` 21 |
| 22 Oct 2025 | `androidx.graphics:graphics-shapes` **1.1.0** (required for `MaterialShapes` morphing — Compose only) |
| **13 May 2026** | **MDC-Android 1.14.0** — Expressive themes/styles for Views, **and the last feature release**; `minSdk` 23 |
| **19 May 2026** | **Google I/O 2026.** Nick Butcher publishes "Android UI Development is Compose First". `android.widget`, 25 View-based Jetpack libraries (incl. Material Design Components (Views)), Layout Editor and Navigation Editor enter maintenance mode. Material publishes "Material Android is Compose-first"; MDC repo re-banners to `[MAINTENANCE MODE]` |
| 24 May 2026 | First community analysis wave (*secondary*) |
| Jul 2026 | "Celebrating 5 years of Jetpack Compose" on the Android Developers Blog |
| 12 Aug 2026 | `androidx.compose.material3` **1.5.0-alpha26**; the alpha train continues on a 2-week cadence |

---

## 8. UNVERIFIED index

Carry these caveats forward; do not let them silently harden into facts.

- Whether the MDC-Android GitHub repo is formally **archived** (GitHub REST API returned 403 to the
  research environment). Observationally not archived.
- Exact `Widget.Material3Expressive.*` **leaf style names** beyond the progress indicators. The
  namespace and the release-note categories are verified; individual style resource names are not.
  Read `docs/components/*.md` in the MDC repo before asserting one.
- Whether **toggle buttons** and a **flexible bottom app bar** have Views counterparts — not named
  in the 1.13.0/1.14.0 notes, not confirmed absent either.
- Whether the Compose **docked toolbar** has a counterpart to `DockedToolbarLayout`.
- Compose **floating toolbar composable entry-point names** (only `FloatingToolbarScrollBehavior`
  and `FloatingToolbarState` were confirmed public).
- Exhaustiveness of the "genuinely Compose-only" list in §3.2 — strong, not audited attribute by
  attribute.
- Per-API experimental status in `androidx.compose.material3` — it moves release to release. Check
  the releases page at time of use. (`MaterialShapes` and `LoadingIndicator` were still experimental
  at 1.5.0-alpha26; several other Expressive APIs have graduated.)
- Current state of `github.com/material-components/material-components-android-compose-theme-adapter`
  — returned 404. Deleted, moved, or transient failure is unknown. Either way it is off the
  recommended path.

---

## 9. Sources

**Primary — Google**

- <https://m3.material.io/blog/material-is-compose-first> — canonical; **body unreadable (JS SPA)**, title/description only
- <https://android-developers.googleblog.com/2026/05/android-ui-development-is-compose-first.html> — full text
- <https://developer.android.com/blog/posts/android-ui-development-is-compose-first> — mirror
- <https://developer.android.com/develop/ui/compose/first> — the 25-library maintenance list
- `raw.githubusercontent.com/material-components/material-components-android/master/README.md`
- `.../master/docs/getting-started.md` — version gates for Material3 / Material3Expressive themes
- `.../master/docs/components/ProgressIndicator.md` — wavy styles and wave attributes in Views
- <https://github.com/material-components/material-components-android/releases/tag/1.14.0>
- <https://developer.android.com/jetpack/androidx/releases/compose-material3>
- <https://developer.android.com/reference/kotlin/androidx/compose/material3/package-summary>
- <https://developer.android.com/develop/ui/compose/migrate/strategy>

**Secondary — corroborating only; no fact above rests on these alone**

- <https://mvnrepository.com/artifact/com.google.android.material/material> (version dates, corroborated 3×)
- <https://dev.to/pulkitgovrani/android-views-is-now-in-maintenance-mode-heres-what-that-actually-means-for-android-devs-1pmd>

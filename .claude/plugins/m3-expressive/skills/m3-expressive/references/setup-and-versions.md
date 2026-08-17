# M3 Expressive — Setup, Versions, and Opt-Ins

Everything needed to get an Expressive project compiling. Read this **before** writing any Gradle
edit or any Expressive composable.

**All version numbers in this file are as of 2026-08-14** and were read against the AndroidX source
tree pinned at `1.5.0-alpha26`'s terminal commit `4d087bd6f764b8425a70fd94102f855aa382d94b`, plus a
per-file opt-in census taken at `androidx/androidx` HEAD `360e8cba7ae6` (2026-08-14). They will go
stale. Before pinning, check the live release notes:

- https://developer.android.com/jetpack/androidx/releases/compose-material3
- https://developer.android.com/jetpack/androidx/releases/compose-material3-adaptive
- https://developer.android.com/jetpack/androidx/releases/graphics
- https://developer.android.com/develop/ui/compose/bom/bom-mapping

If the project already has a version catalog, **read it first and match what is there** rather than
imposing the numbers below. The only numbers you must not silently downgrade are `material3` and
`graphics-shapes`.

### Read these two first

1. **§4 — Breaking changes since alpha24.** If any code in this project (or in your own memory of
   this API) was written against `1.5.0-alpha24` or earlier, several call sites **will not compile**
   on alpha26. `ToggleButtonDefaults.shapes()` is the big one: it is `DeprecationLevel.HIDDEN`, so
   it is invisible to Kotlin source.
2. **§5 — Opt-ins.** The old blanket rule "assume every Expressive API needs
   `@OptIn(ExperimentalMaterial3ExpressiveApi::class)`" is **now wrong**. Most of the surface has
   graduated. A measured census names the small set that is still gated.

---

## 1. Current versions

| Artifact | Stable | Beta / RC | Alpha |
| --- | --- | --- | --- |
| `androidx.compose.material3:material3` | **1.4.0** (2025-09-24) | — / — | **1.5.0-alpha26** (2026-08-12) |
| `androidx.compose.material3:material3-window-size-class` | 1.4.0 | — | 1.5.0-alpha26 |
| `androidx.compose.material3:material3-adaptive-navigation-suite` | 1.4.0 | — | 1.5.0-alpha26 |
| `androidx.compose.material3:material3-ripple` | — | — | tracks the material3 group (new in alpha24) |
| `androidx.compose.material3.adaptive:adaptive` / `-layout` / `-navigation` | **1.3.0** (2026-08-12) | — | — |
| `androidx.compose.material3.adaptive:adaptive-navigation3` | **ships within 1.3.0** (new) | — | — |
| `androidx.graphics:graphics-shapes` | **1.1.0** (2025-10-22) | — | — |
| `androidx.navigation3:navigation3-runtime` / `-ui` | **1.1.6** (2026-08-12) | — | 1.2.0-alpha07 (2026-07-29) |
| `androidx.navigation:navigation-*` | 2.9.8 | **2.10.0-rc01** | — |
| `androidx.compose:compose-bom` | **2026.08.00** | — | `compose-bom-alpha` is a separate line |
| `androidx.compose.{ui,foundation,runtime,animation,material}` | 1.12.0 (2026-08-12) | — | 1.13.0-alpha01 |

Facts that trip people up:

- **1.5.0 has NOT reached beta or RC.** Both cells on the release-notes header table are a literal
  `-`. Any code or plan that assumes a 1.5.0 beta exists is wrong.
- **1.4.0 is the only stable material3.** There is no 1.4.1 / 1.4.2.
- **`material3-adaptive` went stable at 1.3.0 on 2026-08-12** (it was `1.3.0-rc01` a month earlier),
  and 1.3.0 is the release that **adds `adaptive-navigation3`**. If you were pinning `1.3.0-rc01`,
  drop the suffix.
- `material3-adaptive-navigation-suite` is versioned with the **material3 group** (`1.4.0` /
  `1.5.0-alpha26`), **not** with the `material3.adaptive` group (`1.3.0`). Two groups, two version
  trains. Mixing them up produces "cannot resolve" errors that look like typos.
- `compose-bom:2026.08.00` is expected to map `material3` → **1.4.0** (BOMs ship stable only).
  **UNVERIFIED** — the BOM mapping table is JS-driven and the material3 row could not be read
  directly. Confirm with `./gradlew :app:dependencies --configuration debugRuntimeClasspath` if it
  matters.
- `material3-ripple` was introduced in alpha24 and is versioned with the material3 group, but it is
  **not** listed in the release page's verbatim dependency block. Verify the coordinate resolves
  before adding it.
- Essentially the entire Expressive surface already exists in **1.4.0**, gated behind `@OptIn`.
  Graduation happened across **1.5.0-alpha15 → alpha26**, and by alpha26 **most of it is
  un-gated** — see §5 and §6.

### ⚠️ The date trap — 1.4.0 is NOT from August 2026

The compose-material3 release page's header table reads:

| Latest Update | Stable Release | Release Candidate | Beta Release | Alpha Release |
|---|---|---|---|---|
| August 12, 2026 | 1.4.0 | - | - | 1.5.0-alpha26 |

**"August 12, 2026" is the *page's* last-updated date, not 1.4.0's release date.**
**material3 1.4.0 shipped on 2025-09-24** — roughly eleven months old — confirmed against an
independent dated artifact log (the 2025-09-24 Jetpack artifact wave, which lists
`androidx.compose.material3:material3:1.4.0` alongside `material3-window-size-class` and
`material3-adaptive-navigation-suite` at the same version).

This trap is **actively reproducible**. Automated extraction of that page repeatedly returns
"Version 1.4.0 — August 12, 2026" because the page body is long enough that the real 1.4.0 section
falls past the fetch truncation point, so a summarizer falls back to the header cell. Our own
earlier research pass fell for it. **If you re-read that page and come back with "1.4.0, August 12
2026", you have hit the trap — do not overwrite this file with that.** August 12, 2026 is the
correct date for `1.5.0-alpha26`, `material3-adaptive 1.3.0`, `navigation3 1.1.6`, and Compose
1.12.0 — just not for material3 1.4.0.

**Alpha cadence is a 2-week train:** alpha23 = 2026-07-01, alpha24 = 07-15, alpha25 = 07-29,
alpha26 = 08-12. alpha26 is current; the next alpha is expected ~2026-08-26. If today is past that,
assume this file is one or more trains behind and verify at the source.

### Which line to target

| Situation | Target |
| --- | --- |
| Production app, must be on stable | `material3:1.4.0` + BOM, global opt-in for both experimental annotations (on 1.4.0 the blanket opt-in rule **is** still true) |
| Want the full graduated Expressive surface, tolerant of alpha churn | BOM `2026.08.00` + `material3:1.5.0-alpha26` pinned after it. This is what Google's own samples do. |
| Want alpha Expressive but minimal churn risk | Pin a slightly older alpha deliberately — jetpacker (Google) ships `1.5.0-alpha16`, androidify `1.5.0-alpha20`, Jetcaster `1.5.0-alpha22`. All are known-good. |
| Compose Multiplatform | `compose-bom-alpha` (Tomato's approach) — the stable BOM will not resolve the 1.5.0-alpha line |
| Need `MaterialShapes` / `Morph` | any of the above **plus** `androidx.graphics:graphics-shapes:1.1.0`, and opt in regardless of version (see §5, §7.1) |
| Adaptive panes / nav3 list-detail | `material3.adaptive:1.3.0` (now stable) — and `adaptive-navigation3` is available from that same 1.3.0 |

---

## 2. Complete `libs.versions.toml`

Copy-paste starting point for a new Expressive app. AGP/Kotlin numbers match Google's own JetPacker
sample (AGP 9.2.1 / Kotlin 2.3.10), which is the toolchain verified to work with a pinned material3
alpha. Med runs the same AGP with Kotlin 2.4.0 — both work. The `material3`, `graphicsShapes`, and
`adaptive` lines are the load-bearing ones; adjust the rest to the toolchain the project uses.

```toml
[versions]
agp = "9.2.1"
kotlin = "2.3.10"        # Med runs 2.4.0 on the same AGP; either is fine
composeBom = "2026.08.00"
activityCompose = "1.13.0"; coreKtx = "1.18.0"; lifecycle = "2.10.0"

# Material 3 Expressive — pinned explicitly, the stable BOM never ships alphas
material3 = "1.5.0-alpha26"
material3WindowSizeClass = "1.5.0-alpha26"
material3AdaptiveNavigationSuite = "1.5.0-alpha26"

adaptive = "1.3.0"       # DIFFERENT version train from material3. Stable as of 2026-08-12.
nav3 = "1.1.6"           # optional; adaptive-navigation3 lives in the adaptive group above
graphicsShapes = "1.1.0" # required for MaterialShapes / RoundedPolygon / Morph
materialKolor = "4.1.1"  # optional: seed-color scheme generation on the 2025 color spec

[libraries]
# The ordinary set — core-ktx, activity-compose, lifecycle-* with their own version.refs;
# omitted here for brevity.

# Platform BOM — governs compose-ui / foundation / runtime versions
androidx-compose-bom             = { module = "androidx.compose:compose-bom", version.ref = "composeBom" }
# No version on these — the BOM resolves them
androidx-compose-ui              = { module = "androidx.compose.ui:ui" }
androidx-compose-ui-graphics     = { module = "androidx.compose.ui:ui-graphics" }
androidx-compose-ui-tooling      = { module = "androidx.compose.ui:ui-tooling" }
androidx-compose-ui-tooling-preview = { module = "androidx.compose.ui:ui-tooling-preview" }
androidx-compose-foundation      = { module = "androidx.compose.foundation:foundation" }
androidx-compose-animation       = { module = "androidx.compose.animation:animation" }

# material3 — version.ref present ON PURPOSE, this overrides the BOM
androidx-material3 = { module = "androidx.compose.material3:material3", version.ref = "material3" }
androidx-material3-window-size-class = { module = "androidx.compose.material3:material3-window-size-class", version.ref = "material3WindowSizeClass" }
androidx-material3-adaptive-navigation-suite = { module = "androidx.compose.material3:material3-adaptive-navigation-suite", version.ref = "material3AdaptiveNavigationSuite" }

androidx-adaptive             = { module = "androidx.compose.material3.adaptive:adaptive", version.ref = "adaptive" }
androidx-adaptive-layout      = { module = "androidx.compose.material3.adaptive:adaptive-layout", version.ref = "adaptive" }
androidx-adaptive-navigation  = { module = "androidx.compose.material3.adaptive:adaptive-navigation", version.ref = "adaptive" }
androidx-adaptive-navigation3 = { module = "androidx.compose.material3.adaptive:adaptive-navigation3", version.ref = "adaptive" }

androidx-navigation3-runtime = { module = "androidx.navigation3:navigation3-runtime", version.ref = "nav3" }
androidx-navigation3-ui      = { module = "androidx.navigation3:navigation3-ui", version.ref = "nav3" }
androidx-graphics-shapes     = { module = "androidx.graphics:graphics-shapes", version.ref = "graphicsShapes" }
material-kolor               = { module = "com.materialkolor:material-kolor", version.ref = "materialKolor" }

[plugins]
android-application = { id = "com.android.application", version.ref = "agp" }
android-library     = { id = "com.android.library", version.ref = "agp" }
kotlin-compose      = { id = "org.jetbrains.kotlin.plugin.compose", version.ref = "kotlin" }
# NOTE: on AGP 9.x you do NOT need org.jetbrains.kotlin.android — AGP brings built-in Kotlin
# support. JetPacker (AGP 9.2.1) applies no such plugin anywhere. On AGP 8.x, add it back.
```

### Matching `app/build.gradle.kts`

```kotlin
plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    // On AGP 8.x also: alias(libs.plugins.kotlin.android)
}

android {
    namespace = "com.example.expressive"
    compileSdk = 36
    defaultConfig { applicationId = "com.example.expressive"; minSdk = 24; targetSdk = 36 }
    buildFeatures { compose = true }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }
}

kotlin {
    jvmToolchain(21)
    compilerOptions {
        // Kotlin 2.2+ annotation-use-site default migration flag. Required in practice on
        // Kotlin 2.3 + AGP 9 — both JetPacker and vivi-music set it.
        freeCompilerArgs.add("-Xannotation-default-target=param-property")

        // Global Expressive opt-in — a convenience/safety net, NOT a requirement. See §5.
        optIn.addAll(
            "androidx.compose.material3.ExperimentalMaterial3Api",
            "androidx.compose.material3.ExperimentalMaterial3ExpressiveApi",
            "androidx.compose.material3.adaptive.ExperimentalMaterial3AdaptiveApi",
        )
    }
}

dependencies {
    implementation(platform(libs.androidx.compose.bom))

    // Pinned AFTER the platform line — this override is the documented pattern and is what
    // Google's own samples do (§8).
    implementation(libs.androidx.material3)
    implementation(libs.androidx.material3.window.size.class1)
    implementation(libs.androidx.material3.adaptive.navigation.suite)

    implementation(libs.androidx.adaptive)
    implementation(libs.androidx.adaptive.layout)
    implementation(libs.androidx.adaptive.navigation)

    implementation(libs.androidx.graphics.shapes)

    // BOM-governed, no version needed
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.compose.animation)
    implementation(libs.androidx.compose.ui.tooling.preview)
    // + the usual activity.compose / core.ktx / lifecycle.* set, and material.kolor if used

    debugImplementation(libs.androidx.compose.ui.tooling)
}
```

Toolchain notes:

- `compileSdk 37` was **required** by some 1.5.0 alphas and that requirement was **removed in
  1.5.0-alpha23**. `compileSdk = 36` is fine on alpha23+. If a build fails demanding compileSdk 37
  on an older alpha, either raise compileSdk or move to alpha23+.
- JetPacker compiles against `compileSdk 37` using the **AGP 9 minor-version DSL** —
  `compileSdk { version = release(37) { minorApiLevel = 0 } }` — which you will see in new Google
  samples and which is not a typo. Library modules in the same project still use the scalar form.
- JetPacker uses `jvmToolchain(17)` / `JavaVersion.VERSION_17`; the four community reference apps
  use 21. Both work. Match whatever the project already has.

### The release-notes coordinates, verbatim

If the project has no version catalog and you want the smallest diff. This block is printed
verbatim on the release page as of 2026-08-14 — note that it deliberately mixes a stable
`material3` with an alpha `adaptive-navigation-suite`:

```kotlin
dependencies {
    implementation("androidx.compose.material3:material3:1.4.0")
    implementation("androidx.compose.material3:material3-window-size-class:1.4.0")
    implementation("androidx.compose.material3:material3-adaptive-navigation-suite:1.5.0-alpha26")
}
```

```kotlin
dependencies {
    implementation("androidx.compose.material3.adaptive:adaptive:1.3.0")
    implementation("androidx.compose.material3.adaptive:adaptive-layout:1.3.0")
    implementation("androidx.compose.material3.adaptive:adaptive-navigation:1.3.0")
    implementation("androidx.compose.material3.adaptive:adaptive-navigation3:1.3.0")

    implementation("androidx.graphics:graphics-shapes:1.1.0")

    // Living on the Expressive edge — replace the 1.4.0 material3 line above with:
    // implementation("androidx.compose.material3:material3:1.5.0-alpha26")
}
```

---

## 3. BOM versus an explicit pin

**The stable Compose BOM never ships alpha versions.** `compose-bom:2026.08.00` resolves material3
to `1.4.0` (inference — see the UNVERIFIED note in §1). There is no stable BOM that will give you
`1.5.0-alphaNN`.

So there are exactly three valid setups:

| Setup | How | Gets you |
| --- | --- | --- |
| **BOM only** | `implementation(platform("androidx.compose:compose-bom:2026.08.00"))` + `implementation("androidx.compose.material3:material3")` (no version) | material3 1.4.0. Expressive present but opt-in-gated everywhere. |
| **BOM + explicit pin** (recommended for Expressive) | platform line first, then `implementation("androidx.compose.material3:material3:1.5.0-alpha26")` | Alpha material3 with BOM-governed ui/foundation/runtime. **This is what Google's own samples do** — see §8. |
| **Alpha BOM** | `implementation(platform("androidx.compose:compose-bom-alpha:<ver>"))` | Alpha everything, including compose-ui. What Tomato does (`compose-bom-alpha:2026.03.00`) to reach `SegmentedListItem`, `ListItemDefaults.segmentedShapes`, `veilOut`/`unveilIn`, `shapes.extraLargeIncreased`. |

Rules:

1. **The pin must come after the `platform(...)` line** in the `dependencies` block — Gradle applies
   the explicit version as a direct constraint that wins over the BOM's managed version.
2. In a version catalog the pin is expressed either as a `version.ref` on the `material3` entry
   while other Compose entries have none, or as an **inline literal version** —
   `androidx-compose-material3 = { group = "androidx.compose.material3", name = "material3",
   version = "1.5.0-alpha16" }`. JetPacker (Google) and LastChat both use the inline form; sibling
   entries like `material-icons-extended` carry no version so the BOM resolves them.
3. Do not pin material3 to an alpha and leave `compose-ui` far behind — the Expressive components
   consume newer foundation/animation APIs. Keep the BOM reasonably current, or use the alpha BOM.
4. `androidx.graphics:graphics-shapes` arrives **transitively** through material3, so
   `MaterialShapes` and `import androidx.graphics.shapes.Morph` work without declaring it (JetPacker
   never declares it). Declare it explicitly anyway if you name `RoundedPolygon` / `Morph` types
   directly — that is the documented practice and it protects you from a transitive version change.

---

## 4. Breaking changes since 1.5.0-alpha24

If any code was written against alpha24 or earlier, fix these before anything else. Urgency:
**P0** = hard compile break, **P1** = deprecation warning now / break later, **P2** = behavior or
visuals only.

Summary, loudest first:

| # | Change | Level | Since |
| --- | --- | --- | --- |
| 4.1 | `ToggleButtonDefaults.shapes()` → `shapesFor(buttonHeight: Dp)` / `ToggleButtonShapes(...)` | **P0** (HIDDEN) | alpha25 |
| 4.2 | `Modifier.animateWidth` split; `compressionLimit` `PaddingValues` → `Dp` | **P0** | alpha25 |
| 4.3 | `SearchBarScrollBehavior` scroll offsets → `SearchBarScrollState` | **P0** | alpha26 |
| 4.4 | `ComponentOverride` APIs removed, no replacement | **P0** | alpha25 (finished) |
| 4.5 | `ButtonGroupScope` is now a `sealed interface` | **P0** if implemented | alpha25 |
| 4.6 | `SliderState` no longer publicly implements `DraggableState` | **P0** if relied on | alpha25 |
| 4.7 | `DropdownMenuItem` `trailingIcon` → `trailingContent` (3 overloads) | **P0** if named | alpha25 |
| 4.8 | `ExposedDropdownMenu` is now an extension function | P1 (import) | alpha26 |
| 4.9 | `TonalToggleButton` → `FilledTonalToggleButton` | P1 (warns) | alpha25 |
| 4.10 | `SplitButtonDefaults.leadingButtonShapes` / `trailingButtonShapes` (`CornerSize`) deprecated → `*ShapesFor(Dp)`. **`SplitButtonLayout` was NOT renamed** — earlier reading of the release note was wrong | P1 (warns) | alpha25 |
| 4.11 | `SliderState.Saver` / `RangeSliderState.Saver` 2-param overloads deprecated | P1 | alpha25 |
| 4.12 | `BasicAlertDialog` and `BottomAppBar` graduated to stable | P2 (stale opt-ins) | alpha25 / alpha26 |
| 4.13 | Wide nav rail padding, horizontal `NavigationItem` label color, menu colors | P2 (silent visual) | alpha26 |

### 4.1 `ToggleButtonDefaults.shapes` → `shapesFor(buttonHeight: Dp)` — **P0, highest urgency**

**This is not a rename. The argument changed meaning.** `shapesFor` takes the button's **height as a
`Dp`** and derives the shape set from it (Expressive sizes shapes by height). It does not take
shapes. There is no zero-arg `shapesFor()`.

Verified replacement signature: `@Composable public fun shapesFor(buttonHeight: Dp): ToggleButtonShapes`

```kotlin
// OLD (alpha24) — both of these are DeprecationLevel.HIDDEN and DO NOT COMPILE on alpha25+
val shapes = ToggleButtonDefaults.shapes()
val custom = ToggleButtonDefaults.shapes(shape = a, pressedShape = b, checkedShape = c)

// NEW (alpha25+)
val shapes = ToggleButtonDefaults.shapesFor(ButtonDefaults.MinHeight)
val custom = ToggleButtonShapes(shape = a, pressedShape = b, checkedShape = c)
```

Note carefully: **for the customizing case the replacement is the `ToggleButtonShapes(...)
constructor`, not `shapesFor`.** Only the defaulting case maps to `shapesFor`. The verified
`ReplaceWith` targets are `ToggleButtonShapes()` and
`ToggleButtonShapes(shape, pressedShape, checkedShape)`.

`DeprecationLevel.HIDDEN` means the old overloads exist in the binary ("Maintained for binary
compatibility") but are **invisible to the Kotlin compiler**. You get an unresolved-reference error,
not a deprecation warning. alpha24-era source hard-fails.

Related and unaffected: `ButtonGroupDefaults.connectedLeadingButtonShapes()` /
`connectedMiddleButtonShapes()` / `connectedTrailingButtonShapes()` still return `ToggleButtonShapes`
with their old signatures. The connected-group pattern in §9.3 needs no change.

alpha26 also added `ElevatedToggleButtonDefaults`, `FilledTonalToggleButtonDefaults`, and
`OutlinedToggleButtonDefaults`, and "cleaned up non-semantic shape properties in
`ToggleButtonDefaults`" (Ia0a85). If you referenced a raw shape property on `ToggleButtonDefaults`
by name, check it still exists.

### 4.2 `Modifier.animateWidth` split into two overloads — **P0**

Two breaks in one change. Verified, on `ButtonGroupScope`:

```kotlin
public fun Modifier.animateWidth(interactionSource: InteractionSource): Modifier
public fun Modifier.animateWidth(interactionSource: InteractionSource, compressionLimit: Dp): Modifier
```

1. `compressionLimit` changed type from **`PaddingValues` to `Dp`**.
2. It is no longer defaultable in one signature — the 1-arg overload exists so it can be omitted,
   and that overload resolves layout direction **dynamically**.

```kotlin
// OLD
Modifier.animateWidth(interactionSource, compressionLimit = PaddingValues(horizontal = 8.dp))

// NEW
Modifier.animateWidth(interactionSource, compressionLimit = 8.dp)
// or omit it entirely to get dynamic layout-direction resolution:
Modifier.animateWidth(interactionSource)
```

### 4.3 `SearchBarScrollBehavior` scroll offsets → `SearchBarScrollState` — **P0**

`scrollOffset`, `scrollOffsetLimit`, and `contentOffset` were **removed from
`SearchBarScrollBehavior`** and moved onto a new `SearchBarScrollState` class (alpha26, Ib24e4).

```kotlin
@Stable
public interface SearchBarScrollBehavior {
    public val scrollState: SearchBarScrollState
    public val nestedScrollConnection: NestedScrollConnection
    public val searchBarScrollBehaviorModifier: Modifier
}

@Stable
public class SearchBarScrollState(
    initialScrollOffsetLimit: Float,
    initialScrollOffset: Float,
    initialContentOffset: Float,
) {
    public var scrollOffset: Float
    public var scrollOffsetLimit: Float
    public var contentOffset: Float
    public companion object { public val Saver: Saver<SearchBarScrollState, *> }
}

// Same three params, defaulting to -Float.MAX_VALUE / 0f / 0f:
@Composable public fun rememberSearchBarScrollState(...): SearchBarScrollState
```

The fix is one extra hop: `behavior.scrollOffset` → `behavior.scrollState.scrollOffset`, and the
same for `.scrollOffsetLimit` and `.contentOffset`.

The **factory function name** used to obtain the behavior
(`SearchBarDefaults.enterAlwaysSearchBarScrollBehavior()`) is **UNVERIFIED** at this SHA — the
interface and state class shapes are verified, the factory name is not. Check it in the artifact.

### 4.4 `ComponentOverride` APIs removed — **P0, no migration**

Removal spans two releases: alpha23's "Remove `ComponentOverride` APIs" and alpha25's repeat of the
same bullet (I3784b). By alpha25 they are gone entirely. There is no deprecation cycle, no
replacement named anywhere, and **no documented migration** (UNVERIFIED whether one was ever
intended). Any code touching `ComponentOverride`, `LocalXOverride` hooks, or
`@ExperimentalMaterial3ComponentOverrideApi` must be **rewritten**, not ported — wrap the component
yourself, or provide your own `CompositionLocal`.

### 4.5 `ButtonGroupScope` is now a `sealed interface` — **P0 if implemented**

It is declared `public sealed interface ButtonGroupScope`. Any code implementing it outside the
material3 module no longer compiles. Consuming it as a receiver (the normal case —
`content: ButtonGroupScope.() -> Unit`) is unaffected.

### 4.6 `SliderState` no longer publicly implements `DraggableState` — **P0 if relied on**

alpha25 (I7c91b). Code that passed a `SliderState` where a `DraggableState` was expected, or called
`DraggableState` members on it, stops compiling. No replacement is documented.

### 4.7 `DropdownMenuItem` `trailingIcon` → `trailingContent` — **P0 if named**

Applies to the **shape / checked / selected** overloads only. The plain overload
(`text, onClick, modifier, leadingIcon, trailingIcon, …`, declared `public expect fun`) **still uses
`trailingIcon`**. This is a per-overload fix, **not** a global find-and-replace.

The shape overload, verified (`@JvmName("DropdownMenuItemNew")`):
`DropdownMenuItem(onClick, text, shape, modifier, leadingIcon, trailingContent, supportingText,
enabled, colors, horizontalArrangement, contentPadding, interactionSource)`. The checked and
selected overloads mirror it (`checked`/`onCheckedChange` + `checkedLeadingIcon`, and
`selected`/`onClick` + `selectedLeadingIcon`), both taking `shapes: MenuItemShapes` and defaulting
`colors` to `MenuDefaults.selectableItemColors()`.

Old overloads survive as `DropdownMenuItemLegacy` at `DeprecationLevel.HIDDEN` with
`@JvmName("DropdownMenuItem")`: **binary compatible, source-incompatible if you passed
`trailingIcon =` by name.**

Correspondingly on `MenuItemColors`: `trailingIconColor` → `trailingContentColor` and
`disabledTrailingIconColor` → `disabledTrailingContentColor` (old names deprecated with
`ReplaceWith`, so they warn rather than break). New members added: `containerColor`,
`disabledContainerColor`, `selectedContainerColor`, `selectedTextColor`, `selectedLeadingIconColor`,
`selectedTrailingContentColor`. **PARTIAL** — member *names* verified from source; exact declaration
formatting was normalized during extraction, so trust the member list, not a reconstructed
signature.

### 4.8 `ExposedDropdownMenu` is now an extension function — **P1, import change**

```kotlin
public sealed class ExposedDropdownMenuBoxScope { /* … */ }

public fun ExposedDropdownMenuBoxScope.ExposedDropdownMenu(
    expanded: Boolean,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    scrollState: ScrollState = rememberScrollState(),
    matchAnchorWidth: Boolean = true,
    shape: Shape = MenuDefaults.shape,
    containerColor: Color = MenuDefaults.containerColor,
    tonalElevation: Dp = MenuDefaults.TonalElevation,
    shadowElevation: Dp = MenuDefaults.ShadowElevation,
    border: BorderStroke? = null,
    content: @Composable ColumnScope.() -> Unit,
)
```

The receiver is a `public sealed class`, not an interface. The old member version survives at
`DeprecationLevel.HIDDEN`, so binary compatibility holds and source does not. Call sites inside
`ExposedDropdownMenuBox { … }` look identical; you just need
`import androidx.compose.material3.ExposedDropdownMenu`. Wildcard imports
(`androidx.compose.material3.*`) are unaffected. This is exactly what the release note means by
"You may need to update your code with a new import."

### 4.9 `TonalToggleButton` → `FilledTonalToggleButton` — **P1, pure rename**

The parameter list is **identical**; only the name changed. The old name survives at
`DeprecationLevel.WARNING` with a full `ReplaceWith`.

```kotlin
// OLD
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
TonalToggleButton(checked = checked, onCheckedChange = { checked = it }) { Text("Hi") }

// NEW — and note the @OptIn is now redundant
FilledTonalToggleButton(checked = checked, onCheckedChange = { checked = it }) { Text("Hi") }
```

Verified full signature — parameters in order: `checked, onCheckedChange, modifier, enabled, shapes,
colors, elevation, border, contentPadding, interactionSource, content`, defaulting `shapes` to
`ToggleButtonDefaults.shapesFor(ButtonDefaults.MinHeight)`, `colors` to
`FilledTonalToggleButtonDefaults.colors()`, `elevation` to
`ButtonDefaults.filledTonalButtonElevation()`, and `contentPadding` to
`ButtonDefaults.contentPaddingFor(ButtonDefaults.MinHeight)`.

Two non-obvious deltas: the old `TonalToggleButton` carried a **declaration-level**
`@ExperimentalMaterial3ExpressiveApi`, while `FilledTonalToggleButton` carries only an internal
`@OptIn` — **callers no longer need an opt-in**. And its `colors` default moved to the new
`FilledTonalToggleButtonDefaults` object, where the factory is named plainly `colors()`. The old
`ToggleButtonDefaults.filledTonalToggleButtonColors()` (and the elevated / outlined / tonal
equivalents, plus `outlinedToggleButtonBorder()`) are `@Deprecated @BytecodeOnly` at alpha26 —
binary-compatible for already-compiled artifacts, but **uncallable from Kotlin source**. Replace with
`FilledTonalToggleButtonDefaults.colors()`, `ElevatedToggleButtonDefaults.colors()`,
`OutlinedToggleButtonDefaults.colors()` and `OutlinedToggleButtonDefaults.border(enabled, checked)`.
Verified in `compose/material3/material3/api/current.txt` at androidx HEAD `360e8cba` (2026-08-14).

### 4.10 `SplitButtonDefaults.*Shapes(CornerSize)` deprecated — **P1. `SplitButtonLayout` was NOT renamed**

**Correction — this reverses a claim earlier versions of this file asserted as resolved.** Earlier
text said the alpha25 note "Deprecated `SplitButtonLayout` Api" (Ic9840) meant a
`SplitButtonLayout` → `SplitButton` composable rename, "pure rename, identical parameters,
RESOLVED". **That was wrong.**

Verified in `compose/material3/material3/api/current.txt` at androidx HEAD `360e8cba`, 2026-08-14
(post-alpha26):

- The only top-level split-button composable is `SplitButtonLayout`. It carries **no `@Deprecated`
  annotation**.
- **There is no `SplitButton` composable** — zero matches for a top-level `SplitButton(` in any api
  txt file. All **13** call sites in androidx's own `material3` Kotlin sources and samples use
  `SplitButtonLayout`.

```kotlin
// current and undeprecated on every version, incl. alpha26
@Composable
public fun SplitButtonLayout(
    leadingButton: @Composable () -> Unit,
    trailingButton: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    spacing: Dp = SplitButtonDefaults.Spacing,
)
```

What *is* deprecated in this family are the `SplitButtonDefaults` shape helpers that take a
`CornerSize`:

```kotlin
@Deprecated SplitButtonDefaults.leadingButtonShapes(CornerSize)   // → leadingButtonShapesFor(buttonHeight: Dp)
@Deprecated SplitButtonDefaults.trailingButtonShapes(CornerSize)  // → trailingButtonShapesFor(buttonHeight: Dp)
```

**Inference, not fact:** those deprecated `*Shapes(CornerSize)` helpers are most plausibly what the
alpha25 "Deprecated `SplitButtonLayout` API" release note actually referred to. What is fact is that
`SplitButtonLayout` is present and undeprecated at alpha26 and `SplitButton` does not exist.

`SplitButtonLayout` requires **no experimental opt-in** — the string "Experimental" does not occur
anywhere in `SplitButton.kt` at the alpha26 SHA. Write `SplitButtonLayout` on every pin from 1.4.0
through alpha26. `SplitButtonShapes(shape, pressedShape, checkedShape)` is a public class if you
need to build shapes by hand.

### 4.11 Slider `Saver` overloads deprecated — **P1**

The 2-parameter `SliderState.Saver` and `RangeSliderState.Saver` are deprecated in favor of new
overloads that take `steps` (alpha25, I7c91b). **The new signatures are UNVERIFIED** — the
deprecation is confirmed from the release note, the replacement declaration was not read. Follow the
`ReplaceWith` the compiler shows you rather than writing one from memory.

### 4.12 `BasicAlertDialog` and `BottomAppBar` graduated — **P2, cleanup**

- `BasicAlertDialog` graduated from experimental in alpha25 (If157c).
- `BottomAppBar` **and its associated methods** graduated in alpha26 (I42c61) — "These APIs no
  longer require the `@ExperimentalMaterial3Api` opt-in."

Any `@OptIn(ExperimentalMaterial3Api::class)` kept **solely** for one of these is now redundant.
Redundant opt-ins are warnings, not errors — unless the project builds with warnings-as-errors, in
which case this is a break.

### 4.13 Silent visual/behavioral changes — **P2, the compiler will not flag these**

| Change | Effect | Opt-out |
| --- | --- | --- |
| Wide navigation rail content padding (alpha26, I572bb) | **Bottom padding is now 0** — previously 44.dp | `contentPadding = PaddingValues(0.dp, 44.dp, 0.dp, 44.dp)` |
| Horizontal `NavigationItem` label contrast (alpha26, I85855) | Horizontal item's selected label color now matches `selectedIconColor` instead of `secondary`; controlled by the new `selectedTextColorStartIconPosition` (vertical items use `selectedTextColorTopIconPosition`, unchanged) | `ShortNavigationBarItemDefaults`/`WideNavigationRailItemDefaults`.colors().copy(selectedTextColorStartIconPosition = MaterialTheme.colorScheme.secondary)` |
| `MenuDefaults.itemColors` partial customization (alpha26, Iea847) | Unprovided colors are no longer clobbered to `Color.Unspecified` — previously-broken themes may now render differently | none |
| Segmented list item with a single item (alpha25, I2ea1c) | Single-item lists get the correct rounded shape | none |
| `OutlinedToggleButton` border (alpha25, Icb433) | Border stroke now animates smoothly | none |
| `TimePickerState` (alpha26, Iad905) | Active selection mode now persists across state restoration; new `initialSelection` parameter on the factory | pass `initialSelection` explicitly |

### 4.14 The catch-all risk

alpha26 contains the bullet **"Updated the public API surface to align with recent API review
feedback. (I71aff, b/532657001)"** — an **unenumerated** API sweep. It may contain renames that are
not individually documented anywhere. If something fails to resolve on alpha26 and is not in this
section, this bullet is the likely cause. **Trust the compiler over this document.**

---

## 5. Opt-in annotations

### The headline: the blanket rule is dead

Guidance that says "assume every Expressive API needs
`@OptIn(ExperimentalMaterial3ExpressiveApi::class)`" was correct for 1.4.0 and is **wrong for the
1.5.0 alpha line**. Measured at androidx HEAD `360e8cba7ae6` (2026-08-14) in the canonical samples
module (`compose/material3/material3/samples/…`), `ExperimentalMaterial3ExpressiveApi` appears **37**
times against **109** for `ExperimentalMaterial3Api` — and several whole Expressive sample files
carry **zero** opt-ins of any kind.

### The annotations

**`androidx.compose.material3.ExperimentalMaterial3ExpressiveApi`** — the Expressive gate. Source,
verbatim (`ExperimentalMaterial3Api` is structurally identical, with "This material API is
experimental…" as its message and a `public` modifier):

```kotlin
@RequiresOptIn(
    "This material3 API is experimental and is likely to change or to be removed in the" +
        " future."
)
@Retention(AnnotationRetention.BINARY)
annotation class ExperimentalMaterial3ExpressiveApi
```

No `@Target`, no explicit `level` → default `RequiresOptIn.Level.ERROR`, applies to all targets.
**Missing either is a compile error, not a warning.**

**`androidx.compose.material3.ExperimentalMaterial3Api`** — the older, broader material3 gate. Still
the gate that most graduated-from-Expressive components sit behind (app bars, search bars,
carousels, split button, floating toolbars, nav rails — see the table below).

**`androidx.compose.material3.ExperimentalMaterial3ComponentOverrideApi`** — gated the
component-override mechanism. **Removed** (alpha23 → alpha25). Exists in 1.4.0 only. See §4.4.

**`androidx.compose.material3.Material3ExpressiveApi`** — added in alpha18, described in the release
notes as "Add `Material3ExpressiveApi` annotation (non-OptIn)". It is a marker/documentation
annotation, **not** a `@RequiresOptIn` gate. Seeing it on an API does **not** mean you need to opt
in. Its exact declaration is UNVERIFIED.

**`androidx.compose.material3.adaptive.ExperimentalMaterial3AdaptiveApi`** — separate gate for the
`material3.adaptive` group (pane scaffolds, `calculatePaneScaffoldDirective`). Needed independently
of the Expressive gate.

### What actually needs an opt-in at alpha26

Measured per-file from the androidx samples module (counts are occurrences of each annotation
string in the file), cross-checked against declaration-level annotations in the alpha26 source tree.

**Graduated — NO caller opt-in required:**

| Family | Evidence |
| --- | --- |
| `HorizontalFloatingToolbar` / `VerticalFloatingToolbar` / `FloatingToolbarDefaults` | `FloatingToolbarSamples.kt`: 0 Expressive, 15 `ExperimentalMaterial3Api` |
| App bars: `TopAppBar`, `MediumFlexibleTopAppBar`, `LargeFlexibleTopAppBar`, `TwoRowsTopAppBar`, `FlexibleBottomAppBar`, `AppBarRow`/`AppBarColumn` | `AppBarSamples.kt`: 0 Expressive, 2 `ExperimentalMaterial3Api` |
| `WideNavigationRail`, `ModalWideNavigationRail`, `ShortNavigationBar`, their items and defaults | `NavigationRailSamples.kt`: 0 Expressive, 4 `ExperimentalMaterial3Api`; **and zero "Experimental" strings in `WideNavigationRail.kt` / `ShortNavigationBar.kt` source** |
| `SearchBar` family: `SearchBarState`, `ExpandedFullScreenSearchBar`, `TopSearchBar` | `SearchBarSamples.kt`: 0 Expressive, 2 `ExperimentalMaterial3Api` |
| Carousel: `HorizontalMultiBrowseCarousel`, `HorizontalUncontainedCarousel`, `HorizontalCenteredHeroCarousel` | `CarouselSamples.kt`: 0 Expressive, 2 `ExperimentalMaterial3Api` |
| `SplitButtonLayout` (there is no `SplitButton`) | `SplitButtonSamples.kt`: 0 Expressive, 2 `ExperimentalMaterial3Api`; zero "Experimental" in `SplitButton.kt` |
| FAB menu: `FloatingActionButtonMenu`, `FloatingActionButtonMenuItem`, `ToggleFloatingActionButton`, `Modifier.animateFloatingActionButton` | `FloatingActionButtonMenuSamples.kt`: 0 Expressive, 2 `ExperimentalMaterial3Api` |
| Wavy progress: `LinearWavyProgressIndicator`, `CircularWavyProgressIndicator`, `WavyProgressIndicatorDefaults` | `ProgressIndicatorSamples.kt`: **0 and 0** — no opt-in at all |
| `MaterialExpressiveTheme`, `expressiveLightColorScheme`, `MotionScheme` | `ThemeSamples.kt`: **0 and 0** — no opt-in at all |
| `ToggleButton`, `ElevatedToggleButton`, `FilledTonalToggleButton`, `OutlinedToggleButton` (base forms), `ToggleButtonDefaults.shapesFor`, the three new `*ToggleButtonDefaults` objects | `ToggleButtonSamples.kt` lines 47/55/65/75/85 carry **no** opt-in |
| `BasicAlertDialog`, `BottomAppBar` | alpha25 If157c, alpha26 I42c61 |
| `LinearTrackStopIndicatorSize`, `LinearIndicatorTrackGapSize`, `CircularIndicatorTrackGapSize` | alpha25 I794d0 |

Note the pattern, and note its limit: for the **app-bar, nav-rail, floating-toolbar, search-bar and
carousel** rows, graduating out of `ExperimentalMaterial3ExpressiveApi` landed them in
`ExperimentalMaterial3Api` — you still need *an* opt-in there, just the older, broader one. It is
**not** a blanket rule: `SplitButtonLayout`, the wavy progress indicators and `MaterialExpressiveTheme` /
`expressiveLightColorScheme` / `MotionScheme` measure **0 and 0** — no annotation at all.

**Still gated behind `ExperimentalMaterial3ExpressiveApi` at alpha26:**

| API | Evidence |
| --- | --- |
| `LoadingIndicator`, `ContainedLoadingIndicator`, `LoadingIndicatorDefaults` | Declaration-level annotation on **all** overloads in `LoadingIndicator.kt`; `LoadingIndicatorSamples.kt`: 6 Expressive. Promotion was **reverted in alpha19** and never restored. |
| `MaterialShapes` + `RoundedPolygon.toShape()` / `.toPath()` / `Morph.toPath()` | `public sealed class MaterialShapes` preceded by `@ExperimentalMaterial3ExpressiveApi` in `MaterialShapes.kt`; `MaterialShapesSamples.kt`: 3 Expressive. Also reverted in alpha19. |
| Expressive Menu / `ExposedDropdownMenu` APIs | `MenuSamples.kt`, `ExposedDropdownMenuSamples.kt`: >0 Expressive |
| `PullToRefreshDefaults.loadingIndicatorColor` / `loadingIndicatorContainerColor` | `PullToRefreshSamples.kt`: >0 Expressive; re-marked experimental in alpha21 |
| `ToggleButton` **size variants only** | `XSmallToggleButtonWithIconSample` (102), `MediumToggleButtonWithIconSample` (126), `LargeToggleButtonWithIconSample` (150) each carry `@OptIn(ExperimentalMaterial3ExpressiveApi::class)` — the base variants do not |
| `ButtonDefaults.contentPaddingFor(buttonHeight)` (1-arg overload) | re-marked experimental in alpha21 |
| `ButtonGroup` / `ButtonGroupScope` / `ButtonGroupDefaults` | **conflicting evidence — see below** |

**The `ButtonGroup` conflict, stated honestly.** The alpha22 release note says "Promote `ButtonGroup`
APIs to stable — removes the deprecated experimental `ButtonGroup` overloads." But the source census
at HEAD shows `ButtonGroupSamples.kt` carrying **5** `ExperimentalMaterial3ExpressiveApi` opt-ins and
**0** `ExperimentalMaterial3Api`. These cannot both describe the whole surface; most likely some
members (e.g. `ButtonGroupDefaults` shape helpers, or the overflow/menu-state types) remained gated
while the core composable graduated. **We did not resolve which.** Practical rule: **keep the opt-in
on `ButtonGroup` code, and trust the compiler at your pin.** A redundant opt-in is a warning; a
missing one is an error.

**Nav rail documentation gap.** `ShortNavigationBar`, `WideNavigationRail`, and
`ModalWideNavigationRail` carry **zero experimental annotations** in the shipped alpha26 source —
verified by two independent string searches per file returning no "Experimental" occurrences — yet
**no graduation note exists anywhere in the release notes.** Every 1.5.0-alpha section was searched;
only two bullets mention these components at all (alpha26's a11y contrast fix, and alpha20's "Remove
deprecated experimental `WideNavigationRail` APIs"). That alpha20 bullet is the likely mechanism:
stable overloads were added and the experimental ones deleted, so the promotion was never written up.
**Treat them as stable (drop the Expressive opt-in), but know this is a documentation gap, not a
research failure** — re-check at the next alpha.

### The three opt-in strategies

All three are in use across the reference apps. Since most of the surface has graduated, the choice
is now about **noise versus visibility**, not about whether the build works.

**A. Per-callsite `@OptIn`**

```kotlin
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun MyScreen() { /* … */ }
```

- **Pro:** the compiler tells you exactly which functions depend on experimental API. When an alpha
  breaks something, the blast radius is visible in the diff.
- **Con:** noisy. Tomato carries **85** per-site annotations.
- **Use when:** a library or shared module; or when only a handful of screens are Expressive. **This
  is what Google's JetPacker does** (dominant form, plus two `@file:OptIn` headers) — with **no**
  Gradle-level opt-in anywhere.

**B. File-level `@file:OptIn`**

```kotlin
@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
```

Must be the first thing in the file, above `package`. The fully-qualified form
(`@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class, …)`) also works and is
what you need when the file annotation precedes its own imports — JetPacker's screenshot tests do
exactly this.

- **Pro:** one line per UI file, still greppable, still scoped.
- **Con:** hides which specific composable in the file is experimental.
- **Use when:** the whole file is UI. Med's approach — **38 files** carry a `@file:OptIn` header.

**C. Global Gradle opt-in — now a convenience, not a requirement**

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

Equivalent: `freeCompilerArgs.addAll("-opt-in=androidx.compose.material3.ExperimentalMaterial3Api",
"-opt-in=androidx.compose.material3.ExperimentalMaterial3ExpressiveApi")` in the same
`compilerOptions` block. Older projects: the same `freeCompilerArgs.add("-opt-in=…")` call inside
`tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>().configureEach { compilerOptions { … } }`,
or in Groovy inside `android { kotlinOptions { freeCompilerArgs += ["-opt-in=…"] } }`.

- **Pro:** zero annotation noise, and it is a **safety net** against the remaining gated set
  (`LoadingIndicator`, `MaterialShapes`, menus, PullToRefresh colors, ToggleButton size variants,
  `ButtonGroup`) plus anything the alpha26 API-review sweep (§4.14) re-gated without a note.
- **Con:** you lose the compiler's map of experimental dependencies. On an alpha bump you find out
  what broke by reading errors, not by reading the diff.
- **Use when:** an app module committed to Expressive throughout. Still the default recommendation
  for app code — just understand it is now insurance, not a prerequisite. LastChat has **zero**
  Expressive annotations in 637 Kotlin files while using `LoadingIndicator`, wavy progress
  indicators, `MaterialShapes`, and `FloatingToolbarDefaults`.

> The exact Gradle snippets above are UNVERIFIED as verbatim doc quotes — no Expressive-specific
> opt-in snippet is published on developer.android.com. They are the standard Kotlin opt-in
> mechanism applied to the annotation FQNs, which **are** verified:
> `androidx.compose.material3.ExperimentalMaterial3Api`,
> `androidx.compose.material3.ExperimentalMaterial3ExpressiveApi`,
> `androidx.compose.material3.ExperimentalMaterial3ComponentOverrideApi` (≤1.4.x only),
> `androidx.compose.material3.adaptive.ExperimentalMaterial3AdaptiveApi`.

### One more rule: stale opt-ins are now the common error

A codebase carrying `@OptIn(ExperimentalMaterial3ExpressiveApi::class)` on anything in the graduated
table is **not wrong, just redundant** — unless the build treats warnings as errors. But do not go on
a deletion spree: **never remove it from `LoadingIndicator`, `MaterialShapes`, menu,
PullToRefresh-color, ToggleButton-size-variant, or `ButtonGroup` call sites.**

---

## 6. Graduation timeline

This table is the answer to "does this API need opt-in at my version". Entries derived from the
official compose-material3 release notes.

| Version | Date | Expressive-relevant entries |
| --- | --- | --- |
| **1.4.0** | 2025-09-24 | Entire Expressive surface ships, **all opt-in gated**. |
| **1.5.0-alpha15** | 2026-02-25 | **Graduate motion scheme from experimental.** `Scrim` component. Standalone static sheet. `MaterialTheme` refactored to a **single `CompositionLocal`** (`MaterialTheme.LocalMaterialTheme.current`). `PullToRefreshBox` gains `enabled` + `threshold`. `FilterChip`/`ElevatedFilterChip`/`AssistChip`: `horizontalSpacing` → `horizontalArrangement`. `TopAppBarDefaults.enterAlwaysScrollBehavior`/`pinnedScrollBehavior` gain `isAtTopState`. Expressive text-button content padding updated; `TextButtonContentPadding` / `TextButtonWithIconContentPadding` un-deprecated. `BottomSheet` backhandler-disable param. |
| **1.5.0-alpha16** | 2026-03-25 | Promote `Typography` constructors + attrs; new `Typography` constructor taking a default `FontFamily`. Promote slider experimental APIs to stable. `DropdownMenuItemTrailingLabel`. `AlertDialogDefaults.IconSize`. **`SearchBarDefaults.InputField` param order changed.** `FilterChip`/`AssistChip` `horizontalSpacing` overload removed. **Scroll-behavior `isAtTop` renamed `isAtStart`.** `BottomSheet` now respects `MaterialTheme.motionScheme`. |
| **1.5.0-alpha17** | 2026-04-08 | `TopAppBarScrollBehavior` + associated methods promoted to stable. |
| **1.5.0-alpha18** | 2026-04-22 | **Promote `WavyProgressIndicator` APIs.** **Promote `MaterialExpressiveTheme` and `expressiveLightColorScheme`.** `FilterChip`/`ElevatedFilterChip`/`InputChip` overloads with **shape morphing**. Inset focus rings via `LocalRippleThemeConfiguration`. Adds non-OptIn `Material3ExpressiveApi` annotation. `rememberWithGapSearchBarState` → **`rememberSearchBarWithGapState`**. `DropdownMenuPopupPositionProviders` + submenu support. |
| **1.5.0-alpha19** | 2026-05-06 | **Promote `ToggleButtons` to stable.** **Graduate FAB and FAB Menu APIs.** **Promote expressive button APIs** (removes deprecated `SmallButtonContentPadding`). Promote expressive menu APIs. **REVERT `MaterialShapes` and `LoadingIndicator` promotions** ← both go *back* to experimental (I30e69, b/497876695, b/497877850) and are **still** experimental at alpha26. Scaffold order APIs moved back to experimental. Fix crash in `Modifier.animateFloatingActionButton`. |
| **1.5.0-alpha20** | 2026-05-19 | **Graduate the split-button APIs** (release note wording: "Graduate `SplitButton` APIs" — the file/family name; the composable is and stays `SplitButtonLayout`). `rememberBottomSheetState` unified (deprecates `rememberModalBottomSheetState`, `rememberStandardBottomSheetState`). **Remove deprecated experimental `WideNavigationRail` APIs** (Iaadd6, b/497891040) — the likely undocumented mechanism for the nav-rail graduation. Remove `shouldUsePrecisionPointerComponentSizing`. `DropdownMenuItem` `supportingText` moved after `trailingIcon`. |
| **1.5.0-alpha21** | 2026-06-03 | `animateWidth` gains **`compressionLimit`** (old overload deprecated `HIDDEN`). `TimePicker` shapes. `SelectableChipColors` params public. `TextFieldDefaults`/`OutlinedTextFieldDefaults` gain `roundedShape` + `tonalColors()`. `TextFieldLabelPosition.Attached` deprecated → `Inside`/`Cutout`. `OutlinedTextFieldDefaults.contentPadding()` deprecated → `contentPaddingWithLabel()`/`contentPaddingWithoutLabel()`. `MenuItems` gains `horizontalArrangement`. **`PullToRefreshDefaults.loadingIndicatorColor`/`loadingIndicatorContainerColor` re-marked experimental.** **Button `contentPaddingFor` re-marked experimental.** |
| **1.5.0-alpha22** | 2026-06-17 | **Graduate Expressive `FloatingToolbar` APIs.** **Promote `ButtonGroup` APIs to stable — removes the deprecated experimental `ButtonGroup` overloads** (but see the conflict in §5). Promote `TopAppBarScrollBehavior` + related. `pinnedScrollBehavior`/`enterAlwaysScrollBehavior` accept `ScrollableState`. `TopAppBarDefaults.snapAnimationSpec` public getter. Shapes in `AnimatedPane`. `MenuAnchorPosition` collapsed to a single class; `MenuAnchorPositionScope` introduced. Fixed `ButtonGroup` compression animation with asymmetric paddings / RTL. |
| **1.5.0-alpha23** | 2026-07-01 | Expressive **`TimePicker`**. Non-interactive variants of standard + **segmented list item**. **Expressive list-item APIs no longer experimental.** **Graduate `TopAppBar`, `MediumFlexibleTopAppBar`, `LargeTopAppBar`, `LargeFlexibleTopAppBar`, `TwoRowsTopAppBar`, `FlexibleBottomAppBar`, `FlexibleContentPadding`, `FlexibleBottomAppBarHeight`, `FlexibleHorizontalArrangement`, `FlexibleFixedHorizontalArrangement`.** Graduate `ExpandedDockedSearchBarWithGap`, `ExpandedFullScreenContainedSearchBar`. **Remove `ComponentOverride` APIs.** `MenuDefaults.Label` → `MenuDefaults.DropdownMenuGroupLabel`. `TopAppBarDefaults.flingAnimationSpec` public getter. **Removed `compileSdk 37` requirement.** |
| **1.5.0-alpha24** | 2026-07-15 | New scroll variant for expressive `TimePicker`. New **`material3-ripple`** library (inset focus rings instead of an opacity layer). **`SearchBarState` + slot-based `SearchBar` promoted to stable; older `expanded`/`onExpandedChange` `SearchBar` deprecated.** **`@ExperimentalMaterial3Api` re-added to `AppBarWithSearch`.** `ScrollIndicatorState` param made non-nullable. `ScrollField` gets expressive number transitions + a11y. |
| **1.5.0-alpha25** | 2026-07-29 | **Renamed `TonalToggleButton` → `FilledTonalToggleButton`**; smooth border stroke animations on `OutlinedToggleButton`; **deprecated `ToggleButtonDefaults.shapes` in favor of `shapesFor`** (Icb433). **Release note reads "Deprecated `SplitButtonLayout` API" (Ic9840) — but no `SplitButton` composable exists and `SplitButtonLayout` is undeprecated at alpha26; the note most plausibly covers the deprecated `SplitButtonDefaults.*Shapes(CornerSize)` helpers (inference).** **`ButtonGroupScope` → `sealed interface`; `Modifier.animateWidth` split into two overloads; `compressionLimit` retyped `PaddingValues` → `Dp`** (I8ef39). **Remove `ComponentOverride` APIs** (I3784b). **Graduate `BasicAlertDialog`** (If157c). Renamed `trailingIcon` → `trailingContent` in the shape/checked/selected `DropdownMenuItem` overloads, binary-compatibly (I2ecbd). New selection + container color properties on `MenuItemColors`, same rename in the new colors constructor (I20b92). **Deprecated 2-parameter `SliderState.Saver` / `RangeSliderState.Saver` in favor of overloads taking `steps`; `SliderState` no longer publicly implements `DraggableState`** (I7c91b). Promote `LinearTrackStopIndicatorSize`, `LinearIndicatorTrackGapSize`, `CircularIndicatorTrackGapSize` to stable (I794d0). Improved `ScrollField` a11y + keyboard nav (If7477). Fixes: `TimePickerTextField` focus switching on error; single-item segmented list item shape (I2ea1c); `TimePickerDialog` KDocs. |
| **1.5.0-alpha26** | 2026-08-12 | Added `ElevatedToggleButtonDefaults`, `FilledTonalToggleButtonDefaults`, `OutlinedToggleButtonDefaults`; **cleaned up non-semantic shape properties in `ToggleButtonDefaults`** (Ia0a85). **Scroll offset variables removed from `SearchBarScrollBehavior` → new `SearchBarScrollState` class** (Ib24e4). **Promoted `BottomAppBar` and associated methods to stable** (I42c61). **`ExposedDropdownMenu` is now an extension function on `ExposedDropdownMenuBoxScope`** — new import may be needed (Ie8a65, b/356452026). A11y contrast fix for the horizontal `NavigationItem` label: `selectedTextColorStartIconPosition` now matches `selectedIconColor` (I85855, b/490910896). `TimePickerState` persists selection mode across restoration + new `initialSelection` param (Iad905). **"Updated the public API surface to align with recent API review feedback" (I71aff, b/532657001) — unenumerated sweep, treat as catch-all risk.** Fixes: `MenuDefaults.itemColors` partial-customization clobbering (Iea847); wide nav rail bottom padding now 0 (I572bb); `ExposedDropdownMenu` crash when screen height < menu height (I6adf1); TalkBack formatted time in scrollable `TimePicker` (Ice981); `Slider` `Label` during mouse drag (I97a6d, b/533483487). |

Supporting evidence that 1.4.0 gated everything: the frozen
`compose/material3/material3/api/1.4.0-beta01.txt` metalava signature file does not surface
`MaterialExpressiveTheme`, `ButtonGroup`, `ToggleButton`, `SplitButtonLayout`, `LoadingIndicator`,
`LinearWavyProgressIndicator`, `FloatingActionButtonMenu`, `HorizontalFloatingToolbar`,
`ShortNavigationBar`, `WideNavigationRail`, `MediumFlexibleTopAppBar`, `MaterialShapes`,
`MotionScheme`, or `expressiveLightColorScheme` in the readable portion. **Partially UNVERIFIED** —
the fetch was truncated; treat as supporting evidence, not proof.

---

## 7. Version traps

Read this list before debugging any Expressive compile error.

### 7.1 `MaterialShapes` and `LoadingIndicator` promotions were REVERTED in alpha19

They were promoted to stable in alpha18, then **un-promoted in alpha19**, and **never re-promoted
through alpha26**. On every version from 1.4.0 to 1.5.0-alpha26 these require
`@OptIn(ExperimentalMaterial3ExpressiveApi::class)`:

- `MaterialShapes` and all 35 shape properties
- `RoundedPolygon.toShape(startAngle)` and `RoundedPolygon.toPath(startAngle)` (both `@Composable`)
- `Morph.toPath(progress, path, startAngle)` (**not** `@Composable`)
- `LoadingIndicator` (both overloads), `ContainedLoadingIndicator` (both overloads),
  `LoadingIndicatorDefaults`

If you use a global opt-in you will never notice. If you use per-file opt-in, these two are the ones
you will forget. And if you are "cleaning up redundant opt-ins" after reading §5, **these are the two
you must not touch.**

### 7.2 `ButtonGroup`'s signature changed incompatibly (alpha22)

The current signature leads with `overflowIndicator`:

```kotlin
@Composable
fun ButtonGroup(
    overflowIndicator: @Composable (ButtonGroupMenuState) -> Unit,
    modifier: Modifier = Modifier,
    @FloatRange(0.0) expandedRatio: Float = ButtonGroupDefaults.ExpandedRatio,
    horizontalArrangement: Arrangement.Horizontal = ButtonGroupDefaults.HorizontalArrangement,
    verticalAlignment: Alignment.Vertical = Alignment.Top,
    content: ButtonGroupScope.() -> Unit,
)
```

The pre-alpha22 form `ButtonGroup(modifier, horizontalArrangement, content)` existed in 1.4.0 and was
**removed** in alpha22. This is the single biggest 1.4.0-vs-alpha divergence and the most likely
source of a "no overload matches" error. Compounding it: **`ButtonGroupScope` is a `sealed interface`
since alpha25** (§4.5) and **`Modifier.animateWidth`'s `compressionLimit` is now a `Dp`** (§4.2).

The `ButtonGroup` composable is **rare in the wild** — three call sites across the four community
apps. Every other "button group" in the corpus is a plain `Row`/`FlowRow` with
`Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween)` and per-child
`ButtonGroupDefaults.connected{Leading,Middle,Trailing}ButtonShapes()`. **That hand-assembled pattern
is signature-stable across every version in this document, including alpha25/alpha26, and sidesteps
all three breaks.** Use the `ButtonGroup` composable only when you actually need its
overflow/compression behavior.

### 7.3 `ComponentOverride` is gone; 7.4 the `SplitButtonLayout` → `SplitButton` rename **does not exist**

`ComponentOverride` was removed across alpha23 and alpha25 (I3784b); `ExperimentalMaterial3ComponentOverrideApi`
and the `LocalXOverride` hooks do not exist on alpha25+, and no migration is documented (§4.4).

**Retraction.** Earlier versions of this file first marked a `SplitButtonLayout` → `SplitButton`
rename **UNVERIFIED**, then wrongly declared it **RESOLVED** on the strength of the alpha25 release
note (Ic9840). Reading the API surface directly reverses that: **there is no `SplitButton`
composable, and `SplitButtonLayout` is not deprecated** — verified in
`compose/material3/material3/api/current.txt` at androidx HEAD `360e8cba`, 2026-08-14. The release
note most plausibly refers to the deprecated `SplitButtonDefaults.*Shapes(CornerSize)` helpers
(inference, not fact). Write `SplitButtonLayout` on every pin (§4.10).

### 7.5 `isAtTop` → `isAtStart` (alpha16)

`TopAppBarDefaults` scroll behaviors renamed `isAtTop` to `isAtStart`. alpha15 had added
`isAtTopState` to `enterAlwaysScrollBehavior`/`pinnedScrollBehavior`; alpha16 renamed the property.
Code written against 1.4.0 or an early alpha will not compile on alpha16+.

### 7.6 SearchBar API churn (alpha16 → alpha26)

The search-bar family moved more than anything else:

- alpha16: `SearchBarDefaults.InputField` **param order changed**
- alpha18: `rememberWithGapSearchBarState` → `rememberSearchBarWithGapState`
- alpha23: `ExpandedDockedSearchBarWithGap`, `ExpandedFullScreenContainedSearchBar` graduated
- alpha24: `SearchBarState` + slot-based `SearchBar` promoted to **stable**; the older
  `expanded`/`onExpandedChange` `SearchBar` **deprecated**; `AppBarWithSearch` **re-gated** with
  `@ExperimentalMaterial3Api`
- alpha26: **scroll offsets removed from `SearchBarScrollBehavior`** → `SearchBarScrollState` (§4.3)

Do not write search-bar code from memory. Check the artifact.

Note `ExpandedFullScreenSearchBar`'s `windowInsets` is a **`@Composable () -> WindowInsets` lambda**
— `windowInsets: @Composable () -> WindowInsets = { SearchBarDefaults.fullScreenWindowInsets }` —
unlike every other material3 component, which takes a plain `WindowInsets`. Passing a bare
`WindowInsets` there is a type error that reads like a nonsense message.

### 7.7 `expressiveDarkColorScheme()` DOES NOT EXIST

Verified absent from `ColorScheme.kt` on `androidx-main` as of 2026-08-01 and 1.5.0-alpha24. Not
re-verified at alpha26, but nothing in the alpha25/alpha26 notes adds it. **Use plain
`darkColorScheme()` for dark.** If you see `expressiveDarkColorScheme()` anywhere — generated code, a
blog post, your own memory — it is wrong.

The entire delta of `expressiveLightColorScheme()` is four roles, and the official sample simply
branches on `isSystemInDarkTheme()` between it and `darkColorScheme()`:

```kotlin
fun expressiveLightColorScheme() =
    lightColorScheme(
        onPrimaryContainer = PaletteTokens.Primary30,
        onSecondaryContainer = PaletteTokens.Secondary30,
        onTertiaryContainer = PaletteTokens.Tertiary30,
        onErrorContainer = PaletteTokens.Error30,
    )
```

### 7.8 `MaterialTheme` is now one CompositionLocal (alpha15)

Code reading `LocalColorScheme` / `LocalTypography` / `LocalShapes` individually must move to
`MaterialTheme.LocalMaterialTheme.current` (or `currentValueOf(MaterialTheme.LocalMaterialTheme)`).
There is **no** `LocalMotionScheme` — motion lives on the single `LocalMaterialTheme`.

### 7.9 Other renames and deprecations to check

Everything from alpha25/alpha26 is in **§4** — this table covers the *older* churn that a codebase
pinned anywhere between 1.4.0 and alpha24 still has to survive.

| Was | Is | Since |
| --- | --- | --- |
| `rememberModalBottomSheetState`, `rememberStandardBottomSheetState` | `rememberBottomSheetState` | alpha20/21 |
| `MenuDefaults.Label` | `MenuDefaults.DropdownMenuGroupLabel` | alpha23 |
| `TextFieldLabelPosition.Attached` | `.Inside` / `.Cutout` | alpha21 |
| `OutlinedTextFieldDefaults.contentPadding()` | `contentPaddingWithLabel()` / `contentPaddingWithoutLabel()` | alpha21 |
| `FilterChip`/`AssistChip` `horizontalSpacing` | `horizontalArrangement` | alpha15, overload removed alpha16 |
| `SmallButtonContentPadding` | removed | alpha19 |
| `ComponentOverride` APIs | removed, no replacement | alpha23/25 |
| Bottom app bar | Docked toolbar (design-level deprecation) | — |

### 7.10 Documentation is stale — prefer androidx samples over guide pages

Re-checked 2026-08-14, all still negative. Under `developer.android.com/develop/ui/compose/`:

- `components/app-bars` — no `AppBarRow`, `AppBarColumn`, `MediumFlexibleTopAppBar`,
  `LargeFlexibleTopAppBar`. `components/navigation-rail` — no `WideNavigationRail`,
  `ModalWideNavigationRail`, `WideNavigationRailItem`, `ShortNavigationBar`.
- `components/search-bar` — no `SearchBarState`, `rememberSearchBarState`, `TopSearchBar`,
  `ExpandedFullScreenSearchBar`; both examples use the deprecated `var expanded by rememberSaveable`
  form. `components/carousel` — current for multi-browse/uncontained, but no
  `HorizontalCenteredHeroCarousel` and never documents `maskClip`/`maskBorder`/`CarouselItemScope`.
- `designsystems/material3` — Expressive in prose only, no `MaterialExpressiveTheme`/`MotionScheme`.
- Still **no** guide pages at all for `button-groups`, `split-button`, `toggle-button`,
  `loading-indicator`, `fab-menu`, `toolbars` — those URLs 404.
- The docs' `TimePickerDialog` snippet is **hand-rolled from `AlertDialog`** and outdated —
  `androidx.compose.material3.TimePickerDialog` is a real API, with `TimePickerDialogDefaults`,
  `TimePickerDisplayMode`, and `RichTimePickerDialog`.

**Authority order for Expressive: androidx `compose/material3/material3/samples/` source > release
notes > the Kotlin API reference > developer.android.com guide pages.** Also: **composables.com
version-pinned URLs are not version-accurate** — a `.../material3/1.4.0/components/ButtonGroup` URL
returns the *current* signature, not 1.4.0's.

### 7.11 Accessibility overrides are baked in

Floating toolbars stay expanded and disable `scrollBehavior`, and `FlexibleBottomAppBar` disables
`scrollBehavior`, whenever an accessibility service is active. This is intentional. Do not fight it
with manual state.

### 7.12 Feature flags in flight

`ComposeMaterial3Flags`-style toggles introduced along the way:
`isExpressiveListItemHeightBasedOnTextLinesFixEnabled`,
`isAnchoredDraggableComponentsAnchorRecoveryEnabled`,
`isBottomSheetPartiallyExpandedDeterministicEnabled`, `isPrecisionPointerComponentSizingEnabled`,
`ComposeFoundationFlags#isBasicTextFieldMinSizeOptimizationEnabled`. If a component behaves oddly on
a specific alpha, check whether a flag governs it.

---

## 8. What the reference apps pin

Evidence of what actually works in practice, as of 2026-08-14. The first three rows are **Google's
own repositories** and carry the most authority.

| App | Source | material3 | Compose BOM | AGP / Kotlin | adaptive | Opt-in strategy |
| --- | --- | --- | --- | --- | --- | --- |
| **JetPacker** | `android/ai-samples` | **`1.5.0-alpha16`** (inline pin) | `2026.03.00` | **9.2.1 / 2.3.10** | not declared at all | **per-callsite `@OptIn`** + 2 `@file:OptIn`; **no Gradle opt-in anywhere** |
| **androidify** | `android/androidify` | `1.5.0-alpha20` | `2026.05.01` | — | — | 34 files with Expressive opt-in |
| **Jetcaster** | `android/compose-samples` | `1.5.0-alpha22` (pin over BOM) | `2026.08.00` | — | — | 5 files, `@OptIn` on the theme fn |
| **vivi-music** | community | `1.5.0-alpha23` | none — pins `compose = "1.11.4"` directly | 9.1.1 / 2.3.10 | `1.3.0-alpha09` | global `freeCompilerArgs` + 72 explicit `@OptIn` |
| **Med** | community | `1.5.0-alpha21` | `2026.05.01` | 9.2.1 / 2.4.0 | — (uses `material3-adaptive-navigation-suite:1.5.0-alpha21`) | 38 × `@file:OptIn` |
| **LastChat** | community | `1.5.0-alpha08` (inline pin) | `2025.11.00` | — / — | `1.2.0` | global `compilerOptions.optIn.add` |
| **Tomato** | community | BOM-managed, **no explicit pin** | `compose-bom-alpha:2026.03.00` | 9.1.0 / 2.3.20 | `1.2.0` | 85 × per-callsite `@OptIn` |

**The headline finding: Google's own samples pin material3 explicitly over the BOM. That is the
sanctioned pattern, not a hack.** JetPacker, androidify, and Jetcaster all take a stable Compose BOM
and then override just `material3` with an alpha. Nobody in this corpus — Google or community — ships
Expressive on material3 1.4.0 stable.

JetPacker (Google, `android/ai-samples`), verbatim:

```toml
[versions]
agp = "9.2.1"
kotlin = "2.3.10"
composeBom = "2026.03.00"
compileSdk = "37"
compileSdkMinor = "0"
minSdk = "26"
targetSdk = "36"
nav3Core = "1.1.3"

[libraries]
androidx-compose-bom = { group = "androidx.compose", name = "compose-bom", version.ref = "composeBom" }
androidx-compose-material3 = { group = "androidx.compose.material3", name = "material3", version = "1.5.0-alpha16" }
androidx-compose-material-icons-extended = { group = "androidx.compose.material", name = "material-icons-extended" }
androidx-navigation3-runtime = { module = "androidx.navigation3:navigation3-runtime", version.ref = "nav3Core" }
androidx-navigation3-ui = { module = "androidx.navigation3:navigation3-ui", version.ref = "nav3Core" }

[plugins]
android-application = { id = "com.android.application", version.ref = "agp" }
android-library = { id = "com.android.library", version.ref = "agp" }
kotlin-compose = { id = "org.jetbrains.kotlin.plugin.compose", version.ref = "kotlin" }
```

Every Compose module then does **both**:

```kotlin
implementation(platform(libs.androidx.compose.bom))   // BOM governs ui, foundation, tooling, icons
implementation(libs.androidx.compose.material3)        // pinned to 1.5.0-alpha16 by the catalog
```

Things JetPacker establishes that are worth copying:

- **No `org.jetbrains.kotlin.android` plugin anywhere** — AGP 9.2.1 has built-in Kotlin support.
- **No buildSrc, no convention plugins.** Cross-cutting config via `subprojects { }` in the root
  build file, including `freeCompilerArgs.add("-Xannotation-default-target=param-property")`.
- **`androidx.graphics:graphics-shapes` is never declared** — it arrives transitively via material3,
  and `import androidx.graphics.shapes.Morph` just works.
- **`material3.adaptive:*` is not declared at all**; adaptive behavior comes from
  `derivedMediaQuery` / `UiMediaScope`, not `WindowSizeClass`.
- Opt-in census across all `.kt`: `ExperimentalMaterial3Api` **54**, `ExperimentalMaterial3ExpressiveApi`
  **9**. That 6:1 ratio is the practical shape of the post-graduation surface.

vivi-music — no BOM at all, plus the belt-and-braces global opt-in (verbatim):

```toml
[versions]
androidGradlePlugin = "9.1.1"; kotlin = "2.3.10"; compose = "1.11.4"
material3 = "1.5.0-alpha23"; adaptive = "1.3.0-alpha09"; materialKolor = "4.1.1"

[libraries]
material3 = { module = "androidx.compose.material3:material3", version.ref = "material3" }
material3-adaptive-navigation-suite = { module = "androidx.compose.material3:material3-adaptive-navigation-suite", version.ref = "material3" }
androidx-adaptive-layout = { module = "androidx.compose.material3.adaptive:adaptive-layout", version.ref = "adaptive" }
```

```kotlin
    kotlin {
        jvmToolchain(21)
        compilerOptions {
            freeCompilerArgs.add("-Xannotation-default-target=param-property")
            freeCompilerArgs.add("-opt-in=androidx.compose.material3.ExperimentalMaterial3ExpressiveApi")
            jvmTarget.set(JvmTarget.JVM_21)
        }
    }
```

Med — the only app that declares `graphics-shapes` explicitly; also carries a Wear module on the
separate `androidx.wear.compose:compose-material3` train (`composeMaterial3 = "1.6.2"`), which is a
**different artifact with a different version line** and must not be confused with the main one:

```toml
[versions]
agp = "9.2.1"; kotlin = "2.4.0"; composeBom = "2026.05.01"
material3 = "1.5.0-alpha21"; graphicsShapes = "1.1.0"
material3AdaptiveNavigationSuite = "1.5.0-alpha21"; material3WindowSizeClass = "1.5.0-alpha21"

[libraries]
androidx-compose-material3 = { group = "androidx.compose.material3", name = "material3", version.ref = "material3" }
androidx-graphics-shapes = { group = "androidx.graphics", name = "graphics-shapes", version.ref = "graphicsShapes" }
```

LastChat — the inline-pin idiom, no `[versions]` entry for material3. Tomato — the alpha-BOM idiom,
no version on the material3 entry at all:

```toml
# LastChat
androidx-material3 = { group = "androidx.compose.material3", name = "material3", version = "1.5.0-alpha08" }

# Tomato — compose-bom-alpha at 2026.03.00, and material3 with NO version at all
androidx-compose-bom = { group = "androidx.compose", name = "compose-bom-alpha", version.ref = "composeBom" }
androidx-material3 = { group = "androidx.compose.material3", name = "material3" }
androidx-compose-adaptive-navigation3 = { group = "androidx.compose.material3.adaptive", name = "adaptive-navigation3" }
```

Takeaways:

- **Nobody ships Expressive on material3 1.4.0 stable.** All seven are on the 1.5.0 alpha line,
  spanning alpha08 → alpha23. Alphas are the norm for Expressive work, including at Google.
- **Nobody is on alpha25 or alpha26 yet.** The newest pin in the corpus is alpha23 (vivi-music). That
  means the §4 breaking changes are ahead of every one of these apps — including Google's. If you pin
  alpha26 you are ahead of the entire reference corpus, and the compiler is your only reviewer.
- Five of seven use a **stable BOM plus an explicit material3 alpha pin**. Only Tomato (a Compose
  Multiplatform project) uses `compose-bom-alpha`; only vivi-music skips the BOM entirely.
- Only Med declares `graphics-shapes` explicitly. The others reach `MaterialShapes` through
  material3's transitive dependency — which works, but declaring it directly is documented practice
  and is required if you name `RoundedPolygon` / `Morph` types.
- All of them use variable-font typography (Google Sans Flex / `FontVariation`), which is not
  version-gated at all.
- **Non-Expressive Google repos exist and are a trap for evidence-gathering:** `android/snippets`
  (which renders developer.android.com), `nowinandroid`, and `platform-samples` have **zero** M3
  Expressive usage. `snippets`' `ButtonGroup`/`ToggleButton` hits are `androidx.xr.glimmer`, and its
  `MotionScheme` is the Wear one — easy false positives. In `compose-samples`, only **Jetcaster** has
  adopted Expressive; Jetsnack, Reply, JetNews, Jetchat, and JetLagged have not.

---

## 9. Hello Expressive — minimal complete skeleton

Three files, valid at `1.5.0-alpha26`. Everything below is assembled from verified API signatures and
patterns lifted from the reference apps; no signature is invented.

### 9.1 `ui/theme/Theme.kt`

Unchanged by the alpha25/alpha26 breaks — `MaterialExpressiveTheme`, `expressiveLightColorScheme`,
and `MotionScheme` have been stable and opt-in-free since alpha18/alpha15.

```kotlin
package com.example.expressive.ui.theme

// androidx.compose.material3.{ColorScheme, MaterialExpressiveTheme, MotionScheme,
//     darkColorScheme, dynamicDarkColorScheme, dynamicLightColorScheme, expressiveLightColorScheme}
// androidx.compose.foundation.isSystemInDarkTheme, androidx.compose.runtime.Composable,
// androidx.compose.ui.platform.LocalContext, android.os.Build

@Composable
fun ExpressiveTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val useDynamic = dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

    val colorScheme: ColorScheme = when {
        useDynamic && darkTheme -> dynamicDarkColorScheme(context)
        useDynamic -> dynamicLightColorScheme(context)
        // NOTE: there is no expressiveDarkColorScheme(). Dark uses darkColorScheme().
        darkTheme -> darkColorScheme()
        else -> expressiveLightColorScheme()
    }

    MaterialExpressiveTheme(
        colorScheme = colorScheme,
        motionScheme = MotionScheme.expressive(),
        content = content,
    )
}
```

Notes:

- `MaterialExpressiveTheme`'s parameters are **nullable** and `null` means "use the Expressive
  default", not "inherit from the ambient theme" — the opposite of `MaterialTheme`. Omitting
  `shapes` and `typography` is correct and gets you the Expressive defaults.
  ```kotlin
  @Composable
  fun MaterialExpressiveTheme(
      colorScheme: ColorScheme? = null,
      motionScheme: MotionScheme? = null,
      shapes: Shapes? = null,
      typography: Typography? = null,
      content: @Composable () -> Unit,
  )
  ```
- **Pass `motionScheme = MotionScheme.expressive()` explicitly.** If you use bare `MaterialTheme`
  instead, every component silently falls back to `MotionScheme.standard()` springs and the app
  loses its Expressive feel with no compile error. This one line is what vivi-music, Tomato,
  Jetcaster, and Med's FAB subtree all rely on.
- No opt-in is needed here. androidx's own `ThemeSamples.kt` carries **zero** annotations of either
  kind. Jetcaster still writes `@OptIn(ExperimentalMaterial3ExpressiveApi::class)` on its theme
  function — harmless, redundant.
- Incremental adoption: nest `MaterialExpressiveTheme` inside a plain `MaterialTheme` for one
  subtree, passing `colorScheme`/`typography`/`shapes` straight through from `MaterialTheme` and
  overriding only `motionScheme = MotionScheme.expressive()`. Med does exactly this to get expressive
  FAB physics without converting the app.

### 9.2 `MainActivity.kt`

Ordinary `ComponentActivity`; the only Expressive-relevant detail is calling `enableEdgeToEdge()`
before `setContent { ExpressiveTheme { HomeScreen() } }`. Expressive components (floating toolbars,
FAB menus, flexible app bars) assume an edge-to-edge window and will look wrong inset without it.

### 9.3 `HomeScreen.kt` — one screen with real Expressive components

Connected `ToggleButton` group (the single most-used Expressive pattern in the wild) plus a
`LoadingIndicator`. The `ToggleButton` shape assignment is verbatim from vivi-music and is
**unaffected by the alpha25 `ToggleButtonDefaults.shapes` break** — `ButtonGroupDefaults`'
`connected*ButtonShapes()` helpers keep their old signatures and still return `ToggleButtonShapes`.

```kotlin
package com.example.expressive

// Imports: the standard androidx.compose.{foundation.layout, runtime, ui} set, plus
//   androidx.compose.material3.{ButtonGroupDefaults, ExperimentalMaterial3ExpressiveApi,
//       LoadingIndicator, MaterialTheme, Scaffold, Text, ToggleButton}
//   androidx.compose.ui.semantics.{Role, role, semantics}
// Never androidx.compose.material.* — that is M2.

// Still required at 1.5.0-alpha26 for LoadingIndicator (its alpha18 promotion was reverted in
// alpha19 and never restored) and, defensively, for ButtonGroupDefaults — the alpha22 note claims
// ButtonGroup graduated but the source census still shows gating. ToggleButton itself no longer
// needs it.
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun HomeScreen() {
    var selected by remember { mutableIntStateOf(0) }
    val labels = listOf("All", "Recent", "Saved")

    Scaffold { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            Text("Library", style = MaterialTheme.typography.headlineLargeEmphasized)

            // Connected button group: hand-assembled Row, not the ButtonGroup composable.
            // This is what the reference apps overwhelmingly do, and it is the ONLY form that is
            // signature-stable across 1.4.0 → alpha26 (see §7.2).
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween),
            ) {
                labels.forEachIndexed { index, label ->
                    ToggleButton(
                        checked = selected == index,
                        onCheckedChange = { selected = index },
                        modifier = Modifier
                            .weight(1f)
                            .semantics { role = Role.RadioButton },
                        shapes = when (index) {
                            0 -> ButtonGroupDefaults.connectedLeadingButtonShapes()
                            labels.lastIndex -> ButtonGroupDefaults.connectedTrailingButtonShapes()
                            else -> ButtonGroupDefaults.connectedMiddleButtonShapes()
                        },
                    ) {
                        Text(label)
                    }
                }
            }

            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                LoadingIndicator()
            }
        }
    }
}
```

What this demonstrates and why each piece is written that way:

| Piece | Why |
| --- | --- |
| `MotionScheme.expressive()` in the theme | Without it, all 21 physics-driven components use standard springs |
| Hand-built connected group | The `ButtonGroup` composable's signature broke in alpha22 and its scope became `sealed` in alpha25; this pattern survived both, and it is what the reference apps use |
| `ButtonGroupDefaults.ConnectedSpaceBetween` | The connected-group spacing token (2dp per Material spec) |
| `connected{Leading,Middle,Trailing}ButtonShapes()` | Rounded outer corners, small inner corners; also supply the pressed and checked morph shapes. **Signature unchanged by the alpha25 shapes/`shapesFor` break** |
| `semantics { role = Role.RadioButton }` | A connected single-select group is a radio group to a screen reader. Use `Role.Button` for a group of independent actions |
| `@OptIn(ExperimentalMaterial3ExpressiveApi::class)` | Required for `LoadingIndicator` at **every** version through alpha26; kept defensively for `ButtonGroupDefaults`. `ToggleButton` has not needed it since alpha19 |
| `headlineLargeEmphasized` | The Expressive emphasized type role; use these instead of ad-hoc `FontWeight.Bold` |

If you need to **customize** toggle-button shapes rather than use the connected helpers, remember
the alpha25 change (§4.1):

```kotlin
// Default shape set, derived from the button height:
shapes = ToggleButtonDefaults.shapesFor(ButtonDefaults.MinHeight)

// Custom shape set — use the CONSTRUCTOR, not shapesFor:
shapes = ToggleButtonShapes(shape = a, pressedShape = b, checkedShape = c)
```

### 9.4 Verification checklist

Before declaring setup done:

1. `./gradlew :app:compileDebugKotlin` actually runs. Do not assume. On alpha25/alpha26 this is the
   only reliable check — §4 lists nine ways alpha24-era source fails to compile.
2. Imports are `androidx.compose.material3.*`, never `androidx.compose.material.*`.
3. The theme is `MaterialExpressiveTheme` with an explicit `motionScheme`.
4. `graphics-shapes` is on the classpath if anything touches `MaterialShapes`, `RoundedPolygon`, or
   `Morph` (transitive is enough to compile; declare it if you name the types).
5. Dark mode renders — remember `expressiveDarkColorScheme()` does not exist.
6. If the project pins 1.4.0, every Expressive call site is opted in (the blanket rule **is** correct
   there).
7. If the project pins an alpha, check §5 and §6 before removing any `@OptIn`, and **never** remove it
   from `LoadingIndicator`, `MaterialShapes`, menu, PullToRefresh-color, ToggleButton-size-variant, or
   `ButtonGroup` call sites.
8. If the project was on alpha24 or earlier, grep for the P0 breaks before building:
   `ToggleButtonDefaults.shapes(`, `animateWidth(`, `.scrollOffset`, `ComponentOverride`,
   `trailingIcon =`, `TonalToggleButton`, `SplitButtonDefaults.leadingButtonShapes(`,
   `SplitButtonDefaults.trailingButtonShapes(`. (`SplitButtonLayout` itself is **not** a break — it
   is the current, undeprecated name; do not "migrate" it to `SplitButton`, which does not exist.)
9. If you are ahead of the reference corpus (alpha25+), treat the compiler as authoritative over this
   document — alpha26's I71aff API-review sweep is unenumerated.

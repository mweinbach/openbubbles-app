# Starter Project — a complete, running M3 Expressive app

Create these files and you have an app that builds, runs edge-to-edge, themes with
`MaterialExpressiveTheme` + `MotionScheme.expressive()`, adapts its navigation container across all
five width buckets, and has a screenshot-test harness. No elisions in the build files or the theme —
what is printed is the whole file.

**Provenance `[API-alpha26]`.** Every `material3` / `material3-adaptive` composable name and
`*Defaults` member name in this file was machine-checked against
`compose/material3/material3/api/current.txt` and `compose/material3/adaptive/*/api/current.txt`
at the alpha26 / 1.3.0 SHA. Non-material3 APIs (foundation, ui, animation, navigation3,
third-party) were **not** checked; anything that could not be confirmed is flagged `[UNVERIFIED]`
inline at its use site.

**Toolchain:** AGP 9.2.1 / Kotlin 2.3.10 / compileSdk 36 / minSdk 26 / JDK 21. Pins are current as of
**2026-08-17** and will go stale — check
<https://developer.android.com/jetpack/androidx/releases/compose-material3> before committing them.

**Provenance:** the version numbers, the AGP-9 build conventions and the screenshot-test setup come
from `${CLAUDE_PLUGIN_ROOT}/skills/m3-expressive/references/setup-and-versions.md` and
`${CLAUDE_PLUGIN_ROOT}/skills/m3-expressive-review/references/testing-expressive-ui.md`, which trace
them to Google's own JetPacker (`android/ai-samples`) and to the androidx release notes. The Kotlin
files are **[COMPOSED]** from verified signatures — no signature here is invented.

## File tree

```
expressive-starter/
├── settings.gradle.kts
├── build.gradle.kts
├── gradle.properties
├── gradle/
│   └── libs.versions.toml
└── app/
    ├── build.gradle.kts
    └── src/
        ├── main/
        │   ├── AndroidManifest.xml
        │   ├── res/
        │   │   ├── values/strings.xml
        │   │   └── font/            # optional — see Type.kt
        │   └── kotlin/com/example/expressive/
        │       ├── MainActivity.kt
        │       ├── AppShell.kt
        │       ├── navigation/
        │       │   ├── Routes.kt
        │       │   └── AppNavHost.kt
        │       ├── feature/home/
        │       │   ├── HomeRoute.kt
        │       │   ├── HomeScreen.kt
        │       │   └── HomeViewModel.kt
        │       └── ui/theme/
        │           ├── Color.kt
        │           ├── Shape.kt
        │           ├── Type.kt
        │           └── Theme.kt
        └── screenshotTest/kotlin/com/example/expressive/feature/home/
            └── HomeScreenshotTest.kt
```

## The navigation choice, stated up front

This starter uses **Navigation 2** (`androidx.navigation:navigation-compose`, type-safe routes).

**Why.** It is the stable line (2.9.8), every Compose developer already knows `NavHost` /
`composable<T>`, it composes cleanly with `NavigationSuiteScaffold`, its `saveState`/`restoreState`
semantics are the documented fix for "state lost on resize", and predictive back works with no extra
wiring. For a template, "boring and correct" wins.

**Navigation 3** (`androidx.navigation3:navigation3-*` 1.1.6, plus
`androidx.compose.material3.adaptive:adaptive-navigation3` 1.3.0) is the better answer for one
specific thing: **list-detail where one back stack should drive both the panes and the single-pane
stack.** `ListDetailSceneStrategy` turns a contiguous suffix of the back stack into panes, so there
is no separate scaffold navigator and no duplicated selection state. If your app's core surface is
list-detail, start on Nav3 instead — the recipe is in
`${CLAUDE_PLUGIN_ROOT}/skills/m3-adaptive/references/adaptive-recipes.md` ("Recipe: list-detail on
Navigation 3") and the full API in `navigation-suite.md` §13. Nav3 also needs its own predictive-back
wiring (`predictivePopTransitionSpec`), which Nav2 gives you for free.

Do not mix them in one back stack.

---

# 1. `gradle/libs.versions.toml`

```toml
[versions]
# --- Toolchain ---------------------------------------------------------------
agp = "9.2.1"
kotlin = "2.3.10"
composeScreenshot = "0.0.1-alpha15"

# --- SDK ---------------------------------------------------------------------
compileSdk = "36"
minSdk = "26"           # variable-font `variationSettings` requires API 26 (see Type.kt)
targetSdk = "36"

# --- AndroidX core -----------------------------------------------------------
coreKtx = "1.18.0"
activityCompose = "1.13.0"
lifecycle = "2.10.0"
navigation = "2.9.8"

# --- Compose -----------------------------------------------------------------
# The stable BOM governs ui / foundation / runtime / animation / tooling.
composeBom = "2026.08.00"

# --- Material 3 Expressive ---------------------------------------------------
# Pinned EXPLICITLY. The stable BOM never ships alphas, and the entire Expressive
# surface only exists un-gated on the 1.5.0 alpha line. Google's own samples
# (JetPacker, androidify, Jetcaster) do exactly this: stable BOM + material3 pin.
material3 = "1.5.0-alpha26"
material3AdaptiveNavigationSuite = "1.5.0-alpha26"   # material3 GROUP — tracks material3, not adaptive

# A DIFFERENT version train. androidx.compose.material3.adaptive:* is stable at 1.3.0.
# Pinning material3-adaptive-navigation-suite to 1.3.0 is the classic mistake here.
adaptive = "1.3.0"

# Required if you name RoundedPolygon / Morph types directly. Arrives transitively
# through material3 either way; declaring it protects you from a transitive bump.
graphicsShapes = "1.1.0"

[libraries]
androidx-core-ktx                = { module = "androidx.core:core-ktx", version.ref = "coreKtx" }
androidx-activity-compose        = { module = "androidx.activity:activity-compose", version.ref = "activityCompose" }
androidx-lifecycle-runtime-ktx   = { module = "androidx.lifecycle:lifecycle-runtime-ktx", version.ref = "lifecycle" }
androidx-lifecycle-runtime-compose = { module = "androidx.lifecycle:lifecycle-runtime-compose", version.ref = "lifecycle" }
androidx-lifecycle-viewmodel-compose = { module = "androidx.lifecycle:lifecycle-viewmodel-compose", version.ref = "lifecycle" }
androidx-navigation-compose      = { module = "androidx.navigation:navigation-compose", version.ref = "navigation" }

# Platform BOM — no versions on the entries it governs.
androidx-compose-bom             = { module = "androidx.compose:compose-bom", version.ref = "composeBom" }
androidx-compose-ui              = { module = "androidx.compose.ui:ui" }
androidx-compose-ui-graphics     = { module = "androidx.compose.ui:ui-graphics" }
androidx-compose-ui-tooling      = { module = "androidx.compose.ui:ui-tooling" }
androidx-compose-ui-tooling-preview = { module = "androidx.compose.ui:ui-tooling-preview" }
androidx-compose-foundation      = { module = "androidx.compose.foundation:foundation" }
androidx-compose-animation       = { module = "androidx.compose.animation:animation" }
androidx-compose-material-icons-extended = { module = "androidx.compose.material:material-icons-extended" }

# version.ref present ON PURPOSE — this overrides the BOM's managed version.
androidx-material3               = { module = "androidx.compose.material3:material3", version.ref = "material3" }
androidx-material3-adaptive-navigation-suite = { module = "androidx.compose.material3:material3-adaptive-navigation-suite", version.ref = "material3AdaptiveNavigationSuite" }

androidx-adaptive                = { module = "androidx.compose.material3.adaptive:adaptive", version.ref = "adaptive" }
androidx-adaptive-layout         = { module = "androidx.compose.material3.adaptive:adaptive-layout", version.ref = "adaptive" }
androidx-adaptive-navigation     = { module = "androidx.compose.material3.adaptive:adaptive-navigation", version.ref = "adaptive" }

androidx-graphics-shapes         = { module = "androidx.graphics:graphics-shapes", version.ref = "graphicsShapes" }

# Screenshot testing
screenshot-validation-api        = { module = "com.android.tools.screenshot:screenshot-validation-api", version.ref = "composeScreenshot" }

[plugins]
android-application        = { id = "com.android.application", version.ref = "agp" }
android-library            = { id = "com.android.library", version.ref = "agp" }
kotlin-compose             = { id = "org.jetbrains.kotlin.plugin.compose", version.ref = "kotlin" }
kotlin-serialization       = { id = "org.jetbrains.kotlin.plugin.serialization", version.ref = "kotlin" }
android-compose-screenshot = { id = "com.android.compose.screenshot", version.ref = "composeScreenshot" }
```

**Two things that look wrong and are not:**

1. There is **no `org.jetbrains.kotlin.android` plugin.** AGP 9.x has built-in Kotlin support.
   JetPacker (AGP 9.2.1) applies it nowhere. On AGP 8.x you must add it back.
2. `material3-adaptive-navigation-suite` is `1.5.0-alpha26` while `adaptive` is `1.3.0`. Two groups,
   two version trains. `androidx.compose.material3:*` tracks material3;
   `androidx.compose.material3.adaptive:*` is its own line. Mixing them produces "cannot resolve"
   errors that look like typos.

**Deliberately omitted:** `androidx.compose.material3:material3-window-size-class`. It is effectively
legacy — the type you actually want is `androidx.window.core.layout.WindowSizeClass`, which arrives
via the adaptive artifacts. Add the legacy artifact only if you are maintaining old code that calls
`calculateWindowSizeClass(Activity)`.

---

# 2. `settings.gradle.kts`

```kotlin
pluginManagement {
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "expressive-starter"
include(":app")
```

---

# 3. `gradle.properties`

```properties
org.gradle.jvmargs=-Xmx4096m -Dfile.encoding=UTF-8
org.gradle.parallel=true
org.gradle.caching=true
org.gradle.configuration-cache=true

android.useAndroidX=true
android.nonTransitiveRClass=true

kotlin.code.style=official

# Required by the Compose screenshot-testing plugin. Set BOTH here and per module via
# experimentalProperties in app/build.gradle.kts — at AGP 9.2.1 / plugin alpha15 both appear
# to be needed.
android.experimental.enableScreenshotTest=true
```

---

# 4. Root `build.gradle.kts`

```kotlin
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.android.compose.screenshot) apply false
}

subprojects {
    tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>().configureEach {
        compilerOptions {
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_21)

            // The Kotlin 2.2+ annotation-use-site-default migration flag. Required in practice on
            // Kotlin 2.3 + AGP 9 — without it, annotations on constructor properties land on the
            // wrong target and DI / serialization break in confusing ways. Both JetPacker (Google)
            // and vivi-music set it.
            freeCompilerArgs.add("-Xannotation-default-target=param-property")
        }
    }
}
```

No `buildSrc`, no `build-logic`, no convention plugins. That is JetPacker's structure and it is the
right default for a one-module app: cross-cutting config lives in `subprojects { }`.

---

# 5. `app/build.gradle.kts`

```kotlin
plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.android.compose.screenshot)
    // On AGP 8.x also: alias(libs.plugins.kotlin.android)
}

android {
    namespace = "com.example.expressive"
    compileSdk = libs.versions.compileSdk.get().toInt()

    defaultConfig {
        applicationId = "com.example.expressive"
        minSdk = libs.versions.minSdk.get().toInt()
        targetSdk = libs.versions.targetSdk.get().toInt()
        versionCode = 1
        versionName = "0.1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    buildFeatures {
        compose = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }

    // Required by the screenshot-testing plugin, in addition to the gradle.properties flag.
    experimentalProperties["android.experimental.enableScreenshotTest"] = true

    packaging {
        resources { excludes += "/META-INF/{AL2.0,LGPL2.1}" }
    }
}

kotlin {
    jvmToolchain(21)
    compilerOptions {
        // GLOBAL EXPRESSIVE OPT-IN.
        //
        // At alpha26 this is INSURANCE, not a prerequisite. Most of the Expressive surface has
        // graduated. What is still gated: LoadingIndicator / ContainedLoadingIndicator,
        // MaterialShapes + toShape()/toPath(), the menu APIs, PullToRefreshDefaults' loading-
        // indicator colours, the ToggleButton size variants, and (contested) ButtonGroup — plus
        // anything the alpha26 API-review sweep (I71aff) re-gated without a release note.
        //
        // Cost: you lose the compiler's map of which files depend on experimental API, so on an
        // alpha bump you find out what broke by reading errors instead of reading a diff. If you
        // want that map, delete this block and annotate per file with @file:OptIn instead —
        // that is what Google's JetPacker does.
        optIn.addAll(
            "androidx.compose.material3.ExperimentalMaterial3Api",
            "androidx.compose.material3.ExperimentalMaterial3ExpressiveApi",
            "androidx.compose.material3.adaptive.ExperimentalMaterial3AdaptiveApi",
        )
    }
}

// Top-level block, sibling to android { } — NOT nested inside it.
screenshotTests {
    imageDifferenceThreshold = 0.05f
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.navigation.compose)

    // The platform line must come FIRST; the explicit material3 pin below is applied as a direct
    // constraint that wins over the BOM's managed version.
    implementation(platform(libs.androidx.compose.bom))

    implementation(libs.androidx.material3)
    implementation(libs.androidx.material3.adaptive.navigation.suite)
    implementation(libs.androidx.adaptive)
    implementation(libs.androidx.adaptive.layout)
    implementation(libs.androidx.adaptive.navigation)
    implementation(libs.androidx.graphics.shapes)

    // BOM-governed — no versions.
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.compose.animation)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material.icons.extended)

    debugImplementation(libs.androidx.compose.ui.tooling)

    // Both are REQUIRED: ui-tooling renders the @Preview, screenshot-validation-api supplies
    // the @PreviewTest annotation.
    screenshotTestImplementation(libs.androidx.compose.ui.tooling)
    screenshotTestImplementation(libs.screenshot.validation.api)
}
```

> If `@Serializable` does not resolve on the route classes, add
> `implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:<current>")`. Type-safe
> Navigation 2 routes normally reach `kotlinx-serialization-core` transitively through
> `navigation-compose`, so the serialization *plugin* alone is usually enough. **[UNVERIFIED]** at
> navigation 2.9.8 specifically — if the plugin alone works, do not add the dependency.

---

# 6. `app/src/main/AndroidManifest.xml`

```xml
<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android">

    <application
        android:name=".ExpressiveApplication"
        android:allowBackup="true"
        android:icon="@mipmap/ic_launcher"
        android:label="@string/app_name"
        android:roundIcon="@mipmap/ic_launcher_round"
        android:supportsRtl="true"
        android:theme="@style/Theme.Expressive"
        android:enableOnBackInvokedCallback="true">

        <activity
            android:name=".MainActivity"
            android:exported="true"
            android:resizeableActivity="true"
            android:theme="@style/Theme.Expressive">
            <intent-filter>
                <action android:name="android.intent.action.MAIN" />
                <category android:name="android.intent.category.LAUNCHER" />
            </intent-filter>
        </activity>
    </application>
</manifest>
```

**The resizability / orientation stance, and why it is the only defensible one:**

| Attribute | Stance |
| --- | --- |
| `android:resizeableActivity="true"` | **Set it explicitly.** This is the documented required migration for large screens. |
| `android:screenOrientation` | **Absent.** On API 36 it is ignored on any display ≥ sw600dp, and it is ignored under desktop windowing *even with the compat opt-out property set*. All it achieves is guaranteeing your landscape layout is untested when the system forces it anyway. |
| `android:minAspectRatio` / `maxAspectRatio` | **Absent.** Ignored at API 36 on large screens, and users can override aspect ratio in Settings regardless. |
| `android:configChanges` | **Absent.** Compose handles configuration changes; adding `configChanges` here suppresses resource re-resolution you want. |
| `android:enableOnBackInvokedCallback="true"` | Needed for predictive back on Android 15 and lower. Android 16+ enables it by default; keeping the line costs nothing. |
| `PROPERTY_COMPAT_ALLOW_RESTRICTED_RESIZABILITY` | **Not used.** It is a migration-deadline marker, not a solution: the framework removes the opt-out at API 37. |

`ExpressiveApplication` is a two-line `Application` subclass, or delete `android:name` if you have no
process-start work.

`res/values/strings.xml`:

```xml
<resources>
    <string name="app_name">Expressive</string>
    <string name="nav_home">Home</string>
    <string name="nav_library">Library</string>
    <string name="nav_settings">Settings</string>
</resources>
```

`res/values/themes.xml` — a plain splash-safe theme; the real theming is in Compose:

```xml
<resources>
    <style name="Theme.Expressive" parent="android:Theme.Material.Light.NoActionBar">
        <item name="android:windowBackground">@android:color/transparent</item>
    </style>
</resources>
```

---

# 7. `ui/theme/Color.kt`

```kotlin
package com.example.expressive.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.expressiveLightColorScheme
import androidx.compose.ui.graphics.Color

/**
 * The brand seed. Used only when dynamic colour is unavailable or switched off.
 * Replace this before you ship anything — see "First five things to change".
 */
val BrandSeed = Color(0xFF6750A4)

private val BrandPrimaryLight = Color(0xFF65558F)
private val BrandOnPrimaryLight = Color(0xFFFFFFFF)
private val BrandPrimaryContainerLight = Color(0xFFE9DDFF)
private val BrandOnPrimaryContainerLight = Color(0xFF4A3D75)

private val BrandPrimaryDark = Color(0xFFCFBDFE)
private val BrandOnPrimaryDark = Color(0xFF36275D)
private val BrandPrimaryContainerDark = Color(0xFF4D3D75)
private val BrandOnPrimaryContainerDark = Color(0xFFE9DDFF)

/**
 * Light fallback.
 *
 * Built on `expressiveLightColorScheme()`, whose entire delta from `lightColorScheme()` is four
 * roles — onPrimaryContainer, onSecondaryContainer, onTertiaryContainer, onErrorContainer — pushed
 * to the tone-30 palette entries so container text reads with more punch.
 */
val LightScheme: ColorScheme = expressiveLightColorScheme().copy(
    primary = BrandPrimaryLight,
    onPrimary = BrandOnPrimaryLight,
    primaryContainer = BrandPrimaryContainerLight,
    onPrimaryContainer = BrandOnPrimaryContainerLight,
)

/**
 * Dark fallback.
 *
 * NOTE: `expressiveDarkColorScheme()` DOES NOT EXIST. It is absent from ColorScheme.kt at every
 * version through 1.5.0-alpha26. Dark uses plain `darkColorScheme()`; the official sample does
 * exactly this asymmetric branch. If you see `expressiveDarkColorScheme()` anywhere — generated
 * code, a blog post, model output — it is wrong.
 */
val DarkScheme: ColorScheme = darkColorScheme().copy(
    primary = BrandPrimaryDark,
    onPrimary = BrandOnPrimaryDark,
    primaryContainer = BrandPrimaryContainerDark,
    onPrimaryContainer = BrandOnPrimaryContainerDark,
)

/**
 * AMOLED override. Surfaces only — container roles need call-site handling, because flattening
 * every surfaceContainer* tier to black destroys the depth ladder the whole design relies on.
 */
fun ColorScheme.pureBlack(apply: Boolean): ColorScheme =
    if (apply) copy(surface = Color.Black, background = Color.Black) else this
```

---

# 8. `ui/theme/Shape.kt`

```kotlin
package com.example.expressive.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes

/**
 * The eight-step Expressive shape scale.
 *
 * Baseline M3 stopped at extraLarge (28dp). Expressive adds THREE steps:
 * largeIncreased (20dp), extraLargeIncreased (32dp), extraExtraLarge (48dp).
 *
 * Declaration order in the `Shapes` constructor is NOT scale order — `largeIncreased` (20dp) is
 * declared AFTER `extraLarge` (28dp). Always pass named arguments, as below.
 *
 * These values ARE the defaults. This file exists so the scale is visible and editable in one
 * place; passing `shapes = AppShapes` and passing nothing are equivalent until you change a number.
 */
val AppShapes = Shapes(
    extraSmall          = RoundedCornerShape(4.dp),
    small               = RoundedCornerShape(8.dp),
    medium              = RoundedCornerShape(12.dp),
    large               = RoundedCornerShape(16.dp),
    largeIncreased      = RoundedCornerShape(20.dp),
    extraLarge          = RoundedCornerShape(28.dp),
    extraLargeIncreased = RoundedCornerShape(32.dp),
    extraExtraLarge     = RoundedCornerShape(48.dp),
)
```

Add `import androidx.compose.ui.unit.dp` at the top.

**How to use the scale, in one line each:** `small`/`medium` for chips and dense controls;
`large` (16dp) for ordinary cards and list groups; `largeIncreased` (20dp) for cards you want to feel
softer; `extraLarge`/`extraLargeIncreased` for hero media and sheets; `extraExtraLarge` (48dp) for
**one** element per surface. A shape is emphatic only because its neighbours are not — applying 48dp
everywhere is a corner-radius setting, not a design decision.

---

# 9. `ui/theme/Type.kt`

Ships with the platform font so the template builds with no asset. The variable-font path is a
complete separate file (§9.1) — add it together with the font binary.

```kotlin
package com.example.expressive.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.font.FontFamily

/**
 * The active font family for the whole 30-style scale.
 *
 * SWAP: to move to the variable-font path, add `TypeVariable.kt` (§9.1) plus
 * `app/src/main/res/font/google_sans_flex.ttf`, then change this one line to:
 *
 *     val AppFontFamily: FontFamily @Composable get() = GoogleSansFlex
 *
 * and make `AppTypography` a `@Composable get()` too.
 */
val AppFontFamily: FontFamily = FontFamily.Default

/**
 * The Expressive type scale.
 *
 * `Typography(fontFamily = …)` (added 1.5.0-alpha16, extended alpha19) applies one family to all
 * THIRTY slots — the fifteen baseline roles and the fifteen `*Emphasized` roles — and keeps the
 * Material metrics. That single-argument form replaces the old "copy every slot off
 * MaterialTheme.typography" idiom, which is what you must still write on material3 < alpha16.
 *
 * The parameter is `fontFamily`. `defaultFontFamily` is the MATERIAL 2 name and does not exist
 * on material3 `Typography` — writing it will not compile.
 *
 * Do not set only the fifteen baseline slots and leave the emphasized ones defaulted: the
 * emphasized roles are what Tactic 3 ("guide attention with typography") is made of, and a
 * half-applied scale shows up as one brand headline next to a Roboto sub-head.
 */
val AppTypography = Typography(fontFamily = AppFontFamily)
```

## 9.1 `ui/theme/TypeVariable.kt` — the variable-font path

Add this file **and** `app/src/main/res/font/google_sans_flex.ttf` together. Requires `minSdk 26`:
`variationSettings` on `Font(resId, …)` is API 26+.

```kotlin
package com.example.expressive.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import com.example.expressive.R

/**
 * Google Sans Flex axes:
 *   wght 100–1000   stroke weight        — 400 body, 500–600 emphasized, 900 hero
 *   wdth 75–125     condensed → expanded — 100 default, 110–112.5 for display/hero
 *   ROND 0–100      sharp → rounded      — 100 is the M3 Expressive signature; 0 is "normal"
 *   GRAD -50–150    visual weight without changing glyph bounds — safe to animate, no reflow
 *   slnt -10–0      italic angle
 *   opsz auto       optical size — leave to the renderer
 *
 * `ROND` is the axis that makes an app read as "M3 Expressive" rather than "M3".
 *
 * The `weight = FontWeight.X` on each entry is the LOOKUP KEY Compose matches against.
 * `FontVariation.weight(N)` is the AXIS VALUE the renderer applies. They are independent — set
 * both, and make them agree, or `FontWeight.Bold` text selects an entry whose wght axis is 400.
 */
@OptIn(ExperimentalTextApi::class)
private fun googleSansFlex(weight: FontWeight, wght: Int, wdth: Float = 100f, rond: Float = 100f) =
    Font(
        resId = R.font.google_sans_flex,
        weight = weight,
        style = FontStyle.Normal,
        variationSettings = FontVariation.Settings(
            FontVariation.weight(wght),
            FontVariation.width(wdth),
            FontVariation.Setting("ROND", rond),
        ),
    )

val GoogleSansFlex: FontFamily = FontFamily(
    googleSansFlex(FontWeight.Light, 300),
    googleSansFlex(FontWeight.Normal, 400),
    googleSansFlex(FontWeight.Medium, 500),
    googleSansFlex(FontWeight.SemiBold, 600),
    googleSansFlex(FontWeight.Bold, 700),
    googleSansFlex(FontWeight.ExtraBold, 800),
    // Hero face: heavier AND wider. Display sizes take extra width well; body text does not.
    googleSansFlex(FontWeight.Black, 900, wdth = 112.5f),
)

/** Wire this into Theme.kt in place of `AppTypography` once the font file exists. */
val VariableTypography: Typography
    @Composable get() = Typography(fontFamily = GoogleSansFlex)
```

Add `import androidx.compose.material3.Typography`.

**Missing-instance trap.** If the family lacks an entry for a weight the scale asks for, Compose
synthesises it — fake bolding, which looks blurry and wrong next to real instances. Cover the whole
range the scale uses (300–900 above) rather than shipping three entries and hoping.

**Do not animate `wght` or `wdth` casually.** They change glyph bounds, so the text reflows; treat
them as *spatial* motion. `GRAD` changes apparent weight without changing bounds and is the safe axis
to animate.

---

# 10. `ui/theme/Theme.kt`

```kotlin
package com.example.expressive.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext

@Composable
fun AppTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    pureBlack: Boolean = false,
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val useDynamic = dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

    val base: ColorScheme = when {
        useDynamic && darkTheme -> dynamicDarkColorScheme(context)
        useDynamic -> dynamicLightColorScheme(context)
        darkTheme -> DarkScheme
        else -> LightScheme
    }

    // All three inputs must be keys or a stale scheme survives recomposition.
    val colorScheme = remember(base, pureBlack, darkTheme) {
        base.pureBlack(darkTheme && pureBlack)
    }

    MaterialExpressiveTheme(
        colorScheme = colorScheme,
        // THE LINE PEOPLE FORGET. Without an explicit expressive motion scheme, every
        // physics-driven component silently falls back to MotionScheme.standard() springs and the
        // app loses its Expressive feel with no compile error and no visual smoking gun.
        motionScheme = MotionScheme.expressive(),
        shapes = AppShapes,
        typography = AppTypography,
        content = content,
    )
}
```

**The `MaterialExpressiveTheme` trap.** Its parameters are **nullable**, and `null` means *"use the
Expressive default"* — it does **not** mean *"inherit from the ambient theme"*, which is what
`MaterialTheme`'s non-null defaults do. Consequence: a **nested** `MaterialExpressiveTheme` must pass
`colorScheme = MaterialTheme.colorScheme` etc. explicitly to inherit. That nesting is the correct
retrofit path for an existing app — wrap one subtree, pass the ambient values straight through, and
override only `motionScheme = MotionScheme.expressive()` to get expressive physics without converting
the app.

Reading motion off the theme, everywhere, instead of writing durations:

```kotlin
val motionScheme = MaterialTheme.motionScheme    // hoist once per composable

// SPATIAL — anything that moves, resizes, or changes shape. Springs, may overshoot.
motionScheme.fastSpatialSpec<Dp>()      // small, immediate: press states, icon swaps
motionScheme.defaultSpatialSpec<Dp>()   // the workhorse
motionScheme.slowSpatialSpec<Dp>()      // large travel: sheets, full-screen transitions

// EFFECTS — colour, alpha, elevation. Never overshoot; a colour that overshoots is a flicker.
motionScheme.fastEffectsSpec<Color>()
motionScheme.defaultEffectsSpec<Color>()
motionScheme.slowEffectsSpec<Color>()
```

`val motionScheme = MaterialTheme.motionScheme` is not a typo — the property is `@Composable`, and
the local copy is readable from non-composable lambdas (gesture callbacks, `transitionSpec` blocks,
coroutines).

---

# 11. `MainActivity.kt`

```kotlin
package com.example.expressive

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.example.expressive.ui.theme.AppTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        // BEFORE super.onCreate() and before setContent. Expressive components — flexible app bars,
        // floating toolbars, FAB menus, the nav suite — assume an edge-to-edge window and look
        // wrong inset without it. On Android 15+ this is the default anyway; calling it explicitly
        // is what makes the app behave identically on 14 and below.
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        setContent {
            AppTheme {
                AppShell()
            }
        }
    }
}
```

**Do not** set `window.statusBarColor` / `navigationBarColor` — deprecated and a no-op on API 35+.
Control system-bar icon contrast through `enableEdgeToEdge(statusBarStyle = SystemBarStyle.…)` if you
need it, and let the app bars supply their own container colours.

---

# 12. `navigation/Routes.kt`

```kotlin
package com.example.expressive.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.Settings
import androidx.compose.ui.graphics.vector.ImageVector
import com.example.expressive.R
import kotlinx.serialization.Serializable

/** Type-safe Navigation 2 routes. `@Serializable` + `composable<T>` — no string route parsing. */
sealed interface Route {
    @Serializable data object Home : Route
    @Serializable data object Library : Route
    @Serializable data object Settings : Route
    @Serializable data class Detail(val id: String) : Route
}

/**
 * Top-level destinations. Keeping the icon and label ON the enum is what lets `AppShell` render the
 * nav container with a single `forEach` and no per-destination branching — which is in turn what
 * makes the container swap (bar ↔ rail) free.
 */
enum class TopLevel(val labelRes: Int, val icon: ImageVector, val route: Route) {
    Home(R.string.nav_home, Icons.Default.Home, Route.Home),
    Library(R.string.nav_library, Icons.Default.LibraryMusic, Route.Library),
    Settings(R.string.nav_settings, Icons.Default.Settings, Route.Settings),
}
```

---

# 13. `navigation/AppNavHost.kt`

```kotlin
package com.example.expressive.navigation

import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.toRoute
import com.example.expressive.feature.home.HomeRoute
import com.example.expressive.ui.motion.LocalAnimatedVisibilityScope
import com.example.expressive.ui.motion.LocalSharedTransitionScope

@Composable
fun AppNavHost(
    navController: NavHostController,
    modifier: Modifier = Modifier,
) {
    // SharedTransitionLayout must WRAP the nav host, not live inside a destination — elements can
    // only be shared between siblings under the same layout. Set it up once here and every screen
    // can opt into a container transform without changing its signature.
    SharedTransitionLayout(modifier = modifier) {
        CompositionLocalProvider(LocalSharedTransitionScope provides this) {
            NavHost(
                navController = navController,
                startDestination = Route.Home,
            ) {
                composable<Route.Home> {
                    // Re-provided PER DESTINATION: each composable<T> block is its own
                    // AnimatedContentScope.
                    CompositionLocalProvider(LocalAnimatedVisibilityScope provides this) {
                        HomeRoute(
                            onItemClick = { id -> navController.navigate(Route.Detail(id)) },
                        )
                    }
                }
                composable<Route.Library> {
                    CompositionLocalProvider(LocalAnimatedVisibilityScope provides this) {
                        PlaceholderScreen("Library")
                    }
                }
                composable<Route.Settings> {
                    CompositionLocalProvider(LocalAnimatedVisibilityScope provides this) {
                        PlaceholderScreen("Settings")
                    }
                }
                composable<Route.Detail> { entry ->
                    val detail: Route.Detail = entry.toRoute()
                    CompositionLocalProvider(LocalAnimatedVisibilityScope provides this) {
                        PlaceholderScreen("Detail ${detail.id}")
                    }
                }
            }
        }
    }
}
```

`ui/motion/SharedTransitionLocals.kt`:

```kotlin
package com.example.expressive.ui.motion

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.runtime.compositionLocalOf

// Throw from the default rather than returning null: a clear crash at the wrong call site beats a
// silently-missing animation.
val LocalSharedTransitionScope = compositionLocalOf<SharedTransitionScope> {
    error("No SharedTransitionScope — wrap the NavHost in SharedTransitionLayout")
}
val LocalAnimatedVisibilityScope = compositionLocalOf<AnimatedVisibilityScope> {
    error("No AnimatedVisibilityScope — provide it inside each composable<T> { }")
}
```

`PlaceholderScreen` is a `Box` with centred `Text`; replace all three as you build.

---

# 14. `AppShell.kt` — the adaptive root

```kotlin
package com.example.expressive

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfoV2
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteItem
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffold
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffoldDefaults
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteType
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.window.core.layout.WindowSizeClass
import com.example.expressive.navigation.AppNavHost
import com.example.expressive.navigation.TopLevel

@Composable
fun AppShell() {
    val navController = rememberNavController()
    val currentEntry by navController.currentBackStackEntryAsState()

    val adaptiveInfo = currentWindowAdaptiveInfoV2()   // NEVER currentWindowAdaptiveInfo() — the
                                                       // deprecated one clamps everything >= 840dp
                                                       // to Expanded, erasing Large and XL.

    // NavigationSuiteScaffoldDefaults.navigationSuiteType() covers compact (short bar), compact
    // height / tabletop (short bar, horizontal items) and medium/expanded (collapsed wide rail),
    // and then STOPS. It never returns WideNavigationRailExpanded, NavigationDrawer or None.
    // Reaching the >= 1200dp row requires this explicit override.
    val computed = NavigationSuiteScaffoldDefaults.navigationSuiteType(adaptiveInfo)
    val navSuiteType =
        if (adaptiveInfo.windowSizeClass.isWidthAtLeastBreakpoint(
                WindowSizeClass.WIDTH_DP_LARGE_LOWER_BOUND
            )
        ) {
            NavigationSuiteType.WideNavigationRailExpanded
        } else {
            computed
        }

    NavigationSuiteScaffold(
        navigationSuiteType = navSuiteType,
        navigationItemVerticalArrangement = Arrangement.Center,
        // The FAB belongs to the SUITE scaffold, not to an inner Scaffold. `primaryActionContent`
        // places it correctly per container: inside vertical nav components as part of their
        // header, above horizontal ones. One API instead of a per-size-class placement branch.
        // Put it in an inner Scaffold and it overlaps the bar at compact width and sits in dead
        // space beside the rail at expanded width.
        primaryActionContent = {
            FloatingActionButton(onClick = { /* primary action */ }) {
                Icon(Icons.Default.Add, contentDescription = "New")
            }
        },
        navigationItems = {
            TopLevel.entries.forEach { dest ->
                NavigationSuiteItem(
                    selected = currentEntry?.destination?.hierarchy
                        ?.any { it.hasRoute(dest.route::class) } == true,
                    onClick = {
                        navController.navigate(dest.route) {
                            // THE TRIPLE. Without it, resizing from bar to rail re-creates every
                            // screen and loses scroll position — the #1 "state lost on resize"
                            // bug, and it has nothing to do with the adaptive library.
                            popUpTo(navController.graph.findStartDestination().id) {
                                saveState = true
                            }
                            launchSingleTop = true
                            restoreState = true
                        }
                    },
                    icon = { Icon(dest.icon, contentDescription = null) },
                    label = { Text(stringResource(dest.labelRes)) },
                    // Pass it explicitly. The scaffold and the items compute the type
                    // independently; override one and not the other and the item renders for the
                    // wrong container.
                    navigationSuiteType = navSuiteType,
                )
            }
        },
    ) {
        // The nav host goes INSIDE `content`, never beside the scaffold.
        AppNavHost(navController = navController)
    }
}
```

Add `import androidx.navigation.NavDestination.Companion.hierarchy`.

**Never two nav containers.** The three ways this happens, all of which look fine on a phone:
a `Scaffold(bottomBar = { ShortNavigationBar(…) })` inside this scaffold; a
`HorizontalFloatingToolbar` used *as* navigation on top of it; a rail rendered per-screen inside the
nav host. If you want a floating toolbar as your nav container, do not use `NavigationSuiteScaffold`
at all — hand-roll the size switch.

---

# 15. One screen, end to end

`feature/home/HomeViewModel.kt`:

```kotlin
package com.example.expressive.feature.home

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

@Immutable
data class HomeItem(val id: String, val title: String, val subtitle: String)

@Immutable
sealed interface HomeUiState {
    data object Loading : HomeUiState
    data object Empty : HomeUiState
    data class Content(val items: List<HomeItem>) : HomeUiState
}

class HomeViewModel : ViewModel() {
    private val _uiState = MutableStateFlow<HomeUiState>(HomeUiState.Loading)
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    init { load() }

    fun load() {
        viewModelScope.launch {
            _uiState.value = HomeUiState.Loading
            delay(600)   // stand-in for a repository call
            _uiState.value = HomeUiState.Content(
                List(12) { HomeItem("id-$it", "Item ${it + 1}", "Supporting text") }
            )
        }
    }
}
```

`feature/home/HomeRoute.kt` — the stateful half. One line of substance.

```kotlin
package com.example.expressive.feature.home

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel

@Composable
fun HomeRoute(
    onItemClick: (String) -> Unit,
    viewModel: HomeViewModel = viewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    HomeScreen(state = state, onItemClick = onItemClick, onRetry = viewModel::load)
}
```

`feature/home/HomeScreen.kt` — the stateless half. This is what you preview and screenshot-test.

```kotlin
package com.example.expressive.feature.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.expressive.ui.theme.AppTheme

@Composable
fun HomeScreen(
    state: HomeUiState,
    onItemClick: (String) -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // exitUntilCollapsed: the big headline shrinks to a small bar and stays there.
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

    Scaffold(
        topBar = {
            LargeFlexibleTopAppBar(
                title = { Text("Home") },
                // The cheapest legitimate hero moment on a screen: a large flexible bar with a
                // subtitle. Two slots, no custom drawing. Keep the subtitle SEMANTIC (a count, a
                // breadcrumb, a status) — not a tagline.
                subtitle = {
                    val count = (state as? HomeUiState.Content)?.items?.size ?: 0
                    Text(if (count == 0) "Nothing yet" else "$count items")
                },
                scrollBehavior = scrollBehavior,
            )
        },
        // REQUIRED, and it goes on the Scaffold — not the app bar, not the list.
        modifier = modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
    ) { innerPadding ->
        when (state) {
            HomeUiState.Loading -> HomeLoading(innerPadding)
            HomeUiState.Empty -> HomeEmpty(innerPadding, onRetry)
            is HomeUiState.Content -> HomeList(state.items, innerPadding, onItemClick)
        }
    }
}

@Composable
private fun HomeList(
    items: List<HomeItem>,
    contentPadding: PaddingValues,
    onItemClick: (String) -> Unit,
) {
    val colors = ListItemDefaults.segmentedColors(
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
    )
    LazyColumn(
        // contentPadding, never Modifier.padding — this is what lets content scroll under the
        // collapsing bar while the first and last rows stay clear.
        contentPadding = contentPadding,
        verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap),
        modifier = Modifier.fillMaxSize(),
    ) {
        itemsIndexed(items, key = { _, item -> item.id }) { index, item ->
            SegmentedListItem(
                onClick = { onItemClick(item.id) },
                // `shapes` is REQUIRED on every SegmentedListItem overload — there is no default.
                // segmentedShapes(index, count) rounds the outer corners of the run and squares
                // the inner ones, so the group reads as one object, and supplies the pressed /
                // selected morph shapes for free. No animation code anywhere.
                shapes = ListItemDefaults.segmentedShapes(index = index, count = items.size),
                colors = colors,
                supportingContent = {
                    Text(item.subtitle, maxLines = 1, overflow = TextOverflow.Ellipsis)
                },
                modifier = Modifier.padding(horizontal = 16.dp),
            ) {
                // The headline is the TRAILING `content` lambda on every expressive overload.
                // The deprecated baseline overload takes `headlineContent` first — mixing them
                // gives a confusing "no applicable overload".
                Text(item.title, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)   // LoadingIndicator: promoted at alpha18,
                                                    // REVERTED at alpha19, still gated at alpha26.
@Composable
private fun HomeLoading(contentPadding: PaddingValues) {
    Box(
        modifier = Modifier.fillMaxSize().padding(contentPadding),
        contentAlignment = Alignment.Center,
    ) {
        LoadingIndicator()
    }
}

@Composable
private fun HomeEmpty(contentPadding: PaddingValues, onRetry: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(contentPadding).padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically),
    ) {
        // The emphasized type role, not `.copy(fontWeight = Bold)`. Emphasized styles change
        // weight AND tracking AND (with a variable font) width.
        Text("Nothing here yet", style = MaterialTheme.typography.headlineSmallEmphasized)
        Text(
            "Pull to refresh or tap below.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        androidx.compose.material3.OutlinedButton(onClick = onRetry) { Text("Reload") }
    }
}
```

---

# 16. Previews and screenshot tests

Previews at the bottom of `HomeScreen.kt`. Under the screenshot-test setup, previews **are** the
fixtures — preview coverage and screenshot coverage are the same work.

```kotlin
private val PreviewItems = List(6) { HomeItem("p$it", "Item ${it + 1}", "Supporting text") }

@Preview(name = "Phone Light", showBackground = true,
    device = "spec:width=411dp,height=891dp,dpi=440",
    uiMode = android.content.res.Configuration.UI_MODE_NIGHT_NO)
@Preview(name = "Phone Dark", showBackground = true,
    device = "spec:width=411dp,height=891dp,dpi=440",
    uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES)
@Preview(name = "Phone Large Font", showBackground = true, fontScale = 1.5f,
    device = "spec:width=411dp,height=891dp,dpi=440")
@Preview(name = "Tablet Light", showBackground = true,
    device = "spec:width=1280dp,height=800dp,dpi=240")
@Composable
fun HomeScreenPreview() {
    // Dynamic colour OFF in previews: on a real device it reads the wallpaper, which makes
    // screenshot references machine-dependent and every diff a false positive.
    AppTheme(dynamicColor = false) {
        HomeScreen(HomeUiState.Content(PreviewItems), onItemClick = {}, onRetry = {})
    }
}

@Preview(name = "Loading", showBackground = true)
@Composable
fun HomeScreenLoadingPreview() {
    AppTheme(dynamicColor = false) {
        HomeScreen(HomeUiState.Loading, onItemClick = {}, onRetry = {})
    }
}

@Preview(name = "Empty", showBackground = true)
@Composable
fun HomeScreenEmptyPreview() {
    AppTheme(dynamicColor = false) {
        HomeScreen(HomeUiState.Empty, onItemClick = {}, onRetry = {})
    }
}
```

`app/src/screenshotTest/kotlin/com/example/expressive/feature/home/HomeScreenshotTest.kt`:

```kotlin
package com.example.expressive.feature.home

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.android.tools.screenshot.PreviewTest

/**
 * A PLAIN CLASS — no @RunWith, no @get:Rule, no JUnit. Each method carries all three annotations
 * (@PreviewTest, @Preview, @Composable) and its body just calls the @Preview composable that
 * already exists in `main`. Same-package placement reaches internal previews without widening
 * visibility.
 */
class HomeScreenshotTest {

    @PreviewTest
    @Preview(showBackground = true)
    @Composable
    fun homeContent() { HomeScreenPreview() }

    @PreviewTest
    @Preview(showBackground = true)
    @Composable
    fun homeLoading() { HomeScreenLoadingPreview() }

    @PreviewTest
    @Preview(showBackground = true)
    @Composable
    fun homeEmpty() { HomeScreenEmptyPreview() }
}
```

```bash
./gradlew :app:updateDebugScreenshotTest      # generate / accept references (first run, and after
                                              # any intentional visual change)
./gradlew :app:validateDebugScreenshotTest    # verify — what CI runs
```

References land at
`app/src/screenshotTestDebug/reference/com/example/expressive/feature/home/HomeScreenshotTest/<method>_<8-hex>_<index>.png`.
**The hash digests the `@Preview` *configuration*.** Change any `@Preview` parameter and the hash
changes, orphaning the old PNG — the most confusing part of this workflow. `_0`…`_n` is the index
within a multi-`@Preview` function, so the four stacked annotations on `HomeScreenPreview` produce
`_0` through `_3`.

**Why this matters more for Expressive than for baseline M3:** shape morphs, motion end-states, and
alpha-to-alpha default drift are all invisible to assertions and glaring in a pixel diff. Pinning
`material3 1.5.0-alpha26` means shape, spacing, elevation and colour defaults can move *between
alphas with no change on your side*; a screenshot suite is the only cheap guard against a version
bump silently restyling the app.

Full detail — the `LocalInspectionMode` fix for tests that hang on infinite expressive animations,
`mainClock.autoAdvance = false` for mid-animation assertions, threshold tuning, and the adaptive-
output caveat — is in
`${CLAUDE_PLUGIN_ROOT}/skills/m3-expressive-review/references/testing-expressive-ui.md`.

---

# 17. Verify it before you build on it

```bash
./gradlew :app:compileDebugKotlin           # do not assume — on alpha26 this is the only real check
./gradlew :app:updateDebugScreenshotTest
./gradlew :app:validateDebugScreenshotTest
./gradlew :app:installDebug
```

Then, on a device or emulator:

- [ ] Content draws under the status bar and the app bar collapses on scroll.
- [ ] Rotate to landscape. Nothing letterboxes, nothing crops.
- [ ] Resize a freeform / desktop window from ~400dp to ~1400dp: the nav container goes short bar →
      collapsed rail → **expanded rail**, and the list keeps its scroll position throughout.
- [ ] Toggle dark mode. Nothing disappears.
- [ ] Toggle "Force dark" and dynamic colour off (Developer options / a non-Android-12 device) —
      the static fallback schemes render.
- [ ] `fontScale = 2.0` in Accessibility settings: nothing clips. Expressive components run *shorter*
      than their predecessors (the nav bar went 80dp → 64dp), so headroom is thinner than you expect.
- [ ] TalkBack through the whole shell: every icon-only control announces, the nav items announce
      selection state.

If a build fails on an API that is not in this document, check the alpha26 release notes — that
release contains an **unenumerated** bullet ("Updated the public API surface to align with recent API
review feedback", I71aff) that may contain undocumented renames. **Trust the compiler over any
document, including this one.**

---

# 18. First five things to change

This is a template. Shipping it as-is means shipping someone else's app.

### 1. `BrandSeed` and the eight brand colour constants in `Color.kt`

Right now they are the Material default purple. Generate a real scheme from your brand colour — the
Material Theme Builder, or `material-kolor`'s `rememberDynamicColorScheme(seedColor, isDark,
specVersion = ColorSpec.SpecVersion.SPEC_2025, style = PaletteStyle.TonalSpot)` if you want it
computed at runtime (`SPEC_2025` is the Expressive-era palette maths; omit it and you get 2021
maths). Keep **dynamic colour as the default** — the fallback scheme is for API < 31 and for users
who turn wallpaper colours off, not for overriding the system.

### 2. Add a real font and switch `AppFontFamily`

`FontFamily.Default` is Roboto, and Roboto is the most legible signal that a theme was never
finished. Add `TypeVariable.kt` (§9.1) plus the font binary and flip the one line. If you only take
one thing from this list, take this one: with `ROND = 100` on a variable face, an otherwise identical
app reads as M3 Expressive rather than M3.

### 3. Decide where your one hero moment is — and delete the others

The template gives every screen a `LargeFlexibleTopAppBar`, which spends the budget by default.
Material's constraint is explicit: *"Stick to one or two hero moments in your product; too many
moments can be overwhelming or distracting."* Per **product**, not per screen. Pick the surface that
is genuinely key and emotionally impactful, put the expressive weight there, and demote everything
else to `TopAppBar`. A hero moment that persists is no longer surprising — it is the new baseline,
and it stops working.

### 4. Replace the three `PlaceholderScreen`s and pick the real navigation shape

Three top-level destinations with a rail is the template's guess. If your app's core surface is a
list with meaningful detail per item — mail, notes, contacts, media, settings — that surface should
be a `NavigableListDetailPaneScaffold` (or a Nav3 `ListDetailSceneStrategy`), not a destination that
pushes a full-screen detail. See
`${CLAUDE_PLUGIN_ROOT}/skills/m3-expressive-best-practices/references/worked-screens-list-detail-feed.md`
§4 for the whole wiring, and note that the decision is worth making now — retrofitting panes onto a
screen that assumed full-width is more work than it looks.

### 5. Decide your opt-in strategy deliberately

`app/build.gradle.kts` currently opts in globally, which is insurance but costs you the compiler's
map of experimental dependencies. Three valid strategies:

| Strategy | Pro | Use when |
| --- | --- | --- |
| Global Gradle `optIn` (the template) | Zero annotation noise; covers anything alpha26's unenumerated API sweep re-gated | An app module committed to Expressive throughout |
| `@file:OptIn(...)` per UI file | One line per file, still greppable, still scoped | Most of the app is Expressive UI but you want a blast radius |
| Per-call-site `@OptIn` | The compiler tells you exactly which functions depend on experimental API; an alpha bump's damage is visible in the diff | A library or shared module — and it is what Google's JetPacker does, with no Gradle-level opt-in anywhere |

Whichever you choose, do **not** go on a deletion spree removing "redundant" opt-ins. Never remove
one from `LoadingIndicator` / `ContainedLoadingIndicator`, `MaterialShapes` + `toShape()`/`toPath()`,
the menu APIs, `PullToRefreshDefaults`' loading-indicator colours, the `ToggleButton` size variants,
or `ButtonGroup`. A redundant opt-in is a warning; a missing one is a build failure.

---

# 19. Things this template deliberately does not include

Each of these is a real decision, not an oversight.

| Omitted | Why, and when to add it |
| --- | --- |
| Dependency injection (Hilt/Koin) | `viewModel()` is enough for a template and keeps the file count honest. Add DI when you have a repository worth injecting. |
| A multi-module structure | One module, no `buildSrc`, no convention plugins — JetPacker's structure. Split modules when build times or ownership demand it, not preemptively. |
| `material3-window-size-class` | Legacy. The type you want is `androidx.window.core.layout.WindowSizeClass`, which the adaptive artifacts already bring. |
| `derivedMediaQuery` / `UiMediaScope` | Experimental, runtime-flag-gated, and it becomes a second source of truth that disagrees with a pane scaffold at the boundary. `WindowSizeClass` is the conservative choice and `material3.adaptive` is stable. |
| A `Scaffold` inside `NavigationSuiteScaffold`'s content | Per-screen `Scaffold`s are correct; a *second nav container* is not. The template's FAB is on `primaryActionContent` for exactly this reason. |
| `AnimatedPane(shape = …)` | Post-1.3.0. Do not write it against this pin. |
| `compose-bom-alpha` | Needed only for Compose Multiplatform or for `foundation`/`animation` alphas. A stable BOM plus an explicit `material3` pin is what five of the seven reference apps — including all three Google ones — actually do. |

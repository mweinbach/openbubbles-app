# Views/XML → Compose — interop, theme bridging, and the migration mechanics

The working reference for moving an Android Views/XML app to Compose so it can adopt M3 Expressive.
Status, policy, and the migrate/don't-migrate decision are in `compose-first-status.md`. This file is
**how**.

**Provenance tags**

| Tag | Meaning |
| --- | --- |
| `[SRC]` | Verbatim from Google primary documentation |
| `[DERIVED]` | Reasoned here from `[SRC]` facts — not itself a published statement |
| `[UNVERIFIED]` | Not confirmed against a primary source; verify before relying on it |

Google's own framing `[SRC]`:

> "Jetpack Compose supports interoperability with Views — you can use Compose in Views, and Views in
> Compose. This allows adoption of Compose in existing View-based apps without having to migrate all
> Views immediately."

> "Incrementally migrating your app to Compose means that Compose and Views will co-exist in your app."

---

## 1. Interop map — pick the tool first

| You have | You want | Use | Artifact |
| --- | --- | --- | --- |
| An XML layout | A Compose island inside it | `<androidx.compose.ui.platform.ComposeView>` in the layout | `androidx.compose.ui:ui` |
| An Activity, no XML | A whole Compose screen | `setContent { }` on `ComponentActivity` | `androidx.activity:activity-compose` |
| A Fragment | A whole Compose screen | `ComposeView` returned from `onCreateView` | `androidx.compose.ui:ui` |
| A reusable component consumed by XML screens | One class Views teams can drop in | subclass `AbstractComposeView` | `androidx.compose.ui:ui` |
| A composable screen | One legacy custom View | `AndroidView(factory = …, update = …)` | `androidx.compose.ui:ui` |
| A composable screen | A whole existing XML layout | `AndroidViewBinding(Binding::inflate) { }` | `androidx.compose.ui:ui-viewbinding` |
| A `RecyclerView` | Compose rows | `ComposeView` per `ViewHolder` (see §5 — version floor applies) | `androidx.recyclerview:recyclerview:1.3.0-alpha02+` |

`AndroidViewBinding` requires an extra dependency; people forget this and get an unresolved
reference:

```kotlin
implementation("androidx.compose.ui:ui-viewbinding")
```

---

## 2. Compose inside Views — `ComposeView`

### 2.1 In an XML layout

```xml
<!-- res/layout/fragment_example.xml — inside any existing ViewGroup -->
<androidx.compose.ui.platform.ComposeView
    android:id="@+id/compose_view"
    android:layout_width="match_parent"
    android:layout_height="0dp"
    android:layout_weight="1" />
```

The `android:id` is not decorative — see pitfall 2.

### 2.2 In a Fragment — the canonical pattern `[SRC]`

```kotlin
class ExampleFragment : Fragment() {
    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentExampleBinding.inflate(inflater, container, false)
        binding.composeView.apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent { MaterialTheme { Text("Hello Compose!") } }
        }
        return binding.root
    }
}
```

The `setViewCompositionStrategy` line is not optional in a Fragment. See §4.

### 2.3 A whole-screen Compose Fragment (no XML at all)

The target shape for screen-by-screen migration: the Fragment survives only as a navigation
destination, returning a bare `ComposeView` from `onCreateView` with no layout file. Full example
with correct ViewModel ownership in §10.

### 2.4 Reusable component for View-based screens — `AbstractComposeView`

Use when a Views team needs to drop a Compose component into XML without knowing Compose exists.
This is the mechanism behind step 2 of the official strategy ("create a library of common UI
components"). Consume it in XML like any custom view: `<com.example.ui.ExpressiveLoadingView … />`.

```kotlin
class ExpressiveLoadingView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : AbstractComposeView(context, attrs, defStyleAttr) {

    var label: String by mutableStateOf("")

    @Composable
    override fun Content() {
        AppTheme {                           // theme travels with the component — see pitfall 1
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                LoadingIndicator()
                Text(label, style = MaterialTheme.typography.bodyMediumEmphasized)
            }
        }
    }
}
```

Public state must be `mutableStateOf`-backed, not a plain field — a plain field will not recompose.

### 2.5 Every island carries its own theme

`[SRC]`: *"When creating new screens in Compose, regardless of which version of Material Design
you're using, ensure that you apply a `MaterialTheme` before any composables that emit UI from the
Compose Material libraries."*

A `ComposeView` does **not** inherit the XML `Theme.Material3*` applied to its host Activity.
Composition-local theming is a Compose concept; the Android theme is an Android concept. They do not
talk to each other. Omit the theme wrapper and you get the Material baseline purple — this is the
single most common "why does my Compose button look wrong" report. Fix: one shared `AppTheme`
composable, applied inside every `setContent { }` and every `AbstractComposeView.Content()`.

---

## 3. Views inside Compose — `AndroidView` and `AndroidViewBinding`

### 3.1 `AndroidView` — one View

```kotlin
@Composable
fun LegacyChart(
    data: ChartData,
    modifier: Modifier = Modifier,
) {
    AndroidView(
        modifier = modifier.fillMaxWidth(),
        factory = { context ->
            // Called once. Construct the View here. Never read Compose state here.
            LegacyChartView(context).apply {
                setOnPointSelectedListener { /* … */ }
            }
        },
        update = { view ->
            // Called on first composition and on every recomposition where a read state changed.
            view.setData(data)
        },
    )
}
```

Rules:

- `factory` runs **once**. Reading Compose state inside `factory` silently produces a stale View.
- `update` is the recomposition hook — put every state→View push there.
- `AndroidView` also accepts `onReset` / `onRelease` lambdas for View reuse and teardown (detach
  listeners in `onRelease`). `[UNVERIFIED]` exact parameter ordering across versions — read the
  signature in your pinned `compose-ui`.
- The View measures with the Android layout system. `wrap_content` views inside scrolling Compose
  containers are a classic layout-loop source; bound them with a size modifier.

### 3.2 `AndroidViewBinding` — a whole existing layout

Cheapest way to drop a legacy screen chunk into a composable. Type-safe against the generated
binding class; the trailing lambda's receiver **is** the binding.

```kotlin
@Composable
fun LegacyProfileHeader(
    user: User,
    modifier: Modifier = Modifier,
) {
    AndroidViewBinding(ViewProfileHeaderBinding::inflate, modifier) {
        // `this` is ViewProfileHeaderBinding
        profileName.text = user.name
        profileSubtitle.text = user.handle
        followButton.setOnClickListener { /* … */ }
    }
}
```

Requires View Binding enabled in the module:

```kotlin
android {
    buildFeatures { viewBinding = true }
}
```

Use `AndroidViewBinding` for **inverted migration order** — when you want to move the screen's
*root* to Compose first (getting Compose navigation, theming, and insets) while the interior stays
XML for now. That is the opposite of leaf-first (§6) and is the right call for screens with a
complicated interior and a simple frame.

### 3.3 `WebView`

`WebView` in Compose has its own documented page and its own lifecycle rules. Do not hand-roll it
inside `AndroidView` without reading that page first.

---

## 4. `ViewCompositionStrategy` — the #1 interop footgun

Decides **when the Composition is disposed**. Get it wrong and you get state loss, leaked
Compositions, or crashes after a Fragment's view is destroyed.

`[SRC]`: *"In a single-Activity Compose-only app, this default behavior is what you would want,
however, if you are incrementally adding Compose in your codebase, this behavior may cause state
loss in some scenarios."*

| Strategy | Use for |
| --- | --- |
| `DisposeOnDetachedFromWindowOrReleasedFromPool` | **The default.** General use and pooling containers (`RecyclerView`) |
| `DisposeOnViewTreeLifecycleDestroyed` | **Fragments**, when the Lifecycle isn't known yet — the recommended one for Fragment interop |
| `DisposeOnLifecycleDestroyed` | A Fragment (or other host) with a known `Lifecycle` |
| `DisposeOnDetachedFromWindow` | Legacy; superseded by the pooling-aware default |

**Decision rules `[DERIVED]`:**

1. **`ComposeView` inside a Fragment → `DisposeOnViewTreeLifecycleDestroyed`. Always.** A Fragment's
   view can be detached from the window while the Fragment is still alive (backstack, `ViewPager2`
   off-screen pages). With the default, the Composition is disposed on detach and all remembered
   state is lost; the user returns to a reset screen. This is the bug people spend a day on.
2. **`ComposeView` inside a `RecyclerView` `ViewHolder` → keep the default.** It is explicitly
   pool-aware; overriding it here is how you leak a Composition per recycled row.
3. **`ComposeView` in a plain Activity layout → keep the default.**
4. Only reach for `DisposeOnLifecycleDestroyed` when you are handing it a specific `Lifecycle`
   deliberately — e.g. binding to `viewLifecycleOwner.lifecycle` explicitly.

Set it **before** `setContent`:

```kotlin
composeView.apply {
    setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
    setContent { AppTheme { /* … */ } }
}
```

---

## 5. `RecyclerView` + `ComposeView`

**Version floor, verbatim `[SRC]`:** *"Composables in `RecyclerView` are performant since
`RecyclerView` version 1.3.0-alpha02. Make sure you are on at least version 1.3.0-alpha02."*

Below that floor, one `ComposeView` per row is a genuine performance trap — you pay full Composition
setup per bind instead of reusing pooled Compositions. Check the resolved version first:
`./gradlew :app:dependencies --configuration releaseRuntimeClasspath | grep recyclerview`.

```kotlin
class ComposeItemViewHolder(
    private val composeView: ComposeView,
) : RecyclerView.ViewHolder(composeView) {

    init {
        // Pool-aware default. Do NOT swap this for the Fragment strategy here.
        composeView.setViewCompositionStrategy(
            ViewCompositionStrategy.DisposeOnDetachedFromWindowOrReleasedFromPool
        )
    }

    fun bind(item: Item, onClick: (Item) -> Unit) {
        composeView.setContent {
            AppTheme { ItemRow(item = item, onClick = { onClick(item) }) }
        }
    }
}

// onCreateViewHolder: ComposeItemViewHolder(ComposeView(parent.context))
// onBindViewHolder:   holder.bind(getItem(position), onClick)
```

`[DERIVED]` Treat `ComposeView`-per-row as a **transitional** state, not a destination. A list is
usually the highest-value thing to convert wholesale to `LazyColumn` — you delete the adapter,
the `DiffUtil` callback, the ViewHolder, and the layout file in one move, and you get Expressive
list shapes and motion that the hybrid cannot express.

---

## 6. Incremental migration strategies

| Strategy | What it is | Best for | Cost |
| --- | --- | --- | --- |
| **New-features-only** | Freeze XML; every new screen is Compose behind a `ComposeView`. Never touch existing screens. | Every team, always. This is the floor, not a strategy you choose *instead of* others. | ~Zero. Stops the migration bill from growing. |
| **Screen-by-screen** (top-down) | Replace one whole destination at a time. Fragment keeps existing as a nav host; its content becomes one composable. | The default for real migration. Screens are natural units of test coverage and review. | Medium. Each screen is a self-contained, shippable, revertible PR. |
| **Leaf-first** (bottom-up) | Migrate individual UI elements inside a screen that must stay mixed. Work upward until the screen is entirely Compose, then delete the XML. | Screens too large/critical to convert in one PR; screens with complicated state you don't want to move at once. | Highest per-screen. Many `ComposeView` islands, each needing theme + strategy + insets care. Maximum boundary count = maximum pitfall exposure. |
| **Root-first** (`AndroidViewBinding`) | Convert the screen's frame to Compose, keep the interior XML inside `AndroidViewBinding`, hollow it out later. | Screens with a simple frame and a hairy interior (charts, camera, maps, legacy custom views). | Low up-front, but leaves an XML core that still needs doing. |

Official ordering guidance `[SRC]`: start with **low-complexity, low-state screens** — welcome,
confirmation, settings, static content. For screens that must stay mixed, use the **bottom-up**
approach: migrate leaf UI elements piece by piece until the whole screen is Compose.

`[DERIVED]` The failure mode to avoid is *breadth-first* — sprinkling small `ComposeView` islands
across many screens at once. It maximizes the number of live boundaries (theme, insets, strategy,
state ownership) while completing nothing, so you carry every interop tax with none of the payoff.
Finish screens.

---

## 7. Theme bridging — the practical core

### 7.1 The problem, stated by Google `[SRC]`

> "When you introduce Compose in an existing app, you need to migrate your Material XML themes to
> use `MaterialTheme` for Compose components. This means your app's theming will have two sources of
> truth: the View-based theme and the Compose theme. Any changes to your styling need to be made in
> multiple places."

**There is no supported runtime bridge.** You maintain two theme definitions and keep them
numerically identical until the XML is gone. Plan for this as ongoing maintenance, not a one-time
task.

### 7.2 Theme adapters are dead — do not recommend one

- **`com.google.accompanist:accompanist-themeadapter-material3` — DEPRECATED.** Verbatim `[SRC]`:
  *"This library is deprecated, and the API is no longer maintained."* It redirects to: "Use the
  Material Theme Builder tool, or an alternative design tool, to generate a matching XML and Compose
  theme implementation." No sunset date given.
- **`material-components-android-compose-theme-adapter`** (the original MDC adapter) was superseded
  by the Accompanist adapters, which are themselves now deprecated. Its GitHub repo returned **404**;
  exact current state `[UNVERIFIED]`. Either way it is off the recommended path.
- **The tell `[DERIVED]`:** none of the current official migration pages
  (`migrate/other-considerations`, `designsystems/migrate-xml-theme-to-compose`, `migrate/strategy`)
  mention any theme adapter at all. They have been written out of the story.

If a codebase already contains `MdcTheme { }` or `Mdc3Theme { }`, that is legacy code to remove
during migration, not a pattern to extend.

### 7.3 The supported approach: generate both sides from one source

**Material Theme Builder** (<https://m3.material.io/theme-builder>) is the recommended tool — it
emits a matching XML theme and a Compose theme from the same input `[SRC]`: *"You can use the
Material Theme Builder tool for migrating colors."*

Setup `[DERIVED]`: treat the **existing XML theme as the source of truth** while both exist (it is
what ships today and what QA signed off); feed its colors into Material Theme Builder; export and
commit both artifacts with a "generated, do not hand-edit" header; enforce a review rule that any
color/type/shape change is a two-file diff or it is rejected.

### 7.4 The validation rule — quote this at code review `[SRC]`

> "Always use the existing theme values from the original XML theme as the source of truth for the
> new Material Theme in Compose. Never invent new theme values during migration, to maintain brand
> consistency and avoid visual regressions.
>
> Verify all new Compose theme values match the existing XML values. Don't hardcode any migrated
> values."

"Don't hardcode" means: in Compose read `MaterialTheme.colorScheme.primary`, never a `Color(0xFF…)`
literal at a call site. The literals live in the generated `Color.kt` only.

### 7.5 The six official steps `[SRC]`

1. **Evaluate the design system** — which system the XML project uses, and the path to M3 in Compose.
2. **Identify theme source files** — light/dark colors with qualifiers, themes, shapes, dimens,
   typography, styles. Strings and other resources carry over untouched. The conceptual shift: *"In
   XML you write `?attr/colorPrimary`. In Compose, you access theme values with `MaterialTheme.*`."*
3. **Migrate colors** — stop naming colors by hex, name by semantic role. M3 role names differ from
   M2; do not assume a rename is a no-op.
4. **Migrate shapes and typography** — build `Shapes(...)` and `Typography(...)`, pass to the theme.
5. **Migrate `styles.xml`** — XML styles have **no direct Compose equivalent**. Do not inline them;
   wrap each style as a named `@Composable` (§7.9).
6. **Validate** — apply the rule in §7.4.

### 7.6 Color mapping — XML attribute → `ColorScheme` role

From the official guide `[SRC]`:

| XML theme attribute | Compose `MaterialTheme.colorScheme.*` |
| --- | --- |
| `colorPrimary` | `primary` |
| `colorOnPrimary` | `onPrimary` |
| `colorPrimaryDark` / `colorPrimaryVariant` | `primaryContainer` **or** `secondary` — judgement call, look at what it is actually used for |
| `colorAccent` | `secondary` **or** `tertiary` |
| `android:colorBackground` | `background` |
| `colorSurface` | `surface` |
| `colorOnSurface` | `onSurface` |
| `colorSurfaceVariant` | `surfaceVariant` |
| `colorOnSurfaceVariant` | `onSurfaceVariant` |
| `colorError` | `error` |
| `colorOnError` | `onError` |
| `colorOutline` | `outline` |

Notes `[DERIVED]`:

- The two ambiguous rows (`colorPrimaryDark`/`colorPrimaryVariant`, `colorAccent`) are M2→M3
  semantic changes, not renames. Resolve them by auditing usage: a "darker header" use maps to
  `primaryContainer`; a "second accent" use maps to `secondary` or `tertiary`.
- **This table is not complete.** M3 defines 48 color roles (see
  `../../m3-expressive-theming/references/color.md`), including the whole `surfaceContainer*` tonal
  ramp, `outlineVariant`, `inverseSurface`, `scrim`, and the 12 `*Fixed` roles. MDC ships more
  `?attr/color*` names than the official mapping table lists. For any attribute not in the table
  above, confirm the exact MDC attribute name against the MDC `attrs.xml` rather than guessing —
  guessing an `?attr/` name produces a resource-not-found at inflate time, not a compile error.
- Dark theme: XML uses `values-night/` qualifiers, Compose uses a runtime branch
  (`isSystemInDarkTheme()`). Both sides must be migrated; a light-only Compose theme inside a
  dark-capable app is a common half-migration bug.

### 7.7 Typography mapping — `TextAppearance` → `Typography`

`TextAppearance.Material3.<Role>` maps **1:1** onto the 15 Compose roles `[SRC]`:

| XML `TextAppearance.Material3.*` | Compose `Typography` slot |
| --- | --- |
| `DisplayLarge` / `DisplayMedium` / `DisplaySmall` | `displayLarge` / `displayMedium` / `displaySmall` |
| `HeadlineLarge` / `HeadlineMedium` / `HeadlineSmall` | `headlineLarge` / `headlineMedium` / `headlineSmall` |
| `TitleLarge` / `TitleMedium` / `TitleSmall` | `titleLarge` / `titleMedium` / `titleSmall` |
| `BodyLarge` / `BodyMedium` / `BodySmall` | `bodyLarge` / `bodyMedium` / `bodySmall` |
| `LabelLarge` / `LabelMedium` / `LabelSmall` | `labelLarge` / `labelMedium` / `labelSmall` |

Per-attribute: `android:textSize` → `fontSize = X.sp`; `android:lineHeight` → `lineHeight = X.sp`;
`android:letterSpacing` → `letterSpacing = X.sp`; `android:fontFamily` → `fontFamily =
FontFamily(...)`; `android:textFontWeight`/`android:textStyle` → `fontWeight = FontWeight.Medium`.
`android:textColor` has no `TextStyle` home in practice — color comes from the color role at the
call site.

**Trap `[SRC]`:** unlike M2, M3 `Typography` has **no `defaultFontFamily` parameter.** Set
`fontFamily` on each of the 15 `TextStyle`s.

**Emphasized scale `[DERIVED]`:** MDC 1.14.0 shipped an "Emphasized Typescale" for Views and Compose
`Typography` exposes 15 parallel `*Emphasized` properties — conceptually the same scale, but the
mapping between individual XML emphasized text appearances and the Compose properties is
`[UNVERIFIED]`. Compose side: `../../m3-expressive-theming/references/typography-and-variable-fonts.md`.

### 7.8 Shape mapping — `ShapeAppearance` → `Shapes`

| XML | Compose |
| --- | --- |
| `ShapeAppearance.*.SmallComponent` | `Shapes(small = RoundedCornerShape(X.dp))` |
| `ShapeAppearance.*.MediumComponent` | `Shapes(medium = RoundedCornerShape(X.dp))` |
| `ShapeAppearance.*.LargeComponent` | `Shapes(large = RoundedCornerShape(X.dp))` |
| `app:cornerFamily="rounded"` / `"cut"` | `RoundedCornerShape(...)` / `CutCornerShape(...)` |
| `app:cornerSize` / `cornerRadius` | `RoundedCornerShape(X.dp)` |

**Structural mismatch `[DERIVED]`:** the XML M3 shape system has three component sizes. The Compose
Expressive `Shapes` object has **eight** slots — `extraSmall`, `small`, `medium`, `large`,
`largeIncreased`, `extraLarge`, `extraLargeIncreased`, `extraExtraLarge`. The XML side cannot
express the Expressive scale's extra steps. During a half-migration, map the three XML sizes onto
`small`/`medium`/`large` and let the Compose-only screens use the full scale; accept that corner
radii will not be pixel-identical across the boundary on components that use the extra steps. Full
scale detail: `../../m3-expressive-theming/references/shape-scale.md`.

### 7.9 Structural / widget / attribute mapping `[SRC]`

| XML | Compose |
| --- | --- |
| `Theme.Material3.*` | `MaterialTheme(colorScheme, typography, shapes) { }` |
| `Widget.Material3.Button` | `Button(colors = ButtonDefaults.buttonColors(...))` |
| `Widget.Material3.CardView` | `Card(shape = …, elevation = …, colors = …)` |
| `Widget.*.TextInputLayout.OutlinedBox` | `OutlinedTextField(colors = OutlinedTextFieldDefaults.colors(...))` |
| `Widget.*.Chip.Filter` | `FilterChip(colors = FilterChipDefaults.filterChipColors(...))` |
| `Widget.*.Toolbar.Primary` | `TopAppBar(colors = TopAppBarDefaults.topAppBarColors(...))` |
| `Widget.*.FloatingActionButton` | `FloatingActionButton(containerColor = …)` |
| `backgroundTint` | `containerColor` in `ComponentDefaults.ComponentColors()` |
| `android:textColor` | `contentColor` in `ComponentDefaults.ComponentColors()` |
| `cornerRadius` | `shape = RoundedCornerShape(X.dp)` |
| `android:elevation` | `elevation = ComponentDefaults.elevation(defaultElevation = X.dp)` |
| `android:padding` | `contentPadding = PaddingValues(...)` or `Modifier.padding()` |
| `android:minHeight` | `Modifier.heightIn(min = X.dp)` |
| `strokeColor` + `strokeWidth` | `border = BorderStroke(width, color)` |
| `android:textSize` | `fontSize = X.sp` in `TextStyle` |

**`styles.xml` has no Compose equivalent** `[SRC]`. Do not inline a style's attributes at every call
site — wrap each style as a named composable, which is also step 2 of the official strategy:

```xml
<!-- Before -->
<Button style="@style/MyPrimaryButton" android:text="@string/save" />
```

```kotlin
// After — one definition, reused everywhere
@Composable
fun MyPrimaryButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    content: @Composable RowScope.() -> Unit,
) {
    Button(
        onClick = onClick,
        modifier = modifier.heightIn(min = 56.dp),
        enabled = enabled,
        shape = MaterialTheme.shapes.large,
        colors = ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
        ),
        content = content,
    )
}

// Call site
MyPrimaryButton(onClick = ::save) { Text(stringResource(R.string.save)) }
```

### 7.10 Getting `Theme.Material3Expressive.*` and `MaterialExpressiveTheme` to agree

The official guide only covers `Theme.Material3.*` → `MaterialTheme`. For the **Expressive** pairing:

| Axis | Bridges? | How |
| --- | --- | --- |
| **Color** | **Yes, cleanly** | Same M3 role names both sides. §7.6 tables apply unchanged. |
| **Typography** | **Yes, cleanly** | Same 15-role scale; the emphasized typescale exists on both sides (MDC 1.14.0 / Compose `*Emphasized`). |
| **Static shape** | **Mostly** | Three XML sizes vs eight Compose slots — see §7.8. Parity achievable on the three shared steps. |
| **Motion** | **No. Not at all.** | See §8. |
| **Shape morphing** | **No** | `MaterialShapes` morphing is Compose-only. |

Requirements: XML side needs **MDC 1.14.0+** (`minSdk 23`) for `Theme.Material3Expressive.*` and
`Widget.Material3Expressive.*`; Compose side is `MaterialExpressiveTheme(colorScheme, motionScheme,
shapes, typography)`, currently behind `@OptIn(ExperimentalMaterial3ExpressiveApi::class)`.

`[UNVERIFIED]` — whether Material Theme Builder emits an Expressive-flavored **XML** theme
(`Theme.Material3Expressive.*`) alongside the Compose `MaterialExpressiveTheme` could not be
confirmed (the tool is on the same JS-only SPA). Assume you may need to hand-write the XML expressive
theme parent and use the tool only for color/type resources.

Concrete dual definition — one set of values, two consumers:

```xml
<!-- res/values/themes.xml — XML side, MDC 1.14.0+.
     Pick the exact Theme.Material3Expressive.* parent from MDC's themes.xml — only the
     namespace is verified here, not the individual parent names. -->
<style name="Theme.App" parent="Theme.Material3Expressive.[…]">
    <item name="colorPrimary">@color/md_theme_primary</item>
    <item name="colorOnPrimary">@color/md_theme_onPrimary</item>
    <item name="colorSurface">@color/md_theme_surface</item>
    <item name="colorOnSurface">@color/md_theme_onSurface</item>
    <!-- … generated, do not hand-edit … -->
</style>
```

```kotlin
// ui/theme/Theme.kt — Compose side, same values
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun AppTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialExpressiveTheme(
        colorScheme = if (darkTheme) AppDarkColorScheme else AppLightColorScheme,
        typography = AppTypography,
        shapes = AppShapes,
        motionScheme = MotionScheme.expressive(),   // no XML counterpart — see §8
        content = content,
    )
}
```

`[DERIVED]` Guard the invariant with a test rather than discipline. A screenshot test of one
representative component rendered both ways, or a unit test that reads the XML color resources and
asserts equality against the `ColorScheme`, catches drift the moment someone edits one side. Colors
are the easy case: `ContextCompat.getColor(context, R.color.md_theme_primary)` vs
`AppLightColorScheme.primary.toArgb()`.

---

## 8. What does NOT bridge: motion

**Say this plainly to any team doing a partial migration.**

`MotionScheme` has **no XML counterpart.** It is a theme-level contract — six spring specs (spatial
and effects × fast/default/slow) injected through `MaterialExpressiveTheme` and read automatically by
every Compose Material component. XML themes have no slot for animation specs; MDC bakes expressive
motion into individual widget styles, per widget, with no way to retheme it.

**Consequence:** a half-migrated app has **two motion feels.** Compose screens spring and settle;
XML screens use the platform interpolators MDC's styles were authored with. Color, type, and static
shape can be made pixel-identical across the boundary. Motion cannot. Design review will notice, and
they will be right.

Mitigations, best first `[DERIVED]`:

1. **Migrate at screen granularity, not element granularity.** Two adjacent elements on one screen
   animating differently is far more visible than two screens that feel different.
2. **Keep screen-to-screen transitions on one system.** While Fragment navigation owns transitions,
   all inter-screen motion is the Fragment/Transition system — consistent by construction. Do not mix
   Navigation Compose transitions with Fragment transitions mid-migration (§11).
3. **Do not hand-tune XML to match springs.** Interpolators cannot reproduce a spring's settle. You
   will land close-but-wrong, which reads worse than an honest difference.
4. **Sequence high-motion screens first** — onboarding, media player, anything with a hero
   transition — so they get the real thing early.
5. **Set the expectation with design up front**, as a known temporary state with a defined end.

Motion detail: `../../m3-expressive-motion/references/motion-scheme.md`.

---

## 9. Insets and edge-to-edge across the boundary

**Pitfall, verbatim `[SRC]`:** *"By default, each `ComposeView` consumes all insets at the
`WindowInsetsCompat` level of consumption. To change this default behavior, set
`ComposeView.consumeWindowInsets` to `false`."*

Failure signature `[DERIVED]`: a `ComposeView` nested inside a View hierarchy that also handles insets
— the island consumes them and sibling Views get zero insets, drawing under the system bars. Or the
reverse: both apply padding and you get double gaps.

**Rule: exactly one layer applies insets. Decide which, per screen.**

```kotlin
// Island inside a View layout that already handles insets → stop consuming.
binding.composeView.apply {
    consumeWindowInsets = false
    setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
    setContent { AppTheme { Section() } }
}

// ComposeView owns the whole screen → leave the default (true), handle insets in Compose
// with Scaffold's padding or Modifier.windowInsetsPadding.
```

Checklist for a migrating screen:

1. Is `enableEdgeToEdge()` called on the Activity? Edge-to-edge is the default on recent SDK targets;
   if the app never adopted it, migration is when it becomes visible.
2. Who consumes: the Activity's root View, a `CoordinatorLayout`/`AppBarLayout`, the `ComposeView`,
   or a Compose `Scaffold`? Pick one.
3. Set `consumeWindowInsets = false` on every `ComposeView` that is **not** the screen root.
4. Verify on a gesture-navigation device *and* a 3-button-navigation device, portrait and landscape,
   with the IME open.

---

## 10. ViewModel scoping across the boundary

`[SRC]`: *"You should access and call `ViewModel` instances at screen-level composables… You should
never pass down `ViewModel` instances to other composables."*

`[SRC]` warning: *"The same instance of a `ViewModel` type is used in all composables unless the
composable is a destination of the navigation graph or different activity or fragment instances."*

This bites specifically during migration `[DERIVED]`: if two Compose islands in the same Fragment
both call `viewModel()`, they get the **same instance** — sometimes what you want, often not. The
scope is the nearest `ViewModelStoreOwner`, which during migration is the Fragment or the Activity,
not "the screen".

Rules: obtain the `ViewModel` at the **screen-level composable** only, or better, in the **Fragment**
(`by viewModels()`) and pass it into `setContent` so ownership matches where the lifecycle lives.
Below that, pass state down and events up. Never pass a `ViewModel` into a leaf composable — leaves
take data and lambdas, which is what keeps them previewable and testable.

```kotlin
// Fragment owns the scope; the composable takes state + events
class CartFragment : Fragment() {
    private val viewModel: CartViewModel by viewModels()

    override fun onCreateView(i: LayoutInflater, c: ViewGroup?, s: Bundle?): View =
        ComposeView(requireContext()).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                AppTheme {
                    val state by viewModel.state.collectAsStateWithLifecycle()
                    CartScreen(state = state, onRemove = viewModel::remove)
                }
            }
        }
}
```

**Architecture prerequisite `[SRC]`:** *"Unidirectional Data Flow (UDF) architecture patterns work
seamlessly with Compose. If the app uses other types of architecture patterns instead, like Model
View Presenter (MVP), we recommend you migrate that part of the UI to UDF before or whilst adopting
Compose."* An MVP screen converted to Compose without moving to UDF produces a composable that is
hard to test and recomposes unpredictably. Do the UDF move first, in XML, where the tests already
pass.

---

## 11. Navigation during migration

**The sequencing rule, verbatim `[SRC]`:**

> "Until then, you should continue using Fragment-based Navigation component in your mixed View and
> Compose codebase."

> "The recommended end goal for a Compose-first architecture is to replace Fragments entirely with
> screen-level composables managed by Navigation Compose."

> "For hybrid apps with Views and Compose, that use Fragments, use `ComposeView` to wrap composables
> content and add to a Fragment during migration. For Compose-only apps, do not use Fragments and
> instead use the recommended Compose-only architecture with a single Activity and latest navigation
> libraries, like Navigation 3."

**Do not migrate navigation until every destination is a composable.** Half-migrated navigation is
the classic way to make a migration miserable: two back stacks, two transition systems, two sets of
argument plumbing, and a predictive-back implementation that works on half the app.

Phased plan `[DERIVED]`:

| Phase | Navigation | Screens |
| --- | --- | --- |
| 1 | Fragment + Navigation component (XML nav graph) | Mixed. New screens are Fragments returning `ComposeView`. |
| 2 | Unchanged | All destinations are now thin Fragments wrapping one composable each. |
| 3 | Swap once | Delete the Fragments and the nav graph in one change; single Activity + Compose navigation. |

Phase 2 → 3 is one large but mechanical change, and it is *safer* than dribbling destinations across
two navigation systems.

**Which Compose navigation to land on:** Navigation Compose (`androidx.navigation:navigation-compose`)
is the incumbent; Navigation3 (`androidx.navigation3`) is the newer back-stack-as-state model and is
what the Compose-only guidance points at. Jump straight to Navigation3 if you are doing phase 3 now
and want adaptive/list-detail scene strategies — note it is a **separate artifact group on a separate
version train** from `androidx.navigation`. Details:
`../../m3-expressive-navigation/references/adaptive-and-nav3.md`.

`[UNVERIFIED]` — `androidx.fragment:fragment-compose` offers a composable for hosting a Fragment
inside Compose (the reverse direction), useful in phase 2/3 for screens stuck on a Fragment-only SDK.
The artifact exists; its exact API was not confirmed here. Read the signature before using it.

---

## 12. The documented pitfalls — all eight

Source: `developer.android.com/develop/ui/compose/migrate/other-considerations` unless noted.

| # | Pitfall | Symptom | Fix |
| --- | --- | --- | --- |
| 1 | **Missing theme at the boundary** | Compose island renders baseline Material purple | Wrap every `setContent` / `Content()` in your `AppTheme` |
| 2 | **Duplicate `ComposeView` IDs** | State not restored after process death / config change | Give each `ComposeView` in a layout a unique `android:id` |
| 3 | **Insets double-consumption** | Content under system bars, or doubled padding | `composeView.consumeWindowInsets = false` on non-root islands |
| 4 | **`RecyclerView` below 1.3.0-alpha02** | Janky scrolling with `ComposeView` rows | Upgrade `androidx.recyclerview` to ≥ 1.3.0-alpha02 |
| 5 | **Architecture mismatch (MVP)** | Untestable composables, unpredictable recomposition | Move that UI to UDF before or during the Compose conversion |
| 6 | **ViewModel scoping** | Two islands unexpectedly share one ViewModel instance | Obtain at screen level only; never pass ViewModels down |
| 7 | **Ambiguous source of truth in mixed screens** | State desyncs between the XML half and the Compose half | Owner = whichever element is closer to the root; publish outward with `SideEffect` |
| 8 | **Wrong test rule** | Cannot drive Compose and View assertions in one test | `createAndroidComposeRule<MyActivity>()` instead of `ActivityScenarioRule` |

Verbatim, for the ones where wording matters:

- **2 `[SRC]`:** *"If there are multiple `ComposeView` elements in the same layout, each one must
  have a unique ID for `savedInstanceState` to work."* Note this bites only on state restoration, so
  it passes every happy-path test and fails in the field. Grep migrated layouts for `ComposeView`
  with no `android:id` as a matter of routine.
- **7 `[SRC]`:** *"The source of truth should be owned by whichever element is closer to the root of
  the UI hierarchy."* To push Compose state out to a non-Compose consumer:

  ```kotlin
  @Composable
  fun SearchSection(onQueryChanged: (String) -> Unit) {
      var query by rememberSaveable { mutableStateOf("") }
      SearchField(query, onValueChange = { query = it })

      // Publish the Compose-owned value outward to the View half of the screen.
      SideEffect { onQueryChanged(query) }
  }
  ```

  If instead the View half owns the value, drive Compose from it via a `MutableState` or a `Flow`
  the composable collects — but pick one direction per value and write it down.

---

## 13. The official migration tool

Google ships an agent/CLI migration skill `[SRC]`:

```
android skills add --skill migrate-xml-views-to-jetpack-compose
```

Referenced from the platform post `[SRC]`: *"Check out our XML to Compose migration skill to help you
convert existing layouts to Compose."*

`[DERIVED]` Use it for the mechanical bulk — layout translation, attribute mapping, style→composable
extraction. Then review its output for exactly the things it has no way to know about:
`ViewCompositionStrategy` on Fragment-hosted islands, insets consumption, ViewModel scope, and theme
parity against the XML source of truth. It converts layouts; it does not make architecture decisions.

---

## 14. Testing during migration

`[SRC]`: *"Rely on UI testing to ensure you are not introducing regressions during migration."*

**Rule swap `[SRC]`:** use `createAndroidComposeRule<MyActivity>()` rather than
`ActivityScenarioRule` — it *"integrates `ActivityScenarioRule` with a `ComposeTestRule` that lets
you test Compose and View code at the same time."*

```kotlin
class CheckoutScreenTest {
    @get:Rule val composeTestRule = createAndroidComposeRule<MainActivity>()

    @Test fun mixedScreen_showsTotal_andLegacyToolbarTitle() {
        composeTestRule.onNodeWithText("Total").assertIsDisplayed()
        composeTestRule.onNodeWithTag("checkout_button").performClick()

        // Espresso assertions against the surviving View half, same test
        onView(withId(R.id.toolbar)).check(matches(isDisplayed()))
    }
}
```

`[DERIVED]` Practical sequence per screen:

1. **Write or verify the UI test against the XML screen first**, before touching it. A test written
   after conversion tests the new implementation, not the old behavior — exactly the regression you
   were trying to catch.
2. Convert the screen, then re-run the *same* test. Espresso matchers on converted elements become
   Compose matchers; that rewrite is the diff to review carefully.
3. Add screenshot tests at the theme boundary (§7.10) to catch color/type/shape drift between the
   two theme definitions.
4. Test state restoration explicitly — pitfall 2 and the `ViewCompositionStrategy` bugs (§4) appear
   only on config change, process death, and backstack return.

Payoff to cite once complete `[SRC]`: faster development, reduced APK size, reduced build times (see
`developer.android.com/develop/ui/compose/migrate/compare-metrics`).

---

## 15. Team checklist — realistic order of operations

**Phase 0 — before writing any Compose**

- [ ] Upgrade `com.google.android.material` to **1.14.0** (`minSdk 23`). Delivers most of the
      Expressive refresh to the existing XML app — do it whether or not you migrate.
- [ ] Upgrade `androidx.recyclerview` to ≥ **1.3.0-alpha02** (pitfall 4).
- [ ] Add Compose BOM, `activity-compose`, `material3`, `ui-viewbinding`; enable
      `buildFeatures { compose = true; viewBinding = true }`.
- [ ] Move any MVP screens on the migration path to UDF **first, in XML** (pitfall 5).
- [ ] Verify UI test coverage on the screens you'll convert first — before converting.

**Phase 1 — the theme (once, properly)**

- [ ] Declare the **XML theme the source of truth**, in writing.
- [ ] Run Material Theme Builder from the existing XML colors; commit both generated files with a
      "generated, do not hand-edit" header.
- [ ] Build `AppTheme` as a `MaterialExpressiveTheme` wrapper, `motionScheme` included.
- [ ] Convert `styles.xml` entries to named composables (§7.9) — this **is** the shared component
      library from step 2 of the official strategy.
- [ ] Add the parity test (§7.10) and a review rule: styling changes are a two-file diff or rejected.

**Phase 2 — stop the bleeding (immediately, permanently)**

- [ ] Policy: **no new XML layouts.** Enforce in review; add a CI check if you can.
- [ ] New screens = Fragment returning `ComposeView` + `DisposeOnViewTreeLifecycleDestroyed` +
      `AppTheme`. Fragment-based Navigation unchanged.

**Phase 3 — convert (ongoing, opportunistic)**

- [ ] Start low-complexity, low-state: welcome, confirmation, settings, static content.
- [ ] Convert whole screens, not scattered elements. Finish screens.
- [ ] Convert lists to `LazyColumn` rather than leaving `ComposeView`-per-row in place.
- [ ] Convert opportunistically when product work already touches a screen; leave stable,
      rarely-touched screens alone.
- [ ] Per screen: strategy set, theme applied, insets owner decided, unique IDs, ViewModel at screen
      level, tests re-run, state restoration verified.

**Phase 4 — navigation (one change, at the end)**

- [ ] Only when **every** destination is a composable. Delete Fragments and the XML nav graph in one
      change; move to single-Activity Compose navigation (Navigation Compose, or Navigation3 for the
      adaptive scene strategies). Re-verify predictive back app-wide.

**Phase 5 — cleanup**

- [ ] Delete the XML theme, `styles.xml`, unused color/dimen resources, and any
      `MdcTheme`/`Mdc3Theme`/Accompanist theme-adapter usage (§7.2).
- [ ] Drop `com.google.android.material`, `ui-viewbinding`, and `viewBinding = true` once unused.
- [ ] Re-measure APK size and build time against the phase-0 baseline.

---

## 16. Sources

**Primary — Google.** All under `developer.android.com/develop/ui/compose/` unless noted:

- `migrate/migrate-xml-views-to-jetpack-compose` — entry point
- `migrate/strategy` — three-step strategy, screen ordering, Fragment/Navigation sequencing
- `migrate/interoperability-apis` and `migrate/compose-in-views` — `ComposeView`,
  `AbstractComposeView`, `AndroidView`, `AndroidViewBinding`, `ViewCompositionStrategy`
- `migrate/other-considerations` — the eight pitfalls
- `designsystems/migrate-xml-theme-to-compose` — the six theme steps and the mapping tables
- `designsystems/material3`; `migrate/compare-metrics`
- <https://google.github.io/accompanist/themeadapter-material3/> — deprecation notice
- <https://m3.material.io/theme-builder> — Material Theme Builder (JS SPA; not machine-readable)
- <https://android-developers.googleblog.com/2026/05/android-ui-development-is-compose-first.html> — the migration-skill reference

**Sibling references in this plugin**

- `compose-first-status.md` — announcement, MDC status, Expressive-in-Views inventory, decision guidance
- `../../m3-expressive-theming/references/` — `color.md` (48 roles), `shape-scale.md` (8 slots),
  `typography-and-variable-fonts.md` (15 + 15 roles)
- `../../m3-expressive-motion/references/motion-scheme.md` — `MotionScheme`, spring values
- `../../m3-expressive-navigation/references/adaptive-and-nav3.md` — Navigation3 and adaptive layouts

---

## 17. UNVERIFIED index

- `AndroidView`'s exact parameter list including `onReset` / `onRelease` across Compose versions —
  read the signature in your pinned `compose-ui`.
- Whether Material Theme Builder emits an Expressive-flavored **XML** theme
  (`Theme.Material3Expressive.*`), not just the Compose `MaterialExpressiveTheme`.
- Mapping between individual MDC 1.14.0 emphasized text appearances and Compose's 15 `*Emphasized`
  `Typography` properties.
- `androidx.fragment:fragment-compose` API for hosting a Fragment inside Compose.
- MDC `?attr/color*` names beyond the 12 in the official mapping table — confirm against MDC
  `attrs.xml` before using one.
- Per-API experimental status in `androidx.compose.material3` — moves release to release; check
  <https://developer.android.com/jetpack/androidx/releases/compose-material3> at time of use.

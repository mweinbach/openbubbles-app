# Pane navigation and pane expansion

`ThreePaneScaffoldNavigator`, `remember*PaneScaffoldNavigator`, `BackNavigationBehavior`,
`Navigable*PaneScaffold`, `ThreePaneScaffoldPredictiveBackHandler`, `ThreePaneScaffoldState`, and the
whole pane-expansion (drag-to-resize) surface.

Roles, directives, adapt strategies, `AnimatedPane`, scopes → `pane-scaffolds.md`.

**Target:** `androidx.compose.material3.adaptive:*` **1.3.0 stable** (2026-08-12);
`androidx.compose.material3:material3` **1.5.0-alpha26**.
`diff adaptive-navigation/api/1.3.0-rc01.txt … /current.txt` is **empty** — everything in §1–§9 is
exactly 1.3.0 with no HEAD drift.

| Tag | Meaning |
| --- | --- |
| `[API-1.3.0]` | Verbatim from `api/1.3.0-rc01.txt` — byte-identical to shipped 1.3.0 |
| `[SRC@HEAD]` | Verbatim Kotlin from androidx-main HEAD (`360e8cba7ae6fa4fe8059d993f75faefb32f51b8`, 2026-08-14) |
| `[DOC]` | developer.android.com, fetched 2026-08-14 |
| `[REPO]` | Verbatim from a cloned repo, path given |
| `UNVERIFIED` | Stated but not confirmed against a primary source |

```kotlin
implementation("androidx.compose.material3.adaptive:adaptive-navigation:1.3.0")
// transitively needs androidx.activity:activity-compose for PredictiveBackHandler
```
Everything here is `@ExperimentalMaterial3AdaptiveApi` unless stated. KDoc inside code blocks is
elided with `...` where prose covers it; signatures and implementation bodies are verbatim.

---

## 1. Pick your wiring

| Approach | Use when | You must also… |
| --- | --- | --- |
| **`NavigableListDetailPaneScaffold` / `NavigableSupportingPaneScaffold`** (§7) | Default. Pane navigation is self-contained inside one screen. | Nothing — predictive back is wired for you |
| **`ListDetailPaneScaffold` + navigator** (§8) | You need to place other effects around the scaffold, or use the `value` overload | Add `ThreePaneScaffoldPredictiveBackHandler` yourself |
| **`MutableThreePaneScaffoldState` directly, no navigator** (§9) | You already own a back stack and just want the scaffold to render/animate a computed value | Drive `snapTo`/`animateTo`/`seekTo` yourself, incl. back |
| **Navigation 3 `ListDetailSceneStrategy`** | The panes *are* back-stack entries, shared across the app | See `navigation-suite.md` (§16) |

The androidx guidance is explicit `[SRC@HEAD ThreePaneScaffoldNavigator.kt]`:
> *"In general, we suggest you to use `[rememberListDetailPaneScaffoldNavigator]` or
> `[rememberSupportingPaneScaffoldNavigator]` to get remembered default instances of this interface
> … Those default implementations work independently from any navigation frameworks. If you need to
> integrate with existing navigation frameworks or implement your own custom navigation logic,
> usually creating whole new APIs that's tailored for your own solution will be recommended, instead
> of implementing this interface."*

**Do not implement `ThreePaneScaffoldNavigator` yourself.** Read it as a spec; build your own API
around your own state.

---

## 2. `ThreePaneScaffoldNavigator<T>` — the full surface

`[SRC@HEAD ThreePaneScaffoldNavigator.kt]` — complete interface, verbatim:
```kotlin
/**
 * ...
 * @param T the type representing the content key/id for a navigation destination. This type must be
 *   storable in a Bundle. Used to customize navigation behavior (for example,
 *   [BackNavigationBehavior]). If this customization is unneeded, you can pass [Any].
 */
@ExperimentalMaterial3AdaptiveApi
@Stable
public interface ThreePaneScaffoldNavigator<T> {
    /**
     * The current layout directives that the associated three pane scaffold needs to follow. It's
     * supposed to be automatically updated when the window configuration changes.
     */
    public val scaffoldDirective: PaneScaffoldDirective

    /**
     * The current state of the associated three pane scaffold, used to query the transition between
     * layout states.
     */
    public val scaffoldState: ThreePaneScaffoldState

    /**
     * The current layout value of the associated three pane scaffold, which represents unique
     * layout states of the scaffold.
     */
    public val scaffoldValue: ThreePaneScaffoldValue

    /**
     * Returns the scaffold value associated with the previous destination, assuming there is a
     * previous destination to navigate back to. If not, this is the same as [scaffoldValue].
     *
     * @param backNavigationBehavior the behavior describing which backstack entries may be skipped
     *   during the back navigation. See [BackNavigationBehavior].
     */
    public fun peekPreviousScaffoldValue(
        backNavigationBehavior: BackNavigationBehavior =
            BackNavigationBehavior.PopUntilScaffoldValueChange
    ): ThreePaneScaffoldValue

    /**
     * The current destination as tracked by the navigator.
     *
     * Implementors of this interface should ensure this value is updated whenever a navigation
     * operation is performed.
     */
    public val currentDestination: ThreePaneScaffoldDestinationItem<T>?

    /**
     * Indicates if the navigator should be aware of pane destination history when deciding the
     * result [ThreePaneScaffoldValue] by a navigation operation. If the value is `false`, only the
     * current destination will be considered in the scaffold value calculation.
     *
     * @see calculateThreePaneScaffoldValue for more detailed explanation about history awareness.
     */
    public var isDestinationHistoryAware: Boolean

    /**
     * Navigates to a new destination, possibly with an animation, and suspends until the animation
     * is complete. The new destination is supposed to have the highest priority when calculating
     * the new [scaffoldValue].
     * ...
     * @param pane the new destination pane.
     * @param contentKey the optional key or id representing the content of the new destination.
     */
    public suspend fun navigateTo(pane: ThreePaneScaffoldRole, contentKey: T? = null)

    /**
     * Returns `true` if there is a previous destination to navigate back to.
     *
     * Implementors of this interface should ensure the logic of this function is consistent with
     * [navigateBack].
     */
    public fun canNavigateBack(
        backNavigationBehavior: BackNavigationBehavior =
            BackNavigationBehavior.PopUntilScaffoldValueChange
    ): Boolean

    /**
     * Navigates to the previous destination, possibly with an animation, and suspends until the
     * animation is complete. Returns `true` if there is a previous destination to navigate back to.
     */
    public suspend fun navigateBack(
        backNavigationBehavior: BackNavigationBehavior =
            BackNavigationBehavior.PopUntilScaffoldValueChange
    ): Boolean

    /**
     * Seeks the [scaffoldState] transition to the previous destination, as in a predictive back
     * animation.
     *
     * This does not affect the current [scaffoldValue] or backstack. To do so, call [navigateBack]
     * when the back navigation action is finalized.
     *
     * @param backNavigationBehavior the behavior describing which backstack entries may be skipped
     *   during the back navigation. See [BackNavigationBehavior].
     * @param fraction the progress fraction of the transition of backwards navigation.
     */
    public suspend fun seekBack(
        backNavigationBehavior: BackNavigationBehavior =
            BackNavigationBehavior.PopUntilScaffoldValueChange,
        @FloatRange(from = 0.0, to = 1.0) fraction: Float = 1.0f,
    )
}
```

### Member by member

| Member | What it gives you | Where it goes |
| --- | --- | --- |
| `scaffoldDirective: PaneScaffoldDirective` | Auto-updated on window config change | → `ListDetailPaneScaffold(directive = …)` |
| `scaffoldState: ThreePaneScaffoldState` | The seekable transition between values | → `ListDetailPaneScaffold(scaffoldState = …)` — **use this, not `value`**, for animation |
| `scaffoldValue: ThreePaneScaffoldValue` | Current resolved per-pane adapted values | Read `scaffoldValue[role]`; also a `PaneExpansionStateKeyProvider` (§12) |
| `currentDestination: ThreePaneScaffoldDestinationItem<T>?` | The active pane + its content key | `navigator.currentDestination?.contentKey` is the canonical "what is selected" read |
| `isDestinationHistoryAware: Boolean` | **`var`** — flips which `calculateThreePaneScaffoldValue` overload is used | Default `true`. `false` ⇒ only the current destination is considered |
| `navigateTo(pane, contentKey)` | **`suspend`** | `scope.launch { navigator.navigateTo(ListDetailPaneScaffoldRole.Detail, key) }` |
| `navigateBack(behavior)` | **`suspend`**, returns `Boolean` | `scope.launch { navigator.navigateBack() }` |
| `canNavigateBack(behavior)` | Not suspend | Gate a back button / a `BackHandler` |
| `peekPreviousScaffoldValue(behavior)` | The value you would land on | Preview a back result without committing |
| `seekBack(behavior, fraction)` | **`suspend`**, drives predictive back | Normally called for you by `ThreePaneScaffoldPredictiveBackHandler` |

### Critical API-shape facts

- **`navigateTo` / `navigateBack` / `seekBack` are `suspend`.** Wrap in
  `rememberCoroutineScope().launch { … }`. This changed in the 1.1/1.2 era — older tutorials showing
  direct calls will not compile.
- `navigateBack` returns `Boolean` (`true` if it actually went back).
- **Default `backNavigationBehavior` everywhere is `PopUntilScaffoldValueChange`.**
- `seekBack`'s default `fraction` is `1.0f`.
- **`T` must be Bundle-storable** (`Parcelable`, primitive, `String`, `Serializable`…) because the
  navigator is `rememberSaveable`. Pass `Any` if you do not need typed content keys.
- `contentKey` on `navigateTo` is **nullable** and defaults to `null`.

Typical read pattern `[SRC@HEAD samples]`:
```kotlin
val selectedItem = scaffoldNavigator.currentDestination?.contentKey

@OptIn(ExperimentalMaterial3AdaptiveApi::class)
private fun ThreePaneScaffoldNavigator<*>.isExpanded(role: ThreePaneScaffoldRole) =
    scaffoldValue[role] == PaneAdaptedValue.Expanded
```

---

## 3. `ThreePaneScaffoldDestinationItem<T>`

`[SRC@HEAD]`
```kotlin
/**
 * An item representing a navigation destination in a [ThreePaneScaffold].
 *
 * @param pane the pane destination of the navigation.
 * @param contentKey the optional key or id representing the content of the destination. The type
 *   [T] must be storable in a Bundle.
 */
@ExperimentalMaterial3AdaptiveApi
public class ThreePaneScaffoldDestinationItem<out T>(
    public val pane: ThreePaneScaffoldRole,
    public val contentKey: T? = null,
)
```
`out T`. Structural `equals`/`hashCode`; `toString()` =
`"ThreePaneScaffoldDestinationItem(pane=$pane, contentKey=$contentKey)"`.

Structural equality matters: `BackNavigationBehavior.PopUntilContentChange` compares `contentKey`s,
so a **data class** content key behaves correctly while an identity-equality class does not.

Content-key type from the androidx sample `[SRC@HEAD samples/ThreePaneScaffoldSample.kt]`:
```kotlin
private data class NavItemData(val index: Int, val showExtra: Boolean = false) : Parcelable {
    constructor(parcel: Parcel) : this(parcel.readInt(), parcel.readBoolean())

    override fun describeContents(): Int = 0

    override fun writeToParcel(dest: Parcel, flags: Int) {
        dest.writeInt(index)
        dest.writeBoolean(showExtra)
    }

    companion object CREATOR : Parcelable.Creator<NavItemData?> {
        override fun createFromParcel(source: Parcel) = NavItemData(source)
        override fun newArray(size: Int): Array<NavItemData?> = arrayOfNulls(size)
    }
}
```
The hand-rolled `Parcelable` is only because androidx samples cannot use `kotlin-parcelize`.
**In your app use `@Parcelize`** — the developer.android.com guide recommends exactly that `[DOC]`.

---

## 4. `remember*PaneScaffoldNavigator` — the overloads

`[API-1.3.0]` lists **four entries per function**, of which **two are Kotlin-callable**
(`@KotlinOnly`) and two are the `@BytecodeOnly` Composer-lowered forms of the same two. So there are
**4 overloads across the pair that you can actually call**, 2 per scaffold.

`[SRC@HEAD ThreePaneScaffoldNavigator.kt]` — list-detail, both callable forms:
```kotlin
/**
 * Returns a remembered default implementation of [ThreePaneScaffoldNavigator] for
 * [ListDetailPaneScaffold], which will be updated automatically when the input values change. The
 * default navigator is supposed to be used independently from any navigation frameworks and handles
 * the navigation purely inside the [ListDetailPaneScaffold].
 *
 * @param T the type representing the content key/id for a navigation destination. This type must be
 *   storable in a Bundle. ...
 * @param scaffoldDirective the current layout directives to follow. The default value will be
 *   calculated with [calculatePaneScaffoldDirective] using
 *   [WindowAdaptiveInfo][androidx.compose.material3.adaptive.WindowAdaptiveInfo] retrieved from the
 *   current context.
 * @param adaptStrategies adaptation strategies of each pane.
 * @param isDestinationHistoryAware `true` if the scaffold value calculation should be aware of the
 *   full destination history, instead of just the current destination. ...
 * @param initialDestinationHistory the initial pane destination history of the scaffold, by default
 *   it will be just the list pane.
 */
@ExperimentalMaterial3AdaptiveApi
@Composable
public fun <T> rememberListDetailPaneScaffoldNavigator(
    scaffoldDirective: PaneScaffoldDirective =
        calculatePaneScaffoldDirective(currentWindowAdaptiveInfoV2()),
    adaptStrategies: ThreePaneScaffoldAdaptStrategies =
        ListDetailPaneScaffoldDefaults.adaptStrategies(),
    isDestinationHistoryAware: Boolean = true,
    initialDestinationHistory: List<ThreePaneScaffoldDestinationItem<T>> =
        DefaultListDetailPaneHistory,
): ThreePaneScaffoldNavigator<T>

@ExperimentalMaterial3AdaptiveApi
@Composable
@Suppress("DEPRECATION") // TODO (conradchen): deprecate this and support V2 of it
public fun rememberListDetailPaneScaffoldNavigator(
    scaffoldDirective: PaneScaffoldDirective =
        calculatePaneScaffoldDirective(currentWindowAdaptiveInfoV2()),
    adaptStrategies: ThreePaneScaffoldAdaptStrategies =
        ListDetailPaneScaffoldDefaults.adaptStrategies(),
    isDestinationHistoryAware: Boolean = true,
): ThreePaneScaffoldNavigator<Any>
```

`[SRC@HEAD:273 / :306]` — supporting-pane, structurally identical with
`SupportingPaneScaffoldDefaults.adaptStrategies()` and `DefaultSupportingPaneHistory`:
```kotlin
@ExperimentalMaterial3AdaptiveApi
@Composable
public fun <T> rememberSupportingPaneScaffoldNavigator(
    scaffoldDirective: PaneScaffoldDirective =
        calculatePaneScaffoldDirective(currentWindowAdaptiveInfoV2()),
    adaptStrategies: ThreePaneScaffoldAdaptStrategies =
        SupportingPaneScaffoldDefaults.adaptStrategies(),
    isDestinationHistoryAware: Boolean = true,
    initialDestinationHistory: List<ThreePaneScaffoldDestinationItem<T>> =
        DefaultSupportingPaneHistory,
): ThreePaneScaffoldNavigator<T>

@ExperimentalMaterial3AdaptiveApi
@Composable
public fun rememberSupportingPaneScaffoldNavigator(
    scaffoldDirective: PaneScaffoldDirective =
        calculatePaneScaffoldDirective(currentWindowAdaptiveInfoV2()),
    adaptStrategies: ThreePaneScaffoldAdaptStrategies =
        SupportingPaneScaffoldDefaults.adaptStrategies(),
    isDestinationHistoryAware: Boolean = true,
): ThreePaneScaffoldNavigator<Any>
```

Initial histories `[SRC@HEAD]`:
```kotlin
@OptIn(ExperimentalMaterial3AdaptiveApi::class)
private val DefaultListDetailPaneHistory: List<ThreePaneScaffoldDestinationItem<Nothing>> =
    listOf(ThreePaneScaffoldDestinationItem(ListDetailPaneScaffoldRole.List))

@OptIn(ExperimentalMaterial3AdaptiveApi::class)
private val DefaultSupportingPaneHistory: List<ThreePaneScaffoldDestinationItem<Nothing>> =
    listOf(ThreePaneScaffoldDestinationItem(SupportingPaneScaffoldRole.Main))
```
List-detail starts on the **List** pane; supporting-pane starts on the **Main** pane.

### Which overload to use

| Overload | Use when |
| --- | --- |
| **Typed `<T>` (4 params)** | Almost always. You get a real content key on `currentDestination?.contentKey`, and `BackNavigationBehavior.PopUntilContentChange` becomes meaningful. Also the only way to seed `initialDestinationHistory` (deep links, "restore last opened item"). |
| **Untyped (`<Any>`, 3 params)** | Only when panes carry no per-item identity — a supporting-pane screen with a single fixed supporting view. `PopUntilContentChange` degenerates. |

**The `scaffoldDirective` default is already
`calculatePaneScaffoldDirective(currentWindowAdaptiveInfoV2())`** — Large/XL-aware, single-pane at
medium width. You do not need to pass it unless you are customising (e.g. a 0dp gutter, see
`pane-scaffolds.md` §7).

Deep-link seeding:
```kotlin
val navigator = rememberListDetailPaneScaffoldNavigator<MyKey>(
    initialDestinationHistory = if (deepLinkedItem != null) {
        listOf(
            ThreePaneScaffoldDestinationItem(ListDetailPaneScaffoldRole.List),
            ThreePaneScaffoldDestinationItem(ListDetailPaneScaffoldRole.Detail, deepLinkedItem),
        )
    } else {
        listOf(ThreePaneScaffoldDestinationItem(ListDetailPaneScaffoldRole.List))
    }
)
```
Seed both entries, not just the detail one, or back has nowhere to go.

### The shared implementation `[SRC@HEAD]`

```kotlin
@ExperimentalMaterial3AdaptiveApi
@Composable
internal fun <T> rememberThreePaneScaffoldNavigator(
    scaffoldDirective: PaneScaffoldDirective,
    adaptStrategies: ThreePaneScaffoldAdaptStrategies,
    isDestinationHistoryAware: Boolean,
    initialDestinationHistory: List<ThreePaneScaffoldDestinationItem<T>>,
): ThreePaneScaffoldNavigator<T> {
    val navigator =
        rememberSaveable(
                saver =
                    DefaultThreePaneScaffoldNavigator.saver(
                        scaffoldDirective,
                        adaptStrategies,
                        isDestinationHistoryAware,
                    )
            ) {
                DefaultThreePaneScaffoldNavigator(
                    initialDestinationHistory = initialDestinationHistory,
                    initialScaffoldDirective = scaffoldDirective,
                    initialAdaptStrategies = adaptStrategies,
                    initialIsDestinationHistoryAware = isDestinationHistoryAware,
                )
            }
            .apply {
                this.scaffoldDirective = scaffoldDirective
                this.adaptStrategies = adaptStrategies
                this.isDestinationHistoryAware = isDestinationHistoryAware
            }

    LaunchedEffect(scaffoldDirective, adaptStrategies, isDestinationHistoryAware) {
        val targetValue = navigator.scaffoldValue
        if (navigator.scaffoldState.targetState != targetValue) {
            navigator.scaffoldState.snapTo(targetValue)
        }
    }
    return navigator
}
```

Three behaviours to note:
1. **`rememberSaveable`** — destination history survives config change *and* process death, hence the
   Bundle-storable requirement on `T`.
2. `initialDestinationHistory` is only read on **first** creation. Changing it later does nothing; the
   restored history wins.
3. The `LaunchedEffect` **snaps** (no animation) when the directive, strategies or history-awareness
   change — so a rotation or fold re-lays out instantly rather than animating.

---

## 5. `DefaultThreePaneScaffoldNavigator` — the reference implementation

Not public, but its behaviour **is** the contract. `[SRC@HEAD]`
```kotlin
@OptIn(ExperimentalMaterial3AdaptiveApi::class)
internal class DefaultThreePaneScaffoldNavigator<T>(
    initialDestinationHistory: List<ThreePaneScaffoldDestinationItem<T>>,
    initialScaffoldDirective: PaneScaffoldDirective,
    initialAdaptStrategies: ThreePaneScaffoldAdaptStrategies,
    initialIsDestinationHistoryAware: Boolean,
) : ThreePaneScaffoldNavigator<T> {

    private val destinationHistory =
        mutableStateListOf<ThreePaneScaffoldDestinationItem<T>>().apply {
            addAll(initialDestinationHistory)
        }

    override var scaffoldDirective by mutableStateOf(initialScaffoldDirective)
    override var isDestinationHistoryAware by mutableStateOf(initialIsDestinationHistoryAware)
    var adaptStrategies by mutableStateOf(initialAdaptStrategies)

    override val currentDestination
        get() = destinationHistory.lastOrNull()

    override val scaffoldValue by derivedStateOf {
        calculateScaffoldValue(destinationHistory.lastIndex)
    }

    // Must be updated whenever `destinationHistory` changes to keep in sync.
    override val scaffoldState = MutableThreePaneScaffoldState(scaffoldValue)

    override fun peekPreviousScaffoldValue(
        backNavigationBehavior: BackNavigationBehavior
    ): ThreePaneScaffoldValue {
        val index = getPreviousDestinationIndex(backNavigationBehavior)
        return if (index == -1) scaffoldValue else calculateScaffoldValue(index)
    }

    override suspend fun navigateTo(pane: ThreePaneScaffoldRole, contentKey: T?) {
        destinationHistory.add(ThreePaneScaffoldDestinationItem(pane, contentKey))
        animateStateToCurrentScaffoldValue()
    }

    override fun canNavigateBack(backNavigationBehavior: BackNavigationBehavior): Boolean =
        getPreviousDestinationIndex(backNavigationBehavior) >= 0

    override suspend fun navigateBack(backNavigationBehavior: BackNavigationBehavior): Boolean {
        val previousDestinationIndex = getPreviousDestinationIndex(backNavigationBehavior)
        if (previousDestinationIndex < 0) {
            destinationHistory.clear()
            animateStateToCurrentScaffoldValue()
            return false
        }
        val targetSize = previousDestinationIndex + 1
        while (destinationHistory.size > targetSize) {
            destinationHistory.removeLastKt()
        }
        animateStateToCurrentScaffoldValue()
        return true
    }

    override suspend fun seekBack(backNavigationBehavior: BackNavigationBehavior, fraction: Float) {
        if (fraction == 0f) {
            animateStateToCurrentScaffoldValue()
        } else {
            val previousScaffoldValue = peekPreviousScaffoldValue(backNavigationBehavior)
            scaffoldState.seekTo(fraction, previousScaffoldValue, isPredictiveBackInProgress = true)
        }
    }

    private suspend fun animateStateToCurrentScaffoldValue() {
        scaffoldState.animateTo(scaffoldValue)
    }

    private fun calculateScaffoldValue(destinationIndex: Int) =
        if (destinationIndex == -1) {
            calculateThreePaneScaffoldValue(
                maxHorizontalPartitions = scaffoldDirective.maxHorizontalPartitions,
                maxVerticalPartitions = scaffoldDirective.maxVerticalPartitions,
                adaptStrategies = adaptStrategies,
                currentDestination = null,
            )
        } else if (isDestinationHistoryAware) {
            calculateThreePaneScaffoldValue(
                maxHorizontalPartitions = scaffoldDirective.maxHorizontalPartitions,
                maxVerticalPartitions = scaffoldDirective.maxVerticalPartitions,
                adaptStrategies = adaptStrategies,
                destinationHistory = destinationHistory.subList(0, destinationIndex + 1),
            )
        } else {
            calculateThreePaneScaffoldValue(
                maxHorizontalPartitions = scaffoldDirective.maxHorizontalPartitions,
                maxVerticalPartitions = scaffoldDirective.maxVerticalPartitions,
                adaptStrategies = adaptStrategies,
                currentDestination = destinationHistory[destinationIndex],
            )
        }
```

What this tells you:
- **The model is a plain append-only list.** `navigateTo` appends; `navigateBack` truncates to
  `previousDestinationIndex + 1`. Nothing is deduplicated — `navigateTo(Detail, a)` twice puts two
  entries in the history.
- **`currentDestination` is just `destinationHistory.lastOrNull()`** and `scaffoldValue` is a
  `derivedStateOf` over the whole history — so reading either in composition subscribes you to
  navigation changes automatically.
- **`isDestinationHistoryAware` picks the overload**: `true` ⇒ the history-aware
  `calculateThreePaneScaffoldValue(destinationHistory = …)`; `false` ⇒ the single-destination one.
  With `true`, on a 2-partition layout, arriving at Detail keeps List expanded because List is still
  second-highest priority in the history. With `false`, the second slot falls back to the generic
  `Primary → Secondary → Tertiary` order.
- ⚠️ **`navigateBack` clears the ENTIRE history and returns `false`** when there is no previous
  destination. Do not call it speculatively — gate on `canNavigateBack(behavior)`.
- **`seekBack(fraction = 0f)` is the cancel path** — it re-animates to the current value rather than
  seeking.
- `navigateTo`/`navigateBack` `animateTo` the new value; only directive/strategy changes `snapTo`.

Saver `[SRC@HEAD]`:
```kotlin
companion object {
    /** To keep destination history saved */
    fun <T> saver(
        initialScaffoldDirective: PaneScaffoldDirective,
        initialAdaptStrategies: ThreePaneScaffoldAdaptStrategies,
        initialDestinationHistoryAware: Boolean,
    ): Saver<DefaultThreePaneScaffoldNavigator<T>, *> { /* listSaver over destinationItemSaver */ }
}

@OptIn(ExperimentalMaterial3AdaptiveApi::class)
internal fun <T> destinationItemSaver(): Saver<ThreePaneScaffoldDestinationItem<T>, Any> =
    listSaver(
        save = { listOf(it.pane, it.contentKey) },
        restore = {
            @Suppress("UNCHECKED_CAST")
            (ThreePaneScaffoldDestinationItem(
                pane = it[0] as ThreePaneScaffoldRole,
                contentKey = it[1] as T?,
            ))
        },
    )
```
Only `pane` and `contentKey` are saved — the directive and strategies are re-supplied by the
`remember*` call on restore. That is why the saver factory takes them as arguments.

---

## 6. `BackNavigationBehavior` — all four values

This is the API people get wrong most often after the role trap.

`[SRC@HEAD BackNavigationBehavior.kt]` — complete file body, verbatim:
```kotlin
/** A class to control how back navigation should behave in a [ThreePaneScaffoldNavigator]. */
@ExperimentalMaterial3AdaptiveApi
@JvmInline
public value class BackNavigationBehavior private constructor(private val description: String) {
    override fun toString(): String = this.description

    public companion object {
        /**
         * Pop the latest destination from the backstack.
         *
         * Note: a multi-pane layout may create navigation backstacks that are not possible in a
         * single-pane layout (e.g., navigating directly from one detail item to another). If the
         * device size changes in the middle of the navigation, this [BackNavigationBehavior] may
         * result in unintuitive behavior.
         */
        public val PopLatest: BackNavigationBehavior = BackNavigationBehavior("PopLatest")

        /**
         * Pop destinations from the backstack until there is a change in the scaffold value. In
         * other words, back navigation forces a change in which pane(s) is/are shown.
         *
         * For example, in a single-pane layout, this will skip entries until the current
         * destination is a different [ThreePaneScaffoldRole]. In a multi-pane layout, this will
         * skip entries until the [PaneAdaptedValue] of any pane changes.
         */
        public val PopUntilScaffoldValueChange: BackNavigationBehavior =
            BackNavigationBehavior("PopUntilScaffoldValueChange")

        /**
         * Pop destinations from the backstack until there is a change in the current destination
         * pane. In other words, back navigation forces a change in which pane is currently
         * considered "active", as determined by the `pane` of the current
         * [ThreePaneScaffoldDestinationItem].
         */
        public val PopUntilCurrentDestinationChange: BackNavigationBehavior =
            BackNavigationBehavior("PopUntilCurrentDestinationChange")

        /**
         * Pop destinations from the backstack until there is a content change.
         *
         * A "content change" is defined as either a change in the `contentKey` of the current
         * [ThreePaneScaffoldDestinationItem], or a change in the scaffold value (similar to
         * [PopUntilScaffoldValueChange]).
         *
         * Note: a multi-pane layout may create navigation backstacks that are not possible in a
         * single-pane layout (e.g., navigating directly from one detail item to another). If the
         * device size changes in the middle of the navigation, this [BackNavigationBehavior] may
         * result in unintuitive behavior.
         */
        public val PopUntilContentChange: BackNavigationBehavior =
            BackNavigationBehavior("PopUntilContentChange")
    }
}
```

It is a **`@JvmInline value class` over `String`**, not an enum: no `values()`, no exhaustive `when`,
and `toString()` returns the bare name.

### Exactly what each does — from `getPreviousDestinationIndex` `[SRC@HEAD]`

```kotlin
private fun getPreviousDestinationIndex(backNavBehavior: BackNavigationBehavior): Int {
    if (destinationHistory.size <= 1) {
        // No previous destination
        return -1
    }
    when (backNavBehavior) {
        BackNavigationBehavior.PopLatest -> return destinationHistory.lastIndex - 1
        BackNavigationBehavior.PopUntilScaffoldValueChange ->
            for (previousDestinationIndex in destinationHistory.lastIndex - 1 downTo 0) {
                val previousValue = calculateScaffoldValue(previousDestinationIndex)
                if (previousValue != scaffoldValue) {
                    return previousDestinationIndex
                }
            }
        BackNavigationBehavior.PopUntilCurrentDestinationChange ->
            for (previousDestinationIndex in destinationHistory.lastIndex - 1 downTo 0) {
                val destination = destinationHistory[previousDestinationIndex].pane
                if (destination != currentDestination?.pane) {
                    return previousDestinationIndex
                }
            }
        BackNavigationBehavior.PopUntilContentChange ->
            for (previousDestinationIndex in destinationHistory.lastIndex - 1 downTo 0) {
                val contentKey = destinationHistory[previousDestinationIndex].contentKey
                if (contentKey != currentDestination?.contentKey) {
                    return previousDestinationIndex
                }
                // A scaffold value change also counts as a content change.
                val previousValue = calculateScaffoldValue(previousDestinationIndex)
                if (previousValue != scaffoldValue) {
                    return previousDestinationIndex
                }
            }
    }

    return -1
}
```

| Behavior | Scan rule | Returns -1 (⇒ back exits the scaffold) when |
| --- | --- | --- |
| `PopLatest` | No scan — always `lastIndex - 1` | history size ≤ 1 |
| `PopUntilScaffoldValueChange` **(default)** | Walk back until `calculateScaffoldValue(i) != scaffoldValue` | no earlier entry produces a *different layout* |
| `PopUntilCurrentDestinationChange` | Walk back until `history[i].pane != currentDestination.pane` | every earlier entry targets the same pane |
| `PopUntilContentChange` | Walk back until `contentKey` differs **or** the scaffold value differs | neither content nor layout ever differs |

### Behaviour comparison `[DOC developer.android.com/develop/ui/compose/layouts/adaptive/list-detail]`

| Behavior | Multi-pane | Single-pane | Doc's "use when" |
| --- | --- | --- | --- |
| **`PopUntilScaffoldValueChange`** (default, recommended) | Click Item 2 while viewing Item 1 → back might exit the app (no layout change) | Item 1 → Item 2 → back returns directly to the list pane | *"You want distinct layout transitions with each back action"* |
| `PopUntilContentChange` | Click Item 2 → back restores Item 1 in the detail pane | Same content reversion | *"Users expect to return to previously viewed content"* |
| `PopUntilCurrentDestinationChange` | Pops until the current navigation destination changes; same in single/multi-pane | same | *"Maintaining clear visual indication of current navigation is crucial"* |
| `PopLatest` | Removes only the most recent destination | same | *"Back navigation without skipping intermediate states is required"*. ⚠️ *"Multi-pane layouts may create navigation states impossible in single-pane. Device size changes mid-navigation may produce unintuitive results."* |

### How to choose

1. **Start with the default `PopUntilScaffoldValueChange`.** It guarantees every back press changes
   what the user sees, which is the behaviour users expect from the system back gesture. Its cost:
   on a dual-pane layout, item-to-item selection produces no layout change, so back leaves the
   screen. That is usually right — the user "finished" with the list-detail screen.
2. **Switch to `PopUntilContentChange` if your detail pane is the destination**, not the layout —
   browsing chained items (a wiki, linked records, a media queue) where the user thinks of each item
   as a place they can go back to. Requires a meaningful `contentKey` (typed navigator, structural
   equality) or it degenerates to `PopUntilScaffoldValueChange`.
3. **`PopUntilCurrentDestinationChange`** only when the *pane* is the unit of navigation — e.g. an
   extra pane that must always be dismissed to the detail pane regardless of content.
4. **Avoid `PopLatest`** unless you are replaying an exact action history (an editor's step-back). On
   a multi-pane layout it produces back presses that appear to do nothing, and both `PopLatest` and
   `PopUntilContentChange` carry the explicit androidx warning about size changes mid-navigation.
5. **Be consistent.** `canNavigateBack(b)`, `navigateBack(b)`, `seekBack(b, …)` and
   `ThreePaneScaffoldPredictiveBackHandler(navigator, b)` must all receive the **same** behaviour, or
   the predictive-back preview animates to a different destination than the commit lands on. Hoist it:
   ```kotlin
   val backBehavior = BackNavigationBehavior.PopUntilScaffoldValueChange
   ```
   `NavigableListDetailPaneScaffold(defaultBackBehavior = …)` does this for the handler only — your
   own in-content back buttons must pass the same value.

The androidx Navigation-2 sample makes the behaviour user-selectable to demonstrate the differences
`[SRC@HEAD samples/ThreePaneScaffoldSample.kt:516–645]`:
```kotlin
var backBehaviorIndex by rememberSaveable { mutableStateOf(0) }
val backBehaviors =
    listOf(
        BackNavigationBehavior.PopUntilScaffoldValueChange,
        BackNavigationBehavior.PopUntilCurrentDestinationChange,
        BackNavigationBehavior.PopUntilContentChange,
        BackNavigationBehavior.PopLatest,
    )
val backBehavior = backBehaviors[backBehaviorIndex]
```

---

## 7. `NavigableListDetailPaneScaffold` / `NavigableSupportingPaneScaffold`

**Android-only** — they live in
`[SRC@HEAD adaptive-navigation/src/androidMain/.../AndroidThreePaneScaffold.android.kt]`, because
predictive back is an Android platform feature.

```kotlin
@ExperimentalMaterial3AdaptiveApi
@Composable
public fun <T> NavigableListDetailPaneScaffold(
    navigator: ThreePaneScaffoldNavigator<T>,
    listPane: @Composable ThreePaneScaffoldPaneScope.() -> Unit,
    detailPane: @Composable ThreePaneScaffoldPaneScope.() -> Unit,
    modifier: Modifier = Modifier,
    extraPane: (@Composable ThreePaneScaffoldPaneScope.() -> Unit)? = null,
    defaultBackBehavior: BackNavigationBehavior =
        BackNavigationBehavior.PopUntilScaffoldValueChange,
    paneExpansionDragHandle: (@Composable ThreePaneScaffoldScope.(PaneExpansionState) -> Unit)? =
        null,
    paneExpansionState: PaneExpansionState? = null,
) {
    ThreePaneScaffoldPredictiveBackHandler(
        navigator = navigator,
        backBehavior = defaultBackBehavior,
    )

    ListDetailPaneScaffold(
        modifier = modifier,
        directive = navigator.scaffoldDirective,
        scaffoldState = navigator.scaffoldState,
        detailPane = detailPane,
        listPane = listPane,
        extraPane = extraPane,
        paneExpansionDragHandle = paneExpansionDragHandle,
        paneExpansionState = paneExpansionState,
    )
}
```

`[SRC@HEAD:120]` — `NavigableSupportingPaneScaffold` is structurally identical with
`mainPane` / `supportingPane` in place of `listPane` / `detailPane`, and calls
`SupportingPaneScaffold(directive = navigator.scaffoldDirective, scaffoldState = navigator.scaffoldState, …)`.

**That body is the entire value-add:** a predictive-back handler plus `directive` and `scaffoldState`
pulled off the navigator for you. Nothing else is hidden.

**Prefer them** whenever you have a navigator — you cannot forget the back handler, you cannot
accidentally pass `value =` instead of `scaffoldState =`, and `defaultBackBehavior` sits in one
place. Reach for the raw scaffold only when you need the `value` overload, need to place other
effects between the handler and the scaffold, or are driving the scaffold without a navigator.

Note the parameter is `defaultBackBehavior`, **not** `backBehavior` — it is the default used by the
installed handler; in-content back buttons still take their own argument.

---

## 8. `ThreePaneScaffoldPredictiveBackHandler` — predictive back done right

`[SRC@HEAD ThreePaneScaffoldPredictiveBackHandler.android.kt]`
```kotlin
/**
 * An effect to add predictive back handling to a three pane scaffold.
 *
 * [NavigableListDetailPaneScaffold] and [NavigableSupportingPaneScaffold] apply this effect
 * automatically. If instead you are using [ListDetailPaneScaffold] or [SupportingPaneScaffold], use
 * the overloads that accept a [ThreePaneScaffoldState] and pass
 * [navigator.scaffoldState][ThreePaneScaffoldNavigator.scaffoldState] to the scaffold after adding
 * this effect to your composition.
 *
 * A predictive back gesture will cause the [navigator] to
 * [seekBack][ThreePaneScaffoldNavigator.seekBack] to the previous scaffold value. The progress can
 * be read from the [progressFraction][ThreePaneScaffoldState.progressFraction] of the navigator's
 * scaffold state. It will range from 0 (representing the start of the predictive back gesture) to
 * some fraction less than 1 (representing a "peek" or "preview" of the previous scaffold value). If
 * the gesture is committed, back navigation is performed. If the gesture is cancelled, the
 * navigator's scaffold state is reset.
 */
@ExperimentalMaterial3AdaptiveApi
@Composable
public fun <T> ThreePaneScaffoldPredictiveBackHandler(
    navigator: ThreePaneScaffoldNavigator<T>,
    backBehavior: BackNavigationBehavior,
) {
    key(navigator, backBehavior) {
        PredictiveBackHandler(enabled = navigator.canNavigateBack(backBehavior)) { progress ->
            // code for gesture back started
            try {
                progress.collect { backEvent ->
                    navigator.seekBack(
                        backBehavior,
                        fraction =
                            backProgressToStateProgress(
                                progress = backEvent.progress,
                                scaffoldValue = navigator.scaffoldValue,
                            ),
                    )
                }
                // code for completion
                navigator.navigateBack(backBehavior)
            } catch (e: CancellationException) {
                // code for cancellation
                withContext(NonCancellable) { navigator.seekBack(backBehavior, fraction = 0f) }
            }
        }
    }
}
```

Doing it right:
- **`backBehavior` is required here** (no default), unlike every navigator method. Pass the same
  value you use everywhere else (§6.5).
- **Use the `scaffoldState` overload of the scaffold.** The KDoc says so explicitly: predictive back
  works by *seeking* `ThreePaneScaffoldState`. With the `value` overload there is no transition to
  seek and the gesture will show nothing.
- Order matters: add the effect, **then** the scaffold, both reading the same `navigator`.
- `enabled = navigator.canNavigateBack(backBehavior)` — when it is `false` the gesture falls through
  to the enclosing back handler / activity, which is what you want.
- Cancellation is handled with `withContext(NonCancellable) { seekBack(fraction = 0f) }` — the reset
  must survive the cancelled scope. If you write a custom handler, copy that.
- The whole thing is wrapped in `key(navigator, backBehavior)`, so changing the behaviour mid-flight
  re-creates the handler cleanly.

Raw-scaffold wiring:
```kotlin
val backBehavior = BackNavigationBehavior.PopUntilScaffoldValueChange
ThreePaneScaffoldPredictiveBackHandler(navigator, backBehavior)
ListDetailPaneScaffold(
    directive = navigator.scaffoldDirective,
    scaffoldState = navigator.scaffoldState,   // NOT `value =`, or predictive back shows nothing
    listPane = { AnimatedPane { /* ... */ } },
    detailPane = { AnimatedPane { /* ... */ } },
)
```

### Manifest requirement `[DOC]`

> To enable predictive back animations on Android 15 or lower, add this to your
> `AndroidManifest.xml`:
> ```xml
> <application android:enableOnBackInvokedCallback="true">
>   ...
> </application>
> ```
> **Note**: Android 16+ enables predictive back by default.

Without it, on API ≤35 you get a plain back press with no preview animation — silently, no crash.

---

## 9. `ThreePaneScaffoldState` / `MutableThreePaneScaffoldState`

`[SRC@HEAD ThreePaneScaffoldState.kt]`
```kotlin
/**
 * A read-only state of a three pane scaffold. It provides information about the [Transition]
 * between [ThreePaneScaffoldValue]s.
 */
@ExperimentalMaterial3AdaptiveApi
@Stable
public sealed class ThreePaneScaffoldState {
    /**
     * Current [ThreePaneScaffoldValue] state of the transition. If there is an active transition,
     * [currentState] and [targetState] are different.
     */
    public abstract val currentState: ThreePaneScaffoldValue

    /**
     * Target [ThreePaneScaffoldValue] state of the transition. If this is the same as
     * [currentState], no transition is active.
     */
    public abstract val targetState: ThreePaneScaffoldValue

    /**
     * The progress of the transition from [currentState] to [targetState] as a fraction of the
     * entire duration.
     *
     * If [targetState] and [currentState] are the same, [progressFraction] will be 0.
     */
    @get:FloatRange(from = 0.0, to = 1.0) public abstract val progressFraction: Float

    /** Whether a predictive back navigation is currently in progress. */
    public abstract val isPredictiveBackInProgress: Boolean

    @Composable internal abstract fun rememberTransition(): Transition<ThreePaneScaffoldValue>
}

/**
 * The seekable state of a three pane scaffold. It serves as the [SeekableTransitionState] to
 * manipulate the [Transition] between [ThreePaneScaffoldValue]s.
 */
@ExperimentalMaterial3AdaptiveApi
@Stable
public class MutableThreePaneScaffoldState(initialScaffoldValue: ThreePaneScaffoldValue) :
    ThreePaneScaffoldState() {
    private val transitionState = SeekableTransitionState(initialScaffoldValue)

    public suspend fun snapTo(targetState: ThreePaneScaffoldValue)

    public suspend fun seekTo(
        @FloatRange(from = 0.0, to = 1.0) fraction: Float,
        targetState: ThreePaneScaffoldValue = this.targetState,
        isPredictiveBackInProgress: Boolean = false,
    )

    public suspend fun animateTo(
        targetState: ThreePaneScaffoldValue = this.targetState,
        animationSpec: FiniteAnimationSpec<Float>? = null,
        isPredictiveBackInProgress: Boolean = false,
    )
}
```

Backed by `SeekableTransitionState` plus a `MutatorMutex`. `MutableThreePaneScaffoldState` is the
**only** public concrete subclass — you *can* construct one and drive
`ListDetailPaneScaffold(scaffoldState = …)` with no navigator at all, if you already own the back
stack. In that case you own `snapTo` (config changes), `animateTo` (navigation) and `seekTo`
(predictive back) yourself, and you get no `BackNavigationBehavior` machinery.

Read `progressFraction` / `isPredictiveBackInProgress` to drive your own chrome during a back
gesture (e.g. cross-fading a top-app-bar title).

---

## 10. `PaneExpansionState`

Drag-to-resize: the user drags a handle to change the split between two expanded panes.

`[API-1.3.0]`
```
@androidx.compose.runtime.Stable public final class PaneExpansionState {
    method public suspend Object? animateTo(androidx.compose.material3.adaptive.layout.PaneExpansionAnchor anchor, optional float initialVelocity, kotlin.coroutines.Continuation<? super kotlin.Unit>);
    method public void clear();
    method @InaccessibleFromKotlin public androidx.compose.material3.adaptive.layout.PaneExpansionAnchor? getCurrentAnchor();
    method public boolean isUnspecified();
    method public void setFirstPaneProportion(@FloatRange(from=0.0, to=1.0) float firstPaneProportion);
    method public void setFirstPaneWidth(int firstPaneWidth);
    property public androidx.compose.material3.adaptive.layout.PaneExpansionAnchor? currentAnchor;
    field public static final androidx.compose.material3.adaptive.layout.PaneExpansionState.Companion Companion;
    field public static final int Unspecified = -1; // 0xffffffff
}
```

> **`PaneExpansionState` is NOT experimental** (no opt-in on the class), but
> `rememberPaneExpansionState` **is** `@ExperimentalMaterial3AdaptiveApi`.

`[SRC@HEAD PaneExpansionState.kt]` — the constructor is **`internal`**; obtain instances only via
`rememberPaneExpansionState`:
```kotlin
/**
 * This class manages the pane expansion state for pane scaffolds. By providing and modifying an
 * instance of this class, you can specify the expanded panes' expansion width or proportion when
 * pane scaffold is displaying a dual-pane layout.
 *
 * This class also serves as the [DraggableState] of pane expansion handle. When a handle
 * implementation is provided to the associated pane scaffold, the scaffold will use
 * [PaneExpansionState] to store and manage dragging and anchoring of the handle, and thus the pane
 * expansion state.
 */
@Stable
public class PaneExpansionState
internal constructor(
    data: PaneExpansionStateData = PaneExpansionStateData(),
    @get:VisibleForTesting internal val consumeDragDelta: ((Float) -> Float) = noOpConsumeDragDelta,
) {
```

### Public members `[SRC@HEAD]`

```kotlin
/**
 * The current anchor that pane expansion has been settled or is settling to. Note that this
 * field might be `null` if:
 * 1. No anchors have been set to the state.
 * 2. Pane expansion is set directly via [setFirstPaneWidth] or set [setFirstPaneProportion].
 * 3. Pane expansion is in its initial state without an initial anchor provided.
 */
public var currentAnchor: PaneExpansionAnchor?
    get() = data.currentAnchorState
    private set(value) { data.currentAnchorState = value }

/** Returns `true` if none of [firstPaneWidth] or [firstPaneProportion] has been set. */
public fun isUnspecified(): Boolean =
    firstPaneWidth == Unspecified &&
        firstPaneProportion.isNaN() &&
        currentDraggingOffset == Unspecified

/**
 * Set the width of the first expanded pane in the layout. When the set value gets applied, it
 * will be coerced within the range of `[0, the full displayable width of the layout]`.
 *
 * Note that setting this value will reset the first pane proportion previously set via
 * [setFirstPaneProportion] or the current dragging result if there's any. Also if user drags
 * the pane after setting the first pane width, the user dragging result will take the priority
 * over this set value when rendering panes, but the set value will be saved.
 */
public fun setFirstPaneWidth(firstPaneWidth: Int) {
    data.firstPaneProportionState = Float.NaN
    data.currentDraggingOffsetState = Unspecified
    data.firstPaneWidthState = firstPaneWidth
    currentAnchor = null
}

/**
 * Set the proportion of the first expanded pane in the layout. The set value needs to be within
 * the range of `[0f, 1f]`, otherwise the setter throws.
 * ...
 */
public fun setFirstPaneProportion(@FloatRange(0.0, 1.0) firstPaneProportion: Float) {
    require(firstPaneProportion in 0f..1f) { "Proportion value needs to be in [0f, 1f]" }
    data.firstPaneWidthState = Unspecified
    data.currentDraggingOffsetState = Unspecified
    data.firstPaneProportionState = firstPaneProportion
    currentAnchor = null
}

/**
 * Animate the pane expansion to the given [PaneExpansionAnchor]. Note that the given anchor
 * must be one of the provided anchor when creating the state with [rememberPaneExpansionState];
 * otherwise the function throws.
 */
public suspend fun animateTo(anchor: PaneExpansionAnchor, initialVelocity: Float = 0F) {
    require(anchors.contains(anchor)) { "The provided $anchor is not in the anchor list!" }
    currentAnchor = anchor
    measuredDensity?.apply {
        val position = anchor.positionIn(maxExpansionWidth, this, measuredLayoutDirection)
        animateToInternal(position, initialVelocity)
    }
}

/**
 * Clears any previously set [firstPaneWidth] or [firstPaneProportion], as well as the user
 * dragging result.
 */
public fun clear() {
    data.firstPaneWidthState = Unspecified
    data.firstPaneProportionState = Float.NaN
    data.currentDraggingOffsetState = Unspecified
}

public companion object {
    /** The constant value used to denote the pane expansion is not specified. */
    public const val Unspecified: Int = -1

    private const val AnchoringVelocityThreshold = 200F

    internal val DefaultAnchoringAnimationSpec =
        spring(dampingRatio = 0.8f, stiffness = 380f, visibilityThreshold = 1f)

    internal val noOpConsumeDragDelta: ((Float) -> Float) = { delta -> delta }
}
```

Behaviour:
- **Precedence when rendering: user drag > `setFirstPaneWidth` / `setFirstPaneProportion` >
  directive defaults.** Setting either setter clears the other **and** sets `currentAnchor = null`.
- `setFirstPaneWidth` takes **pixels** (`Int`), not `Dp`. Convert with `with(density) { 360.dp.roundToPx() }`.
- Default anchoring animation is `spring(dampingRatio = 0.8f, stiffness = 380f,
  visibilityThreshold = 1f)`; the fling→anchor velocity threshold is `200F`. Both are
  `internal`/`private` — you cannot read them, only override via `anchoringAnimationSpec`.
- **`firstPaneWidth` and `firstPaneProportion` have `internal` getters** — write-only from app code.
  Only `currentAnchor` and `isUnspecified()` are readable.

Throwing conditions: `animateTo(anchor)` throws if the anchor is not in the list passed to
`rememberPaneExpansionState`; `setFirstPaneProportion` throws outside `[0f, 1f]`.

---

## 11. `rememberPaneExpansionState` — the four overloads

Two dimensions: **key vs keyProvider** × **with vs without `consumeDragDelta`**. The two without
`consumeDragDelta` are `@Deprecated`/`@BytecodeOnly` in `[API-1.3.0]` — only the two below are
Kotlin-callable.

`[SRC@HEAD PaneExpansionState.kt]`
```kotlin
/**
 * Remembers and returns a [PaneExpansionState] associated to a given
 * [PaneExpansionStateKeyProvider].
 *
 * Note that the remembered [PaneExpansionState] with all keys that have been used will be
 * persistent through the associated pane scaffold's lifecycles.
 *
 * @param keyProvider the provider of [PaneExpansionStateKey]
 * @param anchors the anchor list of the returned [PaneExpansionState]
 * @param initialAnchoredIndex the index of the anchor that is supposed to be used during the
 *   initial layout of the associated scaffold; it has to be a valid index of the provided [anchors]
 *   otherwise the function throws; by default the value will be -1 and no initial anchor will be
 *   used.
 * @param anchoringAnimationSpec the animation spec used to perform anchoring animation; by default
 *   it will be a spring motion.
 * @param flingBehavior the fling behavior used to handle flings; by default
 *   [ScrollableDefaults.flingBehavior] will be applied.
 * @param consumeDragDelta the callback that will be called before the drag starts to change the
 *   pane sizes; the input of the lambda will be the raw delta by user dragging, and it should
 *   returns the remaining delta after the consumption by the callback; this can be used to
 *   implement custom behavior like nested scrolling or combining pane expansion with other element
 *   expansion behavior like navigation rails.
 */
@ExperimentalMaterial3AdaptiveApi
@Composable
public fun rememberPaneExpansionState(
    keyProvider: PaneExpansionStateKeyProvider,
    anchors: List<PaneExpansionAnchor> = emptyList(),
    initialAnchoredIndex: Int = -1,
    anchoringAnimationSpec: FiniteAnimationSpec<Float> = DefaultAnchoringAnimationSpec,
    flingBehavior: FlingBehavior = ScrollableDefaults.flingBehavior(),
    consumeDragDelta: ((delta: Float) -> Float) = PaneExpansionState.noOpConsumeDragDelta,
): PaneExpansionState =
    rememberPaneExpansionState(
        keyProvider.paneExpansionStateKey,
        anchors,
        initialAnchoredIndex,
        anchoringAnimationSpec,
        flingBehavior,
        consumeDragDelta,
    )

@ExperimentalMaterial3AdaptiveApi
@Composable
@Suppress("UnnecessaryLambdaCreation") // It's necessary to stabilize the lambda parameter
public fun rememberPaneExpansionState(
    key: PaneExpansionStateKey = PaneExpansionStateKey.Default,
    anchors: List<PaneExpansionAnchor> = emptyList(),
    initialAnchoredIndex: Int = -1,
    anchoringAnimationSpec: FiniteAnimationSpec<Float> = DefaultAnchoringAnimationSpec,
    flingBehavior: FlingBehavior = ScrollableDefaults.flingBehavior(),
    consumeDragDelta: ((Float) -> Float) = PaneExpansionState.noOpConsumeDragDelta,
): PaneExpansionState {
    val initialAnchor =
        remember(anchors, initialAnchoredIndex) {
            if (initialAnchoredIndex == -1) null else anchors[initialAnchoredIndex]
        }
    // Note that this will only be updated when the current anchors change, which will serve as a
    // fallback if the current anchor is no longer in the current anchors.
    val initialAnchorForCurrentAnchors = remember(anchors) { initialAnchor }
    val data =
        rememberPersistentlyWithKey(
            key = key,
            keySaver = PaneExpansionStateKeySaver(),
            valueSaver = PaneExpansionStateDataSaver(),
        ) {
            PaneExpansionStateData(currentAnchor = initialAnchor)
        }
    // Create a stable reference to the latest consumeDragDelta
    val consumeDragDeltaRef by rememberUpdatedRef(consumeDragDelta)
    val expansionState = remember {
        PaneExpansionState(data) { delta -> consumeDragDeltaRef(delta) }
    }
    LaunchedEffect(key, anchors, anchoringAnimationSpec, flingBehavior) {
        expansionState.restore(
            data = data,
            anchors = anchors,
            anchoringAnimationSpec = anchoringAnimationSpec,
            flingBehavior = flingBehavior,
            initialAnchorForCurrentAnchors = initialAnchorForCurrentAnchors,
        )
    }
    return expansionState
}
```

Defaults: `anchors = emptyList()`, `initialAnchoredIndex = -1` (no initial anchor —
**an out-of-range index throws** via `anchors[initialAnchoredIndex]`),
`anchoringAnimationSpec = spring(0.8f, 380f, 1f)`, `flingBehavior = ScrollableDefaults.flingBehavior()`,
`consumeDragDelta = { it }`.

`consumeDragDelta` (added 1.2.0) returns the **remaining** delta after your consumption — use it for
nested-scroll-style coordination, e.g. collapsing a navigation rail before the panes start resizing.
The KDoc names exactly that use case.

**Which to use:** the `keyProvider` overload with `keyProvider = navigator.scaffoldValue` in almost
all cases (§12).

---

## 12. `PaneExpansionStateKey` / `PaneExpansionStateKeyProvider`

`[SRC@HEAD]`
```kotlin
/**
 * Interface that provides [PaneExpansionStateKey] to remember and retrieve [PaneExpansionState]
 * with [rememberPaneExpansionState].
 */
@ExperimentalMaterial3AdaptiveApi
@Stable
public sealed interface PaneExpansionStateKeyProvider {
    /** The key that represents the unique state of the provider to index [PaneExpansionState]. */
    public val paneExpansionStateKey: PaneExpansionStateKey
}

/**
 * Interface that serves as keys to remember and retrieve [PaneExpansionState] with
 * [rememberPaneExpansionState].
 */
@ExperimentalMaterial3AdaptiveApi
@Immutable
public sealed interface PaneExpansionStateKey {
    private class DefaultImpl : PaneExpansionStateKey {
        override fun equals(other: Any?): Boolean = this === other
        override fun hashCode(): Int = identityHashCode(this)
    }

    public companion object {
        /**
         * The default [PaneExpansionStateKey]. If you want to always share the same
         * [PaneExpansionState] no matter what current scaffold state is, this key can be used. For
         * example if the default key is used and a user drag the list-detail layout to a 50-50
         * split, when the layout switches to, say, detail-extra, it will remain the 50-50 split
         * instead of using a different (default or user-set) split for it.
         */
        public val Default: PaneExpansionStateKey = DefaultImpl()
    }
}
```

Both interfaces are `sealed` — you cannot implement either. The two usable keys:

| Key source | Behaviour |
| --- | --- |
| `keyProvider = navigator.scaffoldValue` | **`ThreePaneScaffoldValue` implements `PaneExpansionStateKeyProvider`.** Gives a **per-layout-configuration** split: list/detail and detail/extra remember separate splits, and switching back restores the earlier one. This is what every androidx sample does. |
| `key = PaneExpansionStateKey.Default` | One shared split for every layout — drag list/detail to 50-50 and detail/extra stays 50-50 too. The KDoc spells out this exact scenario. |

State is persisted per key by `rememberPersistentlyWithKey`, which is
`rememberSaveable(saver = MapSaver(keySaver, valueSaver)) { mutableMapOf() }` plus `getOrPut`
`[SRC@HEAD internal/RememberUtils.kt:30–38]` — so **all keys used so far** are retained for the
scaffold's lifetime and across process death.

---

## 13. `PaneExpansionAnchor`

Anchors make the drag snap to defined positions on release.

`[SRC@HEAD]` — the sealed class and both implementations:
```kotlin
/**
 * The implementations of this interface represent different types of anchors of pane expansion
 * dragging. Setting up anchors when create [PaneExpansionState] will force user dragging to snap to
 * the set anchors after user releases the drag.
 */
public sealed class PaneExpansionAnchor {
    internal fun positionIn(
        totalSizePx: Int,
        density: Density,
        layoutDirection: LayoutDirection?,
    ): Int {
        val offset = positionIn(totalSizePx, density)
        return if (layoutDirection == LayoutDirection.Rtl) {
            totalSizePx - offset
        } else {
            offset
        }
    }

    /**
     * The description of the anchor that will be used in
     * [androidx.compose.ui.semantics.SemanticsProperties] like accessibility services.
     */
    @get:Composable public abstract val description: String

    /**
     * [PaneExpansionAnchor] implementation that specifies the anchor position in the proportion of
     * the total size of the layout at the start side of the anchor.
     *
     * @param proportion the proportion of the layout at the start side of the anchor. For example,
     *   if the current layout from the start to the end is list-detail, when the proportion value
     *   is 0.3 and this anchor is used, the list pane will occupy 30% of the layout and the detail
     *   pane will occupy 70% of it.
     */
    public class Proportion(@FloatRange(0.0, 1.0) public val proportion: Float) :
        PaneExpansionAnchor() {
        internal override fun positionIn(totalSizePx: Int, density: Density) =
            (totalSizePx * proportion).roundToInt().coerceIn(0, totalSizePx)

        public override fun toString(): String = "PaneExpansionAnchor(Proportion = $proportion)"
    }

    /**
     * [PaneExpansionAnchor] implementation that specifies the anchor position based on the offset
     * in [Dp].
     *
     * @property offset the offset of the anchor in [Dp].
     */
    public abstract class Offset
    internal constructor(public val offset: Dp, override internal val type: Int) :
        PaneExpansionAnchor() {
        public val direction: Direction = Direction(type)

        public override fun toString(): String = "PaneExpansionAnchor(Offset = $offset)"

        /** Represents the direction from where the offset will be calculated. */
        @JvmInline
        public value class Direction internal constructor(internal val value: Int) {
            public companion object {
                /**
                 * Indicates the offset will be calculated from the start. For example, if the
                 * offset is 150.dp, the resulted anchor will be at the position that is 150dp away
                 * from the start side of the associated layout.
                 */
                public val FromStart: Direction = Direction(OffsetFromStartType)

                /**
                 * Indicates the offset will be calculated from the end. ...
                 */
                public val FromEnd: Direction = Direction(OffsetFromEndType)
            }
        }
    }
}
```

All the forms:

| Form | Construct with | Meaning |
| --- | --- | --- |
| `PaneExpansionAnchor.Proportion(f)` | public ctor, `@FloatRange(0.0, 1.0)` | first pane occupies `f` of the layout. `0f` = first pane collapsed; `1f` = first pane full |
| `PaneExpansionAnchor.Offset.fromStart(dp)` | **companion factory** | anchor `dp` from the start edge |
| `PaneExpansionAnchor.Offset.fromEnd(dp)` | **companion factory** | anchor `dp` from the end edge |
| `PaneExpansionAnchor.Offset.Direction.FromStart` / `.FromEnd` | read via `anchor.direction` | which edge an `Offset` measures from |
| `anchor.description` | `@get:Composable` | localized a11y string, used by the drag-handle semantics |

- **`Offset` has no public constructor** — use `Offset.fromStart(dp)` / `Offset.fromEnd(dp)`.
- **RTL support is a headline 1.3.0 change**: `positionIn(totalSizePx, density, layoutDirection)`
  mirrors the position (`totalSizePx - offset`) when `layoutDirection == LayoutDirection.Rtl`. You do
  not mirror anchors yourself.
- `Proportion` coerces into `0..totalSizePx`; out-of-range proportions clamp rather than throw at
  layout time (the `@FloatRange` is advisory).

Canonical anchor list `[REPO androidx-m3 .../samples/ThreePaneScaffoldSample.kt` lines 1099–1106`]`:
```kotlin
private val PaneExpansionAnchors =
    listOf(
        PaneExpansionAnchor.Proportion(0f),
        PaneExpansionAnchor.Offset.fromStart(240.dp),
        PaneExpansionAnchor.Proportion(0.5f),
        PaneExpansionAnchor.Offset.fromEnd(240.dp),
        PaneExpansionAnchor.Proportion(1f),
    )
```
Five anchors: collapse-first, narrow-first, even, narrow-second, collapse-second. Sensible default;
`initialAnchoredIndex = 1` starts at the 240dp-from-start split.

---

## 14. `Modifier.paneExpansionDraggable` + `VerticalDragHandle`

`paneExpansionDraggable` is a member of `PaneScaffoldScope` (`pane-scaffolds.md` §16):
```kotlin
public fun Modifier.paneExpansionDraggable(
    state: PaneExpansionState,
    minTouchTargetSize: Dp,
    interactionSource: MutableInteractionSource,
    semanticsProperties: (SemanticsPropertyReceiver.() -> Unit)? = null,
): Modifier
```
It is **not `@Composable`** and **not experimental** (no opt-in on this member in the API file). It
handles horizontal drag, a11y actions, system-gesture exclusion, and minimum touch target size.

`defaultDragHandleSemantics` is now `@Deprecated` `[API-1.3.0]`:
```
method @KotlinOnly @Deprecated @SuppressCompatibility @androidx.compose.material3.adaptive.ExperimentalMaterial3AdaptiveApi @androidx.compose.runtime.Composable public static kotlin.jvm.functions.Function1<androidx.compose.ui.semantics.SemanticsPropertyReceiver,kotlin.Unit> defaultDragHandleSemantics(androidx.compose.material3.adaptive.layout.PaneExpansionState);
```
**Do not call it** — `paneExpansionDraggable` installs default semantics when
`semanticsProperties == null`.

### `VerticalDragHandle` lives in `androidx.compose.material3`, not the adaptive artifact

`[API compose/material3/material3/api/current.txt]`
```
method @KotlinOnly @androidx.compose.runtime.Composable public static void VerticalDragHandle(optional androidx.compose.ui.Modifier modifier, optional androidx.compose.material3.DragHandleSizes sizes, optional androidx.compose.material3.DragHandleColors colors, optional androidx.compose.material3.DragHandleShapes shapes, optional androidx.compose.foundation.interaction.MutableInteractionSource? interactionSource);

public final class VerticalDragHandleDefaults {
    method @KotlinOnly @androidx.compose.runtime.Composable public androidx.compose.material3.DragHandleColors colors();
    method @KotlinOnly @androidx.compose.runtime.Composable public androidx.compose.material3.DragHandleColors colors(optional androidx.compose.ui.graphics.Color color, optional androidx.compose.ui.graphics.Color pressedColor, optional androidx.compose.ui.graphics.Color draggedColor);
    method @KotlinOnly @androidx.compose.runtime.Composable public androidx.compose.material3.DragHandleShapes shapes();
    method @KotlinOnly @androidx.compose.runtime.Composable public androidx.compose.material3.DragHandleShapes shapes(optional androidx.compose.ui.graphics.Shape? shape, optional androidx.compose.ui.graphics.Shape? pressedShape, optional androidx.compose.ui.graphics.Shape? draggedShape);
    method public androidx.compose.material3.DragHandleSizes sizes();
    method @KotlinOnly public androidx.compose.material3.DragHandleSizes sizes(optional androidx.compose.ui.unit.DpSize size, optional androidx.compose.ui.unit.DpSize pressedSize, optional androidx.compose.ui.unit.DpSize draggedSize);
    field public static final androidx.compose.material3.VerticalDragHandleDefaults INSTANCE;
}
```
`DragHandleColors`, `DragHandleShapes`, `DragHandleSizes` are all `@Immutable`. The
size/color/shape **triples** (default / pressed / dragged) exist so the handle grows and re-shapes on
interaction — that is the Material 3 Expressive drag-handle behaviour.

> **UNVERIFIED** whether material3 **1.4.0 stable** has exactly this signature — the file read is
> `current.txt` (= 1.5.0-alphaNN). Confirm against whichever `material3` version you compile against.

### Canonical drag handle `[REPO androidx-m3 .../samples/ThreePaneScaffoldSample.kt` lines 407–424`]`

This is the `@Sampled` function referenced from `ListDetailPaneScaffold`'s KDoc:
```kotlin
@OptIn(ExperimentalMaterial3AdaptiveApi::class)
@Preview
@Sampled
@Composable
fun ThreePaneScaffoldScope.PaneExpansionDragHandleSample(
    state: PaneExpansionState = rememberPaneExpansionState()
) {
    val interactionSource = remember { MutableInteractionSource() }
    VerticalDragHandle(
        modifier =
            Modifier.paneExpansionDraggable(
                state,
                LocalMinimumInteractiveComponentSize.current,
                interactionSource,
            ),
        interactionSource = interactionSource,
    )
}
```
Two idioms to copy:
1. `minTouchTargetSize = LocalMinimumInteractiveComponentSize.current` (from
   `androidx.compose.material3`).
2. **The same `interactionSource` instance goes to both the modifier and the handle** — otherwise
   press/drag visuals never fire.

`[REPO /root/work/repos/Tomato/shared/src/androidMain/.../timerScreen/TimerScreen.kt` lines 823–837`]`
adds one more thing worth copying on Android:
```kotlin
paneExpansionDragHandle = {
    val interactionSource = remember { MutableInteractionSource() }
    VerticalDragHandle(
        modifier = Modifier
            .paneExpansionDraggable(
                expansionState,
                LocalMinimumInteractiveComponentSize.current,
                interactionSource
            )
            .systemGestureExclusion()
```
`Modifier.systemGestureExclusion()` keeps the drag from fighting the system back gesture when the
handle sits near a screen edge. (`paneExpansionDraggable` already does gesture exclusion internally;
Tomato belt-and-braces it. Harmless, and useful if you build a custom handle.)

### Complete working drag-to-resize example

`[SRC@HEAD samples/ThreePaneScaffoldSample.kt:161–214]` — this is **the** reference for pane
expansion, verbatim:
```kotlin
@OptIn(ExperimentalMaterial3AdaptiveApi::class)
@Preview
@Sampled
@Composable
fun ListDetailPaneScaffoldSampleWithExtraPane() {
    val coroutineScope = rememberCoroutineScope()
    val scaffoldNavigator = rememberListDetailPaneScaffoldNavigator<NavItemData>()
    val items = listOf("Item 1", "Item 2", "Item 3")
    val extraItems = listOf("Extra 1", "Extra 2", "Extra 3")
    val selectedItem = scaffoldNavigator.currentDestination?.contentKey

    ListDetailPaneScaffold(
        directive = scaffoldNavigator.scaffoldDirective,
        scaffoldState = scaffoldNavigator.scaffoldState,
        listPane = {
            AnimatedPane(modifier = Modifier.preferredWidth(200.dp)) {
                ListPaneContent(
                    items = items,
                    selectedItem = selectedItem,
                    scaffoldNavigator = scaffoldNavigator,
                    coroutineScope = coroutineScope,
                )
            }
        },
        detailPane = {
            AnimatedPane {
                DetailPaneContent(
                    items = items,
                    selectedItem = selectedItem,
                    scaffoldNavigator = scaffoldNavigator,
                    hasExtraPane = true,
                    coroutineScope = coroutineScope,
                )
            }
        },
        extraPane = {
            AnimatedPane {
                ExtraPaneContent(
                    extraItems = extraItems,
                    selectedItem = selectedItem,
                    scaffoldNavigator = scaffoldNavigator,
                    coroutineScope = coroutineScope,
                )
            }
        },
        paneExpansionState =
            rememberPaneExpansionState(
                keyProvider = scaffoldNavigator.scaffoldValue,
                anchors = PaneExpansionAnchors,
                initialAnchoredIndex = 1,
            ),
        paneExpansionDragHandle = { state -> PaneExpansionDragHandleSample(state) },
    )
}
```

Assembled end-to-end with `NavigableListDetailPaneScaffold` (predictive back included):
```kotlin
@OptIn(ExperimentalMaterial3AdaptiveApi::class)
@Composable
fun MyListDetailScreen() {
    val scope = rememberCoroutineScope()
    val navigator = rememberListDetailPaneScaffoldNavigator<MyKey>()

    val anchors = remember {
        listOf(
            PaneExpansionAnchor.Proportion(0f),
            PaneExpansionAnchor.Offset.fromStart(240.dp),
            PaneExpansionAnchor.Proportion(0.5f),
            PaneExpansionAnchor.Offset.fromEnd(240.dp),
            PaneExpansionAnchor.Proportion(1f),
        )
    }
    val expansionState = rememberPaneExpansionState(
        keyProvider = navigator.scaffoldValue,   // per-layout split
        anchors = anchors,
        initialAnchoredIndex = 1,
    )

    NavigableListDetailPaneScaffold(
        navigator = navigator,
        defaultBackBehavior = BackNavigationBehavior.PopUntilScaffoldValueChange,
        listPane = {
            AnimatedPane(Modifier.preferredWidth(360.dp)) {
                MyList(
                    selected = navigator.currentDestination?.contentKey,
                    onClick = { key ->
                        scope.launch { navigator.navigateTo(ListDetailPaneScaffoldRole.Detail, key) }
                    },
                )
            }
        },
        detailPane = {
            AnimatedPane {
                MyDetail(
                    item = navigator.currentDestination?.contentKey,
                    onBack = { scope.launch { navigator.navigateBack() } },
                )
            }
        },
        paneExpansionState = expansionState,
        paneExpansionDragHandle = { state ->
            val interactionSource = remember { MutableInteractionSource() }
            VerticalDragHandle(
                modifier = Modifier.paneExpansionDraggable(
                    state,
                    LocalMinimumInteractiveComponentSize.current,
                    interactionSource,
                ),
                interactionSource = interactionSource,
            )
        },
    )
}
```
`remember` the anchor list: it is a key of `rememberPaneExpansionState`'s internal `remember` and
`LaunchedEffect`, so a fresh list every recomposition re-runs `restore` continuously.

Programmatic control, e.g. an "even split" menu action:
```kotlin
val evenSplit = anchors[2]                     // Proportion(0.5f)
scope.launch { expansionState.animateTo(evenSplit) }   // throws if not in `anchors`
// or, without anchors:
expansionState.setFirstPaneProportion(0.5f)
expansionState.clear()                          // back to directive defaults
```

Without a drag handle you can still drive the split: pass `paneExpansionState` and call the setters.
The `ListDetailPaneScaffold` KDoc says so — *"if there's no drag handle, you can still modify
`[paneExpansionState]` directly to apply pane expansion."* Note the scaffold's internally-created
default expansion state is only **mutable when `paneExpansionDragHandle != null`**
(`rememberDefaultPaneExpansionState(mutable = paneExpansionDragHandle != null)`), so pass your own
state if you want to drive it programmatically with no handle.

---

## 15. State preservation — what survives, what you must save

| State | Mechanism | Config change | Process death |
| --- | --- | --- | --- |
| Destination history (`currentDestination`, `scaffoldValue`) | `rememberSaveable` + `DefaultThreePaneScaffoldNavigator.saver` | ✅ | ✅ — **if `T` is Bundle-storable** |
| Pane expansion (`firstPaneWidth`, `firstPaneProportion`, dragging offset, `currentAnchor`) | `rememberPersistentlyWithKey` → `rememberSaveable(MapSaver(...))` with `PaneExpansionStateKeySaver` + `PaneExpansionStateDataSaver` | ✅ | ✅ |
| Per-pane UI state (scroll, text fields, expanded rows) | `AnimatedPane`'s `saveableStateHolder.SaveableStateProvider(paneRole.toString())` | ✅ | ✅ for `rememberSaveable` state inside the pane |
| `scaffoldDirective`, `adaptStrategies`, `isDestinationHistoryAware` | **Not saved** — re-supplied by the `remember*` call and re-applied via `.apply { }` | recomputed | recomputed |
| Transition progress (`ThreePaneScaffoldState`) | plain `remember`-scoped `SeekableTransitionState` | ❌ resets to the target value | ❌ |
| Your own selection/filter/draft state | nothing automatic | ❌ | ❌ |

What this means in practice:

1. **Make `T` `@Parcelize`.** `destinationItemSaver` saves `listOf(it.pane, it.contentKey)` straight
   into a Bundle. A non-Bundle-storable content key throws at save time, not at compile time.
2. **`ThreePaneScaffoldRole` is an enum** and saves fine — the pane half of the history is never the
   problem.
3. **Pane expansion is restored per key.** With `keyProvider = navigator.scaffoldValue`, all
   previously-used layout configurations are restored, because the saver persists the whole map.
4. **Anchors are not saved as objects** — `PaneExpansionStateDataSaver` saves the anchor's *type tag*
   plus its proportion/offset value and reconstructs it `[SRC@HEAD PaneExpansionState.kt:933–970]`.
   So a restored anchor is a **new instance**, matched structurally. If you rebuild your anchor list
   with different values across a config change, the restored anchor may not be in the new list —
   `initialAnchorForCurrentAnchors` is the documented fallback for exactly that case.
5. **The transition does not survive.** After rotation the scaffold snaps to the correct value (the
   `LaunchedEffect` in `rememberThreePaneScaffoldNavigator` calls `snapTo`), it does not animate. That
   is intended.
6. **Save everything else yourself** — `rememberSaveable` for selection, filters, drafts, or hoist to
   a `ViewModel`/`SavedStateHandle`. Contents of a *hidden* pane keep their `rememberSaveable` state
   via the per-pane `SaveableStateProvider`, but plain `remember` inside a hidden pane is discarded
   when the pane leaves composition.
7. **Two independent back stacks.** When combining with an outer navigation library, the
   architectural comment on the androidx Navigation-2 sample is the rule
   `[SRC@HEAD samples/ThreePaneScaffoldSample.kt:516–645]`:
   > *"`navController` handles navigation outside the ListDetailPaneScaffold, and `scaffoldNavigator`
   > handles navigation within it."*

   Each saves itself. Do not try to mirror one into the other.

---

## 16. Navigation 3 integration

`adaptive-navigation3` (new in 1.3.0) bridges these scaffolds into Navigation 3's `SceneStrategy`
model so `NavDisplay` renders several back-stack entries simultaneously as panes —
`ListDetailSceneStrategy`, `SupportingPaneSceneStrategy`, `rememberListDetailSceneStrategy`,
`LocalListDetailSceneScope`, and the `listPane()` / `detailPane()` / `extraPane()` metadata helpers.

**Do not wire a `ThreePaneScaffoldNavigator` into a `NavDisplay`.** With Nav3 the back stack *is* the
pane state; the scene strategy builds the scaffold for you and you use neither
`rememberListDetailPaneScaffoldNavigator` nor `BackNavigationBehavior`.

→ **`navigation-suite.md`** in this skill covers `ListDetailSceneStrategy`,
`SupportingPaneSceneStrategy`, the scene scopes and `NavDisplay` wiring, alongside
`NavigationSuiteScaffold`; **`adaptive-recipes.md`** has the assembled end-to-end examples. Do not
duplicate that material here. Three facts to carry across in the meantime:
- `ListDetailSceneStrategy` returns **`null` for single-pane layouts** by default
  (`shouldHandleSinglePaneLayout = false`) — Nav3 handles that case itself.
- `calculateScene` **stops at the first entry lacking pane metadata**, so only a contiguous suffix of
  the back stack is grouped into the scaffold.
- **`LocalListDetailSceneScope.current == null` means "rendering single-pane"** — use it to decide
  whether a detail screen needs its own back button.

---

## 17. Gotchas

**Navigator**

1. **`navigateTo` / `navigateBack` / `seekBack` are `suspend`.** Wrap in
   `rememberCoroutineScope().launch { }`. Older tutorials show direct calls.
2. **Content-key type `T` must be Bundle-storable** — the navigator is `rememberSaveable`. Use
   `@Parcelize`.
3. **`navigateBack` clears the entire history and returns `false`** when there is no previous
   destination. Gate on `canNavigateBack(behavior)` first.
4. **`initialDestinationHistory` is read only on first creation.** Changing it later has no effect;
   the saved history wins.
5. **Do not implement `ThreePaneScaffoldNavigator`** — androidx's KDoc recommends building your own
   API instead.
6. `isDestinationHistoryAware` is a **`var`**; flipping it changes which
   `calculateThreePaneScaffoldValue` overload runs and triggers a `snapTo`, not an animation.
7. The navigator's default `scaffoldDirective` is already
   `calculatePaneScaffoldDirective(currentWindowAdaptiveInfoV2())` — do not "fix" it with the
   deprecated `currentWindowAdaptiveInfo()`.

**Back behaviour**

8. **`BackNavigationBehavior` is a `value class` over `String`, not an enum** — no `values()`, no
   exhaustive `when`.
9. **Use the same behaviour for `canNavigateBack`, `navigateBack`, `seekBack` and the predictive-back
   handler**, or the preview animates to a different destination than the commit.
10. **`PopUntilContentChange` needs a meaningful `contentKey`** with structural equality — with the
    untyped `<Any>` navigator it degenerates.
11. **`PopLatest` and `PopUntilContentChange` carry an explicit androidx warning**: multi-pane layouts
    create back stacks impossible in single-pane, so a size change mid-navigation *"may result in
    unintuitive behavior."*
12. With the default `PopUntilScaffoldValueChange` on a **dual-pane** layout, selecting a second item
    changes no layout — so back can exit the screen. That is by design.

**Predictive back**

13. **`backBehavior` is required** on `ThreePaneScaffoldPredictiveBackHandler` (no default).
14. **Predictive back requires the `scaffoldState` overload** of the scaffold — with `value =` there
    is no transition to seek and the gesture shows nothing.
15. **Predictive back needs `android:enableOnBackInvokedCallback="true"`** on Android 15 and lower.
    Android 16+ enables it by default. Missing it fails silently.
16. `NavigableListDetailPaneScaffold` / `NavigableSupportingPaneScaffold` are **android-only**
    (`androidMain`) — they do not exist in common code.

**Pane expansion**

17. **`PaneExpansionState.animateTo(anchor)` throws** if the anchor is not in the list passed to
    `rememberPaneExpansionState`. **`setFirstPaneProportion` throws** outside `[0f, 1f]`.
    **`initialAnchoredIndex` out of range throws.**
18. **`firstPaneWidth` / `firstPaneProportion` are write-only** from app code (internal getters). Only
    `currentAnchor` and `isUnspecified()` are readable.
19. **`setFirstPaneWidth` takes pixels (`Int`)**, not `Dp`.
20. **Setting width clears proportion and vice versa, and both clear `currentAnchor` to `null`.**
    Render precedence is user drag > setter > directive defaults.
21. **`remember` your anchor list** — it is a `remember`/`LaunchedEffect` key inside
    `rememberPaneExpansionState`.
22. **`PaneExpansionState`'s constructor is `internal`** — only `rememberPaneExpansionState` produces
    instances.
23. **The scaffold's auto-created default expansion state is mutable only when
    `paneExpansionDragHandle != null`.** Pass your own state to drive the split programmatically
    without a handle.
24. **Share one `MutableInteractionSource`** between `Modifier.paneExpansionDraggable` and
    `VerticalDragHandle`, or press/drag visuals never fire.
25. **`defaultDragHandleSemantics(...)` is deprecated** — `paneExpansionDraggable` installs default
    semantics when `semanticsProperties == null`.
26. **`VerticalDragHandle` lives in `androidx.compose.material3`**, versioned with material3, **not**
    with the adaptive 1.3.0 line. Its `sizes`/`colors`/`shapes` parameterisation at material3 **1.4.0
    stable is UNVERIFIED** — the signature above is from `current.txt` (1.5.0-alphaNN).
27. **`PaneExpansionStateKey` and `PaneExpansionStateKeyProvider` are `sealed`** — you cannot supply a
    custom key. Your two options are `navigator.scaffoldValue` (per-layout) and
    `PaneExpansionStateKey.Default` (shared).

---

## 18. Quick reference

```kotlin
val scope     = rememberCoroutineScope()
val navigator = rememberListDetailPaneScaffoldNavigator<MyKey>()   // typed; @Parcelize MyKey

NavigableListDetailPaneScaffold(                     // adds predictive back for you
    navigator = navigator,
    defaultBackBehavior = BackNavigationBehavior.PopUntilScaffoldValueChange,
    listPane = {
        AnimatedPane(Modifier.preferredWidth(360.dp)) {
            MyList(
                selected = navigator.currentDestination?.contentKey,
                onClick = { key -> scope.launch {
                    navigator.navigateTo(ListDetailPaneScaffoldRole.Detail, key)
                } },
            )
        }
    },
    detailPane = {
        AnimatedPane {
            MyDetail(onBack = { scope.launch { navigator.navigateBack() } })
        }
    },
    extraPane = { AnimatedPane { MyExtra() } },
    paneExpansionState = rememberPaneExpansionState(
        keyProvider = navigator.scaffoldValue,
        anchors = rememberedAnchors,
        initialAnchoredIndex = 1,
    ),
    paneExpansionDragHandle = { state ->
        val src = remember { MutableInteractionSource() }
        VerticalDragHandle(
            modifier = Modifier.paneExpansionDraggable(
                state, LocalMinimumInteractiveComponentSize.current, src),
            interactionSource = src,
        )
    },
)

// raw scaffold: YOU add predictive back, and you must use scaffoldState
ThreePaneScaffoldPredictiveBackHandler(navigator, BackNavigationBehavior.PopUntilScaffoldValueChange)
ListDetailPaneScaffold(
    directive = navigator.scaffoldDirective,
    scaffoldState = navigator.scaffoldState,   // NOT `value =` if you want animation
    listPane = { AnimatedPane { } },
    detailPane = { AnimatedPane { } },
)

// supporting pane
val sNav = rememberSupportingPaneScaffoldNavigator<Any>()          // untyped is fine here
NavigableSupportingPaneScaffold(
    navigator = sNav,
    mainPane = { AnimatedPane { } },
    supportingPane = { AnimatedPane(Modifier.preferredWidth(0.3f)) { } },  // 30%
)
```

| Constant / default | Value |
| --- | --- |
| default `BackNavigationBehavior` | `PopUntilScaffoldValueChange` |
| default `isDestinationHistoryAware` | `true` |
| default list-detail initial history | `[ThreePaneScaffoldDestinationItem(ListDetailPaneScaffoldRole.List)]` |
| default supporting initial history | `[ThreePaneScaffoldDestinationItem(SupportingPaneScaffoldRole.Main)]` |
| `seekBack` default `fraction` | `1.0f` |
| `PaneExpansionState.Unspecified` | `-1` |
| `initialAnchoredIndex` default | `-1` (no initial anchor) |
| `DefaultAnchoringAnimationSpec` | `spring(dampingRatio = 0.8f, stiffness = 380f, visibilityThreshold = 1f)` |
| `AnchoringVelocityThreshold` (private) | `200F` |

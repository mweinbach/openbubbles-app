# Worked Screens — List, Detail, Feed, and the Adaptive Pair

Four complete screens. Each one compiles as written at **`material3` 1.5.0-alpha26** /
**`material3-adaptive` 1.3.0**, and each is the *whole* screen — states, insets, semantics, motion —
not a fragment. Copy one and change the data.

**Provenance `[API-alpha26]`.** Every `material3` / `material3-adaptive` composable name and
`*Defaults` member name in this file was machine-checked against
`compose/material3/material3/api/current.txt` and `compose/material3/adaptive/*/api/current.txt`
at the alpha26 / 1.3.0 SHA. Non-material3 APIs (foundation, ui, animation, navigation3,
third-party) were **not** checked; anything that could not be confirmed is flagged `[UNVERIFIED]`
inline at its use site.

They share a domain model (§0) so screens 1, 2 and 4 fit together as one feature.

| § | Screen | The one thing it teaches |
| --- | --- | --- |
| 1 | `LibraryListScreen` | A list with all four states, a flexible hero bar, and correct edge-to-edge |
| 2 | `TrackDetailScreen` | Container transform in, hero type, a connected action row, predictive back |
| 3 | `HomeFeedScreen` | Width-bucket-driven grid + a carousel that clips to its mask |
| 4 | `LibraryPane` | The same list + detail inside `NavigableListDetailPaneScaffold` |

## Provenance markers

| Marker | Meaning |
| --- | --- |
| **[VERIFIED]** | Signature read from the metalava `current.txt` in the androidx checkout at HEAD `360e8cba7ae6` (2026-08-14, post-alpha26), or from `1.3.0-rc01.txt` for adaptive. |
| **[CORPUS …]** | Pattern lifted from a shipping repo; path given. |
| **[COMPOSED]** | Assembled here from verified pieces. Not lifted from any single repo — but every API call in it is verified. |
| **[UNVERIFIED]** | Flagged inline. There are three in this file; all are marked. |

## Ground rules applied throughout

- `currentWindowAdaptiveInfoV2()`, never `currentWindowAdaptiveInfo()`.
- `isWidthAtLeastBreakpoint(...)`; `when` chains run **largest → smallest** because the predicate
  is `>=`. Buckets: 0 / 600 / 840 / 1200 / 1600.
- `@OptIn(ExperimentalMaterial3ExpressiveApi::class)` appears **exactly four times** in this file,
  and only for `LoadingIndicator`, `MaterialShapes` + `toShape()`, and
  `PullToRefreshDefaults.LoadingIndicator`. Everything else has graduated. Do not add more.
- `FilledTonalToggleButton`, never `TonalToggleButton`. `ToggleButtonDefaults.shapesFor(Dp)` or the
  `ToggleButtonShapes(...)` constructor, never `ToggleButtonDefaults.shapes(...)` — that overload is
  `DeprecationLevel.HIDDEN` and invisible to the compiler.
- `SegmentedListItem`'s `shapes` parameter is **required**, not optional. **[VERIFIED]** Every
  overload takes `shapes: ListItemShapes` as a non-default parameter. Forgetting it is a compile
  error that reads like an overload-resolution failure.
- List-detail roles: **List = Secondary, Detail = Primary.** Write
  `ListDetailPaneScaffoldRole.Detail`, never `ThreePaneScaffoldRole.Primary`.

---

# 0. The shared model and state contract

Every screen below consumes these. Nothing here is Material-specific; it is here so the screens are
mutually consistent and so the `@Preview`s have something to render.

```kotlin
package com.example.expressive.library

import android.os.Parcelable
import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.parcelize.Parcelize

@Immutable
data class Track(
    val id: String,
    val title: String,
    val artist: String,
    val album: String,
    val durationLabel: String,
    val artworkUrl: String?,
    val isFavourite: Boolean = false,
)

/**
 * The content key for the pane scaffold in §4.
 *
 * MUST be Bundle-storable: `rememberListDetailPaneScaffoldNavigator` is a `rememberSaveable`, so a
 * plain data class here crashes on process-death restore — at runtime, not at compile time.
 */
@Parcelize
data class TrackKey(val id: String) : Parcelable

/**
 * One sealed hierarchy for the four states. The alternative — `isLoading` + `error` + `items`
 * booleans on one class — lets you express "loading AND error AND 12 items", which the UI then has
 * to disambiguate. Make the impossible states unrepresentable and the screen's `when` is exhaustive.
 */
@Immutable
sealed interface LibraryUiState {
    data object Loading : LibraryUiState
    data object Empty : LibraryUiState
    data class Error(val message: String) : LibraryUiState
    data class Content(
        val tracks: List<Track>,
        val filter: LibraryFilter = LibraryFilter.All,
        val isRefreshing: Boolean = false,
    ) : LibraryUiState
}

enum class LibraryFilter(val label: String) { All("All"), Recent("Recent"), Favourites("Favourites") }

class LibraryViewModel(
    private val repository: TrackRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow<LibraryUiState>(LibraryUiState.Loading)
    val uiState: StateFlow<LibraryUiState> = _uiState.asStateFlow()

    init { load() }

    fun load() {
        viewModelScope.launch {
            _uiState.value = LibraryUiState.Loading
            _uiState.value = runCatching { repository.tracks() }
                .fold(
                    onSuccess = { if (it.isEmpty()) LibraryUiState.Empty else LibraryUiState.Content(it) },
                    onFailure = { LibraryUiState.Error(it.message ?: "Could not load your library") },
                )
        }
    }

    fun refresh() {
        val current = _uiState.value as? LibraryUiState.Content ?: return load()
        viewModelScope.launch {
            _uiState.value = current.copy(isRefreshing = true)
            _uiState.value = runCatching { repository.tracks() }
                .fold(
                    onSuccess = { if (it.isEmpty()) LibraryUiState.Empty else current.copy(tracks = it, isRefreshing = false) },
                    onFailure = { current.copy(isRefreshing = false) },   // keep content, surface via snackbar
                )
        }
    }

    fun setFilter(filter: LibraryFilter) {
        _uiState.update { if (it is LibraryUiState.Content) it.copy(filter = filter) else it }
    }

    fun toggleFavourite(id: String) {
        viewModelScope.launch { repository.toggleFavourite(id); refresh() }
    }
}

interface TrackRepository {
    suspend fun tracks(): List<Track>
    suspend fun track(id: String): Track
    suspend fun toggleFavourite(id: String)
}
```

**The screen split that makes all of this testable and previewable** — every screen below follows it:

```kotlin
// Stateful: owns the ViewModel. One line. Never previewed, never screenshot-tested.
@Composable
fun LibraryRoute(viewModel: LibraryViewModel, onTrackClick: (String) -> Unit) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    LibraryListScreen(
        state = state,
        onTrackClick = onTrackClick,
        onRefresh = viewModel::refresh,
        onRetry = viewModel::load,
        onFilterChange = viewModel::setFilter,
    )
}

// Stateless: takes data + lambdas. This is what you preview and screenshot-test.
@Composable
fun LibraryListScreen(state: LibraryUiState, /* … */) { /* §1 */ }
```

A preview that calls the stateful overload renders blank, because the ViewModel has no data in the
preview host. That is the single most common reason "my preview is empty". See
`${CLAUDE_PLUGIN_ROOT}/skills/m3-expressive-review/references/testing-expressive-ui.md` §5.

## 0.1 Three shared leaf composables

Referenced by several screens below. Nothing interesting happens in them; they are here so the
screens are complete.

```kotlin
/** Swap `Box` for your image loader (Coil `AsyncImage`, Glide, whatever). */
@Composable
fun TrackArtwork(
    track: Track,
    modifier: Modifier = Modifier,
    size: Dp = Dp.Unspecified,
    contentScale: ContentScale = ContentScale.Crop,
) {
    Box(
        modifier = modifier
            .then(if (size != Dp.Unspecified) Modifier.size(size) else Modifier)
            .clip(MaterialTheme.shapes.medium)
            .background(MaterialTheme.colorScheme.surfaceContainerHighest),
    )
}

/** Shown in the detail pane when nothing is selected. NOT an empty pane — that reads as a bug. */
@Composable
fun DetailPlaceholder(text: String, modifier: Modifier = Modifier) {
    Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(
            text = text,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
fun TrackDescription(
    track: Track,
    expanded: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = "${track.title} from ${track.album}.",
            style = MaterialTheme.typography.bodyLarge,
            maxLines = if (expanded) Int.MAX_VALUE else 3,
            overflow = TextOverflow.Ellipsis,
            // Height is a SIZE change, so this is spatial motion — a spring that may overshoot.
            // A fade here would look like the text was replaced rather than revealed.
            modifier = Modifier.animateContentSize(MaterialTheme.motionScheme.defaultSpatialSpec()),
        )
        TextButton(onClick = onToggle) { Text(if (expanded) "Show less" else "Show more") }
    }
}
```

Needs `androidx.compose.animation.animateContentSize`, `androidx.compose.material3.TextButton`,
`androidx.compose.ui.unit.Dp`, and the layout/graphics imports already listed in §1.

---

# 1. `LibraryListScreen` — the list

**What this demonstrates.** `LargeFlexibleTopAppBar` with a subtitle and `exitUntilCollapsed`
behaviour; a filter row built as a hand-assembled connected `ToggleButton` group; a `LazyColumn` of
`SegmentedListItem`s; a FAB; expressive pull-to-refresh; and distinct loading / empty / error /
content states. Insets and `Scaffold` wiring are correct — content scrolls *under* the bar, the FAB
clears the navigation bar, and nothing is double-padded.

**[COMPOSED]** — the segmented-list body follows
`/root/work/repos/Tomato/shared/src/commonMain/kotlin/org/nsh07/pomodoro/ui/settingsScreen/screens/SettingsMainScreen.kt:166-190`;
the filter row follows
`/root/work/repos/vivi-music/app/src/main/kotlin/com/music/vivi/ui/screens/library/LibraryAlbumsScreen.kt`;
the pull-to-refresh override follows
`/root/work/repos/Med/app/src/main/kotlin/com/fedeveloper95/med/elements/MainActivity/Tabs/Stats.kt:198-230`.

```kotlin
package com.example.expressive.library

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.ToggleButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

@Composable
fun LibraryListScreen(
    state: LibraryUiState,
    onTrackClick: (String) -> Unit,
    onRefresh: () -> Unit,
    onRetry: () -> Unit,
    onFilterChange: (LibraryFilter) -> Unit,
    modifier: Modifier = Modifier,
    // The two parameters §4 needs. Defaulted, so this screen still stands alone on a phone and in
    // a @Preview. `null` selection = nothing highlights; shared elements on = the container
    // transform runs. Both are inverted by the pane scaffold — see §4.1.
    selectedId: String? = null,
    sharedElementsEnabled: Boolean = true,
) {
    // exitUntilCollapsed: the big headline shrinks to a small bar and STAYS. The right behaviour for
    // a screen whose identity is the headline. enterAlways would make it come back on every upward
    // flick, which fights the hero.                                              [VERIFIED — alpha17+]
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val listState = rememberLazyListState()
    val snackbarHostState = remember { SnackbarHostState() }

    val trackCount = (state as? LibraryUiState.Content)?.tracks?.size ?: 0

    Scaffold(
        topBar = {
            LargeFlexibleTopAppBar(
                title = { Text("Library") },
                // subtitle is `(@Composable () -> Unit)? = null` on the FLEXIBLE bars — an ordinary
                // optional named param. On plain `TopAppBar` it is a non-null positional #2 on a
                // separate overload. Do not carry one habit into the other.       [VERIFIED]
                subtitle = {
                    Text(
                        text = if (trackCount == 0) "Your saved tracks" else "$trackCount tracks",
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                actions = {
                    IconButton(onClick = { /* open search */ }) {
                        Icon(Icons.Default.Search, contentDescription = "Search library")
                    }
                    IconButton(onClick = { /* open overflow */ }) {
                        Icon(Icons.Default.MoreVert, contentDescription = "More options")
                    }
                },
                scrollBehavior = scrollBehavior,
            )
        },
        floatingActionButton = {
            // Regular FAB, not Medium/Large. One hero per screen and the hero here is the app bar.
            FloatingActionButton(onClick = { /* add */ }) {
                Icon(Icons.Default.Add, contentDescription = "Add a track")
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        // REQUIRED. On the Scaffold — not on the app bar, not on the LazyColumn. Omitting it is the
        // #1 cause of "my scroll behaviour does nothing".
        modifier = modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
    ) { innerPadding ->

        // One AnimatedContent across the four states, driven by the theme's effects spec. State
        // swaps are a cross-fade (an effect), never a slide (spatial) — nothing moved, the content
        // was replaced.
        AnimatedContent(
            targetState = state,
            transitionSpec = {
                fadeIn(MaterialTheme.motionScheme.defaultEffectsSpec())
                    .togetherWith(fadeOut(MaterialTheme.motionScheme.defaultEffectsSpec()))
            },
            contentKey = { it::class },      // don't re-animate on every Content -> Content update
            label = "library-state",
            modifier = Modifier.fillMaxSize(),
        ) { target ->
            when (target) {
                LibraryUiState.Loading -> LibraryLoading(contentPadding = innerPadding)

                LibraryUiState.Empty -> LibraryEmpty(
                    contentPadding = innerPadding,
                    onAdd = { /* add */ },
                )

                is LibraryUiState.Error -> LibraryError(
                    message = target.message,
                    onRetry = onRetry,
                    contentPadding = innerPadding,
                )

                is LibraryUiState.Content -> LibraryContent(
                    content = target,
                    listState = listState,
                    contentPadding = innerPadding,
                    onTrackClick = onTrackClick,
                    onRefresh = onRefresh,
                    onFilterChange = onFilterChange,
                    selectedId = selectedId,
                    sharedElementsEnabled = sharedElementsEnabled,
                )
            }
        }
    }
}
```

## 1.1 The content state

```kotlin
@OptIn(ExperimentalMaterial3ExpressiveApi::class)   // PullToRefreshDefaults.LoadingIndicator
@Composable
private fun LibraryContent(
    content: LibraryUiState.Content,
    listState: androidx.compose.foundation.lazy.LazyListState,
    contentPadding: PaddingValues,
    onTrackClick: (String) -> Unit,
    onRefresh: () -> Unit,
    onFilterChange: (LibraryFilter) -> Unit,
    selectedId: String?,
    sharedElementsEnabled: Boolean,
) {
    val pullState = rememberPullToRefreshState()

    val visible = remember(content.tracks, content.filter) {
        when (content.filter) {
            LibraryFilter.All -> content.tracks
            LibraryFilter.Recent -> content.tracks.take(20)
            LibraryFilter.Favourites -> content.tracks.filter { it.isFavourite }
        }
    }

    // One shared colour object for the whole segmented group. segmentedColors() (not colors())
    // supplies the selected/pressed container treatment the segmented item expects. [VERIFIED]
    val itemColors = ListItemDefaults.segmentedColors(
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
    )

    PullToRefreshBox(
        isRefreshing = content.isRefreshing,
        onRefresh = onRefresh,
        state = pullState,
        modifier = Modifier.fillMaxSize(),
        // The DEFAULT indicator is the baseline M3 arrow. You get the Expressive morphing one only
        // by overriding this slot. Two lines; the whole upgrade.
        indicator = {
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
                PullToRefreshDefaults.LoadingIndicator(
                    state = pullState,
                    isRefreshing = content.isRefreshing,
                )
            }
        },
    ) {
        LazyColumn(
            state = listState,
            // contentPadding, NOT Modifier.padding. This is what lets content scroll under the
            // collapsing bar while the first and last items stay clear of it. Modifier.padding
            // clips the scroll container and kills the collapse entirely.
            contentPadding = contentPadding,
            verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap),
            modifier = Modifier.fillMaxSize(),
        ) {
            item(key = "filters") {
                FilterRow(
                    selected = content.filter,
                    onSelect = onFilterChange,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                )
            }

            itemsIndexed(
                items = visible,
                // A stable key is not optional. Without it `animateItem` animates the wrong rows,
                // and a width change that reflows the list loses item state.
                key = { _, track -> track.id },
            ) { index, track ->
                SegmentedListItem(
                    // The SELECTABLE overload: `selected` is positional #1, before onClick, and
                    // `shapes` is still positional #3. On a phone selectedId is null, so nothing
                    // stays highlighted after you navigate back.
                    selected = track.id == selectedId,
                    onClick = { onTrackClick(track.id) },
                    // `shapes` is REQUIRED on every SegmentedListItem overload.        [VERIFIED]
                    // segmentedShapes(index, count) computes the positional corner logic: rounded
                    // outer corners, near-square inner ones, so the run reads as one object.
                    // As of alpha25 it handles count == 1 correctly on its own — delete any
                    // `if (count == 1) Modifier.clip(...)` workaround you inherited. (I2ea1c)
                    shapes = ListItemDefaults.segmentedShapes(index = index, count = visible.size),
                    colors = itemColors,
                    leadingContent = { TrackArtwork(track, size = 48.dp) },
                    supportingContent = {
                        Text("${track.artist} · ${track.album}", maxLines = 1, overflow = TextOverflow.Ellipsis)
                    },
                    trailingContent = { Text(track.durationLabel, style = MaterialTheme.typography.labelMedium) },
                    modifier = Modifier
                        .padding(horizontal = 16.dp)
                        // The source half of the container transform (§2.2). Inert when both panes
                        // are visible, because a shared key with two live copies produces a
                        // nonsensical flight.
                        .then(
                            if (sharedElementsEnabled) {
                                Modifier.sharedBoundsReveal(
                                    sharedContentState = LocalSharedTransitionScope.current
                                        .rememberSharedContentState("track-${track.id}"),
                                    clipShape = MaterialTheme.shapes.large,
                                )
                            } else Modifier
                        )
                        .animateItem(),
                ) {
                    // The headline is the TRAILING `content` lambda on every expressive overload.
                    // The deprecated baseline overload takes `headlineContent` as parameter #1.
                    // Both are on the classpath, which is why mixing them gives a confusing
                    // "no applicable overload".
                    Text(track.title, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }

            // Breathing room under the last row so the FAB never covers it. `contentPadding`
            // already accounts for the navigation bar; this is for the FAB.
            item(key = "fab-spacer") { Spacer(Modifier.height(88.dp)) }
        }
    }
}
```

## 1.2 The filter row — a hand-assembled connected group

```kotlin
@Composable
private fun FilterRow(
    selected: LibraryFilter,
    onSelect: (LibraryFilter) -> Unit,
    modifier: Modifier = Modifier,
) {
    val entries = LibraryFilter.entries
    Row(
        modifier = modifier.fillMaxWidth(),
        // 2dp. `ButtonGroupDefaults.HorizontalArrangement` is the ~12dp STANDARD spacing — using
        // the wrong one is the difference between a segmented control and three loose buttons.
        horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween),
    ) {
        entries.forEachIndexed { index, filter ->
            ToggleButton(
                checked = selected == filter,
                onCheckedChange = { onSelect(filter) },
                // The connected*ButtonShapes() helpers keep their pre-alpha25 signatures and are
                // UNAFFECTED by the ToggleButtonDefaults.shapes -> shapesFor break.  [VERIFIED]
                shapes = when {
                    entries.size == 1 -> ButtonGroupDefaults.connectedLeadingButtonShapes()
                    index == 0 -> ButtonGroupDefaults.connectedLeadingButtonShapes()
                    index == entries.lastIndex -> ButtonGroupDefaults.connectedTrailingButtonShapes()
                    else -> ButtonGroupDefaults.connectedMiddleButtonShapes()
                },
                modifier = Modifier
                    .weight(1f)
                    // A single-select connected group is a radio group to a screen reader.
                    // Without this it announces as three independent checkboxes.
                    .semantics { role = Role.RadioButton },
            ) {
                Text(filter.label)
            }
        }
    }
}
```

**Why this and not the `ButtonGroup` composable.** `ButtonGroup`'s signature changed incompatibly at
alpha22 (`overflowIndicator` became the first, required, positional parameter), its scope became a
`sealed interface` at alpha25, and `Modifier.animateWidth`'s `compressionLimit` changed from
`PaddingValues` to `Dp`. The hand-assembled form above survived all three, needs no opt-in, and gives
you `weight(1f)`. Reach for the real `ButtonGroup` only when you want its press-squeeze interaction
or overflow-into-a-menu behaviour — see
`${CLAUDE_PLUGIN_ROOT}/skills/m3-expressive-components/references/buttons.md` §6A.

## 1.3 Loading, empty, error

```kotlin
@OptIn(ExperimentalMaterial3ExpressiveApi::class)   // LoadingIndicator
@Composable
private fun LibraryLoading(contentPadding: PaddingValues) {
    Box(
        modifier = Modifier.fillMaxSize().padding(contentPadding),
        contentAlignment = Alignment.Center,
    ) {
        // The Expressive default spinner: a looping morph through seven MaterialShapes, not a
        // rotating arc. Correct for waits under ~5 seconds. Past that, or if the process becomes
        // determinate, switch to a progress indicator instead — do not swap mid-flight.
        LoadingIndicator()
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)   // MaterialShapes + toShape()
@Composable
private fun LibraryEmpty(contentPadding: PaddingValues, onAdd: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(contentPadding).padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
    ) {
        // A MaterialShapes silhouette as the empty-state mark. This is a legitimate hero moment:
        // the screen is otherwise blank, so the shape is not competing with anything, and it makes
        // an empty state feel intentional rather than broken.
        //
        // `toShape()` is @Composable and takes `startAngle: Int` (default 0), NOT a Float. [VERIFIED]
        Box(
            modifier = Modifier
                .size(96.dp)
                .clip(MaterialShapes.Cookie9Sided.toShape())
                .background(MaterialTheme.colorScheme.secondaryContainer),
        )
        Text(
            text = "Nothing here yet",
            // The Expressive emphasized role. Use these instead of `.copy(fontWeight = Bold)` —
            // emphasized styles change weight AND tracking AND (with a variable font) width.
            style = MaterialTheme.typography.headlineSmallEmphasized,
        )
        Text(
            text = "Tracks you save will show up here.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        ExtendedFloatingActionButton(
            onClick = onAdd,
            icon = { Icon(Icons.Default.Add, contentDescription = null) },
            text = { Text("Add a track") },
        )
    }
}

@Composable
private fun LibraryError(message: String, onRetry: () -> Unit, contentPadding: PaddingValues) {
    Column(
        modifier = Modifier.fillMaxSize().padding(contentPadding).padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
    ) {
        Text("Something went wrong", style = MaterialTheme.typography.headlineSmallEmphasized)
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        // NOT a filled button. An error state is not the place to spend your emphasis budget —
        // the user is already looking at the only control on the screen.
        OutlinedButton(onClick = onRetry) { Text("Try again") }
    }
}
```

## 1.4 Expressive decisions — and the ones deliberately not made

| Decision | Where | Why |
| --- | --- | --- |
| **The hero is the app bar.** `LargeFlexibleTopAppBar` + subtitle + `exitUntilCollapsed`. | §1 | The cheapest legitimate hero moment on a screen: two slots, no custom drawing. The subtitle is a live count, so it is semantic, not decoration. |
| Segmented list with `segmentedShapes` + `SegmentedGap` | §1.1 | Tactic 4 — containment. Rows are variations on one thing, so one container. Press-state shape morph comes free from the `ListItemShapes` triple; there is no animation code anywhere in the list. |
| Connected `ToggleButton` group for filters | §1.2 | Says "these three are alternatives in one set" structurally, not just visually. `Role.RadioButton` makes that true for TalkBack too. |
| Expressive pull-to-refresh indicator | §1.1 | A two-line slot override that replaces the baseline arrow with the morphing loading indicator. Highest ratio of expressiveness to code in the file. |
| `MaterialShapes.Cookie9Sided` in the empty state | §1.3 | The screen is empty; a shape here competes with nothing. Tactic 1 depends on contrast, and an empty screen is maximum contrast. |
| **Not** a Medium or Large FAB | §1 | Large/XLarge are hero controls. The bar is already the hero; a second one halves both. |
| **Not** elevated or outlined cards | §1.1 | Separation comes from surface tier + shape + spacing. `surfaceContainer` under `surface` is a depth step that survives dark mode and AMOLED; shadows do not. |
| **Not** a shape morph on the list rows | §1.1 | The `segmentedShapes` press state is already a morph. Adding a second one per row makes the whole list twitch. |
| **Not** spatial motion on state swaps | §1 | Nothing moved — the content was replaced. Effects spec (fade), not spatial. |
| **Not** `enterAlwaysScrollBehavior` | §1 | The headline is the identity of the screen; making it flick back on every upward scroll turns the hero into a nuisance. |

**Insets checklist for this screen:**

1. `enableEdgeToEdge()` in the Activity; leave `windowInsets = TopAppBarDefaults.windowInsets` alone.
2. `Modifier.nestedScroll(...)` on the **Scaffold**.
3. `contentPadding = innerPadding` on the `LazyColumn` — never `Modifier.padding(innerPadding)`.
4. Trailing spacer for the FAB. `innerPadding` covers system bars; it does not know about the FAB.
5. If this screen is ever hosted *inside* a pane rather than at the window top, pass
   `windowInsets = WindowInsets()` to the app bar so it does not add a second status-bar pad.

---

# 2. `TrackDetailScreen` — the detail

**What this demonstrates.** Entry from the list via a shared-element container transform; a hero
image with hero typography over it; a connected action row; a collapsing app bar; back handling that
includes predictive back and that disappears when the list is visible beside it.

**[COMPOSED]** — the `sharedBoundsReveal` helper is verbatim from
`/root/work/repos/Tomato/shared/src/commonMain/kotlin/org/nsh07/pomodoro/ui/statsScreen/components/sharedBoundsReveal.kt`;
the source/destination pairing follows Tomato's `StatsMainScreen.kt:252-270` ↔ `LastWeekScreen.kt:176-186`;
the `BackHandler` collapse-before-pop follows
`/root/work/repos/Med/app/src/main/kotlin/com/fedeveloper95/med/services/MedApp.kt:204-210`.

## 2.1 App-wide shared-transition plumbing

Set this up once, at the navigation host. Do not thread `SharedTransitionScope` through every
composable signature.

```kotlin
package com.example.expressive.ui.motion

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.runtime.compositionLocalOf

// Throw from the default rather than returning null: you get a clear crash at the wrong call site
// instead of a silently-missing animation.
val LocalSharedTransitionScope = compositionLocalOf<SharedTransitionScope> {
    error("No SharedTransitionScope provided — wrap the NavHost in SharedTransitionLayout")
}
val LocalAnimatedVisibilityScope = compositionLocalOf<AnimatedVisibilityScope> {
    error("No AnimatedVisibilityScope provided — provide it inside each composable<T> { }")
}
```

```kotlin
// At the nav host. SharedTransitionLayout must WRAP the host; elements can only be shared between
// siblings under the same layout.                    [CORPUS LastChat RouteActivity.kt:660-678]
SharedTransitionLayout {
    CompositionLocalProvider(LocalSharedTransitionScope provides this) {
        NavHost(navController, startDestination = Route.Library) {
            composable<Route.Library> {
                // Re-provided PER DESTINATION: each composable<T> block is its own scope.
                CompositionLocalProvider(LocalAnimatedVisibilityScope provides this@composable) {
                    LibraryRoute(/* … */)
                }
            }
            composable<Route.TrackDetail> { entry ->
                CompositionLocalProvider(LocalAnimatedVisibilityScope provides this@composable) {
                    TrackDetailRoute(/* … */)
                }
            }
        }
    }
}
```

On **Navigation 3** you do not need `LocalAnimatedVisibilityScope` — Nav3 supplies
`LocalNavAnimatedContentScope.current` inside every `entry<T>`.

## 2.2 The reusable shared-bounds modifier

**[CORPUS Tomato]** — complete file, verbatim after the licence header, with the default
`animatedVisibilityScope` retargeted from Nav3's local to the one declared above.

```kotlin
package com.example.expressive.ui.motion

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.BoundsTransform
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.SharedTransitionDefaults
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.SharedTransitionScope.ResizeMode.Companion.scaleToBounds
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.layout.ContentScale

@Composable
fun Modifier.sharedBoundsReveal(
    sharedContentState: SharedTransitionScope.SharedContentState,
    sharedTransitionScope: SharedTransitionScope = LocalSharedTransitionScope.current,
    animatedVisibilityScope: AnimatedVisibilityScope = LocalAnimatedVisibilityScope.current,
    // [UNVERIFIED] `androidx.compose.animation.SharedTransitionDefaults.BoundsTransform` could not be
    // confirmed — compose/animation is not in the local androidx checkout, so only material3 and
    // material3-adaptive names were checked against api/current.txt. Confirm against your
    // compose-animation pin; if it does not resolve, pass your own BoundsTransform.
    boundsTransform: BoundsTransform = SharedTransitionDefaults.BoundsTransform,
    // Improvement over the original, which used bare fadeIn()/fadeOut() with default springs:
    // read the specs off the theme so the transition matches every other animation in the app.
    enter: EnterTransition = fadeIn(MaterialTheme.motionScheme.defaultEffectsSpec()),
    exit: ExitTransition = fadeOut(MaterialTheme.motionScheme.defaultEffectsSpec()),
    resizeMode: SharedTransitionScope.ResizeMode = scaleToBounds(contentScale = ContentScale.Crop),
    clipShape: Shape = MaterialTheme.shapes.largeIncreased,
    renderInOverlayDuringTransition: Boolean = true,
): Modifier =
    with(sharedTransitionScope) {
        this@sharedBoundsReveal.sharedBounds(
            sharedContentState = sharedContentState,
            animatedVisibilityScope = animatedVisibilityScope,
            boundsTransform = boundsTransform,
            enter = enter,
            exit = exit,
            resizeMode = resizeMode,
            // OverlayClip is a member of SharedTransitionScope — it only resolves inside the
            // `with(sharedTransitionScope)` block. Outside it, this does not compile.
            clipInOverlayDuringTransition = OverlayClip(clipShape),
            renderInOverlayDuringTransition = renderInOverlayDuringTransition,
        )
    }
```

The **source** half is already wired into §1.1's `SegmentedListItem` — the `.then(if
(sharedElementsEnabled) Modifier.sharedBoundsReveal(...) else Modifier)` block, keyed
`"track-${track.id}"`. §1's import list therefore also needs
`com.example.expressive.ui.motion.LocalSharedTransitionScope` and
`com.example.expressive.ui.motion.sharedBoundsReveal`. The **destination** half is §2.3.

Notes on the helper:

- `OverlayClip(clipShape)` is a member of `SharedTransitionScope` — it only resolves inside the
  `with(sharedTransitionScope)` block. Outside it, this does not compile.
- `MaterialTheme.shapes.largeIncreased` as the overlay clip is what keeps the travelling element
  looking like a Material container the whole way.
- `scaleToBounds(ContentScale.Crop)` scales rather than re-lays-out the content mid-flight. Use
  `ResizeMode.RemeasureToBounds` only when the content must genuinely reflow (long text changing
  line count); it is much more expensive.

## 2.3 The detail screen

```kotlin
package com.example.expressive.library

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PlaylistAdd
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.ToggleButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.example.expressive.ui.motion.LocalSharedTransitionScope
import com.example.expressive.ui.motion.sharedBoundsReveal

@Composable
fun TrackDetailScreen(
    track: Track,
    /**
     * `null` means "the list is visible beside me — do not render a back affordance."
     * Never hardcode `showBackButton = true`. In §4 this is derived from the scaffold directive;
     * on Nav3 it is `LocalListDetailSceneScope.current == null`.
     */
    onBack: (() -> Unit)?,
    onToggleFavourite: () -> Unit,
    onPlay: () -> Unit,
    /** Suppresses shared elements when both panes are on screen — see the pitfall note below. */
    sharedElementsEnabled: Boolean = true,
    modifier: Modifier = Modifier,
) {
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    var descriptionExpanded by rememberSaveable { mutableStateOf(false) }

    // Hoist both scopes once. Reading them inside a modifier chain works but reads badly, and the
    // AnimatedVisibilityScope is needed twice.
    val sharedScope = LocalSharedTransitionScope.current
    val avScope = LocalAnimatedVisibilityScope.current

    // Collapse-before-pop. The `enabled` guard is what lets back fall through to the navigator once
    // everything on this screen is collapsed. Every expanded expressive surface — FAB menu,
    // expanded search bar, selection mode — wants exactly this shape.
    BackHandler(enabled = descriptionExpanded) { descriptionExpanded = false }

    Scaffold(
        topBar = {
            LargeFlexibleTopAppBar(
                title = { Text(track.title) },
                subtitle = { Text(track.artist) },
                navigationIcon = {
                    // Only when the list is NOT beside us.
                    if (onBack != null) {
                        // A FilledTonalIconButton with IconButtonDefaults.shapes() gives the
                        // press-morph. `shapes` is a required positional #2 on this overload.
                        FilledTonalIconButton(
                            onClick = onBack,
                            shapes = IconButtonDefaults.shapes(),
                        ) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                        }
                    }
                },
                scrollBehavior = scrollBehavior,
            )
        },
        modifier = modifier
            .nestedScroll(scrollBehavior.nestedScrollConnection)
            // THE CONTAINER TRANSFORM. The whole Scaffold is the shared container, with the key
            // that matches the list row exactly. The destination node must exist on the FIRST
            // frame — put this behind `if (loaded)` and the transition silently degrades to a fade.
            .then(
                if (sharedElementsEnabled) {
                    Modifier.sharedBoundsReveal(
                        sharedContentState = sharedScope.rememberSharedContentState("track-${track.id}"),
                        clipShape = MaterialTheme.shapes.large,
                    )
                } else Modifier
            ),
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(innerPadding),
            verticalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            // --- Hero image -------------------------------------------------------------------
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .aspectRatio(1f)
                    // sharedElement (not sharedBounds): the artwork is IDENTICAL on both sides, so
                    // one element travelling between two positions — no cross-fade.
                    .then(
                        if (sharedElementsEnabled) {
                            with(sharedScope) {
                                Modifier.sharedElement(
                                    sharedContentState =
                                        sharedScope.rememberSharedContentState("art-${track.id}"),
                                    animatedVisibilityScope = avScope,
                                )
                            }
                        } else Modifier
                    )
                    // extraLargeIncreased (32dp) — an Expressive-only step. Cards and hero media
                    // are where largeIncreased and above earn their keep.
                    .clip(MaterialTheme.shapes.extraLargeIncreased),
            ) {
                TrackArtwork(track, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            }

            // --- Hero type --------------------------------------------------------------------
            Column(
                modifier = Modifier.padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(track.album, style = MaterialTheme.typography.headlineLargeEmphasized)
                Text(
                    text = "${track.artist} · ${track.durationLabel}",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            // --- Action row -------------------------------------------------------------------
            TrackActionRow(
                isFavourite = track.isFavourite,
                onPlay = onPlay,
                onToggleFavourite = onToggleFavourite,
                onAddToPlaylist = { /* … */ },
                onShare = { /* … */ },
                modifier = Modifier.padding(horizontal = 16.dp),
            )

            TrackDescription(
                track = track,
                expanded = descriptionExpanded,
                onToggle = { descriptionExpanded = !descriptionExpanded },
                modifier = Modifier.padding(horizontal = 16.dp),
            )
        }
    }
}
```

## 2.4 The action row — a connected group of mixed toggles and actions

```kotlin
@Composable
private fun TrackActionRow(
    isFavourite: Boolean,
    onPlay: () -> Unit,
    onToggleFavourite: () -> Unit,
    onAddToPlaylist: () -> Unit,
    onShare: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween),
    ) {
        // Play — an ACTION, not a toggle. `checked = false` with a no-op onCheckedChange that
        // performs the action is the corpus idiom for putting an action inside a connected group
        // without breaking the shape run.               [CORPUS vivi-music Queue.kt]
        ToggleButton(
            checked = false,
            onCheckedChange = { onPlay() },
            shapes = ButtonGroupDefaults.connectedLeadingButtonShapes(),
            modifier = Modifier
                .weight(2f)                              // the primary action gets twice the width
                .semantics { role = Role.Button },       // Button, not RadioButton — it is an action
        ) {
            Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(20.dp))
            Spacer(Modifier.size(ToggleButtonDefaults.IconSpacing))
            Text("Play")
        }

        // Favourite — a REAL toggle. Role.Checkbox, and a stateDescription so TalkBack says
        // "Favourite, on" rather than just re-reading the label.
        ToggleButton(
            checked = isFavourite,
            onCheckedChange = { onToggleFavourite() },
            shapes = ButtonGroupDefaults.connectedMiddleButtonShapes(),
            modifier = Modifier
                .weight(1f)
                .semantics {
                    role = Role.Checkbox
                    stateDescription = if (isFavourite) "Saved" else "Not saved"
                },
        ) {
            Icon(
                imageVector = if (isFavourite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                contentDescription = "Favourite",
                modifier = Modifier.size(20.dp),
            )
        }

        ToggleButton(
            checked = false,
            onCheckedChange = { onAddToPlaylist() },
            shapes = ButtonGroupDefaults.connectedMiddleButtonShapes(),
            modifier = Modifier.weight(1f).semantics { role = Role.Button },
        ) {
            Icon(Icons.Default.PlaylistAdd, contentDescription = "Add to playlist",
                modifier = Modifier.size(20.dp))
        }

        ToggleButton(
            checked = false,
            onCheckedChange = { onShare() },
            shapes = ButtonGroupDefaults.connectedTrailingButtonShapes(),
            modifier = Modifier.weight(1f).semantics { role = Role.Button },
        ) {
            Icon(Icons.Default.Share, contentDescription = "Share", modifier = Modifier.size(20.dp))
        }
    }
}
```

Needs these extra imports on top of §2.3's list: `androidx.compose.foundation.layout.Row`,
`androidx.compose.foundation.layout.Spacer`, `androidx.compose.material3.ToggleButtonDefaults`,
`androidx.compose.ui.semantics.stateDescription`, plus
`com.example.expressive.ui.motion.LocalAnimatedVisibilityScope`.

**If you want the press-squeeze and overflow behaviour instead**, this is the same row as a real
`ButtonGroup`. Note `overflowIndicator` is the **first, required, positional** parameter since
alpha22, and keep the Expressive opt-in — the release notes say `ButtonGroup` graduated at alpha22
but the androidx samples still carry the annotation, and the two sources have not been reconciled:

```kotlin
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun TrackActionGroup(/* … */) {
    val sources = remember { List(4) { MutableInteractionSource() } }
    ButtonGroup(
        overflowIndicator = { menuState ->
            ButtonGroupDefaults.OverflowIndicator(
                menuState,
                colors = IconButtonDefaults.filledTonalIconButtonColors(),
            )
        },
    ) {
        customItem(
            buttonGroupContent = {
                FilledTonalIconButton(
                    onClick = onPlay,
                    shapes = IconButtonDefaults.shapes(),
                    interactionSource = sources[0],
                    // 1-arg animateWidth: correct on BOTH old and new artifacts. Only call sites
                    // that pass `compressionLimit` needed touching at alpha25 (PaddingValues -> Dp).
                    modifier = Modifier.animateWidth(sources[0]),
                ) { Icon(Icons.Default.PlayArrow, contentDescription = "Play") }
            },
            menuContent = { menuState ->
                DropdownMenuItem(
                    text = { Text("Play") },
                    leadingIcon = { Icon(Icons.Default.PlayArrow, contentDescription = null) },
                    onClick = { onPlay(); menuState.dismiss() },
                )
            },
        )
        // … one customItem per action
    }
}
```

## 2.5 Predictive back

Three levels, in increasing cost. Pick the lowest one that does the job.

**a. Collapse an expanded surface first** — `BackHandler` with an `enabled` guard. Already in §2.3.
No predictive preview; that is correct, because nothing is being previewed.

**b. Navigation-level predictive pop.** On **Navigation 2**, `composable<T>` already animates and the
system handles the predictive preview; you get it for free. On **Navigation 3** you must set all
three specs — omitting `predictivePopTransitionSpec` leaves a system default that does not match, and
shared elements visibly re-target when the gesture commits:

```kotlin
// [CORPUS Tomato AppScreen.kt:295-345]
NavDisplay(
    backStack = backStack,
    onBack = backStack::onBack,
    transitionSpec = {
        fadeIn(motionScheme.defaultEffectsSpec())
            .togetherWith(fadeOut(motionScheme.defaultEffectsSpec()))
    },
    popTransitionSpec = {
        fadeIn(motionScheme.defaultEffectsSpec())
            .togetherWith(fadeOut(motionScheme.defaultEffectsSpec()))
    },
    predictivePopTransitionSpec = {
        fadeIn(motionScheme.defaultEffectsSpec())
            .togetherWith(fadeOut(motionScheme.defaultEffectsSpec()))
    },
    entryProvider = entryProvider { /* … */ },
)
```

Note the division of labour: effects specs (pure cross-fade) at the nav level, and the *shared
elements* carry all the spatial motion. If the screen also slid, the shared element would appear to
move twice.

**c. Progress-driven** — `PredictiveBackHandler` when the gesture's 0f→1f progress should drive an
animation on a **custom, non-navigation** surface (a drawer, an expanded player). Reference
implementation is JetLagged's `JetLaggedDrawer.kt:98-118`; see
`${CLAUDE_PLUGIN_ROOT}/skills/m3-expressive-motion/references/motion-recipes.md` §10c. Do not reach
for it for ordinary screen-to-screen back.

Android 15 and lower also need this in the manifest; Android 16+ enables it by default:

```xml
<application android:enableOnBackInvokedCallback="true">
```

## 2.6 Expressive decisions — and the ones deliberately not made

| Decision | Why |
| --- | --- |
| **The hero is the container transform.** The list row grows into the detail screen, carrying the artwork. | This is the product's key interaction and it is emotionally impactful — both qualifying questions answered yes. It is also brief, which is what keeps a hero moment working. |
| `sharedBounds` for the container, `sharedElement` for the artwork | `sharedBounds` when the two contents differ (row vs screen) — bounds animate and content cross-fades. `sharedElement` when the content is identical — one element, two positions, no cross-fade. |
| `shapes.extraLargeIncreased` on the hero image | 32dp is an Expressive-only step. The rest of the screen uses `large`, so the hero reads as different. |
| `headlineLargeEmphasized` for the album title | Tactic 3. One emphasized style on the one thing that matters. |
| Connected action row with mixed `Role.Button` / `Role.Checkbox` | The visual grouping claims "these belong together"; the semantics make that claim true, and still distinguish the toggle from the actions. |
| `weight(2f)` on Play | Emphasis through size inside an otherwise uniform group — cheaper and more robust than a different colour. |
| **Not** a floating toolbar | The actions are content-specific and belong with the content, not floating over it. A floating toolbar here would also collide with §4's pane layout. |
| **Not** a shape morph on the hero image | The image is already the visual anchor. A morphing silhouette on top of a photograph reads as a rendering bug. |
| **Not** a second shared element for the title text | Two travelling elements is a transition; four is a swarm. Container + artwork is the budget. |
| **Not** a `PredictiveBackHandler` | Ordinary back out of a navigation destination. The navigator's own predictive pop is correct and free. |

**The shared-element pitfall that matters most:** disable shared elements when both panes are
visible. On an expanded window the list and detail render *simultaneously*, so a shared element would
have two live copies of one key and produce a nonsensical flight. Tomato guards every one with
`if (!widthExpanded)`. This is mandatory on adaptive layouts, not polish — which is why
`sharedElementsEnabled` is a parameter of `TrackDetailScreen` and is wired to the directive in §4.

---

# 3. `HomeFeedScreen` — the feed

**What this demonstrates.** Grid column count derived from the width bucket (largest → smallest);
a `HorizontalMultiBrowseCarousel` with the `maskClip` detail that everyone misses; full-width section
headers via `maxLineSpan`; a spanning hero card; mixed content cards.

**[COMPOSED]** — the grid follows `[REPO snippets:.../adaptivelayouts/CanonicalLayoutSamples.kt:32-44]`
plus the size-class branch from the adaptive skill; the carousel follows
`/root/work/repos/vivi-music/app/src/main/kotlin/com/music/vivi/ui/screens/HomeScreen.kt:1434-1460`
and Jetcaster `Jetcaster/mobile/src/main/java/com/example/jetcaster/ui/home/Home.kt:642-679`.

```kotlin
package com.example.expressive.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfoV2
import androidx.compose.material3.carousel.HorizontalMultiBrowseCarousel
import androidx.compose.material3.carousel.rememberCarouselState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.window.core.layout.WindowSizeClass

/** The width-derived numbers, in one place, computed once per recomposition of the screen. */
private data class FeedMetrics(
    val minCellSize: Dp,
    val gutter: Dp,
    val carouselItemWidth: Dp,
    val carouselHeight: Dp,
    val heroSpan: Int,
)

@Composable
private fun rememberFeedMetrics(): FeedMetrics {
    val windowSizeClass = currentWindowAdaptiveInfoV2().windowSizeClass

    // isWidthAtLeastBreakpoint is a >= test, so a 1600dp window matches EVERY breakpoint.
    // The chain MUST run largest -> smallest or it collapses to the smallest branch for every
    // window. This is the single most common adaptive bug.
    return when {
        windowSizeClass.isWidthAtLeastBreakpoint(WindowSizeClass.WIDTH_DP_EXTRA_LARGE_LOWER_BOUND) ->
            FeedMetrics(280.dp, 32.dp, 400.dp, 380.dp, 3)
        windowSizeClass.isWidthAtLeastBreakpoint(WindowSizeClass.WIDTH_DP_LARGE_LOWER_BOUND) ->
            FeedMetrics(260.dp, 24.dp, 380.dp, 360.dp, 3)
        windowSizeClass.isWidthAtLeastBreakpoint(WindowSizeClass.WIDTH_DP_EXPANDED_LOWER_BOUND) ->
            FeedMetrics(240.dp, 24.dp, 340.dp, 340.dp, 2)
        windowSizeClass.isWidthAtLeastBreakpoint(WindowSizeClass.WIDTH_DP_MEDIUM_LOWER_BOUND) ->
            FeedMetrics(200.dp, 16.dp, 320.dp, 320.dp, 2)
        else ->
            FeedMetrics(160.dp, 16.dp, 300.dp, 300.dp, 1)
    }
}

@Composable
fun HomeFeedScreen(
    featured: List<Track>,
    sections: List<FeedSection>,
    onTrackClick: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val metrics = rememberFeedMetrics()

    Scaffold(
        topBar = {
            LargeFlexibleTopAppBar(
                title = { Text("Home") },
                subtitle = { Text("Made for you") },
                scrollBehavior = scrollBehavior,
            )
        },
        modifier = modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
    ) { innerPadding ->
        LazyVerticalGrid(
            // GridCells.Adaptive alone handles most feeds: it fits as many >= minSize columns as
            // will go, and degrades to LazyColumn behaviour on a compact width. Branch on the size
            // class only when the CONTENT of a cell should differ, not just the count — here it is
            // both, because the carousel and hero also resize.
            columns = GridCells.Adaptive(minSize = metrics.minCellSize),
            contentPadding = innerPadding,
            horizontalArrangement = Arrangement.spacedBy(metrics.gutter),
            verticalArrangement = Arrangement.spacedBy(metrics.gutter),
            modifier = Modifier
                .fillMaxSize()
                // On very wide windows constrain the grid rather than letting line length grow
                // forever. Do NOT cap the column count with GridCells.Fixed(3) instead — a 1600dp
                // desktop window then renders 500dp-wide cards.
                .widthIn(max = 1600.dp),
        ) {
            // --- Carousel section -------------------------------------------------------------
            // maxLineSpan is a property of LazyGridItemSpanScope: it is ONLY readable inside the
            // `span` lambda, never outside it.
            item(span = { GridItemSpan(maxLineSpan) }) {
                SectionHeader(title = "Featured", onSeeAll = { /* … */ })
            }
            item(span = { GridItemSpan(maxLineSpan) }) {
                FeaturedCarousel(
                    items = featured,
                    itemWidth = metrics.carouselItemWidth,
                    height = metrics.carouselHeight,
                    onClick = onTrackClick,
                )
            }

            // --- Hero card ---------------------------------------------------------------------
            // Emphasize by SPANNING, not by writing a separate layout for it. minOf(...) keeps it
            // honest on a compact window where maxLineSpan is 1.
            featured.firstOrNull()?.let { hero ->
                item(
                    key = "hero-${hero.id}",
                    span = { GridItemSpan(minOf(metrics.heroSpan, maxLineSpan)) },
                ) {
                    HeroCard(track = hero, onClick = { onTrackClick(hero.id) })
                }
            }

            // --- Mixed sections ----------------------------------------------------------------
            sections.forEach { section ->
                item(
                    key = "header-${section.id}",
                    span = { GridItemSpan(maxLineSpan) },
                ) {
                    SectionHeader(title = section.title, onSeeAll = { /* … */ })
                }
                items(
                    items = section.tracks,
                    // A stable key. Without it, a resize that changes the column count loses item
                    // state and re-runs every enter animation.
                    key = { it.id },
                ) { track ->
                    TrackCard(track = track, onClick = { onTrackClick(track.id) })
                }
            }
        }
    }
}

data class FeedSection(val id: String, val title: String, val tracks: List<Track>)
```

## 3.1 The carousel

```kotlin
@Composable
private fun FeaturedCarousel(
    items: List<Track>,
    itemWidth: Dp,
    height: Dp,
    onClick: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    // The count is a LAMBDA, re-read on recomposition. `rememberCarouselState { items.size }` and
    // `rememberCarouselState(itemCount = { items.size })` are the same function.
    val carouselState = rememberCarouselState { items.size }

    HorizontalMultiBrowseCarousel(
        state = carouselState,
        // Must be a concrete Dp — never wrap-content. The layout manager uses the first item's
        // width as its reference and sizes the rest to fit.
        preferredItemWidth = itemWidth,
        itemSpacing = 16.dp,
        // contentPadding is applied AFTER clipping: use itemSpacing for gaps between items and
        // contentPadding only for leading/trailing space.
        contentPadding = PaddingValues(horizontal = 16.dp),
        // Fix the height explicitly; items are measured against it.
        modifier = modifier.fillMaxWidth().height(height),
    ) { i ->
        val track = items[i]
        TrackArtwork(
            track = track,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .fillMaxSize()
                // THE detail everyone misses. maskClip is a CarouselItemScope modifier that clips
                // the item to the carousel's CURRENT mask, so its corners animate as it scrolls
                // between the large and small slots. `Modifier.clip(RoundedCornerShape(16.dp))`
                // gives a static corner and the item visibly clips wrong at the edges.
                //
                // It needs no import — maskClip/maskBorder are CarouselItemScope receiver
                // extensions.
                .maskClip(MaterialTheme.shapes.extraLarge)
                // maskClip BEFORE clickable, so the ripple is clipped to the mask too. Backwards,
                // and the ripple paints square over rounded artwork mid-scroll.
                .clickable { onClick(track.id) },
        )
    }
}
```

**Which carousel strategy.** Multi-browse (used here) is for scanning many items fast. Hero
(`HorizontalCenteredHeroCarousel`) is for considered selection of large media — note it takes
`itemSpacing` + `contentPadding` but **no** `preferredItemWidth`/`itemWidth`, because the hero
strategy sizes the focal item itself, and its canonical sample makes off-centre items *tap-to-focus*
(`state.animateScrollToItem(i)`) rather than tap-to-open. Uncontained preserves original aspect
ratios and cuts off edge items. Full-screen is the recommendation for a vertical carousel in
portrait.

## 3.2 Section header and cards

```kotlin
@Composable
private fun SectionHeader(title: String, onSeeAll: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.fillMaxWidth().padding(top = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        // titleLargeEmphasized, not headline*: a section header is structure, not a hero. Reserve
        // the headline roles for the one thing on the screen that IS the hero.
        Text(title, style = MaterialTheme.typography.titleLargeEmphasized)
        TextButton(onClick = onSeeAll) { Text("See all") }
    }
}

@Composable
private fun TrackCard(track: Track, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Card(
        onClick = onClick,
        // Flat. Separation comes from the surface tier, not from a shadow: surfaceContainerLow ->
        // surfaceContainer -> surfaceContainerHigh -> surfaceContainerHighest is a four-step depth
        // ladder that survives dark mode and AMOLED. Shadows do not.
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        shape = MaterialTheme.shapes.largeIncreased,
        modifier = modifier,
    ) {
        Column {
            TrackArtwork(
                track = track,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f)
                    .clip(MaterialTheme.shapes.medium),
            )
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(track.title, style = MaterialTheme.typography.titleMedium,
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(track.artist, style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

@Composable
private fun HeroCard(track: Track, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Card(
        onClick = onClick,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer,
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        // extraExtraLarge (48dp) on exactly one card in the feed. A shape is emphatic only because
        // its neighbours are not — this is the whole of Tactic 1.
        shape = MaterialTheme.shapes.extraExtraLarge,
        modifier = modifier,
    ) {
        Row(
            modifier = Modifier.padding(20.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TrackArtwork(
                track = track,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(96.dp)
                    .clip(MaterialTheme.shapes.extraLarge),
            )
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Today's pick", style = MaterialTheme.typography.labelLargeEmphasized)
                Text(track.title, style = MaterialTheme.typography.headlineSmallEmphasized,
                    maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(track.artist, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}
```

## 3.3 Expressive decisions — and the ones deliberately not made

| Decision | Why |
| --- | --- |
| **The hero is the carousel + the one spanning `HeroCard`.** | Two expressive moves on a screen whose job is browsing. Both are content-forward; neither is decoration. |
| `GridCells.Adaptive` + a five-branch metric table | Column count is derived, not enumerated. There is no "tablet layout" — there is one layout with width-derived numbers, which is what makes it survive a desktop-window drag. |
| Hero emphasised by **span**, not by a bespoke composable | One item type, one card, one span parameter. A separate `HeroLayout` composable is where phone/tablet drift starts. |
| `shapes.extraExtraLarge` on exactly one card | 48dp on everything is not expressive, it is a rounded-corner setting. |
| `primaryContainer` on exactly one card | Tactic 2: contrast is what carries the takeaway. Every other card is `surfaceContainer`. |
| `maskClip` in the carousel | Not a style choice — without it the item corners are static and clip wrong at the edges. |
| **Not** a wavy divider between sections | Section headers already separate. Decorative motion between static blocks is the classic over-application error. |
| **Not** `Modifier.animateItem` on grid cells | A feed appends and scrolls; it does not reorder. The animation would be pure cost. Add it back the day the feed gains a sort control. |
| **Not** a FAB | There is no single primary action on a browse surface. If you cannot name the FAB's verb, do not add it. |
| **Not** `derivedMediaQuery` for the breakpoints | It is experimental, runtime-flag-gated, and becomes a second source of truth that disagrees with a pane scaffold at the boundary. `WindowSizeClass` is the conservative choice and `material3.adaptive` is stable at 1.3.0. |

---

# 4. `LibraryPane` — the list and detail as one adaptive screen

**What this demonstrates.** The *same* `LibraryListScreen` and `TrackDetailScreen` wired into
`NavigableListDetailPaneScaffold`: correct roles, `AnimatedPane`, a drag handle, and state that
survives resize.

Full scaffold API — every parameter, all four `BackNavigationBehavior` values, the pane-expansion
anchors, the custom-directive fork, the Navigation 3 `ListDetailSceneStrategy` alternative — is in
`${CLAUDE_PLUGIN_ROOT}/skills/m3-adaptive/references/adaptive-recipes.md` and `pane-scaffolds.md`.
This section only shows the *integration*: what changes in the two screens you already wrote.

```kotlin
package com.example.expressive.library

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.material3.VerticalDragHandle
import androidx.compose.material3.adaptive.ExperimentalMaterial3AdaptiveApi
import androidx.compose.material3.adaptive.layout.AnimatedPane
import androidx.compose.material3.adaptive.layout.ListDetailPaneScaffoldRole
import androidx.compose.material3.adaptive.layout.PaneExpansionAnchor
import androidx.compose.material3.adaptive.layout.PaneExpansionState
import androidx.compose.material3.adaptive.layout.PaneScaffoldDirective
import androidx.compose.material3.adaptive.layout.ThreePaneScaffoldScope
import androidx.compose.material3.adaptive.layout.rememberPaneExpansionState
import androidx.compose.material3.adaptive.navigation.BackNavigationBehavior
import androidx.compose.material3.adaptive.navigation.NavigableListDetailPaneScaffold
import androidx.compose.material3.adaptive.navigation.rememberListDetailPaneScaffoldNavigator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.systemGestureExclusion
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

// The androidx reference anchor set. initialAnchoredIndex = 1 puts the split at 240dp from the
// start.                    [REPO androidx-m3 .../samples/ThreePaneScaffoldSample.kt:1099-1106]
private val ListDetailAnchors = listOf(
    PaneExpansionAnchor.Proportion(0f),
    PaneExpansionAnchor.Offset.fromStart(240.dp),
    PaneExpansionAnchor.Proportion(0.5f),
    PaneExpansionAnchor.Offset.fromEnd(240.dp),
    PaneExpansionAnchor.Proportion(1f),
)

/** `isSinglePaneLayout()` is internal in the library, so write your own. */
@OptIn(ExperimentalMaterial3AdaptiveApi::class)
private fun PaneScaffoldDirective.isSinglePane() = maxHorizontalPartitions == 1

@OptIn(ExperimentalMaterial3AdaptiveApi::class)
@Composable
fun LibraryPane(
    listState: LibraryUiState,
    detailFor: (String) -> Track?,
    onRefresh: () -> Unit,
    onRetry: () -> Unit,
    onFilterChange: (LibraryFilter) -> Unit,
    onToggleFavourite: (String) -> Unit,
    onPlay: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()

    // The navigator is a rememberSaveable — the destination history survives process death. That is
    // why TrackKey must be @Parcelize.
    val navigator = rememberListDetailPaneScaffoldNavigator<TrackKey>()

    // Selection is DERIVED from the navigator. Never hold a second `selectedId` state beside it —
    // that is how the two disagree after a resize.
    val selectedKey = navigator.currentDestination?.contentKey
    val singlePane = navigator.scaffoldDirective.isSinglePane()

    val expansionState = rememberPaneExpansionState(
        keyProvider = navigator.scaffoldValue,
        anchors = ListDetailAnchors,
        initialAnchoredIndex = 1,
    )

    // Hoist the drag-handle lambda. Recreating it every recomposition rebuilds the handle.
    val dragHandle: @Composable ThreePaneScaffoldScope.(PaneExpansionState) -> Unit =
        remember {
            { state ->
                val interactionSource = remember { MutableInteractionSource() }
                VerticalDragHandle(
                    modifier = Modifier
                        .paneExpansionDraggable(
                            state,
                            // Guarantees a >= 48dp target even though the visual handle is thin.
                            LocalMinimumInteractiveComponentSize.current,
                            interactionSource,
                        )
                        // Stops the system back-gesture edge from stealing the drag.
                        .systemGestureExclusion(),
                    interactionSource = interactionSource,
                )
            }
        }

    NavigableListDetailPaneScaffold(
        navigator = navigator,
        modifier = modifier,
        // The default and the right choice: back produces a distinct layout transition each time.
        defaultBackBehavior = BackNavigationBehavior.PopUntilScaffoldValueChange,

        listPane = {
            // AnimatedPane SPLITS your modifier: size and graphics-layer modifiers go to the
            // internal AnimatedVisibility, everything else to the inner Column. That is why
            // preferredWidth works at this call site and nowhere else.
            //
            // Do NOT write AnimatedPane(shape = …) — that parameter is post-1.3.0.  [VERIFIED]
            AnimatedPane(Modifier.preferredWidth(360.dp)) {
                LibraryListScreen(
                    state = listState,
                    onTrackClick = { id ->
                        // navigateTo / navigateBack / seekBack are SUSPEND. Tutorials predating
                        // adaptive 1.1 call them directly and do not compile.
                        scope.launch {
                            // ListDetailPaneScaffoldRole.Detail maps to ThreePaneScaffoldRole.
                            // Primary; List maps to Secondary. Always write the aliased name —
                            // getting the raw roles backwards silently reverses adapt strategies.
                            navigator.navigateTo(ListDetailPaneScaffoldRole.Detail, TrackKey(id))
                        }
                    },
                    onRefresh = onRefresh,
                    onRetry = onRetry,
                    onFilterChange = onFilterChange,
                    // Highlight the selection only when the detail is visible beside it. On a
                    // phone the row would stay highlighted after you came back, which reads as a
                    // stuck state.
                    selectedId = selectedKey?.id.takeIf { !singlePane },
                    // Shared elements OFF when both panes render simultaneously (§2.6).
                    sharedElementsEnabled = singlePane,
                )
            }
        },

        detailPane = {
            AnimatedPane {
                val track = selectedKey?.id?.let(detailFor)
                if (track == null) {
                    // A placeholder, not an empty pane. An empty half-screen reads as a bug.
                    DetailPlaceholder(text = "Choose a track")
                } else {
                    TrackDetailScreen(
                        track = track,
                        // Derived, never hardcoded. When the list is beside us it already provides
                        // the way back.
                        onBack = if (singlePane) {
                            { scope.launch { navigator.navigateBack() } }
                        } else null,
                        onToggleFavourite = { onToggleFavourite(track.id) },
                        onPlay = { onPlay(track.id) },
                        sharedElementsEnabled = singlePane,
                    )
                }
            }
        },

        paneExpansionState = expansionState,
        paneExpansionDragHandle = dragHandle,
    )
}
```

## 4.1 What changed in the two screens

**Nothing.** Both screens were already written with the four parameters the scaffold needs, all
defaulted so that each screen still stands alone on a phone and in a `@Preview`:

| Screen | Parameter | Phone default | What `LibraryPane` passes |
| --- | --- | --- | --- |
| `LibraryListScreen` | `selectedId: String?` | `null` — nothing highlights | `selectedKey?.id.takeIf { !singlePane }` |
| `LibraryListScreen` | `sharedElementsEnabled: Boolean` | `true` — container transform runs | `singlePane` |
| `TrackDetailScreen` | `onBack: (() -> Unit)?` | supplied by the navigator | `null` when the list is beside it |
| `TrackDetailScreen` | `sharedElementsEnabled: Boolean` | `true` | `singlePane` |

That is the point of the exercise: an adaptive layout should not fork your screens. Neither screen
knows what a pane is; both still preview and screenshot-test standalone. If you find yourself writing
`if (isTablet)` inside a screen body, the parameter is missing, not the branch.

## 4.2 State that survives resize

Three layers, and you need all three:

1. **The navigator** is `rememberSaveable` — destination history survives process death. Hence
   `@Parcelize` on `TrackKey`.
2. **`AnimatedPane`** wraps its content in `SaveableStateProvider(paneRole.toString())`, so each
   pane's own `rememberSaveable` state (§2.3's `descriptionExpanded`, §1's `LazyListState`) survives
   the pane being hidden and re-shown.
3. **Your screen state** still needs a `ViewModel` or `rememberSaveable`. The scaffold does not
   retain arbitrary composable state across a full recomposition of the pane content. This is why
   `LibraryUiState` lives in a `ViewModel` and is passed in — hoisting it into `LibraryPane` as a
   `remember { }` would lose it on rotation.

And if this screen sits under a `NavigationSuiteScaffold` (it should — see the starter project), the
nav host needs the `popUpTo`/`saveState`/`restoreState` triple. Without it, resizing from bar to rail
re-creates every screen and loses scroll position. That is the #1 "state lost on resize" bug and it
has nothing to do with the adaptive library.

## 4.3 Expressive decisions in the pane layout

| Decision | Why |
| --- | --- |
| `VerticalDragHandle` with `paneExpansionDraggable` | Its `DragHandleSizes` / `DragHandleColors` / `DragHandleShapes` triples give a shape morph on press and drag — the Expressive signature, on the one control the user physically manipulates. Under-used. |
| `LocalMinimumInteractiveComponentSize.current` as the touch target | The visual handle is thin; the target must not be. |
| **Not** two different app-bar colours for the two panes | Legitimate and Tomato does it (`surfaceContainer` for the list, `surfaceContainerLow` for the detail, so the panes read as distinct planes) — but it needs custom `TopAppBarColors` objects and a matching `Scaffold(containerColor = …)` or the collapse shows a seam. Add it deliberately, not by accident. |
| **Not** `AnimatedPane(shape = …)` | Post-1.3.0. Do not write it yet. |
| **Not** custom `AnimatedPane` enter/exit transitions | `PaneMotionDefaults` already reads the theme. Override per-pane only when you have a reason. |
| **Not** a `HorizontalFloatingToolbar` as navigation here | One nav container per window. If this screen lives under `NavigationSuiteScaffold`, a toolbar-as-nav is a second one. A *contextual* toolbar inside the detail pane is fine — that is content, not navigation. |

## 4.4 Pitfalls specific to this wiring

- **`navigateTo` / `navigateBack` / `seekBack` are `suspend`.** Wrap in `scope.launch { }`.
- **`T` must be Bundle-storable.** A plain data class crashes on process-death restore, at runtime.
- **Medium width is single-pane by default.** A 700dp window (portrait tablet, unfolded inner display
  in portrait) shows ONE pane. That is deliberate Material guidance. Changing it needs a custom
  directive, and the KDoc recommends against it.
- **`AnimatedPane`'s content root is a `Column`, not a `Box`.** Alignment behaves accordingly.
- **`ListDetailPaneScaffold` applies `fillMaxSize()` internally.** Sizing it from outside mostly does
  nothing.
- **If you use `ListDetailPaneScaffold` directly instead of the `Navigable*` wrapper**, you must add
  `ThreePaneScaffoldPredictiveBackHandler` yourself and pass `scaffoldState = navigator.scaffoldState`
  (not `value =`), or you get no animation. The wrapper's entire value-add is those two lines.
- **`navigateBack` returns `Boolean`** and returns `false` after clearing the history when there is
  no previous destination.

---

# 5. Cross-screen checklist

Run this before calling any of these screens done.

**Compiles at the pin**

- [ ] `./gradlew :app:compileDebugKotlin` actually ran. On alpha25/alpha26 this is the only reliable
      check — nine documented ways alpha24-era source fails.
- [ ] No `TonalToggleButton`, no `ToggleButtonDefaults.shapes(`, no `containsWidthDp`, no
      `currentWindowAdaptiveInfo()` without the `V2`.
- [ ] Every `SegmentedListItem` passes `shapes`.
- [ ] Imports are `androidx.compose.material3.*`, never `androidx.compose.material.*`.

**States**

- [ ] Loading, empty, error and content all reachable and all previewed.
- [ ] Refresh failure keeps the content on screen and surfaces the error out-of-band; it does not
      blank the list.
- [ ] Empty and error states have exactly one action each.

**Insets and scroll**

- [ ] `enableEdgeToEdge()` called; app bars keep their default `windowInsets`.
- [ ] `Modifier.nestedScroll(scrollBehavior.nestedScrollConnection)` on the `Scaffold`.
- [ ] `contentPadding = innerPadding` on every lazy container; no `Modifier.padding(innerPadding)`
      around a scroll container.
- [ ] Trailing spacer clears the FAB.
- [ ] Nested app bars (inside a pane, or as a list item) pass `windowInsets = WindowInsets()`.

**Adaptive**

- [ ] Every `when` on `isWidthAtLeastBreakpoint` runs largest → smallest.
- [ ] All five width buckets considered, even if two share a branch.
- [ ] Shared elements disabled when two panes are visible.
- [ ] The back affordance and the selection highlight are both *derived*, never hardcoded.
- [ ] Every lazy `items` has a stable `key`.
- [ ] Resize from phone width to desktop width and back with content scrolled: nothing resets.

**Expressive budget**

- [ ] Name the hero moment on each screen. If you cannot, there isn't one — that is fine, but check
      that you did not spend the budget by accident.
- [ ] No screen has more than two. (List: the bar. Detail: the container transform. Feed: the
      carousel + the spanning hero card, which is already the ceiling.)
- [ ] Emphasis is relational — the emphasized type role, the bigger shape, the container colour each
      appear on *one* thing per screen.

**Accessibility**

- [ ] `Role.RadioButton` on single-select groups, `Role.Button` on action groups,
      `Role.Checkbox` + `stateDescription` on real toggles.
- [ ] Icon-only controls have a `contentDescription`; decorative icons have `null`.
- [ ] Every state screen reads sensibly with TalkBack, including the empty-state shape (it is
      decorative — no description).
- [ ] Check at `fontScale = 1.5f`. Expressive components run *shorter* than their predecessors, so
      headroom for large text is thinner than you expect.

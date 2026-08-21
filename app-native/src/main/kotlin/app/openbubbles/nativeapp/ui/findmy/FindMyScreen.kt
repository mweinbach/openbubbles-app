package app.openbubbles.nativeapp.ui.findmy

import android.content.Context
import android.content.Intent
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CenterFocusStrong
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.LayersClear
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.PauseCircle
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.rounded.Laptop
import androidx.compose.material.icons.rounded.Smartphone
import androidx.compose.material.icons.rounded.Tablet
import androidx.compose.material.icons.rounded.Watch
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import app.openbubbles.nativeapp.ui.common.ChatAvatar
import app.openbubbles.nativeapp.ui.common.SegmentedRowGap
import app.openbubbles.nativeapp.ui.common.avatarColorFor
import app.openbubbles.nativeapp.ui.common.rememberContactAvatarPath
import app.openbubbles.nativeapp.ui.common.segmentedRowShape
import app.openbubbles.nativeapp.ui.map.GeoPoint
import app.openbubbles.nativeapp.ui.map.MapCamera
import app.openbubbles.nativeapp.ui.map.MapMarker
import app.openbubbles.nativeapp.ui.map.MapTileStore
import app.openbubbles.nativeapp.ui.map.MapViewport
import app.openbubbles.nativeapp.ui.map.OpenMap
import app.openbubbles.nativeapp.ui.map.WebMercator
import app.openbubbles.nativeapp.ui.map.cameraFor
import app.openbubbles.nativeapp.ui.theme.OpenBubblesTheme
import app.openbubbles.nativeapp.ui.tooling.LightDarkPreviews
import kotlinx.coroutines.delay

/**
 * Find My: a live map of this account's devices, followed friends, and beacon
 * items, with a list beside or beneath it.
 *
 * The map is the screen. Every located target is a pin with its reported
 * accuracy drawn to scale and this session's fixes drawn as a track, so "where
 * is it, how sure are we, and which way is it going" is answerable at a glance.
 * The list stays authoritative for anything without a fix and for everything a
 * pin cannot say, and it is the non-gesture route to every target.
 *
 * Tracking is read-only and foreground-only: it repeats the same refresh the
 * button does while the screen is visible, and stops when it is not. Nothing
 * here shares this phone's location, invites anyone, or writes to the account.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FindMyScreen(
    uiState: FindMyUiState,
    onRefresh: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    showBackButton: Boolean = true,
    /** Selects a target (null clears), which opens its card and centres the map. */
    onSelectTarget: (String?) -> Unit = {},
    onSetLiveUpdates: (Boolean) -> Unit = {},
    /** Imagery source; null draws the map without tiles. */
    tiles: MapTileStore? = null,
    imageryEnabled: Boolean = true,
    onSetImageryEnabled: (Boolean) -> Unit = {},
    /** Clock injected so previews and screenshots render fixed freshness text. */
    nowMillis: Long? = null,
    surfaceSwitcher: @Composable (gestureEnabled: Boolean) -> Unit = {},
) {
    val freshnessNowMillis = nowMillis ?: rememberFreshnessClock()
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()
    Scaffold(
        modifier = modifier,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            Column {
                TopAppBar(
                    scrollBehavior = scrollBehavior,
                    title = {
                        Column {
                            Text("Find My", style = MaterialTheme.typography.titleLarge)
                            Text(
                                text = trackingStatus(uiState, freshnessNowMillis),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    },
                    navigationIcon = {
                        if (showBackButton) {
                            IconButton(onClick = onBack) {
                                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                            }
                        }
                    },
                    actions = {
                        IconButton(
                            onClick = { onSetImageryEnabled(!imageryEnabled) },
                        ) {
                            Icon(
                                imageVector = if (imageryEnabled) {
                                    Icons.Filled.Layers
                                } else {
                                    Icons.Filled.LayersClear
                                },
                                contentDescription = if (imageryEnabled) {
                                    "Turn off map imagery"
                                } else {
                                    "Turn on map imagery"
                                },
                            )
                        }
                        IconButton(onClick = { onSetLiveUpdates(!uiState.liveUpdates) }) {
                            Icon(
                                imageVector = if (uiState.liveUpdates) {
                                    Icons.Filled.PauseCircle
                                } else {
                                    Icons.Filled.PlayCircle
                                },
                                contentDescription = if (uiState.liveUpdates) {
                                    "Pause live updates"
                                } else {
                                    "Resume live updates"
                                },
                            )
                        }
                        if (uiState.refreshing) {
                            CircularProgressIndicator(
                                strokeWidth = 2.5.dp,
                                modifier = Modifier.padding(end = 16.dp).size(22.dp),
                            )
                        } else {
                            IconButton(onClick = onRefresh) {
                                Icon(Icons.Filled.Refresh, contentDescription = "Refresh now")
                            }
                        }
                    },
                )
                surfaceSwitcher(true)
            }
        },
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            when {
                uiState.loading -> LoadingState()
                uiState.unavailable -> EmptyState(
                    title = "Not connected",
                    detail = "Sign in with your Apple ID to see devices, friends and items.",
                )
                uiState.isEmpty -> EmptyState(
                    title = "Nothing here yet",
                    detail = "No devices, friends or items are available for this account.",
                )
                else -> FindMyTracker(
                    uiState = uiState,
                    onSelectTarget = onSelectTarget,
                    onRefresh = onRefresh,
                    tiles = tiles.takeIf { imageryEnabled },
                    nowMillis = freshnessNowMillis,
                )
            }
        }
    }
}

@Composable
private fun rememberFreshnessClock(): Long {
    val lifecycleOwner = LocalLifecycleOwner.current
    val current by produceState(System.currentTimeMillis(), lifecycleOwner) {
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (true) {
                value = System.currentTimeMillis()
                delay(30_000L)
            }
        }
    }
    return current
}

/** "Live · 5/7 located · just now" / "Paused · 3/6 located · 4 min ago". */
private fun trackingStatus(uiState: FindMyUiState, nowMillis: Long): String {
    val located = uiState.locatedTargets.size
    val total = uiState.targets.size
    val mode = if (uiState.liveUpdates) "Live" else "Paused"
    val updated = fixFreshness(uiState.lastUpdatedAtMs, nowMillis)
    return listOfNotNull(mode, "$located/$total located", updated).joinToString(" · ")
}

/** Below this width the map and list stack; above it they sit side by side. */
private val SplitWidth = 720.dp

/**
 * Stacked panel heights. A selected target's card is taller than the collapsed
 * list peek, so selecting something never clips its own actions.
 */
private val PanelCollapsedHeight = 196.dp
private val PanelSelectedHeight = 300.dp
private val PanelExpandedHeight = 420.dp

private data class SaveableMapCamera(val camera: MapCamera?)

private val SaveableMapCameraStateSaver = Saver<SaveableMapCamera, List<Double>>(
    save = { state ->
        state.camera?.let { camera ->
            listOf(camera.center.latitude, camera.center.longitude, camera.zoom)
        } ?: emptyList()
    },
    restore = { values ->
        SaveableMapCamera(
            values.takeIf { it.size == 3 }?.let {
                MapCamera(GeoPoint(it[0], it[1]), it[2])
            },
        )
    },
)

@Composable
private fun FindMyTracker(
    uiState: FindMyUiState,
    onSelectTarget: (String?) -> Unit,
    onRefresh: () -> Unit,
    tiles: MapTileStore?,
    nowMillis: Long,
) {
    // The camera is derived, not stored: with no manual camera it follows the
    // selected target's newest fix, or frames everything located. A pan or pinch
    // stores one and hands control to the user until they re-centre, which makes
    // "following" simply mean "no manual camera".
    var manualCameraState by rememberSaveable(stateSaver = SaveableMapCameraStateSaver) {
        mutableStateOf(SaveableMapCamera(null))
    }
    var panelExpanded by rememberSaveable { mutableStateOf(false) }
    val manualCamera = manualCameraState.camera
    val setManualCamera: (MapCamera?) -> Unit = { camera ->
        manualCameraState = SaveableMapCamera(camera)
    }
    val following = manualCamera == null

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val splitPanes = maxWidth >= SplitWidth
        val map: @Composable (Modifier) -> Unit = { mapModifier ->
            FindMyMap(
                uiState = uiState,
                manualCamera = manualCamera,
                onCameraChange = setManualCamera,
                onSelectTarget = { id ->
                    setManualCamera(null)
                    onSelectTarget(id)
                },
                onFitAll = {
                    setManualCamera(null)
                    onSelectTarget(null)
                },
                following = following,
                onFollow = { setManualCamera(null) },
                tiles = tiles,
                nowMillis = nowMillis,
                modifier = mapModifier,
            )
        }
        val panel: @Composable (Modifier) -> Unit = { panelModifier ->
            TargetPanel(
                uiState = uiState,
                nowMillis = nowMillis,
                expanded = splitPanes || panelExpanded,
                onToggleExpanded = { panelExpanded = !panelExpanded },
                showExpandToggle = !splitPanes,
                onSelectTarget = { id ->
                    setManualCamera(null)
                    onSelectTarget(id)
                },
                onRefresh = onRefresh,
                modifier = panelModifier,
            )
        }
        if (splitPanes) {
            Row(modifier = Modifier.fillMaxSize()) {
                panel(Modifier.width(380.dp).fillMaxHeight())
                map(Modifier.weight(1f).fillMaxHeight())
            }
        } else {
            val desiredPanelHeight = when {
                panelExpanded -> PanelExpandedHeight
                uiState.selectedTargetId != null -> PanelSelectedHeight
                else -> PanelCollapsedHeight
            }
            // Compact landscape and freeform windows still reserve meaningful
            // room for the primary map; the panel itself remains scrollable.
            val boundedPanelHeight = minOf(desiredPanelHeight, maxHeight * 0.6f)
            Column(modifier = Modifier.fillMaxSize()) {
                map(Modifier.fillMaxWidth().weight(1f))
                panel(
                    Modifier
                        .fillMaxWidth()
                        .height(boundedPanelHeight),
                )
            }
        }
    }
}

/** Zoom used when the camera moves to one specific target. */
private const val FOCUS_ZOOM = 15.5

@Composable
private fun FindMyMap(
    uiState: FindMyUiState,
    manualCamera: MapCamera?,
    onCameraChange: (MapCamera) -> Unit,
    onSelectTarget: (String?) -> Unit,
    onFitAll: () -> Unit,
    following: Boolean,
    onFollow: () -> Unit,
    tiles: MapTileStore?,
    nowMillis: Long,
    modifier: Modifier = Modifier,
) {
    BoxWithConstraints(modifier = modifier) {
        val density = LocalDensity.current
        val widthPx = with(density) { maxWidth.toPx() }
        val heightPx = with(density) { maxHeight.toPx() }
        val located = uiState.locatedTargets
        val selectedPoint = uiState.selectedTarget?.point
        val activeCamera = when {
            manualCamera != null -> manualCamera
            selectedPoint != null -> MapCamera(
                center = GeoPoint(selectedPoint.latitude, selectedPoint.longitude),
                zoom = FOCUS_ZOOM,
            )
            // Fitting depends on the pixels available, which is why it is decided
            // here rather than in the caller.
            else -> cameraFor(
                points = located.mapNotNull { target ->
                    target.point?.let { GeoPoint(it.latitude, it.longitude) }
                },
                widthPx = widthPx,
                heightPx = heightPx,
                paddingPx = with(density) { 96.dp.toPx() },
            )
        }
        val markers = located.mapNotNull { target ->
            val point = target.point ?: return@mapNotNull null
            MapMarker(
                id = target.id,
                point = GeoPoint(point.latitude, point.longitude),
                accuracyMeters = point.accuracyMeters,
                label = target.name,
                selected = target.id == uiState.selectedTargetId,
                stale = isStaleFix(point, nowMillis),
                trail = uiState.trail(target.id).map { GeoPoint(it.latitude, it.longitude) },
            ) { selected -> TargetGlyph(target = target, selected = selected) }
        }
        OpenMap(
            camera = activeCamera,
            onCameraChange = onCameraChange,
            markers = markers,
            onMarkerClick = { id ->
                onSelectTarget(id)
                onFollow()
            },
            onMapClick = { onSelectTarget(null) },
            tiles = tiles,
            modifier = Modifier.fillMaxSize(),
        )
        Column(
            modifier = Modifier.align(Alignment.TopCenter).fillMaxWidth().padding(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Box(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.align(Alignment.TopEnd).padding(4.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    FilledTonalButton(onClick = onFitAll, contentPadding = PaddingValues(12.dp)) {
                        Icon(Icons.Filled.CenterFocusStrong, contentDescription = null)
                        Spacer(Modifier.width(6.dp))
                        Text("Fit all")
                    }
                    if (uiState.selectedTargetId != null && !following) {
                        FilledTonalButton(onClick = onFollow, contentPadding = PaddingValues(12.dp)) {
                            Icon(Icons.Filled.MyLocation, contentDescription = null)
                            Spacer(Modifier.width(6.dp))
                            Text("Follow")
                        }
                    }
                }
            }
            if (uiState.refreshErrors.isNotEmpty()) {
                RefreshNotice(errors = uiState.refreshErrors)
            }
        }
    }
}

@Composable
private fun TargetGlyph(target: FmTarget, selected: Boolean) {
    when (target.kind) {
        FmTargetKind.Friend -> ChatAvatar(
            title = target.name,
            avatarColor = avatarColorFor(target.address ?: target.id),
            size = 36.dp,
            avatarPath = rememberContactAvatarPath(target.address),
        )
        FmTargetKind.Item -> if (target.emoji != null) {
            Text(text = target.emoji, style = MaterialTheme.typography.titleMedium)
        } else {
            Icon(
                imageVector = Icons.Filled.LocationOn,
                contentDescription = null,
                modifier = Modifier.size(22.dp),
            )
        }
        FmTargetKind.Device -> Icon(
            imageVector = deviceIcon(target.deviceGlyphKey()),
            contentDescription = null,
            tint = if (selected) {
                MaterialTheme.colorScheme.onPrimary
            } else {
                MaterialTheme.colorScheme.onSurface
            },
            modifier = Modifier.size(22.dp),
        )
    }
}

private fun FmTarget.deviceGlyphKey(): String = deviceClass ?: model ?: name

/** Non-blocking notice: stale data stays on the map, the failure is explained. */
@Composable
private fun RefreshNotice(errors: List<String>, modifier: Modifier = Modifier) {
    Surface(
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.errorContainer,
        modifier = modifier.widthIn(max = 520.dp),
    ) {
        Text(
            text = "Couldn't refresh — showing last known locations\n" + errors.joinToString("\n"),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onErrorContainer,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
        )
    }
}

// --------------------------------------------------------------------- panel

@Composable
private fun TargetPanel(
    uiState: FindMyUiState,
    nowMillis: Long,
    expanded: Boolean,
    onToggleExpanded: () -> Unit,
    showExpandToggle: Boolean,
    onSelectTarget: (String?) -> Unit,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = modifier,
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            if (showExpandToggle) {
                // Tap target, not just a drag handle: expanding the list must be
                // reachable with a keyboard, a switch, and TalkBack.
                Surface(
                    onClick = onToggleExpanded,
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                    modifier = Modifier
                        .fillMaxWidth()
                        .semantics {
                            contentDescription = if (expanded) {
                                "Collapse tracked locations panel"
                            } else {
                                "Expand tracked locations panel"
                            }
                            stateDescription = if (expanded) "Expanded" else "Collapsed"
                        },
                ) {
                    Box(
                        modifier = Modifier.fillMaxWidth().heightIn(min = 28.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Box(
                            modifier = Modifier
                                .padding(vertical = 12.dp)
                                .size(width = 32.dp, height = 4.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.outlineVariant),
                        )
                    }
                }
            }
            val selected = uiState.selectedTarget
            if (selected != null) {
                SelectedTargetCard(
                    target = selected,
                    thisDevice = uiState.targets.firstOrNull { it.thisDevice && it.located },
                    nowMillis = nowMillis,
                    onRefresh = onRefresh,
                    onDismiss = { onSelectTarget(null) },
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                )
                HorizontalDivider()
            }
            if (expanded || selected == null) {
                TargetList(
                    uiState = uiState,
                    nowMillis = nowMillis,
                    onSelectTarget = onSelectTarget,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun SelectedTargetCard(
    target: FmTarget,
    thisDevice: FmTarget?,
    nowMillis: Long,
    onRefresh: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    Surface(
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = modifier.fillMaxWidth().animateContentSize(),
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = target.name,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = targetSummary(target, nowMillis),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Filled.Close, contentDescription = "Close details")
                }
            }
            target.point?.address?.let { address ->
                Text(
                    text = address,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
            // Distance is measured between two fixes Apple already reported, so
            // it needs no location permission and no fix of this phone's own.
            val distance = distanceBetween(thisDevice, target)
            if (distance != null) {
                Text(
                    text = distance,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
            Row(
                modifier = Modifier.padding(top = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                target.point?.let { point ->
                    OutlinedButton(onClick = { openInMaps(context, target.name, point) }) {
                        Text("Open in Maps")
                    }
                }
                OutlinedButton(onClick = onRefresh) { Text("Refresh") }
            }
        }
    }
}

private fun distanceBetween(from: FmTarget?, to: FmTarget): String? {
    if (from == null || from.id == to.id) return null
    val a = from.point ?: return null
    val b = to.point ?: return null
    val meters = WebMercator.distanceMeters(
        GeoPoint(a.latitude, a.longitude),
        GeoPoint(b.latitude, b.longitude),
    )
    return "${distanceLabel(meters)} from ${from.name}"
}

@Composable
private fun TargetList(
    uiState: FindMyUiState,
    nowMillis: Long,
    onSelectTarget: (String?) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier.fillMaxWidth().navigationBarsPadding(),
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(SegmentedRowGap),
    ) {
        FmTargetKind.entries.forEach { kind ->
            val targets = uiState.targets.filter { it.kind == kind }
            item(key = "header-$kind") {
                Text(
                    text = sectionTitle(kind, targets.size),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.fillMaxWidth().padding(start = 8.dp, top = 10.dp, bottom = 2.dp),
                )
            }
            if (targets.isEmpty()) {
                item(key = "empty-$kind") {
                    Text(
                        text = "None",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 8.dp, bottom = 4.dp),
                    )
                }
            } else {
                itemsIndexed(targets, key = { _, target -> target.id }) { index, target ->
                    TargetRow(
                        target = target,
                        nowMillis = nowMillis,
                        selected = target.id == uiState.selectedTargetId,
                        shape = segmentedRowShape(index, targets.size),
                        onClick = { onSelectTarget(target.id) },
                    )
                }
            }
        }
    }
}

private fun sectionTitle(kind: FmTargetKind, count: Int): String {
    val name = when (kind) {
        FmTargetKind.Device -> "Devices"
        FmTargetKind.Friend -> "People"
        FmTargetKind.Item -> "Items"
    }
    return if (count > 0) "$name ($count)".uppercase() else name.uppercase()
}

@Composable
private fun TargetRow(
    target: FmTarget,
    nowMillis: Long,
    selected: Boolean,
    shape: RoundedCornerShape,
    onClick: () -> Unit,
) {
    Surface(
        shape = shape,
        color = if (selected) {
            MaterialTheme.colorScheme.secondaryContainer
        } else {
            MaterialTheme.colorScheme.surfaceContainer
        },
        modifier = Modifier
            .fillMaxWidth()
            .clickable(
                onClick = onClick,
                onClickLabel = if (target.located) "Show on map" else "Show details",
                role = Role.Button,
            ),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(
                        if (target.located) {
                            MaterialTheme.colorScheme.primaryContainer
                        } else {
                            MaterialTheme.colorScheme.surfaceContainerHighest
                        },
                    ),
                contentAlignment = Alignment.Center,
            ) {
                TargetGlyph(target = target, selected = false)
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = target.name,
                    style = MaterialTheme.typography.bodyLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = targetSummary(target, nowMillis),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (target.located) {
                Icon(
                    imageVector = Icons.Filled.LocationOn,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
}

// --------------------------------------------------------------------- states

@Composable
private fun LoadingState() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            LoadingIndicator()
            Spacer(modifier = Modifier.padding(8.dp))
            Text(
                text = "Loading last known locations…",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun EmptyState(title: String, detail: String) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(horizontal = 32.dp),
        ) {
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                modifier = Modifier.size(64.dp),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Filled.LocationOn,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(28.dp),
                    )
                }
            }
            Spacer(modifier = Modifier.padding(8.dp))
            Text(text = title, style = MaterialTheme.typography.titleLargeEmphasized)
            Text(
                text = detail,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
    }
}

// --------------------------------------------------------------------- helpers

/** Maps app via a `geo:` URI; the external handoff every row has always had. */
private fun openInMaps(context: Context, name: String, point: FmPoint) {
    runCatching {
        val label = android.net.Uri.encode(name)
        val uri = ("geo:${point.latitude},${point.longitude}" +
            "?q=${point.latitude},${point.longitude}($label)").toUri()
        context.startActivity(
            Intent(Intent.ACTION_VIEW, uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }
}

private fun deviceIcon(model: String): ImageVector = when {
    model.contains("book", ignoreCase = true) ||
        model.contains("mac", ignoreCase = true) -> Icons.Rounded.Laptop
    model.contains("watch", ignoreCase = true) -> Icons.Rounded.Watch
    model.contains("ipad", ignoreCase = true) ||
        model.contains("tablet", ignoreCase = true) -> Icons.Rounded.Tablet
    else -> Icons.Rounded.Smartphone
}

// --------------------------------------------------------------------- previews

/** Fixed clock so previews and screenshot fixtures never drift. */
private const val PREVIEW_NOW = 1_760_000_000_000L

private fun previewState(
    failRefresh: Boolean = false,
    selectedTargetId: String? = null,
): FindMyUiState {
    val port = FakeFindMyPort(failRefresh)
    val devices = kotlinx.coroutines.runBlocking { port.devices() }
    val friends = kotlinx.coroutines.runBlocking { port.friends() }
    val items = kotlinx.coroutines.runBlocking { port.items() }
    val state = FindMyUiState(
        loading = false,
        devices = devices,
        friends = friends,
        items = items,
        lastUpdatedAtMs = PREVIEW_NOW - 20_000,
        selectedTargetId = selectedTargetId,
    )
    // A short track for the phone, so the map shows what tracking looks like.
    val phone = state.targets.first { it.kind == FmTargetKind.Device && it.located }
    return state.copy(
        trails = mapOf(
            phone.id to listOf(
                FmPoint(37.7712, -122.4260, 40.0, PREVIEW_NOW - 12 * 60_000),
                FmPoint(37.7731, -122.4223, 38.0, PREVIEW_NOW - 8 * 60_000),
                FmPoint(37.7749, -122.4194, 65.0, PREVIEW_NOW - 2 * 60_000),
            ),
        ),
    )
}

@LightDarkPreviews
@Composable
private fun FindMyScreenPreview() {
    OpenBubblesTheme(dynamicColor = false) {
        FindMyScreen(
            uiState = previewState(),
            onRefresh = {},
            onBack = {},
            nowMillis = PREVIEW_NOW,
        )
    }
}

@LightDarkPreviews
@Composable
private fun FindMySelectedPreview() {
    OpenBubblesTheme(dynamicColor = false) {
        FindMyScreen(
            uiState = previewState(selectedTargetId = "device:d1"),
            onRefresh = {},
            onBack = {},
            nowMillis = PREVIEW_NOW,
        )
    }
}

@LightDarkPreviews
@Composable
private fun FindMyOfflinePreview() {
    OpenBubblesTheme(dynamicColor = false) {
        FindMyScreen(
            uiState = previewState(failRefresh = true)
                .copy(refreshErrors = listOf("Devices: offline"), liveUpdates = false),
            onRefresh = {},
            onBack = {},
            nowMillis = PREVIEW_NOW,
        )
    }
}

@LightDarkPreviews
@Composable
private fun FindMyUnavailablePreview() {
    OpenBubblesTheme(dynamicColor = false) {
        FindMyScreen(
            uiState = FindMyUiState(loading = false, unavailable = true),
            onRefresh = {},
            onBack = {},
            nowMillis = PREVIEW_NOW,
        )
    }
}

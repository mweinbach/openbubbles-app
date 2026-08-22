package app.openbubbles.nativeapp.ui.navigation

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitHorizontalTouchSlopOrCancellation
import androidx.compose.foundation.gestures.horizontalDrag
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Password
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import app.openbubbles.nativeapp.ui.theme.defaultEffectsSpec
import app.openbubbles.nativeapp.ui.theme.defaultSpatialSpec
import app.openbubbles.nativeapp.ui.theme.fastSpatialSpec
import kotlin.math.roundToInt
import kotlinx.coroutines.launch

/** Test tag for the switcher strip, used by benchmark and instrumentation runs. */
const val SurfaceSwitcherTag: String = "openbubbles_surface_switcher"

private val SurfaceIcons: Map<TopLevelSurface, ImageVector> = mapOf(
    TopLevelSurface.MESSAGES to Icons.AutoMirrored.Filled.Chat,
    TopLevelSurface.PHOTOS to Icons.Filled.PhotoLibrary,
    TopLevelSurface.PASSWORDS to Icons.Filled.Password,
    TopLevelSurface.FIND_MY to Icons.Filled.LocationOn,
)

/** How far the strip follows the finger before the gesture is resolved. */
private const val DragFollowFraction = 0.25f
private val DragFollowMaxDp = 24.dp
private val SurfaceSwitcherPadding = 4.dp
private val SurfaceSwitcherGap = 2.dp
private val SurfaceSwitcherHeight = 48.dp

internal data class SurfaceSwitcherTrackMetrics(
    val itemWidth: Dp,
    val indicatorOffset: Dp,
    val hasSelection: Boolean,
)

internal fun surfaceSwitcherTrackMetrics(
    trackWidth: Dp,
    itemCount: Int,
    selectedIndex: Int,
): SurfaceSwitcherTrackMetrics {
    require(itemCount > 0) { "The surface switcher must contain at least one destination" }

    val itemWidth = (trackWidth - SurfaceSwitcherGap * (itemCount - 1)) / itemCount
    val safeIndex = selectedIndex.coerceIn(0, itemCount - 1)
    return SurfaceSwitcherTrackMetrics(
        itemWidth = itemWidth,
        indicatorOffset = (itemWidth + SurfaceSwitcherGap) * safeIndex,
        hasSelection = selectedIndex in 0 until itemCount,
    )
}

/**
 * The peer-surface switcher that sits directly under a surface's app bar.
 *
 * Every destination is a labelled, selectable control first: touch, pointer,
 * keyboard, switch access and TalkBack reach all four surfaces without any
 * gesture, and the selected one carries selection semantics rather than colour
 * alone. The horizontal swipe is an accelerator layered on top, scoped to this
 * strip only — see [SurfaceSwipePolicy] for why the edges and ambiguous drags
 * are refused.
 *
 * The strip is not a second navigation container: it drives the existing root
 * back stack, and the chat-list overflow menu plus the Settings rows remain the
 * non-gesture routes they already were.
 */
@Composable
fun TopLevelSurfaceSwitcher(
    current: TopLevelSurface?,
    onSelect: (TopLevelSurface) -> Unit,
    modifier: Modifier = Modifier,
    order: TopLevelSurfaceOrder = TopLevelSurfaceOrder.Default,
    /** False while a modal owns input, which keeps the gesture from firing under it. */
    gestureEnabled: Boolean = true,
) {
    val density = LocalDensity.current
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val thresholdPx = with(density) { SurfaceSwipePolicy.COMMIT_THRESHOLD_DP.dp.toPx() }
    val edgeExclusionPx = with(density) { SurfaceSwipePolicy.EDGE_EXCLUSION_DP.dp.toPx() }
    val followMaxPx = with(density) { DragFollowMaxDp.toPx() }
    val scope = rememberCoroutineScope()
    val settleSpec = defaultSpatialSpec<Float>()
    // Gesture progress is the finger's, not an animation's: the strip follows a
    // damped fraction of the drag while it is live, and a spatial spring only
    // runs once the gesture is released.
    val settle = remember { Animatable(0f) }
    var dragging by remember { mutableStateOf(false) }
    var dragFollow by remember { mutableFloatStateOf(0f) }
    val offsetPx = if (dragging) dragFollow else settle.value

    BoxWithConstraints(modifier = modifier.fillMaxWidth()) {
        // Window-driven, not device-driven: the same strip is a full-width
        // header on a phone and a narrow one inside a two-pane list.
        val labelled = maxWidth >= LabelledStripMinWidth * density.fontScale
        Surface(
            shape = MaterialTheme.shapes.extraLarge,
            color = MaterialTheme.colorScheme.surfaceContainerLow,
            modifier = Modifier.fillMaxWidth(),
        ) {
            BoxWithConstraints(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(SurfaceSwitcherPadding)
                    .offset { IntOffset(offsetPx.roundToInt(), 0) },
            ) {
                val surfaceCount = order.surfaces.size
                val selectedIndex = order.surfaces.indexOf(current)
                val metrics = remember(maxWidth, surfaceCount, selectedIndex) {
                    surfaceSwitcherTrackMetrics(maxWidth, surfaceCount, selectedIndex)
                }
                val indicatorOffset by animateDpAsState(
                    targetValue = metrics.indicatorOffset,
                    animationSpec = defaultSpatialSpec(),
                    label = "surfaceSwitcherIndicatorOffset",
                )
                val indicatorAlpha by animateFloatAsState(
                    targetValue = if (metrics.hasSelection) 1f else 0f,
                    animationSpec = defaultEffectsSpec(),
                    label = "surfaceSwitcherIndicatorAlpha",
                )
                Surface(
                    shape = MaterialTheme.shapes.extraLarge,
                    color = MaterialTheme.colorScheme.secondaryContainer,
                    modifier = Modifier
                        .offset { IntOffset(indicatorOffset.roundToPx(), 0) }
                        .width(metrics.itemWidth)
                        .height(SurfaceSwitcherHeight)
                        .graphicsLayer { alpha = indicatorAlpha },
                ) {}

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .selectableGroup()
                        .testTag(SurfaceSwitcherTag)
                        .pointerInput(gestureEnabled, order, current, rtl, thresholdPx) {
                            if (!gestureEnabled) return@pointerInput
                            awaitEachGesture {
                                val down = awaitFirstDown(requireUnconsumed = false)
                                var dragX = 0f
                                var dragY = 0f
                                // Horizontal slop first: a vertical drag stays
                                // with the scrolling content underneath.
                                val slopChange = awaitHorizontalTouchSlopOrCancellation(down.id) { change, _ ->
                                    dragX = change.position.x - down.position.x
                                    dragY = change.position.y - down.position.y
                                    change.consume()
                                } ?: return@awaitEachGesture
                                dragging = true
                                var released = 0f
                                var step: SurfaceStep? = null
                                try {
                                    val completed = horizontalDrag(slopChange.id) { change ->
                                        val moved = change.positionChange()
                                        dragX += moved.x
                                        dragY += moved.y
                                        dragFollow = (dragX * DragFollowFraction)
                                            .coerceIn(-followMaxPx, followMaxPx)
                                        change.consume()
                                    }
                                    step = if (completed) SurfaceSwipePolicy.resolve(
                                        dragX = dragX,
                                        dragY = dragY,
                                        startX = down.position.x,
                                        widthPx = size.width.toFloat(),
                                        thresholdPx = thresholdPx,
                                        edgeExclusionPx = edgeExclusionPx,
                                        enabled = true,
                                        rtl = rtl,
                                    ) else null
                                    released = dragFollow
                                } finally {
                                    dragging = false
                                    dragFollow = 0f
                                    scope.launch {
                                        settle.snapTo(released)
                                        settle.animateTo(0f, settleSpec)
                                    }
                                }
                                val target = step?.let { order.step(current, it) }
                                if (target != null) onSelect(target)
                            }
                        },
                    horizontalArrangement = Arrangement.spacedBy(SurfaceSwitcherGap),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    order.surfaces.forEach { surface ->
                        SurfaceSwitcherItem(
                            surface = surface,
                            selected = surface == current,
                            showLabel = labelled,
                            onClick = { onSelect(surface) },
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }
        }
    }
}

/**
 * Width the four labels need before the strip switches to icons alone. Below
 * this the strip is inside a narrow list pane, a small window, or a large-text
 * setting where labels would ellipsize into noise; the icon then carries the
 * destination name as its content description.
 */
private val LabelledStripMinWidth = 360.dp

@Composable
private fun SurfaceSwitcherItem(
    surface: TopLevelSurface,
    selected: Boolean,
    showLabel: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val contentColor by animateColorAsState(
        targetValue = if (selected) {
            MaterialTheme.colorScheme.onSecondaryContainer
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant
        },
        animationSpec = defaultEffectsSpec(),
        label = "surfaceSwitcherContent",
    )
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val pressedScale by animateFloatAsState(
        targetValue = if (pressed) 0.96f else 1f,
        animationSpec = fastSpatialSpec(),
        label = "surfaceSwitcherPressedScale",
    )
    Surface(
        selected = selected,
        onClick = onClick,
        shape = if (selected) MaterialTheme.shapes.extraLarge else MaterialTheme.shapes.medium,
        color = Color.Transparent,
        contentColor = contentColor,
        interactionSource = interactionSource,
        modifier = modifier
            .heightIn(min = SurfaceSwitcherHeight)
            .graphicsLayer {
                scaleX = pressedScale
                scaleY = pressedScale
            },
    ) {
        Row(
            modifier = Modifier
                .widthIn(min = 48.dp)
                .padding(horizontal = 10.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Label or icon, never both: four icon-plus-label pairs do not fit a
            // phone header without truncating the names they exist to show.
            if (showLabel) {
                Text(
                    text = surface.label,
                    style = if (selected) {
                        MaterialTheme.typography.labelLargeEmphasized
                    } else {
                        MaterialTheme.typography.labelLarge
                    },
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            } else {
                SurfaceIcons[surface]?.let { icon ->
                    Icon(
                        imageVector = icon,
                        contentDescription = surface.label,
                        modifier = Modifier.size(20.dp),
                    )
                }
            }
        }
    }
}

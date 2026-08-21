package app.openbubbles.nativeapp.ui.navigation

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitHorizontalTouchSlopOrCancellation
import androidx.compose.foundation.gestures.horizontalDrag
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import app.openbubbles.nativeapp.ui.theme.defaultEffectsSpec
import app.openbubbles.nativeapp.ui.theme.defaultSpatialSpec
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
    // Window-driven, not device-driven: the same strip is a full-width header on
    // a phone and a narrow one inside a two-pane list. The requirement scales
    // with the user's font size so large text drops to icons instead of
    // truncating every label.
    val labelled = maxWidth >= LabelledStripMinWidth * density.fontScale
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .offset { IntOffset(offsetPx.roundToInt(), 0) }
            .selectableGroup()
            .testTag(SurfaceSwitcherTag)
            .pointerInput(gestureEnabled, order, current, rtl, thresholdPx) {
                if (!gestureEnabled) return@pointerInput
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    var dragX = 0f
                    var dragY = 0f
                    // Horizontal slop first: a vertical drag that starts on the
                    // strip stays with the scrolling content underneath.
                    val slopChange = awaitHorizontalTouchSlopOrCancellation(down.id) { change, over ->
                        dragX += over
                        change.consume()
                    } ?: return@awaitEachGesture
                    dragging = true
                    horizontalDrag(slopChange.id) { change ->
                        val moved = change.positionChange()
                        dragX += moved.x
                        dragY += moved.y
                        dragFollow = (dragX * DragFollowFraction)
                            .coerceIn(-followMaxPx, followMaxPx)
                        change.consume()
                    }
                    val step = SurfaceSwipePolicy.resolve(
                        dragX = dragX,
                        dragY = dragY,
                        startX = down.position.x,
                        widthPx = size.width.toFloat(),
                        thresholdPx = thresholdPx,
                        edgeExclusionPx = edgeExclusionPx,
                        enabled = true,
                        rtl = rtl,
                    )
                    val released = dragFollow
                    dragging = false
                    dragFollow = 0f
                    scope.launch {
                        settle.snapTo(released)
                        settle.animateTo(0f, settleSpec)
                    }
                    // Resolved once, on release: a drag that wandered back under
                    // the threshold, or crossed it diagonally, changes nothing,
                    // and a committed one navigates exactly once.
                    val target = step?.let { order.step(current, it) }
                    if (target != null) onSelect(target)
                }
            },
        horizontalArrangement = Arrangement.spacedBy(4.dp),
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
    // Selection is the state signal: the selected peer takes the tonal
    // secondaryContainer and the pill shape the app already uses for a selected
    // list row, so colour is never the only cue.
    val container by animateColorAsState(
        targetValue = if (selected) {
            MaterialTheme.colorScheme.secondaryContainer
        } else {
            MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0f)
        },
        animationSpec = defaultEffectsSpec(),
        label = "surfaceSwitcherContainer",
    )
    Surface(
        selected = selected,
        onClick = onClick,
        shape = if (selected) MaterialTheme.shapes.extraLarge else MaterialTheme.shapes.medium,
        color = container,
        contentColor = if (selected) {
            MaterialTheme.colorScheme.onSecondaryContainer
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant
        },
        modifier = modifier.heightIn(min = 48.dp),
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
                    style = MaterialTheme.typography.labelLarge,
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

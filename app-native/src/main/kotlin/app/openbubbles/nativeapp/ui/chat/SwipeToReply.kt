package app.openbubbles.nativeapp.ui.chat

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Reply
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import app.openbubbles.nativeapp.data.MessageItem
import app.openbubbles.nativeapp.data.MessageStatus
import app.openbubbles.nativeapp.ui.theme.defaultSpatialSpec
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlinx.coroutines.launch

internal const val ReplySwipeThresholdDp = 48f
internal const val ReplySwipeMaxDp = 72f

internal fun canSwipeReply(message: MessageItem): Boolean =
    !message.isGroupEvent && !message.unsent && message.status != MessageStatus.SENDING

/** Horizontal reply swipe: content moves toward the start edge only. */
internal fun replySwipeOffset(
    currentPx: Float,
    deltaPx: Float,
    maxPx: Float,
    startIsLeft: Boolean,
): Float {
    if (maxPx <= 0f) return 0f
    val min = if (startIsLeft) -maxPx else 0f
    val max = if (startIsLeft) 0f else maxPx
    val resistance = if (abs(currentPx) >= maxPx * 0.7f) 0.35f else 1f
    return (currentPx + deltaPx * resistance).coerceIn(min, max)
}

internal fun replySwipeArmed(offsetPx: Float, thresholdPx: Float): Boolean =
    abs(offsetPx) >= thresholdPx

internal fun replySwipeProgress(offsetPx: Float, thresholdPx: Float): Float =
    if (thresholdPx <= 0f) 0f else (abs(offsetPx) / thresholdPx).coerceIn(0f, 1f)

/**
 * iMessage-style slide-to-reply: drag the bubble toward the start edge,
 * reveal a reply icon, and commit [onReply] if the threshold is crossed.
 * The finger drives the offset; a spatial spring only runs on release.
 */
@Composable
internal fun SwipeToReplyBox(
    enabled: Boolean,
    onReply: () -> Unit,
    modifier: Modifier = Modifier,
    contentAlignment: Alignment = Alignment.CenterStart,
    content: @Composable BoxScope.() -> Unit,
) {
    val scope = rememberCoroutineScope()
    val settleSpec = defaultSpatialSpec<Float>()
    val density = LocalDensity.current
    val startIsLeft = LocalLayoutDirection.current == LayoutDirection.Ltr
    val haptics = LocalHapticFeedback.current
    val maxPx = with(density) { ReplySwipeMaxDp.dp.toPx() }
    val thresholdPx = with(density) { ReplySwipeThresholdDp.dp.toPx() }
    val settle = remember { Animatable(0f) }
    var dragging by remember { mutableStateOf(false) }
    var dragOffset by remember { mutableFloatStateOf(0f) }
    var armed by remember { mutableStateOf(false) }
    val displayedOffset = if (dragging) dragOffset else settle.value
    val progress = replySwipeProgress(displayedOffset, thresholdPx)
    val iconAlignment = if (startIsLeft) Alignment.CenterEnd else Alignment.CenterStart

    Box(modifier) {
        if (enabled) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.Reply,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .align(iconAlignment)
                    .padding(horizontal = 16.dp)
                    .size(24.dp)
                    .graphicsLayer {
                        alpha = progress
                        val scale = 0.7f + 0.3f * progress
                        scaleX = scale
                        scaleY = scale
                    },
            )
        }
        Box(
            modifier = Modifier
                .align(contentAlignment)
                .offset { IntOffset(displayedOffset.roundToInt(), 0) }
                .then(
                    if (enabled) {
                        Modifier.draggable(
                            orientation = Orientation.Horizontal,
                            state = rememberDraggableState { delta ->
                                val next = replySwipeOffset(
                                    currentPx = dragOffset,
                                    deltaPx = delta,
                                    maxPx = maxPx,
                                    startIsLeft = startIsLeft,
                                )
                                val nowArmed = replySwipeArmed(next, thresholdPx)
                                if (nowArmed != armed) {
                                    armed = nowArmed
                                    haptics.performHapticFeedback(
                                        HapticFeedbackType.GestureThresholdActivate,
                                    )
                                }
                                dragOffset = next
                            },
                            onDragStarted = {
                                scope.launch { settle.stop() }
                                dragOffset = settle.value
                                dragging = true
                            },
                            onDragStopped = {
                                val shouldReply = replySwipeArmed(dragOffset, thresholdPx)
                                if (shouldReply) onReply()
                                armed = false
                                val released = dragOffset
                                scope.launch {
                                    settle.snapTo(released)
                                    dragging = false
                                    settle.animateTo(0f, settleSpec)
                                    dragOffset = 0f
                                }
                            },
                        )
                    } else {
                        Modifier
                    },
                ),
            content = content,
        )
    }
}

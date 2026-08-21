package app.openbubbles.nativeapp.ui.chat

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import app.openbubbles.nativeapp.ui.theme.OpenBubblesTheme
import app.openbubbles.nativeapp.ui.theme.defaultEffectsSpec
import app.openbubbles.nativeapp.ui.theme.defaultSpatialSpec
import app.openbubbles.nativeapp.ui.theme.fastEffectsSpec
import app.openbubbles.nativeapp.ui.theme.fastSpatialSpec
import app.openbubbles.nativeapp.ui.tooling.LightDarkPreviews

/**
 * Bottom-anchored "New messages" jump control: a compact tonal pill that
 * appears while the reader is away from the newest message and taps down to it.
 *
 * It is a viewport affordance, not a second unread badge — it never touches
 * read receipts, notifications, or the persisted chat unread flag. Callers lay
 * it out above the measured composer (Scaffold content padding already accounts
 * for the input bar's IME and navigation-bar insets) rather than at a fixed
 * screen coordinate.
 *
 * Motion follows the app policy: spatial specs move the pill, effects specs
 * fade it, and both collapse to `snap()` when the user removed animations, so
 * the control stays discoverable without decorative movement.
 */
@Composable
fun NewMessagesJumpPill(
    visible: Boolean,
    count: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    thread: Boolean = false,
) {
    var announcedCount by remember { mutableIntStateOf(count.coerceAtLeast(0)) }
    LaunchedEffect(visible, count) {
        announcedCount = retainedPillAnnouncementCount(announcedCount, visible, count)
    }
    // AnimatedVisibility retains content during exit. Keep the last visible
    // count so TalkBack does not announce a new zero-count label while fading.
    val label = jumpPillLabel(
        displayedPillAnnouncementCount(announcedCount, visible, count),
        if (thread) JumpPillScope.Thread else JumpPillScope.Conversation,
    )
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(defaultEffectsSpec()) +
            slideInVertically(defaultSpatialSpec()) { height -> height },
        exit = fadeOut(fastEffectsSpec()) +
            slideOutVertically(fastSpatialSpec()) { height -> height },
        modifier = modifier,
    ) {
        Surface(
            onClick = onClick,
            shape = CircleShape,
            color = MaterialTheme.colorScheme.secondaryContainer,
            contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
            tonalElevation = 3.dp,
            shadowElevation = 3.dp,
            // Compact pill, but never below the minimum activation target.
            modifier = Modifier
                .defaultMinSize(minHeight = 48.dp)
                .semantics {
                    // Announce arrivals without requesting or moving focus.
                    liveRegion = LiveRegionMode.Polite
                },
        ) {
            Box(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                contentAlignment = Alignment.Center,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Icon(
                        imageVector = Icons.Filled.ExpandMore,
                        contentDescription = null,
                        modifier = Modifier.defaultMinSize(minWidth = 20.dp, minHeight = 20.dp),
                    )
                    Text(text = label, style = MaterialTheme.typography.labelLarge)
                }
            }
        }
    }
}

internal fun retainedPillAnnouncementCount(previous: Int, visible: Boolean, count: Int): Int =
    if (visible && count > 0) count else previous

/** Current positive count on entry; retained count only while exit content remains composed. */
internal fun displayedPillAnnouncementCount(previous: Int, visible: Boolean, count: Int): Int =
    if (visible && count > 0) count else previous

@LightDarkPreviews
@Composable
private fun NewMessagesJumpPillPreview() {
    OpenBubblesTheme {
        Box(Modifier.padding(24.dp)) {
            NewMessagesJumpPill(visible = true, count = 3, onClick = {})
        }
    }
}

@Preview(name = "new-replies-pill")
@Composable
private fun NewRepliesJumpPillPreview() {
    OpenBubblesTheme {
        Box(Modifier.padding(24.dp)) {
            NewMessagesJumpPill(visible = true, count = 1, onClick = {}, thread = true)
        }
    }
}

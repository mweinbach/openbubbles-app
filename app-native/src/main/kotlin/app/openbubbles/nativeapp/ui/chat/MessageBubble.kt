package app.openbubbles.nativeapp.ui.chat

import android.content.res.Configuration
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Done
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.openbubbles.nativeapp.data.MessageItem
import app.openbubbles.nativeapp.data.MessageStatus
import app.openbubbles.nativeapp.ui.theme.OpenBubblesTheme

private val BubbleMaxWidth = 300.dp

private fun bubbleShape(isFromMe: Boolean): RoundedCornerShape = if (isFromMe) {
    // Slightly tighter corner toward the tail (bottom end).
    RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp, bottomEnd = 16.dp, bottomStart = 20.dp)
} else {
    RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp, bottomEnd = 20.dp, bottomStart = 16.dp)
}

/**
 * A single conversation row: message bubble (mine right / theirs left),
 * centered caption for group events, reaction chip overlapping the corner,
 * and an optional delivery-status row under my latest outgoing message.
 */
@Composable
fun MessageBubble(
    message: MessageItem,
    showStatus: Boolean,
    modifier: Modifier = Modifier,
) {
    if (message.isGroupEvent) {
        GroupEventRow(text = message.text, modifier = modifier)
        return
    }
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 3.dp),
    ) {
        Column(
            horizontalAlignment = if (message.isFromMe) Alignment.End else Alignment.Start,
            modifier = Modifier
                .widthIn(max = BubbleMaxWidth)
                .align(if (message.isFromMe) Alignment.CenterEnd else Alignment.CenterStart),
        ) {
            Box {
                Surface(
                    shape = bubbleShape(message.isFromMe),
                    color = if (message.isFromMe) {
                        MaterialTheme.colorScheme.primaryContainer
                    } else {
                        MaterialTheme.colorScheme.surfaceVariant
                    },
                ) {
                    Text(
                        text = message.text,
                        style = MaterialTheme.typography.bodyLarge,
                        color = if (message.isFromMe) {
                            MaterialTheme.colorScheme.onPrimaryContainer
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                    )
                }
                message.reactionEmoji?.let { emoji ->
                    ReactionChip(
                        emoji = emoji,
                        isFromMe = message.isFromMe,
                        modifier = Modifier.align(
                            if (message.isFromMe) Alignment.BottomEnd else Alignment.BottomStart,
                        ),
                    )
                }
            }
            if (showStatus && message.isFromMe) {
                MessageStatusRow(status = message.status)
            }
        }
    }
}

@Composable
private fun ReactionChip(emoji: String, isFromMe: Boolean, modifier: Modifier = Modifier) {
    Surface(
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = modifier.offset(
            x = if (isFromMe) 7.dp else (-7).dp,
            y = 7.dp,
        ),
    ) {
        Text(
            text = emoji,
            fontSize = 13.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(4.dp),
        )
    }
}

/** Delivery/read indicator shown under my most recent outgoing message. */
@Composable
fun MessageStatusRow(status: MessageStatus, modifier: Modifier = Modifier) {
    val (icon, tint, label) = when (status) {
        MessageStatus.SENDING -> Triple(Icons.Filled.Schedule, MaterialTheme.colorScheme.onSurfaceVariant, "Sending…")
        MessageStatus.SENT -> Triple(Icons.Filled.Done, MaterialTheme.colorScheme.onSurfaceVariant, "Sent")
        MessageStatus.DELIVERED -> Triple(Icons.Filled.DoneAll, MaterialTheme.colorScheme.onSurfaceVariant, "Delivered")
        MessageStatus.READ -> Triple(Icons.Filled.DoneAll, MaterialTheme.colorScheme.primary, "Read")
        MessageStatus.FAILED -> Triple(Icons.Filled.Warning, MaterialTheme.colorScheme.error, "Send failed")
    }
    Row(
        modifier = modifier.padding(top = 2.dp, start = 4.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Icon(imageVector = icon, contentDescription = label, tint = tint, modifier = Modifier.size(15.dp))
        Text(text = label, style = MaterialTheme.typography.labelSmall, color = tint)
    }
}

/** Centered small caption for group events ("X added Y"). */
@Composable
fun GroupEventRow(text: String, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 48.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

/** Day divider shown whenever the conversation crosses a calendar day. */
@Composable
fun DaySeparatorRow(label: String, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHighest,
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
            )
        }
    }
}

// --------------------------------------------------------------------- previews

private fun previewMessage(
    text: String,
    isFromMe: Boolean,
    status: MessageStatus = MessageStatus.READ,
    reaction: String? = null,
) = MessageItem(
    id = 1L,
    text = text,
    isFromMe = isFromMe,
    date = System.currentTimeMillis(),
    status = status,
    isGroupEvent = false,
    reactionEmoji = reaction,
)

@Preview(showBackground = true)
@Preview(showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun MessageBubblePreview() {
    OpenBubblesTheme {
        Column {
            MessageBubble(
                message = previewMessage("hey! still on for the hike saturday?", isFromMe = false, reaction = "❤️"),
                showStatus = false,
            )
            MessageBubble(
                message = previewMessage("yes! 8am trailhead, i'll drive", isFromMe = true, status = MessageStatus.READ),
                showStatus = true,
            )
            GroupEventRow("Mom added Dad")
            DaySeparatorRow("Today")
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun MessageStatusRowPreview() {
    OpenBubblesTheme {
        Column {
            MessageStatusRow(status = MessageStatus.SENDING)
            MessageStatusRow(status = MessageStatus.SENT)
            MessageStatusRow(status = MessageStatus.DELIVERED)
            MessageStatusRow(status = MessageStatus.READ)
            MessageStatusRow(status = MessageStatus.FAILED)
        }
    }
}

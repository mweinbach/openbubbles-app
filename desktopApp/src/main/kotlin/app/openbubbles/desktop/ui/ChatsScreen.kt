package app.openbubbles.desktop.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Badge
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.openbubbles.core.model.ChatListItem
import app.openbubbles.desktop.DesktopGraph
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.math.abs

/**
 * Reactive chat list bound straight to :core's [ChatRepo.observeChats]
 * (core DTOs, no UI-contract layer): title / snippet / date / unread /
 * pinned, plus a search field and a connection-status strip.
 */
@Composable
fun ChatsScreen(
    chats: Flow<List<ChatListItem>>,
    connected: Boolean,
    onOpenChat: (Long) -> Unit,
    onReconnect: () -> Unit,
) {
    val list by chats.collectAsState(initial = emptyList())
    var query by remember { mutableStateOf("") }
    val filtered = remember(list, query) {
        if (query.isBlank()) list
        else list.filter {
            it.title.contains(query, ignoreCase = true) ||
                it.snippet?.contains(query, ignoreCase = true) == true
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        // Search field
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            singleLine = true,
            placeholder = { Text("Search") },
            leadingIcon = { Icon(Icons.Filled.Search, contentDescription = "Search") },
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp, 16.dp, 16.dp, 4.dp),
        )

        if (!connected) {
            ConnectionStrip(onReconnect)
        }

        when {
            filtered.isEmpty() && list.isEmpty() -> EmptyChats()
            filtered.isEmpty() -> Box(Modifier.fillMaxSize()) {
                Text(
                    "No chats match \"$query\"",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.align(Alignment.Center),
                )
            }
            else -> LazyColumn(modifier = Modifier.fillMaxSize()) {
                items(filtered, key = { it.id }) { item ->
                    ChatRow(item = item, onClick = { onOpenChat(item.id) })
                }
            }
        }
    }
}

@Composable
private fun ConnectionStrip(onSignIn: () -> Unit) {
    Surface(
        tonalElevation = 2.dp,
        color = MaterialTheme.colorScheme.secondaryContainer,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        shape = RoundedCornerShape(8.dp),
    ) {
        Row(
            modifier = Modifier
                .clickable(onClick = onSignIn)
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CircularProgressIndicator(
                modifier = Modifier.size(14.dp),
                strokeWidth = 2.dp,
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = "Connecting to Apple push…",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
            )
        }
    }
}

@Composable
private fun EmptyChats() {
    Box(Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier.align(Alignment.Center),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                "No chats yet",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                "New conversations appear here as messages arrive.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun ChatRow(item: ChatListItem, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Avatar(item.title, avatarColorFor(item.guid))
        Spacer(Modifier.width(12.dp))
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = item.title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = if (item.hasUnread) FontWeight.SemiBold else FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                if (item.pinned) {
                    Spacer(Modifier.width(4.dp))
                    Icon(
                        Icons.Filled.PushPin,
                        contentDescription = "Pinned",
                        modifier = Modifier.size(13.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Spacer(Modifier.weight(1f))
                Text(
                    text = listDateLabel(item.date),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = item.snippet ?: "",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                if (item.unreadCount > 0) {
                    Spacer(Modifier.width(8.dp))
                    Badge { Text(item.unreadCount.toString()) }
                }
            }
        }
    }
}

/** Deterministic pastel-ish avatar color from the chat guid. */
internal fun avatarColorFor(seed: String): Color {
    val palette = listOf(
        0xFF7C4FDF, 0xFF4C8BF5, 0xFF00897B, 0xFFD81B60, 0xFFF4511E,
        0xFF6D4C41, 0xFF3949AB, 0xFF43A047, 0xFF8D6E63, 0xFFC0CA33,
    )
    return Color(palette[abs(seed.hashCode()) % palette.size])
}

/** Circular initial avatar (contacts sync is out of scope for the desktop MVP). */
@Composable
internal fun Avatar(title: String, color: Color, size: Int = 44) {
    Box(
        modifier = Modifier
            .size(size.dp)
            .background(color, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = title.trim().take(1).uppercase().ifBlank { "?" },
            color = Color.White,
            fontSize = (size * 0.42f).sp,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

private val TIME_FORMAT = DateTimeFormatter.ofPattern("h:mm a")
private val DAY_FORMAT = DateTimeFormatter.ofPattern("M/d/yy")

/** iMessage-style date label for the chat list. */
internal fun listDateLabel(date: java.util.Date?): String {
    if (date == null) return ""
    val local = date.toInstant().atZone(ZoneId.systemDefault())
    val today = LocalDate.now()
    return when {
        local.toLocalDate().isEqual(today) -> local.format(TIME_FORMAT)
        local.toLocalDate().year == today.year ->
            local.format(DateTimeFormatter.ofPattern("EEE"))
        else -> local.format(DAY_FORMAT)
    }
}

/** Time label for transcript bubbles. */
internal fun bubbleTimeLabel(date: java.util.Date?): String =
    date?.toInstant()?.atZone(ZoneId.systemDefault())?.format(TIME_FORMAT) ?: ""

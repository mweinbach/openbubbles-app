package app.openbubbles.nativeapp.ui.chat

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.derivedStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import app.openbubbles.nativeapp.data.ChatListItem
import app.openbubbles.nativeapp.data.MessageItem
import app.openbubbles.nativeapp.data.MessageStatus
import app.openbubbles.nativeapp.ui.common.ChatAvatar
import app.openbubbles.nativeapp.ui.common.formatConversationDay
import app.openbubbles.nativeapp.ui.common.localDay
import java.time.ZoneId
import kotlinx.coroutines.launch

/** List model for the conversation LazyColumn. */
sealed interface ConversationEntry {
    val key: String

    data class DaySeparator(val epochMillis: Long) : ConversationEntry {
        override val key: String = "day-$epochMillis"
    }

    data class Message(val message: MessageItem, val showStatus: Boolean) : ConversationEntry {
        override val key: String = "message-${message.id}"
    }
}

/**
 * Builds newest-first entries (the reversed list renders index 0 at the
 * bottom) with day separators between calendar days and the status row on my
 * newest outgoing message (or any failed one).
 */
fun buildConversationEntries(
    messages: List<MessageItem>,
    zone: ZoneId = ZoneId.systemDefault(),
): List<ConversationEntry> {
    val lastFromMeId = messages.lastOrNull { it.isFromMe && !it.isGroupEvent }?.id
    val entries = mutableListOf<ConversationEntry>()
    var lastDay = localDay(Long.MIN_VALUE, zone)
    for (message in messages.asReversed()) {
        val day = localDay(message.date, zone)
        if (day != lastDay) {
            entries += ConversationEntry.DaySeparator(message.date)
            lastDay = day
        }
        val showStatus = message.id == lastFromMeId || message.status == MessageStatus.FAILED
        entries += ConversationEntry.Message(message, showStatus)
    }
    return entries
}

/**
 * Conversation view: reversed LazyColumn (newest at the bottom, stays pinned
 * while sending), day separators, bubbles with reactions and delivery status,
 * older-history paging when scrolled to the top, and an IME-aware input bar.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    uiState: ChatUiState,
    onInputChange: (String) -> Unit,
    onSend: () -> Unit,
    onLoadOlder: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val entries = remember(uiState.messages) { buildConversationEntries(uiState.messages) }

    // Reverse layout: the visual top of the list is the highest index.
    val nearTop by remember(entries.size) {
        derivedStateOf {
            val lastVisibleIndex = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            entries.size > 12 && lastVisibleIndex >= entries.size - 5
        }
    }
    LaunchedEffect(nearTop) {
        if (nearTop) onLoadOlder()
    }

    Scaffold(
        modifier = modifier,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            TopAppBar(
                title = { ChatHeader(chat = uiState.chat) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
        bottomBar = {
            MessageInputBar(
                value = uiState.input,
                onValueChange = onInputChange,
                onSend = {
                    onSend()
                    scope.launch { listState.animateScrollToItem(0) }
                },
            )
        },
    ) { padding ->
        Box(modifier = Modifier.padding(padding)) {
            when {
                uiState.initialLoading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
                uiState.messages.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        text = "No messages yet — say hi!",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                else -> LazyColumn(
                    state = listState,
                    reverseLayout = true,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(vertical = 8.dp),
                ) {
                    items(entries, key = { it.key }) { entry ->
                        when (entry) {
                            is ConversationEntry.Message ->
                                MessageBubble(message = entry.message, showStatus = entry.showStatus)
                            is ConversationEntry.DaySeparator ->
                                DaySeparatorRow(label = formatConversationDay(entry.epochMillis))
                        }
                    }
                    if (uiState.loadingOlder) {
                        item(key = "loading-older") {
                            Box(
                                modifier = Modifier.fillMaxWidth().padding(12.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                CircularProgressIndicator(
                                    modifier = Modifier.padding(8.dp).size(22.dp),
                                    strokeWidth = 2.dp,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ChatHeader(chat: ChatListItem?) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        if (chat != null) {
            ChatAvatar(title = chat.title, avatarColor = chat.avatarColor, size = 34.dp)
            Text(
                text = chat.title,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        } else {
            Text(
                text = "Conversation",
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun MessageInputBar(
    value: String,
    onValueChange: (String) -> Unit,
    onSend: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .imePadding()
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.Bottom,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        TextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier.weight(1f),
            placeholder = { Text("Message") },
            maxLines = 5,
            shape = RoundedCornerShape(24.dp),
            colors = TextFieldDefaults.colors(
                focusedIndicatorColor = Color.Transparent,
                unfocusedIndicatorColor = Color.Transparent,
            ),
            keyboardOptions = KeyboardOptions(
                imeAction = ImeAction.Send,
                capitalization = KeyboardCapitalization.Sentences,
            ),
            keyboardActions = KeyboardActions(onSend = { onSend() }),
        )
        FilledIconButton(
            onClick = onSend,
            enabled = value.isNotBlank(),
            shape = CircleShape,
        ) {
            Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Send")
        }
    }
}

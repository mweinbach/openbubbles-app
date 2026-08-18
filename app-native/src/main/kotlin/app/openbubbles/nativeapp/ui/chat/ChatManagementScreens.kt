package app.openbubbles.nativeapp.ui.chat

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.BookmarkRemove
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.openbubbles.nativeapp.data.ChatListItem
import app.openbubbles.nativeapp.data.MessageItem
import java.text.DateFormat
import java.util.Date

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BookmarkedMessagesScreen(
    messages: List<MessageItem>,
    onBack: () -> Unit,
    onOpenChat: () -> Unit,
    onUnbookmark: (MessageItem) -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text("Bookmarks") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { padding ->
        if (messages.isEmpty()) {
            EmptyManagementState(
                title = "No bookmarked messages",
                supporting = "Bookmark a message from its action menu to keep it here.",
                modifier = Modifier.padding(padding),
            )
        } else {
            LazyColumn(modifier = Modifier.fillMaxSize().padding(padding)) {
                items(messages, key = { it.id }) { message ->
                    ListItem(
                        headlineContent = {
                            Text(
                                message.text.ifBlank { "Attachment" },
                                maxLines = 3,
                                overflow = TextOverflow.Ellipsis,
                            )
                        },
                        supportingContent = { Text(formatManagementDate(message.date)) },
                        trailingContent = {
                            IconButton(onClick = { onUnbookmark(message) }) {
                                Icon(Icons.Filled.BookmarkRemove, contentDescription = "Remove bookmark")
                            }
                        },
                        modifier = Modifier.clickable(onClick = onOpenChat),
                    )
                    HorizontalDivider()
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecentlyDeletedScreen(
    chats: List<ChatListItem>,
    messages: List<MessageItem>,
    onBack: () -> Unit,
    onRestoreChat: (ChatListItem) -> Unit,
    onDeleteChat: (ChatListItem) -> Unit,
    onRestoreMessage: (MessageItem) -> Unit,
    onDeleteMessage: (MessageItem) -> Unit,
    modifier: Modifier = Modifier,
) {
    var pendingDeleteChat by remember { mutableStateOf<ChatListItem?>(null) }
    var pendingDeleteMessage by remember { mutableStateOf<MessageItem?>(null) }
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text("Recently Deleted") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { padding ->
        if (chats.isEmpty() && messages.isEmpty()) {
            EmptyManagementState(
                title = "Nothing recently deleted",
                supporting = "Deleted conversations and messages you can recover appear here.",
                modifier = Modifier.padding(padding),
            )
        } else {
            LazyColumn(modifier = Modifier.fillMaxSize().padding(padding)) {
                if (chats.isNotEmpty()) {
                    item { ManagementSectionLabel("Conversations") }
                    items(chats, key = { "chat-${it.id}" }) { chat ->
                        DeletedRow(
                            title = chat.title,
                            supporting = chat.dateDeleted?.let(::formatManagementDate) ?: "Deleted conversation",
                            onRestore = { onRestoreChat(chat) },
                            onDelete = { pendingDeleteChat = chat },
                        )
                    }
                }
                if (messages.isNotEmpty()) {
                    item { ManagementSectionLabel("Messages") }
                    items(messages, key = { "message-${it.id}" }) { message ->
                        DeletedRow(
                            title = message.text.ifBlank { "Attachment" },
                            supporting = message.dateDeleted?.let(::formatManagementDate) ?: "Deleted message",
                            onRestore = { onRestoreMessage(message) },
                            onDelete = { pendingDeleteMessage = message },
                        )
                    }
                }
            }
        }
    }

    pendingDeleteChat?.let { chat ->
        PermanentDeleteDialog(
            subject = "conversation",
            onDismiss = { pendingDeleteChat = null },
            onConfirm = {
                pendingDeleteChat = null
                onDeleteChat(chat)
            },
        )
    }
    pendingDeleteMessage?.let { message ->
        PermanentDeleteDialog(
            subject = "message",
            onDismiss = { pendingDeleteMessage = null },
            onConfirm = {
                pendingDeleteMessage = null
                onDeleteMessage(message)
            },
        )
    }
}

@Composable
private fun DeletedRow(
    title: String,
    supporting: String,
    onRestore: () -> Unit,
    onDelete: () -> Unit,
) {
    ListItem(
        headlineContent = { Text(title, maxLines = 2, overflow = TextOverflow.Ellipsis) },
        supportingContent = { Text(supporting) },
        trailingContent = {
            Row {
                IconButton(onClick = onRestore) {
                    Icon(Icons.Filled.Restore, contentDescription = "Restore")
                }
                IconButton(onClick = onDelete) {
                    Icon(
                        Icons.Filled.DeleteForever,
                        contentDescription = "Delete permanently",
                        tint = MaterialTheme.colorScheme.error,
                    )
                }
            }
        },
    )
    HorizontalDivider()
}

@Composable
private fun ManagementSectionLabel(title: String) {
    Text(
        text = title.uppercase(),
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 24.dp, top = 20.dp, end = 24.dp, bottom = 8.dp),
    )
}

@Composable
private fun EmptyManagementState(
    title: String,
    supporting: String,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            modifier = Modifier.padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(
                supporting,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun PermanentDeleteDialog(
    subject: String,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Delete permanently?") },
        text = { Text("This $subject will be removed from this device and cannot be restored.") },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text("Delete", color = MaterialTheme.colorScheme.error)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

private fun formatManagementDate(epochMs: Long): String =
    DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(epochMs))

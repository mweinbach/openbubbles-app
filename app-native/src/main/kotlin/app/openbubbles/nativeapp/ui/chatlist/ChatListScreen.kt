package app.openbubbles.nativeapp.ui.chatlist

import android.content.res.Configuration
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChatBubble
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.NotificationsOff
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SearchOff
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import app.openbubbles.nativeapp.data.ChatListItem
import app.openbubbles.nativeapp.ui.common.ChatAvatar
import app.openbubbles.nativeapp.ui.common.formatListTimestamp
import app.openbubbles.nativeapp.ui.common.rememberContactAvatarPath
import app.openbubbles.nativeapp.ui.theme.OpenBubblesTheme

private val RowShape = RoundedCornerShape(20.dp)

/**
 * Conversations overview: search field + pinned and recent sections with
 * rounded-20dp iMessage-style rows.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatListScreen(
    uiState: ChatListUiState,
    onQueryChange: (String) -> Unit,
    onChatClick: (ChatListItem) -> Unit,
    onOpenSettings: () -> Unit = {},
    onNewChat: () -> Unit = {},
    onTogglePinned: (ChatListItem) -> Unit = {},
    onToggleMuted: (ChatListItem) -> Unit = {},
    onToggleArchived: (ChatListItem) -> Unit = {},
    onDelete: (ChatListItem) -> Unit = {},
    modifier: Modifier = Modifier,
    footer: @Composable ColumnScope.() -> Unit = {},
) {
    var selectedChat by remember { mutableStateOf<ChatListItem?>(null) }
    var confirmDelete by remember { mutableStateOf<ChatListItem?>(null) }
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "OpenBubbles",
                        style = MaterialTheme.typography.titleLarge,
                    )
                },
                actions = {
                    IconButton(onClick = onOpenSettings) {
                        Icon(Icons.Filled.Settings, contentDescription = "Settings")
                    }
                },
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onNewChat,
                icon = { Icon(Icons.Filled.Edit, contentDescription = null) },
                text = { Text("New Chat") },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            SearchField(
                value = uiState.query,
                onValueChange = onQueryChange,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 4.dp),
            )
            when {
                uiState.loading -> LoadingState(Modifier.fillMaxSize())
                uiState.isEmpty -> EmptyState(
                    query = uiState.query,
                    onNewChat = onNewChat,
                    modifier = Modifier.fillMaxSize(),
                )
                else -> ChatSections(
                    uiState = uiState,
                    onChatClick = onChatClick,
                    onChatLongClick = { selectedChat = it },
                )
            }
            footer()
        }
    }

    selectedChat?.let { chat ->
        ChatListActionSheet(
            chat = chat,
            onTogglePinned = {
                selectedChat = null
                onTogglePinned(chat)
            },
            onToggleMuted = {
                selectedChat = null
                onToggleMuted(chat)
            },
            onToggleArchived = {
                selectedChat = null
                onToggleArchived(chat)
            },
            onDelete = {
                selectedChat = null
                confirmDelete = chat
            },
            onDismiss = { selectedChat = null },
        )
    }

    confirmDelete?.let { chat ->
        AlertDialog(
            onDismissRequest = { confirmDelete = null },
            title = { Text("Delete conversation?") },
            text = { Text("This removes ${chat.title} and its synced history from this account.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmDelete = null
                        onDelete(chat)
                    },
                ) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = null }) { Text("Cancel") }
            },
        )
    }
}

@Composable
private fun ChatSections(
    uiState: ChatListUiState,
    onChatClick: (ChatListItem) -> Unit,
    onChatLongClick: (ChatListItem) -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        if (uiState.pinned.isNotEmpty()) {
            item(key = "header-pinned") { SectionHeader("Pinned") }
            items(uiState.pinned, key = { "chat-${it.id}" }) { chat ->
                ChatListRow(
                    chat = chat,
                    onClick = onChatClick,
                    onLongClick = onChatLongClick,
                    modifier = Modifier.animateItem(),
                )
            }
        }
        if (uiState.chats.isNotEmpty()) {
            item(key = "header-recent") { SectionHeader("Messages") }
            items(uiState.chats, key = { "chat-${it.id}" }) { chat ->
                ChatListRow(
                    chat = chat,
                    onClick = onChatClick,
                    onLongClick = onChatLongClick,
                    modifier = Modifier.animateItem(),
                )
            }
        }
        if (uiState.archived.isNotEmpty()) {
            item(key = "header-archived") { SectionHeader("Archived") }
            items(uiState.archived, key = { "chat-${it.id}" }) { chat ->
                ChatListRow(
                    chat = chat,
                    onClick = onChatClick,
                    onLongClick = onChatLongClick,
                    modifier = Modifier.animateItem(),
                )
            }
        }
    }
}

@Composable
private fun SectionHeader(label: String) {
    Text(
        text = label.uppercase(),
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 8.dp, top = 8.dp, bottom = 2.dp),
    )
}

/** One iMessage-style rounded conversation row. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ChatListRow(
    chat: ChatListItem,
    onClick: (ChatListItem) -> Unit,
    onLongClick: (ChatListItem) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val unread = chat.unread > 0
    Surface(
        shape = RowShape,
        color = if (unread) {
            MaterialTheme.colorScheme.surfaceContainerHigh
        } else {
            MaterialTheme.colorScheme.surfaceContainer
        },
        modifier = modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = { onClick(chat) },
                onLongClick = { onLongClick(chat) },
            ),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            ChatAvatar(
                title = chat.title,
                avatarColor = chat.avatarColor,
                avatarPath = rememberContactAvatarPath(chat.avatarAddress),
            )
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = chat.title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = if (unread) FontWeight.SemiBold else FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    if (chat.pinned) {
                        Icon(
                            imageVector = Icons.Filled.PushPin,
                            contentDescription = "Pinned",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier
                                .padding(start = 6.dp)
                                .size(14.dp),
                        )
                    }
                    Text(
                        text = formatListTimestamp(chat.date),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 8.dp),
                    )
                }
                Spacer(Modifier.height(2.dp))
                Text(
                    text = chat.snippet.orEmpty(),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = if (unread) FontWeight.Medium else FontWeight.Normal,
                    color = if (unread) {
                        MaterialTheme.colorScheme.onSurface
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (chat.muted) {
                Icon(
                    imageVector = Icons.Filled.NotificationsOff,
                    contentDescription = "Muted",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(17.dp),
                )
            }
            if (unread) {
                UnreadBadge(count = chat.unread)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ChatListActionSheet(
    chat: ChatListItem,
    onTogglePinned: () -> Unit,
    onToggleMuted: () -> Unit,
    onToggleArchived: () -> Unit,
    onDelete: () -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Text(
            text = chat.title,
            style = MaterialTheme.typography.titleMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
        )
        ChatActionButton(
            text = if (chat.pinned) "Unpin" else "Pin",
            icon = Icons.Filled.PushPin,
            onClick = onTogglePinned,
        )
        ChatActionButton(
            text = if (chat.muted) "Show alerts" else "Hide alerts",
            icon = Icons.Filled.NotificationsOff,
            onClick = onToggleMuted,
        )
        ChatActionButton(
            text = if (chat.archived) "Unarchive" else "Archive",
            icon = Icons.Filled.Archive,
            onClick = onToggleArchived,
        )
        ChatActionButton(text = "Delete", icon = Icons.Filled.Delete, onClick = onDelete)
    }
}

@Composable
private fun ChatActionButton(
    text: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit,
) {
    TextButton(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp),
    ) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(20.dp))
        Text(text, modifier = Modifier.fillMaxWidth().padding(start = 12.dp))
    }
}

/** Primary-colored pill with a 99+ cap, iMessage-style unread marker. */
@Composable
private fun UnreadBadge(count: Int, modifier: Modifier = Modifier) {
    Surface(
        shape = RoundedCornerShape(999.dp),
        color = MaterialTheme.colorScheme.primary,
        contentColor = MaterialTheme.colorScheme.onPrimary,
        modifier = modifier,
    ) {
        Text(
            text = if (count > 99) "99+" else count.toString(),
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
        )
    }
}

@Composable
private fun SearchField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    TextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier,
        placeholder = { Text("Search") },
        leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
        singleLine = true,
        shape = RoundedCornerShape(16.dp),
        colors = TextFieldDefaults.colors(
            focusedIndicatorColor = Color.Transparent,
            unfocusedIndicatorColor = Color.Transparent,
            focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        ),
    )
}

@Composable
private fun LoadingState(modifier: Modifier = Modifier) {
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        CircularProgressIndicator()
    }
}

/**
 * Branded, actionable empty state: for no-query it points at the FAB with a
 * "Start a chat" button; for an active query it explains the miss.
 */
@Composable
private fun EmptyState(query: String, onNewChat: () -> Unit, modifier: Modifier = Modifier) {
    val hasQuery = query.isNotBlank()
    Column(
        modifier = modifier.padding(horizontal = 32.dp, vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            modifier = Modifier.size(72.dp),
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = if (hasQuery) Icons.Filled.SearchOff else Icons.Filled.ChatBubble,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(32.dp),
                )
            }
        }
        Spacer(Modifier.height(16.dp))
        Text(
            text = if (hasQuery) "No results" else "No conversations yet",
            style = MaterialTheme.typography.titleLarge,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = if (hasQuery) {
                "Nothing matches “${query.trim()}”"
            } else {
                "Messages you send and receive will show up here."
            },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )
        if (!hasQuery) {
            Spacer(Modifier.height(20.dp))
            Button(onClick = onNewChat) {
                Icon(
                    imageVector = Icons.Filled.Edit,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(Modifier.size(8.dp))
                Text("Start a chat")
            }
            Spacer(Modifier.height(8.dp))
            Text(
                text = "Or tap the New Chat button in the corner anytime.",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            )
        }
    }
}

// --------------------------------------------------------------------- previews

@Preview(showBackground = true)
@Preview(showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun ChatListRowPreview() {
    OpenBubblesTheme {
        ChatListRow(
            chat = ChatListItem(
                id = 1,
                title = "Alex Chen",
                snippet = "sounds good — see you at the trailhead",
                date = System.currentTimeMillis() - 52 * 60_000L,
                unread = 2,
                pinned = false,
                avatarColor = 0xFF34C759,
            ),
            onClick = {},
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun ChatListRowUnreadPreview() {
    OpenBubblesTheme {
        ChatListRow(
            chat = ChatListItem(
                id = 2,
                title = "Design Team",
                snippet = "Maya: pushed the new mocks to Figma",
                date = System.currentTimeMillis() - 18 * 60_000L,
                unread = 12,
                pinned = true,
                avatarColor = 0xFFAF52DE,
            ),
            onClick = {},
        )
    }
}

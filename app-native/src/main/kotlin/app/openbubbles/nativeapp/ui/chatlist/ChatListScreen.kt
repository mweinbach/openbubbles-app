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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChatBubble
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.NotificationsOff
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SearchOff
import androidx.compose.material3.Button
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import app.openbubbles.nativeapp.data.ChatListItem
import app.openbubbles.nativeapp.ui.common.ChatAvatar
import app.openbubbles.nativeapp.ui.common.formatListTimestamp
import app.openbubbles.nativeapp.ui.common.rememberContactAvatarPath
import app.openbubbles.nativeapp.ui.theme.OpenBubblesTheme

private val ListContentMaxWidth = 840.dp

/**
 * Expressive conversations overview with a collapsing hero title and an
 * adaptive, width-constrained list that remains comfortable on large screens.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatListScreen(
    uiState: ChatListUiState,
    onQueryChange: (String) -> Unit,
    onChatClick: (ChatListItem) -> Unit,
    onNewChat: () -> Unit = {},
    onTogglePinned: (ChatListItem) -> Unit = {},
    onToggleMuted: (ChatListItem) -> Unit = {},
    onMuteFor: (ChatListItem, Long) -> Unit = { _, _ -> },
    onToggleArchived: (ChatListItem) -> Unit = {},
    onDelete: (ChatListItem) -> Unit = {},
    modifier: Modifier = Modifier,
    /**
     * Banner slot rendered above the conversation list, inside the Scaffold.
     * It has to live here rather than above the NavHost: NavigationSuiteScaffold
     * does not propagate its PaddingValues to inner content, so a banner hoisted
     * outside the Scaffold draws underneath the status bar and pushes the app bar
     * down.
     */
    header: @Composable ColumnScope.() -> Unit = {},
    footer: @Composable ColumnScope.() -> Unit = {},
) {
    var selectedChat by remember { mutableStateOf<ChatListItem?>(null) }
    var confirmDelete by remember { mutableStateOf<ChatListItem?>(null) }
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val conversationCount = uiState.pinned.size + uiState.chats.size + uiState.archived.size
    val unreadCount = (uiState.pinned + uiState.chats + uiState.archived).sumOf { it.unread }
    val subtitle = when {
        uiState.loading -> "Loading conversations"
        uiState.query.isNotBlank() -> "$conversationCount ${if (conversationCount == 1) "result" else "results"}"
        conversationCount == 0 -> "Messages from your Apple devices"
        unreadCount > 0 -> "$conversationCount conversations • $unreadCount unread"
        else -> "$conversationCount ${if (conversationCount == 1) "conversation" else "conversations"}"
    }
    Scaffold(
        modifier = modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        containerColor = MaterialTheme.colorScheme.surface,
        topBar = {
            LargeFlexibleTopAppBar(
                title = { Text("Messages", maxLines = 1) },
                subtitle = { Text(subtitle, maxLines = 1) },
                scrollBehavior = scrollBehavior,
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    scrolledContainerColor = MaterialTheme.colorScheme.surface,
                ),
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = onNewChat,
                shape = MaterialTheme.shapes.large,
            ) {
                Icon(Icons.Filled.Edit, contentDescription = "New chat")
            }
        },
    ) { padding ->
        when {
            uiState.loading -> Column(
                modifier = Modifier.fillMaxSize().padding(padding),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                SearchField(
                    value = uiState.query,
                    onValueChange = onQueryChange,
                    modifier = Modifier.widthIn(max = ListContentMaxWidth).fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                )
                LoadingState(Modifier.fillMaxSize())
                footer()
            }

            uiState.isEmpty -> Column(
                modifier = Modifier.fillMaxSize().padding(padding),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                SearchField(
                    value = uiState.query,
                    onValueChange = onQueryChange,
                    modifier = Modifier.widthIn(max = ListContentMaxWidth).fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                )
                EmptyState(
                    query = uiState.query,
                    onNewChat = onNewChat,
                    modifier = Modifier.widthIn(max = ListContentMaxWidth).fillMaxSize(),
                )
                footer()
            }

            else -> ChatSections(
                uiState = uiState,
                contentPadding = PaddingValues(
                    top = padding.calculateTopPadding() + 8.dp,
                    bottom = padding.calculateBottomPadding() + 88.dp,
                ),
                onQueryChange = onQueryChange,
                onChatClick = onChatClick,
                onChatLongClick = { selectedChat = it },
                header = header,
                footer = footer,
            )
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
            onMuteFor = { durationMs ->
                selectedChat = null
                onMuteFor(chat, durationMs)
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
    contentPadding: PaddingValues,
    onQueryChange: (String) -> Unit,
    onChatClick: (ChatListItem) -> Unit,
    onChatLongClick: (ChatListItem) -> Unit,
    header: @Composable ColumnScope.() -> Unit,
    footer: @Composable ColumnScope.() -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = contentPadding,
        verticalArrangement = Arrangement.spacedBy(6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        item(key = "header") {
            Column(modifier = Modifier.widthIn(max = ListContentMaxWidth).fillMaxWidth()) { header() }
        }
        item(key = "search") {
            SearchField(
                value = uiState.query,
                onValueChange = onQueryChange,
                modifier = Modifier.widthIn(max = ListContentMaxWidth).fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
            )
        }
        if (uiState.pinned.isNotEmpty()) {
            item(key = "header-pinned") {
                SectionHeader("Pinned", Modifier.widthIn(max = ListContentMaxWidth).fillMaxWidth())
            }
            items(
                items = uiState.pinned,
                key = { "chat-${it.id}" },
                contentType = { "conversation" },
            ) { chat ->
                ChatListRow(
                    chat = chat,
                    onClick = onChatClick,
                    onLongClick = onChatLongClick,
                    modifier = Modifier.widthIn(max = ListContentMaxWidth)
                        .padding(horizontal = 12.dp).animateItem(),
                )
            }
        }
        if (uiState.chats.isNotEmpty()) {
            item(key = "header-recent") {
                SectionHeader("Recent", Modifier.widthIn(max = ListContentMaxWidth).fillMaxWidth())
            }
            items(
                items = uiState.chats,
                key = { "chat-${it.id}" },
                contentType = { "conversation" },
            ) { chat ->
                ChatListRow(
                    chat = chat,
                    onClick = onChatClick,
                    onLongClick = onChatLongClick,
                    modifier = Modifier.widthIn(max = ListContentMaxWidth)
                        .padding(horizontal = 12.dp).animateItem(),
                )
            }
        }
        if (uiState.archived.isNotEmpty()) {
            item(key = "header-archived") {
                SectionHeader("Archived", Modifier.widthIn(max = ListContentMaxWidth).fillMaxWidth())
            }
            items(
                items = uiState.archived,
                key = { "chat-${it.id}" },
                contentType = { "conversation" },
            ) { chat ->
                ChatListRow(
                    chat = chat,
                    onClick = onChatClick,
                    onLongClick = onChatLongClick,
                    modifier = Modifier.widthIn(max = ListContentMaxWidth)
                        .padding(horizontal = 12.dp).animateItem(),
                )
            }
        }
        item(key = "footer") {
            Column(modifier = Modifier.widthIn(max = ListContentMaxWidth).fillMaxWidth()) { footer() }
        }
    }
}

@Composable
private fun SectionHeader(label: String, modifier: Modifier = Modifier) {
    Text(
        text = label.uppercase(),
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.primary,
        modifier = modifier.padding(start = 20.dp, top = 14.dp, bottom = 4.dp),
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
        shape = MaterialTheme.shapes.large,
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
                avatarPath = chat.avatarPath ?: rememberContactAvatarPath(chat.avatarAddress),
            )
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = chat.title,
                        style = if (unread) {
                            MaterialTheme.typography.titleMediumEmphasized
                        } else {
                            MaterialTheme.typography.titleMedium
                        },
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
                    style = if (unread) {
                        MaterialTheme.typography.bodyMediumEmphasized
                    } else {
                        MaterialTheme.typography.bodyMedium
                    },
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
    onMuteFor: (Long) -> Unit,
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
        if (chat.muted) {
            ChatActionButton(
                text = "Show alerts",
                icon = Icons.Filled.NotificationsOff,
                onClick = onToggleMuted,
            )
        } else {
            ChatActionButton(
                text = "Mute for 1 hour",
                icon = Icons.Filled.NotificationsOff,
                onClick = { onMuteFor(60 * 60 * 1_000L) },
            )
            ChatActionButton(
                text = "Mute for 8 hours",
                icon = Icons.Filled.NotificationsOff,
                onClick = { onMuteFor(8 * 60 * 60 * 1_000L) },
            )
            ChatActionButton(
                text = "Hide alerts",
                icon = Icons.Filled.NotificationsOff,
                onClick = onToggleMuted,
            )
        }
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
        trailingIcon = if (value.isNotEmpty()) {
            {
                IconButton(onClick = { onValueChange("") }) {
                    Icon(Icons.Filled.Clear, contentDescription = "Clear search")
                }
            }
        } else {
            null
        },
        singleLine = true,
        shape = MaterialTheme.shapes.extraLarge,
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
        LoadingIndicator()
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
            shape = MaterialTheme.shapes.extraLargeIncreased,
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

@Preview(name = "Messages phone", showBackground = true, widthDp = 411, heightDp = 891)
@Preview(name = "Messages tablet", showBackground = true, widthDp = 900, heightDp = 600)
@Composable
private fun ChatListScreenPreview() {
    val now = System.currentTimeMillis()
    OpenBubblesTheme {
        ChatListScreen(
            uiState = ChatListUiState(
                pinned = listOf(
                    ChatListItem(
                        id = 1,
                        title = "Family",
                        snippet = "Dinner at 7? I can bring dessert.",
                        date = now - 12 * 60_000L,
                        unread = 3,
                        pinned = true,
                        avatarColor = 0xFF6750A4,
                    ),
                ),
                chats = listOf(
                    ChatListItem(
                        id = 2,
                        title = "Alex Chen",
                        snippet = "The photos turned out great!",
                        date = now - 52 * 60_000L,
                        unread = 1,
                        pinned = false,
                        avatarColor = 0xFF006C4C,
                    ),
                    ChatListItem(
                        id = 3,
                        title = "Design Team",
                        snippet = "Maya: pushed the new mocks to Figma",
                        date = now - 3 * 60 * 60_000L,
                        unread = 0,
                        pinned = false,
                        avatarColor = 0xFF8C4A60,
                    ),
                    ChatListItem(
                        id = 4,
                        title = "Weekend hike",
                        snippet = "sounds good — see you at the trailhead",
                        date = now - 22 * 60 * 60_000L,
                        unread = 0,
                        pinned = false,
                        muted = true,
                        avatarColor = 0xFF386A20,
                    ),
                ),
            ),
            onQueryChange = {},
            onChatClick = {},
        )
    }
}

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

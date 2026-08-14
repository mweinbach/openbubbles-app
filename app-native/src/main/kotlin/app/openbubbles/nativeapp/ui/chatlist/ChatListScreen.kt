package app.openbubbles.nativeapp.ui.chatlist

import android.content.res.Configuration
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Forum
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Badge
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import app.openbubbles.nativeapp.data.ChatListItem
import app.openbubbles.nativeapp.ui.common.ChatAvatar
import app.openbubbles.nativeapp.ui.common.formatListTimestamp
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
    modifier: Modifier = Modifier,
    footer: @Composable ColumnScope.() -> Unit = {},
) {
    Scaffold(
        modifier = modifier,
        topBar = { TopAppBar(title = { Text("OpenBubbles") }) },
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
                uiState.isEmpty -> EmptyState(query = uiState.query, modifier = Modifier.fillMaxSize())
                else -> ChatSections(uiState = uiState, onChatClick = onChatClick)
            }
            footer()
        }
    }
}

@Composable
private fun ChatSections(
    uiState: ChatListUiState,
    onChatClick: (ChatListItem) -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        if (uiState.pinned.isNotEmpty()) {
            item(key = "header-pinned") { SectionHeader("Pinned") }
            items(uiState.pinned, key = { "chat-${it.id}" }) { chat ->
                ChatListRow(chat = chat, onClick = onChatClick)
            }
        }
        if (uiState.chats.isNotEmpty()) {
            item(key = "header-recent") { SectionHeader("Messages") }
            items(uiState.chats, key = { "chat-${it.id}" }) { chat ->
                ChatListRow(chat = chat, onClick = onChatClick)
            }
        }
    }
}

@Composable
private fun SectionHeader(label: String) {
    Text(
        text = label,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = 8.dp, top = 4.dp, bottom = 2.dp),
    )
}

/** One iMessage-style rounded conversation row. */
@Composable
fun ChatListRow(
    chat: ChatListItem,
    onClick: (ChatListItem) -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        shape = RowShape,
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = modifier
            .fillMaxWidth()
            .clickable { onClick(chat) },
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            ChatAvatar(title = chat.title, avatarColor = chat.avatarColor)
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = chat.title,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        text = formatListTimestamp(chat.date),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Text(
                    text = chat.snippet.orEmpty(),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (chat.unread > 0) {
                Badge { Text(chat.unread.toString()) }
            }
        }
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
        shape = RoundedCornerShape(14.dp),
        colors = TextFieldDefaults.colors(
            focusedIndicatorColor = Color.Transparent,
            unfocusedIndicatorColor = Color.Transparent,
        ),
    )
}

@Composable
private fun LoadingState(modifier: Modifier = Modifier) {
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        CircularProgressIndicator()
    }
}

@Composable
private fun EmptyState(query: String, modifier: Modifier = Modifier) {
    val hasQuery = query.isNotBlank()
    Column(
        modifier = modifier.padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            imageVector = Icons.Filled.Forum,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(44.dp),
        )
        Spacer(Modifier.height(12.dp))
        Text(
            text = if (hasQuery) "No results" else "No conversations",
            style = MaterialTheme.typography.titleMedium,
        )
        Text(
            text = if (hasQuery) "Nothing matches “${query.trim()}”" else "Messages will appear here once you start chatting.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
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
                pinned = false,
                avatarColor = 0xFFAF52DE,
            ),
            onClick = {},
        )
    }
}

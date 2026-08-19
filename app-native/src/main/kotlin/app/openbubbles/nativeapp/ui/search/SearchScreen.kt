package app.openbubbles.nativeapp.ui.search

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.input.clearText
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ChatBubble
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SearchOff
import androidx.compose.material3.ExpandedDockedSearchBar
import androidx.compose.material3.ExpandedFullScreenSearchBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SearchBar
import androidx.compose.material3.SearchBarDefaults
import androidx.compose.material3.SearchBarValue
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberSearchBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import app.openbubbles.core.contacts.RawContact
import app.openbubbles.nativeapp.data.ChatListItem
import app.openbubbles.nativeapp.data.RichLinkPreview
import app.openbubbles.nativeapp.ui.common.ChatAvatar
import app.openbubbles.nativeapp.ui.common.SegmentedRowGap
import app.openbubbles.nativeapp.ui.common.avatarColorFor
import app.openbubbles.nativeapp.ui.common.formatListTimestamp
import app.openbubbles.nativeapp.ui.common.rememberContactAvatarPath
import app.openbubbles.nativeapp.ui.common.segmentedRowShape
import app.openbubbles.nativeapp.ui.theme.OpenBubblesTheme

private val SearchContentMaxWidth = 840.dp

/**
 * Dedicated cross-store search: one field, results grouped into chats,
 * people, messages, and links, with the matching text highlighted in each
 * row. Rows use the connected segmented-group idiom from the conversation-
 * details screen so each section reads as one object.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchScreen(
    uiState: SearchUiState,
    onQueryChange: (String) -> Unit,
    onOpenChat: (Long) -> Unit,
    onOpenContact: (RawContact) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    /**
     * Medium+ list-detail: dock the expanded results to the collapsed bar
     * in the detail pane. Compact windows use the full-screen expanded bar.
     */
    docked: Boolean = false,
) {
    val searchBarState = rememberSearchBarState(
        initialValue = if (docked) SearchBarValue.Collapsed else SearchBarValue.Expanded,
    )
    val textFieldState = rememberTextFieldState(initialText = uiState.query)
    val scope = rememberCoroutineScope()
    val keyboard = LocalSoftwareKeyboardController.current
    val uriHandler = LocalUriHandler.current
    val highlight = uiState.query.trim()

    LaunchedEffect(textFieldState) {
        snapshotFlow { textFieldState.text.toString() }
            .distinctUntilChanged()
            .collect(onQueryChange)
    }
    LaunchedEffect(docked) {
        if (docked) searchBarState.animateToExpanded()
    }
    // Collapse (system back on the expanded bar, or the leading icon) leaves
    // the destination. Do not fire on the docked first frame, which starts
    // collapsed so the bar can measure before the popup anchors to it.
    var hasExpanded by remember { mutableStateOf(!docked) }
    LaunchedEffect(searchBarState.currentValue) {
        when (searchBarState.currentValue) {
            SearchBarValue.Expanded -> hasExpanded = true
            SearchBarValue.Collapsed -> if (hasExpanded) onBack()
        }
    }

    val inputField =
        @Composable {
            SearchBarDefaults.InputField(
                textFieldState = textFieldState,
                searchBarState = searchBarState,
                onSearch = { keyboard?.hide() },
                placeholder = {
                    Text(
                        modifier = Modifier.clearAndSetSemantics {},
                        text = "Chats, people, messages, links",
                    )
                },
                leadingIcon = {
                    IconButton(
                        onClick = { scope.launch { searchBarState.animateToCollapsed() } },
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                trailingIcon = if (textFieldState.text.isNotEmpty()) {
                    {
                        IconButton(onClick = { textFieldState.clearText() }) {
                            Icon(Icons.Filled.Clear, contentDescription = "Clear search")
                        }
                    }
                } else {
                    null
                },
            )
        }
    val results: @Composable ColumnScope.() -> Unit = {
        SearchResults(
            uiState = uiState,
            highlight = highlight,
            uriHandlerOpen = { url -> runCatching { uriHandler.openUri(url) } },
            onOpenChat = onOpenChat,
            onOpenContact = onOpenContact,
        )
    }

    Box(modifier = modifier.fillMaxSize()) {
        SearchBar(
            state = searchBarState,
            inputField = inputField,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
        )
        if (docked) {
            ExpandedDockedSearchBar(
                state = searchBarState,
                inputField = inputField,
                content = results,
            )
        } else {
            ExpandedFullScreenSearchBar(
                state = searchBarState,
                inputField = inputField,
                content = results,
            )
        }
    }
}

@Composable
private fun SearchResults(
    uiState: SearchUiState,
    highlight: String,
    uriHandlerOpen: (String) -> Unit,
    onOpenChat: (Long) -> Unit,
    onOpenContact: (RawContact) -> Unit,
) {
    when {
        uiState.query.isBlank() -> SearchPlaceholder(
            icon = Icons.Filled.Search,
            title = "Search everything",
            body = "Find chats, people, messages, and links across your conversations.",
            modifier = Modifier
                .fillMaxSize()
                .navigationBarsPadding(),
        )
        uiState.searching -> SearchLoading(
            modifier = Modifier
                .fillMaxSize()
                .navigationBarsPadding(),
        )
        uiState.query.trim().length < 2 -> SearchPlaceholder(
            icon = Icons.Filled.Search,
            title = "Keep typing",
            body = "Enter at least two characters to search.",
            modifier = Modifier
                .fillMaxSize()
                .navigationBarsPadding(),
        )
        uiState.error != null -> SearchPlaceholder(
            icon = Icons.Filled.SearchOff,
            title = "Search unavailable",
            body = uiState.error,
            modifier = Modifier
                .fillMaxSize()
                .navigationBarsPadding(),
        )
        !uiState.hasResults -> SearchPlaceholder(
            icon = Icons.Filled.SearchOff,
            title = "No results",
            body = "Nothing matches “${uiState.query.trim()}”",
            modifier = Modifier
                .fillMaxSize()
                .navigationBarsPadding(),
        )
        else -> LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .navigationBarsPadding(),
            contentPadding = PaddingValues(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(SegmentedRowGap),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            if (uiState.chats.isNotEmpty()) {
                item(key = "header-chats") { SearchSectionHeader("Chats") }
                itemsIndexed(
                    uiState.chats,
                    key = { _, chat -> "chat-${chat.id}" },
                ) { index, chat ->
                    ChatResultRow(
                        chat = chat,
                        highlight = highlight,
                        shape = segmentedRowShape(index, uiState.chats.size),
                        onClick = { onOpenChat(chat.id) },
                    )
                }
            }
            if (uiState.people.isNotEmpty()) {
                item(key = "header-people") { SearchSectionHeader("People") }
                itemsIndexed(
                    uiState.people,
                    key = { _, contact -> "person-${contact.id}" },
                ) { index, contact ->
                    PersonResultRow(
                        contact = contact,
                        highlight = highlight,
                        shape = segmentedRowShape(index, uiState.people.size),
                        onClick = { onOpenContact(contact) },
                    )
                }
            }
            if (uiState.messages.isNotEmpty()) {
                item(key = "header-messages") { SearchSectionHeader("Messages") }
                itemsIndexed(
                    uiState.messages,
                    key = { _, row -> "message-${row.guid}" },
                ) { index, row ->
                    MessageResultRow(
                        row = row,
                        highlight = highlight,
                        shape = segmentedRowShape(index, uiState.messages.size),
                        onClick = { onOpenChat(row.chatId) },
                    )
                }
            }
            if (uiState.links.isNotEmpty()) {
                item(key = "header-links") { SearchSectionHeader("Links") }
                itemsIndexed(
                    uiState.links,
                    key = { _, row -> "link-${row.guid}" },
                ) { index, row ->
                    LinkResultRow(
                        row = row,
                        highlight = highlight,
                        shape = segmentedRowShape(index, uiState.links.size),
                        onClick = {
                            row.link?.url?.let(uriHandlerOpen)
                        },
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun SearchLoading(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.semantics(mergeDescendants = true) {
            contentDescription = "Searching"
            progressBarRangeInfo = ProgressBarRangeInfo.Indeterminate
        },
        contentAlignment = Alignment.Center,
    ) {
        LoadingIndicator()
    }
}

/** Every [query] occurrence, case-insensitive, in primary + bold. */
@Composable
private fun highlightedText(text: String, query: String): AnnotatedString {
    val highlightColor = MaterialTheme.colorScheme.primary
    return remember(text, query, highlightColor) {
        val needle = query.trim()
        if (needle.isEmpty()) return@remember AnnotatedString(text)
        buildAnnotatedString {
            var index = 0
            while (index < text.length) {
                val hit = text.indexOf(needle, index, ignoreCase = true)
                if (hit < 0) {
                    append(text.substring(index))
                    break
                }
                append(text.substring(index, hit))
                withStyle(SpanStyle(color = highlightColor, fontWeight = FontWeight.Bold)) {
                    append(text.substring(hit, hit + needle.length))
                }
                index = hit + needle.length
            }
        }
    }
}

@Composable
private fun SearchSectionHeader(title: String, modifier: Modifier = Modifier) {
    Text(
        text = title.uppercase(),
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier
            .widthIn(max = SearchContentMaxWidth)
            .fillMaxWidth()
            .padding(start = 28.dp, top = 16.dp, bottom = 4.dp),
    )
}

@Composable
private fun ResultRowShell(
    shape: RoundedCornerShape,
    onClickLabel: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable RowScope.() -> Unit,
) {
    Surface(
        shape = shape,
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = modifier
            .widthIn(max = SearchContentMaxWidth)
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .clickable(role = Role.Button, onClickLabel = onClickLabel, onClick = onClick),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            content()
        }
    }
}

@Composable
private fun ChatResultRow(
    chat: ChatListItem,
    highlight: String,
    shape: RoundedCornerShape,
    onClick: () -> Unit,
) {
    ResultRowShell(shape = shape, onClickLabel = "Open conversation", onClick = onClick) {
        ChatAvatar(
            title = chat.title,
            avatarColor = chat.avatarColor,
            size = 40.dp,
            avatarPath = chat.avatarPath ?: rememberContactAvatarPath(chat.avatarAddress),
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = highlightedText(chat.title, highlight),
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            chat.snippet?.takeIf { it.isNotBlank() }?.let { snippet ->
                Text(
                    text = highlightedText(snippet, highlight),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun PersonResultRow(
    contact: RawContact,
    highlight: String,
    shape: RoundedCornerShape,
    onClick: () -> Unit,
) {
    val displayName = contact.displayName
        ?: contact.addresses.firstOrNull()
        ?: return
    ResultRowShell(shape = shape, onClickLabel = "Open conversation", onClick = onClick) {
        ChatAvatar(
            title = displayName,
            avatarColor = avatarColorFor(displayName),
            size = 40.dp,
            avatarPath = contact.avatarPath,
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = highlightedText(displayName, highlight),
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            contact.addresses.firstOrNull()?.let { address ->
                Text(
                    text = highlightedText(address, highlight),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun MessageResultRow(
    row: SearchMessageRow,
    highlight: String,
    shape: RoundedCornerShape,
    onClick: () -> Unit,
) {
    ResultRowShell(shape = shape, onClickLabel = "Open conversation", onClick = onClick) {
        val chat = row.chat
        if (chat != null) {
            ChatAvatar(
                title = chat.title,
                avatarColor = chat.avatarColor,
                size = 40.dp,
                avatarPath = chat.avatarPath ?: rememberContactAvatarPath(chat.avatarAddress),
            )
        } else {
            ResultIconTile(icon = Icons.Filled.ChatBubble)
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = listOfNotNull(
                    chat?.title,
                    formatListTimestamp(row.dateMillis).takeIf { row.dateMillis > 0L },
                ).joinToString(" · "),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = highlightedText(row.text, highlight),
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun LinkResultRow(
    row: SearchMessageRow,
    highlight: String,
    shape: RoundedCornerShape,
    onClick: () -> Unit,
) {
    val link = row.link ?: return
    ResultRowShell(shape = shape, onClickLabel = "Open link", onClick = onClick) {
        ResultIconTile(icon = Icons.Filled.Link)
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = highlightedText(link.title ?: link.displayHost, highlight),
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = highlightedText(
                    listOf(link.displayHost, row.chat?.title).joinToString(" · "),
                    highlight,
                ),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun ResultIconTile(icon: ImageVector, modifier: Modifier = Modifier) {
    Surface(
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier.size(40.dp),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(imageVector = icon, contentDescription = null, modifier = Modifier.size(20.dp))
        }
    }
}

@Composable
private fun SearchPlaceholder(
    icon: ImageVector,
    title: String,
    body: String,
    modifier: Modifier = Modifier,
) {
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
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(32.dp),
                )
            }
        }
        Spacer(Modifier.height(16.dp))
        Text(text = title, style = MaterialTheme.typography.titleLargeEmphasized)
        Spacer(Modifier.height(6.dp))
        Text(
            text = body,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

// --------------------------------------------------------------------- previews

private val PreviewChat = ChatListItem(
    id = 2,
    title = "Alex Chen",
    snippet = "grabbing coffee now, want anything?",
    date = 1_759_700_000_000,
    unread = 0,
    pinned = false,
    avatarColor = 0xFF006C4C,
)

@Preview(showBackground = true)
@Composable
private fun SearchScreenPreview() {
    OpenBubblesTheme {
        SearchScreen(
            uiState = SearchUiState(
                query = "coffee",
                chats = listOf(PreviewChat),
                people = listOf(
                    RawContact(
                        id = "p1", displayName = "Courtney Coffeeson",
                        firstName = "Courtney", lastName = "Coffeeson",
                        avatarPath = null, addresses = listOf("courtney@icloud.com"),
                    ),
                ),
                messages = listOf(
                    SearchMessageRow(
                        guid = "m1", chatId = 2, chat = PreviewChat,
                        text = "coffee sounds perfect — see you at the trailhead",
                        dateMillis = 1_759_690_000_000,
                    ),
                ),
                links = listOf(
                    SearchMessageRow(
                        guid = "m2", chatId = 2, chat = PreviewChat,
                        text = "https://www.nps.gov/yose/index.htm",
                        dateMillis = 1_759_680_000_000,
                        link = RichLinkPreview(
                            url = "https://www.nps.gov/yose/index.htm",
                            displayHost = "nps.gov",
                            title = "Coffee Country: Yosemite National Park",
                            summary = null,
                            imageBytes = null, imageMime = null,
                            iconBytes = null, iconMime = null,
                        ),
                    ),
                ),
            ),
            onQueryChange = {},
            onOpenChat = {},
            onOpenContact = {},
            onBack = {},
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun SearchScreenEmptyPreview() {
    OpenBubblesTheme {
        SearchScreen(
            uiState = SearchUiState(),
            onQueryChange = {},
            onOpenChat = {},
            onOpenContact = {},
            onBack = {},
        )
    }
}

package app.openbubbles.nativeapp.ui.chatlist

import android.content.res.Configuration
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.ChatBubble
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.NotificationsOff
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Unarchive
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MediumFloatingActionButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.platform.LocalContext
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.animateFloatingActionButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.openbubbles.nativeapp.data.AppearancePrefs
import app.openbubbles.nativeapp.data.ChatListItem
import app.openbubbles.nativeapp.data.MessagingPrefs
import app.openbubbles.nativeapp.data.visibleTranscriptPrefetchIds
import app.openbubbles.nativeapp.ui.common.ChatAvatar
import app.openbubbles.nativeapp.ui.common.formatListTimestamp
import app.openbubbles.nativeapp.ui.common.rememberContactAvatarPath
import app.openbubbles.nativeapp.ui.common.rememberPolygonMorph
import app.openbubbles.nativeapp.ui.common.sharedChatContainer
import app.openbubbles.nativeapp.ui.settings.SettingsChoiceItem
import app.openbubbles.nativeapp.ui.settings.SettingsGroup
import app.openbubbles.nativeapp.ui.theme.OpenBubblesTheme
import app.openbubbles.nativeapp.ui.theme.fastSpatialSpec
import app.openbubbles.nativeapp.ui.theme.rememberItemAnimationSpecs
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.delay

private val ListContentMaxWidth = 840.dp

/** Minimum pinned-tile width, matching [GridCells.Adaptive] column math. */
internal val PinnedChatMinCell = 120.dp

/** Column count [GridCells.Adaptive] would use for [maxWidth]. */
internal fun pinnedChatColumnCount(maxWidth: Dp, minCell: Dp = PinnedChatMinCell): Int =
    maxOf(1, (maxWidth / minCell).toInt())

/** Inbox is the main conversation list; Archive is the Settings manager. */
enum class ChatListKind { Inbox, Archive }

/**
 * Conversations overview with compact Messages-style chrome and an adaptive,
 * width-constrained list that remains comfortable on large screens.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatListScreen(
    uiState: ChatListUiState,
    onChatClick: (ChatListItem) -> Unit,
    modifier: Modifier = Modifier,
    onNewChat: () -> Unit = {},
    onOpenSearch: () -> Unit = {},
    onOpenFindMy: () -> Unit = {},
    onOpenSettings: () -> Unit = {},
    onTogglePinned: (ChatListItem) -> Unit = {},
    onToggleMuted: (ChatListItem) -> Unit = {},
    onMuteFor: (ChatListItem, Long) -> Unit = { _, _ -> },
    onArchive: (Collection<Long>) -> Unit = {},
    onUnarchive: (Collection<Long>) -> Unit = {},
    onDelete: (Collection<Long>) -> Unit = {},
    /** Registered handles (rust form) offered by the per-chat send-from picker. */
    sendFromChoices: List<String> = emptyList(),
    /** Global default sending handle, read fresh when the picker opens. */
    defaultSendingHandle: () -> String? = { null },
    /** Persists a per-chat send-from override (null = follow the default). */
    onSetSendFrom: (ChatListItem, String?) -> Unit = { _, _ -> },
    kind: ChatListKind = ChatListKind.Inbox,
    showBackButton: Boolean = false,
    onBack: () -> Unit = {},
    /**
     * Conversation currently open in the detail pane, so its row reads as
     * selected. Null on compact windows, where the list and a conversation are
     * never visible together and a highlight would be meaningless.
     */
    selectedChatId: Long? = null,
    /** Pane background — surfaceContainerLow in two-pane for tonal layering. */
    containerColor: Color? = null,
    /**
     * Banner slot rendered above the conversation list, inside the Scaffold.
     * Keeping it here makes the banner share the app bar's insets and prevents a
     * host-level banner from drawing underneath the status bar.
     */
    header: @Composable ColumnScope.() -> Unit = {},
    footer: @Composable ColumnScope.() -> Unit = {},
    /**
     * Conversation ids currently on screen plus a small off-screen buffer.
     * Used to warm the newest transcripts so opening a row is instant.
     */
    onVisibleChatsChanged: (List<Long>) -> Unit = {},
) {
    var selectedIds by rememberSaveable { mutableStateOf(setOf<Long>()) }
    var actionChat by remember { mutableStateOf<ChatListItem?>(null) }
    var sendFromChat by remember { mutableStateOf<ChatListItem?>(null) }
    var confirmDeleteIds by remember { mutableStateOf<Set<Long>?>(null) }
    var profileMenuExpanded by remember { mutableStateOf(false) }
    val selecting = selectedIds.isNotEmpty()
    val paneColor = containerColor ?: MaterialTheme.colorScheme.surface
    val visibleChats = remember(uiState.pinned, uiState.chats, uiState.archived, kind) {
        when (kind) {
            ChatListKind.Inbox -> uiState.pinned + uiState.chats
            ChatListKind.Archive -> uiState.archived
        }
    }
    fun chatById(id: Long): ChatListItem? = visibleChats.firstOrNull { it.id == id }
    fun clearSelection() {
        selectedIds = emptySet()
    }
    fun toggleSelected(chat: ChatListItem) {
        selectedIds = selectedIds.toMutableSet().apply {
            if (!add(chat.id)) remove(chat.id)
        }
    }
    BackHandler(enabled = selecting) { clearSelection() }
    val listState = rememberLazyListState()
    val scrollBehavior = TopAppBarDefaults.enterAlwaysScrollBehavior()
    var hideFabOnScroll by remember { mutableStateOf(false) }
    LaunchedEffect(listState) {
        var lastIndex = 0
        var lastOffset = 0
        snapshotFlow {
            listState.firstVisibleItemIndex to listState.firstVisibleItemScrollOffset
        }.collect { (index, offset) ->
            val goingDown = index > lastIndex ||
                (index == lastIndex && offset > lastOffset + 4)
            val goingUp = index < lastIndex ||
                (index == lastIndex && offset < lastOffset - 4)
            if (goingDown) hideFabOnScroll = true
            if (goingUp || (index == 0 && offset == 0)) hideFabOnScroll = false
            lastIndex = index
            lastOffset = offset
        }
    }
    Scaffold(
        modifier = modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        containerColor = paneColor,
        topBar = {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                TopAppBar(
                    title = {
                        Text(
                            text = when {
                                selecting -> "${selectedIds.size} selected"
                                kind == ChatListKind.Archive -> "Archived"
                                else -> "OpenGarden"
                            },
                            style = MaterialTheme.typography.headlineSmall,
                            maxLines = 1,
                        )
                    },
                    navigationIcon = {
                        when {
                            selecting -> IconButton(onClick = ::clearSelection) {
                                Icon(Icons.Filled.Close, contentDescription = "Cancel selection")
                            }
                            showBackButton -> IconButton(onClick = onBack) {
                                Icon(
                                    Icons.AutoMirrored.Filled.ArrowBack,
                                    contentDescription = "Back",
                                )
                            }
                        }
                    },
                    actions = {
                        if (selecting) {
                            val archiveLabel = if (kind == ChatListKind.Archive) {
                                "Unarchive"
                            } else {
                                "Archive"
                            }
                            IconButton(
                                onClick = {
                                    val ids = selectedIds
                                    if (kind == ChatListKind.Archive) onUnarchive(ids) else onArchive(ids)
                                    clearSelection()
                                },
                            ) {
                                Icon(
                                    imageVector = if (kind == ChatListKind.Archive) {
                                        Icons.Filled.Unarchive
                                    } else {
                                        Icons.Filled.Archive
                                    },
                                    contentDescription = archiveLabel,
                                )
                            }
                            IconButton(onClick = { confirmDeleteIds = selectedIds }) {
                                Icon(Icons.Filled.Delete, contentDescription = "Delete")
                            }
                            if (kind == ChatListKind.Inbox && selectedIds.size == 1) {
                                chatById(selectedIds.single())?.let { chat ->
                                    IconButton(onClick = { actionChat = chat }) {
                                        Icon(Icons.Filled.MoreVert, contentDescription = "More actions")
                                    }
                                }
                            }
                        } else if (kind == ChatListKind.Inbox) {
                            IconButton(onClick = onOpenSearch) {
                                Icon(
                                    imageVector = Icons.Filled.Search,
                                    contentDescription = "Search",
                                )
                            }
                            Box {
                                // Overflow affordance, not a person glyph: the menu
                                // holds app destinations (Find My, Settings), not a
                                // profile page.
                                IconButton(onClick = { profileMenuExpanded = true }) {
                                    Icon(
                                        imageVector = Icons.Filled.MoreVert,
                                        contentDescription = "More options",
                                    )
                                }
                                DropdownMenu(
                                    expanded = profileMenuExpanded,
                                    onDismissRequest = { profileMenuExpanded = false },
                                ) {
                                    // Plain overload: flat full-width rows inside
                                    // the rounded popup. The contained item shapes
                                    // are for grouped/selectable menus; on two
                                    // navigation actions they read as a nested card.
                                    DropdownMenuItem(
                                        text = { Text("Find My") },
                                        leadingIcon = {
                                            Icon(Icons.Filled.LocationOn, contentDescription = null)
                                        },
                                        onClick = {
                                            profileMenuExpanded = false
                                            onOpenFindMy()
                                        },
                                    )
                                    DropdownMenuItem(
                                        text = { Text("Settings") },
                                        leadingIcon = {
                                            Icon(Icons.Filled.Settings, contentDescription = null)
                                        },
                                        onClick = {
                                            profileMenuExpanded = false
                                            onOpenSettings()
                                        },
                                    )
                                }
                            }
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = paneColor,
                        scrolledContainerColor = paneColor,
                    ),
                    scrollBehavior = scrollBehavior,
                )
            }
        },
        floatingActionButton = {
            if (kind == ChatListKind.Inbox) {
                NewChatFab(onClick = onNewChat, visible = !selecting && !hideFabOnScroll)
            }
        },
    ) { padding ->
        when {
            uiState.loading -> Column(
                modifier = Modifier.fillMaxSize().padding(padding),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                LoadingState(Modifier.fillMaxSize())
                footer()
            }

            kind == ChatListKind.Archive && uiState.archived.isEmpty() -> Column(
                modifier = Modifier.fillMaxSize().padding(padding),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                EmptyState(
                    onNewChat = {},
                    archive = true,
                    modifier = Modifier.widthIn(max = ListContentMaxWidth).fillMaxSize(),
                )
                footer()
            }

            kind == ChatListKind.Inbox && uiState.isEmpty -> Column(
                modifier = Modifier.fillMaxSize().padding(padding),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                EmptyState(
                    onNewChat = onNewChat,
                    modifier = Modifier.widthIn(max = ListContentMaxWidth).fillMaxSize(),
                )
                footer()
            }

            else -> ChatSections(
                uiState = uiState,
                kind = kind,
                listState = listState,
                contentPadding = PaddingValues(
                    top = padding.calculateTopPadding() + 4.dp,
                    bottom = padding.calculateBottomPadding() + 88.dp,
                ),
                onChatClick = { chat ->
                    if (selecting) toggleSelected(chat) else onChatClick(chat)
                },
                onChatLongClick = { chat ->
                    if (selecting) toggleSelected(chat) else selectedIds = setOf(chat.id)
                },
                selectedChatId = if (selecting) null else selectedChatId,
                checkedIds = selectedIds,
                swipeEnabled = !selecting,
                onSwipeArchive = { chat ->
                    if (kind == ChatListKind.Archive) onUnarchive(listOf(chat.id))
                    else onArchive(listOf(chat.id))
                },
                header = header,
                footer = footer,
                onVisibleChatsChanged = onVisibleChatsChanged,
            )
        }
    }

    actionChat?.let { chat ->
        ChatListActionSheet(
            chat = chat,
            onTogglePinned = {
                actionChat = null
                onTogglePinned(chat)
            },
            onToggleMuted = {
                actionChat = null
                onToggleMuted(chat)
            },
            onMuteFor = { durationMs ->
                actionChat = null
                onMuteFor(chat, durationMs)
            },
            // SIM SMS chats always send from the device number.
            onSendFrom = if (!chat.isSms && sendFromChoices.isNotEmpty()) {
                {
                    actionChat = null
                    sendFromChat = chat
                }
            } else {
                null
            },
            onDismiss = { actionChat = null },
        )
    }

    sendFromChat?.let { chat ->
        SendFromDialog(
            chat = chat,
            choices = sendFromChoices,
            defaultHandle = remember(chat) { defaultSendingHandle() },
            onPick = { handle ->
                sendFromChat = null
                onSetSendFrom(chat, handle)
                clearSelection()
            },
            onDismiss = { sendFromChat = null },
        )
    }

    confirmDeleteIds?.let { ids ->
        val titles = ids.mapNotNull { chatById(it)?.title }
        AlertDialog(
            onDismissRequest = { confirmDeleteIds = null },
            title = {
                Text(if (ids.size == 1) "Delete conversation?" else "Delete ${ids.size} conversations?")
            },
            text = {
                Text(
                    if (ids.size == 1) {
                        "This permanently removes ${titles.singleOrNull() ?: "this conversation"} and its synced history from this account."
                    } else {
                        "This permanently removes ${ids.size} conversations and their synced history from this account."
                    },
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmDeleteIds = null
                        onDelete(ids)
                        clearSelection()
                    },
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.error,
                    ),
                ) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = { confirmDeleteIds = null }) { Text("Cancel") }
            },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ChatSections(
    uiState: ChatListUiState,
    kind: ChatListKind,
    listState: LazyListState,
    contentPadding: PaddingValues,
    onChatClick: (ChatListItem) -> Unit,
    onChatLongClick: (ChatListItem) -> Unit,
    selectedChatId: Long?,
    checkedIds: Set<Long>,
    swipeEnabled: Boolean,
    onSwipeArchive: (ChatListItem) -> Unit,
    header: @Composable ColumnScope.() -> Unit,
    footer: @Composable ColumnScope.() -> Unit,
    onVisibleChatsChanged: (List<Long>) -> Unit,
) {
    val context = LocalContext.current
    val filterUnknown = remember { MessagingPrefs(context).filterUnknownSenders }
    val itemSpecs = rememberItemAnimationSpecs()
    val orderedIds = remember(uiState.pinned, uiState.chats, uiState.archived, kind, filterUnknown) {
        fun visible(chats: List<ChatListItem>) =
            if (filterUnknown) chats.filterNot { it.unknownSender } else chats
        when (kind) {
            ChatListKind.Inbox -> visible(uiState.pinned).map { it.id } + visible(uiState.chats).map { it.id }
            ChatListKind.Archive -> visible(uiState.archived).map { it.id }
        }
    }
    val pinnedChats = remember(uiState.pinned, filterUnknown) {
        if (filterUnknown) uiState.pinned.filterNot { it.unknownSender } else uiState.pinned
    }
    val pinnedIds = remember(pinnedChats) { pinnedChats.map { it.id } }

    LaunchedEffect(orderedIds) {
        onVisibleChatsChanged(visibleTranscriptPrefetchIds(orderedIds, emptyList()))
    }
    LaunchedEffect(listState, orderedIds, pinnedIds) {
        snapshotFlow {
            val visible = LinkedHashSet<Long>()
            listState.layoutInfo.visibleItemsInfo.forEach { item ->
                when (val key = item.key) {
                    "pinned-grid" -> visible += pinnedIds
                    is String -> if (key.startsWith("chat-")) {
                        key.removePrefix("chat-").toLongOrNull()?.let(visible::add)
                    }
                }
            }
            visibleTranscriptPrefetchIds(orderedIds, visible)
        }
            .distinctUntilChanged()
            .collectLatest { ids ->
                delay(64)
                onVisibleChatsChanged(ids)
            }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        state = listState,
        contentPadding = contentPadding,
        verticalArrangement = Arrangement.spacedBy(0.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        item(key = "header") {
            Column(modifier = Modifier.widthIn(max = ListContentMaxWidth).fillMaxWidth()) { header() }
        }
        if (kind == ChatListKind.Inbox && pinnedChats.isNotEmpty()) {
            item(key = "pinned-grid", contentType = "pinned-grid") {
                PinnedChatsGrid(
                    chats = pinnedChats,
                    onChatClick = onChatClick,
                    onChatLongClick = onChatLongClick,
                    selectedChatId = selectedChatId,
                    checkedIds = checkedIds,
                    modifier = Modifier
                        .widthIn(max = ListContentMaxWidth)
                        .fillMaxWidth()
                        .animateItem(
                            fadeInSpec = itemSpecs.fadeIn,
                            fadeOutSpec = itemSpecs.fadeOut,
                            placementSpec = itemSpecs.placement,
                        ),
                )
            }
        }
        val rows = (if (kind == ChatListKind.Archive) uiState.archived else uiState.chats)
            .let { chats -> if (filterUnknown) chats.filterNot { it.unknownSender } else chats }
        if (rows.isNotEmpty()) {
            items(
                items = rows,
                key = { "chat-${it.id}" },
                contentType = { "conversation" },
            ) { chat ->
                val rowModifier = Modifier.widthIn(max = ListContentMaxWidth)
                    .animateItem(
                        fadeInSpec = itemSpecs.fadeIn,
                        fadeOutSpec = itemSpecs.fadeOut,
                        placementSpec = itemSpecs.placement,
                    )
                if (!swipeEnabled) {
                    ChatListRow(
                        chat = chat,
                        onClick = onChatClick,
                        onLongClick = onChatLongClick,
                        selected = chat.id == selectedChatId || chat.id in checkedIds,
                        checked = if (checkedIds.isEmpty()) null else chat.id in checkedIds,
                        modifier = rowModifier,
                    )
                } else {
                    val dismissState = rememberSwipeToDismissBoxState(
                        confirmValueChange = { value ->
                            if (value == SwipeToDismissBoxValue.EndToStart) {
                                onSwipeArchive(chat)
                                true
                            } else {
                                false
                            }
                        },
                    )
                    SwipeToDismissBox(
                        state = dismissState,
                        backgroundContent = {
                            val archive = kind == ChatListKind.Archive
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(MaterialTheme.colorScheme.primaryContainer)
                                    .padding(horizontal = 24.dp),
                                contentAlignment = Alignment.CenterEnd,
                            ) {
                                Icon(
                                    imageVector = if (archive) {
                                        Icons.Filled.Unarchive
                                    } else {
                                        Icons.Filled.Archive
                                    },
                                    contentDescription = if (archive) "Unarchive" else "Archive",
                                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                )
                            }
                        },
                        enableDismissFromStartToEnd = false,
                        modifier = rowModifier,
                    ) {
                        ChatListRow(
                            chat = chat,
                            onClick = onChatClick,
                            onLongClick = onChatLongClick,
                            selected = chat.id == selectedChatId || chat.id in checkedIds,
                            checked = if (checkedIds.isEmpty()) null else chat.id in checkedIds,
                        )
                    }
                }
            }
        }
        item(key = "footer") {
            Column(modifier = Modifier.widthIn(max = ListContentMaxWidth).fillMaxWidth()) { footer() }
        }
    }
}

@Composable
private fun PinnedChatsGrid(
    chats: List<ChatListItem>,
    onChatClick: (ChatListItem) -> Unit,
    onChatLongClick: (ChatListItem) -> Unit,
    selectedChatId: Long?,
    checkedIds: Set<Long>,
    modifier: Modifier = Modifier,
) {
    BoxWithConstraints(modifier = modifier.padding(horizontal = 8.dp, vertical = 10.dp)) {
        val columns = pinnedChatColumnCount(maxWidth)
        val avatarSize = if (columns >= 4) 76.dp else 72.dp
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            chats.chunked(columns).forEach { rowChats ->
                Row(modifier = Modifier.fillMaxWidth()) {
                    rowChats.forEach { chat ->
                        key(chat.id) {
                            PinnedChatTile(
                                chat = chat,
                                onClick = onChatClick,
                                onLongClick = onChatLongClick,
                                selected = chat.id == selectedChatId || chat.id in checkedIds,
                                checked = if (checkedIds.isEmpty()) null else chat.id in checkedIds,
                                avatarSize = avatarSize,
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }
                    repeat(columns - rowChats.size) {
                        Spacer(Modifier.weight(1f))
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun PinnedChatTile(
    chat: ChatListItem,
    onClick: (ChatListItem) -> Unit,
    onLongClick: (ChatListItem) -> Unit,
    selected: Boolean,
    modifier: Modifier = Modifier,
    checked: Boolean? = null,
    avatarSize: androidx.compose.ui.unit.Dp,
) {
    val unread = chat.unread > 0
    val avatarPath = chat.avatarPath ?: rememberContactAvatarPath(chat.avatarAddress)
    Surface(
        shape = MaterialTheme.shapes.extraLarge,
        color = if (selected) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent,
        contentColor = if (selected) {
            MaterialTheme.colorScheme.onSecondaryContainer
        } else {
            MaterialTheme.colorScheme.onSurface
        },
        modifier = modifier
            .padding(horizontal = 2.dp)
            .sharedChatContainer(chat.id)
            .combinedClickable(
                onClick = { onClick(chat) },
                onLongClick = { onLongClick(chat) },
                onLongClickLabel = "Select conversations",
            ),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 4.dp, vertical = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box {
                ChatAvatar(
                    title = chat.title,
                    avatarColor = chat.avatarColor,
                    size = avatarSize,
                    avatarPath = avatarPath,
                )
                SelectionCheck(checked = checked, size = avatarSize)
                if (unread) {
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.primary,
                        border = BorderStroke(2.dp, MaterialTheme.colorScheme.surface),
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .size(16.dp),
                    ) {}
                }
            }
            Spacer(Modifier.height(8.dp))
            Text(
                text = chat.title,
                style = if (unread) {
                    MaterialTheme.typography.titleSmallEmphasized
                } else {
                    MaterialTheme.typography.titleSmall
                },
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** One flat, Messages-style conversation row. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ChatListRow(
    chat: ChatListItem,
    onClick: (ChatListItem) -> Unit,
    modifier: Modifier = Modifier,
    onLongClick: (ChatListItem) -> Unit = {},
    /** True when this conversation is open in the adjacent detail pane. */
    selected: Boolean = false,
    /** Null when the list is not in selection mode. */
    checked: Boolean? = null,
) {
    val unread = chat.unread > 0
    val context = LocalContext.current
    val use24Hour by AppearancePrefs.use24HourTimeFlow.collectAsState()
    val showDmAvatars = remember { MessagingPrefs(context).showAvatarsInDirectChats }
    val contactAvatarPath = rememberContactAvatarPath(chat.avatarAddress)
    val avatarPath = when {
        !chat.isGroup && !showDmAvatars -> null
        else -> chat.avatarPath ?: contactAvatarPath
    }
    val secondaryText = if (selected) {
        MaterialTheme.colorScheme.onSecondaryContainer
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }
    Surface(
        shape = if (selected) MaterialTheme.shapes.extraLarge else RectangleShape,
        color = if (selected) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent,
        modifier = modifier
            .fillMaxWidth()
            // Container transform into the conversation (compact windows only;
            // the helper no-ops in multi-pane and outside SharedTransitionLayout).
            .sharedChatContainer(chat.id)
            .combinedClickable(
                onClick = { onClick(chat) },
                onLongClick = { onLongClick(chat) },
                onLongClickLabel = "Select conversations",
            ),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box {
                ChatAvatar(
                    title = chat.title,
                    avatarColor = chat.avatarColor,
                    size = 56.dp,
                    avatarPath = avatarPath,
                )
                SelectionCheck(checked = checked, size = 56.dp)
            }
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
                            tint = secondaryText,
                            modifier = Modifier
                                .padding(start = 6.dp)
                                .size(14.dp),
                        )
                    }
                    Text(
                        text = formatListTimestamp(chat.date, use24Hour = use24Hour),
                        style = MaterialTheme.typography.labelMedium,
                        color = secondaryText,
                        modifier = Modifier.padding(start = 8.dp),
                    )
                }
                Spacer(Modifier.height(2.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = chat.snippet.orEmpty(),
                        style = if (unread) {
                            MaterialTheme.typography.bodyMediumEmphasized
                        } else {
                            MaterialTheme.typography.bodyMedium
                        },
                        color = when {
                            selected -> MaterialTheme.colorScheme.onSecondaryContainer
                            unread -> MaterialTheme.colorScheme.onSurface
                            else -> MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    if (chat.muted) {
                        Icon(
                            imageVector = Icons.Filled.NotificationsOff,
                            contentDescription = "Muted",
                            tint = secondaryText,
                            modifier = Modifier.padding(start = 8.dp).size(17.dp),
                        )
                    }
                    if (unread) {
                        UnreadBadge(
                            count = chat.unread,
                            modifier = Modifier.padding(start = 8.dp),
                        )
                    }
                }
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
    /** Null hides the send-from action (SIM chats, no registered handles). */
    onSendFrom: (() -> Unit)?,
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
        if (onSendFrom != null) {
            ChatActionButton(
                text = chat.senderOverride
                    ?.let { "Send from ${handleLabel(it)}" }
                    ?: "Send from…",
                icon = Icons.AutoMirrored.Filled.Send,
                onClick = onSendFrom,
            )
        }
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
    }
}

/**
 * Per-chat send-from override picker. The default option follows the global
 * "Default sending address" setting; picking a handle pins this conversation
 * to it regardless of the default or the address the chat was received on.
 * Internal for screenshot coverage.
 */
@Composable
internal fun SendFromDialog(
    chat: ChatListItem,
    choices: List<String>,
    defaultHandle: String?,
    onPick: (String?) -> Unit,
    onDismiss: () -> Unit,
) {
    val optionCount = choices.size + 1
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Send from") },
        text = {
            Column(
                modifier = Modifier
                    .heightIn(max = 420.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    "Choose the address messages in this conversation are sent from. " +
                        "Sending from a different address can appear as a new " +
                        "conversation to the other people in it.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                SettingsGroup {
                    SettingsChoiceItem(
                        title = "App default",
                        supporting = defaultHandle?.let { "Currently ${handleLabel(it)}" }
                            ?: "Automatic — follows the default sending address setting",
                        selected = chat.senderOverride == null,
                        onClick = { onPick(null) },
                        index = 0,
                        count = optionCount,
                    )
                    choices.forEachIndexed { index, handle ->
                        SettingsChoiceItem(
                            title = handleLabel(handle),
                            supporting = handleSupporting(handle, chat.receivedOnHandle),
                            selected = sameHandle(chat.senderOverride, handle),
                            onClick = { onPick(handle) },
                            index = index + 1,
                            count = optionCount,
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}

private fun handleLabel(handle: String): String = handle.substringAfter(':', handle)

private fun handleSupporting(handle: String, receivedOnHandle: String?): String {
    val type = when {
        handle.startsWith("tel:") -> "Phone number"
        handle.startsWith("mailto:") -> "Email address"
        else -> "Registered address"
    }
    return if (sameHandle(handle, receivedOnHandle)) {
        "$type · This conversation was received here"
    } else {
        type
    }
}

/** Compares handles ignoring the rust `tel:` / `mailto:` prefix and case. */
private fun sameHandle(a: String?, b: String?): Boolean =
    a != null && b != null &&
        a.substringAfter(':', a).equals(b.substringAfter(':', b), ignoreCase = true)

/** Registered handles ordered like the settings picker: phones first, then labels. */
internal fun sendFromChoices(handles: Set<String>): List<String> =
    handles.sortedWith(
        compareBy(
            { if (it.startsWith("tel:")) 0 else 1 },
            { handleLabel(it).lowercase() },
        ),
    )

@Composable
private fun SelectionCheck(checked: Boolean?, size: androidx.compose.ui.unit.Dp) {
    if (checked != true) return
    Box(
        modifier = Modifier
            .size(size)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.72f)),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = Icons.Filled.Check,
            contentDescription = "Selected",
            tint = MaterialTheme.colorScheme.onPrimary,
        )
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
        shape = CircleShape,
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

/**
 * The new-chat action: a Medium FAB in the Expressive cookie shape that
 * morphs into a circle while pressed, and scales/fades out of the corner
 * when multi-select takes over the list. The polygon morph is the screen's
 * one hero shape — everything else holds the rounded-rect baseline.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun NewChatFab(
    onClick: () -> Unit,
    visible: Boolean,
    modifier: Modifier = Modifier,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val morphProgress by animateFloatAsState(
        targetValue = if (pressed) 1f else 0f,
        animationSpec = fastSpatialSpec(),
        label = "newChatFabMorph",
    )
    MediumFloatingActionButton(
        onClick = onClick,
        shape = rememberPolygonMorph(MaterialShapes.Cookie9Sided, MaterialShapes.Circle, morphProgress),
        interactionSource = interactionSource,
        modifier = modifier.animateFloatingActionButton(
            visible = visible,
            alignment = Alignment.BottomEnd,
        ),
    ) {
        Icon(
            imageVector = Icons.Filled.Edit,
            contentDescription = "New chat",
            modifier = Modifier.size(FloatingActionButtonDefaults.MediumIconSize),
        )
    }
}

@Composable
private fun LoadingState(modifier: Modifier = Modifier) {
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        LoadingIndicator()
    }
}

/** Branded, actionable empty state pointing at the new-chat FAB. */
@Composable
private fun EmptyState(
    onNewChat: () -> Unit,
    modifier: Modifier = Modifier,
    archive: Boolean = false,
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
                    imageVector = if (archive) Icons.Filled.Archive else Icons.Filled.ChatBubble,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(32.dp),
                )
            }
        }
        Spacer(Modifier.height(16.dp))
        Text(
            text = if (archive) "No archived conversations" else "No conversations yet",
            style = MaterialTheme.typography.titleLargeEmphasized,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = if (archive) {
                "Long-press conversations on the main list to archive them. They stay on this account until you delete them."
            } else {
                "Messages you send and receive will show up here."
            },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )
        if (!archive) {
            Spacer(Modifier.height(20.dp))
            Button(onClick = onNewChat, shapes = ButtonDefaults.shapes()) {
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

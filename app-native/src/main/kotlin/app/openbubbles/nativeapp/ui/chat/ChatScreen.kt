package app.openbubbles.nativeapp.ui.chat

import android.net.Uri
import android.content.res.Configuration
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.ChatBubble
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import app.openbubbles.nativeapp.data.AttachmentMeta
import app.openbubbles.nativeapp.data.ChatListItem
import app.openbubbles.nativeapp.data.MessageItem
import app.openbubbles.nativeapp.data.MessageStatus
import app.openbubbles.nativeapp.data.OutgoingAttachment
import app.openbubbles.nativeapp.data.UiContacts
import app.openbubbles.nativeapp.ui.common.ChatAvatar
import app.openbubbles.nativeapp.ui.common.formatConversationDay
import app.openbubbles.nativeapp.ui.common.localDay
import app.openbubbles.nativeapp.ui.common.rememberContactAvatarPath
import app.openbubbles.nativeapp.ui.effects.PendingEffectChip
import app.openbubbles.nativeapp.ui.effects.SendEffectCatalog
import app.openbubbles.nativeapp.ui.effects.SendEffectOverlay
import app.openbubbles.nativeapp.ui.effects.SendEffectOption
import app.openbubbles.nativeapp.ui.effects.SendEffectPickerSheet
import app.openbubbles.nativeapp.ui.theme.OpenBubblesTheme
import java.io.File
import java.time.ZoneId
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** List model for the conversation LazyColumn. */
sealed interface ConversationEntry {
    val key: String

    data class DaySeparator(val epochMillis: Long) : ConversationEntry {
        override val key: String = "day-$epochMillis"
    }

    data class Message(
        val message: MessageItem,
        val showStatus: Boolean,
        /**
         * The message directly above (older) is from the same author — tighten
         * the bubble's top corners so runs read as one group.
         */
        val tightTop: Boolean = false,
        /** The message directly below (newer) is from the same author. */
        val tightBottom: Boolean = false,
        /** First visible message of an author's run in a group chat. */
        val showSenderName: Boolean = false,
    ) : ConversationEntry {
        override val key: String = "message-${message.id}"
    }
}

/** Bubble-author identity for grouping (mine vs. a specific sender). */
private fun MessageItem.authorKey(): Pair<Boolean, String?> = isFromMe to senderAddress

/** True when this message renders as a bubble (rows like group events break runs). */
private fun MessageItem.rendersAsBubble(): Boolean = !isGroupEvent && !unsent

/**
 * Builds newest-first entries (the reversed list renders index 0 at the
 * bottom) with day separators between calendar days, grouping-aware corner
 * hints, optional group sender-name labels, and the status row on my newest
 * outgoing message (or any failed one).
 */
fun buildConversationEntries(
    messages: List<MessageItem>,
    zone: ZoneId = ZoneId.systemDefault(),
    showSenderNames: Boolean = false,
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

    // Second pass: grouping corners + sender names from visual neighbors.
    // Index 0 is the visual bottom, so the message "above" entry i is i+1.
    for (i in entries.indices) {
        val entry = entries[i] as? ConversationEntry.Message ?: continue
        val message = entry.message
        if (!message.rendersAsBubble()) continue

        val above = entries.getOrNull(i + 1) as? ConversationEntry.Message
        val below = entries.getOrNull(i - 1) as? ConversationEntry.Message
        val tightTop = above != null &&
            above.message.rendersAsBubble() &&
            above.message.authorKey() == message.authorKey()
        val tightBottom = below != null &&
            below.message.rendersAsBubble() &&
            below.message.authorKey() == message.authorKey()
        val showName = showSenderNames &&
            !message.isFromMe &&
            message.senderAddress != null &&
            !tightTop
        entries[i] = entry.copy(
            tightTop = tightTop,
            tightBottom = tightBottom,
            showSenderName = showName,
        )
    }
    return entries
}

/**
 * Conversation view: reversed LazyColumn (newest at the bottom, stays pinned
 * while sending), day separators, bubbles with attachments, edited/unsent
 * rendering, reactions and delivery status, older-history paging when
 * scrolled to the top, an animated typing indicator, attachment sending via
 * the system photo picker (long-press the paperclip for any file), and an
 * IME-aware input bar.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    uiState: ChatUiState,
    onInputChange: (String) -> Unit,
    onSend: () -> Unit,
    onLoadOlder: () -> Unit,
    onBack: () -> Unit,
    onSendAttachment: (OutgoingAttachment) -> Unit = {},
    onReply: (MessageItem) -> Unit = {},
    onEdit: (MessageItem) -> Unit = {},
    onReact: (MessageItem, Int, String?) -> Unit = { _, _, _ -> },
    onUnsend: (MessageItem) -> Unit = {},
    onCancelComposerAction: () -> Unit = {},
    onActionErrorShown: () -> Unit = {},
    onOpenChatInfo: () -> Unit = {},
    onOpenAttachment: (String) -> Unit = {},
    onDownloadAttachment: (AttachmentMeta) -> Unit = {},
    attachmentFile: (String) -> File? = { null },
    modifier: Modifier = Modifier,
) {
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    var selectedMessage by remember { mutableStateOf<MessageItem?>(null) }
    var confirmUnsend by remember { mutableStateOf<MessageItem?>(null) }

    // Group chats (two or more distinct other senders in the transcript)
    // label incoming runs with the sender's name.
    val isGroupChat = remember(uiState.messages) {
        uiState.messages.asSequence()
            .mapNotNull { it.senderAddress }
            .distinct()
            .count() >= 2
    }
    val entries = remember(uiState.messages, isGroupChat) {
        buildConversationEntries(uiState.messages, showSenderNames = isGroupChat)
    }
    val messagesByGuid = remember(uiState.messages) { uiState.messages.associateBy { it.guid } }
    val isTyping = uiState.typingSenders.isNotEmpty()

    // ---- Send screen effects -------------------------------------------------
    // The ViewModel flags the newest unplayed effect; the overlay plays ~700ms
    // after the message renders (matches the Dart send-animation delay).
    var activeEffect by remember { mutableStateOf<ScreenEffectTrigger?>(null) }
    LaunchedEffect(uiState.screenEffect) {
        activeEffect = null
        val trigger = uiState.screenEffect ?: return@LaunchedEffect
        delay(700)
        activeEffect = trigger
    }

    LaunchedEffect(uiState.actionError) {
        val error = uiState.actionError ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(error)
        onActionErrorShown()
    }

    // Pending effect staged from the picker for the next send (id only, so it
    // survives recomposition via rememberSaveable).
    var pendingEffectId by rememberSaveable { mutableStateOf<String?>(null) }
    val pendingOption: SendEffectOption? = pendingEffectId?.let(SendEffectCatalog::byId)
    var showEffectPicker by remember { mutableStateOf(false) }

    fun stagePendingEffect(option: SendEffectOption?) {
        pendingEffectId = option?.id
        PendingSendEffect.effectId = option?.id
    }

    fun dispatchAttachment(uri: Uri?) {
        if (uri == null) return
        scope.launch {
            val prepared = prepareOutgoingAttachment(context, uri) ?: return@launch
            onSendAttachment(prepared)
            listState.animateScrollToItem(0)
        }
    }

    // System photo picker for images/videos; GetContent for any file.
    val pickMedia = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia(),
    ) { uri -> dispatchAttachment(uri) }
    val pickFile = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent(),
    ) { uri -> dispatchAttachment(uri) }

    // Contact names for group sender labels and "<name> unsent a message"
    // rows (best effort).
    val senderNames = remember { mutableStateMapOf<String, String>() }
    LaunchedEffect(uiState.messages) {
        val resolver = UiContacts.contactNames ?: return@LaunchedEffect
        val addresses = uiState.messages
            .filter { !it.isFromMe && it.senderAddress != null }
            .mapNotNull { it.senderAddress }
            .distinct()
        addresses.forEach { address ->
            val name = resolver(address)?.first ?: return@forEach
            senderNames[address] = name
        }
    }

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

    Box(modifier = modifier) {
        Scaffold(
            modifier = Modifier.fillMaxSize(),
            contentWindowInsets = WindowInsets(0, 0, 0, 0),
            snackbarHost = { SnackbarHost(snackbarHostState) },
            topBar = {
                TopAppBar(
                    title = {
                        ChatHeader(
                            chat = uiState.chat,
                            modifier = Modifier.clickable(onClick = onOpenChatInfo),
                        )
                    },
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
                        stagePendingEffect(null)
                        scope.launch { listState.animateScrollToItem(0) }
                    },
                    onAttachClick = {
                        pickMedia.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo),
                        )
                    },
                    onAttachLongClick = { pickFile.launch("*/*") },
                    pendingEffect = pendingOption,
                    onClearPendingEffect = { stagePendingEffect(null) },
                    onSendLongClick = { showEffectPicker = true },
                    composerActionLabel = when {
                        uiState.editingMessage != null -> "Editing message"
                        uiState.replyingTo != null -> "Replying"
                        else -> null
                    },
                    composerActionText = uiState.editingMessage?.text ?: uiState.replyingTo?.let { message ->
                        message.text.ifBlank { message.attachmentMeta?.name ?: "Attachment" }
                    },
                    onClearComposerAction = onCancelComposerAction,
                )
            },
        ) { padding ->
            Box(modifier = Modifier.padding(padding)) {
                when {
                    uiState.initialLoading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                    uiState.messages.isEmpty() && !isTyping ->
                        ChatEmptyState(Modifier.fillMaxSize())
                    else -> LazyColumn(
                        state = listState,
                        reverseLayout = true,
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(vertical = 8.dp),
                    ) {
                        if (isTyping) {
                            item(key = "typing-indicator") {
                                TypingIndicatorRow(senderAddress = uiState.typingSenders.first())
                            }
                        }
                        items(entries, key = { it.key }) { entry ->
                            when (entry) {
                                is ConversationEntry.Message -> MessageBubble(
                                    message = entry.message,
                                    showStatus = entry.showStatus,
                                    tightTop = entry.tightTop,
                                    tightBottom = entry.tightBottom,
                                    showSenderName = entry.showSenderName,
                                    attachmentFile = attachmentFile,
                                    onOpenAttachment = onOpenAttachment,
                                    onDownloadAttachment = onDownloadAttachment,
                                    senderDisplayName = entry.message.senderAddress?.let { senderNames[it] },
                                    replyPreview = entry.message.replyToGuid?.let { guid ->
                                        messagesByGuid[guid]?.let { target ->
                                            target.text.ifBlank { target.attachmentMeta?.name ?: "Attachment" }
                                        }
                                    },
                                    onLongPress = if (entry.message.status == MessageStatus.SENDING) {
                                        null
                                    } else {
                                        { selectedMessage = entry.message }
                                    },
                                )
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

        // Full-screen send-effect overlay above everything.
        activeEffect?.let { trigger ->
            SendEffectOverlay(
                effectId = trigger.effectId,
                modifier = Modifier.fillMaxSize(),
                onFinished = { activeEffect = null },
            )
        }
    }

    // Effect picker sheet (long-press the send button).
    if (showEffectPicker) {
        SendEffectPickerSheet(
            onPick = { option ->
                stagePendingEffect(option)
                showEffectPicker = false
            },
            onDismiss = { showEffectPicker = false },
        )
    }

    selectedMessage?.let { message ->
        MessageActionSheet(
            message = message,
            isSms = uiState.chat?.isSms == true,
            onReact = { index, emoji ->
                selectedMessage = null
                onReact(message, index, emoji)
            },
            onReply = {
                selectedMessage = null
                onReply(message)
            },
            onEdit = {
                selectedMessage = null
                onEdit(message)
            },
            onUnsend = {
                selectedMessage = null
                confirmUnsend = message
            },
            onDismiss = { selectedMessage = null },
        )
    }

    confirmUnsend?.let { message ->
        AlertDialog(
            onDismissRequest = { confirmUnsend = null },
            title = { Text("Unsend message?") },
            text = { Text("This removes the message for everyone in the conversation.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmUnsend = null
                        onUnsend(message)
                    },
                ) { Text("Unsend") }
            },
            dismissButton = {
                TextButton(onClick = { confirmUnsend = null }) { Text("Cancel") }
            },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MessageActionSheet(
    message: MessageItem,
    isSms: Boolean,
    onReact: (Int, String?) -> Unit,
    onReply: () -> Unit,
    onEdit: () -> Unit,
    onUnsend: () -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        if (!isSms) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
            ) {
                listOf("❤️", "👍", "👎", "😂", "‼️", "❓").forEachIndexed { index, emoji ->
                    Text(
                        text = emoji,
                        style = MaterialTheme.typography.headlineSmall,
                        modifier = Modifier
                            .clip(CircleShape)
                            .clickable { onReact(index, null) }
                            .padding(8.dp),
                    )
                }
            }
        }
        TextButton(
            onClick = onReply,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp),
        ) { Text("Reply", modifier = Modifier.fillMaxWidth()) }
        if (!isSms && message.isFromMe && message.text.isNotBlank() && !message.unsent) {
            TextButton(
                onClick = onEdit,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp),
            ) { Text("Edit", modifier = Modifier.fillMaxWidth()) }
            TextButton(
                onClick = onUnsend,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp),
            ) { Text("Unsend", modifier = Modifier.fillMaxWidth()) }
        }
    }
}

@Composable
private fun ChatHeader(chat: ChatListItem?, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        if (chat != null) {
            ChatAvatar(
                title = chat.title,
                avatarColor = chat.avatarColor,
                size = 34.dp,
                avatarPath = rememberContactAvatarPath(chat.avatarAddress),
            )
            Text(
                text = chat.title,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false),
            )
            Icon(
                imageVector = Icons.Filled.ExpandMore,
                contentDescription = "Conversation details",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp),
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

/** Friendly placeholder before the first message of a conversation. */
@Composable
private fun ChatEmptyState(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.padding(horizontal = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            modifier = Modifier.size(64.dp),
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = Icons.Filled.ChatBubble,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(28.dp),
                )
            }
        }
        Text(
            text = "No messages yet",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(top = 14.dp),
        )
        Text(
            text = "Say hi — everything stays in sync with iMessage.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 4.dp),
        )
    }
}

/**
 * Animated "•••" bubble shown at the bottom of the transcript while a
 * participant is typing (dots fade in sequence via an infinite transition).
 */
@Composable
fun TypingIndicatorRow(senderAddress: String?, modifier: Modifier = Modifier) {
    val name = senderAddress?.let { address ->
        produceState<String?>(null, address) {
            value = runCatching { UiContacts.contactNames?.invoke(address)?.first }.getOrNull()
        }.value
    }
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp),
    ) {
        Column {
            if (name != null) {
                Text(
                    text = "$name is typing",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 8.dp, bottom = 2.dp),
                )
            }
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.surfaceVariant,
            ) {
                TypingDots(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp))
            }
        }
    }
}

@Composable
private fun TypingDots(modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "typing")
    Row(modifier = modifier, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
        repeat(3) { index ->
            val alpha by transition.animateFloat(
                initialValue = 0.25f,
                targetValue = 1f,
                animationSpec = infiniteRepeatable(
                    animation = tween(durationMillis = 450, easing = LinearEasing),
                    repeatMode = RepeatMode.Reverse,
                    initialStartOffset = androidx.compose.animation.core.StartOffset(index * 220),
                ),
                label = "dot-$index",
            )
            val scale by transition.animateFloat(
                initialValue = 0.8f,
                targetValue = 1f,
                animationSpec = infiniteRepeatable(
                    animation = tween(durationMillis = 450, easing = LinearEasing),
                    repeatMode = RepeatMode.Reverse,
                    initialStartOffset = androidx.compose.animation.core.StartOffset(index * 220),
                ),
                label = "dot-scale-$index",
            )
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .graphicsLayer(scaleX = scale, scaleY = scale)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = alpha)),
            )
        }
    }
}

// --------------------------------------------------------------------- previews

@Preview(showBackground = true)
@Preview(showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun TypingIndicatorPreview() {
    OpenBubblesTheme {
        Column {
            TypingIndicatorRow(senderAddress = "emma@icloud.com")
            TypingIndicatorRow(senderAddress = null)
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun MessageInputBarPreview() {
    OpenBubblesTheme {
        Column {
            MessageInputBar(
                value = "",
                onValueChange = {},
                onSend = {},
                onAttachClick = {},
                onAttachLongClick = {},
            )
            MessageInputBar(
                value = "see you at the trailhead!",
                onValueChange = {},
                onSend = {},
                onAttachClick = {},
                onAttachLongClick = {},
            )
        }
    }
}

/**
 * iMessage-style capsule input bar: attachment icon + growing text field +
 * a circular send button that morphs color/scale when text is present.
 * Long-press the send button for the send-effect picker; long-press the
 * paperclip to attach any file.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun MessageInputBar(
    value: String,
    onValueChange: (String) -> Unit,
    onSend: () -> Unit,
    onAttachClick: () -> Unit,
    onAttachLongClick: () -> Unit,
    modifier: Modifier = Modifier,
    pendingEffect: SendEffectOption? = null,
    onClearPendingEffect: () -> Unit = {},
    onSendLongClick: () -> Unit = {},
    composerActionLabel: String? = null,
    composerActionText: String? = null,
    onClearComposerAction: () -> Unit = {},
) {
    val hasText = value.isNotBlank()

    // Send button morph: blue when there's text to send, muted otherwise.
    val sendContainer by animateColorAsState(
        targetValue = if (hasText) {
            MaterialTheme.colorScheme.primary
        } else {
            MaterialTheme.colorScheme.surfaceContainerHighest
        },
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "sendContainer",
    )
    val sendContent by animateColorAsState(
        targetValue = if (hasText) {
            MaterialTheme.colorScheme.onPrimary
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant
        },
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "sendContent",
    )
    val sendScale by animateFloatAsState(
        targetValue = if (hasText) 1f else 0.88f,
        animationSpec = spring(
            stiffness = Spring.StiffnessMediumLow,
            dampingRatio = Spring.DampingRatioMediumBouncy,
        ),
        label = "sendScale",
    )

    Column(
        modifier = modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .imePadding(),
    ) {
        AnimatedVisibility(
            visible = composerActionLabel != null,
            enter = expandVertically() + fadeIn(),
            exit = shrinkVertically() + fadeOut(),
        ) {
            if (composerActionLabel != null) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = composerActionLabel,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary,
                        )
                        composerActionText?.let {
                            Text(
                                text = it,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                    TextButton(onClick = onClearComposerAction) { Text("Cancel") }
                }
            }
        }
        // Pending send-effect chip staged from the picker.
        AnimatedVisibility(
            visible = pendingEffect != null,
            enter = expandVertically() + fadeIn(),
            exit = shrinkVertically() + fadeOut(),
        ) {
            pendingEffect?.let { option ->
                Row(
                    modifier = Modifier.padding(start = 16.dp, top = 4.dp, bottom = 2.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    PendingEffectChip(option = option, onClear = onClearPendingEffect)
                }
            }
        }
        Surface(
            shape = RoundedCornerShape(28.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
        ) {
            Row(
                modifier = Modifier.padding(start = 4.dp, end = 6.dp, top = 4.dp, bottom = 4.dp),
                verticalAlignment = Alignment.Bottom,
            ) {
                Icon(
                    imageVector = Icons.Filled.AttachFile,
                    contentDescription = "Attach photo or video (long-press for any file)",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .padding(start = 8.dp, end = 4.dp, bottom = 12.dp)
                        .size(26.dp)
                        .combinedClickable(onClick = onAttachClick, onLongClick = onAttachLongClick),
                )
                BasicTextField(
                    value = value,
                    onValueChange = onValueChange,
                    modifier = Modifier.weight(1f),
                    textStyle = MaterialTheme.typography.bodyLarge.copy(
                        color = MaterialTheme.colorScheme.onSurface,
                    ),
                    cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                    maxLines = 5,
                    keyboardOptions = KeyboardOptions(
                        imeAction = ImeAction.Send,
                        capitalization = KeyboardCapitalization.Sentences,
                    ),
                    keyboardActions = KeyboardActions(onSend = { onSend() }),
                    decorationBox = { innerTextField ->
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 2.dp, vertical = 12.dp),
                        ) {
                            if (value.isEmpty()) {
                                Text(
                                    text = "Message",
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            innerTextField()
                        }
                    },
                )
                // Long-press opens the send-effect picker (iMessage behavior).
                Box(
                    modifier = Modifier
                        .padding(start = 4.dp, top = 4.dp, bottom = 4.dp)
                        .size(40.dp)
                        .graphicsLayer(scaleX = sendScale, scaleY = sendScale)
                        .clip(CircleShape)
                        .background(sendContainer)
                        .pointerInput(hasText) {
                            detectTapGestures(
                                onTap = { if (hasText) onSend() },
                                onLongPress = { onSendLongClick() },
                            )
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Filled.ArrowUpward,
                        contentDescription = "Send (long-press to pick an effect)",
                        tint = sendContent,
                        modifier = Modifier.size(22.dp),
                    )
                }
            }
        }
    }
}

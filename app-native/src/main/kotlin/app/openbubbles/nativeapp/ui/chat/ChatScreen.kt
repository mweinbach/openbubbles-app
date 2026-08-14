package app.openbubbles.nativeapp.ui.chat

import android.net.Uri
import android.content.res.Configuration
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
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
    onOpenChatInfo: () -> Unit = {},
    onOpenAttachment: (String) -> Unit = {},
    onDownloadAttachment: (AttachmentMeta) -> Unit = {},
    attachmentFile: (String) -> File? = { null },
    modifier: Modifier = Modifier,
) {
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val entries = remember(uiState.messages) { buildConversationEntries(uiState.messages) }
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

    // Contact names for "<name> unsent a message" rows (best effort).
    val senderNames = remember { mutableStateMapOf<String, String>() }
    LaunchedEffect(uiState.messages) {
        val resolver = UiContacts.contactNames ?: return@LaunchedEffect
        val addresses = uiState.messages
            .filter { it.unsent && !it.isFromMe && it.senderAddress != null }
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
                )
            },
        ) { padding ->
            Box(modifier = Modifier.padding(padding)) {
                when {
                    uiState.initialLoading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                    uiState.messages.isEmpty() && !isTyping ->
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
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
                                    attachmentFile = attachmentFile,
                                    onOpenAttachment = onOpenAttachment,
                                    onDownloadAttachment = onDownloadAttachment,
                                    senderDisplayName = entry.message.senderAddress?.let { senderNames[it] },
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
                    modifier = Modifier.padding(start = 6.dp, bottom = 2.dp),
                )
            }
            Surface(
                shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp, bottomEnd = 20.dp, bottomStart = 16.dp),
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
            Box(
                modifier = Modifier
                    .size(8.dp)
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
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .imePadding(),
    ) {
        // Pending send-effect chip staged from the picker.
        pendingEffect?.let { option ->
            Row(
                modifier = Modifier.padding(start = 16.dp, top = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                PendingEffectChip(option = option, onClear = onClearPendingEffect)
            }
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(
                imageVector = Icons.Filled.AttachFile,
                contentDescription = "Attach photo or video (long-press for any file)",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .padding(bottom = 12.dp)
                    .size(26.dp)
                    .combinedClickable(onClick = onAttachClick, onLongClick = onAttachLongClick),
            )
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
            // Long-press opens the send-effect picker (iMessage behavior).
            FilledIconButton(
                onClick = onSend,
                enabled = value.isNotBlank(),
                shape = CircleShape,
                modifier = Modifier.pointerInput(Unit) {
                    detectTapGestures(onLongPress = { onSendLongClick() })
                },
            ) {
                Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Send")
            }
        }
    }
}

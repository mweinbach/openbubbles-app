package app.openbubbles.nativeapp.ui.chat

import android.content.Intent
import android.net.Uri
import android.content.res.Configuration
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.InsertDriveFile
import androidx.compose.material.icons.filled.AddReaction
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.ChatBubble
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.VideoCall
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetValue
import androidx.compose.material3.rememberBottomSheetState
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.IntOffset
import app.openbubbles.nativeapp.data.AttachmentMeta
import app.openbubbles.nativeapp.data.ChatListItem
import app.openbubbles.nativeapp.data.MessageItem
import app.openbubbles.nativeapp.data.MessageStatus
import app.openbubbles.nativeapp.data.OutgoingAttachment
import app.openbubbles.nativeapp.data.StickerTransform
import app.openbubbles.nativeapp.data.UiContacts
import app.openbubbles.nativeapp.data.effectiveBackgroundPath
import app.openbubbles.nativeapp.facetime.FaceTimeActivity
import app.openbubbles.nativeapp.ui.common.ChatAvatar
import app.openbubbles.nativeapp.ui.common.formatConversationDay
import app.openbubbles.nativeapp.ui.common.localDay
import app.openbubbles.nativeapp.ui.common.rememberContactAvatarPath
import app.openbubbles.nativeapp.ui.common.rememberDecodedImage
import app.openbubbles.nativeapp.ui.common.sharedChatContainer
import app.openbubbles.nativeapp.ui.effects.PendingEffectChip
import app.openbubbles.nativeapp.ui.effects.SendEffectCatalog
import app.openbubbles.nativeapp.ui.effects.SendEffectOverlay
import app.openbubbles.nativeapp.ui.effects.SendEffectOption
import app.openbubbles.nativeapp.ui.effects.SendEffectPickerSheet
import app.openbubbles.nativeapp.ui.theme.LocalReduceMotion
import app.openbubbles.nativeapp.ui.theme.OpenBubblesTheme
import app.openbubbles.nativeapp.ui.theme.defaultEffectsSpec
import app.openbubbles.nativeapp.ui.theme.defaultSpatialSpec
import app.openbubbles.nativeapp.ui.theme.fastEffectsSpec
import app.openbubbles.nativeapp.ui.theme.fastSpatialSpec
import app.openbubbles.nativeapp.ui.theme.rememberItemAnimationSpecs
import app.openbubbles.nativeapp.ui.theme.smsServiceColors
import java.io.File
import java.time.ZoneId
import kotlin.math.PI
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private val ConversationContentMaxWidth = 840.dp

/** Per-picking cap for the multi-select system photo picker. */
private const val PhotoPickerMaxItems = 10

/** iMessage tapback set, in the order the protocol indexes them. */
private val Tapbacks = listOf("❤️", "👍", "👎", "😂", "‼️", "❓")
private val CustomReactionSuggestions = listOf("🔥", "🎉", "🥰", "😮", "💯")

/** Accept one emoji grapheme, including flags, skin tones, and ZWJ families. */
internal fun normalizeCustomReaction(raw: String): String? {
    val value = raw.trim()
    if (value.isEmpty() || value.any(Char::isWhitespace)) return null
    val codePoints = value.codePoints().toArray()
    if (codePoints.isEmpty() || codePoints.size > 16) return null
    val regional = codePoints.all { it in 0x1F1E6..0x1F1FF }
    if (regional) return value.takeIf { codePoints.size == 2 }

    var bases = 0
    var expectingBase = true
    var joined = false
    val hasKeycap = codePoints.any { it == 0x20E3 }
    codePoints.forEach { codePoint ->
        when {
            codePoint == 0x200D -> {
                if (expectingBase || bases == 0) return null
                expectingBase = true
                joined = true
            }
            codePoint == 0xFE0E || codePoint == 0xFE0F ||
                codePoint in 0x1F3FB..0x1F3FF ||
                codePoint in 0xE0020..0xE007F ||
                codePoint == 0x20E3 -> {
                if (expectingBase || bases == 0) return null
            }
            else -> {
                val isEmojiBase = codePoint in 0x1F000..0x1FAFF ||
                    codePoint in 0x2600..0x27BF ||
                    codePoint in 0x2190..0x23FF ||
                    codePoint == 0x00A9 || codePoint == 0x00AE || codePoint == 0x2122 ||
                    (hasKeycap && (codePoint == '#'.code || codePoint == '*'.code ||
                        codePoint in '0'.code..'9'.code))
                if (!isEmojiBase || (!expectingBase && !joined)) return null
                bases += 1
                expectingBase = false
                joined = false
            }
        }
    }
    return value.takeIf { !expectingBase && bases >= 1 }
}

private data class SelectedMessageAction(val message: MessageItem, val part: Long)

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
 * scrolled to the top, an animated typing indicator, draft attachments via
 * the system photo picker (multi-select; long-press the paperclip for any
 * file) that ride the next send, and an IME-aware input bar.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    uiState: ChatUiState,
    onInputChange: (String) -> Unit,
    onSend: () -> Unit,
    onLoadOlder: () -> Unit,
    onBack: () -> Unit,
    /**
     * False when this conversation renders as the detail pane beside its own
     * list: there is nothing to navigate back to, and Material specifies that a
     * detail pane in a list-detail layout does not show a back arrow.
     */
    showBackButton: Boolean = true,
    onSendAttachment: (OutgoingAttachment) -> Unit = {},
    onReply: (MessageItem, Long) -> Unit = { _, _ -> },
    onOpenReplyThread: (MessageItem) -> Unit = {},
    onCloseReplyThread: () -> Unit = {},
    onReplyFromThread: (MessageItem, Long) -> Unit = { _, _ -> },
    onSendSticker: (MessageItem, Long, OutgoingAttachment, StickerTransform) -> Unit = { _, _, _, _ -> },
    onEdit: (MessageItem) -> Unit = {},
    onReact: (MessageItem, Long, Int, String?) -> Unit = { _, _, _, _ -> },
    onUnsend: (MessageItem) -> Unit = {},
    onCancelComposerAction: () -> Unit = {},
    onActionErrorShown: () -> Unit = {},
    onStartFaceTime: () -> Unit = {},
    onFaceTimeLaunchConsumed: () -> Unit = {},
    onScreenEffectConsumed: (Long) -> Unit = {},
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
    var selectedAction by remember { mutableStateOf<SelectedMessageAction?>(null) }
    var confirmUnsend by remember { mutableStateOf<MessageItem?>(null) }
    var stickerTarget by remember { mutableStateOf<SelectedMessageAction?>(null) }
    var pendingSticker by remember { mutableStateOf<OutgoingAttachment?>(null) }

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
    val itemSpecs = rememberItemAnimationSpecs()
    val smsChat = uiState.chat?.isSms == true
    val openThread = uiState.replyThread
    BackHandler(enabled = openThread != null) { onCloseReplyThread() }

    // ---- Send screen effects -------------------------------------------------
    // The ViewModel flags the newest unplayed effect; the overlay plays ~700ms
    // after the message renders (matches the Dart send-animation delay). Users
    // who removed animations at the OS level never get the full-screen storm.
    val reduceMotion = LocalReduceMotion.current
    var activeEffect by remember { mutableStateOf<ScreenEffectTrigger?>(null) }
    val effectScope = rememberCoroutineScope()
    var effectJob by remember { mutableStateOf<kotlinx.coroutines.Job?>(null) }
    LaunchedEffect(uiState.screenEffect, reduceMotion) {
        val trigger = uiState.screenEffect ?: return@LaunchedEffect
        effectJob?.cancel()
        activeEffect = null
        onScreenEffectConsumed(trigger.messageId)
        if (reduceMotion) {
            return@LaunchedEffect
        }
        effectJob = effectScope.launch {
            delay(700)
            activeEffect = trigger
        }
    }

    LaunchedEffect(uiState.actionError) {
        val error = uiState.actionError ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(error)
        onActionErrorShown()
    }

    LaunchedEffect(uiState.faceTimeLaunch) {
        val launch = uiState.faceTimeLaunch ?: return@LaunchedEffect
        context.startActivity(
            Intent(context, FaceTimeActivity::class.java)
                .putExtra("link", launch.link)
                .putExtra("name", launch.displayName)
                .putExtra("desc", launch.description)
                .putExtra("callUuid", launch.callUuid),
        )
        onFaceTimeLaunchConsumed()
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

    fun stageAttachments(uris: List<Uri>) {
        if (uris.isEmpty()) return
        scope.launch {
            val prepared = uris.mapNotNull { prepareOutgoingAttachment(context, it) }
            if (prepared.isEmpty()) {
                snackbarHostState.showSnackbar("Could not read attachment")
                return@launch
            }
            onStageAttachments(prepared)
        }
    }

    // System photo picker for images/videos (multi-select stages them on the
    // draft); GetContent for any file.
    val pickMedia = rememberLauncherForActivityResult(
        ActivityResultContracts.PickMultipleVisualMedia(maxItems = PhotoPickerMaxItems),
    ) { uris -> stageAttachments(uris) }
    val pickFile = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent(),
    ) { uri -> stageAttachments(listOfNotNull(uri)) }
    val pickSticker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia(),
    ) { uri ->
        if (uri == null || stickerTarget == null) return@rememberLauncherForActivityResult
        scope.launch {
            pendingSticker = prepareOutgoingAttachment(context, uri)
            if (pendingSticker == null) {
                stickerTarget = null
                snackbarHostState.showSnackbar("Could not read sticker image")
            }
        }
    }

    // Contact names for group sender labels and "<name> unsent a message"
    // rows (best effort).
    val senderNames = remember { mutableStateMapOf<String, String>() }
    LaunchedEffect(uiState.messages) {
        val resolver = UiContacts.contactNames ?: return@LaunchedEffect
        val addresses = uiState.messages
            .filter { !it.isFromMe && it.senderAddress != null }
            .mapNotNull { it.senderAddress }
            .distinct()
        val names = withContext(Dispatchers.IO) {
            addresses.mapNotNull { address ->
                resolver(address)?.first?.let { address to it }
            }
        }
        names.forEach { (address, name) ->
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

    val backgroundFile = uiState.chat?.effectiveBackgroundPath()?.let(::File)
    val background = rememberDecodedImage(backgroundFile, maxDimensionPx = 1440)
    // The scrim keeps bubbles readable over a photo; dark themes need the
    // heavier dim because both the wallpaper and the incoming bubbles are dark.
    val darkTheme = isSystemInDarkTheme()
    val scrimAlpha = when {
        uiState.chat?.customBackgroundPath != null -> if (darkTheme) 0.40f else 0.22f
        else -> if (darkTheme) 0.34f else 0.16f
    }

    // Container-transform target for the chat row (no-op in multi-pane and in
    // previews without a SharedTransitionLayout).
    val sharedContainer = uiState.chat?.let { Modifier.sharedChatContainer(it.id) } ?: Modifier

    Box(modifier = modifier.then(sharedContainer)) {
        background?.image?.let { image ->
            Image(
                bitmap = image,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
            Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = scrimAlpha)))
        }
        Scaffold(
            modifier = Modifier.fillMaxSize(),
            containerColor = if (background != null) Color.Transparent else MaterialTheme.colorScheme.background,
            contentWindowInsets = WindowInsets(0, 0, 0, 0),
            snackbarHost = { SnackbarHost(snackbarHostState) },
            topBar = {
                TopAppBar(
                    title = {
                        ChatHeader(
                            chat = uiState.chat,
                            modifier = Modifier.clickable(
                                onClickLabel = "Open conversation details",
                                role = Role.Button,
                                onClick = onOpenChatInfo,
                            ),
                        )
                    },
                    subtitle = {
                        Text(if (uiState.chat?.isSms == true) "SMS / MMS" else "iMessage")
                    },
                    navigationIcon = {
                        if (showBackButton) {
                            FilledTonalIconButton(
                                onClick = onBack,
                                shapes = IconButtonDefaults.shapes(),
                            ) {
                                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                            }
                        }
                    },
                    actions = {
                        if (uiState.chat?.isSms == false) {
                            FilledTonalIconButton(
                                onClick = onStartFaceTime,
                                shapes = IconButtonDefaults.shapes(),
                                enabled = !uiState.faceTimeStarting,
                            ) {
                                if (uiState.faceTimeStarting) {
                                    // Too small for the morphing polygon; the
                                    // plain circular form reads cleanly at 24dp.
                                    CircularProgressIndicator(
                                        strokeWidth = 2.dp,
                                        modifier = Modifier.size(24.dp),
                                    )
                                } else {
                                    Icon(Icons.Filled.VideoCall, contentDescription = "Start FaceTime call")
                                }
                            }
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
                    pendingAttachments = uiState.pendingAttachments,
                    onRemovePendingAttachment = onRemovePendingAttachment,
                    pendingEffect = pendingOption,
                    onClearPendingEffect = { stagePendingEffect(null) },
                    onSendLongClick = { showEffectPicker = true },
                    composerActionLabel = when {
                        uiState.editingMessage != null -> "Editing message"
                        uiState.replyingTo != null -> "Replying"
                        else -> null
                    },
                    composerActionText = uiState.editingMessage?.text ?: uiState.replyingTo?.let { target ->
                        target.message.text.ifBlank { target.message.attachmentMeta?.name ?: "Attachment" }
                    },
                    composerActionFromMe = uiState.replyingTo?.message?.isFromMe == true,
                    smsChat = smsChat,
                    inputPlaceholder = if (openThread != null || uiState.replyingTo != null) "Reply" else "Message",
                    onClearComposerAction = onCancelComposerAction,
                )
            },
        ) { padding ->
            Box(modifier = Modifier.padding(padding)) {
                when {
                    uiState.initialLoading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        LoadingIndicator()
                    }
                    uiState.messages.isEmpty() && !isTyping ->
                        ChatEmptyState(Modifier.fillMaxSize())
                    else -> LazyColumn(
                        state = listState,
                        reverseLayout = true,
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(vertical = 8.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        if (isTyping) {
                            item(key = "typing-indicator") {
                                TypingIndicatorRow(
                                    senderAddress = uiState.typingSenders.first(),
                                    modifier = Modifier.widthIn(max = ConversationContentMaxWidth),
                                )
                            }
                        }
                        items(
                            items = entries,
                            key = { it.key },
                            contentType = {
                                when (it) {
                                    is ConversationEntry.Message -> "message"
                                    is ConversationEntry.DaySeparator -> "day-separator"
                                }
                            },
                        ) { entry ->
                            when (entry) {
                                is ConversationEntry.Message -> MessageBubble(
                                    message = entry.message,
                                    showStatus = entry.showStatus,
                                    tightTop = entry.tightTop,
                                    tightBottom = entry.tightBottom,
                                    showSenderName = entry.showSenderName,
                                    smsChat = smsChat,
                                    attachmentFile = attachmentFile,
                                    onOpenAttachment = onOpenAttachment,
                                    onDownloadAttachment = onDownloadAttachment,
                                    senderDisplayName = entry.message.senderAddress?.let { senderNames[it] },
                                    replyQuote = resolveReplyQuote(
                                        entry.message,
                                        messagesByGuid,
                                        senderNames,
                                    ),
                                    onOpenReplyThread = { onOpenReplyThread(entry.message) },
                                    onDownloadSticker = { guid ->
                                        onDownloadAttachment(
                                            AttachmentMeta(
                                                guid = guid,
                                                mime = "image/*",
                                                name = "Sticker",
                                                sizeBytes = null,
                                                isImage = true,
                                                downloaded = false,
                                            ),
                                        )
                                    },
                                    onLongPressPart = if (entry.message.status == MessageStatus.SENDING) {
                                        null
                                    } else {
                                        { part -> selectedAction = SelectedMessageAction(entry.message, part) }
                                    },
                                    onSwipeReply = if (canSwipeReply(entry.message)) {
                                        { part -> onReply(entry.message, part) }
                                    } else {
                                        null
                                    },
                                    modifier = Modifier.widthIn(max = ConversationContentMaxWidth)
                                        .animateItem(
                                            fadeInSpec = itemSpecs.fadeIn,
                                            fadeOutSpec = itemSpecs.fadeOut,
                                            placementSpec = itemSpecs.placement,
                                        ),
                                )
                                is ConversationEntry.DaySeparator ->
                                    DaySeparatorRow(
                                        label = formatConversationDay(entry.epochMillis),
                                        modifier = Modifier.widthIn(max = ConversationContentMaxWidth)
                                            .animateItem(
                                                fadeInSpec = itemSpecs.fadeIn,
                                                fadeOutSpec = itemSpecs.fadeOut,
                                                placementSpec = itemSpecs.placement,
                                            ),
                                    )
                            }
                        }
                        if (uiState.loadingOlder) {
                            item(key = "loading-older") {
                                Box(
                                    modifier = Modifier.widthIn(max = ConversationContentMaxWidth)
                                        .fillMaxWidth().padding(12.dp),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    LoadingIndicator()
                                }
                            }
                        }
                    }
                }
            }
        }

        // Full-screen send-effect overlay above everything, fading in and out
        // instead of hard-cutting. The last non-null trigger stays rendered for
        // the exit pass.
        var renderedEffect by remember { mutableStateOf<ScreenEffectTrigger?>(null) }
        AnimatedVisibility(
            visible = activeEffect != null,
            enter = fadeIn(fastEffectsSpec()),
            exit = fadeOut(defaultEffectsSpec()),
        ) {
            LaunchedEffect(activeEffect) { activeEffect?.let { renderedEffect = it } }
            renderedEffect?.let { trigger ->
                SendEffectOverlay(
                    effectId = trigger.effectId,
                    modifier = Modifier.fillMaxSize(),
                    onFinished = { activeEffect = null },
                )
            }
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

    selectedAction?.let { selection ->
        val message = selection.message
        MessageActionSheet(
            message = message,
            isSms = uiState.chat?.isSms == true,
            onReact = { index, emoji ->
                selectedAction = null
                onReact(message, selection.part, index, emoji)
            },
            onReply = {
                selectedAction = null
                onReply(message, selection.part)
            },
            onSticker = {
                selectedAction = null
                stickerTarget = selection
                pickSticker.launch(
                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
                )
            },
            onEdit = {
                selectedAction = null
                onEdit(message)
            },
            onUnsend = {
                selectedAction = null
                confirmUnsend = message
            },
            onDismiss = { selectedAction = null },
        )
    }

    val placementTarget = stickerTarget
    val sticker = pendingSticker
    if (placementTarget != null && sticker != null) {
        StickerPlacementSheet(
            target = placementTarget.message,
            sticker = sticker,
            onConfirm = { transform ->
                onSendSticker(placementTarget.message, placementTarget.part, sticker, transform)
                pendingSticker = null
                stickerTarget = null
            },
            onDismiss = {
                pendingSticker = null
                stickerTarget = null
            },
        )
    }

    uiState.replyThread?.let { thread ->
        ReplyThreadSheet(
            thread = thread,
            threadSmsChat = smsChat,
            attachmentFile = attachmentFile,
            onOpenAttachment = onOpenAttachment,
            onDownloadAttachment = onDownloadAttachment,
            onReply = { message -> onReplyFromThread(message, thread.part) },
            onDismiss = onCloseReplyThread,
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
    onSticker: () -> Unit,
    onEdit: () -> Unit,
    onUnsend: () -> Unit,
    onDismiss: () -> Unit,
) {
    var showCustomReaction by remember(message.guid) { mutableStateOf(false) }
    var customReaction by remember(message.guid) { mutableStateOf("") }
    val normalizedCustomReaction = normalizeCustomReaction(customReaction)
    ModalBottomSheet(onDismissRequest = onDismiss) {
        if (!isSms) {
            // Connected group of tapbacks: they are alternatives within one set,
            // so they read as a single object rather than a loose row of text,
            // and each carries a real button role and a 48dp target.
            //
            // Built from a Row with ButtonGroupDefaults.ConnectedSpaceBetween
            // rather than the ButtonGroup composable on purpose. ButtonGroup
            // reserves width for an overflow indicator and then hands each child
            // a minimum width; with six fixed members in a bottom sheet that
            // budget goes negative and its measure policy throws
            // "maxWidth must be >= than minWidth". The hand-built form is the
            // documented alternative and gives full layout control.
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(
                    ButtonGroupDefaults.ConnectedSpaceBetween,
                ),
            ) {
                Tapbacks.forEachIndexed { index, emoji ->
                    FilledTonalIconButton(
                        onClick = { onReact(index, null) },
                        shapes = IconButtonDefaults.shapes(),
                        modifier = Modifier.weight(1f).height(48.dp),
                    ) {
                        Text(
                            text = emoji,
                            style = MaterialTheme.typography.titleMedium,
                        )
                    }
                }
            }
            TextButton(
                onClick = { showCustomReaction = true },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp),
            ) {
                Icon(Icons.Filled.AddReaction, contentDescription = null)
                Text("Custom reaction", modifier = Modifier.fillMaxWidth().padding(start = 12.dp))
            }
        }
        TextButton(
            onClick = onReply,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp),
        ) { Text("Reply", modifier = Modifier.fillMaxWidth()) }
        if (!isSms) {
            TextButton(
                onClick = onSticker,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp),
            ) { Text("Add sticker", modifier = Modifier.fillMaxWidth()) }
        }
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
    if (showCustomReaction) {
        AlertDialog(
            onDismissRequest = { showCustomReaction = false },
            title = { Text("Custom reaction") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Choose or enter one emoji.")
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        CustomReactionSuggestions.forEach { emoji ->
                            FilledTonalIconButton(
                                onClick = { customReaction = emoji },
                                shapes = IconButtonDefaults.shapes(),
                            ) {
                                Text(emoji)
                            }
                        }
                    }
                    TextField(
                        value = customReaction,
                        onValueChange = { customReaction = it },
                        singleLine = true,
                        label = { Text("Emoji") },
                        isError = customReaction.isNotEmpty() && normalizedCustomReaction == null,
                        supportingText = if (customReaction.isNotEmpty() && normalizedCustomReaction == null) {
                            { Text("Enter a single emoji") }
                        } else {
                            null
                        },
                    )
                }
            },
            confirmButton = {
                TextButton(
                    enabled = normalizedCustomReaction != null,
                    onClick = { onReact(6, requireNotNull(normalizedCustomReaction)) },
                ) { Text("React") }
            },
            dismissButton = {
                TextButton(onClick = { showCustomReaction = false }) { Text("Cancel") }
            },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun StickerPlacementSheet(
    target: MessageItem,
    sticker: OutgoingAttachment,
    onConfirm: (StickerTransform) -> Unit,
    onDismiss: () -> Unit,
) {
    // Hidden + Expanded only: the old skipPartiallyExpanded behavior.
    val sheetState = rememberBottomSheetState(
        initialValue = SheetValue.Hidden,
        enabledValues = setOf(SheetValue.Hidden, SheetValue.Expanded),
    )
    val scope = rememberCoroutineScope()
    val decoded = rememberDecodedImage(sticker.file, maxDimensionPx = 512)
    var normalizedX by remember { mutableStateOf(0.72f) }
    var normalizedY by remember { mutableStateOf(0.18f) }
    var stickerScale by remember { mutableStateOf(1f) }
    var rotationDegrees by remember { mutableStateOf(0f) }
    var previewSize by remember { mutableStateOf(androidx.compose.ui.unit.IntSize.Zero) }

    fun dismissAfter(action: () -> Unit) {
        scope.launch {
            sheetState.hide()
            action()
        }
    }

    ModalBottomSheet(
        sheetState = sheetState,
        onDismissRequest = onDismiss,
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp).navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text("Place sticker", style = MaterialTheme.typography.headlineSmall)
            Text(
                "Pinch to resize, twist to rotate, and drag it into place.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Surface(
                shape = MaterialTheme.shapes.extraLarge,
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                modifier = Modifier.fillMaxWidth().height(260.dp),
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .onSizeChanged { previewSize = it }
                        .pointerInput(previewSize) {
                            detectTransformGestures { _, pan, zoom, rotation ->
                                if (previewSize.width > 0 && previewSize.height > 0) {
                                    normalizedX = (normalizedX + pan.x / previewSize.width).coerceIn(0f, 1f)
                                    normalizedY = (normalizedY + pan.y / previewSize.height).coerceIn(0f, 1f)
                                }
                                stickerScale = (stickerScale * zoom).coerceIn(0.35f, 2.5f)
                                rotationDegrees = (rotationDegrees + rotation) % 360f
                            }
                        },
                ) {
                    Surface(
                        // Mirrors the real bubble family (20dp) and its roles.
                        shape = MaterialTheme.shapes.largeIncreased,
                        color = if (target.isFromMe) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.surfaceContainerHigh
                        },
                        contentColor = if (target.isFromMe) {
                            MaterialTheme.colorScheme.onPrimary
                        } else {
                            MaterialTheme.colorScheme.onSurface
                        },
                        modifier = Modifier.align(Alignment.Center).widthIn(max = 260.dp),
                    ) {
                        Text(
                            target.text.ifBlank { target.attachmentMeta?.name ?: "Attachment" },
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                            maxLines = 3,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    decoded?.image?.let { image ->
                        Image(
                            bitmap = image,
                            contentDescription = "Sticker preview",
                            contentScale = ContentScale.Fit,
                            modifier = Modifier
                                .size(92.dp)
                                .offset {
                                    IntOffset(
                                        (normalizedX * previewSize.width - 46.dp.roundToPx()).roundToInt(),
                                        (normalizedY * previewSize.height - 46.dp.roundToPx()).roundToInt(),
                                    )
                                }
                                .graphicsLayer {
                                    scaleX = stickerScale
                                    scaleY = stickerScale
                                    rotationZ = rotationDegrees
                                },
                        )
                    }
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
                horizontalArrangement = Arrangement.End,
            ) {
                TextButton(onClick = { dismissAfter(onDismiss) }) { Text("Cancel") }
                TextButton(
                    enabled = decoded != null,
                    onClick = {
                        val transform = StickerTransform(
                            messageWidth = previewSize.width.coerceAtLeast(1).toDouble(),
                            normalizedX = normalizedX.toDouble(),
                            normalizedY = normalizedY.toDouble(),
                            rotation = rotationDegrees.toDouble() * PI / 180.0,
                            scale = stickerScale.toDouble(),
                        )
                        dismissAfter { onConfirm(transform) }
                    },
                ) { Text("Send sticker") }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ReplyThreadSheet(
    thread: ReplyThreadState,
    threadSmsChat: Boolean,
    attachmentFile: (String) -> File?,
    onOpenAttachment: (String) -> Unit,
    onDownloadAttachment: (AttachmentMeta) -> Unit,
    onReply: (MessageItem) -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier.fillMaxWidth().navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                "Replies",
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.padding(horizontal = 20.dp),
            )
            if (thread.loading) {
                Box(Modifier.fillMaxWidth().height(160.dp), contentAlignment = Alignment.Center) {
                    LoadingIndicator()
                }
            } else if (thread.messages.isEmpty()) {
                Text(
                    "No replies found for this message part.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(20.dp),
                )
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxWidth().height(420.dp),
                    contentPadding = PaddingValues(vertical = 8.dp),
                ) {
                    items(thread.messages, key = { it.id }) { message ->
                        MessageBubble(
                            message = message,
                            showStatus = false,
                            smsChat = threadSmsChat,
                            attachmentFile = attachmentFile,
                            onOpenAttachment = onOpenAttachment,
                            onDownloadAttachment = onDownloadAttachment,
                            onSwipeReply = if (canSwipeReply(message)) {
                                { onReply(message) }
                            } else {
                                null
                            },
                            modifier = Modifier.fillMaxWidth(),
                        )
                        TextButton(
                            onClick = { onReply(message) },
                            modifier = Modifier.padding(horizontal = 18.dp),
                        ) { Text("Reply") }
                    }
                }
            }
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
                avatarPath = chat.avatarPath ?: rememberContactAvatarPath(chat.avatarAddress),
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
            style = MaterialTheme.typography.titleLargeEmphasized,
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
                shape = MaterialTheme.shapes.largeIncreased,
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
            ) {
                TypingDots(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp))
            }
        }
    }
}

@Composable
private fun TypingDots(modifier: Modifier = Modifier) {
    val reduceMotion = LocalReduceMotion.current
    val transition = rememberInfiniteTransition(label = "typing")
    Row(modifier = modifier, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
        repeat(3) { index ->
            if (reduceMotion) {
                // Removed-animations users get the indicator, not the loop.
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.onSurfaceVariant),
                )
            } else {
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
                        // Draw-phase reads only: no per-frame recomposition.
                        .graphicsLayer {
                            scaleX = scale
                            scaleY = scale
                            this.alpha = alpha
                        }
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.onSurfaceVariant),
                )
            }
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
 * Expressive capsule input bar: tonal attachment action + growing text field
 * + a send action that morphs color/scale when text is present.
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
    val inputFocus = remember { FocusRequester() }
    LaunchedEffect(composerActionLabel) {
        if (composerActionLabel != null) {
            runCatching { inputFocus.requestFocus() }
        }
    }

    // Color is an effects animation; the button's size response is spatial.
    // (The spec helpers read the theme scheme and honor reduced motion.)
    val sendContainer by animateColorAsState(
        targetValue = if (hasText) {
            MaterialTheme.colorScheme.primary
        } else {
            MaterialTheme.colorScheme.surfaceContainerHighest
        },
        animationSpec = defaultEffectsSpec(),
        label = "sendContainer",
    )
    val sendContent by animateColorAsState(
        targetValue = if (hasText) {
            MaterialTheme.colorScheme.onPrimary
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant
        },
        animationSpec = defaultEffectsSpec(),
        label = "sendContent",
    )
    val sendScale by animateFloatAsState(
        targetValue = if (hasContent) 1f else 0.9f,
        animationSpec = fastSpatialSpec(),
        label = "sendScale",
    )
    // Press corner-morph: the send action rounds out extra while pressed, the
    // same interaction signal the shapes= button defaults encode.
    val sendInteractionSource = remember { MutableInteractionSource() }
    val sendPressed by sendInteractionSource.collectIsPressedAsState()
    val sendCorner by animateFloatAsState(
        targetValue = if (sendPressed) 32f else 28f,
        animationSpec = fastSpatialSpec(),
        label = "sendCorner",
    )

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface)
            .navigationBarsPadding()
            .imePadding(),
    ) {
        AnimatedVisibility(
            visible = composerActionLabel != null,
            enter = expandVertically(defaultSpatialSpec()) +
                fadeIn(defaultEffectsSpec()),
            exit = shrinkVertically(defaultSpatialSpec()) +
                fadeOut(defaultEffectsSpec()),
        ) {
            if (composerActionLabel != null) {
                val stripeColor = when {
                    composerActionLabel == "Replying" && composerActionFromMe && smsChat ->
                        smsServiceColors().container
                    composerActionLabel == "Replying" && composerActionFromMe ->
                        MaterialTheme.colorScheme.primary
                    composerActionLabel == "Replying" ->
                        MaterialTheme.colorScheme.outline
                    else -> MaterialTheme.colorScheme.tertiary
                }
                Box(
                    modifier = Modifier.fillMaxWidth(),
                    contentAlignment = Alignment.Center,
                ) {
                    Row(
                        modifier = Modifier
                            .padding(start = 16.dp, end = 4.dp, top = 6.dp, bottom = 2.dp)
                            .widthIn(max = ConversationContentMaxWidth)
                            .fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(
                            modifier = Modifier
                                .padding(end = 10.dp)
                                .size(width = 3.dp, height = 32.dp)
                                .background(stripeColor, RoundedCornerShape(3.dp)),
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = composerActionLabel,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            composerActionText?.let {
                                Text(
                                    text = it,
                                    style = MaterialTheme.typography.bodyMedium,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                        IconButton(
                            onClick = onClearComposerAction,
                            modifier = Modifier.size(48.dp),
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Close,
                                contentDescription = "Cancel ${composerActionLabel.lowercase()}",
                            )
                        }
                    }
                }
            }
        }
        // Pending send-effect chip staged from the picker.
        AnimatedVisibility(
            visible = pendingEffect != null,
            enter = expandVertically(defaultSpatialSpec()) +
                fadeIn(defaultEffectsSpec()),
            exit = shrinkVertically(defaultSpatialSpec()) +
                fadeOut(defaultEffectsSpec()),
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
        Box(
            modifier = Modifier.fillMaxWidth(),
            contentAlignment = Alignment.Center,
        ) {
            Surface(
                shape = MaterialTheme.shapes.extraLargeIncreased,
                // The container role already carries the tonal layer; stacking
                // elevation tint on it double-signals the same thing.
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                    .widthIn(max = ConversationContentMaxWidth).fillMaxWidth(),
            ) {
                Row(
                    modifier = Modifier.padding(6.dp),
                    verticalAlignment = Alignment.Bottom,
                ) {
                    Surface(
                        shape = MaterialTheme.shapes.extraLarge,
                        color = MaterialTheme.colorScheme.surfaceContainerHighest,
                        contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(48.dp).combinedClickable(
                            role = Role.Button,
                            onClickLabel = "Attach photo or video",
                            onLongClickLabel = "Attach any file",
                            onClick = onAttachClick,
                            onLongClick = onAttachLongClick,
                        ),
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Filled.AttachFile,
                                contentDescription = "Attach photo or video",
                                modifier = Modifier.size(24.dp),
                            )
                        }
                    }
                    BasicTextField(
                        value = value,
                        onValueChange = onValueChange,
                        modifier = Modifier
                            .weight(1f)
                            .padding(horizontal = 8.dp)
                            .focusRequester(inputFocus)
                            .semantics { contentDescription = "Message input" },
                        textStyle = MaterialTheme.typography.bodyLarge.copy(
                            color = MaterialTheme.colorScheme.onSurface,
                        ),
                        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                        maxLines = 5,
                        keyboardOptions = KeyboardOptions(
                            imeAction = ImeAction.Send,
                            capitalization = KeyboardCapitalization.Sentences,
                        ),
                        keyboardActions = KeyboardActions(onSend = { if (hasContent) onSend() }),
                        decorationBox = { innerTextField ->
                            Box(
                                modifier = Modifier.fillMaxWidth().padding(vertical = 14.dp),
                            ) {
                                if (value.isEmpty()) {
                                    Text(
                                        text = inputPlaceholder,
                                        style = MaterialTheme.typography.bodyLarge,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                                innerTextField()
                            }
                        },
                    )
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .graphicsLayer {
                                scaleX = sendScale
                                scaleY = sendScale
                            }
                            .clip(RoundedCornerShape(sendCorner))
                            .background(sendContainer)
                            .combinedClickable(
                                interactionSource = sendInteractionSource,
                                indication = LocalIndication.current,
                                enabled = hasContent,
                                role = Role.Button,
                                onClickLabel = "Send",
                                onLongClickLabel = "Choose send effect",
                                onClick = onSend,
                                onLongClick = onSendLongClick,
                            ),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = Icons.Filled.ArrowUpward,
                            contentDescription = "Send",
                            tint = sendContent,
                            modifier = Modifier.size(24.dp),
                        )
                    }
                }
            }
        }
    }
}

/** Square draft thumbnail; decoded image when possible, icon tile otherwise. */
private val StagedThumbSize = 84.dp

@Composable
private fun StagedAttachmentThumb(
    attachment: OutgoingAttachment,
    onRemove: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val decoded = rememberDecodedImage(attachment.file, maxDimensionPx = 256)
    Box(modifier = modifier.size(StagedThumbSize + 14.dp)) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            modifier = Modifier
                .align(Alignment.Center)
                .size(StagedThumbSize),
        ) {
            val image = decoded?.image
            if (image != null) {
                Image(
                    bitmap = image,
                    contentDescription = attachment.name ?: "Attachment",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                // Non-image picks (or not-yet-decoded payloads) get an icon
                // tile with the transfer name.
                Column(
                    modifier = Modifier.fillMaxSize().padding(horizontal = 10.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.InsertDriveFile,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(28.dp),
                    )
                    Text(
                        text = attachment.name ?: "Attachment",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
            }
        }
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.surfaceContainerHighest,
            contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .size(26.dp)
                .clickable(
                    role = Role.Button,
                    onClickLabel = "Remove ${attachment.name ?: "attachment"}",
                    onClick = onRemove,
                ),
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = Icons.Filled.Close,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                )
            }
        }
    }
}

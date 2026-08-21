package app.openbubbles.nativeapp.ui.chat

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import androidx.core.content.FileProvider
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.MutableTransitionState
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.InsertDriveFile
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AddReaction
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.ChatBubble
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Photo
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.VideoCall
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
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
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.IntOffset
import app.openbubbles.nativeapp.data.AttachmentMeta
import app.openbubbles.nativeapp.data.AppGraph
import app.openbubbles.nativeapp.data.CoreGraph
import app.openbubbles.nativeapp.data.ChatListItem
import app.openbubbles.nativeapp.data.MessageItem
import app.openbubbles.nativeapp.data.MessagingPrefs
import app.openbubbles.nativeapp.data.MessageStatus
import app.openbubbles.nativeapp.data.OutgoingAttachment
import app.openbubbles.nativeapp.data.deleteOwnedOutgoingDraft
import app.openbubbles.nativeapp.data.StickerTransform
import app.openbubbles.nativeapp.data.ContactDisplay
import app.openbubbles.nativeapp.data.ContactDisplayWarmCache
import app.openbubbles.nativeapp.data.UiContacts
import app.openbubbles.nativeapp.ui.chat.composer.CaptureReview
import app.openbubbles.nativeapp.ui.chat.composer.ComposerTextField
import app.openbubbles.nativeapp.ui.chat.composer.MentionCandidate
import app.openbubbles.nativeapp.ui.chat.composer.SubjectField
import app.openbubbles.nativeapp.ui.chat.composer.currentLocationMessage
import app.openbubbles.nativeapp.ui.common.rememberChatBackground
import app.openbubbles.nativeapp.facetime.startOutgoingFaceTime
import app.openbubbles.nativeapp.ui.common.ChatAvatar
import app.openbubbles.nativeapp.ui.common.LocalIsMultiPane
import app.openbubbles.nativeapp.ui.common.formatConversationTimestamp
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
import app.openbubbles.nativeapp.ui.tooling.LightDarkPreviews
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
import kotlinx.coroutines.isActive
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

    data class TimeSeparator(val epochMillis: Long) : ConversationEntry {
        override val key: String = "time-$epochMillis"
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
        /** Bottom bubble of an incoming run in a group chat draws the avatar. */
        val showAvatar: Boolean = false,
    ) : ConversationEntry {
        override val key: String = "message-${message.id}"
    }
}

/** Bubble-author identity for grouping (mine vs. a specific sender). */
private fun MessageItem.authorKey(): Pair<Boolean, String?> = isFromMe to senderAddress

/** True when this message renders as a bubble (rows like group events break runs). */
private fun MessageItem.rendersAsBubble(): Boolean = !isGroupEvent && !unsent

/** A quiet gap this long (or a day change) starts a new timestamped cluster. */
private const val TimeSeparatorGapMillis = 60 * 60 * 1000L

/**
 * Builds newest-first entries (the reversed list renders index 0 at the
 * bottom) with timestamp separators above the first message of each time
 * cluster (a new calendar day or an hour-plus quiet gap, the Apple Messages
 * cadence), grouping-aware corner hints, optional group sender-name labels
 * and avatars, and the status row on my newest outgoing message (or any
 * failed one).
 */
fun buildConversationEntries(
    messages: List<MessageItem>,
    zone: ZoneId = ZoneId.systemDefault(),
    showSenderNames: Boolean = false,
): List<ConversationEntry> {
    val lastFromMeId = messages.lastOrNull { it.isFromMe && !it.isGroupEvent }?.id
    // Walk oldest -> newest so each separator lands directly above the first
    // message of its cluster, then reverse into the newest-first order the
    // reversed LazyColumn expects. (Appending separators while walking
    // newest -> oldest put every label below its cluster — "Today" rendered
    // at the very bottom of the transcript.)
    val entries = mutableListOf<ConversationEntry>()
    var previousDate: Long? = null
    for (message in messages) {
        val previous = previousDate
        if (previous == null ||
            message.date - previous >= TimeSeparatorGapMillis ||
            localDay(message.date, zone) != localDay(previous, zone)
        ) {
            entries += ConversationEntry.TimeSeparator(message.date)
        }
        previousDate = message.date
        val showStatus = message.id == lastFromMeId ||
            message.status == MessageStatus.FAILED ||
            message.status == MessageStatus.SENDING
        entries += ConversationEntry.Message(message, showStatus)
    }
    entries.reverse()

    // Second pass: grouping corners + sender names/avatars from visual
    // neighbors. Index 0 is the visual bottom, so the message "above" entry i
    // is i+1. Separators break runs on both sides.
    for (i in entries.indices) {
        val entry = entries[i] as? ConversationEntry.Message ?: continue
        val message = entry.message
        if (!message.rendersAsBubble()) continue

        val above = entries.getOrNull(i + 1) as? ConversationEntry.Message
        val below = entries.getOrNull(i - 1) as? ConversationEntry.Message
        val isReply = message.replyToGuid != null
        val belowIsReply = below?.message?.replyToGuid != null
        val tightTop = above != null &&
            above.message.rendersAsBubble() &&
            above.message.authorKey() == message.authorKey() &&
            !isReply
        val tightBottom = below != null &&
            below.message.rendersAsBubble() &&
            below.message.authorKey() == message.authorKey() &&
            !belowIsReply
        val showName = showSenderNames &&
            !message.isFromMe &&
            message.senderAddress != null &&
            !tightTop
        val showAvatar = showSenderNames &&
            !message.isFromMe &&
            message.senderAddress != null &&
            !tightBottom
        entries[i] = entry.copy(
            tightTop = tightTop,
            tightBottom = tightBottom,
            showSenderName = showName,
            showAvatar = showAvatar,
        )
    }
    return entries
}

/**
 * Conversation view: reversed LazyColumn (newest at the bottom, stays pinned
 * while sending), timestamp separators, bubbles with attachments, edited/unsent
 * rendering, reactions and delivery status, older-history paging when
 * scrolled to the top, an animated typing indicator, draft attachments staged
 * from the + menu (multi-select photo picker, any file, or an in-place voice
 * recording) that ride the next send, and an IME-aware input bar.
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
    onSubjectChange: (String) -> Unit = {},
    onInsertMention: (Int, Int, String, String) -> Unit = { _, _, _, _ -> },
    /**
     * False when this conversation renders as the detail pane beside its own
     * list: there is nothing to navigate back to, and Material specifies that a
     * detail pane in a list-detail layout does not show a back arrow.
     */
    showBackButton: Boolean = true,
    onStageAttachments: (List<OutgoingAttachment>) -> Unit = {},
    onRemovePendingAttachment: (OutgoingAttachment) -> Unit = {},
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
    onOutgoingSendEventConsumed: (Long) -> Unit = {},
    onOpenChatInfo: () -> Unit = {},
    onOpenAttachment: (String) -> Unit = {},
    onDownloadAttachment: (AttachmentMeta) -> Unit = {},
    attachmentFile: (String) -> File? = { null },
) {
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val showDeliveryTimestamps = remember(context) {
        MessagingPrefs(context).showDeliveryTimestamps
    }
    val snackbarHostState = remember { SnackbarHostState() }
    var selectedAction by remember { mutableStateOf<SelectedMessageAction?>(null) }
    var confirmUnsend by remember { mutableStateOf<MessageItem?>(null) }
    var stickerTarget by remember { mutableStateOf<SelectedMessageAction?>(null) }
    val pendingStickerState = remember { mutableStateOf<OutgoingAttachment?>(null) }
    var pendingSticker by pendingStickerState

    // The persisted chat contract is authoritative. Inferring a group from
    // historical sender handles misclassifies 1:1 chats when the same contact
    // has replied from multiple aliases, which adds an unnecessary avatar/name
    // gutter and leaves the reply rail visually detached from their bubble.
    val isGroupChat = uiState.chat?.isGroup == true
    val entries = remember(uiState.messages, isGroupChat) {
        buildConversationEntries(uiState.messages, showSenderNames = isGroupChat)
    }
    val messagesByGuid = remember(uiState.messages) { uiState.messages.associateBy { it.guid } }
    val replyCounts = remember(uiState.messages) { replyCountsByRoot(uiState.messages) }
    val repliesWithContext = remember(entries) { repliesWithInlineContext(entries) }
    val resolvedAttachmentFile = remember(uiState.optimisticStickerFiles, attachmentFile) {
        { guid: String -> uiState.optimisticStickerFiles[guid] ?: attachmentFile(guid) }
    }
    val isTyping = uiState.typingSenders.isNotEmpty()
    val itemSpecs = rememberItemAnimationSpecs()
    val smsChat = uiState.chat?.isSms == true
    val showSubjectLine = remember(context) { MessagingPrefs(context).sendSubjectLines }
    val mentionCandidates by produceState<List<MentionCandidate>>(
        initialValue = emptyList(),
        uiState.chat?.id,
        uiState.chat?.isGroup,
        smsChat,
    ) {
        val chat = uiState.chat
        if (chat == null || !chat.isGroup || smsChat) {
            value = emptyList()
        } else {
            value = withContext(Dispatchers.IO) {
                AppGraph.chatInfo.participantAddresses(chat.preferredChatId).map { address ->
                    val resolved = UiContacts.contactNames?.invoke(address)?.first
                    MentionCandidate(resolved?.substringBefore(' ')?.ifBlank { address } ?: address, address)
                }
            }
        }
    }
    val openThread = uiState.replyThread
    BackHandler(enabled = openThread != null) { onCloseReplyThread() }

    // Tapping a reply quote scrolls to the original and pulses it; the guid
    // outlives the pulse so a row that composes mid-scroll still flashes.
    var replyHighlightGuid by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(replyHighlightGuid) {
        if (replyHighlightGuid != null) {
            delay(2400)
            replyHighlightGuid = null
        }
    }

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
        startOutgoingFaceTime(context, launch)
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

    LaunchedEffect(uiState.outgoingSendEvent) {
        val event = uiState.outgoingSendEvent ?: return@LaunchedEffect
        if (pendingEffectId == event.effectId) stagePendingEffect(null)
        listState.animateScrollToItem(0)
        onOutgoingSendEventConsumed(event.messageId)
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
    val captureFileState = remember { mutableStateOf<File?>(null) }
    var captureFile by captureFileState
    var captureVideo by remember { mutableStateOf(false) }
    val reviewCaptureState = remember { mutableStateOf<File?>(null) }
    var reviewCapture by reviewCaptureState
    val takePhoto = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        if (ok) {
            reviewCapture = captureFile
        } else {
            captureFile?.let { deleteOwnedOutgoingDraft(it, context.cacheDir) }
            captureFile = null
        }
    }
    val takeVideo = rememberLauncherForActivityResult(ActivityResultContracts.CaptureVideo()) { ok ->
        if (ok) {
            reviewCapture = captureFile
        } else {
            captureFile?.let { deleteOwnedOutgoingDraft(it, context.cacheDir) }
            captureFile = null
        }
    }
    fun startCapture(video: Boolean) {
        val file = File(context.cacheDir, "captures/${System.currentTimeMillis()}.${if (video) "mp4" else "jpg"}")
        file.parentFile?.mkdirs()
        captureFile = file
        captureVideo = video
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        if (video) takeVideo.launch(uri) else takePhoto.launch(uri)
    }
    val requestCameraPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) startCapture(captureVideo) else scope.launch { snackbarHostState.showSnackbar("Camera access was denied") }
    }
    fun requestCapture(video: Boolean) {
        captureVideo = video
        if (context.checkSelfPermission(Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) startCapture(video)
        else requestCameraPermission.launch(Manifest.permission.CAMERA)
    }
    val requestLocationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { grants ->
        if (grants[Manifest.permission.ACCESS_FINE_LOCATION] != true &&
            grants[Manifest.permission.ACCESS_COARSE_LOCATION] != true
        ) {
            scope.launch { snackbarHostState.showSnackbar("Location access was denied") }
        } else {
            val message = currentLocationMessage(context)
            if (message == null) scope.launch { snackbarHostState.showSnackbar("Current location is unavailable") }
            else onInputChange(listOf(uiState.input.trimEnd(), message).filter(String::isNotBlank).joinToString("\n"))
        }
    }
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

    // ---- Voice message recording -----------------------------------------
    // Started from the + menu (after the mic runtime permission). While a
    // take is live the composer swaps to a timer + live level bars, the send
    // circle stops-and-sends, and discard (or leaving the screen) deletes it.
    val audioRecordingState = remember { mutableStateOf<AudioRecordingSession?>(null) }
    var audioRecording by audioRecordingState
    val focusManager = LocalFocusManager.current

    fun startAudioRecording() {
        if (audioRecording != null) return
        focusManager.clearFocus()
        // A memo playing in the transcript must not bleed into the take.
        ChatAudioPlayer.stop()
        val session = AudioRecordingSession.start(context)
        if (session == null) {
            scope.launch { snackbarHostState.showSnackbar("Could not start recording") }
        } else {
            audioRecording = session
        }
    }

    val requestMicPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted) {
            startAudioRecording()
        } else {
            scope.launch {
                snackbarHostState.showSnackbar("Microphone access is needed to record audio messages")
            }
        }
    }

    // Pump the session clock + mic levels on a light cadence while live.
    LaunchedEffect(audioRecording) {
        val session = audioRecording ?: return@LaunchedEffect
        while (isActive) {
            session.tick()
            delay(120)
        }
    }

    BackHandler(enabled = audioRecording != null) {
        audioRecording?.discard()
        audioRecording = null
    }

    // Navigating away (or any disposal) mid-take must not leak the recorder.
    DisposableEffect(Unit) {
        onDispose {
            audioRecordingState.value?.discard()
            listOfNotNull(captureFileState.value, reviewCaptureState.value)
                .distinctBy(File::getAbsolutePath)
                .forEach { deleteOwnedOutgoingDraft(it, context.cacheDir) }
            pendingStickerState.value?.file?.let { deleteOwnedOutgoingDraft(it, context.cacheDir) }
        }
    }

    // Voice-memo playback belongs to this transcript; leaving it goes quiet.
    DisposableEffect(Unit) {
        onDispose { ChatAudioPlayer.stop() }
    }

    // Contact names for group sender labels and "<name> unsent a message"
    // rows (best effort). Rows peek ContactDisplayWarmCache while this map
    // fills, so warm names paint on the first frame.
    val senderNames = remember { mutableStateMapOf<String, String>() }
    LaunchedEffect(uiState.messages) {
        val resolver = UiContacts.contactNames ?: return@LaunchedEffect
        val addresses = uiState.messages
            .filter { !it.isFromMe && it.senderAddress != null }
            .mapNotNull { it.senderAddress }
            .distinct()
        val names = withContext(Dispatchers.IO) {
            addresses.mapNotNull { address ->
                val resolved = resolver(address)
                ContactDisplayWarmCache.put(
                    address,
                    ContactDisplay(resolved?.first, resolved?.second),
                )
                resolved?.first?.let { address to it }
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

    val background = rememberChatBackground(
        customPath = uiState.chat?.customBackgroundPath,
        syncedPath = uiState.chat?.transcriptBackgroundPath,
        maxDimensionPx = 1440,
    )
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
            containerColor = when {
                background != null -> Color.Transparent
                LocalIsMultiPane.current -> MaterialTheme.colorScheme.surface
                else -> MaterialTheme.colorScheme.background
            },
            contentWindowInsets = WindowInsets(0, 0, 0, 0),
            snackbarHost = { SnackbarHost(snackbarHostState) },
            topBar = {
                TopAppBar(
                    title = {
                        if (openThread != null) {
                            Text("Thread")
                        } else {
                            ChatHeader(
                                chat = uiState.chat,
                                modifier = Modifier.clickable(
                                    onClickLabel = "Open conversation details",
                                    role = Role.Button,
                                    onClick = onOpenChatInfo,
                                ),
                            )
                        }
                    },
                    subtitle = {
                        if (openThread != null) {
                            val root = openThread.messages.firstOrNull { it.guid == openThread.rootGuid }
                                ?: openThread.messages.firstOrNull()
                            Text(
                                text = root?.text?.ifBlank {
                                    root.attachmentMeta?.name ?: "Replies"
                                } ?: "Replies",
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    },
                    navigationIcon = {
                        if (openThread != null) {
                            FilledTonalIconButton(
                                onClick = onCloseReplyThread,
                                shapes = IconButtonDefaults.shapes(),
                            ) {
                                Icon(
                                    Icons.AutoMirrored.Filled.ArrowBack,
                                    contentDescription = "Close thread",
                                )
                            }
                        } else if (showBackButton) {
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
                    subject = uiState.subject,
                    onSubjectChange = onSubjectChange,
                    showSubjectLine = showSubjectLine,
                    mentionCandidates = mentionCandidates,
                    onMentionSelected = { start, end, candidate ->
                        onInsertMention(start, end, candidate.handle, candidate.displayName)
                    },
                    onSend = {
                        val session = audioRecording
                        if (session != null) {
                            // Stop-and-send: the take rides the staged-attachment
                            // send path; whatever is typed becomes its caption.
                            audioRecording = null
                            val recorded = session.finish()
                            if (recorded == null) {
                                scope.launch {
                                    snackbarHostState.showSnackbar("Recording too short — try again")
                                }
                            } else {
                                onStageAttachments(listOf(recorded))
                                onSend()
                            }
                        } else {
                            onSend()
                        }
                    },
                    onPickMedia = {
                        pickMedia.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo),
                        )
                    },
                    onPickFile = { pickFile.launch("*/*") },
                    onCameraPhoto = { requestCapture(false) },
                    onCameraVideo = { requestCapture(true) },
                    onShareLocation = {
                        if (context.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
                            context.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
                        ) {
                            val message = currentLocationMessage(context)
                            if (message == null) scope.launch { snackbarHostState.showSnackbar("Current location is unavailable") }
                            else onInputChange(listOf(uiState.input.trimEnd(), message).filter(String::isNotBlank).joinToString("\n"))
                        } else {
                            requestLocationPermission.launch(
                                arrayOf(
                                    Manifest.permission.ACCESS_COARSE_LOCATION,
                                    Manifest.permission.ACCESS_FINE_LOCATION,
                                ),
                            )
                        }
                    },
                    onRecordAudio = {
                        if (context.checkSelfPermission(Manifest.permission.RECORD_AUDIO) ==
                            PackageManager.PERMISSION_GRANTED
                        ) {
                            startAudioRecording()
                        } else {
                            requestMicPermission.launch(Manifest.permission.RECORD_AUDIO)
                        }
                    },
                    recording = audioRecording?.let { RecordingUiState(it.elapsedMillis, it.levels) },
                    onCancelRecording = {
                        audioRecording?.discard()
                        audioRecording = null
                    },
                    onFinishRecording = {
                        // Stop-and-stage: the take becomes a draft attachment
                        // (playable in the strip) so a caption can ride along;
                        // the send circle stays the immediate stop-and-send.
                        val session = audioRecording
                        if (session != null) {
                            audioRecording = null
                            val recorded = session.finish()
                            if (recorded == null) {
                                scope.launch {
                                    snackbarHostState.showSnackbar("Recording too short — try again")
                                }
                            } else {
                                onStageAttachments(listOf(recorded))
                            }
                        }
                    },
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
                    sendEnabled = !uiState.textSendInProgress && !uiState.attachmentSendInProgress,
                )
            },
        ) { padding ->
            Box(modifier = Modifier.padding(padding)) {
                when {
                    uiState.initialLoading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        LoadingIndicator()
                    }
                    openThread != null -> ReplyThreadPane(
                        thread = openThread,
                        smsChat = smsChat,
                        senderNames = senderNames,
                        attachmentFile = resolvedAttachmentFile,
                        onOpenAttachment = onOpenAttachment,
                        onDownloadAttachment = onDownloadAttachment,
                        onReply = onReplyFromThread,
                        onLongPressPart = { message, part ->
                            if (message.status != MessageStatus.SENDING) {
                                selectedAction = SelectedMessageAction(message, part)
                            }
                        },
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
                        modifier = Modifier.fillMaxSize(),
                    )
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
                            item(key = "typing-indicator", contentType = "typing-indicator") {
                                TypingIndicatorRow(
                                    senderAddress = uiState.typingSenders.first(),
                                    modifier = Modifier.widthIn(max = ConversationContentMaxWidth)
                                        .animateItem(
                                            fadeInSpec = itemSpecs.fadeIn,
                                            fadeOutSpec = itemSpecs.fadeOut,
                                            placementSpec = itemSpecs.placement,
                                        ),
                                )
                            }
                        }
                        items(
                            items = entries,
                            key = { it.key },
                            contentType = {
                                when (it) {
                                    is ConversationEntry.Message -> "message"
                                    is ConversationEntry.TimeSeparator -> "time-separator"
                                }
                            },
                        ) { entry ->
                            when (entry) {
                                is ConversationEntry.Message -> MessageBubble(
                                    message = entry.message,
                                    showStatus = entry.showStatus,
                                    showDeliveryTimestamp = showDeliveryTimestamps,
                                    tightTop = entry.tightTop,
                                    tightBottom = entry.tightBottom,
                                    showSenderName = entry.showSenderName,
                                    showAvatarGutter = isGroupChat,
                                    showAvatar = entry.showAvatar,
                                    smsChat = smsChat,
                                    attachmentFile = resolvedAttachmentFile,
                                    onOpenAttachment = onOpenAttachment,
                                    onDownloadAttachment = onDownloadAttachment,
                                    senderDisplayName = entry.message.senderAddress?.let {
                                        senderNames[it]
                                            ?: ContactDisplayWarmCache.peek(it)?.displayName
                                    },
                                    replyQuote = if (entry.message.guid in repliesWithContext) {
                                        null
                                    } else {
                                        resolveReplyQuote(
                                            entry.message,
                                            messagesByGuid,
                                            senderNames,
                                        )
                                    },
                                    onReplyQuoteTap = {
                                        val target = resolveReplyScrollTarget(
                                            entries,
                                            entry.message.replyToGuid,
                                        )
                                        if (target == null) {
                                            // Original not in the loaded window:
                                            // the thread pane can fetch it.
                                            onOpenReplyThread(entry.message)
                                        } else {
                                            replyHighlightGuid = entry.message.replyToGuid
                                            scope.launch {
                                                listState.animateScrollToItem(
                                                    target + if (isTyping) 1 else 0,
                                                )
                                            }
                                        }
                                    },
                                    replyCount = replyCounts[entry.message.guid] ?: 0,
                                    onReplyCountTap = { onOpenReplyThread(entry.message) },
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
                                        )
                                        .replyHighlightPulse(
                                            active = replyHighlightGuid == entry.message.guid,
                                        ),
                                )
                                is ConversationEntry.TimeSeparator -> {
                                    val timestamp = formatConversationTimestamp(entry.epochMillis)
                                    TimeSeparatorRow(
                                        day = timestamp.day,
                                        time = timestamp.time,
                                        modifier = Modifier.widthIn(max = ConversationContentMaxWidth)
                                            .animateItem(
                                                fadeInSpec = itemSpecs.fadeIn,
                                                fadeOutSpec = itemSpecs.fadeOut,
                                                placementSpec = itemSpecs.placement,
                                            ),
                                    )
                                }
                            }
                        }
                        if (uiState.loadingOlder) {
                            item(key = "loading-older", contentType = "loading-older") {
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
            chatGuid = uiState.chat?.guid.orEmpty(),
            chatTitle = uiState.chat?.title.orEmpty(),
            isSms = uiState.chat?.isSms == true,
            isGroup = uiState.chat?.isGroup == true,
            attachmentFile = attachmentFile,
            onDownloadAttachment = onDownloadAttachment,
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
            onForward = {
                selectedAction = null
                scope.launch {
                    runCatching {
                        AppGraph.messageActions.markForwarded(listOf(message.id))
                    }
                    snackbarHostState.showSnackbar("Forward: pick a chat from the share sheet")
                }
            },
            onBookmark = {
                selectedAction = null
                scope.launch {
                    AppGraph.messageActions.setBookmarked(listOf(message.id), !message.isBookmarked)
                    snackbarHostState.showSnackbar(
                        if (message.isBookmarked) "Bookmark removed" else "Bookmarked",
                    )
                }
            },
            onSelectMultiple = { selectedAction = null },
            onViewThread = {
                selectedAction = null
                if (message.replyToGuid != null) onOpenReplyThread(message)
            },
            onStartConversation = {
                selectedAction = null
                val address = message.senderAddress
                if (!address.isNullOrBlank()) {
                    scope.launch {
                        val id = withContext(Dispatchers.IO) {
                            CoreGraph.findOrCreateChat(listOf(address), sms = false)
                        }
                        if (id != null) {
                            snackbarHostState.showSnackbar("Opened a conversation")
                        }
                    }
                }
            },
            onBlockSender = {
                selectedAction = null
                scope.launch {
                    AppGraph.messageActions.blockSender(uiState.chat?.id ?: return@launch, archive = true)
                    snackbarHostState.showSnackbar("Sender blocked")
                }
            },
            onDeleteLocal = {
                selectedAction = null
                scope.launch {
                    AppGraph.messageActions.deleteLocal(listOf(message.id))
                }
            },
            onCancelSend = {
                selectedAction = null
                scope.launch {
                    AppGraph.messageActions.cancelOutgoing(message.id)
                }
            },
            onResult = { text ->
                scope.launch { snackbarHostState.showSnackbar(text) }
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
                deleteOwnedOutgoingDraft(sticker.file, context.cacheDir)
                pendingSticker = null
                stickerTarget = null
            },
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
    reviewCapture?.let { file ->
        CaptureReview(
            file = file,
            video = captureVideo,
            onRetake = {
                deleteOwnedOutgoingDraft(file, context.cacheDir)
                reviewCapture = null
                captureFile = null
                requestCapture(captureVideo)
            },
            onUse = {
                onStageAttachments(
                    listOf(
                        OutgoingAttachment(
                            file = file,
                            mime = if (captureVideo) "video/mp4" else "image/jpeg",
                            uti = if (captureVideo) "public.mpeg-4-movie" else "public.jpeg",
                            name = file.name,
                            sizeBytes = file.length(),
                        ),
                    ),
                )
                reviewCapture = null
                captureFile = null
            },
            onDismiss = {
                deleteOwnedOutgoingDraft(file, context.cacheDir)
                reviewCapture = null
                captureFile = null
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
    var normalizedX by remember { mutableFloatStateOf(0.72f) }
    var normalizedY by remember { mutableFloatStateOf(0.18f) }
    var stickerScale by remember { mutableFloatStateOf(1f) }
    var rotationDegrees by remember { mutableFloatStateOf(0f) }
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
                size = 38.dp,
                avatarPath = chat.avatarPath ?: rememberContactAvatarPath(chat.avatarAddress),
            )
            Text(
                text = chat.title,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false),
            )
            if (chat.notifsSilenced) {
                Icon(
                    imageVector = Icons.Filled.Bedtime,
                    contentDescription = "Focus status is silenced",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(16.dp),
                )
            }
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
 * One soft flash behind the original message after scroll-to-original: rise,
 * hold long enough to be found, fade. Reduce-motion swaps the ramps for cuts
 * via the theme effects spec.
 */
@Composable
private fun Modifier.replyHighlightPulse(active: Boolean): Modifier {
    if (!active) return this
    val color = MaterialTheme.colorScheme.primary
    val spec = defaultEffectsSpec<Float>()
    val alpha = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        alpha.animateTo(0.18f, spec)
        delay(650)
        alpha.animateTo(0f, spec)
    }
    return drawBehind {
        drawRoundRect(
            color = color,
            alpha = alpha.value,
            cornerRadius = CornerRadius(24.dp.toPx()),
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
            // Resolving can rebuild the whole handle index; never on main.
            value = withContext(Dispatchers.IO) {
                runCatching { UiContacts.contactNames?.invoke(address)?.first }.getOrNull()
            }
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

@LightDarkPreviews
@Composable
private fun TypingIndicatorPreview() {
    OpenBubblesTheme(dynamicColor = false) {
        Column {
            TypingIndicatorRow(senderAddress = "emma@icloud.com")
            TypingIndicatorRow(senderAddress = null)
        }
    }
}

@LightDarkPreviews
@Composable
private fun MessageInputBarPreview() {
    OpenBubblesTheme(dynamicColor = false) {
        Column {
            MessageInputBar(
                value = "",
                onValueChange = {},
                onSend = {},
                onPickMedia = {},
                onPickFile = {},
                onRecordAudio = {},
            )
            MessageInputBar(
                value = "see you at the trailhead!",
                onValueChange = {},
                onSend = {},
                onPickMedia = {},
                onPickFile = {},
                onRecordAudio = {},
            )
            MessageInputBar(
                value = "I clicked on",
                onValueChange = {},
                onSend = {},
                onPickMedia = {},
                onPickFile = {},
                onRecordAudio = {},
                composerActionLabel = "Replying",
                composerActionText = "For the contact sheet I gave it a screenshot and said when I click contact sheet",
                composerActionFromMe = true,
                inputPlaceholder = "Reply",
            )
            MessageInputBar(
                value = "",
                onValueChange = {},
                onSend = {},
                onPickMedia = {},
                onPickFile = {},
                onRecordAudio = {},
                recording = RecordingUiState(
                    elapsedMillis = 74_000,
                    levels = listOf(
                        0.10f, 0.32f, 0.58f, 0.40f, 0.82f, 0.50f, 0.22f, 0.46f,
                        0.68f, 0.36f, 0.55f, 0.28f, 0.64f, 0.48f, 0.30f, 0.74f,
                        0.42f, 0.20f, 0.56f, 0.34f,
                    ),
                ),
                onCancelRecording = {},
            )
            MessageInputBar(
                value = "",
                onValueChange = {},
                onSend = {},
                onPickMedia = {},
                onPickFile = {},
                onRecordAudio = {},
                pendingAttachments = listOf(
                    OutgoingAttachment(
                        File("/nonexistent/trailhead.jpg"),
                        "image/jpeg", "public.jpeg", "trailhead.jpg", 2_411_520L,
                    ),
                    OutgoingAttachment(
                        File("/nonexistent/itinerary.pdf"),
                        "application/pdf", "com.adobe.pdf", "itinerary.pdf", 412_676L,
                    ),
                ),
                onRemovePendingAttachment = {},
            )
        }
    }
}

/**
 * Expressive composer: a tonal capsule holding the + attach menu and a
 * growing text field (up to three lines, then it scrolls; the IME action
 * stays a plain newline), plus a circular send action outside the capsule
 * that springs in scale when the draft gains content and morphs its corners
 * while pressed. The + menu stages photos/videos or any file on the draft
 * (a removable thumbnail strip above the field rides the next send) and
 * starts an in-place voice recording; while a take is live the capsule
 * swaps to a timer with live level bars and the send circle stops-and-sends
 * it. Long-press the send circle for the send-effect picker.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun MessageInputBar(
    value: String,
    onValueChange: (String) -> Unit,
    onSend: () -> Unit,
    onPickMedia: () -> Unit,
    onPickFile: () -> Unit,
    onRecordAudio: () -> Unit,
    modifier: Modifier = Modifier,
    subject: String = "",
    onSubjectChange: (String) -> Unit = {},
    showSubjectLine: Boolean = false,
    mentionCandidates: List<MentionCandidate> = emptyList(),
    onMentionSelected: (Int, Int, MentionCandidate) -> Unit = { _, _, _ -> },
    onCameraPhoto: () -> Unit = {},
    onCameraVideo: () -> Unit = {},
    onShareLocation: () -> Unit = {},
    recording: RecordingUiState? = null,
    onCancelRecording: () -> Unit = {},
    onFinishRecording: () -> Unit = {},
    pendingAttachments: List<OutgoingAttachment> = emptyList(),
    onRemovePendingAttachment: (OutgoingAttachment) -> Unit = {},
    pendingEffect: SendEffectOption? = null,
    onClearPendingEffect: () -> Unit = {},
    onSendLongClick: () -> Unit = {},
    composerActionLabel: String? = null,
    composerActionText: String? = null,
    composerActionFromMe: Boolean = false,
    smsChat: Boolean = false,
    inputPlaceholder: String = "Message",
    onClearComposerAction: () -> Unit = {},
    sendEnabled: Boolean = true,
) {
    val hasText = value.isNotBlank()
    val hasContent = hasText || pendingAttachments.isNotEmpty()
    // A live voice take is sendable even with an empty draft: the send circle
    // is the stop-and-send action for it.
    val sendActive = hasContent || recording != null
    val inputFocus = remember { FocusRequester() }
    LaunchedEffect(composerActionLabel) {
        if (composerActionLabel != null) {
            runCatching { inputFocus.requestFocus() }
        }
    }

    // Color is an effects animation; the button's size response is spatial.
    // (The spec helpers read the theme scheme and honor reduced motion.)
    val sendContainer by animateColorAsState(
        targetValue = if (sendActive) {
            MaterialTheme.colorScheme.primary
        } else {
            MaterialTheme.colorScheme.surfaceContainerHighest
        },
        animationSpec = defaultEffectsSpec(),
        label = "sendContainer",
    )
    val sendContent by animateColorAsState(
        targetValue = if (sendActive) {
            MaterialTheme.colorScheme.onPrimary
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant
        },
        animationSpec = defaultEffectsSpec(),
        label = "sendContent",
    )
    val sendScale by animateFloatAsState(
        targetValue = if (sendActive) 1f else 0.9f,
        animationSpec = fastSpatialSpec(),
        label = "sendScale",
    )
    // Press corner-morph: the circle softens into a squircle while pressed,
    // the same interaction signal the shapes= button defaults encode.
    val sendInteractionSource = remember { MutableInteractionSource() }
    val sendPressed by sendInteractionSource.collectIsPressedAsState()
    val sendCornerPercent by animateFloatAsState(
        targetValue = if (sendPressed) 30f else 50f,
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
        if (showSubjectLine) SubjectField(subject, onSubjectChange)
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
        // Draft attachments staged from the picker; each removes itself,
        // everything rides the next send. Initial state mirrors visibility so
        // a restored draft (rotation, recomposition entry) renders without an
        // enter animation — and first-frame captures see the strip.
        val attachmentsVisibility = remember {
            MutableTransitionState(pendingAttachments.isNotEmpty())
        }.apply { targetState = pendingAttachments.isNotEmpty() }
        AnimatedVisibility(
            visibleState = attachmentsVisibility,
            enter = expandVertically(defaultSpatialSpec()) +
                fadeIn(defaultEffectsSpec()),
            exit = shrinkVertically(defaultSpatialSpec()) +
                fadeOut(defaultEffectsSpec()),
        ) {
            LazyRow(
                modifier = Modifier.widthIn(max = ConversationContentMaxWidth),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                items(pendingAttachments, key = { it.file.absolutePath }) { attachment ->
                    StagedAttachmentThumb(
                        attachment = attachment,
                        onRemove = { onRemovePendingAttachment(attachment) },
                    )
                }
            }
        }
        Box(
            modifier = Modifier.fillMaxWidth(),
            contentAlignment = Alignment.Center,
        ) {
            Row(
                modifier = Modifier
                    .padding(horizontal = 12.dp, vertical = 8.dp)
                    .widthIn(max = ConversationContentMaxWidth)
                    .fillMaxWidth(),
                verticalAlignment = Alignment.Bottom,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Surface(
                    shape = MaterialTheme.shapes.extraLargeIncreased,
                    // The container role already carries the tonal layer; stacking
                    // elevation tint on it double-signals the same thing.
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    modifier = Modifier.weight(1f),
                ) {
                    if (recording != null) {
                        RecordingComposerRow(
                            state = recording,
                            onDiscard = onCancelRecording,
                            onFinish = onFinishRecording,
                        )
                    } else {
                        Row(
                            modifier = Modifier.padding(6.dp),
                            verticalAlignment = Alignment.Bottom,
                        ) {
                            AttachMenuButton(
                                onPickMedia = onPickMedia,
                                onPickFile = onPickFile,
                                onRecordAudio = onRecordAudio,
                                onCameraPhoto = onCameraPhoto,
                                onCameraVideo = onCameraVideo,
                                onShareLocation = onShareLocation,
                            )
                            ComposerTextField(
                                value = value,
                                onValueChange = onValueChange,
                                candidates = mentionCandidates,
                                onMentionSelected = onMentionSelected,
                                placeholder = inputPlaceholder,
                                focusRequester = inputFocus,
                                modifier = Modifier
                                    .weight(1f),
                            )
                        }
                    }
                }
                // The send circle lives outside the capsule: it springs in
                // scale when the draft becomes sendable and morphs from
                // circle to squircle while pressed.
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .graphicsLayer {
                            scaleX = sendScale
                            scaleY = sendScale
                        }
                        .clip(RoundedCornerShape(sendCornerPercent.roundToInt()))
                        .background(sendContainer)
                        .combinedClickable(
                            interactionSource = sendInteractionSource,
                            indication = LocalIndication.current,
                            enabled = sendActive && sendEnabled,
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

/**
 * The + action anchoring the attach menu: photos/videos, any file, or an
 * in-place voice recording. The icon rotates into a × while the menu is
 * open, a spatial spring so it settles with the same feel as the send circle.
 */
@Composable
private fun AttachMenuButton(
    onPickMedia: () -> Unit,
    onPickFile: () -> Unit,
    onRecordAudio: () -> Unit,
    onCameraPhoto: () -> Unit,
    onCameraVideo: () -> Unit,
    onShareLocation: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var menuOpen by remember { mutableStateOf(false) }
    val iconRotation by animateFloatAsState(
        targetValue = if (menuOpen) 45f else 0f,
        animationSpec = fastSpatialSpec(),
        label = "attachIconRotation",
    )
    Box(modifier = modifier) {
        Surface(
            shape = MaterialTheme.shapes.extraLarge,
            color = MaterialTheme.colorScheme.surfaceContainerHighest,
            contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(48.dp).clickable(
                role = Role.Button,
                onClickLabel = "Attach",
            ) { menuOpen = true },
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = Icons.Filled.Add,
                    contentDescription = "Attach",
                    modifier = Modifier
                        .size(24.dp)
                        .graphicsLayer { rotationZ = iconRotation },
                )
            }
        }
        DropdownMenu(
            expanded = menuOpen,
            onDismissRequest = { menuOpen = false },
        ) {
            DropdownMenuItem(
                text = { Text("Take photo") },
                leadingIcon = { Icon(Icons.Filled.CameraAlt, contentDescription = null) },
                onClick = { menuOpen = false; onCameraPhoto() },
            )
            DropdownMenuItem(
                text = { Text("Record video") },
                leadingIcon = { Icon(Icons.Filled.VideoCall, contentDescription = null) },
                onClick = { menuOpen = false; onCameraVideo() },
            )
            DropdownMenuItem(
                text = { Text("Photos or videos") },
                leadingIcon = { Icon(Icons.Filled.Photo, contentDescription = null) },
                onClick = {
                    menuOpen = false
                    onPickMedia()
                },
            )
            DropdownMenuItem(
                text = { Text("File") },
                leadingIcon = {
                    Icon(Icons.AutoMirrored.Filled.InsertDriveFile, contentDescription = null)
                },
                onClick = {
                    menuOpen = false
                    onPickFile()
                },
            )
            DropdownMenuItem(
                text = { Text("Audio message") },
                leadingIcon = { Icon(Icons.Filled.Mic, contentDescription = null) },
                onClick = {
                    menuOpen = false
                    onRecordAudio()
                },
            )
            DropdownMenuItem(
                text = { Text("Current location") },
                leadingIcon = { Icon(Icons.Filled.LocationOn, contentDescription = null) },
                onClick = { menuOpen = false; onShareLocation() },
            )
        }
    }
}

/**
 * The capsule's recording state: a discard action, a pulsing record dot with
 * the elapsed time, and live mic level bars. The send circle beside the
 * capsule stops and sends the take.
 */
@Composable
private fun RecordingComposerRow(
    state: RecordingUiState,
    onDiscard: () -> Unit,
    onFinish: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Surface(
            shape = MaterialTheme.shapes.extraLarge,
            color = MaterialTheme.colorScheme.surfaceContainerHighest,
            contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(48.dp).clickable(
                role = Role.Button,
                onClickLabel = "Discard recording",
                onClick = onDiscard,
            ),
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = Icons.Filled.Close,
                    contentDescription = "Discard recording",
                    modifier = Modifier.size(24.dp),
                )
            }
        }
        // The pulse is decorative, so it drops out entirely when the user
        // asked the OS to remove animations; the dot stays as the state cue.
        val pulseAlpha = if (LocalReduceMotion.current) {
            1f
        } else {
            val pulse = rememberInfiniteTransition(label = "recordingPulse")
            val alpha by pulse.animateFloat(
                initialValue = 1f,
                targetValue = 0.25f,
                animationSpec = infiniteRepeatable(tween(700), RepeatMode.Reverse),
                label = "recordingPulseAlpha",
            )
            alpha
        }
        Box(
            modifier = Modifier
                .padding(start = 14.dp)
                .size(10.dp)
                .graphicsLayer { alpha = pulseAlpha }
                .background(MaterialTheme.colorScheme.error, CircleShape),
        )
        Text(
            text = formatRecordingTime(state.elapsedMillis),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(horizontal = 10.dp),
        )
        Row(
            modifier = Modifier
                .weight(1f)
                .height(32.dp)
                .padding(end = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(3.dp, Alignment.CenterHorizontally),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            state.levels.forEach { level ->
                val barHeight by animateFloatAsState(
                    targetValue = 4f + level * 22f,
                    animationSpec = fastSpatialSpec(),
                    label = "recordingLevel",
                )
                Box(
                    modifier = Modifier
                        .width(3.dp)
                        .height(barHeight.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.onSurfaceVariant),
                )
            }
        }
        // Stop-and-stage: ends the take and parks it as a playable draft so a
        // caption can ride along; the send circle remains stop-and-send.
        Surface(
            shape = MaterialTheme.shapes.extraLarge,
            color = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
            modifier = Modifier.size(40.dp).clickable(
                role = Role.Button,
                onClickLabel = "Finish recording",
                onClick = onFinish,
            ),
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = Icons.Filled.Stop,
                    contentDescription = "Finish recording",
                    modifier = Modifier.size(20.dp),
                )
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
    // Voice memos stage as a playable card, so the take can be reviewed (and
    // a caption typed) before the send.
    if (attachment.mime.startsWith("audio/", ignoreCase = true)) {
        StagedVoiceMemoCard(attachment = attachment, onRemove = onRemove, modifier = modifier)
        return
    }
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

/**
 * A recorded voice memo staged on the draft: the same inline player face the
 * transcript bubble uses, plus the removable badge every staged attachment
 * carries. Reviewing the take never leaves the composer.
 */
@Composable
private fun StagedVoiceMemoCard(
    attachment: OutgoingAttachment,
    onRemove: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier.padding(top = 7.dp, end = 7.dp)) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            modifier = Modifier.width(232.dp),
        ) {
            VoiceMemoPlayerContent(
                playerKey = "staged:${attachment.file.absolutePath}",
                file = attachment.file,
                playCircle = MaterialTheme.colorScheme.primary,
                onPlayCircle = MaterialTheme.colorScheme.onPrimary,
                wave = MaterialTheme.colorScheme.primary,
                fallbackLabel = attachment.name,
            )
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
                    onClickLabel = "Remove ${attachment.name ?: "voice memo"}",
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

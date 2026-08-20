package app.openbubbles.nativeapp.ui.chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import app.openbubbles.nativeapp.data.AppGraph
import app.openbubbles.nativeapp.data.AttachmentSender
import app.openbubbles.nativeapp.data.ChatListItem
import app.openbubbles.nativeapp.data.ChatListRepository
import app.openbubbles.nativeapp.data.CloudSyncWiring
import app.openbubbles.nativeapp.data.ContactDisplayWarmCache
import app.openbubbles.nativeapp.data.FaceTimeCaller
import app.openbubbles.nativeapp.data.FaceTimeLaunch
import app.openbubbles.nativeapp.data.MessageItem
import app.openbubbles.nativeapp.data.MessageActions
import app.openbubbles.nativeapp.data.MessageListRepository
import app.openbubbles.nativeapp.data.OutgoingAttachment
import app.openbubbles.nativeapp.data.OutgoingMention
import app.openbubbles.nativeapp.data.ReadReceiptSender
import app.openbubbles.nativeapp.data.Sender
import app.openbubbles.nativeapp.data.SmsSender
import app.openbubbles.nativeapp.data.StickerPlacement
import app.openbubbles.nativeapp.data.StickerSender
import app.openbubbles.nativeapp.data.StickerTransform
import app.openbubbles.nativeapp.data.TRANSCRIPT_OPEN_LIMIT
import app.openbubbles.nativeapp.data.TypingRepository
import app.openbubbles.nativeapp.sms.SmsBridge
import app.openbubbles.nativeapp.ui.chatinfo.ChatInfoWarmCache
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

data class ChatUiState(
    val chat: ChatListItem? = null,
    /** Ascending by time (oldest first); the screen reverses for layout. */
    val messages: List<MessageItem> = emptyList(),
    val input: String = "",
    val subject: String = "",
    val mentions: List<OutgoingMention> = emptyList(),
    /** Attachments staged on the draft; sent with the next sendMessage. */
    val pendingAttachments: List<OutgoingAttachment> = emptyList(),
    val loadingOlder: Boolean = false,
    /** Sender addresses with a live typing indicator in this chat. */
    val typingSenders: List<String> = emptyList(),
    /**
     * One-shot trigger to play a full-screen send effect: set when the newest
     * message carries an expressiveSendStyleId that has not been played yet
     * (incoming or outgoing). Distinct per message id so the same effect
     * re-fires for a later message.
     */
    val screenEffect: ScreenEffectTrigger? = null,
    /** Message part selected as the root of the next threaded reply. */
    val replyingTo: ReplyTarget? = null,
    /** Part-aware reply thread currently focused in the conversation. */
    val replyThread: ReplyThreadState? = null,
    /** My text message currently being edited. */
    val editingMessage: MessageItem? = null,
    /** Visible operation failure; cleared after the screen presents it. */
    val actionError: String? = null,
    val faceTimeStarting: Boolean = false,
    /** One-shot handoff to the Android call activity. */
    val faceTimeLaunch: FaceTimeLaunch? = null,
    /** Prevents duplicate taps while the current text draft is being staged. */
    val textSendInProgress: Boolean = false,
    /** Prevents duplicate taps while attachment rows and files are being staged. */
    val attachmentSendInProgress: Boolean = false,
    /** Emitted only after the staged outgoing row is present in [messages]. */
    val outgoingSendEvent: OutgoingSendEvent? = null,
    /** Temporary sticker payloads keyed by their optimistic attachment guid. */
    val optimisticStickerFiles: Map<String, File> = emptyMap(),
) {
    val initialLoading: Boolean get() = chat == null && messages.isEmpty()
}

data class ReplyTarget(
    val message: MessageItem,
    val rootGuid: String,
    val part: Long,
    val partLocator: String,
)

data class ReplyThreadState(
    val rootGuid: String,
    val part: Long,
    val messages: List<MessageItem> = emptyList(),
    val loading: Boolean = true,
    val sourceMessage: MessageItem? = null,
)

/** Identifies the message whose send effect should play (once). */
data class ScreenEffectTrigger(
    val messageId: Long,
    val effectId: String,
)

data class OutgoingSendEvent(
    val messageId: Long,
    val effectId: String?,
)

private data class OptimisticEdit(val token: Long, val text: String)
private data class OptimisticReaction(val token: Long, val emoji: String)
private data class OptimisticUnsend(val token: Long)
private data class OptimisticSticker(
    val token: Long,
    val placement: StickerPlacement,
    val file: File?,
    val expectedAttachmentGuid: String? = null,
)

private data class OptimisticMessageOverlay(
    val edit: OptimisticEdit? = null,
    val reaction: OptimisticReaction? = null,
    val unsend: OptimisticUnsend? = null,
    val stickers: List<OptimisticSticker> = emptyList(),
) {
    val isEmpty: Boolean
        get() = edit == null && reaction == null && unsend == null && stickers.isEmpty()
}

/**
 * Stages the picker's selection for the next [ChatViewModel.sendMessage] call.
 * Lives outside the uiState flow because the chat screen binds fixed
 * callbacks (`onSend: () -> Unit`); the input bar writes it and the send path
 * reads + clears it on the same (main) thread.
 */
object PendingSendEffect {
    @Volatile
    var effectId: String? = null
}

private const val INITIAL_LIMIT = TRANSCRIPT_OPEN_LIMIT
private const val PAGE_SIZE = 20

/**
 * Legacy/cloud rows may not carry reconstructed attributed-body runs yet.
 * Plain text and single attachments still have an unambiguous whole-run
 * range, so keep those replies protocol-correct while newer rows use the
 * exact locator persisted by MessageMapper.
 */
private fun fallbackReplyPartLocator(message: MessageItem, part: Long): String {
    val runLength = if (message.attachmentMetas.any { it.partIndex == part }) {
        1
    } else {
        message.text.length.coerceAtLeast(1)
    }
    return "$part:0:$runLength"
}

class ChatViewModel(
    private val chatId: Long,
    private val chatListRepository: ChatListRepository,
    private val messageRepository: MessageListRepository,
    private val sender: Sender,
    private val messageActions: MessageActions,
    private val faceTimeCaller: FaceTimeCaller,
    private val attachmentSender: AttachmentSender,
    private val stickerSender: StickerSender,
    typingRepository: TypingRepository,
    private val readReceiptSender: ReadReceiptSender,
    private val smsSender: SmsSender = SmsBridge.sender,
    private val smsAttachmentSender: AttachmentSender = SmsBridge.attachmentSender,
    initialInput: String? = null,
    private val historySyncActive: () -> Boolean = { CloudSyncWiring.syncing.value },
    private val openedAtMs: Long = System.currentTimeMillis(),
    private val participantAddresses: (Long) -> List<String> = {
        AppGraph.chatInfo.participantAddresses(it)
    },
    private val participantLookupDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : ViewModel() {

    init {
        // Opening a conversation clears its unread badge and mirrors the
        // receipt to Apple/the user's other devices when connected.
        viewModelScope.launch { readReceiptSender.markRead(chatId, null) }
    }

    private val input = MutableStateFlow(initialInput.orEmpty())
    private val subject = MutableStateFlow("")
    private val mentions = MutableStateFlow<List<OutgoingMention>>(emptyList())
    private val pendingAttachments = MutableStateFlow<List<OutgoingAttachment>>(emptyList())
    private val loadingOlder = MutableStateFlow(false)
    private val replyingTo = MutableStateFlow<ReplyTarget?>(null)
    private val replyThread = MutableStateFlow<ReplyThreadState?>(null)
    private val editingMessage = MutableStateFlow<MessageItem?>(null)
    private val actionError = MutableStateFlow<String?>(null)
    private val faceTimeStarting = MutableStateFlow(false)
    private val faceTimeLaunch = MutableStateFlow<FaceTimeLaunch?>(null)
    private val textSendInProgress = MutableStateFlow(false)
    private val attachmentSendInProgress = MutableStateFlow(false)
    private val outgoingSendEvent = MutableStateFlow<OutgoingSendEvent?>(null)
    private val optimisticMessageOverlays =
        MutableStateFlow<Map<String, OptimisticMessageOverlay>>(emptyMap())
    private var endReached = false
    private var replyThreadJob: Job? = null
    private var composerRevision = 0L
    private var pendingOutgoingSendEvent: OutgoingSendEvent? = null
    private var nextOptimisticToken = 0L

    /** Message ids whose send effect has already been played (once each). */
    private val playedEffectMessageIds = mutableSetOf<Long>()
    private var effectBaselineInitialized = false
    private var lastMarkedIncomingGuid: String? = null
    private var readBaselineInitialized = false

    private val screenEffect = MutableStateFlow<ScreenEffectTrigger?>(null)

    private val cachedMessages = messageRepository.cached(chatId)

    private val chat: StateFlow<ChatListItem?> =
        chatListRepository.chats()
            .map { chats ->
                chats.firstOrNull { item -> item.id == chatId || chatId in item.memberChatIds }
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    init {
        // While the conversation is on screen, warm its details-pane data
        // (shared photos, contact card, poster, Find My) so tapping the
        // header renders content instead of loading placeholders, and the
        // participants' display names + avatars so sender labels, the header
        // avatar, and chat-info rows seed instantly. Local reads only —
        // warming never hits the network.
        viewModelScope.launch {
            val item = chat.filterNotNull().first()
            runCatching {
                val participants = withContext(participantLookupDispatcher) {
                    participantAddresses(item.preferredChatId)
                }
                ContactDisplayWarmCache.warm(participants + listOfNotNull(item.avatarAddress))
            }
            runCatching { ChatInfoWarmCache.warm(item) }
        }
    }

    private val messages: StateFlow<List<MessageItem>> =
        messageRepository.messages(chatId, limit = INITIAL_LIMIT, before = null)
            .onEach { list ->
                observeMessageEffects(list)
                observeIncomingReadState(list)
                observePendingOutgoingSend(list)
                reconcileOptimisticOverlays(list)
            }
            .stateIn(
                viewModelScope,
                SharingStarted.Eagerly,
                cachedMessages,
            )

    private val typingSenders: StateFlow<List<String>> =
        combine(typingRepository.typing(), chat) { entries, item ->
            val chatIds = item?.memberChatIds.orEmpty().ifEmpty { listOf(chatId) }
            entries.filter { it.chatId in chatIds }.map { it.senderAddress }
        }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private fun preferredChatId(): Long = chat.value?.preferredChatId ?: chatId

    private fun sourceChatId(message: MessageItem): Long = message.chatId ?: preferredChatId()

    /**
     * Opening already marks the conversation read. Later incoming rows that
     * land while this transcript is still open should do the same so the
     * unread badge and notification cannot come back for a message the user
     * is looking at.
     */
    private fun observeIncomingReadState(list: List<MessageItem>) {
        val newestIncoming = list.lastOrNull { !it.isFromMe }
        if (!readBaselineInitialized) {
            readBaselineInitialized = true
            lastMarkedIncomingGuid = newestIncoming?.guid
            return
        }
        if (newestIncoming == null || newestIncoming.guid == lastMarkedIncomingGuid) return
        // History import and contact-merge replay rewrite the transcript
        // while this screen is open. Advance the baseline so we do not dump
        // a receipt for every imported page when sync finishes.
        if (historySyncActive()) {
            lastMarkedIncomingGuid = newestIncoming.guid
            return
        }
        // CloudKit rows keep their original Apple timestamp. A page of
        // older messages is not a live arrival and must not start another
        // IDS lookup / iMessage send.
        if (!isLiveIncomingRead(openedAtMs, newestIncoming.date)) {
            lastMarkedIncomingGuid = newestIncoming.guid
            return
        }
        lastMarkedIncomingGuid = newestIncoming.guid
        viewModelScope.launch { readReceiptSender.markRead(chatId, newestIncoming.guid) }
    }

    private fun observeMessageEffects(list: List<MessageItem>) {
        if (!effectBaselineInitialized) {
            effectBaselineInitialized = true
            list.filter { it.expressiveSendStyleId != null }
                .forEach { playedEffectMessageIds.add(it.id) }
            return
        }
        val newest = list.lastOrNull() ?: return
        val styleId = newest.expressiveSendStyleId ?: return
        if (playedEffectMessageIds.add(newest.id)) {
            screenEffect.value = ScreenEffectTrigger(newest.id, styleId)
        }
    }

    val uiState: StateFlow<ChatUiState> =
        combine(messages, chat, input, loadingOlder, typingSenders) { messages, chat, input, loadingOlder, typing ->
            ChatUiState(
                chat = chat,
                messages = messages,
                input = input,
                loadingOlder = loadingOlder,
                typingSenders = typing,
            )
        }.combine(optimisticMessageOverlays) { state, overlays ->
            state.copy(
                messages = applyOptimisticOverlays(state.messages, overlays),
                optimisticStickerFiles = overlays.values
                    .flatMap { it.stickers }
                    .mapNotNull { sticker ->
                        sticker.file?.takeIf { it.isFile }?.let { sticker.placement.attachmentGuid to it }
                    }
                    .toMap(),
            )
        }.combine(screenEffect) { state, effect ->
            state.copy(screenEffect = effect)
        }.combine(replyingTo) { state, reply ->
            state.copy(replyingTo = reply)
        }.combine(replyThread) { state, thread ->
            state.copy(replyThread = thread?.let { mergeReplyThread(it, state.messages) })
        }.combine(editingMessage) { state, editing ->
            state.copy(editingMessage = editing)
        }.combine(actionError) { state, error ->
            state.copy(actionError = error)
        }.combine(faceTimeStarting) { state, starting ->
            state.copy(faceTimeStarting = starting)
        }.combine(faceTimeLaunch) { state, launch ->
            state.copy(faceTimeLaunch = launch)
        }.combine(pendingAttachments) { state, attachments ->
            state.copy(pendingAttachments = attachments)
        }.combine(subject) { state, value ->
            state.copy(subject = value)
        }.combine(mentions) { state, value ->
            state.copy(mentions = value)
        }.combine(textSendInProgress) { state, sending ->
            state.copy(textSendInProgress = sending)
        }.combine(attachmentSendInProgress) { state, sending ->
            state.copy(attachmentSendInProgress = sending)
        }.combine(outgoingSendEvent) { state, event ->
            state.copy(outgoingSendEvent = event)
        }.stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5_000),
            ChatUiState(messages = cachedMessages, input = initialInput.orEmpty()),
        )

    fun onInputChange(value: String) {
        if (input.value != value) composerRevision++
        input.value = value
        mentions.value = mentions.value.filter { mention ->
            val expectedText = buildString {
                append('@')
                append(mention.displayText.removePrefix("@"))
            }
            mention.end <= value.length &&
                value.substring(mention.start, mention.end) == expectedText
        }
    }

    fun onSubjectChange(value: String) {
        if (subject.value != value) composerRevision++
        subject.value = value.take(1_000)
    }

    fun insertMention(start: Int, end: Int, handle: String, displayText: String) {
        val current = input.value
        if (start !in 0..current.length || end !in start..current.length) return
        val label = displayText.removePrefix("@").ifBlank { handle }
        val replacement = "@$label"
        val updated = current.replaceRange(start, end, "$replacement ")
        val delta = updated.length - current.length
        mentions.value = mentions.value.mapNotNull { mention ->
            when {
                mention.end <= start -> mention
                mention.start >= end -> mention.copy(start = mention.start + delta, end = mention.end + delta)
                else -> null
            }
        } + OutgoingMention(start, start + replacement.length, handle, label)
        input.value = updated
        composerRevision++
    }

    fun setBookmarked(messages: Collection<MessageItem>, bookmarked: Boolean) {
        if (messages.isEmpty()) return
        viewModelScope.launch {
            runCatching { messageActions.setBookmarked(messages.map { it.id }, bookmarked) }
                .onFailure { actionError.value = it.message ?: "Could not update bookmark" }
        }
    }

    fun deleteLocal(messages: Collection<MessageItem>) {
        if (messages.isEmpty()) return
        viewModelScope.launch {
            runCatching { messageActions.deleteLocal(messages.map { it.id }) }
                .onFailure { actionError.value = it.message ?: "Could not delete message" }
        }
    }

    fun cancelOutgoing(message: MessageItem) {
        viewModelScope.launch {
            runCatching { messageActions.cancelOutgoing(message.id) }
                .onFailure { actionError.value = it.message ?: "Could not cancel send" }
        }
    }

    fun markForwarded(messages: Collection<MessageItem>) {
        if (messages.isEmpty()) return
        viewModelScope.launch {
            runCatching { messageActions.markForwarded(messages.map { it.id }) }
                .onFailure { actionError.value = it.message ?: "Could not prepare forward" }
        }
    }

    fun blockSender() {
        viewModelScope.launch {
            runCatching { messageActions.blockSender(preferredChatId(), archive = true) }
                .onFailure { actionError.value = it.message ?: "Could not block sender" }
        }
    }

    fun sendMessage() {
        val text = input.value.trim()
        val subjectValue = subject.value.trim().takeIf { it.isNotEmpty() }
        val mentionValues = mentions.value
        val attachments = pendingAttachments.value
        if (attachments.isNotEmpty()) {
            sendDraftAttachments(text, attachments)
            return
        }
        if (text.isEmpty() || textSendInProgress.value) return
        val effectId = PendingSendEffect.effectId
        val editing = editingMessage.value
        val reply = replyingTo.value
        val sendRevision = composerRevision
        if (editing != null) {
            sendEdit(editing, text, sendRevision)
            return
        }
        textSendInProgress.value = true
        viewModelScope.launch {
            runCatching {
                val chatItem = chat.value ?: chat.filterNotNull().first()
                val targetChatId = chatItem.preferredChatId
                when {
                    chatItem.isSms -> smsSender.send(targetChatId, text, subjectValue)
                    reply != null -> sender.sendReply(
                        sourceChatId(reply.message),
                        text,
                        reply.rootGuid,
                        reply.partLocator,
                        subjectValue,
                        mentionValues,
                    )
                    effectId == null -> sender.send(targetChatId, text, subjectValue, mentionValues)
                    else -> sender.sendWithEffect(targetChatId, text, effectId, subjectValue, mentionValues)
                }
            }.onSuccess { accepted ->
                if (PendingSendEffect.effectId == effectId) PendingSendEffect.effectId = null
                queueOutgoingSend(OutgoingSendEvent(accepted.messageId, effectId))
                settleComposerAfterSend(sendRevision)
            }.onFailure { failure ->
                actionError.value = failure.message ?: "Message operation failed"
            }
            textSendInProgress.value = false
        }
    }

    fun consumeOutgoingSendEvent(messageId: Long) {
        if (outgoingSendEvent.value?.messageId == messageId) outgoingSendEvent.value = null
    }

    private fun queueOutgoingSend(event: OutgoingSendEvent) {
        if (messages.value.any { it.id == event.messageId }) {
            outgoingSendEvent.value = event
        } else {
            pendingOutgoingSendEvent = event
        }
    }

    private fun observePendingOutgoingSend(list: List<MessageItem>) {
        val pending = pendingOutgoingSendEvent ?: return
        if (list.any { it.id == pending.messageId }) {
            pendingOutgoingSendEvent = null
            outgoingSendEvent.value = pending
        }
    }

    /** Adds a picked attachment to the draft; sent with the next send. */
    fun stageAttachment(attachment: OutgoingAttachment) {
        stageAttachments(listOf(attachment))
    }

    /** Adds picked attachments to the draft; sent with the next send. */
    fun stageAttachments(attachments: List<OutgoingAttachment>) {
        if (attachments.isEmpty()) return
        // An in-progress edit is a text-only operation; staging media ends it.
        if (editingMessage.value != null) cancelComposerAction()
        composerRevision++
        pendingAttachments.value = pendingAttachments.value + attachments
    }

    /** Removes one staged draft attachment (the thumbnail's remove action). */
    fun removePendingAttachment(attachment: OutgoingAttachment) {
        composerRevision++
        pendingAttachments.value = pendingAttachments.value - attachment
    }

    /**
     * Sends the staged draft attachments as one message; whatever is typed
     * becomes the caption (the input is consumed either way).
     */
    private fun sendDraftAttachments(caption: String, attachments: List<OutgoingAttachment>) {
        if (attachmentSendInProgress.value) return
        // Effects do not ride attachment sends; consume any staged one so it
        // cannot leak onto a later text send.
        val effectId = PendingSendEffect.effectId
        val sendRevision = composerRevision
        attachmentSendInProgress.value = true
        viewModelScope.launch {
            runCatching {
                val value = caption.ifEmpty { null }
                val chatItem = chat.value ?: chat.filterNotNull().first()
                val targetChatId = chatItem.preferredChatId
                if (chatItem.isSms) {
                    smsAttachmentSender.send(targetChatId, attachments, value, subject.value.trim().takeIf { it.isNotEmpty() })
                } else {
                    attachmentSender.send(targetChatId, attachments, value, subject.value.trim().takeIf { it.isNotEmpty() })
                }
            }.onSuccess { accepted ->
                if (PendingSendEffect.effectId == effectId) PendingSendEffect.effectId = null
                queueOutgoingSend(OutgoingSendEvent(accepted.messageId, effectId))
                val composerUnchanged = composerRevision == sendRevision
                pendingAttachments.value = pendingAttachments.value.filterNot { it in attachments }
                if (composerUnchanged) settleComposerAfterSend(sendRevision)
            }.onFailure { failure ->
                actionError.value = failure.message ?: "Could not send attachment"
            }
            attachmentSendInProgress.value = false
        }
    }

    /**
     * Clears the composer action after a successful send — or, when a reply
     * thread sheet is open on the sent target, re-stages the reply so the
     * next message continues the thread.
     */
    private fun settleComposerAfterSend(expectedRevision: Long? = null) {
        if (expectedRevision != null && composerRevision != expectedRevision) return
        input.value = ""
        subject.value = ""
        mentions.value = emptyList()
        editingMessage.value = null
        val open = replyThread.value
        val root = open?.messages?.firstOrNull { it.guid == open.rootGuid }
        if (open != null && root != null) {
            beginReply(root, open.part)
        } else {
            replyingTo.value = null
        }
        composerRevision++
    }

    fun beginReply(message: MessageItem, part: Long = 0L) {
        composerRevision++
        editingMessage.value = null
        val rootPart = message.replyToPart ?: part
        replyingTo.value = ReplyTarget(
            message = message,
            rootGuid = message.replyToGuid ?: message.guid,
            part = rootPart,
            partLocator = message.replyToPartLocator
                ?: message.replyPartLocators[rootPart]
                ?: fallbackReplyPartLocator(message, rootPart),
        )
    }

    fun openReplyThread(message: MessageItem) {
        val rootGuid = message.replyToGuid ?: message.guid
        val part = message.replyToPart ?: 0L
        replyThreadJob?.cancel()
        // Seed the focused thread with the tapped message so the pane is never
        // empty while the part-aware query runs — and so a failed query still
        // has something to show.
        replyThread.value = ReplyThreadState(
            rootGuid = rootGuid,
            part = part,
            messages = listOf(message),
            loading = true,
            sourceMessage = message,
        )
        beginReply(message)
        replyThreadJob = viewModelScope.launch(Dispatchers.IO) {
            val loaded = try {
                messageRepository.thread(sourceChatId(message), rootGuid, part)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Throwable) {
                val current = replyThread.value
                if (current?.rootGuid == rootGuid && current.part == part) {
                    actionError.value = failure.message ?: "Could not load replies"
                }
                emptyList()
            }
            val current = replyThread.value
            if (current?.rootGuid == rootGuid && current.part == part) {
                val messages = ensureThreadContains(loaded, message)
                replyThread.value = current.copy(messages = messages, loading = false)
                val root = messages.firstOrNull { it.guid == rootGuid } ?: message
                beginReply(root, part)
            }
        }
    }

    fun closeReplyThread() {
        replyThreadJob?.cancel()
        replyThreadJob = null
        replyThread.value = null
        if (input.value.isBlank() && editingMessage.value == null) {
            replyingTo.value = null
        }
    }

    fun consumeScreenEffect(messageId: Long) {
        if (screenEffect.value?.messageId == messageId) screenEffect.value = null
    }

    fun replyFromThread(message: MessageItem, part: Long) {
        beginReply(message, part)
    }

    fun beginEdit(message: MessageItem) {
        if (!message.isFromMe || message.text.isBlank() || message.unsent) return
        composerRevision++
        replyingTo.value = null
        editingMessage.value = message
        input.value = message.text
    }

    fun cancelComposerAction() {
        composerRevision++
        val wasEditing = editingMessage.value != null
        replyingTo.value = null
        editingMessage.value = null
        if (wasEditing) input.value = ""
    }

    fun react(message: MessageItem, part: Long, reactionIndex: Int, emoji: String? = null) {
        val display = emoji ?: TAPBACK_EMOJI.getOrNull(reactionIndex) ?: return
        val token = optimisticToken()
        updateOptimisticOverlay(message.guid) { overlay ->
            overlay.copy(reaction = OptimisticReaction(token, display))
        }
        viewModelScope.launch {
            runCatching {
                messageActions.react(
                    chatId = sourceChatId(message),
                    messageGuid = message.guid,
                    messageText = message.text,
                    messagePart = part,
                    reactionIndex = reactionIndex,
                    emoji = emoji,
                )
            }.onFailure { failure ->
                removeOptimisticReaction(message.guid, token)
                actionError.value = failure.message ?: "Could not send reaction"
            }
        }
    }

    fun unsend(message: MessageItem) {
        if (!message.isFromMe || message.unsent) return
        val token = optimisticToken()
        updateOptimisticOverlay(message.guid) { overlay ->
            overlay.copy(unsend = OptimisticUnsend(token))
        }
        viewModelScope.launch {
            runCatching { messageActions.unsend(sourceChatId(message), message.guid) }
                .onFailure { failure ->
                    removeOptimisticUnsend(message.guid, token)
                    actionError.value = failure.message ?: "Could not unsend message"
                }
        }
    }

    fun clearActionError() {
        actionError.value = null
    }

    fun startFaceTime() {
        if (faceTimeStarting.value) return
        faceTimeStarting.value = true
        viewModelScope.launch {
            runCatching { faceTimeCaller.start(preferredChatId()) }
                .onSuccess { faceTimeLaunch.value = it }
                .onFailure { failure ->
                    actionError.value = failure.message ?: "Could not start FaceTime"
                }
            faceTimeStarting.value = false
        }
    }

    fun consumeFaceTimeLaunch() {
        faceTimeLaunch.value = null
    }

    fun sendSticker(
        target: MessageItem,
        part: Long,
        sticker: OutgoingAttachment,
        transform: StickerTransform,
    ) {
        val token = optimisticToken()
        val optimisticGuid = "optimistic-sticker-$token"
        val optimisticSticker = OptimisticSticker(
            token = token,
            placement = StickerPlacement(
                reactionGuid = "optimistic-reaction-$token",
                attachmentGuid = optimisticGuid,
                targetPart = part,
                messageWidth = transform.messageWidth,
                normalizedX = transform.normalizedX,
                normalizedY = transform.normalizedY,
                rotation = transform.rotation,
                scale = transform.scale,
                effectType = transform.effectType,
                downloaded = true,
            ),
            file = sticker.file,
        )
        updateOptimisticOverlay(target.guid) { overlay ->
            overlay.copy(stickers = overlay.stickers + optimisticSticker)
        }
        viewModelScope.launch {
            runCatching {
                stickerSender.send(
                    sourceChatId(target),
                    target.guid,
                    part,
                    target.text,
                    sticker,
                    transform,
                )
            }.onSuccess { accepted ->
                confirmOptimisticSticker(target.guid, token, accepted.attachmentGuid)
            }.onFailure { failure ->
                removeOptimisticSticker(target.guid, token)
                actionError.value = failure.message ?: "Could not send sticker"
            }
        }
    }

    private fun sendEdit(message: MessageItem, newText: String, sendRevision: Long) {
        val token = optimisticToken()
        updateOptimisticOverlay(message.guid) { overlay ->
            overlay.copy(edit = OptimisticEdit(token, newText))
        }
        PendingSendEffect.effectId = null
        settleComposerAfterSend(sendRevision)
        val settledRevision = composerRevision
        viewModelScope.launch {
            runCatching { messageActions.edit(sourceChatId(message), message.guid, newText) }
                .onFailure { failure ->
                    removeOptimisticEdit(message.guid, token)
                    if (
                        composerRevision == settledRevision &&
                        input.value.isBlank() &&
                        editingMessage.value == null
                    ) {
                        editingMessage.value = message
                        input.value = newText
                        composerRevision++
                    }
                    actionError.value = failure.message ?: "Could not edit message"
                }
        }
    }

    private fun optimisticToken(): Long = ++nextOptimisticToken

    private fun updateOptimisticOverlay(
        messageGuid: String,
        transform: (OptimisticMessageOverlay) -> OptimisticMessageOverlay,
    ) {
        val current = optimisticMessageOverlays.value
        val updated = transform(current[messageGuid] ?: OptimisticMessageOverlay())
        optimisticMessageOverlays.value = if (updated.isEmpty) {
            current - messageGuid
        } else {
            current + (messageGuid to updated)
        }
    }

    private fun removeOptimisticEdit(messageGuid: String, token: Long) {
        updateOptimisticOverlay(messageGuid) { overlay ->
            if (overlay.edit?.token == token) overlay.copy(edit = null) else overlay
        }
    }

    private fun removeOptimisticReaction(messageGuid: String, token: Long) {
        updateOptimisticOverlay(messageGuid) { overlay ->
            if (overlay.reaction?.token == token) overlay.copy(reaction = null) else overlay
        }
    }

    private fun removeOptimisticUnsend(messageGuid: String, token: Long) {
        updateOptimisticOverlay(messageGuid) { overlay ->
            if (overlay.unsend?.token == token) overlay.copy(unsend = null) else overlay
        }
    }

    private fun removeOptimisticSticker(messageGuid: String, token: Long) {
        updateOptimisticOverlay(messageGuid) { overlay ->
            overlay.copy(stickers = overlay.stickers.filterNot { it.token == token })
        }
    }

    private fun confirmOptimisticSticker(messageGuid: String, token: Long, attachmentGuid: String) {
        updateOptimisticOverlay(messageGuid) { overlay ->
            overlay.copy(
                stickers = overlay.stickers.map { sticker ->
                    if (sticker.token == token) {
                        sticker.copy(
                            placement = sticker.placement.copy(attachmentGuid = attachmentGuid),
                            expectedAttachmentGuid = attachmentGuid,
                        )
                    } else {
                        sticker
                    }
                },
            )
        }
        reconcileOptimisticOverlays(messages.value)
    }

    private fun reconcileOptimisticOverlays(list: List<MessageItem>) {
        val current = optimisticMessageOverlays.value
        if (current.isEmpty()) return
        val persistedByGuid = list.associateBy { it.guid }
        val reconciled = current.mapNotNull { (guid, overlay) ->
            val persisted = persistedByGuid[guid] ?: return@mapNotNull guid to overlay
            val updated = overlay.copy(
                edit = overlay.edit?.takeUnless { persisted.edited && persisted.text == it.text },
                reaction = overlay.reaction?.takeUnless { persisted.reactionEmoji == it.emoji },
                unsend = overlay.unsend?.takeUnless { persisted.unsent },
                stickers = overlay.stickers.filterNot { sticker ->
                    sticker.expectedAttachmentGuid?.let { expected ->
                        persisted.stickers.any { it.attachmentGuid == expected }
                    } == true
                },
            )
            if (updated.isEmpty) null else guid to updated
        }.toMap()
        if (reconciled != current) optimisticMessageOverlays.value = reconciled
    }

    private fun applyOptimisticOverlays(
        list: List<MessageItem>,
        overlays: Map<String, OptimisticMessageOverlay>,
    ): List<MessageItem> = list.map { message ->
        val overlay = overlays[message.guid] ?: return@map message
        message.copy(
            text = overlay.edit?.text ?: message.text,
            edited = message.edited || overlay.edit != null,
            unsent = message.unsent || overlay.unsend != null,
            reactionEmoji = overlay.reaction?.emoji ?: message.reactionEmoji,
            stickers = message.stickers + overlay.stickers.map { it.placement },
        )
    }

    /**
     * Page in older history. Triggered when the user scrolls to the top of
     * the reversed message list; guarded so it runs one page at a time.
     */
    fun loadOlder() {
        if (loadingOlder.value || endReached) return
        val oldestId = messages.value.firstOrNull()?.id ?: return
        loadingOlder.value = true
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val older = messageRepository.loadMore(chatId, before = oldestId, count = PAGE_SIZE)
                if (older.size < PAGE_SIZE) endReached = true
            } finally {
                loadingOlder.value = false
            }
        }
    }

    override fun onCleared() {
        messageRepository.release(chatId)
    }

    companion object {
        fun factory(
            chatId: Long,
            chatListRepository: ChatListRepository,
            messageRepository: MessageListRepository,
            sender: Sender,
            messageActions: MessageActions,
            attachmentSender: AttachmentSender,
            stickerSender: StickerSender,
            typingRepository: TypingRepository,
            readReceiptSender: ReadReceiptSender,
            faceTimeCaller: FaceTimeCaller = FaceTimeCaller {
                error("FaceTime requires an active Apple push connection")
            },
            smsSender: SmsSender = SmsBridge.sender,
            smsAttachmentSender: AttachmentSender = SmsBridge.attachmentSender,
            initialInput: String? = null,
        ): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                ChatViewModel(
                    chatId,
                    chatListRepository,
                    messageRepository,
                    sender,
                    messageActions,
                    faceTimeCaller,
                    attachmentSender,
                    stickerSender,
                    typingRepository,
                    readReceiptSender,
                    smsSender = smsSender,
                    smsAttachmentSender = smsAttachmentSender,
                    initialInput = initialInput,
                )
            }
        }
    }
}

private val TAPBACK_EMOJI = listOf("❤️", "👍", "👎", "😂", "‼️", "❓")

/** History-import timestamps predate opening the transcript; live arrivals do not. */
internal fun isLiveIncomingRead(openedAtMs: Long, incomingDateMs: Long): Boolean =
    incomingDateMs >= openedAtMs

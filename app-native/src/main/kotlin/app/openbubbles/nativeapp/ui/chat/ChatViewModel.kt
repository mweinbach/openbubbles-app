package app.openbubbles.nativeapp.ui.chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import app.openbubbles.nativeapp.data.AttachmentSender
import app.openbubbles.nativeapp.data.ChatListItem
import app.openbubbles.nativeapp.data.ChatListRepository
import app.openbubbles.nativeapp.data.FaceTimeCaller
import app.openbubbles.nativeapp.data.FaceTimeLaunch
import app.openbubbles.nativeapp.data.MessageItem
import app.openbubbles.nativeapp.data.MessageActions
import app.openbubbles.nativeapp.data.MessageListRepository
import app.openbubbles.nativeapp.data.OutgoingAttachment
import app.openbubbles.nativeapp.data.ReadReceiptSender
import app.openbubbles.nativeapp.data.Sender
import app.openbubbles.nativeapp.data.SmsSender
import app.openbubbles.nativeapp.data.StickerSender
import app.openbubbles.nativeapp.data.StickerTransform
import app.openbubbles.nativeapp.data.TRANSCRIPT_OPEN_LIMIT
import app.openbubbles.nativeapp.data.TypingRepository
import app.openbubbles.nativeapp.sms.SmsBridge
import kotlinx.coroutines.CancellationException
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

data class ChatUiState(
    val chat: ChatListItem? = null,
    /** Ascending by time (oldest first); the screen reverses for layout. */
    val messages: List<MessageItem> = emptyList(),
    val input: String = "",
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
    /** Emitted only after the staged outgoing row is present in [messages]. */
    val outgoingSendEvent: OutgoingSendEvent? = null,
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
    private val smsAttachmentRouter: suspend (Long, List<OutgoingAttachment>, String?) -> Boolean =
        SmsBridge::routeAttachmentsIfSmsChat,
    initialInput: String? = null,
) : ViewModel() {

    init {
        // Opening a conversation clears its unread badge and mirrors the
        // receipt to Apple/the user's other devices when connected.
        viewModelScope.launch { readReceiptSender.markRead(chatId, null) }
    }

    private val input = MutableStateFlow(initialInput.orEmpty())
    private val pendingAttachments = MutableStateFlow<List<OutgoingAttachment>>(emptyList())
    private val loadingOlder = MutableStateFlow(false)
    private val replyingTo = MutableStateFlow<ReplyTarget?>(null)
    private val replyThread = MutableStateFlow<ReplyThreadState?>(null)
    private val editingMessage = MutableStateFlow<MessageItem?>(null)
    private val actionError = MutableStateFlow<String?>(null)
    private val faceTimeStarting = MutableStateFlow(false)
    private val faceTimeLaunch = MutableStateFlow<FaceTimeLaunch?>(null)
    private val textSendInProgress = MutableStateFlow(false)
    private val outgoingSendEvent = MutableStateFlow<OutgoingSendEvent?>(null)
    private var endReached = false
    private var replyThreadJob: Job? = null
    private var composerRevision = 0L
    private var pendingOutgoingSendEvent: OutgoingSendEvent? = null

    /** Message ids whose send effect has already been played (once each). */
    private val playedEffectMessageIds = mutableSetOf<Long>()
    private var effectBaselineInitialized = false
    private var lastMarkedIncomingGuid: String? = null
    private var readBaselineInitialized = false

    private val screenEffect = MutableStateFlow<ScreenEffectTrigger?>(null)

    private val chat: StateFlow<ChatListItem?> =
        chatListRepository.chats()
            .map { chats ->
                chats.firstOrNull { item -> item.id == chatId || chatId in item.memberChatIds }
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private val messages: StateFlow<List<MessageItem>> =
        messageRepository.messages(chatId, limit = INITIAL_LIMIT, before = null)
            .onEach { list ->
                observeMessageEffects(list)
                observeIncomingReadState(list)
                observePendingOutgoingSend(list)
            }
            .stateIn(
                viewModelScope,
                SharingStarted.Eagerly,
                messageRepository.cached(chatId),
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
        }.combine(textSendInProgress) { state, sending ->
            state.copy(textSendInProgress = sending)
        }.combine(outgoingSendEvent) { state, event ->
            state.copy(outgoingSendEvent = event)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ChatUiState())

    fun onInputChange(value: String) {
        if (input.value != value) composerRevision++
        input.value = value
    }

    fun sendMessage() {
        val text = input.value.trim()
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
        textSendInProgress.value = true
        viewModelScope.launch {
            runCatching {
                val chatItem = chat.value ?: chat.filterNotNull().first()
                val targetChatId = chatItem.preferredChatId
                when {
                    editing != null -> {
                        messageActions.edit(sourceChatId(editing), editing.guid, text)
                        null
                    }
                    chatItem.isSms -> smsSender.send(targetChatId, text)
                    reply != null -> sender.sendReply(
                        sourceChatId(reply.message),
                        text,
                        reply.rootGuid,
                        reply.partLocator,
                    )
                    effectId == null -> sender.send(targetChatId, text)
                    else -> sender.sendWithEffect(targetChatId, text, effectId)
                }
            }.onSuccess { accepted ->
                if (accepted != null) {
                    if (PendingSendEffect.effectId == effectId) PendingSendEffect.effectId = null
                    queueOutgoingSend(OutgoingSendEvent(accepted.messageId, effectId))
                }
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
        pendingAttachments.value = pendingAttachments.value + attachments
    }

    /** Removes one staged draft attachment (the thumbnail's remove action). */
    fun removePendingAttachment(attachment: OutgoingAttachment) {
        pendingAttachments.value = pendingAttachments.value - attachment
    }

    /**
     * Sends the staged draft attachments as one message; whatever is typed
     * becomes the caption (the input is consumed either way).
     */
    private fun sendDraftAttachments(caption: String, attachments: List<OutgoingAttachment>) {
        // Effects do not ride attachment sends; consume any staged one so it
        // cannot leak onto a later text send.
        PendingSendEffect.effectId = null
        input.value = ""
        pendingAttachments.value = emptyList()
        viewModelScope.launch {
            runCatching {
                val value = caption.ifEmpty { null }
                val targetChatId = preferredChatId()
                if (!smsAttachmentRouter(targetChatId, attachments, value)) {
                    attachmentSender.send(targetChatId, attachments, value)
                }
            }.onSuccess {
                settleComposerAfterSend()
            }.onFailure { failure ->
                input.value = caption
                pendingAttachments.value = attachments
                actionError.value = failure.message ?: "Could not send attachment"
            }
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
                actionError.value = failure.message ?: "Could not send reaction"
            }
        }
    }

    fun unsend(message: MessageItem) {
        if (!message.isFromMe || message.unsent) return
        viewModelScope.launch {
            runCatching { messageActions.unsend(sourceChatId(message), message.guid) }
                .onFailure { failure ->
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
            }.onFailure { failure ->
                actionError.value = failure.message ?: "Could not send sticker"
            }
        }
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
        super.onCleared()
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
                    initialInput = initialInput,
                )
            }
        }
    }
}

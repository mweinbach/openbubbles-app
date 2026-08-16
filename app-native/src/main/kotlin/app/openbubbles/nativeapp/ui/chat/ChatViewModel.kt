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
import app.openbubbles.nativeapp.data.StickerSender
import app.openbubbles.nativeapp.data.StickerTransform
import app.openbubbles.nativeapp.data.TypingRepository
import app.openbubbles.nativeapp.sms.SmsBridge
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class ChatUiState(
    val chat: ChatListItem? = null,
    /** Ascending by time (oldest first); the screen reverses for layout. */
    val messages: List<MessageItem> = emptyList(),
    val input: String = "",
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
    /** Part-aware reply thread currently expanded in the conversation sheet. */
    val replyThread: ReplyThreadState? = null,
    /** My text message currently being edited. */
    val editingMessage: MessageItem? = null,
    /** Visible operation failure; cleared after the screen presents it. */
    val actionError: String? = null,
    val faceTimeStarting: Boolean = false,
    /** One-shot handoff to the Android call activity. */
    val faceTimeLaunch: FaceTimeLaunch? = null,
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
)

/** Identifies the message whose send effect should play (once). */
data class ScreenEffectTrigger(
    val messageId: Long,
    val effectId: String,
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

private const val INITIAL_LIMIT = 30
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
    private val smsRouter: suspend (Long, String) -> Boolean = SmsBridge::routeIfSmsChat,
    private val smsAttachmentRouter: suspend (Long, OutgoingAttachment, String?) -> Boolean =
        SmsBridge::routeAttachmentIfSmsChat,
    initialInput: String? = null,
) : ViewModel() {

    init {
        // Opening a conversation clears its unread badge and mirrors the
        // receipt to Apple/the user's other devices when connected.
        viewModelScope.launch { readReceiptSender.markRead(chatId, null) }
    }

    private val input = MutableStateFlow(initialInput.orEmpty())
    private val loadingOlder = MutableStateFlow(false)
    private val replyingTo = MutableStateFlow<ReplyTarget?>(null)
    private val replyThread = MutableStateFlow<ReplyThreadState?>(null)
    private val editingMessage = MutableStateFlow<MessageItem?>(null)
    private val actionError = MutableStateFlow<String?>(null)
    private val faceTimeStarting = MutableStateFlow(false)
    private val faceTimeLaunch = MutableStateFlow<FaceTimeLaunch?>(null)
    private var endReached = false
    private var replyThreadJob: Job? = null

    /** Message ids whose send effect has already been played (once each). */
    private val playedEffectMessageIds = mutableSetOf<Long>()
    private var effectBaselineInitialized = false

    private val screenEffect = MutableStateFlow<ScreenEffectTrigger?>(null)

    private val messages: StateFlow<List<MessageItem>> =
        messageRepository.messages(chatId, limit = INITIAL_LIMIT, before = null)
            .onEach(::observeMessageEffects)
            .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    private val chat: StateFlow<ChatListItem?> =
        chatListRepository.chats()
            .map { chats -> chats.firstOrNull { it.id == chatId } }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private val typingSenders: StateFlow<List<String>> =
        typingRepository.typing()
            .map { entries -> entries.filter { it.chatId == chatId }.map { it.senderAddress } }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

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
            state.copy(replyThread = thread)
        }.combine(editingMessage) { state, editing ->
            state.copy(editingMessage = editing)
        }.combine(actionError) { state, error ->
            state.copy(actionError = error)
        }.combine(faceTimeStarting) { state, starting ->
            state.copy(faceTimeStarting = starting)
        }.combine(faceTimeLaunch) { state, launch ->
            state.copy(faceTimeLaunch = launch)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ChatUiState())

    fun onInputChange(value: String) {
        input.value = value
    }

    fun sendMessage() {
        val text = input.value.trim()
        if (text.isEmpty()) return
        // Consume any effect staged by the picker for this send.
        val effectId = PendingSendEffect.effectId
        PendingSendEffect.effectId = null
        input.value = ""
        viewModelScope.launch {
            runCatching {
                val editing = editingMessage.value
                val reply = replyingTo.value
                when {
                    editing != null -> messageActions.edit(chatId, editing.guid, text)
                    // SIM-routed chats (isRpSms) send over the modem; everything else
                    // goes through the APNs iMessage sender.
                    smsRouter(chatId, text) -> Unit
                    reply != null -> sender.sendReply(
                        chatId,
                        text,
                        reply.rootGuid,
                        reply.partLocator,
                    )
                    effectId == null -> sender.send(chatId, text)
                    else -> sender.sendWithEffect(chatId, text, effectId)
                }
            }.onSuccess {
                editingMessage.value = null
                replyingTo.value = null
            }.onFailure { failure ->
                input.value = text
                actionError.value = failure.message ?: "Message operation failed"
            }
        }
    }

    fun beginReply(message: MessageItem, part: Long = 0L) {
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
        replyThread.value = ReplyThreadState(rootGuid, part)
        replyThreadJob = viewModelScope.launch(Dispatchers.IO) {
            val messages = try {
                messageRepository.thread(chatId, rootGuid, part)
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
                replyThread.value = ReplyThreadState(rootGuid, part, messages, loading = false)
            }
        }
    }

    fun closeReplyThread() {
        replyThreadJob?.cancel()
        replyThreadJob = null
        replyThread.value = null
    }

    fun consumeScreenEffect(messageId: Long) {
        if (screenEffect.value?.messageId == messageId) screenEffect.value = null
    }

    fun replyFromThread(message: MessageItem, part: Long) {
        closeReplyThread()
        beginReply(message, part)
    }

    fun beginEdit(message: MessageItem) {
        if (!message.isFromMe || message.text.isBlank() || message.unsent) return
        replyingTo.value = null
        editingMessage.value = message
        input.value = message.text
    }

    fun cancelComposerAction() {
        val wasEditing = editingMessage.value != null
        replyingTo.value = null
        editingMessage.value = null
        if (wasEditing) input.value = ""
    }

    fun react(message: MessageItem, part: Long, reactionIndex: Int, emoji: String? = null) {
        viewModelScope.launch {
            runCatching {
                messageActions.react(
                    chatId = chatId,
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
            runCatching { messageActions.unsend(chatId, message.guid) }
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
            runCatching { faceTimeCaller.start(chatId) }
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

    /**
     * Sends a picked attachment; whatever is typed becomes the caption (the
     * input is consumed either way).
     */
    fun sendAttachment(attachment: OutgoingAttachment) {
        val caption = input.value.trim()
        input.value = ""
        viewModelScope.launch {
            runCatching {
                val value = caption.ifEmpty { null }
                if (!smsAttachmentRouter(chatId, attachment, value)) {
                    attachmentSender.send(chatId, attachment, value)
                }
            }.onFailure { failure ->
                input.value = caption
                actionError.value = failure.message ?: "Could not send attachment"
            }
        }
    }

    fun sendSticker(
        target: MessageItem,
        part: Long,
        sticker: OutgoingAttachment,
        transform: StickerTransform,
    ) {
        viewModelScope.launch {
            runCatching {
                stickerSender.send(chatId, target.guid, part, target.text, sticker, transform)
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
                    initialInput = initialInput,
                )
            }
        }
    }
}

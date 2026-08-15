package app.openbubbles.nativeapp.ui.chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import app.openbubbles.nativeapp.data.AttachmentSender
import app.openbubbles.nativeapp.data.ChatListItem
import app.openbubbles.nativeapp.data.ChatListRepository
import app.openbubbles.nativeapp.data.MessageItem
import app.openbubbles.nativeapp.data.MessageActions
import app.openbubbles.nativeapp.data.MessageListRepository
import app.openbubbles.nativeapp.data.OutgoingAttachment
import app.openbubbles.nativeapp.data.ReadReceiptSender
import app.openbubbles.nativeapp.data.Sender
import app.openbubbles.nativeapp.data.TypingRepository
import app.openbubbles.nativeapp.sms.SmsBridge
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
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
    /** Message selected as the root of the next threaded reply. */
    val replyingTo: MessageItem? = null,
    /** My text message currently being edited. */
    val editingMessage: MessageItem? = null,
    /** Visible operation failure; cleared after the screen presents it. */
    val actionError: String? = null,
) {
    val initialLoading: Boolean get() = chat == null && messages.isEmpty()
}

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

class ChatViewModel(
    private val chatId: Long,
    private val chatListRepository: ChatListRepository,
    private val messageRepository: MessageListRepository,
    private val sender: Sender,
    private val messageActions: MessageActions,
    private val attachmentSender: AttachmentSender,
    typingRepository: TypingRepository,
    private val readReceiptSender: ReadReceiptSender,
    private val smsRouter: suspend (Long, String) -> Boolean = SmsBridge::routeIfSmsChat,
    private val smsAttachmentRouter: suspend (Long, OutgoingAttachment, String?) -> Boolean =
        SmsBridge::routeAttachmentIfSmsChat,
) : ViewModel() {

    init {
        // Opening a conversation clears its unread badge and mirrors the
        // receipt to Apple/the user's other devices when connected.
        viewModelScope.launch { readReceiptSender.markRead(chatId) }
    }

    private val input = MutableStateFlow("")
    private val loadingOlder = MutableStateFlow(false)
    private val replyingTo = MutableStateFlow<MessageItem?>(null)
    private val editingMessage = MutableStateFlow<MessageItem?>(null)
    private val actionError = MutableStateFlow<String?>(null)
    private var endReached = false

    /** Message ids whose send effect has already been played (once each). */
    private val playedEffectMessageIds = mutableSetOf<Long>()

    private val screenEffect = MutableStateFlow<ScreenEffectTrigger?>(null)

    private val messages: StateFlow<List<MessageItem>> =
        messageRepository.messages(chatId, limit = INITIAL_LIMIT, before = null)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val chat: StateFlow<ChatListItem?> =
        chatListRepository.chats()
            .map { chats -> chats.firstOrNull { it.id == chatId } }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private val typingSenders: StateFlow<List<String>> =
        typingRepository.typing()
            .map { entries -> entries.filter { it.chatId == chatId }.map { it.senderAddress } }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    init {
        // Watch the transcript so a *new* newest message with a send effect
        // (incoming or outgoing) triggers the full-screen overlay exactly once.
        viewModelScope.launch {
            messages.collect { list ->
                val newest = list.lastOrNull() ?: return@collect
                val styleId = newest.expressiveSendStyleId ?: return@collect
                if (playedEffectMessageIds.add(newest.id)) {
                    screenEffect.value = ScreenEffectTrigger(newest.id, styleId)
                }
            }
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
        }.combine(editingMessage) { state, editing ->
            state.copy(editingMessage = editing)
        }.combine(actionError) { state, error ->
            state.copy(actionError = error)
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
                    reply != null -> sender.sendReply(chatId, text, reply.replyToGuid ?: reply.guid)
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

    fun beginReply(message: MessageItem) {
        editingMessage.value = null
        replyingTo.value = message
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

    fun react(message: MessageItem, reactionIndex: Int, emoji: String? = null) {
        viewModelScope.launch {
            runCatching {
                messageActions.react(
                    chatId = chatId,
                    messageGuid = message.guid,
                    messageText = message.text,
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

    /**
     * Page in older history. Triggered when the user scrolls to the top of
     * the reversed message list; guarded so it runs one page at a time.
     */
    fun loadOlder() {
        if (loadingOlder.value || endReached) return
        val oldestId = messages.value.firstOrNull()?.id ?: return
        loadingOlder.value = true
        viewModelScope.launch(Dispatchers.Default) {
            try {
                // Brief delay so the "loading older" indicator is visible with fake data.
                delay(250)
                val older = messageRepository.loadMore(chatId, before = oldestId, count = PAGE_SIZE)
                if (older.isEmpty()) endReached = true
            } finally {
                loadingOlder.value = false
            }
        }
    }

    companion object {
        fun factory(
            chatId: Long,
            chatListRepository: ChatListRepository,
            messageRepository: MessageListRepository,
            sender: Sender,
            messageActions: MessageActions,
            attachmentSender: AttachmentSender,
            typingRepository: TypingRepository,
            readReceiptSender: ReadReceiptSender,
        ): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                ChatViewModel(
                    chatId,
                    chatListRepository,
                    messageRepository,
                    sender,
                    messageActions,
                    attachmentSender,
                    typingRepository,
                    readReceiptSender,
                )
            }
        }
    }
}

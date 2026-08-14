package app.openbubbles.nativeapp.ui.chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import app.openbubbles.nativeapp.data.ChatListItem
import app.openbubbles.nativeapp.data.ChatListRepository
import app.openbubbles.nativeapp.data.MessageItem
import app.openbubbles.nativeapp.data.MessageListRepository
import app.openbubbles.nativeapp.data.Sender
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
) {
    val initialLoading: Boolean get() = chat == null && messages.isEmpty()
}

private const val INITIAL_LIMIT = 30
private const val PAGE_SIZE = 20

class ChatViewModel(
    private val chatId: Long,
    private val chatListRepository: ChatListRepository,
    private val messageRepository: MessageListRepository,
    private val sender: Sender,
) : ViewModel() {

    init {
        // Opening a conversation clears its unread badge.
        chatListRepository.markRead(chatId)
    }

    private val input = MutableStateFlow("")
    private val loadingOlder = MutableStateFlow(false)
    private var endReached = false

    private val messages: StateFlow<List<MessageItem>> =
        messageRepository.messages(chatId, limit = INITIAL_LIMIT, before = null)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val chat: StateFlow<ChatListItem?> =
        chatListRepository.chats()
            .map { chats -> chats.firstOrNull { it.id == chatId } }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val uiState: StateFlow<ChatUiState> =
        combine(messages, chat, input, loadingOlder) { messages, chat, input, loadingOlder ->
            ChatUiState(chat = chat, messages = messages, input = input, loadingOlder = loadingOlder)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ChatUiState())

    fun onInputChange(value: String) {
        input.value = value
    }

    fun sendMessage() {
        val text = input.value.trim()
        if (text.isEmpty()) return
        input.value = ""
        viewModelScope.launch { sender.send(chatId, text) }
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
        ): ViewModelProvider.Factory = viewModelFactory {
            initializer { ChatViewModel(chatId, chatListRepository, messageRepository, sender) }
        }
    }
}

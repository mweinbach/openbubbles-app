package app.openbubbles.nativeapp.ui.chatlist

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import app.openbubbles.nativeapp.data.ChatListItem
import app.openbubbles.nativeapp.data.ChatListRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

data class ChatListUiState(
    val loading: Boolean = false,
    val query: String = "",
    val pinned: List<ChatListItem> = emptyList(),
    val chats: List<ChatListItem> = emptyList(),
    val archived: List<ChatListItem> = emptyList(),
) {
    val isEmpty: Boolean get() = !loading && pinned.isEmpty() && chats.isEmpty() && archived.isEmpty()
}

class ChatListViewModel(
    private val repository: ChatListRepository,
) : ViewModel() {

    private val query = MutableStateFlow("")

    val uiState: StateFlow<ChatListUiState> =
        combine(repository.chats(), query) { chats, rawQuery ->
            val trimmed = rawQuery.trim()
            val filtered = if (trimmed.isEmpty()) {
                chats
            } else {
                chats.filter { chat ->
                    chat.title.contains(trimmed, ignoreCase = true) ||
                        chat.snippet?.contains(trimmed, ignoreCase = true) == true
                }
            }
            ChatListUiState(
                loading = false,
                query = rawQuery,
                pinned = filtered.filter { it.pinned && !it.archived }.sortedByDescending { it.date },
                chats = filtered.filter { !it.pinned && !it.archived }.sortedByDescending { it.date },
                archived = filtered.filter { it.archived }.sortedByDescending { it.date },
            )
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = ChatListUiState(loading = true),
        )

    fun onQueryChange(value: String) {
        query.value = value
    }

    fun markRead(id: Long) = repository.markRead(id)

    fun togglePinned(chat: ChatListItem) = repository.setPinned(chat.id, !chat.pinned)

    fun toggleMuted(chat: ChatListItem) = repository.setMuted(chat.id, !chat.muted)

    fun toggleArchived(chat: ChatListItem) = repository.setArchived(chat.id, !chat.archived)

    fun delete(chat: ChatListItem) = repository.delete(chat.id)

    companion object {
        fun factory(repository: ChatListRepository): ViewModelProvider.Factory = viewModelFactory {
            initializer { ChatListViewModel(repository) }
        }
    }
}

package app.openbubbles.nativeapp.ui.chatlist

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import app.openbubbles.nativeapp.data.ChatListItem
import app.openbubbles.nativeapp.data.ChatListRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

data class ChatListUiState(
    val loading: Boolean = false,
    val pinned: List<ChatListItem> = emptyList(),
    val chats: List<ChatListItem> = emptyList(),
    val archived: List<ChatListItem> = emptyList(),
) {
    /** Inbox emptiness ignores archived rows — those live under Settings. */
    val isEmpty: Boolean get() = !loading && pinned.isEmpty() && chats.isEmpty()
}

class ChatListViewModel(
    private val repository: ChatListRepository,
) : ViewModel() {

    val uiState: StateFlow<ChatListUiState> =
        repository.chats().map { chats ->
            ChatListUiState(
                loading = false,
                // Core already returns pins in their persisted pinIndex order.
                // Re-sorting by message date made pin placement unstable.
                pinned = chats.filter { it.pinned && !it.archived },
                chats = chats.filter { !it.pinned && !it.archived }.sortedByDescending { it.date },
                archived = chats.filter { it.archived }.sortedByDescending { it.date },
            )
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = ChatListUiState(loading = true),
        )

    fun markRead(id: Long) = repository.markRead(id)

    fun togglePinned(chat: ChatListItem) = repository.setPinned(chat.id, !chat.pinned)

    fun toggleMuted(chat: ChatListItem) = repository.setMuted(chat.id, !chat.muted)

    fun muteFor(chat: ChatListItem, durationMs: Long) =
        repository.setMutedUntil(chat.id, System.currentTimeMillis() + durationMs)

    fun toggleArchived(chat: ChatListItem) = repository.setArchived(chat.id, !chat.archived)

    fun archive(ids: Collection<Long>) = ids.forEach { repository.setArchived(it, true) }

    fun unarchive(ids: Collection<Long>) = ids.forEach { repository.setArchived(it, false) }

    fun delete(chat: ChatListItem) = repository.delete(chat.id)

    fun delete(ids: Collection<Long>) = ids.forEach(repository::delete)

    companion object {
        fun factory(repository: ChatListRepository): ViewModelProvider.Factory = viewModelFactory {
            initializer { ChatListViewModel(repository) }
        }
    }
}

package app.openbubbles.nativeapp.ui.chatlist

import app.openbubbles.nativeapp.data.ChatListItem
import app.openbubbles.nativeapp.data.ChatListRepository
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest

@OptIn(ExperimentalCoroutinesApi::class)
class ChatListViewModelTest {
    @Test
    fun `persisted pin order is not replaced by message date`() = runTest {
        val repository = PinOrderRepository(
            listOf(
                chat(1L, "Pinned first", date = 10L),
                chat(2L, "Pinned second", date = 20L),
            ),
        )
        val model = ChatListViewModel(repository)
        val state = model.uiState.first { !it.loading }

        assertEquals(
            listOf("Pinned first", "Pinned second"),
            state.pinned.map { it.title },
        )
    }

    private fun chat(id: Long, title: String, date: Long) = ChatListItem(
        id = id,
        title = title,
        snippet = null,
        date = date,
        unread = 0,
        pinned = true,
        avatarColor = 0L,
    )
}

private class PinOrderRepository(initial: List<ChatListItem>) : ChatListRepository {
    private val items = MutableStateFlow(initial)

    override fun chats(): Flow<List<ChatListItem>> = items
    override fun markRead(id: Long) = Unit
    override fun setPinned(id: Long, pinned: Boolean) = Unit
    override fun setMuted(id: Long, muted: Boolean) = Unit
    override fun setArchived(id: Long, archived: Boolean) = Unit
    override fun delete(id: Long) = Unit
}

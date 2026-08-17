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

    @Test
    fun `archived conversations do not keep the inbox from looking empty`() = runTest {
        val repository = PinOrderRepository(
            listOf(chat(1L, "Old thread", date = 10L, archived = true, pinned = false)),
        )
        val model = ChatListViewModel(repository)
        val state = model.uiState.first { !it.loading }

        assertEquals(true, state.isEmpty)
        assertEquals(listOf("Old thread"), state.archived.map { it.title })
    }

    @Test
    fun `batch archive and delete update the repository`() = runTest {
        val repository = RecordingChatListRepository()
        val model = ChatListViewModel(repository)
        model.archive(listOf(1L, 2L))
        model.unarchive(listOf(3L))
        model.delete(listOf(4L, 5L))

        assertEquals(listOf(1L to true, 2L to true, 3L to false), repository.archived)
        assertEquals(listOf(4L, 5L), repository.deleted)
    }

    @Test
    fun `send-from override and reset reach the repository`() = runTest {
        val repository = RecordingChatListRepository()
        val model = ChatListViewModel(repository)
        val item = chat(7L, "Group", date = 10L)

        model.setSenderOverride(item, "tel:+15550000000")
        model.setSenderOverride(item, null)

        assertEquals(
            listOf(7L to "tel:+15550000000", 7L to null),
            repository.senderOverrides,
        )
    }

    private fun chat(
        id: Long,
        title: String,
        date: Long,
        archived: Boolean = false,
        pinned: Boolean = true,
    ) = ChatListItem(
        id = id,
        title = title,
        snippet = null,
        date = date,
        unread = 0,
        pinned = pinned,
        avatarColor = 0L,
        archived = archived,
    )
}

private class RecordingChatListRepository : ChatListRepository {
    val archived = mutableListOf<Pair<Long, Boolean>>()
    val deleted = mutableListOf<Long>()
    val senderOverrides = mutableListOf<Pair<Long, String?>>()

    override fun chats(): Flow<List<ChatListItem>> = MutableStateFlow(emptyList())
    override fun markRead(id: Long) = Unit
    override fun setPinned(id: Long, pinned: Boolean) = Unit
    override fun setMuted(id: Long, muted: Boolean) = Unit
    override fun setArchived(id: Long, archived: Boolean) {
        this.archived += id to archived
    }
    override fun setSenderOverride(id: Long, handle: String?) {
        senderOverrides += id to handle
    }
    override fun delete(id: Long) {
        deleted += id
    }
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

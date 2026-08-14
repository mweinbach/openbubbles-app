package app.openbubbles.nativeapp.data

import kotlinx.coroutines.flow.Flow

/**
 * UI-facing data contracts for the native Android client.
 *
 * These mirror the shapes the `core` module repositories will expose; the UI
 * depends only on these interfaces so the fake implementations here can be
 * swapped for the real ones without touching any composable or ViewModel.
 *
 * All timestamps are epoch milliseconds; [MessageItem.id] and `before` keys
 * are monotonically increasing message ids (ascending in time).
 */
data class ChatListItem(
    val id: Long,
    val title: String,
    val snippet: String?,
    val date: Long,
    val unread: Int,
    val pinned: Boolean,
    val avatarColor: Long,
)

data class MessageItem(
    val id: Long,
    val text: String,
    val isFromMe: Boolean,
    val date: Long,
    val status: MessageStatus,
    val isGroupEvent: Boolean,
    val reactionEmoji: String?,
)

enum class MessageStatus { SENDING, SENT, DELIVERED, READ, FAILED }

interface ChatListRepository {
    fun chats(): Flow<List<ChatListItem>>
    fun markRead(id: Long)
}

interface MessageListRepository {
    fun messages(chatId: Long, limit: Int, before: Long?): Flow<List<MessageItem>>
    fun loadMore(chatId: Long, before: Long?, count: Int): List<MessageItem>
}

interface Sender {
    suspend fun send(chatId: Long, text: String)
}

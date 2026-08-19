package app.openbubbles.core.repo

import app.openbubbles.db.Chat
import app.openbubbles.db.Handle
import app.openbubbles.db.Message
import app.openbubbles.db.MyObjectBox
import io.objectbox.BoxStore
import org.junit.After
import org.junit.Before
import org.junit.Test
import java.io.File
import java.util.Date
import kotlin.test.assertEquals

/**
 * The chat-list projection batches its per-chat message lookups (last-read
 * dates for unread counts, target texts for reaction snippets) into single
 * queries per emission. These tests pin the batched path to the same
 * semantics as the original per-chat queries, including guid misses, and
 * keep the unbatched fallback (recentlyDeleted) honest.
 */
class ChatRepoProjectionBatchTest {

    private lateinit var store: BoxStore
    private lateinit var testDir: File

    @Before
    fun setUp() {
        testDir = java.nio.file.Files.createTempDirectory("ob-chat-batch-test").toFile()
        store = MyObjectBox.builder().directory(testDir).build()
    }

    @After
    fun tearDown() {
        store.close()
        testDir.deleteRecursively()
    }

    @Test
    fun `unread count only counts incoming messages after the last-read message`() {
        val friend = handle("friend@icloud.com")
        val chat = chat("chat-unread", friend)
        val read = incoming(chat, friend, "seen", 100L)
        incoming(chat, friend, "new one", 200L)
        incoming(chat, friend, "new two", 300L)
        markLatest(chat, unread = true, lastReadGuid = read.guid)

        assertEquals(2, ChatRepo(store).chats().single().unreadCount)
    }

    @Test
    fun `unread count falls back to all incoming when the last-read guid is gone`() {
        val friend = handle("friend2@icloud.com")
        val chat = chat("chat-unread-missing", friend)
        incoming(chat, friend, "one", 100L)
        incoming(chat, friend, "two", 200L)
        markLatest(chat, unread = true, lastReadGuid = "guid-that-no-longer-exists")

        assertEquals(2, ChatRepo(store).chats().single().unreadCount)
    }

    @Test
    fun `reaction snippet quotes the target message text`() {
        val friend = handle("friend3@icloud.com")
        val chat = chat("chat-reaction", friend)
        val target = incoming(chat, friend, "pizza tonight?", 100L)
        reaction(chat, friend, target.guid, 200L)
        markLatest(chat)

        assertEquals(
            "Someone loved “pizza tonight?”",
            ChatRepo(store).chats().single().snippet,
        )
    }

    @Test
    fun `reaction snippet degrades to a message when the target is gone`() {
        val friend = handle("friend4@icloud.com")
        val chat = chat("chat-reaction-missing", friend)
        reaction(chat, friend, "deleted-target-guid", 200L)
        markLatest(chat)

        assertEquals(
            "Someone loved “a message”",
            ChatRepo(store).chats().single().snippet,
        )
    }

    @Test
    fun `mixed list resolves every chat in one projection`() {
        val a = handle("a@icloud.com")
        val b = handle("b@icloud.com")
        val chatA = chat("chat-a", a)
        val seen = incoming(chatA, a, "old", 100L)
        incoming(chatA, a, "unread", 200L)
        markLatest(chatA, unread = true, lastReadGuid = seen.guid)

        val chatB = chat("chat-b", b)
        val target = incoming(chatB, b, "nice photo", 100L)
        reaction(chatB, b, target.guid, 300L)
        markLatest(chatB)

        val items = ChatRepo(store).chats().associateBy { it.guid }
        assertEquals(1, items.getValue("chat-a").unreadCount)
        assertEquals("Someone loved “nice photo”", items.getValue("chat-b").snippet)
    }

    @Test
    fun `recently deleted projection still resolves reaction snippets without the batch`() {
        val friend = handle("friend5@icloud.com")
        val chat = chat("chat-deleted", friend)
        val target = incoming(chat, friend, "bye", 100L)
        reaction(chat, friend, target.guid, 200L)
        markLatest(chat)
        chat.dateDeleted = Date(400L)
        store.boxFor(Chat::class.java).put(chat)

        assertEquals(
            "Someone loved “bye”",
            ChatRepo(store).recentlyDeleted().single().snippet,
        )
    }

    private fun handle(address: String): Handle = Handle().apply {
        this.address = address
        service = "iMessage"
        uniqueAddressAndService = "$address/$service"
    }.also(store.boxFor(Handle::class.java)::put)

    private fun chat(guid: String, handle: Handle): Chat {
        val chat = Chat().apply {
            this.guid = guid
            chatIdentifier = handle.address
            isRpSms = false
            handles.add(handle)
        }
        store.boxFor(Chat::class.java).put(chat)
        return chat
    }

    private fun incoming(chat: Chat, sender: Handle, text: String, timestamp: Long): Message {
        val message = Message().apply {
            guid = "msg-${chat.guid}-$timestamp"
            this.text = text
            dateCreated = Date(timestamp)
            isFromMe = false
            handleRelation.target = sender
            this.chat.target = chat
        }
        store.boxFor(Message::class.java).put(message)
        return message
    }

    private fun reaction(chat: Chat, sender: Handle, targetGuid: String, timestamp: Long): Message {
        val message = Message().apply {
            guid = "reaction-${chat.guid}-$timestamp"
            associatedMessageGuid = targetGuid
            associatedMessageType = "love"
            dateCreated = Date(timestamp)
            isFromMe = false
            this.chat.target = chat
        }
        store.boxFor(Message::class.java).put(message)
        return message
    }

    private fun markLatest(chat: Chat, unread: Boolean = false, lastReadGuid: String? = null) {
        val latest = store.boxFor(Message::class.java).all
            .filter { it.chat.targetId == chat.id }
            .maxByOrNull { it.dateCreated.time }
        chat.dbLatestMessage.target = latest
        chat.dbOnlyLatestMessageDate = latest?.dateCreated
        chat.hasUnreadMessage = unread
        chat.lastReadMessageGuid = lastReadGuid
        store.boxFor(Chat::class.java).put(chat)
    }
}

package app.openbubbles.core.repo

import app.openbubbles.core.contacts.ContactSync
import app.openbubbles.core.contacts.RawContact
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
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking

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
    fun `ordinary chat change only rebuilds its cached conversation projection`() {
        val firstHandle = handle("cached-first@icloud.com")
        val secondHandle = handle("cached-second@icloud.com")
        val first = chat("cached-first", firstHandle)
        val second = chat("cached-second", secondHandle)
        incoming(first, firstHandle, "first", 100L)
        incoming(second, secondHandle, "second", 200L)
        markLatest(first, unread = true)
        markLatest(second, unread = true)
        val repo = ChatRepo(store)

        assertEquals(2, repo.chats().size)
        val before = repo.projectionDiagnostics()
        store.boxFor(Chat::class.java).put(first.apply { displayName = "Renamed first" })

        val items = repo.chats().associateBy { it.guid }
        val after = repo.projectionDiagnostics()
        assertEquals("Renamed first", items.getValue("cached-first").title)
        assertEquals(1, items.getValue("cached-second").unreadCount)
        assertEquals(1L, after.projectedChats - before.projectedChats)
        assertEquals(1L, after.unreadQueries - before.unreadQueries)
    }

    @Test
    fun `read conversations never issue an unread count query`() {
        val friend = handle("already-read@icloud.com")
        val chat = chat("already-read", friend)
        incoming(chat, friend, "already read", 100L)
        markLatest(chat, unread = false)
        val repo = ChatRepo(store)

        assertEquals(0, repo.chats().single().unreadCount)
        store.boxFor(Chat::class.java).put(chat.apply { displayName = "Still read" })
        assertEquals("Still read", repo.chats().single().title)
        assertEquals(0L, repo.projectionDiagnostics().unreadQueries)
    }

    @Test
    fun `new message only rebuilds its conversation and batches every unread count`() {
        val firstHandle = handle("new-first@icloud.com")
        val secondHandle = handle("new-second@icloud.com")
        val first = chat("new-first", firstHandle)
        val second = chat("new-second", secondHandle)
        incoming(first, firstHandle, "first", 100L)
        incoming(second, secondHandle, "second", 200L)
        markLatest(first, unread = true)
        markLatest(second, unread = true)
        val repo = ChatRepo(store)
        repo.chats()
        val before = repo.projectionDiagnostics()

        incoming(first, firstHandle, "newest", 300L)
        markLatest(first, unread = true)

        val items = repo.chats().associateBy { it.guid }
        val after = repo.projectionDiagnostics()
        assertEquals("newest", items.getValue("new-first").snippet)
        assertEquals(2, items.getValue("new-first").unreadCount)
        assertEquals(1, items.getValue("new-second").unreadCount)
        assertEquals(1L, after.projectedChats - before.projectedChats)
        assertEquals(1L, after.unreadQueries - before.unreadQueries)
    }

    @Test
    fun `removing an older unread message updates its cached count without reprojection`() {
        val friend = handle("remove-older@icloud.com")
        val chat = chat("remove-older", friend)
        val older = incoming(chat, friend, "older", 100L)
        incoming(chat, friend, "latest", 200L)
        markLatest(chat, unread = true)
        val repo = ChatRepo(store)
        assertEquals(2, repo.chats().single().unreadCount)
        val before = repo.projectionDiagnostics()

        store.boxFor(Message::class.java).remove(older.id)
        store.boxFor(Chat::class.java).put(chat)

        assertEquals(1, repo.chats().single().unreadCount)
        val after = repo.projectionDiagnostics()
        assertEquals(0L, after.projectedChats - before.projectedChats)
        assertEquals(1L, after.unreadQueries - before.unreadQueries)
    }

    @Test
    fun `batched unread counts respect individual markers and exclude outgoing deleted and reactions`() {
        val firstHandle = handle("batch-first@icloud.com")
        val secondHandle = handle("batch-second@icloud.com")
        val first = chat("batch-first", firstHandle)
        val second = chat("batch-second", secondHandle)
        val firstRead = incoming(first, firstHandle, "first seen", 100L)
        incoming(first, firstHandle, "first unread", 200L)
        incoming(first, firstHandle, "outgoing", 250L).also { outgoing ->
            store.boxFor(Message::class.java).put(outgoing.apply { isFromMe = true })
        }
        reaction(first, firstHandle, firstRead.guid, 275L)
        incoming(first, firstHandle, "deleted", 300L).also { deleted ->
            store.boxFor(Message::class.java).put(deleted.apply { dateDeleted = Date(301L) })
        }
        val secondRead = incoming(second, secondHandle, "second seen", 250L)
        incoming(second, secondHandle, "second unread one", 300L)
        incoming(second, secondHandle, "second unread two", 350L)
        markLatest(first, unread = true, lastReadGuid = firstRead.guid)
        markLatest(second, unread = true, lastReadGuid = secondRead.guid)
        val repo = ChatRepo(store)

        val items = repo.chats().associateBy { it.guid }

        assertEquals(1, items.getValue("batch-first").unreadCount)
        assertEquals(2, items.getValue("batch-second").unreadCount)
        assertEquals(1L, repo.projectionDiagnostics().unreadQueries)
    }

    @Test
    fun `limited list preserves every selected contact member across native page boundaries`() {
        val firstHandle = handle("merged-first@icloud.com")
        val secondHandle = handle("merged-second@icloud.com")
        ContactSync(store).upsertContacts(
            listOf(
                RawContact(
                    id = "icloud:bounded-merged",
                    displayName = "Merged Friend",
                    firstName = "Merged",
                    lastName = "Friend",
                    avatarPath = null,
                    addresses = listOf(firstHandle.address, secondHandle.address),
                ),
            ),
        )
        val newest = chat("merged-newest", firstHandle)
        incoming(newest, firstHandle, "newest member", 1_000L)
        markLatest(newest, unread = true)
        repeat(40) { index ->
            val otherHandle = handle("bounded-$index@icloud.com")
            val other = chat("bounded-$index", otherHandle)
            incoming(other, otherHandle, "other $index", 900L - index)
            markLatest(other, unread = true)
        }
        val oldest = chat("merged-oldest", secondHandle)
        incoming(oldest, secondHandle, "oldest member", 1L)
        markLatest(oldest, unread = true)
        val repo = ChatRepo(store)

        val item = repo.chats(limit = 1).single()

        assertEquals("Merged Friend", item.title)
        assertEquals("newest member", item.snippet)
        assertEquals(listOf(newest.id, oldest.id).sorted(), item.memberChatIds)
        assertEquals(2, item.unreadCount)
        assertEquals(2L, repo.projectionDiagnostics().projectedChats)
        assertEquals(1L, repo.projectionDiagnostics().unreadQueries)
    }

    @Test
    fun `bounded observation retains pin ordering archived rows and deleted filtering`() = runBlocking {
        val pinnedHandle = handle("bounded-pinned@icloud.com")
        val archivedHandle = handle("bounded-archived@icloud.com")
        val deletedHandle = handle("bounded-deleted@icloud.com")
        val pinned = chat("bounded-pinned", pinnedHandle)
        val archived = chat("bounded-archived", archivedHandle)
        val deleted = chat("bounded-deleted", deletedHandle)
        incoming(pinned, pinnedHandle, "older pin", 100L)
        incoming(archived, archivedHandle, "newer archived", 300L)
        incoming(deleted, deletedHandle, "deleted newest", 500L)
        markLatest(pinned)
        markLatest(archived)
        markLatest(deleted)
        store.boxFor(Chat::class.java).put(pinned.apply {
            isPinned = true
            pinIndex = 0L
        })
        store.boxFor(Chat::class.java).put(archived.apply { isArchived = true })
        store.boxFor(Chat::class.java).put(deleted.apply { dateDeleted = Date(600L) })

        val rows = ChatRepo(store).observeChats(limit = 2).first()

        assertEquals(listOf("bounded-pinned", "bounded-archived"), rows.map { it.guid })
        assertEquals(true, rows[1].archived)
    }

    @Test
    fun `editing a reaction target invalidates its cached latest reaction snippet`() {
        val friend = handle("reaction-edit@icloud.com")
        val chat = chat("reaction-edit", friend)
        val target = incoming(chat, friend, "before edit", 100L)
        reaction(chat, friend, target.guid, 200L)
        markLatest(chat)
        val repo = ChatRepo(store)
        assertEquals("Someone loved “before edit”", repo.chats().single().snippet)
        val before = repo.projectionDiagnostics()

        store.boxFor(Message::class.java).put(target.apply { text = "after edit" })
        store.boxFor(Chat::class.java).put(chat)

        assertEquals("Someone loved “after edit”", repo.chats().single().snippet)
        assertEquals(1L, repo.projectionDiagnostics().projectedChats - before.projectedChats)
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

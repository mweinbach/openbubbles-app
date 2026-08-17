package app.openbubbles.nativeapp.data

import app.openbubbles.core.repo.MessageRepo
import app.openbubbles.db.Chat
import app.openbubbles.db.Message
import app.openbubbles.db.MyObjectBox
import io.objectbox.BoxStore
import java.io.File
import java.nio.file.Files
import java.util.Date
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CoreMessageListRepositoryTest {
    private lateinit var store: BoxStore
    private lateinit var testDir: File
    private lateinit var firstChat: Chat
    private lateinit var secondChat: Chat
    private lateinit var repository: CoreMessageListRepository

    @Before
    fun setUp() {
        testDir = Files.createTempDirectory("ob-ui-message-paging").toFile()
        store = MyObjectBox.builder().directory(testDir).build()
        val chats = store.boxFor(Chat::class.java)
        firstChat = Chat().apply { guid = "first" }.also(chats::put)
        secondChat = Chat().apply { guid = "second" }.also(chats::put)
        seed(firstChat, 100)
        seed(secondChat, 60)
        repository = CoreMessageListRepository(MessageRepo(store), store)
    }

    @After
    fun tearDown() {
        store.close()
        testDir.deleteRecursively()
    }

    @Test
    fun `load more expands only its chat and emits ascending history`() = runBlocking {
        val initial = repository.messages(firstChat.id, limit = 30, before = null).first()
        assertEquals(30, initial.size)
        assertTrue(initial.zipWithNext().all { (a, b) -> a.date <= b.date })

        val older = repository.loadMore(firstChat.id, before = initial.first().id, count = 20)
        assertEquals(20, older.size)
        assertTrue(older.last().date < initial.first().date)

        val expanded = repository.messages(firstChat.id, limit = 30, before = null).first()
        assertEquals(50, expanded.size)
        val other = repository.messages(secondChat.id, limit = 30, before = null).first()
        assertEquals(30, other.size)
    }

    @Test
    fun `cursor eventually reaches an empty end page`() = runBlocking {
        var page = repository.messages(firstChat.id, limit = 30, before = null).first()
        var loaded = page.size
        while (true) {
            val older = repository.loadMore(firstChat.id, before = page.first().id, count = 20)
            if (older.isEmpty()) break
            loaded += older.size
            page = repository.messages(firstChat.id, limit = 30, before = null).first()
        }
        assertEquals(100, loaded)
    }

    @Test
    fun `prefetch caches the newest page without opening the chat`() = runBlocking {
        repository.prefetch(listOf(firstChat.id), limit = 10)
        val cached = repository.cached(firstChat.id)
        assertEquals(10, cached.size)
        assertEquals("message 91", cached.first().text)
        assertEquals("message 100", cached.last().text)
        assertTrue(cached.zipWithNext().all { (a, b) -> a.date <= b.date })
    }

    @Test
    fun `prefetch evicts chats that leave the window`() = runBlocking {
        repository.prefetch(listOf(firstChat.id), limit = 10)
        assertEquals(10, repository.cached(firstChat.id).size)
        repository.prefetch(listOf(secondChat.id), limit = 10)
        assertTrue(repository.cached(firstChat.id).isEmpty())
        assertEquals(10, repository.cached(secondChat.id).size)
    }

    @Test
    fun `an open chat is retained while the list window moves`() = runBlocking {
        repository.prime(firstChat.id, limit = 30)
        assertEquals(30, repository.cached(firstChat.id).size)
        repository.prefetch(listOf(secondChat.id), limit = 10)
        assertEquals(30, repository.cached(firstChat.id).size)
        assertEquals(10, repository.cached(secondChat.id).size)
    }

    @Test
    fun `prime expands a prefetched snapshot`() = runBlocking {
        repository.prefetch(listOf(firstChat.id), limit = 10)
        assertEquals(10, repository.cached(firstChat.id).size)
        repository.prime(firstChat.id, limit = 30)
        assertEquals(30, repository.cached(firstChat.id).size)
        assertEquals("message 71", repository.cached(firstChat.id).first().text)
        assertEquals("message 100", repository.cached(firstChat.id).last().text)
    }

    @Test
    fun `release drops a snapshot once it is no longer desired`() = runBlocking {
        repository.prime(firstChat.id, limit = 30)
        repository.release(firstChat.id)
        repository.prefetch(listOf(secondChat.id), limit = 10)
        assertTrue(repository.cached(firstChat.id).isEmpty())
    }

    @Test
    fun `new messages do not evict already loaded history`() = runBlocking {
        val initial = repository.messages(firstChat.id, limit = 30, before = null).first()
        repository.loadMore(firstChat.id, before = initial.first().id, count = 20)
        assertEquals(50, repository.messages(firstChat.id, limit = 30, before = null).first().size)

        val box = store.boxFor(Message::class.java)
        box.put(Message().apply {
            guid = "first-101"
            text = "new message"
            dateCreated = Date(101)
            chat.target = firstChat
        })

        val updated = repository.messages(firstChat.id, limit = 30, before = null)
            .first { it.size == 51 }
        assertEquals("new message", updated.last().text)
    }

    @Test
    fun `upload progress overlays mapped messages without database enrichment`() {
        val item = MessageItem(
            id = 1L,
            text = "photo",
            isFromMe = true,
            date = 1L,
            status = MessageStatus.SENDING,
            isGroupEvent = false,
            reactionEmoji = null,
            attachmentMeta = AttachmentMeta(
                guid = "temp-photo_att0",
                mime = "image/jpeg",
                name = "photo.jpg",
                sizeBytes = 100L,
                isImage = true,
                downloaded = true,
            ),
        )

        val updated = applyUploadProgress(listOf(item), mapOf("temp-photo_att0" to (25L to 100L)))

        assertEquals(25L to 100L, updated.single().uploadProgress)
        assertEquals(item.attachmentMeta, updated.single().attachmentMeta)
    }

    private fun seed(target: Chat, count: Int) {
        val box = store.boxFor(Message::class.java)
        repeat(count) { index ->
            val value = index + 1L
            box.put(Message().apply {
                guid = "${target.guid}-$value"
                text = "message $value"
                dateCreated = Date(value)
                chat.target = target
            })
        }
    }
}

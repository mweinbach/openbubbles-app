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

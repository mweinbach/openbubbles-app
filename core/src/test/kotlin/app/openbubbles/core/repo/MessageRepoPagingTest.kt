package app.openbubbles.core.repo

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

class MessageRepoPagingTest {
    private lateinit var store: BoxStore
    private lateinit var testDir: File
    private lateinit var repo: MessageRepo
    private lateinit var chat: Chat

    @Before
    fun setUp() {
        testDir = Files.createTempDirectory("ob-message-paging").toFile()
        store = MyObjectBox.builder().directory(testDir).build()
        repo = MessageRepo(store)
        chat = Chat().apply { guid = "chat-paging" }
        store.boxFor(Chat::class.java).put(chat)
        seed(chat, 100)
    }

    @After
    fun tearDown() {
        store.close()
        testDir.deleteRecursively()
    }

    @Test
    fun `native query limits the reactive page`() = runBlocking {
        val page = repo.observeMessages(chat.id, limit = 10).first()

        assertEquals(10, page.size)
        assertEquals((91L..100L).toList().reversed(), page.map { it.date!!.time })
    }

    @Test
    fun `cursor page is older and does not overlap`() {
        val newest = repo.messages(chat.id, limit = 10)
        val older = repo.messagesBefore(chat.id, beforeId = newest.last().id, limit = 10)

        assertEquals(10, older.size)
        assertTrue(newest.map { it.id }.toSet().intersect(older.map { it.id }.toSet()).isEmpty())
        assertEquals((81L..90L).toList().reversed(), older.map { it.date!!.time })
    }

    private fun seed(target: Chat, count: Int) {
        val box = store.boxFor(Message::class.java)
        repeat(count) { index ->
            val value = index + 1L
            box.put(Message().apply {
                guid = "message-$value"
                text = "message $value"
                dateCreated = Date(value)
                chat.target = target
            })
        }
    }
}

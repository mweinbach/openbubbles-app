package app.openbubbles.core.repo

import app.openbubbles.core.contacts.ContactSync
import app.openbubbles.core.contacts.RawContact
import app.openbubbles.db.Chat
import app.openbubbles.db.Handle
import app.openbubbles.db.Message
import app.openbubbles.db.MyObjectBox
import io.objectbox.BoxStore
import java.io.File
import java.nio.file.Files
import java.util.Date
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.drop
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

    @Test
    fun `contact conversation merges histories and retains source chat ids`() {
        val firstHandle = handle("+15550000001")
        val secondHandle = handle("+15550000002")
        ContactSync(store).upsertContacts(
            listOf(
                RawContact(
                    id = "icloud:merged-history",
                    displayName = "Merged Person",
                    firstName = null,
                    lastName = null,
                    avatarPath = null,
                    addresses = listOf(firstHandle.address, secondHandle.address),
                ),
            ),
        )
        val firstChat = chat("first-address", firstHandle)
        val secondChat = chat("second-address", secondHandle)
        message(firstChat, "from first", 1_000L)
        message(secondChat, "from second", 2_000L)

        val page = repo.messages(firstChat.id, limit = 10)

        assertEquals(listOf("from second", "from first"), page.map { it.text })
        assertEquals(listOf(secondChat.id, firstChat.id), page.map { it.chatId })
    }

    @Test
    fun `attachment staging publishes only the complete local echo`() = runBlocking {
        val update = async(start = CoroutineStart.UNDISPATCHED) {
            repo.observeMessages(chat.id, limit = 101).drop(1).first()
        }

        repo.stageOutgoingMessageWithAttachments(
            chatGuid = chat.guid,
            sender = "mailto:me@icloud.com",
            text = "two files",
            stagingGuid = "temp-attachment-message",
            attachments = listOf(
                MessageRepo.OutgoingAttachmentStage(
                    guid = "temp-attachment-message_att0",
                    mimeType = "image/jpeg",
                    uti = "public.jpeg",
                    transferName = "one.jpg",
                    totalBytes = 10L,
                ),
                MessageRepo.OutgoingAttachmentStage(
                    guid = "temp-attachment-message_att1",
                    mimeType = "image/png",
                    uti = "public.png",
                    transferName = "two.png",
                    totalBytes = 20L,
                ),
            ),
        )

        val staged = update.await().first()
        assertEquals("two files", staged.text)
        assertTrue(staged.hasAttachments)
        assertEquals(2, staged.attachmentCount)
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

    private fun handle(address: String): Handle = Handle().apply {
        this.address = address
        service = "iMessage"
        uniqueAddressAndService = "$address/$service"
    }.also(store.boxFor(Handle::class.java)::put)

    private fun chat(guid: String, handle: Handle): Chat = Chat().apply {
        this.guid = guid
        chatIdentifier = handle.address
        isRpSms = false
        handles.add(handle)
    }.also(store.boxFor(Chat::class.java)::put)

    private fun message(chat: Chat, text: String, timestamp: Long) {
        store.boxFor(Message::class.java).put(Message().apply {
            guid = "${chat.guid}-$timestamp"
            this.text = text
            dateCreated = Date(timestamp)
            this.chat.target = chat
        })
    }
}

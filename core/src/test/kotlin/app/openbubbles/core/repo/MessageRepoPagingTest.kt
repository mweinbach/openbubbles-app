package app.openbubbles.core.repo

import app.openbubbles.core.contacts.ContactSync
import app.openbubbles.core.contacts.RawContact
import app.openbubbles.db.Attachment
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
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Before
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
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

    @Test
    fun `bookmark and deleted restore update message projections`() {
        val messageBox = store.boxFor(Message::class.java)
        val message = messageBox.all.first()

        repo.setBookmarked(listOf(message.id), true)
        assertTrue(repo.bookmarked(chat.id).single { it.id == message.id }.isBookmarked)

        messageBox.put(messageBox.get(message.id).apply { dateDeleted = Date(500L) })
        assertEquals(message.id, repo.recentlyDeleted(chat.id).single().id)

        repo.restoreDeleted(listOf(message.id))
        assertNull(messageBox.get(message.id).dateDeleted)
    }

    /**
     * observeMessages drops structurally identical pages so writes in other
     * chats stop waking open transcripts. That only holds while MessageItem
     * stays a pure value projection; this pins the contract.
     */
    @Test
    fun `page projection is structurally equal across unrelated writes`() {
        val other = chat("unrelated-chat", handle("+15559998888"))
        val before = repo.messages(chat.id, limit = 10)

        message(other, "noise in another conversation", 5_000L)

        assertEquals(before, repo.messages(chat.id, limit = 10))
    }

    /**
     * The dedupe must never swallow attachment-row-only changes: a download
     * completing flips Attachment.isDownloaded without touching the message
     * row, and the open transcript still has to refresh that bubble.
     */
    @Test
    fun `attachment-only download completion re-emits the transcript page`() = runBlocking<Unit> {
        val messageBox = store.boxFor(Message::class.java)
        val attachmentBox = store.boxFor(Attachment::class.java)
        val newest = messageBox.all.maxBy { it.dateCreated.time }
        val attachment = Attachment().apply {
            guid = "att-pending"
            transferName = "photo.jpg"
            isDownloaded = false
            this.message.target = newest
        }
        attachmentBox.put(attachment)
        messageBox.put(newest.apply { hasAttachments = true })

        val initial = repo.messages(chat.id, limit = 10)
        assertFalse(initial.first { it.guid == newest.guid }.attachmentStamps.single().downloaded)

        // Conflation may fold signals together, so collect into a channel and
        // wait until a page reflects the write instead of counting emissions.
        val pages = Channel<List<app.openbubbles.core.model.MessageItem>>(Channel.UNLIMITED)
        val collector = launch { repo.observeMessages(chat.id, limit = 10).collect { pages.send(it) } }
        try {
            withTimeout(10_000) {
                pages.receive()
                attachmentBox.put(attachmentBox.get(attachment.id).apply { isDownloaded = true })
                var page = pages.receive()
                while (!page.first { it.guid == newest.guid }.attachmentStamps.single().downloaded) {
                    page = pages.receive()
                }
            }
        } finally {
            collector.cancel()
        }
    }

    /**
     * The stranding this guards: a transfer promotes its payload while the row
     * already claims `isDownloaded` and the real byte length equals the
     * declared one, so no attachment column changes. Without the payload
     * identity in the projection the page compares equal, the dedupe drops it,
     * and the open transcript keeps its placeholder until it is reopened.
     */
    @Test
    fun `a promoted payload re-emits the page with no attachment column change`() = runBlocking<Unit> {
        val payloadRoot = Files.createTempDirectory("ob-message-paging-payload").toFile()
        try {
            val rendering = MessageRepo(store, attachmentsRoot = payloadRoot)
            val bytes = "payload-bytes".toByteArray()
            val (newest, attachment) = seedAttachment(
                guid = "att-promoted",
                downloaded = true,
                totalBytes = bytes.size.toLong(),
            )

            val stamps = rendering.messages(chat.id, limit = 10)
                .first { it.guid == newest.guid }
                .attachmentStamps
                .single()
            assertTrue(stamps.downloaded)
            assertNull(stamps.payload, "no payload is readable before promotion")

            val pages = Channel<List<app.openbubbles.core.model.MessageItem>>(Channel.UNLIMITED)
            val collector = launch {
                rendering.observeMessages(chat.id, limit = 10).collect { pages.send(it) }
            }
            try {
                withTimeout(10_000) {
                    pages.receive()
                    // Exactly what AttachmentManager does on success: promote
                    // the file, then persist the (here unchanged) row.
                    payloadFile(payloadRoot, attachment).apply {
                        parentFile?.mkdirs()
                        writeBytes(bytes)
                    }
                    store.boxFor(Attachment::class.java)
                        .put(store.boxFor(Attachment::class.java).get(attachment.id))

                    var stamp = pages.receive()
                        .first { it.guid == newest.guid }
                        .attachmentStamps
                        .single()
                    while (stamp.payload == null) {
                        stamp = pages.receive()
                            .first { it.guid == newest.guid }
                            .attachmentStamps
                            .single()
                    }
                    assertTrue(
                        stamp.payload!!
                            .endsWith(":${bytes.size}:${payloadFile(payloadRoot, attachment).lastModified()}"),
                    )
                    // Nothing else about the row moved, so the payload
                    // identity is the only reason this page was not deduped.
                    assertEquals(stamps, stamp.copy(payload = null))
                }
            } finally {
                collector.cancel()
            }
        } finally {
            payloadRoot.deleteRecursively()
        }
    }

    /** A half-written payload must never be advertised as renderable. */
    @Test
    fun `an incomplete payload is not stamped as readable`() {
        val payloadRoot = Files.createTempDirectory("ob-message-paging-partial").toFile()
        try {
            val rendering = MessageRepo(store, attachmentsRoot = payloadRoot)
            val (newest, attachment) = seedAttachment(
                guid = "att-partial",
                downloaded = true,
                totalBytes = 4_096L,
            )
            payloadFile(payloadRoot, attachment).apply {
                parentFile?.mkdirs()
                writeBytes(ByteArray(12))
            }

            val stamp = rendering.messages(chat.id, limit = 10)
                .first { it.guid == newest.guid }
                .attachmentStamps
                .single()
            assertNull(stamp.payload)
        } finally {
            payloadRoot.deleteRecursively()
        }
    }

    /**
     * The transcript must not need a warm-up delay before it is safe to write.
     * A separately merged initial value can query before the change
     * subscription exists, and the commit landing in that window is lost until
     * the conversation is reopened.
     */
    @Test
    fun `a write committed as soon as collection starts is not lost`() = runBlocking<Unit> {
        val (newest, attachment) = seedAttachment(
            guid = "att-readiness",
            downloaded = false,
            totalBytes = 10L,
        )
        val attachmentBox = store.boxFor(Attachment::class.java)
        val pages = Channel<List<app.openbubbles.core.model.MessageItem>>(Channel.UNLIMITED)
        val collector = launch(start = CoroutineStart.UNDISPATCHED) {
            repo.observeMessages(chat.id, limit = 10).collect { pages.send(it) }
        }
        try {
            withTimeout(10_000) {
                attachmentBox.put(attachmentBox.get(attachment.id).apply { isDownloaded = true })
                var stamp = pages.receive().first { it.guid == newest.guid }.attachmentStamps.single()
                while (!stamp.downloaded) {
                    stamp = pages.receive().first { it.guid == newest.guid }.attachmentStamps.single()
                }
            }
        } finally {
            collector.cancel()
        }
    }

    private fun seedAttachment(
        guid: String,
        downloaded: Boolean,
        totalBytes: Long,
    ): Pair<Message, Attachment> {
        val messageBox = store.boxFor(Message::class.java)
        val attachmentBox = store.boxFor(Attachment::class.java)
        val newest = messageBox.all.maxBy { it.dateCreated!!.time }
        val attachment = Attachment().apply {
            this.guid = guid
            transferName = "photo.jpg"
            mimeType = "image/jpeg"
            isDownloaded = downloaded
            this.totalBytes = totalBytes
            this.message.target = newest
        }
        attachmentBox.put(attachment)
        messageBox.put(newest.apply { hasAttachments = true })
        return newest to attachment
    }

    private fun payloadFile(root: File, attachment: Attachment): File =
        File(File(File(root, "attachments"), attachment.guid!!), attachment.transferName!!)

    @Test
    fun `clear transcript deletes all local messages`() {
        repo.clearTranscript(chat.id)

        assertEquals(0L, store.boxFor(Message::class.java).count())
        assertNull(store.boxFor(Chat::class.java).get(chat.id).dbLatestMessage.target)
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

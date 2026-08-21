package app.openbubbles.nativeapp.data

import app.openbubbles.core.repo.MessageRepo
import app.openbubbles.db.Attachment
import app.openbubbles.db.Chat
import app.openbubbles.db.Message
import app.openbubbles.db.MyObjectBox
import io.objectbox.BoxStore
import java.io.File
import java.nio.file.Files
import java.util.Date
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
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
        repository.close()
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

    @Test
    fun `short prefetched chats remember the requested capacity`() = runBlocking {
        val loads = AtomicInteger()
        repository.close()
        repository = CoreMessageListRepository(MessageRepo(store), store) { _, _ ->
            loads.incrementAndGet()
            listOf(messageItem(1L, "short"))
        }

        repository.prefetch(listOf(firstChat.id), limit = 10)
        repository.prefetch(listOf(firstChat.id), limit = 10)

        assertEquals(1, loads.get())
    }

    @Test
    fun `database changes invalidate a warmed snapshot`() = runBlocking {
        repository.prefetch(listOf(firstChat.id), limit = 10)
        assertEquals(10, repository.cached(firstChat.id).size)

        store.boxFor(Message::class.java).put(Message().apply {
            guid = "first-101"
            text = "new message"
            dateCreated = Date(101)
            chat.target = firstChat
        })

        withTimeout(2_000) {
            while (repository.cached(firstChat.id).isNotEmpty()) delay(10)
        }
        repository.prefetch(listOf(firstChat.id), limit = 10)
        assertEquals("new message", repository.cached(firstChat.id).last().text)
    }

    /**
     * The reported stranding: the payload lands while the conversation is
     * open, but no attachment column changes (the row already claims
     * downloaded and the byte length already matches), so only the projected
     * payload identity can tell the mounted bubble to look at disk again.
     */
    @Test
    fun `a promoted payload reaches the collected transcript metadata`() = runBlocking {
        val payloadRoot = Files.createTempDirectory("ob-ui-message-payload").toFile()
        try {
            repository.close()
            repository = CoreMessageListRepository(
                MessageRepo(store, attachmentsRoot = payloadRoot),
                store,
            )
            val messages = store.boxFor(Message::class.java)
            val attachments = store.boxFor(Attachment::class.java)
            val bytes = "promoted-payload".toByteArray()
            val newest = messages.all.maxBy { it.dateCreated!!.time }
            val attachment = Attachment().apply {
                guid = "att-promoted_0"
                transferName = "photo.jpg"
                mimeType = "image/jpeg"
                isDownloaded = true
                totalBytes = bytes.size.toLong()
                message.target = newest
            }
            attachments.put(attachment)
            messages.put(newest.apply { hasAttachments = true })

            val pages = Channel<List<MessageItem>>(Channel.UNLIMITED)
            val collector = launch {
                repository.messages(firstChat.id, limit = 30, before = null).collect(pages::send)
            }
            try {
                withTimeout(10_000) {
                    var meta = pages.receive().last().attachmentMeta
                    while (meta == null) meta = pages.receive().last().attachmentMeta
                    assertEquals(null, meta.payloadStamp)

                    val payload = File(payloadRoot, "attachments/att-promoted_0/photo.jpg").apply {
                        parentFile?.mkdirs()
                        writeBytes(bytes)
                    }
                    attachments.put(attachments.get(attachment.id))

                    var stamped = pages.receive().last().attachmentMeta?.payloadStamp
                    while (stamped == null) {
                        stamped = pages.receive().last().attachmentMeta?.payloadStamp
                    }
                    assertTrue(stamped.endsWith(":${bytes.size}:${payload.lastModified()}"))
                }
            } finally {
                collector.cancel()
            }
        } finally {
            payloadRoot.deleteRecursively()
        }
    }

    @Test
    fun `attachment completion updates the collected transcript in place`() = runBlocking {
        val messages = store.boxFor(Message::class.java)
        val attachments = store.boxFor(Attachment::class.java)
        val newest = messages.all.maxBy { it.dateCreated!!.time }
        val attachment = Attachment().apply {
            guid = "att-pending_0"
            transferName = "photo.jpg"
            mimeType = "image/jpeg"
            isDownloaded = false
            message.target = newest
        }
        attachments.put(attachment)
        messages.put(newest.apply { hasAttachments = true })

        val pages = Channel<List<MessageItem>>(Channel.UNLIMITED)
        val collector = launch {
            repository.messages(firstChat.id, limit = 30, before = null).collect(pages::send)
        }
        try {
            withTimeout(10_000) {
                var page = pages.receive()
                while (page.lastOrNull()?.attachmentMeta == null) page = pages.receive()
                assertEquals(false, page.last().attachmentMeta?.downloaded)

                attachments.put(attachments.get(attachment.id).apply { isDownloaded = true })

                while (page.last().attachmentMeta?.downloaded != true) page = pages.receive()
            }
        } finally {
            collector.cancel()
        }
    }

    @Test
    fun `attachment completion after a new message updates the collected transcript`() = runBlocking {
        val messages = store.boxFor(Message::class.java)
        val attachments = store.boxFor(Attachment::class.java)

        val pages = Channel<List<MessageItem>>(Channel.UNLIMITED)
        val collector = launch {
            repository.messages(firstChat.id, limit = 30, before = null).collect(pages::send)
        }
        try {
            withTimeout(10_000) {
                pages.receive()
                val incoming = Message().apply {
                    guid = "first-101"
                    dateCreated = Date(101)
                    hasAttachments = true
                    chat.target = firstChat
                }
                messages.put(incoming)
                val attachment = Attachment().apply {
                    guid = "att-live_0"
                    transferName = "photo.jpg"
                    mimeType = "image/jpeg"
                    isDownloaded = false
                    message.target = incoming
                }
                attachments.put(attachment)

                var page = pages.receive()
                while (page.lastOrNull()?.attachmentMeta == null) page = pages.receive()

                attachments.put(attachments.get(attachment.id).apply { isDownloaded = true })

                while (page.last().attachmentMeta?.downloaded != true) page = pages.receive()
            }
        } finally {
            collector.cancel()
        }
    }

    @Test
    fun `obsolete prefetch cannot repopulate an evicted snapshot`() = runBlocking {
        val firstStarted = CompletableDeferred<Unit>()
        val releaseFirst = CompletableDeferred<Unit>()
        repository.close()
        repository = CoreMessageListRepository(MessageRepo(store), store) { chatId, _ ->
            if (chatId == firstChat.id) {
                firstStarted.complete(Unit)
                releaseFirst.await()
            }
            listOf(messageItem(chatId, "chat-$chatId"))
        }

        val obsolete = async { repository.prefetch(listOf(firstChat.id), limit = 10) }
        firstStarted.await()
        repository.prefetch(listOf(secondChat.id), limit = 10)
        releaseFirst.complete(Unit)
        obsolete.await()

        assertTrue(repository.cached(firstChat.id).isEmpty())
        assertEquals(listOf("chat-${secondChat.id}"), repository.cached(secondChat.id).map { it.text })
    }

    @Test
    fun `warm gives up after two invalidated loads instead of spinning`() = runBlocking {
        val loads = AtomicInteger()
        repository.close()
        repository = CoreMessageListRepository(MessageRepo(store), store) { _, _ ->
            val before = repository.observedChangeGeneration()
            val sequence = loads.incrementAndGet()
            store.boxFor(Message::class.java).put(Message().apply {
                guid = "invalidating-$sequence"
                chat.target = firstChat
            })
            // The load is only discarded when the repository itself has seen
            // the invalidation, so wait for its counter and not for a second
            // collector of the same store signal.
            withTimeout(2_000) {
                while (repository.observedChangeGeneration() == before) delay(1)
            }
            listOf(messageItem(loads.get().toLong(), "unstable"))
        }

        withTimeout(5_000) { repository.prefetch(listOf(firstChat.id), limit = 10) }

        assertEquals(2, loads.get())
        assertTrue(repository.cached(firstChat.id).isEmpty())
    }

    private fun messageItem(id: Long, text: String) = MessageItem(
        id = id,
        text = text,
        isFromMe = false,
        date = id,
        status = MessageStatus.SENT,
        isGroupEvent = false,
        reactionEmoji = null,
        guid = "test-$id",
    )

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

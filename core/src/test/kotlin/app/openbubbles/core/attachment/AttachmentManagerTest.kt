package app.openbubbles.core.attachment

import app.openbubbles.db.Attachment
import app.openbubbles.db.Attachment_
import app.openbubbles.db.Chat
import app.openbubbles.db.Handle
import app.openbubbles.db.Message
import app.openbubbles.db.MyObjectBox
import io.objectbox.BoxStore
import io.objectbox.query.QueryBuilder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Before
import org.junit.Test
import java.io.File
import java.util.Date
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * [AttachmentManager] behavior with a scripted fake [AttachmentDownloader]:
 * progress → done → persisted `isDownloaded`, concurrent dedupe, failure
 * cleanup, pending/enqueue chat semantics.
 */
class AttachmentManagerTest {

    private lateinit var store: BoxStore
    private lateinit var testDir: File
    private lateinit var rootDir: File
    private lateinit var fake: FakeDownloader
    private lateinit var scope: CoroutineScope
    private lateinit var manager: AttachmentManager

    @Before
    fun setUp() {
        testDir = java.nio.file.Files.createTempDirectory("ob-att-mgr-test").toFile()
        store = MyObjectBox.builder().directory(testDir).build()
        rootDir = File(testDir, "appdocs")
        fake = FakeDownloader()
        scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        manager = AttachmentManager(store, rootDir, fake, scope)
    }

    @After
    fun tearDown() {
        scope.cancel()
        store.close()
        testDir.deleteRecursively()
    }

    /**
     * Fake rust transfer: writes the payload to destPath, reporting progress
     * in steps. `failAfterWrite` emulates a transfer that dies mid-stream,
     * leaving a partial file behind.
     */
    private class FakeDownloader : AttachmentDownloader {
        val calls = mutableListOf<String>()
        @Volatile var failAfterWrite = false
        var stepDelayMs = 20L
        val payload = "payload-bytes!".toByteArray()

        override suspend fun download(
            attachmentGuid: String,
            destPath: String,
            onProgress: (Long, Long) -> Unit,
        ): Result<Unit> {
            synchronized(calls) { calls += attachmentGuid }
            val total = payload.size.toLong()
            onProgress(0, total)
            File(destPath).writeBytes(payload)
            delay(stepDelayMs)
            onProgress(total / 2, total)
            delay(stepDelayMs)
            onProgress(total, total)
            return if (failAfterWrite) {
                Result.failure(IllegalStateException("transfer interrupted"))
            } else {
                Result.success(Unit)
            }
        }
    }

    // ------------------------------------------------------------------
    // Fixtures
    // ------------------------------------------------------------------

    private fun chatBox() = store.boxFor(Chat::class.java)
    private fun messageBox() = store.boxFor(Message::class.java)
    private fun attachmentBox() = store.boxFor(Attachment::class.java)

    private fun seedChat(chatGuid: String, vararg participants: String): Chat {
        val chat = Chat().apply {
            guid = chatGuid
            chatIdentifier = chatGuid
            isRpSms = false
        }
        store.runInTx {
            participants.forEach { address ->
                val handle = Handle().apply {
                    this.address = address
                    service = "iMessage"
                    uniqueAddressAndService = "$address/iMessage-$chatGuid-$address"
                }
                store.boxFor(Handle::class.java).put(handle)
                chat.handles.add(handle)
            }
            chatBox().put(chat)
        }
        return chat
    }

    private fun seedAttachment(
        chat: Chat,
        guid: String,
        transferName: String?,
        outgoing: Boolean = false,
        downloaded: Boolean = false,
    ): Attachment {
        val message = Message().apply {
            this.guid = "msg-$guid"
            text = "text for $guid"
            dateCreated = Date()
            isFromMe = outgoing
        }
        val att = Attachment().apply {
            this.guid = guid
            this.transferName = transferName
            mimeType = "application/octet-stream"
            isOutgoing = outgoing
            isDownloaded = downloaded
        }
        store.runInTx {
            message.chat.target = chat
            messageBox().put(message)
            att.message.target = message
            attachmentBox().put(att)
        }
        return att
    }

    private fun stored(guid: String): Attachment? =
        attachmentBox().query()
            .equal(Attachment_.guid, guid, QueryBuilder.StringOrder.CASE_SENSITIVE)
            .build().use { it.findFirst() }

    // ------------------------------------------------------------------
    // download()
    // ------------------------------------------------------------------

    @Test
    fun `download emits progress then done and persists isDownloaded`() = runBlocking<Unit> {
        val chat = seedChat("chat-1", "friend@icloud.com")
        val att = seedAttachment(chat, "att-1", "pic.png")

        val states = withTimeout(5_000) { manager.download(att).toList() }

        assertTrue(states.any { it is TransferState.Progress }, "expected progress states, got $states")
        val progresses = states.filterIsInstance<TransferState.Progress>()
        assertTrue(progresses.all { it.done <= it.total })
        assertEquals(TransferState.Done, states.last())

        // File landed at the canonical layout with the payload.
        val file = File(rootDir, "attachments/att-1/pic.png")
        assertTrue(file.isFile)
        assertEquals(String(fake.payload), file.readText())

        // DB row persisted: downloaded + real byte length.
        val row = stored("att-1")
        assertTrue(row!!.isDownloaded)
        assertEquals(fake.payload.size.toLong(), row.totalBytes)
        assertEquals(file, manager.localFile(att))
    }

    @Test
    fun `localFile is null before download`() = runBlocking<Unit> {
        val chat = seedChat("chat-2", "friend@icloud.com")
        val att = seedAttachment(chat, "att-2", "pic.png")
        assertNull(manager.localFile(att))
    }

    @Test
    fun `concurrent downloads dedupe into a single transfer`() = runBlocking<Unit> {
        val chat = seedChat("chat-3", "friend@icloud.com")
        val att = seedAttachment(chat, "att-3", "pic.png")

        val (first, second) = withTimeout(5_000) {
            // Start both collectors inline until they suspend on the shared
            // StateFlow. Default-dispatched collectors can be serialized on a
            // loaded CI worker, letting the transfer finish before the second
            // subscribes and turning this into a scheduler test.
            val a = async(start = CoroutineStart.UNDISPATCHED) { manager.download(att).toList() }
            val b = async(start = CoroutineStart.UNDISPATCHED) { manager.download(att).toList() }
            Pair(a.await(), b.await())
        }

        // Exactly one rust transfer ran for the guid…
        assertEquals(listOf("att-3"), synchronized(fake.calls) { fake.calls.toList() })
        // …and both collectors observed the full lifecycle.
        assertEquals(TransferState.Done, first.last())
        assertEquals(TransferState.Done, second.last())
        assertTrue(first.any { it is TransferState.Progress })
        assertTrue(second.any { it is TransferState.Progress })
        assertTrue(stored("att-3")!!.isDownloaded)
    }

    @Test
    fun `failed transfer removes partial file and leaves attachment pending`() = runBlocking<Unit> {
        fake.failAfterWrite = true
        val chat = seedChat("chat-4", "friend@icloud.com")
        val att = seedAttachment(chat, "att-4", "doc.pdf")

        val states = withTimeout(5_000) { manager.download(att).toList() }

        val failure = states.last()
        check(failure is TransferState.Failed) { "expected Failed, got $states" }
        assertTrue(failure.error.contains("interrupted"))

        val partial = File(rootDir, "attachments/att-4/doc.pdf")
        assertTrue(!partial.exists(), "partial file should be cleaned up")
        assertTrue(!stored("att-4")!!.isDownloaded)
        assertNull(manager.localFile(att))
    }

    @Test
    fun `file already on disk short circuits without a transfer`() = runBlocking<Unit> {
        val chat = seedChat("chat-5", "friend@icloud.com")
        val att = seedAttachment(chat, "att-5", "pic.png")

        // Simulate a file left behind by a previous run (flag drifted).
        val dir = File(rootDir, "attachments/att-5").apply { mkdirs() }
        File(dir, "pic.png").writeText("stale")

        val states = withTimeout(5_000) { manager.download(att).toList() }

        assertEquals(listOf<TransferState>(TransferState.Done), states)
        assertTrue(synchronized(fake.calls) { fake.calls.isEmpty() }, "downloader must not run")
        // The stale flag is normalized to downloaded.
        assertTrue(stored("att-5")!!.isDownloaded)
    }

    @Test
    fun `staged temp attachment fails fast`() = runBlocking<Unit> {
        val chat = seedChat("chat-6", "friend@icloud.com")
        val att = seedAttachment(chat, "temp-abc12345", "out.png", outgoing = true)

        val states = withTimeout(5_000) { manager.download(att).toList() }

        val failure = states.single()
        check(failure is TransferState.Failed) { "expected single Failed, got $states" }
        assertTrue(synchronized(fake.calls) { fake.calls.isEmpty() })
    }

    // ------------------------------------------------------------------
    // pendingFor() + enqueueForChat()
    // ------------------------------------------------------------------

    @Test
    fun `pendingFor returns undownloaded attachments with payload info for the chat`() {
        val chatA = seedChat("chatA", "a@icloud.com")
        val chatB = seedChat("chatB", "b@icloud.com")
        seedAttachment(chatA, "p-1", "one.png")
        seedAttachment(chatA, "p-2", "two.png", downloaded = true)
        seedAttachment(chatA, "temp-00000000", "staged.png")
        seedAttachment(chatA, "p-3", null)
        seedAttachment(chatB, "p-4", "other.png")

        val pending = manager.pendingFor(chatA.id).map { it.guid }
        assertEquals(listOf("p-1"), pending)
    }

    @Test
    fun `enqueueForChat bulk downloads incoming attachments only`() = runBlocking<Unit> {
        val chat = seedChat("chat-7", "friend@icloud.com")
        seedAttachment(chat, "q-1", "in-1.png")
        seedAttachment(chat, "q-2", "in-2.png")
        seedAttachment(chat, "q-3", "out.png", outgoing = true)

        withTimeout(10_000) { manager.enqueueForChat(chat.id) }

        assertTrue(stored("q-1")!!.isDownloaded)
        assertTrue(stored("q-2")!!.isDownloaded)
        assertTrue(!stored("q-3")!!.isDownloaded, "outgoing attachment must not be bulk-downloaded")

        val calls = synchronized(fake.calls) { fake.calls.toSet() }
        assertEquals(setOf("q-1", "q-2"), calls)
        assertTrue(manager.pendingFor(chat.id).map { it.guid }.contains("q-3"))
    }

    @Test
    fun `concurrent transfers are bounded so bulk enqueues cannot saturate the dispatcher`() = runBlocking<Unit> {
        val chat = seedChat("chat-8", "friend@icloud.com")
        val attachments = (1..10).map { seedAttachment(chat, "b-$it", "file-$it.png") }

        val active = java.util.concurrent.atomic.AtomicInteger(0)
        val maxActive = java.util.concurrent.atomic.AtomicInteger(0)
        val gated = AttachmentDownloader { _, destPath, _ ->
            val now = active.incrementAndGet()
            maxActive.updateAndGet { seen -> maxOf(seen, now) }
            delay(50)
            File(destPath).writeBytes("payload".toByteArray())
            active.decrementAndGet()
            Result.success(Unit)
        }
        val bounded = AttachmentManager(store, rootDir, gated, scope)

        withTimeout(10_000) {
            attachments.map { att -> async { bounded.download(att).toList() } }.forEach { it.await() }
        }

        assertTrue(maxActive.get() in 1..4, "expected at most 4 concurrent transfers, saw ${maxActive.get()}")
        attachments.forEach { assertTrue(stored(it.guid!!)!!.isDownloaded, "${it.guid} should be downloaded") }
    }
}

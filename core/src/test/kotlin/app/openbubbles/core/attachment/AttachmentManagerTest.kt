package app.openbubbles.core.attachment

import app.openbubbles.db.Attachment
import app.openbubbles.db.Attachment_
import app.openbubbles.db.Chat
import app.openbubbles.db.Handle
import app.openbubbles.db.Message
import app.openbubbles.db.MyObjectBox
import io.objectbox.BoxStore
import io.objectbox.query.QueryBuilder
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Before
import org.junit.Test
import java.io.File
import java.util.Date
import kotlin.test.assertEquals
import kotlin.test.assertFalse
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
        val destinations = mutableListOf<String>()
        val limits = mutableListOf<Long?>()
        @Volatile var failAfterWrite = false
        var stepDelayMs = 20L
        val payload = "payload-bytes!".toByteArray()

        override suspend fun download(
            attachmentGuid: String,
            destPath: String,
            maxBytes: Long?,
            onProgress: (Long, Long) -> Unit,
        ): Result<Unit> {
            synchronized(calls) { calls += attachmentGuid }
            synchronized(destinations) { destinations += destPath }
            synchronized(limits) { limits += maxBytes }
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

    @Test
    fun `unknown-size auto download forwards a hard byte ceiling to the stream writer`() = runBlocking<Unit> {
        val chat = seedChat("chat-bounded-unknown", "friend@icloud.com")
        val attachment = seedAttachment(chat, "bounded-unknown", "voice.m4a", expectedBytes = null)

        val states = withTimeout(5_000) {
            manager.download(attachment, maxBytes = 10L * 1024L * 1024L).toList()
        }

        assertEquals(TransferState.Done, states.last())
        assertEquals(10L * 1024L * 1024L, fake.limits.single())
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
        expectedBytes: Long? = null,
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
            totalBytes = expectedBytes
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
        assertTrue(synchronized(fake.destinations) { fake.destinations.single() }.endsWith(".openbubbles-partial"))
        assertTrue(!File(file.parentFile, ".pic.png.openbubbles-partial").exists())

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
    fun `account cleanup cancels and joins detached attachment transfers before deleting partials`() =
        runBlocking<Unit> {
            val chat = seedChat("chat-cancel-account", "friend@icloud.com")
            val attachment = seedAttachment(chat, "att-cancel-account", "photo.png")
            val started = CompletableDeferred<Unit>()
            val released = CompletableDeferred<Unit>()
            val tracked = AttachmentManager(
                store,
                rootDir,
                AttachmentDownloader { _, destination, _, _ ->
                    File(destination).writeText("incomplete")
                    started.complete(Unit)
                    try {
                        awaitCancellation()
                    } finally {
                        withContext(NonCancellable) {
                            delay(20)
                            released.complete(Unit)
                        }
                    }
                },
                scope,
            )
            val collected = async(start = CoroutineStart.UNDISPATCHED) {
                tracked.download(attachment).toList()
            }

            withTimeout(5_000) { started.await() }
            withTimeout(5_000) { tracked.cancelAndJoin() }

            assertTrue(released.isCompleted, "cleanup must wait for the actual transfer owner")
            assertTrue(withTimeout(5_000) { collected.await() }.last() is TransferState.Failed)
            assertFalse(File(rootDir, "attachments/att-cancel-account/photo.png").exists())
            assertFalse(
                File(rootDir, "attachments/att-cancel-account/.photo.png.openbubbles-partial").exists(),
            )
            assertFalse(checkNotNull(stored("att-cancel-account")).isDownloaded)
        }

    @Test
    fun `account cleanup blocks new transfers until the next validated account starts`() =
        runBlocking<Unit> {
            val chat = seedChat("chat-account-reopen", "friend@icloud.com")
            val attachment = seedAttachment(chat, "att-account-reopen", "photo.png")

            manager.cancelAndJoin()

            val blocked = withTimeout(5_000) { manager.download(attachment).toList() }
            assertTrue(blocked.single() is TransferState.Failed)
            assertTrue(synchronized(fake.calls) { fake.calls.isEmpty() })
            assertFalse(File(rootDir, "attachments/att-account-reopen").exists())

            manager.beginAccount()

            val resumed = withTimeout(5_000) { manager.download(attachment).toList() }
            assertEquals(TransferState.Done, resumed.last())
            assertTrue(checkNotNull(stored("att-account-reopen")).isDownloaded)
            assertEquals(listOf("att-account-reopen"), synchronized(fake.calls) { fake.calls.toList() })
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

        val partial = File(rootDir, "attachments/att-4/.doc.pdf.openbubbles-partial")
        assertTrue(!partial.exists(), "partial file should be cleaned up")
        assertTrue(!File(rootDir, "attachments/att-4/doc.pdf").exists(), "failed payload must never be published")
        assertTrue(!stored("att-4")!!.isDownloaded)
        assertNull(manager.localFile(att))
    }

    @Test
    fun `valid file already on disk short circuits without a transfer`() = runBlocking<Unit> {
        val chat = seedChat("chat-5", "friend@icloud.com")
        val existingPayload = "already complete"
        val att = seedAttachment(chat, "att-5", "pic.png", expectedBytes = existingPayload.length.toLong())

        // Simulate a file left behind by a previous run (flag drifted).
        val dir = File(rootDir, "attachments/att-5").apply { mkdirs() }
        File(dir, "pic.png").writeText(existingPayload)
        val abandonedPartial = File(dir, ".pic.png.openbubbles-partial").apply { writeText("abandoned") }

        val states = withTimeout(5_000) { manager.download(att).toList() }

        assertEquals(listOf<TransferState>(TransferState.Done), states)
        assertTrue(synchronized(fake.calls) { fake.calls.isEmpty() }, "downloader must not run")
        assertTrue(!abandonedPartial.exists(), "owned partial from an earlier process must be cleaned")
        // The drifted flag is normalized to downloaded.
        assertTrue(stored("att-5")!!.isDownloaded)
    }

    @Test
    fun `wrong-size existing file is replaced only after a complete validated download`() = runBlocking<Unit> {
        val chat = seedChat("chat-stale", "friend@icloud.com")
        val att = seedAttachment(
            chat,
            "att-stale",
            "pic.png",
            expectedBytes = fake.payload.size.toLong(),
        )
        val canonical = File(rootDir, "attachments/att-stale/pic.png").apply {
            parentFile.mkdirs()
            writeText("stale")
        }

        val states = withTimeout(5_000) { manager.download(att).toList() }

        assertEquals(TransferState.Done, states.last())
        assertEquals(listOf("att-stale"), synchronized(fake.calls) { fake.calls.toList() })
        assertEquals(fake.payload.toList(), canonical.readBytes().toList())
        assertEquals(fake.payload.size.toLong(), canonical.length())
        assertTrue(stored("att-stale")!!.isDownloaded)
    }

    @Test
    fun `missing completed payload publishes pending state before redownload`() = runBlocking<Unit> {
        val chat = seedChat("chat-missing-complete", "friend@icloud.com")
        val att = seedAttachment(
            chat,
            "att-missing-complete",
            "photo.mov",
            expectedBytes = fake.payload.size.toLong(),
            downloaded = true,
        )
        var observedPending = false

        val states = withTimeout(5_000) {
            manager.download(att)
                .onEach { state ->
                    if (state !is TransferState.Done) {
                        observedPending = observedPending || stored("att-missing-complete")?.isDownloaded == false
                    }
                }
                .toList()
        }

        assertTrue(observedPending, "stale true flag must transition through pending")
        assertEquals(TransferState.Done, states.last())
        assertTrue(stored("att-missing-complete")!!.isDownloaded)
    }

    @Test
    fun `wrong-size completed transfer is rejected without replacing the canonical file`() = runBlocking<Unit> {
        val chat = seedChat("chat-size", "friend@icloud.com")
        val att = seedAttachment(
            chat,
            "att-size",
            "pic.png",
            expectedBytes = fake.payload.size + 1L,
        )
        val canonical = File(rootDir, "attachments/att-size/pic.png").apply {
            parentFile.mkdirs()
            writeText("prior")
        }

        val states = withTimeout(5_000) { manager.download(att).toList() }

        val failure = states.last()
        check(failure is TransferState.Failed) { "expected Failed, got $states" }
        assertTrue(failure.error.contains("expected ${fake.payload.size + 1L} bytes"))
        assertEquals("prior", canonical.readText(), "invalid replacement must not touch the canonical file")
        assertTrue(!File(canonical.parentFile, ".pic.png.openbubbles-partial").exists())
        assertTrue(!stored("att-size")!!.isDownloaded)
        assertNull(manager.localFile(att), "wrong-size canonical file must not be exposed as complete")
    }

    @Test
    fun `process restart discards abandoned partial before starting a fresh transfer`() = runBlocking<Unit> {
        val chat = seedChat("chat-restart", "friend@icloud.com")
        val att = seedAttachment(
            chat,
            "att-restart",
            "pic.png",
            expectedBytes = fake.payload.size.toLong(),
        )
        val canonical = File(rootDir, "attachments/att-restart/pic.png")
        val partial = File(rootDir, "attachments/att-restart/.pic.png.openbubbles-partial").apply {
            parentFile.mkdirs()
            writeText("bytes from dead process")
        }
        var sawCleanStart = false
        val restarted = AttachmentManager(
            store,
            rootDir,
            AttachmentDownloader { _, destPath, _, _ ->
                val destination = File(destPath)
                assertEquals(partial.canonicalFile, destination.canonicalFile)
                sawCleanStart = !destination.exists() && !canonical.exists()
                destination.writeBytes(fake.payload)
                Result.success(Unit)
            },
            scope,
        )

        val states = withTimeout(5_000) { restarted.download(att).toList() }

        assertEquals(TransferState.Done, states.last())
        assertTrue(sawCleanStart, "a prior process partial must not be resumed or published")
        assertTrue(!partial.exists())
        assertEquals(fake.payload.toList(), canonical.readBytes().toList())
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
        val gated = AttachmentDownloader { _, destPath, _, _ ->
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

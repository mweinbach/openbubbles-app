package app.openbubbles.nativeapp.data

import app.openbubbles.core.sync.TranscriptBackgroundUpdate
import app.openbubbles.db.Chat
import app.openbubbles.db.MyObjectBox
import io.objectbox.BoxStore
import java.io.File
import java.nio.file.Files
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Before
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class TranscriptBackgroundStoreTest {
    private lateinit var root: File
    private lateinit var filesDir: File
    private lateinit var cacheDir: File
    private lateinit var store: BoxStore
    private lateinit var chat: Chat

    @Before
    fun setUp() {
        root = Files.createTempDirectory("ob-transcript-background").toFile()
        filesDir = File(root, "files").apply { mkdirs() }
        cacheDir = File(root, "cache").apply { mkdirs() }
        store = MyObjectBox.builder().directory(File(root, "db")).build()
        chat = Chat().apply { guid = "chat-guid" }.also(store.boxFor(Chat::class.java)::put)
    }

    @After
    fun tearDown() {
        store.close()
        root.deleteRecursively()
    }

    @Test
    fun `newer backgrounds replace atomically while stale and removal updates are safe`() = runBlocking {
        val directory = File(filesDir, "chat_backgrounds").apply { mkdirs() }
        val oldFile = File(directory, "shared-${chat.id}-1.img").apply { writeBytes(byteArrayOf(1)) }
        chat.transcriptPosterPath = oldFile.absolutePath
        chat.transcriptBackgroundVersion = 1
        store.boxFor(Chat::class.java).put(chat)
        var loads = 0
        val backgroundStore = TranscriptBackgroundStore(store, filesDir, cacheDir) { _, _ ->
            loads += 1
            byteArrayOf(2, 3, 4)
        }

        backgroundStore.apply(
            TranscriptBackgroundUpdate(chat.id, 7, remove = false, mmcsXml = "<plist/>")
        )

        val applied = store.boxFor(Chat::class.java).get(chat.id)
        val appliedFile = File(requireNotNull(applied.transcriptPosterPath))
        assertEquals(7, applied.transcriptBackgroundVersion)
        assertTrue(appliedFile.isFile)
        assertTrue(appliedFile.readBytes().contentEquals(byteArrayOf(2, 3, 4)))
        assertFalse(oldFile.exists())
        assertEquals(1, loads)

        backgroundStore.apply(
            TranscriptBackgroundUpdate(chat.id, 6, remove = false, mmcsXml = "ignored")
        )
        assertEquals(1, loads)
        assertEquals(appliedFile.absolutePath, store.boxFor(Chat::class.java).get(chat.id).transcriptPosterPath)

        backgroundStore.apply(
            TranscriptBackgroundUpdate(chat.id, 8, remove = true, mmcsXml = null)
        )
        val removed = store.boxFor(Chat::class.java).get(chat.id)
        assertEquals(8, removed.transcriptBackgroundVersion)
        assertNull(removed.transcriptPosterPath)
        assertFalse(appliedFile.exists())
    }

    @Test
    fun `download failure preserves the previous background and version`() = runBlocking {
        val directory = File(filesDir, "chat_backgrounds").apply { mkdirs() }
        val oldFile = File(directory, "shared-${chat.id}-2.img").apply { writeBytes(byteArrayOf(9)) }
        chat.transcriptPosterPath = oldFile.absolutePath
        chat.transcriptBackgroundVersion = 2
        store.boxFor(Chat::class.java).put(chat)
        val backgroundStore = TranscriptBackgroundStore(store, filesDir, cacheDir) { _, _ ->
            error("download failed")
        }

        assertFailsWith<IllegalStateException> {
            backgroundStore.apply(
                TranscriptBackgroundUpdate(chat.id, 3, remove = false, mmcsXml = "<plist/>")
            )
        }

        val unchanged = store.boxFor(Chat::class.java).get(chat.id)
        assertEquals(2, unchanged.transcriptBackgroundVersion)
        assertEquals(oldFile.absolutePath, unchanged.transcriptPosterPath)
        assertTrue(oldFile.isFile)
    }

    @Test
    fun `poster without a wallpaper image clears the background`() = runBlocking {
        val directory = File(filesDir, "chat_backgrounds").apply { mkdirs() }
        val oldFile = File(directory, "shared-${chat.id}-3.img").apply { writeBytes(byteArrayOf(5)) }
        chat.transcriptPosterPath = oldFile.absolutePath
        chat.transcriptBackgroundVersion = 3
        store.boxFor(Chat::class.java).put(chat)
        // Dynamic/gradient posters carry no watch image — Apple's way of
        // saying the wallpaper was cleared.
        val backgroundStore = TranscriptBackgroundStore(store, filesDir, cacheDir) { _, _ ->
            ByteArray(0)
        }

        backgroundStore.apply(
            TranscriptBackgroundUpdate(chat.id, 5, remove = false, mmcsXml = "<plist/>")
        )

        val cleared = store.boxFor(Chat::class.java).get(chat.id)
        assertEquals(5, cleared.transcriptBackgroundVersion)
        assertNull(cleared.transcriptPosterPath)
        assertFalse(oldFile.exists())
    }

    @Test
    fun `same version redownloads when the stored background file is missing`() = runBlocking {
        val missing = File(filesDir, "chat_backgrounds/shared-${chat.id}-4.img")
        chat.transcriptPosterPath = missing.absolutePath
        chat.transcriptBackgroundVersion = 4
        store.boxFor(Chat::class.java).put(chat)
        var loads = 0
        val backgroundStore = TranscriptBackgroundStore(store, filesDir, cacheDir) { _, _ ->
            loads += 1
            byteArrayOf(4, 4, 4)
        }

        backgroundStore.apply(
            TranscriptBackgroundUpdate(chat.id, 4, remove = false, mmcsXml = "<plist/>")
        )

        val repaired = store.boxFor(Chat::class.java).get(chat.id)
        val repairedFile = File(requireNotNull(repaired.transcriptPosterPath))
        assertEquals(1, loads)
        assertEquals(4, repaired.transcriptBackgroundVersion)
        assertTrue(repairedFile.isFile)
        assertTrue(repairedFile.readBytes().contentEquals(byteArrayOf(4, 4, 4)))
    }

    @Test
    fun `legacy flutter poster prefix is rewritten to a native image file`() = runBlocking {
        val prefix = File(root, "app_flutter/avatars/you/poster-9").apply { mkdirs() }
        val png = File(prefix, "layer.png").apply { writeBytes(byteArrayOf(1, 2, 3, 4)) }
        chat.transcriptPosterPath = prefix.absolutePath
        store.boxFor(Chat::class.java).put(chat)
        val backgroundStore = TranscriptBackgroundStore(store, filesDir, cacheDir) { _, _ ->
            error("legacy migration must not download")
        }

        backgroundStore.migrateLegacyPosters()

        val migrated = store.boxFor(Chat::class.java).get(chat.id)
        val dest = File(requireNotNull(migrated.transcriptPosterPath))
        assertTrue(dest.absolutePath.contains("chat_backgrounds"))
        assertTrue(dest.isFile)
        assertTrue(dest.readBytes().contentEquals(png.readBytes()))
    }

    @Test
    fun `conversation background resolution checks files and falls back to synced poster`() {
        val transcript = File(root, "transcript.img").apply { writeBytes(byteArrayOf(1)) }
        val missingCustom = File(root, "missing-custom.img")
        val item = ChatListItem(
            id = chat.id,
            title = "Chat",
            snippet = null,
            date = 0,
            unread = 0,
            pinned = false,
            avatarColor = 0,
            customBackgroundPath = missingCustom.absolutePath,
            transcriptBackgroundPath = transcript.absolutePath,
        )

        assertEquals(transcript.absolutePath, item.effectiveBackgroundPath())

        missingCustom.writeBytes(byteArrayOf(2))
        assertEquals(missingCustom.absolutePath, item.effectiveBackgroundPath())
    }

    @Test
    fun `transcript package size is bounded before allocation`() {
        assertFalse(supportedTranscriptBackgroundPackageSize(0))
        assertTrue(supportedTranscriptBackgroundPackageSize(MAX_TRANSCRIPT_BACKGROUND_PACKAGE_BYTES))
        assertFalse(supportedTranscriptBackgroundPackageSize(MAX_TRANSCRIPT_BACKGROUND_PACKAGE_BYTES + 1))

        val small = File(cacheDir, "small-poster.zip").apply { writeBytes(byteArrayOf(1, 2, 3)) }
        assertTrue(readTranscriptBackgroundPackage(small).contentEquals(byteArrayOf(1, 2, 3)))

        val oversized = File(cacheDir, "oversized-poster.zip")
        java.io.RandomAccessFile(oversized, "rw").use {
            it.setLength(MAX_TRANSCRIPT_BACKGROUND_PACKAGE_BYTES + 1)
        }
        assertFailsWith<IllegalArgumentException> {
            readTranscriptBackgroundPackage(oversized)
        }
    }

    @Test
    fun `different chats download concurrently while duplicate same-chat delivery is serialized`() = runBlocking {
        val secondChat = Chat().apply { guid = "second-chat" }
            .also(store.boxFor(Chat::class.java)::put)
        val entered = Channel<Long>(Channel.UNLIMITED)
        val release = CompletableDeferred<Unit>()
        val loads = AtomicInteger()
        val loader: suspend (String, File) -> ByteArray = { xml, _ ->
            loads.incrementAndGet()
            entered.send(xml.toLong())
            release.await()
            byteArrayOf(xml.toByte())
        }
        val backgroundStore = TranscriptBackgroundStore(store, filesDir, cacheDir, loader)
        val historyBackgroundStore = TranscriptBackgroundStore(store, filesDir, cacheDir, loader)

        val first = launch {
            backgroundStore.apply(
                TranscriptBackgroundUpdate(chat.id, 1, remove = false, mmcsXml = chat.id.toString()),
            )
        }
        val other = launch {
            backgroundStore.apply(
                TranscriptBackgroundUpdate(secondChat.id, 1, remove = false, mmcsXml = secondChat.id.toString()),
            )
        }
        val duplicate = launch {
            historyBackgroundStore.apply(
                TranscriptBackgroundUpdate(chat.id, 1, remove = false, mmcsXml = chat.id.toString()),
            )
        }

        val concurrentChats = withTimeout(2_000) { setOf(entered.receive(), entered.receive()) }
        assertEquals(setOf(chat.id, secondChat.id), concurrentChats)
        assertEquals(2, loads.get())
        release.complete(Unit)
        joinAll(first, other, duplicate)

        assertEquals(2, loads.get(), "same chat/version must not download twice")
        assertEquals(0, activeChatBackgroundLockCountForTest())
        assertTrue(File(requireNotNull(store.boxFor(Chat::class.java).get(chat.id).transcriptPosterPath)).isFile)
        assertTrue(File(requireNotNull(store.boxFor(Chat::class.java).get(secondChat.id).transcriptPosterPath)).isFile)
    }

    @Test
    fun `background lifecycle removes owned crash leftovers and preserves active and foreign files`() {
        val storage = ChatBackgroundStorage(filesDir)
        val directory = storage.directory
        val activeShared = File(directory, "shared-${chat.id}-9.img").apply { writeText("shared") }
        val activeLocal = File(directory, "local-${chat.id}-123e4567-e89b-12d3-a456-426614174000.jpg")
            .apply { writeText("local") }
        val orphan = File(directory, "shared-999-1.img").apply { writeText("orphan") }
        val interruptedStage = File(directory, ".ob-background-123.tmp").apply { writeText("stage") }
        val oldBackup = File(
            directory,
            "local-999-123e4567-e89b-12d3-a456-426614174000.jpg.bak",
        ).apply { writeText("backup") }
        val tombstone = File(directory, "shared-999-2.img.tombstone").apply { writeText("gone") }
        val foreign = File(directory, "wallpaper.jpg").apply { writeText("user") }
        val misleadingForeign = File(directory, "local-wallpaper.jpg").apply { writeText("user") }

        val removed = storage.reconcile { listOf(activeShared.absolutePath, activeLocal.absolutePath) }

        assertEquals(4, removed)
        assertTrue(activeShared.isFile)
        assertTrue(activeLocal.isFile)
        assertTrue(foreign.isFile)
        assertTrue(misleadingForeign.isFile)
        assertFalse(orphan.exists())
        assertFalse(interruptedStage.exists())
        assertFalse(oldBackup.exists())
        assertFalse(tombstone.exists())
    }

    @Test
    fun `failed database publication rolls back new background without deleting previous`() {
        val storage = ChatBackgroundStorage(filesDir)
        val previous = File(
            storage.directory,
            "local-${chat.id}-123e4567-e89b-12d3-a456-426614174000.jpg",
        ).apply { writeText("old") }

        assertFailsWith<IllegalStateException> {
            storage.commitBytes(
                destinationName = "local-${chat.id}-123e4567-e89b-12d3-a456-426614174001.jpg",
                bytes = "new".toByteArray(),
                previousPath = previous.absolutePath,
            ) { error("database failed") }
        }

        assertTrue(previous.isFile)
        assertFalse(
            File(
                storage.directory,
                "local-${chat.id}-123e4567-e89b-12d3-a456-426614174001.jpg",
            ).exists(),
        )
    }

    @Test
    fun `failed tombstone publication preserves the referenced background`() {
        val storage = ChatBackgroundStorage(filesDir)
        val previous = File(storage.directory, "shared-${chat.id}-3.img").apply { writeText("old") }

        assertFailsWith<IllegalStateException> {
            storage.commitRemoval(previous.absolutePath) { error("database failed") }
        }

        assertTrue(previous.isFile)
    }

    @Test
    fun `reconcile snapshots active paths inside the publication lock`() {
        val storage = ChatBackgroundStorage(filesDir)
        val old = File(storage.directory, "shared-${chat.id}-3.img").apply { writeText("old") }
        val active = java.util.concurrent.atomic.AtomicReference<String?>(old.absolutePath)
        val snapshotEntered = CountDownLatch(1)
        val releaseSnapshot = CountDownLatch(1)
        val publicationAttempted = CountDownLatch(1)
        val publicationRan = CountDownLatch(1)
        val executor = Executors.newFixedThreadPool(2)
        try {
            val reconcile = executor.submit<Int> {
                storage.reconcile {
                    snapshotEntered.countDown()
                    check(releaseSnapshot.await(2, TimeUnit.SECONDS))
                    listOf(active.get())
                }
            }
            assertTrue(snapshotEntered.await(2, TimeUnit.SECONDS))
            val publish = executor.submit<File> {
                publicationAttempted.countDown()
                storage.commitBytes(
                    destinationName = "shared-${chat.id}-4.img",
                    bytes = "new".toByteArray(),
                    previousPath = active.get(),
                ) { destination ->
                    active.set(destination.absolutePath)
                    publicationRan.countDown()
                }
            }

            assertTrue(publicationAttempted.await(2, TimeUnit.SECONDS))
            assertFalse(
                publicationRan.await(100, TimeUnit.MILLISECONDS),
                "publication must wait until the active-path snapshot and scan finish",
            )
            releaseSnapshot.countDown()
            reconcile.get(2, TimeUnit.SECONDS)
            val published = publish.get(2, TimeUnit.SECONDS)

            assertEquals(published.absolutePath, active.get())
            assertTrue(published.isFile)
            assertFalse(old.exists())
        } finally {
            releaseSnapshot.countDown()
            executor.shutdownNow()
        }
    }

    @Test
    fun `legacy migration does not retain locks for chats without posters`() = runBlocking {
        repeat(500) { index ->
            Chat().apply { guid = "plain-$index" }.also(store.boxFor(Chat::class.java)::put)
        }
        val backgroundStore = TranscriptBackgroundStore(store, filesDir, cacheDir) { _, _ ->
            error("migration must not download")
        }

        backgroundStore.migrateLegacyPosters()

        assertEquals(0, activeChatBackgroundLockCountForTest())
    }
}

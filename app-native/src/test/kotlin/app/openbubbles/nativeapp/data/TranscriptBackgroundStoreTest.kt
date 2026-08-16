package app.openbubbles.nativeapp.data

import app.openbubbles.core.sync.TranscriptBackgroundUpdate
import app.openbubbles.db.Chat
import app.openbubbles.db.MyObjectBox
import io.objectbox.BoxStore
import java.io.File
import java.nio.file.Files
import kotlinx.coroutines.runBlocking
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
}

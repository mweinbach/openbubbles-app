package app.openbubbles.core.backup

import app.openbubbles.core.backup.BackupManager.BackupInfo
import app.openbubbles.db.Attachment
import app.openbubbles.db.Chat
import app.openbubbles.db.Db
import app.openbubbles.db.Handle
import app.openbubbles.db.Message
import io.objectbox.BoxStore
import org.junit.After
import org.junit.Before
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Round-trip, manifest and corruption tests for [BackupManager]. Uses a real
 * temp ObjectBox store in the production layout (`<root>/objectbox` +
 * `<root>/attachments/<guid>/<name>`), so the swap mechanics are exercised
 * exactly as on device.
 */
class BackupManagerTest {

    private lateinit var root: File
    private lateinit var store: BoxStore
    private lateinit var manager: BackupManager

    @Before
    fun setUp() {
        root = java.nio.file.Files.createTempDirectory("ob-backup-test").toFile()
        store = Db.build(root)
        manager = BackupManager(root, { store }, PassThroughStoreGate, appVersion = "test-1")
    }

    @After
    fun tearDown() {
        runCatching { store.close() }
        root.deleteRecursively()
    }

    // ------------------------------------------------------------------
    // Fixtures
    // ------------------------------------------------------------------

    private fun seedData(): Pair<String, ByteArray> {
        val chatBox = store.boxFor(Chat::class.java)
        val messageBox = store.boxFor(Message::class.java)
        val attachmentBox = store.boxFor(Attachment::class.java)

        val handle = Handle().apply { address = "+15550001111"; service = "iMessage" }
        store.boxFor(Handle::class.java).put(handle)
        val chatRow = Chat().apply { guid = "chat-1"; handles.add(handle) }
        chatBox.put(chatRow)
        messageBox.put(Message().apply {
            guid = "msg-1"; text = "hello backup"; chat.target = chatRow
        })
        messageBox.put(Message().apply {
            guid = "msg-2"; text = "second message"; chat.target = chatRow
        })

        val payload = "png-bytes-0123456789".toByteArray()
        val attachment = Attachment().apply {
            guid = "att-1"; transferName = "pic.png"; mimeType = "image/png"
        }
        attachmentBox.put(attachment)
        val dir = File(root, "attachments/att-1")
        dir.mkdirs()
        File(dir, "pic.png").writeBytes(payload)
        return "hello backup" to payload
    }

    private fun snapshotBytes(progress: MutableList<String> = mutableListOf()): Pair<BackupInfo, ByteArray> {
        val out = ByteArrayOutputStream()
        val result = manager.snapshot(out) { progress.add(it) }
        assertTrue(result.isSuccess, "snapshot failed: ${result.exceptionOrNull()}")
        return result.getOrThrow() to out.toByteArray()
    }

    private fun zipEntries(zipBytes: ByteArray): List<String> {
        val tmp = java.nio.file.Files.createTempFile("ob-zip-inspect", ".zip").toFile()
        tmp.writeBytes(zipBytes)
        return ZipFile(tmp).use { zf -> zf.entries().asSequence().map { it.name }.toList() }
            .also { tmp.delete() }
    }

    private fun manifestOf(zipBytes: ByteArray): String {
        val tmp = java.nio.file.Files.createTempFile("ob-zip-manifest", ".zip").toFile()
        tmp.writeBytes(zipBytes)
        return ZipFile(tmp).use { zf ->
            zf.getInputStream(zf.getEntry(BackupManager.MANIFEST_ENTRY))
                .use { it.readBytes().toString(Charsets.UTF_8) }
        }.also { tmp.delete() }
    }

    /** Reopens a fresh store on [root] (must be called after the old one closed). */
    private fun messageTexts(): List<String> {
        val box = store.boxFor(Message::class.java)
        return box.query().build().use { it.find() }.mapNotNull { it.text }.sorted()
    }

    // ------------------------------------------------------------------
    // Round trip
    // ------------------------------------------------------------------

    @Test
    fun `snapshot and restore round-trips chats messages and attachments`() {
        val (text, payload) = seedData()

        val (info, zipBytes) = snapshotBytes()
        assertEquals(2L, info.messageCount)
        assertEquals(1L, info.attachmentCount)
        assertEquals("test-1", info.appVersion)
        assertTrue(info.dateEpochMs > 0L)

        assertTrue(zipEntries(zipBytes).containsAll(listOf(
            BackupManager.MANIFEST_ENTRY,
            "objectbox/data.mdb",
            "attachments/att-1/pic.png",
        )), "zip missing expected entries: ${zipEntries(zipBytes)}")

        // Wipe: close, delete store + attachments, then restore into root.
        store.close()
        File(root, "objectbox").deleteRecursively()
        File(root, "attachments").deleteRecursively()

        val restored = manager.restore(ByteArrayInputStream(zipBytes), root)
        assertTrue(restored.isSuccess, "restore failed: ${restored.exceptionOrNull()}")
        assertEquals(info.messageCount, restored.getOrThrow().messageCount)

        // Reopen: rows and the attachment payload must be back.
        store = Db.build(root)
        assertEquals(listOf(text, "second message"), messageTexts())
        val attachmentFile = File(root, "attachments/att-1/pic.png")
        assertTrue(attachmentFile.isFile, "attachment payload not restored")
        assertTrue(attachmentFile.readBytes().contentEquals(payload), "attachment payload differs")
        assertEquals(1L, store.boxFor(Attachment::class.java).count())
    }

    @Test
    fun `restore replaces existing data with archive contents`() {
        seedData()
        val (_, zipBytes) = snapshotBytes()

        // Mutate live data after the snapshot: the restore must win.
        store.boxFor(Message::class.java)
            .put(Message().apply { guid = "msg-post-snapshot"; text = "later addition" })
        store.close()

        val restored = manager.restore(ByteArrayInputStream(zipBytes), root)
        assertTrue(restored.isSuccess, "restore failed: ${restored.exceptionOrNull()}")

        store = Db.build(root)
        assertEquals(listOf("hello backup", "second message"), messageTexts())
    }

    // ------------------------------------------------------------------
    // Manifest
    // ------------------------------------------------------------------

    @Test
    fun `manifest records format version date counts and app version`() {
        seedData()
        val (info, zipBytes) = snapshotBytes()

        val manifest = manifestOf(zipBytes)
        assertTrue("\"formatVersion\":1" in manifest, manifest)
        assertTrue("\"dateEpochMs\":${info.dateEpochMs}" in manifest, manifest)
        assertTrue("\"messageCount\":2" in manifest, manifest)
        assertTrue("\"attachmentCount\":1" in manifest, manifest)
        assertTrue("\"appVersion\":\"test-1\"" in manifest, manifest)
        assertTrue(Regex("\"dbBytes\":\\d+").containsMatchIn(manifest), manifest)

        val entries = zipEntries(zipBytes)
        assertEquals(1, entries.count { it.startsWith("objectbox/") && !it.contains("lock") },
            "only data.mdb (no lock.mdb) should be archived: $entries")
    }

    // ------------------------------------------------------------------
    // Corruption / rejection — original data must survive every failure
    // ------------------------------------------------------------------

    private fun assertOriginalDataIntact(failure: Result<BackupInfo>, label: String) {
        assertTrue(failure.isFailure, "$label should fail")
        val message = failure.exceptionOrNull()?.message ?: failure.exceptionOrNull().toString()
        assertTrue(
            message.isNotBlank(),
            "$label should fail with a human-readable error",
        )
        assertEquals(listOf("hello backup", "second message"), messageTexts(), "$label damaged live data")
        assertTrue(File(root, "attachments/att-1/pic.png").isFile, "$label damaged attachments")
        assertTrue(File(root, "objectbox/data.mdb").isFile, "$label damaged the store")
    }

    @Test
    fun `restore rejects garbage input with a clean error`() {
        seedData()
        val failure = manager.restore(ByteArrayInputStream("definitely not a zip".toByteArray()), root)
        assertOriginalDataIntact(failure, "garbage input")
    }

    @Test
    fun `restore rejects a corrupted zip and leaves data intact`() {
        seedData()
        val (_, zipBytes) = snapshotBytes()

        // Corrupt a chunk in the middle of the archive (entry data region —
        // the central directory sits at the end and stays valid, so the
        // failure must come from CRC/inflation, exactly the case we guard).
        val corrupted = zipBytes.copyOf()
        val from = corrupted.size / 2
        for (i in from until minOf(from + 64, corrupted.size)) {
            corrupted[i] = (corrupted[i].toInt() xor 0x5A).toByte()
        }

        store.close()
        val failure = manager.restore(ByteArrayInputStream(corrupted), root)
        store = Db.build(root)
        assertOriginalDataIntact(failure, "corrupted zip")
    }

    @Test
    fun `restore rejects a zip without a manifest`() {
        seedData()
        val zip = ByteArrayOutputStream().use { out ->
            ZipOutputStream(out).use { zos ->
                zos.putNextEntry(ZipEntry("objectbox/data.mdb"))
                zos.write(ByteArray(1024))
                zos.closeEntry()
            }
            out.toByteArray()
        }
        val failure = manager.restore(ByteArrayInputStream(zip), root)
        assertOriginalDataIntact(failure, "missing manifest")
    }

    @Test
    fun `restore rejects an unsupported manifest version`() {
        seedData()
        val zip = ByteArrayOutputStream().use { out ->
            ZipOutputStream(out).use { zos ->
                zos.putNextEntry(ZipEntry(BackupManager.MANIFEST_ENTRY))
                zos.write("{\"formatVersion\":99,\"dateEpochMs\":1,\"messageCount\":0,\"attachmentCount\":0}".toByteArray())
                zos.closeEntry()
            }
            out.toByteArray()
        }
        val failure = manager.restore(ByteArrayInputStream(zip), root)
        assertOriginalDataIntact(failure, "unsupported version")
        val error = failure.exceptionOrNull()?.message
        assertTrue(error?.contains("format version") == true, "unexpected error: $error")
    }

    @Test
    fun `restore rejects a manifest without a database file`() {
        seedData()
        val zip = ByteArrayOutputStream().use { out ->
            ZipOutputStream(out).use { zos ->
                zos.putNextEntry(ZipEntry(BackupManager.MANIFEST_ENTRY))
                zos.write("{\"formatVersion\":1,\"dateEpochMs\":1,\"messageCount\":0,\"attachmentCount\":0,\"dbBytes\":4}".toByteArray())
                zos.closeEntry()
                zos.putNextEntry(ZipEntry("attachments/att-1/pic.png"))
                zos.write(ByteArray(4))
                zos.closeEntry()
            }
            out.toByteArray()
        }
        val failure = manager.restore(ByteArrayInputStream(zip), root)
        assertOriginalDataIntact(failure, "missing db file")
    }

    @Test
    fun `restore rejects zip-slip entries`() {
        seedData()
        val zip = ByteArrayOutputStream().use { out ->
            ZipOutputStream(out).use { zos ->
                zos.putNextEntry(ZipEntry(BackupManager.MANIFEST_ENTRY))
                zos.write("{\"formatVersion\":1,\"dateEpochMs\":1,\"messageCount\":0,\"attachmentCount\":0,\"dbBytes\":4}".toByteArray())
                zos.closeEntry()
                zos.putNextEntry(ZipEntry("objectbox/data.mdb"))
                zos.write(ByteArray(4))
                zos.closeEntry()
                zos.putNextEntry(ZipEntry("attachments/../../evil.txt"))
                zos.write("pwn".toByteArray())
                zos.closeEntry()
            }
            out.toByteArray()
        }
        val failure = manager.restore(ByteArrayInputStream(zip), root)
        assertOriginalDataIntact(failure, "zip-slip")
        assertTrue(!File(root.parentFile, "evil.txt").exists(), "zip-slip escaped the target dir")
        assertTrue(!File(root, "evil.txt").exists(), "zip-slip wrote inside the target dir")
    }

    // ------------------------------------------------------------------
    // Snapshot failure mode
    // ------------------------------------------------------------------

    @Test
    fun `snapshot fails cleanly when the store is unavailable`() {
        val broken = BackupManager(root, { null }, PassThroughStoreGate)
        val result = broken.snapshot(ByteArrayOutputStream()) {}
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()?.message?.contains("store unavailable") == true)
    }
}

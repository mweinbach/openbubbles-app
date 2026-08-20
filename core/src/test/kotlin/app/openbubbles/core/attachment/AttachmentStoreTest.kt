package app.openbubbles.core.attachment

import app.openbubbles.db.Attachment
import app.openbubbles.db.MyObjectBox
import io.objectbox.BoxStore
import io.objectbox.query.QueryBuilder
import org.junit.After
import org.junit.Before
import org.junit.Test
import java.io.File
import java.io.IOException
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Disk-layout, sanitization and traversal-guard tests for [AttachmentStore]
 * (the `FilesystemService`/`Attachment.path` port). Own temp-dir store, same
 * pattern as the db-module tests.
 */
class AttachmentStoreTest {

    private lateinit var store: BoxStore
    private lateinit var testDir: File
    private lateinit var rootDir: File
    private lateinit var disk: AttachmentStore

    @Before
    fun setUp() {
        testDir = java.nio.file.Files.createTempDirectory("ob-att-store-test").toFile()
        store = MyObjectBox.builder().directory(testDir).build()
        rootDir = File(testDir, "appdocs")
        disk = AttachmentStore(store, rootDir)
    }

    @After
    fun tearDown() {
        store.close()
        testDir.deleteRecursively()
    }

    private fun attachment(guid: String, transferName: String?): Attachment =
        Attachment().apply {
            this.guid = guid
            this.transferName = transferName
        }.also { store.boxFor(Attachment::class.java).put(it) }

    private fun stored(guid: String): Attachment? =
        store.boxFor(Attachment::class.java).query()
            .equal(app.openbubbles.db.Attachment_.guid, guid, QueryBuilder.StringOrder.CASE_SENSITIVE)
            .build().use { it.findFirst() }

    // ------------------------------------------------------------------
    // Layout
    // ------------------------------------------------------------------

    @Test
    fun `group icon path is versioned under group_icons`() {
        val path = disk.groupIconFile(42L, "rec/family", 3)
        assertEquals(File(rootDir, "group_icons"), disk.groupIconsDir)
        assertEquals(disk.groupIconsDir, path.parentFile)
        assertEquals("42-rec_family-3.png", path.name)
    }

    @Test
    fun `path layout is attachments guid transferName`() {
        val att = attachment("att-1", "pic.png")
        val path = disk.pathFor(att)
        assertEquals(File(rootDir, "attachments"), disk.attachmentsDir)
        assertEquals(File(disk.attachmentsDir, "att-1"), path.parentFile)
        assertEquals("pic.png", path.name)
    }

    @Test
    fun `parent directories are created on demand`() {
        val att = attachment("att-2", "movie.mov")
        val dir = disk.directoryFor("att-2")
        assertTrue(!dir.exists()) // pure computation — nothing written
        dir.mkdirs()
        assertTrue(File(dir, "movie.mov").let { f -> f.createNewFile() || f.isFile })
    }

    @Test
    fun `local directory is promoted from temp guid to real guid`() {
        val oldDir = disk.directoryFor("temp-upload_att0").apply { mkdirs() }
        val payload = File(oldDir, "photo.jpg").apply { writeText("image bytes") }

        assertTrue(disk.promoteLocalDirectory("temp-upload_att0", "real-message_0"))
        assertTrue(!oldDir.exists())
        assertEquals(
            "image bytes",
            File(disk.directoryFor("real-message_0"), payload.name).readText(),
        )
    }

    @Test
    fun `local directory promotion never overwrites an existing real directory`() {
        disk.directoryFor("temp-upload_att0").apply { mkdirs() }
        val existing = disk.directoryFor("real-message_0").apply { mkdirs() }
        File(existing, "keep.jpg").writeText("keep")

        assertTrue(!disk.promoteLocalDirectory("temp-upload_att0", "real-message_0"))
        assertEquals("keep", File(existing, "keep.jpg").readText())
    }

    // ------------------------------------------------------------------
    // Sanitization + traversal guard
    // ------------------------------------------------------------------

    @Test
    fun `path separators in transfer name are neutralized`() {
        val att = attachment("att-3", "../../../etc/passwd")
        val path = disk.pathFor(att)
        assertEquals(File(disk.attachmentsDir, "att-3"), path.parentFile)
        assertEquals(".._.._.._etc_passwd", path.name)
        // And the canonicalization guard agrees it stayed inside.
        disk.ensureInside(disk.directoryFor("att-3"), path)
    }

    @Test
    fun `windows hostile characters are replaced`() {
        assertEquals("a_b__c_d_e_f_g_h", disk.sanitizeFileName("a<b>:c\"d|e?f*g\\h"))
    }

    @Test
    fun `dot and empty names fall back to unknown`() {
        assertEquals("unknown", disk.sanitizeFileName(".."))
        assertEquals("unknown", disk.sanitizeFileName("."))
        assertEquals("unknown", disk.sanitizeFileName(""))
        assertEquals("unknown", disk.sanitizeFileName(null))
        assertEquals("unknown", disk.sanitizeFileName("  "))
    }

    @Test
    fun `guid is sanitized into a single directory component`() {
        assertEquals("a_b_c", disk.sanitizeDirectoryName("a/b\\c"))
        val att = attachment("a/b\\c", "x.png")
        assertEquals(File(disk.attachmentsDir, "a_b_c"), disk.pathFor(att).parentFile)
    }

    @Test
    fun `traversal guard rejects paths outside the attachment directory`() {
        val dir = disk.directoryFor("att-4")
        assertFailsWith<IOException> {
            disk.ensureInside(dir, File(dir, "../../escape.png"))
        }
        assertFailsWith<IOException> {
            disk.ensureInside(dir, File(rootDir, "evil.png"))
        }
    }

    @Test
    fun `attachment without guid has no derivable path`() {
        assertFailsWith<IOException> { disk.pathFor(Attachment()) }
    }

    // ------------------------------------------------------------------
    // Downloaded state
    // ------------------------------------------------------------------

    @Test
    fun `markDownloaded persists flag and real byte length`() {
        attachment("att-5", "a.png")
        val updated = disk.markDownloaded("att-5", 1234L)
        assertNotNull(updated)
        assertTrue(updated.isDownloaded)
        assertEquals(1234L, updated.totalBytes)

        val row = stored("att-5")
        assertNotNull(row)
        assertTrue(row.isDownloaded)
        assertEquals(1234L, row.totalBytes)
    }

    @Test
    fun `markDownloaded ignores non positive sizes and unknown guids`() {
        val att = attachment("att-6", "b.png")
        att.totalBytes = 42L
        store.boxFor(Attachment::class.java).put(att)

        disk.markDownloaded("att-6", 0L)
        assertEquals(42L, stored("att-6")?.totalBytes)
        assertTrue(stored("att-6")!!.isDownloaded)

        assertNull(disk.markDownloaded("nope"))
    }

    // ------------------------------------------------------------------
    // Existing-file resolution + cleanup
    // ------------------------------------------------------------------

    @Test
    fun `existingFile prefers primary then converted png`() {
        val att = attachment("att-7", "c.heic")
        assertNull(disk.existingFile(att))

        val dir = disk.directoryFor("att-7").apply { mkdirs() }
        val converted = File(dir, "c.heic.png").apply { writeText("png") }
        assertEquals(converted, disk.existingFile(att))

        val primary = File(dir, "c.heic").apply { writeText("heic") }
        assertEquals(primary, disk.existingFile(att))
    }

    @Test
    fun `existingFile rejects empty and wrong-size canonical payloads`() {
        val att = attachment("att-sized", "payload.bin").apply {
            totalBytes = 4L
            store.boxFor(Attachment::class.java).put(this)
        }
        val primary = disk.pathFor(att).apply {
            parentFile.mkdirs()
            writeBytes(byteArrayOf())
        }
        assertNull(disk.existingFile(att))

        primary.writeBytes(byteArrayOf(1, 2, 3))
        assertNull(disk.existingFile(att))

        primary.writeBytes(byteArrayOf(1, 2, 3, 4))
        assertEquals(primary, disk.existingFile(att))
    }

    @Test
    fun `deleteLocalFiles removes payload and converted variants`() {
        val att = attachment("att-8", "d.png")
        val dir = disk.directoryFor("att-8").apply { mkdirs() }
        val primary = File(dir, "d.png").apply { writeText("x") }
        val converted = File(dir, "d.png.png").apply { writeText("x") }
        val thumb = File(dir, "d.png.thumbnail").apply { writeText("x") }
        val partial = File(dir, ".d.png.openbubbles-partial").apply { writeText("partial") }

        disk.deleteLocalFiles(att)
        assertTrue(!primary.exists())
        assertTrue(!converted.exists())
        assertTrue(!thumb.exists())
        assertTrue(!partial.exists())
    }
}

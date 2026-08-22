package app.openbubbles.nativeapp.sms

import java.io.ByteArrayInputStream
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.nio.file.Files
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Test

class IncomingMmsMediaTest {
    private lateinit var root: File

    @Before
    fun setUp() {
        root = Files.createTempDirectory("incoming-mms-media").toFile()
    }

    @After
    fun tearDown() {
        root.deleteRecursively()
    }

    @Test
    fun `carrier payload remains invisible until validated sibling publishes`() {
        val destination = root.resolve("attachments/mms-1_0/photo.jpg")
        val bytes = byteArrayOf(1, 2, 3, 4)

        val staged = stageIncomingMmsMedia(listOf(source(destination, bytes)))

        assertFalse(destination.exists())
        assertTrue(staged.single().partial.isFile)
        assertEquals(
            requireNotNull(destination.parentFile).canonicalFile,
            requireNotNull(staged.single().partial.parentFile).canonicalFile,
        )
        assertTrue(staged.single().partial.name.endsWith(".mms-part"))

        assertEquals(listOf(destination.canonicalFile), publishIncomingMmsMedia(staged))
        assertContentEquals(bytes, destination.readBytes())
        assertFalse(staged.single().partial.exists())
    }

    @Test
    fun `each retry reserves its own unique atomic sibling`() {
        val destination = root.resolve("attachments/mms-1_0/photo.jpg")
        val first = stageIncomingMmsMedia(listOf(source(destination, byteArrayOf(1))))
        val second = stageIncomingMmsMedia(listOf(source(destination, byteArrayOf(2))))

        assertFalse(first.single().partial == second.single().partial)
        discardIncomingMmsMedia(first)
        discardIncomingMmsMedia(second)
        assertFalse(destination.exists())
    }

    @Test
    fun `oversized provider stream never publishes and cleans every owned partial`() {
        val destination = root.resolve("attachments/mms-2_0/large.jpg")

        assertFailsWith<IOException> {
            stageIncomingMmsMedia(
                sources = listOf(source(destination, ByteArray(9))),
                maxPartBytes = 8,
                maxMessageBytes = 16,
            )
        }

        assertFalse(destination.exists())
        assertTrue(requireNotNull(destination.parentFile).listFiles().orEmpty().isEmpty())
    }

    @Test
    fun `aggregate byte ceiling covers all provider parts`() {
        val first = root.resolve("attachments/mms-3_0/one.jpg")
        val second = root.resolve("attachments/mms-3_1/two.jpg")

        assertFailsWith<IOException> {
            stageIncomingMmsMedia(
                sources = listOf(
                    source(first, byteArrayOf(1, 2, 3)),
                    source(second, byteArrayOf(4, 5, 6)),
                ),
                maxPartBytes = 5,
                maxMessageBytes = 5,
            )
        }

        assertFalse(first.exists())
        assertFalse(second.exists())
        assertTrue(requireNotNull(first.parentFile).listFiles().orEmpty().isEmpty())
        assertTrue(requireNotNull(second.parentFile).listFiles().orEmpty().isEmpty())
    }

    @Test
    fun `provider length mismatch and empty media remain retryable failures`() {
        val mismatched = root.resolve("attachments/mms-4_0/mismatch.jpg")
        assertFailsWith<IOException> {
            stageIncomingMmsMedia(
                listOf(
                    IncomingMmsMediaSource(mismatched, expectedBytes = 10) {
                        ByteArrayInputStream(byteArrayOf(1, 2, 3))
                    },
                ),
            )
        }

        val empty = root.resolve("attachments/mms-4_1/empty.jpg")
        assertFailsWith<IOException> {
            stageIncomingMmsMedia(listOf(source(empty, byteArrayOf())))
        }

        assertFalse(mismatched.exists())
        assertFalse(empty.exists())
    }

    @Test
    fun `failed replacement preserves the last valid canonical payload`() {
        val destination = root.resolve("attachments/mms-5_0/photo.jpg").apply {
            requireNotNull(parentFile).mkdirs()
            writeBytes(byteArrayOf(8, 9))
        }
        val staged = stageIncomingMmsMedia(listOf(source(destination, byteArrayOf(1, 2, 3))))
        staged.single().partial.delete()

        assertFailsWith<IOException> { publishIncomingMmsMedia(staged) }
        assertContentEquals(byteArrayOf(8, 9), destination.readBytes())
    }

    @Test
    fun `second provider stream failure leaves first canonical payload invisible`() {
        val first = root.resolve("attachments/mms-6_0/one.jpg")
        val second = root.resolve("attachments/mms-6_1/two.jpg")

        assertFailsWith<IOException> {
            stageIncomingMmsMedia(
                listOf(
                    source(first, byteArrayOf(1, 2, 3)),
                    IncomingMmsMediaSource(second) {
                        object : InputStream() {
                            override fun read(): Int = throw IOException("provider temporarily unavailable")
                        }
                    },
                ),
            )
        }

        assertFalse(first.exists())
        assertFalse(second.exists())
        assertTrue(requireNotNull(first.parentFile).listFiles().orEmpty().isEmpty())
    }

    @Test
    fun `duplicate canonical destinations fail before publication`() {
        val destination = root.resolve("attachments/mms-7_0/photo.jpg")

        assertFailsWith<IOException> {
            stageIncomingMmsMedia(
                listOf(
                    source(destination, byteArrayOf(1)),
                    source(destination, byteArrayOf(2)),
                ),
            )
        }

        assertFalse(destination.exists())
        assertTrue(requireNotNull(destination.parentFile).listFiles().orEmpty().isEmpty())
    }

    @Test
    fun `excessive provider part count is rejected before files are created`() {
        val sources = (0..MAX_INCOMING_MMS_PARTS).map { index ->
            source(root.resolve("attachments/mms-8_$index/photo.jpg"), byteArrayOf(1))
        }

        assertFailsWith<IllegalArgumentException> { stageIncomingMmsMedia(sources) }
        assertFalse(root.resolve("attachments").exists())
    }

    @Test
    fun `provider id is acknowledged only after a successful retry`() = runBlocking {
        val gate = MmsIngestionGate()
        var attempts = 0

        assertFalse(gate.process(41L) { attempts++; false })
        assertTrue(gate.process(41L) { attempts++; true })
        assertTrue(gate.process(41L) { attempts++; true })

        assertEquals(2, attempts)
    }

    @Test
    fun `provider exceptions leave the message eligible for retry`() = runBlocking {
        val gate = MmsIngestionGate()
        assertFailsWith<IOException> {
            gate.process(42L) { throw IOException("database unavailable") }
        }

        var retried = false
        assertTrue(gate.process(42L) { retried = true; true })
        assertTrue(retried)
    }

    @Test
    fun `published media remains recoverable when durable row update fails`() = runBlocking {
        val gate = MmsIngestionGate()
        val destination = root.resolve("attachments/mms-9_0/photo.jpg")
        val bytes = byteArrayOf(3, 4, 5)

        assertFalse(
            gate.process(43L) {
                publishIncomingMmsMedia(stageIncomingMmsMedia(listOf(source(destination, bytes))))
                false
            },
        )
        assertContentEquals(bytes, destination.readBytes())

        assertTrue(
            gate.process(43L) {
                publishIncomingMmsMedia(stageIncomingMmsMedia(listOf(source(destination, bytes))))
                true
            },
        )
        assertContentEquals(bytes, destination.readBytes())
    }

    @Test
    fun `processed provider id memory remains bounded`() = runBlocking {
        val gate = MmsIngestionGate(capacity = 2)
        var firstRuns = 0

        assertTrue(gate.process(1) { firstRuns++; true })
        assertTrue(gate.process(2) { true })
        assertTrue(gate.process(3) { true })
        assertTrue(gate.process(1) { firstRuns++; true })

        assertEquals(2, firstRuns)
    }

    private fun source(destination: File, bytes: ByteArray): IncomingMmsMediaSource =
        IncomingMmsMediaSource(destination) { ByteArrayInputStream(bytes) }
}

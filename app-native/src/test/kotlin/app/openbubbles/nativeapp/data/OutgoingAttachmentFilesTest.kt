package app.openbubbles.nativeapp.data

import java.nio.file.Files
import org.junit.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class OutgoingAttachmentFilesTest {
    @Test
    fun `prepared payload is moved into canonical storage`() {
        val root = Files.createTempDirectory("outgoing-move").toFile()
        try {
            val source = root.resolve("cache/photo.jpg").apply {
                parentFile.mkdirs()
                writeBytes(byteArrayOf(1, 2, 3))
            }
            val destination = root.resolve("app_flutter/attachments/temp/photo.jpg")

            val moved = moveOutgoingAttachment(source, destination)

            assertFalse(source.exists())
            assertTrue(moved.isFile)
            assertContentEquals(byteArrayOf(1, 2, 3), moved.readBytes())
        } finally {
            root.deleteRecursively()
        }
    }
}

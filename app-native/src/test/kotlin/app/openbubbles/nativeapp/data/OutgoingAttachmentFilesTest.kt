package app.openbubbles.nativeapp.data

import java.nio.file.Files
import kotlin.coroutines.Continuation
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.junit.Test
import uniffi.rust_lib_bluebubbles.NativePushState
import uniffi.rust_lib_bluebubbles.UProgressCallback
import uniffi.rust_lib_bluebubbles.USendAttachmentsRequest

class OutgoingAttachmentFilesTest {
    @Test
    fun `prepared payload is moved into canonical storage`() {
        val root = Files.createTempDirectory("outgoing-move").toFile()
        try {
            val source = root.resolve("cache/photo.jpg").apply {
                requireNotNull(parentFile).mkdirs()
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

    @Test
    fun `iMessage attachment sends avoid the foreign progress callback`() {
        assertNull(attachmentSendProgressCallback())
    }

    @Test
    fun `iMessage attachment send binding stays compact and suspending`() {
        val method = NativePushState::class.java.methods.single { it.name == "sendAttachments" }

        assertEquals(3, method.parameterCount)
        assertEquals(USendAttachmentsRequest::class.java, method.parameterTypes[0])
        assertEquals(UProgressCallback::class.java, method.parameterTypes[1])
        assertEquals(Continuation::class.java, method.parameterTypes.last())
    }
}

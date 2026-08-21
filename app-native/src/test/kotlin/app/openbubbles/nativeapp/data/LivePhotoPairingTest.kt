package app.openbubbles.nativeapp.data

import app.openbubbles.db.Attachment
import java.io.File
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class LivePhotoPairingTest {
    @Test
    fun `visible attachment metadata hides motion sidecar`() {
        val still = Attachment().apply {
            guid = "message_0"
            mimeType = "image/heic"
            transferName = "IMG_0001.HEIC"
            metadata = mutableMapOf("livePhotoMotionGuid" to "message_0_iris")
        }
        val motion = Attachment().apply {
            guid = "message_0_iris"
            mimeType = "video/quicktime"
            transferName = "IMG_0001.MOV"
            metadata = mutableMapOf("livePhotoMotion" to true)
        }

        val visible = visibleAttachmentMetas(listOf(still, motion))

        assertEquals(1, visible.size)
        assertEquals("message_0_iris", visible.single().livePhotoMotionGuid)
        assertFalse(visible.single().livePhotoMotionDownloaded)

        motion.isDownloaded = true
        assertTrue(visibleAttachmentMetas(listOf(still, motion)).single().livePhotoMotionDownloaded)
    }

    @Test
    fun `heic and mov siblings pair without iris metadata`() {
        val still = Attachment().apply {
            guid = "message_0"
            mimeType = "image/heic"
            transferName = "IMG_0042.HEIC"
        }
        val motion = Attachment().apply {
            guid = "message_1"
            mimeType = "video/quicktime"
            transferName = "IMG_0042.MOV"
        }

        val visible = visibleAttachmentMetas(listOf(still, motion))

        assertEquals(1, visible.size)
        assertEquals("message_1", visible.single().livePhotoMotionGuid)
        assertEquals(
            listOf("message_0", "message_1"),
            livePhotoTransferGuids(still, listOf(still, motion)),
        )
    }

    @Test
    fun `viewer resolves both files and tolerates missing motion`() {
        val directory = Files.createTempDirectory("live-photo-pair").toFile()
        try {
            val stillFile = File(directory, "photo.heic").apply { writeBytes(byteArrayOf(1)) }
            val motionFile = File(directory, "photo.mov").apply { writeBytes(byteArrayOf(2)) }
            val still = AttachmentMeta(
                guid = "still",
                mime = "image/heic",
                name = "photo.heic",
                sizeBytes = 1,
                isImage = true,
                downloaded = true,
                livePhotoMotionGuid = "motion",
            )
            val motion = AttachmentMeta(
                guid = "motion",
                mime = "video/quicktime",
                name = "photo.mov",
                sizeBytes = 1,
                isImage = false,
                downloaded = true,
                isLivePhotoMotion = true,
            )
            val provider = FakePairProvider(mapOf("still" to still, "motion" to motion), mapOf("still" to stillFile, "motion" to motionFile))

            val complete = assertNotNull(resolveLivePhotoPair(still, provider))
            assertEquals(motionFile, complete.motionFile)

            val missing = assertNotNull(resolveLivePhotoPair(still, FakePairProvider(mapOf("still" to still), mapOf("still" to stillFile))))
            assertNull(missing.motionFile)
        } finally {
            directory.deleteRecursively()
        }
    }
}

private class FakePairProvider(
    private val metadata: Map<String, AttachmentMeta>,
    private val files: Map<String, File>,
) : AttachmentProvider {
    override fun byGuid(guid: String): AttachmentMeta? = metadata[guid]
    override fun localFile(guid: String): File? = files[guid]
}

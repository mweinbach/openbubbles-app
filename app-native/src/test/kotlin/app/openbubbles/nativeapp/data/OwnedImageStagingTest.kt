package app.openbubbles.nativeapp.data

import androidx.exifinterface.media.ExifInterface
import java.nio.file.Files
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.junit.Test

class OwnedImageStagingTest {
    @Test
    fun `all EXIF orientations map to the required mirror then rotation`() {
        val expected = mapOf(
            ExifInterface.ORIENTATION_NORMAL to ExifImageTransform(false, 0),
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL to ExifImageTransform(true, 0),
            ExifInterface.ORIENTATION_ROTATE_180 to ExifImageTransform(false, 180),
            ExifInterface.ORIENTATION_FLIP_VERTICAL to ExifImageTransform(true, 180),
            ExifInterface.ORIENTATION_TRANSPOSE to ExifImageTransform(true, 270),
            ExifInterface.ORIENTATION_ROTATE_90 to ExifImageTransform(false, 90),
            ExifInterface.ORIENTATION_TRANSVERSE to ExifImageTransform(true, 90),
            ExifInterface.ORIENTATION_ROTATE_270 to ExifImageTransform(false, 270),
        )

        expected.forEach { (orientation, transform) ->
            assertEquals(transform, exifImageTransform(orientation), "orientation $orientation")
        }
    }

    @Test
    fun `unknown EXIF orientation is treated as normal`() {
        assertEquals(ExifImageTransform(false, 0), exifImageTransform(0))
        assertEquals(ExifImageTransform(false, 0), exifImageTransform(99))
    }

    @Test
    fun `group icon cleanup accepts both owned roots and refuses foreign files`() {
        val root = Files.createTempDirectory("group-icon-roots").toFile()
        try {
            val directories = groupIconDirectories(root.resolve("files"), root)
            val current = directories[0].resolve("current.png").apply {
                requireNotNull(parentFile).mkdirs()
                writeBytes(byteArrayOf(1))
            }
            val legacy = directories[1].resolve("legacy.png").apply {
                requireNotNull(parentFile).mkdirs()
                writeBytes(byteArrayOf(2))
            }
            val foreign = root.resolve("external/foreign.png").apply {
                requireNotNull(parentFile).mkdirs()
                writeBytes(byteArrayOf(3))
            }

            assertTrue(deleteOwnedGroupIcon(current, directories))
            assertTrue(deleteOwnedGroupIcon(legacy, directories))
            assertFalse(deleteOwnedGroupIcon(foreign, directories))
            assertTrue(foreign.exists())
        } finally {
            root.deleteRecursively()
        }
    }
}

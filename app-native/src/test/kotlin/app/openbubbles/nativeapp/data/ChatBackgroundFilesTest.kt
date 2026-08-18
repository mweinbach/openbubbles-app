package app.openbubbles.nativeapp.data

import java.io.File
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ChatBackgroundFilesTest {
    @Test
    fun `flutter poster prefix resolves a decoded photo layer png`() {
        val root = Files.createTempDirectory("ob-flutter-poster").toFile()
        try {
            val prefix = File(root, "avatars/you/poster-42")
            prefix.mkdirs()
            val png = File(prefix, "layer.png").apply { writeBytes(PngHeader + ByteArray(32)) }
            File("$prefix.jpg").writeBytes(byteArrayOf(0x62, 0x70, 0x6C, 0x69, 0x73, 0x74))

            val resolved = resolveBackgroundImageFile(prefix.absolutePath) { null }
            assertEquals(png.absolutePath, resolved?.absolutePath)
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun `flutter poster save extracts the watch image into a cache file`() {
        val root = Files.createTempDirectory("ob-flutter-watch").toFile()
        try {
            val prefix = File(root, "avatars/you/poster-7")
            prefix.parentFile.mkdirs()
            File("$prefix.jpg").writeBytes(byteArrayOf(9, 9, 9))
            val jpeg = JpegHeader + ByteArray(16)

            val resolved = requireNotNull(resolveBackgroundImageFile(prefix.absolutePath) { jpeg })
            assertEquals(File("$prefix-watch.img").absolutePath, resolved.absolutePath)
            assertTrue(resolved.readBytes().contentEquals(jpeg))
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun `missing flutter prefix does not invent a background`() {
        val root = Files.createTempDirectory("ob-flutter-missing").toFile()
        try {
            assertNull(resolveBackgroundImageFile(File(root, "avatars/you/poster-missing").absolutePath))
        } finally {
            root.deleteRecursively()
        }
    }

    private companion object {
        val PngHeader = byteArrayOf(
            0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A,
        )
        val JpegHeader = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 0xE0.toByte())
    }
}

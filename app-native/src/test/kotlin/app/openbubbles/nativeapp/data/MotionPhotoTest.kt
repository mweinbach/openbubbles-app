package app.openbubbles.nativeapp.data

import java.io.ByteArrayOutputStream
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.junit.Test

class MotionPhotoTest {
    private val xmpHeader = "http://ns.adobe.com/xap/1.0/\u0000"

    private fun segment(marker: Int, payload: ByteArray): ByteArray {
        val length = payload.size + 2
        return ByteArrayOutputStream().apply {
            write(0xFF)
            write(marker)
            write((length ushr 8) and 0xFF)
            write(length and 0xFF)
            write(payload)
        }.toByteArray()
    }

    private fun xmpSegment(content: String): ByteArray =
        segment(0xE1, (xmpHeader + content).toByteArray(Charsets.ISO_8859_1))

    private fun fakeJpeg(vararg headerSegments: ByteArray): ByteArray =
        ByteArrayOutputStream().apply {
            write(0xFF)
            write(0xD8)
            headerSegments.forEach { write(it) }
            write(segment(0xDB, ByteArray(8))) // DQT
            write(byteArrayOf(0xFF.toByte(), 0xDA.toByte(), 0x00, 0x04, 0x01, 0x02)) // SOS
            write(byteArrayOf(0x11, 0x22, 0x33)) // entropy data
            write(byteArrayOf(0xFF.toByte(), 0xD9.toByte())) // EOI
        }.toByteArray()

    private val app0 = segment(0xE0, "JFIF\u0000fake".toByteArray(Charsets.ISO_8859_1))
    private val exif = segment(0xE1, "Exif\u0000\u0000fake-exif".toByteArray(Charsets.ISO_8859_1))
    private val video = ByteArray(257) { (it % 251).toByte() }

    private fun outputXmp(output: ByteArray): String {
        val text = String(output, Charsets.ISO_8859_1)
        val start = text.indexOf(xmpHeader)
        assertTrue(start >= 0, "output must contain an XMP packet")
        return text.substring(start, text.indexOf("<?xpacket end", start))
    }

    @Test
    fun `assembles a motion photo with the video appended verbatim`() {
        val still = fakeJpeg(app0, exif)
        val output = assertNotNull(buildJpegMotionPhoto(still, video, "video/quicktime"))

        assertEquals(0xFF, output[0].toInt() and 0xFF)
        assertEquals(0xD8, output[1].toInt() and 0xFF)
        assertContentEquals(video, output.copyOfRange(output.size - video.size, output.size))
        assertEquals(still.size + (output.size - still.size - video.size) + video.size, output.size)

        val xmp = outputXmp(output)
        assertTrue(xmp.contains("Camera:MotionPhoto=\"1\""))
        assertTrue(xmp.contains("Camera:MotionPhotoVersion=\"1\""))
        assertTrue(xmp.contains("Item:Mime=\"video/quicktime\""))
        assertTrue(xmp.contains("Item:Semantic=\"MotionPhoto\""))
        assertTrue(xmp.contains("Item:Length=\"${video.size}\""))
        assertTrue(xmp.contains("Item:Semantic=\"Primary\""))
    }

    @Test
    fun `carrier writer leaves motion bytes to the caller for streaming`() {
        val still = fakeJpeg(app0, exif)
        val output = ByteArrayOutputStream()

        assertTrue(writeJpegMotionPhotoCarrier(still, video.size.toLong(), "video/quicktime", output))
        val carrierSize = output.size()
        video.inputStream().use { it.copyTo(output) }

        val assembled = output.toByteArray()
        assertEquals(carrierSize + video.size, assembled.size)
        assertContentEquals(video, assembled.copyOfRange(carrierSize, assembled.size))
        assertTrue(outputXmp(assembled).contains("Item:Length=\"${video.size}\""))
    }

    @Test
    fun `readers can locate the video from the trailing length`() {
        val still = fakeJpeg(app0)
        val output = assertNotNull(buildJpegMotionPhoto(still, video, "video/mp4"))
        // Spec reading order: start from the end of the file and move back
        // Item:Length bytes to find the start of the video container.
        val lengths = Regex("Item:Length=\"(\\d+)\"").findAll(String(output, Charsets.ISO_8859_1))
            .map { it.groupValues[1].toInt() }
            .toList()
        assertEquals(listOf(0, video.size), lengths.sorted())
        assertContentEquals(video, output.copyOfRange(output.size - video.size, output.size))
    }

    @Test
    fun `xmp lands after the leading app segments`() {
        val still = fakeJpeg(app0, exif)
        val output = assertNotNull(buildJpegMotionPhoto(still, video, "video/quicktime"))
        val text = String(output, Charsets.ISO_8859_1)
        val xmpIndex = text.indexOf(xmpHeader)
        assertTrue(xmpIndex > text.indexOf("JFIF"), "XMP must come after APP0")
        assertTrue(xmpIndex > text.indexOf("fake-exif"), "XMP must come after the Exif APP1")
        // DQT (the first non-APP segment) must come after the inserted XMP.
        val dqtIndex = output.toList().windowed(2).indexOfFirst {
            (it[0].toInt() and 0xFF) == 0xFF && (it[1].toInt() and 0xFF) == 0xDB
        }
        assertTrue(dqtIndex > xmpIndex)
    }

    @Test
    fun `existing plain xmp is replaced, not duplicated`() {
        val still = fakeJpeg(app0, xmpSegment("<x:xmpmeta>plain camera metadata</x:xmpmeta>"))
        val output = assertNotNull(buildJpegMotionPhoto(still, video, "video/quicktime"))
        val text = String(output, Charsets.ISO_8859_1)
        assertEquals(1, Regex(Regex.escape(xmpHeader)).findAll(text).count())
        assertTrue(text.contains("Camera:MotionPhoto=\"1\""))
        assertTrue(!text.contains("plain camera metadata"))
    }

    @Test
    fun `stills with gain-map or container xmp are refused instead of clobbered`() {
        val ultraHdr = fakeJpeg(
            app0,
            xmpSegment("<rdf:Description hdrgm:Version=\"1.0\"><Container:Directory/></rdf:Description>"),
        )
        assertNull(buildJpegMotionPhoto(ultraHdr, video, "video/quicktime"))

        val extended = fakeJpeg(
            app0,
            segment(0xE1, "http://ns.adobe.com/xmp/extension/rest".toByteArray(Charsets.ISO_8859_1)),
        )
        assertNull(buildJpegMotionPhoto(extended, video, "video/quicktime"))
    }

    @Test
    fun `invalid inputs are refused`() {
        val still = fakeJpeg(app0)
        assertNull(buildJpegMotionPhoto(ByteArray(0), video, "video/quicktime"))
        assertNull(buildJpegMotionPhoto(byteArrayOf(0x00, 0x01, 0x02, 0x03), video, "video/quicktime"))
        assertNull(buildJpegMotionPhoto(still, ByteArray(0), "video/quicktime"))
        assertNull(buildJpegMotionPhoto(still, video, "video/webm"))
        // Truncated segment length must fail instead of over-reading.
        val corrupt = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 0xE0.toByte(), 0x7F, 0x00)
        assertNull(buildJpegMotionPhoto(corrupt, video, "video/quicktime"))
    }

    @Test
    fun `display names follow the MP filename pattern`() {
        val pattern = Regex("^([^\\s/\\\\][^/\\\\]*MP)\\.(JPG|jpg|JPEG|jpeg|HEIC|heic|AVIF|avif)$")
        assertEquals("IMG_0001.MP.jpg", motionPhotoDisplayName("IMG_0001.heic"))
        assertEquals("motion_photo.MP.jpg", motionPhotoDisplayName(null))
        assertEquals("motion_photo.MP.jpg", motionPhotoDisplayName(".heic"))
        assertEquals("live_clip.MP.jpg", motionPhotoDisplayName("live clip.jpeg"))
        listOf("IMG_0001.heic", null, "live clip.jpeg").forEach { source ->
            assertTrue(pattern.matches(motionPhotoDisplayName(source)))
        }
    }
}

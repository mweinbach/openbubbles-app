package app.openbubbles.nativeapp.ui.common

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ImageDecodeFallbackTest {

    @Test
    fun `successful original decode preserves HDR and skips SDR fallback`() {
        var fallbackAttempts = 0

        val decoded = decodeWithSdrFallback(
            decodeOriginal = { "original-hdr" },
            decodeSdr = {
                fallbackAttempts += 1
                "fallback-sdr"
            },
        )

        assertEquals("original-hdr", decoded)
        assertEquals(0, fallbackAttempts)
    }

    @Test
    fun `failed original decode retries once as SDR`() {
        var fallbackAttempts = 0

        val decoded = decodeWithSdrFallback(
            decodeOriginal = { null },
            decodeSdr = {
                fallbackAttempts += 1
                "fallback-sdr"
            },
        )

        assertEquals("fallback-sdr", decoded)
        assertEquals(1, fallbackAttempts)
    }

    @Test
    fun `failed SDR decode keeps the existing preview fallback available`() {
        val decoded = decodeWithSdrFallback<String>(
            decodeOriginal = { null },
            decodeSdr = { null },
        )

        assertNull(decoded)
    }

    @Test
    fun `software decoder keeps the complete screenshot when the screen budget fits`() {
        assertEquals(
            SoftwareDecodeSize(width = 1320, height = 2868),
            softwareDecodeSize(width = 1320, height = 2868, maxDimensionPx = 2955),
        )
    }

    @Test
    fun `oversized originals downsample without changing orientation or aspect ratio`() {
        assertEquals(
            SoftwareDecodeSize(width = 1088, height = 2364),
            softwareDecodeSize(width = 1320, height = 2868, maxDimensionPx = 2364),
        )
        assertEquals(
            SoftwareDecodeSize(width = 2364, height = 1088),
            softwareDecodeSize(width = 2868, height = 1320, maxDimensionPx = 2364),
        )
    }

    @Test
    fun `software decoder never enlarges a smaller original`() {
        assertEquals(
            SoftwareDecodeSize(width = 414, height = 896),
            softwareDecodeSize(width = 414, height = 896, maxDimensionPx = 4096),
        )
    }

    @Test
    fun `invalid image dimensions fail without allocating a bitmap`() {
        assertNull(softwareDecodeSize(width = 0, height = 2868, maxDimensionPx = 2048))
        assertNull(softwareDecodeSize(width = 1320, height = -1, maxDimensionPx = 2048))
        assertNull(softwareDecodeSize(width = 1320, height = 2868, maxDimensionPx = 0))
    }

    @Test
    fun `extended iPhone tone mapped HEIC skips unsupported platform codecs`() {
        val header = containerHeader("heix", "mif1", "tmap", "MiHE", "heix")

        assertTrue(isSoftwareDecodableHeif(header))
        assertTrue(prefersSoftwareHeicDecode(header))
    }

    @Test
    fun `ordinary HEIC and AVIF retain platform decoder priority`() {
        val heic = containerHeader("heic", "mif1", "heic")
        val avif = containerHeader("avif", "mif1", "avif")

        assertTrue(isSoftwareDecodableHeif(heic))
        assertTrue(isSoftwareDecodableHeif(avif))
        assertFalse(prefersSoftwareHeicDecode(heic))
        assertFalse(prefersSoftwareHeicDecode(avif))
    }

    @Test
    fun `truncated and non image containers never load the software decoder`() {
        assertFalse(isSoftwareDecodableHeif(byteArrayOf(0, 0, 0, 1)))
        assertFalse(isSoftwareDecodableHeif(containerHeader("qt  ", "mp41")))
        assertFalse(prefersSoftwareHeicDecode(containerHeader("heix", "mif1")))
    }

    private fun containerHeader(majorBrand: String, vararg compatibleBrands: String): ByteArray {
        val length = 16 + compatibleBrands.size * 4
        return ByteArray(length).apply {
            this[0] = (length ushr 24).toByte()
            this[1] = (length ushr 16).toByte()
            this[2] = (length ushr 8).toByte()
            this[3] = length.toByte()
            "ftyp".encodeToByteArray().copyInto(this, destinationOffset = 4)
            majorBrand.encodeToByteArray().copyInto(this, destinationOffset = 8)
            compatibleBrands.forEachIndexed { index, brand ->
                brand.encodeToByteArray().copyInto(this, destinationOffset = 16 + index * 4)
            }
        }
    }
}

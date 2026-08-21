package app.openbubbles.nativeapp.ui.common

import android.media.MediaCodecInfo
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class HeicDecodeSupportTest {

    @Test
    fun `reads 10-bit 4-4-4 HEVC metadata from nested HEIF properties`() {
        val container = heifContainer(chromaFormat = 3, bitDepth = 10)

        assertEquals(
            listOf(HevcImageConfiguration(chromaFormat = 3, bitDepth = 10)),
            inspectHevcImageConfigurations(container),
        )
    }

    @Test
    fun `reads every HEVC item including gain-map configurations`() {
        val first = configurationBox(chromaFormat = 1, bitDepth = 8)
        val gainMap = configurationBox(chromaFormat = 3, bitDepth = 10)
        val container = heifContainer(first, gainMap)

        assertEquals(
            listOf(
                HevcImageConfiguration(chromaFormat = 1, bitDepth = 8),
                HevcImageConfiguration(chromaFormat = 3, bitDepth = 10),
            ),
            inspectHevcImageConfigurations(container),
        )
    }

    @Test
    fun `normal 4-2-0 HDR remains decodable with Main10`() {
        val decoders = listOf(
            HevcDecoderCapabilities(
                profiles = setOf(MediaCodecInfo.CodecProfileLevel.HEVCProfileMain10),
                colorFormats = setOf(MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Flexible),
            ),
        )

        assertTrue(
            hevcImageConfigurationsSupported(
                configurations = listOf(HevcImageConfiguration(chromaFormat = 1, bitDepth = 10)),
                decoders = decoders,
                sdkInt = 37,
            ),
        )
    }

    @Test
    fun `Android 17 4-2-0 decoder rejects 10-bit 4-4-4 before codec creation`() {
        val decoders = listOf(
            HevcDecoderCapabilities(
                profiles = setOf(
                    MediaCodecInfo.CodecProfileLevel.HEVCProfileMain,
                    MediaCodecInfo.CodecProfileLevel.HEVCProfileMain10,
                ),
                colorFormats = setOf(MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Flexible),
            ),
        )

        assertFalse(
            hevcImageConfigurationsSupported(
                configurations = listOf(HevcImageConfiguration(chromaFormat = 3, bitDepth = 10)),
                decoders = decoders,
                sdkInt = 37,
            ),
        )
    }

    @Test
    fun `Android 17 Main444 capability permits supported 4-4-4 images`() {
        val decoders = listOf(
            HevcDecoderCapabilities(
                profiles = setOf(MediaCodecInfo.CodecProfileLevel.HEVCProfileMain444),
                colorFormats = emptySet(),
            ),
        )

        assertTrue(
            hevcImageConfigurationsSupported(
                configurations = listOf(HevcImageConfiguration(chromaFormat = 3, bitDepth = 8)),
                decoders = decoders,
                sdkInt = 37,
            ),
        )
        assertFalse(
            hevcImageConfigurationsSupported(
                configurations = listOf(HevcImageConfiguration(chromaFormat = 3, bitDepth = 8)),
                decoders = decoders,
                sdkInt = 36,
            ),
        )
    }

    @Test
    fun `malformed or unrelated containers never block decoding speculatively`() {
        assertTrue(inspectHevcImageConfigurations(byteArrayOf(1, 2, 3)).isEmpty())
        assertTrue(inspectHevcImageConfigurations(box("ftyp", "avif0000".toByteArray())).isEmpty())
        val truncated = heifContainer(chromaFormat = 3, bitDepth = 10).dropLast(10).toByteArray()
        assertTrue(inspectHevcImageConfigurations(truncated).isEmpty())
    }

    private fun heifContainer(chromaFormat: Int, bitDepth: Int): ByteArray =
        heifContainer(configurationBox(chromaFormat, bitDepth))

    private fun heifContainer(vararg configurations: ByteArray): ByteArray {
        val ftyp = box("ftyp", "heix".toByteArray() + byteArrayOf(0, 0, 0, 0) + "mif1".toByteArray())
        val properties = configurations.fold(ByteArray(0)) { combined, next -> combined + next }
        val meta = box("meta", byteArrayOf(0, 0, 0, 0) + box("iprp", box("ipco", properties)))
        return ftyp + meta
    }

    private fun configurationBox(chromaFormat: Int, bitDepth: Int): ByteArray {
        val payload = ByteArray(23)
        payload[0] = 1
        payload[16] = (0xfc or chromaFormat).toByte()
        payload[17] = (0xf8 or (bitDepth - 8)).toByte()
        return box("hvcC", payload)
    }

    private fun box(type: String, payload: ByteArray): ByteArray {
        val size = payload.size + 8
        return byteArrayOf(
            (size ushr 24).toByte(),
            (size ushr 16).toByte(),
            (size ushr 8).toByte(),
            size.toByte(),
        ) + type.toByteArray() + payload
    }
}

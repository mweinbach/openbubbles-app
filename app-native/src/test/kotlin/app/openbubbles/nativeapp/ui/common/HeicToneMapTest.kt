package app.openbubbles.nativeapp.ui.common

import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class HeicToneMapTest {

    @Test
    fun `single-channel tone map republishes hidden gain image without mutating original`() {
        val source = syntheticHeic(metadata = singleChannelMetadata())
        val unchanged = source.copyOf()

        val toneMap = assertNotNull(parseHeicToneMap(source))

        assertContentEquals(unchanged, source)
        assertEquals(19, primaryItemId(source))
        assertEquals(40, primaryItemId(toneMap.gainMapImage))
        assertEquals(1, itemFlags(source, 40) and 1)
        assertEquals(0, itemFlags(toneMap.gainMapImage, 40) and 1)

        assertFloatChannels(floatArrayOf(1f, 1f, 1f), toneMap.metadata.ratioMin)
        assertFloatChannels(floatArrayOf(2f, 2f, 2f), toneMap.metadata.ratioMax)
        assertFloatChannels(floatArrayOf(1f, 1f, 1f), toneMap.metadata.gamma)
        assertFloatChannels(floatArrayOf(0.01f, 0.01f, 0.01f), toneMap.metadata.epsilonSdr)
        assertFloatChannels(floatArrayOf(0.02f, 0.02f, 0.02f), toneMap.metadata.epsilonHdr)
        assertEquals(1f, toneMap.metadata.minDisplayRatioForHdrTransition)
        assertEquals(2f, toneMap.metadata.displayRatioForFullHdr)
        assertTrue(toneMap.metadata.useBaseColorSpace)
        assertFalse(toneMap.metadata.baseIsHdr)
    }

    @Test
    fun `three-channel tone map preserves independent ISO gain-map values`() {
        val toneMap = assertNotNull(parseHeicToneMap(syntheticHeic(metadata = threeChannelMetadata())))

        assertFloatChannels(floatArrayOf(0.5f, 1f, 2f), toneMap.metadata.ratioMin)
        assertFloatChannels(floatArrayOf(2f, 4f, 8f), toneMap.metadata.ratioMax)
        assertFloatChannels(floatArrayOf(1f, 0.5f, 1f / 3f), toneMap.metadata.gamma)
        assertFloatChannels(floatArrayOf(0.01f, 0.02f, 0.03f), toneMap.metadata.epsilonSdr)
        assertFloatChannels(floatArrayOf(0.02f, 0.04f, 0.06f), toneMap.metadata.epsilonHdr)
    }

    @Test
    fun `legacy metadata without item-version prefix is accepted`() {
        val metadata = singleChannelMetadata().copyOfRange(1, 62)

        val toneMap = assertNotNull(parseHeicToneMap(syntheticHeic(metadata = metadata)))

        assertEquals(2f, toneMap.metadata.displayRatioForFullHdr)
    }

    @Test
    fun `zero denominator is rejected without changing source`() {
        val metadata = singleChannelMetadata()
        metadata[10] = 0
        metadata[11] = 0
        metadata[12] = 0
        metadata[13] = 0
        val source = syntheticHeic(metadata = metadata)
        val unchanged = source.copyOf()

        assertNull(parseHeicToneMap(source))
        assertContentEquals(unchanged, source)
    }

    @Test
    fun `tone map referencing a different primary image is ignored`() {
        val source = syntheticHeic(metadata = singleChannelMetadata(), referencedBaseId = 18)

        assertNull(parseHeicToneMap(source))
    }

    @Test
    fun `truncated box and out-of-bounds metadata extents are rejected`() {
        val source = syntheticHeic(metadata = singleChannelMetadata())

        assertNull(parseHeicToneMap(source.copyOf(source.size - 1)))
        assertNull(parseHeicToneMap(syntheticHeic(singleChannelMetadata(), extentOffset = 4096)))
        assertNull(parseHeicToneMap(byteArrayOf(0, 0, 0, 1, 109, 101, 116, 97)))
    }

    @Test
    fun `HDR base swaps epsilon channels and normalizes display headroom`() {
        val metadata = metadataBytes(multichannel = false, baseHeadroom = 2, alternateHeadroom = 1)

        val toneMap = assertNotNull(parseHeicToneMap(syntheticHeic(metadata)))

        assertTrue(toneMap.metadata.baseIsHdr)
        assertEquals(2f, toneMap.metadata.minDisplayRatioForHdrTransition)
        assertEquals(4f, toneMap.metadata.displayRatioForFullHdr)
        assertFloatChannels(floatArrayOf(0.02f, 0.02f, 0.02f), toneMap.metadata.epsilonSdr)
        assertFloatChannels(floatArrayOf(0.01f, 0.01f, 0.01f), toneMap.metadata.epsilonHdr)
    }

    @Test
    fun `unsupported metadata flags and equal display headroom are rejected`() {
        val invalidFlags = singleChannelMetadata().also { it[5] = 0x41 }
        val equalHeadroom = metadataBytes(multichannel = false, baseHeadroom = 1, alternateHeadroom = 1)

        assertNull(parseHeicToneMap(syntheticHeic(invalidFlags)))
        assertNull(parseHeicToneMap(syntheticHeic(equalHeadroom)))
    }

    @Test
    fun `verified primary Display P3 ICC becomes equivalent nclx without moving boxes`() {
        val source = syntheticHeic(singleChannelMetadata(), iccProfile = displayP3IccProfile())
        val unchanged = source.copyOf()

        val normalized = normalizeHeicDisplayP3ForSoftware(source)

        assertContentEquals(unchanged, source)
        assertEquals(source.size, normalized.size)
        val colorOffset = normalized.indexOfType("colr") + 4
        assertEquals("nclx", normalized.decodeToString(colorOffset, colorOffset + 4))
        assertEquals(12, unsignedShortAt(normalized, colorOffset + 4))
        assertEquals(13, unsignedShortAt(normalized, colorOffset + 6))
        assertEquals(6, unsignedShortAt(normalized, colorOffset + 8))
        assertEquals(0x80, normalized[colorOffset + 10].toInt() and 0xff)
    }

    @Test
    fun `unknown or unassociated ICC profiles are returned untouched by reference`() {
        val sRgb = syntheticHeic(singleChannelMetadata(), iccProfile = displayP3IccProfile("sRGB"))
        val adaptivePq = syntheticHeic(
            singleChannelMetadata(),
            iccProfile = displayP3IccProfile("Display P3 Primaries; PQ (Adaptive Soft Clip Curve)"),
        )
        val unassociated = syntheticHeic(
            singleChannelMetadata(),
            iccProfile = displayP3IccProfile(),
            associatePrimaryColor = false,
        )
        val noProfile = syntheticHeic(singleChannelMetadata())

        assertSame(sRgb, normalizeHeicDisplayP3ForSoftware(sRgb))
        assertSame(adaptivePq, normalizeHeicDisplayP3ForSoftware(adaptivePq))
        assertSame(unassociated, normalizeHeicDisplayP3ForSoftware(unassociated))
        assertSame(noProfile, normalizeHeicDisplayP3ForSoftware(noProfile))
    }

    @Test
    fun `Apple multilingual Display P3 ICC description is recognized exactly`() {
        val source = syntheticHeic(
            singleChannelMetadata(),
            iccProfile = displayP3IccProfile(multilingual = true),
        )
        val adaptivePq = syntheticHeic(
            singleChannelMetadata(),
            iccProfile = displayP3IccProfile(
                description = "Display P3 Primaries; PQ (Adaptive Soft Clip Curve)",
                multilingual = true,
            ),
        )

        val normalized = normalizeHeicDisplayP3ForSoftware(source)
        val colorOffset = normalized.indexOfType("colr") + 4

        assertEquals("nclx", normalized.decodeToString(colorOffset, colorOffset + 4))
        assertSame(adaptivePq, normalizeHeicDisplayP3ForSoftware(adaptivePq))
    }

    @Test
    fun `gain image clone also preserves verified Display P3 color information`() {
        val source = syntheticHeic(singleChannelMetadata(), iccProfile = displayP3IccProfile())

        val toneMap = assertNotNull(parseHeicToneMap(source))

        val originalColorOffset = source.indexOfType("colr") + 4
        val gainColorOffset = toneMap.gainMapImage.indexOfType("colr") + 4
        assertEquals("prof", source.decodeToString(originalColorOffset, originalColorOffset + 4))
        assertEquals("nclx", toneMap.gainMapImage.decodeToString(gainColorOffset, gainColorOffset + 4))
    }

    private fun syntheticHeic(
        metadata: ByteArray,
        referencedBaseId: Int = 19,
        extentOffset: Int = 16,
        iccProfile: ByteArray? = null,
        associatePrimaryColor: Boolean = true,
    ): ByteArray {
        val pitm = fullBox("pitm", version = 0) { writeShort(19) }
        val iinf = fullBox("iinf", version = 0) {
            writeShort(3)
            write(itemInfo(id = 19, type = "grid", hidden = false))
            write(itemInfo(id = 40, type = "grid", hidden = true))
            write(itemInfo(id = 41, type = "tmap", hidden = false))
        }
        val iref = fullBox("iref", version = 0) {
            write(box("dimg") {
                writeShort(41)
                writeShort(2)
                writeShort(referencedBaseId)
                writeShort(40)
            })
        }
        val iloc = fullBox("iloc", version = 1) {
            writeByte(0x44)
            writeByte(0)
            writeShort(3)
            writeLocation(id = 19, offset = 0, length = 8)
            writeLocation(id = 40, offset = 8, length = 8)
            writeLocation(id = 41, offset = extentOffset, length = metadata.size)
        }
        val idat = box("idat") {
            write(ByteArray(16))
            write(metadata)
        }
        val ftyp = box("ftyp") {
            writeBytes("heix")
            writeInt(0)
            writeBytes("mif1")
            writeBytes("tmap")
        }
        val itemProperties = iccProfile?.let { profile ->
            box("iprp") {
                write(box("ipco") {
                    write(box("colr") {
                        writeBytes("prof")
                        write(profile)
                    })
                    write(box("ispe") { writeInt(0) })
                })
                write(fullBox("ipma", version = 0) {
                    writeInt(1)
                    writeShort(19)
                    writeByte(1)
                    writeByte(if (associatePrimaryColor) 0x81 else 0x82)
                })
            }
        }
        val meta = fullBox("meta", version = 0) {
            write(pitm)
            write(iinf)
            write(iref)
            if (itemProperties != null) write(itemProperties)
            write(iloc)
            write(idat)
        }

        return ftyp + meta
    }

    private fun DataOutputStream.writeLocation(id: Int, offset: Int, length: Int) {
        writeShort(id)
        writeShort(1)
        writeShort(0)
        writeShort(1)
        writeInt(offset)
        writeInt(length)
    }

    private fun itemInfo(id: Int, type: String, hidden: Boolean): ByteArray =
        fullBox("infe", version = 2, flags = if (hidden) 1 else 0) {
            writeShort(id)
            writeShort(0)
            writeBytes(type)
            writeByte(0)
        }

    private fun singleChannelMetadata(): ByteArray =
        metadataBytes(multichannel = false, baseHeadroom = 0, alternateHeadroom = 1)

    private fun threeChannelMetadata(): ByteArray =
        metadataBytes(multichannel = true, baseHeadroom = 0, alternateHeadroom = 3)

    private fun metadataBytes(
        multichannel: Boolean,
        baseHeadroom: Int,
        alternateHeadroom: Int,
    ): ByteArray = data {
        writeByte(0)
        writeShort(0)
        writeShort(0)
        writeByte(0x40 or if (multichannel) 0x80 else 0)
        writeInt(baseHeadroom)
        writeInt(1)
        writeInt(alternateHeadroom)
        writeInt(1)

        val channels = if (multichannel) 3 else 1
        repeat(channels) { channel ->
            writeInt(if (multichannel) channel - 1 else 0)
            writeInt(1)
            writeInt(channel + 1)
            writeInt(1)
            writeInt(channel + 1)
            writeInt(1)
            writeInt(channel + 1)
            writeInt(100)
            writeInt((channel + 1) * 2)
            writeInt(100)
        }
    }

    private fun fullBox(
        type: String,
        version: Int,
        flags: Int = 0,
        contents: DataOutputStream.() -> Unit,
    ): ByteArray = box(type) {
        writeByte(version)
        writeByte(flags ushr 16)
        writeByte(flags ushr 8)
        writeByte(flags)
        contents()
    }

    private fun box(type: String, contents: DataOutputStream.() -> Unit): ByteArray {
        val payload = data(contents)
        return data {
            writeInt(payload.size + 8)
            writeBytes(type)
            write(payload)
        }
    }

    private fun data(contents: DataOutputStream.() -> Unit): ByteArray {
        val bytes = ByteArrayOutputStream()
        DataOutputStream(bytes).use { it.contents() }
        return bytes.toByteArray()
    }

    private fun displayP3IccProfile(
        description: String = "Display P3",
        multilingual: Boolean = false,
    ): ByteArray {
        val descriptionTag = if (multilingual) {
            val text = description.toByteArray(Charsets.UTF_16BE)
            data {
                writeBytes("mluc")
                writeInt(0)
                writeInt(1)
                writeInt(12)
                writeBytes("enUS")
                writeInt(text.size)
                writeInt(28)
                write(text)
            }
        } else {
            data {
                writeBytes("desc")
                writeInt(0)
                writeInt(description.length + 1)
                writeBytes(description)
                writeByte(0)
            }
        }
        val tags = listOf(
            "desc" to descriptionTag,
            "rXYZ" to xyzTag(0.515121, 0.241196, -0.001053),
            "gXYZ" to xyzTag(0.291977, 0.692245, 0.041885),
            "bXYZ" to xyzTag(0.157104, 0.066574, 0.784073),
        )
        val tableSize = 4 + tags.size * 12
        val profileSize = 128 + tableSize + tags.sumOf { it.second.size }
        val header = ByteArray(128)
        writeIntAt(header, 0, profileSize)
        "RGB ".encodeToByteArray().copyInto(header, destinationOffset = 16)
        "XYZ ".encodeToByteArray().copyInto(header, destinationOffset = 20)
        "acsp".encodeToByteArray().copyInto(header, destinationOffset = 36)

        return data {
            write(header)
            writeInt(tags.size)
            var offset = 128 + tableSize
            for ((signature, contents) in tags) {
                writeBytes(signature)
                writeInt(offset)
                writeInt(contents.size)
                offset += contents.size
            }
            for ((_, contents) in tags) write(contents)
        }
    }

    private fun xyzTag(x: Double, y: Double, z: Double): ByteArray = data {
        writeBytes("XYZ ")
        writeInt(0)
        writeInt((x * 65536).toInt())
        writeInt((y * 65536).toInt())
        writeInt((z * 65536).toInt())
    }

    private fun writeIntAt(bytes: ByteArray, offset: Int, value: Int) {
        bytes[offset] = (value ushr 24).toByte()
        bytes[offset + 1] = (value ushr 16).toByte()
        bytes[offset + 2] = (value ushr 8).toByte()
        bytes[offset + 3] = value.toByte()
    }

    private fun unsignedShortAt(bytes: ByteArray, offset: Int): Int =
        ((bytes[offset].toInt() and 0xff) shl 8) or (bytes[offset + 1].toInt() and 0xff)

    private fun primaryItemId(source: ByteArray): Int {
        val index = source.indexOfType("pitm")
        return ((source[index + 8].toInt() and 0xff) shl 8) or
            (source[index + 9].toInt() and 0xff)
    }

    private fun itemFlags(source: ByteArray, itemId: Int): Int {
        var from = 0
        while (true) {
            val index = source.indexOfType("infe", from)
            val candidate = ((source[index + 8].toInt() and 0xff) shl 8) or
                (source[index + 9].toInt() and 0xff)
            if (candidate == itemId) return source[index + 7].toInt() and 0xff
            from = index + 4
        }
    }

    private fun ByteArray.indexOfType(type: String, from: Int = 0): Int {
        val expected = type.encodeToByteArray()
        for (index in from..size - expected.size) {
            if (expected.indices.all { this[index + it] == expected[it] }) return index
        }
        error("Missing synthetic box $type")
    }

    private fun assertFloatChannels(expected: FloatArray, actual: FloatArray) {
        assertEquals(expected.size, actual.size)
        expected.indices.forEach { index -> assertEquals(expected[index], actual[index], 0.00001f) }
    }
}

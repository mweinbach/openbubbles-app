package app.openbubbles.nativeapp.ui.common

import kotlin.math.abs
import kotlin.math.pow

/** The hidden gain-map image, republished as the primary image without changing [source]. */
internal data class HeicToneMap(
    val gainMapImage: ByteArray,
    val metadata: HeicGainmapMetadata,
)

/** ISO 21496-1 gain-map metadata converted into Android Gainmap's linear representation. */
internal data class HeicGainmapMetadata(
    val ratioMin: FloatArray,
    val ratioMax: FloatArray,
    val gamma: FloatArray,
    val epsilonSdr: FloatArray,
    val epsilonHdr: FloatArray,
    val minDisplayRatioForHdrTransition: Float,
    val displayRatioForFullHdr: Float,
    val useBaseColorSpace: Boolean,
    val baseIsHdr: Boolean,
)

private const val MAX_HEIC_BYTES = 128 * 1024 * 1024
private const val MAX_BOX_COUNT = 8_192
private const val MAX_ITEM_COUNT = 4_096
private const val MAX_EXTENT_COUNT = 16
private const val MAX_METADATA_BYTES = 4_096
private const val MAX_ICC_TAG_COUNT = 128
private const val MAX_LOG_RATIO = 20.0
private const val MAX_NUMERIC_VALUE = 1_000_000.0
private const val DISPLAY_P3_MATRIX_TOLERANCE = 0.02

/**
 * Finds an ISO gain-map (`tmap`) associated with the primary HEIC image.
 *
 * A gain image is normally hidden, and generic HEIC decoders only expose the primary image. The
 * returned container is an otherwise unchanged copy with the gain image made primary and visible;
 * decoding it through the same software decoder therefore produces the original gain-map pixels.
 */
internal fun parseHeicToneMap(source: ByteArray): HeicToneMap? {
    if (source.size !in 8..MAX_HEIC_BYTES) return null

    val topLevel = parseBoxes(source, 0, source.size) ?: return null
    val meta = topLevel.firstOrNull { it.type == "meta" } ?: return null
    val metaReader = BoxReader(source, meta.payloadStart, meta.end)
    if (metaReader.readUnsigned(4) == null) return null
    val children = parseBoxes(source, metaReader.position, meta.end) ?: return null

    val primary = parsePrimaryItem(source, children.firstOrNull { it.type == "pitm" } ?: return null)
        ?: return null
    val items = parseItemInfo(source, children.firstOrNull { it.type == "iinf" } ?: return null)
        ?: return null
    val references = parseImageReferences(
        source,
        children.firstOrNull { it.type == "iref" } ?: return null,
    ) ?: return null

    val toneMap = references.firstNotNullOfOrNull { reference ->
        if (reference.targetIds.size < 2 || reference.targetIds[0] != primary.itemId) {
            null
        } else {
            val item = items[reference.itemId]
            val gainImage = items[reference.targetIds[1]]
            if (item?.type == "tmap" && gainImage != null && gainImage.itemId != primary.itemId) {
                ToneMapItems(item, gainImage)
            } else {
                null
            }
        }
    } ?: return null

    val locations = parseItemLocations(
        source,
        children.firstOrNull { it.type == "iloc" } ?: return null,
    ) ?: return null
    val location = locations[toneMap.metadataItem.itemId] ?: return null
    val metadataBytes = readItemContents(
        source,
        location,
        children.firstOrNull { it.type == "idat" },
    ) ?: return null
    val metadata = parseGainmapMetadata(metadataBytes) ?: return null

    if (primary.itemIdWidth == 2 && toneMap.gainImage.itemId > 0xffffL) return null
    val normalizedSource = normalizeHeicDisplayP3ForSoftware(source)
    val gainMapImage = if (normalizedSource === source) source.copyOf() else normalizedSource
    writeUnsigned(gainMapImage, primary.itemIdOffset, primary.itemIdWidth, toneMap.gainImage.itemId)
    val hiddenFlags = gainMapImage[toneMap.gainImage.flagsOffset].toInt()
    gainMapImage[toneMap.gainImage.flagsOffset] = (hiddenFlags and 0xfe).toByte()

    return HeicToneMap(gainMapImage = gainMapImage, metadata = metadata)
}

/**
 * Replaces a verified Display P3 ICC item property with its equivalent ISO `nclx` declaration.
 *
 * Some software HEIC decoders transform embedded ICC profiles to sRGB before exposing a Bitmap.
 * Their `nclx` path retains 10-bit Display P3, so patch only a color property actually associated
 * with the primary image. Box sizes and all offsets remain unchanged. Unrecognized input is
 * returned by reference, and a recognized source is never modified.
 */
internal fun normalizeHeicDisplayP3ForSoftware(source: ByteArray): ByteArray {
    if (source.size !in 8..MAX_HEIC_BYTES) return source
    val topLevel = parseBoxes(source, 0, source.size) ?: return source
    val meta = topLevel.firstOrNull { it.type == "meta" } ?: return source
    if (meta.end - meta.payloadStart < 4) return source
    val children = parseBoxes(source, meta.payloadStart + 4, meta.end) ?: return source
    val primary = parsePrimaryItem(source, children.firstOrNull { it.type == "pitm" } ?: return source)
        ?: return source

    val propertiesBox = children.firstOrNull { it.type == "iprp" } ?: return source
    val propertiesChildren = parseBoxes(source, propertiesBox.payloadStart, propertiesBox.end)
        ?: return source
    val container = propertiesChildren.firstOrNull { it.type == "ipco" } ?: return source
    val properties = parseBoxes(source, container.payloadStart, container.end) ?: return source
    val associationBoxes = propertiesChildren.filter { it.type == "ipma" }
    if (associationBoxes.isEmpty()) return source

    val associatedProperties = mutableSetOf<Int>()
    for (associationBox in associationBoxes) {
        val associations = parsePrimaryColorAssociations(source, associationBox, primary.itemId)
            ?: return source
        associatedProperties += associations
    }

    val colorProperty = associatedProperties.firstNotNullOfOrNull { index ->
        val property = properties.getOrNull(index - 1) ?: return@firstNotNullOfOrNull null
        if (property.type != "colr" || property.end - property.payloadStart < 11) {
            return@firstNotNullOfOrNull null
        }
        val reader = BoxReader(source, property.payloadStart, property.end)
        if (reader.readType() != "prof" || !isDisplayP3IccProfile(source, reader.position, property.end)) {
            null
        } else {
            property
        }
    } ?: return source

    val normalized = source.copyOf()
    val offset = colorProperty.payloadStart
    normalized[offset] = 'n'.code.toByte()
    normalized[offset + 1] = 'c'.code.toByte()
    normalized[offset + 2] = 'l'.code.toByte()
    normalized[offset + 3] = 'x'.code.toByte()
    writeUnsigned(normalized, offset + 4, 2, 12L) // SMPTE 432 / Display P3 primaries.
    writeUnsigned(normalized, offset + 6, 2, 13L) // IEC 61966-2-1 / sRGB transfer.
    writeUnsigned(normalized, offset + 8, 2, 6L) // SMPTE 170M / base HEVC YCbCr matrix.
    normalized[offset + 10] = 0x80.toByte() // Full-range YCbCr.
    return normalized
}

private data class BmffBox(
    val type: String,
    val payloadStart: Int,
    val end: Int,
)

private data class PrimaryItem(
    val itemId: Long,
    val itemIdOffset: Int,
    val itemIdWidth: Int,
)

private data class ItemInfo(
    val itemId: Long,
    val type: String,
    val flagsOffset: Int,
)

private data class ImageReference(
    val itemId: Long,
    val targetIds: List<Long>,
)

private data class ToneMapItems(
    val metadataItem: ItemInfo,
    val gainImage: ItemInfo,
)

private data class ItemExtent(
    val offset: Long,
    val length: Long,
)

private data class ItemLocation(
    val constructionMethod: Int,
    val baseOffset: Long,
    val extents: List<ItemExtent>,
)

private data class IccTag(
    val offset: Int,
    val end: Int,
)

private class BoxReader(
    private val bytes: ByteArray,
    var position: Int,
    private val limit: Int,
) {
    fun readUnsigned(width: Int): Long? {
        if (width !in 0..8 || width > limit - position) return null
        var result = 0L
        repeat(width) {
            val next = bytes[position++].toInt() and 0xff
            if (result > (Long.MAX_VALUE - next) / 256L) return null
            result = result * 256L + next
        }
        return result
    }

    fun readSignedInt(): Int? {
        val value = readUnsigned(4) ?: return null
        return value.toInt()
    }

    fun readType(): String? {
        if (limit - position < 4) return null
        val value = bytes.decodeToString(position, position + 4)
        position += 4
        return value
    }
}

private fun parseBoxes(bytes: ByteArray, start: Int, end: Int): List<BmffBox>? {
    if (start < 0 || end > bytes.size || start > end) return null
    val boxes = mutableListOf<BmffBox>()
    var position = start

    while (position < end) {
        if (boxes.size == MAX_BOX_COUNT || end - position < 8) return null
        val reader = BoxReader(bytes, position, end)
        val initialSize = reader.readUnsigned(4) ?: return null
        val type = reader.readType() ?: return null
        val size = when (initialSize) {
            0L -> (end - position).toLong()
            1L -> reader.readUnsigned(8) ?: return null
            else -> initialSize
        }
        val headerSize = reader.position - position
        if (size < headerSize || size > (end - position).toLong()) return null
        val boxEnd = position + size.toInt()
        boxes += BmffBox(type = type, payloadStart = reader.position, end = boxEnd)
        position = boxEnd
    }

    return boxes
}

private fun parsePrimaryItem(bytes: ByteArray, box: BmffBox): PrimaryItem? {
    val reader = BoxReader(bytes, box.payloadStart, box.end)
    val version = reader.readUnsigned(1)?.toInt() ?: return null
    if (reader.readUnsigned(3) == null) return null
    val width = when (version) {
        0 -> 2
        1 -> 4
        else -> return null
    }
    val itemIdOffset = reader.position
    val itemId = reader.readUnsigned(width) ?: return null
    return PrimaryItem(itemId = itemId, itemIdOffset = itemIdOffset, itemIdWidth = width)
}

private fun parseItemInfo(bytes: ByteArray, box: BmffBox): Map<Long, ItemInfo>? {
    val reader = BoxReader(bytes, box.payloadStart, box.end)
    val version = reader.readUnsigned(1)?.toInt() ?: return null
    if (reader.readUnsigned(3) == null || version !in 0..3) return null
    val itemCount = reader.readUnsigned(if (version == 0) 2 else 4) ?: return null
    if (itemCount > MAX_ITEM_COUNT) return null
    val itemBoxes = parseBoxes(bytes, reader.position, box.end) ?: return null
    if (itemBoxes.size.toLong() != itemCount) return null

    val items = mutableMapOf<Long, ItemInfo>()
    for (itemBox in itemBoxes) {
        if (itemBox.type != "infe") return null
        val itemReader = BoxReader(bytes, itemBox.payloadStart, itemBox.end)
        val itemVersion = itemReader.readUnsigned(1)?.toInt() ?: return null
        val flagsOffset = itemReader.position + 2
        if (itemReader.readUnsigned(3) == null) return null
        val width = when (itemVersion) {
            2 -> 2
            3 -> 4
            else -> return null
        }
        val itemId = itemReader.readUnsigned(width) ?: return null
        if (itemReader.readUnsigned(2) == null) return null
        val type = itemReader.readType() ?: return null
        if (items.put(itemId, ItemInfo(itemId, type, flagsOffset)) != null) return null
    }

    return items
}

private fun parseImageReferences(bytes: ByteArray, box: BmffBox): List<ImageReference>? {
    val reader = BoxReader(bytes, box.payloadStart, box.end)
    val version = reader.readUnsigned(1)?.toInt() ?: return null
    if (reader.readUnsigned(3) == null || version !in 0..1) return null
    val idWidth = if (version == 0) 2 else 4
    val boxes = parseBoxes(bytes, reader.position, box.end) ?: return null
    val references = mutableListOf<ImageReference>()

    for (referenceBox in boxes) {
        if (referenceBox.type != "dimg") continue
        val referenceReader = BoxReader(bytes, referenceBox.payloadStart, referenceBox.end)
        val itemId = referenceReader.readUnsigned(idWidth) ?: return null
        val count = referenceReader.readUnsigned(2)?.toInt() ?: return null
        if (count > MAX_ITEM_COUNT) return null
        val targetIds = ArrayList<Long>(count)
        repeat(count) {
            targetIds += referenceReader.readUnsigned(idWidth) ?: return null
        }
        references += ImageReference(itemId = itemId, targetIds = targetIds)
    }

    return references
}

private fun parsePrimaryColorAssociations(
    bytes: ByteArray,
    box: BmffBox,
    primaryItemId: Long,
): Set<Int>? {
    val reader = BoxReader(bytes, box.payloadStart, box.end)
    val version = reader.readUnsigned(1)?.toInt() ?: return null
    val flags = reader.readUnsigned(3) ?: return null
    if (version !in 0..1) return null
    val count = reader.readUnsigned(4) ?: return null
    if (count > MAX_ITEM_COUNT) return null

    val associations = mutableSetOf<Int>()
    val itemIdWidth = if (version == 0) 2 else 4
    val associationWidth = if (flags and 1L != 0L) 2 else 1
    val propertyIndexMask = if (associationWidth == 2) 0x7fff else 0x7f

    repeat(count.toInt()) {
        val itemId = reader.readUnsigned(itemIdWidth) ?: return null
        val associationCount = reader.readUnsigned(1)?.toInt() ?: return null
        repeat(associationCount) {
            val association = reader.readUnsigned(associationWidth)?.toInt() ?: return null
            val index = association and propertyIndexMask
            if (itemId == primaryItemId && index != 0) associations += index
        }
    }

    return associations
}

private fun isDisplayP3IccProfile(bytes: ByteArray, start: Int, end: Int): Boolean {
    if (end - start < 132) return false
    val headerReader = BoxReader(bytes, start, end)
    val profileSize = headerReader.readUnsigned(4) ?: return false
    if (profileSize < 132 || profileSize > (end - start).toLong()) return false
    val profileEnd = start + profileSize.toInt()

    if (readTypeAt(bytes, start + 16, profileEnd) != "RGB " ||
        readTypeAt(bytes, start + 20, profileEnd) != "XYZ " ||
        readTypeAt(bytes, start + 36, profileEnd) != "acsp"
    ) {
        return false
    }

    val tagReader = BoxReader(bytes, start + 128, profileEnd)
    val tagCount = tagReader.readUnsigned(4) ?: return false
    if (tagCount > MAX_ICC_TAG_COUNT || tagCount > (profileEnd - tagReader.position) / 12) {
        return false
    }
    val tags = mutableMapOf<String, IccTag>()
    repeat(tagCount.toInt()) {
        val signature = tagReader.readType() ?: return false
        val offset = tagReader.readUnsigned(4) ?: return false
        val size = tagReader.readUnsigned(4) ?: return false
        if (offset > profileSize || size > profileSize - offset) return false
        tags[signature] = IccTag(start + offset.toInt(), start + (offset + size).toInt())
    }

    val description = tags["desc"] ?: return false
    if (!hasDisplayP3Description(bytes, description)) return false

    return hasP3Matrix(bytes, tags["rXYZ"] ?: return false, 0.515121, 0.241196, -0.001053) &&
        hasP3Matrix(bytes, tags["gXYZ"] ?: return false, 0.291977, 0.692245, 0.041885) &&
        hasP3Matrix(bytes, tags["bXYZ"] ?: return false, 0.157104, 0.066574, 0.784073)
}

private fun hasDisplayP3Description(bytes: ByteArray, tag: IccTag): Boolean {
    val reader = BoxReader(bytes, tag.offset, tag.end)
    val type = reader.readType() ?: return false
    if (reader.readUnsigned(4) == null) return false

    return when (type) {
        "desc" -> {
            val length = reader.readUnsigned(4) ?: return false
            if (length == 0L || length > (tag.end - reader.position).toLong()) return false
            val textLength = length.toInt() - if (bytes[reader.position + length.toInt() - 1] == 0.toByte()) 1 else 0
            bytes.decodeToString(reader.position, reader.position + textLength)
                .equals("Display P3", ignoreCase = true)
        }
        "mluc" -> {
            val count = reader.readUnsigned(4) ?: return false
            val recordSize = reader.readUnsigned(4) ?: return false
            if (count == 0L || count > MAX_ICC_TAG_COUNT || recordSize !in 12..64) return false
            if (count > (tag.end - reader.position) / recordSize) return false

            repeat(count.toInt()) {
                val recordStart = reader.position
                if (reader.readUnsigned(4) == null) return false // language and country.
                val textLength = reader.readUnsigned(4) ?: return false
                val textOffset = reader.readUnsigned(4) ?: return false
                val tagLength = tag.end - tag.offset
                if (textLength == 0L || textLength > 256 || textLength and 1L != 0L ||
                    textOffset > tagLength || textLength > tagLength - textOffset
                ) {
                    return false
                }
                val textStart = tag.offset + textOffset.toInt()
                val description = String(bytes, textStart, textLength.toInt(), Charsets.UTF_16BE)
                if (description.equals("Display P3", ignoreCase = true)) return true
                reader.position = recordStart + recordSize.toInt()
            }
            false
        }
        else -> false
    }
}

private fun hasP3Matrix(bytes: ByteArray, tag: IccTag, x: Double, y: Double, z: Double): Boolean {
    val reader = BoxReader(bytes, tag.offset, tag.end)
    if (reader.readType() != "XYZ " || reader.readUnsigned(4) == null) return false
    val actualX = (reader.readSignedInt() ?: return false) / 65536.0
    val actualY = (reader.readSignedInt() ?: return false) / 65536.0
    val actualZ = (reader.readSignedInt() ?: return false) / 65536.0
    return abs(actualX - x) <= DISPLAY_P3_MATRIX_TOLERANCE &&
        abs(actualY - y) <= DISPLAY_P3_MATRIX_TOLERANCE &&
        abs(actualZ - z) <= DISPLAY_P3_MATRIX_TOLERANCE
}

private fun readTypeAt(bytes: ByteArray, position: Int, limit: Int): String? =
    BoxReader(bytes, position, limit).readType()

private fun parseItemLocations(bytes: ByteArray, box: BmffBox): Map<Long, ItemLocation>? {
    val reader = BoxReader(bytes, box.payloadStart, box.end)
    val version = reader.readUnsigned(1)?.toInt() ?: return null
    if (reader.readUnsigned(3) == null || version !in 0..2) return null

    val sizes = reader.readUnsigned(1)?.toInt() ?: return null
    val baseSizes = reader.readUnsigned(1)?.toInt() ?: return null
    val offsetSize = sizes ushr 4
    val lengthSize = sizes and 0x0f
    val baseOffsetSize = baseSizes ushr 4
    val indexSize = if (version == 0) 0 else baseSizes and 0x0f
    if (offsetSize > 8 || lengthSize > 8 || baseOffsetSize > 8 || indexSize > 8) return null

    val count = reader.readUnsigned(if (version < 2) 2 else 4) ?: return null
    if (count > MAX_ITEM_COUNT) return null
    val idWidth = if (version < 2) 2 else 4
    val locations = mutableMapOf<Long, ItemLocation>()

    repeat(count.toInt()) {
        val itemId = reader.readUnsigned(idWidth) ?: return null
        val constructionMethod = if (version == 0) {
            0
        } else {
            ((reader.readUnsigned(2) ?: return null) and 0x0f).toInt()
        }
        val dataReference = reader.readUnsigned(2) ?: return null
        val baseOffset = reader.readUnsigned(baseOffsetSize) ?: return null
        val extentCount = reader.readUnsigned(2)?.toInt() ?: return null
        if (extentCount > MAX_EXTENT_COUNT || dataReference != 0L) return null

        val extents = ArrayList<ItemExtent>(extentCount)
        repeat(extentCount) {
            if (version != 0 && indexSize > 0 && reader.readUnsigned(indexSize) == null) return null
            val offset = reader.readUnsigned(offsetSize) ?: return null
            val length = reader.readUnsigned(lengthSize) ?: return null
            extents += ItemExtent(offset, length)
        }
        if (locations.put(itemId, ItemLocation(constructionMethod, baseOffset, extents)) != null) {
            return null
        }
    }

    return locations
}

private fun readItemContents(
    source: ByteArray,
    location: ItemLocation,
    idat: BmffBox?,
): ByteArray? {
    if (location.extents.isEmpty()) return null
    val contentStart: Long
    val contentEnd: Long
    when (location.constructionMethod) {
        0 -> {
            contentStart = 0L
            contentEnd = source.size.toLong()
        }
        1 -> {
            val dataBox = idat ?: return null
            contentStart = dataBox.payloadStart.toLong()
            contentEnd = dataBox.end.toLong()
        }
        else -> return null
    }

    var metadataLength = 0L
    for (extent in location.extents) {
        if (extent.length <= 0 || extent.length > MAX_METADATA_BYTES) return null
        metadataLength += extent.length
        if (metadataLength > MAX_METADATA_BYTES) return null
    }

    val metadata = ByteArray(metadataLength.toInt())
    var outputOffset = 0
    for (extent in location.extents) {
        val offset = location.baseOffset + extent.offset
        if (offset < location.baseOffset || offset > contentEnd - contentStart) return null
        val start = contentStart + offset
        if (extent.length > contentEnd - start) return null
        source.copyInto(metadata, outputOffset, start.toInt(), (start + extent.length).toInt())
        outputOffset += extent.length.toInt()
    }

    return metadata
}

private fun parseGainmapMetadata(bytes: ByteArray): HeicGainmapMetadata? =
    parseGainmapMetadata(bytes, hasItemVersion = true)
        ?: parseGainmapMetadata(bytes, hasItemVersion = false)

private fun parseGainmapMetadata(bytes: ByteArray, hasItemVersion: Boolean): HeicGainmapMetadata? {
    val reader = BoxReader(bytes, 0, bytes.size)
    if (hasItemVersion && reader.readUnsigned(1) != 0L) return null
    val minimumVersion = reader.readUnsigned(2) ?: return null
    val writerVersion = reader.readUnsigned(2) ?: return null
    if (minimumVersion != 0L || writerVersion < minimumVersion) return null

    val flags = reader.readUnsigned(1)?.toInt() ?: return null
    if (flags and 0x3f != 0) return null
    val channelCount = if (flags and 0x80 != 0) 3 else 1
    val useBaseColorSpace = flags and 0x40 != 0

    val baseDisplayRatio = readLogRatio(reader, signed = false) ?: return null
    val alternateDisplayRatio = readLogRatio(reader, signed = false) ?: return null
    if (baseDisplayRatio == alternateDisplayRatio) return null
    val baseIsHdr = baseDisplayRatio > alternateDisplayRatio
    val minDisplayRatio = minOf(baseDisplayRatio, alternateDisplayRatio)
    val fullHdrRatio = maxOf(baseDisplayRatio, alternateDisplayRatio)
    if (minDisplayRatio < 1f) return null

    val ratioMin = FloatArray(3)
    val ratioMax = FloatArray(3)
    val gamma = FloatArray(3)
    val epsilonSdr = FloatArray(3)
    val epsilonHdr = FloatArray(3)

    repeat(channelCount) { channel ->
        val minimum = readLogRatio(reader, signed = true) ?: return null
        val maximum = readLogRatio(reader, signed = true) ?: return null
        val encodedGamma = readRational(reader, signed = false) ?: return null
        val baseEpsilon = readRational(reader, signed = true) ?: return null
        val alternateEpsilon = readRational(reader, signed = true) ?: return null
        if (maximum < minimum || encodedGamma <= 0f) return null
        val channelGamma = 1f / encodedGamma
        if (!channelGamma.isFinite() || channelGamma > MAX_NUMERIC_VALUE) return null

        val targetChannels = if (channelCount == 1) 0..2 else channel..channel
        for (targetChannel in targetChannels) {
            ratioMin[targetChannel] = minimum
            ratioMax[targetChannel] = maximum
            gamma[targetChannel] = channelGamma
            epsilonSdr[targetChannel] = if (baseIsHdr) alternateEpsilon else baseEpsilon
            epsilonHdr[targetChannel] = if (baseIsHdr) baseEpsilon else alternateEpsilon
        }
    }

    return HeicGainmapMetadata(
        ratioMin = ratioMin,
        ratioMax = ratioMax,
        gamma = gamma,
        epsilonSdr = epsilonSdr,
        epsilonHdr = epsilonHdr,
        minDisplayRatioForHdrTransition = minDisplayRatio,
        displayRatioForFullHdr = fullHdrRatio,
        useBaseColorSpace = useBaseColorSpace,
        baseIsHdr = baseIsHdr,
    )
}

private fun readLogRatio(reader: BoxReader, signed: Boolean): Float? {
    val logRatio = readRational(reader, signed) ?: return null
    if (abs(logRatio.toDouble()) > MAX_LOG_RATIO) return null
    val linearRatio = 2.0.pow(logRatio.toDouble())
    if (!linearRatio.isFinite() || linearRatio > MAX_NUMERIC_VALUE) return null
    return linearRatio.toFloat()
}

private fun readRational(reader: BoxReader, signed: Boolean): Float? {
    val numerator = if (signed) {
        reader.readSignedInt()?.toDouble() ?: return null
    } else {
        reader.readUnsigned(4)?.toDouble() ?: return null
    }
    val denominator = reader.readUnsigned(4) ?: return null
    if (denominator == 0L) return null
    val value = numerator / denominator.toDouble()
    if (!value.isFinite() || abs(value) > MAX_NUMERIC_VALUE) return null
    return value.toFloat()
}

private fun writeUnsigned(bytes: ByteArray, offset: Int, width: Int, value: Long) {
    for (index in 0 until width) {
        val shift = (width - index - 1) * 8
        bytes[offset + index] = ((value ushr shift) and 0xff).toByte()
    }
}

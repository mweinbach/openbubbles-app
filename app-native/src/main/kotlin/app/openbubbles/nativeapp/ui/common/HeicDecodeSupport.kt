package app.openbubbles.nativeapp.ui.common

import android.media.MediaCodecInfo
import android.media.MediaCodecList
import android.os.Build
import java.io.File

/** The HEVC decoder configuration advertised by one HEIF image item. */
internal data class HevcImageConfiguration(
    val chromaFormat: Int,
    val bitDepth: Int,
)

/** Pure capability snapshot so the container decision is covered by host tests. */
internal data class HevcDecoderCapabilities(
    val profiles: Set<Int>,
    val colorFormats: Set<Int>,
)

private const val HeifInspectionLimit = 256 * 1024
private const val MaximumBoxDepth = 8
private const val HevcConfigurationLength = 19

private val HeifBrands = setOf(
    "heic", "heix", "hevc", "hevx", "mif1", "msf1", "heif", "heim", "heis", "hevm", "hevs",
)

private val HeifContainerBoxes = setOf("meta", "iprp", "ipco", "moov", "trak", "mdia", "minf", "stbl")

private val deviceHevcDecoders: List<HevcDecoderCapabilities>? by lazy {
    runCatching {
        MediaCodecList(MediaCodecList.REGULAR_CODECS).codecInfos
            .asSequence()
            .filterNot(MediaCodecInfo::isEncoder)
            .filter { info -> info.supportedTypes.any { it.equals("video/hevc", ignoreCase = true) } }
            .mapNotNull { info ->
                runCatching {
                    val capabilities = info.getCapabilitiesForType("video/hevc")
                    HevcDecoderCapabilities(
                        profiles = capabilities.profileLevels.mapTo(mutableSetOf()) { it.profile },
                        colorFormats = capabilities.colorFormats.toSet(),
                    )
                }.getOrNull()
            }
            .toList()
    }.getOrNull()
}

/**
 * HEIF container support does not imply support for its HEVC chroma profile.
 * Preflight the bounded metadata before either framework decoder can start a
 * codec known to reject Apple's 10-bit 4:4:4 screenshot originals.
 */
internal fun platformCanDecodeHeic(file: File): Boolean {
    if (file.extension.lowercase() !in setOf("heic", "heif")) return true
    val header = runCatching {
        val length = file.length().coerceAtMost(HeifInspectionLimit.toLong()).toInt()
        if (length <= 0) return true
        val bytes = ByteArray(length)
        val count = file.inputStream().use { it.read(bytes) }
        if (count <= 0) return true
        if (count == length) bytes else bytes.copyOf(count)
    }.getOrNull() ?: return true
    val configurations = inspectHevcImageConfigurations(header)
    if (configurations.isEmpty()) return true
    val decoders = deviceHevcDecoders ?: return true
    return hevcImageConfigurationsSupported(configurations, decoders, Build.VERSION.SDK_INT)
}

internal fun hevcImageConfigurationsSupported(
    configurations: List<HevcImageConfiguration>,
    decoders: List<HevcDecoderCapabilities>,
    sdkInt: Int,
): Boolean = configurations.all { configuration ->
    if (configuration.bitDepth > 10) return@all false
    when (configuration.chromaFormat) {
        0, 1 -> true
        2 -> decoders.any { decoder ->
            MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV422Flexible in decoder.colorFormats
        }
        3 -> decoders.any { decoder ->
            MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV444Flexible in decoder.colorFormats ||
                (sdkInt >= 37 && MediaCodecInfo.CodecProfileLevel.HEVCProfileMain444 in decoder.profiles)
        }
        else -> false
    }
}

/** Read HEVC configuration records from bounded, nested ISO-BMFF metadata only. */
internal fun inspectHevcImageConfigurations(data: ByteArray): List<HevcImageConfiguration> {
    if (!isHeifContainer(data)) return emptyList()
    return buildList { inspectBoxes(data, 0, data.size, depth = 0, output = this) }
}

private fun isHeifContainer(data: ByteArray): Boolean {
    if (data.size < 12 || boxType(data, 4) != "ftyp") return false
    val declaredSize = unsignedInt(data, 0)
    val limit = if (declaredSize in 16L..data.size.toLong()) declaredSize.toInt() else data.size
    if (boxType(data, 8) in HeifBrands) return true
    var offset = 16
    while (offset + 4 <= limit) {
        if (boxType(data, offset) in HeifBrands) return true
        offset += 4
    }
    return false
}

private fun inspectBoxes(
    data: ByteArray,
    start: Int,
    end: Int,
    depth: Int,
    output: MutableList<HevcImageConfiguration>,
) {
    if (depth > MaximumBoxDepth) return
    var offset = start
    while (offset <= end - 8) {
        val rawSize = unsignedInt(data, offset)
        val headerSize: Int
        val declaredSize: Long
        when (rawSize) {
            0L -> {
                headerSize = 8
                declaredSize = (end - offset).toLong()
            }
            1L -> {
                if (offset > end - 16) return
                headerSize = 16
                val high = unsignedInt(data, offset + 8)
                if (high != 0L) return
                declaredSize = unsignedInt(data, offset + 12)
            }
            else -> {
                headerSize = 8
                declaredSize = rawSize
            }
        }
        if (declaredSize < headerSize) return
        val declaredEnd = offset.toLong() + declaredSize
        val boxEnd = minOf(end.toLong(), declaredEnd).toInt()
        val payload = offset + headerSize
        when (val type = boxType(data, offset + 4)) {
            "hvcC" -> parseHevcConfiguration(data, payload, boxEnd)?.let(output::add)
            in HeifContainerBoxes -> {
                val childStart = payload + if (type == "meta") 4 else 0
                if (childStart <= boxEnd) {
                    inspectBoxes(data, childStart, boxEnd, depth + 1, output)
                }
            }
        }
        if (declaredEnd > end || declaredEnd <= offset) return
        offset = declaredEnd.toInt()
    }
}

private fun parseHevcConfiguration(
    data: ByteArray,
    start: Int,
    end: Int,
): HevcImageConfiguration? {
    if (end - start < HevcConfigurationLength || data[start].toInt() != 1) return null
    return HevcImageConfiguration(
        chromaFormat = data[start + 16].toInt() and 0x03,
        bitDepth = 8 + (data[start + 17].toInt() and 0x07),
    )
}

private fun boxType(data: ByteArray, offset: Int): String =
    String(data, offset, 4, Charsets.US_ASCII)

private fun unsignedInt(data: ByteArray, offset: Int): Long =
    ((data[offset].toLong() and 0xff) shl 24) or
        ((data[offset + 1].toLong() and 0xff) shl 16) or
        ((data[offset + 2].toLong() and 0xff) shl 8) or
        (data[offset + 3].toLong() and 0xff)

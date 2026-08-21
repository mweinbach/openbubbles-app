package app.openbubbles.nativeapp.data

import java.io.ByteArrayOutputStream

/**
 * Google Motion Photo v1 assembly (pure bytes, host-testable).
 *
 * A Motion Photo is a primary JPEG with Camera/Container XMP metadata plus
 * the video container appended verbatim at the end of the file — see
 * https://developer.android.com/media/platform/motion-photo-format. The spec
 * explicitly allows `video/quicktime` items, so an Apple Live Photo's MOV can
 * be appended unchanged; only the still's XMP needs to be written.
 */

/** Video container MIME types the Motion Photo spec permits. */
private val MOTION_PHOTO_VIDEO_MIMES = setOf("video/mp4", "video/quicktime")

private const val JPEG_MARKER_PREFIX = 0xFF
private const val JPEG_SOI = 0xD8
private const val JPEG_EOI = 0xD9
private const val JPEG_SOS = 0xDA
private const val JPEG_APP0 = 0xE0
private const val JPEG_APP1 = 0xE1

/** APP1 payload prefix of a standard XMP packet. */
private val XMP_APP1_HEADER = "http://ns.adobe.com/xap/1.0/\u0000".toByteArray(Charsets.ISO_8859_1)

/** APP1 payload prefix of an Adobe extended-XMP continuation segment. */
private val EXTENDED_XMP_APP1_HEADER =
    "http://ns.adobe.com/xmp/extension/".toByteArray(Charsets.ISO_8859_1)

/**
 * XMP content this writer cannot merge with (an existing GContainer
 * directory — e.g. an Ultra HDR gain map — or Motion Photo metadata).
 * Overwriting it would corrupt the still, so assembly fails over to the
 * caller's fallback instead.
 */
private val UNMERGEABLE_XMP_MARKERS = listOf(
    "ns.google.com/photos/1.0/container",
    "Container:Directory",
    "hdrgm",
    "MotionPhoto",
)

/**
 * Assembles a Motion Photo from a plain [stillJpeg] and a [video] container
 * (`video/mp4` or `video/quicktime`). Returns null when the still is not a
 * JPEG, is structurally corrupt, or already carries XMP this writer cannot
 * safely merge — callers fall back to saving the parts separately. Inputs
 * are never modified.
 */
internal fun buildJpegMotionPhoto(
    stillJpeg: ByteArray,
    video: ByteArray,
    videoMime: String,
): ByteArray? {
    if (video.isEmpty()) return null
    if (videoMime.lowercase() !in MOTION_PHOTO_VIDEO_MIMES) return null
    if (stillJpeg.size < 4 ||
        (stillJpeg[0].toInt() and 0xFF) != JPEG_MARKER_PREFIX ||
        (stillJpeg[1].toInt() and 0xFF) != JPEG_SOI
    ) {
        return null
    }

    // Walk the pre-scan header segments: find the end of the leading
    // APP0/APP1 run (the insertion point) and any existing XMP to replace.
    var position = 2
    var insertAt = 2
    var inLeadingAppRun = true
    val strippedRanges = mutableListOf<IntRange>()
    while (position + 4 <= stillJpeg.size) {
        if ((stillJpeg[position].toInt() and 0xFF) != JPEG_MARKER_PREFIX) return null
        val marker = stillJpeg[position + 1].toInt() and 0xFF
        if (marker == JPEG_SOS || marker == JPEG_EOI) break
        val length = ((stillJpeg[position + 2].toInt() and 0xFF) shl 8) or
            (stillJpeg[position + 3].toInt() and 0xFF)
        if (length < 2 || position + 2 + length > stillJpeg.size) return null
        val segmentEnd = position + 2 + length
        if (marker == JPEG_APP1) {
            val payload = stillJpeg.copyOfRange(position + 4, segmentEnd)
            if (payload.startsWith(EXTENDED_XMP_APP1_HEADER)) return null
            if (payload.startsWith(XMP_APP1_HEADER)) {
                val xmp = String(payload, XMP_APP1_HEADER.size, payload.size - XMP_APP1_HEADER.size, Charsets.ISO_8859_1)
                if (UNMERGEABLE_XMP_MARKERS.any { xmp.contains(it) }) return null
                strippedRanges += position until segmentEnd
            }
        }
        if (marker == JPEG_APP0 || marker == JPEG_APP1) {
            if (inLeadingAppRun) insertAt = segmentEnd
        } else {
            inLeadingAppRun = false
        }
        position = segmentEnd
    }

    val xmpSegment = motionPhotoXmpSegment(videoMime.lowercase(), video.size) ?: return null
    val output = ByteArrayOutputStream(stillJpeg.size + xmpSegment.size + video.size)
    var cursor = 0
    val skips = strippedRanges.sortedBy { it.first }
    var nextSkip = 0
    while (cursor < stillJpeg.size) {
        if (cursor == insertAt) output.write(xmpSegment)
        val skip = skips.getOrNull(nextSkip)
        if (skip != null && cursor == skip.first) {
            cursor = skip.last + 1
            nextSkip++
            continue
        }
        val next = minOf(
            if (insertAt > cursor) insertAt else stillJpeg.size,
            skip?.first?.takeIf { it > cursor } ?: stillJpeg.size,
        )
        output.write(stillJpeg, cursor, next - cursor)
        cursor = next
    }
    if (insertAt == stillJpeg.size) output.write(xmpSegment)
    output.write(video)
    return output.toByteArray()
}

/**
 * Display name for an exported Motion Photo. The spec requires the base name
 * to end in "MP": "IMG_0001.heic" -> "IMG_0001.MP.jpg".
 */
internal fun motionPhotoDisplayName(sourceName: String?): String {
    val base = sourceName.orEmpty()
        .substringBeforeLast('.')
        .replace(Regex("[\\s/\\\\]"), "_")
        .ifBlank { "motion_photo" }
    return "$base.MP.jpg"
}

private fun ByteArray.startsWith(prefix: ByteArray): Boolean {
    if (size < prefix.size) return false
    for (index in prefix.indices) {
        if (this[index] != prefix[index]) return false
    }
    return true
}

/** Complete APP1 segment (marker + length + header + packet) for the XMP. */
private fun motionPhotoXmpSegment(videoMime: String, videoLength: Int): ByteArray? {
    val packet = """
        <?xpacket begin="${'\uFEFF'}" id="W5M0MpCehiHzreSzNTczkc9d"?>
        <x:xmpmeta xmlns:x="adobe:ns:meta/">
          <rdf:RDF xmlns:rdf="http://www.w3.org/1999/02/22-rdf-syntax-ns#">
            <rdf:Description rdf:about=""
                xmlns:Camera="http://ns.google.com/photos/1.0/camera/"
                xmlns:Container="http://ns.google.com/photos/1.0/container/"
                xmlns:Item="http://ns.google.com/photos/1.0/container/item/"
                Camera:MotionPhoto="1"
                Camera:MotionPhotoVersion="1"
                Camera:MotionPhotoPresentationTimestampUs="-1">
              <Container:Directory>
                <rdf:Seq>
                  <rdf:li rdf:parseType="Resource">
                    <Container:Item
                        Item:Mime="image/jpeg"
                        Item:Semantic="Primary"
                        Item:Length="0"
                        Item:Padding="0"/>
                  </rdf:li>
                  <rdf:li rdf:parseType="Resource">
                    <Container:Item
                        Item:Mime="$videoMime"
                        Item:Semantic="MotionPhoto"
                        Item:Length="$videoLength"
                        Item:Padding="0"/>
                  </rdf:li>
                </rdf:Seq>
              </Container:Directory>
            </rdf:Description>
          </rdf:RDF>
        </x:xmpmeta>
        <?xpacket end="w"?>
    """.trimIndent().toByteArray(Charsets.UTF_8)

    val payloadLength = XMP_APP1_HEADER.size + packet.size
    val segmentLength = payloadLength + 2
    if (segmentLength > 0xFFFF) return null
    return ByteArrayOutputStream(4 + payloadLength).apply {
        write(JPEG_MARKER_PREFIX)
        write(JPEG_APP1)
        write((segmentLength ushr 8) and 0xFF)
        write(segmentLength and 0xFF)
        write(XMP_APP1_HEADER)
        write(packet)
    }.toByteArray()
}

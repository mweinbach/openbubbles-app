package app.openbubbles.nativeapp.data

/**
 * Save-to-device conversion policy for received media (pure, host-testable).
 *
 * Apple devices deliver HDR stills as HEIC/HEIF whose gain map Android can
 * only sometimes read (ISO 21496-1 content on newer Android versions). When
 * the platform did decode a gain map, exporting an Ultra HDR JPEG — Google's
 * gain-map format — keeps the HDR rendition visible in Android galleries.
 * Everything else is copied byte-identical: no silent transcodes.
 */

/** How a received image should be written to MediaStore. */
enum class ImageExportPlan {
    /** Byte-identical copy of the canonical attachment payload. */
    CopyBytes,

    /**
     * Re-encode as Ultra HDR JPEG (API 34+ `Bitmap.compress` keeps the gain
     * map) so Android galleries render the HDR headroom Apple sent.
     */
    ConvertToUltraHdrJpeg,
}

/** Minimum SDK where gain maps survive decode + JPEG re-encode. */
internal const val ULTRA_HDR_MIN_SDK = 34

private val HEIC_MIMES = setOf("image/heic", "image/heif", "image/heic-sequence", "image/heif-sequence")
private val HEIC_UTIS = setOf("public.heic", "public.heif")
private val HEIC_EXTENSIONS = setOf("heic", "heif")

/** True when the attachment container is Apple-typical HEIC/HEIF. */
internal fun isHeicContainer(mime: String?, uti: String?, name: String?): Boolean {
    if (mime?.lowercase() in HEIC_MIMES) return true
    if (uti?.lowercase() in HEIC_UTIS) return true
    return name?.substringAfterLast('.', "")?.lowercase() in HEIC_EXTENSIONS
}

/**
 * Chooses the export plan for a received image. Conversion happens only when
 * the source is a HEIC/HEIF container whose gain map the platform actually
 * decoded ([hasReadableGainmap]) on an Ultra-HDR-capable OS — an unreadable
 * (Apple-proprietary) gain map means the copy stays byte-identical rather
 * than silently flattening to a re-encoded SDR JPEG.
 */
fun imageExportPlan(
    mime: String?,
    uti: String?,
    name: String?,
    hasReadableGainmap: Boolean,
    sdkInt: Int,
): ImageExportPlan = if (
    isHeicContainer(mime, uti, name) && hasReadableGainmap && sdkInt >= ULTRA_HDR_MIN_SDK
) {
    ImageExportPlan.ConvertToUltraHdrJpeg
} else {
    ImageExportPlan.CopyBytes
}

/** Display name for the exported image ("IMG_1.heic" -> "IMG_1.jpg" on convert). */
fun exportedImageDisplayName(name: String?, plan: ImageExportPlan): String {
    val fallback = name?.takeIf { it.isNotBlank() } ?: "image"
    if (plan != ImageExportPlan.ConvertToUltraHdrJpeg) return fallback
    return "${fallback.substringBeforeLast('.').ifBlank { "image" }}.jpg"
}

/** MediaStore MIME for the exported image. */
fun exportedImageMime(mime: String?, plan: ImageExportPlan): String = when (plan) {
    ImageExportPlan.ConvertToUltraHdrJpeg -> "image/jpeg"
    ImageExportPlan.CopyBytes -> mime?.takeIf { it.startsWith("image/") } ?: "image/*"
}

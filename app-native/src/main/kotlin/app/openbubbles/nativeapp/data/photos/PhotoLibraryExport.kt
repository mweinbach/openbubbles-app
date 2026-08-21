package app.openbubbles.nativeapp.data.photos

import app.openbubbles.core.photos.PhotoMediaKind

/**
 * Where one downloaded iCloud original lands in the Android gallery.
 *
 * This is a one-way copy out of the app's private original cache: the exported
 * file is an ordinary gallery item with no iCloud identity attached. Nothing in
 * the Photos path observes the gallery, so a saved photo can never travel back
 * to iCloud — only the explicit picker/folder staging plus a separate upload tap
 * crosses the Apple write boundary.
 */
data class PhotoGalleryExportPlan(
    val displayName: String,
    val mimeType: String,
    val relativePath: String,
    val video: Boolean,
    val dateTakenMillis: Long?,
)

/**
 * Pure export decision logic, free of `android.*` so it runs under plain JUnit;
 * the MediaStore write lives in `ui/photos/PhotoGalleryExport.kt`.
 */
object PhotoLibraryExport {

    /**
     * Album shown in Google Photos and other gallery apps. `DCIM` is where
     * gallery apps expect camera-grade media, so photos and videos share one
     * `DCIM/iCloud` bucket rather than splitting across Pictures and Movies.
     */
    const val ALBUM: String = "iCloud"
    const val ALBUM_PATH: String = "DCIM/$ALBUM"

    /**
     * Plans the gallery copy of a promoted original.
     *
     * [cachedFileName] is the coordinator's promoted cache file, which is what
     * actually decides the bytes' format; [filename] only supplies the friendly
     * name. An extension this function does not recognise, or one that
     * disagrees with [mediaKind], returns `null` rather than inserting an
     * unidentifiable row into the user's gallery.
     */
    fun plan(
        cachedFileName: String,
        filename: String?,
        mediaKind: PhotoMediaKind,
        capturedAtMs: Long?,
    ): PhotoGalleryExportPlan? {
        val extension = cachedFileName.substringAfterLast('.', "").lowercase()
        val image = IMAGE_MIMES[extension]
        val video = VIDEO_MIMES[extension]
        val mime = image ?: video ?: return null
        val isVideo = video != null
        when (mediaKind) {
            PhotoMediaKind.Image -> if (isVideo) return null
            PhotoMediaKind.Video -> if (!isVideo) return null
            PhotoMediaKind.Unknown -> return null
        }
        val name = displayName(filename, cachedFileName, extension) ?: return null
        return PhotoGalleryExportPlan(
            displayName = name,
            mimeType = mime,
            relativePath = ALBUM_PATH,
            video = isVideo,
            dateTakenMillis = capturedAtMs?.takeIf { it > 0 },
        )
    }

    /**
     * The iCloud filename when it is safe and already names this format,
     * otherwise the cache file's own name. Path separators and control
     * characters are dropped so a hostile record name cannot escape the album.
     */
    private fun displayName(filename: String?, cachedFileName: String, extension: String): String? {
        val candidate = filename?.substringAfterLast('/')?.substringAfterLast('\\')
            ?.filter { it.code in 0x20..0x10FFFF && it != '/' && it != '\\' }
            ?.trim()
            ?.take(120)
            ?.takeIf { it.isNotEmpty() && it != "." && it != ".." }
        if (candidate != null && candidate.substringAfterLast('.', "").lowercase() == extension) {
            return candidate
        }
        val base = candidate?.substringBeforeLast('.')?.takeIf { it.isNotEmpty() }
        if (base != null) return "$base.$extension"
        return cachedFileName.takeIf { it.isNotEmpty() && it.substringAfterLast('.', "").isNotEmpty() }
    }

    private val IMAGE_MIMES = mapOf(
        "jpg" to "image/jpeg",
        "jpeg" to "image/jpeg",
        "png" to "image/png",
        "gif" to "image/gif",
        "heic" to "image/heic",
        "heif" to "image/heif",
        "webp" to "image/webp",
        "bmp" to "image/bmp",
        "tif" to "image/tiff",
        "tiff" to "image/tiff",
        "dng" to "image/x-adobe-dng",
        "avif" to "image/avif",
    )

    private val VIDEO_MIMES = mapOf(
        "mov" to "video/quicktime",
        "mp4" to "video/mp4",
        "m4v" to "video/x-m4v",
        "3gp" to "video/3gpp",
    )
}

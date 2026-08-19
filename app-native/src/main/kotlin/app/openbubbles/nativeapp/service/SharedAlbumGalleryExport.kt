package app.openbubbles.nativeapp.service

import java.io.File

data class SharedAlbumExportPlan(
    val albumName: String,
    val displayName: String,
    val relativePath: String,
    val mimeType: String,
    val isVideo: Boolean,
    val dedupKey: String,
)

/**
 * Pure decision logic for exporting synced shared-album files to the gallery.
 * Kept free of android.* so it runs under plain JUnit; MediaStore plumbing
 * lives in [SimpleFilePackager].
 *
 * A path qualifies only as `<albumsRoot>/<album>/<file>` with a known media
 * extension: that is exactly what the Rust sync loop writes, and it keeps
 * stray app files out of the user's gallery. The dedup key includes the file
 * length so a re-downloaded, changed asset exports again while an unchanged
 * re-delivery does not.
 */
object SharedAlbumGalleryExport {

    fun plan(albumsRoot: File, file: File, length: Long): SharedAlbumExportPlan? {
        val album = file.parentFile ?: return null
        if (album.parentFile?.path != albumsRoot.path) return null
        if (album.name.isEmpty() || file.name.isEmpty()) return null
        val extension = file.name.substringAfterLast('.', "").lowercase()
        val image = IMAGE_MIMES[extension]
        val video = VIDEO_MIMES[extension]
        val mime = image ?: video ?: return null
        return SharedAlbumExportPlan(
            albumName = album.name,
            displayName = file.name,
            relativePath = "Pictures/Shared Albums/${album.name}",
            mimeType = mime,
            isVideo = video != null,
            dedupKey = "${album.name}/${file.name}:$length",
        )
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
        "mkv" to "video/x-matroska",
        "webm" to "video/webm",
    )
}

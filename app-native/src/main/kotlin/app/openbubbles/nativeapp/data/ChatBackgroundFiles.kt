package app.openbubbles.nativeapp.data

import java.io.File

/**
 * Resolves a conversation background path to a raster file the chat UI
 * can decode.
 *
 * Native writes `files/chat_backgrounds/shared-*.img` (watch-image
 * bytes). The retired Flutter client stored a poster *prefix*:
 * `{app_flutter}/avatars/you/poster-N` with `{prefix}.jpg` holding a
 * `transcriptPosterSave` plist (not a JPEG) and `{prefix}/` holding
 * decoded photo-layer PNGs. [ChatListItem.transcriptBackgroundPath]
 * still points at that prefix after an in-place upgrade, so a plain
 * `File.isFile` check hides the wallpaper.
 */
internal fun resolveBackgroundImageFile(
    path: String?,
    extractWatchImage: (ByteArray) -> ByteArray? = { null },
): File? {
    if (path.isNullOrBlank()) return null
    val file = File(path)
    if (file.isFile) return file

    val cached = File("$path-watch.img")
    if (cached.isFile && cached.length() > 0L) return cached

    val sidecar = File("$path.jpg")
    if (sidecar.isFile) {
        if (isLikelyRaster(sidecar)) return sidecar
        val extracted = runCatching { extractWatchImage(sidecar.readBytes()) }.getOrNull()
        if (extracted != null && extracted.isNotEmpty()) {
            cached.outputStream().use { output ->
                output.write(extracted)
                output.flush()
            }
            if (cached.isFile && cached.length() > 0L) return cached
        }
    }

    val directory = if (file.isDirectory) file else File(path)
    if (!directory.isDirectory) return null
    return directory.walkTopDown()
        .filter { it.isFile && it.extension.lowercase() in DecodableImageExtensions }
        .maxByOrNull(File::length)
}

/** Device-local wallpaper overrides the Apple poster only while a file exists. */
fun ChatListItem.effectiveBackgroundPath(): String? =
    sequenceOf(customBackgroundPath, transcriptBackgroundPath)
        .mapNotNull { resolveBackgroundImageFile(it) }
        .firstOrNull()
        ?.absolutePath

internal fun extractWatchImageFromPosterSave(data: ByteArray): ByteArray? =
    runCatching {
        uniffi.rust_lib_bluebubbles.restoreTranscriptPosterSave(data).use { poster ->
            wallpaperBytesFromParsedPoster(poster)
        }
    }.getOrNull()

internal enum class PosterWallpaperKind { CLEAR, IMAGE }

internal data class PosterImageFile(
    val filename: String,
    val data: ByteArray,
)

/**
 * Chat wallpaper bytes from a parsed transcript poster.
 *
 * Dynamic/gradient posters are Apple's "cleared" encoding. Photo posters
 * often leave `watch.backgroundImage` empty and put the actual image in
 * the layer-stack files (`portrait-layer_background.HEIC`, etc.).
 */
internal fun wallpaperBytesFromPosterParts(
    kind: PosterWallpaperKind,
    watchImage: ByteArray,
    photoFiles: List<PosterImageFile>,
): ByteArray {
    if (kind == PosterWallpaperKind.CLEAR) return ByteArray(0)
    if (watchImage.isNotEmpty()) return watchImage
    return wallpaperBytesFromPhotoFiles(photoFiles)
}

internal fun wallpaperBytesFromPhotoFiles(files: List<PosterImageFile>): ByteArray {
    if (files.isEmpty()) return ByteArray(0)
    return files.maxWithOrNull(compareBy({ photoLayerRank(it.filename) }, { it.data.size }))
        ?.data
        ?: ByteArray(0)
}

internal fun wallpaperBytesFromParsedPoster(
    poster: uniffi.rust_lib_bluebubbles.UTranscriptPoster,
): ByteArray {
    val kind = when (poster.kind()) {
        is uniffi.rust_lib_bluebubbles.UPosterKind.TranscriptDynamic,
        is uniffi.rust_lib_bluebubbles.UPosterKind.TranscriptGradient,
        -> PosterWallpaperKind.CLEAR
        else -> PosterWallpaperKind.IMAGE
    }
    return wallpaperBytesFromPosterParts(
        kind = kind,
        watchImage = poster.watch().backgroundImage,
        photoFiles = poster.photoFiles(0u).map { file ->
            PosterImageFile(filename = file.filename, data = file.data)
        },
    )
}

private fun photoLayerRank(filename: String): Int {
    val name = filename.lowercase()
    return when {
        "overlay" in name || "foreground" in name -> 0
        "background" in name -> 2
        else -> 1
    }
}

private val DecodableImageExtensions = setOf("png", "jpg", "jpeg", "webp", "heic", "heif")

private fun isLikelyRaster(file: File): Boolean {
    if (file.length() < 8L) return false
    val header = ByteArray(12)
    file.inputStream().use { stream ->
        if (stream.read(header) < 8) return false
    }
    return header.isJpeg || header.isPng || header.isWebp || header.isHeif
}

private val ByteArray.isJpeg: Boolean
    get() = size >= 3 && this[0] == 0xFF.toByte() && this[1] == 0xD8.toByte() && this[2] == 0xFF.toByte()

private val ByteArray.isPng: Boolean
    get() = size >= 8 &&
        this[0] == 0x89.toByte() &&
        this[1] == 0x50.toByte() &&
        this[2] == 0x4E.toByte() &&
        this[3] == 0x47.toByte()

private val ByteArray.isWebp: Boolean
    get() = size >= 12 &&
        this[0] == 'R'.code.toByte() &&
        this[1] == 'I'.code.toByte() &&
        this[2] == 'F'.code.toByte() &&
        this[3] == 'F'.code.toByte() &&
        this[8] == 'W'.code.toByte() &&
        this[9] == 'E'.code.toByte() &&
        this[10] == 'B'.code.toByte() &&
        this[11] == 'P'.code.toByte()

private val ByteArray.isHeif: Boolean
    get() = size >= 12 &&
        this[4] == 'f'.code.toByte() &&
        this[5] == 't'.code.toByte() &&
        this[6] == 'y'.code.toByte() &&
        this[7] == 'p'.code.toByte()

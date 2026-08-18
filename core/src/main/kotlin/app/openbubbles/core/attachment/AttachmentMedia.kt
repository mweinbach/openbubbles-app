package app.openbubbles.core.attachment

/**
 * How a chat attachment should render: inline image, video, audio, PDF, or
 * a generic file. Classification prefers a specific MIME type, then Apple
 * UTI, then the transfer-name extension so a `.mov` with
 * `application/octet-stream` still plays as video.
 */
enum class AttachmentMediaKind {
    IMAGE,
    VIDEO,
    AUDIO,
    PDF,
    FILE,
}

object AttachmentMedia {
    fun kind(mime: String?, uti: String?, name: String?): AttachmentMediaKind {
        mimeKind(mime)?.let { return it }
        utiKind(uti)?.let { return it }
        return extensionKind(name) ?: AttachmentMediaKind.FILE
    }

    fun isImage(mime: String?, uti: String?, name: String?): Boolean =
        kind(mime, uti, name) == AttachmentMediaKind.IMAGE

    fun isVideo(mime: String?, uti: String?, name: String?): Boolean =
        kind(mime, uti, name) == AttachmentMediaKind.VIDEO

    fun isAudio(mime: String?, uti: String?, name: String?): Boolean =
        kind(mime, uti, name) == AttachmentMediaKind.AUDIO

    fun isPdf(mime: String?, uti: String?, name: String?): Boolean =
        kind(mime, uti, name) == AttachmentMediaKind.PDF

    /** Types the transcript can preview or play once the file is on disk. */
    fun isInlinePreviewable(mime: String?, uti: String?, name: String?): Boolean =
        when (kind(mime, uti, name)) {
            AttachmentMediaKind.IMAGE,
            AttachmentMediaKind.VIDEO,
            AttachmentMediaKind.AUDIO,
            AttachmentMediaKind.PDF,
            -> true
            AttachmentMediaKind.FILE -> false
        }

    /**
     * MIME an intent or player should use. Generic `octet-stream` falls
     * through to a type implied by UTI / extension.
     */
    fun suggestedMime(mime: String?, uti: String?, name: String?): String {
        val normalized = normalizeMime(mime)
        if (normalized != null && !isGenericMime(normalized)) return normalized
        return when (kind(mime, uti, name)) {
            AttachmentMediaKind.IMAGE -> imageMimeForName(name) ?: "image/*"
            AttachmentMediaKind.VIDEO -> videoMimeForName(name) ?: "video/*"
            AttachmentMediaKind.AUDIO -> audioMimeForName(name) ?: "audio/*"
            AttachmentMediaKind.PDF -> "application/pdf"
            AttachmentMediaKind.FILE -> normalized ?: "application/octet-stream"
        }
    }

    private fun mimeKind(mime: String?): AttachmentMediaKind? {
        val normalized = normalizeMime(mime) ?: return null
        if (isGenericMime(normalized)) return null
        return when {
            normalized == "application/pdf" -> AttachmentMediaKind.PDF
            normalized.startsWith("image/") -> AttachmentMediaKind.IMAGE
            normalized.startsWith("video/") -> AttachmentMediaKind.VIDEO
            normalized.startsWith("audio/") -> AttachmentMediaKind.AUDIO
            else -> null
        }
    }

    private fun utiKind(uti: String?): AttachmentMediaKind? {
        val value = uti?.trim()?.lowercase()?.substringAfterLast('/') ?: return null
        if (value.isEmpty() || value == "public.data" || value == "public.content") return null
        if (value == "com.adobe.pdf" || value == "public.pdf") return AttachmentMediaKind.PDF
        if (AUDIO_UTIS.contains(value) || value.endsWith("-audio")) return AttachmentMediaKind.AUDIO
        if (VIDEO_UTIS.contains(value) ||
            value.endsWith(".movie") ||
            value.endsWith("-video") ||
            value.contains("quicktime") ||
            value.contains("mpeg-4") && !value.contains("audio")
        ) {
            return AttachmentMediaKind.VIDEO
        }
        if (IMAGE_UTIS.contains(value) ||
            value == "public.image" ||
            value.startsWith("public.image.") ||
            value.endsWith(".heic") ||
            value.endsWith(".heif") ||
            value.contains("heic") ||
            value.contains("heif")
        ) {
            return AttachmentMediaKind.IMAGE
        }
        return null
    }

    private fun extensionKind(name: String?): AttachmentMediaKind? {
        val ext = extensionOf(name) ?: return null
        return when {
            IMAGE_EXTENSIONS.contains(ext) -> AttachmentMediaKind.IMAGE
            VIDEO_EXTENSIONS.contains(ext) -> AttachmentMediaKind.VIDEO
            AUDIO_EXTENSIONS.contains(ext) -> AttachmentMediaKind.AUDIO
            ext == "pdf" -> AttachmentMediaKind.PDF
            else -> null
        }
    }

    private fun normalizeMime(mime: String?): String? =
        mime?.substringBefore(';')?.trim()?.lowercase()?.takeIf { it.isNotEmpty() }

    private fun isGenericMime(mime: String): Boolean =
        mime == "application/octet-stream" ||
            mime == "application/x-octet-stream" ||
            mime == "binary/octet-stream"

    private fun extensionOf(name: String?): String? {
        val trimmed = name?.substringAfterLast('/')?.substringAfterLast('\\')?.trim().orEmpty()
        val dot = trimmed.lastIndexOf('.')
        if (dot <= 0 || dot == trimmed.lastIndex) return null
        return trimmed.substring(dot + 1).lowercase()
    }

    private fun imageMimeForName(name: String?): String? = when (extensionOf(name)) {
        "jpg", "jpeg" -> "image/jpeg"
        "png" -> "image/png"
        "gif" -> "image/gif"
        "webp" -> "image/webp"
        "heic" -> "image/heic"
        "heif" -> "image/heif"
        "tif", "tiff" -> "image/tiff"
        "bmp" -> "image/bmp"
        "avif" -> "image/avif"
        else -> null
    }

    private fun videoMimeForName(name: String?): String? = when (extensionOf(name)) {
        "mov" -> "video/quicktime"
        "mp4", "m4v" -> "video/mp4"
        "webm" -> "video/webm"
        "mkv" -> "video/x-matroska"
        "avi" -> "video/x-msvideo"
        "3gp", "3gpp" -> "video/3gpp"
        "hevc" -> "video/hevc"
        else -> null
    }

    private fun audioMimeForName(name: String?): String? = when (extensionOf(name)) {
        "m4a" -> "audio/mp4"
        "aac" -> "audio/aac"
        "mp3" -> "audio/mpeg"
        "wav" -> "audio/wav"
        "caf" -> "audio/x-caf"
        "amr" -> "audio/amr"
        "aiff", "aif" -> "audio/aiff"
        else -> null
    }

    private val IMAGE_EXTENSIONS = setOf(
        "jpg", "jpeg", "png", "gif", "webp", "heic", "heif", "tif", "tiff", "bmp", "avif",
    )
    private val VIDEO_EXTENSIONS = setOf(
        "mov", "mp4", "m4v", "avi", "mkv", "webm", "3gp", "3gpp", "hevc", "ts",
    )
    private val AUDIO_EXTENSIONS = setOf(
        "m4a", "aac", "caf", "mp3", "wav", "aiff", "aif", "amr",
    )
    private val IMAGE_UTIS = setOf(
        "public.jpeg",
        "public.png",
        "public.tiff",
        "public.gif",
        "com.compuserve.gif",
        "public.heic",
        "public.heif",
        "public.heic-sequence",
        "public.heif-sequence",
        "org.webmproject.webp",
        "public.camera-raw-image",
    )
    private val VIDEO_UTIS = setOf(
        "public.movie",
        "public.video",
        "public.mpeg-4",
        "public.mpeg",
        "public.avi",
        "com.apple.quicktime-movie",
        "com.apple.m4v-video",
        "public.hevc",
        "org.webmproject.webm",
        "public.3gpp",
        "public.3gpp2",
    )
    private val AUDIO_UTIS = setOf(
        "public.audio",
        "public.mp3",
        "public.mpeg-4-audio",
        "public.aiff-audio",
        "com.apple.coreaudio-format",
        "com.apple.m4a-audio",
        "com.apple.protected-mpeg-4-audio",
    )
}

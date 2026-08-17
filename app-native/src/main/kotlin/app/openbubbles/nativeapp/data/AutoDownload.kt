package app.openbubbles.nativeapp.data

/**
 * Size ceiling for the automatic download of incoming media attachments,
 * shown as a single-choice list in Settings → Messaging. [maxBytes] is the
 * persisted value: 0 disables auto-download,
 * [MessagingPrefs.AUTO_DOWNLOAD_UNLIMITED] fetches every supported payload.
 */
enum class AutoDownloadLimit(
    val persistedValue: Long,
    val title: String,
    val description: String,
) {
    OFF(
        persistedValue = 0L,
        title = "Off",
        description = "Every attachment downloads only when you tap it",
    ),
    MB_1(
        persistedValue = 1L * 1024 * 1024,
        title = "Up to 1 MB",
        description = "Photos and voice memos; larger files wait for a tap",
    ),
    MB_5(
        persistedValue = 5L * 1024 * 1024,
        title = "Up to 5 MB",
        description = "Most photos and short clips download right away",
    ),
    MB_10(
        persistedValue = MessagingPrefs.DEFAULT_AUTO_DOWNLOAD_MAX_BYTES,
        title = "Up to 10 MB",
        description = "Photos, voice memos, and short videos (default)",
    ),
    MB_25(
        persistedValue = 25L * 1024 * 1024,
        title = "Up to 25 MB",
        description = "Longer videos download right away too",
    ),
    MB_50(
        persistedValue = 50L * 1024 * 1024,
        title = "Up to 50 MB",
        description = "Only very large files wait for a tap",
    ),
    MB_100(
        persistedValue = 100L * 1024 * 1024,
        title = "Up to 100 MB",
        description = "Nearly everything downloads right away",
    ),
    UNLIMITED(
        persistedValue = MessagingPrefs.AUTO_DOWNLOAD_UNLIMITED,
        title = "Unlimited",
        description = "Every photo, video, and voice memo downloads right away",
    ),
    ;

    companion object {
        /** Resolves a persisted ceiling, defaulting to [MB_10] for unknown values. */
        fun fromPersistedValue(value: Long): AutoDownloadLimit =
            entries.firstOrNull { it.persistedValue == value } ?: MB_10
    }
}

/**
 * True when an incoming attachment should download without a tap: the mime
 * type is media we can render or play inline (images, videos, audio — voice
 * memos must be on disk for the in-chat player), the payload is fetchable
 * (the row carries the transfer metadata the downloader needs), and the
 * declared size fits within [maxBytes]. A missing size is treated as small:
 * Apple's transfer records almost always declare one, and blocking a
 * size-less voice memo would break inline playback.
 */
fun isAutoDownloadEligible(
    mime: String?,
    totalBytes: Long?,
    hasTransferMetadata: Boolean,
    maxBytes: Long,
): Boolean {
    if (maxBytes == 0L) return false
    if (!hasTransferMetadata) return false
    val media = mime?.lowercase()?.let {
        it.startsWith("image/") || it.startsWith("video/") || it.startsWith("audio/")
    } == true
    if (!media) return false
    if (maxBytes == MessagingPrefs.AUTO_DOWNLOAD_UNLIMITED) return true
    val size = totalBytes ?: 0L
    return size <= maxBytes
}

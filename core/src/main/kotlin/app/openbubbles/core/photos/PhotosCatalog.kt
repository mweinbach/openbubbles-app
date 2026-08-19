package app.openbubbles.core.photos

/** Metadata restored before a live iCloud request completes. */
data class CachedPhotos(
    val assets: List<PhotoSummary> = emptyList(),
    val nextCursor: String? = null,
)

enum class PhotoTransferDirection {
    Download,
    Upload,
}

enum class PhotoResourceKind {
    Preview,
    Original,
    LivePhotoVideo,
}

enum class PhotoTransferState {
    Queued,
    Running,
    Succeeded,
    Failed,
    Blocked,
}

data class PhotoTransfer(
    val id: String,
    val assetId: String?,
    val direction: PhotoTransferDirection,
    val resourceKind: PhotoResourceKind,
    val localPath: String,
    val filename: String?,
    val mimeType: String?,
    val state: PhotoTransferState,
    val bytesDone: Long = 0,
    val totalBytes: Long = 0,
    val attemptCount: Int = 0,
    val lastError: String? = null,
    val createdAtMs: Long,
    val updatedAtMs: Long,
)

/**
 * Durable Photos state. Implementations must commit a metadata snapshot and
 * its continuation cursor in one transaction: advancing the cursor before
 * the rows are durable would permanently skip assets after a crash.
 */
interface PhotosCatalog {
    suspend fun loadMetadata(): CachedPhotos
    suspend fun replaceMetadata(assets: List<PhotoSummary>, nextCursor: String?)

    suspend fun transfers(): List<PhotoTransfer>
    suspend fun transfer(id: String): PhotoTransfer?
    suspend fun putTransfer(transfer: PhotoTransfer)

    /** A process death cannot leave a transfer permanently marked running. */
    suspend fun recoverInterruptedTransfers()
}

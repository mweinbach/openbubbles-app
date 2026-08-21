package app.openbubbles.core.photos

import uniffi.rust_lib_bluebubbles.NativePushState
import uniffi.rust_lib_bluebubbles.UPhotoMediaKind
import uniffi.rust_lib_bluebubbles.UPhotosAccessState
import uniffi.rust_lib_bluebubbles.UProgressCallback

enum class PhotosAvailability {
    Ready,
    Indexing,
    Unavailable,
}

data class PhotosAccess(
    val availability: PhotosAvailability,
    val detail: String,
)

enum class PhotoMediaKind {
    Image,
    Video,
    Unknown,
}

data class PhotoSummary(
    val id: String,
    val assetId: String,
    val filename: String?,
    val mediaKind: PhotoMediaKind,
    val livePhoto: Boolean,
    val width: Int?,
    val height: Int?,
    val originalSize: Long?,
    val previewSize: Long? = null,
    val capturedAtMs: Long?,
    val addedAtMs: Long?,
    val favorite: Boolean,
    val hidden: Boolean,
)

data class PhotosPage(
    val assets: List<PhotoSummary>,
    val nextCursor: String?,
)

data class PhotoUploadReceipt(
    val masterId: String,
    val assetId: String,
)

/** Foreground personal iCloud Photos seam for metadata, protected resources, and explicit uploads. */
interface PhotosPort {
    suspend fun access(): PhotosAccess
    suspend fun page(cursor: String?, limit: Int): PhotosPage

    suspend fun downloadPreview(
        asset: PhotoSummary,
        destPath: String,
        onProgress: (Long, Long) -> Unit,
    ): Result<Unit> = Result.failure(UnsupportedOperationException("Photos preview downloads are unavailable"))

    suspend fun downloadOriginal(
        asset: PhotoSummary,
        destPath: String,
        onProgress: (Long, Long) -> Unit,
    ): Result<Unit> = Result.failure(UnsupportedOperationException("Photos original downloads are unavailable"))

    suspend fun uploadJpeg(
        originalPath: String,
        previewPath: String,
        filename: String,
        capturedAtMs: Long?,
        orientation: Int,
    ): Result<PhotoUploadReceipt> =
        Result.failure(UnsupportedOperationException("iCloud Photos uploads are unavailable"))
}

class UniffiPhotosPort(private val state: NativePushState) : PhotosPort {
    override suspend fun access(): PhotosAccess {
        val access = state.photosAccessState()
        return PhotosAccess(
            availability = when (access.state) {
                UPhotosAccessState.READY -> PhotosAvailability.Ready
                UPhotosAccessState.INDEXING -> PhotosAvailability.Indexing
                UPhotosAccessState.UNAVAILABLE -> PhotosAvailability.Unavailable
            },
            detail = access.detail,
        )
    }

    override suspend fun page(cursor: String?, limit: Int): PhotosPage {
        require(limit in 1..100) { "Photos page size must be between 1 and 100" }
        val page = state.listPhotosPage(cursor, limit.toUInt())
        return PhotosPage(
            assets = page.assets.map { asset ->
                PhotoSummary(
                    id = asset.id,
                    assetId = asset.assetId,
                    filename = asset.filename,
                    mediaKind = when (asset.mediaKind) {
                        UPhotoMediaKind.IMAGE -> PhotoMediaKind.Image
                        UPhotoMediaKind.VIDEO -> PhotoMediaKind.Video
                        UPhotoMediaKind.UNKNOWN -> PhotoMediaKind.Unknown
                    },
                    livePhoto = asset.livePhoto,
                    width = asset.width?.toSafeInt(),
                    height = asset.height?.toSafeInt(),
                    originalSize = asset.originalSize?.toSafeLong(),
                    previewSize = asset.previewSize?.toSafeLong(),
                    capturedAtMs = asset.capturedAtMs?.toSafeLong(),
                    addedAtMs = asset.addedAtMs?.toSafeLong(),
                    favorite = asset.favorite,
                    hidden = asset.hidden,
                )
            },
            nextCursor = page.nextCursor,
        )
    }

    override suspend fun downloadPreview(
        asset: PhotoSummary,
        destPath: String,
        onProgress: (Long, Long) -> Unit,
    ): Result<Unit> = runCatching {
        val mediaKind = when (asset.mediaKind) {
            PhotoMediaKind.Image -> UPhotoMediaKind.IMAGE
            PhotoMediaKind.Video -> UPhotoMediaKind.VIDEO
            PhotoMediaKind.Unknown -> UPhotoMediaKind.UNKNOWN
        }
        state.downloadPhotoPreview(
            masterId = asset.id,
            mediaKind = mediaKind,
            destPath = destPath,
            progress = object : UProgressCallback {
                override fun onProgress(done: ULong, total: ULong) {
                    onProgress(done.toSafeLong(), total.toSafeLong())
                }
            },
        )
    }

    override suspend fun downloadOriginal(
        asset: PhotoSummary,
        destPath: String,
        onProgress: (Long, Long) -> Unit,
    ): Result<Unit> = runCatching {
        val mediaKind = when (asset.mediaKind) {
            PhotoMediaKind.Image -> UPhotoMediaKind.IMAGE
            PhotoMediaKind.Video -> UPhotoMediaKind.VIDEO
            PhotoMediaKind.Unknown -> UPhotoMediaKind.UNKNOWN
        }
        state.downloadPhotoOriginal(
            masterId = asset.id,
            mediaKind = mediaKind,
            destPath = destPath,
            progress = object : UProgressCallback {
                override fun onProgress(done: ULong, total: ULong) {
                    onProgress(done.toSafeLong(), total.toSafeLong())
                }
            },
        )
    }

    override suspend fun uploadJpeg(
        originalPath: String,
        previewPath: String,
        filename: String,
        capturedAtMs: Long?,
        orientation: Int,
    ): Result<PhotoUploadReceipt> = runCatching {
        require(orientation in 1..8) { "Photos upload orientation is invalid" }
        val result = state.uploadPhotoJpeg(
            originalPath = originalPath,
            previewPath = previewPath,
            filename = filename,
            capturedAtMs = capturedAtMs?.takeIf { it >= 0 }?.toULong(),
            orientation = orientation.toUInt(),
        )
        PhotoUploadReceipt(
            masterId = result.masterId,
            assetId = result.assetId,
        )
    }
}

private fun ULong.toSafeLong(): Long = coerceAtMost(Long.MAX_VALUE.toULong()).toLong()
private fun UInt.toSafeInt(): Int = coerceAtMost(Int.MAX_VALUE.toUInt()).toInt()

data class PhotosSnapshot(
    val access: PhotosAccess,
    val assets: List<PhotoSummary> = emptyList(),
    val nextCursor: String? = null,
)

/** Small orchestration layer shared by Android today and desktop later. */
class PhotosBrowser(
    private val port: PhotosPort,
    private val pageSize: Int = 60,
) {
    init {
        require(pageSize in 1..100) { "Photos page size must be between 1 and 100" }
    }

    suspend fun initial(): PhotosSnapshot {
        val access = port.access()
        if (access.availability != PhotosAvailability.Ready) {
            return PhotosSnapshot(access = access)
        }
        val page = port.page(cursor = null, limit = pageSize)
        return PhotosSnapshot(
            access = access,
            assets = page.assets.distinctBy(PhotoSummary::id),
            nextCursor = page.nextCursor,
        )
    }

    suspend fun next(snapshot: PhotosSnapshot): PhotosSnapshot {
        val cursor = snapshot.nextCursor ?: return snapshot
        val page = port.page(cursor = cursor, limit = pageSize)
        check(page.nextCursor != cursor) { "iCloud Photos cursor did not advance" }
        return snapshot.copy(
            assets = (snapshot.assets + page.assets).distinctBy(PhotoSummary::id),
            nextCursor = page.nextCursor,
        )
    }
}

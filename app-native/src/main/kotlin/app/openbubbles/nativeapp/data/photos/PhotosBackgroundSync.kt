package app.openbubbles.nativeapp.data.photos

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import app.openbubbles.core.photos.PhotoTransferCoordinator
import app.openbubbles.core.photos.PhotoTransferDirection
import app.openbubbles.core.photos.PhotoTransferState
import app.openbubbles.core.photos.PhotosAvailability
import app.openbubbles.core.photos.PhotosBrowser
import app.openbubbles.core.photos.UniffiPhotosPort
import app.openbubbles.nativeapp.data.PushStateHolder
import java.io.File

/**
 * Deliberate dormant boundary for a future policy-reviewed background Photos pass.
 * There is no schedule path or preference that can turn it on in this release.
 */
object PhotosBackgroundSync {
    const val ENABLED = false
    private const val WORK_NAME = "openbubbles-icloud-photos-background-sync"

    fun keepDisabled(context: Context) {
        WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
    }

    /** Sign-out waits until any legacy job is actually cancelled. */
    fun cancelAndAwait(context: Context) {
        WorkManager.getInstance(context)
            .cancelUniqueWork(WORK_NAME)
            .result
            .get()
    }
}

class PhotosBackgroundSyncWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        if (!PhotosBackgroundSync.ENABLED) return Result.success()
        val state = PushStateHolder.state ?: return Result.retry()
        return runCatching {
            val context = applicationContext
            val port = UniffiPhotosPort(state)
            val catalog = PhotosSqliteCatalog(context)
            val coordinator = PhotoTransferCoordinator(
                port = port,
                catalog = catalog,
                previewRoot = File(context.filesDir, "icloud_photos/previews"),
                uploadRoot = File(context.filesDir, "icloud_photos/uploads"),
                originalRoot = File(context.filesDir, "icloud_photos/originals"),
            )
            val folderStore = PhotoFolderSources(context)
            folderStore.sources().forEach { source ->
                folderStore.photos(source).forEach { uri ->
                    val candidate = preparePhotoUploadCandidate(context, uri)
                    try {
                        coordinator.planUpload(
                            sourcePath = candidate.file.absolutePath,
                            previewPath = candidate.previewFile.absolutePath,
                            filename = candidate.filename,
                            mimeType = candidate.mimeType,
                            orientation = candidate.orientation,
                            capturedAtMs = candidate.capturedAtMs,
                        )
                    } finally {
                        candidate.file.delete()
                        candidate.previewFile.delete()
                    }
                }
            }
            catalog.transfers()
                .filter {
                    it.direction == PhotoTransferDirection.Upload &&
                        it.state in listOf(PhotoTransferState.Queued, PhotoTransferState.Failed)
                }
                .forEach { coordinator.upload(it) }
            val snapshot = PhotosBrowser(port).initial()
            if (snapshot.access.availability == PhotosAvailability.Ready) {
                catalog.replaceMetadata(snapshot.assets, snapshot.nextCursor)
            }
        }.fold(
            onSuccess = { Result.success() },
            onFailure = { Result.retry() },
        )
    }
}

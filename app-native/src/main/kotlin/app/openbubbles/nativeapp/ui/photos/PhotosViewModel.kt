package app.openbubbles.nativeapp.ui.photos

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import app.openbubbles.core.photos.PhotoMediaKind
import app.openbubbles.core.photos.PhotoResourceKind
import app.openbubbles.core.photos.PhotoSummary
import app.openbubbles.core.photos.PhotoTransfer
import app.openbubbles.core.photos.PhotoTransferCoordinator
import app.openbubbles.core.photos.PhotoTransferDirection
import app.openbubbles.core.photos.PhotoTransferState
import app.openbubbles.core.photos.PhotosAccess
import app.openbubbles.core.photos.PhotosAvailability
import app.openbubbles.core.photos.PhotosBrowser
import app.openbubbles.core.photos.PhotosCatalog
import app.openbubbles.core.photos.PhotosPort
import app.openbubbles.core.photos.PhotosSnapshot
import app.openbubbles.core.photos.UniffiPhotosPort
import app.openbubbles.nativeapp.data.PushStateHolder
import app.openbubbles.nativeapp.data.photos.PhotoFolderSource
import app.openbubbles.nativeapp.data.photos.PhotoFolderSources
import app.openbubbles.nativeapp.data.photos.PhotosBackgroundSync
import app.openbubbles.nativeapp.data.photos.PhotosSqliteCatalog
import app.openbubbles.nativeapp.data.photos.PhotosWorkRegistry
import app.openbubbles.nativeapp.data.photos.PickedPhotoUpload
import app.openbubbles.nativeapp.data.photos.preparePhotoUploadCandidate
import java.io.File
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import uniffi.rust_lib_bluebubbles.NativePushState

data class PhotosUiState(
    val loading: Boolean = true,
    val refreshing: Boolean = false,
    val loadingMore: Boolean = false,
    val snapshot: PhotosSnapshot? = null,
    val previewTransfers: Map<String, PhotoTransfer> = emptyMap(),
    val originalTransfers: Map<String, PhotoTransfer> = emptyMap(),
    val selectedAssetId: String? = null,
    val uploadPlans: List<PhotoTransfer> = emptyList(),
    val folderSources: List<PhotoFolderSource> = emptyList(),
    val planningUpload: Boolean = false,
    val planningDone: Int = 0,
    val planningTotal: Int = 0,
    val uploadError: String? = null,
    val sourceMessage: String? = null,
    val showingCachedMetadata: Boolean = false,
    val error: String? = null,
    val backgroundSyncEnabled: Boolean = PhotosBackgroundSync.ENABLED,
)

private class LivePhotosPort(private val stateProvider: () -> NativePushState?) : PhotosPort {
    private fun port() = UniffiPhotosPort(
        stateProvider() ?: error("Apple services are not connected"),
    )

    override suspend fun access(): PhotosAccess = port().access()
    override suspend fun page(cursor: String?, limit: Int) = port().page(cursor, limit)
    override suspend fun downloadPreview(
        asset: PhotoSummary,
        destPath: String,
        onProgress: (Long, Long) -> Unit,
    ) = port().downloadPreview(asset, destPath, onProgress)

    override suspend fun downloadOriginal(
        asset: PhotoSummary,
        destPath: String,
        onProgress: (Long, Long) -> Unit,
    ) = port().downloadOriginal(asset, destPath, onProgress)

    override suspend fun uploadJpeg(
        originalPath: String,
        previewPath: String,
        filename: String,
        capturedAtMs: Long?,
        orientation: Int,
    ) = port().uploadJpeg(originalPath, previewPath, filename, capturedAtMs, orientation)
}

internal interface PhotosFolderPort {
    suspend fun sources(): List<PhotoFolderSource>
    suspend fun add(uri: Uri): List<PhotoFolderSource>
    suspend fun remove(uri: Uri): List<PhotoFolderSource>
    suspend fun photos(source: PhotoFolderSource): List<Uri>
}

private class AndroidPhotosFolderPort(
    private val folders: PhotoFolderSources,
) : PhotosFolderPort {
    override suspend fun sources(): List<PhotoFolderSource> = withContext(Dispatchers.IO) {
        folders.sources()
    }

    override suspend fun add(uri: Uri): List<PhotoFolderSource> = withContext(Dispatchers.IO) {
        folders.add(uri)
        folders.sources()
    }

    override suspend fun remove(uri: Uri): List<PhotoFolderSource> = withContext(Dispatchers.IO) {
        folders.remove(uri)
        folders.sources()
    }

    override suspend fun photos(source: PhotoFolderSource): List<Uri> = folders.photos(source)
}

internal class PhotosViewModel(
    private val browser: PhotosBrowser,
    private val catalog: PhotosCatalog,
    private val coordinator: PhotoTransferCoordinator,
    private val folders: PhotosFolderPort,
    private val prepareUpload: suspend (Uri) -> PickedPhotoUpload,
) : ViewModel() {
    private val mutableState = MutableStateFlow(PhotosUiState())
    val uiState: StateFlow<PhotosUiState> = mutableState.asStateFlow()
    private var requestJob: Job? = null
    private val previewJobs = mutableMapOf<String, Job>()
    private val originalJobs = mutableMapOf<String, Job>()
    private var selectionJob: Job? = null
    private val validatedDownloads = mutableSetOf<String>()
    private val previewSlots = Semaphore(4)
    private val originalSlots = Semaphore(1)
    private val workSession = PhotosWorkRegistry.register()

    init {
        requestJob = launchWork { bootstrap() }
        if (requestJob == null) {
            mutableState.value = PhotosUiState(
                loading = false,
                snapshot = PhotosSnapshot(
                    PhotosAccess(
                        PhotosAvailability.Unavailable,
                        "Sign in to Apple services to browse iCloud Photos",
                    ),
                ),
            )
        }
    }

    fun refresh() {
        if (requestJob?.isActive == true) return
        requestJob = launchWork { refreshRemote() }
    }

    private suspend fun bootstrap() {
        catalog.recoverInterruptedTransfers()
        val cached = catalog.loadMetadata()
        val allTransfers = catalog.transfers()
        val folderSources = folders.sources()
        val assetsById = cached.assets.associateBy(PhotoSummary::id)
        val downloads = allTransfers.filter {
            it.direction == PhotoTransferDirection.Download && it.assetId != null
        }.map { transfer ->
            val asset = assetsById[transfer.assetId]
            if (asset == null) {
                transfer
            } else {
                coordinator.revalidateCompletedDownload(asset, transfer).also { checked ->
                    if (checked.state == PhotoTransferState.Succeeded) {
                        validatedDownloads += checked.id
                    }
                }
            }
        }
        val previews = downloads.filter { it.resourceKind == PhotoResourceKind.Preview }
            .associateBy { checkNotNull(it.assetId) }
        val originals = downloads.filter { it.resourceKind == PhotoResourceKind.Original }
            .associateBy { checkNotNull(it.assetId) }
        val uploads = allTransfers.filter { it.direction == PhotoTransferDirection.Upload }
        mutableState.update {
            it.copy(
                previewTransfers = previews,
                originalTransfers = originals,
                uploadPlans = uploads,
                folderSources = folderSources,
                loading = cached.assets.isEmpty(),
                snapshot = cached.assets.takeIf(List<PhotoSummary>::isNotEmpty)?.let { assets ->
                    PhotosSnapshot(
                        access = PhotosAccess(
                            PhotosAvailability.Unavailable,
                            "Showing metadata saved on this device",
                        ),
                        assets = assets,
                        nextCursor = cached.nextCursor,
                    )
                },
                showingCachedMetadata = cached.assets.isNotEmpty(),
            )
        }
        refreshRemote()
    }

    private suspend fun refreshRemote() {
        mutableState.update {
            it.copy(loading = it.snapshot == null, refreshing = it.snapshot != null, error = null)
        }
        try {
            val snapshot = browser.initial()
            if (snapshot.access.availability == PhotosAvailability.Ready) {
                catalog.replaceMetadata(snapshot.assets, snapshot.nextCursor)
            }
            mutableState.update { it.copy(snapshot = snapshot, showingCachedMetadata = false) }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Throwable) {
            mutableState.update {
                val hasCache = !it.snapshot?.assets.isNullOrEmpty()
                it.copy(
                    snapshot = it.snapshot ?: PhotosSnapshot(
                        PhotosAccess(
                            PhotosAvailability.Unavailable,
                            "Personal iCloud Photos could not be reached",
                        ),
                    ),
                    showingCachedMetadata = hasCache,
                    error = if (hasCache) null else error.message ?: "iCloud Photos refresh failed",
                )
            }
        } finally {
            mutableState.update { it.copy(loading = false, refreshing = false) }
        }
    }

    fun loadMore() {
        val snapshot = mutableState.value.snapshot ?: return
        if (snapshot.nextCursor == null || requestJob?.isActive == true) return
        requestJob = launchWork {
            mutableState.update { it.copy(loadingMore = true, error = null) }
            try {
                val next = browser.next(snapshot)
                catalog.replaceMetadata(next.assets, next.nextCursor)
                mutableState.update { it.copy(snapshot = next, showingCachedMetadata = false) }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Throwable) {
                mutableState.update { it.copy(error = error.message ?: "Could not load more photos") }
            } finally {
                mutableState.update { it.copy(loadingMore = false) }
            }
        }
    }

    /** Starts only when a lazy-grid tile enters the composed viewport/prefetch window. */
    fun ensurePreview(asset: PhotoSummary, retry: Boolean = false) {
        if (asset.previewSize == null || asset.mediaKind == PhotoMediaKind.Unknown) return
        val existing = mutableState.value.previewTransfers[asset.id]
        if (previewJobs[asset.id]?.isActive == true) return
        if (existing.isValidatedCacheFile()) return
        if (!retry && existing?.state !in listOf(
                null,
                PhotoTransferState.Queued,
                PhotoTransferState.Succeeded,
            )
        ) return
        lateinit var job: Job
        job = workSession.createJob(viewModelScope) {
            try {
                val completed = previewSlots.withPermit {
                    coordinator.downloadPreview(asset) { updatePreview(asset.id, it) }
                }
                if (completed.state == PhotoTransferState.Succeeded) validatedDownloads += completed.id
                updatePreview(asset.id, completed)
            } finally {
                previewJobs.remove(asset.id, job)
            }
        } ?: return
        previewJobs[asset.id] = job
        job.start()
    }

    fun cancelPreview(asset: PhotoSummary) {
        previewJobs.remove(asset.id)?.cancel()
    }

    fun select(asset: PhotoSummary) {
        mutableState.update { it.copy(selectedAssetId = asset.id) }
        selectionJob?.cancel()
        selectionJob = launchWork {
            val previousJobs = originalJobs
                .filterKeys { it != asset.id }
                .values
                .distinct()
            previousJobs.forEach(Job::cancel)
            previousJobs.joinAll()

            val currentJob = originalJobs[asset.id]
            if (currentJob?.isActive == true) return@launchWork
            currentJob?.join()
            if (mutableState.value.selectedAssetId == asset.id) ensureOriginal(asset)
        }
    }

    fun closeSelected() {
        selectionJob?.cancel()
        selectionJob = null
        originalJobs.values.toSet().forEach(Job::cancel)
        mutableState.update { it.copy(selectedAssetId = null) }
    }

    fun retryOriginal(asset: PhotoSummary) = ensureOriginal(asset, retry = true)

    private fun ensureOriginal(asset: PhotoSummary, retry: Boolean = false) {
        if (
            asset.originalSize == null ||
            asset.mediaKind == PhotoMediaKind.Unknown ||
            originalJobs[asset.id]?.isActive == true
        ) return
        val existing = mutableState.value.originalTransfers[asset.id]
        if (existing.isValidatedCacheFile()) return
        if (!retry && existing?.state !in listOf(
                null,
                PhotoTransferState.Queued,
                PhotoTransferState.Succeeded,
            )
        ) return
        lateinit var job: Job
        job = workSession.createJob(viewModelScope) {
            try {
                val completed = originalSlots.withPermit {
                    coordinator.downloadOriginal(asset) { transfer ->
                        updateOriginal(asset.id, transfer)
                    }
                }
                if (completed.state == PhotoTransferState.Succeeded) validatedDownloads += completed.id
                updateOriginal(asset.id, completed)
            } finally {
                originalJobs.remove(asset.id, job)
            }
        } ?: return
        originalJobs[asset.id] = job
        job.start()
    }

    private fun PhotoTransfer?.isValidatedCacheFile(): Boolean {
        if (this == null || state != PhotoTransferState.Succeeded || id !in validatedDownloads) return false
        return File(localPath).let { it.isFile && it.canRead() && it.length() > 0 }
    }

    private fun updatePreview(assetId: String, transfer: PhotoTransfer) {
        mutableState.update { state ->
            state.copy(previewTransfers = state.previewTransfers + (assetId to transfer))
        }
    }

    private fun updateOriginal(assetId: String, transfer: PhotoTransfer) {
        mutableState.update { state ->
            state.copy(originalTransfers = state.originalTransfers + (assetId to transfer))
        }
    }

    fun planUploads(uris: List<Uri>) {
        if (uris.isEmpty() || mutableState.value.planningUpload) return
        launchWork { stageUris(uris, "selection") }
    }

    fun addFolder(uri: Uri) {
        launchWork {
            try {
                val sources = folders.add(uri)
                mutableState.update { it.copy(folderSources = sources) }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Throwable) {
                mutableState.update {
                    it.copy(uploadError = error.message ?: "Could not keep folder access")
                }
            }
        }
    }

    fun removeFolder(source: PhotoFolderSource) {
        launchWork {
            val sources = folders.remove(source.uri)
            mutableState.update { it.copy(folderSources = sources, sourceMessage = null) }
        }
    }

    fun scanFolder(source: PhotoFolderSource) {
        if (mutableState.value.planningUpload) return
        launchWork {
            try {
                val uris = folders.photos(source)
                if (uris.isEmpty()) {
                    mutableState.update {
                        it.copy(sourceMessage = "No supported photos found in ${source.displayName}")
                    }
                } else {
                    stageUris(uris, source.displayName)
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Throwable) {
                mutableState.update {
                    it.copy(uploadError = error.message ?: "Could not scan the selected folder")
                }
            }
        }
    }

    private suspend fun stageUris(uris: List<Uri>, sourceName: String) {
        mutableState.update {
            it.copy(
                planningUpload = true,
                planningDone = 0,
                planningTotal = uris.size,
                uploadError = null,
                sourceMessage = null,
            )
        }
        var staged = 0
        var lastError: String? = null
        uris.forEachIndexed { index, uri ->
            var candidate: PickedPhotoUpload? = null
            try {
                candidate = prepareUpload(uri)
                val transfer = coordinator.planUpload(
                    sourcePath = candidate.file.absolutePath,
                    previewPath = candidate.previewFile.absolutePath,
                    filename = candidate.filename,
                    mimeType = candidate.mimeType,
                    orientation = candidate.orientation,
                    capturedAtMs = candidate.capturedAtMs,
                )
                staged += 1
                mutableState.update { state ->
                    state.copy(
                        uploadPlans = (listOf(transfer) + state.uploadPlans)
                            .distinctBy(PhotoTransfer::id),
                    )
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Throwable) {
                lastError = error.message ?: "A photo could not be staged"
            } finally {
                candidate?.file?.delete()
                candidate?.previewFile?.delete()
                mutableState.update { it.copy(planningDone = index + 1) }
            }
        }
        mutableState.update {
            it.copy(
                planningUpload = false,
                uploadError = lastError,
                sourceMessage = "Staged $staged of ${uris.size} photos from $sourceName",
            )
        }
    }

    fun upload(transfer: PhotoTransfer) {
        if (transfer.state !in listOf(PhotoTransferState.Queued, PhotoTransferState.Failed)) return
        launchWork {
            val completed = runUpload(transfer)
            if (completed.state == PhotoTransferState.Succeeded) refreshRemote()
        }
    }

    fun uploadAll() {
        val pending = mutableState.value.uploadPlans.filter {
            it.state in listOf(PhotoTransferState.Queued, PhotoTransferState.Failed)
        }
        if (pending.isEmpty()) return
        launchWork {
            var uploaded = false
            pending.forEach { uploaded = runUpload(it).state == PhotoTransferState.Succeeded || uploaded }
            if (uploaded) refreshRemote()
        }
    }

    private suspend fun runUpload(transfer: PhotoTransfer): PhotoTransfer =
        coordinator.upload(transfer) { updated ->
            mutableState.update { state ->
                state.copy(
                    uploadPlans = state.uploadPlans.map { if (it.id == updated.id) updated else it },
                )
            }
        }

    fun clearUploadError() {
        mutableState.update { it.copy(uploadError = null) }
    }

    override fun onCleared() {
        workSession.close()
        super.onCleared()
    }

    private fun launchWork(block: suspend () -> Unit): Job? =
        workSession.createJob(viewModelScope) { block() }?.also { it.start() }

    companion object {
        fun factory(): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val application = checkNotNull(this[APPLICATION_KEY])
                PhotosBackgroundSync.keepDisabled(application)
                if (PushStateHolder.state != null) PhotosWorkRegistry.activate()
                val port = LivePhotosPort { PushStateHolder.state }
                val catalog = PhotosSqliteCatalog(application)
                PhotosViewModel(
                    browser = PhotosBrowser(port),
                    catalog = catalog,
                    coordinator = PhotoTransferCoordinator(
                        port = port,
                        catalog = catalog,
                        previewRoot = File(application.filesDir, "icloud_photos/previews"),
                        uploadRoot = File(application.filesDir, "icloud_photos/uploads"),
                        originalRoot = File(application.filesDir, "icloud_photos/originals"),
                    ),
                    folders = AndroidPhotosFolderPort(PhotoFolderSources(application)),
                    prepareUpload = { uri -> preparePhotoUploadCandidate(application, uri) },
                )
            }
        }
    }
}

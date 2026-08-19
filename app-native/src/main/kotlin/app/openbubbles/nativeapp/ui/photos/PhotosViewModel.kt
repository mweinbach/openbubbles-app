package app.openbubbles.nativeapp.ui.photos

import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import app.openbubbles.core.photos.PhotosAccess
import app.openbubbles.core.photos.PhotosAvailability
import app.openbubbles.core.photos.PhotosBrowser
import app.openbubbles.core.photos.PhotosCatalog
import app.openbubbles.core.photos.PhotosPort
import app.openbubbles.core.photos.PhotosSnapshot
import app.openbubbles.core.photos.PhotoSummary
import app.openbubbles.core.photos.PhotoTransfer
import app.openbubbles.core.photos.PhotoTransferCoordinator
import app.openbubbles.core.photos.PhotoTransferDirection
import app.openbubbles.core.photos.PhotoTransferState
import app.openbubbles.core.photos.UniffiPhotosPort
import app.openbubbles.nativeapp.data.PushStateHolder
import app.openbubbles.nativeapp.data.photos.PhotosSqliteCatalog
import java.io.File
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import uniffi.rust_lib_bluebubbles.NativePushState

data class PhotosUiState(
    val loading: Boolean = true,
    val refreshing: Boolean = false,
    val loadingMore: Boolean = false,
    val snapshot: PhotosSnapshot? = null,
    val transfers: Map<String, PhotoTransfer> = emptyMap(),
    val showingCachedMetadata: Boolean = false,
    val error: String? = null,
)

private class LivePhotosPort(
    private val stateProvider: () -> NativePushState?,
) : PhotosPort {
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
}

class PhotosViewModel(
    private val browser: PhotosBrowser,
    private val catalog: PhotosCatalog,
    private val transfers: PhotoTransferCoordinator,
) : ViewModel() {
    private val mutableState = MutableStateFlow(PhotosUiState())
    val uiState: StateFlow<PhotosUiState> = mutableState.asStateFlow()
    private var requestJob: Job? = null

    init {
        requestJob = viewModelScope.launch { bootstrap() }
    }

    fun refresh() {
        if (requestJob?.isActive == true) return
        requestJob = viewModelScope.launch { refreshRemote() }
    }

    private suspend fun bootstrap() {
        catalog.recoverInterruptedTransfers()
        val cached = catalog.loadMetadata()
        val restoredTransfers = catalog.transfers()
            .filter { it.direction == PhotoTransferDirection.Download && it.assetId != null }
            .associateBy { checkNotNull(it.assetId) }
        if (cached.assets.isNotEmpty()) {
            mutableState.update {
                it.copy(
                    loading = false,
                    snapshot = PhotosSnapshot(
                        access = PhotosAccess(
                            PhotosAvailability.Unavailable,
                            "Showing metadata saved on this device",
                        ),
                        assets = cached.assets,
                        nextCursor = cached.nextCursor,
                    ),
                    transfers = restoredTransfers,
                    showingCachedMetadata = true,
                )
            }
        } else if (restoredTransfers.isNotEmpty()) {
            mutableState.update { it.copy(transfers = restoredTransfers) }
        }
        refreshRemote()
    }

    private suspend fun refreshRemote() {
        mutableState.update {
            it.copy(
                loading = it.snapshot == null,
                refreshing = it.snapshot != null,
                error = null,
            )
        }
        try {
            val snapshot = browser.initial()
            if (snapshot.access.availability == PhotosAvailability.Ready) {
                catalog.replaceMetadata(snapshot.assets, snapshot.nextCursor)
            }
            mutableState.update {
                it.copy(
                    snapshot = snapshot,
                    showingCachedMetadata = false,
                )
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Throwable) {
            mutableState.update {
                val hasCache = !it.snapshot?.assets.isNullOrEmpty()
                it.copy(
                    snapshot = it.snapshot ?: PhotosSnapshot(
                        access = PhotosAccess(
                            PhotosAvailability.Unavailable,
                            "Personal iCloud Photos could not be reached",
                        ),
                    ),
                    showingCachedMetadata = hasCache,
                    error = if (hasCache) null else error.message ?: "iCloud Photos probe failed",
                )
            }
        } finally {
            mutableState.update { it.copy(loading = false, refreshing = false) }
        }
    }

    fun loadMore() {
        val snapshot = mutableState.value.snapshot ?: return
        if (snapshot.nextCursor == null || requestJob?.isActive == true) return
        requestJob = viewModelScope.launch {
            mutableState.update { it.copy(loadingMore = true, error = null) }
            try {
                val next = browser.next(snapshot)
                catalog.replaceMetadata(next.assets, next.nextCursor)
                mutableState.update {
                    it.copy(snapshot = next, showingCachedMetadata = false)
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Throwable) {
                mutableState.update { it.copy(error = error.message ?: "Could not load more Photos metadata") }
            } finally {
                mutableState.update { it.copy(loadingMore = false) }
            }
        }
    }

    fun downloadPreview(asset: PhotoSummary) {
        val existing = mutableState.value.transfers[asset.id]
        if (existing?.state == PhotoTransferState.Running) return
        viewModelScope.launch {
            transfers.downloadPreview(asset) { transfer ->
                mutableState.update { state ->
                    state.copy(transfers = state.transfers + (asset.id to transfer))
                }
            }
        }
    }

    companion object {
        fun factory(): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val application = checkNotNull(this[APPLICATION_KEY])
                val port = LivePhotosPort { PushStateHolder.state }
                val catalog = PhotosSqliteCatalog(application)
                PhotosViewModel(
                    browser = PhotosBrowser(port),
                    catalog = catalog,
                    transfers = PhotoTransferCoordinator(
                        port = port,
                        catalog = catalog,
                        previewRoot = File(application.filesDir, "icloud_photos/previews"),
                    ),
                )
            }
        }
    }
}

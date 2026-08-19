package app.openbubbles.nativeapp.ui.sharedalbums

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import app.openbubbles.nativeapp.data.PushStateHolder
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.withContext
import uniffi.rust_lib_bluebubbles.NativePushState

data class SharedAlbumUi(
    val id: String,
    val name: String,
    val owner: String?,
    val location: String?,
    val assetCount: Int,
    val invitation: Boolean,
    val syncing: Boolean,
    val syncStatus: String?,
)

data class SharedAlbumAssetUi(val id: String, val filename: String)

data class SharedAlbumsUiState(
    val loading: Boolean = true,
    val refreshing: Boolean = false,
    val albums: List<SharedAlbumUi> = emptyList(),
    val selected: SharedAlbumUi? = null,
    val assets: List<SharedAlbumAssetUi> = emptyList(),
    val assetsLoading: Boolean = false,
    val busy: Boolean = false,
    val error: String? = null,
)

internal fun filterSharedAlbums(albums: List<SharedAlbumUi>, query: String): List<SharedAlbumUi> {
    val needle = query.trim().lowercase()
    return if (needle.isEmpty()) albums else albums.filter {
        it.name.lowercase().contains(needle) || it.owner.orEmpty().lowercase().contains(needle)
    }
}

interface SharedAlbumsPort {
    suspend fun list(refresh: Boolean): List<SharedAlbumUi>
    suspend fun syncNow()
    suspend fun accept(albumId: String)
    suspend fun acceptToken(token: String)
    suspend fun setSync(albumId: String, folder: String?)
    suspend fun assets(albumId: String): List<SharedAlbumAssetUi>
}

class RustSharedAlbumsPort(private val stateProvider: () -> NativePushState?) : SharedAlbumsPort {
    private fun state(): NativePushState = stateProvider() ?: error("Apple services are not connected")

    override suspend fun list(refresh: Boolean): List<SharedAlbumUi> = withContext(Dispatchers.IO) {
        state().listSharedAlbums(refresh).map { album ->
            SharedAlbumUi(
                id = album.id,
                name = album.name,
                owner = album.ownerName ?: album.ownerEmail,
                location = album.location,
                assetCount = album.assetCount.toInt(),
                invitation = album.invitation,
                syncing = album.syncing,
                syncStatus = album.syncStatus,
            )
        }
    }

    override suspend fun syncNow() = withContext(Dispatchers.IO) { state().syncSharedAlbums() }
    override suspend fun accept(albumId: String) = withContext(Dispatchers.IO) { state().acceptSharedAlbum(albumId) }
    override suspend fun acceptToken(token: String) = withContext(Dispatchers.IO) { state().acceptSharedAlbumToken(token) }
    override suspend fun setSync(albumId: String, folder: String?) = withContext(Dispatchers.IO) {
        state().setSharedAlbumSync(albumId, folder)
    }
    override suspend fun assets(albumId: String): List<SharedAlbumAssetUi> = withContext(Dispatchers.IO) {
        state().listSharedAlbumAssets(albumId).map { SharedAlbumAssetUi(it.id, it.filename) }
    }
}

class FakeSharedAlbumsPort(var albums: List<SharedAlbumUi> = emptyList()) : SharedAlbumsPort {
    override suspend fun list(refresh: Boolean) = albums
    override suspend fun syncNow() = Unit
    override suspend fun accept(albumId: String) {
        albums = albums.map { if (it.id == albumId) it.copy(invitation = false) else it }
    }
    override suspend fun acceptToken(token: String) = Unit
    override suspend fun setSync(albumId: String, folder: String?) {
        albums = albums.map { if (it.id == albumId) it.copy(syncing = folder != null, location = folder) else it }
    }
    override suspend fun assets(albumId: String) = listOf(SharedAlbumAssetUi("asset", "photo.jpg"))
}

class SharedAlbumsViewModel(private val port: SharedAlbumsPort) : ViewModel() {
    private val mutableState = MutableStateFlow(SharedAlbumsUiState())
    val uiState: StateFlow<SharedAlbumsUiState> = mutableState.asStateFlow()

    private val portMutex = Mutex()
    private val activeOperations = mutableMapOf<Long, OperationKind>()
    private var nextOperationId = 0L
    private var latestAlbumsGeneration = 0L
    private var selectionGeneration = 0L
    private var selectionJob: Job? = null

    init { refresh(false) }

    fun refresh(remote: Boolean = true) {
        if (activeOperations.values.any { it.refreshing }) return
        loadAlbums(
            kind = if (remote) OperationKind.Refresh else OperationKind.InitialLoad,
        ) {
            port.list(remote)
        }
    }

    fun syncNow() {
        if (activeOperations.values.any { it.refreshing || it.busy }) return
        loadAlbums(kind = OperationKind.Refresh) {
            port.syncNow()
            port.list(false)
        }
    }

    fun select(album: SharedAlbumUi?) {
        if (activeOperations.values.any { it == OperationKind.Action }) return
        val generation = ++selectionGeneration
        selectionJob?.cancel()
        mutableState.update { it.copy(selected = album, assets = emptyList()) }

        if (album == null || album.invitation) {
            return
        }

        selectionJob = startOperation(OperationKind.Selection) {
            val assets = serialized { port.assets(album.id) }
            if (generation == selectionGeneration) {
                mutableState.update { current ->
                    if (current.selected?.id == album.id) current.copy(assets = assets) else current
                }
            }
        }
    }

    fun accept(albumId: String) {
        if (activeOperations.values.any { it.busy }) return
        loadAlbums(kind = OperationKind.Action, clearSelection = true) {
            port.accept(albumId)
            port.list(true)
        }
    }

    fun acceptToken(token: String) {
        if (activeOperations.values.any { it.busy }) return
        loadAlbums(kind = OperationKind.Action) {
            port.acceptToken(token.trim())
            port.list(true)
        }
    }

    fun setSync(album: SharedAlbumUi, folder: String?) {
        if (activeOperations.values.any { it.busy }) return
        loadAlbums(kind = OperationKind.Action, clearSelection = true) {
            port.setSync(album.id, folder)
            port.list(false)
        }
    }

    fun clearError() {
        mutableState.update { it.copy(error = null) }
    }

    private fun loadAlbums(
        kind: OperationKind,
        clearSelection: Boolean = false,
        load: suspend () -> List<SharedAlbumUi>,
    ) {
        val albumsGeneration = ++latestAlbumsGeneration
        val selectionAtStart = selectionGeneration
        startOperation(kind) {
            val albums = serialized(load)
            if (albumsGeneration == latestAlbumsGeneration) {
                mutableState.update { current ->
                    val mayClearSelection = clearSelection && selectionAtStart == selectionGeneration
                    current.copy(
                        albums = albums,
                        selected = if (mayClearSelection) null else current.selected,
                        assets = if (mayClearSelection) emptyList() else current.assets,
                    )
                }
                updateOperationFlags()
            }
        }
    }

    private fun startOperation(
        kind: OperationKind,
        operation: suspend () -> Unit,
    ): Job {
        val operationId = ++nextOperationId
        activeOperations[operationId] = kind
        mutableState.update { it.copy(error = null) }
        updateOperationFlags()

        val job = viewModelScope.launch {
            try {
                operation()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Throwable) {
                mutableState.update { it.copy(error = error.message ?: "Shared Albums failed") }
            }
        }
        job.invokeOnCompletion {
            activeOperations.remove(operationId)
            updateOperationFlags()
        }
        return job
    }

    private suspend fun <T> serialized(operation: suspend () -> T): T {
        portMutex.lock()
        return try {
            operation()
        } finally {
            portMutex.unlock()
        }
    }

    private fun updateOperationFlags() {
        val operations = activeOperations.values
        mutableState.update { current ->
            current.copy(
                loading = current.albums.isEmpty() && operations.any { it.loadsAlbums },
                refreshing = operations.any { it.refreshing },
                assetsLoading = operations.any { it == OperationKind.Selection },
                busy = operations.any { it.busy },
            )
        }
    }

    private enum class OperationKind(
        val loadsAlbums: Boolean,
        val refreshing: Boolean = false,
        val busy: Boolean = false,
    ) {
        InitialLoad(loadsAlbums = true),
        Refresh(loadsAlbums = true, refreshing = true),
        Action(loadsAlbums = true, busy = true),
        Selection(loadsAlbums = false, busy = true),
    }

    companion object {
        fun factory(): ViewModelProvider.Factory = factory(RustSharedAlbumsPort { PushStateHolder.state })
        fun factory(port: SharedAlbumsPort): ViewModelProvider.Factory = viewModelFactory {
            initializer { SharedAlbumsViewModel(port) }
        }
    }
}

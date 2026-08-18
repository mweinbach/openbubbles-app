package app.openbubbles.nativeapp.ui.sharedalbums

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import app.openbubbles.nativeapp.data.PushStateHolder
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
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

    init { refresh(false) }

    fun refresh(remote: Boolean = true) = runAction(refreshing = remote) {
        mutableState.update { it.copy(albums = port.list(remote)) }
    }

    fun syncNow() = runAction(refreshing = true) {
        port.syncNow()
        mutableState.update { it.copy(albums = port.list(false)) }
    }

    fun select(album: SharedAlbumUi?) = runAction {
        mutableState.update { it.copy(selected = album, assets = emptyList()) }
        if (album != null && !album.invitation) {
            mutableState.update { it.copy(assets = port.assets(album.id)) }
        }
    }

    fun accept(albumId: String) = runAction {
        port.accept(albumId)
        mutableState.update { it.copy(albums = port.list(true), selected = null) }
    }

    fun acceptToken(token: String) = runAction {
        port.acceptToken(token.trim())
        mutableState.update { it.copy(albums = port.list(true)) }
    }

    fun setSync(album: SharedAlbumUi, folder: String?) = runAction {
        port.setSync(album.id, folder)
        mutableState.update { it.copy(albums = port.list(false), selected = null) }
    }

    fun clearError() = mutableState.update { it.copy(error = null) }

    private fun runAction(refreshing: Boolean = false, action: suspend () -> Unit) {
        viewModelScope.launch {
            mutableState.update { it.copy(loading = if (it.albums.isEmpty()) true else it.loading, refreshing = refreshing, busy = !refreshing, error = null) }
            try {
                action()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Throwable) {
                mutableState.update { it.copy(error = error.message ?: "Shared Albums failed") }
            } finally {
                mutableState.update { it.copy(loading = false, refreshing = false, busy = false) }
            }
        }
    }

    companion object {
        fun factory(): ViewModelProvider.Factory = factory(RustSharedAlbumsPort { PushStateHolder.state })
        fun factory(port: SharedAlbumsPort): ViewModelProvider.Factory = viewModelFactory {
            initializer { SharedAlbumsViewModel(port) }
        }
    }
}

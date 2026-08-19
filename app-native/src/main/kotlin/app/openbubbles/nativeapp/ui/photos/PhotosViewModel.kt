package app.openbubbles.nativeapp.ui.photos

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import app.openbubbles.core.photos.PhotosAccess
import app.openbubbles.core.photos.PhotosAvailability
import app.openbubbles.core.photos.PhotosBrowser
import app.openbubbles.core.photos.PhotosPort
import app.openbubbles.core.photos.PhotosSnapshot
import app.openbubbles.core.photos.UniffiPhotosPort
import app.openbubbles.nativeapp.data.PushStateHolder
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
}

class PhotosViewModel(
    private val browser: PhotosBrowser,
) : ViewModel() {
    private val mutableState = MutableStateFlow(PhotosUiState())
    val uiState: StateFlow<PhotosUiState> = mutableState.asStateFlow()
    private var requestJob: Job? = null

    init {
        refresh()
    }

    fun refresh() {
        if (requestJob?.isActive == true) return
        requestJob = viewModelScope.launch {
            mutableState.update {
                it.copy(
                    loading = it.snapshot == null,
                    refreshing = it.snapshot != null,
                    error = null,
                )
            }
            try {
                mutableState.update { it.copy(snapshot = browser.initial()) }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Throwable) {
                mutableState.update {
                    it.copy(
                        snapshot = it.snapshot ?: PhotosSnapshot(
                            access = PhotosAccess(
                                PhotosAvailability.Unavailable,
                                "Personal iCloud Photos could not be reached",
                            ),
                        ),
                        error = error.message ?: "iCloud Photos probe failed",
                    )
                }
            } finally {
                mutableState.update { it.copy(loading = false, refreshing = false) }
            }
        }
    }

    fun loadMore() {
        val snapshot = mutableState.value.snapshot ?: return
        if (snapshot.nextCursor == null || requestJob?.isActive == true) return
        requestJob = viewModelScope.launch {
            mutableState.update { it.copy(loadingMore = true, error = null) }
            try {
                mutableState.update { it.copy(snapshot = browser.next(snapshot)) }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Throwable) {
                mutableState.update { it.copy(error = error.message ?: "Could not load more Photos metadata") }
            } finally {
                mutableState.update { it.copy(loadingMore = false) }
            }
        }
    }

    companion object {
        fun factory(): ViewModelProvider.Factory = factory(
            LivePhotosPort { PushStateHolder.state },
        )

        fun factory(port: PhotosPort): ViewModelProvider.Factory = viewModelFactory {
            initializer { PhotosViewModel(PhotosBrowser(port)) }
        }
    }
}

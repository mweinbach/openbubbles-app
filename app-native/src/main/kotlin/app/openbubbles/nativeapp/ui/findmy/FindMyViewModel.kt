package app.openbubbles.nativeapp.ui.findmy

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import app.openbubbles.nativeapp.data.PushStateHolder
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * FindMy screen state. [devices]/[friends]/[items] carry the last data that
 * loaded successfully, so the screen keeps working offline after a failed
 * refresh ([refreshErrors] then explains what happened).
 */
data class FindMyUiState(
    /** True until the first cached read has run. */
    val loading: Boolean = true,
    /** True while a user-triggered refresh is in flight. */
    val refreshing: Boolean = false,
    /** True when no live push state is installed (sign-in empty state). */
    val unavailable: Boolean = false,
    val devices: List<FmDeviceUi> = emptyList(),
    val friends: List<FmFriendUi> = emptyList(),
    val items: List<FmItemUi> = emptyList(),
    /** Human-readable failures from the most recent refresh, if any. */
    val refreshErrors: List<String> = emptyList(),
) {
    val isEmpty: Boolean
        get() = !loading && devices.isEmpty() && friends.isEmpty() && items.isEmpty()
}

class FindMyViewModel(
    private val port: FindMyPort,
) : ViewModel() {

    private val _uiState = MutableStateFlow(FindMyUiState())
    val uiState: StateFlow<FindMyUiState> = _uiState.asStateFlow()

    init {
        // Cached read first (works offline from the last persisted data),
        // then one best-effort refresh.
        load { refresh() }
    }

    /** Re-reads the cached lists without touching the network. */
    fun reload() = load {}

    /** Pull-to-refresh button: refreshes all three sections in parallel. */
    fun refresh() {
        if (_uiState.value.refreshing) return
        _uiState.update { it.copy(refreshing = true) }
        viewModelScope.launch {
            val devices = runCatching { port.refreshDevices() }
            val friends = runCatching { port.refreshFriends() }
            val items = runCatching { port.refreshItems() }
            _uiState.update { state ->
                FindMyUiState(
                    loading = false,
                    refreshing = false,
                    unavailable = state.unavailable,
                    // Keep the previous list when a section failed (offline).
                    devices = devices.getOrDefault(state.devices),
                    friends = friends.getOrDefault(state.friends),
                    items = items.getOrDefault(state.items),
                    refreshErrors = listOfNotNull(
                        devices.exceptionOrNull()?.let { "Devices: ${it.message ?: "failed"}" },
                        friends.exceptionOrNull()?.let { "Friends: ${it.message ?: "failed"}" },
                        items.exceptionOrNull()?.let { "Items: ${it.message ?: "failed"}" },
                    ),
                )
            }
        }
    }

    private fun load(then: () -> Unit) {
        viewModelScope.launch {
            if (!port.isAvailable()) {
                _uiState.update { FindMyUiState(loading = false, unavailable = true) }
                return@launch
            }
            val devices = runCatching { port.devices() }.getOrDefault(emptyList())
            val friends = runCatching { port.friends() }.getOrDefault(emptyList())
            val items = runCatching { port.items() }.getOrDefault(emptyList())
            _uiState.update { state ->
                state.copy(
                    loading = false,
                    unavailable = false,
                    devices = devices,
                    friends = friends,
                    items = items,
                )
            }
            then()
        }
    }

    companion object {
        /** Production factory: bridges the live Rust push state reflectively. */
        fun factory(): ViewModelProvider.Factory = factory(RustFindMyPort { PushStateHolder.state })

        /** Injection factory (previews/tests use [FakeFindMyPort]). */
        fun factory(port: FindMyPort): ViewModelProvider.Factory = viewModelFactory {
            initializer { FindMyViewModel(port) }
        }
    }
}

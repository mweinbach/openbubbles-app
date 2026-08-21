package app.openbubbles.nativeapp.ui.findmy

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import app.openbubbles.nativeapp.data.PushStateHolder
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** How often live tracking re-asks Apple while the screen is in front of the user. */
const val FM_LIVE_INTERVAL_MS: Long = 30_000L

/**
 * FindMy screen state. [devices]/[friends]/[items] carry the last data that
 * loaded successfully, so the screen keeps working offline after a failed
 * refresh ([refreshErrors] then explains what happened).
 *
 * [tracking] is the flattened view the map and list share, and it is derived
 * from the three lists rather than stored twice.
 */
data class FindMyUiState(
    /** True until the first cached read has run. */
    val loading: Boolean = true,
    /** True while a refresh is in flight, whether user-triggered or live. */
    val refreshing: Boolean = false,
    /** True when no live push state is installed (sign-in empty state). */
    val unavailable: Boolean = false,
    val devices: List<FmDeviceUi> = emptyList(),
    val friends: List<FmFriendUi> = emptyList(),
    val items: List<FmItemUi> = emptyList(),
    /** Human-readable failures from the most recent refresh, if any. */
    val refreshErrors: List<String> = emptyList(),
    /** Recent fixes per target for this session, drawn as map tracks. */
    val trails: Map<String, List<FmPoint>> = emptyMap(),
    /** The target whose detail card is open and which the camera may follow. */
    val selectedTargetId: String? = null,
    /** Repeat the refresh on a timer while the screen is visible. */
    val liveUpdates: Boolean = true,
    /** When the last refresh finished, successful or not. */
    val lastUpdatedAtMs: Long? = null,
) {
    val targets: List<FmTarget> = findMyTargets(devices, friends, items)

    val selectedTarget: FmTarget?
        get() = selectedTargetId?.let { id -> targets.firstOrNull { it.id == id } }

    val locatedTargets: List<FmTarget> get() = targets.filter(FmTarget::located)

    val isEmpty: Boolean
        get() = !loading && targets.isEmpty()

    fun trail(targetId: String): List<FmPoint> = trails[targetId].orEmpty()
}

class FindMyViewModel(
    private val port: FindMyPort,
    private val liveIntervalMs: Long = FM_LIVE_INTERVAL_MS,
    private val now: () -> Long = System::currentTimeMillis,
) : ViewModel() {

    private val _uiState = MutableStateFlow(FindMyUiState())
    val uiState: StateFlow<FindMyUiState> = _uiState.asStateFlow()

    private var liveJob: Job? = null
    private var visible = false

    init {
        // Cached read first (works offline from the last persisted data),
        // then one best-effort refresh.
        load { refresh() }
    }

    /** Re-reads the cached lists without touching the network. */
    fun reload() = load {}

    /**
     * Live tracking only runs while the screen is actually in front of the user.
     *
     * Tracking is a repeated read: nothing is written to the account, no
     * background worker is registered, and leaving the screen stops the timer
     * rather than leaving it polling behind a chat.
     */
    fun setVisible(isVisible: Boolean) {
        visible = isVisible
        restartLiveUpdates()
    }

    fun setLiveUpdates(enabled: Boolean) {
        if (_uiState.value.liveUpdates == enabled) return
        _uiState.update { it.copy(liveUpdates = enabled) }
        restartLiveUpdates()
    }

    fun select(targetId: String?) {
        _uiState.update { it.copy(selectedTargetId = targetId) }
    }

    /** Refreshes all three sections in parallel and records every new fix. */
    fun refresh() {
        if (_uiState.value.refreshing) return
        _uiState.update { it.copy(refreshing = true) }
        viewModelScope.launch {
            val (devices, friends, items) = coroutineScope {
                val devices = async { captureResult { port.refreshDevices() } }
                val friends = async { captureResult { port.refreshFriends() } }
                val items = async { captureResult { port.refreshItems() } }
                Triple(devices.await(), friends.await(), items.await())
            }
            _uiState.update { state ->
                // Keep the previous list when a section failed (offline).
                val nextDevices = devices.getOrDefault(state.devices)
                val nextFriends = friends.getOrDefault(state.friends)
                val nextItems = items.getOrDefault(state.items)
                state.copy(
                    loading = false,
                    refreshing = false,
                    devices = nextDevices,
                    friends = nextFriends,
                    items = nextItems,
                    trails = trailsFor(
                        previous = state.trails,
                        targets = findMyTargets(nextDevices, nextFriends, nextItems),
                    ),
                    lastUpdatedAtMs = now(),
                    refreshErrors = listOfNotNull(
                        devices.exceptionOrNull()?.let { "Devices: ${it.message ?: "failed"}" },
                        friends.exceptionOrNull()?.let { "Friends: ${it.message ?: "failed"}" },
                        items.exceptionOrNull()?.let { "Items: ${it.message ?: "failed"}" },
                    ),
                )
            }
        }
    }

    private fun restartLiveUpdates() {
        liveJob?.cancel()
        liveJob = null
        val state = _uiState.value
        if (!visible || !state.liveUpdates || state.unavailable) return
        liveJob = viewModelScope.launch {
            while (true) {
                delay(liveIntervalMs)
                refresh()
            }
        }
    }

    private fun trailsFor(
        previous: Map<String, List<FmPoint>>,
        targets: List<FmTarget>,
    ): Map<String, List<FmPoint>> {
        val next = LinkedHashMap<String, List<FmPoint>>(targets.size)
        targets.forEach { target ->
            // A target that disappeared from the account loses its track with it.
            val trail = appendTrail(previous[target.id].orEmpty(), target.point)
            if (trail.isNotEmpty()) next[target.id] = trail
        }
        return next
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
                    trails = trailsFor(
                        previous = state.trails,
                        targets = findMyTargets(devices, friends, items),
                    ),
                )
            }
            then()
        }
    }

    private suspend fun <T> captureResult(block: suspend () -> T): Result<T> = try {
        Result.success(block())
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (error: Throwable) {
        Result.failure(error)
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

package app.openbubbles.nativeapp.ui.onboarding

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import app.openbubbles.nativeapp.data.ICloudKeychainEnrollment
import app.openbubbles.nativeapp.data.PushStateHolder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import uniffi.rust_lib_bluebubbles.UViableBottle

/** Membership probes before treating a warming keychain client as a non-member. */
private const val MEMBERSHIP_CHECK_ATTEMPTS = 5

/** App-owned rendering model; generated Apple records never cross into composition. */
internal data class KeychainDeviceUi(
    val id: String,
    val displayName: String,
    val numericLength: Int?,
)

internal data class KeychainStepUiState(
    val connected: Boolean = false,
    val inClique: Boolean? = null,
    val loadingDevices: Boolean = false,
    val joining: Boolean = false,
    val devices: List<KeychainDeviceUi> = emptyList(),
    val selectedDeviceId: String? = null,
    val passcode: String = "",
    val error: String? = null,
) {
    val selectedDevice: KeychainDeviceUi?
        get() = devices.firstOrNull { it.id == selectedDeviceId }
}

internal fun isKeychainPasscodeComplete(passcode: String, requiredLength: Int?): Boolean =
    if (requiredLength != null) passcode.length == requiredLength else passcode.isNotEmpty()

/** Lifecycle owner for Apple membership, bottle discovery, and escrow recovery. */
internal class KeychainStepViewModel(application: Application) : AndroidViewModel(application) {
    private val _uiState = MutableStateFlow(KeychainStepUiState())
    val uiState: StateFlow<KeychainStepUiState> = _uiState.asStateFlow()

    private val bottles = LinkedHashMap<String, UViableBottle>()
    private var sensitiveGeneration = 0L

    init {
        viewModelScope.launch {
            PushStateHolder.stateFlow.collectLatest { live ->
                bottles.clear()
                _uiState.value = KeychainStepUiState(connected = live != null)
                if (live == null) return@collectLatest

                repeat(MEMBERSHIP_CHECK_ATTEMPTS) { attempt ->
                    val member = withContext(Dispatchers.IO) { runCatching { live.isInClique() } }
                    member.getOrNull()?.let {
                        _uiState.update { state -> state.copy(inClique = it) }
                        return@collectLatest
                    }
                    if (attempt < MEMBERSHIP_CHECK_ATTEMPTS - 1) delay(2_000)
                }
                _uiState.update { it.copy(inClique = false) }
            }
        }
    }

    fun loadDevices() {
        val live = PushStateHolder.state ?: return
        if (_uiState.value.loadingDevices) return
        val generation = ++sensitiveGeneration
        bottles.clear()
        _uiState.update {
            it.copy(
                loadingDevices = true,
                devices = emptyList(),
                selectedDeviceId = null,
                passcode = "",
                error = null,
            )
        }
        viewModelScope.launch {
            val result = ICloudKeychainEnrollment.viableBottles(live)
            if (PushStateHolder.state !== live || sensitiveGeneration != generation) return@launch
            result.onSuccess { found ->
                val models = found.mapIndexed { index, bottle ->
                    val id = "$index:${bottle.escrowData.contentHashCode()}"
                    bottles[id] = bottle
                    KeychainDeviceUi(
                        id = id,
                        displayName = buildString {
                            append(bottle.deviceName.ifBlank { "Apple device" })
                            if (bottle.modelClass.isNotBlank()) append(" · ${bottle.modelClass}")
                        },
                        numericLength = bottle.numericLength.toInt().takeIf { it > 0 },
                    )
                }
                _uiState.update {
                    it.copy(
                        loadingDevices = false,
                        devices = models,
                        selectedDeviceId = models.firstOrNull()?.id,
                        error = if (models.isEmpty()) {
                            ICloudKeychainEnrollment.noViableBottlesMessage()
                        } else {
                            null
                        },
                    )
                }
            }.onFailure { error ->
                _uiState.update {
                    it.copy(
                        loadingDevices = false,
                        error = ICloudKeychainEnrollment.escrowRecoveryFailure(error.message),
                    )
                }
            }
        }
    }

    fun selectDevice(id: String) {
        if (id !in bottles) return
        _uiState.update { it.copy(selectedDeviceId = id, passcode = "", error = null) }
    }

    fun updatePasscode(value: String) {
        val requiredLength = _uiState.value.selectedDevice?.numericLength
        val filtered = if (requiredLength != null) {
            value.filter(Char::isDigit).take(requiredLength)
        } else {
            value
        }
        _uiState.update { it.copy(passcode = filtered) }
    }

    fun join() {
        val live = PushStateHolder.state ?: return
        val state = _uiState.value
        val device = state.selectedDevice ?: return
        val bottle = bottles[device.id] ?: return
        if (state.joining || !isKeychainPasscodeComplete(state.passcode, device.numericLength)) return
        _uiState.update { it.copy(joining = true, error = null) }
        val generation = sensitiveGeneration
        viewModelScope.launch {
            val result = ICloudKeychainEnrollment.joinWithBottle(
                context = getApplication(),
                state = live,
                bottle = bottle,
                passcode = state.passcode,
            )
            if (PushStateHolder.state !== live || sensitiveGeneration != generation) return@launch
            result.onSuccess {
                _uiState.update { it.copy(inClique = true, joining = false, passcode = "") }
            }.onFailure { error ->
                _uiState.update {
                    it.copy(
                        joining = false,
                        passcode = "",
                        error = error.message ?: "Unable to unlock your iCloud data",
                    )
                }
            }
        }
    }

    /** Drops passcodes and escrow records when onboarding leaves this activity-owned ViewModel. */
    fun clearSensitiveState() {
        sensitiveGeneration += 1
        bottles.clear()
        _uiState.update {
            it.copy(
                loadingDevices = false,
                joining = false,
                devices = emptyList(),
                selectedDeviceId = null,
                passcode = "",
                error = null,
            )
        }
    }
}

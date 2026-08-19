package app.openbubbles.nativeapp.ui.passwords

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import app.openbubbles.nativeapp.data.PushStateHolder
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class VaultGroupDetailUiState(
    val groupId: String,
    /** Last known name; the loaded group supersedes it. */
    val name: String,
    val group: VaultGroupUi? = null,
    val loading: Boolean = true,
    val busy: Boolean = false,
    val error: String? = null,
    /** The group was deleted or left; the page should pop. */
    val closed: Boolean = false,
)

/**
 * One shared-password group opened as its own page. Membership is re-fetched
 * after every write so the page always shows the server's roster, and every
 * mutation bumps [VaultEditBus] so the list behind it stays honest.
 */
class VaultGroupDetailViewModel(
    private val port: PasswordsPort,
    groupId: String,
    initialName: String,
) : ViewModel() {
    private val mutableState = MutableStateFlow(
        VaultGroupDetailUiState(groupId = groupId, name = initialName),
    )
    val uiState: StateFlow<VaultGroupDetailUiState> = mutableState.asStateFlow()

    init {
        viewModelScope.launch {
            try {
                reloadGroup()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Throwable) {
                mutableState.update {
                    it.copy(loading = false, error = error.message ?: "iCloud Passwords failed")
                }
            }
        }
    }

    fun rename(name: String) = runAction {
        port.renameGroup(mutableState.value.groupId, name.trim())
        VaultEditBus.notifyChanged()
        reloadGroup()
    }

    fun inviteMember(handle: String) = runAction {
        port.inviteGroupMember(mutableState.value.groupId, handle.trim())
        VaultEditBus.notifyChanged()
        reloadGroup()
    }

    fun removeMember(handle: String) = runAction {
        port.removeGroupMember(mutableState.value.groupId, handle)
        VaultEditBus.notifyChanged()
        reloadGroup()
    }

    /** Owners delete the group; members leave it. Same server call. */
    fun deleteOrLeave() = runAction {
        port.deleteGroup(mutableState.value.groupId)
        VaultEditBus.notifyChanged()
        mutableState.update { it.copy(closed = true) }
    }

    private suspend fun reloadGroup() {
        val group = port.listGroups().firstOrNull { it.id == mutableState.value.groupId }
        mutableState.update {
            it.copy(
                group = group,
                name = group?.name ?: it.name,
                loading = false,
            )
        }
    }

    private fun runAction(action: suspend () -> Unit) {
        if (mutableState.value.busy) return
        viewModelScope.launch {
            mutableState.update { it.copy(busy = true, error = null) }
            try {
                action()
                mutableState.update { it.copy(busy = false) }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Throwable) {
                mutableState.update {
                    it.copy(busy = false, error = error.message ?: "iCloud Passwords failed")
                }
            }
        }
    }

    companion object {
        fun factory(groupId: String, name: String): ViewModelProvider.Factory =
            factory(RustPasswordsPort { PushStateHolder.state }, groupId, name)
        fun factory(port: PasswordsPort, groupId: String, name: String): ViewModelProvider.Factory =
            viewModelFactory {
                initializer { VaultGroupDetailViewModel(port, groupId, name) }
            }
    }
}

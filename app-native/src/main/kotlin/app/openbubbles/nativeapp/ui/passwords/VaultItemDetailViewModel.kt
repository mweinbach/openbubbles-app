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

data class VaultItemDetailUiState(
    val item: VaultItemUi,
    val secret: String? = null,
    val secretExpiresAtSeconds: Long? = null,
    /** Resolved lazily from the group id; null means unresolved or personal. */
    val groupName: String? = null,
    val busy: Boolean = false,
    val error: String? = null,
    /** The item was deleted from iCloud; the page should pop. */
    val deleted: Boolean = false,
)

/**
 * One vault item opened as its own page. The secret is never fetched with the
 * list; it stays server-side until [reveal] runs behind the caller's user
 * authentication, and for verification codes the page re-reveals on expiry.
 */
class VaultItemDetailViewModel(
    private val port: PasswordsPort,
    item: VaultItemUi,
) : ViewModel() {
    private val mutableState = MutableStateFlow(VaultItemDetailUiState(item = item))
    val uiState: StateFlow<VaultItemDetailUiState> = mutableState.asStateFlow()

    init {
        val groupId = item.groupId
        if (groupId != null) {
            viewModelScope.launch {
                // Best-effort label; the row falls back to "Shared" on failure.
                val name = try {
                    port.listGroups().firstOrNull { it.id == groupId }?.name
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Throwable) {
                    null
                }
                if (name != null) mutableState.update { it.copy(groupName = name) }
            }
        }
    }

    fun reveal() {
        val state = mutableState.value
        if (state.busy || state.item.category == VaultCategory.Passkeys) return
        runAction {
            val (secret, expiry) = port.reveal(state.item)
            mutableState.update {
                it.copy(secret = secret, secretExpiresAtSeconds = expiry)
            }
        }
    }

    fun delete() {
        val item = mutableState.value.item
        runAction {
            port.deleteItem(item)
            VaultEditBus.notifyChanged()
            mutableState.update { it.copy(deleted = true) }
        }
    }

    /**
     * Reports a failure that happened outside this view model — in practice a
     * cancelled or unavailable authentication prompt. Without this the reveal
     * and delete buttons simply did nothing when authentication failed.
     */
    fun reportError(message: String?) {
        mutableState.update {
            it.copy(busy = false, error = message ?: "Authentication was not completed")
        }
    }

    fun clearError() {
        mutableState.update { it.copy(error = null) }
    }

    /** Attach a TOTP generator to this password's account. */
    fun addTotp(setup: String) {
        val item = mutableState.value.item
        if (item.category != VaultCategory.Passwords) return
        runAction {
            port.addTotp(item, setup.trim())
            VaultEditBus.notifyChanged()
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
        fun factory(item: VaultItemUi): ViewModelProvider.Factory =
            factory(RustPasswordsPort { PushStateHolder.state }, item)
        fun factory(port: PasswordsPort, item: VaultItemUi): ViewModelProvider.Factory = viewModelFactory {
            initializer { VaultItemDetailViewModel(port, item) }
        }
    }
}

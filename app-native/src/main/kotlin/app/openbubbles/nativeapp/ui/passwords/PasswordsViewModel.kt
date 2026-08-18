package app.openbubbles.nativeapp.ui.passwords

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
import uniffi.rust_lib_bluebubbles.UVaultItemKind

enum class VaultCategory { Passwords, Passkeys, Codes, Wifi, Groups }

data class VaultItemUi(
    val id: String,
    val category: VaultCategory,
    val title: String,
    val username: String? = null,
    val groupId: String? = null,
)

data class VaultGroupUi(
    val id: String,
    val name: String,
    val owner: Boolean,
    val memberCount: Int,
)

data class VaultInviteUi(val id: String, val groupName: String, val inviter: String)

data class PasswordsUiState(
    val loading: Boolean = true,
    val inClique: Boolean? = null,
    val category: VaultCategory = VaultCategory.Passwords,
    val query: String = "",
    val items: List<VaultItemUi> = emptyList(),
    val groups: List<VaultGroupUi> = emptyList(),
    val invites: List<VaultInviteUi> = emptyList(),
    val selected: VaultItemUi? = null,
    val revealedSecret: String? = null,
    val secretExpiresAtSeconds: Long? = null,
    val busy: Boolean = false,
    val error: String? = null,
)

internal fun filterVaultItems(
    items: List<VaultItemUi>,
    category: VaultCategory,
    query: String,
): List<VaultItemUi> {
    val needle = query.trim().lowercase()
    return items.filter { item ->
        item.category == category &&
            (needle.isEmpty() || item.title.lowercase().contains(needle) || item.username.orEmpty().lowercase().contains(needle))
    }
}

interface PasswordsPort {
    suspend fun isInClique(): Boolean
    suspend fun listItems(): List<VaultItemUi>
    suspend fun listGroups(): List<VaultGroupUi>
    suspend fun listInvites(): List<VaultInviteUi>
    suspend fun reveal(item: VaultItemUi): Pair<String, Long?>
    suspend fun createPassword(site: String, username: String, password: String, groupId: String?)
    suspend fun createGroup(name: String)
    suspend fun acceptInvite(id: String)
}

class RustPasswordsPort(private val stateProvider: () -> NativePushState?) : PasswordsPort {
    private fun state(): NativePushState = stateProvider() ?: error("Apple services are not connected")

    override suspend fun isInClique(): Boolean = withContext(Dispatchers.IO) { state().isInClique() }

    override suspend fun listItems(): List<VaultItemUi> = withContext(Dispatchers.IO) {
        state().listPasswords().map { item ->
            VaultItemUi(
                id = item.id,
                category = when (item.kind) {
                    UVaultItemKind.PASSWORD -> VaultCategory.Passwords
                    UVaultItemKind.PASSKEY -> VaultCategory.Passkeys
                    UVaultItemKind.CODE -> VaultCategory.Codes
                    UVaultItemKind.WIFI -> VaultCategory.Wifi
                },
                title = item.title,
                username = item.username,
                groupId = item.groupId,
            )
        }
    }

    override suspend fun listGroups(): List<VaultGroupUi> = withContext(Dispatchers.IO) {
        state().listPasswordGroups().map {
            VaultGroupUi(it.id, it.name, it.owner, it.memberCount.toInt())
        }
    }

    override suspend fun listInvites(): List<VaultInviteUi> = withContext(Dispatchers.IO) {
        state().listPasswordGroupInvites().map { VaultInviteUi(it.id, it.groupName, it.inviter) }
    }

    override suspend fun reveal(item: VaultItemUi): Pair<String, Long?> = withContext(Dispatchers.IO) {
        val secret = state().revealPassword(
            item.id,
            when (item.category) {
                VaultCategory.Passwords -> UVaultItemKind.PASSWORD
                VaultCategory.Passkeys -> UVaultItemKind.PASSKEY
                VaultCategory.Codes -> UVaultItemKind.CODE
                VaultCategory.Wifi -> UVaultItemKind.WIFI
                VaultCategory.Groups -> error("Groups do not contain revealable secrets")
            },
        )
        secret.value to secret.expiresAtS?.toLong()
    }

    override suspend fun createPassword(site: String, username: String, password: String, groupId: String?) =
        withContext(Dispatchers.IO) { state().createPassword(site, username, password, groupId) }

    override suspend fun createGroup(name: String) {
        withContext(Dispatchers.IO) { state().createPasswordGroup(name) }
    }

    override suspend fun acceptInvite(id: String) =
        withContext(Dispatchers.IO) { state().acceptPasswordGroupInvite(id) }
}

class FakePasswordsPort(
    var inClique: Boolean = true,
    var items: List<VaultItemUi> = emptyList(),
    var groups: List<VaultGroupUi> = emptyList(),
    var invites: List<VaultInviteUi> = emptyList(),
    var secret: Pair<String, Long?> = "secret" to null,
) : PasswordsPort {
    override suspend fun isInClique() = inClique
    override suspend fun listItems() = items
    override suspend fun listGroups() = groups
    override suspend fun listInvites() = invites
    override suspend fun reveal(item: VaultItemUi) = secret
    override suspend fun createPassword(site: String, username: String, password: String, groupId: String?) {
        items = items + VaultItemUi("created", VaultCategory.Passwords, site, username, groupId)
    }
    override suspend fun createGroup(name: String) {
        groups = groups + VaultGroupUi("created-group", name, true, 1)
    }
    override suspend fun acceptInvite(id: String) {
        invites = invites.filterNot { it.id == id }
    }
}

class PasswordsViewModel(private val port: PasswordsPort) : ViewModel() {
    private val mutableState = MutableStateFlow(PasswordsUiState())
    val uiState: StateFlow<PasswordsUiState> = mutableState.asStateFlow()

    init { refresh() }

    fun refresh() = runAction(showLoading = true) {
        val inClique = port.isInClique()
        if (!inClique) {
            mutableState.update { it.copy(loading = false, inClique = false, items = emptyList(), groups = emptyList(), invites = emptyList()) }
            return@runAction
        }
        val items = port.listItems()
        val groups = port.listGroups()
        val invites = port.listInvites()
        mutableState.update { it.copy(loading = false, inClique = true, items = items, groups = groups, invites = invites) }
    }

    fun setCategory(category: VaultCategory) = mutableState.update {
        it.copy(category = category, selected = null, revealedSecret = null, error = null)
    }

    fun setQuery(query: String) = mutableState.update { it.copy(query = query) }

    fun select(item: VaultItemUi?) = mutableState.update {
        it.copy(selected = item, revealedSecret = null, secretExpiresAtSeconds = null, error = null)
    }

    fun revealSelected() {
        val item = mutableState.value.selected ?: return
        runAction { 
            val (secret, expiry) = port.reveal(item)
            mutableState.update { it.copy(revealedSecret = secret, secretExpiresAtSeconds = expiry) }
        }
    }

    fun createPassword(site: String, username: String, password: String, groupId: String?) = runAction {
        port.createPassword(site.trim(), username.trim(), password, groupId)
        refreshAfterWrite()
    }

    fun createGroup(name: String) = runAction {
        port.createGroup(name.trim())
        refreshAfterWrite()
    }

    fun acceptInvite(id: String) = runAction {
        port.acceptInvite(id)
        refreshAfterWrite()
    }

    fun clearError() = mutableState.update { it.copy(error = null) }

    private suspend fun refreshAfterWrite() {
        val items = port.listItems()
        val groups = port.listGroups()
        val invites = port.listInvites()
        mutableState.update { it.copy(items = items, groups = groups, invites = invites, selected = null, revealedSecret = null) }
    }

    private fun runAction(showLoading: Boolean = false, action: suspend () -> Unit) {
        viewModelScope.launch {
            mutableState.update { it.copy(loading = if (showLoading) true else it.loading, busy = !showLoading, error = null) }
            try {
                action()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Throwable) {
                mutableState.update { it.copy(loading = false, error = error.message ?: "iCloud Passwords failed") }
            } finally {
                mutableState.update { it.copy(loading = false, busy = false) }
            }
        }
    }

    companion object {
        fun factory(): ViewModelProvider.Factory = factory(RustPasswordsPort { PushStateHolder.state })
        fun factory(port: PasswordsPort): ViewModelProvider.Factory = viewModelFactory {
            initializer { PasswordsViewModel(port) }
        }
    }
}

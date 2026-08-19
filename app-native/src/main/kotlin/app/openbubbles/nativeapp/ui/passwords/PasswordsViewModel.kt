package app.openbubbles.nativeapp.ui.passwords

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
import kotlinx.coroutines.flow.drop
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
    val modifiedAtMs: Long? = null,
)

data class VaultGroupMemberUi(
    val name: String?,
    val handle: String,
    val joined: Boolean,
    val currentUser: Boolean,
)

data class VaultGroupUi(
    val id: String,
    val name: String,
    val owner: Boolean,
    val memberCount: Int,
    val members: List<VaultGroupMemberUi> = emptyList(),
)

data class VaultInviteUi(val id: String, val groupName: String, val inviter: String)

/**
 * Cross-page change signal. Item and group detail pages mutate the vault
 * (delete, TOTP add, group edits) from their own nav entries, so the list's
 * ViewModel never sees those writes; a bump tells any live [PasswordsViewModel]
 * to reload what it is currently showing.
 */
object VaultEditBus {
    private val mutableRevisions = MutableStateFlow(0)
    val revisions: StateFlow<Int> = mutableRevisions.asStateFlow()
    fun notifyChanged() {
        mutableRevisions.update { it + 1 }
    }
}

data class PasswordsUiState(
    val loading: Boolean = true,
    val inClique: Boolean? = null,
    val category: VaultCategory = VaultCategory.Passwords,
    val query: String = "",
    val items: List<VaultItemUi> = emptyList(),
    val categoryCounts: Map<VaultCategory, Int> = emptyMap(),
    val categoryLoading: Boolean = false,
    val groups: List<VaultGroupUi> = emptyList(),
    val invites: List<VaultInviteUi> = emptyList(),
    val groupsLoaded: Boolean = false,
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
    suspend fun sync()
    suspend fun listItems(category: VaultCategory): List<VaultItemUi>
    suspend fun listGroups(): List<VaultGroupUi>
    suspend fun listInvites(): List<VaultInviteUi>
    suspend fun reveal(item: VaultItemUi): Pair<String, Long?>
    suspend fun createPassword(site: String, username: String, password: String, groupId: String?)
    suspend fun addTotp(item: VaultItemUi, setup: String)
    suspend fun deleteItem(item: VaultItemUi)
    suspend fun createGroup(name: String)
    suspend fun renameGroup(id: String, name: String)
    suspend fun deleteGroup(id: String)
    suspend fun inviteGroupMember(id: String, handle: String)
    suspend fun removeGroupMember(id: String, handle: String)
    suspend fun acceptInvite(id: String)
    suspend fun declineInvite(id: String)
}

class RustPasswordsPort(private val stateProvider: () -> NativePushState?) : PasswordsPort {
    private fun state(): NativePushState = stateProvider() ?: error("Apple services are not connected")

    override suspend fun isInClique(): Boolean = withContext(Dispatchers.IO) { state().isInClique() }

    override suspend fun sync() {
        state().syncPasswords()
    }

    override suspend fun listItems(category: VaultCategory): List<VaultItemUi> = withContext(Dispatchers.IO) {
        state().listPasswords(category.itemKind()).map { item ->
            VaultItemUi(
                id = item.id,
                category = item.kind.category(),
                title = item.title,
                username = item.username,
                groupId = item.groupId,
                modifiedAtMs = item.modifiedAtMs.toLong().takeIf { it > 0 },
            )
        }
    }

    override suspend fun listGroups(): List<VaultGroupUi> = withContext(Dispatchers.IO) {
        state().listPasswordGroups().map {
            VaultGroupUi(
                id = it.id,
                name = it.name,
                owner = it.owner,
                memberCount = it.memberCount.toInt(),
                members = it.members.map { member ->
                    VaultGroupMemberUi(
                        name = member.name,
                        handle = member.handle,
                        joined = member.joined,
                        currentUser = member.currentUser,
                    )
                },
            )
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

    override suspend fun addTotp(item: VaultItemUi, setup: String) {
        state().addPasswordTotp(item.title, item.username.orEmpty(), setup, item.groupId)
    }

    override suspend fun deleteItem(item: VaultItemUi) = withContext(Dispatchers.IO) {
        state().deletePassword(item.id, item.category.itemKind(), item.groupId)
    }

    override suspend fun createGroup(name: String) {
        withContext(Dispatchers.IO) { state().createPasswordGroup(name) }
    }

    override suspend fun renameGroup(id: String, name: String) =
        withContext(Dispatchers.IO) { state().renamePasswordGroup(id, name) }

    override suspend fun deleteGroup(id: String) =
        withContext(Dispatchers.IO) { state().deletePasswordGroup(id) }

    override suspend fun inviteGroupMember(id: String, handle: String) {
        state().invitePasswordGroupMember(id, handle)
    }

    override suspend fun removeGroupMember(id: String, handle: String) {
        state().removePasswordGroupMember(id, handle)
    }

    override suspend fun acceptInvite(id: String) =
        withContext(Dispatchers.IO) { state().acceptPasswordGroupInvite(id) }

    override suspend fun declineInvite(id: String) =
        withContext(Dispatchers.IO) { state().declinePasswordGroupInvite(id) }
}

class FakePasswordsPort(
    var inClique: Boolean = true,
    var items: List<VaultItemUi> = emptyList(),
    var groups: List<VaultGroupUi> = emptyList(),
    var invites: List<VaultInviteUi> = emptyList(),
    var secret: Pair<String, Long?> = "secret" to null,
    var syncedItems: List<VaultItemUi>? = null,
) : PasswordsPort {
    var syncCount: Int = 0
    val itemListRequests = mutableListOf<VaultCategory>()
    var groupListCount: Int = 0
    var inviteListCount: Int = 0
    val totpSetups = mutableListOf<Pair<VaultItemUi, String>>()
    val groupInvites = mutableListOf<Pair<String, String>>()
    val groupRemovals = mutableListOf<Pair<String, String>>()

    override suspend fun isInClique() = inClique
    override suspend fun sync() {
        syncCount += 1
        syncedItems?.let { items = it }
    }
    override suspend fun listItems(category: VaultCategory): List<VaultItemUi> {
        itemListRequests += category
        return items.filter { it.category == category }
    }
    override suspend fun listGroups(): List<VaultGroupUi> {
        groupListCount += 1
        return groups
    }
    override suspend fun listInvites(): List<VaultInviteUi> {
        inviteListCount += 1
        return invites
    }
    override suspend fun reveal(item: VaultItemUi) = secret
    override suspend fun createPassword(site: String, username: String, password: String, groupId: String?) {
        items = items + VaultItemUi("created", VaultCategory.Passwords, site, username, groupId)
    }
    override suspend fun addTotp(item: VaultItemUi, setup: String) {
        totpSetups += item to setup
        items = items + VaultItemUi("code-${item.id}", VaultCategory.Codes, item.title, item.username, item.groupId)
    }
    override suspend fun deleteItem(item: VaultItemUi) {
        items = items.filterNot { it.id == item.id && it.category == item.category }
    }
    override suspend fun createGroup(name: String) {
        groups = groups + VaultGroupUi("created-group", name, true, 1)
    }
    override suspend fun renameGroup(id: String, name: String) {
        groups = groups.map { if (it.id == id) it.copy(name = name) else it }
    }
    override suspend fun deleteGroup(id: String) {
        groups = groups.filterNot { it.id == id }
    }
    override suspend fun inviteGroupMember(id: String, handle: String) {
        groupInvites += id to handle
        groups = groups.map { group ->
            if (group.id != id) group else group.copy(
                memberCount = group.memberCount + 1,
                members = group.members + VaultGroupMemberUi(null, handle, false, false),
            )
        }
    }
    override suspend fun removeGroupMember(id: String, handle: String) {
        groupRemovals += id to handle
        groups = groups.map { group ->
            if (group.id != id) group else group.copy(
                memberCount = (group.memberCount - 1).coerceAtLeast(0),
                members = group.members.filterNot { it.handle == handle },
            )
        }
    }
    override suspend fun acceptInvite(id: String) {
        invites = invites.filterNot { it.id == id }
    }
    override suspend fun declineInvite(id: String) {
        invites = invites.filterNot { it.id == id }
    }
}

class PasswordsViewModel(private val port: PasswordsPort) : ViewModel() {
    private val mutableState = MutableStateFlow(PasswordsUiState())
    val uiState: StateFlow<PasswordsUiState> = mutableState.asStateFlow()
    private var categoryLoadJob: Job? = null

    init {
        refresh()
        viewModelScope.launch {
            VaultEditBus.revisions.drop(1).collect {
                try {
                    reloadAfterExternalEdit()
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (error: Throwable) {
                    mutableState.update {
                        it.copy(categoryLoading = false, error = error.message ?: "iCloud Passwords failed")
                    }
                }
            }
        }
    }

    fun refresh() {
        categoryLoadJob?.cancel()
        runAction(showLoading = true) {
            val category = mutableState.value.category
            val inClique = port.isInClique()
            if (!inClique) {
                mutableState.update {
                    it.copy(
                        loading = false,
                        inClique = false,
                        items = emptyList(),
                        categoryCounts = emptyMap(),
                        categoryLoading = false,
                        groups = emptyList(),
                        invites = emptyList(),
                        groupsLoaded = false,
                    )
                }
                return@runAction
            }
            mutableState.update {
                it.copy(
                    inClique = true,
                    items = emptyList(),
                    categoryCounts = emptyMap(),
                    categoryLoading = true,
                    groups = emptyList(),
                    invites = emptyList(),
                    groupsLoaded = false,
                )
            }
            loadCategoryContent(category)
            mutableState.update { it.copy(loading = false, busy = true) }
            port.sync()
            loadCategoryContent(mutableState.value.category)
        }
    }

    fun setCategory(category: VaultCategory) {
        if (category == mutableState.value.category && !mutableState.value.categoryLoading) return
        categoryLoadJob?.cancel()
        mutableState.update {
            it.copy(
                category = category,
                query = "",
                items = emptyList(),
                categoryLoading = true,
                error = null,
            )
        }
        categoryLoadJob = viewModelScope.launch {
            try {
                loadCategoryContent(category)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Throwable) {
                if (mutableState.value.category == category) {
                    mutableState.update {
                        it.copy(
                            categoryLoading = false,
                            error = error.message ?: "iCloud Passwords failed",
                        )
                    }
                }
            }
        }
    }

    fun setQuery(query: String) = mutableState.update { it.copy(query = query) }

    fun createPassword(site: String, username: String, password: String, groupId: String?) = runAction {
        port.createPassword(site.trim(), username.trim(), password, groupId)
        refreshAfterWrite(VaultCategory.Passwords)
    }

    fun createGroup(name: String) = runAction {
        port.createGroup(name.trim())
        refreshAfterWrite(VaultCategory.Groups)
    }

    fun acceptInvite(id: String) = runAction {
        port.acceptInvite(id)
        refreshAfterWrite(VaultCategory.Groups)
    }

    fun declineInvite(id: String) = runAction {
        port.declineInvite(id)
        refreshAfterWrite(VaultCategory.Groups)
    }

    fun clearError() = mutableState.update { it.copy(error = null) }

    fun prepareCreatePassword() {
        if (mutableState.value.groupsLoaded) return
        runAction {
            val groups = port.listGroups()
            mutableState.update {
                it.copy(
                    groups = groups,
                    groupsLoaded = true,
                    categoryCounts = it.categoryCounts + (VaultCategory.Groups to groups.size),
                )
            }
        }
    }

    /** A detail page changed the vault; re-fetch whatever is on screen. */
    private suspend fun reloadAfterExternalEdit() {
        if (mutableState.value.inClique != true) return
        mutableState.update { it.copy(categoryLoading = true, groupsLoaded = false) }
        loadCategoryContent(mutableState.value.category)
    }

    private suspend fun loadCategoryContent(category: VaultCategory) {
        if (category == VaultCategory.Groups) {
            val groups = port.listGroups()
            val invites = port.listInvites()
            if (mutableState.value.category == category) {
                mutableState.update {
                    it.copy(
                        groups = groups,
                        invites = invites,
                        groupsLoaded = true,
                        categoryCounts = it.categoryCounts + (category to groups.size),
                        categoryLoading = false,
                    )
                }
            }
        } else {
            val items = port.listItems(category)
            if (mutableState.value.category == category) {
                mutableState.update {
                    it.copy(
                        items = items,
                        categoryCounts = it.categoryCounts + (category to items.size),
                        categoryLoading = false,
                    )
                }
            }
        }
    }

    private suspend fun refreshAfterWrite(category: VaultCategory) {
        mutableState.update {
            it.copy(
                category = category,
                query = "",
                items = emptyList(),
                categoryLoading = true,
            )
        }
        loadCategoryContent(category)
    }

    private fun runAction(showLoading: Boolean = false, action: suspend () -> Unit) {
        viewModelScope.launch {
            mutableState.update { it.copy(loading = if (showLoading) true else it.loading, busy = !showLoading, error = null) }
            try {
                action()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Throwable) {
                mutableState.update {
                    it.copy(
                        loading = false,
                        categoryLoading = false,
                        error = error.message ?: "iCloud Passwords failed",
                    )
                }
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

internal fun VaultCategory.itemKind(): UVaultItemKind = when (this) {
    VaultCategory.Passwords -> UVaultItemKind.PASSWORD
    VaultCategory.Passkeys -> UVaultItemKind.PASSKEY
    VaultCategory.Codes -> UVaultItemKind.CODE
    VaultCategory.Wifi -> UVaultItemKind.WIFI
    VaultCategory.Groups -> error("Groups are not vault items")
}

private fun UVaultItemKind.category(): VaultCategory = when (this) {
    UVaultItemKind.PASSWORD -> VaultCategory.Passwords
    UVaultItemKind.PASSKEY -> VaultCategory.Passkeys
    UVaultItemKind.CODE -> VaultCategory.Codes
    UVaultItemKind.WIFI -> VaultCategory.Wifi
}

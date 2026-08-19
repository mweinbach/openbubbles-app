package app.openbubbles.nativeapp.ui.passwords

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import app.openbubbles.nativeapp.data.PushStateHolder
import kotlin.coroutines.coroutineContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
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

/**
 * Process-lifetime cache of vault listings (metadata only — never secrets),
 * so reopening the Passwords screen paints instantly while a background
 * refresh runs. Kept strictly in memory: it dies with the process and is
 * cleared when the account leaves the keychain clique.
 */
class VaultCacheStore {
    @Volatile var inClique: Boolean? = null
    @Volatile var itemsByCategory: Map<VaultCategory, List<VaultItemUi>> = emptyMap()
    @Volatile var groups: List<VaultGroupUi>? = null
    @Volatile var invites: List<VaultInviteUi>? = null

    fun clear() {
        inClique = null
        itemsByCategory = emptyMap()
        groups = null
        invites = null
    }

    companion object {
        /** Shared across screen opens; test constructors default to a fresh store. */
        val shared = VaultCacheStore()
    }
}

data class PasswordsUiState(
    val loading: Boolean = true,
    val inClique: Boolean? = null,
    val category: VaultCategory = VaultCategory.Passwords,
    val query: String = "",
    /** Every loaded item across all categories; the list filters by [category]. */
    val items: List<VaultItemUi> = emptyList(),
    val loadedCategories: Set<VaultCategory> = emptySet(),
    val categoryCounts: Map<VaultCategory, Int> = emptyMap(),
    val groups: List<VaultGroupUi> = emptyList(),
    val invites: List<VaultInviteUi> = emptyList(),
    val groupsLoaded: Boolean = false,
    val busy: Boolean = false,
    val error: String? = null,
) {
    /** The shown category has not produced content yet. */
    val categoryLoading: Boolean get() = category !in loadedCategories
}

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

class PasswordsViewModel(
    private val port: PasswordsPort,
    private val cache: VaultCacheStore = VaultCacheStore(),
) : ViewModel() {
    private val mutableState = MutableStateFlow(PasswordsUiState())
    val uiState: StateFlow<PasswordsUiState> = mutableState.asStateFlow()
    private var refreshJob: Job? = null

    init {
        seedFromCache()
        refresh()
        viewModelScope.launch {
            VaultEditBus.revisions.drop(1).collect {
                if (mutableState.value.inClique != true) return@collect
                try {
                    loadEverything()
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (error: Throwable) {
                    mutableState.update {
                        it.copy(error = error.message ?: "iCloud Passwords failed")
                    }
                }
            }
        }
    }

    fun refresh() {
        refreshJob?.cancel()
        refreshJob = viewModelScope.launch {
            // Cached content stays on screen; the full-screen spinner only
            // appears when there is nothing at all to show yet.
            val hasContent = mutableState.value.loadedCategories.isNotEmpty()
            mutableState.update { it.copy(loading = !hasContent, busy = true, error = null) }
            try {
                val inClique = port.isInClique()
                if (!inClique) {
                    cache.clear()
                    mutableState.update {
                        it.copy(
                            loading = false,
                            inClique = false,
                            items = emptyList(),
                            loadedCategories = emptySet(),
                            categoryCounts = emptyMap(),
                            groups = emptyList(),
                            invites = emptyList(),
                            groupsLoaded = false,
                        )
                    }
                    return@launch
                }
                cache.inClique = true
                mutableState.update { it.copy(inClique = true, loading = false) }
                loadEverything()
                port.sync()
                loadEverything()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Throwable) {
                mutableState.update { it.copy(error = error.message ?: "iCloud Passwords failed") }
            } finally {
                // A newer refresh may already be running; only the live one
                // clears the progress flags.
                if (coroutineContext.isActive) {
                    mutableState.update { it.copy(loading = false, busy = false) }
                }
            }
        }
    }

    fun setCategory(category: VaultCategory) {
        val alreadyLoaded = category in mutableState.value.loadedCategories
        mutableState.update { it.copy(category = category, query = "") }
        // Everything is loaded eagerly, so switching is normally instant; this
        // fallback covers a category whose eager load failed earlier.
        if (!alreadyLoaded && refreshJob?.isActive != true && mutableState.value.inClique == true) {
            viewModelScope.launch {
                try {
                    if (category == VaultCategory.Groups) {
                        applyGroups(port.listGroups(), port.listInvites())
                    } else {
                        applyItems(category, port.listItems(category))
                    }
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (error: Throwable) {
                    mutableState.update {
                        it.copy(error = error.message ?: "iCloud Passwords failed")
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
            applyGroups(port.listGroups(), port.listInvites())
        }
    }

    /** Paint the last known vault immediately; [refresh] revalidates behind it. */
    private fun seedFromCache() {
        if (cache.inClique != true) return
        val cachedItems = cache.itemsByCategory
        val cachedGroups = cache.groups
        if (cachedItems.isEmpty() && cachedGroups == null) return
        mutableState.update { state ->
            state.copy(
                loading = false,
                inClique = true,
                items = cachedItems.values.flatten(),
                loadedCategories = cachedItems.keys +
                    if (cachedGroups != null) setOf(VaultCategory.Groups) else emptySet(),
                categoryCounts = cachedItems.mapValues { it.value.size } +
                    (cachedGroups?.let { mapOf(VaultCategory.Groups to it.size) } ?: emptyMap()),
                groups = cachedGroups ?: emptyList(),
                invites = cache.invites ?: emptyList(),
                groupsLoaded = cachedGroups != null,
            )
        }
    }

    /**
     * Fetch every category in parallel so counts and lists are all present
     * without tapping through, updating the screen as each one lands.
     */
    private suspend fun loadEverything() = coroutineScope {
        val loads = ITEM_CATEGORIES.map { category ->
            async { applyItems(category, port.listItems(category)) }
        } + async { applyGroups(port.listGroups(), port.listInvites()) }
        loads.awaitAll()
    }

    private fun applyItems(category: VaultCategory, items: List<VaultItemUi>) {
        cache.itemsByCategory = cache.itemsByCategory + (category to items)
        mutableState.update { state ->
            state.copy(
                items = state.items.filterNot { it.category == category } + items,
                loadedCategories = state.loadedCategories + category,
                categoryCounts = state.categoryCounts + (category to items.size),
            )
        }
    }

    private fun applyGroups(groups: List<VaultGroupUi>, invites: List<VaultInviteUi>) {
        cache.groups = groups
        cache.invites = invites
        mutableState.update { state ->
            state.copy(
                groups = groups,
                invites = invites,
                groupsLoaded = true,
                loadedCategories = state.loadedCategories + VaultCategory.Groups,
                categoryCounts = state.categoryCounts + (VaultCategory.Groups to groups.size),
            )
        }
    }

    private suspend fun refreshAfterWrite(category: VaultCategory) {
        mutableState.update { it.copy(category = category, query = "") }
        loadEverything()
    }

    private fun runAction(action: suspend () -> Unit) {
        viewModelScope.launch {
            mutableState.update { it.copy(busy = true, error = null) }
            try {
                action()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Throwable) {
                mutableState.update {
                    it.copy(error = error.message ?: "iCloud Passwords failed")
                }
            } finally {
                mutableState.update { it.copy(busy = false) }
            }
        }
    }

    companion object {
        private val ITEM_CATEGORIES = listOf(
            VaultCategory.Passwords,
            VaultCategory.Passkeys,
            VaultCategory.Codes,
            VaultCategory.Wifi,
        )

        fun factory(): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                PasswordsViewModel(RustPasswordsPort { PushStateHolder.state }, VaultCacheStore.shared)
            }
        }
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

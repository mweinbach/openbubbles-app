package app.openbubbles.nativeapp.ui.passwords

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import app.openbubbles.core.passwords.VaultCatalog
import app.openbubbles.core.passwords.VaultGroupRecord
import app.openbubbles.core.passwords.VaultItemRecord
import app.openbubbles.core.passwords.record
import app.openbubbles.nativeapp.credentials.vaultPasskeyUserDecoder
import app.openbubbles.nativeapp.data.AppContext
import app.openbubbles.nativeapp.data.PushStateHolder
import app.openbubbles.nativeapp.data.passwords.VaultCatalogStore
import app.openbubbles.nativeapp.data.passwords.VaultCatalogSync
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
 * Cache of vault listings (metadata only — never secrets) so reopening the
 * Passwords screen paints instantly while a background refresh runs.
 *
 * Two tiers. The in-memory maps survive screen opens; [catalog] survives
 * process death, which is what a cold start actually hits. The catalog is
 * read-only here — [VaultCatalogSync] is its single writer, so the rows the
 * Android credential provider depends on cannot be narrowed by a UI-shaped
 * projection. Both tiers are cleared when the account leaves the keychain
 * clique.
 */
class VaultCacheStore(
    private val catalog: VaultCatalog? = null,
    private val requestCatalogRefresh: () -> Unit = {},
) {
    @Volatile var inClique: Boolean? = null
    @Volatile var itemsByCategory: Map<VaultCategory, List<VaultItemUi>> = emptyMap()
    @Volatile var groups: List<VaultGroupUi>? = null
    @Volatile var invites: List<VaultInviteUi>? = null

    /** Whether the in-memory tier can paint right now, with no disk read. */
    fun warm(): Boolean = inClique == true && (itemsByCategory.isNotEmpty() || groups != null)

    /**
     * Restores the durable snapshot into the in-memory tier. Returns whether
     * anything is now paintable; a cold catalog leaves the tiers untouched.
     */
    suspend fun restore(): Boolean {
        if (warm()) return true
        val cached = catalog?.load() ?: return false
        if (cached.cold) return false
        itemsByCategory = ITEM_CATEGORIES
            .filter { it.itemKind().record() in cached.syncedKinds }
            .associateWith { category ->
                cached.items(category.itemKind().record()).map { it.ui(category) }
            }
        if (cached.groupsSynced) {
            groups = cached.groups.map { it.ui() }
            invites = cached.invites.map { VaultInviteUi(it.id, it.groupName, it.inviter) }
        }
        inClique = true
        return itemsByCategory.isNotEmpty() || groups != null
    }

    /** The vault changed, so the durable catalog must be re-read from Apple. */
    fun invalidateCatalog() = requestCatalogRefresh()

    fun clearMemory() {
        inClique = null
        itemsByCategory = emptyMap()
        groups = null
        invites = null
    }

    suspend fun clear() {
        clearMemory()
        catalog?.clearAccountData()
    }

    companion object {
        private val ITEM_CATEGORIES = listOf(
            VaultCategory.Passwords,
            VaultCategory.Passkeys,
            VaultCategory.Codes,
            VaultCategory.Wifi,
        )
    }
}

private fun VaultItemRecord.ui(category: VaultCategory) = VaultItemUi(
    id = id,
    category = category,
    title = title,
    username = username,
    groupId = groupId,
    modifiedAtMs = modifiedAtMs,
)

private fun VaultGroupRecord.ui() = VaultGroupUi(
    id = id,
    name = name,
    owner = owner,
    memberCount = memberCount,
    members = members.map { VaultGroupMemberUi(it.name, it.handle, it.joined, it.currentUser) },
)

/**
 * A credential draft lives only in its navigation-entry ViewModel. In
 * particular, its password must never enter SavedStateHandle, a Bundle, the
 * durable catalog, or a rememberSaveable slot.
 */
data class VaultPasswordDraft(
    val site: String = "",
    val username: String = "",
    val password: String = "",
    val groupId: String? = null,
    val passwordVisible: Boolean = false,
)

enum class VaultComposerKind { CreatePassword, CreateGroup, Invite }

data class VaultComposerUiState(
    val kind: VaultComposerKind,
    val passwordDraft: VaultPasswordDraft? = null,
    val groupDraft: String = "",
    val invite: VaultInviteUi? = null,
    val error: String? = null,
)

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
    /** Transient, memory-only editor retained by this entry's ViewModel. */
    val composer: VaultComposerUiState? = null,
) {
    /** The shown category has not produced content yet. */
    val categoryLoading: Boolean get() = category !in loadedCategories
}

/**
 * The items one category shows, matching [query], in the order they are listed.
 *
 * Rust returns whatever order the CKKS zone yielded, which is stable but
 * arbitrary: a vault of two hundred logins was effectively unsorted. Sorting by
 * title (then username) here makes the list scannable and makes the A–Z headers
 * in [vaultSections] mean something.
 */
internal fun filterVaultItems(
    items: List<VaultItemUi>,
    category: VaultCategory,
    query: String,
): List<VaultItemUi> {
    val needle = query.trim().lowercase()
    return items.asSequence()
        .filter { item ->
            item.category == category &&
                (
                    needle.isEmpty() ||
                        item.title.lowercase().contains(needle) ||
                        item.username.orEmpty().lowercase().contains(needle)
                    )
        }
        .sortedWith(
            compareBy(String.CASE_INSENSITIVE_ORDER, VaultItemUi::title)
                .thenBy(String.CASE_INSENSITIVE_ORDER) { it.username.orEmpty() },
        )
        .toList()
}

/** One alphabetical run of vault items. */
data class VaultSection(val letter: String, val items: List<VaultItemUi>)

/**
 * Alphabetical sections for a category.
 *
 * Anything that does not start with a letter groups under "#", which is where a
 * person looks for `1password.com` or an IP address. Below [minimumForSections]
 * items the list is short enough to read at once and gets a single unlabelled
 * run instead of headers that outnumber the rows.
 */
internal fun vaultSections(
    items: List<VaultItemUi>,
    minimumForSections: Int = 12,
): List<VaultSection> {
    if (items.isEmpty()) return emptyList()
    if (items.size < minimumForSections) return listOf(VaultSection(letter = "", items = items))
    val sections = LinkedHashMap<String, MutableList<VaultItemUi>>()
    items.forEach { item ->
        sections.getOrPut(sectionLetter(item.title)) { mutableListOf() } += item
    }
    return sections.map { (letter, group) -> VaultSection(letter, group) }
}

private fun sectionLetter(title: String): String {
    val first = title.trim().firstOrNull() ?: return "#"
    return if (first.isLetter()) first.uppercaseChar().toString() else "#"
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
        val decoder = vaultPasskeyUserDecoder()
        state().listPasswords(category.itemKind()).map { item ->
            // Apple gives a passkey no account of its own, so the label comes
            // from its user tag rather than leaving the row blank.
            val record = item.record(decoder)
            VaultItemUi(
                id = record.id,
                category = item.kind.category(),
                title = record.title,
                username = record.username,
                groupId = record.groupId,
                modifiedAtMs = record.modifiedAtMs,
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
        // The in-memory tier paints without suspending, so reopening the screen
        // has no empty frame at all; the durable tier is read inside [refresh].
        paintFromCache()
        refresh()
        viewModelScope.launch {
            VaultEditBus.revisions.drop(1).collect {
                if (mutableState.value.inClique != true) return@collect
                val generation = VaultCatalogSync.captureGeneration()
                try {
                    loadEverything(generation)
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (error: Throwable) {
                    VaultCatalogSync.publishIfCurrent(generation) {
                        mutableState.update {
                            it.copy(error = error.message ?: "iCloud Passwords failed")
                        }
                    }
                }
            }
        }
    }

    fun refresh() {
        refreshJob?.cancel()
        refreshJob = viewModelScope.launch {
            val accountGeneration = VaultCatalogSync.captureGeneration()
            // A cold process has no in-memory tier; restore the durable catalog
            // so a cold start paints the last known vault instead of a spinner.
            val restoredPublication = try {
                VaultCatalogSync.publishIfCurrent(accountGeneration) { cache.restore() }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Throwable) {
                false
            }
            if (restoredPublication == null) return@launch
            val restored = restoredPublication
            if (restored && VaultCatalogSync.publishIfCurrent(accountGeneration) {
                    paintFromCache()
                    true
                } == null
            ) return@launch
            // Cached content stays on screen; the full-screen spinner only
            // appears when there is nothing at all to show yet.
            val hasContent = mutableState.value.loadedCategories.isNotEmpty()
            mutableState.update { it.copy(loading = !hasContent, busy = true, error = null) }
            try {
                val inClique = port.isInClique()
                if (!inClique) {
                    val cleared = VaultCatalogSync.publishIfCurrent(accountGeneration) {
                        cache.clear()
                        true
                    }
                    if (cleared == null) return@launch
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
                if (VaultCatalogSync.publishIfCurrent(accountGeneration) {
                        cache.inClique = true
                        mutableState.update { it.copy(inClique = true, loading = false) }
                        true
                    } == null
                ) return@launch
                if (!loadEverything(accountGeneration)) return@launch
                port.sync()
                if (!loadEverything(accountGeneration)) return@launch
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Throwable) {
                VaultCatalogSync.publishIfCurrent(accountGeneration) {
                    mutableState.update { it.copy(error = error.message ?: "iCloud Passwords failed") }
                }
            } finally {
                // A newer refresh may already be running; only the live one
                // clears the progress flags.
                if (coroutineContext.isActive) {
                    VaultCatalogSync.publishIfCurrent(accountGeneration) {
                        mutableState.update { it.copy(loading = false, busy = false) }
                    }
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
                val generation = VaultCatalogSync.captureGeneration()
                try {
                    if (category == VaultCategory.Groups) {
                        applyGroups(generation, port.listGroups(), port.listInvites())
                    } else {
                        applyItems(generation, category, port.listItems(category))
                    }
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (error: Throwable) {
                    VaultCatalogSync.publishIfCurrent(generation) {
                        mutableState.update {
                            it.copy(error = error.message ?: "iCloud Passwords failed")
                        }
                    }
                }
            }
        }
    }

    fun setQuery(query: String) = mutableState.update { it.copy(query = query) }

    fun createPassword(site: String, username: String, password: String, groupId: String?) = runAction(
        closeComposerOnSuccess = true,
    ) { generation ->
        port.createPassword(site.trim(), username.trim(), password, groupId)
        refreshAfterWrite(generation, VaultCategory.Passwords)
    }

    fun createGroup(name: String) = runAction(closeComposerOnSuccess = true) { generation ->
        port.createGroup(name.trim())
        refreshAfterWrite(generation, VaultCategory.Groups)
    }

    fun acceptInvite(id: String) = runAction(closeComposerOnSuccess = true) { generation ->
        port.acceptInvite(id)
        refreshAfterWrite(generation, VaultCategory.Groups)
    }

    fun declineInvite(id: String) = runAction(closeComposerOnSuccess = true) { generation ->
        port.declineInvite(id)
        refreshAfterWrite(generation, VaultCategory.Groups)
    }

    fun clearError() = mutableState.update { state ->
        state.copy(error = null, composer = state.composer?.copy(error = null))
    }

    fun prepareCreatePassword() {
        if (mutableState.value.busy) return
        mutableState.update {
            it.copy(
                composer = VaultComposerUiState(
                    kind = VaultComposerKind.CreatePassword,
                    passwordDraft = VaultPasswordDraft(),
                ),
                error = null,
            )
        }
        if (mutableState.value.groupsLoaded) return
        runAction { generation ->
            applyGroups(generation, port.listGroups(), port.listInvites())
        }
    }

    fun openCreateGroup() {
        if (mutableState.value.busy) return
        mutableState.update {
            it.copy(
                composer = VaultComposerUiState(kind = VaultComposerKind.CreateGroup),
                error = null,
            )
        }
    }

    fun openInvite(invite: VaultInviteUi) {
        if (mutableState.value.busy) return
        mutableState.update {
            it.copy(
                composer = VaultComposerUiState(kind = VaultComposerKind.Invite, invite = invite),
                error = null,
            )
        }
    }

    fun updatePasswordDraft(draft: VaultPasswordDraft) {
        mutableState.update { state ->
            val composer = state.composer
            if (state.busy || composer?.kind != VaultComposerKind.CreatePassword) {
                state
            } else {
                state.copy(composer = composer.copy(passwordDraft = draft, error = null))
            }
        }
    }

    fun updateGroupDraft(value: String) {
        mutableState.update { state ->
            val composer = state.composer
            if (state.busy || composer?.kind != VaultComposerKind.CreateGroup) {
                state
            } else {
                state.copy(composer = composer.copy(groupDraft = value, error = null))
            }
        }
    }

    fun dismissComposer() {
        mutableState.update { state ->
            if (state.busy) state else state.copy(composer = null)
        }
    }

    /** Paint the last known vault immediately; [refresh] revalidates behind it. */
    private fun paintFromCache() {
        if (!cache.warm()) return
        val cachedItems = cache.itemsByCategory
        val cachedGroups = cache.groups
        mutableState.update { state ->
            state.copy(
                loading = false,
                inClique = true,
                items = cachedItems.values.flatten(),
                loadedCategories = cachedItems.keys +
                    if (cachedGroups != null) setOf(VaultCategory.Groups) else emptySet(),
                categoryCounts = cachedItems.mapValues { it.value.size } +
                    (
                        cachedGroups?.let {
                            mapOf(VaultCategory.Groups to it.size + (cache.invites?.size ?: 0))
                        } ?: emptyMap()
                        ),
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
    private suspend fun loadEverything(generation: Long): Boolean = coroutineScope {
        val loads = ITEM_CATEGORIES.map { category ->
            async { applyItems(generation, category, port.listItems(category)) }
        } + async { applyGroups(generation, port.listGroups(), port.listInvites()) }
        if (!loads.awaitAll().all { it }) return@coroutineScope false
        // The screen and the Android credential provider read the same vault;
        // whatever this pass just saw has to reach the durable catalog too.
        VaultCatalogSync.publishIfCurrent(generation) {
            cache.invalidateCatalog()
            true
        } != null
    }

    private suspend fun applyItems(
        generation: Long,
        category: VaultCategory,
        items: List<VaultItemUi>,
    ): Boolean = VaultCatalogSync.publishIfCurrent(generation) {
        cache.itemsByCategory = cache.itemsByCategory + (category to items)
        mutableState.update { state ->
            state.copy(
                items = state.items.filterNot { it.category == category } + items,
                loadedCategories = state.loadedCategories + category,
                categoryCounts = state.categoryCounts + (category to items.size),
            )
        }
        true
    } != null

    private suspend fun applyGroups(
        generation: Long,
        groups: List<VaultGroupUi>,
        invites: List<VaultInviteUi>,
    ): Boolean = VaultCatalogSync.publishIfCurrent(generation) {
        cache.groups = groups
        cache.invites = invites
        mutableState.update { state ->
            state.copy(
                groups = groups,
                invites = invites,
                groupsLoaded = true,
                loadedCategories = state.loadedCategories + VaultCategory.Groups,
                // A pending invitation is a row inside Groups, so the category
                // count has to include it or the badge undercounts what is there.
                categoryCounts = state.categoryCounts +
                    (VaultCategory.Groups to groups.size + invites.size),
            )
        }
        true
    } != null

    private suspend fun refreshAfterWrite(generation: Long, category: VaultCategory) {
        if (VaultCatalogSync.publishIfCurrent(generation) {
                mutableState.update { it.copy(category = category, query = "") }
                true
            } == null
        ) return
        loadEverything(generation)
    }

    private fun runAction(
        closeComposerOnSuccess: Boolean = false,
        action: suspend (Long) -> Unit,
    ) {
        viewModelScope.launch {
            val generation = VaultCatalogSync.captureGeneration()
            mutableState.update { state ->
                state.copy(
                    busy = true,
                    error = null,
                    composer = state.composer?.copy(error = null),
                )
            }
            try {
                action(generation)
                if (closeComposerOnSuccess) {
                    VaultCatalogSync.publishIfCurrent(generation) {
                        mutableState.update { it.copy(composer = null) }
                    }
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Throwable) {
                VaultCatalogSync.publishIfCurrent(generation) {
                    mutableState.update { state ->
                        val message = error.message ?: "iCloud Passwords failed"
                        val composer = state.composer
                        if (composer == null) {
                            state.copy(error = message)
                        } else {
                            state.copy(composer = composer.copy(error = message))
                        }
                    }
                }
            } finally {
                VaultCatalogSync.publishIfCurrent(generation) {
                    mutableState.update { it.copy(busy = false) }
                }
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

        /**
         * Shared across screen opens so reopening Passwords repaints from the
         * warm tier; test constructors default to a fresh, catalog-less store.
         */
        private val sharedCache: VaultCacheStore by lazy {
            VaultCacheStore(
                catalog = AppContext.current?.let { VaultCatalogStore.of(it) },
                requestCatalogRefresh = {
                    val context = AppContext.current
                    val state = PushStateHolder.state
                    if (context != null && state != null) VaultCatalogSync.refreshNow(context, state)
                },
            )
        }

        internal fun clearSharedCacheForAccountCleanup() {
            sharedCache.clearMemory()
        }

        fun factory(): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                PasswordsViewModel(RustPasswordsPort { PushStateHolder.state }, sharedCache)
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

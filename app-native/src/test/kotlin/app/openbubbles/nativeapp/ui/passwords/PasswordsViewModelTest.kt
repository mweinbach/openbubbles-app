package app.openbubbles.nativeapp.ui.passwords

import app.openbubbles.core.passwords.InMemoryVaultCatalog
import app.openbubbles.core.passwords.CachedVault
import app.openbubbles.core.passwords.VaultCatalog
import app.openbubbles.core.passwords.VaultGroupRecord
import app.openbubbles.core.passwords.VaultItemKind
import app.openbubbles.core.passwords.VaultItemRecord
import app.openbubbles.nativeapp.data.passwords.VaultCatalogSync
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain

/** A vault listing that has not answered yet, standing in for a booting backend. */
private class GatedPasswordsPort(
    private val delegate: FakePasswordsPort,
    private val listing: CompletableDeferred<Unit>,
) : PasswordsPort by delegate {
    override suspend fun listItems(category: VaultCategory): List<VaultItemUi> {
        listing.await()
        return delegate.listItems(category)
    }

    override suspend fun listGroups(): List<VaultGroupUi> {
        listing.await()
        return delegate.listGroups()
    }
}

private class GatedCliquePort(
    private val delegate: FakePasswordsPort,
    private val clique: CompletableDeferred<Boolean>,
) : PasswordsPort by delegate {
    override suspend fun isInClique(): Boolean = clique.await()
}

private class GatedVaultSyncPort(
    private val delegate: FakePasswordsPort,
    private val sync: CompletableDeferred<Unit>,
) : PasswordsPort by delegate {
    override suspend fun sync() {
        sync.await()
        delegate.sync()
    }
}

private class ControlledVaultMutationPort(
    private val delegate: FakePasswordsPort,
    var failure: String? = null,
    var gate: CompletableDeferred<Unit>? = null,
) : PasswordsPort by delegate {
    private suspend fun beforeMutation() {
        gate?.await()
        failure?.let { error(it) }
    }

    override suspend fun createPassword(site: String, username: String, password: String, groupId: String?) {
        beforeMutation()
        delegate.createPassword(site, username, password, groupId)
    }

    override suspend fun createGroup(name: String) {
        beforeMutation()
        delegate.createGroup(name)
    }

    override suspend fun acceptInvite(id: String) {
        beforeMutation()
        delegate.acceptInvite(id)
    }

    override suspend fun declineInvite(id: String) {
        beforeMutation()
        delegate.declineInvite(id)
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class PasswordsViewModelTest {

    @Test
    fun `memory cache cleanup drops all previous account metadata`() {
        val cache = VaultCacheStore().apply {
            inClique = true
            itemsByCategory = mapOf(
                VaultCategory.Passwords to listOf(
                    VaultItemUi("item", VaultCategory.Passwords, "example.com", "ada"),
                ),
            )
            groups = listOf(VaultGroupUi("group", "Family", true, 2))
            invites = listOf(VaultInviteUi("invite", "Family", "Ada"))
        }

        cache.clearMemory()

        assertEquals(null, cache.inClique)
        assertTrue(cache.itemsByCategory.isEmpty())
        assertEquals(null, cache.groups)
        assertEquals(null, cache.invites)
    }
    private val dispatcher = StandardTestDispatcher()

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `fake port lists and search filters passwords`() = runTest {
        val port = FakePasswordsPort(
            items = listOf(
                VaultItemUi("1", VaultCategory.Passwords, "example.com", "alice"),
                VaultItemUi("2", VaultCategory.Passwords, "work.test", "bob"),
                VaultItemUi("3", VaultCategory.Wifi, "Home Wi-Fi"),
            ),
        )

        val passwords = port.listItems(VaultCategory.Passwords)
        val wifi = port.listItems(VaultCategory.Wifi)

        assertEquals(listOf("example.com"), filterVaultItems(passwords, VaultCategory.Passwords, "ali").map { it.title })
        assertEquals(listOf("Home Wi-Fi"), filterVaultItems(wifi, VaultCategory.Wifi, "home").map { it.title })
    }

    @Test
    fun `fake port create preserves selected group`() = runTest {
        val port = FakePasswordsPort()

        port.createPassword("example.com", "alice", "secret", "family")

        assertEquals("family", port.listItems(VaultCategory.Passwords).single().groupId)
    }

    @Test
    fun `initial refresh syncs before fetching each vault collection exactly once`() = runTest(dispatcher) {
        val synced = VaultItemUi("remote", VaultCategory.Passwords, "example.com", "alice")
        val port = FakePasswordsPort(syncedItems = listOf(synced))

        val model = PasswordsViewModel(port)
        advanceUntilIdle()

        assertEquals(1, port.syncCount)
        assertEquals(listOf(synced), model.uiState.value.items)
        assertEquals(
            mapOf(
                VaultCategory.Passwords to 1,
                VaultCategory.Passkeys to 1,
                VaultCategory.Codes to 1,
                VaultCategory.Wifi to 1,
            ),
            port.itemListRequests.groupingBy { it }.eachCount(),
        )
        assertEquals(1, port.groupListCount)
        assertEquals(1, port.inviteListCount)
    }

    @Test
    fun `cached metadata remains visible while sync precedes the only listing pass`() = runTest(dispatcher) {
        val cached = VaultItemUi("cached", VaultCategory.Passwords, "cached.example", "alice")
        val synced = VaultItemUi("synced", VaultCategory.Passwords, "fresh.example", "alice")
        val cache = VaultCacheStore().apply {
            inClique = true
            itemsByCategory = mapOf(VaultCategory.Passwords to listOf(cached))
        }
        val synchronization = CompletableDeferred<Unit>()
        val delegate = FakePasswordsPort(items = listOf(cached), syncedItems = listOf(synced))
        val model = PasswordsViewModel(GatedVaultSyncPort(delegate, synchronization), cache)

        runCurrent()

        assertEquals(listOf(cached), model.uiState.value.items)
        assertEquals(false, model.uiState.value.loading)
        assertTrue(model.uiState.value.busy)
        assertTrue(delegate.itemListRequests.isEmpty())
        assertEquals(0, delegate.groupListCount)
        assertEquals(0, delegate.inviteListCount)

        synchronization.complete(Unit)
        advanceUntilIdle()

        assertEquals(listOf(synced), model.uiState.value.items)
        assertEquals(4, delegate.itemListRequests.size)
        assertEquals(1, delegate.groupListCount)
        assertEquals(1, delegate.inviteListCount)
    }

    @Test
    fun `failed synchronization still lists available vault metadata once and reports the error`() =
        runTest(dispatcher) {
            val cached = VaultItemUi("cached", VaultCategory.Passwords, "cached.example", "alice")
            val delegate = FakePasswordsPort(items = listOf(cached))
            val port = object : PasswordsPort by delegate {
                override suspend fun sync() = error("Apple is offline")
            }

            val model = PasswordsViewModel(port)
            advanceUntilIdle()

            assertEquals(listOf(cached), model.uiState.value.items)
            assertEquals("Apple is offline", model.uiState.value.error)
            assertEquals(4, delegate.itemListRequests.size)
            assertEquals(1, delegate.groupListCount)
            assertEquals(1, delegate.inviteListCount)
            assertEquals(false, model.uiState.value.loading)
            assertEquals(false, model.uiState.value.busy)
    }

    @Test
    fun `every category is loaded up front and switching never refetches`() = runTest(dispatcher) {
        val password = VaultItemUi("password", VaultCategory.Passwords, "example.com", "alice")
        val wifi = VaultItemUi("wifi", VaultCategory.Wifi, "Home Wi-Fi")
        val port = FakePasswordsPort(
            items = listOf(password, wifi),
            groups = listOf(VaultGroupUi("family", "Family", true, 2)),
        )
        val model = PasswordsViewModel(port)
        advanceUntilIdle()

        assertEquals(
            mapOf(
                VaultCategory.Passwords to 1,
                VaultCategory.Passkeys to 0,
                VaultCategory.Codes to 0,
                VaultCategory.Wifi to 1,
                VaultCategory.Groups to 1,
            ),
            model.uiState.value.categoryCounts,
        )

        val listCallsBefore = port.itemListRequests.size
        val groupCallsBefore = port.groupListCount
        model.setCategory(VaultCategory.Wifi)
        advanceUntilIdle()

        assertEquals(listCallsBefore, port.itemListRequests.size)
        assertEquals(false, model.uiState.value.categoryLoading)
        assertEquals(listOf(wifi), filterVaultItems(model.uiState.value.items, VaultCategory.Wifi, ""))

        model.setCategory(VaultCategory.Groups)
        advanceUntilIdle()

        assertEquals(groupCallsBefore, port.groupListCount)
        assertEquals(listOf("family"), model.uiState.value.groups.map { it.id })
    }

    @Test
    fun `a warm cache paints the screen before any port call`() = runTest(dispatcher) {
        val cache = VaultCacheStore()
        val item = VaultItemUi("1", VaultCategory.Passwords, "example.com", "alice")
        val port = FakePasswordsPort(items = listOf(item))
        val first = PasswordsViewModel(port, cache)
        advanceUntilIdle()
        assertEquals(listOf(item), first.uiState.value.items)

        // Reopening the screen: state is seeded synchronously from the cache,
        // before the background refresh has run at all.
        val second = PasswordsViewModel(port, cache)
        assertEquals(false, second.uiState.value.loading)
        assertEquals(listOf(item), second.uiState.value.items)
        assertEquals(false, second.uiState.value.categoryLoading)
    }

    @Test
    fun `a cold process paints from the durable catalog before the port answers`() = runTest(dispatcher) {
        val catalog = InMemoryVaultCatalog()
        catalog.replaceItems(
            VaultItemKind.Password,
            listOf(
                VaultItemRecord(
                    id = "1",
                    kind = VaultItemKind.Password,
                    site = "example.com",
                    title = "example.com",
                    username = "alice",
                ),
            ),
            syncedAtMs = 1_000,
        )
        catalog.replaceGroups(
            listOf(VaultGroupRecord("family", "Family", owner = true, memberCount = 2)),
            emptyList(),
            syncedAtMs = 1_000,
        )

        // A fresh process: nothing in memory, everything on disk, and a vault
        // listing that has not answered yet (Rust still booting).
        val listing = CompletableDeferred<Unit>()
        val port = GatedPasswordsPort(FakePasswordsPort(items = emptyList()), listing)
        val model = PasswordsViewModel(port, VaultCacheStore(catalog))
        advanceUntilIdle()

        assertEquals(listOf("example.com"), model.uiState.value.items.map { it.title })
        assertEquals(listOf("Family"), model.uiState.value.groups.map { it.name })
        assertEquals(false, model.uiState.value.loading)
        assertEquals(false, model.uiState.value.categoryLoading)

        // The live listing stays authoritative once it lands.
        listing.complete(Unit)
        advanceUntilIdle()
        assertEquals(emptyList(), model.uiState.value.items)
    }

    @Test
    fun `a failed durable restore falls back to the live vault`() = runTest(dispatcher) {
        val backing = InMemoryVaultCatalog()
        val failingCatalog = object : VaultCatalog by backing {
            override suspend fun load(): CachedVault = error("catalog unavailable")
        }
        val live = VaultItemUi("live", VaultCategory.Passwords, "example.com", "alice")
        val port = FakePasswordsPort(items = listOf(live))

        val model = PasswordsViewModel(port, VaultCacheStore(failingCatalog))
        advanceUntilIdle()

        assertEquals(listOf(live), model.uiState.value.items)
        assertEquals(false, model.uiState.value.loading)
        assertEquals(null, model.uiState.value.error)
    }

    @Test
    fun `leaving the keychain clique clears the durable catalog too`() = runTest(dispatcher) {
        val catalog = InMemoryVaultCatalog()
        catalog.replaceItems(
            VaultItemKind.Password,
            listOf(
                VaultItemRecord(
                    id = "1",
                    kind = VaultItemKind.Password,
                    site = "example.com",
                    title = "example.com",
                    username = "alice",
                ),
            ),
            syncedAtMs = 1_000,
        )

        val port = FakePasswordsPort(inClique = false)
        PasswordsViewModel(port, VaultCacheStore(catalog))
        advanceUntilIdle()

        assertEquals(true, catalog.load().cold)
        assertEquals(emptyList(), catalog.load().items)
    }

    @Test
    fun `stale clique result cannot clear a newer account generation`() = runTest(dispatcher) {
        val record = VaultItemRecord(
            id = "1",
            kind = VaultItemKind.Password,
            site = "example.com",
            title = "example.com",
            username = "alice",
        )
        val item = VaultItemUi("1", VaultCategory.Passwords, "example.com", "alice")
        val catalog = InMemoryVaultCatalog().apply {
            replaceItems(VaultItemKind.Password, listOf(record), syncedAtMs = 1_000)
        }
        val cache = VaultCacheStore(catalog).apply {
            inClique = true
            itemsByCategory = mapOf(VaultCategory.Passwords to listOf(item))
        }
        val answer = CompletableDeferred<Boolean>()
        val model = PasswordsViewModel(GatedCliquePort(FakePasswordsPort(), answer), cache)
        runCurrent()

        VaultCatalogSync.beginAccountCleanup()
        try {
            answer.complete(false)
            advanceUntilIdle()

            assertEquals(listOf(record), catalog.load().items)
            assertEquals(listOf(item), model.uiState.value.items)
            assertEquals(true, model.uiState.value.inClique)
        } finally {
            VaultCatalogSync.endAccountCleanup()
        }
    }

    @Test
    fun `stale positive load cannot repopulate the shared cache after cleanup`() = runTest(dispatcher) {
        val oldItem = VaultItemUi("old", VaultCategory.Passwords, "old.example", "old-user")
        val liveOldItem = VaultItemUi("live-old", VaultCategory.Passwords, "old-live.example", "old-user")
        val cache = VaultCacheStore().apply {
            inClique = true
            itemsByCategory = mapOf(VaultCategory.Passwords to listOf(oldItem))
        }
        val listing = CompletableDeferred<Unit>()
        val port = GatedPasswordsPort(FakePasswordsPort(items = listOf(liveOldItem)), listing)
        PasswordsViewModel(port, cache)
        runCurrent()

        VaultCatalogSync.beginAccountCleanup()
        try {
            cache.clearMemory()
            listing.complete(Unit)
            advanceUntilIdle()

            assertEquals(null, cache.inClique)
            assertTrue(cache.itemsByCategory.isEmpty())
            assertEquals(null, cache.groups)
        } finally {
            VaultCatalogSync.endAccountCleanup()
        }
    }

    @Test
    fun `a completed load asks the credential-provider catalog to re-read the vault`() = runTest(dispatcher) {
        var refreshes = 0
        val port = FakePasswordsPort()
        PasswordsViewModel(port, VaultCacheStore(requestCatalogRefresh = { refreshes += 1 }))
        advanceUntilIdle()

        // One post-sync snapshot is shared with the credential-provider
        // invalidation instead of scheduling a second redundant refresh.
        assertEquals(1, refreshes)
    }

    @Test
    fun `declining an invite removes it`() = runTest(dispatcher) {
        val port = FakePasswordsPort(invites = listOf(VaultInviteUi("inv", "Family", "alice@example.com")))
        val model = PasswordsViewModel(port)
        advanceUntilIdle()
        model.setCategory(VaultCategory.Groups)
        advanceUntilIdle()

        model.declineInvite("inv")
        advanceUntilIdle()

        assertEquals(emptyList<String>(), model.uiState.value.invites.map { it.id })
    }

    @Test
    fun `password draft survives failed writes and stays open until a successful retry`() = runTest(dispatcher) {
        val operation = CompletableDeferred<Unit>()
        val delegate = FakePasswordsPort()
        val port = ControlledVaultMutationPort(delegate, failure = "Apple is offline", gate = operation)
        val model = PasswordsViewModel(port)
        advanceUntilIdle()

        model.prepareCreatePassword()
        val draft = VaultPasswordDraft(
            site = "example.com",
            username = "ada",
            password = "generated-secret",
            passwordVisible = true,
        )
        model.updatePasswordDraft(draft)
        model.createPassword(draft.site, draft.username, draft.password, draft.groupId)
        runCurrent()

        assertEquals(true, model.uiState.value.busy)
        assertEquals(draft, model.uiState.value.composer?.passwordDraft)
        model.dismissComposer()
        assertEquals(draft, model.uiState.value.composer?.passwordDraft)

        operation.complete(Unit)
        advanceUntilIdle()
        assertEquals(false, model.uiState.value.busy)
        assertEquals(draft, model.uiState.value.composer?.passwordDraft)
        assertEquals("Apple is offline", model.uiState.value.composer?.error)
        assertEquals(null, model.uiState.value.error)

        port.failure = null
        port.gate = null
        model.createPassword(draft.site, draft.username, draft.password, draft.groupId)
        advanceUntilIdle()

        assertEquals(null, model.uiState.value.composer)
        assertEquals(listOf("example.com"), delegate.items.map { it.title })
    }

    @Test
    fun `closing the password editor discards its sensitive memory-only draft`() = runTest(dispatcher) {
        val model = PasswordsViewModel(FakePasswordsPort())
        advanceUntilIdle()

        model.prepareCreatePassword()
        model.updatePasswordDraft(VaultPasswordDraft(site = "example.com", password = "must-not-survive"))
        assertEquals("must-not-survive", model.uiState.value.composer?.passwordDraft?.password)

        model.dismissComposer()
        assertEquals(null, model.uiState.value.composer)

        model.prepareCreatePassword()
        assertEquals("", model.uiState.value.composer?.passwordDraft?.password)
    }

    @Test
    fun `group creation failure retains the typed name for an inline retry`() = runTest(dispatcher) {
        val delegate = FakePasswordsPort()
        val port = ControlledVaultMutationPort(delegate, failure = "Group creation failed")
        val model = PasswordsViewModel(port)
        advanceUntilIdle()

        model.openCreateGroup()
        model.updateGroupDraft("Family")
        model.createGroup("Family")
        advanceUntilIdle()

        assertEquals(VaultComposerKind.CreateGroup, model.uiState.value.composer?.kind)
        assertEquals("Family", model.uiState.value.composer?.groupDraft)
        assertEquals("Group creation failed", model.uiState.value.composer?.error)

        port.failure = null
        model.createGroup(model.uiState.value.composer?.groupDraft.orEmpty())
        advanceUntilIdle()

        assertEquals(null, model.uiState.value.composer)
        assertEquals(listOf("Family"), delegate.groups.map { it.name })
    }

    @Test
    fun `invitation remains actionable after a failed response and closes on success`() = runTest(dispatcher) {
        val invite = VaultInviteUi("inv", "Family", "alice@example.com")
        val delegate = FakePasswordsPort(invites = listOf(invite))
        val port = ControlledVaultMutationPort(delegate, failure = "Invitation response failed")
        val model = PasswordsViewModel(port)
        advanceUntilIdle()

        model.openInvite(invite)
        model.acceptInvite(invite.id)
        advanceUntilIdle()

        assertEquals(invite, model.uiState.value.composer?.invite)
        assertEquals("Invitation response failed", model.uiState.value.composer?.error)

        port.failure = null
        model.acceptInvite(invite.id)
        advanceUntilIdle()

        assertEquals(null, model.uiState.value.composer)
        assertTrue(delegate.invites.isEmpty())
    }

    @Test
    fun `an edit-bus bump reloads the visible category`() = runTest(dispatcher) {
        val item = VaultItemUi("1", VaultCategory.Passwords, "example.com", "alice")
        val port = FakePasswordsPort(items = listOf(item))
        val model = PasswordsViewModel(port)
        advanceUntilIdle()
        assertEquals(listOf(item), model.uiState.value.items)

        // A detail page deleted the item and announced the change.
        port.items = emptyList()
        VaultEditBus.notifyChanged()
        advanceUntilIdle()

        assertEquals(emptyList<VaultItemUi>(), model.uiState.value.items)
        assertEquals(0, model.uiState.value.categoryCounts[VaultCategory.Passwords])
    }
}

package app.openbubbles.nativeapp.ui.passwords

import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain

@OptIn(ExperimentalCoroutinesApi::class)
class PasswordsViewModelTest {
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
    fun `initial refresh loads everything, syncs, then reloads`() = runTest(dispatcher) {
        val synced = VaultItemUi("remote", VaultCategory.Passwords, "example.com", "alice")
        val port = FakePasswordsPort(syncedItems = listOf(synced))

        val model = PasswordsViewModel(port)
        advanceUntilIdle()

        assertEquals(1, port.syncCount)
        assertEquals(listOf(synced), model.uiState.value.items)
        // Two eager rounds over the four item categories: cold, then post-sync.
        assertEquals(8, port.itemListRequests.size)
        assertEquals(2, port.groupListCount)
        assertEquals(2, port.inviteListCount)
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

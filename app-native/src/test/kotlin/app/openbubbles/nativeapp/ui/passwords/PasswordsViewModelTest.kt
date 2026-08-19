package app.openbubbles.nativeapp.ui.passwords

import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
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
    fun `initial refresh shows cached category then refreshes it after sync`() = runTest(dispatcher) {
        val synced = VaultItemUi("remote", VaultCategory.Passwords, "example.com", "alice")
        val port = FakePasswordsPort(syncedItems = listOf(synced))

        val model = PasswordsViewModel(port)
        advanceUntilIdle()

        assertEquals(1, port.syncCount)
        assertEquals(listOf(synced), model.uiState.value.items)
        assertEquals(listOf(VaultCategory.Passwords, VaultCategory.Passwords), port.itemListRequests)
        assertEquals(0, port.groupListCount)
        assertEquals(0, port.inviteListCount)
    }

    @Test
    fun `categories load on demand instead of loading the entire vault`() = runTest(dispatcher) {
        val password = VaultItemUi("password", VaultCategory.Passwords, "example.com", "alice")
        val wifi = VaultItemUi("wifi", VaultCategory.Wifi, "Home Wi-Fi")
        val port = FakePasswordsPort(
            items = listOf(password, wifi),
            groups = listOf(VaultGroupUi("family", "Family", true, 2)),
        )
        val model = PasswordsViewModel(port)
        advanceUntilIdle()

        assertEquals(listOf(VaultCategory.Passwords, VaultCategory.Passwords), port.itemListRequests)
        assertEquals(listOf(password), model.uiState.value.items)

        model.setCategory(VaultCategory.Wifi)
        advanceUntilIdle()

        assertEquals(
            listOf(VaultCategory.Passwords, VaultCategory.Passwords, VaultCategory.Wifi),
            port.itemListRequests,
        )
        assertEquals(listOf(wifi), model.uiState.value.items)
        assertEquals(0, port.groupListCount)

        model.setCategory(VaultCategory.Groups)
        advanceUntilIdle()

        assertEquals(1, port.groupListCount)
        assertEquals(1, port.inviteListCount)
        assertEquals(1, model.uiState.value.categoryCounts[VaultCategory.Groups])
    }

    @Test
    fun `deleting the selected item removes it and clears the selection`() = runTest(dispatcher) {
        val keep = VaultItemUi("keep", VaultCategory.Passwords, "keep.example", "alice")
        val drop = VaultItemUi("drop", VaultCategory.Passwords, "drop.example", "bob")
        val port = FakePasswordsPort(items = listOf(keep, drop))
        val model = PasswordsViewModel(port)
        advanceUntilIdle()

        model.select(drop)
        model.deleteSelected()
        advanceUntilIdle()

        assertEquals(listOf("keep"), model.uiState.value.items.map { it.id })
        assertNull(model.uiState.value.selected)
    }

    @Test
    fun `adding a verification code records setup and opens codes`() = runTest(dispatcher) {
        val password = VaultItemUi("password", VaultCategory.Passwords, "example.com", "alice")
        val port = FakePasswordsPort(items = listOf(password))
        val model = PasswordsViewModel(port)
        advanceUntilIdle()

        model.addTotp(password, "JBSW Y3DP EHPK 3PXP")
        advanceUntilIdle()

        assertEquals(listOf(password to "JBSW Y3DP EHPK 3PXP"), port.totpSetups)
        assertEquals(VaultCategory.Codes, model.uiState.value.category)
        assertEquals(listOf("code-password"), model.uiState.value.items.map { it.id })
    }

    @Test
    fun `renaming a group updates its name`() = runTest(dispatcher) {
        val port = FakePasswordsPort(groups = listOf(VaultGroupUi("family", "Family", true, 2)))
        val model = PasswordsViewModel(port)
        advanceUntilIdle()
        model.setCategory(VaultCategory.Groups)
        advanceUntilIdle()

        model.renameGroup("family", "Household")
        advanceUntilIdle()

        assertEquals(listOf("Household"), model.uiState.value.groups.map { it.name })
    }

    @Test
    fun `deleting a group removes it`() = runTest(dispatcher) {
        val port = FakePasswordsPort(groups = listOf(VaultGroupUi("family", "Family", true, 2)))
        val model = PasswordsViewModel(port)
        advanceUntilIdle()
        model.setCategory(VaultCategory.Groups)
        advanceUntilIdle()

        model.deleteGroup("family")
        advanceUntilIdle()

        assertEquals(emptyList<String>(), model.uiState.value.groups.map { it.id })
    }

    @Test
    fun `inviting a group member refreshes pending membership`() = runTest(dispatcher) {
        val owner = VaultGroupMemberUi("Alice", "mailto:alice@example.com", true, true)
        val port = FakePasswordsPort(groups = listOf(VaultGroupUi("family", "Family", true, 1, listOf(owner))))
        val model = PasswordsViewModel(port)
        advanceUntilIdle()
        model.setCategory(VaultCategory.Groups)
        advanceUntilIdle()

        model.inviteGroupMember("family", "bob@example.com")
        advanceUntilIdle()

        assertEquals(listOf("family" to "bob@example.com"), port.groupInvites)
        assertEquals(2, model.uiState.value.groups.single().memberCount)
        assertEquals(false, model.uiState.value.groups.single().members.last().joined)
    }

    @Test
    fun `removing a group member refreshes membership`() = runTest(dispatcher) {
        val owner = VaultGroupMemberUi("Alice", "mailto:alice@example.com", true, true)
        val member = VaultGroupMemberUi("Bob", "mailto:bob@example.com", true, false)
        val port = FakePasswordsPort(groups = listOf(VaultGroupUi("family", "Family", true, 2, listOf(owner, member))))
        val model = PasswordsViewModel(port)
        advanceUntilIdle()
        model.setCategory(VaultCategory.Groups)
        advanceUntilIdle()

        model.removeGroupMember("family", member.handle)
        advanceUntilIdle()

        assertEquals(listOf("family" to member.handle), port.groupRemovals)
        assertEquals(listOf(owner), model.uiState.value.groups.single().members)
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
}

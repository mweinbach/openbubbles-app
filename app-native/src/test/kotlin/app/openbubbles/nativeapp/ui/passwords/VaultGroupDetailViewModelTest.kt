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
class VaultGroupDetailViewModelTest {
    private val dispatcher = StandardTestDispatcher()

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun model(port: FakePasswordsPort) = VaultGroupDetailViewModel(port, "family", "Family")

    @Test
    fun `loads the group's roster on open`() = runTest(dispatcher) {
        val owner = VaultGroupMemberUi("Alice", "mailto:alice@example.com", true, true)
        val port = FakePasswordsPort(groups = listOf(VaultGroupUi("family", "Family", true, 1, listOf(owner))))

        val model = model(port)
        advanceUntilIdle()

        assertEquals(listOf(owner), model.uiState.value.group?.members)
        assertEquals(false, model.uiState.value.loading)
    }

    @Test
    fun `renaming updates the shown name`() = runTest(dispatcher) {
        val port = FakePasswordsPort(groups = listOf(VaultGroupUi("family", "Family", true, 2)))
        val model = model(port)
        advanceUntilIdle()

        model.rename("Household")
        advanceUntilIdle()

        assertEquals("Household", model.uiState.value.name)
        assertEquals("Household", model.uiState.value.group?.name)
    }

    @Test
    fun `inviting a member refreshes pending membership`() = runTest(dispatcher) {
        val owner = VaultGroupMemberUi("Alice", "mailto:alice@example.com", true, true)
        val port = FakePasswordsPort(groups = listOf(VaultGroupUi("family", "Family", true, 1, listOf(owner))))
        val model = model(port)
        advanceUntilIdle()

        model.inviteMember("bob@example.com")
        advanceUntilIdle()

        assertEquals(listOf("family" to "bob@example.com"), port.groupInvites)
        assertEquals(2, model.uiState.value.group?.memberCount)
        assertEquals(false, model.uiState.value.group?.members?.last()?.joined)
    }

    @Test
    fun `removing a member refreshes the roster`() = runTest(dispatcher) {
        val owner = VaultGroupMemberUi("Alice", "mailto:alice@example.com", true, true)
        val member = VaultGroupMemberUi("Bob", "mailto:bob@example.com", true, false)
        val port = FakePasswordsPort(groups = listOf(VaultGroupUi("family", "Family", true, 2, listOf(owner, member))))
        val model = model(port)
        advanceUntilIdle()

        model.removeMember(member.handle)
        advanceUntilIdle()

        assertEquals(listOf("family" to member.handle), port.groupRemovals)
        assertEquals(listOf(owner), model.uiState.value.group?.members)
    }

    @Test
    fun `deleting the group closes the page`() = runTest(dispatcher) {
        val port = FakePasswordsPort(groups = listOf(VaultGroupUi("family", "Family", true, 2)))
        val model = model(port)
        advanceUntilIdle()

        model.deleteOrLeave()
        advanceUntilIdle()

        assertEquals(emptyList<String>(), port.groups.map { it.id })
        assertEquals(true, model.uiState.value.closed)
    }
}

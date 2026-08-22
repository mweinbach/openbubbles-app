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

private class ControlledGroupMutationPort(
    private val delegate: FakePasswordsPort,
    var failure: String? = null,
) : PasswordsPort by delegate {
    override suspend fun renameGroup(id: String, name: String) {
        failure?.let { error(it) }
        delegate.renameGroup(id, name)
    }

    override suspend fun inviteGroupMember(id: String, handle: String) {
        failure?.let { error(it) }
        delegate.inviteGroupMember(id, handle)
    }
}

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

    private fun model(port: PasswordsPort) = VaultGroupDetailViewModel(port, "family", "Family")

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
    fun `failed rename preserves the edited name until a successful retry`() = runTest(dispatcher) {
        val delegate = FakePasswordsPort(groups = listOf(VaultGroupUi("family", "Family", true, 2)))
        val port = ControlledGroupMutationPort(delegate, failure = "Rename failed")
        val model = model(port)
        advanceUntilIdle()

        model.openRenameEditor()
        assertEquals("Family", model.uiState.value.editor?.value)
        model.updateEditor("Household")
        model.rename("Household")
        advanceUntilIdle()

        assertEquals(VaultGroupEditorKind.Rename, model.uiState.value.editor?.kind)
        assertEquals("Household", model.uiState.value.editor?.value)
        assertEquals("Rename failed", model.uiState.value.editor?.error)
        assertEquals(null, model.uiState.value.error)

        port.failure = null
        model.rename(model.uiState.value.editor?.value.orEmpty())
        advanceUntilIdle()

        assertEquals(null, model.uiState.value.editor)
        assertEquals("Household", model.uiState.value.name)
    }

    @Test
    fun `failed invitation preserves its typed handle until the request succeeds`() = runTest(dispatcher) {
        val delegate = FakePasswordsPort(groups = listOf(VaultGroupUi("family", "Family", true, 1)))
        val port = ControlledGroupMutationPort(delegate, failure = "Invite failed")
        val model = model(port)
        advanceUntilIdle()

        model.openInviteEditor()
        model.updateEditor("bob@example.com")
        model.inviteMember("bob@example.com")
        advanceUntilIdle()

        assertEquals(VaultGroupEditorKind.Invite, model.uiState.value.editor?.kind)
        assertEquals("bob@example.com", model.uiState.value.editor?.value)
        assertEquals("Invite failed", model.uiState.value.editor?.error)

        port.failure = null
        model.inviteMember(model.uiState.value.editor?.value.orEmpty())
        advanceUntilIdle()

        assertEquals(null, model.uiState.value.editor)
        assertEquals(listOf("family" to "bob@example.com"), delegate.groupInvites)
    }

    @Test
    fun `dismissing a group editor clears its in-memory draft`() = runTest(dispatcher) {
        val port = FakePasswordsPort(groups = listOf(VaultGroupUi("family", "Family", true, 1)))
        val model = model(port)
        advanceUntilIdle()

        model.openInviteEditor()
        model.updateEditor("bob@example.com")
        model.dismissEditor()

        assertEquals(null, model.uiState.value.editor)
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

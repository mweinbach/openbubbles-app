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
class VaultItemDetailViewModelTest {
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
    fun `reveal populates the secret and its expiry`() = runTest(dispatcher) {
        val port = FakePasswordsPort(secret = "hunter2" to 1_234L)
        val item = VaultItemUi("1", VaultCategory.Passwords, "example.com", "alice")

        val model = VaultItemDetailViewModel(port, item)
        model.reveal()
        advanceUntilIdle()

        assertEquals("hunter2", model.uiState.value.secret)
        assertEquals(1_234L, model.uiState.value.secretExpiresAtSeconds)
        assertNull(model.uiState.value.error)
    }

    @Test
    fun `group name resolves from the item's group id`() = runTest(dispatcher) {
        val port = FakePasswordsPort(groups = listOf(VaultGroupUi("family", "Family", true, 2)))
        val item = VaultItemUi("1", VaultCategory.Passwords, "example.com", "alice", groupId = "family")

        val model = VaultItemDetailViewModel(port, item)
        advanceUntilIdle()

        assertEquals("Family", model.uiState.value.groupName)
    }

    @Test
    fun `personal items never look up groups`() = runTest(dispatcher) {
        val port = FakePasswordsPort()
        val model = VaultItemDetailViewModel(port, VaultItemUi("1", VaultCategory.Passwords, "example.com"))
        advanceUntilIdle()

        assertEquals(0, port.groupListCount)
        assertNull(model.uiState.value.groupName)
    }

    @Test
    fun `passkeys never reveal`() = runTest(dispatcher) {
        val port = FakePasswordsPort(secret = "private-key" to null)
        val model = VaultItemDetailViewModel(port, VaultItemUi("1", VaultCategory.Passkeys, "example.com"))

        model.reveal()
        advanceUntilIdle()

        assertNull(model.uiState.value.secret)
    }

    @Test
    fun `deleting the item removes it and flags the page for closure`() = runTest(dispatcher) {
        val keep = VaultItemUi("keep", VaultCategory.Passwords, "keep.example", "alice")
        val drop = VaultItemUi("drop", VaultCategory.Passwords, "drop.example", "bob")
        val port = FakePasswordsPort(items = listOf(keep, drop))

        val model = VaultItemDetailViewModel(port, drop)
        model.delete()
        advanceUntilIdle()

        assertEquals(listOf("keep"), port.items.map { it.id })
        assertEquals(true, model.uiState.value.deleted)
    }

    @Test
    fun `adding a verification code records the trimmed setup`() = runTest(dispatcher) {
        val password = VaultItemUi("password", VaultCategory.Passwords, "example.com", "alice")
        val port = FakePasswordsPort(items = listOf(password))

        val model = VaultItemDetailViewModel(port, password)
        model.addTotp(" JBSWY3DPEHPK3PXP ")
        advanceUntilIdle()

        assertEquals(listOf(password to "JBSWY3DPEHPK3PXP"), port.totpSetups)
    }

    @Test
    fun `verification codes group into readable halves`() {
        assertEquals("123 456", formatVerificationCode("123456"))
        assertEquals("1234 5678", formatVerificationCode("12345678"))
        assertEquals("123 456 789", formatVerificationCode("123456789"))
        assertEquals("AB-12", formatVerificationCode("AB-12"))
    }
}

package app.openbubbles.nativeapp.ui.passwords

import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withContext

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
    fun `conceal immediately clears a revealed secret and expiry`() = runTest(dispatcher) {
        val port = FakePasswordsPort(secret = "hunter2" to 1_234L)
        val model = VaultItemDetailViewModel(
            port,
            VaultItemUi("1", VaultCategory.Passwords, "example.com"),
        )

        model.reveal()
        advanceUntilIdle()
        model.conceal()

        assertNull(model.uiState.value.secret)
        assertNull(model.uiState.value.secretExpiresAtSeconds)
        assertFalse(model.uiState.value.busy)
    }

    @Test
    fun `a cancelled noncooperative reveal cannot publish after conceal`() = runTest(dispatcher) {
        val lateSecret = CompletableDeferred<Pair<String, Long?>>()
        val port = object : PasswordsPort by FakePasswordsPort() {
            override suspend fun reveal(item: VaultItemUi): Pair<String, Long?> =
                withContext(NonCancellable) { lateSecret.await() }
        }
        val model = VaultItemDetailViewModel(
            port,
            VaultItemUi("1", VaultCategory.Passwords, "example.com"),
        )

        model.reveal()
        runCurrent()
        assertTrue(model.uiState.value.busy)

        model.conceal()
        assertFalse(model.uiState.value.busy)
        assertNull(model.uiState.value.secret)

        lateSecret.complete("too-late" to 9_999L)
        advanceUntilIdle()

        assertNull(model.uiState.value.secret)
        assertNull(model.uiState.value.secretExpiresAtSeconds)
        assertFalse(model.uiState.value.busy)
    }

    @Test
    fun `an old reveal cannot replace a newly authenticated secret`() = runTest(dispatcher) {
        val lateSecret = CompletableDeferred<Pair<String, Long?>>()
        var revealCount = 0
        val port = object : PasswordsPort by FakePasswordsPort() {
            override suspend fun reveal(item: VaultItemUi): Pair<String, Long?> {
                revealCount += 1
                return if (revealCount == 1) {
                    withContext(NonCancellable) { lateSecret.await() }
                } else {
                    "fresh-secret" to 2_000L
                }
            }
        }
        val model = VaultItemDetailViewModel(
            port,
            VaultItemUi("1", VaultCategory.Passwords, "example.com"),
        )

        model.reveal()
        runCurrent()
        model.conceal()
        model.reveal()
        runCurrent()

        assertEquals("fresh-secret", model.uiState.value.secret)
        assertEquals(2_000L, model.uiState.value.secretExpiresAtSeconds)

        lateSecret.complete("stale-secret" to 1_000L)
        advanceUntilIdle()

        assertEquals("fresh-secret", model.uiState.value.secret)
        assertEquals(2_000L, model.uiState.value.secretExpiresAtSeconds)
        assertFalse(model.uiState.value.busy)
    }

    @Test
    fun `verification code refresh requires an existing revealed secret`() = runTest(dispatcher) {
        val port = FakePasswordsPort(secret = "123456" to 100L)
        val model = VaultItemDetailViewModel(
            port,
            VaultItemUi("1", VaultCategory.Codes, "example.com"),
        )

        model.refreshRevealedSecret()
        advanceUntilIdle()
        assertNull(model.uiState.value.secret)

        model.reveal()
        advanceUntilIdle()
        port.secret = "654321" to 200L
        model.refreshRevealedSecret()
        advanceUntilIdle()
        assertEquals("654321", model.uiState.value.secret)

        model.conceal()
        model.refreshRevealedSecret()
        advanceUntilIdle()

        assertNull(model.uiState.value.secret)
        assertNull(model.uiState.value.secretExpiresAtSeconds)
    }

    @Test
    fun `conceal does not cancel an in-flight account mutation`() = runTest(dispatcher) {
        val deleteGate = CompletableDeferred<Unit>()
        var deleted = false
        val port = object : PasswordsPort by FakePasswordsPort() {
            override suspend fun deleteItem(item: VaultItemUi) {
                deleteGate.await()
                deleted = true
            }
        }
        val model = VaultItemDetailViewModel(
            port,
            VaultItemUi("1", VaultCategory.Passwords, "example.com"),
        )

        model.delete()
        runCurrent()
        model.conceal()

        assertTrue(model.uiState.value.busy)
        assertFalse(deleted)

        deleteGate.complete(Unit)
        advanceUntilIdle()

        assertTrue(deleted)
        assertTrue(model.uiState.value.deleted)
        assertFalse(model.uiState.value.busy)
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

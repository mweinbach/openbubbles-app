package app.openbubbles.nativeapp.ui.passwords

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest

@OptIn(ExperimentalCoroutinesApi::class)
class VaultPrivacyTest {
    @Test
    fun `copied vault values are sensitive and expire after sixty seconds`() = runTest {
        val clipboard = FakeVaultClipboardStore()

        copySensitiveVaultValue(
            clipboard = clipboard,
            scope = this,
            value = "hunter2",
            ownerToken = "original-owner",
        )

        val copied = assertNotNull(clipboard.content)
        assertEquals("iCloud Password", copied.label)
        assertEquals("hunter2", copied.value)
        assertEquals("original-owner", copied.ownerToken)
        assertTrue(copied.sensitive)

        advanceTimeBy(VAULT_CLIPBOARD_CLEAR_DELAY_MILLIS - 1)
        runCurrent()
        assertEquals(copied, clipboard.content)

        advanceTimeBy(1)
        runCurrent()
        assertNull(clipboard.content)
        assertEquals(1, clipboard.clearCount)
    }

    @Test
    fun `expiry never clears a later user clipboard replacement`() = runTest {
        val clipboard = FakeVaultClipboardStore()
        copySensitiveVaultValue(clipboard, this, "vault-secret", ownerToken = "vault-owner")

        val replacement = VaultClipboardContent(
            label = "User text",
            value = "keep this",
            ownerToken = "another-app",
            sensitive = false,
        )
        clipboard.content = replacement

        advanceTimeBy(VAULT_CLIPBOARD_CLEAR_DELAY_MILLIS)
        runCurrent()

        assertEquals(replacement, clipboard.content)
        assertEquals(0, clipboard.clearCount)
    }

    @Test
    fun `older timers never clear a newer copy of the same password`() = runTest {
        val clipboard = FakeVaultClipboardStore()
        copySensitiveVaultValue(clipboard, this, "same-secret", ownerToken = "first-copy")
        advanceTimeBy(30_000L)
        runCurrent()

        copySensitiveVaultValue(clipboard, this, "same-secret", ownerToken = "second-copy")
        val newestCopy = assertNotNull(clipboard.content)

        advanceTimeBy(30_000L)
        runCurrent()
        assertEquals(newestCopy, clipboard.content)
        assertEquals(0, clipboard.clearCount)

        advanceTimeBy(30_000L)
        runCurrent()
        assertNull(clipboard.content)
        assertEquals(1, clipboard.clearCount)
    }

    @Test
    fun `expiry refuses to clear changed contents even when the owner token matches`() = runTest {
        val clipboard = FakeVaultClipboardStore()
        copySensitiveVaultValue(clipboard, this, "first-secret", ownerToken = "same-owner")
        val replacement = assertNotNull(clipboard.content).copy(value = "replacement-secret")
        clipboard.content = replacement

        advanceTimeBy(VAULT_CLIPBOARD_CLEAR_DELAY_MILLIS)
        runCurrent()

        assertEquals(replacement, clipboard.content)
        assertEquals(0, clipboard.clearCount)
    }

    private class FakeVaultClipboardStore : VaultClipboardStore {
        var content: VaultClipboardContent? = null
        var clearCount = 0

        override fun set(content: VaultClipboardContent) {
            this.content = content
        }

        override fun current(): VaultClipboardContent? = content

        override fun clear() {
            clearCount += 1
            content = null
        }
    }
}

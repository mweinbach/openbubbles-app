package app.openbubbles.nativeapp.ui

import app.openbubbles.nativeapp.ui.passwords.VaultCategory
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PasswordsSettingsNavigationTest {

    @Test
    fun `passwords pops only when settings is directly underneath`() {
        assertTrue(passwordsReturnsToSettings(listOf(ChatsKey, SettingsKey, PasswordsKey)))
        assertFalse(passwordsReturnsToSettings(listOf(ChatsKey, PasswordsKey)))
        assertFalse(passwordsReturnsToSettings(listOf(ChatsKey, SettingsKey)))
    }

    @Test
    fun `only vault destinations require secure windows`() {
        assertTrue(isSensitiveVaultDestination(PasswordsKey))
        assertTrue(
            isSensitiveVaultDestination(
                VaultItemKey("password", VaultCategory.Passwords, "example.com"),
            ),
        )
        assertTrue(
            isSensitiveVaultDestination(
                VaultItemKey("verification-code", VaultCategory.Codes, "example.com"),
            ),
        )
        assertTrue(isSensitiveVaultDestination(VaultGroupKey("family", "Family")))

        assertFalse(isSensitiveVaultDestination(ChatsKey))
        assertFalse(isSensitiveVaultDestination(SettingsKey))
        assertFalse(isSensitiveVaultDestination(PhotosKey))
        assertFalse(isSensitiveVaultDestination(FindMyKey))
        assertFalse(isSensitiveVaultDestination(null))
    }
}

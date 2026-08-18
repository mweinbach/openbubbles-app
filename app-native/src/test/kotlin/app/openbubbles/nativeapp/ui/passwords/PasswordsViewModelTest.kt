package app.openbubbles.nativeapp.ui.passwords

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.test.runTest

class PasswordsViewModelTest {
    @Test
    fun `fake port lists and search filters passwords`() = runTest {
        val port = FakePasswordsPort(
            items = listOf(
                VaultItemUi("1", VaultCategory.Passwords, "example.com", "alice"),
                VaultItemUi("2", VaultCategory.Passwords, "work.test", "bob"),
                VaultItemUi("3", VaultCategory.Wifi, "Home Wi-Fi"),
            ),
        )

        val items = port.listItems()

        assertEquals(listOf("example.com"), filterVaultItems(items, VaultCategory.Passwords, "ali").map { it.title })
        assertEquals(listOf("Home Wi-Fi"), filterVaultItems(items, VaultCategory.Wifi, "home").map { it.title })
    }

    @Test
    fun `fake port create preserves selected group`() = runTest {
        val port = FakePasswordsPort()

        port.createPassword("example.com", "alice", "secret", "family")

        assertEquals("family", port.listItems().single().groupId)
    }
}

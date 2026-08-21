package app.openbubbles.nativeapp.ui

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
}

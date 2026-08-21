package app.openbubbles.nativeapp.data.passwords

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class VaultCatalogKeystoreKeysTest {
    @Test
    fun `key destruction attempts every alias and propagates failures`() {
        val attempted = mutableListOf<String>()
        val failure = assertFailsWith<IllegalStateException> {
            destroyVaultAliases(listOf("data", "index")) { alias ->
                attempted += alias
                error("failed $alias")
            }
        }

        assertEquals(listOf("data", "index"), attempted)
        assertEquals("failed data", failure.message)
        assertEquals(listOf("failed index"), failure.suppressed.map { it.message })
    }
}

package app.openbubbles.nativeapp.data.passwords

import java.security.MessageDigest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class VaultSqliteCatalogSchemaTest {
    @Test
    fun versionOneSchemaIsPinned() {
        assertEquals(1, VaultSqliteCatalog.DATABASE_VERSION)
        assertEquals(
            "2e40b189cda4802de1ea399ad33f3977019d8817cca56b39b0bb79f8b7bf87ae",
            VaultSqliteCatalog.CREATE_STATEMENTS.joinToString("\n").sha256(),
        )
    }

    @Test
    fun versionBumpCannotSilentlySkipAMigration() {
        assertEquals(emptyList(), VaultSqliteCatalog.migrationStatements(1, 1))
        assertFailsWith<IllegalStateException> { VaultSqliteCatalog.migrationStatements(1, 2) }
    }

    @Test
    fun accountCleanupCoversEveryVaultTableIncludingSyncMarkers() {
        assertEquals(
            listOf(
                "vault_group_members",
                "vault_groups",
                "vault_invites",
                "vault_items",
                "vault_sync_state",
            ),
            VaultSqliteCatalog.ACCOUNT_CLEAR_TABLES,
        )
    }

    @Test
    fun theSiteLookupColumnIsIndexedAndNoSecretColumnExists() {
        val schema = VaultSqliteCatalog.CREATE_STATEMENTS.joinToString("\n")
        assertTrue("CREATE INDEX vault_items_site_idx ON vault_items(kind, site_index)" in schema)
        // The catalog is metadata only. A column that could hold a secret would
        // move the whole security boundary off the Rust keychain state.
        listOf("password", "secret", "private_key", "totp", "seed").forEach { forbidden ->
            assertTrue(forbidden !in schema, "vault catalog schema must not declare a '$forbidden' column")
        }
    }

    private fun String.sha256(): String = MessageDigest.getInstance("SHA-256")
        .digest(toByteArray(Charsets.UTF_8))
        .joinToString(separator = "") { byte -> "%02x".format(byte.toInt() and 0xff) }
}

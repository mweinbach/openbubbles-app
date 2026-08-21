package app.openbubbles.nativeapp.data.passwords

import java.security.MessageDigest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class VaultSqliteCatalogSchemaTest {
    @Test
    fun versionTwoSchemaIsPinned() {
        assertEquals(2, VaultSqliteCatalog.DATABASE_VERSION)
        assertEquals(
            "ba8f1620bda06dbc0a6b7f12bf56939989451a99eba4eacf098cf74cf3adef36",
            VaultSqliteCatalog.CREATE_STATEMENTS.joinToString("\n").sha256(),
        )
    }

    @Test
    fun versionBumpCannotSilentlySkipAMigration() {
        assertEquals(emptyList(), VaultSqliteCatalog.migrationStatements(2, 2))
        val migration = VaultSqliteCatalog.migrationStatements(1, 2).joinToString("\n")
        assertTrue("DROP TABLE vault_items" in migration)
        assertTrue("PRIMARY KEY (record_id, kind)" in migration)
        assertTrue("DELETE FROM vault_sync_state" in migration)
        assertTrue("CREATE TABLE vault_key_state" in migration)
        assertFailsWith<IllegalStateException> { VaultSqliteCatalog.migrationStatements(2, 3) }
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
                "vault_key_state",
            ),
            VaultSqliteCatalog.ACCOUNT_CLEAR_TABLES,
        )
    }

    @Test
    fun theSiteLookupColumnIsIndexedAndNoSecretColumnExists() {
        val schema = VaultSqliteCatalog.CREATE_STATEMENTS.joinToString("\n")
        assertTrue("CREATE INDEX vault_items_site_idx ON vault_items(kind, site_index)" in schema)
        assertTrue("PRIMARY KEY (record_id, kind)" in schema)
        assertTrue("CREATE TABLE vault_key_state" in schema)
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

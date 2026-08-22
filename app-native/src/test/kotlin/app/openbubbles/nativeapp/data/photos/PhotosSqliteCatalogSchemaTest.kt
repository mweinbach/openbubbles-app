package app.openbubbles.nativeapp.data.photos

import java.security.MessageDigest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class PhotosSqliteCatalogSchemaTest {
    @Test
    fun versionTwoSchemaIsPinned() {
        assertEquals(2, PhotosSqliteCatalog.DATABASE_VERSION)
        assertEquals(
            "ff74808d261ce6a0314451f7ecf0fc35f491efe6edfe7c319bfc579a62ea27c5",
            PhotosSqliteCatalog.CREATE_STATEMENTS.joinToString("\n").sha256(),
        )
    }

    @Test
    fun existingTransfersMigrateToManualWithoutAutomaticUploadConsent() {
        assertEquals(
            listOf("ALTER TABLE photo_transfers ADD COLUMN origin TEXT NOT NULL DEFAULT 'Manual'"),
            PhotosSqliteCatalog.migrationStatements(1, 2),
        )
    }

    @Test
    fun versionBumpCannotSilentlySkipAMigration() {
        assertEquals(
            emptyList(),
            PhotosSqliteCatalog.migrationStatements(1, 1),
        )
        assertEquals(
            emptyList(),
            PhotosSqliteCatalog.migrationStatements(2, 2),
        )
        assertFailsWith<IllegalStateException> {
            PhotosSqliteCatalog.migrationStatements(2, 3)
        }
        assertFailsWith<IllegalStateException> {
            PhotosSqliteCatalog.migrationStatements(1, 3)
        }
    }

    @Test
    fun accountCleanupCoversTransfersMetadataAndCursorState() {
        assertEquals(
            listOf("photo_transfers", "photo_assets", "photo_sync_state"),
            PhotosSqliteCatalog.ACCOUNT_CLEAR_TABLES,
        )
    }

    private fun String.sha256(): String = MessageDigest.getInstance("SHA-256")
        .digest(toByteArray(Charsets.UTF_8))
        .joinToString(separator = "") { byte -> "%02x".format(byte.toInt() and 0xff) }
}

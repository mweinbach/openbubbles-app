package app.openbubbles.nativeapp.data.photos

import java.security.MessageDigest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class PhotosSqliteCatalogSchemaTest {
    @Test
    fun versionOneSchemaIsPinned() {
        assertEquals(1, PhotosSqliteCatalog.DATABASE_VERSION)
        assertEquals(
            "3574f8071236464618b73d8cea748a4e5029432cd2c7373c3406234e29cdd358",
            PhotosSqliteCatalog.CREATE_STATEMENTS.joinToString("\n").sha256(),
        )
    }

    @Test
    fun versionBumpCannotSilentlySkipAMigration() {
        assertEquals(
            emptyList(),
            PhotosSqliteCatalog.migrationStatements(1, 1),
        )
        assertFailsWith<IllegalStateException> {
            PhotosSqliteCatalog.migrationStatements(1, 2)
        }
    }

    private fun String.sha256(): String = MessageDigest.getInstance("SHA-256")
        .digest(toByteArray(Charsets.UTF_8))
        .joinToString(separator = "") { byte -> "%02x".format(byte.toInt() and 0xff) }
}

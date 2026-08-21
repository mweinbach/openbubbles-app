package app.openbubbles.nativeapp.data.passwords

import android.content.Context
import app.openbubbles.core.passwords.AesGcmVaultFieldCrypto

/**
 * Process-wide handle for the durable vault catalog. The Passwords screen, the
 * Credential Manager provider, and the legacy Autofill service all read the
 * same open helper, which SQLite already serializes, instead of each opening
 * its own connection to the same file.
 */
object VaultCatalogStore {
    private val lock = Any()

    @Volatile private var keys: VaultCatalogKeystoreKeys? = null

    @Volatile private var catalog: VaultSqliteCatalog? = null

    fun of(context: Context): VaultSqliteCatalog = catalog ?: synchronized(lock) {
        catalog ?: run {
            val catalogKeys = keys ?: VaultCatalogKeystoreKeys().also { keys = it }
            VaultSqliteCatalog(context, AesGcmVaultFieldCrypto(catalogKeys)).also { catalog = it }
        }
    }

    /**
     * Closes the catalog and destroys its keys. Sign-out calls this after the
     * rows are deleted, so any page that outlived the delete is unreadable.
     */
    fun closeAndDestroyKeys() {
        val (openCatalog, openKeys) = synchronized(lock) {
            val pair = catalog to keys
            catalog = null
            keys = null
            pair
        }
        runCatching { openCatalog?.close() }
        openKeys?.destroy()
    }
}

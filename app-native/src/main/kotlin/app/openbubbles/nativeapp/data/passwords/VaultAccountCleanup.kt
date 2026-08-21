package app.openbubbles.nativeapp.data.passwords

import android.content.Context
import app.openbubbles.nativeapp.data.runAccountCleanupSteps
import app.openbubbles.nativeapp.ui.passwords.PasswordsViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * Exact local-state boundary for the cached iCloud Keychain catalog.
 *
 * Ordering matters: the refresh generation is invalidated and joined first so a
 * late listing cannot rewrite rows after the delete, then the rows go, then the
 * catalog keys are destroyed so any page the filesystem still holds cannot be
 * read by the next account.
 */
object VaultAccountCleanup {
    private val mutex = Mutex()

    suspend fun clear(context: Context): Result<Unit> = mutex.withLock {
        VaultCatalogSync.beginAccountCleanup()
        try {
            runAccountCleanupSteps(
                { PasswordsViewModel.clearSharedCacheForAccountCleanup() },
                {
                    withContext(Dispatchers.IO) {
                        VaultCatalogStore.of(context.applicationContext).clearAccountData()
                    }
                },
                { withContext(Dispatchers.IO) { VaultCatalogStore.closeAndDestroyKeys() } },
            )
        } finally {
            VaultCatalogSync.endAccountCleanup()
        }
    }
}

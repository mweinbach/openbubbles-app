package app.openbubbles.nativeapp.data.photos

import android.content.Context
import app.openbubbles.nativeapp.data.ICLOUD_PHOTOS_CACHE_ROOT
import app.openbubbles.nativeapp.data.clearOwnedAppleAccountRoot
import app.openbubbles.nativeapp.data.runAccountCleanupSteps
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/** Exact local-state boundary for the experimental personal Photos account. */
object PhotosAccountCleanup {
    private val mutex = Mutex()

    suspend fun clear(context: Context): Result<Unit> = mutex.withLock {
        withContext(Dispatchers.IO) {
            runAccountCleanupSteps(
                { PhotosWorkRegistry.cancelAndJoinAll() },
                { PhotosBackgroundSync.cancelAndAwait(context) },
                {
                    val grants = PhotoFolderSources(context).clearAccountState()
                    check(grants.complete) { "Could not release Photos folder access" }
                },
                {
                    val catalog = PhotosSqliteCatalog(context)
                    try {
                        catalog.clearAccountData()
                    } finally {
                        catalog.close()
                    }
                },
                {
                    val cache = clearOwnedAppleAccountRoot(
                        context.filesDir,
                        ICLOUD_PHOTOS_CACHE_ROOT,
                    )
                    check(cache.complete) { "Could not clear the iCloud Photos cache" }
                },
            )
        }
    }
}

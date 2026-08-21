package app.openbubbles.nativeapp.data

import java.io.File
import java.nio.file.Files
import java.nio.file.LinkOption

internal const val ICLOUD_CONTACT_AVATAR_ROOT = "icloud_contact_avatars"
internal const val ICLOUD_PHOTOS_CACHE_ROOT = "icloud_photos"
internal const val MAP_TILE_CACHE_ROOT = "map_tiles"

private val APPLE_ACCOUNT_CACHE_ROOTS = setOf(
    ICLOUD_CONTACT_AVATAR_ROOT,
    ICLOUD_PHOTOS_CACHE_ROOT,
)

internal data class OwnedRootCleanup(
    val deletedEntries: Int,
    val complete: Boolean,
)

/**
 * Deletes one whitelisted, app-owned child tree without following symlinks.
 * Nothing derived from a database row or content-provider path reaches this
 * boundary, so malformed persisted paths cannot widen sign-out cleanup.
 */
internal fun clearOwnedAppleAccountRoot(filesDir: File, rootName: String): OwnedRootCleanup {
    require(rootName in APPLE_ACCOUNT_CACHE_ROOTS) { "Unowned Apple-account cache root" }
    val root = File(filesDir, rootName)
    val expectedRoot = File(filesDir.canonicalFile, rootName).absoluteFile
    require(root.absoluteFile.parentFile == filesDir.absoluteFile) { "Cache root escaped filesDir" }

    val deleted = deleteOwnedTree(root, expectedRoot)
    val rootExists = Files.exists(root.toPath(), LinkOption.NOFOLLOW_LINKS)
    val complete = !rootExists || (
        !Files.isSymbolicLink(root.toPath()) && root.listFiles()?.isEmpty() == true
    )
    return OwnedRootCleanup(deletedEntries = deleted, complete = complete)
}

/** Deletes the allowlisted location-derived raster cache during Apple account teardown. */
internal fun clearOwnedMapTileRoot(cacheDir: File): OwnedRootCleanup {
    val root = File(cacheDir, MAP_TILE_CACHE_ROOT)
    val expectedRoot = File(cacheDir.canonicalFile, MAP_TILE_CACHE_ROOT).absoluteFile
    require(root.absoluteFile.parentFile == cacheDir.absoluteFile) { "Map cache root escaped cacheDir" }

    val deleted = deleteOwnedTree(root, expectedRoot)
    val rootExists = Files.exists(root.toPath(), LinkOption.NOFOLLOW_LINKS)
    val complete = !rootExists || (
        !Files.isSymbolicLink(root.toPath()) && root.listFiles()?.isEmpty() == true
    )
    return OwnedRootCleanup(deletedEntries = deleted, complete = complete)
}

private fun deleteOwnedTree(entry: File, expected: File): Int {
    val path = entry.toPath()
    if (!Files.exists(path, LinkOption.NOFOLLOW_LINKS)) return 0
    if (Files.isSymbolicLink(path)) {
        return if (runCatching { Files.deleteIfExists(path) }.getOrDefault(false)) 1 else 0
    }
    val canonical = runCatching { entry.canonicalFile }.getOrNull()

    // A symlink's canonical target differs from its expected position. Delete
    // only the link itself; never traverse into its target.
    if (canonical == null || canonical != expected) {
        return if (entry.delete()) 1 else 0
    }

    var deleted = 0
    if (entry.isDirectory) {
        val children = entry.listFiles() ?: return 0
        children.forEach { child ->
            deleted += deleteOwnedTree(child, File(expected, child.name).absoluteFile)
        }
    }
    if (entry.delete()) deleted += 1
    return deleted
}

/** Runs every cleanup step and returns one error with later failures suppressed. */
internal suspend fun runAccountCleanupSteps(
    vararg steps: suspend () -> Unit,
): Result<Unit> {
    var firstFailure: Throwable? = null
    steps.forEach { step ->
        try {
            step()
        } catch (failure: Throwable) {
            val first = firstFailure
            if (first == null) {
                firstFailure = failure
            } else {
                first.addSuppressed(failure)
            }
        }
    }
    return firstFailure?.let(Result.Companion::failure) ?: Result.success(Unit)
}

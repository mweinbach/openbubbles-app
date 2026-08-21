package app.openbubbles.nativeapp.data

import java.io.File
import java.io.FileOutputStream
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * One crash-safe lifecycle for local and Apple-synced chat background files.
 *
 * Network work is deliberately outside [fileLifecycleLock]. Callers serialize
 * an individual conversation with [withChatBackgroundLock], then enter this
 * short critical section only to stage a file, publish the database pointer,
 * and retire the previous owned file. This keeps unrelated MMCS downloads
 * concurrent while preventing two live/history paths from racing one chat.
 */
internal class ChatBackgroundStorage(private val filesDir: File) {

    val directory: File
        get() = File(filesDir, DIRECTORY_NAME).apply {
            check(isDirectory || mkdirs()) { "failed to create chat background directory" }
        }

    fun commitBytes(
        destinationName: String,
        bytes: ByteArray,
        previousPath: String?,
        persist: (File) -> Unit,
    ): File = synchronized(fileLifecycleLock) {
        commitReplacement(destinationName, previousPath, persist) { output ->
            output.write(bytes)
        }
    }

    fun commitFile(
        destinationName: String,
        source: File,
        previousPath: String?,
        persist: (File) -> Unit,
    ): File = synchronized(fileLifecycleLock) {
        require(source.isFile) { "background image is unavailable" }
        commitReplacement(destinationName, previousPath, persist) { output ->
            source.inputStream().use { input -> input.copyTo(output) }
        }
    }

    /** Publish a tombstone first, then retire the no-longer-referenced file. */
    fun commitRemoval(previousPath: String?, persist: () -> Unit) =
        synchronized(fileLifecycleLock) {
            persist()
            deleteOwned(previousPath, except = null)
        }

    /**
     * Removes only files whose names prove app ownership. Active database
     * paths and unknown files are preserved. Temp/tombstone/backup artifacts
     * from interrupted older writers are handled by the same pass.
     */
    fun reconcile(activePaths: () -> Collection<String?>): Int = synchronized(fileLifecycleLock) {
        val root = directory.canonicalFile
        // Evaluate the database snapshot only after entering the same lock as
        // commitReplacement. Otherwise a new path can publish between the
        // caller's query and this scan, then be mistaken for an orphan.
        val active = activePaths().mapNotNull { path ->
            path?.let(::File)?.runCatching { canonicalFile }?.getOrNull()
        }.toSet()
        var removed = 0
        root.listFiles().orEmpty().forEach { candidate ->
            val canonical = candidate.runCatching { canonicalFile }.getOrNull() ?: return@forEach
            if (canonical in active || !isOwnedName(candidate.name)) return@forEach
            if (candidate.isFile && candidate.delete()) removed++
        }
        removed
    }

    private fun commitReplacement(
        destinationName: String,
        previousPath: String?,
        persist: (File) -> Unit,
        write: (FileOutputStream) -> Unit,
    ): File {
        require(isFinalOwnedName(destinationName)) { "invalid background destination" }
        val root = directory.canonicalFile
        val destination = File(root, destinationName).canonicalFile
        require(destination.parentFile == root) { "background destination escapes owned directory" }
        val staged = File.createTempFile(STAGE_PREFIX, STAGE_SUFFIX, root)
        try {
            FileOutputStream(staged).use { output ->
                write(output)
                output.flush()
                output.fd.sync()
            }
            moveAtomically(staged, destination)
            try {
                persist(destination)
            } catch (error: Throwable) {
                runCatching { destination.delete() }
                throw error
            }
            deleteOwned(previousPath, except = destination)
            return destination
        } finally {
            runCatching { staged.delete() }
        }
    }

    private fun deleteOwned(path: String?, except: File?) {
        val candidate = path?.let(::File)?.runCatching { canonicalFile }?.getOrNull() ?: return
        val root = directory.canonicalFile
        if (candidate.parentFile == root && isOwnedName(candidate.name) && candidate != except?.canonicalFile) {
            runCatching { candidate.delete() }
        }
    }

    private fun moveAtomically(source: File, destination: File) {
        try {
            Files.move(
                source.toPath(),
                destination.toPath(),
                StandardCopyOption.ATOMIC_MOVE,
                StandardCopyOption.REPLACE_EXISTING,
            )
        } catch (_: AtomicMoveNotSupportedException) {
            Files.move(
                source.toPath(),
                destination.toPath(),
                StandardCopyOption.REPLACE_EXISTING,
            )
        }
    }

    private companion object {
        const val DIRECTORY_NAME = "chat_backgrounds"
        const val STAGE_PREFIX = ".ob-background-"
        const val STAGE_SUFFIX = ".tmp"
        val fileLifecycleLock = Any()

        val sharedName = Regex("^shared-[1-9][0-9]*-(?:[0-9]+|legacy)\\.img$")
        val localName = Regex(
            "^local-[1-9][0-9]*-[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-" +
                "[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}\\.[^/\\\\]{2,5}$",
        )
        val stageName = Regex("^\\.ob-background-[0-9]+\\.tmp$")
        val legacyStageName = Regex("^shared-[1-9][0-9]*-[0-9]+-[0-9]+\\.tmp$")

        fun isFinalOwnedName(name: String): Boolean =
            sharedName.matches(name) || localName.matches(name)

        fun isOwnedName(name: String): Boolean =
            isFinalOwnedName(name) ||
                stageName.matches(name) || legacyStageName.matches(name) ||
                listOf(".bak", ".backup", ".tombstone").any { suffix ->
                    name.endsWith(suffix) && isFinalOwnedName(name.removeSuffix(suffix))
                }
    }
}

private data class ChatBackgroundLockEntry(
    val mutex: Mutex = Mutex(),
    var users: Int = 0,
)

private object ChatBackgroundLocks {
    private val guard = Any()
    private val entries = HashMap<Long, ChatBackgroundLockEntry>()

    suspend fun <T> withLock(chatId: Long, block: suspend () -> T): T {
        val entry = synchronized(guard) {
            entries.getOrPut(chatId, ::ChatBackgroundLockEntry).also { it.users++ }
        }
        return try {
            entry.mutex.withLock { block() }
        } finally {
            synchronized(guard) {
                check(entry.users > 0)
                entry.users--
                if (entry.users == 0) entries.remove(chatId, entry)
            }
        }
    }

    fun size(): Int = synchronized(guard) { entries.size }
}

/** Shared by live push, history sync, migration, and local background actions. */
internal suspend fun <T> withChatBackgroundLock(chatId: Long, block: suspend () -> T): T =
    ChatBackgroundLocks.withLock(chatId, block)

internal fun activeChatBackgroundLockCountForTest(): Int = ChatBackgroundLocks.size()

package app.openbubbles.nativeapp.data

import android.content.Context
import app.openbubbles.core.sync.TranscriptBackgroundHandler
import app.openbubbles.core.sync.TranscriptBackgroundUpdate
import app.openbubbles.db.Chat
import io.objectbox.Box
import io.objectbox.BoxStore
import java.io.File
import java.io.FileOutputStream
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import uniffi.rust_lib_bluebubbles.NativePushState
import uniffi.rust_lib_bluebubbles.UPosterKind
import uniffi.rust_lib_bluebubbles.parsePoster

internal class TranscriptBackgroundStore(
    private val store: BoxStore,
    private val filesDir: File,
    private val cacheDir: File,
    /** Wallpaper image bytes for the poster payload; empty means "cleared". */
    private val loadImage: suspend (mmcsXml: String, payload: File) -> ByteArray,
) : TranscriptBackgroundHandler {

    constructor(
        context: Context,
        stateProvider: () -> NativePushState?,
    ) : this(
        store = requireNotNull(CoreGraph.store) { "message store unavailable" },
        filesDir = context.filesDir,
        cacheDir = context.cacheDir,
        loadImage = { mmcsXml, payload ->
            val state = stateProvider()
                ?: error("push state unavailable for transcript background")
            state.downloadMmcs(mmcsXml, payload.absolutePath, null)
            parsePoster(payload.readBytes()).use { poster ->
                when (poster.kind()) {
                    // Apple encodes "wallpaper cleared" as a dynamic or
                    // gradient poster set (the Dart client deleted the local
                    // poster for these kinds).
                    is UPosterKind.TranscriptDynamic,
                    is UPosterKind.TranscriptGradient,
                    -> ByteArray(0)
                    else -> poster.watch().backgroundImage
                }
            }
        },
    )

    override suspend fun apply(update: TranscriptBackgroundUpdate) = withContext(Dispatchers.IO) {
        writeMutex.withLock {
            val chatBox = store.boxFor(Chat::class.java)
            val initial = chatBox.get(update.chatId) ?: return@withLock
            if (hasApplied(initial, update)) {
                return@withLock
            }

            val directory = File(filesDir, "chat_backgrounds").apply {
                check(isDirectory || mkdirs()) { "failed to create transcript background directory" }
            }
            if (update.remove) {
                removeBackground(chatBox, initial, update.version, directory)
                return@withLock
            }

            val mmcsXml = requireNotNull(update.mmcsXml) {
                "transcript background has no MMCS payload"
            }
            val payload = File.createTempFile("transcript-background-", ".zip", cacheDir)
            var staged: File? = null
            try {
                val image = loadImage(mmcsXml, payload)

                val current = chatBox.get(update.chatId) ?: return@withLock
                if (hasApplied(current, update)) {
                    return@withLock
                }

                if (image.isEmpty()) {
                    // Dynamic/gradient posters (no wallpaper image) are how
                    // Apple clears a chat background — apply as a removal.
                    removeBackground(chatBox, current, update.version, directory)
                    return@withLock
                }

                staged = File.createTempFile(
                    "shared-${update.chatId}-${update.version}-",
                    ".tmp",
                    directory,
                )
                FileOutputStream(staged).use { output ->
                    output.write(image)
                    output.flush()
                    output.fd.sync()
                }
                val destination = File(directory, "shared-${update.chatId}-${update.version}.img")
                moveAtomically(staged, destination)
                staged = null

                val previous = current.transcriptPosterPath
                current.transcriptPosterPath = destination.absolutePath
                current.transcriptBackgroundVersion = update.version
                try {
                    chatBox.put(current)
                } catch (error: Throwable) {
                    runCatching { destination.delete() }
                    throw error
                }
                deleteOwnedBackground(previous, directory, destination)
            } finally {
                staged?.let { runCatching { it.delete() } }
                runCatching { payload.delete() }
            }
        }
    }

    private fun removeBackground(
        chatBox: Box<Chat>,
        chat: Chat,
        version: Long,
        directory: File,
    ) {
        val previous = chat.transcriptPosterPath
        chat.transcriptPosterPath = null
        chat.transcriptBackgroundVersion = version
        chatBox.put(chat)
        deleteOwnedBackground(previous, directory, null)
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

    private fun hasApplied(chat: Chat, update: TranscriptBackgroundUpdate): Boolean {
        val currentVersion = chat.transcriptBackgroundVersion ?: Long.MIN_VALUE
        if (currentVersion > update.version) return true
        if (currentVersion < update.version) return false
        return if (update.remove) {
            chat.transcriptPosterPath == null
        } else {
            chat.transcriptPosterPath?.let(::File)?.isFile == true
        }
    }

    private fun deleteOwnedBackground(path: String?, directory: File, except: File?) {
        val candidate = path?.let(::File)?.canonicalFile ?: return
        val root = directory.canonicalFile.toPath()
        if (candidate.toPath().startsWith(root) && candidate != except?.canonicalFile) {
            runCatching { candidate.delete() }
        }
    }

    private companion object {
        val writeMutex = Mutex()
    }
}

package app.openbubbles.nativeapp.data

import android.content.Context
import app.openbubbles.core.sync.TranscriptBackgroundHandler
import app.openbubbles.core.sync.TranscriptBackgroundUpdate
import app.openbubbles.db.Chat
import io.objectbox.Box
import io.objectbox.BoxStore
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import uniffi.rust_lib_bluebubbles.NativePushState
import uniffi.rust_lib_bluebubbles.UPosterKind
import uniffi.rust_lib_bluebubbles.parsePoster

internal const val MAX_TRANSCRIPT_BACKGROUND_PACKAGE_BYTES = 32L * 1024L * 1024L

internal fun supportedTranscriptBackgroundPackageSize(bytes: Long): Boolean =
    bytes in 1L..MAX_TRANSCRIPT_BACKGROUND_PACKAGE_BYTES

/** Rejects an oversized app-owned download before allocating its byte array for UniFFI. */
internal fun readTranscriptBackgroundPackage(payload: File): ByteArray {
    val declaredSize = payload.length()
    require(payload.isFile && supportedTranscriptBackgroundPackageSize(declaredSize)) {
        "transcript background package has an unsupported size"
    }
    return payload.readBytes().also { bytes ->
        check(bytes.size.toLong() == declaredSize) {
            "transcript background package changed while being read"
        }
    }
}

internal class TranscriptBackgroundStore(
    private val store: BoxStore,
    private val filesDir: File,
    private val cacheDir: File,
    /** Wallpaper image bytes for the poster payload; empty means "cleared". */
    private val loadImage: suspend (mmcsXml: String, payload: File) -> ByteArray,
) : TranscriptBackgroundHandler {

    private val backgrounds = ChatBackgroundStorage(filesDir)

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
            parsePoster(readTranscriptBackgroundPackage(payload)).use { poster ->
                val image = wallpaperBytesFromParsedPoster(poster)
                if (image.isEmpty() && poster.kind() !is UPosterKind.TranscriptDynamic &&
                    poster.kind() !is UPosterKind.TranscriptGradient
                ) {
                    // A photo/memoji/monogram poster with no watch image and
                    // no extractable layer is a parse failure, not Apple's
                    // "cleared" encoding. Throw so history sync can retry.
                    error("transcript poster has no wallpaper image")
                }
                image
            }
        },
    )

    /**
     * Rewrite Flutter-era poster prefixes (`avatars/you/poster-N`) to a
     * native watch-image file so the chat UI can decode them. Safe to call
     * more than once; chats whose path already is a regular file are left
     * alone.
     */
    suspend fun migrateLegacyPosters() = withContext(Dispatchers.IO) {
        val chatBox = store.boxFor(Chat::class.java)
        // Box.getAll() otherwise retains a reader transaction in this
        // Dispatchers.IO worker's thread-local cache. Keep the snapshot
        // transaction explicit so it is also closed on its creator
        // thread before a restore can close the process-wide store.
        store.callInReadTx { chatBox.all }
            .filter { chat ->
                chat.transcriptPosterPath?.let(::File)?.isFile == false
            }
            .forEach { snapshot ->
            withChatBackgroundLock(snapshot.id) {
                val chat = store.callInReadTx { chatBox.get(snapshot.id) } ?: return@withChatBackgroundLock
                val path = chat.transcriptPosterPath ?: return@withChatBackgroundLock
                if (File(path).isFile) return@withChatBackgroundLock
                val resolved = resolveBackgroundImageFile(path, ::extractWatchImageFromPosterSave)
                    ?: return@withChatBackgroundLock
                if (resolved.absolutePath == path) return@withChatBackgroundLock
                backgrounds.commitFile(
                    destinationName = "shared-${chat.id}-legacy.img",
                    source = resolved,
                    previousPath = path,
                ) { destination ->
                    chat.transcriptPosterPath = destination.absolutePath
                    chatBox.put(chat)
                }
            }
        }
        reconcileFiles(chatBox)
    }

    override suspend fun apply(update: TranscriptBackgroundUpdate) = withContext(Dispatchers.IO) {
        withChatBackgroundLock(update.chatId) {
            val chatBox = store.boxFor(Chat::class.java)
            val initial = store.callInReadTx { chatBox.get(update.chatId) } ?: return@withChatBackgroundLock
            if (hasApplied(initial, update)) {
                return@withChatBackgroundLock
            }

            if (update.remove) {
                removeBackground(chatBox, initial, update.version)
                return@withChatBackgroundLock
            }

            val mmcsXml = requireNotNull(update.mmcsXml) {
                "transcript background has no MMCS payload"
            }
            val payload = File.createTempFile("transcript-background-", ".zip", cacheDir)
            try {
                val image = loadImage(mmcsXml, payload)

                val current = store.callInReadTx { chatBox.get(update.chatId) }
                    ?: return@withChatBackgroundLock
                if (hasApplied(current, update)) {
                    return@withChatBackgroundLock
                }

                if (image.isEmpty()) {
                    // Dynamic/gradient posters (no wallpaper image) are how
                    // Apple clears a chat background — apply as a removal.
                    removeBackground(chatBox, current, update.version)
                    return@withChatBackgroundLock
                }

                val previous = current.transcriptPosterPath
                backgrounds.commitBytes(
                    destinationName = "shared-${update.chatId}-${update.version}.img",
                    bytes = image,
                    previousPath = previous,
                ) { destination ->
                    current.transcriptPosterPath = destination.absolutePath
                    current.transcriptBackgroundVersion = update.version
                    chatBox.put(current)
                }
            } finally {
                runCatching { payload.delete() }
            }
        }
    }

    private fun removeBackground(
        chatBox: Box<Chat>,
        chat: Chat,
        version: Long,
    ) {
        val previous = chat.transcriptPosterPath
        backgrounds.commitRemoval(previous) {
            chat.transcriptPosterPath = null
            chat.transcriptBackgroundVersion = version
            chatBox.put(chat)
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

    private fun reconcileFiles(chatBox: Box<Chat>) {
        backgrounds.reconcile {
            store.callInReadTx { chatBox.all }.flatMap { chat ->
                listOf(chat.customBackgroundPath, chat.transcriptPosterPath)
            }
        }
    }
}

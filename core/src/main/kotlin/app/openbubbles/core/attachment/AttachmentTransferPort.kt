package app.openbubbles.core.attachment

import app.openbubbles.db.Attachment
import app.openbubbles.db.Attachment_
import app.openbubbles.db.Message_
import io.objectbox.BoxStore
import io.objectbox.query.QueryBuilder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.transformWhile
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import java.io.File
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap

/** Concurrent MMCS transfers; each one parks an IO thread in blocking FFI. */
private const val MAX_CONCURRENT_TRANSFERS = 4

/**
 * Rust transfer seam. A parallel workstream is adding the actual
 * upload/download functions to the rust crate; this interface is the single
 * line of Kotlin they plug into:
 *
 * ```kotlin
 * AttachmentDownloader { guid, destPath, onProgress ->
 *     uniffi.rust_lib_bluebubbles.downloadAttachment(guid, destPath) { done, total ->
 *         onProgress(done, total)
 *     }.map { }.recoverCatching { ... }  // adapt the UniFFI surface
 * }
 * ```
 *
 * Contract: write the payload to [destPath] (already sanitized and parent
 * directories created by the caller), invoke [onProgress] with
 * `(bytesDone, bytesTotal)` as data arrives (total may be 0 when unknown),
 * and return success only after the file is complete on disk. Errors are
 * returned, never thrown.
 */
fun interface AttachmentDownloader {
    suspend fun download(
        attachmentGuid: String,
        destPath: String,
        onProgress: (Long, Long) -> Unit,
    ): Result<Unit>
}

/** States emitted by [AttachmentManager.download]. */
sealed class TransferState {
    /** Bytes transferred so far; [total] is 0 when the size is unknown. */
    data class Progress(val done: Long, val total: Long) : TransferState()

    /** Terminal success — file on disk, `isDownloaded` persisted. */
    object Done : TransferState()

    /** Terminal failure; nothing persisted, partial file removed. */
    data class Failed(val error: String) : TransferState()
}

/**
 * Orchestrates incoming attachment downloads against [AttachmentStore]'s
 * canonical layout and the [AttachmentDownloader] seam — the Kotlin
 * equivalent of Dart's `AttachmentDownloader` controller +
 * `AttachmentsService.getContent` auto-download path, minus the
 * image-conversion/thumbnail pipeline (later milestone).
 *
 * Semantics ported from Dart:
 * - disk presence beats the persisted `isDownloaded` flag (the flag can
 *   drift), so [download] short-circuits to [TransferState.Done] when the
 *   file already exists;
 * - one in-flight transfer per guid: concurrent callers share a single
 *   download and observe the same progress stream (Dart's controller map);
 * - staged outgoing attachments (`temp-…` guids) never download;
 * - on success the fresh DB row is marked downloaded with the real byte
 *   length; on failure the partial file is removed and the guid becomes
 *   downloadable again.
 */
class AttachmentManager(
    store: BoxStore,
    rootDir: File,
    private val downloader: AttachmentDownloader,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
) {
    private val disk = AttachmentStore(store, rootDir)
    private val attachmentBox = store.boxFor(Attachment::class.java)

    /** One live transfer per guid; late subscribers replay the latest state. */
    private class InFlight {
        val states = MutableStateFlow<TransferState?>(null)
    }

    private val inFlightGuard = Mutex()
    private val inFlight = ConcurrentHashMap<String, InFlight>()

    /**
     * The downloader occupies a thread for the entire network transfer (the
     * Rust call is blocking), and enqueue sites fan out a chat's whole
     * backlog at once. Without a cap that saturates the shared
     * [Dispatchers.IO] pool and every other IO-bound path in the app queues
     * behind attachment transfers.
     */
    private val transferPermits = Semaphore(MAX_CONCURRENT_TRANSFERS)

    /**
     * Attachments of a chat that are not on disk yet and carry enough payload
     * metadata to be downloadable: a real (non-staged) guid plus a transfer
     * name. Outgoing rows are included — callers filter when enqueueing.
     */
    fun pendingFor(chatId: Long): List<Attachment> {
        val builder = attachmentBox.query().equal(Attachment_.isDownloaded, false)
        builder.link(Attachment_.message).equal(Message_.chatId, chatId)
        return builder.build()
            .use { it.find() }
            .filter { it.guid != null && !it.guid!!.startsWith("temp") && !it.transferName.isNullOrEmpty() }
    }

    /**
     * Downloads [attachment] (deduped per guid — concurrent collectors share
     * one transfer) and emits progress until a terminal state. Cold flow:
     * nothing starts until collection begins. Cancelling a collector does
     * not abort the underlying transfer; other subscribers (or a later
     * re-subscribe) still observe completion.
     */
    fun download(attachment: Attachment): Flow<TransferState> = flow {
        val guid = attachment.guid
        if (guid == null) {
            emit(TransferState.Failed("Attachment has no guid"))
            return@flow
        }
        if (guid.startsWith("temp")) {
            // Staged outgoing payload — upload progress path, not a download.
            emit(TransferState.Failed("Cannot download staged attachment $guid"))
            return@flow
        }
        // Fast path: file already on disk (Dart prefers disk over the flag).
        if (disk.existingFile(attachment) != null) {
            disk.markDownloaded(guid)
            emit(TransferState.Done)
            return@flow
        }

        val entry = inFlightGuard.withLock {
            inFlight.getOrPut(guid) {
                InFlight().also { entry ->
                    scope.launch { runTransfer(guid, entry) }
                }
            }
        }
        emitAll(
            entry.states
                .filterNotNull()
                .transformWhile { state ->
                    emit(state)
                    state !is TransferState.Done && state !is TransferState.Failed
                },
        )
    }

    /**
     * Bulk-downloads every pending incoming attachment of a chat (the
     * auto-download behavior of `AttachmentsService.getContent` for a chat
     * view). Suspends until every transfer reached a terminal state;
     * individual failures are swallowed (the chat keeps rendering, each
     * bubble re-requests on demand).
     */
    suspend fun enqueueForChat(chatId: Long): Unit = coroutineScope {
        pendingFor(chatId)
            .filter { !it.isOutgoing }
            .forEach { attachment ->
                launch { download(attachment).collect { /* terminal is enough here */ } }
            }
    }

    /** The attachment's payload file, when present on disk (else null). */
    fun localFile(attachment: Attachment): File? = disk.existingFile(attachment)

    /**
     * Runs a single transfer in [scope]: resolves the destination, streams
     * progress into [InFlight.states], persists `isDownloaded` + real byte
     * length on success, and cleans up on failure.
     */
    private suspend fun runTransfer(guid: String, entry: InFlight) = transferPermits.withPermit {
        try {
            val fresh = attachmentBox.query()
                .equal(Attachment_.guid, guid, QueryBuilder.StringOrder.CASE_SENSITIVE)
                .build().use { it.findFirst() }
            if (fresh == null) {
                finish(guid, entry, TransferState.Failed("Unknown attachment $guid"))
                return
            }
            val dest = try {
                disk.pathFor(fresh)
            } catch (e: IOException) {
                finish(guid, entry, TransferState.Failed(e.message ?: "invalid attachment path"))
                return
            }
            dest.parentFile?.mkdirs()

            val result = downloader.download(guid, dest.absolutePath) { done, total ->
                // StateFlow assignment is thread-safe; the Rust callback may
                // fire from any thread.
                entry.states.value = TransferState.Progress(done, total)
            }

            result.fold(
                onSuccess = {
                    val size = if (dest.isFile) dest.length() else fresh.totalBytes
                    disk.markDownloaded(guid, size)
                    finish(guid, entry, TransferState.Done)
                },
                onFailure = { cause ->
                    // Remove the partial file so the guid re-downloads cleanly
                    // (Dart redownload deletes before restarting).
                    dest.delete()
                    File(dest.path + ".png").delete()
                    finish(guid, entry, TransferState.Failed(cause.message ?: "download failed"))
                },
            )
        } catch (t: Throwable) {
            finish(guid, entry, TransferState.Failed(t.message ?: "download failed"))
        }
    }

    /** Publishes the terminal state, then unhooks the guid for future runs. */
    private suspend fun finish(guid: String, entry: InFlight, state: TransferState) {
        entry.states.value = state
        inFlightGuard.withLock {
            // Only remove if this entry is still the registered one — a
            // restart must not be clobbered.
            if (inFlight[guid] === entry) inFlight.remove(guid)
        }
    }
}

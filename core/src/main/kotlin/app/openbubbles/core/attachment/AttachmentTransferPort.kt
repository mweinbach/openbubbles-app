package app.openbubbles.core.attachment

import app.openbubbles.db.Attachment
import app.openbubbles.db.Attachment_
import app.openbubbles.db.Message_
import io.objectbox.BoxStore
import io.objectbox.query.QueryBuilder
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.transformWhile
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap

/** Bound simultaneous MMCS transfers independently of coroutine dispatcher capacity. */
private const val MAX_CONCURRENT_TRANSFERS = 4

/**
 * Rust transfer seam. A parallel workstream is adding the actual
 * upload/download functions to the rust crate; this interface is the single
 * line of Kotlin they plug into:
 *
 * ```kotlin
 * AttachmentDownloader { guid, destPath, maxBytes, onProgress ->
 *     uniffi.rust_lib_bluebubbles.downloadAttachment(guid, destPath, maxBytes) { done, total ->
 *         onProgress(done, total)
 *     }.map { }.recoverCatching { ... }  // adapt the UniFFI surface
 * }
 * ```
 *
 * Contract: write the payload to [destPath] (already sanitized and parent
 * directories created by the caller), invoke [onProgress] with
 * `(bytesDone, bytesTotal)` as data arrives (total may be 0 when unknown),
 * stop the writer before it exceeds `maxBytes` when non-null,
 * and return success only after the file is complete on disk. Errors are
 * returned, never thrown.
 */
fun interface AttachmentDownloader {
    suspend fun download(
        attachmentGuid: String,
        destPath: String,
        maxBytes: Long?,
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
 * - a validated disk payload beats the persisted `isDownloaded` flag (the
 *   flag can drift), so [download] short-circuits only for a non-empty file
 *   whose byte length matches declared metadata;
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
    private class InFlight(val generation: Long) {
        val states = MutableStateFlow<TransferState?>(null)
    }

    private val inFlightGuard = Mutex()
    private val inFlight = ConcurrentHashMap<String, InFlight>()
    private val lifecycleLock = Any()
    private val shutdownGuard = Mutex()
    private val transferJobs = mutableSetOf<Job>()
    private var generation = 0L
    private var acceptingTransfers = true
    private var cleanupInProgress = false

    /**
     * Enqueue sites fan out a chat's whole backlog at once. Bound network,
     * decoding, and file pressure even though the UniFFI transfer now suspends
     * instead of parking a dispatcher thread.
     */
    private val transferPermits = Semaphore(MAX_CONCURRENT_TRANSFERS)

    /**
     * Reopen this process-lifetime manager for a newly validated account.
     * Sign-out leaves transfers blocked until the push owner explicitly calls
     * this method; a retained screen cannot revive the previous account.
     */
    fun beginAccount() {
        synchronized(lifecycleLock) {
            check(!cleanupInProgress) { "attachment account cleanup is still running" }
            if (!acceptingTransfers) {
                generation += 1
                acceptingTransfers = true
            }
        }
    }

    /**
     * Fence new transfers, cancel every detached transfer owner, and wait for
     * its native request, partial-file cleanup, and final publication to stop.
     * The manager remains reusable through [beginAccount].
     */
    suspend fun cancelAndJoin() = shutdownGuard.withLock {
        val jobs = synchronized(lifecycleLock) {
            acceptingTransfers = false
            cleanupInProgress = true
            generation += 1
            transferJobs.toList()
        }
        try {
            jobs.forEach(Job::cancel)
            jobs.joinAll()
            inFlightGuard.withLock {
                inFlight.values.forEach { entry ->
                    entry.states.value = TransferState.Failed("Attachment transfer interrupted")
                }
                inFlight.clear()
            }
        } finally {
            synchronized(lifecycleLock) { cleanupInProgress = false }
        }
    }

    private fun currentGeneration(): Long? = synchronized(lifecycleLock) {
        generation.takeIf { acceptingTransfers }
    }

    /** Serialize durable publication with generation invalidation. */
    private inline fun publishIfCurrent(capturedGeneration: Long, publish: () -> Unit): Boolean =
        synchronized(lifecycleLock) {
            if (!acceptingTransfers || generation != capturedGeneration) {
                false
            } else {
                publish()
                true
            }
        }

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
    fun download(attachment: Attachment, maxBytes: Long? = null): Flow<TransferState> = flow {
        val requestedGeneration = currentGeneration()
        if (requestedGeneration == null) {
            emit(TransferState.Failed("Attachment transfers are unavailable for this account"))
            return@flow
        }
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
        // Read current size metadata before trusting a prior-run payload; the
        // caller may be holding an older ObjectBox entity instance.
        val current = attachmentBox.query()
            .equal(Attachment_.guid, guid, QueryBuilder.StringOrder.CASE_SENSITIVE)
            .build().use { it.findFirst() }
        if (current != null && disk.existingFile(current) != null) {
            if (!publishIfCurrent(requestedGeneration) {
                    runCatching { disk.deleteDownloadPartial(current) }
                    disk.markDownloaded(guid)
                }
            ) {
                emit(TransferState.Failed("Attachment account changed during download"))
                return@flow
            }
            emit(TransferState.Done)
            return@flow
        }
        if (current?.isDownloaded == true) {
            if (!publishIfCurrent(requestedGeneration) { disk.markNotDownloaded(guid) }) {
                emit(TransferState.Failed("Attachment account changed during download"))
                return@flow
            }
        }

        val entry = inFlightGuard.withLock {
            var createdJob: Job? = null
            val tracked = synchronized(lifecycleLock) {
                if (!acceptingTransfers || generation != requestedGeneration) {
                    null
                } else {
                    inFlight.getOrPut(guid) {
                        InFlight(requestedGeneration).also { transfer ->
                            lateinit var job: Job
                            job = scope.launch(start = CoroutineStart.LAZY) {
                                runTransfer(guid, transfer, maxBytes)
                            }
                            transferJobs += job
                            job.invokeOnCompletion { cause ->
                                if (cause is CancellationException) {
                                    transfer.states.value =
                                        TransferState.Failed("Attachment transfer interrupted")
                                }
                                synchronized(lifecycleLock) { transferJobs.remove(job) }
                            }
                            createdJob = job
                        }
                    }
                }
            }
            createdJob?.start()
            tracked
        }
        if (entry == null) {
            emit(TransferState.Failed("Attachment account changed during download"))
            return@flow
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
    private suspend fun runTransfer(guid: String, entry: InFlight, maxBytes: Long?): Unit = transferPermits.withPermit {
        var ownedPartial: File? = null
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
            val partial = try {
                disk.partialPathFor(fresh)
            } catch (e: IOException) {
                finish(guid, entry, TransferState.Failed(e.message ?: "invalid attachment path"))
                return
            }
            ownedPartial = partial
            // A process may have died after writing the sibling but before
            // promotion. It is never a completed payload and must not resume
            // implicitly across a new downloader invocation.
            if (!publishIfCurrent(entry.generation) {
                    dest.parentFile?.mkdirs()
                    disk.deleteDownloadPartial(fresh)
                }
            ) {
                finish(guid, entry, TransferState.Failed("Attachment account changed during download"))
                return
            }

            val result = downloader.download(guid, partial.absolutePath, maxBytes) { done, total ->
                // StateFlow assignment is thread-safe; the Rust callback may
                // fire from any thread. Never revive an invalidated account.
                publishIfCurrent(entry.generation) {
                    entry.states.value = TransferState.Progress(done, total)
                }
            }

            result.fold(
                onSuccess = {
                    try {
                        if (publishIfCurrent(entry.generation) {
                                val completed = disk.promoteCompletedDownload(fresh, partial)
                                disk.markDownloaded(guid, completed.length())
                            }
                        ) {
                            finish(guid, entry, TransferState.Done)
                        } else {
                            partial.delete()
                            finish(
                                guid,
                                entry,
                                TransferState.Failed("Attachment account changed during download"),
                            )
                        }
                    } catch (cause: IOException) {
                        partial.delete()
                        finish(guid, entry, TransferState.Failed(cause.message ?: "download validation failed"))
                    }
                },
                onFailure = { cause ->
                    // Never touch the canonical file on a failed replacement;
                    // only the app-owned partial belongs to this attempt.
                    partial.delete()
                    finish(guid, entry, TransferState.Failed(cause.message ?: "download failed"))
                },
            )
        } catch (cancelled: CancellationException) {
            ownedPartial?.delete()
            withContext(NonCancellable) {
                finish(guid, entry, TransferState.Failed("Attachment transfer interrupted"))
            }
            throw cancelled
        } catch (t: Throwable) {
            ownedPartial?.delete()
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

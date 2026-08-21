package app.openbubbles.nativeapp.data

import java.io.File
import java.util.UUID

internal const val MAX_OUTGOING_DRAFT_BYTES = 100L * 1024L * 1024L
internal const val ABANDONED_OUTGOING_DRAFT_AGE_MS = 24L * 60L * 60L * 1_000L

/** Draft media is owned only under this app's direct cache/outgoing or cache/captures folders. */
internal fun isOwnedOutgoingDraft(file: File, cacheRoot: File): Boolean = runCatching {
    val candidate = file.canonicalFile
    val root = cacheRoot.canonicalFile
    candidate.parentFile == File(root, "outgoing").canonicalFile ||
        candidate.parentFile == File(root, "captures").canonicalFile
}.getOrDefault(false)

internal fun deleteOwnedOutgoingDraft(file: File, cacheRoot: File): Boolean =
    isOwnedOutgoingDraft(file, cacheRoot) && (!file.exists() || file.delete())

/**
 * Startup reconciliation for drafts left by process death. Only stale direct
 * files in this app's own outgoing/captures cache roots are eligible; nested
 * or external files are never traversed or deleted.
 */
internal fun reconcileAbandonedOutgoingDrafts(
    cacheRoot: File,
    nowMs: Long = System.currentTimeMillis(),
    minimumAgeMs: Long = ABANDONED_OUTGOING_DRAFT_AGE_MS,
): Int {
    require(minimumAgeMs >= 0L) { "minimumAgeMs must not be negative" }
    val cutoff = nowMs - minimumAgeMs
    var removed = 0
    listOf(File(cacheRoot, "outgoing"), File(cacheRoot, "captures")).forEach { directory ->
        directory.listFiles().orEmpty().forEach { candidate ->
            if (candidate.isFile && candidate.lastModified() <= cutoff &&
                deleteOwnedOutgoingDraft(candidate, cacheRoot)
            ) {
                removed++
            }
        }
    }
    return removed
}

/** One source/destination pair prepared for durable outgoing-message staging. */
internal data class OutgoingPayloadStage(
    val source: File,
    val destination: File,
)

/**
 * Copies an outgoing payload into canonical storage with a real streaming
 * ceiling, then atomically promotes the completed sibling. The draft source
 * remains untouched until its database row is durably staged.
 */
internal fun copyOutgoingAttachment(
    source: File,
    destination: File,
    maxBytes: Long = MAX_OUTGOING_DRAFT_BYTES,
): File {
    require(source.isFile) { "outgoing attachment is unavailable" }
    require(source.canonicalFile != destination.canonicalFile) {
        "outgoing draft and canonical payload must be different files"
    }
    destination.parentFile?.mkdirs()
    val partial = File(destination.parentFile, ".${destination.name}.${UUID.randomUUID()}.part")
    try {
        source.inputStream().use { input -> copyWithByteLimit(input, partial, maxBytes) }
        promoteOwnedSibling(partial, destination)
    } catch (failure: Throwable) {
        partial.delete()
        throw failure
    }
    return destination
}

/**
 * Stages a complete multi-file payload set before invoking the durable DB
 * transaction. Any copy or DB-stage failure removes canonical candidates and
 * leaves every retryable source intact. Only a successful DB stage retires
 * sources proven to belong to [cacheRoot].
 */
internal suspend fun <T> stageOutgoingPayloadBatch(
    stages: List<OutgoingPayloadStage>,
    cacheRoot: File,
    maxBytes: Long = MAX_OUTGOING_DRAFT_BYTES,
    persist: suspend (List<File>) -> T,
): T {
    require(stages.isNotEmpty()) { "outgoing payload staging requires at least one file" }
    val completed = ArrayList<File>(stages.size)
    val result = try {
        stages.forEach { stage ->
            completed += copyOutgoingAttachment(stage.source, stage.destination, maxBytes)
        }
        persist(completed)
    } catch (failure: Throwable) {
        completed.forEach { it.delete() }
        throw failure
    }
    stages.forEach { stage -> runCatching { deleteOwnedOutgoingDraft(stage.source, cacheRoot) } }
    return result
}

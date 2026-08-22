package app.openbubbles.nativeapp.sms

import app.openbubbles.nativeapp.data.copyWithByteLimit
import app.openbubbles.nativeapp.data.promoteOwnedSibling
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.nio.channels.FileChannel
import java.nio.file.StandardOpenOption
import java.util.UUID
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

internal const val MAX_INCOMING_MMS_PART_BYTES = 16L * 1024L * 1024L
internal const val MAX_INCOMING_MMS_MESSAGE_BYTES = 32L * 1024L * 1024L
internal const val MAX_INCOMING_MMS_PARTS = 32

internal data class IncomingMmsMediaSource(
    val destination: File,
    val expectedBytes: Long? = null,
    val openStream: () -> InputStream,
)

internal data class StagedIncomingMmsMedia(
    val destination: File,
    val partial: File,
    val sizeBytes: Long,
)

/** Stage every provider stream before exposing any carrier attachment's canonical filename. */
@Throws(IOException::class)
internal fun stageIncomingMmsMedia(
    sources: List<IncomingMmsMediaSource>,
    maxPartBytes: Long = MAX_INCOMING_MMS_PART_BYTES,
    maxMessageBytes: Long = MAX_INCOMING_MMS_MESSAGE_BYTES,
): List<StagedIncomingMmsMedia> {
    require(maxPartBytes > 0 && maxMessageBytes > 0) { "MMS byte ceilings must be positive" }
    require(sources.size <= MAX_INCOMING_MMS_PARTS) { "MMS contains too many media parts" }
    if (sources.isEmpty()) return emptyList()

    val staged = mutableListOf<StagedIncomingMmsMedia>()
    val destinations = mutableSetOf<File>()
    var totalBytes = 0L
    try {
        sources.forEach { source ->
            val destination = source.destination.canonicalFile
            if (!destinations.add(destination)) {
                throw IOException("MMS media parts cannot share a destination")
            }
            val directory = destination.parentFile ?: throw IOException("MMS attachment has no directory")
            if (!directory.isDirectory && !directory.mkdirs()) {
                throw IOException("Could not create MMS attachment directory")
            }
            val remaining = maxMessageBytes - totalBytes
            if (remaining <= 0L) throw IOException("MMS media exceeds its total byte ceiling")
            val partial = File(directory, ".${destination.name}.${UUID.randomUUID()}.mms-part")
            try {
                val written = source.openStream().use { input ->
                    copyWithByteLimit(input, partial, minOf(maxPartBytes, remaining))
                }
                if (written <= 0L || partial.length() != written) {
                    throw IOException("MMS attachment is empty or incomplete")
                }
                if (source.expectedBytes != null && source.expectedBytes != written) {
                    throw IOException("MMS attachment length does not match provider metadata")
                }
                staged += StagedIncomingMmsMedia(destination, partial, written)
                totalBytes += written
            } catch (failure: Throwable) {
                partial.delete()
                throw failure
            }
        }
        return staged
    } catch (failure: Throwable) {
        discardIncomingMmsMedia(staged)
        throw failure
    }
}

/** Publish validated siblings atomically, retaining any previous valid payload on failure. */
@Throws(IOException::class)
internal fun publishIncomingMmsMedia(staged: List<StagedIncomingMmsMedia>): List<File> {
    val published = mutableListOf<File>()
    try {
        staged.forEach { media ->
            if (!media.partial.isFile || media.partial.length() != media.sizeBytes || media.sizeBytes <= 0L) {
                throw IOException("Staged MMS attachment is empty or incomplete")
            }
            published += promoteOwnedSibling(media.partial, media.destination)
            syncMmsDirectory(media.destination.parentFile)
        }
        return published
    } catch (failure: Throwable) {
        discardIncomingMmsMedia(staged)
        throw failure
    }
}

internal fun discardIncomingMmsMedia(staged: List<StagedIncomingMmsMedia>) {
    staged.forEach { media -> media.partial.delete() }
}

private fun syncMmsDirectory(directory: File?) {
    if (directory == null) return
    try {
        FileChannel.open(directory.toPath(), StandardOpenOption.READ).use { it.force(true) }
    } catch (_: Exception) {
        // File bytes were synced before their atomic rename; directory syncing
        // is an additional best-effort barrier on filesystems that support it.
    }
}

/** Serialize provider deliveries and acknowledge an ID only after its complete ingest succeeds. */
internal class MmsIngestionGate(private val capacity: Int = 256) {
    private val mutex = Mutex()
    private val processed = linkedSetOf<Long>()

    init {
        require(capacity > 0) { "MMS ingestion capacity must be positive" }
    }

    suspend fun process(providerId: Long, ingest: suspend () -> Boolean): Boolean = mutex.withLock {
        if (providerId in processed) return@withLock true
        val complete = ingest()
        if (complete) {
            if (processed.size >= capacity) {
                val entries = processed.iterator()
                if (entries.hasNext()) {
                    entries.next()
                    entries.remove()
                }
            }
            processed += providerId
        }
        complete
    }
}

package app.openbubbles.nativeapp.data

import java.io.File
import java.io.FileOutputStream
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

/**
 * Durable dispatch ownership, separate from the compatibility-bound ObjectBox schema.
 *
 * Message bodies and attachment bytes stay in their existing account-private ObjectBox/file
 * locations. This journal contains only the identities and metadata needed to recover a send
 * which had definitely not crossed its irreversible Apple transport boundary.
 */
internal class MessagingOutboxStore(private val directory: File) {
    private val lock = Any()
    private val journal = File(directory, JOURNAL_NAME)
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    fun entries(owner: String): List<OutgoingOutboxEntry> = synchronized(lock) {
        requireOwner(owner)
        readEntries().filter { it.accountOwner == owner }
    }

    fun entry(messageId: Long): OutgoingOutboxEntry? = synchronized(lock) {
        readEntries().firstOrNull { it.messageId == messageId }
    }

    fun enqueue(entry: OutgoingOutboxEntry): OutgoingOutboxEntry = synchronized(lock) {
        requireOwner(entry.accountOwner)
        require(entry.messageId > 0L) { "outgoing message needs a durable identity" }
        val existing = readEntries().toMutableList()
        val index = existing.indexOfFirst { it.messageId == entry.messageId }
        if (index >= 0) {
            check(existing[index].accountOwner == entry.accountOwner) {
                "outgoing message belongs to another Apple account"
            }
            existing[index] = entry
        } else {
            check(existing.size < MAX_ENTRIES) { "too many pending outgoing messages" }
            existing += entry
        }
        writeEntries(existing)
        entry
    }

    fun update(
        messageId: Long,
        owner: String,
        transform: (OutgoingOutboxEntry) -> OutgoingOutboxEntry,
    ): OutgoingOutboxEntry? = synchronized(lock) {
        requireOwner(owner)
        val existing = readEntries().toMutableList()
        val index = existing.indexOfFirst { it.messageId == messageId && it.accountOwner == owner }
        if (index < 0) return@synchronized null
        val updated = transform(existing[index])
        require(updated.accountOwner == owner && updated.messageId == messageId) {
            "an outgoing attempt cannot change account or message identity"
        }
        existing[index] = updated
        writeEntries(existing)
        updated
    }

    fun remove(messageId: Long, owner: String): Boolean = synchronized(lock) {
        requireOwner(owner)
        val existing = readEntries()
        val retained = existing.filterNot { it.messageId == messageId && it.accountOwner == owner }
        if (retained.size == existing.size) return@synchronized false
        writeEntries(retained)
        true
    }

    fun clearOwner(owner: String) = synchronized(lock) {
        requireOwner(owner)
        val existing = readEntries()
        val retained = existing.filterNot { it.accountOwner == owner }
        if (retained.size != existing.size) writeEntries(retained)
    }

    fun clear() = synchronized(lock) {
        writeEntries(emptyList())
    }

    private fun readEntries(): List<OutgoingOutboxEntry> {
        if (!journal.isFile) return emptyList()
        val byteCount = journal.length()
        check(byteCount in 1..MAX_JOURNAL_BYTES) { "outgoing message journal is invalid" }
        val decoded = json.decodeFromString(
            ListSerializer(OutgoingOutboxEntry.serializer()),
            journal.readText(),
        )
        check(decoded.size <= MAX_ENTRIES) { "outgoing message journal exceeds its entry limit" }
        check(decoded.map { it.messageId }.distinct().size == decoded.size) {
            "outgoing message journal contains duplicate message identities"
        }
        decoded.forEach { requireOwner(it.accountOwner) }
        return decoded
    }

    private fun writeEntries(entries: List<OutgoingOutboxEntry>) {
        if (entries.isEmpty()) {
            if (journal.exists()) check(journal.delete()) { "could not clear outgoing message journal" }
            return
        }
        check(directory.isDirectory || directory.mkdirs()) { "could not create outgoing message journal" }
        val encoded = json.encodeToString(ListSerializer(OutgoingOutboxEntry.serializer()), entries)
            .toByteArray(Charsets.UTF_8)
        check(encoded.size <= MAX_JOURNAL_BYTES) { "outgoing message journal exceeds its byte limit" }
        val staged = File.createTempFile(".outbox-", ".part", directory)
        try {
            FileOutputStream(staged).use { output ->
                output.write(encoded)
                output.fd.sync()
            }
            try {
                Files.move(
                    staged.toPath(),
                    journal.toPath(),
                    StandardCopyOption.ATOMIC_MOVE,
                    StandardCopyOption.REPLACE_EXISTING,
                )
            } catch (_: AtomicMoveNotSupportedException) {
                Files.move(staged.toPath(), journal.toPath(), StandardCopyOption.REPLACE_EXISTING)
            }
        } finally {
            if (staged.exists()) staged.delete()
        }
    }

    private fun requireOwner(owner: String) {
        require(OWNER_PATTERN.matches(owner)) { "outgoing messaging account owner is invalid" }
    }

    private companion object {
        const val JOURNAL_NAME = "outgoing-messages.json"
        const val MAX_ENTRIES = 512
        const val MAX_JOURNAL_BYTES = 1_048_576L
        val OWNER_PATTERN = Regex("[0-9a-f]{64}")
    }
}

@Serializable
internal data class OutgoingOutboxEntry(
    val accountOwner: String,
    val messageId: Long,
    val chatId: Long,
    val sender: String,
    val kind: OutgoingOutboxKind,
    val state: OutgoingOutboxState = OutgoingOutboxState.QUEUED,
    val attempt: Int = 0,
    val nextAttemptAtMs: Long = 0L,
    val mentions: List<OutgoingOutboxMention> = emptyList(),
)

@Serializable
internal enum class OutgoingOutboxKind { TEXT, ATTACHMENT }

@Serializable
internal enum class OutgoingOutboxState {
    /** No Apple transport operation has begun; automatic replay is safe. */
    QUEUED,

    /** Apple may already have received the message; never replay automatically. */
    DISPATCHING,

    /** A visible error requires an explicit user retry. */
    FAILED,

    /** Cancellation was claimed before dispatch; late publications must fail closed. */
    CANCELLED,
}

@Serializable
internal data class OutgoingOutboxMention(
    val start: Int,
    val end: Int,
    val handle: String,
    val displayText: String,
)

internal fun outgoingRetryDelayMs(attempt: Int): Long {
    val bounded = attempt.coerceIn(0, 6)
    return (2_000L shl bounded).coerceAtMost(120_000L)
}

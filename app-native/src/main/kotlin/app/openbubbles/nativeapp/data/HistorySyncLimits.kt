package app.openbubbles.nativeapp.data

import android.annotation.SuppressLint
import android.content.Context
import app.openbubbles.core.sync.CloudSyncPort
import app.openbubbles.db.Attachment
import app.openbubbles.db.Attachment_
import app.openbubbles.db.Message
import app.openbubbles.db.Message_
import io.objectbox.BoxStore
import io.objectbox.query.QueryBuilder
import uniffi.rust_lib_bluebubbles.UAttachmentSyncPage
import uniffi.rust_lib_bluebubbles.UCloudMessage
import uniffi.rust_lib_bluebubbles.UMessageSyncPage

private const val PREFS_NAME = "history_sync_preferences"
private const val KEY_HISTORY_SYNC_WINDOW = "historySyncWindow"
private const val APPLE_EPOCH_OFFSET_MILLIS = 978_307_200_000L
private const val MILLIS_PER_DAY = 86_400_000L
private const val NANOS_PER_MILLI = 1_000_000L
private const val TRANSCRIPT_BACKGROUND_MESSAGE_TYPE = 138L

/**
 * How much message history is kept on this device.
 *
 * The default, [ALL_HISTORY], mirrors Messages in iCloud. A narrower window
 * is an explicit user opt-in and is applied client-side: CloudKit zone sync
 * has no server-side date filter, so every record is still fetched and the
 * continuation cursor still advances across the whole zone — a window only
 * trims what is kept locally. It does not make the download faster. Titles
 * and descriptions say "keeps" rather than "downloads" for that reason.
 * Attachment payloads are never part of history sync regardless of window;
 * only their metadata syncs, and files download when opened.
 */
enum class HistorySyncWindow(
    val persistedValue: String,
    val title: String,
    val description: String,
    internal val days: Int?,
) {
    LAST_30_DAYS(
        persistedValue = "30_days",
        title = "Last 30 days",
        description = "Keeps only the last month of messages on this phone",
        days = 30,
    ),
    LAST_3_MONTHS(
        persistedValue = "3_months",
        title = "Last 3 months",
        description = "Keeps the last three months for a smaller local database",
        days = 90,
    ),
    LAST_YEAR(
        persistedValue = "1_year",
        title = "Last year",
        description = "Keeps up to one year of message history",
        days = 365,
    ),
    ALL_HISTORY(
        persistedValue = "all",
        title = "All history",
        description = "Mirrors everything in Messages in iCloud",
        days = null,
    ),
    ;

    companion object {
        /** The default when nothing is persisted: mirror the server. */
        val DEFAULT: HistorySyncWindow get() = ALL_HISTORY

        fun fromPersistedValue(value: String?): HistorySyncWindow =
            entries.firstOrNull { it.persistedValue == value } ?: DEFAULT
    }

    /** True when this window is narrower than the server-mirroring default. */
    val limitsHistory: Boolean get() = days != null

    internal fun cutoffAppleNanoseconds(nowMillis: Long): Long? {
        val windowDays = days ?: return null
        val cutoffUnixMillis = nowMillis - windowDays.toLong() * MILLIS_PER_DAY
        return (cutoffUnixMillis - APPLE_EPOCH_OFFSET_MILLIS) * NANOS_PER_MILLI
    }
}

class HistorySyncPreferences(context: Context) {
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    var window: HistorySyncWindow
        get() = HistorySyncWindow.fromPersistedValue(
            prefs.getString(KEY_HISTORY_SYNC_WINDOW, null),
        )
        @SuppressLint("UseKtx") // commit() establishes ordering before the download is armed.
        set(value) {
            check(prefs.edit().putString(KEY_HISTORY_SYNC_WINDOW, value.persistedValue).commit()) {
                "failed to persist history sync window"
            }
        }
}

internal fun shouldIncludeHistoryMessage(
    message: UCloudMessage,
    window: HistorySyncWindow,
    nowMillis: Long,
    alreadyLocal: Boolean,
): Boolean {
    if (window == HistorySyncWindow.ALL_HISTORY || alreadyLocal) return true
    if (message.msgType == TRANSCRIPT_BACKGROUND_MESSAGE_TYPE) return true
    return message.time >= requireNotNull(window.cutoffAppleNanoseconds(nowMillis))
}

/**
 * Filters records before [app.openbubbles.core.sync.CloudSyncManager] applies
 * them while preserving the delegate's continuation cursor. Every CloudKit
 * zone is still drained, so future incremental syncs never skip records.
 */
internal class HistoryLimitedCloudSyncPort(
    private val delegate: CloudSyncPort,
    private val store: BoxStore,
    private val window: () -> HistorySyncWindow,
    private val nowMillis: () -> Long = System::currentTimeMillis,
) : CloudSyncPort by delegate {
    private val selectedAttachmentGuids = HashSet<String>()

    override suspend fun messagesPage(cursor: ByteArray?): UMessageSyncPage {
        val page = delegate.messagesPage(cursor)
        val selectedWindow = window()
        if (selectedWindow == HistorySyncWindow.ALL_HISTORY) return page

        val existingGuids = existingMessageGuids(
            page.records.mapNotNull { it.message?.guid },
        )
        val now = nowMillis()
        val records = page.records.filter { change ->
            val message = change.message ?: return@filter true
            shouldIncludeHistoryMessage(
                message = message,
                window = selectedWindow,
                nowMillis = now,
                alreadyLocal = message.guid in existingGuids,
            )
        }
        records.mapNotNull { it.message }
            .flatMapTo(selectedAttachmentGuids, UCloudMessage::attachmentGuids)
        return page.copy(records = records)
    }

    override suspend fun attachmentsPage(cursor: ByteArray?): UAttachmentSyncPage {
        val page = delegate.attachmentsPage(cursor)
        if (window() == HistorySyncWindow.ALL_HISTORY) return page

        val existingParents = existingMessageGuids(
            page.records.mapNotNull { it.attachment?.messageGuid },
        )
        val existingAttachments = existingAttachmentGuids(
            page.records.mapNotNull { it.attachment?.guid },
        )
        return page.copy(
            records = page.records.filter { change ->
                val attachment = change.attachment ?: return@filter true
                attachment.guid in existingAttachments ||
                    attachment.guid in selectedAttachmentGuids ||
                    attachment.messageGuid?.let(existingParents::contains) == true
            },
        )
    }

    private fun existingMessageGuids(guids: Collection<String>): Set<String> {
        val values = guids.filter(String::isNotEmpty).distinct()
        if (values.isEmpty()) return emptySet()
        return store.boxFor(Message::class.java).query()
            .`in`(Message_.guid, values.toTypedArray(), QueryBuilder.StringOrder.CASE_SENSITIVE)
            .build().use { query -> query.find().mapTo(HashSet(), Message::guid) }
    }

    private fun existingAttachmentGuids(guids: Collection<String>): Set<String> {
        val values = guids.filter(String::isNotEmpty).distinct()
        if (values.isEmpty()) return emptySet()
        return store.boxFor(Attachment::class.java).query()
            .`in`(Attachment_.guid, values.toTypedArray(), QueryBuilder.StringOrder.CASE_SENSITIVE)
            .build().use { query -> query.find().mapTo(HashSet(), Attachment::guid) }
    }
}

package app.openbubbles.nativeapp.sms

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Telephony
import android.util.Log
import app.openbubbles.core.attachment.AttachmentStore
import app.openbubbles.db.Attachment
import app.openbubbles.db.Attachment_
import app.openbubbles.nativeapp.data.CoreGraph
import app.openbubbles.nativeapp.data.PushStateHolder
import io.objectbox.query.QueryBuilder
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * MMS receive trigger. On `WAP_PUSH_RECEIVED` (the MMS notification PDU) this
 * receiver waits briefly for the system/default SMS app to download and store
 * the message in the telephony provider, then ingests the decoded row — text
 * parts as body, media parts persisted straight into the attachment store.
 *
 * Why no PDU download here: downloading the notification's content-location
 * ourselves would require carrier MMSC/APN routing (the `android-smsmms`
 * transaction stack). Non-default SMS apps on modern Android cannot do that
 * reliably; relying on the provider row keeps this dependency-free. When no
 * default SMS app exists (or READ_SMS is not granted) the poll finds nothing
 * and the message is skipped — see the task report (deferred: direct MMSC
 * download + MMS sending).
 */
class MmsReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.WAP_PUSH_RECEIVED_ACTION) return

        if (!SmsPermissions.canReadTelephony(context)) {
            Log.i(TAG, "MMS received but READ_SMS not granted — skipping ingest")
            return
        }

        val receivedAtMs = System.currentTimeMillis()
        val pending = goAsync()
        SmsBridge.scope.launch(Dispatchers.IO) {
            try {
                // The default SMS app needs a moment to download + store the
                // message; stay inside the ~10s goAsync budget.
                repeat(8) {
                    if (ingestNewProviderMms(context, receivedAtMs)) return@launch
                    Thread.sleep(1_000)
                }
                Log.i(TAG, "No provider MMS row appeared after notification")
            } catch (t: Throwable) {
                Log.w(TAG, "MMS ingest failed", t)
            } finally {
                pending.finish()
            }
        }
    }

    /**
     * Ingests every not-yet-seen inbox MMS row dated after [sinceMs].
     * Returns true when at least one row was ingested.
     */
    private suspend fun ingestNewProviderMms(context: Context, sinceMs: Long): Boolean {
        val rows = queryNewMmsRows(context, sinceMs) ?: return false
        var ingested = false
        for (row in rows) {
            // Redelivery/duplicate rows are deduped by the deterministic guid,
            // but skipping known ids keeps the provider read cheap.
            if (SmsBridge.seenMmsIds.contains(row.id)) continue
            if (ingestOne(context, row)) {
                SmsBridge.seenMmsIds.add(row.id)
                ingested = true
            }
        }
        return ingested
    }

    private data class MmsRow(val id: Long, val threadId: Long, val dateMs: Long)

    private fun queryNewMmsRows(context: Context, sinceMs: Long): List<MmsRow>? = runCatching {
        // Provider dates are epoch SECONDS.
        val sinceSeconds = (sinceMs / 1000L) - 3L
        context.contentResolver.query(
            Telephony.Mms.CONTENT_URI,
            arrayOf(Telephony.Mms._ID, Telephony.Mms.THREAD_ID, Telephony.Mms.DATE),
            "${Telephony.Mms.MESSAGE_BOX} = ? AND ${Telephony.Mms.DATE} >= ?",
            arrayOf("1", sinceSeconds.toString()), // MESSAGE_BOX_INBOX
            "${Telephony.Mms.DATE} ASC",
        )?.use { cursor ->
            buildList {
                while (cursor.moveToNext()) {
                    add(
                        MmsRow(
                            id = cursor.getLong(0),
                            threadId = cursor.getLong(1),
                            dateMs = cursor.getLong(2) * 1000L,
                        ),
                    )
                }
            }
        }
    }.getOrNull()

    private suspend fun ingestOne(context: Context, row: MmsRow): Boolean {
        val store = CoreGraph.store ?: return false

        val addresses = queryMmsAddresses(context, row.id)
        val sender = addresses.firstOrNull { it.type == ADDR_FROM }?.address ?: return false
        val others = addresses.filter { it.type != ADDR_FROM }.map { it.address }

        val parts = queryMmsParts(context, row.id)
        val text = parts.filter { it.text != null }.joinToString("\n") { it.text.orEmpty() }
        val media = parts.filter { it.bytes != null }
        if (text.isBlank() && media.isEmpty()) return false

        val guid = SmsPushBuilder.mmsGuid(row.id)
        val push = SmsPushBuilder.buildIncomingMms(
            guid = guid,
            senderAddress = sender,
            participantAddresses = others,
            text = text,
            attachments = media.map { SmsPushBuilder.MmsAttachment(mime = it.mime, name = it.name) },
            timestampMs = row.dateMs,
            myPhoneHandles = PushStateHolder.myHandles.filter { it.startsWith("tel:") },
        )
        val preview = when {
            text.isNotBlank() -> text.lineSequence().firstOrNull { it.isNotBlank() } ?: "MMS"
            media.isNotEmpty() -> media.first().let {
                when {
                    it.mime.startsWith("image/") -> "Photo"
                    it.mime.startsWith("video/") -> "Video"
                    it.mime.startsWith("audio/") -> "Audio"
                    else -> "Attachment"
                }
            }
            else -> "MMS"
        }

        val chatId = SmsIngest.ingestIncoming(context, push, preview, row.threadId) ?: return false

        // The ingestor created attachment metadata rows with guid
        // "<msgGuid>_<idx>" (xml empty); move the payloads into the canonical
        // store layout so bubbles render without a rustpush transfer.
        if (media.isNotEmpty()) persistMedia(context, store, guid, media, chatId)
        return true
    }

    private data class MmsAddress(val address: String, val type: Int)

    /** Provider addr-table types: FROM=137 (PduHeaders.FROM), TO=151, CC=130. */
    private fun queryMmsAddresses(context: Context, mmsId: Long): List<MmsAddress> = runCatching {
        context.contentResolver.query(
            Uri.parse("content://mms/$mmsId/addr"),
            arrayOf(Telephony.Mms.Addr.ADDRESS, Telephony.Mms.Addr.TYPE),
            null, null, null,
        )?.use { cursor ->
            buildList {
                while (cursor.moveToNext()) {
                    val address = cursor.getString(0) ?: continue
                    val type = cursor.getInt(1)
                    if (type == ADDR_FROM || type == ADDR_TO || type == ADDR_CC) add(MmsAddress(address, type))
                }
            }
        }
    }.getOrNull() ?: emptyList()

    private data class MmsPart(
        val mime: String,
        val name: String,
        val text: String?,
        val bytes: ByteArray?,
    )

    private fun queryMmsParts(context: Context, mmsId: Long): List<MmsPart> = runCatching {
        context.contentResolver.query(
            Telephony.Mms.Part.CONTENT_URI,
            arrayOf(Telephony.Mms.Part._ID, Telephony.Mms.Part.CONTENT_TYPE, Telephony.Mms.Part.NAME, Telephony.Mms.Part.TEXT),
            "${Telephony.Mms.Part.MSG_ID} = ?",
            arrayOf(mmsId.toString()),
            null,
        )?.use { cursor ->
            buildList {
                while (cursor.moveToNext()) {
                    val mime = cursor.getString(1) ?: continue
                    if (mime == "application/smil") continue
                    val partId = cursor.getLong(0)
                    val name = cursor.getString(2) ?: "mms_$partId"
                    if (mime.startsWith("text/")) {
                        val body = cursor.getString(3)
                        if (!body.isNullOrEmpty()) add(MmsPart(mime, name, body, null))
                    } else {
                        val bytes = readPartBytes(context, partId) ?: continue
                        add(MmsPart(mime, name, null, bytes))
                    }
                }
            }
        }
    }.getOrNull() ?: emptyList()

    private fun readPartBytes(context: Context, partId: Long): ByteArray? = runCatching {
        context.contentResolver.openInputStream(Uri.parse("content://mms/part/$partId"))?.use { it.readBytes() }
    }.getOrNull()

    private fun persistMedia(
        context: Context,
        store: io.objectbox.BoxStore,
        messageGuid: String,
        media: List<MmsPart>,
        @Suppress("UNUSED_PARAMETER") chatId: Long,
    ) {
        runCatching {
            val root = File(context.dataDir, "app_flutter")
            val disk = AttachmentStore(store, root)
            val box = store.boxFor(Attachment::class.java)
            media.forEachIndexed { index, part ->
                val attachmentGuid = "${messageGuid}_$index"
                val payload = File(disk.directoryFor(attachmentGuid), disk.sanitizeFileName(part.name))
                payload.parentFile?.mkdirs()
                payload.writeBytes(part.bytes ?: return@forEachIndexed)

                box.query()
                    .equal(Attachment_.guid, attachmentGuid, QueryBuilder.StringOrder.CASE_SENSITIVE)
                    .build().use { it.findFirst() }
                    ?.apply {
                        isDownloaded = true
                        totalBytes = payload.length()
                        box.put(this)
                    }
            }
        }.onFailure { Log.w(TAG, "MMS media persist failed", it) }
    }

    private companion object {
        private const val TAG = "MmsReceiver"
        private const val ADDR_FROM = 137
        private const val ADDR_TO = 151
        private const val ADDR_CC = 130
    }
}

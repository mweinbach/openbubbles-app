package app.openbubbles.nativeapp.sms

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.ContentUris
import android.net.Uri
import androidx.core.net.toUri
import android.provider.Telephony
import android.util.Log
import app.openbubbles.core.attachment.AttachmentStore
import app.openbubbles.db.Attachment
import app.openbubbles.db.Attachment_
import app.openbubbles.db.Message
import app.openbubbles.db.Message_
import app.openbubbles.nativeapp.data.CoreGraph
import app.openbubbles.nativeapp.data.PushStateHolder
import io.objectbox.query.QueryBuilder
import java.io.File
import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Non-default MMS receive trigger. On `WAP_PUSH_RECEIVED` this receiver waits
 * briefly for the current default SMS app to store the message in the
 * telephony provider, then ingests text and media into the native store.
 *
 * When OpenBubbles holds Android's default-SMS role, [MmsPushReceiver] uses the
 * carrier transaction stack to download and persist the PDU, then
 * [CarrierMmsReceivedReceiver] calls [ingestProviderMms] directly. This
 * receiver remains as the non-default fallback: Google Messages downloads the
 * PDU and this process polls the shared telephony row when the public push
 * broadcast arrives.
 */
class MmsReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.WAP_PUSH_RECEIVED_ACTION) return

        if (!SmsPermissions.canReadTelephony(context)) {
            Log.i(TAG, "MMS received but READ_SMS not granted — skipping ingest")
            return
        }

        val receivedAtMs = System.currentTimeMillis()
        MmsIngestionRetry.scheduleDiscovery(context, receivedAtMs)
        val pending = goAsync()
        SmsBridge.scope.launch(Dispatchers.IO) {
            try {
                // The default SMS app needs a moment to download + store the
                // message; stay inside the ~10s goAsync budget.
                repeat(8) {
                    if (ingestRecentProviderMms(context, receivedAtMs)) return@launch
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
    internal suspend fun ingestRecentProviderMms(context: Context, sinceMs: Long): Boolean {
        val rows = queryNewMmsRows(context, sinceMs) ?: return false
        var ingested = false
        for (row in rows) {
            // Own recovery durably before provider reads, staging, or ObjectBox
            // writes; a process death cannot turn a partial ingest into loss.
            MmsIngestionRetry.scheduleProvider(context, row.id)
            if (SmsBridge.mmsIngestionGate.process(row.id) { ingestOne(context, row) }) {
                ingested = true
            }
        }
        return ingested
    }

    /** Ingests the exact provider row persisted by the carrier MMS callback. */
    internal suspend fun ingestProviderMms(context: Context, uri: Uri): Boolean {
        val id = runCatching { ContentUris.parseId(uri) }.getOrNull() ?: return false
        MmsIngestionRetry.scheduleProvider(context, id)
        return SmsBridge.mmsIngestionGate.process(id) {
            val row = queryMmsRow(context, uri) ?: return@process false
            ingestOne(context, row)
        }
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

    private fun queryMmsRow(context: Context, uri: Uri): MmsRow? = runCatching {
        context.contentResolver.query(
            uri,
            arrayOf(Telephony.Mms._ID, Telephony.Mms.THREAD_ID, Telephony.Mms.DATE),
            null,
            null,
            null,
        )?.use { cursor ->
            if (!cursor.moveToFirst()) return@use null
            MmsRow(
                id = cursor.getLong(0),
                threadId = cursor.getLong(1),
                dateMs = cursor.getLong(2) * 1000L,
            )
        }
    }.getOrNull()

    private suspend fun ingestOne(context: Context, row: MmsRow): Boolean {
        val store = CoreGraph.store ?: return false

        val addresses = queryMmsAddresses(context, row.id)
        val sender = addresses.firstOrNull { it.type == ADDR_FROM }?.address ?: return false
        val others = addresses.filter { it.type != ADDR_FROM }.map { it.address }

        val parts = queryMmsParts(context, row.id)
        val text = parts.filter { it.text != null }.joinToString("\n") { it.text.orEmpty() }
        val media = parts.filter { it.partId != null }
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

        val staged = if (media.isNotEmpty()) stageMedia(context, store, guid, media) else emptyList()
        try {
            // Retries reuse a previously committed carrier row instead of
            // replaying its notification while recovering failed media writes.
            val existing = store.boxFor(Message::class.java)
                .query()
                .equal(Message_.guid, guid, QueryBuilder.StringOrder.CASE_SENSITIVE)
                .build().use { it.findFirst() }
            if (existing != null) {
                check(existing.chat.target?.isRpSms == true) {
                    "MMS provider row cannot replace a non-carrier message"
                }
            } else if (SmsIngest.ingestIncoming(context, push, preview, row.threadId) == null) {
                return false
            }
            if (media.isNotEmpty()) persistMedia(store, guid, staged)
            return true
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (failure: Exception) {
            Log.w(TAG, "MMS media ingest remains pending for retry", failure)
            return false
        } finally {
            discardIncomingMmsMedia(staged)
        }
    }

    private data class MmsAddress(val address: String, val type: Int)

    /** Provider addr-table types: FROM=137 (PduHeaders.FROM), TO=151, CC=130. */
    private fun queryMmsAddresses(context: Context, mmsId: Long): List<MmsAddress> = runCatching {
        context.contentResolver.query(
            "content://mms/$mmsId/addr".toUri(),
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
        val partId: Long?,
    )

    private fun queryMmsParts(context: Context, mmsId: Long): List<MmsPart> = runCatching {
        context.contentResolver.query(
            "content://mms/part".toUri(),
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
                        add(MmsPart(mime, name, null, partId))
                    }
                }
            }
        }
    }.getOrNull() ?: emptyList()

    private fun stageMedia(
        context: Context,
        store: io.objectbox.BoxStore,
        messageGuid: String,
        media: List<MmsPart>,
    ): List<StagedIncomingMmsMedia> {
        val disk = AttachmentStore(store, File(context.dataDir, "app_flutter"))
        return stageIncomingMmsMedia(
            media.mapIndexed { index, part ->
                val attachmentGuid = "${messageGuid}_$index"
                val payload = File(disk.directoryFor(attachmentGuid), disk.sanitizeFileName(part.name))
                IncomingMmsMediaSource(destination = payload) {
                    val partId = part.partId ?: throw IOException("MMS attachment has no provider identity")
                    context.contentResolver.openInputStream("content://mms/part/$partId".toUri())
                        ?: throw IOException("MMS attachment provider stream is unavailable")
                }
            }
        )
    }

    private fun persistMedia(
        store: io.objectbox.BoxStore,
        messageGuid: String,
        staged: List<StagedIncomingMmsMedia>,
    ) {
        publishIncomingMmsMedia(staged)
        val box = store.boxFor(Attachment::class.java)
        store.runInTx {
            staged.forEachIndexed { index, media ->
                val attachmentGuid = "${messageGuid}_$index"
                val attachment = box.query()
                    .equal(Attachment_.guid, attachmentGuid, QueryBuilder.StringOrder.CASE_SENSITIVE)
                    .build().use { it.findFirst() }
                    ?: throw IOException("MMS attachment row $attachmentGuid was not persisted")
                check(attachment.message.target?.chat?.target?.isRpSms == true) {
                    "MMS attachment does not belong to a SIM conversation"
                }
                attachment.isDownloaded = true
                attachment.totalBytes = media.sizeBytes
                box.put(attachment)
            }
        }
    }

    private companion object {
        private const val TAG = "MmsReceiver"
        private const val ADDR_FROM = 137
        private const val ADDR_TO = 151
        private const val ADDR_CC = 130
    }
}

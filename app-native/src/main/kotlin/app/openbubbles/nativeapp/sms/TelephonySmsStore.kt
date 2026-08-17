package app.openbubbles.nativeapp.sms

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.provider.Telephony
import android.util.Log

/**
 * Writes and updates the platform SMS provider when OpenBubbles holds the
 * default-SMS role. The default app is the only process allowed to persist
 * inbox/sent rows; without this, incoming [SMS_DELIVER] never lands in
 * Android's thread database and outgoing MMS cannot attach to a thread.
 */
internal object TelephonySmsStore {

    data class PersistedSms(
        val uri: Uri? = null,
        val threadId: Long? = null,
    )

    fun insertInbox(
        context: Context,
        address: String,
        body: String,
        dateMs: Long,
    ): PersistedSms {
        if (!SmsRole.isHeld(context)) return PersistedSms()
        return runCatching {
            val uri = context.contentResolver.insert(
                Telephony.Sms.Inbox.CONTENT_URI,
                inboxSmsValues(address, body, dateMs),
            )
            PersistedSms(uri = uri, threadId = threadIdFrom(context, uri, listOf(address)))
        }.onFailure { Log.w(TAG, "inbox persist failed", it) }.getOrDefault(PersistedSms())
    }

    fun insertSent(
        context: Context,
        addresses: Collection<String>,
        body: String,
        dateMs: Long,
        threadId: Long? = null,
    ): PersistedSms {
        if (!SmsRole.isHeld(context)) return PersistedSms()
        val destinations = addresses.map(::displaySmsAddress).filter { it.isNotBlank() }.distinct()
        if (destinations.isEmpty()) return PersistedSms()
        return runCatching {
            val resolvedThread = threadId
                ?: threadId(context, destinations)
            destinations.forEach { destination ->
                context.contentResolver.insert(
                    Telephony.Sms.Sent.CONTENT_URI,
                    sentSmsValues(destination, body, dateMs, resolvedThread),
                )
            }
            PersistedSms(threadId = resolvedThread)
        }.onFailure { Log.w(TAG, "sent persist failed", it) }.getOrDefault(PersistedSms())
    }

    fun markThreadRead(context: Context, threadId: Long) {
        if (!SmsRole.isHeld(context) || threadId <= 0L) return
        val values = ContentValues().apply {
            put(Telephony.Sms.READ, 1)
            put(Telephony.Sms.SEEN, 1)
        }
        runCatching {
            context.contentResolver.update(
                Telephony.Sms.CONTENT_URI,
                values,
                "${Telephony.Sms.THREAD_ID} = ? AND ${Telephony.Sms.READ} = 0",
                arrayOf(threadId.toString()),
            )
        }.onFailure { Log.w(TAG, "mark SMS thread read failed", it) }
        runCatching {
            context.contentResolver.update(
                Telephony.Mms.CONTENT_URI,
                values,
                "${Telephony.Mms.THREAD_ID} = ? AND ${Telephony.Mms.READ} = 0",
                arrayOf(threadId.toString()),
            )
        }.onFailure { Log.w(TAG, "mark MMS thread read failed", it) }
    }

    fun threadId(context: Context, addresses: Collection<String>): Long? = runCatching {
        val dests = addresses.map(::displaySmsAddress).filter { it.isNotBlank() }.toSet()
        if (dests.isEmpty()) return null
        Telephony.Threads.getOrCreateThreadId(context, dests)
    }.getOrNull()

    private fun threadIdFrom(context: Context, uri: Uri?, addresses: Collection<String>): Long? {
        if (uri != null) {
            runCatching {
                context.contentResolver.query(
                    uri,
                    arrayOf(Telephony.Sms.THREAD_ID),
                    null,
                    null,
                    null,
                )?.use { cursor ->
                    if (cursor.moveToFirst()) cursor.getLong(0).takeIf { it > 0L } else null
                }
            }.getOrNull()?.let { return it }
        }
        return threadId(context, addresses)
    }

    private const val TAG = "TelephonySmsStore"
}

internal fun displaySmsAddress(address: String): String =
    address.trim().removePrefix("tel:").removePrefix("mailto:")

internal fun inboxSmsValues(address: String, body: String, dateMs: Long): ContentValues =
    ContentValues().apply {
        put(Telephony.Sms.ADDRESS, displaySmsAddress(address))
        put(Telephony.Sms.BODY, body)
        put(Telephony.Sms.DATE, dateMs)
        put(Telephony.Sms.DATE_SENT, dateMs)
        put(Telephony.Sms.READ, 0)
        put(Telephony.Sms.SEEN, 0)
        put(Telephony.Sms.TYPE, Telephony.Sms.MESSAGE_TYPE_INBOX)
    }

internal fun sentSmsValues(
    address: String,
    body: String,
    dateMs: Long,
    threadId: Long?,
): ContentValues = ContentValues().apply {
    put(Telephony.Sms.ADDRESS, displaySmsAddress(address))
    put(Telephony.Sms.BODY, body)
    put(Telephony.Sms.DATE, dateMs)
    put(Telephony.Sms.DATE_SENT, dateMs)
    put(Telephony.Sms.READ, 1)
    put(Telephony.Sms.SEEN, 1)
    put(Telephony.Sms.TYPE, Telephony.Sms.MESSAGE_TYPE_SENT)
    if (threadId != null && threadId > 0L) put(Telephony.Sms.THREAD_ID, threadId)
}

/**
 * The default SMS app receives [SMS_DELIVER] and must persist the message.
 * [SMS_RECEIVED] is the non-default / OEM echo path — skip it while we hold
 * the role so the same PDU is not ingested twice.
 */
internal fun shouldIngestSmsBroadcast(action: String?, isDefaultSmsApp: Boolean): Boolean =
    when (action) {
        Telephony.Sms.Intents.SMS_DELIVER_ACTION -> true
        Telephony.Sms.Intents.SMS_RECEIVED_ACTION -> !isDefaultSmsApp
        else -> false
    }

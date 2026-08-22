package app.openbubbles.nativeapp.sms

import app.openbubbles.db.Message
import app.openbubbles.db.Message_
import io.objectbox.BoxStore
import io.objectbox.query.QueryBuilder
import java.util.Date

private const val MAX_CARRIER_CALLBACK_COMPONENTS = 4_096
private const val CARRIER_RECIPIENT_COUNT = "openbubbles.carrier.recipientCount"
private const val CARRIER_PART_COUNT = "openbubbles.carrier.partCount"
private const val CARRIER_SENT_COMPONENTS = "openbubbles.carrier.sent"
private const val CARRIER_DELIVERED_COMPONENTS = "openbubbles.carrier.delivered"
private const val CARRIER_FAILED = "openbubbles.carrier.failed"

internal data class CarrierCallbackIdentity(
    val recipientIndex: Int,
    val partIndex: Int,
) {
    val token: String get() = "$recipientIndex:$partIndex"
}

internal enum class CarrierCallbackKind {
    SENT,
    DELIVERED,
}

/** Durable component outcomes survive process death and never let success erase a failure. */
internal data class CarrierSendProgress(
    val recipientCount: Int,
    val partCount: Int,
    val sent: Set<String> = emptySet(),
    val delivered: Set<String> = emptySet(),
    val failed: Boolean = false,
) {
    init {
        require(recipientCount > 0 && partCount > 0) { "Carrier sends require recipients and parts" }
        require(recipientCount <= MAX_CARRIER_CALLBACK_COMPONENTS / partCount) {
            "Carrier send has too many recipient/part callbacks"
        }
    }

    val expectedCallbacks: Int get() = recipientCount * partCount
    val allSent: Boolean get() = sent.size == expectedCallbacks
    val allDelivered: Boolean get() = allSent && delivered.size == expectedCallbacks

    fun accepts(identity: CarrierCallbackIdentity): Boolean =
        identity.recipientIndex in 0 until recipientCount && identity.partIndex in 0 until partCount

    fun record(
        kind: CarrierCallbackKind,
        identity: CarrierCallbackIdentity,
        successful: Boolean,
    ): CarrierSendProgress {
        if (failed || !accepts(identity)) return this
        if (!successful) return copy(failed = true)
        return when (kind) {
            CarrierCallbackKind.SENT -> copy(sent = sent + identity.token)
            CarrierCallbackKind.DELIVERED -> copy(delivered = delivered + identity.token)
        }
    }

    fun preservingMetadata(metadata: Map<String, Any>?): Map<String, Any> =
        LinkedHashMap<String, Any>(metadata.orEmpty()).apply {
            put(CARRIER_RECIPIENT_COUNT, recipientCount.toLong())
            put(CARRIER_PART_COUNT, partCount.toLong())
            put(CARRIER_SENT_COMPONENTS, sent.joinToString(","))
            put(CARRIER_DELIVERED_COMPONENTS, delivered.joinToString(","))
            put(CARRIER_FAILED, failed)
        }

    companion object {
        fun fromMetadata(metadata: Map<String, Any>?): CarrierSendProgress? {
            val stored = metadata ?: return null
            val recipients = (stored[CARRIER_RECIPIENT_COUNT] as? Number)?.toInt() ?: return null
            val parts = (stored[CARRIER_PART_COUNT] as? Number)?.toInt() ?: return null
            return runCatching {
                CarrierSendProgress(
                    recipientCount = recipients,
                    partCount = parts,
                    sent = callbackTokens(stored[CARRIER_SENT_COMPONENTS]),
                    delivered = callbackTokens(stored[CARRIER_DELIVERED_COMPONENTS]),
                    failed = stored[CARRIER_FAILED] == true,
                ).let { progress ->
                    progress.copy(
                        sent = progress.sent.filterTo(linkedSetOf()) { progress.acceptsToken(it) },
                        delivered = progress.delivered.filterTo(linkedSetOf()) { progress.acceptsToken(it) },
                    )
                }
            }.getOrNull()
        }

        private fun callbackTokens(value: Any?): Set<String> =
            (value as? String)
                ?.takeIf(String::isNotBlank)
                ?.split(',')
                ?.take(MAX_CARRIER_CALLBACK_COMPONENTS)
                ?.toCollection(linkedSetOf())
                .orEmpty()
    }

    private fun acceptsToken(token: String): Boolean {
        val separator = token.indexOf(':')
        if (separator <= 0 || separator == token.lastIndex) return false
        val recipient = token.substring(0, separator).toIntOrNull() ?: return false
        val part = token.substring(separator + 1).toIntOrNull() ?: return false
        return accepts(CarrierCallbackIdentity(recipient, part))
    }
}

internal fun carrierCallbackRequestCode(
    guid: String,
    action: String,
    recipientIndex: Int,
    partIndex: Int,
): Int {
    var result = guid.hashCode()
    result = 31 * result + action.hashCode()
    result = 31 * result + recipientIndex
    return 31 * result + partIndex
}

/** Record the complete callback shape before the first irreversible modem call. */
internal fun prepareCarrierSendStatus(
    store: BoxStore,
    guid: String,
    recipientCount: Int,
    partCount: Int,
) {
    val progress = CarrierSendProgress(recipientCount, partCount)
    store.runInTx {
        val box = store.boxFor(Message::class.java)
        val message = box.query()
            .equal(Message_.guid, guid, QueryBuilder.StringOrder.CASE_SENSITIVE)
            .build().use { it.findFirst() }
            ?: error("Carrier message $guid is unavailable")
        check(message.chat.target?.isRpSms == true) { "Carrier callbacks require a SIM conversation" }
        message.metadata = progress.preservingMetadata(message.metadata)
        box.put(message)
    }
}

/** Apply reordered/duplicate carrier callbacks atomically against the latest ObjectBox row. */
internal fun applyCarrierSendStatus(
    store: BoxStore,
    guid: String,
    kind: CarrierCallbackKind,
    identity: CarrierCallbackIdentity,
    successful: Boolean,
    failureDescription: String,
    deliveredAt: Date = Date(),
): Boolean = store.callInTx {
    val box = store.boxFor(Message::class.java)
    val message = box.query()
        .equal(Message_.guid, guid, QueryBuilder.StringOrder.CASE_SENSITIVE)
        .build().use { it.findFirst() }
        ?: return@callInTx false
    if (message.chat.target?.isRpSms != true) return@callInTx false

    val current = CarrierSendProgress.fromMetadata(message.metadata)
        ?: CarrierSendProgress(recipientCount = 1, partCount = 1)
    if (current.failed || message.error?.let { it != 0L } == true || message.errorMessage != null) {
        return@callInTx false
    }
    val updated = current.record(kind, identity, successful)
    if (updated == current) return@callInTx false

    message.metadata = updated.preservingMetadata(message.metadata)
    when {
        updated.failed -> {
            message.error = 1L
            message.errorMessage = failureDescription.take(200)
            message.sendingServiceId = null
            message.dateDelivered = null
        }
        updated.allSent -> {
            message.sendingServiceId = null
            if (updated.allDelivered && message.dateDelivered == null) {
                message.dateDelivered = deliveredAt
            }
        }
    }
    box.put(message)
    true
}

package app.openbubbles.nativeapp.sms

import java.security.MessageDigest
import uniffi.rust_lib_bluebubbles.UConversation
import uniffi.rust_lib_bluebubbles.UIndexedPart
import uniffi.rust_lib_bluebubbles.UMessage
import uniffi.rust_lib_bluebubbles.UMessageInst
import uniffi.rust_lib_bluebubbles.UPart
import uniffi.rust_lib_bluebubbles.UPushMessage

/**
 * Pure builders that shape on-device (SIM) SMS/MMS deliveries into the same
 * [UPushMessage.IMessage] payload the rustpush ingest pipeline consumes, so a
 * local SMS lands in the DB exactly like an iMessage push would: service
 * `SMS` ([UMessage.Normal.isSms]), sender = the originating address, chat
 * resolved via the conversation participants (isRpSms chats are created and
 * matched by [app.openbubbles.core.intake.MessageIngestor] like any relayed
 * SMS).
 *
 * Everything here is plain Kotlin (no Android imports) so the receive→ingest
 * contract is unit-testable on the JVM; the broadcast receivers in this
 * package only translate platform objects into these inputs.
 */
object SmsPushBuilder {

    /** One media part of an incoming MMS (payload bytes stay with the caller). */
    data class MmsAttachment(
        val mime: String,
        val name: String,
    )

    /** Normalizes a telephony address to the rust handle style ("tel:+1…"). */
    fun toRustAddress(raw: String): String {
        val trimmed = raw.trim()
        return if (trimmed.contains('@')) {
            "mailto:" + trimmed.removePrefix("mailto:")
        } else {
            // Keep only the E.164 payload: leading + and digits (drops the
            // formatting noise PDUs/providers sometimes carry).
            "tel:" + trimmed.removePrefix("tel:").filter { it.isDigit() || it == '+' }
        }
    }

    /**
     * Deterministic guid for a broadcast SMS so a redelivered SMS_RECEIVED
     * broadcast dedupes on the Message.guid unique constraint instead of
     * double-ingesting. Keyed on sender+timestamp+body (the concatenation of
     * one broadcast's PDUs is stable).
     */
    fun smsGuid(senderRaw: String, timestampMs: Long, body: String): String =
        "sms-" + sha256Prefix("${toRustAddress(senderRaw)}|$timestampMs|$body", 24)

    /** Deterministic guid for a provider MMS row (redelivery-safe). */
    fun mmsGuid(providerRowId: Long): String = "mms-$providerRowId"

    /**
     * Builds the ingest payload for one reassembled SMS broadcast.
     *
     * @param senderAddress originating address as delivered by the PDU
     *   (usually E.164 like "+15551234567"; emails are possible for some
     *   MMS-gateway SMS).
     * @param myPhoneHandles my rust-style tel: handles, when known — included
     *   in the participants so the ingestor picks the right `usingHandle`;
     *   absent when the app is not logged into iMessage (receive still works).
     */
    fun buildIncomingSms(
        senderAddress: String,
        body: String,
        timestampMs: Long,
        myPhoneHandles: Collection<String> = emptyList(),
    ): UPushMessage {
        val sender = toRustAddress(senderAddress)
        return UPushMessage.IMessage(
            UMessageInst(
                id = smsGuid(senderAddress, timestampMs, body),
                sender = sender,
                conversation = UConversation(
                    participants = (listOf(sender) + myPhoneHandles).distinct(),
                    cvName = null,
                    senderGuid = null,
                    afterGuid = null,
                ),
                message = normal(listOf(textPart(body))),
                sentTimestamp = timestampMs.coerceAtLeast(0L).toULong(),
                sendDelivered = false,
                verificationFailed = false,
            ),
        )
    }

    /**
     * Builds the ingest payload for an MMS already decoded from the telephony
     * provider (the system/default SMS app downloads the content; we read the
     * stored row). Media parts become [UPart.Attachment]s with deterministic
     * guids (`"<msgId>_<idx>"`, the mapping the ingestor persists); the
     * receiver copies the payloads into the attachment store afterwards.
     *
     * @param senderAddress FROM address from the provider addr table.
     * @param participantAddresses TO/CC addresses (empty for 1:1).
     */
    fun buildIncomingMms(
        guid: String,
        senderAddress: String,
        participantAddresses: Collection<String>,
        text: String?,
        attachments: List<MmsAttachment>,
        timestampMs: Long,
        myPhoneHandles: Collection<String> = emptyList(),
    ): UPushMessage {
        val sender = toRustAddress(senderAddress)
        val parts = buildList {
            if (!text.isNullOrEmpty()) add(textPart(text))
            attachments.forEachIndexed { index, att ->
                add(
                    UIndexedPart(
                        part = UPart.Attachment(
                            part = index.toULong(),
                            uti = utiForMime(att.mime),
                            mime = att.mime,
                            name = att.name,
                            iris = false,
                            // No rustpush transfer for on-device media — the
                            // receiver persists the bytes locally instead.
                            xml = "",
                        ),
                        idx = index.toULong(),
                        extJson = null,
                    ),
                )
            }
        }
        return UPushMessage.IMessage(
            UMessageInst(
                id = guid,
                sender = sender,
                conversation = UConversation(
                    participants = (listOf(sender) + participantAddresses.map(::toRustAddress) +
                        myPhoneHandles).distinct(),
                    cvName = null,
                    senderGuid = null,
                    afterGuid = null,
                ),
                message = normal(parts),
                sentTimestamp = timestampMs.coerceAtLeast(0L).toULong(),
                sendDelivered = false,
                verificationFailed = false,
            ),
        )
    }

    /** Best-effort UTI for a mime type (bubble rendering + icon picking). */
    fun utiForMime(mime: String?): String {
        val normalized = mime?.substringBefore(';')?.trim()?.lowercase() ?: return "public.data"
        return when {
            normalized.startsWith("image/") -> when (normalized) {
                "image/jpeg" -> "public.jpeg"
                "image/png" -> "public.png"
                "image/gif" -> "com.compuserve.gif"
                "image/heic" -> "public.heic"
                "image/webp" -> "org.webmproject.webp"
                "image/bmp" -> "com.microsoft.bmp"
                else -> "public.image"
            }
            normalized.startsWith("video/") -> when (normalized) {
                "video/mp4" -> "public.mpeg-4"
                "video/quicktime" -> "com.apple.quicktime-movie"
                "video/3gpp" -> "public.3gpp"
                else -> "public.movie"
            }
            normalized.startsWith("audio/") -> when (normalized) {
                "audio/mpeg" -> "public.mp3"
                "audio/mp4" -> "public.mpeg-4-audio"
                "audio/amr" -> "public.amr-audio"
                "audio/ogg" -> "org.xiph.ogg-audio"
                else -> "public.audio"
            }
            normalized == "text/vcard" -> "public.vcard"
            normalized == "application/pdf" -> "com.adobe.pdf"
            else -> "public.data"
        }
    }

    // ------------------------------------------------------------------
    // Internals
    // ------------------------------------------------------------------

    private fun textPart(text: String) = UIndexedPart(UPart.Text(text, ""), null, null)

    private fun normal(parts: List<UIndexedPart>) = UMessage.Normal(
        parts = parts,
        effect = null,
        replyGuid = null,
        replyPart = null,
        subject = null,
        voice = false,
        isSms = true,
        appJson = null,
        linkJson = null,
        profileJson = null,
    )

    private fun sha256Prefix(input: String, length: Int): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(input.toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it) }.take(length)
    }
}

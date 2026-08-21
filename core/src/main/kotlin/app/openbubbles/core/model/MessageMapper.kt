package app.openbubbles.core.model

import app.openbubbles.db.Attachment
import app.openbubbles.db.Message
import uniffi.rust_lib_bluebubbles.UIndexedPart
import uniffi.rust_lib_bluebubbles.UMessage
import uniffi.rust_lib_bluebubbles.UMessageInst
import uniffi.rust_lib_bluebubbles.UPart
import java.util.Date
import kotlin.text.Regex

/**
 * Pure mapping from UniFFI push types ([UMessageInst] inside
 * [uniffi.rust_lib_bluebubbles.UPushMessage.IMessage]) to :db entities.
 *
 * Port of `RustPushBackend.reflectMessageDyn` +
 * `indexedPartsToAttributedBodyDyn` (lib/services/rustpush/rustpush_service.dart)
 * with the ObjectBox/network side effects stripped out — everything here is a
 * plain object transformation so it is unit-testable without a store. The
 * [app.openbubbles.core.intake.MessageIngestor] owns handle/chat resolution and
 * persistence.
 *
 * Simplifications for MVP (documented in the module report):
 * - Attributed body runs are not materialized; only the flattened `text` is
 *   kept (plus `dbPayloadData` for app balloons and `dbMetadata` for link
 *   metadata JSON).
 * - The serialized rustpush Attachment XML (UPart.Attachment.xml) is stored
 *   in the attachment row's flex `metadata["rustpush"]`; the transfer layer
 *   restores it via `restoreAttachment` for downloads.
 * - Sticker/extension reaction bodies are expanded into ordinary attachment
 *   rows with positional metadata retained in `Attachment.metadata`.
 */
object MessageMapper {
    private const val REPLY_RUNS_PREFIX = "openbubbles.reply-runs.v1|"


    // Tapback type strings — 1:1 with lib/helpers/ui/reaction_helpers.dart
    // (ReactionTypes). A leading '-' means the tapback was removed.
    const val REACTION_LOVE = "love"
    const val REACTION_LIKE = "like"
    const val REACTION_DISLIKE = "dislike"
    const val REACTION_LAUGH = "laugh"
    const val REACTION_EMPHASIZE = "emphasize"
    const val REACTION_QUESTION = "question"
    const val REACTION_EMOJI = "emoji"
    const val REACTION_STICKERBACK = "stickerback"
    const val REACTION_STICKER = "sticker"

    /**
     * A mapped message plus its attachment metadata. `attachments` are unsaved
     * entity instances; the ingestor wires `message`/`chat` relations and puts
     * them in one transaction.
     */
    data class Mapped(
        val message: Message,
        val attachments: List<Attachment>,
    )

    /** Strip tel:/mailto: prefixes — `RustPushBBUtils.rustHandleToBB`. */
    fun normalizeAddress(rustHandle: String): String =
        rustHandle.removePrefix("tel:").removePrefix("mailto:")

    /** Reverse of [normalizeAddress] — `RustPushBBUtils.bbHandleToRust`. */
    fun toRustHandle(address: String): String =
        if (address.contains("@")) "mailto:$address" else "tel:$address"

    fun dateFromMs(ms: ULong): Date = Date(ms.toLong())

    /**
     * Flattens indexed parts into plain text + attachment rows, mirroring
     * `indexedPartsToAttributedBodyDyn`:
     * - Text contributes its text.
     * - Mention contributes the display text.
     * - Attachment contributes a single space and an Attachment row with
     *   guid `"<msgId>_<partIdx>"` (partIdx = explicit idx or the running
     *   attachment count). `iris` (live-photo sidecar) and `smil` parts are
     *   skipped by default, matching Dart's incoming-MMS behavior. A staged
     *   outgoing iMessage can preserve a user-selected SMIL file explicitly.
     * - Object parts (app payloads) contribute nothing to the text.
     */
    fun mapParts(
        parts: List<UIndexedPart>,
        msgId: String,
        isOutgoing: Boolean,
        preserveSmilAttachments: Boolean = false,
    ): Pair<String, List<Attachment>> {
        val text = StringBuilder()
        val attachments = ArrayList<Attachment>()
        var bodyRunCount = 0L
        for (indexed in parts) {
            val fieldIdx = indexed.idx?.toLong() ?: attachments.size.toLong()
            when (val part = indexed.part) {
                is UPart.Text -> {
                    text.append(part.text)
                    bodyRunCount += 1
                }
                is UPart.Mention -> {
                    text.append(part.text)
                    bodyRunCount += 1
                }
                is UPart.Attachment -> {
                    if (part.mime == "application/smil" && !preserveSmilAttachments) {
                        continue // MMS layout, no display value
                    }
                    attachments += Attachment().apply {
                        guid = if (part.iris) "${msgId}_${fieldIdx}_iris" else "${msgId}_$fieldIdx"
                        uti = part.uti
                        mimeType = part.mime
                        this.isOutgoing = isOutgoing
                        transferName = part.name.replace("/", "_").replace("\\", "_")
                        isDownloaded = isOutgoing
                        // Persist the serialized rustpush Attachment so the
                        // transfer layer can restore + download it later
                        // (Dart stored this as metadata["rustpush"]).
                        metadata = linkedMapOf<String, Any>(
                            // The old attributed-body decoder used the run's
                            // ordinal for attachment reply targeting, which
                            // can differ from the transfer GUID suffix.
                            "messagePart" to if (part.iris) part.part.toLong() else bodyRunCount,
                        ).apply {
                            if (part.iris) put("livePhotoIris", true)
                            if (part.xml.isNotEmpty()) put("rustpush", part.xml)
                            if (part.xml.isNotEmpty()) {
                                indexed.extJson?.let { extension ->
                                    extractJsonNumber(extension, "spw")?.let { put("sticker.msgWidth", it) }
                                    extractJsonNumber(extension, "sro")?.let { put("sticker.rotation", it) }
                                    extractJsonNumber(extension, "ssa")?.let { put("sticker.scale", it) }
                                    extractJsonNumber(extension, "sxs")?.let { put("sticker.normalizedX", it) }
                                    extractJsonNumber(extension, "sys")?.let { put("sticker.normalizedY", it) }
                                    extractJsonNumber(extension, "stickerEffectType")?.let {
                                        put("sticker.effectType", it.toLong())
                                    }
                                    extractJsonString(extension, "sid")?.let { put("sticker.id", it) }
                                    put("sticker.extension", extension)
                                }
                            }
                        }
                    }
                    if (!part.iris) {
                        text.append(' ')
                        bodyRunCount += 1
                    }
                }
                is UPart.Object -> Unit // handled via appJson payload, not body text
            }
        }
        pairLivePhotoAttachments(attachments)
        return text.toString() to attachments
    }

    /**
     * Reconstructs the NSAttributedString runs Apple addresses in `tg` reply
     * metadata. Kotlin `String.length` is UTF-16 code units, matching NSRange
     * and the Dart implementation this native port replaces.
     */
    fun encodeReplyPartLocators(
        parts: List<UIndexedPart>,
        preserveSmilAttachments: Boolean = false,
    ): String? {
        val locators = linkedMapOf<Long, String>()
        var bodyOffset = 0
        var bodyRunCount = 0L
        var attachmentCount = 0L
        for (indexed in parts) {
            val fieldIdx = indexed.idx?.toLong() ?: attachmentCount
            val (messagePart, runLength) = when (val part = indexed.part) {
                is UPart.Text -> fieldIdx to part.text.length
                is UPart.Mention -> fieldIdx to part.text.length
                is UPart.Attachment -> {
                    if (
                        part.iris ||
                        (part.mime == "application/smil" && !preserveSmilAttachments)
                    ) {
                        continue
                    }
                    bodyRunCount to 1
                }
                is UPart.Object -> continue
            }
            locators.putIfAbsent(messagePart, "$messagePart:$bodyOffset:$runLength")
            bodyOffset += runLength
            bodyRunCount += 1
            if (indexed.part is UPart.Attachment) attachmentCount += 1
        }
        return locators.values.takeIf { it.isNotEmpty() }
            ?.joinToString(separator = ";", prefix = REPLY_RUNS_PREFIX)
    }

    fun decodeReplyPartLocators(encoded: String?): Map<Long, String> {
        if (encoded?.startsWith(REPLY_RUNS_PREFIX) != true) return emptyMap()
        return encoded.removePrefix(REPLY_RUNS_PREFIX)
            .split(';')
            .mapNotNull { locator ->
                val fields = locator.split(':')
                if (fields.size != 3 || fields.any { it.toLongOrNull() == null }) return@mapNotNull null
                fields[0].toLong() to locator
            }
            .toMap(linkedMapOf())
    }

    fun replyPartIndex(locator: String?): Long? =
        locator?.substringBefore(':')?.toLongOrNull()

    /** Rust `MessageParts::raw_text` equivalent (used for the empty-message guard). */
    fun rawText(parts: List<UIndexedPart>): String =
        parts.joinToString("") { indexed ->
            when (val part = indexed.part) {
                is UPart.Text -> part.text
                is UPart.Mention -> "@${part.text}"
                is UPart.Attachment -> "\uFFFC"
                is UPart.Object -> "\uFFFD\uFFFC"
            }
        }

    fun hasAttachmentParts(parts: List<UIndexedPart>): Boolean =
        parts.any { it.part is UPart.Attachment }

    /**
     * `UMessage.Normal` → Message entity (reflectMessageDyn's Message_Message
     * branch, minus SMS staging logic which the ingestor owns).
     */
    fun mapNormal(
        inst: UMessageInst,
        normal: UMessage.Normal,
        myHandles: Set<String>,
        preserveSmilAttachments: Boolean = false,
    ): Mapped {
        val sender = requireNotNull(inst.sender) { "Normal message without sender" }
        val isFromMe = myHandles.contains(sender)
        val (text, attachments) = mapParts(
            normal.parts,
            inst.id,
            isFromMe,
            preserveSmilAttachments = preserveSmilAttachments,
        )

        val appBalloon = normal.appJson != null
        val message = Message().apply {
            guid = inst.id
            this.text = text
            this.isFromMe = isFromMe
            dateCreated = dateFromMs(inst.sentTimestamp)
            subject = normal.subject
            threadOriginatorGuid = normal.replyGuid
            threadOriginatorPart = normal.replyPart
            dbAttributedBody = encodeReplyPartLocators(
                normal.parts,
                preserveSmilAttachments = preserveSmilAttachments,
            )
            expressiveSendStyleId = normal.effect
            hasAttachments = attachments.isNotEmpty()
            // App balloons / link previews: keep the raw JSON the rust layer
            // serialized; UI rendering of balloons is out of MVP scope.
            hasApplePayloadData = appBalloon
            if (appBalloon) {
                dbPayloadData = normal.appJson
                balloonBundleId = extractJsonString(normal.appJson, "bundleId")
                    ?: extractJsonString(normal.appJson, "bundle_id")
            }
            if (normal.linkJson != null) {
                dbMetadata = normal.linkJson
                if (balloonBundleId == null) balloonBundleId = "com.apple.messages.URLBalloonProvider"
            }
            verificationFailed = inst.verificationFailed
        }
        return Mapped(message, attachments)
    }

    internal fun pairLivePhotoAttachments(attachments: List<Attachment>) {
        val unpairedMotion = attachments.filter { attachment ->
            attachment.metadata?.get("livePhotoIris") == true || isMovAttachment(attachment)
        }.toMutableList()
        attachments.asSequence()
            .filterNot { it in unpairedMotion }
            .filter(::isStillLivePhotoCandidate)
            .forEach { still ->
                val motion = unpairedMotion.firstOrNull { candidate ->
                    sameLivePhotoStem(still.transferName, candidate.transferName) ||
                        messagePart(still) == messagePart(candidate)
                } ?: return@forEach
                still.hasLivePhoto = true
                still.metadata = linkedMapOf<String, Any>().apply {
                    still.metadata?.let(::putAll)
                    motion.guid?.let { put("livePhotoMotionGuid", it) }
                }
                motion.metadata = linkedMapOf<String, Any>().apply {
                    motion.metadata?.let(::putAll)
                    put("livePhotoMotion", true)
                    still.guid?.let { put("livePhotoStillGuid", it) }
                }
                unpairedMotion.remove(motion)
            }
    }

    private fun messagePart(attachment: Attachment): Long? =
        (attachment.metadata?.get("messagePart") as? Number)?.toLong()

    private fun isStillLivePhotoCandidate(attachment: Attachment): Boolean {
        val mime = attachment.mimeType.orEmpty().lowercase()
        val uti = attachment.uti.orEmpty().lowercase()
        val name = attachment.transferName.orEmpty().lowercase()
        return mime.startsWith("image/") || "image" in uti || name.endsWith(".heic") || name.endsWith(".heif")
    }

    private fun isMovAttachment(attachment: Attachment): Boolean {
        val mime = attachment.mimeType.orEmpty().lowercase()
        val uti = attachment.uti.orEmpty().lowercase()
        val name = attachment.transferName.orEmpty().lowercase()
        return mime == "video/quicktime" || "quicktime" in uti || name.endsWith(".mov")
    }

    private fun sameLivePhotoStem(first: String?, second: String?): Boolean {
        val firstStem = first?.substringBeforeLast('.')?.lowercase()?.takeIf { it.isNotBlank() } ?: return false
        val secondStem = second?.substringBeforeLast('.')?.lowercase()?.takeIf { it.isNotBlank() } ?: return false
        return firstStem == secondStem
    }

    /**
     * `UMessage.React` → reaction Message entity. `reactionJson` is the serde
     * encoding of rustpush's `ReactMessageType`, e.g.
     * `{"React":{"reaction":"Heart","enable":true}}` or
     * `{"React":{"reaction":{"Emoji":"❤️"},"enable":false}}` or
     * `{"Extension":{...,"is_meta":false}}`.
     */
    fun mapReaction(inst: UMessageInst, react: UMessage.React, myHandles: Set<String>): Mapped {
        val sender = requireNotNull(inst.sender) { "Reaction without sender" }
        val (type, emoji) = parseReaction(react.reactionJson)
        val (_, attachments) = mapParts(react.parts, inst.id, myHandles.contains(sender))
        val message = Message().apply {
            guid = inst.id
            this.isFromMe = myHandles.contains(sender)
            dateCreated = dateFromMs(inst.sentTimestamp)
            associatedMessagePart = react.toPart?.toLong()
            // Dart: guid kept unless the reaction is not renderable at all.
            associatedMessageGuid = if (type == null) null else react.toUuid
            associatedMessageType = if (type == "meta") null else type
            associatedMessageEmoji = emoji
            text = react.toText.takeIf { type == REACTION_STICKER || type == REACTION_STICKERBACK }
            verificationFailed = inst.verificationFailed
        }
        return Mapped(message, attachments)
    }

    /** Returns (tapback type or null, custom emoji or null). */
    fun parseReaction(reactionJson: String): Pair<String?, String?> {
        val isExtension = reactionJson.contains("\"Extension\"")
        return if (isExtension) {
            // Extension (sticker/app) reactions: sticker semantics only for MVP.
            if (reactionJson.contains("\"is_meta\":true")) "meta" to null else REACTION_STICKER to null
        } else {
            var type: String? = when {
                reactionJson.contains("\"reaction\":\"Heart\"") -> REACTION_LOVE
                reactionJson.contains("\"reaction\":\"Like\"") -> REACTION_LIKE
                reactionJson.contains("\"reaction\":\"Dislike\"") -> REACTION_DISLIKE
                reactionJson.contains("\"reaction\":\"Laugh\"") -> REACTION_LAUGH
                reactionJson.contains("\"reaction\":\"Emphasize\"") -> REACTION_EMPHASIZE
                reactionJson.contains("\"reaction\":\"Question\"") -> REACTION_QUESTION
                reactionJson.contains("\"Sticker\"") -> REACTION_STICKERBACK
                reactionJson.contains("\"Emoji\"") -> REACTION_EMOJI
                else -> null
            }
            var emoji: String? = null
            if (type == REACTION_EMOJI) {
                emoji = extractJsonString(reactionJson, "Emoji") ?: extractEmojiValue(reactionJson)
                if (emoji == null) type = null // unparseable payload: do not render
            }
            // Tapback removal is encoded with a '-' prefix (Dart parity).
            if (type != null && reactionJson.contains("\"enable\":false")) type = "-$type"
            type to emoji
        }
    }

    /** `UMessage.Rename` → group-rename event (itemType 2 / groupActionType 2). */
    fun mapRename(inst: UMessageInst, rename: UMessage.Rename, myHandles: Set<String>): Mapped {
        val message = Message().apply {
            guid = inst.id
            isFromMe = inst.sender?.let { myHandles.contains(it) } ?: false
            dateCreated = dateFromMs(inst.sentTimestamp)
            itemType = 2
            groupActionType = 2
            groupTitle = rename.newName
        }
        return Mapped(message, emptyList())
    }

    /** `UMessage.IconChange` → group-photo event (itemType 3 / groupActionType 1). */
    fun mapIconChange(inst: UMessageInst, myHandles: Set<String>): Mapped {
        val message = Message().apply {
            guid = inst.id
            isFromMe = inst.sender?.let { myHandles.contains(it) } ?: false
            dateCreated = dateFromMs(inst.sentTimestamp)
            itemType = 3
            groupActionType = 1
        }
        return Mapped(message, emptyList())
    }

    /**
     * Participant-change event rows — `updateChatParticipants`. One row per
     * added/removed member; guids derive from the message id so replays
     * dedupe (the first event keeps the raw id, later ones get a suffix so
     * the unique constraint does not silently drop them the way Dart's
     * swallowed UniqueViolationException does).
     */
    fun mapParticipantEvent(
        inst: UMessageInst,
        memberAddress: String,
        memberRowId: Long?,
        added: Boolean,
        senderLeft: Boolean,
        myHandles: Set<String>,
        index: Int,
    ): Message = Message().apply {
        guid = if (index == 0) inst.id else "${inst.id}-${if (added) "add" else "remove"}-$index"
        isFromMe = inst.sender?.let { myHandles.contains(it) } ?: false
        dateCreated = dateFromMs(inst.sentTimestamp)
        if (added) {
            itemType = 1
            groupActionType = 0
        } else if (senderLeft) {
            itemType = 3 // left the conversation
            groupActionType = 0
        } else {
            itemType = 1
            groupActionType = 1 // removed by someone else
        }
        otherHandle = memberRowId
    }

    // ------------------------------------------------------------------
    // Minimal JSON helpers (avoids a JSON dependency in the module; the
    // payloads we read are flat serde strings produced by rustpush).
    // ------------------------------------------------------------------

    private fun extractJsonString(json: String?, key: String): String? {
        if (json == null) return null
        val match = Regex("\"$key\"\\s*:\\s*\"((?:[^\"\\\\]|\\\\.)*)\"").find(json) ?: return null
        return match.groupValues[1].unescapeJson()
    }

    private fun extractJsonNumber(json: String, key: String): Double? =
        Regex("\\\"$key\\\"\\s*:\\s*(-?(?:\\d+(?:\\.\\d*)?|\\.\\d+)(?:[eE][+-]?\\d+)?)")
            .find(json)
            ?.groupValues
            ?.getOrNull(1)
            ?.toDoubleOrNull()

    private fun extractEmojiValue(json: String): String? {
        // {"Emoji":"👍"} — the emoji is the first string value inside the object.
        val match = Regex("\\{\"Emoji\"\\s*:\\s*\"((?:[^\"\\\\]|\\\\.)*)\"\\}").find(json) ?: return null
        return match.groupValues[1].unescapeJson()
    }

    private fun String.unescapeJson(): String = buildString {
        var i = 0
        while (i < this@unescapeJson.length) {
            val c = this@unescapeJson[i]
            if (c == '\\' && i + 1 < length) {
                when (val next = this@unescapeJson[i + 1]) {
                    'n' -> append('\n'); 't' -> append('\t'); 'r' -> append('\r')
                    'b' -> append('\b'); 'f' -> append('')
                    'u' -> {
                        val hex = substring(i + 2, minOf(i + 6, length))
                        val code = hex.toIntOrNull(16)
                        if (code != null) { append(code.toChar()); i += 4 } else append(next)
                    }
                    else -> append(next)
                }
                i += 2
            } else {
                append(c); i++
            }
        }
    }
}

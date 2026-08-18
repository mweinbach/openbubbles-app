package app.openbubbles.core.model

import java.util.Date

/**
 * Delivery status of an outgoing bubble, mirroring the Flutter app's
 * `Message.indicatorToShow` precedence (error > still-sending > read >
 * delivered > sent).
 */
enum class MessageStatus {
    /** Staged locally, send in flight. */
    SENDING,

    /** Handed off, no receipt yet. */
    SENT,

    /** dateDelivered set (or an explicit Delivered receipt). */
    DELIVERED,

    /** dateRead set. */
    READ,

    /** Send failed (Error receipt / SMS confirm failure). */
    FAILED,
}

/** Display classification used by the transcript UI to pick a bubble style. */
enum class MessageKind {
    /** Plain text/attachment message. */
    TEXT,

    /** Tapback reaction (associatedMessage* fields set). */
    REACTION,

    /** Group event: rename, participant change, leave, photo change. */
    GROUP_EVENT,
}

/** One sticker image positioned over a target message bubble. */
data class StickerPlacement(
    val reactionGuid: String,
    val attachmentGuid: String,
    val targetPart: Long,
    val messageWidth: Double,
    val normalizedX: Double,
    val normalizedY: Double,
    val rotation: Double,
    val scale: Double,
    val effectType: Long,
    val downloaded: Boolean,
)

/**
 * UI projection of a [app.openbubbles.db.Message].
 *
 * `text` carries the plain-text rendering (mentions collapsed to their text,
 * attachments rendered as a single space — same convention as the Flutter
 * app's `Message.text`). Group events get a human-readable sentence in
 * [groupEventText]; reactions carry the tapback summary in [reactionEmoji].
 */
data class MessageItem(
    /** ObjectBox entity id of the message. */
    val id: Long,
    /** Message GUID (staging guid while a send is in flight). */
    val guid: String,
    /** Plain text body (empty for pure attachments). */
    val text: String,
    /** True when the message was sent from one of my handles. */
    val isFromMe: Boolean,
    /** Sender address (null for messages from me). */
    val senderAddress: String?,
    val date: Date?,
    val dateDelivered: Date?,
    val dateRead: Date?,
    val status: MessageStatus,
    val kind: MessageKind,
    /** Human-readable group event sentence (only for [MessageKind.GROUP_EVENT]). */
    val groupEventText: String?,
    /** Tapback type string ("love", "-love", "emoji", …) — only for reactions. */
    val reactionType: String?,
    /** Custom emoji for emoji tapbacks. */
    val reactionEmoji: String?,
    /** True when the message carries attachment metadata. */
    val hasAttachments: Boolean,
    /** Attachment count (metadata only; transfers are driven elsewhere). */
    val attachmentCount: Int,
    /** Non-null when this message is a reply (thread originator guid). */
    val threadOriginatorGuid: String?,
    /** Part index on the thread originator that this reply is attached to. */
    val threadOriginatorPart: Long?,
    /** Full Apple reply run locator (`part:start:length`) when this is a reply. */
    val threadOriginatorLocator: String?,
    /** Apple reply run locators keyed by the selectable part on this message. */
    val replyPartLocators: Map<Long, String> = emptyMap(),
    /** GUID of the message this reaction is attached to (reactions only). */
    val associatedMessageGuid: String?,
    /** Expressive send style (screen effects), e.g. "com.apple.MobileSMS.expressivesend.gentle". */
    val expressiveSendStyleId: String?,
    /** Serialized Apple LinkPresentation metadata for rich URL previews. */
    val richLinkMetadataJson: String? = null,
    /** Parsed iMessage app-balloon content, including calm unsupported fallback. */
    val interactivePayload: InteractivePayload? = null,
    /** Active positional stickers layered over this message. */
    val stickers: List<StickerPlacement> = emptyList(),
    /** Optional iMessage subject line. */
    val subject: String? = null,
    /** Protocol chat that carried this message inside a grouped contact thread. */
    val chatId: Long? = null,
    val isBookmarked: Boolean = false,
    val hasBeenForwarded: Boolean = false,
    val dateDeleted: Date? = null,
    val errorCode: Long? = null,
    val errorMessage: String? = null,
    val partCount: Int = 1,
)

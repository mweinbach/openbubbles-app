package app.openbubbles.nativeapp.data

import java.io.File
import kotlinx.coroutines.flow.Flow

/**
 * UI-facing data contracts for the native Android client.
 *
 * These mirror the shapes the `core` module repositories will expose; the UI
 * depends only on these interfaces so the fake implementations here can be
 * swapped for the real ones without touching any composable or ViewModel.
 *
 * All timestamps are epoch milliseconds; [MessageItem.id] and `before` keys
 * are monotonically increasing message ids (ascending in time).
 */
data class ChatListItem(
    val id: Long,
    val title: String,
    val snippet: String?,
    val date: Long,
    val unread: Int,
    val pinned: Boolean,
    val avatarColor: Long,
    /**
     * For direct messages: the other participant's handle address. Used to
     * resolve a contact photo for the avatar (null for groups/unknown).
     */
    val avatarAddress: String? = null,
    /** True when this conversation uses the local SIM SMS path. */
    val isSms: Boolean = false,
)

/** Display metadata for an attachment shown in the transcript. */
data class AttachmentMeta(
    /** Stable attachment GUID (used for the viewer route and file lookup). */
    val guid: String,
    val mime: String?,
    val name: String?,
    val sizeBytes: Long?,
    val isImage: Boolean,
    val downloaded: Boolean,
) {
    val isVideo: Boolean get() = mime?.startsWith("video/", ignoreCase = true) == true
}

data class MessageItem(
    val id: Long,
    val text: String,
    val isFromMe: Boolean,
    val date: Long,
    val status: MessageStatus,
    val isGroupEvent: Boolean,
    val reactionEmoji: String?,
    /** First attachment's display metadata, retained for source compatibility. */
    val attachmentMeta: AttachmentMeta? = null,
    /** Every attachment carried by the message, in database order. */
    val attachmentMetas: List<AttachmentMeta> = attachmentMeta?.let(::listOf).orEmpty(),
    /** True when the sender edited the message after sending. */
    val edited: Boolean = false,
    /** True when the message was retracted ("unsent") by its sender. */
    val unsent: Boolean = false,
    /** Sender's handle address (null for messages from me). */
    val senderAddress: String? = null,
    /**
     * Upload progress (bytesDone, bytesTotal) while an outgoing attachment
     * transfer is in flight; total may be 0 when the size is unknown.
     * Null for everything else.
     */
    val uploadProgress: Pair<Long, Long>? = null,
    /**
     * iMessage expressive-send style id ("com.apple.messages.effect.*" screen
     * effects or "com.apple.MobileSMS.expressivesend.*" bubble effects);
     * null when the message has no effect.
     */
    val expressiveSendStyleId: String? = null,
    /** Stable iMessage GUID used by replies, tapbacks, edits, and unsends. */
    val guid: String = id.toString(),
    /** Root message GUID when this message is part of a reply thread. */
    val replyToGuid: String? = null,
)

enum class MessageStatus { SENDING, SENT, DELIVERED, READ, FAILED }

interface ChatListRepository {
    fun chats(): Flow<List<ChatListItem>>
    fun markRead(id: Long)
}

interface MessageListRepository {
    fun messages(chatId: Long, limit: Int, before: Long?): Flow<List<MessageItem>>
    fun loadMore(chatId: Long, before: Long?, count: Int): List<MessageItem>
}

interface Sender {
    suspend fun send(chatId: Long, text: String)

    /** Sends a text reply rooted at [replyGuid]. */
    suspend fun sendReply(chatId: Long, text: String, replyGuid: String) {
        send(chatId, text)
    }

    /**
     * Sends a text with an iMessage expressive-send effect id (e.g.
     * "com.apple.messages.effect.CKConfettiEffect" or
     * "com.apple.MobileSMS.expressivesend.invisibleink"). Null effect falls
     * back to a plain send; senders without effect support inherit this
     * default so existing implementations keep compiling.
     */
    suspend fun sendWithEffect(chatId: Long, text: String, effectId: String?) {
        send(chatId, text)
    }
}

/** Message mutations supported by the on-device iMessage engine. */
interface MessageActions {
    suspend fun react(
        chatId: Long,
        messageGuid: String,
        messageText: String,
        reactionIndex: Int,
        emoji: String? = null,
        enable: Boolean = true,
    )

    suspend fun edit(chatId: Long, messageGuid: String, newText: String)

    suspend fun unsend(chatId: Long, messageGuid: String)
}

/**
 * On-device (SIM) SMS send for chats flagged `isRpSms` — the modem twin of
 * [Sender]. Implementations stage the outgoing row optimistically (SENDING)
 * and resolve the status asynchronously (sent/delivery receipts); see
 * `app.openbubbles.nativeapp.sms.SmsManagerSender`. Kept separate from
 * [Sender] so the iMessage path stays untouched; routing lives in
 * `sms.SmsBridge.routeIfSmsChat`.
 */
interface SmsSender {
    suspend fun send(chatId: Long, text: String)
}

/** A picked outgoing attachment, ready to stage and upload. */
data class OutgoingAttachment(
    /** Local copy of the payload (app cache); the sender moves it into the store. */
    val file: File,
    /** Resolved MIME type ("image/jpeg"; never null). */
    val mime: String,
    /** Best-effort UTI ("public.jpeg"; "public.data" fallback). */
    val uti: String,
    /** Display / transfer name ("trailhead.jpg"). */
    val name: String?,
    /** Payload size in bytes. */
    val sizeBytes: Long,
)

/**
 * Sends one attachment with an optional caption using the same optimistic
 * staging pattern as [Sender]: a staged message row appears immediately
 * (SENDING), upload progress surfaces through the message flow, and errors
 * leave a FAILED bubble.
 */
interface AttachmentSender {
    suspend fun send(chatId: Long, attachment: OutgoingAttachment, caption: String?)
}

/** One live "X is typing…" entry; entries expire automatically upstream. */
data class TypingEntry(
    val chatId: Long,
    val senderAddress: String,
)

/** Live typing indicators across all chats (filtered per chat by the UI). */
interface TypingRepository {
    fun typing(): Flow<List<TypingEntry>>
}

/**
 * Lookups for the attachment viewer: metadata by guid plus the locally
 * stored file (null while the transfer has not been downloaded).
 */
interface AttachmentProvider {
    fun byGuid(guid: String): AttachmentMeta?
    fun localFile(guid: String): File?
}

/**
 * SAM seam matching `core.attachment.AttachmentManager.localFile` so the UI
 * contract stays free of core imports; CoreGraph binds the real manager.
 */
fun interface AttachmentFileManager {
    fun localFile(attachment: app.openbubbles.db.Attachment): File?
}

/**
 * Optional contact-name injection point. CoreGraph sets [contactNames] once
 * `core.contacts.ContactSync` lands (or to a local ObjectBox-backed lookup);
 * callers must treat null and null results as "no contact known".
 */
object UiContacts {
    /** Returns (display name, avatar path) for a handle address, or null. */
    @Volatile
    var contactNames: (suspend (handleAddress: String) -> Pair<String?, String?>?)? = null
}

/** Read-only chat details for the group-info screen. */
interface ChatInfoRepository {
    /** Participant handle addresses of the conversation (excluding me). */
    fun participantAddresses(chatId: Long): List<String>
}

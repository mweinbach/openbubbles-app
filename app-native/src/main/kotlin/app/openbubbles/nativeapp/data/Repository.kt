package app.openbubbles.nativeapp.data

import app.openbubbles.core.attachment.AttachmentMedia
import app.openbubbles.core.attachment.TransferState
import app.openbubbles.core.model.InteractivePayload
import java.io.File
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flowOf

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
    val muted: Boolean = false,
    val notifsSilenced: Boolean = false,
    val archived: Boolean = false,
    /** Locally cached custom group photo. */
    val avatarPath: String? = null,
    val isGroup: Boolean = false,
    /** User-selected background stored only on this Android device. */
    val customBackgroundPath: String? = null,
    /** Apple-synced transcript background received from another device. */
    val transcriptBackgroundPath: String? = null,
    val transcriptBackgroundVersion: Long? = null,
    /** Protocol chats represented by this contact-grouped conversation. */
    val memberChatIds: List<Long> = listOf(id),
    /** Most recently active protocol chat for a new outgoing message. */
    val preferredChatId: Long = id,
    /** User-selected send-from handle (rust form); null follows the default. */
    val senderOverride: String? = null,
    /** My handle this conversation was received on (rust form), when known. */
    val receivedOnHandle: String? = null,
    val dateDeleted: Long? = null,
    val lockChatName: Boolean = false,
    val lockChatIcon: Boolean = false,
    val autoSendReadReceipts: Boolean = false,
    val autoSendTypingIndicators: Boolean = false,
    val blocked: Boolean = false,
    val guid: String = id.toString(),
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
    /** iMessage part index this attachment occupies. */
    val partIndex: Long = 0L,
    val uti: String? = null,
    /** Paired MOV attachment for an Apple Live Photo. */
    val livePhotoMotionGuid: String? = null,
    /** Changes when the hidden motion row finishes after the visible still. */
    val livePhotoMotionDownloaded: Boolean = false,
    /** Internal sidecar rows are downloaded but never rendered independently. */
    val isLivePhotoMotion: Boolean = false,
    /**
     * Identity (path, length, mtime) of the payload readable on disk, or null
     * while nothing is readable. A bubble memoizes its file lookup, and a
     * completed transfer can promote the payload without changing [downloaded]
     * or [sizeBytes], so this is what tells an already-mounted bubble to look
     * again instead of waiting for the conversation to be reopened.
     */
    val payloadStamp: String? = null,
    /** Same identity for the paired Live Photo motion payload. */
    val livePhotoMotionPayloadStamp: String? = null,
) {
    val isVideo: Boolean
        get() = !isImage && AttachmentMedia.isVideo(mime, uti, name)

    /** Audio payloads render as an inline voice-memo player instead of a file row. */
    val isAudio: Boolean
        get() = !isImage && !isVideo && AttachmentMedia.isAudio(mime, uti, name)

    val isPdf: Boolean
        get() = !isImage && !isVideo && !isAudio && AttachmentMedia.isPdf(mime, uti, name)

    val playbackMime: String
        get() = AttachmentMedia.suggestedMime(mime, uti, name)
}

/** One sticker image transformed over its target bubble. */
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
    val payload: String? = null,
)

/**
 * Apple LinkPresentation metadata projected into a platform-friendly card.
 *
 * Equality is structural over the byte payloads: the preview is re-parsed
 * from the message row on every transcript emission, so reference equality
 * on the arrays would defeat the "identical content is an identical frame"
 * deduplication and recompose every link bubble on unrelated writes.
 */
data class RichLinkPreview(
    val url: String,
    val displayHost: String,
    val title: String?,
    val summary: String?,
    val imageBytes: ByteArray?,
    val imageMime: String?,
    val iconBytes: ByteArray?,
    val iconMime: String?,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is RichLinkPreview) return false
        return url == other.url &&
            displayHost == other.displayHost &&
            title == other.title &&
            summary == other.summary &&
            imageMime == other.imageMime &&
            iconMime == other.iconMime &&
            imageBytes.contentEquals(other.imageBytes) &&
            iconBytes.contentEquals(other.iconBytes)
    }

    override fun hashCode(): Int {
        var result = url.hashCode()
        result = 31 * result + displayHost.hashCode()
        result = 31 * result + (title?.hashCode() ?: 0)
        result = 31 * result + (summary?.hashCode() ?: 0)
        result = 31 * result + (imageMime?.hashCode() ?: 0)
        result = 31 * result + (iconMime?.hashCode() ?: 0)
        result = 31 * result + imageBytes.contentHashCode()
        result = 31 * result + iconBytes.contentHashCode()
        return result
    }
}

/**
 * One active tapback with the handle that sent it, already resolved to the
 * emoji the transcript draws. Apple allows one active tapback per sender and
 * message part, so [senderAddress] identifies the reactor and `null` means it
 * came from me.
 */
data class MessageReactionUi(
    val emoji: String,
    val senderAddress: String?,
    val isFromMe: Boolean,
    val targetPart: Long = 0L,
    /** 0-5 for standard Tapbacks, 6 for the custom-emoji protocol type. */
    val reactionIndex: Int = -1,
)

data class MessageItem(
    val id: Long,
    val text: String,
    val isFromMe: Boolean,
    val date: Long,
    val dateDelivered: Long? = null,
    val dateRead: Long? = null,
    val status: MessageStatus,
    val isGroupEvent: Boolean,
    val reactionEmoji: String?,
    /**
     * Every active tapback, one per sender and part, oldest first. The transcript
     * summarizes these on the bubble; the reaction picker names who reacted.
     */
    val reactions: List<MessageReactionUi> = emptyList(),
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
    /** Part index on the root message used by this thread. */
    val replyToPart: Long? = null,
    /** Full Apple run locator (`part:start:length`) used by this reply. */
    val replyToPartLocator: String? = null,
    /** Apple run locators for parts that can be selected on this message. */
    val replyPartLocators: Map<Long, String> = emptyMap(),
    /** Parent-part summary resolved independently of the progressive page. */
    val replyPreviewText: String? = null,
    /** Rich web preview supplied by Apple, or a URL-only fallback for plain links. */
    val richLink: RichLinkPreview? = null,
    /** Structured iMessage app-balloon content. */
    val interactivePayload: InteractivePayload? = null,
    /** Positional stickers layered over this message. */
    val stickers: List<StickerPlacement> = emptyList(),
    /** Optional iMessage subject line. */
    val subject: String? = null,
    /** Protocol chat that carried this message inside a grouped contact thread. */
    val chatId: Long? = null,
    /** True when this row came from the local SIM/MMS service. */
    val isSms: Boolean = false,
    val isBookmarked: Boolean = false,
    val hasBeenForwarded: Boolean = false,
    val dateDeleted: Long? = null,
    val errorCode: Long? = null,
    val errorMessage: String? = null,
    val partCount: Int = 1,
)

enum class MessageStatus { SENDING, SENT, DELIVERED, READ, FAILED }

interface ChatListRepository {
    fun chats(): Flow<List<ChatListItem>>
    fun markRead(id: Long)
    fun setPinned(id: Long, pinned: Boolean)
    fun setMuted(id: Long, muted: Boolean)
    fun setMutedUntil(id: Long, untilEpochMs: Long) = setMuted(id, true)
    fun setArchived(id: Long, archived: Boolean)

    /** Per-chat send-from override; null returns the chat to the default address. */
    fun setSenderOverride(id: Long, handle: String?) = Unit
    fun setLockChatName(id: Long, locked: Boolean) = Unit
    fun setLockChatIcon(id: Long, locked: Boolean) = Unit
    fun setAutoSendReadReceipts(id: Long, enabled: Boolean) = Unit
    fun setAutoSendTypingIndicators(id: Long, enabled: Boolean) = Unit
    fun setCustomAvatar(id: Long, file: File?) = Unit
    fun setBlocked(id: Long, blocked: Boolean, archive: Boolean = false) = Unit
    fun clearTranscript(id: Long) = Unit
    fun recentlyDeleted(): List<ChatListItem> = emptyList()

    /** Count for the settings badge; avoids projecting every deleted chat. */
    fun recentlyDeletedCount(): Int = recentlyDeleted().size
    fun restoreDeleted(id: Long) = Unit
    fun permanentlyDelete(id: Long) = Unit
    fun delete(id: Long)
}

interface MessageListRepository {
    fun messages(chatId: Long, limit: Int, before: Long?): Flow<List<MessageItem>>
    fun loadMore(chatId: Long, before: Long?, count: Int): List<MessageItem>

    /** Root plus replies for one part-aware thread, oldest first. */
    fun thread(chatId: Long, rootGuid: String, part: Long): List<MessageItem> = emptyList()

    /**
     * Instant snapshot of a warmed transcript, oldest first. Empty when this
     * conversation has not been prefetched or opened yet.
     */
    fun cached(chatId: Long): List<MessageItem> = emptyList()

    /**
     * Warm the newest [limit] messages for list-adjacent chats and drop
     * snapshots that left the window. Does not keep a live ObjectBox
     * subscription — incoming messages refresh only the still-desired set.
     */
    suspend fun prefetch(
        chatIds: Collection<Long>,
        limit: Int = TRANSCRIPT_PREFETCH_LIMIT,
    ) = Unit

    /** Expand one conversation immediately (row tap or notification tap). */
    suspend fun prime(chatId: Long, limit: Int = TRANSCRIPT_OPEN_LIMIT) =
        prefetch(listOf(chatId), limit)

    /** Releases per-conversation paging state when its ViewModel is cleared. */
    fun release(chatId: Long) = Unit

    fun bookmarked(chatId: Long): List<MessageItem> = emptyList()
    fun recentlyDeleted(chatId: Long? = null): List<MessageItem> = emptyList()
    fun setBookmarked(messageIds: Collection<Long>, bookmarked: Boolean) = Unit
    fun markForwarded(messageIds: Collection<Long>) = Unit
    fun deleteLocal(messageIds: Collection<Long>) = Unit
    fun cancelOutgoing(messageId: Long): Boolean = false
    fun restoreDeleted(messageIds: Collection<Long>) = Unit
}

data class OutgoingTextSend(
    val messageId: Long,
)

data class OutgoingAttachmentSend(
    val messageId: Long,
)

data class OutgoingMention(
    val start: Int,
    val end: Int,
    val handle: String,
    val displayText: String,
)

interface Sender {
    /** Returns after the outgoing row is staged locally; transport continues asynchronously. */
    suspend fun send(chatId: Long, text: String): OutgoingTextSend

    suspend fun send(
        chatId: Long,
        text: String,
        subject: String?,
        mentions: List<OutgoingMention>,
    ): OutgoingTextSend = send(chatId, text)

    /** Sends a text reply rooted at [replyGuid]. */
    suspend fun sendReply(
        chatId: Long,
        text: String,
        replyGuid: String,
        replyPartLocator: String,
    ): OutgoingTextSend = send(chatId, text)

    suspend fun sendReply(
        chatId: Long,
        text: String,
        replyGuid: String,
        replyPartLocator: String,
        subject: String?,
        mentions: List<OutgoingMention>,
    ): OutgoingTextSend = sendReply(chatId, text, replyGuid, replyPartLocator)

    /**
     * Sends a text with an iMessage expressive-send effect id (e.g.
     * "com.apple.messages.effect.CKConfettiEffect" or
     * "com.apple.MobileSMS.expressivesend.invisibleink"). Null effect falls
     * back to a plain send; senders without effect support inherit this
     * default so existing implementations keep compiling.
     */
    suspend fun sendWithEffect(chatId: Long, text: String, effectId: String?): OutgoingTextSend =
        send(chatId, text)

    suspend fun sendWithEffect(
        chatId: Long,
        text: String,
        effectId: String?,
        subject: String?,
        mentions: List<OutgoingMention>,
    ): OutgoingTextSend = sendWithEffect(chatId, text, effectId)
}

data class StickerTransform(
    val messageWidth: Double,
    val normalizedX: Double,
    val normalizedY: Double,
    val rotation: Double,
    val scale: Double,
    val effectType: Long = 0L,
)

/** Uploads an image as a positional sticker reaction. */
data class OutgoingStickerSend(
    val attachmentGuid: String,
)

fun interface StickerSender {
    suspend fun send(
        chatId: Long,
        targetGuid: String,
        targetPart: Long,
        targetText: String,
        sticker: OutgoingAttachment,
        transform: StickerTransform,
    ): OutgoingStickerSend
}

/** Marks a conversation read locally and mirrors the receipt through iMessage. */
fun interface ReadReceiptSender {
    suspend fun markRead(chatId: Long, messageGuid: String?)
}

/** Fully prepared outgoing call; the UI only has to launch the call activity. */
data class FaceTimeLaunch(
    val link: String,
    val displayName: String,
    val description: String,
    val callUuid: String,
    /** Normalized remote addresses, for the telecom connection address. */
    val participants: List<String> = emptyList(),
)

/** Validates participants and creates an on-device FaceTime session. */
fun interface FaceTimeCaller {
    suspend fun start(chatId: Long): FaceTimeLaunch
}

/** Message mutations supported by the on-device iMessage engine. */
interface MessageActions {
    suspend fun react(
        chatId: Long,
        messageGuid: String,
        messageText: String,
        messagePart: Long,
        reactionIndex: Int,
        emoji: String? = null,
        enable: Boolean = true,
    )

    suspend fun edit(chatId: Long, messageGuid: String, newText: String)

    suspend fun unsend(chatId: Long, messageGuid: String)

    suspend fun setBookmarked(messageIds: Collection<Long>, bookmarked: Boolean) = Unit
    suspend fun deleteLocal(messageIds: Collection<Long>) = Unit
    suspend fun cancelOutgoing(messageId: Long): Boolean = false
    suspend fun markForwarded(messageIds: Collection<Long>) = Unit
    suspend fun blockSender(chatId: Long, archive: Boolean = false) = Unit
}

/**
 * On-device (SIM) SMS send for chats flagged `isRpSms` — the modem twin of
 * [Sender]. Implementations stage the outgoing row optimistically (SENDING)
 * and resolve the status asynchronously (sent/delivery receipts); see
 * `app.openbubbles.nativeapp.sms.SmsManagerSender`. Kept separate from
 * [Sender] so the iMessage path stays untouched; the conversation ViewModel
 * selects it from already-loaded chat metadata without another database read.
 */
interface SmsSender {
    /** Returns after the outgoing row is staged locally; modem dispatch continues asynchronously. */
    suspend fun send(chatId: Long, text: String): OutgoingTextSend

    suspend fun send(chatId: Long, text: String, subject: String?): OutgoingTextSend = send(chatId, text)
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
 * Sends one message whose parts are the staged [attachments] plus an
 * optional caption, using the same optimistic staging pattern as [Sender]:
 * a staged message row appears immediately (SENDING), upload progress
 * surfaces through the message flow, and errors leave a FAILED bubble.
 */
interface AttachmentSender {
    /** Returns after the message and all attachment rows are staged locally. */
    suspend fun send(
        chatId: Long,
        attachments: List<OutgoingAttachment>,
        caption: String?,
    ): OutgoingAttachmentSend

    suspend fun send(
        chatId: Long,
        attachments: List<OutgoingAttachment>,
        caption: String?,
        subject: String?,
    ): OutgoingAttachmentSend = send(chatId, attachments, caption)
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
 * stored file (null while the transfer has not been downloaded), and the
 * viewer's explicit download seam over the deduplicated transfer port.
 */
interface AttachmentProvider {
    fun byGuid(guid: String): AttachmentMeta?
    fun localFile(guid: String): File?
    fun observe(guid: String): Flow<AttachmentMeta?> = flowOf(byGuid(guid))

    /** True only when a remote transfer can actually be attempted for [guid]. */
    fun canDownload(guid: String): Boolean = false

    /**
     * Requests (or joins the in-flight, guid-deduplicated) download and emits
     * transfer state until terminal. Cancelling collection does not abort the
     * underlying transfer; a re-subscriber observes the same terminal state.
     */
    fun download(guid: String): Flow<TransferState> =
        flowOf(TransferState.Failed("Download is not available"))
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

    private val _avatarGeneration = MutableStateFlow(0)

    /** Bumped after contact or group-photo imports so avatar UIs refetch. */
    val avatarGeneration: StateFlow<Int> = _avatarGeneration

    fun notifyAvatarsChanged() {
        _avatarGeneration.value = _avatarGeneration.value + 1
    }
}

/** Shared attachment or link surfaced on the contact sheet. */
data class SharedContentPreview(
    val id: String,
    val label: String,
    val attachmentGuid: String? = null,
    val url: String? = null,
    val isImage: Boolean = false,
)

/** Read-only chat details for the group-info screen. */
interface ChatInfoRepository {
    /** Participant handle addresses of the conversation (excluding me). */
    fun participantAddresses(chatId: Long): List<String>

    /** Recent attachments and links from this conversation, newest first. */
    fun sharedContent(chatId: Long, limit: Int = 8): List<SharedContentPreview>
}

/** Mutating group operations backed by the on-device iMessage engine. */
interface ChatInfoActions {
    suspend fun rename(chatId: Long, name: String)
    suspend fun addParticipant(chatId: Long, address: String)
    suspend fun removeParticipant(chatId: Long, address: String)
    suspend fun setGroupIcon(chatId: Long, file: File)
    suspend fun removeGroupIcon(chatId: Long)
    suspend fun leave(chatId: Long)
    suspend fun reportJunk(chatId: Long)
}

/** Local per-chat background controls. Apple-synced backgrounds arrive through push intake. */
interface ChatBackgroundActions {
    suspend fun setLocalBackground(chatId: Long, file: File)
    suspend fun clearLocalBackground(chatId: Long)
}

/**
 * Cross-store search for the dedicated search page: message bodies and
 * link-carrying messages from the transcript store, plus the synced contact
 * list for people matches.
 */
interface SearchRepository {
    /** Newest-first messages whose body contains [query], across all chats. */
    suspend fun searchMessages(query: String, limit: Int = 25): List<MessageItem> = emptyList()

    /** Newest-first messages carrying a URL that match [query]. */
    suspend fun searchLinks(query: String, limit: Int = 25): List<MessageItem> = emptyList()

    /** Synced/native contacts for the people section. */
    suspend fun contacts(): List<app.openbubbles.core.contacts.RawContact> = emptyList()
}

package app.openbubbles.nativeapp.ui.chat

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.openbubbles.nativeapp.data.AttachmentMeta
import app.openbubbles.nativeapp.data.MessageItem
import app.openbubbles.nativeapp.data.MessageStatus
import java.io.File

/** Mini original-message preview shown above a reply bubble. */
data class ReplyQuote(
    val text: String,
    val fromMe: Boolean,
    val senderName: String? = null,
)

/**
 * Resolves the Apple-style quote for a reply. Always returns a quote when the
 * message has a thread originator, even if the original is not on the current
 * page — the tap target has to exist so opening the thread cannot no-op.
 */
fun resolveReplyQuote(
    message: MessageItem,
    messagesByGuid: Map<String, MessageItem>,
    senderNames: Map<String, String> = emptyMap(),
): ReplyQuote? {
    val guid = message.replyToGuid ?: return null
    val target = messagesByGuid[guid]
    val text = message.replyPreviewText?.ifBlank { null }
        ?: target?.text?.ifBlank { null }
        ?: target?.attachmentMetas?.firstOrNull()?.name?.ifBlank { null }
        ?: target?.attachmentMeta?.name?.ifBlank { null }
        ?: when {
            !target?.attachmentMetas.isNullOrEmpty() || target?.attachmentMeta != null -> "Attachment"
            else -> "Message"
        }
    val senderName = target
        ?.takeIf { !it.isFromMe }
        ?.senderAddress
        ?.let { senderNames[it] }
    return ReplyQuote(
        text = text,
        fromMe = target?.isFromMe == true,
        senderName = senderName,
    )
}

/**
 * Index of the transcript entry rendering the reply's original message, or
 * null when the original is outside the loaded window — callers fall back to
 * the reply thread pane so the tap never no-ops.
 */
internal fun resolveReplyScrollTarget(
    entries: List<ConversationEntry>,
    replyToGuid: String?,
): Int? {
    val guid = replyToGuid ?: return null
    val index = entries.indexOfFirst { it is ConversationEntry.Message && it.message.guid == guid }
    return index.takeIf { it >= 0 }
}

internal fun replyCountsByRoot(messages: List<MessageItem>): Map<String, Int> =
    messages.asSequence()
        .mapNotNull { it.replyToGuid }
        .groupingBy { it }
        .eachCount()

/**
 * Replies directly following their root (or another reply in the same thread)
 * already read as one connected block and do not repeat the root as a quote.
 * A quote only reappears when unrelated chronology — or a timestamp
 * separator — sits between the reply and its thread context, matching Apple
 * Messages' lightweight inline threads.
 */
internal fun repliesWithInlineContext(entries: List<ConversationEntry>): Set<String> = buildSet {
    entries.forEachIndexed { index, entry ->
        val message = (entry as? ConversationEntry.Message)?.message ?: return@forEachIndexed
        val root = message.replyThreadKey() ?: return@forEachIndexed
        val adjacent = (entries.getOrNull(index + 1) as? ConversationEntry.Message)?.message
            ?: return@forEachIndexed
        if (adjacent.guid == root.guid || adjacent.replyThreadKey() == root) {
            add(message.guid)
        }
    }
}

internal data class ReplyThreadKey(val guid: String, val part: Long)

internal fun MessageItem.replyThreadKey(): ReplyThreadKey? {
    val guid = replyToGuid ?: return null
    return ReplyThreadKey(guid, replyToPart ?: 0L)
}

/**
 * One visually consecutive run of replies to the same root part.
 *
 * [replyMessageIds] is oldest-first (visual top to bottom in the reversed
 * transcript). [attachedToRoot] is true when the original sits immediately
 * above the first reply, so the rail can originate on that bubble instead
 * of repeating it as a quote.
 */
internal data class InlineReplyCluster(
    val rootGuid: String,
    val part: Long,
    val rootMessageId: Long?,
    val replyMessageIds: List<Long>,
    val attachedToRoot: Boolean,
) {
    /** A rail is only needed when the run is attached to its root, or a quoted reply has siblings below it. */
    fun drawsRail(): Boolean = attachedToRoot || replyMessageIds.size > 1

    fun trackedMessageIds(): List<Long> = buildList {
        if (attachedToRoot) {
            rootMessageId?.let { add(it) }
        }
        addAll(replyMessageIds)
    }
}

/**
 * Groups replies that follow each other (or their root) without an
 * unrelated bubble or timestamp in between. Separators and group-event
 * rows break a run so the rail never crosses a caption.
 */
internal fun inlineReplyClusters(entries: List<ConversationEntry>): List<InlineReplyCluster> {
    val clusters = mutableListOf<InlineReplyCluster>()
    var runKey: ReplyThreadKey? = null
    var runReplies = mutableListOf<MessageItem>()
    var attachedRoot: MessageItem? = null
    var pendingRoot: MessageItem? = null

    fun flush() {
        val key = runKey
        if (key != null && runReplies.isNotEmpty()) {
            clusters += InlineReplyCluster(
                rootGuid = key.guid,
                part = key.part,
                rootMessageId = attachedRoot?.id,
                replyMessageIds = runReplies.map { it.id },
                attachedToRoot = attachedRoot != null,
            )
        }
        runKey = null
        runReplies = mutableListOf()
        attachedRoot = null
    }

    // Newest-first entries render index 0 at the bottom; walk oldest-first
    // so a run is collected top to bottom.
    for (entry in entries.asReversed()) {
        when (entry) {
            is ConversationEntry.TimeSeparator -> {
                flush()
                pendingRoot = null
            }
            is ConversationEntry.Message -> {
                val message = entry.message
                if (!message.rendersAsBubble()) {
                    flush()
                    pendingRoot = null
                    continue
                }
                val key = message.replyThreadKey()
                if (key != null) {
                    if (runKey == key) {
                        runReplies += message
                    } else {
                        flush()
                        runKey = key
                        runReplies += message
                        attachedRoot = pendingRoot?.takeIf { it.guid == key.guid }
                    }
                } else {
                    flush()
                }
                pendingRoot = message
            }
        }
    }
    flush()
    return clusters
}

internal fun belongsToReplyThread(message: MessageItem, rootGuid: String, part: Long): Boolean {
    if (message.guid == rootGuid) return true
    if (message.replyToGuid != rootGuid) return false
    return (message.replyToPart ?: 0L) == part
}

internal fun mergeReplyThread(
    thread: ReplyThreadState,
    live: List<MessageItem>,
): ReplyThreadState {
    val fromLive = live.filter { belongsToReplyThread(it, thread.rootGuid, thread.part) }
    if (fromLive.isEmpty()) return thread
    val merged = LinkedHashMap<String, MessageItem>()
    thread.messages.forEach { merged[it.guid] = it }
    fromLive.forEach { merged[it.guid] = it }
    return thread.copy(
        messages = merged.values.sortedWith(compareBy({ it.date }, { it.id })),
    )
}

internal fun ensureThreadContains(
    messages: List<MessageItem>,
    source: MessageItem,
): List<MessageItem> {
    if (messages.any { it.guid == source.guid }) {
        return messages.sortedWith(compareBy({ it.date }, { it.id }))
    }
    return (messages + source).sortedWith(compareBy({ it.date }, { it.id }))
}

/**
 * Focused reply thread: the original plus every reply to that part, oldest
 * first (visually at the top). Quotes are hidden — the original is already
 * in the list, the same way Apple's thread view works.
 */
@Composable
internal fun ReplyThreadPane(
    thread: ReplyThreadState,
    smsChat: Boolean,
    senderNames: Map<String, String>,
    attachmentFile: (String) -> File?,
    onOpenAttachment: (String) -> Unit,
    onDownloadAttachment: (AttachmentMeta) -> Unit,
    onReply: (MessageItem, Long) -> Unit,
    onLongPressPart: ((MessageItem, Long) -> Unit)?,
    onDownloadSticker: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val listState = rememberLazyListState()
    val lastFromMeId = remember(thread.messages) {
        thread.messages.lastOrNull { it.isFromMe && !it.isGroupEvent }?.id
    }
    Box(modifier = modifier.fillMaxSize()) {
        if (thread.messages.isEmpty() && thread.loading) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                androidx.compose.material3.LoadingIndicator()
            }
            return@Box
        }
        LazyColumn(
            state = listState,
            reverseLayout = true,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            if (thread.messages.isEmpty()) {
                item(key = "thread-empty") {
                    Text(
                        text = "This reply’s original message isn’t on this device yet.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .widthIn(max = 840.dp)
                            .padding(horizontal = 32.dp, vertical = 24.dp),
                    )
                }
            } else {
                items(
                    items = thread.messages.asReversed(),
                    key = { "thread-${it.id}-${it.guid}" },
                ) { message ->
                    val showSender = !message.isFromMe && message.senderAddress != null
                    MessageBubble(
                        message = message,
                        showStatus = message.id == lastFromMeId ||
                            message.status == MessageStatus.FAILED,
                        showSenderName = showSender,
                        smsChat = smsChat,
                        attachmentFile = attachmentFile,
                        onOpenAttachment = onOpenAttachment,
                        onDownloadAttachment = onDownloadAttachment,
                        senderDisplayName = message.senderAddress?.let { senderNames[it] },
                        replyQuote = null,
                        onDownloadSticker = onDownloadSticker,
                        onLongPressPart = if (message.status == MessageStatus.SENDING) {
                            null
                        } else {
                            onLongPressPart?.let { callback -> { part -> callback(message, part) } }
                        },
                        onSwipeReply = if (canSwipeReply(message)) {
                            { part -> onReply(message, part) }
                        } else {
                            null
                        },
                        modifier = Modifier.widthIn(max = 840.dp),
                    )
                }
            }
        }
        if (thread.loading && thread.messages.isNotEmpty()) {
            LinearProgressIndicator(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.TopCenter),
            )
        }
    }
}

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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import app.openbubbles.nativeapp.data.LiveMessageArrivals
import app.openbubbles.nativeapp.data.AttachmentMeta
import app.openbubbles.nativeapp.data.MessageItem
import app.openbubbles.nativeapp.data.MessageStatus
import app.openbubbles.nativeapp.ui.theme.LocalReduceMotion
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
 * A quote only reappears when unrelated chronology separates the reply from
 * its thread context, matching Apple Messages' lightweight inline threads.
 */
internal fun repliesWithInlineContext(entries: List<ConversationEntry>): Set<String> = buildSet {
    val messageEntries = entries.filterIsInstance<ConversationEntry.Message>()
    messageEntries.forEachIndexed { index, entry ->
        val rootGuid = entry.message.replyToGuid ?: return@forEachIndexed
        val adjacent = messageEntries.getOrNull(index + 1)?.message
        if (adjacent?.guid == rootGuid || adjacent?.replyToGuid == rootGuid) {
            add(entry.message.guid)
        }
    }
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
    outgoingSendEvent: OutgoingSendEvent?,
    onOutgoingSendEventConsumed: (Long) -> Unit,
    smsChat: Boolean,
    historySyncActive: Boolean,
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
    val scope = rememberCoroutineScope()
    val reduceMotion = LocalReduceMotion.current
    val lastFromMeId = remember(thread.messages) {
        thread.messages.lastOrNull { it.isFromMe && !it.isGroupEvent }?.id
    }

    // Thread-scoped follow policy: the same reducer as the main transcript, but
    // with its own counter over the selected root/part only. No typing row here,
    // so the newest reply always holds the reversed list's bottom slot.
    val newestIndex = if (thread.messages.isEmpty()) -1 else 0
    val thresholdPx = with(LocalDensity.current) { FollowBottomThresholdDp.dp.roundToPx() }
    val anchor by remember(listState) {
        derivedStateOf {
            TranscriptAnchor(
                firstVisibleIndex = listState.firstVisibleItemIndex,
                firstVisibleOffsetPx = listState.firstVisibleItemScrollOffset,
                isScrollInProgress = listState.isScrollInProgress,
            )
        }
    }
    val atBottomNow by remember(listState, newestIndex, thresholdPx) {
        derivedStateOf {
            isFollowingBottom(anchor, newestIndex, thresholdPx)
        }
    }
    // As in the main transcript: the follow decision is the position at the last
    // settled scroll, because inserting the newest reply moves every laid-out
    // index by one before the arrival effect can read it.
    var followingBottom by remember(thread.rootGuid, thread.part) { mutableStateOf(true) }
    LaunchedEffect(listState) {
        snapshotFlow { listState.isScrollInProgress }.collect { scrolling ->
            if (!scrolling) followingBottom = atBottomNow
        }
    }
    var liveArrivalMarkers by rememberSaveable(
        thread.rootGuid,
        thread.part,
        stateSaver = LiveArrivalMarkerStateSaver,
    ) {
        mutableStateOf(LiveArrivalMarkerState())
    }
    var liveArrivalSequence by rememberSaveable(thread.rootGuid, thread.part) {
        mutableLongStateOf(LiveMessageArrivals.latestSequence)
    }
    LaunchedEffect(thread.rootGuid, thread.part) {
        LiveMessageArrivals.events.collect { arrival ->
            if (arrival.sequence <= liveArrivalSequence) return@collect
            liveArrivalSequence = arrival.sequence
            if (arrival.threadRootGuid == thread.rootGuid && arrival.threadPart == thread.part) {
                liveArrivalMarkers = liveArrivalMarkers.added(arrival.messageGuid)
            }
        }
    }
    val liveArrivalGuids = liveArrivalMarkers.reducerGuids
    val liveArrivalFallback = liveArrivalMarkers.chronologicalFallback

    LaunchedEffect(outgoingSendEvent, thread.messages) {
        val event = outgoingSendEvent ?: return@LaunchedEffect
        val reversed = thread.messages.asReversed()
        val targetIndex = reversed.indexOfFirst { it.id == event.messageId }
        if (targetIndex < 0) return@LaunchedEffect
        val target = reversed[targetIndex]
        val targetKey = "thread-${target.id}-${target.guid}"
        if (reduceMotion) {
            listState.scrollToItem(targetIndex)
        } else {
            listState.animateScrollToItem(targetIndex)
        }
        withFrameNanos { }
        if (listState.layoutInfo.visibleItemsInfo.any { it.key == targetKey }) {
            onOutgoingSendEventConsumed(event.messageId)
        }
    }
    // Selecting another root/part is a different viewport; closing the thread
    // disposes this state entirely, so no stale announcement can replay.
    var arrivals by rememberSaveable(
        thread.rootGuid,
        thread.part,
        stateSaver = ArrivalStateSaver,
    ) { mutableStateOf(ArrivalState()) }
    LaunchedEffect(
        thread.messages,
        thread.rootGuid,
        thread.part,
        historySyncActive,
        liveArrivalGuids,
        liveArrivalFallback,
        anchor.isScrollInProgress,
        followingBottom,
    ) {
        if (anchor.isScrollInProgress) return@LaunchedEffect
        val outcome = reduceArrivals(
            state = arrivals,
            messages = thread.messages,
            followingBottom = shouldAutoScrollToNewest(followingBottom, anchor),
            historySyncActive = historySyncActive,
            liveArrivalGuids = liveArrivalGuids,
            chronologicalFallback = liveArrivalFallback,
        )
        arrivals = outcome.state
        if (outcome.pinToNewest && newestIndex >= 0) {
            if (reduceMotion) listState.scrollToItem(newestIndex) else listState.animateScrollToItem(newestIndex)
            arrivals = arrivals.cleared()
        }
        // Keep the marker effect key stable until a suspending pin completes.
        liveArrivalMarkers = liveArrivalMarkers.consumed(
            outcome.matchedLiveGuids,
            fallbackGuids = outcome.reconciledFallbackGuids,
        )
    }
    LaunchedEffect(atBottomNow, anchor.isScrollInProgress, newestIndex) {
        if (arrivals.pendingCount == 0) return@LaunchedEffect
        if (!atBottomNow || anchor.isScrollInProgress) return@LaunchedEffect
        val newestKey = thread.messages.lastOrNull()?.let { "thread-${it.id}-${it.guid}" }
        if (newestKey != null && listState.layoutInfo.visibleItemsInfo.any { it.key == newestKey }) {
            arrivals = arrivals.cleared()
        }
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
            modifier = Modifier
                .fillMaxSize()
                .padding(bottom = if (arrivals.pendingCount > 0) 68.dp else 0.dp),
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
        NewMessagesJumpPill(
            visible = arrivals.pendingCount > 0,
            count = arrivals.pendingCount,
            thread = true,
            onClick = {
                scope.launch {
                    val newest = thread.messages.lastOrNull() ?: return@launch
                    val newestKey = "thread-${newest.id}-${newest.guid}"
                    if (reduceMotion) listState.scrollToItem(0) else listState.animateScrollToItem(0)
                    if (listState.layoutInfo.visibleItemsInfo.any { it.key == newestKey }) {
                        arrivals = arrivals.cleared()
                    }
                }
            },
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 12.dp),
        )
    }
}

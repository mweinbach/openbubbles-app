package app.openbubbles.desktop.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.openbubbles.core.attachment.AttachmentMedia
import app.openbubbles.core.model.MessageItem
import app.openbubbles.core.model.MessageKind
import app.openbubbles.core.model.MessageStatus
import app.openbubbles.desktop.DesktopGraph
import app.openbubbles.db.Attachment
import app.openbubbles.db.Chat
import app.openbubbles.db.Message
import io.objectbox.BoxStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import javax.imageio.ImageIO
import kotlin.math.roundToInt

/** Attachment display metadata extracted from the raw entity. */
internal data class AttachmentMeta(
    val guid: String,
    val name: String,
    val sizeBytes: Long,
    val isImage: Boolean,
    val downloaded: Boolean,
    val localFile: File?,
)

/** One transcript row: the core DTO plus entity-derived display details. */
private data class Row(
    val item: MessageItem,
    val attachment: AttachmentMeta?,
    val edited: Boolean,
    val unsent: Boolean,
)

/** Display width/height cap for image bubbles. */
private val BUBBLE_MAX_WIDTH = 260.dp

/**
 * A conversation: reactive transcript (newest-first core flow widened by a
 * growable window as the user scrolls up), bubbles L/R with status ticks,
 * attachment placeholders (payload download via the Rust push state), and a
 * send box wired to [DesktopGraph.send].
 */
@Composable
fun ChatScreen(
    chatId: Long,
    onBack: () -> Unit,
) {
    val graph = DesktopGraph
    val chat by produceState<Chat?>(initialValue = null, chatId) {
        value = withContext(Dispatchers.IO) {
            graph.store?.boxFor(Chat::class.java)?.get(chatId)
        }
    }

    // Growable newest-first window so scroll-to-top widens the reactive page.
    val windowSize = remember(chatId) { MutableStateFlow(60) }
    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val rowsFlow = remember(chatId) {
        windowSize.flatMapLatest { size ->
            val repo = graph.messageRepo
                ?: return@flatMapLatest flowOf(emptyList())
            repo.observeMessages(chatId, size).map { page -> enrich(page, graph.store) }
        }
    }
    val rows by rowsFlow.collectAsState(initial = emptyList())
    val scope = rememberCoroutineScope()
    val connected = DesktopGraph.PushStateHolder.stateFlow.collectAsState().value != null
    val carrierChat = chat?.isRpSms == true

    // Mark read on open (and whenever a new row lands while open).
    LaunchedEffect(chatId, rows.size) {
        if (rows.isNotEmpty()) graph.chatRepo?.markRead(chatId)
    }

    Column(modifier = Modifier.fillMaxSize()) {
        ChatHeader(
            title = chat?.displayName ?: chat?.chatIdentifier ?: "Chat",
            subtitle = chat?.handles?.joinToString(", ") { it.formattedAddress ?: it.address }.orEmpty(),
            connected = connected,
            onBack = onBack,
        )
        Transcript(
            chatId = chatId,
            chatGuid = chat?.guid,
            rows = rows,
            canLoadMore = rows.size >= windowSize.value,
            onLoadMore = { windowSize.value += 60 },
        )
        InputBox(
            enabled = connected && chat != null && !carrierChat,
            placeholder = if (carrierChat) "SMS is available on Android only" else "iMessage",
            onSend = { text ->
                scope.launch { runCatching { DesktopGraph.send(chatId, text) } }
            },
        )
    }
}

// ------------------------------------------------------------------
// Header
// ------------------------------------------------------------------

@Composable
private fun ChatHeader(title: String, subtitle: String, connected: Boolean, onBack: () -> Unit) {
    Surface(tonalElevation = 2.dp) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
                .padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = if (connected) subtitle.ifBlank { "iMessage" } else "Reconnecting…",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.width(48.dp))
        }
    }
}

// ------------------------------------------------------------------
// Transcript
// ------------------------------------------------------------------

@Composable
private fun Transcript(
    chatId: Long,
    chatGuid: String?,
    rows: List<Row>,
    canLoadMore: Boolean,
    onLoadMore: () -> Unit,
) {
    val listState = rememberLazyListState()

    // reverseLayout: index 0 (newest) sits at the bottom; the visual top is
    // the tail of the list. Load more when the oldest rows become visible.
    LaunchedEffect(listState, canLoadMore) {
        snapshotFlow {
            val info = listState.layoutInfo
            info.totalItemsCount to (info.visibleItemsInfo.lastOrNull()?.index ?: -1)
        }.collect { (total, lastVisible) ->
            if (canLoadMore && total > 0 && lastVisible >= total - 4) {
                onLoadMore()
            }
        }
    }

    // Keep pinned to the newest bubble when the user is near the bottom.
    val newestGuid = rows.firstOrNull()?.item?.guid
    LaunchedEffect(newestGuid) {
        if (newestGuid != null && listState.firstVisibleItemIndex <= 1) {
            runCatching { listState.scrollToItem(0) }
        }
    }

    // Typing indicators for this chat.
    val typingList = DesktopGraph.ingestor?.typing?.collectAsState()?.value.orEmpty()
    val isTyping = chatGuid != null && typingList.any { it.chatGuid == chatGuid }

    Box(modifier = Modifier.fillMaxSize()) {
        if (rows.isEmpty()) {
            Text(
                "No messages yet. Say hi.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.align(Alignment.Center),
            )
        } else {
            LazyColumn(
                state = listState,
                reverseLayout = true,
                modifier = Modifier.fillMaxSize(),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(
                    horizontal = 12.dp, vertical = 10.dp,
                ),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                items(rows, key = { it.item.id }) { row ->
                    TranscriptRow(row = row, showStatus = row.item.id == rows.firstOrNull()?.item?.id)
                }
                if (canLoadMore) {
                    item(key = "loader") {
                        Box(Modifier.fillMaxWidth().padding(8.dp)) {
                            CircularProgressIndicator(
                                modifier = Modifier
                                    .size(18.dp)
                                    .align(Alignment.Center),
                                strokeWidth = 2.dp,
                            )
                        }
                    }
                }
            }
        }
        if (isTyping) {
            Surface(
                shape = RoundedCornerShape(12.dp),
                tonalElevation = 2.dp,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 8.dp),
            ) {
                Text(
                    "typing…",
                    style = MaterialTheme.typography.labelMedium,
                    fontStyle = FontStyle.Italic,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                )
            }
        }
    }
}

@Composable
private fun TranscriptRow(row: Row, showStatus: Boolean) {
    when (row.item.kind) {
        MessageKind.GROUP_EVENT -> CenteredNote(row.item.groupEventText ?: row.item.text)
        MessageKind.REACTION -> CenteredNote(reactionLabel(row.item))
        MessageKind.TEXT -> Bubble(row, showStatus)
    }
}

@Composable
private fun CenteredNote(text: String) {
    Box(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.align(Alignment.Center),
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )
    }
}

private val TAPBACK_EMOJI = mapOf(
    "love" to "❤️", "like" to "👍", "dislike" to "👎", "laugh" to "😂",
    "emphasize" to "‼️", "question" to "❓",
)

private fun reactionLabel(item: MessageItem): String {
    val emoji = item.reactionEmoji
        ?: item.reactionType?.removePrefix("-")?.let { TAPBACK_EMOJI[it] }
        ?: "reacted"
    return "${if (item.isFromMe) "You" else "Someone"} reacted $emoji"
}

@Composable
private fun Bubble(row: Row, showStatus: Boolean) {
    val fromMe = row.item.isFromMe
    val failed = row.item.status == MessageStatus.FAILED
    val container = when {
        failed -> MaterialTheme.colorScheme.errorContainer
        fromMe -> MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.surfaceVariant
    }
    val content = when {
        failed -> MaterialTheme.colorScheme.onErrorContainer
        fromMe -> MaterialTheme.colorScheme.onPrimary
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (fromMe) Arrangement.End else Arrangement.Start,
    ) {
        Surface(
            color = container,
            shape = RoundedCornerShape(
                topStart = 16.dp, topEnd = 16.dp,
                bottomStart = if (fromMe) 16.dp else 4.dp,
                bottomEnd = if (fromMe) 4.dp else 16.dp,
            ),
            modifier = Modifier.widthIn(max = BUBBLE_MAX_WIDTH + if (row.attachment != null) 60.dp else 0.dp),
        ) {
            Column(modifier = Modifier.padding(10.dp, 6.dp, 10.dp, 4.dp)) {
                row.attachment?.let { meta -> AttachmentContent(meta) }
                val text = buildString {
                    append(row.item.text)
                    if (row.edited) {
                        if (isNotEmpty()) append(' ')
                        append("(edited)")
                    }
                }
                if (text.isNotBlank()) {
                    Text(
                        text = text,
                        color = content,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier.align(Alignment.End),
                ) {
                    Text(
                        text = bubbleTimeLabel(row.item.date),
                        color = content.copy(alpha = 0.7f),
                        style = MaterialTheme.typography.labelSmall,
                    )
                    if (fromMe && showStatus && !failed) {
                        StatusTick(row.item.status, tint = content.copy(alpha = 0.85f))
                    }
                    if (failed) {
                        Icon(
                            Icons.Filled.ErrorOutline,
                            contentDescription = "Failed to send",
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(13.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun StatusTick(status: MessageStatus, tint: Color) {
    when (status) {
        MessageStatus.SENDING -> Icon(
            Icons.Filled.Schedule, contentDescription = "Sending",
            tint = tint, modifier = Modifier.size(13.dp),
        )
        MessageStatus.SENT -> Icon(
            Icons.Filled.Check, contentDescription = "Sent",
            tint = tint, modifier = Modifier.size(13.dp),
        )
        MessageStatus.DELIVERED -> Icon(
            Icons.Filled.Check, contentDescription = "Delivered",
            tint = tint, modifier = Modifier.size(13.dp),
        )
        MessageStatus.READ -> Icon(
            Icons.Filled.Check, contentDescription = "Read",
            tint = tint, modifier = Modifier.size(15.dp),
        )
        MessageStatus.FAILED -> Icon(
            Icons.Filled.ErrorOutline, contentDescription = "Failed",
            tint = tint, modifier = Modifier.size(13.dp),
        )
    }
}

// ------------------------------------------------------------------
// Attachments
// ------------------------------------------------------------------

private fun attachmentToMeta(attachment: Attachment): AttachmentMeta = AttachmentMeta(
    guid = attachment.guid.orEmpty(),
    name = attachment.transferName ?: "Attachment",
    sizeBytes = attachment.totalBytes ?: 0L,
    isImage = AttachmentMedia.isImage(
        attachment.mimeType,
        attachment.uti,
        attachment.transferName,
    ),
    downloaded = attachment.isDownloaded,
    localFile = DesktopGraph.localAttachmentFile(attachment),
)

/** Renders an attachment bubble: decoded image when possible, else a chip. */
@Composable
private fun AttachmentContent(meta: AttachmentMeta) {
    if (meta.isImage && meta.downloaded && meta.localFile != null) {
        val bitmap by produceState<ImageBitmap?>(initialValue = null, meta.guid, meta.downloaded) {
            value = withContext(Dispatchers.IO) {
                runCatching {
                    ImageIO.read(meta.localFile)?.toComposeImageBitmap()
                }.getOrNull()
            }
        }
        val img = bitmap
        if (img != null) {
            androidx.compose.foundation.Image(
                bitmap = img,
                contentDescription = meta.name,
                contentScale = ContentScale.FillWidth,
                modifier = Modifier
                    .widthIn(max = BUBBLE_MAX_WIDTH)
                    .clip(RoundedCornerShape(12.dp)),
            )
            Spacer(Modifier.height(4.dp))
        } else {
            Chip(meta, awaitingDecode = true)
        }
    } else {
        Chip(meta, awaitingDecode = false)
    }
}

@Composable
private fun Chip(meta: AttachmentMeta, awaitingDecode: Boolean) {
    Surface(
        color = Color.Transparent,
        shape = RoundedCornerShape(12.dp),
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            MaterialTheme.colorScheme.outline.copy(alpha = 0.5f),
        ),
        modifier = Modifier
            .widthIn(max = BUBBLE_MAX_WIDTH)
            .then(
                if (!meta.downloaded && !awaitingDecode) {
                    Modifier.clickable { DesktopGraph.requestAttachmentDownload(meta.guid) }
                } else {
                    Modifier
                },
            ),
        ) {
        Row(
            modifier = Modifier.padding(10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(
                imageVector = if (meta.downloaded) Icons.Filled.Check else Icons.Filled.Download,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp),
            )
            Column {
                Text(
                    text = meta.name,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = when {
                        awaitingDecode -> "Image (preview unavailable)"
                        meta.downloaded -> humanSize(meta.sizeBytes)
                        else -> "${humanSize(meta.sizeBytes)} — click to download"
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

internal fun humanSize(bytes: Long): String {
    if (bytes <= 0) return "unknown size"
    val kb = bytes / 1024.0
    val mb = kb / 1024.0
    return when {
        mb >= 1 -> "${"%.1f".format(mb)} MB"
        kb >= 1 -> "${kb.roundToInt()} KB"
        else -> "$bytes B"
    }
}

// ------------------------------------------------------------------
// Input
// ------------------------------------------------------------------

@Composable
private fun InputBox(enabled: Boolean, placeholder: String, onSend: (String) -> Unit) {
    var text by remember { mutableStateOf("") }

    Surface(tonalElevation = 2.dp) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .imePadding()
                .padding(12.dp, 8.dp),
            verticalAlignment = Alignment.Bottom,
        ) {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                placeholder = { Text(placeholder) },
                modifier = Modifier.weight(1f),
                enabled = enabled,
                maxLines = 5,
                shape = RoundedCornerShape(20.dp),
            )
            Spacer(Modifier.width(8.dp))
            IconButton(
                onClick = {
                    val value = text.trim()
                    if (value.isNotEmpty()) {
                        text = ""
                        onSend(value)
                    }
                },
                enabled = enabled && text.isNotBlank(),
            ) {
                Icon(
                    Icons.AutoMirrored.Filled.Send,
                    contentDescription = "Send",
                    tint = if (enabled && text.isNotBlank()) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                    },
                )
            }
        }
    }
}

// ------------------------------------------------------------------
// Entity enrichment (attachment metadata / edited flags)
// ------------------------------------------------------------------

private val RETRACTED_PARTS = Regex(
    "\"(?:retractedParts|rp)\"\\s*:\\s*\\[\\s*\\d",
)

private fun editedFlags(entity: Message): Pair<Boolean, Boolean> {
    if (entity.dateEdited == null) return false to false
    val summary = entity.dbMessageSummaryInfo ?: return true to false
    val hasRetracted = RETRACTED_PARTS.containsMatchIn(summary)
    if (!hasRetracted) return true to false
    return if (entity.text.isNullOrBlank()) false to true else true to false
}

/** Batched entity read filling display fields the core DTO does not carry. */
private fun enrich(items: List<MessageItem>, store: BoxStore?): List<Row> {
    if (store == null || items.isEmpty()) {
        return items.map { Row(it, null, false, false) }
    }
    return runCatching {
        val entities = store.boxFor(Message::class.java).get(items.map { it.id })
        val byId = HashMap<Long, Message>(entities.size)
        entities.forEach { byId[it.id] = it }
        items.map { item ->
            val entity = byId[item.id]
            Row(
                item = item,
                attachment = entity?.dbAttachments?.firstOrNull()?.let(::attachmentToMeta),
                edited = entity?.let { editedFlags(it).first } ?: false,
                unsent = entity?.let { editedFlags(it).second } ?: false,
            )
        }
    }.getOrDefault(items.map { Row(it, null, false, false) })
}

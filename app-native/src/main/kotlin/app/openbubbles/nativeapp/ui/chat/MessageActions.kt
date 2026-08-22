package app.openbubbles.nativeapp.ui.chat

import android.content.ClipData
import android.content.ClipboardManager
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.ContactsContract
import android.provider.MediaStore
import androidx.annotation.RequiresApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddReaction
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.isTraversalGroup
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.core.net.toUri
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.lifecycleScope
import app.openbubbles.nativeapp.data.AttachmentMeta
import app.openbubbles.nativeapp.data.MessageItem
import app.openbubbles.nativeapp.data.MessageStatus
import app.openbubbles.nativeapp.service.MessageReminders
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.text.DateFormat
import java.time.ZonedDateTime
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
internal fun MessageActionSheet(
    message: MessageItem,
    selectedPart: Long,
    chatGuid: String,
    chatTitle: String,
    isSms: Boolean,
    isGroup: Boolean,
    attachmentFile: (String) -> File?,
    onDownloadAttachment: (AttachmentMeta) -> Unit,
    onReact: (Int, String?, Boolean) -> Unit,
    onReply: () -> Unit,
    onSticker: () -> Unit,
    onEdit: () -> Unit,
    onUnsend: () -> Unit,
    onForward: () -> Unit,
    onBookmark: () -> Unit,
    onSelectMultiple: () -> Unit,
    onViewThread: () -> Unit,
    onStartConversation: () -> Unit,
    onBlockSender: () -> Unit,
    onDeleteLocal: () -> Unit,
    onDeleteEverywhere: () -> Unit,
    onRetrySend: () -> Unit,
    onCancelSend: () -> Unit,
    onResult: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    // The sheet disappears before its action runs. The chat lifecycle outlives
    // that composition, while still cancelling work when the chat is removed.
    val attachmentActionScope = LocalLifecycleOwner.current.lifecycleScope
    var showCustomReaction by remember(message.guid) { mutableStateOf(false) }
    var showInfo by remember(message.guid) { mutableStateOf(false) }
    var showReminder by remember(message.guid) { mutableStateOf(false) }
    val attachments = message.attachmentMetas.ifEmpty { listOfNotNull(message.attachmentMeta) }
    val downloaded = attachments.mapNotNull { meta -> attachmentFile(meta.guid)?.let { meta to it } }
    val url = message.richLink?.url ?: Regex("https?://\\S+").find(message.text)?.value
    val selectedReaction = myReactionSelection(reactionsForPart(message.reactions, selectedPart))

    fun finish(action: () -> Unit) {
        onDismiss()
        action()
    }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        LazyColumn(modifier = Modifier.fillMaxWidth().heightIn(max = 620.dp)) {
            if (!isSms) {
                item {
                    MessageActionTapbacks(
                        // Mine, not simply the newest: a group message can
                        // carry someone else's tapback as its latest.
                        selected = selectedReaction,
                        onReact = onReact,
                    )
                }
                item {
                    val custom = selectedReaction?.takeIf { it.reactionIndex == CustomReactionIndex }
                    ActionRow(if (custom == null) "Custom reaction" else "Remove custom reaction") {
                        if (custom == null) showCustomReaction = true
                        else onReact(CustomReactionIndex, custom.emoji, false)
                    }
                }
            }
            item { ActionRow("Reply") { finish(onReply) } }
            if (!isSms) item { ActionRow("Add sticker") { finish(onSticker) } }
            item { ActionRow("Forward") { finish(onForward) } }
            item { ActionRow(if (message.isBookmarked) "Remove bookmark" else "Bookmark") { finish(onBookmark) } }
            item { ActionRow("Remind later") { showReminder = true } }
            item { ActionRow("Select multiple") { finish(onSelectMultiple) } }
            item { ActionRow("Message info") { showInfo = true } }
            if (!message.isFromMe && !message.senderAddress.isNullOrBlank()) {
                item { ActionRow("Create contact") { finish { createContact(context, message.senderAddress) } } }
                if (isGroup) item { ActionRow("Start conversation") { finish(onStartConversation) } }
                item { ActionRow("Block sender") { finish(onBlockSender) } }
            }
            if (downloaded.isNotEmpty()) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    item {
                        ActionRow("Save") {
                            finish {
                                launchMessageAttachmentIo(
                                    scope = attachmentActionScope,
                                    work = { saveAttachments(context, downloaded) },
                                    onSuccess = { onResult("Saved to Downloads") },
                                    onFailure = { onResult("Could not save attachment") },
                                )
                            }
                        }
                    }
                    item {
                        ActionRow("Save original") {
                            finish {
                                launchMessageAttachmentIo(
                                    scope = attachmentActionScope,
                                    work = { saveAttachments(context, downloaded) },
                                    onSuccess = { onResult("Saved original payload") },
                                    onFailure = { onResult("Could not save original attachment") },
                                )
                            }
                        }
                    }
                }
                item {
                    ActionRow("Share") {
                        finish {
                            launchMessageAttachmentIo(
                                scope = attachmentActionScope,
                                work = {
                                    downloaded.map { (meta, file) ->
                                        attachmentShareIntent(context, file, meta.mime)
                                    }
                                },
                                onSuccess = { intents ->
                                    intents.forEach { intent -> context.startActivity(intent) }
                                },
                                onFailure = { onResult("Could not share attachment") },
                            )
                        }
                    }
                }
                item {
                    ActionRow("Copy attachment") {
                        finish {
                            val (meta, file) = downloaded.first()
                            launchMessageAttachmentIo(
                                scope = attachmentActionScope,
                                work = { attachmentClipData(context, file, meta.mime) },
                                onSuccess = { clip ->
                                    context.getSystemService(ClipboardManager::class.java)
                                        ?.setPrimaryClip(clip)
                                    onResult("Attachment copied")
                                },
                                onFailure = { onResult("Could not copy attachment") },
                            )
                        }
                    }
                }
            }
            if (attachments.isNotEmpty()) {
                item { ActionRow("Re-download") { finish { attachments.forEach(onDownloadAttachment) } } }
            }
            if (url != null) item { ActionRow("Open in browser") { finish { openBrowser(context, url) } } }
            if (message.text.isNotBlank()) {
                item { ActionRow("Copy text") { finish { copyText(context, message.text); onResult("Copied") } } }
                item { ActionRow("Share text") { finish { shareText(context, message.text) } } }
            }
            item { ActionRow("View thread") { finish(onViewThread) } }
            if (!isSms && message.isFromMe && message.text.isNotBlank() && !message.unsent) {
                item { ActionRow("Edit") { finish(onEdit) } }
                item { ActionRow("Unsend") { finish(onUnsend) } }
            }
            if (canRetryOutgoingMessage(message)) {
                item { ActionRow("Retry send") { finish(onRetrySend) } }
            }
            if (canDeleteMessageLocally(message)) {
                item { ActionRow("Delete from this device", destructive = true) { finish(onDeleteLocal) } }
            }
            if (canDeleteMessageEverywhere(message)) {
                item { ActionRow("Delete on all devices", destructive = true) { finish(onDeleteEverywhere) } }
            }
            if (canCancelOutgoingMessage(message)) {
                item { ActionRow("Cancel send", destructive = true) { finish(onCancelSend) } }
            }
        }
    }

    if (showCustomReaction) {
        CustomReactionDialog(
            onReact = { emoji -> onReact(CustomReactionIndex, emoji, true) },
            onDismiss = { showCustomReaction = false },
        )
    }
    if (showInfo) {
        MessageInfoDialog(message = message, onDismiss = { showInfo = false })
    }
    if (showReminder) {
        ReminderDialog(
            onPick = { time ->
                MessageReminders.schedule(context, chatGuid, chatTitle, message, time)
                showReminder = false
                onDismiss()
                onResult("Reminder scheduled")
            },
            onDismiss = { showReminder = false },
        )
    }
}

/**
 * Emoji entry for a custom tapback, shared by the action sheet and the
 * centered reaction picker so both send through the same reaction index.
 */
@Composable
internal fun CustomReactionDialog(
    onReact: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var customReaction by remember { mutableStateOf("") }
    val normalized = normalizeCustomReaction(customReaction)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Custom reaction") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    CustomReactionSuggestions.forEach { emoji ->
                        FilledTonalIconButton(
                            onClick = { customReaction = emoji },
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .semantics { contentDescription = "Reaction $emoji" },
                        ) {
                            Text(text = emoji, modifier = Modifier.clearAndSetSemantics {})
                        }
                    }
                }
                TextField(
                    value = customReaction,
                    onValueChange = { customReaction = it },
                    singleLine = true,
                    label = { Text("Emoji") },
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = normalized != null,
                onClick = { onReact(requireNotNull(normalized)) },
            ) { Text("React") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
internal fun MessageActionTapbacks(
    selected: MyReactionSelection?,
    onReact: (Int, String?, Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .semantics { isTraversalGroup = true },
        horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween),
    ) {
        ActionTapbacks.forEachIndexed { index, emoji ->
            val isSelected = selected?.reactionIndex == index
            FilledTonalIconButton(
                onClick = {
                    onReact(index, null, enableTappedReaction(selected, index))
                },
                shapes = IconButtonDefaults.shapes(),
                colors = if (isSelected) {
                    IconButtonDefaults.filledTonalIconButtonColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                } else {
                    IconButtonDefaults.filledTonalIconButtonColors()
                },
                modifier = Modifier
                    .weight(1f)
                    .minimumInteractiveComponentSize()
                    .semantics {
                        contentDescription = tapbackContentDescription(emoji)
                        if (isSelected) stateDescription = "Selected"
                    },
            ) {
                Text(
                    text = emoji,
                    style = MaterialTheme.typography.titleMedium,
                    // The label already names the tapback.
                    modifier = Modifier.clearAndSetSemantics {},
                )
            }
        }
    }
}

@Composable
private fun ActionRow(label: String, destructive: Boolean = false, onClick: () -> Unit) {
    TextButton(onClick = onClick, modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp)) {
        Text(
            text = label,
            color = if (destructive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun MessageInfoDialog(message: MessageItem, onDismiss: () -> Unit) {
    val formatter = DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.MEDIUM)
    val lines = listOfNotNull(
        "GUID: ${message.guid}",
        "Service: ${if (message.status == MessageStatus.FAILED) "Failed send" else "Messages"}",
        "Sent: ${formatter.format(java.util.Date(message.date))}",
        message.dateDelivered?.let { "Delivered: ${formatter.format(java.util.Date(it))}" },
        message.dateRead?.let { "Read: ${formatter.format(java.util.Date(it))}" },
        "Parts: ${message.partCount}",
        message.errorCode?.let { "Error: $it" },
        message.errorMessage?.let { "Details: $it" },
    )
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Message info") },
        text = { Text(lines.joinToString("\n")) },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Done") } },
    )
}

@Composable
private fun ReminderDialog(onPick: (Long) -> Unit, onDismiss: () -> Unit) {
    val now = ZonedDateTime.now()
    val tonight = now.withHour(20).withMinute(0).withSecond(0).let { if (it.isAfter(now)) it else it.plusDays(1) }
    val tomorrow = now.plusDays(1).withHour(9).withMinute(0).withSecond(0)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Remind later") },
        text = {
            Column {
                ActionRow("In 1 hour") { onPick(System.currentTimeMillis() + 60 * 60 * 1000L) }
                ActionRow("Tonight") { onPick(tonight.toInstant().toEpochMilli()) }
                ActionRow("Tomorrow morning") { onPick(tomorrow.toInstant().toEpochMilli()) }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

private fun createContact(context: Context, address: String) {
    val isEmail = '@' in address
    context.startActivity(
        Intent(ContactsContract.Intents.Insert.ACTION).apply {
            type = ContactsContract.RawContacts.CONTENT_TYPE
            putExtra(if (isEmail) ContactsContract.Intents.Insert.EMAIL else ContactsContract.Intents.Insert.PHONE, address)
        },
    )
}

private fun copyText(context: Context, text: String) {
    context.getSystemService(ClipboardManager::class.java)?.setPrimaryClip(ClipData.newPlainText("Message", text))
}

private fun attachmentClipData(context: Context, file: File, mime: String?): ClipData {
    val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    return ClipData.newUri(context.contentResolver, file.name, uri).apply {
        description.extras = android.os.PersistableBundle().apply { putString("mime", mime) }
    }
}

private fun attachmentShareIntent(context: Context, file: File, mime: String?): Intent {
    val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    val send = Intent(Intent.ACTION_SEND).apply {
        type = mime ?: "application/octet-stream"
        putExtra(Intent.EXTRA_STREAM, uri)
        clipData = ClipData.newUri(context.contentResolver, file.name, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    return Intent.createChooser(send, "Share attachment")
}

private fun shareText(context: Context, text: String) {
    context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, text)
    }, "Share message"))
}

private fun openBrowser(context: Context, url: String) {
    context.startActivity(Intent(Intent.ACTION_VIEW, url.toUri()))
}

@RequiresApi(Build.VERSION_CODES.Q)
private suspend fun saveAttachments(context: Context, files: List<Pair<AttachmentMeta, File>>) {
    val resolver = context.contentResolver
    files.forEach { (meta, file) ->
        val values = ContentValues().apply {
            put(MediaStore.Downloads.DISPLAY_NAME, meta.name ?: file.name)
            put(MediaStore.Downloads.MIME_TYPE, meta.mime ?: "application/octet-stream")
            put(MediaStore.Downloads.IS_PENDING, 1)
        }
        publishMessageAttachmentExport(
            reserve = { resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values) },
            write = { uri ->
                val output = resolver.openOutputStream(uri)
                    ?: throw IOException("Could not open the attachment export")
                output.use { destination ->
                    file.inputStream().use { source ->
                        copyMessageAttachmentBytes(source, destination)
                    }
                }
            },
            publish = { uri ->
                values.clear()
                values.put(MediaStore.Downloads.IS_PENDING, 0)
                resolver.update(uri, values, null, null) > 0
            },
            rollback = { uri -> resolver.delete(uri, null, null) },
        )
    }
}

/** Keep provider and filesystem work off the owner dispatcher and suppress late results. */
internal fun <Result> launchMessageAttachmentIo(
    scope: CoroutineScope,
    ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    work: suspend () -> Result,
    onSuccess: suspend (Result) -> Unit,
    onFailure: suspend (Throwable) -> Unit,
): Job = scope.launch {
    try {
        val result = withContext(ioDispatcher) { work() }
        currentCoroutineContext().ensureActive()
        onSuccess(result)
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (failure: Exception) {
        currentCoroutineContext().ensureActive()
        onFailure(failure)
    }
}

/** Only abandon our own unpublished provider row; published exports belong to the user. */
internal suspend fun <Destination : Any> publishMessageAttachmentExport(
    reserve: () -> Destination?,
    write: suspend (Destination) -> Unit,
    publish: (Destination) -> Boolean,
    rollback: (Destination) -> Unit,
) {
    currentCoroutineContext().ensureActive()
    val destination = reserve() ?: throw IOException("Could not reserve the attachment export")
    var published = false
    try {
        write(destination)
        currentCoroutineContext().ensureActive()
        if (!publish(destination)) throw IOException("Could not publish the attachment export")
        published = true
    } finally {
        if (!published) {
            // This stays inside the IO context and runs synchronously even
            // when cancellation caused the interrupted copy to unwind.
            try {
                rollback(destination)
            } catch (_: Exception) {
                // Preserve the original provider, stream, or cancellation failure.
            }
        }
    }
}

/** A large local attachment must observe chat destruction between stream chunks. */
internal suspend fun copyMessageAttachmentBytes(input: InputStream, output: OutputStream) {
    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
    while (true) {
        currentCoroutineContext().ensureActive()
        val count = input.read(buffer)
        if (count < 0) break
        currentCoroutineContext().ensureActive()
        output.write(buffer, 0, count)
    }
    currentCoroutineContext().ensureActive()
    output.flush()
}

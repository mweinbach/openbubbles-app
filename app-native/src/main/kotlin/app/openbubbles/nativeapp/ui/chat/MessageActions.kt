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
import app.openbubbles.nativeapp.data.AttachmentMeta
import app.openbubbles.nativeapp.data.MessageItem
import app.openbubbles.nativeapp.data.MessageStatus
import app.openbubbles.nativeapp.service.MessageReminders
import app.openbubbles.nativeapp.ui.attachmentviewer.shareAttachment
import java.io.File
import java.text.DateFormat
import java.time.ZonedDateTime

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
    onCancelSend: () -> Unit,
    onResult: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    var showCustomReaction by remember(message.guid) { mutableStateOf(false) }
    var showInfo by remember(message.guid) { mutableStateOf(false) }
    var showReminder by remember(message.guid) { mutableStateOf(false) }
    val attachments = message.attachmentMetas.ifEmpty { listOfNotNull(message.attachmentMeta) }
    val downloaded = attachments.mapNotNull { meta -> attachmentFile(meta.guid)?.let { meta to it } }
    val url = message.richLink?.url ?: Regex("https?://\\S+").find(message.text)?.value

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
                        selectedEmoji = myReactionEmoji(
                            reactionsForPart(message.reactions, selectedPart),
                        ),
                        onReact = onReact,
                    )
                }
                item { ActionRow("Custom reaction") { showCustomReaction = true } }
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
                    item { ActionRow("Save") { finish { saveAttachments(context, downloaded); onResult("Saved to Downloads") } } }
                    item { ActionRow("Save original") { finish { saveAttachments(context, downloaded); onResult("Saved original payload") } } }
                }
                item { ActionRow("Share") { finish { downloaded.forEach { (meta, file) -> shareAttachment(context, file, meta.mime) } } } }
                item { ActionRow("Copy attachment") { finish { copyAttachment(context, downloaded.first().second, downloaded.first().first.mime); onResult("Attachment copied") } } }
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
            item { ActionRow("Delete from this device", destructive = true) { finish(onDeleteLocal) } }
            if (message.isFromMe && message.status in setOf(MessageStatus.SENDING, MessageStatus.FAILED)) {
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
    selectedEmoji: String?,
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
            val selected = selectedEmoji == emoji
            FilledTonalIconButton(
                onClick = {
                    onReact(index, null, enableTappedReaction(selectedEmoji, emoji))
                },
                shapes = IconButtonDefaults.shapes(),
                modifier = Modifier
                    .weight(1f)
                    .minimumInteractiveComponentSize()
                    .semantics {
                        contentDescription = tapbackContentDescription(emoji)
                        if (selected) stateDescription = "Selected"
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

private fun copyAttachment(context: Context, file: File, mime: String?) {
    val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    context.getSystemService(ClipboardManager::class.java)?.setPrimaryClip(
        ClipData.newUri(context.contentResolver, file.name, uri).apply {
            description.extras = android.os.PersistableBundle().apply { putString("mime", mime) }
        },
    )
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
private fun saveAttachments(context: Context, files: List<Pair<AttachmentMeta, File>>) {
    files.forEach { (meta, file) ->
        val values = ContentValues().apply {
            put(MediaStore.Downloads.DISPLAY_NAME, meta.name ?: file.name)
            put(MediaStore.Downloads.MIME_TYPE, meta.mime ?: "application/octet-stream")
            put(MediaStore.Downloads.IS_PENDING, 1)
        }
        val uri = context.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values) ?: return@forEach
        context.contentResolver.openOutputStream(uri)?.use { output -> file.inputStream().use { it.copyTo(output) } }
        values.clear()
        values.put(MediaStore.Downloads.IS_PENDING, 0)
        context.contentResolver.update(uri, values, null, null)
    }
}

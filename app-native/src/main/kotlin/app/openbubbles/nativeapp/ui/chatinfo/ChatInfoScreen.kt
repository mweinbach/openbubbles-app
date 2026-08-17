package app.openbubbles.nativeapp.ui.chatinfo

import android.content.Context
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import app.openbubbles.nativeapp.data.ChatListItem
import app.openbubbles.nativeapp.data.CoreGraph
import app.openbubbles.nativeapp.data.UiContacts
import app.openbubbles.nativeapp.data.effectiveBackgroundPath
import app.openbubbles.nativeapp.facetime.FaceTimeActivity
import app.openbubbles.nativeapp.ui.common.ChatAvatar
import app.openbubbles.nativeapp.ui.common.SegmentedRowGap
import app.openbubbles.nativeapp.ui.common.avatarColorFor
import app.openbubbles.nativeapp.ui.common.formatListTimestamp
import app.openbubbles.nativeapp.ui.common.rememberContactAvatarPath
import app.openbubbles.nativeapp.ui.common.rememberDecodedImage
import app.openbubbles.nativeapp.ui.common.segmentedRowShape
import app.openbubbles.nativeapp.ui.theme.OpenBubblesTheme
import android.content.Intent
import io.objectbox.query.QueryBuilder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.util.UUID
import kotlin.math.roundToInt

/** One participant row model: raw address plus the resolved contact info. */
data class ParticipantRow(
    val address: String,
    val name: String?,
    /** Contact photo URI when the participant resolves to a contact. */
    val avatarPath: String? = null,
)

/**
 * Conversation details and group mutations backed by the on-device engine.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatInfoScreen(
    chat: ChatListItem?,
    participants: List<ParticipantRow>,
    onBack: () -> Unit,
    onRename: suspend (String) -> Unit = {},
    onAddParticipant: suspend (String) -> Unit = {},
    onRemoveParticipant: suspend (String) -> Unit = {},
    onSetGroupIcon: suspend (File) -> Unit = {},
    onRemoveGroupIcon: suspend () -> Unit = {},
    onSetBackground: suspend (File) -> Unit = {},
    onClearBackground: suspend () -> Unit = {},
    onLeaveChat: suspend () -> Unit = {},
    /**
     * False when this screen renders as the visible extra pane (or a levitated
     * dialog) beside the conversation: there is nothing to navigate back to.
     */
    showBackButton: Boolean = true,
    onOpenChat: (Long) -> Unit = {},
    onOpenAttachment: (String) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    var error by remember { mutableStateOf<String?>(null) }
    var renameDialog by remember { mutableStateOf(false) }
    var addDialog by remember { mutableStateOf(false) }
    var confirmLeave by remember { mutableStateOf(false) }
    var renameText by remember(chat?.title) { mutableStateOf(chat?.title.orEmpty()) }
    var participantText by remember { mutableStateOf("") }
    var openContact by remember { mutableStateOf<ParticipantRow?>(null) }
    val isGroup = chat?.isGroup == true && !chat.isSms
    val directParticipant = participants.singleOrNull()?.takeIf { chat?.isGroup != true }

    fun launchAction(action: suspend () -> Unit, onSuccess: () -> Unit = {}) {
        scope.launch {
            runCatching { action() }
                .onSuccess { onSuccess() }
                .onFailure { error = it.message ?: "Conversation update failed" }
        }
    }

    fun openDirectChat(address: String) {
        val currentId = chat?.id
        if (chat?.isGroup != true && currentId != null) {
            onBack()
            return
        }
        launchAction(
            action = {
                val chatId = CoreGraph.findOrCreateChat(listOf(address), sms = chat?.isSms == true)
                    ?: error("Could not open conversation")
                onOpenChat(chatId)
            },
        )
    }

    fun startFaceTime(address: String? = null) {
        launchAction(
            action = {
                val targetId = if (address == null || chat?.isGroup != true) {
                    chat?.id ?: error("Conversation unavailable")
                } else {
                    CoreGraph.findOrCreateChat(listOf(address), sms = false)
                        ?: error("Could not start FaceTime")
                }
                val launch = CoreGraph.faceTimeCaller.start(targetId)
                context.startActivity(
                    Intent(context, FaceTimeActivity::class.java)
                        .putExtra("link", launch.link)
                        .putExtra("name", launch.displayName)
                        .putExtra("desc", launch.description)
                        .putExtra("callUuid", launch.callUuid),
                )
            },
        )
    }

    LaunchedEffect(error) {
        val message = error ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(message)
        error = null
    }

    val pickGroupPhoto = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val file = runCatching { prepareGroupIcon(context, uri) }
                .onFailure { error = it.message ?: "Could not read group photo" }
                .getOrNull() ?: return@launch
            launchAction({ onSetGroupIcon(file) })
        }
    }
    val pickBackground = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val file = runCatching { prepareChatBackground(context, uri) }
                .onFailure { error = it.message ?: "Could not read chat background" }
                .getOrNull() ?: return@launch
            try {
                runCatching { onSetBackground(file) }
                    .onFailure { error = it.message ?: "Could not set chat background" }
            } finally {
                file.delete()
            }
        }
    }

    Scaffold(
        modifier = modifier,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text("Conversation Details") },
                navigationIcon = {
                    if (showBackButton) {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                        }
                    }
                },
            )
        },
    ) { padding ->
        // The primary participant's contact poster (db Handle.posterPath),
        // when a decoded image exists, replaces the initials header.
        val primaryAddress = participants.firstOrNull()?.address ?: chat?.avatarAddress
        val posterFile = rememberPosterFile(primaryAddress)

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            if (directParticipant != null && chat != null) {
                val details = rememberContactDetails(
                    address = directParticipant.address,
                    fallbackName = directParticipant.name ?: chat.title,
                )
                ContactDetailsCard(
                    details = details,
                    location = rememberContactLocation(details.allAddresses),
                    sharedContent = rememberSharedContent(chat.id),
                    conversationTitle = if (chat.isSms) "SMS" else "iMessage",
                    conversationSubtitle = "Last active ${formatListTimestamp(chat.date)}",
                    smsChat = chat.isSms,
                    onMessage = { openDirectChat(details.handleAddress) },
                    onFaceTime = { startFaceTime() },
                    onOpenAttachment = onOpenAttachment,
                    modifier = Modifier.weight(1f),
                )
            } else {
                HeaderSection(
                    chat = chat,
                    participantCount = participants.size,
                    posterFile = posterFile,
                )
            }
            if (isGroup) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    OutlinedButton(
                        onClick = { renameDialog = true },
                        shapes = ButtonDefaults.shapes(),
                        modifier = Modifier.weight(1f),
                    ) {
                        Text("Rename")
                    }
                    OutlinedButton(
                        onClick = { pickGroupPhoto.launch("image/*") },
                        shapes = ButtonDefaults.shapes(),
                        modifier = Modifier.weight(1f),
                    ) { Text("Group photo") }
                }
                if (chat.avatarPath != null) {
                    TextButton(
                        onClick = { launchAction(onRemoveGroupIcon) },
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text("Remove group photo") }
                }
            }
            BackgroundSection(
                chat = chat,
                onChoose = { pickBackground.launch("image/*") },
                onClear = { launchAction(onClearBackground) },
            )
            if (directParticipant == null && participants.isNotEmpty()) {
                Text(
                    text = "PARTICIPANTS",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 28.dp, top = 20.dp, bottom = 6.dp),
                )
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(
                        start = 16.dp,
                        end = 16.dp,
                        top = 4.dp,
                        // Keep the last row above the gesture bar; only the
                        // group-only leave button used to carry this inset.
                        bottom = 4.dp + WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding(),
                    ),
                    verticalArrangement = Arrangement.spacedBy(SegmentedRowGap),
                ) {
                    itemsIndexed(participants, key = { _, it -> it.address }) { index, participant ->
                        ParticipantListRow(
                            participant = participant,
                            shape = segmentedRowShape(index, participants.size),
                            onOpen = { openContact = participant },
                            onRemove = if (isGroup) {
                                { launchAction(action = { onRemoveParticipant(participant.address) }) }
                            } else {
                                null
                            },
                        )
                    }
                    if (isGroup) {
                        item(key = "add-participant") {
                            OutlinedButton(
                                onClick = { addDialog = true },
                                shapes = ButtonDefaults.shapes(),
                                modifier = Modifier.fillMaxWidth(),
                            ) { Text("Add participant") }
                        }
                    }
                }
            } else {
                Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                    Text(
                        text = "No participants found",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            if (isGroup) {
                OutlinedButton(
                    onClick = { confirmLeave = true },
                    shapes = ButtonDefaults.shapes(shape = MaterialTheme.shapes.medium),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = MaterialTheme.colorScheme.error,
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp)
                        .navigationBarsPadding(),
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Logout,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                    Text(
                        text = "Leave this conversation",
                        modifier = Modifier.padding(start = 8.dp),
                    )
                }
            }
        }
    }

    if (renameDialog) {
        TextInputDialog(
            title = "Rename conversation",
            value = renameText,
            onValueChange = { renameText = it },
            confirmLabel = "Rename",
            onConfirm = {
                renameDialog = false
                launchAction(action = { onRename(renameText) })
            },
            onDismiss = { renameDialog = false },
        )
    }
    if (addDialog) {
        TextInputDialog(
            title = "Add participant",
            value = participantText,
            onValueChange = { participantText = it },
            confirmLabel = "Add",
            onConfirm = {
                val address = participantText.trim()
                addDialog = false
                participantText = ""
                launchAction(action = { onAddParticipant(address) })
            },
            onDismiss = { addDialog = false },
        )
    }
    if (confirmLeave) {
        AlertDialog(
            onDismissRequest = { confirmLeave = false },
            title = { Text("Leave conversation?") },
            text = { Text("You will stop receiving new messages from this group.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmLeave = false
                        launchAction(onLeaveChat, onBack)
                    },
                ) { Text("Leave") }
            },
            dismissButton = {
                TextButton(onClick = { confirmLeave = false }) { Text("Cancel") }
            },
        )
    }

    openContact?.let { row ->
        val details = rememberContactDetails(row.address, row.name)
        ContactSheet(
            details = details,
            location = rememberContactLocation(details.allAddresses),
            sharedContent = chat?.id?.let { rememberSharedContent(it) }.orEmpty(),
            conversationTitle = chat?.title,
            conversationSubtitle = if (chat?.isSms == true) "SMS" else "iMessage",
            smsChat = chat?.isSms == true,
            onDismiss = { openContact = null },
            onMessage = {
                openContact = null
                openDirectChat(row.address)
            },
            onFaceTime = {
                openContact = null
                startFaceTime(row.address)
            },
            onOpenAttachment = { guid ->
                openContact = null
                onOpenAttachment(guid)
            },
        )
    }
}

@Composable
private fun HeaderSection(chat: ChatListItem?, participantCount: Int, posterFile: File?) {
    // Poster header (contact-poster style): full-bleed image with the name
    // overlaid on a bottom scrim. Only when the file exists AND decodes.
    val poster = rememberDecodedImage(posterFile, maxDimensionPx = 1080)
    if (chat != null && poster != null) {
        PosterHeaderCard(
            title = chat.title,
            image = poster.image,
            aspectRatio = poster.aspectRatio,
            participantCount = participantCount,
        )
        return
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (chat != null) {
            ChatAvatar(
                title = chat.title,
                avatarColor = chat.avatarColor,
                size = 96.dp,
                avatarPath = chat.avatarPath ?: rememberContactAvatarPath(chat.avatarAddress),
            )
            Text(
                text = chat.title,
                style = MaterialTheme.typography.headlineSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        } else {
            Text(
                text = "Conversation",
                style = MaterialTheme.typography.headlineSmall,
            )
        }
        if (participantCount > 0) {
            Text(
                text = "$participantCount participant" + if (participantCount == 1) "" else "s",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun BackgroundSection(
    chat: ChatListItem?,
    onChoose: () -> Unit,
    onClear: () -> Unit,
) {
    val path = chat?.effectiveBackgroundPath()
    val file = remember(path) { path?.let(::File)?.takeIf { it.isFile } }
    val decoded = rememberDecodedImage(file, maxDimensionPx = 720)
    Surface(
        shape = MaterialTheme.shapes.largeIncreased,
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            if (decoded != null) {
                Image(
                    bitmap = decoded.image,
                    contentDescription = "Current chat background",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxWidth().aspectRatio(2.4f)
                        .clip(MaterialTheme.shapes.largeIncreased),
                )
            }
            Column(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = if (decoded == null) 14.dp else 4.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text("Chat background", style = MaterialTheme.typography.titleMedium)
                Text(
                    when {
                        chat?.customBackgroundPath != null -> "On this device"
                        chat?.transcriptBackgroundPath != null -> "Synced from Apple"
                        else -> "Use a photo behind this conversation"
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                ) {
                    if (chat?.customBackgroundPath != null) {
                        TextButton(onClick = onClear) {
                            Text(if (chat.transcriptBackgroundPath != null) "Use synced background" else "Remove")
                        }
                    }
                    TextButton(onClick = onChoose) {
                        Text(if (path == null) "Choose photo" else "Change")
                    }
                }
            }
        }
    }
}

/**
 * Contact-poster style header: the poster image cropped to a portrait card
 * (clamped around 3:4) with the chat title and participant count overlaid
 * on a gradient scrim.
 */
@Composable
private fun PosterHeaderCard(
    title: String,
    image: androidx.compose.ui.graphics.ImageBitmap,
    aspectRatio: Float,
    participantCount: Int,
) {
    // Posters are portrait; clamp extreme aspect ratios so a landscape
    // picture doesn't collapse the card or blow it up. The height cap keeps
    // the poster from swallowing the extra pane (participants must survive).
    val clamped = aspectRatio.coerceIn(0.6f, 1.4f)
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 16.dp)
            .heightIn(max = 280.dp)
            .aspectRatio(clamped)
            .clip(MaterialTheme.shapes.extraLarge),
    ) {
        Image(
            bitmap = image,
            contentDescription = title,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
        )
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colorStops = arrayOf(
                            0f to Color.Transparent,
                            0.55f to Color.Transparent,
                            1f to Color.Black.copy(alpha = 0.65f),
                        ),
                    ),
                ),
        )
        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(20.dp),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.headlineMedium,
                color = Color.White,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            if (participantCount > 0) {
                Text(
                    text = "$participantCount participant" + if (participantCount == 1) "" else "s",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White.copy(alpha = 0.85f),
                )
            }
        }
    }
}

/**
 * Resolves the primary participant's poster path from the db (read-only):
 * the Handle whose address or formattedAddress matches [address], when it
 * carries a `posterPath` that points at an existing file.
 */
@Composable
private fun rememberPosterFile(address: String?): File? =
    produceState<File?>(initialValue = null, address) {
        if (address.isNullOrBlank()) return@produceState
        value = withContext(Dispatchers.IO) {
            runCatching {
                val box = CoreGraph.store?.boxFor(app.openbubbles.db.Handle::class.java)
                    ?: return@runCatching null
                val handle = box.query()
                    .equal(
                        app.openbubbles.db.Handle_.address,
                        address,
                        QueryBuilder.StringOrder.CASE_SENSITIVE,
                    )
                    .or()
                    .equal(
                        app.openbubbles.db.Handle_.formattedAddress,
                        address,
                        QueryBuilder.StringOrder.CASE_SENSITIVE,
                    )
                    .build()
                    .use { it.findFirst() }
                    ?: return@runCatching null
                handle.posterPath
                    ?.takeIf { it.isNotBlank() }
                    ?.let(::File)
                    ?.takeIf { it.isFile }
            }.getOrNull()
        }
    }.value

@Composable
private fun ParticipantListRow(
    participant: ParticipantRow,
    shape: RoundedCornerShape,
    onOpen: () -> Unit,
    onRemove: (() -> Unit)? = null,
) {
    val displayName = participant.name ?: participant.address
    Surface(
        shape = shape,
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(role = Role.Button, onClickLabel = "Contact details", onClick = onOpen),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            ChatAvatar(
                title = displayName,
                avatarColor = avatarColorFor(participant.address),
                size = 40.dp,
                avatarPath = participant.avatarPath,
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = displayName,
                    style = MaterialTheme.typography.bodyLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (participant.name != null) {
                    Text(
                        text = participant.address,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            if (onRemove != null) {
                TextButton(onClick = onRemove) { Text("Remove") }
            }
        }
    }
}

@Composable
private fun TextInputDialog(
    title: String,
    value: String,
    onValueChange: (String) -> Unit,
    confirmLabel: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            TextField(
                value = value,
                onValueChange = onValueChange,
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = {
            TextButton(onClick = onConfirm, enabled = value.isNotBlank()) { Text(confirmLabel) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

/** Decode, center-crop, and persist the 570px PNG expected by iMessage group icons. */
private suspend fun prepareGroupIcon(context: Context, uri: Uri): File = withContext(Dispatchers.IO) {
    val source = context.contentResolver.openInputStream(uri)?.use(BitmapFactory::decodeStream)
        ?: error("could not decode group photo")
    val side = minOf(source.width, source.height)
    val left = (source.width - side) / 2
    val top = (source.height - side) / 2
    val square = Bitmap.createBitmap(source, left, top, side, side)
    val scaled = Bitmap.createScaledBitmap(square, 570, 570, true)
    val directory = File(context.filesDir, "group_icons").apply { mkdirs() }
    val destination = File(directory, "outgoing-${UUID.randomUUID()}.png")
    FileOutputStream(destination).use { output ->
        check(scaled.compress(Bitmap.CompressFormat.PNG, 100, output)) { "could not encode group photo" }
    }
    if (scaled !== square) scaled.recycle()
    if (square !== source) square.recycle()
    source.recycle()
    destination
}

private suspend fun prepareChatBackground(context: Context, uri: Uri): File = withContext(Dispatchers.IO) {
    val source = context.contentResolver.openInputStream(uri)?.use(BitmapFactory::decodeStream)
        ?: error("could not decode chat background")
    val maxSide = maxOf(source.width, source.height)
    val scale = (1600f / maxSide.toFloat()).coerceAtMost(1f)
    val outputBitmap = if (scale < 1f) {
        Bitmap.createScaledBitmap(
            source,
            (source.width * scale).roundToInt().coerceAtLeast(1),
            (source.height * scale).roundToInt().coerceAtLeast(1),
            true,
        )
    } else {
        source
    }
    val destination = File(context.cacheDir, "chat-background-${UUID.randomUUID()}.jpg")
    FileOutputStream(destination).use { output ->
        check(outputBitmap.compress(Bitmap.CompressFormat.JPEG, 90, output)) {
            "could not encode chat background"
        }
    }
    if (outputBitmap !== source) outputBitmap.recycle()
    source.recycle()
    destination
}

/**
 * Resolves contact names + photo URIs for participant addresses via
 * [UiContacts] (null resolver or unknown addresses keep the raw address as
 * the display name).
 */
@Composable
fun rememberParticipantRows(addresses: List<String>): List<ParticipantRow> {
    val resolved = remember { mutableStateMapOf<String, Pair<String?, String?>>() }
    val generation by UiContacts.avatarGeneration.collectAsState()
    LaunchedEffect(addresses, generation) {
        val resolver = UiContacts.contactNames ?: return@LaunchedEffect
        val contacts = withContext(Dispatchers.IO) {
            addresses.distinct().mapNotNull { address ->
                resolver(address)?.let { address to it }
            }
        }
        contacts.forEach { (address, info) ->
            resolved[address] = info
        }
    }
    return addresses.map {
        ParticipantRow(
            address = it,
            name = resolved[it]?.first,
            avatarPath = resolved[it]?.second,
        )
    }
}

// --------------------------------------------------------------------- previews

@Preview(showBackground = true)
@Preview(showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun ChatInfoScreenPreview() {
    OpenBubblesTheme {
        ChatInfoScreen(
            chat = ChatListItem(
                id = 1,
                title = "Family",
                snippet = null,
                date = System.currentTimeMillis(),
                unread = 0,
                pinned = true,
                avatarColor = 0xFF7C4FDF,
                isGroup = true,
            ),
            participants = listOf(
                ParticipantRow("mom@icloud.com", "Mom"),
                ParticipantRow("dad@icloud.com", "Dad"),
                ParticipantRow("+15550101", null),
            ),
            onBack = {},
            onLeaveChat = {},
        )
    }
}

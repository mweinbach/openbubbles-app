package app.openbubbles.nativeapp.ui.chatinfo

import android.content.Context
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.core.graphics.scale
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.Photo
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
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
import app.openbubbles.nativeapp.data.AppGraph
import app.openbubbles.nativeapp.data.ChatListItem
import app.openbubbles.nativeapp.data.CoreGraph
import app.openbubbles.nativeapp.data.UiContacts
import app.openbubbles.nativeapp.data.effectiveBackgroundPath
import app.openbubbles.nativeapp.ui.common.rememberChatBackground
import app.openbubbles.nativeapp.facetime.startOutgoingFaceTime
import app.openbubbles.nativeapp.ui.common.ChatAvatar
import app.openbubbles.nativeapp.ui.common.SegmentedRowGap
import app.openbubbles.nativeapp.ui.common.avatarColorFor
import app.openbubbles.nativeapp.ui.common.formatListTimestamp
import app.openbubbles.nativeapp.ui.common.rememberContactAvatarPath
import app.openbubbles.nativeapp.ui.common.rememberDecodedImage
import app.openbubbles.nativeapp.ui.common.segmentedRowShape
import app.openbubbles.nativeapp.ui.theme.OpenBubblesTheme
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
 * In a direct conversation this screen is the contact card for the other
 * person; in a group it lists participants whose rows open a contact sheet.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatInfoScreen(
    chat: ChatListItem?,
    participants: List<ParticipantRow>,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    onRename: suspend (String) -> Unit = {},
    onAddParticipant: suspend (String) -> Unit = {},
    onRemoveParticipant: suspend (String) -> Unit = {},
    onSetGroupIcon: suspend (File) -> Unit = {},
    onRemoveGroupIcon: suspend () -> Unit = {},
    onSetBackground: suspend (File) -> Unit = {},
    onClearBackground: suspend () -> Unit = {},
    onLeaveChat: suspend () -> Unit = {},
    onReportJunk: suspend () -> Unit = {},
    onOpenBookmarks: () -> Unit = {},
    /**
     * False only when this screen is a visible third pane (~1200dp). On
     * phones and two-pane layouts it replaces the conversation, so back
     * returns to the chat.
     */
    showBackButton: Boolean = true,
    onOpenChat: (Long) -> Unit = {},
    onOpenAttachment: (String) -> Unit = {},
    /** Local file for an attachment guid; feeds the shared-photo thumbnails. */
    attachmentFile: (String) -> File? = { null },
) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    var error by remember { mutableStateOf<String?>(null) }
    var renameDialog by remember { mutableStateOf(false) }
    var addDialog by remember { mutableStateOf(false) }
    var confirmLeave by remember { mutableStateOf(false) }
    var confirmReportJunk by remember { mutableStateOf(false) }
    var renameText by remember(chat?.title) { mutableStateOf(chat?.title.orEmpty()) }
    var participantText by remember { mutableStateOf("") }
    var openContact by remember { mutableStateOf<ParticipantRow?>(null) }
    val isGroup = chat?.isGroup == true && !chat.isSms
    val showDirectCard = shouldShowDirectContactCard(chat?.isGroup)

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
                startOutgoingFaceTime(context, launch)
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
                title = {
                    Text(chat?.title?.takeIf { it.isNotBlank() } ?: "Conversation")
                },
                navigationIcon = {
                    if (showBackButton) {
                        FilledTonalIconButton(
                            onClick = onBack,
                            shapes = IconButtonDefaults.shapes(),
                        ) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                        }
                    }
                },
            )
        },
    ) { padding ->
        // The primary participant's contact poster (db Handle.posterPath),
        // when a decoded image exists, replaces the initials header.
        val primaryAddress = directContactAddress(
            chat?.avatarAddress,
            participants.map { it.address },
        )
        val posterFile = rememberPosterFile(primaryAddress.ifBlank { null })

        if (showDirectCard && chat != null) {
            val details = mergeContactAddresses(
                rememberContactDetails(
                    address = directContactAddress(chat.avatarAddress, participants.map { it.address }),
                    fallbackName = chat.title,
                ),
                participants.map { it.address },
            )
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .verticalScroll(rememberScrollState())
                    .navigationBarsPadding()
                    .padding(bottom = 16.dp),
            ) {
                ContactDetailsCard(
                    details = details,
                    location = rememberContactLocation(details.handleAddress, details.allAddresses),
                    sharedContent = rememberSharedContent(chat.id),
                    conversationTitle = if (chat.isSms) "SMS" else "iMessage",
                    conversationSubtitle = "Last active ${formatListTimestamp(chat.date)}",
                    smsChat = chat.isSms,
                    onMessage = { openDirectChat(details.handleAddress.ifBlank { chat.avatarAddress.orEmpty() }) },
                    onFaceTime = { startFaceTime() },
                    onOpenAttachment = onOpenAttachment,
                    posterFile = posterFile,
                    attachmentFile = attachmentFile,
                    scrollable = false,
                )
                BackgroundSection(
                    chat = chat,
                    onChoose = { pickBackground.launch("image/*") },
                    onClear = { launchAction(onClearBackground) },
                )
                ChatLocalOptions(chat, onOpenBookmarks = onOpenBookmarks)
                if (!chat.isSms) {
                    TextButton(
                        onClick = { confirmReportJunk = true },
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                    ) {
                        Text("Report Junk", color = MaterialTheme.colorScheme.error)
                    }
                }
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
            ) {
                HeaderSection(
                    chat = chat,
                    participantCount = participants.size,
                    posterFile = posterFile,
                )
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
                chat?.let { ChatLocalOptions(it, onOpenBookmarks = onOpenBookmarks) }
                if (participants.isNotEmpty()) {
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
                } else if (isGroup) {
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
    if (confirmReportJunk) {
        AlertDialog(
            onDismissRequest = { confirmReportJunk = false },
            title = { Text("Report Junk?") },
            text = { Text("The last five incoming messages will be reported to Apple. This sender will be blocked and the conversation archived.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmReportJunk = false
                        launchAction(onReportJunk, onBack)
                    },
                ) { Text("Report", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { confirmReportJunk = false }) { Text("Cancel") }
            },
        )
    }

    openContact?.let { row ->
        val details = rememberContactDetails(row.address, row.name)
        ContactSheet(
            details = details,
            location = rememberContactLocation(details.handleAddress, details.allAddresses),
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
            posterFile = rememberPosterFile(row.address),
            attachmentFile = attachmentFile,
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

/**
 * Compact background control: a small current-background thumbnail, the
 * source label, and Choose/Change + clear actions in one row — not a banner
 * card, so the contact card stays the focus of this screen.
 */
@Composable
private fun BackgroundSection(
    chat: ChatListItem?,
    onChoose: () -> Unit,
    onClear: () -> Unit,
) {
    val path = chat?.effectiveBackgroundPath()
    val decoded = rememberChatBackground(
        customPath = chat?.customBackgroundPath,
        syncedPath = chat?.transcriptBackgroundPath,
        maxDimensionPx = 256,
    )
    Surface(
        shape = MaterialTheme.shapes.largeIncreased,
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(MaterialTheme.shapes.medium)
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh),
                contentAlignment = Alignment.Center,
            ) {
                if (decoded != null) {
                    Image(
                        bitmap = decoded.image,
                        contentDescription = "Current chat background",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                } else {
                    Icon(
                        imageVector = Icons.Filled.Photo,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Column(modifier = Modifier.weight(1f)) {
                Text("Chat background", style = MaterialTheme.typography.bodyLarge)
                Text(
                    when {
                        chat?.customBackgroundPath != null -> "On this device"
                        chat?.transcriptBackgroundPath != null -> "Synced from Apple"
                        else -> "No background set"
                    },
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (chat?.customBackgroundPath != null) {
                TextButton(onClick = onClear) {
                    Text(if (chat.transcriptBackgroundPath != null) "Use synced" else "Remove")
                }
            }
            TextButton(onClick = onChoose) {
                Text(if (path == null && decoded == null) "Choose" else "Change")
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
 * The primary participant's poster image, seeded from the warm cache (filled
 * while the chat was on screen) so the poster header renders on the first
 * frame instead of swapping in after the db read.
 */
@Composable
private fun rememberPosterFile(address: String?): File? =
    produceState<File?>(
        initialValue = address?.let(ChatInfoWarmCache::poster),
        address,
    ) {
        if (address.isNullOrBlank()) return@produceState
        val loaded = loadHandlePosterFile(address)
        value = loaded
        if (loaded != null) ChatInfoWarmCache.putPoster(address, loaded)
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
    val scaled = square.scale(570, 570)
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
        source.scale(
            (source.width * scale).roundToInt().coerceAtLeast(1),
            (source.height * scale).roundToInt().coerceAtLeast(1),
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

@Composable
private fun ChatLocalOptions(
    chat: ChatListItem,
    onOpenBookmarks: () -> Unit = {},
) {
    val scope = rememberCoroutineScope()
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        Text(
            text = "OPTIONS",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 4.dp),
        )
        TextButton(onClick = { AppGraph.chats.setLockChatName(chat.id, !chat.lockChatName) }) {
            Text(if (chat.lockChatName) "Unlock chat name" else "Lock chat name")
        }
        TextButton(onClick = { AppGraph.chats.setLockChatIcon(chat.id, !chat.lockChatIcon) }) {
            Text(if (chat.lockChatIcon) "Unlock chat icon" else "Lock chat icon")
        }
        TextButton(
            onClick = { AppGraph.chats.setAutoSendTypingIndicators(chat.id, !chat.autoSendTypingIndicators) },
        ) {
            Text(
                if (chat.autoSendTypingIndicators) {
                    "Stop sending typing indicators"
                } else {
                    "Send typing indicators in this chat"
                },
            )
        }
        TextButton(
            onClick = { AppGraph.chats.setAutoSendReadReceipts(chat.id, !chat.autoSendReadReceipts) },
        ) {
            Text(
                if (chat.autoSendReadReceipts) {
                    "Don't send read receipts in this chat"
                } else {
                    "Send read receipts in this chat"
                },
            )
        }
        TextButton(onClick = onOpenBookmarks) {
            Text("Bookmarks")
        }
        TextButton(
            onClick = { scope.launch { AppGraph.chats.clearTranscript(chat.id) } },
        ) {
            Text("Clear transcript")
        }
        if (chat.blocked) {
            TextButton(onClick = { AppGraph.chats.setBlocked(chat.id, blocked = false) }) {
                Text("Unblock sender")
            }
        }
    }
}

@Preview(showBackground = true)
@Preview(showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun ChatInfoDirectScreenPreview() {
    OpenBubblesTheme {
        ChatInfoScreen(
            chat = ChatListItem(
                id = 2,
                title = "Mark Linsangan",
                snippet = null,
                date = System.currentTimeMillis(),
                unread = 0,
                pinned = false,
                avatarColor = 0xFF006C4C,
                avatarAddress = "+17033092799",
                isGroup = false,
            ),
            // Empty on purpose: 1:1 chats must still show the contact card
            // when handle rows were never linked.
            participants = emptyList(),
            onBack = {},
        )
    }
}

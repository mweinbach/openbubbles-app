package app.openbubbles.nativeapp.ui.chatinfo

import android.content.res.Configuration
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import app.openbubbles.nativeapp.data.ChatListItem
import app.openbubbles.nativeapp.data.CoreGraph
import app.openbubbles.nativeapp.data.UiContacts
import app.openbubbles.nativeapp.ui.common.ChatAvatar
import app.openbubbles.nativeapp.ui.common.rememberContactAvatarPath
import app.openbubbles.nativeapp.ui.common.rememberDecodedImage
import app.openbubbles.nativeapp.ui.theme.OpenBubblesTheme
import io.objectbox.query.QueryBuilder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/** One participant row model: raw address plus the resolved contact info. */
data class ParticipantRow(
    val address: String,
    val name: String?,
    /** Contact photo URI when the participant resolves to a contact. */
    val avatarPath: String? = null,
)

/**
 * Conversation details: avatar + title (read-only for now; editing lands with
 * a later milestone), participant list with contact names, and a leave-chat
 * action wired through [onLeaveChat].
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatInfoScreen(
    chat: ChatListItem?,
    participants: List<ParticipantRow>,
    onBack: () -> Unit,
    onLeaveChat: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            TopAppBar(
                title = { Text("Conversation Details") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
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
            HeaderSection(
                chat = chat,
                participantCount = participants.size,
                posterFile = posterFile,
            )
            if (participants.isNotEmpty()) {
                Text(
                    text = "Participants",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 24.dp, top = 16.dp, bottom = 4.dp),
                )
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
                ) {
                    items(participants, key = { it.address }) { participant ->
                        ParticipantListRow(participant = participant)
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
            OutlinedButton(
                onClick = onLeaveChat,
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = MaterialTheme.colorScheme.error,
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
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
                avatarPath = rememberContactAvatarPath(chat.avatarAddress),
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
    // picture doesn't collapse the card or blow it up.
    val clamped = aspectRatio.coerceIn(0.6f, 1.4f)
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 16.dp)
            .aspectRatio(clamped)
            .clip(RoundedCornerShape(28.dp)),
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
private fun ParticipantListRow(participant: ParticipantRow) {
    val displayName = participant.name ?: participant.address
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            ChatAvatar(
                title = displayName,
                avatarColor = avatarColorForAddress(participant.address),
                size = 40.dp,
                avatarPath = participant.avatarPath,
            )
            Column {
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
        }
    }
}

/** Stable avatar color for an address (same palette idea as the chat list). */
private fun avatarColorForAddress(address: String): Long {
    val palette = longArrayOf(
        0xFF7C4FDF, 0xFF4C8BF5, 0xFF00897B, 0xFFD81B60, 0xFFF4511E,
        0xFF6D4C41, 0xFF3949AB, 0xFF43A047,
    )
    return palette[kotlin.math.abs(address.hashCode()) % palette.size]
}

/**
 * Resolves contact names + photo URIs for participant addresses via
 * [UiContacts] (null resolver or unknown addresses keep the raw address as
 * the display name).
 */
@Composable
fun rememberParticipantRows(addresses: List<String>): List<ParticipantRow> {
    val resolved = remember { mutableStateMapOf<String, Pair<String?, String?>>() }
    LaunchedEffect(addresses) {
        val resolver = UiContacts.contactNames ?: return@LaunchedEffect
        addresses.distinct().forEach { address ->
            val info = resolver(address) ?: return@forEach
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

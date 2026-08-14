package app.openbubbles.nativeapp.ui.chatinfo

import android.content.res.Configuration
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
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
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import app.openbubbles.nativeapp.data.ChatListItem
import app.openbubbles.nativeapp.data.UiContacts
import app.openbubbles.nativeapp.ui.common.ChatAvatar
import app.openbubbles.nativeapp.ui.theme.OpenBubblesTheme

/** One participant row model: raw address plus the resolved contact name. */
data class ParticipantRow(
    val address: String,
    val name: String?,
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
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            HeaderSection(chat = chat, participantCount = participants.size)
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
private fun HeaderSection(chat: ChatListItem?, participantCount: Int) {
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
 * Resolves contact names for participant addresses via [UiContacts] (null
 * resolver or unknown addresses keep the raw address as the display name).
 */
@Composable
fun rememberParticipantRows(addresses: List<String>): List<ParticipantRow> {
    val names = remember { mutableStateMapOf<String, String>() }
    LaunchedEffect(addresses) {
        val resolver = UiContacts.contactNames ?: return@LaunchedEffect
        addresses.distinct().forEach { address ->
            val name = resolver(address)?.first ?: return@forEach
            names[address] = name
        }
    }
    return addresses.map { ParticipantRow(address = it, name = names[it]) }
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

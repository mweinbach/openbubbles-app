package app.openbubbles.nativeapp.ui.chatinfo

import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.ChatBubble
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Photo
import androidx.compose.material.icons.filled.VideoCall
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import app.openbubbles.nativeapp.data.CoreGraph
import app.openbubbles.nativeapp.data.PushStateHolder
import app.openbubbles.nativeapp.data.SharedContentPreview
import app.openbubbles.nativeapp.data.UiContacts
import app.openbubbles.nativeapp.ui.common.ChatAvatar
import app.openbubbles.nativeapp.ui.common.avatarColorFor
import app.openbubbles.nativeapp.ui.findmy.FindMyPort
import app.openbubbles.nativeapp.ui.findmy.FmPoint
import app.openbubbles.nativeapp.ui.findmy.RustFindMyPort
import app.openbubbles.nativeapp.ui.theme.OpenBubblesTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private val ContactContentMaxWidth = 840.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ContactSheet(
    details: ContactDetails,
    location: ContactLocationUi,
    sharedContent: List<SharedContentPreview>,
    conversationTitle: String?,
    conversationSubtitle: String?,
    smsChat: Boolean,
    onDismiss: () -> Unit,
    onMessage: () -> Unit,
    onFaceTime: () -> Unit,
    onOpenAttachment: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    ModalBottomSheet(onDismissRequest = onDismiss, modifier = modifier) {
        ContactDetailsCard(
            details = details,
            location = location,
            sharedContent = sharedContent,
            conversationTitle = conversationTitle,
            conversationSubtitle = conversationSubtitle,
            smsChat = smsChat,
            onMessage = onMessage,
            onFaceTime = onFaceTime,
            onOpenAttachment = onOpenAttachment,
            modifier = Modifier
                .navigationBarsPadding()
                .padding(bottom = 16.dp),
        )
    }
}

@Composable
fun ContactDetailsCard(
    details: ContactDetails,
    location: ContactLocationUi,
    sharedContent: List<SharedContentPreview>,
    conversationTitle: String?,
    conversationSubtitle: String?,
    smsChat: Boolean,
    onMessage: () -> Unit,
    onFaceTime: () -> Unit,
    onOpenAttachment: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val uriHandler = LocalUriHandler.current
    Column(
        modifier = modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Column(
            modifier = Modifier.widthIn(max = ContactContentMaxWidth).fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            ChatAvatar(
                title = details.displayName,
                avatarColor = avatarColorFor(details.handleAddress),
                size = 96.dp,
                avatarPath = details.avatarPath,
            )
            Text(
                text = details.displayName,
                style = MaterialTheme.typography.headlineSmall,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            if (conversationTitle != null) {
                Text(
                    text = conversationTitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            conversationSubtitle?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Row(
            modifier = Modifier.widthIn(max = ContactContentMaxWidth).fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
            ContactAction(
                icon = Icons.Filled.ChatBubble,
                label = "Message",
                onClick = onMessage,
            )
            ContactAction(
                icon = Icons.Filled.Call,
                label = "Call",
                enabled = details.phones.isNotEmpty(),
                onClick = { details.phones.firstOrNull()?.let { dialNumber(context, it) } },
            )
            ContactAction(
                icon = Icons.Filled.VideoCall,
                label = "FaceTime",
                enabled = !smsChat,
                onClick = onFaceTime,
            )
        }
        if (details.phones.isNotEmpty() || details.emails.isNotEmpty()) {
            ContactSection("Contact info") {
                details.phones.forEach { phone ->
                    ContactInfoRow(
                        label = "Phone",
                        value = phone,
                        onClick = { dialNumber(context, phone) },
                    )
                }
                details.emails.forEach { email ->
                    ContactInfoRow(
                        label = "Email",
                        value = email,
                        onClick = {
                            runCatching { uriHandler.openUri("mailto:$email") }
                        },
                    )
                }
            }
        }
        ContactLocationSection(
            location = location,
            onOpenMaps = { point -> openLocationInMaps(context, details.displayName, point) },
        )
        if (sharedContent.isNotEmpty()) {
            ContactSection("Shared") {
                sharedContent.forEach { item ->
                    SharedContentRow(
                        item = item,
                        onClick = {
                            item.attachmentGuid?.let(onOpenAttachment)
                            item.url?.let { url -> runCatching { uriHandler.openUri(url) } }
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun ContactAction(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    onClick: () -> Unit,
    enabled: Boolean = true,
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        FilledTonalIconButton(
            onClick = onClick,
            enabled = enabled,
            shapes = IconButtonDefaults.shapes(),
            modifier = Modifier.size(52.dp),
        ) {
            Icon(icon, contentDescription = label)
        }
        Spacer(Modifier.height(6.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = if (enabled) {
                MaterialTheme.colorScheme.onSurface
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
        )
    }
}

@Composable
private fun ContactSection(
    title: String,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = Modifier.widthIn(max = ContactContentMaxWidth).fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(
            text = title.uppercase(),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 12.dp),
        )
        Surface(
            shape = MaterialTheme.shapes.extraLarge,
            color = MaterialTheme.colorScheme.surfaceContainerLowest,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(content = content)
        }
    }
}

@Composable
private fun ContactInfoRow(
    label: String,
    value: String,
    onClick: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        Text(text = label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(text = value, style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
private fun SharedContentRow(
    item: SharedContentPreview,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(
            imageVector = if (item.url != null) Icons.Filled.Link else Icons.Filled.Photo,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = item.label,
            style = MaterialTheme.typography.bodyLarge,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun ContactLocationSection(
    location: ContactLocationUi,
    onOpenMaps: (FmPoint) -> Unit,
) {
    val (title, detail, point) = when (location) {
        ContactLocationUi.Loading -> Triple("Find My", "Checking location…", null)
        ContactLocationUi.Unavailable -> Triple(
            "Find My",
            "Location isn't available. Sign in with your Apple ID to see people who share with you.",
            null,
        )
        ContactLocationUi.NotSharing -> Triple(
            "Find My",
            "This person isn't sharing their location with you.",
            null,
        )
        is ContactLocationUi.NoFix -> Triple(
            "Find My",
            "${location.friendName} is sharing, but no recent location is available.",
            null,
        )
        is ContactLocationUi.Located -> {
            val freshness = locationFreshness(location.point.timestampMs)
            val accuracy = locationAccuracy(location.point.accuracyMeters)
            val line = listOfNotNull(freshness, accuracy).joinToString(" · ")
                .ifBlank { "Location available" }
            Triple("Find My", line, location.point)
        }
        is ContactLocationUi.Failed -> Triple("Find My", location.message, null)
    }
    ContactSection(title) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .then(
                    if (point != null) {
                        Modifier.clickable(
                            role = Role.Button,
                            onClickLabel = "Open in Maps",
                            onClick = { onOpenMaps(point) },
                        )
                    } else {
                        Modifier
                    },
                )
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (location is ContactLocationUi.Loading) {
                LoadingIndicator(modifier = Modifier.size(24.dp))
            } else {
                Icon(
                    imageVector = Icons.Filled.LocationOn,
                    contentDescription = null,
                    tint = if (point != null) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )
            }
            Text(
                text = detail,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
        }
        if (point != null) {
            TextButton(
                onClick = { onOpenMaps(point) },
                modifier = Modifier.padding(start = 4.dp, bottom = 4.dp),
            ) {
                Text("Open in Maps")
            }
        }
    }
}

@Composable
internal fun rememberContactDetails(
    address: String,
    fallbackName: String?,
): ContactDetails {
    val generation by UiContacts.avatarGeneration.collectAsState()
    val initial = remember(address, fallbackName) {
        resolveContactDetails(address, fallbackName, emptyList())
    }
    return produceState(initialValue = initial, address, fallbackName, generation) {
        value = withContext(Dispatchers.IO) {
            resolveContactDetails(address, fallbackName, CoreGraph.preferredContacts())
        }
    }.value
}

@Composable
internal fun rememberContactLocation(
    addresses: List<String>,
    port: FindMyPort = remember { RustFindMyPort { PushStateHolder.state } },
): ContactLocationUi {
    return produceState<ContactLocationUi>(
        initialValue = ContactLocationUi.Loading,
        addresses,
        port,
    ) {
        value = ContactLocationUi.Loading
        value = withContext(Dispatchers.IO) {
            if (!port.isAvailable()) return@withContext ContactLocationUi.Unavailable
            val cached = runCatching { port.friends() }
            val cachedLocation = cached.getOrNull()?.let { friends ->
                contactLocationFromFriends(addresses, friends, available = true)
            }
            if (cachedLocation is ContactLocationUi.Located) return@withContext cachedLocation
            val refreshed = runCatching { port.refreshFriends() }
            when {
                refreshed.isSuccess -> contactLocationFromFriends(
                    addresses,
                    refreshed.getOrDefault(emptyList()),
                    available = true,
                )
                cachedLocation != null -> cachedLocation
                else -> ContactLocationUi.Failed(
                    refreshed.exceptionOrNull()?.message
                        ?: cached.exceptionOrNull()?.message
                        ?: "Couldn't load location",
                )
            }
        }
    }.value
}

@Composable
internal fun rememberSharedContent(chatId: Long): List<SharedContentPreview> {
    return produceState(initialValue = emptyList(), chatId) {
        value = withContext(Dispatchers.IO) {
            CoreGraph.chatInfo.sharedContent(chatId)
        }
    }.value
}

private fun dialNumber(context: Context, number: String) {
    runCatching {
        context.startActivity(
            Intent(Intent.ACTION_DIAL, Uri.parse("tel:${displayContactAddress(number)}"))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }
}

internal fun openLocationInMaps(context: Context, name: String, point: FmPoint) {
    runCatching {
        val label = Uri.encode(name)
        val uri = Uri.parse(
            "geo:${point.latitude},${point.longitude}" +
                "?q=${point.latitude},${point.longitude}($label)",
        )
        context.startActivity(
            Intent(Intent.ACTION_VIEW, uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }
}

@Preview(showBackground = true)
@Preview(showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun ContactDetailsCardPreview() {
    OpenBubblesTheme {
        ContactDetailsCard(
            details = ContactDetails(
                displayName = "Mark Linsangan",
                avatarPath = null,
                phones = listOf("+1 (703) 309-2799"),
                emails = listOf("mark@icloud.com"),
                handleAddress = "+17033092799",
            ),
            location = ContactLocationUi.Located(
                friendName = "Mark Linsangan",
                point = FmPoint(37.7749, -122.4194, 18.0, System.currentTimeMillis() - 8 * 60_000),
            ),
            sharedContent = listOf(
                SharedContentPreview("1", "trailhead.jpg", attachmentGuid = "a1", isImage = true),
                SharedContentPreview("2", "x.com/status/…", url = "https://x.com"),
            ),
            conversationTitle = "iMessage",
            conversationSubtitle = "Last active today",
            smsChat = false,
            onMessage = {},
            onFaceTime = {},
            onOpenAttachment = {},
            modifier = Modifier.padding(vertical = 16.dp),
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun ContactLocationStatesPreview() {
    OpenBubblesTheme {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            val sample = ContactDetails(
                displayName = "Mom",
                avatarPath = null,
                phones = emptyList(),
                emails = listOf("mom@icloud.com"),
                handleAddress = "mom@icloud.com",
            )
            ContactLocationSection(ContactLocationUi.Loading, onOpenMaps = {})
            ContactLocationSection(ContactLocationUi.NotSharing, onOpenMaps = {})
            ContactLocationSection(ContactLocationUi.Unavailable, onOpenMaps = {})
            ContactLocationSection(
                ContactLocationUi.Failed("Couldn't load location"),
                onOpenMaps = {},
            )
            ContactDetailsCard(
                details = sample,
                location = ContactLocationUi.NoFix("Mom"),
                sharedContent = emptyList(),
                conversationTitle = null,
                conversationSubtitle = null,
                smsChat = false,
                onMessage = {},
                onFaceTime = {},
                onOpenAttachment = {},
            )
        }
    }
}

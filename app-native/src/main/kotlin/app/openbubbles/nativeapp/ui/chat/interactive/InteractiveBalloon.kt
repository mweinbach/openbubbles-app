package app.openbubbles.nativeapp.ui.chat.interactive

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.Games
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Payment
import androidx.compose.material.icons.filled.Poll
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import app.openbubbles.core.model.InteractivePayload
import app.openbubbles.core.model.SupportedKind
import kotlin.math.max

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun InteractiveBalloon(
    payload: InteractivePayload,
    onLongPress: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val openAction = when (payload) {
        is InteractivePayload.LiveLocation -> {
            val latitude = payload.latitude
            val longitude = payload.longitude
            if (latitude != null && longitude != null) {
                {
                    val label = payload.label ?: payload.caption ?: "Shared location"
                    val uri = "geo:$latitude,$longitude?q=$latitude,$longitude(${Uri.encode(label)})".toUri()
                    context.startActivity(Intent(Intent.ACTION_VIEW, uri))
                }
            } else {
                payload.url?.asOpenAction(context)
            }
        }
        else -> payload.url?.asOpenAction(context)
    }
    val interaction = when {
        openAction != null || onLongPress != null -> Modifier.combinedClickable(
            onClick = { openAction?.invoke() },
            onLongClick = onLongPress,
        )
        else -> Modifier
    }
    Surface(
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = modifier
            .fillMaxWidth()
            .then(interaction),
    ) {
        when (payload) {
            is InteractivePayload.Poll -> PollCard(payload)
            is InteractivePayload.LiveLocation -> LiveLocationCard(payload, openAction != null)
            is InteractivePayload.Supported -> SupportedCard(payload, openAction != null)
            is InteractivePayload.Unsupported -> UnsupportedCard(payload, openAction != null)
        }
    }
}

@Composable
private fun PollCard(payload: InteractivePayload.Poll) {
    val maxVotes = max(1, payload.options.maxOfOrNull { it.voteCount } ?: 0)
    Column(modifier = Modifier.padding(16.dp)) {
        CardHeader(Icons.Filled.Poll, payload.question, payload.caption ?: "Polls")
        Spacer(Modifier.height(12.dp))
        payload.options.forEachIndexed { index, option ->
            if (index > 0) HorizontalDivider(modifier = Modifier.padding(vertical = 9.dp))
            Text(
                text = option.text,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
            )
            Spacer(Modifier.height(6.dp))
            LinearProgressIndicator(
                progress = { option.voteCount.toFloat() / maxVotes.toFloat() },
                modifier = Modifier.fillMaxWidth(),
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "${option.voteCount} ${if (option.voteCount == 1) "vote" else "votes"}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedButton(onClick = {}, enabled = false) {
                    Text("Vote unavailable")
                }
            }
        }
    }
}

@Composable
private fun LiveLocationCard(payload: InteractivePayload.LiveLocation, canOpen: Boolean) {
    Column {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(132.dp),
            contentAlignment = Alignment.Center,
        ) {
            Surface(
                shape = MaterialTheme.shapes.extraLarge,
                color = MaterialTheme.colorScheme.primaryContainer,
                modifier = Modifier.size(78.dp),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Filled.LocationOn,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.size(40.dp),
                    )
                }
            }
        }
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) {
            Text("Live Location", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            payload.label?.let {
                Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            val coordinates = payload.latitude?.let { latitude ->
                payload.longitude?.let { longitude -> "%.5f, %.5f".format(latitude, longitude) }
            }
            Text(
                text = coordinates ?: payload.caption ?: "Location details are unavailable",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp),
            )
            if (canOpen) OpenHint("Open in maps")
        }
    }
}

@Composable
private fun SupportedCard(payload: InteractivePayload.Supported, canOpen: Boolean) {
    val icon = when (payload.kind) {
        SupportedKind.APPLE_PAY -> Icons.Filled.Payment
        SupportedKind.GAME_PIGEON -> Icons.Filled.Games
        SupportedKind.DIGITAL_TOUCH -> Icons.Filled.TouchApp
        SupportedKind.PASSWORD_SHARE -> Icons.Filled.Lock
        SupportedKind.APP -> Icons.Filled.Apps
    }
    Column(modifier = Modifier.padding(16.dp)) {
        CardHeader(icon, payload.appName, payload.caption ?: supportedSubtitle(payload.kind))
        payload.detail?.takeIf { it.isNotBlank() }?.let { detail ->
            Text(
                text = detail,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 10.dp),
            )
        }
        if (canOpen) OpenHint("Open")
    }
}

@Composable
private fun UnsupportedCard(payload: InteractivePayload.Unsupported, canOpen: Boolean) {
    Column(modifier = Modifier.padding(16.dp)) {
        CardHeader(Icons.Filled.Info, payload.appName, payload.caption)
        Text(
            text = "This iMessage app content is not supported yet.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 10.dp),
        )
        if (canOpen) OpenHint("Open linked content")
    }
}

@Composable
private fun CardHeader(icon: ImageVector, title: String, subtitle: String?) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Surface(
            shape = MaterialTheme.shapes.large,
            color = MaterialTheme.colorScheme.secondaryContainer,
            modifier = Modifier.size(44.dp),
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSecondaryContainer,
                    modifier = Modifier.size(24.dp),
                )
            }
        }
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            subtitle?.takeIf { it.isNotBlank() }?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun OpenHint(text: String) {
    Row(
        modifier = Modifier.padding(top = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = Icons.AutoMirrored.Filled.OpenInNew,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(16.dp),
        )
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(start = 6.dp),
        )
    }
}

private fun supportedSubtitle(kind: SupportedKind): String = when (kind) {
    SupportedKind.APPLE_PAY -> "Payment message"
    SupportedKind.GAME_PIGEON -> "Game message"
    SupportedKind.DIGITAL_TOUCH -> "Digital Touch message"
    SupportedKind.PASSWORD_SHARE -> "Password group invitation"
    SupportedKind.APP -> "iMessage app"
}

private fun String.asOpenAction(context: android.content.Context): (() -> Unit)? {
    val uri = runCatching { toUri() }.getOrNull() ?: return null
    if (uri.scheme.isNullOrBlank() || uri.scheme == "data") return null
    return { context.startActivity(Intent(Intent.ACTION_VIEW, uri)) }
}

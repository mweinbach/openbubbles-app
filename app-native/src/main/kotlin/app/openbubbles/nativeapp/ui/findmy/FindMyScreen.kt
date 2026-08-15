package app.openbubbles.nativeapp.ui.findmy

import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.rounded.Laptop
import androidx.compose.material.icons.rounded.Smartphone
import androidx.compose.material.icons.rounded.Tablet
import androidx.compose.material.icons.rounded.Watch
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.MediumFlexibleTopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import app.openbubbles.nativeapp.ui.common.ChatAvatar
import app.openbubbles.nativeapp.ui.common.rememberContactAvatarPath
import app.openbubbles.nativeapp.ui.theme.OpenBubblesTheme
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.math.abs

/**
 * Find My: devices on this account, followed friends, and beacon items.
 * Rows show battery + location freshness; tapping a located row opens the
 * maps app via a `geo:` Intent. Data survives refresh failures (the last
 * lists stay on screen with a notice).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FindMyScreen(
    uiState: FindMyUiState,
    onRefresh: () -> Unit,
    onBack: () -> Unit,
    showBackButton: Boolean = true,
    modifier: Modifier = Modifier,
) {
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    Scaffold(
        modifier = modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            MediumFlexibleTopAppBar(
                scrollBehavior = scrollBehavior,
                title = { Text("Find My") },
                navigationIcon = {
                    if (showBackButton) {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                        }
                    }
                },
                actions = {
                    if (uiState.refreshing) {
                        CircularProgressIndicator(
                            strokeWidth = 2.5.dp,
                            modifier = Modifier
                                .padding(end = 16.dp)
                                .size(22.dp),
                        )
                    } else {
                        IconButton(onClick = onRefresh) {
                            Icon(Icons.Filled.Refresh, contentDescription = "Refresh")
                        }
                    }
                },
            )
        },
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            when {
                uiState.loading -> LoadingState()
                uiState.unavailable -> EmptyState(
                    title = "Not connected",
                    detail = "Sign in with your Apple ID to see devices, friends and items.",
                )
                uiState.isEmpty -> EmptyState(
                    title = "Nothing here yet",
                    detail = "No devices, friends or items are available for this account.",
                )
                else -> FindMyList(uiState)
            }
        }
    }
}

@Composable
private fun FindMyList(uiState: FindMyUiState) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        if (uiState.refreshErrors.isNotEmpty()) {
            item(key = "errors") {
                Surface(
                    shape = MaterialTheme.shapes.medium,
                    color = MaterialTheme.colorScheme.errorContainer,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        text = "Couldn't refresh — showing last known data\n" +
                            uiState.refreshErrors.joinToString("\n"),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                    )
                }
            }
        }
        section("Devices", uiState.devices.size) {
            uiState.devices.forEach { device ->
                item(key = "device-${device.id}") {
                    DeviceRow(device)
                }
            }
        }
        section("Friends", uiState.friends.size) {
            uiState.friends.forEach { friend ->
                item(key = "friend-${friend.id}") {
                    FriendRow(friend)
                }
            }
        }
        section("Items", uiState.items.size) {
            uiState.items.forEach { item ->
                item(key = "item-${item.id}") {
                    ItemRow(item)
                }
            }
        }
    }
}

/** Section header + one row per entry (kept in one LazyColumn for flat scroll). */
private fun androidx.compose.foundation.lazy.LazyListScope.section(
    title: String,
    count: Int,
    rows: androidx.compose.foundation.lazy.LazyListScope.() -> Unit,
) {
    item(key = "header-$title") {
        Text(
            text = if (count > 0) "$title (${count})".uppercase() else title.uppercase(),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(start = 8.dp, top = 12.dp, bottom = 2.dp),
        )
    }
    if (count > 0) rows()
    else {
        item(key = "empty-$title") {
            Text(
                text = "None",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 8.dp, bottom = 4.dp),
            )
        }
    }
}

// --------------------------------------------------------------------- rows

@Composable
private fun DeviceRow(device: FmDeviceUi) {
    val context = LocalContext.current
    FindMyRow(
        leading = {
            Icon(
                imageVector = deviceIcon(device.model ?: device.name),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primaryContainer)
                    .padding(8.dp),
            )
        },
        name = device.name,
        detail = locationLine(
            location = device.location,
            batteryPercent = device.batteryPercent,
            batteryStatus = device.batteryStatus,
        ),
        clickable = device.location != null,
        onClick = device.location?.let { point -> { openInMaps(context, device.name, point) } },
    )
}

@Composable
private fun FriendRow(friend: FmFriendUi) {
    val context = LocalContext.current
    FindMyRow(
        leading = {
            ChatAvatar(
                title = friend.name,
                avatarColor = avatarColorFor(friend.id),
                size = 40.dp,
                avatarPath = rememberContactAvatarPath(friend.address),
            )
        },
        name = friend.name,
        detail = locationLine(location = friend.location),
        clickable = friend.location != null,
        onClick = friend.location?.let { point -> { openInMaps(context, friend.name, point) } },
    )
}

@Composable
private fun ItemRow(item: FmItemUi) {
    val context = LocalContext.current
    FindMyRow(
        leading = {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.secondaryContainer),
                contentAlignment = Alignment.Center,
            ) {
                if (item.emoji != null) {
                    Text(text = item.emoji)
                } else {
                    Icon(
                        imageVector = Icons.Filled.LocationOn,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSecondaryContainer,
                    )
                }
            }
        },
        name = item.name,
        detail = locationLine(
            location = item.location,
            batteryPercent = item.batteryPercent,
            suffix = item.sharedBy?.let { "shared by $it" },
        ),
        clickable = item.location != null,
        onClick = item.location?.let { point -> { openInMaps(context, item.name, point) } },
    )
}

@Composable
private fun FindMyRow(
    leading: @Composable () -> Unit,
    name: String,
    detail: String?,
    clickable: Boolean,
    onClick: (() -> Unit)?,
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier
            .fillMaxWidth()
            .then(
                if (clickable && onClick != null) {
                    Modifier.clickable(onClick = onClick)
                } else {
                    Modifier
                },
            ),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            leading()
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = name,
                    style = MaterialTheme.typography.bodyLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (detail != null) {
                    Text(
                        text = detail,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            if (clickable) {
                Icon(
                    imageVector = Icons.Filled.LocationOn,
                    contentDescription = "Open in Maps",
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
}

// --------------------------------------------------------------------- states

@Composable
private fun LoadingState() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            CircularProgressIndicator()
            Spacer(modifier = Modifier.padding(8.dp))
            Text(
                text = "Loading last known data…",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun EmptyState(title: String, detail: String) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(horizontal = 32.dp),
        ) {
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                modifier = Modifier.size(64.dp),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Filled.LocationOn,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(28.dp),
                    )
                }
            }
            Spacer(modifier = Modifier.padding(8.dp))
            Text(text = title, style = MaterialTheme.typography.titleMedium)
            Text(
                text = detail,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            )
        }
    }
}

// --------------------------------------------------------------------- helpers

/** Maps app via a `geo:` URI; any failure (no maps app, bad fix) is ignored. */
private fun openInMaps(context: Context, name: String, point: FmPoint) {
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

/** "78% · Charging · 2 min ago · 120 m accuracy" / "No location". */
private fun locationLine(
    location: FmPoint?,
    batteryPercent: Int? = null,
    batteryStatus: String? = null,
    suffix: String? = null,
): String {
    val parts = mutableListOf<String>()
    batteryPercent?.let { parts += "$it%" }
    batteryStatus?.let { parts += it }
    when {
        location == null -> parts += "No location"
        else -> {
            freshness(location.timestampMs)?.let { parts += it }
            accuracy(location.accuracyMeters)?.let { parts += it }
        }
    }
    suffix?.let { parts += it }
    return parts.joinToString(" · ")
}

/** "just now", "7 min ago", "3 h ago", or an absolute short date when stale. */
private fun freshness(
    timestampMs: Long?,
    nowMillis: Long = System.currentTimeMillis(),
): String? {
    if (timestampMs == null || timestampMs <= 0L) return null
    val ageMs = nowMillis - timestampMs
    val minutes = ageMs / 60_000
    val hours = minutes / 60
    return when {
        ageMs < 60_000 -> "just now"
        hours < 1 -> "$minutes min ago"
        hours < 24 -> "$hours h ago"
        else -> DateTimeFormatter.ofPattern("M/d/yy")
            .format(Instant.ofEpochMilli(timestampMs).atZone(ZoneId.systemDefault()))
    }
}

/** "18 m" / "1.2 km" fix radius. */
private fun accuracy(meters: Double?): String? = meters?.takeIf { it > 0 }?.let {
    if (it < 1000) {
        "${kotlin.math.round(it).toInt()} m"
    } else {
        String.format(java.util.Locale.US, "%.1f km", it / 1000)
    }
}

private fun deviceIcon(model: String): ImageVector = when {
    model.contains("book", ignoreCase = true) ||
        model.contains("mac", ignoreCase = true) -> Icons.Rounded.Laptop
    model.contains("watch", ignoreCase = true) -> Icons.Rounded.Watch
    model.contains("ipad", ignoreCase = true) ||
        model.contains("tablet", ignoreCase = true) -> Icons.Rounded.Tablet
    else -> Icons.Rounded.Smartphone
}

/** Stable avatar color for friends (same palette idea as the chat list). */
private fun avatarColorFor(seed: String): Long {
    val palette = longArrayOf(
        0xFF7C4FDF, 0xFF4C8BF5, 0xFF00897B, 0xFFD81B60, 0xFFF4511E,
        0xFF6D4C41, 0xFF3949AB, 0xFF43A047,
    )
    return palette[abs(seed.hashCode()) % palette.size]
}

// --------------------------------------------------------------------- previews

private fun fakeState(failRefresh: Boolean = false): FindMyUiState {
    val port = FakeFindMyPort(failRefresh)
    return FindMyUiState(
        loading = false,
        devices = kotlinx.coroutines.runBlocking { port.devices() },
        friends = kotlinx.coroutines.runBlocking { port.friends() },
        items = kotlinx.coroutines.runBlocking { port.items() },
    )
}

@Preview(showBackground = true)
@Preview(showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun FindMyScreenPreview() {
    OpenBubblesTheme {
        FindMyScreen(uiState = fakeState(), onRefresh = {}, onBack = {})
    }
}

@Preview(showBackground = true)
@Composable
private fun FindMyScreenOfflinePreview() {
    OpenBubblesTheme {
        FindMyScreen(
            uiState = fakeState(failRefresh = true)
                .copy(refreshErrors = listOf("Devices: offline"), refreshing = true),
            onRefresh = {},
            onBack = {},
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun FindMyScreenEmptyPreview() {
    OpenBubblesTheme {
        FindMyScreen(
            uiState = FindMyUiState(loading = false, unavailable = true),
            onRefresh = {},
            onBack = {},
        )
    }
}

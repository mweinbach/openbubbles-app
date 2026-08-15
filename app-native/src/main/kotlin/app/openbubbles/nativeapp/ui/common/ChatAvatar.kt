package app.openbubbles.nativeapp.ui.common

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.openbubbles.nativeapp.data.UiContacts
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Two-letter initials for an avatar, e.g. "Alex Chen" -> "AC", "Family" -> "FA". */
fun initialsFor(title: String): String {
    val words = title.trim().split(Regex("\\s+")).filter { it.isNotBlank() }
    return when {
        words.isEmpty() -> "?"
        words.size == 1 -> words[0].take(2)
        else -> "${words[0].first()}${words[1].first()}"
    }.uppercase()
}

/**
 * Resolves a contact photo URI for a handle address through [UiContacts].
 * Returns null when no resolver is set, the address is unknown, or the
 * contact has no photo — callers keep the initials fallback.
 */
@Composable
fun rememberContactAvatarPath(address: String?): String? =
    produceState<String?>(initialValue = null, address) {
        if (address == null) return@produceState
        value = withContext(Dispatchers.IO) {
            runCatching { UiContacts.contactNames?.invoke(address)?.second }.getOrNull()
        }
    }.value

/**
 * Colored avatar circle with initials, used by the chat list, the chat
 * header and chat info. When [avatarPath] resolves to a decodable image
 * (a contact photo URI), the photo is shown instead of the initials.
 */
@Composable
fun ChatAvatar(
    title: String,
    avatarColor: Long,
    size: Dp = 48.dp,
    modifier: Modifier = Modifier,
    avatarPath: String? = null,
) {
    val decoded = rememberDecodedUriImage(
        uri = avatarPath,
        maxDimensionPx = (size.value.toInt() * 2).coerceAtLeast(64),
    )
    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(Color(avatarColor)),
        contentAlignment = Alignment.Center,
    ) {
        val image = decoded?.image
        if (image != null) {
            Image(
                bitmap = image,
                contentDescription = title,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            Text(
                text = initialsFor(title),
                color = Color.White,
                fontSize = (size.value * 0.36f).sp,
                lineHeight = (size.value * 0.36f).sp,
                style = MaterialTheme.typography.labelLarge,
            )
        }
    }
}

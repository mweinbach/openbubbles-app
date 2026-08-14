package app.openbubbles.nativeapp.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Two-letter initials for an avatar, e.g. "Alex Chen" -> "AC", "Family" -> "FA". */
fun initialsFor(title: String): String {
    val words = title.trim().split(Regex("\\s+")).filter { it.isNotBlank() }
    return when {
        words.isEmpty() -> "?"
        words.size == 1 -> words[0].take(2)
        else -> "${words[0].first()}${words[1].first()}"
    }.uppercase()
}

/** Colored avatar circle with initials, used by the chat list and the chat header. */
@Composable
fun ChatAvatar(
    title: String,
    avatarColor: Long,
    size: Dp = 48.dp,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(Color(avatarColor)),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = initialsFor(title),
            color = Color.White,
            fontSize = (size.value * 0.36f).sp,
            lineHeight = (size.value * 0.36f).sp,
            style = MaterialTheme.typography.labelLarge,
        )
    }
}

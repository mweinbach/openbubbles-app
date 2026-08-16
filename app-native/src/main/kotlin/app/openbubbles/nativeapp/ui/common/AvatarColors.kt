package app.openbubbles.nativeapp.ui.common

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import kotlin.math.abs

/**
 * The one avatar palette + seed convention for the whole app. The same person
 * or conversation must render the same color in the picker, the chat list,
 * the chat header, chat info, and Find My — previously four divergent copies
 * of this palette produced different colors per screen.
 *
 * Seed with the most stable identity available: a contact/participant address
 * when known, otherwise the chat guid or friend id.
 *
 * Every entry passes WCAG AA against its [avatarContentColor] result (worst
 * case 4.6:1). The previous teal (0xFF00897B) sat in the luminance dead zone
 * where neither white nor dark text reaches 4.5:1, hence the deeper teal.
 */
private val AvatarPalette = longArrayOf(
    0xFF7C4FDF, 0xFF4C8BF5, 0xFF00796B, 0xFFD81B60, 0xFFF4511E,
    0xFF6D4C41, 0xFF3949AB, 0xFF43A047, 0xFF8D6E63, 0xFFC0CA33,
)

private val AvatarDarkContent = Color(0xFF1C1C1E)

fun avatarColorFor(seed: String): Long =
    AvatarPalette[abs(seed.hashCode()) % AvatarPalette.size]

/** Initials color chosen by whichever of white/dark clears the seed better. */
fun avatarContentColor(background: Color): Color {
    val luminance = background.luminance()
    val whiteContrast = 1.05f / (luminance + 0.05f)
    val darkContrast = (luminance + 0.05f) / (AvatarDarkContent.luminance() + 0.05f)
    return if (whiteContrast >= darkContrast) Color.White else AvatarDarkContent
}

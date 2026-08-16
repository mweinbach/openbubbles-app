package app.openbubbles.nativeapp.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/**
 * Apple service-identity colors (iMessage blue / SMS green), held outside the
 * colorScheme on purpose: they are brand colors, so dynamic color must never
 * re-hue them, and they double as the SMS bubble identity in the transcript.
 *
 * The container hues are Apple's exact hexes. The content color is dark, not
 * white, because white fails WCAG on all four hues (2.0-4.0:1 measured);
 * near-black text measures 5.2:1 on the blues and 9.5-10.4:1 on the greens.
 */
object ServiceColors {
    val IMessageBlueLight = Color(0xFF007AFF)
    val IMessageBlueDark = Color(0xFF0A84FF)
    val SmsGreenLight = Color(0xFF34C759)
    val SmsGreenDark = Color(0xFF30D158)

    /** Readable on all four service hues (worst case 5.2:1 vs white's 2.0:1). */
    val OnService = Color.Black
}

/** Container/content pair for one messaging service surface. */
data class ServiceColorPair(val container: Color, val content: Color)

@Composable
fun iMessageServiceColors(): ServiceColorPair = ServiceColorPair(
    container = if (isSystemInDarkTheme()) ServiceColors.IMessageBlueDark else ServiceColors.IMessageBlueLight,
    content = ServiceColors.OnService,
)

@Composable
fun smsServiceColors(): ServiceColorPair = ServiceColorPair(
    container = if (isSystemInDarkTheme()) ServiceColors.SmsGreenDark else ServiceColors.SmsGreenLight,
    content = ServiceColors.OnService,
)

@Composable
fun serviceColors(isSms: Boolean): ServiceColorPair =
    if (isSms) smsServiceColors() else iMessageServiceColors()

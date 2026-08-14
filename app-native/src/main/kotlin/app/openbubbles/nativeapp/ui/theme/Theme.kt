package app.openbubbles.nativeapp.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

private val LightColors = lightColorScheme(
    primary = Color(0xFF0061D0),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFD6E4FF),
    onPrimaryContainer = Color(0xFF0B2F5E),
    secondary = Color(0xFF555F71),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFDAE2F9),
    onSecondaryContainer = Color(0xFF121C2B),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFFA8C8FF),
    onPrimary = Color(0xFF00305F),
    primaryContainer = Color(0xFF0A5BC4),
    onPrimaryContainer = Color(0xFFD6E4FF),
    secondary = Color(0xFFBEC6DC),
    onSecondary = Color(0xFF293041),
    secondaryContainer = Color(0xFF3F4759),
    onSecondaryContainer = Color(0xFFDAE2F9),
)

/**
 * App theme: dynamic color on Android 12+, blue-seeded Material 3 palettes
 * below that, light/dark driven by the system setting.
 */
@Composable
fun OpenBubblesTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit,
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColors
        else -> LightColors
    }
    MaterialTheme(colorScheme = colorScheme, content = content)
}

package app.openbubbles.nativeapp.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.openbubbles.nativeapp.data.AppearancePrefs
import app.openbubbles.nativeapp.data.MessagingPrefs

/**
 * Blue-seeded Material 3 palettes (iMessage-flavored): a confident blue
 * primary, cool neutral surfaces, and a full set of surface-container roles
 * so screens can build proper hierarchy without inventing colors.
 */
private val LightColors = lightColorScheme(
    primary = Color(0xFF0061D5),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFD6E3FF),
    onPrimaryContainer = Color(0xFF0B2F60),
    secondary = Color(0xFF555F71),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFDAE2F9),
    onSecondaryContainer = Color(0xFF121C2B),
    tertiary = Color(0xFF006874),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFF9CF1FF),
    onTertiaryContainer = Color(0xFF001F24),
    error = Color(0xFFBA1A1A),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002),
    background = Color(0xFFF9F9FD),
    onBackground = Color(0xFF191C20),
    surface = Color(0xFFF9F9FD),
    onSurface = Color(0xFF191C20),
    surfaceVariant = Color(0xFFE0E2EC),
    onSurfaceVariant = Color(0xFF44474E),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFF3F3F9),
    surfaceContainer = Color(0xFFEDEDF4),
    surfaceContainerHigh = Color(0xFFE7E8EE),
    surfaceContainerHighest = Color(0xFFE1E2E9),
    outline = Color(0xFF74757F),
    outlineVariant = Color(0xFFC4C6D0),
    inverseSurface = Color(0xFF2E3036),
    inverseOnSurface = Color(0xFFF1F0F7),
    inversePrimary = Color(0xFFA8C8FF),
    surfaceTint = Color(0xFF0061D5),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFFA8C8FF),
    onPrimary = Color(0xFF00315E),
    primaryContainer = Color(0xFF0B4A91),
    onPrimaryContainer = Color(0xFFD6E3FF),
    secondary = Color(0xFFBFC6DC),
    onSecondary = Color(0xFF283041),
    secondaryContainer = Color(0xFF3E4759),
    onSecondaryContainer = Color(0xFFDAE2F9),
    tertiary = Color(0xFF80D4E4),
    onTertiary = Color(0xFF00363D),
    tertiaryContainer = Color(0xFF004F58),
    onTertiaryContainer = Color(0xFF9CF1FF),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
    background = Color(0xFF111318),
    onBackground = Color(0xFFE2E2E9),
    surface = Color(0xFF111318),
    onSurface = Color(0xFFE2E2E9),
    surfaceVariant = Color(0xFF44474E),
    onSurfaceVariant = Color(0xFFC4C6D0),
    surfaceContainerLowest = Color(0xFF0C0E13),
    surfaceContainerLow = Color(0xFF191C21),
    surfaceContainer = Color(0xFF1D2025),
    surfaceContainerHigh = Color(0xFF282A2F),
    surfaceContainerHighest = Color(0xFF33353A),
    outline = Color(0xFF8E9099),
    outlineVariant = Color(0xFF44474E),
    inverseSurface = Color(0xFFE2E2E9),
    inverseOnSurface = Color(0xFF2E3036),
    inversePrimary = Color(0xFF0061D5),
    surfaceTint = Color(0xFFA8C8FF),
)

/**
 * The Material 3 Expressive corner-radius scale, at the canonical token values.
 *
 * This previously ran one step high across the bottom half of the scale
 * (extraSmall 8, small 12, medium 16, large 20), which made every themed
 * surface rounder than Material intends and flattened the shape-contrast lever:
 * when everything is soft, a soft shape cannot signal anything. The three
 * larger tokens — largeIncreased, extraLargeIncreased and extraExtraLarge — are
 * the Expressive additions and were already correct.
 *
 * Message bubbles deliberately override this locally (see MessageBubble), where
 * corner radius encodes author grouping rather than surface hierarchy.
 */
private val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(4.dp),
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(12.dp),
    large = RoundedCornerShape(16.dp),
    largeIncreased = RoundedCornerShape(20.dp),
    extraLarge = RoundedCornerShape(28.dp),
    extraLargeIncreased = RoundedCornerShape(32.dp),
    extraExtraLarge = RoundedCornerShape(48.dp),
)

/**
 * App theme: dynamic color on Android 12+ (unless the user disabled it in
 * Settings → Appearance), blue-seeded Material 3 palettes otherwise,
 * light/dark driven by the user's theme mode (the system setting by default).
 *
 * @param darkTheme the system dark setting; the user's theme-mode preference
 *   resolves against it (SYSTEM follows it, LIGHT/DARK override it).
 * @param dynamicColor overrides the user's dynamic-color preference; tests and
 *   screenshot fixtures should pass an explicit value for determinism.
 */
@Composable
fun OpenBubblesTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean? = null,
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    // Idempotent SharedPreferences bind; SideEffect (not remember) because
    // init returns Unit and must run only after a successful composition.
    SideEffect {
        AppearancePrefs.init(context)
        MessagingPrefs.hydrate(context)
    }
    val dynamicColorPref by AppearancePrefs.dynamicColorFlow.collectAsStateWithLifecycle()
    val useDynamicColor = dynamicColor ?: dynamicColorPref
    val themeMode by AppearancePrefs.themeModeFlow.collectAsStateWithLifecycle()
    val useDarkTheme = themeMode.resolvesToDark(systemDark = darkTheme)
    val colorScheme = remember(useDarkTheme, useDynamicColor, context) {
        when {
            useDynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
                if (useDarkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
            }
            useDarkTheme -> DarkColors
            else -> LightColors
        }
    }
    val reduceMotion = rememberReduceMotion()
    CompositionLocalProvider(LocalReduceMotion provides reduceMotion) {
        MaterialExpressiveTheme(
            colorScheme = colorScheme,
            shapes = AppShapes,
            motionScheme = appMotionScheme(),
            content = content,
        )
    }
}

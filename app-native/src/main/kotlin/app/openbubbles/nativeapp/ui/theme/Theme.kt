package app.openbubbles.nativeapp.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.openbubbles.nativeapp.data.AppearancePrefs

/**
 * Blue-seeded Material 3 palettes (iMessage-flavored): a confident blue
 * primary, cool neutral surfaces, and a full set of surface-container roles
 * so screens can build proper hierarchy without inventing colors.
 */
private val LightColors = lightColorScheme(
    primary = Color(0xFF0069E8),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFDBE8FF),
    onPrimaryContainer = Color(0xFF12345E),
    secondary = Color(0xFF525F73),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFE5EAF3),
    onSecondaryContainer = Color(0xFF202A3B),
    tertiary = Color(0xFF39656E),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFD2EDF2),
    onTertiaryContainer = Color(0xFF123D46),
    error = Color(0xFFBA1A1A),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002),
    background = Color(0xFFF8FAFE),
    onBackground = Color(0xFF1A1D22),
    surface = Color(0xFFF8FAFE),
    onSurface = Color(0xFF1A1D22),
    surfaceVariant = Color(0xFFE4E8F0),
    onSurfaceVariant = Color(0xFF4A505C),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFF2F5FA),
    surfaceContainer = Color(0xFFECF0F6),
    surfaceContainerHigh = Color(0xFFE6EAF1),
    surfaceContainerHighest = Color(0xFFE0E5ED),
    outline = Color(0xFF737985),
    outlineVariant = Color(0xFFC4CAD5),
    inverseSurface = Color(0xFF2E3239),
    inverseOnSurface = Color(0xFFF0F2F7),
    inversePrimary = Color(0xFFA9C9FF),
    surfaceTint = Color(0xFF0069E8),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFFA9C9FF),
    onPrimary = Color(0xFF062F60),
    primaryContainer = Color(0xFF234A79),
    onPrimaryContainer = Color(0xFFDCE8FF),
    secondary = Color(0xFFC0C8D8),
    onSecondary = Color(0xFF263140),
    secondaryContainer = Color(0xFF343B47),
    onSecondaryContainer = Color(0xFFE0E5EF),
    tertiary = Color(0xFFA7CDD3),
    onTertiary = Color(0xFF17363D),
    tertiaryContainer = Color(0xFF23464D),
    onTertiaryContainer = Color(0xFFC9ECF1),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
    background = Color(0xFF111318),
    onBackground = Color(0xFFE2E5EA),
    surface = Color(0xFF111318),
    onSurface = Color(0xFFE2E5EA),
    surfaceVariant = Color(0xFF414750),
    onSurfaceVariant = Color(0xFFC3C8D2),
    surfaceContainerLowest = Color(0xFF0C0E12),
    surfaceContainerLow = Color(0xFF171B21),
    surfaceContainer = Color(0xFF1C2027),
    surfaceContainerHigh = Color(0xFF272B32),
    surfaceContainerHighest = Color(0xFF32363E),
    outline = Color(0xFF8D949F),
    outlineVariant = Color(0xFF414750),
    inverseSurface = Color(0xFFE2E5EA),
    inverseOnSurface = Color(0xFF2E3239),
    inversePrimary = Color(0xFF0069E8),
    surfaceTint = Color(0xFFA9C9FF),
)

/**
 * Keep Material's complete, font-scale-aware type ramp while giving headings a
 * quieter, more deliberate hierarchy. The font-family constructor propagates
 * Android's system sans-serif to all 30 baseline and emphasized styles; copied
 * tokens retain Material's accessible sizes and line heights.
 */
private val SystemTypography = Typography(fontFamily = FontFamily.SansSerif)

private val AppTypography = Typography(
    fontFamily = FontFamily.SansSerif,
    displayLarge = SystemTypography.displayLarge.copy(
        fontWeight = FontWeight.Medium,
        letterSpacing = (-0.45).sp,
    ),
    displayMedium = SystemTypography.displayMedium.copy(
        fontWeight = FontWeight.Medium,
        letterSpacing = (-0.35).sp,
    ),
    displaySmall = SystemTypography.displaySmall.copy(
        fontWeight = FontWeight.Medium,
        letterSpacing = (-0.25).sp,
    ),
    headlineLarge = SystemTypography.headlineLarge.copy(
        fontWeight = FontWeight.Medium,
        letterSpacing = (-0.20).sp,
    ),
    headlineMedium = SystemTypography.headlineMedium.copy(
        fontWeight = FontWeight.Medium,
        letterSpacing = (-0.15).sp,
    ),
    headlineSmall = SystemTypography.headlineSmall.copy(
        fontWeight = FontWeight.Medium,
        letterSpacing = (-0.10).sp,
    ),
    titleLarge = SystemTypography.titleLarge.copy(
        fontWeight = FontWeight.Medium,
        letterSpacing = (-0.10).sp,
    ),
    titleMedium = SystemTypography.titleMedium.copy(
        fontWeight = FontWeight.Medium,
        letterSpacing = (-0.05).sp,
    ),
    titleSmall = SystemTypography.titleSmall.copy(fontWeight = FontWeight.Medium),
    displayLargeEmphasized = SystemTypography.displayLargeEmphasized.copy(
        fontWeight = FontWeight.SemiBold,
        letterSpacing = (-0.45).sp,
    ),
    displayMediumEmphasized = SystemTypography.displayMediumEmphasized.copy(
        fontWeight = FontWeight.SemiBold,
        letterSpacing = (-0.35).sp,
    ),
    displaySmallEmphasized = SystemTypography.displaySmallEmphasized.copy(
        fontWeight = FontWeight.SemiBold,
        letterSpacing = (-0.25).sp,
    ),
    headlineLargeEmphasized = SystemTypography.headlineLargeEmphasized.copy(
        fontWeight = FontWeight.SemiBold,
        letterSpacing = (-0.20).sp,
    ),
    headlineMediumEmphasized = SystemTypography.headlineMediumEmphasized.copy(
        fontWeight = FontWeight.SemiBold,
        letterSpacing = (-0.15).sp,
    ),
    headlineSmallEmphasized = SystemTypography.headlineSmallEmphasized.copy(
        fontWeight = FontWeight.SemiBold,
        letterSpacing = (-0.10).sp,
    ),
    titleLargeEmphasized = SystemTypography.titleLargeEmphasized.copy(
        fontWeight = FontWeight.SemiBold,
        letterSpacing = (-0.10).sp,
    ),
    titleMediumEmphasized = SystemTypography.titleMediumEmphasized.copy(
        fontWeight = FontWeight.SemiBold,
        letterSpacing = (-0.05).sp,
    ),
    titleSmallEmphasized = SystemTypography.titleSmallEmphasized.copy(
        fontWeight = FontWeight.SemiBold,
    ),
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
    SideEffect { AppearancePrefs.init(context) }
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
            typography = AppTypography,
            motionScheme = appMotionScheme(),
            content = content,
        )
    }
}

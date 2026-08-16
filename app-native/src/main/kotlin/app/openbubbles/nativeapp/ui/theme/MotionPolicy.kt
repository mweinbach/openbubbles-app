package app.openbubbles.nativeapp.ui.theme

import android.animation.ValueAnimator
import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.snap
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.IntOffset

/**
 * True when the user asked the OS to remove animations (Settings →
 * Accessibility → Remove animations, or a zeroed animator scale). Compose
 * does not follow that setting by itself — every non-essential animation
 * must check this flag.
 */
val LocalReduceMotion = compositionLocalOf { false }

/** Observes the platform animation scales so the flag reacts live. */
@Composable
fun rememberReduceMotion(): Boolean {
    val context = LocalContext.current

    fun read(): Boolean {
        if (!ValueAnimator.areAnimatorsEnabled()) return true
        val resolver = context.contentResolver
        val animator = Settings.Global.getFloat(resolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f)
        val transition = Settings.Global.getFloat(resolver, Settings.Global.TRANSITION_ANIMATION_SCALE, 1f)
        val window = Settings.Global.getFloat(resolver, Settings.Global.WINDOW_ANIMATION_SCALE, 1f)
        return animator == 0f || transition == 0f || window == 0f
    }

    var reduce by remember { mutableStateOf(read()) }
    DisposableEffect(context) {
        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) {
                reduce = read()
            }
        }
        context.contentResolver.registerContentObserver(Settings.Global.CONTENT_URI, true, observer)
        onDispose { context.contentResolver.unregisterContentObserver(observer) }
    }
    return reduce
}

/** The scheme the theme should use: expressive normally, standard when reduced. */
@Composable
fun appMotionScheme(): MotionScheme =
    if (LocalReduceMotion.current) MotionScheme.standard() else MotionScheme.expressive()

// Spec helpers: the theme's motion scheme when motion is allowed, snap() when
// the user removed animations. State changes stay legible through instant
// cuts; nothing springs.

@Composable
fun <T> defaultSpatialSpec(): FiniteAnimationSpec<T> =
    if (LocalReduceMotion.current) snap() else MaterialTheme.motionScheme.defaultSpatialSpec()

@Composable
fun <T> fastSpatialSpec(): FiniteAnimationSpec<T> =
    if (LocalReduceMotion.current) snap() else MaterialTheme.motionScheme.fastSpatialSpec()

@Composable
fun <T> slowSpatialSpec(): FiniteAnimationSpec<T> =
    if (LocalReduceMotion.current) snap() else MaterialTheme.motionScheme.slowSpatialSpec()

@Composable
fun <T> defaultEffectsSpec(): FiniteAnimationSpec<T> =
    if (LocalReduceMotion.current) snap() else MaterialTheme.motionScheme.defaultEffectsSpec()

@Composable
fun <T> fastEffectsSpec(): FiniteAnimationSpec<T> =
    if (LocalReduceMotion.current) snap() else MaterialTheme.motionScheme.fastEffectsSpec()

@Composable
fun <T> slowEffectsSpec(): FiniteAnimationSpec<T> =
    if (LocalReduceMotion.current) snap() else MaterialTheme.motionScheme.slowEffectsSpec()

/** The three specs `LazyItemScope.animateItem` needs, themed + reduce-aware. */
data class ItemAnimationSpecs(
    val fadeIn: FiniteAnimationSpec<Float>,
    val fadeOut: FiniteAnimationSpec<Float>,
    val placement: FiniteAnimationSpec<IntOffset>,
)

@Composable
fun rememberItemAnimationSpecs(): ItemAnimationSpecs {
    val scheme = MaterialTheme.motionScheme
    val reduce = LocalReduceMotion.current
    return remember(scheme, reduce) {
        if (reduce) {
            ItemAnimationSpecs(snap(), snap(), snap())
        } else {
            ItemAnimationSpecs(
                fadeIn = scheme.defaultEffectsSpec(),
                fadeOut = scheme.fastEffectsSpec(),
                placement = scheme.defaultSpatialSpec(),
            )
        }
    }
}

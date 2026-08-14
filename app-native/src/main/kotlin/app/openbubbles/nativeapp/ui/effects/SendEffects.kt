package app.openbubbles.nativeapp.ui.effects

import android.content.res.Configuration
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.tooling.preview.Preview
import app.openbubbles.nativeapp.ui.theme.OpenBubblesTheme
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

/*
 * iMessage send screen effects, ported from the Dart custom painters in
 * lib/app/animations/ (balloon/fireworks/laser/spotlight/love/celebration
 * *_classes.dart + *_rendering.dart). Each effect is a fixed-timestep (60Hz)
 * simulation driven by a Compose frame loop (`withFrameNanos`) and drawn on a
 * full-screen Canvas; every effect self-terminates after ~4-6s.
 *
 * Effect id mapping (mirrors `effectMap` in lib/helpers/types/constants.dart):
 *   com.apple.messages.effect.CKHappyBirthdayEffect -> Balloons
 *   com.apple.messages.effect.CKConfettiEffect      -> Confetti
 *   com.apple.messages.effect.CKHeartEffect         -> Love
 *   com.apple.messages.effect.CKLasersEffect        -> Lasers
 *   com.apple.messages.effect.CKFireworksEffect     -> Fireworks
 *   com.apple.messages.effect.CKSparklesEffect      -> Celebration
 *   com.apple.messages.effect.CKSpotlightEffect     -> Spotlight
 *   com.apple.MobileSMS.expressivesend.*            -> bubble effects; only
 *     invisibleink is handled (blur-reveal bubble in MessageBubble), the rest
 *     and any unknown id are a no-op here.
 */

/** Apple ids for bubble effects handled outside the overlay. */
const val INVISIBLE_INK_EFFECT_ID = "com.apple.MobileSMS.expressivesend.invisibleink"

/** True when the style id is the invisible-ink bubble effect. */
fun isInvisibleInk(styleId: String?): Boolean =
    styleId?.contains("invisibleink", ignoreCase = true) == true

/**
 * The screen effects the overlay can render. [label]/[icon] feed the picker;
 * [darkBackdrop] mirrors the Dart `ScreenEffectsWidget` AnimatedContainer that
 * blacks out the stage for light-on-dark effects.
 */
enum class SendScreenEffect(
    val effectId: String,
    val label: String,
    val icon: String,
    val darkBackdrop: Boolean = false,
) {
    BALLOONS("com.apple.messages.effect.CKHappyBirthdayEffect", "Balloons", "🎈"),
    CONFETTI("com.apple.messages.effect.CKConfettiEffect", "Confetti", "🎊"),
    LOVE("com.apple.messages.effect.CKHeartEffect", "Love", "❤️"),
    LASERS("com.apple.messages.effect.CKLasersEffect", "Lasers", "⚡", darkBackdrop = true),
    FIREWORKS("com.apple.messages.effect.CKFireworksEffect", "Fireworks", "🎆", darkBackdrop = true),
    CELEBRATION("com.apple.messages.effect.CKSparklesEffect", "Celebration", "✨", darkBackdrop = true),
    SPOTLIGHT("com.apple.messages.effect.CKSpotlightEffect", "Spotlight", "🔦", darkBackdrop = true),
    ;

    /** Unknown ids (echo, slam, loud, gentle, invisibleink, garbage) -> null. */
    internal fun newSimulator(origin: Offset?, random: Random = Random.Default): EffectSimulator = when (this) {
        BALLOONS -> BalloonsSimulator(random)
        CONFETTI -> ConfettiSimulator(random)
        LOVE -> LoveSimulator(origin, random)
        LASERS -> LaserSimulator(origin, random)
        FIREWORKS -> FireworksSimulator(random)
        CELEBRATION -> CelebrationSimulator(random)
        SPOTLIGHT -> SpotlightSimulator(origin, random)
    }

    companion object {
        fun fromId(id: String?): SendScreenEffect? =
            id?.let { raw -> entries.firstOrNull { it.effectId.equals(raw, ignoreCase = true) } }
    }
}

/** One selectable entry in the effect picker (screen effects + invisible ink). */
data class SendEffectOption(val id: String, val label: String, val icon: String)

/** Picker catalog; ids are the exact Apple expressive-send strings. */
object SendEffectCatalog {
    val invisibleInk = SendEffectOption(INVISIBLE_INK_EFFECT_ID, "Invisible Ink", "◍")
    val options: List<SendEffectOption> =
        SendScreenEffect.entries.map { SendEffectOption(it.effectId, it.label, it.icon) } + invisibleInk

    fun byId(id: String?): SendEffectOption? = options.firstOrNull { it.id == id }
}

/**
 * Full-screen send-effect overlay. Renders the effect for [effectId] on a
 * Canvas until it self-terminates (~4-6s), then invokes [onFinished]; unknown
 * and bubble-effect ids render nothing. [origin] optionally anchors the
 * lasers/spotlight/love effects to the sent bubble (px); null defaults to the
 * bottom-center of the stage where the newest bubble sits.
 */
@Composable
fun SendEffectOverlay(
    effectId: String?,
    modifier: Modifier = Modifier,
    origin: Offset? = null,
    onFinished: () -> Unit = {},
) {
    val effect = remember(effectId) { SendScreenEffect.fromId(effectId) }
    if (effect == null) {
        // Unknown / bubble-only ids are a no-op overlay.
        if (effectId != null) LaunchedEffect(effectId) { onFinished() }
        return
    }
    val simulator = remember(effect) { mutableStateOf<EffectSimulator?>(null) }
    val frame = remember(effect) { mutableIntStateOf(0) }

    LaunchedEffect(effect) {
        val sim = effect.newSimulator(origin)
        simulator.value = sim
        var lastFrameNanos = -1L
        while (true) {
            val now = withFrameNanos { it }
            if (lastFrameNanos > 0L && sim.width > 0f && sim.height > 0f) {
                sim.advance((now - lastFrameNanos) / 1_000_000_000f)
            }
            lastFrameNanos = now
            frame.intValue++
            if (sim.isDone()) break
        }
        frame.intValue++
        onFinished()
    }

    Box(modifier = modifier) {
        if (effect.darkBackdrop) {
            Box(Modifier.fillMaxSize().background(Color.Black))
        }
        Canvas(modifier = Modifier.fillMaxSize()) {
            // Snapshot reads: `frame` re-invalidates the canvas every tick.
            frame.intValue
            val sim = simulator.value ?: return@Canvas
            if (sim.width <= 0f && size.width > 0f) {
                sim.width = size.width
                sim.height = size.height
            }
            sim.draw(this)
        }
    }
}

// ---------------------------------------------------------------------------
// Simulation plumbing
// ---------------------------------------------------------------------------

private const val STEP_SECONDS = 1f / 60f // Dart tickers parity (60Hz steps)
private val TAU = (2.0 * PI).toFloat()

/**
 * One running effect. [advance] accumulates real frame deltas into fixed 60Hz
 * steps so the per-frame physics ported from the Dart tickers stays stable at
 * any display refresh rate.
 */
internal abstract class EffectSimulator(
    protected val random: Random,
    private val hardStopSeconds: Float,
) {
    var width = 0f
    var height = 0f
    var time = 0f
        protected set

    fun advance(dtSeconds: Float) {
        var remaining = dtSeconds.coerceIn(0f, 0.25f)
        while (remaining >= STEP_SECONDS) {
            step()
            time += STEP_SECONDS
            remaining -= STEP_SECONDS
        }
    }

    protected abstract fun step()

    abstract fun draw(scope: DrawScope)

    /** Done = effect drained, or the hard stop elapsed (always ~4-6s). */
    open fun isDone(): Boolean = time >= hardStopSeconds
}

/** HSV like Dart's HSVColor.toColor(). */
private fun hsvColor(alpha: Float, hue: Float, saturation: Float, value: Float): Color {
    val h = (((hue % 360f) + 360f) % 360f) / 60f
    val sector = h.toInt() % 6
    val f = h - h.toInt()
    val s = saturation.coerceIn(0f, 1f)
    val v = value.coerceIn(0f, 1f)
    val p = v * (1f - s)
    val q = v * (1f - f * s)
    val t = v * (1f - (1f - f) * s)
    val (r, g, b) = when (sector) {
        0 -> Triple(v, t, p)
        1 -> Triple(q, v, p)
        2 -> Triple(p, v, t)
        3 -> Triple(p, q, v)
        4 -> Triple(t, p, v)
        else -> Triple(v, p, q)
    }
    return Color(r, g, b, alpha.coerceIn(0f, 1f))
}

/** Approximation of the Dart HSL darkenAmount used by the balloon/heart fills. */
private fun Color.darkened(amount: Float): Color =
    Color(red * (1f - amount), green * (1f - amount), blue * (1f - amount), alpha)

/** Approximation of the Dart HSL lightenAmount. */
private fun Color.lightened(amount: Float): Color {
    val t = amount.coerceIn(0f, 1f)
    return Color(red + (1f - red) * t, green + (1f - green) * t, blue + (1f - blue) * t, alpha)
}

// ---------------------------------------------------------------------------
// Balloons (port of balloon_classes.dart + balloon_rendering.dart)
// ---------------------------------------------------------------------------

private val KAPPA = (4f * (sqrt(2f) - 1f)) / 3f
private const val BALLOON_WIDTH_FACTOR = 0.0333f
private const val BALLOON_HEIGHT_FACTOR = 0.4f
private const val TIE_WIDTH_FACTOR = 0.12f
private const val TIE_HEIGHT_FACTOR = 0.10f
private const val TIE_CURVE_FACTOR = 0.13f

private val BALLOON_COLORS = listOf(
    Color(0xFFF44336), Color(0xFFE91E63), Color(0xFF9C27B0), Color(0xFF2196F3),
    Color(0xFF03A9F4), Color(0xFF4CAF50), Color(0xFF8BC34A), Color(0xFFFF9800),
    Color(0xFFFFEB3B),
)

internal class BalloonsSimulator(random: Random) : EffectSimulator(random, hardStopSeconds = 6f) {

    internal class Balloon(
        var x: Float,
        var y: Float,
        val color: Color,
        val radius: Float,
        val angle: Float,
        val swayPhase: Float,
    ) {
        private val velocity = 8f

        fun step() {
            x -= velocity * cos(angle)
            y -= velocity * sin(angle)
        }
    }

    private val balloons = ArrayList<Balloon>()
    private var lastLaunch = -1f

    override fun step() {
        // Dart: launch every 100ms until the host stops spawning (~1s in).
        if (time - lastLaunch >= 0.1f && time < 1.2f) {
            lastLaunch = time
            balloons += Balloon(
                x = width,
                y = height + 100f,
                color = BALLOON_COLORS[random.nextInt(BALLOON_COLORS.size)],
                radius = (random.nextFloat() * 100f).coerceIn(40f, 100f),
                angle = (PI / 2.0 - random.nextDouble() * PI / 6.0).toFloat(),
                swayPhase = random.nextFloat() * TAU,
            )
        }
        for (balloon in balloons) balloon.step()
        balloons.removeAll { it.y < -100f || it.x < -100f }
    }

    override fun isDone(): Boolean = super.isDone() || (time > 2f && balloons.isEmpty())

    override fun draw(scope: DrawScope) {
        for (balloon in balloons) scope.drawBalloon(balloon, time)
    }
}

private fun DrawScope.drawBalloon(balloon: BalloonsSimulator.Balloon, time: Float) {
    // Gentle sway is a Compose-side addition (Dart only drifts on the launch angle).
    val sway = sin(balloon.swayPhase + time * TAU / 2.4f) * balloon.radius * 0.08f
    val centerX = balloon.x + sway
    val centerY = balloon.y
    val radius = balloon.radius
    val handleLength = KAPPA * radius
    val widthDiff = radius * BALLOON_WIDTH_FACTOR
    val heightDiff = radius * BALLOON_HEIGHT_FACTOR
    val balloonBottomY = centerY + radius + heightDiff

    val path = Path()
    path.moveTo(centerX - radius, centerY)
    path.cubicTo(
        centerX - radius, centerY - handleLength - widthDiff,
        centerX - handleLength, centerY - radius,
        centerX, centerY - radius,
    )
    path.cubicTo(
        centerX + handleLength + widthDiff, centerY - radius,
        centerX + radius, centerY - handleLength,
        centerX + radius, centerY,
    )
    path.cubicTo(
        centerX + radius, centerY + handleLength,
        centerX + handleLength, balloonBottomY,
        centerX, balloonBottomY,
    )
    path.cubicTo(
        centerX - handleLength, balloonBottomY,
        centerX - radius, centerY + handleLength,
        centerX - radius, centerY,
    )
    path.close()

    val gradient = Brush.radialGradient(
        colorStops = arrayOf(
            0f to balloon.color.darkened(0.2f).copy(alpha = 0.7f),
            0.7f to balloon.color.lightened(0.1f).copy(alpha = 0.7f),
        ),
        center = Offset(centerX + radius / 3f, centerY - radius / 3f),
        radius = radius * 8f,
    )
    drawPath(path, gradient)

    // Balloon tie.
    val halfTieWidth = radius * TIE_WIDTH_FACTOR / 2f
    val tieHeight = radius * TIE_HEIGHT_FACTOR
    val tieCurveHeight = radius * TIE_CURVE_FACTOR
    val tie = Path()
    tie.moveTo(centerX - 1f, balloonBottomY)
    tie.lineTo(centerX - halfTieWidth, balloonBottomY + tieHeight)
    tie.quadraticTo(centerX, balloonBottomY + tieCurveHeight, centerX + halfTieWidth, balloonBottomY + tieHeight)
    tie.lineTo(centerX + 1f, balloonBottomY)
    tie.close()
    drawPath(tie, gradient)

    // Balloon string.
    drawLine(
        color = Color.Gray.copy(alpha = 0.6f),
        start = Offset(centerX, balloonBottomY),
        end = Offset(centerX, balloonBottomY + radius * 3f),
        strokeWidth = 0.6f,
    )
}

// ---------------------------------------------------------------------------
// Fireworks (port of fireworks_classes.dart + fireworks_rendering.dart)
// ---------------------------------------------------------------------------

/** Firework particle, shared by the fireworks and celebration effects. */
private class FireworkParticle(
    random: Random,
    x0: Float,
    y0: Float,
    hueBaseValue: Float,
    val saturation: Float?,
    isCelebration: Boolean,
    baseSize: Float,
) {
    val angle: Float = random.nextFloat() * TAU
    var velocity: Float = random.nextFloat() * (if (isCelebration) 50f else 12f) + 1f
    val hue: Float = hueBaseValue + (if (isCelebration) 0f else -50f + random.nextFloat() * 100f)
    val brightness: Float = .5f + random.nextFloat() * .3f
    val alphaDecay: Float = random.nextFloat() * .007f + .013f
    val strokeWidth: Float = if (isCelebration) random.nextFloat() * 10f else baseSize
    private val friction = .96f
    private val gravity = 2.35f
    private val trailCount = if (isCelebration) 2 else baseSize.toInt() * 2
    internal val trailX = FloatArray(trailCount) { x0 }
    internal val trailY = FloatArray(trailCount) { y0 }

    var x = x0
    var y = y0
    var alpha = 1f

    fun step() {
        // Shift the trail (Dart: removeLast + insert(0, position)).
        for (i in trailX.size - 1 downTo 1) {
            trailX[i] = trailX[i - 1]
            trailY[i] = trailY[i - 1]
        }
        trailX[0] = x
        trailY[0] = y

        velocity *= friction
        x += cos(angle) * velocity
        y += sin(angle) * velocity + gravity
        alpha -= alphaDecay
    }
}

private fun DrawScope.drawParticle(particle: FireworkParticle) {
    drawLine(
        color = hsvColor(particle.alpha.coerceIn(0f, 1f), particle.hue, particle.saturation ?: 1f, particle.brightness),
        start = Offset(particle.trailX.last(), particle.trailY.last()),
        end = Offset(particle.x, particle.y),
        strokeWidth = particle.strokeWidth,
        blendMode = BlendMode.Screen,
        cap = androidx.compose.ui.graphics.StrokeCap.Round,
    )
}

private class FireworkRocket(
    random: Random,
    startX: Float,
    startY: Float,
    val targetX: Float,
    val targetY: Float,
    val hue: Float,
    val strokeWidth: Float,
) {
    private val targetDistance = hypot(targetX - startX, targetY - startY)
    private val angle = atan2(targetY - startY, targetX - startX)
    val brightness: Float = .5f + random.nextFloat() * .2f
    private var velocity = 1f
    private val acceleration = 1.025f
    val trailX = FloatArray(2) { startX }
    val trailY = FloatArray(2) { startY }
    private val start = Offset(startX, startY)

    var x = startX
    var y = startY
    var distanceTraveled = 0f

    fun step() {
        for (i in trailX.size - 1 downTo 1) {
            trailX[i] = trailX[i - 1]
            trailY[i] = trailY[i - 1]
        }
        trailX[0] = x
        trailY[0] = y

        velocity *= acceleration
        val vx = cos(angle) * velocity
        val vy = sin(angle) * velocity
        // Dart: distance from the fixed launch point.
        distanceTraveled = hypot(x + vx - start.x, y + vy - start.y)
        if (distanceTraveled < targetDistance) {
            x += vx
            y += vy
        }
    }

    /** True once the rocket reached its target and should explode. */
    val reachedTarget: Boolean get() = distanceTraveled >= targetDistance
}

private fun DrawScope.drawRocket(rocket: FireworkRocket) {
    drawLine(
        color = hsvColor(1f, rocket.hue, 1f, rocket.brightness),
        start = Offset(rocket.trailX.last(), rocket.trailY.last()),
        end = Offset(rocket.x, rocket.y),
        strokeWidth = rocket.strokeWidth,
        cap = androidx.compose.ui.graphics.StrokeCap.Round,
    )
}

internal class FireworksSimulator(random: Random) : EffectSimulator(random, hardStopSeconds = 6f) {
    private val rockets = ArrayList<FireworkRocket>()
    private val particles = ArrayList<FireworkParticle>()
    private var globalHue = 42f
    private var lastLaunch = -1f
    private val particleSize = 3f

    override fun step() {
        globalHue = (globalHue + random.nextFloat() * 360f) % 360f

        // Dart: launch a rocket every 100ms until the host stops (~1s in).
        if (time - lastLaunch >= 0.1f && time < 1.2f && width > 0f) {
            lastLaunch = time
            rockets += FireworkRocket(
                random = random,
                startX = random.nextFloat() * (width - 32f),
                startY = height * 1.2f,
                targetX = random.nextFloat() * (width - 8f),
                targetY = 8f + random.nextFloat() * height * 4f / 7f,
                hue = globalHue,
                strokeWidth = max(0f, particleSize - 1f),
            )
        }

        for (rocket in rockets) rocket.step()
        for (particle in particles) particle.step()

        val exploded = rockets.filter { it.reachedTarget }
        if (exploded.isNotEmpty()) {
            for (rocket in exploded) {
                repeat(96) {
                    particles += FireworkParticle(
                        random = random,
                        x0 = rocket.x,
                        y0 = rocket.y,
                        hueBaseValue = rocket.hue,
                        saturation = null,
                        isCelebration = false,
                        baseSize = particleSize,
                    )
                }
            }
            rockets.removeAll(exploded)
        }
        particles.removeAll { it.alpha <= 0f }
    }

    override fun isDone(): Boolean = super.isDone() || (time > 2.5f && rockets.isEmpty() && particles.isEmpty())

    override fun draw(scope: DrawScope) = with(scope) {
        for (rocket in rockets) drawRocket(rocket)
        for (particle in particles) drawParticle(particle)
    }
}

// ---------------------------------------------------------------------------
// Celebration / sparkles (port of celebration_class.dart; drawn like fireworks)
// ---------------------------------------------------------------------------

internal class CelebrationSimulator(random: Random) : EffectSimulator(random, hardStopSeconds = 5f) {
    private val particles = ArrayList<FireworkParticle>()

    override fun step() {
        // Dart: 10 golden bursts from the top-right corner whenever the stage
        // is empty, until the host stops (~1s in).
        if (particles.isEmpty() && time < 1f && width > 0f) {
            repeat(10) {
                repeat(96) {
                    particles += FireworkParticle(
                        random = random,
                        x0 = width,
                        y0 = 0f,
                        hueBaseValue = 28f,
                        saturation = 0.5f,
                        isCelebration = true,
                        baseSize = 10f,
                    )
                }
            }
        }
        for (particle in particles) particle.step()
        particles.removeAll { it.alpha <= 0f }
    }

    override fun isDone(): Boolean = super.isDone() || (time > 1.5f && particles.isEmpty())

    override fun draw(scope: DrawScope) = with(scope) {
        for (particle in particles) drawParticle(particle)
    }
}

// ---------------------------------------------------------------------------
// Confetti (native port; the Dart app used the `confetti` pub package here)
// ---------------------------------------------------------------------------

private val CONFETTI_COLORS = listOf(
    Color(0xFFF44336), Color(0xFFE91E63), Color(0xFF9C27B0), Color(0xFF3F51B5),
    Color(0xFF2196F3), Color(0xFF4CAF50), Color(0xFFFF9800), Color(0xFFFFEB3B),
)

internal class ConfettiSimulator(random: Random) : EffectSimulator(random, hardStopSeconds = 5f) {

    private class Piece(random: Random, width: Float) {
        val color = CONFETTI_COLORS[random.nextInt(CONFETTI_COLORS.size)]
        val widthPx = 6f + random.nextFloat() * 6f
        val heightPx = 10f + random.nextFloat() * 8f
        var x = width / 2f + (random.nextFloat() - 0.5f) * width * 0.3f
        var y = -30f - random.nextFloat() * 120f
        var rotation = random.nextFloat() * 360f
        private val vx = (random.nextFloat() - 0.5f) * 4f
        private var vy = 2.5f + random.nextFloat() * 4.5f
        private val rotationVelocity = (random.nextFloat() - 0.5f) * 24f
        private var swayPhase = random.nextFloat() * TAU

        fun step() {
            swayPhase += 0.09f
            x += vx + sin(swayPhase) * 1.2f
            vy = (vy + 0.015f).coerceAtMost(12f)
            y += vy
            rotation += rotationVelocity
        }
    }

    private val pieces = ArrayList<Piece>()

    override fun step() {
        // Downward blast from the top center (Dart ConfettiWidget:
        // blastDirection pi/2, explosive, emissionFrequency 0.35).
        if (time < 1.2f && width > 0f && random.nextFloat() < 0.5f) {
            repeat(3) { pieces += Piece(random, width) }
        }
        for (piece in pieces) piece.step()
        pieces.removeAll { it.y > height + 60f }
    }

    override fun isDone(): Boolean = super.isDone() || (time > 2f && pieces.isEmpty())

    override fun draw(scope: DrawScope) = with(scope) {
        for (piece in pieces) {
            rotate(piece.rotation, pivot = Offset(piece.x, piece.y)) {
                drawRect(
                    color = piece.color,
                    topLeft = Offset(piece.x - piece.widthPx / 2f, piece.y - piece.heightPx / 2f),
                    size = Size(piece.widthPx, piece.heightPx),
                )
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Lasers (port of laser_classes.dart + laser_rendering.dart)
// ---------------------------------------------------------------------------

private class LaserBeam(baseIndex: Int, random: Random) {
    private val minAngle = (PI.toFloat() / 2f) * baseIndex
    private val maxAngle = (PI.toFloat() / 2f) * baseIndex + PI.toFloat() / 2f
    private val originalInternalWidth = (random.nextFloat() * 300f).coerceIn(50f, 300f)
    private val internalWidthVelocity: Float
    private val globalAngleVelocity: Float
    private var internalWidthDirection: Direction
    private var globalAngleDirection: Direction
    var internalWidth = (random.nextFloat() * 300f).coerceIn(50f, 300f)
    var globalAngle = (random.nextFloat() * TAU).coerceIn(minAngle, maxAngle)

    init {
        internalWidthVelocity = internalWidth / 50f
        globalAngleVelocity = globalAngle / 50f / (baseIndex + 1f)
        internalWidthDirection = if (internalWidth > originalInternalWidth) Direction.DOWN else Direction.UP
        globalAngleDirection = if (globalAngle > maxAngle) Direction.DOWN else Direction.UP
    }

    fun step() {
        if (internalWidth > originalInternalWidth ||
            (internalWidthDirection == Direction.DOWN && internalWidth >= 25f)
        ) {
            internalWidthDirection = Direction.DOWN
            internalWidth -= internalWidthVelocity
        }
        if (internalWidth < 25f ||
            (internalWidthDirection == Direction.UP && internalWidth <= originalInternalWidth)
        ) {
            internalWidthDirection = Direction.UP
            internalWidth += internalWidthVelocity
        }
        if (globalAngle >= maxAngle || (globalAngleDirection == Direction.DOWN && globalAngle >= minAngle)) {
            globalAngleDirection = Direction.DOWN
            globalAngle -= globalAngleVelocity
        }
        if (globalAngle <= minAngle || (globalAngleDirection == Direction.UP && globalAngle <= maxAngle)) {
            globalAngleDirection = Direction.UP
            globalAngle += globalAngleVelocity
        }
    }

    private enum class Direction { UP, DOWN }
}

internal class LaserSimulator(origin: Offset?, random: Random) : EffectSimulator(random, hardStopSeconds = 5f) {
    private val overrideOrigin = origin
    private val beams = ArrayList<LaserBeam>()
    private var globalHue = 42f
    private var lastHueShift = -1f

    private fun originX(): Float = overrideOrigin?.x ?: (width / 2f)
    private fun originY(): Float = overrideOrigin?.y ?: (height - 180f)

    override fun step() {
        if (beams.isEmpty() && width > 0f) {
            // Dart: 12 beams, 3 per quadrant (index % 4 selects the quadrant).
            repeat(12) { index -> beams += LaserBeam(index % 4, random) }
        }
        if (time - lastHueShift >= 0.5f) {
            lastHueShift = time
            globalHue = (globalHue + random.nextFloat() * 360f) % 360f
        }
        for (beam in beams) beam.step()
    }

    override fun draw(scope: DrawScope) = with(scope) {
        if (beams.isEmpty()) return@with
        val centerX = originX()
        val centerY = originY()
        val screenHeight = max(width, height) * sqrt(2f)
        val glowRadius = screenHeight * 2f - 100f
        val color = hsvColor(1f, globalHue % 360f, 1f, 1f)

        // Central glow.
        drawCircle(
            brush = Brush.radialGradient(
                colorStops = arrayOf(0f to color.copy(alpha = 0.8f), 0.9f to Color.Transparent),
                center = Offset(centerX, centerY),
                radius = glowRadius,
            ),
            radius = glowRadius,
            center = Offset(centerX, centerY),
        )

        for (beam in beams) {
            val path = Path()
            path.moveTo(centerX, centerY)
            path.lineTo(
                centerX + screenHeight * cos(beam.globalAngle) - beam.internalWidth * sin(beam.globalAngle),
                centerY + screenHeight * sin(beam.globalAngle) + beam.internalWidth * cos(beam.globalAngle),
            )
            path.lineTo(
                centerX + screenHeight * cos(beam.globalAngle) + beam.internalWidth * sin(beam.globalAngle),
                centerY + screenHeight * sin(beam.globalAngle) - beam.internalWidth * cos(beam.globalAngle),
            )
            path.close()
            drawPath(
                path,
                Brush.radialGradient(
                    colorStops = arrayOf(0f to color, 0.9f to Color.Transparent),
                    center = Offset(centerX, centerY),
                    radius = glowRadius,
                ),
            )
            drawPath(path, color = color.lightened(0.1f), style = Stroke(width = 2f))
        }
    }
}

// ---------------------------------------------------------------------------
// Spotlight (port of spotlight_classes.dart + spotlight_rendering.dart)
// ---------------------------------------------------------------------------

internal class SpotlightSimulator(origin: Offset?, random: Random) : EffectSimulator(random, hardStopSeconds = 4.5f) {
    private val overrideOrigin = origin
    private val originalX: Float get() = overrideOrigin?.x ?: (width / 2f)
    private val originalY: Float get() = overrideOrigin?.y ?: (height - 220f)
    private var positionX = Float.NaN
    private var positionY = Float.NaN
    private val spotlightSize = 220f
    private var stop = 1f

    override fun step() {
        if (positionX.isNaN()) {
            positionX = originalX
            positionY = originalY
        }
        // Dart: jitter around the bubble for 3s, then fade (stop -0.05/frame).
        if (time < 3f) {
            positionX = originalX + (random.nextFloat() - 0.5f)
            positionY = originalY + (random.nextFloat() - 0.5f)
        } else {
            stop -= 0.05f
        }
    }

    override fun isDone(): Boolean = super.isDone() || stop < 0f

    override fun draw(scope: DrawScope) = with(scope) {
        if (positionX.isNaN() || width <= 0f) return@with
        val centerX = positionX
        val centerY = positionY
        val radius = spotlightSize / 2f

        // Spotlight circle glow.
        if (stop >= 1f) {
            drawCircle(
                brush = Brush.radialGradient(
                    colorStops = arrayOf(0f to Color.White.copy(alpha = 0.5f), 0.7f to Color.White.copy(alpha = 0.3f)),
                    center = Offset(centerX, centerY),
                    radius = spotlightSize,
                ),
                radius = spotlightSize,
                center = Offset(centerX, centerY),
            )
        }

        // Light cone from the top-right corner, tangent to the circle.
        val apexX = width - (originalY - positionY)
        val apex = Offset(apexX, 0f)
        val dx = apexX - centerX
        val dy = 0f - centerY
        val dxr = -dy
        val dyr = dx
        val d = hypot(dx, dy).coerceAtLeast(1f)
        val rho = (radius / d).coerceAtMost(0.999f)
        val ad = rho * rho
        val bd = rho * sqrt(1f - rho * rho)
        val t1x = centerX + ad * dx + bd * dxr
        val t1y = centerY + ad * dy + bd * dyr
        val t2x = centerX + ad * dx - bd * dxr
        val t2y = centerY + ad * dy - bd * dyr

        val path = Path()
        path.moveTo(apex.x, apex.y)
        path.lineTo(minOf(t1x, t2x), minOf(t1y, t2y))
        // Sample the circle arc between the tangent points through the bottom
        // point (the Dart code used two arcToPoint calls through (cx, cy+size/2)).
        val a1 = atan2(t1y - centerY, t1x - centerX)
        val a2 = atan2(t2y - centerY, t2x - centerX)
        val bottom = PI.toFloat() / 2f
        var start = a1
        var sweep = (a2 - a1) % TAU
        if (sweep < 0f) sweep += TAU
        if ((bottom - a1).mod(TAU) > sweep) {
            // The clockwise sweep misses the bottom; sweep the other way.
            sweep = sweep - TAU
        }
        val segments = 24
        for (i in 1 until segments) {
            val angle = start + sweep * i / segments
            path.lineTo(centerX + radius * cos(angle), centerY + radius * sin(angle))
        }
        path.lineTo(maxOf(t1x, t2x), maxOf(t1y, t2y))
        path.lineTo(apex.x, apex.y)
        path.close()

        val stops: Array<Pair<Float, Color>> = if (stop >= 1f) {
            arrayOf(0f to Color.White.copy(alpha = 0.5f), 0.7f to Color.White.copy(alpha = 0.3f))
        } else {
            arrayOf(
                0f to Color.White.copy(alpha = 0.5f),
                (stop - 0.1f).coerceIn(0.01f, 0.69f) to Color.White.copy(alpha = 0.3f),
                stop.coerceIn(0.02f, 1f) to Color.Transparent,
            )
        }
        drawPath(
            path,
            Brush.radialGradient(
                colorStops = stops,
                center = Offset(width, 0f),
                radius = positionY.coerceAtLeast(1f),
            ),
        )
    }
}

// ---------------------------------------------------------------------------
// Love / heart (port of love_classes.dart + love_rendering.dart)
// ---------------------------------------------------------------------------

internal class LoveSimulator(origin: Offset?, random: Random) : EffectSimulator(random, hardStopSeconds = 5f) {
    private val overrideOrigin = origin
    private val originalX: Float get() = overrideOrigin?.x ?: (width / 2f)
    private val originalY: Float get() = overrideOrigin?.y ?: (height - 200f)

    private var x = Float.NaN
    private var y = Float.NaN
    private var heartSize = 1f
    private var velocity = 0.5f

    override fun step() {
        if (x.isNaN()) {
            x = originalX
            y = originalY
        }
        if (heartSize < 200f) {
            // Grow-in phase anchored to the bubble position.
            heartSize += 1f
            x = originalX - heartSize / 2f
            y = originalY - heartSize
            return
        }
        // Float toward the top corner away from the origin side.
        val denominator = width / 2f - originalX +
            (if (originalX < width / 2f) heartSize / 2f else -heartSize / 2f)
        var angle = atan(abs((0f - originalY) / denominator))
        if (originalX < width / 2f) angle = (PI - angle).toFloat()
        x -= velocity * cos(angle)
        y -= velocity * sin(angle)
        velocity *= 1.01f
    }

    override fun isDone(): Boolean = super.isDone() || (!y.isNaN() && y < -200f)

    override fun draw(scope: DrawScope) = with(scope) {
        if (x.isNaN()) return@with
        val d = heartSize
        val px = x
        val py = y
        val path = Path()
        path.moveTo(px, py + d / 4f)
        path.quadraticTo(px, py, px + d / 4f, py)
        path.quadraticTo(px + d / 2f, py, px + d / 2f, py + d / 4f)
        path.quadraticTo(px + d / 2f, py, px + d * 3f / 4f, py)
        path.quadraticTo(px + d, py, px + d, py + d / 4f)
        path.quadraticTo(px + d, py + d / 2f, px + d * 3f / 4f, py + d * 3f / 4f)
        path.lineTo(px + d / 2f, py + d)
        path.lineTo(px + d / 4f, py + d * 3f / 4f)
        path.quadraticTo(px, py + d / 2f, px, py + d / 4f)
        path.close()
        drawPath(
            path,
            Brush.radialGradient(
                colorStops = arrayOf(
                    0f to Color(0xFFCC0000).darkened(0.2f).copy(alpha = 0.7f),
                    0.7f to Color(0xFFF44336).lightened(0.1f).copy(alpha = 0.7f),
                ),
                center = Offset(px + d * 3f / 4f, py + d / 4f),
                radius = d * 1.5f,
            ),
        )
    }
}

// --------------------------------------------------------------------- previews

/** Renders one deterministic mid-effect frame (static preview, no animation). */
@Composable
private fun EffectStaticFramePreview(effect: SendScreenEffect, atSeconds: Float = 2f) {
    OpenBubblesTheme {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(if (effect.darkBackdrop) Color.Black else Color.White),
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val simulator = effect.newSimulator(origin = null, random = Random(7))
                simulator.width = size.width
                simulator.height = size.height
                simulator.advance(atSeconds)
                simulator.draw(this)
            }
        }
    }
}

@Preview(showBackground = true, widthDp = 320, heightDp = 560)
@Preview(showBackground = true, widthDp = 320, heightDp = 560, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun BalloonsEffectFramePreview() = EffectStaticFramePreview(SendScreenEffect.BALLOONS)

@Preview(showBackground = true, widthDp = 320, heightDp = 560)
@Preview(showBackground = true, widthDp = 320, heightDp = 560, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun FireworksEffectFramePreview() = EffectStaticFramePreview(SendScreenEffect.FIREWORKS)

@Preview(showBackground = true, widthDp = 320, heightDp = 560)
@Preview(showBackground = true, widthDp = 320, heightDp = 560, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun SpotlightEffectFramePreview() = EffectStaticFramePreview(SendScreenEffect.SPOTLIGHT, atSeconds = 1f)

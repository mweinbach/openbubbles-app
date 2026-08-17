package app.openbubbles.nativeapp.ui

import androidx.compose.animation.ContentTransform
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.unit.IntOffset
import androidx.navigation3.ui.NavDisplay
import androidx.navigationevent.NavigationEvent

/**
 * Navigation3 transition builders.
 *
 * List-detail and shared-element destinations stay on a cross-fade so the
 * flying content is not also sliding. Full-screen overlays (Settings, Find
 * My, new chat, login) slide. Predictive pops scale away from the swipe
 * edge so the gesture has a live preview instead of a delayed fade.
 */
internal object NavTransitions {
    const val PREDICTIVE_SCALE = 0.9f
    const val PIVOT_FRACTION = 0.1f
    const val SLIDE_AWAY_FRACTION = 4

    fun fade(
        enter: FiniteAnimationSpec<Float>,
        exit: FiniteAnimationSpec<Float>,
    ): ContentTransform = fadeIn(enter) togetherWith fadeOut(exit)

    fun slideForward(
        spatial: FiniteAnimationSpec<IntOffset>,
        enterFade: FiniteAnimationSpec<Float>,
        exitFade: FiniteAnimationSpec<Float>,
    ): ContentTransform =
        (slideInHorizontally(spatial) { it } + fadeIn(enterFade)) togetherWith
            (slideOutHorizontally(spatial) { -it / SLIDE_AWAY_FRACTION } + fadeOut(exitFade))

    fun slideBack(
        spatial: FiniteAnimationSpec<IntOffset>,
        enterFade: FiniteAnimationSpec<Float>,
        exitFade: FiniteAnimationSpec<Float>,
    ): ContentTransform =
        (slideInHorizontally(spatial) { -it / SLIDE_AWAY_FRACTION } + fadeIn(enterFade)) togetherWith
            (slideOutHorizontally(spatial) { it } + fadeOut(exitFade))

    fun predictivePopOrigin(swipeEdge: Int): TransformOrigin = when (swipeEdge) {
        NavigationEvent.EDGE_LEFT -> TransformOrigin(1f - PIVOT_FRACTION, 0.5f)
        NavigationEvent.EDGE_RIGHT -> TransformOrigin(PIVOT_FRACTION, 0.5f)
        else -> TransformOrigin.Center
    }

    fun predictivePop(
        swipeEdge: Int,
        enterFade: FiniteAnimationSpec<Float>,
        exitFade: FiniteAnimationSpec<Float>,
        scale: FiniteAnimationSpec<Float>,
        reduceMotion: Boolean,
    ): ContentTransform {
        val enter = fadeIn(enterFade)
        val exit = if (reduceMotion) {
            fadeOut(exitFade)
        } else {
            fadeOut(exitFade) + scaleOut(
                animationSpec = scale,
                targetScale = PREDICTIVE_SCALE,
                transformOrigin = predictivePopOrigin(swipeEdge),
            )
        }
        return enter togetherWith exit
    }

    fun overlayMetadata(
        spatial: FiniteAnimationSpec<IntOffset>,
        enterFade: FiniteAnimationSpec<Float>,
        exitFade: FiniteAnimationSpec<Float>,
        scale: FiniteAnimationSpec<Float>,
        reduceMotion: Boolean,
    ): Map<String, Any> {
        val forward = if (reduceMotion) {
            fade(enterFade, exitFade)
        } else {
            slideForward(spatial, enterFade, exitFade)
        }
        val backward = if (reduceMotion) {
            fade(enterFade, exitFade)
        } else {
            slideBack(spatial, enterFade, exitFade)
        }
        return NavDisplay.transitionSpec { forward } +
            NavDisplay.popTransitionSpec { backward } +
            NavDisplay.predictivePopTransitionSpec { edge ->
                predictivePop(edge, enterFade, exitFade, scale, reduceMotion)
            }
    }
}

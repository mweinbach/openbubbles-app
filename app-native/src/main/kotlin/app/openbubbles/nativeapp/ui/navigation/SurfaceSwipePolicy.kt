package app.openbubbles.nativeapp.ui.navigation

import kotlin.math.abs

/**
 * Arbitration for the header surface swipe.
 *
 * The switcher is only one of several owners of horizontal drag in this app:
 * message bubbles own slide-to-reply, the conversation list owns its row
 * swipes, media viewers own panning, and both screen edges belong to predictive
 * back. So this policy is deliberately narrow — it answers "did a gesture that
 * already reached the header strip commit a surface change?" and defaults to
 * no.
 *
 * A gesture is resolved once, on release, from its accumulated travel. Nothing
 * commits mid-drag, so an ambiguous drag that wanders back under the threshold,
 * or a cancelled predictive-back gesture, leaves the selected surface alone.
 */
object SurfaceSwipePolicy {
    /** Horizontal travel a header drag must accumulate before it counts. */
    const val COMMIT_THRESHOLD_DP: Float = 56f

    /**
     * Ignored band at each edge of the header. Both system back edges start
     * here, and a switcher that also claimed them would race the back gesture.
     */
    const val EDGE_EXCLUSION_DP: Float = 24f

    /**
     * How much more horizontal than vertical the travel must be. A diagonal
     * drag is usually a mis-aimed vertical scroll of the content underneath.
     */
    const val DIRECTION_DOMINANCE: Float = 2f

    /**
     * Resolves one completed header drag.
     *
     * @param dragX total horizontal travel in pixels, positive toward the right.
     * @param dragY total vertical travel in pixels.
     * @param startX where the gesture went down, relative to the header's start edge.
     * @param widthPx header width in pixels; a zero width has no interior.
     * @param enabled false while a modal owns input or the header is not showing.
     * @param rtl true when the layout direction is right-to-left, where visual
     *   "next" is a drag toward the right.
     */
    fun resolve(
        dragX: Float,
        dragY: Float,
        startX: Float,
        widthPx: Float,
        thresholdPx: Float,
        edgeExclusionPx: Float,
        enabled: Boolean,
        rtl: Boolean,
    ): SurfaceStep? {
        if (!enabled) return null
        if (widthPx <= 0f || thresholdPx <= 0f) return null
        if (startX < edgeExclusionPx) return null
        if (startX > widthPx - edgeExclusionPx) return null
        if (abs(dragX) < thresholdPx) return null
        if (abs(dragX) <= abs(dragY) * DIRECTION_DOMINANCE) return null
        val towardEnd = if (rtl) dragX > 0f else dragX < 0f
        return if (towardEnd) SurfaceStep.Forward else SurfaceStep.Backward
    }
}

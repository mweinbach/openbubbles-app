package app.openbubbles.nativeapp.ui.attachmentviewer

import java.util.Locale

/** Pure playback math/formatting for the viewer's video controls (host-tested). */

/** "0:05", "1:23", "1:02:03"; negatives clamp to "0:00". */
internal fun formatPlaybackTime(positionMs: Long): String {
    val totalSeconds = (positionMs.coerceAtLeast(0L)) / 1000
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return if (hours > 0) {
        String.format(Locale.US, "%d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format(Locale.US, "%d:%02d", minutes, seconds)
    }
}

/**
 * Clamps a seek target into the playable range, or null when the duration is
 * unknown/invalid — seeking is disabled rather than guessing bounds.
 */
internal fun clampSeekPositionMs(targetMs: Long, durationMs: Long?): Long? =
    durationMs?.takeIf { it > 0 }?.let { targetMs.coerceIn(0L, it) }

/** Progress fraction in [0, 1]; 0 while the duration is unknown. */
internal fun playbackFraction(positionMs: Long, durationMs: Long?): Float {
    val duration = durationMs?.takeIf { it > 0 } ?: return 0f
    return (positionMs.toFloat() / duration.toFloat()).coerceIn(0f, 1f)
}

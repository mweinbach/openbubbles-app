package app.openbubbles.nativeapp.ui.chat.composer

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.openbubbles.nativeapp.data.MAX_OUTGOING_DRAFT_BYTES
import app.openbubbles.nativeapp.data.OutgoingVideoMetadata
import app.openbubbles.nativeapp.ui.chat.VideoCompressionRequest
import app.openbubbles.nativeapp.ui.common.formatBytes

/**
 * Explicit review step for a video over the local draft policy (issue #49):
 * explains the policy the source violates, shows what the derived output
 * will be (at most 1080p, HEVC, HDR kept), and stages nothing until the user
 * confirms. Cancel is available before and during compression.
 */
@Composable
fun VideoCompressionReview(
    request: VideoCompressionRequest,
    inProgress: Boolean,
    progress: Float?,
    onConfirm: () -> Unit,
    onCancel: () -> Unit,
) {
    val plan = request.plan
    val maxMegabytes = MAX_OUTGOING_DRAFT_BYTES / (1024 * 1024)
    val reduction = if (plan.targetHeight != null) "to 1080p HEVC" else "with HEVC at a lower bitrate"
    val hdrNote = if (plan.keepHdr) ", keeping HDR" else ""
    AlertDialog(
        // Outside taps must not silently abort a long transcode; the Cancel
        // button remains the explicit way out while work is running.
        onDismissRequest = { if (!inProgress) onCancel() },
        title = { Text("Video is too large") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                val facts = videoSourceFacts(request.displayName, request.metadata)
                if (facts.isNotEmpty()) {
                    Text(facts, style = MaterialTheme.typography.bodyMedium)
                }
                Text(
                    "Videos larger than $maxMegabytes MB can't be attached. " +
                        "OpenBubbles can compress this video $reduction$hdrNote. " +
                        "The original video stays untouched.",
                )
                plan.estimatedOutputBytes?.let { estimate ->
                    Text(
                        "Estimated size: about ${formatBytes(estimate)}",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
                if (inProgress) {
                    Text("Compressing…", style = MaterialTheme.typography.labelLarge)
                    if (progress != null) {
                        LinearWavyProgressIndicator(
                            progress = { progress },
                            modifier = Modifier.fillMaxWidth(),
                        )
                    } else {
                        LinearWavyProgressIndicator(Modifier.fillMaxWidth())
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onConfirm, enabled = !inProgress) { Text("Compress") }
        },
        dismissButton = {
            TextButton(onClick = onCancel) { Text("Cancel") }
        },
    )
}

/** "clip.mov · 132.0 MB · 0:15 · 3840×2160 · H.264 HDR" (unknowns omitted). */
private fun videoSourceFacts(displayName: String, metadata: OutgoingVideoMetadata): String {
    val rotated = metadata.rotationDegrees % 180 != 0
    val width = if (rotated) metadata.height else metadata.width
    val height = if (rotated) metadata.width else metadata.height
    val codec = metadata.videoMime?.let { mime ->
        when (mime.lowercase()) {
            "video/avc" -> "H.264"
            "video/hevc" -> "HEVC"
            else -> mime.substringAfter('/').uppercase()
        }
    }
    return listOfNotNull(
        displayName.takeIf { it.isNotBlank() },
        formatBytes(metadata.sizeBytes).ifEmpty { null },
        metadata.durationMs?.takeIf { it > 0 }?.let(::formatDuration),
        if (width != null && height != null) "$width×$height" else null,
        codec?.let { if (metadata.isHdr) "$it HDR" else it },
    ).joinToString(" · ")
}

private fun formatDuration(durationMs: Long): String {
    val totalSeconds = durationMs / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "%d:%02d".format(minutes, seconds)
}

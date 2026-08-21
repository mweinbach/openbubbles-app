package app.openbubbles.nativeapp.ui

import android.content.res.Configuration
import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Devices
import androidx.compose.ui.tooling.preview.Preview
import app.openbubbles.nativeapp.data.OutgoingVideoMetadata
import app.openbubbles.nativeapp.data.videoCompressionPlan
import app.openbubbles.nativeapp.ui.chat.VideoCompressionRequest
import app.openbubbles.nativeapp.ui.chat.composer.VideoCompressionReview
import app.openbubbles.nativeapp.ui.theme.OpenBubblesTheme
import com.android.tools.screenshot.PreviewTest

/**
 * Golden coverage for the oversized-video compression review (issue #49):
 * the idle confirm state for a 4K HDR field-shaped source, and the dark
 * in-progress state while the derived 1080p HEVC output is being written.
 * The plan comes from the real policy so the copy stays in sync with it.
 */
private val oversized4kHdr = OutgoingVideoMetadata(
    sizeBytes = 132L * 1024L * 1024L,
    durationMs = 15_000,
    width = 3840,
    height = 2160,
    rotationDegrees = 0,
    videoMime = "video/avc",
    isHdr = true,
    frameRate = 30f,
)

private val oversizedRequest = VideoCompressionRequest(
    source = Uri.EMPTY,
    displayName = "trailhead_4k.mp4",
    metadata = oversized4kHdr,
    plan = videoCompressionPlan(oversized4kHdr),
)

@PreviewTest
@Preview(name = "video-compression-review", device = Devices.PHONE, showBackground = true)
@Composable
fun VideoCompressionReviewScreenshot() {
    OpenBubblesTheme(dynamicColor = false) {
        VideoCompressionReview(
            request = oversizedRequest,
            inProgress = false,
            progress = null,
            onConfirm = {},
            onCancel = {},
        )
    }
}

@PreviewTest
@Preview(
    name = "video-compression-progress-dark",
    device = Devices.PHONE,
    showBackground = true,
    uiMode = Configuration.UI_MODE_NIGHT_YES,
)
@Composable
fun VideoCompressionProgressDarkScreenshot() {
    OpenBubblesTheme(dynamicColor = false) {
        VideoCompressionReview(
            request = oversizedRequest,
            inProgress = true,
            progress = 0.42f,
            onConfirm = {},
            onCancel = {},
        )
    }
}

package app.openbubbles.nativeapp.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Devices
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import app.openbubbles.nativeapp.data.AttachmentMeta
import app.openbubbles.nativeapp.data.AttachmentProvider
import app.openbubbles.nativeapp.ui.attachmentviewer.AttachmentViewerScreen
import app.openbubbles.nativeapp.ui.attachmentviewer.VideoPlayerControls
import app.openbubbles.nativeapp.ui.theme.OpenBubblesTheme
import com.android.tools.screenshot.PreviewTest
import java.io.File

/**
 * Golden coverage for the attachment viewer's new surfaces (issue #51): the
 * video control chrome (playing with a known duration, and the labeled
 * unknown-duration state) and the explicit remote-download offer.
 */

@PreviewTest
@Preview(name = "video-player-controls", device = Devices.PHONE, showBackground = true, backgroundColor = 0xFF000000)
@Composable
fun VideoPlayerControlsScreenshot() {
    OpenBubblesTheme(dynamicColor = false) {
        Box(Modifier.background(Color.Black).padding(vertical = 8.dp)) {
            VideoPlayerControls(
                playing = true,
                positionMs = 83_000,
                durationMs = 245_000,
                muted = false,
                dragFraction = null,
                onPlayPause = {},
                onScrub = {},
                onScrubEnd = {},
                onToggleMute = {},
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@PreviewTest
@Preview(
    name = "video-player-controls-unknown-duration",
    device = Devices.PHONE,
    showBackground = true,
    backgroundColor = 0xFF000000,
)
@Composable
fun VideoPlayerControlsUnknownDurationScreenshot() {
    OpenBubblesTheme(dynamicColor = false) {
        Box(Modifier.background(Color.Black).padding(vertical = 8.dp)) {
            VideoPlayerControls(
                playing = false,
                positionMs = 0,
                durationMs = null,
                muted = true,
                dragFraction = null,
                onPlayPause = {},
                onScrub = {},
                onScrubEnd = {},
                onToggleMute = {},
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@PreviewTest
@Preview(name = "attachment-download-offer", device = Devices.PHONE, showBackground = true)
@Composable
fun AttachmentDownloadOfferScreenshot() {
    OpenBubblesTheme(dynamicColor = false) {
        AttachmentViewerScreen(
            guid = "demo-video-9",
            provider = object : AttachmentProvider {
                override fun byGuid(guid: String) = AttachmentMeta(
                    guid = guid, mime = "video/quicktime", name = "IMG_4021.MOV",
                    sizeBytes = 48_211_968L, isImage = false, downloaded = false,
                )

                override fun localFile(guid: String): File? = null

                override fun canDownload(guid: String): Boolean = true
            },
            onBack = {},
        )
    }
}

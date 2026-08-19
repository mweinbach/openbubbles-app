package app.openbubbles.nativeapp.ui

import android.content.res.Configuration
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Devices
import androidx.compose.ui.tooling.preview.Preview
import app.openbubbles.core.photos.PhotoMediaKind
import app.openbubbles.core.photos.PhotoSummary
import app.openbubbles.core.photos.PhotosAccess
import app.openbubbles.core.photos.PhotosAvailability
import app.openbubbles.core.photos.PhotosSnapshot
import app.openbubbles.nativeapp.ui.photos.PhotosScreen
import app.openbubbles.nativeapp.ui.photos.PhotosUiState
import app.openbubbles.nativeapp.ui.theme.OpenBubblesTheme
import com.android.tools.screenshot.PreviewTest

@PreviewTest
@Preview(name = "photos", device = Devices.PHONE, showBackground = true)
@Preview(
    name = "photos-dark",
    device = Devices.PHONE,
    showBackground = true,
    uiMode = Configuration.UI_MODE_NIGHT_YES,
)
@Composable
fun PhotosScreenScreenshot() {
    OpenBubblesTheme(dynamicColor = false) {
        PhotosScreen(
            uiState = PhotosUiState(
                loading = false,
                snapshot = PhotosSnapshot(
                    access = PhotosAccess(
                        availability = PhotosAvailability.Ready,
                        detail = "Personal iCloud Photos metadata is available",
                    ),
                    assets = listOf(
                        PhotoSummary(
                            id = "master-1",
                            assetId = "asset-1",
                            filename = "IMG_1042.HEIC",
                            mediaKind = PhotoMediaKind.Image,
                            livePhoto = true,
                            width = 4032,
                            height = 3024,
                            originalSize = 4_200_000,
                            previewSize = 102_000,
                            capturedAtMs = null,
                            addedAtMs = null,
                            favorite = true,
                            hidden = false,
                        ),
                        PhotoSummary(
                            id = "master-2",
                            assetId = "asset-2",
                            filename = "IMG_1043.MOV",
                            mediaKind = PhotoMediaKind.Video,
                            livePhoto = false,
                            width = 1920,
                            height = 1080,
                            originalSize = 38_700_000,
                            previewSize = 1_200_000,
                            capturedAtMs = null,
                            addedAtMs = null,
                            favorite = false,
                            hidden = false,
                        ),
                    ),
                    nextCursor = "2",
                ),
            ),
            onBack = {},
            onRefresh = {},
            onLoadMore = {},
            onDownloadPreview = {},
        )
    }
}

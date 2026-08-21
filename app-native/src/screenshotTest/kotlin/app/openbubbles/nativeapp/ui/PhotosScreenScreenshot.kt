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

/**
 * Photos timeline fixtures.
 *
 * The renderer decodes no previews, so these prove the timeline chrome: dated
 * sections at the chosen density, per-section counts, badges, and the app-bar
 * status line. Fixed clock and fixed capture dates keep the section titles
 * stable.
 */
private const val FIXED_NOW = 1_760_011_200_000L
private const val DAY = 86_400_000L

private fun photo(
    id: String,
    filename: String,
    capturedAtMs: Long?,
    favorite: Boolean = false,
    livePhoto: Boolean = false,
    kind: PhotoMediaKind = PhotoMediaKind.Image,
) = PhotoSummary(
    id = id,
    assetId = "asset-$id",
    filename = filename,
    mediaKind = kind,
    livePhoto = livePhoto,
    width = 4032,
    height = 3024,
    originalSize = if (kind == PhotoMediaKind.Video) 38_700_000 else 4_200_000,
    previewSize = 102_000,
    capturedAtMs = capturedAtMs,
    addedAtMs = capturedAtMs,
    favorite = favorite,
    hidden = false,
)

private fun libraryState(): PhotosUiState = PhotosUiState(
    loading = false,
    snapshot = PhotosSnapshot(
        access = PhotosAccess(
            availability = PhotosAvailability.Ready,
            detail = "Personal iCloud Photos metadata is available",
        ),
        assets = listOf(
            photo("m1", "IMG_1042.HEIC", FIXED_NOW - 3_600_000, favorite = true, livePhoto = true),
            photo("m2", "IMG_1043.MOV", FIXED_NOW - 5_400_000, kind = PhotoMediaKind.Video),
            photo("m3", "IMG_1044.HEIC", FIXED_NOW - 7_200_000),
            photo("m4", "IMG_1031.HEIC", FIXED_NOW - DAY - 3_600_000),
            photo("m5", "IMG_1032.HEIC", FIXED_NOW - DAY - 7_200_000, favorite = true),
            photo("m6", "IMG_0980.HEIC", FIXED_NOW - 6 * DAY),
            photo("m7", "IMG_0981.MOV", FIXED_NOW - 6 * DAY - 600_000, kind = PhotoMediaKind.Video),
            photo("m8", "IMG_0754.HEIC", FIXED_NOW - 40 * DAY),
            photo("m9", "IMG_0755.HEIC", FIXED_NOW - 40 * DAY - 600_000),
            photo("m10", "IMG_0330.HEIC", FIXED_NOW - 400 * DAY),
            photo("m11", "IMG_0001.HEIC", null),
        ),
        nextCursor = "11",
    ),
)

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
            uiState = libraryState(),
            onBack = {},
            onRefresh = {},
            onLoadMore = {},
            onPreviewVisible = {},
            onPreviewHidden = {},
            onRetryPreview = {},
            onSelect = {},
            onCloseSelected = {},
            onRetryOriginal = {},
            onSaveToGallery = {},
            onChooseUploads = {},
            onAddFolder = {},
            onScanFolder = {},
            onRemoveFolder = {},
            onUpload = {},
            onUploadAll = {},
            nowMillis = FIXED_NOW,
        )
    }
}

/** Wide window: the same timeline, more columns, same section structure. */
@PreviewTest
@Preview(name = "photos-expanded", widthDp = 1280, heightDp = 800, showBackground = true)
@Composable
fun PhotosExpandedScreenshot() {
    OpenBubblesTheme(dynamicColor = false) {
        PhotosScreen(
            uiState = libraryState(),
            onBack = {},
            onRefresh = {},
            onLoadMore = {},
            onPreviewVisible = {},
            onPreviewHidden = {},
            onRetryPreview = {},
            onSelect = {},
            onCloseSelected = {},
            onRetryOriginal = {},
            onSaveToGallery = {},
            onChooseUploads = {},
            onAddFolder = {},
            onScanFolder = {},
            onRemoveFolder = {},
            onUpload = {},
            onUploadAll = {},
            nowMillis = FIXED_NOW,
        )
    }
}

/** Offline: cached metadata stays browsable and says so. */
@PreviewTest
@Preview(name = "photos-offline", device = Devices.PHONE, showBackground = true)
@Composable
fun PhotosOfflineScreenshot() {
    OpenBubblesTheme(dynamicColor = false) {
        PhotosScreen(
            uiState = libraryState().copy(showingCachedMetadata = true),
            onBack = {},
            onRefresh = {},
            onLoadMore = {},
            onPreviewVisible = {},
            onPreviewHidden = {},
            onRetryPreview = {},
            onSelect = {},
            onCloseSelected = {},
            onRetryOriginal = {},
            onSaveToGallery = {},
            onChooseUploads = {},
            onAddFolder = {},
            onScanFolder = {},
            onRemoveFolder = {},
            onUpload = {},
            onUploadAll = {},
            nowMillis = FIXED_NOW,
        )
    }
}

package app.openbubbles.nativeapp.ui.photos

import android.content.Context
import app.openbubbles.core.photos.PhotoSummary
import app.openbubbles.nativeapp.data.photos.PhotoLibraryExport
import app.openbubbles.nativeapp.ui.attachmentviewer.saveToMediaStore
import app.openbubbles.nativeapp.ui.attachmentviewer.shareAttachment
import java.io.File

/**
 * One-way gallery export for a downloaded iCloud original.
 *
 * The bytes are copied verbatim into `Pictures/iCloud Photos` (videos into
 * `Movies/iCloud Photos`) so the photo becomes an ordinary Android gallery item
 * that any app can pick, send, or share. Nothing links the copy back to iCloud:
 * the Photos path never observes the device gallery, and the only Apple write
 * remains the explicit picker/folder staging plus a separate upload tap.
 */
internal enum class PhotoGalleryExportOutcome { Saved, Unsupported, Failed }

internal fun savePhotoToGallery(
    context: Context,
    asset: PhotoSummary,
    original: File,
): PhotoGalleryExportOutcome {
    val plan = PhotoLibraryExport.plan(
        cachedFileName = original.name,
        filename = asset.filename,
        mediaKind = asset.mediaKind,
        capturedAtMs = asset.capturedAtMs,
    ) ?: return PhotoGalleryExportOutcome.Unsupported
    val saved = saveToMediaStore(
        context = context,
        displayName = plan.displayName,
        mime = plan.mimeType,
        video = plan.video,
        dateTakenMillis = plan.dateTakenMillis,
        relativePath = plan.relativePath,
    ) { output -> original.inputStream().use { it.copyTo(output) } }
    return if (saved) PhotoGalleryExportOutcome.Saved else PhotoGalleryExportOutcome.Failed
}

/**
 * Shares the downloaded original straight from the app's private cache with a
 * temporary read grant, so sending a photo does not require saving it first.
 */
internal fun sharePhotoOriginal(context: Context, asset: PhotoSummary, original: File): Boolean {
    val plan = PhotoLibraryExport.plan(
        cachedFileName = original.name,
        filename = asset.filename,
        mediaKind = asset.mediaKind,
        capturedAtMs = asset.capturedAtMs,
    )
    return shareAttachment(context, original, plan?.mimeType)
}

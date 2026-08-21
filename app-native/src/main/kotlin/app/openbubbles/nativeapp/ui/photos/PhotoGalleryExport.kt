package app.openbubbles.nativeapp.ui.photos

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.provider.MediaStore
import androidx.core.content.ContextCompat
import app.openbubbles.core.photos.PhotoSummary
import app.openbubbles.nativeapp.data.photos.PhotoGalleryExportPlan
import app.openbubbles.nativeapp.data.photos.PhotoLibraryExport
import app.openbubbles.nativeapp.ui.attachmentviewer.requiresLegacyMediaWritePermission
import app.openbubbles.nativeapp.ui.attachmentviewer.saveToMediaStore
import app.openbubbles.nativeapp.ui.attachmentviewer.shareAttachment
import java.io.File

/**
 * One-way gallery export for a downloaded iCloud original.
 *
 * Downloading a full-quality asset mirrors it into `DCIM/iCloud`, so the photo
 * becomes an ordinary Android gallery item that any app can pick, send, or
 * share. Nothing links the copy back to iCloud: the Photos path never observes
 * the device gallery, and the only Apple write remains the explicit
 * picker/folder staging plus a separate upload tap.
 */
enum class PhotoGalleryExportOutcome {
    Saved,
    AlreadySaved,
    PermissionRequired,
    Unsupported,
    Failed,
}

internal fun savePhotoToGallery(
    context: Context,
    asset: PhotoSummary,
    original: File,
): PhotoGalleryExportOutcome {
    val plan = exportPlan(asset, original) ?: return PhotoGalleryExportOutcome.Unsupported
    if (legacyWriteBlocked(context)) return PhotoGalleryExportOutcome.PermissionRequired
    val length = original.length()
    if (length <= 0) return PhotoGalleryExportOutcome.Failed
    if (alreadyInAlbum(context, plan, length)) return PhotoGalleryExportOutcome.AlreadySaved
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
 * temporary read grant, so sending a photo never depends on the gallery copy.
 */
internal fun sharePhotoOriginal(context: Context, asset: PhotoSummary, original: File): Boolean =
    shareAttachment(context, original, exportPlan(asset, original)?.mimeType)

private fun exportPlan(asset: PhotoSummary, original: File): PhotoGalleryExportPlan? =
    PhotoLibraryExport.plan(
        cachedFileName = original.name,
        filename = asset.filename,
        mediaKind = asset.mediaKind,
        capturedAtMs = asset.capturedAtMs,
    )

/** API 26-28 still needs the legacy grant before MediaStore accepts an insert. */
private fun legacyWriteBlocked(context: Context): Boolean {
    val granted = ContextCompat.checkSelfPermission(
        context,
        Manifest.permission.WRITE_EXTERNAL_STORAGE,
    ) == PackageManager.PERMISSION_GRANTED
    return requiresLegacyMediaWritePermission(Build.VERSION.SDK_INT, granted)
}

/**
 * True when this exact file is already in the album, so re-opening a photo does
 * not fill the gallery with copies. `RELATIVE_PATH` only exists on Q+; below
 * that the album is matched by the stored path instead. A same-named entry with
 * a different size is left alone and the new copy is inserted, because a
 * changed asset is not the same photo.
 */
private fun alreadyInAlbum(
    context: Context,
    plan: PhotoGalleryExportPlan,
    length: Long,
): Boolean = runCatching {
    val collection = if (plan.video) {
        MediaStore.Video.Media.EXTERNAL_CONTENT_URI
    } else {
        MediaStore.Images.Media.EXTERNAL_CONTENT_URI
    }
    val projection = arrayOf(MediaStore.MediaColumns.SIZE)
    val selection: String
    val arguments: Array<String>
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        selection = "${MediaStore.MediaColumns.RELATIVE_PATH}=? AND " +
            "${MediaStore.MediaColumns.DISPLAY_NAME}=?"
        arguments = arrayOf("${plan.relativePath}/", plan.displayName)
    } else {
        @Suppress("DEPRECATION")
        selection = "${MediaStore.MediaColumns.DATA} LIKE ? AND " +
            "${MediaStore.MediaColumns.DISPLAY_NAME}=?"
        arguments = arrayOf("%/${plan.relativePath}/%", plan.displayName)
    }
    context.contentResolver.query(collection, projection, selection, arguments, null)?.use { cursor ->
        val sizeColumn = cursor.getColumnIndex(MediaStore.MediaColumns.SIZE)
        while (cursor.moveToNext()) {
            if (sizeColumn >= 0 && cursor.getLong(sizeColumn) == length) return@runCatching true
        }
    }
    false
}.getOrDefault(false)

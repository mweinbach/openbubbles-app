package app.openbubbles.nativeapp.data

import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import app.openbubbles.core.attachment.AttachmentMedia
import java.io.File

/** Copies a downloaded image or video into the device gallery. */
fun saveDownloadedMediaIfEligible(
    context: Context,
    file: File,
    mime: String?,
    name: String?,
    uti: String? = null,
) {
    if (!file.isFile) return
    val isImage = AttachmentMedia.isImage(mime, uti, name)
    val isVideo = AttachmentMedia.isVideo(mime, uti, name)
    if (!isImage && !isVideo) return
    val displayName = name?.substringAfterLast('/')?.ifBlank { null } ?: file.name
    val resolvedMime = AttachmentMedia.suggestedMime(mime, uti, name)
    val collection = if (isVideo) {
        MediaStore.Video.Media.EXTERNAL_CONTENT_URI
    } else {
        MediaStore.Images.Media.EXTERNAL_CONTENT_URI
    }
    val relative = if (isVideo) {
        Environment.DIRECTORY_MOVIES + "/OpenGarden"
    } else {
        Environment.DIRECTORY_PICTURES + "/OpenGarden"
    }
    val values = ContentValues().apply {
        put(MediaStore.MediaColumns.DISPLAY_NAME, displayName)
        put(MediaStore.MediaColumns.MIME_TYPE, resolvedMime)
        if (Build.VERSION.SDK_INT >= 29) {
            put(MediaStore.MediaColumns.RELATIVE_PATH, relative)
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }
    }
    val uri = context.contentResolver.insert(collection, values) ?: return
    runCatching {
        context.contentResolver.openOutputStream(uri)?.use { output ->
            file.inputStream().use { it.copyTo(output) }
        } ?: error("gallery output unavailable")
    }.onFailure {
        runCatching { context.contentResolver.delete(uri, null, null) }
        return
    }
    if (Build.VERSION.SDK_INT >= 29) {
        values.clear()
        values.put(MediaStore.MediaColumns.IS_PENDING, 0)
        context.contentResolver.update(uri, values, null, null)
    }
}

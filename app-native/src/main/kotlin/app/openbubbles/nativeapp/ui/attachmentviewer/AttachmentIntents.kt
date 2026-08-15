package app.openbubbles.nativeapp.ui.attachmentviewer

import android.content.ClipData
import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import java.io.File

private fun contentUri(context: Context, file: File) = FileProvider.getUriForFile(
    context,
    "${context.packageName}.fileprovider",
    file,
)

/** Opens a downloaded attachment in a compatible installed app. */
internal fun openAttachmentExternally(context: Context, file: File, mime: String?): Boolean =
    runCatching {
        val uri = contentUri(context, file)
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, mime ?: "application/octet-stream")
            clipData = ClipData.newUri(context.contentResolver, file.name, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(intent)
        true
    }.getOrDefault(false)

/** Shares one downloaded attachment with a temporary read-only URI grant. */
internal fun shareAttachment(context: Context, file: File, mime: String?): Boolean =
    runCatching {
        val uri = contentUri(context, file)
        val send = Intent(Intent.ACTION_SEND).apply {
            type = mime ?: "application/octet-stream"
            putExtra(Intent.EXTRA_STREAM, uri)
            clipData = ClipData.newUri(context.contentResolver, file.name, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(send, "Share attachment"))
        true
    }.getOrDefault(false)

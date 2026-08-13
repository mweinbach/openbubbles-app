package com.bluebubbles.messaging.services.system

import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import com.bluebubbles.messaging.models.MethodCallHandlerImpl
import io.flutter.plugin.common.MethodCall
import io.flutter.plugin.common.MethodChannel
import java.io.File
import java.io.FileInputStream

/// Saves a file to the system Downloads folder.
/// On Android 10+ (API 29+) uses MediaStore.Downloads so no WRITE_EXTERNAL_STORAGE is needed.
/// On older versions falls back to direct file copy into the public Downloads directory.
class SaveFileToDownloadsHandler : MethodCallHandlerImpl() {
    companion object {
        const val tag: String = "save-file-to-downloads"
    }

    override fun handleMethodCall(call: MethodCall, result: MethodChannel.Result, context: Context) {
        val filePath: String = call.argument("filePath")!!
        val fileName: String = call.argument("fileName")!!
        val mimeType: String = call.argument("mimeType") ?: "application/octet-stream"

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val values = ContentValues().apply {
                    put(MediaStore.Downloads.DISPLAY_NAME, fileName)
                    put(MediaStore.Downloads.MIME_TYPE, mimeType)
                    put(MediaStore.Downloads.IS_PENDING, 1)
                }
                val resolver = context.contentResolver
                val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                    ?: throw Exception("MediaStore.Downloads insert returned null URI")

                resolver.openOutputStream(uri)?.use { outputStream ->
                    FileInputStream(filePath).use { inputStream ->
                        inputStream.copyTo(outputStream)
                    }
                }

                values.clear()
                values.put(MediaStore.Downloads.IS_PENDING, 0)
                resolver.update(uri, values, null, null)

                result.success(fileName)
            } else {
                @Suppress("DEPRECATION")
                val destDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                destDir.mkdirs()
                val destFile = File(destDir, fileName)
                File(filePath).copyTo(destFile, overwrite = true)
                result.success(destFile.absolutePath)
            }
        } catch (e: Exception) {
            result.error("SAVE_FAILED", e.message, null)
        }
    }
}

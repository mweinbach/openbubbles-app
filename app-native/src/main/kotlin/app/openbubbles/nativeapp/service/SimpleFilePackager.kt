package app.openbubbles.nativeapp.service

import android.webkit.MimeTypeMap
import uniffi.rust_lib_bluebubbles.FileInfo
import uniffi.rust_lib_bluebubbles.KotlinFilePackager
import uniffi.rust_lib_bluebubbles.PackagedFile
import java.io.File
import java.net.URLConnection

/**
 * Minimal KotlinFilePackager for the native app: reads the file and guesses
 * basic media info. The Flutter app's AndroidFilePackager additionally does
 * EXIF-aware thumbnailing via a Flutter plugin; shared-album uploads that
 * need real FileInfo metadata arrive with the shared-streams batch.
 */
class SimpleFilePackager : KotlinFilePackager {

    override fun getFile(path: String): PackagedFile {
        return try {
            val file = File(path)
            if (!file.exists()) {
                PackagedFile.Failure("not found: $path")
            } else {
                PackagedFile.Info(FileInfo(duration = null, width = 0u, height = 0u, thumbnail = null))
            }
        } catch (t: Throwable) {
            PackagedFile.Failure(t.message ?: "packaging failed")
        }
    }

    override fun scanFiles(paths: List<String>) {
        // Media scanner is only relevant for gallery-visible downloads;
        // attachment downloads handle visibility themselves.
    }

    companion object {
        fun guessMime(name: String): String =
            MimeTypeMap.getSingleton().getMimeTypeFromExtension(
                MimeTypeMap.getFileExtensionFromUrl(name)
            ) ?: URLConnection.guessContentTypeFromName(name) ?: "application/octet-stream"
    }
}

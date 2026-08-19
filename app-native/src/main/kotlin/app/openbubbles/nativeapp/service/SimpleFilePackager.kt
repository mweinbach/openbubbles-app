package app.openbubbles.nativeapp.service

import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import androidx.core.content.edit
import uniffi.rust_lib_bluebubbles.FileInfo
import uniffi.rust_lib_bluebubbles.KotlinFilePackager
import uniffi.rust_lib_bluebubbles.PackagedFile
import java.io.File
import java.util.concurrent.Executors

/**
 * Minimal KotlinFilePackager for the native app: reads the file and guesses
 * basic media info. The Flutter app's AndroidFilePackager additionally does
 * EXIF-aware thumbnailing via a Flutter plugin; shared-album uploads that
 * need real FileInfo metadata arrive with the shared-streams batch.
 *
 * [scanFiles] receives newly synced shared-album downloads from the Rust
 * sync loop and copies them into MediaStore so they show up in gallery apps.
 * Exports run on a single background thread: the callback arrives on a Rust
 * async worker that must not block on large video copies, and one thread
 * serializes the exported-set bookkeeping.
 */
class SimpleFilePackager(private val context: Context) : KotlinFilePackager {

    private val exportExecutor = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "album-gallery-export")
    }

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
        if (paths.isEmpty()) return
        exportExecutor.execute { exportToGallery(paths) }
    }

    private fun exportToGallery(paths: List<String>) {
        val albumsRoot = context.getExternalFilesDir(Environment.DIRECTORY_PICTURES)
            ?.resolve("Shared Albums") ?: return
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val exported = prefs.getStringSet(KEY_EXPORTED, emptySet())!!.toMutableSet()
        for (path in paths) {
            try {
                val file = File(path)
                if (!file.isFile) continue
                val plan = SharedAlbumGalleryExport.plan(albumsRoot, file, file.length()) ?: continue
                if (plan.dedupKey in exported) continue
                copyToMediaStore(file, plan)
                exported += plan.dedupKey
                prefs.edit { putStringSet(KEY_EXPORTED, exported.toSet()) }
            } catch (t: Throwable) {
                Log.w(TAG, "gallery export failed for $path", t)
            }
        }
    }

    private fun copyToMediaStore(source: File, plan: SharedAlbumExportPlan) {
        val resolver = context.contentResolver
        val collection = if (plan.isVideo) {
            MediaStore.Video.Media.EXTERNAL_CONTENT_URI
        } else {
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        }
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, plan.displayName)
            put(MediaStore.MediaColumns.MIME_TYPE, plan.mimeType)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.MediaColumns.RELATIVE_PATH, plan.relativePath)
                put(MediaStore.MediaColumns.IS_PENDING, 1)
            }
        }
        val uri = resolver.insert(collection, values) ?: error("MediaStore insert failed")
        try {
            resolver.openOutputStream(uri)?.use { output ->
                source.inputStream().use { input -> input.copyTo(output) }
            } ?: error("MediaStore output unavailable")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                values.clear()
                values.put(MediaStore.MediaColumns.IS_PENDING, 0)
                resolver.update(uri, values, null, null)
            }
        } catch (error: Throwable) {
            resolver.delete(uri, null, null)
            throw error
        }
    }

    private companion object {
        const val PREFS = "shared_album_gallery_export"
        const val KEY_EXPORTED = "exported_v1"
        const val TAG = "SimpleFilePackager"
    }
}

package app.openbubbles.nativeapp.data.photos

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import androidx.core.content.edit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class PhotoFolderSource(
    val uri: Uri,
    val displayName: String,
)

internal data class PhotoFolderGrantCleanup(
    val released: Int,
    val failed: Int,
    val preferencesCleared: Boolean,
) {
    val complete: Boolean
        get() = failed == 0 && preferencesCleared
}

internal fun photoFolderGrantValues(
    stored: Collection<String>,
    persisted: Collection<String>,
): List<String> = (stored.asSequence() + persisted.asSequence())
    .map(String::trim)
    .filter(String::isNotEmpty)
    .distinct()
    .sorted()
    .toList()

/** Persisted, user-selected Android document trees. Nothing scans them automatically. */
class PhotoFolderSources(private val context: Context) {
    private val resolver = context.contentResolver
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun sources(): List<PhotoFolderSource> = prefs.getStringSet(KEY_URIS, emptySet()).orEmpty()
        .map { value ->
            val uri = Uri.parse(value)
            runCatching { source(uri) }.getOrElse { PhotoFolderSource(uri, "Unavailable folder") }
        }
        .sortedBy { it.displayName.lowercase() }

    fun add(uri: Uri): PhotoFolderSource {
        resolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        prefs.edit {
            putStringSet(
                KEY_URIS,
                prefs.getStringSet(KEY_URIS, emptySet()).orEmpty() + uri.toString(),
            )
        }
        return source(uri)
    }

    fun remove(uri: Uri) {
        prefs.edit {
            putStringSet(
                KEY_URIS,
                prefs.getStringSet(KEY_URIS, emptySet()).orEmpty() - uri.toString(),
            )
        }
        runCatching {
            resolver.releasePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }

    /**
     * Releases every persisted read-only tree grant owned by this feature,
     * including an orphan whose preference write was previously lost, then
     * clears the account-specific source list.
     */
    internal fun clearAccountState(): PhotoFolderGrantCleanup {
        val storedResult = runCatching { prefs.getStringSet(KEY_URIS, emptySet()).orEmpty() }
        val stored = storedResult.getOrDefault(emptySet())
        // PhotoFolderSources is the app's only owner of persistable tree
        // grants. Enumerating the resolver also catches prefs/grant drift.
        val persistedResult = runCatching {
            resolver.persistedUriPermissions
                .asSequence()
                .filter { it.isReadPermission }
                .map { it.uri }
                .filter { uri -> runCatching { DocumentsContract.isTreeUri(uri) }.getOrDefault(false) }
                .associateBy { it.toString() }
        }
        val persisted = persistedResult.getOrDefault(emptyMap())
        val values = photoFolderGrantValues(stored, persisted.keys)
        var released = 0
        var failed = (if (storedResult.isFailure) 1 else 0) +
            (if (persistedResult.isFailure) 1 else 0)
        values.forEach { value ->
            val uri = persisted[value] ?: return@forEach
            runCatching {
                resolver.releasePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION,
                )
            }.onSuccess {
                released += 1
            }.onFailure {
                failed += 1
            }
        }
        return PhotoFolderGrantCleanup(
            released = released,
            failed = failed,
            preferencesCleared = prefs.edit().clear().commit(),
        )
    }

    suspend fun photos(source: PhotoFolderSource, limit: Int = MAX_SCAN_ITEMS): List<Uri> =
        withContext(Dispatchers.IO) {
            require(DocumentsContract.isTreeUri(source.uri)) { "The selected source is not a folder" }
            val pending = ArrayDeque<String>().apply {
                add(DocumentsContract.getTreeDocumentId(source.uri))
            }
            val photos = mutableListOf<Pair<String, Uri>>()
            while (pending.isNotEmpty() && photos.size < limit) {
                val parentId = pending.removeFirst()
                val children = DocumentsContract.buildChildDocumentsUriUsingTree(
                    source.uri,
                    parentId,
                )
                resolver.query(children, PROJECTION, null, null, null)?.use { cursor ->
                    val idIndex = cursor.getColumnIndexOrThrow(
                        DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                    )
                    val nameIndex = cursor.getColumnIndexOrThrow(
                        DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                    )
                    val mimeIndex = cursor.getColumnIndexOrThrow(
                        DocumentsContract.Document.COLUMN_MIME_TYPE,
                    )
                    while (cursor.moveToNext() && photos.size < limit) {
                        val documentId = cursor.getString(idIndex)
                        val mime = cursor.getString(mimeIndex)
                        if (mime == DocumentsContract.Document.MIME_TYPE_DIR) {
                            pending.add(documentId)
                        } else if (mime?.startsWith("image/") == true) {
                            photos += cursor.getString(nameIndex).orEmpty() to
                                DocumentsContract.buildDocumentUriUsingTree(source.uri, documentId)
                        }
                    }
                }
            }
            photos.sortedBy { it.first.lowercase() }.map { it.second }
        }

    private fun source(uri: Uri): PhotoFolderSource {
        var name: String? = null
        val documentUri = DocumentsContract.buildDocumentUriUsingTree(
            uri,
            DocumentsContract.getTreeDocumentId(uri),
        )
        resolver.query(
            documentUri,
            arrayOf(DocumentsContract.Document.COLUMN_DISPLAY_NAME),
            null,
            null,
            null,
        )?.use { cursor -> if (cursor.moveToFirst()) name = cursor.getString(0) }
        return PhotoFolderSource(uri, name?.takeIf(String::isNotBlank) ?: "Selected folder")
    }

    companion object {
        const val MAX_SCAN_ITEMS = 500
        private const val PREFS_NAME = "icloud_photo_folder_sources"
        private const val KEY_URIS = "tree_uris"
        private val PROJECTION = arrayOf(
            DocumentsContract.Document.COLUMN_DOCUMENT_ID,
            DocumentsContract.Document.COLUMN_DISPLAY_NAME,
            DocumentsContract.Document.COLUMN_MIME_TYPE,
        )
    }
}

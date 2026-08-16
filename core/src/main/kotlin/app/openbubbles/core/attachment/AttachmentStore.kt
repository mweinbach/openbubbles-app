package app.openbubbles.core.attachment

import app.openbubbles.db.Attachment
import app.openbubbles.db.Attachment_
import io.objectbox.BoxStore
import io.objectbox.query.QueryBuilder
import java.io.File
import java.io.IOException

/**
 * Canonical attachment disk layout, ported from the Dart app's
 * `FilesystemService` + `Attachment.path`:
 *
 * ```
 * <rootDir>/attachments/<attachment-guid>/<sanitized transferName>
 * ```
 *
 * where [rootDir] is the app documents/support directory. The Dart app keeps
 * per-guid directories so a re-delivered attachment with a new transfer name
 * never collides with another file, and guid-directory renames stay atomic
 * (`Attachment.replaceAttachmentAsync`).
 *
 * Sanitization replaces filesystem-hostile characters in both the guid and
 * the transfer name (the Dart code only replaces `/` on POSIX but the union
 * of its POSIX + Windows rules everywhere, so Android and desktop produce
 * byte-identical layouts). After sanitization a canonicalization check guards
 * against path traversal — the Dart `path` getter's
 * `canonicalize(file).startsWith(canonicalize(directory))` defense.
 *
 * DB side, [markDownloaded] implements the downloaded-state semantics from
 * `lib/database/io/attachment.dart`: `isDownloaded = true` plus the real byte
 * length once the file is on disk.
 */
class AttachmentStore(
    private val store: BoxStore,
    private val rootDir: File,
) {
    companion object {
        /** Sub-directory of [rootDir] holding every attachment. */
        const val ATTACHMENTS_DIR_NAME = "attachments"

        /** File name used when an attachment has no usable transfer name. */
        const val DEFAULT_FILE_NAME = "unknown"

        /**
         * Characters replaced in file/directory names — the union of the Dart
         * POSIX (`/`) and Windows (`<>:"/\|?*`) sanitizers, plus C0 control
         * characters.
         */
        private val UNSAFE_NAME_CHARS = Regex("[<>:\"/\\\\|?*\u0000-\u001f]")

        /** Names that are still dangerous after character replacement. */
        private val DOT_NAMES = setOf("", ".", "..")
    }

    private val attachmentBox by lazy { store.boxFor(Attachment::class.java) }

    /** `<rootDir>/attachments` (Dart `FilesystemService.attachmentsPath`). */
    val attachmentsDir: File
        get() = File(rootDir, ATTACHMENTS_DIR_NAME)

    /** Per-attachment directory `<rootDir>/attachments/<guid>` (Dart `Attachment.directory`). */
    fun directoryFor(attachmentGuid: String): File =
        File(attachmentsDir, sanitizeDirectoryName(attachmentGuid))

    /**
     * Promotes a locally staged attachment directory to the guid returned by
     * Rust after upload. This is the disk half of the legacy app's
     * `replaceAttachmentAsync` flow.
     */
    fun promoteLocalDirectory(oldGuid: String, newGuid: String): Boolean {
        if (oldGuid == newGuid) return true
        val oldDir = directoryFor(oldGuid)
        val newDir = directoryFor(newGuid)
        if (!oldDir.isDirectory || newDir.exists()) return false
        newDir.parentFile?.mkdirs()
        return oldDir.renameTo(newDir)
    }

    /**
     * Canonical path for an attachment's payload file. Pure computation —
     * the file (and its parent directory) may not exist yet; callers create
     * them on download. Throws [IOException] if the resolved path would
     * escape the attachment's own directory (path traversal).
     */
    @Throws(IOException::class)
    fun pathFor(attachment: Attachment): File {
        val guid = attachment.guid
            ?: throw IOException("Attachment has no guid; no path can be derived")
        val dir = directoryFor(guid)
        val name = sanitizeFileName(attachment.transferName)
        val file = File(dir, name)
        ensureInside(dir, file)
        return file
    }

    /**
     * The file on disk for [attachment], or null when nothing has been
     * written yet. Mirrors `AttachmentsService.getContent`'s preference for
     * actual file presence over the persisted flag, including the fallback
     * to a converted `path.png` when only that survives (HEIC/TIFF
     * conversion output; conversion itself is out of M2 scope).
     */
    fun existingFile(attachment: Attachment): File? {
        if (attachment.guid == null) return null
        val primary = pathFor(attachment)
        if (primary.isFile) return primary
        val converted = File(primary.path + ".png")
        if (converted.isFile) return converted
        return null
    }

    /**
     * Deletes every local representation of [attachment] — the payload, the
     * converted `.png` and the two `.thumbnail` siblings — as
     * `AttachmentsService.redownloadAttachment` does before forcing a fresh
     * download. Missing files are ignored.
     */
    fun deleteLocalFiles(attachment: Attachment) {
        if (attachment.guid == null) return
        val primary = pathFor(attachment)
        listOf(
            primary,
            File(primary.path + ".png"),
            File(primary.path + ".thumbnail"),
            File(primary.path + ".png.thumbnail"),
        ).forEach { it.delete() }
        primary.parentFile?.takeIf { it.isDirectory && it.list().isNullOrEmpty() }?.delete()
    }

    /**
     * Marks the attachment (looked up fresh by guid) as downloaded and
     * persists the real byte length. [actualBytes] only overwrites a stored
     * total when positive — the Dart merge rule never shrinks payload info.
     * Returns the persisted row, or null when the guid is unknown.
     */
    fun markDownloaded(attachmentGuid: String, actualBytes: Long? = null): Attachment? {
        val row = attachmentBox.query()
            .equal(Attachment_.guid, attachmentGuid, QueryBuilder.StringOrder.CASE_SENSITIVE)
            .build().use { it.findFirst() }
            ?: return null
        row.isDownloaded = true
        if (actualBytes != null && actualBytes > 0) row.totalBytes = actualBytes
        attachmentBox.put(row)
        return row
    }

    // ------------------------------------------------------------------
    // Sanitization + traversal guard
    // ------------------------------------------------------------------

    /**
     * Sanitizes a transfer name into a single safe file-name component.
     * Null/blank/`.`-only names collapse to [DEFAULT_FILE_NAME]
     * (Dart `uriToFilename`'s `unknown` fallback).
     */
    fun sanitizeFileName(transferName: String?): String {
        val sanitized = transferName
            ?.replace(UNSAFE_NAME_CHARS, "_")
            ?.trim()
            .orEmpty()
        return if (sanitized in DOT_NAMES) DEFAULT_FILE_NAME else sanitized
    }

    /**
     * Sanitizes an attachment guid for use as a single directory-name
     * component. Unlike Dart's alnum-only `sanitizeGuid` (used for avatars)
     * this keeps guids like `msg-1_0` intact — the attachment layout needs
     * the raw guid to stay recognizable — while making traversal impossible.
     */
    fun sanitizeDirectoryName(guid: String): String {
        val sanitized = guid.replace(UNSAFE_NAME_CHARS, "_").trim()
        return if (sanitized in DOT_NAMES) DEFAULT_FILE_NAME else sanitized
    }

    /**
     * Path-traversal guard, ported from Dart's
     * `if (!canonicalize(file).startsWith(canonicalize(directory))) throw`.
     * `File.getCanonicalFile` normalizes `..` lexically even for files that
     * do not exist yet, so the check works before anything is written.
     */
    @Throws(IOException::class)
    internal fun ensureInside(directory: File, file: File) {
        val canonicalFile = file.canonicalFile.toPath()
        val canonicalDir = directory.canonicalFile.toPath()
        if (!canonicalFile.startsWith(canonicalDir)) {
            throw IOException("Path traversal detected for $file (expected under $directory)")
        }
    }
}

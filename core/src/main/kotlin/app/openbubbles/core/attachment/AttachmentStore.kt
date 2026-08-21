package app.openbubbles.core.attachment

import app.openbubbles.db.Attachment
import app.openbubbles.db.Attachment_
import io.objectbox.BoxStore
import io.objectbox.query.QueryBuilder
import java.io.File
import java.io.IOException
import java.io.RandomAccessFile
import java.nio.channels.FileChannel
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.nio.file.StandardOpenOption

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

        /** Sub-directory of [rootDir] holding CloudKit / live group photos. */
        const val GROUP_ICONS_DIR_NAME = "group_icons"

        /** File name used when an attachment has no usable transfer name. */
        const val DEFAULT_FILE_NAME = "unknown"

        /** Suffix reserved for an incomplete app-owned attachment download. */
        internal const val DOWNLOAD_PARTIAL_SUFFIX = ".openbubbles-partial"

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

    /** `<rootDir>/group_icons` — CloudKit and live group-photo cache. */
    val groupIconsDir: File
        get() = File(rootDir, GROUP_ICONS_DIR_NAME)

    /**
     * Canonical path for a chat's CloudKit group photo. The file may not
     * exist yet; callers create the parent on download. Version is part of
     * the name so a newer cloud photo never collides with a stale cache.
     */
    @Throws(IOException::class)
    fun groupIconFile(chatId: Long, recordId: String, version: Long?): File {
        val dir = groupIconsDir
        val name = sanitizeDirectoryName("$chatId-$recordId-${version ?: 0}") + ".png"
        val file = File(dir, name)
        ensureInside(dir, file)
        return file
    }

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
     * App-owned sibling used as the MMCS/Rust download destination. Keeping
     * it beside the canonical payload makes the final rename stay on one
     * filesystem, while the reserved suffix lets cleanup avoid user files.
     */
    @Throws(IOException::class)
    internal fun partialPathFor(attachment: Attachment): File {
        val finalFile = pathFor(attachment)
        val partial = File(finalFile.parentFile, ".${finalFile.name}$DOWNLOAD_PARTIAL_SUFFIX")
        ensureInside(finalFile.parentFile, partial)
        return partial
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
        if (isCompletePayload(primary, attachment.totalBytes, enforceExpectedSize = true)) return primary
        val converted = File(primary.path + ".png")
        // A converted image is deliberately a different encoding, so its
        // byte length cannot be compared with the source attachment size.
        if (isCompletePayload(converted, expectedBytes = null, enforceExpectedSize = false)) return converted
        return null
    }

    /** A completed payload must exist, be non-empty, and match a declared size. */
    internal fun isCompletePayload(
        file: File,
        expectedBytes: Long?,
        enforceExpectedSize: Boolean = true,
    ): Boolean {
        if (!file.isFile) return false
        val actualBytes = file.length()
        if (actualBytes <= 0L) return false
        return !enforceExpectedSize || expectedBytes == null || expectedBytes <= 0L || actualBytes == expectedBytes
    }

    /** Deletes only the reserved sibling owned by the attachment downloader. */
    @Throws(IOException::class)
    internal fun deleteDownloadPartial(attachment: Attachment) {
        if (attachment.guid == null) return
        Files.deleteIfExists(partialPathFor(attachment).toPath())
    }

    /**
     * Flushes a validated partial and atomically promotes it to the canonical
     * payload path. The fallback is used only on filesystems which explicitly
     * reject ATOMIC_MOVE; the files remain siblings and REPLACE_EXISTING still
     * avoids copying bytes through the canonical path.
     */
    @Throws(IOException::class)
    internal fun promoteCompletedDownload(attachment: Attachment, partial: File): File {
        val finalFile = pathFor(attachment)
        val expectedPartial = partialPathFor(attachment)
        if (partial.canonicalFile != expectedPartial.canonicalFile) {
            throw IOException("Attachment partial is not the owned sibling path")
        }
        if (!isCompletePayload(partial, attachment.totalBytes)) {
            val expected = attachment.totalBytes?.takeIf { it > 0L }
            val detail = if (expected != null) " (expected $expected bytes, found ${partial.length()})" else ""
            throw IOException("Attachment download is empty or incomplete$detail")
        }

        // The downloader contract returns only after closing its output. Open
        // the resulting file independently so the completed bytes reach the
        // filesystem before the canonical name becomes visible.
        RandomAccessFile(partial, "rw").use { it.fd.sync() }
        try {
            Files.move(
                partial.toPath(),
                finalFile.toPath(),
                StandardCopyOption.ATOMIC_MOVE,
                StandardCopyOption.REPLACE_EXISTING,
            )
        } catch (_: AtomicMoveNotSupportedException) {
            Files.move(partial.toPath(), finalFile.toPath(), StandardCopyOption.REPLACE_EXISTING)
        }
        syncDirectoryBestEffort(finalFile.parentFile)
        return finalFile
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
            partialPathFor(attachment),
            File(primary.path + ".png"),
            File(primary.path + ".thumbnail"),
            File(primary.path + ".png.thumbnail"),
        ).forEach { it.delete() }
        primary.parentFile?.takeIf { it.isDirectory && it.list().isNullOrEmpty() }?.delete()
    }

    /** Directory fsync is unavailable on some Android/filesystem pairs. */
    private fun syncDirectoryBestEffort(directory: File?) {
        if (directory == null) return
        try {
            FileChannel.open(directory.toPath(), StandardOpenOption.READ).use { it.force(true) }
        } catch (_: Exception) {
            // The file itself was synced before promotion; directory syncing
            // is an additional durability barrier where the platform permits.
        }
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

    /**
     * Repairs a stale downloaded flag when the canonical payload is absent or
     * invalid. Persisting the transition before a retry gives every observer
     * a real false -> true generation when the replacement is published.
     */
    fun markNotDownloaded(attachmentGuid: String): Attachment? {
        val row = attachmentBox.query()
            .equal(Attachment_.guid, attachmentGuid, QueryBuilder.StringOrder.CASE_SENSITIVE)
            .build().use { it.findFirst() }
            ?: return null
        if (row.isDownloaded) {
            row.isDownloaded = false
            attachmentBox.put(row)
        }
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

package app.openbubbles.core.backup

import app.openbubbles.db.Attachment
import app.openbubbles.db.Db
import app.openbubbles.db.Message
import io.objectbox.BoxStore
import java.io.BufferedOutputStream
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipException
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream

/**
 * Local backup/restore: a zip of the ObjectBox database plus the attachments
 * tree, with a manifest describing the archive.
 *
 * Layout inside the zip mirrors the on-disk layout under the app's data root
 * (the Flutter-era `<dataDir>/app_flutter` directory):
 *
 * ```
 * manifest.json                       — machine-readable archive description
 * objectbox/data.mdb                  — the single ObjectBox database file
 * attachments/<guid>/<transferName>   — attachment payloads
 * ```
 *
 * ## Snapshot strategy
 *
 * objectbox-java 4.3 exposes no client-side hot-backup API (the FlatBuffers
 * "backup" options are server-only), so [snapshot] copies the store's files
 * while app writes are quiesced:
 *
 *  1. all DB-file copying happens inside [StoreGate.withStorePaused], which
 *     the app implements to pause its writers (the app is single-process);
 *  2. an empty `store.runInTx { }` barrier runs first so every already
 *     committed transaction is flushed to `data.mdb` before the copy starts;
 *  3. `lock.mdb` (a pure file lock) is skipped.
 *
 * Caveat: writes that do not route through the app's gate can still land
 * mid-copy, so exports are safest while the app is idle; at worst the archive
 * misses the newest messages (the live DB keeps them).
 *
 * ## Restore strategy
 *
 * [restore] never streams into the live tree. It stages the source bytes to a
 * temp file, validates the manifest + zip structure (entry names, zip-slip
 * guard, db presence/size, CRC while extracting) and only then swaps the
 * staged `objectbox/` + `attachments/` directories into place, keeping the
 * previous data until the swap succeeds. Because the running process still
 * holds the old (open) store, callers must rebuild the graph or restart the
 * process after a successful restore — see the app-native facade.
 */
class BackupManager(
    /** Data root containing `objectbox/` and `attachments/` (e.g. `<dataDir>/app_flutter`). */
    private val rootDir: File,
    /** Live store supplier; used for counts and the flush barrier. */
    private val store: () -> BoxStore?,
    /** Pauses app writes while database files are being copied. */
    private val storeGate: StoreGate,
    /** Version of the calling app, recorded in the manifest. */
    private val appVersion: String? = null,
) {
    companion object {
        /** Zip entry name of the archive manifest. */
        const val MANIFEST_ENTRY = "manifest.json"

        /** Manifest format this build writes/accepts. */
        const val FORMAT_VERSION = 1

        /** The single ObjectBox database file (no separate WAL exists today). */
        private const val DATA_FILE = "data.mdb"

        /** Store-dir files that are pure locks/ephemeral and never archived. */
        private val SKIP_STORE_FILES = setOf("lock.mdb")

        private const val BUFFER = 64 * 1024
        private const val ATTACHMENTS_DIR = "attachments"
        private const val ATTACHMENTS_PREFIX = "$ATTACHMENTS_DIR/"
    }

    /** Result/summary of a snapshot or restore. */
    data class BackupInfo(
        val dateEpochMs: Long,
        val messageCount: Long,
        val attachmentCount: Long,
        val appVersion: String?,
    )

    private val storeDir: File get() = File(rootDir, Db.STORE_DIR_NAME)
    private val attachmentsDir: File get() = File(rootDir, ATTACHMENTS_DIR)

    /**
     * Writes a full backup (database + attachments + manifest) to [target].
     * Does NOT close [target] — the caller owns the stream (SAF pipes on
     * Android). [progress] receives short human-readable stage updates.
     */
    fun snapshot(target: OutputStream, progress: (String) -> Unit): Result<BackupInfo> = runCatching {
        val st = store() ?: error("store unavailable — cannot back up")
        progress("Counting records")
        val messageCount = st.boxFor(Message::class.java).count()
        val attachmentCount = st.boxFor(Attachment::class.java).count()

        val buffered = BufferedOutputStream(target, BUFFER)
        val zip = ZipOutputStream(buffered)
        var dbBytes = 0L
        try {
            progress("Copying database")
            dbBytes = storeGate.withStorePaused {
                // Barrier: an empty write tx flushes all committed state to
                // data.mdb before the file copy begins.
                st.runInTx { }
                val files = storeDir.listFiles()
                    ?.filter { it.isFile && it.name !in SKIP_STORE_FILES }
                    ?.sortedBy { it.name }
                    ?: error("store directory not found at $storeDir")
                if (files.none { it.name == DATA_FILE }) error("$DATA_FILE missing in $storeDir")
                var bytes = 0L
                files.forEach { file ->
                    zip.putNextEntry(ZipEntry("${Db.STORE_DIR_NAME}/${file.name}").apply {
                        time = file.lastModified()
                    })
                    file.inputStream().use { it.copyTo(zip, BUFFER) }
                    zip.closeEntry()
                    bytes += file.length()
                }
                bytes
            }

            var attachmentFiles = 0
            val attachmentFileTotal = attachmentsDir.walkTopDown().filter { it.isFile }.count()
            if (attachmentFileTotal > 0) progress("Adding attachments")
            attachmentsDir.walkTopDown().filter { it.isFile }.forEach { file ->
                val relative = file.relativeTo(attachmentsDir).path
                    .replace(File.separatorChar, '/')
                zip.putNextEntry(ZipEntry(ATTACHMENTS_PREFIX + relative))
                file.inputStream().use { it.copyTo(zip, BUFFER) }
                zip.closeEntry()
                attachmentFiles++
                progress("Adding attachments ($attachmentFiles/$attachmentFileTotal)")
            }

            val info = BackupInfo(
                dateEpochMs = System.currentTimeMillis(),
                messageCount = messageCount,
                attachmentCount = attachmentCount,
                appVersion = appVersion,
            )
            zip.putNextEntry(ZipEntry(MANIFEST_ENTRY))
            zip.write(manifestJson(info, dbBytes, attachmentFiles).toByteArray(Charsets.UTF_8))
            zip.closeEntry()

            zip.finish()
            buffered.flush() // finish() does not flush the wrapping buffer
            info
        } catch (t: Throwable) {
            runCatching { zip.finish() } // best effort; caller sees the failure
            runCatching { buffered.flush() }
            throw t
        }
    }

    /**
     * Replaces the database and attachments under [targetDir] with the
     * contents of [source]. Validates the archive completely (manifest,
     * structure, CRCs, db size) BEFORE anything in [targetDir] is touched,
     * stages to a temp directory, then swaps. On any failure the live data is
     * left byte-for-byte in place.
     *
     * After a success the caller MUST rebuild/restart the store (the running
     * process keeps the old file handles open); the returned [BackupInfo]
     * describes the restored archive.
     */
    fun restore(source: InputStream, targetDir: File): Result<BackupInfo> = runCatching {
        val staging = File(targetDir, ".ob-restore-staging")
        staging.deleteRecursively()
        if (!staging.mkdirs()) error("cannot create restore staging directory")

        try {
            // 1. Stage the raw bytes first — never decode straight into disk state.
            val stagedZip = File(staging, "backup.zip")
            stagedZip.outputStream().use { out -> source.copyTo(out, BUFFER) }

            ZipFile(stagedZip).use { zf ->
                // 2. Validate everything before touching the live tree.
                val manifestEntry = zf.getEntry(MANIFEST_ENTRY)
                    ?: error("not an OpenBubbles backup (missing $MANIFEST_ENTRY)")
                val manifest = zf.getInputStream(manifestEntry).use {
                    it.readBytes().toString(Charsets.UTF_8)
                }
                val parsed = parseManifest(manifest)

                val entries = zf.entries().asSequence().toList()
                val seen = HashSet<String>(entries.size)
                entries.forEach { entry ->
                    if (!seen.add(entry.name)) error("duplicate zip entry: ${entry.name}")
                    val name = entry.name
                    if (name == MANIFEST_ENTRY) return@forEach
                    val allowed = name.startsWith("${Db.STORE_DIR_NAME}/") ||
                        name.startsWith(ATTACHMENTS_PREFIX)
                    if (!allowed) error("unexpected entry in backup: $name")
                    // Zip-slip guard: no parent segments, resolved path stays
                    // inside the staging root.
                    if (name.split('/').any { it.isEmpty() || it == "." || it == ".." }) {
                        error("unsafe entry path in backup: $name")
                    }
                    val resolved = File(staging, name)
                    if (!resolved.canonicalPath.startsWith(staging.canonicalPath + File.separator)) {
                        error("unsafe entry path in backup: $name")
                    }
                }

                val dbEntry = zf.getEntry("${Db.STORE_DIR_NAME}/$DATA_FILE")
                    ?: error("backup contains no $DATA_FILE")
                if (dbEntry.size <= 0L) error("backup database file is empty")

                // 3. Extract (ZipFile verifies each entry's CRC while reading).
                entries.filter { it.name != MANIFEST_ENTRY }.forEach { entry ->
                    if (entry.isDirectory) return@forEach
                    val out = File(staging, entry.name)
                    out.parentFile?.mkdirs()
                    zf.getInputStream(entry).use { input ->
                        out.outputStream().use { output -> input.copyTo(output, BUFFER) }
                    }
                }

                // 4. Post-extract size check against the manifest.
                val stagedDb = File(staging, "${Db.STORE_DIR_NAME}/$DATA_FILE")
                if (parsed.dbBytes != null && stagedDb.length() != parsed.dbBytes) {
                    error("database file size mismatch — backup is corrupt")
                }

                // 5. Swap into place (rolls back on failure).
                swap(staging, targetDir)
                BackupInfo(
                    dateEpochMs = parsed.dateEpochMs,
                    messageCount = parsed.messageCount,
                    attachmentCount = parsed.attachmentCount,
                    appVersion = parsed.appVersion,
                )
            }
        } finally {
            staging.deleteRecursively()
        }
    }

    /**
     * Atomically-ish swaps staged `objectbox/` + `attachments/` into
     * [targetDir]. The previous directories are renamed aside first and
     * restored if any step fails; they are deleted only after the swap
     * succeeded.
     */
    private fun swap(staging: File, targetDir: File) {
        val stagedDb = File(staging, Db.STORE_DIR_NAME)
        val stagedAtt = File(staging, ATTACHMENTS_DIR)
        val liveDb = File(targetDir, Db.STORE_DIR_NAME)
        val liveAtt = File(targetDir, ATTACHMENTS_DIR)
        val asideDb = File(targetDir, "${Db.STORE_DIR_NAME}.pre-restore")
        val asideAtt = File(targetDir, "$ATTACHMENTS_DIR.pre-restore")
        asideDb.deleteRecursively()
        asideAtt.deleteRecursively()

        var dbAside = false
        var attAside = false
        var success = false
        try {
            if (liveDb.exists()) {
                if (!liveDb.renameTo(asideDb)) error("cannot move current database aside")
                dbAside = true
            }
            if (liveAtt.exists()) {
                if (!liveAtt.renameTo(asideAtt)) error("cannot move current attachments aside")
                attAside = true
            }
            if (!stagedDb.renameTo(liveDb)) error("cannot move restored database into place")
            // An archive without attachments means exactly that: the restored
            // state has none (liveAtt is already aside and will be dropped).
            if (stagedAtt.exists() && !stagedAtt.renameTo(liveAtt)) {
                error("cannot move restored attachments into place")
            }
            success = true
        } catch (t: Throwable) {
            // Roll back to the pre-restore state.
            liveDb.deleteRecursively()
            liveAtt.deleteRecursively()
            if (dbAside && !asideDb.renameTo(liveDb)) {
                throw IllegalStateException("restore rollback failed for database", t)
            }
            if (attAside && !asideAtt.renameTo(liveAtt)) {
                throw IllegalStateException("restore rollback failed for attachments", t)
            }
            throw t
        } finally {
            if (success) {
                asideDb.deleteRecursively()
                asideAtt.deleteRecursively()
            }
        }
    }

    // ------------------------------------------------------------------
    // Manifest (flat, hand-rolled — core keeps zero JSON dependencies)
    // ------------------------------------------------------------------

    private fun jsonEscape(s: String): String = buildString {
        for (c in s) when (c) {
            '"' -> append("\\\"")
            '\\' -> append("\\\\")
            '\n' -> append("\\n")
            '\r' -> append("\\r")
            '\t' -> append("\\t")
            else -> if (c < ' ') append("\\u%04x".format(c.code)) else append(c)
        }
    }

    private fun manifestJson(info: BackupInfo, dbBytes: Long, attachmentFileCount: Int): String =
        "{" +
            "\"formatVersion\":$FORMAT_VERSION," +
            "\"dateEpochMs\":${info.dateEpochMs}," +
            "\"messageCount\":${info.messageCount}," +
            "\"attachmentCount\":${info.attachmentCount}," +
            "\"attachmentFileCount\":$attachmentFileCount," +
            "\"dbBytes\":$dbBytes," +
            "\"appVersion\":${info.appVersion?.let { "\"${jsonEscape(it)}\"" } ?: "null"}" +
            "}"

    private class ParsedManifest(
        val dateEpochMs: Long,
        val messageCount: Long,
        val attachmentCount: Long,
        val appVersion: String?,
        val dbBytes: Long?,
    )

    private fun parseManifest(json: String): ParsedManifest {
        fun longField(key: String): Long? =
            Regex("\"$key\"\\s*:\\s*(-?\\d+)").find(json)?.groupValues?.get(1)?.toLongOrNull()
        val formatVersion = longField("formatVersion")
            ?: throw ZipException("manifest has no formatVersion")
        if (formatVersion != FORMAT_VERSION.toLong()) {
            throw ZipException("unsupported backup format version $formatVersion (expected $FORMAT_VERSION)")
        }
        return ParsedManifest(
            dateEpochMs = longField("dateEpochMs")
                ?: throw ZipException("manifest has no dateEpochMs"),
            messageCount = longField("messageCount")
                ?: throw ZipException("manifest has no messageCount"),
            attachmentCount = longField("attachmentCount")
                ?: throw ZipException("manifest has no attachmentCount"),
            appVersion = Regex("\"appVersion\"\\s*:\\s*(null|\"((?:[^\"\\\\]|\\\\.)*)\")")
                .find(json)?.groupValues?.get(2),
            dbBytes = longField("dbBytes"),
        )
    }
}

/**
 * Pauses app-level writes for the duration of [withStorePaused]. Supplied by
 * the app; the backup code only requires that DB writes do not run inside the
 * block (single-process apps can enforce this with a lock around their write
 * paths, or accept the documented newest-messages caveat).
 */
interface StoreGate {
    fun <T> withStorePaused(block: () -> T): T
}

/** [StoreGate] that pauses nothing — tests and single-writer contexts. */
object PassThroughStoreGate : StoreGate {
    override fun <T> withStorePaused(block: () -> T): T = block()
}

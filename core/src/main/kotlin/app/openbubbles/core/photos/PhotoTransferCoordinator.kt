package app.openbubbles.core.photos

import java.io.File
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.FileOutputStream
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.MessageDigest
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * Owns local Photos transfer semantics independently of the Apple protocol:
 * durable state first, a `.part` file during transfer, then atomic promotion.
 * Selecting a JPEG only creates a restorable queued intent; the separate
 * upload action crosses the Apple protocol boundary.
 */
class PhotoTransferCoordinator(
    private val port: PhotosPort,
    private val catalog: PhotosCatalog,
    private val previewRoot: File,
    private val uploadRoot: File = File(previewRoot.parentFile ?: previewRoot, "uploads"),
    private val originalRoot: File = File(previewRoot.parentFile ?: previewRoot, "originals"),
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val nowMs: () -> Long = System::currentTimeMillis,
) {
    private val transferLocks = ConcurrentHashMap<String, Mutex>()

    suspend fun downloadPreview(
        asset: PhotoSummary,
        onProgress: (PhotoTransfer) -> Unit = {},
    ): PhotoTransfer {
        val id = downloadId(asset.id, PhotoResourceKind.Preview)
        return transferLocks.getOrPut(id) { Mutex() }.withLock {
            runDownload(
                id = id,
                asset = asset,
                resourceKind = PhotoResourceKind.Preview,
                root = previewRoot,
                extension = previewExtension(asset.mediaKind),
                expectedBytes = asset.previewSize,
                mimeType = previewMimeType(asset.mediaKind),
                validate = { validPreviewFile(it, asset.mediaKind) },
                download = { path, progress -> port.downloadPreview(asset, path, progress) },
                onProgress = onProgress,
            )
        }
    }

    suspend fun downloadOriginal(
        asset: PhotoSummary,
        onProgress: (PhotoTransfer) -> Unit = {},
    ): PhotoTransfer {
        val id = downloadId(asset.id, PhotoResourceKind.Original)
        return transferLocks.getOrPut(id) { Mutex() }.withLock {
            runDownload(
                id = id,
                asset = asset,
                resourceKind = PhotoResourceKind.Original,
                root = originalRoot,
                extension = originalExtension(asset),
                expectedBytes = asset.originalSize,
                mimeType = originalMimeType(asset),
                validate = { validOriginalFile(it, asset.mediaKind) },
                download = { path, progress -> port.downloadOriginal(asset, path, progress) },
                onProgress = onProgress,
            )
        }
    }

    /** Keep a Live Photo's video companion private and distinct from its still. */
    suspend fun downloadLivePhotoVideo(
        asset: PhotoSummary,
        onProgress: (PhotoTransfer) -> Unit = {},
    ): PhotoTransfer {
        val id = downloadId(asset.id, PhotoResourceKind.LivePhotoVideo)
        return transferLocks.getOrPut(id) { Mutex() }.withLock {
            runDownload(
                id = id,
                asset = asset,
                resourceKind = PhotoResourceKind.LivePhotoVideo,
                root = originalRoot,
                extension = "mov",
                expectedBytes = asset.livePhotoVideoSize,
                mimeType = "video/quicktime",
                validate = { validOriginalFile(it, PhotoMediaKind.Video) },
                download = { path, progress -> port.downloadLivePhotoVideo(asset, path, progress) },
                onProgress = onProgress,
            )
        }
    }

    /**
     * A completed catalog row is only a cache hint. Revalidate its promoted
     * file before restoring it into UI state so eviction or corruption cannot
     * leave a permanently blank tile/viewer.
     */
    suspend fun revalidateCompletedDownload(
        asset: PhotoSummary,
        transfer: PhotoTransfer,
    ): PhotoTransfer {
        if (
            transfer.direction != PhotoTransferDirection.Download ||
            transfer.assetId != asset.id ||
            transfer.state != PhotoTransferState.Succeeded
        ) return transfer
        val expectedBytes = when (transfer.resourceKind) {
            PhotoResourceKind.Preview -> asset.previewSize
            PhotoResourceKind.Original -> asset.originalSize
            PhotoResourceKind.LivePhotoVideo -> asset.livePhotoVideoSize
        }
        val restored = withContext(ioDispatcher) {
            val file = File(transfer.localPath)
            val valid = expectedBytes != null &&
                expectedBytes in 1..downloadByteLimit(transfer.resourceKind) &&
                file.length() == expectedBytes && when (transfer.resourceKind) {
                PhotoResourceKind.Preview -> validPreviewFile(file, asset.mediaKind)
                PhotoResourceKind.Original -> validOriginalFile(file, asset.mediaKind)
                PhotoResourceKind.LivePhotoVideo -> validOriginalFile(file, PhotoMediaKind.Video)
            }
            if (!valid) {
                null
            } else if (transfer.resourceKind == PhotoResourceKind.Original) {
                val destination = originalDestination(file, asset, originalRoot)
                if (file.absolutePath != destination.file.absolutePath) {
                    destination.file.parentFile?.mkdirs()
                    promote(file, destination.file)
                }
                transfer.copy(
                    localPath = destination.file.absolutePath,
                    mimeType = destination.mimeType,
                    updatedAtMs = if (
                        transfer.localPath != destination.file.absolutePath ||
                        transfer.mimeType != destination.mimeType
                    ) nowMs() else transfer.updatedAtMs,
                )
            } else {
                transfer
            }
        }
        if (restored != null) {
            if (restored != transfer) catalog.putTransfer(restored)
            return restored
        }

        return transfer.copy(
            state = PhotoTransferState.Queued,
            bytesDone = 0,
            totalBytes = expectedBytes ?: transfer.totalBytes,
            lastError = null,
            updatedAtMs = nowMs(),
        ).also { catalog.putTransfer(it) }
    }

    private suspend fun runDownload(
        id: String,
        asset: PhotoSummary,
        resourceKind: PhotoResourceKind,
        root: File,
        extension: String,
        expectedBytes: Long?,
        mimeType: String?,
        validate: (File) -> Boolean,
        download: suspend (String, (Long, Long) -> Unit) -> Result<Unit>,
        onProgress: (PhotoTransfer) -> Unit,
    ): PhotoTransfer {
        val timestamp = nowMs()
        val initialFinalFile = File(root, "${safeKey(asset.id)}.$extension")
        val partialFile = File(initialFinalFile.path + ".part")
        val existing = catalog.transfer(id)
        val base = PhotoTransfer(
            id = id,
            assetId = asset.id,
            direction = PhotoTransferDirection.Download,
            resourceKind = resourceKind,
            localPath = initialFinalFile.absolutePath,
            filename = asset.filename,
            mimeType = mimeType,
            state = PhotoTransferState.Queued,
            totalBytes = expectedBytes ?: 0,
            attemptCount = existing?.attemptCount ?: 0,
            createdAtMs = existing?.createdAtMs ?: timestamp,
            updatedAtMs = timestamp,
        )

        if (expectedBytes == null || asset.mediaKind == PhotoMediaKind.Unknown) {
            return base.copy(
                state = PhotoTransferState.Failed,
                lastError = "No ${resourceKind.name.lowercase()} resource was advertised for this asset",
            ).also { catalog.putTransfer(it) }
        }
        val byteLimit = downloadByteLimit(resourceKind)
        if (expectedBytes <= 0 || expectedBytes > byteLimit) {
            return base.copy(
                state = PhotoTransferState.Failed,
                lastError = "The advertised ${resourceKind.name.lowercase()} size exceeds its supported byte limits",
            ).also { catalog.putTransfer(it) }
        }

        val existingFile = sequenceOf(existing?.localPath?.let(::File), initialFinalFile)
            .filterNotNull()
            .distinctBy(File::getAbsolutePath)
            .firstOrNull { file -> file.length() == expectedBytes && validate(file) }
        if (existingFile != null) {
            val destination = if (resourceKind == PhotoResourceKind.Original) {
                originalDestination(existingFile, asset, root)
            } else {
                OriginalDestination(existingFile, mimeType)
            }
            if (existingFile.absolutePath != destination.file.absolutePath) {
                destination.file.parentFile?.mkdirs()
                promote(existingFile, destination.file)
            }
            return base.copy(
                localPath = destination.file.absolutePath,
                mimeType = destination.mimeType,
                state = PhotoTransferState.Succeeded,
                bytesDone = destination.file.length(),
                totalBytes = destination.file.length(),
            ).also { catalog.putTransfer(it) }
        }
        root.mkdirs()
        partialFile.delete()
        val current = AtomicReference(base.copy(
            state = PhotoTransferState.Running,
            attemptCount = base.attemptCount + 1,
        ))

        try {
            catalog.putTransfer(current.get())
            onProgress(current.get())
            val result = download(partialFile.absolutePath) { done, total ->
                val progress = current.updateAndGet { transfer ->
                    transfer.copy(
                        bytesDone = done.coerceAtLeast(0),
                        totalBytes = total.takeIf { it > 0 } ?: transfer.totalBytes,
                        updatedAtMs = nowMs(),
                    )
                }
                onProgress(progress)
            }
            result.getOrThrow()
            check(partialFile.isFile) { "Photo download completed without a file" }
            check(partialFile.length() == expectedBytes) {
                "Downloaded ${resourceKind.name.lowercase()} did not match its expected byte size"
            }
            check(validate(partialFile)) {
                "Downloaded ${resourceKind.name.lowercase()} did not match its expected media format"
            }
            val destination = if (resourceKind == PhotoResourceKind.Original) {
                originalDestination(partialFile, asset, root)
            } else {
                OriginalDestination(initialFinalFile, mimeType)
            }
            promote(partialFile, destination.file)
            current.updateAndGet {
                it.copy(
                    localPath = destination.file.absolutePath,
                    mimeType = destination.mimeType,
                    state = PhotoTransferState.Succeeded,
                    bytesDone = destination.file.length(),
                    totalBytes = destination.file.length(),
                    lastError = null,
                    updatedAtMs = nowMs(),
                )
            }
        } catch (cancelled: CancellationException) {
            partialFile.delete()
            current.updateAndGet {
                it.copy(
                    state = PhotoTransferState.Queued,
                    lastError = "Transfer interrupted",
                    updatedAtMs = nowMs(),
                )
            }
            withContext(NonCancellable) {
                catalog.putTransfer(current.get())
                onProgress(current.get())
            }
            throw cancelled
        } catch (error: Throwable) {
            partialFile.delete()
            current.updateAndGet {
                it.copy(
                    state = PhotoTransferState.Failed,
                    lastError = error.message ?: "Photo download failed",
                    updatedAtMs = nowMs(),
                )
            }
        }
        val completed = current.get()
        withContext(NonCancellable) {
            catalog.putTransfer(completed)
            onProgress(completed)
        }
        return completed
    }

    suspend fun planUpload(
        sourcePath: String,
        previewPath: String,
        filename: String?,
        mimeType: String?,
        orientation: Int,
        capturedAtMs: Long? = null,
        timeZone: PhotoTimeZone? = null,
        origin: PhotoTransferOrigin = PhotoTransferOrigin.Manual,
    ): PhotoTransfer = withContext(ioDispatcher) {
        val source = File(sourcePath)
        val preview = File(previewPath)
        require(source.isFile) { "Upload source does not exist" }
        require(preview.isFile) { "Upload preview does not exist" }
        require(source.length() > 0) { "Upload source is empty" }
        require(preview.length() > 0) { "Upload preview is empty" }
        require(mimeType == "image/jpeg") {
            "The first iCloud Photos upload supports JPEG images only"
        }
        require(orientation in 1..8) { "Upload orientation is invalid" }
        val staged = stageUploadSource(
            source,
            preview,
            UploadMetadata(orientation, capturedAtMs, timeZone?.takeIf { it.name.isNotBlank() }),
        )
        val timestamp = nowMs()
        val id = "upload:${staged.digest}"
        val existing = catalog.transfer(id)
        if (existing?.state == PhotoTransferState.Succeeded) {
            return@withContext existing
        }
        PhotoTransfer(
            id = id,
            assetId = null,
            direction = PhotoTransferDirection.Upload,
            resourceKind = PhotoResourceKind.Original,
            localPath = staged.file.absolutePath,
            filename = filename ?: source.name,
            mimeType = mimeType,
            state = PhotoTransferState.Queued,
            bytesDone = 0,
            totalBytes = staged.file.length() + staged.previewFile.length(),
            attemptCount = existing?.attemptCount ?: 0,
            lastError = null,
            createdAtMs = existing?.createdAtMs ?: timestamp,
            updatedAtMs = timestamp,
            origin = existing?.origin ?: origin,
        ).also { catalog.putTransfer(it) }
    }

    suspend fun upload(
        transfer: PhotoTransfer,
        onProgress: (PhotoTransfer) -> Unit = {},
    ): PhotoTransfer = transferLocks.getOrPut(transfer.id) { Mutex() }.withLock {
        require(transfer.direction == PhotoTransferDirection.Upload) { "Transfer is not an upload" }
        if (transfer.state == PhotoTransferState.Succeeded) return@withLock transfer
        var current = transfer.copy(
            state = PhotoTransferState.Running,
            attemptCount = transfer.attemptCount + 1,
            lastError = null,
            updatedAtMs = nowMs(),
        )
        try {
            catalog.putTransfer(current)
            onProgress(current)
            val source = File(checkNotNull(transfer.localPath))
            val preview = uploadCompanion(source, ".preview.jpg")
            val metadata = readUploadMetadata(uploadCompanion(source, ".metadata"))
            require(source.isFile && preview.isFile) { "Staged Photos upload is incomplete" }
            val receipt = port.uploadJpeg(
                originalPath = source.absolutePath,
                previewPath = preview.absolutePath,
                filename = transfer.filename ?: source.name,
                capturedAtMs = metadata.capturedAtMs,
                orientation = metadata.orientation,
                fallbackTimeZone = metadata.timeZone,
            ).getOrThrow()
            current = current.copy(
                assetId = receipt.masterId,
                state = PhotoTransferState.Succeeded,
                bytesDone = current.totalBytes,
                lastError = null,
                updatedAtMs = nowMs(),
            )
        } catch (cancelled: CancellationException) {
            current = current.copy(
                state = PhotoTransferState.Queued,
                lastError = "Transfer interrupted",
                updatedAtMs = nowMs(),
            )
            withContext(NonCancellable) {
                catalog.putTransfer(current)
                onProgress(current)
            }
            throw cancelled
        } catch (error: Throwable) {
            current = current.copy(
                state = PhotoTransferState.Failed,
                lastError = error.message ?: "iCloud Photos upload failed",
                updatedAtMs = nowMs(),
            )
        }
        withContext(NonCancellable) {
            catalog.putTransfer(current)
            onProgress(current)
        }
        current
    }

    private fun stageUploadSource(
        source: File,
        preview: File,
        metadata: UploadMetadata,
    ): StagedUpload {
        uploadRoot.mkdirs()
        val partial = File(uploadRoot, ".${UUID.randomUUID()}.part")
        val digest = MessageDigest.getInstance("SHA-256")
        try {
            source.inputStream().use { input ->
                FileOutputStream(partial).use { output ->
                    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                    while (true) {
                        val count = input.read(buffer)
                        if (count < 0) break
                        if (count == 0) continue
                        digest.update(buffer, 0, count)
                        output.write(buffer, 0, count)
                    }
                    output.fd.sync()
                }
            }
            check(partial.length() > 0) { "Upload source is empty" }
            val digestHex = digest.digest().toHex()
            val destination = File(uploadRoot, "$digestHex.original.jpg")
            if (destination.isFile && destination.length() == partial.length()) {
                partial.delete()
            } else {
                promote(partial, destination)
            }
            val previewDestination = uploadCompanion(destination, ".preview.jpg")
            copyDurable(preview, previewDestination)
            writeUploadMetadata(uploadCompanion(destination, ".metadata"), metadata)
            return StagedUpload(destination, previewDestination, digestHex)
        } catch (error: Throwable) {
            partial.delete()
            throw error
        }
    }

    companion object {
        // Keep these in sync with the Rust facade's bounded Photos writers.
        internal const val MAX_PHOTO_PREVIEW_BYTES = 32L * 1024 * 1024
        internal const val MAX_PHOTO_ORIGINAL_BYTES = 1024L * 1024 * 1024

        fun downloadId(assetId: String, kind: PhotoResourceKind): String =
            "download:${kind.name.lowercase()}:$assetId"

        private fun downloadByteLimit(resourceKind: PhotoResourceKind): Long = when (resourceKind) {
            PhotoResourceKind.Preview -> MAX_PHOTO_PREVIEW_BYTES
            PhotoResourceKind.Original,
            PhotoResourceKind.LivePhotoVideo,
            -> MAX_PHOTO_ORIGINAL_BYTES
        }

        private fun safeKey(value: String): String = MessageDigest.getInstance("SHA-256")
            .digest(value.toByteArray(Charsets.UTF_8))
            .toHex()

        private fun originalDestination(
            file: File,
            asset: PhotoSummary,
            root: File,
        ): OriginalDestination {
            val format = sniffPhotoOriginalFormat(file, asset.mediaKind)
            val filenameExtension = filenameExtension(asset.filename)
            val extension = when {
                format == null -> originalExtension(asset)
                filenameExtension in format.compatibleExtensions -> checkNotNull(filenameExtension)
                else -> format.extension
            }
            return OriginalDestination(
                file = File(root, "${safeKey(asset.id)}.$extension"),
                mimeType = format?.mimeType ?: originalMimeType(asset),
            )
        }

        private fun promote(source: File, destination: File) {
            try {
                Files.move(
                    source.toPath(),
                    destination.toPath(),
                    StandardCopyOption.ATOMIC_MOVE,
                    StandardCopyOption.REPLACE_EXISTING,
                )
            } catch (_: AtomicMoveNotSupportedException) {
                Files.move(
                    source.toPath(),
                    destination.toPath(),
                    StandardCopyOption.REPLACE_EXISTING,
                )
            }
        }

        private fun copyDurable(source: File, destination: File) {
            val partial = File(destination.parentFile, ".${UUID.randomUUID()}.part")
            try {
                source.inputStream().use { input ->
                    FileOutputStream(partial).use { output ->
                        input.copyTo(output)
                        output.fd.sync()
                    }
                }
                check(partial.length() > 0) { "Upload preview is empty" }
                promote(partial, destination)
            } catch (error: Throwable) {
                partial.delete()
                throw error
            }
        }

        /**
         * Sidecar format. Version 1 stored orientation and capture time;
         * version 2 adds the device time zone. Readers accept both so rows
         * staged before an upgrade still upload.
         */
        internal const val UPLOAD_METADATA_VERSION = 2

        private fun writeUploadMetadata(file: File, metadata: UploadMetadata) {
            val partial = File(file.parentFile, ".${UUID.randomUUID()}.part")
            try {
                FileOutputStream(partial).use { stream ->
                    DataOutputStream(stream).use { output ->
                        output.writeInt(UPLOAD_METADATA_VERSION)
                        output.writeInt(metadata.orientation)
                        output.writeLong(metadata.capturedAtMs ?: -1L)
                        val zone = metadata.timeZone
                        output.writeBoolean(zone != null)
                        if (zone != null) {
                            output.writeUTF(zone.name)
                            output.writeInt(zone.offsetSeconds)
                        }
                        output.flush()
                        stream.fd.sync()
                    }
                }
                promote(partial, file)
            } catch (error: Throwable) {
                partial.delete()
                throw error
            }
        }

        internal fun readUploadMetadata(file: File): UploadMetadata =
            DataInputStream(file.inputStream().buffered()).use { input ->
                val version = input.readInt()
                check(version in 1..UPLOAD_METADATA_VERSION) { "Unsupported Photos upload metadata" }
                val orientation = input.readInt()
                require(orientation in 1..8) { "Upload orientation is invalid" }
                val capturedAtMs = input.readLong().takeIf { it >= 0 }
                val timeZone = if (version >= 2 && input.readBoolean()) {
                    val name = input.readUTF()
                    val offsetSeconds = input.readInt()
                    PhotoTimeZone(name, offsetSeconds).takeIf {
                        name.isNotBlank() && offsetSeconds in -18 * 3600..18 * 3600
                    }
                } else {
                    null
                }
                UploadMetadata(orientation, capturedAtMs, timeZone)
            }

        private fun uploadCompanion(original: File, suffix: String): File {
            val prefix = original.name.removeSuffix(".original.jpg")
            return File(original.parentFile, prefix + suffix)
        }
    }
}

private data class StagedUpload(val file: File, val previewFile: File, val digest: String)

private data class OriginalDestination(val file: File, val mimeType: String?)

internal data class PhotoOriginalFormat(
    val extension: String,
    val mimeType: String,
    val compatibleExtensions: Set<String> = setOf(extension),
)

/** What the staged sidecar remembers for the later explicit upload. */
internal data class UploadMetadata(
    val orientation: Int,
    val capturedAtMs: Long?,
    val timeZone: PhotoTimeZone? = null,
)

private fun ByteArray.toHex(): String =
    joinToString(separator = "") { byte -> "%02x".format(byte.toInt() and 0xff) }

private fun previewExtension(mediaKind: PhotoMediaKind): String = when (mediaKind) {
    PhotoMediaKind.Image -> "jpg"
    PhotoMediaKind.Video -> "mov"
    PhotoMediaKind.Unknown -> "preview"
}

private fun previewMimeType(mediaKind: PhotoMediaKind): String? = when (mediaKind) {
    PhotoMediaKind.Image -> "image/jpeg"
    PhotoMediaKind.Video -> "video/quicktime"
    PhotoMediaKind.Unknown -> null
}

private fun filenameExtension(filename: String?): String? = filename
        ?.substringAfterLast('.', missingDelimiterValue = "")
        ?.lowercase()
        ?.takeIf { it.matches(Regex("[a-z0-9]{1,10}")) }

private fun originalExtension(asset: PhotoSummary): String {
    return filenameExtension(asset.filename) ?: when (asset.mediaKind) {
        PhotoMediaKind.Image -> "image"
        PhotoMediaKind.Video -> "mov"
        PhotoMediaKind.Unknown -> "original"
    }
}

private fun originalMimeType(asset: PhotoSummary): String? = when (asset.mediaKind) {
    PhotoMediaKind.Image -> when (originalExtension(asset)) {
        "jpg", "jpeg" -> "image/jpeg"
        "heic", "heif" -> "image/heic"
        "png" -> "image/png"
        "gif" -> "image/gif"
        "webp" -> "image/webp"
        "dng" -> "image/x-adobe-dng"
        else -> "image/*"
    }
    PhotoMediaKind.Video -> when (originalExtension(asset)) {
        "mp4", "m4v" -> "video/mp4"
        else -> "video/quicktime"
    }
    PhotoMediaKind.Unknown -> null
}

/**
 * Bounded media sniffing keeps CloudKit filenames advisory: encrypted or
 * misleading metadata cannot decide the extension of bytes we have verified.
 */
internal fun sniffPhotoOriginalFormat(file: File, mediaKind: PhotoMediaKind): PhotoOriginalFormat? {
    if (!file.isFile || mediaKind == PhotoMediaKind.Unknown) return null
    val header = ByteArray(64 * 1024)
    val count = runCatching { file.inputStream().use { it.read(header) } }.getOrDefault(-1)
    if (count <= 0) return null

    if (mediaKind == PhotoMediaKind.Image) {
        when {
            isJpeg(header, count) -> return PhotoOriginalFormat(
                extension = "jpg",
                mimeType = "image/jpeg",
                compatibleExtensions = setOf("jpg", "jpeg"),
            )
            isPng(header, count) -> return PhotoOriginalFormat("png", "image/png")
            isGif(header, count) -> return PhotoOriginalFormat("gif", "image/gif")
            isWebp(header, count) -> return PhotoOriginalFormat("webp", "image/webp")
            isTiff(header, count) -> return if (isDng(header, count)) {
                PhotoOriginalFormat("dng", "image/x-adobe-dng")
            } else {
                PhotoOriginalFormat("tiff", "image/tiff", setOf("tif", "tiff"))
            }
            count >= 2 && header[0] == 'B'.code.toByte() && header[1] == 'M'.code.toByte() ->
                return PhotoOriginalFormat("bmp", "image/bmp")
        }
    }

    val brands = isoBmffBrands(header, count)
    if (brands.isEmpty()) return null
    return when (mediaKind) {
        PhotoMediaKind.Image -> when {
            brands.any { it == "avif" || it == "avis" } ->
                PhotoOriginalFormat("avif", "image/avif")
            brands.any { it in HEIF_BRANDS } -> PhotoOriginalFormat(
                extension = "heic",
                mimeType = "image/heic",
                compatibleExtensions = setOf("heic", "heif"),
            )
            else -> null
        }
        PhotoMediaKind.Video -> when {
            brands.first() == "qt  " -> PhotoOriginalFormat("mov", "video/quicktime")
            brands.any { it.startsWith("3gp") } -> PhotoOriginalFormat("3gp", "video/3gpp")
            else -> PhotoOriginalFormat("mp4", "video/mp4", setOf("mp4", "m4v"))
        }
        PhotoMediaKind.Unknown -> null
    }
}

private val HEIF_BRANDS = setOf(
    "heic", "heix", "hevc", "hevx", "mif1", "msf1", "heif", "heim", "heis", "hevm", "hevs",
)

private fun isoBmffBrands(header: ByteArray, count: Int): List<String> {
    if (count < 12 || String(header, 4, 4, Charsets.US_ASCII) != "ftyp") return emptyList()
    val declaredSize = unsignedInt(header, 0, littleEndian = false)
    val limit = if (declaredSize in 16L..count.toLong()) declaredSize.toInt() else count
    return buildList {
        add(String(header, 8, 4, Charsets.US_ASCII))
        var offset = 16
        while (offset + 4 <= limit) {
            add(String(header, offset, 4, Charsets.US_ASCII))
            offset += 4
        }
    }
}

private fun isDng(header: ByteArray, count: Int): Boolean {
    if (count < 8) return false
    val littleEndian = header[0] == 'I'.code.toByte()
    val firstDirectoryOffset = unsignedInt(header, 4, littleEndian)
    if (firstDirectoryOffset > count.toLong() - 2) return false
    val directoryOffset = firstDirectoryOffset.toInt()
    val entries = unsignedShort(header, directoryOffset, littleEndian)
    repeat(entries) { index ->
        val entryOffset = directoryOffset + 2 + index * 12
        if (entryOffset + 12 > count) return false
        if (unsignedShort(header, entryOffset, littleEndian) == 0xc612) return true
    }
    return false
}

private fun unsignedShort(header: ByteArray, offset: Int, littleEndian: Boolean): Int {
    val first = header[offset].toInt() and 0xff
    val second = header[offset + 1].toInt() and 0xff
    return if (littleEndian) first or (second shl 8) else (first shl 8) or second
}

private fun unsignedInt(header: ByteArray, offset: Int, littleEndian: Boolean): Long {
    val first = unsignedShort(header, offset, littleEndian).toLong()
    val second = unsignedShort(header, offset + 2, littleEndian).toLong()
    return if (littleEndian) first or (second shl 16) else (first shl 16) or second
}

private fun validPreviewFile(file: File, mediaKind: PhotoMediaKind): Boolean {
    if (!file.isFile) return false
    val header = ByteArray(12)
    val count = runCatching { file.inputStream().use { it.read(header) } }.getOrDefault(-1)
    return when (mediaKind) {
        PhotoMediaKind.Image -> count >= 3 &&
            header[0] == 0xff.toByte() && header[1] == 0xd8.toByte() && header[2] == 0xff.toByte()
        PhotoMediaKind.Video -> count >= 8 &&
            header[4] == 'f'.code.toByte() && header[5] == 't'.code.toByte() &&
            header[6] == 'y'.code.toByte() && header[7] == 'p'.code.toByte()
        PhotoMediaKind.Unknown -> false
    }
}

private fun validOriginalFile(file: File, mediaKind: PhotoMediaKind): Boolean {
    if (!file.isFile) return false
    val header = ByteArray(12)
    val count = runCatching { file.inputStream().use { it.read(header) } }.getOrDefault(-1)
    val isoBmff = count >= 8 &&
        header[4] == 'f'.code.toByte() && header[5] == 't'.code.toByte() &&
        header[6] == 'y'.code.toByte() && header[7] == 'p'.code.toByte()
    return when (mediaKind) {
        PhotoMediaKind.Image -> isJpeg(header, count) ||
            isPng(header, count) ||
            isGif(header, count) ||
            isWebp(header, count) ||
            isTiff(header, count) ||
            (count >= 2 && header[0] == 'B'.code.toByte() && header[1] == 'M'.code.toByte()) ||
            isoBmff
        PhotoMediaKind.Video -> isoBmff
        PhotoMediaKind.Unknown -> false
    }
}

private fun isJpeg(header: ByteArray, count: Int): Boolean = count >= 3 &&
    header[0] == 0xff.toByte() && header[1] == 0xd8.toByte() && header[2] == 0xff.toByte()

private fun isPng(header: ByteArray, count: Int): Boolean = count >= 8 &&
    header.copyOfRange(0, 8).contentEquals(
        byteArrayOf(
            0x89.toByte(),
            'P'.code.toByte(),
            'N'.code.toByte(),
            'G'.code.toByte(),
            '\r'.code.toByte(),
            '\n'.code.toByte(),
            0x1a,
            '\n'.code.toByte(),
        ),
    )

private fun isGif(header: ByteArray, count: Int): Boolean = count >= 6 &&
    String(header, 0, 6, Charsets.US_ASCII) in setOf("GIF87a", "GIF89a")

private fun isWebp(header: ByteArray, count: Int): Boolean = count >= 12 &&
    String(header, 0, 4, Charsets.US_ASCII) == "RIFF" &&
    String(header, 8, 4, Charsets.US_ASCII) == "WEBP"

private fun isTiff(header: ByteArray, count: Int): Boolean {
    if (count < 4) return false
    val marker = header.copyOfRange(0, 4)
    return marker.contentEquals(byteArrayOf('I'.code.toByte(), 'I'.code.toByte(), '*'.code.toByte(), 0)) ||
        marker.contentEquals(byteArrayOf('M'.code.toByte(), 'M'.code.toByte(), 0, '*'.code.toByte()))
}

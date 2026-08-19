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
import kotlinx.coroutines.Dispatchers
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
    private val nowMs: () -> Long = System::currentTimeMillis,
) {
    private val transferLocks = ConcurrentHashMap<String, Mutex>()

    suspend fun downloadPreview(
        asset: PhotoSummary,
        onProgress: (PhotoTransfer) -> Unit = {},
    ): PhotoTransfer {
        val id = downloadId(asset.id, PhotoResourceKind.Preview)
        return transferLocks.getOrPut(id) { Mutex() }.withLock {
            runPreviewDownload(id, asset, onProgress)
        }
    }

    private suspend fun runPreviewDownload(
        id: String,
        asset: PhotoSummary,
        onProgress: (PhotoTransfer) -> Unit,
    ): PhotoTransfer {
        val timestamp = nowMs()
        val extension = when (asset.mediaKind) {
            PhotoMediaKind.Image -> "jpg"
            PhotoMediaKind.Video -> "mov"
            PhotoMediaKind.Unknown -> "preview"
        }
        val finalFile = File(previewRoot, "${safeKey(asset.id)}.$extension")
        val partialFile = File(finalFile.path + ".part")
        val existing = catalog.transfer(id)
        val base = PhotoTransfer(
            id = id,
            assetId = asset.id,
            direction = PhotoTransferDirection.Download,
            resourceKind = PhotoResourceKind.Preview,
            localPath = finalFile.absolutePath,
            filename = asset.filename,
            mimeType = when (asset.mediaKind) {
                PhotoMediaKind.Image -> "image/jpeg"
                PhotoMediaKind.Video -> "video/quicktime"
                PhotoMediaKind.Unknown -> null
            },
            state = PhotoTransferState.Queued,
            totalBytes = asset.previewSize ?: 0,
            attemptCount = existing?.attemptCount ?: 0,
            createdAtMs = existing?.createdAtMs ?: timestamp,
            updatedAtMs = timestamp,
        )

        if (validPreviewFile(finalFile, asset.mediaKind)) {
            return base.copy(
                state = PhotoTransferState.Succeeded,
                bytesDone = finalFile.length(),
                totalBytes = finalFile.length(),
            ).also { catalog.putTransfer(it) }
        }
        if (asset.previewSize == null || asset.mediaKind == PhotoMediaKind.Unknown) {
            return base.copy(
                state = PhotoTransferState.Failed,
                lastError = "No preview resource was advertised for this asset",
            ).also { catalog.putTransfer(it) }
        }

        previewRoot.mkdirs()
        partialFile.delete()
        val current = AtomicReference(base.copy(
            state = PhotoTransferState.Running,
            attemptCount = base.attemptCount + 1,
        ))
        catalog.putTransfer(current.get())
        onProgress(current.get())

        try {
            val result = port.downloadPreview(asset, partialFile.absolutePath) { done, total ->
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
            check(partialFile.isFile) { "Preview download completed without a file" }
            check(validPreviewFile(partialFile, asset.mediaKind)) {
                "Downloaded preview did not match its expected media format"
            }
            promote(partialFile, finalFile)
            current.updateAndGet {
                it.copy(
                    state = PhotoTransferState.Succeeded,
                    bytesDone = finalFile.length(),
                    totalBytes = finalFile.length(),
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
            catalog.putTransfer(current.get())
            throw cancelled
        } catch (error: Throwable) {
            partialFile.delete()
            current.updateAndGet {
                it.copy(
                    state = PhotoTransferState.Failed,
                    lastError = error.message ?: "Preview download failed",
                    updatedAtMs = nowMs(),
                )
            }
        }
        val completed = current.get()
        catalog.putTransfer(completed)
        onProgress(completed)
        return completed
    }

    suspend fun planUpload(
        sourcePath: String,
        previewPath: String,
        filename: String?,
        mimeType: String?,
        orientation: Int,
        capturedAtMs: Long? = null,
    ): PhotoTransfer = withContext(Dispatchers.IO) {
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
        val staged = stageUploadSource(source, preview, orientation, capturedAtMs)
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
        catalog.putTransfer(current)
        onProgress(current)
        try {
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
            catalog.putTransfer(current)
            onProgress(current)
            throw cancelled
        } catch (error: Throwable) {
            current = current.copy(
                state = PhotoTransferState.Failed,
                lastError = error.message ?: "iCloud Photos upload failed",
                updatedAtMs = nowMs(),
            )
        }
        catalog.putTransfer(current)
        onProgress(current)
        current
    }

    private fun stageUploadSource(
        source: File,
        preview: File,
        orientation: Int,
        capturedAtMs: Long?,
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
            writeUploadMetadata(
                uploadCompanion(destination, ".metadata"),
                UploadMetadata(orientation, capturedAtMs),
            )
            return StagedUpload(destination, previewDestination, digestHex)
        } catch (error: Throwable) {
            partial.delete()
            throw error
        }
    }

    companion object {
        fun downloadId(assetId: String, kind: PhotoResourceKind): String =
            "download:${kind.name.lowercase()}:$assetId"

        private fun safeKey(value: String): String = MessageDigest.getInstance("SHA-256")
            .digest(value.toByteArray(Charsets.UTF_8))
            .toHex()

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

        private fun writeUploadMetadata(file: File, metadata: UploadMetadata) {
            val partial = File(file.parentFile, ".${UUID.randomUUID()}.part")
            try {
                FileOutputStream(partial).use { stream ->
                    DataOutputStream(stream).use { output ->
                        output.writeInt(1)
                        output.writeInt(metadata.orientation)
                        output.writeLong(metadata.capturedAtMs ?: -1L)
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

        private fun readUploadMetadata(file: File): UploadMetadata =
            DataInputStream(file.inputStream().buffered()).use { input ->
                check(input.readInt() == 1) { "Unsupported Photos upload metadata" }
                val orientation = input.readInt()
                require(orientation in 1..8) { "Upload orientation is invalid" }
                val capturedAtMs = input.readLong().takeIf { it >= 0 }
                UploadMetadata(orientation, capturedAtMs)
            }

        private fun uploadCompanion(original: File, suffix: String): File {
            val prefix = original.name.removeSuffix(".original.jpg")
            return File(original.parentFile, prefix + suffix)
        }
    }
}

private data class StagedUpload(val file: File, val previewFile: File, val digest: String)
private data class UploadMetadata(val orientation: Int, val capturedAtMs: Long?)

private fun ByteArray.toHex(): String =
    joinToString(separator = "") { byte -> "%02x".format(byte.toInt() and 0xff) }

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

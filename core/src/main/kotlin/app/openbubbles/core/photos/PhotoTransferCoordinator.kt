package app.openbubbles.core.photos

import java.io.File
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.MessageDigest
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

private const val UPLOAD_PROTOCOL_BLOCK =
    "Waiting for live Apple Photos upload protocol proof"

/**
 * Owns local Photos transfer semantics independently of the Apple protocol:
 * durable state first, a `.part` file during transfer, then atomic promotion.
 * Uploads can be planned and restored now, but execution stays blocked until
 * the live CPL write contract has been captured and verified.
 */
class PhotoTransferCoordinator(
    private val port: PhotosPort,
    private val catalog: PhotosCatalog,
    private val previewRoot: File,
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
        filename: String?,
        mimeType: String?,
    ): PhotoTransfer {
        val source = File(sourcePath)
        require(source.isFile) { "Upload source does not exist" }
        val timestamp = nowMs()
        return PhotoTransfer(
            id = "upload:${UUID.randomUUID()}",
            assetId = null,
            direction = PhotoTransferDirection.Upload,
            resourceKind = PhotoResourceKind.Original,
            localPath = source.absolutePath,
            filename = filename ?: source.name,
            mimeType = mimeType,
            state = PhotoTransferState.Blocked,
            bytesDone = 0,
            totalBytes = source.length(),
            attemptCount = 0,
            lastError = UPLOAD_PROTOCOL_BLOCK,
            createdAtMs = timestamp,
            updatedAtMs = timestamp,
        ).also { catalog.putTransfer(it) }
    }

    companion object {
        fun downloadId(assetId: String, kind: PhotoResourceKind): String =
            "download:${kind.name.lowercase()}:$assetId"

        private fun safeKey(value: String): String = MessageDigest.getInstance("SHA-256")
            .digest(value.toByteArray(Charsets.UTF_8))
            .joinToString(separator = "") { byte -> "%02x".format(byte.toInt() and 0xff) }

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
    }
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

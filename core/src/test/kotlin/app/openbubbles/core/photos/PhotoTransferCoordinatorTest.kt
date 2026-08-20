package app.openbubbles.core.photos

import java.io.File
import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

class PhotoTransferCoordinatorTest {
    @Test
    fun previewDownloadIsPromotedAndPersisted() = runBlocking {
        val root = createTempDirectory("photo-preview").toFile()
        try {
            val catalog = FakeCatalog()
            val payload = jpegPayload()
            val port = FakePort(payload)
            val coordinator = PhotoTransferCoordinator(port, catalog, root) { 1_000 }

            val transfer = coordinator.downloadPreview(photo())

            assertEquals(PhotoTransferState.Succeeded, transfer.state)
            assertContentEquals(payload, File(transfer.localPath).readBytes())
            assertEquals(transfer, catalog.transfer(transfer.id))
            assertFalse(File(transfer.localPath + ".part").exists())
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun invalidPreviewIsNotPromoted() = runBlocking {
        val root = createTempDirectory("photo-preview-invalid").toFile()
        try {
            val transfer = PhotoTransferCoordinator(
                FakePort("encrypted bytes".toByteArray()),
                FakeCatalog(),
                root,
            ).downloadPreview(photo())

            assertEquals(PhotoTransferState.Failed, transfer.state)
            assertTrue(transfer.lastError!!.contains("expected media format"))
            assertFalse(File(transfer.localPath).exists())
            assertFalse(File(transfer.localPath + ".part").exists())
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun corruptCompletedPreviewIsDownloadedAgain() = runBlocking {
        val root = createTempDirectory("photo-preview-corrupt-cache").toFile()
        try {
            val catalog = FakeCatalog()
            val first = PhotoTransferCoordinator(FakePort(jpegPayload()), catalog, root)
                .downloadPreview(photo())
            File(first.localPath).writeText("corrupt cache")
            val retryPort = FakePort(jpegPayload())

            val retried = PhotoTransferCoordinator(retryPort, catalog, root)
                .downloadPreview(photo())

            assertEquals(PhotoTransferState.Succeeded, retried.state)
            assertEquals(1, retryPort.calls)
            assertContentEquals(jpegPayload(), File(retried.localPath).readBytes())
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun missingCompletedPreviewIsDurablyRequeuedOnRestore() = runBlocking {
        val root = createTempDirectory("photo-preview-restore").toFile()
        try {
            val catalog = FakeCatalog()
            val coordinator = PhotoTransferCoordinator(FakePort(jpegPayload()), catalog, root)
            val completed = coordinator.downloadPreview(photo())
            assertTrue(File(completed.localPath).delete())

            val restored = coordinator.revalidateCompletedDownload(photo(), completed)

            assertEquals(PhotoTransferState.Queued, restored.state)
            assertEquals(0, restored.bytesDone)
            assertEquals(null, restored.lastError)
            assertEquals(restored, catalog.transfer(restored.id))
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun cancellingDownloadPublishesQueuedStateAndDeletesPartialFile() = runBlocking {
        val root = createTempDirectory("photo-preview-cancel").toFile()
        try {
            val started = CompletableDeferred<Unit>()
            val catalog = FakeCatalog()
            val port = object : PhotosPort {
                override suspend fun access() = PhotosAccess(PhotosAvailability.Ready, "ready")
                override suspend fun page(cursor: String?, limit: Int) = PhotosPage(emptyList(), null)
                override suspend fun downloadPreview(
                    asset: PhotoSummary,
                    destPath: String,
                    onProgress: (Long, Long) -> Unit,
                ): Result<Unit> {
                    File(destPath).apply { parentFile?.mkdirs() }.writeText("partial")
                    onProgress(7, 10)
                    started.complete(Unit)
                    awaitCancellation()
                }
            }
            val updates = mutableListOf<PhotoTransfer>()
            val coordinator = PhotoTransferCoordinator(port, catalog, root)
            val job = launch { coordinator.downloadPreview(photo(), updates::add) }
            started.await()

            job.cancelAndJoin()

            val persisted = checkNotNull(catalog.transfer(
                PhotoTransferCoordinator.downloadId("master-1", PhotoResourceKind.Preview),
            ))
            assertEquals(PhotoTransferState.Queued, persisted.state)
            assertEquals(PhotoTransferState.Queued, updates.last().state)
            assertFalse(File(persisted.localPath + ".part").exists())
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun failedPreviewLeavesNoPartialFileAndCanRetry() = runBlocking {
        val root = createTempDirectory("photo-preview-failure").toFile()
        try {
            val catalog = FakeCatalog()
            val port = object : PhotosPort {
                override suspend fun access() = PhotosAccess(PhotosAvailability.Ready, "ready")
                override suspend fun page(cursor: String?, limit: Int) = PhotosPage(emptyList(), null)
                override suspend fun downloadPreview(
                    asset: PhotoSummary,
                    destPath: String,
                    onProgress: (Long, Long) -> Unit,
                ): Result<Unit> {
                    File(destPath).apply { parentFile?.mkdirs() }.writeText("partial")
                    return Result.failure(IllegalStateException("network failed"))
                }
            }
            val transfer = PhotoTransferCoordinator(port, catalog, root).downloadPreview(photo())

            assertEquals(PhotoTransferState.Failed, transfer.state)
            assertEquals("network failed", transfer.lastError)
            assertFalse(File(transfer.localPath).exists())
            assertFalse(File(transfer.localPath + ".part").exists())
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun originalDownloadUsesSeparateProtectedResourceAndCache() = runBlocking {
        val root = createTempDirectory("photo-original").toFile()
        try {
            val port = FakePort(jpegPayload())
            val coordinator = PhotoTransferCoordinator(
                port = port,
                catalog = FakeCatalog(),
                previewRoot = File(root, "previews"),
                originalRoot = File(root, "originals"),
            )

            val transfer = coordinator.downloadOriginal(photo())

            assertEquals(PhotoResourceKind.Original, transfer.resourceKind)
            assertEquals(PhotoTransferState.Succeeded, transfer.state)
            assertEquals(0, port.calls)
            assertEquals(1, port.originalCalls)
            assertTrue(File(transfer.localPath).parentFile == File(root, "originals"))
            assertContentEquals(jpegPayload(), File(transfer.localPath).readBytes())
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun invalidOriginalIsNotPromoted() = runBlocking {
        val root = createTempDirectory("photo-original-invalid").toFile()
        try {
            val transfer = PhotoTransferCoordinator(
                port = FakePort("not an image".toByteArray()),
                catalog = FakeCatalog(),
                previewRoot = File(root, "previews"),
                originalRoot = File(root, "originals"),
            ).downloadOriginal(photo())

            assertEquals(PhotoTransferState.Failed, transfer.state)
            assertFalse(File(transfer.localPath).exists())
            assertFalse(File(transfer.localPath + ".part").exists())
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun uploadPlanDurablyStagesOriginalPreviewAndMetadata() = runBlocking {
        val root = createTempDirectory("photo-upload-plan").toFile()
        try {
            val source = File(root, "IMG_1.jpg").apply { writeBytes(jpegPayload()) }
            val preview = File(root, "preview.jpg").apply { writeBytes(jpegPayload()) }
            val catalog = FakeCatalog()
            val uploadRoot = File(root, "durable-uploads")
            val coordinator = PhotoTransferCoordinator(
                FakePort(byteArrayOf()),
                catalog,
                File(root, "previews"),
                uploadRoot,
            )

            val transfer = coordinator.planUpload(
                sourcePath = source.path,
                previewPath = preview.path,
                filename = null,
                mimeType = "image/jpeg",
                orientation = 1,
            )

            assertEquals(PhotoTransferDirection.Upload, transfer.direction)
            assertEquals(PhotoTransferState.Queued, transfer.state)
            assertEquals(null, transfer.lastError)
            assertTrue(File(transfer.localPath).isFile)
            assertTrue(File(transfer.localPath).parentFile == uploadRoot)
            assertContentEquals(source.readBytes(), File(transfer.localPath).readBytes())
            assertEquals(3, uploadRoot.listFiles().orEmpty().size)
            assertEquals(transfer, catalog.transfer(transfer.id))
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun selectingSameUploadTwiceIsIdempotent() = runBlocking {
        val root = createTempDirectory("photo-upload-idempotent").toFile()
        try {
            val source = File(root, "same.jpg").apply { writeBytes(jpegPayload()) }
            val preview = File(root, "preview.jpg").apply { writeBytes(jpegPayload()) }
            val coordinator = PhotoTransferCoordinator(
                FakePort(byteArrayOf()),
                FakeCatalog(),
                File(root, "previews"),
                File(root, "uploads"),
                nowMs = { 2_000 },
            )

            val first = coordinator.planUpload(source.path, preview.path, source.name, "image/jpeg", 1)
            source.setLastModified(3_000)
            val second = coordinator.planUpload(source.path, preview.path, source.name, "image/jpeg", 1)

            assertEquals(first.id, second.id)
            assertEquals(first.localPath, second.localPath)
            assertEquals(3, File(root, "uploads").listFiles().orEmpty().size)
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun uploadPlanRejectsNonImageBeforePersisting() = runBlocking {
        val root = createTempDirectory("photo-upload-reject").toFile()
        try {
            val source = File(root, "notes.txt").apply { writeText("not a photo") }
            val preview = File(root, "preview.jpg").apply { writeBytes(jpegPayload()) }
            val catalog = FakeCatalog()
            val coordinator = PhotoTransferCoordinator(
                FakePort(byteArrayOf()),
                catalog,
                File(root, "previews"),
            )

            assertFailsWith<IllegalArgumentException> {
                coordinator.planUpload(source.path, preview.path, source.name, "text/plain", 1)
            }
            assertTrue(catalog.transfers().isEmpty())
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun queuedUploadRunsOnceAndPersistsCloudMasterId() = runBlocking {
        val root = createTempDirectory("photo-upload-run").toFile()
        try {
            val source = File(root, "same.jpg").apply { writeBytes(jpegPayload()) }
            val preview = File(root, "preview.jpg").apply { writeBytes(jpegPayload()) }
            val catalog = FakeCatalog()
            val port = FakePort(byteArrayOf())
            val coordinator = PhotoTransferCoordinator(
                port,
                catalog,
                File(root, "previews"),
                File(root, "uploads"),
            )
            val queued = coordinator.planUpload(
                source.path,
                preview.path,
                source.name,
                "image/jpeg",
                6,
                1234L,
            )

            val completed = coordinator.upload(queued)

            assertEquals(PhotoTransferState.Succeeded, completed.state)
            assertEquals("master-uploaded", completed.assetId)
            assertEquals(completed.totalBytes, completed.bytesDone)
            assertEquals(1, port.uploadCalls)
            assertEquals(6, port.uploadOrientation)
            assertEquals(1234L, port.uploadCapturedAtMs)
            assertEquals(completed, catalog.transfer(completed.id))
        } finally {
            root.deleteRecursively()
        }
    }

    private fun photo() = PhotoSummary(
        id = "master-1",
        assetId = "asset-1",
        filename = "IMG_1.HEIC",
        mediaKind = PhotoMediaKind.Image,
        livePhoto = false,
        width = 480,
        height = 360,
        originalSize = 1_000,
        previewSize = 7,
        capturedAtMs = 1,
        addedAtMs = 1,
        favorite = false,
        hidden = false,
    )

    private fun jpegPayload() = byteArrayOf(
        0xff.toByte(), 0xd8.toByte(), 0xff.toByte(), 0xe0.toByte(), 0, 1, 2,
    )

    private class FakePort(private val payload: ByteArray) : PhotosPort {
        var calls: Int = 0
            private set
        var uploadCalls: Int = 0
            private set
        var originalCalls: Int = 0
            private set
        var uploadOrientation: Int? = null
            private set
        var uploadCapturedAtMs: Long? = null
            private set

        override suspend fun access() = PhotosAccess(PhotosAvailability.Ready, "ready")
        override suspend fun page(cursor: String?, limit: Int) = PhotosPage(emptyList(), null)
        override suspend fun downloadPreview(
            asset: PhotoSummary,
            destPath: String,
            onProgress: (Long, Long) -> Unit,
        ): Result<Unit> = runCatching {
            calls += 1
            File(destPath).apply { parentFile?.mkdirs() }.writeBytes(payload)
            onProgress(payload.size.toLong(), payload.size.toLong())
        }

        override suspend fun downloadOriginal(
            asset: PhotoSummary,
            destPath: String,
            onProgress: (Long, Long) -> Unit,
        ): Result<Unit> = runCatching {
            originalCalls += 1
            File(destPath).apply { parentFile?.mkdirs() }.writeBytes(payload)
            onProgress(payload.size.toLong(), payload.size.toLong())
        }

        override suspend fun uploadJpeg(
            originalPath: String,
            previewPath: String,
            filename: String,
            capturedAtMs: Long?,
            orientation: Int,
        ): Result<PhotoUploadReceipt> = runCatching {
            check(File(originalPath).isFile)
            check(File(previewPath).isFile)
            uploadCalls += 1
            uploadOrientation = orientation
            uploadCapturedAtMs = capturedAtMs
            PhotoUploadReceipt("master-uploaded", "asset-uploaded")
        }
    }

    private class FakeCatalog : PhotosCatalog {
        private var metadata = CachedPhotos()
        private val transferRows = linkedMapOf<String, PhotoTransfer>()

        override suspend fun loadMetadata() = metadata
        override suspend fun replaceMetadata(assets: List<PhotoSummary>, nextCursor: String?) {
            metadata = CachedPhotos(assets, nextCursor)
        }

        override suspend fun transfers() = transferRows.values.toList()
        override suspend fun transfer(id: String) = transferRows[id]
        override suspend fun putTransfer(transfer: PhotoTransfer) {
            transferRows[transfer.id] = transfer
        }

        override suspend fun recoverInterruptedTransfers() = Unit
    }
}

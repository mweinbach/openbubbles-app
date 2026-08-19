package app.openbubbles.core.photos

import java.io.File
import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
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
    fun uploadPlanIsDurableButBlockedUntilProtocolProof() = runBlocking {
        val root = createTempDirectory("photo-upload-plan").toFile()
        try {
            val source = File(root, "IMG_1.HEIC").apply { writeText("original") }
            val catalog = FakeCatalog()
            val coordinator = PhotoTransferCoordinator(FakePort(byteArrayOf()), catalog, File(root, "previews"))

            val transfer = coordinator.planUpload(source.path, null, "image/heic")

            assertEquals(PhotoTransferDirection.Upload, transfer.direction)
            assertEquals(PhotoTransferState.Blocked, transfer.state)
            assertTrue(transfer.lastError!!.contains("protocol proof", ignoreCase = true))
            assertEquals(transfer, catalog.transfer(transfer.id))
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

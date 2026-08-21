package app.openbubbles.nativeapp.ui.photos

import android.net.Uri
import app.openbubbles.core.photos.CachedPhotos
import app.openbubbles.core.photos.PhotoMediaKind
import app.openbubbles.core.photos.PhotoResourceKind
import app.openbubbles.core.photos.PhotoSummary
import app.openbubbles.core.photos.PhotoTransfer
import app.openbubbles.core.photos.PhotoTransferCoordinator
import app.openbubbles.core.photos.PhotoTransferDirection
import app.openbubbles.core.photos.PhotoTransferState
import app.openbubbles.core.photos.PhotosAccess
import app.openbubbles.core.photos.PhotosAvailability
import app.openbubbles.core.photos.PhotosBrowser
import app.openbubbles.core.photos.PhotosCatalog
import app.openbubbles.core.photos.PhotosPage
import app.openbubbles.core.photos.PhotosPort
import app.openbubbles.nativeapp.data.photos.PhotoFolderSource
import app.openbubbles.nativeapp.data.photos.PhotosWorkRegistry
import java.io.File
import kotlin.io.path.createTempDirectory
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain

@OptIn(ExperimentalCoroutinesApi::class)
class PhotosViewModelTest {
    private val dispatcher = StandardTestDispatcher()

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `missing completed downloads restore as queued instead of blank successes`() =
        runTest(dispatcher) {
            val root = createTempDirectory("photos-view-model-restore").toFile()
            try {
                val asset = photo("one")
                val catalog = FakeCatalog(
                    metadata = CachedPhotos(listOf(asset)),
                    initialTransfers = listOf(
                        completedTransfer(asset, PhotoResourceKind.Preview, File(root, "missing-preview")),
                        completedTransfer(asset, PhotoResourceKind.Original, File(root, "missing-original")),
                    ),
                )
                val port = BlockingDownloadPort(listOf(asset))
                val model = model(port, catalog, root)

                advanceUntilIdle()

                assertEquals(PhotoTransferState.Queued, model.uiState.value.previewTransfers[asset.id]?.state)
                assertEquals(PhotoTransferState.Queued, model.uiState.value.originalTransfers[asset.id]?.state)
                assertEquals(
                    PhotoTransferState.Queued,
                    catalog.transfer(PhotoTransferCoordinator.downloadId(asset.id, PhotoResourceKind.Preview))?.state,
                )
            } finally {
                PhotosWorkRegistry.cancelAndJoinAll()
                root.deleteRecursively()
            }
        }

    @Test
    fun `validated completed preview stays on the scroll fast path`() = runTest(dispatcher) {
        val root = createTempDirectory("photos-view-model-valid-cache").toFile()
        try {
            val asset = photo("one")
            val cachedFile = File(root, "cached-preview.jpg").apply {
                writeBytes(byteArrayOf(0xff.toByte(), 0xd8.toByte(), 0xff.toByte(), 1))
            }
            val catalog = FakeCatalog(
                metadata = CachedPhotos(listOf(asset)),
                initialTransfers = listOf(
                    completedTransfer(asset, PhotoResourceKind.Preview, cachedFile),
                ),
            )
            val port = BlockingDownloadPort(listOf(asset))
            val model = model(port, catalog, root)
            advanceUntilIdle()

            model.ensurePreview(asset)
            runCurrent()

            assertEquals(PhotoTransferState.Succeeded, model.uiState.value.previewTransfers[asset.id]?.state)
            assertEquals(emptyList(), port.previewStarted)
        } finally {
            PhotosWorkRegistry.cancelAndJoinAll()
            root.deleteRecursively()
        }
    }

    @Test
    fun `disposing preview cancels its transfer and makes it retryable`() = runTest(dispatcher) {
        val root = createTempDirectory("photos-view-model-preview-cancel").toFile()
        try {
            val asset = photo("one")
            val catalog = FakeCatalog(CachedPhotos(listOf(asset)))
            val port = BlockingDownloadPort(listOf(asset))
            val model = model(port, catalog, root)
            advanceUntilIdle()

            model.ensurePreview(asset)
            runCurrent()
            assertEquals(listOf(asset.id), port.previewStarted)

            model.cancelPreview(asset)
            runCurrent()

            assertEquals(listOf(asset.id), port.previewCancelled)
            assertEquals(PhotoTransferState.Queued, model.uiState.value.previewTransfers[asset.id]?.state)
            assertEquals(0, port.activeDownloads)
        } finally {
            PhotosWorkRegistry.cancelAndJoinAll()
            root.deleteRecursively()
        }
    }

    @Test
    fun `account invalidation joins retained work and blocks further launches`() =
        runTest(dispatcher) {
            val root = createTempDirectory("photos-view-model-account-cancel").toFile()
            try {
                val asset = photo("one")
                val catalog = FakeCatalog(CachedPhotos(listOf(asset)))
                val port = BlockingDownloadPort(listOf(asset))
                val model = model(port, catalog, root)
                advanceUntilIdle()
                model.ensurePreview(asset)
                runCurrent()
                assertEquals(1, port.activeDownloads)

                PhotosWorkRegistry.cancelAndJoinAll()

                assertEquals(0, port.activeDownloads)
                assertEquals(listOf(asset.id), port.previewCancelled)
                assertEquals(PhotoTransferState.Queued, model.uiState.value.previewTransfers[asset.id]?.state)
                model.ensurePreview(asset)
                runCurrent()
                assertEquals(listOf(asset.id), port.previewStarted)
            } finally {
                PhotosWorkRegistry.cancelAndJoinAll()
                root.deleteRecursively()
            }
        }

    @Test
    fun `switching and closing selection cancel originals with one active at a time`() =
        runTest(dispatcher) {
            val root = createTempDirectory("photos-view-model-original-cancel").toFile()
            try {
                val first = photo("one")
                val second = photo("two")
                val assets = listOf(first, second)
                val catalog = FakeCatalog(CachedPhotos(assets))
                val port = BlockingDownloadPort(assets)
                val model = model(port, catalog, root)
                advanceUntilIdle()

                model.select(first)
                runCurrent()
                model.select(second)
                runCurrent()

                assertEquals(listOf(first.id, second.id), port.originalStarted)
                assertEquals(listOf(first.id), port.originalCancelled)
                assertEquals(1, port.maxActiveDownloads)
                assertEquals(second.id, model.uiState.value.selectedAssetId)

                model.closeSelected()
                runCurrent()

                assertEquals(listOf(first.id, second.id), port.originalCancelled)
                assertEquals(null, model.uiState.value.selectedAssetId)
                assertEquals(PhotoTransferState.Queued, model.uiState.value.originalTransfers[first.id]?.state)
                assertEquals(PhotoTransferState.Queued, model.uiState.value.originalTransfers[second.id]?.state)
                assertEquals(0, port.activeDownloads)
            } finally {
                PhotosWorkRegistry.cancelAndJoinAll()
                root.deleteRecursively()
            }
        }

    @Test
    fun `reselecting a canceled original restarts it after cancellation joins`() =
        runTest(dispatcher) {
            val root = createTempDirectory("photos-view-model-original-reselect").toFile()
            try {
                val first = photo("one")
                val second = photo("two")
                val port = BlockingDownloadPort(listOf(first, second))
                val model = model(port, FakeCatalog(CachedPhotos(listOf(first, second))), root)
                advanceUntilIdle()

                model.select(first)
                runCurrent()
                model.select(second)
                runCurrent()
                model.select(first)
                runCurrent()

                assertEquals(listOf(first.id, second.id, first.id), port.originalStarted)
                assertEquals(listOf(first.id, second.id), port.originalCancelled)
                assertEquals(first.id, model.uiState.value.selectedAssetId)
                assertEquals(1, port.activeDownloads)
                assertEquals(1, port.maxActiveDownloads)

                model.closeSelected()
                runCurrent()
            } finally {
                PhotosWorkRegistry.cancelAndJoinAll()
                root.deleteRecursively()
            }
        }

    @Test
    fun `camera backup switch mirrors the port and explains a refusal`() = runTest(dispatcher) {
        val root = createTempDirectory("photos-view-model-backup").toFile()
        try {
            val asset = photo("one")
            val backup = FakeBackup(allowEnable = false)
            val model = model(BlockingDownloadPort(listOf(asset)), FakeCatalog(CachedPhotos(listOf(asset))), root, backup)
            advanceUntilIdle()
            assertEquals(false, model.uiState.value.backgroundSyncEnabled)

            model.setBackgroundSync(true)
            advanceUntilIdle()
            assertEquals(false, model.uiState.value.backgroundSyncEnabled)
            assertEquals(listOf(true), backup.requests)
            assertEquals("Allow photo access to back up new camera photos", model.uiState.value.uploadError)

            backup.allowEnable = true
            model.setBackgroundSync(true)
            advanceUntilIdle()
            assertEquals(true, model.uiState.value.backgroundSyncEnabled)
            assertEquals(listOf(true, true), backup.requests)

            model.setBackgroundSync(false)
            advanceUntilIdle()
            assertEquals(false, model.uiState.value.backgroundSyncEnabled)
            assertEquals(false, backup.enabled())
        } finally {
            PhotosWorkRegistry.cancelAndJoinAll()
            root.deleteRecursively()
        }
    }

    @Test
    fun `bootstrap restores the persisted camera backup switch`() = runTest(dispatcher) {
        val root = createTempDirectory("photos-view-model-backup-restore").toFile()
        try {
            val asset = photo("one")
            val backup = FakeBackup(allowEnable = true).apply { state = true }
            val model = model(BlockingDownloadPort(listOf(asset)), FakeCatalog(CachedPhotos(listOf(asset))), root, backup)
            advanceUntilIdle()
            assertEquals(true, model.uiState.value.backgroundSyncEnabled)
        } finally {
            PhotosWorkRegistry.cancelAndJoinAll()
            root.deleteRecursively()
        }
    }

    private fun model(
        port: PhotosPort,
        catalog: PhotosCatalog,
        root: File,
        backup: PhotosBackupPort = FakeBackup(allowEnable = true),
    ): PhotosViewModel {
        PhotosWorkRegistry.activate()
        return PhotosViewModel(
            browser = PhotosBrowser(port),
            catalog = catalog,
            coordinator = PhotoTransferCoordinator(
                port = port,
                catalog = catalog,
                previewRoot = File(root, "previews"),
                uploadRoot = File(root, "uploads"),
                originalRoot = File(root, "originals"),
                ioDispatcher = dispatcher,
            ),
            folders = FakeFolders,
            prepareUpload = { error("Uploads are outside this test") },
            backup = backup,
        )
    }

    private class FakeBackup(var allowEnable: Boolean) : PhotosBackupPort {
        var state = false
        val requests = mutableListOf<Boolean>()

        override fun enabled(): Boolean = state

        override suspend fun setEnabled(enabled: Boolean): Boolean {
            requests += enabled
            state = enabled && allowEnable
            return state
        }
    }

    private fun photo(id: String) = PhotoSummary(
        id = id,
        assetId = "asset-$id",
        filename = "$id.jpg",
        mediaKind = PhotoMediaKind.Image,
        livePhoto = false,
        width = 100,
        height = 100,
        originalSize = 100,
        previewSize = 10,
        capturedAtMs = 1,
        addedAtMs = 1,
        favorite = false,
        hidden = false,
    )

    private fun completedTransfer(
        asset: PhotoSummary,
        resourceKind: PhotoResourceKind,
        file: File,
    ) = PhotoTransfer(
        id = PhotoTransferCoordinator.downloadId(asset.id, resourceKind),
        assetId = asset.id,
        direction = PhotoTransferDirection.Download,
        resourceKind = resourceKind,
        localPath = file.absolutePath,
        filename = asset.filename,
        mimeType = "image/jpeg",
        state = PhotoTransferState.Succeeded,
        bytesDone = 10,
        totalBytes = 10,
        createdAtMs = 1,
        updatedAtMs = 1,
    )

    private object FakeFolders : PhotosFolderPort {
        override suspend fun sources(): List<PhotoFolderSource> = emptyList()
        override suspend fun add(uri: Uri): List<PhotoFolderSource> = emptyList()
        override suspend fun remove(uri: Uri): List<PhotoFolderSource> = emptyList()
        override suspend fun photos(source: PhotoFolderSource): List<Uri> = emptyList()
    }

    private class BlockingDownloadPort(
        private val assets: List<PhotoSummary>,
    ) : PhotosPort {
        val previewStarted = mutableListOf<String>()
        val previewCancelled = mutableListOf<String>()
        val originalStarted = mutableListOf<String>()
        val originalCancelled = mutableListOf<String>()
        var activeDownloads = 0
            private set
        var maxActiveDownloads = 0
            private set

        override suspend fun access() = PhotosAccess(PhotosAvailability.Ready, "ready")

        override suspend fun page(cursor: String?, limit: Int) = PhotosPage(assets, null)

        override suspend fun downloadPreview(
            asset: PhotoSummary,
            destPath: String,
            onProgress: (Long, Long) -> Unit,
        ): Result<Unit> {
            previewStarted += asset.id
            return blockDownload(asset.id, destPath, onProgress, previewCancelled)
        }

        override suspend fun downloadOriginal(
            asset: PhotoSummary,
            destPath: String,
            onProgress: (Long, Long) -> Unit,
        ): Result<Unit> {
            originalStarted += asset.id
            return blockDownload(asset.id, destPath, onProgress, originalCancelled)
        }

        private suspend fun blockDownload(
            assetId: String,
            destPath: String,
            onProgress: (Long, Long) -> Unit,
            cancelled: MutableList<String>,
        ): Result<Unit> {
            File(destPath).apply { parentFile?.mkdirs() }.writeText("partial")
            onProgress(1, 10)
            activeDownloads += 1
            maxActiveDownloads = maxOf(maxActiveDownloads, activeDownloads)
            try {
                awaitCancellation()
            } finally {
                activeDownloads -= 1
                cancelled += assetId
            }
        }
    }

    private class FakeCatalog(
        private var metadata: CachedPhotos,
        initialTransfers: List<PhotoTransfer> = emptyList(),
    ) : PhotosCatalog {
        private val transferRows = initialTransfers.associateByTo(linkedMapOf(), PhotoTransfer::id)

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

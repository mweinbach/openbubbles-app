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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext

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
                FakePort("invalid".toByteArray()),
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
    fun previewsRejectTruncatedAndOversizedPayloadsBeforePromotion() = runBlocking {
        val root = createTempDirectory("photo-preview-byte-count").toFile()
        try {
            val expected = jpegPayload()
            listOf(
                expected.copyOf(expected.size - 1),
                expected + byteArrayOf(9),
            ).forEachIndexed { index, actual ->
                val asset = photo().copy(id = "master-byte-count-$index")
                val transfer = PhotoTransferCoordinator(
                    FakePort(actual),
                    FakeCatalog(),
                    root,
                ).downloadPreview(asset)

                assertEquals(PhotoTransferState.Failed, transfer.state)
                assertTrue(transfer.lastError!!.contains("expected byte size"))
                assertFalse(File(transfer.localPath).exists())
                assertFalse(File(transfer.localPath + ".part").exists())
            }
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun advertisedDownloadByteLimitsFailBeforeCrossingTheProtocolBoundary() = runBlocking {
        val root = createTempDirectory("photo-download-byte-limits").toFile()
        try {
            val port = FakePort(jpegPayload())
            val coordinator = PhotoTransferCoordinator(port, FakeCatalog(), root)
            val previews = listOf(
                0L,
                PhotoTransferCoordinator.MAX_PHOTO_PREVIEW_BYTES + 1,
            )
            val originals = listOf(
                0L,
                PhotoTransferCoordinator.MAX_PHOTO_ORIGINAL_BYTES + 1,
            )

            previews.forEachIndexed { index, bytes ->
                val transfer = coordinator.downloadPreview(
                    photo().copy(id = "preview-limit-$index", previewSize = bytes),
                )
                assertEquals(PhotoTransferState.Failed, transfer.state)
            }
            originals.forEachIndexed { index, bytes ->
                val transfer = coordinator.downloadOriginal(
                    photo().copy(id = "original-limit-$index", originalSize = bytes),
                )
                assertEquals(PhotoTransferState.Failed, transfer.state)
            }

            assertEquals(0, port.calls)
            assertEquals(0, port.originalCalls)
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
    fun headerValidButTruncatedCompletedPreviewIsDownloadedAgain() = runBlocking {
        val root = createTempDirectory("photo-preview-truncated-cache").toFile()
        try {
            val catalog = FakeCatalog()
            val asset = photo()
            val first = PhotoTransferCoordinator(FakePort(jpegPayload()), catalog, root)
                .downloadPreview(asset)
            File(first.localPath).writeBytes(jpegPayload().copyOf(4))
            val retryPort = FakePort(jpegPayload())

            val retried = PhotoTransferCoordinator(retryPort, catalog, root)
                .downloadPreview(asset)

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
    fun headerValidButWrongSizeCompletedPreviewIsDurablyRequeuedOnRestore() = runBlocking {
        val root = createTempDirectory("photo-preview-restore-byte-count").toFile()
        try {
            val catalog = FakeCatalog()
            val coordinator = PhotoTransferCoordinator(FakePort(jpegPayload()), catalog, root)
            val asset = photo()
            val completed = coordinator.downloadPreview(asset)
            File(completed.localPath).writeBytes(jpegPayload().copyOf(4))

            val restored = coordinator.revalidateCompletedDownload(asset, completed)

            assertEquals(PhotoTransferState.Queued, restored.state)
            assertEquals(0, restored.bytesDone)
            assertEquals(asset.previewSize, restored.totalBytes)
            assertEquals(restored, catalog.transfer(restored.id))
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun cancellingDownloadDurablyRequeuesWithSuspendingCatalogAndDeletesPartialFile() = runBlocking {
        val root = createTempDirectory("photo-preview-cancel").toFile()
        try {
            val started = CompletableDeferred<Unit>()
            val catalog = FakeCatalog(suspendWrites = true)
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
    fun cancellingDownloadWhileRunningStateIsPersistedDurablyRequeuesIt() = runBlocking {
        val root = createTempDirectory("photo-preview-cancel-running-write").toFile()
        try {
            val runningWritePersisted = CompletableDeferred<Unit>()
            val catalog = FakeCatalog(
                suspendWrites = true,
                runningWritePersisted = runningWritePersisted,
            )
            val port = FakePort(jpegPayload())
            val updates = mutableListOf<PhotoTransfer>()
            val coordinator = PhotoTransferCoordinator(port, catalog, root)
            val job = launch { coordinator.downloadPreview(photo(), updates::add) }
            runningWritePersisted.await()

            job.cancelAndJoin()

            val persisted = checkNotNull(catalog.transfer(
                PhotoTransferCoordinator.downloadId("master-1", PhotoResourceKind.Preview),
            ))
            assertEquals(PhotoTransferState.Queued, persisted.state)
            assertEquals("Transfer interrupted", persisted.lastError)
            assertEquals(PhotoTransferState.Queued, updates.last().state)
            assertEquals(0, port.calls)
            assertFalse(File(persisted.localPath + ".part").exists())
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun cancellingDownloadDuringTerminalWriteStillPersistsItsCompletedFile() = runBlocking {
        val root = createTempDirectory("photo-preview-cancel-terminal-write").toFile()
        try {
            val terminalWriteStarted = CompletableDeferred<Unit>()
            val releaseTerminalWrite = CompletableDeferred<Unit>()
            val catalog = FakeCatalog(
                suspendWrites = true,
                terminalWriteStarted = terminalWriteStarted,
                releaseTerminalWrite = releaseTerminalWrite,
            )
            val port = FakePort(jpegPayload())
            val updates = mutableListOf<PhotoTransfer>()
            val coordinator = PhotoTransferCoordinator(port, catalog, root)
            val job = launch { coordinator.downloadPreview(photo(), updates::add) }
            terminalWriteStarted.await()

            job.cancel()
            releaseTerminalWrite.complete(Unit)
            job.join()

            val persisted = checkNotNull(catalog.transfer(
                PhotoTransferCoordinator.downloadId("master-1", PhotoResourceKind.Preview),
            ))
            assertEquals(PhotoTransferState.Succeeded, persisted.state)
            assertEquals(PhotoTransferState.Succeeded, updates.last().state)
            assertEquals(1, port.calls)
            assertContentEquals(jpegPayload(), File(persisted.localPath).readBytes())
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
            assertTrue(transfer.localPath.endsWith(".jpg"))
            assertEquals("image/jpeg", transfer.mimeType)
            assertContentEquals(jpegPayload(), File(transfer.localPath).readBytes())
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun livePhotoCompanionUsesAnIndependentPrivateVerifiedTransfer() = runBlocking {
        val root = createTempDirectory("photo-live-companion").toFile()
        try {
            val motion = ftypPayload("qt  ")
            val port = FakePort(motion)
            val catalog = FakeCatalog()
            val asset = photo().copy(livePhoto = true, livePhotoVideoSize = motion.size.toLong())
            val coordinator = PhotoTransferCoordinator(
                port = port,
                catalog = catalog,
                previewRoot = File(root, "previews"),
                originalRoot = File(root, "originals"),
            )

            val transfer = coordinator.downloadLivePhotoVideo(asset)
            val restored = coordinator.revalidateCompletedDownload(asset, transfer)

            assertEquals(PhotoResourceKind.LivePhotoVideo, transfer.resourceKind)
            assertEquals(PhotoTransferState.Succeeded, transfer.state)
            assertEquals("video/quicktime", transfer.mimeType)
            assertTrue(transfer.localPath.endsWith(".mov"))
            assertEquals(1, port.livePhotoVideoCalls)
            assertEquals(0, port.originalCalls)
            assertContentEquals(motion, File(transfer.localPath).readBytes())
            assertEquals(transfer, restored)
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun livePhotoCompanionRejectsMismatchedMediaWithoutPublishingIt() = runBlocking {
        val root = createTempDirectory("photo-live-companion-invalid").toFile()
        try {
            val payload = jpegPayload()
            val asset = photo().copy(livePhoto = true, livePhotoVideoSize = payload.size.toLong())
            val transfer = PhotoTransferCoordinator(FakePort(payload), FakeCatalog(), root)
                .downloadLivePhotoVideo(asset)

            assertEquals(PhotoTransferState.Failed, transfer.state)
            assertFalse(File(transfer.localPath).exists())
            assertFalse(File(transfer.localPath + ".part").exists())
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun originalFormatIsSniffedFromMediaBytes() {
        val root = createTempDirectory("photo-original-formats").toFile()
        try {
            val cases = listOf(
                FormatCase(jpegPayload(), PhotoMediaKind.Image, "jpg", "image/jpeg"),
                FormatCase(
                    byteArrayOf(0x89.toByte(), 'P'.code.toByte(), 'N'.code.toByte(), 'G'.code.toByte(),
                        '\r'.code.toByte(), '\n'.code.toByte(), 0x1a, '\n'.code.toByte()),
                    PhotoMediaKind.Image,
                    "png",
                    "image/png",
                ),
                FormatCase("GIF89a".toByteArray(), PhotoMediaKind.Image, "gif", "image/gif"),
                FormatCase(
                    "RIFF1234WEBP".toByteArray(),
                    PhotoMediaKind.Image,
                    "webp",
                    "image/webp",
                ),
                FormatCase(tiffPayload(dng = false), PhotoMediaKind.Image, "tiff", "image/tiff"),
                FormatCase(tiffPayload(dng = true), PhotoMediaKind.Image, "dng", "image/x-adobe-dng"),
                FormatCase(ftypPayload("heic"), PhotoMediaKind.Image, "heic", "image/heic"),
                FormatCase(ftypPayload("heix", "tmap"), PhotoMediaKind.Image, "heic", "image/heic"),
                FormatCase(ftypPayload("mif1", "heic"), PhotoMediaKind.Image, "heic", "image/heic"),
                FormatCase(ftypPayload("mif1", "avif"), PhotoMediaKind.Image, "avif", "image/avif"),
                FormatCase(ftypPayload("qt  "), PhotoMediaKind.Video, "mov", "video/quicktime"),
                FormatCase(ftypPayload("mp42", "isom"), PhotoMediaKind.Video, "mp4", "video/mp4"),
            )

            cases.forEachIndexed { index, expected ->
                val source = File(root, "sample-$index").apply { writeBytes(expected.bytes) }
                val actual = sniffPhotoOriginalFormat(source, expected.mediaKind)

                assertEquals(expected.extension, actual?.extension, "extension for sample $index")
                assertEquals(expected.mimeType, actual?.mimeType, "MIME for sample $index")
            }
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun missingFilenamePromotesHeicOriginalUsingItsActualContainer() = runBlocking {
        val root = createTempDirectory("photo-original-heic").toFile()
        try {
            val payload = ftypPayload("heix", "mif1", "tmap")
            val catalog = FakeCatalog()
            val transfer = PhotoTransferCoordinator(
                port = FakePort(payload),
                catalog = catalog,
                previewRoot = File(root, "previews"),
                originalRoot = File(root, "originals"),
            ).downloadOriginal(photo().copy(filename = null, originalSize = payload.size.toLong()))

            assertEquals(PhotoTransferState.Succeeded, transfer.state)
            assertTrue(transfer.localPath.endsWith(".heic"))
            assertEquals("image/heic", transfer.mimeType)
            assertContentEquals(payload, File(transfer.localPath).readBytes())
            assertEquals(transfer, catalog.transfer(transfer.id))
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun compatibleFilenameExtensionIsPreservedButMismatchedExtensionIsNot() = runBlocking {
        val root = createTempDirectory("photo-original-extension").toFile()
        try {
            val coordinator = PhotoTransferCoordinator(
                port = FakePort(jpegPayload()),
                catalog = FakeCatalog(),
                previewRoot = File(root, "previews"),
                originalRoot = File(root, "originals"),
            )

            val compatible = coordinator.downloadOriginal(photo().copy(filename = "IMG_1.JPEG"))
            val mismatched = coordinator.downloadOriginal(
                photo().copy(id = "master-2", filename = "IMG_2.HEIC"),
            )

            assertTrue(compatible.localPath.endsWith(".jpeg"))
            assertEquals("image/jpeg", compatible.mimeType)
            assertTrue(mismatched.localPath.endsWith(".jpg"))
            assertEquals("image/jpeg", mismatched.mimeType)
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun legacyImageCacheIsRenamedAndRetypedWhenRestored() = runBlocking {
        val root = createTempDirectory("photo-original-legacy").toFile()
        try {
            val payload = ftypPayload("heix", "mif1")
            val asset = photo().copy(filename = null, originalSize = payload.size.toLong())
            val catalog = FakeCatalog()
            val port = FakePort(payload)
            val coordinator = PhotoTransferCoordinator(
                port = port,
                catalog = catalog,
                previewRoot = File(root, "previews"),
                originalRoot = File(root, "originals"),
            )
            val completed = coordinator.downloadOriginal(asset)
            val legacy = File(completed.localPath.removeSuffix(".heic") + ".image")
            assertTrue(File(completed.localPath).renameTo(legacy))
            val previousRelease = completed.copy(localPath = legacy.absolutePath, mimeType = "image/*")
            catalog.putTransfer(previousRelease)

            val restored = coordinator.revalidateCompletedDownload(asset, previousRelease)

            assertEquals(PhotoTransferState.Succeeded, restored.state)
            assertTrue(restored.localPath.endsWith(".heic"))
            assertEquals("image/heic", restored.mimeType)
            assertTrue(File(restored.localPath).isFile)
            assertFalse(legacy.exists())
            assertEquals(1, port.originalCalls)
            assertEquals(restored, catalog.transfer(restored.id))
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
            assertEquals(PhotoTransferOrigin.Manual, transfer.origin)
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
    fun uploadOriginIsDurableAndExistingManualConsentCannotBeUpgraded() = runBlocking {
        val root = createTempDirectory("photo-upload-origin").toFile()
        try {
            val manualSource = File(root, "manual.jpg").apply { writeBytes(jpegPayload()) }
            val cameraSource = File(root, "camera.jpg").apply {
                writeBytes(jpegPayload() + byteArrayOf(9))
            }
            val preview = File(root, "preview.jpg").apply { writeBytes(jpegPayload()) }
            val catalog = FakeCatalog()
            val coordinator = PhotoTransferCoordinator(
                FakePort(byteArrayOf()),
                catalog,
                File(root, "previews"),
                File(root, "uploads"),
            )

            val manual = coordinator.planUpload(
                manualSource.path,
                preview.path,
                manualSource.name,
                "image/jpeg",
                1,
            )
            val collided = coordinator.planUpload(
                manualSource.path,
                preview.path,
                manualSource.name,
                "image/jpeg",
                1,
                origin = PhotoTransferOrigin.CameraBackup,
            )
            val camera = coordinator.planUpload(
                cameraSource.path,
                preview.path,
                cameraSource.name,
                "image/jpeg",
                1,
                origin = PhotoTransferOrigin.CameraBackup,
            )
            val reselected = coordinator.planUpload(
                cameraSource.path,
                preview.path,
                cameraSource.name,
                "image/jpeg",
                1,
            )

            assertEquals(PhotoTransferOrigin.Manual, manual.origin)
            assertEquals(PhotoTransferOrigin.Manual, collided.origin)
            assertEquals(PhotoTransferOrigin.Manual, catalog.transfer(manual.id)?.origin)
            assertEquals(PhotoTransferOrigin.CameraBackup, camera.origin)
            assertEquals(PhotoTransferOrigin.CameraBackup, reselected.origin)
            assertEquals(PhotoTransferOrigin.CameraBackup, catalog.transfer(camera.id)?.origin)
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
                PhotoTimeZone("America/New_York", -14_400),
            )

            val completed = coordinator.upload(queued)

            assertEquals(PhotoTransferState.Succeeded, completed.state)
            assertEquals("master-uploaded", completed.assetId)
            assertEquals(completed.totalBytes, completed.bytesDone)
            assertEquals(1, port.uploadCalls)
            assertEquals(6, port.uploadOrientation)
            assertEquals(1234L, port.uploadCapturedAtMs)
            assertEquals(PhotoTimeZone("America/New_York", -14_400), port.uploadTimeZone)
            assertEquals(completed, catalog.transfer(completed.id))
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun cancellingUploadDurablyRequeuesWithSuspendingCatalog() = runBlocking {
        val root = createTempDirectory("photo-upload-cancel").toFile()
        try {
            val source = File(root, "upload.jpg").apply { writeBytes(jpegPayload()) }
            val preview = File(root, "preview.jpg").apply { writeBytes(jpegPayload()) }
            val started = CompletableDeferred<Unit>()
            val catalog = FakeCatalog(suspendWrites = true)
            val port = object : PhotosPort {
                override suspend fun access() = PhotosAccess(PhotosAvailability.Ready, "ready")
                override suspend fun page(cursor: String?, limit: Int) = PhotosPage(emptyList(), null)
                override suspend fun uploadJpeg(
                    originalPath: String,
                    previewPath: String,
                    filename: String,
                    capturedAtMs: Long?,
                    orientation: Int,
                    fallbackTimeZone: PhotoTimeZone?,
                ): Result<PhotoUploadReceipt> {
                    started.complete(Unit)
                    awaitCancellation()
                }
            }
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
                1,
            )
            val updates = mutableListOf<PhotoTransfer>()
            val job = launch { coordinator.upload(queued, updates::add) }
            started.await()

            job.cancelAndJoin()

            val persisted = checkNotNull(catalog.transfer(queued.id))
            assertEquals(PhotoTransferState.Queued, persisted.state)
            assertEquals("Transfer interrupted", persisted.lastError)
            assertEquals(PhotoTransferState.Queued, updates.last().state)
            assertTrue(File(persisted.localPath).isFile)
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun cancellingUploadWhileRunningStateIsPersistedDurablyRequeuesIt() = runBlocking {
        val root = createTempDirectory("photo-upload-cancel-running-write").toFile()
        try {
            val source = File(root, "upload.jpg").apply { writeBytes(jpegPayload()) }
            val preview = File(root, "preview.jpg").apply { writeBytes(jpegPayload()) }
            val runningWritePersisted = CompletableDeferred<Unit>()
            val catalog = FakeCatalog(
                suspendWrites = true,
                runningWritePersisted = runningWritePersisted,
            )
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
                1,
            )
            val updates = mutableListOf<PhotoTransfer>()
            val job = launch { coordinator.upload(queued, updates::add) }
            runningWritePersisted.await()

            job.cancelAndJoin()

            val persisted = checkNotNull(catalog.transfer(queued.id))
            assertEquals(PhotoTransferState.Queued, persisted.state)
            assertEquals("Transfer interrupted", persisted.lastError)
            assertEquals(PhotoTransferState.Queued, updates.last().state)
            assertEquals(0, port.uploadCalls)
            assertTrue(File(persisted.localPath).isFile)
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun cancellingUploadDuringTerminalWriteStillPersistsItsRemoteReceipt() = runBlocking {
        val root = createTempDirectory("photo-upload-cancel-terminal-write").toFile()
        try {
            val source = File(root, "upload.jpg").apply { writeBytes(jpegPayload()) }
            val preview = File(root, "preview.jpg").apply { writeBytes(jpegPayload()) }
            val terminalWriteStarted = CompletableDeferred<Unit>()
            val releaseTerminalWrite = CompletableDeferred<Unit>()
            val catalog = FakeCatalog(
                suspendWrites = true,
                terminalWriteStarted = terminalWriteStarted,
                releaseTerminalWrite = releaseTerminalWrite,
            )
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
                1,
            )
            val updates = mutableListOf<PhotoTransfer>()
            val job = launch { coordinator.upload(queued, updates::add) }
            terminalWriteStarted.await()

            job.cancel()
            releaseTerminalWrite.complete(Unit)
            job.join()

            val persisted = checkNotNull(catalog.transfer(queued.id))
            assertEquals(PhotoTransferState.Succeeded, persisted.state)
            assertEquals("master-uploaded", persisted.assetId)
            assertEquals(PhotoTransferState.Succeeded, updates.last().state)
            assertEquals(1, port.uploadCalls)
            assertTrue(File(persisted.localPath).isFile)
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun uploadMetadataSidecarRoundTripsAndStillReadsVersionOne() = runBlocking {
        val root = createTempDirectory("photo-upload-sidecar").toFile()
        try {
            val source = File(root, "zoned.jpg").apply { writeBytes(jpegPayload()) }
            val preview = File(root, "preview.jpg").apply { writeBytes(jpegPayload()) }
            val uploadRoot = File(root, "uploads")
            val port = FakePort(byteArrayOf())
            val coordinator = PhotoTransferCoordinator(port, FakeCatalog(), File(root, "previews"), uploadRoot)

            val withoutZone = coordinator.planUpload(source.path, preview.path, source.name, "image/jpeg", 1, 99L)
            val sidecar = File(uploadRoot, withoutZone.localPath.substringAfterLast('/').removeSuffix(".original.jpg") + ".metadata")
            assertTrue(sidecar.isFile)
            assertEquals(
                UploadMetadata(orientation = 1, capturedAtMs = 99L, timeZone = null),
                PhotoTransferCoordinator.readUploadMetadata(sidecar),
            )

            // A sidecar written by the previous release carries no zone at all.
            java.io.DataOutputStream(sidecar.outputStream()).use { output ->
                output.writeInt(1)
                output.writeInt(3)
                output.writeLong(42L)
            }
            assertEquals(
                UploadMetadata(orientation = 3, capturedAtMs = 42L, timeZone = null),
                PhotoTransferCoordinator.readUploadMetadata(sidecar),
            )
            val completed = coordinator.upload(withoutZone)
            assertEquals(PhotoTransferState.Succeeded, completed.state)
            assertEquals(3, port.uploadOrientation)
            assertEquals(42L, port.uploadCapturedAtMs)
            assertEquals(null, port.uploadTimeZone)

            // A blank zone name is dropped rather than sent to the protocol layer.
            val other = File(root, "other.jpg").apply { writeBytes(jpegPayload() + byteArrayOf(9)) }
            val blankZone = coordinator.planUpload(
                other.path,
                preview.path,
                other.name,
                "image/jpeg",
                1,
                null,
                PhotoTimeZone("  ", 0),
            )
            coordinator.upload(blankZone)
            assertEquals(null, port.uploadTimeZone)
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
        originalSize = jpegPayload().size.toLong(),
        previewSize = jpegPayload().size.toLong(),
        capturedAtMs = 1,
        addedAtMs = 1,
        favorite = false,
        hidden = false,
    )

    private fun jpegPayload() = byteArrayOf(
        0xff.toByte(), 0xd8.toByte(), 0xff.toByte(), 0xe0.toByte(), 0, 1, 2,
    )

    private fun ftypPayload(majorBrand: String, vararg compatibleBrands: String): ByteArray {
        val size = 16 + compatibleBrands.size * 4
        return byteArrayOf(0, 0, 0, size.toByte()) +
            "ftyp".toByteArray() +
            majorBrand.toByteArray() +
            byteArrayOf(0, 0, 0, 0) +
            compatibleBrands.joinToString(separator = "").toByteArray()
    }

    private fun tiffPayload(dng: Boolean): ByteArray = byteArrayOf(
        'I'.code.toByte(), 'I'.code.toByte(), 42, 0,
        8, 0, 0, 0,
        1, 0,
        if (dng) 0x12 else 0x00,
        if (dng) 0xc6.toByte() else 0x01,
        1, 0,
        4, 0, 0, 0,
        1, 4, 0, 0,
    )

    private data class FormatCase(
        val bytes: ByteArray,
        val mediaKind: PhotoMediaKind,
        val extension: String,
        val mimeType: String,
    )

    private class FakePort(private val payload: ByteArray) : PhotosPort {
        var calls: Int = 0
            private set
        var uploadCalls: Int = 0
            private set
        var originalCalls: Int = 0
            private set
        var livePhotoVideoCalls: Int = 0
            private set
        var uploadOrientation: Int? = null
            private set
        var uploadCapturedAtMs: Long? = null
            private set
        var uploadTimeZone: PhotoTimeZone? = null
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

        override suspend fun downloadLivePhotoVideo(
            asset: PhotoSummary,
            destPath: String,
            onProgress: (Long, Long) -> Unit,
        ): Result<Unit> = runCatching {
            livePhotoVideoCalls += 1
            File(destPath).apply { parentFile?.mkdirs() }.writeBytes(payload)
            onProgress(payload.size.toLong(), payload.size.toLong())
        }

        override suspend fun uploadJpeg(
            originalPath: String,
            previewPath: String,
            filename: String,
            capturedAtMs: Long?,
            orientation: Int,
            fallbackTimeZone: PhotoTimeZone?,
        ): Result<PhotoUploadReceipt> = runCatching {
            check(File(originalPath).isFile)
            check(File(previewPath).isFile)
            uploadCalls += 1
            uploadOrientation = orientation
            uploadCapturedAtMs = capturedAtMs
            uploadTimeZone = fallbackTimeZone
            PhotoUploadReceipt("master-uploaded", "asset-uploaded")
        }
    }

    private class FakeCatalog(
        private val suspendWrites: Boolean = false,
        private val runningWritePersisted: CompletableDeferred<Unit>? = null,
        private val terminalWriteStarted: CompletableDeferred<Unit>? = null,
        private val releaseTerminalWrite: CompletableDeferred<Unit>? = null,
    ) : PhotosCatalog {
        private var metadata = CachedPhotos()
        private val transferRows = linkedMapOf<String, PhotoTransfer>()

        override suspend fun loadMetadata() = metadata
        override suspend fun replaceMetadata(assets: List<PhotoSummary>, nextCursor: String?) {
            metadata = CachedPhotos(assets, nextCursor)
        }

        override suspend fun transfers() = transferRows.values.toList()
        override suspend fun transfer(id: String) = transferRows[id]
        override suspend fun putTransfer(transfer: PhotoTransfer) {
            if (suspendWrites) {
                withContext(Dispatchers.IO) {
                    if (transfer.state == PhotoTransferState.Succeeded && terminalWriteStarted != null) {
                        terminalWriteStarted.complete(Unit)
                        checkNotNull(releaseTerminalWrite).await()
                    }
                    transferRows[transfer.id] = transfer
                    if (transfer.state == PhotoTransferState.Running && runningWritePersisted != null) {
                        runningWritePersisted.complete(Unit)
                        awaitCancellation()
                    }
                }
            } else {
                transferRows[transfer.id] = transfer
            }
        }

        override suspend fun recoverInterruptedTransfers() = Unit
    }
}

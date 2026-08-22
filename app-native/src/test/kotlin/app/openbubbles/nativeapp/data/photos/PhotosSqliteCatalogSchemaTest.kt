package app.openbubbles.nativeapp.data.photos

import app.openbubbles.core.photos.PhotoMediaKind
import app.openbubbles.core.photos.PhotoSummary
import java.security.MessageDigest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class PhotosSqliteCatalogSchemaTest {
    @Test
    fun versionTwoSchemaIsPinned() {
        assertEquals(2, PhotosSqliteCatalog.DATABASE_VERSION)
        assertEquals(
            "ff74808d261ce6a0314451f7ecf0fc35f491efe6edfe7c319bfc579a62ea27c5",
            PhotosSqliteCatalog.CREATE_STATEMENTS.joinToString("\n").sha256(),
        )
    }

    @Test
    fun existingTransfersMigrateToManualWithoutAutomaticUploadConsent() {
        assertEquals(
            listOf("ALTER TABLE photo_transfers ADD COLUMN origin TEXT NOT NULL DEFAULT 'Manual'"),
            PhotosSqliteCatalog.migrationStatements(1, 2),
        )
    }

    @Test
    fun versionBumpCannotSilentlySkipAMigration() {
        assertEquals(
            emptyList(),
            PhotosSqliteCatalog.migrationStatements(1, 1),
        )
        assertEquals(
            emptyList(),
            PhotosSqliteCatalog.migrationStatements(2, 2),
        )
        assertFailsWith<IllegalStateException> {
            PhotosSqliteCatalog.migrationStatements(2, 3)
        }
        assertFailsWith<IllegalStateException> {
            PhotosSqliteCatalog.migrationStatements(1, 3)
        }
    }

    @Test
    fun accountCleanupCoversTransfersMetadataAndCursorState() {
        assertEquals(
            listOf("photo_transfers", "photo_assets", "photo_sync_state"),
            PhotosSqliteCatalog.ACCOUNT_CLEAR_TABLES,
        )
    }

    @Test
    fun cumulativePaginationWritesOnlyNewAssets() {
        var previous = emptyMap<String, PhotoSummary>()
        var totalAssetWrites = 0
        val pageSize = 25
        val pageCount = 20

        repeat(pageCount) { page ->
            val cumulativeAssets = List((page + 1) * pageSize) { index -> photo("asset-$index") }
            val mutation = planPhotoMetadataMutation(previous, cumulativeAssets)

            assertEquals(pageSize, mutation.insertedAssets.size)
            assertTrue(mutation.updatedAssets.isEmpty())
            assertTrue(mutation.removedAssetIds.isEmpty())
            totalAssetWrites += mutation.assetWriteCount
            previous = mutation.assetsById
        }

        // A delete/reinsert implementation performs 5,250 inserts here;
        // differential pagination touches each of the 500 assets exactly once.
        assertEquals(pageCount * pageSize, totalAssetWrites)
    }

    @Test
    fun refreshUpdatesOnlyChangedRecordsAndDeletesMissingAssets() {
        val unchanged = photo("unchanged")
        val before = photo("changed", filename = "before.jpg")
        val after = before.copy(filename = "after.jpg", favorite = true)
        val removed = photo("removed")
        val inserted = photo("inserted")
        val previous = listOf(unchanged, before, removed).associateBy(PhotoSummary::id)

        val mutation = planPhotoMetadataMutation(previous, listOf(unchanged, after, inserted))

        assertEquals(listOf(inserted), mutation.insertedAssets)
        assertEquals(listOf(after), mutation.updatedAssets)
        assertEquals(listOf(removed.id), mutation.removedAssetIds)
        assertEquals(3, mutation.assetWriteCount)
        assertEquals(listOf(unchanged.id, after.id, inserted.id), mutation.assetsById.keys.toList())
    }

    @Test
    fun unchangedSnapshotsDoNotRewriteExistingAssets() {
        val assets = List(5) { index -> photo("asset-$index") }

        val mutation = planPhotoMetadataMutation(assets.associateBy(PhotoSummary::id), assets)

        assertTrue(mutation.insertedAssets.isEmpty())
        assertTrue(mutation.updatedAssets.isEmpty())
        assertTrue(mutation.removedAssetIds.isEmpty())
        assertEquals(0, mutation.assetWriteCount)
    }

    @Test
    fun duplicateMasterIdsUseTheirNewestMetadata() {
        val original = photo("duplicate", filename = "before.jpg")
        val latest = original.copy(filename = "after.jpg", hidden = true)
        val other = photo("other")

        val first = planPhotoMetadataMutation(emptyMap(), listOf(original, other, latest))

        assertEquals(listOf(latest, other), first.insertedAssets)
        assertEquals(latest, first.assetsById[original.id])
        assertEquals(2, first.assetWriteCount)

        val updated = latest.copy(favorite = true)
        val second = planPhotoMetadataMutation(first.assetsById, listOf(latest, other, updated))

        assertTrue(second.insertedAssets.isEmpty())
        assertEquals(listOf(updated), second.updatedAssets)
        assertTrue(second.removedAssetIds.isEmpty())
        assertEquals(1, second.assetWriteCount)
    }

    @Test
    fun emptyReplacementRetainsRemoteDeletionSemantics() {
        val previous = List(3) { index -> photo("asset-$index") }.associateBy(PhotoSummary::id)

        val mutation = planPhotoMetadataMutation(previous, emptyList())

        assertTrue(mutation.insertedAssets.isEmpty())
        assertTrue(mutation.updatedAssets.isEmpty())
        assertEquals(previous.keys.toList(), mutation.removedAssetIds)
        assertTrue(mutation.assetsById.isEmpty())
    }

    @Test
    fun remoteDeletionQueriesStayWithinTheirBindArgumentBudget() {
        val ids = List(MAX_PHOTO_METADATA_DELETE_BATCH_SIZE * 2 + 1) { "asset-$it" }

        val batches = photoMetadataDeletionBatches(ids)

        assertEquals(listOf(250, 250, 1), batches.map { it.size })
        assertEquals(ids, batches.flatten())
        assertTrue(batches.all { it.size <= MAX_PHOTO_METADATA_DELETE_BATCH_SIZE })
        assertTrue(photoMetadataDeletionBatches(emptyList()).isEmpty())
    }

    @Test
    fun metadataRevisionsAlwaysAdvanceAcrossClockCollisions() {
        assertEquals(120L, nextPhotoMetadataRevision(previousRevision = null, nowMs = 120))
        assertEquals(121L, nextPhotoMetadataRevision(previousRevision = 120, nowMs = 120))
        assertEquals(121L, nextPhotoMetadataRevision(previousRevision = 120, nowMs = 1))
        assertEquals(140L, nextPhotoMetadataRevision(previousRevision = 120, nowMs = 140))
        assertFailsWith<IllegalStateException> {
            nextPhotoMetadataRevision(previousRevision = Long.MAX_VALUE, nowMs = 140)
        }
    }

    @Test
    fun accountCleanupCannotReuseAnotherCatalogInstancesRevision() {
        val revisions = PhotoMetadataRevisionAllocator()
        val firstAccount = revisions.reserve(previousRevision = null, nowMs = 500)
        val advancedFirstAccount = revisions.reserve(previousRevision = firstAccount, nowMs = 500)

        // Account cleanup removes photo_sync_state, but another helper may still
        // retain its old-account baseline while the wall clock remains unchanged.
        val replacementAccount = revisions.reserve(previousRevision = null, nowMs = 500)
        val afterBackwardsClock = revisions.reserve(previousRevision = null, nowMs = 1)

        assertEquals(500L, firstAccount)
        assertEquals(501L, advancedFirstAccount)
        assertEquals(502L, replacementAccount)
        assertEquals(503L, afterBackwardsClock)
        assertFailsWith<IllegalStateException> {
            PhotoMetadataRevisionAllocator(Long.MAX_VALUE).reserve(previousRevision = null, nowMs = 500)
        }
    }

    private fun photo(id: String, filename: String = "$id.jpg") = PhotoSummary(
        id = id,
        assetId = "asset-$id",
        filename = filename,
        mediaKind = PhotoMediaKind.Image,
        livePhoto = false,
        width = 4032,
        height = 3024,
        originalSize = 4_200_000,
        previewSize = 102_000,
        capturedAtMs = 1_000,
        addedAtMs = 1_000,
        favorite = false,
        hidden = false,
    )

    private fun String.sha256(): String = MessageDigest.getInstance("SHA-256")
        .digest(toByteArray(Charsets.UTF_8))
        .joinToString(separator = "") { byte -> "%02x".format(byte.toInt() and 0xff) }
}

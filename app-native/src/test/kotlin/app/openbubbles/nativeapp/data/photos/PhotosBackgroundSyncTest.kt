package app.openbubbles.nativeapp.data.photos

import android.Manifest
import app.openbubbles.core.photos.PhotoResourceKind
import app.openbubbles.core.photos.PhotoTransfer
import app.openbubbles.core.photos.PhotoTransferDirection
import app.openbubbles.core.photos.PhotoTransferOrigin
import app.openbubbles.core.photos.PhotoTransferState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest

class PhotosBackgroundSyncTest {
    @Test
    fun backupPermissionsFollowTheOsVersion() {
        assertEquals(
            listOf(Manifest.permission.READ_EXTERNAL_STORAGE, Manifest.permission.ACCESS_MEDIA_LOCATION),
            photoBackupPermissions(29),
        )
        assertEquals(listOf(Manifest.permission.READ_EXTERNAL_STORAGE), photoBackupPermissions(28))
        assertEquals(
            listOf(Manifest.permission.READ_MEDIA_IMAGES, Manifest.permission.ACCESS_MEDIA_LOCATION),
            photoBackupPermissions(33),
        )
        assertEquals(
            listOf(
                Manifest.permission.READ_MEDIA_IMAGES,
                Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED,
                Manifest.permission.ACCESS_MEDIA_LOCATION,
            ),
            photoBackupPermissions(34),
        )
        // Keep requesting partial access so Android's permission prompt is exact,
        // but it can never substitute for the full-library camera-backup grant.
        assertFalse(Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED in photoBackupReadPermissions(35))
        assertFalse(Manifest.permission.ACCESS_MEDIA_LOCATION in photoBackupReadPermissions(35))
    }

    @Test
    fun cameraBackupRequiresFullLibraryAccessAndUnredactedMetadata() {
        fun granted(sdkInt: Int, vararg permissions: String): Boolean =
            hasRequiredPhotoBackupGrants(sdkInt, permissions.toSet()::contains)

        assertFalse(
            granted(
                35,
                Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED,
                Manifest.permission.ACCESS_MEDIA_LOCATION,
            ),
        )
        assertFalse(granted(35, Manifest.permission.READ_MEDIA_IMAGES))
        assertTrue(
            granted(
                35,
                Manifest.permission.READ_MEDIA_IMAGES,
                Manifest.permission.ACCESS_MEDIA_LOCATION,
            ),
        )
        assertFalse(granted(33, Manifest.permission.READ_MEDIA_IMAGES))
        assertTrue(
            granted(
                33,
                Manifest.permission.READ_MEDIA_IMAGES,
                Manifest.permission.ACCESS_MEDIA_LOCATION,
            ),
        )
        assertFalse(granted(29, Manifest.permission.READ_EXTERNAL_STORAGE))
        assertTrue(
            granted(
                29,
                Manifest.permission.READ_EXTERNAL_STORAGE,
                Manifest.permission.ACCESS_MEDIA_LOCATION,
            ),
        )
        assertTrue(granted(28, Manifest.permission.READ_EXTERNAL_STORAGE))
        assertFalse(granted(28, Manifest.permission.ACCESS_MEDIA_LOCATION))
    }

    @Test
    fun unreadableCameraBaselineFailsClosedButAnEmptyLibraryIsValid() {
        assertEquals(0L, photoBackupBaselineOrNull { 0L })
        assertEquals(42L, photoBackupBaselineOrNull { 42L })
        assertNull(photoBackupBaselineOrNull { null })
        assertNull(photoBackupBaselineOrNull { -1L })
        assertNull(photoBackupBaselineOrNull { error("MediaStore is unavailable") })
        assertFailsWith<CancellationException> {
            photoBackupBaselineOrNull { throw CancellationException("Cancelled") }
        }
    }

    @Test
    fun failedCameraStagingNeverAdvancesTheWatermark() = runTest {
        var watermark = 41L

        assertFailsWith<IllegalStateException> {
            stageCameraBackupImage(
                mediaId = 42L,
                stage = { error("The camera photo could not be staged") },
                recordProcessed = { watermark = it },
            )
        }
        assertEquals(41L, watermark)

        stageCameraBackupImage(
            mediaId = 42L,
            stage = {},
            recordProcessed = { watermark = it },
        )
        assertEquals(42L, watermark)
    }

    @Test
    fun dcimSelectionTargetsCameraImagesOnly() {
        val (modern, modernArgs) = dcimImageSelection(34)
        assertTrue(modern.contains("relative_path LIKE ?"))
        assertTrue(modern.contains("relative_path NOT LIKE ?"))
        assertTrue(modern.contains("mime_type LIKE ?"))
        assertEquals(listOf("DCIM/%", "DCIM/iCloud/%", "image/%"), modernArgs.toList())

        val (legacy, legacyArgs) = dcimImageSelection(28)
        assertTrue(legacy.contains("_data LIKE ?"))
        assertTrue(legacy.contains("_data NOT LIKE ?"))
        assertEquals(listOf("%/DCIM/%", "%/DCIM/iCloud/%", "image/%"), legacyArgs.toList())
    }

    @Test
    fun automaticUploadsRetryFailuresOnlyWhileAttemptsRemain() {
        fun upload(
            state: PhotoTransferState,
            attempts: Int,
            origin: PhotoTransferOrigin = PhotoTransferOrigin.CameraBackup,
        ) = PhotoTransfer(
            id = "upload:$state:$attempts",
            assetId = null,
            direction = PhotoTransferDirection.Upload,
            resourceKind = PhotoResourceKind.Original,
            localPath = "/tmp/x.jpg",
            filename = "x.jpg",
            mimeType = "image/jpeg",
            state = state,
            attemptCount = attempts,
            createdAtMs = 1,
            updatedAtMs = 1,
            origin = origin,
        )

        assertTrue(shouldAutoUpload(upload(PhotoTransferState.Queued, 0)))
        assertTrue(shouldAutoUpload(upload(PhotoTransferState.Failed, PhotosBackgroundSync.MAX_AUTOMATIC_ATTEMPTS - 1)))
        assertFalse(shouldAutoUpload(upload(PhotoTransferState.Queued, 0, PhotoTransferOrigin.Manual)))
        assertFalse(shouldAutoUpload(upload(PhotoTransferState.Failed, 1, PhotoTransferOrigin.Manual)))
        assertFalse(shouldAutoUpload(upload(PhotoTransferState.Failed, PhotosBackgroundSync.MAX_AUTOMATIC_ATTEMPTS)))
        assertFalse(shouldAutoUpload(upload(PhotoTransferState.Succeeded, 1)))
        assertFalse(shouldAutoUpload(upload(PhotoTransferState.Running, 1)))
        assertFalse(shouldAutoUpload(upload(PhotoTransferState.Blocked, 0)))
        assertFalse(
            shouldAutoUpload(
                upload(PhotoTransferState.Queued, 0).copy(direction = PhotoTransferDirection.Download),
            ),
        )
    }

    @Test
    fun backupIsOptInAndBounded() {
        // The worker name is shared with the previously dormant worker so that
        // sign-out and disable also cancel work scheduled by older builds.
        assertEquals("openbubbles-icloud-photos-background-sync", PhotosBackgroundSync.PERIODIC_WORK_NAME)
        assertTrue(PhotosBackgroundSync.BATCH_LIMIT in 1..200)
        assertTrue(PhotosBackgroundSync.MAX_AUTOMATIC_ATTEMPTS in 1..10)
    }

    @Test
    fun cameraBackupNeverRestoresPersistentPushWhileBatterySaverIsEnabled() {
        assertEquals(
            PhotoBackupPushPolicy.BOUNDED_ON_DEMAND,
            photoBackupPushPolicy(hasLiveState = false, batterySaverEnabled = true),
        )
        assertEquals(
            PhotoBackupPushPolicy.BOUNDED_ON_DEMAND,
            photoBackupPushPolicy(hasLiveState = true, batterySaverEnabled = true),
        )
        assertEquals(
            PhotoBackupPushPolicy.RESTORE_PERSISTENT,
            photoBackupPushPolicy(hasLiveState = false, batterySaverEnabled = false),
        )
        assertEquals(
            PhotoBackupPushPolicy.EXISTING,
            photoBackupPushPolicy(hasLiveState = true, batterySaverEnabled = false),
        )
    }
}

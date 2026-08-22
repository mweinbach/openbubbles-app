package app.openbubbles.nativeapp.data.photos

import android.Manifest
import android.content.ContentResolver
import android.content.ContentUris
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import app.openbubbles.core.photos.PhotoTransfer
import app.openbubbles.core.photos.PhotoTransferCoordinator
import app.openbubbles.core.photos.PhotoTransferDirection
import app.openbubbles.core.photos.PhotoTransferState
import app.openbubbles.core.photos.PhotosAvailability
import app.openbubbles.core.photos.PhotosBrowser
import app.openbubbles.core.photos.UniffiPhotosPort
import app.openbubbles.nativeapp.data.PushStateHolder
import app.openbubbles.nativeapp.data.runAccountCleanupSteps
import app.openbubbles.nativeapp.service.BatterySaver
import app.openbubbles.nativeapp.service.NativePushService
import java.io.File
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import uniffi.rust_lib_bluebubbles.NativePushState

/**
 * Opt-in camera backup: once the user turns it on, photos that Android adds to
 * `DCIM` from that moment are staged and uploaded to iCloud Photos in the
 * background. It is a user switch, not a compile-time flag, and it owns its
 * own account-scoped state so sign-out can clear it.
 *
 * What it deliberately does not do: it never uploads photos that already
 * existed when it was enabled (those remain explicit picks or folder scans),
 * never touches videos (the Apple write contract is JPEG-only), and never
 * deletes anything anywhere.
 */
object PhotosBackgroundSync {
    private const val TAG = "PhotosBackup"
    private const val PREFS = "icloud_photos_backup"
    private const val KEY_ENABLED = "enabled"
    private const val KEY_WATERMARK_ID = "dcim_watermark_id"
    private const val KEY_LAST_PASS_MS = "last_pass_ms"

    /** Kept from the dormant worker so cancelling also covers older installs. */
    internal const val PERIODIC_WORK_NAME = "openbubbles-icloud-photos-background-sync"
    internal const val FOLLOW_UP_WORK_NAME = "openbubbles-icloud-photos-new-media"

    /** New DCIM items staged per pass; a longer backlog continues in a follow-up run. */
    internal const val BATCH_LIMIT = 60

    /** Automatic retries stop here; a manual tap in the uploads sheet can still retry. */
    internal const val MAX_AUTOMATIC_ATTEMPTS = 5

    /**
     * The explicit uploads sheet and the worker share one Apple write path, so
     * the same staged file is never uploaded twice at once.
     */
    internal val uploadGate = Mutex()

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun isEnabled(context: Context): Boolean = prefs(context).getBoolean(KEY_ENABLED, false)

    fun lastPassMs(context: Context): Long? =
        prefs(context).getLong(KEY_LAST_PASS_MS, 0L).takeIf { it > 0 }

    fun requiredPermissions(): Array<String> = photoBackupPermissions(Build.VERSION.SDK_INT).toTypedArray()

    /** Full or user-selected read access to device images. */
    fun hasMediaPermission(context: Context): Boolean =
        photoBackupReadPermissions(Build.VERSION.SDK_INT).any { permission ->
            ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
        }

    /**
     * Turns backup on or off. Enabling records the newest current DCIM id as
     * the watermark, so only photos added afterwards are picked up, then
     * schedules the work. Returns the resulting state; enabling without media
     * permission is refused.
     */
    suspend fun setEnabled(context: Context, enabled: Boolean): Boolean = withContext(Dispatchers.IO) {
        val app = context.applicationContext
        if (!enabled) {
            prefs(app).edit { putBoolean(KEY_ENABLED, false) }
            runCatching {
                WorkManager.getInstance(app).apply {
                    cancelUniqueWork(PERIODIC_WORK_NAME)
                    cancelUniqueWork(FOLLOW_UP_WORK_NAME)
                }
            }
            return@withContext false
        }
        if (!hasMediaPermission(app)) return@withContext false
        val watermark = runCatching { currentMaxDcimImageId(app.contentResolver) }.getOrDefault(0L)
        prefs(app).edit {
            putBoolean(KEY_ENABLED, true)
            putLong(KEY_WATERMARK_ID, watermark)
        }
        schedule(app)
        true
    }

    /** Hourly safety net plus a MediaStore change trigger for prompt pickup. */
    fun schedule(context: Context) {
        val app = context.applicationContext
        val request = PeriodicWorkRequestBuilder<PhotosBackgroundSyncWorker>(1, TimeUnit.HOURS)
            .setConstraints(constraints())
            .build()
        runCatching {
            WorkManager.getInstance(app).enqueueUniquePeriodicWork(
                PERIODIC_WORK_NAME,
                ExistingPeriodicWorkPolicy.UPDATE,
                request,
            )
        }.onFailure { Log.w(TAG, "could not schedule periodic backup: ${it.message}") }
        armFollowUp(app, backlog = false)
    }

    /**
     * A one-time run either soon (a batch was cut short by [BATCH_LIMIT]) or
     * when MediaStore reports new images. Re-armed at the end of every pass.
     */
    internal fun armFollowUp(context: Context, backlog: Boolean) {
        val builder = OneTimeWorkRequestBuilder<PhotosBackgroundSyncWorker>()
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .setRequiresBatteryNotLow(true)
            .setRequiresStorageNotLow(true)
        if (backlog) {
            builder.setInitialDelay(30, TimeUnit.SECONDS)
        } else {
            constraints
                .addContentUriTrigger(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, true)
                .setTriggerContentUpdateDelay(30, TimeUnit.SECONDS)
                .setTriggerContentMaxDelay(10, TimeUnit.MINUTES)
        }
        runCatching {
            WorkManager.getInstance(context).enqueueUniqueWork(
                FOLLOW_UP_WORK_NAME,
                ExistingWorkPolicy.REPLACE,
                builder.setConstraints(constraints.build()).build(),
            )
        }.onFailure { Log.w(TAG, "could not arm backup follow-up: ${it.message}") }
    }

    private fun constraints(): Constraints = Constraints.Builder()
        .setRequiredNetworkType(NetworkType.CONNECTED)
        .setRequiresBatteryNotLow(true)
        .setRequiresStorageNotLow(true)
        .build()

    /** Sign-out waits until any background pass is actually cancelled. */
    fun cancelAndAwait(context: Context) {
        WorkManager.getInstance(context).apply {
            cancelUniqueWork(PERIODIC_WORK_NAME).result.get()
            cancelUniqueWork(FOLLOW_UP_WORK_NAME).result.get()
        }
    }

    /** The switch, watermark, and schedule belong to the signed-in account. */
    suspend fun clearAccountState(context: Context): Result<Unit> = withContext(Dispatchers.IO) {
        runAccountCleanupSteps(
            { cancelAndAwait(context) },
            {
                check(prefs(context).edit().clear().commit()) {
                    "Could not clear Photos backup preferences"
                }
            },
        )
    }

    internal fun watermark(context: Context): Long = prefs(context).getLong(KEY_WATERMARK_ID, 0L)

    internal fun recordProcessed(context: Context, mediaId: Long) {
        if (mediaId > watermark(context)) {
            prefs(context).edit { putLong(KEY_WATERMARK_ID, mediaId) }
        }
    }

    internal fun currentMaxDcimImageId(resolver: ContentResolver): Long {
        val (selection, args) = dcimImageSelection(Build.VERSION.SDK_INT)
        return resolver.query(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            arrayOf(MediaStore.Images.Media._ID),
            selection,
            args,
            "${MediaStore.Images.Media._ID} DESC",
        )?.use { cursor -> if (cursor.moveToFirst()) cursor.getLong(0) else 0L } ?: 0L
    }

    /** DCIM images newer than [afterId], oldest first, at most [limit]. */
    internal fun newDcimImages(resolver: ContentResolver, afterId: Long, limit: Int): List<DeviceImage> {
        val (selection, args) = dcimImageSelection(Build.VERSION.SDK_INT)
        val images = mutableListOf<DeviceImage>()
        resolver.query(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            arrayOf(
                MediaStore.Images.Media._ID,
                MediaStore.Images.Media.DISPLAY_NAME,
                MediaStore.Images.Media.MIME_TYPE,
            ),
            "$selection AND ${MediaStore.Images.Media._ID} > ?",
            args + afterId.toString(),
            "${MediaStore.Images.Media._ID} ASC",
        )?.use { cursor ->
            val idIndex = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
            val nameIndex = cursor.getColumnIndex(MediaStore.Images.Media.DISPLAY_NAME)
            val mimeIndex = cursor.getColumnIndex(MediaStore.Images.Media.MIME_TYPE)
            while (cursor.moveToNext() && images.size < limit) {
                val id = cursor.getLong(idIndex)
                images += DeviceImage(
                    id = id,
                    uri = ContentUris.withAppendedId(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, id),
                    displayName = if (nameIndex >= 0 && !cursor.isNull(nameIndex)) cursor.getString(nameIndex) else null,
                    mimeType = if (mimeIndex >= 0 && !cursor.isNull(mimeIndex)) cursor.getString(mimeIndex) else null,
                )
            }
        }
        return images
    }

    internal data class PassOutcome(val staged: Int, val uploaded: Int, val backlog: Boolean)

    /**
     * One background pass: stage new DCIM images, upload what is queued, then
     * refresh the cached library so the grid shows the results next time.
     */
    internal suspend fun runPass(context: Context, state: NativePushState): PassOutcome {
        val app = context.applicationContext
        val port = UniffiPhotosPort(state)
        val catalog = PhotosSqliteCatalog(app)
        try {
            val coordinator = PhotoTransferCoordinator(
                port = port,
                catalog = catalog,
                previewRoot = File(app.filesDir, "icloud_photos/previews"),
                uploadRoot = File(app.filesDir, "icloud_photos/uploads"),
                originalRoot = File(app.filesDir, "icloud_photos/originals"),
            )
            val images = newDcimImages(app.contentResolver, watermark(app), BATCH_LIMIT)
            var staged = 0
            for (image in images) {
                currentCoroutineContext().ensureActive()
                if (image.mimeType?.startsWith("image/") == false) {
                    recordProcessed(app, image.id)
                    continue
                }
                var candidate: PickedPhotoUpload? = null
                try {
                    candidate = preparePhotoUploadCandidate(app, image.uri)
                    coordinator.planUpload(
                        sourcePath = candidate.file.absolutePath,
                        previewPath = candidate.previewFile.absolutePath,
                        filename = candidate.filename,
                        mimeType = candidate.mimeType,
                        orientation = candidate.orientation,
                        capturedAtMs = candidate.capturedAtMs,
                        timeZone = candidate.timeZone,
                    )
                    staged += 1
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (error: Throwable) {
                    // An undecodable or vanished item must not block everything after it.
                    Log.w(TAG, "skipping a DCIM item that could not be staged: ${error.javaClass.simpleName}")
                } finally {
                    candidate?.file?.delete()
                    candidate?.previewFile?.delete()
                }
                recordProcessed(app, image.id)
            }

            var uploaded = 0
            val pending = catalog.transfers().filter(::shouldAutoUpload)
            for (transfer in pending) {
                currentCoroutineContext().ensureActive()
                val completed = uploadGate.withLock { coordinator.upload(transfer) }
                if (completed.state == PhotoTransferState.Succeeded) uploaded += 1
            }

            if (staged > 0 || uploaded > 0) {
                runCatching {
                    val snapshot = PhotosBrowser(port).initial(
                        cachedAssets = catalog.loadMetadata().assets,
                    )
                    if (snapshot.access.availability == PhotosAvailability.Ready) {
                        catalog.replaceMetadata(snapshot.assets, snapshot.nextCursor)
                    }
                }
            }
            prefs(app).edit { putLong(KEY_LAST_PASS_MS, System.currentTimeMillis()) }
            return PassOutcome(staged, uploaded, backlog = images.size >= BATCH_LIMIT)
        } finally {
            catalog.close()
        }
    }
}

internal data class DeviceImage(
    val id: Long,
    val uri: Uri,
    val displayName: String?,
    val mimeType: String?,
)

/** Runtime permissions the backup switch asks for on this OS version. */
internal fun photoBackupPermissions(sdkInt: Int): List<String> = buildList {
    addAll(photoBackupReadPermissions(sdkInt))
    if (sdkInt >= Build.VERSION_CODES.Q) add(Manifest.permission.ACCESS_MEDIA_LOCATION)
}

/** Any one of these grants enough read access for a backup pass. */
internal fun photoBackupReadPermissions(sdkInt: Int): List<String> = when {
    sdkInt >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE -> listOf(
        Manifest.permission.READ_MEDIA_IMAGES,
        Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED,
    )
    sdkInt >= Build.VERSION_CODES.TIRAMISU -> listOf(Manifest.permission.READ_MEDIA_IMAGES)
    else -> listOf(Manifest.permission.READ_EXTERNAL_STORAGE)
}

/** MediaStore selection for camera-roll images: anything under `DCIM/`. */
internal fun dcimImageSelection(sdkInt: Int): Pair<String, Array<String>> =
    if (sdkInt >= Build.VERSION_CODES.Q) {
        "${MediaStore.Images.Media.RELATIVE_PATH} LIKE ? AND ${MediaStore.Images.Media.MIME_TYPE} LIKE ?" to
            arrayOf("DCIM/%", "image/%")
    } else {
        @Suppress("DEPRECATION")
        "${MediaStore.Images.Media.DATA} LIKE ? AND ${MediaStore.Images.Media.MIME_TYPE} LIKE ?" to
            arrayOf("%/DCIM/%", "image/%")
    }

/** Queued rows always upload; failed rows retry a bounded number of times. */
internal fun shouldAutoUpload(transfer: PhotoTransfer): Boolean =
    transfer.direction == PhotoTransferDirection.Upload && when (transfer.state) {
        PhotoTransferState.Queued -> true
        PhotoTransferState.Failed -> transfer.attemptCount < PhotosBackgroundSync.MAX_AUTOMATIC_ATTEMPTS
        else -> false
    }

internal enum class PhotoBackupPushPolicy { EXISTING, BOUNDED_ON_DEMAND, RESTORE_PERSISTENT }

internal fun photoBackupPushPolicy(
    hasLiveState: Boolean,
    batterySaverEnabled: Boolean,
): PhotoBackupPushPolicy = when {
    batterySaverEnabled -> PhotoBackupPushPolicy.BOUNDED_ON_DEMAND
    hasLiveState -> PhotoBackupPushPolicy.EXISTING
    else -> PhotoBackupPushPolicy.RESTORE_PERSISTENT
}

class PhotosBackgroundSyncWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val context = applicationContext
        if (!PhotosBackgroundSync.isEnabled(context)) return Result.success()
        if (!PhotosBackgroundSync.hasMediaPermission(context)) {
            // The user revoked photo access; keep the switch so re-granting resumes.
            PhotosBackgroundSync.armFollowUp(context, backlog = false)
            return Result.success()
        }
        val policy = photoBackupPushPolicy(
            hasLiveState = PushStateHolder.state != null,
            batterySaverEnabled = BatterySaver.isEnabled(context),
        )
        val state = when (policy) {
            PhotoBackupPushPolicy.EXISTING -> PushStateHolder.state
            PhotoBackupPushPolicy.BOUNDED_ON_DEMAND,
            PhotoBackupPushPolicy.RESTORE_PERSISTENT,
            -> if (NativePushService.ensureOnDemandSession(context)) PushStateHolder.state else null
        } ?: return Result.retry()
        val owner = currentCoroutineContext()[Job] ?: return Result.retry()
        val session = PhotosWorkRegistry.register()
        if (!session.adopt(owner)) {
            session.close()
            // Sign-out fenced this account between the state lookup and worker
            // registration. Never rearm a stale account's upload schedule.
            return Result.success()
        }
        return try {
            val outcome = PhotosBackgroundSync.runPass(context, state)
            PhotosBackgroundSync.armFollowUp(context, backlog = outcome.backlog)
            Result.success()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Throwable) {
            Log.w("PhotosBackup", "background pass failed: ${error.javaClass.simpleName}")
            if (PhotosBackgroundSync.isEnabled(context)) {
                PhotosBackgroundSync.armFollowUp(context, backlog = false)
            }
            Result.retry()
        } finally {
            // close() cancels every tracked job, so release the currently
            // executing WorkManager owner before disposing its session.
            session.release(owner)
            session.close()
        }
    }
}

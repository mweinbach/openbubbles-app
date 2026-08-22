package app.openbubbles.nativeapp.sms

import android.content.ContentUris
import android.content.Context
import android.provider.Telephony
import android.util.Log
import androidx.work.BackoffPolicy
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CancellationException

/** Provider-backed MMS retries survive process death and transient filesystem/provider failures. */
internal object MmsIngestionRetry {
    private const val TAG = "MmsIngestionRetry"
    internal const val INPUT_PROVIDER_ID = "provider_id"
    internal const val INPUT_RECEIVED_AT_MS = "received_at_ms"
    internal const val MAX_DISCOVERY_ATTEMPTS = 8
    private const val DISCOVERY_WORK_PREFIX = "openbubbles-mms-discovery-"
    private const val PROVIDER_WORK_PREFIX = "openbubbles-mms-provider-"

    fun scheduleDiscovery(context: Context, receivedAtMs: Long) {
        enqueue(
            context = context,
            name = "$DISCOVERY_WORK_PREFIX$receivedAtMs",
            providerId = null,
            receivedAtMs = receivedAtMs,
        )
    }

    fun scheduleProvider(context: Context, providerId: Long) {
        enqueue(
            context = context,
            name = "$PROVIDER_WORK_PREFIX$providerId",
            providerId = providerId,
            receivedAtMs = null,
        )
    }

    private fun enqueue(context: Context, name: String, providerId: Long?, receivedAtMs: Long?) {
        val request = OneTimeWorkRequestBuilder<MmsIngestionWorker>()
            .setInputData(
                workDataOf(
                    INPUT_PROVIDER_ID to (providerId ?: -1L),
                    INPUT_RECEIVED_AT_MS to (receivedAtMs ?: -1L),
                ),
            )
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 10, TimeUnit.SECONDS)
            .build()
        runCatching {
            WorkManager.getInstance(context.applicationContext)
                .enqueueUniqueWork(name, ExistingWorkPolicy.KEEP, request)
        }.onFailure { failure ->
            Log.w(TAG, "Could not schedule durable MMS ingest retry", failure)
        }
    }
}

/** Exact provider IDs and not-yet-visible push notifications both retain a durable retry owner. */
class MmsIngestionWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val context = applicationContext
        if (!SmsPermissions.canReadTelephony(context)) return Result.retry()
        val providerId = inputData.getLong(MmsIngestionRetry.INPUT_PROVIDER_ID, -1L)
        val receivedAtMs = inputData.getLong(MmsIngestionRetry.INPUT_RECEIVED_AT_MS, -1L)
        val ingested = try {
            if (providerId >= 0L) {
                MmsReceiver().ingestProviderMms(
                    context,
                    ContentUris.withAppendedId(Telephony.Mms.CONTENT_URI, providerId),
                )
            } else if (receivedAtMs >= 0L) {
                MmsReceiver().ingestRecentProviderMms(context, receivedAtMs)
            } else {
                return Result.failure()
            }
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (failure: Exception) {
            Log.w("MmsIngestionWorker", "MMS provider ingest will retry", failure)
            false
        }
        return when {
            ingested -> Result.success()
            providerId < 0L && runAttemptCount + 1 >= MmsIngestionRetry.MAX_DISCOVERY_ATTEMPTS -> {
                Log.w("MmsIngestionWorker", "No inbox MMS appeared during the bounded discovery window")
                Result.failure()
            }
            else -> Result.retry()
        }
    }
}

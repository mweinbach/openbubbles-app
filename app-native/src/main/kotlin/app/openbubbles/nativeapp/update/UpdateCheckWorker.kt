package app.openbubbles.nativeapp.update

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import java.io.IOException

/**
 * Daily/self-serve update check. Short by design: fetch the feed, download if
 * applicable, post the "Update ready" notification. Installing always stays
 * behind a user action (notification tap or Settings button).
 *
 * Transient network problems back off via [Result.retry]; every other failure
 * is terminal for this run — the next period (or manual check) tries again.
 */
class UpdateCheckWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        return try {
            when (val result = UpdateCoordinator.checkNow(applicationContext)) {
                is UpdateCoordinator.CheckResult.Done -> Result.success()
                is UpdateCoordinator.CheckResult.Failed -> {
                    Log.w("SelfUpdate", "check failed: ${result.message}")
                    Result.success()
                }
            }
        } catch (e: IOException) {
            Log.w("SelfUpdate", "transient network failure, will retry", e)
            Result.retry()
        }
    }
}

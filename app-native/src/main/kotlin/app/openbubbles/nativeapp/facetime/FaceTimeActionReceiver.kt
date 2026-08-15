package app.openbubbles.nativeapp.facetime

import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import app.openbubbles.nativeapp.data.PushStateHolder
import app.openbubbles.nativeapp.service.NativePushService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/** Handles call actions even when the heavy FaceTime activity is not alive. */
class FaceTimeActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val callUuid = intent.getStringExtra(EXTRA_CALL_UUID) ?: return
        val notificationId = intent.getIntExtra(EXTRA_NOTIFICATION_ID, callUuid.hashCode())
        val action = intent.action ?: return
        val pending = goAsync()
        val appContext = context.applicationContext
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                val state = PushStateHolder.state ?: run {
                    NativePushService.start(appContext)
                    withTimeoutOrNull(5_000) {
                        PushStateHolder.stateFlow.filterNotNull().first()
                    }
                }
                when (action) {
                    ACTION_DECLINE -> state?.declineFacetime(callUuid)
                    ACTION_END -> state?.cancelFacetime(callUuid)
                    else -> return@launch
                }
            } catch (error: Throwable) {
                Log.w("FaceTimeAction", "Unable to complete $action for $callUuid", error)
            } finally {
                appContext.getSystemService(NotificationManager::class.java)
                    ?.cancel(FtConstants.NEW_FACE_TIME_NOTIFICATION_TAG, notificationId)
                FaceTimeActivity.activeFaceTimeActivity
                    ?.takeIf { it.callUuid == callUuid }
                    ?.closeCallUi()
                pending.finish()
            }
        }
    }

    companion object {
        const val ACTION_DECLINE = "app.openbubbles.action.DECLINE_FACETIME"
        const val ACTION_END = "app.openbubbles.action.END_FACETIME"
        const val EXTRA_CALL_UUID = "callUuid"
        const val EXTRA_NOTIFICATION_ID = "notificationId"
    }
}

package app.openbubbles.nativeapp.service

import android.content.Context
import android.util.Log
import app.openbubbles.nativeapp.data.PushStateHolder
import app.openbubbles.nativeapp.facetime.CreateIncomingFaceTimeNotification
import app.openbubbles.nativeapp.facetime.FtIncomingCall
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import uniffi.rust_lib_bluebubbles.UFtMessage
import uniffi.rust_lib_bluebubbles.UPushMessage

/**
 * Routes FaceTime pushes to the ported call UI. Mirrors the Dart flow:
 * Ring -> resolve session (caller identity) -> incoming link (+rotate) ->
 * incoming-call notification; Decline / RespondedElsewhere -> cancel;
 * LeaveEvent for the active call -> end it.
 */
object FaceTimeDispatch {
    private const val TAG = "FaceTimeDispatch"

    /** Guid of the call we are currently ringing (double-ring guard). */
    @Volatile
    private var ringingCallGuid: String? = null

    fun onPushMessage(context: Context, msg: UPushMessage): Boolean {
        val ft = (msg as? UPushMessage.FaceTime)?.message ?: return false
        when (ft) {
            is UFtMessage.Ring -> onRing(context, ft.guid)
            is UFtMessage.Decline -> cancelRinging(context, ft.guid)
            is UFtMessage.RespondedElsewhere -> cancelRinging(context, ft.guid)
            is UFtMessage.LeaveEvent -> {
                // A participant left; if it was the active call, end the UI.
                app.openbubbles.nativeapp.facetime.FaceTimeActivity.activeFaceTimeActivity
                    ?.takeIf { it.callUuid == ft.guid }
                    ?.endCall()
            }
            else -> Unit
        }
        return true // consumed: not a chat message
    }

    private fun onRing(context: Context, guid: String) {
        if (ringingCallGuid == guid) return
        val state = PushStateHolder.state ?: return
        runBlocking {
            val ok = withContext(Dispatchers.IO) {
                runCatching {
                    val sessions = state.ftSessions()
                    val session = sessions.firstOrNull { it.groupId == guid }
                    val callerName = session?.members
                        ?.filter { it.handle !in (session.myHandles) }
                        ?.firstOrNull()
                        ?.let { it.nickname ?: it.handle }
                        ?: "FaceTime"
                    val link = state.getFtLink("nextincomingcall")
                    runCatching { state.rotateIncomingLinks() }
                    ringingCallGuid = guid
                    CreateIncomingFaceTimeNotification.create(
                        context,
                        FtIncomingCall(
                            notificationId = guid.hashCode(),
                            callUuid = guid,
                            title = callerName,
                            link = link,
                            name = callerName,
                            poster = null,
                            callerName = callerName,
                            callerAvatar = null,
                        ),
                    )
                }.onFailure { Log.w(TAG, "ring handling failed", it) }.isSuccess
            }
            if (!ok) ringingCallGuid = null
        }
    }

    private fun cancelRinging(context: Context, guid: String) {
        if (ringingCallGuid != guid) return
        ringingCallGuid = null
        runCatching {
            context.getSystemService(android.app.NotificationManager::class.java)
                ?.cancel(
                    app.openbubbles.nativeapp.facetime.FtConstants.NEW_FACE_TIME_NOTIFICATION_TAG,
                    guid.hashCode(),
                )
        }
    }
}

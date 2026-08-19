package app.openbubbles.nativeapp.service

import android.content.Context
import android.util.Log
import app.openbubbles.core.model.MessageMapper
import app.openbubbles.nativeapp.data.PushStateHolder
import app.openbubbles.nativeapp.facetime.FaceTimeCallBridge
import app.openbubbles.nativeapp.facetime.FtCallEvent
import app.openbubbles.nativeapp.facetime.FtIncomingCall
import kotlinx.coroutines.Dispatchers
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

    /** Session currently represented by the FaceTime activity/WebView. */
    @Volatile
    private var activeCallGuid: String? = null

    fun activateCall(guid: String) {
        activeCallGuid = guid
    }

    fun clearActiveCall(guid: String) {
        if (activeCallGuid == guid) activeCallGuid = null
    }

    /** Bridge hook: a ring expired or failed to present, so allow a re-ring. */
    fun clearStaleRing(guid: String) {
        if (ringingCallGuid == guid) ringingCallGuid = null
    }

    suspend fun onPushMessage(context: Context, msg: UPushMessage): Boolean {
        val ft = (msg as? UPushMessage.FaceTime)?.message ?: return false
        when (ft) {
            is UFtMessage.Ring -> onRing(context, ft.guid)
            is UFtMessage.AddMembers -> if (ft.ring) onRing(context, ft.guid)
            is UFtMessage.JoinEvent -> {
                if (ft.ring) onRing(context, ft.guid)
                FaceTimeCallBridge.onCallEvent(context, ft.guid, FtCallEvent.CONNECTED)
                app.openbubbles.nativeapp.facetime.FaceTimeActivity.activeFaceTimeActivity
                    ?.takeIf { it.callUuid == ft.guid }
                    ?.markConnected()
            }
            is UFtMessage.Decline -> {
                FaceTimeCallBridge.onCallEvent(context, ft.guid, FtCallEvent.REMOTE_DECLINED)
                cancelRinging(context, ft.guid)
            }
            is UFtMessage.RespondedElsewhere -> {
                FaceTimeCallBridge.onCallEvent(context, ft.guid, FtCallEvent.ANSWERED_ELSEWHERE)
                cancelRinging(context, ft.guid)
            }
            is UFtMessage.LeaveEvent -> {
                FaceTimeCallBridge.onCallEvent(context, ft.guid, FtCallEvent.REMOTE_HUNG_UP)
                // A participant left; if it was the active call, end the UI.
                app.openbubbles.nativeapp.facetime.FaceTimeActivity.activeFaceTimeActivity
                    ?.takeIf { it.callUuid == ft.guid }
                    ?.closeCallUi()
                if (ringingCallGuid == ft.guid) cancelRinging(context, ft.guid)
            }
            is UFtMessage.LetMeInRequest -> {
                val state = PushStateHolder.state
                if (state != null) {
                    val incomingUsage = ft.usage == "incomingcall" || ft.usage == "nextincomingcall"
                    val approvedGroup = if (incomingUsage) {
                        ringingCallGuid ?: activeCallGuid
                    } else {
                        activeCallGuid
                    }
                    withContext(Dispatchers.IO) {
                        state.approveLetMeIn(
                            ft.sharedSecret,
                            ft.pseud,
                            ft.requestor,
                            ft.nickname,
                            ft.token,
                            ft.delegationUuid,
                            ft.usage,
                            approvedGroup,
                        )
                    }
                    if (incomingUsage && ringingCallGuid == approvedGroup) {
                        ringingCallGuid = null
                    }
                }
            }
            else -> Unit
        }
        return true // consumed: not a chat message
    }

    private suspend fun onRing(context: Context, guid: String) {
        if (ringingCallGuid == guid) return
        val state = PushStateHolder.state ?: return
        val ok = withContext(Dispatchers.IO) {
            runCatching {
                val sessions = state.ftSessions()
                val session = sessions.firstOrNull { it.groupId == guid }
                val remoteMembers = session?.members
                    ?.filter { it.handle !in session.myHandles }
                val callerName = remoteMembers
                    ?.joinToString(" & ") { member ->
                        member.nickname ?: MessageMapper.normalizeAddress(member.handle)
                    }
                    ?.takeIf(String::isNotBlank)
                    ?: "FaceTime"
                val displayName = session?.myHandles?.firstOrNull()
                    ?.let(MessageMapper::normalizeAddress)
                    ?: PushStateHolder.myHandles.firstOrNull()
                        ?.let(MessageMapper::normalizeAddress)
                    ?: "You"
                val link = state.getFtLink("nextincomingcall")
                state.rotateIncomingLinks()
                ringingCallGuid = guid
                FaceTimeCallBridge.onIncomingRing(
                    context,
                    FtIncomingCall(
                        notificationId = guid.hashCode(),
                        callUuid = guid,
                        title = callerName,
                        link = link,
                        name = displayName,
                        poster = null,
                        callerName = callerName,
                        callerAvatar = null,
                        callerHandle = remoteMembers?.firstOrNull()?.handle,
                    ),
                )
            }.onFailure { Log.w(TAG, "ring handling failed", it) }.isSuccess
        }
        if (!ok) ringingCallGuid = null
    }

    private fun cancelRinging(context: Context, guid: String) {
        if (ringingCallGuid == guid) ringingCallGuid = null
        runCatching {
            context.getSystemService(android.app.NotificationManager::class.java)
                ?.cancel(
                    app.openbubbles.nativeapp.facetime.FtConstants.NEW_FACE_TIME_NOTIFICATION_TAG,
                    guid.hashCode(),
                )
        }
        app.openbubbles.nativeapp.facetime.FaceTimeActivity.activeFaceTimeActivity
            ?.takeIf { it.callUuid == guid }
            ?.closeCallUi()
    }
}

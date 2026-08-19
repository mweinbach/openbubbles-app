package app.openbubbles.nativeapp.facetime

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.telecom.DisconnectCause
import android.telecom.TelecomManager
import android.telecom.VideoProfile
import android.util.Log
import app.openbubbles.nativeapp.service.FaceTimeDispatch

/**
 * Single owner of the "telecom or notification-only" decision and of live
 * call state. Incoming rings and outgoing placements try the self-managed
 * telecom route first; every failure falls back to exactly the pre-telecom
 * behavior so FaceTime never regresses. All state is main-thread confined:
 * entry points hop to the main looper, and telecom invokes the attach
 * callbacks there already.
 */
internal object FaceTimeCallBridge {
    private const val TAG = "FaceTimeCallBridge"
    internal const val EXTRA_CALL_GUID = "app.openbubbles.facetime.CALL_GUID"

    private class TrackedCall(
        var phase: FtCallPhase,
        val incoming: FtIncomingCall? = null,
        val outgoingAddress: Uri? = null,
        val outgoingDisplayName: String? = null,
    ) {
        var connection: FaceTimeConnection? = null
        var ringTimeout: Runnable? = null
    }

    private val mainHandler = Handler(Looper.getMainLooper())
    private val calls = mutableMapOf<String, TrackedCall>()

    private fun onMain(block: () -> Unit) {
        if (Looper.myLooper() == Looper.getMainLooper()) block() else mainHandler.post(block)
    }

    /**
     * Entry point for a ring push. Tries telecom; any synchronous failure
     * rings through the CallStyle notification exactly as before telecom.
     */
    fun onIncomingRing(context: Context, call: FtIncomingCall) {
        val appContext = context.applicationContext
        onMain {
            val guid = call.callUuid
            if (guid == null) {
                postRingNotification(appContext, call)
                return@onMain
            }
            if (calls.containsKey(guid)) return@onMain
            val tracked = TrackedCall(FtCallPhase.INCOMING_RINGING, incoming = call)
            calls[guid] = tracked
            startRingTimer(appContext, guid, tracked)
            val telecom = FaceTimePhoneAccount.register(appContext)
            if (telecom == null) {
                handleIncomingFailure(appContext, guid, IncomingTelecomFailure.UNAVAILABLE)
                return@onMain
            }
            val handle = FaceTimePhoneAccount.handle(appContext)
            val permitted = runCatching { telecom.isIncomingCallPermitted(handle) }.getOrDefault(false)
            if (!permitted) {
                handleIncomingFailure(appContext, guid, IncomingTelecomFailure.NOT_PERMITTED)
                return@onMain
            }
            runCatching {
                telecom.addNewIncomingCall(
                    handle,
                    Bundle().apply {
                        putInt(TelecomManager.EXTRA_INCOMING_VIDEO_STATE, VideoProfile.STATE_BIDIRECTIONAL)
                        putBundle(
                            TelecomManager.EXTRA_INCOMING_CALL_EXTRAS,
                            Bundle().apply { putString(EXTRA_CALL_GUID, guid) },
                        )
                    },
                )
            }.onFailure {
                Log.w(TAG, "telecom rejected incoming call", it)
                handleIncomingFailure(appContext, guid, IncomingTelecomFailure.ADD_CALL_FAILED)
            }
        }
    }

    /**
     * Outgoing call already created with rustpush; announce it to telecom.
     * On any failure the call proceeds exactly as before: activity-only.
     */
    fun onOutgoingCallPlaced(context: Context, callUuid: String, peerHandle: String?, displayName: String) {
        val appContext = context.applicationContext
        onMain {
            if (calls.containsKey(callUuid)) return@onMain
            val telecom = FaceTimePhoneAccount.register(appContext) ?: return@onMain
            val handle = FaceTimePhoneAccount.handle(appContext)
            val permitted = runCatching { telecom.isOutgoingCallPermitted(handle) }.getOrDefault(false)
            if (!permitted) return@onMain
            val (scheme, ssp) = faceTimeAddressParts(peerHandle, callUuid)
            val address = Uri.fromParts(scheme, ssp, null)
            val tracked = TrackedCall(
                FtCallPhase.OUTGOING_DIALING,
                outgoingAddress = address,
                outgoingDisplayName = displayName,
            )
            calls[callUuid] = tracked
            runCatching {
                telecom.placeCall(
                    address,
                    Bundle().apply {
                        putParcelable(TelecomManager.EXTRA_PHONE_ACCOUNT_HANDLE, handle)
                        putInt(TelecomManager.EXTRA_START_CALL_WITH_VIDEO_STATE, VideoProfile.STATE_BIDIRECTIONAL)
                        putBundle(
                            TelecomManager.EXTRA_OUTGOING_CALL_EXTRAS,
                            Bundle().apply { putString(EXTRA_CALL_GUID, callUuid) },
                        )
                    },
                )
            }.onFailure {
                Log.w(TAG, "telecom rejected outgoing call", it)
                calls.remove(callUuid)
            }
        }
    }

    /** Applies one protocol/UI event to the tracked call, if any. */
    fun onCallEvent(context: Context, guid: String, event: FtCallEvent) {
        val appContext = context.applicationContext
        onMain {
            val tracked = calls[guid] ?: return@onMain
            val verdict = faceTimeTelecomVerdict(tracked.phase, event)
            if (verdict.postMissedNotification) {
                tracked.incoming?.let { postMissedNotification(appContext, it) }
            }
            when (val action = verdict.action) {
                TelecomAction.None -> Unit
                TelecomAction.SetActive ->
                    runCatching { tracked.connection?.setActive() }
                        .onFailure { Log.w(TAG, "setActive failed", it) }
                is TelecomAction.Disconnect -> disconnectConnection(tracked, action.causeCode)
            }
            val nextPhase = verdict.nextPhase
            if (nextPhase == null) {
                cancelRingTimer(tracked)
                calls.remove(guid)
            } else {
                tracked.phase = nextPhase
                if (nextPhase != FtCallPhase.INCOMING_RINGING) cancelRingTimer(tracked)
            }
        }
    }

    /** Telecom accepted the incoming call; synchronous, main thread. */
    fun attachIncomingConnection(context: Context, guid: String): FaceTimeConnection? {
        val tracked = calls[guid] ?: return null
        val call = tracked.incoming ?: return null
        val connection = FaceTimeConnection(context, guid)
        val (scheme, ssp) = faceTimeAddressParts(call.callerHandle, guid)
        connection.setAddress(Uri.fromParts(scheme, ssp, null), TelecomManager.PRESENTATION_ALLOWED)
        connection.setCallerDisplayName(call.callerName, TelecomManager.PRESENTATION_ALLOWED)
        connection.setVideoState(VideoProfile.STATE_BIDIRECTIONAL)
        connection.setRinging()
        tracked.connection = connection
        return connection
    }

    /** Telecom accepted the outgoing call; synchronous, main thread. */
    fun attachOutgoingConnection(context: Context, guid: String): FaceTimeConnection? {
        val tracked = calls[guid] ?: return null
        val address = tracked.outgoingAddress ?: return null
        val connection = FaceTimeConnection(context, guid)
        connection.setAddress(address, TelecomManager.PRESENTATION_ALLOWED)
        tracked.outgoingDisplayName?.let {
            connection.setCallerDisplayName(it, TelecomManager.PRESENTATION_ALLOWED)
        }
        connection.setVideoState(VideoProfile.STATE_BIDIRECTIONAL)
        connection.setDialing()
        tracked.connection = connection
        return connection
    }

    /** Telecom accepted then rejected the call (e.g. an ongoing carrier call). */
    fun onIncomingConnectionFailed(context: Context, guid: String) {
        val appContext = context.applicationContext
        onMain { handleIncomingFailure(appContext, guid, IncomingTelecomFailure.CONNECTION_FAILED) }
    }

    fun onOutgoingConnectionFailed(guid: String) {
        onMain { calls.remove(guid)?.let(::cancelRingTimer) }
    }

    /** Telecom wants the ring UI: the existing CallStyle notification. */
    fun onShowIncomingCallUi(context: Context, guid: String) {
        val appContext = context.applicationContext
        onMain { calls[guid]?.incoming?.let { postRingNotification(appContext, it) } }
    }

    /** Answer from a telecom surface (watch, car, bluetooth): the existing answer path. */
    fun onTelecomAnswer(context: Context, guid: String) {
        val appContext = context.applicationContext
        onMain {
            val call = calls[guid]?.incoming ?: return@onMain
            runCatching {
                appContext.startActivity(
                    faceTimeActivityIntent(appContext, call, answer = true)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                )
            }.onFailure { Log.w(TAG, "telecom answer could not open the call UI", it) }
            onCallEvent(appContext, guid, FtCallEvent.LOCAL_ANSWER)
        }
    }

    /** Reject from a telecom surface: the existing decline path. */
    fun onTelecomReject(context: Context, guid: String) {
        val appContext = context.applicationContext
        onMain {
            sendCallAction(appContext, guid, FaceTimeActionReceiver.ACTION_DECLINE)
            onCallEvent(appContext, guid, FtCallEvent.LOCAL_DECLINE)
        }
    }

    /** Hang-up from a telecom surface: the existing end path. */
    fun onTelecomDisconnect(context: Context, guid: String) {
        val appContext = context.applicationContext
        onMain {
            sendCallAction(appContext, guid, FaceTimeActionReceiver.ACTION_END)
            onCallEvent(appContext, guid, FtCallEvent.LOCAL_HANG_UP)
        }
    }

    private fun sendCallAction(context: Context, guid: String, action: String) {
        val notificationId = calls[guid]?.incoming?.notificationId ?: guid.hashCode()
        context.sendBroadcast(
            Intent(context, FaceTimeActionReceiver::class.java)
                .setAction(action)
                .putExtra(FaceTimeActionReceiver.EXTRA_CALL_UUID, guid)
                .putExtra(FaceTimeActionReceiver.EXTRA_NOTIFICATION_ID, notificationId),
        )
    }

    private fun handleIncomingFailure(context: Context, guid: String, failure: IncomingTelecomFailure) {
        val tracked = calls[guid]
        val call = tracked?.incoming
        when (incomingFallback(failure)) {
            IncomingFallback.RING_NOTIFICATION -> {
                if (call != null) postRingNotification(context, call)
            }
            IncomingFallback.MISSED_NOTIFICATION -> {
                if (call != null) postMissedNotification(context, call)
                tracked?.let(::cancelRingTimer)
                calls.remove(guid)
            }
        }
    }

    private fun postRingNotification(context: Context, call: FtIncomingCall) {
        runCatching { CreateIncomingFaceTimeNotification.create(context, call) }
            .onFailure {
                Log.w(TAG, "ring notification failed", it)
                val guid = call.callUuid ?: return@onFailure
                calls.remove(guid)?.let(::cancelRingTimer)
                FaceTimeDispatch.clearStaleRing(guid)
            }
    }

    private fun postMissedNotification(context: Context, call: FtIncomingCall) {
        runCatching {
            CreateMissedFaceTimeNotification.create(
                context,
                call.notificationId,
                call.callUuid,
                call.title,
                call.poster,
                call.callerName,
                call.callerAvatar,
            )
        }.onFailure { Log.w(TAG, "missed-call notification failed", it) }
    }

    private fun startRingTimer(context: Context, guid: String, tracked: TrackedCall) {
        val expiry = Runnable {
            FaceTimeDispatch.clearStaleRing(guid)
            onCallEvent(context, guid, FtCallEvent.RING_TIMEOUT)
        }
        tracked.ringTimeout = expiry
        mainHandler.postDelayed(expiry, FACETIME_RING_TIMEOUT_MS)
    }

    private fun cancelRingTimer(tracked: TrackedCall) {
        tracked.ringTimeout?.let(mainHandler::removeCallbacks)
        tracked.ringTimeout = null
    }

    private fun disconnectConnection(tracked: TrackedCall, causeCode: Int) {
        val connection = tracked.connection ?: return
        tracked.connection = null
        runCatching {
            connection.setDisconnected(DisconnectCause(causeCode))
            connection.destroy()
        }.onFailure { Log.w(TAG, "disconnect failed", it) }
    }
}

package app.openbubbles.nativeapp.facetime

import android.content.Context
import android.telecom.Connection
import android.telecom.ConnectionRequest
import android.telecom.ConnectionService
import android.telecom.DisconnectCause
import android.telecom.PhoneAccountHandle
import android.telecom.TelecomManager

/**
 * Self-managed ConnectionService. Telecom binds here for calls announced via
 * `addNewIncomingCall` / `placeCall`; every connection bridges back to the
 * existing notification + WebView flow through [FaceTimeCallBridge].
 */
class FaceTimeConnectionService : ConnectionService() {

    override fun onCreateIncomingConnection(
        connectionManagerPhoneAccount: PhoneAccountHandle?,
        request: ConnectionRequest?,
    ): Connection {
        val connection = request?.callGuid()?.let {
            FaceTimeCallBridge.attachIncomingConnection(applicationContext, it)
        }
        return connection ?: Connection.createFailedConnection(DisconnectCause(DisconnectCause.CANCELED))
    }

    override fun onCreateIncomingConnectionFailed(
        connectionManagerPhoneAccount: PhoneAccountHandle?,
        request: ConnectionRequest?,
    ) {
        request?.callGuid()?.let {
            FaceTimeCallBridge.onIncomingConnectionFailed(applicationContext, it)
        }
    }

    override fun onCreateOutgoingConnection(
        connectionManagerPhoneAccount: PhoneAccountHandle?,
        request: ConnectionRequest?,
    ): Connection {
        val connection = request?.callGuid()?.let {
            FaceTimeCallBridge.attachOutgoingConnection(applicationContext, it)
        }
        return connection ?: Connection.createFailedConnection(DisconnectCause(DisconnectCause.CANCELED))
    }

    override fun onCreateOutgoingConnectionFailed(
        connectionManagerPhoneAccount: PhoneAccountHandle?,
        request: ConnectionRequest?,
    ) {
        request?.callGuid()?.let { FaceTimeCallBridge.onOutgoingConnectionFailed(it) }
    }

    private fun ConnectionRequest.callGuid(): String? {
        val bundle = extras ?: return null
        return bundle.getString(FaceTimeCallBridge.EXTRA_CALL_GUID)
            ?: bundle.getBundle(TelecomManager.EXTRA_INCOMING_CALL_EXTRAS)
                ?.getString(FaceTimeCallBridge.EXTRA_CALL_GUID)
            ?: bundle.getBundle(TelecomManager.EXTRA_OUTGOING_CALL_EXTRAS)
                ?.getString(FaceTimeCallBridge.EXTRA_CALL_GUID)
    }
}

/**
 * One self-managed telecom call. Telecom-initiated actions route into the
 * pre-telecom flow (notification intents, [FaceTimeActionReceiver]); protocol
 * events come back through [FaceTimeCallBridge.onCallEvent]. Audio stays with
 * the FaceTime WebView: telecom call-audio routing is intentionally not wired
 * up (see the volume mirroring in FaceTimeActivity).
 */
internal class FaceTimeConnection(
    private val context: Context,
    private val guid: String,
) : Connection() {

    init {
        connectionProperties = PROPERTY_SELF_MANAGED
    }

    override fun onShowIncomingCallUi() {
        FaceTimeCallBridge.onShowIncomingCallUi(context, guid)
    }

    override fun onAnswer(videoState: Int) = onAnswer()

    override fun onAnswer() {
        FaceTimeCallBridge.onTelecomAnswer(context, guid)
    }

    override fun onReject() {
        FaceTimeCallBridge.onTelecomReject(context, guid)
    }

    override fun onDisconnect() {
        FaceTimeCallBridge.onTelecomDisconnect(context, guid)
    }
}

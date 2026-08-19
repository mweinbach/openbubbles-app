package app.openbubbles.nativeapp.facetime

import android.telecom.DisconnectCause

/** One ring window, shared by the notification timeout and the bridge timer. */
internal const val FACETIME_RING_TIMEOUT_MS = 30_000L

/** Scheme for FaceTime handles that are not phone numbers (never `sip:`). */
internal const val FACETIME_URI_SCHEME = "facetime"

/** Phase of a tracked call between FaceTime protocol events. */
internal enum class FtCallPhase {
    /** Ringing locally; the user has not answered yet. */
    INCOMING_RINGING,

    /** Answered locally; waiting for our own JoinEvent to confirm. */
    ANSWERED,

    /** Outgoing call placed; waiting for a JoinEvent. */
    OUTGOING_DIALING,

    /** Connected. */
    ACTIVE,
}

/** Everything that can move a tracked call, from the protocol or from local UI. */
internal enum class FtCallEvent {
    /** JoinEvent for this call (`markConnected` path). */
    CONNECTED,

    /** UFtMessage.Decline. */
    REMOTE_DECLINED,

    /** UFtMessage.RespondedElsewhere. */
    ANSWERED_ELSEWHERE,

    /** UFtMessage.LeaveEvent. */
    REMOTE_HUNG_UP,

    /** The 30s incoming ring window elapsed with no answer. */
    RING_TIMEOUT,

    /** The user answered on this device (notification action or splash button). */
    LOCAL_ANSWER,

    /** The user declined on this device (`FaceTimeActionReceiver.ACTION_DECLINE`). */
    LOCAL_DECLINE,

    /** The user ended the call on this device (`FaceTimeActionReceiver.ACTION_END`). */
    LOCAL_HANG_UP,
}

/** What the telecom side of the bridge should do for a verdict. */
internal sealed interface TelecomAction {
    data object None : TelecomAction

    data object SetActive : TelecomAction

    /** [causeCode] is an `android.telecom.DisconnectCause` constant. */
    data class Disconnect(val causeCode: Int) : TelecomAction
}

/**
 * Result of applying one [FtCallEvent] to a call in one [FtCallPhase].
 * A null [nextPhase] means the call is over and tracking stops.
 */
internal data class TelecomVerdict(
    val nextPhase: FtCallPhase?,
    val action: TelecomAction,
    val postMissedNotification: Boolean = false,
)

/**
 * Pure FT-event to telecom-action table. The bridge applies the verdict on
 * whichever route (telecom Connection or notification-only fallback) the call
 * is riding; [TelecomVerdict.postMissedNotification] applies on both routes.
 */
internal fun faceTimeTelecomVerdict(phase: FtCallPhase, event: FtCallEvent): TelecomVerdict = when (event) {
    FtCallEvent.CONNECTED -> when (phase) {
        FtCallPhase.ANSWERED,
        FtCallPhase.OUTGOING_DIALING,
        -> TelecomVerdict(FtCallPhase.ACTIVE, TelecomAction.SetActive)
        // A JoinEvent while still ringing is another member joining, not us.
        FtCallPhase.INCOMING_RINGING,
        FtCallPhase.ACTIVE,
        -> TelecomVerdict(phase, TelecomAction.None)
    }

    FtCallEvent.LOCAL_ANSWER -> when (phase) {
        FtCallPhase.INCOMING_RINGING -> TelecomVerdict(FtCallPhase.ANSWERED, TelecomAction.None)
        else -> TelecomVerdict(phase, TelecomAction.None)
    }

    FtCallEvent.REMOTE_DECLINED ->
        TelecomVerdict(null, TelecomAction.Disconnect(DisconnectCause.REJECTED))

    FtCallEvent.ANSWERED_ELSEWHERE ->
        TelecomVerdict(null, TelecomAction.Disconnect(DisconnectCause.ANSWERED_ELSEWHERE))

    FtCallEvent.REMOTE_HUNG_UP -> when (phase) {
        // The caller gave up before we answered: a missed call.
        FtCallPhase.INCOMING_RINGING ->
            TelecomVerdict(null, TelecomAction.Disconnect(DisconnectCause.MISSED), postMissedNotification = true)
        else -> TelecomVerdict(null, TelecomAction.Disconnect(DisconnectCause.REMOTE))
    }

    FtCallEvent.RING_TIMEOUT -> when (phase) {
        FtCallPhase.INCOMING_RINGING ->
            TelecomVerdict(null, TelecomAction.Disconnect(DisconnectCause.MISSED), postMissedNotification = true)
        // Stale timer that raced an answer or hangup.
        else -> TelecomVerdict(phase, TelecomAction.None)
    }

    FtCallEvent.LOCAL_DECLINE -> when (phase) {
        FtCallPhase.INCOMING_RINGING,
        FtCallPhase.ANSWERED,
        -> TelecomVerdict(null, TelecomAction.Disconnect(DisconnectCause.REJECTED))
        else -> TelecomVerdict(null, TelecomAction.Disconnect(DisconnectCause.LOCAL))
    }

    FtCallEvent.LOCAL_HANG_UP ->
        TelecomVerdict(null, TelecomAction.Disconnect(DisconnectCause.LOCAL))
}

/** Why the telecom route was not taken for an incoming ring. */
internal enum class IncomingTelecomFailure {
    /** No telecom service, no MANAGE_OWN_CALLS, or account registration failed. */
    UNAVAILABLE,

    /** `TelecomManager.isIncomingCallPermitted` said no. */
    NOT_PERMITTED,

    /** `TelecomManager.addNewIncomingCall` threw. */
    ADD_CALL_FAILED,

    /** `onCreateIncomingConnectionFailed` fired (e.g. an ongoing carrier call). */
    CONNECTION_FAILED,
}

internal enum class IncomingFallback {
    /** Ring exactly as before telecom existed: the CallStyle notification. */
    RING_NOTIFICATION,

    /** Telecom accepted then rejected the call; ringing now would fight the carrier call. */
    MISSED_NOTIFICATION,
}

/** Fallback decision: every failure keeps ringing except a post-accept rejection. */
internal fun incomingFallback(failure: IncomingTelecomFailure): IncomingFallback = when (failure) {
    IncomingTelecomFailure.UNAVAILABLE,
    IncomingTelecomFailure.NOT_PERMITTED,
    IncomingTelecomFailure.ADD_CALL_FAILED,
    -> IncomingFallback.RING_NOTIFICATION
    IncomingTelecomFailure.CONNECTION_FAILED -> IncomingFallback.MISSED_NOTIFICATION
}

/**
 * Scheme + scheme-specific part for a Connection address. Accepts raw rust
 * handles (`tel:`/`mailto:` prefixed) and normalized addresses; group or
 * unknown callers fall back to the call id under the custom scheme.
 */
internal fun faceTimeAddressParts(handle: String?, fallbackId: String): Pair<String, String> {
    val normalized = handle?.removePrefix("tel:")?.removePrefix("mailto:")?.takeIf(String::isNotBlank)
        ?: return FACETIME_URI_SCHEME to fallbackId
    return if (normalized.contains("@")) FACETIME_URI_SCHEME to normalized else "tel" to normalized
}

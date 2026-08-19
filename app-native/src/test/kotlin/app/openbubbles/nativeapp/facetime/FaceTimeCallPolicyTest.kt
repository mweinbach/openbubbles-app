package app.openbubbles.nativeapp.facetime

import android.telecom.DisconnectCause
import kotlin.test.Test
import kotlin.test.assertEquals

class FaceTimeCallPolicyTest {

    private fun disconnect(code: Int, missed: Boolean = false) =
        TelecomVerdict(null, TelecomAction.Disconnect(code), postMissedNotification = missed)

    @Test
    fun `answer then join drives ringing to active`() {
        val answered = faceTimeTelecomVerdict(FtCallPhase.INCOMING_RINGING, FtCallEvent.LOCAL_ANSWER)
        assertEquals(TelecomVerdict(FtCallPhase.ANSWERED, TelecomAction.None), answered)

        val joined = faceTimeTelecomVerdict(FtCallPhase.ANSWERED, FtCallEvent.CONNECTED)
        assertEquals(TelecomVerdict(FtCallPhase.ACTIVE, TelecomAction.SetActive), joined)
    }

    @Test
    fun `outgoing join activates and stays idempotent`() {
        assertEquals(
            TelecomVerdict(FtCallPhase.ACTIVE, TelecomAction.SetActive),
            faceTimeTelecomVerdict(FtCallPhase.OUTGOING_DIALING, FtCallEvent.CONNECTED),
        )
        assertEquals(
            TelecomVerdict(FtCallPhase.ACTIVE, TelecomAction.None),
            faceTimeTelecomVerdict(FtCallPhase.ACTIVE, FtCallEvent.CONNECTED),
        )
    }

    @Test
    fun `join while still ringing is another member not an answer`() {
        assertEquals(
            TelecomVerdict(FtCallPhase.INCOMING_RINGING, TelecomAction.None),
            faceTimeTelecomVerdict(FtCallPhase.INCOMING_RINGING, FtCallEvent.CONNECTED),
        )
    }

    @Test
    fun `remote decline rejects in every phase`() {
        for (phase in FtCallPhase.entries) {
            assertEquals(
                disconnect(DisconnectCause.REJECTED),
                faceTimeTelecomVerdict(phase, FtCallEvent.REMOTE_DECLINED),
                "phase $phase",
            )
        }
    }

    @Test
    fun `responded elsewhere maps to answered-elsewhere cause`() {
        for (phase in FtCallPhase.entries) {
            assertEquals(
                disconnect(DisconnectCause.ANSWERED_ELSEWHERE),
                faceTimeTelecomVerdict(phase, FtCallEvent.ANSWERED_ELSEWHERE),
                "phase $phase",
            )
        }
    }

    @Test
    fun `remote hangup before answer is a missed call`() {
        assertEquals(
            disconnect(DisconnectCause.MISSED, missed = true),
            faceTimeTelecomVerdict(FtCallPhase.INCOMING_RINGING, FtCallEvent.REMOTE_HUNG_UP),
        )
    }

    @Test
    fun `remote hangup after connecting is a remote disconnect`() {
        for (phase in listOf(FtCallPhase.ANSWERED, FtCallPhase.OUTGOING_DIALING, FtCallPhase.ACTIVE)) {
            assertEquals(
                disconnect(DisconnectCause.REMOTE),
                faceTimeTelecomVerdict(phase, FtCallEvent.REMOTE_HUNG_UP),
                "phase $phase",
            )
        }
    }

    @Test
    fun `ring timeout misses only while still ringing`() {
        assertEquals(
            disconnect(DisconnectCause.MISSED, missed = true),
            faceTimeTelecomVerdict(FtCallPhase.INCOMING_RINGING, FtCallEvent.RING_TIMEOUT),
        )
        for (phase in listOf(FtCallPhase.ANSWERED, FtCallPhase.OUTGOING_DIALING, FtCallPhase.ACTIVE)) {
            assertEquals(
                TelecomVerdict(phase, TelecomAction.None),
                faceTimeTelecomVerdict(phase, FtCallEvent.RING_TIMEOUT),
                "phase $phase",
            )
        }
    }

    @Test
    fun `local decline rejects before connecting and hangs up after`() {
        assertEquals(
            disconnect(DisconnectCause.REJECTED),
            faceTimeTelecomVerdict(FtCallPhase.INCOMING_RINGING, FtCallEvent.LOCAL_DECLINE),
        )
        assertEquals(
            disconnect(DisconnectCause.REJECTED),
            faceTimeTelecomVerdict(FtCallPhase.ANSWERED, FtCallEvent.LOCAL_DECLINE),
        )
        assertEquals(
            disconnect(DisconnectCause.LOCAL),
            faceTimeTelecomVerdict(FtCallPhase.ACTIVE, FtCallEvent.LOCAL_DECLINE),
        )
    }

    @Test
    fun `local hangup is a local disconnect in every phase`() {
        for (phase in FtCallPhase.entries) {
            assertEquals(
                disconnect(DisconnectCause.LOCAL),
                faceTimeTelecomVerdict(phase, FtCallEvent.LOCAL_HANG_UP),
                "phase $phase",
            )
        }
    }

    @Test
    fun `every telecom failure keeps ringing except a post-accept rejection`() {
        assertEquals(IncomingFallback.RING_NOTIFICATION, incomingFallback(IncomingTelecomFailure.UNAVAILABLE))
        assertEquals(IncomingFallback.RING_NOTIFICATION, incomingFallback(IncomingTelecomFailure.NOT_PERMITTED))
        assertEquals(IncomingFallback.RING_NOTIFICATION, incomingFallback(IncomingTelecomFailure.ADD_CALL_FAILED))
        assertEquals(IncomingFallback.MISSED_NOTIFICATION, incomingFallback(IncomingTelecomFailure.CONNECTION_FAILED))
    }

    @Test
    fun `connection addresses use tel for numbers and the custom scheme otherwise`() {
        assertEquals("tel" to "+15551234567", faceTimeAddressParts("tel:+15551234567", "GUID"))
        assertEquals("tel" to "+15551234567", faceTimeAddressParts("+15551234567", "GUID"))
        assertEquals("facetime" to "user@example.com", faceTimeAddressParts("mailto:user@example.com", "GUID"))
        assertEquals("facetime" to "user@example.com", faceTimeAddressParts("user@example.com", "GUID"))
        assertEquals("facetime" to "GUID", faceTimeAddressParts(null, "GUID"))
        assertEquals("facetime" to "GUID", faceTimeAddressParts("", "GUID"))
    }
}

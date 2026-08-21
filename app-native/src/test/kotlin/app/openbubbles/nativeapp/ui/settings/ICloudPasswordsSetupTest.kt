package app.openbubbles.nativeapp.ui.settings

import app.openbubbles.nativeapp.data.ICloudKeychainEnrollment
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ICloudPasswordsSetupTest {
    @Test
    fun legacyEscrowFailureSaysNothingWasResetWithoutOfferingNearby() {
        val message = escrowRecoveryFailure("Unimplemented escrow format 1")

        assertTrue(message.contains("Nothing was reset"))
        assertFalse(message.contains("nearby", ignoreCase = true))
    }

    @Test
    fun otherEscrowFailuresRemainVisible() {
        assertEquals("Apple service unavailable", escrowRecoveryFailure("Apple service unavailable"))
        assertEquals("Unable to fetch trusted devices", escrowRecoveryFailure(null))
    }

    @Test
    fun emptyEscrowRecordCopyDoesNotSendUsersToNearbyApproval() {
        val message = ICloudKeychainEnrollment.noViableBottlesMessage()

        assertFalse(message.contains("nearby", ignoreCase = true))
        assertTrue(message.contains("Nothing was reset"))
    }

    @Test
    fun nearbyApprovalStaysOffUntilTheProximityHandshakeWorks() {
        assertFalse(ICloudKeychainEnrollment.NEARBY_APPROVAL_ENABLED)
    }
}

package app.openbubbles.nativeapp.ui.settings

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ICloudPasswordsSetupTest {
    @Test
    fun legacyEscrowFailurePointsToNearbyApprovalWithoutResetting() {
        val message = escrowRecoveryFailure("Unimplemented escrow format 1")

        assertTrue(message.contains("nearby-device approval"))
        assertTrue(message.contains("was not reset"))
    }

    @Test
    fun otherEscrowFailuresRemainVisible() {
        assertEquals("Apple service unavailable", escrowRecoveryFailure("Apple service unavailable"))
        assertEquals("Unable to fetch trusted devices", escrowRecoveryFailure(null))
    }
}

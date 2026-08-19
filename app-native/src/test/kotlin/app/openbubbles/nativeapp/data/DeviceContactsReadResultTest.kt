package app.openbubbles.nativeapp.data

import app.openbubbles.core.contacts.ContactSync
import app.openbubbles.core.contacts.DeviceContactSnapshot
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DeviceContactsReadResultTest {

    @Test
    fun `successful empty snapshot reconciles but failed read does not`() {
        val emptySnapshot = DeviceContactSnapshot(
            contacts = emptyList(),
            legacyNativeIds = emptyMap(),
        )
        var reconciled = 0

        val successApplied = DeviceContactsReadResult.Success(emptySnapshot)
            .applySuccessfulSnapshot {
                assertTrue(it.contacts.isEmpty())
                reconciled++
            }
        val failureApplied = DeviceContactsReadResult.Failure(IllegalStateException("provider failed"))
            .applySuccessfulSnapshot { reconciled++ }
        val deniedApplied = DeviceContactsReadResult.PermissionDenied
            .applySuccessfulSnapshot { reconciled++ }

        assertTrue(successApplied)
        assertFalse(failureApplied)
        assertFalse(deniedApplied)
        assertEquals(1, reconciled)
    }

    @Test
    fun `stable Android id is namespaced lookup key independent of provider row id`() {
        val firstReadId = stableAndroidContactId("alice.lookup")
        val laterReadId = stableAndroidContactId("alice.lookup")

        assertEquals(firstReadId, laterReadId)
        assertEquals("${ContactSync.DEVICE_CONTACT_PREFIX}alice.lookup", firstReadId)
    }
}

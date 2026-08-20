package app.openbubbles.nativeapp.data

import android.provider.ContactsContract
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

    @Test
    fun `provider snapshot must keep the same identities and revisions`() {
        val original = listOf(
            ContactProviderRevision("1", "alice.lookup", 10),
            ContactProviderRevision("2", "bob.lookup", 20),
        )

        assertTrue(providerSnapshotIsStable(original, original.reversed()))
        assertFalse(
            providerSnapshotIsStable(
                original,
                original.map { if (it.rowId == "2") it.copy(updatedAtMillis = 21) else it },
            ),
        )
        assertFalse(providerSnapshotIsStable(original, original.dropLast(1)))
    }

    @Test
    fun `provider address selection prefers normalized phones and keeps email text`() {
        assertEquals(
            "+15551234567",
            preferredContactAddress(
                ContactsContract.CommonDataKinds.Phone.CONTENT_ITEM_TYPE,
                "(555) 123-4567",
                "+15551234567",
            ),
        )
        assertEquals(
            "friend@example.com",
            preferredContactAddress(
                ContactsContract.CommonDataKinds.Email.CONTENT_ITEM_TYPE,
                "friend@example.com",
                null,
            ),
        )
        assertEquals(
            "(555) 123-4567",
            preferredContactAddress(
                ContactsContract.CommonDataKinds.Phone.CONTENT_ITEM_TYPE,
                "(555) 123-4567",
                null,
            ),
        )
        assertEquals(null, preferredContactAddress("unsupported", "value", null))
    }
}

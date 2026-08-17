package app.openbubbles.nativeapp.sms

import android.provider.Telephony
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TelephonySmsStoreTest {

    @Test
    fun `default app consumes deliver and skips the received echo`() {
        assertTrue(
            shouldIngestSmsBroadcast(Telephony.Sms.Intents.SMS_DELIVER_ACTION, isDefaultSmsApp = true),
        )
        assertFalse(
            shouldIngestSmsBroadcast(Telephony.Sms.Intents.SMS_RECEIVED_ACTION, isDefaultSmsApp = true),
        )
        assertTrue(
            shouldIngestSmsBroadcast(Telephony.Sms.Intents.SMS_RECEIVED_ACTION, isDefaultSmsApp = false),
        )
        assertFalse(shouldIngestSmsBroadcast("android.intent.action.BOOT_COMPLETED", false))
    }

    @Test
    fun `tel prefixes are stripped before writing provider rows`() {
        assertEquals("+15550001111", displaySmsAddress("tel:+15550001111"))
        assertEquals("friend@icloud.com", displaySmsAddress("mailto:friend@icloud.com"))
    }
}

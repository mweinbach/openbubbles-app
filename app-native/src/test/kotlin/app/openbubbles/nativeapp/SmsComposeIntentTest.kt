package app.openbubbles.nativeapp

import android.content.Intent
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class SmsComposeIntentTest {

    @Test
    fun sendMultipleParsesTextAndStreamsForChatPicker() {
        val request = parseIncomingShareRequest(
            action = Intent.ACTION_SEND_MULTIPLE,
            mimeType = "image/*",
            extraText = "Trip photos",
            streams = listOf("content://photos/1", "content://photos/2"),
        )

        assertEquals("Trip photos", request?.text)
        assertEquals(listOf("content://photos/1", "content://photos/2"), request?.streams)
        assertEquals("image/*", request?.mimeType)
    }

    @Test
    fun sendRejectsEmptyOrUnsafePayload() {
        assertNull(parseIncomingShareRequest(Intent.ACTION_SEND, "*/*", null, emptyList()))
        assertNull(parseIncomingShareRequest(Intent.ACTION_SEND, "*/*", null, listOf("https://example.com/file")))
    }

    @Test
    fun `SENDTO parser accepts SMS recipients and body`() {
        val request = parseSmsComposeRequest(
            action = Intent.ACTION_SENDTO,
            dataString = "smsto:+15551234567,+15557654321?body=hello%20there",
            extraText = null,
        )

        assertEquals(listOf("+15551234567", "+15557654321"), request?.recipients)
        assertEquals("hello there", request?.body)
        assertEquals(true, request?.useSms)
    }

    @Test
    fun `SENDTO parser preserves phone plus and decodes body plus as space`() {
        val request = parseSmsComposeRequest(
            action = Intent.ACTION_SENDTO,
            dataString = "sms:+15551234567?body=hello+there",
            extraText = null,
        )

        assertEquals(listOf("+15551234567"), request?.recipients)
        assertEquals("hello there", request?.body)
    }

    @Test
    fun `VIEW parser accepts sms recipients`() {
        val request = parseSmsComposeRequest(
            action = Intent.ACTION_VIEW,
            dataString = "sms:+15551234567",
            extraText = null,
        )

        assertEquals(listOf("+15551234567"), request?.recipients)
        assertEquals(true, request?.useSms)
    }

    @Test
    fun `SEND parser uses shared text as an SMS draft`() {
        val request = parseSmsComposeRequest(
            action = Intent.ACTION_SEND,
            dataString = null,
            extraText = "shared text",
        )

        assertEquals(emptyList(), request?.recipients)
        assertEquals("shared text", request?.body)
        assertEquals(true, request?.useSms)
    }

    @Test
    fun `SENDTO parser rejects undeclared schemes`() {
        assertNull(parseSmsComposeRequest(Intent.ACTION_SENDTO, "https://example.com", "hello"))
        assertNull(parseSmsComposeRequest(Intent.ACTION_VIEW, "https://example.com", "hello"))
    }
}

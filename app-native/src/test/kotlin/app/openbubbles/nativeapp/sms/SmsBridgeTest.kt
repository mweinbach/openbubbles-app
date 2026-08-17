package app.openbubbles.nativeapp.sms

import kotlinx.coroutines.test.runTest
import org.junit.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SmsBridgeTest {
    @Test
    fun `non sms chat does not invoke transport`() = runTest {
        var invoked = false

        val routed = routeSmsTransport(isSmsChat = false, send = { invoked = true })

        assertFalse(routed)
        assertFalse(invoked)
    }

    @Test
    fun `failure before staging reaches the composer`() = runTest {
        assertFailsWith<IllegalStateException> {
            routeSmsTransport(isSmsChat = true, send = { error("stage failed") })
        }
    }

    @Test
    fun `failure after staging is represented by failed row`() = runTest {
        val routed = routeSmsTransport(
            isSmsChat = true,
            send = { throw SmsSendAlreadyStagedException(IllegalStateException("modem failed")) },
        )

        assertTrue(routed)
    }
}

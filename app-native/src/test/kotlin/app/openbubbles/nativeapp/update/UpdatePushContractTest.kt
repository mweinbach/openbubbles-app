package app.openbubbles.nativeapp.update

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class UpdatePushContractTest {
    private fun payload(vararg overrides: Pair<String, String>) = mutableMapOf(
        "type" to "update_available",
        "project" to "openbubbles",
        "channel" to "stable",
        "version" to "3.5.0",
        "build" to "20002276",
    ).apply { putAll(overrides) }

    @Test
    fun `valid Ledger wake-up is accepted`() {
        assertEquals(UpdatePushPayload("3.5.0", 20002276), UpdatePushContract.parse(payload()))
    }

    @Test
    fun `other projects channels and message types are ignored`() {
        assertNull(UpdatePushContract.parse(payload("project" to "other")))
        assertNull(UpdatePushContract.parse(payload("channel" to "beta")))
        assertNull(UpdatePushContract.parse(payload("type" to "chat_message")))
    }

    @Test
    fun `invalid display metadata is ignored`() {
        assertNull(UpdatePushContract.parse(payload("version" to "3.5.0 ready now")))
        assertNull(UpdatePushContract.parse(payload("build" to "0")))
        assertNull(UpdatePushContract.parse(payload("build" to "not-a-number")))
    }
}

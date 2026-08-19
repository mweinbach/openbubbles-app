package app.openbubbles.core

import app.openbubbles.core.model.isUnknownDirectSender
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class UnknownSenderTest {
    @Test
    fun `groups are never unknown senders`() {
        assertFalse(isUnknownDirectSender(true, "+15555550100", "tel:+15555550100"))
    }

    @Test
    fun `raw handle titles are unknown`() {
        assertTrue(isUnknownDirectSender(false, "+15555550100", "tel:+15555550100"))
        assertTrue(isUnknownDirectSender(false, "tel:+15555550100", "tel:+15555550100"))
    }

    @Test
    fun `contact names are known`() {
        assertFalse(isUnknownDirectSender(false, "Ada Lovelace", "tel:+15555550100"))
    }
}

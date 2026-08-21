package app.openbubbles.nativeapp.ui.chat

import kotlin.test.Test
import kotlin.test.assertEquals

class UploadProgressLayoutTest {

    @Test
    fun `progress matches a height-capped portrait photo rather than the bubble column`() {
        assertEquals(191, lastChildMatchWidthPx(intArrayOf(191), maxWidthPx = 320))
    }

    @Test
    fun `progress matches the widest of several attachments`() {
        assertEquals(260, lastChildMatchWidthPx(intArrayOf(200, 260), maxWidthPx = 320))
    }

    @Test
    fun `an oversized sibling still cannot exceed the incoming max`() {
        assertEquals(320, lastChildMatchWidthPx(intArrayOf(400), maxWidthPx = 320))
    }

    @Test
    fun `progress-only rows fill the incoming max`() {
        assertEquals(320, lastChildMatchWidthPx(intArrayOf(), maxWidthPx = 320))
    }
}

package app.openbubbles.nativeapp.ui.chat

import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.junit.Test

class OutgoingAttachmentsTest {
    @Test
    fun `video names survive generic provider mime types`() {
        assertTrue(isOutgoingVideo("application/octet-stream", "clip.mov"))
        assertTrue(isOutgoingVideo(null, "clip.MP4"))
        assertTrue(isOutgoingVideo("video/quicktime", "unnamed"))
        assertFalse(isOutgoingVideo("application/octet-stream", "notes.pdf"))
    }
}

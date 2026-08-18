package app.openbubbles.nativeapp.data

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AttachmentMetaTest {

    @Test
    fun `quicktime name is video even when mime is generic`() {
        val meta = AttachmentMeta(
            guid = "v1",
            mime = "application/octet-stream",
            name = "RenderedVideo.mov",
            sizeBytes = 154_000L,
            isImage = false,
            downloaded = true,
            uti = "public.data",
        )
        assertTrue(meta.isVideo)
        assertFalse(meta.isImage)
        assertFalse(meta.isPdf)
        assertEquals("video/quicktime", meta.playbackMime)
    }

    @Test
    fun `heic name is an image`() {
        val meta = AttachmentMeta(
            guid = "i1",
            mime = "application/octet-stream",
            name = "IMG_0091.HEIC",
            sizeBytes = 2_000L,
            isImage = true,
            downloaded = true,
            uti = "public.heic",
        )
        assertTrue(meta.isImage)
        assertFalse(meta.isVideo)
        assertEquals("image/heic", meta.playbackMime)
    }

    @Test
    fun `pdf name is previewable`() {
        val meta = AttachmentMeta(
            guid = "p1",
            mime = null,
            name = "itinerary.pdf",
            sizeBytes = 12_000L,
            isImage = false,
            downloaded = true,
        )
        assertTrue(meta.isPdf)
        assertFalse(meta.isVideo)
        assertEquals("application/pdf", meta.playbackMime)
    }
}

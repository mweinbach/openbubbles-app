package app.openbubbles.core.attachment

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AttachmentMediaTest {

    @Test
    fun `heic is an image by mime uti or extension`() {
        assertEquals(AttachmentMediaKind.IMAGE, AttachmentMedia.kind("image/heic", null, null))
        assertEquals(AttachmentMediaKind.IMAGE, AttachmentMedia.kind(null, "public.heic", null))
        assertEquals(
            AttachmentMediaKind.IMAGE,
            AttachmentMedia.kind("application/octet-stream", null, "IMG_1234.HEIC"),
        )
    }

    @Test
    fun `quicktime and hevc files are video even without a video mime`() {
        assertEquals(
            AttachmentMediaKind.VIDEO,
            AttachmentMedia.kind(null, "com.apple.quicktime-movie", "RenderedVideo.mov"),
        )
        assertEquals(
            AttachmentMediaKind.VIDEO,
            AttachmentMedia.kind("application/octet-stream", "public.data", "RenderedVideo.mov"),
        )
        assertEquals(AttachmentMediaKind.VIDEO, AttachmentMedia.kind("video/hevc", null, "clip.hevc"))
        assertEquals(AttachmentMediaKind.VIDEO, AttachmentMedia.kind(null, "public.mpeg-4", "clip.m4v"))
    }

    @Test
    fun `mpeg-4 audio is not classified as video`() {
        assertEquals(
            AttachmentMediaKind.AUDIO,
            AttachmentMedia.kind("audio/mp4", "public.mpeg-4-audio", "memo.m4a"),
        )
        assertEquals(AttachmentMediaKind.AUDIO, AttachmentMedia.kind(null, "com.apple.coreaudio-format", "x.caf"))
    }

    @Test
    fun `pdf is recognized by mime uti and extension`() {
        assertEquals(AttachmentMediaKind.PDF, AttachmentMedia.kind("application/pdf", null, "doc.bin"))
        assertEquals(AttachmentMediaKind.PDF, AttachmentMedia.kind(null, "com.adobe.pdf", null))
        assertEquals(
            AttachmentMediaKind.PDF,
            AttachmentMedia.kind("application/octet-stream", "public.data", "itinerary.pdf"),
        )
    }

    @Test
    fun `unknown files stay generic`() {
        assertEquals(AttachmentMediaKind.FILE, AttachmentMedia.kind(null, "public.data", "notes.txt"))
        assertEquals(AttachmentMediaKind.FILE, AttachmentMedia.kind("application/zip", null, "bundle.zip"))
        assertFalse(AttachmentMedia.isInlinePreviewable(null, null, "notes.txt"))
    }

    @Test
    fun `previewable kinds include apple media and pdfs`() {
        assertTrue(AttachmentMedia.isInlinePreviewable("image/heif", null, null))
        assertTrue(AttachmentMedia.isInlinePreviewable(null, null, "highlights.MOV"))
        assertTrue(AttachmentMedia.isInlinePreviewable(null, null, "voice.caf"))
        assertTrue(AttachmentMedia.isInlinePreviewable(null, null, "scan.pdf"))
    }

    @Test
    fun `suggested mime fills in when the stored type is generic`() {
        assertEquals("image/heic", AttachmentMedia.suggestedMime("application/octet-stream", null, "IMG_1.heic"))
        assertEquals("video/quicktime", AttachmentMedia.suggestedMime(null, "public.data", "RenderedVideo.mov"))
        assertEquals("application/pdf", AttachmentMedia.suggestedMime(null, "com.adobe.pdf", "x.bin"))
        assertEquals("video/quicktime", AttachmentMedia.suggestedMime("video/quicktime", null, "x.bin"))
    }
}

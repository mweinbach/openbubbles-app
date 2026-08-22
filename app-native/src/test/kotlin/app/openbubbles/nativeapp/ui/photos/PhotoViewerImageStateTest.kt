package app.openbubbles.nativeapp.ui.photos

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PhotoViewerImageStateTest {

    @Test
    fun `preview remains visible while the downloaded original is decoding`() {
        val state = photoViewerImageState(
            originalAvailable = true,
            originalDecoded = false,
            previewDecoded = true,
        )

        assertTrue(state.showPreview)
        assertFalse(state.showTransferStatus)
    }

    @Test
    fun `undecodable original keeps its preview without an error overlay`() {
        val state = photoViewerImageState(
            originalAvailable = true,
            originalDecoded = false,
            previewDecoded = true,
        )

        assertTrue(state.showPreview)
        assertFalse(state.showTransferStatus)
    }

    @Test
    fun `successfully decoded original replaces the preview`() {
        val state = photoViewerImageState(
            originalAvailable = true,
            originalDecoded = true,
            previewDecoded = true,
        )

        assertFalse(state.showPreview)
        assertFalse(state.showTransferStatus)
    }

    @Test
    fun `preview without an original keeps transfer status visible`() {
        val state = photoViewerImageState(
            originalAvailable = false,
            originalDecoded = false,
            previewDecoded = true,
        )

        assertTrue(state.showPreview)
        assertTrue(state.showTransferStatus)
    }

    @Test
    fun `accessible zoom controls step in both directions`() {
        assertEquals(1.5f, steppedPhotoViewerZoom(1f, zoomIn = true))
        assertEquals(1f, steppedPhotoViewerZoom(1.5f, zoomIn = false))
        assertEquals(3f, steppedPhotoViewerZoom(2f, zoomIn = true))
    }

    @Test
    fun `accessible zoom controls stay inside viewer bounds`() {
        assertEquals(1f, steppedPhotoViewerZoom(1f, zoomIn = false))
        assertEquals(1f, steppedPhotoViewerZoom(1.2f, zoomIn = false))
        assertEquals(6f, steppedPhotoViewerZoom(5f, zoomIn = true))
        assertEquals(6f, steppedPhotoViewerZoom(6f, zoomIn = true))
    }
}

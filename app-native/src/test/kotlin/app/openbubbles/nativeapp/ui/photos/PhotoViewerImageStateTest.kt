package app.openbubbles.nativeapp.ui.photos

import kotlin.test.Test
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
}

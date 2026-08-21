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
            originalDecodeInProgress = true,
            previewDecoded = true,
        )

        assertTrue(state.showPreview)
        assertFalse(state.originalUnsupported)
    }

    @Test
    fun `undecodable original keeps its preview and exposes a terminal explanation`() {
        val state = photoViewerImageState(
            originalAvailable = true,
            originalDecoded = false,
            originalDecodeInProgress = false,
            previewDecoded = true,
        )

        assertTrue(state.showPreview)
        assertTrue(state.originalUnsupported)
    }

    @Test
    fun `successfully decoded original replaces the preview`() {
        val state = photoViewerImageState(
            originalAvailable = true,
            originalDecoded = true,
            originalDecodeInProgress = false,
            previewDecoded = true,
        )

        assertFalse(state.showPreview)
        assertFalse(state.originalUnsupported)
    }

    @Test
    fun `preview without an original is not treated as unsupported`() {
        val state = photoViewerImageState(
            originalAvailable = false,
            originalDecoded = false,
            originalDecodeInProgress = false,
            previewDecoded = true,
        )

        assertTrue(state.showPreview)
        assertFalse(state.originalUnsupported)
    }
}

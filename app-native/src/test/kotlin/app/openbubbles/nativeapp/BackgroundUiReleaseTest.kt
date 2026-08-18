package app.openbubbles.nativeapp

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class BackgroundUiReleaseTest {
    @Test
    fun `hidden UI uses a bounded one-shot grace period`() {
        assertEquals(60_000L, NativeMainActivity.BACKGROUND_UI_RELEASE_MS)
        assertTrue(NativeMainActivity.BACKGROUND_UI_RELEASE_MS in 30_000L..120_000L)
    }

    @Test
    fun `fold recreation persists the open route under a stable key`() {
        assertEquals("resume_route", NativeMainActivity.STATE_RESUME_ROUTE)
    }
}

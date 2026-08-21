package app.openbubbles.nativeapp.data

import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.junit.Test

class MediaExportPolicyTest {
    @Test
    fun `hdr heic with a readable gain map converts to ultra hdr jpeg on api 34`() {
        val plan = imageExportPlan(
            mime = "image/heic",
            uti = "public.heic",
            name = "IMG_0001.heic",
            hasReadableGainmap = true,
            sdkInt = 34,
        )
        assertEquals(ImageExportPlan.ConvertToUltraHdrJpeg, plan)
    }

    @Test
    fun `unreadable gain map keeps the copy byte-identical`() {
        val plan = imageExportPlan(
            mime = "image/heic",
            uti = null,
            name = "IMG_0001.heic",
            hasReadableGainmap = false,
            sdkInt = 34,
        )
        assertEquals(ImageExportPlan.CopyBytes, plan)
    }

    @Test
    fun `pre ultra hdr os never converts`() {
        val plan = imageExportPlan(
            mime = "image/heic",
            uti = null,
            name = "IMG_0001.heic",
            hasReadableGainmap = true,
            sdkInt = 33,
        )
        assertEquals(ImageExportPlan.CopyBytes, plan)
    }

    @Test
    fun `jpeg sources copy bytes even with a gain map`() {
        // A gain-mapped JPEG is already Ultra HDR; re-encoding would only
        // lose quality.
        val plan = imageExportPlan(
            mime = "image/jpeg",
            uti = "public.jpeg",
            name = "photo.jpg",
            hasReadableGainmap = true,
            sdkInt = 35,
        )
        assertEquals(ImageExportPlan.CopyBytes, plan)
    }

    @Test
    fun `heic container detection uses mime uti and extension fallbacks`() {
        assertTrue(isHeicContainer("image/heic", null, null))
        assertTrue(isHeicContainer("IMAGE/HEIF", null, null))
        assertTrue(isHeicContainer(null, "public.heic", null))
        assertTrue(isHeicContainer(null, null, "IMG_0001.HEIC"))
        assertTrue(isHeicContainer("application/octet-stream", null, "clip.heif"))
        assertFalse(isHeicContainer("image/jpeg", "public.jpeg", "photo.jpg"))
        assertFalse(isHeicContainer(null, null, null))
    }

    @Test
    fun `converted exports rename to jpg and report the jpeg mime`() {
        assertEquals(
            "IMG_0001.jpg",
            exportedImageDisplayName("IMG_0001.heic", ImageExportPlan.ConvertToUltraHdrJpeg),
        )
        assertEquals(
            "IMG_0001.heic",
            exportedImageDisplayName("IMG_0001.heic", ImageExportPlan.CopyBytes),
        )
        assertEquals("image.jpg", exportedImageDisplayName(null, ImageExportPlan.ConvertToUltraHdrJpeg))
        assertEquals("image/jpeg", exportedImageMime("image/heic", ImageExportPlan.ConvertToUltraHdrJpeg))
        assertEquals("image/heic", exportedImageMime("image/heic", ImageExportPlan.CopyBytes))
        assertEquals("image/*", exportedImageMime(null, ImageExportPlan.CopyBytes))
    }
}

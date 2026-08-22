package app.openbubbles.nativeapp.data.photos

import app.openbubbles.core.photos.PhotoMediaKind
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class PhotoLibraryExportTest {

    @Test
    fun `image export lands in the DCIM album with its iCloud name`() {
        val plan = PhotoLibraryExport.plan(
            cachedFileName = "abc123.heic",
            filename = "IMG_4821.HEIC",
            mediaKind = PhotoMediaKind.Image,
            capturedAtMs = 1_700_000_000_000,
        )

        assertEquals("IMG_4821.HEIC", plan?.displayName)
        assertEquals("image/heic", plan?.mimeType)
        assertEquals("DCIM/iCloud", plan?.relativePath)
        assertEquals(false, plan?.video)
        assertEquals(1_700_000_000_000, plan?.dateTakenMillis)
    }

    @Test
    fun `video export shares the one DCIM album and is flagged as video`() {
        val plan = PhotoLibraryExport.plan(
            cachedFileName = "def456.mov",
            filename = "IMG_0007.MOV",
            mediaKind = PhotoMediaKind.Video,
            capturedAtMs = null,
        )

        assertEquals("video/quicktime", plan?.mimeType)
        assertEquals("DCIM/iCloud", plan?.relativePath)
        assertEquals(true, plan?.video)
        assertNull(plan?.dateTakenMillis)
    }

    @Test
    fun `live photo exports as one motion image instead of a separate still and video`() {
        val plan = PhotoLibraryExport.motionPhotoPlan(
            cachedFileName = "abc123.heic",
            filename = "IMG_4821.HEIC",
            capturedAtMs = 1_700_000_000_000,
        )

        assertEquals("IMG_4821.MP.jpg", plan?.displayName)
        assertEquals("image/jpeg", plan?.mimeType)
        assertEquals("DCIM/iCloud", plan?.relativePath)
        assertEquals(false, plan?.video)
        assertEquals(1_700_000_000_000, plan?.dateTakenMillis)
        assertNull(PhotoLibraryExport.motionPhotoPlan("abc123.mov", "IMG_4821.MOV", null))
    }

    @Test
    fun `the cached file decides the format when the iCloud name disagrees`() {
        val plan = PhotoLibraryExport.plan(
            cachedFileName = "abc123.jpg",
            filename = "IMG_4821.HEIC",
            mediaKind = PhotoMediaKind.Image,
            capturedAtMs = null,
        )

        assertEquals("IMG_4821.jpg", plan?.displayName)
        assertEquals("image/jpeg", plan?.mimeType)
    }

    @Test
    fun `nameless sniffed HEIC exports as a camera-style photo`() {
        val plan = PhotoLibraryExport.plan(
            cachedFileName = "0123456789abcdef0123456789abcdef.heic",
            filename = null,
            mediaKind = PhotoMediaKind.Image,
            capturedAtMs = 1_700_000_000_000,
        )

        assertEquals("IMG_0123456789ab.heic", plan?.displayName)
        assertEquals("image/heic", plan?.mimeType)
        assertEquals("DCIM/iCloud", plan?.relativePath)
        assertEquals(false, plan?.video)
    }

    @Test
    fun `nameless sniffed video uses a camera-style video filename`() {
        val plan = PhotoLibraryExport.plan(
            cachedFileName = "fedcba9876543210.mov",
            filename = null,
            mediaKind = PhotoMediaKind.Video,
            capturedAtMs = null,
        )

        assertEquals("VID_fedcba987654.mov", plan?.displayName)
        assertEquals("video/quicktime", plan?.mimeType)
    }

    @Test
    fun `a missing or hostile name cannot escape the album`() {
        assertEquals(
            "IMG_abc123.jpg",
            PhotoLibraryExport.plan("abc123.jpg", null, PhotoMediaKind.Image, null)?.displayName,
        )
        assertEquals(
            "passwd.jpg",
            PhotoLibraryExport.plan(
                cachedFileName = "abc123.jpg",
                filename = "../../etc/passwd",
                mediaKind = PhotoMediaKind.Image,
                capturedAtMs = null,
            )?.displayName,
        )
        assertEquals(
            "IMG_1.jpg",
            PhotoLibraryExport.plan(
                cachedFileName = "abc123.jpg",
                filename = "IMG\n_1.jpg",
                mediaKind = PhotoMediaKind.Image,
                capturedAtMs = null,
            )?.displayName,
        )
    }

    @Test
    fun `unknown or contradictory media is not exported`() {
        assertNull(PhotoLibraryExport.plan("abc123.original", "IMG_1", PhotoMediaKind.Image, null))
        assertNull(PhotoLibraryExport.plan("abc123.image", "IMG_1", PhotoMediaKind.Image, null))
        assertNull(PhotoLibraryExport.plan("abc123.jpg", "IMG_1.jpg", PhotoMediaKind.Unknown, null))
        // A video-typed asset whose cache file is an image, or the reverse,
        // would misfile the row in the gallery.
        assertNull(PhotoLibraryExport.plan("abc123.jpg", "IMG_1.jpg", PhotoMediaKind.Video, null))
        assertNull(PhotoLibraryExport.plan("abc123.mov", "IMG_1.mov", PhotoMediaKind.Image, null))
    }
}

package app.openbubbles.nativeapp.service

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SharedAlbumGalleryExportTest {

    private val root = File("/data/app/files/Pictures/Shared Albums")

    @Test
    fun `direct child of an album folder qualifies`() {
        val plan = SharedAlbumGalleryExport.plan(root, File(root, "Family/IMG_0001.jpeg"), 42)

        assertNotNull(plan)
        assertEquals("Family", plan.albumName)
        assertEquals("IMG_0001.jpeg", plan.displayName)
    }

    @Test
    fun `file directly under the root does not qualify`() {
        assertNull(SharedAlbumGalleryExport.plan(root, File(root, "IMG_0001.jpeg"), 42))
    }

    @Test
    fun `file nested below an album folder does not qualify`() {
        assertNull(SharedAlbumGalleryExport.plan(root, File(root, "Family/sub/IMG_0001.jpeg"), 42))
    }

    @Test
    fun `file outside the root does not qualify`() {
        assertNull(SharedAlbumGalleryExport.plan(root, File("/data/app/files/Other/Family/IMG_0001.jpeg"), 42))
    }

    @Test
    fun `non-media extension does not qualify`() {
        assertNull(SharedAlbumGalleryExport.plan(root, File(root, "Family/notes.txt"), 42))
        assertNull(SharedAlbumGalleryExport.plan(root, File(root, "Family/noextension"), 42))
    }

    @Test
    fun `album maps to a relative path under Pictures`() {
        val plan = SharedAlbumGalleryExport.plan(root, File(root, "Trip 2024/IMG_0002.heic"), 42)

        assertNotNull(plan)
        assertEquals("Pictures/Shared Albums/Trip 2024", plan.relativePath)
    }

    @Test
    fun `image extension maps to image mime and collection`() {
        val plan = SharedAlbumGalleryExport.plan(root, File(root, "Family/IMG_0003.HEIC"), 42)

        assertNotNull(plan)
        assertEquals("image/heic", plan.mimeType)
        assertFalse(plan.isVideo)
    }

    @Test
    fun `video extension maps to video mime and collection`() {
        val plan = SharedAlbumGalleryExport.plan(root, File(root, "Family/clip.mov"), 42)

        assertNotNull(plan)
        assertEquals("video/quicktime", plan.mimeType)
        assertTrue(plan.isVideo)
    }

    @Test
    fun `same file and length produce the same dedup key`() {
        val first = SharedAlbumGalleryExport.plan(root, File(root, "Family/IMG_0001.jpeg"), 42)
        val again = SharedAlbumGalleryExport.plan(root, File(root, "Family/IMG_0001.jpeg"), 42)

        assertEquals(first?.dedupKey, again?.dedupKey)
    }

    @Test
    fun `changed length produces a different dedup key`() {
        val before = SharedAlbumGalleryExport.plan(root, File(root, "Family/IMG_0001.jpeg"), 42)
        val after = SharedAlbumGalleryExport.plan(root, File(root, "Family/IMG_0001.jpeg"), 43)

        assertNotEquals(before?.dedupKey, after?.dedupKey)
    }

    @Test
    fun `same file name in different albums produces different dedup keys`() {
        val family = SharedAlbumGalleryExport.plan(root, File(root, "Family/IMG_0001.jpeg"), 42)
        val trip = SharedAlbumGalleryExport.plan(root, File(root, "Trip/IMG_0001.jpeg"), 42)

        assertNotEquals(family?.dedupKey, trip?.dedupKey)
    }
}

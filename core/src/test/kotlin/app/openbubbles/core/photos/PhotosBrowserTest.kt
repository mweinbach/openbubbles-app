package app.openbubbles.core.photos

import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class PhotosBrowserTest {
    @Test
    fun `initial probe does not query records while indexing`() = runBlocking {
        val port = FakePhotosPort(
            access = PhotosAccess(PhotosAvailability.Indexing, "Still indexing"),
        )

        val snapshot = PhotosBrowser(port).initial()

        assertEquals(PhotosAvailability.Indexing, snapshot.access.availability)
        assertEquals(emptyList(), snapshot.assets)
        assertEquals(0, port.pageCalls)
    }

    @Test
    fun `next page appends and deduplicates master ids`() = runBlocking {
        val first = photo("master-1")
        val second = photo("master-2")
        val port = FakePhotosPort(
            pages = ArrayDeque(
                listOf(
                    PhotosPage(listOf(first), "1"),
                    PhotosPage(listOf(first, second), null),
                ),
            ),
        )
        val browser = PhotosBrowser(port, pageSize = 2)

        val snapshot = browser.next(browser.initial())

        assertEquals(listOf("master-1", "master-2"), snapshot.assets.map { it.id })
        assertEquals(null, snapshot.nextCursor)
        assertEquals(listOf(null, "1"), port.cursors)
    }

    @Test
    fun `page size stays inside ffi bound`() {
        assertFailsWith<IllegalArgumentException> { PhotosBrowser(FakePhotosPort(), 101) }
    }

    @Test
    fun `non advancing cursor fails instead of repeating the same page`() = runBlocking {
        val port = FakePhotosPort(
            pages = ArrayDeque(
                listOf(
                    PhotosPage(listOf(photo("master-1")), "1"),
                    PhotosPage(listOf(photo("master-2")), "1"),
                ),
            ),
        )
        val browser = PhotosBrowser(port)
        val initial = browser.initial()

        assertFailsWith<IllegalStateException> { browser.next(initial) }
        assertEquals(listOf(null, "1"), port.cursors)
    }
}

private class FakePhotosPort(
    private val access: PhotosAccess = PhotosAccess(PhotosAvailability.Ready, "Ready"),
    private val pages: ArrayDeque<PhotosPage> = ArrayDeque(),
) : PhotosPort {
    var pageCalls = 0
    val cursors = mutableListOf<String?>()

    override suspend fun access(): PhotosAccess = access

    override suspend fun page(cursor: String?, limit: Int): PhotosPage {
        pageCalls += 1
        cursors += cursor
        return pages.removeFirst()
    }
}

private fun photo(id: String) = PhotoSummary(
    id = id,
    assetId = "asset-$id",
    filename = "$id.heic",
    mediaKind = PhotoMediaKind.Image,
    livePhoto = false,
    width = 4032,
    height = 3024,
    originalSize = 4_000_000,
    capturedAtMs = 1_700_000_000_000,
    addedAtMs = 1_700_000_000_000,
    favorite = false,
    hidden = false,
)

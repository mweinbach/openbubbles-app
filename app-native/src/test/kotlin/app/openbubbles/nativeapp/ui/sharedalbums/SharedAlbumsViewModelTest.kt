package app.openbubbles.nativeapp.ui.sharedalbums

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.test.runTest

class SharedAlbumsViewModelTest {
    @Test
    fun `fake port lists and filters albums`() = runTest {
        val port = FakeSharedAlbumsPort(
            albums = listOf(
                SharedAlbumUi("1", "Family", "Alex", null, 4, false, false, null),
                SharedAlbumUi("2", "Vacation", "Sam", null, 8, false, false, null),
            ),
        )

        assertEquals(listOf("Family"), filterSharedAlbums(port.list(false), "alex").map { it.name })
        assertEquals(listOf("Vacation"), filterSharedAlbums(port.list(false), "vac").map { it.name })
    }

    @Test
    fun `fake port toggles album sync`() = runTest {
        val port = FakeSharedAlbumsPort(
            albums = listOf(SharedAlbumUi("1", "Family", null, null, 4, false, false, null)),
        )

        port.setSync("1", "/pictures/family")

        assertEquals(true, port.list(false).single().syncing)
        assertEquals("/pictures/family", port.list(false).single().location)
    }
}

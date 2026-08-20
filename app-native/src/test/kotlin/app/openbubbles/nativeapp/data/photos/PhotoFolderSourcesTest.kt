package app.openbubbles.nativeapp.data.photos

import kotlin.test.Test
import kotlin.test.assertEquals

class PhotoFolderSourcesTest {

    @Test
    fun `folder grant cleanup targets stored and orphaned persisted grants exactly once`() {
        assertEquals(
            listOf(
                "content://documents/persisted-only",
                "content://documents/shared",
                "content://documents/stored-only",
            ),
            photoFolderGrantValues(
                stored = setOf(
                    "content://documents/stored-only",
                    "content://documents/shared",
                    " ",
                ),
                persisted = setOf(
                    "content://documents/shared",
                    "content://documents/persisted-only",
                ),
            ),
        )
    }
}

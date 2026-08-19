package app.openbubbles.nativeapp.ui.common

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

class ImageDecodeCacheKeyTest {

    @Test
    fun `avatar generation changes cache identity for unchanged content URI`() {
        val uri = "content://com.android.contacts/contacts/lookup/alice/7/photo"

        val before = uriImageCacheKey(uri, maxDimensionPx = 96, cacheGeneration = 4)
        val after = uriImageCacheKey(uri, maxDimensionPx = 96, cacheGeneration = 5)

        assertNotEquals(before, after)
        assertEquals(before, uriImageCacheKey(uri, maxDimensionPx = 96, cacheGeneration = 4))
    }
}

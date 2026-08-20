package app.openbubbles.nativeapp.data.photos

import kotlin.test.Test
import kotlin.test.assertFalse

class PhotosBackgroundSyncTest {
    @Test
    fun backgroundPhotosSyncShipsHardDisabled() {
        assertFalse(PhotosBackgroundSync.ENABLED)
    }
}

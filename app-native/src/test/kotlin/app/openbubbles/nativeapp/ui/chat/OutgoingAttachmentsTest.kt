package app.openbubbles.nativeapp.ui.chat

import java.io.File
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.junit.Test

class OutgoingAttachmentsTest {
    private val applicationPackageName = "com.openbubbles.messaging"
    private val cacheDirectory = File("/data/user/0/$applicationPackageName/cache")

    @Test
    fun `video names survive generic provider mime types`() {
        assertTrue(isOutgoingVideo("application/octet-stream", "clip.mov"))
        assertTrue(isOutgoingVideo(null, "clip.MP4"))
        assertTrue(isOutgoingVideo("video/quicktime", "unnamed"))
        assertFalse(isOutgoingVideo("application/octet-stream", "notes.pdf"))
    }

    @Test
    fun `system picker and app owned camera captures remain valid attachment sources`() {
        assertTrue(isSafeSource("content://com.android.providers.media.documents/document/image%3A42"))
        assertTrue(isSafeSource("content://com.google.android.apps.photos.contentprovider/photo/42"))
        assertTrue(isSafeSource("content://$applicationPackageName.fileprovider/captures/photo.jpg"))
        assertTrue(isSafeSource("file://${cacheDirectory.path}/captures/video.mp4"))
        assertTrue(isSafeSource("file://${cacheDirectory.path}/outgoing/compressed.mp4"))
    }

    @Test
    fun `private providers and files outside app owned draft roots are rejected`() {
        assertFalse(isSafeSource("content://$applicationPackageName.fileprovider/attachments/private.jpg"))
        assertFalse(isSafeSource("content://$applicationPackageName.fileprovider/icloud_photo_originals/private.jpg"))
        assertFalse(isSafeSource("content://$applicationPackageName.fileprovider/diagnostics/log.zip"))
        assertFalse(isSafeSource("content://$applicationPackageName.androidx-startup/private"))
        assertFalse(isSafeSource("file:///data/user/0/$applicationPackageName/files/secret.db"))
        assertFalse(isSafeSource("file://${cacheDirectory.path}/outgoing/../secret.db"))
        assertFalse(isSafeSource("https://example.com/photo.jpg"))
        assertFalse(isSafeSource("content:///photo.jpg"))
    }

    private fun isSafeSource(source: String): Boolean =
        isSafeOutgoingAttachmentSource(source, applicationPackageName, cacheDirectory)
}

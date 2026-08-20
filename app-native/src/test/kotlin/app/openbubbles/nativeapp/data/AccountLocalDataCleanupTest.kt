package app.openbubbles.nativeapp.data

import java.nio.file.Files
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AccountLocalDataCleanupTest {

    @Test
    fun `owned cache cleanup removes nested data and preserves every sibling`() {
        val files = Files.createTempDirectory("openbubbles-account-cache").toFile()
        try {
            val photos = files.resolve(ICLOUD_PHOTOS_CACHE_ROOT)
            photos.resolve("previews/nested").mkdirs()
            photos.resolve("previews/nested/preview.jpg").writeText("preview")
            photos.resolve("uploads/original.jpg").apply {
                parentFile!!.mkdirs()
                writeText("upload")
            }
            val contacts = files.resolve(ICLOUD_CONTACT_AVATAR_ROOT).apply { mkdirs() }
            contacts.resolve("avatar.img").writeText("avatar")
            val messageStore = files.resolve("app_flutter").apply { mkdirs() }
            messageStore.resolve("data.mdb").writeText("keep")
            val backgrounds = files.resolve("chat_backgrounds").apply { mkdirs() }
            backgrounds.resolve("wallpaper.jpg").writeText("keep")

            val photosResult = clearOwnedAppleAccountRoot(files, ICLOUD_PHOTOS_CACHE_ROOT)
            val contactsResult = clearOwnedAppleAccountRoot(files, ICLOUD_CONTACT_AVATAR_ROOT)

            assertTrue(photosResult.complete)
            assertTrue(contactsResult.complete)
            assertFalse(photos.exists())
            assertFalse(contacts.exists())
            assertEquals("keep", messageStore.resolve("data.mdb").readText())
            assertEquals("keep", backgrounds.resolve("wallpaper.jpg").readText())
        } finally {
            files.deleteRecursively()
        }
    }

    @Test
    fun `owned cache cleanup deletes a symlink without traversing its target`() {
        val files = Files.createTempDirectory("openbubbles-account-cache-link")
        val outside = Files.createTempDirectory("openbubbles-account-cache-outside")
        try {
            val root = files.resolve(ICLOUD_PHOTOS_CACHE_ROOT)
            Files.createDirectories(root)
            val outsideFile = outside.resolve("must-stay.jpg")
            Files.writeString(outsideFile, "keep")
            Files.createSymbolicLink(root.resolve("escape"), outside)
            Files.createSymbolicLink(root.resolve("dangling"), outside.resolve("missing"))

            val result = clearOwnedAppleAccountRoot(files.toFile(), ICLOUD_PHOTOS_CACHE_ROOT)

            assertTrue(result.complete)
            assertTrue(Files.exists(outsideFile))
            assertEquals("keep", Files.readString(outsideFile))
        } finally {
            files.toFile().deleteRecursively()
            outside.toFile().deleteRecursively()
        }
    }

    @Test
    fun `cleanup refuses roots not owned by Apple account features`() {
        val files = Files.createTempDirectory("openbubbles-account-cache-boundary").toFile()
        try {
            assertFailsWith<IllegalArgumentException> {
                clearOwnedAppleAccountRoot(files, "app_flutter")
            }
        } finally {
            files.deleteRecursively()
        }
    }

    @Test
    fun `cleanup runs every step and retains each failure`() = runTest {
        val calls = mutableListOf<Int>()
        val result = runAccountCleanupSteps(
            {
                calls += 1
                error("first")
            },
            { calls += 2 },
            {
                calls += 3
                error("third")
            },
        )

        assertEquals(listOf(1, 2, 3), calls)
        val failure = result.exceptionOrNull()!!
        assertEquals("first", failure.message)
        assertEquals(listOf("third"), failure.suppressed.map { it.message })
    }
}

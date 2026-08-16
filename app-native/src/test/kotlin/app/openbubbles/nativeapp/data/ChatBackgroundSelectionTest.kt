package app.openbubbles.nativeapp.data

import java.io.File
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ChatBackgroundSelectionTest {
    @Test
    fun `local background overrides and falls back to synced background`() {
        val directory = Files.createTempDirectory("ob-background-selection").toFile()
        try {
            val syncedFile = File(directory, "synced.img").apply { writeBytes(byteArrayOf(1)) }
            val localFile = File(directory, "local.img").apply { writeBytes(byteArrayOf(2)) }
            val missingLocal = File(directory, "missing-local.img")
            val synced = chat(custom = null, synced = syncedFile.absolutePath)
            val local = chat(custom = localFile.absolutePath, synced = syncedFile.absolutePath)
            val fallback = chat(custom = missingLocal.absolutePath, synced = syncedFile.absolutePath)

            assertEquals(syncedFile.absolutePath, synced.effectiveBackgroundPath())
            assertEquals(localFile.absolutePath, local.effectiveBackgroundPath())
            assertEquals(syncedFile.absolutePath, fallback.effectiveBackgroundPath())
            assertNull(chat(custom = null, synced = null).effectiveBackgroundPath())
        } finally {
            directory.deleteRecursively()
        }
    }

    private fun chat(custom: String?, synced: String?) = ChatListItem(
        id = 1L,
        title = "Chat",
        snippet = null,
        date = 0L,
        unread = 0,
        pinned = false,
        avatarColor = 0L,
        customBackgroundPath = custom,
        transcriptBackgroundPath = synced,
    )
}

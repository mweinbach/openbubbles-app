package app.openbubbles.nativeapp.ui.chatinfo

import app.openbubbles.nativeapp.data.ChatListItem
import app.openbubbles.nativeapp.data.SharedContentPreview
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest

class ChatInfoWarmCacheTest {
    private fun details(address: String) = ContactDetails(
        displayName = "Name for $address",
        avatarPath = null,
        phones = listOf(address),
        emails = emptyList(),
        handleAddress = address,
    )

    @Test
    fun `contact details roundtrip by address`() {
        val value = details("+15550001")
        ChatInfoWarmCache.putContactDetails("+15550001", value)
        assertEquals(value, ChatInfoWarmCache.contactDetails("+15550001"))
        assertNull(ChatInfoWarmCache.contactDetails("+15550002"))
    }

    @Test
    fun `blank addresses are never stored`() {
        ChatInfoWarmCache.putContactDetails("", details("ignored"))
        ChatInfoWarmCache.putLocation(" ", ContactLocationUi.Unavailable)
        assertNull(ChatInfoWarmCache.contactDetails(""))
        assertNull(ChatInfoWarmCache.location(" "))
    }

    @Test
    fun `shared content roundtrips by chat id`() {
        val content = listOf(SharedContentPreview(id = "1", label = "photo.jpg", isImage = true))
        ChatInfoWarmCache.putSharedContent(42L, content)
        assertEquals(content, ChatInfoWarmCache.sharedContent(42L))
        assertNull(ChatInfoWarmCache.sharedContent(43L))
    }

    @Test
    fun `loading location is not a cacheable state`() {
        ChatInfoWarmCache.putLocation("+15550003", ContactLocationUi.Loading)
        assertNull(ChatInfoWarmCache.location("+15550003"))
        ChatInfoWarmCache.putLocation("+15550003", ContactLocationUi.NotSharing)
        assertEquals(ContactLocationUi.NotSharing, ChatInfoWarmCache.location("+15550003"))
    }

    @Test
    fun `poster only caches files that exist`() {
        val file = java.io.File.createTempFile("poster", ".jpg")
        try {
            ChatInfoWarmCache.putPoster("+15550004", file)
            assertEquals(file, ChatInfoWarmCache.poster("+15550004"))
            file.delete()
            assertNull(ChatInfoWarmCache.poster("+15550004"))
        } finally {
            file.delete()
        }
    }

    @Test
    fun `entries evict past the cap`() {
        val start = 20_000L
        for (i in 0 until 12) {
            ChatInfoWarmCache.putSharedContent(start + i, emptyList())
        }
        assertNull(ChatInfoWarmCache.sharedContent(start))
        assertTrue(ChatInfoWarmCache.sharedContent(start + 11) != null)
    }

    @Test
    fun `warm seeds a direct chat without a store`() = runTest {
        // No app context in unit tests: the db-backed pieces degrade to
        // fakes, so warm must still seed the card and shared strip from the
        // fallback resolution and never touch the network.
        ChatInfoWarmCache.warm(
            ChatListItem(
                id = 9_001L,
                title = "Mark Linsangan",
                snippet = null,
                date = 0L,
                unread = 0,
                pinned = false,
                avatarColor = 0xFF006C4C,
                avatarAddress = "+17033092799",
            ),
        )
        assertEquals(emptyList(), ChatInfoWarmCache.sharedContent(9_001L))
        assertEquals(
            "Mark Linsangan",
            ChatInfoWarmCache.contactDetails("+17033092799")?.displayName,
        )
    }

    @Test
    fun `warm skips the contact card for groups`() = runTest {
        ChatInfoWarmCache.warm(
            ChatListItem(
                id = 9_002L,
                title = "Family",
                snippet = null,
                date = 0L,
                unread = 0,
                pinned = false,
                avatarColor = 0xFF7C4FDF,
                isGroup = true,
            ),
        )
        assertEquals(emptyList(), ChatInfoWarmCache.sharedContent(9_002L))
        assertNull(ChatInfoWarmCache.contactDetails("Family"))
    }
}

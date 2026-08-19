package app.openbubbles.core.repo

import app.openbubbles.core.contacts.ContactSync
import app.openbubbles.core.contacts.RawContact
import app.openbubbles.db.Chat
import app.openbubbles.db.Handle
import app.openbubbles.db.MyObjectBox
import io.objectbox.BoxStore
import org.junit.After
import org.junit.Before
import org.junit.Test
import java.io.File
import java.util.Date
import kotlin.test.assertEquals

/**
 * relatedDirectChatIds memoizes the contact-to-chats grouping instead of
 * scanning every active chat per call. These tests pin the invalidation
 * story: external chat creation, external soft deletion, and lookups for
 * chats that are not in the active cache must all resolve correctly on the
 * very next call, without anyone calling invalidate.
 */
class ChatRepoRelatedCacheTest {

    private lateinit var store: BoxStore
    private lateinit var testDir: File

    @Before
    fun setUp() {
        testDir = java.nio.file.Files.createTempDirectory("ob-related-cache-test").toFile()
        store = MyObjectBox.builder().directory(testDir).build()
        ContactSync(store).upsertContacts(
            listOf(
                RawContact(
                    id = "icloud:jamie",
                    displayName = "Jamie Example",
                    firstName = "Jamie",
                    lastName = "Example",
                    avatarPath = null,
                    addresses = listOf("+15550000001", "jamie@icloud.com"),
                ),
            ),
        )
    }

    @After
    fun tearDown() {
        store.close()
        testDir.deleteRecursively()
    }

    @Test
    fun `chat created after the cache warmed is grouped on the next call`() {
        val phoneChat = chat("chat-phone", handle("+15550000001"))
        val repo = ChatRepo(store)
        assertEquals(listOf(phoneChat.id), repo.relatedDirectChatIds(phoneChat.id))

        val emailChat = chat("chat-email", handle("jamie@icloud.com"))

        assertEquals(
            listOf(phoneChat.id, emailChat.id).sorted(),
            repo.relatedDirectChatIds(phoneChat.id),
        )
    }

    @Test
    fun `externally soft-deleted chat leaves the group on the next call`() {
        val phoneChat = chat("chat-phone", handle("+15550000001"))
        val emailChat = chat("chat-email", handle("jamie@icloud.com"))
        val repo = ChatRepo(store)
        assertEquals(
            listOf(phoneChat.id, emailChat.id).sorted(),
            repo.relatedDirectChatIds(phoneChat.id),
        )

        store.boxFor(Chat::class.java).put(
            store.boxFor(Chat::class.java).get(emailChat.id).apply { dateDeleted = Date(500L) },
        )

        assertEquals(listOf(phoneChat.id), repo.relatedDirectChatIds(phoneChat.id))
    }

    @Test
    fun `soft-deleted chat still resolves its surviving group members`() {
        val phoneChat = chat("chat-phone", handle("+15550000001"))
        val emailChat = chat("chat-email", handle("jamie@icloud.com"))
        val repo = ChatRepo(store)

        repo.softDelete(phoneChat.id)

        assertEquals(listOf(emailChat.id), repo.relatedDirectChatIds(phoneChat.id))
    }

    @Test
    fun `chat without a linked contact maps to itself`() {
        val stranger = handle("stranger@icloud.com")
        val chat = chat("chat-stranger", stranger)

        assertEquals(listOf(chat.id), ChatRepo(store).relatedDirectChatIds(chat.id))
    }

    private fun handle(address: String): Handle = Handle().apply {
        this.address = address
        service = "iMessage"
        uniqueAddressAndService = "$address/$service"
    }.also(store.boxFor(Handle::class.java)::put)

    private fun chat(guid: String, handle: Handle): Chat {
        val chat = Chat().apply {
            this.guid = guid
            chatIdentifier = handle.address
            isRpSms = false
            handles.add(handle)
        }
        store.boxFor(Chat::class.java).put(chat)
        return chat
    }
}

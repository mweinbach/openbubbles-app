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
import kotlin.test.assertEquals

class ChatRepoContactTest {

    private lateinit var store: BoxStore
    private lateinit var testDir: File

    @Before
    fun setUp() {
        testDir = java.nio.file.Files.createTempDirectory("ob-chat-contact-test").toFile()
        store = MyObjectBox.builder().directory(testDir).build()
    }

    @After
    fun tearDown() {
        store.close()
        testDir.deleteRecursively()
    }

    @Test
    fun `chat title uses contact linked to its participant handle`() {
        val handle = Handle().apply {
            address = "friend@icloud.com"
            service = "iMessage"
            uniqueAddressAndService = "$address/$service"
        }
        store.boxFor(Handle::class.java).put(handle)
        ContactSync(store).upsertContacts(
            listOf(
                RawContact(
                    id = "icloud:friend",
                    displayName = "Friendly Person",
                    firstName = "Friendly",
                    lastName = "Person",
                    avatarPath = null,
                    addresses = listOf("friend@icloud.com"),
                ),
            ),
        )
        val chat = Chat().apply {
            guid = "iMessage;-;friend@icloud.com"
            chatIdentifier = "friend@icloud.com"
            isRpSms = false
            handles.add(handle)
        }
        store.boxFor(Chat::class.java).put(chat)

        assertEquals("Friendly Person", ChatRepo(store).chats().single().title)
    }
}

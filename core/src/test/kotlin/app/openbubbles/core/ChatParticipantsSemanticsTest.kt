package app.openbubbles.core

import app.openbubbles.core.model.participantAddresses
import app.openbubbles.db.Chat
import app.openbubbles.db.Handle
import app.openbubbles.db.MyObjectBox
import io.objectbox.BoxStore
import java.io.File
import kotlin.test.assertEquals
import org.junit.After
import org.junit.Before
import org.junit.Test

class ChatParticipantsSemanticsTest {

    private lateinit var store: BoxStore
    private lateinit var testDir: File

    @Before
    fun setUp() {
        testDir = java.nio.file.Files.createTempDirectory("ob-participants-test").toFile()
        store = MyObjectBox.builder().directory(testDir).build()
    }

    @After
    fun tearDown() {
        store.close()
        testDir.deleteRecursively()
    }

    @Test
    fun `group chat lists every member handle`() {
        val chat = chat(style = 43L, identifier = null, "mom@icloud.com", "dad@icloud.com")

        assertEquals(
            listOf("mom@icloud.com", "dad@icloud.com"),
            chat.participantAddresses(),
        )
    }

    @Test
    fun `direct chat resolves the other person when self is a stored handle`() {
        val chat = chat(
            style = 45L,
            identifier = "friend@icloud.com",
            "me@icloud.com",
            "friend@icloud.com",
        )

        assertEquals(
            listOf("friend@icloud.com"),
            chat.participantAddresses(setOf("me@icloud.com")),
        )
    }

    @Test
    fun `direct chat with one linked handle resolves that handle`() {
        val chat = chat(style = 45L, identifier = null, "+15550000001")

        assertEquals(listOf("+15550000001"), chat.participantAddresses())
    }

    @Test
    fun `direct chat without any handle falls back to the chat identifier`() {
        // Older ingests never linked a handle row; the conversation list still
        // resolves identity from the identifier, and participants must match.
        val chat = chat(style = 45L, identifier = "friend@icloud.com")

        assertEquals(listOf("friend@icloud.com"), chat.participantAddresses())
    }

    @Test
    fun `direct chat with self-only handles falls back to the chat identifier`() {
        val chat = chat(style = 45L, identifier = "friend@icloud.com", "me@icloud.com")

        assertEquals(
            listOf("friend@icloud.com"),
            chat.participantAddresses(setOf("me@icloud.com")),
        )
    }

    @Test
    fun `chat without handles or identifier yields no addresses`() {
        val chat = chat(style = 45L, identifier = null)

        assertEquals(emptyList(), chat.participantAddresses())
    }

    private fun chat(
        style: Long?,
        identifier: String?,
        vararg handleAddresses: String,
    ): Chat {
        val handles = handleAddresses.map { address ->
            Handle().apply {
                this.address = address
                service = "iMessage"
                uniqueAddressAndService = "$address/$service"
            }.also(store.boxFor(Handle::class.java)::put)
        }
        return Chat().apply {
            guid = "chat-${identifier ?: handles.joinToString("+") { it.address }}"
            chatIdentifier = identifier
            this.style = style
            isRpSms = false
            this.handles.addAll(handles)
        }.also(store.boxFor(Chat::class.java)::put)
    }
}

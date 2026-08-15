package app.openbubbles.core

import app.openbubbles.core.model.decodeDeleteMessageCommand
import kotlin.test.Test
import kotlin.test.assertEquals

class DeleteMessageCodecTest {

    @Test
    fun `decodes message recycle envelope`() {
        val command = decodeDeleteMessageCommand(
            """{"MoveToRecycleBin":{"target":{"Messages":["a","b"]},"recoverable_delete_date":1234}}""",
        )

        assertEquals(listOf("a", "b"), command.messageGuids)
        assertEquals(1234L, command.recoverableDeleteDateMs)
    }

    @Test
    fun `decodes operated chat envelope`() {
        val command = decodeDeleteMessageCommand(
            """{"RecoverChat":{"ptcpts":["one@example.com","+15551234567"],"groupID":"group-1","guid":"iMessage;+;group-1"}}""",
        )

        assertEquals("iMessage;+;group-1", command.chatGuid)
        assertEquals("group-1", command.groupId)
        assertEquals(listOf("one@example.com", "+15551234567"), command.participants)
    }
}

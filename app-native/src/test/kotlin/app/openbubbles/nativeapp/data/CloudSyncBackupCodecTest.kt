package app.openbubbles.nativeapp.data

import org.junit.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class CloudSyncBackupCodecTest {

    @Test
    fun `round trips cursors pending deletions and completion state`() {
        val expected = CloudSyncBackupState(
            chatCursor = byteArrayOf(1, 2, 3),
            messageCursor = byteArrayOf(4, 5),
            attachmentCursor = byteArrayOf(6),
            pendingChatDeletes = listOf("chat-a", "chat-b"),
            pendingMessageDeletes = listOf("message-a"),
            pendingAttachmentDeletes = listOf("attachment-a", "attachment-b"),
            historySyncComplete = true,
        )

        val actual = CloudSyncBackupCodec.decode(CloudSyncBackupCodec.encode(expected))

        assertContentEquals(expected.chatCursor, actual.chatCursor)
        assertContentEquals(expected.messageCursor, actual.messageCursor)
        assertContentEquals(expected.attachmentCursor, actual.attachmentCursor)
        assertEquals(expected.pendingChatDeletes, actual.pendingChatDeletes)
        assertEquals(expected.pendingMessageDeletes, actual.pendingMessageDeletes)
        assertEquals(expected.pendingAttachmentDeletes, actual.pendingAttachmentDeletes)
        assertTrue(actual.historySyncComplete)
    }

    @Test
    fun `rejects truncated state instead of partially restoring it`() {
        val encoded = CloudSyncBackupCodec.encode(
            CloudSyncBackupState(chatCursor = byteArrayOf(1, 2, 3)),
        )

        assertFailsWith<IllegalArgumentException> {
            CloudSyncBackupCodec.decode(encoded.copyOf(encoded.size - 1))
        }
    }
}

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
            localMessageDeletes = listOf("local-message-a"),
            localAttachmentDeletes = listOf("local-attachment-a"),
            historySyncComplete = true,
        )

        val actual = CloudSyncBackupCodec.decode(CloudSyncBackupCodec.encode(expected))

        assertContentEquals(expected.chatCursor, actual.chatCursor)
        assertContentEquals(expected.messageCursor, actual.messageCursor)
        assertContentEquals(expected.attachmentCursor, actual.attachmentCursor)
        assertEquals(expected.pendingChatDeletes, actual.pendingChatDeletes)
        assertEquals(expected.pendingMessageDeletes, actual.pendingMessageDeletes)
        assertEquals(expected.pendingAttachmentDeletes, actual.pendingAttachmentDeletes)
        assertEquals(expected.localMessageDeletes, actual.localMessageDeletes)
        assertEquals(expected.localAttachmentDeletes, actual.localAttachmentDeletes)
        assertTrue(actual.historySyncComplete)
    }

    @Test
    fun `legacy backup version remains readable without local-only deletion state`() {
        val modern = CloudSyncBackupCodec.encode(
            CloudSyncBackupState(
                pendingMessageDeletes = listOf("message-a"),
                historySyncComplete = true,
            ),
        )
        val legacy = modern.copyOf(modern.size - 8)
        java.nio.ByteBuffer.wrap(legacy).putInt(4, 1)

        val restored = CloudSyncBackupCodec.decode(legacy)

        assertEquals(listOf("message-a"), restored.pendingMessageDeletes)
        assertTrue(restored.historySyncComplete)
        assertTrue(restored.localMessageDeletes.isEmpty())
        assertTrue(restored.localAttachmentDeletes.isEmpty())
    }

    @Test
    fun `local-only deletion suppression remains bounded and keeps recent explicit removals`() {
        val ids = boundedLocalDeletionIds(
            existing = listOf("old-1", "old-2", "old-3"),
            added = listOf("new-1", "new-2"),
            limit = 3,
        )

        assertEquals(setOf("old-3", "new-1", "new-2"), ids)
        assertEquals(3, ids.size)
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

package app.openbubbles.nativeapp.service

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class JournalNotificationPolicyTest {

    @Test
    fun `fresh live journal entries suppress duplicates but retries recover alerts`() {
        assertEquals(
            IncomingNotificationSource.LIVE,
            journalEntryNotificationSource(IncomingNotificationSource.LIVE, priorAttempts = 0),
        )
        assertEquals(
            IncomingNotificationSource.JOURNAL_RECOVERY,
            journalEntryNotificationSource(IncomingNotificationSource.LIVE, priorAttempts = 1),
        )
        assertEquals(
            IncomingNotificationSource.JOURNAL_RECOVERY,
            journalEntryNotificationSource(IncomingNotificationSource.JOURNAL_RECOVERY, priorAttempts = 0),
        )
    }

    @Test
    fun `recovery after death before ingest posts the newly persisted message`() {
        assertEquals(
            IncomingNotificationDisposition.POST,
            incomingNotificationDisposition(
                eligibleFacts(newlyIngested = true),
            ),
        )
    }

    @Test
    fun `recovery after ingest but before notify posts an unread persisted message`() {
        assertEquals(
            IncomingNotificationDisposition.POST,
            incomingNotificationDisposition(
                eligibleFacts(newlyIngested = false),
            ),
        )
    }

    @Test
    fun `replay after notify does not alert while that message notification is active`() {
        assertEquals(
            IncomingNotificationDisposition.ALREADY_ACTIVE,
            incomingNotificationDisposition(
                eligibleFacts(
                    newlyIngested = false,
                    activeMatchingNotification = true,
                ),
            ),
        )
    }

    @Test
    fun `live dedupe never turns a replay into a new alert`() {
        assertEquals(
            IncomingNotificationDisposition.NOT_NEW_LIVE_DELIVERY,
            incomingNotificationDisposition(
                eligibleFacts(
                    source = IncomingNotificationSource.LIVE,
                    newlyIngested = false,
                ),
            ),
        )
    }

    @Test
    fun `visible muted read and outgoing recovery messages stay suppressed`() {
        val cases = listOf(
            eligibleFacts(conversationVisible = true) to IncomingNotificationDisposition.VISIBLE,
            eligibleFacts(muted = true) to IncomingNotificationDisposition.MUTED,
            eligibleFacts(unread = false) to IncomingNotificationDisposition.READ,
            eligibleFacts(eligibleIncoming = false) to IncomingNotificationDisposition.NOT_ELIGIBLE,
        )

        cases.forEach { (facts, expected) ->
            assertEquals(expected, incomingNotificationDisposition(facts))
        }
    }

    @Test
    fun `active notification matching requires both message and conversation identity`() {
        val identity = conversationIdentity(7L)
        val matching = ActiveMessageNotificationRef(
            id = identity.notificationId,
            conversationId = identity.conversationId,
            messageGuid = "message-1",
        )
        val sameConversationDifferentMessage = matching.copy(messageGuid = "message-2")
        val sameMessageDifferentConversation = ActiveMessageNotificationRef(
            id = conversationNotificationId(8L),
            conversationId = "chat-8",
            messageGuid = "message-1",
        )

        assertTrue(
            hasActiveMatchingMessageNotification(
                entries = listOf(matching),
                identity = identity,
                messageGuid = "message-1",
            ),
        )
        assertFalse(
            hasActiveMatchingMessageNotification(
                entries = listOf(sameConversationDifferentMessage, sameMessageDifferentConversation),
                identity = identity,
                messageGuid = "message-1",
            ),
        )
    }

    @Test
    fun `persisted unread boundary rejects read deleted and outgoing rows`() {
        assertTrue(
            isPersistedIncomingMessageUnread(
                chatHasUnreadMessage = true,
                chatDeleted = false,
                messageFromMe = false,
                messageDeleted = false,
                messageCreatedAtMs = 200L,
                lastReadAtMs = 100L,
            ),
        )
        assertFalse(
            isPersistedIncomingMessageUnread(
                chatHasUnreadMessage = true,
                chatDeleted = false,
                messageFromMe = false,
                messageDeleted = false,
                messageCreatedAtMs = 100L,
                lastReadAtMs = 100L,
            ),
        )
        assertFalse(
            isPersistedIncomingMessageUnread(
                chatHasUnreadMessage = true,
                chatDeleted = false,
                messageFromMe = true,
                messageDeleted = false,
                messageCreatedAtMs = 200L,
                lastReadAtMs = 100L,
            ),
        )
        assertFalse(
            isPersistedIncomingMessageUnread(
                chatHasUnreadMessage = true,
                chatDeleted = true,
                messageFromMe = false,
                messageDeleted = false,
                messageCreatedAtMs = 200L,
                lastReadAtMs = null,
            ),
        )
    }

    private fun eligibleFacts(
        source: IncomingNotificationSource = IncomingNotificationSource.JOURNAL_RECOVERY,
        newlyIngested: Boolean = false,
        eligibleIncoming: Boolean = true,
        persisted: Boolean = true,
        unread: Boolean = true,
        conversationVisible: Boolean = false,
        muted: Boolean = false,
        blocked: Boolean = false,
        notificationsEnabled: Boolean = true,
        activeMatchingNotification: Boolean = false,
    ) = IncomingNotificationFacts(
        source = source,
        newlyIngested = newlyIngested,
        eligibleIncoming = eligibleIncoming,
        persisted = persisted,
        unread = unread,
        conversationVisible = conversationVisible,
        muted = muted,
        blocked = blocked,
        notificationsEnabled = notificationsEnabled,
        activeMatchingNotification = activeMatchingNotification,
    )
}

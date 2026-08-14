package app.openbubbles.core.sync

import app.openbubbles.db.Chat
import app.openbubbles.db.Chat_
import app.openbubbles.db.Message
import app.openbubbles.db.Message_
import app.openbubbles.db.MyObjectBox
import io.objectbox.BoxStore
import io.objectbox.query.QueryBuilder
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Test
import uniffi.rust_lib_bluebubbles.UChatChange
import uniffi.rust_lib_bluebubbles.UChatSyncPage
import uniffi.rust_lib_bluebubbles.UCloudChat
import uniffi.rust_lib_bluebubbles.UCloudMessage
import uniffi.rust_lib_bluebubbles.UMessageChange
import uniffi.rust_lib_bluebubbles.UMessageSyncPage
import uniffi.rust_lib_bluebubbles.USyncState
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Scripted [CloudSyncPort] — hands out pages from queues and records every
 * cursor / deletion call so tests can assert loop + persistence behavior.
 */
private class FakeCloudSyncPort : CloudSyncPort {

    val chatPages = ArrayDeque<UChatSyncPage>()
    val messagePages = ArrayDeque<UMessageSyncPage>()

    /** Cursors received, in order (null = fresh start). */
    val chatCursorsReceived = mutableListOf<ByteArray?>()
    val messageCursorsReceived = mutableListOf<ByteArray?>()

    /** Remote deletions in call order. */
    val deletedChats = mutableListOf<List<String>>()
    val deletedMessages = mutableListOf<List<String>>()

    /** Call log across all port methods, for ordering assertions. */
    val calls = mutableListOf<String>()

    var state: USyncState = USyncState.AVAILABLE
    var inClique: Boolean = true
    var failOn: String? = null

    /** Invoked after each served chat page (index 1-based), for mid-run hooks. */
    var onChatsPageServed: ((Int) -> Unit)? = null
    private var chatPagesServed = 0

    override suspend fun syncState(): USyncState {
        calls += "state"
        return state
    }

    override suspend fun isInClique(): Boolean {
        calls += "clique"
        return inClique
    }

    override suspend fun chatsPage(cursor: ByteArray?): UChatSyncPage {
        calls += "chats"
        chatCursorsReceived += cursor
        failOn?.let { throw IllegalStateException(it) }
        val page = chatPages.removeFirst()
        chatPagesServed += 1
        onChatsPageServed?.invoke(chatPagesServed)
        return page
    }

    override suspend fun messagesPage(cursor: ByteArray?): UMessageSyncPage {
        calls += "messages"
        messageCursorsReceived += cursor
        return messagePages.removeFirst()
    }

    override suspend fun deleteChatsRemote(recordIds: List<String>) {
        calls += "delete-chats"
        deletedChats += recordIds
    }

    override suspend fun deleteMessagesRemote(recordIds: List<String>) {
        calls += "delete-messages"
        deletedMessages += recordIds
    }
}

/**
 * [CloudSyncManager] tests over a real ObjectBox store: backfill mapping,
 * guid dedupe, tombstones, latest-message wiring without unread, cursor
 * persistence across incremental runs, deletion flushing and cancellation.
 */
class CloudSyncManagerTest {

    private lateinit var store: BoxStore
    private lateinit var testDir: File
    private lateinit var port: FakeCloudSyncPort
    private lateinit var syncStore: InMemoryCloudSyncStateStore
    private lateinit var manager: CloudSyncManager

    @Before
    fun setUp() {
        testDir = java.nio.file.Files.createTempDirectory("ob-cloudsync-test").toFile()
        store = MyObjectBox.builder().directory(testDir).build()
        port = FakeCloudSyncPort()
        syncStore = InMemoryCloudSyncStateStore()
        manager = CloudSyncManager(store, port, syncStore)
    }

    @After
    fun tearDown() {
        store.close()
        testDir.deleteRecursively()
    }

    // ------------------------------------------------------------------
    // Builders
    // ------------------------------------------------------------------

    private fun cloudChat(
        recordId: String,
        guid: String = "iMessage;-;+15551234567",
        identifier: String = "+15551234567",
        groupId: String = "cloud-$recordId",
        style: Long = 45,
        serviceName: String = "iMessage",
        participants: List<String> = listOf("tel:+15551234567"),
        displayName: String? = null,
        groupVersion: UInt? = 1u,
        lastReadTimestamp: Long = 0,
    ) = UCloudChat(
        guid = guid,
        style = style,
        chatIdentifier = identifier,
        groupId = groupId,
        serviceName = serviceName,
        participants = participants,
        lastAddressedHandle = "mailto:me@icloud.com",
        displayName = displayName,
        groupVersion = groupVersion,
        lastSeenMessageGuid = null,
        lastReadMessageTimestamp = lastReadTimestamp,
        hasGroupPhoto = false,
    )

    private fun cloudMessage(
        recordId: String,
        guid: String = "msg-$recordId",
        chatId: String,
        text: String? = "hello from the cloud",
        sender: String = "tel:+15551234567",
        time: Long = 700_000_000_000_000_000L, // ~2023-03 in Apple ns
        flags: Long = 0,
    ) = UCloudMessage(
        guid = guid,
        chatId = chatId,
        sender = sender,
        time = time,
        msgType = 0,
        error = 0,
        service = "iMessage",
        flagsBits = flags,
        text = text,
        subject = null,
        hasAttachments = false,
        attachmentGuids = emptyList(),
        balloonBundleId = null,
        hasPayloadData = false,
        summaryInfoJson = null,
        effect = null,
        dateReadNs = null,
        dateDeliveredNs = null,
        associatedMessageType = null,
        associatedMessageGuid = null,
        threadOriginatorGuid = null,
        threadOriginatorPart = null,
        associatedMessageEmoji = null,
    )

    private fun chatPage(vararg records: UChatChange, cursor: ByteArray = byteArrayOf(1), more: Boolean = false) =
        UChatSyncPage(records.toList(), cursor, more, if (more) 1 else 3)

    private fun messagePage(vararg records: UMessageChange, cursor: ByteArray = byteArrayOf(2), more: Boolean = false) =
        UMessageSyncPage(records.toList(), cursor, more, if (more) 1 else 3)

    private fun chatByGuid(guid: String): Chat? =
        store.boxFor(Chat::class.java).query()
            .equal(Chat_.guid, guid, QueryBuilder.StringOrder.CASE_SENSITIVE)
            .build().use { it.findFirst() }

    private fun messageByGuid(guid: String): Message? =
        store.boxFor(Message::class.java).query()
            .equal(Message_.guid, guid, QueryBuilder.StringOrder.CASE_SENSITIVE)
            .build().use { it.findFirst() }

    /** ByteArray has no structural equals — compare cursor lists by content. */
    private fun assertCursors(expected: List<ByteArray?>, actual: List<ByteArray?>) {
        assertEquals(expected.size, actual.size, "cursor count mismatch: $actual")
        expected.zip(actual).forEachIndexed { i, (exp, act) ->
            if (exp == null || act == null) {
                assertEquals(exp == null, act == null, "cursor #$i null-ness")
            } else {
                assertTrue(exp.contentEquals(act), "cursor #$i: expected ${exp.toList()}, got ${act.toList()}")
            }
        }
    }

    private fun runSync(mode: SyncMode = SyncMode.FULL): SyncSummary = runBlocking { manager.sync(mode) }

    // ------------------------------------------------------------------
    // Backfill
    // ------------------------------------------------------------------

    @Test
    fun `full sync backfills chats and messages with entity mapping and latest wiring`() {
        port.chatPages += chatPage(
            UChatChange("rec-chat-1", cloudChat("rec-chat-1", guid = "iMessage;+;family", displayName = "Family", participants = listOf("tel:+15550000001", "mailto:me@icloud.com"), style = 43, groupId = "cloud-family"), blob = byteArrayOf()),
            UChatChange("rec-chat-2", cloudChat("rec-chat-2", guid = "iMessage;-;friend@icloud.com", identifier = "friend@icloud.com", groupId = "cloud-dm"), blob = byteArrayOf()),
        )
        port.messagePages += messagePage(
            UMessageChange("rec-m1", cloudMessage("rec-m1", guid = "m1", chatId = "iMessage;-;friend@icloud.com", text = "first", time = 700_000_000_000_000_000L), blob = byteArrayOf()),
            UMessageChange("rec-m2", cloudMessage("rec-m2", guid = "m2", chatId = "cloud-dm", text = "second (newest)", time = 900_000_000_000_000_000L, flags = 4), blob = byteArrayOf()),
        )

        val summary = runSync()

        assertEquals(2uL, summary.totalChats)
        assertEquals(2uL, summary.totalMessages)
        assertNull(summary.error)

        val dm = chatByGuid("iMessage;-;friend@icloud.com")
        assertNotNull(dm)
        assertEquals("rec-chat-2", dm.ckRecordId)
        assertEquals("cloud-dm", dm.cloudGuid)
        assertEquals("friend@icloud.com", dm.chatIdentifier)
        assertEquals(true, dm.ckSyncState)
        assertTrue(dm.senderIsKnown)

        val family = chatByGuid("iMessage;+;family")
        assertNotNull(family)
        assertEquals("Family", family.displayName)
        assertEquals(43L, family.style)
        assertEquals(listOf("+15550000001", "me@icloud.com"), family.handles.map { it.address })

        val first = messageByGuid("m1")
        assertNotNull(first)
        assertEquals("first", first.text)
        assertEquals("rec-m1", first.ckRecordId)
        assertEquals(true, first.ckSyncState)
        assertFalse(first.isFromMe)
        assertNotNull(first.dateCreated)
        // Apple epoch (2001-01-01) + 700_000_000_000 ms (700e15 ns).
        assertEquals(978_307_200_000L + 700_000_000_000L, first.dateCreated.time)

        val second = messageByGuid("m2")
        assertNotNull(second)
        assertTrue(second.isFromMe) // flags bit 2 (4)
        assertTrue(second.handleRelation.target.address.isNotEmpty())

        // Latest-message wiring: newest message wins, and historical
        // backfill must NOT mark the chat unread.
        assertEquals(second.id, dm.dbLatestMessage.targetId)
        assertEquals(second.dateCreated, dm.dbOnlyLatestMessageDate)
        assertFalse(dm.hasUnreadMessage)
    }

    @Test
    fun `group version gate keeps local state when cloud is not newer`() {
        // Cloud knows version 5, local chat starts absent -> applies v5.
        port.chatPages += chatPage(UChatChange("rec-1", cloudChat("rec-1", groupVersion = 5u, displayName = "Cloud name"), blob = byteArrayOf()))
        port.messagePages += messagePage()
        runSync()
        val chat = chatByGuid("iMessage;-;+15551234567")!!
        assertEquals(5L, chat.groupVersion)
        assertEquals("Cloud name", chat.displayName)

        // Re-sync same version: identifiers refresh, content untouched.
        port.chatPages += chatPage(
            UChatChange("rec-1b", cloudChat("rec-1b", groupVersion = 5u, displayName = "Different name"), blob = byteArrayOf()),
        )
        port.messagePages += messagePage()
        runSync(SyncMode.INCREMENTAL)
        val refreshed = chatByGuid("iMessage;-;+15551234567")!!
        assertEquals("rec-1b", refreshed.ckRecordId) // always refreshed
        assertEquals("Cloud name", refreshed.displayName) // not clobbered
    }

    @Test
    fun `skips non-iMessage chats and chats that do not exist for messages`() {
        port.chatPages += chatPage(
            UChatChange("rec-sms", cloudChat("rec-sms", serviceName = "SMS"), blob = byteArrayOf()),
            UChatChange("rec-ok", cloudChat("rec-ok"), blob = byteArrayOf()),
        )
        port.messagePages += messagePage(
            UMessageChange("rec-orphan", cloudMessage("rec-orphan", guid = "orphan", chatId = "unknown-chat-id"), blob = byteArrayOf()),
        )
        runSync()

        assertNull(store.boxFor(Chat::class.java).query().equal(Chat_.ckRecordId, "rec-sms", QueryBuilder.StringOrder.CASE_SENSITIVE).build().use { it.findFirst() })
        assertNotNull(chatByGuid("iMessage;-;+15551234567"))
        assertNull(messageByGuid("orphan"))
    }

    // ------------------------------------------------------------------
    // Dedupe + tombstones
    // ------------------------------------------------------------------

    @Test
    fun `re-synced guid dedupes to one row and deletes the stale cloud record`() {
        port.chatPages += chatPage(UChatChange("rec-chat", cloudChat("rec-chat"), blob = byteArrayOf()))
        port.messagePages += messagePage(
            UMessageChange("rec-old", cloudMessage("rec-old", guid = "same-guid", chatId = "iMessage;-;+15551234567"), blob = byteArrayOf()),
        )
        runSync()
        assertEquals(1L, store.boxFor(Message::class.java).count())

        // Same message under a new record id: refresh in place, drop the
        // old record remotely, never duplicate.
        port.chatPages += chatPage()
        port.messagePages += messagePage(
            UMessageChange("rec-new", cloudMessage("rec-new", guid = "same-guid", chatId = "iMessage;-;+15551234567", text = "changed cloud text"), blob = byteArrayOf()),
        )
        val summary = runSync(SyncMode.INCREMENTAL)

        assertEquals(1L, store.boxFor(Message::class.java).count())
        assertEquals("rec-new", messageByGuid("same-guid")?.ckRecordId)
        assertEquals("hello from the cloud", messageByGuid("same-guid")?.text) // content untouched
        assertEquals(listOf(listOf("rec-old")), port.deletedMessages.filter { it.isNotEmpty() })
        assertEquals(1uL, summary.totalMessages)
    }

    @Test
    fun `tombstones delete chats with their messages and messages by record id`() {
        port.chatPages += chatPage(UChatChange("rec-chat", cloudChat("rec-chat"), blob = byteArrayOf()))
        port.messagePages += messagePage(
            UMessageChange("rec-m1", cloudMessage("rec-m1", guid = "m1", chatId = "iMessage;-;+15551234567"), blob = byteArrayOf()),
            UMessageChange("rec-m2", cloudMessage("rec-m2", guid = "m2", chatId = "iMessage;-;+15551234567"), blob = byteArrayOf()),
        )
        runSync()
        assertEquals(1L, store.boxFor(Chat::class.java).count())
        assertEquals(2L, store.boxFor(Message::class.java).count())

        port.chatPages += chatPage(UChatChange("rec-chat", null, blob = byteArrayOf()))
        port.messagePages += messagePage(UMessageChange("rec-m1", null, blob = byteArrayOf()))
        val summary = runSync(SyncMode.INCREMENTAL)

        assertEquals(0L, store.boxFor(Chat::class.java).count()) // chat cascade
        assertEquals(0L, store.boxFor(Message::class.java).count())
        assertEquals(1uL, summary.chatTombstones)
        assertEquals(1uL, summary.messageTombstones)
    }

    // ------------------------------------------------------------------
    // Cursors + incremental
    // ------------------------------------------------------------------

    @Test
    fun `incremental resumes from persisted cursors and full ignores them`() {
        port.chatPages += chatPage(UChatChange("rec-chat", cloudChat("rec-chat"), blob = byteArrayOf()), cursor = byteArrayOf(10), more = true)
        port.chatPages += chatPage(cursor = byteArrayOf(11))
        port.messagePages += messagePage(UMessageChange("rec-m1", cloudMessage("rec-m1", guid = "m1", chatId = "iMessage;-;+15551234567"), blob = byteArrayOf()), cursor = byteArrayOf(20), more = true)
        port.messagePages += messagePage(cursor = byteArrayOf(21))
        runSync()

        assertCursors(listOf<ByteArray?>(null, byteArrayOf(10)), port.chatCursorsReceived)
        assertCursors(listOf<ByteArray?>(null, byteArrayOf(20)), port.messageCursorsReceived)
        assertTrue(syncStore.chatCursor()!!.contentEquals(byteArrayOf(11)))
        assertTrue(syncStore.messageCursor()!!.contentEquals(byteArrayOf(21)))

        // Incremental run feeds the stored cursors back in.
        port.chatPages += chatPage(cursor = byteArrayOf(12))
        port.messagePages += messagePage(cursor = byteArrayOf(22))
        runSync(SyncMode.INCREMENTAL)
        assertCursors(listOf<ByteArray?>(null, byteArrayOf(10), byteArrayOf(11)), port.chatCursorsReceived)
        assertCursors(listOf<ByteArray?>(null, byteArrayOf(20), byteArrayOf(21)), port.messageCursorsReceived)

        // Full run starts fresh despite the persisted cursors.
        port.chatPages += chatPage(cursor = byteArrayOf(13))
        port.messagePages += messagePage(cursor = byteArrayOf(23))
        runSync(SyncMode.FULL)
        assertCursors(listOf<ByteArray?>(null, byteArrayOf(10), byteArrayOf(11), null), port.chatCursorsReceived)
        assertCursors(listOf<ByteArray?>(null, byteArrayOf(20), byteArrayOf(21), null), port.messageCursorsReceived)
    }

    @Test
    fun `cursors persist after each page so crashes replay only the last page`() {
        port.chatPages += chatPage(UChatChange("rec-chat", cloudChat("rec-chat"), blob = byteArrayOf()), cursor = byteArrayOf(1), more = true)
        port.chatPages += chatPage(cursor = byteArrayOf(2), more = true)
        port.chatPages += chatPage(cursor = byteArrayOf(3))
        port.messagePages += messagePage()
        runSync()
        assertEquals(3, port.chatCursorsReceived.size)
        assertTrue(syncStore.chatCursor()!!.contentEquals(byteArrayOf(3)))
    }

    // ------------------------------------------------------------------
    // Deletion flush, gating, cancellation, errors
    // ------------------------------------------------------------------

    @Test
    fun `pending local deletions flush before pulling`() {
        syncStore.savePendingChatDeletes(listOf("dead-chat"))
        syncStore.savePendingMessageDeletes(listOf("dead-msg"))
        port.chatPages += chatPage()
        port.messagePages += messagePage()

        runSync()

        val pullIndex = port.calls.indexOf("chats")
        assertTrue(pullIndex > 0, "expected pulls after flush")
        assertTrue(port.calls.indexOf("delete-messages") < pullIndex)
        assertTrue(port.calls.indexOf("delete-chats") < pullIndex)
        assertEquals(listOf(listOf("dead-msg")), port.deletedMessages)
        assertEquals(listOf(listOf("dead-chat")), port.deletedChats)
        assertTrue(syncStore.pendingChatDeletes().isEmpty())
        assertTrue(syncStore.pendingMessageDeletes().isEmpty())
    }

    @Test
    fun `gates on availability and clique membership`() {
        port.state = USyncState.NEEDS_LOGIN
        val needsLogin = runSync()
        assertNotNull(needsLogin.error)
        assertTrue(needsLogin.error!!.contains("login", ignoreCase = true))

        port.state = USyncState.NOT_ENABLED
        val notEnabled = runSync()
        assertTrue(notEnabled.error!!.contains("not enabled", ignoreCase = true))

        port.state = USyncState.AVAILABLE
        port.inClique = false
        val outOfClique = runSync()
        assertTrue(outOfClique.error!!.contains("clique", ignoreCase = true))
        // No pulls happened for any of the gated runs.
        assertTrue(port.calls.none { it == "chats" || it == "messages" })
    }

    @Test
    fun `cancel stops between pages and keeps applied pages`() {
        port.chatPages += chatPage(UChatChange("rec-1", cloudChat("rec-1"), blob = byteArrayOf()))
        port.chatPages += chatPage(UChatChange("rec-2", cloudChat("rec-2"), blob = byteArrayOf())) // never pulled
        port.messagePages += messagePage()
        // Cancel from inside the port mid-run (sync() clears a pre-set flag).
        var served = 0
        port.onChatsPageServed = {
            served += 1
            if (served == 1) manager.cancel()
        }

        val summary = runSync()

        assertTrue(summary.cancelled)
        assertEquals(1uL, summary.totalChats)
        assertEquals(0uL, summary.totalMessages)
        assertEquals(1, port.chatCursorsReceived.size)
        assertNotNull(chatByGuid("iMessage;-;+15551234567"))
    }

    @Test
    fun `port failures surface in the summary without throwing`() {
        port.failOn = "boom"
        port.chatPages += chatPage()
        port.messagePages += messagePage()
        val summary = runSync()
        assertEquals("boom", summary.error)
        assertEquals(SyncPhase.FAILED, manager.progress.value.phase)
    }
}

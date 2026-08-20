package app.openbubbles.core.sync

import app.openbubbles.core.attachment.AttachmentStore
import app.openbubbles.core.repo.ChatRepo
import app.openbubbles.db.Chat
import app.openbubbles.db.Chat_
import app.openbubbles.db.Handle
import app.openbubbles.db.Attachment
import app.openbubbles.db.Attachment_
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
import uniffi.rust_lib_bluebubbles.UAttachmentChange
import uniffi.rust_lib_bluebubbles.UAttachmentSyncPage
import uniffi.rust_lib_bluebubbles.UCloudAttachment
import uniffi.rust_lib_bluebubbles.UCloudChat
import uniffi.rust_lib_bluebubbles.UCloudMessage
import uniffi.rust_lib_bluebubbles.UMessageChange
import uniffi.rust_lib_bluebubbles.UMessageSyncPage
import uniffi.rust_lib_bluebubbles.USyncState
import uniffi.rust_lib_bluebubbles.UTranscriptBackground
import java.io.File
import java.util.Date
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
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
    val attachmentPages = ArrayDeque<UAttachmentSyncPage>()

    /** Cursors received, in order (null = fresh start). */
    val chatCursorsReceived = mutableListOf<ByteArray?>()
    val messageCursorsReceived = mutableListOf<ByteArray?>()
    val attachmentCursorsReceived = mutableListOf<ByteArray?>()

    /** Remote deletions in call order. */
    val deletedChats = mutableListOf<List<String>>()
    val deletedMessages = mutableListOf<List<String>>()
    val deletedAttachments = mutableListOf<List<String>>()

    /** Call log across all port methods, for ordering assertions. */
    val calls = mutableListOf<String>()

    var state: USyncState = USyncState.AVAILABLE
    var inClique: Boolean = true
    var failOn: String? = null
    var chatFailuresRemaining: Int = 0

    /** Invoked after each served chat page (index 1-based), for mid-run hooks. */
    var onChatsPageServed: ((Int) -> Unit)? = null
    var onMessagesPageServed: ((Int) -> Unit)? = null
    private var chatPagesServed = 0
    private var messagePagesServed = 0

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
        if (chatFailuresRemaining > 0) {
            chatFailuresRemaining -= 1
            throw IllegalStateException("temporary chat fetch failure")
        }
        failOn?.let { throw IllegalStateException(it) }
        val page = chatPages.removeFirst()
        chatPagesServed += 1
        onChatsPageServed?.invoke(chatPagesServed)
        return page
    }

    override suspend fun messagesPage(cursor: ByteArray?): UMessageSyncPage {
        calls += "messages"
        messageCursorsReceived += cursor
        val page = messagePages.removeFirst()
        messagePagesServed += 1
        onMessagesPageServed?.invoke(messagePagesServed)
        return page
    }

    override suspend fun attachmentsPage(cursor: ByteArray?): UAttachmentSyncPage {
        calls += "attachments"
        attachmentCursorsReceived += cursor
        return attachmentPages.removeFirstOrNull()
            ?: UAttachmentSyncPage(emptyList(), byteArrayOf(), false, 3)
    }

    override suspend fun deleteChatsRemote(recordIds: List<String>) {
        calls += "delete-chats"
        deletedChats += recordIds
    }

    override suspend fun deleteMessagesRemote(recordIds: List<String>) {
        calls += "delete-messages"
        deletedMessages += recordIds
    }

    override suspend fun deleteAttachmentsRemote(recordIds: List<String>) {
        calls += "delete-attachments"
        deletedAttachments += recordIds
    }

    val groupPhotoDownloads = mutableListOf<Pair<String, String>>()
    var groupPhotoBytes: ByteArray = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47)
    var failGroupPhoto: String? = null
    var transcriptBackgroundRecords = emptyList<UMessageChange>()
    var transcriptBackgroundsError: String? = null

    override suspend fun transcriptBackgrounds(): List<UMessageChange> {
        calls += "transcript-backgrounds"
        transcriptBackgroundsError?.let { throw IllegalStateException(it) }
        return transcriptBackgroundRecords
    }

    override suspend fun downloadGroupPhoto(recordId: String, path: String) {
        calls += "group-photo"
        groupPhotoDownloads += recordId to path
        failGroupPhoto?.let { throw IllegalStateException(it) }
        File(path).apply {
            parentFile?.mkdirs()
            writeBytes(groupPhotoBytes)
        }
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
    private val backgroundUpdates = mutableListOf<TranscriptBackgroundUpdate>()

    @Before
    fun setUp() {
        testDir = java.nio.file.Files.createTempDirectory("ob-cloudsync-test").toFile()
        store = MyObjectBox.builder().directory(testDir).build()
        port = FakeCloudSyncPort()
        syncStore = InMemoryCloudSyncStateStore()
        backgroundUpdates.clear()
        manager = CloudSyncManager(
            store,
            port,
            syncStore,
            AttachmentStore(store, testDir),
            TranscriptBackgroundHandler(backgroundUpdates::add),
            pageRetryDelaysMs = listOf(0L, 0L),
        )
    }

    @After
    fun tearDown() {
        if (!store.isClosed) store.close()
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
        hasGroupPhoto: Boolean = false,
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
        hasGroupPhoto = hasGroupPhoto,
    )

    private fun cloudMessage(
        recordId: String,
        guid: String = "msg-$recordId",
        chatId: String,
        text: String? = "hello from the cloud",
        sender: String = "tel:+15551234567",
        time: Long = 700_000_000_000_000_000L, // ~2023-03 in Apple ns
        flags: Long = 0,
        error: Long = 0,
        attachmentGuids: List<String> = emptyList(),
        balloonBundleId: String? = null,
        linkJson: String? = null,
        hasPayloadData: Boolean = false,
        msgType: Long = 0,
        transcriptBackground: UTranscriptBackground? = null,
    ) = UCloudMessage(
        guid = guid,
        chatId = chatId,
        sender = sender,
        time = time,
        msgType = msgType,
        error = error,
        service = "iMessage",
        flagsBits = flags,
        text = text,
        subject = null,
        hasAttachments = attachmentGuids.isNotEmpty(),
        attachmentGuids = attachmentGuids,
        balloonBundleId = balloonBundleId,
        linkJson = linkJson,
        hasPayloadData = hasPayloadData,
        summaryInfoJson = null,
        effect = null,
        dateReadNs = null,
        dateDeliveredNs = null,
        associatedMessageType = null,
        associatedMessageGuid = null,
        threadOriginatorGuid = null,
        threadOriginatorPart = null,
        associatedMessageEmoji = null,
        transcriptBackground = transcriptBackground,
    )

    private fun chatPage(vararg records: UChatChange, cursor: ByteArray = byteArrayOf(1), more: Boolean = false) =
        UChatSyncPage(records.toList(), cursor, more, if (more) 1 else 3)

    private fun messagePage(vararg records: UMessageChange, cursor: ByteArray = byteArrayOf(2), more: Boolean = false) =
        UMessageSyncPage(records.toList(), cursor, more, if (more) 1 else 3)

    private fun attachmentPage(
        vararg records: UAttachmentChange,
        cursor: ByteArray = byteArrayOf(3),
        more: Boolean = false,
    ) = UAttachmentSyncPage(records.toList(), cursor, more, if (more) 1 else 3)

    private fun chatByGuid(guid: String): Chat? =
        store.boxFor(Chat::class.java).query()
            .equal(Chat_.guid, guid, QueryBuilder.StringOrder.CASE_SENSITIVE)
            .build().use { it.findFirst() }

    private fun messageByGuid(guid: String): Message? =
        store.boxFor(Message::class.java).query()
            .equal(Message_.guid, guid, QueryBuilder.StringOrder.CASE_SENSITIVE)
            .build().use { it.findFirst() }

    private fun attachmentByGuid(guid: String): Attachment? =
        store.boxFor(Attachment::class.java).query()
            .equal(Attachment_.guid, guid, QueryBuilder.StringOrder.CASE_SENSITIVE)
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
        assertNull(second.error) // CloudKit eCode 0 means success, not failure.
        assertTrue(second.handleRelation.target.address.isNotEmpty())

        // Latest-message wiring: newest message wins, and historical
        // backfill must NOT mark the chat unread.
        assertEquals(second.id, dm.dbLatestMessage.targetId)
        assertEquals(second.dateCreated, dm.dbOnlyLatestMessageDate)
        assertFalse(dm.hasUnreadMessage)
    }

    @Test
    fun `latest ordering does not resolve the persisted message relation`() {
        val existingDate = Date(1_000L)
        val chat = Chat().apply {
            guid = "lazy-latest-chat"
            chatIdentifier = "lazy-latest-chat"
            dbOnlyLatestMessageDate = existingDate
        }
        store.boxFor(Chat::class.java).put(chat)
        val existing = Message().apply {
            guid = "large-latest-message"
            dateCreated = existingDate
            dbPayloadData = "x".repeat(1_000_000)
            this.chat.target = chat
        }
        store.boxFor(Message::class.java).put(existing)
        chat.dbLatestMessage.target = existing
        store.boxFor(Chat::class.java).put(chat)

        val persisted = requireNotNull(chatByGuid("lazy-latest-chat"))
        assertFalse(persisted.dbLatestMessage.isResolved)
        assertFalse(CloudSyncManager.shouldReplaceLatestMessage(persisted, Date(999L)))
        assertFalse(persisted.dbLatestMessage.isResolved)
        assertTrue(CloudSyncManager.shouldReplaceLatestMessage(persisted, Date(1_001L)))
        assertFalse(persisted.dbLatestMessage.isResolved)
    }

    @Test
    fun `cloud message retains nonzero send error`() {
        port.chatPages += chatPage(
            UChatChange("rec-chat", cloudChat("rec-chat"), blob = byteArrayOf()),
        )
        port.messagePages += messagePage(
            UMessageChange(
                "rec-failed",
                cloudMessage(
                    "rec-failed",
                    chatId = "iMessage;-;+15551234567",
                    flags = 4,
                    error = 12,
                ),
                blob = byteArrayOf(),
            ),
        )

        runSync()

        assertEquals(12L, messageByGuid("msg-rec-failed")?.error)
    }

    @Test
    fun `cloud URL balloon retains rich link metadata`() {
        val linkJson = """{"data":{"URL":{"NS.base":"${'$'}null","NS.relative":"https://example.com"},"title":"Example"},"attachments":[]}"""
        port.chatPages += chatPage(
            UChatChange("rec-chat", cloudChat("rec-chat"), blob = byteArrayOf()),
        )
        port.messagePages += messagePage(
            UMessageChange(
                "rec-link",
                cloudMessage(
                    "rec-link",
                    chatId = "iMessage;-;+15551234567",
                    text = "https://example.com",
                    balloonBundleId = "com.apple.messages.URLBalloonProvider",
                    linkJson = linkJson,
                    hasPayloadData = true,
                ),
                blob = byteArrayOf(),
            ),
        )

        runSync()

        val message = messageByGuid("msg-rec-link")
        assertNotNull(message)
        assertEquals(linkJson, message.dbMetadata)
        assertEquals("com.apple.messages.URLBalloonProvider", message.balloonBundleId)
        assertFalse(message.hasApplePayloadData)
    }

    @Test
    fun `cloud transcript background updates chat state without creating a message`() {
        port.chatPages += chatPage(
            UChatChange("rec-chat", cloudChat("rec-chat"), blob = byteArrayOf()),
        )
        port.messagePages += messagePage(
            UMessageChange(
                "rec-normal",
                cloudMessage(
                    "rec-normal",
                    guid = "normal-message",
                    chatId = "iMessage;-;+15551234567",
                ),
                blob = byteArrayOf(),
            ),
        )
        runSync()

        val chat = requireNotNull(chatByGuid("iMessage;-;+15551234567"))
        val normal = requireNotNull(messageByGuid("normal-message"))
        val legacy = Message().apply {
            guid = "background-message"
            ckRecordId = "legacy-background-record"
            dateCreated = Date(requireNotNull(normal.dateCreated).time + 1)
            this.chat.target = chat
        }
        store.boxFor(Message::class.java).put(legacy)
        chat.dbLatestMessage.target = legacy
        chat.dbOnlyLatestMessageDate = legacy.dateCreated
        store.boxFor(Chat::class.java).put(chat)

        port.chatPages += chatPage()
        port.messagePages += messagePage(
            UMessageChange(
                "rec-background",
                cloudMessage(
                    "rec-background",
                    guid = "background-message",
                    chatId = "iMessage;-;+15551234567",
                    text = null,
                    msgType = 138,
                    transcriptBackground = UTranscriptBackground(
                        version = 7uL,
                        chatId = "cloud-rec-chat",
                        remove = false,
                        mmcsXml = "<plist/>",
                    ),
                ),
                blob = byteArrayOf(),
            ),
        )

        val summary = runSync(SyncMode.INCREMENTAL)

        assertNull(summary.error)
        assertEquals(
            listOf(TranscriptBackgroundUpdate(chat.id, 7, remove = false, mmcsXml = "<plist/>")),
            backgroundUpdates,
        )
        assertNull(messageByGuid("background-message"))
        val refreshed = requireNotNull(chatByGuid("iMessage;-;+15551234567"))
        assertEquals(normal.id, refreshed.dbLatestMessage.targetId)
        assertEquals(normal.dateCreated, refreshed.dbOnlyLatestMessageDate)
    }

    @Test
    fun `history page applies only the newest representable wallpaper per chat`() {
        port.chatPages += chatPage(
            UChatChange("rec-chat", cloudChat("rec-chat"), blob = byteArrayOf()),
        )
        port.messagePages += messagePage(
            backgroundChange("background-6", 6uL, remove = false),
            backgroundChange("background-8", 8uL, remove = true),
            backgroundChange("background-7", 7uL, remove = false),
            backgroundChange("background-overflow", Long.MAX_VALUE.toULong() + 1uL, remove = false),
        )

        val summary = runSync()

        assertNull(summary.error)
        val chat = requireNotNull(chatByGuid("iMessage;-;+15551234567"))
        assertEquals(
            listOf(TranscriptBackgroundUpdate(chat.id, 8L, remove = true, mmcsXml = null)),
            backgroundUpdates,
        )
        assertEquals(0L, store.boxFor(Message::class.java).count())
    }

    private fun backgroundChange(guid: String, version: ULong, remove: Boolean) =
        UMessageChange(
            "record-$guid",
            cloudMessage(
                "record-$guid",
                guid = guid,
                chatId = "iMessage;-;+15551234567",
                text = null,
                msgType = 138,
                transcriptBackground = UTranscriptBackground(
                    version = version,
                    chatId = "rec-chat",
                    remove = remove,
                    mmcsXml = if (remove) null else "<plist/>",
                ),
            ),
            blob = byteArrayOf(),
        )

    @Test
    fun `malformed cloud transcript background is skipped without wedging the sync`() {
        syncStore.saveMessageCursor(byteArrayOf(9))
        port.chatPages += chatPage(
            UChatChange("rec-chat", cloudChat("rec-chat"), blob = byteArrayOf()),
        )
        port.messagePages += messagePage(
            UMessageChange(
                "rec-background",
                cloudMessage(
                    "rec-background",
                    guid = "background-message",
                    chatId = "iMessage;-;+15551234567",
                    text = null,
                    msgType = 138,
                    transcriptBackground = null,
                ),
                blob = byteArrayOf(),
            ),
            UMessageChange(
                "rec-after",
                cloudMessage("rec-after", chatId = "iMessage;-;+15551234567"),
                blob = byteArrayOf(),
            ),
            cursor = byteArrayOf(10),
        )

        val summary = runSync(SyncMode.INCREMENTAL)

        // An undecodable wallpaper record must not abort the run: aborting
        // leaves the cursor before this page forever, so no message after it
        // would ever sync again.
        assertNull(summary.error)
        assertTrue(syncStore.messageCursor()!!.contentEquals(byteArrayOf(10)))
        assertTrue(backgroundUpdates.isEmpty())
        assertNull(messageByGuid("background-message"))
        assertNotNull(messageByGuid("msg-rec-after"))
    }

    @Test
    fun `transcript background for an unknown chat is skipped without failing the run`() {
        port.chatPages += chatPage(
            UChatChange("rec-chat", cloudChat("rec-chat"), blob = byteArrayOf()),
        )
        port.messagePages += messagePage(
            UMessageChange(
                "rec-background",
                cloudMessage(
                    "rec-background",
                    guid = "background-message",
                    chatId = "iMessage;-;+19998887777",
                    text = null,
                    sender = "tel:+19998887777",
                    msgType = 138,
                    transcriptBackground = UTranscriptBackground(
                        version = 3uL,
                        chatId = "cloud-nonexistent",
                        remove = false,
                        mmcsXml = "<plist/>",
                    ),
                ),
                blob = byteArrayOf(),
            ),
        )

        val summary = runSync()

        assertNull(summary.error)
        assertTrue(backgroundUpdates.isEmpty())
    }

    @Test
    fun `transcript background chat id may be the bare peer address`() {
        port.chatPages += chatPage(
            UChatChange("rec-chat", cloudChat("rec-chat"), blob = byteArrayOf()),
        )
        port.messagePages += messagePage(
            UMessageChange(
                "rec-background",
                cloudMessage(
                    "rec-background",
                    guid = "background-message",
                    chatId = "unresolvable-cloud-chat-ref",
                    text = null,
                    msgType = 138,
                    transcriptBackground = UTranscriptBackground(
                        version = 5uL,
                        // Live-path cid form: the peer address, not a guid.
                        chatId = "+15551234567",
                        remove = false,
                        mmcsXml = "<plist/>",
                    ),
                ),
                blob = byteArrayOf(),
            ),
        )

        val summary = runSync()

        assertNull(summary.error)
        val chat = requireNotNull(chatByGuid("iMessage;-;+15551234567"))
        assertEquals(
            listOf(TranscriptBackgroundUpdate(chat.id, 5, remove = false, mmcsXml = "<plist/>")),
            backgroundUpdates,
        )
    }

    @Test
    fun `transcript background by bare address prefers the iMessage twin over an older SMS chat`() {
        // The SMS twin exists first (lower id, same identifier + address).
        val smsHandle = Handle().apply {
            address = "+15551234567"
            service = "SMS"
            uniqueAddressAndService = "+15551234567/SMS"
        }
        store.boxFor(Handle::class.java).put(smsHandle)
        val smsChat = Chat().apply {
            guid = "SMS;-;+15551234567"
            chatIdentifier = "+15551234567"
            isRpSms = true
            handles.add(smsHandle)
        }
        store.boxFor(Chat::class.java).put(smsChat)

        port.chatPages += chatPage(
            UChatChange("rec-chat", cloudChat("rec-chat"), blob = byteArrayOf()),
        )
        port.messagePages += messagePage(
            UMessageChange(
                "rec-background",
                cloudMessage(
                    "rec-background",
                    guid = "background-message",
                    chatId = "unresolvable-cloud-chat-ref",
                    text = null,
                    msgType = 138,
                    transcriptBackground = UTranscriptBackground(
                        version = 6uL,
                        chatId = "+15551234567",
                        remove = false,
                        mmcsXml = "<plist/>",
                    ),
                ),
                blob = byteArrayOf(),
            ),
        )

        val summary = runSync()

        assertNull(summary.error)
        val imessageChat = requireNotNull(chatByGuid("iMessage;-;+15551234567"))
        assertEquals(
            listOf(TranscriptBackgroundUpdate(imessageChat.id, 6, remove = false, mmcsXml = "<plist/>")),
            backgroundUpdates,
        )
    }

    @Test
    fun `full-guid cloud message routes to its own service's chat despite an identifier twin`() {
        // Older SMS twin shares the identifier with the synced iMessage chat.
        val smsHandle = Handle().apply {
            address = "+15551234567"
            service = "SMS"
            uniqueAddressAndService = "+15551234567/SMS"
        }
        store.boxFor(Handle::class.java).put(smsHandle)
        val smsChat = Chat().apply {
            guid = "SMS;-;+15551234567"
            chatIdentifier = "+15551234567"
            isRpSms = true
            handles.add(smsHandle)
        }
        store.boxFor(Chat::class.java).put(smsChat)

        port.chatPages += chatPage(
            UChatChange("rec-chat", cloudChat("rec-chat"), blob = byteArrayOf()),
        )
        port.messagePages += messagePage(
            UMessageChange(
                "rec-msg",
                cloudMessage("rec-msg", chatId = "iMessage;-;+15551234567"),
                blob = byteArrayOf(),
            ),
        )

        val summary = runSync()

        assertNull(summary.error)
        val imessageChat = requireNotNull(chatByGuid("iMessage;-;+15551234567"))
        val row = requireNotNull(messageByGuid("msg-rec-msg"))
        assertEquals(imessageChat.id, row.chat.targetId)
    }

    @Test
    fun `queried transcript backgrounds apply even after the incremental cursor passed them`() {
        port.chatPages += chatPage(
            UChatChange("rec-chat", cloudChat("rec-chat"), blob = byteArrayOf()),
        )
        port.messagePages += messagePage()
        runSync()

        val chat = requireNotNull(chatByGuid("iMessage;-;+15551234567"))
        syncStore.saveWallpaperBackfillDone(false)
        port.chatPages += chatPage()
        port.messagePages += messagePage()
        port.transcriptBackgroundRecords = listOf(
            UMessageChange(
                "rec-background-query",
                cloudMessage(
                    "rec-background-query",
                    guid = "background-query",
                    chatId = "iMessage;-;+15551234567",
                    text = null,
                    msgType = 138,
                    transcriptBackground = UTranscriptBackground(
                        version = 11uL,
                        chatId = "+15551234567",
                        remove = false,
                        mmcsXml = "<plist/>",
                    ),
                ),
                blob = byteArrayOf(),
            ),
        )

        val summary = runSync(SyncMode.INCREMENTAL)

        assertNull(summary.error)
        assertEquals(
            listOf(TranscriptBackgroundUpdate(chat.id, 11, remove = false, mmcsXml = "<plist/>")),
            backgroundUpdates,
        )
        assertTrue(port.calls.contains("transcript-backgrounds"))
        assertTrue(syncStore.wallpaperBackfillDone())
    }

    @Test
    fun `completed wallpaper backfill skips the direct query`() {
        port.chatPages += chatPage(
            UChatChange("rec-chat", cloudChat("rec-chat"), blob = byteArrayOf()),
        )
        port.messagePages += messagePage(cursor = byteArrayOf(20))
        runSync()

        port.calls.clear()
        port.chatPages += chatPage()
        port.messagePages += messagePage(cursor = byteArrayOf(21))

        val summary = runSync(SyncMode.INCREMENTAL)

        assertNull(summary.error)
        assertFalse(port.calls.contains("transcript-backgrounds"))
        assertTrue(syncStore.wallpaperBackfillDone())
    }

    @Test
    fun `empty wallpaper query preserves the incremental message cursor`() {
        port.chatPages += chatPage(
            UChatChange("rec-chat", cloudChat("rec-chat"), blob = byteArrayOf()),
        )
        port.messagePages += messagePage(cursor = byteArrayOf(20))
        runSync()
        assertTrue(syncStore.messageCursor()!!.contentEquals(byteArrayOf(20)))

        syncStore.saveWallpaperBackfillDone(false)
        port.chatPages += chatPage()
        port.messagePages += messagePage(cursor = byteArrayOf(21))

        val summary = runSync(SyncMode.INCREMENTAL)

        assertNull(summary.error)
        assertEquals(2, port.messageCursorsReceived.size)
        assertTrue(port.messageCursorsReceived[1]!!.contentEquals(byteArrayOf(20)))
        assertTrue(backgroundUpdates.isEmpty())
        assertTrue(syncStore.wallpaperBackfillDone())
    }

    @Test
    fun `failed wallpaper query preserves the cursor and retries on the next run`() {
        port.chatPages += chatPage(
            UChatChange("rec-chat", cloudChat("rec-chat"), blob = byteArrayOf()),
        )
        port.messagePages += messagePage(cursor = byteArrayOf(20))
        runSync()

        syncStore.saveWallpaperBackfillDone(false)
        port.transcriptBackgroundsError = "temporary query failure"
        port.chatPages += chatPage()
        port.messagePages += messagePage(cursor = byteArrayOf(21))

        val summary = runSync(SyncMode.INCREMENTAL)

        assertNull(summary.error)
        assertEquals(2, port.messageCursorsReceived.size)
        assertTrue(port.messageCursorsReceived[1]!!.contentEquals(byteArrayOf(20)))
        assertFalse(syncStore.wallpaperBackfillDone())

        port.transcriptBackgroundsError = null
        port.chatPages += chatPage()
        port.messagePages += messagePage(cursor = byteArrayOf(22))

        val retried = runSync(SyncMode.INCREMENTAL)

        assertNull(retried.error)
        assertEquals(2, port.calls.count { it == "transcript-backgrounds" })
        assertTrue(syncStore.wallpaperBackfillDone())
    }

    @Test
    fun `failed transcript background application does not abort the sync`() {
        manager = CloudSyncManager(
            store,
            port,
            syncStore,
            AttachmentStore(store, testDir),
            TranscriptBackgroundHandler { update ->
                if (update.version == 6L) error("mmcs payload expired")
                backgroundUpdates += update
            },
            pageRetryDelaysMs = listOf(0L, 0L),
        )
        port.chatPages += chatPage(
            UChatChange("rec-chat", cloudChat("rec-chat"), blob = byteArrayOf()),
        )
        port.messagePages += messagePage(
            UMessageChange(
                "rec-background-broken",
                cloudMessage(
                    "rec-background-broken",
                    guid = "background-broken",
                    chatId = "iMessage;-;+15551234567",
                    text = null,
                    msgType = 138,
                    transcriptBackground = UTranscriptBackground(
                        version = 6uL,
                        chatId = null,
                        remove = false,
                        mmcsXml = "<expired/>",
                    ),
                ),
                blob = byteArrayOf(),
            ),
            UMessageChange(
                "rec-background-ok",
                cloudMessage(
                    "rec-background-ok",
                    guid = "background-ok",
                    chatId = "iMessage;-;+15551234567",
                    text = null,
                    msgType = 138,
                    transcriptBackground = UTranscriptBackground(
                        version = 7uL,
                        chatId = null,
                        remove = true,
                        mmcsXml = null,
                    ),
                ),
                blob = byteArrayOf(),
            ),
        )

        val summary = runSync()

        assertNull(summary.error)
        val chat = requireNotNull(chatByGuid("iMessage;-;+15551234567"))
        assertEquals(
            listOf(TranscriptBackgroundUpdate(chat.id, 7, remove = true, mmcsXml = null)),
            backgroundUpdates,
        )
    }

    @Test
    fun `cloud-imported chats can be pinned and keep the pin across resyncs`() {
        port.chatPages += chatPage(
            UChatChange("rec-chat", cloudChat("rec-chat"), blob = byteArrayOf()),
        )
        port.messagePages += messagePage()
        runSync()

        val chat = requireNotNull(chatByGuid("iMessage;-;+15551234567"))
        ChatRepo(store).setPinned(chat.id, true)

        // A newer cloud group version reapplies the full cloud chat state;
        // the local pin is device-only state and must survive it.
        port.chatPages += chatPage(
            UChatChange("rec-chat", cloudChat("rec-chat", groupVersion = 9u), blob = byteArrayOf()),
        )
        port.messagePages += messagePage()
        val summary = runSync(SyncMode.INCREMENTAL)

        assertNull(summary.error)
        val item = ChatRepo(store).chats().single()
        assertTrue(item.pinned)
        assertTrue(requireNotNull(chatByGuid("iMessage;-;+15551234567")).isPinned)
    }

    @Test
    fun `full sync links cloud attachments and applies attachment tombstones`() {
        port.chatPages += chatPage(UChatChange("rec-chat", cloudChat("rec-chat"), blob = byteArrayOf()))
        port.messagePages += messagePage(
            UMessageChange(
                "rec-message",
                cloudMessage(
                    "rec-message",
                    guid = "message_with_underscore",
                    chatId = "iMessage;-;+15551234567",
                    attachmentGuids = listOf("message_with_underscore_0"),
                ),
                blob = byteArrayOf(),
            ),
        )
        port.attachmentPages += attachmentPage(
            UAttachmentChange(
                "rec-attachment",
                UCloudAttachment(
                    guid = "message_with_underscore_0",
                    messageGuid = "message_with_underscore",
                    uti = "public.jpeg",
                    mimeType = "image/jpeg",
                    isOutgoing = false,
                    transferName = "photo.jpg",
                    totalBytes = 42,
                ),
            ),
        )

        val summary = runSync()

        assertEquals(1uL, summary.totalAttachments)
        val attachment = attachmentByGuid("message_with_underscore_0")
        assertNotNull(attachment)
        assertEquals("rec-attachment", attachment.ckRecordId)
        assertEquals("photo.jpg", attachment.transferName)
        assertEquals("rec-attachment", attachment.metadata["cloud"])
        assertEquals("message_with_underscore", attachment.message.target?.guid)
        val payload = AttachmentStore(store, testDir).pathFor(attachment).apply {
            parentFile?.mkdirs()
            writeText("payload")
        }

        port.chatPages += chatPage()
        port.messagePages += messagePage()
        port.attachmentPages += attachmentPage(UAttachmentChange("rec-attachment", null))

        val tombstoneSummary = runSync(SyncMode.INCREMENTAL)

        assertEquals(1uL, tombstoneSummary.attachmentTombstones)
        assertNull(attachmentByGuid("message_with_underscore_0"))
        assertTrue(!payload.exists())
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

    @Test
    fun `next page fetch overlaps current page application`() {
        val secondPageFetched = CountDownLatch(1)
        port.onMessagesPageServed = { page ->
            if (page == 2) secondPageFetched.countDown()
        }
        manager = CloudSyncManager(
            store,
            port,
            syncStore,
            AttachmentStore(store, testDir),
            TranscriptBackgroundHandler { update ->
                assertTrue(
                    secondPageFetched.await(2, TimeUnit.SECONDS),
                    "expected the next CloudKit page while applying the current page",
                )
                backgroundUpdates += update
            },
            pageRetryDelaysMs = listOf(0L, 0L),
        )
        port.chatPages += chatPage(
            UChatChange("rec-chat", cloudChat("rec-chat"), blob = byteArrayOf()),
        )
        port.messagePages += messagePage(
            UMessageChange(
                "rec-background",
                cloudMessage(
                    "rec-background",
                    guid = "background-message",
                    chatId = "iMessage;-;+15551234567",
                    text = null,
                    msgType = 138,
                    transcriptBackground = UTranscriptBackground(
                        version = 1uL,
                        chatId = "cloud-rec-chat",
                        remove = true,
                        mmcsXml = null,
                    ),
                ),
                blob = byteArrayOf(),
            ),
            cursor = byteArrayOf(20),
            more = true,
        )
        port.messagePages += messagePage(cursor = byteArrayOf(21))

        val summary = runSync()

        assertNull(summary.error)
        assertEquals(2, port.messageCursorsReceived.size)
        assertEquals(1, backgroundUpdates.size)
        assertTrue(syncStore.messageCursor()!!.contentEquals(byteArrayOf(21)))
    }

    @Test
    fun `failed page application does not advance its cursor`() {
        syncStore.saveChatCursor(byteArrayOf(9))
        port.chatPages += chatPage(
            UChatChange("rec-chat", cloudChat("rec-chat"), blob = byteArrayOf()),
            cursor = byteArrayOf(10),
        )
        store.close()

        val summary = runSync(SyncMode.INCREMENTAL)

        assertNotNull(summary.error)
        assertTrue(syncStore.chatCursor()!!.contentEquals(byteArrayOf(9)))
    }

    @Test
    fun `transient page failures retry without losing progress`() {
        port.chatFailuresRemaining = 2
        port.chatPages += chatPage(cursor = byteArrayOf(10))
        port.messagePages += messagePage(cursor = byteArrayOf(20))

        val summary = runSync()

        assertNull(summary.error)
        assertEquals(3, port.calls.count { it == "chats" })
        assertTrue(syncStore.chatCursor()!!.contentEquals(byteArrayOf(10)))
        assertTrue(syncStore.messageCursor()!!.contentEquals(byteArrayOf(20)))
    }

    @Test
    fun `non advancing page cursor fails instead of stalling`() {
        repeat(3) {
            port.chatPages += chatPage(cursor = byteArrayOf(), more = true)
        }

        val summary = runSync()

        assertTrue(summary.error?.contains("no continuation cursor") == true)
        assertNull(syncStore.chatCursor())
        assertEquals(3, port.calls.count { it == "chats" })
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
        assertTrue(needsLogin.error.contains("login", ignoreCase = true))

        port.state = USyncState.NOT_ENABLED
        val notEnabled = runSync()
        assertTrue(notEnabled.error.orEmpty().contains("not enabled", ignoreCase = true))

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

    // ------------------------------------------------------------------
    // Group photos
    // ------------------------------------------------------------------

    @Test
    fun `cloud group photo is downloaded and persisted on import`() {
        port.chatPages += chatPage(
            UChatChange(
                "rec-family",
                cloudChat(
                    "rec-family",
                    guid = "iMessage;+;family",
                    identifier = "family",
                    groupId = "cloud-family",
                    style = 43,
                    displayName = "Family",
                    participants = listOf("tel:+15550000001", "mailto:me@icloud.com"),
                    hasGroupPhoto = true,
                ),
                blob = byteArrayOf(),
            ),
        )
        port.messagePages += messagePage()

        val summary = runSync()
        assertNull(summary.error)

        val family = requireNotNull(chatByGuid("iMessage;+;family"))
        assertEquals(listOf("rec-family" to family.customAvatarPath), port.groupPhotoDownloads)
        assertEquals("ck:rec-family:1", family.photoAttachmentGuid)
        assertNotNull(family.customAvatarPath)
        assertTrue(File(family.customAvatarPath).isFile)
        assertTrue(File(family.customAvatarPath).readBytes().contentEquals(port.groupPhotoBytes))
    }

    @Test
    fun `existing cloud group photo is not redownloaded`() {
        port.chatPages += chatPage(
            UChatChange(
                "rec-family",
                cloudChat(
                    "rec-family",
                    guid = "iMessage;+;family",
                    identifier = "family",
                    groupId = "cloud-family",
                    style = 43,
                    hasGroupPhoto = true,
                ),
                blob = byteArrayOf(),
            ),
        )
        port.messagePages += messagePage()
        runSync()
        assertEquals(1, port.groupPhotoDownloads.size)

        port.chatPages += chatPage(
            UChatChange(
                "rec-family",
                cloudChat(
                    "rec-family",
                    guid = "iMessage;+;family",
                    identifier = "family",
                    groupId = "cloud-family",
                    style = 43,
                    hasGroupPhoto = true,
                ),
                blob = byteArrayOf(),
            ),
        )
        port.messagePages += messagePage()
        runSync(SyncMode.INCREMENTAL)
        assertEquals(1, port.groupPhotoDownloads.size)
    }

    @Test
    fun `newer cloud version without a photo clears a cloud-sourced icon`() {
        port.chatPages += chatPage(
            UChatChange(
                "rec-family",
                cloudChat(
                    "rec-family",
                    guid = "iMessage;+;family",
                    identifier = "family",
                    groupId = "cloud-family",
                    style = 43,
                    groupVersion = 1u,
                    hasGroupPhoto = true,
                ),
                blob = byteArrayOf(),
            ),
        )
        port.messagePages += messagePage()
        runSync()
        val path = requireNotNull(chatByGuid("iMessage;+;family")?.customAvatarPath)
        assertTrue(File(path).isFile)

        port.chatPages += chatPage(
            UChatChange(
                "rec-family",
                cloudChat(
                    "rec-family",
                    guid = "iMessage;+;family",
                    identifier = "family",
                    groupId = "cloud-family",
                    style = 43,
                    groupVersion = 2u,
                    hasGroupPhoto = false,
                ),
                blob = byteArrayOf(),
            ),
        )
        port.messagePages += messagePage()
        runSync(SyncMode.INCREMENTAL)

        val family = requireNotNull(chatByGuid("iMessage;+;family"))
        assertNull(family.customAvatarPath)
        assertNull(family.photoAttachmentGuid)
        assertFalse(File(path).exists())
    }

    @Test
    fun `live group photo is not overwritten by an older cloud asset`() {
        val live = File(testDir, "live-icon.png").apply {
            writeBytes(byteArrayOf(7, 7, 7))
        }
        val chat = Chat().apply {
            guid = "iMessage;+;family"
            chatIdentifier = "family"
            style = 43
            groupVersion = 4
            customAvatarPath = live.absolutePath
            photoAttachmentGuid = "live-icon-guid"
        }
        store.boxFor(Chat::class.java).put(chat)

        port.chatPages += chatPage(
            UChatChange(
                "rec-family",
                cloudChat(
                    "rec-family",
                    guid = "iMessage;+;family",
                    identifier = "family",
                    groupId = "cloud-family",
                    style = 43,
                    groupVersion = 1u,
                    hasGroupPhoto = true,
                ),
                blob = byteArrayOf(),
            ),
        )
        port.messagePages += messagePage()
        runSync()

        val family = requireNotNull(chatByGuid("iMessage;+;family"))
        assertTrue(port.groupPhotoDownloads.isEmpty())
        assertEquals(live.absolutePath, family.customAvatarPath)
        assertEquals("live-icon-guid", family.photoAttachmentGuid)
    }

    @Test
    fun `group photo download failure does not fail the sync`() {
        port.failGroupPhoto = "mmcs unavailable"
        port.chatPages += chatPage(
            UChatChange(
                "rec-family",
                cloudChat(
                    "rec-family",
                    guid = "iMessage;+;family",
                    identifier = "family",
                    groupId = "cloud-family",
                    style = 43,
                    hasGroupPhoto = true,
                ),
                blob = byteArrayOf(),
            ),
        )
        port.messagePages += messagePage()

        val summary = runSync()
        assertNull(summary.error)
        val family = requireNotNull(chatByGuid("iMessage;+;family"))
        assertNull(family.customAvatarPath)
        assertNull(family.photoAttachmentGuid)
    }

    @Test
    fun `group photo policy prefers missing files and newer cloud versions`() {
        assertTrue(
            CloudSyncManager.shouldDownloadGroupPhoto(
                lockChatIcon = false,
                customAvatarPath = null,
                photoAttachmentGuid = null,
                recordId = "rec",
                version = 1,
                fileExists = false,
            ),
        )
        assertFalse(
            CloudSyncManager.shouldDownloadGroupPhoto(
                lockChatIcon = true,
                customAvatarPath = null,
                photoAttachmentGuid = null,
                recordId = "rec",
                version = 1,
                fileExists = false,
            ),
        )
        assertFalse(
            CloudSyncManager.shouldDownloadGroupPhoto(
                lockChatIcon = false,
                customAvatarPath = "/icons/live.png",
                photoAttachmentGuid = "live-guid",
                recordId = "rec",
                version = 2,
                fileExists = true,
            ),
        )
        assertTrue(
            CloudSyncManager.shouldDownloadGroupPhoto(
                lockChatIcon = false,
                customAvatarPath = "/icons/old.png",
                photoAttachmentGuid = "ck:rec:1",
                recordId = "rec",
                version = 2,
                fileExists = true,
            ),
        )
        assertTrue(
            CloudSyncManager.shouldClearCloudGroupPhoto("ck:rec:1", "rec", 2),
        )
        assertFalse(
            CloudSyncManager.shouldClearCloudGroupPhoto("live-guid", "rec", 2),
        )
        assertFalse(
            CloudSyncManager.shouldClearCloudGroupPhoto("ck:rec:2", "rec", 2),
        )
    }
}

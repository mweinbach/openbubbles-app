package app.openbubbles.nativeapp.ui.chat

import app.openbubbles.nativeapp.data.AttachmentSender
import app.openbubbles.nativeapp.data.ChatListItem
import app.openbubbles.nativeapp.data.ChatListRepository
import app.openbubbles.nativeapp.data.FaceTimeCaller
import app.openbubbles.nativeapp.data.FaceTimeLaunch
import app.openbubbles.nativeapp.data.MessageActions
import app.openbubbles.nativeapp.data.MessageItem
import app.openbubbles.nativeapp.data.MessageListRepository
import app.openbubbles.nativeapp.data.MessageStatus
import app.openbubbles.nativeapp.data.OutgoingAttachment
import app.openbubbles.nativeapp.data.ReadReceiptSender
import app.openbubbles.nativeapp.data.Sender
import app.openbubbles.nativeapp.data.StickerSender
import app.openbubbles.nativeapp.data.StickerTransform
import app.openbubbles.nativeapp.data.TypingEntry
import app.openbubbles.nativeapp.data.TypingRepository
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain

@OptIn(ExperimentalCoroutinesApi::class)
class ChatViewModelTest {
    private val dispatcher = StandardTestDispatcher()

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `reply targets the thread root`() = runTest(dispatcher) {
        val sender = RecordingSender()
        val actions = RecordingActions()
        val model = model(sender, actions)
        val reply = message(
            guid = "child",
            replyToGuid = "root",
            replyToPart = 4L,
            replyToPartLocator = "4:7:3",
        )

        model.beginReply(reply)
        model.onInputChange("thread reply")
        model.sendMessage()
        advanceUntilIdle()

        assertEquals(Triple(7L, "thread reply", "root"), sender.reply)
        assertEquals("4:7:3", sender.replyPartLocator)
    }

    @Test
    fun `grouped contact sends new messages through its most recent address`() = runTest(dispatcher) {
        val sender = RecordingSender()
        val model = model(
            sender = sender,
            actions = RecordingActions(),
            chatListRepository = GroupedChats,
        )
        backgroundScope.launch(dispatcher) { model.uiState.collect() }
        advanceUntilIdle()

        model.onInputChange("latest route")
        model.sendMessage()
        advanceUntilIdle()

        assertEquals(9L to "latest route", sender.sent)
    }

    @Test
    fun `grouped contact actions retain the message source address`() = runTest(dispatcher) {
        val sender = RecordingSender()
        val actions = RecordingActions()
        val model = model(sender, actions, chatListRepository = GroupedChats)
        val target = message(guid = "alternate", text = "hello", chatId = 9L)

        model.beginReply(target)
        model.onInputChange("reply")
        model.sendMessage()
        model.react(target, 0L, 1)
        model.unsend(target)
        advanceUntilIdle()

        assertEquals(9L, sender.reply?.first)
        assertEquals(9L, actions.reactionChatId)
        assertEquals(9L to "alternate", actions.unsend)
    }

    @Test
    fun `reply to a root message sends its full selected run locator`() = runTest(dispatcher) {
        val sender = RecordingSender()
        val model = model(sender, RecordingActions())

        model.beginReply(
            message(guid = "root", replyPartLocators = mapOf(3L to "3:7:5")),
            part = 3L,
        )
        model.onInputChange("part reply")
        model.sendMessage()
        advanceUntilIdle()

        assertEquals(Triple(7L, "part reply", "root"), sender.reply)
        assertEquals("3:7:5", sender.replyPartLocator)
    }

    @Test
    fun `legacy plain text reply gets a utf16 whole-run locator`() = runTest(dispatcher) {
        val sender = RecordingSender()
        val model = model(sender, RecordingActions())

        model.beginReply(message(guid = "root", text = "Hi 👋"))
        model.onInputChange("reply")
        model.sendMessage()
        advanceUntilIdle()

        assertEquals("0:0:5", sender.replyPartLocator)
    }

    @Test
    fun `sticker placement reaches the native sticker sender unchanged`() = runTest(dispatcher) {
        val stickerSender = RecordingStickerSender()
        val model = model(RecordingSender(), RecordingActions(), stickerSender = stickerSender)
        val file = File.createTempFile("sticker", ".png").apply { writeBytes(byteArrayOf(1, 2, 3)) }
        val attachment = OutgoingAttachment(file, "image/png", "public.png", "sticker.png", file.length())
        val transform = StickerTransform(320.0, 0.25, 0.75, 0.5, 1.4, effectType = 2L)

        model.sendSticker(message(guid = "target", text = "decorate"), 2L, attachment, transform)
        advanceUntilIdle()

        assertEquals(7L, stickerSender.chatId)
        assertEquals("target", stickerSender.targetGuid)
        assertEquals(2L, stickerSender.targetPart)
        assertEquals("decorate", stickerSender.targetText)
        assertEquals(transform, stickerSender.transform)
        file.delete()
    }

    @Test
    fun `edit reaction and unsend reach native action layer`() = runTest(dispatcher) {
        val actions = RecordingActions()
        val model = model(RecordingSender(), actions)
        val target = message(guid = "target", text = "before")

        model.beginEdit(target)
        model.onInputChange("after")
        model.sendMessage()
        model.react(target, 2L, 3)
        model.unsend(target)
        advanceUntilIdle()

        assertEquals(Triple(7L, "target", "after"), actions.edit)
        assertEquals(Triple("target", 3, "before"), actions.reaction)
        assertEquals(2L, actions.reactionPart)
        assertEquals(7L to "target", actions.unsend)
    }

    @Test
    fun `attachment in sms chat never enters imessage uploader`() = runTest(dispatcher) {
        val attachmentSender = RecordingAttachmentSender()
        var routed: Triple<Long, String, String?>? = null
        val model = model(
            sender = RecordingSender(),
            actions = RecordingActions(),
            attachmentSender = attachmentSender,
            smsAttachmentRouter = { chatId, attachment, caption ->
                routed = Triple(chatId, attachment.mime, caption)
                true
            },
        )
        val file = File.createTempFile("mms-route", ".jpg").apply { writeBytes(byteArrayOf(1, 2, 3)) }
        val attachment = OutgoingAttachment(file, "image/jpeg", "public.jpeg", "photo.jpg", file.length())

        model.onInputChange("caption")
        model.sendAttachment(attachment)
        advanceUntilIdle()

        assertEquals(Triple(7L, "image/jpeg", "caption"), routed)
        assertEquals(0, attachmentSender.calls)
        file.delete()
    }

    @Test
    fun `facetime launch is exposed once and can be consumed`() = runTest(dispatcher) {
        val launch = FaceTimeLaunch("https://call.example", "me@example.com", "Test", "CALL-ID")
        val model = model(
            RecordingSender(),
            RecordingActions(),
            faceTimeCaller = FaceTimeCaller { launch },
        )
        backgroundScope.launch(dispatcher) { model.uiState.collect() }

        model.startFaceTime()
        advanceUntilIdle()

        assertEquals(launch, model.uiState.value.faceTimeLaunch)
        assertEquals(false, model.uiState.value.faceTimeStarting)
        model.consumeFaceTimeLaunch()
        advanceUntilIdle()
        assertEquals(null, model.uiState.value.faceTimeLaunch)
    }

    @Test
    fun `historical effects are baselined and new effects are consumed once`() = runTest(dispatcher) {
        val messages = MutableMessages(
            listOf(message(id = 1L, guid = "historical", effectId = "historical-effect")),
        )
        val model = model(
            RecordingSender(),
            RecordingActions(),
            messageRepository = messages,
        )
        backgroundScope.launch(dispatcher) { model.uiState.collect() }

        advanceUntilIdle()
        assertEquals(null, model.uiState.value.screenEffect)

        messages.value.value += message(id = 2L, guid = "new", effectId = "new-effect")
        advanceUntilIdle()

        assertEquals(ScreenEffectTrigger(2L, "new-effect"), model.uiState.value.screenEffect)
        model.consumeScreenEffect(2L)
        advanceUntilIdle()
        assertEquals(null, model.uiState.value.screenEffect)
    }

    @Test
    fun `slower reply lookup cannot overwrite a newer thread`() = runTest(dispatcher) {
        val messages = BlockingThreadMessages()
        val model = model(
            RecordingSender(),
            RecordingActions(),
            messageRepository = messages,
        )
        backgroundScope.launch(dispatcher) { model.uiState.collect() }
        advanceUntilIdle()

        model.openReplyThread(message(guid = "first"))
        assertTrue(messages.firstStarted.await(5, TimeUnit.SECONDS))

        model.openReplyThread(message(guid = "second"))
        assertTrue(messages.secondStarted.await(5, TimeUnit.SECONDS))
        messages.secondRelease.countDown()

        var secondLoaded = false
        for (attempt in 0 until 100) {
            runCurrent()
            val thread = model.uiState.value.replyThread
            if (thread?.rootGuid == "second" && !thread.loading) {
                secondLoaded = true
                break
            }
            Thread.sleep(10)
        }
        assertTrue(secondLoaded)

        messages.firstRelease.countDown()
        assertTrue(messages.firstReturned.await(5, TimeUnit.SECONDS))
        repeat(10) {
            runCurrent()
            Thread.sleep(5)
        }

        assertEquals("second", model.uiState.value.replyThread?.rootGuid)
        assertEquals(false, model.uiState.value.replyThread?.loading)
    }

    private fun model(
        sender: Sender,
        actions: MessageActions,
        chatListRepository: ChatListRepository = StaticChats,
        messageRepository: MessageListRepository = StaticMessages,
        attachmentSender: AttachmentSender = NoopAttachmentSender,
        stickerSender: StickerSender = StickerSender { _, _, _, _, _, _ -> },
        faceTimeCaller: FaceTimeCaller = FaceTimeCaller { error("not used") },
        smsAttachmentRouter: suspend (Long, OutgoingAttachment, String?) -> Boolean = { _, _, _ -> false },
        readReceiptSender: ReadReceiptSender = ReadReceiptSender { _, _ -> },
    ) = ChatViewModel(
        chatId = 7L,
        chatListRepository = chatListRepository,
        messageRepository = messageRepository,
        sender = sender,
        messageActions = actions,
        faceTimeCaller = faceTimeCaller,
        attachmentSender = attachmentSender,
        stickerSender = stickerSender,
        typingRepository = NoopTyping,
        readReceiptSender = readReceiptSender,
        smsRouter = { _, _ -> false },
        smsAttachmentRouter = smsAttachmentRouter,
    )

    private fun message(
        id: Long = 1L,
        guid: String,
        text: String = "hello",
        effectId: String? = null,
        replyToGuid: String? = null,
        replyToPart: Long? = null,
        replyToPartLocator: String? = null,
        replyPartLocators: Map<Long, String> = emptyMap(),
        chatId: Long? = null,
    ) = MessageItem(
        id = id,
        text = text,
        isFromMe = true,
        date = 1L,
        status = MessageStatus.SENT,
        isGroupEvent = false,
        reactionEmoji = null,
        expressiveSendStyleId = effectId,
        guid = guid,
        replyToGuid = replyToGuid,
        replyToPart = replyToPart,
        replyToPartLocator = replyToPartLocator,
        replyPartLocators = replyPartLocators,
        chatId = chatId,
    )
}

private object StaticChats : ChatListRepository {
    override fun chats(): Flow<List<ChatListItem>> = flowOf(
        listOf(ChatListItem(7L, "Test", null, 0L, 0, false, 0L)),
    )

    override fun markRead(id: Long) = Unit
    override fun setPinned(id: Long, pinned: Boolean) = Unit
    override fun setMuted(id: Long, muted: Boolean) = Unit
    override fun setArchived(id: Long, archived: Boolean) = Unit
    override fun delete(id: Long) = Unit
}

private object GroupedChats : ChatListRepository {
    override fun chats(): Flow<List<ChatListItem>> = flowOf(
        listOf(
            ChatListItem(
                id = 7L,
                title = "Grouped",
                snippet = null,
                date = 0L,
                unread = 0,
                pinned = false,
                avatarColor = 0L,
                memberChatIds = listOf(7L, 9L),
                preferredChatId = 9L,
            ),
        ),
    )

    override fun markRead(id: Long) = Unit
    override fun setPinned(id: Long, pinned: Boolean) = Unit
    override fun setMuted(id: Long, muted: Boolean) = Unit
    override fun setArchived(id: Long, archived: Boolean) = Unit
    override fun delete(id: Long) = Unit
}

private object StaticMessages : MessageListRepository {
    override fun messages(chatId: Long, limit: Int, before: Long?): Flow<List<MessageItem>> =
        flowOf(emptyList())

    override fun loadMore(chatId: Long, before: Long?, count: Int): List<MessageItem> = emptyList()
}

private class MutableMessages(initial: List<MessageItem>) : MessageListRepository {
    val value = MutableStateFlow(initial)

    override fun messages(chatId: Long, limit: Int, before: Long?): Flow<List<MessageItem>> = value

    override fun loadMore(chatId: Long, before: Long?, count: Int): List<MessageItem> = emptyList()
}

private class BlockingThreadMessages : MessageListRepository {
    val firstStarted = CountDownLatch(1)
    val secondStarted = CountDownLatch(1)
    val firstRelease = CountDownLatch(1)
    val secondRelease = CountDownLatch(1)
    val firstReturned = CountDownLatch(1)

    override fun messages(chatId: Long, limit: Int, before: Long?): Flow<List<MessageItem>> =
        flowOf(emptyList())

    override fun loadMore(chatId: Long, before: Long?, count: Int): List<MessageItem> = emptyList()

    override fun thread(chatId: Long, rootGuid: String, part: Long): List<MessageItem> {
        when (rootGuid) {
            "first" -> {
                firstStarted.countDown()
                check(firstRelease.await(5, TimeUnit.SECONDS))
                firstReturned.countDown()
            }
            "second" -> {
                secondStarted.countDown()
                check(secondRelease.await(5, TimeUnit.SECONDS))
            }
        }
        return emptyList()
    }
}

private class RecordingSender : Sender {
    var sent: Pair<Long, String>? = null
    var reply: Triple<Long, String, String>? = null
    var replyPartLocator: String? = null

    override suspend fun send(chatId: Long, text: String) {
        sent = chatId to text
    }

    override suspend fun sendReply(
        chatId: Long,
        text: String,
        replyGuid: String,
        replyPartLocator: String,
    ) {
        reply = Triple(chatId, text, replyGuid)
        this.replyPartLocator = replyPartLocator
    }
}

private class RecordingActions : MessageActions {
    var edit: Triple<Long, String, String>? = null
    var reaction: Triple<String, Int, String>? = null
    var reactionPart: Long? = null
    var reactionEmoji: String? = null
    var reactionChatId: Long? = null
    var unsend: Pair<Long, String>? = null

    override suspend fun react(
        chatId: Long,
        messageGuid: String,
        messageText: String,
        messagePart: Long,
        reactionIndex: Int,
        emoji: String?,
        enable: Boolean,
    ) {
        reactionChatId = chatId
        reaction = Triple(messageGuid, reactionIndex, messageText)
        reactionPart = messagePart
        reactionEmoji = emoji
    }

    override suspend fun edit(chatId: Long, messageGuid: String, newText: String) {
        edit = Triple(chatId, messageGuid, newText)
    }

    override suspend fun unsend(chatId: Long, messageGuid: String) {
        unsend = chatId to messageGuid
    }
}

private object NoopAttachmentSender : AttachmentSender {
    override suspend fun send(chatId: Long, attachment: OutgoingAttachment, caption: String?) = Unit
}

private class RecordingAttachmentSender : AttachmentSender {
    var calls = 0

    override suspend fun send(chatId: Long, attachment: OutgoingAttachment, caption: String?) {
        calls++
    }
}

private class RecordingStickerSender : StickerSender {
    var chatId: Long? = null
    var targetGuid: String? = null
    var targetPart: Long? = null
    var targetText: String? = null
    var transform: StickerTransform? = null

    override suspend fun send(
        chatId: Long,
        targetGuid: String,
        targetPart: Long,
        targetText: String,
        sticker: OutgoingAttachment,
        transform: StickerTransform,
    ) {
        this.chatId = chatId
        this.targetGuid = targetGuid
        this.targetPart = targetPart
        this.targetText = targetText
        this.transform = transform
    }
}

private object NoopTyping : TypingRepository {
    override fun typing(): Flow<List<TypingEntry>> = flowOf(emptyList())
}

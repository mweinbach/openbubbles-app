package app.openbubbles.nativeapp.ui.chat

import app.openbubbles.nativeapp.data.AttachmentSender
import app.openbubbles.nativeapp.data.ChatListItem
import app.openbubbles.nativeapp.data.ChatListRepository
import app.openbubbles.nativeapp.data.MessageActions
import app.openbubbles.nativeapp.data.MessageItem
import app.openbubbles.nativeapp.data.MessageListRepository
import app.openbubbles.nativeapp.data.MessageStatus
import app.openbubbles.nativeapp.data.OutgoingAttachment
import app.openbubbles.nativeapp.data.Sender
import app.openbubbles.nativeapp.data.TypingEntry
import app.openbubbles.nativeapp.data.TypingRepository
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
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
        val reply = message(guid = "child", replyToGuid = "root")

        model.beginReply(reply)
        model.onInputChange("thread reply")
        model.sendMessage()
        advanceUntilIdle()

        assertEquals(Triple(7L, "thread reply", "root"), sender.reply)
    }

    @Test
    fun `edit reaction and unsend reach native action layer`() = runTest(dispatcher) {
        val actions = RecordingActions()
        val model = model(RecordingSender(), actions)
        val target = message(guid = "target", text = "before")

        model.beginEdit(target)
        model.onInputChange("after")
        model.sendMessage()
        model.react(target, 3)
        model.unsend(target)
        advanceUntilIdle()

        assertEquals(Triple(7L, "target", "after"), actions.edit)
        assertEquals(Triple("target", 3, "before"), actions.reaction)
        assertEquals(7L to "target", actions.unsend)
    }

    private fun model(sender: Sender, actions: MessageActions) = ChatViewModel(
        chatId = 7L,
        chatListRepository = StaticChats,
        messageRepository = StaticMessages,
        sender = sender,
        messageActions = actions,
        attachmentSender = NoopAttachmentSender,
        typingRepository = NoopTyping,
        smsRouter = { _, _ -> false },
    )

    private fun message(
        guid: String,
        text: String = "hello",
        replyToGuid: String? = null,
    ) = MessageItem(
        id = 1L,
        text = text,
        isFromMe = true,
        date = 1L,
        status = MessageStatus.SENT,
        isGroupEvent = false,
        reactionEmoji = null,
        guid = guid,
        replyToGuid = replyToGuid,
    )
}

private object StaticChats : ChatListRepository {
    override fun chats(): Flow<List<ChatListItem>> = flowOf(
        listOf(ChatListItem(7L, "Test", null, 0L, 0, false, 0L)),
    )

    override fun markRead(id: Long) = Unit
}

private object StaticMessages : MessageListRepository {
    override fun messages(chatId: Long, limit: Int, before: Long?): Flow<List<MessageItem>> =
        flowOf(emptyList())

    override fun loadMore(chatId: Long, before: Long?, count: Int): List<MessageItem> = emptyList()
}

private class RecordingSender : Sender {
    var reply: Triple<Long, String, String>? = null

    override suspend fun send(chatId: Long, text: String) = Unit

    override suspend fun sendReply(chatId: Long, text: String, replyGuid: String) {
        reply = Triple(chatId, text, replyGuid)
    }
}

private class RecordingActions : MessageActions {
    var edit: Triple<Long, String, String>? = null
    var reaction: Triple<String, Int, String>? = null
    var unsend: Pair<Long, String>? = null

    override suspend fun react(
        chatId: Long,
        messageGuid: String,
        messageText: String,
        reactionIndex: Int,
        emoji: String?,
        enable: Boolean,
    ) {
        reaction = Triple(messageGuid, reactionIndex, messageText)
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

private object NoopTyping : TypingRepository {
    override fun typing(): Flow<List<TypingEntry>> = flowOf(emptyList())
}

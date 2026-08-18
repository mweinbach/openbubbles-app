package app.openbubbles.nativeapp.data

import app.openbubbles.core.intake.MessageIngestor
import app.openbubbles.core.repo.MessageRepo
import app.openbubbles.db.Message
import app.openbubbles.db.Message_
import io.objectbox.BoxStore
import io.objectbox.query.QueryBuilder

internal data class OutgoingTextStage(
    val tempGuid: String,
    val message: Message,
)

internal suspend fun stageOutgoingText(
    store: BoxStore,
    chatGuid: String,
    sender: String,
    text: String,
    tempGuid: String = MessageIngestor.tempGuid(),
    effectId: String? = null,
    replyGuid: String? = null,
    replyPartLocator: String? = null,
    subject: String? = null,
    attributedBody: String? = null,
): OutgoingTextStage = OutgoingTextStage(
    tempGuid = tempGuid,
    message = MessageRepo(store).stageOutgoingMessage(
        chatGuid = chatGuid,
        sender = sender,
        text = text,
        stagingGuid = tempGuid,
        expressiveSendStyleId = effectId,
        threadOriginatorGuid = replyGuid,
        threadOriginatorPart = replyPartLocator,
        subject = subject,
        attributedBody = attributedBody,
    ),
)

internal fun promoteOutgoingText(
    store: BoxStore,
    tempGuid: String,
    stagingGuid: String,
): Message? = store.callInTx {
    val messageBox = store.boxFor(Message::class.java)
    val staged = messageBox.query()
        .equal(Message_.guid, tempGuid, QueryBuilder.StringOrder.CASE_SENSITIVE)
        .build().use { it.findFirst() }
        ?: return@callInTx null
    staged.guid = stagingGuid
    staged.stagingGuid = stagingGuid
    messageBox.put(staged)
    staged
}

internal fun failOutgoingText(store: BoxStore, guid: String, reason: String): Message? =
    MessageRepo(store).failOutgoing(guid, reason)

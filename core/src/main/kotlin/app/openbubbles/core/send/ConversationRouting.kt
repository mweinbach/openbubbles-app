package app.openbubbles.core.send

import app.openbubbles.core.model.MessageMapper
import app.openbubbles.db.Chat
import uniffi.rust_lib_bluebubbles.UConversation

fun selectSendingHandle(chat: Chat, handles: Set<String>): String? {
    val preferred = chat.usingHandle
    if (preferred != null) {
        handles.firstOrNull { candidate ->
            candidate == preferred ||
                MessageMapper.normalizeAddress(candidate) == MessageMapper.normalizeAddress(preferred)
        }?.let { return it }
    }
    return handles.firstOrNull()
}

fun buildSendConversation(
    chat: Chat,
    afterGuid: String?,
    sender: String? = null,
): UConversation = UConversation(
    participants = buildList {
        addAll(chat.handles.map { MessageMapper.toRustHandle(it.address) })
        sender?.let { add(MessageMapper.toRustHandle(MessageMapper.normalizeAddress(it))) }
    }.distinct(),
    cvName = chat.apnTitle ?: chat.displayName,
    senderGuid = chat.guid,
    afterGuid = afterGuid ?: chat.guid,
)

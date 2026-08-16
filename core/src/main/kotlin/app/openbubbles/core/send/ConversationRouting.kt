package app.openbubbles.core.send

import app.openbubbles.core.model.MessageMapper
import app.openbubbles.db.Chat
import uniffi.rust_lib_bluebubbles.UConversation

fun resolveRegisteredHandle(preferred: String?, handles: Set<String>): String? {
    val requested = preferred?.takeIf { it.isNotBlank() } ?: return null
    return handles.firstOrNull { candidate ->
        candidate == requested ||
            MessageMapper.normalizeAddress(candidate) == MessageMapper.normalizeAddress(requested)
    }
}

fun selectSendingHandle(
    chat: Chat,
    handles: Set<String>,
    defaultHandle: String? = null,
): String? {
    resolveRegisteredHandle(chat.usingHandle, handles)?.let { return it }
    resolveRegisteredHandle(defaultHandle, handles)?.let { return it }
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
    afterGuid = afterGuid,
)

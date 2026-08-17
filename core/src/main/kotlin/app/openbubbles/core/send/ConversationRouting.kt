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

/**
 * Chooses the handle an outgoing message is sent from.
 *
 * Precedence: the user's explicit per-chat override, then the global default
 * sending handle, then the address the conversation was received on
 * ([Chat.usingHandle]), then the first registered handle. The default beats
 * the conversation address on purpose: a chat that started on another handle
 * (e.g. the iMessage email before a phone number was registered) must follow
 * the default once one is chosen, or every reply keeps splitting threads for
 * the other participants. [Chat.usingHandle] stays available as the
 * received-on address so the user can deliberately reply from it via the
 * per-chat override.
 */
fun selectSendingHandle(
    chat: Chat,
    handles: Set<String>,
    defaultHandle: String? = null,
): String? {
    resolveRegisteredHandle(chat.senderOverride, handles)?.let { return it }
    resolveRegisteredHandle(defaultHandle, handles)?.let { return it }
    resolveRegisteredHandle(chat.usingHandle, handles)?.let { return it }
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

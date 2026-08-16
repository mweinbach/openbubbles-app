package app.openbubbles.nativeapp.data

import app.openbubbles.core.contacts.ContactSync
import app.openbubbles.core.model.isGroupConversation
import app.openbubbles.db.Chat
import app.openbubbles.db.Handle

internal data class MessageNotificationIdentity(
    val title: String,
    val senderName: String?,
    val isGroup: Boolean,
)

internal fun resolveMessageNotificationIdentity(
    chat: Chat,
    senderAddress: String? = null,
    myHandles: Set<String> = emptySet(),
    contactNameFor: (String) -> String?,
): MessageNotificationIdentity {
    val normalizedMyHandles = myHandles.mapNotNullTo(HashSet()) { normalizedAddress(it) }
    val allHandles = chat.handles.toList()
    val participantHandles = allHandles.filter { handle ->
        normalizedAddress(handle.address) !in normalizedMyHandles
    }
    val normalizedSender = normalizedAddress(senderAddress)
        ?.takeUnless(normalizedMyHandles::contains)
    val senderHandle = normalizedSender?.let { sender ->
        allHandles.firstOrNull { handle -> normalizedAddress(handle.address) == sender }
    }
    val contactNames = HashMap<String, String?>()

    fun contactName(address: String?): String? {
        val normalized = normalizedAddress(address) ?: return null
        if (contactNames.containsKey(normalized)) return contactNames[normalized]
        val name = address?.let(contactNameFor)?.takeIf { it.isNotBlank() }
        contactNames[normalized] = name
        return name
    }

    fun handleName(handle: Handle): String? =
        contactName(handle.address)
            ?: handle.formattedAddress?.takeIf { it.isNotBlank() }
            ?: displayAddress(handle.address)

    val senderName = contactName(senderAddress)
        ?: senderHandle?.let(::handleName)
        ?: displayAddress(senderAddress)
    val isGroup = isGroupConversation(chat.style, participantHandles.size)

    val title = if (isGroup) {
        chat.displayName?.takeIf { it.isNotBlank() }
            ?: chat.apnTitle?.takeIf { it.isNotBlank() }
            ?: chat.title?.takeIf { it.isNotBlank() }
            ?: participantHandles.mapNotNull(::handleName).joinToString(", ").takeIf { it.isNotBlank() }
            ?: "Group"
    } else {
        val contactTitle = buildList {
            if (normalizedSender != null) senderAddress?.let(::add)
            participantHandles.mapTo(this) { it.address }
        }.firstNotNullOfOrNull(::contactName)
        contactTitle
            ?: chat.displayName?.takeIf { it.isNotBlank() }
            ?: participantHandles.firstOrNull()?.let(::handleName)
            ?: senderName
            ?: "Message"
    }

    return MessageNotificationIdentity(
        title = title,
        senderName = senderName,
        isGroup = isGroup,
    )
}

private fun normalizedAddress(address: String?): String? =
    address?.let(ContactSync::normalizeAddress)?.takeIf { it.isNotEmpty() }

private fun displayAddress(address: String?): String? {
    val trimmed = address?.trim()?.takeIf { it.isNotEmpty() } ?: return null
    return when {
        trimmed.startsWith("mailto:", ignoreCase = true) -> trimmed.substring(7)
        trimmed.startsWith("tel:", ignoreCase = true) -> trimmed.substring(4)
        else -> trimmed
    }
}

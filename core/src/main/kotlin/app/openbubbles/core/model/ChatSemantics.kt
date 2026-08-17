package app.openbubbles.core.model

import app.openbubbles.core.contacts.ContactSync
import app.openbubbles.db.Chat
import app.openbubbles.db.Handle

/** Apple chat style for a named/unnamed group. */
const val GROUP_CHAT_STYLE = 43L

/** Apple chat style for a one-to-one conversation. */
const val DIRECT_CHAT_STYLE = 45L

/**
 * Legacy Chat.isGroup: style 43 is always a group. Style 45 is always a
 * direct chat, even when CloudKit persisted the local account as a handle.
 * Missing style falls back to the other-participant count.
 */
fun isGroupConversation(style: Long?, otherParticipantCount: Int): Boolean =
    style == GROUP_CHAT_STYLE || (style != DIRECT_CHAT_STYLE && otherParticipantCount > 1)

fun Chat.isGroupConversation(): Boolean =
    isGroupConversation(style, otherDirectHandles().size)

/**
 * The other person in a 1:1 chat. CloudKit rows include our own handles;
 * those are dropped using [selfAddresses] plus [Chat.usingHandle]. Groups
 * and ambiguous participant sets return null so unrelated people are never
 * merged.
 */
fun Chat.otherDirectHandle(selfAddresses: Collection<String> = emptyList()): Handle? =
    otherDirectHandles(selfAddresses).singleOrNull()

fun Chat.otherDirectHandles(selfAddresses: Collection<String> = emptyList()): List<Handle> {
    if (style == GROUP_CHAT_STYLE) return emptyList()
    val selfKeys = LinkedHashSet<String>()
    selfAddresses.forEach { selfKeys += ContactSync.addressMatchKeys(it) }
    usingHandle?.takeIf { it.isNotBlank() }?.let { selfKeys += ContactSync.addressMatchKeys(it) }
    if (selfKeys.isEmpty()) return handles.toList()
    return handles.filter { handle ->
        ContactSync.addressMatchKeys(handle.address).none(selfKeys::contains) &&
            handle.formattedAddress
                ?.let { ContactSync.addressMatchKeys(it).none(selfKeys::contains) } != false
    }
}

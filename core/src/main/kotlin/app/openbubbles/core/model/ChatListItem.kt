package app.openbubbles.core.model

import java.util.Date

/**
 * UI projection of a [app.openbubbles.db.Chat] for the chat list.
 *
 * Mirrors what the Flutter app's chat tile binds to: title (displayName or
 * derived from participants), latest-message snippet + date, unread flag, and
 * pin state. Mapping happens in the repositories; the UI never touches
 * ObjectBox entities.
 */
data class ChatListItem(
    /** ObjectBox entity id of the chat. */
    val id: Long,
    /** Stable chat GUID. */
    val guid: String,
    /** Resolved display title (displayName > derived-from-handles). */
    val title: String,
    /** Short text of the latest message, or null when there is none. */
    val snippet: String?,
    /** Date of the latest message (or null when the chat is empty). */
    val date: Date?,
    /** True when there are unseen incoming messages. */
    val hasUnread: Boolean,
    /** Count of unseen incoming messages (0 when read). */
    val unreadCount: Int,
    /** True when the user pinned this chat. */
    val pinned: Boolean,
    /** True for SMS-relay chats (RP SMS). */
    val isSms: Boolean,
    /** Number of participants (excluding me) — 1 means DM. */
    val participantCount: Int,
)

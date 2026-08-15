package app.openbubbles.core.model

import app.openbubbles.db.Chat

/** Legacy Chat.isGroup: style 43 or more than one participant besides me. */
fun isGroupConversation(style: Long?, otherParticipantCount: Int): Boolean =
    style == 43L || otherParticipantCount > 1

fun Chat.isGroupConversation(): Boolean = isGroupConversation(style, handles.size)

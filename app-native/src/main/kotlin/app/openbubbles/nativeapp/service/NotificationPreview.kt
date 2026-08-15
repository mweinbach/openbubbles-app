package app.openbubbles.nativeapp.service

import app.openbubbles.core.model.MessageMapper
import uniffi.rust_lib_bluebubbles.UMessage
import uniffi.rust_lib_bluebubbles.UMessageInst
import uniffi.rust_lib_bluebubbles.UPart

/**
 * Builds the body shown for a native incoming-message notification.
 *
 * This mirrors the legacy app's Message.getNotificationText attachment
 * summaries. Attachment previews intentionally take precedence over text,
 * matching the old app for multipart messages.
 */
internal fun notificationPreview(inst: UMessageInst): String? {
    val normal = inst.message as? UMessage.Normal
        ?: return (inst.message as? UMessage.React)?.let(::reactionPreview)
    val attachments = normal.parts.mapNotNull { indexed ->
        (indexed.part as? UPart.Attachment)
            ?.takeUnless { it.iris || it.mime == "application/smil" }
    }
    if (attachments.isNotEmpty()) return attachmentSummary(attachments)

    return normal.parts.joinToString("") { indexed ->
        when (val part = indexed.part) {
            is UPart.Text -> part.text
            is UPart.Mention -> part.text
            is UPart.Attachment -> " "
            is UPart.Object -> ""
        }
    }.trim().ifEmpty { null }
}

/** Legacy-style tapback summary used by reaction notifications. */
private fun reactionPreview(reaction: UMessage.React): String? {
    val (rawType, emoji) = MessageMapper.parseReaction(reaction.reactionJson)
    val type = rawType ?: return null
    if (type == "meta") return null
    val removed = type.startsWith("-")
    val base = type.removePrefix("-")
    val verb = if (removed) {
        when (base) {
            MessageMapper.REACTION_LOVE -> "Removed a heart from"
            MessageMapper.REACTION_LIKE -> "Removed a like from"
            MessageMapper.REACTION_DISLIKE -> "Removed a dislike from"
            MessageMapper.REACTION_LAUGH -> "Removed a laugh from"
            MessageMapper.REACTION_EMPHASIZE -> "Removed an exclamation from"
            MessageMapper.REACTION_QUESTION -> "Removed a question mark from"
            MessageMapper.REACTION_EMOJI -> "Removed ${emoji.orEmpty()} from"
            MessageMapper.REACTION_STICKER,
            MessageMapper.REACTION_STICKERBACK,
            -> "Removed a sticker from"
            else -> return null
        }
    } else {
        when (base) {
            MessageMapper.REACTION_LOVE -> "Loved"
            MessageMapper.REACTION_LIKE -> "Liked"
            MessageMapper.REACTION_DISLIKE -> "Disliked"
            MessageMapper.REACTION_LAUGH -> "Laughed at"
            MessageMapper.REACTION_EMPHASIZE -> "Emphasized"
            MessageMapper.REACTION_QUESTION -> "Questioned"
            MessageMapper.REACTION_EMOJI -> "Reacted ${emoji.orEmpty()} to"
            MessageMapper.REACTION_STICKER,
            MessageMapper.REACTION_STICKERBACK,
            -> "Reacted with a sticker to"
            else -> return null
        }
    }
    return "$verb “${reaction.toText}”"
}

private fun attachmentSummary(attachments: List<UPart.Attachment>): String {
    val counts = linkedMapOf<String, Int>()
    attachments.forEach { attachment ->
        val label = attachmentLabel(attachment.mime)
        counts[label] = (counts[label] ?: 0) + 1
    }
    val summaries = counts.map { (label, count) ->
        "$count $label${if (count > 1) "s" else ""}"
    }
    return summaries.joinToString(if (summaries.size == 2) " & " else ", ")
}

private fun attachmentLabel(mime: String): String = when {
    mime.contains("vcard", ignoreCase = true) -> "Contact card"
    mime.contains("location", ignoreCase = true) -> "Location"
    mime.contains("contact", ignoreCase = true) -> "Contact"
    mime.contains("video", ignoreCase = true) -> "Video"
    mime.contains("audio", ignoreCase = true) -> "Audio message"
    mime.contains("image/gif", ignoreCase = true) -> "GIF"
    mime.contains("image", ignoreCase = true) -> "Photo"
    mime.contains("application/pdf", ignoreCase = true) -> "PDF"
    mime.contains('/') -> mime.substringBefore('/').replaceFirstChar { it.titlecase() }
    else -> "File"
}

/**
 * Removes the row that was just ingested and is about to be appended to a
 * MessagingStyle notification. [rowsOldestFirst] must be oldest-to-newest.
 */
internal fun <T> withoutCurrentNotificationRow(
    rowsOldestFirst: List<T>,
    currentText: String?,
    textOf: (T) -> String?,
): List<T> {
    if (currentText == null || rowsOldestFirst.isEmpty()) return rowsOldestFirst
    val newestMatches = textOf(rowsOldestFirst.last())?.trim() == currentText.trim()
    return if (newestMatches) rowsOldestFirst.dropLast(1) else rowsOldestFirst
}

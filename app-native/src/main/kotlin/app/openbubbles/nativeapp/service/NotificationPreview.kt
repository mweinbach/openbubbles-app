package app.openbubbles.nativeapp.service

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
    val normal = inst.message as? UMessage.Normal ?: return null
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

package app.openbubbles.core.model

/** The two mutable part lists in Flutter's MessageSummaryInfo JSON. */
enum class MessageSummaryPartList(val jsonKey: String) {
    RETRACTED("retractedParts"),
    EDITED("editedParts"),
}

/**
 * Adds [part] to one MessageSummaryInfo list without discarding edit history
 * or other fields written by the legacy app. Old compact `rp` retraction
 * arrays are updated in place when present.
 */
fun addMessageSummaryPart(
    existing: String?,
    list: MessageSummaryPartList,
    part: ULong,
): String {
    val source = existing?.takeIf { it.contains('{') }
        ?: """[{"retractedParts":[],"editedContent":{},"originalTextRange":{},"editedParts":[]}]"""
    val keys = if (list == MessageSummaryPartList.RETRACTED) {
        listOf("retractedParts", "rp")
    } else {
        listOf(list.jsonKey)
    }
    keys.forEach { key ->
        val regex = Regex("\\\"${Regex.escape(key)}\\\"\\s*:\\s*\\[([^]]*)]")
        val match = regex.find(source) ?: return@forEach
        val values = Regex("\\d+").findAll(match.groupValues[1]).map { it.value }.toMutableList()
        val value = part.toString()
        if (value !in values) values += value
        return source.replaceRange(
            match.range,
            "\"$key\":[${values.joinToString(",")}]",
        )
    }

    val objectStart = source.indexOf('{')
    if (objectStart < 0) return source
    return source.replaceRange(
        objectStart,
        objectStart + 1,
        "{\"${list.jsonKey}\":[$part],",
    )
}

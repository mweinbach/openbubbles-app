package app.openbubbles.core.contacts

/** Fields rendered on a transcript contact card. */
data class TranscriptVCard(
    val displayName: String,
    val phones: List<String>,
    val emails: List<String>,
    val organization: String?,
)

object TranscriptVCardParser {
    fun parse(raw: String): TranscriptVCard? {
        if (raw.isBlank()) return null
        val looksLikeVcard = raw.contains("BEGIN:VCARD", ignoreCase = true) ||
            raw.contains("FN:", ignoreCase = true) ||
            raw.contains("TEL", ignoreCase = true) ||
            raw.contains("EMAIL", ignoreCase = true)
        if (!looksLikeVcard) return null

        var displayName: String? = null
        var firstName: String? = null
        var lastName: String? = null
        var organization: String? = null
        val phones = ArrayList<String>()
        val emails = ArrayList<String>()

        for (folded in unfold(raw)) {
            val separator = folded.indexOf(':')
            if (separator <= 0) continue
            val descriptor = folded.substring(0, separator)
            val property = descriptor.substringBefore(';').substringAfterLast('.').uppercase()
            val value = unescape(folded.substring(separator + 1).trim())
            if (value.isEmpty()) continue
            when (property) {
                "FN" -> displayName = value
                "N" -> {
                    val parts = value.split(';')
                    lastName = parts.getOrNull(0)?.takeIf { it.isNotBlank() }
                    firstName = parts.getOrNull(1)?.takeIf { it.isNotBlank() }
                }
                "ORG" -> organization = value.substringBefore(';').takeIf { it.isNotBlank() }
                "TEL" -> phones += value.removePrefix("tel:", ignoreCase = true)
                "EMAIL" -> emails += value.removePrefix("mailto:", ignoreCase = true)
            }
        }

        val name = displayName
            ?: listOfNotNull(firstName, lastName).joinToString(" ").ifBlank { null }
            ?: organization
            ?: phones.firstOrNull()
            ?: emails.firstOrNull()
            ?: return null
        return TranscriptVCard(
            displayName = name,
            phones = phones.distinct(),
            emails = emails.distinct(),
            organization = organization,
        )
    }

    private fun unfold(raw: String): List<String> {
        val lines = raw.replace("\r\n", "\n").replace('\r', '\n').split('\n')
        val out = ArrayList<String>()
        for (line in lines) {
            if (line.startsWith(" ") || line.startsWith("\t")) {
                if (out.isNotEmpty()) out[out.lastIndex] = out.last() + line.substring(1)
            } else if (line.isNotBlank()) {
                out += line
            }
        }
        return out
    }

    private fun unescape(value: String): String = buildString(value.length) {
        var index = 0
        while (index < value.length) {
            val char = value[index]
            if (char == '\\' && index + 1 < value.length) {
                when (val next = value[index + 1]) {
                    'n', 'N' -> append('\n')
                    't', 'T' -> append('\t')
                    ',', ';', '\\' -> append(next)
                    else -> append(next)
                }
                index += 2
            } else {
                append(char)
                index += 1
            }
        }
    }
}

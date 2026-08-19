package app.openbubbles.core.text

/**
 * iMessage-style emoticon substitution. Longer tokens win so `:-)` is not
 * truncated to `:)` plus a leftover hyphen.
 */
object EmoticonReplacement {
    private val replacements = listOf(
        ":'(" to "😢",
        "</3" to "💔",
        "<3" to "❤️",
        ":-)" to "🙂",
        ":-(" to "🙁",
        ";-)" to "😉",
        ":-D" to "😃",
        ":-P" to "😛",
        ":-p" to "😛",
        ":-O" to "😮",
        ":-o" to "😮",
        ":)" to "🙂",
        ":(" to "🙁",
        ";)" to "😉",
        ":D" to "😃",
        ":P" to "😛",
        ":p" to "😛",
        ":O" to "😮",
        ":o" to "😮",
        ":*" to "😘",
        ":|" to "😐",
    )

    fun apply(text: String): String {
        if (text.isEmpty()) return text
        var result = text
        for ((token, emoji) in replacements) {
            if (token in result) result = result.replace(token, emoji)
        }
        return result
    }
}

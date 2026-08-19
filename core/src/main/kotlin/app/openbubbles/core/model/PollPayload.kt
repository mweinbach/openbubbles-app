package app.openbubbles.core.model

import java.nio.charset.StandardCharsets
import java.util.Base64

/** Apple Polls balloon identifiers and vote/create JSON. */
object PollPayload {
    const val POLLS_BUNDLE_ID =
        "com.apple.messages.MSMessageExtensionBalloonPlugin:0000000000:com.apple.messages.Polls"
    const val POLLS_APP_NAME = "Polls"
    const val FIND_MY_BUNDLE_ID =
        "com.apple.messages.MSMessageExtensionBalloonPlugin:1:com.apple.findmy.FindMyMessagesApp"
    const val FIND_MY_APP_NAME = "Find My"

    fun voteItemJson(optionId: String, participantHandle: String): String {
        val option = jsonString(optionId)
        val handle = jsonString(participantHandle)
        return """{"item":{"votes":[{"voteOptionIdentifier":$option,"participantHandle":$handle}]},"version":1}"""
    }

    fun createItemJson(question: String, options: List<String>): String {
        val title = jsonString(question)
        val optionJson = options.mapIndexed { index, text ->
            """{"optionIdentifier":"option-$index","text":${jsonString(text)}}"""
        }.joinToString(",")
        return """{"item":{"title":$title,"orderedPollOptions":[$optionJson],"votes":[]},"version":1}"""
    }

    fun dataUrl(json: String): String {
        val encoded = Base64.getEncoder().encodeToString(json.toByteArray(StandardCharsets.UTF_8))
        return "data:,$encoded"
    }

    fun appJson(
        bundleId: String,
        appName: String,
        url: String,
        session: String? = null,
        ldText: String? = null,
    ): String = buildString {
        append("""{"appName":${jsonString(appName)},"bundleId":${jsonString(bundleId)},"url":${jsonString(url)}""")
        if (session != null) append(""","session":${jsonString(session)}""")
        if (ldText != null) append(""","ldText":${jsonString(ldText)}""")
        append('}')
    }

    private fun jsonString(value: String): String = buildString(value.length + 2) {
        append('"')
        for (char in value) {
            when (char) {
                '\\' -> append("\\\\")
                '"' -> append("\\\"")
                '\n' -> append("\\n")
                '\r' -> append("\\r")
                '\t' -> append("\\t")
                else -> append(char)
            }
        }
        append('"')
    }
}

package app.openbubbles.core.model

/** Minimal typed view of rustpush's serde JSON delete/recovery envelopes. */
data class DeleteMessageCommand(
    val messageGuids: List<String>,
    val chatGuid: String?,
    val groupId: String?,
    val participants: List<String>,
    val recoverableDeleteDateMs: Long?,
)

fun decodeDeleteMessageCommand(json: String): DeleteMessageCommand {
    fun string(key: String): String? = Regex(
        "\\\"${Regex.escape(key)}\\\"\\s*:\\s*\\\"([^\\\"]*)\\\"",
    ).find(json)?.groupValues?.get(1)

    fun strings(key: String): List<String> {
        val body = Regex(
            "\\\"${Regex.escape(key)}\\\"\\s*:\\s*\\[([^]]*)]",
        ).find(json)?.groupValues?.get(1) ?: return emptyList()
        return Regex("\\\"([^\\\"]*)\\\"").findAll(body).map { it.groupValues[1] }.toList()
    }

    val messages = strings("Messages")
    val chatTarget = Regex("\\\"Chat\\\"\\s*:").containsMatchIn(json) ||
        Regex("\\\"RecoverChat\\\"\\s*:").containsMatchIn(json)
    return DeleteMessageCommand(
        messageGuids = messages,
        chatGuid = if (chatTarget) string("guid") else null,
        groupId = if (chatTarget) string("groupID") else null,
        participants = if (chatTarget) strings("ptcpts") else emptyList(),
        recoverableDeleteDateMs = Regex(
            "\\\"recoverable_delete_date\\\"\\s*:\\s*(\\d+)",
        ).find(json)?.groupValues?.get(1)?.toLongOrNull(),
    )
}

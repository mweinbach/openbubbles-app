package app.openbubbles.core.sync

data class TranscriptBackgroundUpdate(
    val chatId: Long,
    val version: Long,
    val remove: Boolean,
    val mmcsXml: String?,
)

fun interface TranscriptBackgroundHandler {
    suspend fun apply(update: TranscriptBackgroundUpdate)
}

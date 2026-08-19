package app.openbubbles.nativeapp.service

internal enum class NotificationPendingIntentOperation(
    val action: String,
    val uriPath: String,
) {
    OPEN(
        action = "app.openbubbles.nativeapp.action.NOTIFICATION_OPEN",
        uriPath = "open",
    ),
    DISMISS(
        action = "app.openbubbles.nativeapp.action.NOTIFICATION_DISMISS",
        uriPath = "dismiss",
    ),
    MARK_READ(
        action = "app.openbubbles.nativeapp.action.NOTIFICATION_MARK_READ",
        uriPath = "mark-read",
    ),
    REPLY(
        action = "app.openbubbles.nativeapp.action.NOTIFICATION_REPLY",
        uriPath = "reply",
    ),
}

internal data class NotificationPendingIntentIdentity(
    val action: String,
    val dataUri: String,
)

/**
 * PendingIntent equality ignores extras. Give each operation and stable
 * conversation its own filter identity so hash request-code collisions cannot
 * route an action to another chat.
 */
internal fun notificationPendingIntentIdentity(
    conversationId: String,
    operation: NotificationPendingIntentOperation,
): NotificationPendingIntentIdentity {
    require(conversationId.isNotBlank()) { "conversationId must not be blank" }
    return NotificationPendingIntentIdentity(
        action = operation.action,
        dataUri = "openbubbles://notification/$conversationId/${operation.uriPath}",
    )
}

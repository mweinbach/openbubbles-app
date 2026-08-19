package app.openbubbles.nativeapp.service

/**
 * Whether an incoming message is being handled for the first time or replayed
 * from Rust's durable journal while the service is starting.
 */
internal enum class IncomingNotificationSource {
    LIVE,
    JOURNAL_RECOVERY,
}

internal fun journalEntryNotificationSource(
    drainSource: IncomingNotificationSource,
    priorAttempts: Int,
    failedInThisProcess: Boolean = false,
): IncomingNotificationSource =
    if (
        drainSource == IncomingNotificationSource.JOURNAL_RECOVERY ||
        priorAttempts > 0 ||
        failedInThisProcess
    ) {
        IncomingNotificationSource.JOURNAL_RECOVERY
    } else {
        IncomingNotificationSource.LIVE
    }

/** Final notification disposition reached before a Rust pointer is completed. */
internal enum class IncomingNotificationDisposition {
    POST,
    NOT_ELIGIBLE,
    NOT_PERSISTED,
    NOT_NEW_LIVE_DELIVERY,
    READ,
    VISIBLE,
    MUTED,
    BLOCKED,
    NOTIFICATIONS_DISABLED,
    ALREADY_ACTIVE,
}

/**
 * Pure inputs to the notification recovery decision. Android's
 * NotificationManager is sampled by the caller and reduced to booleans here.
 */
internal data class IncomingNotificationFacts(
    val source: IncomingNotificationSource,
    val newlyIngested: Boolean,
    val eligibleIncoming: Boolean,
    val persisted: Boolean,
    val unread: Boolean,
    val conversationVisible: Boolean,
    val muted: Boolean,
    val blocked: Boolean,
    val notificationsEnabled: Boolean,
    val activeMatchingNotification: Boolean,
)

internal data class IncomingNotificationRuntimeState(
    val notificationsEnabled: Boolean,
    val activeMatchingNotification: Boolean,
)

internal fun incomingNotificationDisposition(
    facts: IncomingNotificationFacts,
): IncomingNotificationDisposition = when {
    !facts.eligibleIncoming -> IncomingNotificationDisposition.NOT_ELIGIBLE
    !facts.persisted -> IncomingNotificationDisposition.NOT_PERSISTED
    facts.source == IncomingNotificationSource.LIVE && !facts.newlyIngested ->
        IncomingNotificationDisposition.NOT_NEW_LIVE_DELIVERY
    !facts.unread -> IncomingNotificationDisposition.READ
    facts.conversationVisible -> IncomingNotificationDisposition.VISIBLE
    facts.muted -> IncomingNotificationDisposition.MUTED
    facts.blocked -> IncomingNotificationDisposition.BLOCKED
    !facts.notificationsEnabled -> IncomingNotificationDisposition.NOTIFICATIONS_DISABLED
    facts.activeMatchingNotification -> IncomingNotificationDisposition.ALREADY_ACTIVE
    else -> IncomingNotificationDisposition.POST
}

/**
 * Minimal representation of an active Android notification used to keep the
 * matching rule deterministic in host tests.
 */
internal data class ActiveMessageNotificationRef(
    val id: Int,
    val conversationId: String?,
    val messageGuid: String?,
)

internal fun hasActiveMatchingMessageNotification(
    entries: Collection<ActiveMessageNotificationRef>,
    identity: ConversationIdentity,
    messageGuid: String,
): Boolean = entries.any { entry ->
    entry.messageGuid == messageGuid &&
        (
            entry.id == identity.notificationId ||
                entry.conversationId == identity.conversationId
            )
}

/**
 * Mirrors ChatRepo's unread boundary for one persisted incoming row.
 */
internal fun isPersistedIncomingMessageUnread(
    chatHasUnreadMessage: Boolean,
    chatDeleted: Boolean,
    messageFromMe: Boolean,
    messageDeleted: Boolean,
    messageCreatedAtMs: Long?,
    lastReadAtMs: Long?,
): Boolean {
    if (!chatHasUnreadMessage || chatDeleted || messageFromMe || messageDeleted) return false
    return lastReadAtMs == null || messageCreatedAtMs?.let { it > lastReadAtMs } == true
}

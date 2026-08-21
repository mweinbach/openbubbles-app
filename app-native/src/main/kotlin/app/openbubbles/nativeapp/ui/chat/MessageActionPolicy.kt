package app.openbubbles.nativeapp.ui.chat

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.ui.Modifier
import app.openbubbles.nativeapp.data.AttachmentMeta
import app.openbubbles.nativeapp.data.MessageItem
import app.openbubbles.nativeapp.data.MessageStatus
import app.openbubbles.nativeapp.data.displayTextForRichLink

internal const val MessageActionsLabel = "Message actions"

internal const val TapbackLove = "❤️"
internal const val TapbackLike = "👍"
internal const val TapbackDislike = "👎"
internal const val TapbackLaugh = "😂"
internal const val TapbackEmphasize = "‼️"
internal const val TapbackQuestion = "❓"

internal val ActionTapbacks = listOf(
    TapbackLove,
    TapbackLike,
    TapbackDislike,
    TapbackLaugh,
    TapbackEmphasize,
    TapbackQuestion,
)

internal fun canOpenMessageActions(message: MessageItem): Boolean =
    !message.isGroupEvent && !message.unsent && message.status != MessageStatus.SENDING

/** Double-tap is the iMessage Tapback shortcut; SMS keeps long-press only. */
internal fun canDoubleTapMessageActions(message: MessageItem, smsChat: Boolean): Boolean =
    canOpenMessageActions(message) && !smsChat

internal fun messageAttachments(message: MessageItem): List<AttachmentMeta> =
    message.attachmentMetas.ifEmpty { listOfNotNull(message.attachmentMeta) }

internal fun messageDisplayText(message: MessageItem): String {
    val richLink = message.richLink
    return if (richLink != null) {
        displayTextForRichLink(message.text, richLink.url)
    } else {
        message.text
    }
}

internal fun messageShowsTextBubble(message: MessageItem): Boolean {
    val displayText = messageDisplayText(message)
    return (displayText.isNotBlank() || !message.subject.isNullOrBlank()) &&
        message.interactivePayload == null
}

internal fun messageTextPart(message: MessageItem): Long {
    val attachmentParts = messageAttachments(message).mapTo(hashSetOf()) { it.partIndex }
    return message.replyPartLocators.keys.firstOrNull { it !in attachmentParts } ?: 0L
}

internal fun defaultMessageActionPart(message: MessageItem): Long {
    val attachments = messageAttachments(message)
    return when {
        messageShowsTextBubble(message) -> messageTextPart(message)
        attachments.isNotEmpty() -> attachments.last().partIndex
        else -> messageTextPart(message)
    }
}

internal enum class MessageSurfaceGesture {
    SingleTap,
    DoubleTap,
    LongPress,
    HorizontalDrag,
}

internal enum class MessageSurfaceOutcome {
    None,
    PrimaryClick,
    OpenActions,
    CommitReply,
}

/**
 * One owner for bubble click, double-tap, long-press, and swipe-to-reply.
 * A horizontal drag past touch slop wins so a reply swipe never also opens
 * the action sheet.
 */
internal fun resolveMessageSurfaceOutcome(
    gesture: MessageSurfaceGesture,
    actionsEnabled: Boolean,
    doubleTapActionsEnabled: Boolean,
    horizontalAbsPx: Float,
    touchSlopPx: Float,
    replyArmed: Boolean,
): MessageSurfaceOutcome {
    if (horizontalAbsPx >= touchSlopPx) {
        return if (replyArmed) MessageSurfaceOutcome.CommitReply else MessageSurfaceOutcome.None
    }
    return when (gesture) {
        MessageSurfaceGesture.LongPress ->
            if (actionsEnabled) MessageSurfaceOutcome.OpenActions else MessageSurfaceOutcome.None
        MessageSurfaceGesture.DoubleTap ->
            if (doubleTapActionsEnabled) MessageSurfaceOutcome.OpenActions else MessageSurfaceOutcome.None
        MessageSurfaceGesture.SingleTap -> MessageSurfaceOutcome.PrimaryClick
        MessageSurfaceGesture.HorizontalDrag -> MessageSurfaceOutcome.None
    }
}

internal fun messageActionInvocationCount(outcomes: List<MessageSurfaceOutcome>): Int =
    outcomes.count { it == MessageSurfaceOutcome.OpenActions }

/** Callbacks [messagePartGestures] attaches to one rendered message surface. */
internal data class MessagePartInteraction(
    val onClick: (() -> Unit)?,
    val onLongClick: (() -> Unit)?,
    val onDoubleClick: (() -> Unit)?,
)

internal fun bindMessagePartInteraction(
    onClick: (() -> Unit)? = null,
    onOpenActions: (() -> Unit)? = null,
    enableDoubleTapActions: Boolean = false,
    onDoubleTapActions: (() -> Unit)? = if (enableDoubleTapActions) onOpenActions else null,
): MessagePartInteraction = MessagePartInteraction(
    onClick = onClick,
    onLongClick = onOpenActions,
    onDoubleClick = onDoubleTapActions,
)

internal fun tapbackContentDescription(emoji: String): String = when (emoji) {
    TapbackLove -> "Love"
    TapbackLike -> "Like"
    TapbackDislike -> "Dislike"
    TapbackLaugh -> "Laugh"
    TapbackEmphasize -> "Emphasize"
    TapbackQuestion -> "Question"
    else -> "Reaction $emoji"
}

@OptIn(ExperimentalFoundationApi::class)
internal fun Modifier.messagePartGestures(
    onClick: (() -> Unit)? = null,
    onClickLabel: String? = null,
    onOpenActions: (() -> Unit)? = null,
    onDoubleTapActions: (() -> Unit)? = null,
    onLongClickLabel: String = MessageActionsLabel,
    enabled: Boolean = true,
): Modifier {
    val interaction = bindMessagePartInteraction(
        onClick = onClick,
        onOpenActions = onOpenActions,
        onDoubleTapActions = onDoubleTapActions,
    )
    if (interaction.onClick == null &&
        interaction.onLongClick == null &&
        interaction.onDoubleClick == null
    ) {
        return this
    }
    if (interaction.onLongClick == null && interaction.onDoubleClick == null) {
        return clickable(
            enabled = enabled,
            onClickLabel = onClickLabel,
            onClick = interaction.onClick ?: {},
        )
    }
    return combinedClickable(
        enabled = enabled,
        onClickLabel = onClickLabel,
        onLongClickLabel = interaction.onLongClick?.let { onLongClickLabel },
        onLongClick = interaction.onLongClick,
        onDoubleClick = interaction.onDoubleClick,
        onClick = interaction.onClick ?: {},
    )
}

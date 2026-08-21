package app.openbubbles.nativeapp.ui.chat

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.ui.Modifier
import app.openbubbles.nativeapp.data.AttachmentMeta
import app.openbubbles.nativeapp.data.MessageItem
import app.openbubbles.nativeapp.data.MessageReactionUi
import app.openbubbles.nativeapp.data.MessageStatus
import app.openbubbles.nativeapp.data.displayTextForRichLink

internal const val MessageActionsLabel = "Message actions"
internal const val AddReactionLabel = "Add reaction"

internal const val TapbackLove = "❤️"
internal const val TapbackLike = "👍"
internal const val TapbackDislike = "👎"
internal const val TapbackLaugh = "😂"
internal const val TapbackEmphasize = "‼️"
internal const val TapbackQuestion = "❓"

/** iMessage tapback set, in the order the protocol indexes them. */
internal val ActionTapbacks = listOf(
    TapbackLove,
    TapbackLike,
    TapbackDislike,
    TapbackLaugh,
    TapbackEmphasize,
    TapbackQuestion,
)

/** Starting points offered in the custom-reaction dialog. */
internal val CustomReactionSuggestions = listOf("🔥", "🎉", "🥰", "😮", "💯")

/** Protocol reaction index that carries a custom emoji instead of a tapback. */
internal const val CustomReactionIndex = 6

/**
 * What the tapback pill on a bubble draws: the distinct emoji present, plus
 * the spoken tally. Falls back to [MessageItem.reactionEmoji] so a projection
 * that only carries the newest reaction still renders one pill.
 */
internal data class BubbleReactionSummary(
    val emojis: List<String>,
    val label: String,
)

/** Distinct emoji shown on the pill before it collapses into a "+N" tail. */
internal const val BubbleReactionEmojiLimit = 3

internal fun bubbleReactionSummary(message: MessageItem): BubbleReactionSummary? {
    if (message.reactions.isEmpty()) {
        val fallback = message.reactionEmoji ?: return null
        return BubbleReactionSummary(listOf(fallback), "Reaction $fallback")
    }
    val counts = LinkedHashMap<String, Int>()
    message.reactions.forEach { reaction ->
        counts[reaction.emoji] = (counts[reaction.emoji] ?: 0) + 1
    }
    return BubbleReactionSummary(
        emojis = counts.keys.toList(),
        label = "Reactions: " + counts.entries.joinToString(", ") { (emoji, count) ->
            if (count > 1) "$emoji $count" else emoji
        },
    )
}

internal fun canOpenMessageActions(message: MessageItem): Boolean =
    !message.isGroupEvent && !message.unsent && message.status != MessageStatus.SENDING

/** Double-tap is the iMessage Tapback shortcut; SMS and failed rows keep long-press only. */
internal fun canDoubleTapMessageActions(message: MessageItem): Boolean =
    canOpenMessageActions(message) && !message.isSms && message.status != MessageStatus.FAILED

internal fun reactionsForPart(
    reactions: List<MessageReactionUi>,
    targetPart: Long,
): List<MessageReactionUi> = reactions.filter { it.targetPart == targetPart }

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

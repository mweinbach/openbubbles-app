package app.openbubbles.nativeapp.ui.chat

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddReaction
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.isTraversalGroup
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.openbubbles.nativeapp.data.MessageReactionUi
import app.openbubbles.nativeapp.ui.common.ChatAvatar
import app.openbubbles.nativeapp.ui.common.avatarColorFor
import app.openbubbles.nativeapp.ui.common.rememberContactAvatarPath
import app.openbubbles.nativeapp.ui.theme.LocalReduceMotion
import app.openbubbles.nativeapp.ui.theme.fastEffectsSpec
import app.openbubbles.nativeapp.ui.theme.fastSpatialSpec

/** Label used for my own tapback wherever a reactor is named. */
internal const val SelfReactorName = "You"

/** Avatars drawn under one emoji before the rest collapse into "+N". */
internal const val SummaryReactorLimit = 3

/**
 * Seven 48dp touch targets (six tapbacks plus custom emoji) have to sit in one
 * pill at the narrowest supported width, so the drawn circle is 36dp while
 * [minimumInteractiveComponentSize] keeps the target at 48dp. Adding an eighth
 * overflows a 360dp screen, which is why full actions stay on long-press.
 */
private val PickerEmojiSize = 36.dp
private val SummaryEmojiSize = 36.dp
private val SummaryAvatarSize = 18.dp

/** Avatars tuck under each other so a column stays under its own emoji. */
private val SummaryAvatarOverlap = (-4).dp
private val PickerMaxWidth = 360.dp

/** One reactor behind a grouped emoji in the "who reacted" summary. */
internal data class ReactionReactor(
    val name: String,
    val address: String?,
    val isFromMe: Boolean,
)

/** Distinct emoji plus everyone who sent it, in first-reacted order. */
internal data class ReactionGroup(
    val emoji: String,
    val reactors: List<ReactionReactor>,
)

/**
 * Groups active tapbacks by emoji so the summary shows one bubble per
 * reaction with its reactors beneath, the way Apple Messages does. A sender
 * only holds one active tapback, so the same reactor never appears twice in a
 * group; [resolveName] maps a handle to a contact name and falls back to the
 * raw address when contacts have not resolved yet.
 */
internal fun groupReactions(
    reactions: List<MessageReactionUi>,
    resolveName: (String) -> String? = { null },
): List<ReactionGroup> {
    val groups = LinkedHashMap<String, MutableList<ReactionReactor>>()
    reactions.forEach { reaction ->
        val reactor = ReactionReactor(
            name = if (reaction.isFromMe) {
                SelfReactorName
            } else {
                reaction.senderAddress?.let { resolveName(it) ?: it } ?: "Unknown"
            },
            address = reaction.senderAddress,
            isFromMe = reaction.isFromMe,
        )
        val bucket = groups.getOrPut(reaction.emoji) { mutableListOf() }
        val alreadyPresent = bucket.any {
            it.isFromMe == reactor.isFromMe && it.address == reactor.address
        }
        if (!alreadyPresent) bucket += reactor
    }
    return groups.map { (emoji, reactors) -> ReactionGroup(emoji, reactors) }
}

/** My active tapback, used to mark the picker's selected emoji. */
internal fun myReactionEmoji(reactions: List<MessageReactionUi>): String? =
    reactions.lastOrNull { it.isFromMe }?.emoji

/** "You and Alex reacted ❤️" — the spoken form of one summary bubble. */
internal fun reactionGroupLabel(group: ReactionGroup): String =
    "${joinReactorNames(group.reactors.map { it.name })} reacted ${group.emoji}"

private fun joinReactorNames(names: List<String>): String = when (names.size) {
    0 -> "Nobody"
    1 -> names[0]
    2 -> "${names[0]} and ${names[1]}"
    else -> "${names.dropLast(1).joinToString(", ")}, and ${names.last()}"
}

/**
 * Centered reaction surface: a "who reacted" card above a floating tapback
 * pill, matching the Apple Messages quick-reaction treatment. Choosing a
 * tapback goes through the same [onReact] contract the action sheet uses.
 * The rest of the message actions stay on long-press.
 */
@Composable
internal fun TapbackPickerOverlay(
    reactions: List<MessageReactionUi>,
    onReact: (Int, String?) -> Unit,
    onCustomReaction: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    resolveName: (String) -> String? = { null },
) {
    val groups = groupReactions(reactions, resolveName)
    val selected = remember(reactions) { myReactionEmoji(reactions) }
    // Reduce motion means no grow-in at all, so the surface starts settled
    // rather than animating from a spec that has been swapped for snap().
    val settled = LocalReduceMotion.current
    val scaleSpec = fastSpatialSpec<Float>()
    val alphaSpec = fastEffectsSpec<Float>()
    val enter = remember(settled) { Animatable(if (settled) 1f else 0f) }
    val scale = remember(settled) { Animatable(if (settled) 1f else 0.88f) }
    LaunchedEffect(settled) {
        if (enter.value != 1f) enter.animateTo(1f, alphaSpec)
    }
    LaunchedEffect(settled) {
        if (scale.value != 1f) scale.animateTo(1f, scaleSpec)
    }

    BackHandler { onDismiss() }
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.32f * enter.value))
            // The scrim owns dismissal; the card below consumes its own taps.
            .pointerInput(Unit) { detectTapGestures { onDismiss() } }
            .semantics {
                paneTitle = "Reactions"
                isTraversalGroup = true
                customActions = listOf(
                    CustomAccessibilityAction("Dismiss reactions") {
                        onDismiss()
                        true
                    },
                )
            },
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier
                .padding(horizontal = 16.dp)
                .pointerInput(Unit) { detectTapGestures { } }
                .graphicsLayer {
                    alpha = enter.value
                    scaleX = scale.value
                    scaleY = scale.value
                },
        ) {
            if (groups.isNotEmpty()) {
                ReactionSummaryCard(groups = groups)
            }
            TapbackPickerBar(
                selectedEmoji = selected,
                onReact = onReact,
                onCustomReaction = onCustomReaction,
            )
        }
    }
}

/** Who reacted: one emoji bubble per distinct tapback with its reactors. */
@Composable
private fun ReactionSummaryCard(
    groups: List<ReactionGroup>,
    modifier: Modifier = Modifier,
) {
    Surface(
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.surfaceContainerLowest,
        tonalElevation = 2.dp,
        shadowElevation = 8.dp,
        modifier = modifier.widthIn(max = PickerMaxWidth),
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.Top,
            modifier = Modifier
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 12.dp),
        ) {
            groups.forEach { group ->
                ReactionSummaryColumn(group = group)
            }
        }
    }
}

@Composable
private fun ReactionSummaryColumn(group: ReactionGroup, modifier: Modifier = Modifier) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
        modifier = modifier.clearAndSetSemantics {
            contentDescription = reactionGroupLabel(group)
        },
    ) {
        Box(
            modifier = Modifier
                .size(SummaryEmojiSize)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceContainerHigh),
            contentAlignment = Alignment.Center,
        ) {
            Text(text = group.emoji, fontSize = 17.sp)
        }
        val shown = group.reactors.take(SummaryReactorLimit)
        val overflow = group.reactors.size - shown.size
        Row(
            horizontalArrangement = Arrangement.spacedBy(SummaryAvatarOverlap),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            shown.forEach { reactor -> ReactorAvatar(reactor = reactor) }
            if (overflow > 0) {
                Text(
                    text = "+$overflow",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 6.dp),
                )
            }
        }
    }
}

@Composable
private fun ReactorAvatar(reactor: ReactionReactor, modifier: Modifier = Modifier) {
    ChatAvatar(
        title = reactor.name,
        avatarColor = avatarColorFor(reactor.address ?: reactor.name),
        size = SummaryAvatarSize,
        avatarPath = rememberContactAvatarPath(reactor.address),
        modifier = modifier,
    )
}

/** Floating pill: the six protocol tapbacks plus custom emoji entry. */
@Composable
private fun TapbackPickerBar(
    selectedEmoji: String?,
    onReact: (Int, String?) -> Unit,
    onCustomReaction: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surfaceContainerLowest,
        tonalElevation = 2.dp,
        shadowElevation = 8.dp,
        modifier = modifier.widthIn(max = PickerMaxWidth),
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(1.dp),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 6.dp, vertical = 6.dp)
                .semantics { isTraversalGroup = true },
        ) {
            ActionTapbacks.forEachIndexed { index, emoji ->
                TapbackEmojiButton(
                    emoji = emoji,
                    selected = selectedEmoji == emoji,
                    onClick = { onReact(index, null) },
                )
            }
            PickerIconButton(
                icon = Icons.Filled.AddReaction,
                label = "Custom reaction",
                selected = selectedEmoji != null && selectedEmoji !in ActionTapbacks,
                onClick = onCustomReaction,
            )
        }
    }
}

@Composable
private fun TapbackEmojiButton(
    emoji: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val background = if (selected) {
        MaterialTheme.colorScheme.primaryContainer
    } else {
        Color.Transparent
    }
    Box(
        modifier = modifier
            .minimumInteractiveComponentSize()
            .size(PickerEmojiSize)
            .clip(CircleShape)
            .background(background)
            .clickable(role = Role.Button, onClick = onClick)
            .semantics {
                contentDescription = tapbackContentDescription(emoji)
                if (selected) stateDescription = "Selected"
            },
        contentAlignment = Alignment.Center,
    ) {
        // The label already names the tapback; the glyph would repeat it.
        Text(text = emoji, fontSize = 18.sp, modifier = Modifier.clearAndSetSemantics {})
    }
}

@Composable
private fun PickerIconButton(
    icon: ImageVector,
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val background = if (selected) {
        MaterialTheme.colorScheme.primaryContainer
    } else {
        MaterialTheme.colorScheme.surfaceContainerHigh
    }
    val iconTint = if (selected) {
        MaterialTheme.colorScheme.onPrimaryContainer
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }
    Box(
        modifier = modifier
            .minimumInteractiveComponentSize()
            .size(PickerEmojiSize)
            .clip(CircleShape)
            .background(background)
            .clickable(role = Role.Button, onClick = onClick)
            .semantics {
                contentDescription = label
                if (selected) stateDescription = "Selected"
            },
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = iconTint,
            modifier = Modifier.size(18.dp),
        )
    }
}

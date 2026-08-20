package app.openbubbles.nativeapp.ui.chat

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Done
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.compose.ui.graphics.Color
import app.openbubbles.nativeapp.data.AttachmentMeta
import app.openbubbles.nativeapp.ui.chat.interactive.InteractiveBalloon
import app.openbubbles.nativeapp.data.MessageItem
import app.openbubbles.nativeapp.data.MessageStatus
import app.openbubbles.nativeapp.data.RichLinkPreview
import app.openbubbles.nativeapp.data.StickerPlacement
import app.openbubbles.nativeapp.data.displayTextForRichLink
import app.openbubbles.nativeapp.ui.effects.isInvisibleInk
import app.openbubbles.nativeapp.ui.common.ChatAvatar
import app.openbubbles.nativeapp.ui.common.avatarColorFor
import app.openbubbles.nativeapp.ui.common.rememberContactAvatarPath
import app.openbubbles.nativeapp.ui.common.rememberDecodedImage
import app.openbubbles.nativeapp.ui.common.rememberDecodedBytes
import app.openbubbles.nativeapp.ui.theme.fastSpatialSpec
import app.openbubbles.nativeapp.ui.theme.OpenBubblesTheme
import app.openbubbles.nativeapp.ui.tooling.LightDarkPreviews
import app.openbubbles.nativeapp.ui.theme.smsServiceColors
import java.io.File
import java.text.DateFormat
import java.util.Date
import kotlinx.coroutines.delay
import kotlin.math.PI
import kotlin.math.roundToInt

/** Outer bubble radius (tail-free modern iMessage look). */
private val BubbleCornerRadius = 20.dp

/** Tightened radius on sides that touch a same-author neighbor. */
private val GroupedCornerRadius = 8.dp

/** Bubbles never exceed 78% of the available width (or this absolute cap). */
private val BubbleMaxWidthCap = 320.dp

/** Sender avatar beside incoming group-chat bubbles (iOS-sized). */
private val SenderAvatarSize = 28.dp

/** Gap between the avatar gutter and the bubble stack. */
private val SenderAvatarSpacing = 8.dp

/** How far the tapback pill rises above the bubble's top edge. */
private val ReactionChipRise = 14.dp

/**
 * How far the pill hangs outside the bubble's outer edge — under the 12dp
 * transcript gutter so a bubble at the screen edge never clips the pill.
 */
private val ReactionChipOverhang = 10.dp

/** Extra row headroom so the overlapping tapback never crosses the item bounds. */
private val ReactionRowExtraTopPadding = 12.dp

/**
 * Caps the bubble column at ~78% of the width it is actually given, so
 * conversations breathe in any orientation, with an absolute cap so long lines
 * stay readable on wide displays.
 *
 * The input must be the width of the transcript, not of the screen. In a
 * list-detail layout the conversation occupies one pane, and measuring the
 * screen there yields a cap wider than the pane itself — the absolute cap keeps
 * it from overflowing, but every bubble runs edge to edge and the 78% breathing
 * room disappears exactly where space is tightest.
 */
private fun bubbleMaxWidth(availableWidth: Dp): Dp =
    (availableWidth * 0.78f).coerceAtMost(BubbleMaxWidthCap)

/**
 * Grouping-aware corner radii: edges facing a consecutive same-author
 * message tighten to 8dp; outer edges stay at 20dp.
 */
private fun bubbleShape(tightTop: Boolean, tightBottom: Boolean): RoundedCornerShape {
    val top = if (tightTop) GroupedCornerRadius else BubbleCornerRadius
    val bottom = if (tightBottom) GroupedCornerRadius else BubbleCornerRadius
    return RoundedCornerShape(topStart = top, topEnd = top, bottomStart = bottom, bottomEnd = bottom)
}

/**
 * Bubble container/content pair. Outgoing iMessage uses the theme primary;
 * outgoing SMS uses the fixed green service identity (the green-bubble
 * metaphor is business-critical, so it never follows dynamic color).
 * Incoming bubbles sit on the surfaceContainer ramp like the rest of the app.
 */
@Composable
private fun bubbleColors(isFromMe: Boolean, smsChat: Boolean): Pair<Color, Color> = when {
    isFromMe && smsChat -> smsServiceColors().let { it.container to it.content }
    isFromMe -> MaterialTheme.colorScheme.primary to MaterialTheme.colorScheme.onPrimary
    else -> MaterialTheme.colorScheme.surfaceContainerHigh to MaterialTheme.colorScheme.onSurface
}

@Composable
private fun StickerOverlay(
    sticker: StickerPlacement,
    contentSize: androidx.compose.ui.unit.IntSize,
    attachmentFile: (String) -> File?,
    onDownloadSticker: (String) -> Unit,
) {
    val file = remember(sticker.attachmentGuid, sticker.downloaded) {
        attachmentFile(sticker.attachmentGuid)
    }
    LaunchedEffect(sticker.attachmentGuid, file) {
        if (file == null) onDownloadSticker(sticker.attachmentGuid)
    }
    val decoded = rememberDecodedImage(file, maxDimensionPx = 384) ?: return
    val scale = sticker.scale.toFloat().coerceIn(0.25f, 3f)
    val size = 88.dp * scale
    Image(
        bitmap = decoded.image,
        contentDescription = "Sticker",
        contentScale = ContentScale.Fit,
        modifier = Modifier
            .size(size)
            .offset {
                IntOffset(
                    (sticker.normalizedX * contentSize.width - size.roundToPx() / 2.0).roundToInt(),
                    (sticker.normalizedY * contentSize.height - size.roundToPx() / 2.0).roundToInt(),
                )
            }
            .graphicsLayer(rotationZ = (sticker.rotation * 180.0 / PI).toFloat())
            .zIndex(4f),
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun RichLinkCard(
    preview: RichLinkPreview,
    modifier: Modifier = Modifier,
    embedded: Boolean = false,
    onLongPress: (() -> Unit)? = null,
) {
    val uriHandler = LocalUriHandler.current
    val previewImage = rememberDecodedBytes(preview.imageBytes, maxDimensionPx = 640)
    val previewIcon = rememberDecodedBytes(preview.iconBytes, maxDimensionPx = 160)
    val openLink: () -> Unit = {
        try {
            uriHandler.openUri(preview.url)
        } catch (_: IllegalArgumentException) {
        }
    }
    Surface(
        shape = if (embedded) RoundedCornerShape(0.dp) else RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHighest,
        contentColor = MaterialTheme.colorScheme.onSurface,
        border = if (embedded) {
            null
        } else {
            BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
        },
        modifier = modifier.then(
            if (onLongPress != null) {
                Modifier.combinedClickable(
                    onClick = openLink,
                    onLongClick = onLongPress,
                    onLongClickLabel = "Message actions",
                )
            } else {
                Modifier.clickable(onClick = openLink)
            },
        ),
    ) {
        Column {
            if (previewImage != null) {
                Image(
                    bitmap = previewImage.image,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(previewImage.aspectRatio.coerceIn(1.25f, 2.2f)),
                )
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(96.dp)
                        .background(MaterialTheme.colorScheme.surfaceContainerHigh),
                    contentAlignment = Alignment.Center,
                ) {
                    if (previewIcon != null) {
                        Image(
                            bitmap = previewIcon.image,
                            contentDescription = null,
                            contentScale = ContentScale.Fit,
                            modifier = Modifier.size(52.dp),
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Filled.Link,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(34.dp),
                        )
                    }
                }
            }
            Column(
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 11.dp),
                verticalArrangement = Arrangement.spacedBy(3.dp),
            ) {
                Text(
                    text = preview.title ?: preview.displayHost,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                preview.summary?.let { summary ->
                    Text(
                        text = summary,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Text(
                    text = preview.displayHost,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

/**
 * A single conversation row: message bubble (mine right / theirs left),
 * centered caption for group events, an italic row for unsent messages,
 * attachment bubbles (image / video / file) above the text, an "Edited"
 * label, reaction chip overlapping the corner, an optional sender name for
 * group chats, and an optional delivery status row under my latest outgoing
 * message.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MessageBubble(
    message: MessageItem,
    showStatus: Boolean,
    modifier: Modifier = Modifier,
    showDeliveryTimestamp: Boolean = false,
    tightTop: Boolean = false,
    tightBottom: Boolean = false,
    showSenderName: Boolean = false,
    /**
     * Group chats reserve an avatar gutter beside every incoming bubble so
     * runs stay aligned; the avatar itself draws only where [showAvatar] is
     * set (the bottom bubble of a sender's run, the way Apple Messages
     * anchors it).
     */
    showAvatarGutter: Boolean = false,
    showAvatar: Boolean = false,
    attachmentFile: (String) -> File? = { null },
    onOpenAttachment: (String) -> Unit = {},
    onDownloadAttachment: (AttachmentMeta) -> Unit = {},
    senderDisplayName: String? = null,
    replyQuote: ReplyQuote? = null,
    /** Tap on the quoted original: scroll-to-original or open the thread pane. */
    onReplyQuoteTap: () -> Unit = {},
    /** Number of direct replies rooted at this message. */
    replyCount: Int = 0,
    /** Opens the focused thread rooted at this message. */
    onReplyCountTap: () -> Unit = {},
    onDownloadSticker: (String) -> Unit = {},
    onLongPressPart: ((Long) -> Unit)? = null,
    /** Slide the bubble toward the start edge to begin an inline reply. */
    onSwipeReply: ((Long) -> Unit)? = null,
    /** True when this conversation is carrier SMS — outgoing bubbles go green. */
    smsChat: Boolean = false,
) {
    when {
        message.isGroupEvent -> {
            GroupEventRow(text = message.text, modifier = modifier)
            return
        }
        message.unsent -> {
            UnsentRow(
                text = if (message.isFromMe) "You unsent a message"
                else "${senderDisplayName ?: message.senderAddress ?: "Someone"} unsent a message",
                modifier = modifier,
            )
            return
        }
    }
    val shape = bubbleShape(tightTop, tightBottom)
    val attachments = message.attachmentMetas.ifEmpty {
        listOfNotNull(message.attachmentMeta)
    }
    val richLink = message.richLink
    val interactivePayload = message.interactivePayload
    val displayText = if (richLink != null) {
        displayTextForRichLink(message.text, richLink.url)
    } else {
        message.text
    }
    val showTextBubble =
        (displayText.isNotBlank() || !message.subject.isNullOrBlank()) && interactivePayload == null
    val invisibleInk = isInvisibleInk(message.expressiveSendStyleId)
    val embedRichLink = richLink != null && showTextBubble && !invisibleInk
    // Attachment-only messages take the grouping shape directly; stacked
    // attachment + text keeps the standalone attachment radius.
    val attachmentShape = if (attachments.size == 1 && message.text.isBlank() && message.subject.isNullOrBlank()) shape else null
    val attachmentParts = attachments.mapTo(hashSetOf()) { it.partIndex }
    val textPart = message.replyPartLocators.keys.firstOrNull { it !in attachmentParts } ?: 0L
    val defaultReplyPart = when {
        showTextBubble -> textPart
        attachments.isNotEmpty() -> attachments.last().partIndex
        else -> textPart
    }
    val avatarGutter = showAvatarGutter && !message.isFromMe
    // Pop the tapback only when it lands while the row is on screen; rows that
    // scroll in already reacted render it settled.
    val reactionPopsIn = remember(message.id) { message.reactionEmoji == null }
    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp)
            .padding(
                top = when {
                    message.reactionEmoji != null -> 3.dp + ReactionRowExtraTopPadding
                    message.replyToGuid != null -> 9.dp
                    else -> 3.dp
                },
                bottom = if (replyCount > 0) 8.dp else 3.dp,
            ),
    ) {
        // Measured from the row itself, so a conversation rendered as the detail
        // pane sizes its bubbles against that pane rather than the whole display.
        val gutterWidth = if (avatarGutter) SenderAvatarSize + SenderAvatarSpacing else 0.dp
        val maxBubbleWidth = bubbleMaxWidth(maxWidth - gutterWidth)
        var contentSize by remember(message.id) { mutableStateOf(androidx.compose.ui.unit.IntSize.Zero) }
        Column(modifier = Modifier.fillMaxWidth()) {
        replyQuote?.let { quote ->
            // The quote keeps the original message's transcript side. The
            // thread rail itself is always anchored to the far-left transcript
            // gutter, matching Apple Messages rather than following my quote
            // out toward the screen edge.
            ReplyQuotePreview(
                quote = quote,
                smsChat = smsChat,
                onOpen = onReplyQuoteTap,
                maxWidth = maxBubbleWidth,
                incomingGutter = if (showAvatarGutter) {
                    SenderAvatarSize + SenderAvatarSpacing
                } else {
                    0.dp
                },
            )
        }
        SwipeToReplyBox(
            enabled = onSwipeReply != null,
            onReply = { onSwipeReply?.invoke(defaultReplyPart) },
            modifier = Modifier
                .fillMaxWidth()
                .then(
                    if (onSwipeReply != null) {
                        Modifier.semantics {
                            customActions = listOf(
                                CustomAccessibilityAction("Reply") {
                                    onSwipeReply.invoke(defaultReplyPart)
                                    true
                                },
                            )
                        }
                    } else {
                        Modifier
                    },
                ),
            contentAlignment = if (message.isFromMe) Alignment.CenterEnd else Alignment.CenterStart,
        ) {
        // The avatar anchors to the bottom of the bubble stack, like iOS.
        Row(verticalAlignment = Alignment.Bottom) {
        if (avatarGutter) {
            Box(modifier = Modifier.size(SenderAvatarSize)) {
                if (showAvatar) {
                    val avatarTitle = senderDisplayName ?: message.senderAddress.orEmpty()
                    ChatAvatar(
                        title = avatarTitle,
                        avatarColor = avatarColorFor(message.senderAddress ?: avatarTitle),
                        size = SenderAvatarSize,
                        avatarPath = rememberContactAvatarPath(message.senderAddress),
                    )
                }
            }
            Spacer(Modifier.width(SenderAvatarSpacing))
        }
        Box(
            modifier = Modifier
                .widthIn(max = maxBubbleWidth)
                .onSizeChanged { contentSize = it },
        ) {
            Column(
                horizontalAlignment = if (message.isFromMe) Alignment.End else Alignment.Start,
                verticalArrangement = Arrangement.spacedBy(3.dp),
            ) {
            if (showSenderName && !message.isFromMe) {
                Text(
                    text = senderDisplayName ?: message.senderAddress.orEmpty(),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 6.dp),
                )
            }
            interactivePayload?.let { payload ->
                InteractiveBalloon(
                    payload = payload,
                    onLongPress = onLongPressPart?.let { callback -> { callback(textPart) } },
                )
            }
            attachments.forEachIndexed { index, attachment ->
                Box {
                    AttachmentContent(
                        attachment = attachment,
                        attachmentFile = attachmentFile,
                        onOpenAttachment = onOpenAttachment,
                        onDownloadAttachment = onDownloadAttachment,
                        shape = attachmentShape,
                        fromMe = message.isFromMe,
                        smsChat = smsChat,
                        onLongPress = if (attachment.livePhotoMotionGuid != null) {
                            onLongPressPart?.let { callback -> { callback(attachment.partIndex) } }
                        } else {
                            null
                        },
                        modifier = if (attachment.livePhotoMotionGuid == null && onLongPressPart != null) {
                            Modifier.combinedClickable(
                                // Audio plays inline; the viewer stays for
                                // everything else.
                                onClick = {
                                    if (!attachment.isAudio) onOpenAttachment(attachment.guid)
                                },
                                onLongClick = { onLongPressPart(attachment.partIndex) },
                            )
                        } else {
                            Modifier
                        },
                    )
                    message.reactionEmoji?.takeIf { index == attachments.lastIndex }?.let { emoji ->
                        ReactionChip(
                            emoji = emoji,
                            isFromMe = message.isFromMe,
                            popIn = reactionPopsIn,
                            modifier = Modifier.align(
                                if (message.isFromMe) Alignment.TopEnd else Alignment.TopStart,
                            ),
                        )
                    }
                }
            }
            message.uploadProgress?.let { progress ->
                UploadProgressRow(done = progress.first, total = progress.second)
            }
            if (embedRichLink) {
                Box {
                    CombinedTextAndLinkBubble(
                        text = displayText,
                        preview = checkNotNull(richLink),
                        shape = shape,
                        isFromMe = message.isFromMe,
                        smsChat = smsChat,
                        onLongPress = onLongPressPart?.let { callback ->
                            { callback(textPart) }
                        },
                    )
                    if (attachments.isEmpty()) {
                        message.reactionEmoji?.let { emoji ->
                            ReactionChip(
                                emoji = emoji,
                                isFromMe = message.isFromMe,
                                popIn = reactionPopsIn,
                                modifier = Modifier.align(
                                    if (message.isFromMe) Alignment.TopEnd else Alignment.TopStart,
                                ),
                            )
                        }
                    }
                }
            } else {
                richLink?.let { preview ->
                    Box {
                        RichLinkCard(
                            preview = preview,
                            onLongPress = onLongPressPart?.let { callback ->
                                { callback(textPart) }
                            },
                        )
                        if (attachments.isEmpty() && !showTextBubble) {
                            message.reactionEmoji?.let { emoji ->
                                ReactionChip(
                                    emoji = emoji,
                                    isFromMe = message.isFromMe,
                                    popIn = reactionPopsIn,
                                    modifier = Modifier.align(
                                        if (message.isFromMe) Alignment.TopEnd else Alignment.TopStart,
                                    ),
                                )
                            }
                        }
                    }
                }
                if (showTextBubble) {
                    Box {
                        if (invisibleInk) {
                            InvisibleInkBubble(
                                message = message,
                                text = displayText,
                                shape = shape,
                                smsChat = smsChat,
                                onLongPress = onLongPressPart?.let { callback ->
                                    { callback(textPart) }
                                },
                            )
                        } else {
                            val (bubbleColor, bubbleContent) = bubbleColors(message.isFromMe, smsChat)
                            Surface(
                                shape = shape,
                                color = bubbleColor,
                                contentColor = bubbleContent,
                                modifier = if (onLongPressPart != null) {
                                    Modifier.combinedClickable(
                                        onClick = {},
                                        onLongClick = { onLongPressPart(textPart) },
                                    )
                                } else {
                                    Modifier
                                },
                            ) {
                                Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
                                    MessageSubject(message.subject)
                                    if (displayText.isNotBlank()) {
                                        Text(text = displayText, style = MaterialTheme.typography.bodyLarge)
                                    }
                                }
                            }
                        }
                        if (attachments.isEmpty()) {
                            message.reactionEmoji?.let { emoji ->
                                ReactionChip(
                                    emoji = emoji,
                                    isFromMe = message.isFromMe,
                                    popIn = reactionPopsIn,
                                    modifier = Modifier.align(
                                        if (message.isFromMe) Alignment.TopEnd else Alignment.TopStart,
                                    ),
                                )
                            }
                        }
                    }
                }
            }
            if (replyCount > 0) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.padding(horizontal = 6.dp),
                ) {
                    if (message.edited) {
                        Text(
                            text = "Edited",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    if (showDeliveryTimestamp && message.isFromMe) {
                        deliveryTimestamp(message)?.let { timestamp ->
                            Text(
                                text = "${timestamp.label} ${DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(timestamp.epochMs))}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    if (showStatus && message.isFromMe) {
                        MessageStatusRow(status = message.status)
                    }
                    Text(
                        text = if (replyCount == 1) "1 Reply ›" else "$replyCount Replies ›",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier
                            .clickable(
                                onClickLabel = "Open reply thread",
                                role = Role.Button,
                                onClick = onReplyCountTap,
                            )
                            .padding(vertical = 2.dp),
                    )
                }
            } else {
                if (message.edited) {
                    Text(
                        text = "Edited",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 6.dp),
                    )
                }
                if (showDeliveryTimestamp && message.isFromMe) {
                    deliveryTimestamp(message)?.let { timestamp ->
                        Text(
                            text = "${timestamp.label} ${DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(timestamp.epochMs))}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 6.dp),
                        )
                    }
                }
                if (showStatus && message.isFromMe) {
                    MessageStatusRow(status = message.status)
                }
            }
            }
            message.stickers.forEach { sticker ->
                StickerOverlay(
                    sticker = sticker,
                    contentSize = contentSize,
                    attachmentFile = attachmentFile,
                    onDownloadSticker = onDownloadSticker,
                )
            }
        }
        }
        }
        }
    }
}

internal data class DeliveryTimestamp(val label: String, val epochMs: Long)

internal fun deliveryTimestamp(message: MessageItem): DeliveryTimestamp? = when {
    message.dateRead != null -> DeliveryTimestamp("Read", message.dateRead)
    message.dateDelivered != null -> DeliveryTimestamp("Delivered", message.dateDelivered)
    else -> null
}

@Composable
private fun MessageSubject(subject: String?) {
    val value = subject?.trim().takeIf { !it.isNullOrEmpty() } ?: return
    Text(
        text = value,
        style = MaterialTheme.typography.labelLarge,
        modifier = Modifier.padding(bottom = 3.dp),
    )
}

/**
 * Invisible-ink bubble (com.apple.MobileSMS.expressivesend.invisibleink): the
 * text renders blurred until tapped, then reveals for 3s and re-hides.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun CombinedTextAndLinkBubble(
    text: String,
    preview: RichLinkPreview,
    shape: RoundedCornerShape,
    isFromMe: Boolean,
    smsChat: Boolean,
    modifier: Modifier = Modifier,
    onLongPress: (() -> Unit)? = null,
) {
    val (bubbleColor, bubbleContent) = bubbleColors(isFromMe, smsChat)
    Surface(
        shape = shape,
        color = bubbleColor,
        contentColor = bubbleContent,
        modifier = modifier.then(
            if (onLongPress != null) {
                Modifier.combinedClickable(
                    onClick = {},
                    onLongClick = onLongPress,
                )
            } else {
                Modifier
            },
        ),
    ) {
        Column {
            Text(
                text = text,
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            )
            RichLinkCard(
                preview = preview,
                embedded = true,
                onLongPress = onLongPress,
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun InvisibleInkBubble(
    message: MessageItem,
    text: String,
    shape: RoundedCornerShape,
    modifier: Modifier = Modifier,
    smsChat: Boolean = false,
    onLongPress: (() -> Unit)? = null,
) {
    var revealed by remember(message.id) { mutableStateOf(false) }
    LaunchedEffect(revealed, message.id) {
        if (revealed) {
            delay(3_000)
            revealed = false
        }
    }
    val (bubbleColor, bubbleContent) = bubbleColors(message.isFromMe, smsChat)
    Surface(
        shape = shape,
        color = bubbleColor,
        contentColor = bubbleContent,
        modifier = modifier.then(
            if (onLongPress != null) {
                Modifier.combinedClickable(
                    onClick = { revealed = !revealed },
                    onLongClick = onLongPress,
                )
            } else {
                Modifier.clickable { revealed = !revealed }
            },
        ),
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier
                .padding(horizontal = 14.dp, vertical = 10.dp)
                .then(
                    // RenderEffect blur is a no-op below Android 12.
                    if (!revealed) Modifier.blur(12.dp) else Modifier,
                ),
        )
    }
}

/** Connector canvas footprint between the quote and the reply bubble. */
private val ReplyConnectorWidth = 28.dp
private val ReplyConnectorHeight = 14.dp

internal data class ReplyConnectorGeometry(
    val start: Offset,
    val control: Offset,
    val end: Offset,
)

/**
 * Quarter-hook between the quote's bottom edge and the reply's rounded top
 * corner. Quote and reply are flush on the transcript's outer edge; the curve
 * leaves the quote vertically [startInsetFromOuter] in from that edge and
 * lands horizontally [endInsetFromOuter] in, aiming into the reply's corner
 * radius. The control point directly under the start is what keeps the launch
 * vertical and the landing horizontal.
 */
internal fun replyConnectorGeometry(
    width: Float,
    height: Float,
    startInsetFromOuter: Float,
    endInsetFromOuter: Float,
    outerEdgeOnRight: Boolean,
): ReplyConnectorGeometry {
    fun fromOuter(inset: Float) = if (outerEdgeOnRight) width - inset else inset
    val start = Offset(fromOuter(startInsetFromOuter), 0f)
    return ReplyConnectorGeometry(
        start = start,
        control = Offset(start.x, height),
        end = Offset(fromOuter(endInsetFromOuter), height),
    )
}

/**
 * Original-message bubble stacked above a reply, the way Apple Messages
 * renders threaded replies: a solid bubble in the original sender's own
 * color carrying the full original text, kept on the original's transcript
 * side (their quote left, mine right — even when the reply sits on the other
 * side), with a curved connector dropping from its outer bottom corner
 * toward the reply. Tapping shows the original message.
 */
@Composable
private fun ReplyQuotePreview(
    quote: ReplyQuote,
    smsChat: Boolean,
    onOpen: () -> Unit,
    maxWidth: Dp,
    incomingGutter: Dp,
    modifier: Modifier = Modifier,
) {
    val (bubbleColor, bubbleContent) = bubbleColors(quote.fromMe, smsChat)
    Column(modifier = modifier.fillMaxWidth()) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = if (quote.fromMe) 0.dp else incomingGutter),
            contentAlignment = if (quote.fromMe) Alignment.CenterEnd else Alignment.CenterStart,
        ) {
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = bubbleColor.copy(alpha = 0.48f),
                contentColor = bubbleContent.copy(alpha = 0.78f),
                modifier = Modifier
                    .widthIn(max = maxWidth * 0.84f)
                    .clickable(
                        onClickLabel = "Show original message",
                        role = Role.Button,
                        onClick = onOpen,
                    ),
            ) {
                Column(modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)) {
                    quote.senderName?.let { name ->
                        Text(
                            text = name,
                            style = MaterialTheme.typography.labelSmall,
                            color = bubbleContent.copy(alpha = 0.68f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    Text(
                        text = quote.text,
                        style = MaterialTheme.typography.labelMedium,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
        val connectorColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.72f)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = incomingGutter),
            contentAlignment = Alignment.CenterStart,
        ) {
            Canvas(
                modifier = Modifier
                    .offset(x = 8.dp, y = (-1).dp)
                    .size(width = ReplyConnectorWidth, height = ReplyConnectorHeight),
            ) {
                // The rail stays in the far-left transcript gutter. For an
                // incoming quoted message it curls outward from that quote;
                // for my quoted message it curls the opposite way, into the
                // incoming reply's top-left corner. Using the same direction
                // for both creates the detached backwards hook seen on device.
                val geometry = replyConnectorGeometry(
                    width = size.width,
                    height = size.height + 8.dp.toPx(),
                    startInsetFromOuter = 18.dp.toPx(),
                    endInsetFromOuter = 6.dp.toPx(),
                    outerEdgeOnRight = quote.fromMe,
                )
                drawPath(
                    path = Path().apply {
                        moveTo(geometry.start.x, geometry.start.y)
                        quadraticTo(
                            geometry.control.x, geometry.control.y,
                            geometry.end.x, geometry.end.y,
                        )
                    },
                    color = connectorColor,
                    style = Stroke(width = 1.5.dp.toPx(), cap = StrokeCap.Round),
                )
            }
        }
        Spacer(Modifier.height(4.dp))
    }
}

/**
 * Physical x-direction from the tapback pill toward the bubble it reacts to.
 * The pill hangs off the bubble's outer top corner (start for incoming, end
 * for outgoing), so the tail always steps back toward the bubble body: right
 * for an LTR incoming bubble, left for LTR outgoing, mirrored under RTL.
 */
internal fun reactionTailDirection(isFromMe: Boolean, isLtr: Boolean): Float =
    if (isFromMe == isLtr) -1f else 1f

/**
 * iOS-style tapback: a pill overlapping the bubble's top corner with a
 * two-dot thought-bubble tail descending toward the bubble, popping in on a
 * spatial spring when the reaction lands while visible.
 */
@Composable
private fun ReactionChip(
    emoji: String,
    isFromMe: Boolean,
    modifier: Modifier = Modifier,
    popIn: Boolean = false,
) {
    val popSpec = fastSpatialSpec<Float>()
    val scale = remember { Animatable(if (popIn) 0.4f else 1f) }
    LaunchedEffect(Unit) {
        if (scale.value != 1f) scale.animateTo(1f, popSpec)
    }
    val towardBubble = reactionTailDirection(
        isFromMe = isFromMe,
        isLtr = LocalLayoutDirection.current == LayoutDirection.Ltr,
    )
    val fill = MaterialTheme.colorScheme.surface
    val outline = MaterialTheme.colorScheme.outlineVariant
    Surface(
        shape = CircleShape,
        color = fill,
        border = BorderStroke(1.dp, outline),
        modifier = modifier
            .offset(
                x = if (isFromMe) ReactionChipOverhang else -ReactionChipOverhang,
                y = -ReactionChipRise,
            )
            .graphicsLayer {
                scaleX = scale.value
                scaleY = scale.value
                // Grow out of the tail corner, like iOS.
                transformOrigin = TransformOrigin(if (towardBubble > 0f) 0.8f else 0.2f, 1f)
            }
            .drawBehind {
                // Two-dot tail: a larger dot tangent under the pill's inner
                // edge, a smaller one below it, stepping toward the bubble.
                val strokeWidth = 1.dp.toPx()
                val bigRadius = 2.5.dp.toPx()
                val smallRadius = 1.5.dp.toPx()
                val big = Offset(
                    size.width / 2f + towardBubble * (size.width / 2f - bigRadius),
                    size.height - bigRadius / 2f,
                )
                val small = Offset(
                    size.width / 2f + towardBubble * (size.width / 2f + smallRadius),
                    size.height + smallRadius * 1.5f,
                )
                drawCircle(fill, bigRadius, big)
                drawCircle(outline, bigRadius, big, style = Stroke(strokeWidth))
                drawCircle(fill, smallRadius, small)
                drawCircle(outline, smallRadius, small, style = Stroke(strokeWidth))
            },
    ) {
        Text(
            text = emoji,
            fontSize = 13.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(4.dp),
        )
    }
}

/** Slim upload progress row shown under an outgoing attachment in flight. */
@Composable
fun UploadProgressRow(done: Long, total: Long, modifier: Modifier = Modifier) {
    val fraction = if (total > 0) (done.toFloat() / total.toFloat()).coerceIn(0f, 1f) else null
    Row(
        modifier = modifier
            .padding(horizontal = 6.dp, vertical = 2.dp)
            .fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        // Unknown total must not fake a determinate 10% — run indeterminate.
        if (fraction == null) {
            LinearProgressIndicator(
                strokeCap = androidx.compose.ui.graphics.StrokeCap.Round,
                modifier = Modifier
                    .weight(1f)
                    .height(4.dp),
            )
        } else {
            LinearProgressIndicator(
                progress = { fraction },
                strokeCap = androidx.compose.ui.graphics.StrokeCap.Round,
                modifier = Modifier
                    .weight(1f)
                    .height(4.dp),
            )
        }
        Text(
            text = if (fraction != null) {
                "Uploading ${(fraction * 100).toInt()}%"
            } else {
                "Uploading ${app.openbubbles.nativeapp.ui.common.formatBytes(done)}…"
            },
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** Delivery/read indicator shown under my most recent outgoing message. */
@Composable
fun MessageStatusRow(status: MessageStatus, modifier: Modifier = Modifier) {
    val (icon, tint, label) = when (status) {
        MessageStatus.SENDING -> Triple(Icons.Filled.Schedule, MaterialTheme.colorScheme.onSurfaceVariant, "Sending…")
        MessageStatus.SENT -> Triple(Icons.Filled.Done, MaterialTheme.colorScheme.onSurfaceVariant, "Sent")
        MessageStatus.DELIVERED -> Triple(Icons.Filled.DoneAll, MaterialTheme.colorScheme.onSurfaceVariant, "Delivered")
        MessageStatus.READ -> Triple(Icons.Filled.DoneAll, MaterialTheme.colorScheme.primary, "Read")
        MessageStatus.FAILED -> Triple(Icons.Filled.Warning, MaterialTheme.colorScheme.error, "Send failed")
    }
    Row(
        modifier = modifier.padding(top = 2.dp, start = 6.dp, end = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Icon(imageVector = icon, contentDescription = label, tint = tint, modifier = Modifier.size(14.dp))
        Text(text = label, style = MaterialTheme.typography.labelSmall, color = tint)
    }
}

/** Centered small caption for group events ("X added Y"). */
@Composable
fun GroupEventRow(text: String, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 48.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

/** Centered italic gray row for retracted messages. */
@Composable
fun UnsentRow(text: String, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 48.dp, vertical = 6.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium,
            fontStyle = FontStyle.Italic,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

/**
 * Timestamp divider above the first message of a time cluster (a new calendar
 * day or an hour-plus quiet gap): bold day, regular time — the Apple Messages
 * treatment. Extra space on top separates it from the previous cluster; the
 * tight bottom keeps it attached to the messages it labels.
 */
@Composable
fun TimeSeparatorRow(day: String, time: String, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(top = 16.dp, bottom = 4.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = buildAnnotatedString {
                withStyle(SpanStyle(fontWeight = FontWeight.SemiBold)) { append(day) }
                append(' ')
                append(time)
            },
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

// --------------------------------------------------------------------- previews

private const val PREVIEW_NOW_MILLIS = 1_760_000_000_000L

private fun previewMessage(
    text: String,
    isFromMe: Boolean,
    status: MessageStatus = MessageStatus.READ,
    reaction: String? = null,
) = MessageItem(
    id = 1L,
    text = text,
    isFromMe = isFromMe,
    date = PREVIEW_NOW_MILLIS,
    status = status,
    isGroupEvent = false,
    reactionEmoji = reaction,
)

@LightDarkPreviews
@Composable
private fun MessageBubblePreview() {
    OpenBubblesTheme(dynamicColor = false) {
        Column {
            MessageBubble(
                message = previewMessage("hey! still on for the hike saturday?", isFromMe = false, reaction = "❤️"),
                showStatus = false,
            )
            MessageBubble(
                message = previewMessage("yes! 8am trailhead, i'll drive", isFromMe = true, status = MessageStatus.READ)
                    .copy(edited = true),
                showStatus = true,
            )
            MessageBubble(
                message = previewMessage("surprise party at 8 — shhh!", isFromMe = false)
                    .copy(
                        expressiveSendStyleId = "com.apple.MobileSMS.expressivesend.invisibleink",
                    ),
                showStatus = false,
            )
            GroupEventRow("Mom added Dad")
            UnsentRow("You unsent a message")
            UnsentRow("Emma unsent a message")
            TimeSeparatorRow(day = "Today", time = "3:02 PM")
        }
    }
}

@LightDarkPreviews
@Composable
private fun MessageGroupingPreview() {
    OpenBubblesTheme(dynamicColor = false) {
        Column {
            MessageBubble(
                message = previewMessage("running 5 behind", isFromMe = false)
                    .copy(id = 1, senderAddress = "alex@icloud.com"),
                showStatus = false,
                showSenderName = true,
                showAvatarGutter = true,
                senderDisplayName = "Alex Chen",
            )
            MessageBubble(
                message = previewMessage("ok see you soon!", isFromMe = false)
                    .copy(id = 2, senderAddress = "alex@icloud.com"),
                showStatus = false,
                tightTop = true,
                showAvatarGutter = true,
                showAvatar = true,
                senderDisplayName = "Alex Chen",
            )
            MessageBubble(
                message = previewMessage("great", isFromMe = true, status = MessageStatus.DELIVERED).copy(id = 3),
                showStatus = true,
                tightBottom = true,
            )
            MessageBubble(
                message = previewMessage("grabbing coffee now, want anything?", isFromMe = true, status = MessageStatus.SENT).copy(id = 4),
                showStatus = false,
            )
        }
    }
}

@LightDarkPreviews
@Composable
private fun MessageReplyPreview() {
    OpenBubblesTheme(dynamicColor = false) {
        Column {
            MessageBubble(
                message = previewMessage(
                    "im trying i dont know why i thought i was calling you but a dream awake somehow and youtube man",
                    isFromMe = false,
                ).copy(id = 1),
                showStatus = false,
                replyCount = 1,
            )
            MessageBubble(
                message = previewMessage("That's so cute", isFromMe = true, status = MessageStatus.DELIVERED)
                    .copy(id = 2, replyToGuid = "root"),
                showStatus = true,
                replyQuote = ReplyQuote(
                    text = "im trying i dont know why i thought i was calling you but a dream awake somehow and youtube man",
                    fromMe = false,
                ),
            )
            MessageBubble(
                message = previewMessage("I love you more than the world itself", isFromMe = true, status = MessageStatus.DELIVERED)
                    .copy(id = 3),
                showStatus = false,
                replyCount = 2,
            )
            MessageBubble(
                message = previewMessage("how is that even possible", isFromMe = false)
                    .copy(id = 4, replyToGuid = "root2"),
                showStatus = false,
                replyQuote = ReplyQuote(
                    text = "I love you more than the world itself",
                    fromMe = true,
                ),
            )
        }
    }
}

@LightDarkPreviews
@Composable
private fun MessageAttachmentPreview() {
    OpenBubblesTheme(dynamicColor = false) {
        Column {
            MessageBubble(
                message = previewMessage("", isFromMe = false).copy(
                    attachmentMeta = AttachmentMeta(
                        guid = "p1", mime = "image/jpeg", name = "trailhead.jpg",
                        sizeBytes = 2_411_520L, isImage = true, downloaded = false,
                    ),
                    senderAddress = "alex@icloud.com",
                ),
                showStatus = false,
                senderDisplayName = "Alex Chen",
            )
            MessageBubble(
                message = previewMessage("found the itinerary too", isFromMe = false).copy(
                    attachmentMeta = AttachmentMeta(
                        guid = "p2", mime = "application/pdf", name = "Grand Canyon itinerary.pdf",
                        sizeBytes = 412_676L, isImage = false, downloaded = true,
                    ),
                ),
                showStatus = false,
            )
        }
    }
}

@LightDarkPreviews
@Composable
private fun MessageRichLinkPreview() {
    val preview = RichLinkPreview(
        url = "https://www.nps.gov/yose/index.htm",
        displayHost = "nps.gov",
        title = "Yosemite National Park",
        summary = "Plan the route, check conditions, and get ready for Saturday's hike.",
        imageBytes = null,
        imageMime = null,
        iconBytes = null,
        iconMime = null,
    )
    OpenBubblesTheme(dynamicColor = false) {
        Column {
            MessageBubble(
                message = previewMessage(
                    "Check the trail conditions https://www.nps.gov/yose/index.htm",
                    isFromMe = false,
                ).copy(richLink = preview),
                showStatus = false,
            )
            MessageBubble(
                message = previewMessage(
                    "https://www.nps.gov/yose/index.htm",
                    isFromMe = true,
                    status = MessageStatus.DELIVERED,
                ).copy(richLink = preview),
                showStatus = true,
            )
        }
    }
}

@LightDarkPreviews
@Composable
private fun MessageStatusRowPreview() {
    OpenBubblesTheme(dynamicColor = false) {
        Column {
            MessageStatusRow(status = MessageStatus.SENDING)
            MessageStatusRow(status = MessageStatus.SENT)
            MessageStatusRow(status = MessageStatus.DELIVERED)
            MessageStatusRow(status = MessageStatus.READ)
            MessageStatusRow(status = MessageStatus.FAILED)
        }
    }
}

@LightDarkPreviews
@Composable
private fun UploadProgressRowPreview() {
    OpenBubblesTheme(dynamicColor = false) {
        Column {
            UploadProgressRow(done = 1_200_000, total = 2_411_520)
            UploadProgressRow(done = 90_000, total = 0)
        }
    }
}

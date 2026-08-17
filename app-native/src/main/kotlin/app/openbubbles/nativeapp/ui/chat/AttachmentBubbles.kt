package app.openbubbles.nativeapp.ui.chat

import android.content.res.Configuration
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AudioFile
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.automirrored.filled.InsertDriveFile
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.VideoFile
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import app.openbubbles.nativeapp.data.AttachmentMeta
import app.openbubbles.nativeapp.ui.common.FallbackAspectRatio
import app.openbubbles.nativeapp.ui.common.formatBytes
import app.openbubbles.nativeapp.ui.common.rememberDecodedImage
import app.openbubbles.nativeapp.ui.common.sharedAttachment
import app.openbubbles.nativeapp.ui.theme.OpenBubblesTheme
import java.io.File

/** Widest an image bubble may be (photos dominate the bubble column). */
private val ImageBubbleMaxWidth = 260.dp

/** Cap so very tall screenshots do not take over the transcript. */
private val ImageBubbleMaxHeight = 340.dp

/** Standalone attachment radius, from the bubble family (see MessageBubble). */
private val AttachmentShape = RoundedCornerShape(18.dp)

/**
 * Renders one attachment in a message bubble: image (thumbnail, tap opens
 * the viewer), video (poster + play affordance), or a generic file row.
 * Undownloaded transfers show a download chip wired to the callback.
 * [shape] overrides the default radius (grouping-aware for text-free
 * messages); null falls back to [AttachmentShape].
 */
@Composable
fun AttachmentContent(
    attachment: AttachmentMeta,
    attachmentFile: (String) -> File?,
    onOpenAttachment: (String) -> Unit,
    onDownloadAttachment: (AttachmentMeta) -> Unit,
    modifier: Modifier = Modifier,
    shape: RoundedCornerShape? = null,
    /** Bubble identity, used by the audio player's color roles. */
    fromMe: Boolean = false,
    smsChat: Boolean = false,
) {
    val effectiveShape = shape ?: AttachmentShape
    when {
        attachment.isAudio -> VoiceMemoBubble(
            attachment = attachment,
            attachmentFile = attachmentFile,
            onDownloadAttachment = onDownloadAttachment,
            fromMe = fromMe,
            smsChat = smsChat,
            modifier = modifier,
            shape = effectiveShape,
        )
        attachment.isImage -> ImageAttachmentBubble(
            attachment = attachment,
            attachmentFile = attachmentFile,
            onOpenAttachment = onOpenAttachment,
            onDownloadAttachment = onDownloadAttachment,
            modifier = modifier,
            shape = effectiveShape,
        )
        attachment.isVideo -> VideoAttachmentBubble(
            attachment = attachment,
            attachmentFile = attachmentFile,
            onOpenAttachment = onOpenAttachment,
            onDownloadAttachment = onDownloadAttachment,
            modifier = modifier,
            shape = effectiveShape,
        )
        else -> FileAttachmentRow(
            attachment = attachment,
            attachmentFile = attachmentFile,
            onOpenAttachment = onOpenAttachment,
            onDownloadAttachment = onDownloadAttachment,
            modifier = modifier,
            shape = effectiveShape,
        )
    }
}

/** Rounded image bubble; 4:3 placeholder until the file is decoded. */
@Composable
private fun ImageAttachmentBubble(
    attachment: AttachmentMeta,
    attachmentFile: (String) -> File?,
    onOpenAttachment: (String) -> Unit,
    onDownloadAttachment: (AttachmentMeta) -> Unit,
    modifier: Modifier = Modifier,
    shape: RoundedCornerShape = AttachmentShape,
) {
    // Disk presence beats the persisted `downloaded` flag (the flag can
    // drift); attachmentFile returns null when nothing is on disk.
    val file = remember(attachment.guid, attachment.downloaded) {
        attachmentFile(attachment.guid)
    }
    val decoded = rememberDecodedImage(file = file, maxDimensionPx = 512)
    val aspect = decoded?.aspectRatio ?: FallbackAspectRatio

    Surface(
        shape = shape,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = modifier
            .widthIn(max = ImageBubbleMaxWidth)
            .heightIn(max = ImageBubbleMaxHeight)
            .aspectRatio(aspect)
            .clickable(enabled = decoded != null) { onOpenAttachment(attachment.guid) },
    ) {
        val image = decoded?.image
        if (image != null) {
            Image(
                bitmap = image,
                contentDescription = attachment.name ?: "Photo",
                contentScale = ContentScale.Crop,
                // Shared-element continuity into the fullscreen viewer.
                modifier = Modifier.fillMaxSize().sharedAttachment(attachment.guid),
            )
        } else {
            AttachmentPlaceholder(
                attachment = attachment,
                onDownloadAttachment = onDownloadAttachment,
                icon = Icons.AutoMirrored.Filled.InsertDriveFile,
                showDownload = file == null,
            )
        }
    }
}

/** Video tile; tapping a local payload opens the system media player. */
@Composable
private fun VideoAttachmentBubble(
    attachment: AttachmentMeta,
    attachmentFile: (String) -> File?,
    onOpenAttachment: (String) -> Unit,
    onDownloadAttachment: (AttachmentMeta) -> Unit,
    modifier: Modifier = Modifier,
    shape: RoundedCornerShape = AttachmentShape,
) {
    val file = remember(attachment.guid, attachment.downloaded) {
        attachmentFile(attachment.guid)
    }
    Surface(
        shape = shape,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = modifier
            .widthIn(max = ImageBubbleMaxWidth)
            .heightIn(max = ImageBubbleMaxHeight)
            .aspectRatio(FallbackAspectRatio)
            .clickable(enabled = file != null) { onOpenAttachment(attachment.guid) },
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            AttachmentPlaceholder(
                attachment = attachment,
                onDownloadAttachment = onDownloadAttachment,
                icon = Icons.Filled.VideoFile,
                showDownload = file == null,
            )
            Surface(
                shape = CircleShape,
                color = Color.Black.copy(alpha = 0.55f),
                modifier = Modifier
                    .align(Alignment.Center)
                    .size(46.dp),
            ) {
                Icon(
                    imageVector = Icons.Filled.PlayArrow,
                    contentDescription = "Play video",
                    tint = Color.White,
                    modifier = Modifier.padding(8.dp),
                )
            }
        }
    }
}

/** Generic file row: tap opens local files; missing payloads can download. */
@Composable
private fun FileAttachmentRow(
    attachment: AttachmentMeta,
    attachmentFile: (String) -> File?,
    onOpenAttachment: (String) -> Unit,
    onDownloadAttachment: (AttachmentMeta) -> Unit,
    modifier: Modifier = Modifier,
    shape: RoundedCornerShape = AttachmentShape,
) {
    val file = remember(attachment.guid, attachment.downloaded) {
        attachmentFile(attachment.guid)
    }
    Surface(
        shape = shape,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = modifier
            .widthIn(max = ImageBubbleMaxWidth)
            .clickable(enabled = file != null) { onOpenAttachment(attachment.guid) },
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(
                imageVector = mimeIcon(attachment.mime),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(30.dp),
            )
            Column {
                Text(
                    text = attachment.name ?: "Attachment",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                val size = formatBytes(attachment.sizeBytes)
                if (size.isNotEmpty()) {
                    Text(
                        text = size,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (file == null) {
                    DownloadChip(
                        attachment = attachment,
                        onDownload = onDownloadAttachment,
                        modifier = Modifier.padding(top = 6.dp),
                    )
                }
            }
        }
    }
}

/** Icon + name + size inside an image/video bubble while not yet rendered. */
@Composable
private fun AttachmentPlaceholder(
    attachment: AttachmentMeta,
    onDownloadAttachment: (AttachmentMeta) -> Unit,
    icon: ImageVector,
    showDownload: Boolean,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surfaceContainerHigh),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(32.dp),
            )
            Text(
                text = attachment.name ?: "Attachment",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            val size = formatBytes(attachment.sizeBytes)
            if (size.isNotEmpty()) {
                Text(
                    text = size,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (showDownload) {
                DownloadChip(attachment = attachment, onDownload = onDownloadAttachment)
            }
        }
    }
}

/** Small bordered chip; swaps to a spinner once the transfer is requested. */
@Composable
internal fun DownloadChip(
    attachment: AttachmentMeta,
    onDownload: (AttachmentMeta) -> Unit,
    modifier: Modifier = Modifier,
) {
    // Keyed on the whole meta so a re-emitted row (downloaded flip, size
    // update) resets the in-flight spinner state.
    var requested by remember(attachment) { mutableStateOf(false) }
    Surface(
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.primary,
        contentColor = MaterialTheme.colorScheme.onPrimary,
        modifier = modifier.clickable(
            role = androidx.compose.ui.semantics.Role.Button,
            onClickLabel = "Download ${attachment.name ?: "attachment"}",
        ) {
            requested = true
            onDownload(attachment)
        },
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            if (requested) {
                CircularProgressIndicator(
                    strokeWidth = 2.dp,
                    color = androidx.compose.material3.LocalContentColor.current,
                    modifier = Modifier.size(14.dp),
                )
            } else {
                Icon(
                    imageVector = Icons.Filled.Download,
                    contentDescription = null,
                    modifier = Modifier.size(14.dp),
                )
                Text(
                    text = "Download",
                    style = MaterialTheme.typography.labelSmall,
                )
            }
        }
    }
}

private fun mimeIcon(mime: String?): ImageVector = when {
    mime == null -> Icons.AutoMirrored.Filled.InsertDriveFile
    mime.startsWith("audio/", ignoreCase = true) -> Icons.Filled.AudioFile
    mime.startsWith("video/", ignoreCase = true) -> Icons.Filled.VideoFile
    mime.startsWith("text/", ignoreCase = true) ||
        mime.equals("application/pdf", ignoreCase = true) -> Icons.Filled.Description
    else -> Icons.AutoMirrored.Filled.InsertDriveFile
}

// --------------------------------------------------------------------- previews

@Preview(showBackground = true)
@Preview(showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun AttachmentBubblesPreview() {
    OpenBubblesTheme {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            AttachmentContent(
                attachment = AttachmentMeta(
                    guid = "1", mime = "image/jpeg", name = "sunset.jpg",
                    sizeBytes = 2_411_520L, isImage = true, downloaded = false,
                ),
                attachmentFile = { null },
                onOpenAttachment = {},
                onDownloadAttachment = {},
            )
            AttachmentContent(
                attachment = AttachmentMeta(
                    guid = "2", mime = "video/quicktime", name = "highlights.mov",
                    sizeBytes = 18_874_368L, isImage = false, downloaded = false,
                ),
                attachmentFile = { null },
                onOpenAttachment = {},
                onDownloadAttachment = {},
            )
            AttachmentContent(
                attachment = AttachmentMeta(
                    guid = "3", mime = "application/pdf", name = "itinerary.pdf",
                    sizeBytes = 412_676L, isImage = false, downloaded = true,
                ),
                attachmentFile = { null },
                onOpenAttachment = {},
                onDownloadAttachment = {},
            )
        }
    }
}

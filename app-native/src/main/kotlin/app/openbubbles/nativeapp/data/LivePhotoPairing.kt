package app.openbubbles.nativeapp.data

import java.io.File

data class LivePhotoPair(
    val still: AttachmentMeta,
    val stillFile: File,
    val motion: AttachmentMeta?,
    val motionFile: File?,
)

fun resolveLivePhotoPair(
    still: AttachmentMeta,
    provider: AttachmentProvider,
): LivePhotoPair? {
    val stillFile = provider.localFile(still.guid) ?: return null
    val motionGuid = still.livePhotoMotionGuid
    val motion = motionGuid?.let(provider::byGuid)
    val motionFile = motionGuid?.let(provider::localFile)
    return LivePhotoPair(
        still = still,
        stillFile = stillFile,
        motion = motion,
        motionFile = motionFile,
    )
}

package app.openbubbles.nativeapp.data

import java.io.File

/** Moves a prepared outgoing payload into the canonical attachment store. */
internal fun moveOutgoingAttachment(source: File, destination: File): File {
    if (source.canonicalFile == destination.canonicalFile) return destination
    destination.parentFile?.mkdirs()
    if (!source.renameTo(destination)) {
        source.copyTo(destination, overwrite = true)
        runCatching { source.delete() }
    }
    return destination
}
